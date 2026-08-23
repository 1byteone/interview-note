package com.passage.agent;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * ContentMergerAgent - 内容合并 Agent
 *
 * 职责：将标题、正文、配图描述合并为最终文章。
 * 工作流位置：StateGraph 最后一个节点（汇聚节点）
 *
 * 此 Agent 负责：
 * 1. 组装标题和正文
 * 2. 在合适位置插入配图占位符
 * 3. 添加文章元信息（标签、摘要等）
 * 4. 输出最终的完整文章
 *
 * @author AI-Passage-Creator
 */
@Component
public class ContentMergerAgent {

    /**
     * 执行内容合并
     *
     * 将各个 Agent 的输出合并为最终文章。
     * 文章格式：
     * ```
     * [标题]
     *
     * [正文内容]
     *
     * [配图占位符（如有）]
     *
     * [文章标签]
     * ```
     *
     * @param state 完整的工作流状态（包含所有前置 Agent 的输出）
     * @return 更新后的状态（包含 finalContent）
     */
    public AgentState execute(AgentState state) {
        // 设置当前步骤
        state.setCurrentStep("content-merge");

        try {
            // 构建最终文章内容
            StringBuilder finalArticle = new StringBuilder();

            // 1. 添加文章标题（一级标题）
            if (state.getTitle() != null && !state.getTitle().isEmpty()) {
                finalArticle.append("# ").append(state.getTitle()).append("\n\n");
            }

            // 2. 添加文章正文
            if (state.getContent() != null && !state.getContent().isEmpty()) {
                finalArticle.append(state.getContent()).append("\n\n");
            }

            // 3. 插入配图占位符（如果有配图描述）
            @SuppressWarnings("unchecked")
            List<String> imageDescriptions = (List<String>) state.getMetadata().get("imageDescriptions");
            if (imageDescriptions != null && !imageDescriptions.isEmpty()) {
                finalArticle.append("---\n\n");
                finalArticle.append("## 配图说明\n\n");
                for (int i = 0; i < imageDescriptions.size(); i++) {
                    finalArticle.append("![配图").append(i + 1).append("](")
                            .append("PLACEHOLDER_IMAGE_").append(i + 1).append(")")
                            .append(" <!-- ").append(imageDescriptions.get(i)).append(" -->\n\n");
                }
            }

            // 4. 添加文章标签
            if (state.getUserInput() != null) {
                finalArticle.append("---\n\n");
                finalArticle.append("**标签**：").append(state.getUserInput()).append("\n");
            }

            // 将最终文章写入状态
            state.setFinalContent(finalArticle.toString());

        } catch (Exception e) {
            // 异常处理
            state.setErrorInfo("内容合并失败: " + e.getMessage());
        }

        return state;
    }
}
