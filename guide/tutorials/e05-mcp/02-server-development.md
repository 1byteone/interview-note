# MCP Server 开发实战（Python + FastMCP）

> 本文档属于 **E05 MCP 协议生态** 系列教程的第二篇，面向开发者，使用 Python 的 FastMCP 框架从零开发一个完整的 MCP 服务器，涵盖工具、资源、提示模板、调试和测试全流程。

---

## 1. FastMCP 框架简介

FastMCP 是 Python 生态中最流行的 MCP 服务器开发框架，由 Anthropic 官方维护。它借鉴了 FastAPI 的设计理念，用装饰器语法极大简化了 MCP 服务器的开发。

### 1.1 核心特性

- **装饰器驱动**: 使用 `@mcp.tool()`、`@mcp.resource()`、`@mcp.prompt()` 声明能力
- **类型自动推断**: 从 Python 类型注解自动生成 JSON Schema
- **异步支持**: 原生支持 async/await，适合 IO 密集型任务
- **内置调试**: 提供 Inspector 工具可视化调试
- **Pydantic 集成**: 使用 Pydantic 模型进行参数校验

---

## 2. 安装

### 2.1 使用 pip

```bash
pip install fastmcp
```

### 2.2 使用 uv（推荐）

`uv` 是现代 Python 包管理工具，速度更快：

```bash
# 创建项目
uv init my-mcp-server
cd my-mcp-server

# 添加 fastmcp 依赖
uv add fastmcp
```

### 2.3 验证安装

```python
import fastmcp
print(fastmcp.__version__)
```

---

## 3. 第一个 MCP 服务器

创建 `server.py`：

```python
from fastmcp import FastMCP

# 创建 MCP 服务器实例
mcp = FastMCP("my-first-server")


@mcp.tool()
def add(a: int, b: int) -> int:
    """两个整数相加"""
    return a + b


@mcp.tool()
def greet(name: str) -> str:
    """生成问候语"""
    return f"Hello, {name}! Welcome to MCP."


if __name__ == "__main__":
    # 启动服务器（STDIO 传输）
    mcp.run()
```

### 3.1 运行

```bash
python server.py
```

服务器以 STDIO 模式启动，等待客户端连接。你可以用 Claude Desktop 或 Inspector 来测试它。

---

## 4. 工具开发

### 4.1 基本工具

使用 `@mcp.tool()` 装饰器声明工具：

```python
@mcp.tool()
def search_users(
    query: str,
    limit: int = 10,
    active_only: bool = True
) -> list[dict]:
    """
    搜索用户

    Args:
        query: 搜索关键词（用户名或邮箱）
        limit: 返回结果数量上限
        active_only: 是否只返回活跃用户
    """
    # 实际场景中这里会查询数据库
    users = [
        {"id": 1, "name": "Alice", "email": "alice@example.com"},
        {"id": 2, "name": "Bob", "email": "bob@example.com"},
    ]
    return users[:limit]
```

### 4.2 异步工具

对于 IO 密集型操作，使用异步函数：

```python
import httpx

@mcp.tool()
async def fetch_weather(city: str) -> dict:
    """获取城市天气信息"""
    async with httpx.AsyncClient() as client:
        response = await client.get(
            f"https://api.weather.com/v1/{city}"
        )
        return response.json()
```

### 4.3 使用 Pydantic 模型

复杂参数推荐使用 Pydantic 模型：

```python
from pydantic import BaseModel, Field
from typing import Optional


class CreateUserRequest(BaseModel):
    username: str = Field(description="用户名，3-20 个字符", min_length=3, max_length=20)
    email: str = Field(description="用户邮箱", pattern=r"^[\w.-]+@[\w.-]+\.\w+$")
    age: Optional[int] = Field(default=None, description="年龄", ge=0, le=150)


@mcp.tool()
def create_user(request: CreateUserRequest) -> dict:
    """创建新用户"""
    # 在这里执行创建逻辑
    return {
        "id": 123,
        "username": request.username,
        "email": request.email,
        "age": request.age,
        "created_at": "2026-08-23T10:00:00Z"
    }
```

### 4.4 工具返回多模态内容

工具可以返回文本、图像等多模态内容：

```python
from fastmcp import Image

@mcp.tool()
def generate_chart(data: list[float]) -> Image:
    """生成数据图表"""
    import matplotlib.pyplot as plt
    
    plt.plot(data)
    plt.title("Data Trend")
    
    # 保存到内存
    import io
    buf = io.BytesIO()
    plt.savefig(buf, format='png')
    buf.seek(0)
    
    return Image(data=buf.read(), format="png")
```

---

## 5. 资源开发

### 5.1 静态资源

使用 `@mcp.resource()` 声明只读资源：

```python
@mcp.resource("config://app")
def get_config() -> str:
    """应用配置"""
    return """
    [server]
    port = 8080
    host = localhost
    
    [database]
    url = postgresql://localhost/myapp
    """
```

### 5.2 资源模板（动态资源）

使用 `{param}` 语法定义 URI 模板，支持动态参数：

```python
@mcp.resource("user://{user_id}")
def get_user(user_id: str) -> dict:
    """根据 ID 获取用户信息"""
    # 模拟数据库查询
    users_db = {
        "1": {"id": 1, "name": "Alice", "role": "admin"},
        "2": {"id": 2, "name": "Bob", "role": "user"},
    }
    return users_db.get(user_id, {"error": "User not found"})
```

客户端访问 `user://1` 就会调用 `get_user("1")`。

### 5.3 文件系统资源

```python
import os
from pathlib import Path

@mcp.resource("file://{path}")
def read_file(path: str) -> str:
    """读取文件内容"""
    safe_path = Path(path).resolve()
    if not safe_path.exists():
        raise FileNotFoundError(f"File not found: {path}")
    return safe_path.read_text(encoding='utf-8')
```

### 5.4 资源订阅

支持资源变化时主动通知客户端：

```python
import asyncio
import time

@mcp.resource("metrics://uptime")
async def get_uptime() -> str:
    """服务运行时间（动态更新）"""
    return f"Uptime: {time.time() - start_time:.0f} seconds"
```

---

## 6. 提示模板开发

### 6.1 基本提示

```python
@mcp.prompt()
def code_review(language: str, code: str) -> str:
    """代码审查提示"""
    return f"""Please review the following {language} code:

```
{code}
```

Focus on:
1. Potential bugs
2. Security vulnerabilities
3. Performance issues
4. Code style improvements
"""
```

### 6.2 多消息提示

提示可以返回多轮对话：

```python
from fastmcp import PromptMessage

@mcp.prompt()
def debug_error(error_message: str, code: str) -> list[PromptMessage]:
    """调试错误的多轮对话"""
    return [
        PromptMessage(
            role="user",
            content=f"I got this error: {error_message}"
        ),
        PromptMessage(
            role="user",
            content=f"Here's the relevant code: {code}"
        ),
        PromptMessage(
            role="assistant",
            content="Let me analyze the error and code to identify the root cause."
        )
    ]
```

### 6.3 带资源的提示

```python
@mcp.prompt()
def analyze_logs(log_uri: str) -> str:
    """分析日志的提示（引用资源）"""
    return f"""Analyze the application logs at {log_uri}:

1. Identify error patterns
2. Find performance bottlenecks
3. Suggest improvements
"""
```

---

## 7. Context 与错误处理

### 7.1 使用 Context

`Context` 对象让工具访问 MCP 运行时上下文，包括日志、进度和资源访问：

```python
from fastmcp import Context

@mcp.tool()
async def long_task(duration: int, ctx: Context) -> str:
    """长时间运行的任务"""
    # 记录日志
    await ctx.info(f"Starting task, will run for {duration} seconds")
    
    # 报告进度
    for i in range(duration):
        await ctx.report_progress(i, duration)
        await asyncio.sleep(1)
    
    await ctx.info("Task completed")
    return f"Task completed after {duration} seconds"
```

### 7.2 错误处理

工具抛出异常时，MCP 会自动转换为错误响应：

```python
@mcp.tool()
def divide(a: float, b: float) -> float:
    """除法运算"""
    if b == 0:
        raise ValueError("除数不能为零")
    return a / b
```

更精细的错误处理：

```python
from fastmcp.exceptions import ToolError

@mcp.tool()
async def query_database(sql: str, ctx: Context) -> list[dict]:
    """执行 SQL 查询"""
    if not sql.strip().lower().startswith("select"):
        raise ToolError("只允许执行 SELECT 查询")
    
    try:
        await ctx.info("Executing query")
        result = await execute_sql(sql)
        return result
    except DatabaseError as e:
        await ctx.error(f"Database error: {e}")
        raise ToolError(f"查询失败: {e}")
```

---

## 8. 调试与 Inspector

### 8.1 启动 Inspector

FastMCP 提供可视化调试工具：

```bash
# 调试 Python 服务器
mcp dev server.py

# 调试指定服务器名称
mcp dev server.py:mcp
```

启动后，Inspector 会打开浏览器界面（通常是 http://localhost:5173），可以：

- 列出所有 Tools、Resources、Prompts
- 手动调用工具并查看结果
- 查看请求和响应的原始 JSON
- 测试不同的参数组合

### 8.2 日志调试

启用详细日志：

```python
import logging

logging.basicConfig(
    level=logging.DEBUG,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)

mcp = FastMCP(
    "my-server",
    log_level=logging.DEBUG
)
```

### 8.3 命令行测试

```bash
# 直接调用工具
echo '{"jsonrpc":"2.0","id":1,"method":"tools/list","params":{}}' | \
  python server.py
```

---

## 9. 测试

### 9.1 单元测试工具

```python
import pytest
from server import mcp, add, greet


def test_add():
    assert add(2, 3) == 5
    assert add(-1, 1) == 0


def test_greet():
    result = greet("Alice")
    assert "Alice" in result
    assert "Welcome" in result
```

### 9.2 集成测试

使用 `Client` 测试完整的 MCP 通信：

```python
import pytest
from fastmcp import Client
from server import mcp


@pytest.fixture
async def client():
    async with Client(mcp) as client:
        yield client


@pytest.mark.asyncio
async def test_list_tools(client):
    tools = await client.list_tools()
    tool_names = [t.name for t in tools]
    assert "add" in tool_names


@pytest.mark.asyncio
async def test_call_tool(client):
    result = await client.call_tool("add", {"a": 2, "b": 3})
    assert result[0].text == "5"
```

### 9.3 测试资源

```python
@pytest.mark.asyncio
async def test_list_resources(client):
    resources = await client.list_resources()
    assert any(r.uri == "config://app" for r in resources)


@pytest.mark.asyncio
async def test_read_resource(client):
    content = await client.read_resource("config://app")
    assert "port" in content
```

---

## 10. 完整示例：文件管理 MCP 服务器

```python
"""
文件管理 MCP 服务器
提供文件读写、搜索功能
"""
from fastmcp import FastMCP, Context
from pathlib import Path
from typing import Optional
import os
import fnmatch

mcp = FastMCP("file-manager")

# 限制可访问的根目录
ALLOWED_ROOT = Path.home() / "projects"


def _safe_resolve(path: str) -> Path:
    """安全地解析路径，确保不越界"""
    target = (ALLOWED_ROOT / path).resolve()
    if not str(target).startswith(str(ALLOWED_ROOT)):
        raise ValueError(f"Access denied: {path}")
    return target


@mcp.tool()
def read_file(path: str) -> str:
    """读取文件内容"""
    target = _safe_resolve(path)
    if not target.is_file():
        raise FileNotFoundError(f"File not found: {path}")
    return target.read_text(encoding='utf-8')


@mcp.tool()
def write_file(path: str, content: str) -> str:
    """写入文件"""
    target = _safe_resolve(path)
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(content, encoding='utf-8')
    return f"Written {len(content)} characters to {path}"


@mcp.tool()
def search_files(pattern: str, directory: str = ".") -> list[str]:
    """搜索匹配的文件"""
    search_dir = _safe_resolve(directory)
    matches = []
    for root, _, files in os.walk(search_dir):
        for filename in files:
            if fnmatch.fnmatch(filename, pattern):
                rel_path = os.path.relpath(
                    os.path.join(root, filename),
                    ALLOWED_ROOT
                )
                matches.append(rel_path)
    return matches[:100]  # 限制返回数量


@mcp.resource("file://{path}")
def get_file_content(path: str) -> str:
    """获取文件内容（资源形式）"""
    return read_file(path)


@mcp.prompt()
def analyze_code(path: str) -> str:
    """分析代码文件的提示"""
    return f"""Analyze the code at {path}:

1. Code quality assessment
2. Potential bugs
3. Security concerns
4. Refactoring suggestions
"""


if __name__ == "__main__":
    mcp.run()
```

---

## 参考链接

- [MCP 协议原理与核心概念](./01-protocol-concepts.md)
- [MCP Client 集成与生产部署](./03-client-integration.md)
- [FastMCP 文档](https://gofastmcp.com)
- [MCP 官方文档](https://modelcontextprotocol.io)