package com.passage.strategy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * AiImageStrategy - AI 图片生成策略（VIP 功能）
 *
 * 调用 AI 图片生成 API（如 DALL-E 3、Stable Diffusion、Midjourney 等）生成图片。
 * 此策略为 VIP 专属功能，需要用户具有 VIP 身份且消耗额外算力额度。
 *
 * 策略说明：
 * - 优先级：高级策略（需要 VIP 权限）
 * - 适用场景：需要特定构图、风格统一的配图，或概念性/抽象性图片
 * - 优势：可精确控制图片内容、风格和构图
 * - 局限性：生成速度较慢（10-30秒），消耗算力额度，仅 VIP 用户可用
 *
 * 权限控制：
 * - isAvailable() 检查用户是否为 VIP，非 VIP 用户直接返回 false
 * - 避免非 VIP 用户调用产生不必要的费用
 *
 * API 调用：
 * - 兼容 OpenAI DALL-E 3 API 格式
 * - 支持自定义图片尺寸、风格和质量参数
 *
 * @author AI-Passage-Creator
 */
public class AiImageStrategy implements ImageStrategy {

    private static final Logger log = LoggerFactory.getLogger(AiImageStrategy.class);

    /** AI 图片生成 API 地址（可配置，支持 DALL-E 3 / Stable Diffusion 等） */
    private static final String AI_IMAGE_API_URL = "https://api.openai.com/v1/images/generations";

    /** 默认生成图片尺寸 */
    private static final String DEFAULT_SIZE = "1024x1024";

    /** 默认图片风格（vivid 为更鲜艳生动的风格） */
    private static final String DEFAULT_STYLE = "vivid";

    /** API Key */
    private final String apiKey;

    /** 是否为 VIP 用户 */
    private final boolean isVip;

    /** Spring RestTemplate */
    private final RestTemplate restTemplate;

    /**
     * 构造方法
     *
     * @param apiKey AI 图片生成 API 的密钥
     * @param isVip  当前用户是否为 VIP（VIP 才可使用此策略）
     */
    public AiImageStrategy(String apiKey, boolean isVip) {
        this.apiKey = apiKey;
        this.isVip = isVip;
        this.restTemplate = new RestTemplate();
        log.info("AiImageStrategy 初始化完成，VIP状态: {}", isVip);
    }

    /**
     * 调用 AI API 生成图片
     *
     * 实现步骤：
     * 1. 构建请求体（包含提示词、尺寸、风格等参数）
     * 2. 设置认证头（Bearer Token）
     * 3. 发送 POST 请求到 AI 图片生成 API
     * 4. 解析返回的 JSON 响应，提取生成的图片 URL
     *
     * @param prompt 图片描述提示词（如"一只穿着西装的猫，数字艺术风格"）
     * @return AI 生成的图片 URL
     * @throws ImageAcquisitionException 当 API 调用失败时抛出
     */
    @Override
    public String generateImage(String prompt) {
        log.info("【AI生图策略】开始生成图片，提示词: {}", prompt);

        // 前置检查：VIP 权限校验
        if (!isVip) {
            throw new ImageAcquisitionException("AI 生图为 VIP 专属功能，当前用户非 VIP");
        }

        try {
            // 步骤1：构建请求体
            // 使用 DALL-E 3 API 格式，支持 prompt、size、style 等参数
            Map<String, Object> requestBody = Map.of(
                    "model", "dall-e-3",
                    "prompt", prompt,
                    "n", 1,
                    "size", DEFAULT_SIZE,
                    "style", DEFAULT_STYLE,
                    "quality", "standard"
            );

            // 步骤2：设置 HTTP 请求头
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            // 步骤3：发送 POST 请求
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    AI_IMAGE_API_URL,
                    request,
                    Map.class
            );

            // 步骤4：解析响应
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new ImageAcquisitionException(
                        "AI 图片生成 API 返回异常: " + response.getStatusCode()
                );
            }

            // 从响应中提取生成的图片数据
            List<Map<String, Object>> data = (List<Map<String, Object>>) response.getBody().get("data");
            if (data == null || data.isEmpty()) {
                throw new ImageAcquisitionException("AI 图片生成返回为空");
            }

            // 取第一张图片的 URL
            String imageUrl = (String) data.get(0).get("url");
            log.info("【AI生图策略】成功生成图片: {}", imageUrl);

            return imageUrl;

        } catch (ImageAcquisitionException e) {
            throw e;
        } catch (Exception e) {
            throw new ImageAcquisitionException(
                    "AI 图片生成调用失败: " + e.getMessage(), e
            );
        }
    }

    /**
     * 获取策略名称
     *
     * @return "ai-image"
     */
    @Override
    public String getName() {
        return "ai-image";
    }

    /**
     * 检查策略是否可用
     *
     * 可用条件（同时满足）：
     * 1. API Key 已配置
     * 2. 当前用户为 VIP
     *
     * @return true 表示 AI 生图策略可用
     */
    @Override
    public boolean isAvailable() {
        boolean available = isVip && apiKey != null && !apiKey.isEmpty();
        if (!available) {
            log.warn("【AI生图策略】不可用 - VIP状态: {}, API Key已配置: {}",
                    isVip, apiKey != null && !apiKey.isEmpty());
        }
        return available;
    }
}