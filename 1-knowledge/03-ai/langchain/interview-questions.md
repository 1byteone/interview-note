# LangChain 面试题大全

> LangChain 专题题库（1-knowledge 面试题库体系），按 Level 1-4 分级。
> 每题仅保留「问题 + 答案 + 解析 + 代码（可选）」；面试官意图 / 深挖方向 / 扣分项见根目录《[LangChain_LangGraph_LangSmith_面试QA_详解版](../../../LangChain_LangGraph_LangSmith_面试QA_详解版.md)》第一章（Q1-Q26）。
> 知识基线：**LangChain 1.0（2025-10）**，`create_agent` 为标准 Agent 入口，AgentExecutor 已弃用。

---

## 📚 知识体系

```
LangChain 核心概念
├── ChatModel 抽象（统一接口 / 厂商适配 / init_chat_model）
├── Messages（System/Human/AI/Tool）
├── Prompt（Template / ChatPromptTemplate / MessagesPlaceholder）
├── LCEL（Runnable 协议 / | 管道 / 惰性求值）
├── OutputParser（结构化输出）
├── Memory（Buffer / Window / Summary）
├── Tool（@tool / BaseTool / Function Calling）
├── Agent（ReAct 循环 / create_agent / Middleware）
├── RAG（混合检索 / Rerank / 幻觉防控）
├── MCP（模型上下文协议）
└── 可观测（Callbacks / LangSmith）
```

---

## 🎯 Level 1：基础题

### 1. 大模型原生调用存在哪些核心困境？
**答案**：三重困境——① 调用方式不统一（各厂商客户端初始化、请求、响应逻辑完全不同，切换模型需改写大量代码）；② 参数体系不统一（`max_tokens` 别名、stop、`temperature`/`top_p` 规则差异，切模型参数可能失效）；③ 代码耦合混乱（业务硬耦合厂商原生类，模型切换需全局改代码，且异步支持不一致）。

**解析**：这是 LangChain 的"价值原点"。答案要给出三重困境的框架，每一条配一个具体例子（如 `max_tokens` 各家别名不同）。可类比 JDBC 的 DriverManager 统一抽象，说明"防腐层/适配器"是通用工程思想。

### 2. LangChain 如何解决大模型调用的三重困境？
**答案**：核心原理是**统一抽象防腐层/适配器层**：业务只依赖统一通用接口（ChatModel），不感知底层厂商差异。① 调用不统一 → ChatModel 基类定义 `invoke`/`stream`/`ainvoke` 统一接口；② 参数不统一 → 厂商适配器内部做参数映射、默认值对齐、标准化过滤；③ 耦合混乱 → `init_chat_model` 收敛创建入口，零代码切换模型。

**解析**：讲清"业务代码 → 统一接口 → 厂商适配器 → 厂商 SDK"的分层链。要能说出抽象层的代价：厂商新特性集成滞后、适配器维护成本。（深挖见详解版 Q2）

### 3. ChatOpenAI 和 init_chat_model 有什么区别？
**答案**：`ChatOpenAI` 是底层**实现类**，仅封装 OpenAI 协议的鉴权、请求、重试、响应解析；`init_chat_model` 是上层**工厂函数**，按模型名/厂商前缀（如 `openai/gpt-4o`）动态导入并创建对应实现类，屏蔽底层差异，支持多模型兼容与零代码切换。

**解析**：经典"类 vs 工厂"辨析题。要点：厂商内 `init_chat_model` 创建的正是 `ChatOpenAI` 实例——两者是"创建"与"实现"两种职责，不是二选一。

---

## 🎯 Level 2：进阶题

### 4. 什么是 LCEL？为什么推荐用它组合链路？
**答案**：LCEL（LangChain Expression Language）是声明式编排语言，核心是 **Runnable 协议 + `|` 管道运算符**：`prompt | model | parser` 组装链路，左侧输出自动作为右侧输入。任何 LCEL 链都实现标准 Runnable 接口，**免费获得 `stream`（逐 token）/ `batch`（并发）/ `ainvoke`（异步）/ 回调 / 重试**。

**解析**：LCEL 是最高频考点。对比手写 Python：声明式 + 惰性求值（构建 DAG，invoke 才执行）+ 能力随接口自带。Runnable 家族的 RunnableLambda（包装函数）/ RunnablePassthrough（透传）/ RunnableParallel（并行）/ RunnableBranch（分支）是必背四件套。

### 5. 四大标准消息类型是什么？
**答案**：① **SystemMessage**：最高优先级全局指令（角色/规范/禁止/格式）；② **HumanMessage**：用户输入载体；③ **AIMessage**：模型输出载体，除文本外携带 token 消耗、`tool_calls`、请求 ID 等元数据；④ **ToolMessage**：工具执行结果回执，通过 `tool_call_id` 与对应调用配对。

**解析**：重点讲"AI 请求工具 → Tool 执行 → ToolMessage 回填 → 再喂模型"的闭环，以及多工具并行调用如何按 tool_call_id 匹配。（深挖见详解版 Q7）

### 6. Memory 有哪些类型？生产如何选型？
**答案**：BufferMemory（全量）/ BufferWindow（滑动窗口）/ Summary（LLM 摘要）/ SummaryBuffer（窗口+摘要混合）。生产推荐「窗口 + 摘要 + 外置持久化（Redis/DB）」组合；**1.0 时代主推 LangGraph Checkpointer（线程内状态）+ Store（跨会话长期记忆）替代 Legacy Memory 类**。

**解析**：选型核心是算"token 成本 vs 失真"的账。多实例部署时内存记忆各自为政，会话记忆必须外置。（深挖见详解版 Q14）

### 7. Tool 与 Function Calling 是什么关系？
**答案**：**Function Calling 是模型侧能力**——厂商在推理时输出结构化 `tool_calls`（"调哪个函数、传什么参数"）；**Tool 是框架侧抽象**——LangChain 用 `bind_tools` 把工具清单传给模型、用 ToolNode 执行、用 ToolMessage 回填，形成闭环。一句话：模型定决策（tool_calls），框架做执行（Tool）。

**解析**：高频混淆点。没有工具调用能力的旧模型退化为文本 ReAct 协议（把工具写进 prompt，模型输出 `Action/Action Input` 文本）。工具设计三要素：清晰的 description、Pydantic 参数校验、幂等容错。（深挖见详解版 Q15-Q16）

---

## 🎯 Level 3：高级题

### 8. LangChain 1.0 有哪些重要变化？
**答案**：① `create_agent` 取代 `create_react_agent`，AgentExecutor 进入维护期（至 2026-12）；② **Middleware 中间件**——`AgentMiddleware` + `before_model`/`wrap_tool_call` 等 6 钩子，内置 PIIMiddleware / SummarizationMiddleware / HumanInTheLoopMiddleware；③ **content_blocks** 跨厂商统一内容块（reasoning / text / tool_call）；④ `langchain-classic` 包拆分（旧 Chain / Retriever / Hub 迁移）；⑤ 结构化输出进主循环，不再额外调 LLM。

**解析**：2026 必考"版本认知"。要能说出 create_agent 底层构建在 LangGraph 上，自动获得持久化 / 流式 / HITL / Time Travel。（深挖见详解版 Q9 / Q19，含 Middleware 完整钩子清单）

### 9. 什么是 ReAct？Agent 循环机制是怎样的？
**答案**：ReAct（Reasoning + Acting）让模型交替执行推理与行动：**Thought（推理下一步）→ Action（输出 tool_call）→ Observation（工具结果回填）→ 再 Thought → … → 模型不再请求工具则输出 Final Answer**。循环由 LangGraph/运行时驱动，受 `recursion_limit` / `max_steps` 上限保护防死循环烧 token。

**解析**：相比 Plan-and-Execute（先规划再执行），ReAct 是"边想边做"的反应式循环。要主动答出终止条件（模型停止请求工具 = 完成）与上限保护，体现生产意识。（深挖见详解版 Q17）

### 10. 什么是混合检索与 Rerank？LangChain 如何实现？
**答案**：**混合检索**：BM25 关键词（精确匹配）+ 向量语义（同义改写）多路召回，RRF 融合；**Rerank**：召回后用 Cross-Encoder（如 bge-reranker）重排，把"语义相关"提升为"真正相关"。原则：**召回层求全、重排层求精，两阶段解耦**。LangChain 用 `EnsembleRetriever`（混合）+ `ContextualCompressionRetriever`（内部 Rerank）组装。

**解析**：RAG 检索优化高频题（字节/阿里）。要理解"向量检索 top-50 召回但上下文只能放 top-5，必须重排精选"的动机。（深挖见详解版 Q24）

### 11. 什么是 Agentic RAG？和普通 RAG 的区别？
**答案**：传统 RAG 是固定线性流水线（检索 → 填上下文 → 生成），一次执行；**Agentic RAG** 把检索流程交给"可控循环"——Agent 自主决定是否检索、检索什么、结果是否够（不够则改写查询/换源/重试）。典型模式：**Self-RAG**（模型自评片段相关性）、**CRAG/Corrective RAG**（低质量触发补救检索）、Plan-and-Execute。

**解析**：在 LangGraph 中就是"检索节点 + 评估节点 + 条件边循环"的图实现。要主动谈成本控制：质检阈值 + 最多迭代 N 次 + 分级模型；查询稳定的场景用固定 RAG 更便宜。（深挖见详解版 Q22）

---

## 🎯 Level 4：专家题

### 12. RAG 中模型幻觉如何检测与缓解？
**答案**：分层缓解——① **检索层**：混合检索 + Rerank + 查询改写 + chunk 优化，减少"没找到/找到错"；② **生成层**：指令"仅基于资料回答，资料不足请说明" + 结构化输出校验；③ **校验层**：检索相关度打分（低分拒答/重检索）+ 回答-证据一致性校验（LLM-as-judge）+ 实体级校验（价格/规格必须来自检索）；④ **兜底层**：拒答话术 + 转人工 + 日志留痕反哺评测集。

**解析**：Correction 字段——"模型会编造价格/规格"的经典解法是实体抽取 + 与数据源比对 + 结构校验三道防线。幻觉率要进评测集量化（呼应 LangSmith 忠实度评估器）。

### 13. 什么是 MCP？LangChain 如何接入 MCP 工具？
**答案**：MCP（Model Context Protocol）是 Anthropic 提出的**开放协议**，标准化"如何向 LLM 提供工具与上下文"——工具以 MCP Server 暴露，任何 MCP Client 复用（USB-C 类比）。LangChain 用 `langchain-mcp-adapters`：`load_mcp_tools(mcp_client)` 把 MCP 工具转为 LangChain Tool，再喂给 `create_agent`。MCP 三要素：Client / Server / 协议（工具、资源、提示三类能力）。

**解析**：2026 Agent 生态标配考点。要区分 MCP 是开放协议而非 LangChain 功能；能说出自建 MCP Server 只要实现 `list_tools` / `call_tool`。（深挖见详解版 Q21）

### 14. LangChain 如何调试"本地正常、生产失败"的应用？
**答案**：五步排查——① **先看 Trace**（LangSmith/自建追踪）定位失败步骤；② **核对环境差异**（模型 key/配额、模型版本漂移、依赖锁、向量库数据同步、真实并发）；③ **检查超时重试**（统一 with_timeout + with_retry）；④ **检查状态持久化**（Checkpointer 生产必须持久化，InMemory 多实例丢会话）；⑤ **失败样本入评测集回归**，防复发。

**解析**：压轴实战题，考察"数据先行"的生产排障方法论——先定位再改代码，修复必须回测。（深挖见详解版 Q26）

---

## 📖 学习资源

### 推荐项目
- [LangChain 官方文档](https://python.langchain.com/)（1.0 入门 / concepts / how-to）
- [LangChain v1 发布说明](https://docs.langchain.com/oss/python/releases/langchain-v1)（create_agent / Middleware / content_blocks）
- [LangChain 实战教程](../../../2-learning/stacks/14-langchain/README.md)（本仓库 14-langchain 五层体系）

### 学习路径
1. Level 1：模型抽象 → 消息 → Prompt
2. Level 2：LCEL → Tool → Memory → Agent 原理
3. Level 3：1.0 新特性 → ReAct → RAG 优化 → Agentic RAG
4. Level 4：幻觉防控 → MCP → 生产排障

### 关联专题
- [LangGraph 面试题](../langgraph/interview-questions.md)（Agent 运行时）
- [LangSmith 面试题](../langsmith/interview-questions.md)（可观测 + 评测）
- [LangChain 实战教程](../../../2-learning/stacks/14-langchain/README.md)（本仓库 14-langchain 五层体系）
- [三合一面试 QA 详解版](../../../LangChain_LangGraph_LangSmith_面试QA_详解版.md)（每题的面试官意图 / 深挖 / 扣分项）
- [三合一面试 QA 背诵版](../../../LangChain_LangGraph_LangSmith_面试QA_背诵版.md)（考前速查）