package com.ruoyi.ai.factory;

import dev.langchain4j.model.chat.ChatLanguageModel;
import org.springframework.stereotype.Component;

/**
 * OpenAiModelFactory - OpenAI 模型工厂实现
 *
 * 负责创建 OpenAI API 兼容的模型实例，支持：
 * - OpenAI 官方 API（GPT-4o/GPT-4/GPT-3.5-turbo）
 * - OpenAI 兼容代理服务（如 LiteLLM、OneAPI）
 *
 * 关键配置项：
 * - apiKey：OpenAI API 密钥（必需）
 * - baseUrl：自定义 API 端点（可选，默认为 OpenAI 官方地址）
 * - modelName：模型名称，如 gpt-4o、gpt-4-turbo
 */
@Component  // 注册为 Spring Bean，自动被 ModelFactoryRegistry 的 Map 注入发现
public class OpenAiModelFactory implements ModelFactory {

    /**
     * 工厂标识名 - 必须与配置文件中的 model.provider 值匹配
     * 对应配置：model.provider: openai
     */
    private static final String PROVIDER_NAME = "openai";

    /**
     * 返回此工厂负责处理的厂商标识
     *
     * ModelFactoryRegistry 通过此值从 Map 中查找对应工厂
     *
     * @return "openai"
     */
    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }

    /**
     * 创建 OpenAI 聊天模型实例
     *
     * 实现逻辑：
     * 1. 检查 API Key 是否存在
     * 2. 使用 LangChain4j 的 OpenAiChatModel.Builder 构建模型
     * 3. 设置 API Key、Base URL、模型名称、温度等参数
     *
     * @param config 包含 API 凭证和模型参数的配置对象
     * @return 配置好的 ChatLanguageModel 实例
     * @throws IllegalArgumentException 当 API Key 缺失时抛出
     */
    @Override
    public ChatLanguageModel createModel(ModelConfig config) {
        // 验证 API Key 必须提供
        if (config.getApiKey() == null || config.getApiKey().isBlank()) {
            throw new IllegalArgumentException(
                "OpenAI API Key 不能为空，请在配置文件中设置 openai.api-key"
            );
        }

        // 构建 OpenAI 聊天模型
        return dev.langchain4j.model.openai.OpenAiChatModel.builder()
                .apiKey(config.getApiKey())           // 设置 API 认证密钥
                .modelName(config.getModelName())     // 模型名称，如 gpt-4o
                .temperature(config.getTemperature()) // 生成温度，范围 0.0-2.0
                // 如果设置了自定义 baseUrl（如代理服务），则使用自定义地址
                .baseUrl(config.getBaseUrl())         // 自定义 API 端点
                .build();
    }
}
