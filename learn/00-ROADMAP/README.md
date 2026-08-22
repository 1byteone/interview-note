# 16 技术栈学习路线图 — AI 智能商城贯穿实战

> 从入门到面试，项目驱动，STAR 法则，干中学

---

## 一、概览

本教程系统覆盖 **16 个技术栈**，以 **AI 智能商城**为贯穿项目，从零构建一个融合传统后端 + AI 能力的完整应用。每个技术栈按「基础 → 核心 → 进阶 → 项目实战 → 面试」五段式结构组织，形成闭环学习体验。

### 核心理念

| 原则 | 说明 |
|------|------|
| **项目驱动** | 每学一个技术栈，立刻在商城中找到它的落地场景 |
| **STAR 法则** | 面试场景用 Situation-Task-Action-Result 框架组织答案 |
| **双轨制** | 同一内容同时提供 👶 入门版和 🎯 面试版 |
| **干中学** | 理论比例不超过 30%，70% 为动手实践 |

---

## 二、16 技术栈一览

| # | 图标 | 技术栈 | 难度 | 学习时长 | 前置要求 | 商城角色 | 文档状态 |
|---|------|--------|------|----------|----------|----------|----------|
| 01 | 🌐 | 后端开发基础 | ★☆☆☆☆ | 3 天 | 无 | 理解商城的架构分层 | ✅ 已完成 |
| 02 | ☕ | Java 核心 | ★★☆☆☆ | 5 天 | 01 | 商城后端主体语言 | ✅ 已完成 |
| 03 | 🚀 | Spring Boot | ★★★☆☆ | 5 天 | 02 | 商城的服务端框架 | 待编写 |
| 04 | 🐍 | Python 基础 | ★★☆☆☆ | 4 天 | 无 | AI 服务端语言 | ✅ 已完成 |
| 05 | ⚡ | FastAPI | ★★☆☆☆ | 3 天 | 04 | AI 网关服务 | 待编写 |
| 06 | 🗄️ | MySQL | ★★★☆☆ | 5 天 | 02/03 | 商城核心关系数据库 | 待编写 |
| 07 | 🧩 | Redis | ★★★☆☆ | 4 天 | 03 | 缓存、Session、限流 | 待编写 |
| 08 | 📨 | RocketMQ | ★★★☆☆ | 3 天 | 03 | 异步消息、订单解耦 | 待编写 |
| 09 | 🔍 | Elasticsearch | ★★★☆☆ | 3 天 | 03/06 | 商品搜索、日志分析 | 待编写 |
| 10 | 🐳 | Docker | ★★☆☆☆ | 3 天 | 11 | 商城容器化部署 | 待编写 |
| 11 | 🐧 | Linux | ★★☆☆☆ | 3 天 | 无 | 商城服务器环境 | ✅ 已完成 |
| 12 | 🏗️ | 基础设施 | ★★★★☆ | 4 天 | 10/11 | CI/CD、监控、网关 | 待编写 |
| 13 | 🛠️ | 开发工具 | ★☆☆☆☆ | 2 天 | 无 | Git、IDE、调试 | ✅ 已完成 |
| 14 | 🔗 | LangChain | ★★★★☆ | 4 天 | 04/05 | AI 智能客服、Agent | 待编写 |
| 15 | 📚 | RAG | ★★★★☆ | 3 天 | 14 | 商品知识库问答 | 待编写 |
| 16 | 🤖 | OpenAI API | ★★★☆☆ | 3 天 | 04 | LLM 接入、Embedding | 待编写 |

**难度说明**：★ 越多难度越大，★☆☆☆☆ 为入门级，★★★★☆ 为高级。

**学习时长**：以每天投入 2-3 小时计算，总计约 **8 周**。

---

## 三、三条学习路线

### 👶 零基础入门路线

> 从零到能干活，适合转行、应届生、非科班读者

```
backend-dev → Java → Linux → Git → Spring Boot → MySQL → Redis → Docker → Python → FastAPI → LangChain → RAG → OpenAI
```

**路线说明**：先建立后端开发的世界观，再从 Java 入手掌握主流后端技术栈，同时学习 Linux 和 Git 作为基本功。后半段切入 Python 和 AI 技术栈，最终实现「Java 后端 + AI 能力」的双修目标。

**里程碑**：
- 第 1 周：完成后端基础 + Java 核心 + Git
- 第 2 周：Spring Boot 入门 + MySQL 基础
- 第 3 周：Redis + Docker + Linux 基础
- 第 4 周：Python + FastAPI 入门
- 第 5 周：LangChain + RAG 基础
- 第 6 周：OpenAI API 接入 + 商城 AI 功能集成

### 🎯 面试突击路线

> 快速准备面试，适合有 1-3 年经验、准备跳槽的读者

```
Java 核心面试 → Spring Boot 面试 → MySQL 面试 → Redis 面试 → RocketMQ 面试 → ES 面试 → 系统设计 → AI 面试
```

**路线说明**：跳过基础理论和环境搭建，直接进入各技术栈的「05-interview」目录。每个面试模块都包含：
- 高频面试题（含 STAR 答案模板）
- 场景题（如「Redis 缓存穿透如何解决」）
- 手撕代码（LeetCode 风格 + 业务场景）
- 系统设计题（如「设计一个秒杀系统」）

**里程碑**：
- 第 1 周：Java + Spring Boot 面试核心
- 第 2 周：MySQL + Redis 面试 + 场景题
- 第 3 周：RocketMQ + ES 面试 + 系统设计
- 第 4 周：AI 面试 + 综合模拟

### 🚀 全栈进阶路线

> Java 后端 + AI 双修，适合有后端基础、希望拓展 AI 能力的读者

**完整 16 栈顺序学习**：按编号 01 → 16 依次推进，每栈完成「基础 → 核心 → 进阶 → 项目 → 面试」五个阶段。

**路线说明**：这是最完整的路线，覆盖全部 16 个技术栈。前半段（01-13）建立扎实的后端工程能力，后半段（14-16）进入 AI 应用开发。每个技术栈都通过 AI 智能商城项目串联，学完即拥有一个可展示的完整项目。

**里程碑**：
- **Phase 1 基础组**（5 栈，约 2 周）：后端基础 → Java → Python → Linux → 开发工具
- **Phase 2 后端核心组**（5 栈，约 3 周）：Spring Boot → MySQL → Redis → RocketMQ → ES
- **Phase 3 AI 核心组**（4 栈，约 3 周）：Docker → FastAPI → LangChain → RAG → OpenAI
- **Phase 4 基础设施组**（2 栈 + 贯穿项目，约 2 周）：基础设施 → 商城全栈集成

---

## 四、分阶段学习计划

### Phase 1：基础组（5 栈）— 已完成 ✅

| 技术栈 | 核心目标 | 商城产出 | 状态 |
|--------|----------|----------|------|
| 01 后端开发基础 | 理解架构分层、API 设计、RESTful | 绘制商城架构图 | ✅ 13 文件 |
| 02 Java 核心 | 集合、并发、JVM 基础 | 编写商城基础 POJO + LRU/线程池手写 | ✅ 15 文件（含深挖题+代码题） |
| 04 Python 基础 | 语法、异步、项目结构 | 编写 AI 服务脚本 | ✅ 14 文件 |
| 11 Linux | 常用命令、Shell、权限 | 配置开发环境 | ✅ 14 文件（含网络排查） |
| 13 开发工具 | Git 工作流、IDE 调试 | 初始化仓库 | ✅ 11 文件（含 Git 高级+故障排查） |

### Phase 2：后端核心组（5 栈）— 深度关联 ✅

| 技术栈 | 核心目标 | 商城产出 | 状态 |
|--------|----------|----------|------|
| 03 Spring Boot | 自动配置、Starter、Security | 搭建商城后端服务 | ✅ 16 文件 + 项目剖析 5 篇 |
| 06 MySQL | 索引优化、事务、SQL 调优 | 设计商城数据库表 | ✅ 16 文件 + 项目剖析 4 篇 |
| 07 Redis | 缓存策略、分布式锁、持久化 | 商品缓存、购物车 | ✅ 16 文件 + 项目剖析 4 篇 |
| 08 RocketMQ | 消息模型、事务消息、顺序消息 | 订单异步处理 | ✅ 15 文件 + 项目剖析 4 篇 |
| 09 Elasticsearch | 索引、分词、聚合查询 | 商品搜索功能 | 脚手架完成，15 文件 |

> 💡 Phase 2 的实战深度已有 `docs/tech-stack-analysis/mall-micro-cloud/`（13 篇）和 `docs/tech-stack-analysis/mall-exercise/`（7 篇）覆盖，learn 中的脚手架提供理论基础。

### Phase 3：AI 核心组（5 栈）— 深度关联 ✅

| 技术栈 | 核心目标 | 商城产出 | 状态 |
|--------|----------|----------|------|
| 10 Docker | 镜像、容器、Compose | 容器化商城服务 | ✅ 15 文件 + 项目剖析 3 篇 |
| 05 FastAPI | 异步接口、Pydantic 校验 | 搭建 AI 网关服务 | ✅ 16 文件 + 项目剖析 3 篇 |
| 14 LangChain | Chain、Agent、Memory | 智能客服 Agent | ✅ 17 文件 + 项目剖析 4 篇 |
| 15 RAG | 文档加载、向量存储、检索 | 商品知识库问答 | ✅ 17 文件 + 项目剖析 4 篇 |
| 16 OpenAI API | Chat、Embedding、Function Call | LLM 接入 | ✅ 17 文件 + 项目剖析 4 篇 |

> 💡 Phase 3 的实战深度由 `docs/tech-stack-analysis/mall-ai-search/`（11 篇）和 `text2sql/`（7 篇）覆盖，learn 中的每个 README 已添加双向交叉引用。

### Phase 4：基础设施组（2 栈 + 贯穿项目）— 深度关联 ✅

| 技术栈 | 核心目标 | 商城产出 | 状态 |
|--------|----------|----------|------|
| 12 基础设施 | CI/CD、监控、网关 | 全链路部署 | ✅ 17 文件 + 项目剖析 4 篇 |
| 贯穿项目 | 全栈集成、压测、优化 | 完成商城交付 | ✅ 4 个项目剖析完成（38 篇） |

---

## 五、AI 智能商城贯穿体系

### 系统架构

```
┌─────────────────────────────────────────────────────────────────────┐
│                          前端（Vue / React）                         │
└────────────────────────────┬────────────────────────────────────────┘
                             │
┌────────────────────────────▼────────────────────────────────────────┐
│                      API 网关（Spring Cloud Gateway）                │
│                      13-基础设施 / 10-Docker                        │
└──────┬─────────────────────────────────────────┬────────────────────┘
       │                                         │
┌──────▼──────────────────────┐   ┌──────────────▼────────────────────┐
│    后端微服务（Java/Spring Boot） │   │   AI 网关服务（Python/FastAPI）   │
│  03-Spring Boot / 02-Java    │   │  05-FastAPI / 04-Python          │
│                              │   │                                  │
│  ┌────────────────────────┐  │   │  ┌────────────────────────────┐  │
│  │ 用户服务 (auth/user)   │  │   │  │ 智能客服 Agent             │  │
│  │ 订单服务 (order/cart)  │  │   │  │ 14-LangChain               │  │
│  │ 商品服务 (product/cat) │  │   │  │ 15-RAG (商品知识库)        │  │
│  │ 支付服务 (payment)     │  │   │  │ 16-OpenAI API              │  │
│  └────────────────────────┘  │   │  └────────────────────────────┘  │
└──────┬───────────────────────┘   └──────────────┬───────────────────┘
       │                                          │
┌──────▼──────────────────────────────────────────▼───────────────────┐
│                        数据层 / 中间件                              │
│                                                                     │
│  ┌───────────┐  ┌───────────┐  ┌───────────┐  ┌──────────────────┐ │
│  │ 06-MySQL  │  │ 07-Redis  │  │08-RocketMQ│  │09-Elasticsearch  │ │
│  │ 关系数据库 │  │ 缓存/KV  │  │ 消息队列  │  │ 搜索引擎         │ │
│  └───────────┘  └───────────┘  └───────────┘  └──────────────────┘ │
└─────────────────────────────────────────────────────────────────────┘
┌─────────────────────────────────────────────────────────────────────┐
│                   基础设施（Docker / CI/CD / 监控）                  │
│                 10-Docker / 11-Linux / 12-基础设施                  │
└─────────────────────────────────────────────────────────────────────┘
```

### 各技术栈在商城中的角色

| 技术栈 | 商城角色 | 具体功能 |
|--------|----------|----------|
| 01 后端开发基础 | 架构设计 | 商城的分层架构、API 设计规范、RESTful 接口约定 |
| 02 Java 核心 | 开发语言 | 所有后端服务的编程语言，涵盖集合、并发、JVM |
| 03 Spring Boot | 服务框架 | 用户服务、订单服务、商品服务的核心框架 |
| 04 Python 基础 | AI 服务语言 | AI 智能客服、商品推荐、知识库索引的脚本语言 |
| 05 FastAPI | AI 网关 | 连接 Java 后端与 LLM 的 AI 网关服务 |
| 06 MySQL | 核心数据库 | 用户表、商品表、订单表、支付记录等关系数据存储 |
| 07 Redis | 缓存中间件 | 商品缓存、购物车、分布式 Session、接口限流 |
| 08 RocketMQ | 消息队列 | 下单后异步扣库存、支付回调通知、订单超时取消 |
| 09 Elasticsearch | 搜索引擎 | 商品全文搜索、用户行为日志分析、搜索建议 |
| 10 Docker | 容器化 | 商城各服务的容器化编排、一键部署 |
| 11 Linux | 运行环境 | 服务器部署、Shell 运维脚本、日志分析 |
| 12 基础设施 | 运维体系 | CI/CD 流水线、Prometheus 监控、ELK 日志 |
| 13 开发工具 | 效率工具 | Git 协作、IDE 调试、API 测试（Postman） |
| 14 LangChain | AI 框架 | 智能客服对话链、商品推荐 Agent、多轮对话管理 |
| 15 RAG | 知识库 | 商品说明书、FAQ 文档的向量化存储与检索增强生成 |
| 16 OpenAI API | LLM 接入 | GPT 对话、Embedding 向量化、Function Call 工具调用 |

### 如何用商城贯穿学习

每个技术栈的学习路径都遵循「从商城中来，到商城中去的」模式：

1. **问题引入**：从一个商城业务痛点出发（如「商品搜索太慢怎么办？」）
2. **技术学习**：学习该技术栈的核心概念和 API（如 ES 的倒排索引、分词器）
3. **商城落地**：在商城项目中实现该功能（如实现商品搜索 REST API）
4. **对比优化**：对比实现前后的差异，量化效果（如搜索耗时从 2s 降至 50ms）
5. **面试总结**：用 STAR 法则组织成面试答案

---

## 六、学习方法论

### STAR 法则

STAR 是面试回答的黄金框架，我们在每个技术栈的面试模块中都采用此结构：

| 要素 | 含义 | 商城示例 |
|------|------|----------|
| **S**ituation | 业务背景与挑战 | 商城大促期间，商品详情页 QPS 飙升，数据库压力过大 |
| **T**ask | 需要解决的问题 | 设计缓存方案，降低数据库查询压力，保证数据一致性 |
| **A**ction | 具体行动与方案 | 采用 Redis 缓存热点商品，Cache-Aside 模式，设置过期时间+MQ 异步更新 |
| **R**esult | 量化结果与收益 | 数据库查询量下降 90%，接口响应时间从 200ms 降至 5ms |

### 项目驱动 vs 纯理论

| 对比维度 | 项目驱动（本教程） | 纯理论学习 |
|----------|-------------------|-----------|
| 学习动机 | 高 — 每个技术栈都有明确目标 | 低 — 不知道学了有什么用 |
| 知识留存率 | 75%（学完即用） | 10%（看完就忘） |
| 面试展示 | 有完整项目可讲 | 只能背八股文 |
| 动手能力 | 强 — 亲手实现 | 弱 — 只会做题 |
| 学习曲线 | 先陡后缓 | 看似平缓，实则停滞 |

### 双轨制说明

每个技术栈目录下都包含两套内容：

```
03-spring-boot/
├── 01-basics/          ← 基础概念（👶 入门必读）
├── 02-core/            ← 核心原理（👶 必读）
├── 03-advanced/        ← 进阶源码（🚀 进阶选读）
├── 04-projects/        ← 商城实战（👶 必做）
│   └── mini-blog/      ← 过渡项目
├── 05-interview/       ← 面试专项（🎯 面试必读）
└── resources/          ← 参考资料
```

- **👶 读者**：完成 01-basics → 02-core → 04-projects，跳过 03-advanced
- **🎯 读者**：直接进入 05-interview，按需回溯 01-basics 和 02-core
- **🚀 读者**：全部完成，重点攻克 03-advanced

---

## 七、跨技术栈关联索引

每个技术栈都不是孤立的，理解它们之间的交互关系是掌握系统设计的关键。

| 技术栈 | 强关联栈 | 关联说明 |
|--------|----------|----------|
| 01 后端开发基础 | 03 Spring Boot, 13 开发工具, 11 Linux | 架构设计需落地为 Spring Boot 项目，IDE 和 Git 是基本工具 |
| 02 Java 核心 | 03 Spring Boot, 06 MySQL, 07 Redis | Java 是 Spring Boot 的母语，JDBC 连接 MySQL，Jedis/Redisson 操作 Redis |
| 03 Spring Boot | 02 Java, 06 MySQL, 07 Redis, 08 RocketMQ, 09 ES | Spring Boot 整合了所有中间件的 Starter |
| 04 Python 基础 | 05 FastAPI, 16 OpenAI, 14 LangChain | Python 是 AI 生态的基础语言 |
| 05 FastAPI | 04 Python, 14 LangChain, 10 Docker | FastAPI 是 AI 服务的 HTTP 载体 |
| 06 MySQL | 03 Spring Boot, 07 Redis, 09 ES | MySQL 与 Redis 组成冷热数据架构，与 ES 通过 CDC 同步 |
| 07 Redis | 03 Spring Boot, 06 MySQL, 08 RocketMQ | Redis 缓存 + MySQL 持久化 + MQ 最终一致性 |
| 08 RocketMQ | 03 Spring Boot, 06 MySQL, 10 Docker | MQ 解耦微服务，Docker 部署集群 |
| 09 Elasticsearch | 06 MySQL, 10 Docker, 12 基础设施 | ES 与 MySQL 数据同步，Docker 部署，ELK 监控 |
| 10 Docker | 11 Linux, 12 基础设施, 03 Spring Boot | Docker 是部署的基础，Compose 编排多服务 |
| 11 Linux | 10 Docker, 12 基础设施, 13 开发工具 | Linux 是所有运行环境的底座 |
| 12 基础设施 | 10 Docker, 11 Linux, 09 ES | CI/CD + 监控 + 日志构成运维铁三角 |
| 13 开发工具 | 所有技术栈 | Git 管理代码，IDE 编写代码，Maven/Gradle 构建 |
| 14 LangChain | 05 FastAPI, 15 RAG, 16 OpenAI | LangChain 编排 AI 调用链，RAG 提供知识，OpenAI 提供推理 |
| 15 RAG | 14 LangChain, 09 ES, 16 OpenAI | 向量存储可用 ES 或专用向量库，检索结果由 LLM 生成回答 |
| 16 OpenAI API | 04 Python, 14 LangChain, 15 RAG | OpenAI 提供底层模型能力 |

### 常见交互场景

**场景 1：用户下单流程**
```
前端 → Spring Boot(订单服务) → RocketMQ(异步扣库存) → MySQL(持久化)
                                      ↓
                                Redis(缓存更新) → 用户收到实时通知
```

**场景 2：AI 智能客服**
```
用户提问 → FastAPI(AI网关) → LangChain(意图识别)
                                  ↓
                     ┌────────────┼────────────┐
                     ↓            ↓            ↓
               RAG(商品文档)  OpenAI(推理)  MySQL(用户历史)
                     ↓            ↓            ↓
                     └────────────┼────────────┘
                                  ↓
                          生成回答 → 返回用户
```

**场景 3：商品搜索**
```
用户搜索 → Spring Boot → ES(全文搜索) → 返回商品ID列表
                                  ↓
                            Redis(缓存热词) → 更新热搜榜
                                  ↓
                            MySQL(商品详情) → 组合返回
```

---

## 八、学习建议与 FAQ

### 学习节奏建议

- **每天 2 小时**：适合在职读者，8 周完成全部 16 栈
- **每天 4 小时**：适合脱产/学生，4 周可以完成
- **周末集中式**：每周六日各 6 小时，10 周完成

### 环境要求

- **硬件**：8GB 以上内存，建议 16GB（运行 Docker + 本地中间件）
- **操作系统**：Windows / macOS / Linux 均可，推荐 macOS 或 Linux
- **开发工具**：IntelliJ IDEA（推荐 Ultimate）、VSCode、Docker Desktop
- **账号准备**：GitHub 账号、OpenAI API Key

### 常见问题

**Q：没有编程基础可以直接学吗？**
A：可以。走「零基础入门路线」，从 01 后端开发基础和 02 Java 核心开始。但建议先花一周时间学习计算机基础知识（二进制、数据结构、网络基础）。

**Q：Java 和 Python 都要学吗？会不会太杂？**
A：这正是本教程的特色 — 传统后端用 Java（Spring Boot）实现，AI 能力用 Python（FastAPI）实现，通过 API 网关打通。这种「Java 后端 + Python AI」的架构是目前大厂的主流方案。

**Q：学完能找到什么级别的工作？**
A：完成全部 16 栈 + AI 商城项目后，可以胜任：
- Java 后端开发（1-3 年经验）
- AI 应用开发工程师（AI Agent 方向）
- 全栈工程师（偏后端）

**Q：每个技术栈学到什么程度算「学完」？**
A：完成该栈的「基础 + 核心 + 项目实战」三个模块，并在商城项目中找到对应落地场景。不以看完文档为标准，以「能在面试中结合 STAR 法则讲清楚」为标准。

---

## 九、目录结构速查

```
learn/
├── 00-ROADMAP/              ← 本文件 — 学习路线总纲
├── 01-backend-development/   ← 后端开发基础
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

---

> **最后的话**：技术栈数量不是重点，重点是你能用它们**做出什么**。16 个技术栈学完，你将拥有一个完整的 AI 智能商城项目，一段可以写在简历上的实战经历，以及一套用 STAR 法则应对面试的思维框架。过程中遇到任何问题，请回到这里，对照路线图检查自己的位置，调整节奏继续前进。
>
> 学习的敌人是完美主义，请记住：先完成，再完美。

---

## 十、本仓库文档体系与 `docs/tech-stack-analysis` 的关联

> 本仓库有两套互补的文档体系：**`learn/`**（16 技术栈双轨教程）+ **`docs/tech-stack-analysis/`**（4 个项目实战剖析）。前者是学习路径，后者是面试落地。

### 两套文档的定位

| | `learn/`（本目录） | `docs/tech-stack-analysis/` |
|--|-------------------|---------------------------|
| **定位** | 技术栈学习路径，按难度递进 | 项目实战剖析，按请求链路串联 |
| **视角** | "这个技术栈是什么" | "这个技术栈在我的项目里怎么用" |
| **结构** | 双轨制（入门 + 面试） | 单轨（面试 + 代码 + Java 对照） |
| **配套** | Mini-Project | 完整项目源码 |
| **面试价值** | 背景知识 + 面试题库 | 项目故事 + STAR 答案 |

### 项目剖析系列索引（`docs/tech-stack-analysis/`）

| 项目 | 语言 | 篇数 | 核心方向 | 路径 |
|------|------|------|---------|------|
| **mall-exercise** | Java | 7 篇 | AOP/反射/集合/Redis/MP | [→ 查看](../docs/tech-stack-analysis/mall-exercise/01-AOP-PRACTICE.md) |
| **mall-micro-cloud** | Java | 13 篇 | 微服务/分布式/高并发 | [→ 查看](../docs/tech-stack-analysis/mall-micro-cloud/00-OVERVIEW.md) |
| **mall-ai-search** | Python | 11 篇 | AI 搜索/Agent/向量检索 | [→ 查看](../docs/tech-stack-analysis/mall-ai-search/00-OVERVIEW.md) |
| **text2sql** | Java | 7 篇 | Java AI/RAG/SQL 验证 | [→ 查看](../docs/tech-stack-analysis/text2sql/00-OVERVIEW.md) |

### 各技术栈的双体系对照

当你在 `learn/02-java/` 学习 Java 核心时，可以同时在 `docs/tech-stack-analysis/mall-exercise/03-REFLECTION.md` 看到 Java 反射在真实项目中的落地：

| learn 中的栈 | 对应的项目剖析文档 | 双体系学习路径 |
|------------|------------------|---------------|
| `01-backend-development` | `mall-micro-cloud/02-COMMON-ARCH.md` | 架构设计理论 → 项目中的统一响应/异常处理 |
| `02-java` | `mall-exercise/01~03` | 集合/反射理论 → AOP/反射/集合实战代码 |
| `03-spring-boot` | `mall-micro-cloud/02-COMMON-ARCH.md` | Spring Boot 理论 → 项目中的 Controller/Service 实践 |
| `04-python` | `mall-ai-search/00-OVERVIEW.md` | Python 基础 → 项目中的完整 Python 代码 |
| `05-fastapi` | `mall-ai-search/02-API-GATEWAY.md` | FastAPI 理论 → 项目中的路由/异常处理/Pydantic |
| `06-mysql` | `mall-micro-cloud/03-PRODUCT-MYBATISPLUS.md` | MySQL 索引理论 → 项目中的多表设计/MP 查询 |
| `07-redis` | `mall-exercise/04-REDIS-CACHE.md` | Redis 结构理论 → Cache-Aside/分布式锁实战 |
| `08-rocketmq` | `mall-micro-cloud/09-ROCKETMQ.md` | MQ 模型理论 → 支付回调/库存同步/最终一致性 |
| `09-elasticsearch` | `mall-micro-cloud/08-ES-SEARCH.md` | ES 索引理论 → 商品搜索/分页/数据分析 |
| `14-langchain` | `mall-ai-search/07-LANGCHAIN-AGENT.md` | LCEL/Agent 理论 → create_agent/Tool/结构化输出 |
| `15-rag` | `text2sql/03-RAG-RETRIEVAL.md` | RAG 架构理论 → 混合检索/融合排序实战 |
| `16-openai` | `mall-ai-search/04-LLM-PROVIDER.md` | OpenAI API 理论 → 多供应商兼容协议 |

> 💡 **面试话术**："我有一套完整的技术栈学习体系（`learn/`，16 个技术栈双轨教程），以及 4 个真实项目的深度剖析文档（`docs/tech-stack-analysis/`，38 篇，覆盖 AOP/反射/微服务/分布式/AI/RAG/Agent/向量检索）。前者是知识输入，后者是项目输出——面试时，我可以用 4 个项目讲清 40+ 个技术栈的实际应用。"

---

## 📖 导航

| ← 上一篇 | 📚 目录 | 下一篇 → |
|----------|---------|----------|
| [🏠 返回项目根目录](../../README.md) | [📚 总目录](../README.md) | [后端开发总纲 →](../01-backend-development/README.md) |