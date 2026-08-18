"""Experiment Service Tool 占位。

1. 查询 task / version / template 等实验上下文。
2. 获取版本 Diff、代码快照引用和运行参数。
3. 后续提供 create_version / submit_experiment 等受控写操作。

TODO:
- [ ] 封装 experiment-service HTTP client。
- [ ] 区分只读 Tool 与需要用户确认的写 Tool。
- [ ] 增加超时、重试、错误码映射。
"""
