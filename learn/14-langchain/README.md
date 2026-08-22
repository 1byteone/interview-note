# LangChain — LCEL · Chain · Agent · Tool · Memory

> 面向后端开发者的 LangChain 实战教程，覆盖声明式编排（LCEL）、Chain 组装、Agent 与 Tool 调用、记忆管理等核心能力。
> 场景项目：AI 智能商城（mall-ai-search 商品推荐搜索 + Agent 客服 + 工具编排）

---

## 学习路径图

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                    LangChain 学习路径（双轨制）                                 │
├──────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  01-basics 👶                                                               │
│  ┌──────────────────────────┐  ┌──────────────────────────────┐             │
│  │ LangChain 快速入门        │  │ LCEL 声明式编排               │             │
│  │ 安装 · 核心概念           │  │ 管道操作符 · Runnable 系列     │             │
│  │ 第一个 Chain             │  │ vs 手写 Python                │             │
│  └────────────┬─────────────┘  └──────────────┬───────────────┘             │
│               │                                │                             │
│               ▼                                ▼                             │
│  02-core 👶→🎯                                                              │
│  ┌──────────────┐ ┌──────────┐ ┌──────────────┐ ┌──────────────┐           │
│  │ 输出解析器     │ │ 记忆管理  │ │ 检索增强 RAG  │ │ 模型调用      │           │
│  │ Pydantic     │ │ 会话记忆  │ │ 文档加载      │ │ 多模态        │           │
│  │ JSON · XML   │ │ 对话缓存  │ │ 向量检索      │ │ 流式输出      │           │
│  └──────┬───────┘ └────┬─────┘ └──────┬───────┘ └──────┬───────┘           │
│         │              │              │                 │                    │
│         ▼              ▼              ▼                 ▼                    │
│  03-advanced 🎯                                                              │
│  ┌──────────────┐ ┌──────────────┐ ┌──────────────┐ ┌──────────────┐       │
│  │ Agent 原理    │ │ Tool 调用    │ │ 多 Agent 编排 │ │ LangGraph     │       │
│  │ ReAct 循环    │ │ 自定义工具    │ │ Team · 分工   │ │ 状态机·持久化  │       │
│  │ 工具选择      │ │ 容错与重试    │ │ 任务分解      │ │ Checkpoint    │       │
│  └──────┬───────┘ └──────┬───────┘ └──────┬───────┘ └──────┬───────┘       │
│         │                │                │                │                 │
│         ▼                ▼                ▼                ▼                 │
│  04-projects 🎯                                                              │
│  ┌──────────────────────────┐  ┌──────────────────────────────┐             │
│  │ AI 商城集成               │  │ 迷你智能客服 Agent            │             │
│  │ 商品推荐链 · 搜索工具      │  │ 工具选择 · 会话记忆 · 结构化输出 │             │
│  │ Agent 客服 · 条件提取     │  │ 流式响应 · 可观测性           │             │
│  └──────────────────────────┘  └──────────────────────────────┘             │
│                                                                              │
│  05-interview 📝                                                             │
│  ┌───────────────────────────────────────────────────────────────────────┐   │
│  │ 速记 · 深挖 · 场景 · 代码                                               │   │
│  └───────────────────────────────────────────────────────────────────────┘   │
│                                                                              │
└──────────────────────────────────────────────────────────────────────────────┘
```

---

## 前置知识

- **Python**：LangChain 生态基于 Python，需掌握基础语法、类型注解与异步（asyncio），可先完成 04-python 教程
- **LLM 基础**：了解大模型 API 调用方式（Chat Completions）、Prompt 工程、temperature 等采样参数
- **FastAPI**：AI 商城中的 LangChain 服务以 FastAPI 暴露接口，可参考 05-fastapi 教程
- **RAG 基础**：Agent 的检索工具通常依赖向量库，可参考 15-rag 教程

建议先完成本系列的 **Python**（04-python）与 **FastAPI**（05-fastapi）教程，再开始学习 LangChain。

---

## 面试高频考点一览表

| 考点 | 重要程度 | 频次 | 说明 | 章节 |
|------|----------|------|------|------|
| LCEL 管道操作符 | ⭐⭐⭐⭐⭐ | 高频 | `\|` 组合、Runnable 协议、与手写 Python 的对比 | 01-basics/02 |
| Runnable 核心接口 | ⭐⭐⭐⭐⭐ | 高频 | RunnablePassthrough / Parallel / Lambda / branch | 01-basics/02 |
| PromptTemplate 与变量注入 | ⭐⭐⭐⭐⭐ | 高频 | 字符串 / ChatPromptTemplate / MessagesPlaceholder | 01-basics/01 |
| 输出解析器（OutputParser） | ⭐⭐⭐⭐ | 中频 | StrOutputParser / PydanticOutputParser / 结构化输出 | 02-core/01 |
| ChatModel 与 LLM 的区别 | ⭐⭐⭐⭐ | 中频 | 消息序列 vs 纯文本、流式与工具调用 | 01-basics/01 |
| Chain 的组装与复用 | ⭐⭐⭐⭐ | 中频 | LLMChain、sequential、与 LCEL Runner 的关系 | 01-basics/01 |
| Agent 原理（ReAct） | ⭐⭐⭐⭐⭐ | 高频 | 思考-行动-观察循环、工具选择、停止条件 | 03-advanced/01 系列 |
| 自定义 Tool | ⭐⭐⭐⭐⭐ | 高频 | @tool 装饰器、参数 Schema、容错与幂等 | 03-advanced/02 |
| 记忆管理（Memory） | ⭐⭐⭐⭐ | 中频 | 会话记忆、窗口记忆、持久化存储 | 02-core/02 |
| LangGraph 与状态机 | ⭐⭐⭐⭐ | 中频 | 图编排、Checkpoint、thread_id 会话隔离 | 03-advanced/04 |

> 商城项目中的 LangChain 考点（参考 `projects/ai-mall/mall-ai-search-knowledge-map.md`）：Agent 与固定 Chain 的职责区别、vector_search_tool 的召回数量（top-k）、结构化输出（Pydantic Response）、thread_id 会话传入、Agent 空召回仍生成商品的 Bug 排查。

---

## LangChain 在 AI 商城中的角色

在 mall-ai-search 智能搜索项目中，Java/Spring Boot 负责交易链路，Python（FastAPI + LangChain）负责**每一个 AI 编排位置**：

```
┌──────────────────────────────────────────────────────────────────┐
│                     AI 智能商城 · LangChain 落位                    │
├──────────────────────────────────────────────────────────────────┤
│                                                                    │
│  1. 商品推荐链（Chain）                                            │
│     PromptTemplate ─▶ ChatModel ─▶ 结构化解析 ─▶ 推荐响应           │
│     输入：用户偏好 / 价格区间 / 品牌诉求                            │
│                                                                    │
│  2. AI 搜索 Agent（Agent + Tool）                                  │
│     create_agent ─▶ vector_search_tool（RedisVectorStore, k=10）  │
│     条件提取链（keyword / min_price / max_price）                  │
│                                                                    │
│  3. 会话与记忆（Memory）                                           │
│     thread_id ─▶ Checkpoint（当前 InMemorySaver，规划 Redis）      │
│                                                                    │
│  4. 接口暴露（FastAPI）                                            │
│     /api/v1/recommend · /api/v1/extract · /api/v1/test            │
│     网关经 OpenFeign 调用 Python 服务                             │
│                                                                    │
└──────────────────────────────────────────────────────────────────┘
```

**一句话概括**：Java 管交易，LangChain 管智能编排——它把「模型调用、提示词、工具、记忆」拼装成可维护、可观测、可测试的 AI 业务链路。

---

## 📖 导航

| ← 上一篇 | 📚 目录 | 下一篇 → |
|----------|---------|----------|
| [← 开发工具](../13-dev-tools/README.md) | [📚 总目录](../README.md) | [RAG →](../15-rag/README.md) |

**相关技术栈：**
- [05-FastAPI](../05-fastapi/README.md) — FastAPI 暴露 LangChain 编排的 AI 服务为 REST API
- [16-OpenAI](../16-openai/README.md) — LangChain 通过 OpenAI 模型实现对话、推理与工具调用

---

## 项目剖析深度参考

本 learn 文档提供理论基础，以下 `docs/tech-stack-analysis/` 文档提供**真实项目中的落地代码**：

| 本 learn 核心内容 | 对应项目剖析 | 重点看什么 |
|------------------|------------|-----------|
| Agent + Tool + Structured Output | [07-LANGCHAIN-AGENT.md](../../docs/tech-stack-analysis/mall-ai-search/07-LANGCHAIN-AGENT.md) | `create_agent` + `@tool` + `response_format` |
| Checkpointer 记忆管理 | [08-LANGGRAPH-MEMORY.md](../../docs/tech-stack-analysis/mall-ai-search/08-LANGGRAPH-MEMORY.md) | InMemorySaver + thread_id 会话隔离 |
| LLM 多供应商切换 | [04-LLM-PROVIDER.md](../../docs/tech-stack-analysis/mall-ai-search/04-LLM-PROVIDER.md) | 阿里云通义千问 + Agnes AI + OpenAI 兼容 |
| Java Spring AI 对标 | [01-LLM-CLIENT.md](../../docs/tech-stack-analysis/text2sql/01-LLM-CLIENT.md) | Spring AI ChatClient 对接 DeepSeek |