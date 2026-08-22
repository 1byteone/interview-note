# 01 · 16 技术栈与商城模块映射表

> 本文是贯穿项目的"索引表"，展示 16 个技术栈如何映射到 AI 智能商城的各个模块，以及每个技术栈的推荐学习顺序。

---

## 一、完整映射表

| 编号 | 技术栈 | 教程目录 | 商城模块 | 具体用途 | 推荐学习顺序 |
|------|--------|---------|---------|---------|:----------:|
| 1 | **后端开发总纲** | `01-backend-development/` | 全项目 | 架构设计原则、API 规范、分层架构、微服务 vs 单体 | 1 |
| 2 | **Java 核心** | `02-java/` | 所有 Java 微服务 | 集合框架（HashMap 在缓存中的使用）、并发（JUC 在秒杀中的应用）、JVM（GC 调优） | 2 |
| 3 | **Spring Boot** | `03-spring-boot/` | mall-user, mall-product, mall-order 等 | 自动配置、AOP 日志、事务管理、数据校验、@Cacheable 缓存 | 3 |
| 4 | **Python 基础** | `04-python/` | AI 搜索服务、RAG 客服 | 异步编程（async/await）、类型注解（Pydantic 模型）、数据类 | 2 |
| 5 | **FastAPI** | `05-fastapi/` | AI 搜索网关、RAG 服务 | Pydantic 模型校验、APIRouter 模块化、全局异常处理、依赖注入 | 4 |
| 6 | **MySQL** | `06-mysql/` | 用户、商品、订单数据库 | 表设计（用户/商品/订单/库存）、索引优化、事务隔离级别、分库分表 | 3 |
| 7 | **Redis** | `07-redis/` | 所有服务 | 缓存（Cache-Aside）、分布式锁（Redisson）、秒杀库存预扣、Redis Stack 向量库 | 4 |
| 8 | **RocketMQ** | `08-rocketmq/` | mall-order, mall-seckill, mall-consumer | 事务消息（订单创建）、延迟消息（超时取消）、削峰填谷（秒杀）、消息幂等 | 5 |
| 9 | **Elasticsearch** | `09-elasticsearch/` | mall-es-service | 商品全文搜索、IK 分词器、DSL 查询、MySQL 数据同步 | 5 |
| 10 | **Docker** | `10-docker/` | 全服务容器化 | Dockerfile 多阶段构建、docker-compose 编排、健康检查、环境一致性 | 6 |
| 11 | **Linux** | `11-linux/` | 部署环境 | 网络配置（iptables/防火墙）、进程管理（systemd）、性能排查（top/vmstat） | 1 |
| 12 | **基础设施** | `12-infrastructure/` | Nginx/CI-CD/监控 | Nginx 反向代理、GitLab CI 流水线、Prometheus + Grafana、ELK 日志 | 6 |
| 13 | **开发工具** | `13-dev-tools/` | 全项目 | Git 分支策略（Feature Branch + Merge Request）、Maven 多模块、Conda 环境 | 1 |
| 14 | **LangChain** | `14-langchain/` | AI 搜索 Agent | Agent 机制（create_agent）、Tool 定义、结构化输出（response_format）、Chain | 7 |
| 15 | **RAG** | `15-rag/` | RAG 智能客服 | 知识库构建、文档切片、向量检索、检索增强生成、证据门控 | 7 |
| 16 | **OpenAI** | `16-openai/` | AI 搜索、客服 | OpenAI 兼容协议、多 Provider 切换、温度参数、成本优化 | 7 |

---

## 二、技术栈在商城中的具体用途

### 2.1 Java 技术栈（后端核心）

| 技术栈 | 商城中的具体用途 | 代码文件/模块 |
|--------|----------------|-------------|
| **Java 集合** | `HashMap` 缓存商品分类、`ConcurrentHashMap` 本地缓存、`PriorityQueue` 秒杀排队 | `mall-product-service` |
| **Java 并发** | `CompletableFuture` 并行查询、`ThreadPoolExecutor` 异步任务、`ReentrantLock` 秒杀锁 | `mall-seckill-service` |
| **JVM** | 堆内存调优（秒杀大流量）、GC 日志分析、OOM 排查 | `JVM 参数配置` |
| **Spring Boot** | `@RestController` 控制器、`@Service` 服务层、`@Transactional` 事务、`@Cacheable` 缓存 | 所有 Java 服务 |
| **Spring AOP** | 统一日志切面、性能监控切面、权限校验切面 | `mall-common` |
| **MyBatis-Plus** | 代码生成器、Wrapper 条件构造、分页插件 | 所有 Java 服务 |

### 2.2 中间件技术栈

| 技术栈 | 商城中的具体用途 | 配置/实现 |
|--------|----------------|---------|
| **MySQL** | 用户表、商品表、订单表、库存表、日志表 | `init.sql` |
| **Redis 缓存** | 商品缓存（Cache-Aside）、用户 Session、分类树 | `@Cacheable` |
| **Redis 分布式锁** | 秒杀库存扣减、幂等校验、任务调度锁 | `Redisson` |
| **Redis 向量库** | AI 搜索商品向量索引、RAG 知识库索引 | `Redis Stack` |
| **RocketMQ** | 订单超时取消（延迟消息）、支付回调（事务消息）、秒杀削峰 | `RocketMQTemplate` |
| **Elasticsearch** | 商品全文搜索、搜索建议、搜索结果聚合 | `mall-es-service` |

### 2.3 Python AI 技术栈

| 技术栈 | 商城中的具体用途 | 代码文件 |
|--------|----------------|---------|
| **FastAPI** | AI 搜索 API 网关、RAG 服务 API | `main.py`, `api/v1.py` |
| **Pydantic** | 统一响应体 Result[T]、搜索条件 SearchCondition、推荐结果 | `schemas.py` |
| **LangChain** | create_agent 创建 Agent、Tool 定义、Chain 条件提取 | `search_service.py` |
| **LangGraph** | Checkpointer 会话记忆、Agent 执行循环 | `search_service.py` |
| **RedisVL** | 向量索引管理、相似度搜索、文档管理 | `tools.py` |
| **OpenAI 兼容** | 阿里云通义千问、SiliconFlow BGE-M3、OpenRouter 备选 | `settings.py`, `tools.py` |

### 2.4 基础设施技术栈

| 技术栈 | 商城中的具体用途 | 配置 |
|--------|----------------|------|
| **Docker** | 11 个 Java 服务 + 4 个 Python 服务 + 8 个中间件容器化 | `Dockerfile` |
| **Docker Compose** | 一站式启动全部服务 | `docker-compose.yml` |
| **Nginx** | 反向代理、SSL 终结、静态资源、负载均衡 | `nginx.conf` |
| **GitLab CI** | 自动测试、构建、部署 | `.gitlab-ci.yml` |
| **Prometheus** | 指标收集、告警规则 | `prometheus.yml` |
| **Grafana** | 业务监控大屏、系统资源监控 | 仪表盘配置 |

---

## 三、每个技术栈的推荐学习路径

### 第一阶段：基础铺垫（1-2 周）

```
1. 后端开发总纲 → 理解架构设计原则
2. Java 核心 → 集合、并发、JVM
3. Python 基础 → 异步编程、类型注解
4. Linux → 基本命令、网络配置
5. 开发工具 → Git 分支策略、Maven 多模块
```

### 第二阶段：后端核心（2-3 周）

```
6. MySQL → 表设计、索引优化、事务
7. Spring Boot → 自动配置、AOP、事务、缓存
8. Redis → 缓存策略、分布式锁、数据结构
9. FastAPI → Python Web 框架、Pydantic 校验
```

### 第三阶段：中间件与消息（1-2 周）

```
10. RocketMQ → 事务消息、延迟消息、消息幂等
11. Elasticsearch → 倒排索引、分词、DSL 查询
```

### 第四阶段：AI 核心（2-3 周）

```
12. LangChain → Agent 机制、Tool 调用、Chain
13. RAG → 知识库构建、检索增强生成
14. OpenAI → 多 Provider 切换、成本优化
```

### 第五阶段：部署与运维（1 周）

```
15. Docker → 容器化、镜像构建、docker-compose
16. 基础设施 → CI/CD、监控、日志
```

---

## 四、模块间依赖关系

```
┌──────────────────────────────────────────────────────────────────────────┐
│                      模块依赖关系图                                        │
│                                                                          │
│  mall-user ──→ Redis (缓存) ──→ MySQL (持久化)                           │
│                                                                          │
│  mall-product ──→ Redis (缓存) ──→ MySQL (持久化)                        │
│      │                                   │                               │
│      │ Canal 同步                        │ ElasticJob 定时同步             │
│      ▼                                   ▼                               │
│  mall-es ──→ Elasticsearch (商品搜索索引)                                 │
│                                                                          │
│  mall-order ──→ RocketMQ (延迟消息) ──→ mall-consumer (超时取消)          │
│      │                                                                   │
│      │ Seata 分布式事务                                                   │
│      ▼                                                                   │
│  mall-inventory ──→ Redis (预扣库存) ──→ MySQL (乐观锁兜底)               │
│                                                                          │
│  mall-seckill ──→ Redis (预扣) ──→ RocketMQ (削峰) ──→ MySQL (乐观锁)    │
│                                                                          │
│  AI Search ──→ Redis Stack (向量库) ──→ LLM API (推理)                   │
│      │                                                                   │
│      │ 数据同步                                                          │
│      ▼                                                                   │
│  MySQL (sku_info) ──→ 切片 ──→ Embedding ──→ RedisVL                     │
└──────────────────────────────────────────────────────────────────────────┘
```

---

> **下一篇：** [02-interview-guide.md](02-interview-guide.md) — 面试指南