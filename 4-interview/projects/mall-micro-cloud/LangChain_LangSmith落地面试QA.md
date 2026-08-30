# 微云商城 · LangChain / LangGraph / LangSmith 落地面试 QA

> 基于 mall-micro-cloud 实训项目 + `5-research/tech-stack-analysis/mall-ai-search/`（10 篇技术栈剖析）真实源码事实编写。
> 定位：**商城项目里 AI 检索模块的 LangChain/LangGraph 落地问答**，与《微云商城面试QA精编版》互为补充（精编版讲 RAG 效果，本文深挖 LangChain 框架落地细节）。
> 每题五要素：**参考话术** → **面试官意图** → **深挖追问** → **项目结合** → **常见扣分项**。
> 事实基线（务必遵守）：`create_agent` + `@tool` + `response_format` 结构化输出 + `InMemorySaver` 会话记忆 + 双供应商（通义/Agnes）+ 四层防幻觉；**LangSmith 目前未接入，只能作为"后续演进"讲，不得包装为已上线能力**。

---

## GOAL（专业交付目标）

| 维度 | 目标 |
|------|------|
| **产出** | 12 题商城 AI 检索模块 LangChain/LangGraph 落地 QA，1 题 LangSmith 演进设计 |
| **深度** | 从"用过框架"深入到"理解 create_agent 底层是 LangGraph 图执行、为什么用 InMemorySaver、response_format 如何强制结构化" |
| **结合** | 每题落到真实源码（`search_service.py` / `vector_sync_service.py` / `config/tools.py` / Java `AiSearchSeriveImpl`） |
| **诚实** | LangSmith 标注"演进方向"，不制造已上线假象 |
| **互链** | 关联三合一 QA 详解版、18-langsmith 教程、03-ai 专题题库 |

---

## 第一章 · LangChain 落地（Q1–Q6）

### Q1：你们项目的 AI 搜索模块是怎么用 LangChain 搭起来的？整体架构是什么？

**参考话术：** 商城 AI 智能搜索用 Python FastAPI + LangChain 构建服务，Java 侧通过 OpenFeign 调用。我把 AI 编排拆成三层能力：① **LLM 层**——用 `ChatOpenAI` 按 OpenAI 兼容协议接入通义千问和 Agnes AI 双供应商，`base_url` + `api_key` 切换，`temperature=0.1` 保证推荐任务稳定输出；② **Agent 层**——用 LangChain 1.0 的 `create_agent` 构建商品推荐 Agent，挂一个 `vector_search_tool` 向量检索工具，配 `response_format` 强制结构化输出；③ **记忆层**——用 LangGraph Checkpointer（`InMemorySaver`）+ `thread_id` 做会话级记忆隔离。

一次请求链路：前端 → Java `AiSearchController` → OpenFeign → FastAPI `/api/v1/recommend` → `create_agent` Agent 判断需要检索 → 调 `vector_search_tool` 从 RedisVL 召回 TOP-10 商品 → LLM 生成推荐 → `response_format` 输出结构化 `ProductRecommendResponse` → 返回 Java 侧。

**面试官意图：** 考察是否真的在项目里落地过 LangChain，而不只是会写 `prompt | model` 的 demo——要能讲出"Agent + Tool + 记忆 + 结构化输出"的组合。
**深挖追问：** 追问"为什么用 create_agent 而不是手写 Chain"→ 推荐任务需要"先检索再生成"的循环决策，Agent 能自主判断是否调用检索工具；create_agent 是 1.0 标准入口，底层构建在 LangGraph 上自动获得记忆和流式能力。
**项目结合：** `src/smart_search/core/search_service.py` 的 `recommend_product()`，`create_agent(model, tools, system_prompt, checkpointer, response_format)` 一次组装完成。
**常见扣分项：** 说不清 Java 与 Python 的分工边界；把 create_agent 说成旧版 AgentExecutor；不知道 response_format 是 1.2.10+ 新特性。

---

### Q2：`create_agent` 内部是怎么工作的？它和底层 LangGraph 什么关系？

**参考话术：** `create_agent` 是 LangChain 1.0 构建 Agent 的标准入口，它的底层就是 LangGraph 的 StateGraph 图执行。执行逻辑可以理解为四步（源码伪代码级）：① 把工具列表 `bind_tools` 绑定到模型；② 把 system_prompt 包装为 SystemMessage；③ 构造 StateGraph——`agent` 节点调模型、`tools` 节点执行工具，条件边判断"模型是否产生了 tool_calls"：有 → 路由到 tools 节点执行后回 agent，没有 → 走 END 输出最终结果；④ `compile()` 时挂载 Checkpointer 实现记忆、`with_structured_output` 强绑结构化输出。

所以它天然获得 LangGraph 的三件套能力：**持久化（checkpointer）、流式（stream）、状态管理（图状态）**——这些都不需要我额外实现。

**面试官意图：** 考察对"LangChain 是层、LangGraph 是运行时"的时代认知，能否穿透 API 讲到底层机制。
**深挖追问：** 追问"条件边怎么写"→ 判断最后一条 AI 消息是否含 `tool_calls`，有则回 `tools` 节点，无则 `END`；这也是 `tools_condition` 预置路由的标准语义。
**项目结合：** 我们项目实际调用 `create_agent` 时只传了 `tools=[vector_search_tool]`，单工具场景下循环路径固定是"agent → tools → agent → 输出"。
**常见扣分项：** 回答"create_agent 就是封装好的函数"不透底层；不知道 bind_tools 与条件边；把 LangGraph 说成和 LangChain 并列的另一个框架（实际是运行时层）。

---

### Q3：`vector_search_tool` 是怎么定义的？为什么用 `@tool` 装饰器？

**参考话术：** 用一个 `@tool` 装饰器把函数转成 Agent 可调用的工具：

```python
@tool
def vector_search_tool(query: str) -> str:
    """商品向量检索工具，获取相关商品资料。"""
    docs = self.vector_store.similarity_search(query, k=10)
    return "\n".join([f"{doc.page_content} | meta:{doc.metadata}" for doc in docs])
```

`@tool` 自动完成四件事：函数名 → 工具 name、docstring → description、类型注解 → 参数 Schema、整体 → 可绑定到模型 tool calling 的工具对象。**description 是工具与 LLM 的契约**——模型靠它判断"什么时候该调、传什么参数"；`k=10` 的召回数量是实测调出来的平衡值（太少丢商品、太多撑爆上下文）。

**面试官意图：** 工具定义是 Agent 落地的核心，考察是否理解"工具是给模型设计的接口"。
**深挖追问：** 追问"工具返回格式为什么是拼接字符串"→ Agent 的上下文是文本，工具结果要以"模型可读的文本"回填上下文，LLM 从中抽取结构化信息再交给 response_format 输出；直接返回原始向量对象模型读不了。
**项目结合：** `search_service.py` 中工具在 `__init__` 内定义，闭包捕获 `self.vector_store`；`RedisVectorStore.similarity_search` 走 HNSW 索引毫秒级召回。
**常见扣分项：** 不说 description 的价值；不知道工具结果要回填为模型可读文本；把 k=10 当拍脑袋数字（应说出是召回率与上下文长度的平衡）。

---

### Q4：`response_format` 结构化输出是怎么实现的？相比 JsonOutputParser 有什么好处？

**参考话术：** `response_format=ProductRecommendResponse` 是 langchain ≥ 1.2.10 的新特性。原理：① 把 Pydantic 模型转成 JSON Schema；② 通过 tool_strategy 把 Schema 注册为模型的 tool calling 能力；③ LLM 生成最终输出时**以 tool call 形式返回结构化数据**，而不是自由文本；④ LangChain 自动把 tool call 解析回 Pydantic 实例。

相比手写 JsonOutputParser 的好处：**零解析烦恼**（不需要 prompt 里反复强调"输出合法 JSON"）、**强类型约束**（模型必须生成符合 Schema 的结构，嵌套的 `List[GoodsInfo]` 也天然支持）、**失败率低**（走协议而非文本猜测）。这是"让框架和模型协议层保证结构"而非"靠提示词碰运气"。

**面试官意图：** 考察是否知道结构化输出的现代做法——用模型原生 tool calling 能力而非 prompt 约束。
**深挖追问：** 追问"1.0 里结构化输出还有什么演进"→ LangChain 1.0 中 Agent 的结构化输出进入主循环生成，不再额外调一次 LLM，省成本。
**项目结合：** `ProductRecommendResponse` 包含 summary（推荐摘要）、reason（推荐理由）、productList（商品列表），嵌套结构靠 Pydantic 校验兜底。
**常见扣分项：** 只提 JsonOutputParser 不知道 response_format/tool_strategy；说不出"以 tool call 形式返回"这一机制核心；不知道解析失败时 Pydantic 校验兜底。

---

### Q5：项目里为什么用 `InMemorySaver` 做记忆？生产环境会怎么改？

**参考话术：** 我们用 LangGraph Checkpointer 的 `InMemorySaver` + `thread_id` 做会话记忆：`thread_id` 是会话作用域键，同一次会话的多次请求共享状态，实现"那苹果的呢？"这类上下文指代；不同 thread_id 之间隔离。

选 InMemorySaver 的理由是**当前场景匹配复杂度**：① 电商搜索是短会话场景，用户通常搜索一两次就结束，不依赖长期对话历史；② 开发阶段快速迭代不需要持久化；③ 部署简单不用额外维护 Redis Checkpointer。**它的代价是进程重启丢记忆、多实例部署各自为政**——所以如果将来做长会话客服或多实例部署，必须替换为 `RedisSaver` / `PostgresSaver`（依赖里已有 `langgraph-checkpoint-redis`），这是明确的演进方向。

**面试官意图：** 考察记忆方案的选型判断——能说"为什么当前用内存版"比"无脑上 Redis"更能体现工程成熟度。
**深挖追问：** 追问"Checkpointer 和 Store 的区别"→ Checkpointer 是线程内短记忆（保存会话状态快照），Store 是跨线程长记忆（保存用户长期偏好）；两者是不同系统，用户长期画像应该用 Store。
**项目结合：** `search_service.py` 中 `self.checkpointer = InMemorySaver()`，请求时 `agent.ainvoke(msg, {"configurable": {"thread_id": thread_id}})`。
**常见扣分项：** 不知道 memory 走 Checkpointer 而非旧版 Memory 类；说不清 InMemorySaver 的丢失风险；把 Checkpointer 当长期记忆（该用 Store）。

---

### Q6：AI 服务的多模型/多供应商是怎么做的？对应 Java 生态怎么做？

**参考话术：** Python 侧用**工厂 + 策略模式**：`tools.py` 里 `_build_aliyun_llm()` 和 `_build_agnes_llm()` 两个构建函数都返回 `ChatOpenAI` 实例，差异只在 `base_url`、`api_key`、`model`、`temperature` 等参数；按配置环境变量选择构建哪个，业务代码只面向统一的 `ChatOpenAI` 抽象——这就是"OpenAI 兼容协议"的红利：**接口标准统一，换 base_url + api_key 即换供应商**。`temperature=0.1` 保证推荐任务稳定，`enable_thinking=False` 关闭推理链避免污染 JSON 输出。

对应 Java 生态（Spring AI）：用 `ChatClient` + `OpenAiChatModel`，`OpenAiChatOptions.builder().withTemperature(0.1)`，`.entity(SearchCondition.class)` 做结构化输出；多供应商用 `@ConditionalOnProperty` 按配置装配不同 bean——抽象思想完全一致，只是载体从 Python 变成 Spring 容器。

**面试官意图：** 考察跨生态抽象能力和"标准接口 + 多实现"的设计意识。
**深挖追问：** 追问"如果某个供应商不支持某个参数怎么办"→ 适配层做参数过滤/映射（这也是 LangChain 适配器层的价值），不支持的参数不传或映射别名。
**项目结合：** 阿里云通义千问 `qwen` 系列 + Agnes AI 双供应商，配置文件里环境变量回填；这正是三合一 QA"模型抽象防腐层"的实战案例。
**常见扣分项：** 只说"用了 OpenAI 所以能换"没讲工厂/策略；不知道 Java 侧 Spring AI 对应实现；没提 temperature 与 enable_thinking 的参数设计理由。

---

## 第二章 · RAG 检索落地（Q7–Q9）

### Q7：商品向量化的数据同步链路是怎么设计的？

**参考话术：** 离线同步链路：管理员触发 `/api/v1/sync` → ① SQLAlchemy 从 MySQL **流式读取** SKU 商品数据（`lazy_load` 避免一次性加载全量内存）；② 字段拼接为 `page_content`（名称+规格+价格，如"华为Pura 70。6.7英寸OLED屏。4999"）；③ `RecursiveCharacterTextSplitter` 切片（256 tokens/chunk，overlap=25，中文分隔符优先）；④ BGE-M3 Embedding 向量化 → RedisVL `add_documents` 批量写入 Redis Stack HNSW 索引（每批 100 条，**幂等 ID 防重复**）。

核心设计思想：**离线把向量建好，在线只做查询**——Embedding 调用要 ~100ms，不能实时算；HNSW 索引构建是 CPU 密集，必须离线完成；商品更新后触发增量同步。

**面试官意图：** 考察数据工程链路意识——RAG 不只在线检索，离线数据管道同样关键。
**深挖追问：** 追问"分块策略为什么按 256 tokens"→ 商品信息是结构化短文本（一个 SKU 几百字符），按 256 tokens 分块保证语义完整且不碎片化；overlap=25 让跨块上下文不丢失。
**项目结合：** `src/smart_search/core/vector_sync_service.py`；与此对应，Java 商城侧有 Elastic-Job 定时同步任务，Python 侧提供 `/sync` 手动/定时触发接口。
**常见扣分项：** 只讲在线检索不讲离线管道；不知道切片参数（chunk size/overlap）是基于场景调出来的；不提幂等。

---

### Q8：你们的防幻觉方案具体是什么？分几层？

**参考话术：** 四层防护（从源头到出口层层拦截）：① **System Prompt 约束**——明确"严禁编造不存在的商品信息""仅使用上下文存在的数据"；② **上下文约束**——只把向量库召回的 TOP-10 商品作为上下文传入，模型无中生有的空间被物理压缩；③ **低温度**——`temperature=0.1` 减少随机创作；④ **结构约束 + 后端校验**——`response_format` 强制 Pydantic Schema，Java 侧二次校验返回的 SKU 真实存在于商品库。

**面试官意图：** 防幻觉是 RAG 必问题，考察是否有"分层拦截"的系统性思维，而不是只答"prompt 里写别编造"。
**深挖追问：** 追问"如果向量检索空召回怎么办"→ 这是幻觉最高发场景，防线在①：Prompt 承诺"资料不足时如实说明"；更完善的方案是低分过滤 + 拒答兜底（这里 LangGraph 可以加评估节点做条件路由，是演进方向）。
**项目结合：** mall 搜索场景实测幻觉率趋近 0（测试同学批量模糊 query 验证）；召回效果 Recall@10 从 62% 提升到 84%（200+ 条人工标注评测集对比）。
**常见扣分项：** 只答一层防护；不知道空召回是幻觉根源；不提后端校验这道最后闸门。

---

### Q9：Agent 里的 `vector_search_tool` 空召回时会发生什么？你们怎么处理？

**参考话术：** 工具代码是直接 `similarity_search(query, k=10)`，如果向量库没有匹配商品，会返回空列表——拼接后工具结果为空字符串回填上下文。此时防线落在两处：① System Prompt 约束"检索结果为空时如实说明，不编造商品"；② 模型输出结构里 `productList` 允许空数组，前端拿到空列表不渲染，Java 侧再做 SKU 二次校验兜底。

坦白说，**空召回的直接处理（主动提示"未找到相关商品"）我们用的还是 prompt + 结构兜底，没有加独立的重检索节点**——如果把检索质量评估做成一个 LangGraph 条件边（低分 → 改写查询重检索 / 直接拒答），是明确的演进方向，LangSmith 忠实度评测可以量化这类场景的覆盖率。

**面试官意图：** 考察对真实边界场景的认知——很多候选人答不出"空召回时模型会硬编"，这恰恰是生产事故高发点。
**深挖追问：** 追问"如何量化幻觉率"→ 批量模糊 query 用例 + 人工校验返回 SKU 是否真实存在 + 统计占比；升级做法是 LangSmith 忠实度评估器自动打分。
**项目结合：** 与 14-langchain 教程中"Agent 空召回仍生成商品的 Bug 排查"考点对应；三合一 QA Q23 幻觉缓解"检索-生成-校验-兜底"四层在本项目实际落地了三层。
**常见扣分项：** 不知道空召回场景存在；说"空召回就报错"（模型会被迫编造）；拿不出量化手段。

---

## 第三章 · LangSmith 演进（Q10–Q11）

### Q10：项目里有没有用 LangSmith？如果引入，你会怎么设计评测与可观测？

**参考话术：** 坦诚说明：**当前实训项目没有接入 LangSmith**，检索效果是靠自建评测集（200+ 条人工标注 query + Recall@10 指标）和人工回归验证的。如果我把它作为**演进方向**引入，会按三步设计：

1. **可观测（Tracing）**：FastAPI 服务设置 `LANGCHAIN_TRACING_V2=true` + API key 两个环境变量，LangChain Agent 零侵入自动上报——每次推荐的"检索命中哪些商品 → LLM 生成什么 → 结构化输出是否解析成功"全链路可见，线上答错能一眼定位是空召回还是 prompt 问题；
2. **评测（Evaluation）**：把现有 200+ 条 query 评测集导入 LangSmith Dataset，挂「忠实度（rag_answer_faithfulness）+ 相关性 + 结构合法」三个评估器跑 Experiment——以后换模型、改 prompt、调 top-k 都先跑一遍对比基线分数，用数据拍板；
3. **反馈闭环 + CI 门槛**：用户负反馈（点了"不相关"）自动沉淀回评测集；评测接进 CI，prompt/模型改动低于忠实度阈值阻断合并，防回归。

**面试官意图：** 这道题考两点：**诚实**（不把未用的工具包装成已用）+ **工程视野**（能说出引入后的完整设计）。
**深挖追问：** 追问"评测成本怎么控"→ 在线评估抽样 + 用便宜小模型做裁判，只有低分样本才人工复核；评测集 50-200 条起步覆盖主要分支即可。
**项目结合：** 现有自建评测体系（Recall@10 62%→84%）正好是 LangSmith Dataset/Experiment 的"手工版"——演进成本低，逻辑一致。
**常见扣分项：** 谎称已用 LangSmith（源码无痕，面试官追问即刻穿帮）；只知道 Tracing 不知道评测与反馈闭环；把自建评测和 LangSmith 说成两套无关的东西。

---

### Q11：你们现在"200+ 条评测集 + Recall@10"的评测方式，和 LangSmith 的评测体系对比，各自优劣是什么？

**参考话术：** 现有方式（自建评测集 + 手动跑对比）的**优点**：贴近检索本质、指标明确（Recall@10 只数"召回里有没有正确商品"）、不依赖外部平台、零成本。**局限**：① 只量化检索不量化"最终回答质量"——回答是否忠实于检索到的商品、是否条理清晰无法用 Recall 衡量；② 评测集"一次性"，生产坏例不能自动回流；③ 换模型/prompt 要手动重跑、结果散落各处无法对比矩阵。

LangSmith 式评测的**优势**正好补这三块：LLM-as-Judge 评"回答质量"（忠实度/相关性）、Annotation Queue + Automation 让生产负反馈自动入集、Experiment 矩阵一键对比历史版本。**局限**：平台依赖、供应商绑定、有成本、语义评分本身需要校准。

**我的结论**：检索层指标（Recall@K）继续自建保留——它快、准、零成本；回答质量层引入 LangSmith 语义评估——它量的是"用户真正感知的质量"。两层互补，不是二选一。

**面试官意图：** 考察评测方法论的理解深度，是否懂得"指标分层"与"工具边界"。
**深挖追问：** 追问"LLM-as-Judge 不可靠怎么办"→ 评分锚点 + few-shot + 固定裁判模型防漂移 + 人工抽样复核（校准四法，见三合一 QA Q47）。
**项目结合：** mall 的 Recall@10 评测集可平滑迁移为 LangSmith Dataset，无需重造；忠实度评估正好量化"防幻觉四层防护"的效果。
**常见扣分项：** 贬低自建方案吹 LangSmith（或反之）；只讲 Recall 不讲回答质量；说不清两者的互补边界。

---

## 📌 附：与全家桶题库的映射

| 本文题目 | 对应三合一 QA | 对应教程 |
|---------|--------------|---------|
| Q1 整体架构 | Q8 / Q19 | 14-langchain README、mall-ai-search 00-OVERVIEW |
| Q2 create_agent 底层 | Q19 / Q27 | 14-langchain 03-advanced/03-langgraph-intro |
| Q3 工具定义 | Q15 / Q16 | 14-langchain 02-core/03-tools |
| Q4 结构化输出 | Q13 | mall-ai-search 07-LANGCHAIN-AGENT |
| Q5 Checkpointer 记忆 | Q31 / Q32 | mall-ai-search 08-LANGGRAPH-MEMORY |
| Q6 多供应商 | Q1-Q4 | mall-ai-search 04-LLM-PROVIDER |
| Q7 数据同步 | Q23（检索层） | mall-ai-search 09-DATA-SYNC |
| Q8 防幻觉分层 | Q23 / Q25 | mall-ai-search 10-ARCHITECTURE 四层防护 |
| Q9 空召回 | Q22 / Q26 | 14-langchain README 商城考点 |
| Q10 LangSmith 设计 | Q43-Q46 / Q55 | 18-langsmith 04-project/01-mall-ai-evaluation |
| Q11 评测方式对比 | Q46 / Q47 | 18-langsmith 02-core/02-evaluation |

> 速查：根目录《[LangChain_LangGraph_LangSmith_面试QA_背诵版](../../../LangChain_LangGraph_LangSmith_面试QA_背诵版.md)》，商城 AI 模块速记见《项目速记卡.md》。