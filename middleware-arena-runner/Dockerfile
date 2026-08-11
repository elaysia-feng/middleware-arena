FROM eclipse-temurin:21-jre
WORKDIR /app
COPY runner.web/target/*.jar app.jar
EXPOSE 9004

# docker CLI：Runner 通过宿主 docker.sock 起/清临时实验容器（实验环境不常驻）。
# DOCKER_HOST 指向宿主 socket 需在 compose 里把 /var/run/docker.sock 挂进容器
#（见 docker-compose.yml runner 的 volumes）。
# TODO[Runner]：build() 现场编译用户 SUT 需要 JDK + Maven + MVN_repo（离线），
# 后续本镜像需加 eclipse-temurin:21-jdk + maven，并挂载 MVN_repo
#（对应 ma.runner.build.jdk-home / maven / local-repo / offline 配置）
RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates curl \
    && curl -fsSL https://download.docker.com/linux/static/stable/x86_64/docker-24.0.7.tgz | tar -xz \
        --strip-components=1 -C /usr/local/bin docker/docker \
    && chmod +x /usr/local/bin/docker \
    && apt-get purge -y curl \
    && apt-get autoremove -y \
    && rm -rf /var/lib/apt/lists/*

ENTRYPOINT ["java", "-jar", "app.jar"]
