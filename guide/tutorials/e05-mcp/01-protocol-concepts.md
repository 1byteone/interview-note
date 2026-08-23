# MCP 协议原理与核心概念

> 本文档属于 **E05 MCP 协议生态** 系列教程的第一篇，系统介绍 Model Context Protocol 的设计理念、协议基础、核心概念和架构原理，为后续的 Server 开发与 Client 集成打下理论基础。

---

## 1. 什么是 MCP

Model Context Protocol（简称 MCP）是由 Anthropic 于 2024 年 11 月开源发布的开放协议，旨在为大语言模型（LLM）提供一种标准化的方式来访问外部数据源和工具。

### 1.1 MCP 解决的问题

在 MCP 出现之前，每接入一个外部系统（数据库、API、文件系统等）都需要：

- 为每个 LLM 客户端单独编写集成代码
- 工具的接口定义重复且不兼容
- 模型切换成本高，集成代码无法复用

MCP 通过定义统一的协议规范，让任何 MCP 兼容的客户端（如 Claude Desktop、Codex CLI、Continue 等）都能连接任何 MCP 服务器，实现 **"一次编写，处处可用"** 的工具生态。

### 1.2 核心价值

- **标准化接口**: 所有工具遵循统一的 JSON-RPC 2.0 协议
- **解耦**: LLM 客户端与工具实现完全分离
- **可组合**: 多个 MCP 服务器可以同时挂载
- **开放生态**: 任何人都可以开发 MCP 服务器，贡献到社区

---

## 2. 协议基础：JSON-RPC 2.0

MCP 基于 JSON-RPC 2.0 协议，这是一种轻量级的远程过程调用协议。

### 2.1 消息格式

JSON-RPC 2.0 的消息分为三种：请求、响应和通知。

**请求（Request）**:

```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "method": "tools/list",
  "params": {}
}
```

**响应（Response）**:

```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "result": {
    "tools": [
      {
        "name": "search",
        "description": "Search documents",
        "inputSchema": { "type": "object", "properties": {} }
      }
    ]
  }
}
```

**通知（Notification）**:（无 id，无需响应）

```json
{
  "jsonrpc": "2.0",
  "method": "notifications/initialized",
  "params": {}
}
```

### 2.2 错误处理

当请求处理失败时，返回错误响应：

```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "error": {
    "code": -32601,
    "message": "Method not found",
    "data": { "details": "Unknown method: tools/execute" }
  }
}
```

标准错误码：

| 代码 | 含义 |
|------|------|
| -32700 | Parse error（解析错误） |
| -32600 | Invalid Request（无效请求） |
| -32601 | Method not found（方法不存在） |
| -32602 | Invalid params（参数无效） |
| -32603 | Internal error（内部错误） |

---

## 3. 核心概念

MCP 定义了五个核心概念：Resources、Prompts、Tools、Sampling 和 Roots。

### 3.1 Resources（资源）

Resources 是 MCP 服务器向客户端暴露的**只读数据源**，类似于 REST API 中的 GET 端点。

- 用 URI 唯一标识，如 `file:///config.json` 或 `postgres://users/schema`
- 可以是静态的（如配置文件）或动态的（如数据库查询结果）
- 客户端可以列出所有资源，并按 URI 读取内容
- 适合暴露文件、数据库表、日志等数据

示例资源列表：

```json
{
  "resources": [
    {
      "uri": "file:///logs/app.log",
      "name": "应用日志",
      "description": "当前应用运行日志",
      "mimeType": "text/plain"
    },
    {
      "uri": "postgres://users/schema",
      "name": "用户表结构",
      "description": "数据库用户表的 Schema"
    }
  ]
}
```

### 3.2 Prompts（提示模板）

Prompts 是预先定义的**提示词模板**，类似于快捷指令。

- 帮助用户快速执行常见任务
- 可以接受参数，生成动态提示词
- 适合封装复杂的工作流提示

示例：

```json
{
  "prompts": [
    {
      "name": "code-review",
      "description": "审查代码并提供改进建议",
      "arguments": [
        {
          "name": "language",
          "description": "编程语言",
          "required": true
        },
        {
          "name": "code",
          "description": "要审查的代码",
          "required": true
        }
      ]
    }
  ]
}
```

### 3.3 Tools（工具）

Tools 是 MCP 服务器提供的**可执行函数**，是 MCP 最常用的能力。

- 有明确的输入参数 Schema（基于 JSON Schema）
- 执行后返回结果
- 可以有副作用（写入数据、调用 API）
- 类似于 OpenAI Function Calling，但是是标准化的

示例工具定义：

```json
{
  "tools": [
    {
      "name": "search_users",
      "description": "在数据库中搜索用户",
      "inputSchema": {
        "type": "object",
        "properties": {
          "query": {
            "type": "string",
            "description": "搜索关键词"
          },
          "limit": {
            "type": "integer",
            "description": "返回数量上限",
            "default": 10
          }
        },
        "required": ["query"]
      }
    }
  ]
}
```

### 3.4 Sampling（采样）

Sampling 是一种特殊机制，允许**服务器反向请求客户端的 LLM** 完成推理任务。

- 服务器可以向客户端发送 `sampling/createMessage` 请求
- 客户端用自己的 LLM 生成响应并返回
- 适用于服务器需要 LLM 能力但没有自带模型的场景
- 是 MCP 中比较高级和少见的用法

典型流程：

```
MCP Server: "请帮我分析这段文本的情感"
  → Client (Claude/GPT) 生成响应
    → MCP Server 获得分析结果
```

### 3.5 Roots（根目录）

Roots 定义了客户端**允许服务器访问的文件系统范围**。

- 客户端告诉服务器：你可以在这些目录内操作
- 类似于 Codex CLI 的沙盒白名单
- 通常以 URI 形式表示：`file:///home/user/project`

示例：

```json
{
  "roots": [
    {
      "uri": "file:///home/user/project",
      "name": "主项目目录"
    },
    {
      "uri": "file:///home/user/docs",
      "name": "文档目录"
    }
  ]
}
```

---

## 4. Client-Server 架构

MCP 采用客户端-服务器架构：

```
┌──────────────────────┐
│   MCP Client         │
│  (Claude Desktop,    │
│   Codex CLI, etc.)   │
└─────────┬────────────┘
          │
          │ JSON-RPC 2.0 over
          │ STDIO or HTTP
          │
┌─────────▼────────────┐
│   MCP Server         │
│  (Python, Node.js,   │
│   Rust, etc.)        │
└──────────────────────┘
```

### 4.1 客户端职责

- 启动和管理 MCP 服务器进程（STDIO 模式）
- 发送协议消息（初始化、列出资源/工具、调用工具）
- 处理服务器的采样请求
- 维护连接状态和权限控制

### 4.2 服务器职责

- 实现并暴露 Resources、Prompts、Tools
- 响应客户端请求
- 管理内部状态和外部资源连接
- 报告能力（capabilities）给客户端

### 4.3 握手流程

```
1. Client → Server: initialize { capabilities, clientInfo }
2. Server → Client: { capabilities, serverInfo }
3. Client → Server: notifications/initialized
4. 正常通信阶段
5. Client → Server: shutdown (或进程终止)
```

---

## 5. 传输层

MCP 支持两种传输方式：

### 5.1 STDIO 传输

- 客户端启动服务器子进程
- 通过 stdin/stdout 通信，每行一个 JSON 消息
- 适合本地工具，无网络开销
- 主流场景的默认选择

### 5.2 HTTP 传输

- 服务器以 HTTP 服务运行
- 客户端通过 HTTP 请求通信
- 适合远程工具和 Serverless 部署
- 使用 Server-Sent Events (SSE) 支持流式响应

---

## 6. MCP vs A2A 对比

Google 提出的 A2A（Agent-to-Agent）协议与 MCP 经常被放在一起讨论，但定位不同：

| 维度 | MCP | A2A |
|------|-----|-----|
| **全称** | Model Context Protocol | Agent-to-Agent Protocol |
| **提出方** | Anthropic | Google |
| **定位** | LLM 与工具之间的接口 | Agent 与 Agent 之间的通信 |
| **核心问题** | LLM 如何访问外部数据和能力 | 多个 AI Agent 如何协作 |
| **通信模型** | Client-Server | Peer-to-Peer |
| **典型场景** | Claude 调用数据库工具 | 多个 Agent 分工完成复杂任务 |

两者是**互补关系**：MCP 让单个 Agent 获得工具能力，A2A 让多个 Agent 协同工作。

---

## 7. MCP 2.0 路线图

MCP 2.0 是协议的演进版本，主要增强方向包括：

### 7.1 增强的能力

- **Streaming Resources**: 资源支持流式输出，适合日志和实时数据
- **Tool Chaining**: 服务器声明工具间的依赖关系，客户端可以自动编排
- **Authentication**: 标准化的认证框架，支持 OAuth 2.1 等
- **Authorization Scopes**: 细粒度权限控制，类似 GitHub 的 scope 机制

### 7.2 生态增强

- **MCP Registry**: 官方维护的服务器注册表，方便发现和安装
- **标准化测试套件**: 帮助开发者验证服务器的合规性
- **多语言 SDK**: 官方维护 Python、TypeScript、Rust、Go 等多语言 SDK

### 7.3 企业特性

- **可观测性**: 标准化的日志、指标和追踪
- **多租户支持**: 单个服务器服务多个隔离的租户
- **审计日志**: 记录所有工具调用，满足合规要求

---

## 8. 生态现状

### 8.1 主要客户端

- **Claude Desktop**: Anthropic 的桌面应用，原生支持 MCP
- **Codex CLI**: OpenAI 的终端工具，通过 config.toml 配置 MCP
- **Continue**: 开源的 AI 编程助手
- **Cursor**: AI 代码编辑器
- **LangChain / LlamaIndex**: 通过适配器集成

### 8.2 主要服务器

官方和社区提供了大量 MCP 服务器，涵盖：

- **文件系统**: 读写本地文件
- **数据库**: PostgreSQL、MySQL、SQLite 查询
- **版本控制**: GitHub、GitLab 操作
- **云服务**: AWS、GCP、Azure 资源管理
- **协作工具**: Slack、Notion、Linear
- **搜索**: Brave、Google 搜索

---

## 9. 学习路径建议

1. **理论入门**: 阅读本文档，理解 MCP 的核心概念
2. **Server 开发**: 学习 [MCP Server 开发实战](./02-server-development.md)
3. **Client 集成**: 学习 [MCP Client 集成与生产部署](./03-client-integration.md)
4. **实战项目**: 选择一个实际场景（如对接内部数据库），开发 MCP 服务器并集成到 Claude Desktop 或 Codex CLI 中

---

## 参考链接

- [MCP 官方文档](https://modelcontextprotocol.io)
- [MCP GitHub](https://github.com/modelcontextprotocol)
- [Anthropic MCP 公告](https://www.anthropic.com/news/model-context-protocol)
- [MCP Server 开发实战](./02-server-development.md)
- [MCP Client 集成与生产部署](./03-client-integration.md)