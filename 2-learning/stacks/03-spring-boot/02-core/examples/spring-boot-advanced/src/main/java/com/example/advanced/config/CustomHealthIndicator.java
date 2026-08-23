package com.example.advanced.config;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.Random;

/**
 * CustomHealthIndicator —— 自定义健康检查
 *
 * Actuator 的 /actuator/health 端点会汇总所有 HealthIndicator 的状态
 *
 * 场景：
 * - 检查下游依赖（数据库、Redis、第三方 API）是否可用
 * - 返回丰富状态信息（版本号、依赖状态等）
 * - 监控系统（Prometheus/Grafana）据此判断服务可用性
 */
@Component // 注册为 Spring Bean，Actuator 自动发现并纳入健康检查
public class CustomHealthIndicator implements HealthIndicator {

    private final Random random = new Random();

    /**
     * 健康检查的核心方法：返回 Health 对象
     * Health.up(...)：UP 状态（服务正常）
     * Health.down(...)：DOWN 状态（服务异常，会触发告警）
     * Health.unknown(...)：UNKNOWN 状态
     */
    @Override
    public Health health() {
        // 如果手动设为 DOWN，优先响应
        if (manualDown) {
            return Health.down()
                    .withDetail("reason", "手动触发 DOWN")
                    .withDetail("manualDown", true)
                    .build();
        }

        // 模拟检查一个关键业务依赖是否正常
        boolean dependencyHealthy = checkDependency();

        if (dependencyHealthy) {
            // withDetail() 可添加任意键值对作为详细状态信息
            return Health.up()
                    .withDetail("database", "ok")
                    .withDetail("cache", "ok")
                    .withDetail("version", "1.0.0")
                    .build();
        }

        // 检查失败：返回 DOWN + 失败原因（可用于告警分析）
        return Health.down()
                .withDetail("database", "connection timeout")
                .withDetail("error.code", "DB_TIMEOUT")
                .withException(new RuntimeException("依赖服务不可用"))
                .build();
    }

    /**
     * 模拟检查外部依赖（真实项目中可替换为实际连接检查）
     */
    private boolean checkDependency() {
        // 90% 概率返回健康，剩下触发 DOWN 便于演示
        return random.nextInt(10) != 0;
    }

    /**
     * 为演示提供手动切换健康状态的能力
     * static 状态变量，可通过其他端点修改
     */
    public static volatile boolean manualDown = false;
}