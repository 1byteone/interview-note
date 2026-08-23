# Agent Teams 多 Agent 协作编排

> **生态**: E01 · Claude Code | **等级**: 专家 | **前置要求**: 熟悉 Claude Code 基础功能与 Skills 开发

Claude Code 的 **Agent Teams** 是一项实验性功能，允许在单个会话中启动多个 Agent 并行协作。每个 Agent 拥有独立的上下文窗口、工具集和指令，它们之间可以直接通信，由一个"团队领导"协调任务分配与结果汇聚。

本教程从架构原理出发，覆盖启用、角色设计、任务编排、通信模式到适用场景，帮助你利用多 Agent 并行处理复杂任务，显著提升开发效率。

---

## 1. Agent Teams 架构

### 1.1 核心概念

```
┌─────────────────────────────────────────────────┐
│  Team Lead (团队领导)                            │
│  - 接收用户的请求                                │
│  - 拆分任务并分配给 Teammate                     │
│  - 汇总结果 + 写入文件                          │
│  - 保持完整上下文                                │
├─────────────────────────────────────────────────┤
│                                                   │
│  ┌──────────┐   ┌──────────┐   ┌──────────┐      │
│  │Teammate A│   │Teammate B│   │Teammate C│      │
│  │ 独立上下文 │   │ 独立上下文 │   │ 独立上下文 │      │
│  │ 工具集 A  │   │ 工具集 B  │   │ 工具集 C  │      │
│  └────┬─────┘   └────┬─────┘   └────┬─────┘      │
│       │              │              │             │
│       └──────────────┼──────────────┘             │
│                     ◄►                             │
│              Teammate 间直接通信                   │
└─────────────────────────────────────────────────┘
```

- **Team Lead**：主会话中的 Agent，负责接收用户请求、拆解任务、分配子任务、汇总输出。它是团队的大脑，也是会话的入口。
- **Teammate**：独立运行的 Agent，每个拥有自己的上下文窗口（含独立的 CLAUDE.md 和指令），工具调用结果只返回给自身，不污染其他 Agent 的上下文。
- **通信通道**：Teammate 之间可以直接发送消息，无需经过 Team Lead 中转，适合并行任务间的状态同步。

### 1.2 与 Subagent 的区别

| 维度 | Agent Teams | Subagent（单 Agent） |
|------|-------------|----------------------|
| 并行度 | 多个 Agent 同时运行 | 一次一个 |
| 上下文覆盖 | 各 Agent 独立（分治） | 子 Agent 共享主上下文 |
| 通信 | Agent 间直接通信 | 仅主→子，子→主 |
| 生命周期 | 会话级，可扩展 | 单次任务 |
| 适用场景 | 大型重构、多模块开发 | 代码审查、模板生成 |

## 2. 启用 Agent Teams

### 2.1 环境变量

Agent Teams 当前为实验性功能，需要显式启用：

```bash
# 方式一：启动时设置
CLAUDE_CODE_EXPERIMENTAL_AGENT_TEAMS=1 claude

# 方式二：settings.json 中声明
```

```json
{
  "env": {
    "CLAUDE_CODE_EXPERIMENTAL_AGENT_TEAMS": "1"
  }
}
```

### 2.2 验证是否启用

启动 Claude Code 后执行：

```
> 查看当前 Agent Teams 状态
```

如果功能可用，Claude 会给出团队配置建议；如果不可用，检查环境变量是否生效。

## 3. 团队角色设计

### 3.1 角色分配原则

好的团队设计遵循"技能互补、上下文隔离"：

| 角色 | 职责 | 工具集 |
|------|------|--------|
| Team Lead | 接收需求、任务拆解、代码审查、汇总输出 | 读文件、Bash、写文件 |
| 数据库分析员 | 读取 Schema、分析查询、给出迁移建议 | MCP-PostgreSQL、Grep |
| 代码实现员 | 编写代码、生成模板、修改文件 | 写文件、Bash(run test) |
| 测试员 | 编写单元测试、运行测试、覆盖率检查 | Bash(test:*)、Read |
| 文档员 | 生成 README、API 文档、CHANGELOG | Read、Write |

### 3.2 典型团队配置

以下是一个"三 Agent 团队"的典型配置，各自持有独立的指令与工具集：

**Team Lead 指令**（从 CLAUDE.md 加载）：

```
你是一个全栈团队领导。你的职责：
1. 接收用户需求，拆解为可并行执行的任务
2. 为每个任务创建 Teammate（指定角色、指令、工具）
3. 汇总 Teammate 的输出，执行最终验证
4. 如果有冲突，协调解决
```

**Teammate 数据库分析员 指令**：

```
你是一个数据库分析师。你可以：
- 使用 MCP PostgreSQL 服务器查询数据库 Schema
- 使用 Grep 搜索项目中的 SQL 语句
- 使用 Read 读取现有的 Entity 和 Repository 代码
- 分析表关系、索引、查询性能

输出格式：Markdown 表格 + 变更建议
```

**Teammate 代码实现员 指令**：

```
你是一个 Java 后端开发者。你需要：
- 遵循分层架构（Controller → Service → Repository）
- 使用 DTO 模式，不暴露 Entity
- 使用 @Slf4j 记录日志
- 所有方法都必须有 Javadoc 注释

工具：Write（写文件）、Bash（编译/运行测试）
```

## 4. 任务拆分与分配

### 4.1 任务拆分策略

当用户请求复杂任务时，Team Lead 自动拆解任务。合理的拆分粒度是：

- **每个 Agent 负责一个"可独立验证"的模块**。例如"新增用户管理模块"拆为：Entity 层 → Repository 层 → Service 层 → Controller 层 → 测试；
- **避免过于细粒度**：一个 Agent 只写一行配置或一个字段，通信开销 > 并行收益；
- **基于依赖关系排序**：上游模块（如 Entity）优先分配，下游模块（如 Controller）稍后启动。

### 4.2 分配示例

```
用户：新增一个"订单管理"模块，包含 CRUD 接口和单元测试

Team Lead 拆解：
- Teammate A: 创建 Order 实体 + OrderRepository + 数据库迁移脚本
- Teammate B: 创建 OrderService + OrderServiceImpl
- Teammate C: 创建 OrderController + OrderCreateDTO/OrderRespDTO
- Teammate D: 运行所有测试，确认全部通过

依赖关系：A → B, B → C, D 在 A/B/C 完成后执行
```

Claude Code 会自动检测依赖关系，部分任务并行执行，带依赖的任务串行执行。

## 5. 通信模式

### 5.1 Team Lead → Teammate

Team Lead 通过 `SendMessage` 工具向 Teammate 分配任务。每个消息包含：

- `to`：Teammate 的名称
- `summary`：200 字以内的任务摘要（用于快速定位）
- `message`：详细的任务描述，包含上下文、约束、输出格式

### 5.2 Teammate → Team Lead

Teammate 完成任务后，向 Team Lead 发送结果摘要。Team Lead 择机汇总。

### 5.3 Teammate ↔ Teammate（直接通信）

Teammate 之间可以直接通信，无需经过 Team Lead 中转。典型场景：

- **状态同步**：数据库分析员确认表结构已就绪，通知代码实现员开始写 Service；
- **结果校验**：测试员发现测试失败，向代码实现员发送错误详情，请求修复；
- **信息共享**：一个 Agent 发现共享配置变更，通知其他 Agent 更新。

### 5.4 通信最佳实践

- **并发通信**：发送独立消息给多个 Agent，让它们并行工作；
- **摘要前置**：每条消息开头用 `summary` 字段说明意图，便于接收方快速判断优先级；
- **避免循环**：不要设计 A→B→A 的循环通信，容易导致无限循环或上下文膨胀。

## 6. 生命周期管理

### 6.1 创建

当 Team Lead 调用创建 Agent 的工具时，Agent Teams 自动启动一个 Teammate：

```
Team Lead 分配任务 → 创建 Teammate 上下文 → 注入指令 → 发送初始消息
```

### 6.2 运行

- Teammate 在后台独立运行，不阻塞 Team Lead 的处理；
- Team Lead 可以持续接收用户输入，或等待所有 Teammate 完成；
- 每个 Teammate 的上下文独立增长，互不影响。

### 6.3 完成

Teammate 完成任务后，通过 `SendMessage` 向 Team Lead 发送结果，然后自动结束。

### 6.4 清理

会话退出时，所有 Teammate 自动清理，不留下残留进程或文件锁。

## 7. 已知限制

Agent Teams 当前为实验性功能，存在以下已知限制：

| 限制 | 说明 | 替代方案 |
|------|------|----------|
| 会话恢复受限 | 含 Agent Teams 的会话无法通过 `--resume` 完整恢复 | 让 Team Lead 先用 `--continue` 处理，必要时重新创建 |
| 任务协调不完善 | 自动依赖检测在复杂 DAG 中可能失效 | 显式声明依赖关系，或在任务描述中手写"先/后"顺序 |
| 上下文上限 | 并行 Agent 过多时，总 token 消耗可能激增 | 控制 Teammate 数量在 3-5 个，使用 `/compact` 压缩 |
| 通信延迟 | 跨 Agent 消息有延迟，不适合实时交互 | 非关键状态用"最终同步"模式而非"实时同步" |
| 跨会话持久化 | Teammate 状态不持久化 | 关键结果由 Team Lead 写入文件保存 |

## 8. 典型使用场景

### 8.1 大型重构

```
场景：将单体应用拆分为微服务

Agent A: 分析现有模块依赖关系 → 输出模块依赖图
Agent B: 设计微服务边界和 API 契约 → 输出 OpenAPI 规范
Agent C: 提取 User 模块为独立服务 → 生成代码 + 配置
Agent D: 提取 Order 模块为独立服务 → 生成代码 + 配置
Agent E: 编写集成测试，验证拆分后功能正常
```

### 8.2 多模块并行开发

```
场景：新增一个"消息通知"功能

Agent A: 数据库设计（通知表、用户通知关联表）
Agent B: 通知发送服务（邮件 + 站内信）
Agent C: 通知管理后台（列表、详情、重试）
Agent D: 单元测试 + 集成测试
```

### 8.3 多语言/多平台项目

```
场景：同一业务逻辑，同时生成前端和后端代码

Agent A: 后端 Spring Boot 实现（Controller + Service + Repository）
Agent B: 前端 Vue 3 实现（页面 + 组件 + API 调用层）
Agent C: 两端联调 API 契约测试
```

### 8.4 安全扫描 + 修复

```
场景：对项目进行安全审计并修复问题

Agent A: 扫描所有 SQL 注入风险（Grep + 正则分析）
Agent B: 扫描所有 XSS 风险（前端模板分析）
Agent C: 修复 Agent A 发现的问题（修改 SQL 为参数化查询）
Agent D: 修复 Agent B 发现的问题（添加转义/过滤）
Agent E: 确认修复后全部测试通过
```

## 9. 性能与成本考量

| 策略 | 效果 |
|------|------|
| 控制 Teammate 数量 ≤ 5 | 平衡并行度与 token 消耗 |
| 使用 `/compact` 压缩长上下文 | 降低后续请求的 token 成本 |
| 任务粒度适中 | 避免"过于细粒度"导致的通信开销超过并行收益 |
| 共享只读数据 | 用文件或 MCP 服务器共享，避免每个 Agent 各自读取 |
| 非关键路径下沉 | 低优先级任务（如生成文档）放在最后，不阻塞核心流程 |

## 10. 最佳实践小结

1. **先单后多**：先在一个 Session 中验证单 Agent 方案，再引入 Teams 并行；
2. **角色边界清晰**：每个 Teammate 有明确的职责边界，避免"谁都能写任何文件"的混乱；
3. **依赖显式化**：在任务描述中写明前置依赖，帮助 Team Lead 正确编排顺序；
4. **结果汇总为王**：Team Lead 是最终输出者，所有 Teammate 的结果都要经过汇总与验证；
5. **监控 token 消耗**：并行 Agent 的 token 消耗是线性叠加的，定期用 `/cost` 检查；
6. **善用 MCP 服务器**：将共享数据源（数据库、API）通过 MCP 暴露，避免每个 Agent 重复认证；
7. **实验性特征**：Agent Teams 仍在迭代，关注 Anthropic 官方 changelog 获取更新。

---

## 进阶指引

- 上一篇：[MCP 集成与外部工具扩展](./03-mcp-integration.md)
- 生态仓库：[claude-code-ultimate-guide](../../repositories/FlorianBruniaux_claude-code-ultimate-guide.md)（Agent Teams 深度章节 + 安全威胁数据库）