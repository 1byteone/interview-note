# 📚 E06 · AI Agent 通识与基础

> **生态定位**: 跨生态的通用知识，覆盖 Agent 开发、面试、系统设计、理论基础  
> **生态规模**: 10 个核心仓库 | 交叉关联 E01/E02/E03/E04/E05（全生态交叉）  
> **技术本质**: 所有 Agent 生态的公共基础层

---

## 1. 生态全景

### 1.1 生态定位

本生态收录不特属于某个技术栈，而是所有 Agent 开发者都需要掌握的通用知识：

| 层级 | 角色 | 仓库 | 说明 |
|------|------|------|------|
| L1 🧠 | 理论基础 | Prompt-Engineering-Guide | 提示工程圣经（77,672⭐） |
| L1 🧠 | 理论基础 | MCP-Chinese-Getting-Started-Guide | MCP 协议入门（已归 E05，此处交叉引用） |
| L2 🎓 | 求职体系 | AgentGuide | Agent 求职一站式（8,639⭐） |
| L2 🎓 | 面试题库 | ai-agent-interview-guide | 200+ 八股文 + 三语言项目 |
| L2 🎓 | 学习路径 | ai-agents-from-zero | 速成指南（3,974⭐） |
| L2 🎓 | 导师工具 | ai-agent-daily-mentor | Agent 教 Agent 学习 |
| L3 🏗️ | 系统设计 | ai-system-design-guide | 生产级 AI 系统设计 |
| L3 🐍 | 语言基础 | python-guide | Python 最佳实践 |
| L4 ⚡ | 生态索引 | awesome-agent-skills (×2) | Skills 资源合集 |
| L4 ⚡ | 生态索引 | awesome-skills-cn | 中文 Skills 索引 |
| L4 ⚡ | 工具 | AI-Shell | AI 命令行助手 |

### 1.2 知识层次

```
L4 生态索引
    └── awesome-agent-skills, awesome-skills-cn, AI-Shell (工具发现)

L3 系统设计 & 语言基础
    └── ai-system-design-guide, python-guide（架构思维 + 工程能力）

L2 求职 & 学习路径
    └── AgentGuide, ai-agent-interview-guide, ai-agents-from-zero, ai-agent-daily-mentor

L1 理论基础
    └── Prompt-Engineering-Guide（所有 Agent 的基石）
```

---

## 2. 核心仓库详解

### 2.1 🧠 L1 理论层

#### dair-ai/Prompt-Engineering-Guide ⭐77,672

| 字段 | 值 |
|------|-----|
| **定位** | 提示工程圣经，业界最全面公开资源 |
| **内容体量** | 网站体量 + 13 种语言 + 视频课程 |
| **独特价值** | 300 万+ 学习者，16+ 高级技术覆盖（CoT, ToT, ReAct, RAG, APE） |

**核心章节**：
- 基础 → 16+ 技术（Zero-Shot → Graph Prompting）
- 应用（Function Calling, Code Generation, Data Generation）
- Prompt Hub（分类/Coding/Creativity/Evaluation 等可直接复用模板）
- 模型专题（ChatGPT, Code Llama, Flan）

**在生态中的角色**：所有 Agent 开发者的理论基础，建议作为第一站。

### 2.2 🎓 L2 求职与学习层

#### adongwanai/AgentGuide ⭐8,639

| 字段 | 值 |
|------|-----|
| **定位** | 对标 JavaGuide 的 AI Agent 学习指南，100% 求职导向 |
| **内容体量** | 约 58,000 字入口文档 + 多级 docs/ 目录 |
| **独特价值** | 算法岗 × 开发岗双线路径，1500+ 面试题，1-2-5 求职范式 |

**核心能力**：LangGraph, OpenAI Agents SDK, MCP, Skills, Context Engineering, RAG, Post-training

#### bcefghj/ai-agent-interview-guide ⭐2,119

| 字段 | 值 |
|------|-----|
| **定位** | 面试全攻略，9 大模块 200+ 道八股文 |
| **独特价值** | Python/Java/Go 三语言企业级项目，哆啦 A 梦漫画图解 |

#### didilili/ai-agents-from-zero ⭐3,974

| 字段 | 值 |
|------|-----|
| **定位** | 2026 最系统的 AI Agent 速成指南 |
| **技术栈** | LangChain, LangGraph, Coze, Dify, MCP |

#### Marcos-wu/ai-agent-daily-mentor ⭐133

| 字段 | 值 |
|------|-----|
| **定位** | 以 Agent Skill 形式提供学习引导，新颖的「Agent 教 Agent」方式 |

### 2.3 🏗️ L3 系统设计层

#### ombharatiya/ai-system-design-guide ⭐2,698

| 字段 | 值 |
|------|-----|
| **定位** | Staff 级 AI 系统设计参考，Living Document |
| **内容体量** | 20 个目录 + 2 个深度指南 |
| **独特价值** | 128 道面试题 + 15 个案例研究，实时跟踪到 2026 年 8 月 |

**核心章节**：LLM 原理 → 模型选型 → 训练 → 推理 → RAG → Agentic → 记忆 → 框架 → MLOps → 安全 → 评估 → 多模态

#### realpython/python-guide ⭐29,770

| 字段 | 值 |
|------|-----|
| **定位** | Python 最佳实践手册（Hitchhiker's Guide to Python） |
| **独特价值** | 有观点的指南，告诉「该用什么、为什么」 |

### 2.4 ⚡ L4 生态索引层

| 仓库 | Stars | 说明 |
|------|-------|------|
| heilcheng/awesome-agent-skills | 6,126 | Agent Skills 教程、指南和目录 |
| libukai/awesome-agent-skills | 4,998 | Agent Skills 终极指南（中英双语） |
| lingxling/awesome-skills-cn | 257 | 7000+ Skills 中文学习版 |
| by123456by/AI-Shell | 15 | 自然语言驱动的 Linux 命令行助手 |

---

## 3. 交叉引用

| 关联生态 | 关联点 |
|----------|--------|
| **E01 Claude Code** | AgentGuide 用 Claude Code 作为 Agent 开发工具；Prompt 是 Claude Code 基础 |
| **E02 Codex** | Prompt 是 Codex 基础；AgentGuide 的技能体系通用 |
| **E03 Harness** | ai-system-design-guide 的 Agentic 系统设计覆盖 Harness 架构 |
| **E04 Hermes** | AgentGuide 的框架对比涉及 Hermes |
| **E05 MCP** | ai-system-design-guide 的 MCP 2.0 分析；AgentGuide 的 MCP 实践 |

---

## 4. 生态内学习路径

> 📖 配套教程：[E06 通识与基础教程系列](../tutorials/e06-general/)（6 篇，从 Prompt 到 Agent 评估）

```
① Prompt-Engineering-Guide (理论基础，1 周)
    ↓
② AgentGuide (求职体系，双线路径，1-2 周)
    ↓
③ ai-agents-from-zero (项目实战，1 周)
    ↓
④ ai-system-design-guide (系统设计，1-2 周)
    ↓
⑤ ai-agent-interview-guide (面试冲刺，1 周)
    ↓
⑥ 进入具体生态 (E01/E02/E03/E04)
```

**教程推荐顺序**：
1. [Prompt Engineering 实战](../tutorials/e06-general/01-prompt-engineering-guide.md) — 从 Zero-shot 到 Context Engineering
2. [AI Agent 设计模式](../tutorials/e06-general/02-agent-design-patterns.md) — 7 种生产级架构模式
3. [生产级 AI Agent 系统设计](../tutorials/e06-general/03-production-system-design.md) — RAG/推理优化/评估监控
4. [LangGraph 编排实战](../tutorials/e06-general/04-langgraph-orchestration.md) — 状态图与多 Agent 协作
5. [高级 RAG 实战](../tutorials/e06-general/05-advanced-rag-systems.md) — 混合检索/重排序/Adaptive RAG
6. [Agent 评估体系](../tutorials/e06-general/06-agent-evaluation.md) — 从 RAG 质量到生产级监控

---

## 5. 生态 SWOT

| 优势 | 劣势 |
|------|------|
| 覆盖最广（从理论到求职到系统设计） | 内容分散，需要自行串接 |
| 仓库质量高（Prompt Engineering 77k, AgentGuide 8k） | 部分仓库深度不足 |
| 跨生态通用，不受平台绑定 | 缺少统一的「学习大纲」 |

| 机会 | 威胁 |
|------|------|
| 成为 Agent 开发者的入门标准路径 | 各生态发展出自己的学习路径 |
| 可与 E01-E05 形成互补闭环 | 内容过时风险（AI 领域变化快） |