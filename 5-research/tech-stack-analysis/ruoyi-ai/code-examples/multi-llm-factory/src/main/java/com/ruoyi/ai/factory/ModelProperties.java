package com.ruoyi.ai.factory;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * ModelProperties - Spring 配置属性绑定类
 *
 * 使用 @ConfigurationProperties 将 application.yml 中的多层嵌套配置
 * 自动绑定到 Java 对象，避免手动读取 Environment
 *
 * 配置映射关系：
 *   model.provider       → provider
 *   model.openai.api-key → openai.apiKey
 *   model.openai.model   → openai.modelName
 *   ...以此类推
 *
 * 支持 kebab-case（yml 常用）→ camelCase（Java 常用）自动转换
 */
@Data       // 自动生成 getter/setter
@Configuration  // 注册为 Spring 配置类
@ConfigurationProperties(prefix = "model")  // 绑定 model. 前缀的所有配置
public class ModelProperties {

    /**
     * 当前激活的模型厂商标识
     *
     * 决定使用哪个 ModelFactory 实现：
     * - openai：使用 OpenAI 或兼容服务
     * - deepseek：使用 DeepSeek 模型
     * - qwen：使用通义千问模型
     *
     * 对应配置：model.provider
     */
    private String provider;

    /**
     * OpenAI 模型配置块
     * 对应配置：model.openai.*
     */
    private ModelConfig openai = new ModelConfig();

    /**
     * DeepSeek 模型配置块
     * 对应配置：model.deepseek.*
     */
    private ModelConfig deepseek = new ModelConfig();

    /**
     * 通义千问模型配置块
     * 对应配置：model.qwen.*
     */
    private ModelConfig qwen = new ModelConfig();

    /**
     * 根据 provider 标识获取对应的模型配置
     *
     * 通过反射或 switch 匹配 provider 字段值
     * 返回对应厂商的配置对象
     *
     * @param provider 厂商标识（openai/deepseek/qwen）
     * @return 对应厂商的 ModelConfig，未找到返回 null
     */
    public ModelConfig getConfigByProvider(String provider) {
        return switch (provider.toLowerCase()) {
            case "openai" -> openai;       // 返回 OpenAI 配置
            case "deepseek" -> deepseek;   // 返回 DeepSeek 配置
            case "qwen" -> qwen;           // 返回通义千问配置
            default -> throw new IllegalArgumentException(
                "不支持的模型提供商: " + provider + "，支持: openai, deepseek, qwen"
            );
        };
    }
}
