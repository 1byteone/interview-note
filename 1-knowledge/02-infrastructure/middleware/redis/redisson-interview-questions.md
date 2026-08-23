# Redisson 面试题大全

## 📚 知识体系

```
Redisson 核心功能
├── 分布式锁
│   ├── 可重入锁 (RLock)
│   ├── 公平锁 (FairLock)
│   ├── 读写锁 (ReadWriteLock)
│   ├── 联锁 (MultiLock)
│   ├── 红锁 (RedLock)
│   └── 信号量 (Semaphore)
├── 分布式对象
│   ├── RBucket
│   ├── RMap
│   ├── RSet
│   ├── RList
│   ├── RQueue
│   └── RAtomicLong
├── 分布式集合
├── 分布式服务
│   ├── 延迟队列 (RDelayedQueue)
│   ├── 限流器 (RRateLimiter)
│   ├── 分布式锁服务
│   └── 分布式计数
└── 高级特性
    ├── WatchDog 看门狗
    ├── 锁自动续期
    ├── 锁重入
    └── 公平锁队列
```

---

## 🎯 Level 1：基础题

### 1. Redisson 是什么？为什么用 Redisson 而不用原生 Redis 实现锁？
**答案**：
Redisson 是 Redis 官方推荐的 Java 客户端，提供开箱即用的分布式锁、分布式对象、分布式服务等。

**与原生 SETNX 锁对比**：

| 特性 | 原生 SETNX 实现 | Redisson |
|------|----------------|----------|
| 可重入 | 需自己实现 | 内置支持 |
| 过期时间自动续期 | 不支持（需手动设置） | WatchDog 自动续期 |
| 锁释放 | 需 Lua 脚本保证原子性 | 内置 |
| 公平锁 | 需自己实现 | 内置 |
| 读写锁 | 需自己实现 | 内置 |
| 复杂度 | 高（容易出错） | 低（开箱即用） |

### 2. Redisson 分布式锁的基本用法？
**答案**：
```java
// 添加依赖
// <dependency>org.redisson:redisson-spring-boot-starter</dependency>

@Configuration
public class RedissonConfig {
    @Bean
    public RedissonClient redissonClient() {
        Config config = new Config();
        config.useSingleServer()
            .setAddress("redis://localhost:6379")
            .setConnectionPoolSize(50)
            .setConnectionMinimumIdleSize(10);
        return Redisson.create(config);
    }
}
```

**加锁**：
```java
RLock lock = redissonClient.getLock("order:seckill:1001");

// 尝试加锁
boolean locked = lock.tryLock(5, 30, TimeUnit.SECONDS);
if (locked) {
    try {
        // 业务逻辑
    } finally {
        lock.unlock();
    }
}
```

---

## 🎯 Level 2：进阶题

### 3. Redisson 锁的 WatchDog 机制是什么？
**答案**：
WatchDog（看门狗）是 Redisson 自动续期的机制，防止业务执行时间超过锁过期时间导致锁提前失效。

**工作原理**：
```text
获取锁成功
    ↓
启动 WatchDog 定时任务（每 10s 一次）
    ↓
检查锁剩余有效期
    ↓
剩余 < 30s → 自动续期到 30s
    ↓
业务执行完毕 → 释放锁 → 停止 WatchDog
```

**代码层面**：
```java
// 默认 leaseTime 为 -1 时启用 WatchDog
// 默认锁有效期 30s，每 10s 自动续期

RLock lock = redissonClient.getLock("myLock");
// 注意：不传 leaseTime 才会启用 WatchDog
lock.lock(10, TimeUnit.SECONDS);  // 这里 10s 是等待时间，不是租期
// 或者
lock.lock();  // 使用 WatchDog 自动续期
```

**WatchDog 设置**：
```java
config.setLockWatchdogTimeout(30000);  // 默认 30s
```

---

## 🎯 Level 3：高级题

### 4. Redisson 分布式锁的实现原理是什么？
**答案**：

**底层使用 Lua 脚本保证原子性**：

```lua
-- 加锁（可重入）
if (redis.call('exists', KEYS[1]) == 0) then
    redis.call('hset', KEYS[1], ARGV[2], 1)  -- 存线程标识
    redis.call('pexpire', KEYS[1], ARGV[1])    -- 设置过期时间
    return nil
end
if (redis.call('hexists', KEYS[1], ARGV[2]) == 1) then
    redis.call('hincrby', KEYS[1], ARGV[2], 1)  -- 重入次数 +1
    redis.call('pexpire', KEYS[1], ARGV[1])
    return nil
end
return redis.call('pttl', KEYS[1])
```

```lua
-- 解锁
if (redis.call('hexists', KEYS[1], ARGV[3]) == 0) then
    return nil
end
local counter = redis.call('hincrby', KEYS[1], ARGV[3], -1)
if (counter > 0) then
    redis.call('pexpire', KEYS[1], ARGV[2])
    return 0
else
    redis.call('del', KEYS[1])
    redis.call('publish', KEYS[2], ARGV[1])
    return 1
end
```

**核心设计**：
1. **Hash 结构**：key = 锁名，field = 线程 ID，value = 重入次数
2. **可重入**：同一线程重复加锁加计数
3. **唯一标识**：线程 ID 防止误删别人的锁
4. **事件通知**：解锁时发布消息，唤醒等待线程
5. **WatchDog**：自动续期解决过期问题

---

## 🎯 Level 4：专家题

### 5. Redisson 公平锁、读写锁、联锁、红锁有什么区别？
**答案**：

| 锁类型 | 说明 | 使用场景 |
|--------|------|----------|
| **可重入锁 RLock** | 同一线程可重复获取 | 默认/常规场景 |
| **公平锁 FairLock** | FIFO 排队获取 | 需要公平排队 |
| **读写锁 RReadWriteLock** | 读读共享、写写互斥 | 读写比例高 |
| **联锁 MultiLock** | 多个锁同时获取 | 跨资源锁定 |
| **红锁 RedLock** | 多 Redis 节点锁 | 多节点容灾 |

**读写锁示例**：
```java
RReadWriteLock lock = redissonClient.getReadWriteLock("product:cache:1001");
RLock readLock = lock.readLock();
RLock writeLock = lock.writeLock();

// 读操作
readLock.lock();
try {
    return productCache.get(1001L);
} finally {
    readLock.unlock();
}

// 写操作
writeLock.lock();
try {
    productCache.update(1001L, data);
} finally {
    writeLock.unlock();
}
```

---

## 📖 学习资源

### 推荐项目
- [Redisson 官方文档](https://github.com/redisson/redisson)
- [Redisson 示例](https://github.com/redisson/redisson-examples)

### 最佳实践
1. 锁粒度要小（按业务 key 加锁，不全局加锁）
2. 设置合适的等待时间和租期
3. 使用 WatchDog 自动续期（或评估 n 业务时间设置租期）
4. 幂等性兜底（锁只是手段，最终一致）
5. 监控分布式锁的性能与死锁情况