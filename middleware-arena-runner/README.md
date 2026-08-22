# middleware-arena-runner

Runner 服务（端口 `9004`）消费实验任务，并按以下流水线执行：

```mermaid
flowchart LR
    A[RabbitMQ 任务] --> B[资源调度]
    B --> C[构建 Candidate 镜像]
    C --> D[创建独立 Docker 网络]
    D --> E[启动中间件与 SUT 容器]
    E --> F[健康检查]
    F --> G[k6 压测]
    G --> H[采集指标]
    H --> I[MQ 回传结果]
    I --> J[清理容器、网络和 Candidate 镜像]
```

任务使用手动 ACK、重试和 DLQ；阶段状态通过 `experiment.task.status.exchange` 回传。每个任务有独立网络、CPU/内存硬限制和可取消的本地 Future，清理逻辑在成功、失败和取消时都会执行。

真实执行依赖 Docker daemon、模板工作目录、离线 Maven/JDK 和 k6 镜像。Windows 本机需启动 Docker Desktop，并通过 `DOCKER_HOST` 指向可用 daemon。
