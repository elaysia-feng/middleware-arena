# 启动全部共用中间件
Set-Location (Split-Path $PSScriptRoot -Parent)
docker compose up -d
docker compose ps
