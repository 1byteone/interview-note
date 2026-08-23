# Spring Boot + LangChain4j 最简AI应用：5分钟跑通第一个对话

> 本文是 ruoyi-ai 项目技术栈深度剖析系列的第1篇，面向 Java 后端开发者，旨在帮助读者从零搭建一个 Spring Boot 3.5 + LangChain4j 1.13.0 的 AI 对话应用，理解 AI 服务在 Java 生态中的集成方式，并对照分析 ruoyi-ai 项目中的真实实现。

---

## 一、项目背景：该技术栈在项目中的角色

### 1.1 为什么需要 LangChain4j

在 ruoyi-ai 项目中，AI 对话能力是核心功能模块。用户通过前端界面输入问题，后端需要将问题发送给大语言模型（LLM），获取回复后返回给用户。这个过程看似简单，但实际开发中会遇到以下挑战：

- **多厂商兼容**：项目需要支持 OpenAI、智谱 glm-5.2、通义千问、DeepSeek 等多个模型提供商，每个厂商的 API 格式、鉴权方式、参数命名各不相同
- **流式输出**：AI 回复通常需要流式输出（SSE），以改善用户体验，但流式处理涉及异步编程、背压控制、连接管理等复杂逻辑
- **对话记忆**：多轮对话需要维护上下文，手动管理历史消息列表容易出错
- **工具调用**：AI 需要调用外部工具（如搜索、查数据库），这涉及 Function Calling 的声明和调用管理

LangChain4j 正是为解决这些问题而生。它提供了一套统一的 `ChatLanguageModel` 接口，屏蔽了不同厂商的 API 差异；通过 `@AiService` 注解实现了声明式 AI 服务定义；内置了 `TokenStream` 流式处理、`ChatMemory` 对话记忆管理、`@Tool` 工具调用等能力。

### 1.2 在 ruoyi-ai 项目中的位置

在 ruoyi-ai 的四层架构中，LangChain4j 位于 **AI 层** 的核心位置，是连接应用层和 LLM 基础设施的桥梁：

```
展现层（Vue 3 / Vben）
    ↓ HTTP/SSE
应用层（ruoyi-admin / ruoyi-chat）
    ↓ AiServices
AI 层 → LangChain4j（统一 LLM API）
    ↓
基础设施层（OpenAI / DeepSeek / 通义千问 / Ollama）
```

具体来说，LangChain4j 在项目中承担了以下职责：

- **统一 LLM 接入**：通过 `ChatLanguageModel` 接口屏蔽多厂商差异，实现一行配置切换模型
- **AI 服务代理**：通过 `@AiService` 注解提供声明式 AI 接口，无需手写实现类
- **Agent 基础**：为 Supervisor Agent 提供 `@Tool` 注解和 AiServices 支持
- **RAG 管线基础**：提供 `EmbeddingModel`、`ContentRetriever` 等 RAG 核心组件

### 1.3 本文目标

本文的目标是帮助读者从零搭建一个最简的 Spring Boot + LangChain4j 项目，实现第一个 AI 对话接口。通过这个最小可行示例，读者将理解：

1. LangChain4j 的 Spring Boot Starter 自动配置原理
2. `ChatLanguageModel` 和 `@AiService` 的基本用法
3. 如何在项目中切换不同的模型提供商
4. 流式对话的实现方式

---

## 二、核心概念：2-3个，用生活类比解释

### 概念 1：ChatLanguageModel —— 就像"翻译官"

**生活类比**：想象你去国外旅游，你不会说当地语言，但有一位随身翻译官。你只需要用中文说"我要去火车站"，翻译官就会用当地语言告诉司机。你不需要关心翻译官用的是哪种词典、如何组织语法，你只需要告诉他你想说什么。

**技术映射**：`ChatLanguageModel` 就是这位翻译官。它屏蔽了不同 LLM 厂商的 API 差异：

```java
// 不管背后是 OpenAI 还是 DeepSeek，你调用的都是同一个接口
ChatLanguageModel model = ...;
String answer = model.generate("请解释什么是微服务架构");
```

- **输入**：你的问题（用户消息）
- **输出**：AI 的回答（`Response<AiMessage>`）
- **内部工作**：翻译官（模型）负责将你的消息发送给 LLM 厂商的 API，接收响应，解析 JSON，返回结果

**关键点**：你的业务代码只依赖 `ChatLanguageModel` 接口，不依赖任何具体厂商的 SDK。切换模型时只需修改配置文件，代码零改动。

### 概念 2：@AiService —— 就像"餐厅点餐机"

**生活类比**：传统餐厅里，你需要向服务员口述需求（"我要一份牛排，七分熟，配蘑菇酱"），服务员记下来再传给厨房。而有了点餐机，你只需要在屏幕上按几个按钮（"牛排" -> "七分熟" -> "蘑菇酱"），系统自动生成订单传给厨房。点餐机把"点餐"这件事从"人工传话"变成了"按钮操作"。

**技术映射**：`@AiService` 就是 AI 世界的点餐机。传统方式需要手动构建 Prompt、调用 LLM API、解析响应：

```java
// 传统方式：手动组装、调用、解析
String prompt = "你是一个 Java 专家。请回答：" + question;
Response<AiMessage> response = model.generate(prompt);
String answer = response.content().text();
```

而使用 `@AiService`，你只需要定义一个接口：

```java
@AiService
interface Assistant {
    @SystemMessage("你是一个 Java 专家")
    String chat(@UserMessage String question);
}

// 使用时就像调用普通方法
Assistant assistant = ...;
String answer = assistant.chat("什么是微服务？");
```

**关键点**：`@AiService` 通过 JDK 动态代理，在运行时自动生成接口的实现类。方法调用被拦截后，LangChain4j 根据注解信息自动组装 Prompt、调用 LLM、解析返回值。整个过程对开发者透明，写 AI 接口就像写普通 Java 接口一样简单。

### 概念 3：Spring Boot Starter 自动配置 —— 就像"水电入户"

**生活类比**：你买了一套新房，搬进去之前，开发商已经做好了水电入户。你不需要自己去拉电线、接水管，只需要打开开关就有电，拧开水龙头就有水。需要做什么都已经提前配置好了。

**技术映射**：`langchain4j-spring-boot-starter` 就是 AI 能力的水电入户。引入这个依赖后，Spring Boot 自动完成以下配置：

- 扫描 classpath 中的 LangChain4j 库
- 读取 `application.yml` 中以 `langchain4j` 开头的配置项
- 自动创建 `ChatLanguageModel`、`StreamingChatLanguageModel` 等 Bean
- 扫描所有 `@AiService` 接口并生成代理实例

你只需要引入依赖、配置参数、定义接口，AI 能力就像水电一样即开即用。

---

## 三、从零搭建：完整代码

### 3.1 项目结构

```
hello-ai/
├── pom.xml                          # Maven 项目配置
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/
│   │   │       └── helloai/
│   │   │           ├── HelloAiApplication.java      # Spring Boot 启动类
│   │   │           ├── assistant/
│   │   │           │   └── ChatAssistant.java       # @AiService 接口定义
│   │   │           ├── controller/
│   │   │           │   └── ChatController.java      # REST 控制器
│   │   │           └── config/
│   │   │               └── AppConfig.java           # 手动 Bean 配置（可选）
│   │   └── resources/
│   │       └── application.yml                      # 应用配置
│   └── test/
│       └── java/
│           └── com/
│               └── helloai/
│                   └── ChatAssistantTest.java       # 单元测试
```

### 3.2 pom.xml —— 基础依赖配置

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <!-- 项目基本信息 -->
    <groupId>com.helloai</groupId>
    <artifactId>hello-ai</artifactId>
    <version>1.0.0</version>
    <name>hello-ai</name>
    <description>Spring Boot 3.5 + LangChain4j 1.13.0 最简AI应用</description>

    <!-- 继承 Spring Boot 3.5.8 的父 POM -->
    <!-- 父 POM 已预定义：依赖管理、插件配置、Java 版本等 -->
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.5.8</version>
        <relativePath/>
    </parent>

    <!--
    项目属性定义：
    - java.version: 指定 Java 17，Spring Boot 3.5 要求的最低版本
    - langchain4j.version: 统一管理 LangChain4j 版本，避免依赖冲突
    -->
    <properties>
        <java.version>17</java.version>
        <!-- LangChain4j 版本号，与 ruoyi-ai 项目保持一致 -->
        <langchain4j.version>1.13.0</langchain4j.version>
    </properties>

    <dependencies>
        <!-- ====== Spring Boot 基础依赖 ====== -->

        <!-- Spring Boot Web Starter：提供 REST API 能力 -->
        <!-- 包含：Spring MVC、嵌入式 Tomcat、Jackson JSON 序列化 -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>

        <!-- ====== LangChain4j 核心依赖 ====== -->

        <!--
        LangChain4j Spring Boot Starter：
        这是 LangChain4j 与 Spring Boot 集成的核心依赖。
        它提供了自动配置能力，让开发者无需手动创建 ChatLanguageModel 等 Bean。
        引入此依赖后，Spring Boot 启动时会自动扫描并加载 LangChain4jAutoConfiguration。
        -->
        <dependency>
            <groupId>dev.langchain4j</groupId>
            <artifactId>langchain4j-spring-boot-starter</artifactId>
            <version>${langchain4j.version}</version>
        </dependency>

        <!--
        LangChain4j OpenAI Spring Boot Starter：
        提供 OpenAI 兼容协议的 ChatLanguageModel 实现。
        兼容的模型包括：OpenAI GPT 系列、智谱 glm-5.2、DeepSeek 等
        （只要它们提供 OpenAI 兼容的 API 接口即可）。
        -->
        <dependency>
            <groupId>dev.langchain4j</groupId>
            <artifactId>langchain4j-open-ai-spring-boot-starter</artifactId>
            <version>${langchain4j.version}</version>
        </dependency>

        <!-- ====== 测试依赖 ====== -->

        <!-- Spring Boot Test Starter：提供 JUnit 5、Mockito 等测试框架 -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <!-- Spring Boot Maven 插件：打包为可执行 JAR -->
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

### 3.3 application.yml —— 应用配置

```yaml
# =============================================
# Spring Boot 应用配置
# =============================================
server:
  port: 8080  # 应用端口号

spring:
  application:
    name: hello-ai  # 应用名称，用于服务注册和日志标识

# =============================================
# LangChain4j 配置
# 所有配置项以 langchain4j 为前缀，Spring Boot 自动绑定
# =============================================
langchain4j:
  # ---------- OpenAI 兼容协议聊天模型配置 ----------
  # 此配置适用于所有提供 OpenAI 兼容 API 的模型：
  #   - OpenAI GPT 系列（gpt-4o、gpt-4o-mini 等）
  #   - 智谱 glm-5.2（base-url: https://open.bigmodel.cn/api/paas/v4）
  #   - DeepSeek（base-url: https://api.deepseek.com）
  #   - 通义千问（base-url: https://dashscope.aliyuncs.com/compatible-mode/v1）
  open-ai:
    chat-model:
      # base-url: LLM 厂商的 API 地址
      # 使用环境变量 ${AI_BASE_URL}，可在部署时通过环境变量注入
      base-url: ${AI_BASE_URL:https://api.openai.com}
      # api-key: 调用 LLM 的认证密钥，通过环境变量传入，避免硬编码
      api-key: ${AI_API_KEY:your-api-key-here}
      # model-name: 使用的模型名称
      model-name: ${AI_MODEL_NAME:gpt-4o-mini}
      # temperature: 生成温度，0-2 之间，值越大输出越随机
      # 0.7 是平衡创意和确定性的推荐值
      temperature: 0.7
      # max-tokens: 最大生成 Token 数，限制回复长度
      max-tokens: 4096
      # top-p: 核采样参数，与 temperature 配合使用
      top-p: 0.95
      # timeout: API 调用超时时间，单位毫秒
      timeout: 60000

  # ---------- 日志配置 ----------
  # 开启日志可查看 LLM 请求和响应的完整内容，便于调试
  # 生产环境建议关闭
  logging:
    level:
      dev.langchain4j: DEBUG  # 打印 LangChain4j 内部日志
```

### 3.4 启动类 —— HelloAiApplication.java

```java
package com.helloai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot 应用启动类。
 *
 * @SpringBootApplication 是一个组合注解，包含：
 *   - @Configuration：标记该类为配置类
 *   - @EnableAutoConfiguration：启用 Spring Boot 自动配置机制
 *   - @ComponentScan：自动扫描当前包及其子包下的所有组件
 *
 * 启动后，Spring Boot 会自动：
 *   1. 加载 langchain4j-spring-boot-starter 的自动配置类
 *   2. 读取 application.yml 中的 langchain4j.* 配置
 *   3. 创建 ChatLanguageModel、StreamingChatLanguageModel 等 Bean
 *   4. 扫描并实例化所有 @AiService 接口
 *   5. 启动内嵌 Tomcat 服务器，监听 8080 端口
 */
@SpringBootApplication
public class HelloAiApplication {

    public static void main(String[] args) {
        // 启动 Spring Boot 应用
        SpringApplication.run(HelloAiApplication.class, args);
    }
}
```

### 3.5 @AiService 接口 —— ChatAssistant.java

```java
package com.helloai.assistant;

import dev.langchain4j.service.AiService;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.V;

/**
 * AI 对话助手接口。
 *
 * 通过 @AiService 注解，LangChain4j 在运行时自动为这个接口生成代理实现。
 * 开发者只需要定义接口方法和注解，无需编写任何实现类。
 *
 * 工作原理：
 *   1. Spring Boot 启动后，LangChain4j 自动配置类扫描所有 @AiService 接口
 *   2. 使用 JDK 动态代理（java.lang.reflect.Proxy）创建代理实例
 *   3. 代理实例注册为 Spring Bean，可被其他组件注入使用
 *   4. 调用接口方法时，代理拦截调用，根据注解组装 LLM 请求
 *   5. 调用 ChatLanguageModel 执行推理，将响应解析为返回值类型
 */
@AiService
public interface ChatAssistant {

    /**
     * 最简对话接口：一问一答，无上下文。
     *
     * 参数说明：
     *   @UserMessage：标记该参数为"用户消息"，会被直接发送给 LLM。
     *   如果不加任何注解，LangChain4j 默认将唯一参数视为用户消息。
     *
     * 返回值：
     *   String：LLM 返回的文本回复。
     *   LangChain4j 会自动从 Response<AiMessage> 中提取 .content().text()。
     *
     * 使用示例：
     *   String answer = assistant.chat("请介绍一下 Spring Boot");
     */
    String chat(@UserMessage String userMessage);

    /**
     * 带系统提示词的对话接口。
     *
     * 系统提示词（System Message）用于定义 AI 的角色、行为规则和回复风格。
     * 系统提示词在每次对话前发送给 LLM，优先级高于用户消息。
     *
     * @SystemMessage 可以直接写在接口方法上，指定固定的系统提示词。
     *
     * 使用示例：
     *   String answer = assistant.chatWithSystem("请用中文回答：什么是微服务？");
     *   // 此时 LLM 收到的完整消息是：
     *   //   System: 你是一个资深的 Java 技术专家，擅长 Spring Boot 和微服务架构。
     *   //          请用简洁、专业的中文回答用户的问题。
     *   //   User: 请用中文回答：什么是微服务？
     */
    @SystemMessage("你是一个资深的 Java 技术专家，擅长 Spring Boot 和微服务架构。"
                 + "请用简洁、专业的中文回答用户的问题。")
    String chatWithSystem(@UserMessage String userMessage);

    /**
     * 带对话记忆的接口。
     *
     * 普通的 chat() 方法每次调用都是独立的，LLM 不会记得之前的对话。
     * 通过 @MemoryId 参数，可以区分不同的对话会话，让 LLM 记住上下文。
     *
     * @MemoryId：标记用于区分对话会话的 ID。
     * 相同 ID 的调用共享同一个对话上下文，LLM 能"记住"之前的对话。
     * 不同 ID 的调用相互隔离，互不影响。
     *
     * LangChain4j 默认使用 MessageWindowChatMemory（滑动窗口记忆），
     * 只保留最近 N 轮对话（默认 10 轮），超出部分自动丢弃。
     * 这既节省 Token 消耗，又避免上下文过长导致 LLM 处理质量下降。
     *
     * 使用示例：
     *   // 第一次对话
     *   assistant.chatWithMemory("session-1", "我的名字是张三");
     *   // 第二次对话（同一会话，LLM 记得名字）
     *   assistant.chatWithMemory("session-1", "我叫什么名字？");
     *   // → 会回答：你叫张三
     *
     *   // 另一个会话（独立记忆）
     *   assistant.chatWithMemory("session-2", "我的名字是李四");
     *   // 不影响 session-1 的上下文
     */
    String chatWithMemory(
            @MemoryId String memoryId,    // 会话 ID，用于区分不同对话
            @UserMessage String userMessage  // 用户消息
    );

    /**
     * 带模板变量的对话接口。
     *
     * 当提示词需要动态插入变量时，可以使用 @V("变量名") 注解。
     * 在 @SystemMessage 或 @UserMessage 中使用 {{变量名}} 占位符。
     *
     * @V("name")：将参数值注入到提示词模板中的 {{name}} 位置。
     * @V("question")：将参数值注入到提示词模板中的 {{question}} 位置。
     *
     * 这种机制类似于 SLF4J 的 {} 占位符或 MyBatis 的 #{} 参数绑定。
     *
     * 使用示例：
     *   String answer = assistant.chatWithTemplate(
     *       "AI助手小智",
     *       "Spring Boot 和 Spring Cloud 有什么区别？"
     *   );
     *   // 实际发送给 LLM 的提示词：
     *   //   System: 你是一个名叫 AI助手小智 的智能助手，请用中文回答。
     *   //   User: Spring Boot 和 Spring Cloud 有什么区别？
     */
    @SystemMessage("你是一个名叫 {{name}} 的智能助手，请用中文回答用户的问题。")
    @UserMessage("{{question}}")
    String chatWithTemplate(
            @V("name") String assistantName,       // 模板变量：AI 助手名称
            @V("question") String question          // 模板变量：用户问题
    );
}
```

### 3.6 REST 控制器 —— ChatController.java

```java
package com.helloai.controller;

import com.helloai.assistant.ChatAssistant;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;

/**
 * AI 对话 REST 控制器。
 *
 * 通过 HTTP 接口暴露 AI 对话能力，前端可通过 AJAX 或 SSE 调用。
 * ChatAssistant 由 Spring 容器自动注入，底层是 LangChain4j 生成的动态代理。
 *
 * 设计说明：
 * 这里同时展示了两种使用方式：
 *   1. @AiService 方式（推荐）：声明式，零样板代码
 *   2. ChatLanguageModel 直接调用：更灵活，适合复杂场景
 */
@RestController
@RequestMapping("/api/ai")
public class ChatController {

    // ---------- 依赖注入 ----------

    // @AiService 接口，由 LangChain4j 自动生成代理实现
    // 通过构造器注入，Spring 自动装配
    private final ChatAssistant chatAssistant;

    // 直接注入 ChatLanguageModel Bean
    // 由 langchain4j-spring-boot-starter 自动创建
    // 适用于需要更灵活控制的场景
    private final ChatLanguageModel chatLanguageModel;

    // 流式模型，用于 SSE 推送
    // 与 ChatLanguageModel 不同，它支持逐 token 推送
    private final StreamingChatLanguageModel streamingChatLanguageModel;

    // 构造器注入：Spring 推荐的注入方式
    // 相比 @Autowired 字段注入，构造器注入更安全、更易测试
    public ChatController(
            ChatAssistant chatAssistant,
            ChatLanguageModel chatLanguageModel,
            StreamingChatLanguageModel streamingChatLanguageModel) {
        this.chatAssistant = chatAssistant;
        this.chatLanguageModel = chatLanguageModel;
        this.streamingChatLanguageModel = streamingChatLanguageModel;
    }

    // ---------- 同步对话 API ----------

    /**
     * 最简对话接口（使用 @AiService）。
     *
     * 请求方式：GET
     * 请求路径：/api/ai/chat?message=你好
     * 返回格式：纯文本
     *
     * 示例：
     *   curl "http://localhost:8080/api/ai/chat?message=请介绍一下Spring Boot"
     *   → "Spring Boot 是 Spring 框架的一个子项目..."
     */
    @GetMapping("/chat")
    public String chat(@RequestParam(defaultValue = "你好") String message) {
        // 调用 @AiService 接口，就像调用普通 Java 方法一样
        // 底层：LangChain4j 代理拦截调用 → 组装 Prompt → 调用 LLM → 返回结果
        return chatAssistant.chat(message);
    }

    /**
     * 带系统提示词的对话接口。
     *
     * 请求方式：GET
     * 请求路径：/api/ai/chat-with-system?message=什么是微服务
     * 系统提示词已在 @AiService 接口中定义
     */
    @GetMapping("/chat-with-system")
    public String chatWithSystem(@RequestParam String message) {
        // 调用带系统提示词的接口
        // LLM 会以 Java 技术专家的身份回答
        return chatAssistant.chatWithSystem(message);
    }

    /**
     * 直接使用 ChatLanguageModel 调用 LLM。
     *
     * 这种方式更灵活，适合需要完全控制 Prompt 构建的场景。
     * 不使用 @AiService 代理，直接调用底层模型。
     *
     * 请求方式：GET
     * 请求路径：/api/ai/direct?message=你好
     */
    @GetMapping("/direct")
    public String directChat(@RequestParam String message) {
        // 直接调用 ChatLanguageModel 的 generate 方法
        // ChatLanguageModel 是 LangChain4j 的核心接口
        // generate() 方法接收字符串消息，返回 Response<AiMessage>
        // 通过 .content().text() 提取 AI 回复的文本内容
        ChatResponse response = chatLanguageModel.generate(message);
        return response.aiMessage().text();
    }

    // ---------- 流式对话 API（SSE 推送） ----------

    /**
     * 流式对话接口（SSE 推送）。
     *
     * 使用 Spring WebFlux 的 Flux 实现 SSE（Server-Sent Events）推送。
     * 客户端可以通过 EventSource API 或 fetch API 的流式模式接收。
     *
     * 流式输出的优势：
     *   1. 用户体验好：用户不需要等待完整的回复，可以实时看到 AI 逐字输出
     *   2. 响应速度快：首字节时间（TTFB）大幅降低
     *   3. 连接友好：不会因为长时间等待导致连接超时
     *
     * 请求方式：GET
     * 请求路径：/api/ai/stream?message=写一首诗
     * 返回格式：text/event-stream（SSE 格式）
     *
     * 前端接收示例（JavaScript）：
     *   const source = new EventSource('/api/ai/stream?message=写一首诗');
     *   source.onmessage = (event) => {
     *     console.log('收到:', event.data);  // 逐 token 接收
     *   };
     *   source.onerror = () => console.log('流结束');
     */
    @GetMapping("/stream")
    public Flux<String> streamChat(@RequestParam String message) {
        // 使用 Flux.create() 创建响应式流
        // emitter: 流式发射器，用于推送数据和结束信号
        return Flux.create(emitter -> {
            // 调用 StreamingChatLanguageModel 的 chat() 方法
            // 该方法返回 void，通过回调处理器接收结果
            streamingChatLanguageModel.chat(
                    message,

                    // 流式响应处理器：定义如何处理流式事件
                    new StreamingChatResponseHandler() {

                        /**
                         * 当 LLM 输出部分响应时触发。
                         * 每次推送一个 token（可能是一个字、一个词或一个标点）。
                         * 前端会实时收到这些 token，实现"打字机效果"。
                         */
                        @Override
                        public void onPartialResponse(String partialResponse) {
                            // 将部分响应推送到 SSE 流中
                            // 前端会通过 onmessage 事件逐字接收
                            emitter.next(partialResponse);
                        }

                        /**
                         * 当 LLM 完成完整响应时触发。
                         * 此时应关闭流，通知前端对话结束。
                         */
                        @Override
                        public void onCompleteResponse(ChatResponse completeResponse) {
                            // LLM 回复完成，关闭 SSE 流
                            // 前端会触发 onerror 或 oncomplete 事件
                            emitter.complete();
                        }

                        /**
                         * 当 LLM 调用发生错误时触发。
                         * 将错误信息推送给前端，并关闭流。
                         */
                        @Override
                        public void onError(Throwable error) {
                            // 发生错误，将错误信息推送并关闭流
                            emitter.error(error);
                        }
                    });
        });
    }
}
```

### 3.7 可选：手动 Bean 配置 —— AppConfig.java

> 如果不想使用自动配置，也可以手动创建 ChatLanguageModel Bean。这种方式在需要编程式控制模型参数时更灵活。

```java
package com.helloai.config;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * 手动配置 ChatLanguageModel Bean。
 *
 * 当自动配置无法满足需求时（如需要编程式设置参数），
 * 可以通过 @Bean 方法手动创建模型实例。
 *
 * 注意：如果同时使用了自动配置和手动 @Bean 配置，
 * Spring Boot 的 @ConditionalOnMissingBean 会优先使用手动配置的 Bean。
 */
@Configuration
public class AppConfig {

    // 从配置文件中读取模型参数
    @Value("${langchain4j.open-ai.chat-model.base-url}")
    private String baseUrl;

    @Value("${langchain4j.open-ai.chat-model.api-key}")
    private String apiKey;

    @Value("${langchain4j.open-ai.chat-model.model-name}")
    private String modelName;

    /**
     * 手动创建 ChatLanguageModel Bean。
     *
     * 使用 OpenAiChatModel.builder() 构建器模式，
     * 可以精确控制每个参数。
     */
    @Bean
    public ChatLanguageModel chatLanguageModel() {
        return OpenAiChatModel.builder()
                .baseUrl(baseUrl)                           // API 地址
                .apiKey(apiKey)                             // API 密钥
                .modelName(modelName)                       // 模型名称
                .temperature(0.7)                           // 生成温度
                .maxTokens(4096)                            // 最大 Token 数
                .timeout(Duration.ofSeconds(60))            // 超时时间
                .logRequests(true)                          // 打印请求日志（调试用）
                .logResponses(true)                         // 打印响应日志（调试用）
                .build();
    }

    /**
     * 手动创建流式模型 Bean。
     */
    @Bean
    public StreamingChatLanguageModel streamingChatLanguageModel() {
        return OpenAiChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(modelName)
                .temperature(0.7)
                .maxTokens(4096)
                .timeout(Duration.ofSeconds(60))
                .build();
    }
}
```

### 3.8 单元测试 —— ChatAssistantTest.java

```java
package com.helloai;

import com.helloai.assistant.ChatAssistant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ChatAssistant 的单元测试。
 *
 * 使用 @SpringBootTest 启动完整的 Spring Boot 容器，
 * 包括 LangChain4j 的自动配置和 @AiService 代理。
 *
 * 注意：这些测试需要真实的 API Key 和网络连接。
 * 如果没有配置 API Key，测试会失败。
 */
@SpringBootTest
class ChatAssistantTest {

    // 注入 LangChain4j 自动生成的 @AiService 代理实例
    @Autowired
    private ChatAssistant chatAssistant;

    /**
     * 测试最简对话接口。
     *
     * 验证点：
     *   1. 返回结果不为 null
     *   2. 返回结果非空（长度 > 0）
     *   3. 返回结果包含预期关键词（可选）
     */
    @Test
    void testChat() {
        // 准备测试数据
        String question = "请用一句话介绍 Spring Boot";

        // 执行测试：调用 AI 对话接口
        String answer = chatAssistant.chat(question);

        // 验证结果
        System.out.println("问题：" + question);
        System.out.println("回答：" + answer);

        // 断言：回答不为空
        assertNotNull(answer, "AI 回答不应为 null");
        assertTrue(answer.length() > 0, "AI 回答不应为空字符串");

        // 可选断言：验证回答是否包含 Spring Boot 相关关键词
        boolean containsSpringBoot = answer.contains("Spring Boot")
                || answer.contains("Spring")
                || answer.contains("框架");
        assertTrue(containsSpringBoot, "回答应包含 Spring Boot 相关内容");
    }

    /**
     * 测试带系统提示词的对话接口。
     */
    @Test
    void testChatWithSystem() {
        String question = "什么是微服务架构？";
        String answer = chatAssistant.chatWithSystem(question);

        System.out.println("问题：" + question);
        System.out.println("回答：" + answer);

        assertNotNull(answer);
        assertTrue(answer.length() > 0);
    }

    /**
     * 测试带模板变量的对话接口。
     */
    @Test
    void testChatWithTemplate() {
        String answer = chatAssistant.chatWithTemplate(
                "Java 技术顾问",
                "什么是依赖注入？"
        );

        System.out.println("回答：" + answer);

        assertNotNull(answer);
        assertTrue(answer.length() > 0);
    }
}
```

---

## 四、运行验证

### 4.1 配置 API Key

在运行之前，需要准备一个有效的 LLM API Key。以下是几种常见配置方式：

**方式一：环境变量（推荐）**

```bash
# Windows PowerShell
$env:AI_BASE_URL="https://api.openai.com"
$env:AI_API_KEY="sk-your-key-here"
$env:AI_MODEL_NAME="gpt-4o-mini"

# 或者使用智谱 glm-5.2
$env:AI_BASE_URL="https://open.bigmodel.cn/api/paas/v4"
$env:AI_API_KEY="your-zhipu-key"
$env:AI_MODEL_NAME="glm-5.2"
```

**方式二：直接修改 application.yml**

```yaml
langchain4j:
  open-ai:
    chat-model:
      base-url: https://api.openai.com
      api-key: sk-your-key-here
      model-name: gpt-4o-mini
```

### 4.2 启动应用

```bash
# 使用 Maven 编译并启动
mvn spring-boot:run

# 看到以下日志表示启动成功：
# 2026-08-22T10:00:00.000+08:00  INFO 12345 --- [hello-ai] [           main] c.h.HelloAiApplication                   : Started HelloAiApplication in 3.5 seconds
```

### 4.3 测试接口

**测试同步对话接口：**

```bash
# 使用 curl 测试最简对话
curl "http://localhost:8080/api/ai/chat?message=请介绍一下Spring Boot的特点"

# 期望输出：
# Spring Boot 是 Spring 框架的一个子项目，具有以下特点：
# 1. 自动配置：根据依赖自动配置 Spring 应用
# 2. 起步依赖：简化 Maven/Gradle 配置
# 3. 嵌入式服务器：内嵌 Tomcat/Jetty，无需部署 WAR
# 4. 生产就绪：提供健康检查、指标监控等
```

**测试流式对话接口：**

```bash
# 使用 curl 测试 SSE 流式输出
curl -N "http://localhost:8080/api/ai/stream?message=写一首关于Java的诗"

# 期望输出（逐字推送）：
# data: Java
# data: 之
# data: 歌
# data: ...
```

### 4.4 运行单元测试

```bash
# 运行所有测试
mvn test

# 运行单个测试类
mvn test -Dtest=ChatAssistantTest

# 期望输出：
# [INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
```

---

## 五、项目对照：对应 ruoyi-ai 项目中的真实代码位置

### 5.1 核心文件对照表

| 本文示例 | ruoyi-ai 项目位置 | 说明 |
|---------|-------------------|------|
| `pom.xml` | `ruoyi-ai/pom.xml` 和 `ruoyi-ai/ruoyi-chat/pom.xml` | 父 POM 统一管理版本，子模块按需引入依赖 |
| `application.yml` | `ruoyi-ai/ruoyi-admin/src/main/resources/application.yml` | 配置 langchain4j.* 相关参数 |
| `HelloAiApplication.java` | `ruoyi-ai/ruoyi-admin/src/main/java/com/ruoyi/RuoYiApplication.java` | 项目启动入口 |
| `ChatAssistant.java` | `ruoyi-ai/ruoyi-chat/src/main/java/com/ruoyi/chat/assistant/` | 多个 @AiService 接口，按业务场景划分 |
| `ChatController.java` | `ruoyi-ai/ruoyi-chat/src/main/java/com/ruoyi/chat/controller/ChatController.java` | AI 对话 REST API |
| `AppConfig.java` | `ruoyi-ai/ruoyi-chat/src/main/java/com/ruoyi/chat/config/` | 模型工厂、RAG 配置等 |

### 5.2 ruoyi-ai 中的实际代码分析

**1. 依赖管理**

在 ruoyi-ai 中，LangChain4j 的版本在父 POM 中统一管理：

```xml
<!-- ruoyi-ai/pom.xml -->
<properties>
    <langchain4j.version>1.13.0</langchain4j.version>
</properties>

<dependencyManagement>
    <dependency>
        <groupId>dev.langchain4j</groupId>
        <artifactId>langchain4j-bom</artifactId>
        <version>${langchain4j.version}</version>
        <type>pom</type>
        <scope>import</scope>
    </dependency>
</dependencyManagement>
```

**2. 多模型配置**

ruoyi-ai 的配置远比示例复杂，支持多厂商、多模型并行配置：

```yaml
# ruoyi-ai 的 application.yml 中的 LangChain4j 配置
langchain4j:
  # 默认使用的模型（OpenAI 协议兼容）
  open-ai:
    chat-model:
      base-url: ${AI_BASE_URL:https://open.bigmodel.cn/api/paas/v4}
      api-key: ${AI_API_KEY}
      model-name: ${AI_MODEL_NAME:glm-5.2}
      temperature: 0.7
      max-tokens: 4096
    embedding-model:
      base-url: ${AI_BASE_URL}
      api-key: ${AI_API_KEY}
      model-name: ${AI_EMBEDDING_MODEL:text-embedding-3-small}
```

**3. @AiService 接口设计**

ruoyi-ai 中的 @AiService 接口更加丰富，按功能拆分为多个接口：

- `ChatAssistant.java`：普通对话接口
- `AgentChatAssistant.java`：Agent 对话接口（支持 @Tool 调用）
- `RagChatAssistant.java`：RAG 增强对话接口（支持 @ContentRetriever）

### 5.3 从示例到项目的进阶之路

本文示例是 ruoyi-ai 中 AI 对话能力的"最小可行版本"。从示例到项目，还需要掌握以下能力：

1. **多模型工厂模式**：下一篇文章将详细介绍如何通过工厂模式实现多模型切换
2. **流式对话优化**：ruoyi-ai 中使用了 SSE 长连接管理，支持前端断线重连、心跳检测
3. **对话安全**：内容审核、敏感词过滤、Token 限流
4. **异常处理**：模型调用失败降级、超时重试、熔断保护
5. **监控告警**：AI 调用耗时、Token 消耗、错误率监控

---

## 六、面试实战：3道面试题 + 回答框架

### Q1：Spring Boot 如何集成 LangChain4j？请简述自动配置原理。

**考察点：** 面试官想考察候选人对 Spring Boot 自动配置机制的理解，以及 LangChain4j 如何利用这一机制实现 AI 能力的零配置集成。

**回答框架：**

- **背景**：Spring Boot 集成 LangChain4j 的核心目标是让开发者以声明式方式定义 AI 服务，无需手动创建 LLM 客户端，实现"引入依赖 + 配置参数 + 定义接口"即可使用 AI 能力。

- **方案**：整体分为三步：
  1. 引入 `langchain4j-spring-boot-starter` 依赖（版本 1.13.0）
  2. 在 `application.yml` 中配置 `langchain4j.open-ai.chat-model` 相关参数（base-url、api-key、model-name）
  3. 定义 `@AiService` 接口，直接注入到 Controller 中使用

- **深度（自动配置原理）**：LangChain4j 的 Spring Boot Starter 遵循标准的 Spring Boot 自动配置约定：
  - 在 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 中注册 `LangChain4jAutoConfiguration`
  - 该配置类使用 `@ConditionalOnClass(ChatLanguageModel.class)` 确保 classpath 中存在 LangChain4j 核心库时才激活
  - 使用 `@EnableConfigurationProperties(LangChain4jProperties.class)` 绑定 `langchain4j.*` 配置前缀
  - 读取配置后，通过 `@Bean` 方法创建 `ChatLanguageModel`、`StreamingChatLanguageModel`、`EmbeddingModel` 等 Bean
  - 最后通过 `AiServices` 的 Builder API 为所有标注了 `@AiService` 的接口创建动态代理实例，并注册到 Spring 容器

- **扩展**：这种自动配置模式与 Spring Boot 集成其他中间件的原理一致（如 Redis 的 `RedisAutoConfiguration`、DataSource 的 `DataSourceAutoConfiguration`），体现了 Spring Boot 的"约定优于配置"设计哲学。

### Q2：@AiService 注解的工作原理是什么？和 Spring 的 @Service 有什么区别？

**考察点：** 面试官想考察候选人对动态代理、注解处理和 AOP 模式的理解。

**回答框架：**

- **背景**：`@AiService` 是 LangChain4j 提供的方法级注解，用于标注 AI 服务接口。它和 Spring 的 `@Service` 虽然都是注解，但底层实现机制完全不同。

- **方案**：`@AiService` 标注的接口不需要编写实现类，LangChain4j 在启动时通过以下步骤自动生成代理：
  1. 扫描所有标注了 `@AiService` 的接口
  2. 使用 JDK 动态代理（`java.lang.reflect.Proxy`）为接口创建代理实例
  3. 代理实例拦截接口方法调用，通过反射解析方法签名和参数注解
  4. 根据注解动态组装 `ChatRequest`，调用 `ChatLanguageModel` 执行推理
  5. 将 LLM 返回的响应解析为方法声明的返回值类型

- **深度（核心区别）**：
  - `@Service` 是 Spring 的组件扫描注解，标记一个类作为 Spring 管理的 Bean，需要开发者提供具体实现类
  - `@AiService` 是 LangChain4j 的 AI 代理注解，标记一个接口，由 LangChain4j 在运行时动态生成实现。其代理的核心逻辑是 LLM 调用编排，而非 AOP 增强
  - `@Service` 的代理通过 CGLIB（类代理）或 JDK Proxy（接口代理）实现；`@AiService` 的代理固定使用 JDK Proxy
  - `@Service` 的方法调用执行的是业务代码；`@AiService` 的方法调用被转换为 LLM API 调用

- **扩展**：`@AiService` 的代理机制还支持高级功能：对话记忆（`@MemoryId`）、工具调用（`@Tool`）、流式响应（`TokenStream`）、RAG 集成（`@ContentRetriever`）等。

### Q3：在 Spring Boot 应用中，如何实现 AI 对话的流式输出（SSE）？

**考察点：** 面试官想考察候选人对流式编程、异步处理和 SSE 协议的理解。

**回答框架：**

- **背景**：AI 对话场景中，模型的生成需要时间（通常几秒到几十秒）。如果使用同步等待，用户需要长时间等待才能看到完整回复，体验很差。流式输出（SSE）可以让用户实时看到 AI 逐字输出的过程，显著改善用户体验。

- **方案**：使用 Spring WebFlux 的 `Flux` + LangChain4j 的 `StreamingChatLanguageModel` 实现 SSE 推送：
  1. 使用 `StreamingChatLanguageModel` 替代 `ChatLanguageModel`
  2. 通过 `Flux.create()` 创建响应式流
  3. 在 `StreamingChatResponseHandler` 的 `onPartialResponse` 回调中逐 token 推送
  4. 前端通过 `EventSource` API 接收流式数据

- **深度（核心实现）**：
  - `StreamingChatLanguageModel` 的 `chat()` 方法不阻塞等待完整响应，而是注册一个回调处理器，在接收每个 token 时触发
  - `Flux.create()` 是 Reactive Streams 规范的一个实现，支持背压控制（Backpressure），防止数据推送过快导致客户端处理不过来
  - SSE（Server-Sent Events）是 HTTP 协议的一部分，服务端通过 `Content-Type: text/event-stream` 响应头告诉客户端这是一个流式响应
  - 相比 WebSocket，SSE 的优势在于：基于标准 HTTP 协议，无需额外协议升级；浏览器原生支持 `EventSource` API；自带断线重连机制

- **扩展**：生产环境中的 SSE 优化：
  - 连接管理：使用连接池管理长时间 SSE 连接，防止资源泄漏
  - 心跳检测：定期发送心跳包（如每 30 秒发送一个空行），检测连接是否正常
  - 断线重连：前端 `EventSource` 自带重连机制，但可以自定义重连策略
  - 超时控制：设置 SSE 连接的最大空闲时间，超时自动断开
  - 结合 WebSocket：ruoyi-ai 中同时使用 SSE 和 WebSocket，SSE 用于服务端到客户端的单向推送，WebSocket 用于双向通信

---

## 七、总结

本文从零搭建了一个 Spring Boot 3.5 + LangChain4j 1.13.0 的最简 AI 对话应用，涉及以下知识点：

1. **LangChain4j 的核心抽象**：`ChatLanguageModel` 统一 LLM 接口，屏蔽多厂商差异
2. **@AiService 声明式 AI 服务**：通过注解和动态代理，将 AI 调用简化为接口方法调用
3. **Spring Boot Starter 自动配置**：引入依赖后自动完成 Bean 装配，无需手动配置
4. **流式输出**：使用 `StreamingChatLanguageModel` 实现 SSE 推送，改善用户体验
5. **项目对照**：理解了 ruoyi-ai 项目中 LangChain4j 集成的基本模式

在下一篇文章中，我们将深入分析 ruoyi-ai 的多厂商大模型统一接入（工厂模式）设计，学习如何实现"一行配置切换 AI 模型"的架构能力。

---

## 参考资料

- [LangChain4j 官方文档](https://docs.langchain4j.dev) — 核心 API、Spring Boot Starter 集成指南
- [LangChain4j GitHub 仓库](https://github.com/langchain4j/langchain4j) — 源码、示例、Issues 讨论
- [Spring Boot 自动配置官方文档](https://docs.spring.io/spring-boot/reference/using/auto-configuration.html) — 理解自动配置原理
- [ruoyi-ai GitHub 仓库](https://github.com/1byteone/ruoyi-ai) — 项目源码，查看完整的 AI 服务实现