# 预下载公共实验镜像（磁盘保存，不启动容器；Runner 按实验类型临时起容器）。
# 对应 Runner 配置 ma.runner.images.*，与 compose 平台服务分离，避免为每个用户常驻一套中间件。
$images = @(
    "redis:7",
    "rabbitmq:management",
    "docker.elastic.co/elasticsearch/elasticsearch:8.13.4",
    "seataio/seata-server:2.1.0",
    "mysql:8.0",
    "grafana/k6:0.47.0",
    "curlimages/curl:8.5.0"
)
foreach ($img in $images) {
    Write-Host "拉取 $img ..."
    docker pull $img
}
Write-Host "实验镜像预下载完成。"
