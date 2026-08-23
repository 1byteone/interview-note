package com.zznursing.ai;

import org.springframework.stereotype.Component;

/**
 * 养老场景Prompt构建器
 *
 * 功能说明：
 * 1. 构建智慧养老场景的系统提示词
 * 2. 根据不同场景生成专属prompt
 * 3. 管理prompt模板和变量
 *
 * 使用场景：
 * - 为AI注入养老领域专业知识
 * - 引导AI以专业、温暖的方式与老人对话
 * - 确保AI回答符合养老护理规范
 *
 * @author zznursing
 * @since 1.0.0
 */
@Component
public class ElderlyCarePromptBuilder {

    /**
     * 系统角色定义
     * 定义AI助手在养老场景中的角色和行为规范
     */
    private static final String SYSTEM_ROLE = """
            你是"智慧养老助手"，一位专业的养老护理顾问和健康咨询师。
            你的职责是为老年人及其家属提供专业的养老护理建议和健康咨询。

            你的特点：
            1. 说话温和、耐心，语速适中，用词通俗易懂
            2. 关心老人的身心健康，态度亲切如家人
            3. 具备专业的护理知识，能提供科学的建议
            4. 能够理解老人的表达习惯，包容口语化表述
            5. 遇到紧急情况时，能准确判断并给出正确指导
            """;

    /**
     * 健康咨询提示词模板
     */
    private static final String HEALTH_CONSULTATION_TEMPLATE = """
            ## 健康咨询场景

            当前老人信息：
            - 年龄：{age}岁
            - 性别：{gender}
            - 已知健康问题：{healthIssues}

            请根据以上信息，提供专业的健康建议。注意：
            1. 建议要具体、可操作
            2. 避免使用专业术语，用通俗语言解释
            3. 如果涉及用药，提醒遵医嘱
            4. 对于严重症状，建议及时就医
            """;

    /**
     * 饮食营养提示词模板
     */
    private static final String DIETARY_NUTRITION_TEMPLATE = """
            ## 饮食营养建议场景

            当前老人信息：
            - 年龄：{age}岁
            - 慢性病：{chronicDiseases}
            - 饮食禁忌：{dietaryRestrictions}

            请提供适合老人的饮食建议。注意：
            1. 考虑慢性病的饮食要求（如糖尿病低糖、高血压低盐）
            2. 推荐易消化、营养均衡的食物
            3. 建议要具体到菜品和份量
            4. 提醒老人注意进食时间和方式
            """;

    /**
     * 运动康复提示词模板
     */
    private static final String EXERCISE_REHABILITATION_TEMPLATE = """
            ## 运动康复建议场景

            当前老人信息：
            - 年龄：{age}岁
            - 身体状况：{physicalCondition}
            - 运动能力：{mobilityLevel}

            请推荐适合老人的运动方式。注意：
            1. 运动强度要适中，循序渐进
            2. 推荐安全、易执行的运动（如太极、散步、简单拉伸）
            3. 提醒运动前热身、运动后放松
            4. 提醒注意运动时间和频率
            5. 对于行动不便的老人，推荐坐姿运动
            """;

    /**
     * 用药提醒提示词模板
     */
    private static final String MEDICATION_REMINDER_TEMPLATE = """
            ## 用药提醒场景

            当前老人用药信息：
            - 用药清单：{medicationList}
            - 用药时间：{medicationSchedule}

            请帮助老人正确用药。注意：
            1. 明确每种药的服用时间和剂量
            2. 提醒药物的注意事项（如饭前/饭后服用）
            3. 提醒可能的药物相互作用
            4. 强调不要自行增减药量
            5. 如有不适，建议及时咨询医生
            """;

    /**
     * 情感陪伴提示词模板
     */
    private static final String EMOTIONAL_COMPANION_TEMPLATE = """
            ## 情感陪伴场景

            老人当前状态：
            - 情绪状态：{moodState}
            - 兴趣爱好：{hobbies}

            请以温暖、理解的方式陪伴老人聊天。注意：
            1. 多倾听，少说教
            2. 关心老人的感受，给予肯定和鼓励
            3. 可以聊聊老人感兴趣的话题
            4. 如果老人情绪低落，给予安慰和陪伴
            5. 分享一些积极向上的故事或趣事
            """;

    /**
     * 构建默认系统提示词
     *
     * @return 系统提示词
     */
    public String buildSystemPrompt() {
        return SYSTEM_ROLE;
    }

    /**
     * 构建健康咨询提示词
     *
     * @param age 年龄
     * @param gender 性别
     * @param healthIssues 健康问题
     * @return 完整的系统提示词
     */
    public String buildHealthConsultationPrompt(String age, String gender, String healthIssues) {
        String scenePrompt = HEALTH_CONSULTATION_TEMPLATE
                .replace("{age}", age)
                .replace("{gender}", gender)
                .replace("{healthIssues}", healthIssues != null ? healthIssues : "无");

        return SYSTEM_ROLE + "\n\n" + scenePrompt;
    }

    /**
     * 构建饮食营养提示词
     *
     * @param age 年龄
     * @param chronicDiseases 慢性病
     * @param dietaryRestrictions 饮食禁忌
     * @return 完整的系统提示词
     */
    public String buildDietaryNutritionPrompt(String age, String chronicDiseases,
                                               String dietaryRestrictions) {
        String scenePrompt = DIETARY_NUTRITION_TEMPLATE
                .replace("{age}", age)
                .replace("{chronicDiseases}", chronicDiseases != null ? chronicDiseases : "无")
                .replace("{dietaryRestrictions}", dietaryRestrictions != null ? dietaryRestrictions : "无");

        return SYSTEM_ROLE + "\n\n" + scenePrompt;
    }

    /**
     * 构建运动康复提示词
     *
     * @param age 年龄
     * @param physicalCondition 身体状况
     * @param mobilityLevel 运动能力
     * @return 完整的系统提示词
     */
    public String buildExerciseRehabilitationPrompt(String age, String physicalCondition,
                                                     String mobilityLevel) {
        String scenePrompt = EXERCISE_REHABILITATION_TEMPLATE
                .replace("{age}", age)
                .replace("{physicalCondition}", physicalCondition != null ? physicalCondition : "一般")
                .replace("{mobilityLevel}", mobilityLevel != null ? mobilityLevel : "正常");

        return SYSTEM_ROLE + "\n\n" + scenePrompt;
    }

    /**
     * 构建用药提醒提示词
     *
     * @param medicationList 用药清单
     * @param medicationSchedule 用药时间表
     * @return 完整的系统提示词
     */
    public String buildMedicationReminderPrompt(String medicationList, String medicationSchedule) {
        String scenePrompt = MEDICATION_REMINDER_TEMPLATE
                .replace("{medicationList}", medicationList != null ? medicationList : "无")
                .replace("{medicationSchedule}", medicationSchedule != null ? medicationSchedule : "未设置");

        return SYSTEM_ROLE + "\n\n" + scenePrompt;
    }

    /**
     * 构建情感陪伴提示词
     *
     * @param moodState 情绪状态
     * @param hobbies 兴趣爱好
     * @return 完整的系统提示词
     */
    public String buildEmotionalCompanionPrompt(String moodState, String hobbies) {
        String scenePrompt = EMOTIONAL_COMPANION_TEMPLATE
                .replace("{moodState}", moodState != null ? moodState : "平静")
                .replace("{hobbies}", hobbies != null ? hobbies : "未提供");

        return SYSTEM_ROLE + "\n\n" + scenePrompt;
    }
}
