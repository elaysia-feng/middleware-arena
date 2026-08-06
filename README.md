# Middleware Arena

基于 **Spring Cloud** 的可编程**中间件性能实验** + **技术社区**平台。

> 用户选实验模板 → 在线改代码 → 提交压测任务 → RabbitMQ 排队 → 自动跑实验 → 采集指标 → Python Agent 分析瓶颈 → 发布实验报告 → 社区点赞/评论/收藏/Fork。

> ⚠️ 当前为**框架骨架**阶段：各服务能编译、能启动、能注册 Nacos、能走通网关。业务功能全部为 TODO。

## 技术基线

JDK 21 · Spring Boot 3.3 · Spring Cloud 2023.0 · Spring Cloud Alibaba 2023.0.3.2 · Nacos 2.3.2 · Seata 2.1(预留) · MyBatis-Plus 3.5.7 · Vue 3 + Element Plus · LangGraph(预留) · k6(预留)

## 仓库地图（按服务独立仓库）

| 仓库 | 端口 | 说明 / 状态 |
|---|---|---|
| `middleware-arena-base` | - | ★ 父 POM(版本矩阵) + base.web/base.jwt 共享库 + Docker Compose + 脚本 ✅ |
| `middleware-arena-gateway` | 8000 | 路由 / CORS / 过滤器占位（token 校验 TODO）✅ |
| `middleware-arena-auth` | 9001 | 登录 / JWT（**双 token 登录 TODO**）✅ |
| `middleware-arena-community` | 9002 | 社区（帖子/评论/点赞/收藏 TODO）✅ |
| `middleware-arena-experiment` | 9003 | 实验模板/版本/任务/SSE（TODO）✅ |
| `middleware-arena-runner` | 9004 | 压测流水线（TODO）✅ |
| `middleware-arena-notification` | 9005 | 实验完成通知（TODO）✅ |
| `middleware-arena-order` | 9006 | 业务宿主服务（Redis/MQ/Seata 实验）✅ |
| `middleware-arena-storage` | 9007 | 扣库存（Seata 参与方）✅ |
| `middleware-arena-account` | 9008 | 扣余额（Seata 参与方）✅ |
| `middleware-arena-agent` | 9500 | Python FastAPI + LangGraph 分析（TODO）✅ |
| `middleware-arena-templates` | - | 实验模板占位目录 ✅ |
| `middleware-arena-frontend` | 5173 | Vue3 + Element Plus 骨架（登录页 TODO）✅ |

✅ 全部完成（框架骨架，业务功能均为 TODO）

## 快速启动

前置：JDK21（`E:\develop\java\jdk-21`）、Maven 3.9、Docker Desktop(WSL2)。

```powershell
# 1. 安装共享父 POM + base.web/base.jwt
$env:JAVA_HOME = 'E:\develop\java\jdk-21'
cd middleware-arena-base && mvn install

# 2. 构建各服务（逐个）
cd ..\middleware-arena-auth;     mvn clean package -DskipTests
cd ..\middleware-arena-gateway;  mvn clean package -DskipTests

# 3. 启动基础模式（mysql/redis/nacos/gateway/auth）
cd ..\middleware-arena-base
.\scripts\start-base.ps1

# 4. 前端
cd ..\middleware-arena-frontend
npm install && npm run dev
```

Nacos 控制台：http://localhost:8848/nacos · 网关：http://localhost:8000

## TODO 路线图

| 优先级 | 功能 | 落点 |
|---|---|---|
| P0 | **双 token 登录**（access+refresh、Redis 轮换/拉黑、/auth/refresh） | auth-service + gateway 过滤器 |
| P0 | 前端登录页对接双 token 流程 | frontend |
| P1 | 社区：帖子/评论/点赞/收藏/关注 | community-service |
| P1 | 实验系统：模板、版本快照、Monaco 编辑、Git Diff、任务状态机、SSE | experiment-service |
| P1 | runner 流水线：构建→起容器→k6 压测→采指标→清理 | runner-service + templates |
| P1 | 四个中间件实验：Redis / RabbitMQ / Seata / Elasticsearch | templates + 宿主服务 |
| P1 | AI Agent：LangGraph 7 步分析链 + OpenAI 兼容 LLM + 报告生成 | agent |
| P2 | 排行榜 / Fork / 重试 / 超时 / 资源限制 / 审计日志 / ECharts 可视化 | 各服务 |

## 16GB 内存运行方案

| Profile | 组件 | 预算 |
|---|---|---|
| `base` | mysql, redis, nacos, gateway, auth | 2~3GB |
| `full` | 全部服务（演示） | 按 mem_limit 控制 |

详见 `middleware-arena-base/docker-compose.yml`。
