# AI 智能商城 — 16 技术栈贯穿实战手册

> 本手册是 16 技术栈教程体系的 **贯穿项目（Capstone Project）**，将 16 个独立技术栈整合为一条完整的电商系统实现链路。从零开始构建一个 AI 驱动的智能商城，覆盖 Java 微服务后端、Python AI 服务、中间件、基础设施和 DevOps 全流程。

---

## 一、项目全景架构

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                             用户层 (User)                                    │
│                  浏览器 (Vue3)  ·  移动端  ·  第三方 API                      │
└────────────────────────────────┬────────────────────────────────────────────┘
                                 │ HTTP / WebSocket
                                 ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                          API 网关层 (Gateway)                                │
│  Spring Cloud Gateway  ·  JWT 鉴权  ·  Sentinel 限流  ·  路由分发            │
│  /api/** → Java 微服务       /ai/** → Python AI 服务                         │
└────────────────┬─────────────────────────────────┬───────────────────────────┘
                 │                                 │
                 ▼                                 ▼
┌─────────────────────────────────┐   ┌───────────────────────────────────────┐
│   Java 微服务集群 (Spring Cloud) │   │   Python AI 服务集群 (FastAPI)        │
│                                 │   │                                       │
│  mall-user  ·  mall-product     │   │  AI 搜索 Agent  ·  RAG 客服           │
│  mall-order ·  mall-payment     │   │  LangChain  ·  LangGraph              │
│  mall-inventory · mall-seckill  │   │  向量检索  ·  Embedding  ·  多 Provider │
│  mall-es (搜索)  ·  mall-cart   │   │                                       │
└───────────────┬─────────────────┘   └──────────────────┬────────────────────┘
                │                                        │
                ▼                                        ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                              中间件层 (Middleware)                           │
│                                                                             │
│  MySQL · Redis · RocketMQ · Elasticsearch · MongoDB · Nacos · Seata         │
│  Redis Stack (向量) · Prometheus · Grafana · ElasticJob                     │
└─────────────────────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                           基础设施层 (Infrastructure)                        │
│                                                                             │
│  Docker · Docker Compose · Nginx · CI/CD (GitLab CI) · 日志收集 (ELK)       │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 二、16 技术栈与商城模块映射表

| 编号 | 技术栈 | 教程目录 | 商城模块 | 具体用途 |
|------|--------|---------|---------|---------|
| 1 | **后端开发总纲** | `01-backend-development/` | 全项目 | 架构设计原则、API 设计规范、分层架构 |
| 2 | **Java 核心** | `02-java/` | 所有 Java 微服务 | 集合、并发、JVM、IO 在微服务中的实践 |
| 3 | **Spring Boot** | `03-spring-boot/` | `mall-user/product/order/...` | 自动配置、AOP、事务、数据校验 |
| 4 | **Python 基础** | `04-python/` | AI 搜索服务、RAG 客服 | 异步编程、类型注解、数据类 |
| 5 | **FastAPI** | `05-fastapi/` | AI 搜索网关、RAG 服务 | Pydantic 模型、依赖注入、异步路由 |
| 6 | **MySQL** | `06-mysql/` | 用户、商品、订单数据库 | 表设计、索引优化、事务隔离 |
| 7 | **Redis** | `07-redis/` | 缓存、分布式锁、向量库 | 缓存策略、Redisson 锁、Redis Stack 向量 |
| 8 | **RocketMQ** | `08-rocketmq/` | 订单异步、秒杀削峰 | 事务消息、延迟消息、消息幂等 |
| 9 | **Elasticsearch** | `09-elasticsearch/` | 商品搜索 | 倒排索引、IK 分词、DSL 查询 |
| 10 | **Docker** | `10-docker/` | 全服务容器化 | Dockerfile、docker-compose、多阶段构建 |
| 11 | **Linux** | `11-linux/` | 部署环境 | 网络配置、进程管理、性能排查 |
| 12 | **基础设施** | `12-infrastructure/` | Nginx/CI-CD/监控 | 反向代理、流水线、Prometheus+Grafana |
| 13 | **开发工具** | `13-dev-tools/` | 全项目 | Git 分支策略、Maven 多模块、Conda 环境 |
| 14 | **LangChain** | `14-langchain/` | AI 搜索 Agent | Agent 机制、Tool 调用、结构化输出 |
| 15 | **RAG** | `15-rag/` | 智能客服 | 知识库构建、向量检索、检索增强生成 |
| 16 | **OpenAI** | `16-openai/` | AI 搜索、客服 | OpenAI 兼容协议、多 Provider 切换、成本优化 |

---

## 三、完整学习路径

### 路径 A：按技术栈顺序（推荐首次学习）

```
第 1 阶段：基础铺垫
  Java 核心 → Python 基础 → Linux → 开发工具
  └── 学完能看懂后端和 AI 服务的代码

第 2 阶段：后端核心
  Spring Boot → MySQL → Redis → RocketMQ → Elasticsearch
  └── 学完能搭建完整的商城微服务后端

第 3 阶段：AI 服务
  FastAPI → LangChain → RAG → OpenAI
  └── 学完能构建 AI 搜索和智能客服

第 4 阶段：基础设施
  Docker → 基础设施 (Nginx/CI-CD/监控)
  └── 学完能部署和运维整个系统

第 5 阶段：贯穿整合
  AI 商城贯穿项目 (本手册)
  └── 将所有技术栈串联为完整系统
```

### 路径 B：按角色分流

| 角色 | 学习重点 | 跳过 |
|------|---------|------|
| **Java 后端工程师** | 2-3, 6-9, 10-12 | 4-5, 14-16（可跳过 AI 细节） |
| **AI 应用工程师** | 4-5, 14-16, 10-12 | 2-3, 6-9（可跳过 Java 细节） |
| **全栈/架构师** | 全部技术栈 | 无 |

### 路径 C：项目驱动（逆向学习）

```
1. 先读本手册，了解全貌
2. 遇到不懂的技术栈，回到对应教程精读
3. 动手修改项目代码，加深理解
4. 重新回到本手册，串联所有知识点
```

---

## 四、项目模块概览

### Java 微服务（Spring Cloud Alibaba）

| 服务 | 端口 | 职责 | 核心依赖 |
|------|------|------|---------|
| `mall-gateway` | 8080 | 统一网关，路由 + 鉴权 + 限流 | Gateway, Sentinel, JWT |
| `mall-user-service` | 8081 | 用户注册登录、用户信息管理 | MyBatis-Plus, JWT |
| `mall-product-service` | 8082 | 商品 CRUD、分类品牌管理 | MyBatis-Plus, Seata |
| `mall-order-service` | 8083 | 订单创建、订单状态流转 | Seata, RocketMQ |
| `mall-payment-service` | 8084 | 支付对接、退款处理 | 支付宝 SDK, RocketMQ |
| `mall-inventory-service` | 8085 | 库存管理、库存扣减 | Redis, Seata |
| `mall-seckill-service` | 8086 | 秒杀活动、高并发库存扣减 | Redis, RocketMQ |
| `mall-es-service` | 8087 | Elasticsearch 商品搜索 | ES, MyBatis-Plus |
| `mall-cart-service` | 8088 | 购物车管理 | MongoDB |
| `mall-consumer-service` | 8089 | 消息消费处理 | RocketMQ |
| `mall-scheduler-service` | 8090 | 定时任务调度 | ElasticJob, ZK |

### Python AI 服务（FastAPI + LangChain）

| 服务 | 端口 | 职责 | 核心依赖 |
|------|------|------|---------|
| `ai-search-gateway` | 9010 | AI 搜索 API 网关，统一路由 | FastAPI, Pydantic |
| `ai-search-agent` | 9011 | LangChain Agent，搜索决策 | LangChain, LangGraph |
| `ai-rag-service` | 9012 | RAG 客服知识库问答 | LangChain, Chroma/Redis |
| `ai-embedding-service` | 9013 | Embedding 向量化服务 | BGE-M3, Sentence-Transformers |

---

## 五、贯穿项目阅读指南

本手册共 13 篇文章，分 5 个章节：

| 章节 | 文件 | 建议阅读时间 | 说明 |
|------|------|------------|------|
| 总览 | **README.md** | 15 分钟 | 全景架构和映射表 |
| 架构 | **01-architecture/01-system-overview.md** | 20 分钟 | 系统架构和技术选型 |
| 架构 | **01-architecture/02-request-lifecycle.md** | 20 分钟 | 请求生命周期和时序图 |
| Java 后端 | **02-java-backend/01-microservices.md** | 30 分钟 | 微服务设计和最佳实践 |
| Java 后端 | **02-java-backend/02-distributed-system.md** | 30 分钟 | 分布式系统组件 |
| AI 服务 | **03-ai-services/01-ai-search.md** | 25 分钟 | AI 搜索完整链路 |
| AI 服务 | **03-ai-services/02-rag-customer-service.md** | 25 分钟 | RAG 客服系统 |
| AI 服务 | **03-ai-services/03-llm-providers.md** | 20 分钟 | LLM 多 Provider 配置 |
| 基础设施 | **04-infrastructure/01-docker-deploy.md** | 25 分钟 | Docker 容器化部署 |
| 基础设施 | **04-infrastructure/02-devops-monitoring.md** | 25 分钟 | CI/CD 和监控 |
| 映射 | **05-stack-mapping/01-16-stack-map.md** | 15 分钟 | 技术栈完整映射表 |
| 映射 | **05-stack-mapping/02-interview-guide.md** | 30 分钟 | 面试话术和题库 |
| 扩展 | **challenges.md** | 20 分钟 | 进阶挑战和架构演进 |

**推荐阅读顺序：** 总览 → 架构 → Java 后端 → AI 服务 → 基础设施 → 映射 → 扩展

---

> **下一步：** 阅读 [01-architecture/01-system-overview.md](01-architecture/01-system-overview.md) 开始架构设计之旅。