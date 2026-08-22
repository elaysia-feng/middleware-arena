# 社区点赞验证与 JMeter 压测

## 1. 启动社区服务

```powershell
.\scripts\start-community.ps1
```

启动日志：

- `logs/community-restart.log`
- `logs/community-restart.err`

健康检查应返回 `UP`：

```powershell
Invoke-RestMethod http://127.0.0.1:9002/actuator/health
```

## 2. 先跑功能验证

该脚本会演示“取消到基线 → 点赞 → 重复点赞”，并断言状态、计数和 PUT 幂等性：

```powershell
.\scripts\test-community-like.ps1 -PostId 1 -UserId 900001
```

脚本默认直连 community-service，并按项目内部协议生成 HMAC 身份签名，不需要手工复制 JWT。
如已通过环境变量配置了 `MA_INTERNAL_AUTH_SECRET`，脚本会自动使用同一密钥。

## 3. 运行 JMeter

小规模冒烟：

```powershell
& "E:\develop\Jmeter\apache-jmeter-5.6.3\bin\jmeter.bat" `
  -n `
  -t ".\jmeter\like-stress-test.jmx" `
  -Jthreads=2 `
  -Jloops=3 `
  -JrampUp=1 `
  -JpostIdMin=1 `
  -JpostIdMax=1 `
  -l ".\jmeter\like-smoke.jtl" `
  -j ".\jmeter\like-smoke.log"
```

默认压测为 20 线程、10 秒升压、每线程 50 次。可用以下参数覆盖：

| 参数 | 默认值 | 说明 |
|---|---:|---|
| `host` | `127.0.0.1` | community-service 地址 |
| `port` | `9002` | community-service 端口 |
| `threads` | `20` | 并发线程数 |
| `loops` | `50` | 每线程循环次数 |
| `rampUp` | `10` | 升压秒数 |
| `postIdMin` | `1` | 随机帖子 ID 下限 |
| `postIdMax` | `100` | 随机帖子 ID 上限 |
| `userIdBase` | `910000` | 每个线程的用户 ID 起点 |
| `internalAuthSecret` | 本地默认密钥 | 环境密钥不同时覆盖 |

测试计划会同时断言：

- HTTP 状态码为 200；
- JSON 业务字段 `code` 为 200，避免把统一异常响应误判为成功；
- 每个线程使用独立用户 ID，并动态生成内部身份签名。

## 4. 看完整效果

压测后不要只看 JMeter 的错误率，还要检查：

1. `GET /community/post/{postId}/like/status`：Redis 实时状态和计数；
2. RabbitMQ：`like.persist.queue`、`like.statistics.queue` 最终回到 0；
3. MySQL：对应物理分片 `post_like_0..3` 出现 `liked/version` 记录；
4. `logs/community-restart.log`：无参数映射、ShardingSphere 路由、MyBatis SQL 或 RabbitMQ 消费异常。
