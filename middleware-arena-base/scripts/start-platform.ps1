# 启动平台模式（长期运行：mysql / redis / rabbitmq / nacos / gateway / auth / experiment / runner）
# 实验环境（实验 Redis/MQ/ES/Seata/SUT/k6）不常驻，由 Runner 按任务临时起/删容器。
Set-Location (Split-Path $PSScriptRoot -Parent)
docker compose --profile platform up -d --build
docker compose ps
