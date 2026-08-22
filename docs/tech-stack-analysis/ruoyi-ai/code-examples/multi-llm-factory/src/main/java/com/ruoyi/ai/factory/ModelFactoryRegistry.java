package com.ruoyi.ai.factory;

import dev.langchain4j.model.chat.ChatLanguageModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * ModelFactoryRegistry - 模型工厂注册表
 *
 * 核心角色：作为所有 ModelFactory 的统一注册中心和访问入口
 *
 * 工作原理（Spring DI Map 注入）：
 * 1. Spring 容器启动时，自动扫描所有 @Component 标注的 ModelFactory 实现
 * 2. 将所有实现注入到 Map<String, ModelFactory> 中
 *    - key：工厂的 getProviderName() 返回值（如 "openai"）
 *    - value：工厂实例（如 OpenAiModelFactory）
 * 3. 通过 provider 名称即可获取对应工厂，无需手动注册
 *
 * 使用场景：
 * - 业务层只需注入 ModelFactoryRegistry，按 provider 名称获取工厂
 * - 无需 if-else 判断使用哪个模型，完全解耦
 */
@Component  // 注册为 Spring Bean
public class ModelFactoryRegistry {

    /**
     * 工厂实例映射表
     *
     * Spring 自动注入流程：
     * 1. 找到所有实现 ModelFactory 接口的 Bean
     * 2. 调用每个 Bean 的 getProviderName() 获取 key
     * 3. 以 key → Bean 的形式注入到此 Map
     *
     * 例如：
     * Map {
     *   "openai"  → OpenAiModelFactory 实例,
     *   "deepseek" → DeepSeekModelFactory 实例,
     *   "qwen"    → QwenModelFactory 实例
     * }
     */
    private final Map<String, ModelFactory> factoryMap;

    /**
     * 构造器注入 - Spring 自动收集所有 ModelFactory Bean
     *
     * @param factories Spring 注入的所有 ModelFactory 实现
     *                   key 由 getProviderName() 自动决定
     */
    @Autowired  // 构造器注入（Spring 推荐方式，不可省略）
    public ModelFactoryRegistry(List<ModelFactory> factories) {
        // 使用 Stream API 将 List 转换为 Map
        // Map 的 key：factory.getProviderName()
        // Map 的 value：factory 实例本身
        this.factoryMap = factories.stream()
                .collect(Collectors.toMap(
                        ModelFactory::getProviderName,   // key 映射器
                        Function.identity()              // value 映射器（保持原对象）
                ));
    }

    /**
     * 根据厂商名称获取对应的模型工厂
     *
     * 使用场景：
     * - 配置文件中 model.provider=deepseek
     * - 调用 getFactory("deepseek") 获取 DeepSeekModelFactory
     *
     * @param provider 厂商标识（openai/deepseek/qwen）
     * @return 对应的 ModelFactory 实例
     * @throws IllegalArgumentException 当 provider 未注册时抛出
     */
    public ModelFactory getFactory(String provider) {
        // 从映射表中查找对应工厂
        ModelFactory factory = factoryMap.get(provider.toLowerCase());
        // 如果未找到，抛出明确的错误信息，列出所有可用的厂商
        if (factory == null) {
            throw new IllegalArgumentException(
                String.format("未注册的模型提供商: %s，可用提供商: %s",
                        provider, factoryMap.keySet())
            );
        }
        return factory;
    }

    /**
     * 根据厂商名称直接创建模型实例（便捷方法）
     *
     * 组合了 getFactory + createModel 两步操作
     *
     * @param provider 厂商标识
     * @param config   模型配置
     * @return 配置好的 ChatLanguageModel 实例
     */
    public ChatLanguageModel createModel(String provider, ModelConfig config) {
        return getFactory(provider).createModel(config);
    }

    /**
     * 获取所有已注册的厂商名称列表
     *
     * 用途：在前端下拉菜单中展示可选的模型厂商
     *
     * @return 所有已注册厂商的名称集合
     */
    public List<String> getAvailableProviders() {
        return List.copyOf(factoryMap.keySet());
    }
}
