# AI 商城 — 全服务容器化部署

> 等级：🎯 项目实战
> 主题：AI 智能商城（mall-micro-cloud）的全服务容器化部署
> 覆盖：Java 微服务 + Python AI 服务 + 中间件 + 滚动升级

---

## 一、容器化清单

### 1.1 全部服务

| 服务 | 类型 | 语言 | 基础镜像 | 端口 | 依赖 |
|------|------|------|---------|------|------|
| 网关 | 微服务 | Java 17 | eclipse-temurin:17-jre-alpine | 8080 | Nacos, Redis |
| 商品服务 | 微服务 | Java 17 | eclipse-temurin:17-jre-alpine | 8081 | Nacos, MySQL, Redis |
| 订单服务 | 微服务 | Java 17 | eclipse-temurin:17-jre-alpine | 8082 | Nacos, MySQL, Redis, RocketMQ |
| 秒杀服务 | 微服务 | Java 17 | eclipse-temurin:17-jre-alpine | 8083 | Nacos, Redis, RocketMQ |
| 用户服务 | 微服务 | Java 17 | eclipse-temurin:17-jre-alpine | 8084 | Nacos, MySQL, Redis |
| 搜索服务 | 微服务 | Java 17 | eclipse-temurin:17-jre-alpine | 8085 | Nacos, ES, Redis |
| AI 服务 | AI 推理 | Python 3.11 | python:3.11-slim | 8086 | Redis, OpenAI |
| Nacos | 注册中心 | Java | nacos/nacos-server:v2.2.3 | 8848 | - |
| MySQL | 数据库 | - | mysql:8.0 | 3306 | 数据卷 |
| Redis | 缓存 | - | redis:7-alpine | 6379 | 数据卷 |
| RocketMQ | 消息队列 | Java | apache/rocketmq:5.1.4 | 9876 | 数据卷 |
| ES | 搜索引擎 | Java | elasticsearch:8.11.0 | 9200 | 数据卷 |

### 1.2 各服务 Dockerfile

**Java 微服务通用 Dockerfile**

```dockerfile
# Dockerfile (mall-product-service / mall-order-service / mall-gateway 等通用)
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /build
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn package -DskipTests -Pdocker

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

COPY --from=builder /build/target/*.jar app.jar
COPY --from=builder /build/src/main/resources/application-docker.yml .

USER appuser
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar", "--spring.profiles.active=docker"]
HEALTHCHECK --interval=30s --timeout=3s --retries=3 \
    CMD wget -qO- http://localhost:8080/actuator/health || exit 1
```

**Python AI 服务 Dockerfile**

```dockerfile
FROM python:3.11-slim AS builder
WORKDIR /app
COPY requirements.txt .
RUN pip install --user -r requirements.txt

FROM python:3.11-slim
WORKDIR /app
COPY --from=builder /root/.local /root/.local
COPY app.py .
ENV PATH=/root/.local/bin:$PATH

RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

EXPOSE 8086
CMD ["uvicorn", "app:app", "--host", "0.0.0.0", "--port", "8086"]
HEALTHCHECK --interval=30s --timeout=3s --retries=3 \
    CMD python -c "import urllib.request; urllib.request.urlopen('http://localhost:8086/health')" || exit 1
```

---

## 二、完整 docker-compose

### 2.1 基础设施层

```yaml
# docker-compose.yml — 完整版已在 02-core/02-docker-compose.md 给出
# 此处仅列出关键差异点和生产环境配置要点
```

### 2.2 生产环境差异化配置

```yaml
# 生产环境配置要点
services:
  # 1. 密码使用环境变量，不硬编码
  mysql:
    image: mysql:8.0
    environment:
      MYSQL_ROOT_PASSWORD: ${DB_PASSWORD}
    volumes:
      - mysql_data:/var/lib/mysql
    deploy:
      replicas: 1
      resources:
        limits:
          memory: 2G
          cpus: "2.0"

  # 2. 健康检查确保服务真实就绪
  product-service:
    build: ./mall-product-service
    depends_on:
      mysql:
        condition: service_healthy
      nacos:
        condition: service_started
    healthcheck:
      test: ["CMD", "wget", "-qO-", "http://localhost:8081/actuator/health"]
      interval: 30s
      timeout: 5s
      retries: 3
      start_period: 60s

  # 3. 资源限制保护宿主机
  redis:
    image: redis:7-alpine
    command: redis-server --requirepass ${REDIS_PASSWORD} --maxmemory 1gb --maxmemory-policy allkeys-lru
    volumes:
      - redis_data:/data
    deploy:
      resources:
        limits:
          memory: 1.5G

  # 4. 日志配置
  order-service:
    logging:
      driver: "json-file"
      options:
        max-size: "10m"
        max-file: "3"
```

### 2.3 环境配置文件

```yaml
# application-docker.yml（所有 Java 微服务使用）
spring:
  datasource:
    url: jdbc:mysql://mysql:3306/mall?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai
    username: root
    password: ${DB_PASSWORD}
  data:
    redis:
      host: redis
      password: ${REDIS_PASSWORD}
  cloud:
    nacos:
      server-addr: nacos:8848
      discovery:
        ip: ${HOSTNAME}    # 容器内注册自身 IP
```

---

## 三、环境一致性管理

### 3.1 三环境策略

| 环境 | 镜像 | 配置 | 启动方式 | 典型用途 |
|------|------|------|---------|---------|
| 开发 | 本地构建 | 环境变量默认值 | docker compose up | 本地调试 |
| 测试 | CI 构建的镜像 | 测试环境变量 | CI 自动部署 | 自动化测试 |
| 生产 | 发布版本 | 生产环境变量 | 手动触发部署 | 线上服务 |

### 3.2 环境变量管理

```bash
# .env.dev（开发环境）
DB_PASSWORD=root123
REDIS_PASSWORD=dev123
OPENAI_API_KEY=sk-test-key
ORDER_TAG=latest

# .env.prod（生产环境，注意 .gitignore 排除）
DB_PASSWORD=SecurePass123!
REDIS_PASSWORD=RedisPass456!
OPENAI_API_KEY=sk-prod-xxxxx
ORDER_TAG=1.0.0
```

```bash
# 按环境启动
docker compose --env-file .env.dev up -d
docker compose --env-file .env.prod up -d
```

### 3.3 docker-compose.override.yml

```yaml
# docker-compose.override.yml（开发环境自动加载，生产不提交）
# 开发时自动挂载热部署目录
services:
  order-service:
    volumes:
      - ./mall-order-service/target:/app    # 挂载本地构建产物
    environment:
      - JAVA_OPTS=-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005
    ports:
      - "5005:5005"                         # 远程调试端口
```

---

## 四、滚动升级与回滚

### 4.1 Compose 滚动升级

```bash
# 场景：订单服务版本从 1.0.0 升级到 1.0.1

# 1. 拉取新镜像
docker compose pull order-service

# 2. 滚动更新（先扩容再替换）
docker compose up -d --no-deps --scale order-service=2 order-service

# 3. 验证新版本
curl http://localhost:8082/actuator/health
# 检查新容器日志
docker compose logs order-service --tail 20

# 4. 缩容旧版本
docker compose up -d --no-deps --scale order-service=1 order-service

# 5. 回滚（如果需要）
docker compose up -d --no-deps order-service=1.0.0
```

### 4.2 完全的回滚脚本

```bash
#!/bin/bash
# rollback.sh — 回滚指定服务到上一个版本
set -e

SERVICE=$1
PREVIOUS_TAG=$(cat /srv/mall/previous-version.txt)

echo "回滚 $SERVICE 到版本 $PREVIOUS_TAG ..."

# 1. 设置环境变量
export ${SERVICE^^}_TAG=$PREVIOUS_TAG

# 2. 拉取旧版本
docker compose pull $SERVICE

# 3. 滚动更新
docker compose up -d --no-deps $SERVICE

# 4. 健康检查
sleep 30
if docker compose ps $SERVICE | grep -q "unhealthy"; then
    echo "回滚失败，服务异常！"
    exit 1
fi

echo "回滚完成！"
```

### 4.3 版本管理策略

```yaml
# 每个服务版本独立管理，使用 TAG 环境变量
services:
  order-service:
    image: harbor.example.com/mall/order:${ORDER_TAG:-latest}
  product-service:
    image: harbor.example.com/mall/product:${PRODUCT_TAG:-latest}
```

```bash
# 部署时指定版本
ORDER_TAG=1.0.1 PRODUCT_TAG=1.2.0 docker compose up -d
```

---

## 五、面试要点

| 问题 | 一句话答案 |
|------|-----------|
| Java 和 Python 服务如何混合容器化？ | 各自写 Dockerfile，统一用 Compose 编排，不同基础镜像 |
| 开发和生产环境配置差异如何处理？ | .env 文件 + docker-compose.override.yml 区分环境 |
| 滚动升级如何保证零停机？ | 先扩容再替换，健康检查通过后缩容旧版本 |
| 容器化后连接配置要注意什么？ | 使用容器名（服务名）代替 localhost 或 IP |
| 如何回滚到旧版本？ | 保留历史镜像 tag，重新指定 tag 重启即可 |

> 进入独立小项目：用 Docker 部署一个 3 层博客应用。