# 模型对比与定价

## 主流模型对比

### GPT-4o

- **定位**: 旗舰多模态模型，文本 + 图像 + 音频
- **知识截止**: 2025 年 5 月（持续更新）
- **上下文窗口**: 128K tokens
- **最大输出**: 16K tokens
- **特点**: 高智商、多模态、函数调用能力强
- **适用场景**: 复杂推理、代码生成、数据分析、多模态任务

### GPT-4o-mini

- **定位**: 轻量级低成本模型
- **上下文窗口**: 128K tokens
- **最大输出**: 16K tokens
- **特点**: 性价比较高，速度较快
- **适用场景**: 简单对话、分类、提取、内容生成、客服

### o1

- **定位**: 推理增强模型
- **上下文窗口**: 200K tokens
- **特点**: 内置思维链，擅长数学、科学、编程
- **适用场景**: 复杂数学推理、竞赛编程、科学研究
- **注意**: 响应时间较长，成本较高

### o3

- **定位**: 最新推理模型，o1 的升级版
- **上下文窗口**: 200K tokens
- **特点**: 更强的推理能力，更长的思考时间
- **适用场景**: 前沿科研、高难度问题求解

## 定价模型

### Token 计费

OpenAI 按 token 计费，1 个 token 约等于 0.75 个英文单词或 0.5 个中文字符。计费分输入（input）和输出（output）两个维度。

### 2025 年参考价格

| 模型 | 输入价格（/1M tokens） | 输出价格（/1M tokens） | 性价比评级 |
|------|------------------------|------------------------|------------|
| GPT-4o | $2.50 | $10.00 | ⭐⭐⭐ |
| GPT-4o-mini | $0.15 | $0.60 | ⭐⭐⭐⭐⭐ |
| o1 | $15.00 | $60.00 | ⭐⭐ |
| o3 | $25.00 | $100.00 | ⭐ |
| GPT-4 Turbo | $10.00 | $30.00 | ⭐⭐ |

### 成本示例

假设每次对话平均输入 500 tokens，输出 300 tokens：

| 模型 | 每次成本 | 1 万次/月成本 |
|------|----------|---------------|
| GPT-4o-mini | $0.000255 | $2.55 |
| GPT-4o | $0.00425 | $42.50 |
| o1 | $0.0255 | $255.00 |

## Token 计算与优化

### 使用 tiktoken 计算 Token

```python
import tiktoken

def count_tokens(text: str, model: str = "gpt-4o") -> int:
    """计算文本的 token 数"""
    encoding = tiktoken.encoding_for_model(model)
    return len(encoding.encode(text))

def count_messages_tokens(messages: list, model: str = "gpt-4o") -> int:
    """计算消息列表的 token 数"""
    try:
        encoding = tiktoken.encoding_for_model(model)
    except KeyError:
        encoding = tiktoken.get_encoding("cl100k_base")

    tokens_per_message = 3
    tokens_per_name = 1
    total = 0

    for message in messages:
        total += tokens_per_message
        for key, value in message.items():
            total += len(encoding.encode(value))
            if key == "name":
                total += tokens_per_name

    total += 3  # 末尾补充
    return total


# 示例
messages = [
    {"role": "system", "content": "你是 AI 商城助手。"},
    {"role": "user", "content": "推荐一款 5000 元以内的轻薄本"},
]
print(f"Token 数: {count_messages_tokens(messages)}")
```

### Token 优化策略

1. **精简 System Prompt**: 去除冗余描述，使用简洁指令
2. **控制历史轮数**: 只保留最近 N 轮对话
3. **摘要历史**: 长对话时对历史做摘要替代
4. **限制 Max Tokens**: 设置合理的输出上限
5. **使用更短的模型名**: 不影响 token 数，但便于维护

## 模型选择策略

### 场景化选型矩阵

| 场景 | 推荐模型 | 原因 |
|------|----------|------|
| 实时聊天 | GPT-4o-mini | 低成本、低延迟 |
| 代码生成 | GPT-4o | 高准确度 |
| 数学推理 | o1 / o3 | 思维链推理 |
| 内容审核 | GPT-4o-mini | 批量处理、成本敏感 |
| 商品推荐 | GPT-4o-mini | 简单任务、高并发 |
| 复杂客服 | GPT-4o | 多轮复杂对话 |
| 数据分析 | GPT-4o | 结构化输出要求 |
| 翻译 | GPT-4o-mini | 简单任务、成本低 |

### 多模型路由策略

在 AI 商城系统中，采用多模型路由显著优化成本与性能：

```python
def route_model(task_type: str, complexity: str) -> str:
    """根据任务类型和复杂度路由到合适的模型"""
    routing = {
        ("search", "simple"):    "gpt-4o-mini",
        ("search", "complex"):   "gpt-4o",
        ("recommend", "simple"): "gpt-4o-mini",
        ("recommend", "complex"): "gpt-4o",
        ("reasoning", "any"):    "o1",
        ("coding", "any"):       "gpt-4o",
        ("moderation", "any"):   "gpt-4o-mini",
        ("chat", "simple"):      "gpt-4o-mini",
        ("chat", "complex"):     "gpt-4o",
    }
    return routing.get((task_type, complexity), "gpt-4o-mini")


def estimate_cost(model: str, input_tokens: int, output_tokens: int) -> float:
    """估算请求成本（美元）"""
    pricing = {
        "gpt-4o":        (2.50, 10.00),
        "gpt-4o-mini":   (0.15, 0.60),
        "o1":           (15.00, 60.00),
        "o3":           (25.00, 100.00),
    }
    input_price, output_price = pricing.get(model, (0.15, 0.60))
    return (input_tokens * input_price + output_tokens * output_price) / 1_000_000
```

### 成本控制最佳实践

1. **默认使用 GPT-4o-mini**: 覆盖 80% 的日常场景
2. **复杂任务升级到 GPT-4o**: 仅当需要更高智能时
3. **推理任务使用 o1**: 仅限数学、逻辑、代码竞赛
4. **设置预算上限**: 监控每日/每月 API 消耗
5. **缓存重复请求**: 相同问题使用缓存响应
6. **批量处理非实时任务**: 使用 Batch API 降低 50% 成本