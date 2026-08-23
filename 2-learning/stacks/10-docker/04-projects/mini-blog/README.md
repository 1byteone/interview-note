# MiniBlog 容器化部署 — Nginx + 后端 + MySQL

> 等级：🎯 独立小项目
> 目标：用 Docker 部署一个完整的 3 层应用（Nginx 前端 + Spring Boot 后端 + MySQL 数据库）
> 覆盖：Dockerfile + docker-compose + 健康检查 + 网络配置

---

## 一、项目概述

### 1.1 架构

```
浏览器 ──► Nginx (80) ──► Spring Boot API (8080) ──► MySQL (3306)
           ↑                ↑
     静态文件代理        反向代理 API 请求
```

### 1.2 技术栈

| 层 | 技术 | 镜像 |
|-----|------|------|
| 前端 | Nginx | nginx:alpine |
| 后端 | Spring Boot 3 | eclipse-temurin:17-jre-alpine |
| 数据库 | MySQL 8.0 | mysql:8.0 |

---

## 二、后端 Dockerfile

```dockerfile
# backend/Dockerfile
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /build
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn package -DskipTests

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
COPY --from=builder /build/target/*.jar app.jar
USER appuser
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
HEALTHCHECK --interval=30s --timeout=3s --retries=3 \
    CMD wget -qO- http://localhost:8080/actuator/health || exit 1
```

---

## 三、Nginx 配置

```nginx
# nginx/nginx.conf
upstream backend {
    server backend:8080;          # Docker Compose 服务名
}

server {
    listen 80;

    # 静态文件
    location / {
        root /usr/share/nginx/html;
        index index.html;
        try_files $uri $uri/ /index.html;
    }

    # API 反向代理
    location /api/ {
        proxy_pass http://backend;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

```dockerfile
# nginx/Dockerfile
FROM nginx:alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY html /usr/share/nginx/html
```

---

## 四、后端配置

```yaml
# backend/src/main/resources/application-docker.yml
server:
  port: 8080

spring:
  datasource:
    url: jdbc:mysql://mysql:3306/miniblog?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai
    username: root
    password: ${DB_PASSWORD}
    hikari:
      maximum-pool-size: 10
  jpa:
    hibernate:
      ddl-auto: update
    show-sql: false
```

---

## 五、docker-compose.yml

```yaml
version: "3.8"

services:
  # ===== 数据库 =====
  mysql:
    image: mysql:8.0
    container_name: miniblog-mysql
    environment:
      MYSQL_ROOT_PASSWORD: ${DB_PASSWORD:-root123}
      MYSQL_DATABASE: miniblog
    volumes:
      - mysql_data:/var/lib/mysql
    ports:
      - "3307:3306"          # 避免与宿主机 MySQL 冲突
    healthcheck:
      test: ["CMD", "mysqladmin", "ping", "-h", "localhost"]
      interval: 10s
      timeout: 5s
      retries: 5
    networks: [miniblog-net]

  # ===== 后端 API =====
  backend:
    build: ./backend
    container_name: miniblog-backend
    environment:
      SPRING_PROFILES_ACTIVE: docker
      DB_PASSWORD: ${DB_PASSWORD:-root123}
    depends_on:
      mysql:
        condition: service_healthy
    ports:
      - "8080:8080"
    healthcheck:
      test: ["CMD", "wget", "-qO-", "http://localhost:8080/actuator/health"]
      interval: 30s
      timeout: 3s
      retries: 3
      start_period: 40s
    networks: [miniblog-net]

  # ===== 前端 Nginx =====
  nginx:
    build: ./nginx
    container_name: miniblog-nginx
    depends_on:
      backend:
        condition: service_healthy
    ports:
      - "80:80"
    networks: [miniblog-net]

networks:
  miniblog-net:
    driver: bridge

volumes:
  mysql_data:
```

---

## 六、一键部署

```bash
# 1. 克隆项目并进入目录
git clone https://github.com/example/mini-blog
cd mini-blog

# 2. 构建并启动
docker compose up -d --build

# 3. 检查状态
docker compose ps
docker compose logs

# 4. 验证各层
# 前端：curl http://localhost        → 返回 HTML
# API：  curl http://localhost/api/articles → 返回 JSON
# 数据库：docker exec miniblog-mysql mysql -uroot -proot123 -e "SHOW DATABASES;"

# 5. 停止
docker compose down

# 6. 完全清理（含数据）
docker compose down -v
```

---

## 七、项目目录结构

```
mini-blog/
├── docker-compose.yml
├── .env                        # 环境变量
├── backend/
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/
│       └── main/
│           ├── java/...
│           └── resources/
│               └── application-docker.yml
├── nginx/
│   ├── Dockerfile
│   ├── nginx.conf
│   └── html/
│       └── index.html
└── README.md
```

---

## 八、扩展

| 扩展方向 | 配置变更 |
|---------|---------|
| 增加 Redis 缓存 | 添加 redis 服务，后端增加 spring.data.redis.host=redis |
| HTTPS 支持 | Nginx 配置 SSL 证书 + 443 端口映射 |
| 多副本后端 | docker compose up -d --scale backend=3 |
| 日志收集 | 添加 fluentd 服务，日志驱动改为 fluentd |
| 监控 | 添加 cAdvisor + Prometheus + Grafana |

> 完成项目实战后，进入面试冲刺篇：速记版高频考点。