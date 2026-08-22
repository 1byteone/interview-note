# LangChain 速查卡 📋

> 面试前30分钟扫一遍，一页纸覆盖核心考点

## 🏷️ 核心概念速记

| 概念 | 一句话解释 | 常见陷阱 |
|------|-----------|----------|
| LCEL (LangChain Expression Language) | 声明式管道操作符 `|`，将组件串联成链 | 不是所有组件都能直接 |，需要实现 Runnable 接口/可调用对象 |
| Chain | 串联多个 LLM 调用的抽象，各组件按序执行 | 旧版 Chain 类在 v0.3 逐步废弃，转向 LCEL |
| Runnable | LCEL 统一接口，所有组件(模型/提示/检索器)都实现 Runnable | RunnableParallel 并发执行，RunnablePassthrough 透传 |
| Memory | 存储历史对话，提供上下文 | 内存膨胀：长对话 Token 超限，需剪裁或摘要 |
| Buffer Memory | ConversationBufferMemory 存储全部对话 | 最简单但最贵(令牌无限增长)，长对话不可用 |
| 摘要 Memory | ConversationSummaryMemory 定期用 LLM 总结 | 额外一次 LLM 调用，开销+延迟；总结可能丢失细节 |
| 向量存储 Memory | 基于向量数据库(如 Chroma/Pinecone) 检索历史 | 需要 embedding 模型，适合长期记忆 |
| Tool | 工具定义：LLM 可调用的函数(计算器/搜索/API) | 工具描述要清晰，参数 JSON Schema 准确，否则 LLM 选错工具 |
| Agent | 推理循环：LLM 决定调用哪个工具 → 执行 → 反馈结果 → 再决定 | 循环可能无限，最大迭代次数要设，防死循环 |
| ReAct Agent | 推理(Reasoning) + 行动(Action) 交替，观察→思考→行动→观察 | 依赖 prompt 模板，指令不清晰会导致乱循环 |
| LangGraph | 有状态图编排，节点(函数)+边(条件跳转)，支持循环/分支 | 比 LangChain Chain 更灵活，但 State 管理更复杂 |
| 回调 (Callback) | 事件监听：LLM 开始/结束、链开始/结束、工具调用 | 异步回调需 AsyncCallbackHandler，同步异步不混用 |

## 🔧 常用命令/API

```python
# LCEL 管道示例（核心考点）
from langchain_core.output_parsers import StrOutputParser
from langchain_core.prompts import ChatPromptTemplate
from langchain_openai import ChatOpenAI

# 定义管道：提示词 → 模型 → 输出解析器
prompt = ChatPromptTemplate.from_template("用{language}写一段{topic}代码")
model = ChatOpenAI(model="gpt-4o-mini", temperature=0)
output_parser = StrOutputParser()

# LCEL 管道
chain = prompt | model | output_parser

# 执行
result = chain.invoke({"language": "Python", "topic": "工厂设计模式"})
print(result)
```

```python
# Agent 定义（ReAct 模式）
from langchain.agents import create_react_agent, AgentExecutor
from langchain_community.tools import tool
from langchain_openai import ChatOpenAI

@tool
def calculate(expression: str) -> str:
    """计算数学表达式，如 '2 + 3 * 4'"""
    return str(eval(expression))  # 注意 eval 安全，示例简化

@tool
def get_weather(city: str) -> str:
    """查询指定城市的天气"""
    return f"{city}: 25°C, 晴"

tools = [calculate, get_weather]
prompt = hub.pull("hwchase17/react")   # ReAct 标准提示模板
agent = create_react_agent(
    llm=ChatOpenAI(model="gpt-4o-mini"),
    tools=tools,
    prompt=prompt
)
agent_executor = AgentExecutor(
    agent=agent,
    tools=tools,
    verbose=True,
    max_iterations=5,          # 防止无限循环
    handle_parsing_errors=True # 解析错误时自动重试
)

result = agent_executor.invoke({"input": "北京天气怎么样？"})
```

```python
# Memory 搭配 Chain
from langchain.memory import ConversationBufferMemory
from langchain_core.chat_history import InMemoryChatMessageHistory
from langchain_core.runnables.history import RunnableWithMessageHistory

store = {}  # session_id → ChatMessageHistory

def get_session_history(session_id: str):
    if session_id not in store:
        store[session_id] = InMemoryChatMessageHistory()
    return store[session_id]

chain = prompt | model | output_parser
chain_with_history = RunnableWithMessageHistory(
    chain,
    get_session_history,
    input_messages_key="input",
    history_messages_key="history"
)

chain_with_history.invoke(
    {"input": "我叫小明"},
    config={"configurable": {"session_id": "user-001"}}
)
chain_with_history.invoke(
    {"input": "我叫什么名字？"},
    config={"configurable": {"session_id": "user-001"}}
)
# 输出: 你叫小明
```

```python
# LangGraph 节点示例
from langgraph.graph import StateGraph, MessageGraph
from typing import TypedDict, List

class GraphState(TypedDict):
    messages: List[str]
    next_step: str

def node_a(state: GraphState) -> GraphState:
    state["messages"].append("A processed")
    return state

def node_b(state: GraphState) -> GraphState:
    state["messages"].append("B processed")
    return state

def router(state: GraphState) -> str:
    return "node_b" if len(state["messages"]) < 5 else "end"

graph = StateGraph(GraphState)
graph.add_node("node_a", node_a)
graph.add_node("node_b", node_b)
graph.set_entry_point("node_a")
graph.add_conditional_edges("node_a", router)
graph.add_edge("node_b", "node_a")
```

## 🎯 面试高频 TOP10

1. **Q: LCEL 是什么？优势？** **A:** 声明式管道操作符 `|`，链式串联组件；优势：流式支持、并行执行、自动重试、可追踪、模块化组合。
2. **Q: Agent 循环原理？** **A:** LLM 输出解析 → 选择工具(含参数) → 执行工具返回结果 → 将结果回喂给 LLM → 重复直到得出最终答案或达到最大迭代次数。
3. **Q: Memory 有哪些类型？适用场景？** **A:** Buffer(对话短/简单)、Summary(长对话/降成本成本)、Vector Store(长期海量记忆)、ConversationTokenBuffer(按 token 截断)；场景：Buffer 限短对话，Summary 长对话，VectorStore 知识库。
4. **Q: Tool 如何设计？** **A:** @tool 装饰器 + 函数名+docstring 描述 + 参数类型注解和 JSON Schema；描述要精确(LLM 依赖它选工具)，参数名要语义化。
5. **Q: LangGraph vs Chain 区别？** **A:** Chain 是线性管道(无状态/无分支)；LangGraph 有状态图(节点+边)，支持循环/分支/条件路由，适合复杂工作流和 Agent 编排。
6. **Q: 怎么保证 Agent 不无限循环？** **A:** max_iterations(硬限制) + handle_parsing_errors + 检查工具调用结果(容错) + 中间步骤超时 + 降级回复。
7. **Q: Runnable 接口有哪些方法？** **A:** invoke(同步)、ainvoke(异步)、stream(流式)、batch(批量)；所有组件(模型/提示/检索器/输出解析器)统一实现 Runnable。
8. **Q: 回调可以做什么？** **A:** 日志记录、监控 Token 消耗、流式输出、中间结果展示、缓存、追踪；AsyncCallbackHandler 处理异步场景。
9. **Q: LangChain 的 Token 管理？** **A:** 用 get_num_tokens(text) 预先计算；Memory 按 token 截断；Callback 收集 token 用量；用 token buffer 限制上下文长度。
10. **Q: 如何做流式输出？** **A:** model.stream() 逐 Token 输出；用 RunnableGenerator 包装；前端配合 SSE 或 WebSocket 实时展示。

## ⚠️ 常见坑 & 最佳实践

| ❌ 坑 | ✅ 正确做法 |
|-------|------------|
| 忽视 Tool 描述质量，LLM 选错工具 | 精确描述工具做什么、输入参数含义及格式，多测试 |
| Memory 不限制，Token 超限崩溃 | 设置 max_token_limit，或使用 Summary/VectorStore memory |
| Agent 没有 max_iterations | 设置 max_iterations(5-15)，避免死循环和无限扣费 |
| 同步/异步回调混用 | 统一用异步(AsyncCallbackHandler)，避免事件循环阻塞 |
| 旧版 Chain 类与 LCEL 混用 | 新项目全用 LCEL，迁移旧 Chain 到 LCEL |
| 不加错误处理，工具调用异常 | 工具内部 try/except 兜底，返回友好错误信息给 LLM |
| 提示词硬编码，不可维护 | 提示词模板化，从 prompt 文件或 Hub 加载 |
| 忽略并行执行的机会 | 用 RunnableParallel 并发执行独立任务，减少总耗时 |

## 📐 架构设计要点

- **组件化**：提示词模板 → 模型 → 输出解析器 / 检索引擎，每步可独立替换。
- **可观测性**：LangSmith 追踪 + 回调日志 + Token 用量监控。
- **错误容忍**：Agent 自动重试(handle_parsing_errors) + 工具异常兜底 + 降级回复。
- **性能优化**：模型缓存 + 流式输出 + 并行执行(RunnableParallel) + 批量处理。
- **安全**：工具输入校验 + 敏感信息脱敏 + 速率限制 + 模型内容安全审核。

## 🔗 关联技术

- **RAG**：LangChain 是实现 RAG 的框架，支持文档加载/分割/检索/生成全链路。
- **OpenAI**：LangChain 默认模型后端，支持 Chat Completions / Embeddings / Function Calling。
- **向量数据库**：Chroma(本地)、Pinecone(云)、Weaviate(自托管)，作为 Memory 和 RAG 的存储层。
- **FastAPI**：LangChain 应用部署为 API 服务，流式响应 SSE。
- **LangGraph**：LangChain 的图编排扩展，处理复杂 Agent 和多步骤工作流。