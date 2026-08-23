# Agent 架构与多 Agent 协作

> 面向 Python 后端开发者的 LangChain Agent 进阶教程，覆盖 ReAct、OpenAI Tools、Structured Chat 三种 Agent 架构，以及多 Agent 协作与电商客服实战。

---

## 1. Agent 架构概述

LangChain 提供了多种 Agent 架构，分别适用于不同的场景。核心区别在于 LLM 的推理方式与工具调用接口。

| 架构类型 | 推理方式 | 适用场景 | 版本 |
|----------|----------|----------|------|
| **ReAct** | Thought→Action→Observation 循环 | 通用场景，适用任何 LLM | 经典 |
| **OpenAI Tools** | 原生 Function Calling | 仅 OpenAI 兼容模型 | v2+ |
| **Structured Chat** | 多参数工具调用 | 需要结构化参数的场景 | 经典 |

---

## 2. ReAct Agent 架构

ReAct（Reasoning + Acting）是 Agent 的核心范式，其循环流程如下：

```
Thought（推理）→ Action（工具调用）→ Observation（观察结果）
    ↓ 循环，直到任务完成
Final Answer（最终回答给用户）
```

### 2.1 ReAct 循环示例

```python
from langchain.agents import create_react_agent, AgentExecutor
from langchain_openai import ChatOpenAI
from langchain import hub
from langchain.tools import tool
from typing import Optional

@tool
def search_product(query: str) -> str:
    """根据关键词搜索商品，返回商品列表"""
    # 模拟商品搜索
    products = {
        "iphone 15": "iPhone 15 128GB 白色 ￥5999，库存充足",
        "macbook pro": "MacBook Pro 14寸 M3 ￥12999，库存充足",
        "airpods pro": "AirPods Pro 2代 ￥1799，库存不足",
    }
    return products.get(query.lower(), f"未找到商品：{query}")

@tool
def check_stock(product_id: str) -> str:
    """查询商品库存状态"""
    stock = {"P001": "充足", "P002": "充足", "P003": "缺货"}
    return stock.get(product_id, "未知商品")

@tool
def query_order(order_id: str) -> str:
    """查询订单状态"""
    orders = {
        "20240801": "已发货，预计 3 天后送达",
        "20240802": "已支付，待发货",
        "20240803": "已取消",
    }
    return orders.get(order_id, "订单号不存在")

tools = [search_product, check_stock, query_order]
llm = ChatOpenAI(model="gpt-4", temperature=0)

# 使用 LangChain Hub 中的标准 ReAct prompt
prompt = hub.pull("hwchase17/react")

agent = create_react_agent(llm, tools, prompt)
agent_executor = AgentExecutor(
    agent=agent,
    tools=tools,
    verbose=True,          # 打印思考过程
    handle_parsing_errors=True,  # 容错处理
    max_iterations=5,      # 最大循环次数，防止死循环
)

result = agent_executor.invoke({
    "input": "查一下订单 20240801 的状态，再看看 iPhone 15 多少钱"
})
print(result["output"])
```

### 2.2 ReAct 循环的思考过程

当运行上述代码时，`verbose=True` 会输出类似以下的思考链：

```
> Entering new AgentExecutor chain...
Thought: 用户需要查询订单状态和商品价格，我需要调用两个工具。
首先查询订单 20240801 的状态。

Action: query_order
Action Input: "20240801"
Observation: 已发货，预计 3 天后送达

Thought: 订单已发货。接下来查询 iPhone 15 的价格。

Action: search_product
Action Input: "iphone 15"
Observation: iPhone 15 128GB 白色 ￥5999，库存充足

Thought: 我已经得到了两个信息，可以回答用户了。

Final Answer: 您的订单 20240801 已发货，预计 3 天后送达。iPhone 15 128GB 白色版售价 ￥5999，目前库存充足。
> Finished chain.
```

---

## 3. OpenAI Tools Agent

OpenAI Tools Agent 利用模型的 Function Calling 能力，相比 ReAct 不需要解析文本格式的 Action/Action Input，而是直接返回结构化工具调用。

```python
from langchain.agents import create_openai_tools_agent, AgentExecutor
from langchain_openai import ChatOpenAI
from langchain.prompts import ChatPromptTemplate

# 定义工具（同上）
tools = [search_product, check_stock, query_order]

llm = ChatOpenAI(model="gpt-4", temperature=0)

prompt = ChatPromptTemplate.from_messages([
    ("system", "你是一个电商助手，可以帮助用户查询商品、库存和订单信息。"),
    ("placeholder", "{chat_history}"),
    ("human", "{input}"),
    ("placeholder", "{agent_scratchpad}"),
])

agent = create_openai_tools_agent(llm, tools, prompt)
agent_executor = AgentExecutor(agent=agent, tools=tools, verbose=True)

result = agent_executor.invoke({
    "input": "帮我查订单 20240802，再看看 MacBook Pro 有货吗"
})
print(result["output"])
```

**ReAct vs OpenAI Tools 对比：**

| 对比维度 | ReAct | OpenAI Tools |
|----------|-------|-------------|
| 解析方式 | 文本正则解析 | 原生 JSON 解析 |
| 解析错误率 | 较高（格式敏感） | 低（结构化输出） |
| 兼容性 | 所有 LLM | 仅 OpenAI 兼容模型 |
| 多工具并行 | 不支持 | 支持（单次可调用多个工具） |
| 调试难度 | 易（文本可读） | 中（JSON 结构） |

---

## 4. AgentExecutor

`AgentExecutor` 是 Agent 的运行时引擎，负责调度 Agent 的循环执行。

### 4.1 核心参数

```python
agent_executor = AgentExecutor(
    agent=agent,
    tools=tools,
    verbose=True,
    max_iterations=10,         # 最大推理迭代次数
    max_execution_time=60,     # 最大执行时间（秒）
    early_stopping_method="generate",  # 超时后的行为：generate/force
    handle_parsing_errors=True,        # 解析错误时自动重试
    return_intermediate_steps=True,    # 返回中间步骤（用于调试）
)
```

### 4.2 自定义错误处理

```python
from langchain.agents import AgentExecutor
from langchain.agents.output_parsers import ReActSingleInputOutputParser

class RobustAgentExecutor(AgentExecutor):
    """带重试机制的 Agent 执行器"""

    def _should_retry(self, error: Exception) -> bool:
        """判断是否应该重试"""
        error_str = str(error)
        # 工具调用错误：重试
        if "ToolError" in error_str:
            return True
        # 解析错误：重试
        if "ParsingError" in error_str:
            return True
        # 速率限制：等待后重试
        if "RateLimit" in error_str:
            return True
        return False
```

---

## 5. 多 Agent 协作

当单个 Agent 能力不足时，可以采用多 Agent 协作架构。常见模式包括：

### 5.1 路由模式（Router Pattern）

一个 Supervisor Agent 将请求路由到不同的 Specialist Agent。

```python
from langchain.agents import create_react_agent, AgentExecutor
from langchain_openai import ChatOpenAI
from langchain.tools import tool

# 定义 Specialist Agent 作为工具
@tool
def search_agent(query: str) -> str:
    """搜索商品信息，包括商品详情、价格、规格等"""
    # 这里是 Specialist Agent 的逻辑
    return f"[搜索Agent] 搜索结果：{query}"

@tool
def order_agent(query: str) -> str:
    """处理订单相关查询，包括订单状态、物流等"""
    return f"[订单Agent] 订单信息：{query}"

@tool
def after_sales_agent(query: str) -> str:
    """处理售后问题，包括退换货、退款等"""
    return f"[售后Agent] 售后信息：{query}"

# Supervisor Agent 根据用户问题路由到不同 Specialist
supervisor_tools = [search_agent, order_agent, after_sales_agent]
llm = ChatOpenAI(model="gpt-4", temperature=0)

prompt = ChatPromptTemplate.from_messages([
    ("system", "你是一个客服主管，请根据用户问题分派给对应的专业 Agent。"),
    ("human", "{input}"),
    ("placeholder", "{agent_scratchpad}"),
])

supervisor = create_openai_tools_agent(llm, supervisor_tools, prompt)
supervisor_executor = AgentExecutor(
    agent=supervisor,
    tools=supervisor_tools,
    verbose=True,
)
```

### 5.2 编排模式（Orchestration Pattern）

一个 Orchestrator Agent 将复杂任务分解为子任务，分配给多个 Agent 并行执行，最后汇总结果。

```
用户问题
    │
    ▼
Orchestrator（任务分解）
    │
    ├── Agent A：商品搜索 ──┐
    ├── Agent B：库存查询 ──┤
    ├── Agent C：价格比对 ──┤
    └── Agent D：生成推荐 ──┘
    │
    ▼
汇总结果 → 返回用户
```

---

## 6. 实战：AI 商城客服 Agent

综合上述知识，构建一个完整的 AI 商城客服 Agent，支持搜索、订单、售后三大功能。

```python
import json
from datetime import datetime
from langchain.agents import create_openai_tools_agent, AgentExecutor
from langchain_openai import ChatOpenAI
from langchain.prompts import ChatPromptTemplate
from langchain.tools import tool
from typing import Optional

# ── 工具定义 ──

@tool
def search_products(query: str, category: Optional[str] = None) -> str:
    """搜索商品，支持按分类筛选。query: 搜索关键词，category: 商品分类（可选）"""
    # 模拟搜索
    results = [
        {"name": "iPhone 15 Pro", "price": 7999, "category": "手机"},
        {"name": "MacBook Air M3", "price": 8999, "category": "笔记本"},
        {"name": "AirPods Pro 2", "price": 1799, "category": "配件"},
    ]
    filtered = [r for r in results if query.lower() in r["name"].lower()]
    if category:
        filtered = [r for r in filtered if r["category"] == category]
    return json.dumps(filtered, ensure_ascii=False)

@tool
def query_order_status(order_id: str) -> str:
    """查询订单状态。order_id: 订单编号"""
    # 模拟订单查询
    orders = {
        "O20240801001": {"status": "已发货", "logistics": "顺丰快递 SF1234567890"},
        "O20240802002": {"status": "已支付", "logistics": "待发货"},
        "O20240803003": {"status": "已完成", "logistics": "已签收"},
    }
    order = orders.get(order_id)
    if not order:
        return json.dumps({"error": "订单不存在"})
    return json.dumps(order, ensure_ascii=False)

@tool
def create_return_request(order_id: str, reason: str) -> str:
    """创建售后退货申请。order_id: 订单编号, reason: 退货原因"""
    return json.dumps({
        "return_id": f"R{datetime.now().strftime('%Y%m%d%H%M%S')}",
        "order_id": order_id,
        "status": "审核中",
        "message": "退货申请已提交，24小时内会有客服联系您",
    }, ensure_ascii=False)

@tool
def get_current_time() -> str:
    """获取当前时间"""
    return datetime.now().strftime("%Y-%m-%d %H:%M:%S")

# ── Agent 构建 ──

tools = [search_products, query_order_status, create_return_request, get_current_time]
llm = ChatOpenAI(model="gpt-4", temperature=0)

prompt = ChatPromptTemplate.from_messages([
    ("system",
     "你是一个 AI 商城客服助手。你可以：\n"
     "1. 搜索商品信息（search_products）\n"
     "2. 查询订单状态（query_order_status）\n"
     "3. 处理售后退货（create_return_request）\n"
     "4. 获取当前时间（get_current_time）\n\n"
     "请根据用户问题选择合适的工具，并提供友好、准确的回答。"),
    ("placeholder", "{chat_history}"),
    ("human", "{input}"),
    ("placeholder", "{agent_scratchpad}"),
])

agent = create_openai_tools_agent(llm, tools, prompt)
agent_executor = AgentExecutor(
    agent=agent,
    tools=tools,
    verbose=True,
    max_iterations=8,
    return_intermediate_steps=True,
)

# ── 使用示例 ──

queries = [
    "我想买一部 iPhone，有什么推荐吗？",
    "我的订单 O20240801001 到哪了？",
    "我要退货，订单号 O20240803003，质量有问题",
]

for query in queries:
    print(f"\n{'='*60}")
    print(f"用户：{query}")
    result = agent_executor.invoke({"input": query, "chat_history": []})
    print(f"助手：{result['output']}")
```

### 6.1 生产化建议

| 关注点 | 建议方案 |
|--------|----------|
| **工具超时** | 为每个工具调用添加 `timeout` 参数，防止长时间阻塞 |
| **重试策略** | 工具调用失败时，Agent 自动重试 1-2 次后切换工具 |
| **审计日志** | 记录每次工具调用的输入、输出、耗时，用于问题排查 |
| **Token 限制** | 监控 Agent 循环的 Token 消耗，设置 `max_iterations` 上限 |
| **安全防护** | 敏感操作（退款、改地址）需要二次确认 |

---

## 总结

- **ReAct Agent** 适用所有 LLM，通过文本推理循环实现工具调用，适合通用场景
- **OpenAI Tools Agent** 利用 Function Calling，解析更稳定、支持多工具并行
- **AgentExecutor** 提供迭代控制、错误处理、中间步骤追踪等运行时能力
- **多 Agent 协作** 通过路由/编排模式将复杂任务分解，适合大型系统
- 生产环境需要关注容错、审计、安全等工程化问题

---

> 下一篇：[02-callbacks-and-tracing.md](./02-callbacks-and-tracing.md) — Callbacks 事件机制与 LangSmith 追踪调试