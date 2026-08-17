"""Agent Patch 接口占位。

1. 根据分析结果生成候选代码 Patch。
2. 提供查看 / 接受 / 拒绝 Patch 的接口契约。
3. 接受后只调用 experiment-service 创建新版本，不直接改线上代码。

TODO:
- [ ] 定义 PatchRequest / PatchResponse。
- [ ] 接入 generate_patch / validate_patch 节点。
- [ ] 接入 experiment-service createVersion。
"""
