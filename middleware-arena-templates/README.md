# Middleware Arena — 实验模板

本目录是**内置模板资产库，不是独立微服务**。

- `experiment-service:9003`：模板 CRUD、版本快照、Diff、权限和任务创建，对外提供 `/experiment/template/**` 接口。
- `middleware-arena-templates`：保存平台内置模板的元数据、白名单和运行规格，作为向 experiment-service 录入模板与版本时的标准资产。
- `runner-service:9004`：接收 experiment-service 下发的模板版本，按固定宿主工程、白名单文件和用户 Diff 构建、运行、压测。

若再把本目录拆成 template-service，会与 experiment-service 的 `experiment_template / experiment_version` 数据和接口职责重复，因此当前架构不重复拆服务。

## 模板清单

| 目录 | 中间件 | 场景 | 宿主服务 |
|---|---|---|---|
| `redis/` | Redis | 订单详情 Cache-Aside | order-service |
| `rabbitmq/` | RabbitMQ | 同步处理与异步削峰 | community-service / order-service |
| `seata/` | Seata AT | 下单、扣库存、扣余额一致性 | order/storage/account-service |
| `elasticsearch/` | Elasticsearch | MySQL LIKE 与全文索引搜索 | community-service |
| `community-interaction/` | Redis + RabbitMQ | 点赞/收藏可靠异步持久化 | community-service |

每个模板包含：

- `template.yml`：用于录入 experiment-service 的元数据、运行参数和文件白名单；当前不在服务启动时自动写入数据库。
- `README.md`：基线组、实验组、观测指标、压测参数和验收条件。

模板只允许修改白名单文件，不能上传任意工程文件。宿主服务必须先通过自身构建和健康检查，runner 才能开始压测。
