# Codex CLI 快速上手与命令指南

> 本文档属于 **E02 Codex 生态** 系列教程的第一篇，面向零基础读者，帮助你在 10 分钟内完成 Codex CLI 的安装、配置并运行第一个会话。

---

## 1. 什么是 Codex CLI

Codex CLI 是 OpenAI 推出的开源终端 AI 编程助手，核心代码用 **Rust** 编写，完全开源。它直接运行在终端中，不需要离开命令行就能完成代码编写、调试、重构、代码审查等任务。

Codex CLI 的核心特性：

- **完全开源**: 代码托管在 GitHub，社区可以贡献和审查
- **Rust 实现**: 高性能、低资源占用、跨平台
- **多模型支持**: 默认使用 GPT-5.3-Codex 模型，也支持 API Key 模式切换模型
- **三种审批模式**: 精细控制 AI 对文件系统的访问权限
- **管道友好**: 支持标准输入管道，可嵌入 Git 工作流
- **图像输入**: 支持截图作为输入，用于 UI 还原等场景
- **MCP 集成**: 支持 Model Context Protocol，扩展工具生态
- **多 Agent 实验性功能**: 支持并行工作树

---

## 2. 系统要求

在安装 Codex CLI 之前，请确保你的环境满足以下要求：

| 要求 | 说明 |
|------|------|
| **Node.js** | 版本 22 或更高 |
| **npm** | 随 Node.js 安装，建议 10+ |
| **操作系统** | macOS 14+、Linux (x86_64/aarch64)、Windows (WSL2) |
| **终端** | 支持 ANSI 颜色和 Unicode |
| **网络** | 能访问 api.openai.com |

验证环境：

```bash
node --version   # 需要 >= 22.x
npm --version    # 需要 >= 10.x
```

---

## 3. 安装

### 3.1 npm 全局安装

```bash
npm install -g @openai/codex
```

安装完成后验证：

```bash
codex --version
```

如果看到版本号输出，说明安装成功。

### 3.2 从源码构建（可选）

如果你希望使用最新代码或参与贡献：

```bash
git clone https://github.com/openai/codex.git
cd codex
cargo build --release
```

需要安装 Rust 工具链（rustup）。

---

## 4. 认证配置

Codex CLI 支持两种认证方式：

### 4.1 ChatGPT 账号认证（推荐）

使用 OpenAI 的 ChatGPT Plus、Pro 或 Business 订阅账号：

```bash
codex auth
```

这会打开浏览器跳转到 OpenAI 登录页面，完成授权后 token 会自动保存在本地。

### 4.2 API Key 认证

如果你有 OpenAI API Key，也可以通过环境变量配置：

```bash
export OPENAI_API_KEY="sk-xxxxx"
```

或者在配置文件 `~/.codex/config.toml` 中设置：

```toml
[auth]
api_key = "sk-xxxxx"
```

> **注意**: ChatGPT 认证模式下默认使用 `gpt-5.3-codex` 模型；API Key 模式可以自定义模型。

---

## 5. 第一个会话

### 5.1 启动交互会话

在项目目录中直接运行：

```bash
codex
```

这会进入交互模式，你会看到类似这样的提示符：

```
Codex> 
```

来尝试第一个问题：

```
Codex> 在当前目录下创建一个名为 hello.py 的 Python 文件，内容为打印 "Hello, Codex!"
```

Codex 会自动创建文件，并询问你是否要执行建议的命令。

### 5.2 单次查询模式

如果你只需要一次回答，不需要交互：

```bash
codex -p "解释一下什么是闭包，用 JavaScript 举例"
```

### 5.3 查看帮助

```bash
codex --help
```

---

## 6. 审批模式详解

Codex CLI 提供三种审批模式，控制 AI 对文件系统的操作权限：

### 6.1 Auto 模式（默认）

```
codex --mode auto
```

- AI 可以在当前工作目录内自由读写文件和运行命令
- 访问工作目录之外的文件时需要用户确认
- 适合日常开发，平衡效率与安全

### 6.2 Read-only 模式

```
codex --mode read-only
```

- AI 只能浏览文件内容，无法修改或创建文件
- 所有写入操作都需要用户逐条审批
- 适合代码审查、分析场景

### 6.3 Full Access 模式

```
codex --mode full-access
```

- AI 拥有完全的文件系统访问权限
- 可以读写任意路径，运行任意命令
- 仅在明确信任的场景下使用，如自动化脚本

---

## 7. 关键命令速查

| 命令 | 说明 |
|------|------|
| `codex` | 启动交互式会话 |
| `codex -p "prompt"` | 单次查询模式 |
| `codex -i screenshot.png -p "实现这个UI"` | 带图像输入的单次查询 |
| `codex resume --last` | 恢复上一次会话 |
| `codex resume --id <session-id>` | 恢复指定会话 |
| `codex exec` | 非交互执行模式（CI/CD） |
| `codex auth` | 重新认证 |
| `codex --help` | 查看帮助 |
| `codex --version` | 查看版本 |
| `/review` | 在会话内进行代码审查 |

---

## 8. 会话管理

### 8.1 恢复会话

Codex CLI 会自动保存会话历史，你可以随时恢复：

```bash
# 恢复最近一次会话
codex resume --last

# 按 ID 恢复特定会话
codex resume --id session_20260823_123456
```

### 8.2 会话列表

```bash
codex list
```

查看所有可恢复的会话及其 ID、时间戳和摘要信息。

### 8.3 会话清理

```bash
codex cleanup --days 30
```

清理 30 天前的旧会话数据。

---

## 9. 模型选择与配置

### 9.1 默认模型

ChatGPT 认证模式默认使用 `gpt-5.3-codex`，这是 OpenAI 针对编程场景优化的专用模型。

### 9.2 自定义模型

在 `~/.codex/config.toml` 中配置：

```toml
[model]
# API Key 模式下使用
name = "gpt-5.3-codex"

# 可选：指定不同的模型
# name = "gpt-4.1"
# name = "o3"
```

### 9.3 全局配置文件

`~/.codex/config.toml` 完整示例：

```toml
[auth]
# 不设置则使用 ChatGPT 认证
# api_key = "sk-xxxxx"

[model]
name = "gpt-5.3-codex"

[approval]
# auto | read-only | full-access
mode = "auto"

[display]
# 是否显示思考过程
show_thinking = true

[mcp]
# MCP 服务器配置
# [mcp.servers]
# [mcp.servers.filesystem]
# command = "npx"
# args = ["-y", "@modelcontextprotocol/server-filesystem"]
```

---

## 10. 管道与图像输入

### 10.1 管道输入

Codex CLI 支持从标准输入读取数据，非常适合嵌入 Git 工作流：

```bash
# 让 AI 解释 git diff 的变更
git diff | codex -p "总结这些代码变更，列出每个文件的主要改动"

# 让 AI 审查代码
cat main.py | codex -p "审查这段代码，检查潜在 bug 和安全问题"

# 结合日志分析
kubectl logs pod-name | codex -p "分析这些日志，找出异常和错误模式"
```

### 10.2 图像输入

支持截图或图片作为输入，适用于 UI 还原、图表分析等场景：

```bash
# 从截图生成前端代码
codex -i ./design-screenshot.png -p "根据这个设计截图，生成对应的 HTML/CSS 代码"

# 分析图表
codex -i ./architecture-diagram.png -p "解释这个架构图，列出所有组件和它们之间的关系"
```

---

## 11. VS Code 扩展

Codex CLI 提供 VS Code 扩展，支持在编辑器中直接使用：

1. 在 VS Code 扩展市场搜索 "Codex CLI"
2. 安装后，使用 `Ctrl+Shift+P` 打开命令面板
3. 选择 "Codex: Start Session" 启动会话

扩展功能包括：
- 内联代码建议
- 文件上下文感知
- 与终端会话同步

---

## 12. 最佳实践小结

1. **首次使用**: 先用 `codex` 进入交互模式，体验完整的对话流程
2. **日常开发**: 使用 `--mode auto` 模式，平衡效率和安全
3. **代码审查**: 切换到 `--mode read-only`，或使用 `/review` 命令
4. **Git 集成**: 善用管道输入，让 AI 辅助审查 diff
5. **项目配置**: 在项目根目录创建 AGENTS.md 提供上下文
6. **会话管理**: 善用 `resume --last` 快速回到上次的工作上下文

---

## 参考链接

- [Codex CLI GitHub](https://github.com/openai/codex)
- [OpenAI 官方文档](https://platform.openai.com/docs)
- [AGENTS.md 配置指南](./02-agents-md-and-sandbox.md)
- [高级工作流：MCP 集成与多 Agent 编排](./03-advanced-workflows.md)