package com.zznursing.iot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 设备命令服务
 *
 * 功能说明：
 * 1. 向IoT设备发送远程控制命令
 * 2. 管理命令的生命周期（发送、确认、超时）
 * 3. 支持同步和异步命令执行
 * 4. 命令结果跟踪和回调
 *
 * 支持的命令类型：
 * - 设备重启
 * - 参数配置（如设置上报频率、阈值等）
 * - 固件升级
 * - 远程锁定/解锁
 * - 紧急告警清除
 *
 * 使用场景：
 * - 智慧养老系统中，远程配置老人的智能设备
 * - 设备故障时，远程重启设备
 * - 紧急情况下，远程锁定设备防止误操作
 *
 * @author zznursing
 * @since 1.0.0
 */
@Service
public class DeviceCommandService {

    private static final Logger logger = LoggerFactory.getLogger(DeviceCommandService.class);

    /**
     * JSON对象映射器
     */
    private final ObjectMapper objectMapper;

    /**
     * MQTT输出通道，用于发送命令到设备
     * 由MqttClientConfig中定义的Bean注入
     */
    private final MessageChannel mqttOutputChannel;

    /**
     * 设备影子服务，用于管理设备状态
     */
    private final DeviceShadowService deviceShadowService;

    /**
     * 命令缓存
     * Key: 命令ID
     * Value: 命令信息
     */
    private final ConcurrentHashMap<String, CommandInfo> commandCache = new ConcurrentHashMap<>();

    /**
     * 构造函数
     *
     * @param objectMapper JSON对象映射器
     * @param mqttOutputChannel MQTT输出通道
     * @param deviceShadowService 设备影子服务
     */
    public DeviceCommandService(ObjectMapper objectMapper,
                                 MessageChannel mqttOutputChannel,
                                 DeviceShadowService deviceShadowService) {
        this.objectMapper = objectMapper;
        this.mqttOutputChannel = mqttOutputChannel;
        this.deviceShadowService = deviceShadowService;
    }

    /**
     * 发送设备命令
     *
     * 功能：向指定设备发送控制命令
     *
     * @param deviceId 设备ID
     * @param commandType 命令类型
     * @param commandData 命令参数
     * @return 命令ID，用于后续查询命令执行状态
     */
    public String sendCommand(String deviceId, String commandType, Map<String, Object> commandData) {
        logger.info("发送设备命令: deviceId={}, commandType={}, data={}",
                deviceId, commandType, commandData);

        // 生成唯一命令ID
        String commandId = UUID.randomUUID().toString();

        try {
            // 构建命令消息
            ObjectNode commandMessage = objectMapper.createObjectNode();
            commandMessage.put("commandId", commandId);
            commandMessage.put("deviceId", deviceId);
            commandMessage.put("commandType", commandType);
            commandMessage.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

            // 设置命令数据
            if (commandData != null && !commandData.isEmpty()) {
                ObjectNode dataNode = objectMapper.createObjectNode();
                commandData.forEach((key, value) -> {
                    if (value instanceof Number) {
                        dataNode.put(key, (Number) value);
                    } else if (value instanceof Boolean) {
                        dataNode.put(key, (Boolean) value);
                    } else {
                        dataNode.put(key, value.toString());
                    }
                });
                commandMessage.set("data", dataNode);
            }

            // 缓存命令信息
            CommandInfo commandInfo = new CommandInfo();
            commandInfo.setCommandId(commandId);
            commandInfo.setDeviceId(deviceId);
            commandInfo.setCommandType(commandType);
            commandInfo.setStatus("PENDING");
            commandInfo.setCreatedAt(LocalDateTime.now());
            commandInfo.setCommandData(commandData);
            commandCache.put(commandId, commandInfo);

            // 构建MQTT消息
            String topic = "devices/" + deviceId + "/commands";
            String payload = objectMapper.writeValueAsString(commandMessage);

            Message<String> message = MessageBuilder
                    .withPayload(payload)
                    .setHeader("mqtt_topic", topic)
                    .setHeader("mqtt_qos", 1)
                    .build();

            // 发送消息到MQTT通道
            mqttOutputChannel.send(message);

            logger.info("设备命令发送成功: commandId={}, topic={}", commandId, topic);

            return commandId;

        } catch (Exception e) {
            logger.error("发送设备命令失败: deviceId={}, commandType={}", deviceId, commandType, e);

            // 更新命令状态为失败
            CommandInfo commandInfo = commandCache.get(commandId);
            if (commandInfo != null) {
                commandInfo.setStatus("FAILED");
                commandInfo.setErrorMessage(e.getMessage());
            }

            return commandId;
        }
    }

    /**
     * 发送设备重启命令
     *
     * @param deviceId 设备ID
     * @return 命令ID
     */
    public String rebootDevice(String deviceId) {
        logger.info("发送设备重启命令: deviceId={}", deviceId);
        return sendCommand(deviceId, "REBOOT", null);
    }

    /**
     * 发送参数配置命令
     *
     * 功能：远程设置设备参数，如上报频率、告警阈值等
     *
     * @param deviceId 设备ID
     * @param configParams 配置参数
     * @return 命令ID
     */
    public String configureDevice(String deviceId, Map<String, Object> configParams) {
        logger.info("发送设备配置命令: deviceId={}, params={}", deviceId, configParams);
        return sendCommand(deviceId, "CONFIGURE", configParams);
    }

    /**
     * 发送固件升级命令
     *
     * @param deviceId 设备ID
     * @param firmwareUrl 固件下载地址
     * @param firmwareVersion 固件版本
     * @return 命令ID
     */
    public String upgradeFirmware(String deviceId, String firmwareUrl, String firmwareVersion) {
        logger.info("发送固件升级命令: deviceId={}, version={}", deviceId, firmwareVersion);

        Map<String, Object> data = Map.of(
                "firmwareUrl", firmwareUrl,
                "firmwareVersion", firmwareVersion
        );

        return sendCommand(deviceId, "FIRMWARE_UPGRADE", data);
    }

    /**
     * 发送远程锁定命令
     *
     * @param deviceId 设备ID
     * @param lockReason 锁定原因
     * @return 命令ID
     */
    public String lockDevice(String deviceId, String lockReason) {
        logger.info("发送设备锁定命令: deviceId={}, reason={}", deviceId, lockReason);

        Map<String, Object> data = Map.of(
                "locked", true,
                "lockReason", lockReason
        );

        return sendCommand(deviceId, "LOCK_DEVICE", data);
    }

    /**
     * 发送远程解锁命令
     *
     * @param deviceId 设备ID
     * @return 命令ID
     */
    public String unlockDevice(String deviceId) {
        logger.info("发送设备解锁命令: deviceId={}", deviceId);

        Map<String, Object> data = Map.of("locked", false);
        return sendCommand(deviceId, "UNLOCK_DEVICE", data);
    }

    /**
     * 发送告警清除命令
     *
     * @param deviceId 设备ID
     * @param alertType 告警类型
     * @return 命令ID
     */
    public String clearAlert(String deviceId, String alertType) {
        logger.info("发送告警清除命令: deviceId={}, alertType={}", deviceId, alertType);

        Map<String, Object> data = Map.of("alertType", alertType);
        return sendCommand(deviceId, "CLEAR_ALERT", data);
    }

    /**
     * 设置设备上报频率
     *
     * @param deviceId 设备ID
     * @param intervalSeconds 上报间隔（秒）
     * @return 命令ID
     */
    public String setReportInterval(String deviceId, int intervalSeconds) {
        logger.info("设置设备上报频率: deviceId={}, interval={}s", deviceId, intervalSeconds);

        Map<String, Object> data = Map.of("reportInterval", intervalSeconds);
        return configureDevice(deviceId, data);
    }

    /**
     * 设置心率监测阈值
     *
     * @param deviceId 设备ID
     * @param minHeartRate 最低心率阈值
     * @param maxHeartRate 最高心率阈值
     * @return 命令ID
     */
    public String setHeartRateThreshold(String deviceId, int minHeartRate, int maxHeartRate) {
        logger.info("设置心率阈值: deviceId={}, min={}, max={}", deviceId, minHeartRate, maxHeartRate);

        Map<String, Object> data = Map.of(
                "minHeartRate", minHeartRate,
                "maxHeartRate", maxHeartRate
        );

        return configureDevice(deviceId, data);
    }

    /**
     * 查询命令执行状态
     *
     * @param commandId 命令ID
     * @return 命令信息
     */
    public CommandInfo getCommandStatus(String commandId) {
        return commandCache.get(commandId);
    }

    /**
     * 更新命令状态（设备上报命令执行结果时调用）
     *
     * @param commandId 命令ID
     * @param status 新状态（SUCCESS, FAILED, TIMEOUT）
     * @param result 执行结果
     */
    public void updateCommandStatus(String commandId, String status, String result) {
        logger.info("更新命令状态: commandId={}, status={}, result={}", commandId, status, result);

        CommandInfo commandInfo = commandCache.get(commandId);
        if (commandInfo != null) {
            commandInfo.setStatus(status);
            commandInfo.setResult(result);
            commandInfo.setUpdatedAt(LocalDateTime.now());

            // 如果是配置命令，同步更新设备影子
            if ("SUCCESS".equals(status) && "CONFIGURE".equals(commandInfo.getCommandType())) {
                deviceShadowService.markConfigSynced(commandInfo.getDeviceId(),
                        commandInfo.getCommandData());
            }
        }
    }

    /**
     * 处理设备命令响应
     *
     * 功能：Spring Integration消息处理器，接收设备对命令的响应
     *
     * @param message 命令响应消息
     */
    @ServiceActivator(inputChannel = "mqttInputChannel")
    public void handleCommandResponse(Message<?> message) {
        try {
            String payload = message.getPayload().toString();
            logger.info("收到设备命令响应: {}", payload);

            // 解析响应JSON
            com.fasterxml.jackson.databind.JsonNode responseNode = objectMapper.readTree(payload);

            // 检查是否是命令响应消息
            JsonNode commandIdNode = responseNode.get("commandId");
            if (commandIdNode != null) {
                String commandId = commandIdNode.asText();
                String status = responseNode.has("status") ?
                        responseNode.get("status").asText() : "UNKNOWN";
                String result = responseNode.has("result") ?
                        responseNode.get("result").asText() : "";

                // 更新命令状态
                updateCommandStatus(commandId, status, result);
            }

        } catch (Exception e) {
            logger.error("处理设备命令响应失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 命令信息类
     *
     * 功能：封装命令的详细信息
     */
    public static class CommandInfo {
        /** 命令ID */
        private String commandId;

        /** 设备ID */
        private String deviceId;

        /** 命令类型 */
        private String commandType;

        /** 命令状态（PENDING, SUCCESS, FAILED, TIMEOUT） */
        private String status;

        /** 命令数据 */
        private Map<String, Object> commandData;

        /** 执行结果 */
        private String result;

        /** 错误信息 */
        private String errorMessage;

        /** 创建时间 */
        private LocalDateTime createdAt;

        /** 更新时间 */
        private LocalDateTime updatedAt;

        // Getter和Setter方法

        public String getCommandId() {
            return commandId;
        }

        public void setCommandId(String commandId) {
            this.commandId = commandId;
        }

        public String getDeviceId() {
            return deviceId;
        }

        public void setDeviceId(String deviceId) {
            this.deviceId = deviceId;
        }

        public String getCommandType() {
            return commandType;
        }

        public void setCommandType(String commandType) {
            this.commandType = commandType;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public Map<String, Object> getCommandData() {
            return commandData;
        }

        public void setCommandData(Map<String, Object> commandData) {
            this.commandData = commandData;
        }

        public String getResult() {
            return result;
        }

        public void setResult(String result) {
            this.result = result;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public void setErrorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
        }

        public LocalDateTime getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
        }

        public LocalDateTime getUpdatedAt() {
            return updatedAt;
        }

        public void setUpdatedAt(LocalDateTime updatedAt) {
            this.updatedAt = updatedAt;
        }

        @Override
        public String toString() {
            return "CommandInfo{" +
                    "commandId='" + commandId + '\'' +
                    ", deviceId='" + deviceId + '\'' +
                    ", commandType='" + commandType + '\'' +
                    ", status='" + status + '\'' +
                    ", createdAt=" + createdAt +
                    '}';
        }
    }
}
