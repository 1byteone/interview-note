# Batch API 与 Prompt Caching

## Batch API

### 概述

Batch API 允许异步批量处理大量请求，相比同步请求可节省 50% 的费用。适合非实时、大批量的任务。

### 适用场景

- 数据标注和分类
- 批量内容审核
- 大规模文本生成
- 离线数据分析
- 定期报告生成

### 工作流程

```
准备批量请求（JSONL 格式）
        │
        ▼
上传文件到 OpenAI
        │
        ▼
创建 Batch 作业
        │
        ▼
等待处理完成（异步，最长 24 小时）
        │
        ▼
下载结果文件
```

### 准备批量请求

```python
import json


def prepare_batch_requests(
    inputs: list,
    model: str = "gpt-4o-mini",
    system_prompt: str = None,
) -> str:
    """准备批量请求文件"""
    requests = []
    system_prompt = system_prompt or "你是一个 AI 商城助手。"

    for i, input_text in enumerate(inputs):
        messages = [
            {"role": "system", "content": system_prompt},
            {"role": "user", "content": input_text},
        ]

        request = {
            "custom_id": f"request-{i:04d}",
            "method": "POST",
            "url": "/v1/chat/completions",
            "body": {
                "model": model,
                "messages": messages,
                "max_tokens": 512,
                "temperature": 0.3,
            },
        }
        requests.append(request)

    # 写入 JSONL 文件
    output_file = "batch_requests.jsonl"
    with open(output_file, "w", encoding="utf-8") as f:
        for req in requests:
            f.write(json.dumps(req, ensure_ascii=False) + "\n")

    return output_file


# 示例：批量商品评论分析
reviews = [
    "这个手机很好用，拍照清晰，续航也不错",
    "质量太差了，用了三天就坏了，差评",
    "性价比很高，推荐购买",
    "外观设计很漂亮，但系统有点卡",
    "物流很快，包装完好，非常满意",
]

batch_file = prepare_batch_requests(
    inputs=[f"分析以下评论的情感倾向（正面/负面/中性）：{r}" for r in reviews],
    model="gpt-4o-mini",
)
```

### 上传并创建 Batch

```python
from openai import OpenAI

client = OpenAI(api_key="sk-xxx")

# 上传批量请求文件
batch_file = client.files.create(
    file=open("batch_requests.jsonl", "rb"),
    purpose="batch",
)

# 创建 Batch 作业
batch = client.batches.create(
    input_file_id=batch_file.id,
    endpoint="/v1/chat/completions",
    completion_window="24h",  # 必须在 24 小时内完成
    metadata={"description": "批量商品评论分析"},
)

print(f"Batch ID: {batch.id}")
print(f"状态: {batch.status}")
```

### 监控与获取结果

```python
import time


def wait_for_batch(batch_id: str, poll_interval: int = 30):
    """等待 Batch 完成"""
    while True:
        batch = client.batches.retrieve(batch_id)
        print(f"状态: {batch.status}")
        print(f"进度: {batch.request_counts.completed}/{batch.request_counts.total}")

        if batch.status == "completed":
            print("Batch 处理完成！")
            return batch
        elif batch.status == "failed":
            print(f"Batch 失败: {batch.errors}")
            return None

        time.sleep(poll_interval)


def get_batch_results(batch):
    """获取 Batch 结果"""
    result = client.files.content(batch.output_file_id)
    results = []

    for line in result.text.strip().split("\n"):
        data = json.loads(line)
        custom_id = data["custom_id"]
        content = data["response"]["body"]["choices"][0]["message"]["content"]
        results.append({"custom_id": custom_id, "content": content})

    return results


# 使用
batch = wait_for_batch(batch.id)
results = get_batch_results(batch)
for r in results:
    print(f"{r['custom_id']}: {r['content']}")
```

### Batch API 定价

| 模型 | 标准价格 | Batch 价格 | 节省 |
|------|----------|------------|------|
| GPT-4o | $2.50/$10.00 | $1.25/$5.00 | 50% |
| GPT-4o-mini | $0.15/$0.60 | $0.075/$0.30 | 50% |
| o1 | $15.00/$60.00 | $7.50/$30.00 | 50% |

## Prompt Caching

### 概述

Prompt Caching 自动缓存重复的 Prompt 前缀，当后续请求的前缀匹配时，直接使用缓存结果，节省时间和成本。

### 缓存机制

```
请求 1: "你是一个 AI 商城助手。请推荐适合学生的笔记本..."
         │
         ▼
         [缓存前缀: "你是一个 AI 商城助手。"]
         │
         ▼
请求 2: "你是一个 AI 商城助手。请推荐适合办公的笔记本..."
         │
         ▼
         前缀匹配！使用缓存 → 只计算新的部分
```

### 缓存条件

- 前缀长度至少 1024 tokens
- 完全匹配（逐 token 匹配）
- 缓存有效期 5-10 分钟
- 适用于 System Prompt 和对话历史

### 缓存命中示例

```python
# 长 System Prompt 可触发缓存
system_prompt = """
你是一个 AI 商城智能助手。你的职责包括：
1. 商品推荐：根据用户需求推荐合适的商品
2. 订单查询：帮助用户查询订单状态
3. 售后服务：处理退换货、投诉等问题
4. 价格咨询：提供商品价格和优惠信息
5. 库存查询：查询商品库存情况

回答规则：
- 保持专业友好的语气
- 提供准确的信息
- 不确定时告知用户
- 保护用户隐私

商品分类：
- 电子产品：手机、电脑、平板、耳机
- 家居用品：家具、家电、厨具
- 服装鞋帽：男装、女装、童装
- 食品饮料：零食、饮料、生鲜
- 图书文具：书籍、文具、办公用品
"""  # 超过 1024 tokens

# 第一次请求（完整计算）
response1 = client.chat.completions.create(
    model="gpt-4o-mini",
    messages=[
        {"role": "system", "content": system_prompt},
        {"role": "user", "content": "推荐一款 5000 元以内的手机"},
    ],
)

# 第二次请求（缓存命中，只计算 user 部分）
response2 = client.chat.completions.create(
    model="gpt-4o-mini",
    messages=[
        {"role": "system", "content": system_prompt},  # 相同前缀
        {"role": "user", "content": "推荐一款适合游戏的笔记本"},
    ],
)
```

### 检查缓存命中

```python
response = client.chat.completions.create(
    model="gpt-4o-mini",
    messages=[...],
)

# 查看缓存命中信息
usage = response.usage
print(f"总 tokens: {usage.total_tokens}")
print(f"Prompt tokens: {usage.prompt_tokens}")
print(f"缓存命中 tokens: {usage.prompt_tokens_details.cached_tokens}")
print(f"实际计费 tokens: {usage.total_tokens - usage.prompt_tokens_details.cached_tokens}")
```

### 缓存优化策略

```python
class PromptCacheOptimizer:
    """Prompt 缓存优化器"""

    @staticmethod
    def design_cacheable_prompt(base_prompt: str, padding: str = "") -> str:
        """设计可缓存的 Prompt 前缀"""
        # 确保 System Prompt 足够长（超过 1024 tokens）
        # 将不变的部分放在前面
        return base_prompt + padding

    @staticmethod
    def estimate_cache_savings(messages: list, cached_tokens: int) -> dict:
        """估算缓存节省"""
        total_prompt = sum(len(m["content"]) for m in messages)
        savings = cached_tokens / total_prompt * 100
        return {
            "total_prompt_chars": total_prompt,
            "cached_tokens": cached_tokens,
            "savings_percent": f"{savings:.1f}%",
        }

    @staticmethod
    def batch_with_cache(batch_inputs: list, system_prompt: str):
        """批量利用缓存"""
        # 所有请求共享相同的 System Prompt
        # 批量处理时，缓存效果更明显
        pass
```

## 成本优化策略

### 综合方案

```python
class CostOptimizer:
    """OpenAI 成本优化器"""

    def __init__(self, client: OpenAI):
        self.client = client
        self.cache = {}

    def should_use_batch(self, task: str, volume: int) -> bool:
        """判断是否使用 Batch API"""
        non_realtime_tasks = [
            "classification", "extraction", "summarization",
            "moderation", "translation", "analysis",
        ]
        return task in non_realtime_tasks and volume > 100

    def select_model(self, complexity: str) -> str:
        """选择最经济的模型"""
        return "gpt-4o-mini" if complexity == "simple" else "gpt-4o"

    def optimize_messages(self, messages: list) -> list:
        """优化消息以减少 token"""
        # 1. 精简 System Prompt
        # 2. 保留最近的 N 轮对话
        # 3. 对长历史做摘要
        return messages[-10:]  # 只保留最近 10 条

    def estimate_cost(
        self, model: str, input_tokens: int, output_tokens: int
    ) -> dict:
        """估算成本（含优化）"""
        pricing = {
            "gpt-4o":        (2.50, 10.00),
            "gpt-4o-mini":   (0.15, 0.60),
        }
        input_price, output_price = pricing.get(model, (0.15, 0.60))

        standard = (input_tokens * input_price
                    + output_tokens * output_price) / 1_000_000

        # 假设缓存命中 50%
        cached = (input_tokens * 0.5 * input_price
                  + output_tokens * output_price) / 1_000_000

        # Batch 再节省 50%
        batch = cached * 0.5

        return {
            "standard": standard,
            "with_cache": cached,
            "with_batch": batch,
            "max_savings": f"{(1 - batch/standard) * 100:.0f}%",
        }
```

### 优化检查清单

1. **使用 GPT-4o-mini 作为默认模型**
2. **设计长 System Prompt 以触发缓存**
3. **非实时任务使用 Batch API**
4. **精简对话历史，只保留必要上下文**
5. **设置合理的 max_tokens 限制**
6. **监控 API 使用量，设置预算告警**
7. **使用 tiktoken 预计算 token 消耗**