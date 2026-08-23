# LangChain — 面试抽认卡

> 来源：`learn/14-langchain/05-interview/`

---

### Card 1: LCEL 核心接口
**维度**: 🔬深挖 | **难度**: ⭐⭐⭐

> **Q: LCEL（LangChain Expression Language）的 Runnable 接口提供了哪些核心方法？**

**A:** Runnable 接口提供 `invoke`（同步调用）、`ainvoke`（异步）、`batch`（批量）、`stream`（流式输出）、`astream`（异步流式）、`astream_log`（带日志的流式）。`|` 管道运算符将左侧 Runnable 的输出传入右侧 Runnable 的输入，基于类型签名自动匹配。`RunnablePassthrough` 透传输入，`RunnableParallel` 并行执行，`RunnableBranch` 条件分支，`RunnableLambda` 将普通函数包装为 Runnable。LCEL 惰性求值——构建 DAG 后调用 `invoke` 才真正执行。

---

### Card 2: Chain 类型
**维度**: 📝速记 | **难度**: ⭐⭐

> **Q: LangChain 中有哪些常见的 Chain 类型？LLMChain 为什么被弃用？**

**A:** 常见 Chain：LLMChain（Prompt + LLM + OutputParser，已弃用）、SimpleSequentialChain（单输入单输出，串行）、SequentialChain（多输入多输出，可指定映射关系）、RouterChain（按条件路由到不同 Chain）、LLMRequestsChain（LLM 决定请求的 URL 和参数）。LLMChain 被弃用是因为 LCEL 的 `prompt | model | output_parser` 更灵活、更简洁。推荐使用 LCEL 替代所有 Legacy Chain。

---

### Card 3: Memory 类型对比
**维度**: 📝速记 | **难度**: ⭐⭐

> **Q: LangChain 的几种 Memory 类型有什么区别？如何选择？**

**A:** ConversationBufferMemory：完整存储所有对话历史，适合短对话。ConversationSummaryMemory：LLM 总结历史，节省 Token，适合长对话（但总结可能丢失细节）。ConversationBufferWindowMemory：只保留最近 k 轮对话，控制 Token 长度。VectorStoreRetrieverMemory：按语义相似度检索最近相关的历史片段，适合海量历史。LCEL 集成：`RunnableWithMessageHistory` 包装原始链，传入 `get_session_history` 回调，每个 `session_id` 独立存储。

---

### Card 4: Tool 定义与使用
**维度**: 💻代码 | **难度**: ⭐⭐

> **Q: 如何在 LangChain 中定义 Tool？@tool 装饰器如何工作？**

**A:** `@tool` 装饰器将 Python 函数快速转为 Tool 对象，自动解析函数签名和 docstring 作为 name 和 description。`Tool.from_function(func, name="my_tool", description="...", args_schema=MyPydanticModel)` 更精细控制。name 和 description 对 LLM 至关重要——LLM 据此决定调用哪个 Tool，description 越清晰准确率越高。Tool 的 `return_direct=True` 直接返回结果给用户（不走 LLM 加工）。`handle_tool_error` 配置工具执行失败时的处理。

---

### Card 5: Agent 循环机制
**维度**: 🔬深挖 | **难度**: ⭐⭐⭐

> **Q: ReAct Agent 的完整循环流程是什么？AgentExecutor 如何工作？**

**A:** ReAct 循环：Thought（思考当前情况，决定下一步）→ Action（选择 Tool 并生成参数）→ Action Input（执行 Tool）→ Observation（获取 Tool 执行结果）→ 循环或终止（判断是否得到最终答案）。AgentExecutor 负责驱动此循环：调用 Agent 生成 Action → 执行 Tool → 收集 Observation → 构造新的 Prompt → 再次调用 Agent → 判断是否终止。`max_iterations`（默认 15）防止无限循环，`early_stopping_method` 控制超时行为（`generate` 直接生成或 `graceful` 返回现有结果）。

---

### Card 6: LangGraph vs Chain
**维度**: 🔬深挖 | **难度**: ⭐⭐⭐

> **Q: LangGraph 和普通 Chain 的核心区别是什么？什么场景下用 LangGraph？**

**A:** 普通 Chain 是线性 DAG（有向无环图），数据单向流动，无法处理循环和条件分支。LangGraph 基于图结构，支持循环（Loop）、条件分支（Conditional Edge）和状态持久化（Checkpoint），适合构建复杂 Agent。LangGraph 适合：需要多步推理的工具调用、人机交互（Human-in-the-Loop）、多 Agent 协作、需要中断和恢复的流程。StateGraph 定义节点（Node，计算单元）和边（Edge，数据流/控制流），`add_node` 和 `add_edge` 构建图，`compile` 编译为可执行应用。

---

### Card 7: StateGraph 设计
**维度**: 💻代码 | **难度**: ⭐⭐⭐

> **Q: LangGraph 的 StateGraph 是如何工作的？如何实现状态持久化？**

**A:** StateGraph 的核心是共享状态（TypedDict 或 Pydantic BaseModel），每个 Node 读取和更新状态。节点返回的字典更新到状态中（`add_node("agent", agent_node)`）。边定义数据流：`add_edge("node1", "node2")`（顺序执行）、`add_conditional_edges("check", decide_next)`（条件分支）。持久化：通过 `Checkpoint` 机制，在每个步骤保存状态快照，支持中断和恢复。`MemorySaver`（内存存储）、`SqliteSaver`（SQLite 持久化）。`thread_id` 区分不同会话。

---

### Card 8: 回调机制
**维度**: 🔬深挖 | **难度**: ⭐⭐

> **Q: LangChain 的回调机制（Callbacks）如何工作？有哪些事件？**

**A:** `BaseCallbackHandler` 定义回调事件：`on_llm_start`（LLM 开始调用）、`on_llm_end`（LLM 结束）、`on_chain_start`（Chain 开始）、`on_chain_end`、`on_tool_start`、`on_tool_end`、`on_retriever_start` 等。`callbacks` 参数可传入全局或组件级别。`ConsoleCallbackHandler` 打印到控制台（调试用）。`StdOutCallbackHandler` 标准输出。自定义回调：继承 `BaseCallbackHandler`，重写需要的事件方法。`AsyncCallbackHandler` 异步回调。LangSmith 回调自动追踪调用链。

---

### Card 9: LangSmith 追踪
**维度**: 📝速记 | **难度**: ⭐⭐

> **Q: LangSmith 提供了哪些功能？如何帮助调试 LLM 应用？**

**A:** 追踪（Tracing）：记录每次 LLM 调用的输入/输出、Token 消耗、延迟。评估（Evaluation）：创建数据集，运行回归测试，对比不同 Prompt/模型的效果。调试（Debugging）：查看每次调用的详细信息（Prompt 内容、LLM 响应、Tool 调用链）。数据集管理：上传测试用例，自动运行评估。`LANGSMITH_TRACING=true` 环境变量开启追踪。`LangSmithHub` 共享 Prompt 模板和评估数据集。`@traceable` 装饰器手动追踪自定义函数。

---

### Card 10: RAG vs Agent
**维度**: 📝速记 | **难度**: ⭐⭐

> **Q: RAG 和 Agent 的区别是什么？什么场景下结合使用？**

**A:** RAG（检索增强生成）：检索文档 → 拼接 Prompt → 生成回答，适合知识密集型问答（公司文档、产品手册）。Agent：自主决策 → 调用工具 → 多步推理，适合需要多步骤作的任务（订票、查询天气+计算路程）。结合使用：Agent 内部使用 RAG 作为工具（知识检索工具），Agent 决定何时需要检索知识。典型：Agent 先 RAG 检索相关知识，再调用外部 API 执行操作。

---

### Card 11: Prompt 模板
**维度**: 💻代码 | **难度**: ⭐⭐

> **Q: LangChain 的 PromptTemplate 和 ChatPromptTemplate 有什么区别？**

**A:** `PromptTemplate` 用于文本模型（`text-davinci-003`），`ChatPromptTemplate` 用于聊天模型（`gpt-4`）。`ChatPromptTemplate.from_messages([("system", "你是{role}"), ("human", "{input}")])` 构建多轮对话模板。`MessagesPlaceholder` 插入动态消息列表（如历史记录）。`FewShotPromptTemplate` 少样本学习模板。`PromptTemplate` 支持 `partial_variables`（部分变量，如 `{date}` 在构建时填充）。`from_template` 从字符串自动解析 `{变量}`。

---

### Card 12: 输出解析器
**维度**: 💻代码 | **难度**: ⭐⭐

> **Q: LangChain 的输出解析器有哪些？如何处理结构化输出？**

**A:** `StrOutputParser`（直接返回字符串）、`PydanticOutputParser`（解析为 Pydantic 模型，最常用）、`CommaSeparatedListOutputParser`（逗号分隔列表）、`JsonOutputParser`（解析 JSON）、`StructuredOutputParser`（按字段定义解析）。`PydanticOutputParser` 用法：定义 Pydantic 模型 → `parser = PydanticOutputParser(pydantic_object=MyModel)` → `parser.get_format_instructions()` 生成格式说明 → 加入 Prompt → `parser.parse(text)` 解析结果。`with_structured_output` 方法（OpenAI 专用）强制结构化输出。

---

### Card 13: 多 Agent 协作
**维度**: 🎯场景 | **难度**: ⭐⭐⭐

> **Q: 多 Agent 协作的模式有哪些？如何处理 Agent 之间的通信？**

**A:** ① Supervisor Agent（一个 Agent 协调多个子 Agent，分配任务并汇总结果）；② 对话式协作（Agent 之间直接对话，轮流发言）；③ 工作流式（Agent 按预定流程执行，A 输出→B 输入→C 输入）。通信：Agent 之间通过共享状态（StateGraph 的状态字典）或消息队列交换信息。LangGraph 多 Agent：每个 Agent 是一个 Node，共享状态，Supervisor 控制调度。`ToolMessage` 作为 Agent 间的通信载体。

---

### Card 14: 工具选择策略
**维度**: 🎯场景 | **难度**: ⭐⭐⭐

> **Q: 如何设计 Agent 的工具选择策略？工具太多时 LLM 如何准确选择？**

**A:** ① 工具名称清晰（动词+名词，如 `search_products`）；② description 详尽（说明何时使用、参数含义、返回值）；③ 工具分组（按领域分组，减少单次暴露的工具数，如 `finance_tools`、`search_tools`）；④ 动态工具注册（根据上下文动态注册相关工具，如只注册当前用户权限内的工具）；⑤ 工具优先级（关键工具放在前面）；⑥ 工具错误处理（执行失败时返回友好错误信息，引导 LLM 重试或换工具）。

---

### Card 15: 上下文窗口管理
**维度**: 🎯场景 | **难度**: ⭐⭐⭐

> **Q: 如何处理 LLM 上下文窗口超限的问题？**

**A:** ① 滑动窗口（只保留最近 N 轮对话）；② 摘要压缩（用 LLM 总结历史，压缩回旧轮次）；③ 选择性记忆（只保留关键信息，如用户偏好、关键决策）；④ 向量检索记忆（`VectorStoreRetrieverMemory`，按语义检索相关历史片段）；⑤ 截断策略（从最旧的消息开始丢弃，直到 token 数在范围内）；⑥ 多轮对话中使用 `MessagesPlaceholder(variable_name="history")` 控制历史条数。`get_num_tokens(text)` 提前计算 token 数。

---

### Card 16: 流式输出
**维度**: 💻代码 | **难度**: ⭐⭐

> **Q: LangChain 如何实现流式输出？LCEL 的 stream 方法如何工作？**

**A:** `chain.stream({"input": "你好"})` 返回生成器，每次 `yield` 输出一个 token。底层调用 LLM 的流式 API（`stream=True`）。`astream` 异步版本。`astream_events` 提供更细粒度的事件流（包括中间步骤）。Web 集成：FastAPI 的 `StreamingResponse` 包装 `chain.stream()`，SSE 格式推送。`Runnable` 的 `batch` 和 `abatch` 批量处理（不流式）。`Runnable` 的 `map` 并行处理多个输入。

---

### Card 17: 评估方法
**维度**: 📝速记 | **难度**: ⭐⭐

> **Q: 如何评估 LLM 应用的质量？LangChain 提供了哪些评估工具？**

**A:** 定量评估：RAGAS（Faithfulness/Answer Relevancy/Context Precision/Context Recall）、BLEU/ROUGE（文本生成质量）。定性评估：人工评分（1-5 分）、A/B 测试。LangChain 评估：`RunEvaluator` 评估单次运行，`StringEvaluator` 评估字符串输出。`LangSmith` 创建数据集，批量运行测试，对比不同配置的结果。`PairwiseStringEvaluator` 对比两个模型输出。`CriteriaEvalChain` 按自定义标准评估（如安全性、长度、语气）。

---

### Card 18: 生产部署
**维度**: 🎯场景 | **难度**: ⭐⭐⭐

> **Q: LangChain 应用生产部署的关键要点有哪些？**

**A:** ① 缓存（LLM 缓存 `langchain.cache`，减少重复调用和成本）；② 限流（API 速率限制，退避重试）；③ 监控（LangSmith 追踪 + 自定义指标，如 P99 延迟、Token 消耗、错误率）；④ 错误处理（LLM API 错误重试、降级方案）；⑤ 安全（Prompt 注入防护、敏感信息过滤、输出审核）；⑥ 版本管理（Prompt 版本控制、模型版本灰度）；⑦ 异步（FastAPI + 异步链，提高并发处理能力）；⑧ 成本控制（Token 预算、模型路由、缓存策略）。