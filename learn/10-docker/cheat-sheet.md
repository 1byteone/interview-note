# Docker 速查卡 📋

> 面试前30分钟扫一遍，一页纸覆盖核心考点

## 🏷️ 核心概念速记

| 概念 | 一句话解释 | 常见陷阱 |
|------|-----------|----------|
| 镜像 (Image) | 只读模板，分层文件系统，Dockerfile 构建 | 镜像层越多越占空间，留意无关文件膨胀 |
| 容器 (Container) | 镜像运行实例，读写层+隔离环境 | 容器重启后数据丢失(无 Volume)，PID 1 进程管理 |
| 镜像分层 | 每层缓存，只传送差异，分享公共层 | 层顺序影响缓存，不变指令放前面，变动的放后面 |
| 多阶段构建 | 一个 Dockerfile 多个 FROM，编译环境+运行环境分离 | 最终镜像只包含最终 stage 产物，依赖不打包进去 |
| Volume | 持久化存储，独立于容器生命周期 | bind mount 权限问题(uid/gid)，生产推荐命名 volume |
| 网络模式 | bridge(默认)、host(共享宿主机网络)、none(隔离) | bridge 容器间必须通过 IP 或 link/网络名通信 |
| Docker Compose | 多容器编排，YAML 定义服务/网络/卷 | 版本和格式对齐，环境变量传递方式 |
| 容器编排 | 单机 Docker Compose，集群 K8s/Swarm | 生产环境极少用 Swarm，K8s 是事实标准 |
| 资源限制 | --memory / --cpus 限制容器资源使用 | 不限制一个容器吃掉宿主机全部资源，影响其他容器 |
| 健康检查 (HEALTHCHECK) | 容器定期检查自身状态，K8s 配合 livenessProbe | 没有健康检查的容器就算挂掉也不会被自动重启 |

## 🔧 常用命令/API

```dockerfile
# Dockerfile 多阶段构建模板（Spring Boot 应用）
# === Stage 1: 编译 ===
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /build
COPY pom.xml .
RUN mvn dependency:go-offline -B          # 离线下载依赖，利用缓存
COPY src/ ./src/
RUN mvn package -DskipTests -B

# === Stage 2: 运行 ===
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=builder /build/target/*.jar app.jar
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s --retries=3 \
    CMD wget -qO- http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
```

```yaml
# docker-compose.yml 模板
version: '3.8'
services:
  app:
    build: .
    ports:
      - "8080:8080"
    environment:
      - SPRING_PROFILES_ACTIVE=prod
      - DB_URL=jdbc:mysql://db:3306/demo
    depends_on:
      db:
        condition: service_healthy
    volumes:
      - app-data:/data
    restart: unless-stopped
    deploy:
      resources:
        limits:
          memory: 512M
          cpus: '0.5'

  db:
    image: mysql:8.0
    environment:
      MYSQL_ROOT_PASSWORD: rootpass
      MYSQL_DATABASE: demo
    volumes:
      - db-data:/var/lib/mysql
    healthcheck:
      test: ["CMD", "mysqladmin", "ping", "-h", "localhost"]
      interval: 10s

volumes:
  app-data:
  db-data:
```

```bash
# 常用 Docker 命令速查
docker build -t myapp:latest .                  # 构建镜像
docker run -d --name myapp -p 8080:8080 myapp   # 运行容器
docker exec -it myapp sh                        # 进入容器
docker logs -f myapp                            # 查看日志
docker stats                                    # 实时资源监控
docker system prune -af                         # 清理所有无用镜像/容器/网络
docker image prune -a                           # 清理悬空镜像
docker compose up -d                            # 启动 compose
docker compose down -v                          # 停止并删除卷
```

```bash
# 镜像优化 & 安全
docker scan myapp:latest                        # 镜像安全扫描
docker images --filter "dangling=true"          # 找悬空镜像
docker history myapp:latest                     # 查看镜像层历史
docker export <container> > container.tar       # 导出容器快照
```

## 🎯 面试高频 TOP10

1. **Q: 镜像分层原理？** **A:** 每个指令产生一层(只读)，层写时复制(CoW)，容器层可写；分层共享缓存，多镜像共用基础层减少磁盘和网络传输。
2. **Q: 镜像优化怎么做？** **A:** ① 多阶段构建(分离编译/运行) ② 选 Alpine 基础镜像(瘦身) ③ .dockerignore 排除无关文件 ④ 不变指令放前面(层缓存) ⑤ 减少层数(合并 RUN)
3. **Q: Docker Compose vs Swarm vs K8s？** **A:** Compose 单机多容器编排；Swarm 简单集群但功能弱(已边缘化)；K8s 生产级plete 集群管理(自动伸缩/滚动更新/自愈/服务发现)。
4. **Q: Namespace 和 Cgroup 是什么？** **A:** Namespace 隔离(PID/NET/IPC/MNT/UTS/USER)，Cgroup 限制资源(CPU/内存/IO)；两者是容器隔离的基础。
5. **Q: 容器安全实践？** **A:** ① 非 root 用户运行 ② 镜像定期扫描漏洞 ③ 最小权限(安全上下文/Seccomp) ④ 只读根文件系统 ⑤ 限制资源上限 ⑥ 私有镜像仓库(Harbor) 加签。
6. **Q: 数据持久化方式？** **A:** Volume 持久化(推荐，Docker 管理)、bind mount(宿主机路径映射)、tmpfs(内存，仅临时数据)；Volume 支持备份和迁移。
7. **Q: 网络模式怎么选？** **A:** bridge 默认(隔离，端口映射)；host 共享宿主机网络(性能好但端口冲突)；none 完全隔离；overlay 跨宿主机组网(K8s flannel/calico)。
8. **Q: 健康检查为什么重要？** **A:** 容器进程活着不代表服务正常，HEALTHCHECK 定期探测，失败后 Docker 可重启容器；K8s 区分 liveness 存活探针和 readiness 服务就绪探针。
9. **Q: 容器 CPU 限制原理？** **A:** --cpus 通过 CFS(完全公平调度器)配额实现，限制容器在若干时间片内最大 CPU 使用；--cpu-shares 控制相对权重，不设硬上限。
10. **Q: ENTRYPOINT vs CMD？** **A:** ENTRYPOINT 固定命令(可接收参数)，CMD 提供默认参数(可被覆盖)；组合 `ENTRYPOINT ["java","-jar"]` + `CMD ["app.jar"]` 最灵活。

## ⚠️ 常见坑 & 最佳实践

| ❌ 坑 | ✅ 正确做法 |
|-------|------------|
| 容器内以 root 运行 | 创建专用 app 用户，Dockerfile 里 USER 指令指定 |
| 在容器里存数据(无 volume) | 容器重启后数据丢失，必须挂载 volume 或 bind mount |
| 一个容器跑多个进程 | 每个容器一个进程(pid 1)，用 supervisord 违背原则 |
| 忘记 .dockerignore 大文件 | 写 .dockerignore 排除 node_modules/.git 等 |
| 用 latest 标签 | 用具体版本号(如 v1.0.0)，可追溯可回滚 |
| 多阶段构建但 COPY 整个 builder | 只 COPY 产物，不 COPY 依赖和源码 |
| 容器不加资源限制 | 必加 --memory/--cpus，防止单个容器拖垮宿主机 |
| 生产用 alpine 但缺 glibc 兼容性 | 测试环境预跑，确认 JDK/Go 等运行时兼容 musl |

## 📐 架构设计要点

- **镜像分层策略**：基础镜像(OS+JDK) → 依赖层(lib) → 配置层 → 业务层；不变层靠前，变层靠后，最大化缓存。
- **容器化原则**：单进程、无状态、可丢弃、配置环境变量化；日志 stdout/stderr。
- **CI/CD 集成**：Git 提交 → 自动构建 → 镜像推送 → 部署到 K8s；用 Harbor 做镜像仓库和扫描。
- **监控**：容器资源监控(cAdvisor/Prometheus) + 日志采集(Filebeat/Loki) 全链路。
- **安全**：扫描镜像漏洞 + 最小权限 + 内容信任(Sign) + 运行时安全(Falco)。

## 🔗 关联技术

- **K8s**：容器编排的工业标准，Pod/Deployment/Service/Ingress 是核心抽象。
- **Docker Compose**：本地开发/测试环境编排，生产环境建议用 K8s。
- **CI/CD**：GitLab CI/GitHub Actions + Docker 构建流水线。
- **监控**：Prometheus 采集容器指标 + Grafana 面板。
- **Nginx**：反向代理容器流量，SSL 终止，配合 Docker 多端口部署。