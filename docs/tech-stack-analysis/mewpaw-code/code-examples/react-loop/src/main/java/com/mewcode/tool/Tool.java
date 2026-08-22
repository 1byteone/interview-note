package com.mewcode.tool;

/**
 * Tool 接口 — 所有 Agent 可调用工具的统一契约。
 * <p>
 * 每个 Tool 实现必须提供：
 * <ul>
 *   <li>{@link #name()} — 唯一标识符（如 "bash"、"read_file"）</li>
 *   <li>{@link #description()} — 供 LLM 理解工具用途的自然语言描述</li>
 *   <li>{@link #execute(String)} — 接收 JSON 参数字符串，返回执行结果</li>
 * </ul>
 */
public interface Tool {

    /**
     * 工具的唯一名称（kebab-case 或 snake_case 均可）。
     *
     * @return 工具名，如 "bash"
     */
    String name();

    /**
     * 工具的功能描述（会注入到 LLM 的 system prompt 中）。
     *
     * @return 自然语言描述
     */
    String description();

    /**
     * 执行工具逻辑。
     *
     * @param argsJson JSON 格式的调用参数
     * @return 执行结果文本（成功或错误信息）
     */
    String execute(String argsJson);
}
