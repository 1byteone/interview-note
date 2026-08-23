# 面试速记版 — 各模块 10 个考点

> 🎯 面试冲刺 · 5 分钟快速回顾

---

## 一、索引（10 个考点）

1. **B+Tree 特点**：多路平衡树，非叶子节点只存 key，叶子节点存数据+链表指针，3-4 层支撑亿级数据
2. **B+Tree vs B-Tree**：B+Tree 非叶子节点不存数据（可存更多 key），叶子节点有链表（范围查询高效）
3. **聚簇索引**：主键索引，叶子节点存完整行数据，每张表只有一个
4. **非聚簇索引**：二级索引，叶子节点存主键值，查询需要回表
5. **覆盖索引**：查询列都在索引中，无需回表，Extra 显示 Using index
6. **最左前缀原则**：联合索引 (a,b,c)，查询从最左列开始匹配，跳过的列后面的索引失效
7. **索引失效场景**：函数操作、隐式类型转换、LIKE 前模糊、OR 非索引列、NOT IN、运算操作
8. **索引下推（ICP）**：MySQL 5.6+，在索引遍历过程中过滤数据，减少回表
9. **前缀索引**：`INDEX(name(10))`，对大字段建前缀索引节省空间
10. **索引设计原则**：高区分度放前面，查询频繁的先建，更新频繁的谨慎建，索引数控制在 5 个以内

---

## 二、事务（10 个考点）

1. **ACID**：原子性（Undo Log）、一致性（约束）、隔离性（锁+MVCC）、持久性（Redo Log）
2. **脏读**：读到其他事务未提交的数据（RC 解决）
3. **不可重复读**：一行数据两次读取结果不同（RR 解决）
4. **幻读**：两次查询结果集行数不同（间隙锁解决）
5. **隔离级别**：读未提交→读已提交→可重复读→串行化，依次解决脏读、不可重复读、幻读
6. **MySQL 默认隔离级别**：REPEATABLE READ，通过间隙锁解决了幻读
7. **Redo Log**：物理日志，WAL（Write Ahead Log）机制，保证持久性
8. **Undo Log**：逻辑日志，记录修改前的数据，用于回滚和 MVCC
9. **事务传播行为**：REQUIRED（默认）、REQUIRES_NEW、NESTED、SUPPORTS 等
10. **事务失效场景**：@Transactional 加在非 public 方法、自调用、异常被 catch、不同数据源

---

## 三、MVCC（10 个考点）

1. **MVCC 全称**：Multi-Version Concurrency Control，多版本并发控制
2. **核心组件**：隐藏字段（DB_TRX_ID, DB_ROLL_PTR）+ Undo Log 版本链 + Read View
3. **DB_TRX_ID**：最近修改此行的事务 ID
4. **DB_ROLL_PTR**：回滚指针，指向 Undo Log 中的上一个版本
5. **Read View 结构**：creator_trx_id（当前事务 ID）、m_ids（活跃事务列表）、min_trx_id、max_trx_id
6. **可见性规则**：TRX_ID < min_trx_id 可见；TRX_ID >= max_trx_id 不可见；TRX_ID 在 m_ids 中不可见
7. **RR 实现**：事务只在第一次 SELECT 时创建 Read View，之后复用 → 可重复读
8. **RC 实现**：每次 SELECT 都创建新的 Read View → 不可重复读
9. **快照读 vs 当前读**：快照读（普通 SELECT 走 MVCC）；当前读（SELECT FOR UPDATE / UPDATE / DELETE 走锁）
10. **MVCC 解决了什么问题**：读不阻塞写，写不阻塞读，提高并发

---

## 四、锁（10 个考点）

1. **行锁（Record Lock）**：锁住单行索引记录，InnoDB 默认行锁
2. **间隙锁（Gap Lock）**：锁住索引之间的间隙，防止幻读，RR 级别生效
3. **临键锁（Next-Key Lock）**：行锁 + 间隙锁，RR 级别默认锁策略
4. **表锁**：LOCK TABLES 手动加锁，InnoDB 自动使用行锁
5. **意向锁**：表级锁，IX（意向排他锁）/ IS（意向共享锁），快速判断表中有无行锁
6. **共享锁（S 锁）**：SELECT ... LOCK IN SHARE MODE，可读不可写，S 锁之间兼容
7. **排他锁（X 锁）**：SELECT ... FOR UPDATE，不可读写，X 锁之间互斥
8. **死锁四个条件**：互斥、持有并等待、不可剥夺、循环等待
9. **死锁排查**：`SHOW ENGINE INNODB STATUS` 查看最近死锁
10. **死锁预防**：固定顺序访问、缩短事务、降低隔离级别、使用索引

---

## 五、优化（10 个考点）

1. **Explain type 字段**：system > const > eq_ref > ref > range > index > ALL
2. **Explain Extra 字段**：Using index（覆盖索引）、Using filesort（文件排序）、Using temporary（临时表）
3. **慢查询排查**：开启慢查询日志→分析慢查询→Explain 分析→索引优化→验证
4. **分页优化**：游标分页（WHERE id > last_id）替代 LIMIT 大偏移量
5. **覆盖索引**：索引包含所有查询字段，避免回表
6. **批量操作**：批量 INSERT（一条 VALUES 多行）、批量 UPDATE（CASE WHEN）
7. **小表驱动大表**：EXISTS 优于 IN（当外层表小时）
8. **主从延迟**：关键数据读主库、并行复制、缓存补偿
9. **连接池配置**：HikariCP，maximum-pool-size 建议 10-20
10. **SQL 优化 Checklist**：走索引了吗？扫描行数合理吗？有 filesort 吗？有覆盖索引吗？

---

## 六、分库分表（10 个考点）

1. **垂直分库**：按业务拆分到不同数据库（用户库、订单库、商品库）
2. **水平分表**：按分片键拆分到不同表（order_0 ~ order_15）
3. **分片键选择**：均匀分布、查询友好、不易变更
4. **取模分片**：shard = key % N，实现简单，扩容困难
5. **范围分片**：按时间/ID 范围分片，扩容方便，可能数据倾斜
6. **ShardingSphere**：JDBC 层透明分库分表，对应用代码无侵入
7. **跨分片查询**：广播到所有分片，结果集合并排序
8. **分布式事务**：Seata AT 模式、TCC、可靠消息最终一致性
9. **全局 ID**：雪花算法（Snowflake），64 位 long，趋势递增
10. **主从复制**：Master 写 binlog → Slave I/O 线程拉取 → Slave SQL 线程回放

---

> 下一步：[deep-dive.md](./deep-dive.md) — 深挖题：MVCC 源码级、B+Tree 层数计算