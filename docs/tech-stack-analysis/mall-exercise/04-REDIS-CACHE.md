# 04 · Redis 缓存策略实战：Cache-Aside、分布式锁、缓存穿透

> 缓存是面试最高频的话题之一。看 mall-exercise 如何用 Redis 实现 Cache-Aside 模式、分布式锁防缓存击穿、以及双写一致性策略。
>
> **对应项目：** `mall-exercise/src/main/java/itcast/cloud/mall/exercise/redis/`

---

## 一、基础概念

### 1.1 三种缓存问题

| 问题 | 描述 | 解决方案 |
|------|------|---------|
| **缓存穿透** | 查询不存在的数据，每次都穿透缓存查数据库 | 布隆过滤器 / 缓存空值 |
| **缓存击穿** | 热点 key 过期，大量请求同时打到数据库 | 分布式锁 / 互斥重建 |
| **缓存雪崩** | 大量 key 同时过期，数据库被打爆 | 过期时间随机化 / 多级缓存 |

---

## 二、进阶机制

### 2.1 Cache-Aside 模式（旁路缓存）

```java
@Service
@RequiredArgsConstructor
public class ProductCacheServiceImpl implements ProductCacheService {

    private final ProductMapper productMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    // 读：先查缓存，未命中再查 DB 并写入缓存
    @Override
    @Cacheable(value = "product", key = "#id", unless = "#result == null")
    public Product getProductById(Long id) {
        log.info("查询数据库获取商品: {}", id);
        return productMapper.selectById(id);
    }

    // 写：先更新 DB，再删除缓存
    @Override
    @CacheEvict(value = "product", key = "#product.id")
    public void updateProduct(Product product) {
        productMapper.updateById(product);  // 先更新 DB
        // 缓存由 @CacheEvict 自动删除
    }
}
```

**为什么是"先更新 DB，再删除缓存"而不是"先更新缓存"？**

| 方案 | 并发问题 | 结论 |
|------|---------|------|
| 先更新缓存，再更新 DB | 缓存写入成功但 DB 写入失败 → 脏数据 | ❌ |
| 先更新 DB，再删除缓存 | 删除缓存失败 → 旧缓存 → 最终一致（延迟双删） | ✅ |
| 先更新 DB，再更新缓存 | 并发写导致缓存数据与 DB 不一致 | ❌ |
| **先更新 DB，再删除缓存** | 删除失败有兜底（延迟双删 + 过期时间） | **✅ 推荐** |

### 2.2 手动实现 —— 分布式锁防缓存击穿

```java
@Override
public Product getProductByIdManual(Long id) {
    String cacheKey = CACHE_KEY_PREFIX + id;

    // 1. 查缓存
    Object cachedValue = redisTemplate.opsForValue().get(cacheKey);
    if (cachedValue != null) {
        return (Product) cachedValue;
    }

    // 2. 缓存未命中，获取分布式锁
    String lockKey = LOCK_KEY_PREFIX + id;
    Boolean locked = redisTemplate.opsForValue()
        .setIfAbsent(lockKey, "locked", LOCK_EXPIRE_TIME, TimeUnit.SECONDS);

    if (Boolean.TRUE.equals(locked)) {
        try {
            // 3. 双重检查：可能其他线程已经重建了缓存
            cachedValue = redisTemplate.opsForValue().get(cacheKey);
            if (cachedValue != null) {
                return (Product) cachedValue;
            }

            // 4. 查数据库
            Product product = productMapper.selectById(id);

            // 5. 写入缓存
            if (product != null) {
                redisTemplate.opsForValue().set(cacheKey, product, 1, TimeUnit.HOURS);
            } else {
                // 缓存空值防穿透（短过期时间）
                redisTemplate.opsForValue().set(cacheKey, new Product(), 5, TimeUnit.MINUTES);
            }
            return product;
        } finally {
            redisTemplate.delete(lockKey); // 释放锁
        }
    } else {
        // 6. 获取锁失败，等待后重试
        Thread.sleep(100);
        return getProductByIdManual(id); // 递归重试
    }
}
```

**完整流程：**

```
请求 → 查缓存 → 命中? → 返回
                ↓ 未命中
           SETNX 分布式锁 → 成功? → 双重检查 → 查 DB → 写缓存 → 返回
                           ↓ 失败
                        等待 100ms → 递归重试
```

---

## 三、面试要点

### Q1: Cache-Aside 模式中，为什么是"先更新 DB，再删除缓存"？

**回答思路：** 保证最终一致性。先更新 DB 确保数据源正确，再删除缓存让下次读取时重新从 DB 加载。如果删除缓存失败，通过缓存的过期时间最终达到一致。相比"先更新缓存"的方案，DB 是权威数据源，以 DB 为准。

### Q2: 缓存击穿怎么解决？

**回答思路：** 热点 key 过期时，大量请求同时打到数据库。解决方案是**分布式锁互斥重建**——第一个请求获取锁，查 DB 重建缓存，其他请求等待后读取新缓存。项目中的 `getProductByIdManual` 实现了这个方案，使用 `SETNX` 作为分布式锁，加上双重检查（double-check）防止重复重建。

### Q3: 缓存穿透怎么解决？

**回答思路：** 查询不存在的数据，每次都会穿透缓存查 DB。两种方案：1) **缓存空值**——查询结果为 null 时也缓存（短过期时间，如 5 分钟）；2) **布隆过滤器**——用 bit 数组判断 key 是否存在，不存在直接返回。项目中的代码在 `product == null` 时缓存了空对象（`new Product()`），这是防穿透的实践。

---

> **下一篇：** [05-MYBATISPLUS-ADV.md —— MyBatis-Plus 高级查询：多表关联、批量操作、统计聚合、链式查询](./05-MYBATISPLUS-ADV.md)
>
> 从基础 CRUD 到高级查询，看 MyBatis-Plus 的 Wrapper、批量操作、统计聚合能力的实战用法。