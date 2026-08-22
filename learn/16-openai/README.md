# OpenAI — 模型 · API · Function Calling · 微调 · 成本

## 学习路径图

```
                        👶 初学者路径                          🎯 进阶路径
                    ┌─────────────────┐                 ┌─────────────────────┐
                    │ 01-quick-start  │                 │ structured-output   │
                    │ API 基础 + 流式  │ ──────────────► │ JSON Schema + 校验   │
                    └────────┬────────┘                 └──────────┬──────────┘
                             │                                     │
                             ▼                                     ▼
                    ┌─────────────────┐                 ┌─────────────────────┐
                    │ models-pricing  │                 │ fine-tuning         │
                    │ 模型选型与成本   │                 │ 微调原理与实战       │
                    └────────┬────────┘                 └──────────┬──────────┘
                             │                                     │
                             ▼                                     ▼
                    ┌─────────────────┐                 ┌─────────────────────┐
                    │ prompt-engineering│                │ assistants-api      │
                    │ System/User/Few-shot              │ Thread/Run/Code Int │
                    └────────┬────────┘                 └──────────┬──────────┘
                             │                                     │
                             ▼                                     ▼
                    ┌─────────────────┐                 ┌─────────────────────┐
                    │ function-calling│                 │ batch-and-cache     │
                    │ 工具调用实战     │                 │ 批量处理 + 缓存优化  │
                    └────────┬────────┘                 └──────────┬──────────┘
                             │                                     │
                             ▼                                     ▼
                    ┌─────────────────┐                 ┌─────────────────────┐
                    │ mall-integration│                 │ safety-moderation   │
                    │ 商城多模型路由    │                 │ 内容安全与审核       │
                    └─────────────────┘                 └─────────────────────┘

                                    ┌──────────────────────────────┐
                                    │      mini-blog 智能写作助手    │
                                    │  综合项目：Prompt + FC + 流式  │
                                    └──────────────────────────────┘

                                    ┌──────────────────────────────┐
                                    │  面试准备：quick-revision    │
                                    │  deep-dive / scenario / coding│
                                    └──────────────────────────────┘
```

## 前置知识

- Python 3.8+ 基础语法
- HTTP 请求与 JSON 理解
- 基本命令行操作

## 面试高频考点一览表

| 考点 | 难度 | 出现频率 | 说明 |
|------|------|----------|------|
| GPT-4o vs GPT-4o-mini 选型 | ⭐⭐ | ★★★★★ | 模型对比与成本权衡 |
| Chat Completions API 参数 | ⭐⭐ | ★★★★★ | messages, temperature, stream |
| 流式输出实现 | ⭐⭐⭐ | ★★★★☆ | SSE 协议与客户端处理 |
| Function Calling | ⭐⭐⭐ | ★★★★★ | 工具定义与多轮调用 |
| Structured Output | ⭐⭐⭐ | ★★★★☆ | JSON Schema 与校验 |
| Token 计算与优化 | ⭐⭐ | ★★★★☆ | tiktoken 库使用 |
| Prompt Engineering | ⭐⭐⭐ | ★★★★★ | System/User/Few-shot/COT |
| 微调原理与流程 | ⭐⭐⭐⭐ | ★★★☆☆ | 数据集准备与 API 调用 |
| Assistants API | ⭐⭐⭐⭐ | ★★★☆☆ | Thread/Run 架构 |
| Batch API | ⭐⭐⭐ | ★★☆☆☆ | 批量处理优化 |
| Prompt Caching | ⭐⭐⭐ | ★★★☆☆ | 缓存命中与成本节省 |
| 安全与审核 | ⭐⭐⭐ | ★★★☆☆ | Moderation API |
| 成本控制策略 | ⭐⭐⭐ | ★★★★☆ | 多模型路由与缓存 |
| 微调 vs RAG 选型 | ⭐⭐⭐⭐ | ★★★★☆ | 场景化决策 |
| 模型幻觉处理 | ⭐⭐⭐ | ★★★★☆ | 输出验证与 grounding |

## OpenAI 在 AI 商城的角色

### AI 搜索
- 基于 GPT-4o-mini 的低延迟商品搜索
- 流式输出搜索结果，提升用户体验
- Function Calling 对接商品搜索引擎

### 智能客服
- 多轮对话上下文管理
- 订单查询与售后处理（Function Calling）
- 情感分析与意图识别

### 商品推荐
- 基于用户画像的个性化推荐
- 多模型路由：简单推荐用 4o-mini，复杂推理用 o1
- 实时价格与库存查询

### 内容审核
- Moderation API 过滤违规内容
- 用户评论自动审核
- 商品描述合规检查

---

## 📖 导航

| ← 上一篇 | 📚 目录 | 下一篇 → |
|----------|---------|----------|
| [← RAG](../15-rag/README.md) | [📚 总目录](../README.md) | [AI 智能商城 →](../projects/ai-mall/README.md) |

**相关技术栈：**
- [14-LangChain](../14-langchain/README.md) — LangChain 封装 OpenAI API 提供链式调用与工具编排
- [05-FastAPI](../05-fastapi/README.md) — FastAPI 作为 AI 网关路由 OpenAI 模型调用请求

---

## 项目剖析深度参考

本 learn 文档提供理论基础，以下 `docs/tech-stack-analysis/` 文档提供**真实项目中的落地代码**：

| 本 learn 核心内容 | 对应项目剖析 | 重点看什么 |
|------------------|------------|-----------|
| OpenAI 兼容协议接入 | [04-LLM-PROVIDER.md](../../docs/tech-stack-analysis/mall-ai-search/04-LLM-PROVIDER.md) | 阿里云通义千问 + Agnes AI + `enable_thinking=False` |
| Embedding 多供应商 | [05-EMBEDDING.md](../../docs/tech-stack-analysis/mall-ai-search/05-EMBEDDING.md) | BGE-M3 + SiliconFlow + OpenRouter 切换 |
| Structured Output | [07-LANGCHAIN-AGENT.md](../../docs/tech-stack-analysis/mall-ai-search/07-LANGCHAIN-AGENT.md) | `response_format=PydanticModel` 结构化输出 |
| SQL 验证器 | [05-SQL-VALIDATOR.md](../../docs/tech-stack-analysis/text2sql/05-SQL-VALIDATOR.md) | 四层验证：语法/安全/语义/性能 |