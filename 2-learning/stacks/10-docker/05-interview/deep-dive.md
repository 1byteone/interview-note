# 深挖题 — 容器运行时原理 · 镜像分层 · Dockerfile 最佳实践

> 等级：🎯 面试进阶
> 目标：深入理解容器运行时底层（Namespace/Cgroup）、镜像分层实现、Dockerfile 最佳实践。

---

## 一、容器运行时原理

### 1.1 容器到底是什么

```
容器不是虚拟机，它是一个被隔离的用户空间进程。
```

**关键区别**：

| 维度 | 虚拟机 | 容器 |
|------|--------|------|
| 内核 | 每个 VM 有自己的内核 | 共享宿主机内核 |
| 隔离 | 硬件级虚拟化（Hypervisor） | 操作系统级（Namespace + Cgroup） |
| 启动 | 分钟级 | 毫秒级 |
| 资源 | 固定分配，浪费大 | 共享，按需限制 |
| 安全性 | 强隔离 | 弱隔离（共享内核） |

### 1.2 Namespace — 隔离什么

容器通过 6 个（+ 2 个新）Namespace 实现资源隔离：

| Namespace | 隔离内容 | 作用 |
|-----------|---------|------|
| **PID** | 进程 ID | 容器内只能看到自己的进程（PID=1） |
| **Network** | 网络栈（网卡、路由、iptables） | 容器有独立 IP 和端口空间 |
| **Mount** | 文件系统挂载点 | 容器看到自己的文件系统 |
| **UTS** | 主机名和域名 | 容器可以有自己的 hostname |
| **IPC** | 进程间通信（信号量、消息队列） | 隔离 System V IPC |
| **User** | 用户和 UID | 容器内 root 映射到宿主机非 root |
| **Cgroup** | 资源限制 | CPU、内存、磁盘 IO 等 |
| **Time** | 系统时间 | 容器可以有独立的时间偏移 |

```bash
# 查看容器中的 Namespace
docker run -d --name demo nginx:alpine
docker inspect demo --format '{{.State.Pid}}'
# 得到 PID 12345

# 查看该进程的 Namespace
ls -la /proc/12345/ns/
# pid:[4026531836]  network:[4026531968]  ...

# 一个 Namespace 文件描述符，多个容器共享同一个值 = 同 Namespace
```

### 1.3 Cgroup — 限制什么

Cgroup（Control Groups）控制资源使用上限：

```bash
# 查看容器的 Cgroup 配置
docker run -d --memory=512m --cpus=1.5 --name demo nginx:alpine

# Cgroup 路径
ls /sys/fs/cgroup/memory/docker/<container-id>/
# memory.limit_in_bytes          ← 内存上限
# memory.usage_in_bytes          ← 当前内存使用
# memory.oom_control             ← OOM 控制
```

```bash
# 模拟 OOM 场景
docker run -d --memory=100m --name oom-test alpine sleep 3600
# 在容器中分配超过 100MB 内存 → 容器被 OOM Kill
docker logs oom-test  # 无输出，查看 docker inspect 有 OOMKilled 标记
```

### 1.4 容器启动流程（简化版）

```
docker run -d nginx:alpine

1. Docker Client 发送 REST 请求到 Docker Daemon
2. Daemon 检查镜像是否存在，不存在则拉取
3. 创建容器（分配 Namespace + Cgroup）
4. 创建 OverlayFS 文件系统（只读层 + 可写层）
5. 分配网络（veth pair 连接到 bridge）
6. 启动容器进程（在隔离的 PID Namespace 中跑 PID=1）
7. 进程运行在 Cgroup 限制的资源范围内
```

---

## 二、镜像分层深入

### 2.1 镜像层的数据结构

```json
{
  "schemaVersion": 2,
  "mediaType": "application/vnd.docker.distribution.manifest.v2+json",
  "config": {
    "mediaType": "application/vnd.docker.container.image.v1+json",
    "digest": "sha256:xxxx",
    "size": 1234
  },
  "layers": [
    {
      "mediaType": "application/vnd.docker.image.rootfs.diff.tar.gzip",
      "digest": "sha256:layer1-digest",
      "size": 12345
    },
    {
      "mediaType": "application/vnd.docker.image.rootfs.diff.tar.gzip",
      "digest": "sha256:layer2-digest",
      "size": 67890
    }
  ]
}
```

每层是一个 tar.gz 文件，存储了该层相对于上一层的文件差异。

### 2.2 分层带来的好处

```bash
# 1. 磁盘节省：多个容器共享相同层
# 两个容器分别用 nginx:alpine 和 redis:alpine
# 它们共享 alpine 基础层，只需下载一次

# 2. 内存节省：相同层在内存中只缓存一份
# 3. 网络节省：拉取新镜像时只下载不存在的层

# 验证
docker pull nginx:alpine
docker pull redis:alpine
# 观察：alpine 基础层已存在，只下载差异层
```

### 2.3 层数限制与性能

```
Docker 早期限制 127 层，现在已放开
但每层都有大小，建议控制在 10-20 层以内
每层都是独立的 diff，过多层会降低存储和构建性能
```

### 2.4 镜像大小优化

```dockerfile
# 优化前：400MB，7 层
FROM ubuntu:22.04
RUN apt-get update
RUN apt-get install -y nginx
RUN apt-get install -y curl
RUN apt-get install -y vim
COPY index.html /var/www/html/
CMD ["nginx", "-g", "daemon off;"]

# 优化后：150MB，5 层
FROM nginx:alpine
RUN rm -rf /etc/nginx/conf.d/default.conf
COPY nginx.conf /etc/nginx/conf.d/
COPY index.html /usr/share/nginx/html/
CMD ["nginx", "-g", "daemon off;"]
```

**优化策略**：
1. 选择更小的基础镜像（alpine 替代 ubuntu）
2. 合并 RUN 指令（减少层数）
3. 删除不必要的包（清理缓存）
4. 多阶段构建（分离构建和运行环境）

---

## 三、Dockerfile 最佳实践

### 3.1 指令顺序优化

```dockerfile
# 最佳实践：变化频率从低到高排列
FROM eclipse-temurin:17-jre-alpine    # 1. 基础镜像（几乎不变）

# 2. 系统依赖（偶尔变）
RUN apk add --no-cache tzdata curl

# 3. 依赖配置（较少变）
COPY pom.xml .
RUN mvn dependency:go-offline -B

# 4. 源码（最常变）
COPY src ./src

# 5. 构建（最常变）
RUN mvn package -DskipTests
```

**原理**：Docker 构建缓存逐层匹配，一旦某层缓存失效，后续层全部重建。将变化频率低的指令放在前面，最大化缓存命中率。

### 3.2 多阶段构建深入

```dockerfile
# 三阶段构建：构建 → 测试 → 运行
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /build
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn package -DskipTests

FROM builder AS tester
RUN mvn test

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=builder /build/target/*.jar app.jar
USER appuser
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### 3.3 安全最佳实践

```dockerfile
# 1. 非 root 用户
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

# 2. 只读文件系统
# 在 docker run 时添加 --read-only

# 3. 不使用 root 运行进程
# 容器内 root 权限可能突破 namespace 隔离
```

### 3.4 健康检查

```dockerfile
# 三种方式
HEALTHCHECK --interval=30s --timeout=3s --retries=3 \
    CMD curl -f http://localhost:8080/health || exit 1

# 或使用 wget
HEALTHCHECK CMD wget -qO- http://localhost:8080/actuator/health || exit 1
```

---

## 四、面试关键点总结

| 主题 | 源码/底层 | 面试价值 |
|------|----------|---------|
| Namespace 六种隔离 | PID/Network/Mount/UTS/IPC/User | 容器的隔离本质 |
| Cgroup 资源限制 | cpu/memory/blkio 子系统 | 资源隔离的实现 |
| OverlayFS | lowerdir + upperdir + merged | 镜像分层原理 |
| 写时复制 | 文件从只读层复制到可写层 | 理解容器写效率 |
| 容器启动流程 | REST → Daemon → Namespace → OverlayFS → 进程 | 完整链路理解 |

> 进入场景题篇：镜像构建慢、容器启动失败、数据丢失、网络不通。