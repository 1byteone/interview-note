# 01 · Spring Boot 3.5 + LangChain4j 1.13.0 集成

> Spring Boot 3.5 作为项目底座提供自动配置、依赖注入与 Web 能力，LangChain4j 作为 Java AI 编排框架将 LLM 能力以类型安全、注解驱动的 Spring 风格注入到应用中，两者结合实现了"零样板代码"的 AI 服务快速搭建。

## 一、你必须知道的 3 个核心概念

### 概念 1：Spring Boot 自动配置原理

Spring Boot 通过 `@EnableAutoConfiguration` 注解，结合 `spring.factories` 或 `org.springframework.boot.autoconfigure.AutoConfiguration.imports` 文件，在启动时扫描 classpath 下的所有自动配置类。每个自动配置类使用 `@ConditionalOnClass`、`@ConditionalOnMissingBean`、`@ConditionalOnProperty` 等条件注解，判断是否满足激活条件，从而按需装配 Bean。在 ruoyi-ai 中，引入 `langchain4j-spring-boot-starter` 后，Spring Boot 自动扫描到该 Starter 的自动配置类，从而完成 `ChatLanguageModel`、`EmbeddingModel` 等核心 Bean 的创建。

### 概念 2：LangChain4j Spring Boot Starter 自动装配

LangChain4j 官方提供了 `langchain4j-spring-boot-starter` 模块，它遵循 Spring Boot 自动配置约定。在 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 中注册了 `LangChain4jAutoConfiguration` 等配置类。这些配置类会读取 `application.yml` 中以 `langchain4j` 为前缀的配置项（如 `langchain4j.open-ai.chat-model.api-key`），当检测到相应的依赖在 classpath 中时，自动创建 `ChatLanguageModel`、`StreamingChatLanguageModel`、`EmbeddingModel` 等 Bean。ruoyi-ai 利用这一机制，在配置文件中一行切换模型提供商，无需手动编写 `@Bean` 方法。

### 概念 3：@AiService 注解代理

`@AiService` 是 LangChain4j 提供的核心注解，用于标注一个 Java 接口，使其成为 AI 服务接口。在运行时，LangChain4j 利用 JDK 动态代理（Java Proxy）为该接口创建代理对象。代理对象的方法调用会被拦截，LangChain4j 根据方法的返回值类型、参数注解（`@UserMessage`、`@SystemMessage`、`@MemoryId`、`@V` 等）自动组装 `ChatRequest`，调用配置好的 `ChatLanguageModel`，并将 LLM 返回的响应解析为方法声明的返回值类型。整个过程对开发者完全透明，写 AI 接口就像写普通 Java 接口一样。

## 二、项目中的实战应用

### 2.1 解决了什么问题

在传统的 Spring Boot 应用中集成 AI 对话能力，需要开发者手动管理 LLM 客户端实例、组装 Prompt、处理对话上下文、解析响应等重复性工作。ruoyi-ai 通过 Spring Boot + LangChain4j 集成解决了以下问题：

- **AI 服务定义标准化**：通过 `@AiService` 注解将 AI 交互抽象为普通 Java 接口，开发者只需关注业务语义，无需关心底层 LLM API 调用细节
- **模型配置外部化**：通过 Spring Boot 的 `application.yml` 统一管理模型提供商、API Key、模型名称等参数，支持环境隔离和运行时动态切换
- **依赖注入统一**：AI 服务实例作为 Spring Bean 管理，可以像普通 Service 一样注入到 Controller 或其他 Service 中，与现有业务代码无缝融合
- **流式响应原生支持**：利用 Spring Boot 的异步能力和 LangChain4j 的 `TokenStream` API，实现 SSE 流式输出，提升用户体验

### 2.2 核心实现（关键代码片段）

#### 1. Maven 依赖配置 (`pom.xml`)

```xml
<!-- LangChain4j Spring Boot Starter：自动装配核心依赖 -->
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-spring-boot-starter</artifactId>
    <version>1.13.0</version>
</dependency>

<!-- OpenAI 模型适配器：通过统一 API 接入 OpenAI 兼容接口 -->
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-open-ai-spring-boot-starter</artifactId>
    <version>1.13.0</version>
</dependency>

<!-- 通义千问模型适配器：接入阿里通义大模型 -->
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-qwen</artifactId>
    <version>1.13.0</version>
</dependency>
```

#### 2. 应用配置 (`application.yml`)

```yaml
langchain4j:
  # 默认使用 OpenAI 协议兼容的模型（如 glm-5.2、通义千问等）
  open-ai:
    chat-model:
      base-url: ${AI_BASE_URL:https://open.bigmodel.cn/api/paas/v4}
      api-key: ${AI_API_KEY:your-api-key-here}
      model-name: ${AI_MODEL_NAME:glm-5.2}
      temperature: 0.7
      max-tokens: 4096
      top-p: 0.95
    # 嵌入模型配置，用于 RAG 向量化
    embedding-model:
      base-url: ${AI_BASE_URL}
      api-key: ${AI_API_KEY}
      model-name: ${AI_EMBEDDING_MODEL:text-embedding-3-small}
```

#### 3. AI 对话接口定义 (`ChatAssistant.java`)

```java
package com.ruoyi.chat.assistant;

import dev.langchain4j.service.*;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;

/**
 * AI 对话助手接口。
 * 通过 @AiService 注解，LangChain4j 在运行时自动生成代理实现。
 * 开发者只需定义方法和注解，无需编写具体实现类。
 */
@AiService  // 标注为 AI 服务接口，LangChain4j 会为其创建动态代理
public interface ChatAssistant {

    /**
     * 普通对话接口。
     * @param userMessage 用户的输入消息
     * @return AI 的文本回复
     */
    String chat(@UserMessage String userMessage);

    /**
     * 带系统提示词的对话接口。
     * @param systemMessage 系统提示词，定义 AI 的角色和行为规则
     * @param userMessage 用户的输入消息
     * @return AI 的文本回复
     */
    String chatWithSystem(
        @SystemMessage String systemMessage,  // 注入系统级提示词
        @UserMessage String userMessage      // 注入用户消息
    );

    /**
     * 带对话记忆的接口。
     * @param memoryId 对话会话 ID，用于区分不同用户的对话上下文
     * @param userMessage 用户的输入消息
     * @return AI 的文本回复
     */
    String chatWithMemory(
        @MemoryId String memoryId,   // 标记对话记忆的会话 ID
        @UserMessage String userMessage
    );

    /**
     * 带模板变量的对话接口。
     * @param userName 用户名称，会被注入到提示词模板中
     * @param question 用户问题
     * @return AI 的文本回复
     */
    String chatWithTemplate(
        @V("name") String userName,      // 模板变量，对应提示词中的 {{name}}
        @V("question") String question   // 模板变量，对应提示词中的 {{question}}
    );
}
```

#### 4. 自定义提示词模板 (`ChatAssistant.java` 扩展)

```java
/**
 * 使用 @SystemMessage 和 @UserMessage 注解定义提示词模板。
 * 支持模板变量 {{it}} 和 {{变量名}} 两种占位符语法。
 */
@AiService
public interface ChatAssistantWithTemplate {

    /**
     * 使用内置模板定义系统提示词。
     * {{name}} 会被 @V("name") 参数的值替换。
     */
    @SystemMessage("你是一个名叫 {{name}} 的智能助手，请用中文回答用户的问题。")
    @UserMessage("{{question}}")
    String chat(
        @V("name") String assistantName,
        @V("question") String question
    );

    /**
     * 使用 {{it}} 占位符接收单个参数。
     * 适用于只需一个输入参数的简化场景。
     */
    @SystemMessage("你是一个 Java 技术专家，擅长 Spring Boot 和 AI 集成。")
    @UserMessage("请用中文回答以下问题：{{it}}")
    String askJavaExpert(String question);
}
```

#### 5. AI 服务装配与使用 (`ChatController.java`)

```java
package com.ruoyi.chat.controller;

import com.ruoyi.chat.assistant.ChatAssistant;
import com.ruoyi.chat.assistant.ChatAssistantWithTemplate;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.service.TokenStream;

/**
 * AI 对话 REST 控制器。
 * ChatAssistant 由 Spring 容器自动注入，底层是 LangChain4j 生成的动态代理。
 */
@RestController
@RequestMapping("/api/ai/chat")
public class ChatController {

    // 注入 LangChain4j 自动生成的 AI 服务代理实例
    private final ChatAssistant chatAssistant;
    private final ChatAssistantWithTemplate templateAssistant;
    // 直接注入流式语言模型，用于更灵活的流式处理
    private final StreamingChatLanguageModel streamingModel;

    // 构造器注入，Spring 自动装配所有依赖
    public ChatController(
            ChatAssistant chatAssistant,
            ChatAssistantWithTemplate templateAssistant,
            StreamingChatLanguageModel streamingModel) {
        this.chatAssistant = chatAssistant;
        this.templateAssistant = templateAssistant;
        this.streamingModel = streamingModel;
    }

    /**
     * 普通对话接口。
     * POST /api/ai/chat/send?message=你好
     */
    @PostMapping("/send")
    public String send(@RequestParam String message) {
        // 调用 AI 服务接口，就像调用普通 Java 方法一样
        return chatAssistant.chat(message);
    }

    /**
     * 流式对话接口（SSE）。
     * GET /api/ai/chat/stream?message=你好
     * 返回 Flux<String> 实现 SSE 推送，前端通过 EventSource 接收。
     */
    @GetMapping("/stream")
    public Flux<String> stream(@RequestParam String message) {
        // 使用 StreamingChatLanguageModel 直接生成流式响应
        return Flux.create(emitter -> {
            streamingModel.generate(message)
                .onPartialResponse(emitter::next)    // 每次收到部分响应就推送给前端
                .onCompleteResponse(r -> emitter.complete())  // 响应完成时关闭流
                .onError(emitter::error)             // 发生错误时通知前端
                .start();                            // 启动流式处理
        });
    }
}
```

### 2.3 设计亮点

**亮点一：零样板代码的 AI 接口定义**

传统的 AI 集成需要手动构建 HTTP 请求、解析 JSON 响应、管理重试和异常。通过 `@AiService` + 动态代理，开发者只需定义一个接口，添加方法签名和注解，LangChain4j 自动完成所有底层工作。这种设计将 AI 调用从"API 调用"提升到了"声明式接口"的抽象层次，与 Spring Data JPA 的 `Repository` 接口设计哲学一脉相承。

**亮点二：Spring 生态的无缝融合**

LangChain4j 的 Spring Boot Starter 遵循 Spring Boot 的自动配置规范，生成的 AI 服务代理实例自动注册为 Spring Bean，可以像普通 Service 一样通过 `@Autowired` 或构造器注入使用。这意味着 AI 服务可以轻松利用 Spring 的 AOP 事务、缓存、异步、重试等能力，与现有业务逻辑深度集成。

**亮点三：统一的模型抽象层**

LangChain4j 定义了统一的 `ChatLanguageModel` 接口，所有模型提供商（OpenAI、智谱、通义千问等）都实现该接口。切换模型时只需修改配置文件中的 `base-url` 和 `model-name`，零代码变更。这种设计为 ruoyi-ai 的多厂商统一接入（工厂模式）提供了坚实基础。

## 三、面试高频题

### Q1：Spring Boot 如何集成 LangChain4j？自动配置原理是什么？

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

- **扩展**：这种自动配置模式与 Spring Boot 集成其他中间件的原理一致（如 Redis 的 `RedisAutoConfiguration`、DataSource 的 `DataSourceAutoConfiguration`），体现了 Spring Boot 的"约定优于配置"设计哲学。此外，LangChain4j 的 Starter 还支持 `@ConditionalOnProperty` 实现条件装配，例如只有在配置了 `langchain4j.open-ai.chat-model.api-key` 时才激活 OpenAI 模型。

### Q2：@AiService 注解的工作原理是什么？和传统 @Service 有什么区别？

**考察点：** 面试官想考察候选人对动态代理、注解处理和 AOP 模式的理解，以及 LangChain4j 特有的代理机制与 Spring 常规代理的区别。

**回答框架：**

- **背景**：`@AiService` 是 LangChain4j 提供的方法级注解，用于标注 AI 服务接口。它和 Spring 的 `@Service` 虽然都是注解，但底层实现机制完全不同。

- **方案**：`@AiService` 标注的接口不需要编写实现类，LangChain4j 在启动时通过以下步骤自动生成代理：
  1. 扫描所有标注了 `@AiService` 的接口
  2. 使用 JDK 动态代理（`java.lang.reflect.Proxy`）为接口创建代理实例
  3. 代理实例拦截接口方法调用，通过反射解析方法签名和参数注解
  4. 根据 `@UserMessage`、`@SystemMessage`、`@MemoryId`、`@V` 等注解，动态组装 `ChatRequest`
  5. 调用配置好的 `ChatLanguageModel` 的 `generate()` 方法执行 LLM 推理
  6. 将 LLM 返回的 `Response<AiMessage>` 解析为方法声明的返回值类型

- **深度（核心区别）**：
  - `@Service` 是 Spring 的组件扫描注解，标记一个类作为 Spring 管理的 Bean，需要开发者提供具体实现类。其代理（如果有）通常用于 AOP 切面（如事务、缓存）
  - `@AiService` 是 LangChain4j 的 AI 代理注解，标记一个接口，由 LangChain4j 在运行时动态生成实现。其代理的核心逻辑是 LLM 调用编排，而非 AOP 增强
  - `@Service` 的代理通过 CGLIB（类代理）或 JDK Proxy（接口代理）实现，由 Spring 容器管理；`@AiService` 的代理固定使用 JDK Proxy，由 LangChain4j 的 `AiServices` 工厂创建
  - `@Service` 的方法调用执行的是业务代码；`@AiService` 的方法调用被转换为 LLM API 调用

- **扩展**：`@AiService` 的代理机制还支持高级功能：
  - 对话记忆：通过 `@MemoryId` 参数区分不同会话，自动管理上下文窗口
  - 工具调用：接口方法可以声明 `@Tool` 参数，让 LLM 在回答过程中自动调用工具
  - 流式响应：方法返回 `TokenStream` 类型时，代理自动切换为流式调用模式
  - RAG 集成：通过 `@ContentRetriever` 注入检索器，在 LLM 调用前自动检索相关知识

### Q3：项目中如何实现多模型切换？工厂模式 + 配置中心如何做？

**考察点：** 面试官想考察候选人在复杂业务场景下的架构设计能力，特别是如何利用工厂模式、策略模式和 Spring 的依赖注入实现灵活的多模型切换。

**回答框架：**

- **背景**：企业级 AI 应用通常需要接入多个大模型厂商（如智谱 glm-5.2、阿里通义千问、OpenAI 等），并支持按业务场景、成本策略、可用性等维度动态切换模型。ruoyi-ai 通过工厂模式 + 配置中心实现了"一行配置切换模型，无需修改业务代码"。

- **方案**：采用三层架构设计：
  1. **统一接口层**：定义 `AiChatService` 接口，封装 `chat()`、`chatStream()` 等方法，对外暴露统一 API
  2. **工厂层**：`ChatModelFactory` 根据配置的 `provider` 类型，动态创建对应的 `ChatLanguageModel` 实例
  3. **适配器层**：每个厂商实现一个适配器类，封装厂商 SDK 差异，转换为统一的 `AiChatService` 接口

- **深度（核心实现）**：

```java
/**
 * 统一 AI 对话服务接口。
 * 所有厂商适配器实现此接口，业务代码只依赖此接口。
 */
public interface AiChatService {
    String chat(String message);
    TokenStream chatStream(String message);
}

/**
 * 模型工厂：根据配置动态创建对应的 ChatLanguageModel。
 * 支持通过配置文件或运行时动态切换模型提供商。
 * 标注 @RefreshScope：当配置中心的 ai.* 配置变更时，
 * 工厂 Bean 会重新创建，其 @Value 注入的配置值随即刷新，
 * 从而让依赖它的 DynamicAiChatService 使用到最新的模型配置。
 */
@RefreshScope  // 配置刷新时重新创建此 Bean，使 @Value 配置值同步更新
@Component
public class ChatModelFactory {

    @Value("${ai.provider:openai}")  // 从配置文件读取模型提供商，默认使用 OpenAI 协议
    private String provider;

    @Value("${ai.base-url}")
    private String baseUrl;

    @Value("${ai.api-key}")
    private String apiKey;

    @Value("${ai.model-name}")
    private String modelName;

    /**
     * 根据当前配置创建 ChatLanguageModel 实例。
     * 工厂方法模式：根据 provider 类型选择不同的构建逻辑。
     */
    public ChatLanguageModel createChatModel() {
        return switch (provider) {
            case "openai", "glm" ->
                // OpenAI 兼容协议：智谱 glm、DeepSeek 等均兼容此协议
                OpenAiChatModel.builder()
                    .baseUrl(baseUrl)
                    .apiKey(apiKey)
                    .modelName(modelName)
                    .temperature(0.7)
                    .build();
            case "qwen" ->
                // 通义千问：使用专用适配器
                QwenChatModel.builder()
                    .apiKey(apiKey)
                    .modelName(modelName)
                    .temperature(0.7)
                    .build();
            case "ollama" ->
                // 本地模型：使用 Ollama 适配器
                OllamaChatModel.builder()
                    .baseUrl(baseUrl)
                    .modelName(modelName)
                    .temperature(0.7)
                    .build();
            default ->
                throw new IllegalArgumentException("Unsupported AI provider: " + provider);
        };
    }
}

/**
 * 配置中心动态刷新：结合 Nacos/Apollo 配置中心。
 * 当配置中心的 ai.provider 变更时，Spring Cloud RefreshScope 触发重新创建 Bean。
 */
@RefreshScope  // 配置刷新时重新创建此 Bean
@Component
public class DynamicAiChatService implements AiChatService {

    private final ChatModelFactory factory;

    public DynamicAiChatService(ChatModelFactory factory) {
        this.factory = factory;
    }

    /**
     * 每次调用时通过工厂创建新的 ChatLanguageModel。
     * 配合 @RefreshScope 实现配置变更后的模型切换。
     */
    @Override
    public String chat(String message) {
        ChatLanguageModel model = factory.createChatModel();
        // generate() 返回 Response<AiMessage>，通过 .content().text() 取出文本内容
        return model.generate(message).content().text();
    }
}
```

- **扩展**：多模型切换的进阶设计模式：
  - **策略模式**：将 `AiChatService` 的不同实现注册到 Spring 的 `Map<String, AiChatService>` 中，通过 `@Qualifier` 或自定义路由注解选择实现
  - **责任链模式**：实现主备模型切换，主模型失败时自动降级到备用模型
  - **适配器模式**：封装各厂商 SDK 的异常处理，统一转换为 `AiServiceException`，保证异常处理一致性
  - **结合配置中心**：使用 Nacos/Apollo 的配置动态刷新能力，配合 `@RefreshScope` 实现运行时切换模型，无需重启应用
  - **负载均衡**：支持按权重随机选择模型、最小响应时间优先、一致性哈希（相同用户始终使用同一模型）等策略

## 四、面试避坑指南

- ❌ **常见错误回答 1**："`@AiService` 和 `@Service` 一样，都是 Spring 的注解，用来标记 Bean 的。"

  - ✅ **正确回答思路**：`@AiService` 是 LangChain4j 的注解，不是 Spring 的。它的核心作用是标记一个接口由 LangChain4j 生成动态代理实现，将接口方法调用转换为 LLM API 调用。而 `@Service` 是 Spring 的组件扫描注解，标记一个类作为 Spring 管理的 Bean，两者底层机制完全不同。

- ❌ **常见错误回答 2**："LangChain4j 是 Python LangChain 的 Java 移植版，功能完全一样。"

  - ✅ **正确回答思路**：LangChain4j 和 Python LangChain 是两个独立的项目。LangChain4j 是 Java 生态的原生实现，采用 Java 开发者熟悉的类型安全、POJO、注解、依赖注入等设计哲学，并非简单移植。两者的 API 设计完全不同，但解决的问题域（LLM 集成、Agent、RAG）相似。

- ❌ **常见错误回答 3**："Spring Boot 集成 LangChain4j 需要在启动类上添加 `@EnableLangChain4j` 之类的注解。"

  - ✅ **正确回答思路**：LangChain4j 的 Spring Boot Starter 遵循 Spring Boot 自动配置规范，引入依赖后自动生效，无需手动添加 `@Enable` 注解。Spring Boot 通过 `AutoConfiguration.imports` 文件自动扫描并加载 `LangChain4jAutoConfiguration`。这是 Spring Boot Starter 的通用设计模式，与 `spring-boot-starter-data-redis` 等类似。

- ❌ **常见错误回答 4**："多模型切换只能在启动时配置，运行时无法动态切换。"

  - ✅ **正确回答思路**：通过工厂模式 + 配置中心（Nacos/Apollo）可以实现运行时动态切换。具体做法：模型工厂类使用 `@RefreshScope` 注解，当配置中心的值变更时，Spring 容器会重新创建该 Bean，下次请求时自动使用新的模型配置。此外，还可以通过数据库配置表存储模型映射关系，实现 Admin 后台管理界面的动态切换。

- ⚡ **加分项**：
  - 能对比 LangChain4j 与 Spring AI 的定位差异，分析各自的优劣（LangChain4j 更成熟、支持更多厂商和向量数据库；Spring AI 是 Spring 官方方案，生态整合更深）
  - 能说明 `@AiService` 代理的局限性：接口方法参数类型受限（需使用注解标记）、复杂工具调用需要额外配置、流式返回类型需特殊处理
  - 能结合生产环境经验，说明 AI 服务调用中的超时控制、重试策略、熔断降级（Resilience4j/Sentinel）等工程实践
  - 能提到虚拟线程（Project Loom）在 AI 调用场景中的优势：AI 接口调用是典型的 I/O 密集型操作，虚拟线程可以大幅降低线程开销

## 五、LangChain4j vs Spring AI Alibaba 对比

> 本节是为后续跨项目对比（如 mall-ai-search 项目使用 Spring AI Alibaba）所做的铺垫。了解两者差异，有助于在面试中展现对 Java AI 框架生态的全局视野。

| 对比维度 | LangChain4j 1.13.0 | Spring AI Alibaba |
|----------|-------------------|-------------------|
| **定位** | 独立的 Java AI 编排框架，不依赖 Spring 生态也可使用 | 阿里云通义系列大模型在 Spring AI 体系中的官方集成方案 |
| **Spring 生态整合** | 通过 `langchain4j-spring-boot-starter` 实现自动配置，但核心框架与 Spring 解耦 | 深度绑定 Spring 生态，是 Spring AI 官方 MCP（Model Context Protocol）的阿里云实现 |
| **厂商支持范围** | 20+ LLM 提供商（OpenAI、智谱、通义千问、Ollama 等），30+ Embedding Store | 以阿里云通义系列为核心，通过 Spring AI 抽象层兼容其他厂商 |
| **核心抽象** | `ChatLanguageModel` 统一接口 + `@AiService` 注解代理 + `AiServices` Builder | `ChatModel` 抽象类 + `@Service` 注解 + `Prompt` 模板机制 |
| **RAG 管线** | 完整的 Advanced RAG 管线（QueryTransformer、ContentRouter、ContentAggregator 等模块化组件） | 通过 Spring AI 的 `DocumentRetriever` 和向量存储抽象实现基本 RAG |
| **Agent 支持** | 内置 Agentic API、Tool 注解、MCP 协议集成、langgraph4j 图编排 | 基于 Spring AI 的 Tool Calling 机制，Agent 能力相对基础 |
| **社区活跃度** | 开源社区活跃，版本迭代快（当前 1.13.0），GitHub 13k+ Stars | 阿里巴巴官方维护，更新节奏跟随 Spring AI 版本，文档以中文为主 |
| **适用场景** | 需要多厂商灵活切换、复杂 RAG 管线、Agent 编排的 Java 项目 | 阿里云生态用户，需要与通义大模型深度集成、阿里云服务紧密配合的项目 |
| **学习曲线** | 中等，需要理解 `@AiService` 代理机制和 RAG 管线概念 | 较低，如果熟悉 Spring 生态，上手较快 |
| **生产案例** | ruoyi-ai、多个开源商业项目 | 阿里云客户、Spring AI 官方示例 |

**选型建议：**
- 如果项目需要多厂商灵活接入（如同时使用智谱 glm-5.2、OpenAI 和通义千问），LangChain4j 是更成熟的选择
- 如果项目深度绑定阿里云生态（ALB、OSS、通义大模型），Spring AI Alibaba 的集成体验更流畅
- 两者并非互斥，ruoyi-ai 中 LangChain4j 作为核心 AI 框架，Spring AI 的某些组件（如观测性）可作为补充

## 六、参考资料与扩展阅读

- [LangChain4j 官方文档](https://docs.langchain4j.dev) — 核心 API、Spring Boot Starter 集成指南、最佳实践
- [LangChain4j GitHub 仓库](https://github.com/langchain4j/langchain4j) — 源码、示例、Issues 讨论
- [LangChain4j Spring Boot Starter 源码](https://github.com/langchain4j/langchain4j/tree/main/langchain4j-spring-boot-starter) — 自动配置实现细节，学习 `AutoConfiguration.imports` 注册机制
- [Spring Boot 自动配置官方文档](https://docs.spring.io/spring-boot/reference/using/auto-configuration.html) — 理解 `@ConditionalOnClass`、`@ConditionalOnProperty` 等条件注解原理
- [ruoyi-ai GitHub 仓库](https://github.com/1byteone/ruoyi-ai) — 项目源码，查看完整的 `@AiService` 接口定义和模型工厂实现
- [Spring AI Alibaba 官方文档](https://sca.aliyun.com/ai/) — 与 LangChain4j 对比，了解 Spring AI 体系的集成方案

> **对比预告**：下一篇文章将深入分析 ruoyi-ai 的多厂商大模型统一接入（工厂模式）设计，与我们已在第五节梳理的 LangChain4j vs Spring AI Alibaba 对比相结合，展现完整的 Java AI 框架选型思考，敬请期待。