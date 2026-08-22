package com.mware.experiment.biz;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.mware.experiment.dto.request.CreateTemplateRequest;
import com.mware.experiment.dto.request.UpdateTemplateRequest;
import com.mware.experiment.dto.response.TaskResponse;
import com.mware.experiment.dto.response.AgentAnalysisContextResponse;
import com.mware.experiment.dto.response.SimilarExperimentResponse;
import com.mware.experiment.dto.response.TemplateResponse;
import com.mware.experiment.dto.response.VersionDiffResponse;
import com.mware.experiment.dto.response.VersionResponse;

import java.util.List;

/**
 * 实验业务接口。
 * <p>
 * 方法签名已定，具体实现留待接入 experiment.mapper / runner 后补齐。
 * Request→domain 与 domain→Response 映射在实现内部完成。
 */
public interface ExperimentService {

    /** 实验模板 CRUD */
    TemplateResponse createTemplate(CreateTemplateRequest request, Long userId);

    TemplateResponse updateTemplate(Long templateId, UpdateTemplateRequest request, Long userId);

    void deleteTemplate(Long templateId);

    TemplateResponse getTemplate(Long templateId);

    List<TemplateResponse> pageTemplates(int page, int size);

    /** 版本：为模板保存一个新版本（文件 + 运行参数 + 变更说明），并推进 latestVersionId */
    VersionResponse createVersion(Long templateId, String filesJson, String runParamsJson, String changeSummary);

    /** 版本回滚：按版本恢复配置 */
    void rollbackVersion(Long templateId, Long versionId);

    /** 创建实验任务（拉起 runner 压测） */
    TaskResponse createTask(Long versionId);

    TaskResponse getTask(Long taskId);

    List<TaskResponse> pageTasks(int page, int size);

    void cancelTask(Long taskId);

    /** 任务进度（SSE 轮询，返回当前阶段描述） */
    String getTaskProgress(Long taskId);

    /** 查询模板所有版本（按 versionNo 倒序） */
    List<VersionResponse> listVersions(Long templateId);

    /** 查询某个版本详情 */
    VersionResponse getVersion(Long versionId);

    /** 对比两个版本（文件级差异） */
    VersionDiffResponse diffVersion(Long fromVersionId, Long toVersionId) throws JsonProcessingException;

    /** 重试失败任务 */
    TaskResponse retryTask(Long taskId);

    /** Agent 内部接口：加载一次分析所需的任务、版本、指标、基线和代码差异。 */
    AgentAnalysisContextResponse getAgentAnalysisContext(Long taskId, Long baselineTaskId);

    /** Agent 内部接口：检索同中间件、同场景且指标接近的历史成功实验。 */
    List<SimilarExperimentResponse> findSimilarExperiments(Long taskId, int limit);
}
