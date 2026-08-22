# middleware-arena-community

社区微服务，默认端口 `9002`。

## 已实现功能

- 帖子发布、编辑、删除、详情缓存和分页列表
- 一级评论、回复、分页、作者删除及帖子评论数维护
- 点赞：Redis Lua 原子状态/计数/version + Redis Stream Outbox + RabbitMQ + MySQL 分片持久化 + 热榜统计
- 收藏：与点赞一致的幂等目标状态、Redis Stream Outbox、RabbitMQ 和 version 防乱序落库
- 关注、取消关注和关注状态查询
- 标题/正文关键词搜索；未配置 Elasticsearch 时使用 MySQL 搜索
- ShardingSphere-JDBC 数据源、内部身份 HMAC 验签、Actuator 健康检查

## 启动与验证

在仓库根目录执行：

```powershell
.\scripts\start-community.ps1
.\scripts\test-community-features.ps1
```

完整验证脚本会创建一条演示帖子，并验证帖子、评论、点赞、收藏、关注和搜索闭环。测试帖子会保留，便于继续查看接口效果。

点赞压测说明见 `jmeter/README.md`。

## 数据库迁移

- 全新数据库执行 `sql/init.sql`
- 已有点赞表执行 `sql/upgrade_like_shards_to_state.sql`
- 已有收藏表执行 `sql/upgrade_favorite_state.sql`

收藏和点赞均为最终一致链路。接口成功表示 Redis 状态已原子写入；RabbitMQ 消费完成后，MySQL 事实状态和帖子聚合计数会更新。
