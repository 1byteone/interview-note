# OpenAI API 快速入门

## API 基础

### 获取 API Key

访问 [platform.openai.com/api-keys](https://platform.openai.com/api-keys) 创建 API Key。请妥善保管，不要泄露到代码仓库中。

### 安装 OpenAI Python SDK

```bash
pip install openai
```

### 初始化客户端

```python
from openai import OpenAI

client = OpenAI(
    api_key="sk-xxx",  # 替换为你的 API Key
)
```

### 模型列表

OpenAI 提供多个模型系列，每个系列针对不同场景优化：

| 模型系列 | 主要用途 | 特点 |
|----------|----------|------|
| GPT-4o | 通用对话、复杂推理 | 多模态、高智能 |
| GPT-4o-mini | 低成本对话、快速响应 | 性价比最高 |
| o1 | 深度推理、数学、代码 | 思维链推理 |
| o3 | 前沿推理 | 最新推理模型 |
| GPT-4 Turbo | 旧版兼容 | 已逐步被 4o 取代 |

### 端点分类

- **Chat Completions**: `POST /v1/chat/completions` — 对话生成
- **Embeddings**: `POST /v1/embeddings` — 文本向量化
- **Fine-tuning**: `POST /v1/fine_tuning/jobs` — 微调
- **Assistants**: `POST /v1/assistants` — 助手 API
- **Moderation**: `POST /v1/moderations` — 内容审核
- **Images**: `POST /v1/images/generations` — 图片生成

## Chat Completions API

### 核心参数

```python
response = client.chat.completions.create(
    model="gpt-4o-mini",
    messages=[
        {"role": "system", "content": "你是一个专业的电商助手。"},
        {"role": "user", "content": "推荐几款适合夏天的运动鞋"},
    ],
    temperature=0.7,      # 随机性，0-2，默认 1
    max_tokens=1024,       # 最大输出 token 数
    top_p=0.9,             # 核采样，默认 1
    frequency_penalty=0,   # 频率惩罚，-2 到 2
    presence_penalty=0,    # 存在惩罚，-2 到 2
)
```

### messages 中的角色

| 角色 | 用途 | 说明 |
|------|------|------|
| `system` | 系统指令 | 设定助手行为、风格、约束 |
| `user` | 用户输入 | 提问或指令 |
| `assistant` | 助手回复 | 模型输出，也可用于多轮对话历史 |
| `tool` | 工具结果 | Function Calling 的返回结果 |

### temperature 详解

- **0.0 - 0.3**: 确定性输出，适合代码生成、事实问答
- **0.4 - 0.7**: 平衡模式，适合通用对话
- **0.8 - 1.0**: 创意模式，适合文案生成
- **1.0 - 2.0**: 高随机性，适合头脑风暴（但不推荐超过 1.5）

## 流式输出

流式输出（Streaming）让模型逐 token 返回结果，显著提升用户体验。

```python
stream = client.chat.completions.create(
    model="gpt-4o-mini",
    messages=[
        {"role": "user", "content": "用 5 句话介绍杭州"},
    ],
    stream=True,
)

for chunk in stream:
    if chunk.choices[0].delta.content is not None:
        print(chunk.choices[0].delta.content, end="")
```

流式输出的每个 chunk 结构：

```python
Chunk(id='chatcmpl-xxx', choices=[
    Choice(delta=Delta(content='杭州', role='assistant'),
           finish_reason=None,
           index=0)
])
```

最后一个 chunk 的 `finish_reason` 为 `"stop"`。

## 最小案例：AI 商城商品推荐助手

```python
from openai import OpenAI

client = OpenAI(api_key="sk-xxx")

def recommend_products(query: str, budget: float = None) -> str:
    """AI 商城商品推荐助手"""
    messages = [
        {
            "role": "system",
            "content": (
                "你是一个专业的 AI 商城购物助手。你擅长根据用户需求"
                "推荐商品，并提供购买建议。请用简洁、专业的语言回复。"
                "如果用户提到预算，请严格按预算范围推荐。"
            ),
        },
        {
            "role": "user",
            "content": f"用户需求：{query}"
                       + (f"，预算：{budget}元" if budget else ""),
        },
    ]

    response = client.chat.completions.create(
        model="gpt-4o-mini",
        messages=messages,
        temperature=0.7,
        max_tokens=512,
    )

    return response.choices[0].message.content


# 使用示例
print(recommend_products("适合大学生的轻薄笔记本", 5000))
print(recommend_products("送女朋友的口红"))
```

### 流式版本

```python
def stream_recommend(query: str):
    stream = client.chat.completions.create(
        model="gpt-4o-mini",
        messages=[
            {"role": "system", "content": "你是一个 AI 商城购物助手。"},
            {"role": "user", "content": f"推荐：{query}"},
        ],
        stream=True,
    )

    for chunk in stream:
        if chunk.choices[0].delta.content:
            yield chunk.choices[0].delta.content


# 使用
for text in stream_recommend("适合跑步的耳机"):
    print(text, end="", flush=True)
```

## 常见问题

### API Key 设置
- 使用环境变量 `OPENAI_API_KEY` 避免硬编码
- 生产环境使用密钥管理服务

### 超时设置
```python
client = OpenAI(
    api_key="sk-xxx",
    timeout=30.0,      # 请求超时
    max_retries=2,     # 重试次数
)
```

### 错误处理
```python
from openai import APIError, RateLimitError, APITimeoutError

try:
    response = client.chat.completions.create(...)
except RateLimitError:
    print("请求频率过高，请稍后重试")
except APITimeoutError:
    print("请求超时")
except APIError as e:
    print(f"API 错误: {e}")
```