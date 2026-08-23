# Prompt Engineering

## Prompt 结构

### System 指令

System 指令设定助手的全局行为，在对话开始时设置一次，后续保持不变。

```python
system_prompt = """
你是一个 AI 商城智能助手。请遵循以下规则：
1. 始终保持专业、友好的语气
2. 推荐商品时提供至少 3 个选项
3. 如果不确定商品信息，请明确告知用户
4. 不要编造不存在的数据
5. 回答需要简洁但信息完整
"""
```

### User 消息

用户输入应清晰、具体，包含必要上下文。

```python
# 好的 Prompt
user_prompt = "我需要一款 5000 元以内、适合编程的轻薄笔记本，需要 16GB 内存"

# 不好的 Prompt
bad_prompt = "推荐笔记本"
```

### Assistant 消息

在多轮对话中，Assistant 消息用于维护上下文。

```python
messages = [
    {"role": "system", "content": "你是 AI 商城助手。"},
    {"role": "user", "content": "有哪些手机适合拍照？"},
    {"role": "assistant", "content": "以下是几款拍照出色的手机推荐：\n1. ...\n2. ..."},
    {"role": "user", "content": "第一个的续航怎么样？"},
]
```

## Few-shot Learning

通过提供示例来引导模型输出格式和风格。

```python
def recommend_with_examples(query: str) -> str:
    messages = [
        {"role": "system", "content": "你是一个 AI 商城商品推荐助手。"},
        {
            "role": "user",
            "content": "推荐适合学生用的蓝牙耳机",
        },
        {
            "role": "assistant",
            "content": (
                "【推荐 1】小米 Buds 4 Pro\n"
                "  价格：¥999\n"
                "  特点：主动降噪、Hi-Fi音质、续航38小时\n"
                "  推荐理由：性价比高，适合学生日常使用\n\n"
                "【推荐 2】漫步者 W820NB\n"
                "  价格：¥349\n"
                "  特点：头戴式、降噪、40小时续航\n"
                "  推荐理由：价格实惠，音质优秀\n\n"
                "【推荐 3】OPPO Enco Air3\n"
                "  价格：¥299\n"
                "  特点：半入耳、低延迟、24小时续航\n"
                "  推荐理由：轻便舒适，适合长时间佩戴"
            ),
        },
        {"role": "user", "content": query},
    ]

    response = client.chat.completions.create(
        model="gpt-4o-mini",
        messages=messages,
        temperature=0.7,
    )
    return response.choices[0].message.content
```

## Chain-of-Thought (CoT)

引导模型逐步推理，提升复杂问题的准确性。

```python
def cot_recommend(query: str):
    messages = [
        {"role": "system", "content": "你是一个 AI 商城购物顾问。"},
        {
            "role": "user",
            "content": (
                f"用户需求：{query}\n\n"
                "请按以下步骤分析并推荐：\n"
                "1. 分析用户的核心需求是什么\n"
                "2. 列出候选商品类别\n"
                "3. 对比各选项的优缺点\n"
                "4. 给出最终推荐并说明理由"
            ),
        },
    ]
    # ...
```

## 输出格式控制

### JSON Mode

```python
response = client.chat.completions.create(
    model="gpt-4o-mini",
    messages=[
        {"role": "system", "content": "你是一个商品推荐助手。请以 JSON 格式输出推荐结果。"},
        {"role": "user", "content": "推荐 3 款适合编程的显示器"},
    ],
    response_format={"type": "json_object"},
)

result = json.loads(response.choices[0].message.content)
```

JSON 输出示例：

```json
{
  "recommendations": [
    {
      "name": "Dell U2723QE",
      "price": 4299,
      "specs": "4K IPS, 27寸, Type-C 90W",
      "reason": "色彩准确，适合长时间编程"
    },
    {
      "name": "Redmi 27寸 4K",
      "price": 1499,
      "specs": "4K IPS, 27寸, Type-C",
      "reason": "性价比高，入门首选"
    }
  ]
}
```

### 输出格式约束技巧

```python
system_prompt = """
请严格按以下格式输出推荐结果：

商品名称：[名称]
价格：[价格]
特点：[用逗号分隔的关键特点]
推荐理由：[一句话说明]

---
如果用户需求不明确，先询问以下信息：
1. 预算范围
2. 主要用途
3. 偏好品牌
"""
```

## 最佳实践

### 1. 编写清晰的 System Prompt

```
✅ 好的做法：
- 明确角色和职责
- 列出具体规则
- 示例输出格式
- 约束行为边界

❌ 不好的做法：
- 模糊的角色描述
- 过于宽泛的规则
- 没有输出格式说明
- 不设行为边界
```

### 2. 避免 Prompt 注入

```python
# 安全处理用户输入
user_input = input("请输入搜索内容：")
# 将用户输入作为内容，而不是指令
messages = [
    {"role": "system", "content": "你是 AI 商城助手，请回答用户关于商品的问题。"},
    {"role": "user", "content": f"用户的问题是：{user_input}"},
]
```

### 3. 迭代优化

1. **先写粗版**: 快速实现功能
2. **测试边界**: 检查极端输入
3. **收集失败案例**: 记录模型表现不佳的场景
4. **针对性优化**: 根据失败案例调整 Prompt
5. **A/B 测试**: 对比不同 Prompt 版本的效果

### 4. 常见的 Prompt 模式

| 模式 | 适用场景 | 示例 |
|------|----------|------|
| 角色扮演 | 客服、顾问 | "你是一个专业的产品顾问" |
| 步骤分解 | 复杂任务 | "请按以下步骤处理..." |
| 格式约束 | 结构化输出 | "请以 JSON 格式输出" |
| 示例引导 | 风格控制 | "参考以下示例的回答风格" |
| 思维链 | 推理任务 | "请逐步思考并给出答案" |
| 约束条件 | 安全控制 | "不要编造产品信息" |