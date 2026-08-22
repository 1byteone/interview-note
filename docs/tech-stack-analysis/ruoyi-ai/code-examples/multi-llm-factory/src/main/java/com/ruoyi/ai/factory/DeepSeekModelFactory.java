package com.ruoyi.ai.factory;

import dev.langchain4j.model.chat.ChatLanguageModel;
import org.springframework.stereotype.Component;

/**
 * DeepSeekModelFactory - DeepSeek 模型工厂实现
 *
 * 负责创建 DeepSeek API 模型实例
 * DeepSeek 是国产大模型厂商，API 兼容 OpenAI 格式
 *
 * 支持模型：
 * - deepseek-chat：通用对话模型
 * - deepseek-coder：代码专用模型
 * - deepseek-reasoner：推理增强模型（R1）
 *
 * API 特点：
 * - Base URL: https://api.deepseek.com
 * - 兼容 OpenAI API 格式
 * - 中文优化效果好
 */
@Component  // 注册为 Spring Bean
public class DeepSeekModelFactory implements ModelFactory {

    /**
     * 工厂标识名 - 对应配置：model.provider: deepseek
     */
    private static final String PROVIDER_NAME = "deepseek";

    /**
     * DeepSeek 官方 API 地址
     * 注意：DeepSeek 的 API 格式与 OpenAI 完全兼容
     */
    private static final String DEFAULT_BASE_URL = "https://api.deepseek.com";

    /**
     * 返回此工厂负责处理的厂商标识
     *
     * @return "deepseek"
     */
    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }

    /**
     * 创建 DeepSeek 聊天模型实例
     *
     * 实现说明：
     * DeepSeek API 兼容 OpenAI 格式，所以这里使用 OpenAiChatModel
     * 只需要修改 Base URL 指向 DeepSeek 服务器即可
     *
     * @param config 包含 API Key 和模型参数的配置
     * @return DeepSeek 聊天模型实例
     */
    @Override
    public ChatLanguageModel createModel(ModelConfig config) {
        // 校验 API Key 必须存在
        if (config.getApiKey() == null || config.getApiKey().isBlank()) {
            throw new IllegalArgumentException(
                "DeepSeek API Key 不能为空，请在配置文件中设置 deepseek.api-key"
            );
        }

        // 使用 OpenAI 兼容客户端创建 DeepSeek 模型
        // DeepSeek 官方说明：API 与 OpenAI 完全兼容，可直接使用 OpenAI SDK 接入
        return dev.langchain4j.model.openai.OpenAiChatModel.builder()
                .apiKey(config.getApiKey())
                .modelName(config.getModelName())       // 默认为 deepseek-chat
                .temperature(config.getTemperature())   // 推荐 0.7 用于通用对话
                // 使用 DeepSeek 官方 API 地址（如果配置文件未指定则使用默认值）
                .baseUrl(config.getBaseUrl() != null && !config.getBaseUrl().isBlank()
                        ? config.getBaseUrl()
                        : DEFAULT_BASE_URL)
                .build();
    }
}
