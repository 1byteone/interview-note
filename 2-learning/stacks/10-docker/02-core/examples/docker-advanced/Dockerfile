# ============================================
# Spring Boot 多阶段构建
# Maven 构建 + JRE 运行时
# ============================================

# ---------- Stage 1: Maven Builder ----------
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /build

# 先复制 pom.xml 以利用 Docker 层缓存
COPY pom.xml .
RUN mvn dependency:go-offline -B

# 复制源码并打包
COPY src ./src
RUN mvn package -DskipTests -B

# ---------- Stage 2: JRE Runtime ----------
FROM eclipse-temurin:17-jre

WORKDIR /app

# 创建非 root 用户运行应用（安全最佳实践）
RUN groupadd -r appuser && useradd -r -g appuser -d /app appuser

# 从 builder 阶段复制打包好的 jar
COPY --from=builder /build/target/*.jar app.jar

# 应用配置文件可通过 volume 挂载覆盖
COPY --from=builder /build/src/main/resources/application.yml /app/config/application.yml

RUN chown -R appuser:appuser /app

USER appuser

EXPOSE 8080

# 健康检查
HEALTHCHECK --interval=30s --timeout=3s --start-period=10s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar", "--spring.config.additional-location=/app/config/"]