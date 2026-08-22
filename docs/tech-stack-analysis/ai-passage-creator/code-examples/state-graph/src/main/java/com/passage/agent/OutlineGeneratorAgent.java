package com.passage.agent;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

/**
 * OutlineGeneratorAgent - 大纲生成 Agent
 *
 * 职责：根据标题和用户输入，生成结构化的文章大纲。
 * 工作流位置：StateGraph 第二个节点（依赖标题生成完成）
 *
 * 生成的大纲采用 Markdown 层级结构，
 * 为后续的内容生成 Agent 提供清晰的写作框架。
 *
 * @author AI-Passage-Creator
 */
@Component
public class OutlineGeneratorAgent {

    /** Spring AI ChatClient 实例 */
    private final ChatClient chatClient;

    /**
     * 构造方法注入 ChatClient
     *
     * @param chatClientBuilder ChatClient 构建器
     */
    public OutlineGeneratorAgent(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    /**
     * 执行大纲生成
     *
     * 读取状态中的 title 和 userInput，生成 Markdown 格式大纲。
     * 大纲结构：一级标题 -> 二级标题 -> 三级标题
     *
     * @param state 当前工作流状态（必须包含 title 字段）
     * @return 更新后的状态（包含生成的大纲）
     */
    public AgentState execute(AgentState state) {
        // 设置当前步骤
        state.setCurrentStep("outline-generation");

        try {
            // 构建大纲生成 Prompt
            String prompt = buildPrompt(state.getTitle(), state.getUserInput());

            // 调用大模型生成大纲
            String generatedOutline = chatClient.prompt()
                    .system("你是一位技术写作专家，擅长构建文章大纲。"
                            + "大纲应具有清晰的层次结构，覆盖主题的核心知识点。"
                            + "使用 Markdown 格式输出，包含 2-4 个主要章节，"
                            + "每个章节下包含 2-3 个子主题。"
                            + "大纲应该逻辑清晰、由浅入深。")
                    .user(prompt)
                    .call()
                    .content();

            // 将生成的大纲写入状态
            state.setOutline(generatedOutline);

        } catch (Exception e) {
            // 异常处理
            state.setErrorInfo("大纲生成失败: " + e.getMessage());
        }

        return state;
    }

    /**
     * 构建大纲生成 Prompt
     *
     * @param title     文章标题
     * @param userInput 用户原始输入
     * @return 完整的用户 Prompt
     */
    private String buildPrompt(String title, String userInput) {
        return "请根据以下信息生成文章大纲：\n\n"
                + "标题：" + title + "\n"
                + "主题/关键词：" + userInput + "\n\n"
                + "要求：\n"
                + "1. 使用 Markdown 标题语法（##、###）\n"
                + "2. 大纲包含 3-5 个主要章节\n"
                + "3. 每个章节下有 2-3 个子主题\n"
                + "4. 结构由浅入深，先概念后实践\n"
                + "5. 适当加入代码示例和最佳实践章节";
    }
}
