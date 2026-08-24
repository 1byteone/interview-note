# 第二十章：MCP 模型上下文协议（P2 实战）

> 📖 **参考资料**：[MCP 官方规范](https://modelcontextprotocol.io/) | [FastMCP](https://github.com/jlowin/fastmcp) | [Anthropic MCP 文档](https://docs.anthropic.com/en/docs/agents-and-tools/mcp)

---

## 20.1 MCP 协议概念

MCP（Model Context Protocol）是 Anthropic 提出的开放标准，目标是为 AI 模型提供 **统一的工具接入协议** —— 就像 USB-C 统一了设备充电接口。

### USB-C 类比

```
┌─────────────────────────────────────────────────────────────┐
│                    MCP 协议定位                              │
│                                                             │
│  ┌──────────────────────────────────────────────────────┐   │
│  │           USB-C 类比                                  │   │
│  │                                                      │   │
│  │   AI 模型 (LLM)  ◄═══ MCP ═══►  外部工具/数据源     │   │
│  │                                                      │   │
│  │   "电脑"              "USB-C"          "外设"        │   │
│  │                                                      │   │
│  │   Claude / GPT      MCP 协议     数据库/API/文件系统  │   │
│  └──────────────────────────────────────────────────────┘   │
│                                                             │
│  ┌─────────── MCP 三大能力 ───────────┐                     │
│  │                                    │                     │
│  │  🔧 Tools    ──  工具调用          │                     │
│  │  📄 Resources ── 资源读取          │                     │
│  │  💬 Prompts  ── 提示词模板         │                     │
│  │                                    │                     │
│  └────────────────────────────────────┘                     │
└─────────────────────────────────────────────────────────────┘
```

### MCP vs Function Calling 对比

| 维度 | Function Calling | MCP |
|------|-----------------|-----|
| 标准 | 厂商私有 (OpenAI/Anthropic) | 开放标准 |
| 发现 | 静态定义在请求中 | 运行时动态发现 |
| 复用 | 跨厂商不可复用 | 一次实现，处处可用 |
| 生态 | 无 | 丰富的 Server/Client 生态 |
| 安全 | 依赖应用层 | 协议层内置权限控制 |

---

## 20.2 FastMCP 构建 Server

FastMCP 是构建 MCP Server 最简洁的 Python 框架，装饰器风格，开箱即用。

```python
# mcp_server/main.py
from fastmcp import FastMCP
from datetime import datetime
import json

# 初始化 MCP Server
mcp = FastMCP(
    name="backend-tools",
    version="1.0.0",
)


# ── 工具：数据库查询 ──
@mcp.tool()
async def query_database(sql: str) -> str:
    """执行只读 SQL 查询并返回结果

    Args:
        sql: SELECT 查询语句（禁止 INSERT/UPDATE/DELETE）
    """
    # 安全校验
    forbidden = ["INSERT", "UPDATE", "DELETE", "DROP", "ALTER", "TRUNCATE"]
    if any(word in sql.upper() for word in forbidden):
        return "❌ 安全拒绝：仅允许 SELECT 查询"

    # 模拟查询结果
    return json.dumps({
        "columns": ["id", "name", "created_at"],
        "rows": [
            [1, "订单A", "2025-01-15"],
            [2, "订单B", "2025-01-16"],
        ],
        "row_count": 2,
    }, ensure_ascii=False)


# ── 工具：发送通知 ──
@mcp.tool()
async def send_notification(user_id: str, message: str, channel: str = "email") -> str:
    """发送用户通知

    Args:
        user_id: 用户 ID
        message: 通知内容
        channel: 通知渠道 (email/sms/push)
    """
    # 实际项目中接入通知服务
    return json.dumps({
        "status": "sent",
        "user_id": user_id,
        "channel": channel,
        "timestamp": datetime.now().isoformat(),
    }, ensure_ascii=False)


# ── 资源：系统配置 ──
@mcp.resource("config://app")
async def get_app_config() -> str:
    """获取应用配置信息"""
    return json.dumps({
        "app_name": "Backend Service",
        "version": "2.1.0",
        "environment": "production",
        "features": {"mcp": True, "rag": True, "agent": True},
    }, ensure_ascii=False)


# ── 资源：文档目录 ──
@mcp.resource("docs://{topic}")
async def get_documentation(topic: str) -> str:
    """获取指定主题的 API 文档"""
    docs = {
        "auth": "# 认证文档\n使用 JWT Bearer Token，过期时间 24h",
        "db": "# 数据库文档\n使用 PostgreSQL + pgvector，连接池 20",
        "cache": "# 缓存文档\n使用 Redis，TTL 默认 300s",
    }
    return docs.get(topic, f"未找到主题 '{topic}' 的文档")


# ── 提示词模板 ──
@mcp.prompt()
async def code_review_prompt(code: str, language: str = "python") -> str:
    """生成代码审查提示词"""
    return (
        f"请审查以下 {language} 代码，关注：\n"
        f"1. 安全漏洞\n2. 性能问题\n3. 代码规范\n\n"
        f"```{language}\n{code}\n```"
    )


# 启动服务
if __name__ == "__main__":
    mcp.run(transport="stdio")  # 本地 stdio 模式
    # mcp.run(transport="sse", host="0.0.0.0", port=8080)  # 远程 SSE 模式
```

---

## 20.3 Tools 与 Resources 详解

### Tools vs Resources 选择指南

| 特性 | Tools | Resources |
|------|-------|-----------|
| 谁触发 | LLM 主动调用 | 客户端/LLM 读取 |
| 副作用 | 可以有（写操作、发送通知） | 无（只读） |
| 类比 | 函数 / API 接口 | 文件 / URI |
| 发现方式 | `list_tools()` | `list_resources()` |
| 示例 | 发邮件、查数据库、执行计算 | 读配置、读文档、读文件 |

### 高级 Tool 模式

```python
# mcp_server/advanced_tools.py
from fastmcp import FastMCP
from pydantic import BaseModel, Field
from typing import Annotated

mcp = FastMCP("advanced-tools")


class AnalysisRequest(BaseModel):
    """数据分析请求模型"""
    table: str = Field(description="数据表名")
    date_range: tuple[str, str] = Field(description="日期范围 (start, end)")
    metrics: list[str] = Field(default=["count"], description="统计指标")


@mcp.tool()
async def analyze_data(
    table: Annotated[str, "数据表名"],
    start_date: Annotated[str, "开始日期 YYYY-MM-DD"],
    end_date: Annotated[str, "结束日期 YYYY-MM-DD"],
) -> str:
    """执行数据分析并返回统计报告

    支持的表：orders, users, products
    支持的指标：count, sum, avg, min, max
    """
    return json.dumps({
        "table": table,
        "period": f"{start_date} ~ {end_date}",
        "results": {"count": 15420, "sum_amount": 892340.50},
        "generated_at": datetime.now().isoformat(),
    }, ensure_ascii=False)


@mcp.tool()
async def batch_insert(rows: list[dict]) -> str:
    """批量插入数据（需要二次确认）

    Args:
        rows: 数据行列表
    """
    return json.dumps({
        "status": "pending_confirmation",
        "row_count": len(rows),
        "message": "请确认批量插入操作",
    }, ensure_ascii=False)
```

---

## 20.4 在 FastAPI Agent 中接入 MCP

```python
# agent/mcp_agent.py
from fastapi import FastAPI
from fastapi.responses import StreamingResponse
from pydantic import BaseModel
from pydantic_ai import Agent
from mcp import ClientSession, StdioServerParameters
from mcp.client.stdio import stdio_client

app = FastAPI(title="MCP Agent API")


class MCPAgent:
    """集成 MCP 工具的 AI Agent"""

    def __init__(self):
        self.agent = Agent(
            model="openai:gpt-4o",
            system_prompt=(
                "你是一个智能助手，可以使用以下 MCP 工具：\n"
                "- query_database: 执行数据库查询\n"
                "- send_notification: 发送通知\n"
                "- analyze_data: 数据分析\n"
                "请根据用户需求选择合适的工具。"
            ),
        )
        self.mcp_session = None

    async def connect_mcp(self, server_path: str):
        """连接 MCP Server"""
        server_params = StdioServerParameters(
            command="python",
            args=[server_path],
        )
        self.mcp_session = await stdio_client(server_params).__aenter__()

        # 初始化并获取可用工具
        await self.mcp_session.__aenter__()
        tools = await self.mcp_session.list_tools()
        print(f"已连接 MCP Server，可用工具：{[t.name for t in tools.tools]}")

    async def execute_tool(self, tool_name: str, arguments: dict) -> str:
        """通过 MCP 协议调用远程工具"""
        if not self.mcp_session:
            return "MCP Server 未连接"

        result = await self.mcp_session.call_tool(tool_name, arguments)
        return result.content[0].text if result.content else "无返回结果"

    async def query(self, question: str) -> str:
        """Agent 问答（自动选择工具）"""
        result = await self.agent.run(question)
        return result.data


# 全局 Agent 实例
mcp_agent = MCPAgent()


# ── FastAPI 端点 ──

class QueryRequest(BaseModel):
    question: str


class ToolRequest(BaseModel):
    tool_name: str
    arguments: dict


@app.on_event("startup")
async def startup():
    """启动时连接 MCP Server"""
    await mcp_agent.connect_mcp("mcp_server/main.py")


@app.post("/agent/query")
async def agent_query(req: QueryRequest):
    """AI Agent 智能问答"""
    answer = await mcp_agent.query(req.question)
    return {"answer": answer, "agent": "mcp-pydantic-ai"}


@app.post("/mcp/tool/execute")
async def execute_mcp_tool(req: ToolRequest):
    """直接调用 MCP 工具（跳过 LLM）"""
    result = await mcp_agent.execute_tool(req.tool_name, req.arguments)
    return {"tool": req.tool_name, "result": result}


@app.get("/mcp/tools")
async def list_mcp_tools():
    """列出所有可用 MCP 工具"""
    if not mcp_agent.mcp_session:
        return {"tools": [], "status": "disconnected"}

    tools = await mcp_agent.mcp_session.list_tools()
    return {
        "tools": [
            {"name": t.name, "description": t.description}
            for t in tools.tools
        ]
    }


@app.get("/agent/health")
async def health():
    return {
        "status": "ok",
        "mcp_connected": mcp_agent.mcp_session is not None,
        "agent": "mcp-pydantic-ai",
    }
```

### 运行流程

```bash
# 1. 启动 MCP Server（stdio 模式）
python mcp_server/main.py

# 2. 启动 FastAPI Agent（自动连接 MCP）
uvicorn agent.mcp_agent:app --reload --port 8000

# 3. 调用智能问答
curl -X POST http://localhost:8000/agent/query \
  -H "Content-Type: application/json" \
  -d '{"question": "查询 orders 表最近 7 天的订单数量"}'

# 4. 直接调用 MCP 工具
curl -X POST http://localhost:8000/mcp/tool/execute \
  -H "Content-Type: application/json" \
  -d '{"tool_name": "query_database", "arguments": {"sql": "SELECT COUNT(*) FROM orders"}}'
```

---

## 必读资源

| 资源 | 链接 | 说明 |
|------|------|------|
| MCP 官方规范 | https://modelcontextprotocol.io/ | 协议标准文档 |
| FastMCP GitHub | https://github.com/jlowin/fastmcp | 最简洁的 Python MCP 框架 |
| Anthropic MCP 指南 | https://docs.anthropic.com/en/docs/agents-and-tools/mcp | 官方使用教程 |
| MCP Server 目录 | https://github.com/modelcontextprotocol/servers | 官方 MCP Server 合集 |
| MCP Python SDK | https://github.com/modelcontextprotocol/python-sdk | 官方 Python SDK |
| MCP Inspector | https://github.com/modelcontextprotocol/inspector | MCP 调试工具 |
