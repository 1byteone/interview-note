# Spring Cloud 微服务面试题

## 📚 知识点概览

Spring Cloud 是微服务架构的完整解决方案，包括服务发现、配置中心、网关、熔断器、分布式事务等组件。

## 🎯 面试题分类

### Level 1: 基础题

#### 微服务基础
1. **微服务架构**
   - 问题：什么是微服务架构？它与单体架构有什么区别？
   - 答案：微服务架构是将单体应用拆分为多个小型、独立部署的服务，每个服务围绕一个业务能力构建，通过轻量级通信机制（如 HTTP/REST）协作。与单体架构的区别在于：微服务是分布式部署、独立技术栈、独立数据库、独立扩容，而单体架构是一个进程包含所有功能模块。
   - 解析：单体架构的所有模块共享进程和数据库，耦合度高，一个模块的问题可能导致整个系统崩溃。微服务通过服务拆分实现解耦，每个服务可以独立开发、测试、部署。但微服务也引入了分布式一致性、网络延迟、服务治理等新挑战。核心理念是"单一职责"和"去中心化"，适合大型复杂系统和团队协作场景。

2. **Spring Cloud 核心组件**
   - 问题：Spring Cloud 的核心组件有哪些？各自的作用是什么？
   - 答案：Spring Cloud 核心组件包括：Nacos（服务注册与发现、配置中心）、Gateway（API 网关，统一入口）、OpenFeign（声明式 HTTP 客户端，服务间调用）、Sentinel（流量控制、熔断降级）、Seata（分布式事务）、Sleuth/Zipkin（链路追踪）、RocketMQ/Kafka（消息队列）。
   - 解析：这些组件构成了微服务架构的基础设施层。Nacos 解决服务注册与配置管理问题；Gateway 作为统一入口，实现路由转发、鉴权、限流；OpenFeign 简化了服务间 HTTP 调用；Sentinel 提供流量控制和熔断降级保护系统稳定性；Seata 解决分布式事务一致性问题；链路追踪组件用于监控和排查分布式系统的调用链路。各组件通过 Spring Cloud Netflix 或 Spring Cloud Alibaba 生态整合。

#### Nacos 基础
3. **Nacos 核心功能**
   - 问题：Nacos 的核心功能有哪些？
   - 答案：Nacos 的核心功能包括：1）服务注册与发现，支持临时实例和持久实例；2）配置管理，支持动态配置推送和灰度发布；3）健康检查，对临时实例采用心跳检测，对持久实例采用主动探测；4）集群管理，支持 CP/AP 模式切换。
   - 解析：Nacos 通过 HTTP 长轮询或 gRPC 实现配置的实时推送，客户端通过定时拉取 + 长轮询结合的方式获取配置变更。服务发现方面，临时实例通过心跳维持注册状态，超时自动剔除；持久实例需要手动注册，不会自动失效。Nacos 支持 AP（Distro 协议）和 CP（Raft 协议）两种模式，AP 模式适合服务发现场景，CP 模式适合配置中心场景。

4. **服务注册与发现**
   - 问题：Nacos 如何实现服务注册与发现？
   - 答案：服务启动时通过 Nacos Client 向 Nacos Server 发送注册请求（HTTP POST），包含服务名、IP、端口等信息。Nacos Server 存储服务实例列表，客户端通过定时拉取 + 长轮询机制获取最新的服务实例列表，并在本地缓存。负载均衡时从缓存的实例列表中选择目标实例。
   - 解析：Nacos 采用 CP + AP 混合模式。服务注册默认使用 AP 模式（Distro 协议），保证高可用；配置中心使用 CP 模式（Raft 协议），保证数据一致性。客户端每 10 秒发送心跳维持临时实例的注册状态，15 秒未收到心跳标记为不健康，30 秒未收到心跳则剔除实例。OpenFeign 和 RestTemplate 通过 `@LoadBalanced` 注解集成 Nacos 的服务发现能力，实现客户端负载均衡。

#### Gateway 基础
5. **API 网关**
   - 问题：什么是 API 网关？Gateway 的作用是什么？
   - 答案：API 网关是微服务架构的统一入口，负责请求路由、负载均衡、鉴权、限流、日志记录等横切关注点。Spring Cloud Gateway 基于 WebFlux + Netty 实现非阻塞异步处理，核心概念包括 Route（路由）、Predicate（断言）、Filter（过滤器）。
   - 解析：Gateway 的工作流程：请求进入后，由 Handler Mapping 根据路由断言（如路径、Header、时间等）匹配路由，交给 Handler Adapter 执行对应的过滤器链（前置过滤器 → 目标服务 → 后置过滤器），最终返回响应。相比 Zuul 1.x 的 Servlet 阻塞模型，Gateway 基于 Reactor 的非阻塞模型，单机吞吐量更高，延迟更低。Gateway 还支持动态路由、跨域配置、重试机制等功能。

6. **路由配置**
   - 问题：如何在 Gateway 中配置路由规则？
   - 答案：Gateway 支持三种路由配置方式：1）YAML 配置，在 application.yml 中定义 routes 列表；2）Java 代码配置，通过 RouteLocatorBuilder 构建路由；3）Nacos 动态配置，结合 Nacos Config 实现路由的动态刷新。路由关键字段包括 id、uri、predicates、filters。
   - 解析：YAML 配置示例：`spring.cloud.gateway.routes[0].id=service-route`，`uri=lb://service-name`（lb 表示负载均衡），predicates 支持 Path、Header、Method、Query、After 等断言。filters 支持 AddRequestHeader、StripPrefix、Retry 等内置过滤器。使用 `lb://` 前缀时，Gateway 会集成服务发现组件（如 Nacos）进行负载均衡路由。动态路由可通过 Gateway 自带的 RouteDefinitionRepository 接口实现，也可结合 Nacos 配置监听实现热更新。

### Level 2: 进阶题

#### Nacos 进阶
7. **配置中心**
   - 问题：Nacos 配置中心的原理是什么？如何实现动态配置？
   - 答案：Nacos 配置中心通过长轮询（Long Polling）机制实现动态配置推送。客户端发起 HTTP 长连接请求，服务端hold住请求一段时间（默认30秒），期间若配置变更则立即返回；超时后客户端重新发起长轮询。客户端收到变更后从本地配置文件加载新配置。
   - 解析：长轮询相比 WebSocket 更简单，相比短轮询更实时。Nacos 服务端通过 `ConfigServletInner` 处理长轮询请求，利用 `DeferredResult` 挂起线程。当配置变更时，通过 `ConfigChangeClusterNotifier` 通知所有订阅者。客户端通过 `NacosConfigManager` 监听配置变更，结合 `@RefreshScope` 注解实现 Bean 的动态刷新。生产环境建议使用 configimport + 共享配置（shared-configs）管理多环境配置。

8. **集群部署**
   - 问题：Nacos 如何实现集群部署？有哪些注意事项？
   - 答案：Nacos 集群部署需配置 cluster.conf 文件指定所有节点地址，通过 Nginx 实现负载均衡。数据同步方面，AP 模式使用 Distro 协议（最终一致性），CP 模式使用 Raft 协议（强一致性）。生产环境至少部署 3 个节点，并使用 MySQL 共享存储保证数据持久化。
   - 解析：Distro 协议是 Nacos 自研的 AP 协议，每个节点负责一部分数据的写入，异步同步到其他节点。Raft 协议用于 CP 模式下的选举和日志复制。集群部署时，客户端通过随机选择一个节点获取服务列表，若该节点不可用则切换到其他节点。注意事项：1）使用内网 IP 避免公网暴露；2）配置 MySQL 集群作为后端存储；3）设置 JVM 参数优化 GC；4）通过 Nginx 或 SLB 做流量分发；5）监控节点状态和数据一致性。

#### Gateway 进阶
9. **过滤器**
   - 问题：Gateway 的过滤器有哪些类型？如何自定义过滤器？
   - 答案：Gateway 过滤器分为两类：1）GatewayFilter（单一路由过滤器），作用于特定路由；2）GlobalFilter（全局过滤器），作用于所有路由。生命周期分为 Pre（前置处理）和 Post（后置处理）。自定义过滤器需实现 GatewayFilter 或 GlobalFilter 接口，并注册为 Spring Bean。
   - 解析：内置 GatewayFilter 包括 AddRequestHeader、StripPrefix、Retry、RequestRateLimiter 等。自定义过滤器示例：实现 `GlobalFilter` 接口，重写 `filter` 方法，通过 `ExchangeUtils.getDownstreamResponse` 获取响应。通过 `@Order` 注解或实现 `Ordered` 接口控制过滤器执行顺序，值越小优先级越高。常见应用场景：统一鉴权（在 Pre 阶段校验 Token）、日志记录（Pre/Post 阶段记录请求响应信息）、请求参数校验、灰度路由等。

10. **限流熔断**
    - 问题：Gateway 如何实现限流和熔断？
    - 答案：Gateway 限流可使用 `RequestRateLimiter` 过滤器结合 Redis + Lua 脚本实现令牌桶算法，也可集成 Sentinel 实现更丰富的限流策略（QPS、线程数、热点参数等）。熔断可集成 Sentinel 或 Resilience4j，实现故障快速失败和自动恢复。
    - 解析：`RequestRateLimiter` 过滤器基于 Redis 的令牌桶算法，通过 `KeyResolver` 确定限流维度（如 IP、用户 ID、接口路径）。Sentinel 与 Gateway 集成后，可通过 Dashboard 动态配置限流规则，支持流控、熔断、系统自适应保护。Resilience4j 是替代 Hystrix 的新一代熔断器，支持 CircuitBreaker、RateLimiter、Retry、Bulkhead 等模式。生产环境推荐 Sentinel，因为其规则可动态推送，且提供完整的监控和管理界面。

#### OpenFeign 进阶
11. **远程调用**
    - 问题：OpenFeign 是如何实现远程调用的？
    - 答案：OpenFeign 通过动态代理机制实现声明式 HTTP 调用。启动时扫描所有 `@FeignClient` 接口，通过 JDK 动态代理生成代理对象。调用时，代理类解析方法注解（`@GetMapping` 等），构建 HTTP 请求，通过 Ribbon/LoadBalancer 选择目标服务实例，执行 HTTP 调用并反序列化响应。
    - 解析：OpenFeign 的核心流程：1）`FeignClientFactoryBean` 创建代理对象；2）`SynchronousMethodHandler` 处理方法调用；3）`Contract` 解析注解元数据（SpringMVCContract 支持 `@RequestMapping` 等注解）；4）`Encoder` 序列化请求体；5）`Client` 执行 HTTP 调用（默认 HttpURLConnection，可替换为 OkHttp、Apache HttpClient）；6）`Decoder` 反序列化响应。OpenFeign 还支持请求拦截器（`RequestInterceptor`）实现统一鉴权，以及 fallback 机制实现降级。

12. **负载均衡**
    - 问题：OpenFeign 如何集成负载均衡？
    - 答案：OpenFeign 集成 Spring Cloud LoadBalancer 实现客户端负载均衡。引入 `spring-cloud-starter-loadbalancer` 依赖，在 `@FeignClient` 注解中指定服务名，调用时通过 `LoadBalancerClient` 从服务实例列表中选择目标实例。默认轮询策略，可自定义负载均衡算法。
    - 解析：负载均衡流程：1）Feign 从服务名解析出 `serviceId`；2）通过 `LoadBalancerClient` 获取可用实例列表；3）根据负载均衡策略选择目标实例；4）用选择的实例地址替换 URL 中的服务名。LoadBalancer 支持 `RoundRobinLoadBalancer`（轮询）和 `RandomLoadBalancer`（随机），可通过实现 `ReactorServiceInstanceLoadBalancer` 接口自定义策略（如加权轮询、一致性哈希）。相比 Ribbon（已停止维护），LoadBalancer 更轻量，基于响应式编程模型。

#### Sentinel 进阶
13. **流量控制**
    - 问题：Sentinel 的流量控制原理是什么？
    - 答案：Sentinel 通过滑动窗口统计实现流量控制。内部维护统计窗口（默认 1 秒），实时统计 QPS 或并发线程数，当超过阈值时触发流控。流控模式包括：直接（针对当前资源）、关联（针对关联资源）、链路（针对入口流量）。流控效果包括：快速失败、Warm Up（预热）、排队等待。
    - 解析：Sentinel 的滑动窗口由 `LeapArray` 实现，将时间窗口分为多个小格子（默认 2 个），每个格子统计独立的请求数。`TrafficShapingController` 负责判断是否放行：`DefaultController` 实现直接拒绝，`WarmUpController` 实现预热（令牌桶算法，允许流量逐渐提升），`RateLimiterController` 实现匀速排队（漏桶算法）。Warm Up 适用于系统刚启动时避免瞬时流量冲击；排队等待适用于削峰填谷场景，保证请求匀速处理。

14. **熔断降级**
    - 问题：Sentinel 如何实现熔断降级？
    - 答案：Sentinel 提供三种熔断策略：1）慢调用比例，当慢调用比例超过阈值时触发熔断；2）异常比例，当异常比例超过阈值时触发熔断；3）异常数，当异常数超过阈值时触发熔断。熔断后进入 Half-Open 状态，经过配置的熔断时长后尝试放行一个请求，成功则恢复，失败则继续熔断。
    - 解析：Sentinel 的熔断器状态机：Closed（正常放行）→ Open（全部拒绝/降级）→ Half-Open（探测恢复）。熔断时长默认 5 秒，可通过 `statIntervalMs` 设置统计窗口。Sentinel 通过 `DegradeSlot` 实现熔断逻辑，统计窗口内的请求指标由 `SlidingWindowCounter` 维护。与 Hystrix 不同，Sentinel 支持慢调用比例和异常数两种更灵活的策略。生产环境建议配合 `@SentinelResource` 注解的 fallback 方法实现优雅降级，返回默认值或缓存数据。

### Level 3: 高级题

#### 分布式事务
15. **Seata 分布式事务**
    - 问题：Seata 是什么？它如何实现分布式事务？
    - 答案：Seata 是一款开源的分布式事务解决方案，支持 AT、TCC、Saga、XA 四种模式。核心架构包含三个角色：TC（事务协调者，Seata Server）、TM（事务管理器，发起全局事务）、RM（资源管理器，管理本地事务）。通过全局事务 ID（XID）串联分支事务，保证数据一致性。
    - 解析：AT 模式是 Seata 的默认模式，通过代理数据源自动管理回滚。原理：1）一阶段：拦截 SQL，记录 before/after image 到 undo_log 表，提交本地事务；2）二阶段提交：删除 undo_log；3）二阶段回滚：根据 undo_log 中的 before image 生成反向 SQL 执行回滚。AT 模式对业务无侵入，但需要全局锁机制，性能开销较大。TCC 模式需要业务自行实现 Try/Confirm/Cancel 三个接口，性能更好但开发成本高。Saga 模式适合长事务，通过正向操作和补偿操作交替执行。

16. **TCC 模式**
    - 问题：什么是 TCC 模式？它有哪些优缺点？
    - 答案：TCC（Try-Confirm-Cancel）是分布式事务的一种柔性事务模式。Try 阶段预留资源（冻结数据），Confirm 阶段确认提交（扣减冻结资源），Cancel 阶段回滚释放资源。优点：不依赖数据库锁，性能高，适用于对一致性要求高的场景。缺点：需要业务侵入实现三个接口，开发成本高，需处理空回滚、幂等、悬挂等问题。
    - 解析：TCC 的关键问题：1）空回滚：Try 未执行但收到了 Cancel，需要判断并跳过；2）幂等：Confirm/Cancel 可能重试，需要保证多次执行结果一致，通常用事务状态表记录已执行的分支；3）悬挂：Cancel 在 Try 之前执行，需要在 Cancel 时检查 Try 是否已执行。Seata 的 TCC 模式通过 `@TwoPhaseBusinessAction` 注解定义 Try 方法，Confirm/Cancel 方法通过命名约定自动关联。生产环境建议使用 Seata 的 TCC 框架，它内置了上述问题的解决方案。

#### 消息队列
17. **RocketMQ 集成**
    - 问题：如何在 Spring Cloud 中集成 RocketMQ？
    - 答案：Spring Cloud 集成 RocketMQ 有两种方式：1）使用 `rocketmq-spring-boot-starter`，通过 `RocketMQTemplate` 发送消息，`@RocketMQMessageListener` 接收消息；2）使用 Spring Cloud Stream + RocketMQ Binder，通过 `@StreamListener` 监听消息通道。生产者支持同步、异步、单向发送，消费者支持集群消费和广播消费。
    - 解析：`RocketMQTemplate` 封装了 RocketMQ 的原生 API，支持 `send`（同步）、`asyncSend`（异步）、`sendOneWay`（单向）等方法。消息类型包括 `Message`、`RocketMQMessage`、`StringMessage` 等。消费者通过 `@RocketMQMessageListener` 注解指定 `topic`、`consumerGroup`、`selectorExpression`（Tag 过滤）等。Spring Cloud Stream 方式通过 `@Input`/`@Output` 定义消息通道，解耦了消息中间件的实现。建议使用事务消息实现分布式事务的最终一致性，例如在本地事务提交后发送半消息，由 Broker 回调确认。

18. **消息可靠性**
    - 问题：如何保证消息的可靠性？有哪些解决方案？
    - 答案：消息可靠性从三个环节保障：1）生产者：同步发送 + 重试机制 + 事务消息；2）Broker：同步刷盘 + 主从同步（同步复制）；3）消费者：手动 ACK + 幂等消费。RocketMQ 通过 FlushDiskType=SYNC_FLUSH 保证同步刷盘，brokerRole=SYNC_MASTER 保证主从同步。
   - 解析：生产端可靠性：使用 `send` 同步发送，失败重试 2 次（默认），可通过 `retryTimesWhenSendFailed` 调整。事务消息通过 Half Message + 本地事务 + 回查机制保证原子性。Broker 端：异步刷盘（ASYNC_FLUSH）性能高但宕机可能丢数据，同步刷盘（SYNC_FLUSH）保证持久化但性能下降。消费端：默认自动 ACK 可能丢消息，应改为手动 ACK（`ConsumeConcurrentlyStatus.CONSUME_SUCCESS`），并实现幂等（如数据库唯一键、Redis SETNX）。RocketMQ 还支持死信队列（DLQ）处理消费失败的消息。

#### 链路追踪
19. **Sleuth 与 Zipkin**
    - 问题：Sleuth 和 Zipkin 的作用是什么？如何实现链路追踪？
    - 答案：Sleuth 是 Spring Cloud 的链路追踪组件，为每个请求生成 TraceId 和 SpanId，通过 MDC 注入日志。Zipkin 是分布式追踪系统，负责收集、存储和展示链路数据。集成方式：引入 `spring-cloud-starter-sleuth` 和 `zipkin` 依赖，配置 Zipkin Server 地址即可自动上报。
    - 解析：Sleuth 的核心概念：TraceId（全局唯一，标识一个完整的请求链路）、SpanId（标识一次服务调用）、ParentSpanId（父级调用）。Sleuth 通过拦截器自动在 HTTP Header 中传递这些 ID，实现跨服务的链路关联。Zipkin 由 Collector（收集器）、Storage（存储，支持 MySQL/ES/Cassandra）、API（查询接口）、UI（Web 界面）组成。数据上报支持 HTTP（同步，可能影响性能）和 Kafka（异步，推荐生产使用）。注意：Spring Cloud Sleuth 从 3.x 开始已被 Micrometer Tracing 替代。

20. **SkyWalking**
    - 问题：SkyWalking 是什么？它有哪些优势？
    - 答案：SkyWalking 是 Apache 开源的分布式系统应用性能监控（APM）工具，支持 Java、.NET、Node.js 等多语言。核心优势：1）无侵入探针（Agent），无需修改代码；2）支持 JVM 指标监控、链路追踪、日志关联；3）支持拓扑图、热力图、告警等可视化功能。
    - 解析：SkyWalking 通过 Java Agent 字节码增强技术（基于 `java.lang.instrument` 和 ByteBuddy），在类加载时注入探针代码，拦截 HTTP、RPC、DB 等调用。架构包含 OAP（Observability Analysis Platform）收集分析数据、UI 展示界面、Agent 探针。相比 Zipkin，SkyWalking 功能更全面，集链路追踪、指标监控、日志分析于一体；支持服务拓扑图，直观展示服务依赖关系；支持多种存储后端（ES、H2、MySQL）；告警规则灵活配置。适用于需要全面监控的微服务集群。

### Level 4: 专家题

#### 微服务架构设计
21. **服务拆分**
    - 问题：微服务应该如何拆分？有哪些原则和最佳实践？
    - 答案：微服务拆分遵循以下原则：1）单一职责原则，每个服务只负责一个业务能力；2）高内聚低耦合，相关功能聚合，服务间松耦合；3）按业务领域拆分（DDD 限界上下文）；4）数据独立，每个服务拥有独立数据库；5）渐进式拆分，从单体逐步拆分。
    - 解析：拆分策略：1）按业务能力拆分（如用户服务、订单服务、支付服务）；2）按子域拆分（核心域、支撑域、通用域）；3）按数据拆分（避免跨服务 JOIN）。最佳实践：避免微服务过小（纳米服务），粒度以团队可维护为宜；服务间通过 API 或事件通信，避免直接数据库访问；使用 API Gateway 统一入口；引入服务网格（如 Istio）管理服务间通信；保持服务的自治性，允许不同服务使用不同技术栈。拆分后需要完善的 DevOps 流程和监控体系支撑。

22. **服务治理**
    - 问题：如何实现微服务的服务治理？包括限流、熔断、降级等。
    - 答案：微服务治理通过以下机制实现：1）限流：Sentinel/QPS 限制，防止流量过载；2）熔断：Sentinel/Resilience4j，故障快速失败；3）降级：返回默认值或缓存数据；4）服务发现：Nacos/Consul，动态管理服务实例；5）负载均衡：LoadBalancer，合理分配流量；6）链路追踪：Sleuth/SkyWalking，监控调用链路。
    - 解析：服务治理的核心目标是保障系统稳定性。限流策略：QPS 限流（令牌桶/漏桶）、并发线程数限流、热点参数限流。熔断策略：慢调用比例熔断、异常比例熔断、异常数熔断，配合半开状态自动恢复。降级策略：返回默认值、读取缓存、简化逻辑（如关闭非核心功能）。服务治理还需配合：统一日志（ELK）、分布式追踪（SkyWalking）、健康检查（Nacos）、配置中心（Nacos Config）。生产环境建议使用 Sentinel + Nacos 实现动态规则推送，结合 Dashboard 实时监控。

#### 高可用设计
23. **容错机制**
    - 问题：微服务架构中有哪些容错机制？如何设计高可用的系统？
    - 答案：微服务容错机制包括：1）超时控制，设置合理的超时时间避免长时间等待；2）重试机制，网络抖动时自动重试；3）熔断器，故障快速失败，防止雪崩效应；4）限流保护，防止过载；5）降级兜底，返回友好提示或缓存数据；6）隔离策略，线程池/信号量隔离故障服务。
    - 解析：高可用设计要点：1）多实例部署，消除单点故障，至少 2 个实例；2）多机房/多区域部署，实现异地容灾；3）服务冗余，无状态设计便于水平扩容；4）数据冗余，主从复制 + 定期备份；5）故障转移，自动检测并切换故障节点；6）限流降级，保护核心服务可用；7）混沌工程，主动注入故障验证系统弹性。经典的容错模式：Fail Fast（快速失败）、Fail Safe（安全失败）、Fail Back（备份失败）、Fail Over（故障转移）。Netflix Hystrix 的线程池隔离是经典实现，每个依赖服务使用独立线程池，避免一个服务故障拖垮整个系统。

24. **灰度发布**
    - 问题：如何实现微服务的灰度发布？
    - 答案：灰度发布（金丝雀发布）通过将新版本服务逐步推送给部分用户，验证稳定后再全量发布。实现方式：1）基于 Nacos 元数据标记版本，Gateway/OpenFeign 根据 Header 或 Cookie 路由到指定版本；2）使用 Istio/Envoy 的流量权重配置；3）自定义 LoadBalancer 策略，按比例分配流量。
    - 解析：灰度发布的关键环节：1）流量染色，通过 HTTP Header（如 `X-Version: v2`）标记请求来源；2）路由策略，Gateway 根据染色标记路由到新版本服务；3）数据兼容，新旧版本数据库 schema 需要向后兼容；4）监控告警，实时对比新旧版本的错误率、延迟等指标；5）回滚机制，发现问题时快速切回旧版本。Nacos 实现方案：给新版本实例打上 `version=v2` 元数据标签，LoadBalancer 通过 `NacosLoadBalancer` 的元数据路由策略，将带 `X-Version: v2` Header 的请求路由到新版本实例。生产环境建议使用 Sentinel 的流量染色规则，动态调整新版本的流量比例（如 1% → 10% → 50% → 100%）。

## 📖 学习资源

### 书籍推荐
- 《Spring Cloud 微服务实战》 - 翟永超
- 《微服务架构设计模式》 - Chris Richardson
- 《凤凰架构》 - 周志明

### 在线资源
- [Spring Cloud 官方文档](https://spring.io/projects/spring-cloud)
- [Spring Cloud Alibaba 官方文档](https://sca.aliyun.com/)
- [JavaGuide Spring Cloud 部分](https://javaguide.cn/system-design/micro-services/spring-cloud.html)

## 🔗 相关链接

- [Nacos 专题](nacos/)
- [Gateway 专题](gateway/)
- [OpenFeign 专题](openfeign/)
- [Sentinel 专题](sentinel/)
- [Seata 专题](seata/)
- [RocketMQ 专题](rocketmq/)
