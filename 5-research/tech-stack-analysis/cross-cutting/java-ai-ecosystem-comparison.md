# 横向对比：四大项目的 Java AI 技术生态选型

> 从 4 个真实开源/实战项目中总结 Java 生态接入大模型的 4 种典型路径，一次看懂 LangChain4j、Spring AI Alibaba、原生 HTTP + SSE、LangChain4j 1.0 四种方案的取舍逻辑。
>
> **适用读者：** 准备 AI 相关岗位面试的 Java 后端工程师
> **对照维度：** 架构设计 / Agent 支持 / RAG 支持 / 流程编排 / 多模型支持 / 社区活跃度 / 适用场景

---

## 一、为什么需要这份对比

同样是用 Java 做 AI 应用，四个项目给出了四种截然不同的答案：

| 项目 | AI 技术选型 | 一句话定位 |
|------|------------|-----------|
| [ruoyi-ai](https://github.com/1byteone/ruoyi-ai) | LangChain4j 1.13.0 + langgraph4j 1.5.3 | 企业级 AI 应用框架（知识库 / 智能体 / MCP） |
| [ai-passage-creator](https://github.com/1byteone/ai-passage-creator) | Spring AI Alibaba 1.1.0（StateGraph） | AI 图文创作流水线 |
| [mewpaw-code](https://github.com/1byteone/mewpaw-code) | LangChain4j 1.0.0（ReAct 自定义循环） | CLI 编码 Agent |
| [zznursing](https://github.com/1byteone/zznursing) | 百度千帆 HTTP/SSE 原生调用 | IoT + AI 养老平台 |

面试官看到简历上同时出现"LangChain4j"和"Spring AI"时，最常见的第一个追问就是：**"这两个框架你分别用在什么场景、为什么这么选？"** 本对比文档就是回答这类问题的完整弹药库。

---

## 二、四大方案全景对比

### 2.1 一图看懂技术选型谱系

```
                          Java 接入大模型的 4 条路径
                                      │
        ┌─────────────────────────────┼─────────────────────────────┐
        │                             │                             │
   高层框架                      高层框架                     原生调用
   ┌──────────────┐        ┌──────────────────┐        ┌──────────────────┐
   │ LangChain4j  │        │ Spring AI Alibaba│        │ HTTP + SSE 直连  │
   │  1.13.0      │        │  1.1.0           │        │  百度千帆 API    │
   ├──────────────┤        ├──────────────────┤        ├──────────────────┤
   │ AiServices   │        │ @Agent 注解      │        │ WebClient        │
   │ @Tool 注解   │        │ StateGraph       │        │ Flux<String>     │
   │ langgraph4j  │        │ KeyStrategy      │        │ WebSocket 桥接   │
   │ 状态图编排   │        │ 节点/边/DAG      │        │                  │
   └──────────────┘        └──────────────────┘        └──────────────────┘
        │ 1.13（最新）               │ 1.0（较旧）
        ▼                            ▼
   ruoyi-ai                    mewpaw-code
   （完整生态）                  （ReAct 自定义循环）
```

关键洞察：**LangChain4j 与 Spring AI 是同一生态位的竞争者**，而百度千帆属于"原生接入"，三者不存在绝对优劣，取决于场景复杂度。

### 2.2 架构设计对比

| 维度 | ruoyi-ai（LangChain4j 1.13） | ai-passage-creator（Spring AI Alibaba） | mewpaw-code（LangChain4j 1.0） | zznursing（千帆原生） |
|------|------------------------------|----------------------------------------|-------------------------------|----------------------|
| 集成方式 | `@AiService` 接口代理 | `@Agent` 注解 + `AgentFactory` | 手动 `AiServices.builder()` | 手写 `QianfanAiClient` |
| 分层 | 应用层(AI能力) / AI层(框架) / 基础设施层 | 独立 `ai` 包 + `StateGraph` 编排层 | 交互层 / 核心引擎 / 工具层 / 安全层 | Controller → Service → Client |
| 与 Spring 整合 | Spring Boot Starter 自动装配 | Spring AI Alibaba 深度整合 | Spring Boot 3.3.5 CLI 模式 (WebApplicationType.NONE) | WebFlux WebClient |
| 可观测性 | 事件回调 + 流式 | `SseEmitter` 推 10 种命名事件 | `AgentEventSink` 8 种事件类型 | 打点日志 |
| 核心创新点 | 9 厂商工厂 + langgraph4j 11 节点状态图 | 5 Agent DAG + 三阶段人机协作 | 自定义 ReAct Loop + 5 层安全链 | SSE→WebSocket 协议桥接 |

### 2.3 核心概念映射表

同一个概念在不同框架中的名字完全不同，面试时最容易在这张表上露怯：

| 核心概念 | LangChain4j 1.13 | Spring AI Alibaba | 千帆原生 |
|----------|-----------------|-------------------|---------|
| LLM 统一接口 | `ChatLanguageModel` | `ChatClient` | 自封 `QianfanAiClient` |
| 函数/工具调用 | `@Tool` / `ToolRegistry` | `@ToolParam` + `tool` 注册 | 无（需手写解析） |
| Agent 声明 | `@AiService` 接口 | `@Agent(name=..)` 注解 | 无（纯函数式） |
| 状态图编排 | `langgraph4j` 的 `StateGraph` | `StateGraph`（同源思想） | 无 |
| 状态合并规则 | `Channel.Reducer`（overwrite/appender） | `KeyStrategy`（Override/Append） | 无 |
| 记忆管理 | `ChatMemory` | 会话对象 + Redis | Redis `ConversationManager` |
| Prompt 模板 | `@UserMessage`/`@SystemMessage` | `PromptTemplate` | 字符串拼接 |

---

## 三、分维度深度对比

### 3.1 架构设计：你是"框架信徒"还是"原生派"？

**ruoyi-ai —— Spring Boot 原生深度整合（LangChain4j Starter）**

```java
// @AiService 接口代理：定义接口即完成 Agent 注入
@AiService
public interface ChatAssistant {
    @UserMessage("你是 {{name}} 助手，用户问题是：{{message}}")
    String chat(@V("name") String name, @V("message") String message);
}
```

- 通过 Spring Boot Starter 自动装配，`@AiService` 生成 JDK 动态代理，业务代码零实现类
- 结合 `ModelFactoryRegistry`（Spring DI 自动收集 9 厂商实现）实现"增删厂商不改业务代码"
- 结合 `@RefreshScope` + 配置中心实现运行时热切换模型

**ai-passage-creator —— Spring AI Alibaba 注解驱动**

```java
@Agent(name = "titleGenerator", description = "根据选题生成标题",
       chatModel = "qwen-max", tools = {})
public class TitleGeneratorAgent {
    @AgentCall
    public List<TitleOption> generate(@AiParam("选题") String topic) {
        // ...
    }
}
```

- 注解驱动声明式开发，Agent 的模型、工具、能力描述全部声明在注解里
- 生态内建 `StateGraph` 工作流引擎，Node/Edge 以代码方式声明 DAG

**mewpaw-code —— 手动装配 + 自定义循环**

```java
Assistant assistant = AiServices.builder(Assistant.class)
        .chatLanguageModel(llm)
        .tools(toolRegistry.listTools())
        .build();
```

- 用 `AiServices.builder()` 手动装配，不走 Starter 自动配置
- 核心是自研 `AgentLoop`，LangChain4j 只提供"语言模型调用 + 工具解析"底座
- 事件驱动（`AgentEventSink`）与安全链（5 层 Filter）完全自研

**zznursing —— 纯原生 HTTP/SSE**

```java
Flux<String> flow = webClient.post()
        .uri(url + "?access_token=" + token)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(requestBody)
        .retrieve()
        .bodyToFlux(String.class)
        .filter(data -> data.startsWith("data: "))
        .map(data -> data.substring(6))
        .map(this::extractStreamContent);
```

- 无任何 AI 框架，`WebClient` 直连千帆 REST API，`Flux<String>` 接收 SSE 流
- Access Token 用 `AtomicReference` + 双重检查锁手动缓存（有效期 30 天）
- 多轮对话上下文用 Redis List 维护（最近 10 条）

> **面试结论：** 框架不代表水平，"为什么用/为什么不用框架"才是加分项。zznursing 选原生是因为只有问答场景，引入框架是过度设计；ruoyi-ai 选 LangChain4j 是因为要支撑多 Agent、RAG、MCP 全套能力。

### 3.2 Agent 支持：从"单 Agent"到"多 Agent 编排"

| 能力 | ruoyi-ai | ai-passage-creator | mewpaw-code | zznursing |
|------|----------|-------------------|-------------|-----------|
| Agent 声明方式 | `@AiService` 接口 | `@Agent` 注解 | `AiServices.builder()` | 无（单次对话） |
| 工具调用层数 | 两层（Supervisor 决策 + 子 Agent 执行） | 单层（LLM 调用工具） | 单层 ReAct | 无工具调用 |
| 调度方式 | LLM 意图分类 4 策略（单路/多路/链式/反馈） | DAG 静态编排 | 自定义循环 | 无 |
| 角色模型 | Supervisor + 4 子 Agent | 5 个专业 Agent | 单 Agent + 6 工具 | 单角色（健康顾问） |

**ruoyi-ai 是唯一实现"Agent 套 Agent"的两层结构的项目**：
- 1 个 Supervisor：LLM 做意图分析 → 结构化输出 → 路由
- 4 个子 Agent：SkillsAgent（MCP 调用）、WebSearchAgent、SQLAgent、ChartAgent
- 调度策略不只是"路由"，还有多路并行（fan-out）和链式传递（链式反馈）

**ai-passage-creator 则演示了"编排而非决策"：**
- 6 个节点（含 START/END）构成固定 DAG，无动态路由
- 靠 `node_async()` 并行（ContentGenerator 与 ImageAnalyzer 并行）
- 人机协作是亮点：标题选择（人工挑选）、大纲编辑（人工修改）后再进入下一节点

**mewpaw-code 强调"自定义循环的掌控力"：**
- 对比 LangChain4j 内置循环：`MAX_ITERATIONS=50`、`MAX_CONSECUTIVE_ERRORS=3`、输出截断、错误回填
- 这展示的不是"用框架"，而是"用框架的模型能力，自建设备逻辑"

### 3.3 RAG 支持：只有完整框架才能支撑完整 RAG

| RAG 能力 | ruoyi-ai | ai-passage-creator | mewpaw-code | zznursing |
|----------|----------|-------------------|-------------|-----------|
| 文档解析 | 4 格式工厂（PDF/Word/MD/Excel） | 无（写文章不读文档） | 无 | 无 |
| 切分策略 | 3 种（Token/Character/Markdown） | 无 | 无 | 无 |
| Embedding | 4 家厂商工厂 | 无 | 无 | 无 |
| 向量库 | Milvus/Weaviate/Qdrant 三选一 | 无 | 无 | 无 |
| 精排 Rerank | 3 家（百炼/SiliconFlow/智谱） | 无 | 无 | 无 |
| GraphRAG | Neo4j 知识图谱 | 无 | 无 | 无 |
| 评估 | RAGAS（Recall/Precision/Faithfulness） | 无 | 无 | 无 |

**ruoyi-ai 是四个项目里唯一完整实现工业级 RAG 全链路的**：
```
文档 → 解析(工厂) → 切分(3策略) → 向量化(4厂商) → 存储(3向量库)
     → 召回(Top-50) → GraphRAG 补强 → 融合 → Rerank(Top-5) → 生成
```
检索精度提升约 30%（Top-50 召回 → Top-5 精排）。

**面试要点：** RAG 只有在"非结构化知识 + 问答"这类项目里才有意义，另外三个项目的领域不需要 RAG，这也是选型的一部分。

### 3.4 流程编排：两种"状态图"的继承与区别

langgraph4j 与 Spring AI Alibaba 的 StateGraph 同源于 LangGraph 思想，但实现不同：

| 对比项 | ruoyi-ai langgraph4j 1.5.3 | ai-passage-creator Spring AI Alibaba |
|--------|---------------------------|--------------------------------------|
| 核心抽象 | `AgentState` + `Channel` | `State` + `KeyStrategy` |
| 状态合并 | `Channel.Reducer`（overwrite/appender） | `KeyStrategy`（Override/Append） |
| 节点类型 | 11 种（Start/End/LLMAnswer/Classifier/KeywordExtractor/KnowledgeRetrieval/Switcher/HttpRequest/Image/MailSend/HumanFeedback） | 自定义 Agent 节点 |
| 条件路由 | `ConditionalEdge` | `addConditionalEdge()` |
| 异步节点 | 内置支持 | `node_async()` 包装 |
| 并行 | 需自行设计 | `ParallelNode` |

**langgraph4j 的价值在于"开箱即用的 AI 节点"**：Switcher（条件分支）、Classifier（意图分类）、KnowledgeRetrieval（知识检索）这些都是 AI 场景的高频节点，不用自己写。

**Spring AI Alibaba 的价值在于"与 Spring 体系融合"**：`@Agent` 注解直接声明节点，`node_async()` 底层是 `CompletableFuture.supplyAsync()`，与 Spring 的线程池、事务、AOP 无缝协作。

### 3.5 多模型支持

| 能力 | ruoyi-ai | ai-passage-creator | mewpaw-code | zznursing |
|------|----------|-------------------|-------------|-----------|
| 厂商数 | 9 家（openai/deepseek/qwen/zhipu/glm/ollama/mimo/atla/custom） | 以 DashScope 为主（qwen） | 以 OpenAI 格式为主 | 文心一言 4.0/3.5 + 第三方 |
| 切换机制 | ModelFactoryRegistry + `@RefreshScope` | 注解 `chatModel` | 配置切换 | 双模型 URL（completions_pro / eb-instant） |
| 降级 | `ResilientAiChatService.chatWithFallback()` | 无（单一模型） | 无 | 模型分级路由 |
| 限流 | `RateLimitedChatModel`（Guava RateLimiter） | 无 | 无 | 每用户每分钟 5 次 |
| 成本控制 | 配置切换不同厂商 | 按任务选模型 | 无 | 简单问题用 3.5（低价），复杂用 4.0 |

### 3.6 社区活跃度（截至 2026 年）

> 数据基于公开信息，面试时讲趋势即可，不必背精确数字。

| 项目/框架 | 定位 | 活跃度 | 生态成熟度 |
|-----------|------|--------|-----------|
| LangChain4j | Java 版 LangChain 替代 | 高（发布节奏快，1.x 版本迭代到 1.13+） | 高（Starter 齐全：向量库、OCR、MCP） |
| langgraph4j | LangGraph 的 Java 移植 | 中高（1.5.3，功能追赶 Python 版） | 中（节点类型丰富，仍在演进） |
| Spring AI / Spring AI Alibaba | Spring 官方 AI 层 | 高（官方背书，2025 年 Spring AI 1.0 GA） | 高（与 Spring Boot/Cloud 深度整合） |
| 百度千帆 | 国内大模型 PaaS 平台 | 高（厂商服务） | 中（REST API + 各家 SDK 参差） |
| LangChain4j 1.0.0 | 早期版本 | 已被 1.x 新版本迭代覆盖 | 旧版 API 有差异（如 ToolRegistry） |

**趋势判断（可用于面试观点）：**
1. Spring AI 已 1.0 GA 并获得官方地位，Spring 生态存量用户迁移成本低，是"从 0 做 AI 项目"的最稳选择
2. LangChain4j 生态更贴近 Python 生态的 Agent 玩法（MCP、Graph、Memory），适合"复杂 Agent + RAG"场景
3. 两者未来会在 Java AI 领域长期并行，选型取决于你的团队是"Spring 原生派"还是"LangChain 生态派"
4. 国内企业落地普遍走"框架 + 国内模型（通义/DeepSeek/文心）+ 向量库（Milvus）"的组合路径

### 3.7 适用场景速查

| 场景 | 推荐方案 | 原因 |
|------|---------|------|
| 企业级 AI 平台（知识库/RAG/多 Agent） | LangChain4j 最新版 + langgraph4j | RAG、Graph、MCP 生态最全 |
| Spring 团队新的 AI 微服务 | Spring AI Alibaba | 与 Spring Boot/Cloud 无缝，注解开发效率高 |
| 固定流程的创作/生成任务 | Spring AI Alibaba StateGraph | DAG 编排 + 人机协作 + 并行天然契合 |
| CLI 工具类 Agent | LangChain4j（轻量装配） | 无需 RAG 与复杂编排，自定义循环更可控 |
| 单一模型问答/特定厂商（百度系） | 原生 HTTP/SSE | 无框架依赖，接入成本最低 |
| 已有 MCP 服务器要接入 | LangChain4j 1.13（MCP 支持完善） | 内置 SseMcpClientManager / StdioTransport |

---

## 四、选型决策框架（面试可直接复用）

```
第一步：这个项目的 AI 需求复杂度是几级？
    ├─ L1 单轮问答 → 原生 API 调用就够（zznursing 路径）
    ├─ L2 多轮 + 流式 → 加 Redis 会话 + WebSocket 桥接
    ├─ L3 工具调用 / Agent → 引入 LangChain4j 或 Spring AI
    └─ L4 多 Agent + RAG + 编排 → 框架 + 状态图引擎（ruoyi-ai 路径）

第二步：选 LangChain4j 还是 Spring AI？
    ├─ 存量 Spring 团队 / 需要官方支持 → Spring AI Alibaba
    ├─ 需要最大生态（RAG 组件/Vector Store/MCP）→ LangChain4j
    └─ 需要固定流水线编排 + 人机协作 → Spring AI StateGraph

第三步：要不要引入厂商 SDK？
    ├─ 单一厂商 → 统一走 HTTP 抽象层，避免 SDK 锁定
    └─ 多厂商 → 自建 Factory + Registry（参考 ModelFactoryRegistry）
```

---

## 五、面试问答（框架选型主题）

### 问题 1：LangChain4j 和 Spring AI 你更推荐哪个？为什么？

**考察点：** 是否有真实的框架对比经验，还是只会背概念。

**参考回答：**

> 两者我都实际用过，我的选择标准是"项目场景 + 团队结构"。
>
> **选 LangChain4j**：当项目需要完整的 AI 生态能力时——比如知识库（需要多格式文档解析、向量化、Rerank、GraphRAG）、多 Agent 编排（langgraph4j）、MCP 接入。LangChain4j 在 Java 生态里对这三块的组件覆盖是最全的，版本迭代也快。我在 ruoyi-ai 里用了 1.13.0，配合 langgraph4j 1.5.3，`@AiService` 接口代理 + `@Tool` 注解的开发体验非常顺。
>
> **选 Spring AI Alibaba**：当团队本来就是 Spring 生态、项目是固定流程的生成型任务时。Spring AI 1.0 GA 后有官方地位，与 Spring Cloud 微服务、配置中心整合是天然优势。我在 ai-passage-creator 里用 1.1.0 的 StateGraph 编排 5 个 Agent 的流水线，`@Agent` 注解 + `node_async()` 并行，比 langgraph4j 更贴近 Spring 心智。
>
> **我的结论**：二者同生态位，竞争会持续。如果让我从零选，AI 能力复杂度高选 LangChain4j，Spring 团队选 Spring AI。但更重要的是——把框架抽象层做好，让框架本身可替换，这才是架构师思维。

**追问应对：** "那 langgraph4j 和 Spring AI StateGraph 有区别吗？" → 见 3.4 节对比表。核心差异是 langgraph4j 有 11 种 AI 专用节点（Switcher/Classifier/KnowledgeRetrieval），Spring AI 的节点都是你自定义的 Agent。

### 问题 2：什么情况下你会选择"不引入 AI 框架"，直接调 API？

**考察点：** 是否理解框架的代价，避免"为用框架而用框架"。

**参考回答：**

> 有明确的边界：**当 AI 需求只有单一问答、且不需要工具调用和复杂上下文时，原生接入更优。**
>
> 我在 zznursing 里就是直接调百度千帆 API，理由有三点：
> 1. **场景简单**：只有"家属提问 → 组装 Prompt（角色 + 老人健康数据 + 问题）→ 流式回复"一个场景，框架的 Agent/RAG/编排能力全部用不上
> 2. **控制力更强**：Access Token 缓存、SSE 解析、WebSocket 桥接、温度参数（医疗场景 0.3）都要精确控制，手写反而更清晰
> 3. **依赖更少**：少引入一个框架，就少一层版本冲突和适配成本
>
> 但我给这个客户端加了抽象——`QianfanAiClient` 封装成独立组件，Service 层只依赖它。将来要换模型平台，只改这一个类。
>
> **判断标准**：一个项目如果超过 2 个 AI 场景、或者需要工具调用/RAG，就该引入框架；单一问答场景，原生接入是更克制的选择。

**追问应对：** "原生接入时你怎么做多轮对话？" → Redis List 维护最近 10 条消息（`ConversationManager`），注意消息长度的窗口控制与 Token 成本。

### 问题 3：LangChain4j 1.0 和 1.13 有什么差异？升级要注意什么？

**考察点：** 版本敏感度，是否"用过旧版本也关注新版本"。

**参考回答：**

> mewpaw-code 用的是 1.0.0，ruoyi-ai 用的是 1.13.0，我确实体验过差异。
>
> **API 层面**：1.x 里 `AiServices` 仍是核心，但工具注册从手写 `ToolRegistry` 演进到更完善的 `@Tool` 注解体系，1.0 时代的很多工具类（比如 `BashTool` 这类）在新版合并进了官方工具包。
>
> **能力层面**：1.13 最大的变化是 **MCP（Model Context Protocol）支持完善**——内置了 `SseMcpClientManager`（SSE 传输）和 `StdioTransport`（子进程传输），可以直接对接 MCP 服务器挂载文件系统、Python 脚本等外部工具，这是 1.0 没有的。
>
> **升级注意点**：
> 1. **依赖传递**：1.x 大版本间 Spring Boot Starter 的 artifactId/groupId 可能有变化，升级前先核对 BOM
> 2. **行为差异**：内置循环的参数（如 `maxToolCallingRoundTrips`）和错误处理策略有调整，测试用例要回归
> 3. **功能弃用**：部分旧 API 标记 deprecated，编译期会有 warning，逐步迁移
>
> 我的建议：升级前先看 changelog 的核心功能表，把老版本的能力映射到新 API，然后让测试覆盖"工具调用、流式、错误恢复"三条主线。

**追问应对：** "mewpaw-code 为什么不用新版？" → 项目锁定 LangChain4j 1.0.0 是为了演示"自研 ReAct 循环 + 5 层安全链"的能力——框架版本越基础，越能展示对语言模型调用、工具解析、上下文管理的底层理解。

---

## 六、一页速览：面试前 30 秒

```
Java AI 框架选型四问：

Q1: 你的 AI 需求复杂度？  →  L1 问答=L0 原生 | L3+ Agent/RAG=框架
Q2: LangChain4j or Spring AI?
    → 生态全(Agent/RAG/MCP)=LangChain4j | Spring 团队=Spring AI
Q3: 要不要多厂商?         → 自建 Factory + Registry（开闭原则）
Q4: 要不要自己造循环?    → 想展示底层能力=自定义 AgentLoop，否则用框架内置

关键记忆点：
- LangChain4j：@AiService 接口代理 | @Tool | langgraph4j 状态图 | MCP
- Spring AI Alibaba：@Agent 注解 | StateGraph | KeyStrategy | node_async()
- 千帆原生：Access Token 缓存 | SSE→WebSocket | temperature=0.3(医疗)
- 复用判断：框架无优劣，场景识别 + 抽象层 = 面试加分项
```

---

## 参考资料

- LangChain4j 官方文档：https://docs.langchain4j.dev/
- LangGraph4j 仓库：https://github.com/bsorrentino/langgraph4j
- Spring AI 官方文档：https://docs.spring.io/spring-ai/reference/
- Spring AI Alibaba 文档：https://java.alibabacloud.com/spring-ai/
- 百度千帆文档：https://cloud.baidu.com/product/wenxinworkshop

## 关联文档

- 上一级目录：[技术栈分析总览](../README.md)（若存在）
- 各项目详情：[ruoyi-ai](../ruoyi-ai/00-PROJECT-OVERVIEW.md) / [ai-passage-creator](../ai-passage-creator/00-PROJECT-OVERVIEW.md) / [mewpaw-code](../mewpaw-code/00-PROJECT-OVERVIEW.md) / [zznursing](../zznursing/00-PROJECT-OVERVIEW.md)
- 架构模式提炼：[enterprise-architecture-patterns.md](enterprise-architecture-patterns.md)
- 面试 STAR 亮点：[overall-star-highlights.md](overall-star-highlights.md)