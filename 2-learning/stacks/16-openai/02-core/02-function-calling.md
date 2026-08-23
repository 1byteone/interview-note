# Function Calling

## 概述

Function Calling 让模型能够调用外部工具和 API。模型本身不执行代码，而是生成符合工具定义的 JSON 参数，由开发者执行实际调用并将结果返回给模型。

## 核心流程

```
用户提问
    │
    ▼
模型判断是否需要调用工具
    │
    ├── 不需要 ──► 直接生成文本回复
    │
    └── 需要 ──► 生成工具调用请求（tool_calls）
                      │
                      ▼
                  开发者执行实际函数
                      │
                      ▼
                  将结果返回给模型
                      │
                      ▼
                  模型生成最终回复
```

## Tool 描述编写

### 基本结构

```python
tools = [
    {
        "type": "function",
        "function": {
            "name": "get_product_info",
            "description": "获取商品详细信息，包括价格、库存、规格等",
            "parameters": {
                "type": "object",
                "properties": {
                    "product_id": {
                        "type": "string",
                        "description": "商品 ID",
                    },
                    "fields": {
                        "type": "array",
                        "items": {"type": "string"},
                        "description": "需要获取的字段列表，如 price, stock, specs",
                    },
                },
                "required": ["product_id"],
                "additionalProperties": False,
            },
        },
    }
]
```

### 参数描述最佳实践

1. **清晰的描述**: 让模型理解参数含义
2. **合理的必填字段**: 只设置真正必要的字段为 required
3. **枚举值约束**: 使用 enum 限制可选值
4. **类型正确**: 使用 JSON Schema 支持的类型

## 基本调用示例

```python
from openai import OpenAI

client = OpenAI(api_key="sk-xxx")

# 定义工具
tools = [
    {
        "type": "function",
        "function": {
            "name": "search_products",
            "description": "搜索商品，支持关键词和筛选条件",
            "parameters": {
                "type": "object",
                "properties": {
                    "keyword": {"type": "string", "description": "搜索关键词"},
                    "category": {
                        "type": "string",
                        "enum": ["electronics", "clothing", "food", "books"],
                        "description": "商品类别",
                    },
                    "min_price": {"type": "number", "description": "最低价格"},
                    "max_price": {"type": "number", "description": "最高价格"},
                    "page": {"type": "integer", "description": "页码"},
                },
                "required": ["keyword"],
                "additionalProperties": False,
            },
        },
    }
]

# 发起请求
response = client.chat.completions.create(
    model="gpt-4o-mini",
    messages=[
        {"role": "system", "content": "你是 AI 商城助手，可以使用工具搜索商品。"},
        {"role": "user", "content": "搜索 500-1000 元之间的电子产品"},
    ],
    tools=tools,
    tool_choice="auto",
)

message = response.choices[0].message
print(message.tool_calls)
```

## 多工具调用

### 定义多个工具

```python
tools = [
    {
        "type": "function",
        "function": {
            "name": "get_order_info",
            "description": "查询订单信息",
            "parameters": {
                "type": "object",
                "properties": {
                    "order_id": {"type": "string", "description": "订单号"},
                },
                "required": ["order_id"],
                "additionalProperties": False,
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "get_product_info",
            "description": "查询商品信息",
            "parameters": {
                "type": "object",
                "properties": {
                    "product_id": {"type": "string", "description": "商品 ID"},
                },
                "required": ["product_id"],
                "additionalProperties": False,
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "cancel_order",
            "description": "取消订单",
            "parameters": {
                "type": "object",
                "properties": {
                    "order_id": {"type": "string", "description": "订单号"},
                    "reason": {"type": "string", "description": "取消原因"},
                },
                "required": ["order_id", "reason"],
                "additionalProperties": False,
            },
        },
    },
]
```

### 处理多轮工具调用

```python
def process_tool_calls(message, messages):
    """处理工具调用并返回结果"""
    if not message.tool_calls:
        return message.content

    # 将 assistant 的消息加入对话
    messages.append(message)

    for tool_call in message.tool_calls:
        function_name = tool_call.function.name
        arguments = json.loads(tool_call.function.arguments)

        # 执行实际函数
        if function_name == "search_products":
            result = search_products(**arguments)
        elif function_name == "get_order_info":
            result = get_order_info(**arguments)
        elif function_name == "cancel_order":
            result = cancel_order(**arguments)
        else:
            result = {"error": f"Unknown function: {function_name}"}

        # 将工具结果返回给模型
        messages.append({
            "role": "tool",
            "tool_call_id": tool_call.id,
            "content": json.dumps(result, ensure_ascii=False),
        })

    # 模型根据工具结果生成最终回复
    final_response = client.chat.completions.create(
        model="gpt-4o-mini",
        messages=messages,
        tools=tools,
    )
    return final_response.choices[0].message.content
```

## 实战：AI 商城查询订单 + 商品

### 模拟函数实现

```python
import json
from datetime import datetime, timedelta

# 模拟数据库
MOCK_ORDERS = {
    "ORD001": {
        "order_id": "ORD001",
        "user_id": "U1001",
        "products": [
            {"product_id": "P001", "name": "iPhone 15 Pro", "price": 7999, "quantity": 1},
            {"product_id": "P002", "name": "AirPods Pro", "price": 1799, "quantity": 1},
        ],
        "total": 9798,
        "status": "shipped",
        "created_at": "2025-06-15 10:30:00",
    },
    "ORD002": {
        "order_id": "ORD002",
        "user_id": "U1001",
        "products": [
            {"product_id": "P003", "name": "MacBook Air M3", "price": 8999, "quantity": 1},
        ],
        "total": 8999,
        "status": "pending",
        "created_at": "2025-06-18 14:20:00",
    },
}

MOCK_PRODUCTS = {
    "P001": {"name": "iPhone 15 Pro", "price": 7999, "stock": 50, "category": "手机"},
    "P002": {"name": "AirPods Pro", "price": 1799, "stock": 200, "category": "耳机"},
    "P003": {"name": "MacBook Air M3", "price": 8999, "stock": 30, "category": "笔记本"},
}


def get_order_info(order_id: str) -> dict:
    """查询订单信息"""
    order = MOCK_ORDERS.get(order_id)
    if not order:
        return {"error": f"订单 {order_id} 不存在"}
    return order


def get_product_info(product_id: str) -> dict:
    """查询商品信息"""
    product = MOCK_PRODUCTS.get(product_id)
    if not product:
        return {"error": f"商品 {product_id} 不存在"}
    return product
```

### 完整调用流程

```python
def mall_assistant(query: str) -> str:
    """AI 商城智能助手"""
    messages = [
        {"role": "system", "content": "你是 AI 商城助手，可以帮助用户查询订单和商品信息。"},
        {"role": "user", "content": query},
    ]

    response = client.chat.completions.create(
        model="gpt-4o-mini",
        messages=messages,
        tools=tools,
        tool_choice="auto",
    )

    message = response.choices[0].message
    return process_tool_calls(message, messages)


# 使用示例
print(mall_assistant("查询我的订单 ORD001 中的商品信息"))
# 模型会先调用 get_order_info 获取订单，再根据结果决定是否调用 get_product_info
```

## 重要参数

### tool_choice

| 值 | 行为 |
|----|------|
| `"auto"` | 模型自行决定是否调用工具 |
| `"required"` | 强制模型调用至少一个工具 |
| `{"type": "function", "function": {"name": "search_products"}}` | 强制调用指定工具 |

### parallel_tool_calls

默认为 `True`，模型可以同时发起多个工具调用。设置为 `False` 则强制顺序调用。

## 常见问题

### 1. 工具调用失败
- 检查参数类型是否匹配 JSON Schema
- 确保 tool_call_id 正确传递
- 验证工具返回结果格式

### 2. 循环调用
- 设置最大工具调用轮次
- 检测重复调用模式

```python
MAX_TOOL_ROUNDS = 5

def safe_process(messages, tools, max_rounds=MAX_TOOL_ROUNDS):
    for _ in range(max_rounds):
        response = client.chat.completions.create(
            model="gpt-4o-mini",
            messages=messages,
            tools=tools,
        )
        message = response.choices[0].message
        if not message.tool_calls:
            return message.content
        # 处理工具调用...
    return "工具调用次数过多，请简化请求"
```

### 3. 工具选择错误
- 优化工具名称和描述
- 使用更具体的参数约束
- 考虑使用 `tool_choice` 指定工具