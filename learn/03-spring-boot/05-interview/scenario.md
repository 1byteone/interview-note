# 场景题 — 实战中的 Spring Boot 问题

> 等级：🎯 面试冲刺
> 目标：通过真实场景问题，考察 Spring Boot 配置管理、启动优化、内存泄漏排查等实战能力。
> 路径：STAR 法则贯穿每个案例。

---

## 一、多环境配置管理

### 场景

**Situation**：AI 商城有 dev、test、staging、prod 四个环境，每个环境的数据库、Redis、日志级别都不同。之前团队用多个 application-{profile}.yml 文件，但配置分散在多个项目，版本管理混乱，经常出现配置不一致导致线上问题。

### Task

设计一套能统一管理、可追溯、可审计的多环境配置方案。

### Action

**方案：Nacos 配置中心 + Git 配置版本化**

1. **本地配置**：`application.yml` 只放通用配置（应用名、编码等）
2. **环境覆盖**：`application-dev.yml` / `application-prod.yml` 放各环境差异配置
3. **配置中心**：Nacos 统一管理敏感配置（数据库密码、API Key），通过 `bootstrap.yml` 引入
4. **配置版本化**：所有配置在 Git 中维护，通过 Code Review 保证变更可追溯

```yaml
# bootstrap.yml
spring:
  application:
    name: mall-order-service
  cloud:
    nacos:
      config:
        server-addr: ${NACOS_ADDR:127.0.0.1:8848}
        namespace: ${NACOS_NAMESPACE:public}
        file-extension: yaml
        shared-configs:
          - data-id: common.yaml
          - data-id: datasource-${spring.profiles.active}.yaml   # 各环境数据库配置
          - data-id: redis-${spring.profiles.active}.yaml        # 各环境缓存配置
        refresh-enabled: true
```

```yaml
# application.yml（本地）
spring:
  profiles:
    active: ${PROFILE:dev}
  application:
    name: mall-order-service

# 本地默认值，Nacos 配置会覆盖
server:
  port: 8082
```

5. **配置优先级策略**

```java
// 配置优先级：命令行 > 环境变量 > Nacos > application-{profile}.yml > application.yml
// 生产环境使用环境变量 + Nacos 配置
// 禁止在 Nacos 中修改敏感配置，一律通过环境变量注入
```

### Result

- 配置集中管理，所有环境配置可追溯
- 密码等敏感信息通过环境变量注入，不落地到配置文件
- 配置变更实时生效（Nacos 自动刷新）
- 环境切换只需修改 `spring.profiles.active`

---

## 二、应用启动慢优化

### 场景

**Situation**：AI 商城二次开发后，订单服务启动时间从 8 秒增加到 30 秒，CI/CD 流水线每次部署等待过久，影响开发效率。

### Task

定位启动慢的根因，将启动时间降低到 15 秒以内。

### Action

**第一步：分析启动时间分布**

```java
// 在 application.yml 开启启动耗时报告
debug: true
logging:
  level:
    org.springframework.boot.autoconfigure.logging: DEBUG
```

启动后查看日志中的 `StartupTime` 和 `AutoConfigurationReport`，定位瓶颈。

**第二步：使用 Actuator 的 conditions 端点**

```bash
curl http://localhost:8082/actuator/conditions
```

查看哪些自动配置类被加载，排除不必要的。

**第三步：优化措施**

```java
// 1. 排除不需要的自动配置
@SpringBootApplication(exclude = {
    DataSourceAutoConfiguration.class,       // 订单服务用 MyBatis，排除 JPA 数据源
    SecurityAutoConfiguration.class,         // 不需要的安全配置
    QuartzAutoConfiguration.class            // 不需要定时任务
})

// 2. 缩小组件扫描范围
@ComponentScan(basePackages = "com.mall.order")

// 3. 非核心 Bean 延迟加载
@Service
@Lazy(true)
public class ReportService { ... }

// 4. 内嵌 Tomcat 配置优化
server:
  tomcat:
    threads:
      min-spare: 10         # 减少初始线程数
      max: 200
```

**第四步：分层优化**

```yaml
# 优化后配置
spring:
  autoconfigure:
    exclude:
      - org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration
      - org.springframework.boot.autoconfigure.quartz.QuartzAutoConfiguration
  jpa:
    open-in-view: false     # 关闭 OSIV（减少 Session 创建开销）
```

### Result

- 启动时间从 30 秒降至 12 秒
- 减少 8 个不必要的自动配置加载
- 非核心服务延迟加载，核心功能 5 秒内可用

---

## 三、应用启动时健康检查失败

### 场景

**Situation**：订单服务在 K8s 中滚动更新时，新 Pod 启动后立即被 K8s 杀死，原因是健康检查失败。但手动访问应用又能正常响应。

### Task

设计合理的健康检查探针，确保应用在真正就绪后才接收流量。

### Action

**根因分析**：K8s 的 readinessProbe 在容器启动后立即检查 `/actuator/health`，但此时 Spring Boot 尚未完全启动（正在初始化数据库连接池、消息队列等），Actuator 返回 DOWN 状态。

```java
// 1. 启用 K8s 探针端点
management:
  endpoint:
    health:
      probes:
        enabled: true        # 开启 /actuator/health/liveness 和 /readiness
      show-details: always

// 2. 自定义就绪检查：等待关键依赖就绪
@Component
public class ReadinessHealthIndicator implements HealthIndicator {

    private final AtomicBoolean ready = new AtomicBoolean(false);

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        ready.set(true);  // 应用完全就绪后才标记为 UP
    }

    @Override
    public Health health() {
        return ready.get()
            ? Health.up().build()
            : Health.down().withDetail("reason", "等待初始化完成").build();
    }
}
```

```yaml
# K8s 探针配置
readinessProbe:
  httpGet:
    path: /actuator/health/readiness
    port: 8080
  initialDelaySeconds: 30    # 等待应用启动
  periodSeconds: 10
  failureThreshold: 3

livenessProbe:
  httpGet:
    path: /actuator/health/liveness
    port: 8080
  initialDelaySeconds: 60
  periodSeconds: 15
  failureThreshold: 3
```

### Result

- Pod 不再被误杀，滚动更新成功率 100%
- 应用完全就绪后才接收流量，用户请求不会打到未就绪的 Pod
- 存活探针及时检测到死锁/OOM 等异常，自动重启

---

## 四、内存泄漏排查

### 场景

**Situation**：秒杀服务上线后，运行 2 天内存从 2GB 飙升到 6GB，接近 OOM。重启后恢复正常，但 2 天后再次飙升。

### Task

利用 Actuator + 监控 + heap dump 定位内存泄漏源。

### Action

**第一步：监控确认**

通过 Grafana 观察 JVM 指标：

```promql
# 堆内存曲线（持续上升）
jvm_memory_used_bytes{area="heap", application="mall-seckill-service"}

# GC 频率（Old GC 越来越频繁）
rate(jvm_gc_pause_seconds_count{application="mall-seckill-service"}[5m])
```

**第二步：Actuator 实时查看**

```bash
# 查看内存使用
curl http://localhost:8083/actuator/metrics/jvm.memory.used
curl http://localhost:8083/actuator/metrics/jvm.gc.pause

# 查看 Bean 状态
curl http://localhost:8083/actuator/beans
```

**第三步：Heap Dump 分析**

```bash
# 生成 heap dump
jmap -dump:live,format=b,file=seckill-heap.hprof <pid>

# 或通过 Actuator 端点（需开启）
# management.endpoint.heapdump.enabled=true
curl http://localhost:8083/actuator/heapdump -o seckill-heap.hprof
```

用 MAT（Memory Analyzer Tool）打开 heap dump，发现 `SeckillOrderCache` 类的 `ConcurrentHashMap` 占用了 3.8GB。

**第四步：根因定位**

```java
// 问题代码：缓存无限增长，没有过期机制
@Component
public class SeckillOrderCache {
    // 问题：ConcurrentHashMap 无限增长，没有容量上限和过期策略
    private final ConcurrentHashMap<Long, SeckillOrder> cache = new ConcurrentHashMap<>();

    public void cacheOrder(SeckillOrder order) {
        cache.put(order.getOrderId(), order);
    }

    public SeckillOrder getOrder(Long orderId) {
        return cache.get(orderId);  // 永远不淘汰
    }
}
```

**第五步：修复**

```java
// 修复方案：使用 Caffeine 替代 ConcurrentHashMap
@Component
public class SeckillOrderCache {
    // Caffeine 支持 TTL 和容量上限
    private final Cache<Long, SeckillOrder> cache = Caffeine.newBuilder()
        .maximumSize(10000)           // 最多缓存 1 万条
        .expireAfterWrite(30, TimeUnit.MINUTES)  // 30 分钟过期
        .recordStats()                // 记录缓存命中率
        .build();

    public void cacheOrder(SeckillOrder order) {
        cache.put(order.getOrderId(), order);
    }

    public SeckillOrder getOrder(Long orderId) {
        return cache.getIfPresent(orderId);
    }
}
```

### Result

- 内存稳定在 2GB 以内，不再持续增长
- 缓存命中率 99.2%，性能不受影响
- 配合监控告警，内存超过阈值自动告警

---

## 五、场景题总结

| 场景 | 核心问题 | 解决方案 | 涉及知识点 |
|------|---------|---------|-----------|
| 多环境配置管理 | 配置分散、版本混乱 | Nacos 配置中心 + Git 版本化 | 配置优先级、Profile |
| 启动慢优化 | 自动配置过多、扫描范围大 | 排除不必要配置、延迟加载 | 自动配置排除、@Lazy |
| 健康检查失败 | 容器就绪前就被检查 | 探针端点 + 自定义 ReadinessIndicator | Actuator、K8s 探针 |
| 内存泄漏 | 本地缓存无限增长 | Caffeine 替代 ConcurrentHashMap | 缓存 TTL、Heap Dump 分析 |

> 进入代码题篇：手写自定义 Starter、自定义条件注解、自定义 HealthIndicator。