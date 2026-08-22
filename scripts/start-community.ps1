# 使用 JDK 21 启动 community-service。
$workspaceDir = Split-Path -Parent $PSScriptRoot
$communityWebDir = Join-Path $workspaceDir "middleware-arena-community\community.web"
$logDir = Join-Path $workspaceDir "logs"
$sentinelLogDir = Join-Path $logDir "sentinel-community"
$mavenSettings = Join-Path $PSScriptRoot "maven-settings-central.xml"

$env:JAVA_HOME = "E:\develop\java\jdk-21"
$env:Path = "$env:JAVA_HOME\bin;" + $env:Path
$env:JAVA_TOOL_OPTIONS = "-Dfile.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8 " +
    "-Dcsp.sentinel.log.dir=`"$sentinelLogDir`" -Dcsp.sentinel.log.use.pid=true"

New-Item -ItemType Directory -Path $logDir -Force | Out-Null
New-Item -ItemType Directory -Path $sentinelLogDir -Force | Out-Null
Set-Location $communityWebDir

$proc = Start-Process -FilePath "E:\develop\apache-maven-3.9.12-bin\apache-maven-3.9.12\bin\mvn.cmd" `
    -ArgumentList "-s","`"$mavenSettings`"","spring-boot:run","-DskipTests" `
    -RedirectStandardOutput (Join-Path $logDir "community-restart.log") `
    -RedirectStandardError (Join-Path $logDir "community-restart.err") `
    -PassThru -WindowStyle Hidden
Write-Host "Started community mvn PID=$($proc.Id)"
