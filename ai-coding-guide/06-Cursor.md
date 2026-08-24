> **[← 目录](README.md)** | 章节 06/12

# 第六部分 Cursor：日常开发 IDE + Agent

Cursor 最大优势是 **IDE + Agent + Codebase Search + Terminal + Rules + MCP** 的原生集成。截至 2026 年，Cursor 已从"代码补全工具"演进为具备 Agent Mode、Cloud Agents、MCP 集成的完整 AI IDE。([Cursor Docs][3])

## 6.1 四种模式：Agent / Ask / Manual / Custom

| 模式 | 用途 | 示例 |
|------|------|------|
| **Ask** | 理解、分析、学习、规划（不改代码） | "为什么这个 Redis 分布式锁会失效？" |
| **Agent** | 实现、重构、调试、测试（自主执行） | "修复订单超卖问题。自行分析代码、修改并测试。" |
| **Manual** | 精准修改（只改指定位置） | "只修改这个方法，不要修改其他文件。" |
| **Custom** | 自定义工作流 | 特定团队流程 |

**关键区分**：
- 找到 Cursor 纠正同一问题两次 → 把纠正写入 Rules
- Agent Mode 用于"准备行动"（代码生成/编辑）
- Ask Mode 用于"理解"（不修改代码）

## 6.2 Cursor Rules：.mdc 规则体系

Cursor Rules 使用 `.mdc` 文件（Markdown with Config），支持 YAML frontmatter：

```markdown
---
description: Spring Boot 编码规范
globs: **/*.java
alwaysApply: false
---

# Spring Boot 编码规范

## 分层约束
- Controller 只做参数校验和响应封装
- Service 负责业务编排
- 不允许在 Controller 中写业务逻辑

## 命名规范
- Entity: PascalCase，如 UserOrder
- DTO: xxxDTO，如 UserCreateDTO
- Service: xxxService，如 OrderService
- Controller: xxxController，如 OrderController

## 异常处理
- 业务异常: BusinessException
- 统一异常处理: @RestControllerAdvice
- 不允许 catch Exception 后不处理
```

### 规则激活模式

| 模式 | 说明 |
|------|------|
| **Always** | 始终应用 |
| **Auto Attached** | 匹配 glob 模式时自动附加 |
| **Agent Requested** | Agent 按需请求 |
| **Manual** | 手动激活 |

### AGENTS.md 与 Cursor Rules 统一

2026 年的最佳实践：**AGENTS.md 作为单一事实来源**，各工具的规则文件只做薄包装：

```
AGENTS.md（核心规范）
  ├── .cursor/rules/java.mdc（导入 AGENTS.md + Cursor 特定配置）
  ├── CLAUDE.md（导入 AGENTS.md + Claude Code 特定配置）
  └── .codex/rules.md（导入 AGENTS.md + Codex 特定配置）
```

## 6.3 Cloud Agents：云端后台任务

Cursor 的 Cloud Agents 可以在云端运行长时间任务：

- 不占用本地资源
- 支持后台执行
- 完成后通知

## 6.4 YOLO 模式与权限控制

Cursor 提供多级权限控制：

| 级别 | 行为 |
|------|------|
| **Normal** | 每次操作都确认 |
| **Auto-review** | 自动执行，但保留审查能力 |
| **YOLO** | 全自动（谨慎使用） |

推荐策略：
- 读操作（grep、cat、git diff）：Auto
- 写操作（编辑代码）：Auto-review
- 高危操作（git push、删除）：Confirm

## 6.5 MCP 集成：连接外部工具

Cursor Agent 通过 MCP 连接外部服务：

```json
// .cursor/mcp.json
{
  "mcpServers": {
    "github": {
      "command": "npx",
      "args": ["-y", "@modelcontextprotocol/server-github"],
      "env": { "GITHUB_TOKEN": "${GITHUB_TOKEN}" }
    },
    "postgres": {
      "command": "npx",
      "args": ["-y", "@modelcontextprotocol/server-postgres"],
      "env": { "DATABASE_URL": "${DATABASE_URL}" }
    },
    "redis": {
      "command": "npx",
      "args": ["-y", "@modelcontextprotocol/server-redis"],
      "env": { "REDIS_URL": "${REDIS_URL}" }
    }
  }
}
```

---

---

[← 上一章: 05-Hermes](05-Hermes.md) | [目录](README.md) | [下一章: 07-Context-Engineering(07-Context-Engineering.md)](07-Context-Engineering.md)
