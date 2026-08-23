# 异步与并发

> 适用：👶→🎯 入门至进阶
> 目标：掌握 FastAPI 的异步编程模型，理解 async/await 在 Web 应用中的最佳实践

---

## 1. async def vs def 路由

FastAPI 路由既支持同步函数也支持异步函数：

```python
from fastapi import FastAPI
import asyncio
import time

app = FastAPI()


# 同步路由——由线程池执行
@app.get("/sync")
def sync_endpoint():
    time.sleep(2)  # 阻塞调用
    return {"message": "同步", "耗时": "2秒"}


# 异步路由——由事件循环执行
@app.get("/async")
async def async_endpoint():
    await asyncio.sleep(2)  # 非阻塞调用
    return {"message": "异步", "耗时": "2秒"}
```

### 如何选择？

| 路由定义 | 执行方式 | 适用场景 |
|----------|----------|----------|
| `def` | 线程池 | CPU 密集型、同步 IO（SQLAlchemy、文件操作） |
| `async def` | 事件循环 | 异步 IO（HTTP 请求、数据库查询、WebSocket） |

> 关键原则：**如果路由内部有阻塞调用，用 `def`；如果全部是异步调用，用 `async def`。** 混用时要小心，在 `async def` 中调用同步阻塞函数会阻塞整个事件循环。

---

## 2. 异步数据库驱动

### asyncpg（PostgreSQL 异步驱动）

```python
import asyncpg
from fastapi import FastAPI

app = FastAPI()


async def get_db():
    conn = await asyncpg.connect(
        user="user", password="pass",
        database="mall", host="localhost"
    )
    try:
        yield conn
    finally:
        await conn.close()


@app.get("/products/")
async def list_products():
    conn = await anext(get_db())
    rows = await conn.fetch("SELECT * FROM products LIMIT 10")
    return [dict(row) for row in rows]
```

### motor（MongoDB 异步驱动）

```python
from motor.motor_asyncio import AsyncIOMotorClient
from fastapi import FastAPI

app = FastAPI()
client = AsyncIOMotorClient("mongodb://localhost:27017")
db = client.mall


@app.get("/products/")
async def list_products():
    cursor = db.products.find().limit(10)
    results = await cursor.to_list(length=10)
    return results
```

### databases（SQLAlchemy 兼容的异步库）

```python
from databases import Database
from fastapi import FastAPI

database = Database("postgresql+asyncpg://user:pass@localhost/mall")
app = FastAPI()


@app.on_event("startup")
async def startup():
    await database.connect()


@app.on_event("shutdown")
async def shutdown():
    await database.disconnect()


@app.get("/products/")
async def list_products():
    query = "SELECT * FROM products LIMIT 10"
    results = await database.fetch_all(query)
    return [dict(r) for r in results]
```

---

## 3. 并发请求处理

FastAPI 基于 Starlette，底层使用 asyncio 事件循环，能高效处理大量并发连接。

### 并发调用外部 API

```python
import httpx
from fastapi import FastAPI

app = FastAPI()


@app.get("/aggregate")
async def aggregate_data():
    """并发请求多个外部 API"""
    async with httpx.AsyncClient() as client:
        # 同时发起三个请求
        tasks = [
            client.get("https://api.example.com/products"),
            client.get("https://api.example.com/orders"),
            client.get("https://api.example.com/users"),
        ]
        results = await asyncio.gather(*tasks, return_exceptions=True)

    return {
        "products": results[0].json() if not isinstance(results[0], Exception) else None,
        "orders": results[1].json() if not isinstance(results[1], Exception) else None,
        "users": results[2].json() if not isinstance(results[2], Exception) else None,
    }
```

### asyncio.gather 并发控制

```python
import asyncio
from fastapi import FastAPI

app = FastAPI()


async def fetch_product(product_id: int) -> dict:
    """模拟异步查询单个商品"""
    await asyncio.sleep(0.1)  # 模拟数据库查询
    return {"id": product_id, "name": f"商品{product_id}"}


@app.get("/products/batch")
async def batch_products(ids: str):
    """批量查询——并发获取多个商品"""
    product_ids = [int(id) for id in ids.split(",")]

    # 并发查询所有商品
    tasks = [fetch_product(pid) for pid in product_ids]
    results = await asyncio.gather(*tasks)

    return {"results": results, "total": len(results)}
```

---

## 4. 后台任务 — BackgroundTasks

后台任务用于在返回响应后执行耗时操作，如发送邮件、记录日志、同步数据。

```python
from fastapi import FastAPI, BackgroundTasks

app = FastAPI()


def send_welcome_email(email: str, username: str):
    """模拟发送邮件（同步函数）"""
    import time
    time.sleep(3)
    print(f"已发送欢迎邮件到 {email}")


@app.post("/users/")
async def create_user(username: str, email: str, tasks: BackgroundTasks):
    # 立即返回响应
    tasks.add_task(send_welcome_email, email, username)
    return {"message": f"用户 {username} 创建成功，欢迎邮件稍后发送"}


# 更复杂的后台任务
async def sync_es_data(product_id: int):
    """异步同步数据到 Elasticsearch"""
    import httpx
    async with httpx.AsyncClient() as client:
        await client.post(
            "http://es:9200/products/_doc/",
            json={"product_id": product_id, "synced_at": "2025-01-01"},
        )


@app.post("/products/")
async def create_product(
    name: str,
    price: float,
    tasks: BackgroundTasks,
):
    product_id = 123  # 假设已创建
    tasks.add_task(sync_es_data, product_id)
    return {"id": product_id, "name": name}
```

### BackgroundTasks vs Celery

| 特性 | BackgroundTasks | Celery |
|------|----------------|--------|
| 进程模型 | 同一进程 | 独立 worker 进程 |
| 任务持久化 | 不支持 | Redis/RabbitMQ |
| 重试机制 | 无 | 内置重试 |
| 分布式 | 不适用 | 支持 |
| 适用场景 | 轻量级后台操作 | 重量级异步任务 |
| 复杂度 | 极低 | 较高 |

---

## 5. 实战：AI 搜索异步调用 LLM

```python
import asyncio
import httpx
from fastapi import FastAPI, BackgroundTasks
from pydantic import BaseModel

app = FastAPI()


class SearchRequest(BaseModel):
    query: str
    user_id: int | None = None


class SearchResponse(BaseModel):
    query: str
    answer: str
    sources: list[str]


# 模拟异步调用 LLM
async def call_llm(prompt: str) -> str:
    """异步调用 LLM API"""
    async with httpx.AsyncClient(timeout=30.0) as client:
        response = await client.post(
            "https://api.openai.com/v1/chat/completions",
            headers={"Authorization": "Bearer sk-xxx"},
            json={
                "model": "gpt-4o",
                "messages": [
                    {"role": "system", "content": "你是 AI 商城客服助手"},
                    {"role": "user", "content": prompt},
                ],
            },
        )
        data = response.json()
        return data["choices"][0]["message"]["content"]


# 异步搜索商品
async def search_products(query: str) -> list[str]:
    """异步搜索商品"""
    await asyncio.sleep(0.05)  # 模拟数据库查询
    # 实际项目中会查询 Elasticsearch 或 PostgreSQL
    products = ["iPhone 15", "MacBook Pro", "AirPods Pro"]
    return [p for p in products if query in p]


# 记录搜索日志
def log_search(query: str, user_id: int | None, result_count: int):
    """后台记录搜索日志"""
    import time
    time.sleep(0.1)
    print(f"[LOG] 用户 {user_id} 搜索 '{query}'，结果数: {result_count}")


@app.post("/ai-search", response_model=SearchResponse)
async def ai_search(request: SearchRequest, tasks: BackgroundTasks):
    """异步 AI 搜索——同时调用 LLM 和数据库"""
    # 并发执行：搜索商品 + 调用 LLM
    products_task = search_products(request.query)
    llm_task = call_llm(f"用户搜索: {request.query}，请推荐相关商品")

    products, llm_answer = await asyncio.gather(products_task, llm_task)

    # 后台记录日志
    tasks.add_task(log_search, request.query, request.user_id, len(products))

    return SearchResponse(
        query=request.query,
        answer=llm_answer,
        sources=products,
    )
```

---

## 6. 异步最佳实践

### 避免阻塞事件循环

```python
# ❌ 错误：在 async def 中调用阻塞函数
@app.get("/wrong")
async def wrong():
    time.sleep(5)  # 阻塞整个事件循环！
    return {"message": "错误"}


# ✅ 正确：使用 asyncio.to_thread 放到线程池
@app.get("/correct")
async def correct():
    await asyncio.to_thread(time.sleep, 5)  # 不阻塞
    return {"message": "正确"}


# ✅ 正确：使用同步 def
@app.get("/also-correct")
def also_correct():
    time.sleep(5)  # 在线程池中执行，不阻塞事件循环
    return {"message": "也正确"}
```

### 连接池管理

```python
# 全局连接池，避免每次请求创建新连接
import httpx

client = httpx.AsyncClient(timeout=30.0, limits=httpx.Limits(max_connections=100))


@app.on_event("startup")
async def startup():
    await client.__aenter__()


@app.on_event("shutdown")
async def shutdown():
    await client.__aexit__(None, None, None)
```

---

## 本章小结

| 概念 | 说明 | 类比 Java |
|------|------|-----------|
| `async def` | 异步路由，事件循环执行 | 无直接对应 |
| `def` | 同步路由，线程池执行 | `@Async` 线程池 |
| `asyncio.gather` | 并发执行多个任务 | `CompletableFuture.allOf` |
| `BackgroundTasks` | 轻量级后台任务 | `@Async` 方法 |
| `httpx.AsyncClient` | 异步 HTTP 客户端 | `WebClient` (Spring WebFlux) |
| `asyncpg` | 异步 PostgreSQL 驱动 | R2DBC |
| 连接池 | 复用连接 | `HikariCP` |

下一章将介绍 WebSocket 和 SSE 流式响应。