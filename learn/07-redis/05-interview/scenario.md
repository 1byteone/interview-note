# 场景题 — 实战问题排查与方案设计

> 等级：🎯 面试冲刺
> 目标：面对 4 个典型的 Redis 生产场景问题，用 STAR 法则组织答案，体现"排查思路 + 解决方案"。

---

## 场景一：缓存穿透导致数据库被打爆

### Situation（场景）

某天凌晨，AI 商城的数据库 CPU 使用率突然飙升到 100%，慢查询日志显示大量 SQL 都是在查不存在的商品 ID。同时 Redis 监控显示缓存命中率从 95% 骤降到 20%。

### Task（任务）

5 分钟内定位问题并止血，然后制定长期解决方案。

### Action（处理过程）

**1. 快速定位（3 分钟）**

```bash
# 1. 查看 Redis 命中率
> INFO stats
keyspace_misses: 950000    # 正常 50000，异常飙升
keyspace_hits: 200000      # 正常 950000

# 2. 分析请求模式（monitor 采样）
> redis-cli monitor | grep "product:"
# 发现大量请求 ID 是 -1、-2、99999999 等不存在的商品
```

**2. 紧急止血（2 分钟）**

```java
// 网关层限流：对商品详情接口进行限流
@Bean
public RateLimiter rateLimiter() {
    return RateLimiter.create(1000);  // 每秒最多 1000 个请求
}

// 快速过滤：在网关层拦截非法 ID
if (id == null || id < 0 || id > 10000000) {
    return Result.error("商品不存在");
}
```

**3. 长期解决方案（生产部署）**

```java
@Component
public class BloomFilterInitializer implements CommandLineRunner {
    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private ProductMapper productMapper;

    @Override
    public void run(String... args) {
        // 启动时加载所有商品 ID 到布隆过滤器
        List<Long> allIds = productMapper.selectAllIds();
        for (Long id : allIds) {
            // 使用 3 个 hash 函数计算位图下标
            for (int i = 0; i < 3; i++) {
                int offset = hash(id, i);
                redisTemplate.opsForValue().setBit("bloom:product", offset, true);
            }
        }
    }
}
```

### Result（效果）

- 紧急止血后，数据库 CPU 从 100% 降至 30%
- 布隆过滤器上线后，非法请求全部被拦截，缓存命中率恢复到 95%+
- 数据库 QPS 从 50000 降至 200

### 面试加分点

> **问题排查的顺序是什么？**
> 先看 Redis 命中率（`INFO stats`）→ 再看慢查询日志 → 分析请求模式（monitor 采样）→ 定位到非法 key 攻击。

> **如何判断是缓存穿透而不是缓存击穿？**
> 穿透是大量不存在的 key，Redis 中查不到，MySQL 也查不到。击穿是热点 key 过期，并发打到 MySQL，但 MySQL 中能查到。看 QPS 曲线：穿透是持续稳定增长，击穿是瞬时尖峰。

---

## 场景二：大 Key 导致 Redis 阻塞

### Situation（场景）

AI 商城上线了一个"用户购物车全部展示"的功能，部分用户购物车中有 5000+ 件商品。每次请求时，Redis 执行 `HGETALL user:cart:{id}` 耗时 3 秒，这段时间内 Redis 无法处理其他请求，导致所有接口延迟飙升。

### Task（任务）

找到导致 Redis 阻塞的"罪魁祸首"，并彻底解决大 Key 问题。

### Action（处理过程）

**1. 定位大 Key**

```bash
# 方法一：redis-cli --bigkeys 扫描
$ redis-cli --bigkeys
# Largest hash found: cart:user:1001 has 5234 fields

# 方法二：分析慢查询
> SLOWLOG GET 10
1) 1) (integer) 58
   2) (integer) 1724313600
   3) (integer) 3200000    # 3.2 秒！
   4) 1) "HGETALL"
      2) "cart:user:1001"
```

**2. 紧急处理**

```bash
# 用 HSCAN 分批获取，避免阻塞
> HSCAN cart:user:1001 0 COUNT 100
1) "123"    # 下一个 cursor
2) 1) "item:1001"
   2) "2"
   3) "item:1002"
   4) "1"
   ...

# 或者直接删除大 Key（异步删除，不阻塞）
> UNLINK cart:user:1001
```

**3. 长期方案：分桶存储**

```java
// 将购物车按 hash 分桶，每个桶最多 100 个商品
public class CartService {
    private static final int BUCKET_SIZE = 100;

    public void addToCart(Long userId, Long skuId, Integer quantity) {
        // 计算分桶编号
        int bucket = (int) (skuId % BUCKET_SIZE);
        String key = "cart:user:" + userId + ":" + bucket;
        redisTemplate.opsForHash().put(key, "item:" + skuId, quantity.toString());
    }

    public Map<String, String> getCart(Long userId) {
        Map<String, String> allItems = new HashMap<>();
        // 遍历所有分桶
        for (int i = 0; i < 100; i++) {
            String key = "cart:user:" + userId + ":" + i;
            Map<Object, Object> entries = redisTemplate.opsForHash().entries(key);
            for (Map.Entry<Object, Object> e : entries.entrySet()) {
                allItems.put((String) e.getKey(), (String) e.getValue());
            }
        }
        return allItems;
    }
}
```

### Result（效果）

- 单次购物车操作响应时间从 3 秒降至 5ms
- Redis 不再被大 Key 阻塞，所有接口恢复正常
- 分桶后，每个 Hash 小于 100 个字段，维持 ziplist 紧凑编码

### 面试加分点

> **大 Key 除了阻塞 Redis，还有什么危害？**
> 1. 网络延迟（传输大块数据）
> 2. 数据倾斜（Cluster 模式下，大 Key 所在节点内存和流量远高于其他节点）
> 3. 备份恢复慢（RDB 生成和加载都慢）
> 4. 复制延迟（大 Key 同步到从节点耗时）

---

## 场景三：Redis Cluster 集群扩容

### Situation（场景）

双 11 大促前，AI 商城的 Redis 内存使用率已经达到 80%，预计大促当天流量翻倍。需要在线扩容 Redis Cluster，从 3 主 3 从扩展到 6 主 6 从，且不能停机。

### Task（任务）

在不影响线上服务的前提下，将 Redis Cluster 从 3 主 3 从扩容到 6 主 6 从。

### Action（处理过程）

**1. 准备新节点**

```bash
# 在 6 台新机器上启动 Redis
redis-server /path/to/redis.conf    # 必须开启 cluster-enabled yes

# 将新节点加入集群
redis-cli --cluster add-node new-node-ip:6379 existing-node-ip:6379
```

**2. 重新分配哈希槽**

```bash
# 自动重新分配哈希槽
redis-cli --cluster rebalance cluster-ip:6379 --cluster-use-empty-masters

# 或者手动分配
redis-cli --cluster reshard cluster-ip:6379
# 输入要移动的槽位数（16384 / 6 ≈ 2730 个槽给每个新节点）
# 输入接收节点 ID
# 输入源节点 ID（all 表示从所有老节点均分）
```

**3. 添加从节点**

```bash
# 为新主节点添加从节点
redis-cli --cluster add-node slave-ip:6379 master-ip:6379 --cluster-slave
```

**4. 验证集群状态**

```bash
> CLUSTER INFO
cluster_state:ok
cluster_slots_assigned:16384

> CLUSTER NODES
# 看到所有 6 主 6 从节点状态正常
```

### Result（效果）

- 在线扩容完成，零停机
- 内存使用率从 80% 降至 40%
- 扩容过程中，少量请求返回 `MOVED` 重定向，客户端自动重试

### 面试加分点

> **扩容过程中，业务会受影响吗？**
> 槽位迁移过程中，被迁移的 key 会短暂不可用，返回 `ASK` 或 `MOVED` 重定向。智能客户端（如 JedisCluster、Lettuce）会缓存槽位映射并自动重试，对业务影响极小。

> **扩容时如何避免数据倾斜？**
> `reshard` 会自动计算每个节点应分配的槽位数，从所有老节点均分。建议分批迁移，每次迁移少量槽位，观察集群状态。

---

## 场景四：缓存与数据库数据不一致

### Situation（场景）

AI 商城的商品管理员反馈：修改商品价格后，部分用户看到的还是旧价格。分析发现是缓存更新逻辑有问题——某些情况下缓存中的旧数据没有被清除。

### Task（任务）

排查数据不一致的原因，并设计一个可靠的缓存一致性方案。

### Action（处理过程）

**1. 排查原因**

```java
// 问题代码：先更新缓存再更新数据库
// 这种顺序下，如果数据库更新失败，缓存中就是错误数据
public void updateProduct(Product product) {
    // 先更新 Redis（错误！）
    redisTemplate.opsForValue().set("product:" + product.getId(), 
        JSON.toJSONString(product));
    // 再更新 MySQL
    productMapper.updateById(product);
    // 如果这里抛异常，Redis 中的就是脏数据
}
```

**2. 修正方案：先更新 DB，再删缓存**

```java
@Transactional
public void updateProduct(Product product) {
    // 1. 先更新数据库
    productMapper.updateById(product);
    
    // 2. 再删除缓存（下次读时重建）
    redisTemplate.delete("product:" + product.getId());
    
    // 3. 如果删除缓存失败，发送 MQ 重试
    // 事务同步器：事务提交后执行
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                // 异步重试删除缓存
                sendRetryMessage(product.getId());
            }
        });
}
```

**3. MQ 重试兜底**

```java
@Component
public class CacheInvalidateConsumer {
    @RabbitListener(queues = "cache.retry")
    public void handleRetry(Long productId) {
        boolean deleted = redisTemplate.delete("product:" + productId);
        if (!deleted) {
            // 重试 3 次
            log.warn("缓存删除失败，准备重试: productId={}", productId);
        }
    }
}
```

### Result（效果）

- 数据不一致问题消除
- 缓存重建延迟 < 1 秒（下一次读请求时自动重建）
- MQ 重试保证了删除操作最终成功

### 面试加分点

> **为什么"先更新 DB 再删缓存"比"先删缓存再更新 DB"好？**
> 先删缓存再更新 DB，在并发读时，读请求可能读到旧数据并写回缓存（见"延时双删"）。先更新 DB 再删缓存，读请求在缓存被删后重建的是新数据，不一致窗口更小。

> **删除缓存失败怎么办？**
> 用 MQ 异步重试，或给缓存设置短 TTL 作为兜底。TTL 过期后缓存自动失效，读请求重建新数据。

---

## 五、面试速记

| 场景 | 根因 | 一句话方案 |
|------|------|-----------|
| 缓存穿透 | 查不存在的数据 | 布隆过滤器 + 空对象 |
| 大 Key 阻塞 | 单个 key 存放过多数据 | 拆分 + 分桶 + UNLINK |
| 集群扩容 | 容量不足 | redis-cli --cluster rebalance |
| 数据不一致 | 更新顺序/缓存策略错误 | 先更新 DB 再删缓存 + MQ 重试 |

> 进入代码题篇：手写分布式锁、Lua 限流脚本、布隆过滤器。