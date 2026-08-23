# 百度千帆 AI 集成

> zznursing 项目集成百度千帆大模型平台，提供智能问答、健康建议、异常预警等 AI 能力。核心场景：老人家属通过微信小程序向 AI 咨询健康问题，系统结合老人实时健康数据生成个性化建议。

---

## 一、百度千帆平台概览

### 1.1 平台架构

```
┌──────────────────────────────────────────────────────────────────┐
│                        微信小程序（家属端）                          │
│  用户输入问题 → WebSocket 连接 → 流式展示 AI 回复                    │
└──────────────────────────────┬───────────────────────────────────┘
                               │
                               ▼
┌──────────────────────────────────────────────────────────────────┐
│                      Spring Boot AI 服务                          │
│  1. 组装 Prompt（角色 + 上下文 + 老人健康数据 + 用户问题）            │
│  2. 调用千帆 API（流式 / 非流式）                                    │
│  3. 后处理（敏感词过滤、格式校验）                                    │
└──────────────────────────────┬───────────────────────────────────┘
                               │
                               ▼
┌──────────────────────────────────────────────────────────────────┐
│                      百度千帆大模型平台                               │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐              │
│  │ 文心一言 4.0 │  │ 文心一言 3.5 │  │ 第三方模型   │              │
│  │ (旗舰模型)   │  │ (经济模型)   │  │ (Llama等)   │              │
│  └─────────────┘  └─────────────┘  └─────────────┘              │
│  模型路由 → Prompt 优化 → 安全审核 → 结果返回                       │
└──────────────────────────────────────────────────────────────────┘
```

### 1.2 核心能力选型

| 能力分类 | 场景 | 千帆模型 | 选型理由 |
|----------|------|----------|----------|
| **智能问答** | 健康咨询、用药建议 | 文心一言 4.0 | 中文理解能力强，医疗知识库完善 |
| **健康建议生成** | 饮食推荐、运动计划 | 文心一言 3.5 | 成本低，满足基本生成需求 |
| **异常预警描述** | 跌倒检测后的伤情评估 | 文心一言 4.0 | 需要高精度判断 |
| **报表摘要** | 月度健康报告总结 | 文心一言 3.5 | 结构化输出，无需流式 |
| **情感分析** | 老人情绪状态判断 | 文心一言 3.5 | 简单分类任务 |

---

## 二、千帆 API 集成

### 2.1 依赖配置

```xml
<!-- pom.xml 千帆 API 依赖 -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-webflux</artifactId>
</dependency>
<dependency>
    <groupId>com.fasterxml.jackson.core</groupId>
    <artifactId>jackson-databind</artifactId>
</dependency>
```

### 2.2 配置文件

```yaml
# application.yml
qianfan:
  # 百度千帆 API 配置
  api:
    # API Key 和 Secret Key，用于获取 Access Token
    api-key: ${QIANFAN_API_KEY}
    secret-key: ${QIANFAN_SECRET_KEY}
    # 千帆 API 基础 URL
    base-url: https://aip.baidubce.com
    # 获取 Access Token 的接口
    token-url: /oauth/2.0/token
    # 文心一言 4.0 对话接口
    chat-url: /rpc/2.0/ai_custom/v1/wenxinworkshop/chat/completions_pro
    # 文心一言 3.5 对话接口（经济型）
    chat-url-lite: /rpc/2.0/ai_custom/v1/wenxinworkshop/chat/eb-instant

  # 对话默认参数
  chat:
    # 温度参数，控制回复随机性，0-1，医疗场景建议低值
    temperature: 0.3
    # 最大输出 Token 数
    max-tokens: 1024
    # 核采样参数
    top-p: 0.8
    # 重复惩罚系数
    penalty-score: 1.0

  # 流式输出配置
  streaming:
    # 是否启用流式输出
    enabled: true
    # SSE 超时时间（毫秒）
    timeout: 60000
```

### 2.3 千帆 API 客户端

```java
// QianfanAiClient.java
// 百度千帆 API 客户端 —— 封装对话、流式对话、Token 管理等核心能力
package com.zznursing.ai.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zznursing.ai.dto.QianfanChatRequest;
import com.zznursing.ai.dto.QianfanChatResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 百度千帆 API 客户端
 * 封装文心一言对话接口的调用，支持流式和非流式两种模式
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QianfanAiClient {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    @Value("${qianfan.api.base-url}")
    private String baseUrl;

    @Value("${qianfan.api.chat-url}")
    private String chatUrl;

    @Value("${qianfan.api.chat-url-lite}")
    private String chatUrlLite;

    /** 缓存的 Access Token，使用 AtomicReference 保证线程安全 */
    private final AtomicReference<String> accessToken = new AtomicReference<>(null);

    /** Token 过期时间戳 */
    private volatile long tokenExpireTime = 0;

    /**
     * 获取 Access Token（带缓存）
     * 千帆 API 使用 OAuth 2.0 鉴权，Access Token 有效期为 30 天
     * 缓存 Token 避免每次请求都重新获取
     */
    public String getAccessToken() {
        // 如果 Token 未过期，直接返回缓存
        if (System.currentTimeMillis() < tokenExpireTime) {
            return accessToken.get();
        }

        // 同步获取新 Token（实际项目应使用分布式锁或定时刷新）
        synchronized (this) {
            // 双重检查，避免重复获取
            if (System.currentTimeMillis() < tokenExpireTime) {
                return accessToken.get();
            }

            try {
                // 调用千帆 OAuth 接口获取 Token
                String response = webClient.get()
                        .uri(baseUrl + "/oauth/2.0/token?grant_type=client_credentials" +
                                "&client_id={apiKey}&client_secret={secretKey}",
                                getApiKey(), getSecretKey())
                        .retrieve()
                        .bodyToMono(String.class)
                        .block(Duration.ofSeconds(10));

                JsonNode root = objectMapper.readTree(response);
                String token = root.path("access_token").asText();
                int expiresIn = root.path("expires_in").asInt(2592000); // 默认 30 天

                // 缓存 Token，提前 1 天过期以留有余量
                this.tokenExpireTime = System.currentTimeMillis() + (expiresIn - 86400) * 1000L;
                this.accessToken.set(token);

                log.info("千帆 Access Token 刷新成功，有效期: {} 秒", expiresIn);
                return token;

            } catch (Exception e) {
                log.error("获取千帆 Access Token 失败", e);
                throw new RuntimeException("获取千帆 Access Token 失败", e);
            }
        }
    }

    // 占位方法，实际从配置读取
    private String getApiKey() { return System.getenv("QIANFAN_API_KEY"); }
    private String getSecretKey() { return System.getenv("QIANFAN_SECRET_KEY"); }

    /**
     * 非流式对话（普通对话）
     * 适用于简单问答、健康建议生成等不需要逐字展示的场景
     *
     * @param request 对话请求
     * @return 完整回复内容
     */
    public QianfanChatResponse chat(QianfanChatRequest request) {
        try {
            String token = getAccessToken();
            String url = baseUrl + chatUrl + "?access_token=" + token;

            // 构建请求体
            String requestBody = buildRequestBody(request);

            // 调用千帆对话 API
            String response = webClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(Duration.ofSeconds(30));

            // 解析响应
            JsonNode root = objectMapper.readTree(response);
            QianfanChatResponse result = new QianfanChatResponse();

            // 提取回复内容
            result.setContent(root.path("result").asText());
            // 提取 Token 用量
            result.setPromptTokens(root.path("usage").path("prompt_tokens").asInt());
            result.setCompletionTokens(root.path("usage").path("completion_tokens").asInt());
            result.setTotalTokens(root.path("usage").path("total_tokens").asInt());

            log.info("千帆对话完成 - promptTokens: {}, completionTokens: {}",
                    result.getPromptTokens(), result.getCompletionTokens());

            return result;

        } catch (Exception e) {
            log.error("千帆对话调用失败", e);
            throw new RuntimeException("AI 服务调用失败，请稍后重试", e);
        }
    }

    /**
     * 流式对话（SSE 流式输出）
     * 适用于微信小程序中逐字展示 AI 回复，提升用户体验
     * 使用 Spring WebFlux Flux 实现响应式流
     *
     * @param request 对话请求
     * @return 流式回复 Flux
     */
    public Flux<String> chatStream(QianfanChatRequest request) {
        String token = getAccessToken();
        String url = baseUrl + chatUrl + "?access_token=" + token;

        // 启用流式输出
        request.setStream(true);

        String requestBody = buildRequestBody(request);

        // 使用 WebClient 发起 SSE 请求，返回 Flux 流
        return webClient.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToFlux(String.class)
                // 解析 SSE 数据流，每个 data 片段是一个 JSON 字符串
                .filter(data -> data.startsWith("data: "))
                .map(data -> data.substring(6)) // 去掉 "data: " 前缀
                .map(this::extractStreamContent)
                // 设置超时
                .timeout(Duration.ofSeconds(60))
                .doOnError(e -> log.error("流式对话异常", e));
    }

    /**
     * 从流式响应片段中提取文本内容
     * 千帆 SSE 格式：data: {"result":"部","is_end":false}
     */
    private String extractStreamContent(String jsonData) {
        try {
            JsonNode node = objectMapper.readTree(jsonData);
            return node.path("result").asText();
        } catch (Exception e) {
            log.warn("解析流式响应片段失败: {}", jsonData);
            return "";
        }
    }

    /**
     * 构建千帆 API 请求体
     * 将内部请求 DTO 转换为千帆要求的 JSON 格式
     */
    private String buildRequestBody(QianfanChatRequest request) {
        try {
            return objectMapper.writeValueAsString(request);
        } catch (Exception e) {
            throw new RuntimeException("构建请求体失败", e);
        }
    }
}
```

### 2.4 对话请求/响应 DTO

```java
// QianfanChatRequest.java
// 千帆对话请求体 —— 包含消息列表、参数配置、是否流式等
package com.zznursing.ai.dto;

import lombok.Data;

import java.util.List;

/**
 * 千帆对话请求体
 * 对应文心一言 API 的请求参数
 */
@Data
public class QianfanChatRequest {

    /** 消息列表，包含角色和内容 */
    private List<Message> messages;

    /** 是否流式输出 */
    private boolean stream;

    /** 温度参数，0-1，越低越确定 */
    private Double temperature;

    /** 最大输出 Token 数 */
    @JsonProperty("max_tokens")
    private Integer maxTokens;

    /** 核采样参数 */
    @JsonProperty("top_p")
    private Double topP;

    /** 重复惩罚系数 */
    @JsonProperty("penalty_score")
    private Double penaltyScore;

    /** 用户标识，用于追踪和安全管理 */
    @JsonProperty("user_id")
    private String userId;

    /**
     * 消息内部类
     * role: user(用户) / assistant(AI) / system(系统)
     */
    @Data
    public static class Message {
        private String role;
        private String content;
    }
}
```

```java
// QianfanChatResponse.java
// 千帆对话响应体 —— 包含回复内容、Token 用量等
package com.zznursing.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 千帆对话响应体
 */
@Data
public class QianfanChatResponse {

    /** AI 回复内容 */
    private String content;

    /** 对话 ID */
    private String id;

    /** 是否结束 */
    @JsonProperty("is_end")
    private Boolean isEnd;

    /** Prompt 消耗的 Token 数 */
    @JsonProperty("prompt_tokens")
    private Integer promptTokens;

    /** 回复消耗的 Token 数 */
    @JsonProperty("completion_tokens")
    private Integer completionTokens;

    /** 总 Token 数 */
    @JsonProperty("total_tokens")
    private Integer totalTokens;
}
```

---

## 三、AI 应用场景实现

### 3.1 健康咨询对话管理

```java
// HealthAdvisorService.java
// 健康顾问服务 —— 组合老人健康数据 + AI 能力，生成个性化健康建议
package com.zznursing.ai.service;

import com.zznursing.ai.client.QianfanAiClient;
import com.zznursing.ai.dto.QianfanChatRequest;
import com.zznursing.ai.dto.QianfanChatResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

/**
 * 健康顾问服务
 * 核心业务：将老人实时健康数据 + 用户问题组装为 Prompt，
 * 调用千帆 API 生成个性化健康建议
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HealthAdvisorService {

    private final QianfanAiClient qianfanClient;

    /**
     * 生成健康建议（非流式）
     * 适用于后台生成健康报告摘要等场景
     *
     * @param elderlyId 老人ID
     * @param question 用户问题，如"父亲血压偏高怎么办？"
     * @return AI 健康建议
     */
    public QianfanChatResponse generateHealthAdvice(String elderlyId, String question) {
        // 步骤1：构建系统 Prompt，设定 AI 角色
        String systemPrompt = buildSystemPrompt();

        // 步骤2：获取老人最近 7 天的健康数据摘要
        String healthContext = buildHealthContext(elderlyId);

        // 步骤3：组装完整消息列表
        QianfanChatRequest request = new QianfanChatRequest();
        List<QianfanChatRequest.Message> messages = new ArrayList<>();

        // 系统消息：定义 AI 角色和行为规范
        QianfanChatRequest.Message systemMsg = new QianfanChatRequest.Message();
        systemMsg.setRole("system");
        systemMsg.setContent(systemPrompt);
        messages.add(systemMsg);

        // 用户消息：包含健康上下文 + 用户问题
        QianfanChatRequest.Message userMsg = new QianfanChatRequest.Message();
        userMsg.setRole("user");
        // 将健康数据上下文和问题拼接在一起
        userMsg.setContent("【老人近期健康数据】\n" + healthContext + "\n\n【用户问题】\n" + question);
        messages.add(userMsg);

        request.setMessages(messages);
        request.setTemperature(0.3);  // 医疗场景使用低温度，回复更保守
        request.setMaxTokens(1024);

        // 步骤4：调用千帆 API
        return qianfanClient.chat(request);
    }

    /**
     * 流式健康咨询（流式输出）
     * 适用于微信小程序中逐字展示 AI 回复
     * 使用 Server-Sent Events 实现流式推送
     *
     * @param elderlyId 老人ID
     * @param question 用户问题
     * @return Flux 流式返回 AI 回复片段
     */
    public Flux<String> streamHealthAdvice(String elderlyId, String question) {
        String systemPrompt = buildSystemPrompt();
        String healthContext = buildHealthContext(elderlyId);

        QianfanChatRequest request = new QianfanChatRequest();
        List<QianfanChatRequest.Message> messages = new ArrayList<>();

        // 系统消息
        QianfanChatRequest.Message systemMsg = new QianfanChatRequest.Message();
        systemMsg.setRole("system");
        systemMsg.setContent(systemPrompt);
        messages.add(systemMsg);

        // 用户消息
        QianfanChatRequest.Message userMsg = new QianfanChatRequest.Message();
        userMsg.setRole("user");
        userMsg.setContent("【老人近期健康数据】\n" + healthContext + "\n\n【用户问题】\n" + question);
        messages.add(userMsg);

        request.setMessages(messages);
        request.setTemperature(0.3);
        request.setMaxTokens(1024);
        request.setStream(true);  // 启用流式

        // 调用千帆流式 API
        return qianfanClient.chatStream(request);
    }

    /**
     * 构建系统 Prompt
     * 定义 AI 的健康顾问角色，限制回答范围，确保安全性
     */
    private String buildSystemPrompt() {
        return "你是一个专业的养老健康顾问，名叫"小护"。请遵循以下规则：\n"
                + "1. 你面对的是老人家属，用温和、通俗的语言回答\n"
                + "2. 健康建议仅供参考，不能替代医生诊断，必要时建议就医\n"
                + "3. 基于老人近期的健康数据给出个性化建议\n"
                + "4. 回答简洁明了，不超过 300 字\n"
                + "5. 涉及用药建议时，必须强调"请遵医嘱"\n"
                + "6. 如果发现异常数据（如心率过高），建议立即联系值班医生";
    }

    /**
     * 构建老人健康数据上下文
     * 从 Redis/MySQL 获取老人最近 7 天的健康数据摘要
     * 作为 AI 的参考信息，帮助生成个性化建议
     */
    private String buildHealthContext(String elderlyId) {
        // 实际项目中从数据库查询老人最近 7 天的健康数据
        // 包含：心率均值、血压均值、体温均值、异常次数等
        // 这里返回模拟数据
        return String.format(
                "老人ID: %s\n" +
                "最近7天健康摘要:\n" +
                "- 心率: 平均 72 次/分, 范围 58-95 次/分\n" +
                "- 血压: 收缩压 125-145 mmHg, 舒张压 75-90 mmHg\n" +
                "- 体温: 36.3-36.8°C\n" +
                "- 步数: 日均 3500 步\n" +
                "- 异常告警: 最近 7 天无跌倒告警, 心率偏高告警 2 次\n" +
                "- 当前状态: 在线, 设备电量 78%%",
                elderlyId
        );
    }
}
```

### 3.2 对话历史管理

```java
// ConversationManager.java
// 对话历史管理器 —— 管理 AI 对话上下文，支持多轮对话
package com.zznursing.ai.service;

import com.zznursing.ai.dto.QianfanChatRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 对话历史管理器
 * 使用 Redis 维护 AI 对话上下文，支持多轮对话
 * 每轮对话保留最近 10 条消息，超出则丢弃最早的
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationManager {

    private final StringRedisTemplate stringRedisTemplate;

    /** 对话上下文在 Redis 中的 Key 前缀 */
    private static final String CONVERSATION_PREFIX = "ai:conversation:";

    /** 最大保留的消息轮数 */
    private static final int MAX_MESSAGES = 10;

    /** 对话过期时间（小时） */
    private static final int CONVERSATION_TTL_HOURS = 24;

    /**
     * 获取对话上下文
     * 从 Redis 中读取历史消息，组装为千帆 API 的消息列表
     *
     * @param userId 用户标识
     * @return 历史消息列表
     */
    public List<QianfanChatRequest.Message> getConversationContext(String userId) {
        String key = CONVERSATION_PREFIX + userId;
        // 从 Redis List 中读取历史消息
        List<String> history = stringRedisTemplate.opsForList().range(key, 0, -1);
        List<QianfanChatRequest.Message> messages = new ArrayList<>();

        if (history != null) {
            for (String msg : history) {
                // 消息格式: "role:content"，例如 "user:今天血压偏高怎么办？"
                int colonIndex = msg.indexOf(':');
                if (colonIndex > 0) {
                    QianfanChatRequest.Message message = new QianfanChatRequest.Message();
                    message.setRole(msg.substring(0, colonIndex));
                    message.setContent(msg.substring(colonIndex + 1));
                    messages.add(message);
                }
            }
        }

        return messages;
    }

    /**
     * 保存对话记录
     * 将用户问题和 AI 回复保存到 Redis，维持对话上下文
     *
     * @param userId 用户标识
     * @param userMessage 用户消息
     * @param aiResponse AI 回复
     */
    public void saveConversation(String userId, String userMessage, String aiResponse) {
        String key = CONVERSATION_PREFIX + userId;

        // 保存用户消息
        stringRedisTemplate.opsForList().rightPush(key, "user:" + userMessage);
        // 保存 AI 回复
        stringRedisTemplate.opsForList().rightPush(key, "assistant:" + aiResponse);

        // 修剪消息列表，只保留最近 N 条
        Long size = stringRedisTemplate.opsForList().size(key);
        if (size != null && size > MAX_MESSAGES * 2) {
            // 移除最早的消息
            stringRedisTemplate.opsForList().trim(key, size - MAX_MESSAGES * 2, -1);
        }

        // 更新过期时间
        stringRedisTemplate.expire(key, CONVERSATION_TTL_HOURS, TimeUnit.HOURS);
    }

    /**
     * 清除对话历史
     */
    public void clearConversation(String userId) {
        stringRedisTemplate.delete(CONVERSATION_PREFIX + userId);
    }
}
```

---

## 四、面试题

### 问题 1：大模型 API 选型考量

**选型维度：**

1. **中文能力**：养老场景的对话内容以中文为主，文心一言在中文理解和生成方面优于 GPT 系列
2. **成本控制**：千帆按 Token 计费，养老场景日均调用量不大（约 5000 次/天），按量付费比私有化部署更经济
3. **数据安全**：百度千帆通过等保三级认证，符合医疗健康数据的合规要求
4. **生态集成**：百度千帆提供标准 REST API，与 Spring Boot 集成简单，SDK 成熟
5. **模型迭代**：千帆平台持续更新模型版本，文心一言 4.0 在医疗知识问答上表现良好

### 问题 2：流式输出方案如何实现？

**实现方案：**

1. **千帆 SSE 接口**：千帆 API 支持 `stream=true` 参数，返回 Server-Sent Events 格式的数据流
2. **Spring WebFlux**：后端使用 WebClient 的 `bodyToFlux` 接收 SSE 流，再通过 `Flux<String>` 推送给前端
3. **WebSocket 桥接**：微信小程序不支持原生 SSE，后端将 SSE 转换为 WebSocket 消息推送给小程序端
4. **体验优化**：前端实现打字机效果，逐字显示 AI 回复，避免用户等待完整响应
5. **错误处理**：流式中断时自动重连，显示"继续生成..."提示

### 问题 3：AI 调用成本如何控制？

**成本控制策略：**

1. **模型分级**：简单问题使用文心一言 3.5（价格更低），复杂问题使用 4.0，通过问题复杂度分类器自动路由
2. **Prompt 压缩**：限制历史对话轮数（最多 10 轮），控制输入 Token 数
3. **缓存策略**：常见问题（如"血压正常范围是多少"）的回复缓存到 Redis，相同问题直接返回
4. **限流控制**：每个用户每分钟最多调用 5 次，防止滥用
5. **Token 监控**：记录每次调用的 Token 消耗，按天/按月统计成本，超阈值自动告警