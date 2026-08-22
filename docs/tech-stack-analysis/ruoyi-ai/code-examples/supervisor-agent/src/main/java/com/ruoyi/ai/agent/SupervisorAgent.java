package com.ruoyi.ai.agent;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.langchain4j.service.spring.AiService;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * SupervisorAgent - 主调度智能体（Supervisor）
 *
 * Supervisor 多智能体架构的核心：
 *
 * ┌──────────────────────────────────────────────────────────────┐
 * │                    多智能体协作架构图                          │
 * ├──────────────────────────────────────────────────────────────┤
 * │                                                              │
 * │                    ┌─────────────────┐                       │
 * │                    │  User Request   │                       │
 * │                    └────────┬────────┘                       │
 * │                             │                                │
 * │                    ┌────────▼────────┐                       │
 * │                    │ SupervisorAgent │ ← 任务分析与调度中心    │
 * │                    │ （LLM 驱动决策） │                       │
 * │                    └───┬────────┬────┘                       │
 * │                        │        │                            │
 * │            ┌───────────▼──┐  ┌──▼───────────┐              │
 * │            │ SkillsAgent  │  │WebSearchAgent│              │
 * │            │（代码生成专家）│  │（搜索专家）    │              │
 * │            └──────────────┘  └──────────────┘              │
 * │                                                              │
 * └──────────────────────────────────────────────────────────────┘
 *
 * Supervisor 的核心职责：
 * 1. 理解用户意图
 * 2. 决定调用哪个子 Agent
 * 3. 整合子 Agent 的返回结果
 * 4. 生成最终回答
 *
 * 实现方式：
 * 使用 LangChain4j @AiService + System Prompt 驱动 LLM 做任务分类
 * LLM 根据用户问题自动选择调用哪个子 Agent
 */
@Data
@Builder
@Slf4j  // 支持日志输出
@AiService  // LangChain4j 自动实现接口，注入 ChatLanguageModel
public interface SupervisorAgent {

    /**
     * 系统消息 - Supervisor 的核心提示词
     *
     * 提示词工程要点：
     * 1. 明确角色：多智能体调度专家
     * 2. 定义职责：分析意图、分发任务、整合结果
     * 3. 定义子 Agent 能力范围：让 LLM 知道何时调用哪个子 Agent
     * 4. 约束输出格式：确保返回结构化的调度决策
     */
    @SystemMessage("""
            你是一个多智能体调度专家（Supervisor）。
            你的职责是：分析用户意图，决定调用哪个子 Agent，并整合返回结果。

            你可以调度的子 Agent：

            1. SkillsAgent（代码技能专家）
               - 能力：代码生成、代码解释、代码优化、技术方案设计
               - 适用场景：用户问代码相关问题，如"写一个排序算法"、"解释这段代码"

            2. WebSearchAgent（网络搜索助手）
               - 能力：搜索互联网获取最新信息、整理信息摘要
               - 适用场景：用户问事实性问题，如"今天天气"、"最新新闻"

            调度规则：
            - 如果用户问题涉及代码/技术，优先调用 SkillsAgent
            - 如果用户问题涉及实时信息/事实查询，调用 WebSearchAgent
            - 如果问题两者都涉及，先搜索再代码处理
            - 如果无法确定，先尝试回答，必要时请求用户澄清

            请用 JSON 格式返回调度决策：
            {
              "agent": "SkillsAgent 或 WebSearchAgent",
              "reason": "选择原因",
              "task": "传递给子 Agent 的具体任务"
            }
            """)
    String dispatchTask(@UserMessage String userMessage);

    /**
     * 综合处理 - 直接返回最终回答（简化版）
     *
     * 与 dispatchTask 的区别：
     * - dispatchTask 返回调度决策（JSON）
     * - this 方法直接返回最终整合后的回答
     *
     * 使用场景：
     * - 简单场景下无需返回调度过程
     * - 直接将子 Agent 结果整合后返回
     */
    @SystemMessage("""
            你是一个多智能体调度专家。
            根据用户问题，调用合适的子 Agent 获取结果，然后整合成完整回答返回给用户。
            请用自然语言直接回答，不要返回 JSON。
            """)
    String processAndAnswer(@UserMessage String userMessage);
}
