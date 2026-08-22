package com.mewcode.security;

/**
 * 安全过滤链接口 — 采用责任链模式，定义 Tool 执行前的安全检查流程。
 * <p>
 * 五层过滤器依次执行（任意一层拒绝则立即终止）：
 * <ol>
 *   <li>{@link ToolFilter} — 检查工具是否在白名单中注册</li>
 *   <li>{@link PathGuardFilter} — 防止路径遍历攻击（如 ../../etc/passwd）</li>
 *   <li>{@link CommandScannerFilter} — 检测危险命令模式（如 rm -rf /）</li>
 *   <li>{@link UserConfirmFilter} — 高危操作需人工确认</li>
 *   <li>{@link AuditLogFilter} — 记录所有操作的审计日志</li>
 * </ol>
 * <p>
 * 实现类负责将调用委托给下一个过滤器，形成完整的过滤链。
 */
public interface SecurityFilterChain {

    /**
     * 对一次 Tool 调用请求执行完整的安全检查。
     *
     * @param toolName    工具名称
     * @param args        工具参数（JSON 格式）
     * @param nextChain   下一个过滤器链（用于委托调用）；为 null 时表示链尾
     * @return 安全检查结果（允许或拒绝 + 原因）
     */
    SecurityResult check(String toolName, String args, SecurityFilterChain nextChain);
}
