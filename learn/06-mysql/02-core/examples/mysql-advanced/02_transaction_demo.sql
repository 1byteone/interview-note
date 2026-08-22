-- ============================================
-- MySQL 事务隔离级别与 MVCC 演示
-- 注意：每个部分需要在不同会话中执行
-- 建议使用两个终端连接同一数据库对比
-- ============================================

USE ecommerce;

-- ==================== 1. 事务基础 ====================

-- 查看当前事务隔离级别
SHOW VARIABLES LIKE 'transaction_isolation';
-- 默认: REPEATABLE-READ（MySQL InnoDB 默认）

-- 设置当前会话隔离级别
SET SESSION TRANSACTION ISOLATION LEVEL READ UNCOMMITTED;
SET SESSION TRANSACTION ISOLATION LEVEL READ COMMITTED;
SET SESSION TRANSACTION ISOLATION LEVEL REPEATABLE READ;
SET SESSION TRANSACTION ISOLATION LEVEL SERIALIZABLE;

-- 典型事务
START TRANSACTION;
    UPDATE products SET stock = stock - 1 WHERE id = 1;
    INSERT INTO orders (order_no, user_id, total_amount, status, address)
    VALUES ('ORD202608220004', 1, 6999.00, 0, '北京市朝阳区');
    -- 业务操作...
COMMIT;
-- 或 ROLLBACK;


-- ==================== 2. 隔离级别演示 ====================

-- 准备数据
-- SET SESSION TRANSACTION ISOLATION LEVEL READ UNCOMMITTED;

-- 会话 A                    | 会话 B
-- BEGIN;                    | BEGIN;
-- SELECT stock FROM products|
-- WHERE id=1; -- 100        |
--                            | UPDATE products SET stock=50 WHERE id=1;
-- SELECT stock FROM products|
-- WHERE id=1; -- 50 (脏读)  |
--                            | ROLLBACK;
-- COMMIT;                   |

-- READ UNCOMMITTED 的问题：脏读（读到未提交的数据）
-- READ COMMITTED 解决脏读，但出现不可重复读
-- REPEATABLE READ 解决不可重复读（MySQL 默认，通过 MVCC 实现）
-- SERIALIZABLE 解决幻读（性能最差，实际很少用）


-- ==================== 3. MVCC 演示 ====================

-- MVCC（多版本并发控制）通过 undo log 实现：
-- - 每行数据有隐藏的 DB_TRX_ID（事务ID）和 DB_ROLL_PTR（回滚指针）
-- - 事务开始时创建 ReadView（活跃事务列表）
-- - 读取时只读取 ReadView 创建前已提交的数据版本

-- 查看 InnoDB 事务状态（需要 root 或 PROCESS 权限）
SHOW ENGINE INNODB STATUS\G;

-- 查看当前运行中的事务
SELECT * FROM information_schema.INNODB_TRX\G;

-- 查看锁等待信息
SELECT * FROM information_schema.INNODB_LOCK_WAITS\G;


-- ==================== 4. 锁监控 ====================

-- 4.1 查看当前锁情况
SELECT
    trx_id,
    trx_state,
    trx_started,
    trx_mysql_thread_id,
    trx_query,
    trx_operation_state,
    trx_tables_in_use,
    trx_lock_structs
FROM information_schema.INNODB_TRX;

-- 4.2 查看锁等待
SELECT
    r.trx_id AS waiting_trx_id,
    r.trx_mysql_thread_id AS waiting_thread,
    b.trx_id AS blocking_trx_id,
    b.trx_mysql_thread_id AS blocking_thread,
    b.trx_query AS blocking_query
FROM information_schema.INNODB_LOCK_WAITS w
JOIN information_schema.INNODB_TRX r ON w.requesting_trx_id = r.trx_id
JOIN information_schema.INNODB_TRX b ON w.blocking_trx_id = b.trx_id;


-- ==================== 5. 死锁检测与处理 ====================

-- 死锁示例（两个事务互相等待对方持有的锁）：
-- 会话 A                          | 会话 B
-- BEGIN;                          | BEGIN;
-- UPDATE products SET stock=50    | UPDATE orders SET status=2
--   WHERE id=1;                   |   WHERE id=1;
--                                  | UPDATE products SET stock=40
-- UPDATE orders SET status=1      |   WHERE id=1;
--   WHERE id=1; -- 死锁！         | -- 死锁！
-- 出错：Deadlock found when trying to get lock
-- MySQL 自动选择其中一个事务回滚，释放锁

-- 预防死锁的建议：
-- 1. 所有事务按相同顺序访问表（如：先 products 再 orders）
-- 2. 尽量缩短事务时间
-- 3. 用低隔离级别减少锁竞争
-- 4. 合理设计索引，减少锁范围

-- 查看最近的死锁日志
SHOW ENGINE INNODB STATUS\G;
-- 关注 LATEST DETECTED DEADLOCK 部分


-- ==================== 6. 显式锁 ====================

-- 6.1 行锁（SELECT ... FOR UPDATE）
-- 悲观锁：锁定读取的行，防止其他事务修改
START TRANSACTION;
    SELECT stock FROM products WHERE id = 1 FOR UPDATE;
    -- 其他事务要修改 id=1 的行会被阻塞
    UPDATE products SET stock = stock - 1 WHERE id = 1;
COMMIT;

-- 6.2 行锁（SELECT ... FOR SHARE，即 LOCK IN SHARE MODE）
-- 共享锁：允许其他事务读，但阻止修改
START TRANSACTION;
    SELECT * FROM products WHERE id = 1 FOR SHARE;
    -- 其他事务可以读，但 FOR UPDATE 或 UPDATE 会被阻塞
COMMIT;

-- 6.3 表锁（InnoDB 不推荐，MyISAM 常用）
-- LOCK TABLES products READ;
-- LOCK TABLES products WRITE;
-- UNLOCK TABLES;

-- 6.4 间隙锁（Gap Lock，REPEATABLE READ 时存在）
-- 防止幻读：锁定索引范围，阻止插入新行
-- 示例：WHERE id BETWEEN 10 AND 20 FOR UPDATE 会锁定此范围