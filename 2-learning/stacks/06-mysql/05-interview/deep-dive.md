# 深挖题 — MVCC 源码级 · B+Tree 层数 · 行锁加锁规则

> 🎯 面试深挖 · 大厂面试杀手锏

---

## 一、MVCC 源码级解析

### 1.1 Read View 源码结构

MySQL 8.0 源码中 Read View 的核心结构（`read_view.h`）：

```cpp
class ReadView {
private:
    trx_id_t m_low_limit_id;     // 大于这个值的事务不可见（max_trx_id）
    trx_id_t m_up_limit_id;      // 小于这个值的事务可见（min_trx_id）
    trx_id_t m_creator_trx_id;   // 创建 Read View 的事务 ID
    ids_t m_ids;                 // 活跃事务 ID 列表（有序集合）
    bool m_closed;               // Read View 是否已关闭
};
```

### 1.2 可见性判断源码

```cpp
// 判断某行版本是否可见
bool changes_visible(trx_id_t id) const {
    // 1. 事务 ID 比 Read View 中最小的活跃事务还小 → 已提交，可见
    if (id < m_up_limit_id) {
        return true;
    }
    // 2. 事务 ID 大于等于 Read View 中最大的事务 → 不可见（未来事务）
    if (id >= m_low_limit_id) {
        return false;
    }
    // 3. 事务 ID 在活跃事务列表中 → 不可见（未提交）
    if (m_ids.end() != std::find(m_ids.begin(), m_ids.end(), id)) {
        return false;
    }
    // 4. 事务 ID 不在活跃列表中 → 已提交，可见
    return true;
}
```

### 1.3 RR 与 RC 的关键区别

**REPEATABLE READ 的 Read View 创建时机：**

```cpp
// 事务第一次执行 SELECT 时创建 Read View
// 后续所有 SELECT 复用同一个 Read View
if (trx->read_view == nullptr) {
    trx->read_view = open_read_view(trx);
}
```

**READ COMMITTED 的 Read View 创建时机：**

```cpp
// 每次 SELECT 都创建新的 Read View
// 每次都能看到已提交事务的最新数据
close_read_view(trx->read_view);
trx->read_view = open_read_view(trx);
```

### 1.4 快照读 vs 当前读

```sql
-- 快照读（Snapshot Read）：读取 MVCC 版本链中的可见版本，不加锁
SELECT * FROM product WHERE id = 1;

-- 当前读（Current Read）：读取最新版本，加锁
SELECT * FROM product WHERE id = 1 FOR UPDATE;      -- 排他锁
SELECT * FROM product WHERE id = 1 LOCK IN SHARE MODE;  -- 共享锁
UPDATE product SET price = 100 WHERE id = 1;        -- 先当前读，再修改
DELETE FROM product WHERE id = 1;                    -- 先当前读，再删除
INSERT INTO product VALUES (...);                    -- 写入新版本
```

**当前读的执行流程：**

```
1. 加锁（行锁/间隙锁/临键锁）
2. 读取最新版本（不走 MVCC 版本链）
3. 执行修改操作
4. 生成新版本（写入 Undo Log）
```

---

## 二、B+Tree 层数精确计算

### 2.1 计算模型

**已知参数：**
- InnoDB 页大小：16KB（16384 字节）
- 主键类型：BIGINT（8 字节）
- 指针大小：6 字节（InnoDB 文件页指针）
- 每行数据大小：约 1KB（1024 字节）

### 2.2 非叶子节点容量

```
每个索引条目 = 主键(8B) + 指针(6B) = 14B
每页可存索引条目数 = 16384 / 14 = 1170 个
```

### 2.3 叶子节点容量

```
每行数据 = 1KB = 1024B
每页可存数据行数 = 16384 / 1024 = 16 行
```

### 2.4 层数与数据量

| 层数 | 计算公式 | 最大数据量 | 说明 |
|------|----------|-----------|------|
| 1 层 | 16 | 16 行 | 根节点就是叶子节点（小表） |
| 2 层 | 1170 * 16 | 18,720 行 | 约 2 万行 |
| 3 层 | 1170 * 1170 * 16 | 21,902,400 行 | 约 2190 万行 |
| 4 层 | 1170^3 * 16 | 25,625,808,000 行 | 约 256 亿行 |

### 2.5 实际场景分析

**场景 1：订单表 5000 万行**
- 主键 BIGINT，每行 500 字节
- 非叶子节点：1170 个 / 页
- 叶子节点：16384 / 500 = 32 行 / 页
- 3 层 B+Tree 容量：1170 * 1170 * 32 = 43,804,800 行
- 5000 万行需要 4 层，但多数行在 3 层即可覆盖

**场景 2：百万级商品表**
- 主键 BIGINT，每行 200 字节
- 叶子节点：16384 / 200 = 81 行 / 页
- 3 层 B+Tree 容量：1170 * 1170 * 81 = 110,916,900 行
- 百万级数据 3 层足够，查询只需 3 次 IO

### 2.6 面试回答要点

> "B+Tree 3 层能支撑约 2000 万行数据，计算逻辑是：非叶子节点每页存 1170 个 key（16KB/14B），叶子节点每页存 16 行（16KB/1KB），三层就是 1170*1170*16。实际场景中，行越小层数越少，所以百万级数据通常 3 次 IO 就能查到。"

---

## 三、行锁加锁规则

### 3.1 加锁规则（基于 MySQL 8.0）

**规则 1：主键等值查询**
- 记录存在 → 只加行锁
- 记录不存在 → 加间隙锁

```sql
-- id = 5 存在 → 只锁 id=5 的行
SELECT * FROM t WHERE id = 5 FOR UPDATE;

-- id = 7 不存在（假设 id 有 5, 10）→ 锁间隙 (5, 10)
SELECT * FROM t WHERE id = 7 FOR UPDATE;
```

**规则 2：主键范围查询**
- 加临键锁（行锁 + 间隙锁）

```sql
-- 锁住 id >= 5 的所有行，以及间隙 (5, +∞)
SELECT * FROM t WHERE id >= 5 FOR UPDATE;
```

**规则 3：普通索引等值查询**
- 加临键锁
- 最后会加一个间隙锁防止幻读

```sql
-- 假设 name 索引有 'Alice', 'Bob', 'Charlie'
-- 查询 'Bob' → 加临键锁 (Alice, Bob] 和间隙锁 (Bob, Charlie)
SELECT * FROM t WHERE name = 'Bob' FOR UPDATE;
```

**规则 4：普通索引范围查询**
- 加多个临键锁

```sql
-- 查询 name > 'Bob' → 加临键锁 (Bob, Charlie], (Charlie, +∞)
SELECT * FROM t WHERE name > 'Bob' FOR UPDATE;
```

**规则 5：无索引查询**
- 升级为表锁（所有行都加锁）

```sql
-- 如果 status 没有索引 → 锁住整张表
SELECT * FROM t WHERE status = 1 FOR UPDATE;
```

### 3.2 加锁实战分析

```sql
-- 表结构：t(id PK, name KEY, age)
-- 数据：id=1,name='Alice',age=20; id=5,name='Bob',age=30; id=10,name='Charlie',age=40

-- 案例 1：主键等值查询（存在）
SELECT * FROM t WHERE id = 5 FOR UPDATE;
-- 锁：行锁 id=5

-- 案例 2：主键等值查询（不存在）
SELECT * FROM t WHERE id = 7 FOR UPDATE;
-- 锁：间隙锁 (5, 10)

-- 案例 3：普通索引等值查询
SELECT * FROM t WHERE name = 'Bob' FOR UPDATE;
-- 锁：临键锁 ('Alice', 'Bob'] + 间隙锁 ('Bob', 'Charlie')
-- 注意：即使只查一行，间隙锁也会锁住相邻间隙

-- 案例 4：范围查询（普通索引）
SELECT * FROM t WHERE name >= 'Bob' FOR UPDATE;
-- 锁：临键锁 ('Alice', 'Bob'] + 临键锁 ('Bob', 'Charlie'] + 临键锁 ('Charlie', +∞)
-- 所有 >= 'Bob' 的记录及其间隙都被锁住
```

### 3.3 死锁案例分析

```sql
-- 事务 A：
BEGIN;
SELECT * FROM t WHERE id = 5 FOR UPDATE;  -- 获得行锁 id=5
SELECT * FROM t WHERE id = 10 FOR UPDATE; -- 等待事务 B 释放 id=10

-- 事务 B：
BEGIN;
SELECT * FROM t WHERE id = 10 FOR UPDATE; -- 获得行锁 id=10
SELECT * FROM t WHERE id = 5 FOR UPDATE;  -- 等待事务 A 释放 id=5

-- 死锁发生！MySQL 检测到死锁，回滚其中一个事务
```

---

## 四、深度面试题

### 4.1 MVCC 相关问题

**Q：RR 级别下，事务 A 更新了某行，事务 B 能读到更新后的值吗？**

A：事务 B 使用快照读（SELECT）读不到，因为 RR 复用第一次的 Read View；但事务 B 使用当前读（SELECT FOR UPDATE / UPDATE）可以读到，因为当前读读取最新版本。

**Q：MVCC 能完全解决幻读吗？**

A：MVCC 只能解决快照读的幻读，不能解决当前读的幻读。当前读的幻读需要间隙锁来解决。MySQL RR 级别通过 MVCC + 间隙锁的配合，完全解决了幻读问题。

### 4.2 B+Tree 相关问题

**Q：为什么选择 B+Tree 而不是 B-Tree？**

A：B+Tree 非叶子节点不存数据，每页可存更多 key，树高更低；叶子节点有链表指针，范围查询只需遍历链表；B-Tree 的每个节点都存数据，树高更高，范围查询需要中序遍历。

**Q：自增主键和 UUID 主键的区别？**

A：自增主键写入时数据页顺序写入，页分裂少；UUID 主键无序，写入时频繁页分裂，性能差。自增主键占用 4-8 字节，UUID 占用 36 字节，索引空间更大。

### 4.3 锁相关问题

**Q：如何排查死锁？**

A：`SHOW ENGINE INNODB STATUS` 查看最近死锁，关注 LATEST DETECTED DEADLOCK 部分，分析事务的等待顺序；或者在业务日志中捕获 DeadlockLoserDataAccessException，记录当时的 SQL 和参数。

**Q：MDL 锁是什么？**

A：MDL（Meta Data Lock），元数据锁，保护表结构定义。DDL 操作需要 MDL 写锁，DML 操作需要 MDL 读锁。MDL 写锁与读锁互斥，所以 DDL 会阻塞 DML。

---

> 下一步：[scenario.md](./scenario.md) — 场景题实战