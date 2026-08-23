package com.ruoyi.ai.agent;

/**
 * AgentTool - 智能体工具接口
 *
 * 在 LangChain4j 中，Tool 是 Agent 与外部世界交互的能力单元：
 *
 * 工具模式架构图：
 *
 *   ┌─────────────────────────────────────────────────────┐
 *   │              Supervisor Agent                       │
 *   │   （接收任务 → 分析 → 选择子 Agent 或工具）          │
 *   └─────────────┬──────────────────────┬───────────────┘
 *                 │                      │
 *        ┌────────▼────────┐   ┌────────▼────────┐
 *        │  Skills Agent   │   │ WebSearch Agent  │
 *        │ （代码生成工具）  │   │ （网络搜索工具）   │
 *   ┌────┴────┐  ┌────┴────┐  ┌────┴────┐  ┌────┴────┐
 *   │Tool 接口│  │Tool 接口│  │Tool 接口│  │Tool 接口│
 *   └─────────┘  └─────────┘  └─────────┘  └─────────┘
 *
 * 每个 Tool 需要定义：
 * 1. name：工具名称（LLM 通过此名称调用）
 * 2. description：工具描述（LLM 用来判断何时使用此工具）
 * 3. execute：具体执行逻辑
 *
 * LangChain4j @Tool 注解方式：
 * 用 @Tool 标注 public 方法，框架自动注册为可调用工具
 */
public interface AgentTool {

    /**
     * 获取工具名称
     *
     * 名称要求：
     * - 使用小写字母和下划线（如 web_search、code_generate）
     * - 简洁且能描述工具功能
     * - LLM 通过此名称选择调用哪个工具
     *
     * @return 工具唯一标识名称
     */
    String getName();

    /**
     * 获取工具功能描述
     *
     * 描述是 LLM 决策的关键依据：
     * - 应清晰说明工具能做什么、输入输出格式
     * - 描述越准确，LLM 调用越精准
     * - 例如："搜索互联网获取最新信息，返回相关网页内容摘要"
     *
     * @return 工具功能的自然语言描述
     */
    String getDescription();

    /**
     * 执行工具逻辑
     *
     * 参数说明：
     * - input：用户输入或上游 Agent 传递的任务描述
     * - 执行结果会作为 Tool 执行结果反馈给 LLM
     *
     * @param input 工具输入参数（任务描述）
     * @return 工具执行结果（会被反馈给 LLM）
     */
    String execute(String input);
}
