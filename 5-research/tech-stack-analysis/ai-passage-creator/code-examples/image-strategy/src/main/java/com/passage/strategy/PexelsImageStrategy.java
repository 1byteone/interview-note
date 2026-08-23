package com.passage.strategy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * PexelsImageStrategy - Pexels 免费图库图片搜索策略
 *
 * 调用 Pexels 开放 API 搜索与提示词匹配的免费图片。
 * Pexels 提供海量高质量免费图片，可作为文章配图的首选来源。
 *
 * 策略说明：
 * - 优先级：首选策略（免费、无需 AI 算力）
 * - 适用场景：需要实景照片、自然风光、人物等真实感图片
 * - 局限性：无法生成特定构图或概念性图片，不适合图表/示意图
 *
 * 降级处理：
 * - API Key 未配置时，isAvailable() 返回 false，触发自动降级
 * - API 调用失败时，抛出 ImageAcquisitionException 由上层处理
 *
 * @author AI-Passage-Creator
 */
public class PexelsImageStrategy implements ImageStrategy {

    private static final Logger log = LoggerFactory.getLogger(PexelsImageStrategy.class);

    /** Pexels API 基础地址 */
    private static final String PEXELS_API_BASE = "https://api.pexels.com/v1";

    /** 搜索接口路径 */
    private static final String SEARCH_ENDPOINT = "/search";

    /** 每次搜索返回的图片数量 */
    private static final int PER_PAGE = 5;

    /** Pexels API Key（实际使用时从配置中心或环境变量获取） */
    private final String apiKey;

    /** Spring RestTemplate 用于发送 HTTP 请求 */
    private final RestTemplate restTemplate;

    /**
     * 构造方法
     *
     * @param apiKey Pexels API Key，从 application.yml 或环境变量注入
     */
    public PexelsImageStrategy(String apiKey) {
        this.apiKey = apiKey;
        this.restTemplate = new RestTemplate();
        log.info("PexelsImageStrategy 初始化完成");
    }

    /**
     * 从 Pexels 搜索与提示词匹配的图片
     *
     * 实现步骤：
     * 1. 构建查询参数，调用 Pexels /search 接口
     * 2. 解析返回的 JSON 响应，提取第一张图片的原始尺寸 URL
     * 3. 如果搜索结果为空，抛出异常触发降级
     *
     * @param prompt 图片描述提示词（如"technology workspace"）
     * @return 图片的原始尺寸 URL
     * @throws ImageAcquisitionException 当搜索失败或结果为空时抛出
     */
    @Override
    public String generateImage(String prompt) {
        log.info("【Pexels策略】开始搜索图片，关键词: {}", prompt);

        try {
            // 构建请求 URL，对查询参数进行 URL 编码
            String url = PEXELS_API_BASE + SEARCH_ENDPOINT
                    + "?query=" + java.net.URLEncoder.encode(prompt, "UTF-8")
                    + "&per_page=" + PER_PAGE;

            // 设置认证请求头：Pexels 要求通过 Authorization 头传递 API Key
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", apiKey);
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            // 发送 GET 请求，携带 API Key 认证头
            ResponseEntity<Map> response = restTemplate.exchange(
                    url,
                    org.springframework.http.HttpMethod.GET,
                    entity,
                    Map.class
            );

            // 检查响应状态
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new ImageAcquisitionException(
                        "Pexels API 返回异常状态码: " + response.getStatusCode()
                );
            }

            // 从响应体中提取图片列表
            List<Map<String, Object>> photos = (List<Map<String, Object>>) response.getBody().get("photos");

            // 检查搜索结果是否为空
            if (photos == null || photos.isEmpty()) {
                throw new ImageAcquisitionException(
                        "Pexels 搜索未找到匹配图片，关键词: " + prompt
                );
            }

            // 取第一张图片的原始尺寸 URL
            Map<String, Object> firstPhoto = photos.get(0);
            Map<String, String> src = (Map<String, String>) firstPhoto.get("src");
            String imageUrl = src.get("original");

            log.info("【Pexels策略】成功获取图片: {}", imageUrl);
            return imageUrl;

        } catch (ImageAcquisitionException e) {
            // 业务异常直接抛出，由上层处理
            throw e;
        } catch (Exception e) {
            // 网络异常等非业务异常，包装后抛出
            throw new ImageAcquisitionException(
                    "Pexels API 调用失败: " + e.getMessage(), e
            );
        }
    }

    /**
     * 获取策略名称
     *
     * @return "pexels"
     */
    @Override
    public String getName() {
        return "pexels";
    }

    /**
     * 检查策略是否可用
     *
     * 可用条件：
     * 1. API Key 已配置（非 null 且非空）
     *
     * @return true 表示 Pexels 策略可用
     */
    @Override
    public boolean isAvailable() {
        return apiKey != null && !apiKey.isEmpty();
    }
}