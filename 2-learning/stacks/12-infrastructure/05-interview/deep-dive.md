# 深挖题

## 网关过滤器链执行顺序和优先级

Spring Cloud Gateway 的过滤器链由 `DefaultFilters`（全局默认过滤器）、`RouteFilters`（路由级别过滤器）和 `GlobalFilters`（全局过滤器）三部分组成，它们按特定规则合并排序后执行。

**过滤器类型优先级：**
- `GlobalFilter` 实现类通过 `@Order` 或 `Ordered` 接口指定优先级
- `GatewayFilter` 工厂在构建时设置 `order`，值越小越靠前
- `DefaultFilters` 默认优先级最高（`order = 0`），其次是指定路由上的过滤器

**执行顺序规则：**
1. 所有符合条件的过滤器合并为一个链表
2. 按 `order` 值升序排序，`order` 相同则按过滤器类型排序
3. 每个过滤器执行 `filter(exchange, chain)` 方法，调用 `chain.filter()` 传递给下一个
4. 前置逻辑在 `chain.filter()` 之前执行，后置逻辑在 `chain.filter()` 的 `Mono` 回调中执行

**典型过滤器优先级（按内置默认值）：**
- `AdaptCachedBodyGlobalFilter`： -2000（最早，缓存请求体）
- `ForwardPathFilter`： 0
- `RouteToRequestUrlFilter`： 10000
- `NettyRoutingFilter`： 2147483647（最晚，发起路由请求）
- `WebSocketRoutingFilter`： 2147483646

**自定义过滤器排序：**
```java
@Component
@Order(-1)  // 值越小越靠前
public class CustomAuthFilter implements GlobalFilter {
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 鉴权逻辑
        return chain.filter(exchange);
    }
}
```

**面试重点：** 过滤器链是"双向"的 -- pre 阶段按 order 升序，post 阶段按 order 降序。理解 `chain.filter()` 的响应式编程模型（Mono 回调）是关键。

---

## Nacos 一致性协议（Distro + Raft 双协议）

Nacos 内部使用双协议架构，根据业务场景选择不同的一致性协议：

**Distro 协议（AP 模式，默认）：**
- 用于服务注册与发现，保证可用性，允许最终一致性
- 每个节点只负责一部分服务数据的写操作（分区）
- 写请求到达非负责节点时，转发到负责节点，或异步同步
- 节点间通过异步复制同步数据，不保证强一致
- 心跳检测：每 5 秒一次，15 秒超时标记不健康，30 秒剔除

**Distro 写入流程：**
1. 客户端发起注册请求，Nacos Server 通过 `DistroMapper` 找到负责该服务的节点
2. 如果当前节点就是负责节点，直接写入内存 + 磁盘
3. 如果不是负责节点，异步将请求转发给负责节点
4. 负责节点写成功后，返回成功，再异步同步到其他节点

**Distro 读取流程：**
1. 客户端查询服务列表，请求到达任意节点
2. 节点先查本地缓存，如果缓存存在直接返回
3. 如果缓存不存在或过期，向负责节点请求最新数据
4. 返回结果给客户端，同时缓存到本地

**Raft 协议（CP 模式）：**
- 用于配置中心，保证强一致性
- Nacos 2.0+ 引入 JRaft（基于 Raft 的 Java 实现）替代了早期的 Raft
- 集群选举 Leader，所有写操作由 Leader 处理
- 配置变更通过 Raft 日志复制到多数节点后才算提交成功
- 保证配置数据的强一致性，但牺牲了部分可用性

**Raft 写入流程：**
1. 客户端发起配置变更请求，Leader 写入日志
2. Leader 将日志复制到 Follower 节点
3. 多数节点（N/2+1）写入成功后，Leader 提交日志
4. Leader 返回成功给客户端

**双协议协调：** Nacos 同时运行两套协议，服务注册走 Distro（AP），配置管理走 Raft（CP）。通过 `nacos.core.protocol.distro` 和 `nacos.core.protocol.raft` 分别配置。

---

## Seata 二阶段提交详细流程（beforeImage/afterImage 校验）

Seata AT 模式的二阶段提交是核心机制，通过 `beforeImage` 和 `afterImage` 实现自动回滚。

**一阶段（业务 SQL 执行）：**

1. **解析 SQL**：Seata 通过 `SQLParser` 解析 `@GlobalTransactional` 中的 SQL，识别出 `UPDATE`、`DELETE`、`INSERT` 语句和涉及的表
2. **生成 beforeImage**：执行 SQL 前，先查询受影响的数据，生成"前镜像"（beforeImage）JSON
   ```sql
   -- 示例：对于 UPDATE stock SET count = count - 1 WHERE id = 100
   -- 先查询：
   SELECT id, count, version FROM stock WHERE id = 100
   -- 结果：{id: 100, count: 10, version: 1}
   ```
3. **执行业务 SQL**：正常执行 `UPDATE stock SET count = count - 1 WHERE id = 100`
4. **生成 afterImage**：执行 SQL 后，再次查询受影响的数据，生成"后镜像"（afterImage）JSON
   ```sql
   SELECT id, count, version FROM stock WHERE id = 100
   -- 结果：{id: 100, count: 9, version: 1}
   ```
5. **写入 undo_log**：将 beforeImage、afterImage、branch_id、xid 等信息写入 `undo_log` 表
6. **注册分支事务**：向 TC（Transaction Coordinator）注册分支事务，等待 TC 的二阶段指令
7. **返回结果**：业务 SQL 执行成功，返回给调用方

**二阶段提交（Commit）：**

1. TC 收到所有分支事务的注册成功响应，决定全局提交
2. TC 向所有 RM 发送 commit 请求
3. RM 收到 commit 请求，删除对应的 `undo_log` 记录
4. 业务数据已更新，无需额外操作，释放资源

**二阶段回滚（Rollback）：**

1. TC 收到任一分支失败或超时，决定全局回滚
2. TC 向所有 RM 发送 rollback 请求
3. RM 收到 rollback 请求，查询 `undo_log` 中的 beforeImage 和 afterImage

**beforeImage/afterImage 校验：**
4. **校验当前数据**：查询当前数据库中的实际数据，与 afterImage 比对
   - 如果一致，说明数据未被其他事务修改，可以安全回滚
   - 如果不一致，说明存在脏写（数据被其他事务修改），抛出异常
5. **生成反向 SQL**：根据 beforeImage 生成反向 SQL
   ```sql
   -- 回滚更新：恢复到旧值
   UPDATE stock SET count = 10 WHERE id = 100
   ```
6. **执行反向 SQL**：执行生成的反向 SQL，将数据恢复到 beforeImage 状态
7. **删除 undo_log**：回滚成功后，删除对应的 `undo_log` 记录

**脏写处理：** 如果校验发现当前数据与 afterImage 不一致，Seata 不会自动回滚，而是抛出异常记录日志，需要人工介入处理。这是 AT 模式的"一阶段资源锁定 + 二阶段校验"机制的局限性。

---

## Sentinel 滑动窗口计数原理

Sentinel 的滑动窗口是其限流和熔断统计的核心数据结构，用于在时间维度上精确统计流量。

**数据结构：**

```text
时间轴：0ms          500ms         1000ms        1500ms
        |─────────────|─────────────|─────────────|
        窗口1[0,500)   窗口2[500,1000) 窗口3[1000,1500)
```

- **时间窗口（Window）**：将时间划分为固定长度的小窗口，每个窗口统计独立的数据（QPS、RT、异常数等）
- **滑动窗口数组（Window Array）**：一个环形数组，默认存储 2 个时间窗口（`sampleCount=2`），每个窗口 500ms
- **LeapArray**：核心实现类，管理窗口数组的创建、复用、过期

**统计流程：**

1. **请求到达**：记录当前时间戳 `currentTime`
2. **计算窗口位置**：`windowStart = currentTime - (currentTime % windowLength)`
3. **计算数组索引**：`index = (windowStart / windowLength) % sampleCount`
4. **检查窗口有效性**：如果数组 `index` 位置的窗口 `windowStart` 与当前计算的 `windowStart` 一致，说明是当前窗口，直接复用
5. **窗口过期处理**：如果窗口已过期（`windowStart + windowLength < currentTime`），重置窗口数据，更新 `windowStart` 为当前时间
6. **数据写入**：通过 `addCount()` 原子操作更新窗口中的计数器（QPS +1、RT 累加等）
7. **数据读取**：遍历所有未过期窗口，累加统计数据

**关键参数：**
- `sampleCount`：窗口数量，默认为 2，值越大统计越精确但性能开销越大
- `intervalInMs`：统计时间间隔，默认为 1000ms（1 秒）
- `windowLengthMs`：每个窗口长度 = `intervalInMs / sampleCount`，默认 500ms

**为什么用滑动窗口解决固定窗口的临界问题：**

固定窗口在 0:00-1:00 统计 100 个请求，1:00-2:00 统计 100 个请求，但在 0:59:59 到 1:00:01 的两秒内可能涌入 200 个请求。滑动窗口将时间粒度细化，例如 1 秒分为 2 个 500ms 窗口，每分钟统计 120 个窗口，以当前时刻为起点向前覆盖 1 秒，精确度过载。窗口数量越多，统计越精确，但内存开销也越大。

---

## Prometheus 拉模型 vs 推模型

Prometheus 采用的是**拉模型（Pull Model）**，与传统的推模型（Push Model，如 Graphite、InfluxDB）有本质区别。

**拉模型（Prometheus 方式）：**
- Prometheus Server 定期主动从目标 exporter 拉取指标数据
- 配置中指定 `scrape_configs`：目标地址、拉取间隔、Metrics 路径
- 每个目标服务需要暴露 HTTP 端点（`/metrics`），Prometheus 按时间间隔请求
- **优势：** 集中式管理，运维人员通过配置控制采集哪些服务和指标；健康检查天然集成，拉取失败即代表服务异常；更容易做容量规划
- **劣势：** 服务需要暴露端点，在某些网络隔离场景（如 Kubernetes 集群外）需要额外配置

**推模型（Push 方式）：**
- 每个服务主动将指标数据发送到中心化的收集器
- 常见实现：StatsD、Graphite、Prometheus Pushgateway（推模型变种）
- 服务 SDK 中调用 `push()` 方法发送数据
- **优势：** 服务无需暴露端口，适用于短生命周期任务（如批处理、CronJob）；网络拓扑更简单，服务只需出站连接
- **劣势：** 运维人员难以统一控制采集范围；数据冗余和重复问题；Pushgateway 作为单点，可能成为瓶颈

**Prometheus 的拉模型细节：**

1. **服务发现**：Prometheus 通过静态配置或服务发现机制（Kubernetes、Consul、DNS）获取目标列表
2. **HTTP 请求**：每个 `scrape_interval`（默认 15s）向目标 URL 发起 HTTP GET 请求
3. **数据格式**：目标返回 Prometheus 文本格式的指标数据
4. **数据存储**：Prometheus 解析文本，将指标数据存储到本地 TSDB（时间序列数据库）
5. **失败处理**：拉取失败（超时、连接拒绝）时，重试并记录 `up` 指标为 0，标记目标不可用

**混合使用场景：** 对于短任务（如批处理），使用 Pushgateway 作为中间代理，任务将指标推送到 Pushgateway，Prometheus 再从 Pushgateway 拉取。对于长期运行的服务（Web 应用、数据库），直接使用拉模型。