# 第十九章：AI Agent 与工作流（P2 实战）

> 📖 **参考资料**：[Pydantic AI](https://ai.pydantic.dev/) | [LangGraph](https://langchain-ai.github.io/langgraph/) | [LangGraph Multi-Agent](https://langchain-ai.github.io/langgraph/tutorials/multi_agent/)

---

## 19.1 Agent 核心概念

Agent 不是简单的 "调 API"，而是具备 **自主决策能力** 的 AI 系统 —— 它能观察环境、制定计划、调用工具、迭代执行。

```
┌────────────────────────────────────────────────────────┐
│                     Agent 核心组成                      │
│                                                        │
│         ┌──────────┐                                   │
│         │   LLM    │  ← 推理引擎（大脑）                │
│         │  (GPT-4o) │                                  │
│         └────┬─────┘                                   │
│              │                                         │
│    ┌─────────┼─────────┐                               │
│    ▼         ▼         ▼                               │
│ ┌──────┐ ┌──────┐ ┌────────┐                          │
│ │Tools │ │State │ │ Memory │                          │
│ │工具集 │ │状态机 │ │记忆系统 │                          │
│ └──────┘ └──────┘ └────────┘                          │
│    │         │         │                               │
│    └─────────┴─────────┘                               │
│              │                                         │
│         ┌────▼─────┐                                   │
│         │ Workflow │  ← 执行编排（LangGraph）           │
│         └──────────┘                                   │
└────────────────────────────────────────────────────────┘
```

### Agent vs Chain vs Function Call

| 维度 | Chain | Function Call | Agent |
|------|-------|---------------|-------|
| 决策 | 线性固定 | 单轮调用 | 多轮自主决策 |
| 工具调用 | 不支持 | 有限 | 动态选择 |
| 状态 | 无 | 无 | 持久化状态 |
| 循环 | 不支持 | 不支持 | 支持循环 |
| 适用场景 | 简单 Pipeline | 单步工具调用 | 复杂多步推理 |

---

## 19.2 Pydantic AI Agent

Pydantic AI 是 Pydantic 团队出品的轻量 Agent 框架，类型安全，与 FastAPI 天然契合。

```python
# agent/pydantic_agent.py
from pydantic import BaseModel
from pydantic_ai import Agent, Tool
import httpx


class SearchResult(BaseModel):
    title: str
    url: str
    snippet: str


# 定义 Agent
search_agent = Agent(
    model="openai:gpt-4o",
    system_prompt=(
        "你是一个信息检索助手。使用搜索工具查找信息，"
        "然后基于搜索结果给出准确、简洁的回答。"
    ),
    result_type=list[SearchResult],
)


# 注册工具
@search_agent.tool
async def web_search(ctx, query: str) -> list[dict]:
    """搜索互联网获取最新信息"""
    async with httpx.AsyncClient() as client:
        resp = await client.get(
            "https://api.search.example.com/search",
            params={"q": query, "num": 5},
        )
        return resp.json().get("results", [])


@search_agent.tool
async def calculate(ctx, expression: str) -> str:
    """执行数学计算"""
    # 生产环境应使用沙箱
    allowed = set("0123456789+-*/.() ")
    if all(c in allowed for c in expression):
        return str(eval(expression))
    return "不支持的表达式"


# 使用 Agent
async def run_agent():
    result = await search_agent.run("北京今天天气怎么样？")
    print(result.data)  # list[SearchResult]
```

---

## 19.3 LangGraph StateGraph

LangGraph 提供状态图（StateGraph）来编排复杂的 Agent 工作流，支持循环、条件分支和人机交互。

```python
# agent/workflow.py
from typing import TypedDict, Annotated
from langgraph.graph import StateGraph, END
from langgraph.graph.message import add_messages
from langchain_openai import ChatOpenAI


# 1. 定义状态
class AgentState(TypedDict):
    messages: Annotated[list, add_messages]
    current_step: str
    context: str
    final_answer: str


# 2. 定义节点
llm = ChatOpenAI(model="gpt-4o", temperature=0)


def reason_node(state: AgentState) -> dict:
    """推理节点 — 分析用户意图"""
    response = llm.invoke(state["messages"])
    return {
        "messages": [response],
        "current_step": "decide",
    }


def search_node(state: AgentState) -> dict:
    """搜索节点 — 检索外部信息"""
    # 实际项目中接入搜索引擎
    return {"context": "搜索结果：Python 3.12 带来了性能优化...", "current_step": "generate"}


def generate_node(state: AgentState) -> dict:
    """生成节点 — 综合信息生成回答"""
    msg = llm.invoke(
        f"基于以下信息回答问题：\n上下文：{state.get('context', '')}\n"
        f"对话：{state['messages']}"
    )
    return {"final_answer": msg.content, "current_step": "done"}


# 3. 条件路由
def route_step(state: AgentState) -> str:
    if state["current_step"] == "decide":
        # 判断是否需要搜索
        last_msg = state["messages"][-1].content
        if "搜索" in last_msg or "最新" in last_msg or "?" in last_msg:
            return "search"
        return "generate"
    return "end"


# 4. 构建图
graph = StateGraph(AgentState)

graph.add_node("reason", reason_node)
graph.add_node("search", search_node)
graph.add_node("generate", generate_node)

graph.set_entry_point("reason")
graph.add_conditional_edges("reason", route_step, {
    "search": "search",
    "generate": "generate",
})
graph.add_edge("search", "generate")
graph.add_edge("generate", END)

app = graph.compile()


# 5. 运行
async def run_workflow():
    result = await app.ainvoke({
        "messages": [{"role": "user", "content": "Python 3.12 有哪些新特性？"}],
        "current_step": "reason",
        "context": "",
        "final_answer": "",
    })
    print(result["final_answer"])
```

---

## 19.4 多 Agent 协作模式

```python
# agent/multi_agent.py
from dataclasses import dataclass, field
from pydantic_ai import Agent


@dataclass
class CollaborationResult:
    planner_output: str = ""
    coder_output: str = ""
    reviewer_output: str = ""
    final_plan: str = ""


# ── 规划 Agent ──
planner = Agent(
    model="openai:gpt-4o",
    system_prompt="你是项目经理，负责拆解需求为可执行的技术任务清单。",
    result_type=str,
)

# ── 编码 Agent ──
coder = Agent(
    model="openai:gpt-4o",
    system_prompt="你是高级 Python 工程师，根据任务清单编写高质量代码。",
    result_type=str,
)

# ── 审查 Agent ──
reviewer = Agent(
    model="openai:gpt-4o",
    system_prompt="你是代码审查专家，检查代码质量、安全性和性能问题。",
    result_type=str,
)


class Orchestrator:
    """多 Agent 协调器 — 顺序执行 + 反馈循环"""

    def __init__(self, max_iterations: int = 3):
        self.max_iterations = max_iterations

    async def run(self, requirement: str) -> CollaborationResult:
        result = CollaborationResult()

        # Phase 1: 规划
        plan = await planner.run(f"需求：{requirement}\n请拆解为技术任务。")
        result.planner_output = plan.data

        # Phase 2: 编码（最多重试 3 轮）
        for i in range(self.max_iterations):
            code = await coder.run(
                f"任务清单：\n{result.planner_output}\n"
                f"审查反馈：\n{result.reviewer_output or '无'}"
            )
            result.coder_output = code.data

            # Phase 3: 审查
            review = await reviewer.run(
                f"代码：\n{result.coder_output}\n请审查并给出修改意见。"
            )
            result.reviewer_output = review.data

            if "通过" in review.data or "LGTM" in review.data:
                break

        result.final_plan = (
            f"✅ 规划: {result.planner_output}\n"
            f"💻 代码: {result.coder_output}\n"
            f"🔍 审查: {result.reviewer_output}"
        )
        return result
```

---

## 19.5 FastAPI Agent API

```python
# agent/api.py
from fastapi import FastAPI
from fastapi.responses import StreamingResponse
from pydantic import BaseModel
from pydantic_ai import Agent

app = FastAPI(title="Agent API", version="1.0")

agent = Agent(
    model="openai:gpt-4o",
    system_prompt="你是一个智能助手，擅长分析问题并提供结构化回答。",
)


class AgentRequest(BaseModel):
    question: str
    stream: bool = False


class AgentResponse(BaseModel):
    answer: str
    tokens_used: int


@app.post("/agent/query", response_model=AgentResponse)
async def query_agent(req: AgentRequest):
    """同步查询 Agent"""
    result = await agent.run(req.question)
    return AgentResponse(
        answer=result.data,
        tokens_used=result.usage().total_tokens,
    )


@app.post("/agent/stream")
async def stream_agent(req: AgentRequest):
    """流式输出 Agent 回答"""
    async def generate():
        async with agent.run_stream(req.question) as stream:
            async for text in stream.stream_text():
                yield f"data: {text}\n\n"
            yield "data: [DONE]\n\n"

    return StreamingResponse(generate(), media_type="text/event-stream")


@app.get("/agent/health")
async def health():
    return {"status": "ok", "agent": "pydantic-ai", "model": "gpt-4o"}
```

---

## 必读资源

| 资源 | 链接 | 说明 |
|------|------|------|
| Pydantic AI 文档 | https://ai.pydantic.dev/ | 类型安全的 Agent 框架 |
| LangGraph 官方教程 | https://langchain-ai.github.io/langgraph/ | 状态图工作流编排 |
| LangGraph Multi-Agent | https://langchain-ai.github.io/langgraph/tutorials/multi_agent/ | 多 Agent 协作 |
| OpenAI Function Calling | https://platform.openai.com/docs/guides/function-calling | 工具调用基础 |
| Agent 设计模式 | https://www.anthropic.com/engineering/building-effective-agents | Anthropic Agent 设计指南 |
| CrewAI | https://docs.crewai.com/ | 多 Agent 协作框架 |
