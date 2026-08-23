# 🧠 AI 应用方向面试题库（20 题）

> 覆盖 yu-ai-agent + yu-ai-code-mother 两大项目
> 目标读者：Java 后端转 AI 应用开发的面试者

---

## yu-ai-agent — Spring AI ReAct Agent（A1-A10）

| # | 问题 | 难度 | 分类 |
|---|------|------|------|
| A1 | Agent 为什么设计成四层继承（Base→ReAct→ToolCall→YuManus）？ | ⭐⭐⭐⭐ | 架构设计 |
| A2 | 为什么禁用 Spring AI 的自动工具执行，改为手动控制 think/act 循环？ | ⭐⭐⭐⭐⭐ | 源码分析 |
| A3 | ReAct 模式的 think() 和 act() 如何协同，循环何时终止？ | ⭐⭐⭐⭐ | AI原理 |
| A4 | AgentState 状态机解决了什么问题？并发下如何保证正确性？ | ⭐⭐⭐ | 架构设计 |
| A5 | RAG 全链路是什么？QueryRewriter + 关键词丰富 + 按状态过滤的作用？ | ⭐⭐⭐⭐ | AI原理 |
| A6 | MCP 集成中既做 Client 又自研 Server，SSE 与 stdio 怎么选？ | ⭐⭐⭐⭐ | 场景设计 |
| A7 | 7 个工具里 Terminal/File 存在安全风险，怎么防护？ | ⭐⭐⭐⭐ | 场景设计 |
| A8 | runStream() 如何实现流式输出？为什么优先选 SSE 而不是 WebSocket？ | ⭐⭐⭐ | 架构设计 |
| A9 | 为什么自己维护 messageList 而不是用 Spring AI 的 ChatMemory？ | ⭐⭐⭐ | 源码分析 |
| A10 | Spring AI 与 LangChain4j 对比？生产中怎么选型？ | ⭐⭐⭐⭐ | 架构设计 |

### A1. 四层继承体系的动机

> **核心是关注点分离 + 模板方法模式**。BaseAgent 只负责"骨架"：状态机、会话上下文 messageList、执行循环 run()（含 maxSteps 上限），定义抽象 step()。ReActAgent 定义"思考-行动"两段式：抽象 think() 判断是否需要工具、act() 执行工具。ToolCallAgent 才真正落地：think() 调 LLM 解析 ToolCalls，act() 用 ToolCallingManager 执行并写回结果，检测 TerminateTool 置 FINISHED。YuManus 是组装层，注入 7 个工具、配 maxSteps=20。
>
> **每个层次改动互不影响**——要新增"纯对话 Agent"只需复用 BaseAgent；要新增工具型 Agent 只需继承 ReActAgent；工具增删只发生在 YuManus 组装层。

### A2. 为什么禁用自动工具执行

> Spring AI 默认行为：LLM 返回 ToolCall → 框架**自动执行工具 → 自动回传模型 → 再次调用 LLM**，整个过程对开发者近乎黑盒。做**自主 Agent** 时致命：① 无法设置每轮之间的自定义逻辑；② 没有 maxSteps 概念，可能无限循环浪费 token；③ 终止语义不可靠。
>
> 禁用后 `.withInternalToolExecutionEnabled(false)`，think() 只做"决策"，act() 只做"执行"。**决策权在 LLM，控制权在代码**。面试时能点出"自动执行=黑盒、手动执行=可控"就说明读过源码。

### A3. ReAct 循环与终止条件

> ReAct 是 Reasoning + Acting 的交替：think() 发消息给 LLM，返回 toolCalls 为空→false→循环结束；不为空→true→act() 执行工具→结果追加到 messageList→下一轮 think()。
>
> **三重保险**：① think() 返回 false（模型主动结束）；② 调用 TerminateTool 状态置 FINISHED（显式声明）；③ maxSteps=10/20 上限（兜底防死循环）。

### A4. AgentState 状态机

> IDLE→RUNNING→FINISHED/ERR 一个枚举覆盖生命周期。价值：① 执行循环前置校验防重复执行；② 前端可轮询状态做进度展示；③ 异常路径统一标记 ERR。并发正确性：run() 循环是单线程顺序执行，天然串行；状态字段声明 volatile 保证可见性。

### A5. RAG 全链路

> **五段链路**：加载（MarkdownDocumentReader + metadata）→ 向量化存储（PgVector/内置/云RAG三档）→ 检索（相似度TopK）→ 增强（QueryRewriter改写 + 关键词丰富 + 按单身/恋爱/已婚状态过滤）→ 生成。
>
> **三个增强点**：QueryRewriter 解决"用户表述与文档不对齐"；关键词丰富补 exact-match 信号；按状态过滤在 Advisor 层拦截文档，既准确又省 token。

### A6. MCP 双模式

> **Client 端**：mcp-servers.json 声明 Server，Spring AI 启动时连接并拉取工具清单。**Server 端**：自研图片搜索服务，stdio 走本地标准输入输出（零网络开销、调试场景），SSE 走 HTTP（远程部署、跨网络调用）。MCP 的价值：同一个 Client 里多个 Server 能力叠加，新增能力只需再配一个 Server。

### A7. 工具安全防护

> 防护分层：① **参数白名单**：文件工具限制可访问目录、终端只允许预置命令集；② **权限收敛**：Runtime.exec 用最小权限账号；③ **资源限制**：下载校验大小、执行加超时；④ **输入净化**：工具结果里"像指令"的文本做剥离；⑤ **审计**：全量日志。生产极端做法是沙箱化执行（Docker 容器）。

### A8. 流式输出 SSE

> runStream() 把每一步产物变成事件流实时推给前端。选 SSE 非 WebSocket：① Agent 场景是单向"服务端→前端"；② SSE 基于标准 HTTP，天然支持自动重连、穿透代理；③ 实现简单。注意点：保活心跳、背压处理、客户端断开时取消底层 LLM 调用。

### A9. 自主维护 messageList

> Agent 需要"精确控制上下文内容"，框架记忆是面向普通多轮对话的 FIFO 窗口。Agent 一次任务可能 10+ 轮 think/act，需要区分消息语义优先级、自主决定何时裁剪/压缩。框架默认是"聊天记忆"，Agent 需要的是"执行记忆"。

### A10. Spring AI vs LangChain4j

> **Spring AI**：Spring 官方、ChatClient 简单、与 Boot 无缝集成。**LangChain4j**：@AiService 声明式接口、LangGraph4j 工作流编排。选型：Spring 技术栈→Spring AI；复杂工作流→LangChain4j。二者主干能力高度同构，迁移成本在 API 层。核心认知："框架只是壳，Prompt/Tool/状态编排的工程能力"才是重点。

---

## yu-ai-code-mother — LangChain4j + 微服务 AI 平台（B1-B10）

| # | 问题 | 难度 | 分类 |
|---|------|------|------|
| B1 | 微服务怎么拆的？AI 服务为什么必须独立？ | ⭐⭐⭐⭐ | 架构设计 |
| B2 | LangChain4j @AiService 声明式编程原理，和 MyBatis Mapper 有何相似？ | ⭐⭐⭐⭐ | 源码分析 |
| B3 | 为什么用 LangGraph4j StateGraph 而不是一个大 Prompt？ | ⭐⭐⭐⭐ | AI原理 |
| B4 | SseEmitter + TokenStream 流式生成，60s 超时怎么理解？ | ⭐⭐⭐ | 架构设计 |
| B5 | CodeTools 里 executeQuery 是危险工具，怎么防护？ | ⭐⭐⭐⭐ | 场景设计 |
| B6 | 网关如何治理 AI 接口？SSE 字节流要注意什么？ | ⭐⭐⭐⭐ | 架构设计 |
| B7 | Sentinel 在 AI 场景的熔断降级和普通接口有何不同？ | ⭐⭐⭐ | 场景设计 |
| B8 | "模板 + LLM"代码生成的业务设计？为什么不全交给 LLM？ | ⭐⭐⭐ | 场景设计 |
| B9 | preview-service 用 WebSocket + Docker 做实时预览，隔离和安全？ | ⭐⭐⭐⭐ | 架构设计 |
| B10 | 平台接多个大模型，模型抽象层和成本控制怎么设计？ | ⭐⭐⭐⭐ | 架构设计 |

### B1. 微服务拆分

> 按 3+1+治理拆分：**网关层**（路由/鉴权/限流）+ **AI 核心服务**（Chat模型/Tool Calling/SSE/RAG）+ **业务服务**（用户/代码生成/预览/部署/监控）+ **治理底座**（Nacos/Sentinel/Seata）。AI 服务必须独立：① 稳定性隔离——LLM 调用慢且依赖外部，独立后可独立扩缩容；② 发布频率不同；③ 成本与监控——独立服务才能单独计量 token 和费用。

### B2. @AiService 声明式原理

> 本质是**"接口即 AI 能力"的动态代理**。@SystemMessage 提供系统指令，@UserMessage 声明用户输入模板，返回 String 走同步、返回 TokenStream 走流式。和 MyBatis Mapper 完全同构：**你只声明契约（接口+元数据），框架在代理里完成"绑定→执行→映射结果"**。

### B3. StateGraph 工作流价值

> 一个大 Prompt 的问题是**过程不可观测、不可干预、出错不可定位**。LangGraph4j 把它结构化成状态驱动的有向图：analyze→design→generate→review 四个节点，条件边 `addConditionalEdge` 实现分支。价值：① 可观测可测试；② 可干预（人工审批、重试）；③ 组合性强（规则判定而非 LLM 自评）；④ 可恢复（State 可持久化）。一句话：从"提示工程"升级为"流程工程"。

### B4. SSE 流式实现

> Controller 创建 `SseEmitter(60_000L)`，TokenStream 挂三个回调：onPartialResponse 实时推 token、onCompleteResponse 收尾、onError 报错。**60s 超时是"首 chunk 等待窗口"**而非总时长。坑点：网关响应缓冲会攒住 SSE 必须关闭；客户端断开后要取消 TokenStream 否则白烧 token；长时间无 token 要发心跳保活。

### B5. 危险工具安全设计

> executeQuery 让 LLM 直接执行 SQL。防护：① **只读账号**且库表级授权；② 语句白名单——只放行 SELECT/EXPLAIN/SHOW；③ **AST 解析器**（JSqlParser）解析再校验，不是字符串正则；④ LIMIT 强制加行数上限。部署侧 deployApp 做**人工确认环节**。核心三件套："**先解析再执行、最小权限、人类回退环**"。

### B6. 网关治理 AI 接口

> Gateway 统一收口鉴权、限流、超时。AI 接口治理重点：① **SSE 穿透**：关缓冲、关压缩，Content-Type 直通；② **限流粒度**：不仅看 QPS，还要看 token 配额（用户维度每日额度）；③ **超时策略**：网关超时大于服务端超时；④ **服务间信任**：鉴权在网关做，服务间调用另建信任。能说出"**SSE 会被网关缓冲**"就说明有生产经验。

### B7. Sentinel AI 场景降级

> AI 调用三个差异：响应慢（秒级+）、依赖外部不稳定、每次花钱。降级设计：① 信号量隔离限制并发调模型的路数；② 慢调用比例熔断（P95>10s 触发）；③ **fallback 分级**：读类返回语义缓存；非关键降级到小模型/模板；写类进 MQ 异步排队。**AI 任务不套强事务（Seata）**是加分点。

### B8. 模板 + LLM 代码生成

> LLM 负责"理解与决策"（需求分析、架构选型、差异点生成）；FreeMarker 模板负责"骨架与合规"（工程结构、依赖版本、安全基线）。不全交给 LLM：① 确定性——模板保证可编译、符合规范；② 成本——模板零 token；③ 可演进——改一个模板全平台生效。一句话：**用模板锁下限，用 LLM 抬上限**。

### B9. Docker 实时预览安全

> 预览容器运行 LLM 生成的不可信代码，等同执行任意代码。必须做：① cgroup 资源限制（CPU/内存/磁盘硬上限）；② 禁止特权模式、禁用宿主挂载、只读根文件系统；③ **出网控制**——默认断外网或白名单，防挖矿防数据外带；④ 每容器独立 user 非 root；⑤ 超时强杀+日志审计。安全基线 = **默认拒绝**。

### B10. 多模型接入与成本控制

> **抽象层**：统一 ChatModel 接口 + 策略/工厂模式按模型分派，密钥放 Nacos 热更新，不同厂商差异封装成 adapter 归一化。**成本五板斧**：① Prompt 瘦身（系统指令压缩、历史裁剪）；② 模型分级（贵模型干贵的活）；③ 配额限流（用户维度 token 额度）；④ 语义缓存（向量相似度命中）；⑤ 全链路计量（trace 记录模型/token/费用，出账单）。

---

*55 题逐题精讲，覆盖 AI 应用开发 80% 的面试深挖点*
