# LangChain 面试深挖题

> 原理分析 + 代码示例

---

## 一、Agent 循环机制（ReAct Loop）

### 原理分析

ReAct（Reasoning + Acting）是 Agent 的核心循环机制，由以下步骤构成闭环：

1. **Thought（思考）**：LLM 分析当前问题，决定下一步行动方案。输出的思考内容包含对问题的推理过程和计划选择的理由。
2. **Action（行动）**：LLM 输出一个结构化的 Tool 调用指令，包含工具名称和参数。格式通常为 `Action: tool_name\nAction Input: {"key": "value"}`。
3. **Observation（观察）**：AgentExecutor 解析 Action，调用对应 Tool，将执行结果以 Observation 形式返回给 LLM。
4. **循环或终止**：LLM 根据 Observation 判断是否完成目标。若完成则输出 `Final Answer:`；否则继续 Thought→Action→Observation 循环。

AgentExecutor 负责整个循环的驱动逻辑：

- 调用 Agent 的 `plan()` 方法获取下一步动作
- 若输出包含 `Final Answer`，终止循环并返回结果
- 否则解析 Action，调用 Tool，将 Observation 拼接回 Prompt
- 检查 `max_iterations` 和 `max_execution_time` 限制，超限时触发 `early_stopping`

### 代码示例

```python
from langchain.agents import AgentExecutor, create_react_agent
from langchain_openai import ChatOpenAI
from langchain.tools import tool
from langchain.prompts import PromptTemplate

@tool
def search(query: str) -> str:
    """搜索指定信息"""
    return f"查询结果：{query} 的相关信息"

tools = [search]

# create_react_agent 内部自动构建 ReAct Prompt Template
agent = create_react_agent(
    llm=ChatOpenAI(model="gpt-4o", temperature=0),
    tools=tools,
    prompt=PromptTemplate.from_template(
        "你是一个助手。请使用工具回答问题。\n{tools}\n\n"
        "工具名称: {tool_names}\n"
        "用户输入: {input}\n"
        "历史: {agent_scratchpad}"
    )
)

agent_executor = AgentExecutor(
    agent=agent,
    tools=tools,
    max_iterations=5,          # 最大循环次数
    max_execution_time=30,     # 最大执行时间（秒）
    early_stopping_method="generate",  # 超限时强制生成回答
    handle_parsing_errors=True # 解析错误时自动重试
)

result = agent_executor.invoke({"input": "请搜索人工智能的最新发展"})
print(result["output"])
```

---

## 二、Tool 调用实现

### 原理分析

Tool 是 Agent 与外部世界交互的桥梁，其调用链如下：

**Function Calling 机制**：LangChain 的 Tool 底层依赖 LLM 的 Function Calling API。当 LLM 判定需要调用工具时，模型返回一个结构化的 JSON，包含函数名和参数，而非自然语言文本。LangChain 解析这个 JSON 后执行对应 Tool。

**@tool 装饰器**：将 Python 函数转化为 Tool 对象的快捷方式：
- 自动解析函数名作为 Tool 的 `name`
- 自动解析函数 docstring 作为 Tool 的 `description`
- 自动解析类型注解生成 `args_schema`（Pydantic 模型）
- 支持 `return_direct` 参数控制是否跳过 Agent 继续推理

**Tool 执行流程**：
1. Agent 生成 `Action: tool_name` 和 `Action Input: {args}`
2. AgentExecutor 查找匹配的 Tool
3. 验证参数是否符合 Tool 的 `args_schema`
4. 调用 Tool 的 `_run()` 或 `_arun()` 方法
5. 捕获异常，返回错误信息或触发重试机制
6. 将结果封装为 `Observation` 返回给 Agent

### 代码示例

```python
from langchain.tools import tool, BaseTool
from langchain.pydantic_v1 import BaseModel, Field
from typing import Type

# 方式一：@tool 装饰器
@tool
def calculate_expression(expression: str) -> str:
    """计算数学表达式的结果"""
    try:
        result = eval(expression, {"__builtins__": {}}, {})
        return f"计算结果: {result}"
    except Exception as e:
        return f"计算错误: {str(e)}"

# 方式二：自定义 Tool 类（更灵活）
class WeatherInput(BaseModel):
    city: str = Field(description="城市名称，如北京、上海")
    date: str = Field(description="日期，格式为 YYYY-MM-DD")

class WeatherTool(BaseTool):
    name: str = "weather_query"
    description: str = "查询指定城市在指定日期的天气情况"
    args_schema: Type[BaseModel] = WeatherInput

    def _run(self, city: str, date: str) -> str:
        # 实际逻辑：调用天气 API
        return f"{city} 在 {date} 的天气：晴，25°C"

    async def _arun(self, city: str, date: str) -> str:
        # 异步版本
        return self._run(city, date)

# 使用示例
weather_tool = WeatherTool()
result = weather_tool.invoke({"city": "北京", "date": "2026-08-22"})
print(result)
```

---

## 三、Memory 存储与检索

### 原理分析

Memory 是 LangChain 中维护对话历史的组件，其核心接口是 `BaseMemory`：

**BufferMemory（缓冲区记忆）**：
- 将消息按时间顺序存储在列表中，不做压缩或总结
- 每次调用时，将所有历史消息拼接为字符串或消息列表
- 优点：信息完整无丢失；缺点：Token 消耗随对话增长，受限于上下文窗口

**SummaryMemory（总结记忆）**：
- 当对话历史超过 Token 阈值时，触发 LLM 总结
- 保留总结后的摘要，丢弃原始历史
- 优点：节省 Token，可处理极长对话；缺点：细节丢失，总结本身也消耗 Token

**VectorStoreMemory（向量存储记忆）**：
- 将每条历史消息向量化后存入向量数据库
- 查询时，按语义相似度召回最相关的 k 条历史
- 优点：不受 Token 限制，可扩展到海量历史；缺点：召回质量依赖 Embedding 模型

**LCEL 中的 Memory 集成**：通过 `RunnableWithMessageHistory` 包装链，传入 `get_session_history` 回调函数，自动管理会话历史的加载和保存。

### 代码示例

```python
from langchain.memory import (
    ConversationBufferMemory,
    ConversationSummaryMemory,
    VectorStoreRetrieverMemory
)
from langchain_community.vectorstores import FAISS
from langchain_openai import OpenAIEmbeddings
from langchain.schema import BaseMemory
from langchain.memory.chat_memory import BaseChatMemory
from typing import Any, Dict, List

# 1. BufferMemory
buffer_memory = ConversationBufferMemory(
    memory_key="chat_history",
    return_messages=True  # 返回消息对象而非字符串
)

# 2. SummaryMemory
summary_memory = ConversationSummaryMemory(
    llm=ChatOpenAI(model="gpt-4o", temperature=0),
    memory_key="chat_history",
    max_token_limit=500  # 超过 500 Token 时触发总结
)

# 3. VectorStoreRetrieverMemory
vector_store = FAISS.from_texts(
    ["初始化占位文本"],
    OpenAIEmbeddings()
)
vs_memory = VectorStoreRetrieverMemory(
    retriever=vector_store.as_retriever(search_kwargs={"k": 3}),
    memory_key="chat_history",
    input_key="input"
)

# 4. 在 LCEL 中集成 Memory
from langchain_core.runnables.history import RunnableWithMessageHistory
from langchain_community.chat_message_histories import ChatMessageHistory

store = {}  # 会话存储

def get_session_history(session_id: str):
    if session_id not in store:
        store[session_id] = ChatMessageHistory()
    return store[session_id]

chain = prompt | model | output_parser
chain_with_history = RunnableWithMessageHistory(
    chain,
    get_session_history,
    input_messages_key="question",
    history_messages_key="history"
)

result = chain_with_history.invoke(
    {"question": "你好"},
    config={"configurable": {"session_id": "user_123"}}
)
```

---

## 四、LCEL 原理

### 原理分析

LCEL（LangChain Expression Language）是 LangChain 定义的声明式编程范式，用于构建可组合的 LLM 管道。

**Runnable 接口**：所有 LCEL 组件的统一抽象，核心方法包括：
- `invoke(input)`：单次调用，同步执行
- `batch(inputs)`：批量调用，内部优化并行度
- `stream(input)`：流式调用，逐 Token 返回
- `ainvoke` / `abatch` / `astream`：对应的异步版本

**管道运算符 `|`**：左操作数输出自动映射为右操作数输入。映射规则：
- 若右侧是 `Runnable`，直接将左侧输出传入
- 若右侧是 `RunnableBinding`，携带绑定参数传入
- 类型不匹配时抛出 `InputTypeError`

**惰性求值**：LCEL 管道在构建时只是建立 DAG（有向无环图），不会执行任何计算。只有在调用 `invoke()`、`batch()` 等触发方法时，才按拓扑顺序执行各节点。这种设计允许：
- 管道在构建时进行类型检查和优化
- 支持动态分支和条件路由
- 便于调试和可视化（LangSmith 追踪）

**组合原语**：
- `RunnableParallel`：并行执行多个分支，结果合并
- `RunnablePassthrough`：透传，不修改数据
- `RunnableAssign`：向数据流注入新字段
- `RunnableBranch`：条件路由
- `RunnableLambda`：将任意函数包装为 Runnable

### 代码示例

```python
from langchain_core.runnables import (
    RunnableParallel,
    RunnablePassthrough,
    RunnableAssign,
    RunnableBranch,
    RunnableLambda
)
from langchain_openai import ChatOpenAI
from langchain.prompts import ChatPromptTemplate
from langchain.schema import StrOutputParser
from operator import itemgetter

# 基础 LCEL 链
prompt = ChatPromptTemplate.from_template(
    "请用中文回答: {question}"
)
model = ChatOpenAI(model="gpt-4o", temperature=0)
output_parser = StrOutputParser()

chain = prompt | model | output_parser
result = chain.invoke({"question": "什么是 LCEL？"})

# RunnableParallel 并行执行
parallel_chain = RunnableParallel(
    answer=prompt | model | output_parser,
    length=RunnableLambda(lambda x: len(x["question"]))
)
result = parallel_chain.invoke({"question": "你好"})

# RunnableBranch 条件分支
def is_english(x: dict) -> bool:
    return x["question"][0].isascii()

english_prompt = ChatPromptTemplate.from_template("Answer in English: {question}")
chinese_prompt = ChatPromptTemplate.from_template("请用中文回答: {question}")

branch_chain = RunnableBranch(
    (is_english, english_prompt | model | output_parser),
    chinese_prompt | model | output_parser  # 默认分支
)
result = branch_chain.invoke({"question": "What is AI?"})

# 复杂管道：RAG 查询
retrieval_chain = {
    "context": retriever | RunnableLambda(
        lambda docs: "\n".join([d.page_content for d in docs])
    ),
    "question": RunnablePassthrough()
} | prompt | model | output_parser
```