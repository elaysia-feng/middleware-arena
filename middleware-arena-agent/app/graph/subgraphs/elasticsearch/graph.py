"""Elasticsearch 专家 SubGraph 占位。

1. 分析查询耗时、深分页、mapping、refresh、bulk、shard 等相关证据。
2. 识别慢查询、索引设计不合理、刷新过频、分片配置问题等候选瓶颈。
3. 输出 ES 专属 findings，交给主图统一裁决。

TODO:
- [ ] 定义 Elasticsearch SubGraph 输入/输出。
- [ ] 接入 elasticsearch diagnosis Prompt。
- [ ] 增加 query profile / shard / refresh 等 Tool 数据源。
"""
