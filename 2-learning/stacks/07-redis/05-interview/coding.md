# 代码题 — 手写分布式锁 · Lua 限流脚本 · 布隆过滤器

> 等级：🎯 面试冲刺
> 目标：手写 3 个面试高频代码题，覆盖分布式锁、Lua 脚本、布隆过滤器。

---

## 一、手写分布式锁（基于 Redis SETNX + Lua）

### 1.1 题目

用 Redis 实现一个分布式锁，要求：
1. 加锁超时自动释放
2. 只能释放自己持有的锁
3. 支持重入

### 1.2 答案

```java
import redis.clients.jedis.Jedis;
import redis.clients.jedis.params.SetParams;
import java.util.Collections;
import java.util.UUID;

public class RedisDistributedLock {
    private final Jedis jedis;
    private final String lockKey;
    private final String lockValue;  // UUID:threadId
    private static final long DEFAULT_EXPIRE = 30000;  // 30 秒

    public RedisDistributedLock(Jedis jedis, String lockKey) {
        this.jedis = jedis;
        this.lockKey = lockKey;
        this.lockValue = UUID.randomUUID().toString() + ":" + Thread.currentThread().getId();
    }

    /**
     * 加锁（非阻塞，尝试一次）
     */
    public boolean tryLock(long expireMs) {
        // SET NX EX：原子操作，不存在才设置，并设置过期时间
        String result = jedis.set(lockKey, lockValue,
            SetParams.setParams().nx().px(expireMs));
        return "OK".equals(result);
    }

    /**
     * 加锁（阻塞，自旋等待）
     */
    public void lock(long expireMs) {
        while (true) {
            String result = jedis.set(lockKey, lockValue,
                SetParams.setParams().nx().px(expireMs));
            if ("OK".equals(result)) {
                return;
            }
            try {
                Thread.sleep(50);  // 自旋间隔
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("锁获取被中断");
            }
        }
    }

    /**
     * 解锁（Lua 脚本保证原子性）
     */
    public boolean unlock() {
        // Lua 脚本：只有 value 匹配时才删除
        String lua = "if redis.call('get', KEYS[1]) == ARGV[1] " +
            "then return redis.call('del', KEYS[1]) " +
            "else return 0 end";
        Long result = (Long) jedis.eval(lua, 
            Collections.singletonList(lockKey), 
            Collections.singletonList(lockValue));
        return result != null && result == 1L;
    }

    /**
     * 可重入锁实现（基于 Hash）
     */
    public boolean tryReentrantLock() {
        String lua = 
            "if redis.call('exists', KEYS[1]) == 0 then " +
            "    redis.call('hincrby', KEYS[1], ARGV[1], 1); " +
            "    redis.call('pexpire', KEYS[1], ARGV[2]); " +
            "    return 1; " +
            "end; " +
            "if redis.call('hexists', KEYS[1], ARGV[1]) == 1 then " +
            "    redis.call('hincrby', KEYS[1], ARGV[1], 1); " +
            "    redis.call('pexpire', KEYS[1], ARGV[2]); " +
            "    return 1; " +
            "end; " +
            "return 0;";

        Long result = (Long) jedis.eval(lua,
            Collections.singletonList(lockKey),
            Collections.singletonList(lockValue),
            String.valueOf(DEFAULT_EXPIRE));
        return result != null && result == 1L;
    }

    /**
     * 可重入锁解锁
     */
    public boolean unlockReentrant() {
        String lua = 
            "if redis.call('hexists', KEYS[1], ARGV[1]) == 0 then " +
            "    return nil; " +
            "end; " +
            "local counter = redis.call('hincrby', KEYS[1], ARGV[1], -1); " +
            "if counter > 0 then " +
            "    redis.call('pexpire', KEYS[1], ARGV[2]); " +
            "    return 0; " +
            "else " +
            "    redis.call('del', KEYS[1]); " +
            "    return 1; " +
            "end;";

        Long result = (Long) jedis.eval(lua,
            Collections.singletonList(lockKey),
            Collections.singletonList(lockValue),
            String.valueOf(DEFAULT_EXPIRE));
        return result != null && result == 1L;
    }
}
```

### 1.3 使用示例

```java
public class SeckillService {
    private final Jedis jedis = new Jedis("localhost", 6379);

    public void seckill(Long skuId) {
        RedisDistributedLock lock = 
            new RedisDistributedLock(jedis, "seckill:lock:" + skuId);
        try {
            lock.lock(30000);  // 等待获取锁，最多 30 秒
            // 业务逻辑...
        } finally {
            lock.unlock();
        }
    }
}
```

### 1.4 面试要点

> **为什么解锁要用 Lua 脚本？**
> 因为 `GET` 和 `DEL` 不是原子操作，如果先 `GET` 判断是自己的锁，然后 `DEL` 之前锁过期了，另一个线程加锁成功，就会误删别人的锁。Lua 脚本将 `GET` + `DEL` 合并为原子操作。

> **自旋锁的缺点？**
> 浪费 CPU 资源。自旋间隔 50ms 是经验值，大厂生产中建议用 Redisson 的 `lock()` 方法（基于发布订阅，锁释放时通知等待线程）。

---

## 二、Lua 脚本实现限流（滑动窗口）

### 2.1 题目

用 Redis + Lua 实现一个滑动窗口限流器，限制某个接口在指定时间窗口内的最大请求次数。

### 2.2 答案

```lua
-- 滑动窗口限流 Lua 脚本
-- KEYS[1] = 限流 key（如 rate:limit:api:/product/detail）
-- ARGV[1] = 窗口大小（毫秒）
-- ARGV[2] = 最大请求次数
-- ARGV[3] = 当前时间戳（毫秒）

-- 1. 删除窗口外的旧记录
redis.call('ZREMRANGEBYSCORE', KEYS[1], 0, ARGV[3] - ARGV[1])

-- 2. 获取当前窗口内的请求数
local current = redis.call('ZCARD', KEYS[1])

-- 3. 判断是否超限
if current >= tonumber(ARGV[2]) then
    -- 返回剩余时间（毫秒），客户端可等待后重试
    local oldest = redis.call('ZRANGE', KEYS[1], 0, 0, 'WITHSCORES')
    local ttl = ARGV[1] - (ARGV[3] - oldest[2])
    return {0, ttl}  -- 拒绝，并返回还需等待时间
end

-- 4. 记录本次请求
redis.call('ZADD', KEYS[1], ARGV[3], ARGV[3])
redis.call('PEXPIRE', KEYS[1], ARGV[1])  -- 设置过期时间

return {1, 0}  -- 允许
```

### 2.3 Java 调用

```java
@Service
public class RateLimiterService {
    @Autowired
    private StringRedisTemplate redisTemplate;

    /**
     * 限流检查
     * @param key 限流 key
     * @param windowMs 窗口大小（毫秒）
     * @param maxCount 最大请求次数
     * @return true=允许通过，false=被限流
     */
    public boolean tryAcquire(String key, long windowMs, int maxCount) {
        String lua = 
            "redis.call('ZREMRANGEBYSCORE', KEYS[1], 0, ARGV[3] - ARGV[1]); " +
            "local current = redis.call('ZCARD', KEYS[1]); " +
            "if current >= tonumber(ARGV[2]) then " +
            "    return 0; " +
            "end; " +
            "redis.call('ZADD', KEYS[1], ARGV[3], ARGV[3]); " +
            "redis.call('PEXPIRE', KEYS[1], ARGV[1]); " +
            "return 1;";

        Long result = redisTemplate.execute(
            new DefaultRedisScript<>(lua, Long.class),
            Collections.singletonList(key),
            String.valueOf(windowMs),
            String.valueOf(maxCount),
            String.valueOf(System.currentTimeMillis())
        );
        return result != null && result == 1L;
    }

    /**
     * 使用示例：限制商品详情接口每秒 100 次
     */
    public boolean checkProductRateLimit(Long productId) {
        return tryAcquire("rate:limit:product:" + productId, 1000, 100);
    }
}
```

### 2.4 面试要点

> **滑动窗口 vs 固定窗口？**
> 固定窗口（`INCR` + `EXPIRE`）在窗口边界可能有两倍流量冲击（如 0:59 大量请求，1:00 又大量请求）。滑动窗口通过 ZSet 记录每个请求的时间戳，精度更高。

> **ZSet 做限流的内存开销？**
> 每个请求需要存储一个 ZSet 成员（时间戳），内存开销可控。如果限流阈值是 1000 QPS，每秒最多 1000 个成员，按 10 字节计算，每秒约 10KB。

---

## 三、手写布隆过滤器（基于 Bitmap）

### 3.1 题目

用 Redis Bitmap 实现一个布隆过滤器，支持：
1. `add(key)`：添加元素
2. `mightContain(key)`：判断元素是否可能存在
3. 配置预估元素数量和误判率

### 3.2 答案

```java
@Component
public class RedisBloomFilter {
    @Autowired
    private StringRedisTemplate redisTemplate;

    private static final String BLOOM_PREFIX = "bloom:";
    private final int bitSize;      // 位图大小
    private final int hashCount;    // hash 函数个数

    /**
     * 构造函数
     * @param name 过滤器名称
     * @param expectedInsertions 预估元素数量
     * @param fpp 误判率（false positive probability）
     */
    public RedisBloomFilter(String name, int expectedInsertions, double fpp) {
        this.bitSize = optimalBitSize(expectedInsertions, fpp);
        this.hashCount = optimalHashCount(expectedInsertions, bitSize);
        // 初始化位图
        redisTemplate.opsForValue().setBit(BLOOM_PREFIX + name, bitSize - 1, false);
    }

    /**
     * 计算最优位图大小
     * m = -n * ln(p) / (ln(2)^2)
     */
    private int optimalBitSize(int n, double p) {
        return (int) (-n * Math.log(p) / (Math.log(2) * Math.log(2)));
    }

    /**
     * 计算最优 hash 函数个数
     * k = (m/n) * ln(2)
     */
    private int optimalHashCount(int n, int m) {
        return (int) Math.round((double) m / n * Math.log(2));
    }

    /**
     * 添加元素
     */
    public void add(String name, String element) {
        String key = BLOOM_PREFIX + name;
        for (int i = 0; i < hashCount; i++) {
            int hash = hash(element, i);
            int offset = hash % bitSize;
            redisTemplate.opsForValue().setBit(key, offset, true);
        }
    }

    /**
     * 判断元素是否可能存在
     * @return true=可能存在（有误判），false=一定不存在
     */
    public boolean mightContain(String name, String element) {
        String key = BLOOM_PREFIX + name;
        for (int i = 0; i < hashCount; i++) {
            int hash = hash(element, i);
            int offset = hash % bitSize;
            Boolean bit = redisTemplate.opsForValue().getBit(key, offset);
            if (bit == null || !bit) {
                return false;  // 只要有一位为 0，一定不存在
            }
        }
        return true;  // 所有位都为 1，可能存在
    }

    /**
     * 批量添加
     */
    public void addAll(String name, List<String> elements) {
        for (String element : elements) {
            add(name, element);
        }
    }

    /**
     * 简单的 hash 函数（使用多个不同的种子）
     */
    private int hash(String element, int seed) {
        int h = 0;
        for (char c : element.toCharArray()) {
            h = 31 * h + c;
        }
        // 使用不同的种子产生不同的 hash
        return h ^ (seed * 0x9E3779B9);
    }
}
```

### 3.3 使用示例

```java
@Service
public class ProductBloomService {
    @Autowired
    private RedisBloomFilter bloomFilter;

    @PostConstruct
    public void init() {
        // 预计 100 万商品，1% 误判率
        bloomFilter = new RedisBloomFilter("product", 1000000, 0.01);
    }

    /**
     * 商品上线时，加入布隆过滤器
     */
    public void onProductOnline(Long productId) {
        bloomFilter.add("product", String.valueOf(productId));
    }

    /**
     * 先查布隆过滤器，再查缓存
     */
    public boolean productExists(Long productId) {
        return bloomFilter.mightContain("product", String.valueOf(productId));
    }
}
```

### 3.4 面试要点

> **布隆过滤器为什么不能删除元素？**
> 因为一个 bit 可能被多个元素共同映射。删除一个元素将 bit 置 0 后，可能导致其他元素被误判为不存在。解决方案：Counting Bloom Filter（每个 bit 变计数器，支持删除，但内存占用更大）。

> **误判率怎么控制？**
> 通过调整位图大小和 hash 函数个数。公式：`m = -n * ln(p) / ln(2)^2`，`k = (m/n) * ln(2)`。1% 误判率时，每个元素约 10 bit。

> **布隆过滤器在生产中怎么用？**
> 启动时加载全量数据，增量上线时实时添加。定期重建（如每周）以清理过期数据。使用 Guava 的 `BloomFilter` 或 Redisson 的 `RBloomFilter` 更方便。

---

## 四、面试速记

| 代码题 | 核心考点 | 关键代码行 |
|--------|---------|-----------|
| 分布式锁 | SETNX + Lua 原子性 | `jedis.set(key, val, SetParams.setParams().nx().px(expire))` |
| 可重入锁 | Hash 记录重入次数 | `redis.call('hincrby', key, id, 1)` |
| 滑动窗口限流 | ZSet 按时间戳记录 | `ZREMRANGEBYSCORE` + `ZCARD` |
| 布隆过滤器 | Bitmap + 多个 hash | `setBit(offset, true)` + `getBit(offset)` |

> 面试准备完成！回到 README.md 对照学习路径图，检查是否还有遗漏的知识点。