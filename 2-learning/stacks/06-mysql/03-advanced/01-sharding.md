# 分库分表 — 策略 · ShardingSphere · 分布式挑战

> 🎯 进阶路线 · 预计阅读时间：50 分钟

---

## 一、分库分表策略

### 1.1 为什么要分库分表

| 问题 | 单库单表 | 分库分表后 |
|------|----------|------------|
| 单表数据量 | 超过 1000 万行后查询变慢 | 每表 500 万，速度翻倍 |
| 写入瓶颈 | 单库写入能力有限（~5000 TPS） | 多库并行写入，线性扩展 |
| 索引大小 | 索引过大，B+Tree 层数增加 | 索引变小，层数降低 |
| 备份恢复 | 大表备份耗时数小时 | 分片备份，时间可控 |

### 1.2 垂直分库

按业务模块拆分到不同数据库：

```
                 ┌──────────────────┐
                 │    单库（所有表）   │
                 │  user, product,   │
                 │  order, payment   │
                 └──────────────────┘
                          ↓
    ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐
    │ 用户库    │  │ 商品库    │  │ 订单库    │  │ 支付库    │
    │ user     │  │ product  │  │ order    │  │ payment  │
    │          │  │ category │  │ order_item│  │ refund   │
    └──────────┘  └──────────┘  └──────────┘  └──────────┘
```

**优点：** 业务隔离、独立扩展、故障隔离
**缺点：** 跨库 JOIN 困难、分布式事务复杂

### 1.3 水平分表

将同一张表的数据按规则拆分到多张表：

```sql
-- 订单表按 user_id 取模分 16 张表
-- order_0, order_1, ..., order_15

-- 分片规则：order_suffix = user_id % 16
-- user_id = 100 → 100 % 16 = 4 → order_4 表
-- user_id = 200 → 200 % 16 = 8 → order_8 表
```

### 1.4 水平分库 + 分表

```sql
-- 分库：user_id % 4 → db0, db1, db2, db3
-- 分表：user_id % 16 / 4 → t_order_0, ..., t_order_3

-- 总共 4 库 * 4 表 = 16 个分片
-- 每个分片数据量 = 总数据量 / 16
```

---

## 二、分片键选择

### 2.1 分片键选择原则

| 原则 | 说明 | 反例 |
|------|------|------|
| 均匀分布 | 数据均匀分散到各分片 | 按时间分片，月底数据集中在新增分片 |
| 查询友好 | 大部分查询带分片键 | 按 order_id 分片，但用户查自己的订单没有 order_id |
| 不易变更 | 分片键值不会改变 | 按手机号分片，用户换手机号导致数据迁移 |

### 2.2 常见分片策略

| 策略 | 实现 | 优点 | 缺点 |
|------|------|------|------|
| 取模 | `shard = key % N` | 实现简单，数据均匀 | 扩容困难 |
| 哈希 | `shard = hash(key) % N` | 数据均匀 | 同样扩容困难 |
| 范围 | `shard = key / 区间` | 扩容方便，范围查询友好 | 数据倾斜 |
| 一致性哈希 | 虚拟节点映射 | 扩容影响小 | 实现复杂 |

### 2.3 分片键选择的实战建议

**电商场景**：
- **订单表**：按 `user_id` 分片（用户查订单是高频）
- **订单表**：按 `order_id` 分片（订单详情查询）
- **折中方案**：用 `user_id` 分片，同时维护 `order_id → user_id` 映射表

---

## 三、ShardingSphere-JDBC 实战

### 3.1 什么是 ShardingSphere-JDBC

轻量级 Java 框架，在 JDBC 层实现分库分表，对应用透明。

### 3.2 Maven 依赖

```xml
<dependency>
    <groupId>org.apache.shardingsphere</groupId>
    <artifactId>shardingsphere-jdbc-core-spring-boot-starter</artifactId>
    <version>5.3.2</version>
</dependency>
```

### 3.3 配置示例

```yaml
# application.yml
spring:
  shardingsphere:
    datasource:
      names: ds0, ds1, ds2, ds3
      ds0:
        type: com.zaxxer.hikari.HikariDataSource
        jdbc-url: jdbc:mysql://localhost:3306/order_db0
        username: root
        password: root123
      ds1:
        jdbc-url: jdbc:mysql://localhost:3306/order_db1
        # ... 类似配置
    rules:
      sharding:
        tables:
          order_info:
            actual-data-nodes: ds$->{0..3}.order_$->{0..3}
            database-strategy:
              standard:
                sharding-column: user_id
                sharding-algorithm-name: db_mod
            table-strategy:
              standard:
                sharding-column: user_id
                sharding-algorithm-name: table_mod
        sharding-algorithms:
          db_mod:
            type: MOD
            props:
              sharding-count: 4
          table_mod:
            type: MOD
            props:
              sharding-count: 4
    props:
      sql-show: true  # 打印 SQL 日志
```

### 3.4 代码示例

```java
@Repository
public class OrderInfoMapper {
    @Insert("INSERT INTO order_info(user_id, order_no, amount) VALUES(#{userId}, #{orderNo}, #{amount})")
    int insert(OrderInfo order);

    @Select("SELECT * FROM order_info WHERE user_id = #{userId} ORDER BY create_time DESC LIMIT 20")
    List<OrderInfo> listByUserId(@Param("userId") Long userId);

    @Select("SELECT * FROM order_info WHERE order_no = #{orderNo}")
    List<OrderInfo> getByOrderNo(@Param("orderNo") String orderNo);
    // 没有 user_id 的分片键，会广播到所有分片，性能差
}
```

---

## 四、跨分片查询

### 4.1 跨分片查询的问题

```sql
-- 不指定分片键，ShardingSphere 会广播到所有分片
SELECT * FROM order_info WHERE status = 1 ORDER BY create_time DESC LIMIT 20;
-- 结果：4 库 * 4 表 = 16 个分片，每个分片查 20 条，内存排序后取前 20
```

### 4.2 解决方案

| 方案 | 说明 | 适用场景 |
|------|------|----------|
| 带分片键查询 | 查询时带上 user_id | 用户个人订单查询 |
| 广播表 | 小表在每个分片都存一份 | 商品分类、配置表 |
| 搜索引擎 | 用 ES 做全文搜索 | 后台管理、运营查询 |
| 汇总查询 | 定时汇总到一张表 | 报表统计、BI 分析 |

### 4.3 广播表与绑定表

```yaml
# 广播表：小表在每个分片都存一份
rules:
  sharding:
    broadcast-tables:
      - t_dict          # 字典表
      - t_config        # 配置表

# 绑定表：保证 JOIN 时两个表的数据在同一分片
    binding-tables:
      - order_info, order_item  # 订单和订单项按相同分片键
```

---

## 五、分布式事务

### 5.1 分布式事务方案

| 方案 | 一致性 | 性能 | 适用场景 |
|------|--------|------|----------|
| XA（两阶段提交） | 强一致 | 低 | 金融、支付 |
| TCC（Try-Confirm-Cancel） | 最终一致 | 中 | 高并发业务 |
| 可靠消息最终一致性 | 最终一致 | 高 | 异步场景 |
| Seata AT | 最终一致 | 中 | 通用场景 |

### 5.2 Seata AT 模式

```java
@GlobalTransactional
public void createOrder(OrderDTO order) {
    // 1. 扣库存（库存库）
    inventoryService.deductStock(order.getProductId(), order.getQuantity());
    // 2. 创建订单（订单库）
    orderService.createOrder(order);
    // 3. 扣余额（用户库）
    accountService.deductBalance(order.getUserId(), order.getAmount());
}
```

---

## 六、全局 ID 生成

### 6.1 为什么需要全局 ID

分库分表后，自增 ID 无法保证全局唯一。需要分布式 ID。

### 6.2 常见方案

| 方案 | ID 长度 | 性能 | 有序性 |
|------|---------|------|--------|
| UUID | 36 位 | 高 | 无序 |
| 雪花算法 | 19 位 | 极高 | 趋势递增 |
| Redis INCR | 19 位 | 高 | 递增 |
| 号段模式 | 19 位 | 极高 | 递增 |

### 6.3 雪花算法

```java
public class SnowflakeIdWorker {
    // 1位符号位 + 41位时间戳 + 10位工作机器ID + 12位序列号
    // 总长度 64 位，返回 long 类型

    private long workerId;
    private long sequence = 0L;
    private long lastTimestamp = -1L;

    public synchronized long nextId() {
        long timestamp = timeGen();
        if (timestamp == lastTimestamp) {
            sequence = (sequence + 1) & 4095;  // 12 位序列号
        } else {
            sequence = 0L;
        }
        lastTimestamp = timestamp;
        return ((timestamp - 1288834974657L) << 22)
               | (workerId << 12)
               | sequence;
    }
}
```

---

## 总结

| 知识点 | 核心要点 |
|--------|----------|
| 垂直分库 | 按业务拆分，独立数据库 |
| 水平分表 | 按分片键拆分，解决单表数据量问题 |
| 分片键选择 | 均匀分布、查询友好、不易变更 |
| ShardingSphere | JDBC 层透明分库分表 |
| 分布式事务 | XA/TCC/Seata/可靠消息 |
| 全局 ID | 雪花算法推荐 |

> 下一步：[02-master-slave.md](./02-master-slave.md) — 主从复制与高可用