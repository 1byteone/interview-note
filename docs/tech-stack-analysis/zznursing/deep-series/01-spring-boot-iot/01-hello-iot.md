# Spring Boot IoT入门：从零搭建设备数据接收服务

> **系列**: zznursing 深度系列 | Level 1 入门篇  
> **作者**: 资深 Java 后端工程师  
> **技术栈**: Java 21 + Spring Boot 3.3.5 + Maven + H2 + JPA  
> **适用读者**: Java 后端初学者，对 IoT 数据接收感兴趣的开发者

---

## 一、项目背景

### 1.1 智慧养老院的 IoT 场景

想象一下，在一家现代化的智慧养老院里，数百位老人佩戴着各种各样的智能设备，这些设备每时每刻都在产生数据：

| 设备类型 | 采集数据 | 上报频率 | 用途 |
|---------|---------|---------|------|
| **智能手环** | 心率、血氧、步数、睡眠 | 每 5 秒 | 健康监测、跌倒检测 |
| **床垫传感器** | 体动、心率、呼吸频率 | 每 10 秒 | 睡眠质量分析、离床报警 |
| **紧急呼叫按钮** | 按钮状态（按下/松开） | 事件触发 | 一键呼救 |
| **定位胸卡** | 经纬度、楼栋楼层 | 每 30 秒 | 人员定位、电子围栏 |

这些设备通过 WiFi、ZigBee 或 LoRa 等无线协议将数据发送到网关，网关再通过 MQTT 或 HTTP 协议将数据上报到后端服务。**后端服务是 IoT 数据链条的第一站**，它负责接收、校验、存储和转发这些海量数据。

### 1.2 zznursing 三层架构

zznursing 智慧养老平台采用经典的三层架构设计：

```
┌─────────────────────────────────────────────────────┐
│           移动互联层 (Mobile)                        │
│  家属 App · 护工 App · 管理后台 · 大屏看板           │
└───────────────────────┬─────────────────────────────┘
                        │ REST API / WebSocket
┌───────────────────────▼─────────────────────────────┐
│            AI 智能层 (AI Intelligence)                │
│  健康预警 · 行为分析 · 跌倒检测 · 睡眠评分             │
│  趋势预测 · 异常识别 · 个性化推荐                     │
└───────────────────────┬─────────────────────────────┘
                        │ 数据流
┌───────────────────────▼─────────────────────────────┐
│          IoT 感知层 (IoT Perception)                  │
│  设备接入 · 数据接收 · 协议转换 · 数据清洗             │
│  设备认证 · 心跳检测 · 命令下发                       │
└─────────────────────────────────────────────────────┘
```

- **IoT 感知层**：负责与物理设备通信，接收原始数据，完成协议转换和数据清洗，是数据进入系统的第一道关口。
- **AI 智能层**：基于感知层提供的数据，运行机器学习模型和规则引擎，进行健康预警、行为分析等智能计算。
- **移动互联层**：面向不同角色（家属、护工、管理者）提供多端应用，将 AI 分析结果以可读的方式呈现给用户。

### 1.3 传统 HTTP 接收 vs MQTT 协议

在 IoT 设备数据上报的场景中，有两种主流协议：

**HTTP 上报**（本文重点）：
- 设备端作为 HTTP 客户端，主动 POST 数据到服务器
- 简单直接，开发门槛低，适合初学者理解
- 适合低频率、非实时性要求不高的场景
- 设备端需要维护 HTTP 连接池

**MQTT 协议**（生产推荐）：
- 基于发布/订阅模式的轻量级消息协议
- 支持 QoS 质量等级，保证消息可达
- 设备端功耗低，适合电池供电设备
- 支持长连接，实时性更好
- 真实 zznursing 项目使用的方案

**学习路径**：本文先从最简单的 HTTP 接收入手，帮助你理解 IoT 数据接收的核心流程。后续文章会深入 MQTT 协议、设备认证、数据清洗等进阶话题。

---

## 二、核心概念

### 2.1 Spring Boot Web 基础

Spring Boot 是一个基于 Spring 框架的快速开发框架，它通过"约定优于配置"的理念，大幅降低了 Spring 应用的门槛。

在 IoT 数据接收场景中，最核心的注解是 `@RestController` 和 `@PostMapping`：

```java
// @RestController 标记这是一个 RESTful 控制器
// 它结合了 @Controller 和 @ResponseBody
// 所有方法的返回值会自动序列化为 JSON
@RestController
@RequestMapping("/api/devices")
public class DeviceDataController {

    // @PostMapping 表示这个方法处理 HTTP POST 请求
    // value 属性指定了请求路径
    // 客户端通过 POST /api/devices/data 来调用
    @PostMapping("/data")
    public ApiResponse<DeviceDataDTO> receiveData(
            @RequestBody @Valid DeviceDataDTO dto) {
        // @RequestBody 将 HTTP 请求体中的 JSON 自动反序列化为 Java 对象
        // @Valid 触发 Bean Validation 校验
        // ...
    }
}
```

**请求流程**：
```
设备 (HTTP POST) ──► Spring Boot 应用
                           │
                    ┌──────▼──────┐
                    │ Dispatcher  │
                    │  Servlet    │
                    └──────┬──────┘
                           │ 路由匹配
                    ┌──────▼──────┐
                    │  Controller │
                    │  @Valid 校验 │
                    └──────┬──────┘
                           │
                    ┌──────▼──────┐
                    │   Service   │
                    └──────┬──────┘
                           │
                    ┌──────▼──────┐
                    │  Repository │
                    │  (JPA 保存)  │
                    └──────┬──────┘
                           │
                    ┌──────▼──────┐
                    │  响应 JSON  │
                    │  返回给设备  │
                    └─────────────┘
```

### 2.2 RESTful API 设计原则

RESTful API 是一套设计 Web 服务的架构风格，针对 IoT 设备数据上报场景，我们遵循以下原则：

| 原则 | 说明 | 示例 |
|------|------|------|
| **资源导向** | 设备数据是资源，用名词命名 | `/api/devices/data` |
| **HTTP 动词** | POST 表示创建/提交 | `POST /api/devices/data` |
| **无状态** | 每次请求独立，不依赖服务端 Session | 每个请求携带完整设备信息 |
| **统一响应格式** | 所有响应结构一致 | `{ "code": 200, "msg": "success", "data": {...} }` |
| **版本管理** | 通过 URL 路径管理 API 版本 | `/api/v1/devices/data` |

### 2.3 设备数据模型

在 IoT 系统中，设备上报的数据通常包含以下核心字段：

```json
{
    "deviceId": "BRACELET-001",
    "timestamp": "2026-08-22T14:30:00",
    "type": "heart_rate",
    "value": 72.5
}
```

| 字段 | 类型 | 说明 | 示例 |
|------|------|------|------|
| `deviceId` | String | 设备唯一标识，由设备类型 + 编号组成 | `BRACELET-001` |
| `timestamp` | LocalDateTime | 数据采集时间，ISO 8601 格式 | `2026-08-22T14:30:00` |
| `type` | String | 数据类型，预定义枚举值 | `heart_rate` |
| `value` | Double | 数据值，根据 type 有不同的含义 | `72.5` |

### 2.4 JSON 序列化

Spring Boot 默认使用 Jackson 库进行 JSON 序列化和反序列化。当设备发送 JSON 请求时，Jackson 自动将 JSON 字段映射到 Java 对象的属性上：

```java
// 设备发送的 JSON:
// { "deviceId": "BRACELET-001", "timestamp": "2026-08-22T14:30:00", ... }

// Jackson 自动映射到 DTO:
public class DeviceDataDTO {
    private String deviceId;    // ← "BRACELET-001"
    private LocalDateTime timestamp;  // ← 2026-08-22T14:30:00
    private String type;        // ← "heart_rate"
    private Double value;       // ← 72.5
}
```

### 2.5 数据校验（@Valid）

设备上报的数据不可信，必须经过校验才能进入系统。Spring Boot 支持 JSR-380 Bean Validation（`@Valid`），配合 Jakarta Validation 注解：

```java
public class DeviceDataDTO {
    @NotBlank(message = "设备ID不能为空")
    private String deviceId;

    @NotNull(message = "采集时间不能为空")
    private LocalDateTime timestamp;

    @NotBlank(message = "数据类型不能为空")
    private String type;

    @NotNull(message = "数据值不能为空")
    private Double value;
}
```

当校验失败时，Spring Boot 会自动返回 400 Bad Request 和错误详情，无需手动编写校验逻辑。

---

## 三、从零搭建代码

### 3.1 项目结构概览

```
iot-device-receiver/
├── pom.xml                           # Maven 构建文件
├── src/
│   ├── main/
│   │   ├── java/com/zznursing/iot/
│   │   │   ├── IotApplication.java            # 启动类
│   │   │   ├── controller/
│   │   │   │   └── DeviceDataController.java  # REST 控制器
│   │   │   ├── service/
│   │   │   │   └── DeviceDataService.java     # 业务逻辑层
│   │   │   ├── entity/
│   │   │   │   └── DeviceDataEntity.java      # JPA 实体
│   │   │   ├── repository/
│   │   │   │   └── DeviceDataRepository.java  # 数据访问层
│   │   │   ├── dto/
│   │   │   │   ├── DeviceDataDTO.java         # 接收 DTO
│   │   │   │   └── ApiResponse.java           # 统一响应
│   │   │   └── exception/
│   │   │       └── GlobalExceptionHandler.java # 全局异常处理
│   │   └── resources/
│   │       └── application.yml                # 应用配置
│   └── test/
│       └── java/com/zznursing/iot/
│           └── IotApplicationTest.java        # 集成测试
```

### 3.2 pom.xml — Maven 构建配置

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <!-- 项目模型版本，固定为 4.0.0 -->
    <modelVersion>4.0.0</modelVersion>

    <!-- 父工程：Spring Boot 3.3.5，继承其默认依赖管理 -->
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.3.5</version>
        <relativePath/>
    </parent>

    <!-- 项目坐标信息 -->
    <groupId>com.zznursing</groupId>
    <artifactId>iot-device-receiver</artifactId>
    <version>1.0.0</version>
    <name>zznursing-iot-device-receiver</name>
    <description>zznursing 智慧养老平台 - IoT 设备数据接收服务</description>

    <!-- Java 版本：使用 Java 21 的最新特性 -->
    <properties>
        <java.version>21</java.version>
    </properties>

    <dependencies>
        <!-- Spring Boot Web 起步依赖 -->
        <!-- 包含 Spring MVC、内嵌 Tomcat、Jackson 序列化等 -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>

        <!-- Spring Data JPA 起步依赖 -->
        <!-- 提供 JPA 规范和 Hibernate 实现，用于数据库操作 -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>

        <!-- Bean Validation 起步依赖 -->
        <!-- 提供 @Valid、@NotBlank、@NotNull 等校验注解 -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>

        <!-- H2 内存数据库 -->
        <!-- 开发测试用，无需安装数据库即可运行 -->
        <dependency>
            <groupId>com.h2database</groupId>
            <artifactId>h2</artifactId>
            <scope>runtime</scope>
        </dependency>

        <!-- Lombok 代码简化工具 -->
        <!-- 自动生成 Getter/Setter/Builder/构造方法等 -->
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>

        <!-- Spring Boot 测试起步依赖 -->
        <!-- 包含 JUnit 5、Mockito、MockMvc 等测试框架 -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <!-- Spring Boot Maven 打包插件 -->
            <!-- 将应用打包为可执行 JAR -->
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <configuration>
                    <!-- 排除 Lombok，减少最终 JAR 体积 -->
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

### 3.3 application.yml — 应用配置

```yaml
# ============================================================
# zznursing IoT 设备数据接收服务 — 应用配置文件
# ============================================================

# 服务器配置
server:
  port: 8080                # 服务端口号，设备通过此端口上报数据

# Spring 框架配置
spring:
  application:
    name: iot-device-receiver  # 应用名称，用于服务注册和日志标识

  # H2 内存数据库配置
  # 数据存储在内存中，应用重启后数据丢失（适合开发测试）
  h2:
    console:
      enabled: true         # 开启 H2 Web Console，可通过浏览器访问
      path: /h2-console     # Console 访问路径：http://localhost:8080/h2-console

  # 数据源配置
  datasource:
    url: jdbc:h2:mem:iotdb  # H2 内存数据库连接 URL，数据库名为 iotdb
    driver-class-name: org.h2.Driver  # H2 数据库驱动类
    username: sa            # H2 默认用户名
    password:               # H2 默认密码为空

  # JPA 配置
  jpa:
    hibernate:
      ddl-auto: update      # 自动建表：应用启动时根据实体类创建/更新表结构
    show-sql: true          # 在控制台打印 SQL 语句，方便调试
    properties:
      hibernate:
        format_sql: true    # 格式化 SQL 输出，提高可读性
    database-platform: org.hibernate.dialect.H2Dialect  # H2 数据库方言
```

### 3.4 DeviceDataEntity.java — JPA 实体

```java
package com.zznursing.iot.entity;

// ============================================================
// 设备数据实体类
// 对应数据库中的 device_data 表，存储设备上报的原始数据
// ============================================================

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 设备数据实体 — 映射到数据库 device_data 表
 * <p>
 * 每条记录代表一次设备上报的数据点。
 * 实体类使用 JPA 注解自动建表，无需手动编写 SQL。
 */
@Entity                                         // 标记为 JPA 实体类
@Table(name = "device_data")                    // 指定数据库表名
@Data                                           // Lombok：自动生成 Getter/Setter/toString/equals/hashCode
@NoArgsConstructor                              // Lombok：生成无参构造方法（JPA 要求必须有无参构造）
@AllArgsConstructor                             // Lombok：生成全参构造方法
@Builder                                        // Lombok：生成建造者模式，方便链式创建对象
public class DeviceDataEntity {

    /**
     * 主键 ID — 自增长
     * 每条设备数据记录的唯一标识
     */
    @Id                                         // 标记为主键字段
    @GeneratedValue(strategy = GenerationType.IDENTITY)  // 主键生成策略：数据库自增长
    private Long id;

    /**
     * 设备唯一标识
     * 例如：BRACELET-001（智能手环001号）
     * 格式：设备类型前缀-设备编号
     */
    @Column(name = "device_id", nullable = false, length = 64)
    private String deviceId;

    /**
     * 数据采集时间戳
     * 设备端实际采集数据的时间（非服务器接收时间）
     * 使用 ISO 8601 格式：2026-08-22T14:30:00
     */
    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;

    /**
     * 数据类型
     * 预定义枚举值，标识本条数据的含义：
     * - heart_rate: 心率（次/分钟）
     * - blood_oxygen: 血氧饱和度（%）
     * - temperature: 体温（℃）
     * - step_count: 步数（步）
     * - location: 位置坐标（经纬度）
     * - fall_alarm: 跌倒报警
     * - emergency: 紧急呼叫
     * - bed_leave: 离床检测
     */
    @Column(name = "type", nullable = false, length = 32)
    private String type;

    /**
     * 数据值
     * 根据 type 字段有不同的含义：
     * - 心率时：72.5 表示 72.5 次/分钟
     * - 体温时：36.5 表示 36.5℃
     * - 步数时：1234.0 表示 1234 步
     * - 报警时：1.0 表示报警，0.0 表示正常
     */
    @Column(name = "value", nullable = false)
    private Double value;

    /**
     * 服务端接收时间
     * 数据到达服务器的时间戳，由系统自动填充
     * 与设备端 timestamp 字段区分，用于监控数据延迟
     */
    @Column(name = "received_at", nullable = false)
    private LocalDateTime receivedAt;
}
```

### 3.5 DeviceDataRepository.java — JPA Repository

```java
package com.zznursing.iot.repository;

// ============================================================
// 设备数据数据访问层
// 继承 JpaRepository 后，自动获得 CRUD 方法
// 无需编写任何实现代码
// ============================================================

import com.zznursing.iot.entity.DeviceDataEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 设备数据 Repository
 * <p>
 * JpaRepository<Entity, ID> 泛型参数：
 * - Entity: 实体类类型
 * - ID: 主键字段类型
 * <p>
 * Spring Data JPA 会根据方法名自动生成查询实现，
 * 无需编写 JPQL 或 SQL。
 */
@Repository     // 标记为 Spring 管理的 Repository 组件
public interface DeviceDataRepository extends JpaRepository<DeviceDataEntity, Long> {

    /**
     * 根据设备ID查询所有数据（按时间降序排列）
     * Spring Data JPA 自动解析方法名生成查询：
     * findByDeviceId → WHERE device_id = ?
     * OrderByTimestampDesc → ORDER BY timestamp DESC
     *
     * @param deviceId 设备唯一标识
     * @return 该设备的所有数据记录列表
     */
    List<DeviceDataEntity> findByDeviceIdOrderByTimestampDesc(String deviceId);

    /**
     * 查询指定设备在时间范围内的数据
     * 方法名解析：Between → BETWEEN ? AND ?
     *
     * @param deviceId 设备唯一标识
     * @param start    开始时间（包含）
     * @param end      结束时间（包含）
     * @return 时间范围内的数据记录列表
     */
    List<DeviceDataEntity> findByDeviceIdAndTimestampBetweenOrderByTimestampAsc(
            String deviceId, LocalDateTime start, LocalDateTime end);

    /**
     * 查询指定时间范围内的报警数据
     * 方法名解析：And → 多个条件 AND 连接
     *
     * @param type  数据类型（如 "fall_alarm" 跌倒报警）
     * @param start 开始时间
     * @param end   结束时间
     * @return 报警数据列表
     */
    List<DeviceDataEntity> findByTypeAndTimestampBetweenOrderByTimestampDesc(
            String type, LocalDateTime start, LocalDateTime end);

    /**
     * 统计指定设备的数据条数
     * 方法名解析：countBy → SELECT COUNT(*)
     *
     * @param deviceId 设备唯一标识
     * @return 数据条数
     */
    long countByDeviceId(String deviceId);

    /**
     * 删除早于指定时间的数据（用于数据清理）
     * 方法名解析：deleteBy → DELETE FROM
     * 注意：删除操作需要加 @Transactional 和 @Modifying
     *
     * @param beforeTime 阈值时间，早于该时间的数据将被删除
     */
    void deleteByReceivedAtBefore(LocalDateTime beforeTime);
}
```

### 3.6 DeviceDataDTO.java — 接收数据 DTO

```java
package com.zznursing.iot.dto;

// ============================================================
// 设备数据接收 DTO（Data Transfer Object）
// 用于接收设备上报的 HTTP 请求体
// 包含 Bean Validation 校验注解，确保数据合法性
// ============================================================

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 设备数据 DTO — 前端/设备端请求体的映射对象
 * <p>
 * DTO 与 Entity 分离的原因：
 * 1. DTO 只包含需要接收的字段，Entity 包含数据库所有字段
 * 2. DTO 可以添加校验注解，Entity 专注于持久化
 * 3. 避免客户端直接操作实体结构
 */
@Data                                           // Lombok：自动生成 Getter/Setter/toString/equals/hashCode
@NoArgsConstructor                              // Lombok：生成无参构造
@AllArgsConstructor                             // Lombok：生成全参构造
@Builder                                        // Lombok：生成建造者模式
public class DeviceDataDTO {

    /**
     * 设备唯一标识
     * 必填字段，不能为空
     * 格式：设备类型前缀-设备编号，如 BRACELET-001
     */
    @NotBlank(message = "设备ID不能为空")
    @Pattern(regexp = "^[A-Z]+-[A-Z0-9]+$",
             message = "设备ID格式不正确，示例：BRACELET-001")
    private String deviceId;

    /**
     * 数据采集时间戳
     * 必填字段，不能为空
     * 格式：ISO 8601，如 2026-08-22T14:30:00
     * Jackson 自动将字符串反序列化为 LocalDateTime
     */
    @NotNull(message = "采集时间不能为空")
    private LocalDateTime timestamp;

    /**
     * 数据类型
     * 必填字段，不能为空
     * 预定义值：heart_rate, blood_oxygen, temperature 等
     */
    @NotBlank(message = "数据类型不能为空")
    @Pattern(regexp = "^(heart_rate|blood_oxygen|temperature|step_count|" +
                      "location|fall_alarm|emergency|bed_leave)$",
             message = "不支持的数据类型")
    private String type;

    /**
     * 数据值
     * 必填字段，不能为空
     * 根据 type 字段有不同的物理含义
     */
    @NotNull(message = "数据值不能为空")
    private Double value;
}
```

### 3.7 ApiResponse.java — 统一响应封装

```java
package com.zznursing.iot.dto;

// ============================================================
// 统一 API 响应封装
// 确保所有接口返回一致的数据格式
// 前端/设备端只需解析一种结构
// ============================================================

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统一 API 响应类
 * <p>
 * 所有 REST 接口统一使用该类包装响应，
 * 格式：{ "code": 200, "msg": "success", "data": {...} }
 * <p>
 * 泛型 T 表示 data 字段的实际数据类型
 *
 * @param <T> data 字段的类型
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
// 当字段值为 null 时，不包含在 JSON 中（减少传输体积）
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    /**
     * 状态码
     * 200: 成功
     * 400: 请求参数错误
     * 500: 服务器内部错误
     */
    private int code;

    /**
     * 提示信息
     * 成功时："success"
     * 失败时：具体的错误描述
     */
    private String msg;

    /**
     * 响应数据
     * 成功时：包含实际业务数据
     * 失败时：通常为 null
     */
    private T data;

    /**
     * 创建成功响应（无数据返回）
     *
     * @param <T> 数据类型
     * @return 成功响应对象
     */
    public static <T> ApiResponse<T> success() {
        return ApiResponse.<T>builder()
                .code(200)
                .msg("success")
                .build();
    }

    /**
     * 创建成功响应（带数据返回）
     *
     * @param data 业务数据
     * @param <T>  数据类型
     * @return 成功响应对象
     */
    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
                .code(200)
                .msg("success")
                .data(data)
                .build();
    }

    /**
     * 创建失败响应
     *
     * @param code    错误状态码
     * @param message 错误描述
     * @param <T>     数据类型
     * @return 失败响应对象
     */
    public static <T> ApiResponse<T> error(int code, String message) {
        return ApiResponse.<T>builder()
                .code(code)
                .msg(message)
                .build();
    }
}
```

### 3.8 GlobalExceptionHandler.java — 全局异常处理

```java
package com.zznursing.iot.exception;

// ============================================================
// 全局异常处理器
// 统一处理控制器层抛出的异常
// 确保异常也以统一格式 { "code": xxx, "msg": "xxx" } 返回
// ============================================================

import com.zznursing.iot.dto.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * 全局异常处理器 — 使用 @RestControllerAdvice 统一拦截异常
 * <p>
 * @RestControllerAdvice 是 @ControllerAdvice + @ResponseBody 的组合注解
 * 所有控制器抛出异常时，由本类中的方法统一处理
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理参数校验失败异常
     * <p>
     * 当 @Valid 校验失败时，Spring 抛出 MethodArgumentNotValidException
     * 本方法将校验错误信息拼接后返回给客户端
     *
     * @param ex 参数校验异常
     * @return 包含错误详情的响应
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(
            MethodArgumentNotValidException ex) {

        // 提取所有字段校验错误信息，用 "; " 拼接成一条消息
        String errorMessage = ex.getBindingResult()
                .getFieldErrors()                       // 获取所有字段错误
                .stream()                               // 转为流
                .map(FieldError::getDefaultMessage)     // 提取每个字段的错误消息
                .collect(Collectors.joining("; "));     // 用分号拼接

        // 返回 400 Bad Request，消息体为统一格式的错误响应
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)         // HTTP 状态码 400
                .body(ApiResponse.error(400, errorMessage));
    }

    /**
     * 处理通用异常（兜底处理器）
     * <p>
     * 所有未被其他异常处理方法捕获的异常，都由本方法处理
     * 返回 500 Internal Server Error
     *
     * @param ex 通用异常
     * @return 服务端错误响应
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneralException(Exception ex) {
        // 记录异常日志（生产环境应使用 Logger）
        System.err.println("服务器内部错误: " + ex.getMessage());
        ex.printStackTrace();

        // 返回 500 Internal Server Error
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(500, "服务器内部错误，请稍后重试"));
    }
}
```

### 3.9 DeviceDataService.java — 业务逻辑层

```java
package com.zznursing.iot.service;

// ============================================================
// 设备数据业务逻辑层
// 负责：数据校验、DTO 转 Entity、调用 Repository 持久化
// 分层设计：Controller → Service → Repository
// ============================================================

import com.zznursing.iot.dto.DeviceDataDTO;
import com.zznursing.iot.entity.DeviceDataEntity;
import com.zznursing.iot.repository.DeviceDataRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 设备数据业务服务
 * <p>
 * @Service 标记为 Spring 业务层组件
 * 事务默认回滚策略：运行时异常（RuntimeException）自动回滚
 */
@Service
public class DeviceDataService {

    // 注入 Repository 数据访问层
    private final DeviceDataRepository repository;

    /**
     * 构造方法注入（推荐方式）
     * Spring 自动将 DeviceDataRepository 注入到此参数
     * 使用 final 确保不可变性，防止被篡改
     *
     * @param repository 设备数据 Repository
     */
    public DeviceDataService(DeviceDataRepository repository) {
        this.repository = repository;
    }

    /**
     * 保存设备上报的数据
     * <p>
     * 处理流程：
     * 1. 记录服务端接收时间
     * 2. DTO 转换为 Entity
     * 3. 调用 Repository 保存到数据库
     * 4. 返回保存后的完整实体（包含自增 ID）
     * <p>
     *
     * @param dto 设备上报的原始数据（已通过 @Valid 校验）
     * @return 保存后的实体（包含数据库生成的 ID 字段）
     */
    @Transactional                              // 开启事务，异常时自动回滚
    public DeviceDataEntity saveDeviceData(DeviceDataDTO dto) {
        // Step 1: 获取当前时间作为服务端接收时间
        LocalDateTime now = LocalDateTime.now();

        // Step 2: 使用建造者模式将 DTO 转换为 Entity
        // 建造者模式（Builder Pattern）让对象创建更清晰
        DeviceDataEntity entity = DeviceDataEntity.builder()
                .deviceId(dto.getDeviceId())        // 设备ID：从 DTO 复制
                .timestamp(dto.getTimestamp())       // 设备采集时间：从 DTO 复制
                .type(dto.getType())                 // 数据类型：从 DTO 复制
                .value(dto.getValue())               // 数据值：从 DTO 复制
                .receivedAt(now)                     // 服务端接收时间：系统自动填充
                .build();

        // Step 3: 保存到数据库，返回包含 ID 的完整实体
        return repository.save(entity);
    }

    /**
     * 查询指定设备的所有数据
     *
     * @param deviceId 设备唯一标识
     * @return 该设备的数据列表（按时间降序）
     */
    @Transactional(readOnly = true)              // 只读事务，提高查询性能
    public List<DeviceDataEntity> getDeviceData(String deviceId) {
        // 调用 Repository 的方法名派生查询
        return repository.findByDeviceIdOrderByTimestampDesc(deviceId);
    }

    /**
     * 查询指定设备在时间范围内的数据
     *
     * @param deviceId 设备唯一标识
     * @param start    开始时间
     * @param end      结束时间
     * @return 时间范围内的数据列表（按时间升序）
     */
    @Transactional(readOnly = true)
    public List<DeviceDataEntity> getDeviceDataByTimeRange(
            String deviceId, LocalDateTime start, LocalDateTime end) {
        return repository.findByDeviceIdAndTimestampBetweenOrderByTimestampAsc(
                deviceId, start, end);
    }

    /**
     * 统计指定设备的数据总数
     *
     * @param deviceId 设备唯一标识
     * @return 数据条数
     */
    @Transactional(readOnly = true)
    public long countDeviceData(String deviceId) {
        return repository.countByDeviceId(deviceId);
    }
}
```

### 3.10 DeviceDataController.java — REST 控制器

```java
package com.zznursing.iot.controller;

// ============================================================
// 设备数据 REST 控制器
// 对外暴露 HTTP 接口，接收设备上报的数据
// 所有请求和响应都使用 JSON 格式
// ============================================================

import com.zznursing.iot.dto.ApiResponse;
import com.zznursing.iot.dto.DeviceDataDTO;
import com.zznursing.iot.entity.DeviceDataEntity;
import com.zznursing.iot.service.DeviceDataService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 设备数据控制器 — IoT 设备数据接入入口
 * <p>
 * 所有设备上报的数据首先到达此控制器。
 * 请求路径前缀：/api/devices
 */
@RestController                                         // 标记为 REST 控制器
@RequestMapping("/api/devices")                         // 请求路径前缀
public class DeviceDataController {

    // 注入业务层
    private final DeviceDataService deviceDataService;

    /**
     * 构造方法注入业务层
     *
     * @param deviceDataService 设备数据业务服务
     */
    public DeviceDataController(DeviceDataService deviceDataService) {
        this.deviceDataService = deviceDataService;
    }

    /**
     * 接收设备上报的数据 — 核心接口
     * <p>
     * 请求方式：POST
     * 请求路径：/api/devices/data
     * 请求体：JSON 格式的设备数据（DeviceDataDTO）
     * <p>
     * 设备调用示例：
     * POST http://localhost:8080/api/devices/data
     * Content-Type: application/json
     * {
     *     "deviceId": "BRACELET-001",
     *     "timestamp": "2026-08-22T14:30:00",
     *     "type": "heart_rate",
     *     "value": 72.5
     * }
     *
     * @param dto 设备上报的 JSON 数据，自动反序列化为 DTO 对象
     * @return 统一响应，包含保存后的完整数据
     */
    @PostMapping("/data")                               // 处理 POST /api/devices/data 请求
    public ResponseEntity<ApiResponse<DeviceDataEntity>> receiveData(
            @RequestBody @Valid DeviceDataDTO dto) {    // @RequestBody: 从 HTTP 请求体解析 JSON
                                                        // @Valid: 触发 Bean Validation 校验

        // 调用业务层保存数据
        DeviceDataEntity savedEntity = deviceDataService.saveDeviceData(dto);

        // 返回 201 Created 状态码，表示资源创建成功
        // 返回统一格式的响应，包含保存后的实体数据
        return ResponseEntity
                .status(HttpStatus.CREATED)              // HTTP 201 Created
                .body(ApiResponse.success(savedEntity));
    }

    /**
     * 查询指定设备的所有数据
     * <p>
     * 请求方式：GET
     * 请求路径：/api/devices/{deviceId}/data
     * 示例：GET http://localhost:8080/api/devices/BRACELET-001/data
     *
     * @param deviceId 设备唯一标识，从 URL 路径中获取
     * @return 该设备的所有数据列表
     */
    @GetMapping("/{deviceId}/data")                     // 处理 GET 请求，{deviceId} 是路径变量
    public ResponseEntity<ApiResponse<List<DeviceDataEntity>>> getDeviceData(
            @PathVariable String deviceId) {            // @PathVariable 从 URL 路径中提取变量

        List<DeviceDataEntity> dataList = deviceDataService.getDeviceData(deviceId);

        return ResponseEntity.ok(ApiResponse.success(dataList));
    }

    /**
     * 查询指定设备在时间范围内的数据
     * <p>
     * 请求方式：GET
     * 请求路径：/api/devices/{deviceId}/data/range
     * 查询参数：start（开始时间）、end（结束时间）
     * <p>
     * 示例：
     * GET /api/devices/BRACELET-001/data/range?start=2026-08-22T00:00:00&end=2026-08-22T23:59:59
     *
     * @param deviceId 设备唯一标识
     * @param start    开始时间（ISO 8601 格式）
     * @param end      结束时间（ISO 8601 格式）
     * @return 时间范围内的数据列表
     */
    @GetMapping("/{deviceId}/data/range")
    public ResponseEntity<ApiResponse<List<DeviceDataEntity>>> getDeviceDataByTimeRange(
            @PathVariable String deviceId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {

        List<DeviceDataEntity> dataList = deviceDataService.getDeviceDataByTimeRange(
                deviceId, start, end);

        return ResponseEntity.ok(ApiResponse.success(dataList));
    }

    /**
     * 统计指定设备的数据总数
     * <p>
     * 请求方式：GET
     * 请求路径：/api/devices/{deviceId}/data/count
     * 示例：GET http://localhost:8080/api/devices/BRACELET-001/data/count
     *
     * @param deviceId 设备唯一标识
     * @return 该设备的数据总条数
     */
    @GetMapping("/{deviceId}/data/count")
    public ResponseEntity<ApiResponse<Long>> countDeviceData(
            @PathVariable String deviceId) {

        long count = deviceDataService.countDeviceData(deviceId);

        return ResponseEntity.ok(ApiResponse.success(count));
    }
}
```

### 3.11 IotApplication.java — 启动类

```java
package com.zznursing.iot;

// ============================================================
// zznursing IoT 设备数据接收服务 — 启动类
// 这是整个应用的入口，main 方法启动 Spring Boot 应用
// ============================================================

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 应用启动类
 * <p>
 * @SpringBootApplication 是一个组合注解，包含：
 * 1. @Configuration — 标记为配置类
 * 2. @EnableAutoConfiguration — 启用 Spring Boot 自动配置
 * 3. @ComponentScan — 自动扫描当前包及其子包的组件
 * <p>
 * 启动后，Spring Boot 会自动：
 * 1. 启动内嵌 Tomcat 服务器
 * 2. 配置数据源和 JPA
 * 3. 扫描并注册所有 Controller/Service/Repository
 * 4. 开启 H2 Web Console
 */
@SpringBootApplication
public class IotApplication {

    /**
     * 应用入口方法
     * SpringApplication.run() 启动整个 Spring 容器
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(IotApplication.class, args);
    }
}
```

### 3.12 IotApplicationTest.java — 集成测试

```java
package com.zznursing.iot;

// ============================================================
// 设备数据接收服务 — 集成测试
// 使用 MockMvc 模拟 HTTP 请求，测试完整的请求-响应流程
// 测试数据自动回滚，不影响后续测试
// ============================================================

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zznursing.iot.dto.DeviceDataDTO;
import com.zznursing.iot.entity.DeviceDataEntity;
import com.zznursing.iot.repository.DeviceDataRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

/**
 * 集成测试类 — 测试完整的 HTTP 请求-数据库-响应链路
 * <p>
 * @SpringBootTest 启动完整的 Spring 应用上下文
 * @AutoConfigureMockMvc 自动配置 MockMvc（无需启动真实服务器）
 * @Transactional 测试方法执行后自动回滚数据库，保持测试环境干净
 */
@SpringBootTest                                    // 启动完整的 Spring Boot 应用上下文
@AutoConfigureMockMvc                              // 自动配置 MockMvc 测试工具
@Transactional                                     // 测试结束后自动回滚事务，不影响数据库
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)  // 按 @Order 注解顺序执行测试
class IotApplicationTest {

    // MockMvc — 模拟 HTTP 请求和响应的测试工具
    @Autowired
    private MockMvc mockMvc;

    // ObjectMapper — Jackson 的 JSON 序列化/反序列化工具
    @Autowired
    private ObjectMapper objectMapper;

    // 注入 Repository，用于测试前准备数据
    @Autowired
    private DeviceDataRepository repository;

    /**
     * 测试 1: 成功接收设备数据
     * <p>
     * 测试场景：设备发送合法的心率数据
     * 预期结果：HTTP 201 Created，返回数据包含所有字段
     */
    @Test
    @Order(1)
    @DisplayName("测试1: 成功接收设备数据 — 返回201 Created")
    void testReceiveDeviceData_Success() throws Exception {
        // 准备测试数据：模拟智能手环上报心率数据
        DeviceDataDTO dto = DeviceDataDTO.builder()
                .deviceId("BRACELET-001")                    // 设备ID：智能手环001号
                .timestamp(LocalDateTime.of(2026, 8, 22, 14, 30, 0))  // 采集时间：2026-08-22 14:30:00
                .type("heart_rate")                          // 数据类型：心率
                .value(72.5)                                 // 心率值：72.5 次/分钟
                .build();

        // 执行 POST 请求并验证响应
        mockMvc.perform(post("/api/devices/data")            // 发送 POST 请求
                .contentType(MediaType.APPLICATION_JSON)     // 请求体格式：JSON
                .content(objectMapper.writeValueAsString(dto)))  // 将 DTO 转为 JSON 字符串
                .andExpect(status().isCreated())             // 断言：HTTP 状态码 201 Created
                .andExpect(jsonPath("$.code").value(200))    // 断言：响应 code 为 200
                .andExpect(jsonPath("$.msg").value("success"))  // 断言：响应 msg 为 "success"
                .andExpect(jsonPath("$.data.deviceId").value("BRACELET-001"))  // 断言：设备ID正确
                .andExpect(jsonPath("$.data.type").value("heart_rate"))        // 断言：类型正确
                .andExpect(jsonPath("$.data.value").value(72.5))               // 断言：数值正确
                .andExpect(jsonPath("$.data.id").isNumber())                   // 断言：ID 自动生成
                .andExpect(jsonPath("$.data.receivedAt").isNotEmpty());        // 断言：接收时间自动填充
    }

    /**
     * 测试 2: 设备ID为空时返回400错误
     * <p>
     * 测试场景：设备发送的数据缺少 deviceId 字段
     * 预期结果：HTTP 400 Bad Request，校验失败信息
     */
    @Test
    @Order(2)
    @DisplayName("测试2: 设备ID为空 — 返回400 Bad Request")
    void testReceiveData_DeviceIdBlank() throws Exception {
        // 准备测试数据：deviceId 为空字符串
        DeviceDataDTO dto = DeviceDataDTO.builder()
                .deviceId("")                                // 设备ID为空（违反 @NotBlank 校验）
                .timestamp(LocalDateTime.now())              // 采集时间：当前时间
                .type("heart_rate")                          // 数据类型：心率
                .value(72.5)                                 // 心率值
                .build();

        // 执行 POST 请求并验证错误响应
        mockMvc.perform(post("/api/devices/data")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())          // 断言：HTTP 状态码 400
                .andExpect(jsonPath("$.code").value(400))    // 断言：业务码 400
                .andExpect(jsonPath("$.msg").value(containsString("设备ID不能为空")));  // 断言：错误消息包含校验提示
    }

    /**
     * 测试 3: 数据类型不合法时返回400错误
     * <p>
     * 测试场景：设备发送了不支持的数据类型
     * 预期结果：HTTP 400 Bad Request，提示数据类型不支持
     */
    @Test
    @Order(3)
    @DisplayName("测试3: 不支持的数据类型 — 返回400 Bad Request")
    void testReceiveData_InvalidType() throws Exception {
        // 准备测试数据：type 为不支持的值 "invalid_type"
        DeviceDataDTO dto = DeviceDataDTO.builder()
                .deviceId("BRACELET-001")                    // 设备ID：合法
                .timestamp(LocalDateTime.now())              // 采集时间：合法
                .type("invalid_type")                        // 数据类型：不合法（不在预定义枚举中）
                .value(72.5)                                 // 数据值：合法
                .build();

        // 执行 POST 请求并验证错误响应
        mockMvc.perform(post("/api/devices/data")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())          // 断言：HTTP 状态码 400
                .andExpect(jsonPath("$.code").value(400))    // 断言：业务码 400
                .andExpect(jsonPath("$.msg").value(containsString("不支持的数据类型")));  // 断言：错误消息
    }

    /**
     * 测试 4: 查询设备数据接口
     * <p>
     * 测试场景：先插入数据，再查询该设备的数据
     * 预期结果：查询接口返回已插入的数据列表
     */
    @Test
    @Order(4)
    @DisplayName("测试4: 查询设备数据 — 返回数据列表")
    void testGetDeviceData() throws Exception {
        // 先在数据库中插入一条测试数据（直接调用 Service 层逻辑）
        // 这里直接使用 Repository 插入，模拟已有数据
        DeviceDataEntity entity = DeviceDataEntity.builder()
                .deviceId("BRACELET-001")
                .timestamp(LocalDateTime.of(2026, 8, 22, 10, 0, 0))
                .type("heart_rate")
                .value(75.0)
                .receivedAt(LocalDateTime.now())
                .build();
        repository.save(entity);

        // 执行 GET 请求查询 BRACELET-001 的数据
        mockMvc.perform(get("/api/devices/BRACELET-001/data"))
                .andExpect(status().isOk())                  // 断言：HTTP 状态码 200
                .andExpect(jsonPath("$.code").value(200))    // 断言：业务码 200
                .andExpect(jsonPath("$.data").isArray())     // 断言：data 是数组
                .andExpect(jsonPath("$.data.length()").value(greaterThanOrEqualTo(1)))  // 断言：至少一条数据
                .andExpect(jsonPath("$.data[0].deviceId").value("BRACELET-001"));  // 断言：设备ID匹配
    }

    /**
     * 测试 5: 查询设备数据统计接口
     * <p>
     * 测试场景：先插入多条数据，再统计数量
     * 预期结果：统计接口返回正确的数据条数
     */
    @Test
    @Order(5)
    @DisplayName("测试5: 统计设备数据数量 — 返回正确计数")
    void testCountDeviceData() throws Exception {
        // 插入两条数据，模拟同一设备上报了多次
        DeviceDataEntity entity1 = DeviceDataEntity.builder()
                .deviceId("BRACELET-001")
                .timestamp(LocalDateTime.of(2026, 8, 22, 10, 0, 0))
                .type("heart_rate")
                .value(75.0)
                .receivedAt(LocalDateTime.now())
                .build();

        DeviceDataEntity entity2 = DeviceDataEntity.builder()
                .deviceId("BRACELET-001")
                .timestamp(LocalDateTime.of(2026, 8, 22, 10, 5, 0))
                .type("heart_rate")
                .value(78.0)
                .receivedAt(LocalDateTime.now())
                .build();

        repository.save(entity1);   // 保存第一条数据
        repository.save(entity2);   // 保存第二条数据

        // 执行 GET 请求统计 BRACELET-001 的数据数
        mockMvc.perform(get("/api/devices/BRACELET-001/data/count"))
                .andExpect(status().isOk())                  // 断言：HTTP 200
                .andExpect(jsonPath("$.code").value(200))    // 断言：业务码 200
                .andExpect(jsonPath("$.data").value(2));     // 断言：统计结果为 2 条
    }

    /**
     * 测试 6: 查询时间范围数据接口
     * <p>
     * 测试场景：插入不同时间的数据，按时间范围查询
     * 预期结果：只返回时间范围内的数据
     */
    @Test
    @Order(6)
    @DisplayName("测试6: 时间范围查询 — 返回时间范围内的数据")
    void testGetDeviceDataByTimeRange() throws Exception {
        // 插入三条不同时间的数据
        repository.save(DeviceDataEntity.builder()
                .deviceId("BRACELET-001")
                .timestamp(LocalDateTime.of(2026, 8, 22, 8, 0, 0))   // 08:00
                .type("heart_rate")
                .value(70.0)
                .receivedAt(LocalDateTime.now())
                .build());

        repository.save(DeviceDataEntity.builder()
                .deviceId("BRACELET-001")
                .timestamp(LocalDateTime.of(2026, 8, 22, 12, 0, 0))  // 12:00
                .type("heart_rate")
                .value(72.0)
                .receivedAt(LocalDateTime.now())
                .build());

        repository.save(DeviceDataEntity.builder()
                .deviceId("BRACELET-001")
                .timestamp(LocalDateTime.of(2026, 8, 22, 18, 0, 0))  // 18:00
                .type("heart_rate")
                .value(68.0)
                .receivedAt(LocalDateTime.now())
                .build());

        // 查询 10:00 ~ 15:00 范围内的数据，应该只返回 12:00 的那条
        mockMvc.perform(get("/api/devices/BRACELET-001/data/range")
                .param("start", "2026-08-22T10:00:00")     // 开始时间
                .param("end", "2026-08-22T15:00:00"))      // 结束时间
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))    // 断言：只返回1条数据
                .andExpect(jsonPath("$.data[0].value").value(72.0));  // 断言：值是 12:00 的数据
    }

    /**
     * 测试 7: 请求体为空时返回400错误
     * <p>
     * 测试场景：设备发送空请求体
     * 预期结果：HTTP 400 Bad Request
     */
    @Test
    @Order(7)
    @DisplayName("测试7: 空请求体 — 返回400 Bad Request")
    void testReceiveData_EmptyBody() throws Exception {
        // 发送空请求体
        mockMvc.perform(post("/api/devices/data")
                .contentType(MediaType.APPLICATION_JSON)
                .content(""))                                // 空请求体
                .andExpect(status().isBadRequest());          // 断言：HTTP 400
    }

    /**
     * 测试 8: 多种设备类型同时上报数据
     * <p>
     * 测试场景：模拟多个不同设备同时上报不同类型的数据
     * 预期结果：所有数据都成功保存
     */
    @Test
    @Order(8)
    @DisplayName("测试8: 多设备同时上报 — 所有数据成功保存")
    void testMultipleDevicesReport() throws Exception {
        // 模拟设备1：智能手环上报心率
        DeviceDataDTO braceletData = DeviceDataDTO.builder()
                .deviceId("BRACELET-001")
                .timestamp(LocalDateTime.now())
                .type("heart_rate")
                .value(72.5)
                .build();
        mockMvc.perform(post("/api/devices/data")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(braceletData)))
                .andExpect(status().isCreated());            // 断言：成功

        // 模拟设备2：床垫传感器上报离床信息
        DeviceDataDTO bedData = DeviceDataDTO.builder()
                .deviceId("BED-001")
                .timestamp(LocalDateTime.now())
                .type("bed_leave")
                .value(1.0)                                  // 1.0 表示离床
                .build();
        mockMvc.perform(post("/api/devices/data")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(bedData)))
                .andExpect(status().isCreated());            // 断言：成功

        // 模拟设备3：紧急呼叫按钮被按下
        DeviceDataDTO emergencyData = DeviceDataDTO.builder()
                .deviceId("EMERGENCY-005")
                .timestamp(LocalDateTime.now())
                .type("emergency")
                .value(1.0)                                  // 1.0 表示报警
                .build();
        mockMvc.perform(post("/api/devices/data")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(emergencyData)))
                .andExpect(status().isCreated());            // 断言：成功

        // 验证：数据库中应该有三条数据
        mockMvc.perform(get("/api/devices/BRACELET-001/data/count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(1));     // 断言：手环1条数据

        // 验证所有设备数据总数
        long totalCount = repository.count();                // 直接查询数据库总数
        Assertions.assertEquals(3, totalCount, "数据库中应该有3条数据");  // 断言：总数为3
    }
}
```

---

## 四、运行验证

### 4.1 启动服务

在项目根目录 `iot-device-receiver/` 下执行：

```bash
# 使用 Maven 编译并启动 Spring Boot 应用
# Spring Boot Maven 插件会自动下载依赖、编译代码、启动内嵌 Tomcat
mvn spring-boot:run
```

启动成功后，控制台会输出类似以下信息：

```
  .   ____          _            __ _ _
 /\\ / ___'_ __ _ _(_)_ __  __ _ \ \ \ \
( ( )\___ | '_ | '_| | '_ \/ _` | \ \ \ \
 \\/  ___)| |_)| | | | | || (_| |  ) ) ) )
  '  |____| .__|_| |_|_| |_\__, | / / / /
 =========|_|==============|___/=/_/_/_/
 :: Spring Boot ::                (v3.3.5)

2026-08-22T14:30:00.000  INFO 12345 --- [main] c.z.iot.IotApplication
: Started IotApplication in 2.345 seconds (process running for 2.567)
```

### 4.2 使用 curl 模拟设备上报数据

打开终端，使用 curl 命令模拟设备发送数据：

```bash
# 模拟智能手环上报心率数据
curl -X POST http://localhost:8080/api/devices/data \
  -H "Content-Type: application/json" \
  -d '{
    "deviceId": "BRACELET-001",
    "timestamp": "2026-08-22T14:30:00",
    "type": "heart_rate",
    "value": 72.5
  }'
```

**预期响应**：
```json
{
  "code": 200,
  "msg": "success",
  "data": {
    "id": 1,
    "deviceId": "BRACELET-001",
    "timestamp": "2026-08-22T14:30:00",
    "type": "heart_rate",
    "value": 72.5,
    "receivedAt": "2026-08-22T14:30:05.123"
  }
}
```

### 4.3 验证更多场景

```bash
# 场景1：床垫传感器上报离床报警
curl -X POST http://localhost:8080/api/devices/data \
  -H "Content-Type: application/json" \
  -d '{
    "deviceId": "BED-001",
    "timestamp": "2026-08-22T15:00:00",
    "type": "bed_leave",
    "value": 1.0
  }'

# 场景2：紧急呼叫按钮触发报警
curl -X POST http://localhost:8080/api/devices/data \
  -H "Content-Type: application/json" \
  -d '{
    "deviceId": "EMERGENCY-005",
    "timestamp": "2026-08-22T15:01:00",
    "type": "emergency",
    "value": 1.0
  }'

# 场景3：查询 BRACELET-001 的所有数据
curl http://localhost:8080/api/devices/BRACELET-001/data

# 场景4：统计 BRACELET-001 的数据条数
curl http://localhost:8080/api/devices/BRACELET-001/data/count

# 场景5：查询时间范围数据
curl "http://localhost:8080/api/devices/BRACELET-001/data/range?start=2026-08-22T00:00:00&end=2026-08-22T23:59:59"
```

### 4.4 使用 H2 Console 查看数据

H2 数据库提供了一个 Web 界面，方便查看和管理数据：

1. 浏览器访问：`http://localhost:8080/h2-console`
2. 登录信息：
   - JDBC URL：`jdbc:h2:mem:iotdb`
   - 用户名：`sa`
   - 密码：（留空）
3. 在 SQL 输入框中执行查询：

```sql
-- 查看所有设备数据
SELECT * FROM device_data ORDER BY received_at DESC;

-- 统计各设备的数据条数
SELECT device_id, COUNT(*) AS data_count
FROM device_data
GROUP BY device_id
ORDER BY data_count DESC;

-- 查询最新 10 条报警数据
SELECT * FROM device_data
WHERE type IN ('fall_alarm', 'emergency', 'bed_leave')
ORDER BY timestamp DESC
LIMIT 10;
```

### 4.5 运行测试

```bash
# 运行所有集成测试
mvn test

# 测试结果示例
[INFO] Tests run: 8, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

---

## 五、项目对照

### 5.1 本文示例 vs 真实 zznursing 项目

本文示例是一个 IoT 数据接收的入门级实现，旨在帮助初学者理解核心流程。真实的 zznursing 项目在以下五个维度进行了深度扩展：

| 对比维度 | 本文示例（入门级） | 真实 zznursing（生产级） | 差异说明 |
|---------|------------------|----------------------|---------|
| **通信协议** | HTTP REST API | MQTT + HTTP 双协议 | MQTT 支持设备长连接、低功耗、QoS 保证，适合电池供电的养老设备 |
| **设备认证** | 无认证（设备ID明文） | JWT + 设备证书双认证 | 防止伪造设备上报虚假数据，保障数据源可信 |
| **数据清洗** | 仅 @Valid 基础校验 | 规则引擎 + 异常值过滤 | 剔除传感器噪声数据（如心率 0 或 999 等异常值） |
| **消息队列** | 直接同步写入数据库 | RocketMQ 异步解耦 | 削峰填谷，防止设备上报高峰压垮数据库 |
| **数据存储** | H2 内存数据库 | TDengine / InfluxDB 时序数据库 | IoT 数据是时间序列数据，时序数据库压缩率更高、查询更快 |

### 5.2 生产级架构演进路线

从本文示例到生产级 zznursing 的演进路线：

```
Phase 1: HTTP + JPA（本文示例）
    ↓ 设备增多，数据库压力增大
Phase 2: HTTP + RocketMQ + JPA
    ↓ 需要实时推送，设备功耗优化
Phase 3: MQTT + RocketMQ + JPA
    ↓ 数据量爆炸，关系型数据库瓶颈
Phase 4: MQTT + RocketMQ + TDengine
    ↓ 需要统一设备管理
Phase 5: MQTT + RocketMQ + TDengine + 设备注册中心
```

### 5.3 核心差异详解

**MQTT 协议优势**：
- **低功耗**：MQTT 协议头部仅 2 字节，比 HTTP 小 100 倍以上
- **长连接**：设备与服务器保持 TCP 连接，减少反复握手的开销
- **QoS 保证**：支持最多一次、至少一次、正好一次三种消息质量等级
- **发布/订阅**：一个设备发布数据，多个消费者订阅，天然适合微服务架构

**RocketMQ 消息队列**：
- 设备上报高峰时（如整点批量上报），消息队列充当缓冲区
- 数据先写入 RocketMQ，业务服务异步消费，避免数据库被瞬间压垮
- 支持消息回溯，数据丢失后可重新消费

**TDengine 时序数据库**：
- 专为 IoT 时序数据设计，压缩率是关系型数据库的 10-20 倍
- 单台服务器可处理每秒数百万条数据写入
- 提供自动降采样、数据保留策略等 IoT 专用功能

---

## 六、面试题

### 面试题 1：RESTful API 设计

**问题**：在设计 IoT 设备数据上报的 RESTful API 时，为什么选择 POST 方法而不是 PUT 或 PATCH？请结合幂等性（Idempotency）概念解释。

**参考答案**：
- POST 不是幂等的，多次调用会创建多条数据。IoT 设备每次上报的都是一次新的数据观测，每条数据代表一个独立的时间点，因此应该创建多条记录。
- PUT 是幂等的，多次调用同一接口结果相同。如果使用 PUT，同一设备同一时间戳的数据会被覆盖，不符合 IoT 数据采集场景（每次都是新的观测值）。
- 选择 POST 方法，请求路径为 `/api/devices/data`，语义上表示"向设备数据集合中添加一条新记录"。

### 面试题 2：JPA 实体设计

**问题**：本文中 DeviceDataEntity 的 `id` 字段使用了 `@GeneratedValue(strategy = GenerationType.IDENTITY)`，这个策略有什么优缺点？在 IoT 海量数据场景下，你有什么更好的建议？

**参考答案**：
- IDENTITY 策略依赖数据库自增主键，每次插入后需要回写 ID，批量插入性能较差。
- 在海量 IoT 数据场景下，建议：
  1. 使用 **雪花算法（Snowflake）** 生成分布式唯一 ID，避免数据库自增瓶颈
  2. 使用 **数据库序列（Sequence）**，如 `GenerationType.SEQUENCE`，通过预分配 ID 区间提升批量插入性能
  3. 或者直接使用 **UUID** 作为主键，但 UUID 作为主键在 B+ 树索引中会产生碎片，需配合 `@UuidGenerator` 使用有序 UUID

### 面试题 3：IoT 数据接收的挑战

**问题**：假设养老院有 5000 个设备，每个设备每 5 秒上报一次数据，服务端需要处理哪些挑战？请从网络、存储、计算三个维度分析。

**参考答案**：

**网络维度**：
- **并发连接**：5000 个设备 × 每 5 秒一次 = 每秒 1000 次请求，需要配置 Tomcat 线程池
- **带宽**：每次请求约 200 字节，每秒约 200KB 流量，上行带宽需要保证
- 解决方案：使用 MQTT 长连接 + Netty 异步非阻塞 I/O

**存储维度**：
- 每天数据量：86400 秒 ÷ 5 秒 × 5000 设备 = 每天 8640 万条数据
- 关系型数据库单表存储 8640 万条数据后，查询性能急剧下降
- 解决方案：使用时序数据库 TDengine（压缩率 20:1）+ 按天分表 + 数据保留策略（30 天后自动清理原始数据）

**计算维度**：
- 实时计算：需要对心率、体温等数据进行实时异常检测（如心率 > 120 触发报警）
- 批量计算：每日生成健康报告、趋势分析
- 解决方案：使用 RocketMQ 消息队列解耦 + Flink 流式计算处理实时 + 批处理任务在凌晨低谷执行

---

## 附录：完整代码仓库

本文所有代码可在以下路径找到：

```
iot-device-receiver/
├── pom.xml
├── src/main/java/com/zznursing/iot/
│   ├── IotApplication.java
│   ├── controller/
│   │   └── DeviceDataController.java
│   ├── service/
│   │   └── DeviceDataService.java
│   ├── entity/
│   │   └── DeviceDataEntity.java
│   ├── repository/
│   │   └── DeviceDataRepository.java
│   ├── dto/
│   │   ├── DeviceDataDTO.java
│   │   └── ApiResponse.java
│   └── exception/
│       └── GlobalExceptionHandler.java
├── src/main/resources/
│   └── application.yml
└── src/test/java/com/zznursing/iot/
    └── IotApplicationTest.java
```

**快速开始**：

```bash
# 1. 创建项目目录
mkdir -p iot-device-receiver && cd iot-device-receiver

# 2. 将上述所有文件放入对应目录

# 3. 编译并启动
mvn spring-boot:run

# 4. 在另一个终端中模拟设备上报
curl -X POST http://localhost:8080/api/devices/data \
  -H "Content-Type: application/json" \
  -d '{"deviceId":"BRACELET-001","timestamp":"2026-08-22T14:30:00","type":"heart_rate","value":72.5}'
```

---

> **下一篇预告**：Level 1 入门篇 · 02-MQTT 协议接入：从 HTTP 到 MQTT，拥抱物联网标准协议  
> 内容预告：EMQX 搭建、MQTT 客户端开发、QoS 等级、遗嘱消息、设备心跳检测