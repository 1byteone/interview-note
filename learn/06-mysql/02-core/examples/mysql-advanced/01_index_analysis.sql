-- ============================================
-- MySQL 索引分析与优化
-- 演示：索引类型 / EXPLAIN 分析 / 索引优化策略
-- ============================================

USE ecommerce;

-- ==================== 1. 索引类型 ====================

-- 1.1 普通索引（加速查询）
CREATE INDEX idx_orders_created_at ON orders(created_at);

-- 1.2 唯一索引（保证唯一性 + 加速查询）
CREATE UNIQUE INDEX uk_products_name ON products(name);

-- 1.3 复合索引（多列联合查询）
-- 原则：最左前缀匹配，将选择性高的列放在前面
CREATE INDEX idx_orders_user_status ON orders(user_id, status);

-- 1.4 前缀索引（对大文本字段，只索引前 N 个字符）
CREATE INDEX idx_products_name_prefix ON products(name(10));

-- 1.5 降序索引（MySQL 8.0+）
CREATE INDEX idx_products_price_desc ON products(price DESC);

-- 1.6 覆盖索引（包含查询所需的所有列，避免回表）
-- 以下索引包含了查询的列，查询时只需扫描索引无需访问数据行
CREATE INDEX idx_orders_covering ON orders(id, order_no, user_id, status, total_amount);


-- ==================== 2. EXPLAIN 分析 ====================

-- 2.1 基本 type 类型（性能从好到差）：
-- system > const > eq_ref > ref > range > index > ALL

-- 2.2 查看简单查询的执行计划
EXPLAIN SELECT * FROM products WHERE id = 1;
-- type: const（主键等值查询，最优）

-- 2.3 查看范围查询
EXPLAIN SELECT * FROM products WHERE price BETWEEN 1000 AND 10000;
-- type: range（范围扫描，良好）
-- 如果未创建索引则为 ALL（全表扫描，差）

-- 2.4 查看 JOIN 的执行计划
EXPLAIN SELECT
    o.order_no, u.username, o.total_amount
FROM orders o
JOIN users u ON o.user_id = u.id
WHERE o.status = 1;
-- 驱动表 orders 走 idx_user_status 索引
-- 被驱动表 users 走 PRIMARY 或 uk_username

-- 2.5 查看子查询的执行计划
EXPLAIN FORMAT=JSON
SELECT * FROM products
WHERE id IN (SELECT product_id FROM order_items WHERE quantity > 1);
-- FORMAT=JSON 可看到更详细的信息（成本估算、索引选择等）


-- ==================== 3. 索引优化实战 ====================

-- 3.1 未使用索引的查询（ALL 扫描）
EXPLAIN SELECT * FROM products WHERE description LIKE '%旗舰%';
-- 解决方案：使用 FULLTEXT 索引
EXPLAIN SELECT * FROM products WHERE MATCH(name, description) AGAINST('旗舰' IN NATURAL LANGUAGE MODE);

-- 3.2 复合索引最左前缀演示
-- 索引 idx_orders_user_status(user_id, status)
-- 走索引: WHERE user_id = 1 AND status = 1  ✓
-- 走索引: WHERE user_id = 1                  ✓
-- 不走索引: WHERE status = 1                  ✗（跳过了最左列）
EXPLAIN SELECT * FROM orders WHERE status = 1;
-- 如果必须查询 status 列，单独建一个 status 索引

-- 3.3 覆盖索引避免回表
-- 查询所需列全部在索引中，Extra 显示 "Using index"
EXPLAIN SELECT id, order_no, user_id, status
FROM orders
WHERE status = 1 AND user_id = 1;
-- Extra: Using index  ✓（覆盖索引）

-- 3.4 索引下推（Index Condition Pushdown, ICP）
-- MySQL 5.6+ 特性：在索引层过滤数据，减少回表次数
-- 复合索引 (status, total_amount) 时：
EXPLAIN SELECT * FROM orders
WHERE status = 1 AND total_amount > 5000;
-- Extra: Using index condition  ✓（ICP 生效）


-- ==================== 4. 索引维护 ====================

-- 4.1 查看表的索引
SHOW INDEX FROM orders;
-- Non_unique: 0=唯一索引 1=普通索引
-- Cardinality: 索引的基数（值越分散选择性越好）
-- Seq_in_index: 在复合索引中的位置

-- 4.2 查看索引使用统计
SELECT * FROM information_schema.INDEX_STATISTICS
WHERE TABLE_SCHEMA = 'ecommerce';

-- 4.3 分析表以更新索引统计信息
ANALYZE TABLE orders;

-- 4.4 重构索引（减少碎片）
ALTER TABLE orders ENGINE=InnoDB;

-- 4.5 删除冗余索引
-- DROP INDEX idx_orders_created_at ON orders;
-- DROP INDEX idx_orders_user_status ON orders;


-- ==================== 5. 索引设计原则总结 ====================
-- 1. 为 WHERE 条件、JOIN ON、ORDER BY、GROUP BY 的列创建索引
-- 2. 复合索引将选择性最高的列放在最左
-- 3. 避免在索引列上使用函数或运算（WHERE DATE(created_at) = ...）
-- 4. 小表不需要索引（全表扫描更快）
-- 5. 索引不是越多越好（影响 INSERT/UPDATE/DELETE 性能）
-- 6. 用 EXPLAIN 验证索引是否生效
-- 7. 监控慢查询日志，针对性优化