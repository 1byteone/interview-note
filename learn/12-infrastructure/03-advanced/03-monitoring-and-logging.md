# 监控与日志 — Prometheus 体系 · ELK 收集 · SkyWalking 链路

> 🎯 进阶路线 · 预计阅读时间：45 分钟
> 目标：掌握 Prometheus + Grafana 指标监控体系、ELK/EFK 日志收集架构、Alertmanager 告警与 SkyWalking 分布式链路追踪，构建生产级可观测性平台。

---

## 一、Prometheus + Grafana 监控体系

### 1.1 拉模型（Pull Model）

Prometheus 的核心设计是**主动拉取**：由 Prometheus Server 定期（默认 15s）向各服务暴露的 `/metrics` 端点发起 HTTP 请求，抓取指标数据。这与 Push 模型（如 Graphite）相比：

| 特性 | 拉模型（Pull） | 推模型（Push） |
|------|---------------|---------------|
| 服务发现 | Prometheus 主动发现目标 | 应用自行上报 |
| 存活检测 | 拉取失败即可判断服务不可用 | 难以判断"数据没来"的原因 |
| 可扩展性 | 简单（只需暴露端点） | 需独立 Agent 与队列 |
| 典型场景 | Prometheus | ELK、业务埋点上报 |

### 1.2 四种基础指标类型

| 类型 | 语义 | PromQL 示例 |
|------|------|-------------|
| Counter | 只增不减的计数器（请求数、错误数） | `rate(requests_total[1m])` |
| Gauge | 可增可减的瞬时值（内存、连接数） | `jvm_memory_used_bytes` |
| Histogram | 分桶统计（请求耗时分布），支持聚合百分位 | `histogram_quantile(0.99, ...)` |
| Summary | 客户端直接计算分位数 | `probe_duration_seconds{quantile="0.99"}` |

**关键差异**：Histogram 由服务端（Prometheus）基于 `_bucket` 计算分位数，可跨实例聚合；Summary 在**客户端**计算，无法聚合，生产推荐 Histogram。

### 1.3 常用 PromQL

```promql
# 服务 QPS（按实例分组）
sum(rate(http_server_requests_seconds_count[1m])) by (instance)

# P99 延迟
histogram_quantile(0.99, sum(rate(http_server_requests_seconds_bucket[1m])) by (le))

# 错误率
sum(rate(http_server_requests_seconds_count{status=~"5.."}[5m]))
/ sum(rate(http_server_requests_seconds_count[5m]))

# 堆内存使用率
sum(jvm_memory_used_bytes{area="heap"}) / sum(jvm_memory_max_bytes{area="heap"})
```

### 1.4 架构图

```
服务 A ───────┐
服务 B ───────┤  Pull /metrics   ┌──────────┐  查询   ┌──────────┐
服务 C ───────┼──────────────────►│Prometheus│◄───────►│ Grafana  │
              │                   │  TSDB    │  Rules  └──────────┘
Exporter（MySQL/Redis/JVM）──────►└────┬─────┘
                                      │ 告警
                                      ▼
                                ┌──────────┐   webhook  ┌──────────┐
                                │Alertmanager│─────────►│ 钉钉/企微 │
                                └──────────┘            └──────────┘
```

---

## 二、Micrometer 指标采集

Micrometer 是 Spring Boot 3 内置的**指标门面**（对标 SLF4J 之于日志），应用只面向 Micrometer API 编程，再由注册器输出到 Prometheus、InfluxDB 等。

```java
@Component
public class MallMetrics {

    private final AtomicInteger activeSessions = new AtomicInteger(0);

    public MallMetrics(MeterRegistry registry) {
        // JVM、CPU、内存指标由 spring-boot-actuator 自动注册
        // 自定义 Counter：AI 搜索调用次数
        Counter.builder("mall.ai.search.count")
            .description("AI 商城搜索召唤次数")
            .tag("engine", "vector-rag")
            .register(registry);

        // 自定义 Gauge：在线会话数
        Gauge.builder("mall.active.sessions", activeSessions, AtomicInteger::get)
            .register(registry);
    }

    public void searchInvoked() {
        meters.searchCounter.increment();
    }
}
```

Spring Boot 自动采集的指标：`jvm_*`（内存、GC、线程）、`system_cpu_*`、`process_*`、`http_server_requests_seconds`、`hikaricp_*`（连接池）、`logback_events_total` 等，无需额外编码。

---

## 三、ELK/EFK 日志收集架构

### 3.1 为什么不用日志文件 + grep

分布式环境下日志分布在几十台机器，按文件名、时间戳查找效率极低；且无法做全文关键字检索和聚合统计。需要一个集中式日志平台。

### 3.2 两种主流管线

| 方案 | 收集端 | 缓冲/传输 | 存储与检索 | 可视化 |
|------|--------|-----------|------------|--------|
| ELK | Logstash | Logstash（较重） | Elasticsearch | Kibana |
| EFK | Filebeat 采集 + Fluentd | Kafka（可选缓冲） | Elasticsearch | Kibana |

**EFK（推荐）架构**：Filebeat 是 Go 编写、内存占用极小的日志采集器，只负责采集和发送；Kafka 作缓冲削峰，防止 ES 被打爆；Logstash 做解析、清洗、富化后写入 ES。

```
应用日志（JSON 格式写入 /var/log/app/*.log）
      │ Filebeat 采集
      ▼
   Kafka（削峰缓冲，可选）
      │ Consumer
      ▼
   Logstash（解析：grok / json / 脱敏 / 字段映射）
      │
      ▼
   Elasticsearch（索引：app-2026.08.22）
      │
      ▼
   Kibana（查询、聚合、Dashboard、告警）
```

### 3.3 应用侧最佳实践

服务端输出**结构化 JSON 日志**（而非纯文本），便于 Logstash 直接解析，避免 grok 正则脆弱易漏：

```json
{"timestamp":"2026-08-22T10:15:30.123+08:00",
 "level":"ERROR","service":"order-service","traceId":"a3f2c1...",
 "message":"扣减库存失败","stack":"java.lang.RuntimeException..."
}
```

关键字段建议：`traceId`（与 SkyWalking/RocketMQ 关联）、`service`、`userId`、`costMs`、`url`，为检索和排障建立索引。

---

## 四、Alertmanager 告警规则

### 4.1 告警规则示例（prometheus.yml 引用的 rules 文件）

```yaml
groups:
  - name: mall-service-alerts
    rules:
      # 服务宕机告警：5 分钟无数据
      - alert: ServiceDown
        expr: up{job="mall-services"} == 0
        for: 5m
        labels:
          severity: critical
        annotations:
          summary: "服务 {{ $labels.instance }} 不可用"

      # 高错误率告警：5 分钟错误率超 5%
      - alert: HighErrorRate
        expr: |
          sum(rate(http_server_requests_seconds_count{status=~"5.."}[5m]))
          / sum(rate(http_server_requests_seconds_count[5m])) > 0.05
        for: 3m
        labels:
          severity: warning
        annotations:
          summary: "{{ $labels.service }} 错误率超过 5%"

      # P99 延迟告警：超过 500ms
      - alert: HighP99Latency
        expr: |
          histogram_quantile(0.99, sum(rate(http_server_requests_seconds_bucket[5m]))
          by (le, service)) > 0.5
        for: 5m
        labels:
          severity: warning
```

### 4.2 通知渠道

```yaml
route:
  group_by: ['alertname']
  group_wait: 30s
  group_interval: 5m
  repeat_interval: 4h
  receiver: 'webhook-dingtalk'

receivers:
  - name: 'webhook-dingtalk'
    webhook_configs:
      - url: 'https://oapi.dingtalk.com/robot/send?access_token=xxxx'
        send_resolved: true
```

---

## 五、SkyWalking 链路追踪

### 5.1 核心概念

| 术语 | 说明 | 类比 |
|------|------|------|
| Trace | 一次完整请求的全链路轨迹 | 通话记录 |
| Span | Trace 中的一个调用单元（一次 RPC/DB/SQL） | 单次通话 |
| Segment | 单个服务实例内的所有 Span 集合 | 一个人在一台设备上的通话 |

```
Trace: 用户下单
├── Span: GET /api/orders (order-service)    ← Segment A
│   ├── Span: Feign 调用 inventory-service    │
│   │   └── Span: 扣减库存 (inventory-service)  ← Segment B
│   └── Span: UPDATE orders (MySQL)
└── Span: RocketMQ 发送消息
```

每个 Span 携带 `traceId + spanId + parentSpanId`，串联出完整调用树；跨服务通过 HTTP Header（`sw8`）或 MQ 消息头传播上下文。

### 5.2 接入方式

**无侵入接入**：Java Agent 方式，`java -javaagent:skywalking-agent.jar -jar app.jar`，自动探针拦截 HTTP/RPC/DB 调用，业务代码零改动。

```yaml
# agent/config/agent.config 关键配置
agent.service_name=mall-order-service
collector.backend_service=oap-server:11800
```

### 5.3 慢链路排查（STAR 案例）

**Situation**：用户反馈过问 AI 商城"提交订单"偶发 3s+。
**Task**：定位慢在哪个服务、哪个环节。
**Action**：SkyWalking 追踪页按 traceId 过滤，拓扑图显示耗时集中在 `search-service` 调用向量数据库的 Span，耗时 2.4s；进一步看是 RAG 召回时 embedding 请求超时未设置读超时。
**Result**：为向量检索 Feign 设置失控于 1s 的连接/读超时 + 降级到关键词检索兜底，P95 从 2.5s 降到 600ms。

---

## 六、面试要点

| 问题 | 一句话答案 |
|------|-----------|
| Prometheus 为什么用拉模型？ | 服务发现简单、天然带存活检测、数据易扩展 |
| Histogram 和 Summary 区别？ | Histogram 服务端聚合可跨实例，Summary 客户端计算无法聚合 |
| Counter 和 Gauge 区别？ | Counter 只增不减（rate 求速率），Gauge 反映瞬时状态 |
| Micrometer 是什么？ | 指标门面，统一 API 输出到 Prometheus/InfluxDB |
| ELK 和 EFK 区别？ | EFK 用 Filebeat 替代 Logstash 采集，轻量；可加 Kafka 缓冲 |
| 为什么日志要结构化？ | JSON 格式方便 Logstash 直接解析，grok 正则脆弱易漏 |
| Alertmanager 三个时间参数？ | group_wait 同组通知等待、group_interval 组间间隔、repeat_interval 重复告警间隔 |
| Trace/Span/Segment 什么关系？ | Trace 是完整链路，Segment 是单实例内的所有 Span，Span 是最小调用单元 |