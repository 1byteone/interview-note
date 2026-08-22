# 🤖 yu-ai-agent 完整源码级剖析 — Spring AI Agent 实战

> 基于 Spring AI 1.0 + Java 21 的 ReAct 智能体 + RAG + MCP 全栈实现

---

## 一、项目概览

| 维度 | 信息 |
|------|------|
| **仓库** | [github.com/liyupi/yu-ai-agent](https://github.com/liyupi/yu-ai-agent) |
| **Stars** | 2,622 ⭐ / 602 Fork |
| **Java 版本** | **21** (虚拟线程) |
| **Spring Boot** | **3.4.4** |
| **AI 框架** | **Spring AI 1.0.0** + LangChain4j 1.0.0-beta2 (对比) |
| **LLM** | 阿里云 qwen-plus (DashScope) |
| **向量库** | PgVector (PostgreSQL) |
| **Topics** | spring-ai, langchain4j, mcp, rag, vector-database |

---

## 二、模块结构

```
yu-ai-agent/
├── src/main/java/com/yupi/yuaiagent/
│   ├── YuAiAgentApplication.java
│   ├── controller/AiController.java       ← REST 入口
│   ├── app/LoveApp.java                   ← AI 应用层（5 种模式）
│   ├── agent/                             ← 🤖 Agent 体系
│   │   ├── BaseAgent.java                 ← 抽象基类
│   │   ├── ReActAgent.java                ← ReAct 模式抽象
│   │   ├── ToolCallAgent.java             ← 工具调用实现
│   │   ├── YuManus.java                   ← 最终 Agent 产品
│   │   └── model/AgentState.java          ← 状态枚举
│   ├── advisor/                           ← Spring AI Advisor 链
│   ├── rag/                               ← RAG 全链路
│   ├── tools/                             ← 7 个工具
│   ├── chatmemory/                        ← 对话记忆
│   └── demo/invoke/                       ← 5 种 AI 调用方式演示
│
├── yu-image-search-mcp-server/            ← MCP Server 独立模块
└── yu-ai-agent-frontend/                  ← Vue 3 前端
```

---

## 三、Agent 四层继承体系（核心设计亮点）

```
┌─────────────────────────────────────────────────┐
│              BaseAgent (抽象基类)                  │
│  - state: AgentState (IDLE→RUNNING→FINISHED/ERR) │
│  - messageList: 自主维护会话上下文                   │
│  - run() / runStream() 执行循环 (maxSteps=10)     │
│  - abstract step()                                │
└──────────────────────┬──────────────────────────┘
                       ↓
┌─────────────────────────────────────────────────┐
│              ReActAgent (ReAct 模式)              │
│  - abstract think(): boolean (是否需要工具)        │
│  - abstract act(): String (执行工具)              │
│  - step() = think() → act()                      │
└──────────────────────┬──────────────────────────┘
                       ↓
┌─────────────────────────────────────────────────┐
│           ToolCallAgent (工具调用实现)              │
│  - think(): 调 LLM → 解析 ToolCalls → 判断        │
│  - act(): ToolCallingManager.executeToolCalls()  │
│  - 关键: 禁用 Spring AI 自动执行, 手动控制循环      │
│  - 检测 TerminateTool → 设 FINISHED              │
└──────────────────────┬──────────────────────────┘
                       ↓
┌─────────────────────────────────────────────────┐
│             YuManus (最终产品)                     │
│  - name: "yuManus"                               │
│  - maxSteps: 20                                  │
│  - 注入全部 7 个工具                               │
│  - 专用 ChatClient + LoggerAdvisor               │
└─────────────────────────────────────────────────┘
```

---

## 四、Tool Calling — 核心机制

### 4.1 关键设计：禁用自动执行

```java
// ToolCallAgent.java — 这是理解 Agent 自主决策的关键
this.chatOptions = DashScopeChatOptions.builder()
    .withInternalToolExecutionEnabled(false)  // ← 禁用 Spring AI 自动执行
    .build();

// think() 方法
public boolean think() {
    messageList.add(new UserMessage(nextStepPrompt));
    
    // 让 LLM 决定调哪些工具
    ChatResponse response = chatClient.prompt()
        .messages(messageList)
        .tools(availableTools)
        .options(chatOptions)
        .call();
    
    List<ToolCall> toolCalls = response.getResult()
        .getOutput().getToolCalls();
    
    if (toolCalls.isEmpty()) {
        // LLM 认为不需要工具 → 返回 false → 结束
        messageList.add(response.getResult().getOutput());
        return false;
    }
    
    // LLM 要调工具 → 返回 true → 触发 act()
    return true;
}
```

### 4.2 act() 执行流程

```java
public String act() {
    // 使用 Spring AI 的 ToolCallingManager 执行
    ToolCallResult toolCallResult = toolCallingManager
        .executeToolCalls(chatRequest);
    
    // 将工具结果写回上下文
    messageList.addAll(toolCallResult.toolCallResults());
    
    // 检查是否调了 TerminateTool
    if (toolCallResult.toolCallResults().stream()
        .anyMatch(r -> r.name().equals("terminate"))) {
        this.state = AgentState.FINISHED;
    }
    
    return toolCallResult.toolExecutionResult();
}
```

### 4.3 7 个工具清单

| 工具 | 功能 | 实现 |
|------|------|------|
| `WebSearchTool` | 联网搜索 | SearchAPI (Baidu engine) |
| `WebScrapingTool` | 网页抓取 | Jsoup HTML 解析 |
| `FileOperationTool` | 文件读写 | Java NIO |
| `TerminalOperationTool` | 终端命令 | Runtime.exec |
| `ResourceDownloadTool` | 资源下载 | HTTP Client |
| `PDFGenerationTool` | PDF 生成 | iText 9.x |
| `TerminateTool` | 终止 Agent | 设置终止标志 |

---

## 五、RAG 全链路

### 5.1 文档加载

```java
// LoveAppDocumentLoader.java
// 加载 classpath:document/*.md (恋爱知识库)
// MarkdownDocumentReader → 提取 metadata (单身/恋爱/已婚状态)
```

### 5.2 三种向量存储方案

| 方案 | 配置 | 适用场景 |
|------|------|---------|
| Spring AI 内置 | `QuestionAnswerAdvisor` | 默认，简单场景 |
| PgVector | `PgVectorStore` + PostgreSQL | 生产环境 |
| 阿里云百炼 | 云 RAG 服务 | 云端部署 |

### 5.3 查询增强

- **QueryRewriter**: 优化用户原始查询
- **MyKeywordEnricher**: 关键词丰富
- **按状态过滤**: `LoveAppRagCustomAdvisorFactory` — 按单身/恋爱/已婚过滤文档

---

## 六、MCP 协议实战

### 6.1 客户端集成

```json
// mcp-servers.json
{
  "mcpServers": {
    "amap-maps": {
      "command": "npx.cmd",
      "args": ["-y", "@amap/amap-maps-mcp-server"]
    },
    "yu-image-search-mcp-server": {
      "command": "java",
      "args": ["-jar", "yu-image-search-mcp-server-0.0.1-SNAPSHOT.jar"]
    }
  }
}
```

### 6.2 自研 MCP Server

`yu-image-search-mcp-server` — 独立 Spring Boot 应用，支持 SSE 和 stdio 双模式。

---

## 七、5 种 AI 调用方式（demo 层）

| 方式 | 类名 | 说明 |
|------|------|------|
| HTTP 直接调 | HttpAiInvoke | 原生 HTTP 请求 |
| SDK 调用 | SdkAiInvoke | DashScope SDK |
| Spring AI | SpringAiAiInvoke | ChatClient |
| LangChain4j | LangChainAiInvoke | 对比教学 |
| Ollama 本地 | OllamaAiInvoke | 本地模型推理 |

---

## 八、面试话术

> "我基于 Spring AI 1.0 实现了一个 ReAct 模式的自主规划智能体。核心设计是四层 Agent 继承体系：BaseAgent 管理状态机和执行循环，ReActAgent 定义 Think-Act 循环，ToolCallAgent 实现具体的工具调用逻辑，YuManus 是最终产品。
>
> 关键技术点：我禁用了 Spring AI 的内置工具执行，手动控制 think/act 循环，让 Agent 能自主决定何时调用工具、何时结束。支持 SSE 流式输出，每一步结果实时推送到前端。
>
> 集成了 RAG 知识库（PgVector）、MCP 协议（既做 Client 也做 Server）、7 种工具（搜索、抓取、文件、终端、下载、PDF、终止）。"

---

*此文档涵盖 Agent 开发的核心知识点，适合作为 AI 方向面试的深度知识储备*
