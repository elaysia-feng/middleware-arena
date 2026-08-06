# 启动全部服务（演示用，先确保各服务已 mvn package 构建）
Set-Location (Split-Path $PSScriptRoot -Parent)
docker compose --profile full up -d --build
docker compose ps
