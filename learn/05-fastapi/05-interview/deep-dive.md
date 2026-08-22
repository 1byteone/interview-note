# 深挖题 — 底层原理与设计思想

> 适用：🎯 面试深挖
> 目标：深入理解 FastAPI 的底层实现和设计哲学

---

## 1. Starlette 底层

FastAPI 是 Starlette 的上层封装。Starlette 提供：

- **ASGI 基础**：请求/响应生命周期管理
- **路由系统**：基于 Radix Tree 的高效路径匹配
- **中间件**：洋葱模型，`__call__` 方法链式调用
- **WebSocket**：WebSocket 端点支持
- **后台任务**：`BackgroundTask` 实现

### FastAPI 在 Starlette 之上增加了什么？

1. **Pydantic 集成**：自动请求验证和序列化
2. **依赖注入**：`Depends` 系统
3. **OpenAPI 生成**：自动文档
4. **类型安全**：Python 类型注解驱动

### 底层调用链

```
请求 → ASGI Server(Uvicorn) → Starlette(ASGI app) → FastAPI(路由匹配)
    → 依赖解析 → 参数验证(Pydantic) → 路由函数 → 响应序列化 → 响应
```

---

## 2. ASGI 协议

ASGI（Asynchronous Server Gateway Interface）是 Python 异步 Web 的规范。

### ASGI 消息格式

```python
# 请求事件
{
    "type": "http.request",
    "method": "GET",
    "path": "/products/1",
    "headers": [(b"host", b"localhost:8000")],
    "body": b"",
}

# 响应事件
{
    "type": "http.response.start",
    "status": 200,
    "headers": [(b"content-type", b"application/json")],
}

# 响应体
{
    "type": "http.response.body",
    "body": b'{"id": 1, "name": "iPhone"}',
    "more_body": False,
}
```

### ASGI 应用签名

```python
async def app(scope: dict, receive: Callable, send: Callable) -> None:
    """
    scope: 连接信息（请求方法、路径、头等）
    receive: 接收事件的异步函数
    send: 发送事件的异步函数
    """
    if scope["type"] == "http":
        await send({
            "type": "http.response.start",
            "status": 200,
            "headers": [(b"content-type", b"text/plain")],
        })
        await send({
            "type": "http.response.body",
            "body": b"Hello, ASGI!",
        })
    elif scope["type"] == "websocket":
        # WebSocket 处理
        pass
```

### ASGI vs WSGI

| 特性 | ASGI | WSGI |
|------|------|------|
| 异步支持 | 原生 | 不支持 |
| WebSocket | 支持 | 不支持 |
| HTTP/2 | 支持 | 不支持 |
| 服务器推送 | 支持 | 不支持 |
| 性能 | 高 | 中 |
| 框架 | FastAPI, Starlette | Flask, Django(传统) |

---

## 3. 依赖注入实现原理

### 源码级理解

`Depends` 实际上是一个标记对象，FastAPI 在路由被调用时：

1. 解析路由函数的参数签名
2. 识别出被 `Depends()` 包裹的参数
3. 递归解析依赖的依赖
4. 构建依赖图（Dependency Graph）
5. 按拓扑顺序执行依赖函数
6. 将结果注入路由参数

### 简化实现

```python
from inspect import signature
from typing import get_type_hints


class Depends:
    def __init__(self, dependency=None):
        self.dependency = dependency


class SimpleContainer:
    """简化版依赖注入容器"""
    def __init__(self):
        self._providers = {}
        self._singletons = {}

    def register(self, interface, provider):
        self._providers[interface] = provider

    def resolve(self, dependency):
        """解析依赖——递归"""
        # 检查是否是 Depends 标记
        if isinstance(dependency, Depends):
            dep_func = dependency.dependency
            # 递归解析依赖函数的参数
            params = signature(dep_func).parameters
            resolved_params = {}
            for name, param in params.items():
                if isinstance(param.default, Depends):
                    resolved_params[name] = self.resolve(param.default)
            return dep_func(**resolved_params)
        return dependency


# 使用
container = SimpleContainer()


def get_db():
    return {"url": "postgresql://..."}


def get_user(db=Depends(get_db)):
    return {"id": 1, "db": db}


def get_profile(user=Depends(get_user)):
    return {"profile": "admin", "user": user}


# 解析
result = container.resolve(Depends(get_profile))
# 结果: {"profile": "admin", "user": {"id": 1, "db": {"url": "postgresql://..."}}}
```

### FastAPI 依赖注入的核心设计

1. **函数式**：依赖就是普通函数，没有类注解的复杂性
2. **显式**：所有依赖在函数签名中可见，而非隐式注入
3. **嵌套**：依赖可以嵌套，形成清晰的依赖链
4. **可测试**：`dependency_overrides` 轻松替换依赖
5. **无状态**：默认每次请求创建新实例，避免共享状态问题

---

## 4. Pydantic v2 核心改进

### Rust 核心引擎

Pydantic v2 的核心用 Rust 实现（`pydantic-core`），Python 仅作为 API 层。

```
Pydantic v1: Python 验证 → 性能瓶颈
Pydantic v2: Rust 验证 → 5-50x 性能提升
```

### 验证模式变化

```python
from pydantic import BaseModel, field_validator, model_validator


class Product(BaseModel):
    name: str
    price: float

    # v1 旧语法
    # @validator("name")
    # def name_must_not_be_empty(cls, v):
    #     ...

    # v2 新语法
    @field_validator("name")
    @classmethod
    def name_must_not_be_empty(cls, v: str) -> str:
        if not v.strip():
            raise ValueError("名称不能为空")
        return v.strip()

    @model_validator(mode="after")
    def check_product(self) -> "Product":
        if self.price < 0:
            raise ValueError("价格不能为负")
        return self
```

### 严格模式 vs 宽松模式

```python
from pydantic import BaseModel


class StrictProduct(BaseModel):
    name: str
    price: int  # 严格模式：不接受 float

    model_config = {"strict": True}


# 宽松模式（默认）：自动类型转换
product = Product(price=99.9)  # 自动转换
# 严格模式：类型必须精确匹配
# StrictProduct(price=99.9)  # 报错
```

### 性能对比

```python
import time
from pydantic import BaseModel


class Model(BaseModel):
    id: int
    name: str
    price: float
    tags: list[str]


data = {"id": 1, "name": "test", "price": 99.9, "tags": ["a", "b", "c"]}

# Pydantic v2: ~100万次/秒
# Pydantic v1: ~5万次/秒
# 性能提升约 20x
```

---

## 5. 路由系统底层

### Radix Tree 匹配

FastAPI/Starlette 使用 Radix Tree（基数树）进行路径匹配，而不是简单的正则匹配或线性查找。

```python
# 路由注册示例
routes = [
    "/users/me",
    "/users/{user_id}",
    "/users/{user_id}/posts",
    "/users/{user_id}/posts/{post_id}",
    "/products/",
    "/products/{product_id}",
]

# 转换为 Radix Tree
# /users
#   /me
#   /{user_id}
#     /posts
#       /{post_id}
# /products
#   /
#   /{product_id}

# 匹配 /users/123/posts/456
# 1. 匹配 /users/
# 2. 匹配 {user_id} = 123
# 3. 匹配 /posts/
# 4. 匹配 {post_id} = 456
```

### 路由优先级

```python
# FastAPI 的匹配顺序：
# 1. 精确路径（/users/me）
# 2. 路径参数（/users/{user_id}）
# 3. 按注册顺序（先注册的先匹配）

# 如果交换顺序：
@app.get("/users/{user_id}")  # 先注册
def get_user(user_id: int): ...

@app.get("/users/me")  # 后注册
def get_me(): ...

# 访问 /users/me → 匹配 get_user，user_id = "me" → 类型转换错误
```

---

## 6. 中间件实现原理

### 洋葱模型

```python
class MiddlewareChain:
    """中间件链的简化实现"""
    def __init__(self):
        self.middlewares = []

    def add_middleware(self, middleware_cls):
        self.middlewares.append(middleware_cls)

    async def build_chain(self, app):
        """构建中间件链——洋葱模型"""
        # 从内到外包装
        current = app
        for middleware_cls in reversed(self.middlewares):
            middleware = middleware_cls(current)
            current = middleware
        return current


class LoggingMiddleware:
    """日志中间件"""
    def __init__(self, app):
        self.app = app

    async def __call__(self, scope, receive, send):
        print(f"请求: {scope['path']}")
        await self.app(scope, receive, send)
        print(f"响应完成")


# 执行顺序
# 请求 → LoggingMiddleware → RateLimitMiddleware → 路由 → RateLimitMiddleware → LoggingMiddleware → 响应
```

---

## 本章小结

| 底层技术 | 核心概念 | 面试关键词 |
|----------|----------|-----------|
| ASGI | 异步网关接口 | `scope/receive/send`，WebSocket 支持 |
| Starlette | ASGI 工具包 | Radix Tree 路由，洋葱中间件 |
| Depends | 依赖注入 | 函数式、显式、嵌套、可测试 |
| Pydantic v2 | Rust 核心验证 | 5-50x 性能、`field_validator`、严格模式 |
| Radix Tree | 高效路径匹配 | O(k) 时间复杂度，前缀压缩 |
| 洋葱模型 | 中间件链 | 请求→响应双向处理 |