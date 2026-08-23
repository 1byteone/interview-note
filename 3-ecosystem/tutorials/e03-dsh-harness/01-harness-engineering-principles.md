# Harness 工程原理：智能体 = 大模型 + Harness

> **生态**: E03 · DSH/Harness | **等级**: 入门 | **前置要求**: 了解 Agent 基本概念

Harness 工程（Harness Engineering）是近年来 Agent 领域最重要的范式创新之一。它提出一个简洁而深刻的公式：**智能体 = 大模型 + Harness**。这个公式将 Agent 的能力从"模型能力"解耦为"模型能力 + 运行时能力"两个正交维度，为 Agent 工程化提供了全新的设计空间。

本教程从 Harness 工程的核心模型出发，解析其五大设计原则、三种参考实现，并与传统 Agent 框架进行对比，帮助你建立对 Harness 范式的系统性理解。

---

## 1. Harness 工程范式

### 1.1 核心公式

```
Agent = LLM + Harness
```

其中：

- **LLM**：大语言模型，提供推理、生成、理解等核心认知能力。模型可以替换（GPT、Claude、DeepSeek 等），不改变 Agent 的架构。
- **Harness**：智能体运行时，提供上下文管理、工具调用、安全管控、状态持久化等工程能力。Harness 的设计决定了 Agent 的可靠性、可扩展性和安全性。

### 1.2 为什么需要 Harness

仅靠大模型无法构成生产级 Agent。以下问题必须由 Harness 解决：

| 问题 | 说明 | Harness 的作用 |
|------|------|---------------|
| 上下文管理 | 模型上下文窗口有限，需要裁剪、压缩、摘要 | 提供上下文窗口管理策略 |
| 工具调用 | 模型输出需要转换为实际 API 调用 | 解析工具调用格式，执行并返回结果 |
| 安全控制 | 模型可能被注入恶意指令 | 执行权限检查、行为审计 |
| 状态持久化 | 会话需要跨次恢复 | 保存/恢复会话状态 |
| 可观测性 | 需要了解 Agent 内部决策过程 | 提供日志、追踪、调试接口 |
| 插件扩展 | 功能需要动态扩展 | 插件加载、生命周期管理 |

### 1.3 与 Agent 框架的区别

传统 Agent 框架（如 LangChain、AutoGPT、CrewAI）通常提供"开箱即用"的 Agent 模板，但存在以下问题：

- **高耦合**：Agent 逻辑与框架 API 深度绑定，迁移成本高
- **黑盒化**：框架内部机制复杂，调试困难
- **扩展受限**：自定义行为需要修改框架源码

Harness 工程则相反：

- **轻内核**：Harness 只提供运行时基础设施，不预设 Agent 行为
- **插件化**：所有功能通过插件扩展，Harness 本身保持最小
- **可观测**：每个环节都有明确的接口和日志

---

## 2. Harness 三层模型：H = 〈C, A, R〉

Harness 工程的架构可以用一个三元组精确定义：

```
H = 〈C, A, R〉
```

### 2.1 C — Control Layer（控制层）

控制层是 Harness 的"大脑"，负责：

- **会话管理**：创建、恢复、销毁会话
- **上下文编排**：决定哪些上下文进入模型窗口
- **策略执行**：执行安全策略、审批策略、成本控制策略
- **生命周期管理**：管理插件的加载、启动、停止、卸载

控制层不直接与模型交互，而是通过策略引擎将指令下发给代理层。

### 2.2 A — Agency Layer（代理层）

代理层是 Harness 的"执行体"，负责：

- **模型交互**：发送请求、接收响应、处理流式输出
- **工具调度**：解析模型输出的工具调用，分发到对应工具执行
- **结果整合**：将工具执行结果返回给模型，形成闭环
- **错误处理**：模型调用失败、工具超时、格式异常等

代理层的核心是"推理-行动循环"（Reasoning-Action Loop）：

```
循环: 模型推理 → 输出行动指令 → 执行工具 → 返回结果 → 模型推理...
```

### 2.3 R — Runtime Layer（运行时层）

运行时层是 Harness 的"基础设施"，提供：

- **插件系统**：插件的注册、发现、隔离
- **事件系统**：事件发布/订阅机制
- **存储系统**：会话状态、配置、记忆的持久化
- **安全沙箱**：工具执行的权限隔离
- **可观测性**：日志、指标、追踪

### 2.4 三层交互流程

```
用户输入
    │
    ▼
┌─────────────────────────────────┐
│  Control Layer                  │
│  ├─ 策略检查（权限/安全/成本）    │
│  ├─ 上下文组装（系统提示+记忆）   │
│  └─ 指令下发                    │
└──────────┬──────────────────────┘
           │
           ▼
┌─────────────────────────────────┐
│  Agency Layer                   │
│  ├─ 模型调用（LLM API）          │
│  ├─ 工具调用解析                 │
│  ├─ 工具执行                     │
│  └─ 结果回传                     │
└──────────┬──────────────────────┘
           │
           ▼
┌─────────────────────────────────┐
│  Runtime Layer                  │
│  ├─ 插件服务                     │
│  ├─ 事件总线                     │
│  ├─ 持久化存储                   │
│  └─ 安全沙箱                     │
└─────────────────────────────────┘
```

---

## 3. 五大设计原则

Harness 工程遵循以下五大设计原则，这也是评估一个 Harness 实现是否合格的标准。

### 3.1 模块化（Modularity）

**原则**：Harness 的每个功能都应该是可替换、可组合的模块。

**实践**：
- 插件化架构，所有功能作为插件实现
- 清晰的接口定义，模块间通过接口通信
- 支持运行时动态加载/卸载插件

**反例**：将所有逻辑写在一个巨大的 `AgentExecutor` 类中。

### 3.2 可观测性（Observability）

**原则**：Agent 的每个决策和行动都应该是可追溯、可理解的。

**实践**：
- 完整的日志链路：从用户输入到模型输出，到工具调用结果
- 实时指标：Token 消耗、响应延迟、工具调用频率
- 调试模式：支持逐步执行、断点、变量查看

### 3.3 安全性（Safety）

**原则**：Agent 绝不能执行超出用户授权的操作。

**实践**：
- 最小权限原则：每个工具默认无权限，需显式授权
- 审批流程：敏感操作需要用户确认
- 行为审计：记录所有工具调用，支持事后审计
- 沙箱执行：隔离模型与系统环境

### 3.4 可扩展性（Extensibility）

**原则**：Harness 应能适应未知的未来需求。

**实践**：
- 插件 API：暴露标准化的扩展点
- 工具协议：统一的工具定义和调用方式
- 事件钩子：支持在关键生命周期插入自定义逻辑

### 3.5 可测试性（Testability）

**原则**：Agent 行为应能被自动化测试验证。

**实践**：
- 模拟模式：用模拟模型替换真实模型
- 确定性执行：在相同输入下产生相同输出序列
- 断言工具：验证 Agent 是否调用了预期工具

---

## 4. 三种参考实现

Harness 工程是一个设计范式，而非具体的软件。目前业界有三种重要的参考实现：

### 4.1 Claude Code（Anthropic）

Claude Code 是 Anthropic 推出的终端 AI 编程助手，也是 Harness 工程理念最早的完整实现之一。

| 组件 | 对应实现 |
|------|---------|
| 控制层 | CLAUDE.md 指令系统、settings.json 配置、审批模式 |
| 代理层 | 推理-行动循环、工具调用框架、Skills 系统 |
| 运行时层 | MCP 服务器、会话管理、上下文压缩 |

**特点**：以 CLAUDE.md 为核心的上下文管理，强大的 Skills 插件机制，完善的 MCP 集成。

### 4.2 Codex CLI（OpenAI）

Codex CLI 是 OpenAI 的开源终端 AI 编程助手，用 Rust 实现。

| 组件 | 对应实现 |
|------|---------|
| 控制层 | AGENTS.md 指令系统、审批模式（auto/read-only/full-access） |
| 代理层 | 模型调用、工具执行、管道输入 |
| 运行时层 | 会话管理、多 Agent 工作树 |

**特点**：Rust 实现高性能，三种审批模式精细控制权限，支持图像输入。

### 4.3 OpenClaw（社区）

OpenClaw 是社区驱动的开源 Agent 实现，遵循 Harness 工程原则。

| 组件 | 对应实现 |
|------|---------|
| 控制层 | 安全策略引擎、行为黑名单、技能审计 |
| 代理层 | 推理引擎、技能调度、工具调用 |
| 运行时层 | 三方记忆系统、Brain Git 灾难恢复、插件系统 |

**特点**：零信任安全架构、完整的技能市场、生态丰富。

### 4.4 对比一览

| 特性 | Claude Code | Codex CLI | OpenClaw |
|------|------------|-----------|----------|
| 开源 | 部分开源 | 完全开源 | 完全开源 |
| 实现语言 | TypeScript | Rust | TypeScript |
| 插件机制 | Skills | 待完善 | 技能系统 |
| 安全模型 | 审批模式 | 三模式审批 | 零信任三层防御 |
| 模型支持 | Claude 系列 | GPT 系列 | 多模型 |
| 上下文管理 | CLAUDE.md | AGENTS.md | 记忆系统 |

---

## 5. MiniHarness 简介

MiniHarness 是 [harness_engineering_guide](../../repositories/yeasy_harness_engineering_guide.md) 仓库中的教学项目，用约 500 行代码实现了一个最小化的 Harness 运行时。

### 5.1 核心设计

MiniHarness 展示了 Harness 三层模型的最小实现：

```typescript
// 控制层：策略检查
class ControlLayer {
  async checkPolicies(action: Action): Promise<PolicyResult> {
    for (const policy of this.policies) {
      const result = await policy.evaluate(action);
      if (!result.allowed) return result;
    }
    return { allowed: true };
  }
}

// 代理层：推理-行动循环
class AgencyLayer {
  async runLoop(input: string): Promise<string> {
    while (true) {
      const response = await this.model.generate(input);
      const toolCalls = this.parseToolCalls(response);
      if (toolCalls.length === 0) return response;
      
      for (const call of toolCalls) {
        const result = await this.runtime.executeTool(call);
        input += `\n${call.name} 返回: ${result}`;
      }
    }
  }
}

// 运行时层：工具注册与执行
class RuntimeLayer {
  private tools = new Map<string, Tool>();
  
  registerTool(tool: Tool) {
    this.tools.set(tool.name, tool);
  }
  
  async executeTool(call: ToolCall): Promise<string> {
    const tool = this.tools.get(call.name);
    if (!tool) throw new Error(`Unknown tool: ${call.name}`);
    return tool.execute(call.args);
  }
}
```

### 5.2 学习价值

- 不到 500 行代码，适合阅读和修改
- 完整演示了推理-行动循环的核心机制
- 展示了插件注册、策略检查、工具调用的基础架构
- 可作为开发自定义 Harness 的起点

---

## 6. 为什么 Harness 工程重要

### 6.1 从"调模型"到"构建系统"

传统 Agent 开发聚焦于"调哪个模型"、"怎么写 Prompt"。Harness 工程将注意力转移到"如何构建可靠的 Agent 系统"——这更接近软件工程而非 Prompt 工程。

### 6.2 模型无关性

Harness 将模型视为可替换组件。同一个 Harness 可以用 GPT、Claude、DeepSeek，只需切换模型适配器。这带来了：

- **供应商锁定消除**：随时切换模型供应商
- **成本优化**：复杂任务用强模型，简单任务用轻模型
- **容灾能力**：一个模型故障时自动切换到备用模型

### 6.3 安全与合规

企业级 Agent 部署必须满足安全与合规要求。Harness 工程通过策略层、审计层、沙箱层提供了系统化的安全方案，而非依赖"提示词约束"这种脆弱手段。

### 6.4 可演进性

Agent 需求变化快，Harness 的插件化架构允许在不修改核心的前提下快速扩展功能。新工具、新策略、新记忆机制都可以通过插件引入。

---

## 7. 最佳实践小结

1. **理解三层模型**：控制层管策略、代理层管执行、运行时层管基础设施，各司其职
2. **拥抱插件化**：不要将功能耦合到核心中，通过插件扩展
3. **安全内置**：在设计阶段就考虑安全，而非事后补丁
4. **可观测优先**：先搭建日志和追踪系统，再开发 Agent 逻辑
5. **从 MiniHarness 开始**：学习 Harness 原理的最佳路径是阅读 MiniHarness 源码

---

## 进阶指引

- 下一篇：[DeepSeek Harness 插件开发入门](./02-dsh-plugin-development.md) — 深入 DSH 与 Cordis 插件生态
- 生态仓库：[harness_engineering_guide](../../repositories/yeasy_harness_engineering_guide.md)（MiniHarness 源码）