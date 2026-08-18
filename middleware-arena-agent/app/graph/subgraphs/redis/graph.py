"""Redis 专家 SubGraph 占位。

1. 分析 Redis 命令模式、缓存访问方式、连接与内存相关证据。
2. 识别 N+1 GET、Big Key、Hot Key、穿透/击穿/雪崩等候选问题。
3. 输出 Redis 专属 findings，交给主图统一生成 hypotheses。

TODO:
- [ ] 定义 Redis SubGraph state 输入/输出契约。
- [ ] 接入 redis diagnosis Prompt。
- [ ] 增加 Redis 专属 Tool：命令统计、key 特征、连接信息。
"""
