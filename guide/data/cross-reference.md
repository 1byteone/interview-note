# 🔗 生态交叉引用矩阵

> 版本: v1.0 | 日期: 2026-08-22

---

## 1. 生态间依赖关系图

```
E06 通识与基础 ─────────────────────────────────────────────┐
  │  (Prompt Engineering, Agent 通识, 系统设计)              │
  │                                                         │
  ├──→ E01 Claude Code    (Prompt 实践、Agent 开发、MCP)     │
  ├──→ E02 Codex          (Prompt 实践、Agent 开发、MCP)     │
  ├──→ E03 Harness        (Harness 理论、系统设计)           │
  ├──→ E04 Hermes/OpenClaw (Agent 框架实践)                 │
  └──→ E05 MCP            (协议理解)                         │
                                                             │
E05 MCP 协议 ───────────────────────────────────────────────┐
  │  (跨生态通用协议)                                        │
  │                                                         │
  ├──→ E01 Claude Code    (MCP 服务器配置)                   │
  ├──→ E02 Codex          (MCP 集成)                        │
  ├──→ E03 Harness/DSH    (MCP 协议集成)                    │
  └──→ E04 Hermes/OpenClaw (MCP 自动化)                     │
```

---

## 2. 仓库级交叉引用

### E01 → 其他生态

| E01 仓库 | → E02 | → E03 | → E04 | → E05 | → E06 |
|----------|-------|-------|-------|-------|-------|
| claude-code-ultimate-guide | 竞品架构对比 | Harness 参考实现 | 安全实践参考 | MCP 服务器配置 | Prompt 实践 |
| zebbern/claude-code-guide | — | 功能对比 | — | MCP 集成 | — |
| claudecode-guide | 竞品对比 | — | — | — | 入门级 |

### E02 → 其他生态

| E02 仓库 | → E01 | → E03 | → E04 | → E05 | → E06 |
|----------|-------|-------|-------|-------|-------|
| CodexGuide | 竞品对比 | Sandbox 对比 | — | MCP 集成 | Prompt 实践 |

### E03 → 其他生态

| E03 仓库 | → E01 | → E02 | → E04 | → E05 | → E06 |
|----------|-------|-------|-------|-------|-------|
| harness_engineering_guide | 参考实现 | 参考实现 | 参考实现 | MCP 协议集成 | 系统设计 |
| deepeseek-harness-guide | 竞品对比 | — | 插件架构对比 | MCP 集成 | Agent 开发 |
| awesome-harness-engineering | — | — | — | — | — |

### E04 → 其他生态

| E04 仓库 | → E01 | → E02 | → E03 | → E05 | → E06 |
|----------|-------|-------|-------|-------|-------|
| hermes-agent-guide | 安全实践参考 | — | Harness 参考实现 | MCP 集成 | 框架对比 |
| openclaw-security-practice-guide | 安全实践参考 | — | 安全体系设计 | — | Agent 安全 |
| awesome-hermes-agent | — | — | — | — | 生态索引 |

### E05 → 其他生态

| E05 仓库 | → E01 | → E02 | → E03 | → E04 | → E06 |
|----------|-------|-------|-------|-------|-------|
| MCP-Chinese-Getting-Started-Guide | Claude Desktop 集成 | Codex 集成 | DSH 集成 | Hermes 集成 | 协议理解 |

### E06 → 其他生态

| E06 仓库 | → E01 | → E02 | → E03 | → E04 | → E05 |
|----------|-------|-------|-------|-------|-------|
| Prompt-Engineering-Guide | ✅ | ✅ | ✅ | ✅ | ✅ |
| AgentGuide | ✅ | ✅ | ✅ | ✅ | ✅ |
| ai-system-design-guide | ✅ | ✅ | ✅ | ✅ | ✅ |
| python-guide | ✅ | ✅ | — | — | — |
| ai-agent-interview-guide | ✅ | ✅ | — | — | — |
| ai-agents-from-zero | ✅ | ✅ | — | ✅ | ✅ |
| awesome-agent-skills | ✅ | ✅ | ✅ | ✅ | ✅ |

---

## 3. 生态间依赖强度

```
            E01  E02  E03  E04  E05  E06
   E01      ●    ○    ●    ○    ●    ●
   E02      ○    ●    ○    ○    ●    ●
   E03      ●    ○    ●    ●    ●    ●
   E04      ○    ○    ●    ●    ●    ●
   E05      ●    ●    ●    ●    ●    ●
   E06      ●    ●    ●    ●    ●    ●

   图例: ● = 强关联  ○ = 弱关联
```

- **E05 MCP** 和 **E06 通识** 是所有生态的公共基础设施（全生态通用）
- **E03 Harness** 理论体系覆盖 E01/E02/E04（三大参考系统）
- **E01 Claude Code** 和 **E02 Codex** 是竞品关系，相互参考对比