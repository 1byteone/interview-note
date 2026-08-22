package com.ruoyi.ai.agent;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.spring.AiService;
import lombok.Builder;
import lombok.Data;

/**
 * WebSearchAgent - 网络搜索子智能体
 *
 * 在 Supervisor 多智能体架构中，WebSearchAgent 负责：
 * - 搜索互联网获取最新信息
 * - 抓取网页内容并提取关键信息
 * - 整理搜索结果为结构化摘要
 *
 * 架构位置：
 *
 *   User Request（"今天天气怎么样？"）
 *        │
 *        ▼
 *   SupervisorAgent ──→ 判断是信息查询 ──→ WebSearchAgent
 *        │                                          │
 *        │                                   执行网络搜索
 *        │                                          │
 *        ◄────────────── 返回摘要 ◄─────────────────┘
 *
 * 注意：
 * WebSearchAgent 需要配合外部搜索工具（如 Tavily、SerpAPI）使用
 * 本示例简化了搜索逻辑，展示 Agent 协作模式
 */
@Data
@Builder
@AiService  // LangChain4j 自动生成接口实现类
public interface WebSearchAgent {

    /**
     * 系统消息 - 定义 WebSearchAgent 的角色和行为规范
     *
     * 要点：
     * - 明确角色为"网络搜索助手"
     * - 强调信息准确性和时效性
     * - 要求标注信息来源
     */
    @SystemMessage("""
            你是一个网络搜索助手。
            你的职责是：
            1. 根据用户问题搜索互联网获取最新信息
            2. 提取关键信息并整理成结构化摘要
            3. 标注信息来源和时间

            请优先提供最新、准确的信息。
            如果无法确定信息准确性，请明确标注。
            """)
    String chatWithSearchAssistant(@UserMessage String userMessage);
}
