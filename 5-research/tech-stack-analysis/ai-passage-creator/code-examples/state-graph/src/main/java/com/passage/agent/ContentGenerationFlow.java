package com.passage.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * ContentGenerationFlow - StateGraph 多Agent编排定义
 *
 * 定义文章生成的完整工作流，包含 5 个 Agent 节点：
 *
 *   [用户输入]
 *       |
 *   [TitleGenerator] ---- 标题生成
 *       |
 *   [OutlineGenerator] -- 大纲生成
 *       |
 *   [ContentGenerator] -- 内容生成
 *       |
 *   [ImageAnalyzer] ---- 图片分析（并行）
 *       |
 *   [ContentMerger] ---- 内容合并（汇聚）
 *       |
 *   [最终文章输出]
 *
 * 工作流特点：
 * 1. 严格的顺序执行（依赖关系）
 * 2. 每个节点读写共享状态
 * 3. 异常状态可被后续节点检测
 * 4. 汇聚节点整合所有输出
 *
 * @author AI-Passage-Creator
 */
@Component
public class ContentGenerationFlow {

    private static final Logger log = LoggerFactory.getLogger(ContentGenerationFlow.class);

    /** 标题生成 Agent */
    private final TitleGeneratorAgent titleGenerator;

    /** 大纲生成 Agent */
    private final OutlineGeneratorAgent outlineGenerator;

    /** 内容生成 Agent */
    private final ContentGeneratorAgent contentGenerator;

    /** 图片分析 Agent */
    private final ImageAnalyzerAgent imageAnalyzer;

    /** 内容合并 Agent */
    private final ContentMergerAgent contentMerger;

    /**
     * 构造方法 - 注入所有 Agent
     *
     * @param titleGenerator   标题生成 Agent
     * @param outlineGenerator 大纲生成 Agent
     * @param contentGenerator 内容生成 Agent
     * @param imageAnalyzer    图片分析 Agent
     * @param contentMerger    内容合并 Agent
     */
    public ContentGenerationFlow(
            TitleGeneratorAgent titleGenerator,
            OutlineGeneratorAgent outlineGenerator,
            ContentGeneratorAgent contentGenerator,
            ImageAnalyzerAgent imageAnalyzer,
            ContentMergerAgent contentMerger
    ) {
        this.titleGenerator = titleGenerator;
        this.outlineGenerator = outlineGenerator;
        this.contentGenerator = contentGenerator;
        this.imageAnalyzer = imageAnalyzer;
        this.contentMerger = contentMerger;
    }

    /**
     * 执行完整的文章生成工作流
     *
     * 按照预定义的顺序依次执行各个 Agent：
     * 1. 标题生成 -> 2. 大纲生成 -> 3. 内容生成 -> 4. 图片分析 -> 5. 内容合并
     *
     * 每个步骤执行前会检查前置步骤是否有错误，
     * 如果有错误则提前终止并返回当前状态。
     *
     * @param userInput 用户输入的主题或关键词
     * @return 包含最终文章的状态对象
     */
    public AgentState execute(String userInput) {
        log.info("开始执行文章生成工作流，用户输入: {}", userInput);

        // 初始化状态
        AgentState state = new AgentState(userInput);

        // ====== 第一步：标题生成 ======
        log.info("步骤 1/5: 生成标题...");
        state = titleGenerator.execute(state);
        if (state.hasError()) {
            log.error("标题生成失败: {}", state.getErrorInfo());
            return state;
        }
        log.info("标题生成完成: {}", state.getTitle());

        // ====== 第二步：大纲生成 ======
        log.info("步骤 2/5: 生成大纲...");
        state = outlineGenerator.execute(state);
        if (state.hasError()) {
            log.error("大纲生成失败: {}", state.getErrorInfo());
            return state;
        }
        log.info("大纲生成完成，大纲长度: {} 字符", state.getOutline().length());

        // ====== 第三步：内容生成 ======
        log.info("步骤 3/5: 生成文章内容...");
        state = contentGenerator.execute(state);
        if (state.hasError()) {
            log.error("内容生成失败: {}", state.getErrorInfo());
            return state;
        }
        log.info("内容生成完成，正文长度: {} 字符", state.getContent().length());

        // ====== 第四步：图片分析 ======
        log.info("步骤 4/5: 分析配图需求...");
        state = imageAnalyzer.execute(state);
        // 图片分析失败不终止流程（非关键步骤）
        if (state.hasError()) {
            log.warn("图片分析失败（非关键错误）: {}", state.getErrorInfo());
            state.setErrorInfo(null); // 清除错误，继续执行
        } else {
            log.info("图片分析完成");
        }

        // ====== 第五步：内容合并 ======
        log.info("步骤 5/5: 合并最终文章...");
        state = contentMerger.execute(state);
        if (state.hasError()) {
            log.error("内容合并失败: {}", state.getErrorInfo());
            return state;
        }
        log.info("文章生成工作流执行完成，最终文章长度: {} 字符",
                state.getFinalContent().length());

        return state;
    }
}
