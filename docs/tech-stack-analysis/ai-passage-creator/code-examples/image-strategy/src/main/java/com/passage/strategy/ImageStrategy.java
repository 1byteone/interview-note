package com.passage.strategy;

/**
 * ImageStrategy - 图片获取策略接口
 *
 * 策略模式的核心接口，定义了所有图片获取策略必须实现的方法。
 * 每种策略代表一种图片来源方式：
 * - PexelsImageStrategy: 从 Pexels 免费图库搜索图片
 * - MermaidImageStrategy: 生成 Mermaid 图表并渲染为图片
 * - AiImageStrategy: 调用 AI 图片生成 API 生图（VIP 功能）
 *
 * 各策略通过 ImageContext 统一编排，实现"优先尝试 -> 失败降级 -> 兜底"的容错链路。
 *
 * @author AI-Passage-Creator
 */
public interface ImageStrategy {

    /**
     * 根据提示词生成/获取图片 URL
     *
     * 核心业务方法，每个策略以自己的方式实现：
     * - Pexels：调用 Pexels REST API 搜索图片，返回第一张匹配结果的 URL
     * - Mermaid：将提示词翻译为 Mermaid 定义，渲染为 SVG/PNG 并返回
     * - AI：调用 DALL-E / Stable Diffusion 等 API 生图，返回生成的图片 URL
     *
     * @param prompt 图片描述提示词（如"春日樱花树下读书的少女"）
     * @return 图片的 URL 字符串
     * @throws ImageAcquisitionException 当图片获取失败时抛出
     */
    String generateImage(String prompt);

    /**
     * 获取策略名称
     *
     * 用于日志记录、监控和调试，方便追踪当前使用的是哪种策略。
     *
     * @return 策略名称（如 "pexels", "mermaid", "ai-image"）
     */
    String getName();

    /**
     * 检查当前策略是否可用
     *
     * 在调用 generateImage 之前由 ImageContext 调用此方法进行前置检查。
     * 检查条件包括：
     * - API Key 是否已配置
     * - 网络连接是否正常
     * - 是否为 VIP 用户（AI 生图策略需要）
     *
     * @return true 表示策略可用，false 表示不可用
     */
    boolean isAvailable();
}