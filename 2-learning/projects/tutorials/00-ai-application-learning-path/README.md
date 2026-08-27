# AI 应用开发专项教程

> 基于 `AI_EXAM/docs` Word 教材整理的 Markdown 学习主线。原始 Word 文件保存在外部素材目录，本教程只维护可检索、可验证和可复用的 Markdown 内容。

## 学习目标

完成本路线后，能够：

- 解释 LLM、Token、Context、Prompt、Tool、Agent 和 Memory；
- 使用 LangChain/LangGraph 设计有状态 Agent；
- 构建农业文档摄入、向量检索、混合检索和 GraphRAG 链路；
- 使用证据门控、领域守卫和安全拒答减少农业场景幻觉；
- 理解 MCP 的 Tools、Resources 和 Prompts；
- 使用 FastAPI + SSE 提供流式问答接口；
- 按检索、生成、安全和系统指标评估 RAG 应用。

## 推荐顺序

```text
动机与边界
  → LLM / Prompt / Agent 基础
  → 环境与 LangChain 生态
  → Agent 基础与工具调用
  → Agent 高级特性
  → LangGraph 状态化工作流
  → 文档摄入与分块
  → 农业 Hybrid RAG
  → 农业 GraphRAG
  → 证据门控与安全拒答
  → MCP
  → FastAPI + SSE + Memory
  → 评估与生产化
  → 面试表达
  → Ollama / Harness 专题
```

## 章节导航

| 顺序 | 教程 | Word 来源 | 状态 |
|---:|---|---|---|
| 00 | 本入口：学习动机、项目边界和路线 | `为什么要学AI.docx`、`第1章 概述.docx` | 当前页 |
| 01 | [AI、LLM、Prompt 与 Agent 基础](01-ai-llm-prompt-agent-basics.md) | `几个重要概念.docx` | 已建立入口 |
| 02 | [开发环境与 LangChain 生态](02-environment-and-langchain.md) | `三大组件.docx`、`第1章 概述.docx` | 已完成 |
| 03 | [Agent 基础与工具调用](03-agent-fundamentals.md) | `第2章 构建智能体.docx` | 已完成 |
| 04 | [Agent 高级特性](04-agent-advanced-features.md) | `第3章 智能体的高级特性.docx` | 已完成 |
| 05 | [LangGraph 状态化工作流](05-langgraph-stateful-workflows.md) | `第 4 章 LangGraph 框架.docx` | 已完成 |
| 06 | [农业文档摄入与分块](06-document-ingestion-and-chunking.md) | `第 5 章 检索增强生成.docx` | 已完成 |
| 07 | [农业 Hybrid RAG](07-agri-hybrid-rag.md) | `第 5 章 检索增强生成.docx` | 已完成 |
| 08 | [农业 GraphRAG](08-agri-graph-rag.md) | `第 4 章 LangGraph 框架.docx`、`第 5 章 检索增强生成.docx` | 已完成 |
| 09 | [证据门控、安全与拒答](09-evidence-safety-and-refusal.md) | `第2章`、`第5章`、项目资料 | 已完成 |
| 10 | [MCP Tools 与 Resources](10-mcp-tools-and-resources.md) | `第 6 章 模型上下文协议.docx` | 已完成 |
| 11 | [FastAPI、SSE 与 Memory](11-fastapi-sse-and-memory.md) | `第2章`、`第3章`、`第 6 章` | 已完成 |
| 12 | [RAG 评估与生产化](12-rag-evaluation-and-production.md) | `第5章`、项目资料 | 已完成 |
| 13 | [面试与简历表达](13-interview-and-resume-mapping.md) | `AI 应用开发岗 标准简历.docx` | 已完成 |
| 14 | [Ollama 与 Harness](14-ollama-and-harness.md) | `DeepSeek-Harness1.docx` | 待核验 |

## 农业项目边界

### CropWise 农业知识库问答

```text
PDF / DOCX / TXT
  → 解析、清洗、分块和元数据
  → Embedding + ChromaDB
  → BM25 / 向量 / Neo4j 混合召回
  → RRF + Reranker
  → 证据门控
  → LangGraph Agent
  → FastAPI + SSE
```

### 智慧农业管理系统

```text
农业数据、设备、传感器、告警和地块
  → FastAPI + SQLite
  → Vue3 + Element Plus + ECharts
  → IoT / MQTT 预留
```

两者业务目标和数据流不同，不能把管理系统的设备数据接口写成知识库问答的检索来源。

## 来源与审核

- [Word 来源映射](../../../../docs/ai-exam-word-source-map.md)
- [技术审核清单](../../../../docs/ai-exam-tech-review.md)
- [现有 Hybrid RAG 教程](../13-hybrid-rag/README.md)
- [现有 LangGraph 教程](../14-langgraph-agent/README.md)
- [现有 GraphRAG 教程](../15-neo4j-graph/README.md)
- [现有 SSE 教程](../16-sse-streaming/README.md)
- [RAG 系统教程](../../../stacks/15-rag/README.md)
- [LangChain 技术栈](../../../stacks/14-langchain/README.md)
- [FastAPI 技术栈](../../../stacks/05-fastapi/README.md)

> 版本提醒：LangChain、LangGraph、MCP、云模型和 Harness API 变化较快。教程中的代码必须与明确依赖版本一起验证。
