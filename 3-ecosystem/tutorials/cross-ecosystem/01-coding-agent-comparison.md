# Coding Agent 横评：Claude Code vs Codex vs Gemini CLI

> **生态**: 跨生态对比 | **等级**: 入门→进阶 | **前置要求**: 了解 CLI 基本操作，至少使用过一种 Coding Agent

Coding Agent 正在重塑软件开发工作流。2025 年，三款终端 AI 编程代理——Claude Code（Anthropic）、Codex CLI（OpenAI）和 Gemini CLI（Google）——形成了三足鼎立的格局。它们共享"AI 助手 + 终端工具"的产品形态，但在架构哲学、安全模型、生态成熟度和适用场景上存在显著差异。

本教程从 Harness 工程范式出发，对这三款工具进行系统性横评，涵盖架构对比、功能矩阵、多 Agent 能力、企业就绪度和成本分析，帮助你在不同场景下做出正确的工具选型决策。

---

## 1. 全景对比表

| 维度 | Claude Code | Codex CLI | Gemini CLI |
|------|-------------|-----------|------------|
| **开发商** | Anthropic | OpenAI | Google |
| **默认模型** | Claude Sonnet 4.5 | GPT-5.3-Codex | Gemini 3 Flash |
| **认证方式** | Anthropic API Key | ChatGPT 订阅 / API Key | Google 账号 |
| **审批模式** | 逐次确认 / 自动接受 / 绕过权限 | 自动 / 只读 / 完全访问 | 沙箱等级控制 |
| **MCP 支持** | 完整（STDIO + HTTP） | 完整（STDIO + HTTP） | 支持 |
| **多 Agent** | Agent Teams（实验性）+ Subagents | 实验性多 Agent（Worktrees） | 不支持 |
| **代码审查** | 手动（/review 命令） | 内置（/review 命令） | 无 |
| **会话恢复** | 支持（--resume + 精确 ID） | 支持（--resume） | 不支持 |
| **开源** | 否（闭源 + 插件化） | 是（Rust，MIT 许可证） | 是（Apache 2.0） |
| **实现语言** | TypeScript | Rust | 未公开 |
| **免费额度** | 无（按 API 用量计费） | 无（ChatGPT Pro 捆绑） | 慷慨免费层 |
| **上下文窗口** | 200K tokens | 128K tokens | 约 500MB 输入（约 1M+ tokens） |

---

## 2. 架构深度对比：Harness 范式视角

Harness 工程将智能体定义为 `Agent = LLM + Harness`，其中 Harness 由三层组成：`H = 〈C, A, R〉`（Control Layer / Agency Layer / Runtime Layer）。以下从这三个维度分析各工具的架构差异。

### 2.1 控制层（Control Layer）

控制层负责会话管理、上下文编排和策略执行。

| 能力 | Claude Code | Codex CLI | Gemini CLI |
|------|-------------|-----------|------------|
| 会话管理 | 成熟：保存/恢复/列出历史会话 | 支持：--resume 恢复最近会话 | 基本：无持久化会话 |
| 上下文编排 | CLAUDE.md 自动注入 + 记忆文件 + 三级设置 | AGENTS.md 项目引导 + 系统提示 | 依赖项目内提示配置 |
| 策略执行 | Hook 系统（PreToolUse/PostToolUse） | 沙箱等级（Auto/Read-only/Full） | 沙箱等级（0-3） |
| 生命周期钩子 | 完善：命令钩子、MCP 钩子 | 基本：无暴露钩子系统 | 无 |
| 配置继承 | 三级：用户级/项目级共享/项目级个人 | 两级：用户级/项目级 | 单级：环境变量 |

**关键差异**：Claude Code 在控制层最为成熟，其 Hook 系统允许在工具调用前后注入任意逻辑，实现"确定性自动化"——这是企业级部署的核心需求。Codex 通过沙箱等级替代了钩子系统，更强调权限隔离而非工作流编排。Gemini CLI 的控制层最为薄弱，缺乏持久化会话和钩子机制。

### 2.2 代理层（Agency Layer）

代理层负责模型交互、工具调度和推理-行动循环。

| 能力 | Claude Code | Codex CLI | Gemini CLI |
|------|-------------|-----------|------------|
| 模型交互 | 推理-行动循环，支持流式输出 | 推理-行动循环，支持流式输出 | 推理-行动循环 |
| 工具调度 | 内置工具 + MCP 扩展 | 内置工具 + MCP 扩展 | 内置工具 + MCP 扩展 |
| 子代理调度 | 完善：Subagents 独立上下文/工具集/提示词 | 实验性：Worktree 子代理 | 不支持 |
| 多步骤推理 | 强：长上下文 + 上下文压缩 | 中：固定上下文窗口 | 强：超大上下文窗口 |
| 结果整合 | 主会话自动汇总子代理结果 | 子代理结果需手动合并 | 不适用 |

**关键差异**：Claude Code 的 Subagents 机制在代理层最具优势——每个子代理拥有独立的上下文窗口、工具白名单和系统提示词，主会话只接收最终汇总结果，实现了"分而治之"的并行处理。Codex 实验性的 Worktree 子代理类似但尚未成熟。Gemini CLI 的超大上下文窗口（约 500MB 输入）使其在单轮处理大型代码库时优势明显，但缺乏子代理机制限制了复杂编排场景。

### 2.3 运行时层（Runtime Layer）

运行时层提供插件系统、安全沙箱、存储和可观测性。

| 能力 | Claude Code | Codex CLI | Gemini CLI |
|------|-------------|-----------|------------|
| 插件系统 | MCP Server 协议 + 内置 Skills | MCP Server 协议 + AGENTS.md | MCP Server 协议 |
| 安全沙箱 | 权限白名单/黑名单 + 逐次确认 | 三级沙箱 + 读/写/执行精细控制 | 三级沙箱 |
| 事件系统 | Hook 事件（PreToolUse/PostToolUse） | 无 | 无 |
| 可观测性 | 调试模式、/cost 成本追踪、日志 | 调试模式、日志 | 基本日志 |
| 存储系统 | 会话持久化 + 记忆文件 | 会话持久化 | 无持久化 |

**关键差异**：Claude Code 的运行时层最为完备，特别是 Hook 事件系统允许在工具调用前后触发自定义逻辑，配合 MCP 协议形成了完整的插件生态。Codex 的沙箱机制最为精细，支持按文件读写和执行权限的独立控制。Gemini CLI 运行时最为精简，适合快速原型验证但缺乏生产级可观测性。

### 2.4 架构总览

```
Claude Code:     C (成熟策略引擎 + Hook 系统) → A (Subagents 编排) → R (MCP + 事件系统)
Codex CLI:       C (沙箱等级控制) → A (单 Agent 为主) → R (MCP + 精细权限)
Gemini CLI:      C (基础) → A (单 Agent + 超大上下文) → R (MCP 基础)
```

---

## 3. 多 Agent 能力对比

多 Agent 协作是 Coding Agent 领域最前沿的能力。三款工具的选择差异显著。

| 能力 | Claude Code | Codex CLI | Gemini CLI |
|------|-------------|-----------|------------|
| **Agent Teams** | 实验性，多 Agent 编排 | 无 | 无 |
| **Subagents** | 生产可用，独立上下文/工具/提示词 | 实验性（Worktrees） | 无 |
| **并行执行** | 支持（多个子代理同时运行） | 有限支持 | 不支持 |
| **职责隔离** | 子代理间完全隔离 | 有限隔离 | 不适用 |
| **结果汇总** | 主会话自动汇总 | 需手动合并 | 不适用 |
| **适用场景** | 大规模代码审计、并行审查、安全扫描 | 简单任务分解 | 不适合 |

详细的 Subagents 机制可参考 [Claude Code Subagents 与动态工作流](../e01-claude-code/06-subagents-and-workflows.md)，Agent Teams 编排可参考 [Agent Teams 多 Agent 协作编排](../e01-claude-code/04-agent-teams.md)。

---

## 4. 优劣势深度分析

### 4.1 Claude Code

**优势**：
- **生态最成熟**：教程、社区、第三方工具集成最丰富，CLAUDE.md 生态已成行业标准
- **安全体系最完善**：Hook 系统 + 权限白名单 + 三层设置继承，符合企业安全合规要求
- **Subagents 机制唯一成熟**：生产可用的子代理编排，擅长处理大规模代码库审计
- **文档质量最高**：官方文档 + 社区教程（如 claude-code-ultimate-guide）覆盖面广
- **会话管理最完善**：精确恢复指定会话，上下文压缩，成本追踪

**劣势**：
- **闭源**：无法自定义内部行为，依赖 Anthropic 的更新节奏
- **成本偏高**：仅 API 计费，无免费额度，大规模使用成本较高
- **TypeScript 实现**：相比 Rust 实现的 Codex，启动和运行速度较慢
- **审批模式繁琐**：默认逐次确认，在批量任务中操作成本较高

### 4.2 Codex CLI

**优势**：
- **完全开源**：Rust 实现，MIT 许可证，可自行编译和定制
- **速度最快**：Rust 编译的二进制文件，启动和响应速度领先
- **沙箱机制最精细**：三级权限 + 文件级读写控制，安全模型设计优秀
- **内置代码审查**：/review 命令直接内置，无需额外配置
- **管道友好**：支持 `stdin/stdout` 管道，适合 CI/CD 集成
- **图像输入**：支持截图作为输入，适合 UI 开发场景

**劣势**：
- **生态尚未成熟**：社区仓库和教程数量远少于 Claude Code
- **多 Agent 功能实验性**：Worktrees 子代理仍在开发中，不稳定
- **上下文窗口较小**：128K tokens 限制，处理大型代码库需要多次压缩
- **依赖 ChatGPT 订阅**：最佳使用体验需要 ChatGPT Pro 订阅

### 4.3 Gemini CLI

**优势**：
- **超大上下文窗口**：约 500MB 输入，可一次性加载整个中型代码库
- **免费额度慷慨**：Google 账号绑定，免费层对个人开发者友好
- **完全开源**：Apache 2.0 许可证，商业友好
- **Google 生态集成**：与 Google Cloud、BigQuery 等服务无缝集成

**劣势**：
- **功能最不完善**：无多 Agent、无代码审查、无会话恢复
- **生态最薄弱**：社区教程、第三方工具集成最少
- **正在转型**：截至 2026 年 5 月，正迁移至 Antigravity CLI，存在不确定性
- **模型能力与其他有差距**：Gemini 3 Flash 在代码生成质量上略逊于 Claude Sonnet 和 GPT-5.3-Codex
- **缺乏企业级功能**：无钩子系统、无精细权限控制、无可观测性工具

---

## 5. 场景推荐与决策框架

### 5.1 快速决策表

| 你的需求 | 推荐工具 | 理由 |
|---------|---------|------|
| 日常开发、个人项目 | Claude Code | 生态成熟，文档丰富，社区支持强 |
| 企业级团队协作 | Claude Code | 安全体系完善，Hook 系统，权限管理 |
| 预算敏感型个人开发 | Gemini CLI | 免费额度慷慨，超大上下文 |
| 开源项目、CI/CD 集成 | Codex CLI | 开源、速度快、管道友好 |
| 大型代码库的审计/重构 | Claude Code + Subagents | 唯一成熟的多 Agent 并行处理 |
| 安全优先的监管环境 | Codex CLI | 三级沙箱 + 精细权限控制 |
| UI 开发、视觉反馈场景 | Codex CLI | 支持图像输入 |
| 快速原型验证 | Gemini CLI | 零配置，免费，超大上下文 |
| 混合工具链（MCP 生态） | 三者均可 | 均支持 MCP 协议 |
| 长周期、大规模代码库 | Gemini CLI | 超大上下文窗口 |

### 5.2 场景矩阵

```
场景：个人开发者日常编码
推荐：Claude Code（入门）→ Codex（进阶）→ 视预算选择
理由：Claude Code 入门门槛最低，社区教程最多

场景：企业级团队（5-50 人）
推荐：Claude Code（主）+ Codex（辅，CI/CD）
理由：Claude Code 的 Hook 系统和权限管理最适合团队协作

场景：安全敏感的金融/医疗行业
推荐：Codex CLI
理由：三级沙箱 + 精细权限控制 + 开源可审计

场景：大型代码库（10 万+ 文件）的全面分析
推荐：Gemini CLI（单次扫描）+ Claude Code Subagents（深度审计）
理由：Gemini 的大上下文适合全貌扫描，Claude 子代理适合深度审查

场景：开源项目维护
推荐：Codex CLI
理由：开源、管道友好、可集成到 GitHub Actions
```

---

## 6. 迁移指南

### 6.1 Claude Code → Codex CLI

迁移要点：
- **CLAUDE.md → AGENTS.md**：项目指令文件需要重写，格式不兼容
- **Skills → AGENTS.md 脚本**：Skills 需要转换为 AGENTS.md 中的工具定义
- **Hook 逻辑 → 沙箱策略**：没有 Hook 系统，安全策略需要重新设计
- **Subagents → Worktrees**：实验性替代，功能不对等

### 6.2 Codex CLI → Claude Code

迁移要点：
- **AGENTS.md → CLAUDE.md**：内容移植，语法调整
- **沙箱策略 → Hook 系统**：从权限控制转为生命周期钩子
- **/review 内置审查 → 手动审查**：Claude Code 的审查需手动触发
- **管道脚本 → CLI 参数**：管道输入输出需要适配

### 6.3 Gemini CLI → 其他

迁移要点：
- **无项目配置文件**：需要新增 CLAUDE.md 或 AGENTS.md
- **无会话恢复**：需要适应会话管理机制
- **无多 Agent**：如有需要，需学习 Claude Code 的 Subagents

---

## 7. 生态成熟度对比

| 维度 | Claude Code | Codex CLI | Gemini CLI |
|------|-------------|-----------|------------|
| 官方文档 | 优秀 | 良好 | 良好 |
| 社区教程 | 丰富（200+ 仓库） | 增长中（50+ 仓库） | 稀少（<10 仓库） |
| 第三方工具 | 大量 MCP Server | 增长中 | 基础 |
| 培训资源 | 多平台覆盖 | 有限 | 有限 |
| 企业支持 | Anthropic 官方支持 | OpenAI 官方支持 | Google Cloud 支持 |
| 更新频率 | 双周 | 月度 | 季度 |
| 生态关联仓库 | [Claude Code 生态](../../categories/01-ecosystem-claude-code.md) | [Codex 生态](../../categories/02-ecosystem-codex.md) | 无独立生态分类 |

---

## 8. 企业级就绪度

| 维度 | Claude Code | Codex CLI | Gemini CLI |
|------|-------------|-----------|------------|
| SSO/团队管理 | 通过 API Key 管理 | ChatGPT 团队版 | Google Workspace 集成 |
| 审计日志 | Hook 系统可自定义 | 无 | 无 |
| 合规凭证管理 | 三级设置隔离 | 环境变量 | 环境变量 |
| 成本控制 | /cost 追踪 + 设置限制 | 订阅制，成本可控 | 免费层充裕 |
| 策略即代码 | Hook 系统支持 | 不支持 | 不支持 |
| 离线部署 | 不支持 | 开源可自建 | 开源可自建 |

**企业推荐**：Claude Code 在企业就绪度上领先，特别是其 Hook 系统实现了"策略即代码"（Policy as Code），这是企业合规审计的关键能力。Codex CLI 的开源特性使其在需要自建部署的场景中具有优势。Gemini CLI 目前缺乏企业级功能，建议关注其 Antigravity CLI 的迁移进展。

---

## 9. 成本对比

| 模式 | Claude Code | Codex CLI | Gemini CLI |
|------|-------------|-----------|------------|
| 免费层 | 无 | 无 | 慷慨（Google 账号绑定） |
| 个人订阅 | 按 API 用量 | ChatGPT Pro（$20/月） | 免费 |
| 团队订阅 | 按 API 用量 | ChatGPT Team（$25/人/月） | 免费+ |
| 企业计费 | 按 API 用量 + 协议价 | 企业协议 | Google Cloud 计费 |
| 典型月成本（个人重度） | $50-200 | $20（Pro 订阅） | $0-10 |
| 典型月成本（团队 10 人） | $500-2000 | $250（Team 订阅） | $0-50 |
| 成本可控性 | 需监控 API 用量 | 固定订阅，成本可预测 | 几乎免费 |

**成本建议**：
- 个人开发者：优先考虑 Gemini CLI（免费），其次 Codex CLI（固定订阅）
- 小型团队：Codex Team 订阅（成本可预测）
- 中大型企业：Claude Code（功能最全，协商 API 定价）
- 混合策略：日常开发用 Gemini/Codex，关键任务用 Claude Code

---

## 10. 总结与展望

```
推荐策略（按优先级）：

第一选择：Claude Code
- 适用：日常开发、企业团队、需要多 Agent 的场景
- 理由：生态最成熟、功能最完善、安全体系最完备
- 注意：成本较高，闭源

第二选择：Codex CLI
- 适用：开源项目、CI/CD 集成、安全敏感场景
- 理由：速度快、开源可审计、沙箱机制精细
- 注意：生态不够成熟，多 Agent 不完善

第三选择：Gemini CLI
- 适用：个人原型验证、预算敏感场景
- 理由：免费、超大上下文、Google 生态集成
- 注意：功能不完善，正在转型，有不确定性
```

Coding Agent 的竞争远未结束。2025-2026 年的趋势表明：
- **MCP 协议正在成为通用接口层**，三类工具均已支持，工具锁定正在降低
- **多 Agent 编排是下一个战场**，Claude Code 目前领先但 Codex 正在追赶
- **开源 vs 闭源之争**：Codex 和 Gemini 选择开源，Claude Code 保持闭源但通过插件化保持开放性
- **企业级安全是刚需**，Hook 系统和沙箱机制将成为差异化竞争的关键

---

## 相关资源

- **生态总览**：[Guide 生态索引](../../ecosystem-index.md) | [教程目录](../README.md)
- **Claude Code 生态**：[Claude Code 教程系列](../e01-claude-code/01-installation-and-basics.md) | [生态仓库](../../categories/01-ecosystem-claude-code.md)
- **Codex 生态**：[Codex 教程系列](../e02-codex/01-quickstart-and-commands.md) | [生态仓库](../../categories/02-ecosystem-codex.md)
- **Harness 工程**：[Harness 工程原理](../e03-dsh-harness/01-harness-engineering-principles.md) | [生态仓库](../../categories/03-ecosystem-dsh-harness.md)
- **MCP 协议**：[MCP 协议原理](../e05-mcp/01-protocol-concepts.md) | [生态仓库](../../categories/05-ecosystem-mcp.md)