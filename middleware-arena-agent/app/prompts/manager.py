"""Langfuse Prompt 管理入口占位。

1. 统一从 Langfuse 拉取 Prompt，Node 不直接写 Prompt 字符串。
2. 支持 Prompt name / label / version 和变量编译。
3. Langfuse 不可用时允许退回本地 fallback Prompt。

TODO:
- [ ] 初始化 Langfuse client。
- [ ] 实现 get_prompt(name, variables, label=None)。
- [ ] 增加缓存和调用失败 fallback。
- [ ] 将 prompt version 写入 Trace metadata。
"""
