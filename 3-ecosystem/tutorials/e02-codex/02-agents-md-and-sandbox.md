# AGENTS.md 配置与沙盒安全模型

> 本文档属于 **E02 Codex 生态** 系列教程的第二篇，深入讲解 AGENTS.md 项目指令文件的结构化编写方法，以及 Codex CLI 的沙盒安全模型和审批控制机制。

---

## 1. AGENTS.md 概述

AGENTS.md 是 Codex CLI 的项目级上下文指令文件，类似于 Claude Code 的 CLAUDE.md。它告诉 AI 助手关于项目的关键信息，包括项目目标、编码规范、架构决策和部署规则等。

### 1.1 为什么需要 AGENTS.md

- **上下文持久化**: 每次会话自动加载，无需重复说明项目背景
- **一致性保障**: 确保 AI 生成的代码符合团队规范
- **新人友好**: 新成员加入项目时，AI 已经了解项目约定
- **规则强约束**: 可以定义明确的规则清单，让 AI 严格遵守

---

## 2. 文件结构

### 2.1 标准格式

AGENTS.md 使用 Markdown 格式，推荐按以下结构组织：

```markdown
# 项目名称

## 项目上下文
简短描述项目的目的、技术栈和核心业务逻辑。

## 编码规范
列出代码风格、命名约定、文件组织方式等。

## 架构决策
记录重要的架构决策和权衡。

## 部署规则
说明构建、测试、部署流程和注意事项。

## 检查清单
列出 AI 在完成任务前需要检查的事项。
```

### 2.2 完整示例

```markdown
# 电商平台后端服务

## 项目上下文
这是一个基于 Spring Boot 3.x 的电商平台后端服务，采用微服务架构。
核心模块包括：用户服务、商品服务、订单服务、支付服务。

## 编码规范
- 使用 Java 21，遵循 Oracle 编码规范
- 包名使用 `com.example.ecommerce.{module}`
- Controller 层使用 RESTful 命名风格
- Service 层必须编写接口和实现分离
- 所有对外接口使用 DTO 模式，禁止直接暴露 Entity
- 使用 Lombok 减少样板代码
- 统一异常处理使用 GlobalExceptionHandler
- 日志使用 SLF4J + Logback

## 架构决策
- 服务间通信使用 Feign + Nacos 服务发现
- 分布式事务使用 Seata AT 模式
- 消息队列使用 RocketMQ，异步解耦
- 缓存使用 Redis Cluster，缓存穿透/击穿/雪崩均有防护
- 数据库使用 MySQL 8.0 + MyBatis-Plus

## 部署规则
- 构建工具: Maven 3.9+
- Docker 镜像使用 `eclipse-temurin:21-jre` 基础镜像
- 必须通过 `mvn clean verify` 全部测试
- 生成 API 文档: `mvn clean package -DskipTests`
- 部署前检查: 所有 Profile 配置完整，无硬编码地址

## 检查清单
- [ ] 所有新增代码通过单元测试
- [ ] 没有 System.out.println，全部使用 Logger
- [ ] SQL 语句经过索引分析
- [ ] 新增 API 已添加参数校验
- [ ] 敏感数据已脱敏或加密
```

---

## 3. 文件合并顺序

Codex CLI 支持多个 AGENTS.md 文件按优先级合并，形成最终的指令上下文：

### 3.1 合并优先级

```
1. ~/.codex/AGENTS.md          ← 个人全局配置（最高优先级）
2. <repo-root>/AGENTS.md       ← 项目仓库配置
3. <cwd>/AGENTS.md             ← 当前工作目录配置（最低优先级）
```

### 3.2 合并规则

- 高优先级文件的内容会追加到低优先级文件之后
- 相同主题的指令会合并，**不会**覆盖
- 如果存在冲突，高优先级文件的指令排在前面，但 AI 会综合所有指令

### 3.3 三层配置示例

**个人全局配置** `~/.codex/AGENTS.md`:

```markdown
# 个人编码偏好

## 编码规范
- 使用 2 空格缩进
- 行尾不要有分号（JavaScript/TypeScript）
- 优先使用 const 而非 let
```

**项目仓库配置** `<repo-root>/AGENTS.md`:

```markdown
# 前端项目

## 项目上下文
React 18 + TypeScript + Vite 构建的 SPA 应用。

## 编码规范
- TypeScript 严格模式
- 使用 ESLint + Prettier
- 组件使用函数式组件 + Hooks
```

**当前目录配置** `<cwd>/AGENTS.md`:

```markdown
# 子模块：用户管理页面

## 项目上下文
负责用户列表、角色管理和权限配置。
```

最终合并后的指令等效于：

```
个人编码偏好 + 前端项目 + 用户管理页面
```

---

## 4. 编写有效指令的原则

### 4.1 具体而非模糊

| 错误写法 | 正确写法 |
|---------|---------|
| "代码质量要好" | "所有 public 方法必须有 Javadoc 注释" |
| "使用合适的命名" | "Controller 类名以 Controller 结尾，Service 接口以 Service 结尾" |
| "处理异常" | "使用 GlobalExceptionHandler 统一处理，禁止在 Controller 中 try-catch" |

### 4.2 可检查的规则

每条规则应该能被自动或手动验证。例如：

```markdown
## 检查清单
- [ ] 所有新增文件行数不超过 500 行
- [ ] 循环复杂度不超过 10
- [ ] 没有 TODO 注释遗留
- [ ] 新增依赖已在 README 中记录
```

### 4.3 分层管理

将指令分为不同层次，避免混淆：

```markdown
## 角色定义
你是一个资深 Java 后端开发工程师，熟悉 Spring Cloud 微服务架构。

## 风格规则
- 使用 SLF4J 日志门面，日志级别：info 记录业务关键信息
- 所有 API 响应使用统一格式：`{code, message, data}`
- 枚举类型统一放在 `enums` 包下

## 架构约束
- 禁止 Service 层直接调用其他 Service 的 Controller
- 禁止循环依赖（Spring Boot 启动时会检测）
- 数据库操作必须通过 Repository 层，禁止直接使用 JdbcTemplate
```

### 4.4 引用外部文件

AGENTS.md 可以引用项目中的其他文档：

```markdown
## 参考文档
- 数据库设计文档: [docs/database-schema.md](docs/database-schema.md)
- API 规范: [docs/api-conventions.md](docs/api-conventions.md)
- 编码规范: [docs/coding-standards.md](docs/coding-standards.md)
```

---

## 5. 沙盒安全模型

Codex CLI 的沙盒模型控制 AI 对文件系统的访问权限，分为三个层次：

### 5.1 目录访问控制

沙盒基于"工作目录"的概念：

```toml
# config.toml
[sandbox]
# 允许 AI 访问的额外目录白名单
allowed_paths = [
    "/home/user/config",
    "/var/log/myapp"
]
# 禁止访问的目录黑名单
blocked_paths = [
    "/etc/shadow",
    "/home/user/.ssh"
]
```

### 5.2 审批模式与沙盒的交互

| 审批模式 | 工作目录内 | 白名单目录 | 其他目录 |
|---------|-----------|-----------|---------|
| Auto | 读写执行 | 需要确认 | 需要确认 |
| Read-only | 只读 | 只读 | 需要确认 |
| Full Access | 完全访问 | 完全访问 | 完全访问 |

### 5.3 命令执行控制

沙盒还控制 AI 可以执行的系统命令：

```toml
[sandbox]
# 实验性：命令执行规则
[command_rules]
# 允许的命令模式
allowed_commands = [
    "npm *",
    "git *",
    "docker *",
    "python *",
    "node *",
    "cargo *"
]
# 禁止的命令模式
blocked_commands = [
    "rm -rf /*",
    "sudo *",
    "chmod *"
]
```

> **注意**: 命令执行规则目前是实验性功能，未来版本可能调整。

---

## 6. CI/CD 模式：codex exec

`codex exec` 是 Codex CLI 的非交互式执行模式，专为 CI/CD 场景设计。

### 6.1 基本用法

```bash
# 在 CI 中执行代码审查
codex exec -p "审查所有新增的 .ts 文件，检查类型安全问题和潜在 bug"

# 自动生成测试
codex exec -p "为 src/services/ 目录下的所有 Service 类生成单元测试"

# 代码重构
codex exec -p "将 src/legacy/ 目录下的 JavaScript 文件迁移到 TypeScript"
```

### 6.2 与 AGENTS.md 配合

在 CI 环境中，AGENTS.md 提供项目上下文：

```bash
# 在项目根目录执行，自动加载 AGENTS.md
cd /workspace/my-project
codex exec -p "按照 AGENTS.md 中的编码规范审查本次 PR 的代码变更"
```

### 6.3 GitHub Actions 示例

```yaml
name: Codex CI Review
on: [pull_request]
jobs:
  review:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with:
          node-version: '22'
      - run: npm install -g @openai/codex
      - run: |
          git diff origin/main...HEAD | \
          codex exec -p "审查这些代码变更，列出所有问题" \
            --mode read-only
        env:
          OPENAI_API_KEY: ${{ secrets.OPENAI_API_KEY }}
```

---

## 7. 最佳实践

### 7.1 AGENTS.md 维护建议

1. **保持简洁**: 每一条指令都应该有明确的意图，避免冗余
2. **定期更新**: 项目架构变化时同步更新 AGENTS.md
3. **团队评审**: AGENTS.md 应该像代码一样经过 Review
4. **版本控制**: 将 AGENTS.md 纳入 Git 管理，追踪变更历史
5. **渐进增强**: 先从核心规则开始，逐步补充细节

### 7.2 安全检查清单

- [ ] 不要在 AGENTS.md 中包含敏感信息（API Key、密码等）
- [ ] 审核 sandbox 白名单，确保最小权限原则
- [ ] 定期审计 AI 对文件系统的访问记录
- [ ] 在 CI/CD 中使用 `--mode read-only` 进行代码审查
- [ ] 为不同的项目分支设置不同的 AGENTS.md

### 7.3 常见问题

**Q: AGENTS.md 和 README.md 有什么区别？**
A: README.md 面向人类开发者，介绍项目使用方式；AGENTS.md 面向 AI 助手，提供编码规范和上下文指令。

**Q: 多个 AGENTS.md 文件内容冲突怎么办？**
A: 高优先级文件的内容排在前面。建议在个人配置中只放通用偏好，项目特定规则放在仓库 AGENTS.md 中。

**Q: 如何临时覆盖 AGENTS.md 的指令？**
A: 在会话中直接说明，例如："忽略 AGENTS.md 中的缩进规则，这次使用 4 空格缩进。"

---

## 参考链接

- [Codex CLI 快速上手与命令指南](./01-quickstart-and-commands.md)
- [高级工作流：MCP 集成与多 Agent 编排](./03-advanced-workflows.md)
- [Codex CLI GitHub](https://github.com/openai/codex)