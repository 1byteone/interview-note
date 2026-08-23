# Docker Compose — 服务编排 · 依赖管理 · AI 商城一站式启动

> 等级：👶→🎯 新手进阶
> 目标：掌握 Compose 文件结构，用一行命令启动完整微服务集群。

---

## 一、什么是 Docker Compose

Docker Compose 是**多容器编排工具**，用 YAML 文件定义一组容器及其关系，一条命令完成启动/停止/重建。

```
docker run 命令                     docker-compose.yml
─────────────────                  ──────────────────────
一条命令管理一个容器      →          一个文件管理整个应用栈
```

### 1.1 安装

```bash
# Docker Desktop 内置 Compose
docker compose version
# Docker Compose version v2.27.1
```

---

## 二、Compose 文件结构

### 2.1 基础结构

```yaml
# docker-compose.yml
services:        # 服务定义（核心）
  web:
    image: nginx:alpine
    ports:
      - "8080:80"
  db:
    image: mysql:8.0
    environment:
      MYSQL_ROOT_PASSWORD: root123
    volumes:
      - db_data:/var/lib/mysql

networks:        # 网络定义
  default:
    driver: bridge

volumes:         # 数据卷定义
  db_data:
```

### 2.2 顶层关键字

| 关键字 | 作用 | 必填 |
|--------|------|------|
| `services` | 定义所有服务 | ✅ |
| `networks` | 定义网络 | 可选 |
| `volumes` | 定义命名数据卷 | 可选 |
| `configs` | 只读配置文件 | 可选 |
| `secrets` | 敏感信息 | 可选 |

### 2.3 服务常用配置项

```yaml
services:
  order-service:
    build: ./mall-order-service        # 从 Dockerfile 构建
    image: mall-order-service:1.0      # 镜像名称（构建后可复用）
    container_name: order-service      # 容器名
    ports:
      - "8082:8082"                    # 端口映射
    environment:                       # 环境变量
      SPRING_PROFILES_ACTIVE: prod
      DB_PASSWORD: ${DB_PASSWORD}      # 引用宿主机 .env 文件
    env_file:
      - .env                           # 从文件加载环境变量
    volumes:                           # 数据卷挂载
      - order_logs:/app/logs
      - ./config:/app/config:ro
    depends_on:                        # 依赖其他服务
      - nacos
      - redis
    networks:
      - mall-net                       # 加入指定网络
    restart: unless-stopped            # 重启策略
    healthcheck:                       # 健康检查
      test: ["CMD", "curl", "-f", "http://localhost:8082/actuator/health"]
      interval: 30s
      timeout: 3s
      retries: 3
```

---

## 三、服务编排核心能力

### 3.1 依赖管理 depends_on

```yaml
services:
  app:
    build: .
    depends_on:
      mysql:
        condition: service_healthy    # 等待 MySQL 健康后再启动
      redis:
        condition: service_started    # 仅确保已启动

  mysql:
    image: mysql:8.0
    healthcheck:
      test: ["CMD", "mysqladmin", "ping", "-h", "localhost"]
      interval: 5s
      timeout: 3s
      retries: 10
```

**注意**：`depends_on` 控制**启动顺序**，不代表依赖就绪——必须配合 `healthcheck` 的 `condition: service_healthy`。

### 3.2 常用命令

```bash
# 启动全部服务（后台）
docker compose up -d

# 重新构建并启动
docker compose up -d --build

# 查看服务状态
docker compose ps

# 查看日志
docker compose logs -f order-service

# 停止全部服务
docker compose down

# 停止并删除数据卷（数据会丢！）
docker compose down -v

# 仅重启某个服务
docker compose restart order-service

# 查看配置（YAML 合并结果）
docker compose config
```

### 3.3 .env 文件与环境变量

```bash
# .env 文件（与 docker-compose.yml 同目录，注意 .gitignore 排除敏感值）
DB_PASSWORD=SecurePass123
REDIS_PASSWORD=RedisPass456
TAG=1.0.0
```

```yaml
# docker-compose.yml 中引用
services:
  mysql:
    image: mysql:8.0
    environment:
      MYSQL_ROOT_PASSWORD: ${DB_PASSWORD}
```

---

## 四、AI 商城全套 docker-compose

### 4.1 完整编排（迁移自 mall-micro-cloud）

```yaml
# docker-compose.yml — AI 智能商城一站式启动
version: "3.8"

services:
  # ===== 基础设施 =====
  nacos:
    image: nacos/nacos-server:v2.2.3
    container_name: mall-nacos
    environment:
      MODE: standalone
      NACOS_AUTH_ENABLE: "false"
    ports:
      - "8848:8848"
      - "9848:9848"
    networks: [mall-net]

  mysql:
    image: mysql:8.0
    container_name: mall-mysql
    environment:
      MYSQL_ROOT_PASSWORD: root123
      MYSQL_DATABASE: mall
    command: --character-set-server=utf8mb4 --collation-server=utf8mb4_unicode_ci
    volumes:
      - mysql_data:/var/lib/mysql
      - ./init:/docker-entrypoint-initdb.d:ro    # 初始化 SQL
    ports:
      - "3306:3306"
    healthcheck:
      test: ["CMD", "mysqladmin", "ping", "-h", "localhost"]
      interval: 10s
      timeout: 5s
      retries: 5
    networks: [mall-net]

  redis:
    image: redis:7-alpine
    container_name: mall-redis
    ports:
      - "6379:6379"
    volumes:
      - redis_data:/data
    networks: [mall-net]

  rocketmq-namesrv:
    image: apache/rocketmq:5.1.4
    container_name: mall-rocketmq-namesrv
    command: sh mqnamesrv
    ports:
      - "9876:9876"
    volumes:
      - rocketmq_namesrv:/home/rocketmq/store
    networks: [mall-net]

  elasticsearch:
    image: elasticsearch:8.11.0
    container_name: mall-es
    environment:
      - discovery.type=single-node
      - xpack.security.enabled=false
      - ES_JAVA_OPTS=-Xms512m -Xmx512m
    ports:
      - "9200:9200"
    volumes:
      - es_data:/usr/share/elasticsearch/data
    networks: [mall-net]

  # ===== AI 商城微服务 =====
  gateway:
    build: ./mall-gateway
    container_name: mall-gateway
    depends_on: [nacos, redis]
    ports:
      - "8080:8080"
    environment:
      SPRING_PROFILES_ACTIVE: docker
    networks: [mall-net]

  product-service:
    build: ./mall-product-service
    container_name: mall-product
    depends_on: [nacos, mysql, redis]
    ports:
      - "8081:8081"
    healthcheck:
      test: ["CMD", "wget", "-qO-", "http://localhost:8081/actuator/health"]
      interval: 30s
      timeout: 3s
      retries: 3
    networks: [mall-net]

  order-service:
    build: ./mall-order-service
    container_name: mall-order
    depends_on: [nacos, mysql, redis, rocketmq-namesrv]
    ports:
      - "8082:8082"
    networks: [mall-net]

  seckill-service:
    build: ./mall-seckill-service
    container_name: mall-seckill
    depends_on: [nacos, redis, rocketmq-namesrv]
    ports:
      - "8083:8083"
    networks: [mall-net]

  user-service:
    build: ./mall-user-service
    container_name: mall-user
    depends_on: [nacos, mysql, redis]
    ports:
      - "8084:8084"
    networks: [mall-net]

  search-service:
    build: ./mall-search-service
    container_name: mall-search
    depends_on: [nacos, elasticsearch, redis]
    ports:
      - "8085:8085"
    networks: [mall-net]

  ai-service:
    build: ./mall-ai-service
    container_name: mall-ai
    depends_on: [nacos, redis]
    ports:
      - "8086:8086"
    environment:
      OPENAI_API_KEY: ${OPENAI_API_KEY}
    networks: [mall-net]

networks:
  mall-net:
    driver: bridge

volumes:
  mysql_data:
  redis_data:
  rocketmq_namesrv:
  es_data:
```

### 4.2 一键启动流程

```bash
# 1. 配置环境变量
cat > .env << EOF
OPENAI_API_KEY=sk-xxx
DB_PASSWORD=root123
EOF

# 2. 启动全部服务
docker compose up -d --build
# 等待 2-3 分钟，中间件先就绪，微服务依次启动

# 3. 验证
docker compose ps                      # 查看所有服务状态
curl http://localhost:8080/api/health  # 通过网关访问

# 4. 查看日志
docker compose logs -f

# 5. 停止
docker compose down
```

### 4.3 各服务连接配置（使用容器名）

```yaml
# mall-product-service 的 application-docker.yml
spring:
  datasource:
    url: jdbc:mysql://mysql:3306/mall?useSSL=false
    username: root
    password: root123
  data:
    redis:
      host: redis
  cloud:
    nacos:
      server-addr: nacos:8848
```

**关键**：容器内必须使用**服务名（容器名）**而非 localhost 连接其他服务。

---

## 五、面试要点

| 问题 | 一句话答案 |
|------|-----------|
| Compose 三要素是什么？ | services / networks / volumes |
| depends_on 能保证依赖就绪吗？ | 只保证启动顺序，就绪需要 healthcheck + condition: service_healthy |
| compose down -v 有什么风险？ | 连带删除命名数据卷，数据库数据会丢失 |
| 环境变量怎么注入？ | environment 直接写 / env_file 加载文件 / 引用 .env |
| 生产环境用什么？ | 单机用 Compose，集群用 K8s |

> 掌握了 Compose，进入进阶篇：容器集群编排（Swarm/K8s）与 CI/CD。