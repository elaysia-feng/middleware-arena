# Elasticsearch 社区搜索实验

对比 `community_post` 的 MySQL LIKE 搜索与 Elasticsearch 标题/正文全文检索。

## 宿主与接口

- 宿主：`middleware-arena-community`
- 接口：`GET /community/search?keyword=...&page=1&size=20`
- 基线组：当前 `SearchServiceImpl` 的 MySQL LIKE
- 实验组：Elasticsearch multi_match + 标题/正文高亮；ES 不可用时降级到基线实现

## 可编辑白名单

- `community.biz/.../SearchServiceImpl.java`
- `community.web/src/main/resources/application.yml`
- 新增的 ES 文档映射和 Repository 文件

帖子写入、权限、分页边界与响应 DTO 不允许编辑。

## 默认数据与压测

- 数据量：`1万 / 10万 / 100万` 帖子
- 关键词：精确标题、正文常见词、无结果词各占三分之一
- 并发阶梯：`10 / 30 / 60`
- 每阶持续：`30s`
- 索引刷新间隔：`1s`

## 指标与验收

- P50/P95/P99、错误率、QPS
- 搜索命中数与结果一致性、索引同步延迟
- ES 查询耗时、CPU、堆、segment 数量
- 验收要求：分页稳定；高亮字段只影响展示；ES 故障时降级响应仍为有效业务结果
