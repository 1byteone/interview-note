package com.mewcode.security;

/**
 * 安全过滤结果 — 不可变记录类型，表示一次安全检查的最终判定。
 * <p>
 * 设计要点：
 * <ul>
 *   <li>使用 Java 17 record，天然不可变、equals/hashCode 自动生成</li>
 *   <li>{@code allowed} 字段为 true 时 {@code reason} 应为 null（通过校验无需解释）</li>
 *   <li>{@code allowed} 字段为 false 时 {@code reason} 必须提供拒绝原因（便于调试和日志）</li>
 * </ul>
 *
 * @param allowed  是否允许执行（true=放行，false=拦截）
 * @param reason   拦截原因（放行时为 null）
 * @param filter   做出判定的过滤器类名（用于审计追踪是哪一层拦截的）
 */
public record SecurityResult(boolean allowed, String reason, String filter) {

    /**
     * 创建一个放行结果（无拦截原因）。
     *
     * @param filterName 做出判定的过滤器类名
     * @return 允许执行的 SecurityResult
     */
    public static SecurityResult allow(String filterName) {
        return new SecurityResult(true, null, filterName);
    }

    /**
     * 创建一个拦截结果（附带拒绝原因）。
     *
     * @param reason     拦截原因描述
     * @param filterName 做出判定的过滤器类名
     * @return 拦截执行的 SecurityResult
     */
    public static SecurityResult deny(String reason, String filterName) {
        return new SecurityResult(false, reason, filterName);
    }
}
