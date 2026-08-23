# SQL 进阶 — JOIN · 子查询 · 窗口函数

> 👶 初学者路线 · 预计阅读时间：40 分钟

---

## 一、JOIN 详解

JOIN 是 SQL 中最核心也最容易出错的知识点。以 mall-micro-cloud 的订单表和用户表为例：

```sql
-- 示例数据
CREATE TABLE `user` (
    `id` INT PRIMARY KEY,
    `name` VARCHAR(50)
);
INSERT INTO user VALUES (1, 'Alice'), (2, 'Bob'), (3, 'Charlie');

CREATE TABLE `order_info` (
    `id` INT PRIMARY KEY,
    `user_id` INT,
    `amount` DECIMAL(10,2)
);
INSERT INTO order_info VALUES (1, 1, 100), (2, 1, 200), (3, 2, 150);
```

### 1.1 INNER JOIN（内连接）

只返回两边都匹配的记录。

```sql
SELECT u.name, o.id, o.amount
FROM user u
INNER JOIN order_info o ON u.id = o.user_id;
```

结果：
```
| name  | id | amount |
|-------|----|--------|
| Alice | 1  | 100    |
| Alice | 2  | 200    |
| Bob   | 3  | 150    |
```

Charlie 没有订单，被排除。Alice 有 2 个订单，产生 2 行。

### 1.2 LEFT JOIN（左连接）

返回左表所有记录，右表没有匹配则填 NULL。

```sql
SELECT u.name, o.id, o.amount
FROM user u
LEFT JOIN order_info o ON u.id = o.user_id;
```

结果：
```
| name    | id   | amount |
|---------|------|--------|
| Alice   | 1    | 100    |
| Alice   | 2    | 200    |
| Bob     | 3    | 150    |
| Charlie | NULL | NULL   |
```

### 1.3 RIGHT JOIN（右连接）

返回右表所有记录，用法同 LEFT JOIN 方向相反。实际开发中习惯统一用 LEFT JOIN。

### 1.4 FULL JOIN（全连接）

MySQL 不直接支持 FULL JOIN，用 UNION 模拟：

```sql
SELECT u.name, o.id, o.amount
FROM user u
LEFT JOIN order_info o ON u.id = o.user_id
UNION
SELECT u.name, o.id, o.amount
FROM user u
RIGHT JOIN order_info o ON u.id = o.user_id;
```

### 1.5 JOIN 执行顺序图

```
INNER JOIN:     LEFT JOIN:          RIGHT JOIN:
┌─────┬─────┐   ┌─────┬─────┐      ┌─────┬─────┐
│  A  │  B  │   │  A  │  B  │      │  A  │  B  │
│  ∩  │     │   │  ∪  │     │      │     │  ∪  │
└─────┴─────┘   └─────┴─────┘      └─────┴─────┘
```

---

## 二、子查询与 CTE

### 2.1 子查询

子查询是嵌套在 SELECT/FROM/WHERE 中的查询。

**标量子查询（返回单值）：**

```sql
-- 查询高于平均价格的商品
SELECT name, price
FROM product
WHERE price > (SELECT AVG(price) FROM product);
```

**行子查询（返回一行）：**

```sql
-- 查询每个分类下最贵的商品
SELECT p.*
FROM product p
WHERE (p.category_id, p.price) IN (
    SELECT category_id, MAX(price)
    FROM product
    GROUP BY category_id
);
```

**表子查询（FROM 子句）：**

```sql
-- 查询每个分类的均价和商品数
SELECT c.name, t.avg_price, t.cnt
FROM category c
INNER JOIN (
    SELECT category_id,
           AVG(price) AS avg_price,
           COUNT(*) AS cnt
    FROM product
    GROUP BY category_id
) t ON c.id = t.category_id;
```

### 2.2 EXISTS 与 IN

```sql
-- EXISTS：有订单的用户（适合大表，短路判断）
SELECT * FROM user u
WHERE EXISTS (
    SELECT 1 FROM order_info o WHERE o.user_id = u.id
);

-- IN：有订单的用户（适合小表，先计算子查询结果）
SELECT * FROM user
WHERE id IN (SELECT user_id FROM order_info);
```

> 原则：外层表小用 EXISTS，子查询结果小用 IN。MySQL 优化器通常能自动选择最优策略，但理解区别很重要。

### 2.3 CTE（公共表表达式，WITH 子句）

MySQL 8.0 引入 CTE，让复杂查询更清晰：

```sql
-- 按月统计销售额
WITH monthly_sales AS (
    SELECT
        DATE_FORMAT(create_time, '%Y-%m') AS month,
        SUM(amount) AS total_amount
    FROM order_info
    WHERE status = 1
    GROUP BY DATE_FORMAT(create_time, '%Y-%m')
)
SELECT month, total_amount,
       total_amount - LAG(total_amount, 1) OVER (ORDER BY month) AS growth
FROM monthly_sales
ORDER BY month;
```

**CTE 递归查询（树形结构）：**

```sql
-- 查询分类树（商品分类层级）
WITH RECURSIVE category_tree AS (
    -- 根节点
    SELECT id, name, parent_id, 1 AS level
    FROM category
    WHERE parent_id = 0
    UNION ALL
    -- 递归子节点
    SELECT c.id, c.name, c.parent_id, ct.level + 1
    FROM category c
    INNER JOIN category_tree ct ON c.parent_id = ct.id
)
SELECT * FROM category_tree ORDER BY level, id;
```

---

## 三、窗口函数

窗口函数在不改变行数的情况下，对每一行计算一个聚合值。MySQL 8.0 支持。

### 3.1 ROW_NUMBER — 行号

```sql
-- 每个分类下按价格排序，取每个分类最贵的商品
SELECT name, category_id, price,
       ROW_NUMBER() OVER (PARTITION BY category_id ORDER BY price DESC) AS rn
FROM product;

-- 取每个分类最贵的商品
SELECT * FROM (
    SELECT name, category_id, price,
           ROW_NUMBER() OVER (PARTITION BY category_id ORDER BY price DESC) AS rn
    FROM product
) t WHERE t.rn = 1;
```

### 3.2 RANK 与 DENSE_RANK

```sql
SELECT name, category_id, price,
       RANK() OVER (PARTITION BY category_id ORDER BY price DESC) AS rk,
       DENSE_RANK() OVER (PARTITION BY category_id ORDER BY price DESC) AS drk
FROM product;
```

| 价格 | RANK | DENSE_RANK | 说明 |
|------|------|------------|------|
| 100  | 1    | 1          | 最高价 |
| 90   | 2    | 2          | 第二 |
| 90   | 2    | 2          | 并列第二 |
| 80   | 4    | 3          | RANK 跳过 3，DENSE_RANK 不跳 |

### 3.3 SUM OVER — 累计求和

```sql
-- 按时间累计销售额
SELECT
    DATE(create_time) AS day,
    SUM(amount) AS daily_amount,
    SUM(SUM(amount)) OVER (ORDER BY DATE(create_time)) AS cumulative_amount
FROM order_info
WHERE status = 1
GROUP BY DATE(create_time);
```

### 3.4 LAG / LEAD — 前后行

```sql
-- 环比增长
SELECT
    DATE_FORMAT(create_time, '%Y-%m') AS month,
    SUM(amount) AS amount,
    LAG(SUM(amount), 1) OVER (ORDER BY DATE_FORMAT(create_time, '%Y-%m')) AS prev_month,
    (SUM(amount) - LAG(SUM(amount), 1) OVER (ORDER BY DATE_FORMAT(create_time, '%Y-%m')))
    / LAG(SUM(amount), 1) OVER (ORDER BY DATE_FORMAT(create_time, '%Y-%m')) * 100 AS growth_rate
FROM order_info
WHERE status = 1
GROUP BY DATE_FORMAT(create_time, '%Y-%m');
```

---

## 四、分组聚合高级用法

### 4.1 GROUP_CONCAT

```sql
-- 每个分类下的商品名称列表
SELECT category_id,
       GROUP_CONCAT(name ORDER BY price DESC SEPARATOR '; ') AS product_list
FROM product
GROUP BY category_id;
```

### 4.2 ROLLUP — 多维汇总

```sql
-- 按分类和状态分组，并统计小计和总计
SELECT category_id, status, COUNT(*) AS cnt
FROM product
GROUP BY category_id, status WITH ROLLUP;
```

### 4.3 HAVING 与 WHERE 的区别

| 对比项 | WHERE | HAVING |
|--------|-------|--------|
| 执行时机 | GROUP BY 之前 | GROUP BY 之后 |
| 可使用聚合函数 | 否 | 是 |
| 使用索引 | 可以 | 通常不能 |
| 性能 | 更快（过滤后再分组） | 较慢（分组后再过滤） |

```sql
-- WHERE 先过滤，再分组
SELECT category_id, AVG(price) AS avg_price
FROM product
WHERE status = 1  -- 先过滤下架商品
GROUP BY category_id;

-- HAVING 分组后过滤
SELECT category_id, AVG(price) AS avg_price
FROM product
GROUP BY category_id
HAVING AVG(price) > 100;  -- 只保留均价 > 100 的分类
```

---

## 总结

| 知识点 | 使用场景 | 注意事项 |
|--------|----------|----------|
| INNER JOIN | 两边都必须存在的数据 | 注意笛卡尔积 |
| LEFT JOIN | 主表数据必须全部保留 | 右表条件放 ON 还是 WHERE 有区别 |
| CTE | 复杂查询拆分、递归树 | MySQL 8.0+ |
| 窗口函数 | 排名、累计、环比 | 不减少行数 |
| ROLLUP | 报表统计、小计总计 | 结果行数超出预期 |

> 下一步：[02-core/01-index-principles.md](../02-core/01-index-principles.md) — 索引原理与 B+Tree