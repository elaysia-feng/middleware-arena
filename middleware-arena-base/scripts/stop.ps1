# 停止 Middleware Arena 全部容器
Set-Location (Split-Path $PSScriptRoot -Parent)
docker compose down
