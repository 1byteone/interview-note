# Java & AI 面试题汇总文档库

## 📚 项目简介

这是一个全面的 Java 后端 + AI 工程师面试题汇总文档库，涵盖从基础到高级的各个技术领域，支持基于真实项目自动生成面试题，并提供多种面试题形式。

## 🎯 核心特性

- **全面覆盖**：Java、Spring、Spring Cloud、中间件、Docker、Nginx、Redis、MySQL、AI 等
- **题型多样**：选择题、判断题、简答题、代码题、Bug题、场景题、系统设计题
- **项目驱动**：支持从 GitHub/本地 Java 项目自动生成面试题
- **AI 增强**：基于 RAG、Agent 技术实现智能面试和个性化学习

## 📁 目录结构

```
interview-note/
├── README.md                    # 本文件
├── java/                        # Java 核心知识
│   ├── core/                    # Java 基础
│   ├── jvm/                     # JVM 虚拟机
│   ├── juc/                     # 并发编程
│   ├── collections/             # 集合框架
│   └── io/                      # IO/NIO
├── spring/                      # Spring 生态
│   ├── spring-boot/             # Spring Boot
│   ├── spring-mvc/              # Spring MVC
│   └── spring-data/             # Spring Data
├── spring-cloud/                # Spring Cloud 微服务
│   ├── nacos/                   # Nacos 服务发现/配置
│   ├── gateway/                 # Gateway 网关
│   ├── openfeign/               # OpenFeign 远程调用
│   ├── sentinel/                # Sentinel 流量控制
│   ├── seata/                   # Seata 分布式事务
│   └── rocketmq/                # RocketMQ 消息队列
├── middleware/                  # 中间件
│   ├── redis/                   # Redis 缓存
│   ├── mysql/                   # MySQL 数据库
│   ├── elasticsearch/           # Elasticsearch 搜索
│   ├── kafka/                   # Kafka 消息队列
│   └── kibana/                  # Kibana 可视化
├── devops/                      # DevOps 部署
│   ├── docker/                  # Docker 容器
│   ├── nginx/                   # Nginx 反向代理
│   ├── nat-traversal/           # 内网穿透
│   └── ci-cd/                   # CI/CD 流水线
├── ai/                          # AI 工程师知识
│   ├── python/                  # Python 基础
│   ├── llm/                     # 大语言模型
│   ├── rag/                     # RAG 检索增强
│   ├── agent/                   # AI Agent
│   ├── agentic/                 # Agentic AI
│   ├── langgraph/               # LangGraph 编排
│   └── harness/                 # Harness 工程
├── interview-tools/             # 面试工具
│   ├── question-generator/      # 题目生成器
│   │   ├── choice/              # 选择题
│   │   ├── short-answer/        # 简答题
│   │   ├── coding/              # 代码题
│   │   ├── bug/                 # Bug题
│   │   ├── scenario/            # 场景题
│   │   ├── design/              # 设计题
│   │   └── deep-dive/           # 深挖题
│   └── mock-interview/          # 模拟面试
│       ├── interview-agent/     # 面试 Agent
│       └── evaluation/          # 评估系统
├── docs/                        # 文档库
│   └── tech-stack-analysis/     # 项目技术栈深度剖析
│       ├── mall-ai-search/      # mall-ai-search 智能搜索项目（11篇系列）
│       ├── mall-micro-cloud/    # mall-micro-cloud 微服务电商项目（11篇系列）
│       └── text2sql/            # text2sql Java Text2SQL+RAG 项目（7篇系列）
└── projects/                    # 项目实战
    ├── java-projects/           # Java 项目
    │   ├── ecommerce/           # 电商系统
    │   ├── social/              # 社交平台
    │   ├── blog/                # 博客系统
    │   └── admin/               # 后台管理
    └── ai-projects/             # AI 项目
        ├── rag-app/             # RAG 应用
        ├── chatbot/             # 聊天机器人
        └── agent-app/           # Agent 应用
```

## 🚀 快速开始

### 1. 查看面试题

每个目录下的 `README.md` 文件包含该领域的面试题汇总，按难度分级：

- **Level 1 (基础)**：适合初级工程师
- **Level 2 (进阶)**：适合中级工程师  
- **Level 3 (高级)**：适合高级工程师
- **Level 4 (专家)**：适合架构师

### 2. 使用面试题生成器

```bash
# 从 Java 项目生成面试题
python question-generator/main.py --project-path ./my-java-project --output ./output

# 指定题型
python question-generator/main.py --project-path ./my-java-project --type scenario,design
```

### 3. 模拟面试

```bash
# 启动 AI 面试 Agent
python mock-interview/main.py --role "Java Backend" --difficulty "senior"
```

## 📋 技术栈覆盖

### Java 后端
- ☕ Java 核心 (JVM, JUC, 集合, IO)
- 🌱 Spring 生态 (Boot, MVC, Data, Security)
- ☁️ Spring Cloud (Nacos, Gateway, OpenFeign, Sentinel, Seata, RocketMQ)
- 🗄️ 数据库 (MySQL, Redis, Elasticsearch)
- 📨 消息队列 (RocketMQ, Kafka)
- 🐳 容器化 (Docker, K8s)
- 🌐 网络 (Nginx, 内网穿透)

### AI 工程师
- 🐍 Python 基础
- 🤖 大语言模型 (LLM)
- 🔍 RAG 检索增强生成
- 🤝 AI Agent
- 🔄 Agentic AI
- 📊 LangGraph 编排
- ⚙️ Harness 工程

## 🎓 推荐学习路线

### Java 后端学习路线
```
Java 核心 → JVM → 并发编程 → Spring → Spring Boot → Spring Cloud → 中间件 → 项目实战
```

### AI 工程师学习路线
```
Python → LLM 基础 → RAG → Agent → Agentic → LangGraph → Harness → 项目实战
```

## 📖 项目技术栈深度剖析系列

从真实项目出发，逐层剖析技术栈——从基础到进阶再到项目实战，每篇附 Java/Spring 生态对照。

### 项目系列总览

| 项目 | 技术栈 | 文档 | 面试方向 |
|------|--------|------|---------|
| mall-ai-search | Python AI 搜索 (FastAPI + LangChain + RedisVL) | [11篇](docs/tech-stack-analysis/mall-ai-search/00-OVERVIEW.md) | AI + 向量检索 + Agent |
| mall-micro-cloud | Java Spring Cloud 微服务电商 (12个服务) | [11篇](docs/tech-stack-analysis/mall-micro-cloud/00-OVERVIEW.md) | 微服务 + 分布式 + 高并发 |
| text2sql | Java Text2SQL + RAG (Spring AI + DeepSeek) | [7篇](docs/tech-stack-analysis/text2sql/00-OVERVIEW.md) | Java AI + RAG + SQL 安全 |

### mall-ai-search（智能搜索项目）

> 一次 AI 搜索请求的完整链路：Vue3 → FastAPI → LangChain Agent → RedisVL → LLM 推荐

| # | 文档 | 核心栈 | 对照 Spring |
|---|------|--------|-------------|
| 00 | [全景导读](docs/tech-stack-analysis/mall-ai-search/00-OVERVIEW.md) | 架构总览 | — |
| 01 | [前端技术栈](docs/tech-stack-analysis/mall-ai-search/01-FRONTEND.md) | Vue3 + Axios + Promise.allSettled | — |
| 02 | [FastAPI 网关层](docs/tech-stack-analysis/mall-ai-search/02-API-GATEWAY.md) | FastAPI + Pydantic v2 | Spring Boot |
| 03 | [多 Provider 配置体系](docs/tech-stack-analysis/mall-ai-search/03-CONFIG-MULTI-PROVIDER.md) | pydantic-settings + 策略模式 | @ConfigurationProperties |
| 04 | [LLM 服务商对接](docs/tech-stack-analysis/mall-ai-search/04-LLM-PROVIDER.md) | 阿里云通义千问 + Agnes AI | Spring AI ChatClient |
| 05 | [Embedding 向量化](docs/tech-stack-analysis/mall-ai-search/05-EMBEDDING.md) | BGE-M3 + SiliconFlow | Spring AI EmbeddingClient |
| 06 | [RedisVL 向量存储](docs/tech-stack-analysis/mall-ai-search/06-VECTOR-STORE.md) | Redis Stack + HNSW | Spring Data Redis |
| 07 | [LangChain Agent 机制](docs/tech-stack-analysis/mall-ai-search/07-LANGCHAIN-AGENT.md) | create_agent + Tool | 工作流引擎 |
| 08 | [LangGraph 记忆与状态](docs/tech-stack-analysis/mall-ai-search/08-LANGGRAPH-MEMORY.md) | Checkpointer + InMemorySaver | 会话管理 |
| 09 | [数据同步链路](docs/tech-stack-analysis/mall-ai-search/09-DATA-SYNC.md) | MySQL → 切片 → Embedding → RedisVL | JPA + ETL |
| 10 | [架构复盘与面试题集](docs/tech-stack-analysis/mall-ai-search/10-ARCHITECTURE.md) | 20+ 面试题 + 架构模式 | 跨栈对比表 |

### mall-micro-cloud（Spring Cloud 微服务电商）

> 从一次"下单"请求出发，穿透 12 个微服务、9 大技术栈：Nacos / Gateway / Seata / Redisson / RocketMQ / JWT / MongoDB / ES

| # | 文档 | 核心栈 |
|---|------|--------|
| 00 | [全景导读](docs/tech-stack-analysis/mall-micro-cloud/00-OVERVIEW.md) | 架构总览 |
| 01 | [Nacos + Gateway 网关](docs/tech-stack-analysis/mall-micro-cloud/01-NACOS-GATEWAY.md) | Nacos, Gateway, Sentinel, 过滤器 |
| 02 | [公共模块与统一架构](docs/tech-stack-analysis/mall-micro-cloud/02-COMMON-ARCH.md) | Result, 异常处理, Feign, 拦截器 |
| 03 | [商品服务与 MyBatis-Plus](docs/tech-stack-analysis/mall-micro-cloud/03-PRODUCT-MYBATISPLUS.md) | MyBatis-Plus, SPU/SKU |
| 04 | [订单服务与 Seata 分布式事务](docs/tech-stack-analysis/mall-micro-cloud/04-ORDER-SEATA.md) | Seata AT, @GlobalTransactional |
| 05 | [购物车服务与 MongoDB](docs/tech-stack-analysis/mall-micro-cloud/05-CART-MONGODB.md) | MongoDB, NoSQL 选型 |
| 06 | [秒杀服务与高并发](docs/tech-stack-analysis/mall-micro-cloud/06-SECKILL-HIGHCONCUR.md) | Redisson, 布隆过滤器, 库存双写 |
| 07 | [用户服务与 JWT 鉴权](docs/tech-stack-analysis/mall-micro-cloud/07-USER-JWT.md) | JWT, 拦截器, 无感续期 |
| 08 | [ES 搜索服务](docs/tech-stack-analysis/mall-micro-cloud/08-ES-SEARCH.md) | Elasticsearch, 倒排索引 |
| 09 | [RocketMQ 消息驱动](docs/tech-stack-analysis/mall-micro-cloud/09-ROCKETMQ.md) | RocketMQ, StreamBridge, 最终一致性 |
| 10 | [架构复盘与面试题集](docs/tech-stack-analysis/mall-micro-cloud/10-ARCHITECTURE.md) | 20+ 面试题 + 能力雷达图 |
| 11 | [定时任务 + 布隆过滤器 + MQ 幂等消费](docs/tech-stack-analysis/mall-micro-cloud/11-SCHEDULER-BLOOMFILTER.md) | ElasticJob, Redisson 布隆过滤器 |
| 12 | [AI 搜索桥接服务](docs/tech-stack-analysis/mall-micro-cloud/12-AI-SEARCH-BRIDGE.md) | Feign → Python AI 桥接 |

### text2sql（Java Text2SQL + RAG）

> 用户输入自然语言 → 系统自动生成 SQL → 执行返回结果。纯 Java 实现的 AI 实战项目：Spring AI + DeepSeek + RAG

| # | 文档 | 核心栈 |
|---|------|--------|
| 00 | [全景导读](docs/tech-stack-analysis/text2sql/00-OVERVIEW.md) | 架构总览 |
| 01 | [Spring AI + DeepSeek LLM 集成](docs/tech-stack-analysis/text2sql/01-LLM-CLIENT.md) | ChatClient, 多供应商抽象 |
| 02 | [Embedding 与向量存储](docs/tech-stack-analysis/text2sql/02-EMBEDDING-VECTOR.md) | EmbeddingModel, VectorStore |
| 03 | [RAG 检索增强](docs/tech-stack-analysis/text2sql/03-RAG-RETRIEVAL.md) | 混合检索, 融合排序 |
| 04 | [Prompt 工程与 Schema 增强](docs/tech-stack-analysis/text2sql/04-PROMPT-SCHEMA.md) | M-Schema, Few-shot |
| 05 | [SQL 验证器四层防护](docs/tech-stack-analysis/text2sql/05-SQL-VALIDATOR.md) | 语法/安全/语义/性能验证 |
| 06 | [对话管理与上下文压缩](docs/tech-stack-analysis/text2sql/06-CONVERSATION.md) | 多轮对话, 滑动窗口 |
| 07 | [架构复盘与面试题集](docs/tech-stack-analysis/text2sql/07-ARCHITECTURE.md) | 三项目对比 + 面试题 |

## 🔗 推荐资源

### 开源项目
- [interview-guide](https://github.com/Snailclimb/interview-guide) - AI 模拟面试系统
- [Spring-Cloud-Alibaba-Practice](https://github.com/scottxing/Spring-Cloud-Alibaba-Practice) - Spring Cloud Alibaba 实践
- [langgraph-101](https://github.com/langchain-ai/langgraph-101) - LangGraph 学习项目
- [harness-skills](https://github.com/lispking/harness-skills) - Agent Harness 技能

### 学习网站
- [JavaGuide](https://javaguide.cn/) - Java 面试指南
- [Spring 官方文档](https://spring.io/projects/spring-cloud-alibaba)
- [LangChain 文档](https://python.langchain.com/)

## 📝 贡献指南

欢迎贡献面试题和学习资源！请遵循以下格式：

```markdown
### 题目标题

**难度**：Level 1/2/3/4
**类型**：选择题/简答题/代码题/场景题/设计题
**考察点**：XXX

**问题**：
...

**答案**：
...

**解析**：
...
```

## 📄 许可证

MIT License

---

## 📚 16 技术栈教程体系 (learn/)

> 16 个技术栈 · 262 篇教程文档 · 12 张 SVG 架构图 · 204 个可运行代码示例 · AI 智能商城贯穿项目

本仓库的 [`learn/`](./learn/) 目录是一套完整的 **16 技术栈双轨教程体系**，从入门到面试，项目驱动，STAR 法则贯穿。与 `docs/tech-stack-analysis/`（项目实战剖析）互补，形成「知识输入 → 项目输出」的闭环学习体验。

### 🗺️ 三条学习路线

| 路线 | 适合人群 | 目标 | 学习路径 |
|------|---------|------|---------|
| 👶 **零基础入门路线** | 转行、应届生、非科班 | 从零到能干活 | `backend-dev → Java → Linux → Git → Spring Boot → MySQL → Redis → Docker → Python → FastAPI → LangChain → RAG → OpenAI` |
| 🎯 **面试突击路线** | 1-3 年经验、准备跳槽 | 快速准备面试 | 直接进入各栈 `05-interview/` 目录，按需回溯基础 |
| 🚀 **全栈进阶路线** | 有后端基础、拓展 AI 能力 | Java 后端 + AI 双修 | 按编号 01→16 依次推进，每栈完成五个阶段 |

### 📊 16 技术栈一览

| # | 技术栈 | 目录 | 文件数 | 核心内容 |
|---|--------|------|--------|---------|
| 01 | 🌐 后端开发基础 | `01-backend-development/` | 14 | 架构设计、API 设计、分层架构、项目实战 |
| 02 | ☕ Java 核心 | `02-java/` | 18 | OOP、集合、JVM、JUC、IO/NIO、新特性 |
| 03 | 🚀 Spring Boot | `03-spring-boot/` | 17 | 自动配置、启动流程、Actuator、测试、GraalVM |
| 04 | 🐍 Python 基础 | `04-python/` | 15 | 语法、OOP、异步编程、类型注解、AI 生态 |
| 05 | ⚡ FastAPI | `05-fastapi/` | 17 | 路由、DI、Pydantic、WebSocket、生产部署 |
| 06 | 🗄️ MySQL | `06-mysql/` | 17 | 高级 SQL、索引原理、事务 MVCC、分库分表 |
| 07 | 🧩 Redis | `07-redis/` | 17 | 数据类型、持久化、缓存策略、分布式锁、集群 |
| 08 | 📨 RocketMQ | `08-rocketmq/` | 17 | 消息类型、事务消息、可靠性、高可用、MQ 对比 |
| 09 | 🔍 Elasticsearch | `09-elasticsearch/` | 15 | DSL 搜索、倒排索引、集群架构、数据同步 |
| 10 | 🐳 Docker | `10-docker/` | 17 | Dockerfile、镜像、Compose、Swarm/K8s、CI/CD |
| 11 | 🐧 Linux | `11-linux/` | 15 | 常用命令、Shell 脚本、网络排查、性能调优 |
| 12 | 🏗️ 基础设施 | `12-infrastructure/` | 17 | Nginx、服务发现、网关、Sentinel、Seata、监控 |
| 13 | 🛠️ 开发工具 | `13-dev-tools/` | 11 | Git、Conda、Jupyter 三个工具全覆盖 |
| 14 | 🔗 LangChain | `14-langchain/` | 17 | LCEL、Chain、Memory、Tool、Agent、LangGraph |
| 15 | 📚 RAG | `15-rag/` | 17 | Embedding、分块策略、检索策略、Graph RAG |
| 16 | 🤖 OpenAI API | `16-openai/` | 17 | Prompt 工程、Function Calling、微调、Assistants |
| — | 🏪 AI 智能商城 | `projects/ai-mall/` | 12 | 贯穿项目：全栈 16 栈集成手册 |

> **总计**：262 篇教程文档 + 12 张 SVG 架构图 + 204 个可运行代码示例 + 贯穿项目 12 篇

### ✨ 设计特色

| 特色 | 说明 |
|------|------|
| **🎯 双轨制** | 每个技术栈同时提供 👶 新手入门版和 🎯 面试进阶版，同一内容两种视角 |
| **⭐ STAR 法则驱动** | 面试答案全部按 Situation-Task-Action-Result 框架组织，告别八股文 |
| **🏪 16 栈 × AI 商城贯穿** | 每个技术栈都在 AI 智能商城中找到落地场景，学完即拥有完整项目 |
| **💻 可运行代码示例** | 16 个技术栈均提供 `examples/` 目录，含 Maven/Gradle 工程和 Python 脚本，开箱即跑 |
| **📐 12 张 SVG 架构图** | 核心流程全部配有架构图：JVM 内存、B+ 树索引、RocketMQ 事务消息、微服务架构等 |
| **📋 面试四件套** | 每个栈的 `05-interview/` 包含：速记卡 + 深挖题 + 场景题 + 代码题 |

### 📁 目录结构

```
learn/
├── 00-ROADMAP/              ← 学习路线总纲
├── 01-backend-development/  ← 后端开发基础
├── 02-java/                 ← Java 核心
├── 03-spring-boot/          ← Spring Boot
├── 04-python/               ← Python 基础
├── 05-fastapi/              ← FastAPI
├── 06-mysql/                ← MySQL
├── 07-redis/                ← Redis
├── 08-rocketmq/             ← RocketMQ
├── 09-elasticsearch/        ← Elasticsearch
├── 10-docker/               ← Docker
├── 11-linux/                ← Linux 基础
├── 12-infrastructure/       ← 基础设施
├── 13-dev-tools/            ← 开发工具
├── 14-langchain/            ← LangChain
├── 15-rag/                  ← RAG 检索增强生成
├── 16-openai/               ← OpenAI API
└── projects/                ← 贯穿项目
    └── ai-mall/             ← AI 智能商城完整项目
```

每个技术栈内部结构（以 `03-spring-boot/` 为例）：

```
03-spring-boot/
├── 01-basics/          ← 基础概念（👶 入门必读）
├── 02-core/            ← 核心原理（👶 必读）
├── 03-advanced/        ← 进阶源码（🚀 进阶选读）
├── 04-projects/        ← 商城实战（👶 必做）
│   └── mini-blog/      ← 过渡项目
├── 05-interview/       ← 面试专项（🎯 面试必读）
│   ├── quick-revision.md   ← 速记卡
│   ├── deep-dive.md        ← 深挖题
│   ├── scenario.md         ← 场景题
│   └── coding.md           ← 代码题
└── resources/          ← 参考资料
```

### 🚀 如何使用

| 你的角色 | 推荐路径 |
|---------|---------|
| 🧑‍💻 **零基础新手** | 从 `00-ROADMAP/` 开始 → 选一条学习路线 → 按栈依次推进 `01-basics → 02-core → 04-projects` |
| 🎯 **面试准备者** | 直接进入目标栈的 `05-interview/` → 速记卡 → 场景题 → 深挖题 → 代码题 |
| 🚀 **进阶学习者** | 按编号 01→16 全部完成，重点攻克 `03-advanced/` 源码分析 |
| 🏗️ **项目实践者** | 直接进入 `projects/ai-mall/`，按需回溯各栈基础 |

### 🔗 快速链接

| 入口 | 链接 |
|------|------|
| 🗺️ 总路线图 | [`learn/00-ROADMAP/README.md`](./learn/00-ROADMAP/README.md) |
| 🏪 AI 智能商城贯穿项目 | [`learn/projects/ai-mall/README.md`](./learn/projects/ai-mall/README.md) |
| ☕ Java 核心教程 | [`learn/02-java/README.md`](./learn/02-java/README.md) |
| 🚀 Spring Boot 教程 | [`learn/03-spring-boot/README.md`](./learn/03-spring-boot/README.md) |
| 🐍 Python 基础教程 | [`learn/04-python/README.md`](./learn/04-python/README.md) |
| ⚡ FastAPI 教程 | [`learn/05-fastapi/README.md`](./learn/05-fastapi/README.md) |
| 🗄️ MySQL 教程 | [`learn/06-mysql/README.md`](./learn/06-mysql/README.md) |
| 🧩 Redis 教程 | [`learn/07-redis/README.md`](./learn/07-redis/README.md) |
| 🐳 Docker 教程 | [`learn/10-docker/README.md`](./learn/10-docker/README.md) |
| 🔗 LangChain 教程 | [`learn/14-langchain/README.md`](./learn/14-langchain/README.md) |
| 📚 RAG 教程 | [`learn/15-rag/README.md`](./learn/15-rag/README.md) |

---

> 💡 本项目旨在帮助开发者系统化准备 Java 后端和 AI 工程师面试，祝你面试顺利！
