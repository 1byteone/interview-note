# 第八章：httpx 异步 HTTP 客户端（P1 实战）

> 📖 **参考资料**：[httpx 官方文档](https://www.python-httpx.org/) | [httpx AsyncClient](https://www.python-httpx.org/async/)

httpx 是 Python 生态中 requests 的现代替代品，原生支持 async/await、HTTP/2、连接池和流式传输。本章从基础用法到 LLM 流式 SSE，覆盖生产级场景。

---

## 8.1 httpx 基础：同步与异步

```python
import httpx

# --- 同步客户端（简单脚本场景）---
def sync_demo():
    with httpx.Client() as client:
        r = client.get("https://httpbin.org/get")
        print(r.status_code, r.json()["origin"])

# --- 异步客户端（FastAPI / 后端首选）---
import asyncio

async def async_demo():
    async with httpx.AsyncClient() as client:
        r = await client.get("https://httpbin.org/get")
        print(r.status_code, r.json()["origin"])

if __name__ == "__main__":
    asyncio.run(async_demo())
```

| 特性 | requests | httpx |
|------|----------|-------|
| 异步支持 | ❌ | ✅ 原生 async/await |
| HTTP/2 | ❌ | ✅ |
| 连接池 | 基础 | 可配置 MaxLimits |
| 超时模型 | 单一 timeout | 分层 timeout（connect/read/write/pool） |
| 流式响应 | 有限 | ✅ async iter |

---

## 8.2 连接池配置

```python
import httpx

# 创建持久化客户端，复用连接池
async def create_pooled_client():
    """生产级连接池配置"""
    limits = httpx.Limits(
        max_connections=100,       # 连接池最大连接数
        max_keepalive_connections=20,  # 保持连接数
        keepalive_expiry=30,       # 保持连接超时（秒）
    )

    client = httpx.AsyncClient(
        base_url="https://api.example.com",
        timeout=httpx.Timeout(
            connect=5.0,   # 建立连接超时
            read=30.0,     # 读取响应超时
            write=10.0,    # 写入请求超时
            pool=5.0,      # 从连接池获取连接超时
        ),
        limits=limits,
        headers={"User-Agent": "MyApp/1.0", "Accept": "application/json"},
        http2=True,  # 启用 HTTP/2
    )
    return client

# 使用（推荐放在 FastAPI lifespan 中管理生命周期）
async def api_call():
    client = await create_pooled_client()
    try:
        r = await client.get("/users?page=1")
        return r.json()
    finally:
        await client.aclose()  # 关闭连接池
```

---

## 8.3 超时与重试

```python
import httpx
import asyncio
from httpx import Timeout, HTTPStatusError

# --- 内置指数退避重试（httpx-resilient 或手写）---
async def fetch_with_retry(
    url: str,
    max_retries: int = 3,
    base_delay: float = 1.0,
) -> httpx.Response:
    """带指数退避的重试请求"""
    async with httpx.AsyncClient(timeout=Timeout(10.0)) as client:
        for attempt in range(max_retries + 1):
            try:
                r = await client.get(url)
                r.raise_for_status()
                return r
            except HTTPStatusError as e:
                if e.response.status_code < 500:
                    raise  # 4xx 客户端错误，不重试
                if attempt == max_retries:
                    raise
            except httpx.RequestError:
                if attempt == max_retries:
                    raise

            delay = base_delay * (2 ** attempt)
            print(f"  ⏳ 重试 {attempt+1}/{max_retries}，等待 {delay}s")
            await asyncio.sleep(delay)

# --- 超时异常处理 ---
async def safe_request():
    try:
        async with httpx.AsyncClient(timeout=5.0) as client:
            r = await client.get("https://httpbin.org/delay/10")
    except httpx.TimeoutException:
        print("请求超时，降级处理")
    except httpx.ConnectError:
        print("连接失败，切换备用节点")
```

---

## 8.4 流式 SSE：LLM 场景

SSE（Server-Sent Events）是 LLM 流式输出的标准协议。httpx 原生支持异步流式读取。

```python
import httpx
import json
from typing import AsyncIterator

async def stream_chat(
    prompt: str,
    api_url: str = "http://localhost:8000/v1/chat/completions",
    model: str = "gpt-4o",
) -> AsyncIterator[str]:
    """
    流式调用 LLM API，逐 token 输出
    兼容 OpenAI 协议格式
    """
    payload = {
        "model": model,
        "messages": [{"role": "user", "content": prompt}],
        "stream": True,  # 关键：开启流式
    }

    async with httpx.AsyncClient(timeout=httpx.Timeout(60.0)) as client:
        async with client.stream("POST", api_url, json=payload) as response:
            response.raise_for_status()

            async for line in response.aiter_lines():
                # SSE 格式：以 "data: " 开头
                if not line.startswith("data: "):
                    continue

                data_str = line[len("data: "):]
                if data_str.strip() == "[DONE]":
                    break

                try:
                    chunk = json.loads(data_str)
                    delta = chunk["choices"][0]["delta"]
                    content = delta.get("content", "")
                    if content:
                        yield content  # 逐 token 产出
                except (json.JSONDecodeError, KeyError, IndexError):
                    continue

# --- 使用示例 ---
async def main():
    full_response = []
    async for token in stream_chat("用 Python 写一个快速排序"):
        print(token, end="", flush=True)
        full_response.append(token)
    print(f"\n\n完整响应长度: {''.join(full_response)} 字符")

if __name__ == "__main__":
    asyncio.run(main())
```

---

## 8.5 OpenAI 兼容 API 调用

```python
import httpx
import json
from dataclasses import dataclass

@dataclass
class ChatMessage:
    role: str  # "system" | "user" | "assistant"
    content: str

async def openai_compatible_call(
    messages: list[ChatMessage],
    model: str = "gpt-4o",
    api_base: str = "https://api.openai.com/v1",
    api_key: str = "sk-xxx",
    temperature: float = 0.7,
    max_tokens: int = 2048,
) -> str:
    """OpenAI 兼容 API 的标准调用（支持自部署模型）"""
    headers = {
        "Authorization": f"Bearer {api_key}",
        "Content-Type": "application/json",
    }

    payload = {
        "model": model,
        "messages": [{"role": m.role, "content": m.content} for m in messages],
        "temperature": temperature,
        "max_tokens": max_tokens,
    }

    async with httpx.AsyncClient(timeout=120.0) as client:
        r = await client.post(f"{api_base}/chat/completions", headers=headers, json=payload)
        r.raise_for_status()
        data = r.json()
        return data["choices"][0]["message"]["content"]

# --- 非流式调用示例 ---
async def chat_demo():
    messages = [
        ChatMessage(role="system", content="你是一个资深 Python 工程师"),
        ChatMessage(role="user", content="解释 Python 的 GIL"),
    ]
    reply = await openai_compatible_call(messages, model="gpt-4o")
    print(f"Assistant: {reply}")

if __name__ == "__main__":
    asyncio.run(chat_demo())
```

### httpx + OpenAI 兼容性速查

| 场景 | 关键配置 | 说明 |
|------|---------|------|
| 自部署 vLLM | `api_base="http://localhost:8080/v1"` | 本地推理 |
| Azure OpenAI | `api_base="https://xxx.openai.azure.com/openai/deployments/xxx"` | Azure 端点 |
| 流式输出 | `stream=True` + `client.stream()` | SSE 逐 token |
| 重试与降级 | `max_retries=3` + 指数退避 | 生产级容错 |

---

## 必读资源

| 资源 | 链接 | 说明 |
|------|------|------|
| httpx 官方文档 | https://www.python-httpx.org/ | 完整 API 参考 |
| httpx GitHub | https://github.com/encode/httpx | 源码与 issue |
| OpenAI API 文档 | https://platform.openai.com/docs/api-reference | Chat Completions 协议 |
| SSE 规范 | https://html.spec.whatwg.org/multipage/server-sent-events.html | Server-Sent Events 标准 |
| 《Async HTTP in Python》 | — | David Beazley 的并发网络编程 |
