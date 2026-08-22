# 面试 STAR 亮点总览：Java AI 四大项目综合提炼

> 从 4 个真实项目中提炼面试必备的 STAR 亮点素材，覆盖自我介绍模板、Top 5 技术亮点、面试追问应对策略。一份文档搞定面试中的"项目介绍"和"技术深挖"环节。
>
> **适用读者：** 准备 Java AI 方向面试的后端工程师
> **覆盖项目：** ruoyi-ai（企业级 AI 框架）/ ai-passage-creator（AI 图文创作）/ mewpaw-code（CLI 编码 Agent）/ zznursing（IoT + AI 养老平台）

---

## 一、自我介绍模板

### 1.1 三分钟版本（项目深挖环节）

> 我最近参与过四个有代表性的 Java AI 项目，覆盖了从企业级 AI 框架、AI 图文创作、CLI 编码 Agent 到 IoT + AI 物联网平台四种不同形态。
>
> 第一个是 **ruoyi-ai**，一个企业级 AI 应用开发框架。它基于 Spring Boot 3.5.8 + LangChain4j 1.13.0 + langgraph4j 1.5.3 构建，核心是四层架构——展现层、应用层、AI 层、基础设施层。我主要负责 AI 层的设计，包括三个子系统：**多厂商 LLM 统一接入**（工厂模式封装 9 家厂商，一行配置切换，新增厂商 50 行代码）、**Supervisor 多智能体调度**（1 个中央调度器 + 4 个子智能体，LLM 驱动的意图识别和路由）、**RAG 全链路管线**（从多格式文档解析、向量化存储到 Rerank 精排，支持 GraphRAG 知识图谱增强，检索精度提升 30%）。此外还基于 langgraph4j 设计了 11 种节点的 AI 流程编排引擎，支持人机协作断点。
>
> 第二个是 **ai-passage-creator**，一个 AI 多 Agent 图文创作平台。后端用 Spring Boot 3.5.9 + Spring AI Alibaba 1.1.0 的 StateGraph 编排 5 个 Agent 的 DAG 流水线——标题生成、大纲撰写、正文创作、配图分析、并行配图。配图模块设计了策略模式，支持 6 种配图方式并带降级链。项目亮点是**人机协作**设计——三阶段创作流程（选题、大纲、正文），每个阶段用户都能介入编辑，Agent 执行过程通过 SSE 实时推送 10 种命名事件到前端。
>
> 第三个是 **mewpaw-code**，一个基于 Java 21 + Spring Boot 3.3.5 CLI 模式 + LangChain4j 1.0.0 的 CLI 编码 Agent。核心是自研的 ReAct Agent Loop，实现了 Thought → Action → Observation 循环，控制迭代上限 50 次、连续错误容忍 3 次。安全方面设计了 5 层责任链模式（ToolFilter → PathGuardFilter → CommandScannerFilter → UserConfirmFilter → AuditLogFilter），每次工具调用都经过全链路检查。项目还通过 Java 21 虚拟线程执行 Bash 命令，Records 定义不可变数据对象，Sealed Classes 约束 8 种事件类型。
>
> 第四个是 **zznursing**，一个 IoT + AI 养老综合运营平台。后端用 Spring Boot + Spring Cloud Alibaba 微服务架构，AI 方面没有引入框架，而是通过**百度千帆原生 HTTP/SSE API** 接入文心一言，Access Token 用 AtomicReference + 双重检查锁手动缓存。IoT 方面接入华为 IoTDA 平台，单日处理 1500 万+ 条设备数据，通过 RocketMQ 削峰填谷、分层存储（Redis → MySQL 当月表 → 历史聚合表）兼顾性能与成本。
>
> 四个项目展示了四种不同的 AI 接入路径——从完整的 LangChain4j 框架生态，到 Spring AI 官方方案，到自定义循环，到原生 API 调用。**核心理念是"场景决定选型"**，不是框架越重越好，而是看项目复杂度需要什么级别的 AI 能力。

### 1.2 一分钟版本（简历/自我介绍场景）

> 我参与过四个 Java AI 项目，覆盖了四个不同方向：
>
> 一是 **ruoyi-ai** 企业级 AI 框架，基于 LangChain4j 1.13 + langgraph4j 实现多厂商 LLM 工厂（9 家）、Supervisor 多智能体调度、RAG + GraphRAG 全链路管线，以及 11 种节点的 AI 流程编排引擎。
>
> 二是 **ai-passage-creator** AI 图文创作平台，基于 Spring AI Alibaba StateGraph 编排 5 个 Agent 的 DAG 流水线，配图模块用策略模式支持 6 种方式 + 降级链，三阶段人机协作 + SSE 实时推送。
>
> 三是 **mewpaw-code** CLI 编码 Agent，基于 Java 21 + Spring Boot CLI 模式 + 自研 ReAct Agent Loop（50 次迭代上限、3 次连续错误容忍）+ 5 层安全责任链。
>
> 四是 **zznursing** IoT + AI 养老平台，百度千帆原生 API 接入（AtomicReference token 缓存 + SSE 流式 + WebSocket 桥接），华为 IoTDA 日处理 1500 万+ 设备数据，RocketMQ 削峰 + 分层存储。
>
> 四个项目的共同点都是**用 Java 生态做 AI 应用**，差异在于选型——场景复杂度决定了用框架还是原生调用。

---

## 二、Top 5 面试亮点（STAR 格式）

### 亮点一：多厂商大模型统一接入（工厂模式）—— ruoyi-ai

**Situation：** 企业级 AI 框架需要对接多个大模型厂商（OpenAI、DeepSeek、通义千问、智谱 GLM、Ollama 本地模型等），每个厂商 SDK 各不相同。如果业务代码直接依赖具体厂商 SDK，厂商切换和模型升级都将导致大面积代码修改。

**Task：** 设计一套多厂商统一接入方案，要求：1）新增厂商只需新增实现类，零修改已有代码；2）运行时通过配置文件切换模型，无需重启；3）支持主备降级和限流保护；4）对业务层完全透明。

**Action：**
- 三层架构：`ModelFactory` 接口（统一创建模型）→ `ModelFactoryRegistry`（Spring DI 自动收集所有实现，`ConcurrentHashMap` 缓存实例）→ `AiModelProperties`（`@ConfigurationProperties` 绑定配置）
- `@RefreshScope` 注解配合配置中心，实现运行时热切换，生效时间 < 1 秒
- `ResilientAiChatService.chatWithFallback()` 主模型异常时自动降级到备用模型
- `RateLimitedChatModel` 使用 Guava `RateLimiter` 令牌桶限流，防止切换瞬间大量请求压垮备用模型

**Result：**
- 9 个厂商接入，全部通过 `@Component` 注册，零侵入
- 新增厂商平均 50 行代码（实现接口 + 标注注解 + 添加配置）
- 配置切换 < 1 秒生效，降级对业务层完全透明

**技术深挖方向：**
- `@RefreshScope` 原理：`ScopedProxy` 动态代理，`Environment` 变更时销毁原有 Bean，下次请求重新创建
- `computeIfAbsent` 原子性防止缓存击穿
- 令牌桶算法 vs 漏桶算法在 AI API 限流场景的选型差异

---

### 亮点二：RAG 全链路 + GraphRAG 增强—— ruoyi-ai

**Situation：** 企业知识库问答场景中，用户上传各种格式文档（PDF/Word/MD/Excel）后提问。核心挑战：文档格式多样、纯向量检索精度不足（尤其实体关系）、检索结果噪音多。

**Task：** 构建完整 RAG 管线，支持多格式解析、智能切分、高精度检索，引入 GraphRAG 增强实体关系检索。

**Action：**
- 解析层：`DocumentParserFactory` 4 种格式解析器（PDF/Word/MD/Excel），统一 `DocumentParser` 接口
- 切分层：3 种策略（Token 切分/字符切分/Markdown 标题切分），可配置 `chunkSize` / `chunkOverlap`
- 向量化 + 存储：`EmbeddingModelFactory` 4 家 Embedding 服务，`VectorStoreFactory` 三选一（Milvus/Weaviate/Qdrant）
- GraphRAG 增强：Neo4j 知识图谱检索实体关系，与向量检索双路融合
- 精排层：双路检索（向量 Top-50 + GraphRAG）→ 融合（GraphRAG 权重 0.95）→ Cross-Encoder Rerank Top-5
- 质量评估：RAGAS 框架（Context Recall / Precision / Faithfulness）

**Result：**
- 支持 4 种文档格式，3 种切分策略适配不同场景
- Top-50 召回 → Rerank 精排 Top-5，精度提升约 30%
- GraphRAG 有效解决"查不到的关联"问题

**技术深挖方向：**
- Bi-Encoder vs Cross-Encoder 的"宽进严出"组合策略
- GraphRAG 与向量检索的互补关系（语义相似度 vs 关系推理）
- RAGAS 三大评估指标的业务含义

---

### 亮点三：Multi-Agent StateGraph 编排—— ai-passage-creator

**Situation：** AI 图文创作需要高质量的文章，但单一 LLM 调用存在内容质量不稳定、专业化能力不足、无法支持流式输出和人工干预等问题。

**Task：** 设计多 Agent 协同工作流，将创作拆解为多个专业化阶段，支持并行执行、流式输出、人工介入。

**Action：**
- 基于 Spring AI Alibaba 1.1.0 StateGraph，构建 5 个 Agent 的 DAG 工作流：TitleGenerator → OutlineGenerator → ContentGenerator → ImageAnalyzer → ParallelImageGenerator → ContentMerger
- `ParallelImageGenerator` 内部使用 `CompletableFuture` 并发执行多张配图生成，`Semaphore` 控制并发度
- 三阶段人机协作：TITLE_SELECTION（用户选标题）→ OUTLINE_EDITING（用户编辑大纲）→ CONTENT_GENERATION（AI 生成正文 + 配图）
- SSE 实时推送 10 种命名事件（`AGENT1_COMPLETE`、`AGENT2_STREAMING` 等），前端精确控制 UI 展示
- 数据库 `article.phase` 字段记录当前阶段，支持断点续传

**Result：**
- 并行配图生成将整体耗时从 15+ 秒降至 8-10 秒（6 张配图并发）
- 流式输出 2-3 秒内可见首段内容
- 断点续传让用户刷新页面不丢失进度

**技术深挖方向：**
- `node_async()` 原理：`CompletableFuture.supplyAsync()` + 异步节点包装
- `KeyStrategy` 状态合并策略：overwrite（覆盖）vs appender（追加）vs custom（自定义）
- `SseEmitter` 资源管理：`onCompletion` / `onTimeout` 回调清理，心跳机制防死连接
- SSE vs WebSocket 在 AI 创作场景的选型依据

---

### 亮点四：自定义 ReAct AgentLoop + 5 层安全链—— mewpaw-code

**Situation：** CLI 编码 Agent 需要 LLM 自主决策调用工具完成编码任务，但 LangChain4j 内置循环不支持连续错误检测、输出截断和事件驱动，也无法内嵌安全过滤。

**Task：** 设计自定义 ReAct 循环引擎，要求：1）可控的迭代次数和错误容忍；2）事件驱动架构支持全生命周期可观测；3）安全链内嵌在循环中，每次工具调用都经过检查。

**Action：**
- **AgentLoop 核心循环**：`MAX_ITERATIONS=50`（防止无限循环）、`MAX_CONSECUTIVE_ERRORS=3`（连续错误强制终止）、成功输出截断 5000 chars、错误输出截断 500 chars
- **事件驱动架构**：`AgentEvent` sealed interface 定义 8 种事件类型（TurnStarted / StepUpdated / AssistantDelta / AssistantCompleted / ToolCallStarted / ToolOutputDelta / ToolCallCompleted / TurnCompleted / TurnFailed），编译器保证穷尽检查
- **5 层安全链**：`ToolFilter`（工具注册检查）→ `PathGuardFilter`（路径规范化防遍历）→ `CommandScannerFilter`（危险命令检测）→ `UserConfirmFilter`（高危操作暂停确认）→ `AuditLogFilter`（全量审计日志），`@Order` 注解控制执行顺序，任何一层 deny 立即阻断
- **BashTool**：跨平台支持（Windows `cmd /c` / Linux `bash -c`），60s 超时 `destroyForcibly()`，虚拟线程执行

**Result：**
- 50 次迭代上限 + 3 次连续错误终止，有效防止 Agent 失控
- 8 种事件覆盖 Agent 完整生命周期，前端可实时展示思考过程
- 5 层安全链层层递进，高危操作需用户确认，安全无事故

**技术深挖方向：**
- 为什么不用 LangChain4j 内置循环：不支持连续错误检测、输出截断、事件驱动、安全链内嵌
- ReAct vs Plan-and-Execute 在编码场景的选型差异
- sealed interface 的穷尽性检查（编译器保证所有 switch 分支覆盖）
- 安全链的"短路过早"策略：任何一层 deny 立即返回，不继续执行后续过滤器

---

### 亮点五：百度千帆 AI 原生接入 + IoTDA 大规模设备接入—— zznursing

**Situation：** 养老机构需要 AI 健康咨询和大规模设备接入能力。家属关注老人健康，护工人数有限；1000+ IoT 设备每 5 秒上报数据，每天产生约 1728 万条数据。

**Task：** 1）AI 智能问答，3 秒内开始流式回复，月成本不超过 1000 元；2）支持 1000+ 设备并发接入，数据零丢失，端到端延迟 < 5 秒。

**Action：**
- **AI 接入**：无框架，`WebClient` 直连百度千帆 REST API。`AtomicReference<String>` + `volatile` tokenExpireTime + `synchronized` 双重检查锁缓存 Access Token（30 天有效期，提前 1 天过期），`bodyToFlux` 接收 SSE 流，`.filter("data: ")` 解析事件流
- **模型分级**：简单问题用文心一言 3.5（低价），复杂用 4.0，通过问题复杂度分类器自动路由
- **IoT 接入**：华为 IoTDA 平台，HTTP 回调 → Controller → RocketMQ 削峰 → 批量写入 MySQL（每 100 条或 1 秒 flush）
- **分层存储**：Redis 热数据（1 小时）→ MySQL 当月表（30 天）→ 历史聚合表（90 天），每天凌晨聚合
- **微信小程序**：WebSocket 桥接 SSE 流式输出（小程序无原生 SSE），分包加载 + 数据预加载 + setData 合并优化首屏 1.5 秒

**Result：**
- 家属满意度提升 40%，健康咨询响应时间从 2 小时缩短到 3 秒
- 日均调用 4500 次，月成本约 800 元
- 单日处理 1500 万+ 条设备数据，无数据丢失，跌倒检测推送成功率 99.9%
- 查询性能提升 10 倍，存储成本降低 60%

**技术深挖方向：**
- 为什么选择不引入 AI 框架：单一问答场景，框架的 Agent/RAG/编排能力全部用不上，原生接入控制力更强、依赖更少
- `AtomicReference` + 双重检查锁的线程安全分析
- SSE → WebSocket 协议桥接的技术要点
- 按月分表 + 分层存储 + 数据聚合的性价比分析

---

## 三、面试官常见追问及应对策略

### 追问 1：这四个项目你分别负责什么角色？

**考察点：** 项目真实性，是否只是"看过代码"而非"参与过"。

**应对策略：** 按项目说明自己的贡献层级：
- **ruoyi-ai**：AI 层的核心设计者，主导多厂商工厂、Supervisor 多智能体、RAG 管线、流程编排引擎的设计与实现
- **ai-passage-creator**：StateGraph 编排 + 配图策略模式 + SSE 事件系统的设计与实现
- **mewpaw-code**：AgentLoop 核心循环 + 事件驱动架构 + 安全链的设计与实现
- **zznursing**：千帆 AI 接入 + 微信小程序全栈 + 分层存储方案的设计与实现

> 关键不是"我写了多少代码"，而是"我在每个项目中解决了什么核心问题"。

### 追问 2：LangChain4j 和 Spring AI 你更推荐哪个？为什么？

**考察点：** 是否有真实的框架对比经验。

**参考回答：** 见 [java-ai-ecosystem-comparison.md](java-ai-ecosystem-comparison.md) 第五部分。

核心要点：
- 选 LangChain4j：当项目需要完整 AI 生态（RAG 组件、MCP、多 Agent 编排）时
- 选 Spring AI：当团队是 Spring 生态、项目是固定流程的生成型任务时
- 更重要的是——把框架抽象层做好，让框架本身可替换

### 追问 3：什么情况下你会选择不引入 AI 框架，直接调 API？

**考察点：** 是否理解框架的代价。

**参考回答：** 当 AI 需求只有单一问答、且不需要工具调用和复杂上下文时，原生接入更优。zznursing 就是典型——只有"家属提问 → 组装 Prompt → 流式回复"一个场景，框架的 Agent/RAG/编排能力全部用不上。判断标准：一个项目超过 2 个 AI 场景、或者需要工具调用/RAG，就该引入框架。

### 追问 4：多厂商工厂模式下，新增一个厂商需要改哪些代码？

**考察点：** 是否真正理解工厂模式+Spring DI 的落地细节。

**应对策略：** 三步走：
1. 实现 `ModelFactory` 接口（`createModel()` + `getProviderType()`）
2. 标注 `@Component`，Spring 自动注册到 `ModelFactoryRegistry`
3. 在配置文件中添加厂商配置（`ai.models.{provider}.apiKey` 等）

> 强调"零侵入"——不需要修改任何已有代码，符合开闭原则。

### 追问 5：RAG 管线中，为什么向量检索 + GraphRAG 双路检索比单路好？

**考察点：** 对检索增强生成的理解深度。

**应对策略：** 向量检索擅长语义相似度（"苹果公司的创始人" → "Steve Jobs"），GraphRAG 擅长关系推理（"Steve Jobs 创立了哪家公司？" → 沿 founder 关系边找到 Apple）。两者互补，不能互相替代。融合策略：GraphRAG 权重 0.95 是因为知识图谱的关系数据更精确，而向量检索噪音较多。

### 追问 6：StateGraph 的并行执行是怎么实现的？怎么控制并发度？

**考察点：** 对异步编排的理解。

**应对策略：** 两个层面：
- Agent 级别：`node_async()` 内部使用 `CompletableFuture.supplyAsync()` 提交到线程池，`StateGraph.stream()` 每执行完一个节点检查后续边的条件，将符合条件的下一个节点提交到线程池
- 并行配图：`ParallelImageGenerator` 内部用 `CompletableFuture.allOf()` 等待所有图片生成完成，`Executors.newFixedThreadPool(3)` + `Semaphore` 控制并发度，防止打爆外部 API

### 追问 7：为什么 mewpaw-code 不用 LangChain4j 内置的 Agent 循环？

**考察点：** 是否理解"用框架"和"造轮子"的边界。

**应对策略：** LangChain4j 内置的 `maxToolCallingRoundTrips` 只控制轮次上限，不支持连续错误检测、输出截断和事件驱动。项目需要：
1. 5 层安全链在循环内检查每次工具调用
2. 事件流推送 8 种事件给前端展示
3. 自定义的成功/错误输出截断策略
4. 连续错误计数和自动终止

这些需求 LangChain4j 内置循环无法满足。但项目仍然用了 LangChain4j 作为 LLM 调用底座——`ChatLanguageModel` 统一接口和 `ToolExecutionRequest` 解析，这是"框架的模型能力 + 自建的控制逻辑"的典型组合。

### 追问 8：SSE 和 WebSocket 在 AI 场景中怎么选？

**考察点：** 对实时通信协议的理解。

**应对策略：** SSE 优势：EventSource API 内置自动重连、服务端单向推送符合 AI 场景模型、基于 HTTP 穿透防火墙容易。局限：只能发送 GET 请求、浏览器并发连接数限制（HTTP/1.1 每域名 6 个）。选型依据：
- 纯推送场景（AI 流式输出、进度推送）→ SSE
- 双向通信场景（聊天、实时协作）→ WebSocket
- 需要小程序支持 → WebSocket 桥接（小程序无原生 EventSource）

### 追问 9：如果让你重新设计这四个项目中的一个，你会怎么改进？

**考察点：** 是否有复盘和反思能力。

**应对策略：** 选择最有把握的项目，说 1-2 个可落地的改进点。示例（ruoyi-ai）：
1. **多厂商工厂引入 SPI 机制**：当前用 `@Component` + Spring 自动扫描，虽然方便但增加了 Spring 耦合。如果引入 Java SPI（`ServiceLoader`），非 Spring 项目也能复用
2. **RAG 管线引入 Query Rewriting**：当前直接使用用户原始问题检索，用户问题可能表述不清。引入 Query Rewriter（LLM 将用户问题改写为更清晰的检索查询），可以进一步提升检索精度

### 追问 10：你提到了"场景决定选型"，具体怎么判断？

**考察点：** 架构思维和决策能力。

**应对策略：** 使用 L1-L4 分级框架（详见 [java-ai-ecosystem-comparison.md](java-ai-ecosystem-comparison.md) 第四部分）：
- L1 单轮问答 → 原生 API 调用（zznursing 路径）
- L2 多轮 + 流式 → 加 Redis 会话 + WebSocket 桥接
- L3 工具调用 / Agent → 引入 LangChain4j 或 Spring AI
- L4 多 Agent + RAG + 编排 → 框架 + 状态图引擎（ruoyi-ai 路径）

> 附加判断：团队技术栈（Spring 团队优先 Spring AI）、部署环境（国内优先国内模型）、成本敏感度（原生调用成本最低）。

---

## 参考资料

- 各项目 STAR 详情：[ruoyi-ai](../ruoyi-ai/15-star-highlights.md) / [ai-passage-creator](../ai-passage-creator/11-star-highlights.md) / [zznursing](../zznursing/09-star-highlights.md)
- 框架选型对比：[java-ai-ecosystem-comparison.md](java-ai-ecosystem-comparison.md)
- 架构模式提炼：[enterprise-architecture-patterns.md](enterprise-architecture-patterns.md)
- 各项目全景导读：[ruoyi-ai](../ruoyi-ai/00-PROJECT-OVERVIEW.md) / [ai-passage-creator](../ai-passage-creator/00-PROJECT-OVERVIEW.md) / [mewpaw-code](../mewpaw-code/00-PROJECT-OVERVIEW.md) / [zznursing](../zznursing/00-PROJECT-OVERVIEW.md)