# 镜像分层与网络 — 存储原理 · 数据卷 · 网络模式

> 等级：👶→🎯 新手进阶
> 目标：深入理解镜像分层原理、数据持久化方案、容器网络模型。

---

## 一、镜像分层原理

### 1.1 什么是分层

Docker 镜像由多个**只读层**组成，每一层都是对上一层的一个增量修改：

```
镜像 image:latest
┌─────────────────────────┐
│ Layer 4: COPY app.jar   │   ← 应用层
├─────────────────────────┤
│ Layer 3: RUN apk add    │   ← 依赖层
├─────────────────────────┤
│ Layer 2: 基础配置       │   ← 系统层
├─────────────────────────┤
│ Layer 1: alpine 基础    │   ← 基础镜像层
└─────────────────────────┘
```

### 1.2 UnionFS / OverlayFS

**UnionFS（联合文件系统）**：将多个只读文件系统叠加，对外呈现为一个统一视图。

**OverlayFS**：Docker 默认的存储驱动，由两层组成：

- **lowerdir**：只读层（镜像层）
- **upperdir**：可写层（容器层）

容器启动时，在只读镜像层之上叠加一个**可写层**：

```
容器视图                    实际存储
┌──────────────────┐      ┌──────────────────┐
│ 可写层 (upperdir)│      │ /var/lib/docker/  │
│ 读流程：从上层    │      │   overlay2/       │
│ 往下找            │      │   ├── lowerdir/   │
│ 写流程：写时复制  │      │   └── upperdir/   │
└──────────────────┘      └──────────────────┘
```

### 1.3 写时复制（Copy-on-Write）

当容器要修改一个在只读层的文件时：

1. 先将文件从只读层**复制**到可写层
2. 在可写层进行修改
3. 只读层的原文件保持不变

**优势**：多个容器共享同一镜像层，节省磁盘空间和内存。

### 1.4 层复用与缓存

```bash
# 拉取镜像时，已存在的层不会重复下载
docker pull nginx:alpine
docker pull nginx:latest    # 共享相同的基础层

# 构建时未变化的层直接使用缓存
docker build -t my-app:1.0 .
docker build -t my-app:1.1 .    # 前面层缓存生效，构建加速
```

---

## 二、数据卷管理

### 2.1 三种持久化方式

| 方式 | 说明 | 适用场景 |
|------|------|---------|
| **Volume** | Docker 管理的数据卷，存储在 `/var/lib/docker/volumes/` | 推荐生产使用 |
| **Bind Mount** | 挂载宿主机任意目录 | 开发调试、配置文件 |
| **tmpfs** | 内存中的临时文件系统 | 不希望落盘的敏感数据 |

### 2.2 Volume 数据卷

```bash
# 创建数据卷
docker volume create mysql_data

# 查看数据卷
docker volume ls
docker volume inspect mysql_data

# 使用数据卷
docker run -d \
  --name mysql-db \
  -v mysql_data:/var/lib/mysql \
  mysql:8.0

# 删除数据卷
docker volume rm mysql_data

# 清理未使用的数据卷
docker volume prune
```

### 2.3 Bind Mount 绑定挂载

```bash
# 宿主机目录挂载到容器目录
docker run -d \
  --name web \
  -v /home/user/html:/usr/share/nginx/html:ro \
  -p 8080:80 \
  nginx:alpine

# :ro 表示只读挂载
# 开发时修改宿主机文件，容器内立即生效
```

### 2.4 tmpfs 临时挂载

```bash
docker run -d \
  --name cache \
  --tmpfs /tmp \
  redis:7-alpine
```

### 2.5 数据卷关键要点

```
容器删除 ≠ 数据删除
  ├── 容器内数据：随容器删除而丢失
  ├── Volume 数据：容器删除后仍保留
  └── Bind Mount 数据：宿主机文件不受影响
```

**面试高频**：MySQL 容器必须挂载数据卷，否则容器删除后**数据全部丢失**。

---

## 三、容器网络模式

### 3.1 四种网络模式

| 模式 | 说明 | 特点 |
|------|------|------|
| **bridge** | 默认，NAT 网络 | 容器间可通信，需端口映射对外 |
| **host** | 共享宿主机网络 | 无隔离，性能最好 |
| **none** | 无网络 | 完全隔离 |
| **container** | 共享其他容器网络 | 与指定容器共享网络栈 |

### 3.2 常用参数选择

```bash
# bridge（默认）
docker run -d -p 8080:80 nginx:alpine

# host：直接使用宿主机 80 端口
docker run -d --network host nginx:alpine

# none：不进网络
docker run -d --network none alpine sleep 3600

# container：共享另一个容器的网络
docker run -d --name sidecar --network container:main-app busybox
```

### 3.3 Docker 内置网络

```bash
# 查看网络列表
docker network ls
# NETWORK ID     NAME      DRIVER    SCOPE
# xxxxxxxx       bridge    bridge    local
# xxxxxxxx       host      host      local
# xxxxxxxx       none      null      local

# 创建自定义网络
docker network create mall-net

# 指定网络运行
docker run -d --network mall-net --name redis-backend redis:7-alpine
```

### 3.4 容器间通信

**关键点**：自定义网络中的容器可以通过**容器名**直接通信（内置 DNS 解析）。

```yaml
# 场景：应用容器连接 Redis 容器
# ❌ 用 IP：容器 IP 会变，不可靠
# ✅ 用容器名：Docker 内置 DNS 自动解析
```

```bash
docker network create mall-net
docker run -d --name mysql-db --network mall-net -e MYSQL_ROOT_PASSWORD=root123 mysql:8.0
docker run -d --name app --network mall-net -p 8080:8080 my-app:1.0

# 在 app 容器中连接 mysql-db 容器
docker exec app telnet mysql-db 3306    # ✅ 通过容器名访问
```

---

## 四、完整实战：电商应用网络拓扑

```
                    ┌─────────────┐
                    │ 宿主机      │ 端口映射
                    │ 8080:80     │
                    └──────┬──────┘
                           │
                    ┌──────▼──────┐
                    │  网关容器    │
                    └──────┬──────┘
                           │  mall-net (自定义网络)
              ┌────────────┼────────────┐
              ▼            ▼            ▼
      ┌────────────┐ ┌──────────┐ ┌──────────┐
      │ 业务容器   │ │ Redis    │ │ MySQL    │
      │ app:8080   │ │ 6379     │ │ 3306     │
      └────────────┘ └──────────┘ └──────────┘
            容器间通过容器名通信，无需映射端口
```

```bash
# 创建网络
docker network create mall-net

# 启动中间件（无需端口映射给宿主机，仅内部使用）
docker run -d --name mysql-db --network mall-net -e MYSQL_ROOT_PASSWORD=root123 mysql:8.0
docker run -d --name redis-cache --network mall-net redis:7-alpine

# 启动应用（只映射对外端口）
docker run -d --name gateway --network mall-net -p 8080:8080 mall-gateway:1.0

# 应用配置中使用容器名！
# spring.datasource.url=jdbc:mysql://mysql-db:3306/mall
# spring.data.redis.host=redis-cache
```

---

## 五、面试要点

| 问题 | 一句话答案 |
|------|-----------|
| 镜像为什么可以共享？ | 分层架构 + 写时复制，多个容器共享只读层 |
| OverlayFS 是什么？ | 联合文件系统的实现，lowerdir 只读 + upperdir 可写 |
| Volume 和 Bind Mount 区别？ | Volume 由 Docker 管理，Bind Mount 直接挂宿主机目录 |
| 容器间如何通信？ | 同一自定义网络内，通过容器名 + DNS 解析 |
| 为什么生产不用 host 网络？ | 无隔离、端口冲突风险，多容器共享宿主机网络栈 |

> 理解分层和网络后，下一节进入编排：Docker Compose 多容器管理。