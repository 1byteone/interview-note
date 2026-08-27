# AI 应用开发岗 · 面试准备资料（基于简历 STAR 优化版）

> 说明：本文档根据你的 `AI应用开发岗_STAR优化版.docx` 提炼项目主线，结合权威技术资料整理个人介绍、项目剖析、追问链与话术模板，便于快速进入面试状态。

---

## 一、个人介绍（30–45s 话术模板）

### 版本 A（偏 AI 应用）
你好，我是 AI 应用开发方向的候选人，主要围绕 **RAG + Agent + 微服务协同** 做工程落地。  
在项目中我负责从检索链路设计、LLM 输出约束、会话状态管理，到 **SSE 流式输出**和异常恢复的全链路实现。  
同时具备 Java 微服务能力，能通过 **Spring Cloud Alibaba + OpenFeign** 与 Python AI 服务完成跨语言集成，并结合 **RocketMQ + Elastic-Job** 做数据同步与最终一致性保障。  
我的优势在于能把“模型能力”落地成“稳定可运维的应用系统”，注重延迟、召回质量、异常兜底和可观测性。

### 版本 B（偏 Java + AI 融合）
我是 Java 后端背景，重点向 AI 应用工程化方向发展。  
在实践中我会把 RAG、Agent、向量检索、SSE 等技术纳入企业级架构，并通过 **Nacos、OpenFeign、RocketMQ、Elastic-Job** 与现有业务系统打通。  
我的关注点不只是功能实现，还包括超时重试、幂等消费、会话持久化、失败恢复和线上问题排查，目标是让 AI 功能在生产环境长期稳定运行。

---

## 二、项目剖析（STAR 结构）

---

### 项目一：农业知识问答助手（RAG + Agent）

#### S（情境）
农业问答场景下，用户提问专业且口语化（例如“水稻纹枯病怎么防治”“某类作物施肥方案”），需要在专业文档中精准检索并生成可执行建议。  
早期问题包括：本地 PDF 检索覆盖不足、模型输出不稳定、会话上下文丢失、长文本生成等待时间过长。

#### T（任务）
构建一套可落地的农业问答系统，实现：
- 高召回、可解释的检索链路；
- 结构化输出与可恢复的会话；
- 流式响应与前端友好交互；
- 稳定可测的接口与异常兜底机制。

#### A（行动）
1. **双召回链路设计**
   - 本地 PDF 检索 + 知识库 Tool 调用，形成“本地文档+外部知识库”双通道召回；
   - 通过 Prompt 与 Tool 约束提高检索意图识别与答案一致性。
2. **LLM 输出治理**
   - 使用 **Pydantic** 约束回答结构（问题类型、回答、依据、置信度等），保障前端可解析；
   - 对模型输出做异常检测与降级，防止非法 JSON 或空结果影响体验。
3. **会话状态管理**
   - 使用 **InMemorySaver** 维护多轮对话状态；
   - 通过 `thread_id` 隔离用户会话，保证上下文连续性。
4. **流式输出与异常恢复**
   - 基于 **FastAPI astream + SSE** 实现 token 级流式输出；
   - 设计断流重连、异常中断恢复和超时处理策略，提升前端稳定性。

#### R（结果）
- 实现从 PDF 解析、检索、生成到流式输出的完整闭环；
- 检索命中率、回答一致性和前端体验均有明显提升；
- 形成可复制的“RAG + Agent + SSE”落地范式。

---

### 项目二：跨语言微服务与 RAG 电商平台（Java + Python）

#### S（情境）
平台既有 Java 微服务体系，又有 Python AI 服务，需要在统一架构下完成商品检索、推荐与数据同步。  
核心挑战包括：跨语言调用稳定性、向量检索准确性、重复数据治理、异步同步一致性。

#### T（任务）
设计并落地一个“Java 业务中台 + Python AI 服务”的融合架构，支持：
- ES 关键词检索与 RAG 双模式切换；
- 高质量向量召回与多样性排序；
- 服务间调用高可用；
- 数据异步同步与全量校验闭环。

#### A（行动）
1. **混合检索与排序优化**
   - 使用 **RedisStack 向量检索 + MMR**，在相关性与多样性之间取得平衡；
   - 保留 ES 关键词检索兜底，支持不同查询场景切换。
2. **数据治理与去重**
   - 通过 **MD5 指纹**对文档/商品数据做唯一标识，防止重复索引；
   - 结合元数据规则清洗，提高数据质量与检索稳定性。
3. **Agent 能力扩展**
   - 封装工具函数（如价格筛选、品类筛选），支持 Agent 根据用户意图动态选择工具；
   - 控制工具调用边界，避免回答超出商品范围。
4. **跨语言调用与稳定性**
   - Java 微服务通过 **OpenFeign** 调用 FastAPI 服务；
   - 设计超时、重试、降级和异常码映射策略，保障调用链可靠性。
5. **异步同步与全量校验**
   - 使用 **RocketMQ** 进行异步数据同步；
   - 通过 **Elastic-Job** 执行定时全量对账，形成“增量同步 + 全量校验”一致性闭环。

#### R（结果）
- 实现 ES 与 RAG 双模检索可切换架构；
- 向量召回准确率与排序质量提升；
- 跨语言调用稳定性增强，同步链路具备可追踪与可恢复能力。

---

## 三、面试高频追问链（由浅入深）

### 3.1 RAG 与检索链路
- **Q：为什么采用双召回链路？**
  - A：本地文档覆盖率有限，增加知识库 Tool 可扩展专业内容来源，形成“本地+全局”互补，提高召回率与鲁棒性。
- **Q：向量检索和关键词检索如何协同？**
  - A：向量擅长语义匹配，ES 关键词检索擅长精确匹配；系统支持双模式切换与融合，满足不同查询特征。
- **Q：MMR 有什么作用？**
  - A：在相关性基础上抑制冗余结果，提高返回结果多样性，避免高度相似内容重复出现。

### 3.2 LLM 输出与结构化
- **Q：为什么用 Pydantic 约束输出？**
  - A：便于前端解析、日志追踪和质量评估；可强制返回结构化字段，减少模型自由发挥带来的不稳定性。
- **Q：模型输出异常怎么处理？**
  - A：做 JSON 校验、字段兜底、重试策略和降级回答；对关键字段设置默认值，避免空值中断链路。

### 3.3 会话与状态管理
- **Q：InMemorySaver 是否适合生产？**
  - A：适合原型和测试，生产建议使用持久化 checkpointer（如 PostgresSaver），避免服务重启丢失会话。
- **Q：thread_id 如何设计？**
  - A：保证唯一性和可追溯性，建议长度控制在 255 以内，使用 UUID/哈希方案便于后续持久化兼容。

### 3.4 SSE 流式输出与异常恢复
- **Q：为什么选择 SSE？**
  - A：协议轻量、浏览器原生支持、适合 AI 场景的逐 token 输出。
- **Q：流式异常如何兜底？**
  - A：设置超时机制、断连检测、重连恢复和部分结果缓冲，避免一次异常导致整体不可用。

### 3.5 跨语言微服务调用
- **Q：OpenFeign 调用 FastAPI 有哪些关键配置？**
  - A：connectTimeout、readTimeout、重试策略、CircuitBreaker/Fallback、异常码映射与统一错误处理。
- **Q：服务发现如何协同？**
  - A：通过 Nacos 管理服务实例，OpenFeign 结合负载均衡完成路由，同时维护健康检查与实例上下线策略。

### 3.6 消息与数据同步
- **Q：RocketMQ 如何保证最终一致性？**
  - A：异步消息 + 业务幂等 + 定时对账；通过 Elastic-Job 做全量校验闭环。
- **Q：重复消费怎么处理？**
  - A：基于唯一键去重（如业务 ID、MD5 指纹），采用插入即去重或 Redis setnx 方案保证幂等。

---

## 四、项目风险与回答策略（面试加分点）

- **检索质量不稳定**：强调你通过 MMR、元数据过滤、混合检索和兜底策略形成多层治理。
- **模型输出不可控**：强调你用 Pydantic 结构化约束 + 异常降级 + 日志追踪构建可解释性闭环。
- **会话丢失风险**：强调你知道 InMemorySaver 边界，并能说明生产级 checkpointer 的演进路径。
- **跨语言调用失败**：强调你做了超时重试、熔断降级和异常码统一映射。
- **同步数据不一致**：强调你有“增量同步 + 全量对账”双保险，并能给出幂等设计细节。

---

## 五、可直接复用话术（项目难点回答）

> 项目最大难点不是单独引入 RAG 或 Agent，而是把它们稳定融入现有微服务体系。  
> 我从四个层面解决：  
> 1) 检索层：向量检索 + 关键词检索双通道，兼顾语义和精确；  
> 2) 生成层：用 Pydantic 做结构化输出约束，降低模型不确定性；  
> 3) 交互层：SSE 流式输出配合异常恢复，提升体验和稳定性；  
> 4) 集成层：OpenFeign + RocketMQ + Elastic-Job，打通跨语言调用和数据一致性闭环。  
> 这样系统不仅能“跑通”，还能在生产环境下长期可控。

---

## 六、来源与参考资料（已用于本资料校核）

- LangChain Persistence（InMemorySaver / checkpointer）：https://docs.langchain.com/oss/python/langgraph/persistence
- Spring Cloud OpenFeign（timeout/fallback）：https://docs.spring.io/spring-cloud-openfeign/docs/current/reference/html/
- RocketMQ Best Practices（幂等/重试/可靠性）：https://rocketmq.apache.org/zh/docs/4.x/bestPractice/01bestpractice/
- Redis Vector Search（KNN/Radius/混合检索）：https://redis.io/docs/latest/develop/ai/search-and-query/query/vector-search/
- FastAPI SSE（Server-Sent Events）：https://fastapi.tiangolo.com/tutorial/server-sent-events/
- Redis vs FAISS/Milvus：https://zilliz.com/comparison/faiss-vs-redis、https://redis.io/blog/milvus-vs-redis-vector-database-comparison/
- Elasticsearch IK 分词：https://www.alibabacloud.com/help/zh/es/user-guide/use-the-analysis-ik-plug-in
- RAG 工程化与面试讨论：https://blog.csdn.net/2401_84494441/article/details/153820632、https://www.cnblogs.com/limingqi/p/20068242

---

> 建议下一步：补充项目量化指标（召回提升比例、延迟改善、异常恢复成功率、同步成功率），进一步增强 STAR 说服力。
