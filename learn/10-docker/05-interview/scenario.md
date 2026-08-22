# 场景题 — 实战中的 Docker 问题

> 等级：🎯 面试冲刺
> 目标：通过真实场景问题，考察容器化部署、网络排查、故障恢复等实战能力。
> 路径：STAR 法则贯穿每个案例。

---

## 一、镜像构建速度慢

### 场景

**Situation**：AI 商城 CI 流水线中，每次构建镜像都需要 5-8 分钟，其中大部分时间花在下载 Maven 依赖和构建上。团队每天多次提交代码，等待时间严重影响开发效率。

### Task

将镜像构建时间从 5 分钟降低到 2 分钟以内。

### Action

**根因分析**：Dockerfile 中 `COPY . .` 导致每次源码变更都使依赖下载缓存失效。

```dockerfile
# 问题代码
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /build
COPY . .                    # 整个项目复制，pom.xml 变更时缓存全失效
RUN mvn package -DskipTests
```

**优化方案**：

```dockerfile
# 优化后：分层利用缓存
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /build

# 1. 先只复制依赖描述文件
COPY pom.xml .

# 2. 下载依赖（只有 pom.xml 变化时才重跑）
RUN mvn dependency:go-offline -B

# 3. 再复制源码（这部分变化频率最高）
COPY src ./src

# 4. 构建
RUN mvn package -DskipTests
```

**额外优化**：

```bash
# 1. 使用 Maven 镜像缓存
docker run -v ~/.m2/repository:/root/.m2/repository ...

# 2. 使用 BuildKit 加速
DOCKER_BUILDKIT=1 docker build -t my-app:1.0 .

# 3. 设置镜像仓库层缓存
docker build --cache-from harbor.example.com/mall/order:latest \
  -t harbor.example.com/mall/order:1.0 .
```

### Result

- 构建时间从 5 分钟降到 40 秒
- pom.xml 不变时，依赖下载步骤命中缓存，直接跳过
- CI 流水线整体快了 3 倍

---

## 二、容器启动失败

### 场景

**Situation**：部署新版本商城订单服务时，容器启动几秒后就退出，`docker ps` 看不到，`docker ps -a` 显示 Exited。日志没有明显错误。

### Task

定位容器启动失败的原因并修复。

### Action

**第一步：查看退出详情**

```bash
# 查看所有容器（包括已退出的）
docker ps -a

# 查看退出码
docker inspect order-service --format '{{.State.ExitCode}}'
# 退出码 1 → 应用内部错误
# 退出码 137 → SIGKILL（OOM）
# 退出码 143 → SIGTERM（优雅关闭）

# 查看日志
docker logs order-service
```

**第二步：检查退出码分析**

```bash
# 退出码 1：Java 应用启动异常
# 查看详细日志
docker logs order-service --tail 100
# 输出：Error: Unable to access jarfile /app/app.jar
```

**第三步：根因定位**

```dockerfile
# 问题：Dockerfile 中构建产物路径不匹配
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=builder /build/target/*.jar app.jar   # 实际路径是 /build/app.jar
# 构建产物被重命名了，但 COPY 通配符没匹配到
```

**修复**：

```dockerfile
# 修复：显式指定文件名
RUN mv /build/target/*.jar /build/app.jar
# 或
RUN mvn package -DskipTests && cp target/*.jar app.jar
```

**第四步：调试技巧**

```bash
# 临时启动，用 bash 覆盖 CMD，检查文件
docker run -it --entrypoint /bin/sh order-service:1.0

# 交互式检查
# 进入容器后查看 /app/ 目录是否有 app.jar
# 检查文件权限、路径等
```

### Result

- 修复后容器正常启动
- 后续在 CI 中增加构建产物验证步骤，确保 jar 包存在

---

## 三、容器删除后数据丢失

### 场景

**Situation**：DBA 在测试环境清理容器时执行了 `docker compose down -v`，导致 MySQL 的所有数据被删除，包括开发团队两周的测试数据。

### Task

制定数据保护策略，确保容器操作不会误删数据。

### Action

**根因**：`-v` 参数会删除所有在 `volumes:` 中定义的数据卷。

```yaml
# docker-compose.yml
services:
  mysql:
    image: mysql:8.0
    volumes:
      - mysql_data:/var/lib/mysql

volumes:
  mysql_data:           # 会被 down -v 删除
```

**防护措施**：

```bash
# 1. 生产环境严禁使用 down -v
# 查看哪些卷会被删除
docker compose down --dry-run  # 无此命令，手动检查

# 2. 使用外部数据卷，不受 compose 生命周期管理
docker volume create prod_mysql_data
```

```yaml
# docker-compose.yml
volumes:
  mysql_data:
    external: true       # 标记为外部卷，compose down 不会删除
    name: prod_mysql_data
```

**恢复方案**：

```bash
# 1. 立即停止所有写入操作
docker compose stop mysql

# 2. 使用数据恢复工具
# 如果有定期备份
docker run --rm -v prod_mysql_data:/data -v /backup:/backup alpine \
  tar xzf /backup/mysql-20260822.tar.gz -C /data

# 3. 如果没有备份，尝试文件恢复
# 注意：数据卷底层是 overlay2 文件系统，普通删除可恢复
```

**最佳实践**：

```bash
# 1. 定期备份
docker exec mysql-db mysqldump --all-databases -uroot -p$DB_PASSWORD > backup.sql

# 2. 使用 cron 自动化备份
0 3 * * * docker exec mysql-db mysqldump -uroot -p$DB_PASSWORD mall > /backup/mall-$(date +\%Y\%m\%d).sql

# 3. 启用 MySQL binlog 实现时间点恢复
# my.cnf
log-bin=mysql-bin
expire-logs-days=7
```

### Result

- 数据卷标记为 `external: true`，防止误删
- 建立每日自动备份机制
- 生产环境操作 checklist 增加确认步骤

---

## 四、容器间网络不通

### 场景

**Situation**：部署 AI 商城后，订单服务无法连接 Redis，日志报错 `java.net.UnknownHostException: redis`。但 `docker compose ps` 显示 Redis 服务正常运行。

### Task

排查容器间网络问题并修复。

### Action

**第一步：确认网络连通性**

```bash
# 1. 确认 Redis 容器在运行
docker compose ps redis
# 输出：redis  Up  ...

# 2. 进入订单容器测试连接
docker exec -it order-service ping redis
# ping: bad address 'redis'

# 3. 检查网络
docker inspect order-service --format '{{.NetworkSettings.Networks}}'
# 发现订单服务不在 mall-net 网络中
```

**第二步：根因定位**

```yaml
# 问题：order-service 没有指定 networks，使用默认网络
# redis 指定了 network: mall-net
# 两个服务在不同网络，无法通过容器名访问

services:
  order-service:
    build: ./mall-order-service
    # 没有指定 networks → 使用默认网络

  redis:
    image: redis:7-alpine
    networks:
      - mall-net        # 在自定义网络中
```

**修复**：

```yaml
services:
  order-service:
    build: ./mall-order-service
    networks:
      - mall-net        # 加入同一网络

  redis:
    image: redis:7-alpine
    networks:
      - mall-net

networks:
  mall-net:
    driver: bridge
```

**第三步：验证**

```bash
# 重启后测试
docker exec -it order-service ping redis
# 64 bytes from 172.18.0.3: icmp_seq=1 ttl=64 time=0.345ms

# 测试 Redis 端口
docker exec -it order-service nc -zv redis 6379
# redis (172.18.0.3:6379) open
```

**其他常见网络问题**：

```bash
# 1. 端口映射错误
# 容器内应用监听 8080，但 docker run -p 80:8081 → 端口不匹配

# 2. 防火墙/安全组未放行
# 云服务器安全组规则需放行对应端口

# 3. 容器内应用绑定 localhost
# 应用配置 server.address=127.0.0.1 → 只能容器内访问
# 应改为 0.0.0.0 或留空

# 4. DNS 解析问题
# 检查 /etc/resolv.conf
docker exec order-service cat /etc/resolv.conf
```

### Result

- 加入同一自定义网络后，容器间通信正常
- 使用容器名解耦 IP 变化，应用配置更稳定

---

## 五、场景题总结

| 场景 | 核心问题 | 解决方案 | 涉及知识点 |
|------|---------|---------|-----------|
| 镜像构建慢 | 缓存失效 | 分步 COPY，先复制 pom.xml 再复制源码 | 构建缓存、Dockerfile 分层 |
| 容器启动失败 | 退出码分析 | 检查退出码，进入容器调试 | 退出码含义、Dockerfile 调试 |
| 数据丢失 | down -v 误删卷 | external: true 保护数据卷，定期备份 | 数据卷生命周期、备份策略 |
| 网络不通 | 容器在不同网络 | 加入同一自定义网络 | 网络模式、DNS 解析、容器名 |

> 进入代码题篇：手写 Dockerfile、Compose 配置、故障排查命令。