# 启动基础模式（mysql / redis / nacos / gateway / auth）
Set-Location (Split-Path $PSScriptRoot -Parent)
docker compose --profile base up -d --build
docker compose ps
