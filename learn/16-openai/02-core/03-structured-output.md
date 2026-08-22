# Structured Outputs

## 概述

Structured Outputs 是 OpenAI 提供的功能，通过 JSON Schema 约束模型输出格式，确保返回结构化的 JSON 数据。相比传统的 JSON mode，Structured Outputs 提供更严格的格式保证。

## 核心概念

### JSON Schema

JSON Schema 是一种描述 JSON 数据结构的标准格式。OpenAI 使用它来定义模型输出的结构约束。

```json
{
  "name": "product_recommendation",
  "schema": {
    "type": "object",
    "properties": {
      "products": {
        "type": "array",
        "items": {
          "type": "object",
          "properties": {
            "name": {"type": "string"},
            "price": {"type": "number"},
            "rating": {"type": "number"},
            "tags": {
              "type": "array",
              "items": {"type": "string"}
            }
          },
          "required": ["name", "price", "rating"],
          "additionalProperties": false
        }
      },
      "total_count": {"type": "integer"},
      "category": {"type": "string"}
    },
    "required": ["products", "total_count"],
    "additionalProperties": false
  }
}
```

## 基本使用

### 使用 response_format 参数

```python
from openai import OpenAI

client = OpenAI(api_key="sk-xxx")

response = client.chat.completions.create(
    model="gpt-4o-mini",
    messages=[
        {"role": "system", "content": "你是一个商品推荐助手。"},
        {"role": "user", "content": "推荐 3 款 5000 元以内的手机"},
    ],
    response_format={
        "type": "json_schema",
        "json_schema": {
            "name": "recommendation_response",
            "strict": True,
            "schema": {
                "type": "object",
                "properties": {
                    "recommendations": {
                        "type": "array",
                        "items": {
                            "type": "object",
                            "properties": {
                                "name": {"type": "string"},
                                "brand": {"type": "string"},
                                "price": {"type": "number"},
                                "rating": {"type": "number"},
                                "features": {
                                    "type": "array",
                                    "items": {"type": "string"},
                                },
                                "recommendation_reason": {"type": "string"},
                            },
                            "required": [
                                "name", "brand", "price",
                                "rating", "features",
                                "recommendation_reason",
                            ],
                            "additionalProperties": False,
                        },
                    },
                    "search_summary": {"type": "string"},
                },
                "required": ["recommendations", "search_summary"],
                "additionalProperties": False,
            },
        },
    },
)

import json
result = json.loads(response.choices[0].message.content)
print(json.dumps(result, ensure_ascii=False, indent=2))
```

## Pydantic 集成

### 定义模型

```python
from pydantic import BaseModel
from typing import List, Optional
from openai import OpenAI

client = OpenAI(api_key="sk-xxx")


class Product(BaseModel):
    name: str
    brand: str
    price: float
    rating: float
    features: List[str]
    recommendation_reason: str


class RecommendationResponse(BaseModel):
    recommendations: List[Product]
    search_summary: str
    total_count: int
```

### 使用 Pydantic 生成 Schema

```python
def get_schema(model_class) -> dict:
    """将 Pydantic 模型转换为 JSON Schema"""
    schema = model_class.model_json_schema()
    # 删除不必要的顶层字段
    return {
        "name": model_class.__name__,
        "strict": True,
        "schema": {
            "type": "object",
            "properties": schema["properties"],
            "required": schema.get("required", []),
            "additionalProperties": False,
        },
    }


def get_recommendations(query: str) -> RecommendationResponse:
    """获取结构化推荐结果"""
    response = client.chat.completions.create(
        model="gpt-4o-mini",
        messages=[
            {"role": "system", "content": "你是 AI 商城商品推荐助手。"},
            {"role": "user", "content": query},
        ],
        response_format={
            "type": "json_schema",
            "json_schema": get_schema(RecommendationResponse),
        },
    )

    return RecommendationResponse.model_validate_json(
        response.choices[0].message.content
    )


# 使用
result = get_recommendations("推荐适合学生用的笔记本电脑，预算 5000")
for product in result.recommendations:
    print(f"{product.name} - {product.price}元 - {product.recommendation_reason}")
```

## 响应格式校验

### 手动校验

```python
from jsonschema import validate, ValidationError

# 定义 Schema
schema = {
    "type": "object",
    "properties": {
        "name": {"type": "string"},
        "price": {"type": "number", "minimum": 0},
        "category": {"type": "string"},
    },
    "required": ["name", "price"],
}

# 校验
def validate_response(data: dict) -> bool:
    try:
        validate(instance=data, schema=schema)
        return True
    except ValidationError as e:
        print(f"校验失败: {e}")
        return False
```

### 自动重试

```python
def robust_structured_call(
    messages: list,
    schema: dict,
    max_retries: int = 3,
) -> dict:
    """带重试的结构化输出调用"""
    for attempt in range(max_retries):
        try:
            response = client.chat.completions.create(
                model="gpt-4o-mini",
                messages=messages,
                response_format={
                    "type": "json_schema",
                    "json_schema": schema,
                },
            )
            data = json.loads(response.choices[0].message.content)
            # 验证结构
            if "error" not in data:
                return data
        except (json.JSONDecodeError, KeyError) as e:
            if attempt == max_retries - 1:
                raise
            print(f"第 {attempt + 1} 次尝试失败: {e}，重试中...")
```

## 实战：API 响应解析

### 商城订单查询响应

```python
from pydantic import BaseModel, Field
from typing import List, Optional
from datetime import datetime


class OrderItem(BaseModel):
    product_id: str
    product_name: str
    quantity: int
    unit_price: float
    subtotal: float


class OrderResponse(BaseModel):
    order_id: str
    status: str  # pending, paid, shipped, delivered, cancelled
    items: List[OrderItem]
    total_amount: float
    shipping_address: str
    created_at: str
    estimated_delivery: Optional[str] = None
    tracking_number: Optional[str] = None


class OrderAnalysis(BaseModel):
    order_summary: str
    total_items: int
    total_amount: float
    status_description: str
    next_steps: List[str]
    recommendations: Optional[List[str]] = None


def analyze_order(order_data: dict) -> OrderAnalysis:
    """分析订单信息并生成结构化摘要"""
    messages = [
        {
            "role": "system",
            "content": "你是一个 AI 商城订单分析助手。"
            "分析订单数据并生成结构化摘要。",
        },
        {
            "role": "user",
            "content": f"请分析以下订单：\n{json.dumps(order_data, ensure_ascii=False)}",
        },
    ]

    response = client.chat.completions.create(
        model="gpt-4o-mini",
        messages=messages,
        response_format={
            "type": "json_schema",
            "json_schema": {
                "name": "order_analysis",
                "strict": True,
                "schema": {
                    "type": "object",
                    "properties": {
                        "order_summary": {"type": "string"},
                        "total_items": {"type": "integer"},
                        "total_amount": {"type": "number"},
                        "status_description": {"type": "string"},
                        "next_steps": {
                            "type": "array",
                            "items": {"type": "string"},
                        },
                        "recommendations": {
                            "type": "array",
                            "items": {"type": "string"},
                        },
                    },
                    "required": [
                        "order_summary", "total_items", "total_amount",
                        "status_description", "next_steps",
                    ],
                    "additionalProperties": False,
                },
            },
        },
    )

    return OrderAnalysis.model_validate_json(
        response.choices[0].message.content
    )
```

## 注意事项

### 1. Schema 限制
- 最多 100 个属性
- 嵌套深度最多 5 层
- 不支持 `$ref` 和 `definitions`
- 必须设置 `additionalProperties: false`

### 2. 性能影响
- 结构化输出比普通输出略慢
- 复杂的 Schema 会增加响应时间
- 建议保持 Schema 简洁

### 3. 适用场景
- 需要解析 API 响应的系统
- 数据库写入前的数据校验
- 多系统间的数据交换
- 前端直接消费的数据

### 4. JSON Mode 与 Structured Outputs 对比

| 特性 | JSON Mode | Structured Outputs |
|------|-----------|-------------------|
| 格式保证 | 保证合法 JSON | 保证合法 JSON + 符合 Schema |
| 字段类型 | 不保证 | 严格保证 |
| 必填字段 | 不保证 | 严格保证 |
| 适用模型 | GPT-4o, GPT-4o-mini | GPT-4o, GPT-4o-mini, o1 |
| 性能 | 较快 | 略慢 |
| 推荐场景 | 简单输出 | 复杂结构化数据 |