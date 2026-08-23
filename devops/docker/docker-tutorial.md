# 🐳 Docker 专业教程：从入门到企业级实战

> **定位**：面向 Java 微服务开发者的 Docker 全栈教程
> **知识截止**：2026 年 8 月 | **核心版本**：Docker Compose v2.30+

---

## 一、Docker 核心概念速查

```
Dockerfile  ──docker build──▶  Image（镜像）  ──docker run──▶  Container（容器）
                                                          │
Docker Compose ──docker compose up──▶ 同时启动多个 Container ──┘
```

**一句话记忆：**

| 组件 | 职责 | 类比 |
|------|------|------|
| Dockerfile | 定义"镜像怎么造" | 造房子的图纸 |
| Image | 构建产物 | 造好的房子 |
| Container | 运行实例 | 入住后的房子 |
| Docker Compose | 多容器编排管理 | 小区物业总管 |
| Volume | 数据持久化 | 家具家电（不随房子拆而消失） |
| Network | 容器间通信 | 房子之间的道路 |

---

## 二、Dockerfile：镜像构建的艺术

### 2.1 基础结构

```dockerfile
# ===== 阶段1：构建 =====
FROM eclipse-temurin:21-jdk AS builder
WORKDIR /build
COPY pom.xml .
COPY src ./src
RUN ./mvnw package -DskipTests

# ===== 阶段2：运行 =====
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=builder /build/target/app.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### 2.2 Dockerfile 最佳实践（Docker 官方 + 业界共识）

#### ① 多阶段构建（Multi-Stage Build）

**核心原理**：用 `FROM` 分多个阶段，构建阶段的依赖不进入最终镜像。

```dockerfile
# 构建阶段：包含 Maven、JDK、源码
FROM eclipse-temurin:21-jdk AS builder
WORKDIR /workspace
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn dependency:go-offline  # 缓存依赖
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn package -DskipTests

# 运行阶段：只含 JRE + JAR
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=builder /workspace/target/app.jar app.jar
RUN addgroup --system spring && adduser --system spring --ingroup spring
USER spring:spring
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

**效果对比**：

| 镜像 | 大小 | 攻击面 |
|------|------|--------|
| JDK 21 全量 | ~500MB | 高（含 javac、源码工具） |
| JRE 21 精简 | ~250MB | 中 |
| JRE + 非 root | ~250MB | **低** ✅ |

#### ② 层缓存优化（Layer Cache）

**黄金法则**：从最不容易变化的层到最易变的层排列指令。

```dockerfile
# ❌ 错误：COPY src . 每次源码变化都重装依赖
COPY . .
RUN mvn package

# ✅ 正确：先复制 pom.xml 安装依赖，再复制源码
COPY pom.xml .
RUN mvn dependency:go-offline
COPY src ./src
RUN mvn package -DskipTests
```

**原理**：Docker 逐层检查缓存，如果某层的输入没变就跳过。

```
Layer 1: FROM eclipse-temurin:21-jdk          ── 缓存 ✅
Layer 2: WORKDIR /workspace                    ── 缓存 ✅
Layer 3: COPY pom.xml .                        ── 缓存 ✅
Layer 4: RUN mvn dependency:go-offline          ── 缓存 ✅（依赖未变）
Layer 5: COPY src ./src                        ── ❌ 源码变了
Layer 6: RUN mvn package -DskipTests            ── 重新执行
```

#### ③ 使用 BuildKit 缓存挂载

```dockerfile
# syntax=docker/dockerfile:1
RUN --mount=type=cache,target=/root/.m2 mvn package -DskipTests
RUN --mount=type=cache,target=/var/cache/apt apt-get install -y curl
```

> BuildKit 是 Docker 的下一代构建引擎，默认在 Docker 23.0+ 启用。

#### ④ .dockerignore 文件

类似 `.gitignore`，排除无关文件，减小构建上下文。

```text
# .dockerignore
.git
target/
*.md
.idea/
*.iml
docker-compose*.yml
.env
node_modules/
```

#### ⑤ 安全实践

```dockerfile
# 1. 不以 root 运行
RUN addgroup --system app && adduser --system app --ingroup app
USER app:app

# 2. 固定镜像版本，不用 latest
FROM eclipse-temurin:21-jre-jammy    # ✅ 具体版本
FROM eclipse-temurin:latest          # ❌ 不确定

# 3. 不在镜像中存储密钥
ENV DB_PASSWORD=${DB_PASSWORD}       # ❌ 构建时写死
COPY .env /app/.env                  # ❌ 打入镜像
# ✅ 运行时通过环境变量或 secrets 注入
```

#### ⑥ Java 项目专用优化

```dockerfile
FROM eclipse-temurin:21-jre

WORKDIR /app

# JVM 容器感知参数（必须！）
ENV JAVA_OPTS="-XX:+UseContainerSupport \
    -XX:MaxRAMPercentage=75.0 \
    -XX:InitialRAMPercentage=50.0"

COPY target/app.jar app.jar

EXPOSE 8080

# 用 exec 格式的 ENTRYPOINT，确保 PID 1 信号正确传递
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
```

> **为什么要 `UseContainerSupport`？** JVM 在容器中默认看不到 cgroup 限制，可能分配超过容器内存限制的堆空间，导致 OOM Kill。

---

## 三、Docker Compose：多容器编排

### 3.1 现代 Compose 文件结构（v2.30+）

> ⚠️ **重要变化**：`version` 字段已废弃，现代 `compose.yaml` 直接从 `services:` 开始。

```yaml
services:

  # ========== 应用服务 ==========
  backend:
    build:
      context: .
      dockerfile: Dockerfile
    ports:
      - "8080:8080"
    environment:
      - SPRING_PROFILES_ACTIVE=docker
      - NACOS_SERVER_ADDR=nacos:8848
      - MYSQL_URL=jdbc:mysql://mysql:3306/demo
      - REDIS_URL=redis://redis:6379
    depends_on:
      mysql:
        condition: service_healthy
      redis:
        condition: service_healthy
      nacos:
        condition: service_healthy
    deploy:
      resources:
        limits:
          memory: 1G
          cpus: "1.5"
    restart: unless-stopped
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/actuator/health"]
      interval: 15s
      timeout: 5s
      retries: 3
      start_period: 30s

  # ========== 基础设施 ==========
  mysql:
    image: mysql:8.0
    environment:
      MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD:-changeme}
      MYSQL_DATABASE: demo
    ports:
      - "3306:3306"
    volumes:
      - mysql-data:/var/lib/mysql
    healthcheck:
      test: ["CMD-SHELL", "mysqladmin ping -h localhost -u root -p$MYSQL_ROOT_PASSWORD"]
      interval: 10s
      timeout: 3s
      retries: 5
      start_period: 30s
    restart: unless-stopped

  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"
    volumes:
      - redis-data:/data
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 5s
      timeout: 3s
      retries: 5
    restart: unless-stopped

  nacos:
    image: nacos/nacos-server:v2.4.3
    environment:
      MODE: standalone
      SPRING_DATASOURCE_PLATFORM: mysql
      MYSQL_SERVICE_HOST: mysql
      MYSQL_SERVICE_DB_NAME: nacos
      MYSQL_SERVICE_USER: root
      MYSQL_SERVICE_PASSWORD: ${MYSQL_ROOT_PASSWORD:-changeme}
    ports:
      - "8848:8848"
      - "9848:9848"
    depends_on:
      mysql:
        condition: service_healthy
    healthcheck:
      test: ["CMD-SHELL", "curl -f http://localhost:8848/nacos/v1/console/health/liveness || exit 1"]
      interval: 10s
      timeout: 5s
      retries: 10
      start_period: 30s
    restart: unless-stopped

  elasticsearch:
    image: elasticsearch:8.15.0
    environment:
      - discovery.type=single-node
      - xpack.security.enabled=false
      - "ES_JAVA_OPTS=-Xms512m -Xmx512m"
    ports:
      - "9200:9200"
    volumes:
      - es-data:/usr/share/elasticsearch/data
    deploy:
      resources:
        limits:
          memory: 1G
    restart: unless-stopped

# ========== 数据卷 ==========
volumes:
  mysql-data:
  redis-data:
  es-data:
```

### 3.2 Profiles：多环境管理（一个文件搞定）

```yaml
services:
  # 核心服务 —— 无 profile，始终启动
  backend:
    build: .
    ports: ["8080:8080"]

  mysql:
    image: mysql:8.0

  redis:
    image: redis:7-alpine

  # 开发调试工具
  debug-tools:
    image: busybox
    profiles: [debug]

  # 监控栈
  prometheus:
    image: prom/prometheus
    profiles: [monitoring]

  grafana:
    image: grafana/grafana
    profiles: [monitoring]

  # 日志收集
  loki:
    image: grafana/loki
    profiles: [logging]
```

**使用方式**：

```bash
# 日常开发：只启动核心
docker compose up -d

# 带监控
docker compose --profile monitoring up -d

# 带调试 + 监控
docker compose --profile debug --profile monitoring up -d

# CI 流水线：通过环境变量
COMPOSE_PROFILES=monitoring docker compose up -d
```

### 3.3 Watch Mode：热重载开发（Compose v2.22+）

```yaml
services:
  backend:
    build: .
    develop:
      watch:
        # 源码变化 → 同步到容器
        - action: sync
          path: ./src
          target: /workspace/src
          ignore:
            - "**/target/"

        # pom.xml 变化 → 重新构建镜像
        - action: rebuild
          path: pom.xml

        # 配置文件变化 → 同步 + 重启进程
        - action: sync+restart
          path: ./src/main/resources
          target: /workspace/src/main/resources
```

```bash
docker compose up --watch
```

| Action | 适用场景 | 延迟 |
|--------|---------|------|
| `sync` | 解释型语言（JS/Python/热重载框架） | <1s |
| `rebuild` | 依赖变更（package.json/pom.xml） | 10-30s |
| `sync+restart` | 配置文件变更 | 1-3s |

### 3.4 Watch Mode vs Bind Mount

| 对比 | Watch Mode | Bind Mount |
|------|-----------|------------|
| 跨平台一致性 | ✅ 一致 | ❌ macOS/Windows 有性能问题 |
| 自动重建 | ✅ rebuild/sync+restart | ❌ 手动 |
| node_modules 兼容 | ✅ | ❌ macOS 上经常卡死 |
| 实现原理 | 应用层文件同步 | 文件系统共享层 |

---

## 四、Docker Network：容器间通信

### 4.1 网络模式一览

```
┌──────────────────────────────────────────────────────┐
│                    Network 模式                       │
├──────────┬────────────┬────────────┬────────────────┤
│  bridge  │   host     │  overlay   │     none       │
│ 默认模式  │ 宿主机网络   │ 跨主机通信   │  无网络         │
│ 容器有独立 │ 无网络隔离   │ Swarm/K8s  │  完全隔离       │
│ IP 地址   │ 共享宿主IP  │            │               │
└──────────┴────────────┴────────────┴────────────────┘
```

### 4.2 自定义网络（推荐）

```yaml
services:
  backend:
    build: .
    networks:
      - app-net

  mysql:
    image: mysql:8.0
    networks:
      - app-net

  redis:
    image: redis:7-alpine
    networks:
      - app-net

networks:
  app-net:
    driver: bridge
    ipam:
      config:
        - subnet: 172.28.0.0/16
```

**关键优势**：自定义网络中的容器可以用 **服务名** 作为 DNS 名称互相访问。

```java
// Spring Boot 配置中直接用服务名
spring.datasource.url=jdbc:mysql://mysql:3306/demo  // ✅ "mysql" 是服务名
spring.redis.host=redis                              // ✅ "redis" 是服务名
```

### 4.3 网络拓扑示意

```
           自定义 Bridge Network (app-net)
┌─────────────────────────────────────────────┐
│                                             │
│   backend          mysql          redis     │
│  172.28.0.2    172.28.0.3    172.28.0.4   │
│      │              │              │        │
│      └──────────────┼──────────────┘        │
│                     │                       │
│              DNS 解析: mysql, redis          │
└─────────────────────┼───────────────────────┘
                      │
                      │ NAT
                      ▼
                宿主机:8080  宿主机:3306
```

### 4.4 网络隔离

```yaml
services:
  # 前端只能访问 backend
  frontend:
    networks:
      - frontend-net

  # backend 同时在两个网络中
  backend:
    networks:
      - frontend-net
      - backend-net

  # 数据库只在 backend-net
  mysql:
    networks:
      - backend-net

  # Redis 也只在 backend-net
  redis:
    networks:
      - backend-net

networks:
  frontend-net:
  backend-net:
```

---

## 五、Docker Volume：数据持久化

### 5.1 三种数据挂载方式

```yaml
services:
  mysql:
    image: mysql:8.0
    volumes:
      # 1. 命名卷（Named Volume）—— 推荐用于持久化数据
      - mysql-data:/var/lib/mysql

      # 2. 绑定挂载（Bind Mount）—— 开发时共享配置
      - ./config/my.cnf:/etc/mysql/conf.d/my.cnf:ro

      # 3. tmpfs —— 敏感数据 / 临时缓存
      - type: tmpfs
        target: /tmp
        tmpfs:
          size: 256M

volumes:
  mysql-data:
```

### 5.2 三种方式对比

| 特性 | Named Volume | Bind Mount | tmpfs |
|------|-------------|-----------|-------|
| 数据存储位置 | Docker 管理的目录 | 宿主机指定路径 | 内存 |
| 宿主机可访问 | ❌（需 Docker 命令） | ✅ 直接访问 | ❌ |
| 跨平台兼容 | ✅ | ❌ 路径格式不同 | ✅ |
| 数据持久性 | ✅ 容器删除后保留 | ✅ | ❌ |
| 适用场景 | 数据库、缓存 | 开发时代码/配置 | 密钥、临时缓存 |

### 5.3 备份与恢复

```bash
# 备份命名卷
docker run --rm -v mysql-data:/data -v $(pwd):/backup \
  alpine tar czf /backup/mysql-backup-$(date +%Y%m%d).tar.gz -C /data .

# 恢复命名卷
docker run --rm -v mysql-data:/data -v $(pwd):/backup \
  alpine tar xzf /backup/mysql-backup-20260823.tar.gz -C /data
```

---

## 六、企业级实战：Spring Boot 微服务全栈

### 6.1 完整项目结构

```
my-microservice/
├── Dockerfile
├── docker-bake.hcl          # 生产构建配置
├── compose.yaml             # 开发环境
├── .dockerignore
├── .env                     # 环境变量（不入 Git）
├── pom.xml
└── src/
    └── main/
        ├── java/
        │   └── com/example/demo/
        │       ├── DemoApplication.java
        │       ├── controller/
        │       ├── service/
        │       ├── repository/
        │       └── config/
        └── resources/
            ├── application.yml
            ├── application-docker.yml    # Docker 环境配置
            └── db/migration/             # Flyway 迁移脚本
```

### 6.2 application-docker.yml

```yaml
spring:
  datasource:
    url: jdbc:mysql://mysql:3306/demo?useSSL=false&serverTimezone=Asia/Shanghai
    username: root
    password: ${MYSQL_ROOT_PASSWORD}
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5

  data:
    redis:
      host: redis
      port: 6379

  cloud:
    nacos:
      discovery:
        server-addr: nacos:8848
      config:
        server-addr: nacos:8848
        file-extension: yml

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics
```

### 6.3 一键启动脚本

```bash
#!/bin/bash
set -e

echo "🔨 构建应用 JAR..."
./mvnw clean package -DskipTests

echo "🐳 构建并启动所有服务..."
docker compose up -d --build

echo "⏳ 等待服务就绪..."
sleep 10

echo "📋 服务状态："
docker compose ps

echo ""
echo "✅ 所有服务已启动："
echo "   后端: http://localhost:8080"
echo "   Nacos: http://localhost:8848/nacos"
echo "   MySQL: localhost:3306"
echo "   Redis: localhost:6379"
echo "   ES:    http://localhost:9200"
```

---

## 七、常用命令速查表

### Docker 基础

```bash
# 镜像
docker build -t myapp:1.0 .                  # 构建镜像
docker images                                 # 列出镜像
docker rmi myapp:1.0                          # 删除镜像

# 容器
docker run -d -p 8080:8080 --name app myapp   # 启动容器
docker ps                                     # 查看运行中容器
docker logs -f app                            # 查看日志
docker exec -it app sh                        # 进入容器
docker stop app                               # 停止
docker rm app                                 # 删除
```

### Docker Compose

```bash
# 生命周期
docker compose up -d                          # 后台启动
docker compose up -d --build                  # 重新构建并启动
docker compose down                           # 停止并删除
docker compose down -v                        # 停止并删除（含数据卷！慎用）

# 状态
docker compose ps                             # 查看服务状态
docker compose logs -f backend                # 查看某个服务日志
docker compose top                            # 查看容器进程

# 管理
docker compose restart backend                # 重启某个服务
docker compose exec backend sh                # 进入容器
docker compose pull                           # 拉取最新镜像

# Watch Mode（开发神器）
docker compose up --watch                     # 热重载开发模式

# Profiles
docker compose --profile monitoring up -d     # 激活 monitoring profile
```

### 清理磁盘

```bash
docker system df                              # 查看 Docker 磁盘占用
docker system prune -a                        # 清理所有未使用资源（慎用）
docker volume prune                           # 清理未使用的数据卷
docker image prune -a                         # 清理未使用的镜像
```

---

## 八、Compose vs Kubernetes：何时用什么

```
                     ┌─────────────────────────────┐
                     │     你需要什么？              │
                     └──────────┬──────────────────┘
                                │
                   ┌────────────┼────────────┐
                   ▼            ▼            ▼
              单机多容器    多机集群       自动伸缩/
              本地开发      高可用         滚动更新
                   │            │            │
                   ▼            ▼            ▼
              Docker Compose   Docker Swarm   Kubernetes
              ✅ 推荐          ⚠️ 可用        ✅ 必须
```

| 场景 | Docker Compose | Kubernetes |
|------|---------------|------------|
| 本地开发 | ✅ **最佳** | ❌ 过重 |
| CI/CD 测试环境 | ✅ **最佳** | ⚠️ 可以但复杂 |
| 单机部署（小项目） | ✅ 可以 | ⚠️ 可以但过重 |
| 多机高可用 | ❌ 不支持 | ✅ **必须** |
| 自动伸缩（HPA） | ❌ | ✅ |
| 滚动更新零停机 | ❌ | ✅ |
| 服务网格（Istio） | ❌ | ✅ |
| GPU 工作负载 | ✅（v2.30+） | ✅ |

**推荐路径**：

```
Dockerfile + Compose（本地/CI）
        │
        ▼
  单机部署（小团队/个人项目）
        │
        ▼  随业务增长
  Kubernetes（企业级/多机/高可用）
```

---

## 九、生产部署 Checklist

```yaml
# ✅ 每个服务必须有
healthcheck:              # 健康检查
restart: unless-stopped   # 异常重启策略
deploy.resources.limits   # 资源限制（防 OOM 影响宿主机）

# ✅ 数据库服务必须有
volumes:                  # 命名卷持久化数据

# ✅ 敏感信息
# 不要在 compose.yaml 中硬编码密码
# 使用 .env 文件 或 Docker Secrets

# ✅ 网络
# 使用自定义网络，不用默认 bridge

# ✅ 镜像
# 使用固定版本标签，不用 latest
# 生产环境使用非 root 用户
# 使用多阶段构建减小镜像
```

---

## 十、知识体系总览

```
Docker 知识体系
│
├── 基础概念
│   ├── Image（镜像）
│   ├── Container（容器）
│   └── Registry（镜像仓库）
│
├── 镜像构建
│   ├── Dockerfile 语法
│   ├── 多阶段构建（Multi-Stage）
│   ├── BuildKit 缓存挂载
│   ├── .dockerignore
│   └── 安全最佳实践
│
├── 容器管理
│   ├── docker run 参数
│   ├── 环境变量与配置
│   ├── 资源限制
│   └── 健康检查
│
├── 数据持久化
│   ├── Named Volume
│   ├── Bind Mount
│   ├── tmpfs
│   └── 备份与恢复
│
├── 网络
│   ├── Bridge（默认 / 自定义）
│   ├── Host
│   ├── Overlay（跨主机）
│   └── 网络隔离
│
├── 多容器编排
│   ├── Docker Compose
│   ├── Profiles（多环境）
│   ├── Watch Mode（热重载）
│   ├── Healthcheck + depends_on
│   └── 资源限制
│
├── 生产实践
│   ├── docker-bake.hcl（构建配置）
│   ├── CI/CD 集成
│   ├── 日志管理
│   └── 监控告警
│
└── 进阶
    ├── Docker Swarm
    ├── Kubernetes
    └── Service Mesh
```

---

> **核心口诀**：Dockerfile 管"造什么"，Compose 管"怎么一起跑"，Network 管"怎么通信"，Volume 管"数据不丢"，Healthcheck 管"死了能活"，Profiles 管"环境切换"。
