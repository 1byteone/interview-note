# 技能系统与三层记忆系统详解

> **生态**: E04 · Hermes/OpenClaw | **等级**: 进阶 | **前置要求**: 完成 Hermes 安装（见本系列第 1 篇）

Hermes Agent 的差异化竞争力来自两个核心子系统：**技能系统（Skills）** 与 **三层记忆系统（Memory）**。技能系统让 Agent 能力可组合、可分发、可演进；记忆系统让 Agent 跨会话持续学习与个性化。两者共同构成了"智能体操作系统"的核心。

本教程详解技能系统的概念、创建、管理与市场安装，47 个内置工具的速查，三层记忆系统的架构与配置，以及实际场景中的协同使用。

---

## 1. 技能系统概览

### 1.1 什么是 Skill

Skill（技能）是 Hermes 中可复用的能力单元，封装了：

- **领域知识**：特定领域的常识、规则、术语
- **工作流**：多步骤的任务执行流程
- **工具调用模式**：何时调用哪个工具、如何解读结果
- **输出规范**：响应的格式、质量标准

技能在文件系统上是一个目录，类似 Claude Code 的 Skills 但更完整：

```
~/.hermes/skills/
├── sql-migrator/
│   ├── skill.yaml         # 元数据与配置
│   ├── prompt.md          # 系统提示词模板
│   ├── examples/          # 示例样本
│   └── scripts/           # 辅助脚本
├── api-reviewer/
│   └── skill.yaml
└── weekly-report/
    ├── skill.yaml
    └── templates/
        └── report.md
```

### 1.2 技能 vs 工具

| 维度 | 工具（Tool） | 技能（Skill） |
|------|------------|---------------|
| 粒度 | 单一原子操作 | 多步骤工作流 |
| 抽象 | 函数级 | 任务级 |
| 复用 | 跨技能复用 | 跨会话复用 |
| 例子 | `read-file` | "生成周报" |

技能内部调用工具，工具是技能的"零件"。

---

## 2. 创建与管理技能

### 2.1 skill.yaml 结构

```yaml
# ~/.hermes/skills/sql-migrator/skill.yaml
name: sql-migrator
version: 1.0.0
description: |
  生成符合团队规范的 Flyway 数据库迁移脚本，
  包括命名约定、字段注解和回滚 SQL。
  当用户请求创建数据库迁移、修改表结构时触发。

author: your-name
license: MIT

# 触发条件（自然语言）
trigger:
  - 当用户请求创建数据库迁移时
  - 当用户说"加字段"、"改表"时
  - 当用户提到 Flyway / Liquibase 时

# 依赖的工具
tools:
  - read-file
  - write-file
  - shell           # 用于执行 flyway 命令

# 配置参数
config:
  naming: snake_case
  include_rollback: true
  dialect: postgresql

# 限制权限（最小权限原则）
permissions:
  files:
    write:
      - "db/migration/**"
  shell:
    blacklist: [rm, sudo]
```

### 2.2 prompt.md 模板

```markdown
# SQL Migrator Prompt

你是一个数据库迁移专家。根据用户的需求，生成符合以下规范的迁移脚本：

## 命名规范
- 文件名：V{version}__{description}.sql
- 表名：snake_case，复数
- 字段名：snake_case

## 字段约定
- 每个表必须包含 created_at、updated_at
- 主键统一为 id BIGSERIAL

## 输出
1. 迁移脚本 V{n}__{desc}.sql
2. 回滚脚本 U{n}__{desc}.sql
3. 简要说明变更内容
```

### 2.3 创建命令

Hermes 提供脚手架命令快速创建技能：

```bash
# 交互式创建
hermes skills create

# 命令行参数创建
hermes skills create \
  --name sql-migrator \
  --description "生成 Flyway 迁移脚本" \
  --tools read-file,write-file,shell

# 从模板创建
hermes skills create --template api-reviewer
```

### 2.4 管理命令

```bash
# 列出已安装技能
hermes skills list

# 查看技能详情
hermes skills info sql-migrator

# 启用/禁用技能
hermes skills enable sql-migrator
hermes skills disable sql-migrator

# 删除技能
hermes skills remove sql-migrator

# 验证技能配置
hermes skills validate sql-migrator
```

---

## 3. 技能市场安装

### 3.1 浏览市场

```bash
# 列出市场热门技能
hermes skills search --popular

# 按关键字搜索
hermes skills search "sql"

# 查看技能详情
hermes skills info @community/api-reviewer
```

### 3.2 安装技能

```bash
# 从官方市场安装
hermes skills install @official/code-reviewer

# 从社区市场安装
hermes skills install @community/weekly-report

# 从 Git 仓库安装
hermes skills install https://github.com/user/my-skill.git

# 从本地路径安装
hermes skills install ./my-local-skill
```

### 3.3 安装审计

Hermes 的零信任架构要求所有技能安装前进行审计（详见本系列第 3 篇）：

```
正在审计技能 @community/weekly-report...
[1/4] 检查技能签名: ✓ 已签名
[2/4] 检查权限范围: ⚠ 要求 shell 访问，是否允许？
  > 允许（推荐用于常规工作流）
  > 拒绝
  > 详细查看
[3/4] 检查依赖工具: ✓ read-file, write-file 已安装
[4/4] 检查供应链完整性: ✓ SHA256 校验通过

技能 @community/weekly-report 安装成功。
```

---

## 4. 47 个内置工具速查

Hermes 内置 47 个工具，按类别速查：

### 4.1 文件操作（8 个）

| 工具 | 作用 |
|------|------|
| `read-file` | 读取文件内容，支持范围读取 |
| `write-file` | 写入文件，支持创建/覆盖/追加 |
| `edit-file` | 精确字符串替换 |
| `delete-file` | 删除文件 |
| `glob` | 文件模式匹配 |
| `grep` | 内容搜索（基于 ripgrep） |
| `move-file` | 移动/重命名 |
| `file-info` | 获取文件元信息 |

### 4.2 系统操作（6 个）

| 工具 | 作用 |
|------|------|
| `shell` | 执行 Shell 命令（沙箱） |
| `process` | 进程管理（启动/停止/查询） |
| `env` | 环境变量读写 |
| `cron` | 定时任务管理 |
| `sys-info` | 系统信息（CPU/内存/磁盘） |
| `clipboard` | 剪贴板读写 |

### 4.3 网络与 Web（7 个）

| 工具 | 作用 |
|------|------|
| `http` | HTTP 请求（GET/POST/...） |
| `web-search` | 网页搜索 |
| `web-fetch` | 抓取网页内容并转为 Markdown |
| `browser` | 浏览器自动化（Playwright） |
| `download` | 文件下载 |
| `websocket` | WebSocket 通信 |
| `dns` | DNS 查询 |

### 4.4 开发工具（10 个）

| 工具 | 作用 |
|------|------|
| `git` | Git 操作（status/diff/commit/...） |
| `gh` | GitHub CLI 集成 |
| `npm` | npm 包管理 |
| `docker` | Docker 容器管理 |
| `kubectl` | Kubernetes 操作 |
| `cargo` | Rust 项目管理 |
| `maven` | Java 项目管理 |
| `gradle` | Gradle 构建 |
| `test-runner` | 测试运行器 |
| `linter` | 代码检查 |

### 4.5 数据工具（8 个）

| 工具 | 作用 |
|------|------|
| `sql` | SQL 查询执行 |
| `redis` | Redis 操作 |
| `vector-db` | 向量数据库操作 |
| `csv` | CSV 读写与转换 |
| `json` | JSON 处理 |
| `yaml` | YAML 处理 |
| `xml` | XML 处理 |
| `markdown` | Markdown 解析与生成 |

### 4.6 MCP 工具（8 个）

| 工具 | 作用 |
|------|------|
| `mcp-list` | 列出 MCP 服务器 |
| `mcp-call` | 调用 MCP 工具 |
| `mcp-resource` | 读取 MCP 资源 |
| `mcp-prompt` | 调用 MCP 提示模板 |
| `mcp-subscribe` | 订阅 MCP 资源更新 |
| `mcp-server-add` | 添加 MCP 服务器 |
| `mcp-server-remove` | 移除 MCP 服务器 |
| `mcp-health` | MCP 健康检查 |

---

## 5. 三层记忆系统

Hermes 的记忆系统是其核心竞争力之一，分为三层：

```
┌────────────────────────────────────┐
│  Short-term（短期记忆）             │  当前会话上下文
├────────────────────────────────────┤
│  Long-term（长期记忆）              │  跨会话持久化
├────────────────────────────────────┤
│  External（外部记忆）               │  向量库 / 知识库
└────────────────────────────────────┘
```

### 5.1 Short-term（短期记忆）

存储当前会话的对话历史与中间状态：

```yaml
# ~/.hermes/config.yaml
memory:
  short_term:
    backend: in-memory
    max_messages: 50        # 保留最近 50 条
    max_tokens: 8000        # 总 token 上限
    compression:
      enabled: true         # 超过上限时自动压缩
      strategy: summarize   # 摘要压缩
```

特点：
- 进程内存储，速度最快
- 会话结束即销毁（除非显式保存到长期）
- 自动压缩避免上下文溢出

### 5.2 Long-term（长期记忆）

跨会话持久化的记忆，支持多种后端：

```yaml
memory:
  long_term:
    backend: sqlite          # sqlite | file | postgres
    path: ~/.hermes/memory/long-term.db
    categories:              # 记忆分类
      - user_preferences     # 用户偏好
      - project_context      # 项目上下文
      - task_history         # 任务历史
    retention:
      max_items: 10000
      ttl_days: 90           # 90 天过期
```

操作命令：

```bash
# 添加记忆
hermes memory add \
  --category user_preferences \
  --content "用户偏好使用 TypeScript"

# 查询记忆
hermes memory search "用户偏好"

# 列出记忆
hermes memory list --category user_preferences

# 删除记忆
hermes memory remove <id>
```

在 Agent 中自动使用：

```typescript
// Hermes SDK 自动召回相关记忆
const hermes = new Hermes();
const reply = await hermes.ask('帮我写个函数', {
  // 自动从长期记忆召回相关条目注入上下文
  recallMemory: true,
  recallTopK: 5,
});
```

### 5.3 External（外部记忆）

接入向量数据库与知识库，支持大规模语义检索：

```yaml
memory:
  external:
    vector_db:
      type: chroma            # chroma | qdrant | pinecone
      url: http://localhost:8000
      collection: hermes-knowledge
      embedding_model: text-embedding-3-small
      top_k: 5

    knowledge_bases:
      - name: team-wiki
        type: confluence
        url: https://team.atlassian.net
        sync_interval: 1h

      - name: code-repo
        type: github
        repo: myorg/myrepo
        path: docs/
```

外部记忆支持：

- **向量检索**：基于语义相似度召回
- **混合检索**：向量 + 关键词
- **多源融合**：同时查询多个知识库
- **增量同步**：定期同步外部知识源

---

## 6. 记忆配置实战

### 6.1 个人助手场景

```yaml
memory:
  short_term:
    max_tokens: 4000
    compression: { enabled: true }
  long_term:
    backend: sqlite
    categories: [user_preferences, schedule, contacts]
  external:
    vector_db:
      type: chroma
      collection: personal-assistant
```

### 6.2 团队开发场景

```yaml
memory:
  short_term:
    max_tokens: 16000   # 大窗口，保留更多代码上下文
  long_term:
    backend: postgres    # 团队共享
    categories: [project_context, coding_standards, team_members]
  external:
    vector_db:
      type: qdrant
      collection: team-knowledge
    knowledge_bases:
      - name: team-wiki
        type: confluence
```

### 6.3 客服 Agent 场景

```yaml
memory:
  short_term:
    max_tokens: 8000
  long_term:
    backend: postgres
    categories: [customer_profile, order_history, ticket_history]
    ttl_days: 365        # 客户记忆保留 1 年
  external:
    vector_db:
      type: pinecone
      collection: product-knowledge
    knowledge_bases:
      - name: product-docs
        type: git
        repo: company/product-docs
```

---

## 7. 技能与记忆协同实战

### 7.1 场景：周报生成

假设安装了 `weekly-report` 技能，它使用长期记忆记住用户的报告偏好：

```
用户：根据本周数据生成周报
Hermes：[触发 weekly-report 技能]
  1. 从长期记忆召回用户偏好（语言、格式、关注指标）
  2. 调用 sql 工具查询本周数据
  3. 调用 vector-db 检索历史周报模板
  4. 生成周报 Markdown
  5. 询问用户是否满意，若满意则将本次偏好写入长期记忆
```

### 7.2 自定义技能中使用记忆

```yaml
# ~/.hermes/skills/weekly-report/skill.yaml
name: weekly-report
tools: [sql, read-file, write-file, vector-db]
memory:
  recall:
    categories: [report_preferences, historical_reports]
    top_k: 3
  save:
    on_success: true
    category: historical_reports
```

技能执行时，Hermes 会自动：
1. 从 `report_preferences` 与 `historical_reports` 召回 top 3 相关条目
2. 注入到系统提示词的"用户偏好"部分
3. 任务成功完成后，将本次会话摘要保存到 `historical_reports`

---

## 8. 最佳实践小结

1. **技能拆分要小**：一个技能只解决一个明确任务，避免"全能技能"
2. **trigger 要具体**：用具体动作描述触发条件，减少误触发
3. **最小权限**：`permissions` 只开放技能必需的文件/命令范围
4. **短期记忆勤压缩**：开启 `compression.enabled`，长会话才不会上下文溢出
5. **长期记忆分类**：按主题分类，召回更精准
6. **外部记忆增量同步**：定期更新知识库，避免召回陈旧信息
7. **技能与记忆协同**：在 skill.yaml 中声明 `memory.recall`，让技能"记得"用户偏好

---

## 进阶指引

- 上一篇：[Hermes Agent 安装部署与架构解析](./01-installation-and-architecture.md)
- 下一篇：[Agent 零信任安全实践](./03-security-practices.md) — 安全策略与审计