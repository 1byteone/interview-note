# 📖 教程目录 — 从仓库导航到系统学习

> **版本**: v1.1 | **更新**: 2026-08-23 | **教程总数**: 23 篇（覆盖 E01-E06 全六大生态）

本目录是 [interview-note](../..) 仓库的教程中枢。与 [Guide 生态索引](../ecosystem-index.md) 的"知道有哪些仓库"不同，这里的教程解决"如何系统学习"的问题——每篇教程都是一篇完整的、可独立阅读的技术指南，覆盖从安装配置到生产部署的全链路。

---

## 🎯 教程体系概览

### 设计理念

```
Guide 生态索引（What）          Learn 技术栈（How 系统）
        ↓                              ↓
        └──────── 教程目录 ───────────┘
                 （Why + 实战）
```

- **Guide 生态索引**：告诉你"有哪些优质仓库"，是知识地图
- **Learn 技术栈**：告诉你"如何系统学习一门技术"，是课程大纲
- **教程目录**：两者结合，"为什么 + 怎么做 + 实战代码"，是深度教程

### 教程特点

1. **实战导向**：每篇教程包含可运行的代码示例和配置
2. **递进难度**：入门 → 进阶 → 高级，标注清晰的前置要求
3. **生态归属**：按 E01-E06 六大生态组织，与 Guide 索引一致
4. **交叉引用**：与 Guide 仓库详情、Learn 技术栈双向链接

---

## 📚 完整教程目录

### E01 · 🤖 Claude Code 生态

> Claude Code 是 Anthropic 推出的终端 AI 编程代理，Coding Agent 领域的标杆工具。

| # | 教程 | 等级 | 关键词 | 关联 Guide 仓库 |
|---|------|------|--------|----------------|
| 1 | [安装与基础使用](./e01-claude-code/01-installation-and-basics.md) | 入门 | 安装、认证、会话、项目配置 | claude-code-ultimate-guide, claude-code-guide |
| 2 | [Skills 开发实战](./e01-claude-code/02-skills-development.md) | 进阶 | SKILL.md、子代理、动态注入 | claude-code-ultimate-guide |
| 3 | [MCP 集成与外部工具扩展](./e01-claude-code/03-mcp-integration.md) | 进阶 | MCP Server、工具协议、配置 | claude-code-ultimate-guide |
| 4 | [Agent Teams 多 Agent 协作编排](./e01-claude-code/04-agent-teams.md) | 高级 | 多 Agent 编排、团队协作、任务协调 | claude-code-ultimate-guide |
| 5 | [Hooks 自动化](./e01-claude-code/05-hooks-automation.md) | 高级 | 生命周期钩子、PreToolUse 守卫、自动化 | claude-code-ultimate-guide |
| 6 | [Subagents 子代理与动态工作流](./e01-claude-code/06-subagents-and-workflows.md) | 高级 | 子代理隔离、.claude/agents、动态工作流 | claude-code-ultimate-guide |

**学习路径**：① → ② → ③ → ④（核心路径）｜ ⑤ → ⑥（进阶专题：确定性自动化与规模化委托）

---

### E02 · 🐙 Codex 生态

> Codex 是 OpenAI 推出的编码 Agent CLI，强调沙箱化执行与 AGENTS.md 配置。

| # | 教程 | 等级 | 关键词 | 关联 Guide 仓库 |
|---|------|------|--------|----------------|
| 1 | [快速入门与命令](./e02-codex/01-quickstart-and-commands.md) | 入门 | 安装、命令、配置 | CodexGuide |
| 2 | [AGENTS.md 与沙箱](./e02-codex/02-agents-md-and-sandbox.md) | 进阶 | AGENTS.md、沙箱、权限 | CodexGuide |
| 3 | [高级工作流](./e02-codex/03-advanced-workflows.md) | 高级 | 多步任务、自动化、CI/CD | CodexGuide |

**学习路径**：① → ② → ③（顺序推荐，从基础命令到生产工作流）

---

### E03 · ⚙️ DSH/Harness 生态

> Harness 工程理念：Agent 运行时 + 上下文管理 + 工具调用的工程化实践。DeepSeek Harness（DSH）是"Everything is a Plugin"范式的开源实现。

| # | 教程 | 等级 | 关键词 | 关联 Guide 仓库 |
|---|------|------|--------|----------------|
| 1 | [Harness 工程原理](./e03-dsh-harness/01-harness-engineering-principles.md) | 入门 | H=〈C,A,R〉模型、五大设计原则、参考实现 | harness_engineering_guide |
| 2 | [DSH 插件开发入门](./e03-dsh-harness/02-dsh-plugin-development.md) | 进阶 | DSH 架构、插件生命周期、Cordis 基础、8 步方法论 | deepeseek-harness-guide |
| 3 | [Cordis 运行时深度解析](./e03-dsh-harness/03-cordis-runtime-deep-dive.md) | 高级 | 时空可组合性、可逆效应、响应式依赖、上下文生命周期 | awesome-harness-engineering |

**学习路径**：① → ② → ③（从理论到框架实操到运行时深度）

---

### E04 · 🔗 Hermes/OpenClaw 生态

> Hermes Agent 是 Nous Research 推出的开源 Agent 框架，继承 OpenClaw 基因并全面升级。OpenClaw 安全实践首创 Agent 端零信任架构。

| # | 教程 | 等级 | 关键词 | 关联 Guide 仓库 |
|---|------|------|--------|----------------|
| 1 | [安装部署与架构解析](./e04-hermes-openclaw/01-installation-and-architecture.md) | 入门 | 安装、五层架构、模型配置、OpenClaw 迁移 | hermes-agent-guide |
| 2 | [技能系统与三层记忆详解](./e04-hermes-openclaw/02-skills-and-memory-system.md) | 进阶 | Skills 系统、47 内置工具、三层记忆 | awesome-hermes-agent |
| 3 | [Agent 零信任安全实践](./e04-hermes-openclaw/03-security-practices.md) | 高级 | 零信任三层防御、审计指标、灾难恢复 | openclaw-security-practice-guide |

**学习路径**：① → ② → ③（安装 → 使用 → 安全加固）

---

### E05 · 🌐 MCP 协议生态

> MCP（Model Context Protocol）是 Anthropic 提出的 AI Agent 工具调用标准协议，正成为跨平台 Agent 的通用接口层。

| # | 教程 | 等级 | 关键词 | 关联 Guide 仓库 |
|---|------|------|--------|----------------|
| 1 | [协议原理与核心概念](./e05-mcp/01-protocol-concepts.md) | 入门 | JSON-RPC、Resources/Prompts/Tools/Sampling、传输层 | MCP-Chinese-Getting-Started-Guide |
| 2 | [Server 开发实战（Python + FastMCP）](./e05-mcp/02-server-development.md) | 进阶 | FastMCP、@tool、@resource、@prompt、Inspector 调试 | MCP-Chinese-Getting-Started-Guide |
| 3 | [Client 集成与生产部署](./e05-mcp/03-client-integration.md) | 高级 | 客户端构建、Claude/Codex/LangChain 集成、Serverless 部署 | MCP-Chinese-Getting-Started-Guide |

**学习路径**：① → ② → ③（理解协议 → 开发服务 → 集成部署）

---

### E06 · 📚 通识与基础

> AI Agent 时代的通用技能：Prompt Engineering、设计模式、生产系统设计。这些知识跨工具、跨生态通用。

| # | 教程 | 等级 | 关键词 | 关联 Guide 仓库 |
|---|------|------|--------|----------------|
| 1 | [Prompt Engineering 实战](./e06-general/01-prompt-engineering-guide.md) | 入门→进阶 | Zero-shot、Few-shot、CoT、ReAct、ToT、Context Engineering | Prompt-Engineering-Guide |
| 2 | [AI Agent 设计模式](./e06-general/02-agent-design-patterns.md) | 进阶 | Reflection、Tool Use、Planning、Multi-agent、RAG、Memory、HITL | AgentGuide, ai-system-design-guide |
| 3 | [生产级 AI Agent 系统设计](./e06-general/03-production-system-design.md) | 高级 | RAG 管线、推理优化、评估监控、成本、安全、扩展 | ai-system-design-guide, ai-agent-interview-guide |
| 4 | [LangGraph 编排实战](./e06-general/04-langgraph-orchestration.md) | 进阶→高级 | StateGraph、多 Agent 架构、HITL、Checkpointing、LangSmith | AgentGuide |
| 5 | [高级 RAG 实战](./e06-general/05-advanced-rag-systems.md) | 高级 | 混合检索、重排序、查询变换、Adaptive RAG、GraphRAG、评估 | ai-system-design-guide |

**学习路径**：① → ② → ③（核心三篇）｜ ④（编排框架）与 ⑤（检索深度）为实战进阶，可与 ③ 并行

---

## 🛤️ 三条学习路径

根据你的角色和目标，我们推荐三条不同的学习路径。

### 🌱 路径一：初学者（建立全景认知）

**适合人群**：刚接触 AI Agent，想快速了解生态和上手工具

```
Step 1: 通识基础
├── E06-01 Prompt Engineering 实战（理解 AI 对话的本质）
└── E06-02 AI Agent 设计模式（理解 Agent 的工作原理）

Step 2: 工具上手
├── E01-01 Claude Code 安装与基础（动手使用 Agent）
└── E02-01 Codex 快速入门（对比另一种 Agent 工具）

Step 3: 扩展视野
├── 浏览 Guide 生态索引（了解有哪些优质资源）
└── 阅读 Guide-Learn 映射表（建立知识连接）
```

**预计耗时**：8-12 小时 | **产出**：能熟练使用 Claude Code / Codex 完成日常编码任务

---

### 🛠️ 路径二：实践者（构建自己的 Agent）

**适合人群**：有一定编程基础，想开发自己的 Agent 应用或 Skill

```
Step 1: 深度掌握工具
├── E01-02 Claude Code Skills 开发（开发可复用技能）
├── E01-03 MCP 集成（集成外部工具）
└── E02-02 Codex AGENTS.md 与沙箱（理解 Agent 配置）

Step 2: 掌握通用模式
├── E06-01 Prompt Engineering（优化 Agent 提示）
├── E06-02 Agent 设计模式（选择合适的架构）
└── E06-03 生产系统设计（构建可上线的系统）

Step 3: 实战项目
├── 参照 Guide 仓库详情（如 awesome-agent-skills）做项目
└── 结合 Learn 技术栈（如 LangChain）完成端到端实现
```

**预计耗时**：20-30 小时 | **产出**：能独立设计和开发一个生产可用的 Agent 系统

---

### 🏛️ 路径三：架构师（设计企业级系统）

**适合人群**：技术负责人、架构师，需要设计企业级 AI Agent 平台

```
Step 1: 理论根基
├── E06-01 Prompt Engineering（深入 Context Engineering 范式）
├── E06-02 Agent 设计模式（Multi-agent 架构对比）
└── E06-03 生产系统设计（重点关注扩展与安全章节）

Step 2: 工程深度
├── E01-02/03 Skills 与 MCP（企业级工具集成）
├── E02-03 Codex 高级工作流（CI/CD 集成）
└── 对照 Guide-Learn 映射表，补充 LangChain / RAG 等技术栈

Step 3: 治理与运营
├── 参考 Guide E04 Hermes/OpenClaw 生态（安全与合规）
├── 参考 Guide E03 Harness 生态（运行时工程化）
└── 建立内部 Agent 平台（参考 awesome-harness-engineering）
```

**预计耗时**：40+ 小时 | **产出**：能设计、实施和运营企业级 AI Agent 平台

---

## 🔗 与 Guide / Learn 的交叉引用

### 与 Guide 生态索引的关系

每篇教程的"关联 Guide 仓库"列指向 [Guide 生态索引](../ecosystem-index.md) 中的具体仓库详情。阅读顺序建议：

```
Guide 仓库详情（了解仓库定位） → 教程（系统学习该仓库涉及的技术）
```

### 与 Learn 技术栈的映射

参考 [Guide-Learn 双向映射表](../guide-learn-mapping.md)，以下是最常用的教程 ↔ Learn 技术栈对应关系：

| 教程 | 对应 Learn 技术栈 | 学习建议 |
|------|------------------|----------|
| E06-01 Prompt Engineering | L16 OpenAI API、L14 LangChain | 先学 Prompt 理论，再用 API/LangChain 实操 |
| E06-02 Agent 设计模式 | L14 LangChain、L15 RAG | 理论 + 框架实践并行 |
| E06-03 生产系统设计 | L12 Infrastructure、L15 RAG | 架构理论 + 基础设施实操 |
| E01-02 Skills 开发 | L14 LangChain | Skills 概念 + LangChain Agent 实现 |
| E01-03 MCP 集成 | L05 FastAPI、L16 OpenAI API | 先学 FastAPI 再学 MCP Server 开发 |

---

## 📋 推荐学习序列（按场景）

### 场景 1：面试准备（2 周冲刺）

```
Week 1:
- Day 1-2:  E06-01 Prompt Engineering（面试必问）
- Day 3-4:  E06-02 Agent 设计模式（架构题核心）
- Day 5-7:  E06-03 生产系统设计（系统设计题）

Week 2:
- Day 8-10: E01-01/02/03 Claude Code（工具实操题）
- Day 11-12: E02-01/02 Codex（对比与场景题）
- Day 13-14: 刷 Guide 中的 ai-agent-interview-guide
```

### 场景 2：快速上手 Claude Code（1 天）

```
- 上午: E01-01 安装与基础使用
- 下午: E01-02 Skills 开发实战
- 晚上: E01-03 MCP 集成（按需）
```

### 场景 3：构建 RAG 系统（3 天）

```
- Day 1: E06-01 Prompt Engineering（RAG 提示策略章节）+ E06-02 RAG 模式章节
- Day 2: E06-03 生产系统设计（RAG 管线设计章节）
- Day 3: 结合 Guide 的 ai-system-design-guide 仓库实战
```

### 场景 4：多 Agent 系统设计（5 天）

```
- Day 1: E06-02 Agent 设计模式（Multi-agent 章节）
- Day 2: E06-03 生产系统设计（架构总览 + 扩展策略）
- Day 3-4: 参考 Guide 的 awesome-hermes-agent、hermes-agent-guide
- Day 5: 动手实现一个简单的多 Agent 系统
```

---

## 🤝 贡献指南

欢迎为本教程体系贡献内容！请遵循以下规范：

1. **文件命名**：`{序号}-{主题}.md`，如 `04-evaluation-framework.md`
2. **前置元信息**：每篇教程开头使用统一格式
   ```markdown
   # 教程标题

   > **生态**: E0X · 生态名 | **等级**: 入门/进阶/高级 | **前置要求**: ...
   ```
3. **内容结构**：包含概述、原理、代码示例、实战案例、总结
4. **代码规范**：所有代码块标注语言，包含必要注释
5. **交叉引用**：引用其他教程时使用相对路径

详见 [贡献指南](../CONTRIBUTING.md)。

---

## 📌 快速导航

| 我想要... | 推荐教程 |
|---------|---------|
| 快速上手 Claude Code | [E01-01](./e01-claude-code/01-installation-and-basics.md) |
| 开发自己的 Skill | [E01-02](./e01-claude-code/02-skills-development.md) |
| 集成外部工具到 Agent | [E01-03](./e01-claude-code/03-mcp-integration.md) |
| 多 Agent 协作编排 | [E01-04](./e01-claude-code/04-agent-teams.md) |
| 在生命周期注入确定性逻辑 | [E01-05](./e01-claude-code/05-hooks-automation.md) |
| 用子代理做规模化委托 | [E01-06](./e01-claude-code/06-subagents-and-workflows.md) |
| 学习 Codex 的沙箱机制 | [E02-02](./e02-codex/02-agents-md-and-sandbox.md) |
| 理解 Harness 工程范式 | [E03-01](./e03-dsh-harness/01-harness-engineering-principles.md) |
| 开发 DSH 插件 | [E03-02](./e03-dsh-harness/02-dsh-plugin-development.md) |
| 深入 Cordis 运行时 | [E03-03](./e03-dsh-harness/03-cordis-runtime-deep-dive.md) |
| 安装 Hermes Agent | [E04-01](./e04-hermes-openclaw/01-installation-and-architecture.md) |
| 理解三层记忆系统 | [E04-02](./e04-hermes-openclaw/02-skills-and-memory-system.md) |
| 构建 Agent 安全体系 | [E04-03](./e04-hermes-openclaw/03-security-practices.md) |
| 理解 MCP 协议 | [E05-01](./e05-mcp/01-protocol-concepts.md) |
| 开发 MCP Server | [E05-02](./e05-mcp/02-server-development.md) |
| 部署 MCP 到生产 | [E05-03](./e05-mcp/03-client-integration.md) |
| 系统学习 Prompt Engineering | [E06-01](./e06-general/01-prompt-engineering-guide.md) |
| 了解 Agent 的 7 种设计模式 | [E06-02](./e06-general/02-agent-design-patterns.md) |
| 设计生产级 AI 系统 | [E06-03](./e06-general/03-production-system-design.md) |
| 用 LangGraph 编排多 Agent | [E06-04](./e06-general/04-langgraph-orchestration.md) |
| 构建生产级 RAG 管线 | [E06-05](./e06-general/05-advanced-rag-systems.md) |
| 了解有哪些优质仓库 | [Guide 生态索引](../ecosystem-index.md) |
| 找到技术栈学习路径 | [Guide-Learn 映射表](../guide-learn-mapping.md) |

---

> 💡 **提示**：本教程体系持续迭代中。如发现内容过时、错误或有改进建议，欢迎提交 Issue 或 PR。所有教程的源文件位于 [`guide/tutorials/`](./) 目录下。