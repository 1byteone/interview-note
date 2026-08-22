# Tools（工具）

> 👶→🎯 入门到进阶 | 预计阅读：30 分钟

大语言模型的知识截止于训练数据，无法实时查询数据库、调用 API 或操作文件系统。**Tool（工具）** 就是给 LLM 装上"手脚"，让它能主动与外部系统交互。

---

## 1. @tool 装饰器 —— 自定义工具

LangChain 提供 `@tool` 装饰器，将任意 Python 函数快速包装成工具。

```python
from langchain_core.tools import tool

@tool
def calculate_discount(price: float, discount_rate: float) -> float:
    """计算商品折扣后的价格。
    
    Args:
        price: 商品原价
        discount_rate: 折扣率，例如 0.8 表示 8 折
    
    Returns:
        折扣后的价格
    """
    return round(price * discount_rate, 2)

@tool
def get_shipping_fee(weight: float, city: str) -> str:
    """根据商品重量和收货城市计算运费。
    
    Args:
        weight: 商品重量，单位 kg
        city: 收货城市名称
    
    Returns:
        运费说明
    """
    fee_table = {
        "北京": 8, "上海": 8, "广州": 10, "深圳": 10,
        "其他": 15
    }
    base_fee = fee_table.get(city, 15)
    extra = max(0, weight - 3) * 2  # 超过 3kg 每公斤加 2 元
    total = base_fee + extra
    return f"配送至{city}，运费 ¥{total}（含首重 {base_fee} 元）"

# 测试工具
print(calculate_discount.invoke({"price": 100, "discount_rate": 0.8}))
print(get_shipping_fee.invoke({"weight": 5, "city": "广州"}))
```

**关键点**：
- 函数名即工具名，LLM 通过名称识别工具
- 函数的参数注解和 docstring 会被 LLM 理解，用于决定何时调用以及传入什么参数
- 返回值的类型提示也很重要，帮助 LLM 理解结果

---

## 2. 内置工具集

LangChain 提供丰富的内置工具，覆盖搜索、数据库、文件系统等常见场景。

### 2.1 Tavily Search —— 联网搜索

```python
from langchain_community.tools.tavily_search import TavilySearchResults

# 需要设置 TAVILY_API_KEY 环境变量
search_tool = TavilySearchResults(
    max_results=3,
    description="搜索互联网上的最新信息"
)

# 搜索示例
results = search_tool.invoke("2025 年最受欢迎的笔记本电脑")
for r in results:
    print(f"- {r['title']}: {r['content'][:100]}...")
```

### 2.2 SQL 查询工具

```python
from langchain_community.tools.sql_database.tool import QuerySQLDataBaseTool
from langchain_community.utilities.sql_database import SQLDatabase

# 连接数据库（示例：SQLite）
db = SQLDatabase.from_uri("sqlite:///mall_orders.db")
sql_tool = QuerySQLDataBaseTool(db=db)

# 查询订单
result = sql_tool.invoke("SELECT COUNT(*) FROM orders WHERE status = 'pending'")
print(f"待处理订单数: {result}")
```

### 2.3 文件系统工具

```python
from langchain_community.tools.file_management import (
    WriteFileTool,
    ReadFileTool,
    ListDirectoryTool
)

write_tool = WriteFileTool()
read_tool = ReadFileTool()
list_tool = ListDirectoryTool()

# 写入订单报告
write_tool.invoke({
    "file_path": "./reports/daily_orders.md",
    "text": "# 日订单报告\n\n总订单数: 156\n待发货: 23\n已完成: 128"
})

# 读取报告
content = read_tool.invoke({"file_path": "./reports/daily_orders.md"})
print(content)
```

---

## 3. 工具绑定与 Function Calling

将工具绑定到 LLM 后，模型会根据用户输入自动判断是否需要调用工具，以及调用哪个工具——这就是 **Function Calling**。

```python
from langchain_openai import ChatOpenAI
from langchain_core.messages import HumanMessage

# 1. 定义工具
@tool
def get_order_status(order_id: str) -> str:
    """根据订单号查询订单状态。
    
    Args:
        order_id: 订单号，格式如 ORD20240801001
    """
    # 模拟数据库查询
    orders_db = {
        "ORD20240801001": {"status": "已发货", "logistics": "顺丰快递 SF123456"},
        "ORD20240801002": {"status": "待支付", "logistics": None},
        "ORD20240801003": {"status": "已完成", "logistics": "中通快递 ZT789012"},
    }
    order = orders_db.get(order_id)
    if not order:
        return f"未找到订单 {order_id}"
    msg = f"订单 {order_id} 状态：{order['status']}"
    if order["logistics"]:
        msg += f"，物流信息：{order['logistics']}"
    return msg

@tool
def get_product_info(product_name: str) -> str:
    """查询商品详细信息。
    
    Args:
        product_name: 商品名称
    """
    products = {
        "MacBook Air": "MacBook Air M3 芯片，8 核 CPU，10 核 GPU，15.3 英寸，¥10499 起",
        "iPhone 15 Pro": "iPhone 15 Pro A17 Pro 芯片，4800 万像素，钛金属设计，¥8999 起",
    }
    return products.get(product_name, f"未找到商品: {product_name}")

# 2. 绑定工具到 LLM
llm = ChatOpenAI(model="gpt-4o-mini", temperature=0)
llm_with_tools = llm.bind_tools([get_order_status, get_product_info])

# 3. 自动工具调用
messages = [HumanMessage(content="帮我查一下订单 ORD20240801001 的状态")]
response = llm_with_tools.invoke(messages)

# 检查是否有工具调用
if response.tool_calls:
    for tool_call in response.tool_calls:
        tool_name = tool_call["name"]
        tool_args = tool_call["args"]
        print(f"调用工具: {tool_name}({tool_args})")
        
        # 手动执行工具
        if tool_name == "get_order_status":
            result = get_order_status.invoke(tool_args)
            print(f"工具结果: {result}")
```

---

## 4. Agent —— 自动执行工具

Agent 比手动处理工具调用更进一步——它自动决定调用哪个工具、解析结果、决定下一步动作，直到得出最终答案。

```python
from langchain.agents import create_tool_calling_agent, AgentExecutor
from langchain.prompts import ChatPromptTemplate

# 1. 收集工具
tools = [get_order_status, get_product_info, calculate_discount, get_shipping_fee]

# 2. 创建 Agent
prompt = ChatPromptTemplate.from_messages([
    ("system", "你是 AI 商城智能助手。你可以查询订单、商品信息、计算折扣和运费。"
               "请根据用户的问题，自主判断需要使用哪些工具来完成回答。"),
    ("human", "{input}"),
    ("placeholder", "{agent_scratchpad}"),
])

agent = create_tool_calling_agent(llm, tools, prompt)
agent_executor = AgentExecutor(
    agent=agent,
    tools=tools,
    verbose=True,
    handle_parsing_errors=True
)

# 3. 测试 Agent
queries = [
    "我想买 MacBook Air，运费到北京多少钱？",
    "我的订单 ORD20240801001 到了吗？",
    "iPhone 15 Pro 打 9 折后多少钱？",
]

for query in queries:
    print(f"\n{'='*50}")
    print(f"用户: {query}")
    response = agent_executor.invoke({"input": query})
    print(f"助手: {response['output']}")
```

---

## 5. 实战：AI 商城查询订单工具

综合运用以上知识，构建一个完整的订单查询工具集。

```python
from datetime import datetime, timedelta
import random

# ========== 定义商城工具 ==========

@tool
def query_order(order_id: str) -> dict:
    """查询订单详情，包括状态、商品、金额、物流。
    
    Args:
        order_id: 订单号
    """
    # 模拟数据
    statuses = ["待支付", "待发货", "已发货", "已完成", "已取消"]
    order = {
        "order_id": order_id,
        "status": random.choice(statuses),
        "total_amount": round(random.uniform(100, 10000), 2),
        "items": [
            {"name": "商品A", "quantity": 1, "price": 2999},
            {"name": "商品B", "quantity": 2, "price": 399},
        ],
        "create_time": (datetime.now() - timedelta(days=random.randint(1, 30))).isoformat(),
    }
    return order

@tool
def query_logistics(waybill_no: str) -> str:
    """查询物流轨迹。
    
    Args:
        waybill_no: 运单号
    """
    traces = [
        "2025-10-30 18:00 已揽收",
        "2025-10-30 22:00 到达【广州分拣中心】",
        "2025-10-31 08:00 离开【广州分拣中心】",
        "2025-10-31 14:00 到达【北京分拣中心】",
        "2025-10-31 16:00 派送中，预计今日送达",
    ]
    return "\n".join(traces)

@tool
def cancel_order(order_id: str, reason: str) -> str:
    """取消订单。
    
    Args:
        order_id: 订单号
        reason: 取消原因
    """
    return f"订单 {order_id} 已取消。原因：{reason}。退款将在 1-3 个工作日到账。"

# ========== 构建 Agent ==========

tools = [query_order, query_logistics, cancel_order]

mall_agent = create_tool_calling_agent(
    llm=ChatOpenAI(model="gpt-4o-mini", temperature=0),
    tools=tools,
    prompt=ChatPromptTemplate.from_messages([
        ("system", "你是 AI 商城的订单助手。你可以查询订单、追踪物流、取消订单。"
                   "请用友好语气回答，并给出清晰的订单信息。"),
        ("human", "{input}"),
        ("placeholder", "{agent_scratchpad}"),
    ])
)

executor = AgentExecutor(
    agent=mall_agent,
    tools=tools,
    verbose=True,
    max_iterations=5,  # 防止无限循环
    early_stopping_method="force"
)

# 测试
response = executor.invoke({
    "input": "我查一下订单 ORD20240801001，帮我看看到哪了，如果还没发货就取消掉"
})
print(response["output"])
```

---

## 总结

| 概念 | 说明 | 使用场景 |
|-----|------|---------|
| `@tool` 装饰器 | 将函数包装为工具 | 自定义业务工具 |
| 内置工具集 | Tavily、SQL、FileSystem 等 | 搜索、数据库、文件操作 |
| Function Calling | LLM 自动选择工具并传参 | 智能路由到工具 |
| Agent | 自动多步工具调用 | 复杂任务编排 |

**下一步**：学习 Agent 的深入用法，探索 ReAct 模式和多 Agent 协作。