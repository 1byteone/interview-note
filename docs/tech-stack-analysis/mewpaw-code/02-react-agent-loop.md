# 02 · ReAct Agent Loop：CLI 编码 Agent 的决策中枢

> mwpaw-code 的核心引擎是一个自定义的 ReAct Agent Loop，不依赖 LangChain4j 内置的 Agent 循环，而是自己管理迭代控制、事件驱动、错误恢复。本篇从 ReAct 论文出发，深入到项目 AgentLoop 的代码实现。
>
> **对应模块：** `mewcode-core` → `com.mewcode.core.engine` → `AgentLoop.java`

---

## 一、基础概念

### 1.1 什么是 ReAct

**ReAct = Reason + Act**，来自 Yao et al. 2022 年论文《ReAct: Synergizing Reasoning and Acting in Language Models》。

核心思想：LLM 在循环中交替进行"推理（Reasoning）"和"行动（Acting）"，通过观察外部环境的反馈来逐步逼近目标。

与传统方法对比：

| 模式 | 工作方式 | 局限 |
|------|---------|------|
| 纯 LLM（问答） | 用户输入 → LLM → 直接回答 | 知识截止、无法获取实时信息 |
| Chain-of-Thought (CoT) | 用户输入 → LLM 逐步推理 → 回答 | 推理正确但事实可能错误 |
| ReAct | 用户输入 → LLM 推理 → 调用工具 → 观察结果 → 再推理 → ... | 突破知识边界，但需要可靠的工具 |

**ReAct 循环示意图：**

```
用户输入
    │
    ▼
┌──────────────┐     ┌──────────────────┐
│  Thought     │────→│  Action (工具调用) │
│  (推理步骤)   │     │  (bash / read..) │
└──────────────┘     └────────┬─────────┘
         ▲                     │
         │                     ▼
         │              ┌──────────────┐
         └──────────────│  Observation  │
                        │  (工具结果)    │
                        └──────────────┘
                              │
                              ▼ (无工具调用时)
                        ┌──────────────┐
                        │  Final Answer │
                        └──────────────┘
```

### 1.2 为什么项目要自定义 AgentLoop，而不是用 LangChain4j 内置的

| 维度 | LangChain4j 内置循环 | 自定义 AgentLoop |
|------|---------------------|-----------------|
| 迭代控制 | `maxToolCallingRoundTrips` 默认 100 | `MAX_ITERATIONS=50`，且支持连续错误计数 |
| 事件驱动 | 无内置事件机制 | 8 种 AgentEvent 事件，支持流式输出 |
| 安全集成 | 外部包装 | 循环内嵌 SecurityFilterChain |
| 错误恢复 | 抛出异常终止 | 3 次连续错误阈值，工具执行异常回填为消息 |
| 输出截断 | 无 | 成功 5000 chars / 错误 500 chars |

---

## 二、进阶机制

### 2.1 AgentLoop 完整实现

**核心参数：**

```java
// MAX_ITERATIONS：最大循环轮次，防止 Agent 无限循环
// 选型理由：50 次足够完成绝大多数编码任务
// 如果某次任务需要 50+ 次工具调用，说明 LLM 可能陷入"思考迷宫"
private static final int MAX_ITERATIONS = 50;

// MAX_CONSECUTIVE_ERRORS：连续工具执行错误上限
// 超过此值强制终止循环，避免 LLM 在错误的工具调用上反复尝试
private static final int MAX_CONSECUTIVE_ERRORS = 3;

// 成功输出截断：工具执行结果超过 5000 chars 自动截断
// 防止大型输出（如 ls -la 巨量文件）撑爆 LLM 上下文窗口
private static final int MAX_OUTPUT_LENGTH = 5000;

// 错误输出截断：工具执行错误结果超过 500 chars 自动截断
// 错误信息通常比成功信息更简洁，但也不应过长
private static final int MAX_ERROR_OUTPUT_LENGTH = 500;
```

**AgentLoop 核心循环逻辑：**

```java
package com.mewcode.core.engine;

import java.util.ArrayList;
import java.util.List;

/**
 * ReAct Agent 循环引擎
 * 实现 Thought → Action → Observation 循环，直到 LLM 不再需要工具调用
 */
public class AgentLoop {

    // 最大迭代次数
    private static final int MAX_ITERATIONS = 50;
    // 最大连续错误次数
    private static final int MAX_CONSECUTIVE_ERRORS = 3;
    // 成功输出截断长度
    private static final int MAX_OUTPUT_LENGTH = 5000;
    // 错误输出截断长度
    private static final int MAX_ERROR_OUTPUT_LENGTH = 500;

    // LLM 提供者（封装 LangChain4j 的 ChatLanguageModel 调用）
    private final LlmProvider llmProvider;
    // 工具注册中心（存储 6 种内置工具的描述和执行器）
    private final ToolRegistry toolRegistry;
    // 安全过滤器链（5 层责任链）
    private final SecurityFilterChain securityChain;
    // 事件发射器（推送 AgentEvent 事件）
    private final AgentEventSink eventSink;

    // 构造器注入
    public AgentLoop(LlmProvider llmProvider, ToolRegistry toolRegistry,
                     SecurityFilterChain securityChain, AgentEventSink eventSink) {
        this.llmProvider = llmProvider;
        this.toolRegistry = toolRegistry;
        this.securityChain = securityChain;
        this.eventSink = eventSink;
    }

    /**
     * 执行 Agent 循环
     * @param systemPrompt 系统提示词（包含工具描述、安全规则等）
     * @param userMessage 用户输入消息
     * @return Agent 最终回复
     */
    public String execute(String systemPrompt, String userMessage) {
        // ① 构建消息列表：SystemMessage + UserMessage
        // SystemMessage 包含：工具描述、安全规则、角色定义
        // UserMessage 包含：用户的具体问题或指令
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new SystemMessage(systemPrompt));
        messages.add(new UserMessage(userMessage));

        // ② 初始化迭代计数器和错误计数器
        int iteration = 0;
        int consecutiveErrors = 0;

        // 发送 TurnStarted 事件
        eventSink.emit(new TurnStarted(turnId()));

        // ③ ReAct 主循环：迭代直到 LLM 不再调用工具或达到上限
        while (iteration < MAX_ITERATIONS) {
            iteration++;

            // 发送 StepUpdated 事件
            eventSink.emit(new StepUpdated(turnId(), iteration));

            // ④ 调用 LLM：传递完整消息列表，获得 AI 回复
            // AiMessage 可能包含 ToolExecutionRequests（工具调用请求）
            // 也可能只是一个文本回复（最终回答）
            AiMessage aiResponse = llmProvider.chat(messages);

            // ⑤ 判断 LLM 是否还需要调用工具
            // 如果 AiMessage 没有 toolExecutionRequests，说明 LLM 选择直接回答
            // 此时应终止循环，返回最终回答
            if (!aiResponse.hasToolExecutionRequests()) {
                // 无需工具调用 → 终止循环，返回最终回复
                eventSink.emit(new AssistantCompleted(turnId(), aiResponse.text()));
                eventSink.emit(new TurnCompleted(turnId()));
                return aiResponse.text();
            }

            // ⑥ 有工具调用 → 遍历执行每个工具请求
            // 注意：一次 LLM 回复可能包含多个并行工具调用请求
            List<ToolExecutionRequest> requests = aiResponse.toolExecutionRequests();

            // ⑦ 发送工具开始事件
            eventSink.emit(new ToolCallStarted(turnId(), requests));

            // ⑧ 逐个执行工具调用
            for (ToolExecutionRequest request : requests) {
                try {
                    // 安全链检查：5 层过滤
                    // 任何一层 deny 都会阻断工具执行
                    SecurityResult securityResult = securityChain.check(request);

                    if (!securityResult.allowed()) {
                        // 安全链拒绝：记录拒绝原因并回填给 LLM
                        // LLM 可以据此调整行为，不会直接崩溃
                        String errorMsg = "Security check failed: " + securityResult.reason();
                        messages.add(new ToolExecutionResultMessage(request.id(),
                                request.name(), errorMsg));
                        consecutiveErrors++;
                        continue;
                    }

                    // 执行工具：从 ToolRegistry 获取执行器并调用
                    // ToolDescriptor 包含 name / description / parameters / dangerous / version
                    ToolDescriptor descriptor = toolRegistry.getDescriptor(request.name());
                    ToolExecutor executor = toolRegistry.getExecutor(request.name());

                    // 执行工具，传入参数（JSON 字符串形式的参数）
                    String result = executor.execute(descriptor, request.arguments());

                    // 输出截断：防止工具输出撑爆上下文
                    if (result.length() > MAX_OUTPUT_LENGTH) {
                        result = result.substring(0, MAX_OUTPUT_LENGTH)
                                + "\n...(truncated, " + result.length() + " chars total)";
                    }

                    // 将工具执行结果包装为 ToolExecutionResultMessage 追加到消息列表
                    messages.add(ToolExecutionResultMessage.from(request, result));

                    // 发送工具完成事件
                    eventSink.emit(new ToolCallCompleted(turnId(), request.name(), result));

                    // 连续错误计数器归零（成功执行一次）
                    consecutiveErrors = 0;

                } catch (Exception e) {
                    // 工具执行异常：捕获异常并回填给 LLM
                    // 与安全链拒绝一样，LLM 可以自行处理
                    String errorMsg = e.getMessage();
                    if (errorMsg != null && errorMsg.length() > MAX_ERROR_OUTPUT_LENGTH) {
                        errorMsg = errorMsg.substring(0, MAX_ERROR_OUTPUT_LENGTH)
                                + "\n...(truncated)";
                    }
                    messages.add(ToolExecutionResultMessage.from(request, errorMsg));
                    consecutiveErrors++;
                }
            }

            // ⑨ 连续错误检测：超过阈值强制终止
            if (consecutiveErrors >= MAX_CONSECUTIVE_ERRORS) {
                // 连续错误超过上限，强制终止循环
                String errorMsg = "Agent terminated: " + MAX_CONSECUTIVE_ERRORS
                        + " consecutive errors exceeded";
                eventSink.emit(new TurnFailed(turnId(), errorMsg));
                return errorMsg;
            }
        }

        // ⑩ 达到最大迭代次数：强制终止
        String timeoutMsg = "Agent terminated: max iterations (" + MAX_ITERATIONS + ") reached";
        eventSink.emit(new TurnFailed(turnId(), timeoutMsg));
        return timeoutMsg;
    }
}
```

**逐行注释：**

```java
// 行 12-13：MAX_ITERATIONS = 50
// 为什么是 50 不是 10 或 100？
// - 10 次对复杂编码任务不够（可能需要多次 read → edit → bash 验证）
// - 100 次太多，容易让 Agent 无限循环、浪费 LLM 调用成本
// - 50 是经验值，平衡了"完成复杂任务"和"防止失控"

// 行 15-16：MAX_CONSECUTIVE_ERRORS = 3
// 持续 3 次错误说明 LLM 在当前状态下无法正确使用工具
// 继续下去只会浪费 token，不如终止让用户重新输入

// 行 78：aiResponse.hasToolExecutionRequests()
// 这是 LangChain4j 的 AiMessage 方法
// 如果 LLM 认为需要调用工具，会在回复中附带 toolExecutionRequests
// 如果 LLM 认为可以直接回答，就不带工具请求

// 行 108-112：输出截断逻辑
// 工具执行结果可能非常庞大（如 cat 一个 10MB 的日志文件）
// 5000 chars 是经验值，足以让 LLM 理解结果内容
// 截断后附上原始长度，LLM 能感知到信息被截断

// 行 140：consecutiveErrors = 0
// 只要有一次成功，连续错误计数器就归零
// 这样 Agent 不会因为"历史错误"被迫终止
```

### 2.2 AgentEvent 事件驱动架构

**AgentEvent sealed interface 定义：**

```java
// sealed interface：8 种事件类型覆盖 Agent 完整生命周期
// 编译器保证穷尽性检查，新增事件类型必须同步更新所有 switch 分支
public sealed interface AgentEvent
        permits TurnStarted,          // 一轮对话开始
                StepUpdated,          // ReAct 循环步骤更新
                AssistantDelta,       // LLM 流式增量输出（逐字推送）
                AssistantCompleted,   // LLM 回复完成
                ToolCallStarted,      // 工具调用开始
                ToolOutputDelta,      // 工具输出流式增量
                ToolCallCompleted,    // 工具调用完成
                TurnCompleted,        // 一轮对话正常完成
                TurnFailed {          // 一轮对话失败
    // 所有事件必须提供 turnId，用于关联事件序列
    String turnId();
}

// 具体事件实现（Record 形式，不可变）

// 一轮对话开始：携带初始消息
record TurnStarted(String turnId, String initialMessage) implements AgentEvent {}

// 步骤更新：携带当前迭代次数
record StepUpdated(String turnId, int iteration) implements AgentEvent {}

// LLM 流式增量：携带当前已生成的文本片段
record AssistantDelta(String turnId, String delta) implements AgentEvent {}

// LLM 回复完成：携带完整回复文本
record AssistantCompleted(String turnId, String text) implements AgentEvent {}

// 工具调用开始：携带本次调用的所有工具请求
record ToolCallStarted(String turnId, List<ToolExecutionRequest> requests) implements AgentEvent {}

// 工具输出流式增量：携带工具输出的文本片段
record ToolOutputDelta(String turnId, String toolName, String delta) implements AgentEvent {}

// 工具调用完成：携带工具名称和完整结果
record ToolCallCompleted(String turnId, String toolName, String result) implements AgentEvent {}

// 一轮对话正常完成
record TurnCompleted(String turnId) implements AgentEvent {}

// 一轮对话失败：携带失败原因
record TurnFailed(String turnId, String reason) implements AgentEvent {}
```

**事件流时序：**

```
TurnStarted
    │
    ▼
StepUpdated(iter=1)
    │
    ├─ AssistantDelta (逐字推送 LLM 的 Thought)
    │  ...
    ├─ AssistantCompleted
    │
    ├─ ToolCallStarted (LLM 决定调用工具)
    │  ├─ ToolOutputDelta (工具输出流式推送)
    │  └─ ToolCallCompleted
    │
    ▼
StepUpdated(iter=2)
    │
    ├─ AssistantDelta
    ├─ AssistantCompleted (LLM 已获得足够信息，直接回答)
    ├─ (无工具调用)
    │
    ▼
TurnCompleted
```

### 2.3 ReAct vs Plan-and-Execute

| 维度 | ReAct (本项目使用) | Plan-and-Execute |
|------|-------------------|-----------------|
| 工作方式 | 边想边做，观察结果后调整下一步 | 先制定完整计划，再逐步执行 |
| 灵活性 | 高，可根据中间结果动态调整 | 中，计划变更需重新规划 |
| 可预测性 | 低，执行路径不确定 | 高，执行路径预先可知 |
| 适用场景 | 探索性任务（写代码、调试） | 确定性任务（数据批处理、ETL） |
| LLM 调用次数 | 较多（每次 Action 后都调用 LLM） | 较少（计划阶段 + 执行阶段） |
| 错误恢复 | 自然恢复（观察错误后调整行动） | 需要重新计划或人力介入 |

**为什么本项目选 ReAct？** 编码任务是典型的探索性工作——你可能不知道最终代码长什么样，需要边写边看结果、边调整。Plan-and-Execute 更适合"我知道要做什么，只是需要按步执行"的场景。

---

## 三、面试题

**Q1：ReAct Agent 循环的核心逻辑是什么？与 Plan-and-Execute 有什么区别？**

A：ReAct 的核心是 Thought → Action → Observation 循环：LLM 推理出当前需要做什么（Thought），调用工具执行（Action），观察工具结果（Observation），然后进入下一轮推理。ReAct 是"边想边做"，Plan-and-Execute 是"先计划后执行"。编码任务适合 ReAct，因为你需要不断看代码、改代码、验证结果，路径是动态的。

**Q2：为什么 MAX_ITERATIONS 设为 50，MAX_CONSECUTIVE_ERRORS 设为 3？**

A：50 次迭代足够完成大多数编码任务（创建文件、读代码、执行命令、调试）。如果超过 50 次，通常意味着 LLM 陷入了"思考迷宫"，继续执行只会浪费 token。3 次连续错误意味着 LLM 连续 3 次调用工具都失败了，说明它可能不理解工具的正确用法，继续下去没有意义。

**Q3：项目为什么没有直接用 LangChain4j 内置的 Agent 循环，而是自己实现了一个？**

A：LangChain4j 内置的 `maxToolCallingRoundTrips` 只控制轮次上限，不支持连续错误检测、输出截断、事件驱动。项目需要 5 层安全链在循环内检查每次工具调用，需要事件流推送 8 种事件给前端展示，需要自定义的成功/错误输出截断策略。这些需求 LangChain4j 内置循环无法满足。

**Q4：事件驱动架构在 AgentLoop 中扮演什么角色？**

A：事件驱动让 AgentLoop 的执行过程可观测。8 种事件从 TurnStarted 到 TurnFailed 覆盖了完整生命周期，每个事件都携带 turnId 用于关联。前端/REPL 可以订阅事件流，实现"实时展示 Agent 思考过程"的效果，而不是等待最终结果。

**Q5：工具执行结果为什么要截断？截断长度如何选择？**

A：LLM 的上下文窗口有限（通常是 4K-128K tokens），工具输出可能非常大（如 `ls -la` 遍历整个项目目录）。截断保证 LLM 不会因为输出过长而丢失上下文的关键信息。5000 chars 是经验值——足以让 LLM 理解输出内容，又不至于撑爆上下文。错误截断 500 chars 是因为错误信息通常较短，太长也无意义。

---

## 四、总结

| 设计点 | 实现 | 价值 |
|--------|------|------|
| 迭代上限 | MAX_ITERATIONS=50 | 防止无限循环，控制成本 |
| 错误容错 | MAX_CONSECUTIVE_ERRORS=3 | 避免无效重复尝试 |
| 输出截断 | 5000/500 chars | 避免上下文撑爆 |
| 事件驱动 | 8 种 AgentEvent | 全生命周期可观测 |
| 安全集成 | 内嵌 SecurityFilterChain | 每次工具调用都经过安全链 |
| 错误恢复 | 回填为 ToolExecutionResultMessage | 让 LLM 自己处理错误 |

**核心收获：** AgentLoop 的核心设计哲学是"可控的自主性"——既给 LLM 足够的自由度（完整 ReAct 循环），又通过迭代上限、错误计数、输出截断、安全链等机制确保系统不会失控。

---

## 参考资料

- ReAct 论文：Yao et al., "ReAct: Synergizing Reasoning and Acting in Language Models", 2022
- LangChain4j Agent docs: https://docs.langchain4j.dev/tutorials/agents
- Java 21 Sealed Classes: https://openjdk.org/jeps/409