package com.mware.experiment.biz;

/**
 * 实验业务接口。
 * <p>
 * TODO：
 *   - 实验模板 CRUD：定义实验步骤 / 参数 / 中间件组合
 *   - 版本快照：保存实验配置版本，支持回滚
 *   - 任务状态机：pending → queued → running → success/failed/cancelled
 *   - SSE 进度推送：实时推送任务执行进度给前端
 *   - 调用 runner 服务发起实际压测任务
 *   - 接入时需引入 experiment.mapper 依赖并启用数据源
 */
public interface ExperimentService {

}
