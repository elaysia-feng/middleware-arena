param(
    [string]$JdkHome = 'E:\develop\java\jdk-21'
)

$ErrorActionPreference = 'Stop'
$javaExe = Join-Path $JdkHome 'bin\java.exe'
if (-not (Test-Path -LiteralPath $javaExe)) {
    throw "JDK 21 不存在：$javaExe"
}

$oldJavaEntries = @(
    'E:\develop\java\jdk-17\bin',
    'E:\develop\java\jdk-21\bin',
    '%JAVA_HOME%\bin'
)

$machinePath = [Environment]::GetEnvironmentVariable('Path', 'Machine')
$machineEntries = @($machinePath -split ';' | Where-Object {
    $_ -and $_ -notin $oldJavaEntries
})
$newMachinePath = (@('%JAVA_HOME%\bin') + $machineEntries) -join ';'

[Environment]::SetEnvironmentVariable('JAVA_HOME', $JdkHome, 'Machine')
[Environment]::SetEnvironmentVariable('Path', $newMachinePath, 'Machine')
[Environment]::SetEnvironmentVariable('JAVA_HOME', $JdkHome, 'User')

$userPath = [Environment]::GetEnvironmentVariable('Path', 'User')
$userEntries = @($userPath -split ';' | Where-Object {
    $_ -and $_ -notin $oldJavaEntries
})
[Environment]::SetEnvironmentVariable('Path', ((@('%JAVA_HOME%\bin') + $userEntries) -join ';'), 'User')

Write-Output "JAVA_HOME 已切换为 $JdkHome"
