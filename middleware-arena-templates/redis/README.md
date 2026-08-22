# Redis 订单详情缓存实验

对比订单详情直接查询 MySQL 与 Caffeine + Redis Cache-Aside 两种读取路径。

## 宿主与接口

- 宿主：`middleware-arena-order`
- 接口：`GET /order/{orderId}`
- 基线组：关闭本地缓存和 Redis，直接读取 MySQL
- 实验组：本地 Caffeine → Redis → MySQL，Redis 故障时 Fail-open 回源 MySQL

## 可编辑白名单

- `order.biz/.../OrderServiceImpl.java`
- `order.config/.../RedisConfig.java`
- `order.config/.../OrderCacheConfig.java`
- `order.web/src/main/resources/application.yml`

Controller、DTO、Mapper、鉴权和数据库 DDL 不允许编辑，防止通过改接口或跳过归属校验伪造性能结果。

## 默认压测参数

- 并发阶梯：`20 / 50 / 100`
- 每阶持续：`30s`
- 预热：`15s`
- 订单热点比例：80% 请求访问 20% 订单
- 请求超时：`3s`

## 指标与验收

- QPS、P50/P95/P99、错误率
- MySQL 查询量、Redis 命中率、本地缓存命中率
- Redis 连接数、JVM 堆与 GC
- 验收要求：业务响应一致；缓存不可用时接口仍可回源；实验报告明确命中率与 P95 的对应关系
