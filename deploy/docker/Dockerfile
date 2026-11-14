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
COPY --from=build /build/veryal-bootstrap/target/veryal-bootstrap-*.jar app.jar
EXPOSE 8080 9098 9099
ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_TOOL_OPTIONS="-XX:+UseG1GC -XX:MaxRAMPercentage=75"
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
