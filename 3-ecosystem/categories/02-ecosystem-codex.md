# 🐙 E02 · OpenAI Codex 生态

> **生态定位**: OpenAI 的 Coding Agent 平台，Claude Code 的强劲竞品  
> **生态规模**: 1 个核心仓库 + 交叉生态引用 | 交叉关联 E01/E05/E06  
> **技术本质**: Codex CLI + AGENTS.md + Sandbox 沙盒

---

## 1. 生态全景

### 1.1 生态定位

Codex 是 OpenAI 推出的 AI 编程代理，核心组件包括：

| 角色 | 仓库 | 说明 |
|------|------|------|
| 🏆 官方指南 | freestylefly/CodexGuide | Codex 实践指南（中文标杆） |
| 🔍 生态索引 | （待补） | 暂无专门 awesome 合集 |

### 1.2 生态规模

本生态目前收录 1 个核心仓库（CodexGuide），但通过交叉引用与 Claude Code 生态（E01）、MCP（E05）紧密关联。

---

## 2. 核心仓库详解

### 2.1 🏆 freestylefly/CodexGuide

| 字段 | 值 |
|------|-----|
| **全名** | freestylefly/CodexGuide |
| **Stars** | 3,234 |
| **定位** | 面向全球初学者、创作者、开发者与团队的 Codex 实践指南 |
| **语言** | 中文 + 英文 |
| **内容体量** | VuePress 文档站（6 个目录、数十个页面） |
| **独特价值** | 多入口使用地图（CLI/桌面/IDE/Cloud/ChatGPT）、AGENTS.md 模板、团队 Playbook |

**核心章节**：
- `guide/` — 学习路线
- `start/` — 快速上手（桌面 App、CLI 安装）
- `advanced/` — 进阶（AGENTS.md、沙盒与审批、团队 Playbook）
- `recipes/` — 实战案例库
- `manual/` — 参考手册

**技术栈**：Codex CLI, AGENTS.md, Sandbox, IDE 插件

---

## 3. 交叉引用

| 关联生态 | 关联仓库 | 关联点 |
|----------|----------|--------|
| **E01 Claude Code** | claude-code-ultimate-guide | 竞品对比（Codex vs Claude Code 架构差异） |
| **E01 Claude Code** | mshadmanrahman/claudecode-guide | 竞品对比章节（6 项对比） |
| **E05 MCP** | MCP-Chinese-Getting-Started-Guide | Codex 与 MCP 服务器集成 |
| **E06 通识** | Prompt-Engineering-Guide | Codex 中 Prompt 工程实践 |
| **E06 通识** | ai-system-design-guide | Agentic 框架选型对比 |

---

## 4. 生态内学习路径

> 📖 配套教程：[E02 Codex 教程系列](../tutorials/e02-codex/)（4 篇，从快速上手到多 Agent 并行工作树）

```
① CodexGuide start/ (快速上手，30 分钟)
    ↓
② CodexGuide advanced/ (AGENTS.md、沙盒、团队)
    ↓
③ CodexGuide recipes/ (实战案例，按需)
    ↓
④ 与 Claude Code 对比 → E01 (EC 对比)
```

**教程推荐顺序**：
1. [快速入门与命令指南](../tutorials/e02-codex/01-quickstart-and-commands.md) — 安装配置与审批模式
2. [AGENTS.md 配置与沙盒安全模型](../tutorials/e02-codex/02-agents-md-and-sandbox.md) — 项目指令与安全边界
3. [高级工作流：MCP 集成与多 Agent 编排](../tutorials/e02-codex/03-advanced-workflows.md) — 生产级工作流
4. [多 Agent 并行工作树：Git Worktrees 实战](../tutorials/e02-codex/04-multi-agent-worktrees.md) — 大规模并行重构

---

## 5. 生态 SWOT

| 优势 | 劣势 |
|------|------|
| OpenAI 背书，模型能力强 | 生态文档相对 Claude Code 少 |
| 中文指南完善（CodexGuide） | 社区资源不如 Claude Code 丰富 |
| 多入口覆盖（IDE/CLI/桌面） | 新特性迭代快，文档易过时 |

| 机会 | 威胁 |
|------|------|
| AGENTS.md 逐步成为跨 agent 标准 | Claude Code 占据主流心智 |
| Sandbox 安全模型领先 | 开源替代品成本更低 |