"""Seata 专家 SubGraph 占位。

1. 分析全局事务耗时、锁冲突、undo_log、RPC 链路和事务范围。
2. 识别事务过大、热点行竞争、参与方耗时异常等候选问题。
3. 输出 Seata 专属 findings，交给主图统一生成瓶颈假设。

TODO:
- [ ] 定义 Seata SubGraph 输入/输出。
- [ ] 接入 seata diagnosis Prompt。
- [ ] 增加事务耗时、锁等待、参与方调用链 Tool。
"""
