# 百度千帆AI入门：5分钟集成文心一言

> 本文是 zznursing 项目技术栈深度剖析系列的第2篇（入门篇），面向 Java 后端开发者。手把手带你从零搭建一个百度千帆大模型 API 集成服务，跑通第一个 AI 对话。
>
> **对应项目：** zznursing 养老机构综合运营平台
> **难度等级：** Level 1 入门
> **预计阅读时间：** 20 分钟（含代码实操）

---

## 一、项目背景

### 1.1 什么是百度千帆大模型平台

百度千帆（Qianfan）是百度智能云推出的一站式大模型开发和应用平台。简单来说，它做了两件事：

1. **模型聚合**：集成了百度的文心一言系列模型（ERNIE Bot），以及 Llama、ChatGLM 等第三方开源模型，开发者无需分别对接各家 API
2. **工具链**：提供 Prompt 工程、模型精调、应用评估等配套工具，让大模型落地更简单

对于 Java 后端开发者来说，千帆平台最核心的价值是：**提供标准 REST API，一行代码都不用改就能切换不同模型**。你在代码中调用的始终是千帆的 API，后台部署的是文心一言 4.0 还是 3.5，只是一个配置项的区别。

### 1.2 zznursing 为什么选择千帆

zznursing 是一个养老机构综合运营平台，核心理念是"物联网感知 + AI 智能 + 移动互联"三位一体。在 AI 能力的选型上，团队面临几个关键考量：

| 考量维度 | 需求 | 千帆方案 |
|---------|------|---------|
| 中文理解 | 养老场景涉及大量中文健康术语（血压、心率、压疮等） | 文心一言中文训练数据充分，医疗术语理解准确 |
| 数据安全 | 老人健康数据属于敏感信息，需要合规处理 | 千帆通过等保三级认证，数据不出百度云 |
| 成本控制 | 养老院 AI 调用量有限（日均约 5000 次） | 按 Token 计费，无最低消费，和业务量匹配 |
| 集成难度 | 团队以 Java 为主，需要最简单的 API 方案 | 标准 REST API，Spring Boot 原生支持，无额外 SDK 依赖 |

### 1.3 养老场景中的 AI 应用

在 zznursing 中，文心一言主要承担三个角色：

**健康咨询助手**：老人家属通过微信小程序提问，例如"父亲血压偏高，饮食上需要注意什么？"AI 结合老人实时的健康数据（心率、血压、血糖等），生成个性化的饮食和运动建议。

**膳食推荐引擎**：根据老人的健康状况（糖尿病、高血压等）和口味偏好，推荐合适的食谱。例如"老人有糖尿病，午餐建议吃什么？"AI 会生成低糖、高纤维的餐单建议。

**异常预警分析**：当 IoT 设备检测到异常（如心率骤升、跌倒检测），AI 自动生成异常描述和初步处理建议，推送给值班护士和家属。

这些场景共同的特点是：**需要中文回答、涉及医疗健康知识、对回复的准确性和安全性要求高**。文心一言经过百度的医疗领域知识增强，在健康类问答上表现可靠。

### 1.4 文心一言（ERNIE Bot）能力概览

文心一言是百度自研的大语言模型，千帆平台上主要提供两个版本：

| 模型 | 特点 | 适用场景 | 价格 |
|------|------|---------|------|
| ERNIE-Bot 4.0 | 旗舰版，推理能力强，准确率高 | 复杂健康咨询、异常分析 | 较高 |
| ERNIE-Bot 3.5（eb-instant） | 经济版，响应速度快，成本低 | 简单问答、膳食推荐 | 较低 |

zznursing 的策略是：**简单问题走 3.5，复杂问题走 4.0**，通过问题分类器自动路由，既保证质量又控制成本。

---

## 二、核心概念

### 2.1 千帆 API 鉴权：Access Token 机制

千帆 API 使用 OAuth 2.0 的客户端凭证（Client Credentials）模式进行鉴权。理解这个机制是集成的第一步。

**鉴权流程：**

```
1. 客户端用 API Key + Secret Key 向千帆请求 Access Token
   POST https://aip.baidubce.com/oauth/2.0/token
   ?grant_type=client_credentials
   &client_id={API_KEY}
   &client_secret={SECRET_KEY}

2. 千帆返回 Access Token（有效期 30 天）
   {
     "access_token": "24.xxx...",
     "expires_in": 2592000
   }

3. 后续请求携带 Access Token 调用对话 API
   POST https://aip.baidubce.com/rpc/2.0/ai_custom/v1/wenxinworkshop/chat/completions_pro
   ?access_token=24.xxx...
```

**关键点：**
- Access Token 有效期为 30 天（2592000 秒），在此期间不需要重复获取
- 实际项目中必须缓存 Token，避免每次请求都去获取
- Token 过期后 API 会返回 401 错误，需要捕获后自动刷新

### 2.2 REST API 与 SSE 流式输出

千帆对话 API 支持两种调用模式：

**普通模式（REST API）**：客户端发送请求，服务端等待 AI 完整生成回复后，一次性返回 JSON 结果。适用于后台任务（如生成健康报告摘要），不要求实时展示。

```
请求 → [等待 AI 生成完整回复（可能 3-10 秒）] → 响应
```

**流式模式（SSE）**：客户端发送请求时设置 `stream=true`，服务端通过 Server-Sent Events 协议，逐 Token 推送 AI 生成的内容。适用于微信小程序中需要"打字机效果"的场景。

```
请求 → 推送"血" → 推送"压" → 推送"偏" → 推送"高" → 推送"怎么办" → 推送结束标记
```

对于 Java 后端，SSE 模式通常使用 Spring WebFlux 的 `Flux` 类型来处理，或者使用 OkHttp 的 EventSource 监听器。

### 2.3 Prompt 工程：给 AI 设定角色

在养老场景中，Prompt（提示词）设计的质量直接影响 AI 回复的准确性和安全性。一个典型的护理场景 Prompt 包含三个部分：

```
[系统角色] → 你是一个专业的养老健康顾问，名叫"小护"
[上下文数据] → 老人最近7天的健康数据：心率72次/分，血压135/85mmHg...
[用户问题] → 父亲血压偏高，饮食上需要注意什么？
```

**设计原则：**
1. **角色明确**：告诉 AI 它是什么身份，以什么风格回答
2. **边界清晰**：告诉 AI 什么能说什么不能说（如"不能替代医生诊断"）
3. **数据驱动**：提供老人实时的健康数据，让 AI 给出个性化建议
4. **安全兜底**：涉及医疗建议时，必须添加"请遵医嘱"等免责声明

### 2.4 请求/响应格式详解

千帆对话 API 的请求和响应都是 JSON 格式，理解每个字段的含义是最基本的。

**请求体（Request Body）：**

```json
{
  "messages": [
    {"role": "system", "content": "你是一个健康顾问"},
    {"role": "user", "content": "血压偏高怎么办"}
  ],
  "temperature": 0.3,
  "top_p": 0.8,
  "max_output_tokens": 1024,
  "stream": false
}
```

| 字段 | 说明 | 建议值 |
|------|------|--------|
| `messages` | 对话消息列表，按顺序排列 | 包含 system + user + assistant 消息 |
| `role` | 消息角色：system（系统设定）、user（用户）、assistant（AI） | 固定值 |
| `content` | 消息内容 | 根据场景填写 |
| `temperature` | 温度参数 0-1，越低回复越确定，越高越随机 | 医疗场景 0.3，创意场景 0.8 |
| `top_p` | 核采样参数，控制候选词的概率累加和 | 0.8-0.9 |
| `max_output_tokens` | 最大输出 Token 数量 | 1024-2048 |
| `stream` | 是否启用流式输出 | 需要实时展示时设为 true |

**响应体（Response Body）：**

```json
{
  "id": "as-xxxxx",
  "object": "chat.completion",
  "result": "血压偏高需要注意以下几点：1. 低盐饮食...",
  "usage": {
    "prompt_tokens": 120,
    "completion_tokens": 85,
    "total_tokens": 205
  }
}
```

| 字段 | 说明 |
|------|------|
| `id` | 对话 ID，用于追踪和排查 |
| `result` | AI 生成的回复内容 |
| `usage.prompt_tokens` | 输入消耗的 Token 数（用于计费） |
| `usage.completion_tokens` | 输出消耗的 Token 数 |
| `usage.total_tokens` | 总 Token 消耗 |

### 2.5 核心参数详解

**Temperature（温度）**：控制 AI 回复的随机性，取值范围 0-1。
- 值越低（如 0.3）：AI 回复更保守、更确定，适合医疗、法律等需要准确性的场景
- 值越高（如 0.8）：AI 回复更多样、更有创意，适合写作、头脑风暴等场景
- 养老场景建议：健康咨询用 0.3，膳食推荐用 0.5

**Top P（核采样）**：控制候选词的概率累加和，取值范围 0-1。
- 值为 0.8 时：AI 只从概率累加和为 80% 的候选词中选择
- 通常和 temperature 配合使用，一般不同时调低

**Max Output Tokens（最大输出 Token）**：限制 AI 单次回复的最大长度。
- 简单问答：512 tokens 足够
- 健康建议：1024 tokens
- 长篇报告：2048+ tokens
- 注意：Token 数不等于字数，一个中文汉字约等于 1-2 个 Token

---

## 三、从零搭建代码

### 3.1 项目结构

我们创建一个完整的 Maven 项目，目录结构如下：

```
qianfan-demo/
├── pom.xml
├── src/
│   ├── main/
│   │   ├── java/com/example/qianfan/
│   │   │   ├── QianfanApplication.java          # 启动类
│   │   │   ├── config/
│   │   │   │   └── QianfanProperties.java       # 配置属性类
│   │   │   ├── model/
│   │   │   │   ├── ChatMessage.java             # 消息记录
│   │   │   │   ├── ChatRequest.java             # 请求 DTO
│   │   │   │   └── ChatResponse.java            # 响应 DTO
│   │   │   ├── service/
│   │   │   │   ├── QianfanApiService.java       # 服务接口
│   │   │   │   └── MockQianfanService.java      # Mock 实现
│   │   │   └── controller/
│   │   │       └── QianfanController.java       # REST 控制器
│   │   └── resources/
│   │       └── application.yml                  # 配置文件
│   └── test/
│       └── java/com/example/qianfan/
│           └── QianfanApplicationTests.java     # 测试类
```

### 3.2 pom.xml —— 依赖配置

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!-- Maven 项目配置文件 —— 定义了本项目所需的所有依赖 -->
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <!-- 父工程：Spring Boot 3.2.5，统一管理起步依赖版本 -->
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.2.5</version>
        <relativePath/>
    </parent>

    <!-- 项目坐标 -->
    <groupId>com.example</groupId>
    <artifactId>qianfan-demo</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <name>Qianfan Demo</name>
    <description>百度千帆 AI 入门示例 —— 5分钟集成文心一言</description>

    <!-- 版本属性集中管理 -->
    <properties>
        <java.version>17</java.version>
        <!-- OkHttp 版本，用于发送 HTTP 请求 -->
        <okhttp.version>4.12.0</okhttp.version>
    </properties>

    <dependencies>
        <!-- Spring Boot Web 起步依赖 —— 提供 REST API 和 Tomcat 内嵌容器 -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>

        <!-- OkHttp —— 高性能 HTTP 客户端，用于调用千帆 REST API -->
        <!-- 相比 RestTemplate，OkHttp 支持更灵活的连接池和超时控制 -->
        <dependency>
            <groupId>com.squareup.okhttp3</groupId>
            <artifactId>okhttp</artifactId>
            <version>${okhttp.version}</version>
        </dependency>

        <!-- Jackson 核心 —— JSON 序列化/反序列化，Spring Boot 已自带但显式声明 -->
        <dependency>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-databind</artifactId>
        </dependency>

        <!-- Lombok —— 通过注解自动生成 getter/setter/构造函数/Builder 等 -->
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>

        <!-- Spring Boot 配置处理器 —— 支持 @ConfigurationProperties 的 IDE 提示 -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-configuration-processor</artifactId>
            <optional>true</optional>
        </dependency>

        <!-- Spring Boot 测试起步依赖 —— 包含 JUnit 5、Mockito 等 -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <!-- Spring Boot Maven 插件，用于打包可执行 JAR 和运行 -->
    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <configuration>
                    <!-- 排除 Lombok，避免打入最终 JAR 包 -->
                    <excludes>
                        <exclude>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                        </exclude>
                    </excludes>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

### 3.3 application.yml —— 配置文件

```yaml
# 百度千帆 AI 入门示例 —— 主配置文件
# 使用 kebab-case（短横线命名法）格式
server:
  port: 8080

# 百度千帆 API 配置，通过 @ConfigurationProperties 绑定到 QianfanProperties
qianfan:
  # API 鉴权配置 —— 生产环境通过环境变量注入，不要硬编码
  api-key: ${QIANFAN_API_KEY:your-api-key}
  secret-key: ${QIANFAN_SECRET_KEY:your-secret-key}
  # 千帆 API 基础 URL，固定值
  base-url: https://aip.baidubce.com
  # 是否启用 Mock 模式 —— 设为 true 时不调用真实 API，直接返回模拟数据
  # 默认 true，方便开发调试，无需 API Key
  mock: true

  # 对话默认参数
  chat:
    # 温度参数：0-1，值越低回复越确定，医疗场景建议 0.3
    temperature: 0.3
    # 核采样参数
    top-p: 0.8
    # 最大输出 Token 数
    max-output-tokens: 1024

# 日志配置
logging:
  level:
    # 打印我们代码的日志，方便调试
    com.example.qianfan: DEBUG
```

### 3.4 QianfanProperties.java —— 配置属性绑定

```java
package com.example.qianfan.config;

// Spring Boot 配置属性绑定
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 百度千帆 API 配置属性类
 *
 * 将 application.yml 中 qianfan.* 的配置项映射到 Java 对象属性
 * 使用 @ConfigurationProperties 注解自动绑定，比 @Value 更优雅
 *
 * prefix = "qianfan" 表示绑定以 qianfan. 开头的配置项
 */
@Component // 声明为 Spring Bean，让 Spring 管理生命周期
@ConfigurationProperties(prefix = "qianfan") // 绑定配置前缀为 qianfan 的配置项
public class QianfanProperties {

    /** 百度千帆 API Key —— 用于获取 Access Token */
    private String apiKey;

    /** 百度千帆 Secret Key —— 配合 API Key 获取 Access Token */
    private String secretKey;

    /** 千帆 API 基础 URL，默认使用百度云官方地址 */
    private String baseUrl = "https://aip.baidubce.com";

    /** 是否启用 Mock 模式 —— true 时不调用真实 API，返回模拟数据 */
    private boolean mock = true;

    /** 对话参数配置，嵌套类 */
    private Chat chat = new Chat();

    // ---------- Getter / Setter 方法 ----------

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public boolean isMock() {
        return mock;
    }

    public void setMock(boolean mock) {
        this.mock = mock;
    }

    public Chat getChat() {
        return chat;
    }

    public void setChat(Chat chat) {
        this.chat = chat;
    }

    /**
     * 对话参数嵌套配置类
     * 对应 application.yml 中 qianfan.chat.* 的配置项
     */
    public static class Chat {

        /** 温度参数：0-1，值越低回复越确定 */
        private double temperature = 0.3;

        /** 核采样参数 */
        private double topP = 0.8;

        /** 最大输出 Token 数 */
        private int maxOutputTokens = 1024;

        // ---------- Getter / Setter 方法 ----------

        public double getTemperature() {
            return temperature;
        }

        public void setTemperature(double temperature) {
            this.temperature = temperature;
        }

        public double getTopP() {
            return topP;
        }

        public void setTopP(double topP) {
            this.topP = topP;
        }

        public int getMaxOutputTokens() {
            return maxOutputTokens;
        }

        public void setMaxOutputTokens(int maxOutputTokens) {
            this.maxOutputTokens = maxOutputTokens;
        }
    }
}
```

### 3.5 ChatMessage.java —— 消息记录

```java
package com.example.qianfan.model;

/**
 * 对话消息记录 —— 对应千帆 API 中 messages 数组里的单个元素
 *
 * 使用 Java 14+ 的 Record 类型，自动生成构造器、getter、equals、hashCode 方法
 * Record 是不可变的（immutable），适合作为 DTO 使用
 *
 * @param role    消息角色：system（系统设定）、user（用户）、assistant（AI）
 * @param content 消息内容文本
 */
public record ChatMessage(
        // 消息角色：system / user / assistant
        String role,
        // 消息内容，例如"血压偏高怎么办"
        String content
) {
    /**
     * 创建一个用户消息的快捷工厂方法
     *
     * @param content 用户提问内容
     * @return 角色为 user 的 ChatMessage 对象
     */
    public static ChatMessage user(String content) {
        return new ChatMessage("user", content);
    }

    /**
     * 创建一个系统消息的快捷工厂方法
     *
     * @param content 系统提示词内容
     * @return 角色为 system 的 ChatMessage 对象
     */
    public static ChatMessage system(String content) {
        return new ChatMessage("system", content);
    }

    /**
     * 创建一个 AI 回复消息的快捷工厂方法
     *
     * @param content AI 回复内容
     * @return 角色为 assistant 的 ChatMessage 对象
     */
    public static ChatMessage assistant(String content) {
        return new ChatMessage("assistant", content);
    }
}
```

### 3.6 ChatRequest.java —— 请求 DTO

```java
package com.example.qianfan.model;

// Jackson 注解，用于自定义 JSON 序列化字段名
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 千帆对话 API 请求体 —— 发送给文心一言的请求参数
 *
 * 包含消息列表、模型参数、流式标记等
 * 使用 @JsonProperty 注解将 Java 驼峰命名映射为千帆 API 的 snake_case 命名
 */
public class ChatRequest {

    /** 对话消息列表，按顺序排列，包含 system/user/assistant 消息 */
    private List<ChatMessage> messages;

    /** 温度参数：0-1，值越低回复越确定，医疗场景建议 0.3 */
    private double temperature = 0.3;

    /** 核采样参数：控制候选词的概率累加和，默认 0.8 */
    @JsonProperty("top_p") // 千帆 API 字段名为 top_p，Java 中为 topP
    private double topP = 0.8;

    /** 最大输出 Token 数，限制 AI 回复长度 */
    @JsonProperty("max_output_tokens") // 千帆 API 字段名为 max_output_tokens
    private int maxOutputTokens = 1024;

    /** 是否启用流式输出（SSE 模式） */
    private boolean stream = false;

    /** 用户标识，用于追踪和安全管理，可选 */
    @JsonProperty("user_id") // 千帆 API 字段名为 user_id
    private String userId;

    // ---------- 构造方法 ----------

    public ChatRequest() {
    }

    /**
     * 带消息列表的构造方法 —— 最常用的构造方式
     *
     * @param messages 对话消息列表
     */
    public ChatRequest(List<ChatMessage> messages) {
        this.messages = messages;
    }

    // ---------- Getter / Setter 方法 ----------

    public List<ChatMessage> getMessages() {
        return messages;
    }

    public void setMessages(List<ChatMessage> messages) {
        this.messages = messages;
    }

    public double getTemperature() {
        return temperature;
    }

    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }

    public double getTopP() {
        return topP;
    }

    public void setTopP(double topP) {
        this.topP = topP;
    }

    public int getMaxOutputTokens() {
        return maxOutputTokens;
    }

    public void setMaxOutputTokens(int maxOutputTokens) {
        this.maxOutputTokens = maxOutputTokens;
    }

    public boolean isStream() {
        return stream;
    }

    public void setStream(boolean stream) {
        this.stream = stream;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }
}
```

### 3.7 ChatResponse.java —— 响应 DTO

```java
package com.example.qianfan.model;

// Jackson 注解，用于自定义 JSON 反序列化字段名
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 千帆对话 API 响应体 —— 文心一言返回的对话结果
 *
 * @JsonIgnoreProperties(ignoreUnknown = true) 忽略未知字段，防止反序列化报错
 * 千帆 API 可能在未来版本增加新字段，这个注解保证向后兼容
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChatResponse {

    /** AI 生成的回复内容文本，这是最核心的字段 */
    private String result;

    /** 对话 ID，用于追踪和问题排查 */
    private String id;

    /** 是否结束（流式模式下使用） */
    @JsonProperty("is_end") // 千帆 API 返回的字段名为 is_end
    private Boolean isEnd;

    /** Token 使用情况，包含计费相关信息 */
    private Usage usage;

    // ---------- Getter / Setter 方法 ----------

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Boolean getIsEnd() {
        return isEnd;
    }

    public void setIsEnd(Boolean isEnd) {
        this.isEnd = isEnd;
    }

    public Usage getUsage() {
        return usage;
    }

    public void setUsage(Usage usage) {
        this.usage = usage;
    }

    /**
     * Token 使用情况 —— 嵌套静态类
     * 对应千帆 API 返回的 usage 对象
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Usage {

        /** 输入（Prompt）消耗的 Token 数 */
        @JsonProperty("prompt_tokens") // 千帆 API 返回的字段名
        private int promptTokens;

        /** 输出（Completion）消耗的 Token 数 */
        @JsonProperty("completion_tokens") // 千帆 API 返回的字段名
        private int completionTokens;

        /** 总 Token 消耗数 */
        @JsonProperty("total_tokens") // 千帆 API 返回的字段名
        private int totalTokens;

        // ---------- Getter / Setter 方法 ----------

        public int getPromptTokens() {
            return promptTokens;
        }

        public void setPromptTokens(int promptTokens) {
            this.promptTokens = promptTokens;
        }

        public int getCompletionTokens() {
            return completionTokens;
        }

        public void setCompletionTokens(int completionTokens) {
            this.completionTokens = completionTokens;
        }

        public int getTotalTokens() {
            return totalTokens;
        }

        public void setTotalTokens(int totalTokens) {
            this.totalTokens = totalTokens;
        }
    }
}
```

### 3.8 QianfanApiService.java —— 服务接口

```java
package com.example.qianfan.service;

// 引入我们定义的模型类
import com.example.qianfan.model.ChatMessage;
import com.example.qianfan.model.ChatResponse;

import java.util.List;

/**
 * 百度千帆 API 服务接口 —— 定义 AI 对话的核心能力
 *
 * 面向接口编程的好处：
 * 1. 业务层只依赖这个接口，不依赖具体实现
 * 2. 可以轻松切换 Mock 实现和真实实现
 * 3. 单元测试时可以 Mock 这个接口
 */
public interface QianfanApiService {

    /**
     * 发起 AI 对话 —— 最核心的接口方法
     *
     * 接收消息列表，返回 AI 的完整回复文本
     * 消息列表可以包含 system（系统设定）、user（用户提问）、assistant（历史回复）
     *
     * @param messages 对话消息列表，按时间顺序排列
     * @return AI 回复的文本内容
     */
    String chat(List<ChatMessage> messages);

    /**
     * 便捷方法 —— 单轮对话，只有用户消息
     *
     * 适用于简单的问答场景，不需要系统提示词和历史记录
     *
     * @param userMessage 用户的提问内容
     * @return AI 回复的文本内容
     */
    default String chat(String userMessage) {
        // 将用户消息包装为 ChatMessage 列表，调用主方法
        return chat(List.of(ChatMessage.user(userMessage)));
    }

    /**
     * 便捷方法 —— 带系统提示词的对话
     *
     * 适用于需要设定 AI 角色的场景，如健康顾问、膳食专家等
     *
     * @param systemMessage 系统提示词，用于设定 AI 的角色和行为
     * @param userMessage 用户的提问内容
     * @return AI 回复的文本内容
     */
    default String chat(String systemMessage, String userMessage) {
        // 系统消息在前，用户消息在后，这是千帆 API 要求的顺序
        return chat(List.of(
                ChatMessage.system(systemMessage),
                ChatMessage.user(userMessage)
        ));
    }
}
```

### 3.9 MockQianfanService.java —— Mock 实现（核心）

```java
package com.example.qianfan.service;

// 引入我们定义的模型类
import com.example.qianfan.config.QianfanProperties;
import com.example.qianfan.model.ChatMessage;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mock 千帆服务实现 —— 不调用真实 API，根据关键词返回预设回复
 *
 * 设计目的：
 * 1. 让开发者无需申请百度千帆 API Key 即可运行和测试
 * 2. 本地开发和 CI 环境不需要网络请求，速度快且稳定
 * 3. 模拟真实 API 的回复格式，前端开发不受影响
 *
 * 切换为真实 API：只需将 application.yml 中 qianfan.mock 改为 false
 * 并配置真实的 qianfan.api-key 和 qianfan.secret-key 即可
 */
@Service // 声明为 Spring Bean，自动被组件扫描发现
public class MockQianfanService implements QianfanApiService {

    // 日志记录器，用于输出调试信息
    private static final Logger log = LoggerFactory.getLogger(MockQianfanService.class);

    // 注入配置属性，用于读取温度等参数（虽然 Mock 实现不实际使用，但保持接口一致）
    private final QianfanProperties properties;

    /**
     * 关键词 -> 回复 的映射表
     * 使用 ConcurrentHashMap 保证线程安全
     * 根据用户消息中的关键词匹配最合适的回复
     */
    private final Map<String, String> responseTemplates = new ConcurrentHashMap<>();

    /**
     * 构造器注入 —— 注入配置属性
     *
     * @param properties 千帆配置属性
     */
    public MockQianfanService(QianfanProperties properties) {
        this.properties = properties;
    }

    /**
     * 初始化方法 —— Spring 在 Bean 创建完成后自动调用
     *
     * 在此方法中初始化模拟回复模板
     * @PostConstruct 注解确保在构造方法和依赖注入完成后执行
     */
    @PostConstruct
    public void init() {
        // 初始化关键词 -> 回复的映射表
        // 每个键是关键词，值是 AI 模拟回复内容
        // 模拟养老健康咨询场景的常见问题

        // 血压相关问题的回复模板
        responseTemplates.put("血压", "您好，关于老人血压偏高的问题，建议您注意以下几点：\n\n" +
                "1. **饮食调整**：减少盐分摄入，每日食盐不超过5克；增加钾摄入，多吃香蕉、土豆等富含钾的食物\n" +
                "2. **规律监测**：建议每天早晚各测量一次血压，记录数据以便观察趋势\n" +
                "3. **适度运动**：在医生指导下进行适度活动，如散步、太极等温和运动\n" +
                "4. **用药提醒**：请严格遵医嘱服用降压药物，不要自行停药或调整剂量\n\n" +
                "⚠️ 以上建议仅供参考，具体治疗方案请咨询主治医生。");

        // 饮食相关问题的回复模板
        responseTemplates.put("饮食", "您好，关于老人的饮食健康，给您以下建议：\n\n" +
                "1. **均衡营养**：保证蛋白质摄入，每天一个鸡蛋、一杯牛奶；适量食用鱼、禽、瘦肉\n" +
                "2. **粗细搭配**：主食中增加全谷物和杂粮，如燕麦、糙米、小米等\n" +
                "3. **多吃蔬果**：每天摄入 300-500 克蔬菜和 200-300 克水果\n" +
                "4. **少食多餐**：每餐七八分饱，可在上午和下午各加一次小食\n" +
                "5. **充足饮水**：每天饮水 1500-2000ml，少量多次\n\n" +
                "⚠️ 如果老人有糖尿病、高血压等慢性病，请根据医嘱调整饮食方案。");

        // 心率相关问题的回复模板
        responseTemplates.put("心率", "您好，关于老人心率的问题，以下信息供您参考：\n\n" +
                "1. **正常范围**：静息状态下，老年人正常心率范围为 60-100 次/分\n" +
                "2. **偏高原因**：情绪激动、运动后、发热、贫血、甲状腺功能亢进等都可能导致心率偏快\n" +
                "3. **偏低原因**：某些药物（如倍他乐克）、甲状腺功能减退、窦房结功能减退可能导致心率偏慢\n" +
                "4. **建议措施**：\n" +
                "   - 持续监测心率变化，记录异常时刻和伴随症状\n" +
                "   - 保持情绪稳定，避免过度激动\n" +
                "   - 如心率持续异常（>100 或 <50），建议及时就医\n\n" +
                "⚠️ 心率异常可能是多种原因导致，请及时咨询医生进行专业评估。");

        // 血糖相关问题的回复模板
        responseTemplates.put("血糖", "您好，关于老人血糖的问题，建议如下：\n\n" +
                "1. **正常参考值**：空腹血糖 3.9-6.1 mmol/L，餐后2小时血糖 < 7.8 mmol/L\n" +
                "2. **饮食控制**：\n" +
                "   - 控制碳水化合物总量，选择低 GI（升糖指数）食物\n" +
                "   - 避免含糖饮料、甜点、精制米面\n" +
                "   - 增加膳食纤维，多吃绿叶蔬菜\n" +
                "3. **运动建议**：餐后半小时散步 15-30 分钟，有助于控制餐后血糖\n" +
                "4. **监测频率**：血糖控制稳定者每周测 2-4 次，不稳定者遵医嘱增加频率\n\n" +
                "⚠️ 糖尿病治疗需要综合管理，请遵医嘱进行药物治疗和生活方式干预。");

        // 睡眠相关问题的回复模板
        responseTemplates.put("睡眠", "您好，关于老人睡眠问题，以下建议供参考：\n\n" +
                "1. **规律作息**：每天固定时间上床和起床，培养生物钟\n" +
                "2. **环境优化**：保持卧室安静、黑暗、凉爽，使用舒适的床垫和枕头\n" +
                "3. **睡前习惯**：\n" +
                "   - 睡前 1 小时避免使用手机、电视等电子设备\n" +
                "   - 可以喝杯温牛奶或泡脚 15 分钟帮助放松\n" +
                "   - 避免睡前大量饮水，减少夜间起夜\n" +
                "4. **白天活动**：适度日间活动，但避免睡前剧烈运动\n" +
                "5. **午睡控制**：午睡不超过 30 分钟，避免影响夜间睡眠\n\n" +
                "⚠️ 长期失眠会影响免疫力，如持续超过 2 周，建议咨询医生。");

        // 跌倒相关问题的回复模板
        responseTemplates.put("跌倒", "您好，关于老人跌倒预防，给您以下建议：\n\n" +
                "1. **环境安全**：\n" +
                "   - 保持地面干燥，清除走道上的杂物和电线\n" +
                "   - 卫生间安装扶手，使用防滑垫\n" +
                "   - 卧室和走廊安装夜灯，保证夜间照明充足\n" +
                "2. **辅助器具**：根据老人行走能力，使用拐杖、助行器等辅助工具\n" +
                "3. **穿着防护**：穿防滑、合脚的鞋子，避免拖鞋和过大的鞋子\n" +
                "4. **身体锻炼**：进行平衡训练（如单脚站立、脚跟对脚尖走路）和肌力训练\n" +
                "5. **药物管理**：某些药物（如降压药、安眠药）可能导致头晕，服药后注意休息\n\n" +
                "⚠️ 如果老人已经发生过跌倒，建议进行跌倒风险评估，制定个性化预防方案。");

        // 没有匹配到关键词时的默认回复
        responseTemplates.put("default", "您好，感谢您的咨询。作为养老健康顾问，我很乐意为您提供帮助。\n\n" +
                "您可以咨询以下方面的内容：\n" +
                "1. **健康问题**：血压、心率、血糖、睡眠等健康指标相关问题\n" +
                "2. **饮食建议**：老人的营养搭配、膳食建议\n" +
                "3. **安全防护**：跌倒预防、用药安全等\n" +
                "4. **日常护理**：生活照护、康复训练等\n\n" +
                "请告诉我您具体想了解哪方面的问题，我将为您提供详细的建议。\n\n" +
                "⚠️ 温馨提示：我的建议仅供参考，不能替代专业医疗诊断。");

        log.info("MockQianfanService 初始化完成，共加载 {} 个回复模板", responseTemplates.size());
    }

    /**
     * 核心方法 —— 根据消息列表返回模拟回复
     *
     * 实现逻辑：
     * 1. 从消息列表中提取最后一条用户消息的内容
     * 2. 根据关键词匹配最合适的回复模板
     * 3. 如果没有匹配的关键词，返回默认回复
     *
     * @param messages 对话消息列表
     * @return 模拟的 AI 回复文本
     */
    @Override
    public String chat(List<ChatMessage> messages) {
        // 记录调用日志，方便调试
        log.debug("MockQianfanService 收到对话请求，消息数量: {}", messages.size());

        // 从消息列表中提取最后一条用户消息的内容
        // 遍历消息列表，找到最后一个 role 为 user 的消息
        String userContent = "";
        for (ChatMessage msg : messages) {
            if ("user".equals(msg.role())) {
                // 记录最后一条用户消息
                userContent = msg.content();
            }
        }

        // 如果用户消息为空，直接返回默认回复
        if (userContent == null || userContent.isBlank()) {
            log.warn("用户消息内容为空，返回默认回复");
            return responseTemplates.get("default");
        }

        // 根据关键词匹配回复模板
        // 遍历 responseTemplates 的键（关键词），检查用户消息是否包含该关键词
        String matchedResponse = null;
        for (String keyword : responseTemplates.keySet()) {
            // 跳过 "default" 键，它没有对应的关键词
            if ("default".equals(keyword)) {
                continue;
            }
            // 检查用户消息是否包含当前关键词
            if (userContent.contains(keyword)) {
                // 匹配到关键词，记录日志并返回对应的回复
                matchedResponse = responseTemplates.get(keyword);
                log.debug("匹配到关键词 '{}'，返回对应回复", keyword);
                break; // 匹配到第一个关键词就返回，不再继续匹配
            }
        }

        // 如果匹配到关键词，返回对应的回复；否则返回默认回复
        if (matchedResponse != null) {
            return matchedResponse;
        }

        // 没有匹配到任何关键词，返回默认回复
        log.debug("未匹配到关键词，返回默认回复");
        return responseTemplates.get("default");
    }
}
```

### 3.10 QianfanController.java —— REST 控制器

```java
package com.example.qianfan.controller;

// 引入我们定义的模型类和服务接口
import com.example.qianfan.model.ChatMessage;
import com.example.qianfan.service.QianfanApiService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 百度千帆对话控制器 —— 提供 REST API 接口
 *
 * 提供两个核心接口：
 * 1. POST /api/chat —— 普通对话（非流式），返回完整回复
 * 2. GET /api/chat/stream —— SSE 流式对话（模拟），逐段推送回复
 */
@RestController // 标记为 REST 控制器，所有方法返回 JSON
@RequestMapping("/api") // 请求路径前缀，所有接口统一以 /api 开头
public class QianfanController {

    // 日志记录器
    private static final Logger log = LoggerFactory.getLogger(QianfanController.class);

    // 注入千帆服务接口 —— 面向接口编程，不依赖具体实现
    private final QianfanApiService qianfanService;

    /**
     * 构造器注入 —— 推荐方式，比 @Autowired 字段注入更安全
     *
     * 实际注入的是 MockQianfanService 还是真实实现，
     * 由 Spring 根据条件装配决定
     *
     * @param qianfanService 千帆服务接口实现
     */
    public QianfanController(QianfanApiService qianfanService) {
        this.qianfanService = qianfanService;
    }

    /**
     * 普通对话接口 —— 非流式，返回完整回复
     *
     * 请求示例：
     * POST /api/chat
     * Content-Type: application/json
     * Body: {"messages":[{"role":"user","content":"老人血压偏高怎么办"}]}
     *
     * @param request 请求体，包含 messages 消息列表
     * @return 统一格式的响应，包含 AI 回复内容
     */
    @PostMapping("/chat") // 处理 POST /api/chat 请求
    public Map<String, Object> chat(@RequestBody Map<String, Object> request) {
        // 记录请求日志
        log.info("收到对话请求: {}", request);

        // 从请求体中提取 messages 参数
        @SuppressWarnings("unchecked") // 抑制类型转换警告，因为 JSON 反序列化后是 LinkedHashMap
        List<Map<String, String>> messagesMap = (List<Map<String, String>>) request.get("messages");

        // 将 Map 列表转换为 ChatMessage 列表
        // 使用 Java Stream API 进行类型转换
        List<ChatMessage> messages = messagesMap.stream()
                .map(m -> new ChatMessage(m.get("role"), m.get("content")))
                .toList();

        // 调用千帆服务，获取 AI 回复
        String result = qianfanService.chat(messages);

        // 记录回复日志
        log.info("AI 回复: {}", result.substring(0, Math.min(result.length(), 100)) + "...");

        // 返回统一格式的响应
        return Map.of(
                "code", 200,        // 业务状态码，200 表示成功
                "data", Map.of(
                        "result", result,     // AI 回复内容
                        "messages", messages   // 返回原始消息列表，方便调试
                ),
                "message", "success" // 业务状态描述
        );
    }

    /**
     * 流式对话接口 —— SSE 模式，逐段推送回复
     *
     * 请求示例：
     * GET /api/chat/stream?message=老人血压偏高怎么办
     *
     * 注意：本示例的流式实现是模拟的，逐段返回预设的回复内容
     * 真实项目中，这个接口会调用千帆的 SSE API，由 WebFlux Flux 驱动
     *
     * @param message 用户提问内容
     * @return SSE 流式响应，使用 text/event-stream 媒体类型
     */
    @GetMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public String chatStream(@RequestParam(defaultValue = "你好") String message) {
        // 记录 SSE 流式请求日志
        log.info("收到流式对话请求: {}", message);

        // 使用便捷方法发起单轮对话
        // 将用户消息包装为 ChatMessage 列表
        List<ChatMessage> messages = List.of(ChatMessage.user(message));
        String result = qianfanService.chat(messages);

        // 构建 SSE 格式的响应
        // SSE 协议的格式为：data: 内容\n\n
        // 浏览器端使用 EventSource API 接收
        StringBuilder sseResponse = new StringBuilder();
        // 将回复内容按字符拆分，模拟逐字推送的效果
        // 实际项目中不需要这样拆分，直接推送完整内容即可
        for (int i = 0; i < result.length(); i++) {
            // 每行格式：data: 字符\n\n
            // 注意：这里为了演示 SSE 协议，逐字符推送
            // 实际项目应根据千帆 SSE API 的返回格式解析
            sseResponse.append("data: ").append(result.charAt(i)).append("\n\n");
        }
        // 推送结束标记，告诉客户端流式传输结束
        sseResponse.append("data: [DONE]\n\n");

        return sseResponse.toString();
    }

    /**
     * 健康检查接口 —— 验证服务是否正常运行
     *
     * 请求示例：GET /api/health
     * 响应示例：{"status":"UP","service":"qianfan-demo","mock":true}
     *
     * @return 服务状态信息
     */
    @GetMapping("/health") // 处理 GET /api/health 请求
    public Map<String, Object> health() {
        // 返回服务状态信息
        return Map.of(
                "status", "UP",              // 服务状态
                "service", "qianfan-demo",   // 服务名称
                "mock", true                 // 是否为 Mock 模式
        );
    }
}
```

### 3.11 QianfanApplication.java —— 启动类

```java
package com.example.qianfan;

// Spring Boot 核心注解
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
// 启用配置属性绑定
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * 百度千帆 AI 入门示例 —— 主启动类
 *
 * @SpringBootApplication 是一个组合注解，包含：
 * 1. @Configuration —— 声明这是一个配置类
 * 2. @EnableAutoConfiguration —— 开启 Spring Boot 自动配置
 * 3. @ComponentScan —— 自动扫描当前包及其子包下的组件
 *
 * @EnableConfigurationProperties 启用 @ConfigurationProperties 绑定
 * 让 application.yml 中的配置自动映射到 QianfanProperties 类
 */
@SpringBootApplication
@EnableConfigurationProperties
public class QianfanApplication {

    /**
     * 应用入口方法
     *
     * SpringApplication.run() 会：
     * 1. 加载 application.yml 配置
     * 2. 创建 Spring IoC 容器
     * 3. 自动扫描并注册所有 Bean
     * 4. 启动内嵌 Tomcat 服务器
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        // 启动 Spring Boot 应用
        SpringApplication.run(QianfanApplication.class, args);

        // 启动成功后打印提示信息
        System.out.println("========================================");
        System.out.println("百度千帆 AI Demo 启动成功！");
        System.out.println("测试接口：");
        System.out.println("  POST http://localhost:8080/api/chat");
        System.out.println("  GET  http://localhost:8080/api/chat/stream?message=你好");
        System.out.println("  GET  http://localhost:8080/api/health");
        System.out.println("当前模式: Mock（无需 API Key）");
        System.out.println("========================================");
    }
}
```

### 3.12 QianfanApplicationTests.java —— 测试类

```java
package com.example.qianfan;

// 引入我们定义的模型类和服务接口
import com.example.qianfan.model.ChatMessage;
import com.example.qianfan.service.QianfanApiService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 百度千帆 AI 入门示例 —— 单元测试类
 *
 * @SpringBootTest 注解会自动加载完整的 Spring 应用上下文
 * 测试时不需要真实的 API Key，因为 MockQianfanService 会生效
 *
 * 测试覆盖的场景：
 * 1. 单轮对话（最简单的问答）
 * 2. 关键词匹配（验证 Mock 回复是否正确匹配）
 * 3. 多轮对话（验证消息列表的处理）
 */
@SpringBootTest // 标记为 Spring Boot 测试，自动加载应用上下文
@DisplayName("千帆服务测试") // 测试类的显示名称，在测试报告中展示
class QianfanApplicationTests {

    // 自动注入千帆服务接口，实际注入的是 MockQianfanService
    @Autowired
    private QianfanApiService qianfanService;

    /**
     * 测试单轮对话 —— 最简单的问答场景
     *
     * 测试步骤：
     * 1. 发送一条用户消息"老人血压偏高怎么办"
     * 2. 验证 AI 回复不为空
     * 3. 验证 AI 回复包含关键词"血压"（表明匹配到了血压模板）
     */
    @Test // 标记为 JUnit 5 测试方法
    @DisplayName("单轮对话 - 血压问题") // 测试方法的显示名称
    void testSingleChat() {
        // 准备测试数据：创建一条用户消息
        ChatMessage userMessage = ChatMessage.user("老人血压偏高怎么办");
        List<ChatMessage> messages = List.of(userMessage);

        // 执行测试：调用服务接口
        String result = qianfanService.chat(messages);

        // 验证结果
        // 1. 回复不为空
        assertNotNull(result, "AI 回复不能为空");
        // 2. 回复有内容
        assertFalse(result.isBlank(), "AI 回复内容不能为空字符串");
        // 3. 回复包含血压相关的关键词（说明匹配到了血压模板）
        assertTrue(result.contains("血压"), "回复应包含血压相关建议");
        // 4. 回复包含免责声明（说明回复格式正确）
        assertTrue(result.contains("以上建议仅供参考"), "回复应包含免责声明");

        // 打印测试结果，方便人工观察
        System.out.println("========== 单轮对话测试结果 ==========");
        System.out.println("用户: 老人血压偏高怎么办");
        System.out.println("AI: " + result);
        System.out.println("======================================");
    }

    /**
     * 测试关键词匹配 —— 验证 Mock 回复是否正确匹配关键词
     *
     * 测试步骤：
     * 1. 发送包含"饮食"关键词的用户消息
     * 2. 验证 AI 回复包含饮食相关的建议
     * 3. 验证回复没有匹配到其他关键词的模板
     */
    @Test
    @DisplayName("关键词匹配 - 饮食问题")
    void testKeywordMatching() {
        // 准备测试数据：包含"饮食"关键词的用户消息
        ChatMessage userMessage = ChatMessage.user("老人有糖尿病，饮食上需要注意什么");
        List<ChatMessage> messages = List.of(userMessage);

        // 执行测试
        String result = qianfanService.chat(messages);

        // 验证结果
        // 1. 回复不为空
        assertNotNull(result, "AI 回复不能为空");
        // 2. 回复包含"饮食"相关建议
        assertTrue(result.contains("饮食"), "回复应包含饮食建议");
        // 3. 回复包含具体的饮食建议关键词
        assertTrue(result.contains("营养") || result.contains("蛋白质") || result.contains("蔬菜"),
                "回复应包含具体的饮食建议内容");

        // 打印测试结果
        System.out.println("========== 关键词匹配测试结果 ==========");
        System.out.println("用户: 老人有糖尿病，饮食上需要注意什么");
        System.out.println("AI: " + result);
        System.out.println("========================================");
    }

    /**
     * 测试默认回复 —— 输入不包含任何关键词时的兜底行为
     *
     * 测试步骤：
     * 1. 发送一个不包含任何关键词的消息
     * 2. 验证 AI 回复为默认回复
     * 3. 验证默认回复包含引导用户提问的内容
     */
    @Test
    @DisplayName("默认回复 - 无匹配关键词")
    void testDefaultResponse() {
        // 准备测试数据：不包含任何预设关键词的消息
        ChatMessage userMessage = ChatMessage.user("你好，请问能帮我做什么");
        List<ChatMessage> messages = List.of(userMessage);

        // 执行测试
        String result = qianfanService.chat(messages);

        // 验证结果
        // 1. 回复不为空
        assertNotNull(result, "AI 回复不能为空");
        // 2. 默认回复应包含引导性内容，提示用户可以咨询哪些方面
        assertTrue(result.contains("血压") || result.contains("健康") || result.contains("建议"),
                "默认回复应包含引导用户提问的内容");

        // 打印测试结果
        System.out.println("========== 默认回复测试结果 ==========");
        System.out.println("用户: 你好，请问能帮我做什么");
        System.out.println("AI: " + result);
        System.out.println("======================================");
    }

    /**
     * 测试系统提示词 + 用户消息 —— 多轮消息场景
     *
     * 测试步骤：
     * 1. 创建系统消息（设定 AI 角色）
     * 2. 创建用户消息（具体提问）
     * 3. 验证 AI 回复不为空
     */
    @Test
    @DisplayName("系统提示词 + 用户消息")
    void testWithSystemPrompt() {
        // 准备测试数据：系统消息 + 用户消息
        ChatMessage systemMessage = ChatMessage.system("你是一个专业的养老健康顾问，请用温和的语气回答");
        ChatMessage userMessage = ChatMessage.user("老人最近睡眠不好，有什么建议吗");
        List<ChatMessage> messages = List.of(systemMessage, userMessage);

        // 执行测试
        String result = qianfanService.chat(messages);

        // 验证结果
        assertNotNull(result, "AI 回复不能为空");
        assertFalse(result.isBlank(), "AI 回复内容不能为空字符串");

        // 打印测试结果
        System.out.println("========== 系统提示词测试结果 ==========");
        System.out.println("系统: 你是一个专业的养老健康顾问，请用温和的语气回答");
        System.out.println("用户: 老人最近睡眠不好，有什么建议吗");
        System.out.println("AI: " + result);
        System.out.println("========================================");
    }
}
```

### 3.13 代码结构总结

到这一步，我们完成了所有代码文件的编写。整个项目的核心设计思路是：

1. **面向接口编程**：`QianfanApiService` 接口定义能力，`MockQianfanService` 提供实现，后续可以增加 `RealQianfanService` 实现类
2. **配置驱动**：通过 `application.yml` 中的 `qianfan.mock` 配置，一行切换 Mock 和真实模式
3. **模型层独立**：`ChatMessage`、`ChatRequest`、`ChatResponse` 三个模型类完全独立，不依赖任何框架
4. **异常兜底**：Mock 实现也有默认回复，即使输入不包含任何关键词也不会报错

---

## 四、运行验证

### 4.1 环境准备

运行项目需要以下环境：

- **JDK 17+**：Spring Boot 3.2.x 要求 JDK 17 以上
- **Maven 3.8+**：项目管理工具
- **无需 API Key**：因为默认使用 Mock 模式

### 4.2 启动项目

```bash
# 进入项目目录
cd qianfan-demo

# 编译并启动
mvn spring-boot:run
```

启动成功后，控制台输出：

```
========================================
百度千帆 AI Demo 启动成功！
测试接口：
  POST http://localhost:8080/api/chat
  GET  http://localhost:8080/api/chat/stream?message=你好
  GET  http://localhost:8080/api/health
当前模式: Mock（无需 API Key）
========================================
```

### 4.3 测试普通对话接口

```bash
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"messages":[{"role":"user","content":"老人血压偏高怎么办"}]}'
```

预期响应：

```json
{
  "code": 200,
  "data": {
    "result": "您好，关于老人血压偏高的问题，建议您注意以下几点：\n\n1. **饮食调整**：减少盐分摄入...",
    "messages": [{"role": "user", "content": "老人血压偏高怎么办"}]
  },
  "message": "success"
}
```

### 4.4 测试流式接口

```bash
curl "http://localhost:8080/api/chat/stream?message=老人血压偏高怎么办"
```

预期响应（SSE 格式）：

```
data: 您
data: 好
...
data: [DONE]
```

### 4.5 测试健康检查接口

```bash
curl "http://localhost:8080/api/health"
```

预期响应：

```json
{
  "status": "UP",
  "service": "qianfan-demo",
  "mock": true
}
```

### 4.6 运行单元测试

```bash
# 运行所有测试
mvn test
```

测试结果应该全部通过，控制台输出类似：

```
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0
```

---

## 五、项目对照

### 5.1 本示例 vs zznursing 真实项目

本示例是一个简化的入门版本，zznursing 项目中的千帆 AI 集成要复杂得多。以下是对比表：

| 本示例 | zznursing 真实项目 |
|--------|-------------------|
| Mock 实现，不调用真实 API | 真实调用千帆 API，通过 Access Token 鉴权 |
| 非流式返回 | 支持 SSE 流式输出，通过 WebSocket 转发给微信小程序 |
| 关键词匹配回复 | 真实 AI 生成，每次回复不同 |
| 无状态对话 | 集成 Redis 管理对话历史，支持多轮上下文 |
| 单一模型 | 模型分级路由：简单问题走 ERNIE 3.5，复杂问题走 4.0 |
| 通用 Prompt | 针对养老场景定制的 Prompt 模板，包含老人健康数据 |
| 无上下文 | 上下文感知：结合老人实时健康数据生成个性化建议 |

### 5.2 zznursing 中的 Prompt 模板

在 zznursing 项目中，每次 AI 调用都会组装一个完整的 Prompt，包含系统角色、健康上下文和用户问题三部分。以下是简化版的 Prompt 组装逻辑：

```java
// zznursing 项目中的 Prompt 组装（简化版）
// 每次调用千帆 API 时，将老人健康数据动态注入到 Prompt 中

// 步骤 1：定义系统角色 —— 固定不变
String systemPrompt = "你是一个专业的养老健康顾问，名叫"小护"。\n"
    + "请遵循以下规则：\n"
    + "1. 用温和、通俗的语言回答老人家属的问题\n"
    + "2. 健康建议仅供参考，不能替代医生诊断\n"
    + "3. 基于老人近期的健康数据给出个性化建议\n"
    + "4. 涉及用药建议时，必须强调"请遵医嘱"";

// 步骤 2：获取老人健康数据 —— 动态注入（从 Redis/MySQL 查询）
String healthContext = "【老人健康数据】\n"
    + "姓名: 张爷爷\n"
    + "年龄: 78岁\n"
    + "心率: 72次/分（正常范围: 60-100）\n"
    + "血压: 145/90mmHg（偏高）\n"
    + "血糖: 6.8mmol/L（偏高）\n"
    + "近7天异常: 心率偏高告警2次";

// 步骤 3：组装完整消息列表
List<ChatMessage> messages = List.of(
    ChatMessage.system(systemPrompt),
    ChatMessage.user(healthContext + "\n\n【用户问题】\n" + userQuestion)
);
```

### 5.3 从入门到项目实战的差距

要从本示例过渡到 zznursing 的实际开发，需要掌握以下进阶内容：

1. **真实 Access Token 管理**：实现 Token 缓存、自动刷新、并发安全
2. **SSE 流式处理**：使用 Spring WebFlux 的 Flux 处理千帆的 SSE 流，再通过 WebSocket 推送给小程序
3. **对话历史管理**：使用 Redis List 存储对话历史，控制上下文窗口长度
4. **模型路由**：根据问题复杂度自动选择 ERNIE-Bot 4.0 或 3.5
5. **异常处理**：千帆 API 调用失败的重试、降级、熔断策略
6. **成本监控**：记录每次调用的 Token 消耗，按天/按月统计

---

## 六、面试题

### 面试题 1：百度千帆 API 的鉴权方式是什么？如何实现 Access Token 的缓存和自动刷新？

**参考答案：**

千帆 API 使用 OAuth 2.0 的客户端凭证模式进行鉴权。具体流程是：

1. 客户端使用 `api_key` 和 `secret_key` 调用千帆的 OAuth 接口获取 `access_token`
2. 千帆返回的 `access_token` 有效期为 30 天（2592000 秒）
3. 后续所有 API 请求都需要在 URL 参数中携带 `access_token`

Access Token 的缓存和自动刷新有几个关键设计要点：

- **缓存策略**：使用 `AtomicReference<String>` 或 Redis 缓存 Token，避免每次请求都去获取。Token 获取接口有频率限制，频繁调用可能被限流。
- **提前刷新**：不等到 Token 过期才刷新，而是在过期前 1 天就主动刷新，留有余量。例如 Token 有效期 30 天，缓存的过期时间设为 29 天。
- **并发安全**：在高并发场景下，多个线程可能同时发现 Token 过期，导致重复获取。使用 `synchronized` 或分布式锁保证只有一个线程去获取 Token。
- **异常处理**：Token 获取失败时，不能影响已缓存的 Token 使用（降级策略），同时记录告警日志。

**代码示例（简化版 Token 缓存）：**

```java
// 使用 AtomicReference 保证线程安全
private final AtomicReference<String> accessToken = new AtomicReference<>(null);
// Token 过期时间戳
private volatile long tokenExpireTime = 0;

public String getAccessToken() {
    // 如果 Token 未过期，直接返回缓存
    if (System.currentTimeMillis() < tokenExpireTime) {
        return accessToken.get();
    }
    // 同步获取新 Token（双重检查锁）
    synchronized (this) {
        if (System.currentTimeMillis() < tokenExpireTime) {
            return accessToken.get();
        }
        // 调用千帆 OAuth 接口获取新 Token
        String newToken = fetchTokenFromQianfan();
        // 缓存 Token，提前 1 天过期
        tokenExpireTime = System.currentTimeMillis() + (2592000 - 86400) * 1000L;
        accessToken.set(newToken);
        return newToken;
    }
}
```

### 面试题 2：流式输出（SSE）和普通 REST API 有什么区别？在养老场景中为什么需要流式输出？

**参考答案：**

**区别：**

| 对比维度 | 普通 REST API | 流式输出（SSE） |
|---------|--------------|----------------|
| 响应方式 | 等待 AI 完整生成后一次性返回 | AI 逐 Token 生成，实时推送 |
| 响应时间 | 3-10 秒（取决于问题复杂度） | 首 Token 延迟 < 1 秒 |
| 用户体验 | 用户需要等待完整回复 | 用户可以看到逐字生成的过程 |
| 协议 | 标准 HTTP JSON | Server-Sent Events |
| 连接类型 | 短连接，请求-响应 | 长连接，持续推送 |
| 复杂度 | 低 | 较高（需要处理连接中断、重连等） |

**养老场景为什么需要流式输出：**

在 zznursing 项目中，AI 对话主要通过微信小程序提供给老人家属使用。家属群体通常年龄偏大（40-60 岁），对等待时间的容忍度较低。流式输出的核心价值在于：

1. **降低感知等待时间**：用户不需要等待 AI 完整生成（可能 3-10 秒），而是看到文字逐字出现，心理上感觉"马上就有回复了"
2. **实时反馈**：如果 AI 生成的内容方向不对，用户可以提前中断，重新提问，避免浪费等待时间
3. **网络友好**：长连接模式，不需要为每个 Token 建立新的 HTTP 连接

**后端实现方案：**

```java
// 简化版：使用 Spring WebFlux 的 Flux 处理千帆 SSE 流
// 接收千帆的 SSE 数据 → 解析 result 字段 → 推送给前端

public Flux<String> chatStream(ChatRequest request) {
    return webClient.post()
        .uri(chatUrl + "?access_token=" + token)
        .bodyValue(request)
        .retrieve()
        .bodyToFlux(String.class)
        .filter(data -> data.startsWith("data: "))
        .map(data -> data.substring(6))  // 去掉 "data: " 前缀
        .map(this::extractResult)         // 提取 result 字段
        .filter(content -> !content.isEmpty())
        .timeout(Duration.ofSeconds(60)); // 超时保护
}
```

### 面试题 3：如何设计一个 AI API 调用的错误重试和降级策略？

**参考答案：**

AI API 调用可能因为网络波动、服务端限流、Token 过期等原因失败。一个健壮的系统必须有完善的错误处理策略。

**错误分类和处理策略：**

| 错误类型 | 原因 | 处理策略 |
|---------|------|---------|
| 401 Unauthorized | Access Token 过期 | 自动刷新 Token 后重试 |
| 429 Too Many Requests | 触发限流 | 等待后重试（指数退避） |
| 5xx Server Error | 千帆服务端故障 | 切换到备用模型或降级回复 |
| 超时 | 网络问题 | 重试 1-2 次，超时时间适当延长 |
| JSON 解析错误 | 响应格式异常 | 记录异常响应，返回降级回复 |

**推荐的重试策略（指数退避）：**

```java
// 简化版重试策略
public String chatWithRetry(List<ChatMessage> messages, int maxRetries) {
    Exception lastException = null;
    for (int attempt = 1; attempt <= maxRetries; attempt++) {
        try {
            return callQianfanApi(messages);
        } catch (Exception e) {
            lastException = e;
            if (attempt < maxRetries) {
                // 指数退避：第一次重试等 1 秒，第二次等 2 秒，第三次等 4 秒
                long waitMs = (long) Math.pow(2, attempt - 1) * 1000;
                Thread.sleep(waitMs);
            }
        }
    }
    // 所有重试都失败，返回降级回复
    return "您好，AI 服务暂时不可用，请稍后重试。如需紧急帮助，请联系值班护士。";
}
```

**降级策略：**

在 zznursing 项目中，当千帆 API 不可用时，系统会降级到预设的回复模板（类似本示例的 Mock 实现）。这些模板覆盖了最常见的健康问题（血压、血糖、饮食等），保证在 AI 服务故障时，用户仍然能获得基本的帮助信息。

---

## 七、总结

本文从零开始搭建了一个百度千帆 AI 集成项目，实现了从项目背景、核心概念到代码实现、运行验证的完整流程。

**关键要点回顾：**

1. 百度千帆是百度的一站式大模型平台，集成文心一言等模型，提供标准 REST API
2. 千帆 API 使用 OAuth 2.0 鉴权，Access Token 有效期 30 天，需要缓存和自动刷新
3. 支持普通模式和 SSE 流式模式两种调用方式，体验差异明显
4. 通过面向接口编程 + Mock 模式，可以在不申请 API Key 的情况下开发和测试
5. 养老场景的 Prompt 工程要注重角色设定、安全边界和个性化数据注入

**下一篇预告：** 华为 IoTDA 设备接入 —— 从零搭建物联网设备数据采集服务。