# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概览

Middleware Arena：基于 **Spring Cloud** 的可编程**中间件性能实验** + **技术社区**平台。用户选实验模板 → 在线改代码 → 提交压测任务 → RabbitMQ 排队 → runner 跑实验采指标 → Python Agent 分析瓶颈 → 发布报告 → 社区互动。

当前为**框架骨架**阶段：服务能编译、能启动、能注册 Nacos、能走通网关；绝大多数业务功能仍是 TODO。不要假设业务功能已可用，动手前先确认目标方法是否只是占位。

技术基线：JDK 21 · Spring Boot 3.3.5 · Spring Cloud 2023.0.3 · Spring Cloud Alibaba 2023.0.3.2 · Nacos 2.3.2（服务发现）· MyBatis-Plus 3.5.7 · Seata 2.1（预留）· Vue 3 + Vite + Element Plus · Python FastAPI + LangGraph。

## 构建（重要：本机离线 Maven 环境）

项目**没有根聚合 pom**，每个服务目录是独立 Maven 项目，根 pom 自身作为聚合器（含 web/biz/domain/dto/mapper 等子模块）。

- 本地仓库被 settings.xml 重定向到 Maven 目录下的 `MVN_repo`（**不是** `~/.m2`）
- 必须**离线编译**（`-o`）；**不要用 `clean`**（maven-clean-plugin 不在本地仓库，会失败）
- 路径含空格，脚本内一律加引号
- 构建工具固定路径：
  - JDK：`E:/develop/java/jdk-21`
  - Maven：`E:/develop/apache-maven-3.9.12-bin/apache-maven-3.9.12/bin/mvn`

```bash
# 0. 首次/改动共享库后：安装 ma-parent + base.web/base.jwt 到 MVN_repo
cd "F:/myInterestingProgram/Middleware Arena/middleware-arena-base"
JAVA_HOME="E:/develop/java/jdk-21" "E:/develop/apache-maven-3.9.12-bin/apache-maven-3.9.12/bin/mvn" -o -q install -DskipTests

# 1. 编译某个服务（快速验证）
cd "F:/myInterestingProgram/Middleware Arena/middleware-arena-auth"
JAVA_HOME="E:/develop/java/jdk-21" "E:/develop/apache-maven-3.9.12-bin/apache-maven-3.9.12/bin/mvn" -o -q compile -DskipTests

# 2. 打可执行 jar（Dockerfile 构建前）
JAVA_HOME="E:/develop/java/jdk-21" "E:/develop/apache-maven-3.9.12-bin/apache-maven-3.9.12/bin/mvn" -o -q package -DskipTests
```

修改 `middleware-arena-base`（父 pom / base.web / base.jwt）后，**必须先重新 `install` 它**，其它服务才能编译通过。

## 基础设施与启动

```bash
# 基础模式：mysql/redis/nacos/gateway/auth（内存预算 2~3GB）
cd middleware-arena-base
docker compose --profile base up -d --build
# 或 powershell: .\scripts\start-base.ps1   全部服务: .\scripts\start-full.ps1   停止: .\scripts\stop.ps1
```

- 服务注册到 Nacos（`NACOS_ADDR` 默认 `127.0.0.1:8848`），网关按 `lb://服务名` 转发
- 网关：`http://localhost:8000`，Nacos 控制台：`http://localhost:8848/nacos`
- 开发态 MySQL 默认 root/root123，库名 `middleware_arena`

## 前端 / Agent

```bash
cd middleware-arena-frontend
npm install
npm run dev     # Vite dev，端口 5173
npm run build   # vue-tsc -b && vite build
```

```bash
cd middleware-arena-agent        # Python FastAPI，端口 9500
.\.venv\Scripts\Activate.ps1
pip install -r requirements.txt
uvicorn app.main:app --reload --port 9500
```

`middleware_arena_front_demo/` 是独立静态 HTML 演示页（非 Vite 项目，直接浏览器打开即可），与前端工程无关。

## 服务架构与模块约定

每个服务目录（`middleware-arena-xxx`）是独立 Maven 项目，子模块固定分层：

| 子模块 | 职责 |
|---|---|
| `*.web` | Spring Boot 入口（`XxxApplication`）+ Controller，含 `application.yml` |
| `*.biz` | Service 接口 + `impl/` 实现（业务逻辑） |
| `*.domain` | 数据库实体（MyBatis-Plus，只依赖 `mybatis-plus-annotation`，不带 jdbc） |
| `*.dto` | 请求/响应对象（`request/`、`response/`，配 springdoc `@Schema`） |
| `*.mapper` | MyBatis-Plus Mapper 接口 |
| 附加 | `*.feign`（跨服务调用）、`*.config`、`*.mq`（RabbitMQ）等按需 |

**依赖版本规则**：兄弟子模块之间引用**不写 version**（由服务根 pom 的 `dependencyManagement` 统一管理）；跨项目依赖（如 `base.web`、`base.jwt`）必须写 version，版本号在 `ma-parent` 锁定。

**新增 Maven 模块**：先在服务根 pom 的 `<modules>` + `<dependencyManagement>` 注册，再写子模块。

服务名与端口（Nacos 注册名 = `-service` 后缀）：

| 服务 | 端口 | 服务名 |
|---|---|---|
| gateway | 8000 | gateway |
| auth | 9001 | auth-service |
| community | 9002 | community-service |
| experiment | 9003 | experiment-service |
| runner | 9004 | runner-service |
| notification | 9005 | notification-service |
| order | 9006 | order-service |
| storage | 9007 | storage-service |
| account | 9008 | account-service |
| agent | 9500 | -（Python，非 Nacos） |

**新服务接入三件套**：网关 `application.yml` 加 route → `base/docker-compose.yml` 加 service → 根 README 仓库地图登记。

## 关键实现细节

- **配置约定**：各服务 `application.yml` 位于 `<svc>.web/src/main/resources/`；Nacos、MySQL、Redis 地址均用环境变量可覆盖（`NACOS_ADDR` / `MYSQL_ADDR` / `MYSQL_PASSWORD` / `REDIS_HOST` / `REDIS_PORT`）；敏感配置（如 `ma.jwt.secret`）生产用环境变量覆盖，代码里只留开发默认值。
- **数据库**：每个服务 `sql/init.sql` 用 `CREATE TABLE IF NOT EXISTS`，utf8mb4；MyBatis-Plus 默认驼峰↔下划线自动映射，无需手写 resultMap。
- **共享库**：`base.web` 提供统一响应体 / 业务异常 / 全局异常处理（自动装配）；`base.jwt` 提供 JWT + 内部鉴权 `InternalAuthSigner`；`TransmittableThreadLocal` 用于异步场景透传用户上下文。
- **参考实现**：`middleware-arena-experiment` 是目前业务代码最完整的服务（含 `experiment.config`、`experiment.mq` RabbitMQ、SSE），做新功能时先看它的实现风格。
- **Swagger**：springdoc，路径 `/swagger-ui.html`。

## 工作习惯约束

- 重构移动文件用 `git mv`（保留历史）；**删除文件先确认**，不要直接 rm。
- 所有 pom/配置改动后必须用上面离线命令实测编译，不要声称“应该能编译”。
- 前端改 axios/Pinia 时注意双 token 流程是 TODO，登录态尚未真正打通。
