# AI 商城 — Redis 集成实战

> 等级：🎯 面试进阶
> 主题：Redis 在 AI 智能商城中的 4 个核心落地场景，每个场景用 STAR 法则组织
> 路径：多级缓存 → 秒杀库存预扣 → 分布式 Session → 接口幂等性 Token

---

## 〇、深度剖析参考

| 主题 | 本 learn 文档 | docs/tech-stack-analysis 深度剖析 |
|------|--------------|--------------------------------|
| Cache-Aside 模式 | 本文 § 一 | [04-REDIS-CACHE.md](../../../../5-research/tech-stack-analysis/mall-exercise/04-REDIS-CACHE.md) — 缓存策略实战 |
| 秒杀库存预扣 + 分布式锁 | 本文 § 二 | [06-SECKILL-HIGHCONCUR.md](../../../../5-research/tech-stack-analysis/mall-micro-cloud/06-SECKILL-HIGHCONCUR.md) — Redisson + 布隆过滤器 |
| Redis 向量检索 (RedisVL) | — | [06-VECTOR-STORE.md](../../../../5-research/tech-stack-analysis/mall-ai-search/06-VECTOR-STORE.md) — Redis Stack HNSW 索引 |
| 布隆过滤器防穿透 | — | [11-SCHEDULER-BLOOMFILTER.md](../../../../5-research/tech-stack-analysis/mall-micro-cloud/11-SCHEDULER-BLOOMFILTER.md) — Redisson RBloomFilter |

---

## 一、多级缓存架构：商品详情页

### Situation（场景）

AI 商城首页和商品详情页每天数百万次请求，高峰期 QPS 达到 50000+。每次查询都走 MySQL 是不现实的，响应时间 200ms+ 用户体验极差。

### Task（任务）

设计一个多级缓存架构，同时满足：
- 读请求 P99 响应时间 < 20ms
- 缓存命中率 > 95%
- 数据最终一致性（容忍秒级不一致）
- 缓存雪崩时能自动降级

### Action（方案）

**三层缓存架构**：

```
┌────────┐    ┌────────────┐    ┌───────────┐    ┌────────┐
│ 客户端  │───▶│ Caffeine  │───▶│  Redis    │───▶│ MySQL  │
│ (请求)  │    │ (本地缓存)  │    │ (分布式缓存)│    │ (持久化)│
└────────┘    └────────────┘    └───────────┘    └────────┘
                  L1: 1ms          L2: 5ms          L3: 50ms
```

```java
@Component
public class ProductCacheService {
    // L1: Caffeine 本地缓存
    private final Cache<Long, Product> localCache = Caffeine.newBuilder()
        .maximumSize(10000)
        .expireAfterWrite(5, TimeUnit.MINUTES)
        .recordStats()
        .build();

    @Autowired
    private StringRedisTemplate redisTemplate;

    // L2 + L3
    public Product getProduct(Long id) {
        // 1. 查本地缓存
        Product product = localCache.getIfPresent(id);
        if (product != null) return product;

        // 2. 查 Redis
        String json = redisTemplate.opsForValue().get("product:" + id);
        if (json != null) {
            product = JSON.parseObject(json, Product.class);
            localCache.put(id, product);
            return product;
        }

        // 3. 查数据库（回源）
        product = productMapper.selectById(id);
        if (product != null) {
            redisTemplate.opsForValue()
                .set("product:" + id, JSON.toJSONString(product), 1, TimeUnit.HOURS);
            localCache.put(id, product);
        }
        return product;
    }

    // 缓存失效广播（更新时由 MQ 触发）
    @RabbitListener(queues = "cache.invalidate")
    public void onCacheInvalidate(Long productId) {
        localCache.invalidate(productId);          // 清理本地缓存
        redisTemplate.delete("product:" + productId); // 清理 Redis
    }
}
```

### Result（效果）

| 指标 | 优化前 | 优化后 | 提升 |
|------|--------|--------|------|
| P99 响应时间 | 220ms | 8ms | 27x |
| 缓存命中率 | 0% | 98.5% | — |
| 数据库 QPS | 50000 | 750 | 98.5% 降低 |
| Redis QPS | 0 | 50000 | 正常 |

---

## 二、秒杀库存预扣：分布式锁 + Lua 原子扣减

### Situation（场景）

双 11 秒杀活动，5000 件商品 1 秒内被抢光。多个微服务实例并发扣减库存，需要保证不超卖。

### Task（任务）

设计一个高并发、高可用的秒杀库存扣减方案：
- 支持 10 万+ QPS 的瞬时并发
- 0 超卖，0 少卖
- 库存 Redis 预扣 + MQ 异步落库，保证最终一致性

### Action（方案）

**流程**：Redis 预扣 → Redisson 分布式锁 → Lua 原子扣减 → MQ 异步落库 → 异常回补

```java
@Service
@Slf4j
public class SeckillService {
    @Autowired
    private RedissonClient redissonClient;
    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private RocketMQProducer mqProducer;

    public boolean seckill(Long activityId, Long skuId, Long userId) {
        // 1. 幂等校验
        String idempotentKey = "seckill:idempotent:" + activityId + ":" + skuId + ":" + userId;
        if (Boolean.FALSE.equals(redisTemplate.opsForValue()
                .setIfAbsent(idempotentKey, "1", 30, TimeUnit.SECONDS))) {
            log.warn("重复下单: {}", userId);
            return false;
        }

        // 2. 分布式锁
        RLock lock = redissonClient.getLock("seckill:lock:" + activityId + ":" + skuId);
        lock.lock();
        try {
            // 3. Lua 脚本原子扣减
            String lua = "local stock = redis.call('get', KEYS[1]) " +
                "if not stock or tonumber(stock) < 1 then return -1 end " +
                "redis.call('decr', KEYS[1]) " +
                "return tonumber(stock) - 1";

            Long remain = redisTemplate.execute(
                new DefaultRedisScript<>(lua, Long.class),
                Collections.singletonList("seckill:stock:" + activityId + ":" + skuId));

            if (remain == null || remain < 0) {
                return false;  // 无库存
            }

            // 4. 异步落库（MQ）
            SeckillOrderMessage msg = new SeckillOrderMessage(activityId, skuId, userId, remain);
            mqProducer.sendAsync("seckill_order", msg);

            log.info("秒杀成功: userId={}, remain={}", userId, remain);
            return true;
        } finally {
            lock.unlock();
        }
    }
}
```

### Result（效果）

| 指标 | 值 |
|------|-----|
| 单机 QPS（Redis 扣减） | 80000+ |
| 超卖率 | 0% |
| 最终一致性时延 | < 500ms |
| 扣减成功率 | 99.99% |

---

## 三、分布式 Session：Spring Session + Redis

### Situation（场景）

AI 商城采用微服务架构（用户服务、订单服务、商品服务），用户登录后，Session 需要跨服务共享。传统的 Tomcat Session 只在一个 JVM 内有效。

### Task（任务）

实现分布式 Session，让所有微服务共享同一个用户登录态，同时支持 Session 自动过期和续期。

### Action（方案）

使用 Spring Session + Redis，实现无侵入的分布式 Session 方案。

**依赖**：
```xml
<dependency>
    <groupId>org.springframework.session</groupId>
    <artifactId>spring-session-data-redis</artifactId>
</dependency>
```

**配置**：
```yaml
spring:
  session:
    store-type: redis          # Session 存储在 Redis
    timeout: 30m               # Session 过期时间
    redis:
      namespace: mall:session  # key 前缀
      flush-mode: on-save      # 立即写入
```

**无代码侵入**：只需添加依赖和配置，原有的 `HttpSession` API 保持不变：

```java
@RestController
@RequestMapping("/api/user")
public class UserController {
    @PostMapping("/login")
    public Result login(@RequestBody LoginRequest req, HttpSession session) {
        User user = userService.login(req.getUsername(), req.getPassword());
        if (user != null) {
            session.setAttribute("userId", user.getId());
            session.setAttribute("username", user.getUsername());
            return Result.success(user);
        }
        return Result.error("登录失败");
    }

    @GetMapping("/current")
    public Result current(HttpSession session) {
        Long userId = (Long) session.getAttribute("userId");
        if (userId == null) {
            return Result.error("未登录");
        }
        return Result.success(userService.getUser(userId));
    }
}
```

**Redis 中存储的结构**：
```
mall:session:session-id -> Hash
  ├── creationTime: 1724313600000
  ├── lastAccessedTime: 1724317200000
  ├── maxInactiveInterval: 1800
  ├── sessionAttr:userId: 1001
  └── sessionAttr:username: "张三"
```

### Result（效果）

- 用户登录一次，所有微服务共享 Session
- Session 自动过期，无需手动清理
- 支持 Session 平滑迁移（重启服务不丢失）
- 通过 Redis TTL 自动处理过期 Session

---

## 四、接口幂等性 Token：防重复提交

### Situation（场景）

用户在商城下单时，由于网络抖动或前端重复点击，同一个请求被发送多次。如果后端不进行幂等处理，会导致重复下单、重复扣款。

### Task（任务）

实现接口幂等性，保证同一个请求 ID 在 30 秒内只能被处理一次。

### Action（方案）

使用 Redis `SETNX` 实现幂等性 Token 机制。

```java
@Service
@Slf4j
public class IdempotentService {
    @Autowired
    private StringRedisTemplate redisTemplate;

    /**
     * 生成幂等 Token（前端请求时先获取 Token）
     */
    public String generateToken() {
        String token = UUID.randomUUID().toString();
        // Token 存入 Redis，30 秒内有效
        redisTemplate.opsForValue()
            .set("idempotent:token:" + token, "1", 30, TimeUnit.SECONDS);
        return token;
    }

    /**
     * 校验并消费 Token
     * @param token 幂等 Token
     * @return true=首次请求，false=重复请求
     */
    public boolean checkAndConsume(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        // Lua 脚本：原子检查 + 删除
        String lua = "return redis.call('del', KEYS[1])";
        Long result = redisTemplate.execute(
            new DefaultRedisScript<>(lua, Long.class),
            Collections.singletonList("idempotent:token:" + token));
        // 删除成功说明 token 存在且未被消费（首次请求）
        return result != null && result > 0;
    }
}
```

**前端使用**：
```java
@RestController
@RequestMapping("/api/order")
public class OrderController {
    @Autowired
    private IdempotentService idempotentService;
    @Autowired
    private OrderService orderService;

    @PostMapping("/create")
    public Result createOrder(@RequestBody @Valid OrderRequest req) {
        // 幂等校验
        if (!idempotentService.checkAndConsume(req.getIdempotentToken())) {
            return Result.error("请勿重复提交");
        }
        // 创建订单
        Long orderId = orderService.createOrder(req);
        return Result.success(orderId);
    }
}
```

**另一种方式：业务 key 幂等**
不需要前端传 Token，后端用业务唯一标识做幂等：

```java
public boolean placeOrder(Long userId, Long skuId, Integer quantity) {
    // 业务幂等 key：用户 + 商品 + 活动
    String idempotentKey = "order:idempotent:" + userId + ":" + skuId;
    Boolean acquired = redisTemplate.opsForValue()
        .setIfAbsent(idempotentKey, "1", 30, TimeUnit.SECONDS);
    if (!Boolean.TRUE.equals(acquired)) {
        log.warn("重复下单: userId={}, skuId={}", userId, skuId);
        return false;
    }
    // 继续处理订单...
}
```

### Result（效果）

- 100% 拦截重复请求
- 30 秒自动过期，不影响正常后续操作
- 业务 key 幂等方案无需前端配合，改造成本更低

---

## 五、总结

| 场景 | 技术 | Redis 角色 | 核心能力 |
|------|------|-----------|---------|
| 商品缓存 | Caffeine + Redis 多级缓存 | 分布式缓存层 | 高性能读、防雪崩 |
| 秒杀库存 | Redisson + Lua 脚本 | 原子计数器 | 高并发扣减、0 超卖 |
| 分布式 Session | Spring Session + Redis | 共享存储 | 跨服务登录态、自动过期 |
| 接口幂等 | SETNX + Lua 删除 | 分布式锁 | 防重复提交、最终一致性 |

> 进入独立小项目：用 Redis ZSet 实现一个排行榜系统。