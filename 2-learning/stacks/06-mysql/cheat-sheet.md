# MySQL 速查卡 📋

> 面试前30分钟扫一遍，一页纸覆盖核心考点

## 🏷️ 核心概念速记

| 概念 | 一句话解释 | 常见陷阱 |
|------|-----------|----------|
| B+ 树 | 多路平衡查找树，非叶子只存键+指针，叶子存数据并串成链表 | B+ 树矮胖、范围查询友好、IO 次数 = 树高(3-4层) |
| 聚簇索引 | InnoDB 主键索引，叶子节点存整行数据，表即索引 | 主键推荐自增/单调，UUID 主键会导致页分裂 |
| 二级索引 | 非主键索引，叶子存主键值（回表查询要再走聚簇索引） | 避免不可控回表：覆盖索引/联合索引优化 |
| 索引失效 | 函数运算、隐式类型转换、左模糊 %xx、联合索引最左匹配违例 | != / <> / not in / is null 等并不总是失效，要具体分析 |
| 事务 (ACID) | 原子性、一致性、隔离性、持久性 | 一致性是目标，其它三性是手段 |
| 隔离级别 | READ UNCOMMITTED / RC(读已提交) / RR(默认) / SERIALIZABLE | 可重复读在 MySQL 用 MVCC 实现，不是锁实现的 |
| MVCC | 多版本并发控制，读不加锁，通过 undo log 版本链 + ReadView | RR 的 ReadView 生成时机是事务第一次读，RC 是每条 SQL |
| 快照读 vs 当前读 | 快照读(SELECT)走 MVCC；当前读(UPDATE/DELETE/锁读)走最新版本 | 快照读与当前读混用会幻读，gap 锁只锁索引区间 |
| 幻读 (Phantom) | 同一查询两次结果行数不同 | InnoDB RR 下 MVCC 解决快照读幻读，间隙锁/临键锁解决当前读幻读 |
| 死锁 | 两个事务互持对方想要的行锁 | 加锁顺序一致 + 超时 innodb_lock_wait_timeout 兜底 |
| 间隙锁 | 锁住索引范围(含区间)，防止插入 | 只在 RR 隔离级别存在，会阻塞其他事务插入 |
| 分布式事务 (分库分表) | XA(2PC)、TCC(Seata)、本地消息表、SAGA | 分库分表后本地事务失效，分布式事务是难点 |
| 主从复制 | binlog → relay log → SQL，主库写从库读 | binlog 格式：Statement(函数不确定)、Row(默认)、Mixed |

## 🔧 常用命令/API

```sql
-- EXPLAIN 输出解读（重点）
EXPLAIN SELECT * FROM t_user WHERE name = 'tom' AND age > 20;
-- id           查询编号
-- select_type   SIMPLE / PRIMARY / SUBQUERY / DERIVED
-- type         访问类型: system > const > eq_ref > ref > range > index > ALL(全表扫描)
-- possible_keys 可能使用的索引
-- key          实际使用的索引
-- key_len      使用的索引长度(与字段类型/字符集相关)
-- ref          关联引用列
-- rows         预估扫描行数(越小越好)
-- Extra        关键信息: Using index(覆盖索引/无需回表) / Using index condition(5.6 ICP) /
--               Using where(过滤后回表) / Using filesort(文件排序=慢!) / Using temporary(临时表)
```

```sql
-- 索引优化语句
CREATE INDEX idx_name_age ON t_user(name, age);   -- 最左前缀：name/name+age 可用，age 单独不可用
DROP INDEX idx_name_age ON t_user;

-- 分页优化：延迟关联 + 覆盖索引
-- ❌ 深分页: SELECT * FROM t ORDER BY id LIMIT 100000, 20;  -- 扫描10万行
-- ✅ 优化:    SELECT t.* FROM t
--             JOIN (SELECT id FROM t ORDER BY id LIMIT 100000, 20) tmp
--             ON t.id = tmp.id;
```

```sql
-- 窗口函数 (MySQL 8.0+)
SELECT
  name, salary,
  ROW_NUMBER() OVER (PARTITION BY dept ORDER BY salary DESC) AS rn,  -- 排名(不并列)
  RANK()       OVER (PARTITION BY dept ORDER BY salary DESC) AS rk,  -- 排名(并列占位)
  DENSE_RANK() OVER (PARTITION BY dept ORDER BY salary DESC) AS drk, -- 排名(并列不占位)
  SUM(salary)  OVER (PARTITION BY dept) AS dept_total,
  LAG(salary)  OVER (ORDER BY id) AS prev_salary
FROM emp;
-- 取出每组前3: 子查询包一层 WHERE rn <= 3
```

```sql
-- 生产事故：慢查询定位
SHOW PROCESSLIST;                          -- 看当前连接和慢 SQL
SHOW VARIABLES LIKE '%slow%';              -- slow_query_log 开关
EXPLAIN ANALYZE SELECT ...;                -- 8.0 实测执行路径和时间
```

## 🎯 面试高频 TOP10

1. **Q: 为什么用 B+ 树不用红黑树/哈希/B树？** **A:** 树矮(3层千万级)减少磁盘 IO；叶子链表支持范围查询；哈希只支持等值；B 树节点存数据更胖;B+ 非叶子不存数据，key 更多 IO 更少。
2. **Q: 索引失效场景？** **A:** 对索引列做函数/运算、隐式类型转换(字符串列传数字)、like '%xx'、联合索引跳过最左列、not in 大数据量、索引列排序方向不一致。
3. **Q: MVCC 原理？** **A:** 每行隐藏列 trx_id(事务id)+rollptr(回滚指针)，undo log 构成版本链；快照读时用 ReadView(活跃事务列表) 判断可见性，读到 <= 当前事务版本的最新版本。
4. **Q: MySQL 有哪些锁？** **A:** 全局锁/表锁/行锁(Record/Gap/Next-Key)；行锁是索引锁，锁在索引记录上；意向锁协调表锁与行锁；自增锁。
5. **Q: 行锁加锁规则（8.0 前）？** **A:** 等值查询：命中唯一索引=Record Lock，普通索引/无索引=加 Gap+Record(锁更大范围)；范围查询：Next-Key；无索引条件=全表加锁(危险)。
6. **Q: 分库分表策略？** **A:** 垂直拆分(按业务/字段) + 水平拆分(按主键 hash/范围/日期)；中间件 ShardingSphere/MyCat；核心是路由算法和分布式 ID。
7. **Q: 主从延迟问题怎么解决？** **A:** 读写分离下一致性：强制读主、延迟双删、缓存版本号、半同步复制(组复制增强)、异步转半同步、按业务可容忍度路由。
8. **Q: 为什么件 InnoDB 而不 MyISAM？** **A:** InnoDB 支持事务/行锁/外键/崩溃恢复(redo log)，MyISAM 只表锁且不支持事务，用在读多写少数据仓库场景。
9. **Q: 事务四大隔离级别解决了什么问题？** **A:** 脏读(未提交读到) → RC 解决；不可重复读(同事务两次读不一致) → RR 解决；幻读(行数变化) → SERIALIZABLE / RR+MVCC+间隙锁 解决。
10. **Q: count(\*) vs count(1) vs count(字段)?** **A:** count(*) 优化最彻底(空行也计入)；count(1) 与 count(*) 等价优化；count(字段) 需非 NULL 才算，性能更低；大表用缓存/抽样统计替代精确 count。

## ⚠️ 常见坑 & 最佳实践

| ❌ 坑 | ✅ 正确做法 |
|-------|------------|
| 索引列上做运算/函数 `WHERE name+0=1` | 保持列纯净，计算放右边：`WHERE name='1'` 类型匹配 |
| 深分页 LIMIT 100000,20 | 延迟关联 / 游标分页(WHERE id > last_id) |
| 大字段 SELECT * | 只查需要的列，覆盖索引减少回表 |
| 慢日志全开乱调 | 先慢日志定位 → EXPLAIN 分析 → 针对性加索引 |
| 高并发直接加悲观锁 | 判断冲突率用乐观锁(version) 降低等待 |
| 事务里执行过长逻辑(业务+IO) | 事务只包必要写操作，减少锁持有时间；长事务放大 binlog 和锁问题 |
| 缺少表字段字符集/排序规则设置 | utf8mb4 + utf8mb4_bin/unicode_ci 统一，避免乱码和排序不一致 |
| 滥用 LIKE '%keyword%' | 全文索引(MATCH AGAINST) 或搜索引擎(ES) 承接模糊搜索 |
| 主键用 UUID 无规律 | 自增 id / 雪花 id / 有序 UUID(如 ULID)，避免页分裂和碎片 |

## 📐 架构设计要点

- **索引设计**：区分高选择性列(差分度)、联合索引最左匹配+覆盖缩短回表、冗余索引定期清理、基于 EXPLAIN 复查。
- **高可用**：一主多从读写分离 + 半同步 + MHA/Orchestrator 故障切换；分库分表后用一致性哈希和虚拟节点均衡。
- **连接与池化**：HikariCP 参数调优(maximumPoolSize ≤ 核数*2+1 经验值)，避免连接耗尽。
- **数据一致性**：binlog/undo/redo 三兄弟——redo 崩溃恢复、undo MVCC+回滚、binlog 主从复制；两阶段提交保证 redo/binlog 一致。
- **监控**：慢查询监控、锁等待监控(information_schema.innodb_trx)、磁盘 IO(GiB/s)、主从延迟(SHOW SLAVE STATUS)。

## 🔗 关联技术

- **Redis**：MySQL 缓存层(SSR 模式)，防穿透/击穿/雪崩；ES 做全文检索。
- **Spring 事务**：@Transactional 与隔离级别传播行为交互。
- **ShardingSphere**：分库分表 + 读写分离中间件。
- **DeBezium/Canal**：binlog 变更捕获 → 数据同步到 ES/Redis。
- **MySQL Router / ProxySQL / Vitess**：连接路由和分片集群方案。