# MCP 集成与外部工具扩展

> **生态**: E01 · Claude Code / E05 · MCP 协议 | **等级**: 进阶 | **前置要求**: 了解 Claude Code 项目配置

MCP（Model Context Protocol，模型上下文协议）是 Anthropic 推出的开放标准，定义了 LLM 应用与外部工具/数据源之间的统一接口。在 Claude Code 生态中，MCP 是 Skills 之外的另一种"扩展能力"方式：Skills 注入知识，MCP 注入工具。

本教程从 MCP 协议基础出发，覆盖服务器配置、传输方式、官方服务器列表，以及自定义 MCP 服务器的开发集成，帮助你建立与数据库、API、文件系统等外部资源的安全交互通道。

---

## 1. MCP 协议概述

MCP 借鉴了 LangChain Tool 和 OpenAI Function Calling 的经验，但更强调：

- **标准化**：同一套协议，任意 MCP 服务器可被任意 MCP 客户端调用；
- **双向通信**：客户端发送请求，服务器返回结果，工具调用结果反馈回模型上下文；
- **资源驱动**：除工具调用外，MCP 还支持资源暴露（文件、数据库记录、API 端点）和提示模板。

### 1.1 协议架构

```
┌───────────────────────┐       MCP 协议        ┌──────────────────────┐
│                       │  ◄──────────────►      │                      │
│   Claude Code         │     JSON-RPC 2.0       │   MCP Server         │
│   (MCP Client)        │  STDIO / HTTP(S)       │   (GitHub / DB / FS) │
│                       │                        │                      │
└───────────────────────┘                        └──────────────────────┘
         │                                               │
         ▼                                               ▼
    Skills / Context                               External Systems
    (知识注入)                                    (GitHub、PostgreSQL、文件系统)
```

Claude Code 作为 MCP 客户端，启动时加载所有配置的 MCP 服务器；当用户请求需要访问外部系统时，模型自主决定调用哪个 MCP 工具。

### 1.2 MCP vs Skills 对比

| 维度 | MCP | Skills |
|------|-----|--------|
| 本质 | 工具调用协议 | 知识注入 |
| 执行者 | 外部程序（MCP Server） | Claude 自身 |
| 典型场景 | 查询数据库、调用 GitHub API、读写文件 | 代码审查、提交消息生成、模板渲染 |
| 开发语言 | 任意（Node.js / Python / Go / Rust） | 仅 Markdown |
| 配置位置 | `.claude/settings.json` | `.claude/skills/` |
| 安全边界 | 独立进程隔离 | 工具权限白名单 |

两者互补：Skills 告诉 Claude "怎么做"，MCP 告诉 Claude "用什么做"。

## 2. 配置 MCP 服务器

### 2.1 基本配置格式

在 `.claude/settings.json`（项目级）或 `~/.claude/settings.json`（用户级）的 `mcp.servers` 字段中声明：

```json
{
  "mcp.servers": {
    "github": {
      "type": "stdio",
      "command": "npx",
      "args": ["@anthropic/mcp-servers-github"],
      "env": {
        "GITHUB_TOKEN": "ghp_..."
      }
    },
    "filesystem": {
      "type": "stdio",
      "command": "npx",
      "args": ["@anthropic/mcp-servers-filesystem", "/allowed/path"]
    },
    "postgres": {
      "type": "http",
      "url": "https://mcp-postgres.example.com/rpc",
      "headers": {
        "Authorization": "Bearer sk-..."
      }
    }
  }
}
```

### 2.2 配置字段说明

| 字段 | 说明 | 必填 |
|------|------|------|
| `type` | 传输方式：`stdio`（本地进程）或 `http`（远程服务） | 是 |
| `command` | stdio 模式下启动命令 | 是（stdio） |
| `args` | 命令参数 | 否 |
| `url` | HTTP 模式下端点 URL | 是（http） |
| `headers` | HTTP 请求头（如 API Key） | 否 |
| `env` | 环境变量（安全注入，不暴露到日志） | 否 |
| `disabled` | `true` 时跳过加载 | 否 |

## 3. STDIO vs HTTP 传输

### 3.1 STDIO 传输

服务器作为 Claude Code 的子进程运行，通过标准输入/输出通信：

- **优点**：零网络开销、不暴露端口、本地文件系统原生访问；
- **适用场景**：本地数据库、文件系统操作、GitHub CLI 集成；
- **配置示例**：

```json
{
  "mcp.servers": {
    "sqlite": {
      "type": "stdio",
      "command": "uvx",
      "args": ["mcp-server-sqlite", "--db-path", "./data/app.db"]
    }
  }
}
```

### 3.2 HTTP 传输

服务器作为独立 HTTP 服务运行，通过 JSON-RPC over HTTP 通信：

- **优点**：可远程部署、支持多客户端共享、可负载均衡；
- **适用场景**：组织共享服务、SaaS 集成、权限集中管理；
- **配置示例**：

```json
{
  "mcp.servers": {
    "team-search": {
      "type": "http",
      "url": "https://internal-mcp.example.com/search",
      "headers": {
        "X-API-Key": "${TEAM_MCP_API_KEY}"
      }
    }
  }
}
```

> **安全提示**：HTTP 传输的凭证（API Key / Token）不要硬编码在 settings.json 中，使用环境变量引用（如 `"${VAR_NAME}"`）或写入 `.local.json` 文件。

## 4. 官方 MCP 服务器

以下为 Anthropic 官方维护的 MCP 服务器，可直接通过 `npx @anthropic/mcp-servers-*` 使用：

| 服务器 | 用途 | 安装命令 |
|--------|------|----------|
| GitHub | 操作仓库、Issue、PR、Code Review | `npx @anthropic/mcp-servers-github` |
| Filesystem | 安全的文件读写（沙箱路径） | `npx @anthropic/mcp-servers-filesystem` |
| PostgreSQL | 数据库查询与 Schema 分析 | `npx @anthropic/mcp-servers-postgres` |
| SQLite | 轻量嵌入式数据库操作 | `npx @anthropic/mcp-servers-sqlite` |
| Brave Search | 网络搜索能力 | `npx @anthropic/mcp-servers-brave-search` |
| Memory | 持久化 Key-Value 存储 | `npx @anthropic/mcp-servers-memory` |

### 4.1 GitHub 服务器典型配置

```json
{
  "mcp.servers": {
    "github": {
      "type": "stdio",
      "command": "npx",
      "args": ["@anthropic/mcp-servers-github"],
      "env": {
        "GITHUB_TOKEN": "ghp_xxxxxxxxxxxxxxxx"
      }
    }
  }
}
```

配置后，Claude Code 可以直接执行以下操作：

```
> 列出当前仓库最近的 5 个 Issue
> 帮我创建一个 PR，标题为 "feat: 添加用户注册接口"
> 为 PR #42 发起 Code Review
```

## 5. 自定义 MCP 服务器

当官方服务器无法满足需求时，你可以用任意语言开发 MCP 服务器。MCP 协议基于 JSON-RPC 2.0，服务端只需实现以下接口：

### 5.1 最小实现（Python 示例）

```python
import json
import sys

def handle_request(request):
    method = request.get("method")

    if method == "initialize":
        return {
            "jsonrpc": "2.0",
            "id": request["id"],
            "result": {
                "protocolVersion": "2025-03-26",
                "capabilities": {
                    "tools": {
                        "listChanged": False
                    }
                }
            }
        }

    elif method == "tools/list":
        return {
            "jsonrpc": "2.0",
            "id": request["id"],
            "result": {
                "tools": [
                    {
                        "name": "weather",
                        "description": "查询指定城市的天气",
                        "inputSchema": {
                            "type": "object",
                            "properties": {
                                "city": {
                                    "type": "string",
                                    "description": "城市名称，如 '北京'"
                                }
                            },
                            "required": ["city"]
                        }
                    }
                ]
            }
        }

    elif method == "tools/call":
        tool_name = request["params"]["name"]
        args = request["params"]["arguments"]

        if tool_name == "weather":
            city = args["city"]
            # 实现天气查询逻辑
            return {
                "jsonrpc": "2.0",
                "id": request["id"],
                "result": {
                    "content": [
                        {
                            "type": "text",
                            "text": f"{city} 当前天气：晴，25°C"
                        }
                    ]
                }
            }

# 主循环：从 stdin 读取 JSON-RPC 请求
for line in sys.stdin:
    request = json.loads(line.strip())
    response = handle_request(request)
    sys.stdout.write(json.dumps(response) + "\n")
    sys.stdout.flush()
```

### 5.2 使用 SDK 开发

官方推荐使用 MCP SDK 简化开发：

```bash
npm init @anthropic/mcp-server my-server
cd my-server
npm install
```

```typescript
import { Server } from "@anthropic/mcp-server";

const server = new Server({
  name: "weather-server",
  version: "1.0.0",
});

server.tool(
  "weather",
  { city: "string" },
  async (args) => {
    const data = await fetchWeather(args.city);
    return `当前 ${args.city} 天气：${data.condition}，${data.temp}°C`;
  }
);

server.listen();
```

Python SDK 同样可用：

```bash
pip install mcp
```

```python
from mcp.server import Server

server = Server("weather-server")

@server.tool()
def weather(city: str) -> str:
    """查询指定城市的天气"""
    data = fetch_weather(city)
    return f"当前 {city} 天气：{data['condition']}，{data['temp']}°C"

server.run()
```

### 5.3 编写 MCP 服务器的最佳实践

1. **工具命名清晰**：`create_pr` 优于 `cp`，`query_database` 优于 `qd`；
2. **输入 Schema 完整**：每个参数都写 `description`，告诉模型参数的语义与取值范围；
3. **错误处理友好**：返回中文错误信息，附带解决建议，让模型能据此调整参数重试；
4. **幂等设计**：同一参数重复调用不应产生副作用（或副作用可预期）；
5. **超时策略**：长耗时操作（如数据库查询）设置合理超时，避免阻塞模型上下文。

## 6. MCP 连接排障

### 6.1 常见问题

| 症状 | 原因 | 排查方法 |
|------|------|----------|
| 工具列表为空 | 服务器未正常启动 | 终端单独运行 `command` 参数检查输出 |
| 工具调用返回空 | 环境变量未正确传递给子进程 | 检查 `env` 字段，确认 Key 有效 |
| 超时无响应 | 底层命令阻塞（如无限等待输入） | 添加 `timeout` 参数，或检查 STDIO 握手 |
| 重复加载 | 项目级 + 用户级 settings 冲突 | 合并到一处，或在新版 settings 中配置 |
| 权限错误 | CLI 审批未通过 | 在 settings 中配置 `permissions.allow` |

### 6.2 调试步骤

```bash
# 1. 单独启动服务器，确认可以运行
npx @anthropic/mcp-servers-github --help

# 2. 使用 MCP Inspector 图形化调试
npx @anthropic/mcp-inspector

# 3. 开启 Claude Code 调试日志
claude --debug
# 观察日志中 mcp.initialize 和 mcp.tools.list 的调用结果
```

### 6.3 安全注意事项

- **路径沙箱**：Filesystem 服务器限定了 `allowedPaths`，不要为了省事放 "/"；
- **凭证最小化**：GitHub Token 只给 `repo` 权限，不要给 `admin` 或 `delete_repo`；
- **审计日志**：HTTP 模式的 MCP 服务器应记录所有请求，便于事后审计；
- **第三方服务器**：使用前阅读源码，确认没有恶意工具调用。

## 7. 实战案例：组合 MCP 服务器

将多个 MCP 服务器组合使用，可以实现复杂的端到端工作流。

**场景**：从 GitHub Issue 中读取需求，查询数据库确认数据结构，然后生成代码并创建 PR。

```json
{
  "mcp.servers": {
    "github": {
      "type": "stdio",
      "command": "npx",
      "args": ["@anthropic/mcp-servers-github"],
      "env": { "GITHUB_TOKEN": "ghp_..." }
    },
    "postgres": {
      "type": "stdio",
      "command": "npx",
      "args": ["@anthropic/mcp-servers-postgres"],
      "env": { "PG_URL": "postgresql://..." }
    },
    "filesystem": {
      "type": "stdio",
      "command": "npx",
      "args": ["@anthropic/mcp-servers-filesystem", "./src"]
    }
  }
}
```

使用方式：

```
> 读取 Issue #42 的需求描述，查询 users 表结构，生成对应的 CRUD 代码，最后创建 PR
```

Claude Code 会自主编排调用顺序：GitHub 读取 Issue → PostgreSQL 查询表结构 → 生成代码并写入文件 → GitHub 创建 PR。

## 8. 最佳实践小结

1. **优先 STDIO**：本地开发场景下 STDIO 零网络开销、安全边界清晰，除非需要共享服务，否则不引入 HTTP；
2. **凭证不落地**：通过 `env` 字段或环境变量引用注入，settings.json 中只有占位符；
3. **工具数量精炼**：每个服务器暴露 3-5 个"够用"的工具即可，太多工具会让模型选择困难、增加 token 消耗；
4. **组合大于单个**：多个 MCP 服务器组合使用，Claude 自带的编排能力远超单工具调度；
5. **定期检查社区**：MCP 生态发展迅速，关注 [MCP-Chinese-Getting-Started-Guide](../../repositories/liaokongVFX_MCP-Chinese-Getting-Started-Guide.md) 等社区项目获取最新服务器列表与最佳实践。

---

## 进阶指引

- 上一篇：[Claude Code Skills 开发实战](./02-skills-development.md)
- 下一篇：[Agent Teams 多 Agent 协作编排](./04-agent-teams.md) — 多 Agent 并行协作
- 生态仓库：[MCP-Chinese-Getting-Started-Guide](../../repositories/liaokongVFX_MCP-Chinese-Getting-Started-Guide.md)（MCP 协议中文入门）