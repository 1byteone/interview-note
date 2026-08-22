# Dockerfile — 指令详解 · 多阶段构建 · 镜像优化

> 等级：👶 新手通道
> 目标：掌握 Dockerfile 核心指令，编写高效的 Dockerfile，实现镜像瘦身。

---

## 一、Dockerfile 核心指令

### 1.1 指令速查表

| 指令 | 作用 | 示例 |
|------|------|------|
| `FROM` | 指定基础镜像 | `FROM eclipse-temurin:17-jre-alpine` |
| `RUN` | 执行命令（构建时） | `RUN apt-get update && apt-get install -y curl` |
| `COPY` | 复制本地文件到镜像 | `COPY target/app.jar /app/` |
| `ADD` | 复制并支持自动解压 | `ADD jdk.tar.gz /opt/` |
| `CMD` | 容器启动时的默认命令 | `CMD ["java", "-jar", "app.jar"]` |
| `ENTRYPOINT` | 容器启动的主程序 | `ENTRYPOINT ["java", "-jar", "app.jar"]` |
| `EXPOSE` | 声明容器监听端口 | `EXPOSE 8080` |
| `ENV` | 设置环境变量 | `ENV JAVA_OPTS="-Xmx512m"` |
| `ARG` | 构建时参数 | `ARG APP_VERSION=1.0.0` |
| `WORKDIR` | 设置工作目录 | `WORKDIR /app` |
| `USER` | 指定运行用户 | `USER appuser` |
| `VOLUME` | 声明挂载点 | `VOLUME /data` |
| `LABEL` | 添加元数据 | `LABEL maintainer="team@example.com"` |
| `HEALTHCHECK` | 健康检查指令 | `HEALTHCHECK --interval=30s CMD curl -f http://localhost/` |

### 1.2 CMD vs ENTRYPOINT

| 特性 | CMD | ENTRYPOINT |
|------|-----|------------|
| 可被覆盖 | `docker run` 参数可覆盖 | 不易覆盖，除非 `--entrypoint` |
| 常见用法 | 提供默认参数 | 固定主程序 |
| 组合使用 | `CMD` 作为 `ENTRYPOINT` 的默认参数 | `ENTRYPOINT` 固定可执行文件 |

```dockerfile
# 组合使用
ENTRYPOINT ["java", "-jar", "app.jar"]
CMD ["--spring.profiles.active=prod"]
# 运行：docker run my-app --spring.profiles.active=dev
# 实际执行：java -jar app.jar --spring.profiles.active=dev
```

---

## 二、多阶段构建（Multi-Stage Build）

### 2.1 为什么需要多阶段构建

**问题**：Java 应用构建需要 JDK + Maven（~400MB），运行只需要 JRE（~150MB）。

**方案**：多阶段构建——第一阶段用大镜像构建，第二阶段用小镜像运行。

### 2.2 Java 应用多阶段构建

```dockerfile
# 第一阶段：构建（Maven + JDK 17）
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /build
COPY pom.xml .
RUN mvn dependency:go-offline -B    # 提前下载依赖，利用缓存
COPY src ./src
RUN mvn package -DskipTests

# 第二阶段：运行（JRE 17，体积小）
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
# 从 builder 阶段复制构建产物
COPY --from=builder /build/target/*.jar app.jar

# 安全：非 root 运行
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

**优化效果**：400MB → 150MB（减少 62.5%）

### 2.3 Python 应用多阶段构建

```dockerfile
# 第一阶段：安装依赖
FROM python:3.11-slim AS builder
WORKDIR /app
COPY requirements.txt .
RUN pip install --user -r requirements.txt

# 第二阶段：运行
FROM python:3.11-slim
WORKDIR /app
COPY --from=builder /root/.local /root/.local
COPY app.py .
ENV PATH=/root/.local/bin:$PATH
EXPOSE 8000
CMD ["uvicorn", "app:app", "--host", "0.0.0.0", "--port", "8000"]
```

---

## 三、构建上下文与 .dockerignore

### 3.1 构建上下文

```bash
# docker build 的最后一个参数是构建上下文路径
docker build -t my-app:1.0 .

# 构建上下文 = 当前目录及其子目录
# Docker 引擎会将该目录打包发送给 Docker daemon
```

### 3.2 .dockerignore

```dockerignore
# 类似 .gitignore，排除不需要的文件
.git/
node_modules/
target/
*.log
*.md
Dockerfile
.dockerignore
```

**避免**：将 `target/` 或 `node_modules/` 发送到构建上下文，否则构建会非常慢。

---

## 四、镜像优化技巧

### 4.1 优化前后对比

| 优化项 | 优化前 | 优化后 | 效果 |
|--------|--------|--------|------|
| 基础镜像 | ubuntu:22.04 (200MB) | alpine (5MB) | 减少 195MB |
| 构建方式 | 单阶段 (400MB) | 多阶段 (150MB) | 减少 250MB |
| 层合并 | 每行一个 RUN | 合并 RUN 指令 | 减少层数 |
| 依赖缓存 | 每次都下载 | 利用缓存 | 构建提速 3x |

### 4.2 具体技巧

**技巧 1：选择合适的基础镜像**

```dockerfile
# ❌ 太大
FROM ubuntu:22.04        # 200MB
FROM python:3.11         # 900MB

# ✅ 推荐
FROM alpine:3.19         # 5MB
FROM eclipse-temurin:17-jre-alpine  # 150MB
FROM python:3.11-slim    # 120MB
FROM golang:1.22-alpine  # 150MB
```

**技巧 2：合并 RUN 指令，减少层数**

```dockerfile
# ❌ 每行一个 RUN，产生 3 层
RUN apt-get update
RUN apt-get install -y curl
RUN rm -rf /var/lib/apt/lists/*

# ✅ 合并为一行，只产生 1 层
RUN apt-get update && \
    apt-get install -y curl && \
    rm -rf /var/lib/apt/lists/*
```

**技巧 3：利用构建缓存**

```dockerfile
# ❌ 每次修改 src 都会重新下载依赖
COPY . .
RUN mvn package

# ✅ 先复制 pom.xml 下载依赖，再复制源码
COPY pom.xml .
RUN mvn dependency:go-offline -B    # 利用缓存，pom.xml 不变就不重跑
COPY src ./src
RUN mvn package -DskipTests
```

**技巧 4：删除中间产物**

```dockerfile
RUN mvn package -DskipTests && \
    rm -rf /root/.m2/repository    # 清理 Maven 缓存
```

---

## 五、完整示例：Spring Boot 应用 Dockerfile

```dockerfile
# ===== 多阶段构建 =====
# 阶段 1：构建
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /build
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn package -DskipTests && \
    mv target/*.jar app.jar

# 阶段 2：运行
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# 时区设置
RUN apk add --no-cache tzdata && \
    cp /usr/share/zoneinfo/Asia/Shanghai /etc/localtime && \
    echo "Asia/Shanghai" > /etc/timezone

# 安全
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
COPY --from=builder /build/app.jar .
USER appuser

EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s --retries=3 \
    CMD wget -qO- http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
```

---

## 六、面试要点

| 问题 | 一句话答案 |
|------|-----------|
| 多阶段构建解决了什么问题？ | 构建需要大镜像，运行只需要小镜像，分离构建和运行环境 |
| COPY 和 ADD 的区别？ | ADD 支持自动解压 tar 和远程 URL，但一般不推荐用 |
| CMD 和 ENTRYPOINT 的区别？ | CMD 可被覆盖，ENTRYPOINT 固定主程序，可组合使用 |
| 为什么合并 RUN 指令？ | 减少镜像层数，每层都有大小，层数过多浪费空间 |
| .dockerignore 有什么用？ | 排除无关文件，减少构建上下文大小，提升构建速度 |

> 掌握了 Dockerfile，下一节深入镜像分层原理和网络模型。