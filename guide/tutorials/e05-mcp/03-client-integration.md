# MCP Client 集成与生产部署

> 本文档属于 **E05 MCP 协议生态** 系列教程的第三篇，讲解如何构建 MCP 客户端、集成到主流 AI 应用（Claude Desktop、Codex CLI 等），以及将 MCP 服务器部署到生产环境的最佳实践。

---

## 1. 构建 MCP 客户端

### 1.1 Python 客户端基础

使用 FastMCP 提供的 Client API 构建客户端：

```python
import asyncio
from fastmcp import Client


async def main():
    # 通过 STDIO 连接服务器
    async with Client("server.py") as client:
        # 列出所有工具
        tools = await client.list_tools()
        for tool in tools:
            print(f"Tool: {tool.name}")
            print(f"  Description: {tool.description}")
        
        # 调用工具
        result = await client.call_tool("add", {"a": 2, "b": 3})
        print(f"Result: {result}")


if __name__ == "__main__":
    asyncio.run(main())
```

### 1.2 连接方式

**STDIO 连接**（本地服务器）：

```python
from fastmcp import Client

# 直接指定服务器脚本
client = Client("server.py")

# 或指定 Python 解释器和参数
client = Client(
    transport={
        "type": "stdio",
        "command": "python",
        "args": ["server.py"],
        "env": {"DEBUG": "true"}
    }
)
```

**HTTP 连接**（远程服务器）：

```python
client = Client(
    transport={
        "type": "http",
        "url": "https://api.example.com/mcp",
        "headers": {
            "Authorization": "Bearer your-token"
        }
    }
)
```

**SSE 连接**（Server-Sent Events）：

```python
client = Client(
    transport={
        "type": "sse",
        "url": "https://api.example.com/sse"
    }
)
```

---

## 2. 会话管理

### 2.1 生命周期管理

```python
import asyncio
from fastmcp import Client


class MCPClientManager:
    """管理多个 MCP 服务器的连接"""
    
    def __init__(self):
        self.clients: dict[str, Client] = {}
    
    async def connect(self, name: str, config: dict):
        """连接到 MCP 服务器"""
        client = Client(transport=config)
        await client.__aenter__()
        self.clients[name] = client
        print(f"Connected to {name}")
    
    async def disconnect(self, name: str):
        """断开连接"""
        if name in self.clients:
            await self.clients[name].__aexit__(None, None, None)
            del self.clients[name]
            print(f"Disconnected from {name}")
    
    async def disconnect_all(self):
        """断开所有连接"""
        for name in list(self.clients.keys()):
            await self.disconnect(name)
    
    async def call_tool(self, server: str, tool: str, params: dict):
        """调用指定服务器的工具"""
        if server not in self.clients:
            raise ValueError(f"Server {server} not connected")
        return await self.clients[server].call_tool(tool, params)


async def main():
    manager = MCPClientManager()
    
    try:
        # 连接多个服务器
        await manager.connect("filesystem", {
            "type": "stdio",
            "command": "npx",
            "args": ["-y", "@modelcontextprotocol/server-filesystem"]
        })
        await manager.connect("github", {
            "type": "stdio",
            "command": "npx",
            "args": ["-y", "@modelcontextprotocol/server-github"]
        })
        
        # 调用工具
        result = await manager.call_tool(
            "github", "search_repos", {"query": "mcp"}
        )
        print(result)
    
    finally:
        await manager.disconnect_all()


if __name__ == "__main__":
    asyncio.run(main())
```

### 2.2 错误重连

```python
import asyncio
from fastmcp import Client


async def call_with_retry(
    client: Client,
    tool: str,
    params: dict,
    max_retries: int = 3
):
    """带重试的工具调用"""
    for attempt in range(max_retries):
        try:
            return await client.call_tool(tool, params)
        except ConnectionError:
            if attempt < max_retries - 1:
                await asyncio.sleep(2 ** attempt)
                continue
            raise
        except Exception as e:
            raise RuntimeError(f"Tool call failed: {e}")
```

---

## 3. 工具调用与资源访问

### 3.1 完整的客户端示例

```python
import asyncio
from fastmcp import Client


async def comprehensive_example():
    async with Client("server.py") as client:
        # 1. 列出所有能力
        tools = await client.list_tools()
        resources = await client.list_resources()
        prompts = await client.list_prompts()
        
        print(f"Tools: {len(tools)}")
        print(f"Resources: {len(resources)}")
        print(f"Prompts: {len(prompts)}")
        
        # 2. 调用工具
        tool_result = await client.call_tool(
            "search_users",
            {"query": "alice", "limit": 5}
        )
        print(f"Tool result: {tool_result}")
        
        # 3. 读取资源
        resource = await client.read_resource("config://app")
        print(f"Resource: {resource}")
        
        # 4. 获取提示
        prompt = await client.get_prompt(
            "code_review",
            {"language": "python", "code": "print('hello')"}
        )
        print(f"Prompt: {prompt}")


asyncio.run(comprehensive_example())
```

---

## 4. 集成到 Claude Desktop

### 4.1 配置文件位置

Claude Desktop 的配置文件位于：

- **macOS**: `~/Library/Application Support/Claude/claude_desktop_config.json`
- **Windows**: `%APPDATA%\Claude\claude_desktop_config.json`

### 4.2 配置 MCP 服务器

```json
{
  "mcpServers": {
    "filesystem": {
      "command": "npx",
      "args": [
        "-y",
        "@modelcontextprotocol/server-filesystem",
        "/Users/yourname/Documents"
      ]
    },
    "github": {
      "command": "npx",
      "args": ["-y", "@modelcontextprotocol/server-github"],
      "env": {
        "GITHUB_PERSONAL_ACCESS_TOKEN": "ghp_xxxxx"
      }
    },
    "my-python-server": {
      "command": "python",
      "args": ["/path/to/server.py"]
    }
  }
}
```

### 4.3 配置完成后

重启 Claude Desktop，在对话中可以看到工具图标，AI 会自动选择并调用合适的 MCP 工具。

---

## 5. 集成到 Codex CLI

在 `~/.codex/config.toml` 中配置：

```toml
[mcp]

  [mcp.servers.filesystem]
  command = "npx"
  args = ["-y", "@modelcontextprotocol/server-filesystem", "/home/user"]

  [mcp.servers.my-python-server]
  command = "python"
  args = ["/path/to/server.py"]

  [mcp.servers.remote]
  transport = "http"
  url = "https://api.example.com/mcp"
```

配置完成后，在 Codex CLI 会话中可以直接使用 MCP 工具。

---

## 6. LangChain 集成

LangChain 提供了 MCP 适配器，将 MCP 服务器转换为 LangChain 工具。

### 6.1 安装

```bash
pip install langchain-mcp-adapters
```

### 6.2 使用示例

```python
import asyncio
from langchain_mcp_adapters.client import MultiServerMCPClient
from langchain.agents import create_agent
from langchain_openai import ChatOpenAI


async def main():
    # 配置多个 MCP 服务器
    client = MultiServerMCPClient({
        "filesystem": {
            "command": "npx",
            "args": ["-y", "@modelcontextprotocol/server-filesystem"],
            "transport": "stdio"
        },
        "github": {
            "command": "npx",
            "args": ["-y", "@modelcontextprotocol/server-github"],
            "transport": "stdio",
            "env": {"GITHUB_PERSONAL_ACCESS_TOKEN": "ghp_xxx"}
        }
    })
    
    # 获取所有工具（作为 LangChain Tool）
    tools = await client.get_tools()
    print(f"Loaded {len(tools)} tools")
    
    # 创建 Agent
    model = ChatOpenAI(model="gpt-4")
    agent = create_agent(model, tools)
    
    # 执行任务
    result = await agent.ainvoke("List all Python files in the current directory")
    print(result)


asyncio.run(main())
```

### 6.3 与 LangGraph 集成

```python
from langgraph.prebuilt import create_react_agent

# 创建 ReAct Agent
agent = create_react_agent(model, tools)

# 流式输出
async for event in agent.astream("Search for MCP repos on GitHub"):
    print(event)
```

---

## 7. Serverless 部署

### 7.1 Cloudflare Workers

MCP 服务器可以部署到 Cloudflare Workers，实现全球低延迟访问。

**wrangler.toml**:

```toml
name = "my-mcp-server"
main = "src/index.ts"
compatibility_date = "2024-12-01"

[vars]
API_KEY = "your-api-key"
```

**src/index.ts**:

```typescript
import { McpServer } from '@modelcontextprotocol/sdk/server/mcp.js';
import { McpAgent } from 'mcp-agent-cloudflare';

export class MyMCPServer extends McpAgent {
  server = new McpServer({
    name: "my-server",
    version: "1.0.0"
  });
  
  async init() {
    this.server.tool(
      "search",
      { query: z.string() },
      async ({ query }) => {
        // 在这里实现搜索逻辑
        return {
          content: [{
            type: "text",
            text: `Search results for: ${query}`
          }]
        };
      }
    );
  }
}

export default MyMCPServer;
```

部署：

```bash
npx wrangler deploy
```

### 7.2 AWS Lambda

```python
"""
AWS Lambda MCP 服务器
"""
from fastmcp import FastMCP
import json

mcp = FastMCP("lambda-server")


@mcp.tool()
def get_user(user_id: str) -> dict:
    """获取用户信息"""
    return {"id": user_id, "name": "Alice"}


def lambda_handler(event, context):
    """AWS Lambda 入口"""
    body = json.loads(event.get('body', '{}'))
    
    # 将请求路由到 MCP
    response = mcp.handle_request(body)
    
    return {
        'statusCode': 200,
        'headers': {'Content-Type': 'application/json'},
        'body': json.dumps(response)
    }
```

配合 API Gateway + Lambda + SQS 实现完整部署。

---

## 8. Docker 部署

### 8.1 Dockerfile

```dockerfile
FROM python:3.12-slim

WORKDIR /app

# 安装依赖
COPY pyproject.toml .
RUN pip install --no-cache-dir fastmcp

# 复制源码
COPY . .

# 暴露端口（HTTP 模式）
EXPOSE 8080

# 启动命令
CMD ["python", "server.py"]
```

### 8.2 docker-compose.yml

```yaml
version: '3.8'
services:
  mcp-server:
    build: .
    ports:
      - "8080:8080"
    environment:
      - MCP_TRANSPORT=http
      - MCP_PORT=8080
      - LOG_LEVEL=info
    restart: unless-stopped
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/health"]
      interval: 30s
      timeout: 10s
      retries: 3
```

### 8.3 在 Codex/Claude 中连接 Docker 服务器

```toml
[mcp.servers.docker-server]
transport = "http"
url = "http://localhost:8080/mcp"
```

---

## 9. 生产最佳实践

### 9.1 安全清单

- [ ] **认证**: 配置 API Key 或 OAuth 认证
- [ ] **授权**: 实施最小权限原则，按工具粒度控制访问
- [ ] **输入校验**: 使用 Pydantic 严格校验所有输入
- [ ] **路径安全**: 文件操作使用 `Path.resolve()` 防止路径穿越
- [ ] **SQL 注入防护**: 使用参数化查询，禁止字符串拼接 SQL
- [ ] **限流**: 对工具调用实施速率限制
- [ ] **敏感信息**: 不要在日志或响应中暴露敏感数据

### 9.2 可观测性

```python
import logging
import time
from fastmcp import FastMCP, Context

# 配置结构化日志
logging.basicConfig(
    level=logging.INFO,
    format='{"time":"%(asctime)s","level":"%(levelname)s","msg":"%(message)s"}'
)

mcp = FastMCP("production-server")


@mcp.tool()
async def monitored_operation(query: str, ctx: Context) -> dict:
    """带监控的工具"""
    start_time = time.time()
    
    await ctx.info(f"Processing query: {query}")
    
    try:
        result = await perform_operation(query)
        duration = time.time() - start_time
        await ctx.info(f"Operation completed in {duration:.3f}s")
        return result
    except Exception as e:
        await ctx.error(f"Operation failed: {e}")
        raise
```

### 9.3 性能优化

1. **异步优先**: 所有 IO 操作使用 async/await
2. **连接池复用**: 数据库、HTTP 客户端使用连接池
3. **缓存**: 对频繁访问的数据使用缓存
4. **批量操作**: 支持批量调用减少往返
5. **超时控制**: 为所有外部调用设置超时

```python
import httpx
from asyncio import TimeoutError

@mcp.tool()
async def fetch_data(url: str, ctx: Context) -> dict:
    """带超时的 HTTP 请求"""
    async with httpx.AsyncClient(timeout=10.0) as client:
        try:
            response = await client.get(url)
            return response.json()
        except TimeoutError:
            await ctx.error(f"Request to {url} timed out")
            raise ValueError("Request timeout")
```

### 9.4 版本管理

- 使用语义化版本（SemVer）管理服务器版本
- 在 `serverInfo` 中声明版本，方便客户端兼容性判断
- 重大变更前提前通知，并提供迁移指南

---

## 10. 总结

MCP 的生产部署要点：

1. **客户端集成**: Claude Desktop、Codex CLI、LangChain 都有标准化的集成方式
2. **部署形态**: STDIO 本地部署、HTTP 远程部署、Serverless 部署、Docker 部署各有适用场景
3. **安全第一**: 认证、授权、输入校验、路径安全缺一不可
4. **可观测性**: 结构化日志、指标监控、分布式追踪是生产环境的标配
5. **性能优化**: 异步 IO、连接池、缓存、超时控制是核心优化点

通过本文档的学习，你应该能够：
- 开发一个完整的 MCP 客户端
- 将 MCP 服务器集成到 Claude Desktop、Codex CLI、LangChain
- 选择合适的部署方式（本地、Docker、Serverless）
- 实施生产级的安全和监控措施

---

## 参考链接

- [MCP 协议原理与核心概念](./01-protocol-concepts.md)
- [MCP Server 开发实战](./02-server-development.md)
- [MCP 官方文档](https://modelcontextprotocol.io)
- [FastMCP 文档](https://gofastmcp.com)
- [LangChain MCP 适配器](https://github.com/langchain-ai/langchain-mcp-adapters)