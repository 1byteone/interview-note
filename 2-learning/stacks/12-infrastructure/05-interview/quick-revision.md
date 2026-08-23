# 速记版

## Nginx 5 个考点

### 1. 反向代理
Nginx 接收客户端请求，转发到后端服务器，客户端感知不到后端的存在。核心配置 `proxy_pass`，支持 HTTP/HTTPS、gRPC、WebSocket 等协议代理。反向代理隐藏了后端架构，同时可实现统一入口、SSL 卸载、请求过滤等功能。

### 2. 负载均衡策略
- **轮询（默认）**：请求依次分发到每台服务器，适用于服务器配置相近的场景
- **加权轮询**：通过 `weight` 参数分配请求比例，权重越高分配越多
- **IP Hash**：对客户端 IP 做 hash，同一 IP 固定到同一台服务器，解决 Session 问题
- **Least Connections**：优先分配给当前活跃连接数最少的服务器
- **Fair（第三方）**：根据响应时间分配，响应快的服务器优先

### 3. Location 匹配规则
- `=` 精确匹配：优先级最高，如 `location = /login`
- `^~` 前缀匹配：匹配到后不再检查正则，如 `location ^~ /static/`
- `~` 正则匹配（区分大小写）：如 `location ~ \.(gif|jpg)$`
- `~*` 正则匹配（不区分大小写）：如 `location ~* \.(png|css)$`
- 普通前缀匹配：按最长匹配优先

### 4. 动静分离
将静态资源（HTML、CSS、JS、图片）交给 Nginx 直接处理，动态请求代理到后端服务。配置示例：`location ~* \.(jpg|png|css|js)$ { root /data/static; expires 30d; }`。动静分离减少了后端压力，利用 Nginx 高效处理静态文件的能力。

### 5. 限流
Nginx 使用漏桶算法实现限流，通过 `limit_req_zone` 和 `limit_req` 指令配置。关键参数：`rate` 限制速率（如 `1r/s`），`burst` 允许突发流量，`nodelay` 不延迟处理。同时支持 `limit_conn` 限制并发连接数。

---

## Gateway 5 个考点

### 1. 路由（Route）
路由是 Spring Cloud Gateway 的核心概念，包含三个要素：ID（路由唯一标识）、Predicate（断言匹配条件）、Filter（过滤器）。路由配置支持声明式（YAML）和编程式（Java DSL），路由表存储在内存中，支持动态刷新。

### 2. 过滤器链（Filter Chain）
Gateway 的过滤器分为 `GlobalFilter`（全局生效）和 `GatewayFilter`（路由级别）。每个过滤器有 `order` 属性控制顺序，值越小优先级越高。过滤器链执行顺序：前置过滤器（pre）→ 目标服务 → 后置过滤器（post）。常见过滤器：AddRequestHeader、StripPrefix、Retry、CircuitBreaker。

### 3. 断言（Predicate）
断言决定请求是否匹配路由规则。内置断言工厂：`Path`（路径匹配）、`Method`（HTTP 方法）、`Header`（请求头）、`Query`（查询参数）、`Cookie`（Cookie 匹配）、`Host`（域名匹配）、`After/Before/Between`（时间匹配）。多个断言默认 AND 关系，可通过 `and`、`or`、`negate` 组合。

### 4. 跨域（CORS）
Gateway 统一配置跨域，避免在每个微服务中重复配置。通过 `spring.cloud.gateway.globalcors` 配置允许的来源、请求头、方法等。关键参数：`allowedOrigins`、`allowedMethods`、`allowedHeaders`、`maxAge`。生产环境应限制 `allowedOrigins` 而非使用 `*`。

### 5. Sentinel 集成
Gateway 与 Sentinel 集成实现网关层限流。通过 `spring.cloud.sentinel.scg.fallback` 配置限流后的降级页面。支持 API 分组限流，可为不同服务配置不同限流规则。规则持久化到 Nacos，控制台动态推送。

---

## Nacos 5 个考点

### 1. 服务注册与发现
服务启动时向 Nacos Server 注册自身信息（IP、端口、服务名）。消费者通过服务名获取实例列表，实现客户端负载均衡。支持健康检查、权重路由、保护阈值等功能。Nacos 通过 Distro 协议保证 AP，通过持久化服务保证 CP。

### 2. 配置中心
Nacos 作为配置中心，支持配置的动态管理和热更新。核心概念：`DataID`（配置标识）、`Group`（配置分组）、`Namespace`（命名空间隔离）。通过 `@RefreshScope` 注解实现 Bean 的热刷新，客户端通过长轮询感知配置变更。

### 3. 长轮询（Long Polling）
Nacos 客户端通过长轮询机制监听配置变化。客户端发起请求，服务端如果有配置变更立即返回，否则保持连接（默认 30 秒超时）。超时后客户端再次发起请求，形成"准实时"推送。相比 WebSocket 推送，长轮询实现简单且兼容性更好。

### 4. CAP 理论权衡
Nacos 默认是 AP 模式（可用性 + 分区容错性），通过 Distro 协议实现最终一致性。支持切换到 CP 模式（一致性 + 分区容错性），使用 Raft 协议保证强一致性。AP 模式适用于服务发现场景（允许短暂的不一致），CP 模式适用于配置中心场景（配置必须一致）。

### 5. 命名空间（Namespace）
Namespace 用于环境隔离和租户隔离。不同命名空间下的服务、配置相互隔离，不可见。典型用法：`dev`、`test`、`prod` 各建一个命名空间，或按业务线划分。切换命名空间只需修改 `spring.cloud.nacos.config.namespace` 配置。

---

## Sentinel 5 个考点

### 1. 限流算法
- **固定窗口**：单位时间窗口内计数，超过阈值则拒绝，有临界突发问题
- **滑动窗口**：将时间窗口划分为多个小格子，滑窗统计，解决临界突发
- **漏桶算法**：固定速率出水，请求以任意速率进入，超过桶容量则丢弃，适合流量整形
- **令牌桶算法**：固定速率生成令牌放入桶，请求必须获取令牌才能通过，允许一定突发

### 2. 熔断状态机
Sentinel 熔断器有三种状态：**CLOSED**（关闭，正常放行请求）→ **OPEN**（开启，请求直接降级）→ **HALF_OPEN**（半开，尝试放行探测请求）。熔断策略：慢调用比例（RT > 阈值）、异常比例、异常数。熔断后经过 `maxAllowedRequests` 个探测请求，根据结果决定恢复或继续熔断。

### 3. 热点限流
针对热点参数（如商品 ID、用户 ID）进行精细化限流。通过 `@SentinelResource` 注解的 `fallback` 指定降级方法。支持参数例外项，对特定参数值设置不同的限流阈值。热点限流精度高于普通 API 限流，适用于秒杀、爆款商品等场景。

### 4. 规则持久化
Sentinel 原生规则存储在内存中，重启后丢失。生产环境需持久化规则，常用方式：推模式（Nacos 配置中心 → Sentinel 控制台 → 客户端）和拉模式（客户端主动拉取）。推荐推模式，规则变更实时生效且持久化存储。

### 5. 系统保护
系统自适应限流，根据系统负载（Load、CPU、RT、入口 QPS、并发线程数）动态调整流量。核心思路：当系统负载超过阈值时，降低入口流量，保证系统不被压垮。系统保护规则是全局的，适用于整体流量控制。

---

## Seata 5 个考点

### 1. AT 模式
自动补偿型分布式事务方案，Seata 自动生成回滚 SQL。原理：一阶段（业务 SQL + 前后镜像记录到 `undo_log`），二阶段（提交时删除 `undo_log`，回滚时根据镜像生成反向 SQL）。对业务代码无侵入，只需 `@GlobalTransactional` 注解。

### 2. TCC 模式
手动补偿型分布式事务方案，需要业务方实现 Try、Confirm、Cancel 三个接口。Try 阶段预留资源，Confirm 阶段确认执行，Cancel 阶段释放资源。适用于金融转账、库存扣减等需要精确控制补偿的场景。

### 3. Saga 模式
长事务解决方案，每个本地事务完成后发布事件，触发下一个事务。Saga 由一系列本地事务组成，每个事务有对应的补偿事务。适用于业务流程长、允许最终一致性的场景。Saga 不要求全局锁，性能优于 AT 和 TCC。

### 4. 全局事务
全局事务 ID（XID）贯穿整个分布式调用链路。Seata 通过 `RootContext` 在 ThreadLocal 中传递 XID，跨服务通过 OpenFeign 拦截器传播。`@GlobalTransactional` 注解开启全局事务，超时时间默认 60 秒，超时后自动回滚。

### 5. undo_log 表
在 AT 模式下，每个 RM 数据库中需要创建 `undo_log` 表。表中记录 `branch_id`、`xid`、`rollback_info`（前后镜像 JSON）、`log_status`、`log_created` 等字段。二阶段回滚时，Seata 根据 `undo_log` 中的前后镜像校验数据一致性，生成反向 SQL 恢复数据。

---

## CI/CD 5 个考点

### 1. 流水线（Pipeline）
CI/CD 流水线由多个阶段（Stage）组成：代码检查 → 单元测试 → 构建打包 → 镜像构建 → 部署到测试环境 → 集成测试 → 部署到预发布环境 → 生产发布。每个阶段包含多个步骤（Step），失败时终止流水线。流水线定义即代码（Pipeline as Code），存储在 Git 仓库中（如 Jenkinsfile、GitHub Actions YAML）。

### 2. 蓝绿发布
维护两套生产环境（蓝环境和绿环境），一套提供服务，另一套部署新版本。切换方式：通过负载均衡器将流量从旧环境切换到新环境。优势：切换迅速，回滚只需切回旧环境，零停机时间。劣势：需要双倍资源成本。

### 3. 金丝雀发布（灰度发布）
新版本先部署到少量服务器，路由少量用户流量到新版本，验证无问题后逐步扩大流量比例，最终全量上线。流量比例可按百分比、用户 ID、地域等维度控制。相比蓝绿发布，金丝雀发布更安全，问题影响面小。

### 4. 回滚
部署失败或发现 Bug 时恢复到上一个稳定版本。回滚策略：滚动回滚（逐步替换为新版本）、全量回滚（一次性切回旧版本）。GitOps 模式下，回滚即 `git revert` 提交，工具自动同步回旧版本。

### 5. GitOps
以 Git 仓库作为基础设施和应用的"唯一事实来源"，通过 Pull Request 管理变更，工具自动将 Git 状态同步到集群环境。核心工具：ArgoCD、Flux。优势：变更可审计、回滚即 `git revert`、环境状态与 Git 仓库完全一致。