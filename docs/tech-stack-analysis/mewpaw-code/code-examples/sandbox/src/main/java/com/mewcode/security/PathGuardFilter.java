package com.mewcode.security;

/**
 * 第二层过滤器：路径遍历防护。
 * <p>
 * 职责：检测 Tool 参数中是否包含路径遍历攻击模式。
 * 拦截所有包含 ".." 的路径片段，防止越权访问父目录文件。
 * <p>
 * 攻击示例：
 * <ul>
 *   <li>{@code ../../etc/passwd} — 尝试读取系统密码文件</li>
 *   <li>{@code ../../../home/user/.ssh/id_rsa} — 窃取 SSH 私钥</li>
 *   <li>{@code ..\\windows\\system32} — Windows 路径遍历</li>
 * </ul>
 * <p>
 * 安全意义：即使 Tool 已注册，其参数仍可能被注入恶意路径。
 * 本层是参数级别的二次校验。
 */
public class PathGuardFilter implements SecurityFilterChain {

    /** 路径遍历攻击特征正则（匹配 .. 及其变体） */
    private static final String PATH_TRAVERSAL_PATTERN = "(\\.\\.[\\\\/])|(\\.\\.[\\\\/]?$)";

    @Override
    public SecurityResult check(String toolName, String args, SecurityFilterChain nextChain) {
        // 检测路径遍历模式
        if (args != null && args.matches(".*" + PATH_TRAVERSAL_PATTERN + ".*")) {
            return SecurityResult.deny(
                    "检测到路径遍历攻击模式：参数中包含 '..'，可能存在越权访问风险",
                    "PathGuardFilter");
        }

        // 额外检查：纯 ".." 片段（如参数本身为 ".."）
        if (args != null) {
            String[] segments = args.split("[\\\\/]");
            for (String segment : segments) {
                if ("..".equals(segment.trim())) {
                    return SecurityResult.deny(
                            "参数路径片段包含 '..'，存在路径遍历风险",
                            "PathGuardFilter");
                }
            }
        }

        // 通过检查，委托给下一层过滤器
        if (nextChain != null) {
            return nextChain.check(toolName, args, null);
        }
        return SecurityResult.allow("PathGuardFilter");
    }
}
