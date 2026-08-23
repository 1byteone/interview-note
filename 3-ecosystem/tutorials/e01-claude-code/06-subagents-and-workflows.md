# Subagents 子代理与动态工作流

> **生态**: E01 · Claude Code | **等级**: 专家 | **前置要求**: 熟悉 Claude Code 基础功能、Skills 开发与 Agent Teams

Claude Code 的 **Subagents（子代理）** 是一种将复杂任务分解为多个专业化 Agent 协同执行的机制。与 Agent Teams 不同，Subagents 运行在**单个会话内**，每个子代理拥有独立的系统提示词、上下文窗口和工具白名单，通过明确的职责边界实现"分而治之"。

本教程从架构原理、配置方法、嵌套规则到动态工作流编排，帮助你利用 Subagents 处理大规模代码库审计、迁移与交叉审查等高难度任务。

---

## 1. Subagents 架构

### 1.1 核心概念

```
┌──────────────────────────────────────────────────┐
│  Main Session（主会话）                          │
│  - 接收用户请求                                  │
│  - 调度子代理                                    │
│  - 汇总结果                                      │
│  - 拥有完整工具集                                │
├──────────────────────────────────────────────────┤
│                                                   │
│  ┌──────────────┐  ┌──────────────┐             │
│  │ Code Reviewer│  │ Security     │  ...        │
│  │ Subagent     │  │ Auditor      │             │
│  │              │  │ Subagent     │             │
│  │ 上下文 A     │  │ 上下文 B     │             │
│  │ 工具：Read   │  │ 工具：Grep   │             │
│  │ 系统提示 X   │  │ 系统提示 Y   │             │
│  └──────────────┘  └──────────────┘             │
│                                                   │
│  特点：                                          │
│  - 各子代理上下文完全隔离                        │
│  - 工具集独立配置（最小权限原则）                │
│  - 系统提示词针对单一职责优化                    │
└──────────────────────────────────────────────────┘
```

每个 Subagent 都是"小型 Claude 实例"：

- **独立系统提示词**：子代理只知道自己的职责，不会被主会话的其他上下文干扰；
- **独立上下文窗口**：子代理处理过程的中间结果不污染主上下文，主会话只接收最终汇总；
- **独立工具白名单**：每个子代理只持有完成本职工作必需的工具，降低误操作风险；
- **独立模型选择**：可以为不同子代理指定不同模型（如用 Haiku 处理简单检索、Sonnet 处理复杂分析）。

### 1.2 Subagents vs Agent Teams 对比

| 维度 | Subagents | Agent Teams |
|------|-----------|-------------|
| 运行环境 | 单个会话内 | 跨多个独立会话 |
| 上下文关系 | 各自独立，主会话汇总 | 各自完全独立 |
| 通信方式 | 主会话调度，结果回传 | Teammate 之间可直接通信 |
| 配置方式 | `.claude/agents/*.md` | 环境变量 + Team Lead 指令 |
| 嵌套深度 | 支持嵌套（最多 5 层） | 通常单层 |
| 适用规模 | 中等任务（数十文件） | 大型任务（数百文件 / 跨模块） |
| 启用成本 | 即开即用 | 需启用实验性环境变量 |
| 结果一致性 | 高（同一会话上下文） | 中（需 Team Lead 汇总） |

简单选择标准：

- 任务可在 **单次会话** 内完成 → 用 **Subagents**；
- 任务需要 **多会话并行** 或 **跨仓库协作** → 用 **Agent Teams**（见[第 4 篇](./04-agent-teams.md)）。

## 2. 配置 Subagents

### 2.1 文件结构

Subagents 通过 `.claude/agents/` 目录下的 Markdown 文件配置，每个文件对应一个子代理：

```
.claude/agents/
├── code-reviewer.md        # 代码审查员
├── debugger.md             # 调试专家
├── security-auditor.md     # 安全审计员
├── doc-writer.md           # 文档撰写员
└── migration-assistant.md  # 迁移助手
```

### 2.2 Frontmatter 字段

每个 `.md` 文件由 YAML frontmatter 和 Markdown 正文两部分组成：

```markdown
---
name: code-reviewer
description: 对 Java 后端代码进行分层架构审查，检查 DTO 泄漏、事务边界、异常处理。
tools:
  - Read
  - Grep
  - Glob
model: claude-sonnet-4-5
---

# Code Reviewer

你是一个资深的 Java 代码审查专家。你的职责是...

## 审查重点
1. Controller 层不应直接操作 Repository
2. Service 层方法必须有事务注解
3. DTO 中不能包含 Entity 引用
```

字段说明：

| 字段 | 类型 | 说明 |
|------|------|------|
| `name` | string | 子代理唯一标识，kebab-case |
| `description` | string | 一句话职责描述，主会话据此判断何时调用 |
| `tools` | string[] | 允许使用的工具白名单，未列出的工具不可用 |
| `model` | string | 子代理使用的模型，可不同于主会话 |

### 2.3 正文：系统提示词

frontmatter 之后的 Markdown 正文即为子代理的**系统提示词**。编写要点：

- **角色明确**：开头一句话定义身份（"你是…"）；
- **职责清晰**：列出 3-5 条核心任务；
- **约束显式**：明确"禁止做什么"（如"不要修改文件"）；
- **输出规范**：规定输出格式（Markdown 表格、JSON、代码块）；
- **边界清晰**：说明遇到无法处理的情况应如何反馈。

## 3. 创建专业化子代理

### 3.1 代码审查员

`.claude/agents/code-reviewer.md`：

```markdown
---
name: code-reviewer
description: 对 Spring Boot 项目进行分层架构审查，发现 Controller 越权、DTO 泄漏、事务缺失等问题。
tools:
  - Read
  - Grep
  - Glob
model: claude-sonnet-4-5
---

# Code Reviewer

你是一个资深的 Java 代码审查专家，专注于 Spring Boot 项目的分层架构合规性审查。

## 审查清单

1. **分层依赖**：Controller 只能调用 Service，Service 只能调用 Repository，不允许跨层调用
2. **DTO 边界**：API 出入参必须使用 DTO，Controller 不应返回 Entity
3. **事务管理**：Service 层写操作方法必须有 `@Transactional` 注解
4. **异常处理**：必须有全局异常处理器，业务异常使用自定义异常类
5. **日志规范**：关键操作必须有日志，使用 `@Slf4j` 而非手动创建 Logger

## 输出格式

按以下 Markdown 表格输出：

| 严重级别 | 文件 | 行号 | 问题 | 修复建议 |
|----------|------|------|------|----------|
| 严重 | UserController.java | 42 | 直接调用 Repository | 注入 UserService，改为调用 service.findById() |

## 约束

- 只读不写：发现问题但不修改文件，修复由主会话或用户决定
- 不审查测试代码：测试目录（src/test）跳过
- 不审查配置文件：application.yml 等配置文件跳过
```

### 3.2 调试专家

`.claude/agents/debugger.md`：

```markdown
---
name: debugger
description: 分析 Java 应用异常堆栈，定位根因并给出修复方案。
tools:
  - Read
  - Grep
  - Glob
  - Bash
model: claude-sonnet-4-5
---

# Debugger

你是一个 Java 应用调试专家，擅长从异常堆栈中定位根因。

## 分析步骤

1. **解析堆栈**：识别异常类型、抛出位置、调用链
2. **追溯源码**：Read 到对应文件和行号，理解上下文
3. **复现条件**：分析触发条件（参数、状态、并发）
4. **根因判断**：给出最可能的根因，按概率排序
5. **修复方案**：提供 2-3 种修复方案，说明优劣

## 输出格式

### 根因分析
- 异常类型：NullPointerException
- 抛出位置：UserService.findById():87
- 根因：findById 返回 Optional 但调用方未判空

### 修复方案
1. **方案 A**：调用方添加判空（影响范围小）
2. **方案 B**：被调方抛出业务异常（更符合语义）

## 工具使用

- `Bash` 仅用于运行诊断命令（如查看日志、检查线程栈），不可修改代码
- 修改代码由主会话执行
```

### 3.3 安全审计员

`.claude/agents/security-auditor.md`：

```markdown
---
name: security-auditor
description: 扫描项目中的安全漏洞，包括 SQL 注入、XSS、CSRF、敏感信息泄露、权限绕过。
tools:
  - Read
  - Grep
  - Glob
model: claude-sonnet-4-5
---

# Security Auditor

你是一个应用安全审计专家，专注于 Java Web 应用的常见漏洞检测。

## 扫描项

| 类别 | 检测模式 | Grep 关键词 |
|------|----------|-------------|
| SQL 注入 | 字符串拼接 SQL | `String sql.*\+` |
| XSS | 未转义输出 | `response.getWriter().write` |
| 敏感信息 | 硬编码密钥 | `password\s*=\s*"` |
| 权限绕过 | 缺少权限注解 | `@RequestMapping(?!.*@PreAuthorize)` |
| 反序列化 | 不安全反序列化 | `ObjectInputStream` |

## 输出格式

按 CVSS 评分分级：

| 风险等级 | 位置 | 漏洞类型 | 描述 | 修复建议 |
|----------|------|----------|------|----------|
| 高危 | UserMapper.xml:23 | SQL 注入 | 使用 ${} 拼接用户输入 | 改为 #{} 参数化查询 |

## 约束

- 不读取 .git、node_modules、target 目录
- 不修改任何文件，仅输出报告
- 发现高危漏洞立即标记，不要等待完整扫描结束
```

## 4. 嵌套子代理

Subagents 支持嵌套调用，即一个子代理内部还可以再调用其他子代理。这允许构建"分层专家系统"——顶层子代理负责统筹，底层子代理执行具体细分任务。

### 4.1 嵌套深度限制

```
主会话（深度 0）
  └─ 子代理 A（深度 1）
       └─ 子代理 A1（深度 2）
            └─ 子代理 A11（深度 3）
                 └─ 子代理 A111（深度 4）
                      └─ 子代理 A1111（深度 5，上限）
```

**最大嵌套深度为 5**。超过此深度的调用会被拒绝，子代理会收到错误并需要自行处理（通常会汇总已获取的信息返回）。

### 4.2 嵌套使用场景

- **分层审查**：顶层"架构审查员"调用"分层审查员"（Controller / Service / Repository 各一个子代理）；
- **分模块扫描**：顶层"安全审计员"按模块拆分，每个子代理扫描一个微服务；
- **多视角分析**：顶层"问题分析员"调用"代码审查员"、"安全审计员"、"性能分析师"，汇总多视角结论。

### 4.3 嵌套最佳实践

```markdown
# 顶层子代理：chief-auditor.md

---
name: chief-auditor
description: 统筹代码质量、安全、性能三方面的综合审计。
tools:
  - Read
  - Agent
---

# Chief Auditor

你是项目的首席审计员。你的工作方式：

1. 接收用户的审计请求
2. 并行调用三个子代理：
   - `code-reviewer`：代码质量审查
   - `security-auditor`：安全漏洞扫描
   - `performance-analyst`：性能瓶颈分析
3. 收集三个子代理的报告
4. 去重、合并、按严重级别排序
5. 输出综合审计报告

## 注意

- 并行调用以提升效率
- 如果某子代理返回错误，继续其他子代理
- 不要自己执行具体审查，你的职责是统筹
```

## 5. 动态工作流

**动态工作流（Dynamic Workflows）** 是 Subagents 的高级用法：通过脚本或主会话的 Agent 工具，根据任务需求动态编排大量子代理，形成临时但高效的工作流。

### 5.1 与静态子代理的区别

| 维度 | 静态子代理 | 动态工作流 |
|------|-----------|------------|
| 触发方式 | 主会话按需调用 | 由脚本批量调度 |
| 子代理数量 | 通常 1-3 个 | 可达数十个 |
| 任务粒度 | 较粗（一个子代理负责一个领域） | 较细（一个子代理负责一个文件 / 模块） |
| 编排方式 | 人工或主会话决策 | 脚本逻辑 / DAG 调度 |
| 适用场景 | 单次审查、单次调试 | 大规模迁移、全仓库审计 |

### 5.2 大规模代码库审计

**场景**：对一个包含 200+ Java 文件的老旧项目进行全面审计，找出所有架构违规问题。

**传统方式**（主会话直接扫描）：
- 主上下文会被 200 个文件的内容撑爆；
- 审查质量随上下文增长而下降；
- 无法并行，耗时长。

**动态工作流方式**：

```
主会话
  ├─ Glob 扫描所有 .java 文件，得到文件列表
  ├─ 按包路径分组（每包 5-10 个文件）
  ├─ 为每组动态创建一个子代理：
  │    ├─ com.example.user 包 → reviewer-1
  │    ├─ com.example.order 包 → reviewer-2
  │    ├─ com.example.payment 包 → reviewer-3
  │    └─ ...
  ├─ 并行调用所有子代理
  ├─ 收集所有报告
  └─ 合并为统一审计报告
```

主会话的指令可以这样写：

```
对整个项目进行架构审计：
1. 用 Glob 找出所有 src/main/java 下的 .java 文件
2. 按包路径分组，每组 5 个文件以内
3. 为每组调用 code-reviewer 子代理
4. 收集所有子代理报告，合并为 Markdown 审计报告
```

主会话会自动完成调度，无需人工干预。

### 5.3 大规模框架迁移

**场景**：将一个 Spring Boot 2.x 项目升级到 3.x，涉及 `javax.*` 到 `jakarta.*` 的包名迁移。

**动态工作流编排**：

```
主会话
  ├─ Grep 找出所有 import javax.* 的文件
  ├─ 按文件类型分组：
  │    ├─ Controller 文件 → migration-controller（子代理 A）
  │    ├─ Service 文件 → migration-service（子代理 B）
  │    ├─ Config 文件 → migration-config（子代理 C）
  │    └─ 其他 → migration-misc（子代理 D）
  ├─ 并行调用 4 个子代理
  ├─ 每个子代理使用 Edit 工具修改各自负责的文件
  └─ 主会话验证编译通过
```

每个迁移子代理的系统提示词：

```markdown
---
name: migration-service
description: 将 Service 文件中的 javax.* 包名迁移为 jakarta.*。
tools:
  - Read
  - Edit
  - Grep
---

# Migration Assistant (Service)

你的任务：将指定 Service 文件中的 `import javax.*` 替换为 `import jakarta.*`。

## 规则

1. 仅替换以下包：
   - javax.persistence → jakarta.persistence
   - javax.validation → jakarta.validation
   - javax.servlet → jakarta.servlet
   - javax.annotation → jakarta.annotation
2. 不替换 javax.sql、javax.net 等仍在 Java 标准库中的包
3. 修改完成后运行 `./mvnw compile -pl <module>` 验证
4. 如果编译失败，回退修改并报告问题
```

### 5.4 交叉审查工作流

**场景**：对一份关键代码进行多视角审查，避免单一视角的盲区。

```
主会话
  ├─ 同时调用三个子代理：
  │    ├─ code-reviewer：架构与规范
  │    ├─ security-auditor：安全漏洞
  │    └─ performance-analyst：性能问题
  ├─ 收集三份报告
  ├─ 交叉对比：
  │    ├─ code-reviewer 与 security-auditor 的交集 → 架构导致的安全问题
  │    └─ security-auditor 与 performance-analyst 的交集 → 安全机制引入的性能损耗
  └─ 输出综合报告 + 优先级建议
```

## 6. 子代理调度实践

### 6.1 并行 vs 串行

- **并行**：子代理之间无依赖时，并行调用可显著缩短总耗时；
- **串行**：后一个依赖前一个的输出时，必须串行；
- **混合**：大多数实际场景是 DAG，部分并行部分串行。

### 6.2 结果汇总策略

主会话汇总子代理结果时，建议：

1. **结构化收集**：要求每个子代理按统一格式输出（如 Markdown 表格），便于合并；
2. **去重合并**：多个子代理可能报告相同问题，主会话需去重；
3. **优先级排序**：按严重级别或影响范围排序，而非按子代理调用顺序；
4. **上下文回写**：最终报告写回主会话上下文，供用户查看和后续操作。

### 6.3 错误处理

子代理可能失败（如文件不存在、工具调用超时、嵌套深度超限）。主会话应：

- 捕获子代理的错误输出；
- 不中断整体流程，继续其他子代理；
- 在最终报告中标注失败的部分；
- 必要时重试或切换策略。

## 7. 性能与成本考量

| 策略 | 效果 |
|------|------|
| 限制并发数 | 避免同时运行过多子代理导致 token 消耗激增，建议 ≤ 5 |
| 选择合适模型 | 简单检索用 Haiku，复杂分析用 Sonnet，减少总成本 |
| 缩小工具集 | 工具越少，子代理上下文越精简，决策越聚焦 |
| 设置明确边界 | 子代理只输出报告而非直接修改，降低回滚成本 |
| 复用静态子代理 | 同一类任务优先复用已配置的子代理，避免重复定义 |

## 8. 最佳实践小结

1. **单一职责**：每个子代理只做一件事，职责越窄效果越好。一个"代码审查员"远胜于一个"通用审查员"。
2. **最小工具集**：`tools` 字段只列出必需工具。只读审查的子代理不应有 Write/Edit 权限。
3. **Description 决定调用**：主会话依据 `description` 判断何时调用子代理，描述要具体、可区分（参考 [第 2 篇 Skills 开发](./02-skills-development.md) 中的 Description 编写艺术）。
4. **输出结构化**：要求子代理按固定格式输出（表格、JSON、代码块），便于主会话汇总与用户阅读。
5. **嵌套适度**：嵌套深度 ≤ 3 通常够用，5 层嵌套仅在极特殊场景下使用，会增加调试难度。
6. **动态工作流优先**：面对大规模任务，优先考虑动态工作流（脚本编排数十个子代理）而非让主会话独自处理。
7. **错误隔离**：一个子代理失败不应影响整体流程，主会话设计时要预留错误处理路径。
8. **审查静态子代理源码**：加载第三方子代理配置前，阅读其 `.md` 文件，确认 `tools` 和系统提示词没有恶意行为（参考 [openclaw-security-practice-guide](../../repositories/slowmist_openclaw-security-practice-guide.md) 的 Agent 安全章节）。

---

## 进阶指引

- 上一篇：[Hooks 自动化：在生命周期中注入确定性逻辑](./05-hooks-automation.md)
- 系列起点：[Claude Code 安装与基础使用](./01-installation-and-basics.md)
- 生态仓库：[claude-code-ultimate-guide](../../repositories/FlorianBruniaux_claude-code-ultimate-guide.md)（Subagents 与动态工作流深度章节）