# WebSocket 与流式响应

> 适用：🎯 进阶
> 目标：掌握 WebSocket 连接管理和 SSE 流式响应，实现 AI 实时对话

---

## 1. WebSocket 基础

WebSocket 提供全双工通信通道，适用于实时聊天、推送通知、协同编辑等场景。

```python
from fastapi import FastAPI, WebSocket

app = FastAPI()


@app.websocket("/ws")
async def websocket_endpoint(websocket: WebSocket):
    await websocket.accept()
    await websocket.send_text("连接成功")
    while True:
        data = await websocket.receive_text()
        await websocket.send_text(f"服务端回复: {data}")
```

> Java 对比：`@ServerEndpoint` → `@app.websocket`。FastAPI 的 WebSocket 更简洁，但需要手动管理连接。

---

## 2. WebSocket 连接管理

生产环境中需要管理多个连接，支持广播和房间。

```python
from fastapi import FastAPI, WebSocket, WebSocketDisconnect
from typing import Set

app = FastAPI()


class ConnectionManager:
    """WebSocket 连接管理器"""
    def __init__(self):
        self.active_connections: Set[WebSocket] = set()

    async def connect(self, websocket: WebSocket):
        await websocket.accept()
        self.active_connections.add(websocket)

    def disconnect(self, websocket: WebSocket):
        self.active_connections.discard(websocket)

    async def send_personal(self, message: str, websocket: WebSocket):
        await websocket.send_text(message)

    async def broadcast(self, message: str):
        for connection in self.active_connections:
            try:
                await connection.send_text(message)
            except Exception:
                self.disconnect(connection)


manager = ConnectionManager()


@app.websocket("/ws/{client_id}")
async def websocket_endpoint(websocket: WebSocket, client_id: str):
    await manager.connect(websocket)
    try:
        await manager.broadcast(f"用户 {client_id} 已加入")
        while True:
            data = await websocket.receive_text()
            await manager.send_personal(f"你: {data}", websocket)
            await manager.broadcast(f"用户 {client_id}: {data}")
    except WebSocketDisconnect:
        manager.disconnect(websocket)
        await manager.broadcast(f"用户 {client_id} 已离开")
```

---

## 3. 心跳检测与断线重连

```python
from fastapi import FastAPI, WebSocket, WebSocketDisconnect
import asyncio
import json

app = FastAPI()


class HeartbeatManager:
    """带心跳检测的连接管理器"""
    def __init__(self, heartbeat_interval: int = 30):
        self.connections: dict[str, WebSocket] = {}
        self.heartbeat_interval = heartbeat_interval

    async def connect(self, client_id: str, websocket: WebSocket):
        await websocket.accept()
        self.connections[client_id] = websocket

    def disconnect(self, client_id: str):
        self.connections.pop(client_id, None)

    async def send(self, client_id: str, message: str):
        ws = self.connections.get(client_id)
        if ws:
            try:
                await ws.send_text(message)
            except Exception:
                self.disconnect(client_id)

    async def heartbeat(self, client_id: str):
        """心跳检测循环"""
        ws = self.connections.get(client_id)
        while ws:
            try:
                await ws.send_json({"type": "ping"})
                await asyncio.sleep(self.heartbeat_interval)
            except Exception:
                self.disconnect(client_id)
                break


manager = HeartbeatManager()


@app.websocket("/ws/{client_id}")
async def websocket_endpoint(websocket: WebSocket, client_id: str):
    await manager.connect(client_id, websocket)

    # 启动心跳
    heartbeat_task = asyncio.create_task(manager.heartbeat(client_id))

    try:
        while True:
            data = await websocket.receive_text()
            msg = json.loads(data)

            if msg.get("type") == "pong":
                continue  # 心跳回复

            # 处理业务消息
            await manager.send(client_id, json.dumps({
                "type": "message",
                "content": f"收到: {msg.get('content', '')}",
            }))
    except WebSocketDisconnect:
        manager.disconnect(client_id)
        heartbeat_task.cancel()
```

---

## 4. SSE（Server-Sent Events）流式响应

SSE 适用于服务端单向推送数据，如 AI 对话流式输出、日志推送、数据更新通知。

```python
from fastapi import FastAPI
from fastapi.responses import StreamingResponse
import asyncio
import json

app = FastAPI()


async def generate_events():
    """模拟事件流"""
    for i in range(10):
        yield f"data: {json.dumps({'count': i, 'message': f'事件 {i}'})}\n\n"
        await asyncio.sleep(1)


@app.get("/events")
async def sse_endpoint():
    return StreamingResponse(
        generate_events(),
        media_type="text/event-stream",
        headers={
            "Cache-Control": "no-cache",
            "Connection": "keep-alive",
            "X-Accel-Buffering": "no",  # 禁用 Nginx 缓冲
        },
    )
```

---

## 5. 实战：流式 AI 对话 API

将 LLM 的流式响应通过 SSE 实时推送给客户端。

```python
import httpx
import json
import asyncio
from fastapi import FastAPI
from fastapi.responses import StreamingResponse
from pydantic import BaseModel

app = FastAPI()


class ChatRequest(BaseModel):
    message: str
    session_id: str | None = None


async def stream_llm(prompt: str):
    """异步流式调用 LLM API"""
    async with httpx.AsyncClient(timeout=60.0) as client:
        async with client.stream(
            "POST",
            "https://api.openai.com/v1/chat/completions",
            headers={
                "Authorization": "Bearer sk-xxx",
                "Content-Type": "application/json",
            },
            json={
                "model": "gpt-4o",
                "messages": [{"role": "user", "content": prompt}],
                "stream": True,  # 开启流式
            },
        ) as response:
            async for line in response.aiter_lines():
                if line.startswith("data: "):
                    data = line[6:]
                    if data == "[DONE]":
                        break
                    try:
                        chunk = json.loads(data)
                        content = chunk["choices"][0]["delta"].get("content", "")
                        if content:
                            yield f"data: {json.dumps({'content': content})}\n\n"
                    except json.JSONDecodeError:
                        continue


@app.post("/chat/stream")
async def chat_stream(request: ChatRequest):
    """流式 AI 对话——SSE 推送"""
    return StreamingResponse(
        stream_llm(request.message),
        media_type="text/event-stream",
        headers={
            "Cache-Control": "no-cache",
            "Connection": "keep-alive",
            "X-Accel-Buffering": "no",
        },
    )


# 服务端直接使用 WebSocket 推送流式结果
@app.websocket("/ws/chat")
async def websocket_chat(websocket):
    """WebSocket 版 AI 对话"""
    await websocket.accept()
    async with httpx.AsyncClient(timeout=60.0) as client:
        while True:
            # 接收用户消息
            data = await websocket.receive_text()
            msg = json.loads(data)

            # 流式调用 LLM
            full_response = ""
            async with client.stream(
                "POST",
                "https://api.openai.com/v1/chat/completions",
                headers={"Authorization": "Bearer sk-xxx"},
                json={
                    "model": "gpt-4o",
                    "messages": [{"role": "user", "content": msg["content"]}],
                    "stream": True,
                },
            ) as response:
                async for line in response.aiter_lines():
                    if line.startswith("data: "):
                        data = line[6:]
                        if data == "[DONE]":
                            break
                        try:
                            chunk = json.loads(data)
                            content = chunk["choices"][0]["delta"].get("content", "")
                            if content:
                                full_response += content
                                await websocket.send_text(json.dumps({
                                    "type": "chunk",
                                    "content": content,
                                }))
                        except json.JSONDecodeError:
                            continue

            # 发送完成标记
            await websocket.send_text(json.dumps({
                "type": "done",
                "full_response": full_response,
            }))
```

---

## 6. WebSocket vs SSE 对比

| 特性 | WebSocket | SSE |
|------|-----------|-----|
| 通信方向 | 双向 | 服务端→客户端 |
| 协议 | ws:// / wss:// | HTTP |
| 浏览器支持 | 全部现代浏览器 | 全部现代浏览器（IE 除外） |
| 自动重连 | 需手动实现 | 内置 |
| 消息格式 | 任意（文本/二进制） | 仅文本（text/event-stream） |
| 并发连接限制 | 无 | 浏览器限制（通常 6 个/域名） |
| 适用场景 | 实时聊天、游戏 | AI 流式输出、通知推送 |

---

## 本章小结

| 场景 | 实现方案 | 关键代码 |
|------|----------|----------|
| 实时聊天 | WebSocket | `@app.websocket("/ws")` |
| 连接管理 | ConnectionManager | `set[WebSocket]` + broadcast |
| 心跳检测 | 定时 Ping/Pong | `asyncio.create_task` |
| AI 流式输出 | SSE | `StreamingResponse` |
| 流式 LLM 对话 | WebSocket + 流式 API | `client.stream()` + `aiter_lines()` |
| 断线重连 | 异常捕获 + 清理 | `WebSocketDisconnect` |

下一章将介绍测试与调试技巧。