# Middleware Arena — Base（共享基础设施）

本仓库承载：**共享父 POM（版本矩阵）**、**共享库 base.web / base.jwt**、**Docker Compose（按场景启停）**。

## 仓库地图

| 仓库 | 说明 |
|---|---|
| `middleware-arena-base` | ★ 本仓库：父 POM / base.web / base.jwt / compose / 脚本 |
| `middleware-arena-gateway` | Spring Cloud Gateway（路由 / CORS / 过滤器占位） |
| `middleware-arena-auth` | 登录 / JWT / 权限（**双 token 登录：TODO**） |
| `middleware-arena-community` | 社区（帖子/评论/点赞/收藏：TODO） |
| `middleware-arena-experiment` | 实验模板 / 版本 / 任务（TODO） |
| `middleware-arena-runner` | 压测流水线（TODO） |
| `middleware-arena-notification` | 通知（TODO） |
| `middleware-arena-order` / `storage` / `account` | 业务服务（实验宿主，骨架） |
| `middleware-arena-templates` | 实验模板占位目录 |
| `middleware-arena-agent` | Python AI Agent 骨架 |
| `middleware-arena-frontend` | Vue3 + Element Plus 骨架 |

## 前置条件

- JDK **21**（本机在 `E:\develop\java\jdk-21`，默认 17 需要切换）
- Maven 3.9+
- Docker Desktop（WSL2 后端）+ Docker Compose
- Node 20+（前端）

## 首次构建

```powershell
# 1. 用 JDK21 安装父 POM + base.web/base.jwt（所有服务都依赖它）
$env:JAVA_HOME = 'E:\develop\java\jdk-21'
cd middleware-arena-base
mvn install

# 2. 逐个构建服务（也可在 IDE 里直接打开）
cd ..\middleware-arena-auth;   mvn clean package -DskipTests
cd ..\middleware-arena-gateway; mvn clean package -DskipTests
```

## 启动（按 16GB 内存分场景）

```powershell
# 基础模式：mysql / redis / nacos / gateway / auth（约 2~3GB）
.\scripts\start-base.ps1

# 全部服务（演示用）
.\scripts\start-full.ps1

# 停止
.\scripts\stop.ps1
```

启动后：

- Nacos 控制台：http://localhost:8848/nacos
- 网关：http://localhost:8000
- auth：http://localhost:9001（Swagger: /swagger-ui.html）

## 构建策略

- 主机 Maven（JDK21）增量构建 → Dockerfile 打镜像。
- `.m2` 建议用命名卷缓存，避免每次全量下载依赖。

## 版本矩阵

见 [pom.xml](./pom.xml) 顶部 `properties`。升级组件版本只需改这一处，然后全仓回归。

## TODO 总览

见根目录 `PROJECT_PLAN.md`（或各仓库 `README.md` 中的 TODO 注释）。
