# 面试场景题

## 场景 1：API 超时与限流

### 问题描述

AI 商城的搜索功能在高峰期经常出现 API 超时和限流错误，用户反馈搜索响应慢，部分请求失败。

### 解决方案

#### 1. 超时与重试

```python
from openai import OpenAI, APITimeoutError, RateLimitError, APIError
import time
from typing import Optional


class ResilientOpenAIClient:
    """弹性 OpenAI 客户端：超时重试 + 退避策略"""

    def __init__(self, api_key: str):
        self.client = OpenAI(
            api_key=api_key,
            timeout=30.0,
            max_retries=3,
        )

    def chat_with_retry(
        self,
        messages: list,
        model: str = "gpt-4o-mini",
        max_retries: int = 3,
        base_delay: float = 1.0,
    ) -> Optional[str]:
        """带指数退避的请求"""
        for attempt in range(max_retries):
            try:
                response = self.client.chat.completions.create(
                    model=model,
                    messages=messages,
                    timeout=15.0,
                )
                return response.choices[0].message.content

            except APITimeoutError:
                print(f"请求超时，第 {attempt + 1} 次重试")
                time.sleep(base_delay * (2 ** attempt))  # 指数退避

            except RateLimitError as e:
                retry_after = float(e.headers.get("retry-after", 5))
                print(f"限流，等待 {retry_after} 秒")
                time.sleep(retry_after)

            except APIError as e:
                print(f"API 错误: {e}")
                if attempt == max_retries - 1:
                    raise

        return None
```

#### 2. 降级策略

```python
class DegradationStrategy:
    """降级策略：模型链路 + 本地缓存兜底"""

    def __init__(self, client: OpenAI):
        self.client = client
        self.model_chain = ["gpt-4o-mini", "gpt-4o"]
        self.local_cache = {}  # 简单本地缓存

    def search_with_fallback(self, query: str) -> str:
        """带降级的搜索"""
        # 1. 先查本地缓存
        if query in self.local_cache:
            return f"[缓存] {self.local_cache[query]}"

        # 2. 依次尝试模型链路
        for model in self.model_chain:
            try:
                response = self.client.chat.completions.create(
                    model=model,
                    messages=[
                        {"role": "system", "content": "你是 AI 商城搜索助手。"},
                        {"role": "user", "content": query},
                    ],
                    timeout=10.0,
                )
                result = response.choices[0].message.content
                self.local_cache[query] = result  # 写入缓存
                return result
            except Exception:
                continue

        # 3. 全部失败，返回兜底结果
        return "搜索服务暂时不可用，请稍后重试"


# 使用
client = OpenAI(api_key="sk-xxx")
degradation = DegradationStrategy(client)
print(degradation.search_with_fallback("推荐笔记本"))
```

### 回答要点

1. 使用重试 + 指数退避处理瞬时错误
2. 参考 `retry-after` 响应头处理限流
3. 多模型降级链路
4. 本地缓存兜底，保证可用性
5. 使用消息队列削峰

## 场景 2：Token 超限

### 问题描述

AI 商城的客服对话中，随着对话轮数增加，消息历史越来越长，最终超过模型的上下文窗口（如 128K tokens）导致请求失败。

### 解决方案

#### 1. 滑动窗口 + 历史摘要

```python
class ConversationContextManager:
    """对话上下文管理器"""

    MAX_TOKEN_LIMIT = 100_000  # 预留余量
    MAX_RECENT_MESSAGES = 10   # 保留最近 N 条完整消息

    def __init__(self):
        self.summary = None  # 历史摘要
        self.recent_messages = []  # 最近消息

    def add_message(self, role: str, content: str):
        """添加消息"""
        self.recent_messages.append(
            {"role": role, "content": content}
        )
        # 超过阈值，压缩历史
        if len(self.recent_messages) > self.MAX_RECENT_MESSAGES:
            self._compress()

    def _compress(self):
        """压缩历史：保留关键信息"""
        old_messages = self.recent_messages[: -self.MAX_RECENT_MESSAGES]
        self.recent_messages = self.recent_messages[-self.MAX_RECENT_MESSAGES:]

        # 删除的轮次太长时生成摘要
        compact_text = " ".join(
            f"{m['role']}: {m['content']}" for m in old_messages
        )
        self.summary = self._summarize(compact_text)

    def _summarize(self, text: str) -> str:
        """调用模型生成摘要"""
        response = client.chat.completions.create(
            model="gpt-4o-mini",
            messages=[
                {
                    "role": "system",
                    "content": "请将对话历史压缩为简洁摘要，保留关键信息。",
                },
                {"role": "user", "content": text},
            ],
            max_tokens=500,
        )
        return response.choices[0].message.content

    def build_messages(self) -> list:
        """构建完整的 messages，防止 Token 超限"""
        messages = []
        if self.summary:
            messages.append(
                {"role": "system", "content": f"对话历史摘要：{self.summary}"}
            )
        messages.extend(self.recent_messages)
        return messages
```

#### 2. 超限前拦截

```python
def estimate_tokens(text: str) -> int:
    """估算 token 数"""
    return len(text) // 2  # 中文约 0.5 字/token


def check_before_request(messages: list) -> bool:
    """请求前检查 token 是否超限"""
    total = sum(estimate_tokens(m.get("content", "")) for m in messages)
    return total < 100_000
```

### 回答要点

1. 限制保留的对话轮数（滑动窗口）
2. 长历史使用摘要替代
3. 请求前用 tiktoken 预计算
4. 合理设置 max_tokens

## 场景 3：模型幻觉

### 问题描述

AI 商城客服经常编造不存在的商品信息、错误价格、虚假优惠活动，导致用户投诉。

### 解决方案

#### 1. 约束 Prompt

```python
SYSTEM_PROMPT = """
你是 AI 商城客服。

严格规则：
1. 商品信息必须以数据库查询结果为准，禁止编造
2. 不知道的价格、库存、促销信息，明确说"需要查询"
3. 禁止虚构商品、品牌、优惠活动
4. 涉及金额时必须与查询结果一致
5. 不确定时引导用户提供订单号/商品名，通过工具查询
"""
```

#### 2. 强制使用工具

```python
def answer_with_grounding(query: str) -> str:
    """基于真实数据的回答，抑制幻觉"""
    messages = [
        {"role": "system", "content": SYSTEM_PROMPT},
        {"role": "user", "content": query},
    ]

    response = client.chat.completions.create(
        model="gpt-4o-mini",
        messages=messages,
        tools=[
            {
                "type": "function",
                "function": {
                    "name": "get_real_product_info",
                    "description": "从数据库中获取真实商品信息",
                    "parameters": {
                        "type": "object",
                        "properties": {
                            "product_name": {"type": "string"},
                        },
                        "required": ["product_name"],
                        "additionalProperties": False,
                    },
                },
            }
        ],
        tool_choice="required",  # 强制调用工具，确保信息真实
    )

    message = response.choices[0].message
    if not message.tool_calls:
        return "请提供商品名称，我来查询真实信息。"

    # 执行查询并返回结果
    tool_call = message.tool_calls[0]
    args = json.loads(tool_call.function.arguments)
    product = get_real_product_info(args["product_name"])

    messages.append(message)
    messages.append({
        "role": "tool",
        "tool_call_id": tool_call.id,
        "content": json.dumps(product, ensure_ascii=False),
    })

    final = client.chat.completions.create(
        model="gpt-4o-mini",
        messages=messages,
    )
    return final.choices[0].message.content
```

#### 3. 输出校验

```python
def validate_answer(answer: str, facts: dict) -> tuple:
    """校验回答中的事实是否与数据一致"""
    issues = []
    for product, price in facts.items():
        if product in answer and str(price) not in answer:
            issues.append(f"价格与数据库不一致: {product}")

    if issues:
        return False, issues
    return True, []
```

### 回答要点

1. System Prompt 约束行为边界
2. 关键数据强制走 Function Calling 获取真实值
3. 输出做事实校验
4. 结合 RAG 检索真实文档
5. 承认不确定性，不硬编造

## 场景 4：成本失控

### 问题描述

AI 商城接入 OpenAI 后，月度 API 成本从预算的 2000 元飙升至 3 万元，主要原因是全量使用 GPT-4o 且无控制。

### 解决方案

#### 1. 成本监控与预算

```python
class CostMonitor:
    """成本监控器"""

    def __init__(self, monthly_budget: float = 2000.0):
        self.monthly_budget = monthly_budget
        self.daily_budget = monthly_budget / 30
        self.usage = {"cost": 0.0, "calls": 0}
        self.alert_threshold = 0.8  # 80% 告警

    def record(self, model: str, in_tokens: int, out_tokens: int):
        """记录使用成本"""
        pricing = {
            "gpt-4o":        (2.50, 10.00),
            "gpt-4o-mini":   (0.15, 0.60),
            "o1":           (15.00, 60.00),
        }
        in_price, out_price = pricing.get(model, (0.15, 0.60))
        cost = (in_tokens * in_price + out_tokens * out_price) / 1_000_000
        self.usage["cost"] += cost
        self.usage["calls"] += 1

    def check_and_alert(self) -> bool:
        """检查预算，超阈值返回告警"""
        ratio = self.usage["cost"] / self.monthly_budget
        if ratio >= self.alert_threshold:
            print(f"告警: 已消耗月预算的 {ratio*100:.0f}%")
            return True
        return False
```

#### 2. 限流熔断

```python
class RequestThrottler:
    """请求限流器：令牌桶"""

    def __init__(self, rate: float = 100.0, burst: int = 20):
        self.rate = rate          # 每秒补充令牌数
        self.burst = burst        # 桶容量
        self.tokens = burst
        self.last_refill = time.time()

    def _refill(self):
        now = time.time()
        delta = now - self.last_refill
        self.tokens = min(
            self.burst, self.tokens + delta * self.rate
        )
        self.last_refill = now

    def acquire(self) -> bool:
        """获取令牌，成功返回 True"""
        self._refill()
        if self.tokens >= 1:
            self.tokens -= 1
            return True
        return False
```

#### 3. 成本优化落地

```python
def cost_optimized_dispatch(task: str, complexity: str) -> str:
    """成本优化的任务分发"""
    # 规则 1: 非实时任务走 Batch API
    if task in ("分类", "审核", "标注", "报表"):
        return "batch"  # 节省 50%

    # 规则 2: 简单任务用 4o-mini
    if complexity == "simple":
        return "gpt-4o-mini"

    # 规则 3: 复杂任务限量使用 4o
    return "gpt-4o"

    # 规则 4: 推理任务仅在必要时用 o1
```

### 回答要点

1. 默认模型降级为 4o-mini，复杂任务才升级
2. 非实时任务走 Batch API 节省 50%
3. 长 System Prompt 触发缓存
4. 设置预算告警与限流熔断
5. 按用户等级差异化服务（VIP 用更好的模型）