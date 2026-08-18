"""代码分析节点占位。

1. 分析当前版本代码与基线版本 Diff。
2. 提取可能影响性能的调用、循环、事务、批处理和配置变化。
3. 只生成 code_findings，最终瓶颈由 hypothesis/judge 节点综合判断。

TODO:
- [ ] 读取 version diff 与必要文件上下文。
- [ ] 接入 Langfuse Prompt: middleware-code-analysis。
- [ ] 输出文件路径、代码位置、风险类型和证据。
"""
