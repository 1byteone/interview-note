# ruoyi-ai 面试题汇总

> 覆盖全部技术栈：Spring Boot 3.5 + LangChain4j 1.13.0 集成、多厂商大模型统一接入（工厂模式）、RAG 全链路 + GraphRAG、向量存储策略（Milvus/Weaviate/Qdrant）、langgraph4j 1.5.3 流程编排引擎、Supervisor 多智能体调度、MCP 协议、Redis + Redisson 缓存与分布式锁、Sa-Token 认证鉴权、SSE 流式推送、MyBatis-Plus 多数据源、Warm-Flow BPM 等。

---

## 一、选择题（10 道）

### 第 1 题
ruoyi-ai 项目中，LangChain4j 的 `@AiService` 注解的工作原理是什么？

A. 使用 CGLIB 动态代理创建实现类，方法调用被转换为 HTTP 请求调用 LLM API  
B. 使用 JDK 动态代理创建接口代理实例，通过反射解析方法注解，动态组装 ChatRequest 后调用 ChatLanguageModel  
C. 使用 Spring AOP 切面拦截方法调用，在每个方法执行前调用 LLM API  
D. 使用 ASM 字节码增强技术，在编译期生成实现类，直接调用 LLM SDK

**答案：B**

**解析：** `@AiService` 基于 JDK 动态代理（`java.lang.reflect.Proxy`）为接口创建代理实例。代理拦截方法调用，通过反射解析 `@UserMessage`、`@SystemMessage`、`@MemoryId`、`@V` 等注解，动态组装 ChatRequest，调用 `ChatLanguageModel` 的 `generate()` 方法执行 LLM 推理，最后将 LLM 返回的 `Response<AiMessage>` 解析为方法声明的返回值类型。与 Spring 的 `@Service` 不同，`@AiService` 是 LangChain4j 的注解，不依赖 CGLIB 或 AOP。

---

### 第 2 题
在 ruoyi-ai 的多厂商大模型统一接入设计中，`ModelFactoryRegistry` 如何实现工厂的注册与发现？

A. 使用 XML 配置文件声明所有工厂类，启动时通过 DOM 解析注册  
B. 使用 `@Component` 注解 + 构造器注入 `List<ModelFactory>`，Spring 自动收集到 `Map<String, ModelFactory>` 中  
C. 在 application.yml 中配置工厂列表，通过 `@Value` 注入后手动创建  
D. 使用 SPI 机制（`META-INF/services`）加载所有 ModelFactory 实现

**答案：B**

**解析：** `ModelFactoryRegistry` 的构造器注入 `List<ModelFactory>`，Spring 容器自动将 `ModelFactory` 接口的所有实现类收集为列表。注册表内部通过 `Map<String, ModelFactory>` 存储，key 为 `getProviderType()` 返回值（如 "openai"、"deepseek"、"qwen"）。新增厂商只需实现 `ModelFactory` 接口并标注 `@Component`，完全符合开闭原则，"对扩展开放，对修改关闭"。

---

### 第 3 题
ruoyi-ai 的 RAG 管线中，向量检索召回 Top-50 后，通过什么机制精排到 Top-5？

A. 使用 Cosine 相似度重新计算，取分数最高的前 5 个  
B. 使用 Cross-Encoder Rerank 模型（如 bge-reranker-v2-m3）对 Top-50 进行精排，取前 5 个  
C. 使用 HNSW 索引的 efSearch 参数缩小搜索范围，直接取 Top-5  
D. 使用 LLM 对 Top-50 逐条打分，取分数最高的 5 个

**答案：B**

**解析：** RAG 管线采用"宽进严出"策略：向量检索（Bi-Encoder）召回 Top-50（minScore 0.5），再通过 Cross-Encoder Rerank 模型进行精排，取 Top-5。Bi-Encoder 效率高但精度有限，Cross-Encoder 精度高但计算成本高，两者结合实现"效率与精度的平衡"。项目中支持 AliBaiLian、SiliconFlow（bge-reranker-v2-m3）、ZhipuAI 三家 Rerank 服务，通过 `RerankService` 接口统一封装。

---

### 第 4 题
ruoyi-ai 的向量存储策略中，`VectorStoreFactory` 如何实现"一行配置切换向量库"？

A. 使用 `@ConditionalOnProperty` 在启动时按配置只装配一个 VectorStore Bean  
B. 使用 Spring Map 注入（`Map<String, VectorStore>`），启动时自动注册所有实现，运行时按 `type` 查表获取对应实例  
C. 使用 if-else switch-case 在工厂方法中判断 type 值创建对应实例  
D. 使用 `@Profile` 注解，不同环境（dev/prod）使用不同的向量库

**答案：B**

**解析：** `VectorStoreFactory` 构造器注入 `Map<String, VectorStore>`，Spring 将容器中所有 `VectorStore` 类型的 Bean 按 Bean 名称收集为 Map（key = "milvus"/"weaviate"/"qdrant"）。`getStore(type)` 从 Map 中按 key 查询，`getDefaultStore()` 从 `VectorStoreProperties` 读取 `type` 字段（yml 中 `ai.vector-store.type`）。新增向量库只需写一个 `@Component("xxx")` 实现类，工厂零改动，完全符合开闭原则。

---

### 第 5 题
langgraph4j 中，`Channel.Reducer` 的 `Channels.overwrite()` 和 `Channels.appender()` 分别适用于什么场景？

A. `overwrite` 适用于列表字段，`appender` 适用于单值字段  
B. `overwrite` 适用于单值字段（如 input/output），`appender` 适用于需要累积的列表字段（如 keywords/knowledge）  
C. 两者功能相同，都是覆盖策略  
D. `overwrite` 适用于 Map 类型，`appender` 适用于 List 类型

**答案：B**

**解析：** `Channels.overwrite()` 定义新值直接覆盖旧值的策略，适用于单值字段如 `input`、`output`、`classifyResult`。`Channels.appender(ArrayList::new)` 定义新值追加到列表末尾的策略，适用于需要累积的字段如 `keywords`（关键词列表）和 `knowledge`（知识检索结果列表）。用错 Reducer 会导致数据丢失（该追加的被覆盖）或数据重复（该覆盖的被追加）。

---

### 第 6 题
在 Supervisor 多智能体调度架构中，Supervisor 如何决定将任务分配给哪个子智能体？

A. 使用轮询策略，依次尝试每个子智能体直到找到能处理的  
B. 所有子智能体同时处理，取最先返回的结果  
C. 调用 LLM 分析用户输入，输出结构化决策（意图分类 + 参数提取），根据决策结果路由到对应子智能体  
D. 使用固定规则匹配关键词，匹配到哪个就分配给哪个

**答案：C**

**解析：** Supervisor 不硬编码规则，而是调用 LLM 分析用户输入，输出结构化的决策结果（包括意图分类、参数提取等）。根据 LLM 的决策，Supervisor 将任务路由到 Skills Agent（本地文档技能）、WebSearch Agent（网络搜索）、SQL Agent（数据查询）或 Chart Agent（图表生成）之一。整体架构是"LLM 决策 + 规则路由"的组合模式，兼顾灵活性与可控性。

---

### 第 7 题
ruoyi-ai 中 MCP 协议的 SSE 传输层，服务端如何管理多个客户端的连接？

A. 使用 HTTP Session 机制，每个客户端自动关联一个 Session  
B. 使用 `ConcurrentHashMap<String, SseEmitter>` 管理，每个连接分配唯一 sessionId  
C. 使用 WebSocket 的 session 管理机制  
D. 使用 Redis 共享 Session，所有客户端连接共享同一个 SseEmitter

**答案：B**

**解析：** `McpSseController` 中使用 `ConcurrentHashMap<String, SseEmitter>` 管理多个 SSE 连接。建立连接时生成唯一 sessionId（UUID），存入 Map；通过 `onCompletion` 和 `onTimeout` 回调在连接关闭或超时时自动清理。客户端通过 `X-Session-Id` 请求头标识会话，服务端异步处理请求后通过对应 emitter 推送结果事件。这种设计支持多客户端并发连接，且线程安全。

---

### 第 8 题
ruoyi-ai 使用 Redisson 实现分布式锁时，WatchDog 机制的作用是什么？

A. 监控锁的竞争情况，当锁冲突严重时自动降级为本地锁  
B. 当业务执行时间超过锁的过期时间时，自动续期，防止业务未完成锁先释放  
C. 监视所有锁的持有者，当持有者崩溃时立即释放锁  
D. 定时检查锁是否被非法持有，发现后强制释放

**答案：B**

**解析：** WatchDog 是 Redisson 的核心机制：默认锁过期时间 30 秒，每 10 秒检查一次业务是否仍在执行，若未结束自动续期 30 秒。这解决了原生 `SETNX + EXPIRE` 最大的痛点——"业务执行时间 > 锁过期时间"导致锁提前释放，其他实例趁虚而入。若持有锁的线程崩溃，WatchDog 随 JVM 守护线程销毁，锁正常超时释放，不会永久占用。

---

### 第 9 题
ruoyi-ai 项目中，Sa-Token 的 `StpUtil.login()` 方法执行后，内部完成了哪些核心操作？

A. 仅生成一个随机 Token 字符串，存入 Redis 后返回  
B. 生成 Token 并存入 Redis，同时创建 SecurityContextHolder 的认证上下文  
C. 创建用户会话，生成 Token 关联用户信息，写入 Redis 并设置过期时间，返回 Token 给前端  
D. 只生成 JWT 令牌并返回，不存储任何服务端状态

**答案：C**

**解析：** `StpUtil.login()` 内部执行完整登录流程：创建用户会话（SaSession），生成唯一的 Token 字符串，将 Token 与用户信息（userId、角色、权限等）关联并写入 Redis，设置会话过期时间，返回 Token 给前端。前端后续请求携带此 Token 即可完成身份认证。Sa-Token 采用"Token 即 SessionId"的设计理念，认证信息服务端集中管理，支持集群环境下的会话共享。

---

### 第 10 题
ruoyi-ai 的 langgraph4j 流程编排中，`interruptBefore("humanFeedback")` 和 `interruptAfter("humanFeedback")` 的区别是什么？

A. 两者功能相同，都是让节点暂停执行  
B. `interruptBefore` 在节点执行前暂停，节点本身不执行；`interruptAfter` 在节点执行后暂停，节点已执行完毕  
C. `interruptBefore` 暂停整个图，`interruptAfter` 只暂停当前分支  
D. `interruptBefore` 用于人工审核场景，`interruptAfter` 用于异步任务场景

**答案：B**

**解析：** `interruptBefore("humanFeedback")` 在 humanFeedback 节点执行前暂停，此时节点本身未执行，检查点持久化当前状态，等待人工输入反馈后，节点才接收反馈数据开始执行。`interruptAfter` 在节点执行后暂停，节点已执行完毕，适合需要人工确认执行结果的场景。项目中采用 `interruptBefore`，因为 HumanFeedback 节点需要等待人工输入反馈后才能执行。

---

## 二、判断题（5 道）

### 第 1 题
LangChain4j 的 Spring Boot Starter 需要在启动类上添加 `@EnableLangChain4j` 注解才能生效。

**答案：错误**

**解析：** LangChain4j 的 Spring Boot Starter 遵循 Spring Boot 自动配置规范，通过 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 文件注册 `LangChain4jAutoConfiguration`，引入依赖后自动生效，无需手动添加 `@Enable` 注解。这与 Spring Boot 集成 Redis、DataSource 等中间件的原理一致。

---

### 第 2 题
ruoyi-ai 的 RAG 管线中，GraphRAG 和向量检索是二选一的关系，只能使用其中一种检索方式。

**答案：错误**

**解析：** 项目中 GraphRAG（Neo4j 图谱检索）和向量检索（Milvus/Weaviate/Qdrant）是并行使用的双路检索策略。`RagRetrievalService.retrieve()` 中同时执行向量检索（Top-50）和 GraphRAG 检索，然后通过 `fuseResults()` 融合两路结果（GraphRAG 权重 0.95），最后统一交给 Rerank 精排。两者互补：向量检索擅长语义相似度，GraphRAG 擅长关系推理。

---

### 第 3 题
在 langgraph4j 的状态图中，边只能定义从 A 到 B 的直线路径，不支持循环执行。

**答案：错误**

**解析：** langgraph4j 的 StateGraph 支持循环边，这是智能体 ReAct 模式的关键能力。例如 Agent 调用工具后，结果可以返回到 LLM 节点继续处理，形成"思考-行动-观察"的循环。这是 langgraph4j 与传统 BPMN 工作流引擎（如 Activiti、Camunda）的重要区别——BPMN 的流程图是静态定义的有向无环图，而 StateGraph 支持有环图。

---

### 第 4 题
Lock4j 是一个独立的分布式锁实现，不依赖任何第三方客户端。

**答案：错误**

**解析：** Lock4j 本身不是锁的实现，而是基于注解 + AOP 的声明式锁封装框架。它通过 `LockTemplate` 抽象接口对接具体实现，`ruoyi-ai` 中默认对接 Redisson 的 `RLock`。Lock4j 负责"加锁/解锁的声明式编程"，Redisson 负责"锁的真正实现"。两者关系是"封装层与实现层"，不是并列的两个方案。

---

### 第 5 题
Sa-Token 的 `@SaCheckLogin` 和 `@SaCheckPermission` 注解可以同时标注在同一个方法上，实现登录认证 + 权限校验的双重控制。

**答案：正确**

**解析：** Sa-Token 的注解设计支持多层叠加。`@SaCheckLogin` 确保用户已登录，`@SaCheckPermission("system:model:edit")` 确保用户拥有指定权限。两者同时标注时，AOP 切面会依次校验，先验证登录状态，再验证权限，任何一层不通过即抛出对应异常，由全局异常处理器统一处理返回友好提示。

---

## 三、简答题（10 道）

### 第 1 题：Spring Boot 如何集成 LangChain4j？自动配置的原理是什么？

**参考答案：**

集成分为三步：引入 `langchain4j-spring-boot-starter` 依赖（版本 1.13.0）→ 在 `application.yml` 中配置 `langchain4j.open-ai.chat-model` 参数（base-url、api-key、model-name）→ 定义 `@AiService` 接口，直接注入到 Controller 中使用。

自动配置原理：LangChain4j 的 Starter 在 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 中注册 `LangChain4jAutoConfiguration`。该配置类使用 `@ConditionalOnClass(ChatLanguageModel.class)` 确保 classpath 中存在 LangChain4j 核心库时才激活，使用 `@EnableConfigurationProperties(LangChain4jProperties.class)` 绑定 `langchain4j.*` 配置前缀，通过 `@Bean` 方法创建 `ChatLanguageModel`、`StreamingChatLanguageModel`、`EmbeddingModel` 等 Bean，最后通过 `AiServices` 的 Builder API 为 `@AiService` 接口创建动态代理并注册到 Spring 容器。

---

### 第 2 题：多厂商大模型统一接入的工厂模式是如何设计的？如何做到"一行配置切换模型"？

**参考答案：**

采用三层架构：

1. **统一接口层**：`ModelFactory` 接口定义了 `createModel(ModelConfig config)` 和 `getProviderType()` 两个方法，所有厂商适配器实现此接口
2. **注册层**：`ModelFactoryRegistry` 通过构造器注入 `List<ModelFactory>`，Spring 自动收集所有实现，构建 `Map<String, ModelFactory>`（key = provider 类型），同时使用 `ConcurrentHashMap` 缓存已创建的模型实例（cache key = `provider:modelName`）
3. **配置层**：`AiModelProperties` 使用 `@ConfigurationProperties(prefix = "ai")` 绑定 yml 配置，包含 `activeProvider` 和 `Map<String, ModelConfig> models`

切换方式：修改 yml 中 `ai.active-provider` 的值（如从 "deepseek" 改为 "qwen"），`AiChatService` 内部通过 `ModelFactoryRegistry.getModel(activeProvider, modelName)` 获取对应模型。新增厂商只需：实现 `ModelFactory` 接口 → 标注 `@Component` → 在配置文件中添加对应配置，零修改已有代码。

---

### 第 3 题：RAG 管线中，向量检索 + Rerank 精排的双阶段召回策略是如何设计的？

**参考答案：**

采用"宽进严出"的双阶段策略：

**第一阶段：向量检索（Bi-Encoder）**
- 用户问题经 Embedding 模型向量化
- 在向量库中执行 ANN 搜索，召回 Top-50（minScore=0.5）
- Bi-Encoder 将问题和文档分别编码为向量，计算余弦相似度，效率高

**第二阶段：Cross-Encoder Rerank 精排**
- 将 Top-50 结果逐条与用户问题拼接，输入 Cross-Encoder 模型
- Cross-Encoder 同时"看"问题和文档，计算更精确的相关性分数
- 取分数最高的 Top-5 作为最终结果
- 支持 AliBaiLian、SiliconFlow（bge-reranker-v2-m3）、ZhipuAI 三家 Rerank 服务

**设计意图**：Bi-Encoder 效率高但精度有限，Cross-Encoder 精度高但计算成本高。向量检索负责"宽进"（保证召回率），Rerank 负责"严出"（保证精度），两者互补实现效率与精度的平衡。

---

### 第 4 题：项目中如何通过工厂策略模式支持多种向量数据库（Milvus/Weaviate/Qdrant）？

**参考答案：**

分四层设计：

1. **统一接口层**：定义 `VectorStore` 接口，收敛所有向量操作到两个方法：`add(segments, embeddings)` 批量写入、`search(query, topK)` 相似度检索
2. **实现层**：`@Component("milvus")`、`@Component("weaviate")`、`@Component("qdrant")` 三个策略实现类，各自封装 LangChain4j 的 `MilvusEmbeddingStore`、`WeaviateEmbeddingStore`、`QdrantEmbeddingStore`
3. **注册层**：Spring 的 Map 注入（`Map<String, VectorStore>`），自动将 Bean 名称作为 key 收集为注册表
4. **路由层**：`VectorStoreFactory.getStore(type)` 从 Map 中按 type 取实例，`getDefaultStore()` 从 `VectorStoreProperties` 读取 `type` 字段

切换方式：修改 `ai.vector-store.type` 为 milvus/weaviate/qdrant 即可，业务代码零改动。新增向量库只需写一个 `@Component("xxx")` 实现类。

---

### 第 5 题：langgraph4j 的 InterruptedFlow（人机协作断点）机制是如何实现的？

**参考答案：**

InterruptedFlow 由三部分配合实现：

1. **编译时配置断点**：`CompileConfig.builder().checkpointSaver(new MemorySaver()).interruptBefore("humanFeedback").build()`，告诉引擎执行到 humanFeedback 节点前暂停，并将当前状态持久化到 checkpoint

2. **执行时自动暂停**：`graph.stream(input, config)` 执行到 humanFeedback 前自动退出循环，此时状态已通过 checkpointSaver 持久化。前端通过 SSE 接收节点执行事件，知道流程已暂停在审核节点

3. **恢复执行**：调用 `graph.getState(config)` 获取暂停时的 checkpoint 配置，通过 `GraphInput.resume(update)` 注入人工反馈（如 "approved"），从断点处继续执行。HumanFeedbackNode 读取反馈，更新状态，继续后续流程

**生产注意事项**：开发环境用 `MemorySaver`（重启丢失），生产环境用 `PostgresSaver` 或 `RedisSaver` 持久化 checkpoint。`threadId` 用于隔离不同会话的断点。

---

### 第 6 题：Supervisor 多智能体调度架构是如何设计的？各子智能体承担什么职责？

**参考答案：**

Supervisor 采用"中央调度 + 专业执行"的分层架构：

**Supervisor（调度中枢）**：接收用户输入，调用 LLM 进行意图分析，输出结构化决策（意图分类 + 参数提取），根据决策结果将任务路由到对应的子智能体。

**四个子智能体：**
- **Skills Agent**：负责本地文档技能，通过 MCP 协议集成文件系统工具（read_file、write_file、list_directory）和 Python 脚本执行工具，处理文档解析、数据清洗等任务
- **WebSearch Agent**：负责网络搜索，通过 MCP 协议连接外部搜索服务，获取实时信息
- **SQL Agent**：负责数据查询，将自然语言问题转换为 SQL 语句，执行数据库查询并返回结果
- **Chart Agent**：负责图表生成，根据数据生成可视化图表

**设计特点**：每个子智能体职责单一、独立部署，新增子智能体不影响已有系统。Supervisor 的决策逻辑由 LLM 驱动，不依赖硬编码规则，灵活适应多种场景。

---

### 第 7 题：MCP 协议在 ruoyi-ai 中解决了什么问题？SSE 传输是如何实现的？

**参考答案：**

MCP（Model Context Protocol）解决了 AI 应用与外部工具之间"碎片化集成"的问题。在没有 MCP 之前，工具需要硬编码、集成方式碎片化、参数描述不统一、生命周期管理各自实现。

**MCP 带来的改变：**
- 统一标准协议，工具动态发现
- 工具描述标准化（JSON Schema），LLM 准确理解参数
- 生命周期管理标准化（连接、心跳、重连、关闭）

**SSE 传输实现：**

服务端（`McpSseController`）：
- `POST /mcp/sse`：建立 SSE 连接，创建 `SseEmitter`（超时 0 = 不超时），生成唯一 sessionId 存入 `ConcurrentHashMap`，发送 `initialized` 事件确认
- `POST /mcp/message`：接收客户端消息，根据 `method` 字段（`list_tools`/`call_tool`）分发处理，通过 `CompletableFuture` 异步执行，结果通过 SSE 推送

客户端（`SseMcpClientManager`）：
- `McpTransport.sse(serverUrl)` 创建 SSE 传输层
- `McpClient.builder().transport(transport).build()` 创建客户端
- `client.initialize()` 初始化连接，`client.listTools()` 发现工具，`client.callTool()` 调用工具

**与 LangChain4j 集成**：将 MCP 工具包装为 `@Tool` 注解方法，通过 `AiServices.builder().tools()` 注册，LLM 通过 Function Calling 自动决策调用。

---

### 第 8 题：项目中 Redis 缓存如何应对缓存穿透、击穿和雪崩三大难题？

**参考答案：**

| 难题 | 现象 | 对策 |
|------|------|------|
| **穿透** | 查不存在的数据，缓存永远 miss 打到 DB | 缓存空值（短 TTL 60s），布隆过滤器可选 |
| **击穿** | 热点 Key 过期瞬间所有请求并发打 DB | Redisson 分布式锁互斥重建 + double-check 二次查缓存 |
| **雪崩** | 大量 Key 同时过期 | TTL 加随机因子（300 + random(60)秒）错峰过期 |

**互斥重建实现要点**：缓存未命中时获取分布式锁，拿到锁后二次查缓存（double-check），防止上一个线程已重建。拿不到锁的线程等待后重查缓存而不是直接 fail。释放锁时必须放在 finally 块中并检查 `isHeldByCurrentThread()`。

---

### 第 9 题：Sa-Token 在项目中如何实现登录认证和权限校验？

**参考答案：**

Sa-Token 在 ruoyi-ai 中承担认证和授权两大职责：

**登录认证**：用户登录成功后调用 `StpUtil.login(userId)`，内部创建用户会话，生成 Token 关联用户信息，写入 Redis 并设置过期时间，返回 Token 给前端。前端后续请求携带 Token（通常放在 Header 中），`StpUtil.getLoginId()` 获取当前登录用户 ID。

**权限校验**：支持两种方式：
- 编程式：`StpUtil.checkPermission("system:model:edit")`
- 注解式：`@SaCheckPermission("system:model:edit")`

**集成特点**：Sa-Token 采用"Token 即 SessionId"的设计理念，认证信息集中存储在 Redis 中，天然支持集群环境的会话共享，无需额外配置 Session 同步。与 Spring Boot 集成通过 `sa-token-spring-boot-starter` 自动配置完成。

---

### 第 10 题：项目中如何实现 SSE 流式推送？LangChain4j 的 TokenStream 和 Spring 的 SseEmitter 如何配合？

**参考答案：**

ruoyi-ai 中有两处 SSE 流式推送：

**1. AI 对话流式输出（ChatController）**：
```java
@GetMapping("/stream")
public Flux<String> stream(@RequestParam String message) {
    return Flux.create(emitter -> {
        streamingModel.generate(message)
            .onPartialResponse(emitter::next)       // 每次收到部分响应就推送
            .onCompleteResponse(r -> emitter.complete())  // 完成时关闭流
            .onError(emitter::error)                // 错误时通知前端
            .start();                               // 启动流式处理
    });
}
```
`StreamingChatLanguageModel.generate()` 返回 `TokenStream`，支持 `onPartialResponse`（每次收到 Token 片段）、`onCompleteResponse`（响应完成）、`onError`（错误处理）三个回调，配合 Spring WebFlux 的 `Flux` 实现 SSE 推送。

**2. 流程执行状态推送（AiFlowExecutionService）**：
`graph.stream()` 每执行一个节点就返回一个事件，通过 `SseEmitter` 的 `event().name("node-executed").data(...)` 推送到前端，让用户实时看到流程执行进度。

---

## 四、场景题（5 道）

### 第 1 题：知识库问答场景
**场景：** 用户上传了一份 100 页的 PDF 技术文档，然后提问"第三章第二节中提到的配置参数有哪些？"。请描述 ruoyi-ai 从文档上传到生成回答的完整处理链路。

**参考答案：**

完整链路分为入库和检索两个阶段：

**入库阶段**：
1. 文档解析：`DocumentParserFactory` 根据 `.pdf` 后缀路由到 `PdfDocumentParser`，使用 Apache PDFBox 逐页解析，每页保留页码信息
2. 文档切分：`DocumentSplitter` 将 PDF 内容切分为适合检索的文本块，支持三种策略：
   - 按 Token 切分（`OpenAiTokenCountEstimator` + `DocumentByParagraphSplitter`）
   - 按字符切分（句号智能切分 + 重叠）
   - 按 Markdown 标题切分（正则 `^(#+ .+)`）
3. 向量化：`EmbeddingService.embedAndStore()` 调用 Embedding 模型（如 text-embedding-3-small）将每个切片转为向量
4. 写入向量库：通过 `VectorStoreFactory` 获取当前配置的向量库（Milvus/Weaviate/Qdrant），批量写入

**检索阶段**：
1. 用户问题向量化：使用与入库时相同的 Embedding 模型
2. 向量检索：召回 Top-50（minScore 0.5）
3. GraphRAG 补充检索：`graphRagService.searchByQuery()` 从 Neo4j 知识图谱检索相关实体关系
4. 结果融合：`fuseResults()` 合并两路结果（GraphRAG 权重 0.95）
5. Rerank 精排：Cross-Encoder 模型对融合结果重排序，取 Top-5
6. 构建上下文：将 Top-5 结果拼接为 LLM 的上下文
7. LLM 生成回答：`ChatLanguageModel` 根据用户问题 + 检索上下文生成最终回答

---

### 第 2 题：多模型切换与容错场景
**场景：** 生产环境中，主力模型 DeepSeek 突然 API 超时不可用，需要自动切换到备用模型 GLM-5.2，同时限流避免大量请求同时压到备用模型。请描述 ruoyi-ai 如何应对。

**参考答案：**

ruoyi-ai 通过以下机制应对：

**1. 多模型配置**：`AiModelProperties` 配置了多个模型，每个模型可设置 `primary`（是否主模型）和 `weight`（权重），yml 中配置了 DeepSeek（主）和 GLM-5.2（备）。

**2. 降级切换**：`ResilientAiChatService.chatWithFallback()` 实现了主备切换：
- 优先调用主模型（DeepSeek）
- 捕获异常或超时后，自动降级到备用模型（GLM-5.2）
- 降级逻辑对业务层透明，调用方无感知

**3. 限流保护**：`RateLimitedChatModel` 使用 Guava `RateLimiter` 的 `rateLimiter.acquire()` 进行限流，防止切换瞬间大量请求压垮备用模型。

**4. 配置刷新**：使用 `@RefreshScope` 注解，配合配置中心（Nacos/Apollo），运维人员可以在运行时修改 `ai.active-provider` 配置，无需重启应用。

**5. 缓存管理**：`ModelFactoryRegistry` 的 `clearCache()` 方法在配置刷新时清除已缓存的模型实例，下次请求自动按新配置创建。

---

### 第 3 题：人工审核流程场景
**场景：** 运营人员需要设计一个 AI 流程：用户输入营销文案需求 → AI 生成草稿 → 运营审核 → 审核通过后发送邮件。请描述如何使用 langgraph4j 实现这个带人机协作的流程。

**参考答案：**

使用 langgraph4j 的 StateGraph + InterruptedFlow 实现：

**图定义（AiFlowGraphBuilder）**：
```java
// 1. 注册节点
graph.addNode("classifier", new ClassifierNode(model));       // 意图分类
graph.addNode("llmAnswer", new LLMAnswerNode(model));         // 生成营销文案
graph.addNode("humanFeedback", new HumanFeedbackNode());      // 人工审核
graph.addNode("mailSend", new MailSendNode());                // 发送邮件

// 2. 定义边
graph.addEdge(START, "classifier");
graph.addEdge("classifier", "llmAnswer");     // 分类后生成文案
graph.addEdge("llmAnswer", "humanFeedback");  // 生成后等待人工审核
graph.addEdge("humanFeedback", "mailSend");   // 审核通过后发邮件
graph.addEdge("mailSend", END);

// 3. 编译时配置断点
CompileConfig.builder()
    .checkpointSaver(new MemorySaver())
    .interruptBefore("humanFeedback")  // 在审核节点前暂停
    .build();
```

**执行流程**：
1. 用户输入需求，流程启动
2. `ClassifierNode` 识别意图为"营销文案生成"
3. `LLMAnswerNode` 调用大模型生成文案草稿，写入状态
4. SSE 推送节点执行事件，前端展示文案草稿和审核界面
5. 流程自动暂停在 humanFeedback 前，checkpoint 持久化状态
6. 运营人员审核（通过/修改/拒绝），提交反馈
7. `resumeFlow(threadId, feedback)` 恢复执行
8. HumanFeedbackNode 读取反馈，审核通过则 MailSendNode 发送邮件
9. 流程结束，SSE 推送最终结果

---

### 第 4 题：高并发文档导入去重场景
**场景：** 多个用户同时上传同一份文档到知识库，如何防止重复入库？同时保证海量文档导入时缓存的可用性？

**参考答案：**

**文档去重（分布式锁）**：使用 Lock4j 注解 + Redisson 分布式锁：
```java
@Lock4j(keys = "#knowledgeBaseId + ':' + #fileName", expire = 60, acquireTimeout = 5)
public void importDocument(String knowledgeBaseId, String fileName) {
    // 同一 KB 同一文件名并发导入时全局互斥
    doImport(knowledgeBaseId, fileName);
}
```
锁 Key 设计遵循"最小互斥范围"原则：`lock:doc:import:{kbId}:{fileName}`，不同资源不互相影响。

**缓存防护**：
- 文档摘要、知识库列表等热点数据使用 `@Cacheable` 缓存
- 缓存未命中时使用 Redisson 互斥重建 + double-check 防击穿
- 缓存空值（短 TTL）防穿透
- TTL 加随机因子（300 + random(60)秒）防雪崩

**幂等设计**：文档入库前先检查文档指纹（MD5），如果已存在则直接返回已有结果，不重复处理。结合分布式锁，保证同一文档只被处理一次。

---

### 第 5 题：MCP 工具调用安全场景
**场景：** 用户通过 AI Agent 调用文件系统 MCP 工具时，恶意传入 `../../etc/passwd` 路径试图读取系统敏感文件。项目如何防止此类攻击？

**参考答案：**

项目从多个层面确保 MCP 工具调用的安全性：

**1. 路径安全检查（PathSecurityValidator）**：
- 路径规范化：使用 `Path.normalize().toAbsolutePath()` 解析 `..` 和 `.` 的相对路径
- 白名单校验：只允许访问配置的根目录（如 `D:/ruoyi-ai/data`、`D:/ruoyi-ai/temp`）
- 禁止模式匹配：正则匹配禁止访问的敏感路径

**2. 脚本执行安全（ScriptSecuritySandbox）**：
- 脚本大小限制：不超过 100KB
- 危险操作拦截：禁止 `import os`、`import subprocess`、`eval()`、`exec()` 等
- 白名单库：只允许 json、csv、pandas、numpy 等安全库
- 执行超时控制：默认 30 秒超时自动终止

**3. SSE 连接安全**：
- JWT 身份认证：只有经过认证的 Agent 才能建立 SSE 连接
- 同一连接的所有操作通过 `X-Session-Id` 追踪

**4. 运行时安全**：
- 操作审计日志：AOP 切面记录所有工具调用
- 速率限制：Guava RateLimiter 防止单个 Agent 过度调用
- 输出过滤：防止敏感信息泄露

---

## 五、深挖题（5 道）

### 第 1 题：LangChain4j 的 `@AiService` 动态代理 vs Spring 的 `@Service` AOP 代理，两者底层实现有何本质区别？如果我想在 `@AiService` 接口的方法上同时使用 `@Transactional` 事务注解，会生效吗？

**参考答案：**

**本质区别：**

| 维度 | @AiService（LangChain4j） | @Service（Spring AOP） |
|------|--------------------------|----------------------|
| 代理机制 | JDK 动态代理（`java.lang.reflect.Proxy`） | CGLIB（类代理）或 JDK Proxy（接口代理） |
| 代理创建者 | LangChain4j 的 `AiServices` Builder | Spring 容器（`AnnotationAwareAspectJAutoProxyCreator`） |
| 拦截逻辑 | 方法调用 → 解析注解 → 组装 ChatRequest → 调用 LLM | 方法调用 → 执行切面逻辑（事务、缓存等）→ 执行业务方法 |
| 代理对象管理 | 手动注册为 Spring Bean | 自动注册为 Spring Bean |

**`@Transactional` 兼容性分析：**

`@AiService` 标注的是接口，LangChain4j 通过 JDK Proxy 创建代理对象。问题在于：`@Transactional` 注解标注在接口方法上时，Spring 默认使用 JDK Proxy 代理，事务注解可以正常生效——前提是 `@AiService` 的代理和 Spring 的 AOP 代理是**同一个代理对象**。

但实际上，LangChain4j 创建的代理对象和 Spring 创建的 AOP 代理是两个独立的代理。LangChain4j 的代理注册为 Bean 后，Spring 的 AOP 会在其外层再包装一层代理（通过 `BeanPostProcessor`）。理论上事务注解可以生效，但存在两个风险：
1. 事务切面在 LLM 调用外层，而 LLM 调用是 I/O 密集型操作，事务可能持有数据库连接过长时间
2. `@AiService` 接口方法返回值可能是 `TokenStream`（流式），事务在流式响应完成前不会提交

**建议**：不在 `@AiService` 接口上使用 `@Transactional`，而是在调用 `@AiService` 的业务 Service 层管理事务，将 LLM 调用与 DB 操作分离。

---

### 第 2 题：工厂模式 vs 策略模式，在 ruoyi-ai 的多厂商模型接入和向量库接入中，为什么要同时使用两种模式？各自的职责边界在哪里？

**参考答案：**

**两种模式的分工：**

- **工厂模式（Factory Pattern）**：解决"怎么创建"的问题。封装对象创建的复杂性，调用方不需要知道具体实现类的构造参数和依赖
- **策略模式（Strategy Pattern）**：解决"怎么替换"的问题。定义统一接口，多个实现算法可互换，调用方面向接口编程

**在多厂商模型接入中的体现：**

- **工厂**：`ModelFactory` 接口的 `createModel(ModelConfig config)` 负责创建具体模型实例。`OpenAiModelFactory` 知道如何从 `ModelConfig` 中读取 apiKey、baseUrl、modelName 等参数，构建 `OpenAiChatModel`。调用方不需要关心 `OpenAiChatModel.builder()` 的细节
- **策略**：`ModelFactoryRegistry` 通过 `Map<String, ModelFactory>` 管理多个工厂，运行时根据 `activeProvider` 选择对应的工厂。切换模型厂商就是切换策略

**在向量库接入中的体现：**

- **策略**：`VectorStore` 接口 + Milvus/Weaviate/Qdrant 三个实现类，算法可替换
- **工厂**：`VectorStoreFactory` 封装了"从 Map 中按 type 取实例"的逻辑，调用方只需 `getDefaultStore(properties)` 即可

**为什么两者必须同时使用？**

单一模式无法覆盖全部需求：只用策略模式，业务层需要知道具体策略的创建方式（如 `new MilvusVectorStore(props)`）；只用工厂模式，所有实现类在工厂中创建，新增实现需要修改工厂代码。两者结合让"创建"和"替换"两个维度解耦，各自有独立的扩展点。

---

### 第 3 题：Bi-Encoder（向量检索）和 Cross-Encoder（Rerank）的本质区别是什么？为什么 RAG 管线不能只用 Cross-Encoder？

**参考答案：**

**本质区别：**

| 维度 | Bi-Encoder | Cross-Encoder |
|------|-----------|---------------|
| 编码方式 | 问题和文档分别编码为独立向量 | 问题和文档拼接后一起编码 |
| 计算方式 | 向量点积/余弦相似度 | 全连接层输出相关性分数 |
| 时间复杂度 | O(N) 线性 | O(N) 但每对都需要完整前向传播 |
| 预计算 | 文档向量可离线预计算 | 无法预计算，每次在线计算 |
| 精度 | 中等 | 高 |
| 适用场景 | 大规模候选集检索 | 小规模精排 |

**为什么不能只用 Cross-Encoder？**

假设知识库有 100 万条文档，用户提问后：
- 如果用 Cross-Encoder 直接对所有文档计算相关性：需要 100 万次完整 Transformer 前向传播，每次都是问题和文档的拼接序列，单次推理时间约 10-50ms，总耗时 10000-50000 秒，不可接受
- 如果用 Bi-Encoder：文档向量离线预计算，用户问题一次向量化，在向量库中 ANN 搜索百万级只需毫秒级，召回 Top-50 后 Cross-Encoder 只需 50 次推理

**正确组合**：Bi-Encoder 负责"宽进"（召回阶段，保证效率），Cross-Encoder 负责"严出"（精排阶段，保证精度）。这是工业级 RAG 系统的标准架构。

---

### 第 4 题：langgraph4j 的 StateGraph 与 Activiti/Camunda 等传统 BPMN 工作流引擎相比，核心区别是什么？在 AI 场景中有哪些不可替代的优势？

**参考答案：**

**核心区别：**

| 维度 | langgraph4j StateGraph | Activiti/Camunda BPMN |
|------|----------------------|----------------------|
| 图类型 | 有向图（支持循环） | 有向无环图（DAG） |
| 流程定义 | Java 代码声明式（addNode/addEdge） | XML 文件（BPMN 2.0 规范） |
| 运行时决策 | 条件边在运行时根据状态动态路由 | 网关条件在流程定义时已固定 |
| 状态管理 | AgentState（Map 黑板），Channel.Reducer 控制合并策略 | 流程变量（Map），无 Reducer 概念 |
| 节点类型 | 开发人员自定义 NodeAction | 预定义任务类型（UserTask/ServiceTask） |
| 部署方式 | 编译为 CompiledGraph，不可变 | 部署 BPMN 文件到引擎 |
| 面向人群 | 开发者 | 开发者 + 业务分析师 |

**AI 场景中的不可替代优势：**

1. **循环图支持**：ReAct 模式（Agent 思考 → 行动 → 观察 → 再思考）天然是循环，BPMN 不支持循环
2. **运行时动态路由**：下一步走哪里取决于 LLM 的输出，无法在流程设计时预知。StateGraph 的 `EdgeAction` 在运行时读取状态做决策
3. **Checkpoint 机制**：支持任意节点暂停/恢复，适合人机协作场景。BPMN 的暂停需要额外的流程实例管理
4. **流式执行**：`graph.stream()` 每步返回事件，配合 SSE 推送到前端。BPMN 通常只有同步/异步回调
5. **轻量无状态**：langgraph4j 是纯内存引擎（可选持久化），BPMN 引擎需要数据库支撑流程实例持久化，部署重

---

### 第 5 题：在 ruoyi-ai 的架构中，如果将 MCP 协议替换为传统的 REST API 调用，会损失哪些能力？MCP 的三大原语（Tools、Resources、Prompts）在项目中分别有什么体现？

**参考答案：**

**替换为 REST API 的损失：**

| 能力 | REST API 方案 | MCP 协议方案 | 损失 |
|------|-------------|-------------|------|
| 工具发现 | 需要手动阅读 API 文档，编写调用代码 | LLM 运行时通过 `list_tools` 自动发现 | 丧失自动化，新增工具必须修改 Agent 代码 |
| 工具描述 | 自然语言文档，LLM 无法自动理解 | JSON Schema 标准化描述，LLM 精确理解参数 | LLM 容易传错参数，工具调用准确率下降 |
| 动态扩展 | 新增工具需要修改代码 + 重新部署 | 启动新的 MCP Server，自动注册到 Agent | 丧失动态扩展能力 |
| 跨语言调用 | Java 进程直接调用 Python 脚本困难 | Python 脚本通过 MCP Server 暴露，语言无关 | 集成复杂度上升 |
| 连接管理 | 自行实现连接池、心跳、重试 | MCP Client 统一管理生命周期 | 重复造轮子，易出错 |

**MCP 三大原语在项目中的体现：**

1. **Tools（可执行操作）**：项目中最常用的原语。`FileSystemMcpServer` 注册了 `read_file`、`write_file`、`list_directory` 三个工具；`PythonScriptMcpServer` 注册了 `execute_python_script` 工具。每个工具通过 `McpTool.builder()` 定义名称、描述、输入 Schema（JSON Schema）和执行 Handler。LLM 通过 Function Calling 自动决策调用

2. **Resources（只读数据源）**：提供结构化数据，类似 REST API 的 GET 请求。虽然在 ruoyi-ai 的当前实现中 Resources 使用较少，但 MCP 协议支持将数据库查询结果、配置文件等暴露为只读资源，供 LLM 读取

3. **Prompts（提示模板）**：预定义的提示模板，帮助 LLM 更好地处理特定场景。例如在 Skills Agent 中，可以为"文档总结"、"数据提取"等场景预定义 Prompt 模板，Agent 在接收到相关任务时自动加载对应的 Prompt

**实际价值**：MCP 让 AI Agent 的工具生态从"硬编码"升级为"可插拔"。新增任何工具能力，只需启动一个 MCP Server 并注册到 Skills Agent，Agent 无需重新部署即可获得新能力。这是传统 REST API 集成方式无法比拟的。