# Docker Advanced — Spring Boot 全栈部署

## 概述

演示 Spring Boot 多阶段构建 + Docker Compose 全栈编排：
Nginx（反向代理）→ Spring Boot（后端）→ MySQL（数据库）+ Redis（缓存）

## 多阶段构建

```dockerfile
# Stage 1: Maven Builder — 安装依赖、编译、打包
FROM maven:3.9-eclipse-temurin-17 AS builder
# ...
RUN mvn package -DskipTests -B

# Stage 2: JRE Runtime — 仅复制 jar，减小镜像体积
FROM eclipse-temurin:17-jre
COPY --from=builder /build/target/*.jar app.jar
```

**优势：** 最终镜像仅包含 JRE + jar，不含 Maven 和源码，体积从 ~800MB 降至 ~200MB。

## Compose 网络

```
frontend ── nginx ── backend ── backend
                          │
                     ┌────┴────┐
                     │         │
                   mysql     redis
```

- **frontend 网络**：nginx 和 backend 可互相访问
- **backend 网络**：backend、mysql、redis 可互相访问
- **nginx 暴露 80/443 端口**，后端不对外暴露

## 运行

```bash
# 启动所有服务
docker-compose up -d

# 查看日志
docker-compose logs -f backend

# 测试 API
curl http://localhost/api/hello

# 健康检查
curl http://localhost/health

# 停止并清理
docker-compose down -v
```

## 关键最佳实践

| 实践 | 说明 |
|------|------|
| **非 root 用户** | Dockerfile 中创建 appuser 运行应用 |
| **HEALTHCHECK** | 定期检查 /actuator/health 端点 |
| **条件依赖** | depends_on + condition: service_healthy |
| **环境变量** | 通过 environment 传递数据库/Redis 配置 |
| **命名卷** | mysql_data、redis_data 持久化数据 |
| **网络隔离** | frontend/backend 两层网络，前后端分离 |