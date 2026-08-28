package com.mware.runner.biz.build.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mware.runner.biz.build.SutBuilder;
import com.mware.runner.biz.config.ExperimentType;
import com.mware.runner.biz.config.RunnerProperties;
import com.mware.runner.biz.docker.DockerService;
import com.mware.runner.biz.storage.OssVersionFileStorage;
import com.mware.runner.dto.RunnerTaskMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Candidate SUT 构建实现：固定宿主工程 + 版本文件覆盖 + Maven 打包 + Docker build。
 * <p>
 * 版本消息只保存文件快照，不能直接作为 Docker build context；先复制对应宿主工程，
 * 再把快照中的文件覆盖到工作目录，保证不可编辑的宿主代码仍来自平台源码。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SutBuilderImpl implements SutBuilder {

    private static final Set<String> IGNORED_DIRECTORIES = Set.of(
            ".git", ".idea", ".omc", ".runner", "target", "node_modules", "logs");

    private final RunnerProperties properties;
    private final DockerService dockerService;
    private final OssVersionFileStorage versionFileStorage;
    private final ObjectMapper objectMapper;

    @Override
    public String build(RunnerTaskMessage message, ExperimentType type) {
        validateTask(message, type);
        if (Boolean.TRUE.equals(message.getBaseline())) {
            return type.baselineImage(properties.getImages());
        }

        List<SourceFile> files = loadFiles(message);
        Path sourceRoot = resolveSourceRoot(files);
        Path sourceProject = sourceRoot.resolve(files.get(0).serviceName());
        Path workProject = prepareWorkProject(message.getTaskId(), sourceProject);
        applyVersionFiles(files, sourceRoot, workProject);

        runMavenPackage(workProject);
        if (!Files.isRegularFile(workProject.resolve("Dockerfile"))) {
            throw new IllegalStateException("SUT 宿主工程缺少 Dockerfile: " + workProject);
        }

        String image = dockerService.sutImageName(message.getTaskId());
        dockerService.buildImage(image, workProject.toString());
        log.info("candidate SUT 镜像构建完成 taskId={}, image={}, project={}",
                message.getTaskId(), image, workProject);
        return image;
    }

    private void validateTask(RunnerTaskMessage message, ExperimentType type) {
        if (message == null || message.getTaskId() == null) {
            throw new IllegalArgumentException("SUT 构建任务缺少 taskId");
        }
        if (type == null || type == ExperimentType.UNKNOWN) {
            throw new IllegalArgumentException("SUT 构建任务缺少有效实验类型");
        }
    }

    private List<SourceFile> loadFiles(RunnerTaskMessage message) {
        String filesJson = versionFileStorage.download(message);
        try {
            JsonNode root = objectMapper.readTree(filesJson);
            if (root == null || !root.isArray() || root.isEmpty()) {
                throw new IllegalArgumentException("实验版本文件必须是非空数组");
            }

            List<SourceFile> files = new ArrayList<>();
            Set<String> paths = new HashSet<>();
            String serviceName = null;
            for (JsonNode file : root) {
                String path = normalizeRelativePath(file.path("path").asText(null));
                String content = file.hasNonNull("content") ? file.get("content").asText() : null;
                if (content == null) {
                    throw new IllegalArgumentException("版本文件缺少 content: " + path);
                }

                String currentService = Path.of(path).getName(0).toString();
                if (serviceName == null) {
                    serviceName = currentService;
                } else if (!serviceName.equals(currentService)) {
                    throw new IllegalArgumentException("一次实验只能构建一个宿主工程");
                }
                if (!paths.add(path)) {
                    throw new IllegalArgumentException("版本文件存在重复路径: " + path);
                }
                files.add(new SourceFile(Path.of(path), content, serviceName));
            }
            return files;
        } catch (IOException e) {
            throw new IllegalArgumentException("实验版本文件 JSON 格式错误", e);
        }
    }

    private Path resolveSourceRoot(List<SourceFile> files) {
        String serviceName = files.get(0).serviceName();
        for (Path candidate : sourceRootCandidates()) {
            Path serviceProject = candidate.resolve(serviceName).normalize();
            if (Files.isDirectory(serviceProject)) {
                return candidate;
            }
        }
        throw new IllegalStateException("找不到 SUT 宿主工程: " + serviceName
                + "，请检查 ma.runner.build.source-root 或 template-dir");
    }

    private List<Path> sourceRootCandidates() {
        Set<Path> candidates = new LinkedHashSet<>();
        addPathAndParents(candidates, properties.getBuild().getSourceRoot());
        addPathAndParents(candidates, properties.getBuild().getTemplateDir());
        addPathAndParents(candidates, System.getProperty("user.dir"));
        return List.copyOf(candidates);
    }

    private void addPathAndParents(Set<Path> candidates, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        Path path = Path.of(value).toAbsolutePath().normalize();
        for (int i = 0; i < 8 && path != null; i++) {
            candidates.add(path);
            path = path.getParent();
        }
    }

    private Path prepareWorkProject(Long taskId, Path sourceProject) {
        Path workRoot = Path.of(properties.getBuild().getWorkDir()).toAbsolutePath().normalize();
        Path workProject = workRoot.resolve(String.valueOf(taskId)).normalize();
        if (!workProject.startsWith(workRoot)) {
            throw new IllegalStateException("SUT 工作目录解析越界");
        }
        try {
            deleteTree(workProject);
            Files.createDirectories(workRoot);
            copyTree(sourceProject, workProject);
            return workProject;
        } catch (IOException e) {
            throw new IllegalStateException("准备 SUT 构建工作目录失败: " + workProject, e);
        }
    }

    private void applyVersionFiles(List<SourceFile> files, Path sourceRoot, Path workProject) {
        String serviceName = files.get(0).serviceName();
        for (SourceFile file : files) {
            Path repoRelative = file.path();
            Path targetRelative = repoRelative.subpath(1, repoRelative.getNameCount());
            Path target = workProject.resolve(targetRelative).normalize();
            if (!target.startsWith(workProject)) {
                throw new IllegalArgumentException("版本文件路径越界: " + repoRelative);
            }
            Path source = sourceRoot.resolve(repoRelative).normalize();
            if (!source.startsWith(sourceRoot) || !source.startsWith(sourceRoot.resolve(serviceName))) {
                throw new IllegalArgumentException("版本文件不属于宿主工程: " + repoRelative);
            }
            try {
                Files.createDirectories(target.getParent());
                Files.writeString(target, file.content(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new IllegalStateException("写入版本文件失败: " + repoRelative, e);
            }
        }
    }

    private void runMavenPackage(Path workProject) {
        RunnerProperties.Build build = properties.getBuild();
        List<String> args = new ArrayList<>();
        args.add("-f");
        args.add(workProject.resolve("pom.xml").toString());
        args.add("-DskipTests");
        if (build.getLocalRepo() != null && !build.getLocalRepo().isBlank()) {
            args.add("-Dmaven.repo.local=" + build.getLocalRepo());
        }
        if (build.isOffline()) {
            args.add("--offline");
        }
        args.add("package");

        Path outputFile = null;
        try {
            outputFile = Files.createTempFile("runner-maven-", ".log");
            ProcessBuilder processBuilder = new ProcessBuilder(mavenCommand(build.getMaven(), args));
            processBuilder.directory(workProject.toFile());
            if (build.getJdkHome() != null && !build.getJdkHome().isBlank()) {
                processBuilder.environment().put("JAVA_HOME", build.getJdkHome());
            }
            processBuilder.redirectErrorStream(true);
            processBuilder.redirectOutput(outputFile.toFile());

            Process process = processBuilder.start();
            boolean finished = process.waitFor(build.getCompileTimeoutSeconds(), TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new IllegalStateException("Maven 构建超时，工作目录=" + workProject
                        + "\n" + readOutputTail(outputFile));
            }
            if (process.exitValue() != 0) {
                throw new IllegalStateException("Maven 构建失败，退出码=" + process.exitValue()
                        + "，工作目录=" + workProject + "\n" + readOutputTail(outputFile));
            }
        } catch (IOException e) {
            throw new IllegalStateException("启动 Maven 构建失败: " + build.getMaven(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("等待 Maven 构建时被中断", e);
        } finally {
            try {
                if (outputFile != null) {
                    Files.deleteIfExists(outputFile);
                }
            } catch (IOException e) {
                log.debug("删除 Maven 临时日志失败: {}", outputFile, e);
            }
        }
    }

    private List<String> mavenCommand(String maven, List<String> args) {
        if (maven == null || maven.isBlank()) {
            throw new IllegalStateException("未配置 Maven 可执行文件");
        }
        if (maven.endsWith(".cmd") || maven.endsWith(".bat")) {
            List<String> command = new ArrayList<>();
            command.add("cmd.exe");
            command.add("/d");
            command.add("/c");
            command.add(quote(maven) + " " + args.stream().map(this::quote).reduce((a, b) -> a + " " + b).orElse(""));
            return command;
        }
        List<String> command = new ArrayList<>();
        command.add(maven);
        command.addAll(args);
        return command;
    }

    private String quote(String value) {
        return value.contains(" ") ? "\"" + value + "\"" : value;
    }

    private String readOutputTail(Path outputFile) {
        try {
            String output = Files.readString(outputFile, StandardCharsets.UTF_8);
            int maxLength = 12000;
            return output.length() <= maxLength ? output : output.substring(output.length() - maxLength);
        } catch (IOException e) {
            return "无法读取 Maven 构建日志: " + e.getMessage();
        }
    }

    private String normalizeRelativePath(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            throw new IllegalArgumentException("版本文件缺少 path");
        }
        String pathText = rawPath.trim().replace('\\', '/');
        if (pathText.startsWith("/") || pathText.matches("^[A-Za-z]:.*")) {
            throw new IllegalArgumentException("版本文件必须是相对路径: " + rawPath);
        }
        Path path = Path.of(pathText).normalize();
        for (Path part : path) {
            if ("..".equals(part.toString()) || ".".equals(part.toString())) {
                throw new IllegalArgumentException("版本文件路径包含非法片段: " + rawPath);
            }
        }
        if (path.getNameCount() < 2) {
            throw new IllegalArgumentException("版本文件路径缺少宿主工程目录: " + rawPath);
        }
        return path.toString();
    }

    private void copyTree(Path source, Path target) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                if (!dir.equals(source) && IGNORED_DIRECTORIES.contains(dir.getFileName().toString())) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                Files.createDirectories(target.resolve(source.relativize(dir)));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.copy(file, target.resolve(source.relativize(file)));
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exception) throws IOException {
                if (exception != null) {
                    throw exception;
                }
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private record SourceFile(Path path, String content, String serviceName) {
    }
}
