package com.mewcode.tool;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

/**
 * Bash 执行工具 — 允许 Agent 在受控环境中执行 shell 命令。
 * <p>
 * <strong>安全说明：</strong>本示例仅用于演示 ReAct 循环机制，
 * 生产环境应叠加 {@link com.mewcode.security} 沙箱过滤链。
 * <p>
 * 执行流程：
 * <ol>
 *   <li>解析 JSON 参数，提取 "command" 字段</li>
 *   <li>通过 {@link Runtime#exec(String[])} 启动子进程</li>
 *   <li>合并 stdout + stderr，超时自动终止（默认 30 秒）</li>
 *   <li>返回合并输出文本</li>
 * </ol>
 */
public class BashTool implements Tool {

    /** 子进程最大等待时间（秒） */
    private static final int TIMEOUT_SECONDS = 30;

    @Override
    public String name() {
        return "bash";
    }

    @Override
    public String description() {
        return "在系统 shell 中执行命令并返回输出。参数: {\"command\": \"ls -la\"}";
    }

    @Override
    public String execute(String argsJson) {
        // 简易参数解析：从 JSON 中提取 command 字段值
        String command = extractCommand(argsJson);
        if (command == null || command.isBlank()) {
            return "[错误] 未提供 command 参数";
        }

        try {
            // 启动子进程执行 shell 命令
            ProcessBuilder pb = new ProcessBuilder("bash", "-c", command);
            pb.redirectErrorStream(true); // 合并 stderr 到 stdout
            Process process = pb.start();

            // 读取子进程输出（合并 stdout + stderr）
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            // 等待子进程结束，超时则强制终止
            boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return "[错误] 命令执行超时（" + TIMEOUT_SECONDS + "秒）已强制终止\n" + output;
            }

            int exitCode = process.exitValue();
            String result = output.toString().trim();
            if (exitCode != 0) {
                return "[exit=" + exitCode + "]\n" + result;
            }
            return result.isEmpty() ? "[命令执行成功，无输出]" : result;

        } catch (Exception e) {
            return "[异常] " + e.getClass().getSimpleName() + ": " + e.getMessage();
        }
    }

    /**
     * 从简易 JSON 字符串中提取 "command" 的值。
     * <p>
     * 仅用于示例，生产环境应使用 Jackson/Gson。
     *
     * @param json 原始 JSON 字符串
     * @return command 的值，解析失败返回 null
     */
    private String extractCommand(String json) {
        if (json == null) return null;
        // 匹配 "command": "..." 模式
        int idx = json.indexOf("\"command\"");
        if (idx < 0) return null;
        int colonIdx = json.indexOf(':', idx + 9);
        if (colonIdx < 0) return null;
        int startQuote = json.indexOf('"', colonIdx + 1);
        if (startQuote < 0) return null;
        int endQuote = json.indexOf('"', startQuote + 1);
        if (endQuote < 0) return null;
        return json.substring(startQuote + 1, endQuote);
    }
}
