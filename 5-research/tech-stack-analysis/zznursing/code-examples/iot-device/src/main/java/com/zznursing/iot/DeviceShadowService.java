package com.zznursing.iot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 设备影子服务
 *
 * 功能说明：
 * 1. 维护设备的期望状态（Desired State）
 * 2. 维护设备的上报状态（Reported State）
 * 3. 实现设备状态同步
 * 4. 支持离线设备状态管理
 *
 * 设备影子概念：
 * - 每个设备在云端都有一个JSON文档，称为"设备影子"
 * - 设备影子包含两个主要部分：
 *   1. reported: 设备上报的实际状态
 *   2. desired: 系统设置的期望状态
 * - 当设备在线时，会同步desired状态到设备
 *
 * 使用场景：
 * - 智慧养老系统中，远程设置老人的健康监测参数
 * - 设备离线时，保存控制指令，待设备上线后同步
 *
 * @author zznursing
 * @since 1.0.0
 */
@Service
public class DeviceShadowService {

    private static final Logger logger = LoggerFactory.getLogger(DeviceShadowService.class);

    /**
     * JSON对象映射器
     */
    private final ObjectMapper objectMapper;

    /**
     * 设备影子缓存
     * Key: 设备ID
     * Value: 设备影子JSON文档
     *
     * 实际项目中应存储在数据库或Redis中
     */
    private final ConcurrentHashMap<String, ObjectNode> deviceShadowCache = new ConcurrentHashMap<>();

    /**
     * 构造函数
     *
     * @param objectMapper JSON对象映射器
     */
    public DeviceShadowService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 创建设备影子
     *
     * 功能：为新设备创建初始的设备影子文档
     *
     * @param deviceId 设备ID
     * @param deviceType 设备类型
     * @return 创建的设备影子文档
     */
    public ObjectNode createDeviceShadow(String deviceId, String deviceType) {
        logger.info("创建设备影子: deviceId={}, deviceType={}", deviceId, deviceType);

        // 创建影子文档根节点
        ObjectNode shadow = objectMapper.createObjectNode();

        // 设置设备基本信息
        shadow.put("deviceId", deviceId);
        shadow.put("deviceType", deviceType);
        shadow.put("createdAt", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        shadow.put("version", 1);

        // 初始化期望状态（desired）
        ObjectNode desired = objectMapper.createObjectNode();
        shadow.set("desired", desired);

        // 初始化上报状态（reported）
        ObjectNode reported = objectMapper.createObjectNode();
        shadow.set("reported", reported);

        // 初始化元数据
        ObjectNode metadata = objectMapper.createObjectNode();
        shadow.set("metadata", metadata);

        // 缓存设备影子
        deviceShadowCache.put(deviceId, shadow);

        logger.info("设备影子创建成功: deviceId={}", deviceId);
        return shadow;
    }

    /**
     * 更新设备上报状态
     *
     * 功能：当设备上报数据时，更新设备影子的reported部分
     *
     * @param deviceId 设备ID
     * @param reportedState 上报的状态数据
     */
    public void updateReportedState(String deviceId, Map<String, Object> reportedState) {
        logger.info("更新设备上报状态: deviceId={}, state={}", deviceId, reportedState);

        // 获取或创建设备影子
        ObjectNode shadow = deviceShadowCache.computeIfAbsent(deviceId,
                id -> createDeviceShadow(id, "unknown"));

        // 获取上报状态节点
        ObjectNode reported = (ObjectNode) shadow.get("reported");

        // 更新上报状态
        reportedState.forEach((key, value) -> {
            if (value instanceof Number) {
                reported.put(key, (Number) value);
            } else if (value instanceof Boolean) {
                reported.put(key, (Boolean) value);
            } else {
                reported.put(key, value.toString());
            }
        });

        // 更新时间戳
        reported.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

        // 更新版本号
        int version = shadow.get("version").asInt();
        shadow.put("version", version + 1);

        // 更新元数据
        ObjectNode metadata = (ObjectNode) shadow.get("metadata");
        reportedState.keySet().forEach(key -> {
            ObjectNode metaItem = objectMapper.createObjectNode();
            metaItem.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            metadata.set(key, metaItem);
        });

        logger.debug("设备上报状态更新完成: deviceId={}, version={}", deviceId, version + 1);
    }

    /**
     * 更新设备期望状态
     *
     * 功能：系统设置设备的期望状态，设备上线后会同步这些配置
     *
     * @param deviceId 设备ID
     * @param desiredState 期望的状态数据
     */
    public void updateDesiredState(String deviceId, Map<String, Object> desiredState) {
        logger.info("更新设备期望状态: deviceId={}, state={}", deviceId, desiredState);

        // 获取或创建设备影子
        ObjectNode shadow = deviceShadowCache.computeIfAbsent(deviceId,
                id -> createDeviceShadow(id, "unknown"));

        // 获取期望状态节点
        ObjectNode desired = (ObjectNode) shadow.get("desired");

        // 更新期望状态
        desiredState.forEach((key, value) -> {
            if (value instanceof Number) {
                desired.put(key, (Number) value);
            } else if (value instanceof Boolean) {
                desired.put(key, (Boolean) value);
            } else {
                desired.put(key, value.toString());
            }
        });

        // 更新时间戳
        desired.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

        // 更新版本号
        int version = shadow.get("version").asInt();
        shadow.put("version", version + 1);

        logger.debug("设备期望状态更新完成: deviceId={}, version={}", deviceId, version + 1);
    }

    /**
     * 获取设备影子
     *
     * @param deviceId 设备ID
     * @return 设备影子文档，如果不存在则返回null
     */
    public ObjectNode getDeviceShadow(String deviceId) {
        return deviceShadowCache.get(deviceId);
    }

    /**
     * 获取设备上报状态
     *
     * @param deviceId 设备ID
     * @return 上报状态节点
     */
    public JsonNode getReportedState(String deviceId) {
        ObjectNode shadow = deviceShadowCache.get(deviceId);
        return shadow != null ? shadow.get("reported") : null;
    }

    /**
     * 获取设备期望状态
     *
     * @param deviceId 设备ID
     * @return 期望状态节点
     */
    public JsonNode getDesiredState(String deviceId) {
        ObjectNode shadow = deviceShadowCache.get(deviceId);
        return shadow != null ? shadow.get("desired") : null;
    }

    /**
     * 比较期望状态和上报状态
     *
     * 功能：检测设备是否需要同步配置
     *
     * @param deviceId 设备ID
     * @return 需要同步的配置项，如果无需同步则返回空Map
     */
    public Map<String, Object> getPendingSyncConfig(String deviceId) {
        ObjectNode shadow = deviceShadowCache.get(deviceId);
        if (shadow == null) {
            return new ConcurrentHashMap<>();
        }

        JsonNode desired = shadow.get("desired");
        JsonNode reported = shadow.get("reported");

        ConcurrentHashMap<String, Object> pendingSync = new ConcurrentHashMap<>();

        if (desired != null && reported != null) {
            // 遍历期望状态，检查是否与上报状态一致
            desired.fields().forEachRemaining(entry -> {
                String key = entry.getKey();
                JsonNode desiredValue = entry.getValue();
                JsonNode reportedValue = reported.get(key);

                // 如果上报状态不存在或值不同，则需要同步
                if (reportedValue == null || !desiredValue.equals(reportedValue)) {
                    pendingSync.put(key, desiredValue);
                }
            });
        }

        return pendingSync;
    }

    /**
     * 标记设备配置已同步
     *
     * 功能：设备同步配置后，更新上报状态，使其与期望状态一致
     *
     * @param deviceId 设备ID
     * @param syncedConfig 已同步的配置项
     */
    public void markConfigSynced(String deviceId, Map<String, Object> syncedConfig) {
        logger.info("标记设备配置已同步: deviceId={}, config={}", deviceId, syncedConfig);

        // 更新上报状态
        updateReportedState(deviceId, syncedConfig);
    }

    /**
     * 删除设备影子
     *
     * @param deviceId 设备ID
     */
    public void deleteDeviceShadow(String deviceId) {
        logger.info("删除设备影子: deviceId={}", deviceId);
        deviceShadowCache.remove(deviceId);
    }

    /**
     * 获取设备影子文档的JSON字符串
     *
     * @param deviceId 设备ID
     * @return JSON字符串
     */
    public String getDeviceShadowJson(String deviceId) {
        ObjectNode shadow = deviceShadowCache.get(deviceId);
        if (shadow != null) {
            try {
                return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(shadow);
            } catch (Exception e) {
                logger.error("序列化设备影子失败: deviceId={}", deviceId, e);
                return "{}";
            }
        }
        return "{}";
    }
}
