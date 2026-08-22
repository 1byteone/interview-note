# 补充篇 · 定时任务调度 + 布隆过滤器 + MQ 幂等消费

> 上篇系列遗漏了三个关键模块：ElasticJob 定时任务调度、Redisson 布隆过滤器、MQ 消息幂等消费。本篇补全遗漏内容。
>
> **对应项目：** `mall-scheduler-service` + `mall-common` + `mall-seckill-service`

---

## 一、ElasticJob 定时任务调度

### 1.1 基础概念

| 定时框架 | 原理 | 分布式支持 | 项目选择 |
|---------|------|-----------|---------|
| Spring `@Scheduled` | 单机定时器 | ❌ 多实例重复执行 | ❌ |
| **ElasticJob** | 分片任务，ZooKeeper 协调 | ✅ 分布式分片 | **✅** |
| XXL-Job | 调度中心 + 执行器 | ✅ 分布式 | 备选 |

**ElasticJob 是当当网开源的分布式定时任务框架，通过 ZooKeeper 实现任务分片和调度协调。**

### 2.2 三个定时任务

```java
// 任务1：加载秒杀商品（每日凌晨执行）
@Component
public class LoadSeckillProductTask implements SimpleJob {
    @Autowired
    private SeckilProductFeignClient seckilProductFeignClient;

    @Override
    public void execute(ShardingContext shardingContext) {
        // 1. 获取今天要秒杀的商品
        seckilProductFeignClient.listTodaySeckillGoods();
        // 2. 生成静态页面
        seckilProductFeignClient.generateHtml();
        // 3. 加载商品库存缓存到 Redis
        seckilProductFeignClient.loadStockCache();
    }
}

// 任务2：支付状态检查（定时轮询未支付订单）
@Component
public class PayCheckTask implements SimpleJob {
    @Autowired
    private AlipayFeignClient alipayFeignClient;

    @Override
    public void execute(ShardingContext shardingContext) {
        // 查询未支付订单 → 调用支付宝查询支付结果
        alipayFeignClient.queryPaymentResult(orderId);
    }
}

// 任务3：示例任务
@Component
public class DemoTask implements SimpleJob {
    @Override
    public void execute(ShardingContext shardingContext) {
        System.out.println("执行定时任务: " + LocalDateTime.now());
    }
}
```

**LoadSeckillProductTask 是秒杀系统的"启动器"**——每天定时把秒杀商品、静态页面、库存缓存准备好，用户秒杀时直接访问预热好的数据。

---

## 二、Redisson 布隆过滤器

### 2.1 布隆过滤器实现

```java
@Component
public class CacheBloomFilter<T> {
    private static final String BLOOM_FILTER_NAME_PREFIX = "cache:bloom:";
    private RBloomFilter<T> bloomFilter;
    @Autowired private RedissonClient redissonClient;

    @Value("${cache.bloom.name}") private String name;
    @Value("${cache.bloom.expectedInsertions}") private Long expectedInsertions;
    @Value("${cache.bloom.falseProbability}") private Double falseProbability;

    @PostConstruct
    public void init() {
        String fullName = BLOOM_FILTER_NAME_PREFIX + name;
        bloomFilter = redissonClient.getBloomFilter(fullName);
        boolean initialized = bloomFilter.tryInit(expectedInsertions, falseProbability);
        // 预计容量 10000，误判率 1%
    }

    public void add(T value) { bloomFilter.add(value); }
    public boolean mightContain(T value) { return bloomFilter.contains(value); }
}
```

**布隆过滤器的三个核心参数：**

| 参数 | 说明 | 项目中的值 |
|------|------|-----------|
| `expectedInsertions` | 预期插入数量 | 10000 |
| `falseProbability` | 误判率 | 0.01 (1%) |
| 位数组大小 | 自动计算 | ~120KB |

**一股 set 1% 误判率意味着：** 10000 个非法请求中，有 100 个会穿透布隆过滤器打到 Redis，但 9900 个被拦截——数据库压力降低 99%。

---

## 三、MQ 幂等消费

### 3.1 库存扣减消息幂等消费

```java
@Component
public class StockDeductConsumer {

    @Bean
    public Consumer<Message<String>> stockDeductInput() {
        return message -> {
            String body = message.getPayload();
            StockDeductMessageDTO dto = JsonUtils.toObj(body, StockDeductMessageDTO.class);

            // 幂等校验：基于 transactionId，30s 窗口覆盖重试
            String lockKey = "que:lock:stock:" + dto.getTransactionId();
            Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(lockKey, "1", 30, TimeUnit.SECONDS);

            if (!Boolean.TRUE.equals(acquired)) {
                log.debug("重复消息跳过: {}", dto.getTransactionId());
                return;  // 重复消息，直接跳过
            }

            // 写入库存流水 + 同步数据库库存
            seckillStockFlowService.recordStockFlow(dto);
        };
    }
}
```

**幂等设计要点：**

| 要素 | 设计 | 说明 |
|------|------|------|
| **唯一标识** | `transactionId` | 每条消息的唯一 ID |
| **去重窗口** | 30 秒 | 覆盖 MQ 重试间隔 |
| **存储介质** | Redis SETNX | 原子操作，天然防并发 |
| **自动过期** | 30 秒后自动释放 | 防内存泄漏 |

### 3.2 Redis 扣减 + MQ 写入 + 库存流水表 完整链路

```
秒杀扣减请求
    │
    ▼
Redis 原子扣减  (decrement, 扛高并发)
    │
    ▼
发送 MQ 消息  (streamBridge, 异步)
    │
    ▼
StockDeductConsumer 消费
    │
    ├── 幂等校验 (Redis SETNX transactionId)
    │
    ├── SeckillStockFlowService.recordStockFlow()
    │   ├── 写入库存流水表 (流水记录, 对账用)
    │   └── 同步数据库库存 (UPDATE 扣减)
    │
    └── 完成
```

---

## 四、面试要点

### Q1: ElasticJob 和 Spring @Scheduled 的区别？

**回答思路：** `@Scheduled` 是单机定时器，多实例部署时每个实例都会执行，导致重复执行。ElasticJob 通过 ZooKeeper 协调，任务只在某一台机器上执行一次，支持分片（数据量大时拆成多片并行处理）。电商项目必须用分布式定时任务框架。

### Q2: 布隆过滤器为什么用 1% 误判率？怎么选的？

**回答思路：** 误判率越低，位数组越大。1% 误判率意味着 99% 的非法请求被拦截，同时位数组只有 ~120KB——性价比最优。如果要 0.1% 误判率，位数组需要 ~180KB，占用增加 50% 但收益只增加 0.9 个百分点，不划算。

### Q3: MQ 消息幂等消费怎么做的？

**回答思路：** 基于 `transactionId` + Redis SETNX 实现。每条消息携带唯一 `transactionId`，消费者消费前先尝试写入 Redis（SETNX），写入成功说明是首次消费，写入失败说明是重复消息直接跳过。SETNX 带 30 秒自动过期，覆盖 MQ 重试窗口。

---

> **下一篇：** [AI搜索桥接服务 —— Java 桥接 Python AI 搜索服务的 Feign 客户端设计](./AI-SEARCH-BRIDGE.md)
>
> 看 mall-aisearch-service 如何通过 Feign 调用 Python AI 搜索服务，实现 Java 微服务与 Python AI 的桥接。