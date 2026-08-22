# 全景导读：text2sql —— Java 实现 Text2SQL + RAG 的完整实战

> 用户输入"查询本月销售额前10的商品"，系统自动生成 SQL、执行、返回结果。看纯 Java 项目如何用 Spring AI + DeepSeek + RAG 实现自然语言到 SQL 的转换。
>
> **适用读者：** Java 后端工程师转型 AI 应用开发
> **对照体系：** 与 mall-ai-search 的 Python AI 栈对比，理解 Java AI 生态的现状与能力
> **项目源码：** `mall-ai/text2sql`

---

## 一、项目定位

### 1.1 什么是 Text2SQL

Text2SQL = 自然语言 → SQL 语句。用户用中文描述查询需求，系统自动生成可执行的 SQL：

```
用户输入： "查询本月销售额前10的商品"
系统输出： SELECT p.name, SUM(oi.quantity * oi.price) AS revenue
           FROM order_items oi JOIN products p ON oi.product_id = p.id
           JOIN orders o ON oi.order_id = o.id
           WHERE o.created_at >= DATE_TRUNC('month', CURRENT_DATE)
           GROUP BY p.id, p.name
           ORDER BY revenue DESC LIMIT 10
```

### 1.2 三个项目的技术栈对比

| 对比维度 | mall-ai-search (Python) | mall-micro-cloud (Java) | text2sql (Java AI) |
|---------|----------------------|----------------------|-------------------|
| **语言** | Python | Java | **Java** |
| **框架** | FastAPI + LangChain | Spring Cloud Alibaba | **Spring Boot + Spring AI** |
| **LLM 集成** | LangChain ChatOpenAI | 桥接 Python 服务 | **Spring AI ChatClient** |
| **Embedding** | langchain-openai | — | **Spring AI EmbeddingModel** |
| **向量库** | RedisVL (Redis Stack) | — | **Spring AI VectorStore** |
| **RAG** | Agent + Tool 调用 | — | **RAGRetrievalService** |
| **搜索** | 语义向量搜索 | ES 关键词 | **Schema 语义检索** |
| **AI 能力** | 商品推荐/条件提取 | — | **Text2SQL 生成** |
| **面试价值** | AI 搜索 + Agent | 微服务 + 分布式 | **Java + AI 融合实战** |

**核心价值：** text2sql 是**纯 Java 实现 AI 能力**的最佳范例——证明 Java 生态也能做 AI 工程，而不只是"调 Python 服务"。

---

## 二、架构全景

### 2.1 模块架构

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                          text2sql-web (Web 层)                              │
│  Text2SQLController  │  ConversationController  │  GlobalExceptionHandler   │
│  Text2SQLResponse    │  ConversationSession     │  SwaggerConfig            │
│  IntelligentResponse │  ErrorFixService         │  IntentAnalysisService    │
└─────────────────────────┬───────────────────────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                          text2sql-core (核心业务层)                          │
│  Text2SQLService        │  NaturalLanguageQuery  │  GeneratedSQL            │
│  CacheManager           │  MSchema / MSchemaColumn │  ValidationResult      │
│  BusinessMetadata       │  DataSample             │  TableRelationship      │
└─────────────────────────┬───────────────────────────────────────────────────┘
                          │
          ┌───────────────┼───────────────┐
          │               │               │
          ▼               ▼               ▼
┌─────────────────┐ ┌─────────────┐ ┌─────────────────────┐
│ text2sql-ai     │ │text2sql-    │ │ text2sql-validator   │
│ AI 核心模块     │ │schema       │ │ SQL 验证器           │
│                 │ │ Schema 模块 │ │                     │
│ LLMClient       │ │             │ │ SyntaxValidator     │
│ EmbeddingService│ │ Table-      │ │ SecurityValidator   │
│ VectorStore     │ │ Retrieval   │ │ SemanticValidator   │
│ RAGRetrieval    │ │ Schema-     │ │ PerformanceEstimator│
│ PromptBuilder   │ │ Enhancer    │ │ SQLCorrectionService│
│ SQLExampleLib   │ │ Metadata-   │ │                     │
│ SQLGenerator    │ │ Service     │ │                     │
└─────────────────┘ └─────────────┘ └─────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                          text2sql-common (公共模块)                          │
│  ApiResponse<T>  │  Text2SQLException  │  枚举 (QueryIntent, QueryComplexity)│
└─────────────────────────────────────────────────────────────────────────────┘
```

### 2.2 一次 Text2SQL 请求的完整链路

```
用户输入: "查询本月销售额前10的商品"
    │
    ▼
┌─ 1. Controller ──────────────────────────────────────────────────────────┐
│  POST /text2sql/convert  →  Text2SQLController.convertToSQL()           │
└──────────────────────────────────────────────────────────────────────────┘
    │
    ▼
┌─ 2. 意图识别 ───────────────────────────────────────────────────────────┐
│  IntentAnalysisService : 判断查询意图 (SELECT / UPDATE / DELETE / ...)  │
│  → 只允许 SELECT 操作，拒绝非查询请求                                    │
└──────────────────────────────────────────────────────────────────────────┘
    │
    ▼
┌─ 3. RAG 检索 ───────────────────────────────────────────────────────────┐
│  RAGRetrievalService.retrieveRelevantSchemas(query, topK)               │
│  ├── 向量检索: VectorStoreService.searchSimilarSchemas(query, topK)    │
│  │     → Schema 文本 → Embedding → 向量库 → 语义相似表                  │
│  └── 关键词检索: TableRetrievalService.retrieveRelevantTables(query)   │
│        → 表名/注释关键词匹配 → 精确匹配                                 │
│  → 融合排序 → 返回 TOP-K 相关 Schema                                   │
└──────────────────────────────────────────────────────────────────────────┘
    │
    ▼
┌─ 4. Schema 增强 ────────────────────────────────────────────────────────┐
│  SchemaEnhancer : 从检索到的表中提取字段名、类型、注释、主键、外键       │
│  → 生成 M-Schema (结构化 Schema 描述)                                    │
└──────────────────────────────────────────────────────────────────────────┘
    │
    ▼
┌─ 5. Few-shot 示例检索 ──────────────────────────────────────────────────┐
│  SQLExampleLibrary : 从历史相似的 NL→SQL 对中检索示例                   │
│  → 提供 2-3 个 Few-shot 示例，帮助 LLM 理解输出格式                      │
└──────────────────────────────────────────────────────────────────────────┘
    │
    ▼
┌─ 6. SQL 生成 (LLM) ────────────────────────────────────────────────────┐
│  PromptBuilder.buildSQLGenerationPrompt(query, schemas, examples)       │
│  → LLMClient.generate(systemPrompt, userPrompt)                        │
│  → 解析 LLM 返回内容，提取纯 SQL                                        │
│  → 返回 GeneratedSQL (含置信度、耗时)                                   │
└──────────────────────────────────────────────────────────────────────────┘
    │
    ▼
┌─ 7. SQL 验证 ───────────────────────────────────────────────────────────┐
│  SQLValidatorService.validate(sql)                                      │
│  ├── SyntaxValidator:    语法解析 → AST 树 → 检查语法错误                │
│  ├── SecurityValidator:  检查 DROP/TRUNCATE/DELETE 等危险操作            │
│  ├── SemanticValidator:  检查表名、字段名是否存在                        │
│  └── PerformanceEstimator: 预估执行成本，检查全表扫描等风险               │
│  → 通过 → 执行；失败 → SQLCorrectionService 自动修正或返回错误           │
└──────────────────────────────────────────────────────────────────────────┘
    │
    ▼
┌─ 8. SQL 执行 + 响应 ───────────────────────────────────────────────────┐
│  SQLExecutionService.execute(sql) → 查询结果                             │
│  → 返回 Text2SQLResponse { sql, data, columns, executionTime, ... }     │
└──────────────────────────────────────────────────────────────────────────┘
    │
    ▼
┌─ 9. 对话记录 (可选) ────────────────────────────────────────────────────┐
│  ConversationService : 保存 NL→SQL→结果 三元组                           │
│  → 下次查询时，相同意图可复用 / 连续对话可参考上下文                     │
└──────────────────────────────────────────────────────────────────────────┘
```

---

## 三、文档体系

| 编号 | 文档 | 核心模块 | 核心考点 | 面试权重 |
|------|------|---------|---------|---------|
| 00 | 本篇·全景导读 | 架构总览 | Java AI 工程化 | ★★★★★ |
| 01 | [Spring AI + DeepSeek LLM 集成](./01-LLM-CLIENT.md) | text2sql-ai | LLM 客户端抽象、多供应商 | ★★★★ |
| 02 | [Embedding 与向量存储](./02-EMBEDDING-VECTOR.md) | text2sql-ai | Spring AI EmbeddingModel | ★★★★ |
| 03 | [RAG 检索增强：向量+关键词混合检索](./03-RAG-RETRIEVAL.md) | text2sql-ai | 混合检索、融合排序 | ★★★★★ |
| 04 | [Prompt 工程与 Schema 增强](./04-PROMPT-SCHEMA.md) | text2sql-ai + schema | M-Schema、Few-shot | ★★★★ |
| 05 | [SQL 验证器四层防护](./05-SQL-VALIDATOR.md) | text2sql-validator | 语法/安全/语义/性能 | ★★★★★ |
| 06 | [对话管理与上下文压缩](./06-CONVERSATION.md) | text2sql-web | 多轮对话、历史管理 | ★★★★ |
| 07 | [架构复盘与面试题集](./07-ARCHITECTURE.md) | 全栈 | 三个项目横向对比 | ★★★★★ |

---

## 四、与前面两个项目的关联

### 快速定位指南

```
你是 Java 后端，想了解：
├── 微服务架构 + 分布式事务 → mall-micro-cloud 系列
├── AI 搜索 + LangChain Agent → mall-ai-search 系列
└── Java 中怎么做 AI → text2sql 系列 (当前)
```

### text2sql 在 Java AI 生态中的独特价值

1. **纯 Java 实现**——没有调 Python 服务，全部用 Spring AI 完成
2. **Spring AI 全栈**——ChatClient + EmbeddingModel + VectorStore 三件套
3. **RAG 架构完整实现**——不是简单的"调 LLM"，而是检索→增强→生成→验证的完整链路
4. **SQL 验证器的四层防护**——这是 AI 生成内容的安全护栏，生产场景必备

---

> **下一篇：** [01-LLM-CLIENT.md —— Spring AI + DeepSeek LLM 集成：客户端抽象、多供应商切换](./01-LLM-CLIENT.md)
>
> 看纯 Java 项目如何通过 Spring AI 的 ChatClient 统一接入 DeepSeek 大模型，以及多供应商抽象设计。