# 面试速记版：20 个高频考点

## 1. 模型对比

| 模型 | 定位 | 价格 | 适合场景 |
|------|------|------|----------|
| GPT-4o | 旗舰多模态 | 高 | 复杂推理、代码、多模态 |
| GPT-4o-mini | 轻量低成本 | 低 | 简单对话、分类、提取 |
| o1 | 推理增强 | 高 | 数学、科学、编程竞赛 |
| o3 | 最新推理 | 最高 | 前沿科研、高难度问题 |

## 2. Chat Completions API 参数

- **messages**: 对话消息列表，含 system/user/assistant/tool 角色
- **temperature**: 0-2，控制随机性，0 确定，1 平衡，2 随机
- **max_tokens**: 最大输出 token 数
- **stream**: 是否流式输出
- **top_p**: 核采样，0.1 只考虑前 10% 概率的 token
- **frequency_penalty**: 频率惩罚，减少重复
- **presence_penalty**: 存在惩罚，鼓励新话题

## 3. 流式输出

```
stream = client.chat.completions.create(..., stream=True)
for chunk in stream:
    content = chunk.choices[0].delta.content
```

- 每个 chunk 包含一个 delta，最后 chunk 的 finish_reason 为 "stop"
- 适合需要实时展示的场景（搜索、对话、写作）

## 4. Function Calling 流程

1. 定义 tools（name, description, parameters）
2. 模型返回 tool_calls（function name + arguments）
3. 开发者执行实际函数
4. 将结果以 tool role 返回给模型
5. 模型生成最终回复

## 5. tool_choice 参数

| 值 | 行为 |
|----|------|
| "auto" | 模型自行决定 |
| "required" | 强制调用工具 |
| {"type": "function", "function": {"name": "xxx"}} | 强制调用指定工具 |

## 6. Token 计算

- 1 token ≈ 0.75 英文单词 ≈ 0.5 中文字符
- 使用 tiktoken 库计算：`tiktoken.encoding_for_model("gpt-4o").encode(text)`
- 计费分输入和输出两个维度

## 7. 结构化输出

- JSON Mode: `response_format={"type": "json_object"}`
- Structured Outputs: `response_format={"type": "json_schema", "json_schema": {...}}`
- 支持 Pydantic 集成，自动生成 Schema

## 8. Prompt Engineering 技巧

- **System Prompt**: 设定角色、规则、行为边界
- **Few-shot**: 提供示例引导输出格式
- **Chain-of-Thought**: "请逐步思考" 提升推理准确率
- **输出格式约束**: 明确要求 JSON 或特定格式

## 9. 微调

- 使用特定领域数据继续训练模型
- 数据格式：JSONL，含 messages 数组
- 关键参数：n_epochs, batch_size, learning_rate_multiplier
- 微调 vs Prompt Engineering：微调更稳定，但成本更高

## 10. Assistants API

- **Assistant**: 助手定义（模型、指令、工具）
- **Thread**: 对话线程，持久化上下文
- **Run**: 一次模型调用
- 内置工具：Code Interpreter、File Search

## 11. Batch API

- 异步批量处理，节省 50% 成本
- 准备 JSONL 请求文件 → 上传 → 创建 Batch → 等待完成 → 下载结果
- 适合非实时任务：数据标注、批量审核、离线分析

## 12. Prompt Caching

- 缓存重复的 Prompt 前缀（至少 1024 tokens）
- 缓存有效期 5-10 分钟
- 长 System Prompt 利用缓存效果显著

## 13. Moderation API

- 检测仇恨言论、骚扰、色情、暴力、自残等
- 支持分类和分数
- 建议多层安全策略：输入过滤 + Prompt 约束 + 输出过滤 + 业务规则

## 14. 成本控制策略

- 默认使用 GPT-4o-mini
- 复杂任务升级到 GPT-4o
- 非实时任务使用 Batch API
- 长 System Prompt 利用缓存
- 设置预算上限和用量告警

## 15. 多模型路由

```python
routing = {
    ("search", "simple"): "gpt-4o-mini",
    ("search", "complex"): "gpt-4o",
    ("reasoning", "any"): "o1",
}
```

## 16. 错误处理

| 错误 | 处理方式 |
|------|----------|
| RateLimitError | 退避重试 |
| APITimeoutError | 增加超时时间 |
| APIError | 检查 API Key 和请求参数 |
| Token 超限 | 截断历史或使用摘要 |

## 17. 微调 vs RAG

| 维度 | 微调 | RAG |
|------|------|-----|
| 知识更新 | 需要重新训练 | 更新向量库即可 |
| 幻觉 | 无法消除 | 检索增强减少幻觉 |
| 成本 | 训练 + 托管 | 向量库 + Token 成本 |
| 适用场景 | 固定风格/格式 | 知识密集型问答 |

## 18. 模型幻觉处理

- 使用 grounding 检查事实
- 设置 System Prompt 约束
- 使用 Function Calling 获取真实数据
- 添加置信度评分

## 19. 安全最佳实践

- 输入输出双重过滤
- 敏感信息脱敏（PII）
- 设置合理的审核阈值
- 保留审核日志
- 提供申诉机制

## 20. API 限流

- 按组织/API Key 分级限流
- 使用退避重试策略
- 实现请求队列
- 监控 Token 使用率