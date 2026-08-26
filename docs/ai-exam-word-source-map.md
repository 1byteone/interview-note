# AI_EXAM Word 教材来源映射

> 本表将 `D:/code/codeByCursor/AI_EXAM/docs` 中的原始 Word 教材映射到本知识库的 Markdown 教程。原始 Word 文件只作为只读素材，不复制到本仓库。

## 文件清单与处理决策

| 原始 Word 文件 | 主题与内容范围 | 处理决策 | Markdown 去向 |
|---|---|---|---|
| `为什么要学AI.docx` | 从 Elasticsearch 关键词搜索升级到 LLM、RAG、Agent 智能导购 | 保留动机，删减宣传性指标 | `2-learning/projects/tutorials/00-ai-application-learning-path/README.md` |
| `为什么要学AI (1).docx` | 与上一文件相同 | 标记为完全重复副本，不重复改写 | 同上 |
| `几个重要概念.docx` | LLM、Token、Context、Prompt、Tool、Memory | 改写为概念前置章节 | `01-ai-llm-prompt-agent-basics.md` |
| `三大组件.docx` | LangChain、LangGraph、LangSmith、Prompt/RAG/Agent 范式 | 合并为生态与版本边界章节 | `02-environment-and-langchain.md` |
| `第1章 概述.docx` | AI 基础、MaaS、Conda、开发工具、LangChain、AI 编程 | 作为正式课程入口素材，拆分并去重 | `README.md`、`01-ai-llm-prompt-agent-basics.md`、`02-environment-and-langchain.md` |
| `第2章 构建智能体.docx` | Agent、工具、Memory、结构化输出、流式、Runnable | 改写为 Agent 基础实战 | `03-agent-fundamentals.md` |
| `第3章 智能体的高级特性.docx` | 流式、中间件、动态 Prompt、多模态、并行和条件分支 | 改写为进阶章节 | `04-agent-advanced-features.md` |
| `第 4 章 LangGraph 框架.docx` | StateGraph、节点、边、循环、检查点、中断恢复 | 改写为状态化工作流 | `05-langgraph-stateful-workflows.md` |
| `第 5 章 检索增强生成.docx` | 文档加载、分块、Embedding、向量库、农业知识库 | 拆分摄入与混合检索，补充参数口径 | `06-document-ingestion-and-chunking.md`、`07-agri-hybrid-rag.md` |
| `第 6 章 模型上下文协议.docx` | MCP Host/Client/Server、Tools、Resources、Prompts | 改写为协议和安全专题 | `10-mcp-tools-and-resources.md` |
| `DeepSeek-Harness1.docx` | Ollama、本地模型、Harness、插件化 Agent | 独立专题，先核验命令再写实操 | `14-ollama-and-harness.md` |
| `AI 应用开发岗 标准简历.docx` | AI 应用岗位简历、农业/电商项目表述 | 仅用于面试表达和事实核对 | `13-interview-and-resume-mapping.md` |

## 重复与边界

- 两个 `为什么要学AI` 文件内容重复，`(1)` 文件不作为独立版本。
- `AI_EXAM/docs/README.md` 描述的是智慧农业管理系统：`FastAPI + SQLite + Vue3 + ECharts + IoT`。
- CropWise 农业知识库问答是另一条项目线：`FastAPI + LangGraph + RAG + ChromaDB + Neo4j + SSE`。
- Word 中的参数、性能数字和 API 版本不自动视为项目真实配置，必须参考 `docs/ai-exam-tech-review.md`。

## 使用规则

1. 教程引用 Word 内容时，注明原始文件名和对应主题。
2. 教学示例、项目当前配置、历史配置、生产建议分开书写。
3. 高变 API 先核验官方文档；无法核验时使用概念伪代码并标记待确认。
4. 原始文件路径只在来源说明中出现，不写入可复制运行的代码。
