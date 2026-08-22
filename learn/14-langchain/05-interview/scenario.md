# LangChain 面试场景题

> 问题描述、原因分析、解决方案、代码示例

---

## 场景一：Agent 循环超时

### 问题描述

Agent 在执行复杂任务时陷入无限循环，反复调用同一个 Tool 或在不同 Tool 之间来回切换，最终超过 `max_iterations` 或 `max_execution_time` 限制，抛出异常或返回不完整的回答。

### 原因分析

- **Tool 返回结果不明确**：Tool 返回的信息不足以让 LLM 判断任务是否完成，导致 Agent 反复调用
- **Prompt 引导不足**：Agent 的 System Prompt 没有明确说明何时终止循环
- **Tool 粒度过细**：单个 Tool 能力过弱，Agent 需要多次组合才能完成任务
- **LLM 幻觉**：模型在多次循环后产生幻觉，认为需要继续调用已经调用过的 Tool

### 解决方案

1. 设置合理的 `max_iterations` 和 `max_execution_time` 上限
2. 配置 `early_stopping_method="generate"`，超限时强制生成最终答案
3. 优化 Tool 的 `description`，让 LLM 更精准地判断何时使用
4. 增加 Tool 的聚合度，减少不必要的多次调用
5. 使用 `handle_parsing_errors=True` 自动处理解析失败后的重试
6. 在 Prompt 中明确给出终止条件示例

### 代码示例

```python
from langchain.agents import AgentExecutor, create_react_agent
from langchain_openai import ChatOpenAI
from langchain.tools import tool
from langchain.prompts import PromptTemplate

@tool
def search_web(query: str) -> str:
    """搜索网络信息，每次调用获取一页搜索结果。查询应尽量具体。"""
    # 实际调用搜索 API
    return f"搜索结果：{query} 的相关信息"

tools = [search_web]

agent = create_react_agent(
    llm=ChatOpenAI(model="gpt-4o", temperature=0),
    tools=tools,
    prompt=PromptTemplate.from_template(
        "你是一个智能助手。请使用工具回答问题。\n\n"
        "重要规则：\n"
        "1. 每次选择一个工具调用\n"
        "2. 如果工具返回的结果已经足够回答用户问题，立即输出 Final Answer\n"
        "3. 不要重复调用同一个工具超过 2 次\n"
        "4. 如果工具调用失败，最多重试 1 次\n\n"
        "工具列表: {tools}\n"
        "工具名称: {tool_names}\n"
        "用户输入: {input}\n"
        "历史: {agent_scratchpad}"
    )
)

# 安全配置：限制循环次数 + 超时保护
agent_executor = AgentExecutor(
    agent=agent,
    tools=tools,
    max_iterations=5,               # 最多 5 次循环
    max_execution_time=30,          # 最多 30 秒
    early_stopping_method="generate",  # 超限时生成回答
    handle_parsing_errors=True,     # 自动处理解析错误
    return_intermediate_steps=True  # 返回中间步骤，便于调试
)

try:
    result = agent_executor.invoke({"input": "请搜索人工智能的最新发展"})
    print(result["output"])
except Exception as e:
    print(f"Agent 执行超时或出错: {e}")
    # 可以降级处理：直接调用 LLM 回答
    fallback_llm = ChatOpenAI(model="gpt-4o", temperature=0)
    print(fallback_llm.invoke(f"请简要回答：{result.get('input', '')}"))
```

---

## 场景二：Tool 调用失败

### 问题描述

Agent 调用 Tool 时出现异常，如 API 返回 500 错误、网络超时、参数格式错误等。如果不处理，整个 Agent 循环会崩溃，无法正常返回结果。

### 原因分析

- **外部服务不可用**：Tool 依赖的第三方 API 服务故障
- **参数校验失败**：LLM 生成的参数不符合 Tool 的 `args_schema` 约束
- **网络超时**：Tool 执行时间超过设置的超时阈值
- **权限不足**：Tool 需要认证信息但未提供
- **Tool 内部错误**：Tool 实现中存在 Bug 或未处理的异常

### 解决方案

1. 在 Tool 内部实现异常捕获，返回人类可读的错误信息而非抛异常
2. 配置 `handle_tool_errors=True`，让 AgentExecutor 自动捕获 Tool 异常
3. 设置 Tool 的超时时间，避免单个 Tool 调用阻塞整个循环
4. 实现重试机制：在 Tool 内部使用 `tenacity` 库自动重试
5. 提供 Fallback Tool：当主 Tool 失败时，使用备选 Tool 提供兜底信息

### 代码示例

```python
from langchain.tools import BaseTool, tool
from langchain.pydantic_v1 import BaseModel, Field
from typing import Type, Optional
from tenacity import retry, stop_after_attempt, wait_exponential
import time

# 方案一：Tool 内部容错
@tool
def query_api(endpoint: str) -> str:
    """查询外部 API 接口"""
    try:
        # 模拟 API 调用
        response = simulated_api_call(endpoint)
        return f"API 响应: {response}"
    except ConnectionError as e:
        return f"API 连接失败: {str(e)}。建议稍后重试。"
    except ValueError as e:
        return f"参数错误: {str(e)}。请检查输入参数格式。"
    except Exception as e:
        return f"未知错误: {str(e)}。已记录日志，请联系管理员。"

# 方案二：自动重试机制
@retry(
    stop=stop_after_attempt(3),           # 最多重试 3 次
    wait=wait_exponential(multiplier=1, min=1, max=10),  # 指数退避
    reraise=True
)
def unstable_api_call(param: str) -> str:
    """不稳定 API 调用，带自动重试"""
    response = requests.get(
        f"https://api.example.com/data?q={param}",
        timeout=5  # 单次超时 5 秒
    )
    response.raise_for_status()
    return response.text

@tool
def search_with_retry(query: str) -> str:
    """搜索信息，内置自动重试和超时保护"""
    try:
        result = unstable_api_call(query)
        return f"搜索结果: {result}"
    except Exception as e:
        return f"搜索服务暂时不可用: {str(e)}"

# 方案三：Fallback Tool 链
class PrimarySearchTool(BaseTool):
    name: str = "primary_search"
    description: str = "主搜索工具"
    def _run(self, query: str) -> str:
        raise ConnectionError("主搜索服务宕机")

class FallbackSearchTool(BaseTool):
    name: str = "fallback_search"
    description: str = "备选搜索工具"
    def _run(self, query: str) -> str:
        return f"备选搜索结果: {query}"

# AgentExecutor 配置：自动处理 Tool 错误
agent_executor = AgentExecutor(
    agent=agent,
    tools=[PrimarySearchTool(), FallbackSearchTool()],
    max_iterations=5,
    handle_tool_errors=True,  # 自动捕获 Tool 异常，不中断循环
    max_execution_time=30
)
```

---

## 场景三：记忆泄漏

### 问题描述

多个用户共享同一个 Memory 实例，导致用户 A 的对话历史泄漏给用户 B，出现跨会话的隐私安全问题。或者 Memory 中积累了过多无效信息，导致 Agent 回答质量下降。

### 原因分析

- **全局单例 Memory**：在 Web 应用中使用单例 Memory 对象，所有请求共享
- **未按 session_id 隔离**：没有为每个会话创建独立的 `ChatMessageHistory`
- **Memory 清理缺失**：长期运行未清理过期会话，内存持续增长
- **敏感信息存储**：Memory 中存储了密码、Token 等敏感信息

### 解决方案

1. 使用 `RunnableWithMessageHistory` 配合 `get_session_history` 回调，按 session_id 隔离
2. 使用 Redis 或数据库持久化会话历史，替代内存存储
3. 实现会话过期自动清理机制（TTL）
4. 在保存到 Memory 前，过滤掉敏感信息字段
5. 使用 `ConversationSummaryMemory` 限制历史大小，避免无限增长

### 代码示例

```python
from langchain_core.runnables.history import RunnableWithMessageHistory
from langchain_community.chat_message_histories import RedisChatMessageHistory
from langchain.memory import ConversationBufferMemory
from datetime import datetime, timedelta
import time

# 错误写法：全局单例 Memory（导致泄漏）
# WRONG_MEMORY = ConversationBufferMemory(memory_key="chat_history")  # 不要这样写！

# 正确写法：按 session_id 隔离
store: Dict[str, ChatMessageHistory] = {}

def get_session_history(session_id: str) -> ChatMessageHistory:
    """每个 session_id 独立存储，互不干扰"""
    if session_id not in store:
        store[session_id] = ChatMessageHistory()
    return store[session_id]

# 更优方案：使用 Redis 存储
def get_redis_session_history(session_id: str) -> RedisChatMessageHistory:
    """Redis 存储，支持 TTL 自动清理"""
    return RedisChatMessageHistory(
        session_id=session_id,
        url="redis://localhost:6379/0",
        ttl=3600,  # 1 小时自动过期
        key_prefix="langchain:session:"
    )

# 敏感信息过滤
class SafeMemory(ConversationBufferMemory):
    """过滤敏感信息的 Memory"""
    SENSITIVE_KEYS = ["password", "token", "secret", "api_key"]

    def save_context(self, inputs: Dict[str, Any], outputs: Dict[str, Any]) -> None:
        clean_inputs = {
            k: v for k, v in inputs.items()
            if k not in self.SENSITIVE_KEYS
        }
        clean_outputs = {
            k: "***" if any(key in str(k).lower() for key in self.SENSITIVE_KEYS) else v
            for k, v in outputs.items()
        }
        super().save_context(clean_inputs, clean_outputs)

# 结合 LCEL 的安全会话管理
chain_with_history = RunnableWithMessageHistory(
    prompt | model | output_parser,
    get_session_history=get_redis_session_history,
    input_messages_key="question",
    history_messages_key="history"
)

# 使用示例：用户 A 和用户 B 完全隔离
result_a = chain_with_history.invoke(
    {"question": "我的密码是 123456"},
    config={"configurable": {"session_id": "user_a"}}
)
result_b = chain_with_history.invoke(
    {"question": "刚才的用户说了什么密码？"},
    config={"configurable": {"session_id": "user_b"}}
)
# result_b 不会包含用户 A 的密码信息 ✅
```

---

## 场景四：上下文窗口超限

### 问题描述

长对话或复杂任务中，Agent 的 Prompt 超过了 LLM 的上下文窗口限制（如 GPT-4 的 128K Tokens），导致模型无法正常处理请求，抛出 Token 超限异常或「遗忘」早期对话内容。

### 原因分析

- **历史累积**：`ConversationBufferMemory` 无限制累积历史消息
- **Agent 中间步骤过多**：ReAct 循环中每次迭代的 Thought/Action/Observation 都追加到 Prompt
- **Tool 返回结果过大**：Tool 返回大量文本（如全文搜索结果），迅速占满上下文
- **多文档 RAG**：检索阶段召回过多文档片段，超出 Token 容量

### 解决方案

1. 使用 `ConversationSummaryMemory` 或 `ConversationTokenBufferMemory` 限制历史大小
2. 设置 `max_iterations` 限制 Agent 循环次数
3. 对 Tool 返回结果进行截断或摘要
4. 使用 `VectorStoreRetrieverMemory` 按相关性召回历史，而非全部加载
5. 在 Prompt 中设置 Token 预算，按照优先级分配 Token 空间
6. 使用 `trim_messages` 工具自动裁剪超出限制的消息

### 代码示例

```python
from langchain.memory import (
    ConversationTokenBufferMemory,
    ConversationSummaryMemory
)
from langchain_openai import ChatOpenAI
from langchain_core.messages import trim_messages
from langchain.tools import tool

# 方案一：Token 限制的 Memory
token_budget_memory = ConversationTokenBufferMemory(
    llm=ChatOpenAI(model="gpt-4o"),
    max_token_limit=2000,  # 历史最多保留 2000 Tokens
    memory_key="chat_history",
    return_messages=True
)

# 方案二：摘要 Memory（适合极长对话）
summary_memory = ConversationSummaryMemory(
    llm=ChatOpenAI(model="gpt-4o", temperature=0),
    max_token_limit=500,  # 超过 500 触发总结
    memory_key="chat_history"
)

# 方案三：Tool 返回结果截断
@tool
def search_web_truncated(query: str) -> str:
    """搜索网络信息，结果自动截断到 500 字符"""
    raw_result = actual_search(query)
    max_chars = 500
    if len(raw_result) > max_chars:
        return raw_result[:max_chars] + "...(已截断)"
    return raw_result

# 方案四：自动裁剪消息工具
def get_trimmed_history(session_id: str, max_tokens: int = 4000):
    """获取裁剪后的历史消息"""
    history = store.get(session_id, [])
    trimmed = trim_messages(
        messages=history,
        max_tokens=max_tokens,
        strategy="last",       # 保留最新的消息
        token_counter=ChatOpenAI(model="gpt-4o").get_num_tokens_from_messages
    )
    return trimmed

# 方案五：完整的安全 Agent 配置
safe_agent_executor = AgentExecutor(
    agent=agent,
    tools=[search_web_truncated],  # 使用截断 Tool
    max_iterations=5,              # 限制循环次数
    max_execution_time=30,
    early_stopping_method="generate",
    handle_parsing_errors=True,
    return_intermediate_steps=False  # 不返回中间步骤，节省 Token
)
```