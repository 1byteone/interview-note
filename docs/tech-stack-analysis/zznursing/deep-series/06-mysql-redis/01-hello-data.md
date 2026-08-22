# MySQL + Redis入门：IoT设备数据存储架构

## 1. 项目背景

### 1.1 IoT数据存储的挑战

在物联网（IoT）场景中，设备数据存储面临着与传统业务系统截然不同的挑战。以 zznursing 智慧养老院平台为例，每间房间部署了多种传感器——温湿度传感器、烟雾报警器、门磁传感器、红外人体感应器、紧急呼叫按钮等。一个中型养老院可能包含 200 个房间，每个房间 5-8 个设备，总计 1000-1600 台设备。每台设备每 10-30 秒上报一次数据，一天产生的数据量可达数百万条。

这些 IoT 数据具有几个显著特征：

- **高吞吐量（High Throughput）**：每秒可能有数百甚至数千条数据写入请求，关系型数据库单库难以承受。
- **时间序列特性（Time-Series）**：每条数据都带有时间戳，查询通常是按时间范围检索——"过去1小时的温度变化"、"昨天门磁触发的次数"。
- **实时性要求（Real-Time）**：护理人员需要实时查看老人的当前状态，数据延迟不能超过几秒。
- **冷热分层明显（Hot/Warm/Cold）**：最新数据（最近1小时）被高频访问，历史数据（超过30天）几乎不会被查询，但需要保留用于审计和数据分析。

### 1.2 zznursing 的数据架构：冷热分层存储

面对上述挑战，zznursing 采用了**冷热分层存储架构**，将数据按时间维度和访问频率分为三层：

```
+------------------+       +------------------+       +------------------+
|   热数据 (Hot)   |  -->  |  温数据 (Warm)   |  -->  |  冷数据 (Cold)   |
|   Redis 7.x      |       |  MySQL 8.0       |       |  归档表/OSS      |
|   最近1小时       |       |  最近7天         |       |  30天以上        |
|   毫秒级响应      |       |  毫秒-秒级响应   |       |  秒-分级响应     |
+------------------+       +------------------+       +------------------+
```

- **热数据层（Redis）**：存储最近1小时内所有设备的实时状态和最新采样值，支持毫秒级读写。Redis 的丰富数据结构为 IoT 场景提供了天然支持。
- **温数据层（MySQL）**：存储最近7天的详细设备数据，用于日常查询、报表生成和趋势分析。MySQL 8.0 的关系模型和 SQL 查询能力让复杂聚合分析变得简单。
- **冷数据层（归档表/OSS）**：超过30天的数据从 MySQL 主表迁移到归档表或对象存储，仅保留按月的聚合统计，用于长期审计和合规要求。

### 1.3 为什么需要两个数据库？

这涉及到分布式系统中最经典的 **CAP 定理** 的实践权衡：

- **MySQL** 优先保证 **一致性（Consistency）** 和 **可用性（Availability）**。它作为数据的权威来源（Source of Truth），通过 ACID 事务确保设备数据不会丢失或错乱。
- **Redis** 优先保证 **可用性（Availability）** 和 **分区容忍性（Partition Tolerance）**。它作为高速缓存层，牺牲了部分持久化能力来换取极致的读写性能。

在实际 IoT 场景中，没有任何单一数据库能完美满足所有需求。MySQL 的写入瓶颈（单表每秒几千条插入）在高频 IoT 场景下很快会成为瓶颈；而 Redis 的内存成本和有限的持久化能力使其不适合作为唯一存储引擎。两者的结合实现了优势互补——Redis 扛住写入洪峰，MySQL 保证数据不丢，归档层控制存储成本。

---

## 2. 核心概念

### 2.1 MySQL 时序数据表设计

IoT 设备数据在 MySQL 中存储的核心是**时序数据表**。这类表的设计有以下几个关键考量：

**表结构设计：**

```sql
CREATE TABLE device_data (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,          -- 主键，自增
    device_id VARCHAR(64) NOT NULL,                -- 设备唯一标识
    data_type VARCHAR(32) NOT NULL,                -- 数据类型：temperature, humidity, smoke, door_magnetic 等
    value DOUBLE NOT NULL,                         -- 采样数值
    unit VARCHAR(16) DEFAULT '',                   -- 单位：℃, %, ppm 等
    ts DATETIME NOT NULL,                          -- 设备采样时间戳
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 记录创建时间
    
    INDEX idx_device_ts (device_id, ts),           -- 复合索引：按设备+时间查询
    INDEX idx_ts (ts),                             -- 时间索引：按时间范围查询
    INDEX idx_device_type (device_id, data_type)   -- 设备+类型索引
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

**设计要点：**

- **device_id + ts 复合索引**：这是最核心的查询模式——查某个设备某段时间的数据。复合索引可以快速定位到目标数据范围。
- **避免过宽的表**：IoT 数据行数极大，每行应尽量精简。id 用 BIGINT（而非 UUID），字段用合适的最小类型。
- **表分区（Partitioning）**：生产环境中按时间（如按月或按周）进行 RANGE 分区，便于数据管理和归档。

### 2.2 Redis 数据结构选型

Redis 提供了多种数据结构，在 IoT 场景中各有用途：

**String（字符串）：设备实时状态**

每个设备的当前最新状态存储为一个 JSON 字符串，键名设计为 `device:status:{deviceId}`。

```
device:status:sensor_temp_001 -> {"value":25.3,"unit":"℃","timestamp":"2026-08-22T10:30:00","battery":85}
```

String 结构简单高效，读写都是 O(1) 复杂度，适合单条数据的快速存取。

**SortedSet（有序集合）：时间序列缓存**

每个设备最近1小时的数据点存储在 SortedSet 中，以时间戳作为分数（score），数据内容作为成员（member）。

```
device:timeseries:sensor_temp_001 -> [
    (member: "25.1", score: 1692685800),
    (member: "25.3", score: 1692685830),
    (member: "25.4", score: 1692685860)
]
```

SortedSet 的 `ZRANGEBYSCORE` 命令可以按时间范围查询，`ZREVRANGE` 可以获取最新几条数据，天然适合时序数据缓存。

**Hash（哈希表）：设备配置信息**

每个设备的静态配置信息存储在 Hash 中。

```
device:config:sensor_temp_001 -> {
    "name": "一楼温湿度传感器",
    "room": "A101",
    "type": "temperature",
    "reportInterval": "30",
    "alarmThreshold": "40.0"
}
```

Hash 可以单独读写某个字段，适合存储和更新设备配置这类结构化数据。

### 2.3 缓存模式

在 MySQL + Redis 双存储架构中，常用的缓存模式有三种：

**Cache-Aside（旁路缓存）——最常用**

```
读取：先查 Redis → 命中则返回 → 未命中则查 MySQL → 写入 Redis → 返回
写入：先写 MySQL → 删除或更新 Redis 缓存
```

这是 zznursing 采用的主要模式。优点是实现简单，缓存与数据库解耦，适合读多写少的场景。

**Write-Through（穿透写入）**

```
写入：先写 Redis → Redis 同步写入 MySQL
```

优点是数据一致性高，但写入延迟较大，且需要 Redis 端支持。

**Write-Behind（异步写入）**

```
写入：只写 Redis → 后台异步批量写入 MySQL
```

写入性能最高，但存在数据丢失风险（Redis 宕机时未刷入 MySQL 的数据会丢失），适合对一致性要求不高的场景。

### 2.4 数据生命周期管理

zznursing 中 IoT 数据的生命周期如下：

| 阶段 | 存储位置 | 保留时间 | 访问频率 | 响应要求 |
|------|---------|---------|---------|---------|
| 热数据 | Redis | 最近1小时 | 极高（秒级查询） | < 5ms |
| 温数据 | MySQL | 最近7天 | 高（分钟级查询） | < 50ms |
| 冷数据 | 归档表 | 30天以上 | 低（偶尔审计查询） | < 1s |

数据流转由定时任务驱动：每小时将 Redis 中超过1小时的数据批量写入 MySQL；每天凌晨将 MySQL 中超过7天的数据迁移到归档表。

### 2.5 时序查询索引优化

对于 `WHERE device_id = ? AND ts BETWEEN ? AND ?` 这类查询，索引优化至关重要：

- **复合索引顺序**：`(device_id, ts)` 比 `(ts, device_id)` 更优，因为先按设备过滤可以大幅缩小扫描范围。
- **覆盖索引**：如果查询只涉及少数几个字段，可以创建包含所有查询字段的复合索引，避免回表查询。
- **索引下推（Index Condition Pushdown）**：MySQL 8.0 的 ICP 优化可以在索引层面过滤数据，减少回表次数。

---

## 3. 从零搭建代码

本节我们从一个 Maven 空项目开始，搭建一个完整的 IoT 数据存储服务。项目使用 Spring Boot 3.2.x、H2 内存数据库（模拟 MySQL）、Spring Data Redis（可对接真实 Redis 或降级运行）。

### 3.1 项目结构

```
iot-data-storage/
├── pom.xml
├── src/main/java/com/zznursing/iot/
│   ├── DataStorageApplication.java          // 启动类
│   ├── config/
│   │   └── RedisConfig.java                 // Redis 配置
│   ├── entity/
│   │   └── DeviceDataEntity.java            // JPA 实体
│   ├── repository/
│   │   └── DeviceDataRepository.java        // JPA 仓库
│   ├── service/
│   │   └── DeviceDataService.java           // 业务服务
│   └── controller/
│       └── DeviceStatusController.java      // REST 控制器
├── src/main/resources/
│   └── application.yml                      // 配置文件
└── src/test/java/com/zznursing/iot/
    └── DataStorageApplicationTests.java     // 测试类
```

### 3.2 pom.xml

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         https://maven.apache.org/xsd/maven-4.0.0.xsd">

    <modelVersion>4.0.0</modelVersion>

    <!-- 使用 Spring Boot 3.2.x 作为父工程 -->
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.2.5</version>
        <relativePath/>
    </parent>

    <groupId>com.zznursing</groupId>
    <artifactId>iot-data-storage</artifactId>
    <version>1.0.0</version>
    <name>iot-data-storage</name>
    <description>zznursing IoT 设备数据存储演示项目</description>

    <properties>
        <java.version>17</java.version>
    </properties>

    <dependencies>
        <!-- Spring Boot Web 起步依赖，提供 REST API 能力 -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>

        <!-- Spring Boot Data JPA 起步依赖，提供 JPA/ORM 持久化能力 -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>

        <!-- H2 内存数据库，开发环境模拟 MySQL，无需安装外部数据库 -->
        <dependency>
            <groupId>com.h2database</groupId>
            <artifactId>h2</artifactId>
            <scope>runtime</scope>
        </dependency>

        <!-- Spring Boot Data Redis 起步依赖，提供 Redis 操作能力 -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-redis</artifactId>
        </dependency>

        <!-- Lombok，简化 POJO 的 getter/setter/构造器编写 -->
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>

        <!-- Jackson 数据绑定，用于 JSON 序列化/反序列化 -->
        <dependency>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-databind</artifactId>
        </dependency>

        <!-- Jackson 对 Java 8 时间类型的支持 -->
        <dependency>
            <groupId>com.fasterxml.jackson.datatype</groupId>
            <artifactId>jackson-datatype-jsr310</artifactId>
        </dependency>

        <!-- Spring Boot 测试起步依赖 -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <!-- Spring Boot Maven 打包插件 -->
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <configuration>
                    <excludes>
                        <exclude>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                        </exclude>
                    </excludes>
                </configuration>
            </plugin>
        </plugins>
    </build>

</project>
```

### 3.3 application.yml

```yaml
# ============================================================
# zznursing IoT 数据存储服务 - 应用配置
# 默认使用 H2 内存数据库 + Redis 可选配置
# ============================================================

# 服务端口号，与云服务器上实际部署端口一致
server:
  port: 8080

spring:
  # 应用名称，用于服务注册和日志标识
  application:
    name: iot-data-storage

  # ============================================================
  # 数据源配置：使用 H2 内存数据库模拟 MySQL
  # 生产环境替换为 MySQL 8.0 的 JDBC 连接串即可
  # ============================================================
  datasource:
    url: jdbc:h2:mem:iotdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE
    # H2 内存数据库连接串，DB_CLOSE_DELAY=-1 表示 JVM 退出前不关闭
    driver-class-name: org.h2.Driver
    # H2 数据库驱动类名
    username: sa
    # H2 默认用户名
    password:
    # H2 默认无密码

  # ============================================================
  # H2 Web 控制台配置：开发时可通过浏览器查看数据库内容
  # 访问地址：http://localhost:8080/h2-console
  # ============================================================
  h2:
    console:
      enabled: true
      # 启用 H2 Web 控制台
      path: /h2-console
      # 控制台访问路径

  # ============================================================
  # JPA / Hibernate 配置
  # ============================================================
  jpa:
    hibernate:
      ddl-auto: update
      # ddl-auto=update 表示根据实体类自动建表，开发阶段方便
      # 生产环境建议使用 validate 或手动管理 DDL
    show-sql: true
    # 控制台打印 SQL 语句，便于调试
    properties:
      hibernate:
        format_sql: true
        # 格式化 SQL 输出，提高可读性
        # 使用 H2 的 MySQL 方言模式，尽量模拟 MySQL 行为
        dialect: org.hibernate.dialect.H2Dialect

  # ============================================================
  # Redis 配置（可选）
  # 如果本地没有 Redis，注释掉 spring.redis 部分即可
  # 服务会以降级模式运行（仅使用 MySQL 存储）
  # ============================================================
  redis:
    host: localhost
    # Redis 服务器地址
    port: 6379
    # Redis 服务器端口
    database: 0
    # Redis 数据库索引，默认 0
    timeout: 3000ms
    # 连接超时时间
    lettuce:
      pool:
        max-active: 16
        # 连接池最大活跃连接数
        max-idle: 8
        # 连接池最大空闲连接数
        min-idle: 2
        # 连接池最小空闲连接数

# ============================================================
# 日志级别配置
# ============================================================
logging:
  level:
    com.zznursing: DEBUG
    # 应用包级别日志输出 DEBUG 级别信息
    org.springframework.data.redis: INFO
    # Redis 相关日志输出 INFO 级别
```

### 3.4 DeviceDataEntity.java —— JPA 实体

```java
package com.zznursing.iot.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 设备数据实体类
 * 映射到数据库中的 device_data 表，存储 IoT 设备的采样数据
 *
 * 该实体对应 MySQL 中的温数据层（Warm Data Layer），
 * 存储最近 7 天的设备采样记录。
 */
@Entity
// @Entity 注解标记这是一个 JPA 实体，Hibernate 会为其创建数据库表
@Table(name = "device_data", indexes = {
        // 复合索引：按设备ID和时间戳查询，这是最核心的查询场景
        @Index(name = "idx_device_ts", columnList = "deviceId, ts"),
        // 单列索引：按时间范围查询
        @Index(name = "idx_ts", columnList = "ts"),
        // 复合索引：按设备ID和数据类型查询
        @Index(name = "idx_device_type", columnList = "deviceId, dataType")
})
@Data
// @Data 是 Lombok 注解，自动生成 getter、setter、toString、equals、hashCode
@Builder
// @Builder 是 Lombok 注解，提供建造者模式构建对象
@NoArgsConstructor
// 无参构造器，JPA 规范要求实体类必须有无参构造器
@AllArgsConstructor
// 全参构造器，配合 @Builder 使用
public class DeviceDataEntity {

    @Id
    // @Id 标记该字段为数据库主键
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    // @GeneratedValue 配置主键生成策略，IDENTITY 表示自增
    private Long id;
    // 主键 ID，使用 Long 类型而非 Integer，因为 IoT 数据量巨大

    @Column(nullable = false, length = 64)
    // @Column 配置数据库列属性：非空，最大长度 64
    private String deviceId;
    // 设备唯一标识，例如 "sensor_temp_001" 或 "smoke_alarm_A101"

    @Column(nullable = false, length = 32)
    private String dataType;
    // 数据类型，例如 "temperature"（温度）、"humidity"（湿度）、"smoke"（烟雾浓度）

    @Column(nullable = false)
    private Double value;
    // 采样数值，使用 Double 类型存储浮点型传感器数据

    @Column(length = 16)
    private String unit;
    // 数据单位，例如 "℃"（摄氏度）、"%"（百分比）、"ppm"（百万分比浓度）

    @Column(nullable = false)
    private LocalDateTime ts;
    // 设备采样的时间戳（设备端上报的时间，非服务端接收时间）

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
    // 记录创建时间（服务端接收时间），设置 updatable=false 防止被更新

    /**
     * 在持久化之前自动设置 createdAt 字段
     * 这是 JPA 的生命周期回调方法
     */
    @PrePersist
    // @PrePersist 注解标记的方法会在实体保存到数据库之前自动执行
    public void prePersist() {
        // 如果 createdAt 为 null，则设置为当前时间
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }
}
```

### 3.5 DeviceDataRepository.java —— JPA 仓库

```java
package com.zznursing.iot.repository;

import com.zznursing.iot.entity.DeviceDataEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 设备数据 JPA 仓库接口
 * 继承 JpaRepository 获得基础的 CRUD 操作能力
 * 泛型参数：<实体类型, 主键类型>
 */
@Repository
// @Repository 注解标记这是一个 Spring Data 仓库，会被 Spring 扫描并注入
public interface DeviceDataRepository extends JpaRepository<DeviceDataEntity, Long> {

    /**
     * 按设备ID和时间范围查询数据
     * Spring Data JPA 会自动根据方法名生成查询实现
     * 方法命名规则：findBy + 字段名 + And + 字段名 + Between
     *
     * 生成的 SQL 类似：
     * SELECT * FROM device_data
     * WHERE device_id = ?1 AND ts BETWEEN ?2 AND ?3
     * ORDER BY ts ASC
     *
     * @param deviceId 设备ID
     * @param start    开始时间
     * @param end      结束时间
     * @return 按时间升序排列的数据列表
     */
    List<DeviceDataEntity> findByDeviceIdAndTsBetweenOrderByTsAsc(
            String deviceId,
            LocalDateTime start,
            LocalDateTime end);

    /**
     * 查询某设备最新的 N 条数据
     * Top10 表示限制返回前 10 条，OrderByTsDesc 表示按时间戳降序排列
     * 这样就能得到最新的 10 条记录
     *
     * @param deviceId 设备ID
     * @return 最新的 10 条数据
     */
    List<DeviceDataEntity> findTop10ByDeviceIdOrderByTsDesc(String deviceId);

    /**
     * 使用 JPQL 自定义查询：统计某设备在指定时间范围内的数据条数
     * 这在仪表盘统计场景中很常见
     *
     * @param deviceId 设备ID
     * @param start    开始时间
     * @param end      结束时间
     * @return 数据条数
     */
    @Query("SELECT COUNT(d) FROM DeviceDataEntity d WHERE d.deviceId = :deviceId AND d.ts BETWEEN :start AND :end")
    // @Query 注解允许我们编写自定义的 JPQL 查询语句，:param 是命名参数占位符
    long countByDeviceIdAndTsBetween(
            @Param("deviceId") String deviceId,
            // @Param 将方法参数绑定到 JPQL 中的命名参数
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    /**
     * 查询某设备在指定时间范围内的最新一条数据
     * 使用 JPQL 子查询实现，限制返回结果为 1 条
     *
     * @param deviceId 设备ID
     * @param start    开始时间
     * @param end      结束时间
     * @return 时间范围内的最新数据（Optional 包装，可能为空）
     */
    @Query("SELECT d FROM DeviceDataEntity d WHERE d.deviceId = :deviceId AND d.ts BETWEEN :start AND :end ORDER BY d.ts DESC LIMIT 1")
    Optional<DeviceDataEntity> findLatestByDeviceIdAndTsBetween(
            @Param("deviceId") String deviceId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    /**
     * 删除指定时间之前的所有数据（用于数据归档和清理）
     * 配合 @Modifying 注解表示这是一个修改操作，会触发事务
     * 注意：@Modifying 必须配合 @Transactional 使用
     *
     * @param beforeTime 时间阈值，删除该时间之前的所有数据
     * @return 删除的记录条数
     */
    @Modifying
    // @Modifying 表示这是一个 UPDATE/DELETE 操作，非 SELECT 查询
    @Query("DELETE FROM DeviceDataEntity d WHERE d.ts < :beforeTime")
    int deleteByTsBefore(@Param("beforeTime") LocalDateTime beforeTime);
}
```

### 3.6 DeviceDataService.java —— 业务服务层

```java
package com.zznursing.iot.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.zznursing.iot.entity.DeviceDataEntity;
import com.zznursing.iot.repository.DeviceDataRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 设备数据服务层
 * 实现冷热分层存储的核心业务逻辑：
 * - 热数据（Hot）：Redis，最近1小时，毫秒级响应
 * - 温数据（Warm）：MySQL（演示用 H2），最近7天，毫秒-秒级响应
 *
 * 缓存模式：Cache-Aside（旁路缓存）
 *   读取：先查 Redis -> 命中则返回 -> 未命中则查 MySQL -> 写入 Redis -> 返回
 *   写入：先写 MySQL -> 更新 Redis 缓存
 */
@Slf4j
// @Slf4j 是 Lombok 注解，自动生成 log 对象，便于日志输出
@Service
// @Service 注解标记这是一个 Spring 服务层 Bean，会被自动扫描注入
public class DeviceDataService {

    // ==================== 常量定义 ====================

    // Redis 键前缀：设备实时状态，完整键如 "device:status:sensor_temp_001"
    private static final String REDIS_KEY_STATUS_PREFIX = "device:status:";

    // Redis 键前缀：设备时间序列缓存，完整键如 "device:timeseries:sensor_temp_001"
    private static final String REDIS_KEY_TIMESERIES_PREFIX = "device:timeseries:";

    // 热数据在 Redis 中的过期时间：1 小时（单位：秒）
    private static final long HOT_DATA_TTL_SECONDS = 3600;

    // 时间格式化器，用于日志输出
    private static final DateTimeFormatter DT_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // ==================== 依赖注入 ====================

    @Autowired
    // @Autowired 自动注入 JPA 仓库，Spring 会自动提供实现类
    private DeviceDataRepository deviceDataRepository;

    @Autowired
    // @Autowired 自动注入 StringRedisTemplate，这是 Spring Data Redis 提供的模板类
    // StringRedisTemplate 是 RedisTemplate 的特化版本，其 key 和 value 的序列化方式都是 String
    private StringRedisTemplate stringRedisTemplate;

    // Jackson 的 ObjectMapper，用于对象和 JSON 字符串之间的转换
    private final ObjectMapper objectMapper;

    /**
     * 构造器：初始化 ObjectMapper 并注册 Java 8 时间模块
     * 这样 LocalDateTime 等类型才能正确序列化/反序列化
     */
    public DeviceDataService() {
        this.objectMapper = new ObjectMapper();
        // 注册 Java 8 时间模块，支持 LocalDateTime 的序列化
        this.objectMapper.registerModule(new JavaTimeModule());
        // 配置不将日期写为时间戳，而是写为 ISO-8601 字符串格式
        this.objectMapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    // ==================== 核心业务方法 ====================

    /**
     * 保存设备数据：写入 MySQL + 更新 Redis 缓存
     * 这是 Cache-Aside 模式的写入路径
     *
     * 步骤：
     * 1. 将数据保存到 MySQL（通过 JPA Repository）
     * 2. 将设备最新状态更新到 Redis（String 结构）
     * 3. 将数据点追加到 Redis 时间序列缓存（SortedSet 结构）
     * 4. 设置 Redis 键的过期时间，确保热数据不会无限增长
     *
     * @param deviceId 设备ID
     * @param dataType 数据类型
     * @param value    采样值
     * @param unit     单位
     * @param ts       采样时间戳
     * @return 持久化后的实体对象
     */
    @Transactional
    // @Transactional 开启事务，确保数据完整性和一致性
    // 如果方法中抛出异常，所有数据库操作会回滚
    public DeviceDataEntity saveDeviceData(String deviceId, String dataType,
                                           Double value, String unit,
                                           LocalDateTime ts) {

        // 第一步：构建实体对象并保存到数据库
        // 使用 @Builder 模式创建 DeviceDataEntity 实例
        DeviceDataEntity entity = DeviceDataEntity.builder()
                .deviceId(deviceId)           // 设置设备ID
                .dataType(dataType)            // 设置数据类型
                .value(value)                  // 设置采样数值
                .unit(unit)                    // 设置单位
                .ts(ts)                        // 设置采样时间戳
                .build();                      // 构建对象

        // 调用 JPA Repository 的 save 方法，将实体持久化到数据库
        // 返回的 savedEntity 包含了自动生成的 id 和 createdAt
        DeviceDataEntity savedEntity = deviceDataRepository.save(entity);

        // 记录日志：设备数据已保存到数据库
        log.debug("数据已保存到数据库: deviceId={}, value={}{}, ts={}",
                deviceId, value, unit, ts.format(DT_FORMATTER));

        // 第二步：更新 Redis 中的设备实时状态
        // 将设备的最新采样数据以 JSON 格式存入 Redis String
        updateDeviceStatusInRedis(deviceId, dataType, value, unit, ts);

        // 第三步：将数据点追加到 Redis 时间序列缓存
        // 用于最近1小时内的快速时序查询
        addToTimeSeriesCache(deviceId, value, ts);

        // 返回包含完整信息的实体对象
        return savedEntity;
    }

    /**
     * 更新 Redis 中的设备实时状态
     * 使用 Redis String 结构，键为 "device:status:{deviceId}"
     * 值为 JSON 格式的字符串，包含最新的设备状态信息
     *
     * @param deviceId 设备ID
     * @param dataType 数据类型
     * @param value    采样值
     * @param unit     单位
     * @param ts       采样时间戳
     */
    private void updateDeviceStatusInRedis(String deviceId, String dataType,
                                           Double value, String unit,
                                           LocalDateTime ts) {
        try {
            // 构建状态 Map，包含设备的最新状态信息
            Map<String, Object> statusMap = new HashMap<>();
            statusMap.put("deviceId", deviceId);    // 设备ID
            statusMap.put("dataType", dataType);     // 数据类型
            statusMap.put("value", value);            // 最新采样值
            statusMap.put("unit", unit);              // 单位
            statusMap.put("timestamp", ts.toString()); // 时间戳转字符串
            statusMap.put("updatedAt", LocalDateTime.now().toString()); // 更新时间

            // 将 Map 序列化为 JSON 字符串
            String statusJson = objectMapper.writeValueAsString(statusMap);

            // 构建 Redis 键名
            String redisKey = REDIS_KEY_STATUS_PREFIX + deviceId;

            // 将 JSON 字符串写入 Redis，并设置过期时间（1小时）
            // 过期时间确保 Redis 不会存储过时的数据
            stringRedisTemplate.opsForValue().set(redisKey, statusJson, HOT_DATA_TTL_SECONDS, TimeUnit.SECONDS);

            // 记录日志：设备实时状态已更新到 Redis 缓存
            log.debug("Redis 缓存已更新: key={}, value={}", redisKey, statusJson);

        } catch (JsonProcessingException e) {
            // JSON 序列化异常处理：记录错误日志但不会影响主流程
            // 即使 Redis 缓存更新失败，数据已经保存到 MySQL，不会丢失
            log.error("Redis 状态序列化失败: deviceId={}", deviceId, e);
        }
    }

    /**
     * 将数据点追加到 Redis 时间序列缓存
     * 使用 Redis SortedSet 结构，按时间戳排序
     * 键为 "device:timeseries:{deviceId}"
     * member 为数据点的 JSON 字符串，score 为时间戳的 epoch 秒数
     *
     * @param deviceId 设备ID
     * @param value    采样值
     * @param ts       采样时间戳
     */
    private void addToTimeSeriesCache(String deviceId, Double value, LocalDateTime ts) {
        try {
            // 构建 Redis 键名
            String redisKey = REDIS_KEY_TIMESERIES_PREFIX + deviceId;

            // 将时间戳转换为 epoch 秒数，作为 SortedSet 的 score
            // ZonedDateTime 转换确保时区正确处理
            long score = ts.atZone(java.time.ZoneId.systemDefault()).toEpochSecond();

            // 将数据值转为字符串作为 member
            // 注意：这里简化为只存数值，生产环境可以存 JSON 字符串包含更多信息
            String member = String.valueOf(value);

            // 将数据点添加到 SortedSet 中
            // ZADD 命令：如果 member 已存在则更新 score（这里不会重复因为时间戳不同）
            stringRedisTemplate.opsForZSet().add(redisKey, member, score);

            // 设置 SortedSet 的过期时间，防止 Redis 内存无限增长
            stringRedisTemplate.expire(redisKey, HOT_DATA_TTL_SECONDS, TimeUnit.SECONDS);

            // 记录日志：数据点已添加到 Redis 时间序列缓存
            log.debug("Redis 时序缓存已更新: key={}, value={}, score={}", redisKey, value, score);

        } catch (Exception e) {
            // 异常处理：Redis 操作失败不影响主流程
            log.error("Redis 时序缓存更新失败: deviceId={}", deviceId, e);
        }
    }

    /**
     * 获取设备最新数据：先查 Redis 缓存，未命中则查数据库
     * 这是 Cache-Aside 模式的读取路径
     *
     * @param deviceId 设备ID
     * @return 最新的设备数据实体（Optional 包装，可能为空）
     */
    public Optional<DeviceDataEntity> getLatestData(String deviceId) {

        // 第一步：尝试从 Redis 缓存中获取设备实时状态
        // 键格式为 "device:status:{deviceId}"
        String redisKey = REDIS_KEY_STATUS_PREFIX + deviceId;
        String cachedJson = stringRedisTemplate.opsForValue().get(redisKey);

        // 如果 Redis 缓存命中
        if (cachedJson != null && !cachedJson.isEmpty()) {
            try {
                // 将 JSON 字符串反序列化为 Map
                // 注意：这里从 Redis 获取的是部分字段，我们需要将其转换为 DeviceDataEntity
                @SuppressWarnings("unchecked")
                Map<String, Object> statusMap = objectMapper.readValue(cachedJson, Map.class);

                // 从 Map 中提取各个字段的值
                String dataType = (String) statusMap.get("dataType");   // 数据类型
                Double value = (Double) statusMap.get("value");          // 采样值
                String unit = (String) statusMap.get("unit");            // 单位
                String timestampStr = (String) statusMap.get("timestamp"); // 时间戳字符串

                // 将时间戳字符串解析为 LocalDateTime
                LocalDateTime timestamp = LocalDateTime.parse(timestampStr);

                // 构建 DeviceDataEntity 对象并返回
                // 注意：从 Redis 获取的数据没有 id 和 createdAt，因为这些字段在 MySQL 中才完整
                DeviceDataEntity entity = DeviceDataEntity.builder()
                        .deviceId(deviceId)       // 设置设备ID
                        .dataType(dataType)        // 设置数据类型
                        .value(value)              // 设置采样值
                        .unit(unit)                // 设置单位
                        .ts(timestamp)             // 设置时间戳
                        .build();

                // 记录日志：缓存命中
                log.debug("Redis 缓存命中: deviceId={}, value={}", deviceId, value);

                // 返回包装在 Optional 中的实体
                return Optional.of(entity);

            } catch (Exception e) {
                // JSON 解析失败时，记录错误并降级到数据库查询
                log.warn("Redis 缓存解析失败，降级到数据库查询: deviceId={}", deviceId, e);
            }
        }

        // 第二步：缓存未命中或解析失败，从数据库查询
        // 查询该设备最新的 1 条数据（按时间戳降序，取第一条）
        List<DeviceDataEntity> topList = deviceDataRepository
                .findTop10ByDeviceIdOrderByTsDesc(deviceId);

        // 如果有数据，取第一条（即最新的一条）
        if (!topList.isEmpty()) {
            DeviceDataEntity latestEntity = topList.get(0);

            // 缓存预热：将数据库查询结果写入 Redis，下次查询时可直接命中缓存
            // 这是 Cache-Aside 模式的"回填"步骤
            updateDeviceStatusInRedis(
                    latestEntity.getDeviceId(),
                    latestEntity.getDataType(),
                    latestEntity.getValue(),
                    latestEntity.getUnit(),
                    latestEntity.getTs());

            // 记录日志：从数据库获取并回填 Redis 缓存
            log.debug("从数据库获取最新数据，并回填 Redis 缓存: deviceId={}", deviceId);

            return Optional.of(latestEntity);
        }

        // 第三步：Redis 和数据库都没有数据，返回空 Optional
        log.warn("未找到设备数据: deviceId={}", deviceId);
        return Optional.empty();
    }

    /**
     * 获取设备历史数据：直接查询 MySQL（温数据层）
     * 对于历史数据查询，直接从数据库读取，不经过 Redis 缓存
     * 因为历史数据范围较大，缓存命中率低，且缓存大量历史数据会浪费内存
     *
     * @param deviceId 设备ID
     * @param start    开始时间
     * @param end      结束时间
     * @return 历史数据列表，按时间升序排列
     */
    public List<DeviceDataEntity> getHistoryData(String deviceId,
                                                  LocalDateTime start,
                                                  LocalDateTime end) {

        // 记录日志：查询历史数据范围
        log.info("查询历史数据: deviceId={}, start={}, end={}",
                deviceId, start.format(DT_FORMATTER), end.format(DT_FORMATTER));

        // 调用 JPA Repository 的自定义查询方法
        // 生成的 SQL 会利用复合索引 (device_id, ts) 进行高效查询
        List<DeviceDataEntity> historyList = deviceDataRepository
                .findByDeviceIdAndTsBetweenOrderByTsAsc(deviceId, start, end);

        // 记录日志：查询结果数量
        log.debug("历史数据查询结果: deviceId={}, count={}", deviceId, historyList.size());

        return historyList;
    }

    /**
     * 获取设备实时状态：从 Redis 缓存中读取
     * 这个方法只查 Redis，不查数据库
     * 用于实时监控面板等需要极低延迟的场景
     *
     * @param deviceId 设备ID
     * @return 设备状态信息 Map（包含设备ID、最新值、时间戳等），如果未找到则返回空 Map
     */
    public Map<String, Object> getDeviceStatus(String deviceId) {

        // 构建 Redis 键名
        String redisKey = REDIS_KEY_STATUS_PREFIX + deviceId;

        // 从 Redis 获取设备状态 JSON 字符串
        String cachedJson = stringRedisTemplate.opsForValue().get(redisKey);

        // 如果缓存命中
        if (cachedJson != null && !cachedJson.isEmpty()) {
            try {
                // 反序列化 JSON 为 Map
                @SuppressWarnings("unchecked")
                Map<String, Object> statusMap = objectMapper.readValue(cachedJson, Map.class);

                // 记录日志：从 Redis 获取设备实时状态
                log.debug("获取设备实时状态(Redis): deviceId={}", deviceId);

                // 返回状态信息
                return statusMap;

            } catch (Exception e) {
                // JSON 解析失败，记录错误
                log.warn("设备状态 JSON 解析失败: deviceId={}", deviceId, e);
            }
        }

        // 缓存未命中，返回空 Map
        log.debug("设备实时状态未缓存: deviceId={}", deviceId);
        return Collections.emptyMap();
    }

    /**
     * 批量保存设备数据（模拟设备批量上报场景）
     * 使用 @Transactional 确保批量操作的事务性
     * 生产环境中可结合 Redis Pipeline 提升性能
     *
     * @param dataList 设备数据列表，每个元素包含 deviceId, dataType, value, unit, ts
     */
    @Transactional
    public void batchSaveDeviceData(List<DeviceDataEntity> dataList) {

        // 记录日志：开始批量保存
        log.info("批量保存设备数据: count={}", dataList.size());

        // 遍历数据列表，逐条保存
        // 生产环境可以使用 Redis Pipeline 批量写入 Redis
        // 使用 JPA 的 saveAll 方法批量写入数据库
        for (DeviceDataEntity data : dataList) {
            // 调用单条保存方法，写入数据库并更新 Redis 缓存
            saveDeviceData(
                    data.getDeviceId(),
                    data.getDataType(),
                    data.getValue(),
                    data.getUnit(),
                    data.getTs()
            );
        }

        // 记录日志：批量保存完成
        log.info("批量保存完成: count={}", dataList.size());
    }

    /**
     * 清理过期数据（模拟定时任务的数据归档清理操作）
     * 删除指定时间之前的所有设备数据
     * 生产环境中会先归档到冷数据表，再执行删除
     *
     * @param beforeTime 时间阈值，删除该时间之前的所有数据
     * @return 删除的记录条数
     */
    @Transactional
    public int cleanExpiredData(LocalDateTime beforeTime) {

        // 记录日志：开始清理过期数据
        log.info("清理过期数据: beforeTime={}", beforeTime.format(DT_FORMATTER));

        // 执行删除操作
        int deletedCount = deviceDataRepository.deleteByTsBefore(beforeTime);

        // 记录日志：清理完成
        log.info("过期数据清理完成: deletedCount={}", deletedCount);

        return deletedCount;
    }
}
```

### 3.7 DeviceStatusController.java —— REST 控制器

```java
package com.zznursing.iot.controller;

import com.zznursing.iot.entity.DeviceDataEntity;
import com.zznursing.iot.service.DeviceDataService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 设备状态 REST 控制器
 * 提供 IoT 设备数据的 CRUD 接口，供前端和第三方系统调用
 *
 * 接口设计遵循 RESTful 风格：
 * - POST /api/data —— 保存设备数据
 * - GET /api/data/{deviceId}/latest —— 获取设备最新数据
 * - GET /api/data/{deviceId}/history —— 获取设备历史数据
 * - GET /api/data/{deviceId}/status —— 获取设备实时状态
 */
@Slf4j
// @Slf4j 自动生成 log 日志对象
@RestController
// @RestController = @Controller + @ResponseBody，所有方法返回 JSON 格式
@RequestMapping("/api/data")
// @RequestMapping 设置控制器的基础路径为 /api/data
public class DeviceStatusController {

    @Autowired
    // 自动注入设备数据服务层
    private DeviceDataService deviceDataService;

    /**
     * 保存设备数据
     * 接口：POST /api/data
     * 请求体示例：
     * {
     *   "deviceId": "sensor_temp_001",
     *   "dataType": "temperature",
     *   "value": 25.3,
     *   "unit": "℃",
     *   "ts": "2026-08-22T10:30:00"
     * }
     *
     * @param requestBody 请求体，包含设备数据信息
     * @return 保存后的完整实体对象
     */
    @PostMapping
    // @PostMapping 映射 HTTP POST 请求到该方法
    public ResponseEntity<DeviceDataEntity> saveData(
            @RequestBody Map<String, Object> requestBody) {
            // @RequestBody 将请求体 JSON 自动绑定到 Map 参数

        // 从请求体中提取各个字段
        String deviceId = (String) requestBody.get("deviceId");     // 设备ID
        String dataType = (String) requestBody.get("dataType");      // 数据类型
        Double value = Double.valueOf(requestBody.get("value").toString()); // 采样值
        String unit = (String) requestBody.get("unit");              // 单位
        String tsStr = (String) requestBody.get("ts");               // 时间戳字符串

        // 将时间戳字符串解析为 LocalDateTime
        LocalDateTime ts = LocalDateTime.parse(tsStr);

        // 记录日志：收到保存请求
        log.info("收到保存请求: deviceId={}, value={}, ts={}", deviceId, value, tsStr);

        // 调用服务层保存数据
        DeviceDataEntity savedEntity = deviceDataService.saveDeviceData(
                deviceId, dataType, value, unit, ts);

        // 返回 200 OK 响应，包含保存后的实体数据
        return ResponseEntity.ok(savedEntity);
    }

    /**
     * 获取设备最新数据
     * 接口：GET /api/data/{deviceId}/latest
     * 先查 Redis 缓存，未命中则查数据库，并将结果回填缓存
     *
     * @param deviceId 设备ID（路径变量）
     * @return 最新的设备数据，如果未找到则返回 404
     */
    @GetMapping("/{deviceId}/latest")
    // @GetMapping 映射 HTTP GET 请求，{deviceId} 是路径变量
    public ResponseEntity<DeviceDataEntity> getLatestData(
            @PathVariable String deviceId) {
            // @PathVariable 将 URL 路径中的 {deviceId} 绑定到方法参数

        // 记录日志：收到查询最新数据请求
        log.info("查询最新数据: deviceId={}", deviceId);

        // 调用服务层获取最新数据
        Optional<DeviceDataEntity> result = deviceDataService.getLatestData(deviceId);

        // 如果数据存在，返回 200 OK；如果不存在，返回 404 Not Found
        return result.map(ResponseEntity::ok)
                // map 操作：如果 Optional 有值，返回 200 + 实体
                .orElseGet(() -> ResponseEntity.notFound().build());
                // orElseGet：如果 Optional 为空，返回 404
    }

    /**
     * 获取设备历史数据
     * 接口：GET /api/data/{deviceId}/history?start=...&end=...
     * 直接查询 MySQL 数据库
     *
     * @param deviceId 设备ID
     * @param start    开始时间（格式：yyyy-MM-dd HH:mm:ss）
     * @param end      结束时间（格式：yyyy-MM-dd HH:mm:ss）
     * @return 历史数据列表，按时间升序排列
     */
    @GetMapping("/{deviceId}/history")
    public ResponseEntity<List<DeviceDataEntity>> getHistoryData(
            @PathVariable String deviceId,
            // 开始时间参数，使用 @DateTimeFormat 指定日期格式
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime start,
            // 结束时间参数，同样指定日期格式
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime end) {

        // 记录日志：收到历史数据查询请求
        log.info("查询历史数据: deviceId={}, start={}, end={}", deviceId, start, end);

        // 校验参数：开始时间不能晚于结束时间
        if (start.isAfter(end)) {
            // 如果参数不合法，返回 400 Bad Request
            return ResponseEntity.badRequest().build();
        }

        // 调用服务层查询历史数据
        List<DeviceDataEntity> historyData = deviceDataService.getHistoryData(deviceId, start, end);

        // 返回 200 OK 响应，包含历史数据列表
        return ResponseEntity.ok(historyData);
    }

    /**
     * 获取设备实时状态
     * 接口：GET /api/data/{deviceId}/status
     * 仅从 Redis 缓存中读取，不查数据库，响应速度极快
     * 用于实时监控面板等高频刷新场景
     *
     * @param deviceId 设备ID
     * @return 设备实时状态信息
     */
    @GetMapping("/{deviceId}/status")
    public ResponseEntity<Map<String, Object>> getDeviceStatus(
            @PathVariable String deviceId) {

        // 记录日志：收到实时状态查询请求
        log.info("查询实时状态: deviceId={}", deviceId);

        // 调用服务层从 Redis 获取设备实时状态
        Map<String, Object> status = deviceDataService.getDeviceStatus(deviceId);

        // 如果状态数据不为空，返回 200 OK；否则返回 404 Not Found
        if (status.isEmpty()) {
            // 状态数据为空，返回 404
            return ResponseEntity.notFound().build();
        }

        // 返回 200 OK 响应，包含设备实时状态信息
        return ResponseEntity.ok(status);
    }
}
```

### 3.8 RedisConfig.java —— Redis 配置

```java
package com.zznursing.iot.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis 配置类
 * 配置 RedisTemplate 的序列化方式，确保存入 Redis 的数据可读
 *
 * 关键配置：
 * 1. 使用 StringRedisSerializer 序列化 key 和 value
 * 2. 确保 JSON 格式的数据在 Redis 中可读
 * 3. 配置连接工厂（由 Spring Boot 自动配置）
 */
@Configuration
// @Configuration 注解标记这是一个配置类，Spring 会在启动时加载它
public class RedisConfig {

    /**
     * 配置 RedisTemplate Bean
     * 使用 StringRedisSerializer 作为 key 和 value 的序列化器
     * 这样 Redis 中的键值对都是可读的字符串格式，方便调试
     *
     * Spring Data Redis 默认使用 JdkSerializationRedisSerializer，
     * 会导致 Redis 中存储的是二进制数据，不可读。
     * 本配置覆盖默认行为，改为字符串序列化。
     *
     * @param redisConnectionFactory Spring Boot 自动注入的 Redis 连接工厂
     * @return 配置好的 RedisTemplate 实例
     */
    @Bean
    // @Bean 注解标记该方法返回的对象会被注册为 Spring 容器中的 Bean
    public RedisTemplate<String, Object> redisTemplate(
            RedisConnectionFactory redisConnectionFactory) {

        // 创建 RedisTemplate 实例
        // 泛型参数：<String, Object> 表示 key 为 String，value 为 Object
        RedisTemplate<String, Object> template = new RedisTemplate<>();

        // 设置连接工厂，这是 RedisTemplate 与 Redis 服务器通信的基础
        template.setConnectionFactory(redisConnectionFactory);

        // 创建字符串序列化器，使用 UTF-8 编码
        // StringRedisSerializer 会将所有 key 和 value 序列化为可读的字符串
        StringRedisSerializer stringSerializer = new StringRedisSerializer();

        // 设置 key 的序列化方式为字符串序列化
        // Redis 的 key 通常都是字符串，使用字符串序列化最直观
        template.setKeySerializer(stringSerializer);

        // 设置 value 的序列化方式为字符串序列化
        // 这样 Redis 中存储的值是 JSON 字符串，而不是二进制数据
        template.setValueSerializer(stringSerializer);

        // 设置 Hash 类型 key 的序列化方式
        template.setHashKeySerializer(stringSerializer);

        // 设置 Hash 类型 value 的序列化方式
        template.setHashValueSerializer(stringSerializer);

        // 初始化 RedisTemplate，应用所有配置
        template.afterPropertiesSet();

        // 返回配置完成的 RedisTemplate
        return template;
    }

    /**
     * 配置 StringRedisTemplate Bean
     * StringRedisTemplate 是 RedisTemplate 的特化版本，
     * 其 key 和 value 的序列化方式固定为 StringRedisSerializer
     *
     * 本服务中直接使用 StringRedisTemplate 而非 RedisTemplate，
     * 因为我们的数据都是 JSON 字符串格式
     *
     * @param redisConnectionFactory Redis 连接工厂
     * @return StringRedisTemplate 实例
     */
    @Bean
    public StringRedisTemplate stringRedisTemplate(
            RedisConnectionFactory redisConnectionFactory) {

        // 直接创建 StringRedisTemplate，传入连接工厂
        // StringRedisTemplate 的序列化方式已经固定为字符串序列化
        return new StringRedisTemplate(redisConnectionFactory);
    }
}
```

### 3.9 DataStorageApplication.java —— 启动类

```java
package com.zznursing.iot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * zznursing IoT 数据存储服务 - 启动类
 *
 * 这个微服务提供了 IoT 设备数据的冷热分层存储能力：
 * - Redis：实时状态缓存（热数据，1小时过期）
 * - JPA（H2/MySQL）：持久化存储（温数据，7天保留）
 *
 * 技术栈：Spring Boot 3.2.x + Spring Data JPA + Spring Data Redis + H2
 *
 * 启动后访问：
 * - REST API: http://localhost:8080/api/data/...
 * - H2 控制台: http://localhost:8080/h2-console
 */
@SpringBootApplication
// @SpringBootApplication = @Configuration + @EnableAutoConfiguration + @ComponentScan
// 这个注解标记这是 Spring Boot 应用的入口，会自动扫描当前包及子包下的所有 Spring 组件
public class DataStorageApplication {

    /**
     * 应用主入口方法
     * 使用 SpringApplication.run() 启动 Spring Boot 应用
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        // 启动 Spring Boot 应用
        // run() 方法会启动内嵌的 Web 服务器（默认 Tomcat）
        // 自动配置数据源、JPA、Redis 等组件
        SpringApplication.run(DataStorageApplication.class, args);
    }
}
```

### 3.10 DataStorageApplicationTests.java —— 测试类

```java
package com.zznursing.iot;

import com.zznursing.iot.entity.DeviceDataEntity;
import com.zznursing.iot.repository.DeviceDataRepository;
import com.zznursing.iot.service.DeviceDataService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * IoT 数据存储服务 - 集成测试类
 *
 * 测试冷热分层存储的核心功能：
 * 1. 保存数据：同时写入 MySQL（H2）和 Redis
 * 2. 查询最新数据：验证 Cache-Aside 模式
 * 3. 查询历史数据：验证 MySQL 查询
 * 4. 查询实时状态：验证 Redis 缓存
 * 5. 批量保存：验证批量写入
 */
@SpringBootTest
// @SpringBootTest 注解表示这是一个 Spring Boot 集成测试
// 它会启动完整的 Spring 应用上下文，加载所有配置和 Bean
class DataStorageApplicationTests {

    // ==================== 注入测试所需的 Bean ====================

    @Autowired
    // 自动注入设备数据服务层，测试核心业务逻辑
    private DeviceDataService deviceDataService;

    @Autowired
    // 自动注入 JPA 仓库，用于直接验证数据库中的数据
    private DeviceDataRepository deviceDataRepository;

    @Autowired
    // 自动注入 StringRedisTemplate，用于直接验证 Redis 中的数据
    private StringRedisTemplate stringRedisTemplate;

    // 测试用的设备ID常量
    private static final String TEST_DEVICE_ID = "sensor_temp_test_001";

    // 测试用的数据类型常量
    private static final String TEST_DATA_TYPE = "temperature";

    // 测试基准时间：用于构建测试数据的时间戳
    private LocalDateTime baseTime;

    /**
     * 每个测试方法执行前的初始化工作
     * 清理测试数据，确保测试环境干净
     */
    @BeforeEach
    // @BeforeEach 注解标记的方法会在每个 @Test 方法执行前运行
    void setUp() {
        // 设置基准时间为当前时间
        baseTime = LocalDateTime.now();

        // 清理 Redis 中的测试数据，避免测试之间的干扰
        String redisStatusKey = "device:status:" + TEST_DEVICE_ID;
        String redisTimeseriesKey = "device:timeseries:" + TEST_DEVICE_ID;
        stringRedisTemplate.delete(redisStatusKey);
        stringRedisTemplate.delete(redisTimeseriesKey);

        // 清理数据库中的测试数据，确保每个测试从干净状态开始
        // 使用 JPA Repository 的删除方法
        // 注意：这里简化为删除所有数据，仅用于测试环境
        deviceDataRepository.deleteAll();
    }

    /**
     * 测试 1：保存设备数据并验证数据持久化
     *
     * 验证点：
     * - 数据成功保存到数据库（JPA）
     * - 数据成功缓存到 Redis（String 结构）
     * - 保存后返回的实体包含自动生成的 id 和 createdAt
     */
    @Test
    @DisplayName("保存设备数据 - 验证数据库和Redis双写")
    // @DisplayName 为测试方法提供可读的中文描述
    void testSaveDeviceData() {

        // 准备测试数据：温度25.3度，单位摄氏度
        Double testValue = 25.3;
        String testUnit = "℃";
        LocalDateTime testTs = baseTime;

        // 执行保存操作：调用服务层方法
        DeviceDataEntity savedEntity = deviceDataService.saveDeviceData(
                TEST_DEVICE_ID,   // 设备ID
                TEST_DATA_TYPE,    // 数据类型：temperature
                testValue,         // 采样值：25.3
                testUnit,          // 单位：℃
                testTs             // 采样时间
        );

        // ====== 断言 1：验证数据库持久化 ======

        // 验证：保存后的实体 ID 不为 null（说明数据库生成了自增主键）
        assertNotNull(savedEntity.getId(), "数据库生成的ID不应为空");

        // 验证：保存后的实体字段与输入一致
        assertEquals(TEST_DEVICE_ID, savedEntity.getDeviceId(), "设备ID应匹配");
        assertEquals(TEST_DATA_TYPE, savedEntity.getDataType(), "数据类型应匹配");
        assertEquals(testValue, savedEntity.getValue(), "采样值应匹配");
        assertEquals(testUnit, savedEntity.getUnit(), "单位应匹配");

        // 验证：createdAt 字段被自动填充
        assertNotNull(savedEntity.getCreatedAt(), "createdAt 应自动填充");

        // 验证：数据库中确实有这条记录
        Optional<DeviceDataEntity> dbEntity = deviceDataRepository.findById(savedEntity.getId());
        assertTrue(dbEntity.isPresent(), "数据库中应能找到保存的记录");
        assertEquals(testValue, dbEntity.get().getValue(), "数据库中的值应匹配");

        // ====== 断言 2：验证 Redis 缓存 ======

        // 验证：Redis 中存在设备状态缓存
        String redisStatusKey = "device:status:" + TEST_DEVICE_ID;
        String cachedStatus = stringRedisTemplate.opsForValue().get(redisStatusKey);
        assertNotNull(cachedStatus, "Redis 缓存不应为空");

        // 验证：缓存内容包含设备最新值
        assertTrue(cachedStatus.contains("25.3"), "Redis 缓存应包含最新的采样值");

        // 验证：Redis 中存在时间序列缓存
        String redisTimeseriesKey = "device:timeseries:" + TEST_DEVICE_ID;
        Long size = stringRedisTemplate.opsForZSet().size(redisTimeseriesKey);
        assertNotNull(size, "Redis 时序缓存不应为空");
        assertTrue(size > 0, "Redis 时序缓存应包含数据点");
    }

    /**
     * 测试 2：查询最新数据 - 验证 Cache-Aside 模式
     *
     * 流程：
     * 1. 先保存一条数据到数据库
     * 2. 查询最新数据，应命中 Redis 缓存
     * 3. 验证返回的数据是刚刚保存的
     */
    @Test
    @DisplayName("查询最新数据 - 验证Cache-Aside缓存模式")
    void testGetLatestData() {

        // 第一步：准备数据，保存一条测试数据
        Double testValue = 26.8;
        deviceDataService.saveDeviceData(
                TEST_DEVICE_ID, TEST_DATA_TYPE, testValue, "℃", baseTime);

        // 第二步：查询最新数据，应返回刚刚保存的数据
        Optional<DeviceDataEntity> latestData = deviceDataService.getLatestData(TEST_DEVICE_ID);

        // 验证：查询结果不为空
        assertTrue(latestData.isPresent(), "最新数据不应为空");

        // 验证：返回的值与刚刚保存的一致
        assertEquals(testValue, latestData.get().getValue(), "最新数据值应匹配");
        assertEquals(TEST_DEVICE_ID, latestData.get().getDeviceId(), "设备ID应匹配");
    }

    /**
     * 测试 3：查询历史数据 - 验证 MySQL 查询
     *
     * 流程：
     * 1. 按时间顺序保存多条数据
     * 2. 查询指定时间范围内的数据
     * 3. 验证返回的数据条数和时间顺序
     */
    @Test
    @DisplayName("查询历史数据 - 验证MySQL时间范围查询")
    void testGetHistoryData() {

        // 第一步：准备多条测试数据，时间间隔 1 分钟
        int dataCount = 5;
        for (int i = 0; i < dataCount; i++) {
            // 每条数据的时间戳依次递增 1 分钟
            LocalDateTime ts = baseTime.plusMinutes(i);
            // 数值依次递增 0.5
            Double value = 25.0 + i * 0.5;

            // 保存数据
            deviceDataService.saveDeviceData(
                    TEST_DEVICE_ID, TEST_DATA_TYPE, value, "℃", ts);
        }

        // 第二步：查询时间范围在 [baseTime, baseTime + 4分钟] 内的数据
        LocalDateTime queryStart = baseTime;
        LocalDateTime queryEnd = baseTime.plusMinutes(4);

        // 执行查询
        List<DeviceDataEntity> historyData = deviceDataService.getHistoryData(
                TEST_DEVICE_ID, queryStart, queryEnd);

        // 验证：返回的数据条数应为5条
        assertEquals(dataCount, historyData.size(), "历史数据条数应匹配");

        // 验证：数据按时间升序排列
        for (int i = 0; i < historyData.size() - 1; i++) {
            // 前一条数据的时间戳应早于或等于后一条
            assertTrue(
                    historyData.get(i).getTs().isBefore(historyData.get(i + 1).getTs())
                            || historyData.get(i).getTs().isEqual(historyData.get(i + 1).getTs()),
                    "历史数据应按时间升序排列"
            );
        }
    }

    /**
     * 测试 4：查询设备实时状态 - 验证 Redis 缓存
     *
     * 流程：
     * 1. 保存数据到数据库
     * 2. 查询设备实时状态（只查 Redis）
     * 3. 验证返回的状态信息包含设备最新数据
     */
    @Test
    @DisplayName("查询设备实时状态 - 验证Redis缓存读取")
    void testGetDeviceStatus() {

        // 第一步：保存数据，触发 Redis 缓存更新
        Double testValue = 30.5;
        deviceDataService.saveDeviceData(
                TEST_DEVICE_ID, TEST_DATA_TYPE, testValue, "℃", baseTime);

        // 第二步：查询设备实时状态
        Map<String, Object> status = deviceDataService.getDeviceStatus(TEST_DEVICE_ID);

        // 验证：状态信息不为空
        assertFalse(status.isEmpty(), "设备状态不应为空");

        // 验证：状态信息包含设备ID
        assertEquals(TEST_DEVICE_ID, status.get("deviceId"), "状态中的设备ID应匹配");

        // 验证：状态信息包含最新采样值
        // 注意：从 Redis 获取的 value 可能是 Integer 或 Double，需转换成 Double 比较
        Number statusValue = (Number) status.get("value");
        assertNotNull(statusValue, "状态中的值不应为空");
        assertEquals(testValue, statusValue.doubleValue(), "状态中的值应匹配");
    }

    /**
     * 测试 5：批量保存设备数据
     *
     * 验证批量保存功能的正确性
     */
    @Test
    @DisplayName("批量保存设备数据 - 验证批量写入")
    void testBatchSaveDeviceData() {

        // 准备批量数据：10条记录
        int batchSize = 10;
        java.util.List<DeviceDataEntity> dataList = new java.util.ArrayList<>();

        for (int i = 0; i < batchSize; i++) {
            // 使用 Builder 模式构建实体
            DeviceDataEntity entity = DeviceDataEntity.builder()
                    .deviceId(TEST_DEVICE_ID)           // 同一个设备
                    .dataType(TEST_DATA_TYPE)            // 相同数据类型
                    .value(20.0 + i)                     // 数值递增
                    .unit("℃")                           // 单位
                    .ts(baseTime.plusMinutes(i))         // 时间递增
                    .build();
            dataList.add(entity);
        }

        // 执行批量保存
        deviceDataService.batchSaveDeviceData(dataList);

        // 验证：数据库中的数据条数等于批量条数
        List<DeviceDataEntity> allData = deviceDataRepository
                .findByDeviceIdAndTsBetweenOrderByTsAsc(
                        TEST_DEVICE_ID,
                        baseTime,
                        baseTime.plusMinutes(batchSize));
        assertEquals(batchSize, allData.size(), "批量保存的数据条数应匹配");
    }

    /**
     * 测试 6：Redis 缓存未命中时降级到数据库查询
     *
     * 验证 Cache-Aside 模式中的"缓存未命中"路径
     */
    @Test
    @DisplayName("缓存未命中时降级到数据库查询")
    void testCacheMissFallbackToDatabase() {

        // 第一步：保存数据到数据库（但不主动更新 Redis）
        Double testValue = 28.0;
        DeviceDataEntity savedEntity = deviceDataService.saveDeviceData(
                TEST_DEVICE_ID, TEST_DATA_TYPE, testValue, "℃", baseTime);

        // 第二步：手动删除 Redis 缓存，模拟缓存过期
        String redisStatusKey = "device:status:" + TEST_DEVICE_ID;
        stringRedisTemplate.delete(redisStatusKey);

        // 验证：Redis 缓存确实被删除了
        String cachedStatus = stringRedisTemplate.opsForValue().get(redisStatusKey);
        assertNull(cachedStatus, "Redis 缓存应已被删除");

        // 第三步：查询最新数据，应触发"缓存未命中 -> 查数据库 -> 回填缓存"流程
        Optional<DeviceDataEntity> latestData = deviceDataService.getLatestData(TEST_DEVICE_ID);

        // 验证：依然能查到数据（从数据库获取）
        assertTrue(latestData.isPresent(), "即使缓存未命中，也应从数据库查到数据");
        assertEquals(testValue, latestData.get().getValue(), "从数据库查到的值应匹配");

        // 验证：缓存已被回填
        String reCachedStatus = stringRedisTemplate.opsForValue().get(redisStatusKey);
        assertNotNull(reCachedStatus, "缓存未命中后应自动回填 Redis 缓存");
    }
}
```

---

## 4. 运行验证

### 4.1 启动服务

在项目根目录下执行以下命令启动服务：

```bash
# 如果在本地有 Redis 服务，直接启动即可
# 如果没有 Redis，注释掉 application.yml 中的 spring.redis 配置，服务以降级模式运行
mvn spring-boot:run
```

启动成功后，控制台输出日志类似：

```
2026-08-22 10:00:00.123  INFO 12345 --- [main] c.z.iot.DataStorageApplication           : Started DataStorageApplication in 2.345 seconds
```

### 4.2 插入设备数据

使用 curl 命令模拟设备上报数据：

```bash
# 模拟温度传感器上报数据
curl -X POST http://localhost:8080/api/data \
  -H "Content-Type: application/json" \
  -d '{
    "deviceId": "sensor_temp_001",
    "dataType": "temperature",
    "value": 25.3,
    "unit": "℃",
    "ts": "2026-08-22T10:00:00"
  }'

# 模拟湿度传感器上报数据
curl -X POST http://localhost:8080/api/data \
  -H "Content-Type: application/json" \
  -d '{
    "deviceId": "sensor_humidity_001",
    "dataType": "humidity",
    "value": 65.0,
    "unit": "%",
    "ts": "2026-08-22T10:00:00"
  }'
```

### 4.3 查询最新数据（应命中 Redis 缓存）

```bash
curl http://localhost:8080/api/data/sensor_temp_001/latest
```

预期响应（从 Redis 缓存返回，响应时间应在 1-5ms 内）：

```json
{
  "deviceId": "sensor_temp_001",
  "dataType": "temperature",
  "value": 25.3,
  "unit": "℃",
  "ts": "2026-08-22T10:00:00"
}
```

### 4.4 查询历史数据（应命中 MySQL）

```bash
curl "http://localhost:8080/api/data/sensor_temp_001/history?start=2026-08-22+00:00:00&end=2026-08-22+23:59:59"
```

### 4.5 查询设备实时状态

```bash
curl http://localhost:8080/api/data/sensor_temp_001/status
```

### 4.6 运行测试

```bash
# 运行集成测试，验证所有数据流
mvn test
```

测试通过时，所有 6 个测试用例均应绿色通过，控制台输出 "BUILD SUCCESS"。

---

## 5. 项目对照：与 zznursing 真实架构对比

上述演示项目展现了 IoT 数据存储的核心模式，但真实生产环境中的 zznursing 平台有更多进阶设计：

### 5.1 MySQL 表分区

zznursing 生产环境中的 `device_data` 表使用 MySQL 8.0 的 **RANGE 分区** 功能，按月份分区：

```sql
CREATE TABLE device_data (
    id BIGINT AUTO_INCREMENT,
    device_id VARCHAR(64) NOT NULL,
    data_type VARCHAR(32) NOT NULL,
    value DOUBLE NOT NULL,
    unit VARCHAR(16),
    ts DATETIME NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id, ts)  -- 主键必须包含分区键
)
PARTITION BY RANGE (TO_DAYS(ts)) (
    PARTITION p202601 VALUES LESS THAN (TO_DAYS('2026-02-01')),
    PARTITION p202602 VALUES LESS THAN (TO_DAYS('2026-03-01')),
    PARTITION p202603 VALUES LESS THAN (TO_DAYS('2026-04-01')),
    PARTITION p_future VALUES LESS THAN MAXVALUE
);
```

分区的好处：查询时只需扫描相关分区，归档时可直接 `DROP PARTITION` 或 `TRUNCATE PARTITION`，比逐条 DELETE 效率高几个数量级。

### 5.2 Redis 持久化

zznursing 的 Redis 7.x 采用 **RDB + AOF 混合持久化**：

- **RDB（快照）**：每 5 分钟生成一次快照，用于快速恢复。
- **AOF（追加文件）**：设置为 `everysec` 模式，每秒同步一次，最多丢失 1 秒数据。
- 混合持久化（Redis 4.0+）：AOF 重写时使用 RDB 格式作为基础，追加增量 AOF 日志，兼顾重启速度和数据安全。

### 5.3 数据清理定时任务

zznursing 使用 **Spring Boot @Scheduled** 注解实现定时任务，每天凌晨 3 点执行数据归档：

```java
@Component
public class DataArchiveJob {

    @Scheduled(cron = "0 0 3 * * ?")  // 每天凌晨3点执行
    @Transactional
    public void archiveOldData() {
        // 1. 将 7 天前的数据从 device_data 迁移到 device_data_archive
        // 2. 使用 INSERT ... SELECT 批量迁移
        // 3. 迁移完成后删除源表数据
        // 4. 归档表按月分区，保留 12 个月
    }
}
```

### 5.4 MySQL 读写分离

zznursing 使用 **MySQL 主从复制 + 读写分离**：

- **主库（Master）**：处理写入请求（INSERT、UPDATE、DELETE），单库扛住写入洪峰。
- **从库（Slave）**：处理查询请求（SELECT），支持水平扩展，可添加多个从库分担读压力。
- 通过 Spring 的 `@Transactional(readOnly = true)` 注解自动路由到从库。

### 5.5 Redis Pipeline 批量写入

当设备批量上报数据时，zznursing 使用 **Redis Pipeline** 减少网络往返：

```java
// 使用 Redis Pipeline 批量写入，性能提升 5-10 倍
List<Object> results = stringRedisTemplate.executePipelined(
    (RedisCallback<Object>) connection -> {
        for (DeviceData data : batch) {
            String key = "device:status:" + data.getDeviceId();
            String value = objectMapper.writeValueAsString(data);
            connection.stringCommands().set(
                key.getBytes(), value.getBytes());
        }
        return null;
    }
);
```

Pipeline 将多条命令打包一次发送，避免了每条命令的网络往返延迟（RTT），在批量场景下性能提升显著。

---

## 6. 面试题3道

### 面试题 1：为什么 IoT 场景下需要同时使用 MySQL 和 Redis，单一数据库不行吗？

**参考答案：**

单一数据库在 IoT 场景下存在明显的局限性：

- **只用 MySQL**：高频写入是主要瓶颈。IoT 设备可能每秒产生数千条数据，MySQL 单表 INSERT 吞吐量有限（通常每秒几千到一万条），且 InnoDB 的 B+ 树索引维护成本高，写入过多会导致索引频繁分裂，性能进一步下降。此外，MySQL 的查询响应时间在毫秒级，对于需要微秒级响应的实时监控面板来说不够快。

- **只用 Redis**：内存成本是主要限制。Redis 将所有数据存储在内存中，而 IoT 数据量巨大（一个中型项目每天可能产生数 GB 数据），全部放在内存中成本极高。另外 Redis 的持久化机制（RDB/AOF）在极端情况下可能丢失数据，不适合作为唯一的数据存储引擎。

两者结合使用，Redis 负责扛住写入洪峰并提供毫秒级实时查询，MySQL 负责可靠持久化存储和复杂分析查询，实现了性能、成本和可靠性的最佳平衡。

### 面试题 2：Cache-Aside 模式中，为什么是"先写 MySQL，再更新 Redis"，而不是反过来？

**参考答案：**

Cache-Aside 模式的写入顺序是"先更新数据库，再更新缓存"，这主要是为了**保证数据一致性**：

- 如果先更新 Redis 再写 MySQL，在 Redis 更新成功后、MySQL 写入前发生宕机，会导致 Redis 中的缓存数据与数据库不一致（缓存有数据，数据库没有），下次读取时缓存命中返回了错误数据。

- 如果先写 MySQL 再更新 Redis，即使 Redis 更新失败，数据库中的数据仍然是正确的。下次读取时缓存未命中，会从数据库读取最新数据并回填到缓存，数据最终一致。

- 更推荐的做法是"先写 MySQL，再删除缓存"而不是"更新缓存"。因为删除缓存比更新缓存更简单、更安全。如果并发读写同时发生，更新缓存可能导致"旧数据覆盖新数据"的问题，而删除缓存让下次读取时自然从数据库加载最新数据。

### 面试题 3：设计一个 IoT 数据归档方案，如何将 7 天前的数据从 MySQL 主表迁移到归档表，同时保证不影响在线业务？

**参考答案：**

推荐使用 **分批迁移 + 低峰期执行** 的方案：

1. **时间窗口选择**：在业务低峰期执行（如凌晨 3 点），使用 Spring @Scheduled 定时任务触发。

2. **分批处理**：避免一次性迁移大量数据导致锁表或长事务，使用 LIMIT 分批迁移，每批 1000-5000 条。每次迁移后休眠短暂时间，让数据库有喘息机会。

3. **迁移流程**：
   - 第一步：SELECT 出需要归档的数据（按时间排序，LIMIT 批次大小）
   - 第二步：INSERT INTO 归档表 SELECT ...（在同一个事务中）
   - 第三步：DELETE FROM 主表 WHERE id IN (...)
   - 第四步：记录迁移日志，包括迁移条数、耗时等

4. **表分区优化**：如果主表使用了 MySQL 分区，归档操作可以简化为 TRUNCATE 或 DROP 整个分区，而不是逐条 DELETE，效率提升巨大。

5. **监控与告警**：记录每次迁移的耗时和影响行数，如果迁移时间超过阈值，触发告警，防止影响第二天的在线业务。

---

## 总结

本文从 zznursing 智慧养老院平台的 IoT 数据存储需求出发，介绍了 MySQL + Redis 冷热分层存储架构的设计思路和核心概念，并通过一个完整的 Spring Boot 3.2.x 项目演示了从零搭建 IoT 数据存储服务的过程。项目代码包含了 JPA 实体设计、Repository 数据访问、Service 层业务逻辑、REST 控制器和集成测试，覆盖了数据写入、缓存查询、历史查询和状态监控等核心场景。

通过本文的学习，读者应该能够：

1. 理解 IoT 场景下数据存储的核心挑战和应对策略
2. 掌握 MySQL 时序数据表设计的基本原则
3. 熟悉 Redis 在 IoT 场景中的常用数据结构
4. 理解 Cache-Aside 缓存模式的实现原理
5. 能够独立搭建一个基础的 IoT 数据存储服务

在实际生产环境中，zznursing 在本文演示的基础上还增加了表分区、读写分离、Redis Pipeline 批量写入、定时任务归档等进阶优化，这些内容将在后续文章中深入探讨。