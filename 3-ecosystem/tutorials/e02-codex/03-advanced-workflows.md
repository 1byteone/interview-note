# 高级工作流：MCP 集成与多 Agent 编排

> 本文档属于 **E02 Codex 生态** 系列教程的第三篇，面向已有 Codex CLI 基础的用户，深入讲解 MCP 服务器集成、多 Agent 并行工作树、代码审查自动化以及 CI/CD 集成等高级功能。

---

## 1. MCP 服务器集成

Codex CLI 支持通过 Model Context Protocol (MCP) 集成外部工具，扩展 AI 的能力边界。

### 1.1 MCP 配置

MCP 服务器在 `~/.codex/config.toml` 的 `[mcp.servers]` 部分配置：

```toml
[mcp]

  [mcp.servers.filesystem]
  command = "npx"
  args = ["-y", "@modelcontextprotocol/server-filesystem"]

  [mcp.servers.github]
  command = "npx"
  args = ["-y", "@modelcontextprotocol/server-github"]

  [mcp.servers.sqlite]
  command = "uvx"
  args = ["mcp-server-sqlite", "--db-path", "./test.db"]

  [mcp.servers.custom]
  # HTTP 传输的 MCP 服务器
  transport = "http"
  url = "http://localhost:8080/mcp"
```

### 1.2 STDIO 传输

STDIO 是 MCP 的默认传输方式，Codex CLI 启动子进程并通过标准输入/输出通信：

```toml
[mcp.servers.my-tool]
command = "python"
args = ["path/to/mcp_server.py"]
```

STDIO 传输的优势：
- 零网络开销，本地通信延迟低
- 不需要端口管理，避免端口冲突
- 子进程生命周期由 Codex CLI 管理

### 1.3 HTTP 传输

HTTP 传输用于连接远程 MCP 服务器：

```toml
[mcp.servers.remote-service]
transport = "http"
url = "https://api.example.com/mcp"
headers = { Authorization = "Bearer your-token" }
```

HTTP 传输的优势：
- 支持远程服务调用
- 可以复用已有的 REST API 基础设施
- 适合微服务架构中的工具注册

### 1.4 会话中使用 MCP 工具

配置完成后，在 Codex CLI 会话中可以直接使用 MCP 工具：

```
Codex> 帮我查询 GitHub 上 openai/codex 仓库的最新 Release
```

AI 会自动调用配置的 GitHub MCP 服务器来获取数据。

---

## 2. 多 Agent 并行工作树

Codex CLI 的实验性多 Agent 功能允许并行执行多个任务，通过 Git 工作树实现隔离。

### 2.1 启用多 Agent

在 `config.toml` 中启用：

```toml
[experimental]
multi_agent = true
```

### 2.2 并行工作流

多 Agent 工作流的典型场景：

```bash
# 启动主会话
codex

# 在另一个终端中启动并行 Agent
codex --worktree feature-a -p "实现用户注册功能"
codex --worktree feature-b -p "实现用户登录功能"
```

每个 Agent 在独立的 Git 工作树中工作，互不干扰。

### 2.3 工作树管理

```bash
# 列出所有工作树
codex worktree list

# 合并工作树的变更到主分支
codex worktree merge feature-a

# 删除工作树
codex worktree remove feature-a
```

### 2.4 实际应用场景

**场景一：并行功能开发**

```bash
# 终端 1: 后端 API 开发
codex --worktree backend-api -p "创建订单管理接口，包括 CRUD 和状态流转"

# 终端 2: 前端页面开发
codex --worktree frontend-pages -p "创建订单列表页面，对接 /api/orders 接口"

# 终端 3: 数据库迁移
codex --worktree db-migration -p "创建订单表迁移脚本，包含索引和约束"
```

**场景二：代码审查与修复并行**

```bash
# 终端 1: 审查并修复安全漏洞
codex --worktree security-fix -p "审查代码中的 SQL 注入风险并修复"

# 终端 2: 审查并优化性能
codex --worktree perf-opt -p "审查慢查询并优化数据库访问代码"
```

---

## 3. 代码审查

Codex CLI 内置 `/review` 命令，可以对代码进行自动审查。

### 3.1 基本使用

在交互会话中：

```
Codex> /review src/main/java/com/example/service/OrderService.java
```

AI 会输出审查结果，包括：
- 潜在 Bug
- 安全漏洞
- 性能问题
- 代码风格问题
- 改进建议

### 3.2 审查整个目录

```
Codex> /review src/main/java/
```

### 3.3 审查 Git 变更

```
Codex> /review --diff
```

这会审查当前工作目录中所有未提交的修改。

### 3.4 审查结果输出格式

审查结果的结构化输出示例：

```
## 审查报告: OrderService.java

### 严重问题 (2)
1. [BUG] 第 45 行: 空指针风险 - `order.getItems()` 可能为 null
2. [SECURITY] 第 78 行: SQL 注入风险 - 使用了字符串拼接

### 建议改进 (3)
1. [PERF] 第 120 行: 循环内调用数据库，建议批量查询
2. [STYLE] 第 200 行: 方法过长 (150行)，建议拆分为多个小方法
3. [TEST] 第 300 行: 缺少边界条件检查
```

---

## 4. 无头 CI/CD 集成

### 4.1 GitHub Actions 集成

完整的 CI/CD 流水线示例：

```yaml
name: Codex Automated Workflow
on:
  pull_request:
    types: [opened, synchronize]
  push:
    branches: [main]

jobs:
  code-review:
    name: AI Code Review
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
        with:
          fetch-depth: 0
      - uses: actions/setup-node@v4
        with:
          node-version: '22'
      - name: Install Codex
        run: npm install -g @openai/codex
      - name: Review PR changes
        run: |
          git diff origin/main...HEAD | \
          codex exec -p "审查代码变更，列出严重问题" \
            --mode read-only
        env:
          OPENAI_API_KEY: ${{ secrets.OPENAI_API_KEY }}

  auto-test:
    name: AI Test Generation
    runs-on: ubuntu-latest
    needs: code-review
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with:
          node-version: '22'
      - name: Install Codex
        run: npm install -g @openai/codex
      - name: Generate tests for changed files
        run: |
          CHANGED_FILES=$(git diff origin/main...HEAD --name-only -- '*.ts' '*.tsx')
          for file in $CHANGED_FILES; do
            codex exec -p "为 $file 生成 Jest 单元测试" \
              --mode auto
          done
        env:
          OPENAI_API_KEY: ${{ secrets.OPENAI_API_KEY }}
```

### 4.2 GitLab CI 集成

```yaml
codex-review:
  image: node:22
  stage: review
  script:
    - npm install -g @openai/codex
    - git diff origin/main...HEAD | codex exec -p "审查代码变更" --mode read-only
  variables:
    OPENAI_API_KEY: $OPENAI_API_KEY_SECRET
  only:
    - merge_requests
```

### 4.3 自定义 CI 脚本

```bash
#!/bin/bash
# codex-ci.sh - 在 CI 中执行多步骤 Codex 工作流

set -e

echo "=== Step 1: 代码审查 ==="
git diff origin/main...HEAD | \
  codex exec -p "审查代码变更，输出 JSON 格式的结果" \
    --mode read-only > review-report.json

echo "=== Step 2: 自动修复简单问题 ==="
codex exec -p "修复代码风格问题，保持功能不变" \
  --mode auto

echo "=== Step 3: 生成测试 ==="
codex exec -p "为新增的函数生成单元测试" \
  --mode auto

echo "=== Step 4: 验证 ==="
npm test
npm run build
```

---

## 5. 端到端工作流示例

### 5.1 Feature 开发全流程

```bash
# 1. 创建功能分支
git checkout -b feature/user-dashboard

# 2. 启动 Codex 开发会话
codex

# 在 Codex 会话中:
# - 分析需求并设计数据模型
# - 创建数据库迁移脚本
# - 实现后端 API
# - 实现前端组件
# - 编写单元测试
# - 执行 /review 审查代码

# 3. 提交流代码
git add -A
git commit -m "feat: implement user dashboard"

# 4. 让 AI 生成提交信息
git diff --cached | codex -p "根据 diff 生成规范的 Git 提交信息"
```

### 5.2 Bug 修复流程

```bash
# 1. 创建修复分支
git checkout -b fix/order-calculation-bug

# 2. 分析 Bug
codex -p "分析 src/order/calculator.ts 中的计算逻辑，检查金额计算是否有精度问题"

# 3. 修复 Bug
codex -p "修复金额计算中的浮点数精度问题，使用 Decimal 类型替代 Number"

# 4. 验证修复
codex -p "为修复后的计算逻辑编写边界测试用例"
```

### 5.3 重构流程

```bash
# 1. 分析代码复杂度
codex -p "分析 src/legacy/ 目录，列出需要重构的模块和优先级"

# 2. 并行重构
codex --worktree refactor-module-a -p "将模块 A 从 JavaScript 迁移到 TypeScript"
codex --worktree refactor-module-b -p "将模块 B 从 JavaScript 迁移到 TypeScript"

# 3. 合并变更
codex worktree merge refactor-module-a
codex worktree merge refactor-module-b

# 4. 全面测试
codex exec -p "运行所有测试并修复失败的测试用例"
```

---

## 6. 配置最佳实践

### 6.1 完整 config.toml 示例

```toml
# ~/.codex/config.toml

[auth]
# 使用 ChatGPT 认证，不设置 API Key

[model]
name = "gpt-5.3-codex"

[approval]
mode = "auto"

[display]
show_thinking = true
theme = "dark"

[sandbox]
allowed_paths = [
    "/home/user/shared-data"
]
blocked_paths = [
    "/etc"
]

[experimental]
multi_agent = true

[mcp]

  [mcp.servers.filesystem]
  command = "npx"
  args = ["-y", "@modelcontextprotocol/server-filesystem"]

  [mcp.servers.github]
  command = "npx"
  args = ["-y", "@modelcontextprotocol/server-github"]

  [mcp.servers.postgres]
  command = "npx"
  args = ["-y", "@modelcontextprotocol/server-postgres", "postgresql://localhost/mydb"]

  [mcp.servers.web-search]
  command = "python"
  args = ["mcp-servers/search_server.py"]
```

### 6.2 安全建议

1. **最小权限原则**: MCP 服务器只授予必要的文件访问权限
2. **审核配置变更**: config.toml 的变更应该经过 Review
3. **隔离敏感操作**: 使用独立的 MCP 服务器处理敏感操作，设置严格的权限控制
4. **监控日志**: 开启 MCP 服务器的日志记录，便于审计

---

## 参考链接

- [Codex CLI 快速上手与命令指南](./01-quickstart-and-commands.md)
- [AGENTS.md 配置与沙盒安全模型](./02-agents-md-and-sandbox.md)
- [Codex CLI GitHub](https://github.com/openai/codex)
- [MCP 协议原理与核心概念](../e05-mcp/01-protocol-concepts.md)