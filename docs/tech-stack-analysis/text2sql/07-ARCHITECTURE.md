# 07 · 架构复盘与面试题集：三个项目横向对比

> 复盘 text2sql 的完整架构，与 mall-ai-search、mall-micro-cloud 做横向对比，提炼 Java + AI 融合面试的核心要点。这是本系列的收官之作，也是你"Java AI 项目"面试的弹药库。
>
> **系列定位：** 三个项目形成"Java 微服务 + Python AI + Java AI"的完整面试覆盖。

---

## 一、项目架构复盘

### 1.1 text2sql 核心流程

```
用户输入自然语言 → 意图识别 → RAG 检索 (向量+关键词) → Schema 增强
    → Few-shot 示例检索 → Prompt 构建 → LLM 生成 SQL
    → 四层验证 (语法/安全/语义/性能) → 执行 → 返回结果
```

### 1.2 模块职责

| 模块 | 职责 | 核心类 |
|------|------|--------|
| text2sql-ai | AI 核心：LLM 调用、Embedding、向量存储、RAG 检索、Prompt 构建 | LLMClient, EmbeddingService, VectorStoreService, RAGRetrievalService, PromptBuilder |
| text2sql-core | 领域模型：查询、Schema、SQL 生成结果、验证结果 | Text2SQLService, NaturalLanguageQuery, MSchema, GeneratedSQL |
| text2sql-schema | Schema 管理：元数据提取、表检索、Schema 增强 | DatabaseMetadataService, TableRetrievalService, SchemaEnhancer |
| text2sql-validator | SQL 验证：语法分析、安全检查、语义校验、性能预估 | SQLValidatorService, SyntaxValidator, SecurityValidator |
| text2sql-web | Web 层：Controller、对话管理、错误处理 | Text2SQLController, ConversationService, ErrorFixService |
| text2sql-common | 公共模块：响应体、异常、枚举 | ApiResponse, Text2SQLException |

---

## 二、三个项目横向对比

### 2.1 技术栈全景

| 维度 | mall-ai-search | mall-micro-cloud | text2sql |
|------|---------------|-----------------|---------|
| **语言** | Python | Java | **Java** |
| **框架** | FastAPI + LangChain | Spring Cloud Alibaba | **Spring Boot + Spring AI** |
| **LLM** | ChatOpenAI(阿里云/Agnes) | — | **ChatClient(DeepSeek)** |
| **Embedding** | OpenAIEmbeddings(BGE-M3) | — | **EmbeddingModel** |
| **向量库** | RedisVL (Redis Stack) | — | **VectorStore (JDBC/PGVector)** |
| **RAG** | Agent + Tool 调用 | — | **RAGRetrievalService** |
| **Agent** | LangChain create_agent | — | — |
| **对话记忆** | LangGraph Checkpointer | — | ConversationSession |
| **搜索** | 语义向量搜索 | ES 关键词搜索 | Schema 语义检索 |
| **微服务** | 单体 | 12 个微服务 | 6 个模块 |
| **分布式事务** | — | Seata AT | — |
| **高并发** | — | Redisson + 布隆过滤 | — |
| **消息队列** | — | RocketMQ | — |

### 2.2 面试价值矩阵

| 面试考点 | mall-ai-search | mall-micro-cloud | text2sql |
|---------|---------------|-----------------|---------|
| **微服务架构** | ⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐⭐ |
| **分布式事务** | — | ⭐⭐⭐⭐⭐ | — |
| **高并发/秒杀** | — | ⭐⭐⭐⭐⭐ | — |
| **消息队列** | — | ⭐⭐⭐⭐⭐ | — |
| **LLM 集成** | ⭐⭐⭐⭐⭐ | — | ⭐⭐⭐⭐⭐ |
| **RAG 实现** | ⭐⭐⭐⭐⭐ | — | ⭐⭐⭐⭐⭐ |
| **Prompt 工程** | ⭐⭐⭐⭐ | — | ⭐⭐⭐⭐⭐ |
| **向量检索** | ⭐⭐⭐⭐⭐ | — | ⭐⭐⭐⭐ |
| **Agent 机制** | ⭐⭐⭐⭐⭐ | — | — |
| **Java AI 融合** | ⭐ (Python) | ⭐ (桥接) | ⭐⭐⭐⭐⭐ |
| **SQL 安全** | — | — | ⭐⭐⭐⭐⭐ |

---

## 三、面试题集

### 3.1 Java AI 融合类

**Q1: 你在 Java 项目中是怎么集成大模型的？**

> **回答思路：** 通过 Spring AI 框架。Spring AI 提供了 ChatClient、EmbeddingModel、VectorStore 等抽象，类似 Spring Data 对数据库的抽象。具体到 text2sql 项目，我们用 ChatClient 调用 DeepSeek 生成 SQL，用 EmbeddingModel 做 Schema 向量化，用 VectorStore 做语义检索。

**Q2: Spring AI 和 LangChain 有什么区别？**

> **回答思路：** 两者定位相同——都是 AI 框架。Spring AI 是 Spring 生态原生，与 Spring Boot 的配置体系、依赖注入、事务管理无缝集成。LangChain 生态更成熟、Agent 能力更强、社区更大。Spring AI 适合 Java 团队，LangChain 适合 Python 团队。项目中用 Spring AI 完全实现了 RAG 架构，证明 Java 生态也能做 AI 工程。

**Q3: 你们的 Text2SQL 是怎么保证 SQL 安全的？**

> **回答思路：** 四层验证：1) 语法解析器检查 SQL 语法；2) 安全检查拦截 DROP/TRUNCATE/DELETE 等危险操作，只允许 SELECT；3) 语义检查验证表名和字段名是否存在；4) 性能预估防止全表扫描。如果验证失败，自动修正最多 3 次。

### 3.2 RAG 与检索类

**Q4: 你们项目中 RAG 是怎么实现的？**

> **回答思路：** 混合检索：向量检索（语义相似）+ 关键词检索（精确匹配）。向量检索将表名和字段注释转为 Embedding 存储到 VectorStore，查询时用语义匹配找到最相关的表。关键词检索通过表名和注释的精确匹配补充召回。两者融合排序后，将 Schema 信息注入 LLM 的上下文，提高生成 SQL 的准确率。

**Q5: 向量检索和关键词检索的结果怎么融合？**

> **回答思路：** 向量结果优先排序（语义匹配更准确），关键词结果去重后补充在后面（提高召回率）。更复杂的方案可以用 RRF (Reciprocal Rank Fusion) 加权融合。

### 3.3 Prompt 工程类

**Q6: Text2SQL 的 Prompt 和通用 Chat Prompt 有什么不同？**

> **回答思路：** 三个关键差异：1) **Schema 精确性**——字段名、类型、关系必须准确，否则 SQL 执行报错；2) **输出格式约束**——只输出 SQL 不加解释，否则解析失败；3) **Few-shot 示例**——给 LLM 2-3 个 NL→SQL 对，让它理解输出格式和数据库方言。项目中用 PromptBuilder 构建结构化的 Prompt，包含系统角色、Schema 信息、示例、用户查询、输出约束五个部分。

### 3.4 架构设计类

**Q7: 你在三个项目中分别负责什么？怎么串联起来讲？**

> **回答思路：** 三个项目是同一套电商系统的不同模块：**mall-micro-cloud** 是 Java 微服务底座，负责商品、订单、用户、秒杀等核心业务；**mall-ai-search** 是 Python AI 搜索服务，通过 LangChain Agent 做语义推荐；**text2sql** 是 Java AI 工具，让用户用自然语言查询数据库。三者通过 Gateway 桥接，Java 微服务 + Python AI + Java AI 形成完整的技术栈矩阵。

---

## 四、面试能力雷达图

```
                     面试评级
                 ┌──────────────────────────┐
                 │      Java AI 工程化       │
                 │        ★★★★★            │
                 ├──────────────────────────┤
                 │  RAG 实现  ｜  LLM 集成   │
                 │  ★★★★★    ｜  ★★★★★    │
                 ├──────────────────────────┤
                 │ Prompt工程 ｜  SQL 安全   │
                 │  ★★★★★    ｜  ★★★★★    │
                 ├──────────────────────────┤
                 │  向量检索   ｜  对话管理   │
                 │  ★★★★☆    ｜  ★★★★☆    │
                 ├──────────────────────────┤
                 │ 微服务架构  ｜  高并发    │
                 │  ★★★☆☆    ｜  ★★★☆☆    │
                 └──────────────────────────┘
```

---

## 五、全套面试话术

### 10 秒版本

> "我做过一个 Java 实现的 Text2SQL 项目，用户输入自然语言就能自动生成 SQL 并执行。核心用 Spring AI 集成 DeepSeek 大模型，通过 RAG 检索数据库 Schema 来提高生成准确率。"

### 60 秒版本

> "项目用 Spring AI 的 ChatClient 集成 DeepSeek 模型实现 SQL 生成。核心是 RAG 架构——先把数据库表结构向量化存储到 VectorStore，用户查询时混合检索（向量+关键词）找到相关表，再通过 PromptBuilder 构建包含 Schema 信息和 Few-shot 示例的 Prompt 发给 LLM。生成的 SQL 经过四层验证（语法/安全/语义/性能）后才执行。对话历史通过会话管理保存，支持多轮交互。这个项目证明 Java 生态完全能做 AI 工程。"

### 5 分钟版本

按本系列 7 篇文档的链路逐一展开：LLM 集成 → Schema 向量化 → RAG 检索 → Prompt 构建 → SQL 生成 → 四层验证 → 对话管理。最后对比三个项目的技术栈选择。

---

## 六、三个项目体系总结

```
┌─────────────────────────────────────────────────────────────────────┐
│                 面试项目阐述体系（三件套）                            │
│                                                                     │
│  mall-micro-cloud          mall-ai-search          text2sql         │
│  (Java 微服务电商)          (Python AI 搜索)        (Java AI 工具)   │
│                                                                     │
│  微服务/分布式/高并发       AI 搜索/Agent/RAG      Java+AI 融合     │
│  Nacos/Gateway/Seata/      LangChain/RedisVL/     Spring AI/        │
│  Redisson/RocketMQ/JWT     FastAPI/BGE-M3        DeepSeek/Vector     │
│                                                                     │
│  面试官问 Java 方向       面试官问 AI 方向        面试官问 Java+AI  │
│  → 讲这个                 → 讲这个               → 讲这个          │
│                                                                     │
│  三个项目来自同一套电商系统，可以串联成"一个完整的故事"              │
└─────────────────────────────────────────────────────────────────────┘
```

> **系列完成！** 三个项目、22 篇技术栈分析文档，覆盖 Java 微服务 + Python AI + Java AI 三大方向。
>
> 祝你面试顺利！