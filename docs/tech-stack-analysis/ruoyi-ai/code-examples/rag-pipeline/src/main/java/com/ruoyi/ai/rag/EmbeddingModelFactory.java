package com.ruoyi.ai.rag;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;

/**
 * EmbeddingModelFactory - 多提供商嵌入模型工厂
 *
 * 嵌入模型的作用：
 * 将文本转换为高维向量（如 1536 维），用于：
 * 1. 文档向量化：将分块后的文本转为向量存入向量数据库
 * 2. 查询向量化：将用户问题转为向量，用于相似度检索
 *
 * 支持的嵌入模型：
 * ┌────────────────────┬───────────┬──────────────┬───────────────┐
 * │ 模型                │ 提供商     │ 维度          │ 特点           │
 * ├────────────────────┼───────────┼──────────────┼───────────────┤
 * │ text-embedding-3-small │ OpenAI │ 1536        │ 低成本，效果好  │
 * │ text-embedding-3-large │ OpenAI │ 3072        │ 高精度         │
 * │ text-embedding-v1  │ DashScope │ 1536        │ 国内直连       │
 * └────────────────────┴───────────┴──────────────┴───────────────┘
 */
public class EmbeddingModelFactory {

    // 嵌入模型提供商枚举
    public enum EmbeddingProvider {
        OPENAI,      // OpenAI 嵌入模型
        DASHSCOPE    // 阿里云 DashScope 嵌入模型
    }

    /**
     * 创建 OpenAI 嵌入模型
     *
     * @param apiKey  OpenAI API Key
     * @param modelName 模型名称，如 text-embedding-3-small
     * @return OpenAiEmbeddingModel 实例
     */
    public static EmbeddingModel createOpenAiEmbedding(String apiKey, String modelName) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("OpenAI API Key 不能为空");
        }
        return OpenAiEmbeddingModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)       // 推荐：text-embedding-3-small（性价比最高）
                .build();
    }

    /**
     * 创建 DashScope 嵌入模型（通义千问 Embedding）
     *
     * @param apiKey  DashScope API Key
     * @param modelName 模型名称，如 text-embedding-v1
     * @return DashScope 嵌入模型实例
     */
    public static EmbeddingModel createDashScopeEmbedding(String apiKey, String modelName) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("DashScope API Key 不能为空");
        }
        // 使用 LangChain4j DashScope 提供的嵌入模型
        return dev.langchain4j.model.dashscope.DashScopeEmbeddingModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                .build();
    }

    /**
     * 根据提供商类型创建嵌入模型
     *
     * @param provider  提供商标识
     * @param apiKey    API Key
     * @param modelName 模型名称
     * @return EmbeddingModel 实例
     */
    public static EmbeddingModel create(EmbeddingProvider provider,
                                        String apiKey,
                                        String modelName) {
        return switch (provider) {
            case OPENAI -> createOpenAiEmbedding(apiKey, modelName);
            case DASHSCOPE -> createDashScopeEmbedding(apiKey, modelName);
        };
    }
}
