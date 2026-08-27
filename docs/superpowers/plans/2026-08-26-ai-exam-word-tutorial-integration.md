# AI_EXAM Word 教材整合实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 `D:/code/codeByCursor/AI_EXAM/docs` 中的 12 个 Word 教材整理为可追溯的 Markdown 学习主线，并同步增强 `interview-note` 的 AI 应用、农业 RAG、Agent、MCP 和索引文档。

**Architecture:** 保留 AI_EXAM Word 文件为外部只读素材，在 `interview-note/2-learning` 中建立整合入口和分层教程；以 CropWise 农业知识库问答贯穿 RAG、LangGraph、MCP、SSE 和评估，以现有短教程作为可复用章节而非重复创建。所有高变 API 先建立核验说明，再将代码写入教程。

**Tech Stack:** Markdown、Python 3.x 教学示例、LangChain、LangGraph、ChromaDB、Neo4j、FastAPI、SSE、MCP；文档校验使用项目现有 Markdown/链接检查脚本。

**Spec:** `docs/superpowers/specs/2026-08-26-ai-exam-word-tutorial-integration-design.md`

## Global Constraints

- 不修改 `D:/code/codeByCursor/AI_EXAM/docs` 中的原始 Word 文件。
- 所有教程代码必须标注版本、依赖和运行前提；未经核验的 API 标注“待确认”，不得伪装成稳定接口。
- CropWise 农业知识库问答与智慧农业管理系统必须分开描述。
- RAG 分数、分块参数、Memory 实现和图谱统计必须区分教学示例、项目配置和生产建议。
- 新增 Markdown 使用 UTF-8、LF、中文与英文之间留空格、相对链接和规范代码围栏。
- 每个新增或显著改写的教程必须包含学习目标、前置知识、实操示例、故障/安全边界和面试要点。
- 不修改 `_scripts/`，除非验证命令本身证明脚本存在缺陷且获得单独授权。

---

### Task 1: 建立 Word 来源映射和版本审核表

**Files:**
- Create: `docs/ai-exam-word-source-map.md`
- Create: `docs/ai-exam-tech-review.md`
- Modify: `2-learning/README-learning.md`（仅在确认存在对应入口后添加导航）

**Interfaces:**
- Produces：12 个 Word 文件的路径、主题、处理决策、对应教程和待核验项；后续教程以该表作为来源声明。

- [ ] **Step 1: 编写来源映射表**

逐条登记 12 个 Word 文件，明确重复副本、保留/改写/合并/专题化决策、原始主题、目标 Markdown 章节和农业项目关联。对 `为什么要学AI (1).docx` 明确写明与主文件二进制重复。

- [ ] **Step 2: 编写技术审核表**

按“可直接引用 / 需要核验 / 不作为事实来源”三类记录：`create_agent`、检查点、LangGraph 入口、LangSmith 配置、RedisStack、MCP SDK、Harness CLI、模型指标和 Python 兼容性。

- [ ] **Step 3: 增加来源入口链接**

在现有学习总入口中增加“AI_EXAM Word 教材整合”链接；链接目标只能使用仓库内相对路径。

- [ ] **Step 4: 检查链接和格式**

运行：`python _scripts/check_links.py`（若仓库脚本要求其他参数，按脚本帮助执行）。Expected：新增来源入口可解析；无 Markdown 代码围栏或相对路径错误。

- [ ] **Step 5: Commit**

```bash
git add docs/ai-exam-word-source-map.md docs/ai-exam-tech-review.md 2-learning/README-learning.md
git commit -m "docs: map AI_EXAM Word sources"
```

---

### Task 2: 建立 AI 应用课程总入口和项目边界

**Files:**
- Create: `2-learning/projects/tutorials/00-ai-application-learning-path/README.md`
- Create: `2-learning/projects/tutorials/00-ai-application-learning-path/source-map.md`
- Modify: `2-learning/projects/tutorials/README.md`
- Modify: `README.md`

**Interfaces:**
- Consumes：Task 1 的来源映射和审核表。
- Produces：统一课程导航、前置依赖、学习顺序，以及两个农业项目的边界说明。

- [ ] **Step 1: 写课程入口**

建立动机、基础、Prompt/RAG/Tool/Agent、LangChain、Agent、LangGraph、RAG、农业 Hybrid RAG/GraphRAG、安全、MCP、SSE、评估、面试和 Harness 的学习路径。每个节点链接到已有教程或本计划后续创建的文件，禁止失效链接。

- [ ] **Step 2: 写项目边界说明**

明确：智慧农业管理系统是 `FastAPI + SQLite + Vue3 + ECharts + IoT`；CropWise 农业知识库问答是 `FastAPI + LangGraph + RAG + ChromaDB + Neo4j + SSE`，说明业务目标和数据流不同。

- [ ] **Step 3: 写 Word 到教程的对应表**

将来源映射压缩成读者可用的章节表，并链接至 `docs/ai-exam-word-source-map.md`。

- [ ] **Step 4: 更新项目教程索引**

在 `2-learning/projects/tutorials/README.md` 增加总入口，保持现有编号教程不改名、不移动。

- [ ] **Step 5: 验证并提交**

运行链接检查，确认新链接目标存在；提交：

```bash
git add 2-learning/projects/tutorials/00-ai-application-learning-path 2-learning/projects/tutorials/README.md README.md
git commit -m "docs: add AI application learning path"
```

---

### Task 3: 补充基础概念、环境和 LangChain 版本边界

**Files:**
- Create: `2-learning/projects/tutorials/00-ai-application-learning-path/01-ai-llm-prompt-agent-basics.md`
- Create: `2-learning/projects/tutorials/00-ai-application-learning-path/02-environment-and-langchain.md`
- Modify: `2-learning/stacks/14-langchain/README.md`

**Interfaces:**
- Consumes：Task 2 的总入口和 Task 1 的版本审核表。
- Produces：从 `几个重要概念.docx`、`三大组件.docx`、`第1章 概述.docx` 整合出的基础章节。

- [ ] **Step 1: 编写基础概念章节**

覆盖 LLM、Token/TokenId、Context Window、System/User Prompt、Tool Calling、Agent 闭环和短期/长期 Memory，明确 tokenizer 差异、TokenId 不可跨模型复用和 Context 不等于持久记忆。

- [ ] **Step 2: 编写环境章节**

覆盖 Python 虚拟环境、依赖锁定、MaaS API Key、`.env.example`、日志脱敏、Jupyter/VS Code/Cursor/Copilot 的角色；不写未经核验的固定免费额度。

- [ ] **Step 3: 更新 LangChain 索引**

增加 Word 来源、LangChain/LangGraph/LangSmith 职责边界和版本核验提示；区分经典六组件模型与 v1 现代 API。

- [ ] **Step 4: 验证并提交**

检查术语和链接，提交：

```bash
git add 2-learning/projects/tutorials/00-ai-application-learning-path 2-learning/stacks/14-langchain/README.md
git commit -m "docs: add AI fundamentals and LangChain boundaries"
```

---

### Task 4: 补充 Agent 基础、高级特性和 LangGraph 主线

**Files:**
- Create: `2-learning/projects/tutorials/00-ai-application-learning-path/03-agent-fundamentals.md`
- Create: `2-learning/projects/tutorials/00-ai-application-learning-path/04-agent-advanced-features.md`
- Create: `2-learning/projects/tutorials/00-ai-application-learning-path/05-langgraph-stateful-workflows.md`
- Modify: `2-learning/projects/tutorials/14-langgraph-agent/README.md`

**Interfaces:**
- Consumes：Task 3 的基础概念和版本边界。
- Produces：Agent、工具、结构化输出、流式、Runnable、条件路由、状态图、检查点和恢复的连续教学链路。

- [ ] **Step 1: 编写 Agent 基础章节**

用农业病虫害问答解释感知—思考—行动—观察闭环，覆盖工具 Schema、参数校验、Pydantic 输出、Memory 最小版本和生产边界。`create_agent` 仅使用已核验版本，否则标记待确认。

- [ ] **Step 2: 编写高级章节**

覆盖同步/异步/批处理/流式行为、`messages`/`updates`/`custom` 事件、动态 Prompt、中间件、多模态、并行和条件分支，并说明超时、取消、重试和最大步数。

- [ ] **Step 3: 编写 LangGraph 章节**

定义 State、Node、Edge、条件边、循环终止、`thread_id`、检查点和中断恢复，实现 `Domain Guard → Query Router → Retrieval → Evidence Check → Generate/Refuse` 的农业工作流。

- [ ] **Step 4: 更新现有教程**

将 `14-langgraph-agent` 作为项目实战入口，补充工具白名单、调用审计、错误降级和状态隔离说明。

- [ ] **Step 5: 验证并提交**

静态检查所有代码片段的导入和变量一致性，提交：

```bash
git add 2-learning/projects/tutorials/00-ai-application-learning-path 2-learning/projects/tutorials/14-langgraph-agent/README.md
git commit -m "docs: connect Agent and LangGraph learning path"
```

---

### Task 5: 补充农业文档摄入、Hybrid RAG 和 GraphRAG

**Files:**
- Create: `2-learning/projects/tutorials/00-ai-application-learning-path/06-document-ingestion-and-chunking.md`
- Create: `2-learning/projects/tutorials/00-ai-application-learning-path/07-agri-hybrid-rag.md`
- Create: `2-learning/projects/tutorials/00-ai-application-learning-path/08-agri-graph-rag.md`
- Modify: `2-learning/projects/tutorials/13-hybrid-rag/README.md`
- Modify: `2-learning/projects/tutorials/15-neo4j-graph/README.md`
- Modify: `2-learning/stacks/15-rag/README.md`

**Interfaces:**
- Consumes：Task 3 的基础概念和 Task 4 的 LangGraph 路由。
- Produces：农业 PDF/DOCX/TXT 摄入、元数据、分块、去重、向量/BM25/图谱召回、RRF、Reranker 和参数口径。

- [ ] **Step 1: 编写文档摄入章节**

使用 `AI_EXAM/docs/rag_intro.txt` 作为可追溯示例，说明 PDF/DOCX/TXT 解析、页码/标题/来源元数据、清洗、content hash 去重、失败重试和索引状态；教程代码不得写入用户绝对路径。

- [ ] **Step 2: 编写 Hybrid RAG 章节**

说明向量、BM25、Graph 三路召回分工；RRF 只融合排名，不直接比较异构分数；Reranker 负责候选精排；按作物、病虫害、生育期、地区过滤；低证据时拒答或追问。

- [ ] **Step 3: 编写 GraphRAG 章节**

定义农业实体和关系，说明 Neo4j 与向量库边界、实体去重、Cypher 查询和图谱适用场景。

- [ ] **Step 4: 更新现有教程**

将 `13-hybrid-rag`、`15-neo4j-graph` 改为项目型章节入口，并在 `15-rag` 索引中区分农业与商城主线。

- [ ] **Step 5: 参数核查并提交**

将 `512/64`、`1000/200` 等参数标注为教学示例或历史配置，不宣称固定值适用于所有文档；提交：

```bash
git add 2-learning/projects/tutorials/00-ai-application-learning-path 2-learning/projects/tutorials/13-hybrid-rag 2-learning/projects/tutorials/15-neo4j-graph 2-learning/stacks/15-rag/README.md
git commit -m "docs: add agricultural ingestion and hybrid RAG path"
```

---

### Task 6: 补充证据、安全、MCP、SSE 和会话

**Files:**
- Create: `2-learning/projects/tutorials/00-ai-application-learning-path/09-evidence-safety-and-refusal.md`
- Create: `2-learning/projects/tutorials/00-ai-application-learning-path/10-mcp-tools-and-resources.md`
- Create: `2-learning/projects/tutorials/00-ai-application-learning-path/11-fastapi-sse-and-memory.md`
- Modify: `2-learning/projects/tutorials/16-sse-streaming/README.md`
- Modify: 实际存在的 MCP 教程索引（先确认路径）

**Interfaces:**
- Consumes：Task 4 的 Agent/LangGraph 工作流和 Task 5 的检索证据。
- Produces：证据等级、领域守卫、拒答、Prompt Injection 防护、MCP 工具/资源接入、SSE 事件契约和 thread 会话隔离。

- [ ] **Step 1: 编写安全章节**

定义 A/B/C 证据等级、来源页码引用、低置信度拒答、农业高风险回答边界、Prompt Injection 防护、工具白名单、参数校验、SSRF、日志脱敏和审计。

- [ ] **Step 2: 编写 MCP 章节**

从普通函数工具演进到 MCP Server，解释 Host/Client/Server、Tools/Resources/Prompts、发现与调用、权限、超时/重试/幂等和传输安全。未核验 API 只写概念伪代码。

- [ ] **Step 3: 编写 SSE/Memory 章节**

统一 `status`、`delta`、`tool`、`sources`、`memory`、`done`、`error` 事件；说明断线、取消、重连、thread_id 隔离以及 InMemory/SQLite/Redis/PostgreSQL 边界。

- [ ] **Step 4: 更新索引并提交**

确认 MCP 实际路径后更新链接，扫描新增内容中的密钥、内网地址和绝对路径，提交：

```bash
git add 2-learning/projects/tutorials/00-ai-application-learning-path 2-learning/projects/tutorials/16-sse-streaming 3-ecosystem/tutorials 2-learning/stacks
git commit -m "docs: add evidence MCP and SSE guidance"
```

---

### Task 7: 补充评估、生产化和 Harness 专题

**Files:**
- Create: `2-learning/projects/tutorials/00-ai-application-learning-path/12-rag-evaluation-and-production.md`
- Create: `2-learning/projects/tutorials/00-ai-application-learning-path/13-interview-and-resume-mapping.md`
- Create: `2-learning/projects/tutorials/00-ai-application-learning-path/14-ollama-and-harness.md`
- Modify: `4-interview/README.md`

**Interfaces:**
- Consumes：Task 5 的检索链路和 Task 6 的证据/服务协议。
- Produces：评测集设计、检索/生成/安全/系统指标、部署建议、简历面试映射和 Harness 专题边界。

- [ ] **Step 1: 编写评估章节**

按作物、病害、政策、天气分层，区分 Recall@K/MRR/NDCG、Faithfulness/Citation Accuracy、拒答率、延迟、首字时间、并发、成本和缓存指标；说明消融实验。

- [ ] **Step 2: 编写面试映射章节**

将简历 Word 转为技术事实、量化结果、面试追问、来源和不可直接宣称指标的表格。

- [ ] **Step 3: 编写 Harness 专题**

只写已核验的 Ollama/Harness 内容；未验证的 CLI、仓库和插件市场明确标记待确认。

- [ ] **Step 4: 更新面试索引、验证并提交**

检查指标来源、绝对路径和凭据，提交：

```bash
git add 2-learning/projects/tutorials/00-ai-application-learning-path 4-interview
git commit -m "docs: add evaluation production and interview mapping"
```

---

### Task 8: 全量文档质量检查和交付报告

**Files:**
- Create: `docs/ai-exam-tutorial-delivery-report.md`
- Modify: `2-learning/projects/tutorials/README.md`
- Modify: `2-learning/stacks/README-learning.md`
- Modify: `README.md`（仅修正确实缺失或错误的入口）

**Interfaces:**
- Consumes：Tasks 1–7 的全部教程和索引。
- Produces：最终变更清单、来源覆盖率、待核验项、链接检查结果和后续建议。

- [ ] **Step 1: 检查来源覆盖率**

确认 12 个 Word 文件都在来源映射中，且每个有处理决策和目标章节。

- [ ] **Step 2: 检查导航和格式**

检查总 README、学习索引、项目索引、RAG/LangChain/SSE/MCP/面试索引无断链；扫描未闭合代码块、绝对路径、无效链接、凭据样式和 Mermaid 异常。

- [ ] **Step 3: 运行校验**

```bash
python _scripts/check_links.py
python _scripts/validate.py
```

记录既有失败与本次新增内容的独立结果，不修改脚本。

- [ ] **Step 4: 编写交付报告**

报告包含变更文件、Word 来源覆盖表、学习路径、API 核验状态、未解决风险、校验命令和结果。

- [ ] **Step 5: Commit**

```bash
git add docs/ai-exam-tutorial-delivery-report.md 2-learning README.md
git commit -m "docs: finalize AI_EXAM tutorial integration"
```

## 自检清单

- [ ] 12 个 Word 文档全部有来源决策。
- [ ] 课程动机、基础、框架、Agent、RAG、MCP、安全、SSE、评估和面试均有任务。
- [ ] 没有 TBD、TODO 或空泛占位步骤。
- [ ] 新文件路径与现有仓库结构一致。
- [ ] 后续任务引用的路径和术语与前置任务一致。
- [ ] 原始 Word 文件保持不变。
