# LangChain + LangGraph + LangSmith 求职实战平台设计规格

> 状态：设计已获用户确认，等待书面规格复核
> 日期：2026-08-26
> 目标读者：Java 后端工程师、AI 应用工程师、Agent 工程师求职者

## 1. 背景与目标

本项目已有 `2-learning/stacks/14-langchain/`、RAG 教程、LangGraph 面试题和多个 AI 项目分析。现有内容能够覆盖基础概念，但部分示例仍使用旧版 `LLMChain`、`create_react_agent` 和直接绑定单一模型供应商的写法，缺少一条从 LangChain v1 到 LangGraph、LangSmith、Java 服务集成的统一求职主线。

本设计不删除既有教程，而是在其基础上增量现代化，构建一套可学习、可运行、可测试、可面试和可写入简历的体系。

### 总目标

让学习者能够：

1. 使用 LangChain v1 构建可维护的 LLM 应用；
2. 使用 LangGraph 构建有状态、可恢复、可观测的 Agent Workflow；
3. 使用 LangSmith 完成 Trace、Debug、Dataset、Evaluation 和回归测试；
4. 使用 Python + FastAPI 实现 AI 服务；
5. 使用 Java + Spring Boot 集成 AI 服务并消费 REST / SSE；
6. 完成一个可公开展示、可运行、可接受面试深挖的企业级 AI Agent 项目。

### 验收结果

- 教程文档有完整导航、现代 API 和迁移说明；
- 代码窗口支持 Mock / Real 双模式；
- 模型供应商、Embedding、Vector Store 和 LangSmith 配置可替换；
- Agent 具备循环上限、Checkpoint、Memory 和人工审批能力；
- RAG 具备空召回、查询重写、文档评分和引用来源；
- LangSmith 具备 Trace、脱敏、Golden Dataset 和 Evaluation；
- Java Gateway 能调用 Python AI Service 并消费 SSE；
- 项目 README、架构图、测试、评测报告、面试问答和简历表达齐全。

## 2. 方案选择

采用“增量现代化 + 新旗舰项目”方案：

```text
保留旧教程
    ↓
增加 LangChain v1 现代主线
    ↓
增加独立 Python AI 项目代码窗口
    ↓
增加 Java Spring Boot Gateway 集成
    ↓
补充测试、评测、图表、面试与简历交付
```

不采用全面覆写，原因是：

- 避免破坏现有文档和相对链接；
- 保留旧 API 作为迁移背景；
- 降低一次性变更范围；
- 让新旧知识边界可解释。

## 3. 旗舰项目定义

项目名称：**Enterprise Developer Intelligence Platform**（企业级 AI 开发者智能平台）。

### 核心场景

用户提出技术问题后，系统根据意图选择普通问答、企业知识库检索、代码分析、SQL / Redis 诊断或复杂任务流程，并返回结构化回答、引用来源、置信度和可追踪的执行信息。

```text
用户问题
    ↓
输入安全检查
    ↓
意图分类
    ├── 技术知识 → RAG
    ├── 代码问题 → 代码分析工具
    ├── SQL / Redis → 只读诊断工具
    ├── 复杂任务 → LangGraph 规划流程
    └── 不支持 → 安全拒答
    ↓
Agentic RAG / Tool Workflow
    ↓
结构化回答 + 引用
    ↓
LangSmith Trace / Evaluation
```

项目由三个递进子项目组成：

1. **AI Developer Copilot**：Model、Prompt、Structured Output、Tool Calling；
2. **Enterprise Knowledge Assistant**：文档加载、Embedding、Retriever、Rerank、引用；
3. **Agentic RAG / Enterprise Platform**：LangGraph 状态编排、Checkpoint、HITL、LangSmith、Java 集成。

第一阶段不实现真实生产写操作、任意 SQL 执行、真实 GitHub 写权限、真实部署和真实敏感业务数据接入。

## 4. 总体架构

```text
Web / API Client
        ↓ REST / SSE
Spring Boot Gateway
        ↓ HTTP / SSE
FastAPI AI Service
        ├── Model Factory
        ├── LangChain v1
        ├── LangGraph Workflow
        ├── RAG Pipeline
        ├── Read-only Tools
        └── LangSmith
                ├── PostgreSQL Checkpoint
                ├── Redis / Store
                ├── Vector Store
                └── Configurable Model Provider
```

### Java Gateway 职责

- 用户身份、权限和请求校验；
- 会话、租户、`request_id` 管理；
- 调用 Python AI Service；
- REST 与 SSE 转发；
- 超时、限流、统一异常和审计；
- 业务数据库访问。

### Python AI Service 职责

- Model、Prompt、Structured Output；
- Tool Calling、RAG 和 Agent；
- LangGraph State / Node / Edge / Loop；
- Checkpoint、Memory、Store 和 HITL；
- LangSmith Trace / Evaluation。

Java 不依赖 Python 内部的 StateGraph 类名，双方以稳定 HTTP API 合同通信。

## 5. 教程体系

主入口继续使用：

```text
2-learning/stacks/14-langchain/
```

现代主线目录：

```text
01-basics/
├── 01-model-and-provider-abstraction.md
├── 02-prompt-and-context-engineering.md
├── 03-structured-output.md
├── 04-streaming.md
└── examples/

02-core/
├── 01-tool-calling.md
├── 02-agent-basics.md
├── 03-retrieval-and-rag.md
├── 04-hybrid-retrieval-and-rerank.md
├── 05-context-engineering.md
└── examples/

03-advanced/
├── 01-langgraph-state-and-routing.md
├── 02-checkpoint-memory-and-store.md
├── 03-human-in-the-loop.md
├── 04-agentic-rag.md
├── 05-langsmith-tracing.md
├── 06-langsmith-evaluation.md
└── examples/

04-projects/
├── 01-ai-developer-copilot.md
├── 02-enterprise-knowledge-assistant.md
├── 03-agentic-rag.md
└── 04-enterprise-developer-platform.md

05-interview/
├── quick-revision.md
├── deep-dive.md
├── scenario.md
├── coding.md
└── resume-project-story.md
```

旧教程不删除：`LLMChain`、`create_react_agent`、旧版 Evaluation 等内容作为历史 API 或迁移背景保留；现代主线统一使用 LangChain v1 风格，并在文档中显式标注版本边界。

每篇新教程统一包含：学习目标、面试考点、核心概念、最小代码窗口、项目代码窗口、失败案例、测试验证、LangSmith 观测 / 评测、Java 对应理解、面试回答、简历表达和来源链接。

## 6. 代码窗口

新增 Python 项目：

```text
2-learning/projects/langchain-agent-platform/
```

主要结构：

```text
├── README.md
├── pyproject.toml
├── Makefile
├── .env.example
├── docker-compose.yml
├── src/ai_platform/
│   ├── api/
│   ├── config/
│   ├── llm/
│   ├── prompts/
│   ├── tools/
│   ├── rag/
│   ├── graph/
│   ├── observability/
│   └── evaluation/
├── tests/
├── datasets/
├── knowledge/
└── docs/
```

新增 Java 模块：

```text
2-learning/projects/langchain-agent-platform/java-gateway/
```

主要结构：

```text
├── README.md
├── pom.xml
└── src/
    ├── main/java/com/example/aiplatform/
    │   ├── controller/
    │   ├── client/
    │   ├── service/
    │   ├── config/
    │   └── exception/
    └── test/java/com/example/aiplatform/
```

## 7. 业务流程与状态模型

### 意图

```python
class Intent(str, Enum):
    GENERAL_QA = "general_qa"
    KNOWLEDGE_SEARCH = "knowledge_search"
    CODE_ANALYSIS = "code_analysis"
    SQL_DIAGNOSIS = "sql_diagnosis"
    REDIS_DIAGNOSIS = "redis_diagnosis"
    COMPLEX_TASK = "complex_task"
    UNSUPPORTED = "unsupported"
```

### LangGraph State

状态至少包含：

- 请求上下文：`request_id`、`session_id`、`user_id`；
- 输入：`question`、`intent`；
- 执行：`messages`、`retrieved_documents`、`tool_results`；
- 输出：`answer`、`citations`、`confidence`；
- 控制：`retry_count`、`rewrite_count`、`tool_call_count`；
- HITL：`requires_human_review`、`human_decision`；
- 错误：`error_code`、`error_message`。

State 不保存 API Key、完整敏感原文或无法序列化的运行时对象。

### 节点

```text
input_guard → classify_intent → route_request
route_request → direct_answer / retrieve_documents / analyze_code /
                diagnose_sql / diagnose_redis / plan_complex_task
grade_result → rewrite_query / generate_answer / human_review
validate_output → finalize
```

### 循环上限

```text
MAX_REWRITE_COUNT = 2
MAX_TOOL_CALL_COUNT = 5
MAX_TOTAL_STEPS = 12
```

达到上限后必须安全停止或拒答，不能由模型单独决定终止。

## 8. 高风险工具策略

工具分级：

- 只读工具：默认允许，如文档搜索、代码搜索、SQL 解释、Redis 命令分析；
- 模拟写工具：只提供 Mock，如创建工单、生成补丁；
- 高风险工具：不接入真实执行，如 `execute_sql`、`delete_cache`、`modify_code`、`deploy_service`。

高风险动作流程：

```text
Agent 请求动作 → 风险检查 → LangGraph interrupt → 人工确认
                                  ├── approve → resume
                                  ├── reject → safe response
                                  └── timeout → cancel
```

## 9. API 契约

Python 服务提供：

```text
GET  /api/v1/health
POST /api/v1/chat
POST /api/v1/rag/query
POST /api/v1/agent/run
GET  /api/v1/agent/{session_id}/stream
POST /api/v1/agent/{session_id}/resume
```

同步响应至少包含：

```json
{
  "request_id": "req-001",
  "session_id": "session-001",
  "answer": "...",
  "citations": [],
  "confidence": 0.86,
  "trace_id": "trace-001"
}
```

流式事件至少包含：`run_started`、`node_started`、`retrieval`、`token`、`citation`、`run_completed`。

## 10. Provider 解耦与配置

配置通过 Settings 和环境变量注入：

```env
APP_ENV=local
APP_MODE=mock
CHAT_MODEL_PROVIDER=openai
CHAT_MODEL_NAME=gpt-4o-mini
CHAT_MODEL_BASE_URL=
CHAT_MODEL_API_KEY=
EMBEDDING_PROVIDER=mock
EMBEDDING_MODEL_NAME=
EMBEDDING_BASE_URL=
EMBEDDING_API_KEY=
LANGSMITH_TRACING=false
LANGSMITH_API_KEY=
LANGSMITH_PROJECT=developer-intelligence-local
VECTOR_STORE_BACKEND=memory
CHECKPOINT_BACKEND=memory
```

业务节点只依赖 `ModelFactory`、Retriever 和 Tool 接口，不散落 Provider 判断。

模型能力在启动阶段校验：Tool Calling、Structured Output、Streaming 和 Vision 等能力不支持时，必须启动失败或进入明确降级流程。

## 11. Mock / Real 双模式

### Mock 模式

默认 `APP_MODE=mock`，不需要 API Key，使用：

- `MockChatModel`；
- `MockEmbeddingModel`；
- `FakeRetriever`；
- `FakeToolExecutor`；
- `InMemory` Checkpointer；
- No-op 或本地 Tracer。

必须覆盖正常回答、工具调用、工具失败、空召回、查询重写、循环上限、人工中断、结构化输出异常和超时场景。

### Real 模式

只替换基础设施，不修改 Graph：

```text
Mock Model → Real ChatModel
Fake Retriever → Vector Retriever
Fake Tool → Read-only Tool Adapter
Memory Saver → PostgreSQL Checkpointer
Noop Tracer → LangSmith Tracer
```

## 12. LangSmith 与数据安全

LangSmith 默认关闭：

```env
LANGSMITH_TRACING=false
```

支持 `OFF`、`SAFE`、`FULL` 三种记录级别，生产默认 `SAFE`。Trace 需要脱敏邮箱、手机号、Token、数据库地址、代码密钥等敏感字段。

禁止：

- API Key 写入代码、Prompt、日志、Trace 或测试数据集；
- 真实用户数据直接进入公开 Dataset；
- 将完整数据库连接串发送到前端；
- LangSmith 失败时泄露原始输入。

## 13. 评测与质量门禁

数据集：

```text
datasets/
├── knowledge_qa.jsonl
├── agent_trajectory.jsonl
└── safety_boundary.jsonl
```

指标：

- RAG：Context Recall、Context Precision、Answer Relevance、Faithfulness、Citation Completeness；
- Agent：Intent Accuracy、Tool Selection Accuracy、Tool Argument Accuracy、Trajectory Accuracy、Max-Step Rate；
- 安全：Prompt Injection Block Rate、Unauthorized Tool Block Rate、Sensitive Data Leakage Rate；
- 工程：P50 / P95 Latency、Token Usage、Cost、Error Rate、Retry Rate、Checkpoint Resume Success Rate。

初始质量门禁：

```text
结构化输出成功率 >= 98%
Mock Agent 完成率 >= 95%
工具参数校验通过率 >= 98%
安全拒绝准确率 >= 95%
RAG 引用完整率 >= 90%
未处理异常率 <= 2%
```

真实模型指标先建立 baseline，再根据数据调整，不预先虚构结果。

## 14. 测试矩阵

| 层级 | 模式 | 目标 |
|---|---|---|
| 单元测试 | Mock | Settings、节点、路由、Schema |
| Graph 测试 | Mock | 分支、循环、终止、Checkpoint |
| API 测试 | Mock | FastAPI HTTP 契约 |
| SSE 测试 | Mock | 事件顺序与断开处理 |
| Java Client 测试 | Mock Server | REST / SSE / 错误转换 |
| 集成测试 | Real | PostgreSQL、Vector Store |
| Evaluation | Real | LangSmith 数据集与实验 |
| 安全测试 | Mock + 可选 Real | 拒答、脱敏、工具权限 |

默认 `pytest` 不调用真实模型；真实模型和 LangSmith 评测必须显式标记执行。

## 15. 实施阶段

1. **Phase 0：版本与规格**：固化目录、版本、API 风格、安全约束；
2. **Phase 1：项目骨架与配置**：Python Settings、Mock / Real、健康检查、Java 骨架；
3. **Phase 2：LangChain 基础闭环**：Model、Structured Output、Tool、Agent、Streaming；
4. **Phase 3：RAG / Agentic RAG**：Loader、Chunker、Embedding、Retriever、Grader、Rewrite、Citation；
5. **Phase 4：LangGraph 工程化**：State、Routing、Loop、Checkpoint、Memory、Interrupt、Retry；
6. **Phase 5：LangSmith**：Tracing、脱敏、Dataset、Evaluation、Regression Gate；
7. **Phase 6：Java 集成与容器化**：REST、SSE、错误转换、Docker Compose；
8. **Phase 7：文档与面试收口**：索引、图表、面试题、STAR 项目说辞、简历表达。

## 16. 完成定义

### 文档

- 新文档有目录入口；
- 使用相对链接；
- 外部资料带来源；
- 旧 / 现代 API 边界明确；
- 文档代码与项目代码一致。

### Python

- Mock 模式可启动并通过测试；
- Real 模式配置明确；
- Graph 可运行、可恢复、有循环上限；
- 高风险工具需要审批；
- API 有统一错误格式。

### Java

- Java 17 编译通过；
- REST、SSE、超时和错误转换有测试；
- 无硬编码密钥。

### AI 质量

- 有 Golden Dataset、RAG 评测、轨迹评测和安全用例；
- Trace 支持脱敏；
- 评测可生成报告并执行指标门禁。

### 工程验证

- Python 单元 / API / Graph 测试；
- Java 单元 / Mock Server 测试；
- Docker Compose 启动检查；
- Markdown 链接、代码和 Git diff 检查。

## 17. 参考资料

- [LangChain Python Quickstart](https://docs.langchain.com/oss/python/langchain/quickstart)
- [LangChain v1 Migration](https://docs.langchain.com/oss/python/migrate/langchain-v1)
- [LangChain Models](https://docs.langchain.com/oss/python/langchain/models)
- [LangChain Agents](https://docs.langchain.com/oss/python/langchain/agents)
- [LangGraph Overview](https://docs.langchain.com/oss/python/langgraph/overview)
- [LangGraph Persistence](https://docs.langchain.com/oss/python/langgraph/persistence)
- [LangGraph Agentic RAG](https://docs.langchain.com/oss/python/langgraph/agentic-rag)
- [LangGraph Human-in-the-loop](https://docs.langchain.com/oss/python/langchain/human-in-the-loop)
- [LangSmith Observability](https://docs.langchain.com/langsmith/observability)
- [LangSmith Evaluation](https://docs.langchain.com/langsmith/evaluation)
- [LangSmith Sensitive Data](https://docs.langchain.com/langsmith/mask-inputs-outputs)
- [Spring Boot REST Clients](https://docs.spring.io/spring-boot/reference/io/rest-client.html)
- [Spring Boot Externalized Configuration](https://docs.spring.io/spring-boot/reference/features/external-config.html)
