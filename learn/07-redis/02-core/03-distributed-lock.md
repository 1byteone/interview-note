# 分布式锁 — SETNX → Redisson → RedLock · Lua 原子性 · Watch Dog

> 等级：🎯 面试进阶
> 目标：理解分布式锁的演进过程，掌握 Redisson 的高级特性，并在 AI 商城秒杀场景中实战。

---

## 一、为什么需要分布式锁？

在单机环境下，用 `synchronized` 或 `ReentrantLock` 就能保证线程安全。但在微服务架构中，多个服务实例运行在不同的 JVM 中，本地锁不再有效——需要**分布式锁**来协调跨进程的资源访问。

**AI 商城典型场景**：秒杀活动中，多个服务实例同时扣减同一件商品的库存，必须保证：
1. **互斥性**：同一时刻只有一个实例能扣减
2. **原子性**：扣减过程不可中断
3. **容错性**：持有锁的实例崩溃后，锁能自动释放

---

## 二、分布式锁演进：从原始到成熟

### 2.1 第一版：SETNX + EXPIRE（原始方案）

```java
// 加锁
Boolean locked = redisTemplate.opsForValue()
    .setIfAbsent("lock:seckill:1001", "1");
if (Boolean.TRUE.equals(locked)) {
    redisTemplate.expire("lock:seckill:1001", 30, TimeUnit.SECONDS);
    try {
        // 业务逻辑
    } finally {
        redisTemplate.delete("lock:seckill:1001");
    }
}
```

**问题**：
- `SETNX` 和 `EXPIRE` 不是原子操作——如果 `SETNX` 成功但 `EXPIRE` 前进程崩溃，锁永不过期
- 不能重入——同一线程再次加锁会死锁
- 释放锁时没有校验持有者——可能释放别人的锁

### 2.2 第二版：SET 原子命令 + UUID 校验

```java
// 加锁（原子操作：SETNX + EXPIRE 合并）
String lockValue = UUID.randomUUID().toString();
Boolean locked = redisTemplate.opsForValue()
    .setIfAbsent("lock:seckill:1001", lockValue, 30, TimeUnit.SECONDS);

if (Boolean.TRUE.equals(locked)) {
    try {
        // 业务逻辑
    } finally {
        // 释放锁：用 Lua 脚本保证原子性
        String script = "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";
        redisTemplate.execute(new DefaultRedisScript<>(script, Long.class),
            Collections.singletonList("lock:seckill:1001"), lockValue);
    }
}
```

**改进**：
- `SET NX EX 30` 原子命令解决加锁 + 过期原子性问题
- UUID 防止误删别人的锁
- Lua 脚本保证释放锁的原子性

**剩余问题**：
- 锁过期时间难确定：业务执行时间超过锁过期时间，锁自动释放，其他线程进入
- 不可重入
- 非公平

### 2.3 第三版：Redisson 分布式锁（生产推荐）

```java
@Autowired
private RedissonClient redissonClient;

public void seckill(Long activityId, Long skuId) {
    RLock lock = redissonClient.getLock("seckill:lock:" + activityId + ":" + skuId);
    
    // 加锁（默认 30 秒，Watch Dog 自动续期）
    lock.lock();
    try {
        // 1. 预扣库存
        Long remain = redisTemplate.opsForValue()
            .increment("seckill:stock:" + skuId, -1);
        if (remain >= 0) {
            // 2. 发送 MQ 异步落库
            orderService.createOrder(activityId, skuId);
        } else {
            // 3. 回补库存
            redisTemplate.opsForValue()
                .increment("seckill:stock:" + skuId, 1);
        }
    } finally {
        lock.unlock();
    }
}
```

**Redisson 解决了什么**：
- **Watch Dog 自动续期**：锁默认 30s 过期，每 10s 自动续期，业务未完成锁不会提前释放
- **可重入**：同一线程可多次加锁（用 Hash 结构记录重入次数）
- **公平锁 / 非公平锁**：按需选择
- **Lua 脚本保证原子性**：所有操作都是原子执行

---

## 三、Lua 脚本：分布式锁的原子性基石

### 3.1 Redisson 底层加锁 Lua 脚本

```lua
-- KEYS[1] = 锁的 key
-- ARGV[1] = 锁过期时间（毫秒）
-- ARGV[2] = 客户端唯一标识（UUID:threadId）

-- 锁不存在，直接加锁
if redis.call('exists', KEYS[1]) == 0 then
    redis.call('hincrby', KEYS[1], ARGV[2], 1)
    redis.call('pexpire', KEYS[1], ARGV[1])
    return nil
end

-- 锁存在且是当前线程持有，重入计数 +1
if redis.call('hexists', KEYS[1], ARGV[2]) == 1 then
    redis.call('hincrby', KEYS[1], ARGV[2], 1)
    redis.call('pexpire', KEYS[1], ARGV[1])
    return nil
end

-- 锁被其他线程持有，返回剩余过期时间
return redis.call('pttl', KEYS[1])
```

### 3.2 Redisson 释放锁 Lua 脚本

```lua
-- 锁不存在，直接返回
if redis.call('hexists', KEYS[1], ARGV[2]) == 0 then
    return nil
end

-- 重入次数 -1
local counter = redis.call('hincrby', KEYS[1], ARGV[2], -1)

-- 如果还有重入次数，刷新过期时间
if counter > 0 then
    redis.call('pexpire', KEYS[1], ARGV[1])
    return 0
else
    -- 重入次数归零，删除锁
    redis.call('del', KEYS[1])
    return 1
end
```

### 3.3 Lua 脚本为什么是原子的？

Redis 使用**单线程**执行命令，Lua 脚本在执行期间不会被其他命令打断。因此，脚本内的所有操作要么全部成功，要么全部失败——天然原子性。

---

## 四、Watch Dog 自动续期机制

### 4.1 工作原理

```
加锁成功（默认 30s）
    │
    ├── 业务 10 秒执行完 → 解锁 → Watch Dog 无事可做
    │
    └── 业务超过 10 秒未完成 → Watch Dog 检查锁是否还在
        │
        ├── 锁还在 → 续期 30 秒
        └── 锁已释放 → 停止续期
```

### 4.2 配置

```java
// 自定义锁过期时间（默认 30 秒）
RLock lock = redissonClient.getLock("myLock");
lock.lock(60, TimeUnit.SECONDS);  // 指定 60 秒，Watch Dog 不再续期

// 或
lock.lock();  // 默认 30 秒，Watch Dog 自动续期
```

### 4.3 面试要点

> **Watch Dog 会不会导致锁永远不释放？**
> 不会。如果持有锁的客户端崩溃，Watch Dog 线程也会停止，锁在过期后自动释放。Redis 的 `pexpire` 命令是最终兜底。

> **lock(10, seconds) 和 lock() 的区别？**
> `lock(10, seconds)` 指定 10 秒后自动释放，Watch Dog 不续期；`lock()` 使用默认 30 秒，Watch Dog 自动续期。推荐用 `lock()` 让 Watch Dog 兜底。

---

## 五、Redisson 高级锁类型

### 5.1 公平锁（Fair Lock）

```java
// 按请求顺序获取锁，避免饥饿
RLock fairLock = redissonClient.getFairLock("fairLock");
fairLock.lock();
```

**原理**：内部使用 Redis List 保存等待队列，保证先来先得。

### 5.2 读写锁（ReadWriteLock）

```java
RReadWriteLock rwLock = redissonClient.getReadWriteLock("product:1001:lock");

// 读锁（共享锁）：多个读线程可以同时持有
RLock readLock = rwLock.readLock();
readLock.lock();

// 写锁（排他锁）：写线程独占，读锁和写锁都不能同时持有
RLock writeLock = rwLock.writeLock();
writeLock.lock();
```

**适用场景**：商品详情页——读多写少。读锁不阻塞读，写锁阻塞所有读写。

### 5.3 信号量（Semaphore）

```java
// 限流：控制同时访问的线程数
RSemaphore semaphore = redissonClient.getSemaphore("semaphore:seckill");
semaphore.trySetPermits(100);  // 允许 100 个并发

// 获取许可
semaphore.acquire();
try {
    // 业务逻辑
} finally {
    semaphore.release();
}
```

### 5.4 闭锁（CountDownLatch）

```java
RCountDownLatch latch = redissonClient.getCountDownLatch("latch:order");
latch.trySetCount(5);  // 等待 5 个任务完成

// 每个任务完成时调用
latch.countDown();

// 等待所有任务完成
latch.await();
```

---

## 六、RedLock 算法（跨节点的高可用锁）

### 6.1 问题场景

在 Redis 主从架构中，如果客户端在 master 上加了锁，但 master 宕机，slave 尚未同步该锁数据就升为 master——另一个客户端可以在新 master 上加锁成功，导致**锁失效**。

### 6.2 RedLock 方案

**核心思想**：锁不只在单节点上，而是同时在 N 个独立的 Redis 节点（通常 5 个）上加锁，**大多数节点加锁成功**才算获取到锁。

```java
// Redisson 中启用 RedLock
Config config = new Config();
config.useSentinelServers()
    .addSentinelAddress("redis://node1:6379", "redis://node2:6379", "redis://node3:6379")
    .setMasterName("mymaster");

RedissonClient redisson = Redisson.create(config);
RLock lock1 = redisson.getLock("myLock");
RLock lock2 = redisson2.getLock("myLock");
RLock lock3 = redisson3.getLock("myLock");

RedissonRedLock redLock = new RedissonRedLock(lock1, lock2, lock3);
redLock.lock();
try {
    // 业务逻辑
} finally {
    redLock.unlock();
}
```

### 6.3 RedLock 的争议

**Martin Kleppmann 的批评**：
- 依赖时钟同步，时钟跳跃会导致锁失效
- 性能开销大（需要 N 次网络请求）
- 实际生产中很少需要 RedLock

**蚂蚁金服/美团的实践**：
- 用 Redis 哨兵模式 + 合理的锁过期时间 + 业务兜底（乐观锁、版本号）就足够了
- 真正需要 RedLock 的场景极少

---

## 七、实战：AI 商城秒杀库存扣减

### 7.1 完整实现

```java
@Service
@Slf4j
public class SeckillService {
    @Autowired
    private RedissonClient redissonClient;
    @Autowired
    private RedisTemplate<String, String> redisTemplate;
    @Autowired
    private OrderService orderService;

    /**
     * 秒杀下单
     * @param activityId 活动 ID
     * @param skuId      商品 SKU ID
     * @param userId     用户 ID
     * @return true=成功, false=失败
     */
    public boolean placeOrder(Long activityId, Long skuId, Long userId) {
        // 1. 幂等校验：防止重复下单
        String idempotentKey = "order:idempotent:" + activityId + ":" + userId + ":" + skuId;
        Boolean isNew = redisTemplate.opsForValue()
            .setIfAbsent(idempotentKey, "1", 30, TimeUnit.SECONDS);
        if (!Boolean.TRUE.equals(isNew)) {
            log.warn("重复下单: activityId={}, skuId={}, userId={}", activityId, skuId, userId);
            return false;
        }

        // 2. 获取分布式锁
        RLock lock = redissonClient.getLock("seckill:stock:lock:" + activityId + ":" + skuId);
        lock.lock();
        try {
            // 3. 检查库存是否充足
            String stockKey = "seckill:stock:" + activityId + ":" + skuId;
            String stockStr = redisTemplate.opsForValue().get(stockKey);
            if (stockStr == null) {
                log.error("库存未初始化: {}", stockKey);
                return false;
            }
            int stock = Integer.parseInt(stockStr);
            if (stock <= 0) {
                log.warn("库存不足: skuId={}", skuId);
                return false;
            }

            // 4. Lua 脚本原子扣减
            String luaScript = "local stock = redis.call('get', KEYS[1]) " +
                "if not stock or tonumber(stock) <= 0 then return -1 end " +
                "redis.call('decr', KEYS[1]) " +
                "return tonumber(stock) - 1";
            Long remain = redisTemplate.execute(
                new DefaultRedisScript<>(luaScript, Long.class),
                Collections.singletonList(stockKey)
            );

            if (remain == null || remain < 0) {
                return false;
            }

            // 5. 异步落库（MQ）
            OrderMessage msg = new OrderMessage(activityId, skuId, userId, remain);
            orderService.asyncCreateOrder(msg);

            log.info("秒杀成功: activityId={}, skuId={}, userId={}, remain={}",
                activityId, skuId, userId, remain);
            return true;
        } finally {
            lock.unlock();
        }
    }
}
```

### 7.2 核心流程

```
用户请求 → 幂等校验 → 分布式锁 → Lua 原子扣减 → MQ 异步落库 → 返回结果
    ↓          ↓            ↓            ↓              ↓
 防重复下单  SETNX 30s  Redisson     原子性操作     订单持久化
```

---

## 八、面试速记

| 阶段 | 方案 | 关键问题 |
|------|------|---------|
| 原始版 | SETNX + EXPIRE | 非原子，死锁风险 |
| 改进版 | SET NX EX + UUID 校验 | 过期时间不好定，不可重入 |
| 生产版 | Redisson | Watch Dog 自动续期，可重入，Lua 原子性 |
| 高可用版 | RedLock | 多节点 + 大多数原则，时钟依赖争议 |

> 进入进阶篇：Redis 集群与哨兵——高可用架构的基石。