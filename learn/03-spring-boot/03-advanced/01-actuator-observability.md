# Actuator 可观测性 — 监控 · 指标 · 健康检查

> 等级：🎯 面试进阶
> 目标：使用 Actuator 构建生产级监控体系，掌握 health、metrics、info 等核心端点，并集成 Prometheus + Grafana。

---

## 一、Actuator 是什么

Spring Boot Actuator 是**生产环境监控组件**，提供应用运行时的健康检查、指标、日志、环境信息等 HTTP 端点。它是微服务可观测性（Observability）的基础。

### 1.1 引入依赖

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

### 1.2 暴露端点

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,env,loggers,beans,conditions
        # include: "*"  # 暴露所有端点（生产慎用）
  endpoint:
    health:
      show-details: always    # 显示健康检查详情
      probes:
        enabled: true          # 启用探针（K8s 就绪/存活探针）
```

---

## 二、核心端点详解

| 端点 | 路径 | 说明 |
|------|------|------|
| `health` | `/actuator/health` | 健康状态（UP/DOWN） |
| `metrics` | `/actuator/metrics` | JVM、HTTP、DB 等指标 |
| `info` | `/actuator/info` | 应用自定义信息 |
| `env` | `/actuator/env` | 环境属性（注意脱敏） |
| `beans` | `/actuator/beans` | 容器中所有 Bean |
| `conditions` | `/actuator/conditions` | 自动配置条件评估（哪些生效/失效） |
| `loggers` | `/actuator/loggers` | 运行时修改日志级别 |
| `mappings` | `/actuator/mappings` | URL 映射 |
| `shutdown` | `/actuator/shutdown` | 优雅关闭（默认关闭，需显式开启） |
| `metrics` | `/actuator/metrics/jvm.memory.used` | 指定指标详细值 |

### 2.1 health 端点响应

```json
{
  "status": "UP",
  "components": {
    "db": { "status": "UP" },
    "diskSpace": { "status": "UP" },
    "redis": { "status": "UP", "details": { "version": "7.4.1" } },
    "rocketMq": { "status": "UP" }
  }
}
```

---

## 三、Micrometer 指标监控

### 3.1 Micrometer 是什么

Micrometer 是 Spring Boot 3 内置的**指标门面**（类似 SLF4J 之于日志），统一了各种监控系统的指标格式，可以输出到 Prometheus、InfluxDB、Datadog 等。

### 3.2 内置指标类型

| 类型 | 说明 | 示例 |
|------|------|------|
| Counter | 单调递增计数器 | 请求数、错误数 |
| Gauge | 可增减的数值 | 内存使用量、线程数 |
| Timer | 耗时统计 | 接口响应时间 |
| DistributionSummary | 分布统计 | 订单金额分布 |

### 3.3 自定义指标

```java
@Component
public class OrderMetrics {

    private final Counter orderCounter;
    private final Timer orderTimer;

    public OrderMetrics(MeterRegistry registry) {
        this.orderCounter = Counter.builder("mall.order.created")
            .description("创建的订单数")
            .tag("source", "api")
            .register(registry);

        this.orderTimer = Timer.builder("mall.order.create.duration")
            .description("创建订单耗时")
            .publishPercentileHistogram()
            .register(registry);
    }

    // 记录订单创建
    public void recordOrderCreated() {
        orderCounter.increment();
    }

    // 用 Timer 包裹耗时逻辑
    public void recordCreateTime(Runnable action) {
        orderTimer.record(action);
    }
}
```

### 3.4 自定义 HealthIndicator

```java
// 场景：检查 RocketMQ 连接是否正常
@Component
public class RocketMQHealthIndicator implements HealthIndicator {

    private final RocketMQTemplate rocketMQTemplate;

    public RocketMQHealthIndicator(RocketMQTemplate rocketMQTemplate) {
        this.rocketMQTemplate = rocketMQTemplate;
    }

    @Override
    public Health health() {
        try {
            // 检查 Producer 是否可用
            rocketMQTemplate.getProducer().getMQClientAPIImpl();
            return Health.up()
                .withDetail("nameServer", "10.0.0.12:9876")
                .build();
        } catch (Exception e) {
            return Health.down()
                .withDetail("error", e.getMessage())
                .build();
        }
    }
}
```

---

## 四、集成 Prometheus + Grafana

### 4.1 引入 Micrometer Prometheus 依赖

```xml
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```

### 4.2 暴露 Prometheus 端点

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus
  metrics:
    tags:
      application: mall-order-service   # 全局标签，区分服务
```

访问 `http://localhost:8080/actuator/prometheus` 即可看到 Prometheus 格式指标：

```prometheus
# HELP http_server_requests_seconds
# TYPE http_server_requests_seconds histogram
http_server_requests_seconds_count{application="mall-order-service",method="GET",status="200",uri="/api/orders"} 156.0
```

### 4.3 Prometheus 配置

```yaml
# prometheus.yml
scrape_configs:
  - job_name: 'mall-services'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['order-service:8080', 'product-service:8080', 'search-service:8080']
```

### 4.4 Grafana Dashboard

在 Grafana 中创建 Dashboard，常用面板：

| 面板 | PromQL 表达式 |
|------|--------------|
| QPS | `sum(rate(http_server_requests_seconds_count[1m])) by (uri)` |
| P99 延迟 | `histogram_quantile(0.99, sum(rate(http_server_requests_seconds_bucket[1m])) by (le, uri))` |
| 错误率 | `sum(rate(http_server_requests_seconds_count{status=~"5.."}[1m])) / sum(rate(http_server_requests_seconds_count[1m]))` |
| JVM 堆内存 | `jvm_memory_used_bytes{area="heap"}` |
| 线程数 | `jvm_threads_live_threads` |

---

## 五、生产实践：健康检查与优雅退出

### 5.1 K8s 探针配置

```yaml
# Kubernetes 使用 Actuator 探针端点
livenessProbe:
  httpGet:
    path: /actuator/health/liveness
    port: 8080
  initialDelaySeconds: 30
  periodSeconds: 10

readinessProbe:
  httpGet:
    path: /actuator/health/readiness
    port: 8080
  initialDelaySeconds: 10
  periodSeconds: 5
```

### 5.2 优雅停机

```yaml
server:
  shutdown: graceful    # 优雅停机
spring:
  lifecycle:
    timeout-per-shutdown-phase: 30s   # 每阶段等待时间
```

优雅停机流程：收到 SIGTERM → 停止接收新请求 → 处理完已接收请求 → 释放资源 → 退出。

---

## 六、面试 STAR 案例：排查订单服务内存飙升

**Situation**：订单服务运行 2 天后内存从 2GB 飙升到 6GB，接近 OOM。

**Task**：利用 Actuator 与监控体系定位内存泄漏源头。

**Action**：

1. 通过 `/actuator/health` 确认各组件状态正常
2. 通过 `/actuator/metrics/jvm.memory.used` + Grafana 曲线确认堆内存泄漏趋势
3. 通过 `/actuator/beans` + heap dump 分析发现 `OrderMessageConsumer` 的 `ConcurrentHashMap` 缓存订单消息无限增长
4. 修复：为本地缓存增加容量上限 + TTL 过期策略，改用 Caffeine

**Result**：内存稳定在 2GB 以内，P99 延迟从 800ms 降到 120ms，监控面板用红色告警提前发现，避免生产故障。

---

## 七、面试要点

| 问题 | 一句话答案 |
|------|-----------|
| Actuator 是什么？ | 生产级监控组件，通过 HTTP 端点暴露健康、指标、环境等信息 |
| health 端点和 K8s 探针什么关系？ | 探针直接调用 /actuator/health/liveness 和 /readiness |
| Micrometer 是什么？ | 指标门面，统一输出到 Prometheus/InfluxDB 等监控系统 |
| 怎么自定义健康检查？ | 实现 HealthIndicator 接口，返回 Health.up()/down() |
| 端点安全注意什么？ | 用 acutor 独立端口 + Security 认证，env 等敏感端点不暴露 |
| 优雅停机怎么实现？ | server.shutdown=graceful + 超时时间 |

> 监控体系完备后，进入测试篇：如何给 Spring Boot 应用写出高质量的测试。