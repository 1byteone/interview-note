package com.mewcode.security;

import java.util.Set;

/**
 * 第三层过滤器：危险命令扫描。
 * <p>
 * 职责：检测 Tool 参数中是否包含已知的危险命令模式。
 * 使用关键词黑名单匹配，拦截高风险 shell 操作。
 * <p>
 * 拦截的危险模式：
 * <ul>
 *   <li>{@code rm -rf /} — 递归强制删除根目录</li>
 *   <li>{@code chmod 777} — 赋予全局读写权限</li>
 *   <li>{@code wget | sh} — 下载并执行远程脚本（水坑攻击）</li>
 *   <li>{@code curl | bash} — 同上，bash 变体</li>
 *   <li>{@code mkfs} — 格式化磁盘（数据销毁）</li>
 *   <li>{@code dd if=} — 底层磁盘写入（可擦除分区表）</li>
 * </ul>
 * <p>
 * 安全意义：即使路径安全，命令本身也可能造成不可逆损害。
 * 本层是命令级别的内容审计。
 */
public class CommandScannerFilter implements SecurityFilterChain {

    /** 危险命令关键词黑名单（不区分大小写） */
    private static final Set<String> DANGEROUS_PATTERNS = Set.of(
            "rm -rf /",           // 递归强制删除根目录
            "rm -rf /*",          // rm -rf /* 变体
            "chmod 777",          // 全局开放权限（安全隐患）
            "wget | sh",          // 下载并执行（水坑攻击典型手法）
            "wget | bash",        // wget + bash 变体
            "curl | sh",          // curl + sh 变体
            "curl | bash",        // curl + bash 变体
            "mkfs",               // 格式化磁盘（数据销毁）
            "dd if=",             // 底层磁盘写入（可擦除 MBR）
            "> /dev/sda",         // 直接写入磁盘设备
            ":(){ :|:& };:"       // Fork 炸弹（耗尽系统资源）
    );

    @Override
    public SecurityResult check(String toolName, String args, SecurityFilterChain nextChain) {
        if (args == null) {
            // 参数为空，跳过命令扫描，委托下一层
            if (nextChain != null) {
                return nextChain.check(toolName, args, null);
            }
            return SecurityResult.allow("CommandScannerFilter");
        }

        // 将参数转为小写进行不区分大小写的匹配
        String lowerArgs = args.toLowerCase();

        // 遍历所有危险模式进行匹配
        for (String pattern : DANGEROUS_PATTERNS) {
            if (lowerArgs.contains(pattern.toLowerCase())) {
                return SecurityResult.deny(
                        "检测到危险命令模式：'" + pattern + "'，出于安全考虑已被拦截",
                        "CommandScannerFilter");
            }
        }

        // 通过检查，委托给下一层过滤器
        if (nextChain != null) {
            return nextChain.check(toolName, args, null);
        }
        return SecurityResult.allow("CommandScannerFilter");
    }
}
