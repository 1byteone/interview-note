package com.mewcode.agent;

import com.mewcode.tool.Tool;
import com.mewcode.tool.ToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * ReAct Agent 循环 — 实现 Thought → Action → Observation 的迭代执行模式。
 * <p>
 * 核心设计：
 * <ul>
 *   <li><strong>MAX_ITERATIONS = 50</strong>：防止无限循环的安全上限</li>
 *   <li><strong>MAX_CONSECUTIVE_ERRORS = 3</strong>：连续失败达到阈值时提前终止，避免浪费 token</li>
 *   <li><strong>事件驱动</strong>：每个阶段产出 {@link AgentEvent}，由 {@link AgentEventSink} 广播给监听者</li>
 * </ul>
 * <p>
 * 执行流程（每轮迭代）：
 * <ol>
 *   <li><b>Thought</b>：调用 LLM，获取推理结果（决定调用哪个 Tool + 参数）</li>
 *   <li><b>Action</b>：从 LLM 输出中解析工具名和参数，调用 {@link ToolRegistry}</li>
 *   <li><b>Observation</b>：将 Tool 执行结果作为观察值回注到对话历史</li>
 *   <li>重复，直到 LLM 给出最终答案或触发终止条件</li>
 * </ol>
 */
public class AgentLoop {

    private static final Logger log = LoggerFactory.getLogger(AgentLoop.class.class);

    /** 单轮循环最大迭代次数（防止无限循环） */
    public static final int MAX_ITERATIONS = 50;

    /** 连续错误次数达到此阈值时终止循环（防止重复失败浪费资源） */
    public static final int MAX_CONSECUTIVE_ERRORS = 3;

    /** 当前已迭代的轮次计数器 */
    private int iterationCount = 0;

    /** 连续错误计数器（成功执行 Tool 后重置为 0） */
    private int consecutiveErrorCount = 0;

    /** 工具注册表：管理所有可供 Agent 调用的 Tool */
    private final ToolRegistry toolRegistry;

    /** 事件发射器：向外部监听者广播 AgentEvent */
    private final AgentEventSink eventSink;

    /**
     * 构造 ReAct Agent 循环。
     *
     * @param toolRegistry 工具注册表（应预注册所需 Tool）
     * @param eventSink    事件发射器（可注册多个监听者）
     */
    public AgentLoop(ToolRegistry toolRegistry, AgentEventSink eventSink) {
        this.toolRegistry = toolRegistry;
        this.eventSink = eventSink;
    }

    /**
     * 执行单轮 ReAct 迭代（Thought → Action → Observation）。
     * <p>
     * 本方法是循环的最小执行单元，可被外部调度器逐轮调用。
     *
     * @param userQuery      用户原始输入
     * @param conversationContext 当前对话历史（含之前所有 Thought/Action/Observation）
     * @return 本轮迭代后的更新对话上下文；若达到终止条件返回 null
     */
    public String iterate(String userQuery, String conversationContext) {
        iterationCount++;

        // ── 终止条件 1：达到最大迭代次数 ──
        if (iterationCount > MAX_ITERATIONS) {
            log.warn("已达最大迭代次数 {}，终止循环", MAX_ITERATIONS);
            eventSink.emit(new AgentEvent.MaxIterationsReached(
                    System.currentTimeMillis(), iterationCount));
            return null;
        }

        // ── 终止条件 2：连续错误次数超限 ──
        if (consecutiveErrorCount >= MAX_CONSECUTIVE_ERRORS) {
            log.warn("连续错误 {} 次达到阈值，终止循环", consecutiveErrorCount);
            eventSink.emit(new AgentEvent.MaxConsecutiveErrors(
                    System.currentTimeMillis(), consecutiveErrorCount));
            return null;
        }

        // ── Phase 1: Thought（推理） ──
        String prompt = buildPrompt(userQuery, conversationContext);
        eventSink.emit(new AgentEvent.ReasoningStarted(
                System.currentTimeMillis(), prompt));

        // TODO: 替换为真实 LLM 调用（如 OpenAI / Ollama）
        String llmResponse = simulateLLMCall(prompt);
        String thought = extractThought(llmResponse);
        eventSink.emit(new AgentEvent.ReasoningCompleted(
                System.currentTimeMillis(), thought));

        // ── Phase 2: Action（执行工具） ──
        String toolName = extractToolName(llmResponse);
        String toolArgs = extractToolArgs(llmResponse);

        if (toolName == null) {
            // LLM 未选择任何 Tool → 返回最终答案
            log.info("Agent 给出最终答案（未调用 Tool）");
            return llmResponse;
        }

        eventSink.emit(new AgentEvent.ActionPlanned(
                System.currentTimeMillis(), toolName, toolArgs));

        // 在注册表中查找目标 Tool
        Tool tool = toolRegistry.get(toolName).orElse(null);
        if (tool == null) {
            consecutiveErrorCount++;
            String errorResult = "[错误] 未找到工具: " + toolName;
            eventSink.emit(new AgentEvent.ActionCompleted(
                    System.currentTimeMillis(), toolName, errorResult));
            return appendObservation(conversationContext, errorResult);
        }

        // 执行 Tool
        eventSink.emit(new AgentEvent.ActionStarted(
                System.currentTimeMillis(), toolName));
        String toolResult = tool.execute(toolArgs);
        eventSink.emit(new AgentEvent.ActionCompleted(
                System.currentTimeMillis(), toolName, truncate(toolResult, 200)));

        // 连续错误计数器重置（本次执行成功）
        consecutiveErrorCount = 0;

        // ── Phase 3: Observation（回注观察值） ──
        String updatedContext = appendObservation(conversationContext, toolResult);
        eventSink.emit(new AgentEvent.ObservationRecorded(
                System.currentTimeMillis(), truncate(toolResult, 100)));

        return updatedContext;
    }

    /**
     * 重置迭代计数器（通常在开始新的用户会话时调用）。
     */
    public void reset() {
        this.iterationCount = 0;
        this.consecutiveErrorCount = 0;
    }

    /**
     * 获取当前迭代轮次。
     *
     * @return 已执行的迭代次数
     */
    public int getIterationCount() {
        return iterationCount;
    }

    // ─────────────────────── 内部辅助方法 ───────────────────────

    /**
     * 构建发送给 LLM 的完整提示词。
     */
    private String buildPrompt(String userQuery, String context) {
        // 简化示例：实际应使用 ChatPromptTemplate 组装
        return """
                [系统] 你是 ReAct Agent。请按以下格式思考：
                Thought: <你的推理>
                Action: <工具名>
                Action Input: <JSON 参数>
                若已得到最终答案，直接输出 Answer: <答案>

                [用户] %s

                [对话历史] %s
                """.formatted(userQuery, context);
    }

    /**
     * 模拟 LLM 调用（示例桩实现）。
     * 生产环境应替换为真实 LLM API 调用。
     */
    private String simulateLLMCall(String prompt) {
        // 占位实现：返回一条示例 Thought + Action
        return """
                Thought: 用户想查看当前目录文件列表
                Action: bash
                Action Input: {"command": "ls -la"}
                """;
    }

    /** 从 LLM 输出中提取 Thought 文本 */
    private String extractThought(String llmResponse) {
        int idx = llmResponse.indexOf("Thought:");
        if (idx < 0) return llmResponse;
        int end = llmResponse.indexOf("\n", idx);
        return end < 0 ? llmResponse.substring(idx + 8).trim()
                : llmResponse.substring(idx + 8, end).trim();
    }

    /** 从 LLM 输出中提取 Action（工具名），无 Action 返回 null */
    private String extractToolName(String llmResponse) {
        int idx = llmResponse.indexOf("Action:");
        if (idx < 0) return null;
        int start = idx + 7;
        int end = llmResponse.indexOf("\n", start);
        return end < 0 ? llmResponse.substring(start).trim()
                : llmResponse.substring(start, end).trim();
    }

    /** 从 LLM 输出中提取 Action Input（JSON 参数） */
    private String extractToolArgs(String llmResponse) {
        int idx = llmResponse.indexOf("Action Input:");
        if (idx < 0) return "{}";
        int start = idx + 13;
        int end = llmResponse.indexOf("\n", start);
        return end < 0 ? llmResponse.substring(start).trim()
                : llmResponse.substring(start, end).trim();
    }

    /** 将 Observation 追加到对话上下文 */
    private String appendObservation(String context, String observation) {
        return context + "\nObservation: " + observation;
    }

    /** 截断过长文本（用于事件摘要展示） */
    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...";
    }
}
