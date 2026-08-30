# LangChain × LangGraph × LangSmith 面试 QA · 背诵版

> 基于《LangChain_LangGraph_LangSmith_面试QA_详解版.md》精简，**每题保留 3-6 条得分点**，供考前快速过目。
> 详解版含完整参考回答与深挖方向，用于深入复习。
> 知识基线：LangChain 1.0 / LangGraph 1.0（2025-10），AgentExecutor 已弃用。

---

## 第一章 · LangChain（26 题）

**Q1：大模型原生调用有哪些困境？**
- 三重困境：调用方式不统一（各家客户端初始化/请求/响应完全不同）
- 参数体系不统一（max_tokens 别名、stop、temperature/top_p 规则差异，切模型参数失效）
- 代码耦合混乱（业务硬耦合厂商类，切模型全局改代码，异步支持不一致）
- 本质：缺少统一抽象层，框架价值原点

**Q2：LangChain 怎么解决？原理？**
- 核心：统一抽象防腐层/适配器层，业务只依赖统一接口
- 调用不统一 → ChatModel 基类定义 invoke/stream/ainvoke 统一接口
- 参数不统一 → 适配器内部做参数映射、默认值对齐、标准化
- 耦合混乱 → init_chat_model 收敛创建入口，零代码切换模型

**Q3：ChatModel 三层组件？**
- 抽象基类 BaseChatModel：统一接口契约（invoke/stream/bind_tools）
- 厂商实现类（ChatOpenAI 等）：封装鉴权、请求、重试、响应解析
- init_chat_model 工厂：动态导入 + 参数映射 + 实例创建，主流范式
- 本质：接口抽象 → 具体实现 → 工厂创建的三层结构

**Q4：ChatOpenAI vs init_chat_model？**
- ChatOpenAI：底层实现类，仅 OpenAI 协议
- init_chat_model：上层工厂，按模型名/厂商前缀动态适配所有厂商
- 厂内创建的就是 ChatOpenAI 实例，是"创建"与"实现"两种职责
- 适用：多模型兼容、动态切换业务 → init_chat_model

**Q5：提示词的核心作用？**
- 角色定义：身份与边界，减少幻觉
- 任务指令：传目标与要求，避免语义偏差
- 格式约束：统一输出格式/长度，适配工程化
- 逻辑引导：规范推理步骤（CoT），提升精准度
- 提示词是需要版本管理、评测的资产 → 衔接 LangSmith Prompt Hub

**Q6：消息 vs 提示词？**
- 消息：最小标准化单元 = 角色标识 + 文本 + 元数据，支撑多轮上下文
- 提示词：核心指令内容，依赖消息实现角色区分与上下文挂载
- 封装示例：用户输入→HumanMessage，模型输出→AIMessage
- 关系一句话：消息是结构化载体，提示词是内容

**Q7：四大消息类型？**
- SystemMessage：全局指令（角色/规范/禁止/格式）
- HumanMessage：用户输入载体
- AIMessage：模型输出载体，含 token 消耗、tool_calls、请求 ID
- ToolMessage：工具结果回执，绑定 tool_call_id 配对
- 闭环：AI 请求工具 → Tool 执行 → ToolMessage 回填 → 再喂模型

**Q8：LangChain 是什么？核心能力？**
- 五大要素：模型、提示词、工具、记忆、检索 + LCEL 编排 + Agent
- 三层：组件层（模型/提示词/工具/记忆/检索）+ 编排层（LCEL）+ Agent 层
- 1.0 时代 Agent 构建在 LangGraph 运行时之上
- 1000+ 集成生态是重要价值

**Q9：LangChain 1.0 重要变化？**
- create_agent 取代 create_react_agent，AgentExecutor 维护至 2026-12
- Middleware 中间件：before_model / wrap_tool_call 等 6 钩子，内置 PII/Summarization/HITL
- content_blocks：跨厂商统一内容块（reasoning/text/tool_call）
- langchain-classic 包拆分（旧 Chain/Retriever/Hub 迁移）
- 结构化输出进主循环，不再额外调 LLM

**Q10：LCEL 是什么？价值？**
- 声明式编排：`prompt | model | parser`，Runnable 协议 + `|` 管道
- 免费获得 stream/batch/async/回调/重试
- RunnableParallel 并行、RunnableBranch 分支、惰性求值
- 与 LangSmith 自动打通 trace

**Q11：Runnable 核心方法？**
- invoke / ainvoke / batch / abatch / stream / astream / bind
- 统一契约：类型签名自动串联、惰性构建 DAG
- 包装已有函数：RunnableLambda；透传：RunnablePassthrough；并行：RunnableParallel
- 与手写函数区别：流式/并发/重试/回调随接口自带

**Q12：PromptTemplate vs ChatPromptTemplate？**
- 字符串模板（LLM）vs 消息列表模板（ChatModel）
- ChatPromptTemplate 产出 System/Human/AIMessage 序列
- MessagesPlaceholder：预留消息列表位置，注入多轮历史与 agent_scratchpad

**Q13：输出解析器与结构化输出？**
- StrOutputParser / PydanticOutputParser / JsonOutputParser
- 现代做法：model.with_structured_output(Pydantic) 走厂商原生能力（JSON mode / tool use）
- 1.0：create_agent 结构化输出在主循环生成，省一次 LLM 调用
- 解析失败：自动重试 + 降级 + 人工兜底

**Q14：Memory 选型？**
- Buffer（全量）/ BufferWindow（窗口）/ Summary（摘要）/ SummaryBuffer（混合）
- 生产：窗口 + 摘要 + 外置持久化（Redis/DB）组合
- 1.0 主推：LangGraph Checkpointer（线程内）+ Store（跨会话长期）替代 Legacy Memory
- 长对话 token 爆炸 → trim_messages / 摘要 / 归档

**Q15：如何自定义 Tool？**
- @tool 装饰器：签名 + 注解 + docstring 自动生成 name/description/schema
- BaseTool 子类：_run/_arun 精细控制
- 契约三要素：description 写清楚、Pydantic Schema 校验、幂等 + 错误友好返回
- 工具是给模型用的接口，不是给人用的函数

**Q16：Tool 与 Function Calling 关系？**
- Function Calling：模型侧能力，输出结构化 tool_calls
- Tool：框架侧抽象，bind_tools 传清单 + ToolNode 执行 + ToolMessage 回填
- 一句话：模型定决策（tool_calls），框架做执行（Tool）
- 老模型无工具能力 → 文本 ReAct 协议退化

**Q17：ReAct 循环机制？**
- Thought（推理）→ Action（tool_call）→ Observation（工具结果）→ 循环 → Final Answer
- 终止条件：模型不再请求工具 = 任务完成
- 保护：recursion_limit / max_steps 防死循环烧 token
- 与 Plan-and-Execute：边想边做 vs 先规划再执行

**Q18：Agent vs Chain？**
- Chain：开发者预编排、固定线性（DAG）、可预测、成本低
- Agent：模型自主决策下一步、循环+条件路由、不可预测、成本高
- 工程立场：能用 Chain 不用 Agent；可混合（Chain 主干 + Agent 分支）

**Q19：create_agent 是什么？**
- 1.0 标准 Agent 入口（langchain.agents），取代 create_react_agent
- 字符串模型名 + tools + system_prompt + middleware 直接配置
- Middleware 是其灵魂：6 钩子自定义上下文工程/守门/动态换模型
- 底层仍是 LangGraph：自动获得持久化/流式/HITL/Time Travel

**Q20：流式输出？**
- chain.astream() 自动逐 token（LCEL 组件实现 stream 即链级流式）
- 必用场景：聊天 UI（打字机）、长文生成、Agent 多步进度
- 落地：FastAPI SSE/StreamingResponse，首 token 延迟（TTFT）是关键
- LangGraph：stream_mode=["updates","messages"] 同时流节点与 token

**Q21：MCP 是什么？**
- 开放协议，标准化"如何向 LLM 提供工具/上下文"（USB-C 类比）
- 工具以 MCP Server 暴露，任何 Client 复用
- LangChain 接入：langchain-mcp-adapters（load_mcp_tools → Tool → Agent）
- 核心元素：Client / Server / 协议（工具、资源、提示）

**Q22：Agentic RAG？**
- 传统 RAG：固定流水线，一次执行
- Agentic RAG：控制循环，自主决策检索/改写/换源/重试
- Self-RAG：模型自评片段相关性；CRAG：质量低触发补救检索
- 成本控制：阈值 + 最多迭代 N 次 + 分级模型；查询稳定场景用固定 RAG 更划算

**Q23：幻觉如何检测与缓解？**
- 检索层：混合检索 + Rerank + 查询改写 + chunk 优化
- 生成层：指令约束"仅基于资料回答" + 结构化校验
- 校验层：检索相关度打分（低分拒答/重检索）+ 回答-证据一致性校验（LLM-judge）+ 实体级校验
- 兜底：拒答话术 + 转人工 + 日志反哺评测集

**Q24：混合检索与 Rerank？**
- 混合检索：BM25（精确）+ 向量（语义）多路召回，RRF 融合
- Rerank：Cross-Encoder 重排，把"语义相关"提为"真正相关"
- 原则：召回层求全（高召回），重排层求精（高精度），两阶段解耦
- LangChain：EnsembleRetriever（混合）+ ContextualCompressionRetriever（Rerank）

**Q25：Callbacks 机制？**
- 事件点：on_llm_start/end、on_tool_start、on_chain_start 等
- 用途：日志调试、指标采集、横切逻辑、衔接 LangSmith
- 关系：回调是机制，LangSmith 是其上的零侵入自动追踪产品
- 自定义场景保留 Callbacks（BaseCallbackHandler），默认走 LangSmith

**Q26：本地正常生产失败如何调试？**
- 五步：Trace 定位 → 环境差异核对（key/模型版本/依赖锁/数据同步/并发）→ 超时重试检查 → 状态持久化核对 → 失败样本入评测集回归
- 90% 差异能从 trace 找到；数据先行，别上来改代码

---

## 第二章 · LangGraph（16 题）

**Q27：LangGraph 是什么？与 LangChain 关系？**
- 基于图的低层编排运行时：节点做工、边定流向、类型化 State 贯穿
- 定位 2026：LangGraph 是运行时（runtime），LangChain 是上层 batteries-included
- create_agent 就运行在 LangGraph 上；分层而非竞争
- 用 LangGraph 的真实理由：循环、分支、持久化、审批（Chain 是直线）

**Q28：StateGraph 三要素？**
- State：类型化契约（TypedDict/Pydantic），节点返回部分更新由运行时合并
- Node：`node(state) -> dict`，只返回要改的 key（增量），可接收 config
- Edge：静态边 / 条件边（动态路由）/ START/END 哨兵
- compile() 校验图 + 挂 checkpointer + 产出可执行 Runnable

**Q29：Reducer？无 reducer 并发写？**
- Reducer = 状态字段的合并函数（Annotated[field, fn]）
- 默认 last-write-wins；并行分支写同字段 → 抛 InvalidUpdateError（不是静默覆盖）
- messages 字段必须 add_messages（按 id 去重、保序），列表用 operator.add
- 陷阱题正确答案："并发写冲突会报错"

**Q30：条件边 vs 普通边？**
- 普通边 A→B 静态；条件边 A + router(state) + mapping 动态路由
- router 返回目标节点名；mapping 无则返回值即节点名
- 回边（tools→agent）是循环关键
- 预置 tools_condition：最后消息有 tool_calls → tools，否则 END

**Q31：Checkpointer 与 thread_id？**
- Checkpointer 每步保存完整状态快照（暂停/恢复/崩溃恢复/HITL 的基础）
- thread_id = 会话作用域键；恢复传同一 id；续跑 invoke 传 None
- InMemorySaver 测试用；生产 PostgresSaver/SqliteSaver
- Checkpointer（线程内短记忆）≠ Store（跨线程长记忆）

**Q32：Checkpointer vs Store？**
- 记忆类型：短记忆 vs 长记忆；作用域：线程内 vs 跨线程
- 内容：状态快照 vs KV 命名空间事实
- 答的问题："这次到哪了" vs "这个用户记住了什么"
- 反模式：长期偏好塞 checkpoint；正确：checkpointer 管状态 + store 管记忆

**Q33：Human-in-the-Loop？**
- interrupt() 暂停并抛载荷 + Command(resume=...) 恢复
- 必须配 Checkpointer；恢复时节点从顶部重跑（interrupt 前的副作用会重跑）
- 三态：approve / edit / reject（读 resume 值 + 条件边）
- blast-radius 原则：只闸花钱/对外/写核心/删数据等大爆炸动作

**Q34：崩溃恢复与幂等？**
- 恢复：同 thread_id + invoke(None) 续跑，不重放已完成节点
- checkpoint 保证状态可恢复，不保证副作用恰好一次
- 解法：幂等键（thread_id+step+action 去重）、动作后置 checkpoint、先查后做
- 故障注入测试验证恢复正确

**Q35：流式模式？**
- values：每步完整状态 / updates：节点增量 / messages：LLM token / custom：自定义事件
- 场景：调试用 values、可观测用 updates、UI 用 messages、进度用 custom
- 组合：stream_mode=["values","messages"]
- 红线：浏览器只见 messages/custom，绝不流 values/updates（泄露内部状态/密钥）

**Q36：Send 并行（Map-Reduce）？**
- fan_out 节点返回 [Send("worker", payload)] 动态扇出
- worker 并发执行，结果字段必须配 Reducer（否则 InvalidUpdateError）
- aggregate 节点汇聚全部结果
- Send 是运行期动态分发（区别于静态并行）

**Q37：多智能体拓扑？**
- Supervisor：集中路由委派，易推理易调试，默认首选
- Swarm：对等移交（Command(goto)），灵活难调试
- Hierarchical：监督者的监督者，大系统按团队拆
- 反多智能体崇拜：单 Agent + 好工具列表优先，有明确理由才升级

**Q38：子图？**
- 编译后的图作为节点嵌入父图
- 状态键相同时直接合并；不同时包装节点做映射
- 用途：自包含可复用逻辑块、大图拆分、团队独立维护
- 可独立编译、独立测试

**Q39：AgentExecutor 迁移？**
- 弃用时间线：维护至 2026-12，新项目禁用
- 快路：create_agent（等价 ReAct + 自带 checkpoint/流式/HITL）
- 定制路：手写 StateGraph（agent 节点 + tools 节点 + 回边）
- 检查：工具 schema、prompt、记忆（→Checkpointer）、回调（→Middleware）

**Q40：日 1 万次请求设计？**
- 先算账：≈7 次/分钟，瓶颈在成本与状态非吞吐
- 持久化 PostgresSaver（跨实例共享 + 可恢复）；绝不用 InMemory
- 状态瘦身：大工具结果存引用，checkpoint 只序列化指针
- 防护：步数上限 + token 预算 + 超时 + HITL 只闸大爆炸动作
- 队列削峰，避免击穿模型 rate limit

**Q41：ToolNode / tools_condition？**
- ToolNode：执行最后 AI 消息的 tool_calls，自动按 tool_call_id 配 ToolMessage
- tools_condition：有 tool_calls → tools，无 → END
- 回边 add_edge("tools","agent") 不能忘
- 工具异常默认捕获回填，循环不断

**Q42：流式不泄露状态？**
- 前端只见 messages（token）+ custom（get_stream_writer 显式写）
- 绝不流 values/updates 给浏览器
- 规则：明确表达"用户该看什么"而非"系统里有什么"
- SSE 落地：StreamingResponse 包 astream，鉴权照做

---

## 第三章 · LangSmith（15 题）

**Q43：LangSmith 是什么？铁三角？**
- 可观测 + 评测平台，覆盖开发调试→评测→生产监控全生命周期
- LangChain 构建（造）、LangGraph 编排（跑）、LangSmith 验证（看+量）
- 接入零侵入：LANGCHAIN_TRACING_V2=true + API key 两个环境变量
- 2025-10 LangGraph Platform 更名 LangSmith Deployment

**Q44：Tracing 概念？**
- Trace = 单次执行完整记录（执行树）；Run = 一步（模型/工具/节点，含 IO/token/延迟）
- Thread = 跨多次调用的会话维度
- 层级：Thread（会话）→ Trace（执行）→ Run（步骤）
- 瀑布视图调试复杂 Agent 的核心视图

**Q45：如何接入？**
- 三步：建项目拿 Key → pip install langsmith → 配置环境变量（TRACING_V2/API_KEY/PROJECT）
- LangChain/LangGraph 自动上报；纯函数用 traceable 装饰器
- 生产：key 走密钥管理；trace 可采样（SAMPLING_RATE）控制成本
- 私有环境：LangSmith 私有化部署或自建

**Q46：Dataset / Experiment？**
- Dataset = 考卷（输入+期望+标签）；Experiment = 考生在考卷上的成绩单
- 流程：Dataset → 目标函数 → 批量跑 → 评估器打分 → 对比迭代
- 评估器三态：代码规则 / LLM-as-Judge / 人工标注
- 生产负反馈样本回流入集（关键闭环）

**Q47：LLM-as-Judge？**
- 大模型评分模型输出，适合无标准答案的语义评价
- 定义：评估 Prompt（锚点/量纲/JSON）+ 裁判模型 + few-shot
- 校准四法：明确锚点、few-shot、人类反馈回灌、防裁判漂移
- 局限：self-bias、成本、复杂度不可靠 → 规则+裁判+人工抽样三层互补

**Q48：自定义评估器？**
- 签名：`(run: Run, example: Example|None) -> {"key","score","comment"}`
- 代码型：确定性校验（格式/实体/长度/正则）快且可复现
- 组合：evaluators=[代码规则, LLM裁判, RAG 评估器]
- RAG 场景预置 rag_* 评估器族（忠实度/相关性）

**Q49：Prompt Hub？**
- 提示词中心化版本管理：版本化/回滚/协作/评星/共享
- 热更新：运行时按 tag 拉取（hub.pull），不重启改 prompt
- 环境化：dev/prod 不同 tag，变更走 review-as-code
- 发布纪律：先建版本 → 跑评测 → 通过切 tag → 监控回归

**Q50：Annotation Queue？**
- 人工评审工作流：筛选 trace 入队，专家打标
- 闭环：生产 trace → 规则筛选 → 队列 → 人工标注 → 沉淀 Dataset → 评测 → 改进
- Feedback API：client.create_feedback(run_id, key, score) 挂反馈
- 负反馈样本自动入队评审 = 数据飞轮燃料

**Q51：Automation 规则？**
- 事件驱动规则引擎：条件（负反馈/延迟/错误率/关键词/成本）+ 动作（入队/Webhook/Dataset/告警）
- 价值：失败自动变"待评审 + 评测素材 + 即时告警"
- 与自建系统打通：Webhook 通知 Slack/钉钉
- 防告警疲劳：分级阈值、聚合、冷却窗口

**Q52：生产监控看什么？**
- 质量：在线评估（auto-eval）、用户反馈、错误样本率
- 健康：错误率、p95/p99 延迟、工具失败率
- 成本：每 run 价格、token 趋势、单用户成本异常
- 定位：trace 瀑布 → 定位模型/检索/工具 → 修 → 样本回归

**Q53：LangSmith vs Langfuse？**
- 主轴：生态绑定（LangSmith 零侵入原生）vs 开放/自托管（Langfuse 开源）
- 覆盖：LangSmith 全生命周期；Langfuse 追踪+评测+Prompt（弱部署）
- 选型：LangChain 栈且接受协同 → LangSmith；多框架/数据主权 → Langfuse 或自建
- 供应商锁定是最大隐性成本

**Q54：多轮/Agent 轨迹评测？**
- 三层：轨迹级 LLM Judge / 代码断言（必须调某工具、步数上限）/ 人工 Annotation
- 起点：先定义成功标准（任务达成/成本/安全）
- 样本：多轮输入序列 + 期望轨迹/终态（带三步评测）
- 务实：自动规则 + 抽样人工复核

**Q55：评测入 CI/CD？**
- PR → CI 跑 evaluate → 对比 baseline → 低于阈值阻断合并
- 环境分级：dev 全量、生产发布前关键子集
- 活评测集：生产负反馈持续入集，覆盖真实坏例
- 防抖动：固定裁判模型/温度、均值 ± 容差、阈值先松后紧

**Q56：用 LangSmith 定位线上错误回答？**
- 五步：收 trace（找 Thread）→ 回看输入（上下文/检索片段）→ 三处归因（检索空召回/生成注入/校验放行）→ 样本入集复现修复 → Automation 监控回归
- 空召回是幻觉高频源头；修复必须回测（Experiment 对比）

---

## 📌 三库速览

| 库 | 定位 | 核心面试点 |
|----|------|-----------|
| LangChain | 应用构建 | 模型抽象、消息、LCEL、Tool、create_agent、1.0 新特性 |
| LangGraph | Agent 运行时 | StateGraph、Reducer、Checkpointer、interrupt、Send、流式 |
| LangSmith | 可观测+评测 | Trace、Dataset/Experiment、LLM-as-Judge、Prompt Hub、反馈闭环 |

> 深挖见详解版；LangChain 教程：`2-learning/stacks/14-langchain`；LangSmith 教程：`2-learning/stacks/18-langsmith`。