# 事务与 MVCC — ACID · 隔离级别 · 锁机制

> 👶→🎯 核心进阶 · 预计阅读时间：60 分钟

---

## 一、事务 ACID 特性

### 1.1 四大特性

| 特性 | 含义 | 实现机制 |
|------|------|----------|
| **A**tomicity（原子性） | 事务要么全部成功，要么全部回滚 | Undo Log |
| **C**onsistency（一致性） | 事务前后数据满足约束 | 应用层 + 数据库约束 |
| **I**solation（隔离性） | 事务之间互不干扰 | 锁 + MVCC |
| **D**urability（持久性） | 提交后数据永久保存 | Redo Log |

### 1.2 事务操作

```sql
-- 开启事务
START TRANSACTION;
-- 或 BEGIN;

-- 扣库存 + 创建订单
UPDATE inventory SET stock = stock - 1 WHERE product_id = 100 AND stock > 0;
INSERT INTO order_info (user_id, product_id, amount, status) VALUES (1, 100, 99.00, 0);

-- 提交
COMMIT;

-- 或回滚
ROLLBACK;
```

### 1.3 Redo Log 与 Undo Log

```
                    Redo Log（物理日志，记录页修改）
                    ┌──────────────────────────────┐
  事务提交 ────────→│ 写入 Redo Log Buffer         │
                    │   → 顺序写入磁盘 redo log     │
                    │   → 崩溃恢复时重放            │
                    └──────────────────────────────┘
                    Undo Log（逻辑日志，记录旧值）
                    ┌──────────────────────────────┐
  事务回滚 ────────→│ 记录修改前的数据             │
                    │   → MVCC 版本链              │
                    │   → 事务回滚数据恢复          │
                    └──────────────────────────────┘
```

**Redo Log 保证持久性**：事务提交时，Redo Log 先写入磁盘（WAL 机制），然后脏页异步刷盘。即使崩溃，重启时通过 Redo Log 重放即可恢复。

**Undo Log 保证原子性**：事务回滚时，通过 Undo Log 反向操作恢复数据到修改前。

---

## 二、隔离级别

### 2.1 并发事务的问题

| 问题 | 定义 | 示例 |
|------|------|------|
| 脏读 | 读到其他事务未提交的数据 | 事务 A 修改了金额未提交，事务 B 读到了修改后的值 |
| 不可重复读 | 同一事务内两次读取同一行数据，结果不同 | 事务 A 两次读金额，中间被事务 B 修改并提交 |
| 幻读 | 同一事务内两次查询，结果集行数不同 | 事务 A 查询订单列表，中间被事务 B 插入新订单 |

### 2.2 四种隔离级别

```sql
-- 查看当前隔离级别
SELECT @@transaction_isolation;

-- 设置隔离级别
SET SESSION TRANSACTION ISOLATION LEVEL READ COMMITTED;
```

| 隔离级别 | 脏读 | 不可重复读 | 幻读 | 默认数据库 |
|----------|------|------------|------|------------|
| READ UNCOMMITTED | 可能 | 可能 | 可能 | - |
| READ COMMITTED | 不会 | 可能 | 可能 | Oracle, PostgreSQL |
| REPEATABLE READ | 不会 | 不会 | 可能（InnoDB 通过间隙锁解决） | **MySQL** |
| SERIALIZABLE | 不会 | 不会 | 不会 | - |

### 2.3 MySQL 默认隔离级别：REPEATABLE READ

MySQL 的 REPEATABLE READ 通过**间隙锁（Gap Lock）**解决了幻读问题，所以实际上 MySQL 的 RR 隔离级别等价于其他数据库的 SERIALIZABLE 级别（在幻读方面）。

| 对比 | MySQL RR | 标准 RR |
|------|----------|---------|
| 幻读 | 解决（间隙锁 + MVCC） | 可能 |
| 实现 | MVCC + 间隙锁 | MVCC 仅 |
| 性能 | 略低于 RC | - |

---

## 三、MVCC 原理

### 3.1 核心概念

MVCC（Multi-Version Concurrency Control）通过多版本并发控制，实现**读不阻塞写，写不阻塞读**。

### 3.2 隐藏字段

InnoDB 每行数据有三个隐藏字段：

| 字段 | 大小 | 说明 |
|------|------|------|
| DB_TRX_ID | 6 字节 | 最近修改此行的事务 ID |
| DB_ROLL_PTR | 7 字节 | 回滚指针，指向 Undo Log 中的上一个版本 |
| DB_ROW_ID | 6 字节 | 隐藏主键（没有显式主键时使用） |

### 3.3 Undo Log 版本链

```
事务 100 插入：     DB_TRX_ID=100, DB_ROLL_PTR=NULL  → 原始版本
事务 200 修改：     DB_TRX_ID=200, DB_ROLL_PTR ──→ 版本1（TRX_ID=100）
事务 300 修改：     DB_TRX_ID=300, DB_ROLL_PTR ──→ 版本2（TRX_ID=200）
                    ↓
              Undo Log 链：v300 → v200 → v100
```

### 3.4 Read View 可见性规则

**Read View 结构：**

| 字段 | 含义 |
|------|------|
| creator_trx_id | 创建 Read View 的事务 ID |
| m_ids | 活跃事务 ID 列表（未提交的） |
| min_trx_id | 活跃事务中最小的事务 ID |
| max_trx_id | 下一个要分配的事务 ID（最大事务 + 1） |

**可见性判断：**

```
DB_TRX_ID < min_trx_id   → 版本可见（事务已提交）
DB_TRX_ID >= max_trx_id  → 版本不可见（事务在未来）
DB_TRX_ID 在 m_ids 中   → 版本不可见（事务未提交）
DB_TRX_ID 不在 m_ids 中 → 版本可见（事务已提交）
```

### 3.5 REPEATABLE READ 实现

**RR 级别下，事务只在第一次查询时创建 Read View，之后复用。**

```
事务 A: 第一次 SELECT → 创建 Read View (m_ids=[100, 200])
事务 A: 第二次 SELECT → 复用同一个 Read View

所以不管其他事务是否提交，事务 A 看到的数据始终不变。
```

**RC 级别下，每次 SELECT 都创建新的 Read View。**

```
事务 A: 第一次 SELECT → Read View 1 (m_ids=[100])
事务 A: 第二次 SELECT → Read View 2 (m_ids=[])  ← 事务 100 已提交

所以两次可能看到不同结果（不可重复读）。
```

### 3.6 MVCC 整体流程

```
                    ┌──────────────┐
                    │   查询请求    │
                    └──────┬───────┘
                           │
                    ┌──────▼───────┐
                    │ 创建 Read View│
                    └──────┬───────┘
                           │
                    ┌──────▼───────┐
                    │ 遍历版本链    │
                    └──────┬───────┘
                           │
              ┌────────────┼────────────┐
              │            │            │
         ┌────▼───┐  ┌────▼───┐  ┌────▼───┐
         │ 可见   │  │ 不可见 │  │ 不可见 │
         │ 返回   │  │ 继续找 │  │ 继续找 │
         └────────┘  └────────┘  └────────┘
```

---

## 四、锁机制

### 4.1 锁类型概览

| 锁类型 | 粒度 | 说明 |
|--------|------|------|
| 行锁（Record Lock） | 行 | 锁住索引记录 |
| 间隙锁（Gap Lock） | 间隙 | 锁住索引之间的间隙 |
| 临键锁（Next-Key Lock） | 行+间隙 | 行锁 + 间隙锁的组合 |
| 表锁 | 表 | 锁住整张表 |
| 意向锁 | 表 | 表明事务要加行锁 |

### 4.2 行锁

```sql
-- 行锁加锁方式
SELECT * FROM product WHERE id = 1 FOR UPDATE;  -- 排他锁（X 锁）
SELECT * FROM product WHERE id = 1 LOCK IN SHARE MODE;  -- 共享锁（S 锁）

-- 共享锁和排他锁的兼容性
-- S 锁和 S 锁兼容（都可读）
-- S 锁和 X 锁互斥（读阻塞写）
-- X 锁和 X 锁互斥（写阻塞写）
```

### 4.3 间隙锁（Gap Lock）

间隙锁锁的是**索引记录之间的间隙**，用于解决幻读。

```sql
-- 假设 product 表 id 有：1, 5, 10
-- 间隙锁会锁住以下间隙：(-∞, 1), (1, 5), (5, 10), (10, +∞)

-- 以下查询会给 (5, 10) 间隙加锁，防止插入 id=7 的记录
SELECT * FROM product WHERE id = 7 FOR UPDATE;
```

**间隙锁的触发条件：**
- 隔离级别为 REPEATABLE READ（或 SERIALIZABLE）
- 使用普通索引或范围查询
- 唯一索引等值查询且记录不存在时，也会加间隙锁

### 4.4 临键锁（Next-Key Lock）

临键锁 = 行锁 + 间隙锁，是 InnoDB RR 级别的默认锁策略。

```
索引记录：1, 5, 10
临键锁范围：(-∞, 1], (1, 5], (5, 10], (10, +∞)

查询 id > 3 FOR UPDATE 会锁住：
- (1, 5] 的间隙锁
- 5 的行锁
- (5, 10] 的间隙锁
- 10 的行锁
- (10, +∞) 的间隙锁
```

### 4.5 意向锁（Intention Lock）

意向锁是表级锁，用于快速判断表中有没有行锁：

```sql
-- 事务 A 加行锁
BEGIN;
SELECT * FROM product WHERE id = 1 FOR UPDATE;
-- 此时自动给 product 表加意向排他锁（IX）

-- 事务 B 想加表锁
LOCK TABLES product WRITE;
-- 发现意向排他锁存在，快速判断表中有行锁，等待
```

### 4.6 表锁

```sql
-- 手动加表锁（不常用，InnoDB 自动使用行锁）
LOCK TABLES product READ;   -- 读锁，其他会话可读不可写
LOCK TABLES product WRITE;  -- 写锁，其他会话不可读写
UNLOCK TABLES;
```

---

## 五、死锁分析与排查

### 5.1 死锁经典场景

```
事务 A：                             事务 B：
UPDATE product SET stock=9 WHERE id=1;  UPDATE product SET stock=9 WHERE id=2;
UPDATE product SET stock=9 WHERE id=2;  UPDATE product SET stock=9 WHERE id=1;
```

**死锁产生条件：**
1. 互斥（一个资源只能被一个事务持有）
2. 持有并等待（事务持有资源并等待其他资源）
3. 不可剥夺（资源只能由持有者释放）
4. 循环等待（A 等 B，B 等 A）

### 5.2 死锁排查

```sql
-- 查看最近死锁
SHOW ENGINE INNODB STATUS\G
-- 关注 LATEST DETECTED DEADLOCK 部分

-- 查看当前锁等待
SELECT * FROM performance_schema.data_lock_waits\G
```

### 5.3 死锁预防

1. **固定顺序访问**：所有事务按相同顺序更新资源
2. **缩短事务**：不要在一个事务中做太多操作
3. **降低隔离级别**：RC 级别下间隙锁少，死锁概率低
4. **使用索引**：无索引时行锁升级为表锁，死锁概率大增
5. **重试机制**：捕获死锁异常后重试

---

## 总结

| 知识点 | 一句话概括 |
|--------|-----------|
| ACID | 原子性、一致性、隔离性、持久性 |
| 隔离级别 | 读未提交 < 读已提交 < 可重复读 < 串行化 |
| MVCC | 通过隐藏字段 + Undo Log 版本链 + Read View 实现 |
| 行锁 | 锁住单行记录 |
| 间隙锁 | 锁住索引间隙，防止幻读 |
| 临键锁 | 行锁 + 间隙锁，RR 默认 |
| 死锁 | 四个必要条件同时满足时发生 |

> 下一步：[03-sql-optimization.md](./03-sql-optimization.md) — SQL 优化实战