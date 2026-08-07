package com.mware.experiment.biz.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mware.common.web.ApiException;
import com.mware.common.web.ErrorCode;
import com.mware.common.web.UserContext;
import com.mware.experiment.biz.ExperimentService;
import com.mware.experiment.domain.ExperimentTemplate;
import com.mware.experiment.domain.ExperimentVersion;
import com.mware.experiment.dto.request.CreateTemplateRequest;
import com.mware.experiment.dto.request.TemplateFileRequest;
import com.mware.experiment.dto.response.TaskResponse;
import com.mware.experiment.dto.response.TemplateResponse;
import com.mware.experiment.dto.response.VersionDiffResponse;
import com.mware.experiment.dto.response.VersionResponse;
import com.mware.experiment.mapper.ExperimentTaskMapper;
import com.mware.experiment.mapper.ExperimentTemplateMapper;
import com.mware.experiment.mapper.ExperimentVersionMapper;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 实验业务实现。
 * <p>
 * 目前仅实现 createTemplate（创建模板 + 初始版本 V1），其余方法均为 TODO 骨架，
 * 待接入数据源 + runner 后逐个补齐。
 */
@Service
public class ExperimentServiceImpl implements ExperimentService {

    private final ExperimentTemplateMapper experimentTemplateMapper;
    private final ExperimentTaskMapper experimentTaskMapper;
    private final ExperimentVersionMapper experimentVersionMapper;

    public ExperimentServiceImpl(ExperimentTemplateMapper experimentTemplateMapper,
            ExperimentTaskMapper experimentTaskMapper,
            ExperimentVersionMapper experimentVersionMapper) {
        this.experimentTemplateMapper = experimentTemplateMapper;
        this.experimentTaskMapper = experimentTaskMapper;
        this.experimentVersionMapper = experimentVersionMapper;
    }

    /** 快照 JSON 序列化：base.web → spring-boot-starter-web 传递引入 jackson-databind */
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public TemplateResponse createTemplate(CreateTemplateRequest request, Long userId) {
        Long uid = UserContext.getUserId();
        if (uid == null || !uid.equals(userId)) {
            throw new ApiException(ErrorCode.FORBIDDEN);
        }

        // 1. 校验必填：name / middlewareType / scenario / files（files 决定模板能否运行）
        if (request.getName() == null || request.getMiddlewareType() == null
                || request.getScenario() == null
                || request.getFiles() == null || request.getFiles().isEmpty()) {
            throw new ApiException(ErrorCode.PARAM_INVALID);
        }

        // 2. 插模板（仅元数据，files + config 属于版本快照）
        ExperimentTemplate template = ExperimentTemplate.builder()
                .userId(uid)
                .name(request.getName())
                .middlewareType(request.getMiddlewareType())
                .scenario(request.getScenario())
                .description(request.getDescription())
                .createdAt(LocalDateTime.now())
                .build();
        experimentTemplateMapper.insert(template);

        // 3. 创建初始版本 V1（files + config → config_snapshot）
        insertVersion(template.getId(), request.getFiles(), request.getConfig());

        // 4. 返回带自增 id 的模板
        return toTemplateResponse(template);
    }

    @Override
    public TemplateResponse updateTemplate(Long templateId, CreateTemplateRequest request, Long userId) {
        // TODO[实验]：实现模板更新（仅创建者可编辑，改元数据 + files/config 有变更时生成新版本）
        // 1. experimentTemplateMapper.selectById(templateId)，不存在抛 ApiException(NOT_FOUND)
        // 2. 校验 template.getUserId() == UserContext.getUserId()，否则抛 ApiException(FORBIDDEN)
        // 3. 仅非空字段 updateById；files 非空时 insertVersion 生成新版本
        // 4. domain → TemplateResponse 返回
        return null;
    }

    @Override
    public void deleteTemplate(Long templateId) {
        // TODO[实验]：实现模板删除
        // 1. 检查模板存在，不存在抛 ApiException
        // 2. experimentTemplateMapper.deleteById(templateId)
    }

    @Override
    public TemplateResponse getTemplate(Long templateId) {
        // TODO[实验]：实现模板查询
        // 1. experimentTemplateMapper.selectById(templateId)，不存在抛 ApiException
        // 2. domain → TemplateResponse 返回
        return null;
    }

    @Override
    public List<TemplateResponse> pageTemplates(int page, int size) {
        // TODO[实验]：实现模板分页
        // 1. 构造 Page<ExperimentTemplate>，experimentTemplateMapper.selectPage(page, null)
        // 2. records 逐条 domain → TemplateResponse 返回
        return null;
    }

    @Override
    public VersionResponse createVersion(Long templateId, String configSnapshot) {
        // TODO[实验]：保存配置快照
        // 1. 校验模板存在：experimentTemplateMapper.selectById(templateId)，不存在抛 ApiException
        // 2. 生成递增 versionNo：同模板内最大 versionNo + 1
        // 3. 组装 ExperimentVersion{templateId, versionNo, configSnapshot, createdAt}，
        //    experimentVersionMapper.insert(version)，回填自增 id
        // 4. domain → VersionResponse 返回
        return null;
    }

    @Override
    public void rollbackVersion(Long templateId, Long versionId) {
        // TODO[实验]：按版本回滚配置
        // 1. 校验版本归属：versionId 必须属于 templateId，否则抛 ApiException(NOT_FOUND)
        // 2. 用快照恢复模板 configJson：template.setConfigJson(version.getConfigSnapshot())
        // 3. experimentTemplateMapper.updateById(template)，回滚完成
    }

    @Override
    public TaskResponse createTask(Long versionId) {
        // TODO[实验]：创建任务并拉起 runner
        // 1. 组装 ExperimentTask（status=pending，关联 versionId）
        // 2. experimentTaskMapper.insert(task)
        // 3. 异步通知 runner 服务开始压测
        // 4. domain → TaskResponse 返回
        return null;
    }

    @Override
    public TaskResponse getTask(Long taskId) {
        // TODO[实验]：查询任务详情
        // 1. experimentTaskMapper.selectById(taskId)，不存在抛 ApiException
        // 2. domain → TaskResponse 返回
        return null;
    }

    @Override
    public List<TaskResponse> pageTasks(int page, int size) {
        // TODO[实验]：任务分页
        // 1. 构造 Page<ExperimentTask>，experimentTaskMapper.selectPage(page, null)
        // 2. records 逐条 domain → TaskResponse 返回
        return null;
    }

    @Override
    public void cancelTask(Long taskId) {
        // TODO[实验]：取消任务
        // 1. 校验任务存在且未终结（queued / running）
        // 2. 置 status=cancelled，experimentTaskMapper.updateById(task)
    }

    @Override
    public String getTaskProgress(Long taskId) {
        // TODO[实验]：返回进度阶段描述
        // 1. 查询任务，按 status 返回阶段文案：pending / queued / running / success / failed / cancelled
        return null;
    }

    @Override
    public List<VersionResponse> listVersions(Long templateId) {
        // TODO[实验]：查询模板所有版本（按 versionNo 倒序）
        // 1. 校验模板存在
        // 2. experimentVersionMapper.selectList(templateId 过滤, orderByDesc versionNo)
        // 3. 逐条 domain → VersionResponse 返回
        return null;
    }

    @Override
    public VersionResponse getVersion(Long versionId) {
        // TODO[实验]：查询某个版本详情
        // 1. experimentVersionMapper.selectById(versionId)，不存在抛 ApiException
        // 2. domain → VersionResponse 返回
        return null;
    }

    @Override
    public VersionDiffResponse diffVersion(Long fromVersionId, Long toVersionId) {
        // TODO[实验]：对比两个版本（文件级差异）
        // 1. 查两个版本，任一不存在抛 ApiException(NOT_FOUND)
        // 2. 校验两版本属于同一模板，否则抛 ApiException(PARAM_INVALID)
        // 3. 解析两版快照 files，按 path 对比出 ADDED / MODIFIED / DELETED / UNCHANGED
        return null;
    }

    @Override
    public TaskResponse retryTask(Long taskId) {
        // TODO[实验]：重试失败任务
        // 1. experimentTaskMapper.selectById(taskId)，不存在抛 ApiException(NOT_FOUND)
        // 2. 仅 status=failed 的任务可重试；其余抛 ApiException(PARAM_INVALID)
        // 3. 置 status=queued，experimentTaskMapper.updateById(task)
        // 4. 重新异步通知 runner 服务执行（复用 createTask 的通知通道）
        // 5. domain → TaskResponse 返回
        return null;
    }

    /** 生成递增版本号并落库（并发靠 uk_template_version 唯一键兜底） */
    private ExperimentVersion insertVersion(Long templateId, List<TemplateFileRequest> files,
            Map<String, Object> config) {
        List<ExperimentVersion> versions = experimentVersionMapper.selectList(
                new LambdaQueryWrapper<ExperimentVersion>()
                        .eq(ExperimentVersion::getTemplateId, templateId)
                        .orderByDesc(ExperimentVersion::getVersionNo));
        int nextNo = versions.isEmpty() ? 1 : versions.get(0).getVersionNo() + 1;

        ExperimentVersion version = ExperimentVersion.builder()
                .templateId(templateId)
                .versionNo(nextNo)
                .configSnapshot(toSnapshotJson(files, config))
                .createdAt(LocalDateTime.now())
                .build();
        experimentVersionMapper.insert(version);
        return version;
    }

    /** files + config → 版本快照 JSON：{"files":[...],"config":{...}} */
    private String toSnapshotJson(List<TemplateFileRequest> files, Map<String, Object> config) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("files", files == null ? List.of() : files);
        if (config != null && !config.isEmpty()) {
            snapshot.put("config", config);
        }
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException e) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR);
        }
    }

    private TemplateResponse toTemplateResponse(ExperimentTemplate template) {
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
                .configJson(template.getConfigJson())
                .userId(template.getUserId())
                .createdAt(template.getCreatedAt())
                .build();
    }
}
