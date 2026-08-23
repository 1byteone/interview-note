# Spring Boot IoT 后端架构

> zznursing 项目基于 Spring Boot 3 构建 IoT 后端，核心职责：设备数据采集、消息驱动处理、业务服务编排。

---

## 一、IoT 后端架构设计

### 1.1 分层架构

```
┌──────────────────────────────────────────────────────────────┐
│                     Controller 层                              │
│  DeviceDataController  │  DeviceCommandController               │
│  AlertController       │  DeviceManageController                │
├──────────────────────────────────────────────────────────────┤
│                     Service 层                                 │
│  DeviceDataService      │  DeviceStateService                   │
│  AlertService           │  DeviceCommandService                 │
│  MqttMessageHandler     │  DataAggregationService               │
├──────────────────────────────────────────────────────────────┤
│                     Repository 层                              │
│  DeviceDataRepository   │  DeviceInfoRepository                 │
│  AlertRuleRepository    │  DeviceAlertLogRepository             │
├──────────────────────────────────────────────────────────────┤
│                     基础设施层                                  │
│  MQTT 客户端 (EMQX)  │  RocketMQ 生产者/消费者                  │
│  Redis 缓存           │  MySQL 持久化                          │
└──────────────────────────────────────────────────────────────┘
```

### 1.2 核心依赖

```xml
<!-- pom.xml 核心依赖 -->
<dependencies>
    <!-- Spring Boot 基础 -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>

    <!-- Spring Integration MQTT：用于连接 MQTT Broker -->
    <dependency>
        <groupId>org.springframework.integration</groupId>
        <artifactId>spring-integration-mqtt</artifactId>
    </dependency>

    <!-- RocketMQ 消息驱动 -->
    <dependency>
        <groupId>org.apache.rocketmq</groupId>
        <artifactId>rocketmq-spring-boot-starter</artifactId>
        <version>2.3.0</version>
    </dependency>

    <!-- 数据持久化 -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-jpa</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-redis</artifactId>
    </dependency>
    <dependency>
        <groupId>com.mysql</groupId>
        <artifactId>mysql-connector-j</artifactId>
    </dependency>

    <!-- 验证 -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-validation</artifactId>
    </dependency>
</dependencies>
```

### 1.3 配置文件

```yaml
# application.yml
server:
  port: 8081

spring:
  application:
    name: zznursing-device-service

  # 数据源配置
  datasource:
    url: jdbc:mysql://localhost:3306/zznursing?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai
    username: root
    password: ${DB_PASSWORD}
    driver-class-name: com.mysql.cj.jdbc.Driver
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      idle-timeout: 300000

  # Redis 配置
  data:
    redis:
      host: localhost
      port: 6379
      password: ${REDIS_PASSWORD}
      timeout: 3000ms
      lettuce:
        pool:
          max-active: 16
          max-idle: 8
          min-idle: 4

  # JPA 配置
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
    properties:
      hibernate:
        dialect: org.hibernate.dialect.MySQLDialect

# MQTT 配置
mqtt:
  broker:
    url: tcp://localhost:1883
    client-id: zznursing-device-service-${random.int}
    username: ${MQTT_USERNAME}
    password: ${MQTT_PASSWORD}
  topic:
    device-data: device/+/data        # 设备数据上报主题
    device-status: device/+/status    # 设备状态上报主题
    command-response: device/+/command/response  # 命令响应主题

# RocketMQ 配置
rocketmq:
  name-server: localhost:9876
  producer:
    group: zznursing-device-producer
  consumer:
    group: zznursing-device-consumer
```

---

## 二、设备数据采集

### 2.1 设备数据接收接口

```java
// DeviceDataController.java
// 设备数据接收控制器 —— 接收华为 IoTDA 平台转发的设备数据
package com.zznursing.iot.controller;

import com.zznursing.iot.common.Result;
import com.zznursing.iot.dto.DeviceDataReportRequest;
import com.zznursing.iot.service.DeviceDataService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 设备数据接收控制器
 * 接收 IoTDA 平台转发的设备上报数据，包含心率、血压、体温等健康指标
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/device/data")
@RequiredArgsConstructor
public class DeviceDataController {

    private final DeviceDataService deviceDataService;

    /**
     * 接收设备上报的健康监测数据
     *
     * @param request 设备数据上报请求体
     * @return 处理结果
     */
    @PostMapping("/report")
    public Result<Void> reportDeviceData(@Valid @RequestBody DeviceDataReportRequest request) {
        // 记录设备数据上报日志，包含设备ID和数据时间戳
        log.info("收到设备数据上报 - 设备ID: {}, 时间: {}, 数据类型: {}",
                request.getDeviceId(), request.getTimestamp(), request.getDataType());

        // 将设备数据异步发送到消息队列，实现削峰填谷
        deviceDataService.processDeviceData(request);

        // 快速返回响应，避免设备端或 IoTDA 平台超时重试
        return Result.success();
    }

    /**
     * 批量查询设备历史数据
     *
     * @param deviceId 设备ID
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @param page 页码
     * @param size 每页大小
     * @return 分页数据
     */
    @GetMapping("/history")
    public Result<?> getDeviceDataHistory(
            @RequestParam String deviceId,
            @RequestParam(required = false) Long startTime,
            @RequestParam(required = false) Long endTime,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(deviceDataService.getHistoryData(deviceId, startTime, endTime, page, size));
    }
}
```

### 2.2 设备数据 DTO

```java
// DeviceDataReportRequest.java
// 设备数据上报请求体 —— 支持多种数据类型的心率/血压/体温等
package com.zznursing.iot.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

/**
 * 设备数据上报请求体
 * 支持多种健康监测数据类型，通过 dataType 字段区分
 */
@Data
public class DeviceDataReportRequest {

    /** 设备唯一标识，由 IoTDA 平台分配 */
    @NotBlank(message = "设备ID不能为空")
    private String deviceId;

    /** 数据类型：heart_rate(心率) / blood_pressure(血压) / temperature(体温) / step(步数) */
    @NotBlank(message = "数据类型不能为空")
    private String dataType;

    /** 数据值，不同数据类型使用不同字段结构 */
    @NotNull(message = "数据值不能为空")
    private Object value;

    /** 数据采集时间戳（毫秒） */
    private Long timestamp;

    /** 扩展属性，如设备电量、信号强度等 */
    private Map<String, Object> properties;

    /** 数据签名，用于校验数据完整性 */
    private String signature;
}
```

### 2.3 数据接收处理器

```java
// DeviceDataService.java
// 设备数据服务 —— 核心业务逻辑：校验、转换、分发
package com.zznursing.iot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zznursing.iot.dto.DeviceDataReportRequest;
import com.zznursing.iot.entity.DeviceDataRecord;
import com.zznursing.iot.repository.DeviceDataRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

/**
 * 设备数据服务
 * 负责数据校验、转换、异步发送到消息队列、实时状态更新
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceDataService {

    private final RocketMQTemplate rocketMQTemplate;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final DeviceDataRepository deviceDataRepository;

    /** 设备数据上报到 RocketMQ 的主题 */
    private static final String ROCKETMQ_TOPIC_DEVICE_DATA = "zznursing-device-data";

    /**
     * 处理设备上报数据
     * 1. 更新设备实时状态到 Redis（TTL 策略）
     * 2. 发送数据到 RocketMQ 异步处理
     * 3. 直接返回，不阻塞设备上报
     */
    public void processDeviceData(DeviceDataReportRequest request) {
        try {
            // 步骤1：更新设备实时状态到 Redis
            // 使用 Hash 结构存储设备最新状态，key = device:status:{deviceId}
            String redisKey = "device:status:" + request.getDeviceId();
            String valueJson = objectMapper.writeValueAsString(request);
            stringRedisTemplate.opsForValue().set(redisKey, valueJson, 5, TimeUnit.MINUTES);

            // 步骤2：发送到 RocketMQ 异步处理（批量写入 MySQL）
            // 这样 Controller 可以快速返回，由消费者线程处理持久化
            rocketMQTemplate.convertAndSend(ROCKETMQ_TOPIC_DEVICE_DATA, request);

            log.debug("设备数据已发送到消息队列 - deviceId: {}, type: {}",
                    request.getDeviceId(), request.getDataType());

        } catch (Exception e) {
            // 数据发送失败不能丢失，记录错误日志并尝试补偿
            log.error("处理设备数据异常 - deviceId: {}, error: {}",
                    request.getDeviceId(), e.getMessage(), e);
            // 这里可以补充：写入本地失败队列或发送到死信主题
        }
    }

    /**
     * 获取设备最新状态
     */
    public String getDeviceLatestStatus(String deviceId) {
        return stringRedisTemplate.opsForValue().get("device:status:" + deviceId);
    }

    /**
     * 获取设备历史数据
     */
    public Object getHistoryData(String deviceId, Long startTime, Long endTime, int page, int size) {
        // 使用 JPA Specification 查询历史数据
        // 分页查询，避免一次加载过多数据
        return deviceDataRepository.findByDeviceIdAndTimeRange(
                deviceId,
                startTime != null ? Instant.ofEpochMilli(startTime) : null,
                endTime != null ? Instant.ofEpochMilli(endTime) : null,
                org.springframework.data.domain.PageRequest.of(page - 1, size)
        );
    }
}
```

---

## 三、MQTT 消息驱动

### 3.1 MQTT 配置类

```java
// MqttConfig.java
// MQTT 连接配置 —— 使用 Spring Integration MQTT 适配器
package com.zznursing.iot.config;

import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.core.MessageProducer;
import org.springframework.integration.mqtt.core.Mqttv5ClientManager;
import org.springframework.integration.mqtt.inbound.Mqttv5PahoMessageDrivenChannelAdapter;
import org.springframework.integration.mqtt.outbound.Mqttv5PahoMessageHandler;
import org.springframework.integration.mqtt.support.MqttHeaderMapper;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;

import java.util.Set;

/**
 * MQTT 配置类
 * 配置设备数据上报、命令下发、状态监听三个主题的通道
 */
@Slf4j
@Configuration
public class MqttConfig {

    @Value("${mqtt.broker.url}")
    private String brokerUrl;

    @Value("${mqtt.broker.client-id}")
    private String clientId;

    @Value("${mqtt.broker.username}")
    private String username;

    @Value("${mqtt.broker.password}")
    private String password;

    @Value("${mqtt.topic.device-data}")
    private String deviceDataTopic;

    /**
     * 创建 MQTT v5 客户端管理器
     * MQTT 5 相比 3.1.1 增加了会话过期、用户属性、原因码等特性
     */
    @Bean
    public Mqttv5ClientManager clientManager() {
        MqttConnectionOptions options = new MqttConnectionOptions();
        options.setServerURIs(new String[]{brokerUrl});
        options.setUserName(username);
        options.setPassword(password.toCharArray());
        // 设置遗嘱消息：设备离线时通知系统
        // 遗嘱消息主题为 device/{clientId}/status，内容为 offline
        options.setWill("device/" + clientId + "/status", "offline".getBytes(), 1, false);
        // 自动重连
        options.setAutomaticReconnect(true);
        // 会话过期时间（秒），0 表示会话随连接关闭而清除
        options.setSessionExpiryInterval(300L);

        return new Mqttv5ClientManager(clientId, options, "tcp");
    }

    /**
     * 设备数据入站通道
     * 所有 MQTT 消息通过此通道进入系统
     */
    @Bean
    public MessageChannel mqttInputChannel() {
        return new DirectChannel();
    }

    /**
     * 配置 MQTT 消息监听适配器
     * 订阅设备数据上报主题，接收所有设备的上报数据
     */
    @Bean
    public MessageProducer inboundAdapter() {
        Mqttv5PahoMessageDrivenChannelAdapter adapter =
                new Mqttv5PahoMessageDrivenChannelAdapter(
                        clientManager(), "zznursing-inbound", deviceDataTopic);

        // 设置消息头映射，保留 MQTT 关键属性
        MqttHeaderMapper headerMapper = new MqttHeaderMapper();
        headerMapper.setOutboundHeaderNames(Set.of("mqtt_topic", "mqtt_qos", "mqtt_id"));
        adapter.setHeaderMapper(headerMapper);

        // 设置 QoS 级别为 1（至少一次），确保数据不丢失
        adapter.setQos(1);
        // 设置输出通道
        adapter.setOutputChannel(mqttInputChannel());

        return adapter;
    }

    /**
     * 设备数据出站通道（用于下发命令到设备）
     */
    @Bean
    public MessageChannel mqttOutputChannel() {
        return new DirectChannel();
    }

    /**
     * MQTT 发送处理器，用于向设备下发命令
     */
    @Bean
    @ServiceActivator(inputChannel = "mqttOutputChannel")
    public MessageHandler mqttOutboundHandler() {
        Mqttv5PahoMessageHandler handler = new Mqttv5PahoMessageHandler(clientManager());
        handler.setAsync(true);
        handler.setDefaultTopic("device/command");
        // 命令下发使用 QoS 1，确保设备能收到
        handler.setDefaultQos(1);
        return handler;
    }
}
```

### 3.2 MQTT 消息处理器

```java
// MqttMessageHandler.java
// MQTT 消息处理器 —— 解析设备上报数据并路由到对应业务处理器
package com.zznursing.iot.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zznursing.iot.service.DeviceDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

/**
 * MQTT 消息处理器
 * 接收来自 MQTT 通道的所有设备消息，根据消息类型分发到不同业务逻辑
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MqttMessageHandler {

    private final ObjectMapper objectMapper;
    private final DeviceDataService deviceDataService;

    /**
     * 处理 MQTT 入站消息
     * 通过 @ServiceActivator 绑定到 mqttInputChannel 通道
     */
    @ServiceActivator(inputChannel = "mqttInputChannel")
    public void handleMessage(Message<?> message) {
        try {
            // 获取 MQTT 消息主题
            String topic = (String) message.getHeaders().get("mqtt_topic");
            // 获取消息载荷（JSON 格式）
            String payload = new String((byte[]) message.getPayload());

            log.debug("收到 MQTT 消息 - topic: {}, payload: {}", topic, payload);

            // 解析 JSON 消息
            JsonNode rootNode = objectMapper.readTree(payload);

            // 根据消息类型分发处理
            String messageType = rootNode.path("type").asText("unknown");

            switch (messageType) {
                case "heart_rate":
                    // 处理心率数据
                    handleHeartRateData(rootNode);
                    break;
                case "blood_pressure":
                    // 处理血压数据
                    handleBloodPressureData(rootNode);
                    break;
                case "temperature":
                    // 处理体温数据
                    handleTemperatureData(rootNode);
                    break;
                case "fall_detection":
                    // 处理跌倒检测（紧急告警，立即推送）
                    handleFallDetection(rootNode);
                    break;
                case "location":
                    // 处理位置上报
                    handleLocationData(rootNode);
                    break;
                default:
                    log.warn("未知消息类型: {}", messageType);
            }
        } catch (Exception e) {
            log.error("处理 MQTT 消息异常 - {}", e.getMessage(), e);
        }
    }

    /**
     * 处理心率数据
     * 包含当前心率值、采集时间戳
     */
    private void handleHeartRateData(JsonNode node) {
        String deviceId = node.path("deviceId").asText();
        int heartRate = node.path("heartRate").asInt();
        log.info("心率数据 - deviceId: {}, heartRate: {}", deviceId, heartRate);

        // 构建标准数据上报请求并进入统一处理流程
        // 业务逻辑：心率低于 40 或高于 120 触发告警
    }

    /**
     * 处理血压数据
     * 包含收缩压、舒张压
     */
    private void handleBloodPressureData(JsonNode node) {
        int systolic = node.path("systolic").asInt();    // 收缩压
        int diastolic = node.path("diastolic").asInt();  // 舒张压
        log.info("血压数据 - systolic: {}, diastolic: {}", systolic, diastolic);
    }

    /**
     * 处理跌倒检测（紧急事件）
     * 跌倒检测需要立即触发告警推送，通知护工和家属
     */
    private void handleFallDetection(JsonNode node) {
        String deviceId = node.path("deviceId").asText();
        double latitude = node.path("latitude").asDouble();
        double longitude = node.path("longitude").asDouble();
        log.warn("⚠️ 跌倒检测告警 - deviceId: {}, location: {},{}", deviceId, latitude, longitude);

        // 触发紧急告警流程：推送微信消息 + 短信通知护工
        // 告警级别：P0（最高优先级）
    }

    /**
     * 处理位置数据
     * 定期上报老人位置，用于电子围栏判断
     */
    private void handleLocationData(JsonNode node) {
        // 位置数据用于电子围栏和轨迹追踪
        log.debug("位置数据上报");
    }
}
```

### 3.3 RocketMQ 消息消费

```java
// DeviceDataConsumer.java
// RocketMQ 消费者 —— 消费设备数据消息，批量写入 MySQL
package com.zznursing.iot.consumer;

import com.zznursing.iot.entity.DeviceDataRecord;
import com.zznursing.iot.repository.DeviceDataRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

/**
 * 设备数据 RocketMQ 消费者
 * 异步消费设备数据，批量写入 MySQL，实现数据持久化
 * 通过消息队列实现削峰填谷，保护数据库不被高并发写入打垮
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = "zznursing-device-data",
        consumerGroup = "zznursing-device-data-group",
        // 最大重试次数，超过则进入死信队列
        maxReconsumeTimes = 3
)
public class DeviceDataConsumer implements RocketMQListener<String> {

    private final DeviceDataRepository deviceDataRepository;

    @Override
    public void onMessage(String message) {
        try {
            // 将 JSON 字符串解析为实体对象
            // 实际项目中可使用 Jackson 或 Fastjson 反序列化
            log.debug("消费设备数据消息: {}", message);

            // 批量写入逻辑：每积累 100 条或每 1 秒批量 flush 一次
            // 这里简化为单条写入，实际应使用批量插入优化
            // deviceDataRepository.save(record);

            log.info("设备数据持久化完成");
        } catch (Exception e) {
            log.error("消费设备数据失败 - {}", e.getMessage(), e);
            // 抛出异常触发 RocketMQ 重试机制
            throw new RuntimeException("设备数据消费失败", e);
        }
    }
}
```

---

## 四、面试题

### 问题 1：IoT 后端架构设计要点

**核心要点：**

1. **分层设计**：Controller 层只负责协议转换和数据校验，不包含业务逻辑；Service 层处理核心业务；消息队列层负责异步解耦
2. **消息驱动架构**：设备数据上报频率高（每 5 秒/设备），必须使用消息队列削峰填谷，避免直接写入数据库
3. **连接管理**：MQTT 长连接 + 心跳检测 + 自动重连机制，确保设备与服务器的连接稳定性
4. **设备状态管理**：使用 Redis 维护设备在线状态和最新数据，MySQL 存储历史数据，分层存储兼顾性能与成本

### 问题 2：高频率设备数据上报如何应对？

**应对策略：**

1. **消息队列削峰**：数据先进入 RocketMQ，消费端控制消费速率，避免数据库被打满
2. **批量写入**：每积累 100 条或间隔 1 秒，批量写入 MySQL，减少 IO 次数
3. **Redis 缓存最新状态**：最新一条数据写入 Redis，查询实时状态直接走缓存，不查数据库
4. **分级存储**：热数据在 Redis（1 小时），温数据在 MySQL 热表（7 天），冷数据归档到历史表
5. **限流熔断**：对接入层做限流，超出阈值时降级（丢弃非关键数据或记录日志）

### 问题 3：设备状态管理如何实现？

**实现方案：**

1. **在线状态**：MQTT 心跳 + 遗嘱消息，设备离线时自动发送遗嘱消息标记离线
2. **实时数据**：Redis Hash 结构 `device:status:{deviceId}`，TTL 5 分钟，每次上报更新
3. **设备影子**：参考 IoTDA 设备影子模式，在 Redis 中维护设备期望状态和报告状态的双缓存
4. **状态变更通知**：设备上线/离线/数据异常时，通过事件机制推送通知到相关模块