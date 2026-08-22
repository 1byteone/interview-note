package com.zznursing.ai;

import com.baidu.cloud.qianfan.Qianfan;
import com.baidu.cloud.qianfan.model.ChatCompletion;
import com.baidu.cloud.qianfan.model.ChatCompletionRequest;
import com.baidu.cloud.qianfan.model.ChatCompletionResponse;
import com.baidu.cloud.qianfan.model.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;

/**
 * 百度千帆AI客户端
 *
 * 功能说明：
 * 1. 封装百度千帆大模型API调用
 * 2. 支持流式和非流式对话
 * 3. 管理API密钥和配置
 * 4. 提供统一的AI调用接口
 *
 * 支持的模型：
 * - ERNIE-Bot-4.0: 百度最强对话模型
 * - ERNIE-Bot-3.5: 性价比高的通用模型
 * - ERNIE-Speed: 轻量级快速响应模型
 *
 * 使用场景：
 * - 智慧养老系统中的智能问答
 * - 健康咨询对话
 * - 养老知识科普
 *
 * @author zznursing
 * @since 1.0.0
 */
@Component
public class QianfanAIClient {

    private static final Logger logger = LoggerFactory.getLogger(QianfanAIClient.class);

    /**
     * 百度千帆API Access Key
     * 从配置文件中读取
     */
    @Value("${zznursing.qianfan.access-key}")
    private String accessKey;

    /**
     * 百度千帆API Secret Key
     * 从配置文件中读取
     */
    @Value("${zznursing.qianfan.secret-key}")
    private String secretKey;

    /**
     * 默认使用的模型名称
     * 可选值：ERNIE-Bot-4.0, ERNIE-Bot-3.5, ERNIE-Speed
     */
    @Value("${zznursing.qianfan.model:ERNIE-Bot-3.5}")
    private String defaultModel;

    /**
     * 温度参数 (0.0 - 1.0)
     * 值越高，回答越随机；值越低，回答越确定
     */
    @Value("${zznursing.qianfan.temperature:0.7}")
    private double temperature;

    /**
     * 最大输出token数
     */
    @Value("${zznursing.qianfan.max-tokens:2000}")
    private int maxTokens;

    /**
     * 百度千帆客户端实例
     */
    private Qianfan qianfanClient;

    /**
     * 初始化百度千帆客户端
     *
     * 功能：在Bean初始化时创建Qianfan客户端实例
     */
    @PostConstruct
    public void init() {
        logger.info("初始化百度千帆AI客户端...");

        // 创建百度千帆客户端
        qianfanClient = Qianfan.builder()
                .accessKey(accessKey)
                .secretKey(secretKey)
                .build();

        logger.info("百度千帆AI客户端初始化完成，默认模型: {}", defaultModel);
    }

    /**
     * 发送对话请求（非流式）
     *
     * 功能：发送用户消息，获取AI回复
     *
     * @param userMessage 用户消息
     * @return AI回复内容
     */
    public String chat(String userMessage) {
        return chat(userMessage, defaultModel);
    }

    /**
     * 发送对话请求（指定模型）
     *
     * @param userMessage 用户消息
     * @param modelName 模型名称
     * @return AI回复内容
     */
    public String chat(String userMessage, String modelName) {
        logger.info("发送AI对话请求: model={}, message={}", modelName,
                userMessage.length() > 100 ? userMessage.substring(0, 100) + "..." : userMessage);

        try {
            // 构建消息列表
            List<Message> messages = new ArrayList<>();
            messages.add(Message.builder()
                    .role("user")
                    .content(userMessage)
                    .build());

            // 构建请求
            ChatCompletionRequest request = ChatCompletionRequest.builder()
                    .model(modelName)
                    .messages(messages)
                    .temperature(temperature)
                    .maxTokens(maxTokens)
                    .build();

            // 调用API
            ChatCompletionResponse response = qianfanClient.chatCompletion(request);

            // 提取回复内容
            if (response.getChoices() != null && !response.getChoices().isEmpty()) {
                String reply = response.getChoices().get(0).getMessage().getContent();
                logger.info("AI回复成功: length={}", reply.length());
                return reply;
            } else {
                logger.warn("AI回复为空");
                return "抱歉，我暂时无法回答您的问题。";
            }

        } catch (Exception e) {
            logger.error("调用百度千帆API失败: {}", e.getMessage(), e);
            return "抱歉，AI服务暂时不可用，请稍后再试。";
        }
    }

    /**
     * 发送带历史上下文的对话请求
     *
     * 功能：支持多轮对话，保持上下文连贯
     *
     * @param messages 历史消息列表
     * @return AI回复内容
     */
    public String chatWithHistory(List<Message> messages) {
        return chatWithHistory(messages, defaultModel);
    }

    /**
     * 发送带历史上下文的对话请求（指定模型）
     *
     * @param messages 历史消息列表
     * @param modelName 模型名称
     * @return AI回复内容
     */
    public String chatWithHistory(List<Message> messages, String modelName) {
        logger.info("发送带上下文的AI对话请求: model={}, messageCount={}", modelName, messages.size());

        try {
            // 构建请求
            ChatCompletionRequest request = ChatCompletionRequest.builder()
                    .model(modelName)
                    .messages(messages)
                    .temperature(temperature)
                    .maxTokens(maxTokens)
                    .build();

            // 调用API
            ChatCompletionResponse response = qianfanClient.chatCompletion(request);

            // 提取回复内容
            if (response.getChoices() != null && !response.getChoices().isEmpty()) {
                String reply = response.getChoices().get(0).getMessage().getContent();
                logger.info("AI回复成功: length={}", reply.length());
                return reply;
            } else {
                logger.warn("AI回复为空");
                return "抱歉，我暂时无法回答您的问题。";
            }

        } catch (Exception e) {
            logger.error("调用百度千帆API失败: {}", e.getMessage(), e);
            return "抱歉，AI服务暂时不可用，请稍后再试。";
        }
    }

    /**
     * 发送流式对话请求
     *
     * 功能：以流式方式获取AI回复，适用于实时显示场景
     *
     * @param userMessage 用户消息
     * @param callback 流式回复回调函数
     */
    public void chatStream(String userMessage, StreamCallback callback) {
        chatStream(userMessage, defaultModel, callback);
    }

    /**
     * 发送流式对话请求（指定模型）
     *
     * @param userMessage 用户消息
     * @param modelName 模型名称
     * @param callback 流式回复回调函数
     */
    public void chatStream(String userMessage, String modelName, StreamCallback callback) {
        logger.info("发送流式AI对话请求: model={}", modelName);

        try {
            // 构建消息列表
            List<Message> messages = new ArrayList<>();
            messages.add(Message.builder()
                    .role("user")
                    .content(userMessage)
                    .build());

            // 构建请求，启用流式输出
            ChatCompletionRequest request = ChatCompletionRequest.builder()
                    .model(modelName)
                    .messages(messages)
                    .temperature(temperature)
                    .maxTokens(maxTokens)
                    .stream(true)  // 启用流式输出
                    .build();

            // 调用流式API
            qianfanClient.chatCompletionStream(request)
                    .forEachRemaining(response -> {
                        if (response.getChoices() != null && !response.getChoices().isEmpty()) {
                            String content = response.getChoices().get(0).getDelta().getContent();
                            if (content != null) {
                                callback.onStream(content);
                            }
                        }
                    });

            // 流式输出完成
            callback.onComplete();

        } catch (Exception e) {
            logger.error("调用流式API失败: {}", e.getMessage(), e);
            callback.onError(e);
        }
    }

    /**
     * 流式回复回调接口
     */
    public interface StreamCallback {
        /**
         * 接收流式内容片段
         *
         * @param content 内容片段
         */
        void onStream(String content);

        /**
         * 流式输出完成
         */
        void onComplete();

        /**
         * 发生错误
         *
         * @param error 异常信息
         */
        void onError(Throwable error);
    }

    /**
     * 获取默认模型名称
     *
     * @return 模型名称
     */
    public String getDefaultModel() {
        return defaultModel;
    }

    /**
     * 获取温度参数
     *
     * @return 温度值
     */
    public double getTemperature() {
        return temperature;
    }

    /**
     * 获取最大token数
     *
     * @return 最大token数
     */
    public int getMaxTokens() {
        return maxTokens;
    }
}
