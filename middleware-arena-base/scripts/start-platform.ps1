# 启动共用中间件（Redis / RabbitMQ / Nacos）
Set-Location (Split-Path $PSScriptRoot -Parent)
docker compose up -d
docker compose ps
