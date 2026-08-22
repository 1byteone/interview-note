# 缓存模式 — 穿透 · 击穿 · 雪崩 · 一致性 · 多级缓存

> 等级：👶 入门 → 🎯 面试进阶
> 目标：透彻理解缓存三大问题的原因与解决方案，掌握缓存一致性方案，并了解多级缓存架构。

---

## 一、缓存穿透

### 1.1 定义

查询一个**根本不存在的数据**，缓存和数据库中都查不到，每次请求都直接打到数据库。

**恶意攻击示例**：攻击者用大量不存在的商品 ID（-1、-2、99999999）请求商品详情，缓存永远不命中，数据库被打爆。

### 1.2 解决方案

#### 方案一：缓存空对象（布隆过滤器的兜底方案）

```java
public Product getProduct(Long id) {
    String key = "product:" + id;
    Product product = redisTemplate.opsForValue().get(key);
    if (product != null) {
        return product;
    }
    
    // 从数据库查询
    product = productMapper.selectById(id);
    if (product == null) {
        // 缓存空对象，过期时间短（30-60秒）
        redisTemplate.opsForValue().set(key, new Product(), 60, TimeUnit.SECONDS);
        return null;
    }
    
    redisTemplate.opsForValue().set(key, product, 3600, TimeUnit.SECONDS);
    return product;
}
```

**优点**：简单易实现。**缺点**：缓存大量空对象浪费内存，且无法完全防御恶意攻击（换不同的 key）。

#### 方案二：布隆过滤器（Bloom Filter）—— 推荐

```java
@Component
public class BloomFilterService {
    private final RedisTemplate<String, String> redisTemplate;
    private static final String FILTER_KEY = "bloom:product";
    private static final int EXPECTED_SIZE = 1000000;  // 100万
    private static final double FPR = 0.01;            // 1% 误判率

    public void init() {
        // 启动时加载所有商品 ID 到布隆过滤器
        List<Long> allIds = productMapper.selectAllIds();
        allIds.forEach(id -> redisTemplate.opsForValue()
            .setBit(FILTER_KEY, id % (EXPECTED_SIZE * 10), true));
    }

    public boolean mayExist(Long id) {
        return redisTemplate.opsForValue().getBit(FILTER_KEY, id % (EXPECTED_SIZE * 10));
    }
}
```

**布隆过滤器特性**：
- **说"不存在"就一定不存在**（100% 准确）
- **说"存在"可能不存在**（有误判率，但可以通过调参降低）
- 不可删除元素（除非用 Counting Bloom Filter）

**生产推荐**：空对象 + 布隆过滤器双重防御。第一层过滤器拦截绝大多数非法 key，第二层空对象缓存兜底剩余误判。

---

## 二、缓存击穿

### 2.1 定义

一个**热点 key** 在缓存过期的一瞬间，大量并发请求同时涌入，全部打到数据库。

**典型场景**：秒杀商品详情页、微博热搜第一条、双 11 爆款详情。

### 2.2 解决方案

#### 方案一：互斥锁（Mutex Lock）

```java
public Product getProduct(Long id) {
    String key = "product:" + id;
    Product product = redisTemplate.opsForValue().get(key);
    if (product != null) return product;

    // 缓存未命中，尝试加锁
    String lockKey = "lock:product:" + id;
    String lockValue = UUID.randomUUID().toString();
    Boolean locked = redisTemplate.opsForValue()
        .setIfAbsent(lockKey, lockValue, 10, TimeUnit.SECONDS);

    if (Boolean.TRUE.equals(locked)) {
        try {
            // 再次检查缓存（double-check，防止锁等待期间其他线程已重建）
            product = redisTemplate.opsForValue().get(key);
            if (product != null) return product;

            // 查询数据库
            product = productMapper.selectById(id);
            if (product != null) {
                redisTemplate.opsForValue().set(key, product, 3600, TimeUnit.SECONDS);
            }
            return product;
        } finally {
            // 释放锁（只释放自己的锁）
            String script = "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";
            redisTemplate.execute(new DefaultRedisScript<>(script, Long.class), 
                Collections.singletonList(lockKey), lockValue);
        }
    } else {
        // 没有获取到锁，等待重试（自旋）
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return getProduct(id);  // 递归重试
    }
}
```

**优点**：保证只有一个线程查数据库。**缺点**：有锁的开销，并发降级为串行，但缓存重建后立即恢复。

#### 方案二：逻辑过期（主动刷新）

不给缓存设置 `EXPIRE`，而是在 value 中存一个逻辑过期时间，后台异步线程负责刷新。

```java
@Data
public class CacheProduct {
    private Product product;
    private LocalDateTime expireTime;  // 逻辑过期时间
}

public Product getProduct(Long id) {
    String key = "product:" + id;
    CacheProduct cache = redisTemplate.opsForValue().get(key);
    if (cache == null) return null;

    // 检查逻辑过期时间
    if (cache.getExpireTime().isBefore(LocalDateTime.now())) {
        // 异步重建缓存
        threadPool.submit(() -> {
            String lockKey = "lock:product:" + id;
            if (Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(lockKey, "1", 10, TimeUnit.SECONDS))) {
                try {
                    Product product = productMapper.selectById(id);
                    if (product != null) {
                        CacheProduct newCache = new CacheProduct();
                        newCache.setProduct(product);
                        newCache.setExpireTime(LocalDateTime.now().plusSeconds(3600));
                        redisTemplate.opsForValue().set(key, newCache);
                    }
                } finally {
                    redisTemplate.delete(lockKey);
                }
            }
        });
    }
    return cache.getProduct();  // 返回旧数据
}
```

**优点**：无阻塞，用户始终能拿到数据。**缺点**：有短暂的数据不一致窗口。

---

## 三、缓存雪崩

### 3.1 定义

大量缓存**集中在同一时间过期**，或者 Redis 实例宕机，导致所有请求直接打到数据库。

### 3.2 解决方案

| 方案 | 说明 | 示例 |
|------|------|------|
| 随机过期时间 | 在基础过期时间上增加随机值 | `set + 3600 + Math.random() * 600` |
| 多级缓存 | 本地缓存 + Redis 双层保护 | 见下文"多级缓存" |
| 高可用 | Redis 主从/哨兵/集群，防止单点 | 见 03-advanced |
| 限流降级 | 请求量过大时直接返回默认值 | Sentinel / Hystrix |
| 预热 | 提前加载热点数据，设置不同的过期时间 | 启动时批量加载 |

**代码示例：随机过期时间**

```java
// 基础过期时间 1 小时，增加 0~10 分钟的随机值
int baseExpire = 3600;
int randomExpire = baseExpire + new Random().nextInt(600);
redisTemplate.opsForValue().set(key, value, randomExpire, TimeUnit.SECONDS);
```

---

## 四、缓存一致性

### 4.1 问题本质

缓存和数据库是两个独立的存储系统，对同一个数据做写操作时，必然会出现暂时的不一致。

### 4.2 常见方案对比

| 方案 | 说明 | 一致性 | 复杂度 | 推荐度 |
|------|------|--------|--------|--------|
| **Cache-Aside + 删缓存** | 先更新 DB，再删缓存 | 最终一致 | 低 | 最推荐 |
| 延迟双删 | 删缓存 → 更新 DB → 等待 → 再删缓存 | 最终一致 | 中 | 可用 |
| 读写锁 | 读加共享锁，写加排他锁 | 强一致 | 高 | 不推荐 |
| Canal 订阅 binlog | 监听 MySQL binlog 变更，异步刷新缓存 | 最终一致 | 高 | 推荐 |

### 4.3 Cache-Aside + 删除缓存（标准方案）

```java
@Transactional
public void updateProduct(Product product) {
    // 1. 更新数据库
    productMapper.updateById(product);
    
    // 2. 删除缓存
    redisTemplate.delete("product:" + product.getId());
}
```

**为什么是"删缓存"而不是"更新缓存"？**
- 并发写时，更新缓存会存在"旧值覆盖新值"的问题
- 删缓存让下一次读时重建，天然避免了写写冲突
- 删缓存比更新缓存更省一次 Redis 写操作

### 4.4 延时双删（解决读写并发不一致）

```java
@Transactional
public void updateProduct(Product product) {
    // 1. 先删除缓存（让旧数据失效）
    redisTemplate.delete("product:" + product.getId());
    
    // 2. 更新数据库
    productMapper.updateById(product);
    
    // 3. 延时再删缓存（确保读请求重建的过期数据被清除）
    executor.schedule(() -> {
        redisTemplate.delete("product:" + product.getId());
    }, 500, TimeUnit.MILLISECONDS);
}
```

**为什么需要延时？** 一个可能的并发场景：
1. 线程 A 删除了缓存
2. 线程 B 读不到缓存，查数据库得到旧数据，写回缓存
3. 线程 A 更新数据库，此时缓存中仍是旧数据

### 4.5 Canal 订阅 binlog（高级方案）

```
MySQL binlog → Canal → MQ → 消费者 → 更新/删除 Redis
```

**优点**：
- 与业务代码完全解耦，无需在业务代码中写缓存处理逻辑
- 保证最终一致性

**缺点**：
- 引入 Canal + MQ 两条中间件，运维成本高
- 有一定延迟（毫秒到秒级）

---

## 五、多级缓存（本地缓存 + Redis）

### 5.1 架构图

```
┌─────────┐     ┌──────────┐     ┌─────────┐
│ 客户端   │────▶│ Caffeine  │────▶│  Redis   │────▶│  MySQL  │
│ (请求)   │     │ (本地缓存) │     │ (分布式) │     │ (持久化) │
└─────────┘     └──────────┘     └─────────┘     └─────────┘
                   L1: 1ms         L2: 5ms          L3: 50ms
```

### 5.2 实现

```java
@Component
public class MultiLevelCache {
    @Autowired
    private RedisTemplate<String, String> redisTemplate;
    
    // L1 本地缓存（Caffeine）
    private final Cache<Long, Product> localCache = Caffeine.newBuilder()
        .maximumSize(10000)          // 最大 1 万个商品
        .expireAfterWrite(5, TimeUnit.MINUTES)  // 5 分钟过期
        .recordStats()               // 记录命中率
        .build();

    public Product getProduct(Long id) {
        // 1. 查本地缓存
        Product product = localCache.getIfPresent(id);
        if (product != null) return product;

        // 2. 查 Redis
        String json = redisTemplate.opsForValue().get("product:" + id);
        if (json != null) {
            product = JSON.parseObject(json, Product.class);
            localCache.put(id, product);  // 回填本地缓存
            return product;
        }

        // 3. 查数据库
        product = productMapper.selectById(id);
        if (product != null) {
            redisTemplate.opsForValue().set("product:" + id, JSON.toJSONString(product), 1, TimeUnit.HOURS);
            localCache.put(id, product);
        }
        return product;
    }
}
```

### 5.3 优缺点

| 维度 | 说明 |
|------|------|
| 优点 | 性能极高（L1 命中 < 1ms）、防缓存雪崩兜底、减少 Redis 网络开销 |
| 缺点 | 本地缓存数据不一致（各节点自己的缓存）、内存占用、需要广播失效机制 |

**广播失效方案**：更新数据时，通过 Redis Pub/Sub 或 RocketMQ 广播通知所有节点清理本地缓存。

---

## 六、面试速记

| 问题 | 一句话原因 | 一句话解决方案 |
|------|-----------|--------------|
| 缓存穿透 | 查不存在的数据 | 布隆过滤器 + 空对象缓存 |
| 缓存击穿 | 热点 key 过期 | 互斥锁 或 逻辑过期 |
| 缓存雪崩 | 大量 key 同时过期或 Redis 宕机 | 随机过期 + 多级缓存 + 高可用 |
| 数据不一致 | 缓存和数据库独立写入 | Cache-Aside + 删缓存 + Canal |

> 进入下一节：分布式锁——从 SETNX 到 Redisson 再到 RedLock 的演进之路。