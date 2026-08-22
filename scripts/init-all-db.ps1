# Middleware Arena 一次性建库脚本
# 用法：在 PowerShell 里执行  .\scripts\init-all-db.ps1
# 功能：建 4 个库 + 跑全部 9 个服务的 init.sql + community 分片物理表

$ErrorActionPreference = "Stop"
$root = "F:\myInterestingProgram\Middleware Arena"
$mysql = "mysql -h 127.0.0.1 -P 3306 -uroot -p'@Feng050813'"

Write-Host "=== 1. 建数据库 ===" -ForegroundColor Cyan
& $mysql -e @"
CREATE DATABASE IF NOT EXISTS middleware_arena   DEFAULT CHARACTER SET utf8mb4;
CREATE DATABASE IF NOT EXISTS ma_community       DEFAULT CHARACTER SET utf8mb4;
CREATE DATABASE IF NOT EXISTS ma_community_ds0   DEFAULT CHARACTER SET utf8mb4;
CREATE DATABASE IF NOT EXISTS ma_community_ds1   DEFAULT CHARACTER SET utf8mb4;
"@

Write-Host "=== 2. 跑 init.sql ===" -ForegroundColor Cyan
# 共享库 middleware_arena：auth / product / order / storage / account / experiment / notification
& $mysql middleware_arena < "$root\middleware-arena-auth\sql\init.sql"
& $mysql middleware_arena < "$root\middleware-arena-product\sql\init.sql"
& $mysql middleware_arena < "$root\middleware-arena-order\sql\init.sql"
& $mysql middleware_arena < "$root\middleware-arena-storage\sql\init.sql"
& $mysql middleware_arena < "$root\middleware-arena-account\sql\init.sql"
& $mysql middleware_arena < "$root\middleware-arena-experiment\sql\init.sql"
& $mysql middleware_arena < "$root\middleware-arena-notification\sql\init.sql"

# community 用 ma_community（主）+ ma_community_ds0/ds1（分片）
& $mysql ma_community < "$root\middleware-arena-community\sql\init.sql"

Write-Host "=== 3. community 分片物理表（ds0 + ds1 各 4 张） ===" -ForegroundColor Cyan
$shardDDL = @"
CREATE TABLE IF NOT EXISTS post_like_0    LIKE ma_community.post_like;
CREATE TABLE IF NOT EXISTS post_like_1    LIKE ma_community.post_like;
CREATE TABLE IF NOT EXISTS post_like_2    LIKE ma_community.post_like;
CREATE TABLE IF NOT EXISTS post_like_3    LIKE ma_community.post_like;
CREATE TABLE IF NOT EXISTS event_outbox_0 LIKE ma_community.event_outbox;
CREATE TABLE IF NOT EXISTS event_outbox_1 LIKE ma_community.event_outbox;
CREATE TABLE IF NOT EXISTS event_outbox_2 LIKE ma_community.event_outbox;
CREATE TABLE IF NOT EXISTS event_outbox_3 LIKE ma_community.event_outbox;
"@
& $mysql ma_community_ds0 -e $shardDDL
& $mysql ma_community_ds1 -e $shardDDL

Write-Host "=== 4. 验收 ===" -ForegroundColor Cyan
Write-Host "[middleware_arena]" -ForegroundColor Yellow
& $mysql middleware_arena   -e "SHOW TABLES;"
Write-Host "[ma_community]"      -ForegroundColor Yellow
& $mysql ma_community       -e "SHOW TABLES;"
Write-Host "[ma_community_ds0]"  -ForegroundColor Yellow
& $mysql ma_community_ds0   -e "SHOW TABLES;"
Write-Host "[ma_community_ds1]"  -ForegroundColor Yellow
& $mysql ma_community_ds1   -e "SHOW TABLES;"

Write-Host "`n=== 完成 ===" -ForegroundColor Green