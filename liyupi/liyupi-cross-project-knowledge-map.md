# 🗺️ 鱼皮项目 × 面试知识点全景映射表

> 这不是面试题，而是一个**反向索引**——当你准备某个面试知识点时，快速定位到鱼皮哪个项目、哪篇文档里有对应的实战代码和最佳实践。
>
> 覆盖 **35 个核心 Java 后端面试知识点**，映射到鱼皮 10+ 个开源项目。

---

## 快速导航

| 分类 | 知识点 |
|------|--------|
| 🏗️ **Spring 生态** | #1-5 |
| 🗄️ **数据库 & 缓存** | #6-13 |
| ⚡ **JVM & 并发** | #14-15 |
| 🧩 **设计模式 & 框架** | #16-19 |
| 🌐 **网络 & 通信** | #20-24 |
| 🐳 **容器 & 架构** | #25-26 |
| 🤖 **AI 应用** | #27-32 |
| 🏛️ **架构 & 安全** | #33-35 |

---

## 🏗️ Spring 生态

### 1. Spring Boot 自动装配

| 维度 | 内容 |
|------|------|
| **覆盖项目** | `springboot-guide`、`yu-rpc-spring-boot-starter`、所有 Spring Boot 项目 |
| **对应文档** | [learning-roadmap.md](./learning-roadmap.md) Phase 1 W1 |
| **关键代码/概念** | `@EnableAutoConfiguration` → `spring.factories` → `@Conditional` 条件装配；yu-rpc 的 `@EnableRpc` 注解驱动 Starter |
| **杀手句** | "自动装配的核心是 SpringFactoriesLoader 加载 `spring.factories` 中配置的 AutoConfiguration 类，再通过 `@ConditionalOnClass`/`@ConditionalOnMissingBean` 等条件注解按需生效，最终实现『约定优于配置』。" |

### 2. Spring Cloud Gateway

| 维度 | 内容 |
|------|------|
| **覆盖项目** | `yu-ai-code-mother` (gateway-service) |
| **对应文档** | [yu-ai-code-mother-analysis.md](./yu-ai-code-mother-analysis.md) §3.5 |
| **关键代码/概念** | 路由配置 `lb://yu-ai-code-mother-ai`；断言工厂 `Path=/api/ai/**`；过滤器链；Gateway 与 Sentinel 集成限流 |
| **杀手句** | "Gateway 基于 WebFlux 响应式编程，路由匹配通过**断言工厂链**顺序执行，过滤器链支持全局/局部两种粒度，配合 Sentinel 实现网关级限流熔断。" |

### 3. Nacos 注册/配置中心

| 维度 | 内容 |
|------|------|
| **覆盖项目** | `yu-ai-code-mother` (微服务层) |
| **对应文档** | [yu-ai-code-mother-analysis.md](./yu-ai-code-mother-analysis.md) §3.5 |
| **关键代码/概念** | `namespace: dev` 环境隔离；配置热更新 `@RefreshScope`；AP vs CP 模式切换；心跳检测机制 |
| **杀手句** | "Nacos 区别于 Eureka 的核心是同时支持 AP 和 CP 模式——临时实例用 AP（心跳检测），持久实例用 CP（Raft 协议），注册中心通常选 AP 保证可用性。" |

### 4. Sentinel 限流熔断

| 维度 | 内容 |
|------|------|
| **覆盖项目** | `mianshiya-next`、`yu-ai-code-mother` |
| **对应文档** | [mianshiya-next-deep-dive.md](./mianshiya-next-deep-dive.md) §4.2、[yu-ai-code-mother-analysis.md](./yu-ai-code-mother-analysis.md) §3.5 |
| **关键代码/概念** | 参数限流 `ParamFlowRule` 按 IP 限流；慢调用熔断（响应>3s+比例>20%→熔断60s）；异常率熔断；规则持久化到本地 JSON |
| **杀手句** | "Sentinel 的滑动窗口统计以秒级精度计算 QPS/RT，相比 Hystrix 的信号量隔离，Sentinel 支持**热点参数限流**和**熔断后慢调用恢复探测**，更适合精细化流量治理。" |

### 5. Seata 分布式事务

| 维度 | 内容 |
|------|------|
| **覆盖项目** | `yu-ai-code-mother` (微服务层) |
| **对应文档** | [yu-ai-code-mother-analysis.md](./yu-ai-code-mother-analysis.md) §3.5、[interview-value-guide.md](./interview-value-guide.md) §2.1 |
| **关键代码/概念** | AT/TCC/Saga 模式对比；`@GlobalTransactional`；全局锁机制；二阶段提交回滚 |
| **杀手句** | "Seata AT 模式通过**代理数据源自动生成回滚 SQL**，对业务代码零侵入；TCC 模式适合性能敏感场景，但需要手动实现 Try-Confirm-Cancel 三阶段；Saga 适合长事务，通过状态机编排补偿操作。" |

---

## 🗄️ 数据库 & 缓存

### 6. MyBatis-Plus

| 维度 | 内容 |
|------|------|
| **覆盖项目** | `mianshiya-next`、`yu-ai-code-mother`、`sql-father-backend` |
| **对应文档** | [mianshiya-next-deep-dive.md](./mianshiya-next-deep-dive.md) §7 |
| **关键代码/概念** | `BaseMapper` 泛型 CRUD；`LambdaQueryWrapper` 链式查询；逻辑删除 `@TableLogic`；分页插件 `PaginationInnerInterceptor` |
| **杀手句** | "MyBatis-Plus 的 LambdaQueryWrapper 通过**编译期类型推导**避免字段名硬编码，相比 JPA 的 HQL 更灵活，适合复杂查询和手写 SQL 的场景。" |

### 7. Redis 缓存策略

| 维度 | 内容 |
|------|------|
| **覆盖项目** | `mianshiya-next` (HotKey)、`yu-picture` (多级缓存)、`yu-ai-code-mother` |
| **对应文档** | [mianshiya-next-deep-dive.md](./mianshiya-next-deep-dive.md) §4.3、[frontend-projects-analysis.md](./frontend-projects-analysis.md) yu-picture 部分 |
| **关键代码/概念** | 缓存穿透（布隆过滤器/空值缓存）、缓存击穿（互斥锁/逻辑过期）、缓存雪崩（随机过期/多级缓存）；HotKey 探测 + Caffeine L1 缓存 |
| **杀手句** | "缓存穿透用布隆过滤器拦截不存在 key，缓存击穿用互斥锁或逻辑过期只让一个线程重建缓存，缓存雪崩通过过期时间加随机偏移来避免批量失效——**三个问题本质不同，解决方案必须区分对待**。" |

### 8. Redis 分布式锁

| 维度 | 内容 |
|------|------|
| **覆盖项目** | `mianshiya-next` (Redisson)、`yu-ai-code-mother` |
| **对应文档** | [mianshiya-next-deep-dive.md](./mianshiya-next-deep-dive.md) §4.5 |
| **关键代码/概念** | Redisson `RLock`；`lock()` vs `tryLock(waitTime, leaseTime, unit)`；WatchDog 自动续期；可重入锁语义 |
| **杀手句** | "Redisson 分布式锁的核心是 Lua 脚本保证原子性，WatchDog 机制每 10 秒自动续期防止业务未完成锁就过期，**比手写 SETNX + EXPIRE 更安全**——但 RedLock 在极端场景下仍有时钟漂移风险。" |

### 9. Redis BitMap

| 维度 | 内容 |
|------|------|
| **覆盖项目** | `mianshiya-next` |
| **对应文档** | [mianshiya-next-deep-dive.md](./mianshiya-next-deep-dive.md) §4.5 |
| **关键代码/概念** | `RBitSet` 签到日历；`bitSet.set(dayOfYear)` 签到；`bitSet.get(dayOfYear)` 查询；一年约 46 字节空间 |
| **杀手句** | "BitMap 用 1 位表示一个状态，存储 365 天签到记录仅需 46 字节，相比数据库 365 行**空间效率提升 3 个数量级**，适合签到、在线用户统计等二值状态场景。" |

### 10. Redis Lua 脚本

| 维度 | 内容 |
|------|------|
| **覆盖项目** | `mianshiya-next` |
| **对应文档** | [mianshiya-next-deep-dive.md](./mianshiya-next-deep-dive.md) §4.6 |
| **关键代码/概念** | `CounterManager` Lua 原子计数器；1 分钟内访问 >10 次警告、>20 次封号；`redis.call('INCR', key)` + `EXPIRE` |
| **杀手句** | "Redis Lua 脚本通过**原子执行**保证多命令的完整性和隔离性，避免竞态条件——反爬虫计数器中 INCR + EXPIRE 必须在 Lua 内完成，否则高并发下会漏计数。" |

### 11. Elasticsearch 全文搜索

| 维度 | 内容 |
|------|------|
| **覆盖项目** | `mianshiya-next` |
| **对应文档** | [mianshiya-next-deep-dive.md](./mianshiya-next-deep-dive.md) §4.4 |
| **关键代码/概念** | `BoolQueryBuilder` + `multiMatchQuery`；`NativeSearchQuery` 构建；`IncSyncQuestionToEs` 增量同步定时任务；ES ↔ MySQL 数据一致性 |
| **杀手句** | "ES 的 `multiMatchQuery` 跨字段检索配合 `boolQuery` 的 must/should/filter 组合，实现灵活的相关性评分；增量同步通过定时任务 + 版本号机制保证 ES 与 MySQL 的**最终一致性**。" |

### 12. MySQL 索引优化

| 维度 | 内容 |
|------|------|
| **覆盖项目** | `sql-mother`、`sql-father`、`mianshiya-next` |
| **对应文档** | [mianshiya-next-deep-dive.md](./mianshiya-next-deep-dive.md) §3、[frontend-projects-analysis.md](./frontend-projects-analysis.md) sql-mother 部分 |
| **关键代码/概念** | 复合索引 `(appId, createTime)` 支持游标分页；最左匹配原则；覆盖索引；`EXPLAIN` 分析；sql-mother Wasm 引擎执行 SQL |
| **杀手句** | "联合索引 `(a, b, c)` 实际创建了 `(a)`、`(a,b)`、`(a,b,c)` 三个索引——**最左匹配原则**决定了跳过的列之后的索引失效，设计时要把等值条件放前面、范围条件放后面。" |

### 13. MySQL 分表

| 维度 | 内容 |
|------|------|
| **覆盖项目** | `yu-picture` |
| **对应文档** | [frontend-projects-analysis.md](./frontend-projects-analysis.md) yu-picture 部分 |
| **关键代码/概念** | ShardingSphere 按 `spaceId` 动态分表 `picture_{spaceId}`；分片键选择；跨分片查询限制 |
| **杀手句** | "ShardingSphere 的分表策略要选**业务均匀分布且查询必带**的字段作为分片键，避免跨分片 JOIN 和分布式事务——yu-picture 按 spaceId 分表，天然隔离不同空间的数据访问。" |

---

## ⚡ JVM & 并发

### 14. JVM 调优

| 维度 | 内容 |
|------|------|
| **覆盖项目** | `yu-rpc` (性能测试)、`java-concurrent` |
| **对应文档** | [learning-roadmap.md](./learning-roadmap.md) Phase 1、[yu-rpc-deep-dive.md](./yu-rpc-deep-dive.md) 面试话术 |
| **关键代码/概念** | 堆内存分配（-Xms/-Xmx）；GC 选型（G1/ZGC）；OOM 分析（MAT/Arthas）；yu-rpc 性能测试经验 |
| **杀手句** | "JVM 调优三步走：先用 jstat 看 GC 频率和停顿，再用 jmap dump 堆转储分析大对象，最后调整 -Xms/-Xmx 和 GC 线程数——**不要盲目抄参数，要基于监控数据做决策**。" |

### 15. 线程池

| 维度 | 内容 |
|------|------|
| **覆盖项目** | `java-concurrent`、`yu-picture` (Disruptor) |
| **对应文档** | [learning-roadmap.md](./learning-roadmap.md) Phase 1 W2、[frontend-projects-analysis.md](./frontend-projects-analysis.md) yu-picture 部分 |
| **关键代码/概念** | 核心参数 `corePoolSize`/`maxPoolSize`/`workQueue`/`rejectedExecutionHandler`；`ThreadPoolExecutor` 工作原理；Disruptor 无锁队列比线程池更高吞吐 |
| **杀手句** | "线程池的核心参数要根据任务类型动态调整：**CPU 密集型**线程数 = CPU 核数 + 1，**IO 密集型**线程数 = CPU 核数 × (1 + 等待时间/计算时间)；队列用有界队列防止 OOM。" |

---

## 🧩 设计模式 & 框架

### 16. 设计模式

| 维度 | 内容 |
|------|------|
| **覆盖项目** | `Design-Model`、`yu-rpc`、`yu-ai-code-mother`、`yu-ai-agent` |
| **对应文档** | [interview-value-guide.md](./interview-value-guide.md) §4、[yu-rpc-deep-dive.md](./yu-rpc-deep-dive.md) §7.1、[yu-ai-agent-deep-dive.md](./yu-ai-agent-deep-dive.md) §3 |
| **关键代码/概念** | 策略模式（负载均衡）、模板方法（Agent 四层继承）、观察者模式（SSE 推送）、工厂模式（SPI 序列化器）、代理模式（JDK 动态代理）、构建者模式（LangChain4j Chain） |
| **杀手句** | "鱼皮项目中最极致的模式应用是 Agent 四层继承——**模板方法模式**固定骨架（BaseAgent 的 run/step 循环），**策略模式**让子类自定义 think/act 逻辑，每层只关注一个维度的变化。" |

### 17. RPC 原理

| 维度 | 内容 |
|------|------|
| **覆盖项目** | `yu-rpc` |
| **对应文档** | [yu-rpc-deep-dive.md](./yu-rpc-deep-dive.md) 全文、[yu-rpc-interview-questions.md](./yu-rpc-interview-questions.md) |
| **关键代码/概念** | 动态代理（JDK Proxy）→ 序列化（4种）→ 网络通信（Vert.x TCP）→ 注册中心（Etcd/ZK）→ 负载均衡 → 重试/容错 |
| **杀手句** | "RPC 的本质是**让远程调用像本地调用一样透明**：动态代理拦截方法调用，把类名、方法名、参数序列化后通过网络传输，接收端反序列化后反射执行——整个流程对调用方完全透明。" |

### 18. 序列化

| 维度 | 内容 |
|------|------|
| **覆盖项目** | `yu-rpc` |
| **对应文档** | [yu-rpc-deep-dive.md](./yu-rpc-deep-dive.md) §4 |
| **关键代码/概念** | JDK 序列化（兼容最好）、JSON/Jackson（调试友好）、Kryo（高性能，ThreadLocal 包装）、Hessian（跨语言）；SPI 可插拔 |
| **杀手句** | "Kryo 比 JDK 序列化快 10 倍以上，但必须注意**线程安全**——用 ThreadLocal 包装 Kryo 实例，避免多线程竞争导致的序列化异常。" |

### 19. SPI 扩展机制

| 维度 | 内容 |
|------|------|
| **覆盖项目** | `yu-rpc` |
| **对应文档** | [yu-rpc-deep-dive.md](./yu-rpc-deep-dive.md) §4.2 |
| **关键代码/概念** | 自研 SPI 支持 key→class 映射；`META-INF/rpc/system/` 内置 + `META-INF/rpc/custom/` 用户扩展；单例缓存；按需加载 |
| **杀手句** | "自研 SPI 相比 Java SPI 的优势在于：Java SPI 只能按接口**全量加载所有实现**，而自研 SPI 支持按名称获取具体实现，且优先级 system < custom 实现框架内置和用户扩展的隔离。" |

---

## 🌐 网络 & 通信

### 20. 负载均衡算法

| 维度 | 内容 |
|------|------|
| **覆盖项目** | `yu-rpc` |
| **对应文档** | [yu-rpc-deep-dive.md](./yu-rpc-deep-dive.md) §6 |
| **关键代码/概念** | 随机（Random）、轮询（RoundRobin）、一致性哈希（ConsistentHash）；策略模式实现；`@RpcReference(loadBalancer = "roundRobin")` |
| **杀手句** | "负载均衡的三个策略对应不同场景：随机适合无状态服务、轮询适合权重均等、一致性哈希适合有状态缓存——**面试重点在一致性哈希的虚拟节点机制**。" |

### 21. 一致性哈希

| 维度 | 内容 |
|------|------|
| **覆盖项目** | `yu-rpc` |
| **对应文档** | [yu-rpc-deep-dive.md](./yu-rpc-deep-dive.md) §6.1 |
| **关键代码/概念** | TreeMap 环形空间；100 个虚拟节点/真实节点；`ceilingEntry(hash)` 顺时针查找；`firstEntry()` 环形回绕 |
| **杀手句** | "一致性哈希通过虚拟节点（每个真实节点映射 100 个虚拟节点）解决节点分布不均问题，增减节点时只影响环上相邻节点——**从 100 个节点减少到 99 个，只有 1% 的 key 需要迁移**。" |

### 22. TCP 粘包半包

| 维度 | 内容 |
|------|------|
| **覆盖项目** | `yu-rpc` |
| **对应文档** | [yu-rpc-deep-dive.md](./yu-rpc-deep-dive.md) §7.1、[yu-rpc-interview-questions.md](./yu-rpc-interview-questions.md) Q4 |
| **关键代码/概念** | 17 字节定长 Header（含 bodyLength 字段）；Vert.x `RecordParser` 先读 Header 再读 Body；固定长度解码器 |
| **杀手句** | "TCP 是字节流协议没有消息边界，解决方案是**自定义协议头 + 长度字段**：接收端先读固定 17 字节 Header，提取 bodyLength 后读完整消息体，再用 RecordParser 切换回 Header 模式——本质是把字节流『切』成消息帧。" |

### 23. WebSocket

| 维度 | 内容 |
|------|------|
| **覆盖项目** | `yu-picture` |
| **对应文档** | [frontend-projects-analysis.md](./frontend-projects-analysis.md) yu-picture 部分 |
| **关键代码/概念** | WebSocket 实时图片协同编辑；LMAX Disruptor 无锁队列处理事件；双向通信 vs SSE 单向推送 |
| **杀手句** | "WebSocket 适合**双向实时通信**场景（协同编辑、即时消息），而 SSE 适合**服务端单向推送**（AI 流式生成）；yu-picture 用 Disruptor 无锁队列处理 WebSocket 事件，相比 LinkedBlockingQueue 吞吐量提升 3-5 倍。" |

### 24. SSE 流式输出

| 维度 | 内容 |
|------|------|
| **覆盖项目** | `yu-ai-code-mother`、`yu-ai-agent` |
| **对应文档** | [yu-ai-code-mother-analysis.md](./yu-ai-code-mother-analysis.md) §3.4、[yu-ai-agent-deep-dive.md](./yu-ai-agent-deep-dive.md) §8 |
| **关键代码/概念** | `SseEmitter` + `TokenStream`；`onPartialResponse` / `onCompleteResponse`；`produces = MediaType.TEXT_EVENT_STREAM_VALUE`；60s 超时设计 |
| **杀手句** | "SSE 基于**标准 HTTP 协议**，相比 WebSocket 优势在于：天然支持自动重连、穿透防火墙和代理、实现简单——AI 流式输出场景是『服务端→前端』单向推送，SSE 比 WebSocket 更轻量。" |

---

## 🐳 容器 & 架构

### 25. Docker 容器化

| 维度 | 内容 |
|------|------|
| **覆盖项目** | `yu-ai-code-mother` (deploy-service) |
| **对应文档** | [yu-ai-code-mother-analysis.md](./yu-ai-code-mother-analysis.md) §4 |
| **关键代码/概念** | Dockerfile 多阶段构建；`docker-compose.yml` 编排 MySQL/Redis/Nacos；K8s 部署；一键部署面板 |
| **杀手句** | "Docker 多阶段构建将构建环境和运行环境分离，最终镜像仅包含运行时依赖——Spring Boot 应用从 200MB+ 压缩到 50MB 以下，同时减少攻击面。" |

### 26. 微服务架构

| 维度 | 内容 |
|------|------|
| **覆盖项目** | `yu-ai-code-mother` (7 个微服务)、`mianshiya-next` (单体+微服务组件) |
| **对应文档** | [yu-ai-code-mother-analysis.md](./yu-ai-code-mother-analysis.md) §4、[mianshiya-next-deep-dive.md](./mianshiya-next-deep-dive.md) §2 |
| **关键代码/概念** | 7 服务拆分（gateway/user/ai/code/preview/deploy/monitor）；服务间通信（Feign/MQ）；分布式治理（Nacos+Sentinel+Seata）；全链路监控 |
| **杀手句** | "微服务拆分遵循**单一职责 + 业务边界**原则，AI 服务独立拆分是因为：AI 模型调用是 IO 密集型且响应时间长，独立部署可以独立扩缩容，不影响其他业务接口的响应速度。" |

---

## 🤖 AI 应用

### 27. AI Agent (ReAct)

| 维度 | 内容 |
|------|------|
| **覆盖项目** | `yu-ai-agent` |
| **对应文档** | [yu-ai-agent-deep-dive.md](./yu-ai-agent-deep-dive.md) §3、[ai-projects-interview-bank.md](./ai-projects-interview-bank.md) A1-A4 |
| **关键代码/概念** | 四层继承体系（BaseAgent → ReActAgent → ToolCallAgent → YuManus）；Think-Act 循环；`maxSteps=10/20` 兜底；AgentState 状态机（IDLE→RUNNING→FINISHED/ERR） |
| **杀手句** | "ReAct 模式将 LLM 的推理能力（Reasoning）和工具调用能力（Acting）交替执行——**think() 让 LLM 自主决策是否调用工具，act() 执行工具并写回上下文**，三重终止保险（模型主动结束 + TerminateTool + maxSteps 兜底）防止无限循环。" |

### 28. RAG 检索增强

| 维度 | 内容 |
|------|------|
| **覆盖项目** | `yu-ai-agent`、`ai-guide` |
| **对应文档** | [yu-ai-agent-deep-dive.md](./yu-ai-agent-deep-dive.md) §5、[ai-projects-interview-bank.md](./ai-projects-interview-bank.md) A5 |
| **关键代码/概念** | 五段链路（加载→向量化→检索→增强→生成）；PgVector 向量库；QueryRewriter 查询改写；按状态过滤文档（单身/恋爱/已婚） |
| **杀手句** | "RAG 的核心价值是**用外部知识库『锚定』LLM 的输出，减少幻觉**——但关键不在检索，而在『增强』环节：QueryRewriter 改写用户查询对齐文档语义、关键词丰富补 exact-match 信号、按状态过滤减少 token 浪费。" |

### 29. Tool Calling

| 维度 | 内容 |
|------|------|
| **覆盖项目** | `yu-ai-agent`、`yu-ai-code-mother` |
| **对应文档** | [yu-ai-agent-deep-dive.md](./yu-ai-agent-deep-dive.md) §4、[yu-ai-code-mother-analysis.md](./yu-ai-code-mother-analysis.md) §3.3 |
| **关键代码/概念** | 禁用 Spring AI 自动执行（`withInternalToolExecutionEnabled(false)`）；`@Tool` 注解注册工具；7 个工具（搜索/抓取/文件/终端/下载/PDF/终止）；`ToolCallingManager.executeToolCalls()` |
| **杀手句** | "Tool Calling 的关键设计是**手动控制 think/act 循环**——禁用框架自动执行后，think() 只做决策『LLM 决定调什么工具』，act() 只做执行『代码调用工具并写回结果』，决策权在 LLM，控制权在代码。" |

### 30. LangChain4j / Spring AI

| 维度 | 内容 |
|------|------|
| **覆盖项目** | `yu-ai-code-mother` (LangChain4j)、`yu-ai-agent` (Spring AI) |
| **对应文档** | [yu-ai-code-mother-analysis.md](./yu-ai-code-mother-analysis.md) §3.1、[yu-ai-agent-deep-dive.md](./yu-ai-agent-deep-dive.md) §7、[tech-trends-analysis.md](./tech-trends-analysis.md) §2.1 |
| **关键代码/概念** | LangChain4j `@AiService` 声明式接口（类似 MyBatis Mapper）；Spring AI `ChatClient` 流式 API；两种框架对比选型 |
| **杀手句** | "LangChain4j 的 `@AiService` 声明式接口和 MyBatis 的 `@Mapper` 本质是同一思想——**接口定义行为，注解注入语义**，运行时通过动态代理生成实现；Spring AI 的 `ChatClient` 则更偏向函数式编程风格，适合快速集成 Spring 生态。" |

### 31. LangGraph4j 工作流

| 维度 | 内容 |
|------|------|
| **覆盖项目** | `yu-ai-code-mother` |
| **对应文档** | [yu-ai-code-mother-analysis.md](./yu-ai-code-mother-analysis.md) §3.2、[ai-projects-interview-bank.md](./ai-projects-interview-bank.md) B3 |
| **关键代码/概念** | `StateGraph<AgentState>` 编排多步骤；节点（analyze→design→generate→review）；条件边 `needsReview()`；状态机驱动 |
| **杀手句** | "LangGraph4j 的 StateGraph 把 AI 工作流从『一步 Prompt』升级为**有向图多步骤编排**：每个节点专注一个任务（需求分析/架构设计/代码生成/代码审查），条件边支持分支决策，状态机驱动整个流程——比一个大 Prompt 更可控、可观测、可调试。" |

### 32. MCP 协议

| 维度 | 内容 |
|------|------|
| **覆盖项目** | `yu-ai-agent`、`ai-guide` |
| **对应文档** | [yu-ai-agent-deep-dive.md](./yu-ai-agent-deep-dive.md) §6、[tech-trends-analysis.md](./tech-trends-analysis.md) §2.3 |
| **关键代码/概念** | `mcp-servers.json` 配置两个 Server；自研 `yu-image-search-mcp-server`（SSE + stdio 双模式）；MCP = AI 的 USB-C 接口 |
| **杀手句** | "MCP 是 AI 应用的**标准化协议层**，类比 USB-C 统一了外设接口——同一个 Client 通过 MCP 协议连接多个 Server，新增能力只需再配一个 Server 配置，无需修改代码。SSE 适合远程部署，stdio 适合本地零网络开销。" |

---

## 🏛️ 架构 & 安全

### 33. DDD 领域驱动

| 维度 | 内容 |
|------|------|
| **覆盖项目** | `yu-picture` (DDD 版本) |
| **对应文档** | [frontend-projects-analysis.md](./frontend-projects-analysis.md) yu-picture 部分 |
| **关键代码/概念** | 四层架构（interfaces/application/domain/infrastructure）；picture/space/user 三个聚合；传统三层 vs DDD 双版本对比 |
| **杀手句** | "DDD 的核心是**业务复杂度和架构复杂度的匹配**——yu-picture 同时维护了传统三层和 DDD 两个版本，当业务逻辑简单时三层架构更高效，当业务逻辑复杂（如多聚合交互）时 DDD 的领域层能有效防止业务逻辑散落在 Service 层。" |

### 34. Sa-Token 认证

| 维度 | 内容 |
|------|------|
| **覆盖项目** | `mianshiya-next`、`yu-picture` |
| **对应文档** | [mianshiya-next-deep-dive.md](./mianshiya-next-deep-dive.md) §4.1 |
| **关键代码/概念** | `@SaCheckRole("admin")` 注解鉴权；`StpUtil.login(userId, device)` 同端互斥；RBAC 角色权限模型；比 Spring Security 更轻量 |
| **杀手句** | "Sa-Token 相比 Spring Security 的优势在于：**原生支持同端登录互斥**（`StpUtil.login` 传入设备类型，同设备新登录自动踢掉旧登录），以及注解式鉴权更简洁——适合中小型项目快速集成。" |

### 35. 三级缓存 (Caffeine+Redis+DB)

| 维度 | 内容 |
|------|------|
| **覆盖项目** | `mianshiya-next` (HotKey + Caffeine)、`yu-picture` |
| **对应文档** | [mianshiya-next-deep-dive.md](./mianshiya-next-deep-dive.md) §4.3 |
| **关键代码/概念** | L1 Caffeine（本地内存，μs 级）→ L2 Redis（分布式，ms 级）→ L3 MySQL（持久化）；HotKey 探测自动升温；`smartSet` 回写 |
| **杀手句** | "三级缓存架构的本质是**不同层级用不同速度的存储热数据**：HotKey 探测到热 key 后自动将其从 L2 Redis 升温到 L1 Caffeine，本地缓存 μs 级响应，分布式缓存保证一致性，数据库兜底持久化——但需要处理 L1 的缓存一致性问题（订阅 Redis key 过期事件主动失效）。" |

---

## 附录：文档速查表

| 面试知识点 | 速查文档 | 对应项目 | 页码 |
|-----------|---------|---------|------|
| Spring Boot 自动装配 | learning-roadmap.md Phase 1 W1 | springboot-guide | — |
| Spring Cloud 微服务 | yu-ai-code-mother-analysis.md §3.5 | yu-ai-code-mother | §3.5 |
| Redis 缓存/锁/BitMap | mianshiya-next-deep-dive.md §4 | mianshiya-next | §4.1-4.6 |
| ES 全文搜索 | mianshiya-next-deep-dive.md §4.4 | mianshiya-next | §4.4 |
| RPC 全体系 | yu-rpc-deep-dive.md 全文 | yu-rpc | §1-9 |
| AI Agent ReAct | yu-ai-agent-deep-dive.md §3 | yu-ai-agent | §3 |
| Tool Calling | yu-ai-agent-deep-dive.md §4 | yu-ai-agent | §4 |
| RAG 全链路 | yu-ai-agent-deep-dive.md §5 | yu-ai-agent | §5 |
| MCP 协议 | yu-ai-agent-deep-dive.md §6 | yu-ai-agent | §6 |
| LangChain4j/LangGraph4j | yu-ai-code-mother-analysis.md §3 | yu-ai-code-mother | §3.1-3.2 |
| DDD 架构 | frontend-projects-analysis.md yu-picture | yu-picture | — |
| 设计模式 | interview-value-guide.md §4 | 多个项目 | §4 |
| 面试 STAR 话术 | interview-value-guide.md §1 | 多个项目 | §1 |
| AI 面试题库 | ai-projects-interview-bank.md | yu-ai-agent/code-mother | 全文 |
| RPC 面试题库 | yu-rpc-interview-questions.md | yu-rpc | 全文 |
| 技术趋势分析 | tech-trends-analysis.md | 全局 | 全文 |

---

*生成日期: 2026-08-22 | 知识点数: 35 | 覆盖项目: 10+ | 映射文档: 12 篇*