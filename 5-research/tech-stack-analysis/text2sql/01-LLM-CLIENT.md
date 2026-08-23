# 01 · Spring AI + DeepSeek LLM 集成：客户端抽象、多供应商切换

> Java 生态中如何集成大语言模型？看 Spring AI 如何通过 `ChatClient` 统一接入 DeepSeek，以及多供应商抽象设计。
>
> **对应项目：** `text2sql/text2sql-ai`

---

## 一、基础概念

### 1.1 Spring AI 是什么

Spring AI 是 Spring 官方推出的 AI 集成框架，对标 Python 的 LangChain：

| 能力 | Spring AI | LangChain (Python) |
|------|-----------|-------------------|
| LLM 调用 | `ChatClient` | `ChatOpenAI` |
| Embedding | `EmbeddingModel` | `OpenAIEmbeddings` |
| 向量存储 | `VectorStore` | `VectorStore` |
| 结构化输出 | `.entity(Class)` | `with_structured_output()` |
| 工具调用 | `ToolCallback` | `@tool` |

### 1.2 项目中的 LLM 客户端层次

```
LLMClient (接口)
    ↑
DeepSeekLLMClient (实现)
    ↑
ChatClient (Spring AI 底层)
    ↑
DeepSeek API (HTTP /v1/chat/completions)
```

---

## 二、进阶机制

### 2.1 LLMClient 接口抽象

```java
public interface LLMClient {
    /**
     * 生成文本
     * @param systemPrompt 系统提示词
     * @param userPrompt 用户提示词
     * @return 生成的文本
     */
    String generate(String systemPrompt, String userPrompt);
}
```

**为什么需要接口抽象？** 与 mall-ai-search 的多供应商设计同理——将来可以无缝切换 DeepSeek → 通义千问 → GPT，只需新增实现类。

### 2.2 DeepSeekLLMClient 实现

```java
@Service
@RequiredArgsConstructor
public class DeepSeekLLMClient implements LLMClient {

    private final ChatClient chatClient;  // Spring AI 注入

    @Override
    public String generate(String systemPrompt, String userPrompt) {
        return chatClient.prompt()
            .system(systemPrompt)
            .user(userPrompt)
            .call()
            .content();  // 返回文本
    }
}
```

### 2.3 DeepSeekProperties 配置

```java
@Configuration
@ConfigurationProperties(prefix = "deepseek")
public class DeepSeekProperties {
    private String apiKey;
    private String baseUrl = "https://api.deepseek.com";
    private String model = "deepseek-chat";
    private double temperature = 0.1;
    private int maxTokens = 4096;
}
```

**application.yml 配置：**

```yaml
deepseek:
  api-key: ${DEEPSEEK_API_KEY}
  base-url: https://api.deepseek.com
  model: deepseek-chat
  temperature: 0.1
  max-tokens: 4096

spring:
  ai:
    openai:
      api-key: ${DEEPSEEK_API_KEY}
      base-url: https://api.deepseek.com
      chat:
        options:
          model: deepseek-chat
```

---

## 三、面试要点

### Q1: Spring AI 的 ChatClient 和 LangChain 的 ChatOpenAI 在概念上有什么对应关系？

**回答思路：** 两者都是 LLM 调用抽象层。ChatClient 的 `.prompt().system().user().call().content()` 对应 LangChain 的 `ChatOpenAI.invoke()`。Spring AI 还支持 `.entity(Class)` 结构化输出，类似 LangChain 的 `with_structured_output()`。

### Q2: 项目中为什么需要 LLMClient 接口抽象？

**回答思路：** 解耦。将来切换模型供应商（DeepSeek → 通义千问 → GPT）只需新增实现类，业务代码（SQLGeneratorService）只依赖接口，不改动。这是依赖倒置原则的实践。

---

> **下一篇：** [02-EMBEDDING-VECTOR.md —— Spring AI Embedding + 向量存储：Schema 向量化与语义检索](./02-EMBEDDING-VECTOR.md)
>
> 看 Spring AI 的 EmbeddingModel 和 VectorStore 如何实现 Schema 文本的向量化存储与语义检索。