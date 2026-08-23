# 🗺️ Guide 仓库收录指南

> **专业收录 · 生态分类 · 场景驱动**  
> 基于今天（2026-08-22）Starred 的 `guide` 系列仓库，按**技术栈生态**分类整合的知识库。

---

## 🎯 专业目标

本指南以**技术栈生态**为分类维度，构建跨生态、可交叉引用的 guide 知识库：

```
                  ┌─────────────────────────────────┐
                  │      AI Agent 全生态地图         │
                  └─────────────────────────────────┘
                               │
      ┌────────────┬───────────┼───────────┬────────────┐
      ▼            ▼           ▼           ▼            ▼
┌─────────┐  ┌─────────┐  ┌─────────┐  ┌─────────┐  ┌─────────┐
│ E01     │  │ E02     │  │ E03     │  │ E04     │  │ E06     │
│ Claude  │  │ Codex   │  │ DSH/    │  │ Hermes/ │  │ 通识与  │
│ Code    │  │ 生态    │  │ Harness │  │ OpenClaw│  │ 基础    │
└─────────┘  └─────────┘  └─────────┘  └─────────┘  └─────────┘
      │           │           │           │            │
      └───────────┴─────┬─────┴───────────┴────────────┘
                        ▼
                   E05 MCP 协议（全生态基础设施）
```

---

## 🌐 六大技术栈生态

| 生态 | 名称 | 核心定位 | 核心仓库 | Stars |
|------|------|----------|----------|-------|
| **E01** | 🤖 Claude Code 生态 | 最主流 Coding Agent | claude-code-ultimate-guide, claude-code-guide, claudecode-guide | 10,400 |
| **E02** | 🐙 Codex 生态 | OpenAI Coding Agent | CodexGuide | 3,234 |
| **E03** | ⚙️ DSH/Harness 生态 | 新一代 Agent 运行时 | harness_engineering_guide, deepeseek-harness-guide, awesome-harness-engineering | 4,019 |
| **E04** | 🔗 Hermes/OpenClaw 生态 | 开源社区 Agent 框架 | hermes-agent-guide, openclaw-security-practice-guide, awesome-hermes-agent | 8,912 |
| **E05** | 🌐 MCP 协议生态 | 跨平台工具调用协议 | MCP-Chinese-Getting-Started-Guide | 3,560 |
| **E06** | 📚 通识与基础 | 跨生态通用知识 | AgentGuide, Prompt-Engineering-Guide, ai-system-design-guide 等 | 125,000+ |

---

## 📊 快速导航

### 你想用哪个工具/框架？

| 我的需求 | 去哪个生态 |
|----------|-----------|
| 学习使用 Claude Code | [E01 Claude Code 生态](categories/01-ecosystem-claude-code.md) |
| 学习使用 Codex | [E02 Codex 生态](categories/02-ecosystem-codex.md) |
| 理解 Harness / 开发 DSH 插件 | [E03 DSH/Harness 生态](categories/03-ecosystem-dsh-harness.md) |
| 学习 Hermes / OpenClaw | [E04 Hermes/OpenClaw 生态](categories/04-ecosystem-hermes-openclaw.md) |
| 学习 MCP 协议 | [E05 MCP 协议生态](categories/05-ecosystem-mcp.md) |
| 想入门 Agent / 面试准备 | [E06 通识与基础](categories/06-ecosystem-general-agent.md) |
| 想快速总览所有仓库 | [📊 生态总索引](ecosystem-index.md) |
| 想看 Guide ↔ Learn 映射 | [🔗 双向映射表](guide-learn-mapping.md) |

### 分类目录

新增 **[`tutorials/`](tutorials/README.md)** 深度教程目录（23 篇，覆盖 E01-E06 全六大生态），从仓库导航升级为可系统学习的教程内容体系：

| 生态 | 教程数 | 核心主题 |
|------|--------|----------|
| [E01 Claude Code](tutorials/e01-claude-code/) | 6 篇 | 安装基础 / Skills 开发 / MCP 集成 / Agent Teams / Hooks 自动化 / Subagents |
| [E02 Codex](tutorials/e02-codex/) | 3 篇 | 快速上手 / AGENTS.md+沙箱 / 高级工作流 |
| [E03 DSH/Harness](tutorials/e03-dsh-harness/) | 3 篇 | Harness 原理 / 插件开发 / Cordis 运行时 |
| [E04 Hermes/OpenClaw](tutorials/e04-hermes-openclaw/) | 3 篇 | 安装架构 / 技能记忆 / 零信任安全 |
| [E05 MCP 协议](tutorials/e05-mcp/) | 3 篇 | 协议概念 / Server 开发 / Client 部署 |
| [E06 通识与基础](tutorials/e06-general/) | 5 篇 | Prompt Engineering / 设计模式 / 生产系统 / LangGraph 编排 / 高级 RAG |

> 📖 教程目录详见 [`tutorials/README.md`](tutorials/README.md)

```
guide/
├── README.md                       # 本文件 — 生态总览
├── ecosystem-index.md              # 📊 生态总索引（按生态/Stars/角色/层级速查）
├── guide-learn-mapping.md          # 🔗 Guide ↔ Learn 双向映射表
├── categories/                     # 六大技术栈生态分类
│   ├── 01-ecosystem-claude-code.md        # E01 Claude Code
│   ├── 02-ecosystem-codex.md              # E02 Codex
│   ├── 03-ecosystem-dsh-harness.md        # E03 DSH/Harness
│   ├── 04-ecosystem-hermes-openclaw.md    # E04 Hermes/OpenClaw
│   ├── 05-ecosystem-mcp.md                # E05 MCP 协议
│   └── 06-ecosystem-general-agent.md      # E06 通识与基础
├── tutorials/                      # 📖 深度教程（新增！从仓库导航到系统学习）
│   ├── README.md                   # 教程目录与学习路径导航
│   ├── e01-claude-code/            # E01 Claude Code（4 篇）
│   ├── e02-codex/                  # E02 Codex（3 篇）
│   ├── e03-dsh-harness/            # E03 DSH/Harness（3 篇）
│   ├── e04-hermes-openclaw/        # E04 Hermes/OpenClaw（3 篇）
│   ├── e05-mcp/                    # E05 MCP 协议（3 篇）
│   └── e06-general/                # E06 通识与基础（3 篇）
├── repositories/                   # 仓库详情（每库一个文件）
├── data/                           # 专业交付物
│   ├── standards.md                # 收录标准与质量评估
│   ├── landscape.md                # 领域全景图
│   ├── learning-paths.md           # 学习路线建议
│   ├── tech-radar.md               # 技术雷达
│   └── cross-reference.md          # 生态交叉引用矩阵
├── assets/                         # 生态图谱（Mermaid）
└── guide_repos.json                # 结构化数据
```

---

## 🏛️ 收录标准

详见 [收录标准与质量评估](data/standards.md)，核心原则：

- 仓库名包含 `guide` 教育属性关键词
- 内容与 AI Agent / Coding Agent / Harness / MCP 等专业领域相关
- Stars ≥ 100，文档完整性 ≥ 3 章节
- ⚠️ 排除：yupi/* 系列仓库（暂不收录）

---

## 🚀 场景导航

> 3 秒定位 — 你是什么角色，直接走哪条路

### 👶 我是 AI Agent 初学者，从零开始

```
📌 目标：12 周达到求职水平
① E06 Prompt-Engineering-Guide  (理论基础，1周)
② E06 AgentGuide                 (求职体系，双线路径，2周)
③ E01 claudecode-guide → claude-code-guide  (上手 Coding Agent，2周)
④ E06 ai-system-design-guide     (系统设计，2周)
⑤ E06 ai-agent-interview-guide   (面试冲刺，2周)
```

### 🔄 我是后端工程师，想转型 AI

```
📌 目标：8 周完成转型
① E06 python-guide                (语言补强，1周)
② E03 harness_engineering_guide   (理解 Agent 本质，2周)
③ E01 claude-code-ultimate-guide  (Coding Agent 工具链，2周)
④ E04 hermes-agent-guide          (开源框架探索，2周)
⑤ E06 ai-agent-interview-guide    (面试准备，1周)
```

### 🏗️ 我是资深开发者，深入 Agent 架构

```
📌 目标：深入架构能力
① E06 ai-system-design-guide      (系统设计，3周)
② E03 harness_engineering_guide   (Harness 理论，3周)
③ E04 openclaw-security-practice-guide (安全体系，2周)
④ E01/E02/E04 框架对比            (选型能力，2周)
```

### 🎯 我要面试 Agent 岗位

```
📌 目标：快速冲刺
① E06 AgentGuide                   (求职体系 + 简历)
② E06 ai-agent-interview-guide      (200+ 八股文)
③ E06 ai-system-design-guide        (128 道系统设计题)
④ E01 claude-code-ultimate-guide    (Agent 深度理解)
```

### 💰 我是独立开发者/创业者

```
📌 目标：探索变现机会
① E04 hermes-agent-guide (第14册)  (九大变现路径，1周)
② E01 claude-code-ultimate-guide   (提升开发效率，1周)
③ E05 MCP-Chinese-Getting-Started-Guide (MCP 服务开发，3天)
```

### 🔒 我关注 Agent 安全

```
📌 目标：安全体系构建
① E04 openclaw-security-practice-guide  (零信任三层防御)
② E01 claude-code-ultimate-guide        (28 CVE 威胁库)
③ E03 harness_engineering_guide Part 4  (安全体系设计)
```

---

> 📖 完整学习路线详见 [`data/learning-paths.md`](data/learning-paths.md)  
> 📊 生态总索引详见 [`ecosystem-index.md`](ecosystem-index.md)

---

> **维护者**: @1byteone  
> **收录日期**: 2026-08-22  
> **收录仓库数**: 27  
> **生态分类**: 6 大技术栈生态  
> **总 Stars**: 186,650