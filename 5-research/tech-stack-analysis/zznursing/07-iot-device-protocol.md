# IoT 设备协议 (MQTT)

> zznursing 项目使用 MQTT 作为核心物联网通信协议，通过 EMQX 消息中间件连接华为 IoTDA 平台与养老院智能设备，实现设备数据采集和远程命令下发。

---

## 一、MQTT 协议概述

### 1.1 协议选型

| 维度 | MQTT | CoAP | HTTP | 选择理由 |
|------|------|------|------|----------|
| **传输层** | TCP（可靠连接） | UDP（不可靠） | TCP | 养老设备数据不能丢，选 TCP |
| **消息模型** | 发布/订阅 | 请求/响应 | 请求/响应 | 设备上报场景天然适合发布/订阅 |
| **QoS 级别** | 0/1/2 三级 | 0/1 两级 | 无原生 QoS | 关键数据（跌倒检测）可用 QoS 1 |
| **功耗** | 中（维持长连接） | 低（无连接） | 高（每次请求建连） | 穿戴设备对功耗敏感，CoAP 更适合 |
| **实时性** | 高（长连接推送） | 中（轮询） | 低（轮询） | 实时监测需要设备主动推送 |
| **生态** | 极为成熟 | 一般 | 成熟但非 IoT 专用 | 社区支持、客户端库丰富度 |

**最终选型：** 以 MQTT 5.0 为主协议，低功耗定位设备使用 CoAP 补充。

### 1.2 主题设计

```
# 设备数据上报主题
device/{deviceId}/data          # 设备上报健康监测数据
device/{deviceId}/status        # 设备状态上报（在线/离线/低电量）
device/{deviceId}/event         # 设备事件上报（跌倒检测/按钮按下）

# 命令下发主题
device/{deviceId}/command       # 平台下发命令到设备

# 命令响应主题
device/{deviceId}/command/response  # 设备执行命令后返回结果

# 系统主题
system/device/register          # 设备注册通知
system/device/unregister        # 设备注销通知
system/ota/upgrade/{deviceId}   # OTA 升级通知
```

---

## 二、MQTT 配置与实现

### 2.1 MQTT 配置

```yaml
# application.yml —— MQTT 连接配置
mqtt:
  # EMQX Broker 连接地址
  broker:
    # 主节点地址
    url: tcp://emqx-cluster:1883
    # SSL 加密地址（生产环境使用）
    ssl-url: ssl://emqx-cluster:8883
    # 客户端 ID，添加随机后缀避免冲突
    client-id: zznursing-backend-${random.int}
    # 认证信息
    username: ${MQTT_USERNAME}
    password: ${MQTT_PASSWORD}

  # 连接参数
  connection:
    # 自动重连
    automatic-reconnect: true
    # 最大重连间隔（秒）
    max-reconnect-delay: 60
    # 心跳间隔（秒），保持连接活跃
    keep-alive-interval: 30
    # 会话过期时间（秒），0 = 连接断开即清除
    session-expiry-interval: 300

  # 主题订阅
  topic:
    # 设备数据上报：订阅所有设备的数据上报
    device-data: device/+/data
    # 设备状态上报：订阅所有设备的状态变更
    device-status: device/+/status
    # 设备事件上报：订阅所有设备的事件
    device-event: device/+/event
    # 命令响应
    command-response: device/+/command/response
    # 通配符说明：
    #   + 匹配单级：device/+/data 匹配 device/abc/data 但不匹配 device/abc/def/data
    #   # 匹配多级：device/# 匹配 device/abc/data 和 device/abc/def/data

  # QoS 配置
  qos:
    # 设备数据上报 QoS（至少一次，确保不丢失）
    device-data: 1
    # 设备状态上报 QoS（最多一次，允许丢失）
    device-status: 0
    # 命令下发 QoS（至少一次，确保设备收到）
    command: 1
    # 跌倒检测事件 QoS（恰好一次，严格去重）
    fall-event: 2
```

### 2.2 MQTT 设备影子

```java
// DeviceShadowService.java
// 设备影子服务 —— 管理设备期望状态和报告状态的双缓存
package com.zznursing.iot.mqtt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 设备影子服务
 * 参考 AWS IoT Device Shadow 模式，在 Redis 中维护设备影子
 * 影子包含两部分：
 *   - desired（期望状态）：平台希望设备达到的状态
 *   - reported（报告状态）：设备实际报告的状态
 * 应用层与影子交互，不直接操作设备，实现解耦
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceShadowService {

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    /** 设备影子 Redis Key 前缀 */
    private static final String SHADOW_PREFIX = "device:shadow:";

    /**
     * 获取设备完整影子文档
     * 影子文档结构：
     * {
     *   "deviceId": "xxx",
     *   "timestamp": 1234567890,
     *   "state": {
     *     "reported": { "heartRateInterval": 5, "battery": 85 },
     *     "desired":  { "heartRateInterval": 10 }
     *   },
     *   "metadata": {
     *     "reported": { "heartRateInterval": { "timestamp": 1234567890 } },
     *     "desired":  { "heartRateInterval": { "timestamp": 1234567891 } }
     *   }
     * }
     */
    public JsonNode getDeviceShadow(String deviceId) {
        String key = SHADOW_PREFIX + deviceId;
        String shadowJson = stringRedisTemplate.opsForValue().get(key);

        if (shadowJson == null) {
            // 影子不存在，返回空状态
            ObjectNode emptyShadow = objectMapper.createObjectNode();
            emptyShadow.put("deviceId", deviceId);
            emptyShadow.put("timestamp", System.currentTimeMillis());
            emptyShadow.set("state", objectMapper.createObjectNode());
            return emptyShadow;
        }

        try {
            return objectMapper.readTree(shadowJson);
        } catch (Exception e) {
            log.error("解析设备影子失败 - deviceId: {}", deviceId, e);
            return null;
        }
    }

    /**
     * 更新设备报告状态
     * 设备上报数据时，更新影子中的 reported 部分
     */
    public void updateReportedState(String deviceId, JsonNode reportedState) {
        String key = SHADOW_PREFIX + deviceId;

        try {
            // 获取当前影子
            JsonNode shadow = getDeviceShadow(deviceId);
            ObjectNode shadowNode = (ObjectNode) shadow;

            // 更新 state.reported
            ObjectNode stateNode = (ObjectNode) shadowNode.get("state");
            stateNode.set("reported", reportedState);

            // 更新 metadata.reported 的时间戳
            ObjectNode metadataNode = (ObjectNode) shadowNode.get("metadata");
            if (metadataNode == null) {
                metadataNode = objectMapper.createObjectNode();
                shadowNode.set("metadata", metadataNode);
            }
            ObjectNode reportedMetadata = (ObjectNode) metadataNode.get("reported");
            if (reportedMetadata == null) {
                reportedMetadata = objectMapper.createObjectNode();
                metadataNode.set("reported", reportedMetadata);
            }

            // 为每个属性添加时间戳
            reportedState.fieldNames().forEachRemaining(field -> {
                ObjectNode fieldMetadata = objectMapper.createObjectNode();
                fieldMetadata.put("timestamp", System.currentTimeMillis());
                reportedMetadata.set(field, fieldMetadata);
            });

            // 更新时间戳
            shadowNode.put("timestamp", System.currentTimeMillis());

            // 写入 Redis，TTL 1 小时
            stringRedisTemplate.opsForValue().set(
                    key, objectMapper.writeValueAsString(shadow), 1, TimeUnit.HOURS);

            log.debug("设备影子报告状态已更新 - deviceId: {}", deviceId);

        } catch (Exception e) {
            log.error("更新设备影子失败 - deviceId: {}", deviceId, e);
        }
    }

    /**
     * 更新设备期望状态
     * 平台下发给设备的配置，设备下次心跳时同步
     */
    public void updateDesiredState(String deviceId, JsonNode desiredState) {
        String key = SHADOW_PREFIX + deviceId;

        try {
            JsonNode shadow = getDeviceShadow(deviceId);
            ObjectNode shadowNode = (ObjectNode) shadow;

            // 更新 state.desired
            ObjectNode stateNode = (ObjectNode) shadowNode.get("state");
            stateNode.set("desired", desiredState);

            // 更新 metadata.desired 的时间戳
            ObjectNode metadataNode = (ObjectNode) shadowNode.get("metadata");
            if (metadataNode == null) {
                metadataNode = objectMapper.createObjectNode();
                shadowNode.set("metadata", metadataNode);
            }
            ObjectNode desiredMetadata = (ObjectNode) metadataNode.get("desired");
            if (desiredMetadata == null) {
                desiredMetadata = objectMapper.createObjectNode();
                metadataNode.set("desired", desiredMetadata);
            }

            desiredState.fieldNames().forEachRemaining(field -> {
                ObjectNode fieldMetadata = objectMapper.createObjectNode();
                fieldMetadata.put("timestamp", System.currentTimeMillis());
                desiredMetadata.set(field, fieldMetadata);
            });

            shadowNode.put("timestamp", System.currentTimeMillis());
            stringRedisTemplate.opsForValue().set(
                    key, objectMapper.writeValueAsString(shadow), 1, TimeUnit.HOURS);

            log.info("设备影子期望状态已更新 - deviceId: {}", deviceId);

        } catch (Exception e) {
            log.error("更新设备期望状态失败 - deviceId: {}", deviceId, e);
        }
    }
}
```

### 2.3 设备消息处理

```java
// MqttDeviceMessageHandler.java
// MQTT 设备消息处理器 —— 处理设备上报的各种消息类型
package com.zznursing.iot.mqtt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.MessagingException;
import org.springframework.stereotype.Component;

/**
 * MQTT 设备消息处理器
 * 处理所有来自 MQTT 通道的设备消息
 * 根据消息主题区分消息类型，路由到不同业务逻辑
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MqttDeviceMessageHandler implements MessageHandler {

    private final ObjectMapper objectMapper;
    private final DeviceShadowService deviceShadowService;

    /**
     * 处理 MQTT 消息
     * 根据主题路由到不同处理器
     */
    @Override
    public void handleMessage(Message<?> message) throws MessagingException {
        // 获取消息主题
        String topic = (String) message.getHeaders().get(MqttHeaders.RECEIVED_TOPIC);
        // 获取消息内容
        String payload = new String((byte[]) message.getPayload());

        try {
            // 根据主题分发处理
            if (topic == null) {
                log.warn("收到消息主题为空");
                return;
            }

            if (topic.endsWith("/data")) {
                // 设备数据上报
                handleDeviceData(topic, payload);
            } else if (topic.endsWith("/status")) {
                // 设备状态上报
                handleDeviceStatus(topic, payload);
            } else if (topic.endsWith("/event")) {
                // 设备事件上报
                handleDeviceEvent(topic, payload);
            } else if (topic.endsWith("/command/response")) {
                // 命令响应
                handleCommandResponse(topic, payload);
            } else {
                log.warn("未知消息主题: {}", topic);
            }

        } catch (Exception e) {
            log.error("处理 MQTT 消息异常 - topic: {}, payload: {}", topic, payload, e);
        }
    }

    /**
     * 处理设备数据上报
     * 更新设备影子中的报告状态，并发送到消息队列
     */
    private void handleDeviceData(String topic, String payload) throws Exception {
        // 从主题中提取设备ID：device/{deviceId}/data
        String deviceId = extractDeviceId(topic);

        JsonNode dataNode = objectMapper.readTree(payload);
        log.info("设备数据上报 - deviceId: {}, data: {}", deviceId, dataNode);

        // 更新设备影子的报告状态
        deviceShadowService.updateReportedState(deviceId, dataNode);

        // 后续处理：发送到 RocketMQ 进行持久化
        // 在 DeviceDataService 中完成
    }

    /**
     * 处理设备状态上报
     * 包含：在线/离线状态、电量、信号强度等
     */
    private void handleDeviceStatus(String topic, String payload) throws Exception {
        String deviceId = extractDeviceId(topic);
        JsonNode statusNode = objectMapper.readTree(payload);

        String status = statusNode.path("status").asText("unknown");
        int battery = statusNode.path("battery").asInt(100);
        int rssi = statusNode.path("rssi").asInt(0);

        log.info("设备状态上报 - deviceId: {}, status: {}, battery: {}%, rssi: {}",
                deviceId, status, battery, rssi);

        // 更新设备影子
        ObjectNode reportedState = objectMapper.createObjectNode();
        reportedState.put("status", status);
        reportedState.put("battery", battery);
        reportedState.put("rssi", rssi);
        reportedState.put("lastOnlineTime", System.currentTimeMillis());
        deviceShadowService.updateReportedState(deviceId, reportedState);
    }

    /**
     * 处理设备事件上报
     * 包含：跌倒检测、按钮按下、设备故障等
     */
    private void handleDeviceEvent(String topic, String payload) throws Exception {
        String deviceId = extractDeviceId(topic);
        JsonNode eventNode = objectMapper.readTree(payload);

        String eventType = eventNode.path("eventType").asText("unknown");
        log.warn("设备事件上报 - deviceId: {}, eventType: {}", deviceId, eventType);

        // 跌倒检测事件：立即触发告警
        if ("fall_detection".equals(eventType)) {
            handleFallEvent(deviceId, eventNode);
        }
    }

    /**
     * 处理跌倒检测事件（最高优先级）
     */
    private void handleFallEvent(String deviceId, JsonNode eventNode) {
        double latitude = eventNode.path("latitude").asDouble();
        double longitude = eventNode.path("longitude").asDouble();

        log.warn("⚠️ 跌倒检测 - deviceId: {}, 位置: {},{}", deviceId, latitude, longitude);

        // 1. 更新设备影子
        // 2. 发送告警到 RocketMQ
        // 3. 推送微信消息给家属
        // 4. 通知值班护工
    }

    /**
     * 处理命令响应
     * 设备执行完平台下发的命令后返回结果
     */
    private void handleCommandResponse(String topic, String payload) throws Exception {
        String deviceId = extractDeviceId(topic);
        JsonNode responseNode = objectMapper.readTree(payload);

        String commandName = responseNode.path("commandName").asText();
        int resultCode = responseNode.path("resultCode").asInt();
        String resultMessage = responseNode.path("resultMessage").asText();

        log.info("命令响应 - deviceId: {}, command: {}, result: {}",
                deviceId, commandName, resultCode);

        if (resultCode != 0) {
            log.warn("命令执行失败 - deviceId: {}, command: {}, message: {}",
                    deviceId, commandName, resultMessage);
        }
    }

    /**
     * 从主题中提取设备ID
     * 主题格式：device/{deviceId}/data → 提取 deviceId
     */
    private String extractDeviceId(String topic) {
        // 主题格式：device/xxx/data
        String[] parts = topic.split("/");
        if (parts.length >= 2) {
            return parts[1];
        }
        return "unknown";
    }
}
```

---

## 三、心跳检测机制

### 3.1 心跳检测服务

```java
// HeartbeatMonitorService.java
// 心跳检测服务 —— 监测设备在线状态，检测离线设备
package com.zznursing.iot.mqtt;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 心跳检测服务
 * 定期扫描 Redis 中的设备在线状态，检测长时间未上报的设备
 * 使用 MQTT 遗嘱消息 + 定时扫描双重保证
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HeartbeatMonitorService {

    private final StringRedisTemplate stringRedisTemplate;

    /** 设备在线状态 Redis Key 前缀 */
    private static final String KEY_DEVICE_ONLINE = "device:online:";

    /** 心跳超时时间（秒）：超过此时间未上报视为离线 */
    private static final long HEARTBEAT_TIMEOUT_SECONDS = 300;

    /**
     * 每分钟检查一次设备在线状态
     * 扫描所有在线设备，检查是否超过心跳超时时间
     */
    @Scheduled(fixedRate = 60000)
    public void checkDeviceHeartbeat() {
        // 扫描 Redis 中所有在线设备
        // 使用 SCAN 命令替代 KEYS，避免阻塞 Redis
        Set<String> keys = stringRedisTemplate.keys(KEY_DEVICE_ONLINE + "*");

        if (keys == null || keys.isEmpty()) {
            return;
        }

        int offlineCount = 0;
        for (String key : keys) {
            // 检查 key 的剩余 TTL
            Long ttl = stringRedisTemplate.getExpire(key, TimeUnit.SECONDS);

            // 如果 TTL 小于 0，key 已过期，设备已离线
            // 如果 TTL 接近 0，设备即将过期
            if (ttl != null && ttl < 0) {
                // 设备已离线，更新状态
                String deviceId = key.replace(KEY_DEVICE_ONLINE, "");
                log.warn("心跳超时，设备离线 - deviceId: {}", deviceId);
                offlineCount++;
            }
        }

        if (offlineCount > 0) {
            log.info("心跳检测完成，发现 {} 台设备离线", offlineCount);
        }
    }

    /**
     * 手动刷新设备心跳
     * 设备上报数据时同时刷新心跳
     */
    public void refreshHeartbeat(String deviceId) {
        String key = KEY_DEVICE_ONLINE + deviceId;
        stringRedisTemplate.opsForValue().set(key, "online", 10, TimeUnit.MINUTES);
    }

    /**
     * 获取当前在线设备数
     */
    public long getOnlineDeviceCount() {
        Set<String> keys = stringRedisTemplate.keys(KEY_DEVICE_ONLINE + "*");
        return keys != null ? keys.size() : 0;
    }

    /**
     * 获取当前离线设备数
     * 总设备数 - 在线设备数 = 离线设备数
     * 实际项目中从数据库查询总设备数
     */
    public long getOfflineDeviceCount(long totalDevices) {
        return totalDevices - getOnlineDeviceCount();
    }
}
```

### 3.2 MQTT 遗嘱消息

```java
// MqttWillMessageConfig.java
// MQTT 遗嘱消息配置 —— 设备异常离线时自动通知
package com.zznursing.iot.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.mqtt.core.Mqttv5ClientManager;
import org.springframework.integration.mqtt.support.MqttMessageConverter;

/**
 * MQTT 遗嘱消息配置
 * 当设备因网络异常、断电等原因非正常离线时，
 * MQTT Broker 会自动发布遗嘱消息到指定主题
 * 系统收到遗嘱消息后标记设备离线并触发告警
 */
@Slf4j
@Configuration
public class MqttWillMessageConfig {

    /**
     * 配置遗嘱消息处理器
     * 设备离线时，系统接收遗嘱消息并更新设备状态
     */
    @Bean
    public MqttMessageConverter mqttMessageConverter() {
        // 遗嘱消息格式：{"deviceId":"xxx","status":"offline","reason":"unexpected_disconnect"}
        // 收到遗嘱消息后：
        // 1. 更新 Redis 设备状态为 offline
        // 2. 记录设备离线日志
        // 3. 如果设备是重要监测设备，触发告警通知
        return new MqttMessageConverter();
    }
}
```

---

## 四、面试题

### 问题 1：MQTT vs CoAP 协议选型

**对比分析：**

| 维度 | MQTT | CoAP |
|------|------|------|
| **传输层** | TCP，可靠连接 | UDP，不可靠传输 |
| **消息模型** | 发布/订阅（一对多） | 请求/响应（一对一） |
| **连接开销** | 需要建立 TCP 连接，开销大 | 无连接，开销小 |
| **功耗** | 需要维持心跳，功耗较高 | 无心跳，功耗极低 |
| **适用场景** | 频繁上报、需要推送的设备 | 极低功耗、偶发上报的设备 |
| **养老场景** | 心率手环（5秒/次上报） | 定位胸牌（5分钟/次上报） |

**选型结论：** 心率手环等频繁上报设备使用 MQTT，低功耗定位胸牌使用 CoAP。

### 问题 2：MQTT QoS 级别选择策略

**QoS 级别说明：**

| 级别 | 名称 | 保证 | 养老场景应用 |
|------|------|------|-------------|
| **QoS 0** | 至多一次 | 消息可能丢失，不重试 | 设备状态上报（低电量告警重复上报也无妨） |
| **QoS 1** | 至少一次 | 消息至少到达一次，可能重复 | 心率数据上报、命令下发（可接受少量重复） |
| **QoS 2** | 恰好一次 | 消息不重不漏，性能开销大 | 跌倒检测事件（必须严格一次，不能重复触发告警） |

**策略：** 日常数据用 QoS 1，跌倒检测用 QoS 2，设备状态用 QoS 0。

### 问题 3：设备离线处理机制

**处理方案：**

1. **MQTT 遗嘱消息**：设备连接时设置遗嘱消息，异常离线时 Broker 自动发布
2. **心跳超时检测**：定时任务扫描 Redis 中的在线设备标记，超过 10 分钟未上报则标记离线
3. **Redis TTL 自动过期**：设备在线状态设置 TTL，设备停止上报后自动过期
4. **离线告警**：设备离线超过阈值（如 30 分钟）时触发告警，通知护工检查
5. **断线重连**：设备端实现自动重连机制，MQTT 客户端配置 `automatic-reconnect=true`
6. **数据缓存**：设备离线期间的数据缓存到本地，重连后批量上报