package com.ruoyi.ai.factory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ModelConfig - 模型配置 POJO
 *
 * 用于承载单个模型实例所需的配置参数：
 * - apiKey：API 认证密钥（每个厂商不同）
 * - modelName：模型标识名称
 * - temperature：生成随机性控制（0.0-2.0）
 * - baseUrl：自定义 API 端点地址
 *
 * 使用 Lombok 注解简化代码：
 * - @Data：自动生成 getter/setter/toString/equals/hashCode
 * - @Builder：支持流式构建对象
 * - @NoArgsConstructor/@AllArgsConstructor：支持无参/全参构造
 */
@Data           // 自动生成 getter/setter 等方法
@Builder        // 启用 Builder 构建模式，支持链式调用
@NoArgsConstructor  // 无参构造函数（序列化需要）
@AllArgsConstructor // 全参构造函数（Builder 内部使用）
public class ModelConfig {

    /**
     * API 认证密钥
     *
     * 每个厂商的密钥不同：
     * - OpenAI: sk-xxx 格式
     * - DeepSeek: sk-xxx 格式（与 OpenAI 格式一致）
     * - DashScope: sk-xxx 格式（从阿里云控制台获取）
     */
    private String apiKey;

    /**
     * 模型名称标识
     *
     * 不同厂商的模型命名不同：
     * - OpenAI: gpt-4o, gpt-4-turbo, gpt-3.5-turbo
     * - DeepSeek: deepseek-chat, deepseek-coder, deepseek-reasoner
     * - Qwen: qwen-turbo, qwen-plus, qwen-max, qwen-long
     */
    private String modelName;

    /**
     * 生成温度参数
     *
     * 控制输出的随机性和创造性：
     * - 0.0：确定性输出，适合代码生成、结构化数据
     * - 0.7：默认推荐值，平衡准确性和创造性
     * - 2.0：最高随机性，适合创意写作
     */
    private double temperature;

    /**
     * 自定义 API 端点地址
     *
     * 用途场景：
     * - 使用代理服务转发 API 请求
     * - 使用私有化部署的模型服务
     * - 使用 OneAPI/LiteLLM 等网关
     *
     * 为 null 时使用各厂商的默认官方地址
     */
    private String baseUrl;
}
