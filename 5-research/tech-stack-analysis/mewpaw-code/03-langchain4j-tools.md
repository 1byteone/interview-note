# 03 · LangChain4j 工具调用：Agent 的"手脚"

> Agent 的"大脑"是 LLM，"手脚"就是工具。mewpaw-code 没有使用 LangChain4j 的 `@Tool` 注解声明式定义，而是自建了 ToolRegistry 体系，提供 6 种内置工具 + MCP 扩展能力。
>
> **对应模块：** `mewcode-tools` → `com.mewcode.tools`

---

## 一、基础概念

### 1.1 LangChain4j 工具调用机制

LangChain4j 提供了完整的工具调用支持，核心 API 包括：

| 组件 | 角色 | 说明 |
|------|------|------|
| `@Tool` 注解 | 声明式工具定义 | 标注方法为工具，`@P` 描述参数 |
| `ToolSpecification` | 工具规范 | 包含 name/description/parameters JSON Schema |
| `ToolExecutionRequest` | 工具调用请求 | LLM 返回的调用指令（id/name/arguments） |
| `ToolExecutionResultMessage` | 工具结果消息 | 包装执行结果回填到消息列表 |

**LangChain4j 标准用法（本项目未使用）：**

```java
// ① 使用 @Tool 注解声明工具
class MyTools {
    @Tool("Execute a bash command")
    public String executeBash(
            @P("command") String command,
            @P(value = "timeout", required = false) int timeout
    ) {
        // 执行逻辑...
    }
}

// ② 将工具绑定到 LLM
ChatLanguageModel model = OpenAiChatModel.builder()
        .apiKey("...")
        .modelName("gpt-4")
        .build();

// ③ 创建带工具的模型
// LangChain4j 会扫描 @Tool 方法，自动生成 ToolSpecification
// LLM 调用时自动解析 ToolExecutionRequest 并执行
```

### 1.2 为什么本项目自建工具体系

| 维度 | LangChain4j `@Tool` | 自建 ToolRegistry |
|------|---------------------|-------------------|
| 安全性 | 无内置安全机制 | 每个工具调用都经过 5 层安全链 |
| 灵活性 | 方法级别绑定，动态注册困难 | ConcurrentHashMap，支持运行时注册/注销 |
| 元数据 | 只能描述 name/description/params | 额外携带 dangerous / version / prompt 模板 |
| 扩展性 | 仅支持 Java 方法作为工具 | 支持 MCP 协议远程工具 |

---

## 二、进阶机制

### 2.1 ToolRegistry 实现

**ToolDescriptor（工具元数据 Record）：**

```java
// 工具描述 Record：name / description / parameters / dangerous / version
// 用于生成 LLM 可读的工具列表和驱动安全检查
public record ToolDescriptor(
        String name,                          // 工具名称，如 "bash"
        String description,                   // 工具功能描述，如 "Execute shell commands"
        Map<String, ParameterSchema> parameters, // 参数 Schema
        boolean dangerous,                    // 是否危险工具（bash = true）
        String version                        // 工具版本号
) {
    // 将工具描述格式化为 LLM 可读的提示文本
    // 例如: "bash - Execute shell commands (dangerous)"
    // 参数: command(string, required) - The command to execute
    public String toPromptString() {
        StringBuilder sb = new StringBuilder();
        sb.append(name).append(" - ").append(description);
        if (dangerous) {
            sb.append(" (dangerous)");
        }
        sb.append("\n");
        // 逐个参数追加描述
        for (Map.Entry<String, ParameterSchema> entry : parameters.entrySet()) {
            sb.append("  ").append(entry.getKey()).append(": ")
              .append(entry.getValue().type()).append(" ")
              .append(entry.getValue().required() ? "(required)" : "(optional)")
              .append(" - ").append(entry.getValue().description())
              .append("\n");
        }
        return sb.toString();
    }
}

// 参数 Schema Record：描述工具参数的元数据
public record ParameterSchema(
        String name,        // 参数名
        String type,        // 参数类型（string / integer / boolean / array）
        String description, // 参数描述
        boolean required,   // 是否必填
        List<String> enums  // 枚举值（可选）
) {}
```

**ToolRegistry（工具注册中心）：**

```java
package com.mewcode.tools;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工具注册中心：管理所有内置工具和外部工具的注册与查找
 * 使用 ConcurrentHashMap 保证线程安全
 */
public class ToolRegistry {

    // 工具描述符缓存：工具名 → ToolDescriptor
    // ConcurrentHashMap 保证并发注册/注销的安全性
    private final ConcurrentHashMap<String, ToolDescriptor> descriptors = new ConcurrentHashMap<>();

    // 工具执行器缓存：工具名 → ToolExecutor
    // 与 descriptors 对应的执行器，分离元数据和执行逻辑
    private final ConcurrentHashMap<String, ToolExecutor> executors = new ConcurrentHashMap<>();

    /**
     * 注册工具：同时注册描述符和执行器
     * @param descriptor 工具描述（元数据）
     * @param executor 工具执行器（执行逻辑）
     */
    public void register(ToolDescriptor descriptor, ToolExecutor executor) {
        // 注册前检查：名称不能为空
        if (descriptor.name() == null || descriptor.name().isBlank()) {
            throw new IllegalArgumentException("Tool name must not be blank");
        }
        // 注册描述符和执行器到对应的 Map
        descriptors.put(descriptor.name(), descriptor);
        executors.put(descriptor.name(), executor);
    }

    /**
     * 注销工具：同时移除描述符和执行器
     * @param name 工具名称
     */
    public void unregister(String name) {
        descriptors.remove(name);
        executors.remove(name);
    }

    /**
     * 获取工具描述符
     * @param name 工具名称
     * @return 工具描述符，未注册返回 null
     */
    public ToolDescriptor getDescriptor(String name) {
        return descriptors.get(name);
    }

    /**
     * 获取工具执行器
     * @param name 工具名称
     * @return 工具执行器，未注册返回 null
     */
    public ToolExecutor getExecutor(String name) {
        return executors.get(name);
    }

    /**
     * 获取所有工具描述符
     * @return 所有已注册的工具描述符集合
     */
    public Collection<ToolDescriptor> getAllDescriptors() {
        return descriptors.values();
    }

    /**
     * 检查工具是否已注册
     * @param name 工具名称
     * @return 是否已注册
     */
    public boolean hasTool(String name) {
        return descriptors.containsKey(name);
    }

    /**
     * 获取已注册工具数量
     * @return 工具数量
     */
    public int size() {
        return descriptors.size();
    }

    /**
     * 生成 System Prompt 中的工具列表文本
     * 逐行调用 toPromptString()，拼装成 LLM 可读的工具描述
     * @return 工具列表文本
     */
    public String toToolListPrompt() {
        StringBuilder sb = new StringBuilder("Available tools:\n");
        for (ToolDescriptor descriptor : descriptors.values()) {
            sb.append(descriptor.toPromptString()).append("\n");
        }
        return sb.toString();
    }
}
```

**ToolExecutor（工具执行器接口）：**

```java
package com.mewcode.tools;

/**
 * 工具执行器接口：所有工具的执行逻辑
 * 与 ToolDescriptor 分离，将"元数据"和"执行逻辑"解耦
 */
public interface ToolExecutor {

    /**
     * 执行工具
     * @param descriptor 工具描述（用于获取配置信息）
     * @param arguments 参数 JSON 字符串
     * @return 工具执行结果字符串
     * @throws ToolExecutionException 工具执行异常
     */
    String execute(ToolDescriptor descriptor, String arguments) throws ToolExecutionException;
}
```

### 2.2 6 种内置工具

| 工具 | 危险标记 | 核心功能 | 安全措施 |
|------|---------|---------|---------|
| bash | dangerous=true | 执行 shell 命令 | 危险命令扫描 + 用户确认 |
| read_file | 无 | 读取文件内容 | 路径守卫检查 |
| write_file | 无 | 写入文件内容 | 路径守卫检查 |
| edit_file | 无 | 编辑文件内容 | 路径守卫检查 |
| glob | 无 | 文件搜索 | 路径守卫检查 |
| grep | 无 | 内容搜索 | 路径守卫检查 |

**BashTool 核心实现：**

```java
package com.mewcode.tools.impl;

import com.mewcode.tools.ToolDescriptor;
import com.mewcode.tools.ToolExecutor;
import com.mewcode.tools.ToolExecutionException;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Bash 工具：执行 shell 命令
 * 跨平台支持（Windows cmd /c，Linux /bin/bash -c）
 * 虚拟线程执行 + 超时控制 + 输出截断
 */
public class BashTool implements ToolExecutor {

    // 虚拟线程执行器：每次 submit 创建一个虚拟线程
    // 适合 IO 密集型任务（等待子进程退出）
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    // 默认超时时间：60 秒
    private static final int DEFAULT_TIMEOUT_SECONDS = 60;
    // 最大输出字符数：50000 chars
    private static final int MAX_OUTPUT_CHARS = 50000;

    @Override
    public String execute(ToolDescriptor descriptor, String arguments) throws ToolExecutionException {
        // 解析参数 JSON，提取 command 和 timeout
        // 实际使用 Jackson 解析，这里简化表示
        String command = extractCommand(arguments);
        int timeout = extractTimeout(arguments, DEFAULT_TIMEOUT_SECONDS);

        try {
            // ① 构建 ProcessBuilder
            // 注意：跨平台适配
            // Windows 使用 cmd /c
            // Linux/Unix 使用 /bin/bash -c
            ProcessBuilder processBuilder = new ProcessBuilder();
            if (System.getProperty("os.name").toLowerCase().contains("win")) {
                // Windows 平台：cmd /c "command"
                processBuilder.command("cmd", "/c", command);
            } else {
                // Linux 平台：/bin/bash -c "command"
                processBuilder.command("/bin/bash", "-c", command);
            }

            // ② 启动进程
            Process process = processBuilder.start();

            // ③ 在虚拟线程中异步执行，等待进程退出
            // 虚拟线程在 get() 阻塞时自动让出载体线程
            Future<String> future = executor.submit(() -> {
                // 读取进程输出流
                StringBuilder output = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        // 输出截断检查
                        if (output.length() + line.length() + 1 > MAX_OUTPUT_CHARS) {
                            // 超过最大长度，截断并提示
                            output.append("\n...(output truncated at ")
                                  .append(MAX_OUTPUT_CHARS).append(" chars)");
                            break;
                        }
                        output.append(line).append("\n");
                    }
                }
                // 等待进程退出并获取退出码
                int exitCode = process.waitFor();
                // 返回退出码 + 输出内容
                return "exit code: " + exitCode + "\n" + output.toString();
            });

            // ④ 带超时等待结果
            // 超时未返回 → 强制销毁进程
            return future.get(timeout, TimeUnit.SECONDS);

        } catch (java.util.concurrent.TimeoutException e) {
            // 超时处理：强制销毁进程
            throw new ToolExecutionException(
                    "Command timed out after " + timeout + " seconds");
        } catch (Exception e) {
            // 其他异常包装
            throw new ToolExecutionException("Command execution failed: " + e.getMessage());
        }
    }
}
```

**FileTool 基类（以 read_file 为例）：**

```java
package com.mewcode.tools.impl;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 文件工具基类：提供路径安全检查和规范化
 * 所有文件类工具（read/write/edit/glob/grep）都继承此基类
 */
public abstract class FileTool implements ToolExecutor {

    // 允许操作的工作目录
    protected final Path workDir;

    public FileTool(String workDir) {
        // 规范化工作目录路径
        this.workDir = Paths.get(workDir).normalize().toAbsolutePath();
    }

    /**
     * 路径安全检查：确保目标路径在工作目录内
     * 防止路径遍历攻击（如 ../../etc/passwd）
     * @param targetPath 用户传入的目标路径
     * @return 规范化后的安全路径
     * @throws SecurityException 路径越权时抛出
     */
    protected Path resolveSecurePath(String targetPath) {
        // ① 规范化目标路径：去除 . 和 .. 等相对路径
        Path resolved = workDir.resolve(targetPath).normalize();

        // ② 检查规范化后的路径是否以工作目录开头
        // 如果 targetPath 包含 ../../etc/passwd，规范化后不在 workDir 下
        if (!resolved.startsWith(workDir)) {
            throw new SecurityException(
                    "Path traversal detected: " + targetPath + " resolved to " + resolved);
        }

        return resolved;
    }
}
```

**ReadFileTool 实现：**

```java
package com.mewcode.tools.impl;

import com.mewcode.tools.ToolDescriptor;
import com.mewcode.tools.ToolExecutionException;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 读取文件工具
 * 继承 FileTool 基类，自动获得路径安全检查能力
 */
public class ReadFileTool extends FileTool {

    public ReadFileTool(String workDir) {
        super(workDir);
    }

    @Override
    public String execute(ToolDescriptor descriptor, String arguments) throws ToolExecutionException {
        try {
            // 从参数 JSON 中提取文件路径
            String filePath = extractFilePath(arguments);
            // 安全检查：路径规范化
            Path safePath = resolveSecurePath(filePath);

            // 检查文件是否存在
            if (!Files.exists(safePath)) {
                throw new ToolExecutionException("File not found: " + safePath);
            }
            // 检查是否为目录
            if (Files.isDirectory(safePath)) {
                throw new ToolExecutionException("Path is a directory: " + safePath);
            }

            // 读取文件内容
            return Files.readString(safePath);
        } catch (SecurityException e) {
            // 路径越权：直接抛出
            throw new ToolExecutionException("Security error: " + e.getMessage());
        } catch (Exception e) {
            throw new ToolExecutionException("Failed to read file: " + e.getMessage());
        }
    }
}
```

### 2.3 工具注册流程

**工具初始化代码：**

```java
// 在 Spring Boot 启动阶段初始化 ToolRegistry
// 注册 6 种内置工具
ToolRegistry toolRegistry = new ToolRegistry();

// 注册 Bash 工具（危险标记 = true）
toolRegistry.register(
    new ToolDescriptor(
        "bash",                          // name
        "Execute shell commands",        // description
        Map.of(                          // parameters
            "command", new ParameterSchema("command", "string", "The command to execute", true, null),
            "timeout", new ParameterSchema("timeout", "integer", "Timeout in seconds", false, null)
        ),
        true,                            // dangerous = true
        "1.0.0"                          // version
    ),
    new BashTool()                       // executor
);

// 注册文件工具（危险标记 = false，但受路径守卫保护）
toolRegistry.register(
    new ToolDescriptor("read_file", "Read file content",    params, false, "1.0.0"),
    new ReadFileTool(workDir)
);
toolRegistry.register(
    new ToolDescriptor("write_file", "Write file content",  params, false, "1.0.0"),
    new WriteFileTool(workDir)
);
toolRegistry.register(
    new ToolDescriptor("edit_file", "Edit file content",    params, false, "1.0.0"),
    new EditFileTool(workDir)
);
toolRegistry.register(
    new ToolDescriptor("glob", "Search files by pattern",   params, false, "1.0.0"),
    new GlobTool(workDir)
);
toolRegistry.register(
    new ToolDescriptor("grep", "Search content in files",   params, false, "1.0.0"),
    new GrepTool(workDir)
);
```

### 2.4 MCP 工具扩展

除了 6 种内置工具，项目还支持通过 MCP 协议调用外部工具：

```java
// 在 AgentLoop 中，工具执行逻辑同时检查内置工具和 MCP 工具
// McpClient 通过 StdioTransport 与 MCP 服务器通信
// 协议：JSON-RPC 2.0，版本 2024-11-05

// 连接流程：
// 1. initialize → 发送协议版本和能力声明
// 2. notifications/initialized → 服务端就绪通知
// 3. tools/list → 获取 MCP 服务器提供的工具列表
// 4. tools/call → 调用具体 MCP 工具

// 这些 MCP 工具会被动态注册到 ToolRegistry 中
// 与内置工具统一通过 ToolRegistry.getExecutor() 获取
```

---

## 三、面试题

**Q1：本项目为什么没有使用 LangChain4j 的 @Tool 注解，而是自建了 ToolRegistry？**

A：@Tool 注解适合简单场景，但本项目需要：1）每个工具调用都经过 5 层安全链检查，@Tool 无法内嵌安全逻辑；2）运行时动态注册/注销工具（MCP 工具可能随时加入），@Tool 是编译期静态绑定；3）额外携带 dangerous 标记和版本信息，@Tool 不支持这些元数据。

**Q2：ToolRegistry 为什么用 ConcurrentHashMap？**

A：AgentLoop 是多线程环境（虚拟线程执行多个工具），ToolRegistry 可能被多个线程同时访问。ConcurrentHashMap 提供高效的并发读（无锁）和安全的并发写（分段锁），保证注册和查找的线程安全。

**Q3：BashTool 为什么使用虚拟线程执行器？**

A：Bash 命令执行是典型的 IO 等待场景——提交任务后大多数时间在等待子进程退出。虚拟线程在 `Future.get()` 阻塞时自动让出载体线程给其他任务使用，不会浪费 OS 线程。如果使用平台线程池，需要配置合适的池大小，且大量并发等待时线程池可能被占满导致级联超时。

**Q4：Bash 工具的超时机制是怎么实现的？**

A：使用 `Future.get(timeout, TimeUnit)` 带超时等待。如果超时，执行 `process.destroyForcibly()` 强制销毁子进程。超时时间默认 60 秒，可通过参数自定义。这避免了 Bash 命令永远挂起的问题。

**Q5：FileTool 的路径安全是怎么保证的？**

A：通过 `resolveSecurePath()` 方法：1）用 `workDir.resolve(targetPath).normalize()` 规范化路径，去除 `.` 和 `..`；2）检查规范化后的路径是否以 `workDir` 开头。如果传入 `../../etc/passwd`，规范化后不在工作目录下，直接抛出 SecurityException。

---

## 四、总结

| 设计点 | 实现 | 价值 |
|--------|------|------|
| 工具注册 | ToolRegistry (ConcurrentHashMap) | 线程安全 + 动态注册 |
| 工具描述 | ToolDescriptor (Record) | 不可变元数据 + dangerous 标记 |
| 工具执行 | ToolExecutor 接口 | 策略模式，执行逻辑与元数据解耦 |
| Bash 执行 | 虚拟线程 + 超时 + 截断 | 安全可控的命令执行 |
| 文件安全 | 路径规范化 + 守卫检查 | 防止路径遍历攻击 |
| 扩展能力 | MCP 协议动态注册 | 第三方工具无缝集成 |

**核心收获：** 工具层是 Agent 能力的"手脚"，mewpaw-code 通过自建 ToolRegistry 体系，在 LangChain4j 工具调用 API 的基础上增加了安全、元数据、动态注册等能力，让 Agent 的执行既灵活又可控。

---

## 参考资料

- LangChain4j Tools API: https://docs.langchain4j.dev/tutorials/tools
- LangChain4j ToolSpecification: https://docs.langchain4j.dev/apis/tool-specification
- MCP Specification: https://spec.modelcontextprotocol.io/