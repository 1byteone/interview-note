# LangChain 面试速记版：30 个高频考点

> 涵盖 LCEL / Chain / Memory / Tool / Agent / LangGraph / 评估

## 一、LCEL（LangChain Expression Language）

| # | 问题 | 答案 |
|---|------|------|
| 1 | LCEL 的核心接口是什么？ | `Runnable` 接口，提供 `invoke`、`batch`、`stream`、`ainvoke` 等方法 |
| 2 | `|` 管道运算符在 LCEL 中如何工作？ | 左侧 Runnable 的输出自动传入右侧 Runnable 的输入，基于类型签名匹配 |
| 3 | LCEL 是立即执行还是惰性求值？ | 惰性求值（Lazy Evaluation），构建 DAG 后调用 `invoke()` 时才真正执行 |
| 4 | `RunnablePassthrough` 的作用？ | 透传输入，常用于调试或分支场景，不修改数据流 |
| 5 | `RunnableParallel` 的作用？ | 并行执行多个 Runnable，结果合并为字典 |
| 6 | `RunnableLambda` 的用途？ | 将普通 Python 函数包装为 Runnable，方便接入 LCEL 管道 |
| 7 | LCEL 如何实现条件分支？ | `RunnableBranch` 根据条件路由到不同 Runnable |
| 8 | `RunnableAssign` 的作用？ | 向数据流中注入新字段，不覆盖已有字段 |

## 二、Chain

| # | 问题 | 答案 |
|---|------|------|
| 9 | LLMChain 在新版中为何被弃用？ | 推荐使用 LCEL 的 `prompt \| model \| output_parser` 替代，更灵活 |
| 10 | 什么情况下仍需使用 Legacy Chain？ | 兼容旧代码或需要 `Chain` 类的高级回调接口时 |
| 11 | 如何自定义 Chain？ | 继承 `Chain` 类，实现 `_call` 和 `_input_keys`/`_output_keys` 属性 |
| 12 | SequentialChain 和 SimpleSequentialChain 区别？ | Simple 是单输入单输出；Sequential 支持多输入输出，可指定映射关系 |

## 三、Memory

| # | 问题 | 答案 |
|---|------|------|
| 13 | ConversationBufferMemory 如何工作？ | 将所有对话历史完整存储在内存列表中，每次调用时拼接全部消息 |
| 14 | ConversationSummaryMemory 的优点？ | 用 LLM 总结历史，节省 Token，适合长对话 |
| 15 | VectorStoreRetrieverMemory 的适用场景？ | 海量对话历史，按语义相似度检索最近相关的历史片段 |
| 16 | 如何实现自定义 Memory？ | 继承 `BaseMemory`，实现 `load_memory_variables` 和 `save_context` |
| 17 | Memory 在 LCEL 中如何集成？ | 通过 `RunnableWithMessageHistory` 包装原始链，传入 `get_session_history` 回调 |
| 18 | 什么是会话 ID（session_id）？ | 用于区分不同用户的对话历史，每个 session_id 独立存储 |

## 四、Tool

| # | 问题 | 答案 |
|---|------|------|
| 19 | `@tool` 装饰器的作用？ | 将 Python 函数快速转为 Tool 对象，自动解析函数签名和 docstring |
| 20 | Tool 的 name 和 description 为什么重要？ | LLM 据此决定调用哪个 Tool，description 越清晰准确率越高 |
| 21 | Function Calling 和 Tool 的关系？ | 底层通过 LLM 的 Function Calling API 实现，模型返回结构化参数 |
| 22 | Tool 执行失败时如何重试？ | 可在 Agent 的 `max_iterations` 和 `early_stopping_method` 中配置 |
| 23 | 如何给 Tool 传递动态参数？ | 使用 `Tool.from_function` 配合 `args_schema` 指定 Pydantic 模型 |

## 五、Agent

| # | 问题 | 答案 |
|---|------|------|
| 24 | ReAct 循环的完整步骤？ | Thought（思考行动方案）→ Action（调用 Tool）→ Observation（观察结果）→ 循环或终止 |
| 25 | AgentExecutor 的作用？ | 负责驱动 ReAct 循环：调用 Agent、执行 Tool、收集 Observation、判断是否终止 |
| 26 | `max_iterations` 默认值是多少？ | 默认为 15，超过后抛出异常或触发 `early_stopping` |
| 27 | `handle_parsing_errors` 的作用？ | 当 Agent 输出格式无法解析时，将错误信息返回给 Agent 重新生成 |

## 六、LangGraph

| # | 问题 | 答案 |
|---|------|------|
| 28 | LangGraph 与普通 Chain 的核心区别？ | LangGraph 基于图结构，支持循环、条件分支和状态持久化，适合复杂 Agent |
| 29 | StateGraph 中的 Node 和 Edge 是什么？ | Node 是计算单元，Edge 定义 Node 之间的数据流和控制流 |
| 30 | LangGraph 如何实现持久化？ | 通过 `Checkpoint` 机制，在每个步骤保存状态快照，支持中断和恢复 |

## 七、评估

| # | 问题 | 答案 |
|---|------|------|
| 31 | LangSmith 的主要功能？ | 追踪 LLM 调用、评估数据集、回归测试、调试 Prompt |
| 32 | 如何评估 RAG 系统？ | 使用 `RAGAS` 指标：Faithfulness（忠实度）、Answer Relevance（答案相关度）、Context Precision（上下文精确度） |