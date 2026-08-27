# LangChain Agent Platform Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在现有知识库中增量建设一套 LangChain v1 + LangGraph + LangSmith 求职实战体系，并交付可测试的 Python AI Service、Java Spring Boot Gateway 和完整教程文档。

**Architecture:** Python FastAPI AI Service 负责 LangChain v1、RAG、LangGraph Workflow、Checkpoint、HITL 和 LangSmith；Java 17 + Spring Boot 3 Gateway 负责鉴权边界、请求校验、REST/SSE 转发和统一异常。业务层只依赖 ModelFactory、Retriever、Tool 和稳定 HTTP API，默认使用 Mock 依赖，配置真实 Provider 后切换 Real 模式。

**Tech Stack:** Python 3.10+, FastAPI, Pydantic 2, pydantic-settings, LangChain v1, LangGraph v1, LangSmith, pytest, PostgreSQL, Redis, Vector Store, Java 17+, Spring Boot 3.x, WebClient, Docker Compose, Mermaid。

**Spec:** `docs/superpowers/specs/2026-08-26-langchain-agent-platform-design.md`

## Global Constraints

- 保留现有 `2-learning/stacks/14-langchain/` 教程，不删除旧 API 内容；新增现代主线并显式说明迁移边界。
- Python 运行时最低版本为 3.10；Java 运行时最低版本为 17。
- Python 默认 `APP_MODE=mock`，默认测试不调用真实模型、真实向量库或 LangSmith。
- 真实模型、Embedding、Vector Store、Checkpoint 和 LangSmith 均只能通过环境变量或配置对象注入。
- 不提交 `.env`、API Key、数据库密码、真实用户数据、真实 GitHub 写权限或生产连接串。
- 业务节点不得散落 Provider 判断；统一通过 `ModelFactory`、`EmbeddingFactory`、`Retriever` 和 Tool 接口获取依赖。
- Agent 必须设置 `MAX_REWRITE_COUNT=2`、`MAX_TOOL_CALL_COUNT=5`、`MAX_TOTAL_STEPS=12`，达到上限后安全停止。
- 高风险工具 `execute_sql`、`delete_cache`、`modify_code`、`deploy_service` 只实现 Mock / 人工确认路径，不执行真实副作用。
- State 不保存 API Key、完整敏感原文或无法序列化的运行时对象。
- LangSmith 默认关闭；启用 Trace 时至少对邮箱、手机号、Token、数据库地址和代码密钥脱敏。
- 每个任务先写失败测试，再写最小实现；每个任务结束后执行定向测试和 `git diff --check`，并单独提交。
- Markdown 使用 UTF-8、LF、中英文间保留空格、代码块标注语言、链接使用相对路径。
- Mermaid 图表使用 `default` 主题、项目统一字体回退链和语义色板；单图节点不超过 25 个、连线不超过 30 条、源码不超过 80 行。

---

## 文件地图

### 新增 Python 项目

- `2-learning/projects/langchain-agent-platform/pyproject.toml`：依赖、测试和包配置。
- `2-learning/projects/langchain-agent-platform/.env.example`：无秘密的完整配置模板。
- `2-learning/projects/langchain-agent-platform/README.md`：启动、架构、模式切换、API 和面试入口。
- `2-learning/projects/langchain-agent-platform/src/ai_platform/config/settings.py`：集中配置和启动校验。
- `2-learning/projects/langchain-agent-platform/src/ai_platform/llm/model_factory.py`：ChatModel / Embedding 工厂和能力检查。
- `2-learning/projects/langchain-agent-platform/src/ai_platform/llm/mock_models.py`：确定性 Mock ChatModel 和 Embedding。
- `2-learning/projects/langchain-agent-platform/src/ai_platform/api/schemas.py`：HTTP 请求、响应、错误和流式事件模型。
- `2-learning/projects/langchain-agent-platform/src/ai_platform/api/app.py`：FastAPI 应用装配和依赖注入。
- `2-learning/projects/langchain-agent-platform/src/ai_platform/api/routes_health.py`：健康检查。
- `2-learning/projects/langchain-agent-platform/src/ai_platform/api/routes_chat.py`：同步问答与 Agent 流式接口。
- `2-learning/projects/langchain-agent-platform/src/ai_platform/tools/definitions.py`：只读和高风险工具定义。
- `2-learning/projects/langchain-agent-platform/src/ai_platform/tools/executor.py`：工具校验、审计和 Mock 执行。
- `2-learning/projects/langchain-agent-platform/src/ai_platform/rag/documents.py`：示例知识文档和文档加载。
- `2-learning/projects/langchain-agent-platform/src/ai_platform/rag/retriever.py`：Retriever 协议、FakeRetriever 和 InMemory Retriever。
- `2-learning/projects/langchain-agent-platform/src/ai_platform/rag/pipeline.py`：召回、评分、重写、引用和空召回策略。
- `2-learning/projects/langchain-agent-platform/src/ai_platform/graph/state.py`：Typed State、Intent 和限制常量。
- `2-learning/projects/langchain-agent-platform/src/ai_platform/graph/nodes.py`：Graph 节点函数。
- `2-learning/projects/langchain-agent-platform/src/ai_platform/graph/routing.py`：条件路由和终止判断。
- `2-learning/projects/langchain-agent-platform/src/ai_platform/graph/workflow.py`：StateGraph、Checkpoint 和中断装配。
- `2-learning/projects/langchain-agent-platform/src/ai_platform/observability/redaction.py`：Trace 脱敏。
- `2-learning/projects/langchain-agent-platform/src/ai_platform/observability/tracing.py`：LangSmith 可选配置和运行元数据。
- `2-learning/projects/langchain-agent-platform/src/ai_platform/evaluation/datasets.py`：本地 Golden Dataset 加载。
- `2-learning/projects/langchain-agent-platform/src/ai_platform/evaluation/evaluators.py`：RAG、工具轨迹和安全指标。
- `2-learning/projects/langchain-agent-platform/src/ai_platform/evaluation/regression.py`：Mock 回归门禁和 LangSmith Evaluation 入口。
- `2-learning/projects/langchain-agent-platform/tests/unit/`：设置、模型、工具、RAG、脱敏和评测单元测试。
- `2-learning/projects/langchain-agent-platform/tests/integration/`：Graph、API、SSE 和 Checkpoint 集成测试。
- `2-learning/projects/langchain-agent-platform/datasets/`：脱敏、公开、确定性的 JSONL 数据集。
- `2-learning/projects/langchain-agent-platform/docs/diagrams/`：架构、流程、状态、SSE 和评测 Mermaid 源文件。

### 新增 Java Gateway

- `2-learning/projects/langchain-agent-platform/java-gateway/pom.xml`：Spring Boot WebFlux、Validation、Actuator 和测试依赖。
- `2-learning/projects/langchain-agent-platform/java-gateway/src/main/java/com/example/aiplatform/AiPlatformApplication.java`：启动类。
- `.../config/AiServiceProperties.java`：AI 服务地址和超时配置。
- `.../client/AiServiceClient.java`：同步 HTTP 客户端。
- `.../client/AiStreamClient.java`：SSE 客户端。
- `.../service/AiConversationService.java`：业务编排和 request ID 透传。
- `.../controller/ChatController.java`：同步问答和 SSE 接口。
- `.../exception/GlobalExceptionHandler.java`：统一错误映射。
- `.../src/main/resources/application.yml`：外部化配置默认值。
- `.../src/test/java/...`：Controller、客户端、超时和 SSE 测试。

### 现代教程与索引

- `2-learning/stacks/14-langchain/README.md`：增加现代主线和旗舰项目导航。
- `2-learning/stacks/14-langchain/01-basics/01-model-and-provider-abstraction.md`：模型与 Provider 解耦。
- `2-learning/stacks/14-langchain/01-basics/02-prompt-and-context-engineering.md`：Prompt / Context Engineering。
- `2-learning/stacks/14-langchain/01-basics/03-structured-output.md`：结构化输出。
- `2-learning/stacks/14-langchain/01-basics/04-streaming.md`：流式输出。
- `2-learning/stacks/14-langchain/02-core/01-tool-calling.md`：工具调用。
- `2-learning/stacks/14-langchain/02-core/02-agent-basics.md`：Agent 基础。
- `2-learning/stacks/14-langchain/02-core/03-retrieval-and-rag.md`：RAG。
- `2-learning/stacks/14-langchain/02-core/04-hybrid-retrieval-and-rerank.md`：混合检索与重排。
- `2-learning/stacks/14-langchain/02-core/05-context-engineering.md`：上下文工程。
- `2-learning/stacks/14-langchain/03-advanced/01-langgraph-state-and-routing.md`：StateGraph 和路由。
- `2-learning/stacks/14-langchain/03-advanced/02-checkpoint-memory-and-store.md`：Checkpoint、Memory、Store。
- `2-learning/stacks/14-langchain/03-advanced/03-human-in-the-loop.md`：HITL。
- `2-learning/stacks/14-langchain/03-advanced/04-agentic-rag.md`：Agentic RAG。
- `2-learning/stacks/14-langchain/03-advanced/05-langsmith-tracing.md`：Tracing 和脱敏。
- `2-learning/stacks/14-langchain/03-advanced/06-langsmith-evaluation.md`：Dataset、Evaluation 和回归。
- `2-learning/stacks/14-langchain/04-projects/01-ai-developer-copilot.md`：子项目一。
- `2-learning/stacks/14-langchain/04-projects/02-enterprise-knowledge-assistant.md`：子项目二。
- `2-learning/stacks/14-langchain/04-projects/03-agentic-rag.md`：子项目三。
- `2-learning/stacks/14-langchain/04-projects/04-enterprise-developer-platform.md`：旗舰项目。
- `2-learning/stacks/14-langchain/05-interview/resume-project-story.md`：STAR 项目说辞和简历表达。
- `2-learning/projects/README.md`、`2-learning/README-learning.md`、`1-knowledge/03-ai/README.md`：入口索引。

---

## Task 1: 建立 Python 项目骨架与集中配置

**Depends on:** None

**Files:**
- Create: `2-learning/projects/langchain-agent-platform/pyproject.toml`
- Create: `2-learning/projects/langchain-agent-platform/.env.example`
- Create: `2-learning/projects/langchain-agent-platform/src/ai_platform/__init__.py`
- Create: `2-learning/projects/langchain-agent-platform/src/ai_platform/config/__init__.py`
- Create: `2-learning/projects/langchain-agent-platform/src/ai_platform/config/settings.py`
- Create: `2-learning/projects/langchain-agent-platform/src/ai_platform/api/__init__.py`
- Create: `2-learning/projects/langchain-agent-platform/tests/unit/test_settings.py`

**Interfaces:**
- Produces `Settings` with fields `app_env`, `app_mode`, `chat_model_provider`, `chat_model_name`, `chat_model_base_url`, `chat_model_api_key`, `embedding_provider`, `embedding_model_name`, `embedding_base_url`, `embedding_api_key`, `langsmith_tracing`, `langsmith_api_key`, `langsmith_project`, `vector_store_backend`, `checkpoint_backend`, `max_rewrite_count`, `max_tool_call_count`, `max_total_steps`.
- Produces `get_settings() -> Settings` with an `lru_cache` so application code receives one immutable configuration object per process.
- `APP_MODE` accepts only `mock` or `real`; defaults to `mock`.
- Real mode requires `CHAT_MODEL_PROVIDER` and `CHAT_MODEL_NAME`; Mock mode does not require API keys.

- [ ] **Step 1: Write the failing configuration tests**

```python
# tests/unit/test_settings.py
import pytest
from pydantic import ValidationError

from ai_platform.config.settings import Settings


def test_mock_mode_has_safe_defaults(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.delenv("APP_MODE", raising=False)
    settings = Settings()

    assert settings.app_mode == "mock"
    assert settings.vector_store_backend == "memory"
    assert settings.checkpoint_backend == "memory"
    assert settings.max_rewrite_count == 2
    assert settings.max_tool_call_count == 5
    assert settings.max_total_steps == 12


def test_invalid_mode_is_rejected() -> None:
    with pytest.raises(ValidationError):
        Settings(app_mode="production")


def test_real_mode_requires_chat_model_configuration() -> None:
    with pytest.raises(ValueError, match="CHAT_MODEL_NAME"):
        Settings(app_mode="real", chat_model_provider="", chat_model_name="")


def test_langsmith_requires_key_when_enabled() -> None:
    with pytest.raises(ValueError, match="LANGSMITH_API_KEY"):
        Settings(langsmith_tracing=True, langsmith_api_key="")
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd 2-learning/projects/langchain-agent-platform && python -m pytest tests/unit/test_settings.py -q`

Expected: FAIL with `ModuleNotFoundError: No module named 'ai_platform'` or missing `Settings`.

- [ ] **Step 3: Add package configuration and Settings implementation**

`pyproject.toml` must include:

```toml
[build-system]
requires = ["setuptools>=68"]
build-backend = "setuptools.build_meta"

[project]
name = "langchain-agent-platform"
version = "0.1.0"
requires-python = ">=3.10"
dependencies = [
  "fastapi>=0.115",
  "uvicorn[standard]>=0.30",
  "pydantic>=2.8",
  "pydantic-settings>=2.4",
  "langchain>=1.0",
  "langgraph>=1.0",
  "langsmith>=0.4",
]

[project.optional-dependencies]
real = [
  "langchain-openai>=1.0",
  "langchain-anthropic>=1.0",
  "langchain-postgres>=0.0.15",
  "langgraph-checkpoint-postgres>=2.0",
  "psycopg[binary]>=3.2",
]
test = [
  "pytest>=8.3",
  "pytest-asyncio>=0.24",
  "httpx>=0.27",
  "pyyaml>=6.0",
]

[tool.pytest.ini_options]
pythonpath = ["src"]
testpaths = ["tests"]
markers = [
  "real_model: requires configured model credentials",
  "langsmith: requires LangSmith credentials",
]

[tool.setuptools.packages.find]
where = ["src"]
```

`settings.py` must define a `Settings(BaseSettings)` with `SettingsConfigDict(env_file=(".env",), extra="ignore")`, use `Literal["mock", "real"]` for `app_mode`, and validate real mode and LangSmith requirements in a `@model_validator(mode="after")`.

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd 2-learning/projects/langchain-agent-platform && python -m pytest tests/unit/test_settings.py -q`

Expected: PASS with four tests.

- [ ] **Step 5: Add the environment template and commit**

`.env.example` must contain concrete, empty-secret-safe values:

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
MAX_REWRITE_COUNT=2
MAX_TOOL_CALL_COUNT=5
MAX_TOTAL_STEPS=12
```

Run: `git add 2-learning/projects/langchain-agent-platform && git diff --check --cached && git commit -m "feat(ai): scaffold agent platform configuration"`

Expected: a commit containing only Task 1 files and no secret values.

---

## Task 2: Implement Provider-neutral ModelFactory and deterministic Mock models

**Depends on:** Task 1

**Files:**
- Create: `2-learning/projects/langchain-agent-platform/src/ai_platform/llm/__init__.py`
- Create: `2-learning/projects/langchain-agent-platform/src/ai_platform/llm/model_factory.py`
- Create: `2-learning/projects/langchain-agent-platform/src/ai_platform/llm/mock_models.py`
- Create: `2-learning/projects/langchain-agent-platform/tests/unit/test_model_factory.py`

**Interfaces:**
- `ModelCapabilities` is a Pydantic model with booleans `tool_calling`, `structured_output`, `streaming`, `vision`.
- `ModelFactory(settings: Settings)` exposes `create_chat_model()`, `create_embedding_model()`, `capabilities()`, and `validate_for_workflow() -> None`.
- `create_chat_model()` returns a LangChain chat-compatible runnable. In Mock mode it returns `MockChatModel`; in Real mode it calls `init_chat_model` using the configured provider/model and optional `base_url` / API key.
- `MockChatModel` supports `invoke`, `ainvoke`, `stream`, `astream`, and deterministic responses for normal, retrieval, tool, and structured-output prompts.
- No route, node, or tool file imports a provider-specific class directly.

- [ ] **Step 1: Write failing factory and Mock tests**

```python
# tests/unit/test_model_factory.py
import pytest
from langchain_core.messages import HumanMessage

from ai_platform.config.settings import Settings
from ai_platform.llm.model_factory import ModelFactory


def test_mock_factory_returns_deterministic_answer() -> None:
    factory = ModelFactory(Settings(app_mode="mock"))
    response = factory.create_chat_model().invoke(
        [HumanMessage(content="Explain Redis cache hit rate")]
    )

    assert response.content == "Mock answer: Redis cache hit rate should be monitored."


def test_mock_factory_reports_required_capabilities() -> None:
    factory = ModelFactory(Settings(app_mode="mock"))

    assert factory.capabilities().tool_calling is True
    assert factory.capabilities().structured_output is True
    assert factory.capabilities().streaming is True


def test_real_factory_does_not_require_provider_import_in_mock_mode() -> None:
    factory = ModelFactory(Settings(app_mode="mock", chat_model_provider=""))

    assert factory.create_chat_model() is not None


def test_factory_rejects_workflow_when_capability_is_missing(monkeypatch: pytest.MonkeyPatch) -> None:
    factory = ModelFactory(Settings(app_mode="mock"))
    original_capabilities = factory.capabilities
    monkeypatch.setattr(
        factory,
        "capabilities",
        lambda: original_capabilities().model_copy(update={"structured_output": False}),
    )

    with pytest.raises(ValueError, match="structured_output"):
        factory.validate_for_workflow()
```

- [ ] **Step 2: Run the focused tests and confirm failure**

Run: `cd 2-learning/projects/langchain-agent-platform && python -m pytest tests/unit/test_model_factory.py -q`

Expected: FAIL because `ModelFactory` and `MockChatModel` do not exist.

- [ ] **Step 3: Implement MockChatModel and ModelFactory**

Use `langchain_core.messages.AIMessage`, `langchain_core.outputs.ChatGeneration`, `ChatResult`, and `langchain.chat_models.init_chat_model`. Keep mock responses stable and branch only on normalized user text:

```text
包含 “Redis”       → Mock answer: Redis cache hit rate should be monitored.
包含 “SQL”          → Mock answer: Use EXPLAIN before changing the query.
包含 “检索”/“文档”   → Mock answer: Retrieved context is required for this answer.
其他               → Mock answer: The developer assistant is ready.
```

For Real mode, construct the model from a provider-qualified string such as `openai:gpt-4o-mini`, `anthropic:claude-sonnet-4-6`, or `openrouter:provider/model`, pass only non-empty optional settings, and raise `ValueError` with the configuration field name when a provider is unsupported. Do not pass `temperature` by default; keep model parameters provider-neutral.

`validate_for_workflow()` must require `tool_calling`, `structured_output`, and `streaming`, and include the failed capability names in the exception.

- [ ] **Step 4: Run tests and compile the package**

Run: `cd 2-learning/projects/langchain-agent-platform && python -m pytest tests/unit/test_model_factory.py -q && python -m compileall -q src`

Expected: all model tests PASS and `compileall` exits 0.

- [ ] **Step 5: Commit the model abstraction**

Run: `git add 2-learning/projects/langchain-agent-platform/src/ai_platform/llm 2-learning/projects/langchain-agent-platform/tests/unit/test_model_factory.py && git diff --check --cached && git commit -m "feat(ai): add provider-neutral model factory"`

---

## Task 3: Add Structured Output, tools, tool audit, and FastAPI health/chat contracts

**Depends on:** Task 2

**Files:**
- Create: `2-learning/projects/langchain-agent-platform/src/ai_platform/api/schemas.py`
- Create: `2-learning/projects/langchain-agent-platform/src/ai_platform/api/app.py`
- Create: `2-learning/projects/langchain-agent-platform/src/ai_platform/api/routes_health.py`
- Create: `2-learning/projects/langchain-agent-platform/src/ai_platform/api/routes_chat.py`
- Create: `2-learning/projects/langchain-agent-platform/src/ai_platform/tools/definitions.py`
- Create: `2-learning/projects/langchain-agent-platform/src/ai_platform/tools/executor.py`
- Create: `2-learning/projects/langchain-agent-platform/tests/unit/test_tools.py`
- Create: `2-learning/projects/langchain-agent-platform/tests/integration/test_api.py`

**Interfaces:**
- `ChatRequest(question: str, session_id: str | None, user_id: str | None)` validates non-empty questions up to 4,000 characters.
- `Citation(title: str, source: str, score: float | None)` and `ChatResponse(request_id, session_id, answer, citations, confidence, trace_id)` are stable HTTP DTOs.
- `StreamEvent(type: Literal["run_started", "node_started", "retrieval", "token", "citation", "run_completed", "error"], data: dict)` is the SSE event DTO.
- `@tool` functions `search_documentation(query: str)`, `analyze_sql(query: str)`, `analyze_redis(command: str)`, and `execute_sql(query: str)` exist; the first three are read-only, the last is high-risk Mock only.
- `ToolExecutor.execute(name: str, arguments: dict) -> ToolExecutionResult` validates arguments, records duration and error code, and never executes a real write.
- `create_app(settings: Settings | None = None) -> FastAPI` exposes `GET /api/v1/health` and `POST /api/v1/chat`.

- [ ] **Step 1: Write failing tool and API tests**

```python
# tests/unit/test_tools.py
from ai_platform.tools.executor import ToolExecutor


def test_sql_analysis_is_read_only_and_audited() -> None:
    result = ToolExecutor().execute("analyze_sql", {"query": "SELECT * FROM orders"})

    assert result.ok is True
    assert result.error_code is None
    assert result.duration_ms >= 0
    assert "EXPLAIN" in result.result


def test_high_risk_sql_execution_requires_approval() -> None:
    result = ToolExecutor().execute("execute_sql", {"query": "DELETE FROM orders"})

    assert result.ok is False
    assert result.error_code == "HUMAN_APPROVAL_REQUIRED"
```

```python
# tests/integration/test_api.py
from fastapi.testclient import TestClient

from ai_platform.api.app import create_app
from ai_platform.config.settings import Settings


def test_health_endpoint_works_without_credentials() -> None:
    client = TestClient(create_app(Settings(app_mode="mock")))

    response = client.get("/api/v1/health")

    assert response.status_code == 200
    assert response.json()["status"] == "UP"
    assert response.json()["mode"] == "mock"


def test_chat_returns_stable_structured_response() -> None:
    client = TestClient(create_app(Settings(app_mode="mock")))

    response = client.post(
        "/api/v1/chat",
        json={"question": "如何排查 Redis 慢查询？", "session_id": "session-001"},
    )

    assert response.status_code == 200
    body = response.json()
    assert body["session_id"] == "session-001"
    assert body["answer"]
    assert 0 <= body["confidence"] <= 1
```

- [ ] **Step 2: Run tests to confirm failure**

Run: `cd 2-learning/projects/langchain-agent-platform && python -m pytest tests/unit/test_tools.py tests/integration/test_api.py -q`

Expected: FAIL because DTOs, tools, executor, and FastAPI app are absent.

- [ ] **Step 3: Implement DTOs, tools, executor, and minimal API**

`ToolExecutionResult` must contain `ok`, `result`, `error_code`, `duration_ms`, and `tool_name`. The executor must dispatch from a fixed dictionary, reject unknown names with `TOOL_NOT_FOUND`, convert Pydantic validation errors to `TOOL_VALIDATION_FAILED`, and return `HUMAN_APPROVAL_REQUIRED` for `execute_sql`, `delete_cache`, `modify_code`, and `deploy_service`.

`routes_chat.py` must generate a request ID with `uuid4`, invoke the Mock model through `ModelFactory`, return `ChatResponse`, and leave `trace_id` as `None` until Task 7 enables tracing. No route may log the request body verbatim.

- [ ] **Step 4: Run focused tests and API contract checks**

Run: `cd 2-learning/projects/langchain-agent-platform && python -m pytest tests/unit/test_tools.py tests/integration/test_api.py -q`

Expected: all tests PASS.

- [ ] **Step 5: Commit the API and tool boundary**

Run: `git add 2-learning/projects/langchain-agent-platform/src/ai_platform/api 2-learning/projects/langchain-agent-platform/src/ai_platform/tools 2-learning/projects/langchain-agent-platform/tests && git diff --check --cached && git commit -m "feat(ai): add tool audit and FastAPI contracts"`

---

## Task 4: Implement the RAG pipeline with empty-recall and citation guarantees

**Depends on:** Task 3

**Files:**
- Create: `2-learning/projects/langchain-agent-platform/src/ai_platform/rag/__init__.py`
- Create: `2-learning/projects/langchain-agent-platform/src/ai_platform/rag/documents.py`
- Create: `2-learning/projects/langchain-agent-platform/src/ai_platform/rag/retriever.py`
- Create: `2-learning/projects/langchain-agent-platform/src/ai_platform/rag/pipeline.py`
- Create: `2-learning/projects/langchain-agent-platform/knowledge/java/cache-policy.md`
- Create: `2-learning/projects/langchain-agent-platform/knowledge/redis/slow-query.md`
- Create: `2-learning/projects/langchain-agent-platform/knowledge/mysql/explain-guide.md`
- Create: `2-learning/projects/langchain-agent-platform/tests/unit/test_rag_pipeline.py`
- Create: `2-learning/projects/langchain-agent-platform/tests/unit/test_retriever.py`

**Interfaces:**
- `KnowledgeDocument(id: str, title: str, source: str, content: str, metadata: dict[str, str])` is serializable.
- `Retriever` protocol exposes `search(query: str, top_k: int = 4) -> list[KnowledgeDocument]`.
- `FakeRetriever` provides deterministic fixture-based search and an explicit empty-result mode for tests.
- `RagResult(answer: str, documents: list[KnowledgeDocument], citations: list[Citation], rewritten_query: str | None, rewrite_count: int, grounded: bool)` is returned by `RagPipeline.answer(question, max_rewrites=2)`.
- A zero-document result must return a grounded refusal, not a fabricated technical answer.
- Citation source paths are relative to the project root and never contain API credentials.

- [ ] **Step 1: Write failing RAG tests**

```python
# tests/unit/test_rag_pipeline.py
from ai_platform.rag.pipeline import RagPipeline
from ai_platform.rag.retriever import FakeRetriever


def test_relevant_context_produces_citations() -> None:
    pipeline = RagPipeline(FakeRetriever())

    result = pipeline.answer("Redis 慢查询如何排查？")

    assert result.grounded is True
    assert result.citations[0].source == "knowledge/redis/slow-query.md"
    assert "Redis" in result.answer


def test_empty_recall_refuses_without_fabrication() -> None:
    pipeline = RagPipeline(FakeRetriever(always_empty=True))

    result = pipeline.answer("如何配置不存在的中间件？")

    assert result.grounded is False
    assert result.citations == []
    assert "没有找到足够的知识库依据" in result.answer


def test_rewrite_is_bounded() -> None:
    retriever = FakeRetriever(always_empty=True)
    result = RagPipeline(retriever).answer("无效查询", max_rewrites=2)

    assert result.rewrite_count == 2
```

- [ ] **Step 2: Run tests and confirm failure**

Run: `cd 2-learning/projects/langchain-agent-platform && python -m pytest tests/unit/test_rag_pipeline.py -q`

Expected: FAIL because RAG modules and fixture documents are absent.

- [ ] **Step 3: Add fixture documents and Retriever**

Each knowledge file must contain a title, a short operational explanation, a safe read-only example, and a source path. Do not include real credentials or private URLs.

`FakeRetriever.search()` must normalize query text, rank documents by deterministic keyword overlap, return at most `top_k`, and return `[]` when `always_empty=True`. Add an optional `InMemoryVectorStoreRetriever` adapter that receives an injected embedding function but is not constructed during Mock tests.

- [ ] **Step 4: Implement RagPipeline**

The pipeline must:

1. Search the original question;
2. If results exist, build a grounded answer from the selected fixture content and produce one `Citation` per selected source;
3. If no results exist and `rewrite_count < max_rewrites`, generate a deterministic rewritten query and search again;
4. If no results remain, return the exact refusal prefix `没有找到足够的知识库依据，无法可靠回答该问题。`;
5. Never produce a citation when no document was retrieved.

Keep reranking behind a `Reranker` protocol with a deterministic `KeywordReranker` implementation; do not add a remote reranker dependency in this task.

- [ ] **Step 5: Run tests and commit**

Run: `cd 2-learning/projects/langchain-agent-platform && python -m pytest tests/unit/test_retriever.py tests/unit/test_rag_pipeline.py -q && git diff --check`

Expected: all RAG tests PASS and no whitespace errors.

Run: `git add 2-learning/projects/langchain-agent-platform/src/ai_platform/rag 2-learning/projects/langchain-agent-platform/knowledge 2-learning/projects/langchain-agent-platform/tests/unit/test_rag_pipeline.py 2-learning/projects/langchain-agent-platform/tests/unit/test_retriever.py && git commit -m "feat(ai): add grounded rag pipeline"`

---

## Task 5: Build LangGraph StateGraph, routing, bounded loops, Checkpoint, and HITL

**Depends on:** Tasks 2–4

**Files:**
- Create: `2-learning/projects/langchain-agent-platform/src/ai_platform/graph/__init__.py`
- Create: `2-learning/projects/langchain-agent-platform/src/ai_platform/graph/state.py`
- Create: `2-learning/projects/langchain-agent-platform/src/ai_platform/graph/nodes.py`
- Create: `2-learning/projects/langchain-agent-platform/src/ai_platform/graph/routing.py`
- Create: `2-learning/projects/langchain-agent-platform/src/ai_platform/graph/workflow.py`
- Create: `2-learning/projects/langchain-agent-platform/tests/integration/test_graph_workflow.py`
- Create: `2-learning/projects/langchain-agent-platform/tests/integration/test_graph_interrupt.py`

**Interfaces:**
- `Intent` enum contains `GENERAL_QA`, `KNOWLEDGE_SEARCH`, `CODE_ANALYSIS`, `SQL_DIAGNOSIS`, `REDIS_DIAGNOSIS`, `COMPLEX_TASK`, `UNSUPPORTED`.
- `DeveloperAgentState` is a `TypedDict` containing request/session/user context, question, intent, messages, retrieved documents, tool results, answer, citations, confidence, counters, HITL fields, and error fields from the spec.
- `build_workflow(settings: Settings, checkpointer: BaseCheckpointSaver | None = None)` returns a compiled graph.
- `run_workflow(question: str, session_id: str, user_id: str | None = None)` returns the final state or interrupt information.
- `resume_workflow(session_id: str, decision: Literal["approve", "reject"])` resumes a paused high-risk action.
- Graph topology includes `input_guard`, `classify_intent`, `route_request`, `retrieve_documents`, `grade_documents`, `rewrite_query`, `generate_answer`, `validate_output`, `human_review`, and `finalize`.

- [ ] **Step 1: Write failing Graph tests**

```python
# tests/integration/test_graph_workflow.py
from ai_platform.config.settings import Settings
from ai_platform.graph.workflow import build_workflow


def test_knowledge_question_reaches_grounded_final_state() -> None:
    graph = build_workflow(Settings(app_mode="mock"))
    result = graph.invoke(
        {
            "request_id": "req-001",
            "session_id": "session-001",
            "user_id": "user-001",
            "question": "Redis 慢查询如何排查？",
            "messages": [],
            "retry_count": 0,
            "rewrite_count": 0,
            "tool_call_count": 0,
        },
        {"configurable": {"thread_id": "session-001"}},
    )

    assert result["answer"]
    assert result["citations"]
    assert result["error_code"] is None


def test_rewrite_loop_never_exceeds_two_rewrites() -> None:
    graph = build_workflow(Settings(app_mode="mock"))
    result = graph.invoke(
        {
            "request_id": "req-002",
            "session_id": "session-002",
            "question": "不存在的技术主题",
            "messages": [],
            "retry_count": 0,
            "rewrite_count": 0,
            "tool_call_count": 0,
        },
        {"configurable": {"thread_id": "session-002"}},
    )

    assert result["rewrite_count"] <= 2
    assert "没有找到足够的知识库依据" in result["answer"]
```

```python
# tests/integration/test_graph_interrupt.py
from langgraph.types import Command

from ai_platform.config.settings import Settings
from ai_platform.graph.workflow import build_workflow


def test_high_risk_action_interrupts_before_execution() -> None:
    graph = build_workflow(Settings(app_mode="mock"))
    config = {"configurable": {"thread_id": "approval-001"}}
    result = graph.invoke(
        {
            "request_id": "req-003",
            "session_id": "approval-001",
            "question": "执行 SQL 删除 30 天前的订单",
            "messages": [],
            "retry_count": 0,
            "rewrite_count": 0,
            "tool_call_count": 0,
        },
        config,
    )

    assert result["__interrupt__"]

    resumed = graph.invoke(Command(resume={"decision": "reject"}), config)
    assert resumed["human_decision"] == "reject"
    assert resumed["error_code"] == "TOOL_PERMISSION_DENIED"
```

- [ ] **Step 2: Run tests and confirm failure**

Run: `cd 2-learning/projects/langchain-agent-platform && python -m pytest tests/integration/test_graph_workflow.py tests/integration/test_graph_interrupt.py -q`

Expected: FAIL because StateGraph, nodes, routes, and workflow are absent.

- [ ] **Step 3: Define State and pure routing functions**

Implement `state.py` with the exact enum and fields. Define constants from Settings instead of duplicating numeric limits in nodes. Implement pure functions:

```python
def classify_question(question: str) -> Intent: ...
def route_after_classification(state: DeveloperAgentState) -> str: ...
def route_after_grading(state: DeveloperAgentState) -> str: ...
def should_stop(state: DeveloperAgentState) -> bool: ...
```

Classification must be deterministic in Mock mode: SQL keywords route to SQL diagnosis, Redis keywords route to Redis diagnosis, code terms route to code analysis, knowledge terms route to RAG, and unknown requests route to general QA.

- [ ] **Step 4: Implement nodes and compiled graph**

Each node returns only changed state keys. `input_guard` rejects empty or overlong input. `retrieve_documents` calls `RagPipeline`. `grade_documents` checks document count and source validity. `rewrite_query` increments `rewrite_count`. `generate_answer` uses the injected model and citations. `validate_output` rejects an answer that has no grounding for a knowledge intent. `human_review` calls `interrupt()` with a serializable dictionary containing action, summary, risk level, and sanitized arguments; do not pass a Python function into `interrupt()`.

Compile with `InMemorySaver()` in Mock mode. Keep the `checkpointer` parameter injectable so Task 6 can add PostgreSQL configuration. Use `thread_id` for every invocation and never use a global mutable state.

- [ ] **Step 5: Run Graph tests and commit**

Run: `cd 2-learning/projects/langchain-agent-platform && python -m pytest tests/integration/test_graph_workflow.py tests/integration/test_graph_interrupt.py -q`

Expected: all Graph and interrupt tests PASS.

Run: `git add 2-learning/projects/langchain-agent-platform/src/ai_platform/graph 2-learning/projects/langchain-agent-platform/tests/integration/test_graph_workflow.py 2-learning/projects/langchain-agent-platform/tests/integration/test_graph_interrupt.py && git diff --check --cached && git commit -m "feat(ai): add bounded langgraph workflow"`

---

## Task 6: Integrate Graph into FastAPI, add SSE streaming, and add Checkpoint backend seam

**Depends on:** Task 5

**Files:**
- Modify: `2-learning/projects/langchain-agent-platform/src/ai_platform/api/routes_chat.py`
- Modify: `2-learning/projects/langchain-agent-platform/src/ai_platform/api/app.py`
- Modify: `2-learning/projects/langchain-agent-platform/src/ai_platform/graph/workflow.py`
- Create: `2-learning/projects/langchain-agent-platform/src/ai_platform/graph/checkpoints.py`
- Create: `2-learning/projects/langchain-agent-platform/tests/integration/test_sse_api.py`
- Create: `2-learning/projects/langchain-agent-platform/tests/unit/test_checkpoints.py`

**Interfaces:**
- `CheckpointProvider.create(settings: Settings) -> BaseCheckpointSaver` returns `InMemorySaver` for Mock / memory and an injected PostgreSQL saver for Real / postgres.
- `POST /api/v1/agent/run` accepts `ChatRequest` and returns `AgentRunResponse` with `request_id`, `session_id`, `status`, `answer`, `citations`, `confidence`, `trace_id`, and optional `approval`.
- `GET /api/v1/agent/{session_id}/stream` returns `text/event-stream` with `StreamEvent` data.
- `POST /api/v1/agent/{session_id}/resume` accepts `ResumeRequest(decision: Literal["approve", "reject"])`.
- SSE events are ordered `run_started`, one or more `node_started` / `retrieval` / `token` / `citation`, then `run_completed` or `error`.

- [ ] **Step 1: Write failing SSE and checkpoint tests**

```python
# tests/integration/test_sse_api.py
from fastapi.testclient import TestClient

from ai_platform.api.app import create_app
from ai_platform.config.settings import Settings


def test_agent_stream_emits_start_and_completion_events() -> None:
    client = TestClient(create_app(Settings(app_mode="mock")))

    with client.stream(
        "GET",
        "/api/v1/agent/session-sse-001/stream",
        params={"question": "Redis 慢查询如何排查？"},
    ) as response:
        body = "".join(response.iter_text())

    assert response.status_code == 200
    assert "event: run_started" in body
    assert "event: run_completed" in body
    assert "knowledge/redis/slow-query.md" in body
```

```python
# tests/unit/test_checkpoints.py
from langgraph.checkpoint.memory import InMemorySaver

from ai_platform.config.settings import Settings
from ai_platform.graph.checkpoints import CheckpointProvider


def test_mock_checkpoint_provider_returns_in_memory_saver() -> None:
    saver = CheckpointProvider(Settings(app_mode="mock")).create()

    assert isinstance(saver, InMemorySaver)
```

- [ ] **Step 2: Run tests and confirm failure**

Run: `cd 2-learning/projects/langchain-agent-platform && python -m pytest tests/integration/test_sse_api.py tests/unit/test_checkpoints.py -q`

Expected: FAIL because the Agent endpoints and checkpoint provider are absent.

- [ ] **Step 3: Implement CheckpointProvider and graph dependency injection**

For `memory`, return `InMemorySaver`. For `postgres`, validate `POSTGRES_DSN` at Settings level and construct the installed PostgreSQL saver only in Real mode. Do not silently fall back from a configured PostgreSQL backend to memory. If the optional PostgreSQL package is unavailable, raise an error naming `langgraph-checkpoint-postgres`.

- [ ] **Step 4: Implement Agent endpoints and SSE event adapter**

The stream adapter must run the graph with the session ID as `thread_id`, map graph progress to the stable event types, emit JSON with `json.dumps(..., ensure_ascii=False)`, and never send a raw exception string containing secrets. The resume endpoint must call `resume_workflow` with the same thread ID and return the updated state or a stable `GRAPH_RESUME_FAILED` error.

- [ ] **Step 5: Run all Python tests and commit**

Run: `cd 2-learning/projects/langchain-agent-platform && python -m pytest -q`

Expected: all tests accumulated through Task 6 PASS.

Run: `git add 2-learning/projects/langchain-agent-platform/src/ai_platform/api 2-learning/projects/langchain-agent-platform/src/ai_platform/graph 2-learning/projects/langchain-agent-platform/tests/integration/test_sse_api.py 2-learning/projects/langchain-agent-platform/tests/unit/test_checkpoints.py && git diff --check --cached && git commit -m "feat(ai): expose agent workflow over rest and sse"`

---

## Task 7: Add LangSmith tracing, redaction, datasets, evaluators, and regression gate

**Depends on:** Task 6

**Files:**
- Create: `2-learning/projects/langchain-agent-platform/src/ai_platform/observability/__init__.py`
- Create: `2-learning/projects/langchain-agent-platform/src/ai_platform/observability/redaction.py`
- Create: `2-learning/projects/langchain-agent-platform/src/ai_platform/observability/tracing.py`
- Create: `2-learning/projects/langchain-agent-platform/src/ai_platform/evaluation/__init__.py`
- Create: `2-learning/projects/langchain-agent-platform/src/ai_platform/evaluation/datasets.py`
- Create: `2-learning/projects/langchain-agent-platform/src/ai_platform/evaluation/evaluators.py`
- Create: `2-learning/projects/langchain-agent-platform/src/ai_platform/evaluation/regression.py`
- Create: `2-learning/projects/langchain-agent-platform/datasets/knowledge_qa.jsonl`
- Create: `2-learning/projects/langchain-agent-platform/datasets/agent_trajectory.jsonl`
- Create: `2-learning/projects/langchain-agent-platform/datasets/safety_boundary.jsonl`
- Create: `2-learning/projects/langchain-agent-platform/tests/unit/test_redaction.py`
- Create: `2-learning/projects/langchain-agent-platform/tests/unit/test_evaluators.py`
- Create: `2-learning/projects/langchain-agent-platform/tests/integration/test_regression_gate.py`

**Interfaces:**
- `redact_trace_payload(payload: object) -> object` recursively replaces secrets, emails, phone numbers, database URLs, and bearer tokens while preserving JSON shape.
- `TracingConfig(settings: Settings)` exposes `enabled()`, `project_name()`, `metadata(request_id, session_id, intent)`, and `sanitize(payload)`.
- Dataset records contain only `input`, `reference_output`, `expected_sources`, `expected_tools`, and `category`.
- `evaluate_rag_case(prediction: RagResult, expected_sources: list[str]) -> dict` returns `grounded`, `citation_completeness`, and `source_hit`.
- `evaluate_tool_case(actual_tools: list[str], expected_tools: list[str]) -> dict` returns precision, recall, and F1.
- `run_mock_regression() -> RegressionReport` returns structured metrics and a non-zero exit status when the gates fail.
- `run_langsmith_evaluation(dataset_name: str, agent_fn: Callable)` is the opt-in integration entry and must be marked `langsmith`.

- [ ] **Step 1: Write failing redaction and evaluator tests**

```python
# tests/unit/test_redaction.py
from ai_platform.observability.redaction import redact_trace_payload


def test_redaction_preserves_shape_and_removes_sensitive_values() -> None:
    payload = {
        "email": "alice@example.com",
        "phone": "13812345678",
        "authorization": "Bearer secret-token",
        "dsn": "postgresql://user:password@db.internal:5432/app",
        "nested": ["contact bob@example.com"],
    }

    safe = redact_trace_payload(payload)

    assert safe["email"] == "<EMAIL>"
    assert safe["phone"] == "<PHONE>"
    assert "secret-token" not in str(safe)
    assert "password" not in str(safe)
    assert safe["nested"] == ["contact <EMAIL>"]
```

```python
# tests/unit/test_evaluators.py
from ai_platform.evaluation.evaluators import evaluate_tool_case


def test_tool_evaluator_handles_expected_empty_tool_set() -> None:
    result = evaluate_tool_case([], [])

    assert result == {"precision": 1.0, "recall": 1.0, "f1": 1.0}
```

- [ ] **Step 2: Run tests and confirm failure**

Run: `cd 2-learning/projects/langchain-agent-platform && python -m pytest tests/unit/test_redaction.py tests/unit/test_evaluators.py -q`

Expected: FAIL because observability and evaluation modules are absent.

- [ ] **Step 3: Implement recursive redaction and tracing configuration**

Use compiled regular expressions for email, Chinese mobile number, bearer/API token patterns, and PostgreSQL/MySQL URLs. Redact values before constructing LangSmith metadata or logs. `TracingConfig` must return disabled behavior when `LANGSMITH_TRACING=false`; no LangSmith client is created in that mode. When enabled, use LangSmith-supported environment configuration and an anonymizer / callback boundary without storing raw payloads in application logs.

- [ ] **Step 4: Add public datasets and deterministic evaluators**

Each JSONL file must contain at least five cases and no real personal information. Include:

- `knowledge_qa.jsonl`: Redis slow query, MySQL EXPLAIN, cache policy, unknown question, citation case;
- `agent_trajectory.jsonl`: expected tool empty, `analyze_sql`, `analyze_redis`, and a rejected high-risk tool;
- `safety_boundary.jsonl`: prompt injection, secret request, unauthorized delete, safe read-only diagnosis, approval rejection.

Implement explicit metrics and the initial gates from the spec: structured output >= 0.98, Mock Agent completion >= 0.95, tool validation >= 0.98, safety accuracy >= 0.95, citation completeness >= 0.90, unhandled exception <= 0.02. The Mock regression command must not call LangSmith.

- [ ] **Step 5: Add opt-in LangSmith evaluation entry and tests**

`run_langsmith_evaluation()` must use `langsmith.Client.evaluate` only when the caller runs `pytest -m langsmith` or the explicit CLI command. It must pass the stable dataset name, agent function, evaluators, and experiment prefix; it must not silently upload local test data during ordinary tests.

- [ ] **Step 6: Run tests and commit**

Run: `cd 2-learning/projects/langchain-agent-platform && python -m pytest -q -m "not langsmith and not real_model"`

Expected: all local tests PASS without external credentials.

Run: `git add 2-learning/projects/langchain-agent-platform/src/ai_platform/observability 2-learning/projects/langchain-agent-platform/src/ai_platform/evaluation 2-learning/projects/langchain-agent-platform/datasets 2-learning/projects/langchain-agent-platform/tests/unit/test_redaction.py 2-learning/projects/langchain-agent-platform/tests/unit/test_evaluators.py 2-learning/projects/langchain-agent-platform/tests/integration/test_regression_gate.py && git diff --check --cached && git commit -m "feat(ai): add tracing redaction and evaluation gates"`

---

## Task 8: Add Java Spring Boot Gateway with REST, SSE, validation, and error mapping

**Depends on:** Task 6

**Files:**
- Create: `2-learning/projects/langchain-agent-platform/java-gateway/pom.xml`
- Create: `2-learning/projects/langchain-agent-platform/java-gateway/src/main/java/com/example/aiplatform/AiPlatformApplication.java`
- Create: `2-learning/projects/langchain-agent-platform/java-gateway/src/main/java/com/example/aiplatform/config/AiServiceProperties.java`
- Create: `2-learning/projects/langchain-agent-platform/java-gateway/src/main/java/com/example/aiplatform/client/AiServiceClient.java`
- Create: `2-learning/projects/langchain-agent-platform/java-gateway/src/main/java/com/example/aiplatform/client/AiStreamClient.java`
- Create: `2-learning/projects/langchain-agent-platform/java-gateway/src/main/java/com/example/aiplatform/service/AiConversationService.java`
- Create: `2-learning/projects/langchain-agent-platform/java-gateway/src/main/java/com/example/aiplatform/controller/ChatController.java`
- Create: `2-learning/projects/langchain-agent-platform/java-gateway/src/main/java/com/example/aiplatform/exception/GlobalExceptionHandler.java`
- Create: `2-learning/projects/langchain-agent-platform/java-gateway/src/main/resources/application.yml`
- Create: `2-learning/projects/langchain-agent-platform/java-gateway/src/test/java/com/example/aiplatform/client/AiServiceClientTest.java`
- Create: `2-learning/projects/langchain-agent-platform/java-gateway/src/test/java/com/example/aiplatform/controller/ChatControllerTest.java`

**Interfaces:**
- `AiServiceProperties` binds `ai.service.base-url`, `ai.service.connect-timeout`, and `ai.service.response-timeout` with validation.
- `AiServiceClient.chat(ChatRequest, String requestId) -> Mono<ChatResponse>` calls Python `POST /api/v1/chat` and forwards `X-Request-Id`.
- `AiStreamClient.stream(String sessionId, String question, String requestId) -> Flux<ServerSentEvent<AiStreamEvent>>` consumes Python SSE with `WebClient`.
- `ChatController` exposes `POST /api/v1/chat` and `GET /api/v1/chat/stream`.
- `GlobalExceptionHandler` maps validation errors to `400`, Python dependency failures to `502`, timeouts to `504`, and unknown errors to `500` without returning stack traces.

- [ ] **Step 1: Write failing Java tests against a mock HTTP server**

Use `MockWebServer` in the test scope and a concrete JSON fixture for Python response:

```java
@Test
void chatClientForwardsRequestIdAndMapsResponse() throws Exception {
    server.enqueue(new MockResponse()
        .setHeader("Content-Type", "application/json")
        .setBody("""
            {"request_id":"req-001","session_id":"s-001","answer":"Redis 命中率需要监控。","citations":[],"confidence":0.86,"trace_id":null}
            """));

    ChatResponse response = client.chat(
        new ChatRequest("Redis 命中率如何监控？", "s-001"),
        "req-001"
    ).block();

    assertThat(response.answer()).isEqualTo("Redis 命中率需要监控。");
    assertThat(server.takeRequest().getHeader("X-Request-Id")).isEqualTo("req-001");
}
```

Add a controller test asserting invalid blank questions return `400`, and an SSE test asserting `run_started` and `run_completed` events are forwarded.

- [ ] **Step 2: Run Maven tests and confirm failure**

Run: `cd 2-learning/projects/langchain-agent-platform/java-gateway && mvn test -q`

Expected: FAIL because the Maven project and Java classes are absent.

- [ ] **Step 3: Create the Spring Boot project and typed DTOs**

`pom.xml` must use Spring Boot 3.x parent, Java 17, and dependencies `spring-boot-starter-webflux`, `spring-boot-starter-validation`, `spring-boot-starter-actuator`, `spring-boot-starter-test`, `reactor-test`, and `mockwebserver` for tests. Define Java records for `ChatRequest`, `Citation`, `ChatResponse`, and `AiStreamEvent`; use Bean Validation annotations on request fields.

- [ ] **Step 4: Implement WebClient clients and service boundary**

Configure `WebClient.Builder` once. Use `retrieve().onStatus(...)` to map `400`, `502`, and `503` responses to typed gateway exceptions. Apply connect and response timeout settings from `AiServiceProperties`. `AiStreamClient` must use `accept(MediaType.TEXT_EVENT_STREAM)` and map `ServerSentEvent<String>` payloads to `AiStreamEvent`; it must not buffer the entire stream.

- [ ] **Step 5: Implement controllers and global exception handling**

`ChatController` must create or accept `X-Request-Id`, pass it to the service, return `Mono<ResponseEntity<ChatResponse>>` for normal requests, and return `Flux<ServerSentEvent<AiStreamEvent>>` for streaming. Error responses use:

```json
{"code":"AI_SERVICE_UNAVAILABLE","message":"AI service is temporarily unavailable","request_id":"req-001"}
```

Do not expose the Python stack trace or upstream URL.

- [ ] **Step 6: Run Java tests and commit**

Run: `cd 2-learning/projects/langchain-agent-platform/java-gateway && mvn test -q`

Expected: all Java tests PASS.

Run: `git add 2-learning/projects/langchain-agent-platform/java-gateway && git diff --check --cached && git commit -m "feat(java): add spring boot ai gateway"`

---

## Task 9: Add Docker Compose, project README, API contract, and Mermaid diagrams

**Depends on:** Tasks 6 and 8

**Files:**
- Create: `2-learning/projects/langchain-agent-platform/docker-compose.yml`
- Create: `2-learning/projects/langchain-agent-platform/Dockerfile`
- Create: `2-learning/projects/langchain-agent-platform/docs/api-contract.md`
- Create: `2-learning/projects/langchain-agent-platform/docs/architecture.md`
- Create: `2-learning/projects/langchain-agent-platform/docs/diagrams/developer-platform-context.mmd`
- Create: `2-learning/projects/langchain-agent-platform/docs/diagrams/agentic-rag-flow.mmd`
- Create: `2-learning/projects/langchain-agent-platform/docs/diagrams/agent-workflow-state.mmd`
- Create: `2-learning/projects/langchain-agent-platform/docs/diagrams/java-python-sse-sequence.mmd`
- Create: `2-learning/projects/langchain-agent-platform/docs/diagrams/evaluation-loop-flow.mmd`
- Create: `2-learning/projects/langchain-agent-platform/README.md`
- Create: `2-learning/projects/langchain-agent-platform/Makefile`
- Create: `2-learning/projects/langchain-agent-platform/tests/integration/test_compose_config.py`

**Interfaces:**
- `docker-compose.yml` defines `ai-service`, `java-gateway`, `postgres`, and `redis`; default AI service environment is Mock and contains no secret values.
- `README.md` documents Mock startup, Real configuration, API examples, test commands, architecture, security boundary, evaluation, and interview entry points.
- `docs/api-contract.md` is the single source for Python / Java REST and SSE payloads.
- Mermaid diagrams represent one topic each and use the repository's semantic classes and labels.

- [ ] **Step 1: Write failing compose and documentation checks**

```python
# tests/integration/test_compose_config.py
from pathlib import Path
import yaml


def test_compose_has_mock_safe_services() -> None:
    compose = yaml.safe_load(Path("docker-compose.yml").read_text(encoding="utf-8"))

    assert {"ai-service", "java-gateway", "postgres", "redis"}.issubset(compose["services"])
    assert compose["services"]["ai-service"]["environment"]["APP_MODE"] == "mock"
    assert "API_KEY" not in Path("docker-compose.yml").read_text(encoding="utf-8")
```

- [ ] **Step 2: Run the check and confirm failure**

Run: `cd 2-learning/projects/langchain-agent-platform && python -m pytest tests/integration/test_compose_config.py -q`

Expected: FAIL because Compose and documentation files are absent; add `pyyaml` to the test extra if the environment does not already provide it.

- [ ] **Step 3: Implement Docker Compose and local commands**

Compose must expose:

```text
ai-service: 8000
java-gateway: 8080
postgres: 5432
redis: 6379
```

Use health checks for PostgreSQL and Redis. Do not mount `.env` into containers. The Python `Dockerfile` installs the package and starts `uvicorn ai_platform.api.app:create_app --factory --host 0.0.0.0 --port 8000`; if the implementation uses a module-level `app` instead, update the README and Makefile to use that exact command. The Makefile provides `install`, `test`, `test-real`, `run-mock`, `run-real`, `compose-up`, and `compose-down` commands; `test-real` and LangSmith commands must be explicit.

- [ ] **Step 4: Write the API and architecture documentation**

`api-contract.md` must include concrete request/response JSON for `/chat`, `/agent/run`, `/agent/{session_id}/resume`, health, and SSE event examples. `architecture.md` must explain Java/Python responsibility boundaries, dependency injection, failure handling, request ID propagation, Checkpoint selection, and Mock/Real behavior.

- [ ] **Step 5: Add and validate Mermaid sources**

Each diagram must use:

```mermaid
%%{ init: { 'theme': 'default', 'themeVariables': {
  'fontFamily': 'Arial, Microsoft YaHei, Helvetica, PingFang SC, sans-serif',
  'primaryColor': '#4A90D9',
  'primaryBorderColor': '#3A7BC8',
  'primaryTextColor': '#1F2937',
  'secondaryColor': '#F59E0B',
  'tertiaryColor': '#10B981',
  'lineColor': '#6B7280',
  'backgroundColor': '#FFFFFF'
} } }%%
```

Use `service`, `database`, `gateway`, and `external` class definitions rather than inline `style`. Include labels such as `REST`, `SSE`, `Checkpoint`, `RAG`, and `Trace` with no label longer than eight characters.

- [ ] **Step 6: Run checks and commit**

Run: `cd 2-learning/projects/langchain-agent-platform && python -m pytest tests/integration/test_compose_config.py -q && git diff --check`

Expected: compose test and whitespace check PASS.

Run: `git add 2-learning/projects/langchain-agent-platform/Dockerfile 2-learning/projects/langchain-agent-platform/docker-compose.yml 2-learning/projects/langchain-agent-platform/Makefile 2-learning/projects/langchain-agent-platform/README.md 2-learning/projects/langchain-agent-platform/docs 2-learning/projects/langchain-agent-platform/tests/integration/test_compose_config.py && git commit -m "docs(ai): document agent platform architecture and local run"`

---

## Task 10: Add and modernize the LangChain learning path and project indexes

**Depends on:** Tasks 2–9

**Files:**
- Modify: `2-learning/stacks/14-langchain/README.md`
- Create: `2-learning/stacks/14-langchain/01-basics/01-model-and-provider-abstraction.md`
- Create: `2-learning/stacks/14-langchain/01-basics/02-prompt-and-context-engineering.md`
- Create: `2-learning/stacks/14-langchain/01-basics/03-structured-output.md`
- Create: `2-learning/stacks/14-langchain/01-basics/04-streaming.md`
- Create: `2-learning/stacks/14-langchain/02-core/01-tool-calling.md`
- Create: `2-learning/stacks/14-langchain/02-core/02-agent-basics.md`
- Create: `2-learning/stacks/14-langchain/02-core/03-retrieval-and-rag.md`
- Create: `2-learning/stacks/14-langchain/02-core/04-hybrid-retrieval-and-rerank.md`
- Create: `2-learning/stacks/14-langchain/02-core/05-context-engineering.md`
- Create: `2-learning/stacks/14-langchain/03-advanced/01-langgraph-state-and-routing.md`
- Create: `2-learning/stacks/14-langchain/03-advanced/02-checkpoint-memory-and-store.md`
- Create: `2-learning/stacks/14-langchain/03-advanced/03-human-in-the-loop.md`
- Create: `2-learning/stacks/14-langchain/03-advanced/04-agentic-rag.md`
- Create: `2-learning/stacks/14-langchain/03-advanced/05-langsmith-tracing.md`
- Create: `2-learning/stacks/14-langchain/03-advanced/06-langsmith-evaluation.md`
- Create: `2-learning/stacks/14-langchain/04-projects/01-ai-developer-copilot.md`
- Create: `2-learning/stacks/14-langchain/04-projects/02-enterprise-knowledge-assistant.md`
- Create: `2-learning/stacks/14-langchain/04-projects/03-agentic-rag.md`
- Create: `2-learning/stacks/14-langchain/04-projects/04-enterprise-developer-platform.md`
- Create: `2-learning/stacks/14-langchain/05-interview/resume-project-story.md`
- Create: `2-learning/projects/langchain-agent-platform/README.md` if Task 9 has not already created it; otherwise update it with tutorial links.
- Modify: `2-learning/projects/README.md`
- Modify: `2-learning/README-learning.md`
- Modify: `1-knowledge/03-ai/README.md`

**Interfaces:**
- Every tutorial links to at least one runnable file in `2-learning/projects/langchain-agent-platform/` and one test command.
- Every tutorial uses current `init_chat_model`, `create_agent`, LangGraph StateGraph, or LangSmith APIs when discussing the modern path; old APIs are placed in an explicit migration section only.
- Every external source is linked in a Sources section.
- The project index links to the new Python project and Java Gateway.

- [ ] **Step 1: Define documentation acceptance checks before writing content**

Create a local verification command using existing repository tools where possible. Do not modify `_scripts/`; invoke the existing scripts as checks:

```bash
python _scripts/check_links.py
python _scripts/validate.py
```

Before running the checks, use a small script or `rg` to verify every new Markdown file contains these headings:

```text
学习目标
核心概念
最小代码窗口
项目代码窗口
测试验证
面试回答
简历表达
来源
```

The checks must treat missing headings as failures rather than silently skipping files.

- [ ] **Step 2: Add the modern basics and core tutorials**

Each document must explain the concept in Chinese, provide a compact Python code window, map it to one project file, explain one failure mode, provide its exact test command, and end with a short interview answer. Use the following code anchors:

```python
from langchain.chat_models import init_chat_model
from langchain.agents import create_agent
from langgraph.graph import StateGraph, START, END
from langsmith import Client
```

The model abstraction tutorial must explain runtime provider switching and capability differences. The structured-output tutorial must show a Pydantic schema and `response_format`. The tool tutorial must show schema validation and high-risk confirmation. The RAG tutorial must show empty recall and citations, not only vector search.

- [ ] **Step 3: Add LangGraph, Agentic RAG, and LangSmith tutorials**

The advanced tutorials must point to the actual files `src/ai_platform/graph/state.py`, `workflow.py`, `rag/pipeline.py`, `observability/redaction.py`, and `evaluation/regression.py`. Explain `thread_id`, `InMemorySaver`, PostgreSQL Checkpoint seam, `interrupt` / resume, bounded loops, LangSmith traces, datasets, trajectory evaluation, and PII redaction. Include no fabricated evaluation numbers; label thresholds as initial gates and results as locally measured only after execution.

- [ ] **Step 4: Add project and interview documents**

The four project documents must form one progression:

```text
AI Developer Copilot
    → Enterprise Knowledge Assistant
    → Agentic RAG
    → Enterprise Developer Intelligence Platform
```

`resume-project-story.md` must include a 60-second introduction, STAR bullets, architecture explanation, five measurable-but-unfilled metric templates expressed as “通过运行评测后填写”, and at least 15 follow-up interview questions with grounded answers. Do not claim a metric until a test or evaluation report produces it.

- [ ] **Step 5: Update all indexes and preserve old links**

Add a modern-mainline table to `14-langchain/README.md`, add the flagship project to `2-learning/projects/README.md`, add it to the learning route in `2-learning/README-learning.md`, and add LangChain / Agentic RAG / LangSmith links to `1-knowledge/03-ai/README.md`. Do not remove existing AI 商城 or mini-blog links. Use relative links only.

- [ ] **Step 6: Run documentation validation and commit**

Run:

```bash
python _scripts/check_links.py
python _scripts/validate.py
git diff --check
```

Expected: link and repository validation pass; any pre-existing unrelated warning must be recorded separately instead of hidden.

Run: `git add 2-learning/stacks/14-langchain 2-learning/projects/langchain-agent-platform/README.md 2-learning/projects/README.md 2-learning/README-learning.md 1-knowledge/03-ai/README.md && git commit -m "docs(ai): add modern langchain learning path"`

---

## Task 11: Add final end-to-end verification, reports, and release checklist

**Depends on:** Tasks 1–10

**Files:**
- Create: `2-learning/projects/langchain-agent-platform/docs/evaluation-report.md`
- Create: `2-learning/projects/langchain-agent-platform/docs/interview-guide.md`
- Modify: `2-learning/projects/langchain-agent-platform/README.md`
- Modify: `2-learning/projects/langchain-agent-platform/Makefile`

**Interfaces:**
- `make test` runs only credential-free Python and Java tests.
- `make test-real` is explicit and fails clearly when model credentials are absent.
- `make evaluate-mock` produces a deterministic JSON and Markdown report.
- `docs/evaluation-report.md` records actual command, date, environment mode, case count, metrics, thresholds, failures, and whether LangSmith was enabled.
- `docs/interview-guide.md` maps implementation files to likely interview questions.

- [ ] **Step 1: Run credential-free Python tests**

Run: `cd 2-learning/projects/langchain-agent-platform && python -m pytest -q -m "not real_model and not langsmith"`

Expected: all local Python tests PASS. If a test needs credentials, move it behind the correct marker before continuing.

- [ ] **Step 2: Run Java tests**

Run: `cd 2-learning/projects/langchain-agent-platform/java-gateway && mvn test -q`

Expected: all Java tests PASS without a running Python service because clients use MockWebServer.

- [ ] **Step 3: Run the local Mock smoke test**

Run: `cd 2-learning/projects/langchain-agent-platform && python -m uvicorn ai_platform.api.app:create_app --factory --host 127.0.0.1 --port 8000` in one terminal, then:

```bash
curl -fsS http://127.0.0.1:8000/api/v1/health
curl -fsS -X POST http://127.0.0.1:8000/api/v1/chat -H "Content-Type: application/json" -d '{"question":"Redis 慢查询如何排查？","session_id":"smoke-001"}'
```

Expected: health status `UP`, structured chat response, no API key requirement, and no traceback.

- [ ] **Step 4: Run Mock regression and record its output**

Run: `cd 2-learning/projects/langchain-agent-platform && python -m ai_platform.evaluation.regression --mode mock --output docs/evaluation-report.json`

Expected: deterministic report with all metrics, thresholds, case counts, and exit code 0 only when gates pass. Copy the actual output into `docs/evaluation-report.md`; do not invent values.

- [ ] **Step 5: Validate Docker Compose configuration**

Run: `cd 2-learning/projects/langchain-agent-platform && docker compose config`

Expected: valid rendered configuration with no secrets. If Docker is unavailable, record the skipped command and still run YAML parsing plus application tests.

- [ ] **Step 6: Perform final repository validation**

Run:

```bash
python _scripts/check_links.py
python _scripts/validate.py
git diff --check
git status --short
```

Expected: no broken new links, no whitespace errors, and only intended files changed. Review that no `.env`, credential, generated cache, or private data file is staged.

- [ ] **Step 7: Commit verification artifacts**

Run: `git add 2-learning/projects/langchain-agent-platform/README.md 2-learning/projects/langchain-agent-platform/Makefile 2-learning/projects/langchain-agent-platform/docs/evaluation-report.md 2-learning/projects/langchain-agent-platform/docs/interview-guide.md && git diff --check --cached && git commit -m "chore(ai): verify agent platform delivery"`

---

## Completion Checklist

- [ ] Python Mock mode starts without any model or LangSmith credential.
- [ ] Python Real mode accepts external model configuration without business-code edits.
- [ ] At least two model Provider paths are documented and capability checks are explicit.
- [ ] Structured output, tool validation, tool auditing, and high-risk approval paths have tests.
- [ ] RAG handles relevant context, empty recall, bounded rewrite, reranking seam, and citations.
- [ ] LangGraph uses typed State, conditional routing, Checkpoint, `thread_id`, bounded loops, interrupt, and resume.
- [ ] FastAPI exposes stable REST and SSE contracts with safe error responses.
- [ ] LangSmith is opt-in, traces are sanitized, datasets are public-safe, and evaluation is explicit.
- [ ] Java 17 + Spring Boot 3 Gateway compiles, calls REST, consumes SSE, validates input, forwards request IDs, and maps failures.
- [ ] Docker Compose defines local services without secrets.
- [ ] Modern tutorial documents link to real project files and tests.
- [ ] Existing tutorials and indexes remain reachable.
- [ ] Mermaid source files meet repository complexity, style, accessibility, and naming rules.
- [ ] Python tests, Java tests, Mock smoke test, documentation checks, and repository validation have recorded output.
- [ ] No completion claim is made without the verification evidence above.
