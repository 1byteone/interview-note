package com.passage.strategy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * ImageStrategyFactory - 图片策略工厂
 *
 * 根据配置创建并组装 ImageContext 实例。
 * 工厂负责：
 * 1. 读取配置参数（API Key、VIP 状态等）
 * 2. 创建所有策略实例
 * 3. 按优先级排序并注入 ImageContext
 * 4. 根据用户等级（VIP/普通）设置默认主策略
 *
 * 使用示例：
 * <pre>
 * // 普通用户：Pexels 为主策略，Mermaid 为备选
 * ImageContext context = ImageStrategyFactory.createDefaultContext("pexels_key_xxx");
 *
 * // VIP 用户：AI 生图为主策略
 * ImageContext vipContext = ImageStrategyFactory.createVipContext(
 *     "pexels_key_xxx", "openai_key_xxx"
 * );
 * </pre>
 *
 * @author AI-Passage-Creator
 */
public class ImageStrategyFactory {

    private static final Logger log = LoggerFactory.getLogger(ImageStrategyFactory.class);

    /**
     * 私有构造方法，防止实例化
     */
    private ImageStrategyFactory() {
        // 工具类，不需要实例化
    }

    /**
     * 创建默认的 ImageContext（普通用户）
     *
     * 策略优先级：
     * 1. PexelsImageStrategy（主策略 - 免费图库搜索）
     * 2. MermaidImageStrategy（备选 - 图表生成）
     * 3. Picsum 兜底（在 ImageContext 内部实现）
     *
     * @param pexelsApiKey Pexels API Key
     * @return 配置好的 ImageContext 实例
     */
    public static ImageContext createDefaultContext(String pexelsApiKey) {
        log.info("创建默认 ImageContext（普通用户）");

        // 按优先级创建策略列表
        List<ImageStrategy> strategies = new ArrayList<>();
        strategies.add(new PexelsImageStrategy(pexelsApiKey));  // 主策略：Pexels
        strategies.add(new MermaidImageStrategy());              // 备选：Mermaid 图表

        ImageContext context = new ImageContext(strategies);
        log.info("默认 ImageContext 创建完成，策略: {}", context.getStrategyNames());
        return context;
    }

    /**
     * 创建 VIP 用户的 ImageContext
     *
     * 策略优先级：
     * 1. AiImageStrategy（主策略 - AI 生图，VIP 专属）
     * 2. PexelsImageStrategy（备选 - 免费图库搜索）
     * 3. MermaidImageStrategy（备选 - 图表生成）
     * 4. Picsum 兜底（在 ImageContext 内部实现）
     *
     * VIP 用户拥有更多策略选择，且 AI 生图作为主策略可获得更精确的配图。
     *
     * @param pexelsApiKey Pexels API Key
     * @param aiApiKey     AI 图片生成 API Key
     * @return 配置好的 ImageContext 实例
     */
    public static ImageContext createVipContext(String pexelsApiKey, String aiApiKey) {
        log.info("创建 VIP ImageContext");

        // 按优先级创建策略列表
        List<ImageStrategy> strategies = new ArrayList<>();
        strategies.add(new AiImageStrategy(aiApiKey, true));     // 主策略：AI 生图（VIP）
        strategies.add(new PexelsImageStrategy(pexelsApiKey));   // 备选：Pexels
        strategies.add(new MermaidImageStrategy());               // 备选：Mermaid 图表

        ImageContext context = new ImageContext(strategies);
        // 将主策略设置为 AI 生图（索引0）
        context.setPrimaryStrategy("ai-image");
        log.info("VIP ImageContext 创建完成，策略: {}", context.getStrategyNames());
        return context;
    }

    /**
     * 自定义策略列表创建 ImageContext
     *
     * 允许调用方完全自定义策略列表和顺序，灵活性最高。
     * 适用于需要特殊策略组合的场景。
     *
     * @param strategies 自定义策略列表（按优先级排序）
     * @return 配置好的 ImageContext 实例
     */
    public static ImageContext createCustomContext(List<ImageStrategy> strategies) {
        log.info("创建自定义 ImageContext，策略数量: {}", strategies.size());
        return new ImageContext(strategies);
    }
}