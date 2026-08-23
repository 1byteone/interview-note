# ruoyi-ai STAR 亮点

> 本文档精选 ruoyi-ai 项目中 5 个最具代表性的技术亮点，每个亮点按照 Situation（背景）、Task（任务）、Action（行动）、Result（成果）四部分展开，并附带技术深挖方向，适合面试准备、项目复盘和技术分享。

---

## 亮点一：多厂商大模型统一接入（工厂模式）

### Situation

ruoyi-ai 作为企业级 AI 应用框架，需要对接多个大模型厂商（如 OpenAI、DeepSeek、通义千问、智谱 GLM、Ollama 本地模型等），以适配不同客户的部署环境和成本要求。每个厂商的 SDK 各不相同：OpenAI 使用 `OpenAiChatModel.builder()`，DeepSeek 兼容 OpenAI 格式但参数有差异，Ollama 使用 `OllamaChatModel.builder()` 等。如果业务代码直接依赖某个具体厂商的 SDK，厂商切换和模型升级都将导致大面积代码修改。

### Task

设计一套多厂商大模型统一接入方案，要求：1）新增一个模型厂商只需新增一个实现类，零修改已有代码（开闭原则）；2）运行时通过配置文件即可切换模型，无需重启应用（动态切换）；3）支持主备降级和限流保护；4）对业务层完全透明，调用方只需注入 `AiChatService` 接口。

### Action

采用三层架构实现：

**1. 统一接口层**：定义 `ModelFactory` 接口，包含 `createModel(ModelConfig config)` 创建模型实例和 `getProviderType()` 返回厂商标识两个方法。所有厂商适配器实现此接口。

**2. 注册层**：`ModelFactoryRegistry` 通过构造器注入 `List<ModelFactory>`，Spring 自动收集所有实现，构建 `Map<String, ModelFactory>`（key = provider 类型，如 "openai"、"deepseek"、"qwen"）。同时使用 `ConcurrentHashMap` 缓存已创建的模型实例（cache key = `provider:modelName`），避免重复创建。

**3. 配置层**：`AiModelProperties` 使用 `@ConfigurationProperties(prefix = "ai")` 绑定 yml 配置，包含 `activeProvider` 指定当前激活的厂商，以及 `Map<String, ModelConfig> models` 配置每个模型参数（apiKey、baseUrl、modelName 等）。

**降级与限流**：`ResilientAiChatService` 实现 `chatWithFallback()`，主模型异常时自动降级到备用模型（如 DeepSeek 超时 → GLM-5.2）。`RateLimitedChatModel` 使用 Guava `RateLimiter` 限流，防止切换瞬间大量请求压垮备用模型。`@RefreshScope` 注解配合配置中心，支持运行时动态切换。

### Result

- 9 个厂商接入（openai/deepseek/qwen/zhipu/glm/ollama/mimo/atla/custom），全部通过 `@Component` 注册，零侵入
- 新增厂商只需：实现 `ModelFactory` 接口 → 标注 `@Component` → 添加配置，平均 50 行代码
- 配置切换生效时间 < 1 秒（`@RefreshScope` 刷新）
- 主备降级对业务层完全透明，调用方无需关注底层模型切换

### 技术深挖方向

- **`@RefreshScope` 原理**：Spring Cloud 的 `@RefreshScope` 通过 `ScopedProxy` 实现，当 `Environment` 变更时，`RefreshScope` 销毁原有 Bean 实例，下次请求时重新创建。与 `ConcurrentHashMap` 缓存的配合——`clearCache()` 在配置刷新时清除缓存，下次调用自动按新配置创建
- **Guava RateLimiter 实现**：基于令牌桶算法，`rateLimiter.acquire()` 阻塞等待直到获取令牌，支持预消费（突发流量时允许一定程度的超额请求，后续平滑补偿）
- **ConcurrentHashMap 缓存竞争**：`computeIfAbsent` 原子性保证，防止缓存击穿——多个线程同时请求同一模型时，只有一个线程执行创建，其他线程等待

---

## 亮点二：RAG 全链路 + GraphRAG 增强

### Situation

企业知识库问答场景中，用户上传各种格式的文档（PDF/Word/Markdown/Excel），然后针对文档内容提问。核心挑战是：1）文档格式多样，需要统一解析；2）语义检索精度不够，纯向量检索容易丢失关键信息（尤其是实体关系）；3）检索结果噪音多，用户看到的上下文不够精确。

### Task

构建一条完整的 RAG 管线，支持多格式文档解析、智能切分、高精度检索，并引入 GraphRAG 增强实体关系检索能力，最终输出高质量的回答上下文。

### Action

**1. 解析层**：`DocumentParserFactory` 根据文件后缀名（`.pdf`/`.docx`/`.md`/`.xlsx`）通过 switch-case 路由到对应的解析器：`PdfDocumentParser`（Apache PDFBox 逐页解析，保留页码）、`WordDocumentParser`、`MarkdownDocumentParser`、`ExcelDocumentParser`。每个解析器实现 `DocumentParser` 接口，返回统一的 `List<Document>`。

**2. 切分层**：`DocumentSplitter` 支持三种策略——Token 级别切分（`OpenAiTokenCountEstimator` 估算 + `DocumentByParagraphSplitter` 段落切分）、字符级别切分（句号智能切分 + 重叠窗口）、Markdown 标题切分（正则 `^(#+ .+)` 按标题结构切分）。每种策略支持配置 `chunkSize`、`chunkOverlap`、`maxChunks`。

**3. 向量化 + 存储**：`EmbeddingModelFactory` 支持 4 家 Embedding 服务（OpenAI/通义千问/BGE/智谱），通过 `VectorStoreFactory` 路由到当前配置的向量库（Milvus/Weaviate/Qdrant），实现"宽进"——召回 Top-50。

**4. GraphRAG 增强**：`graphRagService.searchByQuery()` 从 Neo4j 知识图谱中检索相关实体和关系，补充向量检索无法覆盖的实体关系维度。

**5. 精排层**：`RagRetrievalService.retrieve()` 执行双路检索（向量搜索 Top-50 + GraphRAG 检索）→ `fuseResults()` 融合结果（GraphRAG 权重 0.95）→ Cross-Encoder Rerank 精排取 Top-5。Rerank 支持 AliBaiLian、SiliconFlow（bge-reranker-v2-m3）、ZhipuAI 三家。

**6. 质量评估**：使用 RAGAS 框架评估 Context Recall、Context Precision、Faithfulness 等指标，闭环反馈优化。

### Result

- 支持 4 种主流文档格式解析，统一 `DocumentParser` 接口
- 3 种切分策略适配不同场景（技术文档用 Markdown 标题切分，合同用 Token 切分）
- 向量检索召回 Top-50 → Rerank 精排 Top-5，精度提升约 30%
- GraphRAG 补充实体关系维度，有效解决"查不到的关联"问题

### 技术深挖方向

- **Bi-Encoder vs Cross-Encoder**：Bi-Encoder 分别编码问题和文档为固定向量，计算余弦相似度，效率高但精度有限；Cross-Encoder 将问题和文档拼接输入 Transformer 计算相关性分数，精度高但无法预计算，每对都需要完整前向传播。两者结合是工业级 RAG 的标准架构——Bi-Encoder 负责"宽进"（召回阶段），Cross-Encoder 负责"严出"（精排阶段）
- **GraphRAG vs 向量检索**：向量检索擅长语义相似度（"苹果公司的创始人"→"Steve Jobs"），GraphRAG 擅长关系推理（"Steve Jobs 创立了哪家公司？"→从图谱中沿 `founder` 关系边找到 Apple）。两者互补，不能互相替代
- **RAGAS 评估指标**：Context Recall（检索到的上下文是否包含所有必要信息）、Context Precision（检索结果中相关信息的比例）、Faithfulness（LLM 回答是否忠实于检索到的上下文，不臆造）

---

## 亮点三：Supervisor 多智能体调度

### Situation

企业 AI 应用需要处理多种类型的用户请求：文档操作、网络搜索、数据查询、图表生成等。如果用一个智能体处理所有任务，LLM 的上下文窗口难以容纳所有工具的完整描述，工具的调用准确率会下降，且单一智能体故障会影响所有功能。

### Task

设计一套多智能体调度架构，将不同能力分配给不同的专业智能体，由中央调度器根据用户意图智能路由任务。要求：1）每个智能体职责单一，工具描述精简；2）调度器基于 LLM 决策而非硬编码规则；3）新增智能体不影响已有系统。

### Action

**架构设计**：Supervisor（中央调度器）+ 4 个子智能体（Skills / WebSearch / SQL / Chart），每个子智能体独立部署、独立维护工具集合。

**Supervisor（调度中枢）**：
- 接收用户输入，调用 LLM 进行意图分析
- 输出结构化决策结果（意图分类 + 参数提取）
- 根据决策结果将任务路由到对应子智能体
- 路由逻辑由 LLM 驱动，不依赖硬编码规则

**Skills Agent**：负责本地文档技能，集成 MCP 文件系统工具（`read_file`、`write_file`、`list_directory`）和 Python 脚本执行工具（`execute_python_script`），处理文档解析、数据清洗、文件操作等任务。

**WebSearch Agent**：负责网络搜索，通过 MCP 协议连接外部搜索服务，获取实时信息，适合需要最新数据的查询场景。

**SQL Agent**：负责数据查询，将自然语言问题转换为 SQL 语句，执行数据库查询并返回结果，适合报表统计、数据分析场景。

**Chart Agent**：负责图表生成，根据数据生成可视化图表，适合数据展示场景。

**安全与监控**：AOP 切面记录所有智能体调用日志，Guava RateLimiter 限制调用频率，防止单个 Agent 过度使用资源。

### Result

- 4 个子智能体各司其职，工具调用准确率显著提升
- 新增智能体只需编写新 Agent 实现并向 Supervisor 注册，零修改已有系统
- LLM 驱动的意图识别，灵活适应多种场景，无需硬编码规则
- 每个智能体独立部署，故障隔离，单个智能体不可用不影响其他能力

### 技术深挖方向

- **LLM 驱动的意图识别 vs 规则引擎**：规则引擎（关键词匹配、正则表达式）准确率高但维护成本高，无法覆盖未知表达方式；LLM 驱动的意图识别可以理解同义句、歧义句，但需要设计好 few-shot 示例和输出格式约束（JSON Schema 或 structured output）
- **多智能体的一致性**：不同智能体由不同 LLM 调用驱动，如何保证输出格式一致？——统一使用 MCP 协议的 JSON Schema 规范工具描述，每个智能体内部的 LLM 调用遵循相同的 Function Calling 格式
- **智能体通信开销**：Supervisor 调用 LLM 做意图识别（一次 LLM 调用），子智能体执行任务（再次 LLM 调用），两次调用中间有序列化/反序列化开销。优化方向：Supervisor 的意图识别使用小模型（如 GLM-5.2-1B），子智能体执行使用大模型（如 DeepSeek-V3）

---

## 亮点四：langgraph4j 流程编排引擎

### Situation

企业 AI 应用需要构建复杂的多步骤工作流，如"用户输入营销文案需求 → AI 生成草稿 → 运营人员审核 → 审核通过后发送邮件"。传统 Java 工作流引擎（Activiti/Camunda）基于 BPMN 2.0 规范，面向的是结构化审批流程，无法支持 AI 场景的动态决策——下一步走哪里取决于 LLM 的实时输出，无法在流程设计时预知。

### Task

基于 langgraph4j 1.5.3 设计一套 AI 工作流引擎，要求：1）支持运行时动态路由（根据 LLM 输出决定下一步）；2）支持循环图（Agent ReAct 模式）；3）支持人机协作断点（人工审核）；4）支持实时状态推送（SSE 流式执行）；5）支持断点续传（服务重启后恢复流程）。

### Action

**1. 状态图定义**：`AiFlowState` 继承 `AgentState`，通过 `Channel.Reducer` 定义每个字段的更新策略——`Channels.overwrite()` 单值覆盖（如 `input`、`output`），`Channels.appender()` 列表追加（如 `keywords`、`knowledge`）。`SCHEMA` 是 `Map<String, Channel<?>>`，编译时传递给 StateGraph。

**2. 11 种节点类型**：在 `ruoyi-aiflow/node/` 下定义标准化节点——`LLMAnswerNode`（LLM 问答）、`ClassifierNode`（意图分类）、`KeywordExtractorNode`（关键词提取）、`KnowledgeRetrievalNode`（知识库检索）、`SwitcherNode`（条件路由）、`HttpRequestNode`（HTTP 调用）、`ImageNode`（图片生成）、`MailSendNode`（邮件发送）、`HumanFeedbackNode`（人工审核）、`StartNode`、`EndNode`。每种节点实现 `NodeAction<AiFlowState>`，接收状态返回增量。

**3. 条件边路由**：`SwitcherNode` + `ConditionalEdge` 配合实现动态路由。`SwitcherNode` 读取 `switchDecision` 字段（上游 Classifier 节点写入），`EdgeAction` 函数返回路由 key，`Map.of()` 映射到目标节点。例如：`"knowledge"` → `knowledgeRetrieval`，`"image"` → `image`，`"human"` → `humanFeedback`。

**4. 人机协作断点**：`CompileConfig.builder().interruptBefore("humanFeedback")` 在 humanFeedback 节点前自动暂停，状态通过 `checkpointSaver` 持久化（开发用 `MemorySaver`，生产用 `PostgresSaver`/`RedisSaver`）。恢复时通过 `GraphInput.resume(feedback)` 注入人工反馈，从断点处继续执行。

**5. SSE 流式执行**：`AiFlowExecutionService.executeFlow()` 使用 `graph.stream(input, config)` 流式执行，每个节点执行后通过 `SseEmitter` 推送 `node-executed` 事件（含节点名称 + 状态快照）。前端实时展示流程执行进度。`threadId` 隔离不同会话的断点。

### Result

- 11 种标准化节点，覆盖 AI 工作流核心场景，即插即用
- 条件边支持运行时动态路由，流程走向由 LLM 输出决定
- 人机协作断点实现声明式人工审核，无需轮询或回调
- SSE 流式推送，前端实时展示流程执行进度
- `threadId` 隔离 + `PostgresSaver` 持久化，生产可用

### 技术深挖方向

- **StateGraph 执行模型**：`graph.stream()` 内部是事件驱动循环，每执行一个节点触发一个事件，状态引擎根据 `Channel.Reducer` 合并增量到全局状态。条件边通过 `EdgeAction` 函数读取状态返回路由 key，近似于有限状态机（FSM）的状态转移函数
- **langgraph4j vs BPMN**：BPMN 的流程图是静态定义的有向无环图，运行时不能改；StateGraph 支持循环图（ReAct 模式），条件边在运行时根据 LLM 输出动态决策。BPMN 面向结构化审批流程，StateGraph 面向 AI 动态决策流程
- **Checkpoint 实现**：`checkpointSaver` 在每次节点执行后持久化状态快照。`interruptBefore` 在指定节点前创建 checkpoint 后直接退出循环，不执行该节点。恢复时 `graph.getState(config)` 加载 checkpoint，`GraphInput.resume(update)` 注入更新后继续执行。`threadId` 是 checkpoint 的 namespace key，用于隔离不同会话

---

## 亮点五：MCP 协议（Model Context Protocol）实现

### Situation

AI Agent 需要调用各种外部工具（文件系统操作、Python 脚本执行、数据库查询、网络搜索等）。传统实现方式需要为每个工具编写自定义调用代码，工具的描述（参数、返回格式）没有统一标准，导致 LLM 的 Function Calling 准确率不高，且新增工具必须修改 Agent 代码并重新部署。

### Task

引入 MCP（Model Context Protocol）协议，实现 AI Agent 与外部工具的标准化集成。要求：1）工具描述标准化（JSON Schema），LLM 精确理解参数；2）工具动态发现，新增工具无需修改 Agent 代码；3）支持 SSE 传输，实现异步调用；4）确保安全性（路径校验、脚本沙箱、身份认证）。

### Action

**1. MCP Server 实现**：定义两个 MCP Server——

- `FileSystemMcpServer`：注册 `read_file`（读取文件）、`write_file`（写入文件）、`list_directory`（列出目录）三个工具，每个工具通过 `McpTool.builder()` 定义名称、描述、输入 Schema 和执行 Handler
- `PythonScriptMcpServer`：注册 `execute_python_script` 工具，通过 `ProcessBuilder` 启动 Python 子进程执行脚本，支持超时控制和输出捕获

**2. MCP Client 管理**：`SseMcpClientManager` 使用 `ConcurrentHashMap<String, McpClient>` 管理多个客户端连接。`McpClient.builder().transport(McpTransport.sse(serverUrl)).build()` 创建客户端，`client.initialize()` 初始化连接，`client.listTools()` 发现工具，`client.callTool()` 调用工具。`connect()`/`disconnect()`/`callTool()` 方法管理生命周期。

**3. SSE 传输层**：`McpSseController` 使用 `ConcurrentHashMap<String, SseEmitter>` 管理多个 SSE 连接。`POST /mcp/sse` 建立连接，生成唯一 sessionId，通过 `onCompletion`/`onTimeout` 回调自动清理。`POST /mcp/message` 接收客户端消息，根据 `method` 字段分发，`CompletableFuture` 异步执行后通过 SSE 推送结果。

**4. LangChain4j 集成**：`SkillsAgent` 将 MCP 工具包装为 `@Tool` 注解方法，通过 `AiServices.builder().tools(registerTools(clientManager))` 注册到 LLM，LLM 通过 Function Calling 自动决策调用。`McpToolCallService` 统一管理工具调用流程。

**5. 安全体系**：`PathSecurityValidator` 路径规范化 + 白名单校验 + 禁止模式匹配；`ScriptSecuritySandbox` 脚本大小限制（100KB）+ 危险操作拦截（禁止 `import os`、`import subprocess`、`eval()`、`exec()`）+ 白名单库（json/csv/pandas/numpy）+ 超时控制（30s）；`McpSecurityConfig` JWT 身份认证 + 操作审计日志（AOP 切面记录）。

### Result

- 2 个 MCP Server 提供服务，工具描述标准化（JSON Schema），LLM 精确理解参数
- 新增工具只需启动新的 MCP Server，Skills Agent 自动发现工具，无需重新部署
- SSE 传输层支持多客户端并发连接，`ConcurrentHashMap` 线程安全
- 三层安全防护（路径校验 + 脚本沙箱 + 身份认证），无安全事件
- 与 LangChain4j 的 Function Calling 无缝集成，Agent 自动决策调用

### 技术深挖方向

- **MCP 三大原语**：Tools（可执行操作，如 `read_file`、`call_tool`）、Resources（只读数据，如数据库查询结果、配置文件）、Prompts（预定义提示模板，帮助 LLM 处理特定场景）。项目中主要使用 Tools，Resources 和 Prompts 是扩展方向
- **SSE 传输 vs WebSocket**：SSE 是服务端单向推送，客户端通过 HTTP POST 发送请求，适合 AI 场景的异步工具调用（服务端推送结果）；WebSocket 是全双工通信，适合实时交互场景。MCP 协议同时支持 SSE、Streamable HTTP、stdio 三种传输方式
- **Function Calling 与 MCP 的配合**：LLM 的 Function Calling 机制让 LLM 输出工具调用请求（工具名 + 参数），应用层执行工具后返回结果。MCP 提供了工具定义的标准化框架（名称、描述、参数 Schema），使 LLM 能精确理解每个工具的用途和参数。两者结合：MCP 定义工具，Function Calling 驱动调用
- **安全设计权衡**：FileSystem MCP Server 的白名单路径校验 vs 灵活性——白名单路径列表由配置管理，客户可按需调整。脚本沙箱的库白名单也通过配置维护，新增安全库无需修改代码