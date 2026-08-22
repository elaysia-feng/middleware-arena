# middleware-arena-experiment

实验与模板管理服务，默认端口 `9003`。

## 服务职责

- 实验模板 CRUD：`/experiment/template/**`
- 版本快照、版本详情、回滚与文件 Diff：`/experiment/version/**`
- 实验任务创建、取消、重试、分页与进度查询：`/experiment/task/**`
- 模板文件正文支持数据库内联或 OSS 压缩对象存储
- 将待运行版本和参数下发给 runner-service

内置模板资产位于 `middleware-arena-templates`。该目录不单独启动，模板元数据和版本由本服务统一管理，避免出现两个模板中心。当前资产需通过模板、版本接口录入，不会在服务启动时自动写入数据库。
