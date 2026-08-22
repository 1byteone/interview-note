package com.passage.agent;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

/**
 * TitleGeneratorAgent - 标题生成 Agent
 *
 * 职责：根据用户输入的主题/关键词，生成吸引人的文章标题。
 * 工作流位置：StateGraph 第一个节点
 *
 * 使用 Spring AI ChatClient 调用大语言模型生成标题，
 * 通过精心设计的 Prompt 确保生成高质量标题。
 *
 * @author AI-Passage-Creator
 */
@Component
public class TitleGeneratorAgent {

    /** Spring AI ChatClient 实例，用于调用大模型 */
    private final ChatClient chatClient;

    /**
     * 构造方法注入 ChatClient
     *
     * @param chatClientBuilder ChatClient 构建器（Spring 自动配置）
     */
    public TitleGeneratorAgent(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    /**
     * 执行标题生成
     *
     * 读取状态中的 userInput，调用大模型生成标题，
     * 将结果写入状态的 title 字段。
     *
     * @param state 当前工作流状态
     * @return 更新后的状态（包含生成的标题）
     */
    public AgentState execute(AgentState state) {
        // 设置当前步骤标识（用于调试和日志追踪）
        state.setCurrentStep("title-generation");

        try {
            // 构建标题生成 Prompt（系统角色 + 用户输入）
            String prompt = buildPrompt(state.getUserInput());

            // 调用大模型生成标题
            String generatedTitle = chatClient.prompt()
                    .system("你是一位资深内容创作者，擅长撰写吸引人的文章标题。"
                            + "标题应简洁有力，包含关键词，能够吸引读者点击。"
                            + "标题长度控制在 15-30 个字符之间。"
                            + "只输出标题本身，不要输出任何其他内容。")
                    .user(prompt)
                    .call()
                    .content();

            // 清理标题（去除多余空白和引号）
            String cleanTitle = cleanTitle(generatedTitle);

            // 将生成的标题写入状态
            state.setTitle(cleanTitle);

        } catch (Exception e) {
            // 异常处理：记录错误信息到状态
            state.setErrorInfo("标题生成失败: " + e.getMessage());
        }

        return state;
    }

    /**
     * 构建标题生成 Prompt
     *
     * @param userInput 用户输入的主题/关键词
     * @return 完整的用户 Prompt
     */
    private String buildPrompt(String userInput) {
        return "请根据以下主题生成一篇技术文章的标题：\n\n"
                + "主题：" + userInput + "\n\n"
                + "要求：\n"
                + "1. 标题要包含核心关键词\n"
                + "2. 标题要体现技术深度\n"
                + "3. 适当使用数字或问句增加吸引力";
    }

    /**
     * 清理生成的标题
     *
     * @param title 大模型原始输出
     * @return 清理后的标题
     */
    private String cleanTitle(String title) {
        if (title == null) {
            return "未命名文章";
        }
        // 去除首尾空白
        String cleaned = title.trim();
        // 去除可能的引号包裹
        if ((cleaned.startsWith("\"") && cleaned.endsWith("\""))
                || (cleaned.startsWith("'") && cleaned.endsWith("'"))) {
            cleaned = cleaned.substring(1, cleaned.length() - 1);
        }
        return cleaned.trim();
    }
}
