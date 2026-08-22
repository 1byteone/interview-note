# =============================================================================
# Dockerfile.java — Java Spring Boot 服务多阶段构建
#
# 使用方式（在 docker-compose.yml 中）:
#   build:
#     context: .
#     dockerfile: Dockerfile.java
#     args:
#       SERVICE_NAME: mall-user-service
#       JAR_FILE: mall-user-service/target/mall-user-service.jar
#
# 阶段一(builder): 先拷贝 pom.xml 下载依赖（利用 Docker 层缓存加速构建）
# 阶段二(runtime): 精简 jre 运行环境，以非 root 用户运行
# =============================================================================

# ---------------------------------------------------------------------------
# 阶段 1：构建阶段 — Maven 编译打包
# ---------------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-17 AS builder

# 传递的服务名（用于镜像标签，可选）
ARG SERVICE_NAME
ENV SERVICE_NAME=${SERVICE_NAME:-app}

# 工作目录
WORKDIR /build

# 先只拷贝根 pom.xml 和所有子模块的 pom.xml，用于缓存依赖下载层
# 注意：最终项目的实际结构应使用项目的根 pom.xml
COPY pom.xml ./
# 如果存在多模块，各子模块的 pom 也要提前拷贝（注释示例，可按需启用）
# COPY mall-gateway/pom.xml mall-gateway/
# COPY mall-user-service/pom.xml mall-user-service/
# COPY mall-product-service/pom.xml mall-product-service/
# COPY mall-order-service/pom.xml mall-order-service/
# COPY mall-payment-service/pom.xml mall-payment-service/

# 下载依赖但不编译（利用 Docker 层缓存，源码变更不重复下载依赖）
RUN mvn dependency:go-offline -B || true

# 拷贝源码并打包（跳过测试，加快构建）
COPY . .

RUN mvn clean package -DskipTests -B

# ---------------------------------------------------------------------------
# 阶段 2：运行阶段 — 精简 JRE
# ---------------------------------------------------------------------------
FROM eclipse-temurin:17-jre-alpine

# 编译环境变量 JDK 版本，便于 jlink/瘦身（此处为记录用途）
ENV LANG=C.UTF-8 \
    LANGUAGE=C.UTF-8 \
    LC_ALL=C.UTF-8 \
    TZ=Asia/Shanghai

# 创建非 root 用户，提升容器安全性
RUN addgroup -S mall && adduser -S mall -G mall

WORKDIR /app

# 从构建阶段拷贝打好的 jar
# 默认使用第一个 jar；可通过构建参数 JAR_FILE 覆盖
ARG JAR_FILE
COPY --from=builder /build/target/*.jar /app/app.jar

# 切换为非 root 用户运行
USER mall

# 暴露服务端口（实际端口由各服务 docker-compose 配置决定）
EXPOSE 8080

# 容器启动命令
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-Djava.security.egd=file:/dev/./urandom", "-jar", "/app/app.jar"]