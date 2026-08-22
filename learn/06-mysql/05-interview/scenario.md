# 场景题 — 慢查询排查 · 死锁排查 · 大表分页 · 分库分表

> 🎯 面试场景 · 解决实际业务问题

---

## 场景一：SQL 慢查询排查

### 问题描述

某天下午，运维反馈商城订单查询接口响应变慢，从 50ms 飙升到 5s。你是负责的 DBA/后端开发，如何排查？

### 排查步骤

**Step 1：确认问题范围**

```sql
-- 1. 查看慢查询日志
-- 找到最近 30 分钟的慢查询
mysqldumpslow -s t -t 10 /var/lib/mysql/slow.log

-- 2. 查看当前正在执行的查询
SHOW FULL PROCESSLIST;
-- 关注：Time（执行时间）、State（状态）、Info（SQL 语句）
-- 常见状态：Sending data（正在查询）、Locked（等待锁）
```

**Step 2：分析慢查询**

```sql
-- 假设找到的慢查询如下
SELECT * FROM order_info
WHERE user_id = 12345
  AND status IN (0, 1)
  AND create_time >= '2024-06-01'
  AND create_time < '2024-07-01'
ORDER BY create_time DESC
LIMIT 20;
```

**Step 3：Explain 分析**

```sql
EXPLAIN SELECT * FROM order_info
WHERE user_id = 12345
  AND status IN (0, 1)
  AND create_time >= '2024-06-01'
  AND create_time < '2024-07-01'
ORDER BY create_time DESC
LIMIT 20;
```

**Step 4：诊断问题**

```
type: ref
key: idx_user_id
rows: 56234
Extra: Using where; Using filesort
```

问题诊断：
1. `rows=56234`：扫描了 5 万多行，太多
2. `Using filesort`：文件排序，性能差
3. 只有 `user_id` 索引，`create_time` 排序没有利用索引

**Step 5：优化方案**

```sql
-- 创建联合索引，让排序也能走索引
CREATE INDEX idx_user_create ON order_info(user_id, create_time);

-- 优化后再次 Explain
EXPLAIN SELECT * FROM order_info
WHERE user_id = 12345
  AND status IN (0, 1)
  AND create_time >= '2024-06-01'
  AND create_time < '2024-07-01'
ORDER BY create_time DESC
LIMIT 20;
```

```
type: range
key: idx_user_create
rows: 346
Extra: Using index condition; Using where
```

扫描行数从 56234 降到 346，filesort 消失，查询时间从 5s 降到 50ms。

---

## 场景二：死锁排查

### 问题描述

线上频繁出现死锁异常，错误日志：`Deadlock found when trying to get lock; try restarting transaction`

### 排查步骤

**Step 1：查看死锁日志**

```sql
SHOW ENGINE INNODB STATUS\G
```

重点关注 `LATEST DETECTED DEADLOCK` 部分：

```
------------------------
LATEST DETECTED DEADLOCK
------------------------
*** (1) TRANSACTION:
TRANSACTION 2001, ACTIVE 10 sec
MySQL thread id 8, query id 100
UPDATE product SET stock = stock - 1 WHERE id = 1

*** (1) WAITING FOR THIS LOCK TO BE GRANTED:
RECORD LOCKS space id 100 page no 3 n bits 72 index PRIMARY of table `product`
*** (1) HOLDS THE LOCK:
RECORD LOCKS space id 100 page no 3 n bits 72 index PRIMARY of table `product`
*** (2) TRANSACTION:
TRANSACTION 2002, ACTIVE 8 sec
UPDATE product SET stock = stock - 1 WHERE id = 2

*** (2) WAITING FOR THIS LOCK TO BE GRANTED:
RECORD LOCKS space id 100 page no 3 n bits 72 index PRIMARY of table `product`
*** (2) HOLDS THE LOCK:
RECORD LOCKS space id 100 page no 3 n bits 72 index PRIMARY of table `product`
```

**Step 2：分析死锁原因**

```
事务 2001：持有 id=1 的锁，等待 id=2 的锁
事务 2002：持有 id=2 的锁，等待 id=1 的锁
→ 循环等待，死锁
```

**Step 3：解决方案**

```java
// 方案 1：固定顺序访问（推荐）
// 所有事务按相同顺序更新资源
public void batchDeductStock(Long id1, Long id2, int quantity) {
    // 确保按 ID 从小到大顺序更新
    if (id1 > id2) {
        Long temp = id1; id1 = id2; id2 = temp;
    }
    updateStock(id1, quantity);
    updateStock(id2, quantity);
}

// 方案 2：重试机制
@Retryable(value = DeadlockLoserDataAccessException.class, maxAttempts = 3)
public void deductStock(Long productId, int quantity) {
    productMapper.deductStock(productId, quantity);
}
```

---

## 场景三：大表分页优化

### 问题描述

订单管理后台需要查看所有订单，支持翻页。数据量 5000 万行。用户翻到第 10000 页时，接口超时。

### 问题代码

```sql
-- 传统分页，越往后越慢
SELECT * FROM order_info ORDER BY id DESC LIMIT 100000, 20;
```

### 优化方案

**方案 1：游标分页（推荐）**

```sql
-- 第一次查询：不需要 lastId
SELECT * FROM order_info
ORDER BY id DESC
LIMIT 20;

-- 后续查询：传入上一页最后一条记录的 id
SELECT * FROM order_info
WHERE id < #{lastId}
ORDER BY id DESC
LIMIT 20;
```

**优点：** 每次查询都是固定扫描行数，不受页数影响
**缺点：** 不支持跳页，只适合"加载更多"场景

**方案 2：子查询优化（支持跳页）**

```sql
-- 先通过覆盖索引快速定位 ID
SELECT * FROM order_info
WHERE id IN (
    SELECT id FROM order_info
    ORDER BY id DESC
    LIMIT 100000, 20
);
```

注意：MySQL 对 IN 子查询优化有限，有时比直接 LIMIT 还慢。

**方案 3：延迟关联（推荐支持跳页）**

```sql
SELECT o.*
FROM order_info o
INNER JOIN (
    SELECT id FROM order_info
    ORDER BY id DESC
    LIMIT 100000, 20
) t ON o.id = t.id;
```

---

## 场景四：分库分表方案设计

### 问题描述

商城订单表数据量已达 2 亿行，查询和写入性能明显下降。需要设计分库分表方案。

### 需求分析

| 维度 | 需求 |
|------|------|
| 数据量 | 当前 2 亿，年增长 50% |
| 写入 | 高峰期 5000 TPS |
| 查询 | 用户查自己的订单（高频），后台管理查询（低频） |
| 存储 | 3 年内预计 10 亿数据 |

### 设计方案

**1. 分片键选择：user_id**

原因：用户查询订单是最高频的查询，带 user_id 可以精确定位到分片。

**2. 分片数量计算**

```
目标：每张表不超过 500 万行
总数据量：10 亿行
分表数量：10亿 / 500万 = 200 张表
分库数量：4 个库，每库 50 张表
```

**3. 分片规则**

```sql
-- 分库：user_id % 4 → db0, db1, db2, db3
-- 分表：FLOOR(user_id / 4) % 50 → t_order_0 ~ t_order_49
-- 总 4 库 * 50 表 = 200 个分片

-- 分库算法
db_index = user_id % 4;

-- 分表算法
table_index = FLOOR(user_id / 4) % 50;
```

**4. 非分片键查询**

```sql
-- 查询订单详情（只有 order_no）
-- 方案：维护 order_no → user_id 映射表
CREATE TABLE order_no_mapping (
    order_no VARCHAR(32) PRIMARY KEY,
    user_id BIGINT NOT NULL,
    create_time DATETIME
);
```

**5. 扩容方案**

```
初始：4 库 * 50 表 = 200 分片
扩容：8 库 * 50 表 = 400 分片

扩容步骤：
1. 新建 4 个库
2. 双写（旧分片 + 新分片）
3. 迁移旧数据
4. 切换双写到新分片
5. 下线旧分片
```

---

## 场景五：缓存与数据库一致性

### 问题描述

商品详情页使用 Redis 缓存，但更新商品信息后，用户看到的是旧数据。

### 解决方案

**方案 1：Cache Aside Pattern（旁路缓存）**

```java
// 更新商品
public void updateProduct(Product product) {
    // 1. 更新数据库
    productMapper.updateById(product);

    // 2. 删除缓存（不是更新缓存）
    redisTemplate.delete("product:" + product.getId());
}

// 查询商品
public Product getProduct(Long id) {
    // 1. 查缓存
    Product product = redisTemplate.opsForValue().get("product:" + id);
    if (product != null) {
        return product;
    }

    // 2. 查数据库
    product = productMapper.selectById(id);

    // 3. 写入缓存
    if (product != null) {
        redisTemplate.opsForValue().set("product:" + id, product, 30, TimeUnit.MINUTES);
    }
    return product;
}
```

**方案 2：延迟双删（处理并发写）**

```java
public void updateProduct(Product product) {
    // 1. 删除缓存
    redisTemplate.delete("product:" + product.getId());

    // 2. 更新数据库
    productMapper.updateById(product);

    // 3. 延迟 500ms 再次删除缓存（处理并发读写的脏数据）
    Thread.sleep(500);
    redisTemplate.delete("product:" + product.getId());
}
```

---

> 下一步：[coding.md](./coding.md) — 代码题实战