package com.ruoyi.ai.factory;

import dev.langchain4j.model.chat.ChatLanguageModel;
import org.springframework.stereotype.Component;

/**
 * QwenModelFactory - 通义千问模型工厂实现
 *
 * 负责创建阿里云 DashScope 平台的通义千问模型实例
 *
 * 支持模型：
 * - qwen-turbo：快速推理模型，响应延迟低
 * - qwen-plus：均衡模型，性价比高
 * - qwen-max：最强能力模型，支持长上下文
 * - qwen-long：长上下文模型，最高支持 100 万 tokens
 *
 * API 特点：
 * - 使用阿里云 DashScope 平台
 * - 需要单独的 DashScope SDK（非 OpenAI 格式）
 * - 国内直连，无需翻墙
 */
@Component  // 注册为 Spring Bean，自动注入到 Factory Registry
public class QwenModelFactory implements ModelFactory {

    /**
     * 工厂标识名 - 对应配置：model.provider: qwen
     */
    private static final String PROVIDER_NAME = "qwen";

    /**
     * 返回此工厂负责处理的厂商标识
     *
     * @return "qwen"
     */
    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }

    /**
     * 创建通义千问聊天模型实例
     *
     * 与 OpenAI/DeepSeek 不同，通义千问使用 DashScope 专用 SDK
     * 需要引入 langchain4j-dashscope 依赖
     *
     * @param config 包含 API Key（DashScope API Key）和模型参数
     * @return 通义千问聊天模型实例
     */
    @Override
    public ChatLanguageModel createModel(ModelConfig config) {
        // DashScope API Key 也称为 Access Key，从阿里云控制台获取
        if (config.getApiKey() == null || config.getApiKey().isBlank()) {
            throw new IllegalArgumentException(
                "DashScope API Key 不能为空，请在配置文件中设置 qwen.api-key"
            );
        }

        // 使用 LangChain4j 提供的 DashScope 模型构建器
        // 注意：这里使用的是 langchain4j-dashscope 专用类，而非 OpenAiChatModel
        return dev.langchain4j.model.dashscope.DashScopeChatModel.builder()
                .apiKey(config.getApiKey())             // DashScope API Key
                .modelName(config.getModelName())       // 模型名称，如 qwen-plus
                .temperature(config.getTemperature())   // 生成温度
                .build();
    }
}
