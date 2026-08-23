# 推荐资源

## 官方文档

| 资源 | 链接 | 说明 |
|------|------|------|
| OpenAI 官方文档 | https://platform.openai.com/docs | 最新 API 参考 |
| API 参考 | https://platform.openai.com/docs/api-reference | 完整端点文档 |
| 模型定价页 | https://platform.openai.com/pricing | 实时价格查询 |
| 状态页面 | https://status.openai.com | 服务状态监控 |
| Cookbook | https://cookbook.openai.com | 官方示例代码库 |

## 核心文档专题

- Chat Completions Guide — 对话生成完整指南
- Function Calling Guide — 工具调用最佳实践
- Structured Outputs Guide — 结构化输出规范
- Prompt Engineering Guide — 提示词工程六大策略
- Fine-tuning Guide — 微调数据准备与训练
- Assistants API 文档 — Thread/Run 架构详解
- Batch API 文档 — 批量处理与定价
- Moderation Guide — 审核 API 使用指南
- Safety Best Practices — 安全最佳实践（重点阅读）
- Tokenizer 工具 — 官方 token 计算器

## 开发资源

### Python SDK
```bash
pip install openai
pip install tiktoken     # token 计算
pip install pydantic     # 结构化输出集成
```

- OpenAI Python SDK: https://github.com/openai/openai-python
- tiktoken: https://github.com/openai/tiktoken

### Java / 其他语言
- OpenAI Java SDK（社区）
- openai-node (官方 Node.js)

## 学习社区

- OpenAI 开发者论坛: https://community.openai.com
- GitHub 示例仓库: https://github.com/openai
- Anthropic/OpenAI 对比学习（了解行业动态）
- 中文技术社区：掘金、思否、知乎（搜索 "OpenAI Function Calling"）

## 进阶主题

### LLM 原理
- 论文：《Attention Is All You Need》（Transformer 原始论文）
- 论文：《Language Models are Few-Shot Learners》（GPT-3）
- 论文：《Training language models to follow instructions》（InstructGPT / RLHF）

### 工程化实践
- 向量数据库：Pinecone、Milvus、Weaviate
- RAG 框架：LangChain、LlamaIndex
- 提示词工程：PromptPerfect、OpenAI Prompt Guide

### 成本与监控
- LiteLLM（多模型代理与成本统计）
- Helicone（LLM 观测平台）
- Langfuse（LLM 可观测性）

## 面试准备

- 本教程 05-interview/ 目录全部练习一遍
- 动手实现一个带 Function Calling 的小项目（如 mini-blog）
- 关注 OpenAI 官方博客的模型更新公告
- 了解不同模型的定价差异和选型逻辑

## 注意事项

1. API Key 永不提交到代码仓库，使用环境变量
2. 生产环境务必设置超时和重试
3. 关注定价变动，定期复核模型选型
4. 涉及安全场景必须使用审核 API
5. 中文内容注意 token 消耗更高（约 0.5-1 token/字）