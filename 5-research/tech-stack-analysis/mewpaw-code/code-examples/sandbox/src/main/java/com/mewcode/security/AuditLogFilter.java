package com.mewcode.security;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 第五层过滤器：审计日志记录。
 * <p>
 * 职责：无论操作最终是否被允许，均记录完整的审计日志条目。
 * 本层位于过滤链最末端，确保所有请求都被记录（包括被前四层拒绝的请求）。
 * <p>
 * 审计日志包含：
 * <ul>
 *   <li>时间戳（精确到毫秒）</li>
 *   <li>工具名称</li>
 *   <li>参数摘要（截断超长内容）</li>
 *   <li>最终判定（允许/拒绝）</li>
 *   <li>拒绝原因（若有）</li>
 *   <li>做出判定的过滤器名称</li>
 * </ul>
 * <p>
 * 安全意义：审计日志是合规性和事后追溯的基础。
 * 生产环境应将日志持久化到数据库或日志聚合系统（如 ELK）。
 */
public class AuditLogFilter implements SecurityFilterChain {

    /** 日志条目格式化器 */
    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    /** 存储所有审计日志条目（线程安全） */
    private final List<String> auditLog = Collections.synchronizedList(new ArrayList<>());

    /** 参数摘要最大长度（超过此长度截断并附加省略号） */
    private static final int MAX_ARGS_SUMMARY_LEN = 100;

    @Override
    public SecurityResult check(String toolName, String args, SecurityFilterChain nextChain) {
        // 审计日志层是链尾（nextChain 应为 null）
        // 直接记录一条"已放行"的日志
        String logEntry = formatLogEntry(toolName, args, true, null, "AuditLogFilter");
        auditLog.add(logEntry);

        // 同时打印到标准输出（生产环境应替换为 Logger）
        System.out.println("[审计日志] " + logEntry);

        // 审计日志层始终放行（它只记录，不拦截）
        return SecurityResult.allow("AuditLogFilter");
    }

    /**
     * 记录一条被拒绝的操作日志（供前四层过滤器调用）。
     *
     * @param toolName     工具名称
     * @param args         工具参数
     * @param reason       拒绝原因
     * @param filterName   拒绝发生的过滤器名称
     */
    public void logDenied(String toolName, String args, String reason, String filterName) {
        String logEntry = formatLogEntry(toolName, args, false, reason, filterName);
        auditLog.add(logEntry);
        System.out.println("[审计日志-拒绝] " + logEntry);
    }

    /**
     * 获取所有审计日志条目（不可变视图）。
     *
     * @return 审计日志列表
     */
    public List<String> getAuditLog() {
        return List.copyOf(auditLog);
    }

    /**
     * 获取审计日志条目总数。
     *
     * @return 日志条目数量
     */
    public int getLogCount() {
        return auditLog.size();
    }

    /**
     * 格式化一条审计日志条目。
     */
    private String formatLogEntry(String toolName, String args,
                                  boolean allowed, String reason, String filterName) {
        String timestamp = LocalDateTime.now().format(FORMATTER);
        String argsSummary = truncate(args, MAX_ARGS_SUMMARY_LEN);

        if (allowed) {
            return String.format("[%s] ALLOWED | tool=%s | args=%s | filter=%s",
                    timestamp, toolName, argsSummary, filterName);
        } else {
            return String.format("[%s] DENIED  | tool=%s | args=%s | reason=%s | filter=%s",
                    timestamp, toolName, argsSummary, reason, filterName);
        }
    }

    /**
     * 截断过长文本。
     */
    private String truncate(String text, int maxLen) {
        if (text == null) return "null";
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...";
    }
}
