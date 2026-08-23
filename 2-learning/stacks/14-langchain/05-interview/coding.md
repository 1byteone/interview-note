# LangChain 面试代码题

> 题目描述、代码实现、关键点说明

---

## 题目一：自定义 Tool

### 题目描述

实现一个自定义 Tool，用于查询用户信息。要求：
- 支持根据用户 ID 查询用户姓名、邮箱、角色
- 输入参数需经过 Pydantic 校验
- 包含同步和异步两种实现
- 当用户不存在时返回友好的错误提示

### 代码实现

```python
from langchain.tools import BaseTool
from langchain.pydantic_v1 import BaseModel, Field, validator
from typing import Type, Optional, Dict, Any
import asyncio

# 模拟用户数据库
USER_DB = {
    "u001": {"name": "张三", "email": "zhangsan@example.com", "role": "admin"},
    "u002": {"name": "李四", "email": "lisi@example.com", "role": "user"},
    "u003": {"name": "王五", "email": "wangwu@example.com", "role": "editor"},
}

# 输入参数 Schema
class UserQueryInput(BaseModel):
    user_id: str = Field(description="用户 ID，格式为 u 开头 + 三位数字，如 u001")
    include_email: bool = Field(default=False, description="是否返回邮箱信息")

    @validator("user_id")
    def validate_user_id(cls, v):
        if not v.startswith("u") or not v[1:].isdigit():
            raise ValueError("用户 ID 格式错误，应为 u001 格式")
        return v

class UserInfoTool(BaseTool):
    name: str = "user_info_query"
    description: str = "根据用户 ID 查询用户信息，包括姓名、邮箱和角色"
    args_schema: Type[BaseModel] = UserQueryInput

    def _run(self, user_id: str, include_email: bool = False) -> str:
        """同步执行方法"""
        user = USER_DB.get(user_id)
        if not user:
            return f"未找到用户: {user_id}，请确认用户 ID 是否正确"

        info = f"用户: {user['name']}, 角色: {user['role']}"
        if include_email:
            info += f", 邮箱: {user['email']}"
        return info

    async def _arun(self, user_id: str, include_email: bool = False) -> str:
        """异步执行方法"""
        # 模拟异步数据库查询
        await asyncio.sleep(0.1)
        return self._run(user_id, include_email)

# 使用 @tool 装饰器的简化版本
from langchain.tools import tool

@tool
def get_user_role(user_id: str) -> str:
    """查询用户角色。输入参数 user_id 为用户 ID，格式如 u001"""
    user = USER_DB.get(user_id)
    if not user:
        return f"未找到用户: {user_id}"
    return f"用户 {user['name']} 的角色是: {user['role']}"

# 测试
user_tool = UserInfoTool()
result = user_tool.invoke({"user_id": "u001", "include_email": True})
print(result)  # 输出: 用户: 张三, 角色: admin, 邮箱: zhangsan@example.com

# 参数校验失败示例
try:
    user_tool.invoke({"user_id": "invalid"})
except Exception as e:
    print(f"校验失败: {e}")
```

### 关键点说明

- **BaseTool 继承**：必须实现 `_run`（同步）和 `_arun`（异步）方法
- **args_schema**：使用 Pydantic 模型定义参数结构，自动获得校验和文档生成
- **validator 校验**：在 `@validator` 装饰器中实现自定义校验逻辑
- **description 重要性**：Tool 的 `description` 直接影响 LLM 是否选择该 Tool，务必清晰准确
- **错误处理**：Tool 应返回人类可读的错误信息，而非抛出异常

---

## 题目二：自定义 Memory

### 题目描述

实现一个基于文件的持久化 Memory，要求：
- 将对话历史保存到本地 JSON 文件
- 支持按 session_id 隔离不同会话
- 实现 `load_memory_variables` 和 `save_context` 方法
- 限制最大保存 100 条消息，超出时自动清理旧消息

### 代码实现

```python
from langchain.memory import BaseMemory
from langchain.schema import get_buffer_string
from typing import Dict, Any, List
import json
import os
from datetime import datetime
from collections import deque

class FilePersistentMemory(BaseMemory):
    """基于文件的持久化 Memory"""

    file_path: str
    session_id: str
    max_messages: int = 100
    memory_key: str = "chat_history"

    def __init__(self, file_path: str, session_id: str, **kwargs):
        super().__init__(**kwargs)
        self.file_path = file_path
        self.session_id = session_id
        self._ensure_file()

    @property
    def memory_variables(self) -> List[str]:
        return [self.memory_key]

    def _ensure_file(self):
        """确保文件存在"""
        os.makedirs(os.path.dirname(self.file_path), exist_ok=True)
        if not os.path.exists(self.file_path):
            with open(self.file_path, "w", encoding="utf-8") as f:
                json.dump({}, f)

    def _load_all_sessions(self) -> Dict:
        """加载所有会话数据"""
        with open(self.file_path, "r", encoding="utf-8") as f:
            return json.load(f)

    def _save_all_sessions(self, data: Dict):
        """保存所有会话数据"""
        with open(self.file_path, "w", encoding="utf-8") as f:
            json.dump(data, f, ensure_ascii=False, indent=2)

    def load_memory_variables(self, inputs: Dict[str, Any]) -> Dict[str, str]:
        """加载当前会话的历史"""
        data = self._load_all_sessions()
        messages = data.get(self.session_id, [])
        # 将消息列表转换为字符串
        history_str = get_buffer_string(messages)
        return {self.memory_key: history_str}

    def save_context(self, inputs: Dict[str, Any], outputs: Dict[str, Any]) -> None:
        """保存当前轮次的对话"""
        data = self._load_all_sessions()
        messages = data.get(self.session_id, [])

        # 构建消息对
        messages.append({
            "role": "human",
            "content": inputs.get("input", ""),
            "timestamp": datetime.now().isoformat()
        })
        messages.append({
            "role": "ai",
            "content": outputs.get("output", ""),
            "timestamp": datetime.now().isoformat()
        })

        # 限制消息数量，超出时删除最旧的消息
        if len(messages) > self.max_messages:
            messages = messages[-self.max_messages:]

        data[self.session_id] = messages
        self._save_all_sessions(data)

    def clear(self) -> None:
        """清空当前会话的历史"""
        data = self._load_all_sessions()
        if self.session_id in data:
            del data[self.session_id]
        self._save_all_sessions(data)

# 使用示例
memory = FilePersistentMemory(
    file_path="./data/chat_history.json",
    session_id="session_001",
    max_messages=100
)

# 保存对话
memory.save_context(
    {"input": "你好，我叫张三"},
    {"output": "你好张三！很高兴认识你。"}
)

# 加载历史
history = memory.load_memory_variables({})
print(history["chat_history"])
```

### 关键点说明

- **BaseMemory 抽象方法**：必须实现 `memory_variables` 属性、`load_memory_variables` 和 `save_context` 方法
- **持久化策略**：选择文件/数据库/Redis 等存储后端，确保数据安全
- **消息上限**：使用 `deque` 或列表切片实现 FIFO 淘汰策略
- **时间戳记录**：为每条消息记录时间戳，便于后续分析
- **session_id 隔离**：不同会话的数据互不干扰

---

## 题目三：Agent 定义

### 题目描述

实现一个智能客服 Agent，要求：
- 支持查询订单状态
- 支持查询物流信息
- 支持处理退款申请
- 当遇到无法回答的问题时优雅降级
- 限制最大循环次数为 10

### 代码实现

```python
from langchain.agents import AgentExecutor, create_react_agent
from langchain_openai import ChatOpenAI
from langchain.tools import tool, BaseTool
from langchain.prompts import PromptTemplate
from langchain.pydantic_v1 import BaseModel, Field
from typing import Optional

# 模拟订单数据库
ORDER_DB = {
    "ORD001": {"status": "已发货", "product": "手机", "amount": 5999},
    "ORD002": {"status": "已签收", "product": "电脑", "amount": 12999},
    "ORD003": {"status": "退款中", "product": "耳机", "amount": 999},
}

@tool
def query_order_status(order_id: str) -> str:
    """查询订单状态。输入参数 order_id 为订单号，格式如 ORD001"""
    order = ORDER_DB.get(order_id)
    if not order:
        return f"未找到订单: {order_id}"
    return f"订单 {order_id} 状态: {order['status']}, 商品: {order['product']}"

@tool
def query_logistics(order_id: str) -> str:
    """查询物流信息。输入参数 order_id 为订单号"""
    order = ORDER_DB.get(order_id)
    if not order or order["status"] not in ("已发货", "已签收"):
        return f"订单 {order_id} 暂无物流信息"
    return f"订单 {order_id} 的物流信息: 已到达 [城市] 分拣中心"

# 退款工具：带参数校验
class RefundInput(BaseModel):
    order_id: str = Field(description="订单号，格式如 ORD001")
    reason: str = Field(description="退款原因说明")

@tool(args_schema=RefundInput)
def apply_refund(order_id: str, reason: str) -> str:
    """申请退款。需要提供订单号和退款原因"""
    order = ORDER_DB.get(order_id)
    if not order:
        return f"未找到订单: {order_id}"
    if order["status"] == "已签收":
        return f"订单 {order_id} 已签收，请联系人工客服处理退款"
    # 更新订单状态
    ORDER_DB[order_id]["status"] = "退款中"
    return f"订单 {order_id} 退款申请已提交，退款金额: {order['amount']} 元，预计 3-5 个工作日到账"

tools = [query_order_status, query_logistics, apply_refund]

# 定制 Agent Prompt
system_prompt = PromptTemplate.from_template(
    """你是一个智能客服助手。请使用工具为用户提供帮助。

可用的工具：
{tools}

工具名称: {tool_names}

处理规则：
1. 用户查询订单时，先查询订单状态再查询物流信息
2. 退款申请需要确认用户身份（模拟：直接受理）
3. 如果所有工具都无法解决问题，礼貌告知用户转接人工客服
4. 回答要简洁友好

用户输入: {input}

历史对话: {agent_scratchpad}"""
)

agent = create_react_agent(
    llm=ChatOpenAI(model="gpt-4o", temperature=0.3),
    tools=tools,
    prompt=system_prompt
)

agent_executor = AgentExecutor(
    agent=agent,
    tools=tools,
    max_iterations=10,
    max_execution_time=60,
    early_stopping_method="generate",
    handle_parsing_errors=True,
    verbose=True  # 打印中间步骤，便于调试
)

# 测试
result = agent_executor.invoke({
    "input": "我订单 ORD001 现在到哪了？我想申请退款"
})
print(result["output"])
```

### 关键点说明

- **create_react_agent**：自动构建 ReAct 格式的 Prompt Template
- **Tool 组合设计**：每个 Tool 职责单一，通过 Agent 串联完成复杂任务
- **Prompt 规则注入**：在 System Prompt 中明确处理规则，引导 Agent 行为
- **AgentExecutor 配置**：`max_iterations`、`handle_parsing_errors`、`verbose` 等参数是生产环境的关键配置
- **优雅降级**：当 Tool 无法解决问题时，Agent 应给出明确的兜底方案

---

## 题目四：LCEL 链

### 题目描述

使用 LCEL 构建一个 RAG（检索增强生成）问答链，要求：
- 使用 LCEL 管道运算符 `|` 连接各组件
- 包含文档检索、上下文拼接、Prompt 格式化、LLM 调用、输出解析
- 支持流式输出
- 添加日志记录中间件

### 代码实现

```python
from langchain_core.runnables import (
    RunnableParallel,
    RunnablePassthrough,
    RunnableLambda,
    RunnableAssign
)
from langchain_core.output_parsers import StrOutputParser
from langchain_openai import ChatOpenAI, OpenAIEmbeddings
from langchain.prompts import ChatPromptTemplate
from langchain_community.vectorstores import FAISS
from langchain.schema.document import Document
from operator import itemgetter
from typing import Dict, Any
import time

# 1. 准备数据
documents = [
    Document(
        page_content="LangChain 是一个用于构建 LLM 应用的框架，支持链式调用和 Agent 机制。",
        metadata={"source": "intro"}
    ),
    Document(
        page_content="LCEL 是 LangChain 表达式语言，提供声明式管道运算符 | 来组合组件。",
        metadata={"source": "lcel"}
    ),
    Document(
        page_content="RAG 是检索增强生成技术，先检索相关知识再生成回答。",
        metadata={"source": "rag"}
    ),
]

# 2. 构建向量存储
vectorstore = FAISS.from_documents(documents, OpenAIEmbeddings())
retriever = vectorstore.as_retriever(search_kwargs={"k": 2})

# 3. 定义 Prompt
prompt = ChatPromptTemplate.from_messages([
    ("system", "你是一个 AI 助手。请基于以下上下文回答问题。\n\n上下文：{context}"),
    ("human", "{question}")
])

# 4. 定义模型
model = ChatOpenAI(model="gpt-4o", temperature=0)

# 5. 输出解析器
output_parser = StrOutputParser()

# 6. LCEL 日志中间件
def log_step(step_name: str):
    """记录步骤执行时间和输入"""
    def wrapper(input_data):
        start = time.time()
        print(f"[{step_name}] 开始执行，输入类型: {type(input_data).__name__}")
        result = input_data  # 透传
        elapsed = time.time() - start
        print(f"[{step_name}] 完成，耗时: {elapsed:.3f}s")
        return result
    return RunnableLambda(wrapper)

# 7. 构建 LCEL 链
# 方式一：简洁写法
def format_docs(docs):
    return "\n\n".join([d.page_content for d in docs])

simple_rag_chain = (
    {
        "context": retriever | RunnableLambda(format_docs),
        "question": RunnablePassthrough()
    }
    | prompt
    | model
    | output_parser
)

# 方式二：详细写法（带日志）
rag_chain = (
    RunnableAssign({
        "context": (
            retriever
            | log_step("检索")
            | RunnableLambda(format_docs)
            | log_step("格式化上下文")
        )
    })
    | log_step("Prompt 构建")
    | prompt
    | log_step("LLM 调用")
    | model
    | log_step("输出解析")
    | output_parser
)

# 8. 使用
# 同步调用
result = rag_chain.invoke("什么是 LCEL？")
print(f"结果: {result}")

# 批量调用
results = rag_chain.batch([
    "什么是 LangChain？",
    "RAG 是什么技术？"
])
for r in results:
    print(f"批量结果: {r}")

# 流式输出
for chunk in rag_chain.stream("什么是 LCEL？"):
    print(chunk, end="", flush=True)
print()

# 9. 复杂 RAG 链：返回更多信息
full_rag_chain = (
    RunnableParallel(
        answer=rag_chain,
        context=RunnablePassthrough() | retriever | RunnableLambda(
            lambda docs: [d.page_content for d in docs]
        ),
        sources=RunnablePassthrough() | retriever | RunnableLambda(
            lambda docs: [d.metadata.get("source", "unknown") for d in docs]
        )
    )
)

full_result = full_rag_chain.invoke("什么是 LCEL？")
print(f"答案: {full_result['answer']}")
print(f"来源文档: {full_result['context']}")
print(f"来源: {full_result['sources']}")
```

### 关键点说明

- **管道运算符 `|`**：LCEL 的核心，将 Runnable 组件串联成数据流
- **RunnableAssign**：向数据流中注入新字段，不覆盖原有字段，适合构建上下文
- **RunnableParallel**：并行执行多个分支，结果合并为字典，适合同时返回答案和来源
- **流式输出**：LCEL 链天然支持 `stream()` 方法，无需额外配置
- **日志中间件**：通过 `RunnableLambda` 包装函数实现，不影响数据流
- **类型安全**：LCEL 在构建时检查类型签名，避免运行时类型错误