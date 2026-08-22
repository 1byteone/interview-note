# 大表设计 — 亿级数据 · 冷热分离 · 归档策略

> 🎯 进阶路线 · 预计阅读时间：40 分钟

---

## 一、大表设计原则

### 1.1 什么时候需要大表设计

| 数据量级 | 设计策略 | 参考案例 |
|----------|----------|----------|
| < 1000 万 | 单表 + 合理索引 | 商品表、用户表 |
| 1000 万 ~ 1 亿 | 分区表 + 读写分离 | 订单表、日志表 |
| 1 亿 ~ 10 亿 | 分库分表 + 冷热分离 | 电商订单、历史数据 |
| > 10 亿 | 上述 + 搜索引擎 + 数仓 | 交易流水、埋点数据 |

### 1.2 大表设计核心原则

1. **减少无关字段**：没用字段不建，不要 `SELECT *`
2. **数据分层**：热数据（高频访问）与冷数据（历史归档）分离
3. **索引精简**：大表索引越多，写入越慢，索引维护成本高
4. **分区策略**：按时间分区，方便删除旧数据（DROP PARTITION）
5. **只存必要字段**：大字段（TEXT/BLOB）单独拆表

---

## 二、分区表

### 2.1 分区类型

```sql
-- RANGE 分区（最常用，按时间）
CREATE TABLE order_info (
    id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    amount DECIMAL(10,2),
    create_time DATETIME NOT NULL
) ENGINE=InnoDB
PARTITION BY RANGE (YEAR(create_time)) (
    PARTITION p2021 VALUES LESS THAN (2022),
    PARTITION p2022 VALUES LESS THAN (2023),
    PARTITION p2023 VALUES LESS THAN (2024),
    PARTITION p2024 VALUES LESS THAN (2025),
    PARTITION p_future VALUES LESS THAN MAXVALUE
);

-- LIST 分区（按枚举值）
PARTITION BY LIST (status) (
    PARTITION p_active VALUES IN (0, 1),
    PARTITION p_inactive VALUES IN (2, 3)
);

-- HASH 分区（按哈希值）
PARTITION BY HASH (user_id) PARTITIONS 16;
```

### 2.2 分区表的优势

| 场景 | 普通表 | 分区表 |
|------|--------|--------|
| 删除旧数据 | `DELETE FROM log WHERE create_time < '2023-01-01'`（慢，产生大量 binlog） | `ALTER TABLE log DROP PARTITION p2022`（瞬间完成） |
| 查询特定时间 | 全表扫描或索引扫描 | 分区裁剪，只扫描相关分区 |
| 数据归档 | 复杂，需要导出导入 | 交换分区，秒级完成 |

### 2.3 分区裁剪

```sql
-- 查询只会扫描 p2024 分区
EXPLAIN SELECT * FROM order_info
WHERE create_time >= '2024-01-01' AND create_time < '2024-02-01';
-- 结果中 partitions 字段显示 p2024
```

### 2.4 分区注意事项

- 分区数建议不超过 1024 个
- 分区字段必须是主键或唯一索引的一部分（MySQL 5.7+ 限制）
- 分区表不支持外键
- 分区表不能使用全文索引

---

## 三、冷热数据分离

### 3.1 冷热分离策略

```
                         ┌──────────────────────┐
                         │    热数据存储         │
                         │  MySQL 高性能 SSD     │
                         │  最近 3 个月的数据    │
                         │  (高频访问)           │
                         └──────────┬───────────┘
                                    │ 定时任务（每天凌晨）
                                    ↓
                         ┌──────────────────────┐
                         │    冷数据存储         │
                         │  MySQL 大容量 HDD     │
                         │  3 个月前的数据       │
                         │  或归档到 Hive/OSS   │
                         │  (低频访问)           │
                         └──────────────────────┘
```

### 3.2 冷热分离实现

**方案 1：同库不同表**

```sql
-- 热表：order_info_active（最近 3 个月）
CREATE TABLE order_info_active LIKE order_info;

-- 冷表：order_info_archive（3 个月前）
CREATE TABLE order_info_archive LIKE order_info;

-- 定时任务（每天凌晨 3 点）
INSERT INTO order_info_archive
SELECT * FROM order_info_active
WHERE create_time < DATE_SUB(NOW(), INTERVAL 3 MONTH);

DELETE FROM order_info_active
WHERE create_time < DATE_SUB(NOW(), INTERVAL 3 MONTH);
```

**方案 2：不同存储引擎**

```sql
-- 热数据：InnoDB（支持事务、行锁）
CREATE TABLE order_active (...) ENGINE=InnoDB;

-- 冷数据：MyISAM 或 Archive（压缩率高、查询慢）
CREATE TABLE order_archive (...) ENGINE=Archive;
-- Archive 引擎压缩比 10:1，适合只读历史数据
```

**方案 3：不同数据库实例**

```
热库：MySQL 8.0 + NVMe SSD（高性能）
冷库：MySQL 8.0 + SATA HDD（高容量）
归档：HDFS / OSS（低成本）
```

### 3.3 代码层透明访问

```java
public interface OrderService {
    default List<OrderInfo> listOrders(Long userId, Date start, Date end) {
        // 判断时间范围
        if (isWithinHotPeriod(start, end)) {
            return orderActiveMapper.listByUserId(userId);
        } else {
            // 跨冷热期，合并查询
            List<OrderInfo> hot = orderActiveMapper.listByUserId(userId);
            List<OrderInfo> cold = orderArchiveMapper.listByUserId(userId);
            return mergeAndSort(hot, cold);
        }
    }
}
```

---

## 四、定期归档策略

### 4.1 归档方案对比

| 方案 | 实现方式 | 优点 | 缺点 |
|------|----------|------|------|
| DELETE | 直接删除 | 简单 | 慢、binlog 暴涨、碎片多 |
| DROP PARTITION | 删除分区 | 秒级 | 需要分区表 |
| 归档表 | 迁移到归档表 | 可回溯 | 需要维护迁移脚本 |
| 外部存储 | 导出到 OSS/HDFS | 成本低 | 查询不便 |

### 4.2 归档实现

```sql
-- 创建存储过程
DELIMITER $$
CREATE PROCEDURE archive_orders()
BEGIN
    DECLARE cutoff_date DATETIME;
    SET cutoff_date = DATE_SUB(NOW(), INTERVAL 6 MONTH);

    -- 1. 归档到冷表
    INSERT INTO order_info_archive
    SELECT * FROM order_info
    WHERE create_time < cutoff_date
    LIMIT 10000;

    -- 2. 删除已归档数据
    DELETE oi FROM order_info oi
    INNER JOIN order_info_archive oa
    ON oi.id = oa.id;

    -- 3. 优化表
    OPTIMIZE TABLE order_info;
END$$
DELIMITER ;

-- 定时执行（每天凌晨 2 点）
CREATE EVENT archive_event
ON SCHEDULE EVERY 1 DAY STARTS '2024-01-01 02:00:00'
DO CALL archive_orders();
```

### 4.3 归档策略建议

| 数据类型 | 热数据保留时间 | 冷数据保留时间 | 归档方式 |
|----------|---------------|---------------|----------|
| 订单数据 | 3 个月 | 3 年 | 按月归档到冷表 |
| 日志数据 | 7 天 | 30 天 | 按天分区，删除分区 |
| 操作记录 | 1 个月 | 1 年 | 导出到 OSS |
| 消息记录 | 1 个月 | 6 个月 | 归档到 ClickHouse |

---

## 五、亿级数据表设计案例

### 5.1 订单表设计（亿级）

```sql
-- 核心设计思路
-- 1. 按时间分区（便于管理历史数据）
-- 2. 精简字段（大字段拆到扩展表）
-- 3. 索引精简（只保留必要索引）
-- 4. 分库分表（按 user_id 分 16 片）

CREATE TABLE `order_info` (
    `id` BIGINT NOT NULL COMMENT '雪花算法 ID',
    `user_id` BIGINT NOT NULL,
    `order_no` VARCHAR(32) NOT NULL,
    `total_amount` DECIMAL(10,2) NOT NULL,
    `status` TINYINT NOT NULL DEFAULT 0,
    `payment_time` DATETIME DEFAULT NULL,
    `create_time` DATETIME NOT NULL,
    `update_time` DATETIME NOT NULL,
    PRIMARY KEY (`id`, `create_time`),  -- 主键包含分区字段
    UNIQUE KEY `uk_order_no` (`order_no`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
PARTITION BY RANGE (TO_DAYS(create_time)) (
    PARTITION p2024q1 VALUES LESS THAN (TO_DAYS('2024-04-01')),
    PARTITION p2024q2 VALUES LESS THAN (TO_DAYS('2024-07-01')),
    PARTITION p2024q3 VALUES LESS THAN (TO_DAYS('2024-10-01')),
    PARTITION p2024q4 VALUES LESS THAN (TO_DAYS('2025-01-01'))
);
```

### 5.2 设计要点说明

1. **主键使用雪花算法 ID**：全局唯一、趋势递增、支持分布式
2. **主键包含分区字段**：满足分区键必须在主键中的限制
3. **UNIQUE KEY 单独建**：唯一约束需要包含分区键，或单独建唯一索引
4. **按季度分区**：粒度适中，便于数据管理
5. **大字段拆表**：订单详情、物流信息等 Text/JSON 字段存在扩展表

---

## 总结

| 知识点 | 一句话概括 |
|--------|-----------|
| 分区表 | 按时间/范围分区，DROP PARTITION 秒级删除 |
| 冷热分离 | 热数据高性能存储，冷数据低成本存储 |
| 归档策略 | 定时迁移旧数据，释放热表空间 |
| 大表设计 | 精简字段、分区表、分库分表、索引精简 |

> 下一步：[04-projects/mall-integration.md](../04-projects/mall-integration.md) — AI 商城中的 MySQL 应用