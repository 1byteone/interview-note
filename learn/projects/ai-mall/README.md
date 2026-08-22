# AI Mall (AI 智能商城) — 全栈容器化部署指南

> 一站式 `docker compose up` 启动 **16 个技术栈组件**：
> 基础设施（MySQL / Redis / RocketMQ / Elasticsearch / Nacos）+
> Java 微服务（Gateway / User / Product / Order / Payment）+
> Python AI 服务（语义搜索 / RAG 客服）+
> 监控体系（Prometheus / Grafana / Nginx）。

---

## 📁 项目结构

```
ai-mall/
├── docker-compose.yml                 # 编排全部 16 个服务
├── .env                               # 环境变量（数据库密码、API Key 等）
├── .dockerignore                      # Docker 构建上下文忽略文件
├── Dockerfile.java                    # Java Spring Boot 多阶段构建
├── Dockerfile.python                  # Python FastAPI 多阶段构建
├── nginx/
│   ├── nginx.conf                     # 反向代理 / gzip / 限流 / CORS
│   ├── conf.d/ssl.conf                # HTTPS 配置模板（可选）
│   └── static/                        # 前端静态资源挂载目录
├── prometheus/
│   └── prometheus.yml                 # Prometheus 采集配置
├── grafana/
│   └── provisioning/
│       ├── datasources/               # 自动配置 Prometheus 数据源
│       └── dashboards/                # 自动加载监控面板
└── scripts/
    ├── init-db.sql                    # 数据库初始化（建表 + 示例数据）
    └── sync-products-to-es.py         # MySQL → Elasticsearch 数据同步
```

---

## 🏗️ 架构图

```
                                 ┌─────────────────────┐
                                 │   Nginx :80         │
                                 │ 反向代理 / 静态资源   │
                                 └──────────┬──────────┘
                                            │ /api/*
                                 ┌──────────▼──────────┐
                                 │ mall-gateway :8080  │
                                 │ Spring Cloud Gateway│
                                 └──┬───┬───┬───┬───┬──┘
                                    │   │   │   │   │
                  ┌─────────────────┘   │   │   │   └──────────────┐
                  │                     │   │   │                  │
        ┌─────────▼──────┐   ┌──────────▼──┐ │ ┌─────────▼──────┐  │
        │ mall-user      │   │ mall-product│ │ │ mall-order     │  │
        │ :8081          │   │ :8082       │ │ │ :8083          │  │
        └─────────┬──────┘   └──────┬──────┘ │ └─────────┬──────┘  │
                  │                 │        │           │         │
        ┌─────────▼──────┐   ┌──────▼──────┐ │ ┌─────────▼──────┐  │
        │ mall-payment   │   │ai-search    │ │ │ai-customer     │  │
        │ :8084          │   │service :8085│ │ │service :8086    │  │
        └─────────┬──────┘   └──────┬──────┘ │ └─────────┬──────┘  │
                  │                 │        │           │         │
        ──────────┼─────────────────┼────────┼───────────┼─────────┴─────
                  │                 │        │           │         (mall-network)
┌─────────────────▼─────────────────▼────────▼───────────▼──────────────┐
│                             中间件层 (mall-infra)                       │
│  MySQL:3306 │ Redis:6379 │ Nacos:8848 │ ES:9200 │ MQ:9876/10911       │
└──────────────────────────────────────────────────────────────────────────┘
              ▲                                        ▲
              │                                        │
      Prometheus :9090 ←── 采集指标 ────→ Grafana :3000
```

---

## ✅ 环境要求（Prerequisites）

| 依赖 | 最低要求 | 说明 |
|------|---------|------|
| Docker | **24.0+** | 支持 `docker compose` 子命令（Compose v2） |
| Docker Compose | v2.20+ | 支持 `depends_on.condition: service_healthy` |
| 内存 | **4GB+** （推荐 8GB） | ES(1G) + Nacos(768M) + RocketMQ(1G) + MySQL + Redis |
| 磁盘 | 5GB+ 可用空间 | 镜像 + 数据卷 |
| 端口 | 见下方端口表 | 13 个端口未被占用 |
| git bash / WSL2 | Windows 推荐 | 生产环境建议 Linux 服务器 |

> WSL2 + Docker Desktop 用户：确保在 Docker Desktop → Settings → Resources 中
> 分配至少 6GB 内存。

---

## 🚀 快速开始（Quick Start）

```bash
# 进入项目目录
cd learn/projects/ai-mall

# 1. 复制环境变量文件（按需修改密码、API Key）
cp .env.example .env   # 若 .env 已存在可跳过

# 2. 验证配置
docker compose config --quiet && echo "✓ 配置合法"

# 3. 一键启动全部服务
docker compose up -d

# 4. 查看启动进度（输出各服务状态）
docker compose ps

# 5. 跟踪日志（-f 持续输出，Ctrl+C 退出）
docker compose logs -f mall-gateway
```

等待所有服务的健康检查通过（healthcheck `service_healthy` 通过后才会启动依赖它的服务），
首次启动拉取镜像 + 初始化数据库 + 构建应用镜像大约需要 **5~15 分钟**。

---

## 🗄️ 服务端点一览（Service Endpoints）

### 基础设施层

| 服务 | 端口 | 访问地址 | 说明 |
|------|------|---------|------|
| MySQL | 3306 | `localhost:3306` | 数据库 `ai_mall`，用户 `mall_user` |
| Redis | 6379 | `localhost:6379` | 带密码 `redis_2024` |
| RocketMQ Namesrv | 9876 | `localhost:9876` | 命名服务 |
| RocketMQ Broker | 10911 | `localhost:10911` | 消息代理 |
| Elasticsearch | 9200 | http://localhost:9200 | 全文检索（Security 已禁用） |
| Nacos | 8848 | http://localhost:8848/nacos | 注册 & 配置中心 |

### Java 后端层

| 服务 | 端口 | Actuator 健康检查 |
|------|------|------------------|
| mall-gateway | 8080 | http://localhost:8080/actuator/health |
| mall-user-service | 8081 | http://localhost:8081/actuator/health |
| mall-product-service | 8082 | http://localhost:8082/actuator/health |
| mall-order-service | 8083 | http://localhost:8083/actuator/health |
| mall-payment-service | 8084 | http://localhost:8084/actuator/health |

### Python AI 层

| 服务 | 端口 | 健康检查 | 说明 |
|------|------|---------|------|
| ai-search-service | 8085 | http://localhost:8085/health | 语义搜索 |
| ai-customer-service | 8086 | http://localhost:8086/health | RAG 客服 |

### 监控运维层

| 服务 | 端口 | 访问地址 | 默认账号 |
|------|------|---------|---------|
| Prometheus | 9090 | http://localhost:9090 | — |
| Grafana | 3000 | http://localhost:3000 | `admin / admin` |
| Nginx | 80 | http://localhost | — |

---

## 🎯 首个服务验证

```bash
# 1. 验证 MySQL 已初始化（应看到 users 表）
docker compose exec mysql mysql -umall_user -pmall_pass_2024 ai_mall -e "SHOW TABLES;"

# 2. 验证 Nacos 服务注册（应看到 5 个 Java 服务）→ 浏览器访问
open http://localhost:8848/nacos

# 3. 验证 Elasticsearch 索引（首次需运行同步脚本）
open http://localhost:9200/_cat/indices?v

# 4. 验证网关路由（看日志中是否转发成功）
docker compose logs -f mall-gateway

# 5. 首页
open http://localhost
```

---

## 🦀 同步商品数据到 Elasticsearch（First-time Setup）

AI 搜索服务依赖 ES 中的商品索引。首次启动后需执行一次数据同步：

```bash
# 方式一：在宿主机执行（需要 Python 3.10+）
pip install pymysql elasticsearch
python scripts/sync-products-to-es.py --host 127.0.0.1

# 方式二：在容器内执行
docker compose exec ai-search-service python /app/sync-products-to-es.py

# 验证索引
curl 'http://localhost:9200/ai_mall_products/_count?pretty'
```

> 脚本支持增量同步：`--since` 参数只同步 updated_at 之后变更的商品，
> 配合 cron 或调度器可实现定时增量同步（见脚本 README 注释）。

---

## 🛠️ 单服务管理（Individual Service Management）

```bash
# 只重启某个服务
docker compose restart mall-product-service

# 只查看某个服务的日志
docker compose logs -f mall-order-service

# 只重建某个服务（代码改动后）
docker compose up -d --build mall-product-service

# 进入容器（调试）
docker compose exec mysql mysql -uroot -p mall_root_2024 ai_mall
docker compose exec redis redis-cli -a redis_2024
docker compose exec elasticsearch bash

# 暂停 / 恢复全部
docker compose stop          # 停止（保留容器与数据卷）
docker compose start         # 恢复
docker compose restart       # 全部重启

# 完整拆除（⚠️ 会删除容器，但保留数据卷）
docker compose down

# 彻底清除（⚠️ 连同数据卷一起删除，数据不可恢复！）
docker compose down -v

# 查看配置渲染结果
docker compose config
```

---

## 🔧 环境变量自定义（Environment Variables）

修改 `.env` 后执行 `docker compose up -d` 使配置生效：

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `MYSQL_ROOT_PASSWORD` | `mall_root_2024` | MySQL root 密码 |
| `MYSQL_DATABASE` | `ai_mall` | 业务库名 |
| `MYSQL_USER` / `MYSQL_PASSWORD` | `mall_user` / `mall_pass_2024` | 业务账号 |
| `REDIS_PASSWORD` | `redis_2024` | Redis 密码 |
| `ROCKETMQ_NAMESRV` | `rocketmq-namesrv:9876` | RocketMQ 地址（容器内地址） |
| `NACOS_SERVER` | `nacos:8848` | Nacos 服务地址 |
| `ES_HOST` | `elasticsearch:9200` | ES 地址 |
| `OPENAI_API_KEY` | `sk-your-api-key-here` | **必须替换**，否则 AI 功能受限 |
| `GF_SECURITY_ADMIN_PASSWORD` | `admin` | Grafana 登录密码 |

> ⚠️ **安全提醒**：修改密码后，需要 `docker compose down -v` 清除旧数据卷
> 并重新初始化（否则 MySQL 首次初始化已固化旧密码）。

---

## ❓ 故障排查（Troubleshooting）

### 1. 端口被占用

```bash
# 查看哪个进程占用端口
netstat -ano | findstr :8080    # Windows
lsof -i :8080                   # macOS / Linux
```
> 解决：修改 docker-compose.yml 中对应服务的 `ports` 映射，或关闭占用程序。

### 2. 容器启动失败 / 一直重启（Restarting）

```bash
# 查看具体报错
docker compose logs -f <service-name>
# 或查看最近 100 行
docker compose logs --tail=100 <service-name>
```

### 3. Java 服务启动报「Nacos 连接失败」

- 确认 nacos 健康检查通过：`docker compose ps` 中 nacos 状态为 `healthy`
- Java 服务 depends_on nacos + mysql + redis 三重条件，等待时间较长属正常
- 查看 Nacos 日志：`docker compose logs nacos`

### 4. Elasticsearch 启动失败（max virtual memory 报错）

宿主机执行（Linux）：
```bash
sudo sysctl -w vm.max_map_count=262144
```
Windows Docker Desktop 通常无需处理；若出现该错误，
在 Docker Desktop → Resources → Advanced 中调整内存。

### 5. MySQL 首次初始化失败

- init-db.sql 只会在数据卷**为空**时执行一次
- 若初始化中途失败，需清除数据卷重来：
  ```bash
  docker compose down -v
  docker compose up -d mysql
  ```

### 6. Grafana 无法登录

- 默认账号密码：`admin / admin`（来自 .env 的 `GF_SECURITY_ADMIN_PASSWORD`）
- 首次登录会强制修改密码；忘记密码时删除 grafana 数据卷重置：
  ```bash
  docker volume rm ai-mall_grafana-data
  ```

### 7. AI 服务报 OpenAI 认证错误

- `.env` 中 `OPENAI_API_KEY` 是占位符，请替换为真实 Key
- 修改后：`docker compose up -d --force-recreate ai-search-service ai-customer-service`

### 8. 健康检查一直 pending

- 首次启动需要下载镜像、初始化数据库，`start_period` 期间处于 `starting` 状态属正常
- 观察日志确认是在联网拉取镜像还是在等依赖

### 9. 端口扫描工具一览

```bash
# 一键查看所有容器健康状态
docker compose ps -a
```

---

## 💾 数据持久化说明（Data Persistence）

所有关键数据均挂载到 Docker 命名卷（named volumes），**容器删除数据不丢**：

| 数据卷 | 对应服务 | 存储内容 |
|--------|---------|---------|
| `mysql-data` | MySQL | 全部业务表数据 + init 脚本 |
| `redis-data` | Redis | RDB 快照 / AOF 日志 |
| `rocketmq-namesrv-data` | Namesrv | broker 路由信息 |
| `rocketmq-broker-data/logs` | Broker | 消息数据与日志 |
| `elasticsearch-data` | ES | 全部商品索引分片 |
| `nacos-data/logs` | Nacos | 注册表 + 配置 |
| `prometheus-data` | Prometheus | TSDB 时序数据 |
| `grafana-data` | Grafana | 面板配置 + 用户 |

查看数据卷占用：
```bash
docker volume ls
docker system df
```

**备份示例**（生产环境建议定期执行）：
```bash
# MySQL 逻辑备份
docker compose exec mysql mysqldump -uroot -p mall_root_2024 ai_mall > backup.sql
```

---

## 📚 常见问题 FAQ

**Q: 为什么有 14+ 个容器？这么多服务能跑起来吗？**
A: 这是教学/sandbox 环境设计。服务总量约 16 个容器，8GB 内存可流畅运行；
    生产环境请按职责拆分部署，且不要把监控混在同一 Docker 主机。

**Q: Java 服务构建失败？**
A: 确认 `Dockerfile.java` 的构建上下文。docker-compose 中 `build.context: .`
    指向 ai-mall 根目录，需保证子模块的 `target/*.jar` 已由 Maven 构建，
    或使用 CI 流水线先 `mvn package` 再 `docker build`。
    推荐：CI 中执行 `mvn clean package` 后 `docker compose build`。

**Q: 如何接入真实的 AI 模型？**
A: 1. `.env` 中配置 `OPENAI_API_KEY`；
    2. ai-search-service / ai-customer-service 的 FastAPI 应用读取该环境变量；
    3. 也可替换为兼容 OpenAI 协议的国内模型（如通义千问 DashScope），
    只需修改 base_url 与 model 名称。

**Q: 是否可以生产使用？**
A: 不适合直接生产：未配置 TLS 证书、CORS 为开发宽限模式、密码为示例值、
    没有日志采集（ELK/Loki）、未做容器安全加固（secret 管理、镜像扫描）。
    此为 capstone 学习项目，作为架构总览与技术栈整合演示。

---

## 🧭 技术栈清单（16 个组件）

| # | 组件 | 版本 | 角色 |
|---|------|------|------|
| 1 | MySQL | 8.0 | 主数据库 |
| 2 | Redis | 7-alpine | 缓存/分布式锁 |
| 3 | RocketMQ | 5.2.0 | 异步消息/订单事件 |
| 4 | Elasticsearch | 8.13.0 | 商品全文索引 |
| 5 | Nacos | 2.3.2 | 注册+配置中心 |
| 6 | Spring Cloud Gateway | 3.x | 网关路由/鉴权 |
| 7 | mall-user-service | Spring Boot 3.x | 用户微服务 |
| 8 | mall-product-service | Spring Boot 3.x | 商品微服务 |
| 9 | mall-order-service | Spring Boot 3.x | 订单微服务 |
| 10 | mall-payment-service | Spring Boot 3.x | 支付微服务 |
| 11 | ai-search-service | FastAPI | AI 语义搜索 |
| 12 | ai-customer-service | FastAPI | RAG 客服机器人 |
| 13 | Nginx | 1.26-alpine | 反向代理/静态资源 |
| 14 | Prometheus | 2.52.0 | 指标采集 |
| 15 | Grafana | 10.4.3 | 可视化面板 |
| 16 | Docker Compose V2 | — | 全部编排 |

---

*本项目为学习用 capstone project，用于演示微服务 + AI + 监控的完整技术栈整合。*
*遇到问题请先阅读上方 Troubleshooting 章节，或查看 `docker compose logs` 输出。*