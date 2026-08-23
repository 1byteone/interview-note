# LangGraph 面试题大全

## 📚 知识体系

```
LangGraph 核心概念
├── StateGraph (状态图)
├── Node (节点)
├── Edge (边)
├── State (状态)
├── Conditional Edge (条件边)
├── Persistence (持久化)
├── Streaming (流式输出)
└── Checkpoint (检查点)

LangGraph 高级模式
├── Agent (Agent 循环)
├── Multi-Agent (多智能体)
├── Supervisor (监督者)
├── Hierarchical Teams (层级团队)
├── Planning (规划)
├── Reflection (反思)
├── Human-in-the-Loop (人在回路)
└── Parallel Execution (并行执行)

LangGraph 应用场景
├── Chatbot (聊天机器人)
├── Agentic RAG (智能 RAG)
├── Corrective RAG (纠错 RAG)
├── Self-RAG (自省 RAG)
├── Plan-and-Execute (规划执行)
└── Research Agent (研究 Agent)
```

---

## 🎯 Level 1：基础题

### 1. LangGraph 是什么？和 LangChain 有什么关系？
**答案**：
LangGraph 是一个基于图的编排框架，用于构建 Agent 和复杂工作流。它是 LangChain 生态的一部分，专注处理**循环、状态、分支**等复杂流程。

**与 LangChain 的关系**：
- **LangChain**：核心抽象（LLM、Prompt、Chains、Retriever）
- **LangGraph**：基于图的编排引擎（Agent Loop、Multi-Agent）
- **区别**：LangChain 的 Chain 是线性 DAG，LangGraph 支持**循环**和**条件分支**

### 2. 什么是 StateGraph？
**答案**：
StateGraph 是 LangGraph 的核心组件，用于定义 Agent 的状态流转图。

```python
from langgraph.graph import StateGraph, State

class AgentState(State):
    messages: list
    next_step: str

# 创建图
graph = StateGraph(AgentState)

# 添加节点
graph.add_node("agent", agent_node)
graph.add_node("tools", tools_node)

# 添加边
graph.add_edge("agent", "tools")
graph.add_conditional_edges(
    "agent",
    should_continue,
    {"continue": "tools", "end": END}
)

# 编译
app = graph.compile()
```

---

## 🎯 Level 2：进阶题

### 3. 什么是 Agent Loop？LangGraph 如何实现？
**答案**：
Agent Loop 是 Agent 的核心运行机制——LLM 推理 → 工具调用 → 结果反馈 → 继续推理的循环过程。

**LangGraph 实现**：
```python
# 定义 Agent 节点
def agent_node(state: AgentState):
    response = llm.invoke(state.messages, tools=[search, calculator])
    return {"messages": [response]}

# 定义工具执行节点
def tool_node(state: AgentState):
    last_message = state.messages[-1]
    if last_message.tool_calls:
        # 执行工具调用
        results = execute_tools(last_message.tool_calls)
        return {"messages": results}
    return state

# 条件判断：是否需要继续
def should_continue(state: AgentState):
    last = state.messages[-1]
    if last.tool_calls:
        return "continue"
    return "end"

# 构建图
graph = StateGraph(AgentState)
graph.add_node("agent", agent_node)
graph.add_node("tools", tool_node)
graph.add_edge("tools", "agent")
graph.add_conditional_edges("agent", should_continue, {
    "continue": "tools",
    "end": END
})
graph.set_entry_point("agent")
```

### 4. 什么是 Conditional Edge？有什么作用？
**答案**：
Conditional Edge 是根据当前状态动态决定下一步走向的边。

**作用**：
- 实现分支逻辑（if/else）
- 决定 Agent 是否继续循环
- 路由到不同的处理节点
- 实现动态工作流

**示例**：
```python
def route_decision(state: AgentState):
    """根据状态路由到不同节点"""
    if state.requires_search:
        return "search_tool"
    elif state.requires_calculation:
        return "calculator"
    elif state.requires_database:
        return "database_query"
    else:
        return "final_answer"

graph.add_conditional_edges(
    "router",
    route_decision,
    {
        "search_tool": "search_node",
        "calculator": "calc_node",
        "database_query": "db_node",
        "final_answer": "output_node"
    }
)
```

---

## 🎯 Level 3：高级题

### 5. 什么是 Multi-Agent？LangGraph 如何实现？
**答案**：
Multi-Agent 是多个 Agent 协作完成复杂任务的模式。

**LangGraph 实现方案**：

**方案一：合作模式（Collaboration）**
```python
# 多个 Agent 共享消息列表
def agent_a(state):
    # 处理特定任务
    return {"messages": [response]}

def agent_b(state):
    # 处理特定任务
    return {"messages": [response]}

graph = StateGraph(AgentState)
graph.add_node("agent_a", agent_a)
graph.add_node("agent_b", agent_b)
# 全连接，任一 Agent 可看到所有消息
```

**方案二：监督者模式（Supervisor）**
```python
def supervisor(state):
    # 监督者决定由谁执行
    next_agent = llm.invoke(
        f"当前状态: {state.messages}，选择下一个 Agent"
    )
    return {"next": next_agent}

graph.add_conditional_edges(
    "supervisor",
    lambda s: s.next,
    {"agent_a": "agent_a", "agent_b": "agent_b", "end": END}
)
```

### 6. 什么是 Reflection 模式？如何使用？
**答案**：
Reflection 是 Agent 自我检查、自我改进的机制。

```python
# 生成节点
def generator(state):
    output = llm.invoke(f"生成: {state.task}")
    return {"generation": output}

# 反思节点
def reflector(state):
    feedback = llm.invoke(f"""
    分析以下回答的质量：
    {state.generation}
    
    找出问题并给出改进建议。
    """)
    return {"feedback": feedback}

# 改进节点
def improver(state):
    improved = llm.invoke(f"""
    原始任务: {state.task}
    反馈: {state.feedback}
    请改进回答。
    """)
    return {"generation": improved}

# 判断是否结束
def should_continue(state):
    if state.iteration >= 3 or "满意" in state.feedback:
        return "end"
    return "improve"
```

### 7. 什么是 Human-in-the-Loop？如何实现？
**答案**：
Human-in-the-Loop 是 Agent 在执行过程中暂停并等待人类确认的机制。

**LangGraph 实现**：
```python
from langgraph.checkpoint import MemorySaver

# 使用持久化支持中断
graph = StateGraph(AgentState)
graph.add_node("agent", agent_node)
graph.add_node("action", action_node)
graph.add_node("human_review", human_review_node)

# 在 action 节点前中断
graph.add_edge("agent", "human_review")
graph.add_edge("human_review", "action")

# 编译时设置中断点
app = graph.compile(
    checkpointer=MemorySaver(),
    interrupt_before=["action"]  # 执行 action 前等待人类确认
)

# 运行
config = {"configurable": {"thread_id": "1"}}
events = app.stream({"messages": [user_input]}, config)

# 人类确认后继续
for event in app.stream(None, config):
    print(event)
```

---

## 🎯 Level 4：专家题

### 8. LangGraph 的 Persistence 和 Checkpoint 机制？
**答案**：

**Persistence（持久化）**：
- 保存 Agent 运行状态到外部存储
- 支持断点恢复
- 支持历史状态回溯

**Checkpoint（检查点）**：
```python
from langgraph.checkpoint import MemorySaver, PostgresSaver

# 内存持久化
memory = MemorySaver()

# 数据库持久化
db_saver = PostgresSaver.from_conn_string("postgresql://...")

app = graph.compile(checkpointer=db_saver)

# 每次运行自动保存状态
config = {"configurable": {"thread_id": "user-session-1"}}
for event in app.stream(input, config):
    pass

# 从指定检查点恢复
checkpoint_config = {"configurable": {"thread_id": "user-session-1", "checkpoint_id": "xxx"}}
app.stream(None, checkpoint_config)
```

### 9. 如何设计一个生产级的 LangGraph Agent？
**答案**：

**架构设计**：
```text
用户输入
    ↓
输入处理 (Input Guard)
    ↓
意图识别 (Intent Classifier)
    ├── 简单问答 → 直接回复
    ├── 知识查询 → RAG Agent
    ├── 复杂任务 → Plan & Execute Agent
    └── 多步任务 → Multi-Agent Supervisor
    ↓
执行 (Agent Loop)
    ├── LLM 推理
    ├── 工具调用
    │   ├── 搜索工具
    │   ├── 数据库查询
    │   ├── API 调用
    │   └── 代码执行
    └── 结果验证
    ↓
输出处理 (Output Guard)
    ↓
最终响应
```

**关键设计**：
1. **超时控制**：设置 Agent 最大执行时间
2. **重试机制**：工具调用失败重试
3. **错误处理**：Agent 异常时降级
4. **日志追踪**：完整记录 Agent 执行链路
5. **限流控制**：避免工具调用过度
6. **安全检查**：阻止危险操作

---

## 📖 学习资源

### 推荐项目
- [LangGraph 官方文档](https://langchain-ai.github.io/langgraph/)
- [LangGraph 101](https://github.com/langchain-ai/langgraph-101) - 官方教程
- [LangGraph 示例](https://github.com/langchain-ai/langgraphjs)

### 学习路径
1. 基础概念：StateGraph、Node、Edge
2. Agent Loop：ReAct 模式实现
3. 多 Agent 模式：Collaboration、Supervisor
4. 高级模式：Reflection、Human-in-the-Loop
5. 生产部署：Persistence、Streaming、Monitoring