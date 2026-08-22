param(
    [string]$BaseUrl = "http://127.0.0.1:9002",
    [long]$PostId = 1,
    [long]$UserId = 900001,
    [string]$Username = "like-demo",
    [string]$InternalAuthSecret = $(if ($env:MA_INTERNAL_AUTH_SECRET) { $env:MA_INTERNAL_AUTH_SECRET } else { "middleware-arena-internal-token" })
)

$ErrorActionPreference = "Stop"

function New-InternalAuthHeaders {
    $timestamp = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds().ToString()
    $payload = "$UserId&$Username&$timestamp"
    $hmac = [System.Security.Cryptography.HMACSHA256]::new(
        [System.Text.Encoding]::UTF8.GetBytes($InternalAuthSecret)
    )
    try {
        $signBytes = $hmac.ComputeHash([System.Text.Encoding]::UTF8.GetBytes($payload))
        $sign = [Convert]::ToHexString($signBytes).ToLowerInvariant()
    }
    finally {
        $hmac.Dispose()
    }

    return @{
        "X-User-Id" = $UserId.ToString()
        "X-Username" = $Username
        "X-Timestamp" = $timestamp
        "X-Sign" = $sign
    }
}

function Invoke-LikeApi {
    param(
        [ValidateSet("GET", "PUT", "DELETE")]
        [string]$Method,
        [string]$Path
    )

    $response = Invoke-RestMethod `
        -Uri "$BaseUrl$Path" `
        -Method $Method `
        -Headers (New-InternalAuthHeaders) `
        -TimeoutSec 10

    if ($response.code -ne 200) {
        throw "$Method $Path 业务失败：code=$($response.code)，message=$($response.message)"
    }
    return $response
}

function Get-LikeStatus {
    return (Invoke-LikeApi -Method GET -Path "/community/post/$PostId/like/status").data
}

$ping = Invoke-RestMethod -Uri "$BaseUrl/community/ping" -TimeoutSec 5
if ($ping.code -ne 200 -or $ping.data -ne "pong") {
    throw "community-service 未就绪：$($ping | ConvertTo-Json -Compress)"
}

Write-Host "社区服务已就绪：$BaseUrl" -ForegroundColor Green
Write-Host "测试对象：postId=$PostId, userId=$UserId"

# 先取消一次，确保演示从未点赞状态开始；该接口本身是幂等的。
Invoke-LikeApi -Method DELETE -Path "/community/post/$PostId/like" | Out-Null
$before = Get-LikeStatus

Invoke-LikeApi -Method PUT -Path "/community/post/$PostId/like" | Out-Null
$afterLike = Get-LikeStatus

# 重复点赞必须成功，但计数不能再次增加。
Invoke-LikeApi -Method PUT -Path "/community/post/$PostId/like" | Out-Null
$afterDuplicate = Get-LikeStatus

if ($before.liked -ne $false) {
    throw "前置状态异常：取消点赞后 liked 应为 false"
}
if ($afterLike.liked -ne $true) {
    throw "点赞状态异常：点赞后 liked 应为 true"
}
if ([long]$afterLike.likeCount -ne ([long]$before.likeCount + 1)) {
    throw "点赞计数异常：期望 $([long]$before.likeCount + 1)，实际 $($afterLike.likeCount)"
}
if ([long]$afterDuplicate.likeCount -ne [long]$afterLike.likeCount) {
    throw "幂等性异常：重复点赞导致计数再次变化"
}

[pscustomobject]@{
    阶段 = "点赞前"
    已点赞 = $before.liked
    点赞数 = $before.likeCount
}, [pscustomobject]@{
    阶段 = "首次点赞后"
    已点赞 = $afterLike.liked
    点赞数 = $afterLike.likeCount
}, [pscustomobject]@{
    阶段 = "重复点赞后"
    已点赞 = $afterDuplicate.liked
    点赞数 = $afterDuplicate.likeCount
} | Format-Table -AutoSize

Write-Host "点赞状态、计数与幂等性验证通过。最终保留为已点赞状态。" -ForegroundColor Green
