# LangSmith — 可观测 · 评测 · Prompt 管理

> 面向后端开发者与 AI 工程师的 LangSmith 实战教程，覆盖 **Tracing（可观测）→ Evaluation（评测）→ Prompt Hub（提示词管理）→ 生产监控与反馈闭环** 的完整链路。
> 场景项目：AI 智能商城（mall-ai-search 商品推荐 + RAG 问答）与农业知识库问答智能体。
> 配套题库：`LangChain_LangGraph_LangSmith_面试QA_详解版.md`（第三章 LangSmith 15 题）。

---

## 学习路径图

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                  LangSmith 学习路径（五层体系）                                 │
├──────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  01-basics 👶                                                               │
│  ┌──────────────────────────┐                                               │
│  │ 快速上手                  │                                               │
│  │ 环境变量 · 自动 Tracing    │                                               │
│  │ LangSmith 控制台初识      │                                               │
│  └──────────────────────────┘                                               │
│              ▼                                                               │
│  02-core 👶→🎯                                                              │
│  ┌────────────────────────┐  ┌──────────────────────────────┐               │
│  │ Tracing 概念            │  │ Dataset + 评测流程           │               │
│  │ Trace / Run / Thread   │  │ 评估器三态 · Experiment      │               │
│  └────────────────────────┘  └──────────────────────────────┘               │
│              ▼                                                               │
│  03-advanced 🎯                                                              │
│  ┌──────────────────┐ ┌──────────────────┐ ┌──────────────────┐             │
│  │ LLM-as-Judge      │ │ Prompt Hub       │ │ Feedback / 自动化 │             │
│  │ 裁判校准 · 自定义   │ │ 版本管理 · 热更新  │ │ Annotation Queue  │             │
│  └──────────────────┘ └──────────────────┘ └──────────────────┘             │
│              ▼                                                               │
│  04-project 🎯                                                               │
│  ┌──────────────────────────────────────────┐                                │
│  │ 商城 RAG 评测 + 农业知识库可观测落地       │                                │
│  │ 评测集 · LLM 裁判 · 反馈闭环 · CI 回归     │                                │
│  └──────────────────────────────────────────┘                                │
│              ▼                                                               │
│  05-interview 📝                                                             │
│  ┌──────────────────────────────────────────┐                                │
│  │ 速记 · 深挖 · 场景（与 QA 详解版互链）      │                                │
│  └──────────────────────────────────────────┘                                │
│                                                                              │
└──────────────────────────────────────────────────────────────────────────────┘
```

---

## 前置知识

- **Python / LangChain**：本教程以 LangChain 1.x / LangGraph 应用为追踪与评测对象，建议先完成 `14-langchain` 教程
- **RAG 基础**：评测场景以检索增强问答为主，可参考 `15-rag` 教程
- **概念认知**：先明确"铁三角"分工——LangChain 构建（造）、LangGraph 编排（跑）、LangSmith 验证（看 + 量）

---

## 面试高频考点一览表

| 考点 | 重要程度 | 频次 | 说明 | 章节 |
|------|----------|------|------|------|
| 铁三角定位（LangChain/LangGraph/LangSmith） | ⭐⭐⭐⭐⭐ | 高频 | 造 / 跑 / 看 分工，LangChain 构建·LangGraph 编排·LangSmith 验证 | 01-basics |
| Tracing 概念（Trace / Run / Thread） | ⭐⭐⭐⭐⭐ | 高频 | 会话→执行→步骤的层级，瀑布视图调试 | 02-core/01 |
| 接入方式与环境变量 | ⭐⭐⭐⭐ | 中频 | TRACING_V2 / API_KEY / PROJECT，零侵入自动上报 | 01-basics |
| Dataset 与 Experiment 评测流程 | ⭐⭐⭐⭐⭐ | 高频 | 考卷 / 考生 / 判卷 / 成绩单模型 | 02-core/02 |
| LLM-as-Judge 与校准 | ⭐⭐⭐⭐⭐ | 高频 | 裁判 prompt、few-shot、人类反馈对齐、防漂移 | 03-advanced/01 |
| 自定义评估器（Run/Example 签名） | ⭐⭐⭐⭐ | 中频 | score / key / comment 返回结构 | 03-advanced/01 |
| Prompt Hub 版本管理 | ⭐⭐⭐⭐ | 中频 | 版本化 / 回滚 / 热更新 / 绑评测 | 03-advanced/02 |
| 反馈闭环（Feedback + Annotation Queue） | ⭐⭐⭐⭐ | 中频 | 生产负反馈 → 评测集 → 改进飞轮 | 03-advanced/03 |
| 生产监控指标分层 | ⭐⭐⭐ | 低频 | 质量 / 健康 / 成本三层 | 03-advanced/03 |
| LangSmith vs Langfuse 选型 | ⭐⭐⭐ | 中频 | 生态绑定 vs 开源自托管 | 03-advanced/03 |

---

## LangSmith 在 AI 工程链路中的角色

```
┌────────────────────────────────────────────────────────────────────┐
│                 LangSmith 验证闭环（开发 → 生产）                     │
├────────────────────────────────────────────────────────────────────┤
│                                                                    │
│  开发期   Tracing ── 调试链路 / 定位失败 / 优化 prompt               │
│              │                                                      │
│              ▼                                                      │
│  评测期   Dataset + Experiment ── 量化质量 / 对比模型与 prompt       │
│              │                                                      │
│              ▼                                                      │
│  生产期   Monitoring + Feedback + Automation                       │
│          在线评估 · 用户反馈 · 异常告警 · 负样本沉淀                  │
│              │                                                      │
│              └──→ 负反馈回流入 Dataset（评测集"活"起来）→ 再评测       │
│                                                                    │
└────────────────────────────────────────────────────────────────────┘
```

**一句话概括**：LangSmith 让 AI 应用从"玄学调试"变成"可观测、可评测、可回归"的工程产物——它是 LLM 应用的质量与运维底座。

---

## 📖 导航

| ← 上一篇 | 📚 目录 | 下一篇 → |
|----------|---------|----------|
| [← LangChain](../14-langchain/README.md) | [📚 总目录](../../README-learning.md) | [RAG →](../15-rag/README.md) |

**相关技术栈：**
- [14-LangChain](../14-langchain/README.md) — 被追踪与评测的应用构建框架
- [15-RAG](../15-rag/README.md) — 评测场景（忠实度 / 相关性 / 检索质量）
- [16-OpenAI](../16-openai/README.md) — 评测中的裁判模型与目标模型

---

## 图表示例（Mermaid 源文件）

本教程内嵌的 Mermaid 图表源文件统一存放于 [`_assets/diagrams/langsmith/`](../../../_assets/diagrams/langsmith/)（kebab-case 命名，渲染已验证）：

| 图表 | 位置 | 主题 |
|------|------|------|
| `eval-flow-0.mmd` | 02-core/02-evaluation.md | Dataset→目标函数→评估器→Experiment 评测流程 |
| `prompt-release-0.mmd` | 03-advanced/02-prompt-hub.md | Prompt 发布纪律（先评测→切 prod→回滚） |
| `feedback-loop-0.mmd` | 03-advanced/03-feedback-automation.md | 生产反馈→标注→评测集→改进→回归飞轮 |

---

## 参考资料

- [LangSmith 官方文档](https://docs.langchain.com/langsmith)（Tracing / Evaluation / Prompt Hub）
- [LangChain v1 发布说明](https://docs.langchain.com/oss/python/releases/langchain-v1)（create_agent / Middleware）
- [LangSmith Evaluation Concepts](https://docs.langchain.com/langsmith/evaluation-concepts)
- [LangSmith vs Langfuse 官方对比](https://www.langchain.com/resources/langsmith-vs-langfuse)
- [LangSmith 12 种评估方法详解（中文）](https://zhuanlan.zhihu.com/p/1935065663346544823)
- [Agent 质量评估实践（AWS）](https://aws.amazon.com/cn/blogs/china/agent-quality-evaluation/)
