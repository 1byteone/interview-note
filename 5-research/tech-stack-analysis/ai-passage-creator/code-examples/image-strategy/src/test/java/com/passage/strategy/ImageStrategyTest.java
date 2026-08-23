package com.passage.strategy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ImageStrategyTest - 图片策略单元测试
 *
 * 测试覆盖场景：
 * 1. 默认策略链路测试（Pexels + Mermaid + Picsum 兜底）
 * 2. VIP 策略链路测试（AI 生图 + Pexels + Mermaid + Picsum 兜底）
 * 3. 空 API Key 降级测试（Pexels 不可用 → Mermaid 降级 → Picsum 兜底）
 * 4. 自定义策略切换测试
 *
 * @author AI-Passage-Creator
 */
class ImageStrategyTest {

    /** 测试用 Pexels API Key（模拟值，非真实 Key） */
    private static final String TEST_PEXELS_KEY = "test_pexels_api_key_12345";

    /** 测试用 AI API Key（模拟值，非真实 Key） */
    private static final String TEST_AI_KEY = "test_ai_api_key_67890";

    /** 测试用提示词 */
    private static final String TEST_PROMPT = "春日樱花树下读书的少女";

    /** 测试用技术类提示词 */
    private static final String TECH_PROMPT = "微服务架构流程图";

    // ==================== 测试用例 1：默认策略链路 ====================

    /**
     * 测试默认策略链路（普通用户）
     *
     * 验证场景：
     * - 普通用户使用 Pexels 为主策略 + Mermaid 为备选
     * - 由于 Pexels API Key 是模拟值，实际调用会失败
     * - 预期降级到 Mermaid 策略（Mermaid 不依赖 API Key）
     * - 最终返回的图片 URL 应该是 Mermaid 图表的渲染 URL
     */
    @Test
    @DisplayName("测试默认策略链路：Pexels失败 → Mermaid降级成功")
    void testDefaultStrategyChain() {
        // 创建默认的 ImageContext（普通用户模式）
        // 策略顺序：Pexels（主） → Mermaid（备选）
        ImageContext context = ImageStrategyFactory.createDefaultContext(TEST_PEXELS_KEY);

        // 执行图片获取（会触发降级链路）
        String imageUrl = context.getImage(TEST_PROMPT);

        // 验证：虽然 Pexels 会失败，但 Mermaid 降级成功
        assertNotNull(imageUrl, "图片 URL 不应为 null");
        assertTrue(imageUrl.contains("mermaid.ink"),
                "降级后应返回 Mermaid 图片 URL，实际: " + imageUrl);

        System.out.println("【测试1】默认策略链路结果: " + imageUrl);
    }

    // ==================== 测试用例 2：VIP 策略链路 ====================

    /**
     * 测试 VIP 策略链路
     *
     * 验证场景：
     * - VIP 用户使用 AI 生图为主策略 + Pexels + Mermaid 为备选
     * - 由于 AI API Key 是模拟值，AI 生图会失败
     * - Pexels API Key 也是模拟值，Pexels 也会失败
     * - 预期最终降级到 Mermaid 策略
     */
    @Test
    @DisplayName("测试VIP策略链路：AI生图失败 → Pexels失败 → Mermaid降级成功")
    void testVipStrategyChain() {
        // 创建 VIP 的 ImageContext
        ImageContext context = ImageStrategyFactory.createVipContext(
                TEST_PEXELS_KEY, TEST_AI_KEY
        );

        // 执行图片获取
        String imageUrl = context.getImage(TECH_PROMPT);

        // 验证：AI 生图和 Pexels 都失败后，Mermaid 降级成功
        assertNotNull(imageUrl, "图片 URL 不应为 null");
        assertTrue(imageUrl.contains("mermaid.ink"),
                "降级后应返回 Mermaid 图片 URL，实际: " + imageUrl);

        System.out.println("【测试2】VIP策略链路结果: " + imageUrl);
    }

    // ==================== 测试用例 3：空 API Key 降级 ====================

    /**
     * 测试 API Key 为空时的降级行为
     *
     * 验证场景：
     * - Pexels API Key 为空字符串
     * - PexelsStrategy.isAvailable() 返回 false（因为 API Key 为空）
     * - 直接跳过 Pexels，使用 Mermaid 作为降级
     * - 验证 isAvailable() 前置检查机制有效
     */
    @Test
    @DisplayName("测试空API Key降级：Pexels不可用 → Mermaid降级成功")
    void testEmptyApiKeyFallback() {
        // 使用空字符串作为 API Key，模拟未配置的情况
        ImageContext context = ImageStrategyFactory.createDefaultContext("");

        // 执行图片获取
        String imageUrl = context.getImage(TEST_PROMPT);

        // 验证：Pexels 因 API Key 为空不可用，直接降级到 Mermaid
        assertNotNull(imageUrl, "图片 URL 不应为 null");
        assertTrue(imageUrl.contains("mermaid.ink"),
                "Pexels不可用时应降级到 Mermaid，实际: " + imageUrl);

        System.out.println("【测试3】空API Key降级结果: " + imageUrl);
    }

    // ==================== 测试用例 4：Picsum 最终兜底 ====================

    /**
     * 测试 Picsum 最终兜底方案
     *
     * 验证场景：
     * - 创建一个只有 Pexels 策略的 ImageContext
     * - Pexels API Key 为空，isAvailable() 返回 false
     * - 所有策略都不可用/失败
     * - 最终降级到 Picsum 随机图片
     *
     * 此测试验证"三段式容错"的最后一道防线是否正常工作。
     */
    @Test
    @DisplayName("测试Picsum最终兜底：所有策略失败 → Picsum兜底成功")
    void testPicsumFallback() {
        // 创建一个只有 Pexels 策略的上下文，且 API Key 为空
        // 这样所有策略都不可用，触发 Picsum 兜底
        PexelsImageStrategy pexels = new PexelsImageStrategy("");
        ImageContext context = ImageStrategyFactory.createCustomContext(
                List.of(pexels)
        );

        // 执行图片获取
        String imageUrl = context.getImage(TEST_PROMPT);

        // 验证：最终返回 Picsum 图片 URL
        assertNotNull(imageUrl, "Picsum 兜底图片 URL 不应为 null");
        assertTrue(imageUrl.contains("picsum.photos"),
                "所有策略失败后应返回 Picsum 兜底图片，实际: " + imageUrl);

        // 验证：URL 包含 seed 参数（基于提示词哈希生成）
        assertTrue(imageUrl.contains("/seed/"),
                "Picsum URL 应包含 seed 参数，实际: " + imageUrl);

        System.out.println("【测试4】Picsum兜底结果: " + imageUrl);
    }

    // ==================== 测试用例 5：策略切换 ====================

    /**
     * 测试动态切换主策略
     *
     * 验证场景：
     * - 默认主策略为 Pexels
     * - 动态切换主策略为 Mermaid
     * - 验证切换后主策略名称正确更新
     * - 验证切换后能正确获取图片
     */
    @Test
    @DisplayName("测试动态切换主策略")
    void testSwitchPrimaryStrategy() {
        // 创建默认上下文
        ImageContext context = ImageStrategyFactory.createDefaultContext(TEST_PEXELS_KEY);

        // 验证默认主策略是 Pexels
        assertEquals("pexels", context.getPrimaryStrategyName(),
                "默认主策略应为 pexels");

        // 动态切换主策略为 Mermaid
        context.setPrimaryStrategy("mermaid");
        assertEquals("mermaid", context.getPrimaryStrategyName(),
                "切换后主策略应为 mermaid");

        // 验证切换后能正确获取图片
        String imageUrl = context.getImage(TEST_PROMPT);
        assertNotNull(imageUrl, "切换主策略后图片 URL 不应为 null");
        assertTrue(imageUrl.contains("mermaid.ink"),
                "主策略为 Mermaid 时应返回 Mermaid 图片，实际: " + imageUrl);

        System.out.println("【测试5】策略切换结果: " + imageUrl);
    }

    // ==================== 测试用例 6：策略不可用状态验证 ====================

    /**
     * 测试策略的 isAvailable() 方法
     *
     * 验证场景：
     * - Pexels 策略：有 API Key 时可用，无 API Key 时不可用
     * - Mermaid 策略：始终可用
     * - AI 生图策略：VIP + 有 API Key 时可用，非 VIP 时不可用
     */
    @Test
    @DisplayName("测试策略可用性状态")
    void testStrategyAvailability() {
        // 验证 Pexels 策略可用性
        PexelsImageStrategy pexelsWithKey = new PexelsImageStrategy(TEST_PEXELS_KEY);
        assertTrue(pexelsWithKey.isAvailable(), "有 API Key 时 Pexels 应可用");

        PexelsImageStrategy pexelsNoKey = new PexelsImageStrategy("");
        assertFalse(pexelsNoKey.isAvailable(), "无 API Key 时 Pexels 应不可用");

        // 验证 Mermaid 策略可用性（始终可用）
        MermaidImageStrategy mermaid = new MermaidImageStrategy();
        assertTrue(mermaid.isAvailable(), "Mermaid 策略应始终可用");

        // 验证 AI 生图策略可用性
        AiImageStrategy aiVip = new AiImageStrategy(TEST_AI_KEY, true);
        assertTrue(aiVip.isAvailable(), "VIP + 有 Key 时 AI 生图应可用");

        AiImageStrategy aiNonVip = new AiImageStrategy(TEST_AI_KEY, false);
        assertFalse(aiNonVip.isAvailable(), "非 VIP 时 AI 生图应不可用");

        System.out.println("【测试6】策略可用性验证完成");
    }
}