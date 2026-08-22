# Middleware Arena

基于 **Spring Cloud** 的可编程**中间件性能实验** + **技术社区**平台。

> 用户选实验模板 → 在线改代码 → 提交压测任务 → RabbitMQ 排队 → 自动跑实验 → 采集指标 → Python Agent 分析瓶颈 → 发布实验报告 → 社区点赞/评论/收藏/Fork。

> 当前 Java/Vue 非 Agent 主链路已落地：双 Token 登录、社区互动、实验任务、Runner 编排、通知、Seata 下单事务均有实际实现。Python Agent 分析链仍单独演进。

## 技术基线

JDK 21 · Spring Boot 3.3 · Spring Cloud 2023.0 · Spring Cloud Alibaba 2023.0.3.2 · Nacos 2.3.2 · Seata 2.1(预留) · MyBatis-Plus 3.5.7 · Vue 3 + Element Plus · LangGraph(预留) · k6(预留)

## 仓库地图（按服务独立仓库）

| 仓库 | 端口 | 说明 / 状态 |
|---|---|---|
| `middleware-arena-base` | - | ★ 父 POM(版本矩阵) + base.web/base.jwt 共享库 + Docker Compose + 脚本 ✅ |
| `middleware-arena-gateway` | 8000 | JWT 鉴权、内部 HMAC 身份签名、服务路由 ✅ |
| `middleware-arena-auth` | 9001 | BCrypt、Access JWT、Refresh Token 轮换与登出 ✅ |
| `middleware-arena-community` | 9002 | 帖子、评论、点赞、收藏、关注、搜索 ✅ |
| `middleware-arena-experiment` | 9003 | 模板、版本、Diff、任务状态与 Runner MQ ✅ |
| `middleware-arena-runner` | 9004 | 资源调度、构建、容器、k6、指标、清理与状态回传 ✅ |
| `middleware-arena-notification` | 9005 | 站内信、未读、RabbitMQ 消费与 SSE ✅ |
| `middleware-arena-order` | 9006 | 业务宿主服务（Redis/MQ/Seata 实验）✅ |
| `middleware-arena-storage` | 9007 | 扣库存（Seata 参与方）✅ |
| `middleware-arena-account` | 9008 | 扣余额（Seata 参与方）✅ |
| `middleware-arena-agent` | 9500 | Python FastAPI + LangGraph 分析（TODO）✅ |
| `middleware-arena-templates` | - | 五套 YAML 内置模板资产及白名单 ✅ |
| `middleware-arena-frontend` | 5173 | Vue3 双 Token 登录、自动刷新、路由守卫 ✅ |

除 Python Agent 外，核心服务源码均可构建；真实 Runner 压测还要求 Docker daemon 和模板构建环境在线。

## 快速启动

前置：JDK21（`E:\develop\java\jdk-21`）、Maven 3.9、Docker Desktop(WSL2)。

```powershell
# 1. 安装共享父 POM + base.web/base.jwt
$env:JAVA_HOME = 'E:\develop\java\jdk-21'
cd middleware-arena-base && mvn install

# 2. 构建各服务（逐个）
cd ..\middleware-arena-auth;     mvn clean package -DskipTests
cd ..\middleware-arena-gateway;  mvn clean package -DskipTests

# 3. 在 192.168.0.192 启动共用中间件（Redis/RabbitMQ/Nacos）
cd ..\middleware-arena-base
.\scripts\start-base.ps1

# 4. 前端
cd ..\middleware-arena-frontend
npm install && npm run dev
```

Nacos 控制台：http://localhost:8848/nacos · 网关：http://localhost:8000

## 后续增强

| 优先级 | 功能 | 落点 |
|---|---|---|
| P1 | AI Agent：LangGraph 7 步分析链 + OpenAI 兼容 LLM + 报告生成 | agent |
| P2 | 多实例 Runner 分布式资源计数、审计日志与更完整可视化 | 各服务 |

## 16GB 内存运行方案

| Profile | 组件 | 预算 |
|---|---|---|
| 默认 | redis, rabbitmq, nacos, sentinel, elasticsearch, seata | 按 mem_limit 控制 |

详见 `middleware-arena-base/docker-compose.yml`。
