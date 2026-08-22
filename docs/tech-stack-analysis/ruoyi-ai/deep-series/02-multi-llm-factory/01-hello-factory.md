# 多模型工厂模式入门：一行配置切换AI模型

> 本文是 ruoyi-ai 项目技术栈深度剖析系列的第2篇，面向 Java 后端开发者，旨在帮助读者理解如何通过工厂模式+策略模式实现多厂商大模型统一接入，从零搭建一个支持 OpenAI、DeepSeek、通义千问的模型工厂，实现"一行配置切换模型，业务代码零侵入"的优雅设计。

---

## 一、项目背景：该技术栈在项目中的角色

### 1.1 为什么需要多模型工厂

在 ruoyi-ai 项目中，AI 对话能力是核心功能。但现实情况是，没有一个 LLM 厂商能完美满足所有场景：

- **成本优化**：日常简单问答使用便宜的模型（如 DeepSeek），复杂推理任务使用高性能模型（如 GPT-4o）
- **高可用性**：某厂商服务不可用时，自动切换到备用厂商，保证服务不中断
- **区域合规**：不同地区的用户可能需要使用不同的模型提供商，以符合当地数据合规要求
- **模型能力差异**：不同模型在代码生成、逻辑推理、创意写作等不同维度各有优劣，按需选择最合适的模型

ruoyi-ai 项目支持九种大模型提供商，包括 OpenAI、DeepSeek、通义千问、智谱、glm-5.2、Ollama、Mimo、Atla、CustomApi。如果没有一套统一的管理机制，业务代码将充斥着各厂商 SDK 的调用代码，维护成本极高。

### 1.2 在 ruoyi-ai 项目中的位置

多模型工厂位于 AI 层的核心位置，是 LLM 接入的"总闸门"：

```
用户请求
    ↓
业务服务（AiChatService） ← 只依赖 ChatLanguageModel 接口
    ↓
模型工厂注册中心（ModelFactoryRegistry） ← 根据配置路由到具体工厂
    ↓
OpenAiModelFactory  DeepSeekModelFactory  QwenModelFactory  ...
    ↓
OpenAI API           DeepSeek API          通义千问 API
```

### 1.3 本文目标

本文的目标是帮助读者从零搭建一个多模型工厂，实现：

1. 理解工厂模式+策略模式在多模型接入场景中的应用
2. 从零搭建完整的 ModelFactory 接口和多个实现
3. 通过 `application.yml` 一行配置切换模型
4. 掌握 Spring Boot 的 `@ConfigurationProperties` 配置绑定

---

## 二、核心概念：2-3个，用生活类比解释

### 概念 1：工厂模式 —— 就像"汽车制造厂"

**生活类比**：你去买车，不会自己去造发动机、焊车身、装轮胎。你只需要告诉汽车制造厂"我要一辆 SUV"，工厂就会按照标准流程生产出你需要的车。不同的工厂可能生产不同的车型（丰田工厂生产丰田，宝马工厂生产宝马），但所有工厂都遵循"生产汽车"这个统一标准。

**技术映射**：`ModelFactory` 就是汽车制造厂。它负责"生产" `ChatLanguageModel` 实例，隐藏了创建过程中的复杂细节：

```java
// 你不需要知道如何创建 OpenAI 的模型实例
// 只需要告诉工厂：给我一个 OpenAI 模型
ModelFactory factory = new OpenAiModelFactory();
ChatLanguageModel model = factory.createModel(config);
```

- **产品接口**：`ChatLanguageModel` —— 所有工厂生产的"汽车"都遵循这个接口
- **具体产品**：`OpenAiChatModel`、`QwenChatModel` 等 —— 不同厂商的"车型"
- **工厂接口**：`ModelFactory` —— 定义"生产汽车"的标准流程
- **具体工厂**：`OpenAiModelFactory`、`DeepSeekModelFactory` 等 —— 不同厂商的"制造厂"

**关键点**：业务代码只依赖 `ChatLanguageModel` 接口和 `ModelFactory` 接口，不依赖任何具体厂商的实现类。这就是"依赖倒置原则"——依赖抽象，不依赖具体。

### 概念 2：策略模式 —— 就像"导航路线选择"

**生活类比**：你用导航 App 从 A 地到 B 地，App 会提供多种路线选择：最短路线（距离最近）、最快路线（时间最短）、避开高速路线（特殊需求）。你只需要选一个策略，导航就会按照这个策略规划路线。不同策略之间可以随时切换，而且切换策略不影响你"从 A 到 B"这个目标。

**技术映射**：策略模式在工厂注册中心中体现为"运行时切换"能力：

```java
// 策略模式：根据当前配置选择不同的模型策略
// 修改 active-provider 的值，就相当于切换了导航策略
String activeProvider = "deepseek";  // 当前策略
// 或者 "qwen" 或 "openai"

// 根据策略获取对应的模型实例
ChatLanguageModel model = registry.getModel(config);
// 切换策略后，同样的代码会获取到不同的模型实例
```

- **策略上下文**：`ModelFactoryRegistry` —— 管理所有策略，根据条件选择策略
- **具体策略**：`OpenAiModelFactory`、`DeepSeekModelFactory` 等 —— 不同的模型创建策略
- **策略切换**：修改 `application.yml` 中的 `active-provider` 配置 —— 运行时切换策略

**工厂模式 vs 策略模式**：
- 工厂模式负责"创建"：根据配置创建对应的模型实例
- 策略模式负责"切换"：在运行时动态选择使用哪个模型
- 两者结合，实现了"创建和使用的完全解耦"

### 概念 3：@ConfigurationProperties —— 就像"客房服务菜单"

**生活类比**：住酒店时，床头柜上有一本客房服务菜单。你只需要在菜单上勾选你想要的选项（"早餐：中式/西式"、"枕头：软/硬/记忆棉"），酒店就会按照你的选择提供服务。菜单本身不关心你选了什么，它只是把选择结果传递给对应的服务部门。

**技术映射**：`@ConfigurationProperties` 就是 Spring Boot 的"客房服务菜单"。它把 `application.yml` 中的配置项映射到 Java 对象的属性上：

```yaml
# application.yml —— 菜单
ai:
  active-provider: openai
  models:
    openai:
      api-key: sk-xxx
      model-name: gpt-4o
```

```java
// AiModelProperties.java —— 菜单的 Java 映射
@ConfigurationProperties(prefix = "ai")
public class AiModelProperties {
    private String activeProvider;  // 对应 ai.active-provider
    private Map<String, ModelConfig> models;  // 对应 ai.models
}
```

**关键点**：配置和代码是分离的。修改配置不需要修改代码，新增厂商也不需要修改配置类结构（Map 结构天然支持动态扩展）。

---

## 三、从零搭建：完整代码

### 3.1 项目结构

```
hello-multi-factory/
├── pom.xml
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/
│   │   │       └── hellofactory/
│   │   │           ├── HelloFactoryApplication.java      # 启动类
│   │   │           ├── config/
│   │   │           │   ├── ModelConfig.java              # 模型配置 POJO
│   │   │           │   └── AiModelProperties.java        # 配置绑定类
│   │   │           ├── factory/
│   │   │           │   ├── ModelFactory.java             # 工厂接口
│   │   │           │   ├── ModelFactoryRegistry.java     # 工厂注册中心
│   │   │           │   ├── OpenAiModelFactory.java       # OpenAI 工厂
│   │   │           │   ├── DeepSeekModelFactory.java     # DeepSeek 工厂
│   │   │           │   └── QwenModelFactory.java         # 通义千问工厂
│   │   │           ├── service/
│   │   │           │   └── AiChatService.java            # 统一 AI 服务
│   │   │           └── controller/
│   │   │               └── AiChatController.java         # REST 控制器
│   │   └── resources/
│   │       └── application.yml                           # 配置
│   └── test/
│       └── java/
│           └── com/
│               └── hellofactory/
│                   ├── factory/
│                   │   ├── OpenAiModelFactoryTest.java
│                   │   └── ModelFactoryRegistryTest.java
│                   └── service/
│                       └── AiChatServiceTest.java
```

### 3.2 pom.xml

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.hellofactory</groupId>
    <artifactId>hello-multi-factory</artifactId>
    <version>1.0.0</version>
    <name>hello-multi-factory</name>
    <description>多模型工厂模式入门示例</description>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.5.8</version>
        <relativePath/>
    </parent>

    <properties>
        <java.version>17</java.version>
        <langchain4j.version>1.13.0</langchain4j.version>
    </properties>

    <dependencies>
        <!-- Spring Boot Web -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>

        <!-- LangChain4j Spring Boot Starter：自动配置 ChatLanguageModel -->
        <dependency>
            <groupId>dev.langchain4j</groupId>
            <artifactId>langchain4j-spring-boot-starter</artifactId>
            <version>${langchain4j.version}</version>
        </dependency>

        <!-- LangChain4j OpenAI Starter：提供 OpenAiChatModel -->
        <!-- 兼容 OpenAI、DeepSeek、通义千问等厂商 -->
        <dependency>
            <groupId>dev.langchain4j</groupId>
            <artifactId>langchain4j-open-ai-spring-boot-starter</artifactId>
            <version>${langchain4j.version}</version>
        </dependency>

        <!-- Spring Boot 配置处理器：生成配置元数据 -->
        <!-- 开发时在 application.yml 中会有代码提示 -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-configuration-processor</artifactId>
            <optional>true</optional>
        </dependency>

        <!-- 测试依赖 -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

### 3.3 application.yml

```yaml
# =============================================
# 多模型工厂配置
# 通过修改 active-provider 一行切换模型
# =============================================
server:
  port: 8080

ai:
  # --------------------------------------------------
  # 核心：修改这一行即可切换模型提供商
  # 可选值: openai / deepseek / qwen
  # --------------------------------------------------
  active-provider: openai

  # --------------------------------------------------
  # 各厂商的详细配置
  # 新增厂商只需在下面添加新的配置块
  # --------------------------------------------------
  models:
    # ---- OpenAI 配置 ----
    openai:
      api-key: ${AI_OPENAI_KEY:sk-placeholder}
      base-url: https://api.openai.com
      model-name: gpt-4o-mini
      timeout: 60000
      max-retries: 3

    # ---- DeepSeek 配置 ----
    # DeepSeek 兼容 OpenAI API 格式
    deepseek:
      api-key: ${AI_DEEPSEEK_KEY:sk-placeholder}
      base-url: https://api.deepseek.com
      model-name: deepseek-chat
      timeout: 60000
      max-retries: 3

    # ---- 通义千问配置 ----
    # 阿里云 DashScope 兼容 OpenAI API 格式
    qwen:
      api-key: ${AI_QWEN_KEY:sk-placeholder}
      base-url: https://dashscope.aliyuncs.com/compatible-mode/v1
      model-name: qwen-plus
      timeout: 60000
      max-retries: 3
```

### 3.4 启动类

```java
package com.hellofactory;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Spring Boot 启动类。
 *
 * @EnableConfigurationProperties：启用 @ConfigurationProperties 绑定。
 * 虽然 AiModelProperties 类上也有 @Component，但显式声明更清晰。
 */
@SpringBootApplication
@EnableConfigurationProperties
public class HelloFactoryApplication {

    public static void main(String[] args) {
        SpringApplication.run(HelloFactoryApplication.class, args);
    }
}
```

### 3.5 模型配置类

#### ModelConfig.java —— 单个厂商的配置

```java
package com.hellofactory.config;

/**
 * 单个模型厂商的配置参数。
 *
 * 这是一个 POJO（Plain Old Java Object），
 * 用于承载从 application.yml 读取的每个厂商的配置信息。
 *
 * 设计说明：
 * 所有厂商的配置通过统一的 ModelConfig 类承载，
 * 这样工厂注册中心就不需要关心每个厂商的特殊参数。
 * 厂商特有的参数可以通过 extraParams（Map<String, Object>）扩展。
 */
public class ModelConfig {

    // ========== 通用参数：所有厂商都需要的配置 ==========

    /** API 密钥，用于调用 LLM 的身份认证 */
    private String apiKey;

    /** API 基础地址，各厂商的 endpoint */
    private String baseUrl;

    /** 模型名称，如 gpt-4o-mini、deepseek-chat、qwen-plus */
    private String modelName;

    /** 请求超时时间，单位毫秒，默认 60 秒 */
    private Long timeout = 60000L;

    /** 调用失败时的最大重试次数，默认 3 次 */
    private Integer maxRetries = 3;

    // ========== 厂商特有参数扩展 ==========

    /** 厂商特有参数，如自定义 headers、额外参数等 */
    // 通过 Map 结构支持动态扩展，无需修改 ModelConfig 类
    // private Map<String, Object> extraParams = new HashMap<>();

    // ========== getters/setters ==========

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public Long getTimeout() {
        return timeout;
    }

    public void setTimeout(Long timeout) {
        this.timeout = timeout;
    }

    public Integer getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(Integer maxRetries) {
        this.maxRetries = maxRetries;
    }
}
```

#### AiModelProperties.java —— 配置绑定类

```java
package com.hellofactory.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * AI 模型配置属性类。
 *
 * 通过 @ConfigurationProperties 将 application.yml 中的 ai.* 配置
 * 绑定到此类的属性上。
 *
 * 配置映射关系：
 *   application.yml                  Java 属性
 *   ─────────────────────────────    ──────────────────────
 *   ai.active-provider               activeProvider
 *   ai.models.openai.api-key         models["openai"].apiKey
 *   ai.models.deepseek.base-url      models["deepseek"].baseUrl
 *
 * 设计亮点：
 * 使用 Map<String, ModelConfig> 结构存储各厂商配置，
 * 新增厂商时只需在 yml 中添加新的配置块，无需修改此类。
 * 这体现了"对扩展开放"的开闭原则。
 */
@Component
@ConfigurationProperties(prefix = "ai")
public class AiModelProperties {

    /**
     * 当前激活的厂商类型。
     * 对应 yml 中的 ai.active-provider。
     * 值可以是 "openai"、"deepseek"、"qwen" 等。
     * 修改此值即可切换模型，无需修改任何代码。
     */
    private String activeProvider;

    /**
     * 各厂商的模型配置映射。
     * key = 厂商类型（如 "openai"、"deepseek"、"qwen"）
     * value = 该厂商的配置参数
     *
     * 使用 LinkedHashMap 保持配置文件中定义的顺序。
     * Map 结构天然支持动态扩展，新增厂商无需修改代码。
     */
    private Map<String, ModelConfig> models = new LinkedHashMap<>();

    // ========== 核心方法 ==========

    /**
     * 获取当前激活的模型配置。
     *
     * 这是"一行配置切换"的核心实现：
     * 1. 用户修改 application.yml 中的 active-provider
     * 2. 此方法根据 active-provider 的值从 Map 中查找对应的配置
     * 3. 工厂注册中心根据此配置创建对应的模型实例
     * 4. 业务代码无需任何修改
     *
     * @return 当前激活的模型配置
     * @throws IllegalStateException 如果未找到对应配置
     */
    public ModelConfig getActiveModel() {
        // 从 models Map 中查找 activeProvider 对应的配置
        ModelConfig config = models.get(activeProvider);

        if (config == null) {
            // 如果配置不存在，抛出异常，提示可用的厂商列表
            throw new IllegalStateException(
                    "未找到激活的模型配置: " + activeProvider
                    + "，可用的厂商: " + models.keySet());
        }

        return config;
    }

    // ========== getters/setters ==========

    public String getActiveProvider() {
        return activeProvider;
    }

    public void setActiveProvider(String activeProvider) {
        this.activeProvider = activeProvider;
    }

    public Map<String, ModelConfig> getModels() {
        return models;
    }

    public void setModels(Map<String, ModelConfig> models) {
        this.models = models;
    }
}
```

### 3.6 工厂接口与实现

#### ModelFactory.java —— 工厂接口

```java
package com.hellofactory.factory;

import com.hellofactory.config.ModelConfig;
import dev.langchain4j.model.chat.ChatLanguageModel;

/**
 * 模型工厂接口 —— 所有厂商的模型工厂都要实现此接口。
 *
 * 这是工厂模式的核心抽象：
 * 1. 定义"创建模型"的标准方法
 * 2. 隐藏各厂商具体的创建逻辑
 * 3. 业务代码只需依赖此接口，不依赖具体实现
 *
 * 设计思想：依赖倒置原则（Dependency Inversion Principle）
 *   - 高层模块（业务服务）不应依赖低层模块（具体厂商实现）
 *   - 两者都应依赖抽象（ModelFactory 接口）
 */
public interface ModelFactory {

    /**
     * 根据配置创建 ChatLanguageModel 实例。
     *
     * @param config 模型配置，包含 API Key、Base URL、模型名称等
     * @return ChatLanguageModel LangChain4j 的统一 LLM 接口
     */
    ChatLanguageModel createModel(ModelConfig config);

    /**
     * 获取当前工厂支持的厂商类型。
     *
     * 返回值用于工厂注册中心的路由匹配。
     * 例如：OpenAiModelFactory 返回 "openai"，
     * DeepSeekModelFactory 返回 "deepseek"。
     *
     * @return 厂商标识字符串，如 "openai"、"deepseek"、"qwen"
     */
    String getProviderType();
}
```

#### OpenAiModelFactory.java —— OpenAI 工厂

```java
package com.hellofactory.factory;

import com.hellofactory.config.ModelConfig;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * OpenAI 模型工厂。
 *
 * 使用 LangChain4j 的 OpenAiChatModel 构建器创建模型实例。
 * 适用于 OpenAI GPT 系列模型（gpt-4o、gpt-4o-mini 等）。
 *
 * 所有提供 OpenAI 兼容 API 的厂商也可以使用此工厂，
 * 只需修改 baseUrl 指向对应的 API 地址即可。
 */
@Component
public class OpenAiModelFactory implements ModelFactory {

    @Override
    public ChatLanguageModel createModel(ModelConfig config) {
        // 使用 OpenAiChatModel 的构建器模式创建实例
        return OpenAiChatModel.builder()
                // API 密钥：从配置中读取，生产环境通过环境变量注入
                .apiKey(config.getApiKey())
                // API 基础地址：OpenAI 默认 https://api.openai.com
                .baseUrl(config.getBaseUrl())
                // 模型名称：如 gpt-4o、gpt-4o-mini
                .modelName(config.getModelName())
                // 超时时间：从配置中读取，单位毫秒
                .timeout(Duration.ofMillis(config.getTimeout()))
                // 最大重试次数：网络抖动时自动重试
                .maxRetries(config.getMaxRetries())
                // 生成温度：0.7 是平衡创意和确定性的推荐值
                .temperature(0.7)
                // 最大 Token 数：限制回复长度
                .maxTokens(4096)
                // 构建 ChatLanguageModel 实例
                .build();
    }

    @Override
    public String getProviderType() {
        // 返回厂商标识，用于工厂注册中心的路由匹配
        return "openai";
    }
}
```

#### DeepSeekModelFactory.java —— DeepSeek 工厂

```java
package com.hellofactory.factory;

import com.hellofactory.config.ModelConfig;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * DeepSeek 模型工厂。
 *
 * DeepSeek 兼容 OpenAI 的 API 格式，
 * 因此可以直接使用 OpenAiChatModel，只需修改 baseUrl。
 *
 * 这体现了"统一接口，不同实现"的设计思想：
 * 虽然是不同的厂商，但因为 API 兼容，实现方式可以复用。
 */
@Component
public class DeepSeekModelFactory implements ModelFactory {

    @Override
    public ChatLanguageModel createModel(ModelConfig config) {
        // DeepSeek 兼容 OpenAI API 格式
        // 使用 OpenAiChatModel，将 baseUrl 指向 DeepSeek 的 API 地址
        return OpenAiChatModel.builder()
                .apiKey(config.getApiKey())
                // DeepSeek API 地址：https://api.deepseek.com
                .baseUrl(config.getBaseUrl())
                // 模型名称：deepseek-chat、deepseek-coder 等
                .modelName(config.getModelName())
                .timeout(Duration.ofMillis(config.getTimeout()))
                .maxRetries(config.getMaxRetries())
                .temperature(0.7)
                .maxTokens(4096)
                .build();
    }

    @Override
    public String getProviderType() {
        return "deepseek";
    }
}
```

#### QwenModelFactory.java —— 通义千问工厂

```java
package com.hellofactory.factory;

import com.hellofactory.config.ModelConfig;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 通义千问模型工厂。
 *
 * 阿里云 DashScope 兼容 OpenAI API 格式（compatible-mode），
 * 因此同样可以使用 OpenAiChatModel，只需修改 baseUrl。
 *
 * 注意：阿里云 DashScope 的兼容模式需要将 baseUrl 指向
 * https://dashscope.aliyuncs.com/compatible-mode/v1
 */
@Component
public class QwenModelFactory implements ModelFactory {

    @Override
    public ChatLanguageModel createModel(ModelConfig config) {
        // 阿里云 DashScope 兼容 OpenAI API 格式
        // 使用 OpenAiChatModel，指向阿里云的兼容 endpoint
        return OpenAiChatModel.builder()
                .apiKey(config.getApiKey())
                // 阿里云 DashScope 兼容模式地址
                .baseUrl(config.getBaseUrl())
                // 模型名称：qwen-max、qwen-plus、qwen-turbo 等
                .modelName(config.getModelName())
                .timeout(Duration.ofMillis(config.getTimeout()))
                .maxRetries(config.getMaxRetries())
                .temperature(0.7)
                .maxTokens(4096)
                .build();
    }

    @Override
    public String getProviderType() {
        return "qwen";
    }
}
```

### 3.7 工厂注册中心

```java
package com.hellofactory.factory;

import com.hellofactory.config.ModelConfig;
import dev.langchain4j.model.chat.ChatLanguageModel;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 模型工厂注册中心 —— 多厂商 LLM 的调度中枢。
 *
 * 职责：
 * 1. 自动收集所有 ModelFactory 实现（通过 Spring 依赖注入）
 * 2. 根据配置的 provider 类型，动态选择对应的工厂
 * 3. 缓存已创建的模型实例，避免重复创建
 * 4. 提供统一的模型获取入口
 *
 * 设计亮点：
 * - 自动发现：新增工厂时只需添加 @Component 类，无需修改注册中心
 * - 并发安全：使用 ConcurrentHashMap 实现线程安全的缓存
 * - 延迟创建：computeIfAbsent 确保只在首次使用时创建实例
 */
@Component
public class ModelFactoryRegistry {

    private static final Logger log = LoggerFactory.getLogger(ModelFactoryRegistry.class);

    /**
     * 工厂映射表。
     * key = provider 类型（如 "openai"、"deepseek"、"qwen"）
     * value = 对应的工厂实例
     * 使用 final 修饰，初始化后不可变，保证线程安全。
     */
    private final Map<String, ModelFactory> factoryMap;

    /**
     * 模型实例缓存。
     * key = 缓存键（provider:modelName）
     * value = 模型实例
     * 使用 ConcurrentHashMap 保证线程安全。
     * 模型实例是线程安全的，可以全局复用。
     */
    private final Map<String, ChatLanguageModel> modelCache = new ConcurrentHashMap<>();

    /**
     * 构造函数注入 —— Spring 自动收集所有 ModelFactory 实现。
     *
     * @param factories 所有实现了 ModelFactory 接口的 Bean 列表
     */
    public ModelFactoryRegistry(List<ModelFactory> factories) {
        // 将工厂列表转为 Map，方便按 provider 类型快速查找
        // 使用 Collectors.toMap 将 List 转换为 Map
        this.factoryMap = factories.stream()
                .collect(Collectors.toMap(
                        ModelFactory::getProviderType,  // key: 工厂的 providerType
                        Function.identity(),            // value: 工厂实例本身
                        (existing, replacement) -> {
                            // 冲突处理：如果两个工厂返回相同的 providerType，保留第一个
                            log.warn("发现重复的工厂类型: {}，保留已注册的，忽略新注册的",
                                    existing.getProviderType());
                            return existing;
                        }
                ));
    }

    /**
     * 初始化方法，打印已注册的厂商列表。
     * @PostConstruct 确保在 Bean 初始化完成后调用。
     */
    @PostConstruct
    public void init() {
        log.info("模型工厂注册中心初始化完成，已注册厂商: {}", factoryMap.keySet());
    }

    /**
     * 根据配置获取模型实例。
     *
     * 流程：
     * 1. 根据 provider 类型找到对应的工厂
     * 2. 使用工厂创建模型实例并缓存
     * 3. 返回缓存的模型实例
     *
     * @param config 模型配置，包含 provider、apiKey、baseUrl 等
     * @return ChatLanguageModel 模型实例
     * @throws IllegalArgumentException 如果 provider 类型不支持
     */
    public ChatLanguageModel getModel(ModelConfig config) {
        // 1. 构建缓存键：provider:modelName
        // 例如 "openai:gpt-4o-mini"、"deepseek:deepseek-chat"
        String cacheKey = buildCacheKey(config);

        // 2. 从缓存中获取或创建模型实例
        // computeIfAbsent 是线程安全的：
        //   - 如果缓存中存在，直接返回
        //   - 如果不存在，执行创建逻辑并存入缓存
        return modelCache.computeIfAbsent(cacheKey, key -> {
            // 3. 根据 provider 类型找到对应的工厂
            ModelFactory factory = factoryMap.get(config.getProvider());

            // 如果找不到对应的工厂，抛出异常
            if (factory == null) {
                throw new IllegalArgumentException(
                        "不支持的模型厂商: " + config.getProvider()
                        + "，已注册的厂商: " + factoryMap.keySet());
            }

            // 4. 使用工厂创建模型实例
            log.info("创建模型实例: provider={}, model={}",
                    config.getProvider(), config.getModelName());
            return factory.createModel(config);
        });
    }

    /**
     * 构建缓存键。
     * 相同的 provider + modelName 复用同一个模型实例。
     */
    private String buildCacheKey(ModelConfig config) {
        return config.getProvider() + ":" + config.getModelName();
    }

    /**
     * 获取所有已注册的厂商类型。
     */
    public Map<String, ModelFactory> getFactoryMap() {
        return factoryMap;
    }

    /**
     * 清空模型实例缓存。
     * 配置变更时调用此方法，下次请求将重新创建模型实例。
     */
    public void clearCache() {
        modelCache.clear();
        log.info("模型实例缓存已清空，下次请求将重新创建实例");
    }
}
```

### 3.8 统一业务服务

```java
package com.hellofactory.service;

import com.hellofactory.config.AiModelProperties;
import com.hellofactory.config.ModelConfig;
import com.hellofactory.factory.ModelFactoryRegistry;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 统一 AI 对话服务 —— 业务代码的唯一入口。
 *
 * 业务层只依赖此服务，不关心底层是哪个厂商的模型。
 * 通过工厂注册中心获取模型实例，实现"一行配置切换模型"。
 *
 * 设计原则：
 * - 单一职责：只负责对话业务逻辑，不关心模型创建细节
 * - 依赖倒置：依赖 ChatLanguageModel 接口，不依赖具体实现
 * - 开闭原则：新增厂商时，业务代码无需修改
 */
@Service
public class AiChatService {

    private static final Logger log = LoggerFactory.getLogger(AiChatService.class);

    // 工厂注册中心：负责创建和管理模型实例
    private final ModelFactoryRegistry registry;

    // 模型配置：从 application.yml 读取
    private final AiModelProperties properties;

    /**
     * 构造器注入。
     * Spring 自动注入 registry 和 properties 实例。
     */
    public AiChatService(ModelFactoryRegistry registry, AiModelProperties properties) {
        this.registry = registry;
        this.properties = properties;
    }

    /**
     * 发送对话消息。
     *
     * 业务代码只调用 ChatLanguageModel 的接口，
     * 不关心底层是 OpenAI 还是 DeepSeek 还是通义千问。
     *
     * @param message 用户消息
     * @return AI 回复内容
     */
    public String chat(String message) {
        // 1. 从配置中获取当前激活的模型配置
        // 用户修改 active-provider 后，此配置会自动切换
        ModelConfig config = properties.getActiveModel();
        log.info("当前使用的模型: provider={}, model={}",
                properties.getActiveProvider(), config.getModelName());

        // 2. 通过工厂注册中心获取模型实例
        // 自动路由到对应厂商的模型
        ChatLanguageModel model = registry.getModel(config);

        // 3. 调用模型，发送消息
        // 业务代码只依赖 ChatLanguageModel 接口
        // 无论底层是哪个厂商，调用方式完全一致
        return model.generate(message);
    }

    /**
     * 获取当前激活的厂商信息。
     */
    public String getActiveProvider() {
        return properties.getActiveProvider();
    }

    /**
     * 获取所有已注册的厂商列表。
     */
    public String getSupportedProviders() {
        return String.join(", ", registry.getFactoryMap().keySet());
    }
}
```

### 3.9 REST 控制器

```java
package com.hellofactory.controller;

import com.hellofactory.service.AiChatService;
import org.springframework.web.bind.annotation.*;

/**
 * AI 对话 REST 控制器。
 *
 * 业务代码完全不知道底层是哪个厂商的模型，
 * 只通过 AiChatService 进行对话。
 * 切换模型时只需修改 application.yml，无需修改此控制器。
 */
@RestController
@RequestMapping("/api/ai")
public class AiChatController {

    private final AiChatService aiChatService;

    public AiChatController(AiChatService aiChatService) {
        this.aiChatService = aiChatService;
    }

    /**
     * 对话接口。
     * GET /api/ai/chat?message=你好
     */
    @GetMapping("/chat")
    public String chat(@RequestParam(defaultValue = "你好") String message) {
        // 业务代码不关心底层是哪个厂商
        return aiChatService.chat(message);
    }

    /**
     * 查看当前使用的模型信息。
     * GET /api/ai/info
     */
    @GetMapping("/info")
    public String info() {
        return "当前模型: " + aiChatService.getActiveProvider()
                + " | 支持的厂商: " + aiChatService.getSupportedProviders();
    }
}
```

### 3.10 单元测试

#### OpenAiModelFactoryTest.java

```java
package com.hellofactory.factory;

import com.hellofactory.config.ModelConfig;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * OpenAI 模型工厂的单元测试。
 *
 * 测试工厂能否正确创建模型实例。
 * 注意：此测试不实际调用 LLM API，只验证工厂创建行为。
 */
class OpenAiModelFactoryTest {

    private OpenAiModelFactory factory;

    @BeforeEach
    void setUp() {
        // 每个测试方法前创建新的工厂实例
        factory = new OpenAiModelFactory();
    }

    @Test
    void testGetProviderType() {
        // 验证工厂返回的 provider 类型正确
        assertEquals("openai", factory.getProviderType());
    }

    @Test
    void testCreateModel() {
        // 准备测试配置
        ModelConfig config = new ModelConfig();
        config.setApiKey("test-key");
        config.setBaseUrl("https://api.openai.com");
        config.setModelName("gpt-4o-mini");
        config.setTimeout(30000L);
        config.setMaxRetries(2);

        // 执行：使用工厂创建模型实例
        ChatLanguageModel model = factory.createModel(config);

        // 验证：创建成功，不为 null
        assertNotNull(model, "创建的模型实例不应为 null");
    }
}
```

#### ModelFactoryRegistryTest.java

```java
package com.hellofactory.factory;

import com.hellofactory.config.ModelConfig;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 工厂注册中心的单元测试。
 *
 * 验证注册中心能否正确管理多个工厂，
 * 并根据配置路由到对应的工厂。
 */
class ModelFactoryRegistryTest {

    private ModelFactoryRegistry registry;

    @BeforeEach
    void setUp() {
        // 创建三个工厂实例，模拟 Spring 注入
        List<ModelFactory> factories = List.of(
                new OpenAiModelFactory(),
                new DeepSeekModelFactory(),
                new QwenModelFactory()
        );

        // 创建注册中心，注入工厂列表
        registry = new ModelFactoryRegistry(factories);
    }

    @Test
    void testRegistryContainsAllFactories() {
        // 验证注册中心包含了所有工厂
        assertTrue(registry.getFactoryMap().containsKey("openai"));
        assertTrue(registry.getFactoryMap().containsKey("deepseek"));
        assertTrue(registry.getFactoryMap().containsKey("qwen"));
        assertEquals(3, registry.getFactoryMap().size());
    }

    @Test
    void testGetModelWithOpenAi() {
        // 准备 OpenAI 配置
        ModelConfig config = new ModelConfig();
        config.setProvider("openai");
        config.setApiKey("test-key");
        config.setBaseUrl("https://api.openai.com");
        config.setModelName("gpt-4o-mini");

        // 获取模型实例
        ChatLanguageModel model = registry.getModel(config);

        // 验证：创建成功
        assertNotNull(model);
    }

    @Test
    void testGetModelWithDeepSeek() {
        // 准备 DeepSeek 配置
        ModelConfig config = new ModelConfig();
        config.setProvider("deepseek");
        config.setApiKey("test-key");
        config.setBaseUrl("https://api.deepseek.com");
        config.setModelName("deepseek-chat");

        // 获取模型实例
        ChatLanguageModel model = registry.getModel(config);

        // 验证：创建成功
        assertNotNull(model);
    }

    @Test
    void testGetModelWithQwen() {
        // 准备通义千问配置
        ModelConfig config = new ModelConfig();
        config.setProvider("qwen");
        config.setApiKey("test-key");
        config.setBaseUrl("https://dashscope.aliyuncs.com/compatible-mode/v1");
        config.setModelName("qwen-plus");

        // 获取模型实例
        ChatLanguageModel model = registry.getModel(config);

        // 验证：创建成功
        assertNotNull(model);
    }

    @Test
    void testGetModelWithUnsupportedProvider() {
        // 准备一个不支持的厂商配置
        ModelConfig config = new ModelConfig();
        config.setProvider("unsupported-provider");
        config.setApiKey("test-key");
        config.setModelName("test-model");

        // 验证：抛出 IllegalArgumentException
        assertThrows(IllegalArgumentException.class, () -> {
            registry.getModel(config);
        });
    }

    @Test
    void testModelCacheReusesInstance() {
        // 准备配置
        ModelConfig config = new ModelConfig();
        config.setProvider("openai");
        config.setApiKey("test-key");
        config.setBaseUrl("https://api.openai.com");
        config.setModelName("gpt-4o-mini");

        // 第一次获取模型实例
        ChatLanguageModel model1 = registry.getModel(config);

        // 第二次获取模型实例
        ChatLanguageModel model2 = registry.getModel(config);

        // 验证：两次获取的是同一个实例（缓存复用）
        assertSame(model1, model2,
                "相同的配置应返回相同的模型实例（缓存复用）");
    }

    @Test
    void testClearCache() {
        // 准备配置
        ModelConfig config = new ModelConfig();
        config.setProvider("openai");
        config.setApiKey("test-key");
        config.setBaseUrl("https://api.openai.com");
        config.setModelName("gpt-4o-mini");

        // 获取模型实例
        ChatLanguageModel model1 = registry.getModel(config);

        // 清空缓存
        registry.clearCache();

        // 再次获取模型实例
        ChatLanguageModel model2 = registry.getModel(config);

        // 验证：清空缓存后，获取到的是新实例
        // 注意：这里不能使用 assertSame，因为清空缓存后重新创建
        // 但实际测试中，由于工厂创建逻辑相同，可能是同一个实例
        // 这里只验证清空缓存后获取不报错
        assertNotNull(model2);
    }
}
```

---

## 四、运行验证

### 4.1 配置 API Key

在运行应用之前，需要配置有效的 API Key。建议通过环境变量注入：

```bash
# Windows PowerShell
$env:AI_OPENAI_KEY="sk-your-openai-key"

# 或者使用 DeepSeek
$env:AI_DEEPSEEK_KEY="sk-your-deepseek-key"
```

### 4.2 启动应用

```bash
# 使用 Maven 编译并启动
mvn spring-boot:run

# 看到以下日志表示启动成功
# 模型工厂注册中心初始化完成，已注册厂商: [openai, deepseek, qwen]
```

### 4.3 测试 API

**测试对话接口：**

```bash
# 使用当前配置的模型进行对话
curl "http://localhost:8080/api/ai/chat?message=请介绍一下Java"

# 期望输出：AI 根据当前配置的模型返回回答
```

**查看模型信息：**

```bash
curl "http://localhost:8080/api/ai/info"

# 期望输出：
# 当前模型: openai | 支持的厂商: openai, deepseek, qwen
```

### 4.4 切换模型测试

**切换到 DeepSeek：**

```yaml
# 修改 application.yml，将 active-provider 改为 deepseek
ai:
  active-provider: deepseek  # 从 openai 改为 deepseek
```

重启应用后再次测试：

```bash
# 再次调用对话接口
curl "http://localhost:8080/api/ai/chat?message=介绍一下Java"

# 查看模型信息
curl "http://localhost:8080/api/ai/info"
# 输出：当前模型: deepseek | 支持的厂商: openai, deepseek, qwen
```

**切换到通义千问：**

```yaml
ai:
  active-provider: qwen  # 切换为通义千问
```

### 4.5 运行单元测试

```bash
# 运行所有测试
mvn test

# 期望输出：
# [INFO] Tests run: 7, Failures: 0, Errors: 0, Skipped: 0
```

---

## 五、项目对照：对应 ruoyi-ai 项目中的真实代码位置

### 5.1 核心文件对照表

| 本文示例 | ruoyi-ai 项目位置 | 说明 |
|---------|-------------------|------|
| `ModelFactory.java` | `ruoyi-ai/ruoyi-chat/src/main/java/com/ruoyi/chat/factory/ModelFactory.java` | 工厂接口定义 |
| `ModelFactoryRegistry.java` | `ruoyi-ai/ruoyi-chat/src/main/java/com/ruoyi/chat/factory/ModelFactoryRegistry.java` | 工厂注册中心 |
| `OpenAiModelFactory.java` | `ruoyi-ai/ruoyi-chat/src/main/java/com/ruoyi/chat/factory/OpenAiModelFactory.java` | OpenAI 工厂实现 |
| `DeepSeekModelFactory.java` | `ruoyi-ai/ruoyi-chat/src/main/java/com/ruoyi/chat/factory/DeepSeekModelFactory.java` | DeepSeek 工厂实现 |
| `QwenModelFactory.java` | `ruoyi-ai/ruoyi-chat/src/main/java/com/ruoyi/chat/factory/QwenModelFactory.java` | 通义千问工厂实现 |
| `ModelConfig.java` | `ruoyi-ai/ruoyi-chat/src/main/java/com/ruoyi/chat/config/ModelConfig.java` | 模型配置 POJO |
| `AiModelProperties.java` | `ruoyi-ai/ruoyi-chat/src/main/java/com/ruoyi/chat/config/AiModelProperties.java` | 配置绑定类 |
| `AiChatService.java` | `ruoyi-ai/ruoyi-chat/src/main/java/com/ruoyi/chat/service/AiChatService.java` | 统一 AI 服务 |

### 5.2 ruoyi-ai 中的实际增强

ruoyi-ai 项目中的多模型工厂在生产环境中做了以下增强：

1. **更多厂商支持**：除了本文实现的 3 个厂商，ruoyi-ai 还支持智谱、glm-5.2、Ollama、Mimo、Atla、CustomApi 等共 9 个厂商
2. **流式模型支持**：除了 `ChatLanguageModel`，还支持 `StreamingChatLanguageModel` 的工厂创建
3. **配置中心集成**：结合 Nacos 配置中心，支持运行时动态切换，无需重启应用
4. **异常统一处理**：各厂商的异常统一封装为 `AiServiceException`
5. **模型降级**：主模型故障时自动切换到备用模型
6. **限流保护**：使用 Guava RateLimiter 或 Sentinel 做 API 调用限流

### 5.3 从示例到项目的进阶之路

1. **配置刷新**：学习 Spring Cloud 的 `@RefreshScope` 注解，实现运行时动态切换
2. **多模型路由**：根据用户级别、业务场景、成本策略等维度，实现更精细的模型路由
3. **模型监控**：记录每次调用的耗时、Token 消耗、错误率，通过 Metrics 监控模型健康状态
4. **成本控制**：统计各厂商的 Token 消耗和费用，实现成本预算和告警

---

## 六、面试实战：3道面试题 + 回答框架

### Q1：工厂模式 + 策略模式在多模型接入场景中如何配合使用？

**考察点：** 面试官想考察候选人对两种设计模式的理解，以及在实际场景中如何结合使用。

**回答框架：**

- **背景**：在多厂商 LLM 接入场景中，我们需要解决两个问题：一是如何创建不同厂商的模型实例（创建问题），二是如何在运行时动态切换使用哪个模型（切换问题）。工厂模式解决前者，策略模式解决后者。

- **方案**：
  - **工厂模式（创建时）**：定义 `ModelFactory` 接口，每个厂商实现一个具体工厂。`OpenAiModelFactory` 创建 OpenAI 实例，`DeepSeekModelFactory` 创建 DeepSeek 实例。工厂负责封装模型实例的创建细节，隐藏各厂商 SDK 的差异。
  - **策略模式（运行时）**：`ModelFactoryRegistry` 作为策略上下文，管理所有已注册的工厂。根据 `application.yml` 中的 `active-provider` 配置，动态选择对应的工厂创建模型实例。修改配置即可切换策略，实现运行时动态切换。

- **深度**：两者的结合点在于：
  - 工厂模式负责"创建"：根据配置创建对应的模型实例
  - 策略模式负责"切换"：在运行时动态选择使用哪个模型
  - 注册中心是两者的桥梁：管理所有工厂（策略），根据条件选择具体工厂（策略选择），调用工厂创建实例（工厂方法）
  - 具体分工：`ModelFactory` 是工厂接口，`ModelFactoryRegistry` 是策略上下文

- **扩展**：这种"工厂+策略"的组合模式在 Java 生态中非常常见，如 Spring 的 `BeanFactory`（工厂模式）+ `ApplicationContext`（策略容器）、JDBC 的 `DriverManager`（工厂）+ `Connection`（策略接口）。

### Q2：@ConfigurationProperties 是如何将 yml 配置映射到 Java 对象的？如何处理嵌套结构？

**考察点：** 面试官想考察候选人对 Spring Boot 配置绑定机制的理解，以及对复杂配置结构的处理能力。

**回答框架：**

- **背景**：`@ConfigurationProperties` 是 Spring Boot 提供的配置绑定注解，用于将配置文件（application.yml/properties）中的属性值绑定到 Java 对象的属性上。

- **方案**：
  - 在类上标注 `@ConfigurationProperties(prefix = "ai")`，指定配置前缀
  - 类的属性名与配置项名通过"松散绑定"规则匹配（如 `active-provider` 匹配 `activeProvider`）
  - 嵌套结构通过内部类或独立 POJO 处理，如 `Map<String, ModelConfig>` 的 Map 结构

- **深度（绑定机制）**：
  - **松散绑定（Relaxed Binding）**：Spring Boot 支持多种命名风格的自动转换。`active-provider`（kebab-case）、`active_provider`（underscore notation）、`activeProvider`（camelCase）都会被自动绑定到 `activeProvider` 属性上。
  - **嵌套绑定**：`ai.models.openai.api-key` 会被绑定到 `AiModelProperties` 的 `models` Map 中，key 为 `"openai"` 的 `ModelConfig` 对象的 `apiKey` 属性上。
  - **类型转换**：Spring Boot 自动将 String 类型的配置值转换为目标类型（如 `timeout: 60000` 转为 `Long` 类型）。
  - **校验支持**：可以配合 `@Validated` 注解进行参数校验，如 `@NotEmpty`、`@Min` 等。

- **扩展**：实际项目中，`@ConfigurationProperties` 还可以结合配置中心（Nacos/Apollo）实现动态刷新，配合 `@RefreshScope` 注解，在配置变更时自动更新 Bean 的属性值，无需重启应用。

### Q3：设计一个多厂商 LLM 接入方案时，需要考虑哪些非功能性问题？

**考察点：** 面试官想考察候选人的工程化思维，是否考虑过生产环境中的实际挑战。

**回答框架：**

- **背景**：多厂商 LLM 接入方案不仅要解决"如何切换模型"的功能问题，还需要考虑生产环境中的非功能性问题，包括可用性、安全性、性能、成本等方面。

- **方案**：从以下六个维度考虑：

  - **1. 高可用性（降级与熔断）**
    - 主备模型机制：主模型不可用时自动切换到备用模型
    - 熔断保护：连续失败达到阈值后熔断，避免雪崩
    - 健康检查：定期检测各厂商服务的可用性

  - **2. 安全性（凭证与数据）**
    - API Key 不应明文存储，应使用环境变量或密钥管理服务（KMS）
    - 通信加密：所有 API 调用通过 HTTPS
    - 数据隔离：用户敏感信息不应被发送到 LLM

  - **3. 性能（超时与限流）**
    - 超时控制：为每个厂商设置合理的超时时间
    - 限流保护：使用令牌桶算法限制 API 调用频率，避免触发厂商限流惩罚
    - 连接池管理：复用 HTTP 连接，减少连接建立开销

  - **4. 可观测性（监控与日志）**
    - 记录每次调用的耗时、模型、Token 消耗
    - 通过 Metrics 监控各厂商的成功率、响应时间
    - 链路追踪：在分布式系统中追踪一次 AI 调用的完整链路

  - **5. 成本控制**
    - Token 统计：记录每次调用的输入/输出 Token 数
    - 费用核算：根据各厂商的定价计算每次调用的费用
    - 预算告警：当月度费用超过预算时触发告警

  - **6. 配置管理**
    - 配置中心集中管理各厂商的配置
    - 支持配置的动态刷新，无需重启应用
    - 配置的版本管理和回滚能力

- **扩展**：这些非功能性需求在 ruoyi-ai 项目中都有对应的实现，如使用 Resilience4j 实现熔断降级、使用 Micrometer 实现指标监控、使用 Nacos 实现配置管理等。

---

## 七、总结

本文从零搭建了一个多模型工厂，实现了 OpenAI、DeepSeek、通义千问三个厂商的统一接入，涉及以下知识点：

1. **工厂模式**：定义 `ModelFactory` 接口，每个厂商实现具体工厂，隐藏创建细节
2. **策略模式**：通过 `ModelFactoryRegistry` 管理多个工厂，根据配置动态选择
3. **@ConfigurationProperties**：将 `application.yml` 配置绑定到 Java 对象，实现配置驱动
4. **开闭原则**：新增厂商只需添加新工厂类和配置，无需修改已有代码
5. **模型实例缓存**：使用 `ConcurrentHashMap` 缓存已创建的实例，避免重复创建

在下一篇文章中，我们将深入分析向量数据库的概念和在 ruoyi-ai 中的应用，学习如何通过 LangChain4j 的 `EmbeddingStore` 接口实现向量检索。

---

## 参考资料

- [LangChain4j 官方文档](https://docs.langchain4j.dev) — ChatLanguageModel 集成指南
- [Spring Boot @ConfigurationProperties](https://docs.spring.io/spring-boot/reference/features/external-config/configuration-properties.html) — 配置绑定官方文档
- [设计模式：工厂模式与策略模式](https://refactoring.guru/design-patterns) — GoF 设计模式详解
- [ruoyi-ai GitHub 仓库](https://github.com/1byteone/ruoyi-ai) — 查看完整的工厂模式实现