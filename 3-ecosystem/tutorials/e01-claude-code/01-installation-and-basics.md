# Claude Code 安装与基础使用

> **生态**: E01 · Claude Code | **等级**: 入门 | **前置要求**: 终端基础操作

Claude Code 是 Anthropic 推出的终端 AI 编程代理（Coding Agent），通过与代码库的直接交互完成代码编写、测试、重构和调试等任务。它是目前 Coding Agent 领域的标杆工具，也是 Harness 工程理念（Agent 运行时 + 上下文管理 + 工具调用）的参考实现之一。

本教程覆盖从系统要求、安装、认证到首次会话，再到项目配置、环境变量与会话管理的完整链路，帮助你建立可投入日常开发的生产级环境。

---

## 1. 系统要求

Claude Code 是一个 Node.js CLI 应用，官方支持的操作系统如下：

| 平台 | 最低版本 | 备注 |
|------|---------|------|
| macOS | 13.0+ | Apple Silicon 与 Intel 均支持 |
| Windows | 10 1809+ | 原生 + WSL 双通道 |
| Ubuntu / Debian | 20.04+ | 其他 Linux 发行版可尝试 |
| 内存 | 4 GB+ | 推荐 8 GB，大型仓库建议 16 GB |

> **Windows 用户优先推荐 WSL**：多数社区排障文档（如 zebbern/claude-code-guide）中的脚本基于 Bash，`#` 注释、`$()` 命令替换等语法仅有少数在 PowerShell / cmd 可用。在 WSL 中保持一致的环境，能显著减少跨平台兼容性问题。

## 2. 安装

Claude Code 以 npm 包分发，官方推荐三种安装途径：

### 2.1 macOS（Homebrew）

```bash
brew install claude-code
```

### 2.2 任意平台（npm 全局安装）

```bash
npm install -g @anthropic/claude-code
```

### 2.3 Windows（winget）

```powershell
winget install ClaudeCode
```

另外可随时用以下命令确认/升级版本：

```bash
claude --version
npm update -g @anthropic/claude-code   # npm 方式升级
brew upgrade claude-code               # Homebrew 方式升级
```

## 3. 身份认证

Claude Code 通过 **Anthropic API Key** 认证。获取与配置流程：

1. 登录 [console.anthropic.com](https://console.anthropic.com)（需求 Anthropic 账号）；
2. 在 **API Keys** 页面创建新 Key，立即复制保存（仅显示一次）；
3. 配置到环境变量：

```bash
# macOS / Linux / WSL
export ANTHROPIC_API_KEY="sk-ant-..."
echo 'export ANTHROPIC_API_KEY="sk-ant-..."' >> ~/.zshrc   # 持久化

# Windows PowerShell
$env:ANTHROPIC_API_KEY = "sk-ant-..."
[Environment]::SetEnvironmentVariable("ANTHROPIC_API_KEY", "sk-ant-...", "User")
```

也可以不设置环境变量，首次运行 `claude` 时 CLI 会引导你交互式登录。

> **安全提示**：API Key 属于机密凭证，绝不可提交到 Git 仓库。建议使用本仓库环境（如 `.claude/settings.local.json`）或系统密钥管理器保存。检测到 Key 泄漏时应立即在控制台撤销并重新生成。

## 4. 首次会话

在项目根目录启动：

```bash
cd /path/to/your-project
claude
```

启动后你会进入 REPL 风格的交互界面，可以直接用自然语言下达指令：

```
> 分析一下项目结构，并告诉我入口文件在哪
> 为 UserService 写单元测试
> 解释一下这段代码做了什么
```

Claude 会自主执行读取文件、运行命令、编辑代码等操作，并随时请求你确认。

### 4.1 常用操作

| 操作 | 方式 |
|------|------|
| 提交输入 | `Enter`（单行）或 `Shift+Enter`（多行） |
| 中断执行 | 按 `Esc` |
| 退出 | `/exit` 或按 `Ctrl+C` 两次 |
| 打开命令面板 | `/` |

## 5. CLI 命令速查

`claude` 支持直接从终端带参数启动，适合脚本化与自动化场景：

```bash
claude "为 README 生成一张架构图"        # 直接执行一次任务
claude -p "修复 tests 目录下所有失败用例"  # -p / --print：非交互模式
claude --resume                           # 恢复最近的会话
claude --resume <session-id>              # 恢复指定会话
claude --continue                         # 在最近上下文上继续
claude --output-format json               # JSON 输出（供 CI 解析）
claude --model claude-sonnet-4-5          # 指定模型
claude --debug                            # 开启调试日志
```

### 5.1 会话内斜杠命令

| 命令 | 作用 |
|------|------|
| `/help` | 查看帮助与可用命令 |
| `/compact` | 压缩上下文，节省 token 并继续长会话 |
| `/cost` | 查看本次会话 token 消耗与费用 |
| `/review` | 对当前改动发起代码审查 |
| `/debug` | 进入调试模式，查看内部日志 |
| `/clear` | 清空当前会话上下文 |
| `/model` | 切换或查看模型 |
| `/config` | 交互式编辑设置 |

## 6. 项目级配置：CLAUDE.md

**`CLAUDE.md`** 位于项目根目录，是 Claude Code 的项目级指令文件，每次会话启动时自动加载到上下文中。它相当于"项目的操作手册"，告诉 Claude 项目的背景、约定与约束。

### 6.1 推荐结构

```markdown
# 项目名

## 技术栈
Spring Boot 3.x + MyBatis + Vue 3

## 构建与运行
- 构建：`./mvnw clean package`
- 运行：`./mvnw spring-boot:run`

## 代码约定
- 分层架构：Controller → Service → Repository
- 所有 API 出入参使用 DTO，禁止直接暴露 Entity
- 数据库迁移使用 Flyway，禁止手改表结构

## 测试
- 单元测试：JUnit 5 + Mockito
- 启动前必须运行：`./mvnw test`
```

### 6.2 编写原则

- **具体可执行**：给出命令而非模糊描述；
- **约束优先**：明确"禁止做什么"（如禁止提交 target/ 目录）；
- **保持精简**：CLAUDE.md 全部内容会进上下文，过长会浪费 token 并稀释重点；
- **可子目录扩展**：仅针对子目录生效的内容放在子目录的 `CLAUDE.md` 中，实现局部约束。

### 6.3 记忆文件（Memory）

长期约定（适用所有项目）可写入用户级记忆文件；单是待办性质、会变化的内容放到 `CLAUDE.md` 或 `@file` 引用中，避免指令过期。

## 7. 全局设置：settings.json

Claude Code 的设置文件支持三级继承：

| 文件 | 作用域 | 路径 |
|------|--------|------|
| `settings.json` | 用户级 | `~/.claude/settings.json` |
| `.claude/settings.json` | 项目级（入库共享） | `<repo>/.claude/settings.json` |
| `.claude/settings.local.json` | 项目级（个人，不入库） | `<repo>/.claude/settings.local.json` |

示例：

```json
{
  "model": "claude-sonnet-4-5",
  "permissions": {
    "allow": ["Bash(npm run:*)", "Read(**)"] ,
    "deny": ["Bash(git push)"]
  },
  "hooks": {
    "PostToolUse": [
      {
        "matcher": "Bash",
        "hooks": [{ "type": "command", "command": "node tools/hook.js" }]
      }
    ]
  },
  "env": {
    "MY_CUSTOM_VAR": "value"
  }
}
```

常用配置项：`model`（默认模型）、`approvalMode`（审批模式：`acceptEdits` / `bypassPermissions` / 默认逐次询问）、`theme`（主题）、`permissions`（工具权限白/黑名单）、`hooks`（生命周期钩子）、`mcpServers`（MCP 服务器，见本系列第 3 篇）。

## 8. 环境变量

### 8.1 核心变量

| 变量 | 说明 |
|------|------|
| `ANTHROPIC_API_KEY` | API 认证凭证（必需） |
| `ANTHROPIC_BASE_URL` | 自定义 API 端点（代理/网关场景） |
| `CLAUDE_CODE_MAX_OUTPUT_TOKENS` | 限制单次输出 token |
| `CLAUDE_CODE_DISABLE_NONESSENTIAL_TRAFFIC` | 关闭非必要遥测 |
| `CLAUDE_CODE_ENABLE_TELEMETRY` | 开启/关闭遥测 |
| `CLAUDE_CODE_EXPERIMENTAL_AGENT_TEAMS` | 启用 Agent Teams（见本系列第 4 篇） |

### 8.2 工程化建议

将环境变量沉淀到 `.claude/settings.json` 的 `env` 字段中，随项目分发，避免团队各自 `export` 不一致：

```json
{
  "env": {
    "CLAUDE_CODE_MAX_OUTPUT_TOKENS": "8192"
  }
}
```

## 9. 会话管理

- **恢复会话**：`claude --resume` 列出历史会话，`claude --resume <id>` 精确恢复，上下文与工具状态无缝延续；
- **多会话隔离**：每个会话拥有独立上下文窗口，互不干扰，适合按功能/任务拆分并行；
- **上下文压缩**：长会话用 `/compact` 或 `/clear` 释放上下文空间，避免模型"遗忘"前面的指令；
- **成本追踪**：`/cost` 随时查看 token 消耗，避免失控账单；配合 `--model` 在轻量任务上使用更便宜的模型。

## 10. 最佳实践小结

1. **先写 CLAUDE.md 再开工**：好的项目指令能减少 30% 以上的来回纠错；
2. **Key 不进仓库**：一律通过环境变量或 `settings.local.json` 注入；
3. **任务拆分会话**：一个会话聚焦一个功能，善用 `--resume` 续接；
4. **权限最小化**：默认逐次确认，不要盲目 `bypassPermissions`；
5. **版本跟随**：Claude Code 迭代快，定期 `claude --version` 检查并升级，重要变更关注官方 changelog。

---

## 进阶指引

- 下一篇：[Claude Code Skills 开发实战](./02-skills-development.md) — 构建可复用的技能库
- 生态仓库：[claude-code-ultimate-guide](../../repositories/FlorianBruniaux_claude-code-ultimate-guide.md)（深度教学）｜ [claude-code-guide](../../repositories/zebbern_claude-code-guide.md)（功能排障手册）