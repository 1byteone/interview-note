# 🌐 E05 · MCP 协议生态

> **生态定位**: 跨平台 Agent 工具调用标准协议  
> **生态规模**: 1 个核心仓库 | 交叉关联 E01/E02/E03/E04/E06（全生态通用）  
> **技术本质**: Model Context Protocol — 连接 LLM 与外部工具的标准化接口

---

## 1. 生态全景

### 1.1 生态定位

MCP（Model Context Protocol）是 Anthropic 提出的 AI Agent 工具调用标准协议，正成为跨平台 Agent 的通用接口层：

| 角色 | 仓库 | 说明 |
|------|------|------|
| 🏆 入门指南 | MCP-Chinese-Getting-Started-Guide | 中文社区 MCP 入门第一站 |

### 1.2 MCP 在生态中的位置

```
                   LLM (DeepSeek/GPT/Claude)
                        │
                   ┌────┴────┐
                   │  MCP    │ ← E05 生态
                   │  Protocol│
                   └────┬────┘
                        │
         ┌──────────────┼──────────────┐
         │              │              │
    Claude Code    Codex CLI      DSH / Hermes
     (E01)          (E02)         (E03/E04)
```

MCP 是连接所有生态的通用协议，E05 既是独立生态，也是其他生态的基础设施。

---

## 2. 核心仓库详解

### 2.1 🏆 liaokongVFX/MCP-Chinese-Getting-Started-Guide

| 字段 | 值 |
|------|-----|
| **全名** | liaokongVFX/MCP-Chinese-Getting-Started-Guide |
| **Stars** | 3,560 |
| **定位** | MCP 编程极速入门，10 分钟上手 |
| **语言** | 中文 |
| **内容体量** | 约 26,000 字（README 即正文） |
| **独特价值** | 中文社区 MCP 入门第一站，全程带可运行代码，全链路覆盖 |

**章节结构**：
1. MCP 概念介绍（Resources/Prompts/Tools/Sampling/Roots/Transports）
2. 开发 MCP 服务器（Python + FastMCP + web_search 示例）
3. 调试 MCP 服务器（Inspector）
4. 开发 MCP 客户端（stdio_client + DeepSeek 调用）
5. Sampling 讲解（人工监督删除文件示例）
6. Claude Desktop 加载 MCP Server
7. 在 LangChain 中使用 MCP
8. DeepSeek + cline + 自定义 MCP
9. Serverless 部署

**技术栈**：Python, FastMCP, uv, DeepSeek, LangChain, Serverless

---

## 3. 交叉引用

| 关联生态 | 关联仓库 | 关联点 |
|----------|----------|--------|
| **E01 Claude Code** | claude-code-ultimate-guide | Claude Code 的 MCP 服务器配置 |
| **E02 Codex** | CodexGuide | Codex 与 MCP 服务器集成 |
| **E03 Harness** | deepeseek-harness-guide | DSH 的 MCP 集成 |
| **E03 Harness** | harness_engineering_guide | MCP 协议集成章节 |
| **E04 Hermes** | hermes-agent-guide | Hermes 的 MCP 协议与自动化 |
| **E06 通识** | ai-system-design-guide | MCP 2.0 协议深度分析 |
| **E06 通识** | AgentGuide | Agent 工具调用中的 MCP 实践 |

---

## 4. 生态内学习路径

```
① MCP 概念理解 (README 简介，15 分钟)
    ↓
② 开发 MCP 服务器 (Python + FastMCP，1 小时)
    ↓
③ 调试 MCP 服务器 (Inspector，30 分钟)
    ↓
④ 开发 MCP 客户端 (stdio_client，1 小时)
    ↓
⑤ 接入 LLM (DeepSeek/Claude Desktop，1 小时)
    ↓
⑥ 在 LangChain 中使用 MCP (1 小时)
    ↓
⑦ Serverless 部署 (30 分钟)
```

---

## 5. 生态 SWOT

| 优势 | 劣势 |
|------|------|
| 跨生态通用协议，覆盖面广 | 协议本身仍在演进（MCP 2.0） |
| 中文入门指南完善 | 深度资料较少 |
| 生态支持良好（Claude/DSH/Hermes 均支持） | 非 Anthropic 的 LLM 支持度参差 |

| 机会 | 威胁 |
|------|------|
| MCP 2.0 带来更多能力 | A2A 协议可能分流 |
| 成为 Agent 工具调用的 HTTP 级标准 | 厂商各自定义协议 |