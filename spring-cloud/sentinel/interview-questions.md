# Sentinel 面试题大全

## 📚 知识体系

```
Sentinel 核心能力
├── 流量控制 (Flow Control)
│   ├── QPS 限流
│   ├── 线程数限流
│   ├── 并发控制
│   └── 预热/匀速器/排队等待
├── 熔断降级 (Circuit Breaking)
│   ├── 慢调用比例
│   ├── 异常比例
│   ├── 异常数
│   └── 熔断恢复（半开检测）
├── 系统自适应保护
│   ├── Load 自适应
│   ├── CPU 使用率
│   ├── 平均 RT
│   ├── 入口 QPS
│   └── 并发线程数
├── 热点参数限流
│   ├── 参数索引
│   ├── 参数限流阈值
│   └── 参数例外项
└── 规则管理
    ├── 动态规则
    ├── Nacos 持久化
    └── Dashboard 控制台
```

---

## 🎯 Level 1：基础题

### 1. Sentinel 是什么？主要功能有哪些？
**答案**：
Sentinel 是阿里巴巴开源的**流量控制、熔断降级、系统负载保护**组件，是微服务稳定性的核心保障。

**核心功能**：
1. **流量控制**：控制请求进入的速率，防止系统过载
2. **熔断降级**：下游服务异常时切断调用，快速失败
3. **系统自适应保护**：根据系统负载自动调节入口流量
4. **热点参数限流**：对特定参数（如商品 ID）进行限流

### 2. Sentinel 与 Hystrix 的区别？
**答案**：

| 特性 | Sentinel | Hystrix |
|------|----------|---------|
| 隔离策略 | 信号量隔离 | 线程池/信号量 |
| 熔断策略 | 慢调用/异常/异常数 | 异常比例 |
| 实时监控 | Dashboard 可视化 | 有限 |
| 动态规则 | 支持（Nacos/APOLLO） | 不支持 |
| 流量控制 | 丰富（QPS/线程/预热/匀速） | 有限 |
| 系统防护 | 自适应保护 | 不支持 |
| 维护状态 | 阿里活跃维护 | 停止维护 |

---

## 🎯 Level 2：进阶题

### 3. Sentinel 限流有哪些策略？
**答案**：

**基于 QPS 限流**：
| 模式 | 说明 | 适用场景 |
|------|------|----------|
| 直接拒绝 | 超过阈值直接拒绝（默认） | 常规限流 |
| 预热（Warm Up） | 逐步增加到阈值 | 冷启动场景 |
| 匀速排队 | 请求匀速通过，排队等待 | 削峰填谷 |
| 预热+匀速 | 先预热再匀速 | 复杂场景 |

**基于线程数限流**：
- 控制并发线程数，超过阈值拒绝新请求

### 4. Sentinel 熔断降级的原理？
**答案**：

**熔断状态机**：
```text
关闭（CLOSED）
    ↓ 达到熔断条件
    ↓
打开（OPEN）
    ↓ 熔断超时（默认 5s）
    ↓
半开（HALF_OPEN）
    ↓ 探测成功 → 关闭（CLOSED）
    ↓ 探测失败 → 重新打开（OPEN）
```

**熔断策略**：
| 策略 | 触发条件 | 说明 |
|------|----------|------|
| 慢调用比例 | RT > 阈值 且 比例 > 阈值 | 响应时间过长触发 |
| 异常比例 | 异常比例 > 阈值 | 错误率过高触发 |
| 异常数 | 异常数 > 阈值（分钟级） | 错误量过大触发 |

---

## 🎯 Level 3：高级题

### 5. Sentinel 热点参数限流如何实现？
**答案**：
热点参数限流是对**特定参数值**进行限流，例如对频繁查询的商品 ID 进行限流。

**配置示例**：
```java
@GetMapping("/product/{id}")
@SentinelResource(
    value = "getProduct",
    blockHandler = "handleBlock"
)
public Product getProduct(@PathVariable Long id) {
    return productService.getProductById(id);
}
```

**热点规则**：
```java
ParamFlowRule rule = new ParamFlowRule("getProduct")
    .setParamIdx(0)           // 对第 0 个参数限流
    .setCount(100)            // 阈值 100 QPS
    .setDurationInSec(1);     // 时间窗口 1s

// 例外：商品 ID 1001 可以 2000 QPS
ParamFlowItem item = new ParamFlowItem()
    .setObject("1001")
    .setClassType(Long.class.getName())
    .setCount(2000);
rule.getParamFlowItemList().add(item);
```

### 6. Sentinel 规则如何持久化？
**答案**：
**问题**：Sentinel 默认规则存储在内存中，重启丢失。

**解决方案**：通过 `DataSource` 扩展将规则持久化到 Nacos/APOLLO/ZK。

```java
@Configuration
public class SentinelConfig {
    @Bean
    public SentinelDataSource sentinelDataSource() {
        // 从 Nacos 读取限流规则
        ReadableDataSource<String, List<FlowRule>> ds = new NacosDataSource<>(
            "localhost:8848",
            "DEFAULT_GROUP",
            "sentinel-flow-rules",
            source -> JSON.parseObject(source, new TypeReference<List<FlowRule>>() {})
        );
        FlowRuleManager.register2Property(ds.getProperty());
        return ds;
    }
}
```

---

## 🎯 Level 4：专家题

### 7. Sentinel 与 Spring Cloud Gateway 集成如何实现限流？
**答案**：

```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: order-service
          uri: lb://order-service
          predicates:
            - Path=/order/**
          filters:
            - name: RequestRateLimiter
              args:
                key-resolver: "#{@userKeyResolver}"
                redis-rate-limiter:
                  replenishRate: 100
                  burstCapacity: 200
```

**结合 Sentinel**：
```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: order-service
          uri: lb://order-service
          predicates:
            - Path=/order/**
          filters:
            - name: SentinelGatewayFilter
```

```java
@Configuration
public class SentinelGatewayConfig {
    @PostConstruct
    public void init() {
        // 按 API 分组限流
        GatewayFlowRule rule = new GatewayFlowRule("order-service")
            .setCount(100)
            .setIntervalSec(1);
        GatewayRuleManager.loadRules(Collections.singletonList(rule));
    }
}
```

---

## 📖 学习资源

### 推荐项目
- [Sentinel 官方文档](https://sentinelguard.io/)
- [Sentinel 示例代码](https://github.com/alibaba/Sentinel/tree/master/sentinel-demo)
- [Spring Cloud Alibaba Sentinel 集成](https://sca.aliyun.com/)

### 最佳实践
1. 核心接口必须配置限流规则
2. 熔断时间不宜过长（默认 5s）
3. 规则持久化到 Nacos，避免重启丢失
4. 结合 Sentinel Dashboard 监控实时流量
5. 分级熔断：高优服务先熔断，保护核心链路