# 基础设施 — 面试抽认卡

> 来源：`learn/12-infrastructure/05-interview/`

---

### Card 1: Nginx 负载均衡策略
**维度**: 📝速记 | **难度**: ⭐⭐

> **Q: Nginx 的负载均衡策略有哪些？upstream 配置如何实现？**

**A:** 轮询（默认，依次分发）、加权轮询（`weight` 参数控制比例）、IP Hash（`ip_hash`，同一 IP 固定到同一台，解决 Session 问题）、Least Connections（`least_conn`，分给活跃连接最少的）。配置：`upstream backend { server 10.0.0.1:8080 weight=3; server 10.0.0.2:8080; server 10.0.0.3:8080 down; }`。健康检查：`max_fails=3 fail_timeout=30s` 自动摘除故障节点。`backup` 标记备用节点，主节点全挂时才启用。

---

### Card 2: Nacos 一致性协议
**维度**: 🔬深挖 | **难度**: ⭐⭐⭐

> **Q: Nacos 如何实现 AP 和 CP 模式切换？两种模式下各使用什么协议？**

**A:** Nacos 默认 AP 模式（Distro 协议，最终一致性），适合服务发现（允许多少秒不一致）。支持切换到 CP 模式（Raft 协议，强一致性），适合配置中心（配置必须一致）。切换：`curl -X PUT 'http://localhost:8848/nacos/v1/ns/operator/switches?entry=serverMode&value=CP'`。Distro 协议：每个节点只负责一部分数据，写请求转发到目标节点，节点间异步复制。Raft 协议：Leader 选举，日志复制，多数派确认。AP 下 Nacos 每秒处理百万级心跳，CP 下性能略低但配置一致。

---

### Card 3: Gateway 过滤器链
**维度**: 🔬深挖 | **难度**: ⭐⭐

> **Q: Spring Cloud Gateway 的过滤器链执行顺序是如何确定的？GlobalFilter 和 GatewayFilter 有什么区别？**

**A:** 过滤器分为 GlobalFilter（全局生效，所有路由）和 GatewayFilter（路由级别，`filters` 配置）。执行顺序由 `order` 值决定：值越小优先级越高，先执行 pre 逻辑，然后反向执行 post 逻辑。`@Order` 注解或实现 `Ordered` 接口设置顺序。常见过滤器链：AddRequestHeader（加请求头）→ StripPrefix（去路径前缀）→ RateLimiter（限流）→ 目标服务（响应）→ 后置过滤器。自定义过滤器：实现 `GatewayFilter` 和 `Ordered`，在 `filter` 方法中写 `chain.filter(exchange).then(Mono.fromRunnable(() -> {}))`。

---

### Card 4: Sentinel 滑动窗口算法
**维度**: 🔬深挖 | **难度**: ⭐⭐⭐

> **Q: Sentinel 的滑动窗口限流算法是如何实现的？**

**A:** Sentinel 将时间划分为固定长度的时间窗口（如 1 秒），每个窗口内统计请求数。每个窗口有开始时间和统计计数器。统计当前时间所在的窗口，累加请求数，如果超过阈值则限流。窗口滑动：当前时间超过窗口结束时间时，创建新窗口，丢弃最旧的窗口。精度取决于窗口数量（如 1 秒分 2 个 500ms 窗口）。滑动窗口解决了固定窗口的边界突刺问题（如每分钟 100 次，第一秒 100 次+最后 999 秒 0 次，实际 1 秒内 100 次已超限）。Sentinel 支持 QPS/线程数/系统负载等多种限流维度。

---

### Card 5: Seata AT 原理
**维度**: 🔬深挖 | **难度**: ⭐⭐⭐

> **Q: Seata AT 模式如何实现分布式事务？相比 TCC 有什么优势？**

**A:** AT 模式（Automatic Transaction）：① TM 向 TC 开启全局事务，获取 XID；② RM 注册分支事务，执行本地 SQL（INSERT/UPDATE/DELETE），Seata 代理自动解析 SQL，生成前后镜像（Before Image/After Image）并记录到 undo_log 表；③ 所有 RM 执行成功，TM 通知 TC 提交全局事务；④ 任一 RM 失败，TC 通知所有 RM 回滚，RM 根据 undo_log 逆向补偿。对比 TCC：AT 无侵入（自动解析 SQL），无需业务实现 Try/Confirm/Cancel 三阶段。AT 适合 SQL 操作场景，TCC 适合非 SQL 资源（如 Redis、外部 API）。

---

### Card 6: OpenFeign 原理
**维度**: 🔬深挖 | **难度**: ⭐⭐

> **Q: OpenFeign 是如何实现声明式 HTTP 调用的？**

**A:** `@EnableFeignClients` 开启，`@FeignClient` 注解接口。Spring 在启动时扫描 `@FeignClient` 接口，通过 JDK 动态代理创建实现类。方法调用时，代理根据 `@RequestMapping`、`@PathVariable` 等注解构建 HTTP 请求 URL 和参数，通过 `Client` 执行（默认使用 HttpURLConnection，可替换为 OkHttpClient 或 Apache HttpClient）。集成 Ribbon 负载均衡（`@LoadBalanced`）：`openfeign.spring.cloud.codec` 配置。集成 Sentinel：`feign.sentinel.enabled=true` 开启熔断降级。

---

### Card 7: Prometheus 指标
**维度**: 📝速记 | **难度**: ⭐⭐

> **Q: Prometheus 的四种指标类型分别是什么？适用于什么场景？**

**A:** Counter（只增不减，如请求总数、错误总数，用于计数）；Gauge（可增可减，如当前内存使用、CPU 使用率，用于快照值）；Histogram（直方图，如请求延迟分布 `{le="0.1"}`，可计算 P50/P99）；Summary（类似 Histogram，但客户端计算分位数，更适合聚合前计算）。`rate(http_requests_total[5m])` 计算每秒请求速率。`histogram_quantile(0.99, rate(http_request_duration_seconds_bucket[5m]))` 计算 P99 延迟。`up{job="myapp"}` 检查服务是否存活。

---

### Card 8: Grafana 面板
**维度**: 📝速记 | **难度**: ⭐⭐

> **Q: Grafana 面板的核心组件是什么？如何设计有效的监控面板？**

**A:** 核心组件：Dashboard（面板集合）、Panel（可视化组件，支持 Graph/Table/Stat/Bar Gauge/Heatmap）、Query（数据源查询，PromQL/SQL）、Alert（告警规则）。有效面板设计原则：① 按维度分层（基础设施层/JVM 层/业务层）；② 黄金指标（延迟/流量/错误率/饱和度）；③ 每个 Panel 有明确信息量（避免过多无用指标）；④ 设置告警阈值（P99 延迟 > 500ms 告警）；⑤ 关联日志（Grafana + Loki 日志关联 metrics）。`$__rate_interval` 自动选择合适的时间范围聚合。

---

### Card 9: ELK 架构
**维度**: 📝速记 | **难度**: ⭐⭐

> **Q: ELK 日志系统的完整架构是怎样的？如何实现日志告警？**

**A:** 完整架构：Filebeat（轻量级采集器，读取日志文件）→ Logstash（数据转换/过滤，grok 解析）→ Elasticsearch（存储和索引）→ Kibana（可视化，查询日志）。Elasticsearch 集群：索引按天/月分（`logs-2024.01.01`），ILM 管理生命周期。告警：Elastic 8.x 内置 Alerting（Kibana 告警规则，触发条件满足时发送通知）。替代方案：Loki + Grafana（轻量级，不建立全文索引，适合 K8s 环境）。优化：Filebeat 采集时标记字段，减少 Logstash 处理压力。

---

### Card 10: SkyWalking 链路追踪
**维度**: 🔬深挖 | **难度**: ⭐⭐⭐

> **Q: SkyWalking 的链路追踪原理是什么？如何实现分布式追踪？**

**A:** SkyWalking 通过 Java Agent（字节码注入）自动拦截 RPC 调用（HTTP/gRPC/DB/MQ），生成 Trace 和 Span。每个 Span 记录：TraceId（全局唯一链路 ID）、ParentSpanId（父 Span ID）、StartTime/EndTime（耗时）、Tags（服务名/方法/参数/异常）。Span 类型：EntrySpan（接收请求）、ExitSpan（发出请求）、LocalSpan（内部逻辑）。上报：Agent 通过 gRPC 上报到 OAP Server（Observability Analysis Platform），OAP 存储到 ES，UI 展示。`@Trace` 注解手动埋点。

---

### Card 11: 蓝绿发布
**维度**: 📝速记 | **难度**: ⭐⭐

> **Q: 蓝绿部署和灰度发布的区别是什么？如何实现？**

**A:** 蓝绿部署：维护两套完整环境（蓝=当前生产，绿=新版本），切换时瞬间将流量全部转到绿环境，回滚时切回蓝环境。优点：切换快，回滚快。缺点：成本高（两倍资源）。灰度发布（金丝雀发布）：新版本先让一小部分用户（如 5%）使用，验证无误后逐步扩大比例到 100%，异常时只影响小范围。K8s 实现灰度：`nginx.ingress.kubernetes.io/canary: "true"` + `canary-weight: 5`。蓝绿适合"要全量或全不"，灰度适合"逐步验证"。

---

### Card 12: 金丝雀发布
**维度**: 🎯场景 | **难度**: ⭐⭐⭐

> **Q: 如何设计一个金丝雀发布系统？需要考虑哪些因素？**

**A:** ① 流量路由：Ingress/Gateway 按权重或 Header 路由（`canary: true` Header 走灰度版本）；② 指标监控：灰度版本与原版本对比 P99 延迟、错误率、业务指标（如下单成功率）；③ 自动回滚：错误率超过阈值（如 > 1%）自动切回旧版本；④ 用户维度：按用户 ID 哈希、地域、用户等级分流；⑤ 数据兼容：灰度版本的数据格式兼容旧版本（或新旧版本同时读写）。K8s 实现：两个 Deployment（stable + canary），Service 通过 label selector 共同选择，Ingress 控制权重。

---

### Card 13: CI/CD 流水线
**维度**: 🎯场景 | **难度**: ⭐⭐⭐

> **Q: 设计一个完整的 CI/CD 流水线，包含哪些阶段？**

**A:** CI（持续集成）：① 代码提交触发构建；② 代码检查（SonarQube 静态扫描、Checkstyle）；③ 单元测试（JUnit + Mockito）；④ 构建打包（Maven/Gradle）；⑤ 制品推送（Artifactory/Harbor 镜像仓库）。CD（持续部署）：① 部署到测试环境（Testcontainers 集成测试）；② 自动验收测试（Selenium/Cypress）；③ 审批门（人工或自动）；④ 灰度发布（金丝雀 5% → 25% → 100%）；⑤ 监控验证（告警无异常则继续）。GitHub Actions 示例：`.github/workflows/ci.yml` 包含 `build → test → docker-build → deploy` 阶段。

---

### Card 14: 配置中心
**维度**: 📝速记 | **难度**: ⭐⭐

> **Q: 配置中心的核心功能是什么？Nacos 和 Apollo 的区别？**

**A:** 核心功能：配置集中管理、动态刷新（不重启应用）、环境隔离（dev/test/prod）、变更历史/回滚、权限控制、灰度配置。Nacos：轻量级，服务发现 + 配置中心一体化，长轮询通知，`@RefreshScope` 热刷新。Apollo：专门配置中心，功能更完善（配置界面、灰度发布、权限管理、多环境），支持配置监听（`@ApolloConfigChangeListener`）。选型：已有 Nacos 选 Nacos 配置中心；需要强大配置管理功能（如灰度配置、审批流）选 Apollo。

---

### Card 15: 服务注册发现
**维度**: 📝速记 | **难度**: ⭐⭐

> **Q: 服务注册发现的核心流程是什么？Eureka 和 Nacos 的区别？**

**A:** 流程：① 服务启动时向注册中心注册自身信息（IP、端口、服务名、元数据）；② 注册中心维护服务实例列表，定期心跳检测；③ 消费者从注册中心获取服务实例列表（客户端负载均衡）；④ 消费者选择实例发起调用。Eureka：AP 模式（注册不成功不影响服务运行），2 分钟踢出不发送心跳的实例，自我保护机制（网络分区时不踢实例）。Nacos：支持 AP/CP 切换，健康检查更丰富（TCP/HTTP/MySQL），支持权重路由和保护阈值。

---

### Card 16: 限流算法对比
**维度**: 🔬深挖 | **难度**: ⭐⭐⭐

> **Q: 四种常见限流算法（固定窗口/滑动窗口/令牌桶/漏桶）的对比和适用场景？**

**A:** 固定窗口：按时间窗口计数，实现简单但有边界突刺（窗口切换时可能瞬间打满）。滑动窗口：将窗口细分，精度高但需要更多存储，适合精确统计。令牌桶：以固定速率生成令牌，桶满则丢弃，允许突发流量，适合接口限流。漏桶：以固定速率流出，超量丢弃，平滑流量零突发，适合流量整形。选型：API 网关用令牌桶（允许突发），数据库保护用漏桶（平滑写入），精确统计用滑动窗口，简单场景用固定窗口。

---

### Card 17: 分布式事务选型
**维度**: 🎯场景 | **难度**: ⭐⭐⭐

> **Q: 分布式事务的几种方案分别适用于什么场景？**

**A:** ① 事务消息（RocketMQ）：高一致性，适合最终一致场景（订单创建→扣库存），Broker 协调回查。② TCC（Try-Confirm-Cancel）：强一致性，业务侵入大，适合短事务（扣余额→冻结资金）。③ Seata AT：无侵入自动补偿，适合 SQL 操作场景（自动生成回滚 SQL）。④ Saga（编排/编排）：长事务拆解子事务+补偿，适合跨服务跨天的长流程。⑤ 本地消息表：简单可靠，适合中小系统。选型原则：能不分布式就不分布式，能最终一致就别强一致，能用事务消息就别上 TCC。

---

### Card 18: 网关鉴权
**维度**: 🎯场景 | **难度**: ⭐⭐⭐

> **Q: 在网关层实现鉴权的方案有哪些？JWT 和 Session 的区别？**

**A:** 网关统一鉴权：所有请求经过网关校验 Token，校验通过后转发到下游服务，下游服务无需重复鉴权。JWT：无状态，Token 包含用户信息和过期时间，网关校验签名即可，适合分布式。Session：有状态，Session 存储在 Redis，网关校验 Session ID，适合单体/传统架构。JWT 缺点：无法主动失效（签发后到过期前一直有效），Token 体积大。白名单：登录/注册/健康检查等接口跳过鉴权。Spring Cloud Gateway 实现：`GlobalFilter` 解析 JWT → 校验签名 → 提取用户信息放入 Header → 转发。