# LangGraph 编排实战：从状态图到多 Agent 协作

> **生态**: E06 · 通识与基础 | **等级**: 进阶 | **前置要求**: 了解 Agent 设计模式（建议先阅读 02-agent-design-patterns.md）

AI Agent 从单步工具调用进化为多步骤、有状态、长时运行的协作系统，编排层（Orchestration）成为核心挑战。2025 年，LangGraph 以图结构编排的独特设计脱颖而出，成为构建生产级 Agent 系统的主流框架之一。2025 Q2 发布的 Cloud-Native GA 版本更是将 Serverless 自动扩缩和零停机热更新带入了 Agent 部署领域。

本教程从 LangGraph 的核心概念出发，逐步构建从简单 Agent 到多 Agent 协作系统的完整实战路径。

---

## 1. 为什么需要编排层

在 Agent 系统的早期实践中，开发者通常通过手写循环（`while True` + `if-else`）来实现 Agent 的推理-行动循环。这种方式在简单场景下可行，但随着任务复杂度的提升，暴露出以下问题：

| 问题 | 表现 | 后果 |
|------|------|------|
| **状态管理混乱** | 需要在循环中手动维护对话历史、工具结果、中间变量 | 代码臃肿、难以调试 |
| **控制流脆弱** | 分支逻辑散落在循环体中，修改一个分支可能影响全局 | 迭代成本高、容易引入 Bug |
| **并行能力缺失** | 手写循环天然串行，无法并行执行独立子任务 | 长任务延迟高 |
| **可观测性差** | 每一步的执行状态、决策原因无法自动记录 | 生产排障困难 |
| **持久化缺失** | 进程崩溃后所有状态丢失 | 无法支持长时任务 |

LangGraph 通过**有向图（Directed Graph）** 模型解决了这些问题。它将 Agent 的执行流程建模为图结构——节点（Node）是计算单元，边（Edge）是控制流，状态（State）是全局数据总线。这种设计天然支持复杂的分支、循环、并行和人类介入。

### LangGraph vs LangChain vs AutoGen

| 维度 | LangGraph | LangChain | AutoGen |
|------|-----------|-----------|---------|
| **定位** | 低层编排框架 | 高层应用框架 | 多 Agent 对话框架 |
| **核心抽象** | StateGraph（状态图） | Chain（链） | Agent + Conversation |
| **控制流** | 图结构（节点+边） | 线性/分支链 | 对话轮次 |
| **状态管理** | 显式全局状态（TypedDict） | 隐式上下文传递 | 对话历史 |
| **并行支持** | 原生（fan-out/fan-in） | 有限 | 有限 |
| **Human-in-loop** | 内置（interrupt） | 需手动实现 | 内置 |
| **持久化** | 内置 Checkpointer | 无 | 无 |
| **部署** | LangGraph Cloud / 自托管 | 自托管 | 自托管 |
| **学习曲线** | 中高 | 低 | 中 |

选择建议：如果需要**精细控制执行流程、多 Agent 协作、长时任务**，LangGraph 是最佳选择；如果只是快速构建简单的 RAG 或工具调用链，LangChain 的 Chain 模式更轻量。

---

## 2. 核心概念：StateGraph

LangGraph 的核心是 **StateGraph**——一个有状态的图结构，包含四个基本要素。

### 2.1 State（状态）

State 是图的全局数据总线，所有节点都可以读取和写入。State 通常用 TypedDict 定义：

```python
from typing import TypedDict, List, Dict, Any
from langgraph.graph import StateGraph, END

class AgentState(TypedDict):
    """Agent 的全局状态"""
    messages: List[Dict[str, str]]       # 对话历史
    tool_results: Dict[str, Any]         # 工具调用结果
    current_step: int                    # 当前执行步数
    max_steps: int                       # 最大步数限制
    final_answer: str                    # 最终答案
```

状态设计是 LangGraph 应用的第一步，也是最关键的一步。好的状态设计应遵循以下原则：

- **最小化**：只包含节点间需要共享的数据，避免将所有数据塞入状态
- **类型安全**：使用 TypedDict 或 Pydantic 模型，利用类型系统提前发现错误
- **版本兼容**：生产系统中状态结构会演进，设计时考虑向前/向后兼容

### 2.2 Node（节点）

Node 是图的计算单元，接收 State 作为输入，返回更新后的 State 子集：

```python
from langgraph.graph import StateGraph, END

def agent_node(state: AgentState) -> dict:
    """Agent 推理节点：决定下一步行动"""
    messages = state["messages"]
    # 调用 LLM 进行推理
    response = llm.invoke(messages)
    return {"messages": messages + [response]}

def tool_node(state: AgentState) -> dict:
    """工具执行节点：执行 Agent 指定的工具调用"""
    last_message = state["messages"][-1]
    if hasattr(last_message, "tool_calls"):
        results = execute_tools(last_message.tool_calls)
        return {"tool_results": results}
    return {}
```

Node 的返回值会**合并**到 State 中，而不是替换。这意味着每个 Node 只需返回自己修改的字段，其他字段保持不变。

### 2.3 Edge（边）

Edge 连接节点，定义控制流。LangGraph 支持两种类型的边：

| 边类型 | 说明 | 用途 |
|--------|------|------|
| **普通边** | 从源节点到目标节点的固定路径 | 顺序执行步骤 |
| **条件边** | 根据 State 动态选择下一个节点 | 分支、循环、终止判断 |

```python
# 普通边：agent 执行完后进入 tool 节点
graph.add_edge("agent", "tool")

# 条件边：根据 agent 的决策路由
graph.add_conditional_edges(
    "agent",
    decide_next,           # 条件函数，接收 state 返回节点名称
    {
        "tool": "tool",    # 继续工具调用
        "end": END,        # 结束
    }
)
```

### 2.4 条件边函数

条件边是 LangGraph 最强大的特性之一。它让 Agent 能够根据当前状态动态决定下一步走向：

```python
def decide_next(state: AgentState) -> str:
    """根据当前状态决定下一步"""
    # 1. 检查是否达到最大步数
    if state["current_step"] >= state["max_steps"]:
        return "end"

    # 2. 检查最后一条消息是否有工具调用
    last_message = state["messages"][-1]
    if hasattr(last_message, "tool_calls") and last_message.tool_calls:
        state["current_step"] += 1
        return "tool"

    # 3. 没有工具调用，Agent 已生成最终答案
    state["final_answer"] = last_message.content
    return "end"
```

---

## 3. 构建一个简单的 Agent

让我们从零开始构建一个完整的 ReAct Agent，包含推理-工具调用循环。

### 3.1 定义状态和工具

```python
from typing import TypedDict, List, Dict, Any, Sequence
from langgraph.graph import StateGraph, END
from langchain_core.messages import BaseMessage, HumanMessage, AIMessage
from langchain_openai import ChatOpenAI

# 1. 定义状态
class SimpleAgentState(TypedDict):
    messages: Sequence[BaseMessage]
    next_step: str

# 2. 定义工具
def get_weather(city: str) -> str:
    """获取指定城市的天气"""
    # 模拟天气查询
    weather_data = {
        "北京": "晴天，25°C",
        "上海": "多云，28°C",
        "深圳": "阵雨，30°C"
    }
    return weather_data.get(city, f"暂未收录 {city} 的天气数据")

def calculate(expression: str) -> str:
    """执行数学计算"""
    try:
        result = eval(expression, {"__builtins__": {}}, {})
        return f"计算结果：{result}"
    except Exception as e:
        return f"计算错误：{e}"

tools = [get_weather, calculate]
```

### 3.2 定义节点

```python
# 3. 初始化 LLM
llm = ChatOpenAI(model="gpt-4o", temperature=0)
llm_with_tools = llm.bind_tools(tools)

# 4. 定义 Agent 节点
def agent(state: SimpleAgentState) -> dict:
    """Agent 推理节点：分析消息并决定是否调用工具"""
    messages = state["messages"]
    response = llm_with_tools.invoke(messages)
    return {"messages": [response]}

# 5. 定义工具执行节点
def execute_tools(state: SimpleAgentState) -> dict:
    """执行 Agent 请求的工具调用"""
    last_message = state["messages"][-1]
    results = []

    for tool_call in last_message.tool_calls:
        tool_name = tool_call["name"]
        tool_args = tool_call["args"]

        # 根据工具名称路由
        if tool_name == "get_weather":
            result = get_weather(**tool_args)
        elif tool_name == "calculate":
            result = calculate(**tool_args)
        else:
            result = f"未知工具：{tool_name}"

        results.append(
            AIMessage(
                content=result,
                name=tool_name,
                tool_call_id=tool_call["id"]
            )
        )

    return {"messages": results}
```

### 3.3 定义路由函数

```python
# 6. 定义路由函数
def should_continue(state: SimpleAgentState) -> str:
    """判断是否继续工具调用"""
    last_message = state["messages"][-1]
    if hasattr(last_message, "tool_calls") and last_message.tool_calls:
        return "continue"  # 需要继续执行工具
    return "end"           # 无工具调用，结束
```

### 3.4 编译并运行

```python
# 7. 构建图
workflow = StateGraph(SimpleAgentState)

# 添加节点
workflow.add_node("agent", agent)
workflow.add_node("tools", execute_tools)

# 设置入口点
workflow.set_entry_point("agent")

# 添加条件边
workflow.add_conditional_edges(
    "agent",
    should_continue,
    {
        "continue": "tools",  # 有工具调用 → 执行工具
        "end": END            # 无工具调用 → 结束
    }
)

# 添加普通边：工具执行完成后回到 Agent
workflow.add_edge("tools", "agent")

# 8. 编译图
app = workflow.compile()

# 9. 运行
def run_agent(query: str):
    """运行 Agent 并返回结果"""
    inputs = {
        "messages": [HumanMessage(content=query)]
    }
    for output in app.stream(inputs):
        for node_name, node_output in output.items():
            messages = node_output.get("messages", [])
            if messages:
                print(f"[{node_name}]: {messages[-1].content}")

run_agent("北京今天天气怎么样？顺便算一下 15 * 24 + 7 等于多少？")
```

输出示例：

```
[agent]: 我需要查询北京的天气并计算一个数学表达式。让我先获取天气信息。
[tools]: 晴天，25°C
[agent]: 计算结果：367
[agent]: 北京今天天气晴朗，气温 25°C。15 * 24 + 7 的计算结果是 367。
```

---

## 4. 多 Agent 架构

LangGraph 的强大之处在于它天然支持多种多 Agent 协作模式。以下是四种主要架构的对比和实现。

### 4.1 四种架构对比

| 架构 | 协作方式 | 优势 | 劣势 | 适用场景 |
|------|---------|------|------|---------|
| **Subagents** | 主 Agent 创建子 Agent 执行子任务 | 隔离性好、职责清晰 | 通信开销大 | 并行子任务（数据分析、报告生成） |
| **Skills** | 预定义可复用技能模块 | 低延迟、可复用 | 灵活性差 | 标准化操作（翻译、摘要） |
| **Handoffs** | Agent 间转移对话控制权 | 自然、可追溯 | 复杂度高 | 客服分级、故障升级 |
| **Routers** | 路由器根据意图分发到指定 Agent | 扩展性好、负载均衡 | 单点瓶颈 | 多服务整合（智能客服平台） |

### 4.2 Subagents 模式实现

Subagents 模式是最常用的多 Agent 架构。主 Agent 负责任务分解，子 Agent 专注特定领域。

```python
class SubAgentState(TypedDict):
    """多 Agent 系统的状态"""
    messages: Sequence[BaseMessage]
    current_task: str
    sub_agent_results: Dict[str, str]
    tasks: List[Dict[str, str]]

# 子 Agent：数据分析
def data_analysis_agent(state: SubAgentState) -> dict:
    """数据分析子 Agent"""
    task = state["current_task"]
    prompt = f"""
    你是一个数据分析专家。请分析以下任务并提供数据洞察：
    {task}
    输出格式：包含数据来源、分析方法、关键发现。
    """
    response = llm.invoke([HumanMessage(content=prompt)])
    return {"sub_agent_results": {"data_analysis": response.content}}

# 子 Agent：报告生成
def report_agent(state: SubAgentState) -> dict:
    """报告生成子 Agent"""
    analysis_result = state["sub_agent_results"].get("data_analysis", "")
    prompt = f"""
    你是一个报告撰写专家。基于以下数据分析结果，生成一份专业报告：
    {analysis_result}
    输出格式：标题、摘要、详细分析、结论与建议。
    """
    response = llm.invoke([HumanMessage(content=prompt)])
    return {"sub_agent_results": {"report": response.content}}

# 主 Agent：任务调度
def orchestrator(state: SubAgentState) -> dict:
    """主 Agent：负责任务分解和调度"""
    user_query = state["messages"][-1].content
    prompt = f"""
    将以下用户请求分解为子任务：
    {user_query}

    子任务列表：
    1. data_analysis：数据分析
    2. report：生成报告

    请确认是否已准备好执行子任务。
    """
    response = llm.invoke([HumanMessage(content=prompt)])
    return {"messages": [response], "tasks": [
        {"name": "data_analysis", "status": "pending"},
        {"name": "report", "status": "pending"}
    ]}

# 构建多 Agent 图
def build_multi_agent_graph():
    graph = StateGraph(SubAgentState)

    # 添加节点
    graph.add_node("orchestrator", orchestrator)
    graph.add_node("data_analysis", data_analysis_agent)
    graph.add_node("report", report_agent)

    # 设置入口
    graph.set_entry_point("orchestrator")

    # 路由逻辑
    graph.add_conditional_edges(
        "orchestrator",
        lambda s: "data_analysis",
        {"data_analysis": "data_analysis"}
    )
    graph.add_edge("data_analysis", "report")
    graph.add_edge("report", END)

    return graph.compile()
```

### 4.3 Routers 架构实现

Routers 模式适用于需要根据用户意图分发到不同 Specialist Agent 的场景：

```python
# 路由器节点
def router(state: AgentState) -> dict:
    """意图识别路由器"""
    user_query = state["messages"][-1].content

    classification_prompt = f"""
    将以下用户请求分类到合适的 Agent：
    - "code": 代码编写、调试、代码审查相关
    - "doc": 文档撰写、技术写作相关
    - "data": 数据分析、数据处理相关
    - "general": 通用对话、其他

    用户请求：{user_query}
    输出：仅返回分类名称
    """
    decision = llm.invoke([HumanMessage(content=classification_prompt)])
    return {"routed_to": decision.content.strip()}

# 在条件边中使用路由器
def route_based_on_intent(state: AgentState) -> str:
    """根据意图路由到对应 Agent"""
    return state.get("routed_to", "general")

# 构建路由器图
graph.add_conditional_edges(
    "router",
    route_based_on_intent,
    {
        "code": "code_agent",
        "doc": "doc_agent",
        "data": "data_agent",
        "general": "general_agent"
    }
)
```

---

## 5. Human-in-the-loop

LangGraph 内置了 `interrupt` 机制，支持在图的任意节点插入人工审批门：

```python
from langgraph.checkpoint import MemorySaver

def sensitive_operation_node(state: AgentState) -> dict:
    """敏感操作节点：需要人工确认"""
    operation = state.get("pending_operation", {})

    # 使用 interrupt 暂停执行，等待人工确认
    confirmation = interrupt({
        "type": "human_approval",
        "operation": operation,
        "message": f"请确认是否执行以下操作：{operation['description']}"
    })

    if confirmation.get("approved"):
        # 执行操作
        result = execute_operation(operation)
        return {"operation_result": result}
    else:
        return {"operation_result": "操作被拒绝", "status": "rejected"}

# 构建带 Human-in-the-loop 的图
workflow = StateGraph(AgentState)
workflow.add_node("agent", agent_node)
workflow.add_node("sensitive_op", sensitive_operation_node)
workflow.add_node("normal_op", normal_operation_node)

# 条件路由：根据操作类型决定是否需要人工审批
workflow.add_conditional_edges(
    "agent",
    decide_operation_type,
    {
        "sensitive": "sensitive_op",  # 敏感操作 → 人工审批
        "normal": "normal_op"         # 普通操作 → 直接执行
    }
)

# 使用 MemorySaver 启用 Checkpointer
checkpointer = MemorySaver()
app = workflow.compile(checkpointer=checkpointer)

# 运行时需要提供 thread_id 以支持中断恢复
config = {"configurable": {"thread_id": "user_session_123"}}
for event in app.stream(inputs, config):
    if isinstance(event, tuple) and event[0] == "interrupt":
        # 收到中断信号，等待人工确认
        approval = await request_approval(event[1])
        # 恢复执行
        app.invoke(None, config, resume=approval)
```

---

## 6. Checkpointing 与持久化

LangGraph 的 Checkpointer 机制实现了 Agent 状态的持久化，是支撑长时任务和故障恢复的关键组件。

```python
from langgraph.checkpoint import MemorySaver, SqliteSaver, PostgresSaver

# 1. 内存级 Checkpointer（开发/测试用）
memory_checkpointer = MemorySaver()

# 2. SQLite Checkpointer（单机生产）
sqlite_checkpointer = SqliteSaver.from_conn_string("checkpoints.db")

# 3. PostgreSQL Checkpointer（分布式生产）
pg_checkpointer = PostgresSaver.from_conn_string(
    "postgresql://user:pass@host:5432/langgraph"
)

# 编译时传入 Checkpointer
app = workflow.compile(checkpointer=pg_checkpointer)

# 恢复中断的执行
config = {"configurable": {"thread_id": "task_001"}}
state = app.get_state(config)
print(f"当前状态：步骤 {state.values['current_step']}/{state.values['max_steps']}")

# 继续执行
for event in app.stream(None, config):
    print(event)
```

Checkpointer 的核心能力：

| 能力 | 说明 | 用途 |
|------|------|------|
| **状态快照** | 每一步执行后自动保存完整状态 | 故障恢复 |
| **时间旅行** | 支持回溯到任意历史状态 | 调试、审计 |
| **分支执行** | 从历史状态分叉出新执行路径 | A/B 测试、重试 |
| **并发隔离** | 不同 thread_id 的状态完全隔离 | 多用户支持 |

---

## 7. LangSmith 调试与追踪

LangSmith 是 LangGraph 官方提供的可观测性平台，提供端到端的链路追踪和调试能力：

```python
import os
from langsmith import Client

# 配置 LangSmith
os.environ["LANGCHAIN_TRACING_V2"] = "true"
os.environ["LANGCHAIN_API_KEY"] = "your-api-key"
os.environ["LANGCHAIN_PROJECT"] = "my-agent-project"

# 创建 LangSmith 客户端
client = Client()

# 运行 Agent 时自动记录追踪
for event in app.stream(inputs, config):
    # 所有追踪数据自动上传到 LangSmith
    pass

# 手动添加自定义指标
with client.trace("custom_metric") as rt:
    rt.add_metadata({"user_id": "u123", "latency_ms": 450})
```

LangSmith 提供的关键能力：

| 功能 | 说明 | 排障场景 |
|------|------|---------|
| **Trace 视图** | 展示每次运行的完整调用链路 | 定位哪一步出错了 |
| **Token 用量** | 每个节点消耗的输入/输出 token | 成本优化 |
| **延迟分析** | 每个节点的执行时间 | 性能瓶颈定位 |
| **输入/输出对比** | 对比不同版本的 Agent 输出 | 回归测试 |
| **数据集评估** | 运行测试集并计算评分 | 上线前质量验证 |

---

## 8. 部署与生产运维

### 8.1 LangGraph Cloud 部署

LangGraph Cloud 提供 Serverless 部署能力，支持自动扩缩和零停机热更新：

```yaml
# langgraph.json
{
  "python_version": "3.11",
  "dependencies": ["."],
  "graphs": {
    "agent": "./agent.py:app"
  },
  "env": {
    "OPENAI_API_KEY": "${OPENAI_API_KEY}",
    "LANGCHAIN_API_KEY": "${LANGCHAIN_API_KEY}"
  },
  "checkpointer": "postgres",
  "autoscaling": {
    "min_instances": 2,
    "max_instances": 20,
    "target_cpu": 70
  }
}
```

```bash
# 部署命令
langgraph deploy --config langgraph.json
```

### 8.2 生产最佳实践

| 实践 | 说明 | 实施方案 |
|------|------|---------|
| **超时控制** | 每个节点设置最大执行时间 | Node 内部使用 `asyncio.wait_for` |
| **重试策略** | LLM 调用和工具调用失败时重试 | 使用 `@retry` 装饰器或指数退避 |
| **速率限制** | 控制 LLM API 调用频率 | 使用 Token Bucket 或 Semaphore |
| **状态大小控制** | 防止状态无限增长 | 定期压缩消息历史 |
| **优雅降级** | 子 Agent 失败时不影响主流程 | 使用 try-except 包裹子节点 |
| **版本管理** | 图结构变更需要版本控制 | 使用 LangSmith 对比不同版本 |

### 8.3 超时与重试示例

```python
import asyncio
from tenacity import retry, stop_after_attempt, wait_exponential

@retry(
    stop=stop_after_attempt(3),
    wait=wait_exponential(multiplier=1, min=2, max=10)
)
async def robust_llm_call(messages, timeout=30):
    """带重试和超时的 LLM 调用"""
    try:
        response = await asyncio.wait_for(
            llm.ainvoke(messages),
            timeout=timeout
        )
        return response
    except asyncio.TimeoutError:
        raise TimeoutError("LLM 调用超时")

async def robust_agent_node(state: AgentState) -> dict:
    """带超时保护和优雅降级的 Agent 节点"""
    try:
        response = await robust_llm_call(state["messages"])
        return {"messages": [response]}
    except Exception as e:
        # 优雅降级：返回错误信息而非崩溃
        return {
            "messages": [AIMessage(content=f"处理请求时遇到问题：{e}，请稍后重试")],
            "error": str(e)
        }
```

---

## 总结

LangGraph 通过图结构编排为 Agent 系统提供了强大的控制力和灵活性。关键要点：

1. **StateGraph 是核心抽象**：State（数据）、Node（计算）、Edge（控制流）三要素组成可执行的 Agent 图
2. **条件边是灵魂**：使 Agent 能够根据状态动态决策，实现 ReAct 循环、分支路由等复杂逻辑
3. **多 Agent 架构灵活**：Subagents、Routers、Handoffs、Skills 四种模式覆盖不同协作场景
4. **Human-in-the-loop 内置**：interrupt 机制让人工审批无缝融入自动化流程
5. **Checkpointer 保障可靠性**：状态持久化支撑长时任务和故障恢复
6. **LangSmith 提供可观测性**：全链路追踪是生产运维的基础设施

### 参考资源

- [LangGraph 官方文档](https://langchain-ai.github.io/langgraph/)
- [LangSmith 可观测性平台](https://smith.langchain.com/)
- 本系列：[Prompt Engineering 实战](./01-prompt-engineering-guide.md) | [AI Agent 设计模式](./02-agent-design-patterns.md) | [生产级 AI Agent 系统设计](./03-production-system-design.md)
- 仓库参考：[ai-system-design-guide](../../repositories/ombharatiya_ai-system-design-guide.md) | [AgentGuide](../../repositories/adongwanai_AgentGuide.md)