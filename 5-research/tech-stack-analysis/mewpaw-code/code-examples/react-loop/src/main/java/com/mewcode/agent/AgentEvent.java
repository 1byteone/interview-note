package com.mewcode.agent;

/**
 * Agent 事件密封接口 — 定义 ReAct 循环中所有可能产出的事件类型。
 * <p>
 * 使用 Java 17 sealed interface + record，实现编译期穷举检查：
 * <ul>
 *   <li>switch 表达式无需 default 分支，新增事件类型时编译器强制处理</li>
 *   <li>所有事件均为不可变值对象，天然线程安全</li>
 * </ul>
 */
public sealed interface AgentEvent {

    /** 事件产生的时间戳（毫秒），用于日志排序和 UI 时间线展示 */
    long timestamp();

    // ─────────────────── Reasoning 阶段事件 ───────────────────

    /** LLM 开始推理（思考） */
    record ReasoningStarted(long timestamp, String prompt) implements AgentEvent {}

    /** LLM 推理完成，返回思维链文本 */
    record ReasoningCompleted(long timestamp, String thought) implements AgentEvent {}

    // ─────────────────── Action 阶段事件 ───────────────────

    /** Agent 决定调用某个 Tool（toolName + 参数 JSON） */
    record ActionPlanned(long timestamp, String toolName, String argumentsJson) implements AgentEvent {}

    /** Tool 开始执行 */
    record ActionStarted(long timestamp, String toolName) implements AgentEvent {}

    /** Tool 执行完成，返回结果摘要 */
    record ActionCompleted(long timestamp, String toolName, String resultSummary) implements AgentEvent {}

    // ─────────────────── 循环控制事件 ───────────────────

    /** 检测到连续错误达到阈值，循环提前终止 */
    record MaxConsecutiveErrors(long timestamp, int errorCount) implements AgentEvent {}

    /** Agent 达到最大迭代次数，循环正常结束 */
    record MaxIterationsReached(long timestamp, int iterationCount) implements AgentEvent {}

    // ─────────────────── Observation 阶段事件 ───────────────────

    /** Agent 将 Tool 结果回注到上下文，准备下一轮推理 */
    record ObservationRecorded(long timestamp, String observation) implements AgentEvent {}
}
