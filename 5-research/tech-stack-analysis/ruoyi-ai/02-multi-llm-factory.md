# 02 · 多厂商大模型工厂模式：一行配置切换，业务代码零侵入

> 项目支持 OpenAI、DeepSeek、通义千问、智谱、glm-5.2、Ollama、Mimo、Atla、CustomApi 等九种大模型提供商，通过工厂模式 + 策略模式实现"一行配置切换模型，无需修改任何业务代码"的优雅设计。
>
> **对应项目模块：** `ruoyi-chat`（AI 核心模块）

---

## 一、你必须知道的 3 个核心概念

### 1.1 工厂模式（Factory Pattern）

工厂模式是最常用的创建型设计模式之一，核心思想是**将对象的创建和使用分离**。

| 角色 | 在项目中的对应 |
|------|---------------|
| **产品接口** | `ChatLanguageModel`（LangChain4j 定义的统一 LLM 接口） |
| **具体产品** | `OpenAiChatModel`、`QwenChatModel` 等各厂商的模型实现 |
| **工厂接口** | `ModelFactory`（项目自定义的工厂接口） |
| **具体工厂** | `OpenAiModelFactory`、`DeepSeekModelFactory` ... |

**通俗理解：** 我们不需要自己 new 一个"OpenAI 模型对象"，而是告诉工厂"给我一个 OpenAI 模型"，工厂就会根据配置创建好并返回。后续如果要换成 DeepSeek，只需要改配置，不需要改业务代码。

### 1.2 策略模式（Strategy Pattern）

策略模式定义了算法族，分别封装起来，让它们之间可以互相替换。

在项目中，策略模式体现在**运行时切换**能力上。当用户选择不同模型时，系统根据当前策略（配置文件中的 `ai.provider` 值）动态选择对应的模型实现。工厂模式负责"创建"，策略模式负责"切换"——两者结合，实现了创建和使用的完全解耦。

### 1.3 模型实例管理（Model Instance Management）

每个 LLM 提供商都有自己的 API 地址、认证方式、超时设置、模型名称等参数。模型实例管理需要解决以下问题：

- **实例化**：根据配置创建模型实例（API Key、Base URL、超时等）
- **生命周期**：管理实例的创建、复用、销毁
- **线程安全**：模型实例通常是线程安全的，可被多个请求共享
- **配置刷新**：支持运行时动态修改配置（如切换模型）

---

## 二、项目中的实战应用

### 2.1 解决了什么问题

在 AI 应用开发中，多厂商 LLM 接入面临以下痛点：

| 问题 | 描述 | 解决方案 |
|------|------|----------|
| **厂商绑定** | 代码直接依赖某个厂商的 SDK，切换成本高 | 工厂模式封装，统一接口 |
| **配置散乱** | API Key、Base URL 散落在代码各处 | 配置中心集中管理 |
| **异常不统一** | 各厂商异常类型不同，处理逻辑重复 | 统一异常封装 + 适配器模式 |
| **切换成本高** | 换模型要改大量业务代码 | 一行配置切换，零代码修改 |
| **降级困难** | 某厂商不可用时无法自动切换 | 主备模型 + 熔断降级机制 |

**核心目标：** 业务代码只依赖 `ChatLanguageModel` 接口，不关心底层是哪个厂商。通过配置文件即可切换模型，新增厂商也无需修改业务代码。

### 2.2 核心实现（关键代码片段）

#### 2.2.1 统一模型工厂接口

首先定义一个抽象的工厂接口，所有厂商的工厂类都实现该接口：

```java
/**
 * 模型工厂接口 —— 所有厂商的模型工厂都要实现此接口
 * 
 * 职责：根据配置创建一个 ChatLanguageModel 实例
 * 设计：工厂方法模式，每个厂商对应一个具体工厂
 */
public interface ModelFactory {

    /**
     * 创建 ChatLanguageModel 实例
     * 
     * @param config 模型配置（包含 API Key、Base URL、模型名称等）
     * @return ChatLanguageModel LangChain4j 的统一 LLM 接口
     */
    ChatLanguageModel createModel(ModelConfig config);

    /**
     * 获取当前工厂支持的厂商类型
     * 用于工厂注册和路由匹配
     * 
     * @return 厂商标识，如 "openai", "deepseek", "qwen"
     */
    String getProviderType();
}
```

#### 2.2.2 模型配置类

统一的配置模型，将所有厂商的配置参数抽象为通用字段，厂商特有参数通过 `Map<String, Object>` 扩展：

```java
/**
 * 模型配置类 —— 统一所有厂商的配置参数
 * 
 * 从 application.yml 中读取配置，映射到该对象
 * 支持通过 @ConfigurationProperties 绑定
 */
public class ModelConfig {

    // ========== 通用参数 ==========

    /** 厂商类型：openai / deepseek / qwen / zhipu / glm / ollama / mimo / atla / custom */
    private String provider;

    /** API 密钥 */
    private String apiKey;

    /** API 基础地址（各厂商的 endpoint） */
    private String baseUrl;

    /** 模型名称，如 gpt-4o, deepseek-chat, qwen-max */
    private String modelName;

    /** 请求超时时间（毫秒） */
    private Long timeout = 60000L;

    /** 最大重试次数 */
    private Integer maxRetries = 3;

    // ========== 厂商特有参数（通过 Map 扩展） ==========

    /** 厂商特有参数，如 ollama 的 temperature、智谱的 doSample 等 */
    private Map<String, Object> extraParams = new HashMap<>();

    // ========== 可选高级参数 ==========

    /** 是否为主模型（用于主备切换） */
    private Boolean primary = true;

    /** 权重（用于负载均衡） */
    private Integer weight = 10;

    // getters / setters 省略...
}
```

#### 2.2.3 OpenAI 模型工厂实现

以 OpenAI 为例，展示具体工厂的实现方式：

```java
/**
 * OpenAI 模型工厂 —— 创建 OpenAI 的 ChatLanguageModel 实例
 * 
 * 使用 LangChain4j 提供的 OpenAiChatModel 构建器
 * 从 ModelConfig 中读取 API Key、Base URL、模型名称等参数
 */
@Component  // 注册为 Spring Bean，由 FactoryRegistry 自动发现
public class OpenAiModelFactory implements ModelFactory {

    @Override
    public ChatLanguageModel createModel(ModelConfig config) {
        // 使用 LangChain4j 的 OpenAiChatModel 构建器创建模型实例
        return OpenAiChatModel.builder()
                .apiKey(config.getApiKey())           // API 密钥，从配置中心读取
                .baseUrl(config.getBaseUrl())          // API 地址，如 https://api.openai.com
                .modelName(config.getModelName())      // 模型名称，如 gpt-4o
                .timeout(Duration.ofMillis(config.getTimeout()))  // 超时设置
                .maxRetries(config.getMaxRetries())    // 失败重试次数
                .logRequests(true)                     // 开启请求日志（调试用）
                .logResponses(true)                    // 开启响应日志（调试用）
                .build();                              // 构建 ChatLanguageModel 实例
    }

    @Override
    public String getProviderType() {
        return "openai";  // 返回厂商标识，用于配置匹配
    }
}
```

#### 2.2.4 DeepSeek 模型工厂实现

DeepSeek 兼容 OpenAI 的 API 格式，因此可以直接复用 OpenAiChatModel，只需修改 baseUrl：

```java
/**
 * DeepSeek 模型工厂 —— DeepSeek 兼容 OpenAI API 格式
 * 
 * 利用 LangChain4j 的 OpenAiChatModel，只需修改 baseUrl 指向 DeepSeek 的 endpoint
 * 体现了"统一接口，不同实现"的设计思想
 */
@Component
public class DeepSeekModelFactory implements ModelFactory {

    @Override
    public ChatLanguageModel createModel(ModelConfig config) {
        // DeepSeek 兼容 OpenAI 的 API 格式
        // 所以使用 OpenAiChatModel，只需将 baseUrl 指向 DeepSeek 的 API 地址
        return OpenAiChatModel.builder()
                .apiKey(config.getApiKey())
                .baseUrl(config.getBaseUrl())           // https://api.deepseek.com
                .modelName(config.getModelName())       // deepseek-chat
                .timeout(Duration.ofMillis(config.getTimeout()))
                .maxRetries(config.getMaxRetries())
                .build();
    }

    @Override
    public String getProviderType() {
        return "deepseek";
    }
}
```

#### 2.2.5 通义千问模型工厂实现

通义千问（Qwen）通过 LangChain4j 的 QianfanChatModel 或自定义 OpenAiChatModel + 阿里云 DashScope endpoint 实现：

```java
/**
 * 通义千问模型工厂 —— 阿里云通义千问
 * 
 * 阿里云 DashScope 兼容 OpenAI 的 API 格式
 * 因此同样可以使用 OpenAiChatModel，指向阿里云的 endpoint
 */
@Component
public class QwenModelFactory implements ModelFactory {

    @Override
    public ChatLanguageModel createModel(ModelConfig config) {
        // 阿里云 DashScope 兼容 OpenAI API 格式
        // baseUrl 指向 https://dashscope.aliyuncs.com/compatible-mode/v1
        return OpenAiChatModel.builder()
                .apiKey(config.getApiKey())             // 阿里云 DashScope API Key
                .baseUrl(config.getBaseUrl())           // 阿里云 endpoint
                .modelName(config.getModelName())       // qwen-max / qwen-plus / qwen-turbo
                .timeout(Duration.ofMillis(config.getTimeout()))
                .maxRetries(config.getMaxRetries())
                .build();
    }

    @Override
    public String getProviderType() {
        return "qwen";  // 通义千问
    }
}
```

#### 2.2.6 智谱 AI 模型工厂实现

智谱 AI 使用独立的 SDK，但 LangChain4j 也提供了兼容支持：

```java
/**
 * 智谱 AI 模型工厂 —— 智谱 ChatGLM 系列
 * 
 * 智谱 AI 的 API 兼容 OpenAI 格式，使用 OpenAiChatModel 即可
 * 支持 glm-4-plus, glm-4-air 等模型
 */
@Component
public class ZhipuModelFactory implements ModelFactory {

    @Override
    public ChatLanguageModel createModel(ModelConfig config) {
        return OpenAiChatModel.builder()
                .apiKey(config.getApiKey())             // 智谱 API Key
                .baseUrl(config.getBaseUrl())           // https://open.bigmodel.cn/api/paas/v4
                .modelName(config.getModelName())       // glm-4-plus
                .timeout(Duration.ofMillis(config.getTimeout()))
                .maxRetries(config.getMaxRetries())
                .build();
    }

    @Override
    public String getProviderType() {
        return "zhipu";
    }
}
```

#### 2.2.7 Ollama 本地模型工厂实现

Ollama 是本地部署方案，支持在本地运行各种开源模型：

```java
/**
 * Ollama 模型工厂 —— 本地部署的开源模型
 * 
 * Ollama 兼容 OpenAI API 格式
 * 支持本地运行的 llama3、qwen2、mistral 等开源模型
 * 适合数据安全要求高的场景（内网部署）
 */
@Component
public class OllamaModelFactory implements ModelFactory {

    @Override
    public ChatLanguageModel createModel(ModelConfig config) {
        return OpenAiChatModel.builder()
                .apiKey("ollama")                       // Ollama 不需要 API Key
                .baseUrl(config.getBaseUrl())           // http://localhost:11434/v1
                .modelName(config.getModelName())       // llama3.1 / qwen2 / mistral
                .timeout(Duration.ofMillis(config.getTimeout()))
                .maxRetries(config.getMaxRetries())
                .build();
    }

    @Override
    public String getProviderType() {
        return "ollama";
    }
}
```

#### 2.2.8 工厂注册中心（核心调度器）

工厂注册中心是工厂模式的核心，负责管理所有已注册的工厂，并根据配置动态选择具体工厂：

```java
/**
 * 模型工厂注册中心 —— 多厂商 LLM 的调度中枢
 * 
 * 设计亮点：
 * 1. 自动发现：通过 Spring 的依赖注入，自动收集所有 ModelFactory 实现
 * 2. 策略路由：根据配置的 provider 类型，动态选择对应的工厂
 * 3. 模型缓存：缓存已创建的 ChatLanguageModel 实例，避免重复创建
 * 4. 主备切换：支持主模型故障时自动切换到备用模型
 */
@Component
public class ModelFactoryRegistry {

    private static final Logger log = LoggerFactory.getLogger(ModelFactoryRegistry.class);

    // Spring 自动注入所有 ModelFactory 实现
    // 新增厂商时，只需新增一个 @Component 类，无需修改此注册中心
    private final Map<String, ModelFactory> factoryMap;

    // 模型实例缓存，key 为 provider 类型，避免重复创建
    private final Map<String, ChatLanguageModel> modelCache = new ConcurrentHashMap<>();

    /**
     * 构造函数注入 —— Spring 自动收集所有 ModelFactory Bean
     * 
     * @param factories 所有实现了 ModelFactory 接口的 Bean 列表
     */
    public ModelFactoryRegistry(List<ModelFactory> factories) {
        // 将工厂列表转为 Map，key = providerType，value = factory 实例
        this.factoryMap = factories.stream()
                .collect(Collectors.toMap(
                        ModelFactory::getProviderType,  // key: "openai" / "deepseek" / ...
                        Function.identity(),            // value: 工厂实例
                        (existing, replacement) -> existing  // 冲突时保留第一个
                ));
        log.info("模型工厂注册中心初始化完成，已注册厂商：{}", factoryMap.keySet());
    }

    /**
     * 根据配置获取模型实例
     * 
     * @param config 模型配置（包含 provider、apiKey、baseUrl 等）
     * @return ChatLanguageModel 模型实例
     * @throws IllegalArgumentException 不支持的厂商类型
     */
    public ChatLanguageModel getModel(ModelConfig config) {
        // 1. 先查缓存，避免重复创建（模型实例是线程安全的）
        String cacheKey = buildCacheKey(config);
        return modelCache.computeIfAbsent(cacheKey, key -> {
            // 2. 根据 provider 找到对应的工厂
            ModelFactory factory = factoryMap.get(config.getProvider());
            if (factory == null) {
                throw new IllegalArgumentException(
                        "不支持的模型厂商: " + config.getProvider()
                        + "，支持的厂商: " + factoryMap.keySet());
            }

            // 3. 使用工厂创建模型实例
            log.info("创建模型实例：provider={}, model={}", 
                    config.getProvider(), config.getModelName());
            return factory.createModel(config);
        });
    }

    /**
     * 构建缓存 key，确保相同配置复用同一个实例
     */
    private String buildCacheKey(ModelConfig config) {
        return config.getProvider() + ":" + config.getModelName();
    }

    /**
     * 获取所有已注册的厂商类型
     */
    public Set<String> getSupportedProviders() {
        return factoryMap.keySet();
    }

    /**
     * 清空缓存（配置变更时调用，实现运行时切换）
     */
    public void clearCache() {
        modelCache.clear();
        log.info("模型实例缓存已清空，下次请求将重新创建实例");
    }
}
```

#### 2.2.9 统一 AI 服务封装

业务层通过统一的 `AiChatService` 使用模型，完全不感知底层厂商：

```java
/**
 * 统一 AI 对话服务 —— 业务代码的唯一入口
 * 
 * 业务层只依赖此服务，不关心底层是哪个厂商的模型
 * 通过配置中心动态切换，实现"一行配置切换模型"
 */
@Service
public class AiChatService {

    // 注入工厂注册中心，由它负责创建和管理模型实例
    private final ModelFactoryRegistry registry;

    // 注入模型配置（从 application.yml 读取）
    private final AiModelProperties properties;

    public AiChatService(ModelFactoryRegistry registry, AiModelProperties properties) {
        this.registry = registry;
        this.properties = properties;
    }

    /**
     * 发送对话消息
     * 
     * 业务代码只调用 ChatLanguageModel 的接口
     * 不关心底层是 OpenAI 还是 DeepSeek
     * 
     * @param message 用户消息
     * @return AI 回复内容
     */
    public String chat(String message) {
        // 1. 从配置中心获取当前使用的模型配置
        ModelConfig config = properties.getActiveModel();

        // 2. 通过工厂注册中心获取模型实例（自动路由到对应厂商）
        ChatLanguageModel model = registry.getModel(config);

        // 3. 调用模型，发送消息（业务代码只依赖 ChatLanguageModel 接口）
        return model.generate(message);
    }

    /**
     * 流式对话（SSE 模式）
     * 
     * @param message 用户消息
     * @return TokenStream 流式响应，由 Controller 层处理 SSE 推送
     */
    public TokenStream chatStream(String message) {
        ModelConfig config = properties.getActiveModel();
        ChatLanguageModel model = registry.getModel(config);

        // 流式调用，返回 TokenStream
        return model.generate(message);
    }
}
```

#### 2.2.10 配置类与 YAML 配置

通过 Spring Boot 的 `@ConfigurationProperties` 绑定配置，实现"一行切换"：

```java
/**
 * AI 模型配置属性类 —— 绑定 application.yml 中的 ai 配置
 * 
 * 支持多模型配置，通过 active-provider 字段切换当前使用的模型
 * 新增厂商只需在 yaml 中添加新的配置块，无需修改代码
 */
@ConfigurationProperties(prefix = "ai")
@Component
public class AiModelProperties {

    /** 当前激活的厂商类型，如 openai / deepseek / qwen */
    private String activeProvider;

    /** 各厂商的模型配置（Map 结构，key 为 provider 类型） */
    private Map<String, ModelConfig> models = new LinkedHashMap<>();

    /**
     * 获取当前激活的模型配置
     * 这就是"一行配置切换"的核心实现
     */
    public ModelConfig getActiveModel() {
        ModelConfig config = models.get(activeProvider);
        if (config == null) {
            throw new IllegalStateException(
                    "未找到激活的模型配置: " + activeProvider);
        }
        return config;
    }

    // getters / setters
}
```

**application.yml 配置示例：**

```yaml
ai:
  # 核心：一行切换模型，只需修改这一行
  # 可选值: openai / deepseek / qwen / zhipu / glm / ollama / mimo / atla / custom
  active-provider: openai

  # 各厂商的详细配置
  models:
    # ---- OpenAI ----
    openai:
      api-key: sk-xxxxxxxxxxxxxxxx
      base-url: https://api.openai.com
      model-name: gpt-4o
      timeout: 60000
      max-retries: 3
      primary: true
      weight: 10

    # ---- DeepSeek ----
    deepseek:
      api-key: sk-xxxxxxxxxxxxxxxx
      base-url: https://api.deepseek.com
      model-name: deepseek-chat
      timeout: 60000
      max-retries: 3

    # ---- 通义千问 ----
    qwen:
      api-key: sk-xxxxxxxxxxxxxxxx
      base-url: https://dashscope.aliyuncs.com/compatible-mode/v1
      model-name: qwen-max
      timeout: 60000
      max-retries: 3

    # ---- 智谱 AI ----
    zhipu:
      api-key: xxxxxxxxxxxxxxxx
      base-url: https://open.bigmodel.cn/api/paas/v4
      model-name: glm-4-plus
      timeout: 60000
      max-retries: 3

    # ---- Ollama 本地部署 ----
    ollama:
      api-key: ollama
      base-url: http://localhost:11434/v1
      model-name: llama3.1
      timeout: 120000
      max-retries: 0
```

#### 2.2.11 使用示例

业务代码中只需要注入 `AiChatService`，完全不需要关心底层模型：

```java
/**
 * AI 对话控制器 —— 展示业务代码如何使用
 * 
 * 业务代码只依赖 AiChatService，看不到任何厂商相关的代码
 * 切换模型时，只需修改 application.yml 中的 active-provider 字段
 */
@RestController
@RequestMapping("/api/ai/chat")
public class AiChatController {

    @Autowired
    private AiChatService aiChatService;

    /**
     * 同步对话
     * 无论底层是 OpenAI 还是 DeepSeek，业务代码完全不变
     */
    @PostMapping("/sync")
    public Result<String> chat(@RequestBody ChatRequest request) {
        // 业务代码不关心底层是哪个厂商
        String reply = aiChatService.chat(request.getMessage());
        return Result.success(reply);
    }

    /**
     * 流式对话（SSE 推送）
     */
    @PostMapping("/stream")
    public SseEmitter chatStream(@RequestBody ChatRequest request) {
        SseEmitter emitter = new SseEmitter(0L);  // 不超时
        TokenStream tokenStream = aiChatService.chatStream(request.getMessage());

        tokenStream.onPartialResponse(chunk -> {
            // 逐 token 推送
            emitter.send(SseEmitter.event().data(chunk));
        }).onCompleteResponse(response -> {
            emitter.complete();  // 推送完成
        }).onError(emitter::completeWithError);  // 异常处理

        return emitter;
    }
}
```

### 2.3 设计亮点

#### 2.3.1 开闭原则的完美实践

整个架构对扩展开放、对修改关闭。新增一个模型厂商只需要：

1. 新建一个工厂类实现 `ModelFactory` 接口（加上 `@Component` 注解）
2. 在 `application.yml` 中添加对应配置

**不需要修改**任何已存在的类（注册中心、业务服务、控制器都不需要修改）。

#### 2.3.2 配置中心驱动

通过 `@ConfigurationProperties` 将配置集中管理，支持：

- **静态切换**：修改 `active-provider` 后重启应用
- **动态切换**：配合配置中心（Nacos/Apollo），修改配置后实时刷新，无需重启
- **多模型共存**：可以同时配置多个厂商，按需切换

#### 2.3.3 统一响应格式

所有厂商的返回结果都被统一为 `ChatLanguageModel` 的返回格式，业务层感知不到差异。异常也被统一封装为 `AiServiceException`，上层统一处理。

#### 2.3.4 异常处理与降级

```java
/**
 * 增强版 AI 对话服务 —— 加入异常处理和降级机制
 * 
 * 生产环境必须考虑：网络超时、API 限流、模型不可用等异常场景
 */
@Service
public class ResilientAiChatService {

    // 主模型配置（当前激活的模型）
    // 备用模型配置（主模型不可用时切换）
    private final AiModelProperties properties;
    private final ModelFactoryRegistry registry;

    /**
     * 带降级的对话方法
     * 主模型失败时自动切换到备用模型
     */
    public String chatWithFallback(String message) {
        // 1. 获取当前激活的模型配置（主模型）
        ModelConfig primaryConfig = properties.getActiveModel();

        try {
            // 2. 尝试使用主模型
            ChatLanguageModel primary = registry.getModel(primaryConfig);
            return primary.generate(message);
        } catch (Exception e) {
            log.warn("主模型 {} 调用失败，尝试备用模型", primaryConfig.getProvider(), e);

            // 3. 主模型失败，查找备用模型
            ModelConfig fallbackConfig = findFallbackConfig(primaryConfig);
            if (fallbackConfig != null) {
                ChatLanguageModel fallback = registry.getModel(fallbackConfig);
                return fallback.generate(message);
            }

            // 4. 所有模型都不可用，抛出统一异常
            throw new AiServiceException("所有模型服务均不可用，请稍后重试");
        }
    }

    /**
     * 查找备用模型配置
     * 遍历所有模型配置，找到第一个非主模型且配置完整的
     */
    private ModelConfig findFallbackConfig(ModelConfig exclude) {
        return properties.getModels().values().stream()
                .filter(c -> !c.getProvider().equals(exclude.getProvider()))
                .filter(c -> StringUtils.hasText(c.getApiKey()))
                .findFirst()
                .orElse(null);
    }
}
```

#### 2.3.5 Rate Limiting 限流保护

```java
/**
 * 限流包装器 —— 防止 API 调用超支
 * 
 * 各厂商 API 都有调用频率限制（如 OpenAI 的 RPM/TPM 限制）
 * 通过限流保护避免触发限流惩罚
 */
public class RateLimitedChatModel implements ChatLanguageModel {

    private final ChatLanguageModel delegate;  // 被包装的模型
    private final RateLimiter rateLimiter;     // 限流器

    public RateLimitedChatModel(ChatLanguageModel delegate, double permitsPerSecond) {
        this.delegate = delegate;
        // Guava 的 RateLimiter，每秒允许的请求数
        this.rateLimiter = RateLimiter.create(permitsPerSecond);
    }

    @Override
    public Response<AiMessage> generate(ChatRequest request) {
        // 获取令牌，阻塞直到获取成功
        rateLimiter.acquire();
        return delegate.generate(request);
    }

    @Override
    public TokenStream generate(String userMessage) {
        rateLimiter.acquire();
        return delegate.generate(userMessage);
    }
}
```

---

## 三、面试高频题

### Q1: 设计一个多厂商大模型统一接入方案，要考虑哪些方面？

**回答思路：**

设计多厂商大模型统一接入方案，需要从以下六个维度全面考虑：

**1. 接口抽象层（核心）**
- 定义统一的 LLM 调用接口（如 `ChatLanguageModel`）
- 各厂商适配器实现该接口，屏蔽 SDK 差异
- 业务代码只依赖抽象接口，不依赖具体实现

**2. 配置管理**
- 集中管理 API Key、Base URL、模型名称等参数
- 支持配置中心（Nacos/Apollo）动态刷新
- 敏感信息加密存储（API Key 不应明文存储）

**3. 工厂创建**
- 工厂模式根据配置动态创建模型实例
- 实例缓存避免重复创建（模型实例是线程安全的）
- 支持运行时切换模型（缓存失效 + 重新创建）

**4. 异常统一**
- 各厂商异常类型不同（OpenAI 的 `OpenAiHttpException`、超时、限流等）
- 统一包装为业务异常（`AiServiceException`）
- 全局异常处理返回统一响应格式

**5. 容错与降级**
- 重试机制：网络抖动临时失败可重试
- 主备切换：主模型不可用时自动切到备用模型
- 熔断保护：连续失败后熔断，避免雪崩
- 兜底回复：所有模型都不可用时返回预设回复

**6. 可观测性**
- 记录每次调用的耗时、模型、Token 消耗
- 通过 Metrics 监控模型健康状态
- 日志链路追踪，便于问题排查

**代码结构示意：**

```
├── factory/
│   ├── ModelFactory.java              # 工厂接口
│   ├── ModelFactoryRegistry.java      # 工厂注册中心
│   ├── OpenAiModelFactory.java        # OpenAI 工厂
│   ├── DeepSeekModelFactory.java      # DeepSeek 工厂
│   ├── QwenModelFactory.java          # 通义千问工厂
│   ├── ZhipuModelFactory.java         # 智谱工厂
│   ├── OllamaModelFactory.java        # Ollama 工厂
│   └── ...
├── config/
│   ├── ModelConfig.java               # 模型配置 POJO
│   └── AiModelProperties.java         # 配置绑定类
├── service/
│   ├── AiChatService.java             # 统一 AI 服务
│   └── ResilientAiChatService.java    # 带降级的 AI 服务
└── exception/
    ├── AiServiceException.java        # 统一异常
    └── GlobalExceptionHandler.java    # 全局异常处理
```

### Q2: 工厂模式 + 策略模式在这个场景下的优缺点？

**回答思路：**

| 维度 | 优点 | 缺点 |
|------|------|------|
| **解耦性** | 业务代码完全解耦，不依赖具体厂商 | 架构复杂度增加，接口定义需要前瞻性设计 |
| **扩展性** | 新增厂商只需新增类 + 配置，符合开闭原则 | 工厂类数量随厂商数量线性增长 |
| **可维护性** | 各厂商实现独立，修改互不影响 | 需要维护统一接口的稳定性，接口变更影响所有实现 |
| **测试性** | 可以轻松 Mock 模型实例进行单元测试 | 需要为每个厂商编写独立的集成测试 |
| **运行时切换** | 配合配置中心可实现运行时动态切换 | 切换时需要处理缓存失效，已有连接需要优雅关闭 |

**工厂模式 vs 策略模式的具体分工：**

```
工厂模式（创建时）         策略模式（运行时）
┌─────────────────┐       ┌─────────────────┐
│ 根据 provider    │       │ 根据当前策略     │
│ 创建模型实例     │  ──→  │ 选择模型实例     │
│ 各工厂独立实现   │       │ 运行时动态切换   │
│ 一次创建，缓存复用│       │ 支持主备降级     │
└─────────────────┘       └─────────────────┘
```

**在项目中两者的结合方式：**

```java
// 1. 工厂模式：创建实例（启动时执行一次）
ModelFactory factory = factoryMap.get("openai");
ChatLanguageModel model = factory.createModel(config);

// 2. 策略模式：切换策略（运行时动态切换）
// 修改配置 active-provider: deepseek
// 下次请求时，registry.getModel() 返回 DeepSeek 的实例
String activeProvider = config.getActiveProvider();  // "deepseek"
ChatLanguageModel model = registry.getModel(config); // 自动路由到 DeepSeek
```

### Q3: 如果新增一个模型厂商，需要修改哪些代码？如何做到开闭原则？

**回答思路：**

**需要做的工作（仅 2 步）：**

**第 1 步：新建工厂类**

```java
/**
 * 新增厂商：智谱 AI 的 glm-5.2 模型
 * 
 * 只需要新增这一个类，不需要修改任何已存在的代码
 * 体现了"对扩展开放，对修改关闭"的开闭原则
 */
@Component  // 通过 @Component 自动注册到工厂列表
public class GlmModelFactory implements ModelFactory {

    @Override
    public ChatLanguageModel createModel(ModelConfig config) {
        return OpenAiChatModel.builder()
                .apiKey(config.getApiKey())
                .baseUrl(config.getBaseUrl())           // 智谱 API 地址
                .modelName(config.getModelName())       // glm-5.2
                .timeout(Duration.ofMillis(config.getTimeout()))
                .maxRetries(config.getMaxRetries())
                .build();
    }

    @Override
    public String getProviderType() {
        return "glm";  // 返回厂商标识
    }
}
```

**第 2 步：添加配置文件**

```yaml
ai:
  active-provider: glm    # 切换到 glm-5.2
  models:
    # ... 其他厂商配置不变 ...
    
    # 新增：glm-5.2 配置
    glm:
      api-key: xxxxxxxxxxxxxxxx
      base-url: https://open.bigmodel.cn/api/paas/v4
      model-name: glm-5.2
      timeout: 60000
      max-retries: 3
```

**不需要修改的代码：**

| 类 | 为什么不需要修改 |
|----|-----------------|
| `ModelFactory` 接口 | 没变化，新厂商同样实现该接口 |
| `ModelFactoryRegistry` 注册中心 | 通过 `@Component` 自动发现，无需手动注册 |
| `AiChatService` 业务服务 | 只依赖 `ChatLanguageModel` 接口，不关心厂商 |
| `AiChatController` 控制器 | 业务代码完全不变 |
| `AiModelProperties` 配置类 | 配置结构不变，新增厂商只是 Map 中的一个新条目 |

**关键设计保证开闭原则：**

```
1. 依赖注入（DI）自动收集：
   @Component → Spring 自动扫描 → 注入到注册中心
   新增 @Component 类即自动注册，无需修改注册中心代码

2. 统一的接口契约：
   所有工厂实现 ModelFactory 接口
   注册中心通过接口调用，不依赖具体实现类

3. 配置驱动：
   通过 application.yml 的 Map 结构支持动态扩展
   新增厂商只需添加新的配置块
```

---

## 四、面试避坑指南

### 4.1 不要混淆"工厂模式"和"策略模式"的职责

**常见错误：** 面试时说"工厂模式切换模型"。

**纠正：** 工厂模式负责"创建"，策略模式负责"切换"。正确的说法是"工厂模式创建模型实例，策略模式在运行时切换使用哪个模型"。

### 4.2 不要忽略模型实例的生命周期管理

**常见错误：** 只关注工厂创建，忽略缓存、复用和销毁。

**关键点：**
- `ChatLanguageModel` 实例是线程安全的，可以全局复用
- 每次请求都创建新实例是严重的性能浪费
- 配置变更时需要清空缓存，重新创建实例
- 注意连接池的优雅关闭（如 HTTP 连接池）

### 4.3 不要忽略异常处理的统一性

**常见错误：** 各厂商的异常在各业务层分散处理。

**关键点：**
- 所有厂商异常统一转换为 `AiServiceException`
- 在适配器层捕获 SDK 异常，转换为统一异常
- 全局异常处理器统一返回格式
- 区分"可重试异常"（网络超时）和"不可重试异常"（认证失败）

### 4.4 不要忽略 Rate Limiting

**常见错误：** 生产环境未做限流，导致 API 调用超支或触发限流惩罚。

**关键点：**
- 各厂商 API 都有频率限制（OpenAI 的 RPM/TPM 限制）
- 超过限制会被封禁或产生额外费用
- 使用 Guava RateLimiter 或 Sentinel 做限流保护
- 不同厂商的限流策略不同，需要分别配置

### 4.5 不要忽略配置安全

**常见错误：** API Key 明文存储在配置文件中并提交到 Git。

**关键点：**
- API Key 应使用环境变量或配置中心加密存储
- 配置文件中的 API Key 应使用占位符 `${AI_OPENAI_API_KEY}`
- 生产环境通过 KMS（密钥管理服务）管理
- Git 仓库中不应包含任何真实 API Key

### 4.6 不要忽略模型的差异化能力

**常见错误：** 假定所有模型能力相同，一套 Prompt 所有模型通用。

**关键点：**
- 不同模型的上下文窗口不同（如 GPT-4o 128K vs 某些模型 8K）
- 不同模型的 Function Calling 能力不同
- 不同模型的价格差异巨大（成本优化）
- 建议为不同模型配置不同的 Prompt 模板

---

## 五、参考资料与扩展阅读

### 项目源码
- [ruoyi-ai GitHub 仓库](https://github.com/1byteone/ruoyi-ai)

### 设计模式参考
- 《设计模式：可复用面向对象软件的基础》—— GoF 23 种设计模式
- 工厂模式（Factory Method）—— 定义创建对象的接口，让子类决定实例化哪个类
- 策略模式（Strategy）—— 定义算法族，分别封装，使它们可以互相替换

### LangChain4j 相关
- [LangChain4j 官方文档](https://docs.langchain4j.dev) — ChatLanguageModel 统一 API 设计
- [LangChain4j GitHub 仓库](https://github.com/langchain4j/langchain4j) — 各 Provider 集成实现

### 生产实践参考
- Spring Boot `@ConfigurationProperties` 绑定配置的最佳实践
- Nacos / Apollo 配置中心动态刷新原理
- Resilience4j 熔断降级机制
- Guava RateLimiter 令牌桶限流算法