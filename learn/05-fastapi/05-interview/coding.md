# 代码题 — 手写实现

> 适用：🎯 面试手写
> 目标：掌握 FastAPI 高频代码题的实现套路

---

## 1. 自定义依赖注入

**题目**：不使用 FastAPI 的 `Depends`，实现一个简单的依赖注入容器，支持函数依赖和类依赖。

```python
from inspect import signature
from typing import Any, Callable


class SimpleDI:
    """手写依赖注入容器"""

    def __init__(self):
        self._registry: dict[type, Callable] = {}
        self._singletons: dict[type, Any] = {}

    def register(self, dependency_type: type, provider: Callable):
        """注册依赖"""
        self._registry[dependency_type] = provider

    def get(self, dependency_type: type) -> Any:
        """获取依赖实例（无缓存）"""
        if dependency_type not in self._registry:
            raise ValueError(f"未注册的依赖: {dependency_type}")
        return self._registry[dependency_type]()

    def get_singleton(self, dependency_type: type) -> Any:
        """获取单例依赖"""
        if dependency_type not in self._singletons:
            self._singletons[dependency_type] = self.get(dependency_type)
        return self._singletons[dependency_type]

    def resolve(self, func: Callable):
        """解析函数参数并调用"""
        params = signature(func).parameters
        kwargs = {}

        for name, param in params.items():
            annotation = param.annotation
            if annotation is not inspect.Parameter.empty:
                # 递归解析依赖的依赖
                kwargs[name] = self.get(annotation)

        return func(**kwargs)


# ---------- 使用示例 ----------

class Database:
    """数据库依赖"""
    def __init__(self):
        self.connected = True


class UserService:
    """用户服务——依赖数据库"""

    def __init__(self, db: Database):
        self.db = db

    def get_user(self, user_id: int) -> dict:
        return {"id": user_id, "connected": self.db.connected}


# 创建容器
di = SimpleDI()

# 注册依赖（自动解析类的构造参数）
di.register(Database, lambda: Database())
di.register(UserService, lambda: UserService(db=di.get(Database())))

# 获取并使用
service = di.get(UserService)
print(service.get_user(1))
# 输出: {'id': 1, 'connected': True}
```

**考察点**：`inspect.signature` 反射解析参数、递归依赖、单例与工厂。

---

## 2. 异步中间件

**题目**：实现一个异步中间件，统计每个请求的耗时并把耗时信息写入响应头。

```python
import time
from fastapi import FastAPI, Request


class TimingMiddleware:
    """异步耗时统计中间件"""

    def __init__(self, app):
        self.app = app

    async def __call__(self, scope, receive, send):
        if scope["type"] != "http":
            # 非 HTTP 请求（如 WebSocket）直接放行
            await self.app(scope, receive, send)
            return

        start = time.perf_counter()

        async def send_wrapper(message):
            """包装 send，在响应开始时写入耗时头"""
            if message["type"] == "http.response.start":
                headers = dict(message.get("headers", []))
                elapsed = time.perf_counter() - start
                headers[b"X-Process-Time-Ms"] = str(round(elapsed * 1000)).encode()
                message["headers"] = list(headers.items())
            await send(message)

        await self.app(scope, receive, send_wrapper)


# 在 FastAPI 中使用
app = FastAPI()
app.add_middleware(TimingMiddleware)


@app.get("/")
def root():
    return {"message": "hello"}
```

**考察点**：ASGI 中间件签名（scope/receive/send）、send 包装、协程闭包。

---

## 3. WebSocket 聊天室

**题目**：实现一个支持多房间的 WebSocket 聊天室，支持加入房间、广播消息、离开房间。

```python
from fastapi import FastAPI, WebSocket, WebSocketDisconnect
from typing import dict, set
import json

app = FastAPI()


class ChatRoomManager:
    """多房间聊天管理"""

    def __init__(self):
        # 房间 -> {用户名: WebSocket}
        self.rooms: dict[str, dict[str, WebSocket]] = {}

    async def join(self, room: str, username: str, websocket: WebSocket):
        """加入房间"""
        await websocket.accept()
        if room not in self.rooms:
            self.rooms[room] = {}
        self.rooms[room][username] = websocket
        await self.broadcast(room, {
            "type": "system",
            "message": f"{username} 加入了房间",
        })

    async def leave(self, room: str, username: str):
        """离开房间"""
        if room in self.rooms and username in self.rooms[room]:
            del self.rooms[room][username]
            if not self.rooms[room]:
                del self.rooms[room]
            await self.broadcast(room, {
                "type": "system",
                "message": f"{username} 离开了房间",
            })

    async def broadcast(self, room: str, message: dict):
        """向房间广播消息"""
        for ws in list(self.rooms.get(room, {}).values()):
            try:
                await ws.send_text(json.dumps(message))
            except Exception:
                pass

    async def send_private(self, room: str, target: str, message: dict):
        """私聊"""
        ws = self.rooms.get(room, {}).get(target)
        if ws:
            await ws.send_text(json.dumps(message))
            return True
        return False


manager = ChatRoomManager() 


@app.websocket("/ws/chat/{room}/{username}")
async def chat_endpoint(websocket: WebSocket, room: str, username: str):
    await manager.join(room, username, websocket)
    try:
        while True:
            data = await websocket.receive_text()
            msg = json.loads(data)

            if msg.get("target"):
                # 私聊
                await manager.send_private(
                    room, msg["target"],
                    {"type": "private", "from": username, "content": msg["content"]},
                )
            else:
                # 群聊
                await manager.broadcast(room, {
                    "type": "message",
                    "from": username,
                    "content": msg["content"],
                })
    except WebSocketDisconnect:
        await manager.leave(room, username)
```

**考察点**：连接集合设计、广播容错、WebSocketDisconnect 处理、房间隔离。

---

## 4. 单元测试

**题目**：对 FastAPI 的认证接口编写完整的单元测试。

```python
# app.py
import jwt
from fastapi import FastAPI, Depends, HTTPException, Header
from pydantic import BaseModel
from datetime import datetime, timedelta

app = FastAPI()
SECRET_KEY = "test-secret"


class UserCreate(BaseModel):
    username: str


class TokenResponse(BaseModel):
    access_token: str
    token_type: str = "bearer"


def create_token(username: str) -> str:
    """生成 JWT"""
    payload = {
        "sub": username,
        "exp": datetime.utcnow() + timedelta(minutes=30),
    }
    return jwt.encode(payload, SECRET_KEY, algorithm="HS256")


def get_current_user(authorization: str = Header(...)) -> str:
    """认证依赖"""
    try:
        token = authorization.replace("Bearer ", "")
        payload = jwt.decode(token, SECRET_KEY, algorithms=["HS256"])
        return payload["sub"]
    except Exception:
        raise HTTPException(status_code=401, detail="认证失败")


@app.post("/auth/login", response_model=TokenResponse)
def login(user: UserCreate):
    """登录接口"""
    return TokenResponse(access_token=create_token(user.username))


@app.get("/me")
def get_me(username: str = Depends(get_current_user)):
    """获取当前用户"""
    return {"username": username}
```

```python
# test_app.py
import pytest
from fastapi.testclient import TestClient
from app import app, create_token

client = TestClient(app)


class TestAuth:
    """认证接口测试"""

    def test_login_success(self):
        """登录成功"""
        response = client.post("/auth/login", json={"username": "zhangsan"})
        assert response.status_code == 200
        data = response.json()
        assert "access_token" in data
        assert data["token_type"] == "bearer"

    def test_login_missing_username(self):
        """缺少用户名"""
        response = client.post("/auth/login", json={})
        assert response.status_code == 422  # 验证错误

    def test_get_me_with_valid_token(self):
        """有效 Token 访问"""
        token = create_token("zhangsan")
        response = client.get(
            "/me",
            headers={"Authorization": f"Bearer {token}"},
        )
        assert response.status_code == 200
        assert response.json() == {"username": "zhangsan"}

    def test_get_me_without_token(self):
        """无 Token 访问"""
        response = client.get("/me")
        assert response.status_code == 401

    def test_get_me_with_invalid_token(self):
        """无效 Token 访问"""
        response = client.get(
            "/me",
            headers={"Authorization": "Bearer invalid-token"},
        )
        assert response.status_code == 401

    @pytest.mark.parametrize("header", [
        "",  # 空值
        "Basic abc123",  # 非 Bearer
        None,  # 缺失
    ])
    def test_get_me_invalid_auth_header(self, header):
        """各种无效认证头"""
        headers = {"Authorization": header} if header else {}
        response = client.get("/me", headers=headers)
        assert response.status_code == 401
```

**考察点**：JWT 生成/校验、异常场景覆盖、参数化测试、依赖注入覆盖。

---

## 5. 异步重试

**题目**：实现一个通用的异步重试装饰器，支持最大重试次数和退避策略。

```python
import asyncio
import functools
import random
from typing import Awaitable, Callable, TypeVar

T = TypeVar("T")


def async_retry(
    max_retries: int = 3,
    base_delay: float = 0.5,
    max_delay: float = 10.0,
    exponential: bool = True,
):
    """异步重试装饰器——支持指数退避

    参数:
        max_retries: 最大重试次数
        base_delay: 基础延迟（秒）
        max_delay: 最大延迟
        exponential: 是否使用指数退避
    """
    def decorator(func: Callable[..., Awaitable[T]]):
        @functools.wraps(func)
        async def wrapper(*args, **kwargs) -> T:
            for attempt in range(max_retries + 1):
                try:
                    return await func(*args, **kwargs)
                except Exception as e:
                    if attempt >= max_retries:
                        raise e  # 超过重试次数，抛出原始异常

                    # 计算退避时间
                    if exponential:
                        delay = min(base_delay * (2 ** attempt) + random.uniform(0, 0.1), max_delay)
                    else:
                        delay = base_delay

                    print(f"第 {attempt + 1} 次失败: {e}，{delay}s 后重试")
                    await asyncio.sleep(delay)

            return None  # 不可达
        return wrapper
    return decorator


# ---------- 使用示例 ----------

calls = 0


@async_retry(max_retries=3, base_delay=0.1)
async def flaky_api():
    """模拟不稳定 API"""
    global calls
    calls += 1
    if calls < 3:
        raise ConnectionError("模拟网络故障")
    return {"status": "success", "calls": calls}


async def main():
    result = await flaky_api()
    print(result)  # {'status': 'success', 'calls': 3}


asyncio.run(main())
```

**考察点**：装饰器实现、指数退避算法、异常传递、`functools.wraps` 保留元数据。

---

## 本章小结

| 题目 | 考点 | 常用 API |
|------|------|----------|
| 自定义依赖注入 | 反射、递归 | `inspect.signature` |
| 异步中间件 | ASGI 协议 | `scope/receive/send` |
| WebSocket 聊天室 | 连接管理 | `WebSocketDisconnect` |
| 单元测试 | 测试覆盖 | `TestClient`、`pytest` |
| 异步重试 | 装饰器、退避 | `asyncio.sleep` |