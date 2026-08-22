package com.passage.strategy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * ImageContext - 图片获取策略上下文（核心编排类）
 *
 * 策略模式中的 Context 角色，负责：
 * 1. 管理策略列表（按优先级排序）
 * 2. 执行策略选择与调用
 * 3. 实现失败降级链路
 * 4. 提供最终兜底方案（Picsum 随机图片）
 *
 * 降级链路（三段式容错）：
 * ┌─────────────────────────────────────────────────────┐
 * │ 第一级：主策略优先尝试                                │
 * │   → 可用 & 成功 → 返回结果                            │
 * │   → 不可用或失败 → 进入第二级                         │
 * ├─────────────────────────────────────────────────────┤
 * │ 第二级：遍历备用策略                                  │
 * │   → 按优先级顺序尝试每个策略                           │
 * │   → 某个策略成功 → 返回结果                           │
 * │   → 全部失败 → 进入第三级                             │
 * ├─────────────────────────────────────────────────────┤
 * │ 第三级：Picsum 兜底                                  │
 * │   → 返回 Picsum 随机图片 URL（100% 可用）              │
 * └─────────────────────────────────────────────────────┘
 *
 * 这种设计保证了：
 * - 图片获取的健壮性：任何单点故障都不会导致整个流程失败
 * - 资源利用的最优性：优先使用免费/低成本的策略
 * - 用户体验的平滑性：即使所有外部服务都不可用，也有兜底图片
 *
 * @author AI-Passage-Creator
 */
public class ImageContext {

    private static final Logger log = LoggerFactory.getLogger(ImageContext.class);

    /** Picsum 兜底图片基础 URL（生成随机图片，100% 可用） */
    private static final String PICSUM_BASE_URL = "https://picsum.photos";

    /** 兜底图片尺寸：宽度 */
    private static final int FALLBACK_WIDTH = 800;

    /** 兜底图片尺寸：高度 */
    private static final int FALLBACK_HEIGHT = 600;

    /** 策略列表（按优先级从高到低排序） */
    private final List<ImageStrategy> strategies;

    /** 默认主策略索引 */
    private int primaryStrategyIndex;

    /**
     * 构造方法
     *
     * @param strategies 按优先级排序的策略列表（索引0为最高优先级）
     */
    public ImageContext(List<ImageStrategy> strategies) {
        this.strategies = new ArrayList<>(strategies);
        this.primaryStrategyIndex = 0;
        log.info("ImageContext 初始化完成，共 {} 个策略", strategies.size());
    }

    /**
     * 获取图片（带完整降级链路）
     *
     * 执行流程：
     * 1. 尝试主策略（primaryStrategyIndex 指向的策略）
     * 2. 如果主策略不可用或失败，遍历所有备用策略
     * 3. 如果所有策略都失败，使用 Picsum 兜底图片
     *
     * @param prompt 图片描述提示词
     * @return 获取到的图片 URL（保证不会返回 null）
     */
    public String getImage(String prompt) {
        log.info("====== 开始获取图片，提示词: {} ======", prompt);

        // 第一阶段：尝试主策略
        String result = tryPrimaryStrategy(prompt);
        if (result != null) {
            return result;
        }

        // 第二阶段：尝试备用策略（降级）
        result = tryFallbackStrategies(prompt);
        if (result != null) {
            return result;
        }

        // 第三阶段：Picsum 兜底（保证成功）
        return usePicsumFallback(prompt);
    }

    /**
     * 尝试主策略
     *
     * 主策略通常是当前最合适的策略。如果主策略不可用或调用失败，
     * 自动进入降级链路。
     *
     * @param prompt 图片描述提示词
     * @return 图片 URL，如果主策略失败返回 null
     */
    private String tryPrimaryStrategy(String prompt) {
        ImageStrategy primary = strategies.get(primaryStrategyIndex);

        log.info("【阶段一】尝试主策略: {}", primary.getName());

        // 检查主策略是否可用
        if (!primary.isAvailable()) {
            log.warn("主策略 {} 不可用（如 API Key 未配置或非 VIP），进入降级", primary.getName());
            return null;
        }

        try {
            String imageUrl = primary.generateImage(prompt);
            log.info("主策略 {} 成功获取图片", primary.getName());
            return imageUrl;
        } catch (ImageAcquisitionException e) {
            log.warn("主策略 {} 执行失败: {}，进入降级", primary.getName(), e.getMessage());
            return null;
        }
    }

    /**
     * 尝试备用策略（降级链路）
     *
     * 遍历所有非主策略，按优先级依次尝试：
     * 1. 跳过主策略（已失败）
     * 2. 跳过不可用的策略
     * 3. 遇到第一个成功的策略立即返回
     * 4. 所有策略都失败则返回 null
     *
     * @param prompt 图片描述提示词
     * @return 图片 URL，所有备用策略都失败返回 null
     */
    private String tryFallbackStrategies(String prompt) {
        log.info("【阶段二】尝试备用策略（降级）");

        for (int i = 0; i < strategies.size(); i++) {
            // 跳过主策略（已在第一阶段尝试过）
            if (i == primaryStrategyIndex) {
                continue;
            }

            ImageStrategy strategy = strategies.get(i);

            // 检查备用策略是否可用
            if (!strategy.isAvailable()) {
                log.warn("备用策略 {} 不可用，跳过", strategy.getName());
                continue;
            }

            try {
                log.info("尝试备用策略: {}", strategy.getName());
                String imageUrl = strategy.generateImage(prompt);
                log.info("备用策略 {} 成功获取图片", strategy.getName());
                return imageUrl;
            } catch (ImageAcquisitionException e) {
                log.warn("备用策略 {} 执行失败: {}", strategy.getName(), e.getMessage());
                // 继续尝试下一个备用策略
            }
        }

        log.warn("所有备用策略均已失败，进入 Picsum 兜底");
        return null;
    }

    /**
     * Picsum 兜底方案
     *
     * 当所有策略都失败时，使用 Picsum 提供的随机图片作为最终兜底。
     * Picsum 是一个免费图片占位符服务，返回随机的高质量图片，
     * 不需要任何 API Key，100% 可用。
     *
     * 使用 seed 参数保证相同提示词生成相同的兜底图片（缓存友好）。
     *
     * @param prompt 图片描述提示词（用于生成 seed，保证一致性）
     * @return Picsum 随机图片 URL
     */
    private String usePicsumFallback(String prompt) {
        // 使用提示词的哈希值作为 seed，保证相同提示词获得相同兜底图片
        int seed = prompt.hashCode();
        String fallbackUrl = String.format(
                "%s/seed/%d/%d/%d",
                PICSUM_BASE_URL, seed, FALLBACK_WIDTH, FALLBACK_HEIGHT
        );

        log.info("【阶段三】使用 Picsum 兜底图片: {}", fallbackUrl);
        return fallbackUrl;
    }

    /**
     * 设置主策略
     *
     * 允许动态调整主策略，例如：
     * - VIP 用户可以将 AI 生图设为主策略
     * - 非 VIP 用户默认以 Pexels 为主策略
     *
     * @param strategyName 策略名称（对应 getName() 返回值）
     * @throws IllegalArgumentException 如果找不到对应策略
     */
    public void setPrimaryStrategy(String strategyName) {
        for (int i = 0; i < strategies.size(); i++) {
            if (strategies.get(i).getName().equals(strategyName)) {
                this.primaryStrategyIndex = i;
                log.info("主策略已切换为: {}", strategyName);
                return;
            }
        }
        throw new IllegalArgumentException("未找到名为 " + strategyName + " 的策略");
    }

    /**
     * 获取当前主策略名称
     *
     * @return 主策略名称
     */
    public String getPrimaryStrategyName() {
        return strategies.get(primaryStrategyIndex).getName();
    }

    /**
     * 获取所有策略名称列表
     *
     * @return 策略名称列表
     */
    public List<String> getStrategyNames() {
        return strategies.stream()
                .map(ImageStrategy::getName)
                .toList();
    }
}