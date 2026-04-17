# ── Stage 1: Build ────────────────────────────────────────────
FROM eclipse-temurin:17-jdk-alpine AS builder

WORKDIR /build

# 先只复制 pom 文件，利用 Docker 层缓存，依赖未变时跳过下载
COPY pom.xml .
COPY hify-common/pom.xml    hify-common/pom.xml
COPY hify-provider/pom.xml  hify-provider/pom.xml
COPY hify-agent/pom.xml     hify-agent/pom.xml
COPY hify-mcp/pom.xml       hify-mcp/pom.xml
COPY hify-chat/pom.xml      hify-chat/pom.xml
COPY hify-workflow/pom.xml  hify-workflow/pom.xml
COPY hify-knowledge/pom.xml hify-knowledge/pom.xml
COPY hify-app/pom.xml       hify-app/pom.xml

RUN mvn dependency:go-offline -q

# 再复制源码编译
COPY hify-common/src    hify-common/src
COPY hify-provider/src  hify-provider/src
COPY hify-agent/src     hify-agent/src
COPY hify-mcp/src       hify-mcp/src
COPY hify-chat/src      hify-chat/src
COPY hify-workflow/src  hify-workflow/src
COPY hify-knowledge/src hify-knowledge/src
COPY hify-app/src       hify-app/src

RUN mvn package -DskipTests -q

# 用 layertools 拆分 jar，让运行阶段的层缓存更细
RUN java -Djarmode=layertools \
    -jar hify-app/target/hify-app-*.jar extract \
    --destination /build/layers

# ── Stage 2: Runtime ──────────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine

# 非 root 用户运行
RUN addgroup -S hify && adduser -S hify -G hify

WORKDIR /app

# 按变化频率从低到高分层，最大化缓存命中
COPY --from=builder --chown=hify:hify /build/layers/dependencies/          ./
COPY --from=builder --chown=hify:hify /build/layers/spring-boot-loader/    ./
COPY --from=builder --chown=hify:hify /build/layers/snapshot-dependencies/ ./
COPY --from=builder --chown=hify:hify /build/layers/application/           ./

# 挂载点：外部 application.yml 和日志目录
VOLUME ["/app/config", "/app/logs"]

USER hify

EXPOSE 8080

ENV SERVER_PORT=8080 \
    JVM_OPTS="-Xms256m -Xmx512m"

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD wget -qO- http://localhost:${SERVER_PORT}/api/v1/health || exit 1

ENTRYPOINT ["sh", "-c", \
  "exec java ${JVM_OPTS} \
    -Djava.security.egd=file:/dev/./urandom \
    org.springframework.boot.loader.launch.JarLauncher \
    --spring.config.additional-location=optional:file:/app/config/application.yml"]
