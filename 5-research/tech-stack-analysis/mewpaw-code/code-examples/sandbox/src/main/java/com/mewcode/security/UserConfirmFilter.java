package com.mewcode.security;

import java.util.Set;

/**
 * 第四层过滤器：人工确认拦截。
 * <p>
 * 职责：对于特定高危操作（即使通过了前三层检查），仍要求用户明确确认后方可执行。
 * <p>
 * 设计原则：
 * <ul>
 *   <li>本示例使用模拟的自动确认机制（alwaysConfirm=false 直接放行）</li>
 *   <li>生产环境应对接真实的 TUI 交互界面（如 JLine prompt / WebSocket 确认弹窗）</li>
 *   <li>配合 ReAct 循环的 AgentEvent 机制，可在 UI 层展示确认对话框</li>
 * </ul>
 * <p>
 * 需人工确认的高危操作示例：
 * <ul>
 *   <li>删除文件（rm、del）</li>
 *   <li>修改系统配置（chmod、chown）</li>
 *   <li>执行网络请求（curl、wget）</li>
 *   <li>进程管理（kill、taskkill）</li>
 * </ul>
 */
public class UserConfirmFilter implements SecurityFilterChain {

    /** 需要人工确认的操作关键词（不区分大小写匹配） */
    private static final Set<String> CONFIRM_REQUIRED_KEYWORDS = Set.of(
            "rm ",               // 删除文件
            "del ",              // Windows 删除文件
            "chmod",             // 修改文件权限
            "chown",             // 修改文件所有者
            "curl",              // 网络请求
            "wget",              // 下载文件
            "kill",              // 终止进程
            "taskkill",          // Windows 终止进程
            "shutdown",          // 关机
            "reboot"             // 重启
    );

    /** 是否启用真实的人工确认（false=模拟模式，自动放行） */
    private final boolean alwaysConfirm;

    /**
     * 构造人工确认过滤器。
     *
     * @param alwaysConfirm true=所有操作都需确认，false=仅高危操作需确认
     */
    public UserConfirmFilter(boolean alwaysConfirm) {
        this.alwaysConfirm = alwaysConfirm;
    }

    @Override
    public SecurityResult check(String toolName, String args, SecurityFilterChain nextChain) {
        // 强制确认模式：所有操作都需确认
        if (alwaysConfirm) {
            // TODO: 生产环境应阻塞等待用户输入
            // 本示例中直接放行，实际应调用 TUI 交互组件
            return SecurityResult.allow("UserConfirmFilter");
        }

        // 检查是否包含需要确认的关键词
        if (args != null) {
            String lowerArgs = args.toLowerCase();
            for (String keyword : CONFIRM_REQUIRED_KEYWORDS) {
                if (lowerArgs.contains(keyword)) {
                    // TODO: 生产环境应展示确认对话框，等待用户输入 y/n
                    // 本示例中直接放行，日志记录该操作需人工确认
                    System.out.println("[UserConfirmFilter] 注意：操作包含关键词 '"
                            + keyword.trim() + "'，生产环境需人工确认");
                    break;
                }
            }
        }

        // 通过检查，委托给下一层过滤器
        if (nextChain != null) {
            return nextChain.check(toolName, args, null);
        }
        return SecurityResult.allow("UserConfirmFilter");
    }
}
