# 代码题 — SQL 优化 · 表设计 · Explain 分析

> 🎯 面试编码 · 手写 SQL + 设计题

---

## 一、SQL 优化题

### 题目 1：慢查询优化

**原始 SQL：**

```sql
SELECT *
FROM order_info
WHERE user_id = 12345
  AND status = 1
  AND create_time > '2024-01-01'
ORDER BY create_time DESC
LIMIT 20;
```

**Explain 分析：**

```
type: ref
key: idx_user_id
rows: 12345
Extra: Using where; Using filesort
```

**问题：**
1. `rows=12345`：扫描行数过多
2. `Using filesort`：未使用索引排序
3. 回表查询所有字段

**优化方案：**

```sql
-- 优化 1：创建联合索引，覆盖查询和排序
CREATE INDEX idx_user_status_time ON order_info(user_id, status, create_time);

-- 优化 2：如果只需要部分字段，创建覆盖索引
CREATE INDEX idx_user_time ON order_info(user_id, create_time);
-- 只查询需要的字段
SELECT id, order_no, user_id, total_amount, status, create_time
FROM order_info
WHERE user_id = 12345
  AND status = 1
  AND create_time > '2024-01-01'
ORDER BY create_time DESC
LIMIT 20;
```

### 题目 2：分页优化

**原始 SQL（第 10000 页）：**

```sql
SELECT * FROM product ORDER BY id DESC LIMIT 200000, 20;
```

**优化：**

```sql
-- 游标分页（推荐）
SELECT * FROM product
WHERE id < #{lastId}
ORDER BY id DESC
LIMIT 20;

-- 延迟关联（支持跳页）
SELECT p.*
FROM product p
INNER JOIN (
    SELECT id FROM product
    ORDER BY id DESC
    LIMIT 200000, 20
) t ON p.id = t.id;
```

### 题目 3：JOIN 优化

**原始 SQL：**

```sql
SELECT *
FROM order_info o
LEFT JOIN order_item oi ON o.id = oi.order_id
LEFT JOIN product p ON oi.product_id = p.id
WHERE o.user_id = 12345;
```

**问题：**
- 查询所有字段（`*`），大量数据传输
- 三表关联，可能产生大临时表

**优化：**

```sql
-- 只查询需要的字段
SELECT o.id, o.order_no, o.total_amount, o.status,
       oi.product_id, oi.quantity, oi.price,
       p.name AS product_name
FROM order_info o
LEFT JOIN order_item oi ON o.id = oi.order_id
LEFT JOIN product p ON oi.product_id = p.id
WHERE o.user_id = 12345;
```

---

## 二、表设计题

### 题目 1：设计商品评论表

**需求：**
- 用户可以对商品发表评论
- 评论可以带图（最多 9 张）
- 评论可以回复
- 评论可以点赞
- 需要统计评论数和好评率

**设计：**

```sql
-- 评论表
CREATE TABLE `review` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '评论ID',
    `product_id` BIGINT NOT NULL COMMENT '商品ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `order_id` BIGINT NOT NULL COMMENT '订单ID（已验证购买）',
    `content` TEXT NOT NULL COMMENT '评论内容',
    `rating` TINYINT NOT NULL COMMENT '评分 1-5',
    `like_count` INT NOT NULL DEFAULT 0 COMMENT '点赞数',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态 1=显示 0=隐藏',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_product_id` (`product_id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_order_id` (`order_id`),
    KEY `idx_product_status` (`product_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品评论表';

-- 评论图片表（一对多）
CREATE TABLE `review_image` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `review_id` BIGINT NOT NULL COMMENT '评论ID',
    `url` VARCHAR(500) NOT NULL COMMENT '图片URL',
    `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_review_id` (`review_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评论图片表';

-- 评论回复表（评论回复使用独立的表，结构更清晰）
CREATE TABLE `review_reply` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `review_id` BIGINT NOT NULL COMMENT '评论ID',
    `user_id` BIGINT NOT NULL COMMENT '回复者ID',
    `content` VARCHAR(500) NOT NULL COMMENT '回复内容',
    `parent_id` BIGINT DEFAULT NULL COMMENT '回复的回复ID',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_review_id` (`review_id`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评论回复表';

-- 商品统计表（冗余统计信息）
CREATE TABLE `product_review_stat` (
    `product_id` BIGINT NOT NULL PRIMARY KEY,
    `total_reviews` INT NOT NULL DEFAULT 0 COMMENT '评论总数',
    `avg_rating` DECIMAL(2,1) NOT NULL DEFAULT 0.0 COMMENT '平均评分',
    `good_rate` DECIMAL(5,2) NOT NULL DEFAULT 0.00 COMMENT '好评率（评分>=4）',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品评论统计表';
```

### 题目 2：设计秒杀活动表

**需求：**
- 商品限时限量秒杀
- 每个用户限购一件
- 防止超卖

**设计：**

```sql
-- 秒杀活动表
CREATE TABLE `seckill_activity` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `product_id` BIGINT NOT NULL COMMENT '商品ID',
    `seckill_price` DECIMAL(10,2) NOT NULL COMMENT '秒杀价',
    `stock` INT NOT NULL COMMENT '秒杀库存',
    `start_time` DATETIME NOT NULL COMMENT '开始时间',
    `end_time` DATETIME NOT NULL COMMENT '结束时间',
    `status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态 0=待开始 1=进行中 2=已结束',
    `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_product_id` (`product_id`),
    KEY `idx_start_time` (`start_time`, `end_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='秒杀活动表';

-- 秒杀订单表
CREATE TABLE `seckill_order` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `activity_id` BIGINT NOT NULL COMMENT '活动ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `order_no` VARCHAR(32) NOT NULL COMMENT '订单号',
    `status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态 0=待支付 1=已支付',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_activity_user` (`activity_id`, `user_id`),  -- 一人一单
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='秒杀订单表';

-- 库存扣减（乐观锁，防止超卖）
-- UPDATE seckill_activity SET stock = stock - 1, version = version + 1
-- WHERE id = #{activityId} AND stock > 0 AND version = #{version};
```

---

## 三、Explain 分析题

### 题目 1：分析执行计划

```sql
-- 表结构
CREATE TABLE `t` (
    `id` INT PRIMARY KEY,
    `a` INT,
    `b` INT,
    INDEX idx_a (`a`),
    INDEX idx_b (`b`)
);

-- 查询
EXPLAIN SELECT * FROM t WHERE a = 1 AND b = 2;
```

**可能的结果：**

```
id: 1
select_type: SIMPLE
table: t
type: ref
possible_keys: idx_a, idx_b
key: idx_a  （或 idx_b，取决于优化器选择）
rows: 100
Extra: Using where
```

**分析：** 优化器选择其中一个索引，然后通过回表过滤另一个条件。更好的方案是创建联合索引 `(a, b)`。

### 题目 2：判断以下查询是否走索引

```sql
-- 表结构
CREATE TABLE `user` (
    `id` INT PRIMARY KEY AUTO_INCREMENT,
    `phone` VARCHAR(11) NOT NULL,
    `name` VARCHAR(50) NOT NULL,
    `status` TINYINT NOT NULL,
    `create_time` DATETIME NOT NULL,
    INDEX idx_phone (`phone`),
    INDEX idx_name (`name`),
    INDEX idx_status (`status`)
);
```

**查询 1：是否走索引？**

```sql
SELECT * FROM user WHERE phone = 13800138000;
```

**答案：** 不走索引。`phone` 是 VARCHAR 类型，传入 INT 导致隐式类型转换，索引失效。

**优化：** `WHERE phone = '13800138000'`

**查询 2：是否走索引？**

```sql
SELECT * FROM user WHERE status = 1;
```

**答案：** 可能不走索引。如果 `status` 的区分度很低（如 90% 的数据 status=1），优化器认为全表扫描比走索引回表更快，会选择全表扫描。

**优化：** 考虑覆盖索引 `INDEX(status, id, name)` 或 `INDEX(status, name)`。

**查询 3：是否走索引？**

```sql
SELECT * FROM user WHERE DATE(create_time) = '2024-01-01';
```

**答案：** 不走索引。`DATE()` 函数导致索引失效。

**优化：**

```sql
WHERE create_time >= '2024-01-01' AND create_time < '2024-01-02';
```

---

## 四、综合题

### 题目：优化库存扣减

**场景：** 商品秒杀，需要安全扣减库存，防止超卖。

**问题方案：**

```sql
-- 问题：非原子操作，并发下超卖
SELECT stock FROM product WHERE id = 100;  -- stock=10
-- 如果两个线程同时读到 10，都扣减到 9
UPDATE product SET stock = 9 WHERE id = 100;
```

**优化方案 1：乐观锁**

```sql
UPDATE product
SET stock = stock - 1
WHERE id = 100 AND stock > 0;
-- 返回受影响行数，如果为 0 说明库存不足
```

**优化方案 2：乐观锁 + 版本号**

```sql
UPDATE product
SET stock = stock - 1, version = version + 1
WHERE id = 100 AND stock > 0 AND version = #{version};
```

**优化方案 3：Redis 预热 + 异步落库**

```java
// 1. 提前将库存加载到 Redis
// 2. 秒杀时先扣 Redis
Long stock = redisTemplate.opsForValue().decrement("seckill:stock:100");
if (stock < 0) {
    // 库存不足，回滚
    redisTemplate.opsForValue().increment("seckill:stock:100");
    return "库存不足";
}

// 3. 异步发送消息到 MQ 落库
// 4. 消费消息执行数据库扣减
```

---

> 下一步：[resources.md](../resources.md) — 推荐学习资源