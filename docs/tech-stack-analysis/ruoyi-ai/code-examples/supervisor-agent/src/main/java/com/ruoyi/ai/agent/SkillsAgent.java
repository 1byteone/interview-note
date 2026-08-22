package com.ruoyi.ai.agent;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.langchain4j.service.spring.AiService;
import lombok.Builder;
import lombok.Data;

/**
 * SkillsAgent - 技能子智能体
 *
 * 在 Supervisor 多智能体架构中，SkillsAgent 负责：
 * - 代码生成与解释
 * - 技术方案设计
 * - 代码审查与优化
 *
 * 架构位置：
 *
 *   User Request
 *        │
 *        ▼
 *   SupervisorAgent ──→ 判断是代码相关任务 ──→ SkillsAgent
 *        │                                           │
 *        │                                     执行代码技能
 *        │                                           │
 *        ◄─────────────── 返回结果 ◄─────────────────┘
 *
 * LangChain4j @AiService 注解：
 * 声明式定义 Agent，框架自动生成实现类
 * - @SystemMessage：定义 Agent 的角色和行为约束
 * - 方法参数通过 @V 注解绑定到模板变量
 */
@Data
@Builder
@AiService  // LangChain4j 自动创建此接口的实现类，注入 ChatLanguageModel
public interface SkillsAgent {

    /**
     * 系统消息 - 定义 SkillsAgent 的角色身份和行为准则
     *
     * @SystemMessage 内容会被作为 System Prompt 发送给 LLM：
     * - 第一行：定义角色（"你是一个代码技能专家"）
     * - 后续行：约束行为规范（"只回答代码相关问题"）
     * - 使用中文是因为目标用户群体是中文开发者
     */
    @SystemMessage("""
            你是一个专业的代码技能专家。
            你的职责是：
            1. 根据用户需求生成高质量代码
            2. 解释代码逻辑和设计意图
            3. 提供代码优化建议

            请用中文回答，代码部分可以使用英文注释。
            """)
    String chatWithCodeExpert(@UserMessage String userMessage);
}
