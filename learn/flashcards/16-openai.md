# OpenAI — 面试抽认卡

> 来源：`learn/16-openai/05-interview/`

---

### Card 1: Token 计算与定价
**维度**: 📝速记 | **难度**: ⭐⭐

> **Q: OpenAI 的 Token 是如何计算的？如何估算成本？**

**A:** 1 Token ≈ 0.75 英文单词 ≈ 0.5 中文字符。使用 `tiktoken` 库计算：`tiktoken.encoding_for_model("gpt-4o").encode("Hello World")`。计费分输入和输出两个维度（输出更贵）。成本估算：`tokens_per_request × 每日请求数 × 单价`。示例：GPT-4o 输入 $2.50/M tokens，输出 $10/M tokens，每次 1000 输入+200 输出，1 万次/天，每天成本 ≈ $45。GPT-4o-mini 便宜 30 倍（输入 $0.15/M，输出 $0.60/M），适合大部分场景。

---

### Card 2: Function Calling 原理
**维度**: 🔬深挖 | **难度**: ⭐⭐⭐

> **Q: Function Calling 的实现流程是怎样的？tool_choice 参数如何控制？**

**A:** 流程：① 定义 tools（name, description, parameters 用 JSON Schema 描述）；② 模型返回 `tool_calls`（function name + arguments），不执行实际函数；③ 开发者执行业务函数；④ 将结果以 `tool` role 返回给模型；⑤ 模型生成最终自然语言回复。`tool_choice: "auto"`（模型自行决定）、`"required"`（强制调用工具）、`{"type": "function", "function": {"name": "search"}}`（强制调用指定工具）。`parallel_tool_calls` 支持一次调用多个工具（并行执行）。

---

### Card 3: Structured Output
**维度**: 💻代码 | **难度**: ⭐⭐

> **Q: OpenAI 的结构化输出（Structured Outputs）和 JSON Mode 有什么区别？**

**A:** JSON Mode（`response_format={"type": "json_object"}`）保证输出是合法 JSON，但不保证 JSON 结构符合预期 Schema。Structured Outputs（`response_format={"type": "json_schema", "json_schema": {"name": "mySchema", "schema": {...}}}`）保证输出完全符合 JSON Schema 定义。Structured Outputs 支持嵌套对象、数组、枚举、可选字段等复杂结构。结合 Pydantic：`response_model=MyModel`（OpenAI SDK 自动生成 Schema 并解析响应）。推荐：Structured Outputs 比 JSON Mode 更可靠。

---

### Card 4: JSON Mode 与 Pydantic
**维度**: 💻代码 | **难度**: ⭐⭐

> **Q: 如何在 OpenAI API 中使用 Pydantic 模型处理结构化输出？**

**A:** `from openai import OpenAI; client = OpenAI()`。定义 Pydantic 模型：`class User(BaseModel): name: str; age: int`。调用：`completion = client.beta.chat.completions.parse(model="gpt-4o", messages=[...], response_format=User)`。`completion.choices[0].message.parsed` 返回 Pydantic 实例。`refusal` 字段检查模型是否拒绝回答。`parse` 方法自动处理 JSON 解析和 Pydantic 校验，失败时抛出异常。`response_format` 也支持 `type="json_schema"` 手动指定 Schema。

---

### Card 5: 微调 vs RAG
**维度**: 📝速记 | **难度**: ⭐⭐

> **Q: 微调和 RAG 的适用场景分别是什么？如何选择？**

**A:** 微调：让模型学习特定风格、格式、行为模式（如客服话术、邮件模板、代码规范），训练后模型"内化"知识。RAG：外挂知识库，实时更新知识，可溯源。对比：微调成本高（训练+托管），更新慢（需重新训练），无法消除幻觉；RAG 成本低，更新快，可解释。选型指南：固定格式/风格 → 微调；知识密集型 → RAG；两者结合 → RAG 检索 + 微调优化输出（如 RAG 检索 + 微调模型回答风格）。

---

### Card 6: Batch API
**维度**: 📝速记 | **难度**: ⭐⭐

> **Q: OpenAI Batch API 的优点是什么？如何使用？**

**A:** Batch API 异步批量处理，成本降低 50%。适合非实时任务：数据标注、批量审核、离线分析、大规模分类。使用流程：① 准备 JSONL 请求文件（每行一个完整的 Chat Completions 请求，含 `custom_id`）；② 上传文件：`client.files.create(file=..., purpose="batch")`；③ 创建 Batch：`client.batches.create(input_file_id=..., endpoint="/v1/chat/completions", completion_window="24h")`；④ 等待完成，轮询状态；⑤ 下载结果文件。结果文件包含每行的 `custom_id` 和 `response`。24 小时内完成，无超时退款。

---

### Card 7: Prompt Caching
**维度**: 🔬深挖 | **难度**: ⭐⭐

> **Q: OpenAI Prompt Caching 的工作原理是什么？如何利用它节省成本？**

**A:** Prompt Caching 自动缓存重复的 Prompt 前缀（至少 1024 tokens），缓存命中时输入价格降低 50%。缓存有效期约 5-10 分钟（最近使用过）。利用策略：① 长 System Prompt 放在开头（如 2000 tokens 的 System Prompt，后续请求复用缓存）；② 共享上下文前缀（如多轮对话中，历史消息在前面，新消息在后面）；③ 固定模板（Few-shot 示例放在 Prompt 开头，多个请求复用）。缓存自动生效，无需额外配置。`usage.prompt_tokens_details.cached_tokens` 查看缓存命中量。

---

### Card 8: 安全防护与 Moderation
**维度**: 🎯场景 | **难度**: ⭐⭐

> **Q: OpenAI 应用的安全防护策略有哪些？**

**A:** ① Moderation API：检测仇恨言论、骚扰、色情、暴力、自残等，支持分类和分数；② 输入过滤（检查用户输入是否包含 Prompt 注入、SQL 注入、XSS 攻击）；③ 输出过滤（检查模型输出是否包含敏感信息、违规内容）；④ Prompt 约束（System Prompt 中设定行为边界，如"不要回答非法问题"）；⑤ 多层安全策略（输入过滤 → Moderation → Prompt 约束 → 输出过滤 → 业务规则）；⑥ 速率限制（用户级别限流，防止滥用）。

---

### Card 9: 成本优化策略
**维度**: 🎯场景 | **难度**: ⭐⭐⭐

> **Q: 如何优化 OpenAI 的使用成本？**

**A:** ① 模型选择：默认用 GPT-4o-mini，复杂任务升级到 GPT-4o；② Batch API：非实时任务用 Batch，节省 50%；③ Prompt Caching：长 System Prompt 复用缓存；④ 上下文压缩：减少历史消息数，用摘要代替完整对话历史；⑤ 缓存常见查询：Redis 缓存常见问题的 LLM 回答；⑥ 输出 Token 限制：`max_tokens` 设置合理上限（如 500），避免浪费；⑦ 输入 Token 限制：精简 Prompt，移除不必要的示例；⑧ 预算监控：设置用量告警（如每月 $1000 告警），`usage` 字段记录每次调用的 Token 消耗。

---

### Card 10: 流式输出实现
**维度**: 💻代码 | **难度**: ⭐⭐

> **Q: OpenAI 流式输出的完整实现是什么样的？如何处理流式事件？**

**A:** `stream = client.chat.completions.create(model="gpt-4o", messages=[...], stream=True)`。`for chunk in stream:` 遍历每个 chunk。`chunk.choices[0].delta.content` 获取文本增量（可能为 None）。`chunk.choices[0].finish_reason` 完成原因（`stop`、`length`、`content_filter`、`tool_calls`）。`chunk.usage` 只在最后 chunk 包含完整 Token 计数。流式 Function Calling：`chunk.choices[0].delta.tool_calls` 增量返回工具调用参数。SSE 格式：`data: {json}\n\n` 逐行推送。

---

### Card 11: Assistants API
**维度**: 📝速记 | **难度**: ⭐⭐

> **Q: OpenAI Assistants API 的核心概念是什么？Thread 和 Run 如何工作？**

**A:** Assistant（助手定义：模型、指令、工具）、Thread（对话线程，持久化上下文）、Run（一次模型调用，异步执行）。流程：创建 Assistant → 创建 Thread → 添加 Message（用户提问）→ 创建 Run（触发模型推理）→ 轮询 Run 状态（`requires_action` 触发工具调用）→ 提交 Tool Outputs → 完成。Assistant 内置工具：Code Interpreter（沙箱执行 Python 代码）、File Search（RAG 搜索上传文件）、Function Calling（自定义工具）。`run_id` 和 `thread_id` 追踪状态。

---

### Card 12: 模型选择策略
**维度**: 🎯场景 | **难度**: ⭐⭐

> **Q: 如何选择合适的 OpenAI 模型？多模型路由策略如何设计？**

**A:** 模型选择：GPT-4o（旗舰，复杂推理/代码/多模态）、GPT-4o-mini（轻量，简单对话/分类/提取，30 倍便宜）、o1（推理增强，数学/科学/编程竞赛）、o3（最新推理，前沿科研）。多模型路由：`{"search_simple": "gpt-4o-mini", "search_complex": "gpt-4o", "reasoning": "o1"}`。策略：简单查询（分类、提取）→ mini；复杂查询（推理、分析）→ 4o；高难度问题（数学、编程竞赛）→ o1/o3。`max_tokens` 和 `temperature` 也按场景调整：分类用 temperature=0，创意写作用 0.8。

---

### Card 13: 限流处理
**维度**: 🎯场景 | **难度**: ⭐⭐

> **Q: 如何处理 OpenAI API 的限流（Rate Limit）？**

**A:** 限流类型：RPM（每分钟请求数）、TPM（每分钟 Token 数）、RPD（每日请求数，Free Tier）。处理策略：① 退避重试（指数退避：第一次 1s，第二次 2s，第三次 4s...最大 60s）；② 请求队列（在客户端排队，控制并发数）；③ Token 预算（计算每次请求的 Token 消耗，控制总速率）；④ 多 API Key 轮询（多个 Key 分散请求）；⑤ 预留容量（Tier 5 可购买预留容量，保证稳定）。`openai.RateLimitError` 捕获并重试。`max_retries` 参数控制 SDK 自动重试次数。

---

### Card 14: 幻觉缓解
**维度**: 🎯场景 | **难度**: ⭐⭐⭐

> **Q: 如何缓解 OpenAI 模型的幻觉问题？**

**A:** ① 降低温度（temperature=0 减少随机性）；② 提供上下文（RAG 检索相关文档作为参考）；③ Prompt 约束（"仅基于提供的资料回答，不知道就说不知道"）；④ 引用溯源（要求回答标注证据来源）；⑤ 多轮反问（"请确认您的理解是否正确"）；⑥ 自我校验（让模型检查自己的回答是否一致）；⑦ 输出门控（检查回答中是否有模型不确定的内容，安全过滤）；⑧ 领域限定（明确模型的知识边界，避免回答超出领域的问题）。

---

### Card 15: 多模态能力
**维度**: 📝速记 | **难度**: ⭐⭐

> **Q: GPT-4o 的多模态能力有哪些？如何传入图片/音频？**

**A:** GPT-4o 支持文本、图片、音频输入。图片输入：`{"role": "user", "content": [{"type": "text", "text": "描述这张图片"}, {"type": "image_url", "image_url": {"url": "https://..."}}]}`。支持 Base64 编码的图片数据。`detail: "high"` 启用高细节模式（更多 Token 消耗）。音频输入：`{"type": "input_audio", "input_audio": {"data": base64_audio, "format": "wav"}}`。GPT-4o 不能直接生成图片（用 DALL-E 3），不能直接生成音频（用 TTS）。多模态适合：图片分析、图表解读、文档 OCR、音频转文字分析。