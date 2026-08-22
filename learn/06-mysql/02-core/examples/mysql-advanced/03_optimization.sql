-- ============================================
-- MySQL 查询优化实战
-- 演示：慢查询优化 / 分页优化 / 批量插入 / 分区表
-- ============================================

USE ecommerce;

-- ==================== 1. 慢查询优化 ====================

-- 1.1 开启慢查询日志
-- SET GLOBAL slow_query_log = ON;
-- SET GLOBAL long_query_time = 2;  -- 超过 2 秒记录
-- SET GLOBAL log_queries_not_using_indexes = ON;

-- 1.2 慢查询日志分析（shell 中执行）
-- mysqldumpslow /var/log/mysql/slow.log
-- pt-query-digest /var/log/mysql/slow.log

-- 1.3 优化案例：大表查询不走索引
-- 原始：WHERE YEAR(created_at) = 2026  → 函数包裹索引列，不走索引
-- 优化后：WHERE created_at >= '2026-01-01' AND created_at < '2027-01-01'
EXPLAIN SELECT * FROM orders
WHERE created_at >= '2026-01-01' AND created_at < '2027-01-01';

-- 1.4 优化案例：避免 SELECT *
-- 只查需要的列，配合覆盖索引
EXPLAIN SELECT id, order_no, total_amount FROM orders WHERE status = 1;

-- 1.5 优化案例：OR 改为 UNION
-- 原始：WHERE status = 0 OR status = 3  → 可能不走索引
-- 优化：
EXPLAIN SELECT * FROM orders WHERE status = 0
UNION ALL
SELECT * FROM orders WHERE status = 3;

-- 1.6 优化案例：NOT IN 改为 NOT EXISTS
-- 原始：WHERE id NOT IN (SELECT product_id FROM order_items)
-- 优化：
EXPLAIN SELECT p.* FROM products p
WHERE NOT EXISTS (SELECT 1 FROM order_items oi WHERE oi.product_id = p.id);


-- ==================== 2. 分页优化（大偏移量场景） ====================

-- 2.1 传统 LIMIT 分页的问题（偏移量越大越慢）
-- 问题：OFFSET 1000000 需要读取 1000000 行然后丢弃
EXPLAIN SELECT * FROM orders ORDER BY id LIMIT 10 OFFSET 1000000;

-- 2.2 优化方案 1：游标分页（基于上一页最后一条记录的 ID）
-- 适用于按顺序 ID 翻页
EXPLAIN SELECT * FROM orders
WHERE id > 1000000
ORDER BY id
LIMIT 10;

-- 2.3 优化方案 2：子查询延迟关联
-- 先快速查到需要的 ID，再回表查询完整数据
EXPLAIN SELECT o.* FROM orders o
INNER JOIN (
    SELECT id FROM orders
    ORDER BY id
    LIMIT 10 OFFSET 1000000
) AS tmp ON o.id = tmp.id;

-- 2.4 优化方案 3：覆盖索引 + JOIN
EXPLAIN SELECT o.* FROM orders o
JOIN (
    SELECT id FROM orders
    WHERE created_at > '2026-01-01'
    ORDER BY created_at
    LIMIT 10 OFFSET 100000
) AS tmp ON o.id = tmp.id;


-- ==================== 3. 批量插入优化 ====================

-- 3.1 单条插入 vs 批量插入
-- 差：逐条 INSERT（1000 次网络往返）
-- 好：一次 INSERT 多行（1 次网络往返）
INSERT INTO orders (order_no, user_id, total_amount, status, address) VALUES
('ORD202608220005', 1, 99.00, 0, '北京市'),
('ORD202608220006', 2, 199.00, 0, '上海市'),
('ORD202608220007', 3, 299.00, 0, '广州市');

-- 3.2 批量插入优化参数
-- 单条 INSERT 大小建议 100~1000 行
-- SET GLOBAL max_allowed_packet = 64M;  -- 增大允许的包大小

-- 3.3 使用事务包裹批量操作
-- 差：每条 INSERT 单独提交（1000 次 fsync）
-- 好：BEGIN ... 1000 条 INSERT ... COMMIT（1 次 fsync）
START TRANSACTION;
    INSERT INTO products (category_id, name, price, stock) VALUES
    (2, '商品A', 100, 1000),
    (2, '商品B', 200, 2000);
    -- 更多插入...
COMMIT;

-- 3.4 禁用索引和约束检查（仅限大量数据导入时）
-- ALTER TABLE products DISABLE KEYS;
-- SET UNIQUE_CHECKS = 0;
-- SET FOREIGN_KEY_CHECKS = 0;
-- -- 执行大量 INSERT...
-- SET FOREIGN_KEY_CHECKS = 1;
-- SET UNIQUE_CHECKS = 1;
-- ALTER TABLE products ENABLE KEYS;

-- 3.5 LOAD DATA 批量导入（比 INSERT 快 20 倍）
-- 需要文件在服务器本地
-- LOAD DATA INFILE '/tmp/products.csv'
-- INTO TABLE products
-- FIELDS TERMINATED BY ',' ENCLOSED BY '"'
-- LINES TERMINATED BY '\n'
-- (category_id, name, price, stock);


-- ==================== 4. 表分区 ====================

-- 4.1 RANGE 分区：按日期范围分区（适合日志表、订单表）
CREATE TABLE orders_partitioned (
    id BIGINT AUTO_INCREMENT,
    order_no VARCHAR(32) NOT NULL,
    user_id BIGINT NOT NULL,
    total_amount DECIMAL(12,2),
    status TINYINT,
    created_at DATETIME NOT NULL,
    PRIMARY KEY (id, created_at)  -- 分区列必须包含在主键中
) ENGINE=InnoDB
PARTITION BY RANGE (YEAR(created_at)) (
    PARTITION p2024 VALUES LESS THAN (2025),
    PARTITION p2025 VALUES LESS THAN (2026),
    PARTITION p2026 VALUES LESS THAN (2027),
    PARTITION p_future VALUES LESS THAN MAXVALUE
);

-- 4.2 LIST 分区：按枚举值分区（适合按状态）
CREATE TABLE orders_by_status (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_no VARCHAR(32),
    status TINYINT NOT NULL,
    created_at DATETIME
) ENGINE=InnoDB
PARTITION BY LIST (status) (
    PARTITION p_pending VALUES IN (0),
    PARTITION p_paid VALUES IN (1),
    PARTITION p_shipped VALUES IN (2),
    PARTITION p_done_cancelled VALUES IN (3, 4)
);

-- 4.3 HASH 分区：按用户 ID 哈希分布（适合均匀分布数据）
CREATE TABLE orders_by_hash (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_no VARCHAR(32),
    user_id BIGINT NOT NULL,
    total_amount DECIMAL(12,2),
    created_at DATETIME
) ENGINE=InnoDB
PARTITION BY HASH(user_id) PARTITIONS 8;

-- 4.4 分区修剪：查询只扫描相关分区
EXPLAIN SELECT * FROM orders_partitioned
WHERE created_at >= '2026-06-01' AND created_at < '2026-07-01';
-- partitions: p2026（只扫描该分区）


-- ==================== 5. 其他优化技巧 ====================

-- 5.1 使用 EXISTS 代替 COUNT(*) 判断存在性
-- 差：SELECT COUNT(*) FROM orders WHERE user_id = 1;  → 扫描计数
-- 好：SELECT EXISTS(SELECT 1 FROM orders WHERE user_id = 1);  → 找到即停

-- 5.2 合理使用 UNION ALL 代替 UNION
-- UNION 会有去重操作（排序），UNION ALL 没有
-- 如果知道结果不会重复，用 UNION ALL

-- 5.3 优化 ORDER BY + LIMIT
-- 为 ORDER BY 的列创建索引可以避免文件排序
CREATE INDEX idx_orders_created_at_status ON orders(created_at, status);
EXPLAIN SELECT * FROM orders
WHERE status = 1
ORDER BY created_at DESC
LIMIT 10;
-- Extra: Backward index scan  ✓（使用索引避免排序）

-- 5.4 优化 GROUP BY
-- GROUP BY 的列应有索引
CREATE INDEX idx_orders_user_status_total ON orders(user_id, status, total_amount);
EXPLAIN SELECT user_id, COUNT(*), SUM(total_amount)
FROM orders
WHERE status = 1
GROUP BY user_id;
-- Extra: Using index  ✓（覆盖索引，无临时表）