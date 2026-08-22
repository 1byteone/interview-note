package com.passage.agent;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * ImageAnalyzerAgent - 图片分析 Agent
 *
 * 职责：分析文章内容，识别需要配图的位置，并生成配图描述。
 * 工作流位置：StateGraph 第四个节点（与内容生成并行或在其之后）
 *
 * 此 Agent 不直接生成图片，而是分析文章内容，
 * 输出每个配图位置的描述文本，供后续配图策略使用。
 *
 * @author AI-Passage-Creator
 */
@Component
public class ImageAnalyzerAgent {

    /** Spring AI ChatClient 实例 */
    private final ChatClient chatClient;

    /**
     * 构造方法注入 ChatClient
     *
     * @param chatClientBuilder ChatClient 构建器
     */
    public ImageAnalyzerAgent(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    /**
     * 执行图片分析
     *
     * 分析文章内容，输出配图位置和描述。
     * 结果存储在状态的 metadata 中，格式为：
     * [
     *   {"position": "章节位置", "description": "配图描述", "type": "配图类型"},
     *   ...
     * ]
     *
     * @param state 当前工作流状态（必须包含 content 和 title）
     * @return 更新后的状态（包含配图分析结果）
     */
    public AgentState execute(AgentState state) {
        // 设置当前步骤
        state.setCurrentStep("image-analysis");

        try {
            // 构建图片分析 Prompt
            String prompt = buildPrompt(state.getTitle(), state.getContent());

            // 调用大模型分析配图需求
            String analysisResult = chatClient.prompt()
                    .system("你是一位技术文章配图专家。"
                            + "你的任务是分析文章内容，找出需要配图的位置，"
                            + "并为每个位置生成配图描述。\n"
                            + "配图类型包括：\n"
                            + "- architecture: 架构图/流程图\n"
                            + "- code: 代码截图示意图\n"
                            + "- diagram: 数据结构图\n"
                            + "- illustration: 概念插图\n\n"
                            + "请以 JSON 数组格式输出，每个元素包含：\n"
                            + "position: 配图位置描述\n"
                            + "description: 配图内容描述（用于图片搜索或生成）\n"
                            + "type: 配图类型（architecture/code/diagram/illustration）\n"
                            + "只输出 JSON 数组，不要输出其他内容。")
                    .user(prompt)
                    .call()
                    .content();

            // 解析配图分析结果，存入状态元数据
            List<String> imageDescriptions = parseImageAnalysis(analysisResult);
            state.getMetadata().put("imageDescriptions", imageDescriptions);
            state.getMetadata().put("imageAnalysisRaw", analysisResult);

        } catch (Exception e) {
            // 异常处理：配图分析失败不影响文章生成，记录警告
            state.getMetadata().put("imageAnalysisError", e.getMessage());
        }

        return state;
    }

    /**
     * 构建图片分析 Prompt
     *
     * @param title   文章标题
     * @param content 文章正文
     * @return 完整的用户 Prompt
     */
    private String buildPrompt(String title, String content) {
        return "请分析以下技术文章，找出需要配图的位置：\n\n"
                + "## 文章标题\n" + title + "\n\n"
                + "## 文章内容\n" + content + "\n\n"
                + "请识别 2-4 个最适合配图的位置，为每个位置：\n"
                + "1. 说明配图出现在哪个章节\n"
                + "2. 描述配图应展示的内容\n"
                + "3. 指定配图类型（架构图/代码示意图/数据结构图/概念插图）";
    }

    /**
     * 解析图片分析结果
     *
     * @param rawResult 大模型原始输出（JSON 格式字符串）
     * @return 配图描述列表
     */
    private List<String> parseImageAnalysis(String rawResult) {
        List<String> descriptions = new ArrayList<>();
        if (rawResult == null || rawResult.isEmpty()) {
            return descriptions;
        }
        // 简单解析：按行分割，过滤有效描述
        String[] lines = rawResult.split("\n");
        for (String line : lines) {
            String trimmed = line.trim();
            // 过滤空行和纯标记行
            if (!trimmed.isEmpty()
                    && !trimmed.startsWith("[")
                    && !trimmed.startsWith("]")
                    && !trimmed.equals(",")) {
                descriptions.add(trimmed);
            }
        }
        return descriptions;
    }
}
