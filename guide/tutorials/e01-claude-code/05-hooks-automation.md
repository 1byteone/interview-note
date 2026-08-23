# Hooks 自动化：在生命周期中注入确定性逻辑

> **生态**: E01 · Claude Code | **等级**: 进阶 | **前置要求**: 熟悉 Claude Code 项目配置与 settings.json

Claude Code 的 **Hooks（钩子）** 机制允许你在 Claude Code 生命周期的特定节点注入自定义逻辑——执行 Shell 脚本、调用 HTTP 端点、或由 LLM 自身处理。这是实现"自动化规范"的关键工具：代码保存后自动格式化、敏感命令执行前进行安全检查、会话结束时发送通知，所有重复性工作都可以交由 Hooks 接管。

本教程从事件生命周期出发，覆盖三种 Handler 类型、配置语法、输入输出格式、权限决策以及典型场景的完整实现。

---

## 1. Hooks 的事件模型

Hooks 的核心思路是"在特定时刻触发特定动作"。Claude Code 的生命周期事件分为三个层级：

### 1.1 事件层级概览

```
会话级 ── SessionStart ── SessionEnd
                           │
循环级 ── UserPromptSubmit ── Stop ── StopFailure
                           │
调用级 ── PreToolUse ── PostToolUse
```

| 层级 | 事件 | 触发时机 | 典型用途 |
|------|------|----------|----------|
| 会话级 | `SessionStart` | Claude Code 会话初始化时 | 加载环境、校验配置、发送通知 |
| 会话级 | `SessionEnd` | 会话正常结束时 | 记录会话摘要、清理临时文件、上报指标 |
| 循环级 | `UserPromptSubmit` | 用户提交消息后、模型开始处理前 | 预处理用户输入、注入上下文 |
| 循环级 | `Stop` | 模型完成一轮响应后 | 文件变更后自动格式化、触发 CI |
| 循环级 | `StopFailure` | 模型处理出错时 | 错误日志采集、异常告警 |
| 调用级 | `PreToolUse` | 每次工具调用前 | 权限检查、命令拦截、参数校验 |
| 调用级 | `PostToolUse` | 每次工具调用完成后 | 结果格式化、日志记录、文件同步 |

整个系统包含约 **30 个生命周期事件**，以上是最常用的 7 个。其余事件（如 `CompletionStart`、`CompletionEnd` 等）适用于更细粒度的监控场景。

### 1.2 执行顺序

以一个典型对话轮次为例，Hook 的执行顺序如下：

```
用户提交消息
  → UserPromptSubmit（可预处理输入）
  → 模型开始生成
  → PreToolUse（可拦截工具调用）
  → 工具执行
  → PostToolUse（可处理工具输出）
  → PreToolUse（下一个工具，循环）
  → ...
  → Stop（本轮处理结束）
  → StopFailure（如果出错）
```

## 2. 三种 Handler 类型

每个 Hook 可以绑定三种 Handler，分别适用于不同的自动化场景：

### 2.1 Command Handler（Shell 命令）

直接在本地执行 Shell 脚本或可执行文件，适合文件操作、格式化、通知等本地任务。

```json
{
  "hooks": {
    "PostToolUse": [
      {
        "matcher": "Edit",
        "hooks": [
          { "type": "command", "command": "node tools/format.js" }
        ]
      }
    ]
  }
}
```

Command Handler 通过 **stdin** 接收事件 JSON 输入，通过 **stdout** 返回决策结果。如果脚本退出码非零，Claude Code 会记录错误但不会中断会话。

### 2.2 HTTP Handler（远程端点）

向指定 URL 发起 POST 请求，适合与远程服务集成（如通知平台、CI/CD 系统、告警网关）。

```json
{
  "hooks": {
    "Stop": [
      {
        "matcher": "Bash",
        "hooks": [
          {
            "type": "http",
            "url": "https://hooks.example.com/claude-notify",
            "headers": {
              "Authorization": "Bearer ${WEBHOOK_TOKEN}"
            }
          }
        ]
      }
    ]
  }
}
```

HTTP Handler 的请求体为 JSON，响应体同样按照 JSON 格式解析决策结果。超时默认为 30 秒。

### 2.3 LLM Handler（模型处理）

由 Claude Code 自身（而非外部脚本）处理 Hook 事件，适合需要自然语言理解能力的场景。

```json
{
  "hooks": {
    "UserPromptSubmit": [
      {
        "matcher": "Bash",
        "hooks": [
          {
            "type": "llm",
            "prompt": "分析用户输入是否包含敏感信息（如 API Key、密码），如果包含则返回 { \"permissionDecision\": \"deny\" }"
          }
        ]
      }
    ]
  }
}
```

LLM Handler 将 `prompt` 字段与事件输入拼接后交给模型处理，返回结果同样遵循 JSON 决策格式。注意这会消耗额外的 token 和响应时间。

## 3. 配置语法详解

### 3.1 完整配置结构

Hooks 配置在 `.claude/settings.json`（或 `.claude/settings.local.json`）的 `hooks` 字段中：

```json
{
  "hooks": {
    "SessionStart": [
      {
        "hooks": [
          { "type": "command", "command": "echo 'Session started at $(date)' >> .claude/session.log" }
        ]
      }
    ],
    "UserPromptSubmit": [
      {
        "matcher": "Bash(git push*)",
        "hooks": [
          { "type": "command", "command": "node tools/check-branch-protection.js" }
        ]
      }
    ],
    "PreToolUse": [
      {
        "matcher": "Bash(rm *)",
        "hooks": [
          {
            "type": "llm",
            "prompt": "判断这个 rm 命令是否危险，若是则返回 deny"
          }
        ]
      }
    ],
    "PostToolUse": [
      {
        "matcher": "Edit",
        "hooks": [
          { "type": "command", "command": "npx prettier --write" }
        ]
      }
    ],
    "Stop": [
      {
        "hooks": [
          {
            "type": "http",
            "url": "http://localhost:9090/api/log",
            "headers": { "Content-Type": "application/json" }
          }
        ]
      }
    ],
    "StopFailure": [
      {
        "hooks": [
          { "type": "command", "command": "node tools/report-error.js" }
        ]
      }
    ],
    "SessionEnd": [
      {
        "hooks": [
          { "type": "command", "command": "node tools/summarize-session.js" }
        ]
      }
    ]
  }
}
```

### 3.2 Matcher 匹配规则

`matcher` 字段控制 Hook 在哪些工具调用上触发，支持通配符模式：

| 模式 | 匹配目标 | 示例 |
|------|----------|------|
| `Bash` | 任意 Bash 调用 | `Bash` |
| `Bash(git*)` | 以 git 开头的 Bash 命令 | `Bash(git push)` 匹配 |
| `Edit` | 任意 Edit 调用 | `Edit` |
| `Edit(**/*.java)` | 编辑 Java 文件的 Edit 调用 | `Edit(src/main/*.java)` 匹配 |
| `Read` | 任意 Read 调用 | `Read` |
| `Write` | 任意 Write 调用 | `Write` |
| `*` | 所有工具调用 | `*` |

如果不指定 `matcher`，Hook 将在该事件的所有触发场景下执行。

### 3.3 多 Handler 链

同一事件、同一匹配器可以绑定多个 Handler，按数组顺序依次执行：

```json
{
  "hooks": {
    "PostToolUse": [
      {
        "matcher": "Edit",
        "hooks": [
          { "type": "command", "command": "node tools/format.js" },
          { "type": "command", "command": "node tools/lint-check.js" },
          { "type": "http", "url": "https://api.example.com/audit" }
        ]
      }
    ]
  }
}
```

多个 Handler 按顺序执行，前一个的输出不会传递给后一个。每个 Handler 独立接收原始的 Hook 事件输入。

## 4. 输入输出格式

### 4.1 Hook 输入（stdin / POST body）

Command 和 HTTP Handler 接收的 JSON 输入结构如下：

```json
{
  "event": "PreToolUse",
  "toolUse": {
    "tool": "Bash",
    "input": {
      "command": "rm -rf /temp/cache"
    }
  },
  "session": {
    "id": "sess_abc123",
    "project": "my-app"
  }
}
```

各字段说明：

| 字段 | 说明 |
|------|------|
| `event` | 触发的事件名称 |
| `toolUse.tool` | 被调用的工具名称 |
| `toolUse.input` | 工具调用的参数（不同工具结构不同） |
| `session.id` | 当前会话 ID |
| `session.project` | 项目名称（如果有） |

### 4.2 Hook 输出（stdout / HTTP response）

Handler 通过 stdout（Command）或 HTTP Response Body（HTTP）返回 JSON 决策：

```json
{
  "permissionDecision": "allow",
  "hookSpecificOutput": "格式化完成，跳过 2 个文件（未变更）",
  "continue": true
}
```

| 字段 | 可选值 | 说明 |
|------|--------|------|
| `permissionDecision` | `allow` / `deny` / `ask` | 仅对 PreToolUse 生效；`ask` 会将决定权交还给用户 |
| `hookSpecificOutput` | 任意字符串 | 回写到 Claude Code 上下文的文本，模型可见 |
| `continue` | `true` / `false` | 是否继续执行后续 Hook 链 |

在非 `PreToolUse` 事件中，`permissionDecision` 字段会被忽略，但 `hookSpecificOutput` 始终有效。

## 5. PreToolUse：权限决策网关

`PreToolUse` 是 Hooks 中最强大的事件——它能在工具实际执行之前拦截并做出决策。这是实现"安全门禁"的关键。

### 5.1 三种决策模式

| 决策 | 效果 | 使用场景 |
|------|------|----------|
| `allow` | 允许执行，不提示用户 | 已知安全的操作（如 `git status`） |
| `deny` | 直接拒绝，不提示用户 | 绝对禁止的操作（如 `git push --force`） |
| `ask` | 弹出用户确认对话框 | 不确定的操作，交由人工判断 |

### 5.2 实战：保护 Git 分支

以下 Hook 在 `git push` 到 `main` 分支时强制要求用户确认：

```json
{
  "hooks": {
    "PreToolUse": [
      {
        "matcher": "Bash(git push*)",
        "hooks": [
          {
            "type": "command",
            "command": "node tools/check-branch-protection.js"
          }
        ]
      }
    ]
  }
}
```

`check-branch-protection.js` 实现：

```javascript
#!/usr/bin/env node
const input = JSON.parse(require('fs').readFileSync(0, 'utf-8'));
const command = input.toolUse.input.command;

if (command.includes('main') || command.includes('master')) {
  console.log(JSON.stringify({
    permissionDecision: 'ask',
    hookSpecificOutput: '检测到推送到 main/master 分支，请确认操作'
  }));
} else {
  console.log(JSON.stringify({
    permissionDecision: 'allow',
    hookSpecificOutput: '非保护分支，放行'
  }));
}
```

### 5.3 实战：拦截危险命令

```json
{
  "hooks": {
    "PreToolUse": [
      {
        "matcher": "Bash(rm *)",
        "hooks": [
          {
            "type": "llm",
            "prompt": "分析以下命令是否包含危险操作（如递归删除、强制删除、删除系统目录）。如果危险，返回 { \"permissionDecision\": \"deny\" }；否则返回 { \"permissionDecision\": \"allow\" }。"
          }
        ]
      }
    ]
  }
}
```

借助 LLM Handler 的自然语言理解能力，可以识别出 `rm -rf /`、`rm -rf ~` 等变体，弥补正则匹配的不足。

## 6. PostToolUse：格式化与通知

`PostToolUse` 在工具执行完成后触发，最适合做"后处理"——格式化代码、记录日志、发送通知。

### 6.1 自动格式化（Format-on-Save）

在 `Edit` 工具调用后自动运行 Prettier 或 ESLint 修复：

```json
{
  "hooks": {
    "PostToolUse": [
      {
        "matcher": "Edit",
        "hooks": [
          { "type": "command", "command": "npx prettier --write --ignore-unknown ." }
        ]
      }
    ]
  }
}
```

> **注意**：PostToolUse 中的 `command` 执行完毕后，Claude Code 会读取文件变更并把结果纳入上下文。如果格式化导致文件变化，模型后续会看到更新后的内容。

### 6.2 变更通知

将文件变更发送到团队协作工具（如飞书、钉钉、Slack）：

```json
{
  "hooks": {
    "PostToolUse": [
      {
        "matcher": "Write",
        "hooks": [
          {
            "type": "http",
            "url": "https://open.feishu.cn/open-apis/bot/v2/hook/xxxxx",
            "headers": { "Content-Type": "application/json" }
          }
        ]
      }
    ]
  }
}
```

### 6.3 审计日志

记录每次工具调用的完整信息到本地文件：

```json
{
  "hooks": {
    "PostToolUse": [
      {
        "matcher": "*",
        "hooks": [
          { "type": "command", "command": "node tools/audit-log.js" }
        ]
      }
    ]
  }
}
```

`audit-log.js` 实现：

```javascript
#!/usr/bin/env node
const fs = require('fs');
const input = JSON.parse(fs.readFileSync(0, 'utf-8'));
const log = `[${new Date().toISOString()}] ${input.event} | ${input.toolUse.tool} | ${JSON.stringify(input.toolUse.input)}\n`;
fs.appendFileSync('.claude/audit.log', log);
```

## 7. Hooks 使用场景汇总

### 7.1 安全加固

| Hook | 事件 | 用途 |
|------|------|------|
| 分支保护 | `PreToolUse` | 拦截向受保护分支的 `git push` |
| 密钥泄露检测 | `PreToolUse` | 检查文件写入是否包含 API Key 或密码 |
| 危险命令拦截 | `PreToolUse` | 拦截 `rm -rf`、`drop table` 等破坏性操作 |
| 权限审计 | `PostToolUse` | 记录所有高风险操作到审计日志 |

### 7.2 代码质量

| Hook | 事件 | 用途 |
|------|------|------|
| 自动格式化 | `PostToolUse` | Edit 后自动运行 Prettier / ESLint |
| 静态检查 | `PostToolUse` | 文件写入后自动运行 linter |
| 测试触发 | `Stop` | 代码变更后自动运行关联测试 |

### 7.3 工作流自动化

| Hook | 事件 | 用途 |
|------|------|------|
| 会话开始通知 | `SessionStart` | 发送"Claude 开始处理"到群聊 |
| 会话摘要 | `SessionEnd` | 生成 Markdown 格式的会话摘要并保存 |
| 错误告警 | `StopFailure` | 模型报错时发送告警到运维群 |
| 提交前检查 | `PreToolUse` | 检查 `git commit` 消息是否符合规范 |

### 7.4 团队协作

| Hook | 事件 | 用途 |
|------|------|------|
| 代码变更通知 | `PostToolUse` | 文件变更后通知相关协作者 |
| 进度同步 | `Stop` | 每轮对话结束后更新任务看板 |
| 合规检查 | `PreToolUse` | 检查是否有未授权的外网请求 |

## 8. 调试与排障

### 8.1 启用调试日志

```bash
claude --debug
```

查看日志中的 `hook` 相关行，确认 Hook 是否被正确加载和触发：

```
[Hook] Loading hooks from settings.json
[Hook] Registered 3 hooks for PostToolUse
[Hook] Executing command hook: node tools/format.js
[Hook] Hook completed with output: { permissionDecision: "allow" }
```

### 8.2 常见问题

| 症状 | 原因 | 解决方案 |
|------|------|----------|
| Hook 未触发 | Matcher 不匹配或事件名写错 | 检查事件名称大小写（如 `SessionStart` 非 `sessionStart`） |
| Hook 返回错误 | 命令路径不对或脚本语法错误 | 在终端单独运行 `command` 字符串确认可执行 |
| 权限决策不生效 | 输出 JSON 格式错误 | 用 `JSON.stringify` 序列化输出，不要 `console.log` 拼接 |
| 性能影响 | Hook 执行耗时过长 | 避免在 Hook 中执行重量级操作；LLM Handler 尤其消耗 token |
| 循环触发 | 命令又触发了同一个 Hook 事件 | 加状态标记或文件锁避免递归 |

### 8.3 测试 Hook 的独立验证方法

在配置前，先独立验证脚本逻辑：

```bash
# 1. 模拟 Hook 输入
echo '{"event":"PreToolUse","toolUse":{"tool":"Bash","input":{"command":"git push origin main"}},"session":{}}' | node tools/check-branch-protection.js

# 2. 确认输出格式
# 应输出: {"permissionDecision":"ask","hookSpecificOutput":"..."}
```

## 9. 最佳实践小结

1. **最小 Hook 集**：不要为所有事件都注册 Hook，只添加有明确需求的。每个 Hook 都是额外的 IO 开销。
2. **Command Handler 优先**：本地 Shell 脚本比 HTTP 调用更快、更可靠、不依赖网络。
3. **LLM Handler 慎用**：它消耗 token 且增加延迟，仅在需要自然语言理解时使用（如模糊命令识别）。
4. **Matcher 精确匹配**：`Bash(git push*)` 优于 `Bash`，减少不必要的 Hook 调用。
5. **输出有信息量**：`hookSpecificOutput` 的内容会进入上下文，写上对模型有用的信息，而不仅仅是"OK"。
6. **错误不中断**：默认 Hook 失败不会中断会话，但会记录日志。如果希望 Hook 失败中断流程，在脚本中设置非零退出码。
7. **版本控制**：Hooks 配置（尤其是团队共享的）应纳入 Git 仓库，通过 `.claude/settings.json` 分发。
8. **安全第一**：不要在 Command Handler 中拼接用户输入直接执行，防止命令注入；HTTP Handler 的 URL 和 Token 使用环境变量引用。

---

## 进阶指引

- 上一篇：[Agent Teams 多 Agent 协作编排](./04-agent-teams.md)
- 下一篇：[Subagents 子代理与动态工作流](./06-subagents-and-workflows.md)
- 生态仓库：[claude-code-ultimate-guide](../../repositories/FlorianBruniaux_claude-code-ultimate-guide.md)（Hooks 章节 + 安全最佳实践）