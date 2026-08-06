# Middleware Arena — 实验模板（占位目录）

存放各实验的**宿主服务代码模板**。runner-service 构建实验时，用「模板 + 用户 Diff」生成目标代码。

**第一版策略**：固定模板 + 指定文件可修改（白名单），不支持用户上传任意工程。
**完整代码沙箱：TODO**（后续迭代）。

```
redis/           # Redis 缓存实验模板（TODO）
rabbitmq/        # RabbitMQ 异步下单实验模板（TODO）
seata/           # Seata 分布式事务实验模板（TODO）
elasticsearch/   # ES 全文搜索实验模板（TODO）
```

每个目录下的 README 说明：可编辑白名单文件、宿主服务、对比指标、默认压测参数。
