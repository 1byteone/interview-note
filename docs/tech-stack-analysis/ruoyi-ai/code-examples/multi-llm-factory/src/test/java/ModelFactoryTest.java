package com.ruoyi.ai.factory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ModelFactoryTest - 模型工厂模式单元测试
 *
 * 测试重点：
 * 1. ModelFactoryRegistry 的 Map 注入机制
 * 2. 工厂获取逻辑的正确性
 * 3. 异常情况的处理
 *
 * 注意：本测试不实际调用外部 API（避免网络依赖和费用）
 * 仅测试工厂注册和查找逻辑
 */
class ModelFactoryTest {

    // 工厂注册表实例（待测试）
    private ModelFactoryRegistry registry;

    // 各工厂实例
    private OpenAiModelFactory openAiFactory;
    private DeepSeekModelFactory deepSeekFactory;
    private QwenModelFactory qwenFactory;

    /**
     * 每个测试方法执行前初始化测试数据
     * 模拟 Spring 容器注入的场景
     */
    @BeforeEach
    void setUp() {
        // 手动创建各工厂实例（单元测试中不启动 Spring 容器）
        openAiFactory = new OpenAiModelFactory();
        deepSeekFactory = new DeepSeekModelFactory();
        qwenFactory = new QwenModelFactory();

        // 模拟 Spring 的 Map 注入：传入所有工厂实例
        // ModelFactoryRegistry 构造器内部会自动调用 getProviderName() 建立映射
        registry = new ModelFactoryRegistry(List.of(
                openAiFactory,
                deepSeekFactory,
                qwenFactory
        ));
    }

    // ==================== 工厂标识测试 ====================

    @Test
    @DisplayName("各工厂的 providerName 标识正确")
    void shouldReturnCorrectProviderNames() {
        // 验证每个工厂返回的标识符合预期
        assertEquals("openai", openAiFactory.getProviderName());
        assertEquals("deepseek", deepSeekFactory.getProviderName());
        assertEquals("qwen", qwenFactory.getProviderName());
    }

    // ==================== 工厂注册测试 ====================

    @Test
    @DisplayName("获取所有可用的厂商标识列表")
    void shouldReturnAllAvailableProviders() {
        // 调用 getAvailableProviders() 获取所有已注册的厂商
        List<String> providers = registry.getAvailableProviders();

        // 断言：应该包含所有 3 个厂商标识
        assertNotNull(providers);
        assertEquals(3, providers.size());
        assertTrue(providers.contains("openai"));
        assertTrue(providers.contains("deepseek"));
        assertTrue(providers.contains("qwen"));
    }

    // ==================== 工厂查找测试 ====================

    @Test
    @DisplayName("根据厂商标识正确获取对应工厂")
    void shouldGetCorrectFactoryByProvider() {
        // 获取各厂商标识对应的工厂实例
        ModelFactory openai = registry.getFactory("openai");
        ModelFactory deepseek = registry.getFactory("deepseek");
        ModelFactory qwen = registry.getFactory("qwen");

        // 断言：返回的工厂类型正确
        assertInstanceOf(OpenAiModelFactory.class, openai);
        assertInstanceOf(DeepSeekModelFactory.class, deepseek);
        assertInstanceOf(QwenModelFactory.class, qwen);
    }

    @Test
    @DisplayName("厂商标识应大小写不敏感")
    void shouldHandleProviderNameCaseInsensitive() {
        // 测试大小写混合的情况
        // getFactory 内部会调用 toLowerCase()
        ModelFactory factory1 = registry.getFactory("OpenAI");
        ModelFactory factory2 = registry.getFactory("DEEPSEEK");
        ModelFactory factory3 = registry.getFactory("Qwen");

        assertNotNull(factory1);
        assertNotNull(factory2);
        assertNotNull(factory3);
    }

    // ==================== 异常情况测试 ====================

    @Test
    @DisplayName("未注册的厂商应抛出 IllegalArgumentException")
    void shouldThrowExceptionForUnknownProvider() {
        // 测试不存在的厂商标识
        // 应该抛出 IllegalArgumentException 并包含 "未注册" 信息
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> registry.getFactory("unknown-provider")
        );

        // 验证异常信息包含关键内容
        assertTrue(exception.getMessage().contains("未注册的模型提供商"));
        assertTrue(exception.getMessage().contains("unknown-provider"));
    }

    // ==================== 工厂创建模型测试 ====================

    @Test
    @DisplayName("缺少 API Key 时创建模型应抛出异常")
    void shouldThrowExceptionWhenApiKeyMissing() {
        // 构造缺少 API Key 的配置
        ModelConfig configWithoutKey = ModelConfig.builder()
                .modelName("gpt-4o")
                .temperature(0.7)
                .build();  // apiKey 为 null

        // 验证 OpenAI 工厂抛出异常
        assertThrows(
                IllegalArgumentException.class,
                () -> openAiFactory.createModel(configWithoutKey)
        );

        // 验证 DeepSeek 工厂抛出异常
        assertThrows(
                IllegalArgumentException.class,
                () -> deepSeekFactory.createModel(configWithoutKey)
        );

        // 验证 Qwen 工厂抛出异常
        assertThrows(
                IllegalArgumentException.class,
                () -> qwenFactory.createModel(configWithoutKey)
        );
    }

    // ==================== ModelConfig 构建测试 ====================

    @Test
    @DisplayName("ModelConfig Builder 应正确构建配置对象")
    void shouldBuildModelConfigCorrectly() {
        // 使用 Builder 模式构建配置对象
        ModelConfig config = ModelConfig.builder()
                .apiKey("test-api-key")
                .modelName("deepseek-chat")
                .temperature(0.7)
                .baseUrl("https://api.deepseek.com")
                .build();

        // 验证所有字段正确设置
        assertEquals("test-api-key", config.getApiKey());
        assertEquals("deepseek-chat", config.getModelName());
        assertEquals(0.7, config.getTemperature(), 0.001);
        assertEquals("https://api.deepseek.com", config.getBaseUrl());
    }
}
