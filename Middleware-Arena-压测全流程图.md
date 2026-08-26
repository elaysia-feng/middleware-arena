# Middleware Arena 压测全流程图

## 一、整体压测流程

```mermaid
flowchart TD
    A[用户进入实验场景] --> B[选择或创建实验模板]
    B --> C[编辑代码文件]
    C --> D[配置运行参数]
    D --> D1[并发梯度]
    D --> D2[压测时长]
    D --> D3[请求体和请求头]
    D --> D4[选择压测接口]
    D4 --> E[保存实验版本]
    E --> E1[保存代码快照]
    E --> E2[保存 runParamsJson]
    E --> E3[上传 OSS]
    E3 --> F[提交压测任务]
    F --> G[experiment-service 创建 task]
    G --> H[写入 experiment_task]
    H --> I[RabbitMQ 投递 RunnerTaskMessage]
    I --> J[Runner 接收任务]
    J --> K[资源调度]
    K --> K1{CPU、内存、并发是否足够}
    K1 -- 否 --> K2[FIFO 队列等待]
    K2 --> K
    K1 -- 是 --> L[预留资源]
    L --> M[BUILDING 构建 SUT 镜像]
    M --> M1[下载 OSS 版本文件]
    M1 --> M2[Maven 编译]
    M2 --> M3[构建 candidate Docker 镜像]
    M3 --> N[RUNNING 启动实验环境]
    N --> N1[创建任务网络]
    N1 --> N2[复用已有 MySQL/Redis]
    N2 --> N3[启动 SUT 容器]
    N3 --> O[WAITING_HEALTH]
    O --> O1[访问 /actuator/health]
    O1 --> O2{健康检查通过?}
    O2 -- 否 --> O3[读取 SUT 容器日志并失败回传]
    O2 -- 是 --> P[BENCHMARKING]
    P --> P1[生成 k6 脚本]
    P1 --> P2[启动 k6 容器]
    P2 --> P3[执行接口压测]
    P3 --> P4[记录延迟、吞吐量和错误率]
    P4 --> Q[COLLECTING]
    Q --> Q1[采集容器 CPU/内存指标]
    Q1 --> Q2[生成 metricsJson]
    Q2 --> R[CLEANING]
    R --> R1[删除 SUT 容器]
    R1 --> R2[删除任务网络]
    R2 --> R3[删除 candidate 镜像]
    R3 --> R4[释放 CPU、内存和并发槽位]
    R4 --> S[回传最终结果]
    S --> T[experiment-service 更新任务状态]
    T --> U[前端展示结果]
```

## 二、资源分配

```mermaid
flowchart TD
    A[Runner 收到任务] --> B[识别实验类型]
    B --> C[计算任务资源需求]
    C --> C1[中间件容器 CPU/内存]
    C --> C2[SUT CPU/内存]
    C --> C3[k6 CPU/内存]
    C --> C4[Maven 构建资源]
    C --> C5[Runner 额外开销]
    C1 --> D[统一资源检查]
    C2 --> D
    C3 --> D
    C4 --> D
    C5 --> D
    D --> E{槽位、CPU、内存同时满足?}
    E -- 否 --> F[加入 FREE/VIP FIFO 队列]
    F --> D
    E -- 是 --> G[原子登记 Reservation]
    G --> H[允许构建和运行]
    H --> I[任务结束/失败/取消]
    I --> J[归还槽位、CPU、内存]
```

当前默认平台容量：

```text
最大并发：3 个任务
最大 CPU：4 核
系统保留：0.5 核
可调度 CPU：3.5 核
最大内存：8192 MB
系统保留：1024 MB
可调度内存：7168 MB
```

Redis 实验当前预留约 `2 核 CPU + 2048 MB 内存`。

## 三、共享基础设施

```mermaid
flowchart LR
    R[本机 Runner] --> D[本机 Docker Desktop]
    D --> S[SUT 容器]
    S --> M[host.docker.internal:3306]
    M --> DB[(本机 MySQL)]
    S --> REDIS[192.168.1.177:6379]
    S --> NACOS[192.168.1.177:8848 Nacos]
    S --> MQ[192.168.1.177:5672 RabbitMQ]
```

当前 SUT 会使用：

```text
MySQL：本机 benchmark 数据库
Redis：192.168.1.177 上已有 Redis
Nacos：192.168.1.177:8848
RabbitMQ：192.168.1.177:5672
SUT 端口：8080
```

## 四、压测数据库

```mermaid
flowchart LR
    A[压测数据空间] --> B[ma_order_benchmark]
    A --> C[ma_account_benchmark]
    A --> D[ma_storage_benchmark]
    B --> B1[order]
    B --> B2[undo_log]
    C --> C1[account_balance]
    C --> C2[undo_log]
    D --> D1[stock]
    D --> D2[undo_log]
```

当前已准备：

```text
用户 ID：4
账户余额：100000000 分
商品 ID：1
商品库存：1000000
```

正式库不会被压测任务直接使用：

```text
正式库：ma_order / ma_account / ma_storage
压测库：ma_order_benchmark / ma_account_benchmark / ma_storage_benchmark
```

## 五、创建订单、扣库存、扣余额

```mermaid
sequenceDiagram
    participant K6 as k6
    participant O as order-service SUT
    participant P as product-service
    participant S as storage-service
    participant A as account-service
    participant OD as ma_order_benchmark
    participant SD as ma_storage_benchmark
    participant AD as ma_account_benchmark
    participant R as Redis

    K6->>O: POST /order/create
    O->>R: SETNX 幂等键
    O->>P: 查询商品价格
    P-->>O: 商品快照
    O->>OD: 创建订单
    O->>S: POST /storage/deduct
    S->>SD: quantity >= 请求数量
    SD-->>S: 原子扣减库存
    S-->>O: 扣库存成功
    O->>A: POST /account/deduct
    A->>AD: balance >= 订单金额
    AD-->>A: 原子扣减余额
    A-->>O: 扣款成功
    O-->>K6: 返回订单
```

库存不足或余额不足时，由 Seata AT 回滚订单、库存和账户变更。

## 六、Nacos、RabbitMQ、Redis 职责

```mermaid
flowchart TD
    E[experiment-service] --> MQ[RabbitMQ]
    MQ --> R[runner-service]
    R --> MQ
    MQ --> E

    Gateway[Gateway] --> N[Nacos]
    N --> Runner[runner-service]
    N --> Order[order-service]
    N --> Account[account-service]
    N --> Storage[storage-service]
    N --> Product[product-service]

    R --> Redis[Redis]
    Redis --> R1[任务实例状态]
    Redis --> R2[幂等键]
    Redis --> R3[缓存和资源调度状态]
```

```text
RabbitMQ：任务投递、进度回传、最终结果回传
Nacos：服务注册与服务发现
Redis：缓存、幂等键、任务状态、资源状态
MySQL：实验任务、订单、余额、库存持久化
Docker：SUT 和 k6 容器运行环境
```

## 七、基线版本和候选版本

```mermaid
flowchart TD
    A[同一套压测参数] --> B[相同 benchmark 初始数据]
    B --> C[Baseline 版本]
    B --> D[Candidate 版本]
    C --> C1[相同接口]
    C --> C2[相同并发和时长]
    C --> C3[采集基线指标]
    D --> D1[相同接口]
    D --> D2[相同并发和时长]
    D --> D3[采集候选指标]
    C3 --> E[结果对比]
    D3 --> E
    E --> E1[平均延迟]
    E --> E2[P95/P99]
    E --> E3[吞吐量]
    E --> E4[错误率]
    E --> E5[CPU 使用率]
    E --> E6[内存使用率]
```

核心原则：Baseline 和 Candidate 必须使用相同的接口、请求参数、并发梯度以及初始账户、库存和数据库状态。
