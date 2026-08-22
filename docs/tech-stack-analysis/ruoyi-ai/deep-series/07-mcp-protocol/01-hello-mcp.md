# MCP 协议入门：从零搭建智能体工具调用框架

> 本文是 ruoyi-ai 项目技术栈深度剖析系列的第7篇，面向 Java 后端开发者，旨在帮助读者从零搭建一个 MCP（Model Context Protocol）协议应用，涵盖 MCP Server/Client 架构、SSE 传输机制、工具动态发现与调用、安全校验机制，并对照分析 ruoyi-ai 项目中的 MCP 协议真实实现。

---

## 一、项目背景：该技术栈在项目中的角色

### 1.1 为什么需要 MCP 协议

在智能体应用中，一个核心需求是"让 LLM 能够调用外部工具"。例如，智能体需要读取文件、执行脚本、调用 API 等。然而，实现这一能力面临几个关键挑战：

- **协议不统一**：每个智能体框架有自己的工具调用方式，缺乏标准化协议
- **跨语言互通**：Java 智能体可能需要调用 Python 脚本，不同语言之间需要统一的通信协议
- **动态发现**：工具列表需要动态扩展，不能每次加工具都修改代码
- **安全控制**：工具调用涉及文件系统、网络等敏感操作，需要精细的权限管理

MCP（Model Context Protocol）正是为解决这些问题而生。它由 Anthropic 提出，是一个开放标准协议，定义了 LLM 与外部工具之间的通信方式。MCP 的核心思想是：**将工具调用抽象为 Client <-> Server 的标准化通信，LLM 通过 MCP Client 发现和调用 MCP Server 提供的工具**。

### 1.2 在 ruoyi-ai 项目中的位置

在 ruoyi-ai 的 AI 层中，MCP 协议位于 **Skills Agent** 的核心位置，是智能体与外部工具之间的桥梁：

```
Skills Agent
    ↓
McpToolManager（MCP 工具管理器）
    ├── FileSystemMcpServer（文件系统工具：读文件、写文件、列出目录）
    ├── PythonScriptMcpServer（Python 脚本执行工具）
    └── SseMcpClientManager（远程 MCP 服务器管理）
        ├── listTools → 动态发现可用工具
        └── callTool  → 调用工具执行
    ↓
McpToolCallService（MCP 工具调用服务，与 LangChain4j 集成）
```

具体来说，MCP 协议在项目中承担了以下职责：

- **文件系统操作**：通过 FileSystem MCP Server 提供读写文件、列出目录等能力
- **脚本执行**：通过 PythonScript MCP Server 执行 Python 脚本
- **远程 MCP 集成**：通过 SSE 传输连接远程 MCP 服务器，扩展能力边界
- **安全校验**：PathSecurityValidator 防止路径遍历攻击，ScriptSecuritySandbox 阻止危险操作

### 1.3 本文目标

本文的目标是帮助读者从零搭建一个最简的 MCP 协议应用，实现"MCP Server 注册工具 -> MCP Client 发现工具 -> 调用工具执行 -> 返回结果"的完整流程。通过这个最小可行示例，读者将理解：

1. MCP Server 和 MCP Client 的核心架构设计
2. 三种传输机制（SSE、stdio、Streamable HTTP）的适用场景
3. 工具动态发现（listTools）和调用（callTool）的完整流程
4. 安全校验机制的设计思路

---

## 二、核心概念：3个，用生活类比解释

### 概念 1：MCP Server 和 MCP Client —— 就像"餐厅和顾客"

**生活类比**：想象你去一家餐厅吃饭。餐厅（MCP Server）有一份菜单，上面列出了所有能做的菜（工具列表）。你看菜单（listTools），然后点菜（callTool），厨师做好菜后端上来（返回结果）。不同的餐厅擅长不同的菜系（文件系统操作、脚本执行、API 调用），你可以根据需要选择不同的餐厅。

**技术映射**：MCP 协议定义了 Client 和 Server 之间的标准化通信：

- **MCP Server**：提供一组工具，每个工具有名称、描述、输入参数 Schema 和执行逻辑。Server 可以独立部署，用任何语言实现（Java、Python、Node.js 等），通过标准化协议暴露工具。
- **MCP Client**：连接到 Server，发现可用工具，调用工具执行。Client 通常嵌入在 LLM 应用中，将 MCP 工具作为 LLM 的 Tool 注册。
- **工具协议**：工具通过 JSON Schema 定义输入参数，Client 和 Server 之间通过 JSON-RPC 消息通信。

**关键点**：MCP 协议的核心价值在于标准化。无论底层工具是什么语言实现的，无论 Server 部署在哪里，Client 都通过统一的协议与 Server 通信。

### 概念 2：SSE 传输 —— 就像"无线对讲机"

**生活类比**：想象两个人使用无线对讲机通信。一个人按下通话键说话（发送请求），另一个人听到后回复（返回响应）。如果消息很长，可以分段发送（流式传输）。SSE（Server-Sent Events）就像这种单向通信——服务器可以持续向客户端推送消息，客户端不需要反复询问"有没有新消息"。

**技术映射**：SSE 是 MCP 协议的主要传输机制之一：

- **单向推送**：服务器主动向客户端推送事件，客户端通过监听事件流接收消息
- **长连接**：建立一次连接后，可以持续接收多个事件，不需要重复建立连接
- **流式响应**：适合大文件读取、长时间运行的任务等需要流式传输的场景
- **MCP 中的 SSE**：Client 通过 HTTP POST 发送请求，Server 通过 SSE 推送响应

**对比其他传输方式**：
- **SSE**：适合远程通信，需要 HTTP 服务器，支持流式响应
- **stdio**：适合本地进程间通信，通过标准输入输出传输 JSON-RPC 消息
- **Streamable HTTP**：适合需要双向流式传输的场景，如流式对话

### 概念 3：工具动态发现 —— 就像"扫码点餐"

**生活类比**：想象你去一家餐厅，桌上有一个二维码。扫码后，手机显示餐厅的完整菜单（listTools），你看到菜单上新加了一道菜，不需要重新印刷菜单，手机上自动更新了。这就是"动态发现"——你不需要提前知道餐厅有什么菜，扫码后就能看到最新的菜单。

**技术映射**：工具动态发现是 MCP 协议的核心能力之一：

- **listTools**：Client 向 Server 发送 listTools 请求，Server 返回所有可用工具的列表，每个工具包含名称、描述、输入参数 Schema
- **动态扩展**：Server 可以随时添加新工具，Client 下次 listTools 时就能发现
- **无需代码修改**：新增工具不需要修改 Client 代码，只需要在 Server 端注册新的工具定义
- **Schema 驱动**：输入参数通过 JSON Schema 定义，Client 可以根据 Schema 生成正确的参数

---

## 三、从零搭建：完整代码

### 3.1 项目结构

```
mcp-demo/
├── pom.xml
├── src/main/java/com/mcpdemo/
│   ├── McpDemoApplication.java        # 启动类
│   ├── server/
│   │   ├── FileSystemMcpServer.java   # 文件系统 MCP Server
│   │   └── PythonScriptMcpServer.java # Python 脚本 MCP Server
│   ├── client/
│   │   ├── McpClientManager.java      # MCP Client 管理器
│   │   └── McpToolCallService.java    # MCP 工具调用服务
│   ├── security/
│   │   ├── PathSecurityValidator.java # 路径安全校验器
│   │   └── ScriptSecuritySandbox.java # 脚本安全沙箱
│   └── controller/
│       └── McpController.java         # REST 控制器
└── src/main/resources/
    └── application.yml                # 配置文件
```

### 3.2 pom.xml —— 基础依赖配置

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.5.0</version>
        <relativePath/>
    </parent>

    <groupId>com.mcpdemo</groupId>
    <artifactId>mcp-demo</artifactId>
    <version>1.0.0</version>
    <name>mcp-demo</name>
    <description>MCP 协议应用示例</description>

    <properties>
        <java.version>21</java.version>
        <langchain4j.version>1.13.0</langchain4j.version>
    </properties>

    <dependencies>
        <!-- Spring Boot Web -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>

        <!-- LangChain4j 核心依赖 -->
        <dependency>
            <groupId>dev.langchain4j</groupId>
            <artifactId>langchain4j</artifactId>
            <version>${langchain4j.version}</version>
        </dependency>

        <!-- LangChain4j Spring Boot Starter -->
        <dependency>
            <groupId>dev.langchain4j</groupId>
            <artifactId>langchain4j-spring-boot-starter</artifactId>
            <version>${langchain4j.version}</version>
        </dependency>

        <!-- Jackson for JSON processing -->
        <dependency>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-databind</artifactId>
        </dependency>

        <!-- 测试 -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

### 3.3 application.yml —— 应用配置

```yaml
server:
  port: 8080

spring:
  application:
    name: mcp-demo

# MCP 配置
mcp:
  filesystem:
    # 允许操作的根目录（白名单）
    allowed-roots:
      - /tmp/mcp-demo
      - ./data
    # 禁止访问的路径模式
    forbidden-patterns:
      - "**/etc/**"
      - "**/sys/**"
      - "**/.env"
      - "**/config*.yml"
  script:
    # 脚本最大长度（字节）
    max-script-length: 102400
    # 允许的 Python 库
    allowed-libraries:
      - json
      - math
      - datetime
      - csv
    # 禁止的 Python 操作
    dangerous-patterns:
      - "import os;"
      - "import subprocess"
      - "eval("
      - "exec("
      - "__import__"
```

### 3.4 核心代码实现

#### 3.4.1 MCP 工具定义 —— 核心数据结构

```java
package com.mcpdemo.server;

import java.util.Map;

/**
 * MCP 工具定义 —— 描述一个 MCP 工具的元信息
 *
 * 每个工具包含：
 * - name：工具名称，Client 通过名称调用工具
 * - description：工具描述，LLM 通过描述判断是否使用该工具
 * - inputSchema：输入参数 Schema（JSON Schema 格式），定义参数类型和约束
 * - handler：工具执行逻辑
 */
public class McpTool {

    private final String name;
    private final String description;
    private final Map<String, Object> inputSchema;
    private final ToolHandler handler;

    public McpTool(String name, String description,
                   Map<String, Object> inputSchema, ToolHandler handler) {
        this.name = name;
        this.description = description;
        this.inputSchema = inputSchema;
        this.handler = handler;
    }

    public String getName() { return name; }
    public String getDescription() { return description; }
    public Map<String, Object> getInputSchema() { return inputSchema; }
    public ToolHandler getHandler() { return handler; }

    /**
     * 工具执行处理器接口
     * 接收参数 Map，返回执行结果 JSON 字符串
     */
    @FunctionalInterface
    public interface ToolHandler {
        String execute(Map<String, Object> arguments);
    }
}
```

#### 3.4.2 文件系统 MCP Server

```java
package com.mcpdemo.server;

import com.mcpdemo.security.PathSecurityValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 文件系统 MCP Server —— 提供文件读写和目录列表功能
 *
 * 注册的工具：
 * 1. read_file：读取文件内容
 * 2. write_file：写入文件内容
 * 3. list_directory：列出目录内容
 *
 * 安全设计：
 * - 操作范围限制在 allowedRoots 白名单目录内
 * - 禁止访问 forbiddenPatterns 匹配的路径
 * - 路径遍历攻击防护（如 ../ 等）
 */
@Component
public class FileSystemMcpServer {

    private static final Logger log = LoggerFactory.getLogger(FileSystemMcpServer.class);

    @Resource
    private PathSecurityValidator pathValidator;

    /**
     * 获取文件系统相关的所有工具
     *
     * 每个工具都包含：
     * - name：工具名称，Client 调用时使用
     * - description：工具描述，帮助 LLM 理解工具用途
     * - inputSchema：输入参数 JSON Schema 定义
     * - handler：工具执行逻辑
     *
     * @return 工具列表
     */
    public List<McpTool> getTools() {
        List<McpTool> tools = new ArrayList<>();

        // 工具 1：read_file —— 读取文件内容
        tools.add(new McpTool(
                "read_file",                    // 工具名称
                "读取指定文件的内容并返回文本",  // 工具描述
                createReadFileSchema(),          // 输入参数 Schema
                this::handleReadFile             // 执行逻辑
        ));

        // 工具 2：write_file —— 写入文件内容
        tools.add(new McpTool(
                "write_file",
                "将内容写入指定文件（如果文件不存在则创建）",
                createWriteFileSchema(),
                this::handleWriteFile
        ));

        // 工具 3：list_directory —— 列出目录内容
        tools.add(new McpTool(
                "list_directory",
                "列出指定目录下的所有文件和子目录",
                createListDirectorySchema(),
                this::handleListDirectory
        ));

        return tools;
    }

    /**
     * 创建 read_file 工具的输入参数 Schema
     *
     * JSON Schema 格式：
     * {
     *   "type": "object",
     *   "properties": {
     *     "filePath": { "type": "string", "description": "文件路径" }
     *   },
     *   "required": ["filePath"]
     * }
     */
    private Map<String, Object> createReadFileSchema() {
        Map<String, Object> properties = new HashMap<>();
        properties.put("filePath", Map.of(
                "type", "string",
                "description", "要读取的文件路径"
        ));

        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", List.of("filePath"));
        return schema;
    }

    /**
     * 创建 write_file 工具的输入参数 Schema
     */
    private Map<String, Object> createWriteFileSchema() {
        Map<String, Object> properties = new HashMap<>();
        properties.put("filePath", Map.of(
                "type", "string",
                "description", "要写入的文件路径"
        ));
        properties.put("content", Map.of(
                "type", "string",
                "description", "要写入的文件内容"
        ));

        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", List.of("filePath", "content"));
        return schema;
    }

    /**
     * 创建 list_directory 工具的输入参数 Schema
     */
    private Map<String, Object> createListDirectorySchema() {
        Map<String, Object> properties = new HashMap<>();
        properties.put("dirPath", Map.of(
                "type", "string",
                "description", "要列出的目录路径"
        ));

        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", List.of("dirPath"));
        return schema;
    }

    /**
     * 处理 read_file 工具调用
     *
     * 流程：
     * 1. 校验路径是否在白名单内
     * 2. 校验路径是否包含禁止模式
     * 3. 读取文件内容
     * 4. 返回结果
     *
     * @param arguments 工具参数
     * @return 文件内容
     */
    private String handleReadFile(Map<String, Object> arguments) {
        String filePath = (String) arguments.get("filePath");
        log.info("MCP 工具调用：read_file, path={}", filePath);

        // 路径安全校验
        pathValidator.validate(filePath);

        try {
            // 读取文件内容
            String content = Files.readString(Path.of(filePath));
            return "文件内容：\n" + content;
        } catch (IOException e) {
            throw new RuntimeException("读取文件失败: " + filePath, e);
        }
    }

    /**
     * 处理 write_file 工具调用
     */
    private String handleWriteFile(Map<String, Object> arguments) {
        String filePath = (String) arguments.get("filePath");
        String content = (String) arguments.get("content");
        log.info("MCP 工具调用：write_file, path={}", filePath);

        // 路径安全校验
        pathValidator.validate(filePath);

        try {
            // 确保父目录存在
            Path path = Path.of(filePath);
            Files.createDirectories(path.getParent());
            // 写入文件内容
            Files.writeString(path, content);
            return "文件写入成功：" + filePath;
        } catch (IOException e) {
            throw new RuntimeException("写入文件失败: " + filePath, e);
        }
    }

    /**
     * 处理 list_directory 工具调用
     */
    private String handleListDirectory(Map<String, Object> arguments) {
        String dirPath = (String) arguments.get("dirPath");
        log.info("MCP 工具调用：list_directory, path={}", dirPath);

        // 路径安全校验
        pathValidator.validate(dirPath);

        try (Stream<Path> paths = Files.list(Path.of(dirPath))) {
            // 收集目录内容列表
            List<String> entries = paths
                    .map(p -> {
                        // 标记是文件还是目录
                        String type = Files.isDirectory(p) ? "[DIR]" : "[FILE]";
                        return type + " " + p.getFileName().toString();
                    })
                    .collect(Collectors.toList());

            return "目录内容：\n" + String.join("\n", entries);
        } catch (IOException e) {
            throw new RuntimeException("列出目录失败: " + dirPath, e);
        }
    }
}
```

#### 3.4.3 Python 脚本 MCP Server

```java
package com.mcpdemo.server;

import com.mcpdemo.security.ScriptSecuritySandbox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Python 脚本 MCP Server —— 执行 Python 脚本并返回结果
 *
 * 注册的工具：
 * 1. execute_python_script：执行 Python 脚本
 *
 * 安全设计：
 * - 脚本长度限制（maxScriptLength）
 * - 危险操作拦截（dangerousPatterns）
 * - 超时控制（30 秒后自动终止）
 * - 临时文件自动清理
 */
@Component
public class PythonScriptMcpServer {

    private static final Logger log = LoggerFactory.getLogger(PythonScriptMcpServer.class);

    @Resource
    private ScriptSecuritySandbox scriptSandbox;

    /**
     * 获取 Python 脚本相关的工具
     */
    public List<McpTool> getTools() {
        List<McpTool> tools = new ArrayList<>();

        // 工具：execute_python_script —— 执行 Python 脚本
        tools.add(new McpTool(
                "execute_python_script",                     // 工具名称
                "执行一段 Python 脚本并返回执行结果",        // 工具描述
                createExecutePythonScriptSchema(),            // 输入参数 Schema
                this::handleExecutePythonScript               // 执行逻辑
        ));

        return tools;
    }

    /**
     * 创建 execute_python_script 工具的输入参数 Schema
     */
    private Map<String, Object> createExecutePythonScriptSchema() {
        Map<String, Object> properties = new HashMap<>();
        properties.put("script", Map.of(
                "type", "string",
                "description", "要执行的 Python 脚本代码"
        ));

        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", List.of("script"));
        return schema;
    }

    /**
     * 处理 execute_python_script 工具调用
     *
     * 流程：
     * 1. 安全检查：脚本长度、危险操作
     * 2. 将脚本写入临时文件
     * 3. 通过 ProcessBuilder 启动 Python 进程执行
     * 4. 超时控制（30 秒）
     * 5. 读取执行结果
     * 6. 清理临时文件
     *
     * @param arguments 工具参数
     * @return 脚本执行结果
     */
    private String handleExecutePythonScript(Map<String, Object> arguments) {
        String script = (String) arguments.get("script");
        log.info("MCP 工具调用：execute_python_script");

        // 安全检查：脚本长度限制
        scriptSandbox.validateScriptLength(script);

        // 安全检查：危险操作拦截
        scriptSandbox.validateScriptContent(script);

        Path tempFile = null;
        try {
            // 创建临时文件保存脚本
            tempFile = Files.createTempFile("mcp_script_", ".py");
            Files.writeString(tempFile, script);

            // 使用 ProcessBuilder 启动 Python 进程
            // 命令行：python <临时文件路径>
            ProcessBuilder pb = new ProcessBuilder("python", tempFile.toString());
            pb.redirectErrorStream(true);  // 将错误输出合并到标准输出
            Process process = pb.start();

            // 等待进程完成，超时时间 30 秒
            boolean finished = process.waitFor(30, TimeUnit.SECONDS);
            if (!finished) {
                // 超时：强制终止进程
                process.destroyForcibly();
                throw new RuntimeException("Python 脚本执行超时（30 秒）");
            }

            // 读取执行结果
            String output = new String(process.getInputStream().readAllBytes());
            return "脚本执行结果：\n" + output;

        } catch (IOException e) {
            throw new RuntimeException("Python 脚本执行失败", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Python 脚本执行被中断", e);
        } finally {
            // 清理临时文件
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException e) {
                    log.warn("临时文件清理失败: {}", tempFile);
                }
            }
        }
    }
}
```

#### 3.4.4 MCP Client 管理器

```java
package com.mcpdemo.client;

import com.mcpdemo.server.FileSystemMcpServer;
import com.mcpdemo.server.McpTool;
import com.mcpdemo.server.PythonScriptMcpServer;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MCP Client 管理器 —— 管理所有 MCP 服务器的连接和工具调用
 *
 * 职责：
 * 1. 初始化时连接所有注册的 MCP Server
 * 2. 提供 listTools 方法，获取所有可用的工具
 * 3. 提供 callTool 方法，调用指定工具执行
 * 4. 提供 disconnect 方法，断开与服务器的连接
 *
 * 设计模式：门面模式
 * 对外提供统一的 MCP 工具访问接口
 * 屏蔽底层多个 MCP Server 的差异
 */
@Component
public class McpClientManager {

    private static final Logger log = LoggerFactory.getLogger(McpClientManager.class);

    @Resource
    private FileSystemMcpServer fileSystemMcpServer;

    @Resource
    private PythonScriptMcpServer pythonScriptMcpServer;

    /** 工具缓存：工具名称 -> 工具定义的映射 */
    private final Map<String, McpTool> toolRegistry = new ConcurrentHashMap<>();

    /**
     * 初始化 —— 连接所有 MCP Server 并注册工具
     *
     * @PostConstruct 在 Spring Bean 初始化完成后自动调用
     * 收集所有 MCP Server 的工具注册到缓存中
     */
    @PostConstruct
    public void init() {
        log.info("初始化 MCP Client 管理器");

        // 注册文件系统工具
        List<McpTool> fileTools = fileSystemMcpServer.getTools();
        for (McpTool tool : fileTools) {
            toolRegistry.put(tool.getName(), tool);
            log.info("注册工具: {}", tool.getName());
        }

        // 注册 Python 脚本工具
        List<McpTool> scriptTools = pythonScriptMcpServer.getTools();
        for (McpTool tool : scriptTools) {
            toolRegistry.put(tool.getName(), tool);
            log.info("注册工具: {}", tool.getName());
        }

        log.info("MCP Client 管理器初始化完成，共注册 {} 个工具", toolRegistry.size());
    }

    /**
     * 列出所有可用的 MCP 工具
     *
     * 返回工具列表，包含每个工具的名称、描述和输入参数 Schema
     * LLM 通过此方法了解可用工具
     *
     * @return 工具列表
     */
    public List<McpTool> listTools() {
        return new ArrayList<>(toolRegistry.values());
    }

    /**
     * 调用指定工具执行
     *
     * 流程：
     * 1. 根据工具名称查找工具定义
     * 2. 校验参数
     * 3. 调用工具执行
     * 4. 返回执行结果
     *
     * @param toolName  工具名称
     * @param arguments 工具参数
     * @return 执行结果
     * @throws IllegalArgumentException 如果工具不存在
     */
    public String callTool(String toolName, Map<String, Object> arguments) {
        // 查找工具定义
        McpTool tool = toolRegistry.get(toolName);
        if (tool == null) {
            throw new IllegalArgumentException("未知工具: " + toolName);
        }

        log.info("调用 MCP 工具: {}", toolName);

        // 调用工具执行
        return tool.getHandler().execute(arguments);
    }

    /**
     * 断开所有 MCP 服务器连接
     *
     * @PreDestroy 在 Spring Bean 销毁时自动调用
     * 清理资源，释放连接
     */
    @PreDestroy
    public void disconnectAll() {
        log.info("断开所有 MCP 服务器连接");
        toolRegistry.clear();
    }
}
```

#### 3.4.5 MCP 工具调用服务（与 LangChain4j 集成）

```java
package com.mcpdemo.client;

import com.mcpdemo.server.McpTool;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Map;

/**
 * MCP 工具调用服务 —— 将 MCP 工具包装为 LangChain4j 的 @Tool 方法
 *
 * 职责：
 * 1. 将 MCP 协议的 McpTool 转换为 LangChain4j 的 @Tool 方法
 * 2. 使 LLM 可以通过 LangChain4j 的标准工具调用机制使用 MCP 工具
 * 3. 提供统一的工具调用入口
 *
 * 这是 MCP 与 LangChain4j 集成的关键桥梁
 */
@Component
public class McpToolCallService {

    private static final Logger log = LoggerFactory.getLogger(McpToolCallService.class);

    @Resource
    private McpClientManager mcpClientManager;

    /**
     * 读取文件 —— 包装 MCP 的 read_file 工具
     *
     * 通过 @Tool 注解暴露给 LangChain4j 的 LLM
     * LLM 可以像调用普通工具一样调用 MCP 工具
     *
     * @param filePath 要读取的文件路径
     * @return 文件内容
     */
    @Tool("读取指定的文件内容")
    public String readFile(@P("文件路径") String filePath) {
        log.info("LangChain4j 调用 MCP 工具: read_file, path={}", filePath);
        return mcpClientManager.callTool("read_file", Map.of("filePath", filePath));
    }

    /**
     * 写入文件 —— 包装 MCP 的 write_file 工具
     *
     * @param filePath 文件路径
     * @param content  文件内容
     * @return 执行结果
     */
    @Tool("将内容写入指定文件")
    public String writeFile(@P("文件路径") String filePath,
                            @P("文件内容") String content) {
        log.info("LangChain4j 调用 MCP 工具: write_file, path={}", filePath);
        return mcpClientManager.callTool("write_file",
                Map.of("filePath", filePath, "content", content));
    }

    /**
     * 列出目录 —— 包装 MCP 的 list_directory 工具
     *
     * @param dirPath 目录路径
     * @return 目录内容列表
     */
    @Tool("列出指定目录下的所有文件和子目录")
    public String listDirectory(@P("目录路径") String dirPath) {
        log.info("LangChain4j 调用 MCP 工具: list_directory, path={}", dirPath);
        return mcpClientManager.callTool("list_directory", Map.of("dirPath", dirPath));
    }

    /**
     * 执行 Python 脚本 —— 包装 MCP 的 execute_python_script 工具
     *
     * @param script Python 脚本代码
     * @return 执行结果
     */
    @Tool("执行 Python 脚本并返回结果")
    public String executePythonScript(@P("Python 脚本代码") String script) {
        log.info("LangChain4j 调用 MCP 工具: execute_python_script");
        return mcpClientManager.callTool("execute_python_script", Map.of("script", script));
    }
}
```

#### 3.4.6 安全校验组件

```java
package com.mcpdemo.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * 路径安全校验器 —— 防止路径遍历攻击和越权访问
 *
 * 安全策略：
 * 1. 白名单：只允许在 allowedRoots 目录内操作
 * 2. 黑名单：禁止访问 forbiddenPatterns 匹配的路径
 * 3. 路径规整化：处理 ../ 等相对路径，防止路径遍历
 */
@Component
public class PathSecurityValidator {

    private static final Logger log = LoggerFactory.getLogger(PathSecurityValidator.class);

    @Value("${mcp.filesystem.allowed-roots}")
    private List<String> allowedRoots;

    @Value("${mcp.filesystem.forbidden-patterns}")
    private List<String> forbiddenPatterns;

    /**
     * 校验路径是否安全
     *
     * 校验步骤：
     * 1. 规整化路径：将路径转换为绝对路径，解析 .. 和 .
     * 2. 白名单校验：路径必须在 allowedRoots 目录内
     * 3. 黑名单校验：路径不能匹配 forbiddenPatterns
     *
     * @param filePath 待校验的文件路径
     * @throws SecurityException 如果路径不合法
     */
    public void validate(String filePath) {
        // 1. 规整化路径：解析相对路径，转换为标准绝对路径
        Path normalizedPath = Paths.get(filePath).normalize().toAbsolutePath();

        // 2. 白名单校验：路径必须在 allowedRoots 目录内
        boolean allowed = false;
        for (String root : allowedRoots) {
            Path rootPath = Paths.get(root).normalize().toAbsolutePath();
            if (normalizedPath.startsWith(rootPath)) {
                allowed = true;
                break;
            }
        }
        if (!allowed) {
            throw new SecurityException("路径不在白名单内: " + filePath);
        }

        // 3. 黑名单校验：路径不能匹配禁止模式
        for (String pattern : forbiddenPatterns) {
            if (matchesPattern(normalizedPath.toString(), pattern)) {
                throw new SecurityException("路径被禁止访问: " + filePath
                        + " (匹配禁止模式: " + pattern + ")");
            }
        }

        log.debug("路径安全校验通过: {}", filePath);
    }

    /**
     * 简单的通配符匹配
     * 将 ** 和 * 通配符转换为正则表达式
     */
    private boolean matchesPattern(String path, String pattern) {
        // 将通配符模式转换为正则表达式
        String regex = pattern
                .replace("**", ".*")  // ** 匹配任意路径
                .replace("*", "[^/]*"); // * 匹配单级名称
        return path.matches(regex);
    }
}
```

```java
package com.mcpdemo.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 脚本安全沙箱 —— 防止恶意脚本执行
 *
 * 安全策略：
 * 1. 长度限制：脚本不能超过 maxScriptLength 字节
 * 2. 危险操作拦截：禁止包含 dangerousPatterns 中的操作
 * 3. 库白名单：只允许使用 allowedLibraries 中的库
 */
@Component
public class ScriptSecuritySandbox {

    private static final Logger log = LoggerFactory.getLogger(ScriptSecuritySandbox.class);

    @Value("${mcp.script.max-script-length:102400}")
    private int maxScriptLength;

    @Value("#{'${mcp.script.dangerous-patterns}'.split(',')}")
    private List<String> dangerousPatterns;

    @Value("#{'${mcp.script.allowed-libraries}'.split(',')}")
    private List<String> allowedLibraries;

    /**
     * 校验脚本长度是否在限制范围内
     *
     * @param script 脚本内容
     * @throws SecurityException 如果脚本过长
     */
    public void validateScriptLength(String script) {
        if (script.length() > maxScriptLength) {
            throw new SecurityException("脚本长度超过限制: "
                    + script.length() + " > " + maxScriptLength);
        }
    }

    /**
     * 校验脚本内容是否包含危险操作
     *
     * 检查脚本中是否包含危险模式：
     * - import os 或 from os import：系统操作
     * - import subprocess：执行外部命令
     * - eval( 或 exec(：动态执行代码
     * - __import__：动态导入模块
     *
     * @param script 脚本内容
     * @throws SecurityException 如果包含危险操作
     */
    public void validateScriptContent(String script) {
        // 检查是否包含危险模式
        for (String pattern : dangerousPatterns) {
            if (script.contains(pattern.trim())) {
                throw new SecurityException("脚本包含危险操作: " + pattern);
            }
        }

        // 检查 import 语句是否使用了白名单外的库
        // 匹配 import xxx 和 from xxx import 语句
        String[] lines = script.split("\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("import ") || trimmed.startsWith("from ")) {
                // 提取库名
                String libName = trimmed.startsWith("import ")
                        ? trimmed.substring(7).split("\\s")[0]
                        : trimmed.substring(5).split("\\s")[0];
                // 检查是否在白名单内
                if (allowedLibraries.stream().noneMatch(lib -> lib.trim().equals(libName))) {
                    throw new SecurityException("脚本使用了不允许的库: " + libName);
                }
            }
        }

        log.debug("脚本安全校验通过");
    }
}
```

#### 3.4.7 控制器 —— 暴露 REST API

```java
package com.mcpdemo.controller;

import com.mcpdemo.client.McpClientManager;
import com.mcpdemo.server.McpTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * MCP 控制器 —— 提供 MCP 工具管理 REST API
 *
 * 端点说明：
 * - GET /api/mcp/tools：列出所有可用的 MCP 工具
 * - POST /api/mcp/tools/{toolName}/call：调用指定 MCP 工具
 */
@RestController
@RequestMapping("/api/mcp")
public class McpController {

    private static final Logger log = LoggerFactory.getLogger(McpController.class);

    @Resource
    private McpClientManager mcpClientManager;

    /**
     * 列出所有可用的 MCP 工具
     *
     * 返回工具列表，包含每个工具的名称、描述和输入参数 Schema
     * LLM 或前端可以通过此接口了解可用工具
     *
     * @return 工具摘要列表
     */
    @GetMapping("/tools")
    public List<Map<String, Object>> listTools() {
        log.info("列出所有 MCP 工具");

        // 获取所有工具
        List<McpTool> tools = mcpClientManager.listTools();

        // 转换为简化的响应格式
        return tools.stream().map(tool -> Map.of(
                "name", tool.getName(),
                "description", tool.getDescription(),
                "inputSchema", tool.getInputSchema()
        )).collect(Collectors.toList());
    }

    /**
     * 调用指定 MCP 工具
     *
     * @param toolName  工具名称
     * @param arguments 工具参数
     * @return 执行结果
     */
    @PostMapping("/tools/{toolName}/call")
    public String callTool(@PathVariable("toolName") String toolName,
                           @RequestBody Map<String, Object> arguments) {
        log.info("调用 MCP 工具: {}", toolName);

        // 调用工具执行
        return mcpClientManager.callTool(toolName, arguments);
    }
}
```

---

## 四、运行验证

### 4.1 准备测试目录

```bash
# 创建测试目录
mkdir -p /tmp/mcp-demo/data
echo "Hello, MCP!" > /tmp/mcp-demo/hello.txt
```

### 4.2 启动应用

```bash
# 使用 Maven 编译并启动
mvn spring-boot:run

# 看到以下日志表示启动成功：
# 2026-08-22T10:00:00.000+08:00  INFO 12345 --- [mcp-demo] [main] c.m.McpDemoApplication: Started McpDemoApplication in 3.2 seconds
```

### 4.3 测试工具发现

```bash
# 列出所有可用的 MCP 工具
curl http://localhost:8080/api/mcp/tools

# 期望输出（工具列表，包含名称、描述和输入参数 Schema）：
# [
#   {
#     "name": "read_file",
#     "description": "读取指定文件的内容并返回文本",
#     "inputSchema": {
#       "type": "object",
#       "properties": { "filePath": { "type": "string", "description": "要读取的文件路径" } },
#       "required": ["filePath"]
#     }
#   },
#   {
#     "name": "write_file",
#     "description": "将内容写入指定文件",
#     ...
#   },
#   {
#     "name": "list_directory",
#     "description": "列出指定目录下的所有文件和子目录",
#     ...
#   },
#   {
#     "name": "execute_python_script",
#     "description": "执行一段 Python 脚本并返回执行结果",
#     ...
#   }
# ]
```

### 4.4 测试工具调用

```bash
# 测试 read_file 工具
curl -X POST http://localhost:8080/api/mcp/tools/read_file/call \
  -H "Content-Type: application/json" \
  -d "{\"filePath\": \"/tmp/mcp-demo/hello.txt\"}"

# 期望输出：
# 文件内容：
# Hello, MCP!

# 测试 write_file 工具
curl -X POST http://localhost:8080/api/mcp/tools/write_file/call \
  -H "Content-Type: application/json" \
  -d "{\"filePath\": \"/tmp/mcp-demo/data/test.txt\", \"content\": \"测试内容\"}"

# 期望输出：
# 文件写入成功：/tmp/mcp-demo/data/test.txt

# 测试 list_directory 工具
curl -X POST http://localhost:8080/api/mcp/tools/list_directory/call \
  -H "Content-Type: application/json" \
  -d "{\"dirPath\": \"/tmp/mcp-demo\"}"

# 期望输出：
# 目录内容：
# [FILE] hello.txt
# [DIR] data

# 测试 execute_python_script 工具
curl -X POST http://localhost:8080/api/mcp/tools/execute_python_script/call \
  -H "Content-Type: application/json" \
  -d "{\"script\": \"print('Hello from Python!')\"}"

# 期望输出：
# 脚本执行结果：
# Hello from Python!
```

### 4.5 测试安全校验

```bash
# 测试路径越权访问（should fail）
curl -X POST http://localhost:8080/api/mcp/tools/read_file/call \
  -H "Content-Type: application/json" \
  -d "{\"filePath\": \"/etc/passwd\"}"

# 期望输出：SecurityException - 路径不在白名单内

# 测试危险脚本（should fail）
curl -X POST http://localhost:8080/api/mcp/tools/execute_python_script/call \
  -H "Content-Type: application/json" \
  -d "{\"script\": \"import os; os.system('rm -rf /')\"}"

# 期望输出：SecurityException - 脚本包含危险操作
```

---

## 五、项目对照：对应 ruoyi-ai 项目中的真实代码位置

### 5.1 核心文件对照表

| 本示例中的类 | ruoyi-ai 中的对应类 | 所在模块 | 核心差异 |
|-------------|-------------------|---------|---------|
| `McpTool` | `McpTool` | `ruoyi-chat/agent/mcp/` | 结构基本一致 |
| `FileSystemMcpServer` | `FileSystemMcpServer` | `ruoyi-chat/agent/mcp/server/` | 工具更多（追加文件、删除文件等） |
| `PythonScriptMcpServer` | `PythonScriptMcpServer` | `ruoyi-chat/agent/mcp/server/` | 实现方式相同 |
| `McpClientManager` | `SseMcpClientManager` | `ruoyi-chat/agent/mcp/client/` | ruoyi-ai 支持 SSE 远程连接 |
| `McpToolCallService` | `McpToolCallService` | `ruoyi-chat/agent/mcp/` | 集成方式相同 |
| `PathSecurityValidator` | `PathSecurityValidator` | `ruoyi-chat/agent/mcp/security/` | 实现方式相同 |
| `ScriptSecuritySandbox` | `ScriptSecuritySandbox` | `ruoyi-chat/agent/mcp/security/` | 实现方式相同 |
| `McpController` | `McpSseController` | `ruoyi-chat/controller/` | ruoyi-ai 增加 SSE 端点 |

### 5.2 ruoyi-ai 中的进阶实现

ruoyi-ai 的 MCP 协议实现在本示例的基础上增加了以下进阶特性：

**1. SSE 远程 MCP 服务器连接**

```java
// ruoyi-ai 中支持通过 SSE 连接远程 MCP 服务器
// 可以动态添加和移除远程 MCP 服务器

@Service
public class SseMcpClientManager {

    /** 客户端缓存：serverUrl -> MCP Client */
    private final Map<String, SseMcpClient> clientCache = new ConcurrentHashMap<>();

    /**
     * 连接到远程 MCP 服务器
     *
     * @param serverUrl MCP 服务器 SSE 地址
     */
    public void connect(String serverUrl) {
        // 创建 SSE MCP 客户端
        McpClient mcpClient = McpClient.using(
                McpTransport.sse(serverUrl)  // SSE 传输方式
        );

        // 缓存客户端连接
        clientCache.put(serverUrl, mcpClient);
    }

    /**
     * 列出所有远程 MCP 服务器的工具
     */
    public List<McpTool> listTools() {
        List<McpTool> allTools = new ArrayList<>();
        for (McpClient client : clientCache.values()) {
            allTools.addAll(client.listTools());
        }
        return allTools;
    }
}
```

**2. 动态工具注册到 SkillsAgent**

```java
// ruoyi-ai 中将 MCP 工具动态注册为 SkillsAgent 的 @Tool 方法
// 新增 MCP Server 后，工具自动可用，无需修改代码

@Component
public class SkillsAgent {

    @PostConstruct
    public void init() {
        // 获取所有 MCP Server 的工具
        List<McpTool> mcpTools = mcpClientManager.listTools();

        // 动态创建 @Tool 包装方法
        for (McpTool tool : mcpTools) {
            createToolObject(tool);  // 将 MCP 工具包装为 @Tool 对象
        }
    }

    /**
     * 将 MCP 工具包装为 LangChain4j 的 @Tool 对象
     * 使用 ToolSpecification 和 ToolExecutor 动态注册
     */
    private Object createToolObject(McpTool tool) {
        // 通过 LangChain4j 的 ToolSpecification 机制
        // 将 MCP 工具动态注册为 LLM 可用的工具
        return ToolSpecification.builder()
                .name(tool.getName())
                .description(tool.getDescription())
                .build();
    }
}
```

**3. SSE 控制器端点**

```java
// ruoyi-ai 中通过 SSE 端点实现 MCP 协议通信
// 支持 list_tools 和 call_tool 两种消息类型

@RestController
public class McpSseController {

    @PostMapping("/mcp/sse")
    public SseEmitter handleSseConnection() {
        // 创建 SSE 连接，超时时间 30 分钟
        SseEmitter emitter = new SseEmitter(1800000L);
        return emitter;
    }

    @PostMapping("/mcp/message")
    public CompletableFuture<String> handleMessage(@RequestBody McpMessage message) {
        // 根据消息类型处理
        return switch (message.getType()) {
            case "list_tools" -> handleListTools(message);
            case "call_tool" -> handleCallTool(message);
            default -> CompletableFuture.completedFuture("未知消息类型");
        };
    }
}
```

### 5.3 从示例到项目的进阶之路

| 维度 | 本文示例 | ruoyi-ai 项目 |
|------|---------|--------------|
| **传输方式** | 本地工具注册 | 本地 + SSE 远程连接 |
| **工具注册** | 手动注册 | 动态发现 + 自动注册 |
| **远程MCP** | 无 | SSE 连接远程 MCP 服务器 |
| **SSE端点** | 无 | 完整 SSE 通信端点 |
| **安全校验** | 基础实现 | 完整实现 + JWT 认证 |
| **动态工具** | 固定工具集 | 动态发现 + 动态注册到 SkillsAgent |
| **监控** | 无 | 工具调用日志 + 性能监控 |

---

## 六、面试实战：3道面试题 + 回答框架

### Q1：MCP 协议的核心价值是什么？解决了哪些问题？

**考察点：** 面试官想考察候选人对 MCP 协议的理解深度，以及是否能说清楚"为什么需要 MCP"。

**回答框架：**

- **背景**：在智能体开发中，一个核心需求是"让 LLM 能够调用外部工具"。然而，在不同框架和语言之间实现工具调用，缺乏统一的标准化协议，导致重复造轮子和集成困难。

- **MCP 的核心价值**：MCP（Model Context Protocol）由 Anthropic 提出，是一个开放标准协议，定义了 LLM 与外部工具之间的标准化通信方式。它解决了三个核心问题：

  1. **协议标准化**：无论工具是用 Java、Python 还是 Node.js 实现的，都通过统一的 MCP 协议暴露给 LLM。Client 不需要关心底层实现语言，只需要通过标准接口（listTools、callTool）与 Server 通信。
  2. **跨语言互通**：Java 写的智能体可以通过 MCP 调用 Python 写的工具，反之亦然。这打破了语言壁垒，让团队可以使用最适合的语言实现工具。
  3. **动态发现**：工具列表是动态的，Server 可以随时添加新工具，Client 通过 listTools 就能发现。新增工具不需要修改 Client 代码，也不需要重新部署。

- **对比传统方式**：在 MCP 之前，智能体工具调用通常是通过在代码中硬编码 @Tool 方法实现的。每次新增工具都需要修改代码、重新编译、重新部署。MCP 将这个过程标准化为 Client <-> Server 通信，工具可以独立于智能体部署和更新。

- **适用场景**：MCP 特别适合需要集成多种语言实现的工具、需要动态扩展工具集、或者需要将工具作为独立服务部署的场景。

### Q2：MCP 的三种传输机制（SSE、stdio、Streamable HTTP）有什么区别？各有什么适用场景？

**考察点：** 面试官想考察候选人对 MCP 传输层的理解，以及是否能根据场景选择合适的传输方式。

**回答框架：**

- **背景**：MCP 协议支持多种传输机制，适用于不同的部署场景。选择合适的传输机制对系统性能和架构设计有重要影响。

- **三种传输机制对比**：

  1. **SSE（Server-Sent Events）**：
     - 原理：Client 通过 HTTP POST 发送请求，Server 通过 SSE 推送响应。建立一次长连接，Server 可以持续推送多个事件。
     - 优点：支持流式响应，适合大文件读取、长时间运行的任务；基于 HTTP，穿透防火墙简单。
     - 缺点：单向推送（Server -> Client），Client 需要额外的 HTTP 请求发送数据。
     - 适用场景：远程部署的 MCP Server，需要流式输出的场景。

  2. **stdio（标准输入输出）**：
     - 原理：通过子进程的标准输入输出传输 JSON-RPC 消息，Client 启动 Server 进程，通过 stdin 发送请求，从 stdout 读取响应。
     - 优点：延迟低，没有网络开销；适合本地工具；进程级隔离，安全。
     - 缺点：只能本地使用，不能远程；Server 生命周期与 Client 绑定。
     - 适用场景：本地部署的工具，如文件系统操作、脚本执行。

  3. **Streamable HTTP**：
     - 原理：在标准 HTTP 上实现双向流式传输，支持请求和响应的流式处理。
     - 优点：双向流式，适合流式对话场景；标准 HTTP，兼容性好。
     - 缺点：实现复杂度高；需要专门的 HTTP 服务器支持。
     - 适用场景：需要双向流式传输的场景，如流式对话生成。

- **选择建议**：
  - 本地工具 -> stdio（延迟最低，实现最简单）
  - 远程工具 -> SSE（远程部署，支持流式输出）
  - 流式对话 -> Streamable HTTP（双向流式，适合复杂交互）

### Q3：MCP 工具的安全校验是如何设计的？有哪些关键点？

**考察点：** 面试官想考察候选人对工具调用安全性的考虑，以及安全设计的最佳实践。

**回答框架：**

- **背景**：MCP 工具调用涉及文件系统、脚本执行、网络访问等敏感操作，安全校验是必须考虑的核心问题。不安全的工具调用可能导致路径遍历攻击、代码注入、数据泄露等安全风险。

- **安全设计的关键点**：

  1. **路径安全校验**：
     - 白名单机制：只允许操作 allowedRoots 目录内的文件，所有操作都被限制在指定范围内
     - 路径规整化：使用 Path.normalize() 将路径转换为标准形式，防止 ../ 等路径遍历攻击
     - 黑名单模式：禁止访问 /etc、/sys 等敏感路径，以及 .env、config.yml 等配置文件
     - 双重校验：先校验白名单，再校验黑名单，确保没有遗漏

  2. **脚本安全沙箱**：
     - 长度限制：限制脚本最大长度（如 100KB），防止拒绝服务攻击
     - 危险操作拦截：禁止 import os、import subprocess、eval()、exec() 等危险操作
     - 库白名单：只允许使用 json、math、datetime 等安全库，禁止系统操作库
     - 超时控制：设置脚本执行超时时间（如 30 秒），防止无限循环

  3. **进程隔离**：
     - 每个脚本在独立的子进程中执行，通过 ProcessBuilder 启动
     - 子进程的崩溃不影响主进程
     - 临时文件自动清理，不留下痕迹

  4. **认证鉴权**：
     - 生产环境应该在 MCP 端点上增加 JWT 认证，防止未授权访问
     - SSE 端点也需要安全保护，防止被恶意利用

- **深度（项目经验）**：在 ruoyi-ai 中，我们设计了 PathSecurityValidator 和 ScriptSecuritySandbox 两个安全组件，分别处理文件路径安全和脚本内容安全。同时，在 SSE 端点上增加了 JWT 认证，确保只有授权的客户端才能连接 MCP 服务器。安全校验是工具调用的第一道防线，绝对不能省略。

---

## 七、总结

本文从零搭建了一个完整的 MCP 协议应用，涵盖了"MCP Server 注册工具 -> MCP Client 发现工具 -> 调用工具执行 -> 返回结果"的完整流程。通过这个最小可行示例，我们学习了以下核心知识点：

1. **MCP Server 和 Client 架构**：Server 负责提供工具，Client 负责发现和调用工具，两者通过标准化协议通信
2. **三种传输机制**：SSE 适合远程通信，stdio 适合本地通信，Streamable HTTP 适合双向流式传输
3. **工具动态发现**：通过 listTools 动态发现可用工具，新增工具无需修改 Client 代码
4. **安全校验机制**：路径安全校验（白名单 + 黑名单 + 路径规整化）、脚本安全沙箱（长度限制 + 危险操作拦截 + 库白名单）
5. **项目对照**：理解了 ruoyi-ai 项目中 MCP 协议的真实实现，以及从示例到生产环境的进阶路径

---

## 参考资料

- [Model Context Protocol 官方文档](https://modelcontextprotocol.io/) — Anthropic 的 MCP 协议规范
- [MCP 规范](https://spec.modelcontextprotocol.io/) — MCP 协议详细规范
- [LangChain4j MCP 集成](https://docs.langchain4j.dev/integrations/language-models/mcp) — LangChain4j 的 MCP 支持
- [Server-Sent Events 规范](https://html.spec.whatwg.org/multipage/server-sent-events.html) — SSE 技术规范
- [JSON Schema 官方文档](https://json-schema.org/) — 工具参数 Schema 定义标准
- [ruoyi-ai GitHub 仓库](https://github.com/1byteone/ruoyi-ai) — 项目源码，查看完整的 MCP 协议实现