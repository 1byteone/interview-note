> **[← 目录](README.md)** | 章节 07/12

# 第七部分 Context Engineering：从 Prompt 到 Context

2026 年，AI 工程领域最重要的概念转变是 **从 Prompt Engineering 到 Context Engineering**。([Anthropic][5])

## 7.1 为什么 Context 比 Prompt 重要

以前：写更好的 Prompt
现在：设计更好的 Context

一个 Agent 的上下文应该包括：

```
Context
=
项目规范（CLAUDE.md / AGENTS.md）
+ 架构文档
+ 当前任务
+ 相关代码
+ 历史决策（ADR）
+ 测试覆盖
+ 工具能力（MCP）
+ Skills
+ Git 状态
+ CI/CD 状态
+ 监控数据
```

## 7.2 AGENTS.md：跨工具统一规范

AGENTS.md 是 2026 年出现的**跨工具标准规范文件**，所有 AI 编程工具都能读取：

```markdown
# AGENTS.md - 跨工具 AI 编程规范

## 项目概述
电商平台后端，Spring Boot 3 微服务架构。

## 架构原则
- 单一职责
- 关注点分离
- 依赖倒置
- 领域驱动设计

## 编码标准
- Java 21 LTS
- 不使用 Lombok
- 所有公共 API 必须有 Javadoc
- 异常处理必须有日志记录

## 测试标准
- 单元测试覆盖率 ≥ 85%
- 所有 API 必须有集成测试
- 使用 Testcontainers 做集成测试
- 禁止 mock 数据库连接

## 安全标准
- 禁止硬编码密钥/密码
- 所有输入必须校验
- SQL 查询必须使用参数化
- 敏感数据必须加密存储

## Git 标准
- 分支策略: Git Flow
- 提交信息: Conventional Commits
- PR 必须通过 CI + Code Review
```

## 7.3 记忆体系设计

### 三级记忆架构

```
Session Memory（会话内）
    ↓ 转存
Short-term Memory（跨会话，Hermes MEMORY.md / Claude Auto Memory）
    ↓ 精炼
Long-term Memory（永久，文档/AGENTS.md/知识库）
```

### Claude Code 的双记忆系统

| 机制 | 来源 | 用途 |
|------|------|------|
| **CLAUDE.md** | 人工编写 | 项目规范、架构约束 |
| **Auto Memory** | Claude 自动记录 | 发现的模式、纠正、经验 |

## 7.4 四大 Context 策略

根据 Anthropic 和业界实践，Context Engineering 有四大策略：

| 策略 | 说明 | 示例 |
|------|------|------|
| **Write** | 写入上下文 | CLAUDE.md、AGENTS.md、Memory |
| **Select** | 选择相关上下文 | Skills 按需加载、Subagent 结果 |
| **Compress** | 压缩上下文 | 代码摘要、历史压缩 |
| **Isolate** | 隔离上下文 | Subagent 独立上下文窗口 |

---

---

[← 上一章: 06-Cursor](06-Cursor.md) | [目录](README.md) | [下一章: 08-MCP与Skills(08-MCP与Skills.md)](08-MCP与Skills.md)
