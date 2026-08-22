# 02 路由与中间件

> 适用：👶 入门
> 目标：掌握 FastAPI 路由装饰器家族和中间件体系

---

## 1. 路由装饰器

FastAPI 支持所有标准 HTTP 方法的路由装饰器：

```python
from fastapi import FastAPI

app = FastAPI()


@app.get("/items/{item_id}")       # GET — 查询
@app.post("/items/")               # POST — 创建
@app.put("/items/{item_id}")       # PUT — 全量更新
@app.patch("/items/{item_id}")     # PATCH — 部分更新
@app.delete("/items/{item_id}")    # DELETE — 删除
@app.options("/items/{item_id}")   # OPTIONS — 预检请求
@app.head("/items/{item_id}")      # HEAD — 仅获取响应头
```

### WebSocket 路由

```python
from fastapi import FastAPI, WebSocket

app = FastAPI()


@app.websocket("/ws")
async def websocket_endpoint(websocket: WebSocket):
    await websocket.accept()
    while True:
        data = await websocket.receive_text()
        await websocket.send_text(f"收到: {data}")
```

---

## 2. 路由顺序与路径匹配

路由匹配顺序很重要——**先注册的路由优先匹配**。

```python
@app.get("/users/me")          # 1. 精确路径优先
def get_current_user():
    return {"user": "current"}


@app.get("/users/{user_id}")   # 2. 路径参数在后
def get_user(user_id: int):
    return {"user_id": user_id}
```

如果 `get_current_user` 定义在 `get_user` 之后，访问 `/users/me` 会被 `/users/{user_id}` 匹配，将 `me` 作为 `user_id` 参数，导致类型转换错误。

> Java 对比：Spring Boot 中 `@RequestMapping` 也会根据路径匹配优先级排序，但 FastAPI 严格遵循注册顺序。

### 路由标签与分组

```python
@app.get("/products/", tags=["商品"])
def list_products(): ...


@app.post("/products/", tags=["商品"])
def create_product(): ...


@app.get("/orders/", tags=["订单"])
def list_orders(): ...
```

`tags` 参数将路由分组，在 OpenAPI 文档中会按标签分组显示。

---

## 3. 中间件

中间件是每次请求处理前/后执行的钩子函数。

### 自定义中间件

```python
import time
from fastapi import FastAPI, Request

app = FastAPI()


@app.middleware("http")
async def add_process_time_header(request: Request, call_next):
    """记录请求处理时间"""
    start_time = time.perf_counter()
    response = await call_next(request)
    process_time = time.perf_counter() - start_time
    response.headers["X-Process-Time"] = str(process_time)
    return response
```

> Java 对比：类似 Spring Boot 的 `HandlerInterceptor` 或 `Filter`。区别在于 FastAPI 中间件是异步的，且通过 `call_next` 传递请求。

### 内置中间件 — CORSMiddleware

```python
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

app = FastAPI()

app.add_middleware(
    CORSMiddleware,
    allow_origins=["http://localhost:3000", "https://yourdomain.com"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)
```

| 参数 | 说明 | 生产建议 |
|------|------|----------|
| `allow_origins` | 允许的源 | 不要使用 `["*"]`，指定具体域名 |
| `allow_credentials` | 允许携带 Cookie | 为 `True` 时不能使用 `["*"]` |
| `allow_methods` | 允许的 HTTP 方法 | `["*"]` 或具体列表 |
| `allow_headers` | 允许的请求头 | `["*"]` 或具体列表 |

### 中间件执行顺序

中间件以**洋葱模型**执行：先注册的先处理请求，后注册的先处理响应。

```python
app.add_middleware(MiddlewareA)  # 请求 → A → B → 路由 → B → A → 响应
app.add_middleware(MiddlewareB)
```

---

## 4. 异常处理

### HTTPException

```python
from fastapi import FastAPI, HTTPException

app = FastAPI()


@app.get("/products/{product_id}")
def get_product(product_id: int):
    if product_id <= 0:
        raise HTTPException(
            status_code=400,
            detail="商品 ID 必须为正数",
            headers={"X-Error": "invalid_id"},
        )
    # ...
```

### 自定义异常处理器

```python
from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse


class InsufficientStockError(Exception):
    """库存不足异常"""
    def __init__(self, product_id: int, requested: int, available: int):
        self.product_id = product_id
        self.requested = requested
        self.available = available


app = FastAPI()


@app.exception_handler(InsufficientStockError)
async def insufficient_stock_handler(request: Request, exc: InsufficientStockError):
    return JSONResponse(
        status_code=409,
        content={
            "code": "INSUFFICIENT_STOCK",
            "message": f"商品 {exc.product_id} 库存不足",
            "requested": exc.requested,
            "available": exc.available,
        },
    )


@app.exception_handler(404)
async def not_found_handler(request: Request, exc):
    return JSONResponse(
        status_code=404,
        content={"code": "NOT_FOUND", "message": "请求的资源不存在"},
    )
```

### 全局异常处理器

```python
@app.exception_handler(Exception)
async def global_exception_handler(request: Request, exc: Exception):
    return JSONResponse(
        status_code=500,
        content={"code": "INTERNAL_ERROR", "message": "服务器内部错误"},
    )
```

> Java 对比：`@ExceptionHandler` + `@ControllerAdvice` → `@app.exception_handler()`。FastAPI 更灵活，可以针对特定异常类型或状态码注册处理器。

---

## 5. 实战：带日志和限流的中间件

```python
import time
import logging
from fastapi import FastAPI, Request, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse

logger = logging.getLogger("mall-api")
app = FastAPI()

# CORS 配置
app.add_middleware(
    CORSMiddleware,
    allow_origins=["https://mall.example.com"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


# 请求日志中间件
@app.middleware("http")
async def request_logging(request: Request, call_next):
    start = time.time()
    response = await call_next(request)
    elapsed = time.time() - start
    logger.info(
        f"{request.method} {request.url.path} "
        f"{response.status_code} {elapsed:.3f}s"
    )
    return response


# 简单限流中间件
request_counts = {}


@app.middleware("http")
async def rate_limit(request: Request, call_next):
    client_ip = request.client.host
    now = time.time()

    # 清理过期记录
    request_counts[client_ip] = [
        t for t in request_counts.get(client_ip, [])
        if now - t < 60
    ]

    # 限制每分钟 60 次
    if len(request_counts[client_ip]) >= 60:
        return JSONResponse(
            status_code=429,
            content={"code": "RATE_LIMITED", "message": "请求过于频繁"},
        )

    request_counts[client_ip].append(now)
    return await call_next(request)
```

---

## 本章小结

FastAPI 的路由和中间件系统设计简洁但功能强大。路由装饰器覆盖所有 HTTP 方法和 WebSocket；中间件以洋葱模型组织，支持自定义逻辑和内置组件如 CORS；异常处理通过 `HTTPException` 和自定义处理器实现细粒度控制。

对比 Spring Boot，FastAPI 的代码更紧凑，但概念上高度相似——适合 Java 开发者快速迁移。下一章将介绍 FastAPI 最核心的依赖注入机制。