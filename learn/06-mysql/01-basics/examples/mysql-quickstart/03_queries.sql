-- ============================================
-- SQL 查询示例
-- 演示：JOIN / 聚合 / 子查询 / 窗口函数 / CTE
-- ============================================

USE ecommerce;

-- ==================== 1. SELECT + JOIN ====================

-- 1.1 查询订单详情（三表关联）
-- 需求：查看每个订单的商品明细
SELECT
    o.order_no,
    u.username,
    oi.product_name,
    oi.price,
    oi.quantity,
    oi.subtotal,
    o.status,
    o.created_at
FROM orders o
JOIN users u ON o.user_id = u.id
JOIN order_items oi ON oi.order_id = o.id
ORDER BY o.created_at DESC;

-- 1.2 查询每个分类下的商品数量（LEFT JOIN）
-- 需求：查看所有分类（含无商品的分类）
SELECT
    c.name AS category_name,
    COUNT(p.id) AS product_count
FROM categories c
LEFT JOIN products p ON p.category_id = c.id
GROUP BY c.id, c.name
ORDER BY product_count DESC;


-- ==================== 2. 聚合（GROUP BY） ====================

-- 2.1 用户订单统计
-- 需求：每个用户的订单数、总消费金额
SELECT
    u.id,
    u.username,
    COUNT(o.id)           AS order_count,
    COALESCE(SUM(o.total_amount), 0) AS total_spent,
    MAX(o.created_at)     AS last_order_time
FROM users u
LEFT JOIN orders o ON o.user_id = u.id
GROUP BY u.id, u.username
ORDER BY total_spent DESC;

-- 2.2 商品销售排行
-- 需求：销量前 5 的商品
SELECT
    p.name,
    p.price,
    p.sales,
    p.stock,
    c.name AS category
FROM products p
JOIN categories c ON c.id = p.category_id
ORDER BY p.sales DESC
LIMIT 5;


-- ==================== 3. 子查询 ====================

-- 3.1 查询购买过 iPhone 的用户
-- 需求：找出购买过特定商品的用户
SELECT DISTINCT u.username, u.email
FROM users u
WHERE u.id IN (
    SELECT DISTINCT o.user_id
    FROM orders o
    JOIN order_items oi ON oi.order_id = o.id
    WHERE oi.product_name LIKE '%iPhone%'
);

-- 3.2 查询价格高于平均价的商品
SELECT name, price
FROM products
WHERE price > (SELECT AVG(price) FROM products)
ORDER BY price DESC;


-- ==================== 4. 窗口函数 ====================

-- 4.1 ROW_NUMBER：每个分类下最贵的商品
SELECT
    c.name AS category,
    p.name AS product,
    p.price,
    ROW_NUMBER() OVER (PARTITION BY p.category_id ORDER BY p.price DESC) AS price_rank
FROM products p
JOIN categories c ON c.id = p.category_id
ORDER BY c.name, price_rank;

-- 4.2 RANK：各用户订单金额排名（相同金额并列）
SELECT
    u.username,
    o.order_no,
    o.total_amount,
    RANK() OVER (ORDER BY o.total_amount DESC) AS amount_rank
FROM orders o
JOIN users u ON u.id = o.user_id;

-- 4.3 累计销售额（SUM OVER）
SELECT
    DATE(created_at) AS order_date,
    COUNT(*)         AS order_count,
    SUM(total_amount) AS daily_amount,
    SUM(SUM(total_amount)) OVER (ORDER BY DATE(created_at)) AS cumulative_amount
FROM orders
GROUP BY DATE(created_at)
ORDER BY order_date;


-- ==================== 5. CTE（公用表表达式） ====================

-- 5.1 递归 CTE：查询分类树
WITH RECURSIVE category_tree AS (
    -- 根分类
    SELECT id, name, parent_id, 1 AS depth, CAST(name AS CHAR(200)) AS path
    FROM categories
    WHERE parent_id IS NULL

    UNION ALL

    -- 子分类
    SELECT
        c.id, c.name, c.parent_id,
        ct.depth + 1,
        CONCAT(ct.path, ' > ', c.name)
    FROM categories c
    JOIN category_tree ct ON c.parent_id = ct.id
)
SELECT * FROM category_tree
ORDER BY path;

-- 5.2 CTE：找出高价值用户（消费 > 5000 的活跃用户）
WITH user_spending AS (
    SELECT
        u.id,
        u.username,
        COUNT(o.id) AS order_count,
        SUM(o.total_amount) AS total_spent
    FROM users u
    JOIN orders o ON o.user_id = u.id
    WHERE o.status NOT IN (4)  -- 排除已取消订单
    GROUP BY u.id, u.username
    HAVING total_spent > 5000
)
SELECT * FROM user_spending
ORDER BY total_spent DESC;