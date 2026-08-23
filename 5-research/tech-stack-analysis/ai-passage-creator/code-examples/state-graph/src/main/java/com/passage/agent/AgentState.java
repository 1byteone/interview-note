package com.passage.agent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AgentState - StateGraph 状态定义类
 *
 * 定义多Agent工作流中流转的状态数据结构。
 * StateGraph 的每个节点（Agent）读取和写入此状态。
 *
 * 状态字段说明：
 * - userInput: 用户原始输入（主题/关键词）
 * - title: 生成的文章标题
 * - outline: 生成的文章大纲
 * - content: 生成的文章正文
 * - imageUrls: AI 配图 URL 列表
 * - finalContent: 最终合并后的完整文章
 * - currentStep: 当前执行步骤（用于调试和日志）
 * - errorInfo: 错误信息（异常时填充）
 *
 * @author AI-Passage-Creator
 */
public class AgentState {

    /** 用户输入的主题或关键词 */
    private String userInput;

    /** 生成的文章标题 */
    private String title;

    /** 生成的文章大纲（Markdown 格式） */
    private String outline;

    /** 生成的文章正文（Markdown 格式） */
    private String content;

    /** AI 生成的配图 URL 列表 */
    private List<String> imageUrls;

    /** 最终合并后的完整文章 */
    private String finalContent;

    /** 当前执行步骤标识 */
    private String currentStep;

    /** 错误信息（异常时填充） */
    private String errorInfo;

    /** 状态元数据（扩展字段） */
    private Map<String, Object> metadata;

    // ========== 构造方法 ==========

    /**
     * 默认无参构造方法
     */
    public AgentState() {
        this.metadata = new HashMap<>();
    }

    /**
     * 带用户输入的构造方法
     *
     * @param userInput 用户输入的主题或关键词
     */
    public AgentState(String userInput) {
        this.userInput = userInput;
        this.metadata = new HashMap<>();
    }

    // ========== Getters & Setters ==========

    public String getUserInput() {
        return userInput;
    }

    public void setUserInput(String userInput) {
        this.userInput = userInput;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getOutline() {
        return outline;
    }

    public void setOutline(String outline) {
        this.outline = outline;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public List<String> getImageUrls() {
        return imageUrls;
    }

    public void setImageUrls(List<String> imageUrls) {
        this.imageUrls = imageUrls;
    }

    public String getFinalContent() {
        return finalContent;
    }

    public void setFinalContent(String finalContent) {
        this.finalContent = finalContent;
    }

    public String getCurrentStep() {
        return currentStep;
    }

    public void setCurrentStep(String currentStep) {
        this.currentStep = currentStep;
    }

    public String getErrorInfo() {
        return errorInfo;
    }

    public void setErrorInfo(String errorInfo) {
        this.errorInfo = errorInfo;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    // ========== 工具方法 ==========

    /**
     * 检查状态是否包含错误
     *
     * @return 如果有错误信息返回 true
     */
    public boolean hasError() {
        return errorInfo != null && !errorInfo.isEmpty();
    }

    /**
     * 将状态转换为 Map 格式（用于 StateGraph 序列化）
     *
     * @return 包含所有状态字段的 Map
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("userInput", userInput);
        map.put("title", title);
        map.put("outline", outline);
        map.put("content", content);
        map.put("imageUrls", imageUrls);
        map.put("finalContent", finalContent);
        map.put("currentStep", currentStep);
        map.put("errorInfo", errorInfo);
        map.put("metadata", metadata);
        return map;
    }

    /**
     * 从 Map 恢复状态对象
     *
     * @param map 状态 Map
     * @return 恢复后的 AgentState 对象
     */
    public static AgentState fromMap(Map<String, Object> map) {
        AgentState state = new AgentState();
        state.setUserInput((String) map.get("userInput"));
        state.setTitle((String) map.get("title"));
        state.setOutline((String) map.get("outline"));
        state.setContent((String) map.get("content"));
        state.setImageUrls((List<String>) map.get("imageUrls"));
        state.setFinalContent((String) map.get("finalContent"));
        state.setCurrentStep((String) map.get("currentStep"));
        state.setErrorInfo((String) map.get("errorInfo"));
        state.setMetadata((Map<String, Object>) map.getOrDefault("metadata", new HashMap<>()));
        return state;
    }

    @Override
    public String toString() {
        return "AgentState{" +
                "userInput='" + userInput + '\'' +
                ", title='" + title + '\'' +
                ", currentStep='" + currentStep + '\'' +
                ", hasError=" + hasError() +
                '}';
    }
}
