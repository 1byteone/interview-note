# Claude Code Skills 开发实战

> **生态**: E01 · Claude Code | **等级**: 进阶 | **前置要求**: 熟悉 Claude Code 基础命令与项目配置

Claude Code Skills（技能）是一套用于将可复用领域知识、工作流和指令封装为"即插即用"模块的机制。它源自开放标准 **Agent Skills**（见 [agentskills.io](https://agentskills.io)），让你可以把团队沉淀的最佳实践、代码模板、领域 DSL 直接挂载到 Claude Code 会话中。

本教程聚焦于"开发自己的 Skill"：从架构理解、SKILL.md 编写到子代理执行、动态注入与分发，帮助你建立可演进的技能库。

---

## 1. Skills 架构总览

一个 Skill 在文件系统上就是一个目录，内含一个 `SKILL.md` 描述文件及可选的辅助资源：

```
.claude/skills/
├── code-reviewer/
│   ├── SKILL.md
│   ├── checklist.md
│   └── templates/
│       └── review-comment.md
├── sql-migrator/
│   └── SKILL.md
└── apifox-sync/
    ├── SKILL.md
    └── scripts/
        └── sync.sh
```

关键特征：

- **声明式加载**：Claude 启动会话时扫描 `.claude/skills/*/SKILL.md`，根据 frontmatter 的 `description` 与 `trigger` 自动判断是否注入当前上下文；
- **目录即边界**：所有辅助文件（脚本、模板、JSON 数据）都通过相对路径引用，便于分发与版本管理；
- **可分层**：用户级技能放 `~/.claude/skills/<name>/`，跨项目复用；项目级技能放 `<repo>/.claude/skills/<name>/`，随仓库共享。

## 2. SKILL.md 结构

### 2.1 基本骨架

```markdown
---
name: sql-migrator
description: 生成符合团队规范的 Flyway 迁移脚本，包括命名、注解和回滚提示。
metadata:
  type: workflow
  trigger: 当用户请求创建数据库迁移、修改表结构或新增字段时
---

# SQL Migrator

## 何时使用
- 新建表、修改字段、加索引
- 需要生成回滚 SQL

## 执行步骤
1. 读取 `db/migration/` 目录最新版本号
2. 询问目标变更
3. 生成 `V{next}__{description}.sql`
4. 同时生成 `U{next}__{description}.sql` 回滚脚本

## 输出规范
- 表名 snake_case，复数
- 字段名 snake_case
- 每个表必须有 `created_at` / `updated_at`
```

### 2.2 Frontmatter 字段说明

| 字段 | 说明 |
|------|------|
| `name` | 技能唯一标识，建议 kebab-case |
| `description` | 一句话描述，**最关键的字段**，Claude 依赖它判断是否触发 |
| `metadata.type` | 类型：`workflow`（流程）/`reference`（参考资料）/`command`（命令）等 |
| `trigger` | 自然语言触发条件描述，可使用模板变量 |
| `subagent` | `true` 时技能在独立子代理上下文中执行（见 §4） |
| `allowed-tools` | 限制技能可用工具，最小权限原则 |

### 2.3 Description 的编写艺术

Claude 在每次请求时，将所有 Skill 的 `description` 与用户意图做语义匹配。写好描述等同于写好"检索召回文档"：

- ❌ "代码审查"：太泛，所有代码问题都会命中；
- ✅ "针对 Spring Boot 3 项目的分层架构审查：检查 Controller/Service/Repository 是否违反约定，DTO 是否泄漏 Entity"：具体、可区分、带上下文。

## 3. 创建自定义 Skill：实战

下面我们构建一个 **`commit-helper`** 技能：根据 Git diff 生成符合 Conventional Commits 规范的提交消息。

### 3.1 目录结构

```
.claude/skills/commit-helper/
├── SKILL.md
└── examples.md
```

### 3.2 SKILL.md

```markdown
---
name: commit-helper
description: 根据 git diff 自动生成 Conventional Commits 风格的提交消息，支持中英双语。
metadata:
  type: workflow
  trigger: 当用户要求"写提交消息""生成 commit message""帮我 commit"时
allowed-tools:
  - Bash(git diff:*)
  - Bash(git status:*)
  - Bash(git log:*)
---

# Commit Helper

## 执行步骤

1. 运行 `git status --short` 与 `git diff --cached`（优先）或 `git diff` 查看改动
2. 读取最近 5 条提交作为风格参考：`git log -5 --oneline`
3. 按以下模板生成提交消息：

   ```
   <type>(<scope>): <subject>

   <body>
   ```

   type 取值：feat / fix / docs / style / refactor / test / chore / perf

4. 提交消息为中文 subject（≤50 字），body 用列表说明改动要点
5. 输出最终命令：`git commit -m "..."`，由用户确认后执行

## 边界
- 不要自动执行 `git push`
- 如果检测到 `package-lock.json` 或 `pnpm-lock.yaml` 变更，body 中需说明依赖变更
```

### 3.3 辅助资源 `examples.md`

```markdown
# 提交消息示例

## feat
feat(user-service): 新增按手机号查询接口

- 新增 UserService.findByPhone 方法
- 添加对应控制器路由 /api/users/phone/{phone}
- 覆盖单元测试 3 例

## fix
fix(auth): 修复 token 过期判断逻辑

- 修正 expiresIn 单位混淆（秒 vs 毫秒）
- 增加 5000ms 容差，避免边界过期
```

技能执行时，Claude 会自动读取 `examples.md` 作为参考样本。

## 4. 子代理执行（Subagent）

当一个技能任务繁重（例如扫描整个仓库生成架构报告），在主上下文中执行会挤占 token 预算。此时启用 `subagent: true`：

```yaml
---
name: architecture-report
description: 扫描整个仓库，生成 Markdown 格式的架构分析报告（模块依赖、技术栈、风险点）。
metadata:
  type: workflow
subagent: true
allowed-tools:
  - Read(**)
  - Grep(**)
  - Glob(**)
---
```

子代理模式下：

- Claude 启动一个独立 Agent，拥有自己的上下文窗口与工具权限；
- 主会话只接收最终汇总结果，避免中间产物污染；
- 子代理默认不可写文件，需要写权限时显式声明 `allowed-tools`。

### 4.1 何时使用子代理

| 场景 | 推荐 |
|------|------|
| 简单代码模板生成 | 主上下文 |
| 跨多文件读取 + 大量中间推理 | 子代理 |
| 需要长时运行 + 中断风险高 | 子代理 |
| 需要与用户多轮交互 | 主上下文 |

## 5. 动态上下文注入

Skills 支持 `{{variable}}` 模板语法，在执行时由 Claude 动态填充。常用于参数化模板、跨项目复用：

```markdown
---
name: api-endpoint-generator
description: 根据给定的实体名 {{EntityName}} 和字段列表，生成符合团队规范的 RESTful 接口代码。
trigger: 当用户说"为 {{EntityName}} 生成接口"或"生成 CRUD for {{EntityName}}"时
---

## 输出

- Controller: `{{EntityName}}Controller.java`
- Service: `{{EntityName}}Service.java` + `{{EntityName}}ServiceImpl.java`
- Mapper: `{{EntityName}}Mapper.java` + `{{EntityName}}Mapper.xml`
- DTO: `{{EntityName}}CreateDTO.java`, `{{EntityName}}RespDTO.java`
- 单元测试: `{{EntityName}}ControllerTest.java`
```

执行时，Claude 会：

1. 从用户消息中提取 `EntityName`（例如 "User"）；
2. 把 SKILL.md 中所有 `{{EntityName}}` 替换为实际值；
3. 按模板逐个生成文件，每个文件写完后向用户展示路径。

## 6. 内置 Skills 速查

Claude Code 自带若干内置技能，可直接通过斜杠命令调用，也是学习 SKILL.md 写法的优秀样本：

| 命令 | 作用 |
|------|------|
| `/debug` | 进入调试模式，查看内部日志、工具调用链 |
| `/code-review` | 对当前改动发起代码审查 |
| `/test` | 自动为指定代码生成或运行测试 |
| `/explain` | 解释选中代码的功能、设计意图 |
| `/security-review` | 安全审查（CVE、注入、权限） |
| `/init` | 初始化项目的 CLAUDE.md |

阅读这些内置技能的源码（通常位于 `~/.claude/skills/` 下）是快速提高 Skill 编写水平的捷径。

## 7. 测试与调试

### 7.1 验证加载

启动会话后用 `/help` 查看是否列出你的技能；若未出现，检查：

- 文件路径是否为 `.claude/skills/<name>/SKILL.md`（注意大小写）；
- frontmatter 是否能被 YAML 解析（缩进、引号闭合）；
- `description` 是否为空。

### 7.2 检查触发

用自然语言测试触发条件：

```
> 帮我写一条提交消息         # 应触发 commit-helper
> 给 User 实体生成 CRUD 接口  # 应触发 api-endpoint-generator
```

如果不触发，最常见原因是 description 写得太泛或太窄。可以临时改写为更明确的句子再次测试。

### 7.3 调试模式

```bash
claude --debug
```

开启后会输出技能加载、上下文注入、工具调用的详细日志，便于定位"为什么没触发"或"触发了但参数错误"。

### 7.4 单元化测试

把技能拆解为可独立验证的步骤，单独测试每一步：

- 模板渲染：手动替换 `{{var}}` 检查输出；
- 脚本调用：在终端单独执行 `scripts/sync.sh` 验证；
- 工具权限：在 `allowed-tools` 中临时只保留一个工具，确认最小可用集。

## 8. 分发与版本管理

### 8.1 项目内分发

将 `.claude/skills/` 纳入 Git 仓库，团队成员拉取代码即获得相同技能集，是事实上的团队标准。

### 8.2 跨项目复用

放到 `~/.claude/skills/<name>/`，所有项目可用。适合通用工具类技能（如 `commit-helper`）。

### 8.3 社区分发

参考 [awesome-agent-skills](../../repositories/heilcheng_awesome-agent-skills.md) 等仓库，将技能打包为独立 Git 仓库并发布。建议：

- 仓库根放 `SKILL.md`，用户 `git clone` 后软链到 `.claude/skills/<name>/`；
- 提供最小可运行示例与 `README`，说明依赖与触发条件；
- 用语义化版本（SemVer）标记技能演化，破坏性变更升 major。

### 8.4 安全提示

技能是"指令注入"的天然载体，加载第三方技能前应：

- 阅读 SKILL.md 全文，特别注意 `allowed-tools`；
- 检查 `scripts/` 中的可执行文件；
- 在隔离环境（如 worktree、容器）中先试运行。

详见 [openclaw-security-practice-guide](../../repositories/slowmist_openclaw-security-practice-guide.md) 中的 Agent 安全实践章节。

## 9. 最佳实践小结

1. **Description 是第一公民**：花 50% 的精力打磨它，直接决定触发准确率；
2. **最小权限**：`allowed-tools` 只列必需工具，降低误操作风险；
3. **辅助文件优先**：长内容拆到 `checklist.md` / `examples.md`，主文件保持精简；
4. **版本化**：随仓库入 Git，用 PR 评审技能变更；
5. **分层组织**：通用技能上提到用户级，项目特定技能留项目级；
6. **先复用后自造**：参考 [awesome-agent-skills](../../repositories/libukai_awesome-agent-skills.md) 现成技能，避免重复造轮子。

---

## 进阶指引

- 上一篇：[Claude Code 安装与基础使用](./01-installation-and-basics.md)
- 下一篇：[MCP 集成与外部工具扩展](./03-mcp-integration.md) — Skills 调用外部服务的标准协议