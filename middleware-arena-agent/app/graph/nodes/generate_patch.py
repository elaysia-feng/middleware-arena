"""代码 Patch 生成节点占位。

1. 根据最终瓶颈和证据生成最小修改方案。
2. 输出文件级 Patch，不直接写入 experiment_version。
3. 为前端 Monaco Diff 和 Human-in-the-loop 提供候选修改。

TODO:
- [ ] 定义 Patch schema（path/before/after/diff/reason）。
- [ ] 接入 Langfuse Prompt: middleware-patch-generator。
- [ ] 限制只修改 editable=true 的实验文件。
"""
