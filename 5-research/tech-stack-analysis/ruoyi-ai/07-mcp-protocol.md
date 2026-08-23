# 07 · MCP 协议集成：让 AI Agent 拥有无限工具生态

> MCP（Model Context Protocol）是由 Anthropic 推出的开放标准协议，用于连接 AI 助手与外部数据源和工具。在 ruoyi-ai 中，MCP 协议让 Skills Agent 能够调用文件系统、Python 脚本等外部工具，极大扩展了 AI Agent 的能力边界。
>
> **对应项目模块：** `ruoyi-chat/agent/`（Skills Agent）和 `ruoyi-chat/mcp/`（MCP 协议集成）

---

## 一、你必须知道的 3 个核心概念

### 1.1 MCP Server（MCP 服务器）

MCP Server 是**工具的提供方**，它将具体的能力（如文件读写、脚本执行、数据库查询）封装为标准化的工具接口，供 AI 应用调用。

| 角色 | 在项目中的对应 |
|------|---------------|
| **工具定义** | 每个 MCP Server 暴露一组 Tools（可执行操作）、Resources（只读数据）、Prompts（提示模板） |
| **工具实现** | 如文件系统 Server 提供 `read_file`、`write_file`、`list_directory` 等操作 |
| **注册方式** | 通过 MCP 协议标准注册，LLM 可以动态发现有哪些工具可用 |

**通俗理解：** MCP Server 就像是一个"技能插件市场"。每个 Server 提供一组专业技能（如文件操作、Python 执行），AI Agent 需要什么技能就连接对应的 Server，无需事先硬编码。

### 1.2 MCP Client（MCP 客户端）

MCP Client 是**工具的调用方**，它负责发现 MCP Server 上的工具、获取工具的描述信息（名称、参数、返回值），并代表 LLM 执行工具调用。

| 角色 | 在项目中的对应 |
|------|---------------|
| **客户端角色** | 每个 MCP Server 对应一个 MCP Client，建立 1:1 连接 |
| **工具发现** | Client 通过 `list_tools` 请求获取 Server 上的所有工具列表 |
| **工具调用** | Client 通过 `call_tool` 请求触发 Server 执行具体工具 |
| **生命周期** | Client 管理连接的初始化、心跳检测和关闭 |

**通俗理解：** MCP Client 是 AI Agent 的"工具管家"。Agent 不需要自己知道怎么调用文件系统，只需要告诉 MCP Client"帮我查一下文件"，Client 就会找到对应的 Server 并执行。

### 1.3 SSE 传输（Server-Sent Events）

SSE 是 MCP 的一种**传输层协议**，基于 HTTP 长连接实现服务端到客户端的单向数据推送。

```
┌─────────────────────────────────────────────────┐
│                  MCP 架构图                        │
│                                                   │
│   Host (AI 应用运行环境)                           │
│     │                                              │
│     ├── MCP Client (1:1 连接)                      │
│     │       │                                      │
│     │       ├── Transport: SSE (HTTP 长连接)       │
│     │       │     │                                │
│     │       │     ├── Server → Client: 事件推送    │
│     │       │     └── Client → Server: HTTP POST  │
│     │       │                                      │
│     │       └── MCP Server (工具提供方)            │
│     │               ├── Tools (可执行操作)          │
│     │               ├── Resources (只读数据)        │
│     │               └── Prompts (提示模板)          │
│     │                                              │
│     └── 其他 MCP Client ...                        │
└─────────────────────────────────────────────────┘
```

**三种传输方式对比：**

| 传输方式 | 适用场景 | 特点 |
|---------|---------|------|
| **stdio** | 本地子进程通信 | 启动子进程，通过标准输入/输出通信，适合本地部署的工具 |
| **Streamable HTTP** | 远程 HTTP 服务 | 基于 HTTP 请求/响应模式，支持流式返回，适合远程部署 |
| **SSE** | 远程长连接服务 | 基于 HTTP 长连接，Server 主动推送事件，ruoyi-ai 中使用 |

**为什么 ruoyi-ai 选择 SSE？**
- SSE 是单向通道，Server 可以主动推送状态更新，适合长时间运行的工具任务
- 相比 WebSocket，SSE 更轻量，基于标准 HTTP 协议，无需额外握手
- 在 Java 生态中，Spring Boot 对 SSE 有原生支持（`SseEmitter`）
- 与 LangChain4j 的流式 Token 输出可以共用同一套 SSE 基础设施

---

## 二、项目中的实战应用

### 2.1 解决了什么问题

在没有 MCP 之前，AI Agent 的工具能力面临以下痛点：

| 问题 | 描述 | MCP 的解决方案 |
|------|------|---------------|
| **工具硬编码** | 每个工具需要写死代码，新增工具要修改 Agent 代码 | MCP 动态发现工具，新增工具只需启动新的 MCP Server |
| **集成碎片化** | 每个数据源（文件系统、数据库、API）需要不同的集成方式 | MCP 统一协议，所有工具通过标准接口暴露 |
| **类型不安全** | 工具参数缺乏标准化描述，LLM 容易传错参数 | MCP 使用 JSON Schema 定义工具入参，LLM 准确理解 |
| **生命周期管理** | 工具连接、断开、重试需要自行实现 | MCP Client 管理连接生命周期，提供心跳和重连机制 |
| **跨语言调用** | Python 脚本需要 Java 进程调用，集成复杂 | MCP 是语言无关的协议，Python 脚本通过 MCP Server 暴露给 Java Agent |

### 2.2 内置工具：文件系统 MCP Server

ruoyi-ai 内置了文件系统 MCP Server，让 AI Agent 能够直接操作本地文件：

```java
/**
 * 文件系统 MCP Server —— 让 AI Agent 具备文件操作能力
 * 
 * 功能：读取本地文档（docx/pdf/xlsx）、写入处理结果、列出目录文件
 * 实现：基于 LangChain4j 的 MCP Server API
 * 注册：Spring 启动时自动注册到 Skills Agent
 */
@Component
public class FileSystemMcpServer {

    private static final Logger log = LoggerFactory.getLogger(FileSystemMcpServer.class);

    /**
     * 注册文件系统工具到 MCP Server
     * 
     * 每个工具包含：名称、描述、输入 Schema（JSON Schema 格式）
     * LLM 根据这些信息决定何时调用哪个工具
     * 
     * @param server  MCP Server 实例，用于注册工具
     */
    public void registerTools(McpServer server) {
        // ========== 工具 1：读取文件 ==========

        server.addTool(McpTool.builder()
                .name("read_file")                              // 工具名称：供 LLM 识别
                .description("读取本地文件的内容，支持 txt、docx、pdf、xlsx 格式")  // 工具描述：告诉 LLM 何时使用
                .inputSchema(McpToolInputSchema.builder()       // 定义输入参数
                        .addProperty("filePath",                // 参数名：文件路径
                                JsonSchemaProperty.STRING,      // 参数类型：字符串
                                JsonSchemaProperty.description("要读取的文件绝对路径，如 D:/docs/report.docx"),
                                JsonSchemaProperty.required())  // 必填参数
                        .build())
                .handler(request -> {                           // 工具执行逻辑
                    String filePath = request.getArgument("filePath");
                    log.info("MCP 工具调用：read_file, path={}", filePath);

                    // 实际的文件读取逻辑（省略具体实现）
                    String content = readFileContent(filePath);
                    return McpToolResult.success(content);
                })
                .build());

        // ========== 工具 2：写入文件 ==========

        server.addTool(McpTool.builder()
                .name("write_file")                             // 工具名称
                .description("将内容写入本地文件，如果文件已存在则覆盖")  // 工具描述
                .inputSchema(McpToolInputSchema.builder()
                        .addProperty("filePath",                // 文件路径
                                JsonSchemaProperty.STRING,
                                JsonSchemaProperty.description("要写入的文件路径"),
                                JsonSchemaProperty.required())
                        .addProperty("content",                 // 文件内容
                                JsonSchemaProperty.STRING,
                                JsonSchemaProperty.description("要写入的文件内容"),
                                JsonSchemaProperty.required())
                        .build())
                .handler(request -> {
                    String filePath = request.getArgument("filePath");
                    String content = request.getArgument("content");
                    log.info("MCP 工具调用：write_file, path={}", filePath);

                    // 实际的写入逻辑（省略具体实现）
                    writeFileContent(filePath, content);
                    return McpToolResult.success("文件写入成功: " + filePath);
                })
                .build());

        // ========== 工具 3：列出目录 ==========

        server.addTool(McpTool.builder()
                .name("list_directory")                         // 工具名称
                .description("列出指定目录下的所有文件和子目录")  // 工具描述
                .inputSchema(McpToolInputSchema.builder()
                        .addProperty("dirPath",                 // 目录路径
                                JsonSchemaProperty.STRING,
                                JsonSchemaProperty.description("要列出的目录绝对路径"),
                                JsonSchemaProperty.required())
                        .addProperty("recursive",               // 是否递归
                                JsonSchemaProperty.BOOLEAN,
                                JsonSchemaProperty.description("是否递归列出子目录，默认 false"),
                                JsonSchemaProperty.defaultValue(false))
                        .build())
                .handler(request -> {
                    String dirPath = request.getArgument("dirPath");
                    boolean recursive = request.getOptionalArgument("recursive", false);
                    log.info("MCP 工具调用：list_directory, path={}, recursive={}", dirPath, recursive);

                    // 实际的目录列出逻辑（省略具体实现）
                    String listing = listDirectory(dirPath, recursive);
                    return McpToolResult.success(listing);
                })
                .build());
    }

    /**
     * 读取文件内容（内部方法，支持多种格式）
     */
    private String readFileContent(String filePath) {
        // 根据文件扩展名选择不同的解析方式
        // .txt → 直接读取文本
        // .docx → 使用 Apache POI 解析
        // .pdf  → 使用 PDFBox 或 iText 解析
        // .xlsx → 使用 Apache POI 解析
        // 此处省略具体实现
        return "文件内容...";
    }

    /**
     * 写入文件内容（内部方法）
     */
    private void writeFileContent(String filePath, String content) {
        // 使用 Java NIO 写入文件
        // 注意：需要做路径安全检查，防止路径穿越攻击
        // 此处省略具体实现
    }

    /**
     * 列出目录内容（内部方法）
     */
    private String listDirectory(String dirPath, boolean recursive) {
        // 使用 java.nio.file.Files 遍历目录
        // 注意：需要做路径安全检查，防止访问敏感目录
        // 此处省略具体实现
        return "目录列表...";
    }
}
```

### 2.3 内置工具：Python 脚本执行

除了文件系统，ruoyi-ai 还支持通过 MCP 协议执行 Python 脚本，用于处理复杂的文档解析任务：

```java
/**
 * Python 脚本执行 MCP Server —— 让 AI Agent 具备 Python 生态能力
 * 
 * 功能：执行 Python 脚本处理文档（docx/pdf 解析、数据清洗、图表生成）
 * 适用场景：需要 Python 生态的 NLP 库、数据分析库时
 * 通信方式：通过 stdio 传输与本地的 Python 进程通信
 */
@Component
public class PythonScriptMcpServer {

    private static final Logger log = LoggerFactory.getLogger(PythonScriptMcpServer.class);

    /**
     * 注册 Python 脚本执行工具
     * 
     * 此工具允许 AI Agent 调用本地 Python 环境来执行脚本
     * 用于处理 Java 生态中较难处理的文档解析和数据分析任务
     */
    @PostConstruct
    public void init() {
        log.info("Python 脚本 MCP Server 初始化完成");
    }

    /**
     * 注册工具到 MCP Server
     */
    public void registerTools(McpServer server) {
        server.addTool(McpTool.builder()
                .name("execute_python_script")                  // 工具名称
                .description("执行 Python 脚本处理文档或数据，支持 pandas、openpyxl、python-docx 等库")
                .inputSchema(McpToolInputSchema.builder()
                        .addProperty("script",                  // Python 脚本内容
                                JsonSchemaProperty.STRING,
                                JsonSchemaProperty.description("要执行的 Python 脚本代码"),
                                JsonSchemaProperty.required())
                        .addProperty("timeout",                 // 超时时间
                                JsonSchemaProperty.INTEGER,
                                JsonSchemaProperty.description("脚本执行超时时间（秒），默认 30"),
                                JsonSchemaProperty.defaultValue(30))
                        .build())
                .handler(request -> {
                    String script = request.getArgument("script");
                    int timeout = request.getOptionalArgument("timeout", 30);

                    log.info("MCP 工具调用：execute_python_script, timeout={}s", timeout);

                    // 将脚本写入临时文件并执行
                    // 通过 ProcessBuilder 调用系统 Python 解释器
                    String result = runPythonScript(script, timeout);
                    return McpToolResult.success(result);
                })
                .build());
    }

    /**
     * 执行 Python 脚本
     * 
     * 实现思路：
     * 1. 将脚本内容写入临时文件
     * 2. 通过 ProcessBuilder 启动 python3 进程
     * 3. 读取 stdout 获取执行结果
     * 4. 超时强制终止进程
     * 5. 清理临时文件
     */
    private String runPythonScript(String script, int timeout) {
        // 1. 写入临时文件
        // 2. 启动 Python 进程
        // 3. 读取执行结果
        // 4. 超时控制
        // 5. 清理临时文件
        // 此处省略具体实现
        return "脚本执行结果...";
    }
}
```

### 2.4 SSE MCP Clients 集成

ruoyi-ai 中的 MCP Client 通过 SSE 传输连接外部 MCP Server，实现远程工具调用：

```java
/**
 * SSE MCP 客户端管理器 —— 管理与外部 MCP Server 的 SSE 连接
 * 
 * 功能：
 * 1. 通过 SSE 协议连接到远程 MCP Server
 * 2. 发现远程 Server 上的可用工具
 * 3. 代理 LLM 调用远程工具
 * 4. 管理连接生命周期（连接、心跳、重连、关闭）
 * 
 * 适用场景：连接远程的数据处理服务、第三方 API 网关等
 */
@Component
public class SseMcpClientManager {

    private static final Logger log = LoggerFactory.getLogger(SseMcpClientManager.class);

    /** 已连接的 MCP Client 缓存，key = Server 标识 */
    private final Map<String, McpClient> clientCache = new ConcurrentHashMap<>();

    /** 线程池，用于管理 SSE 连接的异步处理 */
    private final ExecutorService executor = Executors.newCachedThreadPool();

    /**
     * 通过 SSE 连接到远程 MCP Server
     * 
     * @param serverId  Server 标识，用于缓存和日志
     * @param serverUrl Server 的 SSE endpoint URL
     * @return McpClient 已连接的客户端实例
     */
    public McpClient connect(String serverId, String serverUrl) {
        // 1. 先查缓存，避免重复连接
        return clientCache.computeIfAbsent(serverId, id -> {
            log.info("通过 SSE 连接 MCP Server：id={}, url={}", id, serverUrl);

            // 2. 创建 SSE 传输层
            //    LangChain4j 的 McpClient 支持 SSE 传输
            McpTransport transport = McpTransport.sse(serverUrl);

            // 3. 创建 MCP Client
            McpClient client = McpClient.builder()
                    .transport(transport)           // 设置传输层（SSE）
                    .requestTimeout(Duration.ofSeconds(30))  // 请求超时
                    .build();

            // 4. 初始化连接（发送 initialize 请求）
            client.initialize();
            log.info("MCP Server 连接成功：id={}", id);

            return client;
        });
    }

    /**
     * 获取指定 Server 上的所有可用工具
     * 
     * LLM 在决定调用哪个工具之前，需要先知道有哪些工具可用
     * 此方法返回工具的 JSON Schema 描述，供 LLM 理解工具用途
     * 
     * @param serverId Server 标识
     * @return 工具列表，每个工具包含名称、描述、参数 Schema
     */
    public List<McpTool> getTools(String serverId) {
        McpClient client = clientCache.get(serverId);
        if (client == null) {
            throw new IllegalStateException("MCP Server 未连接: " + serverId);
        }

        // 调用 MCP 协议的 list_tools 请求，获取工具列表
        return client.listTools();
    }

    /**
     * 调用远程 MCP Server 上的工具
     * 
     * @param serverId  Server 标识
     * @param toolName  工具名称
     * @param arguments 工具参数（Map 形式）
     * @return 工具执行结果
     */
    public McpToolResult callTool(String serverId, String toolName, Map<String, Object> arguments) {
        McpClient client = clientCache.get(serverId);
        if (client == null) {
            throw new IllegalStateException("MCP Server 未连接: " + serverId);
        }

        log.info("调用 MCP 工具：server={}, tool={}, args={}", serverId, toolName, arguments);

        // 调用 MCP 协议的 call_tool 请求，执行工具
        return client.callTool(toolName, arguments);
    }

    /**
     * 断开与 MCP Server 的连接
     */
    public void disconnect(String serverId) {
        McpClient client = clientCache.remove(serverId);
        if (client != null) {
            log.info("断开 MCP Server 连接：id={}", serverId);
            client.close();  // 发送 close 请求，释放资源
        }
    }

    /**
     * 断开所有连接（应用关闭时调用）
     */
    @PreDestroy
    public void disconnectAll() {
        log.info("断开所有 MCP Server 连接，共 {} 个", clientCache.size());
        clientCache.forEach((id, client) -> {
            try {
                client.close();
            } catch (Exception e) {
                log.warn("断开 MCP 连接失败：id={}", id, e);
            }
        });
        clientCache.clear();
        executor.shutdown();
    }
}
```

---

## 三、核心代码实现

### 3.1 MCP Server 注册到 Skills Agent

Skills Agent 是 ruoyi-ai 中负责执行本地文档技能的子智能体，它通过 MCP 协议集成了文件系统和 Python 脚本工具：

```java
/**
 * Skills Agent —— 集成 MCP 工具的本地文档技能执行器
 * 
 * 职责：
 * 1. 管理 MCP Server 的生命周期（启动、注册工具、关闭）
 * 2. 将 MCP 工具自动注册为 LangChain4j 的 @Tool
 * 3. 将用户请求路由到合适的工具
 * 
 * 通过 MCP 协议，Skills Agent 可以动态扩展能力：
 * - 新增工具不需要修改 Agent 代码
 * - 只需启动新的 MCP Server 并注册到 Agent 即可
 */
@Component
public class SkillsAgent {

    private static final Logger log = LoggerFactory.getLogger(SkillsAgent.class);

    /** MCP Server 实例，管理所有注册的工具 */
    private final McpServer mcpServer;

    /** 文件系统 MCP Server（内置工具） */
    private final FileSystemMcpServer fileSystemMcpServer;

    /** Python 脚本 MCP Server（内置工具） */
    private final PythonScriptMcpServer pythonScriptMcpServer;

    /** SSE MCP 客户端管理器（连接远程工具） */
    private final SseMcpClientManager sseMcpClientManager;

    /** 已注册的 MCP 工具列表，供 LangChain4j 的 AiServices 使用 */
    private List<McpTool> registeredTools;

    /**
     * 构造函数注入所有 MCP 组件
     */
    public SkillsAgent(FileSystemMcpServer fileSystemMcpServer,
                       PythonScriptMcpServer pythonScriptMcpServer,
                       SseMcpClientManager sseMcpClientManager) {
        this.fileSystemMcpServer = fileSystemMcpServer;
        this.pythonScriptMcpServer = pythonScriptMcpServer;
        this.sseMcpClientManager = sseMcpClientManager;

        // 创建 MCP Server 实例
        this.mcpServer = McpServer.builder()
                .serverInfo("ruoyi-ai-skills-agent", "1.0.0")
                .build();
    }

    /**
     * 初始化 —— 注册所有工具
     * 
     * 在 Spring 容器初始化完成后，注册所有内置工具
     * 这是 MCP 协议的核心：工具注册
     */
    @PostConstruct
    public void init() {
        log.info("Skills Agent 初始化，开始注册 MCP 工具...");

        // 1. 注册文件系统工具
        fileSystemMcpServer.registerTools(mcpServer);
        log.info("已注册文件系统 MCP 工具");

        // 2. 注册 Python 脚本工具
        pythonScriptMcpServer.registerTools(mcpServer);
        log.info("已注册 Python 脚本 MCP 工具");

        // 3. 获取所有已注册的工具列表
        //    MCP Server 会返回所有通过 addTool 注册的工具
        this.registeredTools = mcpServer.listTools();
        log.info("Skills Agent 初始化完成，共注册 {} 个 MCP 工具", registeredTools.size());

        // 4. 连接到远程 MCP Server（如果配置了）
        connectRemoteMcpServers();
    }

    /**
     * 连接远程 MCP Server
     * 
     * 从配置文件中读取远程 MCP Server 地址
     * 通过 SSE 协议建立连接
     */
    private void connectRemoteMcpServers() {
        // 读取配置中的远程 MCP Server 列表
        // 配置示例：
        // mcp:
        //   servers:
        //     - id: data-processor
        //       url: http://data-processor:8080/mcp/sse
        //     - id: image-generator
        //       url: http://image-gen:8080/mcp/sse
        //
        // 通过 SSE MCP 客户端管理器连接
        // 此处省略具体配置读取逻辑
    }

    /**
     * 获取所有可用的 MCP 工具描述
     * 
     * 此方法返回的列表会被 LangChain4j 的 AiServices 使用
     * 自动转换为 LLM 可理解的 Function Calling 格式
     * 
     * @return 所有已注册的 MCP 工具列表
     */
    public List<McpTool> getAvailableTools() {
        List<McpTool> allTools = new ArrayList<>(registeredTools);

        // 同时获取远程 MCP Server 上的工具
        // 连接远程 Server 并获取其工具列表
        // 此处省略具体实现

        return allTools;
    }

    /**
     * 执行 MCP 工具
     * 
     * LLM 决定调用哪个工具后，由 Skills Agent 负责执行
     * 执行结果返回给 LLM 继续处理
     * 
     * @param toolName  工具名称
     * @param arguments 工具参数
     * @return 工具执行结果
     */
    public McpToolResult executeTool(String toolName, Map<String, Object> arguments) {
        log.info("Skills Agent 执行 MCP 工具：tool={}", toolName);

        // 1. 先查找本地 MCP Server 是否有该工具
        try {
            return mcpServer.callTool(toolName, arguments);
        } catch (Exception e) {
            log.warn("本地 MCP Server 未找到工具：{}，尝试远程 Server", toolName);
        }

        // 2. 如果是远程工具，通过 SSE MCP 客户端调用
        // 遍历所有远程连接，查找匹配的工具
        // 此处省略具体实现

        throw new IllegalArgumentException("未找到 MCP 工具: " + toolName);
    }

    /**
     * 生成 LangChain4j 兼容的 @Tool 方法
     * 
     * 将 MCP 工具包装为 LangChain4j 的 @Tool 注解方法
     * 这样 AiServices 就可以像调用普通 @Tool 一样调用 MCP 工具
     * 
     * @return 工具对象，包含 @Tool 注解的方法
     */
    public Object createToolObject() {
        return new Object() {

            /**
             * 通用的 MCP 工具调用方法
             * 
             * 被 @Tool 注解标记，LangChain4j 的 AiServices 会自动识别
             * LLM 会根据工具描述决定何时调用此方法
             */
            @Tool("""
                执行 MCP 工具操作。可用工具包括：
                - read_file: 读取本地文件内容
                - write_file: 写入内容到本地文件
                - list_directory: 列出目录内容
                - execute_python_script: 执行 Python 脚本
                调用格式：{"tool": "工具名称", "args": {"参数名": "参数值"}}
                """)
            public String executeMcpTool(@P("JSON 格式的工具调用请求") String requestJson) {
                // 解析 JSON 请求
                // 获取工具名称和参数
                // 调用 executeTool 方法
                // 返回执行结果
                return "工具执行结果";
            }
        };
    }
}
```

### 3.2 MCP Client 调用链路

从 LLM 决策到 MCP 工具执行完毕的完整调用链路：

```java
/**
 * MCP 工具调用链路 —— 完整流程
 * 
 * 用户请求 → LLM 决策 → MCP 工具发现 → 工具执行 → 结果返回
 * 
 * 此服务类封装了完整的调用链路，供 Controller 层调用
 */
@Service
public class McpToolCallService {

    private static final Logger log = LoggerFactory.getLogger(McpToolCallService.class);

    /** Skills Agent，管理 MCP 工具 */
    private final SkillsAgent skillsAgent;

    /** LangChain4j 的 AiServices，用于构建 AI 助手 */
    private final AiServices<?> aiServices;

    public McpToolCallService(SkillsAgent skillsAgent) {
        this.skillsAgent = skillsAgent;

        // 构建 AiServices，将 MCP 工具注册为 LangChain4j 的 @Tool
        this.aiServices = AiServices.builder(Object.class)
                .chatModel(createChatModel())                  // 设置 LLM 模型
                .tools(skillsAgent.createToolObject())          // 注册 MCP 工具
                .build();
    }

    /**
     * 处理用户消息 —— 完整调用链路
     * 
     * 流程：
     * 1. 用户发送消息
     * 2. LLM 根据上下文判断是否需要调用工具
     * 3. 如果需要，LLM 生成工具调用请求（Function Calling）
     * 4. LangChain4j 自动路由到对应的 @Tool 方法
     * 5. @Tool 方法内部调用 MCP Client 执行工具
     * 6. 工具执行结果返回给 LLM
     * 7. LLM 根据结果生成最终回复
     * 
     * @param userMessage 用户消息
     * @return AI 回复
     */
    public String processMessage(String userMessage) {
        log.info("处理用户消息，启动 MCP 工具调用链路");

        // 第 1 步：用户发送消息
        // 第 2 步：LLM 分析消息，判断是否需要调用工具
        // 第 3 步：如果需要，LLM 生成 Function Calling 请求
        // 第 4 步：AiServices 自动路由到 @Tool 方法
        // 第 5 步：@Tool 方法内部调用 MCP Server
        // 第 6 步：工具执行结果返回给 LLM
        // 第 7 步：LLM 生成最终回复
        return aiServices.chat(userMessage);
    }

    /**
     * 流式处理用户消息
     * 
     * 支持 SSE 流式输出，用户可以实时看到 LLM 的思考过程
     * 包括工具调用的中间状态
     */
    public TokenStream processMessageStream(String userMessage) {
        return aiServices.chat(userMessage);
    }

    private ChatLanguageModel createChatModel() {
        // 创建 LLM 模型实例
        // 此处省略具体实现
        return null;
    }
}
```

### 3.3 SSE 传输的完整实现

SSE 传输在 ruoyi-ai 中的完整实现，包括服务端推送和客户端接收：

```java
/**
 * MCP SSE 传输控制器 —— 处理 SSE 连接的 HTTP 端点
 * 
 * 作为 MCP Server 的传输层，通过 SSE 协议推送事件
 * 客户端通过 HTTP POST 发送请求，服务端通过 SSE 推送响应
 * 
 * 端点说明：
 * - POST /mcp/sse：建立 SSE 连接，服务端持续推送事件
 * - POST /mcp/message：客户端发送消息到服务端
 */
@RestController
@RequestMapping("/mcp")
public class McpSseController {

    private static final Logger log = LoggerFactory.getLogger(McpSseController.class);

    /** MCP Server 实例 */
    private final McpServer mcpServer;

    /** SSE 发射器管理器，管理所有活跃的 SSE 连接 */
    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    public McpSseController(McpServer mcpServer) {
        this.mcpServer = mcpServer;
    }

    /**
     * 建立 SSE 连接
     * 
     * 客户端通过此端点建立 SSE 长连接
     * 服务端通过 SseEmitter 持续推送事件
     * 连接建立后，服务端会发送 initialized 事件确认
     * 
     * @return SseEmitter SSE 发射器，保持长连接
     */
    @PostMapping("/sse")
    public SseEmitter establishSseConnection() {
        // 创建 SSE 发射器，超时时间设为 0 表示不超时
        SseEmitter emitter = new SseEmitter(0L);

        // 生成唯一的会话 ID
        String sessionId = UUID.randomUUID().toString();
        emitters.put(sessionId, emitter);

        log.info("建立 SSE 连接：sessionId={}", sessionId);

        // 设置回调：连接完成和超时时的清理
        emitter.onCompletion(() -> {
            log.info("SSE 连接完成：sessionId={}", sessionId);
            emitters.remove(sessionId);
        });
        emitter.onTimeout(() -> {
            log.warn("SSE 连接超时：sessionId={}", sessionId);
            emitters.remove(sessionId);
        });

        // 发送 initialized 事件，通知客户端连接已就绪
        try {
            emitter.send(SseEmitter.event()
                    .name("initialized")            // 事件名称：initialized
                    .data("{\"status\":\"ok\"}"));  // 事件数据：JSON 格式
        } catch (IOException e) {
            log.error("发送 initialized 事件失败", e);
            emitters.remove(sessionId);
            throw new RuntimeException("SSE 连接初始化失败", e);
        }

        return emitter;
    }

    /**
     * 接收客户端消息
     * 
     * 客户端通过 HTTP POST 发送消息到此端点
     * 消息体包含 MCP 协议请求（如 list_tools、call_tool）
     * 服务端处理后将结果通过 SSE 推送
     * 
     * @param sessionId 会话 ID（从请求头中获取）
     * @param request   MCP 请求体
     */
    @PostMapping("/message")
    public void handleMessage(
            @RequestHeader("X-Session-Id") String sessionId,
            @RequestBody McpRequest request) {

        log.info("收到 MCP 消息：sessionId={}, method={}", sessionId, request.getMethod());

        SseEmitter emitter = emitters.get(sessionId);
        if (emitter == null) {
            throw new IllegalArgumentException("无效的会话 ID: " + sessionId);
        }

        // 异步处理请求，避免阻塞 SSE 连接
        CompletableFuture.runAsync(() -> {
            try {
                // 根据请求方法分发处理
                Object result;
                switch (request.getMethod()) {
                    case "list_tools":              // 列出所有可用工具
                        result = mcpServer.listTools();
                        break;
                    case "call_tool":               // 调用指定工具
                        result = mcpServer.callTool(
                                request.getToolName(),
                                request.getArguments());
                        break;
                    default:
                        throw new IllegalArgumentException("未知方法: " + request.getMethod());
                }

                // 通过 SSE 推送处理结果
                emitter.send(SseEmitter.event()
                        .name("result")             // 事件名称：result
                        .data(toJson(result)));     // 事件数据：序列化为 JSON

            } catch (Exception e) {
                log.error("处理 MCP 消息失败", e);
                try {
                    // 推送错误事件
                    emitter.send(SseEmitter.event()
                            .name("error")
                            .data("{\"error\":\"" + e.getMessage() + "\"}"));
                } catch (IOException ex) {
                    log.error("发送错误事件失败", ex);
                }
            }
        });
    }

    /**
     * 将对象转为 JSON 字符串
     */
    private String toJson(Object obj) {
        // 使用 Jackson 序列化
        // 此处省略具体实现
        return "{}";
    }
}
```

---

## 四、面试高频题

### Q1: MCP 协议解决了什么问题？和传统 API 调用有什么区别？

**回答思路：**

MCP（Model Context Protocol）解决了 **AI 应用与外部工具/数据源之间"碎片化集成"** 的问题。

**在没有 MCP 之前：**
- 每个 AI 应用需要为每个工具编写自定义集成代码
- 新增工具需要修改 Agent 代码，重新部署
- 工具的描述格式不统一，LLM 难以准确理解工具用途
- 工具调用的错误处理、重试、超时等需要各自实现

**MCP 带来的改变：**
- 统一标准协议，一次实现 MCP Server，所有兼容 MCP 的 AI 应用都可以直接使用
- 工具动态发现，LLM 可以在运行时了解有哪些工具可用、怎么用
- 工具描述标准化（JSON Schema），LLM 准确理解工具参数
- 生命周期管理标准化，连接、心跳、重连、关闭由 MCP Client 统一管理

**与传统 API 调用的核心区别：**

| 维度 | 传统 API 调用 | MCP 协议调用 |
|------|-------------|-------------|
| **调用方** | 人工开发者（写代码调用） | AI 模型（自动决策调用） |
| **接口发现** | 阅读 API 文档，手动编码 | 运行时动态发现，自动理解 |
| **参数描述** | API 文档中自然语言描述 | JSON Schema 标准化描述 |
| **调用决策** | 开发者决定何时调用 | LLM 根据上下文自动决定 |
| **错误处理** | 开发者手动处理 | MCP 协议内置错误码和重试机制 |
| **协议标准** | 各服务自定义 REST 风格 | 统一标准协议，跨语言跨平台 |

**一句话总结：** 传统 API 是"人→机器"的接口，MCP 是"AI→工具"的接口，让 AI 能够像人一样发现和使用工具。

---

### Q2: 项目中如何实现 SSE 传输的 MCP？

**回答思路：**

在 ruoyi-ai 项目中，SSE 传输的 MCP 实现分为三个层面：

**第 1 层：MCP Server 端（工具提供方）**

```java
// 1. 创建 MCP Server，注册工具
McpServer server = McpServer.builder()
        .serverInfo("file-system-server", "1.0.0")
        .build();

// 2. 注册工具（文件读取、写入、目录列出）
server.addTool(McpTool.builder()
        .name("read_file")
        .description("读取本地文件")
        .inputSchema(...)
        .handler(request -> { ... })
        .build());

// 3. 通过 SSE 控制器暴露端点
// POST /mcp/sse → 建立 SSE 连接
// POST /mcp/message → 接收客户端消息
```

**第 2 层：MCP Client 端（工具调用方）**

```java
// 1. 创建 SSE 传输层
McpTransport transport = McpTransport.sse("http://server:8080/mcp/sse");

// 2. 创建 MCP Client
McpClient client = McpClient.builder()
        .transport(transport)
        .requestTimeout(Duration.ofSeconds(30))
        .build();

// 3. 初始化连接
client.initialize();

// 4. 发现工具
List<McpTool> tools = client.listTools();

// 5. 调用工具
McpToolResult result = client.callTool("read_file", args);
```

**第 3 层：与 LangChain4j 集成**

```java
// 将 MCP 工具包装为 @Tool 注解方法
@Tool("读取文件内容")
public String readFile(@P("文件路径") String path) {
    return mcpClient.callTool("read_file", Map.of("filePath", path));
}

// 在 AiServices 中注册
AiServices.builder(Assistant.class)
        .chatModel(model)
        .tools(new McpToolWrapper(mcpClient))  // 注册 MCP 工具
        .build();
```

**SSE 传输的核心流程：**

```
客户端（Agent）                    服务端（MCP Server）
     │                                │
     │──── POST /mcp/sse ────────────→│  建立 SSE 连接
     │←── SSE: initialized ──────────│  连接确认
     │                                │
     │──── POST /mcp/message ────────→│  发送 list_tools 请求
     │    (header: X-Session-Id)      │
     │←── SSE: result (工具列表) ────│  返回工具列表
     │                                │
     │──── POST /mcp/message ────────→│  发送 call_tool 请求
     │    (header: X-Session-Id)      │
     │←── SSE: result (执行结果) ────│  返回工具执行结果
     │                                │
     │──── POST /mcp/message ────────→│  发送 close 请求
     │←── SSE: closed ───────────────│  连接关闭
```

**项目中的具体实现要点：**
1. `SseEmitter` 管理：使用 `ConcurrentHashMap` 管理多个 SSE 连接，每个连接有一个唯一 sessionId
2. 异步处理：`CompletableFuture` 异步处理工具调用，不阻塞 SSE 连接
3. 心跳机制：定期发送心跳事件，保持连接活跃
4. 异常处理：工具执行失败时通过 SSE 推送 error 事件

---

### Q3: MCP 工具的安全性如何保障？

**回答思路：**

MCP 工具的安全性是一个多层次的问题，需要从以下维度全面保障：

**1. 路径安全（文件系统工具）**

```java
/**
 * 路径安全检查器 —— 防止路径穿越攻击
 * 
 * 攻击者可能通过 ../../etc/passwd 等方式访问敏感文件
 * 必须对工具传入的文件路径进行严格校验
 */
@Component
public class PathSecurityValidator {

    /** 允许访问的根目录（白名单），从配置读取 */
    private final List<Path> allowedRoots;

    /** 禁止访问的敏感路径模式 */
    private final List<Pattern> forbiddenPatterns = List.of(
            Pattern.compile("^\\.\\./"),                     // 相对路径穿越
            Pattern.compile(".*/etc/passwd$"),               // 系统敏感文件
            Pattern.compile(".*/windows/system32/.*"),       // 系统敏感目录（Windows）
            Pattern.compile(".*/\\..*")                      // 任何隐藏目录
    );

    public PathSecurityValidator() {
        // 从配置文件中读取允许的根目录
        // allowedRoots = config.getAllowedDirectories()
        this.allowedRoots = List.of(
                Path.of("D:/ruoyi-ai/data"),     // 项目数据目录
                Path.of("D:/ruoyi-ai/temp")      // 临时文件目录
        );
    }

    /**
     * 验证路径是否安全
     * 
     * @param userInputPath 用户传入的路径
     * @throws SecurityException 如果路径不安全
     */
    public void validatePath(String userInputPath) {
        // 1. 规范化路径（解析 .. 和 .）
        Path normalizedPath = Path.of(userInputPath).normalize().toAbsolutePath();

        // 2. 检查是否在允许的根目录下
        boolean isAllowed = allowedRoots.stream()
                .anyMatch(root -> normalizedPath.startsWith(root));
        if (!isAllowed) {
            throw new SecurityException("路径不在允许的访问范围内: " + userInputPath);
        }

        // 3. 检查是否匹配禁止模式
        for (Pattern pattern : forbiddenPatterns) {
            if (pattern.matcher(userInputPath).matches()) {
                throw new SecurityException("路径包含敏感内容: " + userInputPath);
            }
        }
    }
}
```

**2. 执行安全（Python 脚本工具）**

```java
/**
 * Python 脚本执行安全检查器
 * 
 * 防止恶意脚本执行，限制脚本可访问的资源
 */
@Component
public class ScriptSecuritySandbox {

    /**
     * 验证脚本是否安全执行
     * 
     * 安全措施：
     * 1. 脚本大小限制：防止超大脚本
     * 2. 脚本内容扫描：禁止危险操作（import os.system、subprocess 等）
     * 3. 执行超时控制：防止死循环
     * 4. 资源限制：内存、CPU、网络访问
     * 5. 结果大小限制：防止输出过大
     */
    public void validateScript(String script) {
        // 1. 脚本大小限制
        if (script.length() > 100_000) {  // 100KB 上限
            throw new SecurityException("脚本内容过长");
        }

        // 2. 禁止危险操作
        List<String> dangerousPatterns = List.of(
                "import os",            // 系统操作
                "import subprocess",    // 子进程
                "import shutil",        // 文件系统操作
                "__import__",           // 动态导入
                "eval(",                // 动态执行
                "exec(",                // 动态执行
                "open(",                // 文件操作（应通过 MCP 文件工具）
                "with open"             // 文件操作
        );

        for (String pattern : dangerousPatterns) {
            if (script.contains(pattern)) {
                throw new SecurityException("脚本包含禁止操作: " + pattern);
            }
        }

        // 3. 限制脚本只能使用白名单库
        List<String> allowedLibraries = List.of(
                "json", "csv", "re", "math", "datetime",
                "collections", "itertools", "functools",
                "pandas", "numpy", "openpyxl", "python-docx"
        );
        // 检查 import 语句是否都在白名单中
        // 此处省略具体实现
    }
}
```

**3. 访问控制（SSE 连接安全）**

```java
/**
 * SSE 连接安全配置
 * 
 * 确保只有授权的 Agent 可以连接到 MCP Server
 */
@Configuration
public class McpSecurityConfig {

    /**
     * 配置 SSE 端点的安全策略
     * 
     * 1. 身份认证：只有经过认证的 Agent 才能连接
     * 2. API Key 验证：每个连接请求需要携带有效的 API Key
     * 3. IP 白名单：限制只允许内网 IP 连接
     * 4. 速率限制：防止滥用
     * 5. 连接数限制：防止资源耗尽
     */
    @Bean
    public SecurityFilterChain mcpSecurityFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher("/mcp/**")          // 只拦截 /mcp/ 路径
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/mcp/sse").authenticated()     // SSE 连接需要认证
                        .requestMatchers("/mcp/message").authenticated() // 消息需要认证
                        .anyRequest().denyAll())                          // 其他拒绝
                .oauth2ResourceServer(OAuth2ResourceServerConfigurer::jwt); // JWT 认证
        return http.build();
    }
}
```

**4. 运行时安全监控**

| 安全维度 | 具体措施 | 实现方式 |
|---------|---------|---------|
| **身份认证** | 只有授权的 Agent 能调用 MCP 工具 | JWT / API Key 验证 |
| **路径白名单** | 限制文件操作只能访问指定目录 | 路径规范化 + 前缀匹配 |
| **操作审计** | 记录所有工具调用日志 | AOP 切面 + 数据库持久化 |
| **速率限制** | 防止单个 Agent 过度调用工具 | Guava RateLimiter / Sentinel |
| **超时控制** | 工具执行超时自动终止 | CompletableFuture.orTimeout() |
| **资源隔离** | 不同 Agent 的资源隔离 | 沙箱 / 容器隔离 |
| **输入校验** | 严格校验工具参数 | JSON Schema 校验 + 自定义校验器 |
| **输出过滤** | 防止敏感信息泄露 | 敏感词过滤 + 数据脱敏 |

**5. 配置示例（安全相关的配置项）**

```yaml
mcp:
  security:
    # 路径白名单：文件系统工具只能访问这些目录
    allowed-directories:
      - D:/ruoyi-ai/data
      - D:/ruoyi-ai/temp
    
    # 禁止访问的路径模式
    forbidden-paths:
      - "**/etc/**"
      - "**/windows/**"
      - "**/.git/**"
    
    # 脚本执行安全
    script:
      max-length: 100000       # 脚本最大长度
      timeout-seconds: 30      # 执行超时
      allowed-libraries:       # 白名单库
        - json, csv, re, math
        - pandas, numpy, openpyxl
    
    # 连接限制
    connection:
      max-per-client: 5        # 每个客户端最大连接数
      idle-timeout-seconds: 300  # 空闲超时
    
    # 审计日志
    audit:
      enabled: true            # 开启审计日志
      log-all-tools: true      # 记录所有工具调用
```

---

## 五、面试避坑指南

### 5.1 不要混淆 MCP 与 Function Calling

**常见错误：** 面试时说"MCP 就是 Function Calling"。

**纠正：** Function Calling 是 LLM 自身的能力——LLM 根据 tool 描述决定调用哪个函数。MCP 是工具注册和发现协议——它定义了工具如何注册、描述、被调用。MCP 可以看作是 Function Calling 的"基础设施层"，让工具不再是硬编码的，而是可以通过 MCP 协议动态发现和调用。

**正确表述：** "MCP 是工具层协议，Function Calling 是 LLM 能力。MCP 负责工具的组织和发现，Function Calling 负责工具的决策和执行。两者配合使用：MCP 告诉 LLM '有什么工具可用'，Function Calling 让 LLM 决定 '什么时候调用哪个工具'。"

### 5.2 不要忽略 MCP 的传输层差异

**常见错误：** 只关注 MCP 的协议层面，忽略传输层的实现差异。

**关键点：**
- **stdio** 适合本地工具，子进程生命周期由 MCP Client 管理，性能最好
- **SSE** 适合远程工具，基于 HTTP 长连接，适合跨网络调用
- **Streamable HTTP** 是 newer 的传输方式，结合了 HTTP 的简单和流式的能力
- 面试中要能说清楚不同传输方式的适用场景和 trade-off

### 5.3 不要忽略 MCP 与 API Gateway 的区别

**常见错误：** 面试官问 MCP 时，回答"和 API Gateway 差不多"。

**纠正：** API Gateway 是面向人工客户端（浏览器、移动端）的请求路由和治理层，关注的是"请求怎么路由、限流、鉴权"。MCP 是面向 AI 模型的工具发现和调用协议，关注的是"让 AI 模型自动理解有什么工具可用、什么时候调用、怎么调用"。

**对比要点：**
- API Gateway 的调用方是"人写的代码"，MCP 的调用方是"AI 模型"
- API Gateway 需要开发者手动编码调用，MCP 由 LLM 自动决策调用
- API Gateway 的接口文档是给人看的，MCP 的工具描述是给 AI 看的

### 5.4 不要忽略 MCP 工具的安全边界

**常见错误：** 只讲 MCP 有多强大，不提安全风险。

**关键点：** MCP 让 AI Agent 直接操作文件系统、执行脚本，安全性是首要考虑：
- 文件系统工具必须做路径白名单校验，防止路径穿越攻击
- 脚本执行工具必须做沙箱隔离，限制危险操作
- 远程工具的 SSE 连接必须做身份认证
- 所有工具调用必须记录审计日志
- 面试中要主动提到安全设计，这体现工程素养

### 5.5 不要忽略 MCP 在 Java 生态中的具体实现

**常见错误：** 只讲 MCP 概念，不讲在 Java 中怎么落地。

**关键点：** 在 Java 生态中集成 MCP 主要依赖 LangChain4j：
- `langchain4j-mcp` 模块：提供 MCP Server 和 Client 的核心实现
- `langchain4j-agentic-mcp` 模块：提供与 LangChain4j Agent 的集成
- `McpServer.builder()` 创建 Server，`McpClient.builder()` 创建 Client
- 通过 `@Tool` 注解将 MCP 工具注册到 LangChain4j 的 AiServices
- Spring Boot 中通过 `@Component` 管理 MCP 组件的生命周期

### 5.6 不要忘记 MCP 的三大原语

**常见错误：** 只讲 Tools（工具），忽略 Resources（资源）和 Prompts（提示模板）。

**关键点：** MCP 协议定义了三个核心原语：
- **Tools**：可执行的操作，LLM 可以调用（最常用，面试重点）
- **Resources**：只读数据源，类似于 REST API 的 GET 请求，提供结构化数据
- **Prompts**：预定义的提示模板，帮助 LLM 更好地处理特定场景

在面试中展示对三个原语的理解，说明你不仅会使用 MCP，还理解其完整设计。

---

## 六、参考资料与扩展阅读

### 项目源码
- [ruoyi-ai GitHub 仓库](https://github.com/1byteone/ruoyi-ai) — `ruoyi-chat/mcp/` 和 `ruoyi-chat/agent/` 模块

### MCP 协议官方
- [Model Context Protocol 官方文档](https://modelcontextprotocol.io) — 协议规范、架构说明、快速入门
- [MCP 协议规范 GitHub](https://github.com/modelcontextprotocol/specification) — 协议规范的完整定义

### LangChain4j MCP 集成
- [LangChain4j MCP 模块文档](https://docs.langchain4j.dev/tutorials/mcp) — MCP Server 和 Client 的使用教程
- [LangChain4j GitHub 仓库](https://github.com/langchain4j/langchain4j) — `langchain4j-mcp` 和 `langchain4j-agentic-mcp` 模块源码

### 传输层技术
- [SSE (Server-Sent Events) 规范](https://html.spec.whatwg.org/multipage/server-sent-events.html) — W3C 标准
- [Spring Boot SSE 支持](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-async.html#mvc-ann-async-sse) — `SseEmitter` 的使用文档

### 安全实践
- OWASP Path Traversal 防护指南 — 文件系统工具的安全设计参考
- Spring Security 参考文档 — SSE 端点的认证和授权配置