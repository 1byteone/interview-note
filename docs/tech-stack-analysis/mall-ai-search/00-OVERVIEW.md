# 全景导读：mall-ai-search 技术栈深度剖析

> 从一次 AI 搜索请求出发，穿透 10 个技术栈，理解电商智能搜索的完整链路。
>
> **适用读者：** Java 后端工程师转型 AI 应用开发
> **对照体系：** Spring Boot / Spring AI 生态
> **项目源码：** `mall-ai/mall-ai-search`

---

## 一、架构全景

```
┌─────────────────────────────────────────────────────────────────────────┐
│                          前端层 (Vue3 + Axios)                          │
│   [传统搜索]  radio  [AI自然语言搜索]                                    │
│   Promise.allSettled([/recommend, /extract]) 并行调用，容错设计          │
└────────────────────────────────┬────────────────────────────────────────┘
                                 │ HTTP GET
                                 ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                     API 网关层 (FastAPI + Uvicorn)                       │
│   Pydantic Result<T> 统一响应体  │  全局异常处理  │  APIRouter 模块化    │
│   路由: /api/v1/{recommend, extract, sync, test}                        │
└────────────────┬───────────────┬────────────────────────────────────────┘
                 │               │
        /extract │               │ /recommend
                 ▼               ▼
┌───────────────────┐   ┌───────────────────────────────────────────────┐
│  条件提取 Chain    │   │        LangChain Agent (create_agent)         │
│  LLM + Pydantic   │   │                                                │
│  OutputParser     │   │  ┌───────────────────────────────────────────┐ │
│  → SearchCondition│   │  │  LLM Tool: vector_search_tool             │ │
│  {keyword, price} │   │  │  → RedisVectorStore.similarity_search()   │ │
└───────────────────┘   │  │    → OpenAIEmbeddings(BGE-M3)             │ │
                        │  │      → Redis Stack (HNSW 向量索引)         │ │
                        │  └───────────────────────────────────────────┘ │
                        │                                                │
                        │  ┌───────────────────────────────────────────┐ │
                        │  │  LangGraph InMemorySaver (thread_id 记忆)  │ │
                        │  │  → ProductRecommendResponse 结构化输出     │ │
                        │  └───────────────────────────────────────────┘ │
                        └───────────────────────────────────────────────┘
                                           │
                                           ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                     数据同步层 (离线/触发)                               │
│  MySQL(SKU) → SQLAlchemy → RecursiveCharacterTextSplitter              │
│  → BGE-M3 Embedding → RedisVL add_documents(batch=100)                 │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## 二、一次 AI 搜索请求的完整生命周期

以用户输入 **"5000元以下续航强的华为手机"** 为例，走通全链路：

### Step 1: 前端调用 (01-FRONTEND.md)

用户选择 `AI自然语言搜索` 模式，输入查询，点击搜索。

```javascript
// Promise.allSettled 并行调用两个接口，任一失败不阻塞另一方
const [resRecommend, resExtract] = await Promise.allSettled([
  request.get("/v1/search/recommend", { params: { query, threadId } }),
  request.get("/v1/search/extract", { params: { query } })
]);
```

### Step 2: 网关分发 (02-API-GATEWAY.md)

FastAPI 将请求路由到 `/api/v1/extract` 和 `/api/v1/recommend`。

### Step 3: 条件提取 (/extract) (04-LLM-PROVIDER.md + 07-LANGCHAIN-AGENT.md)

LLM 将自然语言解析为结构化查询条件：

```json
{"keyword": "华为手机", "min_price": 0, "max_price": 5000}
```

### Step 4: 智能推荐 (/recommend) (07-LANGCHAIN-AGENT.md)

Agent 调用 `vector_search_tool`，从 Redis 向量库语义召回 TOP-10 商品。

### Step 5: 向量检索 (05-EMBEDDING.md + 06-VECTOR-STORE.md)

用户查询文本 → BGE-M3 模型转为 1024 维向量 → Redis HNSW 索引 ANN 搜索 → 返回最相似商品。

### Step 6: LLM 生成推荐 (04-LLM-PROVIDER.md + 08-LANGGRAPH-MEMORY.md)

Agent 将召回商品 + thread_id 历史会话 → 通义千问 → 总结导语 + 推荐理由 + 商品列表。

### Step 7: 前端渲染 (01-FRONTEND.md)

```html
<div class="ai-summary-card">{{ aiSummary }}</div>
<ul class="ai-reason-list"><li v-for="r in aiReasonList">{{ r }}</li></ul>
<div class="recommend-goods"><div v-for="item in aiRecommendList">...</div></div>
```

### Step 0: 数据准备（离线） (09-DATA-SYNC.md)

管理员调用 `/api/v1/sync` 触发：MySQL 商品数据 → 文本切片 → 向量化 → 写入 RedisVL。

---

## 三、文档体系

| 编号 | 文档 | 核心栈 | 对照 Spring 组件 | 面向面试 |
|------|------|--------|-----------------|---------|
| 00 | 本篇·全景导读 | 架构总览 | — | 链路图面试表达 |
| 01 | [前端技术栈](./01-FRONTEND.md) | Vue3, Axios, Promise.allSettled | — | 前端与 AI 后端协作 |
| 02 | [FastAPI 网关层](./02-API-GATEWAY.md) | FastAPI, Uvicorn, Pydantic v2 | Spring Boot, Jackson | 网关设计模式 |
| 03 | [多 Provider 配置体系](./03-CONFIG-MULTI-PROVIDER.md) | pydantic-settings, dotenv, 策略模式 | @ConfigurationProperties | 配置架构设计 |
| 04 | [LLM 服务商对接](./04-LLM-PROVIDER.md) | ChatOpenAI, 阿里云通义千问, Agnes AI | Spring AI ChatClient | 多供应商切换 |
| 05 | [Embedding 向量化](./05-EMBEDDING.md) | OpenAIEmbeddings, BGE-M3, SiliconFlow | Spring AI EmbeddingClient | 文本向量化原理 |
| 06 | [RedisVL 向量存储与检索](./06-VECTOR-STORE.md) | RedisVL, Redis Stack, HNSW | Spring Data Redis | 向量数据库选型 |
| 07 | [LangChain Agent 机制](./07-LANGCHAIN-AGENT.md) | create_agent, Tool, Structured Output | 工作流引擎, 策略模式 | Agent 架构设计 |
| 08 | [LangGraph 记忆与状态](./08-LANGGRAPH-MEMORY.md) | Checkpointer, InMemorySaver, RedisSaver | 会话管理, 状态机 | 对话记忆实现 |
| 09 | [数据同步链路](./09-DATA-SYNC.md) | SQLAlchemy, TextSplitter, tiktoken | Spring Data JPA, ETL | 数据 ETL 设计 |
| 10 | [架构复盘与面试题集](./10-ARCHITECTURE.md) | 全栈复盘, 架构模式, 面试题 | 跨栈对比总表 | 面试高频题 |

### 阅读顺序

**推荐顺序（链路驱动）：** 00 → 01 → 02 → 03 → 07 → 04 → 05 → 06 → 08 → 09 → 10

**按需跳读：**
- 只想看 AI 核心：00 → 07 → 04 → 05 → 06 → 08 → 10
- 只想看 Java 对照：00 → 02 → 03 → 04 → 05 → 06 → 10
- 只想看面试题：直接跳到 10

---

## 四、三条贯穿主线

### 主线 1：请求链路（从前端到 AI 再到前端）

```
01 → 02 → 03 → 07 → 04 → 05 → 06 → 08 → 01
```

这条线追踪一次用户查询的完整路径，每个技术栈回答"我在请求链路中的位置和职责"。

### 主线 2：数据流（从 MySQL 到向量库）

```
09 → 06 → 05 → 07 → 04 → 02 → 01
```

这条线追踪商品数据从 MySQL 表结构 → 文本切片 → 向量化 → 索引存储 → 检索召回 → 前端渲染的全过程。

### 主线 3：配置切换（Provider 开关设计）

```
03 → 04 → 05
```

这条线展示 `EMBED_PROVIDER` 和 `LLM_PROVIDER` 两个开关如何控制整条链路的 AI 供应商选择，是策略模式在 AI 工程中的典型实践。

---

## 五、前置知识要求

| 领域 | 要求 | 不需要 |
|------|------|--------|
| **Python** | 基础语法（变量、函数、类、异步） | 深度学习/数据科学 |
| **Java/Spring** | Spring Boot 基础（IoC、MVC、配置） | Spring Cloud 全栈 |
| **数据库** | 基础 SQL、Redis 基础操作 | 数据库 internals |
| **AI 基础** | 了解"什么是大模型"即可 | 手写 Transformer |

> 如果你是 Java 工程师但对 Python 不熟，不要担心——Python 在本项目中仅作为"胶水层"使用，每一篇都会附 Java 对照代码帮助你理解。

---

## 六、项目整体技术栈层次

```
┌───────────────────────────────────────────────────────────────┐
│                     应用层 (Application)                       │
│  Vue3 前端  ·  FastAPI 网关  ·  Pydantic 模型                  │
├───────────────────────────────────────────────────────────────┤
│                      AI 框架层 (AI Framework)                  │
│  LangChain Agent  ·  LangGraph  ·  OpenAI Compatible SDK       │
│  create_agent  ·  Checkpointer  ·  Structured Output           │
├───────────────────────────────────────────────────────────────┤
│                    模型服务层 (Model Service)                   │
│  阿里云通义千问  ·  SiliconFlow(BGE-M3)  ·  OpenRouter          │
│  Agnes AI  ·  ChatOpenAI  ·  OpenAIEmbeddings                  │
├───────────────────────────────────────────────────────────────┤
│                      数据层 (Data)                             │
│  MySQL(SKU)  ·  Redis Stack(向量)  ·  RedisVL(索引)            │
│  SQLAlchemy  ·  tiktoken  ·  TextSplitter                      │
└───────────────────────────────────────────────────────────────┘
```

---

## 七、面试价值

学习完本系列，你将能够在面试中回答以下类型的问题：

1. **"讲一个你做过的最有技术含量的项目"** → 用本系列的全链路架构图做蓝本
2. **"AI 搜索与传统搜索的区别"** → 语义理解 vs 关键词匹配，向量召回 vs ES 倒排索引
3. **"Agent 是什么，为什么需要 Agent"** → 结合 LangChain Agent 的 tool-calling 机制
4. **"向量数据库选型考量"** → Redis vs Milvus vs Pinecone，结合项目场景
5. **"多供应商 AI 切换设计"** → 策略模式 + 工厂模式 + 环境变量配置
6. **"对话记忆怎么实现"** → LangGraph Checkpointer + thread_id 隔离
7. **"Java 中怎么实现类似功能"** → Spring AI 生态的对应组件

---

> **下一篇：** [01-FRONTEND.md —— 前端技术栈（Vue3 + Axios + AI/ES 搜索模式）](./01-FRONTEND.md)
>
> 从用户点击"搜索"按钮开始，看前端如何管理两种搜索模式，以及如何用 Promise.allSettled 实现容错调用。