# 微调

## 微调原理

微调（Fine-tuning）是在预训练模型的基础上，使用特定领域的数据继续训练，使模型适应特定任务或风格。

### 微调的工作方式

```
预训练模型（通用知识）
        │
        ▼
  准备微调数据集（JSONL 格式）
        │
        ▼
  上传文件到 OpenAI
        │
        ▼
  创建微调作业（Fine-tuning Job）
        │
        ▼
  微调完成，获得自定义模型
        │
        ▼
  使用自定义模型（ft:gpt-4o-mini:xxx:xxx:xxx）
```

### 微调能做什么

- **风格适配**: 让模型学习特定的回复风格
- **领域知识**: 注入特定领域的术语和知识
- **任务优化**: 提升特定任务的表现
- **格式控制**: 学习特定的输出格式
- **减少 Prompt 长度**: 将行为固化到模型权重中

### 微调不能做什么

- 不能替代预训练（微调是浅层调整）
- 不能注入大量新知识（知识主要来自预训练）
- 不能修复基础能力缺陷（如推理能力）

## 数据集准备

### JSONL 格式

每条数据是一个 JSON 对象，包含 `messages` 字段：

```jsonl
{"messages": [{"role": "system", "content": "你是 AI 商城客服助手。"}, {"role": "user", "content": "你好，我想查询一下我的订单"}, {"role": "assistant", "content": "您好！很高兴为您服务。请问您能提供订单号吗？我帮您查询订单状态。"}]}
{"messages": [{"role": "system", "content": "你是 AI 商城客服助手。"}, {"role": "user", "content": "我的订单 ORD123 已经三天了还没发货"}, {"role": "assistant", "content": "非常抱歉给您带来不便。我查看到订单 ORD123 目前处于"待发货"状态。由于近期订单量较大，发货有所延迟。我帮您催一下仓库，预计会在 24 小时内安排发货。您看可以吗？"}]}
```

### 数据准备脚本

```python
import json


def prepare_training_data(examples: list, output_file: str):
    """将训练示例转换为 JSONL 格式"""
    with open(output_file, "w", encoding="utf-8") as f:
        for example in examples:
            messages = [
                {"role": "system", "content": example.get("system", "你是 AI 商城客服助手。")},
                {"role": "user", "content": example["user"]},
                {"role": "assistant", "content": example["assistant"]},
            ]
            f.write(json.dumps({"messages": messages}, ensure_ascii=False) + "\n")


# 示例数据
training_examples = [
    {
        "user": "退货流程是什么样的？",
        "assistant": "退货流程如下：\n1. 在"我的订单"中找到需要退货的商品\n2. 点击"申请退货"并填写原因\n3. 等待商家审核（通常 1-2 小时）\n4. 审核通过后，按指引寄回商品\n5. 商家收到商品后，退款将在 3-5 个工作日到账",
    },
    {
        "user": "你们有优惠券吗？",
        "assistant": "目前有以下优惠活动：\n1. 新用户注册立减 50 元\n2. 满 300 减 30（每日限量）\n3. 会员专享 9.5 折\n您可以在"我的优惠"页面查看可用的优惠券。",
    },
    # ... 更多示例
]

prepare_training_data(training_examples, "mall_training_data.jsonl")
```

### 数据质量要求

| 要求 | 说明 |
|------|------|
| 最少样本数 | 至少 50 条，建议 100-1000 条 |
| 数据多样性 | 覆盖各种场景和问法 |
| 数据准确性 | 回复必须准确，不能有错误信息 |
| 格式一致性 | 所有样本使用相同的格式 |
| 去重 | 避免重复数据导致过拟合 |

## 微调 API 调用

### 上传文件

```python
from openai import OpenAI

client = OpenAI(api_key="sk-xxx")

# 上传训练文件
file = client.files.create(
    file=open("mall_training_data.jsonl", "rb"),
    purpose="fine-tune",
)

print(f"文件 ID: {file.id}")
```

### 创建微调作业

```python
# 创建微调作业
job = client.fine_tuning.jobs.create(
    training_file=file.id,
    model="gpt-4o-mini-2024-07-18",  # 基础模型
    suffix="mall-assistant",          # 自定义后缀
    hyperparameters={
        "n_epochs": 3,               # 训练轮数
        "batch_size": "auto",        # 批次大小
        "learning_rate_multiplier": "auto",  # 学习率
    },
)

print(f"微调作业 ID: {job.id}")
print(f"状态: {job.status}")
```

### 监控微调进度

```python
import time


def wait_for_fine_tuning(job_id: str, poll_interval: int = 30):
    """等待微调完成"""
    while True:
        job = client.fine_tuning.jobs.retrieve(job_id)
        print(f"状态: {job.status}")

        if job.status == "succeeded":
            print(f"微调完成！模型 ID: {job.fine_tuned_model}")
            return job.fine_tuned_model
        elif job.status == "failed":
            print(f"微调失败: {job.error}")
            return None

        # 查看训练指标
        if job.status == "running":
            events = client.fine_tuning.jobs.list_events(job_id)
            for event in events.data[:3]:
                print(f"  {event.message}")

        time.sleep(poll_interval)


fine_tuned_model = wait_for_fine_tuning(job.id)
```

### 使用微调模型

```python
# 使用微调后的模型
response = client.chat.completions.create(
    model=fine_tuned_model,
    messages=[
        {"role": "user", "content": "我想退货，怎么操作？"},
    ],
)

print(response.choices[0].message.content)
```

## 微调 vs Prompt Engineering

### 对比

| 维度 | 微调 | Prompt Engineering |
|------|------|-------------------|
| 成本 | 高（训练 + 托管） | 低（按 token 计费） |
| 延迟 | 同基础模型 | 同基础模型 |
| 灵活性 | 固定行为，难以修改 | 灵活调整 Prompt |
| 效果 | 更稳定，更一致 | 受 Prompt 质量影响 |
| 维护 | 需要重新训练更新 | 修改 Prompt 即可 |
| 数据量 | 需要大量标注数据 | 不需要额外数据 |
| 适用场景 | 高频、固定风格任务 | 低频、多变任务 |

### 选型决策树

```
需要模型输出高度一致的格式/风格？
    ├── 是 ──► 微调
    └── 否
         └── 通过 Prompt 能否达到预期效果？
              ├── 能 ──► Prompt Engineering
              └── 不能
                   └── 是否有足够的标注数据？
                        ├── 有 ──► 微调
                        └── 无 ──► 尝试优化 Prompt 或使用 RAG
```

## 成本与效果评估

### 训练成本估算

```python
def estimate_training_cost(
    num_samples: int,
    avg_tokens_per_sample: int,
    base_model: str,
    n_epochs: int,
) -> float:
    """估算微调训练成本"""
    # 训练 token 数 = 样本数 * 平均 token 数 * 轮数
    total_tokens = num_samples * avg_tokens_per_sample * n_epochs

    # 定价（每 1M tokens）
    pricing = {
        "gpt-4o-mini-2024-07-18": 3.00,
        "gpt-4o-2024-08-06": 25.00,
    }

    price_per_m = pricing.get(base_model, 3.00)
    cost = total_tokens * price_per_m / 1_000_000

    return cost


# 例如：1000 条数据，平均 500 tokens，训练 3 轮
cost = estimate_training_cost(1000, 500, "gpt-4o-mini-2024-07-18", 3)
print(f"预计训练成本: ${cost:.2f}")  # 约 $4.50
```

### 效果评估

```python
def evaluate_model(model_id: str, test_cases: list) -> dict:
    """评估微调模型效果"""
    results = []

    for case in test_cases:
        response = client.chat.completions.create(
            model=model_id,
            messages=[{"role": "user", "content": case["input"]}],
        )
        output = response.choices[0].message.content

        results.append({
            "input": case["input"],
            "expected": case["expected"],
            "actual": output,
            "match": case["expected"].lower() in output.lower(),
        })

    accuracy = sum(r["match"] for r in results) / len(results)
    return {"accuracy": accuracy, "results": results}
```

### 最佳实践

1. **从 Prompt 开始**: 先尝试 Prompt Engineering，确认瓶颈
2. **小规模试点**: 先用 50-100 条数据测试
3. **混合使用**: 微调 + Prompt 组合效果更好
4. **定期评估**: 持续监控微调模型的表现
5. **版本管理**: 保存每次微调的配置和数据集
6. **成本控制**: 微调后监控 token 使用量