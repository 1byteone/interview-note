# 01 · Docker 容器化部署

> 本文展示如何将 AI 智能商城的全部服务容器化，通过 Docker Compose 一站式启动，实现环境一致性。这是贯穿项目中 Docker 技术栈的集中实践。

---

## 一、容器化总览

```
┌──────────────────────────────────────────────────────────────────────────┐
│                         Docker Compose 一站式启动                          │
│                         docker-compose up -d                             │
└──────────────────────────────────────────────────────────────────────────┘
                                    │
         ┌──────────────────────────┼──────────────────────────┐
         │                          │                          │
         ▼                          ▼                          ▼
┌────────────────────┐   ┌────────────────────┐   ┌────────────────────┐
│ Java 服务集群       │   │ Python AI 服务集群   │   │ 中间件集群          │
│ 11 个容器          │   │ 4 个容器            │   │ 8 个容器            │
├────────────────────┤   ├────────────────────┤   ├────────────────────┤
│ mall-user          │   │ ai-search-gateway  │   │ MySQL 8.0          │
│ mall-product       │   │ ai-search-agent    │   │ Redis Stack        │
│ mall-order         │   │ ai-rag-service     │   │ RocketMQ           │
│ mall-payment       │   │ ai-embedding       │   │ Elasticsearch      │
│ mall-inventory     │   │                    │   │ Nacos              │
│ mall-seckill       │   │                    │   │ MongoDB            │
│ mall-es            │   │                    │   │ Zookeeper          │
│ mall-cart          │   │                    │   │ Prometheus+Grafana│
│ mall-consumer      │   │                    │   └────────────────────┘
│ mall-scheduler     │   │                    │
│ mall-gateway       │   │                    │
└────────────────────┘   └────────────────────┘
```

---

## 二、Dockerfile 设计

### 2.1 Java 微服务 Dockerfile（多阶段构建）

```dockerfile
# 1. 构建阶段
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /build
COPY pom.xml .
RUN mvn dependency:go-offline -B  # 预下载依赖
COPY src ./src
RUN mvn package -DskipTests -B

# 2. 运行阶段
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=builder /build/target/*.jar app.jar
EXPOSE 8080

# 健康检查
HEALTHCHECK --interval=30s --timeout=3s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
```

### 2.2 Python AI 服务 Dockerfile

```dockerfile
FROM python:3.12-slim

WORKDIR /app

# 安装系统依赖
RUN apt-get update && apt-get install -y --no-install-recommends \
    curl \
    && rm -rf /var/lib/apt/lists/*

# 安装 Python 依赖
COPY pyproject.toml .
RUN pip install --no-cache-dir -e ".[dev]"

# 复制源码
COPY src/ ./src/

EXPOSE 9010

HEALTHCHECK --interval=30s --timeout=3s --retries=3 \
  CMD curl -f http://localhost:9010/api/v1/test || exit 1

CMD ["uvicorn", "src.smart_search.main:app", "--host", "0.0.0.0", "--port", "9010"]
```

---

## 三、Docker Compose 配置

### 3.1 中间件服务

```yaml
version: "3.8"

services:
  # ========== 中间件 ==========
  mysql:
    image: mysql:8.0
    environment:
      MYSQL_ROOT_PASSWORD: root123
      MYSQL_DATABASE: mall_ai
    ports:
      - "3306:3306"
    volumes:
      - mysql_data:/var/lib/mysql
      - ./init/mysql:/docker-entrypoint-initdb.d  # 初始化 SQL
    networks:
      - mall-network

  redis-stack:
    image: redis/redis-stack:latest
    ports:
      - "6379:6379"    # Redis 标准端口
      - "8001:8001"    # RedisInsight UI
    volumes:
      - redis_data:/data
    networks:
      - mall-network

  nacos:
    image: nacos/nacos-server:v2.3.0
    environment:
      MODE: standalone
      MYSQL_SERVICE_HOST: mysql
      MYSQL_SERVICE_DB_NAME: nacos_config
      MYSQL_SERVICE_USER: root
      MYSQL_SERVICE_PASSWORD: root123
    ports:
      - "8848:8848"
    depends_on:
      - mysql
    networks:
      - mall-network

  rocketmq-namesrv:
    image: apache/rocketmq:5.1.0
    command: sh mqnamesrv
    ports:
      - "9876:9876"
    networks:
      - mall-network

  rocketmq-broker:
    image: apache/rocketmq:5.1.0
    command: sh mqbroker -c /home/rocketmq/conf/broker.conf
    ports:
      - "10909:10909"
      - "10911:10911"
    volumes:
      - ./rocketmq/conf/broker.conf:/home/rocketmq/conf/broker.conf
    depends_on:
      - rocketmq-namesrv
    networks:
      - mall-network

  elasticsearch:
    image: elasticsearch:8.11.0
    environment:
      - discovery.type=single-node
      - "ES_JAVA_OPTS=-Xms512m -Xmx512m"
      - xpack.security.enabled=false
    ports:
      - "9200:9200"
    volumes:
      - es_data:/usr/share/elasticsearch/data
    networks:
      - mall-network
```

### 3.2 Java 微服务

```yaml
  # ========== Java 微服务 ==========
  mall-gateway:
    build: ./mall-gateway
    ports:
      - "8080:8080"
    environment:
      - NACOS_HOST=nacos
      - MYSQL_HOST=mysql
      - REDIS_HOST=redis-stack
    depends_on:
      - nacos
      - redis-stack
    networks:
      - mall-network

  mall-user-service:
    build: ./mall-user-service
    ports:
      - "8081:8081"
    environment:
      - NACOS_HOST=nacos
      - MYSQL_HOST=mysql
      - REDIS_HOST=redis-stack
    depends_on:
      - nacos
      - mysql
      - redis-stack
    networks:
      - mall-network

  # ... 其他 Java 服务类似，省略 ...
```

### 3.3 Python AI 服务

```yaml
  # ========== Python AI 服务 ==========
  ai-search-gateway:
    build:
      context: ./ai-backend/mall-micro-ai-search
      dockerfile: Dockerfile
    ports:
      - "9010:9010"
    environment:
      - REDIS_URL=redis://redis-stack:6379
      - MYSQL_HOST=mysql
      - MYSQL_PORT=3306
      - MYSQL_USER=root
      - MYSQL_PASSWORD=root123
      - EMBED_PROVIDER=siliconflow
      - LLM_PROVIDER=aliyun
    env_file:
      - ./ai-backend/.env  # API Key 等敏感信息
    depends_on:
      - redis-stack
      - mysql
    networks:
      - mall-network

  ai-rag-service:
    build:
      context: ./ai-backend/rag-service
      dockerfile: Dockerfile
    ports:
      - "9012:9012"
    environment:
      - REDIS_URL=redis://redis-stack:6379
      - EMBED_PROVIDER=siliconflow
      - LLM_PROVIDER=aliyun
    env_file:
      - ./ai-backend/.env
    depends_on:
      - redis-stack
    networks:
      - mall-network
```

### 3.4 监控与网络

```yaml
  # ========== 监控 ==========
  prometheus:
    image: prom/prometheus:latest
    ports:
      - "9090:9090"
    volumes:
      - ./prometheus/prometheus.yml:/etc/prometheus/prometheus.yml
    networks:
      - mall-network

  grafana:
    image: grafana/grafana:latest
    ports:
      - "3000:3000"
    volumes:
      - grafana_data:/var/lib/grafana
    depends_on:
      - prometheus
    networks:
      - mall-network

# ========== 网络与存储 ==========
networks:
  mall-network:
    driver: bridge

volumes:
  mysql_data:
  redis_data:
  es_data:
  grafana_data:
```

---

## 四、启动与验证

### 4.1 一键启动

```bash
# 1. 克隆项目
git clone https://github.com/example/ai-mall.git
cd ai-mall

# 2. 配置环境变量
cp .env.example .env
# 编辑 .env 填入 API Key 等敏感信息

# 3. 启动所有服务
docker-compose up -d

# 4. 查看启动日志
docker-compose logs -f

# 5. 验证服务状态
curl http://localhost:8080/actuator/health          # 网关
curl http://localhost:9010/api/v1/test              # AI 搜索
curl http://localhost:3000                           # Grafana
curl http://localhost:8848/nacos                     # Nacos 控制台
```

### 4.2 服务启动顺序

```
Step 1: 中间件先启动
  mysql → redis-stack → nacos → rocketmq → elasticsearch
  （中间件依赖数据库和网络，需要先就绪）

Step 2: Java 微服务启动
  mall-gateway → mall-user → mall-product → mall-order → ...
  （Java 服务依赖 Nacos 注册中心）

Step 3: Python AI 服务启动
  ai-search-gateway → ai-rag-service → ai-embedding-service
  （AI 服务依赖 Redis 和 LLM 供应商）

Step 4: 数据初始化
  curl http://localhost:9010/api/v1/sync  # 同步商品数据到向量库
  curl http://localhost:8080/api/mall/product/init  # 初始化商品数据
```

---

## 五、环境一致性保障

| 问题 | Docker 解决方案 |
|------|----------------|
| "在我机器上能跑" | 同一镜像，同一环境，消除环境差异 |
| 依赖版本冲突 | 每个服务独立镜像，依赖隔离 |
| 本地开发调试 | `docker-compose` 启动中间件，IDE 热部署服务 |
| 生产环境部署 | 镜像仓库 + 编排工具（K8s/Docker Swarm） |
| 扩容缩容 | `docker-compose up -d --scale mall-order=3` |

---

> **下一篇：** [02-devops-monitoring.md](02-devops-monitoring.md) — CI/CD 与监控