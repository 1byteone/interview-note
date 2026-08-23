package com.mewcode.security;

import java.util.Set;

/**
 * 第一层过滤器：未注册工具拦截。
 * <p>
 * 职责：检查请求的工具名是否在白名单中注册。
 * 若工具未注册，立即拒绝，不传递给后续过滤器。
 * <p>
 * 安全意义：防止 Agent 调用未知的、可能具有破坏性的工具。
 * 只有预注册的工具才能通过此层检查。
 */
public class ToolFilter implements SecurityFilterChain {

    /** 已注册的合法工具名白名单 */
    private final Set<String> registeredTools;

    /**
     * 构造工具白名单过滤器。
     *
     * @param registeredTools 已注册的合法工具名集合
     */
    public ToolFilter(Set<String> registeredTools) {
        this.registeredTools = registeredTools;
    }

    @Override
    public SecurityResult check(String toolName, String args, SecurityFilterChain nextChain) {
        // 检查工具是否在白名单中
        if (!registeredTools.contains(toolName)) {
            return SecurityResult.deny(
                    "工具 '" + toolName + "' 未在白名单中注册，调用被拒绝",
                    "ToolFilter");
        }

        // 通过检查，委托给下一层过滤器
        if (nextChain != null) {
            return nextChain.check(toolName, args, null);
        }
        return SecurityResult.allow("ToolFilter");
    }
}
