package com.mware.experiment.biz.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mware.common.web.ApiException;
import com.mware.common.web.ErrorCode;
import com.mware.common.web.UserContext;
import com.mware.experiment.biz.ExperimentService;
import com.mware.experiment.domain.ExperimentTask;
import com.mware.experiment.domain.ExperimentTemplate;
import com.mware.experiment.domain.ExperimentVersion;
import com.mware.experiment.dto.request.CreateTemplateRequest;
import com.mware.experiment.dto.request.TemplateFileRequest;
import com.mware.experiment.dto.request.UpdateTemplateRequest;
import com.mware.experiment.dto.response.DiffLine;
import com.mware.experiment.dto.response.FileDiff;
import com.mware.experiment.dto.response.TaskResponse;
import com.mware.experiment.dto.response.TemplateResponse;
import com.mware.experiment.dto.response.VersionDiffResponse;
import com.mware.experiment.dto.response.VersionResponse;
import com.mware.experiment.mapper.ExperimentResultMapper;
import com.mware.experiment.mapper.ExperimentTaskMapper;
import com.mware.experiment.mapper.ExperimentTemplateMapper;
import com.mware.experiment.mapper.ExperimentVersionMapper;
import com.mware.experiment.mq.message.RunnerTaskMessage;
import com.mware.experiment.mq.producer.RunnerCreaetTaskProducer;
import com.mware.experiment.mq.producer.RunnerCancelTaskProducer;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 实验业务实现。
 * <p>
 * 数据职责（按既定架构定死）：
 * <ul>
 * <li>模板 = 纯元数据（name/description/middlewareType/scenario/tags/status +
 * latestVersionId）</li>
 * <li>版本 = 内容快照（filesJson + runParamsJson + changeSummary + createdBy），只存在
 * experiment_version</li>
 * <li>任务 = 运行状态（status/currentStage/progress），runner 侧不持久化</li>
 * <li>结果 = 压测指标结构化（experiment_result，task_id 唯一）</li>
 * </ul>
 * 已实现：模板 CRUD + 版本查询；createVersion / rollbackVersion / 任务链路 / diff 均为 TODO。
 * <p>
 * 归属校验：身份一律来自 {@link UserContext}；update / delete 需为模板创建者。
 * 可见性：模板元数据公开（场景画廊），版本内容（filesJson / runParamsJson）仅创建者可读。
 */
@Service
@RequiredArgsConstructor
public class ExperimentServiceImpl implements ExperimentService {

    private final ExperimentTemplateMapper experimentTemplateMapper;
    private final ExperimentTaskMapper experimentTaskMapper;
    private final ExperimentVersionMapper experimentVersionMapper;
    private final ExperimentResultMapper experimentResultMapper;
    private final RunnerCreaetTaskProducer runnerTaskProducer;
    private final RunnerCancelTaskProducer runnerCancelTaskProducer;

    /** JSON 序列化：base.web → spring-boot-starter-web 传递引入 jackson-databind */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 默认状态：新建模板即可用，前端场景卡片可见 */
    private static final String DEFAULT_STATUS = "ENABLED";

    // ==================== 模板 CRUD ====================

    @Override
    public TemplateResponse createTemplate(CreateTemplateRequest request, Long userId) {
        Long uid = varifyAndGetUserId(userId);
        // 1. 校验必填：name / middlewareType / scenario / files（files 决定模板能否运行）
        if (request.getName() == null || request.getMiddlewareType() == null
                || request.getScenario() == null
                || request.getFiles() == null || request.getFiles().isEmpty()) {
            throw new ApiException(ErrorCode.PARAM_INVALID);
        }

        LocalDateTime now = LocalDateTime.now();
        // 2. 插模板：仅元数据，不含任何快照字段
        ExperimentTemplate template = ExperimentTemplate.builder()
                .userId(uid)
                .name(request.getName())
                .middlewareType(request.getMiddlewareType())
                .scenario(request.getScenario())
                .description(request.getDescription())
                .tags(request.getTags())
                .status(resolveStatus(request.getStatus()))
                .createdAt(now)
                .updatedAt(now)
                .build();
        experimentTemplateMapper.insert(template);

        // 3. 创建初始版本 V1（filesJson + runParamsJson 只落 experiment_version）
        ExperimentVersion version = insertVersion(template.getId(), request.getFiles(),
                request.getRunParams(), null, uid);

        // 4. 模板指向最新版本
        template.setLatestVersionId(version.getId());
        experimentTemplateMapper.updateById(template);

        return toTemplateResponse(template, version);
    }

    @Override
    public TemplateResponse updateTemplate(Long templateId, UpdateTemplateRequest request, Long userId) {
        Long uid = varifyAndGetUserId(userId);

        ExperimentTemplate template = experimentTemplateMapper.selectById(templateId);
        if (template == null) {
            throw new ApiException(ErrorCode.NOT_FOUND);
        }
        // 归属校验：仅创建者可编辑
        if (!uid.equals(template.getUserId())) {
            throw new ApiException(ErrorCode.FORBIDDEN);
        }

        // 1. 更新非空元数据字段
        if (request.getName() != null) {
            template.setName(request.getName());
        }
        if (request.getMiddlewareType() != null) {
            template.setMiddlewareType(request.getMiddlewareType());
        }
        if (request.getScenario() != null) {
            template.setScenario(request.getScenario());
        }
        if (request.getDescription() != null) {
            template.setDescription(request.getDescription());
        }
        if (request.getTags() != null) {
            template.setTags(request.getTags());
        }
        if (request.getStatus() != null) {
            template.setStatus(resolveStatus(request.getStatus()));
        }
        template.setUpdatedAt(LocalDateTime.now());

        // 2. files 有变更（前端编辑器保存总是携带完整 files）→ 生成新版本并推进 latestVersionId
        ExperimentVersion latest;
        if (request.getFiles() != null && !request.getFiles().isEmpty()) {
            latest = insertVersion(templateId, request.getFiles(), request.getRunParams(),
                    null, uid);
            template.setLatestVersionId(latest.getId());
        } else {
            // 仅元数据更新，不产生新版本
            latest = latestVersion(templateId);
        }
        experimentTemplateMapper.updateById(template);

        return toTemplateResponse(template, latest);
    }

    @Override
    public void deleteTemplate(Long templateId) {
        Long uid = UserContext.getUserId();
        if (uid == null) {
            throw new ApiException(ErrorCode.UNAUTHORIZED);
        }

        ExperimentTemplate template = experimentTemplateMapper.selectById(templateId);
        if (template == null) {
            throw new ApiException(ErrorCode.NOT_FOUND);
        }
        if (!uid.equals(template.getUserId())) {
            throw new ApiException(ErrorCode.FORBIDDEN);
        }

        // 级联删除该模板的全部版本，避免孤儿快照
        experimentTemplateMapper.deleteById(templateId);
        experimentVersionMapper.delete(new LambdaQueryWrapper<ExperimentVersion>()
                .eq(ExperimentVersion::getTemplateId, templateId));
    }

    @Override
    public TemplateResponse getTemplate(Long templateId) {
        ExperimentTemplate template = experimentTemplateMapper.selectById(templateId);
        if (template == null) {
            throw new ApiException(ErrorCode.NOT_FOUND);
        }
        // 公开读：模板只含元数据，无快照内容，画廊卡片可直接展示
        return toTemplateResponse(template, latestVersion(templateId));
    }

    @Override
    public List<TemplateResponse> pageTemplates(int page, int size) {
        if (page < 1) {
            page = 1;
        }
        if (size < 1 || size > 100) {
            size = 10;
        }

        // 公开场景画廊：展示全部模板（含他人生成），按创建时间倒序
        Page<ExperimentTemplate> p = new Page<>(page, size);
        experimentTemplateMapper.selectPage(p, new LambdaQueryWrapper<ExperimentTemplate>()
                .orderByDesc(ExperimentTemplate::getCreatedAt));

        // 批量取各模板最新版本，避免逐条 N+1 查询
        Map<Long, ExperimentVersion> latestByTemplate = latestVersionMap(p.getRecords());
        return p.getRecords().stream()
                .map(t -> toTemplateResponse(t, latestByTemplate.get(t.getId())))
                .toList();
    }

    // ==================== 版本查询 ====================

    @Override
    public List<VersionResponse> listVersions(Long templateId) {
        ExperimentTemplate template = experimentTemplateMapper.selectById(templateId);
        if (template == null) {
            throw new ApiException(ErrorCode.NOT_FOUND);
        }
        // 版本内容（filesJson / runParamsJson）仅创建者可读，非属主只返回版本元数据
        boolean includeContent = isOwner(template);
        return experimentVersionMapper.selectList(
                new LambdaQueryWrapper<ExperimentVersion>()
                        .eq(ExperimentVersion::getTemplateId, templateId)
                        .orderByDesc(ExperimentVersion::getVersionNo))
                .stream()
                .map(v -> toVersionResponse(v, includeContent))
                .toList();
    }

    @Override
    public VersionResponse getVersion(Long versionId) {
        ExperimentVersion version = experimentVersionMapper.selectById(versionId);
        if (version == null) {
            throw new ApiException(ErrorCode.NOT_FOUND);
        }
        // 版本无 userId，需回查所属模板做归属校验
        ExperimentTemplate template = experimentTemplateMapper.selectById(version.getTemplateId());
        return toVersionResponse(version, template != null && isOwner(template));
    }

    // ==================== TODO（数据链路，接入 runner / MQ 后实现） ====================
    // 待验证
    @Override
    @Transactional
    public VersionResponse createVersion(Long templateId, String filesJson, String runParamsJson,
            String changeSummary) {

        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new ApiException(ErrorCode.UNAUTHORIZED);
        }

        // 1. 校验模板存在：experimentTemplateMapper.selectById(templateId)，不存在抛 ApiException
        if (templateId == null) {
            throw new ApiException(ErrorCode.NOT_FOUND);
        }
        if (filesJson == null) {
            throw new ApiException(ErrorCode.NOT_FOUND);
        }
        if (runParamsJson == null) {
            throw new ApiException(ErrorCode.NOT_FOUND);
        }
        if (changeSummary == null) {
            throw new ApiException(ErrorCode.NOT_FOUND);
        }

        ExperimentTemplate template = experimentTemplateMapper.selectById(templateId);
        if (template == null) {
            throw new ApiException(ErrorCode.NOT_FOUND);
        }

        // 2. 生成递增 versionNo：同模板内最大 versionNo + 1
        Long versionNo = template.getLatestVersionId() == null ? 1 : template.getLatestVersionId() + 1;

        // 3. 组装 ExperimentVersion{templateId, versionNo, filesJson, runParamsJson,
        // changeSummary, createdBy=uid}，
        // experimentVersionMapper.insert(version)，回填自增 id
        ExperimentVersion version = ExperimentVersion.builder()
                .templateId(templateId)
                .versionNo(versionNo)
                .filesJson(filesJson)
                .runParamsJson(runParamsJson)
                .changeSummary(changeSummary)
                .createdBy(userId)
                .build();

        experimentVersionMapper.insert(version);
        // 4. 同步 template.latestVersionId =
        // version.id，experimentTemplateMapper.updateById
        template.setLatestVersionId(version.getId());
        experimentTemplateMapper.updateById(template);
        // 5. domain → VersionResponse 返回
        return toVersionResponse(version, false);
    }

    @Override
    @Transactional
    public void rollbackVersion(Long templateId, Long versionId) {
        // 1. 校验版本归属：versionId 必须属于 templateId，否则抛 ApiException(NOT_FOUND)
        if (templateId == null || versionId == null) {
            throw new ApiException(ErrorCode.NOT_FOUND);
        }
        ExperimentTemplate template = experimentTemplateMapper.selectById(templateId);
        ExperimentVersion version = experimentVersionMapper.selectById(versionId);
        if (template == null || template.getLatestVersionId().equals(versionId) || version == null) {
            throw new ApiException(ErrorCode.NOT_FOUND);
        }
        // 2. 以该版本 filesJson + runParamsJson 生成新版本（内容回滚到旧版），推进 latestVersionId
        if (version.getVersionNo().equals(1)) {
            throw new ApiException(ErrorCode.NOT_FOUND);
        }
        ExperimentVersion newVersion = oldVersionToNewVersion(
                experimentVersionMapper.selectOne(new LambdaQueryWrapper<ExperimentVersion>()
                        .eq(ExperimentVersion::getVersionNo, version.getVersionNo() - 1)));

        // 3. 或回退 template.latestVersionId 指向旧版本，experimentTemplateMapper.updateById
        template.setLatestVersionId(newVersion.getId());
        experimentTemplateMapper.updateById(template);
        experimentVersionMapper.insert(newVersion);
    }

    /** 回退老版本需要， 把老版本提升成新版本 */
    private ExperimentVersion oldVersionToNewVersion(ExperimentVersion version) {
        return ExperimentVersion.builder()
                .changeSummary(version.getChangeSummary())
                .filesJson(version.getFilesJson())
                .runParamsJson(version.getRunParamsJson())
                .createdAt(LocalDateTime.now())
                .createdBy(version.getCreatedBy())
                .versionNo(version.getVersionNo() + 1)
                .build();
    }

    @Override
    public TaskResponse createTask(Long versionId) {
        Long uid = UserContext.getUserId();
        if (uid == null) {
            throw new ApiException(ErrorCode.UNAUTHORIZED);
        }

        // 1. 校验版本存在：RunnerTaskMessage 需带 filesJson / runParamsJson，随版本快照投递
        if (versionId == null) {
            throw new ApiException(ErrorCode.NOT_FOUND);
        }
        ExperimentVersion version = experimentVersionMapper.selectById(versionId);
        if (version == null) {
            throw new ApiException(ErrorCode.NOT_FOUND);
        }
        // 版本内容（filesJson / runParamsJson）仅创建者可读，任务消费该版本快照，仅模板属主可对其建任务
        ExperimentTemplate template = experimentTemplateMapper.selectById(version.getTemplateId());
        if (template == null || !isOwner(template)) {
            throw new ApiException(ErrorCode.FORBIDDEN);
        }

        // 2. 组装 ExperimentTask（status=QUEUED，currentStage=null，progress=0，关联 versionId）
        LocalDateTime now = LocalDateTime.now();
        ExperimentTask task = ExperimentTask.builder()
                .userId(uid)
                .versionId(versionId)
                .status("QUEUED")
                .currentStage(null)
                .progress(0)
                .createdAt(now)
                .updatedAt(now)
                .build();
        experimentTaskMapper.insert(task);

        // 3. 投递 RunnerTaskMessage{taskId, versionId, filesJson, runParamsJson} 至
        // RabbitMQ，
        // runner 执行后回传 progress/status，由本服务更新 task
        RunnerTaskMessage taskMessage = RunnerTaskMessage.builder()
                .taskId(task.getId())
                .versionId(versionId)
                .filesJson(version.getFilesJson())
                .runParamsJson(version.getRunParamsJson())
                .taskType("CREATE")
                .build();
        runnerTaskProducer.send(taskMessage);

        // 4. domain → TaskResponse 返回
        return toTaskResponse(task);
    }

    /** domain → TaskResponse 映射（任务响应输出） */
    private TaskResponse toTaskResponse(ExperimentTask task) {
        return TaskResponse.builder()
                .id(task.getId())
                .userId(task.getUserId())
                .versionId(task.getVersionId())
                .status(task.getStatus())
                .currentStage(task.getCurrentStage())
                .progress(task.getProgress())
                .errorMessage(task.getErrorMessage())
                .createdAt(task.getCreatedAt())
                .startedAt(task.getStartedAt())
                .finishedAt(task.getFinishedAt())
                .build();
    }

    @Override
    public TaskResponse getTask(Long taskId) {
        // 1. experimentTaskMapper.selectById(taskId)，不存在抛 ApiException(NOT_FOUND)
        ExperimentTask task = experimentTaskMapper.selectById(taskId);
        if (task == null) {
            throw new ApiException(ErrorCode.NOT_FOUND);
        }
        // 2. 归属校验：仅任务创建者可读
        if (!task.getUserId().equals(UserContext.getUserId())) {
            throw new ApiException(ErrorCode.FORBIDDEN);
        }
        // 3. domain → TaskResponse 返回
        return toTaskResponse(task);
    }

    @Override
    public List<TaskResponse> pageTasks(int page, int size) {
        if (page < 1) {
            page = 1;
        }
        if (size < 1 || size > 100) {
            size = 10;
        }

        // 1. 仅查当前登录用户的任务
        Long uid = UserContext.getUserId();
        if (uid == null) {
            throw new ApiException(ErrorCode.UNAUTHORIZED);
        }
        // 2. 构造 Page<ExperimentTask>，experimentTaskMapper.selectPage(page, wrapper)
        Page<ExperimentTask> p = new Page<>(page, size);
        experimentTaskMapper.selectPage(p, new LambdaQueryWrapper<ExperimentTask>()
                .eq(ExperimentTask::getUserId, uid)
                .orderByDesc(ExperimentTask::getCreatedAt));

        // 3. records 逐条 domain → TaskResponse 返回
        return p.getRecords().stream()
                .map(this::toTaskResponse)
                .toList();
    }

    @Override
    public void cancelTask(Long taskId) {
        // 1. 校验任务存在且未终结（QUEUED / RUNNING）
        ExperimentTask task = experimentTaskMapper.selectById(taskId);
        if (task == null) {
            throw new ApiException(ErrorCode.NOT_FOUND);
        }
        // 归属校验：仅任务创建者可取消
        if (!task.getUserId().equals(UserContext.getUserId())) {
            throw new ApiException(ErrorCode.FORBIDDEN);
        }
        if (!"QUEUED".equals(task.getStatus()) && !"RUNNING".equals(task.getStatus())) {
            throw new ApiException(ErrorCode.PARAM_INVALID);
        }
        // 2. 置 status=CANCELLED、finishedAt=now，先落盘再发消息：
        // 先 updateById 持久化 CANCELLED，返回行数 !=1（任务已被删/状态已变）抛 NOT_FOUND 阻止后续 send；
        // 再发 MQ。彻底闭合"send 成功但 update 失败"导致 runner 已取消而 DB 仍 QUEUED/RUNNING 的不一致窗口。
        task.setStatus("CANCELLED");
        task.setFinishedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        int updated = experimentTaskMapper.updateById(task);
        if (updated != 1) {
            throw new ApiException(ErrorCode.NOT_FOUND);
        }

        // 3. 用mq发送消息, 这个可能后面的字段会拓展
        runnerCancelTaskProducer.send(RunnerTaskMessage.builder().taskId(taskId).taskType("CANCEL").build());
    }

    @Override
    public String getTaskProgress(Long taskId) {
        // 1. 查询任务，不存在抛 ApiException(NOT_FOUND)
        ExperimentTask task = experimentTaskMapper.selectById(taskId);
        if (task == null) {
            throw new ApiException(ErrorCode.NOT_FOUND);
        }
        // 归属校验：仅任务创建者可查进度
        if (!task.getUserId().equals(UserContext.getUserId())) {
            throw new ApiException(ErrorCode.FORBIDDEN);
        }

        // 2. 按 status + currentStage 返回文案：
        // QUEUED → "排队中"，RUNNING →
        // 当前阶段（BUILDING/STARTING/BENCHMARKING/COLLECTING/ANALYZING），
        // SUCCESS / FAILED / CANCELLED → 终态
        String status = task.getStatus();
        if ("QUEUED".equals(status)) {
            return "排队中";
        }
        if ("RUNNING".equals(status)) {
            String stage = task.getCurrentStage();
            if ("BUILDING".equals(stage)) {
                return "构建中";
            }
            if ("STARTING".equals(stage)) {
                return "启动中";
            }
            if ("BENCHMARKING".equals(stage)) {
                return "压测中";
            }
            if ("COLLECTING".equals(stage)) {
                return "数据采集中";
            }
            if ("ANALYZING".equals(stage)) {
                return "分析中";
            }
            return "运行中";
        }
        if ("SUCCESS".equals(status)) {
            return "已完成，进度 " + task.getProgress() + "%";
        }
        if ("FAILED".equals(status)) {
            return "失败：" + (task.getErrorMessage() == null ? "未知原因" : task.getErrorMessage());
        }
        if ("CANCELLED".equals(status)) {
            return "已取消";
        }
        return status;
    }

    @Override
    public VersionDiffResponse diffVersion(Long fromVersionId, Long toVersionId) throws JsonProcessingException {
        // 1. 查两个版本，任一不存在抛 ApiException(NOT_FOUND)
        if (fromVersionId == null || toVersionId == null) {
            throw new ApiException(ErrorCode.NOT_FOUND);
        }
        ExperimentVersion fromVersion = experimentVersionMapper.selectById(fromVersionId);
        ExperimentVersion toVersion = experimentVersionMapper.selectById(toVersionId);
        if (fromVersion == null || toVersion == null) {
            throw new ApiException(ErrorCode.NOT_FOUND);
        }

        // 2. 两版本必须属于同一模板，否则参数组合无效（跨模板版本无法对比）
        if (!fromVersion.getTemplateId().equals(toVersion.getTemplateId())) {
            throw new ApiException(ErrorCode.PARAM_INVALID);
        }

        // 3. 从两版文件快照中各抽 path → content 索引
        Map<String, String> fromContents = indexFileContentsByPath(fromVersion.getFilesJson());
        Map<String, String> toContents = indexFileContentsByPath(toVersion.getFilesJson());

        // 对比主键 = 两版 filesJson 快照解析出的文件 key 并集（见 indexFileContentsByPath；
        // 保持 from 顺序，to 独有 key 追加在后）。以 key 相同为准，两侧各取 content 对比：
        // 一侧缺该 key 即整文件新增 / 删除；key 相同再比内容，内容不同则逐行 LCS diff。
        Set<String> fileJsonKeys = new LinkedHashSet<>(fromContents.keySet());
        fileJsonKeys.addAll(toContents.keySet());

        List<FileDiff> fileDiffs = new ArrayList<>();
        for (String fileJsonKey : fileJsonKeys) {
            String fromContent = fromContents.get(fileJsonKey);
            String toContent = toContents.get(fileJsonKey);

            if (fromContent == null) {
                // 仅目标版本存在：整文件新增，diffLines 全为 ADD（行号按 to 版本 1..N）
                fileDiffs.add(FileDiff.builder().path(fileJsonKey).changeType("ADDED")
                        .diffLines(allAddedLines(toContent)).build());
            } else if (toContent == null) {
                // 仅基准版本存在：整文件删除，diffLines 全为 REMOVE（行号按 from 版本 1..N）
                fileDiffs.add(FileDiff.builder().path(fileJsonKey).changeType("DELETED")
                        .diffLines(allRemovedLines(fromContent)).build());
            } else if (fromContent.equals(toContent)) {
                // key 相同且 content 逐字节一致：未变化，无行级明细
                fileDiffs.add(FileDiff.builder().path(fileJsonKey).changeType("UNCHANGED")
                        .diffLines(List.of()).build());
            } else {
                // key 相同但 content 不同：按行 LCS diff，输出 + / − 行（相同行跳过）
                fileDiffs.add(FileDiff.builder().path(fileJsonKey).changeType("MODIFIED")
                        .diffLines(lineLevelDiff(toLines(fromContent), toLines(toContent)))
                        .build());
            }
        }

        // 4. 组装响应
        return VersionDiffResponse.builder()
                .fromVersionId(fromVersionId)
                .toVersionId(toVersionId)
                .fromVersionNo(fromVersion.getVersionNo().intValue())
                .toVersionNo(toVersion.getVersionNo().intValue())
                .fileDiffs(fileDiffs)
                .build();
    }

    /**
     * 从版本文件快照 filesJson 中按 path 建立内容索引。
     * <p>
     * filesJson 是 JSON 数组，每个元素是完整文件对象
     * {@code {path, language, content, editable}}（见 ExperimentVersion.filesJson 注释）；
     * 但 diff 只关心 path（文件身份）与 content（行内容），language / editable 与对比无关，
     * 故此处仅抽取 path → content 映射并保持数组出现顺序。
     */
    private Map<String, String> indexFileContentsByPath(String filesJson) throws JsonProcessingException {
        Map<String, String> contents = new LinkedHashMap<>();
        if (filesJson == null || filesJson.isBlank()) {
            return contents;
        }
        JsonNode arr = objectMapper.readTree(filesJson);
        if (!arr.isArray()) {
            return contents;
        }
        for (JsonNode node : arr) {
            String path = node.path("path").asText();
            if (!path.isBlank()) {
                contents.put(path, node.path("content").asText());
            }
        }
        return contents;
    }

    /** 内容 → 行列表；统一 \r\n / \r 为 \n，避免跨平台换行差异误判整文件 MODIFIED */
    private List<String> toLines(String content) {
        if (content == null || content.isEmpty()) {
            return List.of();
        }
        return Arrays.asList(content.replace("\r\n", "\n").replace("\r", "\n").split("\n", -1));
    }

    /** 整文件新增的行：to 版本 1..N 行号，type=ADD */
    private List<DiffLine> allAddedLines(String content) {
        List<String> lines = toLines(content);
        List<DiffLine> result = new ArrayList<>(lines.size());
        for (int k = 0; k < lines.size(); k++) {
            result.add(DiffLine.builder().type("ADD").newLineNo(k + 1).content(lines.get(k)).build());
        }
        return result;
    }

    /** 整文件删除的行：from 版本 1..N 行号，type=REMOVE */
    private List<DiffLine> allRemovedLines(String content) {
        List<String> lines = toLines(content);
        List<DiffLine> result = new ArrayList<>(lines.size());
        for (int k = 0; k < lines.size(); k++) {
            result.add(DiffLine.builder().type("REMOVE").oldLineNo(k + 1).content(lines.get(k)).build());
        }
        return result;
    }

    /**
     * 行级 diff（手写 LCS 后缀 DP + 回溯，零第三方依赖——离线仓库无 java-diff-utils）：
     * 输出变更行序列，ADD 带 to 版本行号、REMOVE 带 from 版本行号，相同行作为 context 跳过。
     * 文件几百行量级 O(N*M) 足够（500×500 = 25 万次比较）。
     */
    private List<DiffLine> lineLevelDiff(List<String> from, List<String> to) {
        int m = from.size();
        int n = to.size();
        // dp[i][j]：from[i..] 与 to[j..] 的最长公共子序列长度
        int[][] dp = new int[m + 1][n + 1];
        for (int i = m - 1; i >= 0; i--) {
            for (int j = n - 1; j >= 0; j--) {
                dp[i][j] = from.get(i).equals(to.get(j))
                        ? dp[i + 1][j + 1] + 1
                        : Math.max(dp[i + 1][j], dp[i][j + 1]);
            }
        }

        List<DiffLine> result = new ArrayList<>();
        int i = 0;
        int j = 0;
        while (i < m && j < n) {
            if (from.get(i).equals(to.get(j))) {
                i++;
                j++;
            } else if (dp[i + 1][j] >= dp[i][j + 1]) {
                // 删除 from 当前行（行号 i+1）
                result.add(DiffLine.builder().type("REMOVE").oldLineNo(i + 1).content(from.get(i)).build());
                i++;
            } else {
                // 新增 to 当前行（行号 j+1）
                result.add(DiffLine.builder().type("ADD").newLineNo(j + 1).content(to.get(j)).build());
                j++;
            }
        }
        while (i < m) {
            result.add(DiffLine.builder().type("REMOVE").oldLineNo(i + 1).content(from.get(i)).build());
            i++;
        }
        while (j < n) {
            result.add(DiffLine.builder().type("ADD").newLineNo(j + 1).content(to.get(j)).build());
            j++;
        }
        return result;
    }

    @Override
    public TaskResponse retryTask(Long taskId) {
        // 1. 校验任务存在
        ExperimentTask task = experimentTaskMapper.selectById(taskId);
        if (task == null) {
            throw new ApiException(ErrorCode.NOT_FOUND);
        }
        // 2. 归属校验：仅任务创建者可重试（对齐 cancelTask / getTask）
        if (!task.getUserId().equals(UserContext.getUserId())) {
            throw new ApiException(ErrorCode.FORBIDDEN);
        }
        // 3. 仅 FAILED 状态可重试；终态成功 / 取消 / 运行中的任务不允许
        if (!"FAILED".equals(task.getStatus())) {
            throw new ApiException(ErrorCode.PARAM_INVALID);
        }
        // 4. 复位运行字段为排队态，先落盘再投递（对齐 cancelTask 的"先 update 后 send"顺序）
        LocalDateTime now = LocalDateTime.now();
        task.setStatus("QUEUED");
        task.setCurrentStage(null);
        task.setProgress(0);
        task.setErrorMessage(null);
        task.setStartedAt(null);
        task.setFinishedAt(null);
        task.setUpdatedAt(now);
        int updated = experimentTaskMapper.updateById(task);
        if (updated != 1) {
            throw new ApiException(ErrorCode.NOT_FOUND);
        }

        // 5. 重新投递 RunnerTaskMessage（复用 createTask 投递通道；版本内容是不可变快照）
        ExperimentVersion version = experimentVersionMapper.selectById(task.getVersionId());
        if (version == null) {
            throw new ApiException(ErrorCode.NOT_FOUND);
        }
        RunnerTaskMessage taskMessage = RunnerTaskMessage.builder()
                .taskId(task.getId())
                .versionId(task.getVersionId())
                .filesJson(version.getFilesJson())
                .runParamsJson(version.getRunParamsJson())
                .taskType("CREATE")
                .build();
        runnerTaskProducer.send(taskMessage);

        // 6. domain → TaskResponse 返回
        return toTaskResponse(task);
    }

    // ==================== 私有辅助 ====================

    /** 生成递增版本号并落库（并发靠 uk_template_version 唯一键兜底）；changeSummary 暂由前端可选传入 */
    private ExperimentVersion insertVersion(Long templateId, List<TemplateFileRequest> files,
            Map<String, Object> runParams, String changeSummary, Long createdBy) {
        List<ExperimentVersion> versions = experimentVersionMapper.selectList(
                new LambdaQueryWrapper<ExperimentVersion>()
                        .eq(ExperimentVersion::getTemplateId, templateId)
                        .orderByDesc(ExperimentVersion::getVersionNo));
        long nextNo = versions.isEmpty() ? 1 : versions.get(0).getVersionNo() + 1;

        // editable 白名单缺省视为 true，显式落快照
        List<TemplateFileRequest> resolvedFiles = files.stream()
                .map(f -> {
                    if (f.getEditable() == null) {
                        f.setEditable(Boolean.TRUE);
                    }
                    return f;
                })
                .toList();

        ExperimentVersion version = ExperimentVersion.builder()
                .templateId(templateId)
                .versionNo(nextNo)
                .filesJson(toJson(resolvedFiles))
                .runParamsJson(toJson(runParams))
                .changeSummary(changeSummary)
                .createdBy(createdBy)
                .createdAt(LocalDateTime.now())
                .build();
        experimentVersionMapper.insert(version);
        return version;
    }

    /** 查询某模板最新版本（versionNo 最大的一行） */
    private ExperimentVersion latestVersion(Long templateId) {
        return experimentVersionMapper.selectOne(
                new LambdaQueryWrapper<ExperimentVersion>()
                        .eq(ExperimentVersion::getTemplateId, templateId)
                        .orderByDesc(ExperimentVersion::getVersionNo)
                        .last("LIMIT 1"));
    }

    /** 批量查询各模板最新版本：模板 id → 最大 versionNo 的版本 */
    private Map<Long, ExperimentVersion> latestVersionMap(List<ExperimentTemplate> templates) {
        Map<Long, ExperimentVersion> result = new HashMap<>();
        if (templates == null || templates.isEmpty()) {
            return result;
        }
        List<Long> ids = templates.stream().map(ExperimentTemplate::getId).toList();
        List<ExperimentVersion> versions = experimentVersionMapper.selectList(
                new LambdaQueryWrapper<ExperimentVersion>()
                        .in(ExperimentVersion::getTemplateId, ids));
        for (ExperimentVersion v : versions) {
            ExperimentVersion current = result.get(v.getTemplateId());
            if (current == null || v.getVersionNo() > current.getVersionNo()) {
                result.put(v.getTemplateId(), v);
            }
        }
        return result;
    }

    /** 任意对象 → JSON 字符串；null 原样返回 null */
    private String toJson(Object obj) {
        if (obj == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR);
        }
    }

    /** status 空值兜底为 ENABLED */
    private String resolveStatus(String status) {
        return status == null || status.isBlank() ? DEFAULT_STATUS : status;
    }

    /** 当前登录用户是否模板创建者（null uid = 未登录，视为非属主） */
    private boolean isOwner(ExperimentTemplate template) {
        Long uid = UserContext.getUserId();
        return uid != null && uid.equals(template.getUserId());
    }

    private TemplateResponse toTemplateResponse(ExperimentTemplate template, ExperimentVersion version) {
        if (template == null) {
            return null;
        }
        return TemplateResponse.builder()
                .id(template.getId())
                .name(template.getName())
                .description(template.getDescription())
                .middlewareType(template.getMiddlewareType())
                .scenario(template.getScenario())
                .tags(template.getTags())
                .status(template.getStatus())
                .userId(template.getUserId())
                .createdAt(template.getCreatedAt())
                .updatedAt(template.getUpdatedAt())
                .latestVersionId(template.getLatestVersionId())
                .latestVersionNo(version == null ? null : version.getVersionNo())
                .build();
    }

    /** includeContent=false：非属主视图，剥离版本内容（filesJson / runParamsJson），仅保留版本元数据 */
    private VersionResponse toVersionResponse(ExperimentVersion version, boolean includeContent) {
        return VersionResponse.builder()
                .id(version.getId())
                .templateId(version.getTemplateId())
                .versionNo(version.getVersionNo())
                .filesJson(includeContent ? version.getFilesJson() : null)
                .runParamsJson(includeContent ? version.getRunParamsJson() : null)
                .changeSummary(version.getChangeSummary())
                .createdBy(version.getCreatedBy())
                .createdAt(version.getCreatedAt())
                .build();
    }

    /** 校验是否还是当前user_id防止篡改 */
    private Long varifyAndGetUserId(Long userId) {
        Long uid = UserContext.getUserId();
        if (uid == null || !uid.equals(userId)) {
            throw new ApiException(ErrorCode.FORBIDDEN);
        }
        return uid;
    }

}
