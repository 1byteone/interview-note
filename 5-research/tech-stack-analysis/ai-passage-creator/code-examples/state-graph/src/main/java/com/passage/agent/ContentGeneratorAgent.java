package com.passage.agent;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

/**
 * ContentGeneratorAgent - 内容生成 Agent
 *
 * 职责：根据大纲和标题，生成完整的文章正文内容。
 * 工作流位置：StateGraph 第三个节点（依赖大纲生成完成）
 *
 * 这是最核心的 Agent，负责产出文章的主体内容。
 * 生成内容采用 Markdown 格式，包含代码示例、技术分析等。
 *
 * @author AI-Passage-Creator
 */
@Component
public class ContentGeneratorAgent {

    /** Spring AI ChatClient 实例 */
    private final ChatClient chatClient;

    /**
     * 构造方法注入 ChatClient
     *
     * @param chatClientBuilder ChatClient 构建器
     */
    public ContentGeneratorAgent(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    /**
     * 执行内容生成
     *
     * 读取状态中的 title 和 outline，生成完整的文章正文。
     * 生成的内容为 Markdown 格式，每个章节 200-400 字。
     *
     * @param state 当前工作流状态（必须包含 title 和 outline）
     * @return 更新后的状态（包含生成的文章正文）
     */
    public AgentState execute(AgentState state) {
        // 设置当前步骤
        state.setCurrentStep("content-generation");

        try {
            // 构建内容生成 Prompt
            String prompt = buildPrompt(
                    state.getTitle(),
                    state.getOutline(),
                    state.getUserInput()
            );

            // 调用大模型生成文章内容
            String generatedContent = chatClient.prompt()
                    .system("你是一位资深技术博主，擅长撰写深入浅出的技术文章。"
                            + "写作风格要求：\n"
                            + "1. 语言通俗易懂，但不失技术深度\n"
                            + "2. 适当使用代码示例（Java 语言）\n"
                            + "3. 每个核心概念都配以实际案例说明\n"
                            + "4. 使用 Markdown 格式排版\n"
                            + "5. 文章总字数控制在 1500-3000 字之间\n"
                            + "6. 避免空洞的描述，注重实用性")
                    .user(prompt)
                    .call()
                    .content();

            // 将生成的内容写入状态
            state.setContent(generatedContent);

        } catch (Exception e) {
            // 异常处理
            state.setErrorInfo("内容生成失败: " + e.getMessage());
        }

        return state;
    }

    /**
     * 构建内容生成 Prompt
     *
     * @param title     文章标题
     * @param outline   文章大纲
     * @param userInput 用户原始输入
     * @return 完整的用户 Prompt
     */
    private String buildPrompt(String title, String outline, String userInput) {
        return "请根据以下大纲撰写完整的技术文章：\n\n"
                + "## 文章标题\n" + title + "\n\n"
                + "## 文章大纲\n" + outline + "\n\n"
                + "## 写作要求\n"
                + "1. 严格按照大纲结构展开每个章节\n"
                + "2. 每个章节包含技术原理、代码示例、最佳实践\n"
                + "3. 代码示例使用 Java 语法，并添加中文注释\n"
                + "4. 在关键概念处使用加粗或引用块强调\n"
                + "5. 文章结尾加入总结和延伸阅读建议";
    }
}
