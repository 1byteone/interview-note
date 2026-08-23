# MySQL 面试题大全

## 📚 知识体系

```
MySQL 基础知识
├── SQL 语法
├── 数据类型
├── 存储引擎 (InnoDB / MyISAM)
├── 事务 (ACID)
├── 索引 (B+Tree / Hash)
├── 锁机制
├── 日志系统
└── 性能优化

MySQL 高级特性
├── MVCC 多版本并发控制
├── 主从复制
├── 读写分离
├── 分库分表
├── 高可用架构
├── 慢查询优化
├── 执行计划分析
└── SQL 调优

MySQL 实战场景
├── 亿级大表优化
├── 热点数据缓存策略
├── 秒杀库存控制
├── 订单流水表归档
├── 全文搜索方案
└── 双写一致性
```

---

## 🎯 Level 1：基础题

### 1. InnoDB 和 MyISAM 的区别？
**答案**：

| 特性 | InnoDB | MyISAM |
|------|--------|--------|
| 事务 | 支持 | 不支持 |
| 锁粒度 | 行级锁 | 表级锁 |
| 外键 | 支持 | 不支持 |
| 索引结构 | B+树 | B+树 |
| 数据缓存 | 缓冲池 | 仅缓存索引 |
| 全文索引 | 5.6+ 支持 | 原生支持 |
| 崩溃恢复 | 支持（redo log） | 不支持 |

### 2. MySQL 索引有哪些类型？
**答案**：
- **主键索引**：非空且唯一
- **唯一索引**：列值唯一
- **普通索引**：加速查询
- **联合索引**：多个列组合
- **全文索引**：全文检索
- **前缀索引**：只索引列的前缀部分

### 3. 什么是回表和覆盖索引？
**答案**：
- **回表**：使用非主键索引查询，通过二级索引找到主键，再回主键索引查询完整数据
- **覆盖索引**：查询的列都在索引中，不需要回表查询

---

## 🎯 Level 2：进阶题

### 4. 事务的隔离级别有哪些？
**答案**：

| 隔离级别 | 脏读 | 不可重复读 | 幻读 |
|----------|------|------------|------|
| 读未提交 | ✗ | ✗ | ✗ |
| 读已提交 | ✓ | ✗ | ✗ |
| 可重复读 | ✓ | ✓ | ✗ |
| 串行化 | ✓ | ✓ | ✓ |

MySQL 默认隔离级别：**可重复读（Repeatable Read）**

### 5. MVCC 的实现原理？
**答案**：
- **隐藏字段**：`DB_TRX_ID`（事务ID）、`DB_ROLL_PTR`（回滚指针）
- **Undo Log**：记录数据修改前的版本
- **Read View**：事务快照，判断数据可见性
- **实现机制**：
  1. 每次事务开始时创建 Read View
  2. 查询时判断每个版本的事务ID是否可见
  3. 通过版本链实现多版本数据读取

### 6. MySQL 的锁有哪些？
**答案**：
- **全局锁**：`FLUSH TABLES WITH READ LOCK`
- **表级锁**：表锁、元数据锁（MDL）、意向锁
- **行级锁**：记录锁（Record Lock）、间隙锁（Gap Lock）、临键锁（Next-Key Lock）
- **死锁**：多个事务互相等待对方的锁

### 7. 什么是索引失效？常见场景有哪些？
**答案**：
1. 对索引列使用函数或表达式
2. 隐式类型转换
3. 使用 `LIKE '%xxx'`（前置通配符）
4. 联合索引不满足最左前缀原则
5. `OR` 条件中有非索引列
6. 优化器认为全表扫描更快
7. `!=`、`NOT IN`、`IS NOT NULL`

---

## 🎯 Level 3：高级题

### 8. 如何优化慢 SQL？
**答案**：

**分析工具**：
```sql
-- 查看慢查询日志
SHOW VARIABLES LIKE 'slow_query_log';
-- 查看执行计划
EXPLAIN SELECT * FROM orders WHERE user_id = 100;
```

**优化策略**：
1. **索引优化**：合理建索引，避免索引失效
2. **SQL 重写**：优化 JOIN、子查询、分页
3. **表结构优化**：合理数据类型、字段拆分
4. **分页优化**：使用游标分页代替 OFFSET 分页
5. **读写分离**：读操作走从库
6. **缓存降级**：热点数据走 Redis

### 9. 如何设计 MySQL 高可用架构？
**答案**：

**方案一：主从复制 + 读写分离**
```
         Application
              ↓
   ┌────── Read/Write ──────┐
   ↓                         ↓
  Master                   Slave-1
   ↓                         ↓
  Slave-2                  Slave-3
   ↓
  Slave-N
```

**方案二：MHA（Master High Availability）**
- 自动检测主节点故障
- 自动提升从节点为主节点
- 自动重新配置其他从节点

**方案三：MGR（MySQL Group Replication）**
- 组复制模式
- 数据最终一致性
- 自动故障转移

### 10. 百万/千万级数据如何分库分表？
**答案**：

**分片策略**：
1. **水平分表**：按某个字段（如 user_id）哈希取模
2. **水平分库**：按业务维度拆分到不同库
3. **垂直拆分**：按功能模块拆分

**常用中间件**：
- **ShardingSphere**：Apache 开源分库分表中间件
- **MyCat**：数据库中间件

**分片键设计**：
```sql
-- 用户按 user_id % 16 分片
CREATE DATABASE user_0;
CREATE DATABASE user_1;
-- ...
CREATE DATABASE user_15;
```

**全局唯一 ID**：
- 雪花算法（Snowflake）
- 数据库自增 + 步长
- Redis INCR
- 美团 Leaf / 百度 UidGenerator

### 11. 如何设计秒杀系统的库存扣减？
**答案**：

**方案一：数据库乐观锁**
```sql
UPDATE inventory
SET stock = stock - 1
WHERE product_id = 100 AND stock > 0;
-- 影响行数为 1 表示扣减成功
```

**方案二：Redis 预扣减**
```java
// 预扣减库存
long remain = redis.incrBy("stock:100", -1);
if (remain >= 0) {
    // 发送 MQ 异步落库
    rocketMQ.send("stock_order", order);
} else {
    // 回补库存
    redis.incrBy("stock:100", 1);
}
```

**方案三：Lua 脚本保证原子性**
```lua
-- 判断库存并扣减（原子操作）
local stock = tonumber(redis.call('get', KEYS[1]))
if stock and stock > 0 then
    redis.call('decrby', KEYS[1], 1)
    return 1
end
return 0
```

---

## 🎯 Level 4：专家题

### 12. 亿级大表如何优化？
**答案**：

**八大优化策略**：
1. **分区表**：按时间/范围分区
2. **分表分库**：水平拆分
3. **冷热分离**：历史数据归档
4. **索引优化**：合理索引 + 覆盖索引
5. **读写分离**：主写从读
6. **缓存层**：热点数据到 Redis
7. **归档清理**：定期删除/归档旧数据
8. **异步迁移**：数据同步到 ES/ClickHouse

**示例**：
```sql
-- 按日期分区
CREATE TABLE orders (
    id BIGINT,
    order_no VARCHAR(32),
    create_time DATETIME
) PARTITION BY RANGE (TO_DAYS(create_time)) (
    PARTITION p2024 VALUES LESS THAN (TO_DAYS('2025-01-01')),
    PARTITION p2025 VALUES LESS THAN (TO_DAYS('2026-01-01')),
    PARTITION p2026 VALUES LESS THAN MAXVALUE
);
```

### 13. MySQL 与 Redis 双写一致性怎么保证？
**答案**：

**方案一：先更新 DB，再删除缓存（推荐）**
```
更新 DB → 删除缓存
```
- 问题：删除缓存失败会导致脏数据
- 解决：删除失败重试 + 补偿

**方案二：延时双删**
```
更新 DB → 删除缓存 → 延时 500ms → 再次删除缓存
```

**方案三：消息队列异步删除**
```
更新 DB → 发送 MQ → 消费删除缓存
```

**方案四：订阅 binlog（Canal）**
```
MySQL binlog → Canal → 更新缓存 / 同步 ES
```

---

## 📖 学习资源

### 推荐项目
- [ShardingSphere](https://github.com/apache/shardingsphere) - 分库分表中间件
- [Canal](https://github.com/alibaba/canal) - binlog 同步工具
- [MySQL 官方文档](https://dev.mysql.com/doc/)

### 调优工具
- `EXPLAIN` - 执行计划分析
- `SHOW PROFILE` - SQL 性能分析
- `pt-query-digest` - 慢查询分析
- `mysqlslap` - 压力测试

### 最佳实践
1. 建表必须指定字符集和引擎
2. 主键使用自增或雪花算法
3. 合理设计索引，避免冗余索引
4. 大事务拆分，避免长事务
5. 索引字段避免 NULL 值