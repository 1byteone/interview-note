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

### 分类目录

```
guide/
├── README.md                       # 本文件 — 生态总览
├── categories/                     # 六大技术栈生态分类
│   ├── 01-ecosystem-claude-code.md        # E01 Claude Code
│   ├── 02-ecosystem-codex.md              # E02 Codex
│   ├── 03-ecosystem-dsh-harness.md        # E03 DSH/Harness
│   ├── 04-ecosystem-hermes-openclaw.md    # E04 Hermes/OpenClaw
│   ├── 05-ecosystem-mcp.md                # E05 MCP 协议
│   └── 06-ecosystem-general-agent.md      # E06 通识与基础
├── repositories/                   # 仓库详情（每库一个文件）
├── data/                           # 专业交付物
│   ├── standards.md                # 收录标准与质量评估
│   ├── landscape.md                # 领域全景图
│   ├── learning-paths.md           # 学习路线建议
│   ├── tech-radar.md               # 技术雷达
│   └── cross-reference.md          # 生态交叉引用矩阵
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

### 场景 1：我是 AI Agent 初学者
```
E06 通识 → AgentGuide/Prompt-Engineering-Guide（理论基础）
  → 选一个生态深入：E01 (Claude Code) 或 E02 (Codex)
  → E05 MCP（理解 Agent 工具调用）
```

### 场景 2：我是后端工程师，想转型 AI
```
E06 → python-guide（语言补强）
  → E03 Harness 工程（理解 Agent 本质）
  → E01/E02（上手 Coding Agent）
```

### 场景 3：我是资深开发者，想深入 Agent 架构
```
E03 Harness 工程（理论体系）
  → E04 Hermes/OpenClaw（开源框架深度）
  → E05 MCP（协议深度）
  → E06 ai-system-design-guide（系统设计）
```

---

> **维护者**: @1byteone  
> **收录日期**: 2026-08-22  
> **收录仓库数**: 20  
> **生态分类**: 6 大技术栈生态  
> **排除**: yupi/* 系列仓库