# AI 商城 OpenAI 集成方案

## 概述

本文档展示如何在 AI 商城中集成 OpenAI API，涵盖多模型路由、流式搜索、Function Calling 订单查询和成本控制策略。

## 多模型路由

### 路由策略

根据任务类型和复杂度动态选择模型，在保证质量的同时控制成本。

```python
from openai import OpenAI
from enum import Enum


class TaskType(Enum):
    SEARCH = "search"
    RECOMMEND = "recommend"
    CHAT = "chat"
    REASONING = "reasoning"
    MODERATION = "moderation"


class Complexity(Enum):
    SIMPLE = "simple"
    COMPLEX = "complex"


class ModelRouter:
    """多模型路由器"""

    MODEL_CONFIG = {
        "gpt-4o-mini": {
            "cost_per_input_m": 0.15,
            "cost_per_output_m": 0.60,
            "speed": "fast",
            "capability": "standard",
        },
        "gpt-4o": {
            "cost_per_input_m": 2.50,
            "cost_per_output_m": 10.00,
            "speed": "medium",
            "capability": "high",
        },
        "o1": {
            "cost_per_input_m": 15.00,
            "cost_per_output_m": 60.00,
            "speed": "slow",
            "capability": "expert",
        },
    }

    ROUTING_RULES = {
        (TaskType.SEARCH, Complexity.SIMPLE): "gpt-4o-mini",
        (TaskType.SEARCH, Complexity.COMPLEX): "gpt-4o",
        (TaskType.RECOMMEND, Complexity.SIMPLE): "gpt-4o-mini",
        (TaskType.RECOMMEND, Complexity.COMPLEX): "gpt-4o",
        (TaskType.CHAT, Complexity.SIMPLE): "gpt-4o-mini",
        (TaskType.CHAT, Complexity.COMPLEX): "gpt-4o",
        (TaskType.REASONING, Complexity.SIMPLE): "gpt-4o",
        (TaskType.REASONING, Complexity.COMPLEX): "o1",
        (TaskType.MODERATION, Complexity.SIMPLE): "gpt-4o-mini",
        (TaskType.MODERATION, Complexity.COMPLEX): "gpt-4o-mini",
    }

    def __init__(self, client: OpenAI):
        self.client = client

    def route(self, task_type: TaskType, complexity: Complexity) -> str:
        """路由到合适的模型"""
        return self.ROUTING_RULES.get((task_type, complexity), "gpt-4o-mini")

    def estimate_cost(
        self, model: str, input_tokens: int, output_tokens: int
    ) -> float:
        """估算成本"""
        config = self.MODEL_CONFIG[model]
        cost = (
            input_tokens * config["cost_per_input_m"]
            + output_tokens * config["cost_per_output_m"]
        ) / 1_000_000
        return cost
```

## 流式搜索 API

### 流式搜索实现

```python
from typing import Generator


class StreamingSearchAPI:
    """流式搜索 API"""

    def __init__(self, client: OpenAI, router: ModelRouter):
        self.client = client
        self.router = router

    def search(
        self,
        query: str,
        category: str = None,
        max_price: float = None,
    ) -> Generator[str, None, None]:
        """流式搜索商品"""
        messages = [
            {
                "role": "system",
                "content": (
                    "你是一个 AI 商城搜索助手。根据用户需求推荐商品。\n"
                    "请按以下格式输出：\n"
                    "1. 先确认用户需求\n"
                    "2. 推荐 3-5 个商品（名称、价格、特点）\n"
                    "3. 给出购买建议"
                ),
            },
            {
                "role": "user",
                "content": (
                    f"搜索：{query}"
                    + (f"，类别：{category}" if category else "")
                    + (f"，预算：{max_price}元" if max_price else "")
                ),
            },
        ]

        # 判断复杂度，路由到合适模型
        complexity = (
            Complexity.COMPLEX
            if any(kw in query for kw in ["对比", "推荐", "哪个好", "区别"])
            else Complexity.SIMPLE
        )
        model = self.router.route(TaskType.SEARCH, complexity)

        # 流式输出
        stream = self.client.chat.completions.create(
            model=model,
            messages=messages,
            stream=True,
            temperature=0.7,
            max_tokens=1024,
        )

        for chunk in stream:
            if chunk.choices[0].delta.content:
                yield chunk.choices[0].delta.content


# FastAPI 端点示例
from fastapi import FastAPI
from fastapi.responses import StreamingResponse

app = FastAPI()
client = OpenAI(api_key="sk-xxx")
router = ModelRouter(client)
search_api = StreamingSearchAPI(client, router)


@app.get("/api/search/stream")
async def stream_search(query: str, category: str = None):
    """流式搜索接口"""
    return StreamingResponse(
        search_api.search(query, category),
        media_type="text/event-stream",
    )
```

## Function Calling 订单查询

### 订单工具定义

```python
ORDER_TOOLS = [
    {
        "type": "function",
        "function": {
            "name": "query_order",
            "description": "查询订单信息，包括订单状态、商品列表、金额等",
            "parameters": {
                "type": "object",
                "properties": {
                    "order_id": {
                        "type": "string",
                        "description": "订单号，格式如 ORD001",
                    },
                    "user_id": {
                        "type": "string",
                        "description": "用户 ID",
                    },
                },
                "required": ["order_id"],
                "additionalProperties": False,
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "cancel_order",
            "description": "取消指定订单",
            "parameters": {
                "type": "object",
                "properties": {
                    "order_id": {
                        "type": "string",
                        "description": "要取消的订单号",
                    },
                    "reason": {
                        "type": "string",
                        "description": "取消原因",
                    },
                },
                "required": ["order_id", "reason"],
                "additionalProperties": False,
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "track_delivery",
            "description": "查询物流信息",
            "parameters": {
                "type": "object",
                "properties": {
                    "order_id": {
                        "type": "string",
                        "description": "订单号",
                    },
                },
                "required": ["order_id"],
                "additionalProperties": False,
            },
        },
    },
]
```

### 订单服务实现

```python
class OrderService:
    """AI 商城订单服务"""

    def __init__(self, client: OpenAI, router: ModelRouter):
        self.client = client
        self.router = router

    def handle_order_query(self, user_message: str, user_id: str) -> str:
        """处理订单查询"""
        messages = [
            {
                "role": "system",
                "content": (
                    "你是 AI 商城的订单助手。你可以查询订单、取消订单、"
                    "跟踪物流。请使用提供的工具帮助用户处理订单问题。"
                ),
            },
            {"role": "user", "content": user_message},
        ]

        # 使用 GPT-4o 保证订单处理的准确性
        response = self.client.chat.completions.create(
            model="gpt-4o",
            messages=messages,
            tools=ORDER_TOOLS,
            tool_choice="auto",
        )

        message = response.choices[0].message

        # 处理工具调用
        if message.tool_calls:
            messages.append(message)
            for tool_call in message.tool_calls:
                result = self._execute_tool(tool_call, user_id)
                messages.append({
                    "role": "tool",
                    "tool_call_id": tool_call.id,
                    "content": json.dumps(result, ensure_ascii=False),
                })

            # 生成最终回复
            final = self.client.chat.completions.create(
                model="gpt-4o",
                messages=messages,
                tools=ORDER_TOOLS,
            )
            return final.choices[0].message.content

        return message.content

    def _execute_tool(self, tool_call, user_id: str) -> dict:
        """执行工具调用"""
        name = tool_call.function.name
        args = json.loads(tool_call.function.arguments)

        if name == "query_order":
            return self._query_order(args["order_id"], user_id)
        elif name == "cancel_order":
            return self._cancel_order(args["order_id"], args["reason"], user_id)
        elif name == "track_delivery":
            return self._track_delivery(args["order_id"])
        return {"error": "未知操作"}

    def _query_order(self, order_id: str, user_id: str) -> dict:
        """查询订单（模拟实现）"""
        # 实际项目中查询数据库
        return {
            "order_id": order_id,
            "status": "shipped",
            "total": 9798.00,
            "items": [
                {"name": "iPhone 15 Pro", "price": 7999, "quantity": 1},
                {"name": "AirPods Pro", "price": 1799, "quantity": 1},
            ],
            "shipping_address": "北京市朝阳区...",
            "estimated_delivery": "2025-06-22",
        }

    def _cancel_order(self, order_id: str, reason: str, user_id: str) -> dict:
        """取消订单（模拟实现）"""
        return {
            "order_id": order_id,
            "status": "cancelled",
            "refund_amount": 9798.00,
            "refund_eta": "3-5 个工作日",
        }

    def _track_delivery(self, order_id: str) -> dict:
        """查询物流（模拟实现）"""
        return {
            "order_id": order_id,
            "carrier": "顺丰快递",
            "tracking_number": "SF1234567890",
            "status": "运输中",
            "current_location": "广州分拨中心",
            "updates": [
                {"time": "2025-06-20 10:00", "event": "已揽收"},
                {"time": "2025-06-20 15:00", "event": "到达广州分拨中心"},
            ],
        }
```

## 成本控制策略

### 完整成本控制方案

```python
class CostController:
    """AI 商城成本控制器"""

    def __init__(self, client: OpenAI, monthly_budget: float = 1000):
        self.client = client
        self.monthly_budget = monthly_budget
        self.daily_budget = monthly_budget / 30
        self.usage = {"daily": 0.0, "monthly": 0.0, "requests": 0}

    def should_use_batch(self, task: str) -> bool:
        """判断是否使用 Batch API"""
        batch_tasks = [
            "评论分类", "商品标签", "批量审核",
            "数据标注", "报表生成", "价格分析",
        ]
        return any(t in task for t in batch_tasks)

    def get_model_for_task(self, task: str, user_tier: str = "normal") -> str:
        """根据任务和用户等级选择模型"""
        # VIP 用户使用更好的模型
        if user_tier == "vip":
            return "gpt-4o"

        # 普通用户的模型选择
        model_map = {
            "商品搜索": "gpt-4o-mini",
            "商品推荐": "gpt-4o-mini",
            "简单咨询": "gpt-4o-mini",
            "订单查询": "gpt-4o",
            "投诉处理": "gpt-4o",
            "售后问题": "gpt-4o",
        }
        return model_map.get(task, "gpt-4o-mini")

    def track_usage(self, model: str, input_tokens: int, output_tokens: int):
        """追踪使用量"""
        pricing = {
            "gpt-4o-mini": (0.15, 0.60),
            "gpt-4o": (2.50, 10.00),
            "o1": (15.00, 60.00),
        }
        in_price, out_price = pricing.get(model, (0.15, 0.60))
        cost = (input_tokens * in_price + output_tokens * out_price) / 1_000_000

        self.usage["daily"] += cost
        self.usage["monthly"] += cost
        self.usage["requests"] += 1

    def check_budget(self) -> dict:
        """检查预算使用情况"""
        daily_remaining = self.daily_budget - self.usage["daily"]
        monthly_remaining = self.monthly_budget - self.usage["monthly"]

        alerts = []
        if daily_remaining < self.daily_budget * 0.2:
            alerts.append("日预算即将用完")
        if monthly_remaining < self.monthly_budget * 0.1:
            alerts.append("月预算即将用完")

        return {
            "daily_usage": f"${self.usage['daily']:.2f}",
            "daily_remaining": f"${daily_remaining:.2f}",
            "monthly_usage": f"${self.usage['monthly']:.2f}",
            "monthly_remaining": f"${monthly_remaining:.2f}",
            "total_requests": self.usage["requests"],
            "alerts": alerts,
        }
```

### 集成示例

```python
class MallOpenAIIntegration:
    """AI 商城 OpenAI 集成主入口"""

    def __init__(self, api_key: str, monthly_budget: float = 1000):
        self.client = OpenAI(api_key=api_key)
        self.router = ModelRouter(self.client)
        self.cost = CostController(self.client, monthly_budget)
        self.search = StreamingSearchAPI(self.client, self.router)
        self.orders = OrderService(self.client, self.router)

    def handle_request(
        self,
        task_type: TaskType,
        query: str,
        user_id: str = None,
        user_tier: str = "normal",
    ):
        """处理各类请求"""
        # 成本追踪
        model = self.router.route(task_type, Complexity.SIMPLE)
        self.cost.track_usage(model, len(query) * 2, 0)

        # 预算检查
        budget = self.cost.check_budget()
        if budget["alerts"]:
            print(f"预算告警: {budget['alerts']}")

        # 路由处理
        if task_type == TaskType.SEARCH:
            return self.search.search(query)
        elif task_type == TaskType.CHAT and user_id:
            return self.orders.handle_order_query(query, user_id)
        else:
            # 通用对话
            return self._general_chat(query, model)

    def _general_chat(self, query: str, model: str):
        """通用对话"""
        response = self.client.chat.completions.create(
            model=model,
            messages=[
                {"role": "system", "content": "你是 AI 商城助手。"},
                {"role": "user", "content": query},
            ],
        )
        return response.choices[0].message.content
```

## 部署建议

### 1. 环境变量
```bash
OPENAI_API_KEY=sk-xxx
OPENAI_ORG_ID=org-xxx
MONTHLY_BUDGET=1000
DEFAULT_MODEL=gpt-4o-mini
```

### 2. 监控告警
- 设置 API 使用量告警
- 监控各模型响应时间
- 记录错误率和重试次数

### 3. 降级策略
- 模型不可用时降级到备用模型
- 高峰期使用队列限流
- 关键功能保留本地缓存