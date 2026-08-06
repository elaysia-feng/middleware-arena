# Redis 缓存实验模板（TODO）

> 第一版闭环目标：用户 Fork → 修改 `ProductService.java`（加/不加 Redis 缓存）→ 压测对比。

## 可编辑白名单文件（TODO）
- `ProductService.java`（加不加缓存）
- `RedisConfig.java`
- `application.yml`（线程池 / 连接池参数）

## 对比指标
QPS / P95 / 错误率 / MySQL 查询量 / 缓存命中率 / JVM 堆

## 宿主服务
TODO：实现时提供 ProductController / ProductService / ProductMapper 的模板源码，
并在 Runner 实验配置中声明「基线 = 无缓存，修改后 = 用户编辑版本」。
