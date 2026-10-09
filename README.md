# Middleware Arena

Middleware Arena 是一个用于学习 Redis、RabbitMQ、Seata、Elasticsearch 等中间件的性能实验平台。用户从内置实验场景创建自己的模板版本，在浏览器中修改白名单内的源码和运行参数，提交任务后由 Runner 构建、压测并回传任务状态，再进入 AI 分析。当前不提供社区发布、讨论和社交页面。

后续计划围绕中间件建设知识库，整理原理、实践笔记、实验结果和常见问题，支持检索与提问。知识库与问答尚未实现；当前先完善中间件实验和学习流程。

## 核心流程

```text
内置模板资产 → experiment-service 创建用户模板 V1 → 在线编辑 / 保存新版本
    → RabbitMQ → runner-service 构建、运行、压测、回传状态
    → 前端任务监控 / Agent 分析
```

## 内置实验

`middleware-arena-templates` 是模板资产库，不单独启动，也不直接暴露给浏览器。前端在“实验场景”点击“进入实验”后，调用 `POST /experiment/template/builtin/{key}`，由 experiment-service 读取受控的 YAML 白名单与仓库源码，创建当前用户可编辑的模板 V1。

| 模板 | 宿主服务 | 编辑内容 |
|---|---|---|
| Redis 订单详情多级缓存 | `lab-order-service` | 订单缓存、Redis 配置与运行参数 |
| RabbitMQ 社区事件异步削峰 | `community-service` / `lab-order-service` | Stream Relay、RabbitMQ 配置与消费链路 |
| Seata AT 下单一致性 | `lab-order-service` / `lab-seata-storage-participant` / `lab-seata-account-participant` | 下单、扣库存、扣余额及各服务配置 |
| Elasticsearch 社区全文搜索 | `community-service` | 搜索实现与索引配置 |
| 社区点赞收藏可靠持久化 | `community-service` | Redis Lua、Stream Outbox、RabbitMQ 与幂等落库 |

`lab-order-service`、`lab-seata-storage-participant`、`lab-seata-account-participant` 是模板的宿主或事务参与服务，不是独立的前端业务模块。浏览器不会调用内部扣库存、扣余额接口。

`community-service` 暂时保留为 RabbitMQ、Elasticsearch 和可靠持久化实验的宿主，相关模板、压测计划和验证脚本仍可使用；仅在运行这些实验时启动。社区前端页面和 API 封装已移除。

## 服务组成

| 模块 | 端口 | 职责 |
|---|---:|---|
| `middleware-arena-gateway` | 8000 | API 网关、路由、CORS 与身份透传 |
| `middleware-arena-auth` | 9001 | 注册、登录、双 Token 刷新、会员信息 |
| `middleware-arena-community` | 9002 | RabbitMQ / Elasticsearch / 可靠持久化实验宿主 |
| `middleware-arena-experiment` | 9003 | 模板、版本、Diff、任务与内置模板导入 |
| `middleware-arena-runner` | 9004 | 资源调度、构建、容器运行、k6 压测、状态回传 |
| `middleware-arena-notification` | 9005 | 站内信、未读数与 SSE 推送 |
| `middleware-arena-lab-order` | 9006 | Redis / MQ / Seata 实验宿主服务 |
| `middleware-arena-lab-seata-storage` | 9007 | Seata 库存参与方 |
| `middleware-arena-lab-seata-account` | 9008 | Seata 账户参与方 |
| `middleware-arena-lab-product` | 9009 | 实验用商品快照与单价查询 |
| `middleware-arena-agent` | 9500 | FastAPI + LangGraph 资源建议与性能诊断 |
| `middleware-arena-frontend` | 5173 | Vue 3 前端 |

模板管理统一由 `experiment-service` 提供。`middleware-arena-lab-*` 是按实验需要启动的样例工程：`lab-order-service` 是订单实验宿主，`lab-product-service` 提供实验商品数据，`lab-seata-storage-participant` 和 `lab-seata-account-participant` 分别模拟跨服务事务的库存与余额参与方。学习 Seata 时保留独立进程，才能观察分支事务与失败回滚。

目录和 Nacos 注册名已统一增加实验角色标识；端口、HTTP 路径与 Maven 模块名保持现有约定。升级代码后需重启相关实验宿主及网关，并同步自定义 Nacos 配置中的旧注册名。

## 前端功能

- 登录注册、自动刷新登录态、账户与站内通知。
- 内置场景一键创建、模板文件编辑、版本历史与 Diff。
- 任务创建、取消、失败重试、状态与阶段轮询。
- Agent 资源建议和 LangGraph 工作流分析。

页面只展示后端已公开提供的数据。任务服务当前对外提供状态、阶段和进度；QPS、P95、JVM 等时序指标需要对应查询接口后才能在前端展示。

## 本地启动

### 前置条件

- JDK 21、Maven 3.9+、Node.js 20+。
- Docker Desktop；真实 Runner 压测需要 Docker daemon。
- 可访问的 MySQL、Redis、RabbitMQ、Nacos；地址由各服务 `application.yml` 或环境变量配置。

### 构建共享依赖与中间件

```powershell
$env:JAVA_HOME = 'E:\develop\java\jdk-21'
cd middleware-arena-base
mvn install
.\scripts\start-base.ps1
```

### 启动服务与前端

至少启动 gateway、auth、experiment；执行实验时还需要 runner 与对应宿主服务。

```powershell
cd middleware-arena-experiment
mvn -pl experiment.web -am spring-boot:run

cd ..\middleware-arena-gateway
mvn spring-boot:run

cd ..\middleware-arena-frontend
npm install
npm run dev
```

如需 AI 分析，在 `middleware-arena-agent` 配置环境变量后启动 FastAPI 服务；网关把 `/agent/**` 转发到 `AGENT_SERVICE_URL`，默认 `http://127.0.0.1:9500`。前端开发服务器将 `/api/**` 代理到 `http://localhost:8000`。

## 验证

```powershell
cd middleware-arena-frontend
npm run build

cd ..\middleware-arena-experiment
mvn -pl experiment.web -am package -DskipTests
```

构建通过不等于完整实验已执行。真实端到端实验还依赖数据库、中间件、Docker、Runner 构建环境和模板宿主服务均可用。
