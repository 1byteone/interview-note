# 06 · 秒杀服务与高并发：Redisson 分布式锁 + 布隆过滤器 + 库存双写

> 秒杀是电商系统压测强度的天花板：瞬时请求量是平时的百倍千倍。看项目如何用"布隆过滤器 → 分布式锁 → Redis 原子扣减 → 异步消息双写"四层架构扛住高并发。
>
> **对应项目：** `mall-services/mall-seckill-service`

---

## 一、基础概念

### 1.1 秒杀场景的三大挑战

| 挑战 | 说明 | 解决思路 |
|------|------|---------|
| **大流量冲击** | 瞬时上千 TPS 打爆服务 | 请求分流、静态化、限流 |
| **超卖** | 库存卖超（100 件卖出 1000 单） | 原子扣减 + 分布式锁 |
| **库存一致性** | Redis 和数据库库存不一致 | 消息队列异步双写 |

### 1.2 四层防护架构

```
第 1 层: 商品静态页 (预先生成 HTML)
  避免高并发下动态渲染商品页

第 2 层: 布隆过滤器
  快速判断"这个商品是否存在"，拦截非法/过期请求

第 3 层: Redisson 分布式锁
  保证同一商品同一时刻只有一个线程扣减库存

第 4 层: Redis 原子扣减 + MQ 异步双写
  Redis 扛住瞬时高频扣减，异步把最终结果同步到 MySQL
```

---

## 二、进阶机制

### 2.1 第 1 层：商品静态页

```java
@Override
public void generateHtml() throws Exception {
    // 获取今天的活动
    List<SeckillActivityDTO> activityList = seckillActivityService.listAllByToday();
    // 逐活动生成商品详情模板（Thymeleaf 静态化）
    for (SeckillActivityDTO activity : activityList) {
        pageService.generateProductTemplate(activity.getId());
    }
}
```

**为什么不动态渲染？** 秒杀页在活动开始后会面临海量访问，如果每个请求都走"查数据库 → 渲染模板 → 返回 HTML"的流程，数据库会被打爆。预先生成静态 HTML，部署到 CDN/Nginx，高并发下直接返回静态文件。

### 2.2 第 2 层：布隆过滤器

```java
// 库存预热时，将库存 key 加入布隆过滤器
public void loadStockCache() {
    for (SeckillGoodsDTO goods : goodsList) {
        String stockKey = SeckillRedisKeyConstant.stockKey(activityId, skuId);
        redisTemplate.opsForValue().set(stockKey, goods.getStoreCount());
        redisTemplate.expire(stockKey, 1, TimeUnit.DAYS);
        cacheBloomFilter.add(stockKey);  // 加入布隆过滤器
    }
}

// 扣减库存时，先检查布隆过滤器
public void decreaseStock(StockDeductDTO stockDeductDTO) {
    String stockKey = SeckillRedisKeyConstant.stockKey(...);
    // 布隆过滤器判断商品是否存在
    if (!cacheBloomFilter.mightContain(stockKey)) {
        throw new BusinessException(7003, "库存不存在");
    }
    ...
}
```

**布隆过滤器解决什么问题？**

```
传统流程（缓存穿透）：
  请求 → Redis 查不到 key → 去数据库查 → 数据库也没有 → 返回空
  → 大量非法 key 请求会直接打到数据库，打爆 MySQL

布隆过滤器优化：
  请求 → 布隆过滤器判断 key 是否存在
       → 不存在 → 直接返回"无此商品"（不查 Redis 和 MySQL）
       → 存在   → 才继续查 Redis → 数据库
  → 非法请求在布隆过滤器这一层就被拦截
```

**布隆过滤器原理：** 用多个哈希函数将 key 映射到 bit 数组的多个位置并置 1。查询时检查这些位置是否全为 1：**全为 1 则可能存在（有误判），有一个为 0 则一定不存在**。空间占用极小，查询极快。

### 2.3 第 3 层：Redisson 分布式锁

```java
// 构建分布式锁
String lockKey = SeckillRedisKeyConstant.stockLockKey(activityId, skuId);
RLock lock = redissonClient.getLock(lockKey);

try {
    // 尝试获取锁，最多等待 3 秒
    boolean isLocked = lock.tryLock(3, TimeUnit.SECONDS);
    if (!isLocked) {
        throw new BusinessException(7001, "系统繁忙，请稍后重试");
    }

    // 获取锁成功，执行库存扣减
    Object stockObj = redisTemplate.opsForValue().get(stockKey);
    int beforeStock = Integer.parseInt(stockObj.toString());
    if (beforeStock <= 0) {
        throw new BusinessException(ResultCodeEnum.STOCK_SHORTAGE);
    }

    // Redis 原子扣减
    Long remainCount = redisTemplate.opsForValue().decrement(stockKey);

    if (remainCount >= 0) {
        // 扣减成功
    } else {
        // 超卖回滚
        redisTemplate.opsForValue().increment(stockKey);
        throw new BusinessException(ResultCodeEnum.STOCK_SHORTAGE);
    }
} finally {
    // 释放锁（必须检查是否当前线程持有）
    if (lock.isHeldByCurrentThread()) {
        lock.unlock();
    }
}
```

**Redisson 分布式锁 vs 手动实现：**

| 对比项 | 手动 Redis SETNX | Redisson RLock |
|--------|-----------------|----------------|
| 原子性 | 需 SETNX + EXPIRE 两步 | 底层 Lua 脚本保证原子 |
| 自动释放 | 需手动判断过期时间 | 看门狗（watchdog）自动续期 |
| 重入 | 不支持 | 支持（可重入锁） |
| 释放安全 | 可能误删别人锁 | 检查线程持有再释放 |
| 效率 | 低 | 高（底层 Lua 原子操作） |

**看门狗机制：** 获取锁后，Redisson 后台线程每 10 秒自动续期锁的过期时间（默认 30 秒）。业务代码执行完释放锁，看门狗自动停止。**即使业务执行时间超过锁的过期时间，也不会提前释放。**

### 2.4 第 4 层：Redis 原子扣减 + MQ 异步双写

```java
// 第 1 步: Redis 原子扣减（扛高并发）
Long remainCount = redisTemplate.opsForValue().decrement(stockKey);

// 第 2 步: 发送 MQ 消息（异步同步到数据库）
StockDeductMessageDTO message = new StockDeductMessageDTO();
message.setActivityId(stockDeductDTO.getActivityId());
message.setSkuId(stockDeductDTO.getSkuId());
message.setQuantity(stockDeductDTO.getQuantity());
message.setBeforeStock(beforeStock);
message.setAfterStock(afterStock);
message.setTransactionId(lockKey + ":" + System.currentTimeMillis());
message.setDeductTime(LocalDateTime.now());

streamBridge.send("stockDeductOutput-out-0", message);
```

**为什么 Redis 扣减 + MQ 双写而不是直接扣 MySQL？**

```
方案 A（直接扣 MySQL）:
  请求 → 数据库 UPDATE stock SET stock=stock-1 WHERE stock>0
  → 行锁竞争激烈，数据库连接打满
  → 只能承受几百 TPS

方案 B（本项目 Redis + MQ）:
  请求 → Redis INCR/DECR 原子扣减（内存操作，百万 QPS）
  → 发送 MQ 消息（异步，不阻塞请求）
  → 消费者异步更新 MySQL（串行化，避免行锁竞争）
  → 可以承受数万 QPS
```

**一致性保证：** Redis 是"预扣减"，最终数据一致性通过 MQ 异步同步到 MySQL。如果 Redis 扣减成功但 MQ 消费失败，通过 `restoreStock`（幂等 + 补偿）恢复库存。

---

## 三、项目现场

### 3.1 完整扣减流程

```
用户秒杀请求
    │
    ▼
┌─ 布隆过滤器检查 ──────────────┐
│ key 存在? → 否: 返回"库存不存在"│
│ key 存在? → 是: 继续           │
└───────────────────────────────┘
    │
    ▼
┌─ Redisson 分布式锁 ───────────┐
│ tryLock(3s) 获取锁            │
│ 获取失败 → "系统繁忙"           │
│ 获取成功 → 继续               │
└───────────────────────────────┘
    │
    ▼
┌─ Redis 原子扣减 ──────────────┐
│ decrement(stockKey)          │
│ remainCount >= 0 → 成功       │
│ remainCount < 0  → 回滚+报错   │
└───────────────────────────────┘
    │
    ▼
┌─ 发送 MQ 消息 ────────────────┐
│ streamBridge.send(...)       │
│ 异步：消费者更新 MySQL 库存    │
└───────────────────────────────┘
    │
    ▼
┌─ 释放分布式锁 ─────────────────┐
│ finally: isHeldByCurrentThread│
│ → unlock()                   │
└───────────────────────────────┘
```

### 3.2 代码演进记录（读注释中的三版实现）

源码注释里保留了从简单到专业的演进过程：

| 版本 | 实现 | 问题 |
|------|------|------|
| V1 | `get()` 读库存 + `decrement()` 扣减 | 非原子操作，并发下超卖 |
| V2 | Redisson 锁 + 原子扣减 | 解决了超卖，但代码冗长 |
| V3（最终） | 布隆过滤器 + Redisson 锁 + 原子扣减 + MQ | 防御穿透 + 性能 + 一致性 |

**这个演进过程是面试的加分故事：** 展示了你从"能用"到"专业"的成长路径。

---

## 四、面试要点

### Q1: 你们是怎么防止库存扣超卖的（超卖）？

**回答思路：** 三层防护：1) **Redis 原子操作**——`decrement()` 是原子的，返回扣减后的剩余量，小于 0 则回滚；2) **Redisson 分布式锁**——同一商品的扣减并发通过 `tryLock` 串行化；3) **数据库兜底**——MQ 异步同步到 MySQL 时使用 `UPDATE ... WHERE stock > 0` 条件更新，再次防止超卖。

### Q2: Redisson 分布式锁和手动 SETNX 有什么区别？

**回答思路：** 手动实现需要 SETNX + EXPIRE 两步，容易原子性问题（SETNX 成功但 EXPIRE 失败 → 锁永不释放）。Redisson 底层用 Lua 脚本保证原子性，还提供看门狗自动续期、可重入、公平锁等特性。释放前检查 `isHeldByCurrentThread` 防止误删别人的锁。

### Q3: 布隆过滤器是怎么解决缓存穿透的？

**回答思路：** 布隆过滤器用 bit 数组 + 多个哈希函数判断 key 是否存在。查询时先查布隆过滤器，若不存在则直接返回，不再查 Redis 和数据库。这样大量非法 key 请求在布隆过滤器层被拦截，保护数据库。

### Q4: 为什么库存扣减放在 Redis 而不是数据库？

**回答思路：** 高并发下数据库行锁竞争激烈，性能瓶颈明显。Redis 内存操作支持数百万 QPS。用 **Redis 预扣减**扛住瞬时流量，再通过 **MQ 异步双写**把最终结果同步到 MySQL，保证最终一致性。这是一致性与性能的权衡。

---

> **下一篇：** [07-USER-JWT.md —— 用户服务与 JWT 鉴权：登录、Token 刷新、双拦截器](./07-USER-JWT.md)
>
> 从登录到鉴权，看 JWT 在网关层如何校验，在服务层如何传递用户信息。