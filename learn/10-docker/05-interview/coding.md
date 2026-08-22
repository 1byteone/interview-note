# 代码题 — Dockerfile 编写 · Compose 配置 · 故障排查

> 等级：🎯 面试冲刺
> 目标：面试中高频出现的 Docker 代码题，考察 Dockerfile 编写、Compose 编排、故障排查命令的综合能力。

---

## 一、Dockerfile 编写（多阶段构建）

### 题目 1：Spring Boot 应用 Dockerfile

**要求**：实现一个 Spring Boot 应用的 Dockerfile，要求：
1. 多阶段构建（Maven 构建 + JRE 运行）
2. 镜像体积控制在 200MB 以内
3. 非 root 用户运行
4. 健康检查
5. 设置时区为 Asia/Shanghai

```dockerfile
# 第一阶段：构建
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /build
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn package -DskipTests && \
    mv target/*.jar app.jar

# 第二阶段：运行
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# 时区设置
RUN apk add --no-cache tzdata && \
    cp /usr/share/zoneinfo/Asia/Shanghai /etc/localtime && \
    echo "Asia/Shanghai" > /etc/timezone && \
    apk del tzdata

# 安全：非 root 用户
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# 复制产物
COPY --from=builder /build/app.jar .

USER appuser
EXPOSE 8080

# 健康检查
HEALTHCHECK --interval=30s --timeout=3s --start-period=30s --retries=3 \
    CMD wget -qO- http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
```

### 题目 2：Python FastAPI 应用 Dockerfile

**要求**：优化下面这个 Dockerfile，使其体积更小、构建更快、更安全。

```dockerfile
# 优化前（问题代码）
FROM python:3.11
COPY . /app
WORKDIR /app
RUN pip install -r requirements.txt
CMD ["uvicorn", "app:app", "--host", "0.0.0.0", "--port", "8000"]
```

**优化后**：

```dockerfile
# 第一阶段：安装依赖
FROM python:3.11-slim AS builder
WORKDIR /app
COPY requirements.txt .
RUN pip install --user --no-cache-dir -r requirements.txt

# 第二阶段：运行
FROM python:3.11-slim

# 安全：非 root
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# 复制依赖
COPY --from=builder /root/.local /root/.local
ENV PATH=/root/.local/bin:$PATH

WORKDIR /app
COPY app.py .
COPY static ./static

USER appuser
EXPOSE 8000

HEALTHCHECK --interval=30s --timeout=3s --retries=3 \
    CMD python -c "import urllib.request; urllib.request.urlopen('http://localhost:8000/health')" || exit 1

CMD ["uvicorn", "app:app", "--host", "0.0.0.0", "--port", "8000"]
```

**优化效果**：从 900MB 降到 120MB，减少 87%，且非 root 运行更安全。

---

## 二、Docker Compose 配置

### 题目 3：编写 3 层应用 compose

**要求**：写一个 docker-compose.yml，包含：
1. Nginx 反向代理（端口 80）
2. Spring Boot 后端（端口 8080）
3. MySQL 数据库（端口 3306）
4. 后端依赖数据库，等待数据库就绪后才启动
5. 数据持久化

```yaml
version: "3.8"

services:
  mysql:
    image: mysql:8.0
    container_name: app-mysql
    environment:
      MYSQL_ROOT_PASSWORD: ${DB_PASSWORD:-root123}
      MYSQL_DATABASE: appdb
    volumes:
      - mysql_data:/var/lib/mysql
    ports:
      - "3306:3306"
    healthcheck:
      test: ["CMD", "mysqladmin", "ping", "-h", "localhost"]
      interval: 10s
      timeout: 5s
      retries: 5
    networks: [app-net]

  backend:
    build: ./backend
    container_name: app-backend
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
    networks: [app-net]

  nginx:
    image: nginx:alpine
    container_name: app-nginx
    ports:
      - "80:80"
    volumes:
      - ./nginx.conf:/etc/nginx/conf.d/default.conf:ro
    depends_on:
      backend:
        condition: service_healthy
    networks: [app-net]

networks:
  app-net:
    driver: bridge

volumes:
  mysql_data:
```

### 题目 4：K8s Deployment 配置

**要求**：将上面的 Spring Boot 后端部署到 K8s，包含：
1. Deployment（3 副本、资源限制、探针）
2. Service（ClusterIP）
3. 使用 ConfigMap 存储配置

```yaml
# deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: app-backend
  namespace: production
spec:
  replicas: 3
  selector:
    matchLabels:
      app: backend
  template:
    metadata:
      labels:
        app: backend
    spec:
      containers:
        - name: backend
          image: harbor.example.com/app/backend:1.0.0
          ports:
            - containerPort: 8080
          env:
            - name: DB_PASSWORD
              valueFrom:
                secretKeyRef:
                  name: db-secret
                  key: password
          resources:
            requests:
              cpu: 250m
              memory: 512Mi
            limits:
              cpu: "1"
              memory: 1Gi
          readinessProbe:
            httpGet:
              path: /actuator/health/readiness
              port: 8080
            initialDelaySeconds: 20
            periodSeconds: 10
          livenessProbe:
            httpGet:
              path: /actuator/health/liveness
              port: 8080
            initialDelaySeconds: 30
            periodSeconds: 15
          volumeMounts:
            - name: config
              mountPath: /app/config
      volumes:
        - name: config
          configMap:
            name: backend-config
---
apiVersion: v1
kind: Service
metadata:
  name: backend-service
  namespace: production
spec:
  selector:
    app: backend
  ports:
    - port: 80
      targetPort: 8080
  type: ClusterIP
---
apiVersion: v1
kind: ConfigMap
metadata:
  name: backend-config
  namespace: production
data:
  application.yml: |
    server:
      port: 8080
    spring:
      datasource:
        url: jdbc:mysql://mysql-service:3306/appdb
```

---

## 三、故障排查命令

### 题目 5：容器启动失败排查

**场景**：容器启动后立即退出，你需要排查问题。

```bash
# 1. 查看所有容器（包括已退出的）
docker ps -a

# 2. 查看退出码
docker inspect <container> --format '{{.State.ExitCode}} {{.State.Error}}'
# 退出码含义：
#   0   正常退出
#   1   应用错误
#   137  SIGKILL（通常是 OOM）
#   139  SIGSEGV（段错误）
#   143  SIGTERM（优雅关闭）

# 3. 查看日志
docker logs <container> --tail 50
docker logs <container> --tail 100 -f

# 4. 进入容器调试（如果容器还在运行）
docker exec -it <container> /bin/sh

# 5. 用临时容器覆盖入口点，检查文件系统
docker run -it --entrypoint /bin/sh <image>

# 6. 检查资源限制
docker inspect <container> --format '{{.HostConfig.Memory}} {{.HostConfig.NanoCpus}}'
```

### 题目 6：网络问题排查

**场景**：容器 A 无法连接容器 B。

```bash
# 1. 确认两个容器在同一网络
docker inspect <container> --format '{{json .NetworkSettings.Networks}}'

# 2. 测试连通性
docker exec <container> ping <target>
docker exec <container> nc -zv <target> <port>

# 3. 查看 DNS 解析
docker exec <container> cat /etc/resolv.conf
docker exec <container> nslookup <target>

# 4. 检查端口监听
docker exec <container> netstat -tlnp
docker exec <container> ss -tln

# 5. 查看网络详情
docker network inspect <network>

# 6. 抓包分析
docker exec <container> tcpdump -i eth0 port 6379
```

### 题目 7：资源占用排查

**场景**：宿主机 CPU 飙升，怀疑某个容器异常。

```bash
# 1. 查看所有容器的资源占用
docker stats
docker stats --no-stream

# 2. 查看特定容器的详细资源
docker inspect <container> --format '{{json .HostConfig.Resources}}'

# 3. 从宿主机角度查看容器进程
# 获取容器 PID
docker inspect <container> --format '{{.State.Pid}}'
# 查看该进程的资源
top -p <PID>
# 或查看整个 cgroup
cat /sys/fs/cgroup/memory/docker/<container-id>/memory.usage_in_bytes

# 4. 限制资源后重启
docker update --memory=512m --cpus=1.0 <container>
docker restart <container>
```

---

## 四、代码题总结

| 题目 | 核心考点 | 考察能力 |
|------|---------|---------|
| Spring Boot Dockerfile | 多阶段构建、非 root、健康检查 | Dockerfile 编写规范 |
| Python Dockerfile 优化 | 镜像瘦身、多阶段、安全 | 优化意识 |
| 3 层 Compose | 服务编排、依赖、数据持久化 | Compose 配置能力 |
| K8s Deployment | 探针、资源限制、ConfigMap | K8s 基础 |
| 容器启动失败排查 | 退出码、日志、调试技巧 | 故障排查能力 |
| 网络问题排查 | 网络模式、DNS、端口 | 网络诊断能力 |

> 进入推荐资源篇：书籍、官方文档、工具、开源项目。