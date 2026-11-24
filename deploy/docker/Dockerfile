# syntax=docker/dockerfile:1

# 构建阶段
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
COPY veryal-common ./veryal-common
COPY veryal-protocol ./veryal-protocol
COPY veryal-domain ./veryal-domain
COPY veryal-application ./veryal-application
COPY veryal-infrastructure ./veryal-infrastructure
COPY veryal-access ./veryal-access
COPY veryal-bootstrap ./veryal-bootstrap
RUN mvn -B -ntp -DskipTests package

# 运行阶段
FROM eclipse-temurin:21-jre
WORKDIR /app

# 不用 root 跑应用：镜像被拿下时影响面小得多。
# /app 下要有 logs（logback 默认 ${VERYAL_LOG_PATH:-logs}）与 data（本地存储默认 ./data/files）。
RUN groupadd --system veryal \
 && useradd --system --gid veryal --home-dir /app --shell /usr/sbin/nologin veryal \
 && mkdir -p /app/logs /app/data \
 && chown -R veryal:veryal /app

COPY --from=build /build/veryal-bootstrap/target/veryal-bootstrap-*.jar app.jar

USER veryal

EXPOSE 8080 9098 9099
ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_TOOL_OPTIONS="-XX:+UseG1GC -XX:MaxRAMPercentage=75"

# 镜像里没有 curl/wget，用 bash 的 /dev/tcp 发一个最小 HTTP 请求判断 200。
# 启动慢（Flyway 迁移 + Netty 绑定）所以 start-period 给足 90s。
HEALTHCHECK --interval=15s --timeout=5s --start-period=90s --retries=10 \
    CMD bash -c 'exec 3<>/dev/tcp/127.0.0.1/8080 && printf "GET /actuator/health HTTP/1.0\r\n\r\n" >&3 && head -1 <&3 | grep -q 200'

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
