# AI_EXAM Word 教材与 AI 应用教程整合设计

## 目标

将 `D:/code/codeByCursor/AI_EXAM/docs` 中的 12 个 Word 文档转化为可追溯、可学习、可验证的 Markdown 教程资产，并与 `interview-note` 现有 LangChain、RAG、LangGraph、Neo4j、SSE 和面试文档建立清晰的导航关系。

## 范围

### 纳入范围

- 12 个 `.docx` 的内容、重复关系、章节结构和技术风险梳理；
- `2-learning` 下教程入口、教程索引和相关现有教程的增补；
- Word 来源映射、技术版本说明、项目边界说明；
- 以 CropWise 农业知识库问答为贯穿案例，串联文档摄入、RAG、GraphRAG、Agent、MCP、SSE 和评估；
- 代码示例的版本标注、运行前提和安全说明。

### 不纳入范围

- 不修改 `AI_EXAM/docs` 原始 Word 文件；
- 不把简历文档当作教程事实来源；
- 不把未经官方资料或实际运行验证的 Harness 命令直接写成稳定操作指南；
- 不重构无关的 Java、商城或其他项目文档。

## 文档资产决策

| Word 文档 | 处理决策 |
|---|---|
| `为什么要学AI.docx` 与 `(1)` | 合并为一份课程导入素材，标记 `(1)` 为完全重复副本 |
| `几个重要概念.docx` | 转为 LLM、Token、Context、Prompt、Tool、Memory 基础章节 |
| `三大组件.docx` | 转为 LangChain/LangGraph/LangSmith 生态总览，核验 v1 API |
| `第1章 概述.docx` | 作为课程主入口，保留 AI 基础、MaaS、环境和 AI 编程实践 |
| `第2章 构建智能体.docx` | 转为 Agent 基础、工具、记忆、结构化输出和流式章节 |
| `第3章 智能体的高级特性.docx` | 转为中间件、动态 Prompt、多模态、Runnable 和并发进阶章节 |
| `第 4 章 LangGraph 框架.docx` | 转为状态图、条件路由、循环、检查点和恢复章节 |
| `第 5 章 检索增强生成.docx` | 转为文档摄入、分块、Embedding、向量检索和 RAG 实战章节 |
| `第 6 章 模型上下文协议.docx` | 转为 MCP Tools/Resources/Prompts、客户端和安全章节 |
| `DeepSeek-Harness1.docx` | 独立本地 Agent/Harness 专题，官方资料核验后再写实操 |
| `AI 应用开发岗 标准简历.docx` | 转为项目面试和简历表达参考，不作为技术事实唯一来源 |

## 教程架构

按学习依赖组织为：

```text
课程动机 → AI 基础 → Prompt/RAG/Tool/Agent → LangChain 生态与环境
→ Agent 基础 → Agent 高级特性 → LangGraph
→ RAG 文档链路 → 农业 Hybrid RAG/GraphRAG
→ 证据门控与安全 → MCP → FastAPI/SSE/记忆
→ 评估、部署与生产化 → 面试表达 → Harness 专题
```

农业主线明确使用 CropWise 农业知识库问答；智慧农业管理系统作为独立业务背景，不与 CropWise 混写。

## 技术与内容约束

- 每个新增教程包含：目标、前置知识、核心概念、最小示例、运行前提、常见故障、面试要点和来源链接；
- 代码必须标注 Python/依赖版本，区分教学示例、项目配置和生产建议；
- RAG 教程明确区分向量分数、BM25 分数、融合分数和重排分数，不跨数据库直接比较；
- 分块、Memory、Embedding、图谱规模等参数必须标注来源和版本，不把不同阶段配置混为一谈；
- Agent 和 MCP 示例必须包含工具白名单、参数校验、超时、调用次数限制、审计和敏感信息保护说明；
- 所有外部链接使用相对路径或权威官方来源；
- 原始 Word 文件只读引用，不复制二进制文件到仓库。

## 验收标准

1. 12 个 Word 文件均有来源映射和处理结论；
2. 重复文件被明确标记，课程章节编号和术语统一；
3. `2-learning` 有可从总目录进入的 Word 教材整合入口；
4. 现有 RAG、LangGraph、Neo4j、SSE 教程与新主线互相链接；
5. CropWise 与智慧农业管理系统边界有明确说明；
6. 技术待核验项有清单，未经核验内容不以确定事实发布；
7. Markdown 链接、代码围栏、目录索引和格式检查通过；
8. 交付报告包含文件变更清单、来源映射、未解决风险和验证结果。
