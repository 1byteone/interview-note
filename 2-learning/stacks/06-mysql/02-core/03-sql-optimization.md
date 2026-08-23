# SQL 优化 — 慢查询 · 索引优化 · 实战案例

> 🎯 进阶路线 · 预计阅读时间：50 分钟

---

## 一、慢查询日志分析

### 1.1 开启慢查询日志

```sql
-- 查询当前慢查询配置
SHOW VARIABLES LIKE '%slow_query%';
SHOW VARIABLES LIKE '%long_query_time%';

-- 开启慢查询日志（生产环境谨慎使用）
SET GLOBAL slow_query_log = ON;
SET GLOBAL long_query_time = 1;  -- 超过 1 秒记录
SET GLOBAL log_queries_not_using_indexes = ON;
```

### 1.2 慢查询日志分析

```bash
# 使用 mysqldumpslow 分析
mysqldumpslow -s t -t 10 /var/lib/mysql/slow.log

# 参数说明
# -s t: 按查询时间排序
# -s c: 按次数排序
# -t 10: 取前 10 条
```

### 1.3 使用 pt-query-digest

```bash
# Percona Toolkit 的慢查询分析工具
pt-query-digest /var/lib/mysql/slow.log > digest_report.txt

# 输出包含：
# 1. 总体统计（查询数量、总时间、最慢查询）
# 2. 按时间排序的查询排名
# 3. 每个查询的详细分析
```

---

## 二、SQL 优化策略

### 2.1 分页优化

**传统分页的问题：**

```sql
-- 越往后翻越慢，因为 LIMIT 100000, 10 需要扫描 100010 行后丢弃前 100000 行
SELECT * FROM order_info ORDER BY id DESC LIMIT 100000, 10;
```

**优化方案 1：子查询分页（延迟关联）**

```sql
-- 先通过覆盖索引快速定位 ID，再关联获取完整数据
SELECT o.*
FROM order_info o
INNER JOIN (
    SELECT id FROM order_info
    ORDER BY id DESC
    LIMIT 100000, 10
) t ON o.id = t.id;
```

**优化方案 2：游标分页（基于上次位置）**

```sql
-- 记录上一页的最后 ID，下一页从该 ID 开始
SELECT * FROM order_info
WHERE id < 100000  -- 上一页最后一条记录的 ID
ORDER BY id DESC
LIMIT 10;

-- 适用于：用户下拉加载更多，不支持跳页
-- 优点：每次查询都是固定扫描行数，无论第几页
```

### 2.2 覆盖索引

```sql
-- 优化前：回表查询所有字段
SELECT * FROM order_info WHERE status = 1 ORDER BY create_time DESC LIMIT 10;

-- 优化后：创建覆盖索引
CREATE INDEX idx_status_time ON order_info(status, create_time);

-- 如果查询只需要部分字段，让索引包含所有查询字段
SELECT id, status, create_time FROM order_info
WHERE status = 1 ORDER BY create_time DESC LIMIT 10;
-- 此时 Extra 显示 Using index（完全覆盖索引）
```

### 2.3 避免回表

```sql
-- 回表场景：二级索引找到主键后，再通过主键查聚簇索引
-- 解决方案：覆盖索引 / 只查询索引中包含的字段

-- 优化前（需要回表）
SELECT * FROM product WHERE name LIKE 'iPhone%';

-- 优化后（只查询索引中的字段，无需回表）
CREATE INDEX idx_name ON product(name, price, stock);
SELECT name, price, stock FROM product WHERE name LIKE 'iPhone%';
```

### 2.4 批量操作优化

```sql
-- 优化前：逐条插入（大量网络开销 + 事务开销）
INSERT INTO order_item (order_id, product_id, quantity) VALUES (1, 100, 1);
INSERT INTO order_item (order_id, product_id, quantity) VALUES (1, 101, 2);
INSERT INTO order_item (order_id, product_id, quantity) VALUES (1, 102, 1);

-- 优化后：批量插入（一条 SQL 插入多条）
INSERT INTO order_item (order_id, product_id, quantity) VALUES
(1, 100, 1),
(1, 101, 2),
(1, 102, 1);

-- 批量更新（使用 CASE WHEN）
UPDATE product SET
    price = CASE id
        WHEN 100 THEN 6999
        WHEN 101 THEN 14999
        WHEN 102 THEN 1999
    END
WHERE id IN (100, 101, 102);
```

### 2.5 数据类型优化

```sql
-- 优化前：使用 VARCHAR 存 IP 地址
CREATE TABLE log (
    ip VARCHAR(15)  -- 浪费空间，无法比较大小
);

-- 优化后：使用 INT UNSIGNED
CREATE TABLE log (
    ip INT UNSIGNED  -- 4 字节，支持范围查询
);
-- INSERT INTO log VALUES (INET_ATON('192.168.1.1'));
-- SELECT INET_NTOA(ip) FROM log;
```

---

## 三、实战：订单查询从 5s 到 50ms

### 3.1 问题描述

mall-micro-cloud 的订单列表页，查询某个用户的订单，数据量 500 万行，原始 SQL 执行 5 秒。

### 3.2 原始 SQL

```sql
SELECT *
FROM order_info
WHERE user_id = 12345
  AND status IN (0, 1, 2)
  AND create_time >= '2024-01-01'
  AND create_time < '2024-07-01'
ORDER BY create_time DESC
LIMIT 20;
```

### 3.3 分析过程

**Step 1: Explain 分析**

```sql
EXPLAIN SELECT *
FROM order_info
WHERE user_id = 12345
  AND status IN (0, 1, 2)
  AND create_time >= '2024-01-01'
  AND create_time < '2024-07-01'
ORDER BY create_time DESC
LIMIT 20;
```

结果：
```
type: ref
key: idx_user_id
rows: 85632
Extra: Using where; Using filesort
```

**问题诊断：**
1. `rows=85632`：扫描了 8 万多行
2. `Using filesort`：内存排序，性能差
3. 只有 `user_id` 索引，status 和 create_time 都在回表后过滤

### 3.4 优化方案

**方案 1：创建联合索引**

```sql
-- 创建联合索引（user_id 过滤 + create_time 排序）
CREATE INDEX idx_user_time ON order_info(user_id, create_time);

-- 再次 Explain
EXPLAIN SELECT *
FROM order_info
WHERE user_id = 12345
  AND status IN (0, 1, 2)
  AND create_time >= '2024-01-01'
  AND create_time < '2024-07-01'
ORDER BY create_time DESC
LIMIT 20;
```

结果：
```
type: range
key: idx_user_time
rows: 500
Extra: Using index condition; Using where
```

**方案 2：覆盖索引优化**

```sql
-- 如果查询只返回部分字段，可以创建覆盖索引
CREATE INDEX idx_user_time_status ON order_info(user_id, create_time, status);

-- 查询只返回需要的字段，无需回表
SELECT id, order_no, user_id, total_amount, status, create_time
FROM order_info
WHERE user_id = 12345
  AND status IN (0, 1, 2)
  AND create_time >= '2024-01-01'
  AND create_time < '2024-07-01'
ORDER BY create_time DESC
LIMIT 20;
```

### 3.5 优化效果对比

| 指标 | 优化前 | 优化后 | 提升 |
|------|--------|--------|------|
| 扫描行数 | 85,632 | 500 | 171 倍 |
| 查询耗时 | 5.2s | 45ms | 115 倍 |
| Extra | Using filesort | Using index condition | 消除排序 |
| 回表次数 | 85632 次 | 20 次 | 4281 倍 |

---

## 四、更多优化技巧

### 4.1 索引下推（ICP）

MySQL 5.6 引入，在索引遍历过程中就过滤数据，减少回表次数。

```sql
-- 联合索引 (zipcode, lastname, firstname)
SELECT * FROM people
WHERE zipcode = '100000'
  AND lastname LIKE '%张%'   -- LIKE 前模糊，lastname 无法使用索引
  AND firstname = '三';
```

**没有 ICP：** 只能用到 zipcode，回表 10000 行后再过滤 lastname 和 firstname
**有 ICP：** 遍历索引时就用 lastname 和 firstname 过滤，回表只 10 行

### 4.2 小表驱动大表

```sql
-- 优化前：大表（order_info）驱动小表（user）
SELECT * FROM user u
WHERE EXISTS (SELECT 1 FROM order_info o WHERE o.user_id = u.id);

-- 优化后：小表（user）驱动大表（order_info）—— 实际上 MySQL 优化器会做选择
-- 但写 SQL 时养成习惯：数据量小的表放前面
```

### 4.3 使用 FORCE INDEX

当优化器选择了错误的索引时，可以强制指定：

```sql
SELECT * FROM order_info FORCE INDEX (idx_user_time)
WHERE user_id = 12345
  AND create_time >= '2024-01-01';
```

---

## 五、SQL 优化 Checklist

- [ ] 是否使用了 Explain 分析执行计划？
- [ ] type 是否达到了 ref 或 range？
- [ ] 是否有 Using filesort 或 Using temporary？
- [ ] 扫描行数（rows）是否合理？
- [ ] 是否可以使用覆盖索引？
- [ ] 分页查询是否使用了游标分页？
- [ ] 索引列上是否有函数操作？
- [ ] 是否有隐式类型转换？
- [ ] 批量操作是否合并为一条 SQL？
- [ ] 数据量大的表是否做了分区或分表？

---

## 总结

- **慢查询日志**是发现性能问题的第一步
- **索引优化**是最有效的 SQL 优化手段
- **覆盖索引**避免回表，**游标分页**解决深度分页
- 一个 5s 的查询，通过合理索引可以降到 50ms
- 优化后一定要用 Explain 验证效果

> 下一步：[03-advanced/01-sharding.md](../03-advanced/01-sharding.md) — 分库分表策略