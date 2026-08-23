# 华为 IoTDA 设备接入

> zznursing 项目使用华为 IoTDA（IoT Device Access）平台作为设备接入层，统一管理养老院各类智能设备（心率手环、跌倒检测器、定位胸牌等），提供设备注册、数据采集、命令下发、OTA 升级等核心能力。

---

## 一、华为 IoTDA 平台架构

### 1.1 平台定位

```
┌──────────────────────────────────────────────────────────────────┐
│                        IoT 设备层                                 │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐        │
│  │ 心率手环  │  │ 跌倒检测器│  │ 定位胸牌  │  │ 血压计   │        │
│  │ MQTT上报  │  │ MQTT上报  │  │ MQTT上报  │  │ CoAP上报 │        │
│  └─────┬────┘  └─────┬────┘  └─────┬────┘  └─────┬────┘        │
└────────┼──────────────┼──────────────┼──────────────┼───────────┘
         │              │              │              │
         ▼              ▼              ▼              ▼
┌──────────────────────────────────────────────────────────────────┐
│                      华为 IoTDA 平台                              │
│                                                                  │
│  ┌─────────────┐  ┌─────────────┐  ┌───────────────────────────┐ │
│  │ 设备接入网关  │  │ 设备管理     │  │ 数据转发规则               │ │
│  │ MQTT/CoAP/   │  │ 注册/认证/   │  │ 设备数据 → 应用侧         │ │
│  │ HTTP/HTTPS   │  │ 影子/OTA    │  │ HTTP 回调 / 消息队列     │ │
│  └─────────────┘  └─────────────┘  └───────────────────────────┘ │
│                                                                  │
│  ┌─────────────┐  ┌─────────────┐  ┌───────────────────────────┐ │
│  │ 安全认证     │  │ 设备影子     │  │ 监控运维                  │ │
│  │ X.509证书/   │  │ 期望状态/    │  │ 在线调试/日志/告警        │ │
│  │ 设备密钥     │  │ 报告状态    │  │                          │ │
│  └─────────────┘  └─────────────┘  └───────────────────────────┘ │
└──────────────────────────┬───────────────────────────────────────┘
                           │ 数据转发 (HTTP回调 / 消息队列)
                           ▼
┌──────────────────────────────────────────────────────────────────┐
│                      Spring Boot 应用侧                           │
│  ┌────────────────┐  ┌────────────────┐  ┌──────────────────┐   │
│  │ 设备数据接收    │  │ 命令下发服务    │  │ 设备管理服务      │   │
│  │ 接收设备上报    │  │ 远程控制设备    │  │ 注册/状态/OTA    │   │
│  └────────────────┘  └────────────────┘  └──────────────────┘   │
└──────────────────────────────────────────────────────────────────┘
```

### 1.2 配置文件

```yaml
# application.yml
# 华为 IoTDA 平台配置
iotda:
  # IoTDA 平台接入地址
  host: https://iotda.cn-north-4.myhuaweicloud.com
  # 华为云项目 ID
  project-id: ${IOTDA_PROJECT_ID}
  # 华为云 IAM 认证
  iam:
    endpoint: https://iam.cn-north-4.myhuaweicloud.com
    domain-name: ${IAM_DOMAIN_NAME}
    username: ${IAM_USERNAME}
    password: ${IAM_PASSWORD}
  # 设备接入配置
  device:
    # 设备接入域名（MQTT 连接地址）
    mqtt-host: ${IOTDA_MQTT_HOST}
    # 设备接入端口（MQTTS 加密端口）
    mqtt-port: 8883
    # 默认设备指纹
    default-fingerprint: ${IOTDA_DEFAULT_FINGERPRINT}
  # 数据转发规则配置
  forwarding:
    # 数据转发 HTTP 回调地址（接收 IoTDA 推送的设备数据）
    callback-url: https://api.zznursing.com/api/v1/iotda/callback
    # 设备状态变化回调
    status-callback-url: https://api.zznursing.com/api/v1/iotda/status-callback
  # 命令下发配置
  command:
    # 命令响应超时时间（秒）
    response-timeout: 30
```

---

## 二、设备注册管理

### 2.1 设备注册服务

```java
// IoTDADeviceManager.java
// 华为 IoTDA 设备管理服务 —— 封装设备注册、查询、删除等操作
package com.zznursing.iotda.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * 华为 IoTDA 设备管理服务
 * 通过 IoTDA REST API 管理设备生命周期：注册、查询、更新、删除
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IoTDADeviceManager {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${iotda.host}")
    private String iotdaHost;

    @Value("${iotda.project-id}")
    private String projectId;

    /**
     * 注册新设备到 IoTDA 平台
     * 新设备入库时调用，在 IoTDA 平台创建设备记录
     *
     * @param deviceId 设备标识（全局唯一）
     * @param deviceName 设备名称
     * @param productId 产品 ID（对应产品模型）
     * @return 设备注册响应，包含设备ID和鉴权信息
     */
    public JsonNode registerDevice(String deviceId, String deviceName, String productId) {
        // 构建请求 URL
        String url = iotdaHost + "/v5/iot/" + projectId + "/devices";

        // 构建请求体
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("device_id", deviceId);         // 设备标识
        requestBody.put("device_name", deviceName);     // 设备名称
        requestBody.put("product_id", productId);       // 所属产品
        requestBody.put("auth_type", "SECRET");         // 认证方式：密钥认证
        requestBody.put("secret", generateDeviceSecret(deviceId));  // 设备密钥

        // 设备初始信息
        Map<String, Object> extensionInfo = new HashMap<>();
        extensionInfo.put("type", "wearable");          // 设备类型：可穿戴
        extensionInfo.put("location", "nursing_room");  // 安装位置：护理房间
        requestBody.put("extension_info", extensionInfo);

        try {
            // 发送 POST 请求到 IoTDA 平台
            HttpHeaders headers = buildAuthHeaders();
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.POST, request, String.class);

            // 解析响应
            JsonNode responseNode = objectMapper.readTree(response.getBody());
            log.info("设备注册成功 - deviceId: {}, deviceName: {}",
                    deviceId, deviceName);

            return responseNode;

        } catch (Exception e) {
            log.error("设备注册失败 - deviceId: {}, error: {}",
                    deviceId, e.getMessage(), e);
            throw new RuntimeException("设备注册失败", e);
        }
    }

    /**
     * 查询设备详情
     *
     * @param deviceId 设备ID
     * @return 设备详细信息
     */
    public JsonNode getDevice(String deviceId) {
        String url = iotdaHost + "/v5/iot/" + projectId + "/devices/" + deviceId;

        try {
            HttpHeaders headers = buildAuthHeaders();
            HttpEntity<String> request = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.GET, request, String.class);

            return objectMapper.readTree(response.getBody());

        } catch (Exception e) {
            log.error("查询设备失败 - deviceId: {}", deviceId, e);
            throw new RuntimeException("查询设备失败", e);
        }
    }

    /**
     * 删除设备
     * 设备报废或退库时调用
     */
    public void deleteDevice(String deviceId) {
        String url = iotdaHost + "/v5/iot/" + projectId + "/devices/" + deviceId;

        try {
            HttpHeaders headers = buildAuthHeaders();
            HttpEntity<String> request = new HttpEntity<>(headers);
            restTemplate.exchange(url, HttpMethod.DELETE, request, String.class);

            log.info("设备已删除 - deviceId: {}", deviceId);

        } catch (Exception e) {
            log.error("删除设备失败 - deviceId: {}", deviceId, e);
            throw new RuntimeException("删除设备失败", e);
        }
    }

    /**
     * 生成设备密钥
     * 使用 HMAC-SHA256 基于设备ID生成唯一密钥
     */
    private String generateDeviceSecret(String deviceId) {
        // 实际项目中使用更安全的密钥生成策略
        // 例如：HMAC-SHA256(主密钥, deviceId) 取前 16 位
        return java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    /**
     * 构建 IoTDA 认证请求头
     * 使用华为云 IAM Token 进行认证
     */
    private HttpHeaders buildAuthHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        // 从 IAM 服务获取 Token（实际项目应缓存 Token）
        String token = getIamToken();
        headers.set("X-Auth-Token", token);
        return headers;
    }

    /**
     * 获取 IAM Token（简化实现）
     * 实际项目应缓存 Token 并在过期前刷新
     */
    private String getIamToken() {
        // 实际实现：调用华为云 IAM API 获取 Token
        // 这里简化处理，从配置读取
        return System.getenv("IOTDA_IAM_TOKEN");
    }
}
```

---

## 三、设备数据上报监听

### 3.1 数据回调接收

```java
// IoTDACallbackController.java
// IoTDA 数据回调控制器 —— 接收 IoTDA 平台推送的设备数据
package com.zznursing.iotda.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zznursing.iotda.service.DeviceDataProcessService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * IoTDA 数据回调控制器
 * 接收华为 IoTDA 平台通过 HTTP 回调推送的设备数据
 * 包含设备上报数据、设备状态变更、设备生命周期事件等
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/iotda")
@RequiredArgsConstructor
public class IoTDACallbackController {

    private final ObjectMapper objectMapper;
    private final DeviceDataProcessService deviceDataProcessService;

    /**
     * 接收设备上报数据回调
     * IoTDA 平台通过数据转发规则将设备数据推送到此接口
     * 推送格式为 JSON，包含设备ID、服务ID、数据内容等
     */
    @PostMapping("/callback")
    public String handleDeviceDataCallback(@RequestBody String requestBody,
                                           @RequestHeader Map<String, String> headers) {
        try {
            // 解析 IoTDA 推送的数据
            JsonNode rootNode = objectMapper.readTree(requestBody);
            String deviceId = rootNode.path("notify_data").path("device_id").asText("/");
            String serviceId = rootNode.path("notify_data").path("service_id").asText();
            JsonNode data = rootNode.path("notify_data").path("body");

            log.info("收到 IoTDA 设备数据 - deviceId: {}, serviceId: {}, data: {}",
                    deviceId, serviceId, data);

            // 处理设备数据：校验、格式转换、发送到消息队列
            deviceDataProcessService.processDeviceData(deviceId, serviceId, data);

            // 返回成功响应，IoTDA 平台收到 200 后不再重试
            return "{\"result\":\"success\"}";

        } catch (Exception e) {
            log.error("处理 IoTDA 回调数据异常", e);
            // 返回非 200 状态码，IoTDA 平台会重试
            throw new RuntimeException("数据处理异常", e);
        }
    }

    /**
     * 接收设备状态变更回调
     * 设备上线/离线时 IoTDA 平台推送状态变更通知
     */
    @PostMapping("/status-callback")
    public String handleDeviceStatusCallback(@RequestBody String requestBody) {
        try {
            JsonNode rootNode = objectMapper.readTree(requestBody);
            String deviceId = rootNode.path("notify_data").path("device_id").asText();
            String status = rootNode.path("notify_data").path("status").asText();

            log.info("设备状态变更 - deviceId: {}, status: {}", deviceId, status);

            // 更新 Redis 中的设备在线状态
            deviceDataProcessService.updateDeviceStatus(deviceId, status);

            return "{\"result\":\"success\"}";

        } catch (Exception e) {
            log.error("处理设备状态变更异常", e);
            throw new RuntimeException("状态处理异常", e);
        }
    }
}
```

### 3.2 数据处理服务

```java
// DeviceDataProcessService.java
// 设备数据处理服务 —— 解析 IoTDA 数据，分发到业务模块
package com.zznursing.iotda.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zznursing.iotda.dto.DeviceDataMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 设备数据处理服务
 * 核心职责：接收 IoTDA 回调数据，解析为标准格式，分发到消息队列
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceDataProcessService {

    private final RocketMQTemplate rocketMQTemplate;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    /** 设备数据 RocketMQ 主题 */
    private static final String TOPIC_DEVICE_DATA = "zznursing-device-data";

    /** 设备状态 RocketMQ 主题 */
    private static final String TOPIC_DEVICE_STATUS = "zznursing-device-status";

    /** 设备在线状态 Redis Key 前缀 */
    private static final String REDIS_KEY_DEVICE_STATUS = "device:online:";

    /**
     * 处理设备上报数据
     * 1. 解析数据内容
     * 2. 发送到 RocketMQ 异步处理
     * 3. 更新 Redis 设备最新状态
     */
    public void processDeviceData(String deviceId, String serviceId, JsonNode data) {
        try {
            // 构建标准设备数据消息
            DeviceDataMessage message = new DeviceDataMessage();
            message.setDeviceId(deviceId);
            message.setServiceId(serviceId);
            message.setTimestamp(System.currentTimeMillis());

            // 根据服务ID解析不同数据类型
            // IoTDA 中每个设备可以定义多个服务（Service），每个服务对应一类数据
            switch (serviceId) {
                case "HeartRate":  // 心率服务
                    message.setDataType("heart_rate");
                    message.setValue(data.path("heart_rate").asInt());
                    break;
                case "BloodPressure":  // 血压服务
                    message.setDataType("blood_pressure");
                    message.setValue(data.path("systolic").asInt());
                    message.setDiastolic(data.path("diastolic").asInt());
                    break;
                case "Temperature":  // 体温服务
                    message.setDataType("temperature");
                    message.setValue(data.path("temperature").asDouble());
                    break;
                case "FallDetection":  // 跌倒检测服务
                    message.setDataType("fall_detection");
                    message.setValue(1);
                    message.setLatitude(data.path("latitude").asDouble());
                    message.setLongitude(data.path("longitude").asDouble());
                    break;
                default:
                    log.warn("未知服务类型: {}", serviceId);
                    message.setDataType("unknown");
                    message.setValue(data.toString());
            }

            // 发送到 RocketMQ 异步处理
            rocketMQTemplate.convertAndSend(TOPIC_DEVICE_DATA, message);

            // 更新 Redis 设备最新数据缓存
            String redisKey = "device:latest:" + deviceId;
            stringRedisTemplate.opsForValue().set(
                    redisKey, objectMapper.writeValueAsString(message), 5, TimeUnit.MINUTES);

            log.debug("设备数据处理完成 - deviceId: {}, type: {}", deviceId, message.getDataType());

        } catch (Exception e) {
            log.error("设备数据处理异常 - deviceId: {}", deviceId, e);
        }
    }

    /**
     * 更新设备在线状态
     * 设备上线/离线时更新 Redis 中的状态缓存
     */
    public void updateDeviceStatus(String deviceId, String status) {
        String redisKey = REDIS_KEY_DEVICE_STATUS + deviceId;

        if ("ONLINE".equals(status)) {
            // 设备上线，写入状态并设置过期时间（设备最大心跳间隔的 2 倍）
            stringRedisTemplate.opsForValue().set(redisKey, "online", 10, TimeUnit.MINUTES);
            log.info("设备上线 - deviceId: {}", deviceId);
        } else if ("OFFLINE".equals(status)) {
            // 设备离线，更新状态
            stringRedisTemplate.opsForValue().set(redisKey, "offline", 30, TimeUnit.MINUTES);
            log.warn("设备离线 - deviceId: {}", deviceId);

            // 发送设备离线消息到 RocketMQ，触发告警逻辑
            rocketMQTemplate.convertAndSend(TOPIC_DEVICE_STATUS, deviceId + ":offline");
        }
    }
}
```

---

## 四、远程命令下发

### 4.1 命令下发服务

```java
// IoTDACommandService.java
// IoTDA 命令下发服务 —— 向设备发送远程控制指令
package com.zznursing.iotda.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * IoTDA 命令下发服务
 * 向设备发送远程控制指令，如：调整心率检测频率、触发蜂鸣器报警等
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IoTDACommandService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${iotda.host}")
    private String iotdaHost;

    @Value("${iotda.project-id}")
    private String projectId;

    @Value("${iotda.command.response-timeout}")
    private int responseTimeout;

    /**
     * 下发异步命令到设备
     * 异步命令：只管下发，不等待设备响应
     * 适用于：调整设备配置、设置参数等
     *
     * @param deviceId 设备ID
     * @param commandName 命令名称，如 "set_heart_rate_interval"
     * @param params 命令参数
     */
    public void sendAsyncCommand(String deviceId, String commandName, Map<String, Object> params) {
        String url = iotdaHost + "/v5/iot/" + projectId + "/devices/" + deviceId + "/commands";

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("service_id", "DeviceControl");    // 服务ID
        requestBody.put("command_name", commandName);      // 命令名称
        requestBody.put("paras", params);                  // 命令参数

        try {
            HttpHeaders headers = buildAuthHeaders();
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.POST, request, String.class);

            JsonNode responseNode = objectMapper.readTree(response.getBody());
            String commandId = responseNode.path("command_id").asText();

            log.info("异步命令下发成功 - deviceId: {}, command: {}, commandId: {}",
                    deviceId, commandName, commandId);

        } catch (Exception e) {
            log.error("异步命令下发失败 - deviceId: {}, command: {}",
                    deviceId, commandName, e);
            throw new RuntimeException("命令下发失败", e);
        }
    }

    /**
     * 下发同步命令到设备
     * 同步命令：下发后等待设备响应，超时则返回失败
     * 适用于：查询设备状态、立即检测等需要实时响应的场景
     *
     * @param deviceId 设备ID
     * @param commandName 命令名称
     * @param params 命令参数
     * @return 设备响应内容
     */
    public JsonNode sendSyncCommand(String deviceId, String commandName, Map<String, Object> params) {
        String url = iotdaHost + "/v5/iot/" + projectId + "/devices/" + deviceId + "/commands";

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("service_id", "DeviceControl");
        requestBody.put("command_name", commandName);
        requestBody.put("paras", params);
        requestBody.put("expire_time", responseTimeout);  // 超时时间

        try {
            HttpHeaders headers = buildAuthHeaders();
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.POST, request, String.class);

            JsonNode responseNode = objectMapper.readTree(response.getBody());
            String status = responseNode.path("status").asText();

            log.info("同步命令下发完成 - deviceId: {}, command: {}, status: {}",
                    deviceId, commandName, status);

            return responseNode;

        } catch (Exception e) {
            log.error("同步命令下发失败 - deviceId: {}, command: {}",
                    deviceId, commandName, e);
            throw new RuntimeException("命令下发失败，设备可能离线", e);
        }
    }

    /**
     * 触发设备蜂鸣器报警（用于寻找老人位置）
     * 当老人走失或需要定位时，向设备下发蜂鸣命令
     */
    public void triggerBuzzer(String deviceId, int durationSeconds) {
        Map<String, Object> params = new HashMap<>();
        params.put("duration", durationSeconds);  // 蜂鸣时长（秒）
        params.put("volume", 100);                 // 音量 0-100

        sendAsyncCommand(deviceId, "trigger_buzzer", params);
        log.info("设备蜂鸣器已触发 - deviceId: {}, duration: {}s", deviceId, durationSeconds);
    }

    /**
     * 设置心率检测频率
     *
     * @param deviceId 设备ID
     * @param intervalSeconds 检测间隔（秒）
     */
    public void setHeartRateInterval(String deviceId, int intervalSeconds) {
        Map<String, Object> params = new HashMap<>();
        params.put("interval", intervalSeconds);

        sendAsyncCommand(deviceId, "set_heart_rate_interval", params);
        log.info("心率检测频率已设置 - deviceId: {}, interval: {}s",
                deviceId, intervalSeconds);
    }

    /**
     * 构建认证请求头
     */
    private HttpHeaders buildAuthHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Auth-Token", System.getenv("IOTDA_IAM_TOKEN"));
        return headers;
    }
}
```

### 4.2 命令下发场景

```java
// CommandController.java
// 命令下发控制器 —— 提供 REST API 供管理后台调用
package com.zznursing.iotda.controller;

import com.zznursing.iotda.service.IoTDACommandService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 设备命令控制器
 * 管理后台通过此接口下发远程控制指令
 */
@RestController
@RequestMapping("/api/v1/device/command")
@RequiredArgsConstructor
public class CommandController {

    private final IoTDACommandService commandService;

    /**
     * 触发设备蜂鸣器（寻找老人位置）
     */
    @PostMapping("/{deviceId}/buzzer")
    public void triggerBuzzer(@PathVariable String deviceId,
                              @RequestParam(defaultValue = "30") int duration) {
        commandService.triggerBuzzer(deviceId, duration);
    }

    /**
     * 调整心率检测频率
     */
    @PostMapping("/{deviceId}/heart-rate-interval")
    public void setHeartRateInterval(@PathVariable String deviceId,
                                     @RequestParam int interval) {
        commandService.setHeartRateInterval(deviceId, interval);
    }

    /**
     * 查询设备实时状态
     */
    @GetMapping("/{deviceId}/status")
    public Object getDeviceStatus(@PathVariable String deviceId) {
        return commandService.sendSyncCommand(deviceId, "get_status", Map.of());
    }
}
```

---

## 五、面试题

### 问题 1：华为 IoTDA 平台的架构优势

**核心优势：**

1. **设备接入协议丰富**：原生支持 MQTT、CoAP、HTTP、HTTPS 等多种协议，兼容不同厂商的智能设备
2. **设备影子机制**：IoTDA 维护设备影子（Device Shadow），云端始终保存设备最新状态，应用层不直接与设备通信
3. **数据转发规则**：支持将设备数据转发到多种目标（HTTP、消息队列、函数计算等），灵活集成
4. **安全认证**：支持 X.509 证书、设备密钥、OAuth 2.0 等多种认证方式，满足医疗数据安全要求
5. **OTA 升级**：内置 OTA 升级服务，支持设备固件远程升级

### 问题 2：设备协议选型策略

**选型决策：**

| 协议 | 适用场景 | 养老场景应用 |
|------|----------|-------------|
| **MQTT** | 频繁上报、低功耗 | 心率手环数据上报（首选） |
| **CoAP** | 资源受限、UDP 场景 | 低功耗定位胸牌 |
| **HTTP** | 非实时数据上报 | 血压计定时测量结果上报 |
| **LwM2M** | 资源极受限设备 | 体温贴片 |

**选型依据：** 养老设备以穿戴式为主，数据上报频率高（5-30 秒/次），设备电池续航要求高（至少 3 天），因此 MQTT 是主流选择，配合 QoS 1 保证数据不丢失。

### 问题 3：边缘计算在养老场景中的应用

**应用场景：**

1. **本地告警处理**：边缘网关在本地即可判断心率异常并触发告警，无需等待云端处理，延迟从秒级降到毫秒级
2. **断网续传**：网络不稳定时，边缘网关缓存设备数据，网络恢复后批量上传
3. **数据聚合**：边缘网关对设备数据进行聚合（如 1 分钟内的平均值），减少上报数据量
4. **AI 推理**：边缘节点运行轻量级 AI 模型（如跌倒检测模型），实现本地智能判断