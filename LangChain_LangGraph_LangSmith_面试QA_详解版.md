# LangChain × LangGraph × LangSmith 面试 QA 详解版

> 基于 `D:\code\codeByCursor\AI_EXAM\docs\LangChain 核心面试题.docx`（7 题精简版详解化）+ LangChain 官方文档 + LangGraph 1.0 / LangSmith 评测体系 + 2026 年社区高频面试题汇总。
> 知识时效基线：**LangChain 1.0 / LangGraph 1.0（2025-10 发布）**，AgentExecutor 已弃用，`create_agent` 成为标准入口。
> 每题包含：**问题 → 参考回答 → 关键点解析（面试官意图 / 怎么答 / 深挖方向）→ 常见扣分项**。
> 配套速查：见《LangChain_LangGraph_LangSmith_面试QA_背诵版.md》；教程体系：见 `2-learning/stacks/14-langchain` 与 `2-learning/stacks/18-langsmith`。

---

## 第一章 · LangChain（26 题）

> 覆盖：模型抽象、消息体系、Prompt、LCEL、Memory、Tool、Agent、1.0 新特性、RAG、可观测。
> docx 原 7 题已在前 7 位完整保留并详解化。

### Q1：大模型原生调用存在哪些核心困境？

**参考回答：** 大模型各厂商 API、参数、依赖规范不统一，开发者直接对接原生 SDK 会面临三重核心困境：

1. **调用方式不统一**：各厂商客户端初始化、请求、响应逻辑完全不同（OpenAI、通义千问、文心一言调用方法各异），多模型接入、切换需改写大量代码，维护成本极高。
2. **参数体系不统一**：同款参数命名、取值、语义、交互规则不一致（如 `max_tokens` 别名、stop 停止序列参数、`temperature` 与 `top_p` 规则差异），切换模型易出现参数失效、结果异常，无法统一评测。
3. **代码耦合混乱**：每个厂商需单独引入依赖，业务代码硬耦合厂商原生类，模型切换需全局改代码，极易产生 Bug；同时各厂商异步支持不一致，进一步加剧调用混乱。

**关键点解析：**
- **面试官意图**：这道题是 LangChain 存在的"为什么"，考察你是否理解框架的价值原点，而不是背 API。
- **怎么答**：先给出"三重困境"的框架（调用、参数、耦合），每一条给一个具体例子（如 `max_tokens` 各厂商别名不同），让答案有画面感。
- **深挖方向**：追问"那 Java 生态有没有类似问题？"——可类比 JDBC 驱动的统一抽象、Spring Data 的 Repository 抽象，说明"防腐层/适配器"是通用工程思想。

**常见扣分项：** 只背框架名不讲困境；把"参数不统一"和"调用方式不统一"混为一谈；答不出异步支持差异。

---

### Q2：LangChain 如何解决大模型调用的三重困境？核心原理是什么？

**参考回答：** 核心原理是**引入统一抽象防腐层/适配器层**，解耦业务代码与厂商原生实现，让业务只依赖统一通用接口，不感知底层模型厂商差异。针对性解决方案：

1. **解决调用不统一**：通过 `ChatModel` 基类定义 `invoke`、`stream`、`ainvoke`、`batch` 等统一调用接口，所有厂商模型均适配该标准；
2. **解决参数不统一**：各厂商适配器内部完成参数映射、默认值对齐、参数标准化过滤，对外暴露统一超参数；厂商不支持的参数，LangChain 会自动拦截或映射（如把 `max_tokens` 映射为对应厂商的别名）；
3. **解决耦合混乱**：收敛模型创建入口（`init_chat_model`），摒弃厂商单独导入方式，业务侧零代码切换模型，彻底消除硬编码耦合。

**关键点解析：**
- **面试官意图**：检验你是否理解"抽象层"这个设计模式，而不只是知道 LangChain 有 ChatModel 这个类。
- **怎么答**：讲清"业务代码 → 统一接口（ChatModel）→ 厂商适配器 → 厂商 SDK"这条分层链，再落到三个困境各自怎么解。
- **深挖方向**：追问"抽象层的代价是什么？"——新增厂商适配器有维护成本；LangChain 可能滞后于厂商新特性（如某些厂商先推的新参数要等集成）；引入间接层有轻微性能损耗与版本一致性问题。

**常见扣分项：** 只答"适配器"两个字没有展开；说不清参数映射的具体机制；不知道 `init_chat_model` 是收敛入口。

---

### Q3：简述 LangChain ChatModel 核心组件及作用

**参考回答：** `ChatModel` 是 LangChain 对话大模型的核心抽象体系，适配多轮对话、RAG、智能体等主流场景，核心分为三层组件：

1. **ChatModel 抽象基类（BaseChatModel）**：所有对话模型的顶层抽象，定义统一接口契约（`invoke` / `stream` / `batch` / `ainvoke` / `bind_tools` 等），规范所有模型的通用能力；
2. **厂商实现类（如 ChatOpenAI、ChatAnthropic、ChatOllama）**：基类的具体实现，封装对应厂商的鉴权、请求、重试、响应解析等底层细节，完成厂商差异适配；
3. **`init_chat_model` 统一工厂函数**：工业级标准化模型初始化入口，支持通过模型名称/厂商一键实例化各类模型，自动完成动态导入、参数映射、实例创建。实现零代码切换模型、统一参数、统一同步/流式/异步调用，是当前主流开发范式。

**关键点解析：**
- **面试官意图**：考察你是否理解"接口抽象 → 具体实现 → 工厂创建"三层结构，这是理解 LangChain 一切组件的钥匙。
- **怎么答**：用"基类 + 实现类 + 工厂"三明治结构回答，点名每个层次职责；能顺手写出 `init_chat_model("gpt-4o")` 调用示例更好。
- **深挖方向**：追问"`messages` 参数为什么是消息列表而不是字符串"——LLM 对话是多轮上下文，`ChatModel` 天然按消息序列建模，这也是它区别于旧式 `LLM` 类（纯文本输入）的本质。

**常见扣分项：** 把 ChatModel 和 ChatOpenAI 混为一谈；忘记 `init_chat_model` 这层工厂；讲不出 `bind_tools` 这类模型无关的能力接口。

---

### Q4：高频辨析题：ChatOpenAI 和 init_chat_model 的区别？

**参考回答：**

| 维度 | ChatOpenAI | init_chat_model |
|------|-----------|-----------------|
| 定位 | 底层实现类 | 上层工厂函数 |
| 职责 | 专门封装 OpenAI 协议的调用细节，是单一厂商的具体实现 | 创建 ChatOpenAI 对象的工厂方法，通用统一入口 |
| 适配面 | 仅 OpenAI 协议 | 可动态适配所有主流厂商模型（OpenAI / Anthropic / 通义 / Ollama…） |
| 扩展性 | 换厂商要改 import 和初始化代码 | 只改模型名称字符串即可，自动匹配对应实现类 |
| 适用场景 | 明确只用 OpenAI 且要深度调参数 | 多模型兼容、动态切换、按环境变量配置模型的业务场景 |

一句话：`ChatOpenAI` 是"具体实现"，`init_chat_model` 是"统一入口"——它内部自动完成动态导入与参数映射，屏蔽底层差异，更适配多模型兼容、动态切换的业务场景。

**关键点解析：**
- **面试官意图**：经典"类 vs 工厂"辨析题，考察你对抽象层次的理解，以及是否能区分"实现"与"创建"两种职责。
- **怎么答**：先给身份定位（实现类 vs 工厂函数），再比适配面、切换成本、适用场景；最好给出 `init_chat_model("openai/gpt-4o")` 与 `ChatOpenAI(model="gpt-4o")` 对照。
- **深挖方向**：追问 `init_chat_model` 如何做到"字符串→实现类"的？——基于厂商前缀映射（如 `openai/`、`anthropic/`）做动态导入，未安装对应集成包会抛出清晰错误，可深入读源码 `load_chat_model_kg` 的解析逻辑。

**常见扣分项：** 说成"init_chat_model 是 ChatOpenAI 的别名"；认为两者互斥（实际 init_chat_model 内部创建的就是 ChatOpenAI 实例）；不知道参数映射和动态导入为何物。

---

### Q5：什么是提示词？核心作用有哪些？

**参考回答：** 提示词（Prompt）是连接用户、开发者与大模型的核心交互媒介，是 AI 工程化开发的核心要素，保障模型输出可控、可复用、可落地。核心作用分四点：

1. **角色定义**：赋予模型专属身份与能力边界，约束模型行为，减少幻觉与无效输出（如"你是一名资深 Java 架构师"）；
2. **任务指令**：清晰传递任务目标与执行要求，规避模型语义理解偏差（如"请用 500 字以内总结"）；
3. **格式约束**：统一模型输出格式、长度与排版，适配工程标准化开发（如 JSON 输出、Markdown 结构）；
4. **逻辑引导**：规范模型推理步骤（Chain-of-Thought），提升复杂任务输出的精准度与逻辑性（如"先分析，再给结论"）。

**关键点解析：**
- **面试官意图**：考察 Prompt 工程基础，以及你是否理解 Prompt 是"产品的一部分"而非随手写几句话。
- **怎么答**：按四个作用分类作答，每个给一个业务化例子；能引申到"提示词也是需要版本管理、评测的资产"加分（衔接 LangSmith Prompt Hub）。
- **深挖方向**：可追问"提示词泄露与注入如何防护"——敏感指令前置、输入隔离、检测 `ignore previous instructions` 类注入、在生产用 PIIMiddleware 脱敏。

**常见扣分项：** 只答"给模型说话的话"这种大白话；四个作用记不全；不讲与工程化的关系。

---

### Q6：LangChain 中什么是消息？消息和提示词的关系是什么？

**参考回答：**

- **消息定义**：消息是 LangChain 与 LLM 交互的最小标准化单元，包含角色标识、文本内容、交互元数据，可区分对话身份，是支撑多轮对话上下文留存的结构化载体——区别于无角色、无法留存上下文的纯文本提示词。
- **二者从属关系**：消息是结构化载体，提示词是核心指令内容。提示词依托消息实现角色区分与上下文挂载，无法脱离消息单独生效；消息的核心承载内容即为提示词。业务中用户提示词会封装为用户消息（HumanMessage），模型输出会封装为 AI 消息（AIMessage）。

**关键点解析：**
- **面试官意图**：考察是否理解"消息"这个 LangChain 一等的抽象——很多初学者只用字符串调模型，理解不了多轮对话和工具调用的结构基础。
- **怎么答**：用"载体 vs 内容"的关系作答，再举"用户输入→HumanMessage，模型输出→AIMessage"的封装例子。
- **深挖方向**：追问"为什么必须是结构化消息"——多轮对话需要区分身份才能正确累积上下文；工具调用需要 AIMessage 携带 tool_calls 元数据；流式输出需要按消息分段。

**常见扣分项：** 把"消息"和"提示词"说成同义词；不知道消息携带元数据（token 消耗、tool_calls、request id）。

---

### Q7：LangChain 四大标准消息类型及核心作用？

**参考回答：** LangChain 定义四类标准化交互消息，适配常规对话与工具调用全场景：

1. **SystemMessage 系统消息**：最高优先级全局指令，全程生效。用于定义模型角色、行为规范、禁止规则、输出格式，约束整体对话逻辑；
2. **HumanMessage 用户消息**：用户实时输入载体，持久存入对话上下文。承载用户提问、需求与补充指令，是驱动模型应答的核心输入；
3. **AIMessage AI 消息**：模型输出载体，存入对话上下文。不仅存储应答文本，还包含 Token 消耗、工具调用标识（tool_calls）、请求 ID 等元数据，是多轮对话与工具调用的核心；
4. **ToolMessage 工具消息**：工具执行结果回执，绑定对应工具调用 ID。承载外部工具、函数的执行结果，配合 AI 消息完成 Agent 工具调用闭环，支持多工具并行调用匹配。

**关键点解析：**
- **面试官意图**：考察消息体系的完整性；重点在能否讲出 AIMessage 的 tool_calls 与 ToolMessage 的 tool_call_id 如何配对——这是 LangChain 工具调用的机制核心。
- **怎么答**：四类逐一讲清"是什么 + 用于什么场景"，最后用"AI 请求工具 → Tool 执行 → ToolMessage 回执 → 二次喂给模型"闭环收尾。
- **深挖方向**：追问"多工具并行调用如何匹配结果"——每个 tool_call 有唯一 id，ToolMessage 通过 tool_call_id 关联回对应调用，LangGraph 的 ToolNode 自动完成配对。

**常见扣分项：** 漏掉 ToolMessage；不知道 ToolMessage 必须绑定 tool_call_id；把 AIMessage 只当"文本输出"。

---

### Q8：什么是 LangChain？它提供了哪些核心能力？

**参考回答：** LangChain 是开源的大模型应用开发框架，围绕"模型、提示词、工具、记忆、检索"五大要素，把 LLM 应用的可复用部分抽象成标准组件，并提供 LCEL 声明式编排把这些组件组装为应用。

核心能力：
1. **模型抽象**：统一 ChatModel 接口 + `init_chat_model`，多厂商零切换；
2. **提示词管理**：PromptTemplate / ChatPromptTemplate / MessagesPlaceholder，模板化、变量注入、少样本示例；
3. **工具与函数调用**：`@tool` 装饰器、BaseTool、与模型 tool calling 协议对齐；
4. **记忆**：会话历史存储、摘要、窗口截断；
5. **检索增强**：Document Loader、Splitter、VectorStore、Retriever 全套 RAG 组件；
6. **编排（LCEL）**：Runnable 协议 + `|` 管道，声明式组装可流式、可批量、可异步的链路；
7. **Agent**：1.0 起通过 `create_agent` 构建工具调用型智能体，运行在 LangGraph 运行时之上。

**关键点解析：**
- **面试官意图**：第一印象题，考察你能否结构化说清框架全貌，而不是背营销文案。
- **怎么答**：用"组件层（模型/提示词/工具/记忆/检索）+ 编排层（LCEL）+ Agent 层"三层讲，每层一句话带一个实际用途。
- **深挖方向**：追问"LangChain 和直接写 Python 调 API 有什么本质区别"——组件标准化 + 声明式编排 + 生态集成（1000+ 集成），并承认裸调用在简单场景更轻量，体现工程判断。

**常见扣分项：** 只背"LangChain 是开发大模型应用的框架"没有展开；不知道 1.0 时代 Agent 基于 LangGraph。

---

### Q9：LangChain 1.0 有哪些重要变化？（2025-10 发布）

**参考回答：** LangChain 1.0（2025年10月与 LangGraph 1.0 同期发布）是聚焦 Agent 构建的生产级重构，四大核心变化：

1. **`create_agent` 取代 `create_react_agent`**：`create_agent` 成为构建 Agent 的标准入口（`langchain.agents`），提供更简洁的接口、更强的可定制性；`langgraph.prebuilt.create_react_agent` 被弃用，`AgentExecutor` 进入维护期（至 2026-12）；`create_agent` 底层构建在 LangGraph 上，自动获得持久化、流式、HITL、Time Travel 能力。
2. **Middleware 中间件体系**：1.0 标志性特性——`AgentMiddleware` 基类 + `before_agent / before_model / wrap_model_call / wrap_tool_call / after_model / after_agent` 钩子，可组合式实现 PII 脱敏、对话摘要、敏感操作审批、动态工具/模型选择（如按用户等级换模型）等横切能力，内置 PIIMiddleware、SummarizationMiddleware、HumanInTheLoopMiddleware。
3. **标准内容块（content_blocks）**：跨厂商统一的 `content_blocks` 属性，用同一套 API 访问 reasoning 推理痕迹、文本、工具调用等异构内容，对 Anthropic / OpenAI / AWS / Google / Ollama 等适配。
4. **命名空间精简**：`langchain` 主包只保留 Agent 构建核心；旧 Chain、Retriever、Hub 等迁移到 `langchain-classic` 独立包，`pip install langchain-classic` 即可兼容旧代码。

另：结构化输出改进——在主循环中生成（不额外调 LLM），支持 ToolStrategy 与 provider 侧结构化输出双策略。

**关键点解析：**
- **面试官意图**：2026 年面试必考"版本认知"，考察候选人是否跟上生态演进，而非停留在 0.x 教程。
- **怎么答**：按"新增 create_agent → Middleware → content_blocks → 包拆分"四块答，每块一句话价值；主动点出 AgentExecutor 弃用时间线体现信息新。
- **深挖方向**：追问"create_agent 的 Middleware 和 Spring 的拦截器/AOP 什么关系"——都是"横切关注点"的钩子抽象，可类比 `HandlerInterceptor` / Filter，展示跨生态迁移能力。

**常见扣分项：** 还在推荐 AgentExecutor / create_react_agent 作为新项目方案；不知道 langchain-classic；把 Middleware 理解成简单的装饰器而说不出钩子点。

---

### Q10：什么是 LCEL？为什么推荐用它组合链路？

**参考回答：** LCEL（LangChain Expression Language）是 LangChain 的声明式编排语言，核心是 `Runnable` 协议与 `|` 管道运算符。`prompt | model | parser` 一行即可组装链路：左侧 Runnable 的输出自动作为右侧输入。

核心价值：
1. **统一接口**：任何 LCEL 链都实现标准 Runnable 接口（`invoke` / `batch` / `stream` / `ainvoke` / `abatch` / `astream`）；
2. **免费流式**：组件级实现流式则自动获得链级流式传输（逐 token），对话体验好；
3. **并行与批处理**：`RunnableParallel` 可并行执行多条路径；`batch` 天然支持并发批量；
4. **异步原生**：ainvoke 等异步接口与 FastAPI 等异步框架无缝集成；
5. **可观测与可重试**：内置回调注入点、自动重试配置（`with_retry`）、完善错误处理；
6. **与 LangSmith 打通**：LCEL 链自动上报 trace，调试零成本。

**关键点解析：**
- **面试官意图**：LCEL 是 LangChain 最核心考点之一，考察声明式 vs 命令式的工程理解。
- **怎么答**：先给一行代码示例（`prompt | model | StrOutputParser()`），再讲"免费获得 stream/batch/async"这个关键词，这是对比手写 Python 的最大卖点。
- **深挖方向**：追问"LCEL 是立即执行还是惰性求值"——声明构建 DAG，`invoke` 时才执行；追问"如何做条件分支"——`RunnableBranch` / `RunnableLambda` 返回条件路由。

**常见扣分项：** 只会背语法不会讲价值；不知道 RunnableParallel / RunnableBranch / RunnablePassthrough 的存在；说"LCEL 就是链式调用"（它比函数链多了惰性求值和统一契约）。

---

### Q11：Runnable 接口的核心方法有哪些？它与普通 Python 函数调用的区别？

**参考回答：** Runnable 是 LCEL 的统一执行契约（类似"可执行单元接口"），核心方法：

| 方法 | 含义 |
|------|------|
| `invoke(input)` | 同步单次执行 |
| `ainvoke(input)` | 异步单次执行 |
| `batch(inputs)` | 同步批量执行（自动并发） |
| `abatch(inputs)` | 异步批量执行 |
| `stream(input)` | 同步流式执行（逐块产出） |
| `astream(input)` | 异步流式执行 |
| `bind(**kwargs)` | 绑定运行时参数（如 model 的 temperature、tools） |

与普通函数调用的区别：
1. **统一契约**：prompt、model、parser、retriever、自定义函数包装成 Runnable 后可无缝组合，类型签名自动串联；
2. **惰性构建**：链是数据流描述（DAG），执行时才真正触发；
3. **能力内置**：流式、并发批处理、重试、回调、日志、Schema 检查随接口自带，无需手工实现。

**关键点解析：**
- **面试官意图**：考察对抽象接口的理解深度——是否能把"统一抽象带来的能力"讲透。
- **怎么答**：用"标 / 批 / 流 / 异"四个维度覆盖方法表，再对比普通函数"每次调用都是孤立的、流式得自己写生成器"，突出契约价值。
- **深挖方向**：追问如何把已有 Python 函数接入——`RunnableLambda(fn)` 包装；如何透传——`RunnablePassthrough`；如何并行——`RunnableParallel`。三者是高频三兄弟。

**常见扣分项：** 方法表记不全（漏 bind / batch / astream）；说不清 RunnableLambda / RunnablePassthrough / RunnableParallel 用途。

---

### Q12：PromptTemplate 与 ChatPromptTemplate 的区别？MessagesPlaceholder 有什么用？

**参考回答：**

- **PromptTemplate（字符串模板）**：针对纯文本 LLM 的模板，`"请总结以下内容：{content}"`，变量用 `{}` 占位；
- **ChatPromptTemplate（对话模板）**：针对 ChatModel 的消息列表模板，由 `SystemMessagePromptTemplate`、`HumanMessagePromptTemplate`、`AIMessagePromptTemplate` 组合而成，生成的是消息序列而不是纯文本，天然适配多轮对话；
- **MessagesPlaceholder（消息占位符）**：在模板中预留"消息列表"位置（如 `MessagesPlaceholder("history")`），运行时把多轮历史（或 tool_calls 等结构化消息）整体注入，是**对话记忆与工具调用在提示词层的关键连接点**。

**关键点解析：**
- **面试官意图**：考察是否理解"纯文本 vs 消息序列"两种模型接口的本质差异，以及多轮对话如何注入历史。
- **怎么答**：先对比两种模板产出物（字符串 vs 消息列表），再讲 MessagesPlaceholder 如何解决"历史消息动态长度"问题——它允许传任意多条消息而不用提前拼接字符串。
- **深挖方向**：追问工具调用场景——模型 1.0 后 `bind_tools` 把工具 schema 附加到模型调用，不需要放进 PromptTemplate；MessagesPlaceholder 多用于 `history` 和 `agent_scratchpad`（Agent 中间推理）。

**常见扣分项：** 混用 PromptTemplate 调 ChatModel（类型不匹配报错）；不知道 MessagesPlaceholder 存在；说 MessagesPlaceholder 只能放字符串历史（它放的是消息对象）。

---

### Q13：输出解析器有哪几种？如何保证模型输出是合法 JSON / Pydantic 对象？

**参考回答：** 输出解析器（OutputParser）把模型原始输出转为结构化数据，常用三种：

1. **StrOutputParser**：直接取字符串内容，最基础；
2. **PydanticOutputParser / 结构化输出**：定义 Pydantic 模型，解析器把模型输出解析成校验后的对象，解析失败自动触发修复重试（`with_structured_output` 是更推荐的新方式）；
3. **JsonOutputParser**：把输出解析为 JSON，配合 `format_instructions` 约束模型输出格式。

**保证合法结构的核心手段**（现代做法）：优先用 `model.with_structured_output(PydanticModel)`，它直接调用模型厂商的**原生结构化输出能力**（OpenAI 的 JSON mode / `strict` 模式、Anthropic 的 tool use 模式）——让模型把"输出 schema"当作工具调用返回，天然合法，远优于"靠 prompt 让它吐 JSON 再解析"；LangChain 1.0 中 create_agent 的结构化输出已进主循环，不再额外调一次 LLM。

**关键点解析：**
- **面试官意图**：考察"工程落地"意识——结构化输出是 AI 应用接业务系统的硬需求，面试官想看你会不会选方案。
- **怎么答**：讲两级方案——老派（prompt + parser + 修复重试）与新派（with_structured_output 原生工具调用模式），并说明为什么新派更稳。
- **深挖方向**：追问"解析失败怎么办"——自动重试（部分模型支持）、降级为字符串再正则抽取、人工兜底；追问"JSON 大括号被模型截断"——用流式拼接 + 完整校验 + 超时重试。

**常见扣分项：** 只会 PydanticOutputParser 不知道 with_structured_output；不知道"模型原生结构化输出"机制；把解析器当万能（模型输出合法 JSON 仍需校验）。

---

### Q14：Memory 有哪几种？生产环境中如何选择？

**参考回答：** LangChain Memory 解决多轮对话上下文问题，主流方案：

1. **ConversationBufferMemory**：完整保存所有对话历史，最朴素，上下文越长 token 成本越高；
2. **ConversationBufferWindowMemory**：只保留最近 K 轮（滑动窗口），控制 token，但会丢早期信息；
3. **ConversationSummaryMemory**：用 LLM 生成历史摘要，长对话省 token 但摘要本身有失真和额外调用；
4. **ConversationSummaryBufferMemory**：窗口 + 摘要混合，超阈值的历史转摘要；
5. **基于外置存储的持久记忆**：把历史存入 Redis/数据库，跨会话/跨实例复用（生产推荐）。

**生产建议**：不要用内存版的"全部历史"方案，应按"短期窗口 + 摘要 + 外置持久化"组合；**LangGraph 时代更推荐用 Checkpointer（thread_id 会话级状态持久化）+ Store（跨会话长期记忆）替代 Legacy Memory 类**——这也是 1.0 官方主推方向。

**关键点解析：**
- **面试官意图**：考察记忆方案选型的工程判断，能否算 token 成本和失真的账。
- **怎么答**：先列四类及适用场景，再给生产组合方案（窗口 + 摘要 + 持久化），点出 LangGraph Checkpointer 演进。
- **深挖方向**：追问"长对话 token 爆炸怎么办"——trim_messages 截断、摘要、按会话归档；追问"多实例部署会话记忆如何共享"——必须外置（Redis/DB），内存记忆多实例各自为政。

**常见扣分项：** 只背类名不会选型；推荐在内存环境里存全部历史；不知道 1.0 时代 Checkpointer 已成主流记忆方案。

---

### Q15：如何自定义 Tool？`@tool` 装饰器和 BaseTool 有什么区别？

**参考回答：** 自定义工具是 Agent 能力的延伸，两种方式：

1. **`@tool` 装饰器（推荐，快速）**：把普通函数变成 Tool，函数签名 + 类型注解 + docstring 自动生成工具的 name / description / args_schema：
```python
from langchain.tools import tool

@tool
def get_weather(city: str) -> str:
    """查询指定城市的天气，入参为城市名。"""
    return query_weather_api(city)
```
2. **BaseTool 子类（复杂定制）**：继承 BaseTool，实现 `_run`（同步）/ `_arun`（异步），可精细控制参数 Schema、错误处理、缓存、重试、回调等。

**核心设计要点**：
- **description 要写清楚**：模型靠 description 决定何时调用、传什么参数——它是工具与 LLM 的契约；
- **参数 Schema 要校验**：用 Pydantic 定义参数模型，非法入参在进入函数前拦截；
- **工具要幂等**：Agent 重试/循环可能重复调用，工具实现应可重复执行；
- **错误要友好返回**：返回"查询失败原因"字符串而非抛异常，让模型能据此调整重试。

**关键点解析：**
- **面试官意图**：工具调用是 Agent 落地核心，考察是否能正确设计"可被 LLM 使用"的工具。
- **怎么答**：给一个 @tool 示例 + 三条契约要点（description / schema / 幂等容错），并强调"工具是为模型设计的接口，不是给人设计的函数"。
- **深挖方向**：追问异步工具——用 `@tool` 装饰 async 函数即可（LangChain 自动识别）；追问"参数是 Pydantic 模型"，`StructuredTool.from_function` 可显式传入 args_schema。

**常见扣分项：** 不写 description 或写得很差；工具抛异常导致 Agent 循环中断；不知道工具同步/异步要分别实现 _run/_arun。

---

### Q16：Tool 与 Function Calling（函数调用）是什么关系？

**参考回答：**

- **Function Calling（函数调用）**：是**模型侧能力**——厂商（OpenAI / Anthropic / 通义等）在模型训练中支持"输出工具调用指令"，模型在推理时决定"该调用哪个函数、传什么参数"，以结构化 `tool_calls` 返回，而不是用自然语言描述；
- **Tool（工具）** 是**框架侧抽象**——LangChain 把"可被模型调用的能力"封装为 Tool（name / description / args_schema），并负责：把工具清单通过 `bind_tools` 传给模型 → 解析模型返回的 tool_calls → 执行对应 Tool → 把 ToolMessage 结果回填给模型形成闭环。

一句话：**Function Calling 是模型的"决策协议"，Tool 是框架的"执行包装"，两者通过 tool_calls / ToolMessage 衔接**。

**关键点解析：**
- **面试官意图**：高频混淆点，考察你是否分得清"模型能力"和"框架抽象"两个层面。
- **怎么答**：从"模型侧输出 tool_calls"和"框架侧 bind_tools + ToolNode 执行"两端讲，强调 ToolMessage 回填闭环。
- **深挖方向**：追问"没有工具调用能力的模型怎么办"——ReAct 文本协议（把工具说明写进 prompt，模型输出文本格式的 Action/Action Input），LangChain 的 create_react_agent 走的就是"模型工具调用优先，退化为文本 ReAct"兼容路径。

**常见扣分项：** 把 Tool 和 Function Calling 说成同一件事；不知道 bind_tools 这个衔接 API；不知道老模型退化为文本 ReAct 协议。

---

### Q17：什么是 ReAct Agent？其循环机制是怎样的？

**参考回答：** ReAct（Reasoning + Acting）是 Agent 的核心范式：让模型**交替执行"推理（Reasoning）"与"行动（Acting）"**，通过"思考-行动-观察"循环完成任务。

循环机制（以工具调用型 Agent 为例）：
```
Thought（模型推理下一步）→ Action（模型输出 tool_call 调用工具）
→ Observation（执行工具返回结果）→ 再次 Thought → … → 模型认为任务完成 → Final Answer
```
在 LangChain/LangGraph 中，这个循环由运行时驱动：`create_agent` / StateGraph 里 agent 节点调模型 → 若模型返回 tool_calls 则路由到工具节点执行 → 结果回填消息 → 再调模型 → 直到模型不再请求工具则输出最终答案；循环受 `max_steps` / `recursion_limit` 等上限约束，防止死循环烧 token。

**关键点解析：**
- **面试官意图**：Agent 原理必考，考察理解深度——能否讲清循环、终止条件、上限保护。
- **怎么答**：先给"Thought→Action→Observation"循环骨架，再讲运行时如何驱动与终止（模型停止请求工具 = 结束），最后带上限保护（recursion_limit / max_steps）体现生产意识。
- **深挖方向**：追问"ReAct 和 Plan-and-Execute 区别"——ReAct 边想边做（反应式），Plan-and-Execute 先产出完整计划再执行（规划式），各有适用场景；追问"ReAct 的幻觉风险"——工具结果与模型判断冲突时以工具为准，加校验。

**常见扣分项：** 只会背"ReAct 是推理+行动"无循环细节；不说终止条件；不知道为什么需要 recursion_limit。

---

### Q18：Agent 与 Chain 的区别是什么？

**参考回答：**

| 维度 | Chain（链） | Agent（智能体） |
|------|------------|----------------|
| 控制流 | 预定义、固定线性流程（DAG） | 动态、由模型决策下一步（循环 + 条件路由） |
| 决策者 | 开发者 | 模型（借助工具列表与推理） |
| 工具使用 | 手动编排 | 模型自主选择并调用工具 |
| 可预测性 | 高（流程确定） | 低（路径不固定） |
| 适用 | 流程明确的业务（如"取参→检索→生成"固定 RAG 链） | 需要多步推理、动态工具组合的开放式任务 |

**工程建议**：能用 Chain 不用 Agent——Agent 每次运行会反复调模型、不可预测、成本高；只有当任务需要"模型动态决定步骤"时才用 Agent；也可以"Chain 为主干 + Agent 处理特殊分支"混合。

**关键点解析：**
- **面试官意图**：考察架构选型判断力，防止把 Agent 当万能锤子。
- **怎么答**：用"开发者预编排 vs 模型自主决策"一句话定调，再列适用场景对比；给出"优先 Chain、必要时 Agent、可混合"的工程立场。
- **深挖方向**：追问"现代框架中 Chain 还有位置吗"——LangGraph 是更通用的图编排，LCEL Chain 仍是简单线性流程的最轻方案；1.0 中链式能力保留但 Agent 层已统一构建在 LangGraph 上。

**常见扣分项：** 说不清决策者差异；无脑推荐 Agent；不知道混合架构（Chain 主干 + Agent 分支）。

---

### Q19：LangChain 1.0 的 create_agent 是什么？和 create_react_agent 相比有什么变化？

**参考回答：** `create_agent` 是 LangChain 1.0 构建 Agent 的新标准入口（位于 `langchain.agents`），是 `langgraph.prebuilt.create_react_agent` 的升级替代：

```python
from langchain.agents import create_agent

agent = create_agent(
    model="claude-sonnet-4-6",
    tools=[search_web, analyze_data, send_email],
    system_prompt="You are a helpful research assistant.",
    middleware=[SummarizationMiddleware(...)],   # 1.0 中间件
)
result = agent.invoke({"messages": [{"role": "user", "content": "..."}]})
```

关键变化：
1. **接口更简洁**：字符串模型名即可初始化（类似 init_chat_model 风格），支持 tools、system_prompt、middleware 直接配置；
2. **可定制性更强**：通过 Middleware 钩子（before_model / wrap_tool_call 等）实现上下文工程、守门、PII 脱敏、动态模型/工具切换，而旧 create_react_agent 难以自定义；
3. **底层不变**：依然构建在 LangGraph 上，自动获得持久化（checkpointing）、流式、Human-in-the-Loop、Time Travel；
4. **官方迁移路径**：`AgentExecutor`（维护至 2026-12）→ `create_react_agent`（已弃用）→ `create_agent`。

**关键点解析：**
- **面试官意图**：2026 高频新题，考察对 1.0 演进的实际掌握。
- **怎么答**：点出"新标准入口 + Middleware 是其灵魂 + 底层仍是 LangGraph"，给一行代码即可。
- **深挖方向**：追问 Middleware 的钩子清单——before_agent / before_model / wrap_model_call / wrap_tool_call / after_model / after_agent；追问内置中间件——PIIMiddleware / SummarizationMiddleware / HumanInTheLoopMiddleware。

**常见扣分项：** 不知道 1.0 有 create_agent；把 AgentExecutor 当现行方案；说不清 Middleware 钩子。

---

### Q20：LangChain 如何实现流式输出？流式在什么场景必须用？

**参考回答：** 流式输出通过 Runnable 的 `stream` / `astream` 方法实现：

```python
async for chunk in chain.astream({"question": q}):   # LCEL 链自动支持流式
    print(chunk, end="")
```

关键点：
1. **LCEL 链自动流式**：只要链中每个组件实现 stream，`|` 组合的链就会自动逐 token 传输，无需手写；
2. **流式模式**：`stream_mode` 可控制流的是最终 token 还是中间步骤（Append、Values、Messages、Updating 等）；
3. **必须流式的场景**：① 聊天 UI——用户感知"正在生成"，等待体验显著更好（首 token 延迟优化）；② 长文生成——先给对方骨架印象；③ 回报进度——Agent 多步执行时逐步渲染工具调用过程而非干等。

**生产注意**：流式 + 网关/SSE 集成（FastAPI StreamingResponse / SSE）、断线续传、流式输出时的限流与成本控制；首 token 延迟（TTFT）是体验关键指标，通常本地模型或预热连接来优化。

**关键点解析：**
- **面试官意图**：考察实战能力——多数候选人会说"用 stream"，但说不出为什么流式和怎么跟 Web 层衔接。
- **怎么答**：给 LCEL 自动流式示例 + 三个必用场景 + SSE 落地一句话，体现全链路认知。
- **深挖方向**：追问"LangGraph 中流式"——`app.stream(..., stream_mode=["updates","messages"])` 可同时流节点状态和 token；追问"断流恢复"——前端重连 + 服务端幂等 + 缓存增量。

**常见扣分项：** 只会 stream() 不会 astream()；不知道 SSE 协议；说不出流式的产品价值。

---

### Q21：什么是 MCP？LangChain / LangGraph 如何接入 MCP 工具？

**参考回答：** MCP（Model Context Protocol）是 Anthropic 提出的**开放协议**，标准化"应用如何向 LLM 提供工具与上下文"，把工具接入从"每家模型一个 SDK"变成"统一的 client-server 协议"——工具（如 GitHub、数据库、浏览器）以 MCP Server 形式暴露，任何 MCP Client（Claude、LangChain 等）都能复用。

LangChain 接入方式：
```python
from langchain_mcp_adapters import load_mcp_tools
from langchain_mcp_adapters.client import MultiServerMCPClient

client = MultiServerMCPClient(...)      # 连接 MCP Server
tools = await load_mcp_tools(client)    # MCP 工具 → LangChain Tool
agent = create_agent(model=..., tools=tools)   # 接入 Agent
```

- **langchain-mcp-adapters** 官方适配包：MCP 工具 ↔ LangChain / LangGraph Tool 双向转换；
- 收益：MCP Server 一次实现，多个 AI 应用复用，规避"每家集成单独写一遍"的重复开发；
- 生态位：MCP 是"工具的 USB-C 接口"，LangChain 通过适配器成为"支持所有接口的宿主"。

**关键点解析：**
- **面试官意图**：2026 年 MCP 已成 Agent 生态标配考点，考察你的协议视野。
- **怎么答**：先用"USB-C"类比讲清 MCP 是什么，再讲适配器接入链路，最后讲生态价值。
- **深挖方向**：追问"MCP 的三个核心元素"——Client / Server / 协议（工具、资源、提示三类能力）；追问"自建 MCP Server"——用 fastmcp / 官方 SDK 实现 `list_tools / call_tool` 即可。

**常见扣分项：** 不知道 MCP 是开放协议（以为是 LangChain 功能）；分不清 Client 与 Server 角色；说不清适配器价值。

---

### Q22：什么是 Agentic RAG？和普通 RAG 有什么区别？（LangGraph 视角）

**参考回答：**

- **普通 RAG**：固定流水线——用户问题 → 向量检索（可能混合检索 + Rerank）→ 填充上下文 → 生成回答。流程确定、无决策，一次执行结束；
- **Agentic RAG**：把检索流程交给"可控循环"——Agent 自主决定：是否需要检索？检索什么（哪些库/什么查询）？结果是否足够（要不要改写查询、换数据源、扩展检索）？是否需要多步迭代？

区别对照：

| 维度 | 传统 RAG | Agentic RAG |
|------|---------|-------------|
| 流程 | 固定线性 | 循环决策 |
| 检索 | 一次执行 | 可重试、可改写查询、可换源 |
| 回答质量 | 依赖单次召回 | 可自我评估，不足则再检索 |
| 复杂度/成本 | 低 | 高（多轮 LLM 调用） |
| 典型实现 | LCEL Chain | LangGraph（检索节点 + 评估节点 + 循环条件边） |

常见模式在 LangGraph 中直接落地：**Self-RAG**（模型自评检索片段相关性，决定是否用它）、**Corrective RAG / CRAG**（检索质量低则触发 Web 搜索或查询改写补救）、**Plan-and-Execute**（先规划检索步骤再执行）。

**关键点解析：**
- **面试官意图**：高级 RAG 考点，考察是否理解"从流水线到控制循环"的演进，以及能否用 LangGraph 实现。
- **怎么答**：先对比流程（线性 vs 循环），再点名 Self-RAG / CRAG 两种可执行模式在 LangGraph 中的节点/边实现。
- **深挖方向**：追问"Agentic RAG 的成本怎么控制"——给质检条件边设阈值、最多迭代 N 次（recursion_limit）、分级模型（便宜模型做检索质量判断）；追问"何时不该用 Agentic RAG"——流量大且查询稳定的场景用固定 RAG 更便宜更稳。

**常见扣分项：** 把 Agentic RAG 说成"RAG + 一个 Agent 调 LLM"的空话；不知道 Self-RAG / CRAG 的具体机制；意识不到成本翻倍。

---

### Q23：RAG 中模型"幻觉"如何检测与缓解？（结合 LangChain）

**参考回答：** 幻觉指模型生成与检索证据不符的内容。RAG 场景下缓解分层推进：

1. **检索层（减少"没找到/找到错"）**：混合检索（BM25 关键词 + 向量语义）+ Rerank 重排；多路召回；查询改写（HyQuery / 多查询）；元数据过滤；chunk 切分优化（按语义边界、重叠窗口）；
2. **生成层（约束输出）**：显式指令"仅基于提供的资料回答，资料不足请说明"；限制上下文窗口只放高相关片段；结构化输出校验；
3. **校验层（检测幻觉）**：**检索相关性打分**（片段与问题的语义相似度阈值，低分说明"没找到证据"→ 直接拒答或触发补救检索）；**回答与证据一致性校验**（LLM-as-judge 检查回答是否有事实依据/引用出处）；**实体级校验**（商品名、价格、规格等关键实体必须来自检索结果——电商 RAG 的经典做法）；
4. **兜底层**：拒答话术 + 低置信度转人工 + 日志留痕反哺评测集。

在 LangGraph 中实现"三层幻觉防控"通常就是：`检索节点 → 相关性评估节点（条件边：低分转重检索或拒答）→ 生成节点 → 一致性校验节点（不通过则重生成或标记）`。

**关键点解析：**
- **面试官意图**：幻觉是 RAG 面试必问题，考察系统性思维——能否从检索、生成、校验、兜底四个层面给出可执行方案。
- **怎么答**：按"检索-生成-校验-兜底"四层讲，每层给一个具体手段；能结合自己项目（如电商 API 商品实体校验）讲加分的假想例子。
- **深挖方向**：追问"模型会编造价格/规格怎么办"——实体抽取 + 与数据源比对 + 结构校验三重防线；追问"校验成本"——只在关键链路做 LLM 校验，低风险查询走规则。

**常见扣分项：** 只会说"prompt 里写'不要编造'"；不分层；说不出可观测/可评测的闭环（幻觉率应该进评测集量化）。

---

### Q24：什么是混合检索与 Rerank？LangChain 如何实现？

**参考回答：**

- **混合检索（Hybrid Search）**：关键词检索（BM25，精确匹配、擅长专有名词/编号）与向量检索（语义相似、擅长同义改写）组合，多路召回合并——解决"关键词查得准查不全、向量查得全查不准"的互补问题；合并后常用 RRF（Reciprocal Rank Fusion 倒数排名融合）或加权打分；
- **Rerank（重排序）**：召回后对候选片段用更精细的打分模型（Cross-Encoder，如 bge-reranker）重新排序，把"语义相关"提升为"与问题真正相关"——因为向量检索的双塔相似度并不能完美反映相关性，重排把证据力强的片段前置（LLM 对上下文前部更敏感）；
- **工程原则**：**召回层追求召回率（多而全），重排层追求精度（精而准）**，两阶段解耦、各管一摊。

LangChain 实现：组装 `EnsembleRetriever`（BM25Retriever + VectorStoreRetriever，weights 加权）→ 结果给 `ContextualCompressionRetriever`（内部用 Cross-Encoder Rerank 压缩），即可"先混合召回、再 Rerank"。

**关键点解析：**
- **面试官意图**：RAG 检索优化核心考点（字节/阿里高频），考察是否掌握"召回-重排"两阶段范式。
- **怎么答**：一句话定义各是什么 → 讲互补性与 RRF 融合 → 用"召回求全、重排求精"收口 → 给 LangChain 组装示例。
- **深挖方向**：追问"什么时候必须 Rerank"——top-k 召回 50 条但上下文只能放 5 条，必须重排精选；追问"Rerank 模型选型"——Cross-Encoder 比双塔 Bi-Encoder 准，但推理慢，小候选集可接受。

**常见扣分项：** 混滑混合检索与 Rerank（前者解决召回、后者解决重排）；不知道 RRF；没有"两阶段解耦"意识。

---

### Q25：LangChain 的 Callbacks / Handler 机制有什么用？

**参考回答：** Callbacks（回调）是 LangChain 的"可观测点系统"：在链/模型的各个生命周期节点（`on_llm_start`、`on_llm_end`、`on_chain_start`、`on_tool_start`、`on_retriever_start` 等）挂载处理器，用于：

1. **日志与调试**：打印/记录每一步出入参，快速定位"问题出在取参还是生成"；
2. **指标采集**：记录延迟、token 数、工具调用次数，导出到监控系统；
3. **横切逻辑**：限流、超时、审计、（自定义需求如"每次调用模型前检查配额"）；
4. **与 LangSmith 衔接**：LangChain 回调是 LangSmith 自动追踪的底层机制——设置环境变量后无需改业务代码即获得全链路 trace。

现代实践中，**首选 LangSmith 自动追踪**（零侵入），Callbacks 用于自定义非追踪场景或深度控制（如自定义 `BaseCallbackHandler` 接自己的监控平台）。

**关键点解析：**
- **面试官意图**：考察"生产可观测"意识，以及在自动追踪流行后你能否说出回调的定位变化。
- **怎么答**：讲清回调事件点 + 四大用途 + "被 LangSmith 自动追踪取代为默认，回调保留给自定义场景"的演进判断。
- **深挖方向**：追问"RunnableConfig 的 callbacks 参数与全局 handler 区别"——链级配置可控制单个调用的回调，全局 handler 全链路生效；追问"如何把自定义指标送进 LangSmith"——`langsmith.run_helpers.traceable` 或在节点内写 `client` 上报。

**常见扣分项：** 以为回调 = LangSmith（两者是"机制"与"产品"关系）；说不出事件点名称；不知道 callbacks 通过 RunnableConfig 传递。

---

### Q26：如何调试一个"本地正常、生产失败"的 LangChain 应用？

**参考回答：** 按"可观测数据先行的分层排查"思路：

1. **先看 Trace（LangSmith / 自建追踪）**：定位失败发生在哪一步——是模型调用（超时/限流/参数非法）、工具执行（外部 API 报错）、还是解析器（输出格式不符）；生产与本地差异 90% 能从 trace 找到；
2. **核对环境差异**：模型供应商 key/配额、模型版本漂移（厂商悄悄换版本导致输出行为变化）、依赖版本（requirements 锁没锁）、向量库连接与数据（生产库数据是否同步）、并发（生产真并发触发此前单线程没暴露的竞态/资源耗尽）；
3. **检查超时与重试**：外部依赖无超时 → 挂起；无重试 → 偶发 5xx 直接失败；建议统一 `with_timeout` + `with_retry`；
4. **检查状态与记忆**：如果用了 Checkpointer，确认生产持久化（InMemory 多实例各存各的会"会话丢失"）；
5. **回归验证**：把失败输入沉淀为评测集用例（LangSmith Dataset），修复后回归，防止同类问题复发。

**关键点解析：**
- **面试官意图**：压轴实战题，考察"生产排障方法论"而非 API 知识。
- **怎么答**：以"trace 定位 → 环境差异 → 超时重试 → 状态持久化 → 评测回归"五步作答，体现完整闭环。
- **深挖方向**：追问无 LangSmith 怎么办——先自配 logging 全链路 + 统一 trace_id；追问"模型版本漂移如何提前发现"——定期在固定评测集上跑回归（LangSmith 的持续评估正解决此问题）。

**常见扣分项：** 上来就改代码；没有"数据先行"思路；不沉淀回归用例，同一 bug 反复踩。

---

## 第二章 · LangGraph（16 题）

> 覆盖：图模型、状态合并、持久化、HITL、多智能体、流式、生产设计。
> 2026 定位：**LangGraph 是 Agent 运行时，LangChain 是其上的 batteries-included 层**。

### Q27：LangGraph 是什么？和 LangChain 是什么关系？

**参考回答：** LangGraph 是一个**基于图的低层编排运行时**，用于构建有状态、多步骤的 LLM 应用：节点做工作、边决定下一步运行什么、一个类型化的 State 对象贯穿全程。当控制流不再是线性（需要循环、依赖模型输出的分支、跨步骤持久化、人工审批）时，LangGraph 比普通 Chain 更合适——**Chain 是一条直线，LangGraph 是一台状态机**。

与 LangChain 的关系（2026 正确定位）：
- **LangGraph 是运行时，LangChain 是上层"开箱即用"层**：LangChain 1.0 的高层 Agent（`create_agent`）就运行在 LangGraph 运行时之上；
- LangChain 提供模型抽象、工具、消息等组件（batteries），LangGraph 提供状态图执行引擎（runtime）；
- 二者是**分层关系而非竞争关系**——`AgentExecutor` 已弃用，新代码或走 `create_agent` 或手写 `StateGraph`。

**关键点解析：**
- **面试官意图**：考察是否能给出"分层"而非"对比"的准确认知——2026 面试的标准答案就是这句话。
- **怎么答**：先定义 LangGraph（图/状态/控制流），再讲"LangGraph 是 runtime、LangChain 是层"，点出 create_agent 跑在 LangGraph 上。
- **深挖方向**：追问"何时选 LangGraph 而非轻量 LCEL Chain"——需要循环/分支/持久化/审批时；纯单向流程用 Chain 更简单。

**常见扣分项：** 把两者说成互斥框架二选一；不知道 LangGraph 是 create_agent 的底层；说不出"状态机"这一本质。

---

### Q28：什么是 StateGraph？图模型的核心三要素是什么？

**参考回答：** StateGraph 是 LangGraph 的核心类，把应用建模为"状态 + 节点 + 边"三要素：

1. **状态（State）**：类型化的数据契约（TypedDict 或 Pydantic 模型），是在图中贯穿流转的唯一共享对象；节点读取它、返回**部分更新**，由运行时合并；
2. **节点（Node）**：执行单位——一个函数，签名 `node(state) -> dict`（只返回要更新的 key，即"增量"），必要时可接收 `config`（运行时信息 thread_id 等）；
3. **边（Edge）**：连接节点、决定执行顺序；`START` / `END` 是哨兵节点（入口/终止）；`add_conditional_edges` 支持按状态/模型输出动态路由（分支、循环回边）。

```python
from langgraph.graph import StateGraph, START, END
from typing import TypedDict, Annotated
import operator

class State(TypedDict):
    messages: Annotated[list, operator.add]

g = StateGraph(State)
g.add_node("agent", agent_node)
g.add_edge(START, "agent")
g.add_edge("agent", END)
app = g.compile()          # 编译后才是可执行 Runnable
```

**关键点解析：**
- **面试官意图**：图模型基础必考，考察能否讲清"节点返回增量、运行时合并"这一执行语义。
- **怎么答**：三要素逐一定义 + "节点只返回要改的 key"这个关键细节 + compile 步骤。
- **深挖方向**：追问编译（`.compile()`）做什么——校验图（可达性、悬空边）、挂载 Checkpointer、产出可执行 Runnable；追问 `MessageGraph`——旧版消息专用图，已被 `StateGraph + messages 字段 + add_messages` 取代。

**常见扣分项：** 把状态说得像全局变量（其实是显式契约）；不知道节点返回部分更新；跳过 compile 直接 invoke。

---

### Q29：什么是 Reducer？没有 Reducer 时并行分支写同一字段会发生什么？

**参考回答：** Reducer 是给状态字段定义的**合并函数**，决定"多个更新怎么写进同一字段"而不是默认的覆盖。典型实现：

```python
from typing import Annotated, TypedDict
import operator

class State(TypedDict):
    messages: Annotated[list, operator.add]   # 追加而非覆盖
    summary: str                               # 无 reducer，默认 last-write-wins
```

- 没有 Reducer 时，默认行为是 **last-write-wins（后写覆盖）**；如果两个并行分支在同一 step 写同一个无 reducer 的字段，LangGraph 无法裁决先后，会**直接抛 `InvalidUpdateError`**（并发写冲突），而不是静默覆盖；
- 有 Reducer（如 `operator.add` 或 `add_messages`）时，多次写入确定性地合并（追加/拼接），并行安全。

**经典陷阱题**：消息列表必须配 `add_messages` reducer，否则多节点各自返回消息会互相覆盖、丢历史；并行 Map-Reduce 的收集字段必须配 reducer，否则并发写直接崩。

**关键点解析：**
- **面试官意图**：LangGraph 最高频陷阱题——正确答案是"并发写无 reducer 字段会报错"，不是"它会合并"。
- **怎么答**：先定义 Reducer（合并函数 vs 默认覆盖），再给并发写报错结论 + messages 字段必须配 add_messages 的实践。
- **深挖方向**：追问"自定义 Reducer"——`Annotated[field, reducer_fn]`，reducer_fn 接收 (current_value, new_values)；追问 add_messages 与 operator.add 区别——前者感知消息内容（按 id 去重、保留顺序），后者纯列表拼接。

**常见扣分项：** 答"会自动合并"；不知道报错类型 InvalidUpdateError；不知道消息字段必须用 add_messages。

---

### Q30：条件边（Conditional Edge）与普通边有什么区别？如何实现路由？

**参考回答：**

- **普通边**：`add_edge("A", "B")`，A 执行完必然到 B，静态无决策；
- **条件边**：`add_conditional_edges("A", router, mapping)`，A 执行后运行 `router(state)` 函数，返回**目标节点名**，实现按状态/模型输出动态路由（分支、回边循环）。mapping 是返回名→节点名/END 的映射。

```python
def route(state: State) -> str:
    last = state["messages"][-1]
    return "tools" if last.tool_calls else "respond"   # 有工具调用则执行工具，否则直接回答

g.add_conditional_edges(
    "agent",
    route,
    {"tools": "tools", "respond": "respond"},   # 也可省略 mapping，返回名直接作为节点名
)
g.add_edge("tools", "agent")   # 回边形成循环
```

**应用场景**：ReAct 循环（有无 tool_calls → 工具/回答）、质量分路由（分低重生成、达标结束）、查询改写决策等。预置的 `tools_condition` 就是"最后一条消息是否有 tool_calls"的标准路由。

**关键点解析：**
- **面试官意图**：图编排核心能力，考察能否干净地写出路由函数 + 分支映射 + 回边。
- **怎么答**：对比静态/动态 → 给 route 函数 + add_conditional_edges 示例 → 点名回边是循环的关键。
- **深挖方向**：追问"条件边返回值的映射规则"——返回值先按 mapping 找目标节点，没给 mapping 时返回值即节点名；追问"多条件分支"——路由函数返回不同值即可。

**常见扣分项：** 忘了回边（tools 不连回 agent 循环断掉）；mapping 与返回值不匹配报 KeyError；不知道 tools_condition 预置路由。

---

### Q31：什么是 Checkpointer？为什么需要 thread_id？

**参考回答：** Checkpointer（检查点）在图的**每一步执行后保存完整状态快照**，这是 LangGraph 实现暂停、恢复、崩溃恢复与 Human-in-the-Loop 的基础：

```python
from langgraph.checkpoint.memory import InMemorySaver

app = g.compile(checkpointer=InMemorySaver())
app.invoke(inp, config={"configurable": {"thread_id": "user-42"}})
```

**thread_id** 是会话作用域键（scope key）：同一用户会话的所有 checkpoint 共享一个 thread_id，恢复时传入同一 id，运行时加载对应会话历史。没有 thread_id，运行时无法知道恢复哪次运行的状态——它是"对话身份"，不是可选项。

**演进要点**：线程内短记忆（Checkpointer，会话状态）+ 跨线程长记忆（Store）是两套系统，前者回答"这次运行走到哪了"，后者回答"跨所有会话我们记住了这个用户的什么"。生产用持久化 Checkpointer（PostgresSaver / SqliteSaver），InMemory 仅测试用。

**关键点解析：**
- **面试官意图**：持久化是 LangGraph "挣工资"的功能，必考；考察是否理解 thread_id 的语义而不是机械背参数。
- **怎么答**：Checkpointer 保存每步快照 → thread_id 是会话作用域键 → 恢复时传同一 id → 生产必须持久化。
- **深挖方向**：追问 Checkpointer vs Store 的区别——短记忆 vs 长记忆、线程内 vs 跨线程、状态快照 vs KV 命名空间存储，讲混是常见致命错误。

**常见扣分项：** 忘记传 thread_id 导致"多轮对话互不知情"；不知道恢复时 `invoke` 传 `None` 表示续跑；把 Checkpointer 当长期用户记忆用（该用 Store）。

---

### Q32：Checkpointer（线程内）与 Store（跨线程长期记忆）有什么区别？

**参考回答：**

| 维度 | Checkpointer（检查点） | Store（存储） |
|------|----------------------|--------------|
| 记忆类型 | 短记忆（短期） | 长记忆（长期） |
| 作用域 | 线程内（thread_id 限定） | 跨线程（命名空间，常按用户） |
| 内容 | 运行中完整状态快照（每步保存） | 持久化的 KV 事实（键值 + 命名空间） |
| 回答的问题 | "这次运行到哪了？" | "这个用户跨会话记住了什么？" |
| 典型场景 | 恢复对话、暂停/继续、HITL | 用户偏好、长期画像、跨会话知识 |
| 实现 | MemorySaver / PostgresSaver / SqliteSaver | InMemoryStore / PostgresStore |

**工程意义**：LangGraph 设计上区分"会话状态恢复"（checkpoint）与"组织记忆"（store）两套持久化——把用户长期偏好塞进 checkpoint 是常见反模式（会话一多各存一份、无法共享）；正确做法是 checkpointer 管状态、store 管记忆，两者配合。

**关键点解析：**
- **面试官意图**：高频混淆点，考察记忆架构的清晰度——能分清两套系统的候选人非常少。
- **怎么答**：用"短/长、线程内/跨线程、状态/事实"三组对立讲清，各给一个例子。
- **深挖方向**：追问 Store 的命名空间——`BaseStore` 的 `(namespace, key)` 两级寻址，namespace 常用 `("users", user_id)`；追问持久化选型——生产 PostgresStore。

**常见扣分项：** 把两者当同一个东西；用 checkpoint 存长期记忆；说不清 namespace 寻址。

---

### Q33：如何实现 Human-in-the-Loop（人在回路）？interrupt 与 Command 怎么配合？

**参考回答：** HITL（人工审批/介入）是让 Agent 在关键动作前暂停等待人工确认的架构模式，LangGraph 通过 `interrupt()` + `Command(resume=...)` 实现：

```python
from langgraph.types import interrupt, Command

def approval_node(state):
    decision = interrupt({"action": state["proposed_action"]})   # 暂停并抛出载荷给外部
    return {"approved": decision == "yes"}

# 第一次调用：图在 interrupt 处暂停（必须有 checkpointer）
app.invoke(inp, config={"configurable": {"thread_id": "t1"}})

# 人工审查后，携带决策恢复执行
app.invoke(Command(resume="yes"), config={"configurable": {"thread_id": "t1"}})
```

关键点：
1. **必须配 Checkpointer**：interrupt 靠保存状态实现暂停/恢复，无 checkpointer 无法挂起；
2. **恢复用 Command(resume=...)**：恢复后节点会**从节点顶部重新执行**，interrupt() 调用处返回 resume 值继续——这是最容易踩的坑（节点内 interrupt 之前的副作用会重跑）；
3. **审批三态**：approve（继续执行）/ edit（人工修改状态或参数）/ reject（条件边路由到重生成/降级/终止）——通过读取 resume 值 + 条件边分支实现；
4. **blast-radius 原则**：只为"爆炸半径大"的动作加闸（花钱、对外发消息、写核心系统、删数据），读操作/可逆步骤不审批，否则体验慢且审批疲劳。

**关键点解析：**
- **面试官意图**：HITL 是"敢不敢让 Agent 上生产"的判题，考察对 interrupt 机制细节（含坑）的掌握。
- **怎么答**：给 interrupt/Command 最小示例 + 三个关键点（必须 checkpoint、节点顶部重跑、三态处理）+ blast-radius 选闸原则。
- **深挖方向**：追问"让人类编辑计划再继续"——interrupt 载荷携带计划，人工返回编辑版写回 state，或 `update_state` 直接改 checkpoint；追问旧式静态断点与 interrupt 的区别——1.0 统一为 interrupt 动态模式。

**常见扣分项：** 忘了必须有 checkpointer；答复"节点从断点那行继续"（实际是从节点顶部重跑）；审批只有 yes/no 没有 reject 分支。

---

### Q34：LangGraph 如何实现崩溃恢复？恢复时如何处理副作用重复？

**参考回答：**

**崩溃恢复**：使用持久化 Checkpointer（PostgresSaver 等），崩溃后只需用**同一 thread_id + 无新输入**重新 invoke，运行时加载最近 checkpoint，从"已完成节点之后的下一个待执行节点"继续——不会重放已完成节点：

```python
app.invoke(None, config={"configurable": {"thread_id": "user-42"}})   # None = 从保存状态续跑
```

**副作用幂等（关键）**：checkpoint 保证"状态可恢复"，但**不保证"外部副作用恰好一次"**——如果节点做了真实动作（扣款、发邮件、写行）后在 checkpoint 前崩溃，恢复时该节点会重跑，副作用重复。解法：
1. **幂等键**：下游系统按 `(thread_id, step, action)` 稳定键去重（先查"这事做过没"再做）;
2. **动作后置 checkpoint**：尽量让副作用发生点后有足够的持久化保障；
3. **先检查后执行**：外部操作前查询状态是否已完成该步骤。

**关键点解析：**
- **面试官意图**：高级生产题（senior 信号），考察"状态恢复 ≠ 恰好一次语义"的清醒认知。
- **怎么答**：恢复机制一句话（同 thread 续跑、不重放）+ 幂等三招重点展开。
- **深挖方向**：追问"如何验证恢复正确"——故障注入测试（kill 进程在任意节点间重启，核对状态与外部记录）；追问"分布式并发同 thread 怎么办"——线程锁/队列串行化同 thread 执行。

**常见扣分项：** 以为崩溃恢复自动保证幂等；不知道恢复不重放已完成节点；幂等只想到 Redis 锁而没考虑业务去重语义。

---

### Q35：LangGraph 有哪些流式模式（stream_mode）？各适用什么场景？

**参考回答：** `app.stream(input, config, stream_mode=...)` 控制图运行时发射什么内容：

| stream_mode | 发射内容 | 适用场景 |
|-------------|---------|---------|
| `values` | 每步之后的完整状态 | 调试、需要全量状态快照 |
| `updates` | 每步节点返回的增量（delta） | 可观测性、展示"哪个节点干了什么" |
| `messages` | LLM 逐 token 生成（及元数据） | 聊天 UI 打字机效果 |
| `custom` | 节点内显式 `get_stream_writer().write()` 的自定义事件 | 进度条、工具状态、想给前端看的特殊信号 |
| `debug` | 完整调试事件流 | 深度 debug |

- 可组合：`stream_mode=["values", "messages"]` 同时拿状态与 token；
- **安全原则**：面向浏览器/客户端只流 messages 或显式 custom 事件，**绝不流 values/updates**（会把内部状态、工具结果、密钥泄露给前端）。

**关键点解析：**
- **面试官意图**：考察流式落地与"边界安全"意识——选错模式是功能 bug，流错内容是安全事故。
- **怎么答**：模式表 + 每模式一个场景 + "浏览器只见 messages/custom、状态永远留服务端"这条安全红线。
- **深挖方向**：追问与 LCEL 流式的区别——LCEL `astream` 流 Runnable 链 token；LangGraph 流的是图运行时（含节点状态），粒度更丰富。

**常见扣分项：** 只用 values 一股脑推给前端；不知道 custom 模式存在；说不出各模式的差异本质（状态 vs 增量 vs token）。

---

### Q36：如何用 Send 实现 Map-Reduce 并行（扇出/汇聚）？

**参考回答：** `Send` 让一个节点**动态分发并行作业**：节点返回 `Send("worker", payload)` 列表，运行时为每个 payload 并行启动一个 worker 副本；配合结果字段的 Reducer（如 `operator.add`）收集各 worker 输出，实现"扇出执行 + 汇聚合并"：

```python
from langgraph.types import Send

def fan_out(state: State):                 # 扇出节点：按 items 并发派发
    return [Send("worker", {"item": x}) for x in state["items"]]

def worker(state: State):                  # 每个 item 一个 worker 副本
    result = process(state["item"])
    return {"results": [result]}           # 结果用 reducer 追加，避免并发写冲突

def aggregate(state: State):               # 汇聚节点：全部 worker 完成后执行
    return {"output": summarize(state["results"])}
```

关键点：
1. **worker 必须配 Reducer 收集字段**：并发 worker 同时写 `results`，无 reducer 直接 InvalidUpdateError（见 Q29）；
2. **Send 是动态扇出**：worker 数量由运行期数据决定（不同于静态并行节点）；
3. **适用**：批量文档处理、多路检索、子任务并行；注意并发量与 rate limit 平衡。

**关键点解析：**
- **面试官意图**：考察分布式思维的图化——能否用 Send + Reducer 表达 map-reduce。
- **怎么答**：三节点结构（fan_out → worker → aggregate）+ "Send 动态扇出、Reducer 汇聚"一句话点题。
- **深挖方向**：追问与 `parallel` 工具的区别——`Send` 是在图结构内、数量动态、逐个 Send 对象；手写 asyncio.gather 则脱离图的可恢复性；追问单 worker 失败——异常由运行时捕获，可配重试。

**常见扣分项：** 忘了结果字段配 reducer；不知道 Send 是运行期动态分发；把 Send 和普通边混淆。

---

### Q37：多智能体协作有哪些拓扑？如何选型？

**参考回答：** LangGraph 多智能体（Multi-Agent）常见三种拓扑：

1. **Supervisor（监督者）**：一个"路由型"主 Agent 掌控全局，把子任务委派给各 worker Agent，汇总结果——**集中式、易推理、易调试**，默认首选；
2. **Swarm（群集/移交式）**：Agent 之间**对等移交**（peer-to-peer handoff），没有中央老板，用 `Command(goto=...)` 直接跳转到另一个 Agent——灵活但难追踪，调试成本高；
3. **Hierarchical（层级式）**：监督者的监督者（supervisor of supervisors），用于大规模按团队拆解的系统——结构清晰但编排复杂度高。

实现机制：每个 Agent 是图中的一个**节点或子图**，交接用边或 `Command(goto=...)`；通信用共享状态（shared-state，LangGraph 原生，需 reducer 防写冲突）或消息传递（隔离性强、side-effect 少）。

**选型判断**（关键）：**默认单 Agent + 好工具列表，够用就别上多智能体**——多智能体成倍增加成本与故障面。只有出现这些真实原因才升级：上下文窗口被工具列表撑爆、领域真的需要不同 prompt/模型、子任务确实可并行独立执行。

**关键点解析：**
- **面试官意图**：考察"反多智能体崇拜"的工程克制 + 拓扑认知——最强答案开头往往是"通常一个 Agent 就够了"。
- **怎么答**：拓扑三选一对比 + "单 Agent 优先、明确理由才升级"的判断标准。
- **深挖方向**：追问"Supervisor 与 Swarm 何时互换"——路由 Agent 成为瓶颈或工具过多时考虑化为多个；追问共享状态 vs 消息传递的耦合权衡。

**常见扣分项：** 上来就设计多智能体；不知道 Command(goto) 与边的两种交接机制；说不出 supervisor 的瓶颈信号。

---

### Q38：子图（Subgraph）是什么？何时使用？

**参考回答：** 子图是把一个**已编译的图**作为节点嵌入父图：

```python
research_subgraph = research_graph.compile()     # 独立子图

def research_node(state):
    return subgraph_app.invoke(state)            # 在父节点中调用编译后的子图
```

1. **状态边界**：子图与父图状态键相同时直接合并；schema 不同时，用包装节点做"父状态 → 子图输入 → 子图结果 → 父状态"的映射；
2. **用途**：逻辑块自包含且可复用（研究子程序、多智能体系统中单个 Agent 的循环）、大图切成可读小图、多个团队各自维护；
3. **价值**：隔离复杂度 + 复用 + 独立测试——每个子图都能单独编译、单独调优。

**关键点解析：**
- **面试官意图**：考察图结构组织能力与"状态映射"这一易错边界。
- **怎么答**：子图=图里嵌图 → 讲清状态映射两种情形（键同直接合并 / 不同需包装映射）→ 用途。
- **深挖方向**：追问子图与独立编译的区别——子图仍经编译、可作独立应用；追问"子图状态映射错误"的典型症状——父状态出现子图内部键，或子图结果没回到父状态。

**常见扣分项：** 不知道子图是"编译后的图当节点"；忽略状态映射；把子图当成模块化代码组织（它是运行单元）。

---

### Q39：AgentExecutor 已弃用，如何迁移到 LangGraph？

**参考回答：** AgentExecutor（旧版 agent 执行器）弃用时间线：**维护期至 2026-12**，新项目禁止使用。两条迁移路径：

1. **快速路径 → `create_agent`（LangChain 1.0 标准）**：等价的 ReAct 工具循环，且自带 Checkpointing、流式、HITL、Time Travel，无需手写执行器：
```python
from langchain.agents import create_agent
agent = create_agent(model="...", tools=tools, system_prompt=...)
```
2. **定制路径 → 手写 StateGraph**：当旧逻辑有 AgentExecutor 表达不了的分支、重试、审批门时，降级到低层 `StateGraph` 手写节点/边（agent 节点 + tools 节点 + 循环回边）。工具与 prompt 原样保留，只替换执行层。

**迁移检查清单**：工具 schema 是否兼容（bind_tools 风格）、prompt 是否还是字符串模板（可迁到 ChatPromptTemplate）、记忆逻辑（内存 → Checkpointer）、回调逻辑（→ Middleware / LangSmith）。

**关键点解析：**
- **面试官意图**：2026 面试"版本时代感"考题，考察是否知道 AgentExecutor 已被淘汰及迁移方向。
- **怎么答**：点出弃用时间线 → 双路径（create_agent 快路 / StateGraph 定制路）→ 列出要适配的存量代码点。
- **深挖方向**：追问"为什么弃用"——AgentExecutor 是黑盒命令式循环，无法表达图的分支/循环/持久化；LangGraph 把它能表达的一切以显式图 + 状态 + checkpointer 重写。

**常见扣分项：** 还在推荐 AgentExecutor；只知道 create_react_agent 不知道 1.0 的 create_agent 已上位；迁移不考虑记忆与回调的适配。

---

### Q40：设计一个日请求 1 万次的 LangGraph 智能体系统（生产设计题）

**参考回答：** 先算账：1 万次/天 ≈ 平均 7 次/分钟，量级不大，**瓶颈在成本与状态管理而非吞吐**。给出架构决策：

1. **图结构**：单 Agent + 工具列表优先（检索 + 汇总工具），复杂分支再考虑子图；`create_agent` 起步，需要定制时降级 StateGraph；
2. **持久化**：`PostgresSaver` Checkpointer（跨实例共享会话状态、崩溃可恢复）+ PostgresStore（用户长记忆）；绝不用 InMemorySaver（多实例各自为政、重启丢状态）；
3. **状态瘦身**：大工具结果（抓取页面、大文档）**以引用入状态**（存对象存储/缓存 ID，下游按需取），因为每个 checkpoint 都序列化全量状态——胖状态 = 慢写入 + 大数据库；
4. **成本与失控防护**：每请求步数上限（recursion_limit/step cap）+ token 预算 + 超时；HITL 闸门只加在"爆炸半径大"的动作上；
5. **高可用**：应用实例放队列后（burst 不击穿模型 rate limit）；优雅退出前完成当前 step 的 checkpoint；
6. **可观测与回归**：全链路 LangSmith trace；生产失败 Trace 自动沉淀为评测集用例，持续回归防漂移。

**关键点解析：**
- **面试官意图**：架构题考"规模感 + 纪律"——先量化负载再定架构，拒绝过度设计。
- **怎么答**：负载估算一句 → 六大决策逐项一句（尤其持久化、状态瘦身、成本上限）→ 收尾"数学很简单，纪律在状态与预算"。
- **深挖方向**：追问"突发流量"——队列削峰 + 模型限流退避 + 降级响应；追问"跨实例并发同 thread"——同 thread 串行化处理，避免 checkpoint 覆盖。

**常见扣分项：** 按"高并发"堆架构（K8s/HPA 讲一堆）；不量化负载；状态里塞大对象；没有成本上限意识。

---

### Q41：ToolNode 与 tools_condition 是什么？如何用它搭建标准工具循环？

**参考回答：** LangGraph 提供两个预置组件，覆盖"模型→工具→回填→再模型"的 React 循环：

- **ToolNode(tools)**：预置节点——读取最后一条 AI 消息的 `tool_calls`，执行匹配的 Tool，把结果作为 **ToolMessage 追加回状态**（自动按 tool_call_id 配对，支持多工具并行调用）；
- **tools_condition**：预置路由函数——最后一条消息含 tool_calls 则去工具节点，否则结束（返回 END 方向）。

```python
from langgraph.prebuilt import ToolNode, tools_condition

g.add_node("agent", agent_node)
g.add_node("tools", ToolNode(tools))
g.add_conditional_edges("agent", tools_condition)   # 有调用→tools，无调用→END
g.add_edge("tools", "agent")                        # 回边：执行后回到模型
```

效益：不用手写"解析 tool_calls → 分发 → 拼 ToolMessage"的样板代码；一致性错误处理（工具异常捕获回填，模型可据此修正）。

**关键点解析：**
- **面试官意图**：考察是否知道标准预置循环（很多人在手写工具分发，重复造轮子）。
- **怎么答**：两个组件的职责一句话 + 三行图代码 + "自动配对 tool_call_id"的价值点。
- **深挖方向**：追问 ToolNode 的异常处理——默认捕获异常并把错误信息作为工具输出回填，保证循环不断；追问自定义分发——自己写函数节点替代 ToolNode 但需处理 tool_call_id。

**常见扣分项：** 手写工具分发循环；不知道 tools_condition 返回语义；忘加 `add_edge("tools", "agent")` 回边。

---

### Q42：LangGraph 如何做流式输出到前端而不泄露内部状态？

**参考回答：** 前端只暴露两种流：

1. **`stream_mode="messages"`**：只流 LLM 生成的 token 与元数据，实现打字机效果；
2. **`stream_mode="custom"`**：节点内用 `get_stream_writer()` 显式写自定义事件（进度、工具状态），由你决定前端能看到什么：

```python
from langgraph.config import get_stream_writer

def node(state):
    writer = get_stream_writer()
    writer({"type": "status", "message": "正在检索..."})   # 自定义可见事件
    ...
```

**红线**：绝不把 `stream_mode="values"/"updates"` 直接推给浏览器——那是完整状态/节点增量，内含内部 scratchpad、工具结果原文、甚至密钥。规则：**明确表达"用户该看什么"，而不是"把系统里有什么都发出去"**。

**关键点解析：**
- **面试官意图**：安全与 UX 双考察——多数候选人没想到流状态会泄露内部数据。
- **怎么答**：messages + custom 两种白名单流 + "绝不流 values/updates"红线 + 一个 custom 事件示例。
- **深挖方向**：追问 SSE 落地——FastAPI StreamingResponse 包 astream 事件，事件类型映射为 SSE event；追问鉴权——流式连接同样要校验会话身份。

**常见扣分项：** 用 values 流给前端；不知道 get_stream_writer 显式事件通道；不校验流式连接身份。

---

## 第三章 · LangSmith（15 题）

> 覆盖：平台定位、Tracing 概念、评测体系、Prompt 管理、反馈闭环、生产监控、选型对比。

### Q43：LangSmith 是什么？LangChain / LangGraph / LangSmith 三者是什么关系？

**参考回答：** LangSmith 是 LangChain 官方推出的 **AI 应用可观测性与评测平台**（商业 SaaS，也有私有部署），覆盖 LLM/Agent 应用全生命周期：**开发期调试（Tracing）→ 评测期量化（Evaluation）→ 生产期监控（Monitoring）**。

三者关系——"铁三角"：
- **LangChain**：应用构建框架（组件 + 编排），负责"造"；
- **LangGraph**：Agent 运行时（图 + 状态 + 持久化），负责"跑"；
- **LangSmith**：可观测与评测平台，负责"看 + 量"——追踪 LangChain/LangGraph 应用执行、用数据集评测质量、把生产反馈变评测数据、管理 Prompt 版本。

一句话：**LangChain 构建、LangGraph 编排、LangSmith 验证**。

对接成本极低：LangChain/LangGraph 集成 SDK 后设置 `LANGCHAIN_TRACING_V2=true` + API key 两个环境变量即自动上报全链路 trace，业务代码零侵入。

**关键点解析：**
- **面试官意图**：考"生态全貌"题，看候选人是否理解三件套分工而非只当三个名词。
- **怎么答**：定位一句话（可观测+评测平台）→ 造/跑/看分工 → 环境变量零侵入接入。
- **深挖方向**：追问"没有 LangSmith 前怎么调试 LLM 应用"——print + 手动日志 + 自建表，对比体现 LangSmith 的价值密度；追问 LangSmith Deployment——2025-10 由 LangGraph Platform 更名，做生产部署托管。

**常见扣分项：** 不知道 LangSmith 是什么；把三个名字当同义词；只会说"追踪工具"而说不出评测与 Prompt 管理。

---

### Q44：LangSmith 的 Tracing 是什么？Run / Trace / Thread 分别是什么？

**参考回答：**

- **Trace（追踪）**：单次应用执行的完整记录（一棵执行树），树的根是用户请求，向下展开每一步；
- **Run（运行/步骤）**：Trace 中的一个执行单元——一次模型调用、一次工具执行、一个链/图节点，构成树的节点；每条 Run 记录输入输出、token 数、延迟、错误、元数据；
- **Thread（线程/会话）**：跨多次调用的关联维度——同一用户会话的多次 Trace 用同一 thread 聚合，可按 thread 查看多轮对话的完整历程。

LangSmith 把三者组织为：**Thread（会话）→ Trace（单次执行）→ Run（执行步骤）** 的层级；UI 中瀑布视图（waterfall view）展开 trace，可点进任意 run 看输入输出、耗时、token 成本——这是调试复杂 Agent 的核心视图。

**关键点解析：**
- **面试官意图**：Tracing 概念是 LangSmith 地基，考察是否理解层级关系而非只见过截图。
- **怎么答**：三级层级（Thread→Trace→Run）+ 每个的概念与记录内容 + 瀑布视图调试价值。
- **深挖方向**：追问"如何自定义 trace 内容"——`langsmith.run_helpers.traceable` 装饰普通函数自动成 run，或用 context manager 手动包；追问跨服务追踪——trace 可聚合多服务的 run（父进程/子进程同 trace_id）。

**常见扣分项：** 分不清 Trace 与 Run；把 Thread 理解为线程；不知道 traceable 可接入非 LangChain 代码。

---

### Q45：如何接入 LangSmith？需要配置什么？

**参考回答：** 三步接入（Python）：

1. **创建账号与项目**：LangSmith UI 创建 Project，拿到 API Key；
2. **安装 SDK**：`pip install langsmith`（LangChain 应用通常已带）；
3. **环境变量配置**：
```bash
export LANGCHAIN_TRACING_V2=true
export LANGCHAIN_API_KEY="lsv2_..."
export LANGCHAIN_PROJECT="my-rag-app"    # 可选，指定项目名
```
- LangChain / LangGraph 应用设置后**自动上报** trace，业务代码零侵入（底层走 Callbacks 机制）；
- 非 LangChain 代码（纯函数、FastAPI 接口）用 `traceable` 装饰器或 `client` API 手动上报；
- 生产接入注意：**API key 走密钥管理**（env / Vault / CI Secret），不上库；成本考虑——trace 有调用量配额与费用，可采样上报（`LANGCHAIN_TRACING_SAMPLING_RATE`）。

**关键点解析：**
- **面试官意图**：落地题，考察是否真的配过——环境变量名、项目归属、采样、密钥安全都会问。
- **怎么答**：三步 + 两个关键环境变量 + traceable 补位纯函数 + 采样/密钥两个生产注意点。
- **深挖方向**：追问"没网/私有环境怎么办"——LangSmith 私有化部署（Docker）或自建链路，评估成本；追问采样如何保证数据代表性——按用户/请求类型分层采样。

**常见扣分项：** 只记得配过 key 不记得 TRACING_V2；把 key 硬编码进代码；不知道节流采样存在。

---

### Q46：LangSmith 的 Dataset 与 Experiment 是什么？评测流程是怎样的？

**参考回答：**

- **Dataset（数据集）**：一组测试样本（输入 + 期望输出/标签 + 元数据），是评测的"考卷"；可手工创建、从 trace 沉淀（出错样本一键入集）、批量导入；
- **Experiment（实验/评测运行）**：把**目标函数**（待测的链/Agent，或 prompt 版本）在 Dataset 上批量跑一遍，并用**评估器（Evaluator）**给每个样本打分的完整记录；UI 中可对比不同实验（换模型、换 prompt、换检索参数）的分数矩阵。

标准评测流程：
```
数据集 Dataset → 目标函数 Target（chain/agent）→ 批量运行
→ 评估器 Evaluators（代码规则 / LLM-as-Judge / 人工）
→ Experiment 报告（平均分 + 逐样本明细）→ 横向对比迭代
```
评估器三态：**代码型**（确定性规则，如"回答是否包含实体/JSON 是否合法"）、**LLM-as-Judge**（语义/事实评分）、**人工评估**（Annotation）。

**关键点解析：**
- **面试官意图**：评测体系是 LangSmith 核心价值，考察是否理解"考卷-考生-判卷"模型。
- **怎么答**：Dataset=考卷、Target=考生、Evaluator=判卷、Experiment=成绩单，用比喻一句话讲清再给流程链。
- **深挖方向**：追问"评测集从哪来"——生产 trace 的负反馈样本回流入集（关键闭环，见 Q50/51）；追问"多少样本够"——起步 50-200 条覆盖主要分支与边界即可 CI 回归。

**常见扣分项：** 分不清 Dataset 与 Experiment；评估器只想到 LLM-as-Judge 不知道代码规则评估器；没有"生产数据回流评测集"的闭环意识。

---

### Q47：什么是 LLM-as-Judge？如何定义与校准？

**参考回答：** LLM-as-Judge（LLM 裁判）是用一个大模型评分另一个模型的输出——在**无标准答案**或语义评价场景（相关性、事实性、有用性、风格）比代码规则更灵活：

- 定义方式：UI 中配置评估 Prompt（打分标准、量纲、JSON 输出要求）+ 指定裁判模型 + 可加 few-shot 示例；离线评测常用 `llm-as-judge` 配置，生产实时监控可挂 `auto-eval`；
- 常见维度：相关性、忠实度/事实性（对照检索证据）、有用性、无害性、格式合规；
- **校准方法**：① 裁判 prompt 明确评分锚点与禁止项；② 提供 few-shot 对齐；③ **与人类反馈对齐**——LangSmith 支持把人工标注结果回灌到 judge，用偏好数据微调/调整裁判 prompt（aligning LLM-as-Judge with human preferences）；④ 定期校验裁判自身漂移。

**局限**：裁判与被评模型同圈会产生系统性偏差（self-bias）；裁判成本与延迟；复杂任务裁判本身不可靠——常需"规则评估器 + 裁判 + 人工抽样复核"三层。

**关键点解析：**
- **面试官意图**：评测方法论核心考点，考察是否理解裁判的"校准"而不只是会用。
- **怎么答**：定义（大模型评分）→ 适用场景 → 校准四法（锚点/few-shot/人类反馈/防漂移）→ 局限与三层互补。
- **深挖方向**：追问"裁判 prompt 怎么写好"——评分锚点 + 逐条理由 + JSON 输出 + 防范围坍缩（禁止全给满分）；追问"哪些场景 LLM 裁判无效"——代码对错、事实核对类应优先代码/人工。

**常见扣分项：** 不知道校验必要；以为 LLM-as-Judge 万能；不设评分标准导致分数不可复现。

---

### Q48：如何写一个自定义评估器（代码型）？和 LLM-as-Judge 怎么配合？

**参考回答：** 自定义（代码型）评估器是确定性函数——接收 `run`（目标输出）与可选的 `example`（期望输出），返回分数与解释：

```python
from langsmith.schemas import Run, Example
from langsmith import evaluate

def json_validity(run: Run, example: Example | None = None) -> dict:
    try:
        json.loads(run.outputs["output"])
        return {"key": "json_valid", "score": 1}
    except Exception:
        return {"key": "json_valid", "score": 0, "comment": "输出不是合法 JSON"}

results = evaluate(predict, data="my_dataset", evaluators=[json_validity, llm_judge])
```

配合使用：
- **代码型**：确定性校验（格式、必含实体、长度、正则、API 契约）——快、可复现、零成本；
- **LLM-as-Judge**：语义质量（相关性/事实性/有用性）——灵活但慢且需校准；
- **最佳实践**：一个实验挂多个评估器（`evaluators=[代码规则, LLM裁判, 自定义RAG评估器]`），规则层快速兜底 + 语义层综合评价；RAG 场景官方预置 `rag_*` 评估器族（忠实度、相关性、上下文精确率等）。

**关键点解析：**
- **面试官意图**：考察能否写"第 10 行代码"而非只会点 UI——评估器签名、返回结构、挂载是必考。
- **怎么答**：一段 15 行可运行代码（Run/Example 签名 + score/comment 返回）+ 两类评估器分工 + 多评估器组合。
- **深挖方向**：追问评估器返回结构——`score`（0-1 或 bool）、`key`（指标名，聚合用）、`comment`（可追溯）；追问跑批量评估的异步——evaluate 默认并行跑样本，注意目标函数的并发安全。

**常见扣分项：** 不知道 Run/Example 签名；返回结构缺 key/score；只挂一个评估器不看多维度。

---

### Q49：Prompt Hub / Prompt 版本管理有什么价值？和生产部署如何结合？

**参考回答：** LangSmith Prompt Hub 是**提示词的中心化版本管理仓库**，价值：

1. **版本化与回滚**：每个 prompt 有 commits / version 标签，A/B 对比不同版本，出问题秒回滚；
2. **团队协作**：提示词从"各人代码里的一行字符串"变成"团队共享的可评测资产"，可评论、可评星、可共享；
3. **环境化部署**：开发/测试/生产用同一 repo 不同版本 tag，配合 CI 评审（prompt 变更走 review-as-code）；
4. **绑定评测**：prompt 版本与评测结果、trace 关联，选型有数据支撑（哪个版本分数高）;
5. **代码侧拉取**：运行时按 tag 拉取 prompt（`pull_prompt`），支持热更新（不重启服务改 prompt——生产常用）。

```python
from langchain import hub
prompt = hub.pull("user/qa-system:prod")   # 生产 tag
```

**生产结合建议**：代码内保留默认 prompt 作兜底 → 优先从 Hub 拉 tag → 变更流程：先建新版本 → 跑评测对比 → 通过后切 tag → 监控回归。

**关键点解析：**
- **面试官意图**：考察"提示词也是需要工程化管理的资产"的意识——how to 之外看工程观。
- **怎么答**：版本化/协作/热更新/绑评测四个价值 + "先评测后切 tag"的发布纪律。
- **深挖方向**：追问"第三方视角"——LangSmith 是商业平台，开源替代可用 Langfuse prompt 管理 + 自建；追问 Prompt 中包含敏感词的公司合规——脱敏后入库。

**常见扣分项：** 认为 prompt 就是代码里的字符串；不知道热更新能力；发布不评测直接改线上。

---

### Q50：Annotation Queue 是什么？如何形成"生产反馈 → 评测数据"的闭环？

**参考回答：** Annotation Queue（标注队列）是 LangSmith 的**人工评审工作流**：把生产 trace 按规则筛选后送入队列，分派给领域专家对特定 run 打标（评分、评论、结构化反馈），用于人工评测、坏样本沉淀：

典型闭环（生产 → 标注 → 数据 → 评测）：
```
生产 trace → Automation 规则筛选（如"负反馈/低分/异常"）
→ 送入 Annotation Queue → 人工标注（打分/纠错/分类）
→ 标注结果沉淀为 Dataset 用例 → 定期跑 Experiment 评测
→ 驱动 prompt/模型/检索改进
```
配合机制：**Feedback（反馈）** 把任何 run 挂上结构化反馈（用户点赞/点踩、评分、评论），一条 `client.create_feedback(run_id, key, score)` 即完成；负反馈样本自动入队评审，评审通过即入评测集——这就是"让生产数据持续滋养评测集"的飞轮。

**关键点解析：**
- **面试官意图**：考察是否理解"评测不是一次性活动，而是生产闭环"——这是 LangSmith 与朴素追踪工具的分水岭。
- **怎么答**：队列定位（人工评审工作流）→ 六步闭环 → Feedback API 一句话（反馈 = 数据飞轮的燃料）。
- **深挖方向**：追问 Automation 怎么筛——按分数阈值/关键词/异常模式入队（见 Q51）；追问标注人力成本——只标异常/低置信样本，分层抽样。

**常见扣分项：** 只把 LangSmith 当"查看日志"工具；没有数据回流意识；不知道 Feedback API。

---

### Q51：Automation（自动化规则）在 LangSmith 中能做什么？

**参考回答：** Automation 是 LangSmith 的**事件驱动规则引擎**：监听生产 trace 满足条件时触发动作，实现"零人工盯守"的可观测响应。常用规则与动作：

| 触发器（条件） | 动作（Actions） |
|---------------|----------------|
| 负反馈/低分（Feedback score < 阈值） | 加入 Annotation Queue（人工复核） |
| 错误率/延迟异常（p95 latency > 阈值） | 触发 Webhook（通知 Slack/钉钉/自建系统） |
| 特定关键词/实体出现（如敏感词） | 创建 Dataset 条目（沉淀评测数据） |
| 某模型/某 prompt 版本表现漂移 | 标注 Trace 标签、发告警 |
| 成本异常（单 run 超预算） | 通知团队、关闭高消耗调用 |

价值：告警 + 数据管道双合一——**把"失败"自动变成"待评审项 + 评测集素材 + 即时告警"**，是生产监控的自动化底座；可与自建告警系统通过 Webhook 打通。

**关键点解析：**
- **面试官意图**：考察可观测性的"自动化思维"——不只记录，还要触发行动。
- **怎么答**：规则 = 条件 + 动作的引擎 → 3-4 条高频规则示例 → "失败自动变告警 + 数据资产"的价值。
- **深挖方向**：追问"如何避免告警疲劳"——分级阈值、聚合、冷却窗口；追问成本控制——单 run 预算 + 每日配额规则。

**常见扣分项：** 只谈人工看 trace；不知道 Webhook 打通自建系统；规则设计无去重/冷却。

---

### Q52：LangSmith 生产监控主要看哪些指标？如何发现与定位线上问题？

**参考回答：** 生产监控按"业务质量 + 运行健康 + 成本"三层看：

1. **质量指标**：在线评估（auto-eval，如 LLM-as-Judge 实时打分）、用户反馈（点赞/点踩）、错误样本率——发现"回答变差"这类隐性回归；
2. **运行健康**：错误率、异常类型分布、p95/p99 延迟、令牌超限、工具失败率——发现"服务挂了/慢了"这类显性问题；
3. **成本指标**：每 run 价格、token 消耗趋势、单用户成本异常（防滥用）、模型切换的 ROI 对比。

**定位方法**（Trace 驱动）：高延迟/故障 run → 瀑布视图展开 → 定位是模型调用慢、检索慢还是工具慢 → 看具体 run 的输入输出找根因 → 修复 → 样本入评测集回归。

**结合上下文**：监控阈值 + Automation 告警 + Annotation 人工复核三者配合，形成"自动发现 → 人工确认 → 数据回流"的运维闭环。

**关键点解析：**
- **面试官意图**：考察"生产可观测的完整度"——指标分层与排障方法论比记 UI 重要。
- **怎么答**：三层指标表 + trace 驱动的定位五步 + 自动发现闭环。
- **深挖方向**：追问"在线评估成本高怎么办"——抽样 + 便宜裁判模型 + 低风险场景代码规则替补；追问模型漂移如何早期发现——固定评测集定期回归 + 生产分布监控。

**常见扣分项：** 只看错误率不看质量；不会从 trace 分层定位；没有成本监控。

---

### Q53：LangSmith 与 Langfuse 如何选型？（对比题）

**参考回答：** LangSmith 与 Langfuse 都是 LLM 可观测/评测平台，核心差异在**生态绑定与部署模式**：

| 维度 | LangSmith | Langfuse |
|------|-----------|----------|
| 出品方 | LangChain 官方 | 开源社区商业公司 |
| 开源 | 开源 SDK，平台闭源 | **开源可自托管**（也可云） |
| LangChain 集成 | 零侵入原生（自动 trace） | 需适配（也支持 LangChain） |
| 功能覆盖 | 全生命周期：Tracing + 评测 + Prompt 管理 + 标注 + 部署 | 追踪 + 评测 + Prompt 管理（部署能力弱） |
| 供应商锁定 | 与 LangChain 生态深度绑定 | 框架无关，数据自主 |
| 私有化 | 支持私有化部署（商业） | 自托管免费 |

**选型建议**：
- 技术栈是 LangChain，接受供应商协同 → **LangSmith**：集成顺滑 + 评测体系最全 + 与 LangGraph 部署一体化；
- 多框架混用 / 数据主权要求 / 要开源免费自托管 → **Langfuse**（或自建）；
- 提醒：两者也互相学习（Langfuse 补评测、LangSmith 补开放），先想清"我要的是追踪、评测还是全生命周期"。

**关键点解析：**
- **面试官意图**：考察选型方法与权衡意识，而非背参数。
- **怎么答**：差异主轴（生态绑定 vs 开放/自托管）+ 功能面 + 按场景给结论。
- **深挖方向**：追问"自建 vs SaaS 的账怎么算"——数据量、合规、人力维护、功能滞后四因素；追问"评测数据能迁移吗"——数据集可导出，但 trace 与标注体系绑定平台，迁移成本高。

**常见扣分项：** 二元站队没有场景判断；不知道 Langfuse 可自托管；忽略供应商锁定风险这个关键变量。

---

### Q54：LangSmith 如何评测"多轮对话 / Agent 轨迹"这类复杂对象？

**参考回答：** 简单问答可看单输出，复杂 Agent 需要**轨迹级评测**，LangSmith 方案分三层：

1. **评估器（Evaluators）**：接收整个 `run`（含输入输出），可写：
   - **轨迹级 LLM-as-Judge**：把"完整对话/推理过程 + 最终回答"喂给裁判模型，按目标（完成了任务吗/用对工具了吗/路径高效吗）评分；
   - **代码规则**：断言关键步骤发生（必须调用了某工具）、终态条件（输出含合法 JSON）、步骤数上限（路径是否绕圈）；
2. **带三步评测**：Dataset 样本可含多轮输入序列与期望轨迹/终态；官方 Offline Evaluators 区分"运行级评估器"与"最后一步评估器"（可对照既定评分标准）；
3. **人工 Annotation**：复杂轨迹自动评分不可靠时，用标注队列人工审阅"轨迹是否正确、理由是否充分"。

**工程要点**：Agent 评测先定"成功标准"（任务达成、成本约束、安全约束），再选评估器组合；生产上优先自动规则 + 抽样人工复核。

**关键点解析：**
- **面试官意图**：Agent 应用如何评测是 2026 前沿考点，考察"目标定义 → 评估器 → 人工复核"的方法论。
- **怎么答**：三层（轨迹 Judge / 代码断言 / 人工标注）+ "先定义成功标准"的起点 + 抽样人工复核的务实收尾。
- **深挖方向**：追问"路径效率"——步骤数、token 成本作为打分维度；追问"多轮一致性问题"（前后矛盾）——会话级评估器读整个 thread。

**常见扣分项：** 只用末尾回答打分不看轨迹；不定义成功标准就写评估器；完全依赖自动评分无人工兜底。

---

### Q55：LangSmith 评测如何融入 CI/CD？（持续评测）

**参考回答：** 把"跑评测"变成流水线里的一个 gate，防止回归上线，LangSmith 支持：

1. **CI 跑 Experiment**：PR 提交 → CI 中 `langsmith.evaluate`（或 CLI）在固定 Dataset 跑目标函数 → 对比基准实验（baseline）；
2. **回归门禁（diff）**：比较新实验与 baseline 的分数（平均分、各指标、方差），**低于阈值则阻断合并**——把"模型输出质量"变成可自动验证的工程约束；
3. **按环境分级**：开发环境跑全量样本，生产发布前跑关键子集 + 人工看板确认；
4. **提示词/模型变更触发**：prompt 版本、模型 ID、检索参数变化自动触发对应实验，评估成本由变更频率决定；
5. **配合样本沉淀**：生产负反馈持续入 Dataset，评测集"活"起来，回归门禁覆盖真实坏例。

**实践提醒**：评测集要有版本（样本增删走 review）；门禁阈值先松后紧（避免频繁误杀）；LLM 裁判有随机性，用多次运行取均值 + 固定裁判模型/温度。

**关键点解析：**
- **面试官意图**：考察"AI 系统也要可重复质量门禁"的工程观——把 LLM 从'玄学'变'可回归'。
- **怎么答**：CI evaluate → 对比 baseline 设门禁 → 环境分级 → 活评测集，四步闭环。
- **深挖方向**：追问"LLM 随机性导致门禁抖动"——固定 seed/温度、样本加权、均值 ± 容差；追问"成本"——CI 全量评测一次几百次调用，按 PR 频率预算控制。

**常见扣分项：** 评测只发生在开发期不接 CI；门禁只有总分不看分项；没有样本版本管理。

---

### Q56：LangChain / LangGraph 项目里，如何用 LangSmith 定位一个"回答错误"的线上问题？

**参考回答：** 端到端排障示范（Trace-first）：

1. **收 trace**：找到用户反馈对应 Thread → 打开瀑布视图，定位"最后答案错"的具体 run；
2. **回看输入**：AIMessage 的上下文是否包含检索片段？用户问题是否被截断/误解析？
3. **三处归因**：
   - **检索问题**：召回片段是否相关、是否为空（空召回 → 模型只能编造）——看 retriever run 的 hits；
   - **生成问题**：prompt 是否被用户输入注入/系统指令是否被覆盖——看 system prompt 实际内容；
   - **校验问题**：检索相关性阈值是否放行低分片段（幻觉源头）——看评估/过滤节点输出；
4. **复现与修复**：把该样本加入 Dataset → 本地复现 → 修改（prompt / 检索 top-k / 增加过滤）→ 跑 Experiment 对比修正前后分数；
5. **闭环**：修复后 Automation 监控同类错误率回归，防复发。

**关键点解析：**
- **面试官意图**：综合应用题，考察能否把"Trace 数据 → 归因 → 修复 → 回归"完整走通——这是 LangSmith 评测 + 可观测的最终价值体现。
- **怎么答**：五步链（定位 → 看输入 → 三处归因 → 复现修复 → 回归闭环），示范一次真实排障叙事。
- **深挖方向**：追问"空召回的坑"——检索 top-k 过小/embedding 与查询不匹配/分块质量问题，Agentic RAG 的"重检索"补救（Q22）；追问如何防"prompt 注入"——输入隔离 + PIIMiddleware（Q9）+ 敏感动作审批。

**常见扣分项：** 不查 trace 直接猜；改了不回测；只有修复没有回归监控。

---

## 📌 附：三库速览对照表

| 库 | 定位 | 核心概念 | 面试高频 |
|----|------|---------|---------|
| LangChain | 应用构建框架 | ChatModel / Messages / LCEL(Runnable) / Tool / create_agent / Middleware | 模型抽象、LCEL、工具、1.0 新特性 |
| LangGraph | Agent 运行时 | StateGraph / Reducer / Checkpointer / interrupt / Send / 流式 | 状态合并、持久化、HITL、生产设计 |
| LangSmith | 可观测 + 评测 | Trace / Dataset / Experiment / LLM-as-Judge / Prompt Hub / Annotation | 评测体系、反馈闭环、选型对比 |

> 背诵速查见《LangChain_LangGraph_LangSmith_面试QA_背诵版.md》；教程见 `2-learning/stacks/14-langchain`（LangChain/LangGraph 教程）与 `2-learning/stacks/18-langsmith`（LangSmith 教程）。