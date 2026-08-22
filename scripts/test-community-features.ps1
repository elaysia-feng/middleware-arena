param(
    [string]$BaseUrl = "http://127.0.0.1:9002",
    [long]$UserId = 920001,
    [long]$FollowUserId = 930001,
    [string]$Username = "community-demo",
    [string]$InternalAuthSecret = $(if ($env:MA_INTERNAL_AUTH_SECRET) { $env:MA_INTERNAL_AUTH_SECRET } else { "middleware-arena-internal-token" })
)

$ErrorActionPreference = "Stop"

function New-InternalAuthHeaders {
    # 直连服务端口时模拟网关身份透传；每个请求使用新时间戳，避免重放窗口问题。
    $timestamp = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds().ToString()
    $payload = "$UserId&$Username&$timestamp"
    $hmac = [System.Security.Cryptography.HMACSHA256]::new(
        [System.Text.Encoding]::UTF8.GetBytes($InternalAuthSecret)
    )
    try {
        $sign = [Convert]::ToHexString(
            $hmac.ComputeHash([System.Text.Encoding]::UTF8.GetBytes($payload))
        ).ToLowerInvariant()
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

function Invoke-CommunityApi {
    param(
        [ValidateSet("GET", "POST", "PUT", "DELETE")]
        [string]$Method,
        [string]$Path,
        [object]$Body
    )

    $parameters = @{
        Uri = "$BaseUrl$Path"
        Method = $Method
        Headers = New-InternalAuthHeaders
        TimeoutSec = 10
    }
    if ($null -ne $Body) {
        $parameters.ContentType = "application/json; charset=utf-8"
        $parameters.Body = $Body | ConvertTo-Json -Depth 5 -Compress
    }

    $response = Invoke-RestMethod @parameters
    # GlobalExceptionHandler 可能仍返回 HTTP 200，因此必须继续断言业务 code。
    if ($response.code -ne 200) {
        throw "$Method $Path 业务失败：code=$($response.code)，message=$($response.message)"
    }
    return $response
}

function Assert-True([bool]$Condition, [string]$Message) {
    if (-not $Condition) {
        throw $Message
    }
}

$ping = Invoke-RestMethod -Uri "$BaseUrl/community/ping" -TimeoutSec 5
Assert-True ($ping.code -eq 200 -and $ping.data -eq "pong") "community-service 未就绪"

# 1. 帖子创建、编辑、详情、分页和搜索。
$suffix = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
$created = (Invoke-CommunityApi POST "/community/post/create" @{
    title = "社区功能验证-$suffix"
    content = "帖子、评论、点赞、收藏、关注与搜索闭环验证"
}).data
$postId = [long]$created.id
Assert-True ($postId -gt 0) "发帖未返回有效 postId"

$updated = (Invoke-CommunityApi PUT "/community/post/$postId" @{
    title = "社区功能验证-$suffix-已编辑"
    content = "完整社区功能验证关键字-$suffix"
}).data
Assert-True ($updated.title -like "*-已编辑") "编辑帖子未生效"

$detail = (Invoke-CommunityApi GET "/community/post/$postId" $null).data
$page = (Invoke-CommunityApi GET "/community/post/page?page=1&size=10" $null).data
$search = (Invoke-CommunityApi GET "/community/search?keyword=$suffix&page=1&size=10" $null).data
Assert-True ($detail.id -eq $postId) "帖子详情查询失败"
Assert-True (($page | Where-Object { $_.id -eq $postId }).Count -eq 1) "帖子分页未包含新帖子"
Assert-True (($search | Where-Object { $_.id -eq $postId }).Count -eq 1) "搜索未命中新帖子"

# 2. 一级评论、回复及两个分页接口。
$comment = (Invoke-CommunityApi POST "/community/post/$postId/comment" @{ content = "一级评论" }).data
$reply = (Invoke-CommunityApi POST "/community/post/$postId/comment" @{
    content = "评论回复"
    parentId = $comment.id
}).data
$comments = (Invoke-CommunityApi GET "/community/post/$postId/comment/page?page=1&size=10" $null).data
$replies = (Invoke-CommunityApi GET "/community/comment/$($comment.id)/reply/page?page=1&size=10" $null).data
Assert-True ($comments.Count -eq 2) "评论分页数量异常"
Assert-True ($replies.Count -eq 1 -and $replies[0].id -eq $reply.id) "回复分页异常"

# 3. 点赞采用目标状态语义：先回到未点赞，再验证首次 PUT 和重复 PUT。
Invoke-CommunityApi DELETE "/community/post/$postId/like" $null | Out-Null
$likeBefore = (Invoke-CommunityApi GET "/community/post/$postId/like/status" $null).data
Invoke-CommunityApi PUT "/community/post/$postId/like" $null | Out-Null
Invoke-CommunityApi PUT "/community/post/$postId/like" $null | Out-Null
$likeAfter = (Invoke-CommunityApi GET "/community/post/$postId/like/status" $null).data
Assert-True ($likeAfter.liked -and [long]$likeAfter.likeCount -eq ([long]$likeBefore.likeCount + 1)) "点赞状态或幂等计数异常"

# 4. 收藏与点赞使用相同的目标状态和幂等计数断言。
Invoke-CommunityApi DELETE "/community/post/$postId/favorite" $null | Out-Null
$favoriteBefore = (Invoke-CommunityApi GET "/community/post/$postId/favorite/status" $null).data
Invoke-CommunityApi PUT "/community/post/$postId/favorite" $null | Out-Null
Invoke-CommunityApi PUT "/community/post/$postId/favorite" $null | Out-Null
$favoriteAfter = (Invoke-CommunityApi GET "/community/post/$postId/favorite/status" $null).data
Assert-True ($favoriteAfter.favorited -and [long]$favoriteAfter.favoriteCount -eq ([long]$favoriteBefore.favoriteCount + 1)) "收藏状态或幂等计数异常"

# 5. 关注、取消关注均为幂等操作，不能因为重复 PUT 插入两条关系。
Invoke-CommunityApi DELETE "/community/user/$FollowUserId/follow" $null | Out-Null
Invoke-CommunityApi PUT "/community/user/$FollowUserId/follow" $null | Out-Null
Invoke-CommunityApi PUT "/community/user/$FollowUserId/follow" $null | Out-Null
$followStatus = (Invoke-CommunityApi GET "/community/user/$FollowUserId/follow/status" $null).data
Assert-True $followStatus.following "关注状态异常"

# 6. 删除一级评论时级联删除回复，帖子 commentCount 应回到 0。
Invoke-CommunityApi DELETE "/community/post/$postId/comment/$($comment.id)" $null | Out-Null
Start-Sleep -Seconds 2
$finalDetail = (Invoke-CommunityApi GET "/community/post/$postId" $null).data
Assert-True ([long]$finalDetail.commentCount -eq 0) "删除评论后 commentCount 未归零"

[pscustomobject]@{
    PostId = $postId
    PostTitle = $finalDetail.title
    Like = $likeAfter.liked
    LikeCount = $likeAfter.likeCount
    Favorite = $favoriteAfter.favorited
    FavoriteCount = $favoriteAfter.favoriteCount
    Following = $followStatus.following
    CommentCount = $finalDetail.commentCount
    SearchHits = $search.Count
} | Format-List

Write-Host "社区帖子、评论、点赞、收藏、关注、搜索闭环验证通过；测试帖子保留用于查看效果。" -ForegroundColor Green
