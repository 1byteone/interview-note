# MySQL + Redis 数据存储方案

> zznursing 项目采用 MySQL + Redis 混合存储方案，MySQL 负责业务数据持久化，Redis 负责设备状态缓存、实时数据、会话管理。核心挑战：处理 IoT 设备时序数据的高并发写入和高效查询。

---

## 一、数据库设计

### 1.1 核心表结构

#### 设备数据表

```sql
-- device_data_record.sql —— 设备数据记录表
-- 存储所有设备上报的健康监测数据，按时间分区存储
-- 典型数据量：1000 台设备 × 每 5 秒一条 = 1728 万条/天
-- 分表策略：按月分表 device_data_record_yyyyMM
CREATE TABLE `device_data_record_202608` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `device_id` VARCHAR(64) NOT NULL COMMENT '设备唯一标识',
    `elderly_id` BIGINT NOT NULL COMMENT '老人ID，关联老人信息表',
    `data_type` VARCHAR(32) NOT NULL COMMENT '数据类型：heart_rate(心率) / blood_pressure(血压) / temperature(体温) / step(步数) / fall_detection(跌倒检测)',
    `data_value` VARCHAR(255) NOT NULL COMMENT '数据值，不同数据类型格式不同，如心率存数字、血压存"systolic/diastolic"',
    `unit` VARCHAR(16) DEFAULT NULL COMMENT '数据单位，如 bpm(心率) / mmHg(血压) / ℃(体温)',
    `battery` INT DEFAULT NULL COMMENT '设备电量百分比，0-100',
    `signal_strength` INT DEFAULT NULL COMMENT '信号强度，RSSI 值',
    `collect_time` DATETIME(3) NOT NULL COMMENT '设备数据采集时间，精确到毫秒',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '记录创建时间，即服务端接收时间',
    `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除标记，0-未删除 1-已删除',
    PRIMARY KEY (`id`),
    -- 按设备ID+采集时间索引：查询某个设备的历史数据
    KEY `idx_device_id_collect_time` (`device_id`, `collect_time`),
    -- 按老人ID+数据类型+采集时间索引：查询某个老人的某项健康指标趋势
    KEY `idx_elderly_type_time` (`elderly_id`, `data_type`, `collect_time`),
    -- 按采集时间索引：用于数据清理和归档
    KEY `idx_collect_time` (`collect_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备数据记录表（按月分表）';

-- 说明：
-- 1. 按月分表：每月创建新表 device_data_record_yyyyMM，避免单表数据量过大
-- 2. 分区键设计：collect_time 作为核心分区键，按时间范围查询高效
-- 3. 索引策略：device_id + collect_time 联合索引覆盖大多数查询场景
-- 4. 数据类型处理：data_value 使用 VARCHAR 而非多列，灵活性更高
```

#### 设备信息表

```sql
-- device_info.sql —— 设备信息表
-- 存储设备的静态信息和最新状态
CREATE TABLE `device_info` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `device_id` VARCHAR(64) NOT NULL COMMENT '设备唯一标识，与 IoTDA 平台一致',
    `device_name` VARCHAR(128) NOT NULL COMMENT '设备名称，如"三楼心率手环-01"',
    `device_type` VARCHAR(32) NOT NULL COMMENT '设备类型：heart_band(心率手环) / fall_detector(跌倒检测器) / locator(定位胸牌)',
    `product_id` VARCHAR(64) NOT NULL COMMENT '华为 IoTDA 产品ID，对应产品模型',
    `status` VARCHAR(16) NOT NULL DEFAULT 'offline' COMMENT '设备状态：online(在线) / offline(离线) / fault(故障) / inactive(未激活)',
    `battery` INT DEFAULT NULL COMMENT '最新电量百分比',
    `firmware_version` VARCHAR(32) DEFAULT NULL COMMENT '固件版本号',
    `elderly_id` BIGINT DEFAULT NULL COMMENT '绑定的老人ID，null 表示未绑定',
    `bind_time` DATETIME DEFAULT NULL COMMENT '绑定时间',
    `last_online_time` DATETIME DEFAULT NULL COMMENT '最后在线时间',
    `last_offline_time` DATETIME DEFAULT NULL COMMENT '最后离线时间',
    `total_online_minutes` BIGINT NOT NULL DEFAULT 0 COMMENT '累计在线时长（分钟）',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_device_id` (`device_id`),
    KEY `idx_status` (`status`),
    KEY `idx_elderly_id` (`elderly_id`),
    KEY `idx_device_type` (`device_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备信息表';
```

#### 告警记录表

```sql
-- device_alert_log.sql —— 设备告警记录表
-- 存储设备触发的告警事件，用于告警中心展示和分析
CREATE TABLE `device_alert_log` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `device_id` VARCHAR(64) NOT NULL COMMENT '设备ID',
    `elderly_id` BIGINT NOT NULL COMMENT '老人ID',
    `alert_type` VARCHAR(32) NOT NULL COMMENT '告警类型：heart_rate_high(心率过高) / heart_rate_low(心率过低) / fall_detection(跌倒检测) / device_offline(设备离线) / battery_low(电量低)',
    `alert_level` VARCHAR(8) NOT NULL DEFAULT 'WARNING' COMMENT '告警级别：INFO(提示) / WARNING(警告) / CRITICAL(严重)',
    `alert_value` VARCHAR(255) DEFAULT NULL COMMENT '触发告警的数据值',
    `description` VARCHAR(500) DEFAULT NULL COMMENT '告警描述信息',
    `status` VARCHAR(16) NOT NULL DEFAULT 'pending' COMMENT '处理状态：pending(待处理) / processing(处理中) / resolved(已解决) / ignored(已忽略)',
    `handler_id` BIGINT DEFAULT NULL COMMENT '处理人ID，关联员工表',
    `handle_time` DATETIME DEFAULT NULL COMMENT '处理时间',
    `handle_note` VARCHAR(500) DEFAULT NULL COMMENT '处理备注',
    `alert_time` DATETIME(3) NOT NULL COMMENT '告警触发时间',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_elderly_alert_time` (`elderly_id`, `alert_time`),
    KEY `idx_status` (`status`),
    KEY `idx_alert_type` (`alert_type`),
    KEY `idx_alert_time` (`alert_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备告警记录表';
```

### 1.2 JPA 实体

```java
// DeviceInfo.java —— 设备信息实体
package com.zznursing.iot.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 设备信息实体
 * 映射 device_info 表，包含设备静态信息和运行时状态
 */
@Data
@Entity
@Table(name = "device_info")
public class DeviceInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 设备唯一标识 */
    @Column(name = "device_id", nullable = false, unique = true, length = 64)
    private String deviceId;

    /** 设备名称 */
    @Column(name = "device_name", nullable = false, length = 128)
    private String deviceName;

    /** 设备类型 */
    @Column(name = "device_type", nullable = false, length = 32)
    private String deviceType;

    /** 产品ID */
    @Column(name = "product_id", nullable = false, length = 64)
    private String productId;

    /** 设备状态 */
    @Column(name = "status", nullable = false, length = 16)
    private String status;

    /** 电量 */
    @Column(name = "battery")
    private Integer battery;

    /** 固件版本 */
    @Column(name = "firmware_version", length = 32)
    private String firmwareVersion;

    /** 绑定老人ID */
    @Column(name = "elderly_id")
    private Long elderlyId;

    /** 最后在线时间 */
    @Column(name = "last_online_time")
    private LocalDateTime lastOnlineTime;

    /** 创建时间 */
    @CreationTimestamp
    @Column(name = "create_time", updatable = false)
    private LocalDateTime createTime;

    /** 更新时间 */
    @UpdateTimestamp
    @Column(name = "update_time")
    private LocalDateTime updateTime;
}
```

---

## 二、Redis 缓存方案

### 2.1 缓存架构设计

```
┌─────────────────────────────────────────────────────────────────┐
│                        Redis 缓存分层                            │
│                                                                 │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │  L1: 实时数据（毫秒级响应）                               │   │
│  │  ┌────────────────────────────────────────────────────┐ │   │
│  │  │ device:latest:{deviceId} → 最新数据 JSON (TTL 5min) │ │   │
│  │  │ device:online:{deviceId} → online/offline (TTL 10m) │ │   │
│  │  │ device:alert:{deviceId} → 未处理告警数               │ │   │
│  │  └────────────────────────────────────────────────────┘ │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                 │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │  L2: 热点数据（分钟级响应）                               │   │
│  │  ┌────────────────────────────────────────────────────┐ │   │
│  │  │ elderly:health:{id}:{type} → 最近 1 小时数据聚合    │ │   │
│  │  │ device:stats:{type} → 设备统计信息                  │ │   │
│  │  │ ai:conversation:{userId} → AI 对话历史              │ │   │
│  │  └────────────────────────────────────────────────────┘ │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                 │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │  L3: 业务数据（降级缓存）                                 │   │
│  │  ┌────────────────────────────────────────────────────┐ │   │
│  │  │ elderly:info:{id} → 老人信息缓存                   │ │   │
│  │  │ device:info:{id} → 设备信息缓存                    │ │   │
│  │  │ sys:config:* → 系统配置缓存                        │ │   │
│  │  └────────────────────────────────────────────────────┘ │   │
│  └─────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
```

### 2.2 Redis 缓存服务

```java
// DeviceCacheService.java
// 设备缓存服务 —— 管理设备实时状态和数据的 Redis 缓存
package com.zznursing.iot.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 设备缓存服务
 * 核心职责：管理设备实时数据缓存、在线状态、设备信息缓存
 * 使用 Redis 存储热数据，降低 MySQL 查询压力
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceCacheService {

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    // ============== Redis Key 前缀常量 ==============

    /** 设备最新数据缓存 Key 前缀 */
    private static final String KEY_DEVICE_LATEST = "device:latest:";

    /** 设备在线状态 Key 前缀 */
    private static final String KEY_DEVICE_ONLINE = "device:online:";

    /** 设备信息缓存 Key 前缀 */
    private static final String KEY_DEVICE_INFO = "device:info:";

    /** 老人健康数据聚合缓存 Key 前缀 */
    private static final String KEY_ELDERLY_HEALTH = "elderly:health:";

    /** 未处理告警数 Key 前缀 */
    private static final String KEY_DEVICE_ALERT = "device:alert:";

    // ============== 设备最新数据缓存 ==============

    /**
     * 更新设备最新数据缓存
     * 每次设备上报数据时调用，保存最新一条数据到 Redis
     * 设置 TTL 5 分钟，设备停止上报后自动过期
     *
     * @param deviceId 设备ID
     * @param dataJson 数据 JSON 字符串
     */
    public void updateLatestData(String deviceId, String dataJson) {
        String key = KEY_DEVICE_LATEST + deviceId;
        stringRedisTemplate.opsForValue().set(key, dataJson, 5, TimeUnit.MINUTES);
    }

    /**
     * 获取设备最新数据
     *
     * @param deviceId 设备ID
     * @return 最新数据 JSON，如果不存在返回 null
     */
    public String getLatestData(String deviceId) {
        return stringRedisTemplate.opsForValue().get(KEY_DEVICE_LATEST + deviceId);
    }

    /**
     * 批量获取设备最新数据
     * 仪表盘首页展示时批量查询多个设备的最新状态
     *
     * @param deviceIds 设备ID列表
     * @return 设备ID到最新数据的映射
     */
    public Map<String, String> getBatchLatestData(String[] deviceIds) {
        // 批量构造 Redis Key
        String[] keys = new String[deviceIds.length];
        for (int i = 0; i < deviceIds.length; i++) {
            keys[i] = KEY_DEVICE_LATEST + deviceIds[i];
        }

        // 使用 multiGet 批量查询，减少网络往返
        java.util.List<String> values = stringRedisTemplate.opsForValue().multiGet(
                java.util.Arrays.asList(keys));

        // 构建返回结果
        java.util.Map<String, String> result = new java.util.HashMap<>();
        for (int i = 0; i < deviceIds.length; i++) {
            if (values != null && values.get(i) != null) {
                result.put(deviceIds[i], values.get(i));
            }
        }
        return result;
    }

    // ============== 设备在线状态 ==============

    /**
     * 更新设备在线状态
     * 设备心跳上报时刷新，TTL = 10 分钟
     * 超过 10 分钟未上报，Redis 自动过期，视为离线
     */
    public void updateOnlineStatus(String deviceId, boolean online) {
        String key = KEY_DEVICE_ONLINE + deviceId;
        if (online) {
            // 设备在线，写入状态并设置过期时间
            stringRedisTemplate.opsForValue().set(key, "online", 10, TimeUnit.MINUTES);
        } else {
            // 设备离线，写入状态但保留较长时间便于查询
            stringRedisTemplate.opsForValue().set(key, "offline", 30, TimeUnit.MINUTES);
        }
    }

    /**
     * 检查设备是否在线
     * 如果 Redis 中存在 key 且值为 "online" 则判定为在线
     */
    public boolean isDeviceOnline(String deviceId) {
        String value = stringRedisTemplate.opsForValue().get(KEY_DEVICE_ONLINE + deviceId);
        return "online".equals(value);
    }

    /**
     * 批量获取设备在线状态
     */
    public Map<String, Boolean> getBatchOnlineStatus(String[] deviceIds) {
        String[] keys = new String[deviceIds.length];
        for (int i = 0; i < deviceIds.length; i++) {
            keys[i] = KEY_DEVICE_ONLINE + deviceIds[i];
        }

        java.util.List<String> values = stringRedisTemplate.opsForValue()
                .multiGet(java.util.Arrays.asList(keys));

        java.util.Map<String, Boolean> result = new java.util.HashMap<>();
        for (int i = 0; i < deviceIds.length; i++) {
            result.put(deviceIds[i], values != null && "online".equals(values.get(i)));
        }
        return result;
    }

    // ============== 设备信息缓存 ==============

    /**
     * 缓存设备信息
     * 设备信息变更不频繁，缓存 1 小时
     */
    public void cacheDeviceInfo(String deviceId, String deviceInfoJson) {
        stringRedisTemplate.opsForValue().set(
                KEY_DEVICE_INFO + deviceId, deviceInfoJson, 1, TimeUnit.HOURS);
    }

    /**
     * 获取缓存的设备信息
     */
    public String getCachedDeviceInfo(String deviceId) {
        return stringRedisTemplate.opsForValue().get(KEY_DEVICE_INFO + deviceId);
    }

    // ============== 告警计数 ==============

    /**
     * 增加未处理告警计数
     * 设备触发告警时递增，用于在设备列表显示告警标记
     */
    public void incrementAlertCount(String deviceId) {
        stringRedisTemplate.opsForValue().increment(KEY_DEVICE_ALERT + deviceId);
    }

    /**
     * 获取未处理告警数
     */
    public Integer getAlertCount(String deviceId) {
        String value = stringRedisTemplate.opsForValue().get(KEY_DEVICE_ALERT + deviceId);
        return value != null ? Integer.parseInt(value) : 0;
    }

    /**
     * 清除告警计数（告警处理后调用）
     */
    public void clearAlertCount(String deviceId) {
        stringRedisTemplate.delete(KEY_DEVICE_ALERT + deviceId);
    }
}
```

### 2.3 Redis 配置

```java
// RedisConfig.java —— Redis 配置类
package com.zznursing.iot.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis 配置类
 * 配置 RedisTemplate 的序列化方式，支持对象存储
 */
@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(
            RedisConnectionFactory redisConnectionFactory) {

        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(redisConnectionFactory);

        // 使用 Jackson 序列化，支持对象到 JSON 的自动转换
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        objectMapper.registerModule(new JavaTimeModule());
        // 允许序列化未知类型
        objectMapper.activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL
        );

        GenericJackson2JsonRedisSerializer serializer =
                new GenericJackson2JsonRedisSerializer(objectMapper);

        // Key 使用 String 序列化
        template.setKeySerializer(StringRedisSerializer.UTF_8);
        // Value 使用 JSON 序列化
        template.setValueSerializer(serializer);
        // Hash Key 使用 String 序列化
        template.setHashKeySerializer(StringRedisSerializer.UTF_8);
        // Hash Value 使用 JSON 序列化
        template.setHashValueSerializer(serializer);

        template.afterPropertiesSet();
        return template;
    }
}
```

---

## 三、数据存储策略

### 3.1 时序数据存储策略

**分层存储架构：**

| 数据层级 | 存储介质 | 保留时间 | 数据粒度 | 典型查询 |
|----------|----------|----------|----------|----------|
| **热数据** | Redis | 1 小时 | 原始数据（每 5 秒） | 实时监控 |
| **温数据** | MySQL 当月表 | 30 天 | 原始数据 | 趋势分析 |
| **冷数据** | MySQL 历史表 | 90 天 | 聚合数据（1 分钟粒度） | 报表统计 |
| **归档数据** | 文件存储 | 1 年+ | 聚合数据（1 小时粒度） | 合规审计 |

**数据清理策略：**

```java
// DataCleanupService.java
// 数据清理服务 —— 定期清理过期数据，迁移到归档表
package com.zznursing.iot.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * 数据清理服务
 * 定时任务：每日凌晨执行数据清理和归档
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataCleanupService {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 每天凌晨 2:00 执行数据清理
     * 1. 清理超过 30 天的原始数据
     * 2. 将超过 30 天的数据聚合后移入历史表
     * 3. 删除超过 90 天的聚合数据
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void cleanupDeviceData() {
        log.info("开始执行设备数据清理任务");

        try {
            // 步骤1：将超过 30 天的原始数据聚合到历史表
            // 按 1 分钟粒度聚合：计算平均值、最大值、最小值
            String aggregateSql = """
                INSERT INTO device_data_history_yyyyMM
                (device_id, elderly_id, data_type, avg_value, max_value, min_value,
                 data_count, start_time, end_time, create_time)
                SELECT
                    device_id, elderly_id, data_type,
                    AVG(CAST(data_value AS DECIMAL(10,2))),
                    MAX(CAST(data_value AS DECIMAL(10,2))),
                    MIN(CAST(data_value AS DECIMAL(10,2))),
                    COUNT(*),
                    DATE_FORMAT(collect_time, '%Y-%m-%d %H:%i:00'),
                    DATE_FORMAT(collect_time, '%Y-%m-%d %H:%i:59'),
                    NOW()
                FROM device_data_record_yyyyMM
                WHERE collect_time < DATE_SUB(NOW(), INTERVAL 30 DAY)
                GROUP BY device_id, elderly_id, data_type,
                         DATE_FORMAT(collect_time, '%Y-%m-%d %H:%i')
                """;

            // 使用动态表名
            String currentTable = "device_data_record_" +
                    java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMM"));
            String historyTable = "device_data_history_" +
                    java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMM"));

            String sql = aggregateSql
                    .replace("device_data_record_yyyyMM", currentTable)
                    .replace("device_data_history_yyyyMM", historyTable);

            int insertedCount = jdbcTemplate.update(sql);
            log.info("数据聚合完成，插入 {} 条聚合记录", insertedCount);

            // 步骤2：删除已经聚合的原始数据
            String deleteSql = "DELETE FROM " + currentTable
                    + " WHERE collect_time < DATE_SUB(NOW(), INTERVAL 30 DAY)";
            int deletedCount = jdbcTemplate.update(deleteSql);
            log.info("原始数据清理完成，删除 {} 条记录", deletedCount);

        } catch (Exception e) {
            log.error("数据清理任务执行异常", e);
        }
    }
}
```

### 3.2 缓存与数据库一致性方案

**缓存更新策略：**

1. **Cache-Aside Pattern**：读取时先查缓存，缓存未命中再查数据库，然后回填缓存
2. **写操作**：先更新数据库，再删除缓存（而不是更新缓存），避免并发写导致的数据不一致
3. **缓存过期**：设置合理的 TTL，设备数据 TTL 短（5 分钟），业务数据 TTL 长（1 小时）
4. **主动失效**：数据变更时主动删除相关缓存，下次读取时重新加载

---

## 四、面试题

### 问题 1：设备时序数据存储方案设计

**设计方案：**

1. **按月分表**：`device_data_record_yyyyMM` 按月创建新表，避免单表数据量过大
2. **分层存储**：Redis（热数据）→ MySQL 当月表（温数据）→ 历史聚合表（冷数据）
3. **数据聚合**：超过 30 天的原始数据按 1 分钟粒度聚合，减少存储量约 90%
4. **索引优化**：`(device_id, collect_time)` 联合索引覆盖大部分查询场景
5. **批量写入**：使用 RocketMQ 削峰，批量写入 MySQL，每 100 条或 1 秒 flush 一次

### 问题 2：Redis 缓存策略设计

**策略要点：**

1. **多级缓存**：L1 实时数据（5 分钟 TTL），L2 热点数据（1 小时 TTL），L3 业务数据（1 小时+ TTL）
2. **过期策略**：设备数据使用 TTL 自动过期，设备离线后自动清理
3. **批量操作**：`multiGet` 批量查询，减少网络往返
4. **缓存穿透防护**：缓存空值（短期 TTL）+ 布隆过滤器
5. **缓存雪崩防护**：缓存 TTL 添加随机偏移，避免大量缓存同时过期

### 问题 3：数据清理和归档策略

**策略方案：**

1. **定时任务**：每天凌晨 2:00 执行数据清理
2. **数据聚合**：30 天前的原始数据按 1 分钟粒度聚合，保留平均值/最大值/最小值
3. **数据删除**：聚合完成后删除原始数据，释放存储空间
4. **归档保留**：聚合数据保留 90 天，1 年以上的数据导出到文件存储
5. **执行监控**：记录每次清理的执行时间、处理数据量，异常时发送告警