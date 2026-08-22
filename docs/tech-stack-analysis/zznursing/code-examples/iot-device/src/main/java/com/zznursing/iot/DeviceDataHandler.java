package com.zznursing.iot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 设备数据处理器
 *
 * 功能说明：
 * 1. 接收IoT设备上报的原始数据
 * 2. 解析JSON格式的设备数据
 * 3. 数据验证和清洗
 * 4. 触发告警判断
 * 5. 存储设备状态信息
 *
 * 使用场景：
 * - 智慧养老系统中，接收老人佩戴的智能设备数据
 * - 包括：心率、血压、体温、位置、跌倒检测等
 *
 * 支持的设备类型：
 * - 智能手环：心率、血氧、步数、睡眠质量
 * - 智能血压计：收缩压、舒张压、脉搏
 * - 智能体温计：体温
 * - GPS定位器：经度、纬度、速度
 * - 跌倒检测器：加速度、跌倒事件
 *
 * @author zznursing
 * @since 1.0.0
 */
@Component
public class DeviceDataHandler {

    private static final Logger logger = LoggerFactory.getLogger(DeviceDataHandler.class);

    /**
     * JSON对象映射器，用于解析设备上报的JSON数据
     */
    private final ObjectMapper objectMapper;

    /**
     * 设备状态缓存
     * Key: 设备ID
     * Value: 设备最新状态数据
     *
     * 使用ConcurrentHashMap保证线程安全
     */
    private final ConcurrentHashMap<String, Map<String, Object>> deviceStatusCache = new ConcurrentHashMap<>();

    /**
     * 告警阈值配置
     * 用于判断设备数据是否异常
     */
    private final Map<String, Double> alertThresholds = new HashMap<>();

    /**
     * 构造函数
     *
     * @param objectMapper JSON对象映射器，由Spring自动注入
     */
    public DeviceDataHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        // 初始化告警阈值
        initAlertThresholds();
    }

    /**
     * 初始化告警阈值配置
     *
     * 阈值说明：
     * - 心率：< 50 或 > 120 为异常
     * - 收缩压：< 90 或 > 140 为异常
     * - 舒张压：< 60 或 > 90 为异常
     * - 体温：< 35.0 或 > 37.3 为异常
     * - 血氧：< 90 为异常
     */
    private void initAlertThresholds() {
        // 心率阈值
        alertThresholds.put("heartRate_min", 50.0);
        alertThresholds.put("heartRate_max", 120.0);

        // 血压阈值
        alertThresholds.put("systolicPressure_min", 90.0);
        alertThresholds.put("systolicPressure_max", 140.0);
        alertThresholds.put("diastolicPressure_min", 60.0);
        alertThresholds.put("diastolicPressure_max", 90.0);

        // 体温阈值
        alertThresholds.put("temperature_min", 35.0);
        alertThresholds.put("temperature_max", 37.3);

        // 血氧阈值
        alertThresholds.put("bloodOxygen_min", 90.0);
    }

    /**
     * 处理设备上报的消息
     *
     * 功能：Spring Integration消息处理器，自动接收MQTT通道中的消息
     * 处理流程：
     * 1. 接收原始消息
     * 2. 解析JSON数据
     * 3. 验证数据有效性
     * 4. 更新设备状态缓存
     * 5. 判断是否需要告警
     *
     * @param message Spring Integration消息对象，包含设备上报的原始数据
     */
    @ServiceActivator(inputChannel = "mqttInputChannel")
    public void handleDeviceMessage(Message<?> message) {
        try {
            // 获取消息负载（设备上报的原始JSON字符串）
            Object payload = message.getPayload();

            // 记录接收到的原始消息
            logger.info("接收到设备消息: {}", payload);

            // 如果是字节数组，转换为字符串
            String jsonString;
            if (payload instanceof byte[]) {
                jsonString = new String((byte[]) payload);
            } else {
                jsonString = payload.toString();
            }

            // 解析JSON数据
            JsonNode rootNode = objectMapper.readTree(jsonString);

            // 提取设备ID
            String deviceId = extractDeviceId(rootNode);
            if (deviceId == null || deviceId.isEmpty()) {
                logger.warn("消息中缺少设备ID，忽略该消息");
                return;
            }

            // 提取消息类型（设备类型）
            String deviceType = extractDeviceType(rootNode);

            // 提取时间戳
            String timestamp = extractTimestamp(rootNode);

            // 提取设备数据
            Map<String, Object> deviceData = extractDeviceData(rootNode);

            // 记录解析后的设备数据
            logger.info("设备ID: {}, 设备类型: {}, 时间: {}, 数据: {}",
                    deviceId, deviceType, timestamp, deviceData);

            // 更新设备状态缓存
            updateDeviceStatus(deviceId, deviceType, timestamp, deviceData);

            // 判断是否需要告警
            checkAndTriggerAlert(deviceId, deviceType, deviceData);

            // 处理特殊事件（如跌倒检测）
            handleSpecialEvents(deviceId, deviceType, rootNode);

        } catch (Exception e) {
            // 记录错误日志，但不抛出异常，避免消息重试
            logger.error("处理设备消息时发生错误: {}", e.getMessage(), e);
        }
    }

    /**
     * 从JSON中提取设备ID
     *
     * @param rootNode JSON根节点
     * @return 设备ID，如果不存在则返回null
     */
    private String extractDeviceId(JsonNode rootNode) {
        // 尝试多种可能的字段名
        JsonNode deviceIdNode = rootNode.get("deviceId");
        if (deviceIdNode == null) {
            deviceIdNode = rootNode.get("device_id");
        }
        if (deviceIdNode == null) {
            deviceIdNode = rootNode.get("id");
        }
        return deviceIdNode != null ? deviceIdNode.asText() : null;
    }

    /**
     * 从JSON中提取设备类型
     *
     * @param rootNode JSON根节点
     * @return 设备类型，默认为"unknown"
     */
    private String extractDeviceType(JsonNode rootNode) {
        JsonNode typeNode = rootNode.get("deviceType");
        if (typeNode == null) {
            typeNode = rootNode.get("device_type");
        }
        if (typeNode == null) {
            typeNode = rootNode.get("type");
        }
        return typeNode != null ? typeNode.asText("unknown") : "unknown";
    }

    /**
     * 从JSON中提取时间戳
     *
     * @param rootNode JSON根节点
     * @return 时间戳字符串，如果不存在则返回当前时间
     */
    private String extractTimestamp(JsonNode rootNode) {
        JsonNode timestampNode = rootNode.get("timestamp");
        if (timestampNode == null) {
            timestampNode = rootNode.get("time");
        }
        if (timestampNode != null) {
            return timestampNode.asText();
        }
        // 返回当前时间
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    /**
     * 从JSON中提取设备数据
     *
     * @param rootNode JSON根节点
     * @return 设备数据映射
     */
    private Map<String, Object> extractDeviceData(JsonNode rootNode) {
        Map<String, Object> data = new HashMap<>();

        // 尝试从data字段提取
        JsonNode dataNode = rootNode.get("data");
        if (dataNode == null) {
            dataNode = rootNode.get("payload");
        }

        if (dataNode != null && dataNode.isObject()) {
            // 遍历所有数据字段
            dataNode.fields().forEachRemaining(entry ->
                    data.put(entry.getKey(), entry.getValue())
            );
        } else {
            // 如果没有data字段，直接从根节点提取所有字段
            rootNode.fields().forEachRemaining(entry -> {
                // 排除元数据字段
                String key = entry.getKey();
                if (!"deviceId".equals(key) && !"device_id".equals(key) &&
                    !"deviceType".equals(key) && !"device_type".equals(key) &&
                    !"timestamp".equals(key) && !"time".equals(key)) {
                    data.put(key, entry.getValue());
                }
            });
        }

        return data;
    }

    /**
     * 更新设备状态缓存
     *
     * @param deviceId 设备ID
     * @param deviceType 设备类型
     * @param timestamp 时间戳
     * @param deviceData 设备数据
     */
    private void updateDeviceStatus(String deviceId, String deviceType,
                                    String timestamp, Map<String, Object> deviceData) {
        Map<String, Object> status = new HashMap<>();
        status.put("deviceId", deviceId);
        status.put("deviceType", deviceType);
        status.put("lastUpdateTime", timestamp);
        status.put("data", deviceData);
        status.put("status", "online");

        // 更新缓存
        deviceStatusCache.put(deviceId, status);

        logger.debug("更新设备状态缓存: deviceId={}, deviceType={}", deviceId, deviceType);
    }

    /**
     * 检查并触发告警
     *
     * @param deviceId 设备ID
     * @param deviceType 设备类型
     * @param deviceData 设备数据
     */
    private void checkAndTriggerAlert(String deviceId, String deviceType,
                                      Map<String, Object> deviceData) {
        // 检查心率
        checkHeartRateAlert(deviceId, deviceData);

        // 检查血压
        checkBloodPressureAlert(deviceId, deviceData);

        // 检查体温
        checkTemperatureAlert(deviceId, deviceData);

        // 检查血氧
        checkBloodOxygenAlert(deviceId, deviceData);
    }

    /**
     * 检查心率告警
     *
     * @param deviceId 设备ID
     * @param deviceData 设备数据
     */
    private void checkHeartRateAlert(String deviceId, Map<String, Object> deviceData) {
        Object heartRateObj = deviceData.get("heartRate");
        if (heartRateObj != null) {
            try {
                double heartRate = Double.parseDouble(heartRateObj.toString());
                if (heartRate < alertThresholds.get("heartRate_min") ||
                    heartRate > alertThresholds.get("heartRate_max")) {
                    triggerAlert(deviceId, "HEART_RATE_ABNORMAL",
                            "心率异常", "当前心率: " + heartRate + " bpm");
                }
            } catch (NumberFormatException e) {
                logger.warn("心率数据格式错误: {}", heartRateObj);
            }
        }
    }

    /**
     * 检查血压告警
     *
     * @param deviceId 设备ID
     * @param deviceData 设备数据
     */
    private void checkBloodPressureAlert(String deviceId, Map<String, Object> deviceData) {
        Object systolicObj = deviceData.get("systolicPressure");
        Object diastolicObj = deviceData.get("diastolicPressure");

        if (systolicObj != null && diastolicObj != null) {
            try {
                double systolic = Double.parseDouble(systolicObj.toString());
                double diastolic = Double.parseDouble(diastolicObj.toString());

                if (systolic < alertThresholds.get("systolicPressure_min") ||
                    systolic > alertThresholds.get("systolicPressure_max") ||
                    diastolic < alertThresholds.get("diastolicPressure_min") ||
                    diastolic > alertThresholds.get("diastolicPressure_max")) {
                    triggerAlert(deviceId, "BLOOD_PRESSURE_ABNORMAL",
                            "血压异常", "收缩压: " + systolic + " mmHg, 舒张压: " + diastolic + " mmHg");
                }
            } catch (NumberFormatException e) {
                logger.warn("血压数据格式错误");
            }
        }
    }

    /**
     * 检查体温告警
     *
     * @param deviceId 设备ID
     * @param deviceData 设备数据
     */
    private void checkTemperatureAlert(String deviceId, Map<String, Object> deviceData) {
        Object temperatureObj = deviceData.get("temperature");
        if (temperatureObj != null) {
            try {
                double temperature = Double.parseDouble(temperatureObj.toString());
                if (temperature < alertThresholds.get("temperature_min") ||
                    temperature > alertThresholds.get("temperature_max")) {
                    triggerAlert(deviceId, "TEMPERATURE_ABNORMAL",
                            "体温异常", "当前体温: " + temperature + " ℃");
                }
            } catch (NumberFormatException e) {
                logger.warn("体温数据格式错误: {}", temperatureObj);
            }
        }
    }

    /**
     * 检查血氧告警
     *
     * @param deviceId 设备ID
     * @param deviceData 设备数据
     */
    private void checkBloodOxygenAlert(String deviceId, Map<String, Object> deviceData) {
        Object bloodOxygenObj = deviceData.get("bloodOxygen");
        if (bloodOxygenObj != null) {
            try {
                double bloodOxygen = Double.parseDouble(bloodOxygenObj.toString());
                if (bloodOxygen < alertThresholds.get("bloodOxygen_min")) {
                    triggerAlert(deviceId, "BLOOD_OXYGEN_LOW",
                            "血氧偏低", "当前血氧: " + bloodOxygen + " %");
                }
            } catch (NumberFormatException e) {
                logger.warn("血氧数据格式错误: {}", bloodOxygenObj);
            }
        }
    }

    /**
     * 处理特殊事件（如跌倒检测）
     *
     * @param deviceId 设备ID
     * @param deviceType 设备类型
     * @param rootNode JSON根节点
     */
    private void handleSpecialEvents(String deviceId, String deviceType, JsonNode rootNode) {
        // 检查跌倒检测事件
        JsonNode eventNode = rootNode.get("event");
        if (eventNode != null) {
            String eventType = eventNode.asText();
            if ("FALL_DETECTED".equals(eventType)) {
                triggerAlert(deviceId, "FALL_DETECTED",
                        "跌倒检测", "检测到老人跌倒事件，请立即确认！");
            }
        }

        // 检查SOS紧急求助
        JsonNode sosNode = rootNode.get("sos");
        if (sosNode != null && sosNode.asBoolean()) {
            triggerAlert(deviceId, "SOS_ALERT",
                    "紧急求助", "老人触发SOS紧急求助！");
        }
    }

    /**
     * 触发告警
     *
     * 功能：发送告警通知（实际项目中会调用告警服务）
     *
     * @param deviceId 设备ID
     * @param alertType 告警类型
     * @param alertTitle 告警标题
     * @param alertMessage 告警详细信息
     */
    private void triggerAlert(String deviceId, String alertType,
                              String alertTitle, String alertMessage) {
        logger.warn("触发告警 - 设备ID: {}, 类型: {}, 标题: {}, 信息: {}",
                deviceId, alertType, alertTitle, alertMessage);

        // TODO: 实际项目中，这里会调用告警服务
        // 例如：发送短信、推送通知、记录告警日志等
        // alertService.sendAlert(deviceId, alertType, alertTitle, alertMessage);
    }

    /**
     * 获取设备状态（供外部调用）
     *
     * @param deviceId 设备ID
     * @return 设备状态信息，如果设备不存在则返回null
     */
    public Map<String, Object> getDeviceStatus(String deviceId) {
        return deviceStatusCache.get(deviceId);
    }

    /**
     * 获取所有在线设备状态
     *
     * @return 所有设备状态映射
     */
    public ConcurrentHashMap<String, Map<String, Object>> getAllDeviceStatus() {
        return deviceStatusCache;
    }
}
