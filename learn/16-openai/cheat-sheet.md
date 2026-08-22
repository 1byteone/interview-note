# OpenAI API 速查卡 📋

> 面试前30分钟扫一遍，一页纸覆盖核心考点

## 🏷️ 核心概念速记

| 概念 | 一句话解释 | 常见陷阱 |
|------|-----------|----------|
| Chat Completions | 聊天补全 API，messages 数组驱动对话 | system 消息会被用户消息覆盖行为，注意角色作用域 |
| Token | 文本切分的最小单元(约4字符/中文字)，计费单位 | 中文1字≈1-2 token，prompt+completion 都计费 |
| Temperature | 采样温度(0-2)，越高随机性越强 | 事实性任务用 0-0.3，创意任务 0.7-1.0，不能两者兼得 |
| Top-p | 核采样：保留概率累积到 p 的最小 token 集 | temperature 和 top-p 都改会叠加效果，通常只调一个 |
| System / User / Assistant | 三种消息角色：系统设定、用户输入、模型回复 | assistant 消息需保留(多轮对话上下文需要) |
| Function Calling | 模型输出结构化函数调用意图，不执行函数 | 模型只"提议"调用，执行权在你(安全边界) |
| Structured Output | 强制输出符合 JSON Schema 的结构化结果 | 复杂嵌套 schema 须严格遵循 JSON Schema 语法 |
| 微调 (Fine-tuning) | 用标注数据继续训练模型，改善特定风格/格式 | 数据量少效果不明显；不解决"知识不足"(用 RAG) |
| Batch API | 异步批量处理，24h 内完成，价格 50% off | 一次性提交上限、完成后下载结果文件 |
| Embeddings | 文本→向量，相似度检索/聚类/分类 | embedding 模型与 chat 模型不同，维度与 token 限制不同 |
| 流式 (Streaming) | SSE 逐 token 返回，降低首 token 延迟 | 流式模式下函数调用/工具结果需聚合完整消息再解析 |
| 成本优化 | 模型选型(gpt-4o-mini vs gpt-4o) + 缓存 + 批量 | prompt 里放无关 token 白花钱，长 prompt 缓存命中可打折 |

## 🔧 常用命令/API

```python
# Chat Completions 标准模板
from openai import OpenAI

client = OpenAI(api_key="sk-...", base_url="https://api.openai.com/v1")

response = client.chat.completions.create(
    model="gpt-4o-mini",
    messages=[
        {"role": "system", "content": "你是一个资深后端工程师助手"},
        {"role": "user", "content": "解释一下 MVCC 原理"},
    ],
    temperature=0.3,          # 事实性任务低温度
    max_tokens=512,            # 限制输出长度，防超时/防爆费
    stream=False,
)

print(response.choices[0].message.content)
print(response.usage)          # prompt_tokens / completion_tokens / total_tokens
```

```python
# Function Calling 定义模板（核心考点）
tools = [
    {
        "type": "function",
        "function": {
            "name": "get_weather",
            "description": "查询指定城市的实时天气",
            "parameters": {
                "type": "object",
                "properties": {
                    "city": {
                        "type": "string",
                        "description": "城市名称，如 北京"
                    },
                    "unit": {
                        "type": "string",
                        "enum": ["celsius", "fahrenheit"],
                        "description": "温度单位"
                    }
                },
                "required": ["city"]
            }
        }
    }
]

# 1. 第一次调用：模型返回 tool_calls 意图
response = client.chat.completions.create(
    model="gpt-4o-mini",
    messages=[{"role": "user", "content": "北京天气怎么样？"}],
    tools=tools,
    tool_choice="auto"
)

# 2. 取出工具调用意图
tool_calls = response.choices[0].message.tool_calls

# 3. 应用侧执行对应函数
result = get_weather(tool_calls[0].function.arguments["city"])

# 4. 把工具结果回填给模型，得到最终答复
messages = [
    {"role": "user", "content": "北京天气怎么样？"},
    response.choices[0].message,          # assistant 的 tool_calls 消息
    {
        "role": "tool",
        "tool_call_id": tool_calls[0].id,
        "content": str(result)
    }
]
final = client.chat.completions.create(model="gpt-4o-mini", messages=messages)
print(final.choices[0].message.content)
```

```python
# 流式输出（Streaming / SSE）
stream = client.chat.completions.create(
    model="gpt-4o-mini",
    messages=[{"role": "user", "content": "写一首关于夏天的诗"}],
    stream=True,
)

for chunk in stream:
    delta = chunk.choices[0].delta
    if delta and delta.content:
        print(delta.content, end="", flush=True)  # 逐 token 输出
```

```python
# Structured Output（强制 JSON Schema）
from pydantic import BaseModel

class OrderInfo(BaseModel):
    order_id: str
    amount: float
    items: list[str]
    status: str

response = client.beta.chat.completions.parse(   # 结构化输出端点
    model="gpt-4o-mini",
    messages=[{"role": "user", "content": "解析订单：12345 金额99.9 包含手机和耳机 已支付"}],
    response_format=OrderInfo,
)
print(response.choices[0].message.parsed)        # → OrderInfo 对象
```

```python
# Token 计数 & 成本预估
import tiktoken

enc = tiktoken.encoding_for_model("gpt-4o-mini")
tokens = enc.encode("hello world 你好世界")
print(len(tokens))                      # 预计算 token 数，控制成本

# 成本估算: prompt_tokens * 输入单价 + completion_tokens * 输出单价
```

## 🎯 面试高频 TOP10

1. **Q: Token 怎么计算？** **A:** 模型内置 tokenizer(如 tiktoken)，prompt + completion 都计费；中文约 1 字=1-1.5 token(实际看 tokenizer)；可用 tiktoken 本地预估，也可看 usage 响应字段。
2. **Q: Function Calling 原理？** **A:** 定义 tools(JSON Schema) → 模型判断需要工具时返回结构化 tool_calls(函数名+参数) → 应用执行函数 → 把结果作为 tool 消息回给模型 → 模型生成最终回答；模型不执行函数，只"提议"。
3. **Q: 微调 vs RAG？** **A:** 微调改变模型行为(风格/格式/领域术语)，弥补能力；RAG 提供动态外部知识(最新信息/私有知识)；一般先用 RAG，稳定后微调做兜底。
4. **Q: 成本怎么优化？** **A:** ① 选便宜模型(gpt-4o-mini 起步) ② prompt 压缩/去重 ③ 缓存命中(相同前缀请求打折) ④ Batch API 便宜 50% ⑤ 限制 max_tokens ⑥ prompt-cached 语义最小化。
5. **Q: 如何防护 prompt injection / 敏感信息？** **A:** ① 系统提示强约束 ② 输出过滤器(Jailbreak 检测) ③ function calling 边界(权限最小化) ④ 用户输入脱敏/隔离 ⑤ 上下文划分(系统/用户/工具消息权限隔离)。
6. **Q: temperature 和 top_p 什么区别？** **A:** temperature 平滑概率分布(高=更随机)，top_p 截断尾部低概率 token(累计概率 p)；一般只调一个，事实任务低 temperature，创意任务高。
7. **Q: 多轮对话怎么管理上下文？** **A:** 保留 system + 最近的 user/assistant 历史(受 max tokens 限制)，超出截断/摘要；用 token 计数监控上下文长度。
8. **Q: 模型输出不稳定怎么处理？** **A:** Structured Output 强制 schema + 温度调低 0 + 多次采样取最佳 + 后处理校验(pydantic) + 重试策略。
9. **Q: Batch API 什么时候用？** **A:** 非实时任务(数据标注/批量分类/离线生成) 用 Batch API，48h 内完成且价格减半；实时对话仍用同步/流式接口。
10. **Q: API 限速和错误处理？** **A:** 429(限流)指数退避重试、500/503 重试、超时重试；流式用 timeout+SSE 心跳；用 usage 指针监控配额和成本。

## ⚠️ 常见坑 & 最佳实践

| ❌ 坑 | ✅ 正确做法 |
|-------|------------|
| API Key 硬编码进前端/仓库 | 服务端保管，放环境变量/Git 忽略，用服务器代理转发 |
| 忽略 usage 字段盲目烧钱 | 监控 prompt/completion token，设预算上限 |
| 温度过高答非所问 | 事实型任务 temperature=0，创意型才调高 |
| 单次对话塞超长历史 | Token 超限报错；用摘要/截断控制上下文 |
| 流式不处理中断 | 用 timeout + 心跳 + 重连/缓存已生成部分 |
| Function Calling 不设工具白名单 | 只暴露必需工具，参数做服务端校验，防止注入 |
| 输出不校验直接展示 | 用 Structured Output + 服务端校验，防御 prompt injection |
| 多轮对话忘记保留 assistant 消息 | 不保留则模型没有上下文，回答断链 |

## 📐 架构设计要点

- **安全**：API Key 服务端管理、工具调用权限隔离、输出内容过滤、速率限制。
- **成本治理**：模型分级(简单任务用小模型) + token 监控 + 缓存 + 预算告警。
- **可靠性**：重试(指数退避) + 超时 + 降级(模型降级到小模型/兜底回复) + 可观测(链路追踪)。
- **上下文管理**：system 常量 + 多轮截断摘要 + 工具结果回填 + 记忆(外部存储) 分层管理。
- **评测**：离线评测集 + 线上 A/B + 用户反馈，持续迭代 prompt 和模型选型。

## 🔗 关联技术

- **LangChain**：封装 OpenAI 为熟悉的组件(Model/Chain/Tool/Function Calling)。
- **RAG**：OpenAI Embeddings 做向量化，检索知识注入 Chat API。
- **FastAPI**：作为 OpenAI 调用的服务端包装层，管理 Key/限流/缓存。
- **Redis**：对话记忆、函数调用结果缓存、一致哈希会话分配。
- **监控**：Token 用量、错误率、延迟指标接入 Prometheus/Grafana，成本看板。