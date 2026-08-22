# 华为IoTDA入门：第一个设备接入与控制

> **zznursing 深度系列 | Level 1 入门篇**
>
> 本文面向 Java 后端初学者，从零搭建一个完整的 Spring Boot 应用，模拟养老院设备通过华为云 IoTDA 上报数据，并接收云平台下发的控制指令。读完本文，你将掌握 IoTDA 设备接入的核心流程，能在 30 分钟内跑通第一个"设备→云→设备"闭环。

---

## 一、项目背景

### 1.1 什么是华为云 IoTDA

**IoTDA（IoT Device Access）** 是华为云提供的一站式设备接入托管服务。它位于物联网架构的"连接层"，承担着海量设备与云端应用之间的桥梁角色。简单说，设备端装上 SIM 卡或 Wi-Fi 模组后，通过 MQTT 等协议连接到 IoTDA，IoTDA 再把设备数据路由到你的业务应用，同时接收应用下发的指令并转发给设备。

华为云 IoTDA 的核心能力可以概括为四个维度：

| 能力维度 | 说明 |
|---------|------|
| **设备接入** | 支持 MQTT、CoAP、LwM2M、HTTP 等多种协议，提供设备认证与鉴权 |
| **消息通信** | 设备数据上报、云端命令下发、双向消息路由 |
| **设备影子** | 云端缓存设备最新状态，应用和设备解耦读写 |
| **规则引擎** | 将设备数据流转到其他云服务（Kafka、OBS、FunctionGraph 等） |

### 1.2 zznursing 为什么选择华为云 IoTDA

zznursing 智慧养老平台在技术选型时，曾对比过多个方案，最终选择华为云 IoTDA，主要基于以下考量：

**设备量级与弹性。** 养老院场景虽然单个院区设备数（几百台）不算大，但平台需要覆盖全国数百家养老院，总设备量级在十万到百万级别。IoTDA 原生支持水平扩展，无需关心底层集群运维。

**可靠性要求。** 老人的健康监测数据（心率、血氧、跌倒报警）属于"关键数据"，不允许丢失。IoTDA 提供 QoS 1（至少一次）和 QoS 2（恰好一次）的消息投递保障，配合设备影子机制，即使设备离线也能在恢复后同步最新状态。

**国内合规与生态。** 作为面向国内养老行业的平台，数据必须存储在国内，且需要满足等保合规要求。华为云 IoTDA 已通过多项认证，同时与华为云其他服务（OBS 存储、FunctionGraph 函数计算、RocketMQ 消息队列）无缝集成，生态完善。

### 1.3 自建 MQTT Broker vs 云平台托管

有些团队可能会问："MQTT 协议是开放的，为什么不用开源的 EMQX、Mosquitto 自建？"

| 对比维度 | 自建 MQTT（EMQX/Mosquitto） | 华为云 IoTDA |
|---------|---------------------------|-------------|
| **部署运维** | 需要自己搭建集群、配置高可用、监控告警 | 开箱即用，免运维 |
| **设备管理** | 需要自建设备注册、认证、影子等管理功能 | 内置设备管理控制台 |
| **规则引擎** | 需要自研或集成额外组件 | 内置规则引擎，可直接流转到其他云服务 |
| **成本** | 中低负载时成本低，高负载时运维成本高 | 按设备量/消息量计费，小规模免费额度 |
| **灵活性** | 完全可控，可深度定制 | 平台能力范围内可控 |

对于 zznursing 这样需要快速交付、聚焦业务逻辑而非基础设施的团队，选择 IoTDA 是合理的。但如果团队有专职 IoT 基础设施团队、设备量级极大且对成本敏感，自建也是可行的方案。

---

## 二、核心概念

在开始写代码之前，我们需要先理解 IoTDA 的几个核心概念。这些概念是所有 IoT 平台（阿里云 IoT、AWS IoT Core、Azure IoT Hub）的通用抽象，掌握了它们，换到任何平台都能快速上手。

### 2.1 设备注册与认证

设备要接入 IoTDA，必须先注册。每个设备在云平台上有一个唯一的身份标识。

**设备 ID（Device ID）：** 全局唯一标识，类似设备的"身份证号"。在 MQTT 连接中，设备 ID 作为 Client ID 的一部分。

**设备密钥（Device Secret）：** 设备认证的凭证。设备连接 IoTDA 时，使用设备 ID + 密钥生成 MQTT 连接密码，IoTDA 端验证通过后才允许接入。

认证流程简化为三步：

```
设备端：用设备ID + 密钥生成MQTT连接参数
   ↓
IoTDA：验证身份，返回连接成功/失败
   ↓
设备端：连接成功后，开始订阅/发布 Topic
```

> **注意：** 生产环境密钥不能硬编码在代码中，应通过密钥管理服务（KMS）或环境变量注入。

### 2.2 MQTT 主题（Topic）设计

IoTDA 使用 MQTT 协议进行通信，所有的消息收发都通过 Topic 完成。Topic 的命名规则有严格约定，格式如下：

```
$oc/devices/{device_id}/sys/{category}/{action}
```

常见的内置 Topic：

| Topic 模板 | 方向 | 用途 |
|-----------|------|------|
| `$oc/devices/{device_id}/sys/properties/report` | 设备→云 | 设备属性上报 |
| `$oc/devices/{device_id}/sys/properties/set/response` | 设备→云 | 设置属性响应 |
| `$oc/devices/{device_id}/sys/commands/down` | 云→设备 | 平台下发命令 |
| `$oc/devices/{device_id}/sys/commands/response/request_id={request_id}` | 设备→云 | 命令响应 |
| `$oc/devices/{device_id}/sys/messages/up` | 设备→云 | 设备消息上报 |
| `$oc/devices/{device_id}/sys/messages/down` | 云→设备 | 平台下发消息 |

在我们今天的示例中，主要使用 **属性上报** 和 **命令下发** 两个 Topic。

### 2.3 设备影子（Device Shadow）

**设备影子** 是 IoTDA 的一个关键特性。它本质上是一个 JSON 文档，在云端持久化存储设备的"期望状态"和"报告状态"。

为什么需要设备影子？

- **设备离线时也能更新状态。** 应用端通过 API 修改设备影子中的"期望状态"，设备上线后自动同步。
- **应用和设备解耦。** 应用不需要关心设备当前是否在线，只需读写影子即可。
- **状态缓存。** 即使设备不在线，应用也能从影子中获取设备最新的已知状态。

举个例子：当护工通过管理后台远程调节老人房间的空调温度，如果空调此时离线，控制指令不会丢失 —— 指令会保存在设备影子中。空调上线后，IoTDA 自动将影子的期望温度下发给设备。

### 2.4 消息推送（数据上报 / 命令下发）

IoTDA 的消息通信模型是双向的：

**数据上报（设备→云）：** 设备传感器采集数据后，通过 MQTT 发布到属性上报 Topic，IoTDA 收到后可以：
- 更新设备影子
- 通过规则引擎转发到其他服务
- 通过 AMQP 或 HTTP 推送给应用端

**命令下发（云→设备）：** 应用端通过 IoTDA 的 API 或控制台向设备下发命令，IoTDA 将命令通过 MQTT 推送给设备。设备执行后回复响应，IoTDA 再将响应返回给应用端。

### 2.5 规则引擎（数据流转）

规则引擎是 IoTDA 的"数据路由中枢"。它允许你编写类 SQL 规则，将设备数据流转到其他云服务：

```
SELECT * FROM /sys/properties/report WHERE temperature > 38
→ 转发给 FunctionGraph 触发告警短信
→ 转发给 Kafka 做数据分析
→ 转发给 OBS 做数据归档
```

在 zznursing 中，规则引擎用于：
- 将老人的健康异常数据实时推送给告警服务
- 将设备日志归档到 OBS 用于审计
- 将设备状态变更事件推送给业务系统

---

## 三、从零搭建代码

下面我们开始搭建一个完整的 Java 21 + Spring Boot 3.3.5 项目，模拟一个养老院智能设备通过 IoTDA 上报数据并接收命令。

### 3.1 项目结构

```
iotda-hello-world/
├── pom.xml                                    # Maven 构建文件
├── src/main/resources/
│   └── application.yml                        # 应用配置
└── src/main/java/com/zznursing/iotda/
    ├── IotdaApplication.java                  # 启动类
    ├── config/
    │   └── IotdaConfig.java                   # IoTDA 客户端配置
    ├── model/
    │   └── DeviceProperty.java                # 设备属性模型
    ├── service/
    │   ├── DeviceDataPublisher.java           # 设备数据上报服务
    │   └── CommandCallback.java               # 命令下发回调处理器
    └── runner/
        └── DeviceSimulator.java               # 设备模拟器
```

### 3.2 pom.xml

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <!-- 父工程：Spring Boot 3.3.5，统一管理版本号 -->
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.3.5</version>
        <relativePath/>
    </parent>

    <groupId>com.zznursing</groupId>
    <artifactId>iotda-hello-world</artifactId>
    <version>1.0.0</version>
    <name>iotda-hello-world</name>
    <description>zznursing 智慧养老 - IoTDA 设备接入示例</description>

    <properties>
        <!-- 使用 Java 21 长期支持版本 -->
        <java.version>21</java.version>
        <!-- Eclipse Paho MQTT 客户端版本 -->
        <paho-mqtt.version>1.2.5</paho-mqtt.version>
        <!-- Jackson 用于 JSON 序列化，Spring Boot 已管理版本，此处无需指定 -->
    </properties>

    <dependencies>
        <!-- Spring Boot Web Starter：提供 REST 支持，用于健康检查和调试接口 -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>

        <!-- Spring Boot Actuator：提供应用监控和健康检查端点 -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>

        <!-- Eclipse Paho MQTT v5 客户端：用于与 IoTDA 建立 MQTT 连接 -->
        <dependency>
            <groupId>org.eclipse.paho</groupId>
            <artifactId>org.eclipse.paho.mqttv5.client</artifactId>
            <version>${paho-mqtt.version}</version>
        </dependency>

        <!-- Jackson 数据绑定：用于 Java 对象与 JSON 之间的互转 -->
        <dependency>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-databind</artifactId>
        </dependency>

        <!-- Lombok：简化 POJO 代码（@Data、@Slf4j 等注解） -->
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>

        <!-- Spring Boot 测试 Starter：包含 JUnit 5、Mockito 等测试框架 -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <!-- Spring Boot Maven 插件：支持打包成可执行 JAR -->
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <configuration>
                    <!-- 排除 Lombok 避免打包到最终 JAR 中 -->
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
# zznursing 智慧养老平台 - IoTDA 设备接入示例配置文件
# ============================================================

# 应用基础配置
spring:
  application:
    name: iotda-hello-world

# 服务器配置：用于提供健康检查和调试接口
server:
  port: 8080

# IoTDA 连接配置（自定义前缀，通过 @ConfigurationProperties 读取）
iotda:
  # 华为云 IoTDA 平台连接地址，格式：tcp://{iotda-address}:1883
  # 生产环境使用 SSL 加密：ssl://{iotda-address}:8883
  host: "tcp://your-iotda-host.iotda.cn-north-4.myhuaweicloud.com:1883"

  # 设备 ID：在 IoTDA 控制台注册设备时生成
  device-id: "your_device_id"

  # 设备密钥：在 IoTDA 控制台注册设备时设置
  device-secret: "your_device_secret"

  # MQTT 连接超时时间（秒）
  connection-timeout: 30

  # 心跳间隔（秒）：设备与 IoTDA 之间保持连接的保活时间
  keep-alive-interval: 60

  # 数据上报相关配置
  report:
    # 属性上报的 Topic，遵循 IoTDA 标准格式
    # $oc/devices/{device_id}/sys/properties/report
    property-topic: "$oc/devices/{device_id}/sys/properties/report"

    # 数据上报周期（秒）：模拟器每隔多久上报一次数据
    interval-seconds: 10

  # 命令下发相关配置
  command:
    # 订阅命令下发的 Topic，设备端监听此 Topic 接收云端指令
    # $oc/devices/{device_id}/sys/commands/down
    subscribe-topic: "$oc/devices/{device_id}/sys/commands/down"

# 日志配置
logging:
  level:
    # 打印 IoTDA 相关日志，便于调试
    com.zznursing.iotda: DEBUG
    # MQTT 客户端日志
    org.eclipse.paho: INFO
```

### 3.4 IotdaConfig.java - IoTDA 客户端配置

```java
package com.zznursing.iotda.config;

import com.zznursing.iotda.service.CommandCallback;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.mqttv5.client.*;
import org.eclipse.paho.mqttv5.client.persist.MemoryPersistence;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.packet.MqttProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/**
 * IoTDA 客户端配置类。
 * <p>
 * 负责初始化 MQTT 客户端，建立与华为云 IoTDA 的连接。
 * 主要职责包括：
 * 1. 读取配置文件的连接参数
 * 2. 使用 HMAC-SHA256 算法生成 MQTT 连接密码
 * 3. 创建并配置 MqttClient 实例
 * 4. 设置命令回调处理器
 * 5. 发起连接并返回客户端 Bean
 */
@Slf4j
@Configuration
public class IotdaConfig {

    // ======================== 配置属性注入 ========================

    /**
     * IoTDA 平台地址，格式：tcp://host:port 或 ssl://host:port
     */
    @Value("${iotda.host}")
    private String host;

    /**
     * 设备 ID，在 IoTDA 控制台注册设备时生成
     */
    @Value("${iotda.device-id}")
    private String deviceId;

    /**
     * 设备密钥，在 IoTDA 控制台注册设备时设置
     */
    @Value("${iotda.device-secret}")
    private String deviceSecret;

    /**
     * MQTT 连接超时时间（秒）
     */
    @Value("${iotda.connection-timeout}")
    private int connectionTimeout;

    /**
     * MQTT 心跳保活间隔（秒）
     */
    @Value("${iotda.keep-alive-interval}")
    private int keepAliveInterval;

    // ======================== MQTT 客户端 Bean ========================

    /**
     * 创建并配置 MQTT 客户端，作为 Spring Bean 管理。
     * <p>
     * 此方法完成以下步骤：
     * 1. 根据 IoTDA 的认证规范，使用 HMAC-SHA256 对设备 ID 和密钥进行签名，生成 MQTT 密码
     * 2. 配置 MQTT 连接选项（超时、心跳、自动重连等）
     * 3. 创建 MqttClient 实例并建立连接
     * 4. 订阅命令下发 Topic，注册回调处理器
     * 5. 返回客户端实例供其他组件使用
     *
     * @param commandCallback 命令下发回调处理器，由 Spring 自动注入
     * @return 已连接的 MqttClient 实例
     * @throws MqttException 如果连接失败则抛出异常
     */
    @Bean(destroyMethod = "close")
    public MqttClient mqttClient(CommandCallback commandCallback) throws MqttException {
        // 步骤1：生成 IoTDA 认证所需的 MQTT 连接密码
        // IoTDA 使用 HMAC-SHA256 算法：密码 = Base64(HMAC-SHA256(device_secret, device_id))
        String password = generateIotdaPassword(deviceId, deviceSecret);
        log.info("已生成 IoTDA 连接密码（设备ID: {}）", deviceId);

        // 步骤2：配置 MQTT 连接选项
        MqttConnectionOptions options = new MqttConnectionOptions();
        // 设置 IoTDA 平台地址
        options.setServerURIs(new String[]{host});
        // 设置用户名固定为设备 ID
        options.setUserName(deviceId);
        // 设置生成的加密密码
        options.setPassword(password.getBytes(StandardCharsets.UTF_8));
        // 设置连接超时时间
        options.setConnectionTimeout(connectionTimeout);
        // 设置心跳保活间隔
        options.setKeepAliveInterval(keepAliveInterval);
        // 启用自动重连：网络断开后自动尝试重新连接
        options.setAutomaticReconnect(true);
        // 设置自动重连的最大等待时间（秒）
        options.setMaxReconnectDelay(30);
        // 清理会话：每次连接都是全新会话，不恢复之前的订阅
        options.setCleanStart(true);

        // 步骤3：创建 MQTT 客户端实例
        // 使用内存持久化方式（MemoryPersistence），不持久化消息到磁盘
        MqttClient client = new MqttClient(host, deviceId, new MemoryPersistence());

        // 步骤4：设置命令回调处理器
        // 当云端下发命令时，回调处理器会收到消息并处理
        client.setCallback(commandCallback);

        // 步骤5：建立连接
        log.info("正在连接 IoTDA 平台: {}", host);
        IMqttToken token = client.connect(options);
        // 等待连接完成
        token.waitForCompletion();
        log.info("IoTDA 连接成功！设备ID: {}", deviceId);

        // 步骤6：订阅命令下发 Topic
        // 设备端订阅此主题后，才能接收云端下发的控制指令
        String commandTopic = "$oc/devices/" + deviceId + "/sys/commands/down";
        client.subscribe(commandTopic, 1);
        log.info("已订阅命令下发 Topic: {}", commandTopic);

        return client;
    }

    // ======================== 密码生成工具 ========================

    /**
     * 生成 IoTDA MQTT 连接密码。
     * <p>
     * 华为云 IoTDA 的认证算法：
     * 1. 使用设备密钥（deviceSecret）作为 HMAC 密钥
     * 2. 对设备 ID（deviceId）进行 HMAC-SHA256 签名
     * 3. 将签名结果进行 Base64 编码
     * <p>
     * 这种认证方式保证了密钥不会以明文在网络中传输，提高了安全性。
     *
     * @param deviceId   设备 ID
     * @param deviceSecret 设备密钥
     * @return Base64 编码后的 HMAC-SHA256 签名
     */
    private String generateIotdaPassword(String deviceId, String deviceSecret) {
        try {
            // 获取 HMAC-SHA256 算法实例
            Mac mac = Mac.getInstance("HmacSHA256");
            // 使用设备密钥初始化 Mac 对象
            SecretKeySpec keySpec = new SecretKeySpec(
                    deviceSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"
            );
            mac.init(keySpec);
            // 对设备 ID 进行签名计算
            byte[] hmacBytes = mac.doFinal(deviceId.getBytes(StandardCharsets.UTF_8));
            // 将签名结果 Base64 编码后返回
            return Base64.getEncoder().encodeToString(hmacBytes);
        } catch (NoSuchAlgorithmException e) {
            // 正常情况下不会发生，HMAC-SHA256 是 Java 标准算法
            log.error("不支持的加密算法: HmacSHA256", e);
            throw new RuntimeException("HMAC-SHA256 算法不可用", e);
        } catch (InvalidKeyException e) {
            log.error("无效的设备密钥", e);
            throw new RuntimeException("设备密钥格式错误", e);
        }
    }
}
```

### 3.5 DeviceProperty.java - 设备属性模型

```java
package com.zznursing.iotda.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 设备属性模型。
 * <p>
 * 使用 Java 21 的 Record 类型定义不可变的数据载体。
 * 该模型对应 IoTDA 属性上报 JSON 中的 services 数组元素。
 * <p>
 * IoTDA 属性上报的 JSON 格式示例：
 * {
 *   "services": [{
 *     "service_id": "location",
 *     "properties": {
 *       "latitude": 39.9042,
 *       "longitude": 116.4074
 *     },
 *     "event_time": "2026-08-22T10:30:00Z"
 *   }]
 * }
 *
 * @param serviceId  服务 ID，在 IoTDA 产品模型中定义，如 "location"、"health"、"alert"
 * @param properties 属性键值对，根据 serviceId 不同包含不同的属性字段
 * @param eventTime  事件发生时间，ISO 8601 格式，如 "2026-08-22T10:30:00Z"
 */
public record DeviceProperty(
        /**
         * 服务 ID。
         * 在 IoTDA 产品模型中定义，每个服务包含一组相关的属性。
         * 例如：
         * - "location" 服务包含 latitude（纬度）、longitude（经度）
         * - "health" 服务包含 heartRate（心率）、bloodPressure（血压）
         * - "alert" 服务包含 alertType（告警类型）、message（告警消息）
         */
        @JsonProperty("service_id")
        String serviceId,

        /**
         * 属性键值对。
         * 使用 Java Map 存储灵活的键值结构，不同的服务包含不同的属性字段。
         * 例如：{"latitude": 39.9042, "longitude": 116.4074}
         */
        @JsonProperty("properties")
        java.util.Map<String, Object> properties,

        /**
         * 事件发生时间，ISO 8601 格式。
         * 如果没有指定，IoTDA 平台会自动填入接收时间。
         */
        @JsonProperty("event_time")
        String eventTime
) {
}
```

### 3.6 DeviceDataPublisher.java - 设备数据上报服务

```java
package com.zznursing.iotda.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zznursing.iotda.model.DeviceProperty;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 设备数据上报服务。
 * <p>
 * 负责模拟养老院设备的各种数据上报场景，包括：
 * 1. 🌡️ 位置信息上报（经纬度）
 * 2. ❤️ 健康数据上报（心率、血氧、体温）
 * 3. 🚨 报警事件上报（跌倒告警、紧急呼叫）
 * 4. 🔋 设备状态上报（电量、信号强度）
 * <p>
 * 每次上报时，服务会构造符合 IoTDA 规范的 JSON 消息，
 * 并通过 MQTT 发布到属性上报 Topic。
 */
@Slf4j
@Service
public class DeviceDataPublisher {

    /**
     * MQTT 客户端，由 IotdaConfig 注入
     */
    private final MqttClient mqttClient;

    /**
     * Jackson ObjectMapper，用于将 Java 对象序列化为 JSON 字符串
     */
    private final ObjectMapper objectMapper;

    /**
     * 设备 ID，从配置文件中读取
     */
    @Value("${iotda.device-id}")
    private String deviceId;

    /**
     * 随机数生成器，用于模拟传感器数据
     */
    private final Random random = new Random();

    /**
     * 消息序列号计数器，用于跟踪已发送的消息数量
     */
    private final AtomicInteger messageCounter = new AtomicInteger(0);

    /**
     * 构造函数，通过依赖注入获取 MQTT 客户端和 ObjectMapper。
     *
     * @param mqttClient    MQTT 客户端实例
     * @param objectMapper  Jackson JSON 处理器
     */
    public DeviceDataPublisher(MqttClient mqttClient, ObjectMapper objectMapper) {
        this.mqttClient = mqttClient;
        this.objectMapper = objectMapper;
    }

    // ======================== 公开方法 ========================

    /**
     * 上报位置信息。
     * <p>
     * 模拟养老院老人的定位设备，上报当前经纬度。
     * 位置数据用于：电子围栏告警、老人轨迹追踪、附近护工调度。
     * <p>
     * 纬度范围：养老院所在的北京市区，约 39.85 ~ 39.95
     * 经度范围：约 116.30 ~ 116.50
     */
    public void reportLocation() {
        // 构造位置属性：纬度（39.85~39.95 之间随机）
        // 构造位置属性：经度（116.30~116.50 之间随机）
        Map<String, Object> properties = new HashMap<>();
        // 纬度：北京市区范围，保留 4 位小数
        properties.put("latitude", 39.85 + (random.nextDouble() * 0.10));
        // 经度：北京市区范围，保留 4 位小数
        properties.put("longitude", 116.30 + (random.nextDouble() * 0.20));
        // 位置精度（米）：模拟 GPS 定位精度，通常在 5~50 米之间
        properties.put("accuracy", 5 + random.nextInt(46));

        // 创建属性记录，服务 ID 为 "location"
        DeviceProperty property = new DeviceProperty(
                "location",
                properties,
                Instant.now().toString()  // 当前时间的 ISO 8601 格式
        );

        // 发布到 IoTDA
        publishProperty(property);
    }

    /**
     * 上报健康数据。
     * <p>
     * 模拟老人的穿戴设备（智能手环/手表），上报生理指标。
     * 这些数据是智慧养老平台的核心数据，用于：
     * - 实时健康监测大屏
     * - 异常指标自动告警
     * - 健康趋势分析
     */
    public void reportHealthData() {
        // 构造健康属性
        Map<String, Object> properties = new HashMap<>();
        // 心率（次/分钟）：正常范围 60~100，老人可能略低或略高
        properties.put("heart_rate", 60 + random.nextInt(41));
        // 血氧饱和度（%）：正常范围 95~100，低于 90 为危险
        properties.put("blood_oxygen", 95 + random.nextInt(6));
        // 体温（摄氏度）：正常范围 36.0~37.3
        properties.put("temperature", 36.0 + (random.nextDouble() * 1.3));
        // 收缩压（mmHg）：正常范围 90~140
        properties.put("systolic_pressure", 90 + random.nextInt(51));
        // 舒张压（mmHg）：正常范围 60~90
        properties.put("diastolic_pressure", 60 + random.nextInt(31));

        // 创建属性记录，服务 ID 为 "health"
        DeviceProperty property = new DeviceProperty(
                "health",
                properties,
                Instant.now().toString()
        );

        // 发布到 IoTDA
        publishProperty(property);
    }

    /**
     * 上报报警事件。
     * <p>
     * 模拟紧急情况下的设备告警，如跌倒检测、紧急按钮触发等。
     * 报警数据需要高优先级处理，通常会触发：
     * - 短信/电话通知家属或护工
     * - 大屏弹窗告警
     * - 生成工单记录
     *
     * @param alertType 告警类型：FALL（跌倒）、EMERGENCY（紧急呼叫）、LOW_BATTERY（低电量）
     * @param message   告警描述信息
     */
    public void reportAlert(String alertType, String message) {
        // 构造告警属性
        Map<String, Object> properties = new HashMap<>();
        // 告警类型
        properties.put("alert_type", alertType);
        // 告警描述
        properties.put("message", message);
        // 告警级别：1-紧急，2-重要，3-一般
        properties.put("severity", alertType.equals("FALL") ? 1 : 2);

        // 创建属性记录，服务 ID 为 "alert"
        DeviceProperty property = new DeviceProperty(
                "alert",
                properties,
                Instant.now().toString()
        );

        // 发布到 IoTDA
        publishProperty(property);
        // 报警日志单独打印，便于在控制台突出显示
        log.warn("🚨 报警事件已上报: type={}, message={}", alertType, message);
    }

    /**
     * 上报设备状态信息。
     * <p>
     * 模拟设备自身的运行状态，用于设备运维监控。
     * 包括电量、信号强度、运行时长等指标。
     */
    public void reportDeviceStatus() {
        // 构建设备状态属性
        Map<String, Object> properties = new HashMap<>();
        // 电池电量百分比（%）：模拟电量逐渐下降
        properties.put("battery_level", 20 + random.nextInt(81));
        // 信号强度（dBm）：范围 -100 ~ -50，数值越大信号越好
        properties.put("signal_strength", -100 + random.nextInt(51));
        // 设备运行时长（小时）
        properties.put("uptime_hours", random.nextInt(720) + 1);
        // 设备固件版本号
        properties.put("firmware_version", "v1.2." + random.nextInt(10));

        // 创建属性记录，服务 ID 为 "device_status"
        DeviceProperty property = new DeviceProperty(
                "device_status",
                properties,
                Instant.now().toString()
        );

        // 发布到 IoTDA
        publishProperty(property);
    }

    // ======================== 私有方法 ========================

    /**
     * 将属性记录发布到 IoTDA 平台。
     * <p>
     * 核心方法：将 DeviceProperty 对象序列化为 JSON，
     * 包装成 MqttMessage，然后发布到属性上报 Topic。
     * <p>
     * IoTDA 属性上报的完整 JSON 格式：
     * {
     *   "services": [{
     *     "service_id": "health",
     *     "properties": {
     *       "heart_rate": 72,
     *       "blood_oxygen": 98
     *     },
     *     "event_time": "2026-08-22T10:30:00Z"
     *   }]
     * }
     *
     * @param property 设备属性记录
     */
    private void publishProperty(DeviceProperty property) {
        try {
            // 步骤1：构造 IoTDA 标准属性上报请求体
            // 外层需要包裹 "services" 数组
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("services", new DeviceProperty[]{property});

            // 步骤2：将请求体序列化为 JSON 字符串
            String jsonPayload = objectMapper.writeValueAsString(requestBody);

            // 步骤3：构建 MQTT 消息
            MqttMessage message = new MqttMessage();
            // 消息内容：序列化后的 JSON 字符串（UTF-8 编码）
            message.setPayload(jsonPayload.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            // QoS 等级：1（至少一次），确保消息可靠送达，允许重复
            message.setQos(1);
            // 保留消息：false，不保留在 Broker 上，避免新订阅者收到旧数据
            message.setRetained(false);

            // 步骤4：构造属性上报 Topic
            // 格式：$oc/devices/{device_id}/sys/properties/report
            String topic = "$oc/devices/" + deviceId + "/sys/properties/report";

            // 步骤5：发布消息到 IoTDA
            mqttClient.publish(topic, message);

            // 步骤6：记录日志
            int seq = messageCounter.incrementAndGet();
            log.info("📤 [{}] 已上报 {} 数据: {}", seq, property.serviceId(), jsonPayload);

        } catch (JsonProcessingException e) {
            // JSON 序列化异常：正常情况下不会发生，除非属性对象包含无法序列化的字段
            log.error("属性对象序列化失败: serviceId={}", property.serviceId(), e);
        } catch (MqttException e) {
            // MQTT 发布异常：可能是网络断开、连接未建立等原因
            log.error("MQTT 消息发布失败: topic={}, errorCode={}",
                    "$oc/devices/" + deviceId + "/sys/properties/report",
                    e.getReasonCode(), e);
        }
    }
}
```

### 3.7 CommandCallback.java - 命令下发回调处理器

```java
package com.zznursing.iotda.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.mqttv5.client.IMqttToken;
import org.eclipse.paho.mqttv5.client.MqttCallback;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.client.MqttDisconnectResponse;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.eclipse.paho.mqttv5.common.packet.MqttProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 命令下发回调处理器。
 * <p>
 * 实现 MqttCallback 接口，当 IoTDA 平台向设备下发命令时，
 * 此处理器会被触发，解析命令内容并执行相应的设备操作。
 * <p>
 * 支持的命令类型：
 * - "set_temperature"：设置空调温度
 * - "set_volume"：设置设备音量
 * - "reboot"：重启设备
 * - "query_status"：查询设备状态
 */
@Slf4j
@Service
public class CommandCallback implements MqttCallback {

    /**
     * MQTT 客户端，用于发送命令响应
     */
    private final MqttClient mqttClient;

    /**
     * Jackson ObjectMapper，用于解析和生成 JSON
     */
    private final ObjectMapper objectMapper;

    /**
     * 设备 ID，从配置文件中读取
     */
    @Value("${iotda.device-id}")
    private String deviceId;

    /**
     * 构造函数。
     *
     * @param mqttClient   MQTT 客户端实例
     * @param objectMapper Jackson JSON 处理器
     */
    public CommandCallback(MqttClient mqttClient, ObjectMapper objectMapper) {
        this.mqttClient = mqttClient;
        this.objectMapper = objectMapper;
    }

    // ======================== MqttCallback 接口实现 ========================

    /**
     * 当与 IoTDA 的连接断开时调用。
     * <p>
     * 由于我们在 IotdaConfig 中启用了自动重连（setAutomaticReconnect(true)），
     * 大多数情况下客户端会自动恢复连接，此处只需记录日志。
     *
     * @param disconnectResponse 断开连接响应，包含错误码和原因
     */
    @Override
    public void disconnected(MqttDisconnectResponse disconnectResponse) {
        log.warn("MQTT 连接断开: reasonCode={}, reason={}",
                disconnectResponse.getReasonCode(),
                disconnectResponse.getReasonString());
    }

    /**
     * 当 MQTT 客户端与其他方法（如 publish、subscribe）返回结果时调用。
     * <p>
     * 所有异步操作完成后都会触发此回调，我们在此记录传递的令牌信息。
     *
     * @param token 异步操作令牌，包含操作状态和结果
     */
    @Override
    public void mqttErrorOccurred(IMqttToken token) {
        log.error("MQTT 异步操作异常: {}", token.getException() != null
                ? token.getException().getMessage() : "未知异常");
    }

    /**
     * 当收到订阅的 Topic 消息时调用。
     * <p>
     * 这是核心方法：当 IoTDA 平台下发命令时，此方法被触发。
     * 处理流程：
     * 1. 解析 JSON 消息，提取命令名称和参数
     * 2. 根据命令名称执行对应的设备操作
     * 3. 构造命令响应，发布到命令响应 Topic
     *
     * @param topic   收到的消息所属 Topic，此处是命令下发 Topic
     * @param message MQTT 消息对象，包含消息内容和 QoS
     */
    @Override
    public void messageArrived(String topic, MqttMessage message) {
        // 将消息字节数组转换为 UTF-8 字符串
        String payload = new String(message.getPayload(), StandardCharsets.UTF_8);
        log.info("📥 收到命令下发: topic={}, payload={}", topic, payload);

        try {
            // 步骤1：解析 JSON 消息
            JsonNode commandJson = objectMapper.readTree(payload);

            // 步骤2：提取命令信息
            // IoTDA 命令下发 JSON 格式：
            // {
            //   "command_name": "set_temperature",
            //   "service_id": "device_control",
            //   "paras": { "temperature": 26 },
            //   "request_id": "abc-123"
            // }
            String commandName = commandJson.get("command_name").asText();
            String serviceId = commandJson.get("service_id").asText();
            JsonNode paras = commandJson.get("paras");
            String requestId = commandJson.get("request_id").asText();

            log.info("🔧 执行命令: command={}, serviceId={}, params={}",
                    commandName, serviceId, paras);

            // 步骤3：根据命令名称执行操作
            Map<String, Object> result = executeCommand(commandName, paras);

            // 步骤4：发送命令响应
            // 响应 Topic 格式：$oc/devices/{device_id}/sys/commands/response/request_id={request_id}
            sendCommandResponse(requestId, commandName, result);

        } catch (JsonProcessingException e) {
            log.error("命令消息解析失败: payload={}", payload, e);
        } catch (Exception e) {
            log.error("命令执行异常: command={}", payload, e);
        }
    }

    /**
     * 当消息已成功投递到 Broker 时调用。
     * <p>
     * 用于确认消息（如命令响应）是否被 IoTDA 成功接收。
     *
     * @param token 投递操作令牌
     */
    @Override
    public void deliveryComplete(IMqttToken token) {
        // 消息投递完成，可选日志记录
        log.debug("消息投递完成: messageId={}", token.getMessageId());
    }

    /**
     * 当与 Broker 的认证/连接成功完成时调用。
     *
     * @param reconnected 是否为重连（true=重连，false=首次连接）
     */
    @Override
    public void connectComplete(boolean reconnected, String serverURI) {
        if (reconnected) {
            log.info("MQTT 已重新连接到 IoTDA: {}", serverURI);
            // 重连后需要重新订阅命令下发 Topic
            try {
                String commandTopic = "$oc/devices/" + deviceId + "/sys/commands/down";
                mqttClient.subscribe(commandTopic, 1);
                log.info("重连后已重新订阅命令 Topic: {}", commandTopic);
            } catch (MqttException e) {
                log.error("重连后订阅命令 Topic 失败", e);
            }
        } else {
            log.info("MQTT 首次连接成功: {}", serverURI);
        }
    }

    /**
     * 认证相关属性变更时调用（MQTT v5 特性）。
     */
    @Override
    public void authPacketArrived(int reasonCode, MqttProperties properties) {
        log.debug("MQTT 认证包到达: reasonCode={}", reasonCode);
    }

    // ======================== 命令执行逻辑 ========================

    /**
     * 根据命令名称执行对应的设备操作。
     * <p>
     * 当前支持的命令：
     * - set_temperature：设置空调温度，返回设置结果
     * - set_volume：设置设备音量，返回设置结果
     * - reboot：重启设备，模拟重启过程
     * - query_status：查询设备当前状态，返回状态快照
     *
     * @param commandName 命令名称
     * @param paras       命令参数，JSON 节点
     * @return 执行结果，包含响应码和消息
     */
    private Map<String, Object> executeCommand(String commandName, JsonNode paras) {
        // 准备返回结果
        Map<String, Object> result = new HashMap<>();
        result.put("result_code", 0);  // 0 表示成功

        // 根据命令名称分发到不同的处理逻辑
        switch (commandName) {
            case "set_temperature":
                // 设置空调温度：从参数中提取目标温度值
                int targetTemp = paras.get("temperature").asInt();
                log.info("🌡️ 设置空调温度为 {}°C", targetTemp);
                result.put("message", "温度已设置为 " + targetTemp + "°C");
                result.put("current_temperature", targetTemp);
                break;

            case "set_volume":
                // 设置设备音量：从参数中提取音量值（0~100）
                int volume = paras.get("volume").asInt();
                log.info("🔊 设置设备音量为 {}%", volume);
                result.put("message", "音量已设置为 " + volume + "%");
                result.put("current_volume", volume);
                break;

            case "reboot":
                // 重启设备：模拟重启过程
                log.info("🔄 正在重启设备...");
                result.put("message", "设备将在 5 秒后重启");
                result.put("reboot_delay", 5);
                // 在实际设备中，此处会触发设备重启逻辑
                break;

            case "query_status":
                // 查询设备状态：返回当前状态快照
                log.info("📊 查询设备状态");
                result.put("message", "设备状态查询成功");
                result.put("status", "online");
                result.put("uptime_hours", 168);
                break;

            default:
                // 未知命令：返回错误
                log.warn("⚠️ 未知命令: {}", commandName);
                result.put("result_code", -1);
                result.put("message", "不支持的命令: " + commandName);
                break;
        }

        return result;
    }

    // ======================== 命令响应发送 ========================

    /**
     * 发送命令执行响应到 IoTDA 平台。
     * <p>
     * 命令响应 Topic 格式：
     * $oc/devices/{device_id}/sys/commands/response/request_id={request_id}
     * <p>
     * 响应 JSON 格式：
     * {
     *   "result_code": 0,
     *   "responses": [
     *     { "result_code": 0, "message": "温度已设置为 26°C" }
     *   ]
     * }
     *
     * @param requestId   命令请求 ID，用于匹配响应与请求
     * @param commandName 命令名称，仅用于日志记录
     * @param result      命令执行结果
     */
    private void sendCommandResponse(String requestId, String commandName,
                                     Map<String, Object> result) {
        try {
            // 步骤1：构造响应 JSON
            // IoTDA 要求的命令响应格式
            Map<String, Object> responseBody = new HashMap<>();
            // 响应码：0 表示成功，非 0 表示失败
            responseBody.put("result_code", result.getOrDefault("result_code", 0));
            // 响应数据数组，包含具体的执行结果
            responseBody.put("responses", new Map[]{result});

            // 步骤2：序列化为 JSON 字符串
            String jsonPayload = objectMapper.writeValueAsString(responseBody);

            // 步骤3：构造响应 Topic
            String responseTopic = "$oc/devices/" + deviceId
                    + "/sys/commands/response/request_id=" + requestId;

            // 步骤4：构建 MQTT 消息并发布
            MqttMessage responseMessage = new MqttMessage();
            responseMessage.setPayload(jsonPayload.getBytes(StandardCharsets.UTF_8));
            responseMessage.setQos(1);
            responseMessage.setRetained(false);

            // 发布响应消息
            mqttClient.publish(responseTopic, responseMessage);
            log.info("📤 命令响应已发送: command={}, requestId={}, result={}",
                    commandName, requestId, jsonPayload);

        } catch (JsonProcessingException e) {
            log.error("命令响应序列化失败", e);
        } catch (MqttException e) {
            log.error("命令响应发布失败", e);
        }
    }
}
```

### 3.8 DeviceSimulator.java - 设备模拟器

```java
package com.zznursing.iotda.runner;

import com.zznursing.iotda.service.DeviceDataPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 设备模拟器。
 * <p>
 * 实现 CommandLineRunner 接口，在 Spring Boot 应用启动后自动运行。
 * 模拟养老院智能设备的行为，定时向 IoTDA 上报各种数据。
 * <p>
 * 模拟的数据类型：
 * 1. 位置信息（每 10 秒上报一次）
 * 2. 健康数据（每 30 秒上报一次）
 * 3. 设备状态（每 60 秒上报一次）
 * 4. 报警事件（随机触发，概率约 5%）
 */
@Slf4j
@Component
public class DeviceSimulator implements CommandLineRunner {

    /**
     * 设备数据发布服务，用于上报各类数据到 IoTDA
     */
    private final DeviceDataPublisher dataPublisher;

    /**
     * 数据上报周期（秒），从配置文件读取
     */
    @Value("${iotda.report.interval-seconds}")
    private int reportInterval;

    /**
     * 随机数生成器，用于模拟数据变化和随机事件
     */
    private final Random random = new Random();

    /**
     * 定时任务线程池，用于周期性地执行数据上报任务
     * 使用虚拟线程（Java 21 Virtual Thread）提高并发性能
     */
    private final ScheduledExecutorService scheduler =
            Executors.newScheduledThreadPool(4, Thread.ofVirtual().factory());

    /**
     * 运行轮次计数器，用于记录模拟器运行了多少轮
     */
    private int round = 0;

    /**
     * 构造函数。
     *
     * @param dataPublisher 设备数据发布服务
     */
    public DeviceSimulator(DeviceDataPublisher dataPublisher) {
        this.dataPublisher = dataPublisher;
    }

    /**
     * Spring Boot 启动后自动执行的方法。
     * <p>
     * 在此方法中启动定时任务，模拟设备的各种数据上报行为。
     *
     * @param args 命令行参数
     */
    @Override
    public void run(String... args) {
        log.info("============================================");
        log.info("  🏥 zznursing 设备模拟器已启动");
        log.info("  设备ID: {}", System.getProperty("iotda.device-id", "unknown"));
        log.info("  上报间隔: {} 秒", reportInterval);
        log.info("============================================");

        // 启动定时上报任务：每 reportInterval 秒执行一次
        scheduler.scheduleAtFixedRate(
                this::doReport,   // 要执行的任务
                0,                 // 初始延迟（立即执行第一次）
                reportInterval,    // 执行周期
                TimeUnit.SECONDS   // 时间单位
        );

        log.info("定时上报任务已启动，周期: {} 秒", reportInterval);
    }

    /**
     * 执行一轮数据上报。
     * <p>
     * 每次执行时，上报以下数据：
     * 1. 位置信息（每次上报，用于定位追踪）
     * 2. 健康数据（每轮都上报，但内容随机变化）
     * 3. 设备状态（每轮都上报，模拟设备运行状态变化）
     * 4. 随机报警（概率触发，模拟真实场景中的异常事件）
     */
    private void doReport() {
        round++;
        log.info("--- 第 {} 轮数据上报 ---", round);

        try {
            // 1. 上报位置信息
            // 模拟老人的实时位置，每次上报都有微小变化
            dataPublisher.reportLocation();

            // 2. 上报健康数据
            // 模拟心率、血氧、体温等生理指标
            dataPublisher.reportHealthData();

            // 3. 上报设备状态
            // 模拟电量、信号强度等设备运行状态
            dataPublisher.reportDeviceStatus();

            // 4. 随机触发报警事件（概率约 5%）
            // 模拟真实场景中的跌倒告警、紧急呼叫等异常情况
            if (random.nextInt(100) < 5) {
                // 随机选择一种告警类型
                String[] alertTypes = {"FALL", "EMERGENCY", "LOW_BATTERY"};
                String[] alertMessages = {
                        "检测到老人跌倒，请立即查看！",
                        "老人按下紧急呼叫按钮！",
                        "设备电量不足，请及时充电！"
                };
                int index = random.nextInt(alertTypes.length);
                dataPublisher.reportAlert(alertTypes[index], alertMessages[index]);
            }

        } catch (Exception e) {
            // 捕获所有异常，避免定时任务被中断
            log.error("数据上报异常", e);
        }
    }
}
```

### 3.9 IotdaApplication.java - 启动类

```java
package com.zznursing.iotda;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * zznursing 智慧养老平台 - IoTDA 设备接入示例启动类。
 * <p>
 * 启动流程：
 * 1. Spring Boot 加载 application.yml 配置文件
 * 2. IotdaConfig 初始化 MQTT 客户端并连接到 IoTDA
 * 3. DeviceSimulator 自动启动，开始定时上报数据
 * 4. CommandCallback 注册命令监听，等待云端下发指令
 * <p>
 * 启动命令：
 * mvn spring-boot:run
 * <p>
 * 或打包后运行：
 * mvn clean package -DskipTests
 * java -jar target/iotda-hello-world-1.0.0.jar
 */
@SpringBootApplication
public class IotdaApplication {

    /**
     * 应用入口。
     *
     * @param args 命令行参数，可通过 --iotda.device-id=xxx 覆盖配置
     */
    public static void main(String[] args) {
        // 启动 Spring Boot 应用
        SpringApplication.run(IotdaApplication.class, args);
    }
}
```

### 3.10 IotdaApplicationTest.java - 单元测试

```java
package com.zznursing.iotda;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zznursing.iotda.model.DeviceProperty;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * IoTDA 设备接入示例的单元测试类。
 * <p>
 * 测试覆盖：
 * 1. 设备属性模型的 JSON 序列化/反序列化
 * 2. 设备属性上报 JSON 格式验证
 * 3. IoTDA 密码生成算法验证
 * <p>
 * 注意：MQTT 连接测试需要真实的 IoTDA 平台连接信息，
 * 此处仅测试数据模型和工具方法，不依赖外部服务。
 */
@SpringBootTest
class IotdaApplicationTest {

    /**
     * Jackson ObjectMapper，用于 JSON 序列化测试
     */
    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 测试1：设备属性模型序列化。
     * <p>
     * 验证 DeviceProperty record 能否正确序列化为 JSON，
     * 并且字段名符合 IoTDA 的规范（service_id、properties、event_time）。
     */
    @Test
    @DisplayName("设备属性模型序列化测试 - 验证 JSON 格式符合 IoTDA 规范")
    void testDevicePropertySerialization() throws JsonProcessingException {
        // 准备测试数据：模拟健康数据上报
        Map<String, Object> properties = new HashMap<>();
        properties.put("heart_rate", 72);       // 心率 72 次/分钟
        properties.put("blood_oxygen", 98);     // 血氧饱和度 98%
        properties.put("temperature", 36.5);    // 体温 36.5°C

        // 创建 DeviceProperty record 实例
        DeviceProperty property = new DeviceProperty(
                "health",           // 服务 ID：health
                properties,         // 属性键值对
                "2026-08-22T10:30:00Z"  // 事件时间
        );

        // 构造完整的 IoTDA 属性上报请求体
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("services", new DeviceProperty[]{property});

        // 序列化为 JSON 字符串
        String json = objectMapper.writeValueAsString(requestBody);

        // 验证 JSON 包含期望的字段
        assertNotNull(json, "序列化结果不应为 null");
        assertTrue(json.contains("health"), "JSON 应包含服务 ID 'health'");
        assertTrue(json.contains("heart_rate"), "JSON 应包含字段 'heart_rate'");
        assertTrue(json.contains("blood_oxygen"), "JSON 应包含字段 'blood_oxygen'");
        assertTrue(json.contains("2026-08-22T10:30:00Z"), "JSON 应包含事件时间");

        System.out.println("序列化结果: " + json);
    }

    /**
     * 测试2：设备属性模型反序列化。
     * <p>
     * 验证从 IoTDA 返回的 JSON 能否正确反序列化为 DeviceProperty 对象。
     * 模拟 IoTDA 平台返回的属性数据格式。
     */
    @Test
    @DisplayName("设备属性模型反序列化测试 - 验证 JSON 能正确解析为 Java 对象")
    void testDevicePropertyDeserialization() throws JsonProcessingException {
        // 准备测试数据：模拟 IoTDA 返回的属性 JSON
        String json = """
                {
                    "service_id": "location",
                    "properties": {
                        "latitude": 39.9042,
                        "longitude": 116.4074,
                        "accuracy": 10
                    },
                    "event_time": "2026-08-22T10:30:00Z"
                }
                """;

        // 反序列化为 DeviceProperty 对象
        DeviceProperty property = objectMapper.readValue(json, DeviceProperty.class);

        // 验证字段值
        assertNotNull(property, "反序列化结果不应为 null");
        assertEquals("location", property.serviceId(), "服务 ID 应为 'location'");
        assertEquals(39.9042, property.properties().get("latitude"),
                "纬度应为 39.9042");
        assertEquals(116.4074, property.properties().get("longitude"),
                "经度应为 116.4074");
        assertEquals("2026-08-22T10:30:00Z", property.eventTime(),
                "事件时间应匹配");
    }

    /**
     * 测试3：IoTDA 密码生成算法验证。
     * <p>
     * 验证 HMAC-SHA256 签名算法的正确性。
     * 使用已知的设备 ID 和密钥，验证生成的密码是否符合预期格式。
     * 这个算法与 IotdaConfig 中使用的算法一致。
     */
    @Test
    @DisplayName("IoTDA 密码生成算法测试 - 验证 HMAC-SHA256 签名正确性")
    void testPasswordGeneration() throws Exception {
        // 模拟测试数据
        String deviceId = "test_device_001";
        String deviceSecret = "test_secret_key_123";

        // 使用 HMAC-SHA256 算法生成密码
        javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
        javax.crypto.spec.SecretKeySpec keySpec = new javax.crypto.spec.SecretKeySpec(
                deviceSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                "HmacSHA256"
        );
        mac.init(keySpec);
        byte[] hmacBytes = mac.doFinal(deviceId.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String password = java.util.Base64.getEncoder().encodeToString(hmacBytes);

        // 验证密码不为空且格式正确
        assertNotNull(password, "生成的密码不应为 null");
        assertFalse(password.isEmpty(), "生成的密码不应为空字符串");
        assertTrue(password.length() > 0, "生成的密码长度应大于 0");

        // 验证确定性：同一输入应产生相同输出
        mac.init(keySpec);
        byte[] hmacBytes2 = mac.doFinal(deviceId.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String password2 = java.util.Base64.getEncoder().encodeToString(hmacBytes2);
        assertEquals(password, password2, "同一输入应产生相同的密码");

        // 不同输入应产生不同输出
        String differentSecret = "different_secret";
        javax.crypto.spec.SecretKeySpec differentKeySpec = new javax.crypto.spec.SecretKeySpec(
                differentSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                "HmacSHA256"
        );
        mac.init(differentKeySpec);
        byte[] hmacBytes3 = mac.doFinal(deviceId.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String password3 = java.util.Base64.getEncoder().encodeToString(hmacBytes3);
        assertNotEquals(password, password3, "不同密钥应产生不同的密码");

        System.out.println("生成的密码: " + password);
    }
}
```

---

## 四、运行验证

### 4.1 前置准备

在运行代码之前，需要先在华为云 IoTDA 控制台完成以下配置：

**步骤1：创建 IoTDA 实例**

登录华为云控制台，搜索"IoTDA 设备接入"，进入服务页面。如果还没有实例，点击"创建实例"，选择区域（建议选择离你最近的区域，如"华东-上海一"）。免费版即可满足本教程的测试需求。

**步骤2：创建产品**

在 IoTDA 控制台左侧导航栏，进入"产品"菜单，点击"创建产品"：

- 产品名称：`zznursing_hello_world`
- 协议类型：`MQTT`
- 数据格式：`JSON`
- 模型定义：可暂不定义，后续通过代码上报数据

**步骤3：注册设备**

在产品详情页，进入"设备"子菜单，点击"注册设备"：

- 设备标识码：`hello_device_001`
- 设备名称：`HelloWorld设备`
- 认证类型：`密钥认证`
- 密钥：`自定义`，输入一个测试密钥，如 `hello_test_key_2026`

注册成功后，记录下 **设备 ID**（在设备详情页可以查看）和刚才设置的 **密钥**。

**步骤4：获取接入地址**

在 IoTDA 控制台的"总览"页面，找到"平台接入信息"，记录 **MQTT 接入地址**。格式类似：

```
xxxxxxxx.iotda.cn-north-4.myhuaweicloud.com
```

### 4.2 配置并运行

打开 `application.yml`，将占位符替换为实际值：

```yaml
iotda:
  host: "tcp://xxxxxxxx.iotda.cn-north-4.myhuaweicloud.com:1883"
  device-id: "xxxxxxxxxx_hello_device_001"
  device-secret: "hello_test_key_2026"
```

然后在项目根目录执行：

```bash
# 编译并运行
mvn spring-boot:run
```

### 4.3 验证运行结果

**在控制台观察日志输出：**

```
--- 第 1 轮数据上报 ---
📤 [1] 已上报 location 数据: {"services":[{"service_id":"location","properties":{"latitude":39.9042,"longitude":116.4074,"accuracy":30},"event_time":"2026-08-22T10:30:00Z"}]}
📤 [2] 已上报 health 数据: {"services":[{"service_id":"health","properties":{"heart_rate":72,"blood_oxygen":98,"temperature":36.5},"event_time":"2026-08-22T10:30:00Z"}]}
📤 [3] 已上报 device_status 数据: ...
```

**在 IoTDA 控制台查看设备消息：**

进入设备详情页，点击"设备影子"标签，可以看到设备上报的最新属性数据。点击"消息跟踪"标签，可以查看设备与平台之间的实时消息流动。

**测试命令下发：**

在 IoTDA 控制台的设备详情页，点击"命令下发"标签，选择"同步命令下发"，输入：

```json
{
  "command_name": "set_temperature",
  "service_id": "device_control",
  "paras": {
    "temperature": 26
  }
}
```

点击"下发"，观察设备端日志，应该能看到命令被接收并响应的输出：

```
📥 收到命令下发: topic=$oc/devices/xxx/sys/commands/down, payload={"command_name":"set_temperature",...}
🔧 执行命令: command=set_temperature, params={"temperature":26}
🌡️ 设置空调温度为 26°C
📤 命令响应已发送: command=set_temperature, requestId=xxx, result={"result_code":0,"message":"温度已设置为 26°C"}
```

---

## 五、与真实 zznursing 项目对照

本文的示例代码是一个简化版的"Hello World"，但麻雀虽小五脏俱全。下面从五个维度，对照真实 zznursing 项目的实践，帮助你理解从入门到生产落地的差距。

### 5.1 设备管理平台

| 维度 | 本文示例 | 真实 zznursing |
|------|---------|---------------|
| 设备注册 | 手动在控制台注册 | 通过 REST API 批量注册，设备出厂时预置凭证 |
| 设备管理 | 无 | 自建设备管理后台，支持设备分组、标签、OTA 升级管理 |
| 设备认证 | 密钥认证 | 密钥认证 + X.509 证书（高安全场景）+ 动态注册 |
| 设备拓扑 | 单设备独立 | 支持网关+子设备模式（如 ZigBee 网关管理多个传感器） |

在真实项目中，设备不是一个个手动注册的，而是通过 IoTDA 的 REST API 批量注册。设备出厂时，固件中预置了设备 ID 和密钥，用户扫码即可自动完成注册和绑定。

### 5.2 数据清洗规则引擎

| 维度 | 本文示例 | 真实 zznursing |
|------|---------|---------------|
| 数据上报 | 原始数据直接上报 | 设备端做初步过滤（去重、阈值检查），减少无效数据 |
| 规则引擎 | 未使用 | 使用 IoTDA 规则引擎，将数据流转到 Kafka 做实时分析 |
| 数据清洗 | 无 | 使用 Flink/Spark Streaming 清洗异常数据，填补缺失值 |
| 数据存储 | 数据仅到 IoTDA | IoTDA → 规则引擎 → Kafka → Flink → ClickHouse/时序数据库 |

示例中数据直接上报到 IoTDA，但在生产环境中，设备端会先做简单的数据过滤（如连续两次心率相同则丢弃），IoTDA 规则引擎将数据转发到 Kafka，再由 Flink 做实时清洗和聚合，最终存入 ClickHouse 用于大屏展示和趋势分析。

### 5.3 设备 OTA 升级

| 维度 | 本文示例 | 真实 zznursing |
|------|---------|---------------|
| OTA 升级 | 未实现 | 集成 IoTDA OTA 升级服务 |
| 固件管理 | 无 | 固件版本管理、灰度发布、升级策略 |
| 升级可靠性 | 无 | 断点续传、升级失败自动回滚、升级进度跟踪 |

OTA 升级在 IoT 系统中至关重要。真实项目中，设备上线后先检查固件版本，如果低于最新版本则在后台静默下载升级包，升级成功后上报新版本号。如果升级失败，自动回滚到上一个版本，避免设备变砖。

### 5.4 批量设备管理

| 维度 | 本文示例 | 真实 zznursing |
|------|---------|---------------|
| 设备数量 | 1 台测试设备 | 数十万台设备 |
| 批量操作 | 无 | 设备分组管理、批量配置下发、批量固件升级 |
| 设备监控 | 无 | 设备在线率、消息量、异常率监控大盘 |
| 告警通知 | 无 | 设备离线告警、数据异常告警，通过短信/企微通知 |

当设备数量达到一定规模后，批量管理能力成为刚需。真实项目使用 IoTDA 的设备分组功能，按养老院、楼层、设备类型等维度分组，运维人员可以一键对某个分组的设备下发配置或执行升级。

### 5.5 设备安全

| 维度 | 本文示例 | 真实 zznursing |
|------|---------|---------------|
| 通信加密 | 无（TCP 明文） | TLS 1.3 加密传输 |
| 设备认证 | 简单密钥 | 密钥 + 设备证书双向认证 |
| 数据隐私 | 无 | 敏感数据（健康数据）端到端加密 |
| 访问控制 | 无 | IoTDA 策略 + IAM 权限控制 |

安全是 IoT 系统的生命线。真实生产环境必须使用 SSL/TLS 加密（`ssl://` 协议），重要数据需要在设备端加密后再上报。访问控制方面，需要为不同角色（管理员、护工、家属）设置不同的设备访问权限，避免数据泄露。

---

## 六、面试题3道

### 面试题1：华为云 IoTDA 与自建 MQTT Broker（如 EMQX）相比，优缺点分别是什么？什么场景下应该选择自建？

**参考答案：**

IoTDA 的优势在于**免运维**（不需要关心集群部署、高可用、监控告警）、**开箱即用的设备管理能力**（设备注册、认证、影子、OTA 升级、规则引擎）和**华为云生态集成**（与 FunctionGraph、OBS、Kafka 等无缝对接）。适合中小规模设备量、团队缺乏 IoT 基础设施经验、需要快速上线的场景。

自建 MQTT Broker 的优势在于**完全可控**（可深度定制协议、优化性能）、**长期成本更低**（大规模设备时，云平台费用可能超过自建运维成本）和**数据不外流**（数据完全在自己的基础设施中）。适合设备量极大（百万级以上）、有专职 IoT 基础设施团队、对数据主权有严格要求的场景。

**一句话总结：** 快和省心选 IoTDA，控和规模选自建。

### 面试题2：什么是设备影子（Device Shadow）？在智慧养老场景中，设备影子解决了什么问题？

**参考答案：**

设备影子是云端持久化的设备状态缓存，是一个 JSON 文档，包含设备的最新报告状态（reported）和应用端期望状态（desired）。

在智慧养老场景中，设备影子解决了三个关键问题：

1. **离线状态同步：** 老人房间的空调离线时，护工通过管理后台设置目标温度 26°C，这个温度被写入设备影子的 desired 字段。空调恢复连接后，IoTDA 自动将 desired 值与设备当前状态对比，发现不一致则下发指令给设备，实现"离线时配置，上线后同步"。

2. **状态缓存查询：** 家属 APP 需要查看老人手环的实时心率和位置，不需要直接连接设备，而是从设备影子中读取最新值。即使设备处于休眠状态（低功耗模式），影子也能提供最近一次上报的数据。

3. **应用-设备解耦：** 应用端不需要关心设备是否在线、使用什么协议，只需要读写设备影子。设备端也不需要关心有哪些应用在读取它的数据，只需上报数据到影子即可。

### 面试题3：MQTT 的 QoS 等级有哪几个？在 IoTDA 设备接入中，数据上报和命令下发分别应该使用什么 QoS，为什么？

**参考答案：**

MQTT 有三个 QoS 等级：

- **QoS 0（至多一次）：** 消息发送后不确认，不重试，可能丢失。"即发即忘"。
- **QoS 1（至少一次）：** 消息发送后等待 PUBACK 确认，超时重发，保证收到但可能重复。
- **QoS 2（恰好一次）：** 通过四次握手保证消息不丢失不重复，开销最大。

在 IoTDA 设备接入中的推荐用法：

**数据上报建议使用 QoS 1。** 原因：设备上报的健康数据（心率、血氧、位置）允许少量重复，通过应用层去重即可。QoS 1 的开销适中，能保证数据不丢失。QoS 0 可能丢失重要的告警数据，QoS 2 的开销对电池供电设备不友好。

**命令下发建议使用 QoS 1。** 原因：控制指令（如设置空调温度、呼叫护工）必须送达设备，不能丢失。QoS 1 配合设备端的幂等处理（同一指令多次执行结果相同），既保证可靠性又避免过度开销。QoS 2 虽然更可靠，但握手延迟较大，不适合需要快速响应的控制场景。

---

## 总结

本文从零搭建了一个完整的 Java 21 + Spring Boot 3.3.5 项目，实现了设备通过华为云 IoTDA 上报数据、接收命令的完整闭环。通过这个"Hello World"示例，你应该已经掌握了：

1. IoTDA 的核心概念：设备注册、Topic 设计、设备影子、规则引擎
2. 设备接入的完整流程：注册产品/设备 → 生成认证密码 → MQTT 连接 → 属性上报 → 命令监听
3. 从入门到生产的差距：设备管理、数据清洗、OTA 升级、安全等生产级实践

在下一篇文章中，我们将深入 IoTDA 的规则引擎，实现设备数据的实时流转和清洗，敬请期待。

---

> **📚 系列文章导航**
>
> - 上一篇：[百度千帆大模型集成：AI 对话能力接入](02-baidu-qianfan-ai.md)
> - 下一篇：IoTDA 规则引擎：设备数据实时流转与清洗（敬请期待）
>
> **🔗 参考资源**
>
> - [华为云 IoTDA 产品文档](https://support.huaweicloud.com/iotda/)
> - [Eclipse Paho MQTT Client 文档](https://www.eclipse.org/paho/index.php?page=clients/java/index.php)
> - [MQTT 3.1.1 协议规范](https://mqtt.org/)