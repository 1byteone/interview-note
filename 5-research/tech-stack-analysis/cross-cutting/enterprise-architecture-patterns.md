# 企业级架构模式提炼：四项目通用设计模式实战

> 从 4 个真实项目中提取 5 种高频企业级设计模式，深入代码实现，直击面试追问。
>
> **适用读者：** 准备高级 Java 后端/AI 架构师岗位面试的工程师
> **覆盖模式：** 工厂模式 / 策略模式 / Agent 模式 / 适配器模式 / 责任链模式

---

## 一、工厂模式（Factory Pattern）

### 1.1 模式说明

**是什么：** 定义一个创建对象的接口，让子类决定实例化哪个类。将对象的创建和使用分离，符合开闭原则。

**为什么在 AI 项目中高频出现：** AI 项目天然需要对接多个厂商（OpenAI、通义千问、DeepSeek、智谱等），每个厂商的模型初始化参数、API 地址、认证方式都不同。工厂模式让"增加一个厂商"变成"加一个实现类"，不修改现有代码。

### 1.2 项目应用

| 项目 | 应用位置 | 工厂角色 | 产品角色 |
|------|---------|---------|---------|
| ruoyi-ai | `ModelFactoryRegistry` | `ModelFactory` 接口 + Spring DI 自动收集 | 9 种 LLM 实现（openai/deepseek/qwen/zhipu/ollama 等） |
| ruoyi-ai | `DocumentParserFactory` | 解析器工厂 | PDF/Word/Markdown/Excel 4 种文档解析器 |
| ruoyi-ai | `VectorStoreFactory` | 向量库工厂 | Milvus/Weaviate/Qdrant 3 种向量库 |
| ruoyi-ai | `EmbeddingModelFactory` | 嵌入模型工厂 | 4 家 Embedding 厂商 |

### 1.3 源码级分析：ModelFactoryRegistry

```java
// ModelFactory.java —— 工厂接口
public interface ModelFactory {
    ChatLanguageModel createModel(ProviderConfig config);
    boolean supports(String providerType);
}

// ModelFactoryRegistry.java —— 注册中心，Spring DI 自动收集所有工厂
@Component
public class ModelFactoryRegistry {
    // Spring 自动注入所有 ModelFactory 实现
    private final List<ModelFactory> factories;

    public ModelFactoryRegistry(List<ModelFactory> factories) {
        this.factories = factories;
    }

    public ChatLanguageModel getModel(String providerType, ProviderConfig config) {
        return factories.stream()
                .filter(f -> f.supports(providerType))
                .findFirst()
                .orElseThrow(() -> new UnsupportedProviderException(providerType))
                .createModel(config);
    }
}

// 具体工厂示例 —— DeepSeekModelFactory
@Component
public class DeepSeekModelFactory implements ModelFactory {
    @Override
    public boolean supports(String providerType) {
        return "deepseek".equalsIgnoreCase(providerType);
    }

    @Override
    public ChatLanguageModel createModel(ProviderConfig config) {
        return OpenAiChatModel.builder()
                .apiKey(config.getApiKey())
                .baseUrl("https://api.deepseek.com")
                .modelName("deepseek-chat")
                .logRequests(true)
                .build();
    }
}
```

**关键设计点：**
- `List<ModelFactory>` 注入：Spring 在启动时扫描所有 `@Component` 实现，自动注入到 `ModelFactoryRegistry`
- 支持热切换：结合 `@RefreshScope` + 配置中心，运行时切换模型无需重启
- 限流保护：`RateLimitedChatModel` 用 Guava RateLimiter 包装，每个厂商独立限流

### 1.4 面试问答

**Q：工厂模式 + 注册中心这种方式和 Map<providerType, Factory> 手动注册有什么区别？**

A：本质上是一样的，但 Spring DI 的 `List` 注入省去了手动注册的样板代码，新增厂商只需加一个 `@Component` 类。缺点是需要 Spring 扫描，不能做编译期检查。如果项目不用 Spring，可以用 `Map<String, Factory>` + 枚举手动注册，可读性反而更好。

**Q：多厂商接入时，如何保证不同厂商的 API 差异不影响业务层？**

A：关键在 `ModelFactory` 接口的抽象粒度——它返回的是 `ChatLanguageModel`，这是 LangChain4j 的统一抽象。不管底层是 OpenAI 格式、通义 DashScope 还是本地 Ollama，业务层都只面向 `ChatLanguageModel` 编程。这就是"依赖倒置"——抽象不依赖细节，细节依赖抽象。

---

## 二、策略模式（Strategy Pattern）

### 2.1 模式说明

**是什么：** 定义一系列算法，把它们一个个封装起来，并且使它们可以互相替换。策略模式让算法独立于使用它的客户端而变化。

**为什么在 AI 项目中高频出现：** AI 项目的"策略"选择无处不在——配图用什么方式、切分文档用什么策略、Rerank 用什么模型、Agent 调度走什么路径。策略模式把"选择"交给运行时，而不是写死在 if-else 里。

### 2.2 项目应用

| 项目 | 应用位置 | 上下文 | 策略接口 | 策略实现数 |
|------|---------|--------|---------|-----------|
| ai-passage-creator | `ImageSearchService` | 文章配图 | `ImageSearchService.search()` | 6 种（Pexels/Mermaid/Iconify/表情包/Nano Banana/SVG） |
| ruoyi-ai | `RerankStrategy` | 结果精排 | `Reranker` 接口 | 3 家（百炼/SiliconFlow/智谱） |
| ruoyi-ai | `ChunkingStrategy` | 文档切分 | `DocumentSplitter` | 3 种（Token/Character/Markdown） |
| ruoyi-ai | `SchedulingStrategy` | Agent 调度 | 调度策略接口 | 4 种（单路/多路/链式/反馈） |

### 2.3 源码级分析：ImageSearchStrategy

```java
// ImageSearchService.java —— 策略接口
public interface ImageSearchService {
    ImageResult search(ImageRequirement requirement);
    boolean supports(ImageMethodEnum method);
}

// ImageRequirement.java —— 策略参数
@Data
public class ImageRequirement {
    private int paragraphIndex;       // 段落索引
    private ImageMethodEnum method;  // 配图方式
    private String keyword;          // 搜索关键词
    private String styleHint;        // 风格提示
    private int priority;            // 优先级（降级时使用）
}

// ImageSearchStrategyRegistry.java —— 策略注册中心
@Component
public class ImageSearchStrategyRegistry {
    private final List<ImageSearchService> strategies;
    private static final List<ImageMethodEnum> FALLBACK_CHAIN = List.of(
        ImageMethodEnum.PEXELS,       // 首选：Pexels 图片搜索
        ImageMethodEnum.MERMAID,      // 次选：Mermaid 图表
        ImageMethodEnum.ICONIFY,      // 第三：Iconify 图标
        ImageMethodEnum.EMOJI,        // 第四：表情包
        ImageMethodEnum.NANO_BANANA,  // 第五：Nano Banana AI 生图
        ImageMethodEnum.SVG_DIAGRAM,  // 第六：SVG 示意图
        ImageMethodEnum.PICSUM,       // 兜底：Picsum 随机图
        ImageMethodEnum.SKIP          // 最终兜底：跳过配图
    );

    public ImageSearchStrategyRegistry(List<ImageSearchService> strategies) {
        this.strategies = strategies;
    }

    // 带降级的策略执行
    public ImageResult searchWithFallback(ImageRequirement requirement) {
        // 从指定策略开始，逐级降级
        int startIndex = FALLBACK_CHAIN.indexOf(requirement.getMethod());
        if (startIndex < 0) startIndex = 0;

        for (int i = startIndex; i < FALLBACK_CHAIN.size(); i++) {
            ImageMethodEnum method = FALLBACK_CHAIN.get(i);
            requirement.setMethod(method);

            for (ImageSearchService strategy : strategies) {
                if (strategy.supports(method)) {
                    try {
                        ImageResult result = strategy.search(requirement);
                        if (result.isSuccess()) {
                            return result;
                        }
                    } catch (Exception e) {
                        log.warn("配图策略 {} 失败，降级至 {}", method, e.getMessage());
                        continue; // 降级到下一个策略
                    }
                }
            }
        }
        return ImageResult.empty(); // 全部失败，返回空
    }
}
```

**关键设计点：**
- 降级链（Degradation Chain）：不是简单的"选一个策略执行"，而是"首选失败 → 自动降级到下一个"的容错机制
- 6 种策略 + 2 种兜底，覆盖"有图就配图，无图不报错"的健壮性要求
- 和工厂模式的区别：工厂模式是"创建"，策略模式是"行为"——这里既包含对象的创建（配图），也包含行为的选择（降级链）

### 2.4 面试问答

**Q：策略模式和工厂模式有什么区别？什么时候用策略？什么时候用工厂？**

A：核心区别在意图——**工厂模式关注"创建谁"**，**策略模式关注"怎么做"**。工厂模式返回一个对象（如 `ChatLanguageModel`），调用方用这个对象去做事；策略模式封装一个算法（如 `ImageSearchService.search()`），调用方直接调用策略方法。实践中两者经常配合：工厂创建策略对象，策略决定执行行为。

**Q：降级链的设计有什么注意事项？**

A：三个要点：1）**降级顺序很重要**，按质量从高到低排列，保证最好效果；2）**兜底策略必须存在**，不能降级到空；3）**降级日志要完整**，方便排查为什么首选策略失败了（是 API 限流、网络超时还是参数错误）。

---

## 三、Agent 模式（Agent Pattern）

### 3.1 模式说明

**是什么：** Agent 模式并不是 GoF 23 设计模式之一，而是在 AI 应用中新兴的架构模式。核心思想是：将 LLM 视为一个"决策者"，让它自主判断当前任务、选择工具、执行操作、观察结果，并在循环中持续优化。

**在项目中的三种形态：**
1. **单 Agent 循环**：一个 LLM 自主决策（mewpaw-code 的 ReAct AgentLoop）
2. **Supervisor + 子 Agent**：一个 LLM 做调度，路由到多个专业子 Agent（ruoyi-ai）
3. **Agent 流水线**：多个 Agent 按 DAG 编排，协作完成复杂任务（ai-passage-creator）

### 3.2 项目应用

| 项目 | Agent 形态 | 核心实现 | 关键特征 |
|------|-----------|---------|---------|
| mewpaw-code | 单 Agent 循环 | `AgentLoop`（自定义 ReAct） | Thought→Action→Observation, 50 次迭代上限, 3 次错误容忍 |
| ruoyi-ai | Supervisor + 子 Agent | `Supervisor` + 4 子 Agent | 两层 tool calling, 4 种调度策略 |
| ai-passage-creator | Agent 流水线 | 5 Agent DAG（StateGraph） | 固定 DAG 编排, 人机协作, 并行节点 |

### 3.3 源码级分析：三种形态

**形态一：单 Agent 循环（mewpaw-code）**

```java
// AgentLoop 核心循环（简化）
public class AgentLoop {
    private static final int MAX_ITERATIONS = 50;
    private static final int MAX_CONSECUTIVE_ERRORS = 3;

    public String execute(String systemPrompt, String userMessage) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new SystemMessage(systemPrompt));
        messages.add(new UserMessage(userMessage));

        int iteration = 0;
        int consecutiveErrors = 0;

        while (iteration < MAX_ITERATIONS) {
            iteration++;
            AiMessage aiResponse = llmProvider.chat(messages);

            // LLM 不需要调用工具 → 直接返回
            if (!aiResponse.hasToolExecutionRequests()) {
                return aiResponse.text();
            }

            // 有工具调用 → 逐个执行
            for (ToolExecutionRequest request : aiResponse.toolExecutionRequests()) {
                try {
                    // 安全链检查
                    SecurityResult result = securityChain.check(request);
                    if (!result.allowed()) {
                        messages.add(ToolExecutionResultMessage.from(request, result.reason()));
                        consecutiveErrors++;
                        continue;
                    }
                    // 执行工具
                    String output = toolRegistry.execute(request);
                    messages.add(ToolExecutionResultMessage.from(request, output));
                    consecutiveErrors = 0;
                } catch (Exception e) {
                    messages.add(ToolExecutionResultMessage.from(request, e.getMessage()));
                    consecutiveErrors++;
                }
            }

            if (consecutiveErrors >= MAX_CONSECUTIVE_ERRORS) {
                return "Agent terminated: consecutive errors exceeded";
            }
        }
        return "Agent terminated: max iterations reached";
    }
}
```

**形态二：Supervisor + 子 Agent（ruoyi-ai）**

```java
// Supervisor 决策逻辑
// 1. LLM 分析用户意图 → 结构化输出（意图分类 + 参数提取）
// 2. 根据意图选择调度策略：
//    - 单路策略：路由到一个子 Agent（如 "查 Java 语法" → SkillsAgent）
//    - 多路策略：并行分发给多个子 Agent（如"帮我查资料并画图" → WebSearch + Chart）
//    - 链式策略：Agent 输出作为下一个 Agent 输入（如"搜索 → 生成报表"）
//    - 反馈策略：子 Agent 结果回传给 Supervisor 做最终判断
public class SupervisorAgent {
    public AgentResult execute(String userMessage) {
        Intent intent = llm.analyzeIntent(userMessage);
        SchedulingStrategy strategy = selectStrategy(intent);

        // 多路并行
        if (intent.isMultiRoute()) {
            List<AgentResult> results = strategy.execute(intent.getSubTasks());
            return mergeResults(results);
        }

        // 单路路由
        AgentResult result = strategy.execute(intent);
        return result;
    }
}
```

**形态三：Agent 流水线（ai-passage-creator）**

```java
// StateGraph 定义 5 Agent 流水线
// 1 TitleGenerator → 2 OutlineGenerator → 3 ContentGenerator
//    → 4 ImageAnalyzer → 5 ParallelImageGenerator → 6 ContentMerger
StateGraph<PassageState> graph = new StateGraph<>(PassageState::new);

graph.addNode("titleGenerator", titleGenerator);
graph.addNode("outlineGenerator", outlineGenerator);
graph.addNode("contentGenerator", contentGenerator);
graph.addNode("imageAnalyzer", imageAnalyzer);
graph.addNode("parallelImageGenerator", parallelImageGenerator);  // 异步并行
graph.addNode("contentMerger", contentMerger);

graph.addEdge("titleGenerator", "outlineGenerator");
graph.addEdge("outlineGenerator", "contentGenerator");
// ContentGenerator 与 ImageAnalyzer 并行执行
graph.addEdge("contentGenerator", "imageAnalyzer");
graph.addConditionalEdge("imageAnalyzer", context -> {
    return "parallelImageGenerator"; // 根据分析结果决定是否配图
});
graph.addEdge("parallelImageGenerator", "contentMerger");
```

### 3.4 面试问答

**Q：三种 Agent 形态各自适用于什么场景？**

A：单 Agent 循环适合"探索性任务"——你不知道最终结果长什么样，需要 LLM 边做边看（如调试代码）。Supervisor + 子 Agent 适合"多领域交叉任务"——需要判断该用哪个专业能力（如查资料 + 画图 + 写 SQL）。Agent 流水线适合"确定性生产流程"——流程固定，但每个环节需要 LLM 的专业能力（如写文章 → 配图 → 合并）。

**Q：Supervisor 模式如何避免"调度开销超过任务本身价值"？**

A：关键问题是"调度 LLM 的调用成本"——Supervisor 本身也是一次 LLM 调用。如果任务很简单（如"查天气"），用 Supervisor 就过度设计了。解决方案：加一个"快速预分类器"——用规则/关键词匹配先筛选，简单任务直接路由，复杂任务才走 LLM 分析。

---

## 四、适配器模式（Adapter Pattern）

### 4.1 模式说明

**是什么：** 将一个类的接口转换成客户端期望的另一个接口。适配器让原本不兼容的类可以协同工作。

**为什么在 AI 项目中高频出现：** AI 项目需要对接大量外部系统和 SDK——不同厂商的 API 格式不同、不同文档格式的解析库不同、不同向量库的客户端不同。适配器模式让这些差异被封装在统一接口后面。

### 4.2 项目应用

| 项目 | 适配器 | 适配内容 | 统一接口 |
|------|--------|---------|---------|
| ruoyi-ai | `DocumentParserFactory` | PDFBox / POI / CommonMark 等 | `DocumentParser.parse()` |
| ruoyi-ai | `EmbeddingModelFactory` | 4 家 Embedding 厂商 API | `EmbeddingModel.embed()` |
| zznursing | `QianfanAiClient` | 百度千帆 REST API → 内部 DTO | `chat()` / `chatStream()` |
| ai-passage-creator | `ImageSearchService` | 6 种配图来源 API | `ImageSearchService.search()` |

### 4.3 源码级分析：千帆 API 适配器

```java
// 千帆 REST API → 业务层统一接口
// 适配的内容：OAuth 认证、SSE 解析、错误码映射、请求/响应格式转换

@Component
public class QianfanAiClient {
    private final WebClient webClient;
    private final AtomicReference<String> accessToken = new AtomicReference<>(null);
    private volatile long tokenExpireTime = 0;

    // 适配点 1：OAuth 2.0 认证 → 业务层无需关心 Token
    public String getAccessToken() {
        if (System.currentTimeMillis() < tokenExpireTime) {
            return accessToken.get();
        }
        synchronized (this) {
            if (System.currentTimeMillis() < tokenExpireTime) {
                return accessToken.get();
            }
            // 调用千帆 OAuth 接口获取 Token，缓存 30 天（提前 1 天过期）
            String token = fetchTokenFromQianfan();
            this.tokenExpireTime = System.currentTimeMillis() + (2592000 - 86400) * 1000L;
            this.accessToken.set(token);
            return token;
        }
    }

    // 适配点 2：SSE 流式响应 → Flux<String> 统一流
    public Flux<String> chatStream(QianfanChatRequest request) {
        return webClient.post()
                .uri(baseUrl + chatUrl + "?access_token=" + getAccessToken())
                .bodyValue(buildRequestBody(request))
                .retrieve()
                .bodyToFlux(String.class)
                .filter(data -> data.startsWith("data: "))     // 过滤 SSE 元数据
                .map(data -> data.substring(6))                // 去除 "data: " 前缀
                .map(this::extractStreamContent);              // 提取 result 字段
    }

    // 适配点 3：千帆 JSON 响应 → 内部 DTO
    public QianfanChatResponse chat(QianfanChatRequest request) {
        // 调用千帆 API
        String response = webClient.post()
                .uri(baseUrl + chatUrl + "?access_token=" + getAccessToken())
                .bodyValue(buildRequestBody(request))
                .retrieve()
                .bodyToMono(String.class)
                .block(Duration.ofSeconds(30));

        // 解析千帆 JSON → 内部 DTO
        JsonNode root = objectMapper.readTree(response);
        QianfanChatResponse result = new QianfanChatResponse();
        result.setContent(root.path("result").asText());
        result.setPromptTokens(root.path("usage").path("prompt_tokens").asInt());
        result.setCompletionTokens(root.path("usage").path("completion_tokens").asInt());
        return result;
    }
}
```

### 4.4 面试问答

**Q：适配器模式在 AI 项目中的"标配"适配点是哪些？**

A：四个必配：1）**认证适配**——不同厂商的认证方式不同（OAuth / API Key / Bearer Token），适配器统一管理；2）**协议适配**——SSE / WebSocket / 普通 HTTP，对业务层透明；3）**数据格式适配**——JSON / XML / Protobuf，统一为内部 DTO；4）**错误码适配**——各厂商的错误码不同，适配器统一映射为业务异常。

**Q：千帆的 Access Token 缓存为什么用 AtomicReference + 双重检查锁？不用分布式锁？**

A：因为 Access Token 的缓存是进程内缓存，不需要跨进程同步。`AtomicReference` 提供线程安全的引用更新，`synchronized` + 双重检查保证只调用一次 Token 刷新接口。如果项目是分布式部署，多个实例会各自缓存 Token——千帆 API 对同一 Token 的并发请求是允许的，所以不会出问题。但如果 Token 刷新接口有调用频率限制，就需要用分布式锁或定时任务统一刷新。

---

## 五、责任链模式（Chain of Responsibility）

### 5.1 模式说明

**是什么：** 使多个对象都有机会处理请求，从而避免请求的发送者和接收者之间的耦合关系。将这些对象连成一条链，并沿着这条链传递请求，直到有一个对象处理它为止。

**为什么在 AI 项目中高频出现：** AI Agent 的工具调用需要多层安全检查、多级降级策略——每一层都有自己的处理逻辑，层级之间有严格的顺序。责任链模式让"新增一个过滤条件"变成"加一个 Filter"，不修改已有链。

### 5.2 项目应用

| 项目 | 责任链 | 链路长度 | 处理内容 |
|------|--------|---------|---------|
| mewpaw-code | `SecurityFilterChain` | 5 层 | ToolFilter → PathGuardFilter → CommandScannerFilter → UserConfirmFilter → AuditLogFilter |
| ai-passage-creator | 配图降级链 | 8 级 | Pexels → Mermaid → Iconify → 表情包 → AI 生图 → SVG → Picsum → 跳过 |

### 5.3 源码级分析：SecurityFilterChain

```java
// SecurityFilterChain.java —— 安全过滤器链
// 5 层过滤器，每一层检查工具调用请求，任一 deny 则阻断

public class SecurityFilterChain {
    private final List<SecurityFilter> filters;

    public SecurityFilterChain(List<SecurityFilter> filters) {
        // filters 按 @Order 注解排序注入
        this.filters = filters;
    }

    public SecurityResult check(ToolExecutionRequest request) {
        for (SecurityFilter filter : filters) {
            SecurityResult result = filter.check(request);
            if (!result.allowed()) {
                log.warn("安全链拒绝: {} - {}", filter.name(), result.reason());
                return result; // 任一拒绝，终止链
            }
        }
        return SecurityResult.allowed(); // 全部通过
    }
}

// SecurityFilter.java —— 过滤器接口
public interface SecurityFilter {
    String name();
    SecurityResult check(ToolExecutionRequest request);
}

// 第 1 层：ToolFilter —— 检查工具是否注册
@Order(1)
@Component
public class ToolFilter implements SecurityFilter {
    @Override
    public String name() { return "ToolFilter"; }

    @Override
    public SecurityResult check(ToolExecutionRequest request) {
        if (!toolRegistry.contains(request.name())) {
            return SecurityResult.deny("工具未注册: " + request.name());
        }
        return SecurityResult.allowed();
    }
}

// 第 2 层：PathGuardFilter —— 路径规范化，防遍历攻击
@Order(2)
@Component
public class PathGuardFilter implements SecurityFilter {
    @Override
    public String name() { return "PathGuardFilter"; }

    @Override
    public SecurityResult check(ToolExecutionRequest request) {
        String path = extractPath(request.arguments());
        if (path == null) return SecurityResult.allowed();

        // 路径规范化
        Path normalizedPath = Paths.get(path).normalize();
        // 检查是否在允许的根目录内
        if (!normalizedPath.startsWith(ALLOWED_ROOT)) {
            return SecurityResult.deny("路径越权: " + path);
        }
        return SecurityResult.allowed();
    }
}

// 第 3 层：CommandScannerFilter —— 危险命令检测
@Order(3)
@Component
public class CommandScannerFilter implements SecurityFilter {
    private static final List<Pattern> DANGEROUS_PATTERNS = List.of(
        Pattern.compile("rm\\s+-rf\\s+/"),
        Pattern.compile(":(){ :\\|:& };:"),  // fork 炸弹
        Pattern.compile("mkfs\\."),
        Pattern.compile("dd\\s+if=")
    );

    @Override
    public SecurityResult check(ToolExecutionRequest request) {
        if (!"bash".equals(request.name())) {
            return SecurityResult.allowed(); // 非 bash 工具跳过
        }
        String command = request.arguments();
        for (Pattern pattern : DANGEROUS_PATTERNS) {
            if (pattern.matcher(command).find()) {
                return SecurityResult.deny("危险命令拦截: " + command);
            }
        }
        return SecurityResult.allowed();
    }
}

// 第 4 层：UserConfirmFilter —— 高危操作需要用户确认
@Order(4)
@Component
public class UserConfirmFilter implements SecurityFilter {
    @Override
    public SecurityResult check(ToolExecutionRequest request) {
        if (isHighRisk(request)) {
            boolean confirmed = userConfirm(request);
            if (!confirmed) {
                return SecurityResult.deny("用户拒绝确认");
            }
        }
        return SecurityResult.allowed();
    }
}

// 第 5 层：AuditLogFilter —— 全量审计日志
@Order(5)
@Component
public class AuditLogFilter implements SecurityFilter {
    @Override
    public SecurityResult check(ToolExecutionRequest request) {
        auditLogger.log(request);
        return SecurityResult.allowed(); // 审计日志不阻断
    }
}
```

**关键设计点：**
- 5 层过滤器各司其职：工具注册 → 文件路径 → 危险命令 → 用户确认 → 审计日志
- 前 4 层任一拒绝即终止链（短路模式），第 5 层只记录不阻断
- `@Order` 注解控制顺序，新增过滤器只需加一个 `@Component` 类
- 安全链在 AgentLoop 循环内执行，每次工具调用都经过安全检查

### 5.4 面试问答

**Q：责任链模式在 AI Agent 安全中扮演什么角色？和 Spring Security 的 FilterChain 有什么关系？**

A：核心思想相同——将安全检查拆解为多个独立的过滤器，每个过滤器只关注一个检查维度，通过链式组合形成完整的安全策略。和 Spring Security 的 FilterChain 区别在于：Spring Security 处理 HTTP 请求过滤，这里的 SecurityFilterChain 处理 Agent 工具调用过滤。Spring Security 的 `OncePerRequestFilter` 是针对 Servlet 容器的，这里针对的是 LLM 生成的工具调用。

**Q：5 层安全链的顺序为什么这么排？调换顺序会有什么问题？**

A：顺序原则是"成本从低到高，范围从窄到宽"——先做低成本、高覆盖的检查，再做高成本、低覆盖的检查。ToolFilter（检查工具是否存在）是 O(1) 操作，放在最前面可以快速拦截大量无效请求。UserConfirmFilter 需要用户交互，成本最高，放在最后——前面的检查都通过了，才需要问用户。如果调换顺序，用户可能被频繁打扰，审批的是前面就会被拦截的请求。

---

## 六、模式速查表

| 模式 | 核心意图 | 识别关键词 | 项目示例 | 面试高频问题 |
|------|---------|-----------|---------|-------------|
| 工厂模式 | 创建对象 | `Factory`、`Registry`、`supports()` | `ModelFactoryRegistry` | 和策略模式的区别？ |
| 策略模式 | 封装算法 | `Strategy`、`Registry`、降级链 | `ImageSearchStrategyRegistry` | 降级链设计注意什么？ |
| Agent 模式 | LLM 自主决策 | `AgentLoop`、`Supervisor`、`StateGraph` | 三种形态各适用什么场景？ | Supervisor 调度开销？ |
| 适配器模式 | 接口转换 | `Adapter`、`Client`、`DTO` | `QianfanAiClient` | 标配适配点有哪些？ |
| 责任链模式 | 解耦处理链路 | `Filter`、`Chain`、`@Order` | `SecurityFilterChain` | 链的顺序怎么定？ |

---

## 参考资料

- 《设计模式：可复用面向对象软件的基础》GoF 著
- 《重构：改善既有代码的设计》Martin Fowler 著
- Spring 源码：`org.springframework.core.annotation.Order`
- LangChain4j 源码：`dev.langchain4j.model.chat.ChatLanguageModel`

## 关联文档

- [Java AI 技术生态横评](java-ai-ecosystem-comparison.md)
- [面试 STAR 亮点](overall-star-highlights.md)
- ruoyi-ai：[15-star-highlights.md](../ruoyi-ai/15-star-highlights.md)
- ai-passage-creator：[03-image-strategy-pattern.md](../ai-passage-creator/03-image-strategy-pattern.md)
- mewpaw-code：[04-security-sandbox.md](../mewpaw-code/04-security-sandbox.md)
- zznursing：[02-baidu-qianfan-ai.md](../zznursing/02-baidu-qianfan-ai.md)