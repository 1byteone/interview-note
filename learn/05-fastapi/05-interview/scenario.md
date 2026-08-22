# 场景题 — 实战问题与解决方案

> 适用：🎯 面试实战
> 目标：掌握 FastAPI 在真实业务场景中的问题解决思路

---

## 场景一：API 性能优化

**问题**：AI 商城的搜索 API 响应时间超过 3 秒，如何优化？

### 分析思路

1. **定位瓶颈**：使用中间件记录每个请求的耗时分布
2. **常见瓶颈**：数据库查询慢、LLM 调用慢、序列化开销大、无缓存

### 解决方案

**方案一：异步并发**

```python
# 优化前：串行执行
@app.get("/search")
async def search(query: str):
    # 1. 查询 ES（500ms）
    products = await search_es(query)
    # 2. 查询 LLM（2000ms）
    recommendation = await call_llm(query)
    # 总耗时：2500ms
    return {"products": products, "recommendation": recommendation}


# 优化后：并发执行
@app.get("/search")
async def search(query: str):
    # 同时查询 ES 和 LLM
    products_task = search_es(query)
    llm_task = call_llm(query)
    products, recommendation = await asyncio.gather(
        products_task, llm_task,
    )
    # 总耗时：2000ms（取最慢的）
    return {"products": products, "recommendation": recommendation}
```

**方案二：缓存层**

```python
import aioredis
import json
from fastapi import FastAPI

redis = aioredis.from_url("redis://localhost:6379/0")


@app.get("/search")
async def search(query: str):
    # 1. 查缓存
    cached = await redis.get(f"search:{query}")
    if cached:
        return json.loads(cached)

    # 2. 缓存未命中，查询并设置缓存
    products = await search_es(query)
    result = {"products": products}

    # 缓存 60 秒
    await redis.setex(f"search:{query}", 60, json.dumps(result))
    return result
```

**方案三：数据库优化**

```sql
-- 1. 添加索引
CREATE INDEX idx_products_name ON products(name);
CREATE INDEX idx_products_category ON products(category);
```

```python
# 2. 连接池配置
engine = create_async_engine(
    DATABASE_URL,
    pool_size=20,       # 连接池大小
    max_overflow=10,    # 最大溢出连接数
    pool_pre_ping=True, # 连接前检查
)

# 3. 只查询需要的字段
query = select(Product.id, Product.name, Product.price).limit(20)
```

**方案四：响应压缩**

```python
from fastapi import FastAPI
from fastapi.middleware.gzip import GZipMiddleware

app = FastAPI()
app.add_middleware(GZipMiddleware, minimum_size=1000)
```

### 优化效果

| 优化手段 | 预期效果 |
|----------|----------|
| 异步并发（gather） | 减少 50% 耗时 |
| Redis 缓存 | 热数据响应 < 10ms |
| 数据库索引 | 查询加速 10-100 倍 |
| 连接池 | 避免连接建立开销 |
| GZip 压缩 | 传输体积减少 60-80% |

**回答要点**：先说定位（测量），再说方案（并发/缓存/索引），最后说效果量化。

---

## 场景二：WebSocket 连接管理

**问题**：客服系统的 WebSocket 连接数达到 1 万，出现连接丢失、内存暴涨、消息乱序问题。

### 分析思路

1. **连接丢失**：无心跳、断线未清理
2. **内存暴涨**：连接集合无限增长、消息队列堆积
3. **消息乱序**：单连接并发发消息

### 解决方案

**方案一：连接上限保护**

```python
from fastapi import FastAPI, WebSocket, WebSocketDisconnect
import asyncio

app = FastAPI()

MAX_CONNECTIONS = 10000


class ConnectionManager:
    def __init__(self, max_connections: int = MAX_CONNECTIONS):
        self.connections: dict[str, WebSocket] = {}
        self.max_connections = max_connections

    async def connect(self, client_id: str, websocket: WebSocket) -> bool:
        """连接，超限时拒绝"""
        if len(self.connections) >= self.max_connections:
            await websocket.accept()
            await websocket.send_text(json.dumps({
                "type": "error",
                "code": "CONNECTION_LIMIT",
                "message": "连接数已满，请稍后重试",
            }))
            await websocket.close()
            return False

        await websocket.accept()
        self.connections[client_id] = websocket
        return True


manager = ConnectionManager()
```

**方案二：心跳 + 超时清理**

```python
@app.websocket("/ws/{client_id}")
async def websocket_endpoint(websocket: WebSocket, client_id: str):
    if not await manager.connect(client_id, websocket):
        return

    # 启动心跳检测
    watchdog = asyncio.create_task(heartbeat(client_id))
    try:
        while True:
            try:
                data = await asyncio.wait_for(
                    websocket.receive_text(), timeout=60,
                )
                # 更新最后活跃时间
                manager.update_last_active(client_id)
            except asyncio.TimeoutError:
                # 超时无活动——断开
                await websocket.close(code=1000)
                break
    except WebSocketDisconnect:
        pass
    finally:
        manager.disconnect(client_id)
        watchdog.cancel()
```

**方案三：消息序列化（防乱序）**

```python
import asyncio


class OrderedSender:
    """为单个连接提供有序发送"""
    def __init__(self):
        self._lock = asyncio.Lock()

    async def send(self, websocket: WebSocket, message: str):
        """加锁保证发送顺序"""
        async with self._lock:
            await websocket.send_text(message)
```

**回答要点**：先分类问题（连接丢失/内存/乱序），逐个给出对应方案（心跳/上限/锁）。

---

## 场景三：大文件上传

**问题**：商城支持上传商品图片/视频，单文件可达 500MB，当前实现直接 `await request.body()` 导致内存溢出。

### 分析思路

1. **问题根因**：`request.body()` 将整个文件读入内存
2. **正确方案**：分片读取 + 流式处理 + 上传进度

### 解决方案

**方案一：UploadFile 流式读取**

```python
from fastapi import FastAPI, UploadFile, File
import aiofiles

app = FastAPI()


@app.post("/upload")
async def upload_file(file: UploadFile = File(...)):
    """流式接收文件到磁盘"""
    chunk_size = 1024 * 1024  # 1MB

    # 流式写入
    async with aiofiles.open(f"uploads/{file.filename}", "wb") as out_file:
        while True:
            chunk = await file.read(chunk_size)
            if not chunk:
                break
            await out_file.write(chunk)

    return {
        "filename": file.filename,
        "content_type": file.content_type,
        "size": file.size,
    }
```

**方案二：分片上传（前端配合）**

```python
from pydantic import BaseModel


class ChunkUploadRequest(BaseModel):
    upload_id: str       # 上传会话 ID
    chunk_index: int     # 当前分片序号
    total_chunks: int    # 总分片数
    filename: str


@app.post("/upload/chunk")
async def upload_chunk(
    request: ChunkUploadRequest,
    file: UploadFile = File(...),
):
    """接收单个分片"""
    chunk_dir = f"uploads/{request.upload_id}/"
    chunk_path = f"{chunk_dir}{request.chunk_index}"

    os.makedirs(chunk_dir, exist_ok=True)
    async with aiofiles.open(chunk_path, "wb") as f:
        await f.write(await file.read())

    return {"received": request.chunk_index}


@app.post("/upload/merge")
async def merge_chunks(request: MergeRequest):
    """合并分片"""
    chunk_dir = f"uploads/{request.upload_id}/"
    output_path = f"uploads/{request.filename}"

    async with aiofiles.open(output_path, "wb") as out_file:
        for i in range(request.total_chunks):
            async with aiofiles.open(f"{chunk_dir}{i}", "rb") as chunk:
                await out_file.write(await chunk.read())

    # 清理分片目录
    shutil.rmtree(chunk_dir)
    return {"filename": request.filename, "size": os.path.getsize(output_path)}
```

**方案三：文件服务器分离**

- 上传到对象存储（MinIO / 阿里云 OSS / S3）
- FastAPI 只负责元数据处理
- 使用 `presigned URL` 直传，减少服务端带宽压力

**回答要点**：先指出问题（全部读入内存），再给方案（流式/分片/对象存储），最后提限流和文件校验。

---

## 场景四：请求超时处理

**问题**：调用第三方 LLM API 经常超时（>30s），导致用户长时间无响应或 API 网关 504。

### 分析思路

1. **超时层级**：客户端超时 → 服务端超时 → 依赖服务超时
2. **核心策略**：快速失败 + 流式响应 + 重试/降级

### 解决方案

**方案一：客户端超时设置**

```python
# 设置合理的超时时间
client = httpx.AsyncClient(
    timeout=httpx.Timeout(
        connect=5.0,    # 连接超时
        read=30.0,      # 读取超时
        write=10.0,     # 写入超时
        pool=5.0,       # 连接池超时
    ),
)
```

**方案二：流式响应避免长时间等待**

```python
@app.post("/chat")
async def chat(request: ChatRequest):
    """使用 SSE 流式返回，避免用户长时间等待"""
    return StreamingResponse(
        stream_llm(request.message),
        media_type="text/event-stream",
    )
```

**方案三：超时降级**

```python
import asyncio


async def call_llm_with_timeout(prompt: str, timeout: float = 10.0):
    """带超时的 LLM 调用——超时后降级"""
    try:
        result = await asyncio.wait_for(call_llm(prompt), timeout=timeout)
        return {"source": "llm", "content": result}
    except asyncio.TimeoutError:
        return {
            "source": "fallback",
            "content": "当前回答由本地模型生成（LLM 超时降级）",
        }
```

**方案四：重试策略**

```python
import asyncio
from tenacity import retry, stop_after_attempt, wait_exponential


@retry(
    stop=stop_after_attempt(3),       # 最多重试 3 次
    wait=wait_exponential(multiplier=1, min=2, max=10),  # 指数退避
)
async def call_llm_with_retry(prompt: str):
    """带重试的 LLM 调用"""
    async with httpx.AsyncClient(timeout=30.0) as client:
        response = await client.post(LLM_URL, json={"prompt": prompt})
        response.raise_for_status()  # 非 2xx 触发重试
        return response.json()
```

**回答要点**：分层分析超时（客户端/服务端/依赖），给出组合策略（超时+流式+重试+降级）。

---

## 场景五：数据库连接池耗尽

**问题**：高并发下 PostgreSQL 报 "connection limit exceeded" 错误。

### 分析思路

1. **根因**：连接池配置不合理或连接泄漏（未关闭）
2. **排查**：监控连接数、检查 `finally` 释放逻辑

### 解决方案

```python
# 1. 合理配置连接池
engine = create_async_engine(
    DATABASE_URL,
    pool_size=20,            # 常规连接数
    max_overflow=10,         # 峰值额外连接
    pool_timeout=30,         # 获取连接超时
    pool_recycle=3600,       # 连接回收（防 MySQL 8h 断开）
)

# 2. 确保会话正确关闭（依赖注入模式）
async def get_session():
    async with async_session_maker() as session:
        yield session  # 无论成功失败都会自动 close
```

**回答要点**：连接池配置 + 会话生命周期管理（依赖注入 + 上下文管理器）。

---

## 本章小结

| 场景 | 核心问题 | 关键方案 |
|------|----------|----------|
| API 性能优化 | 响应慢 | 并发、缓存、索引、压缩 |
| WebSocket 管理 | 连接丢失/内存暴涨 | 上限保护、心跳、锁 |
| 大文件上传 | 内存溢出 | 流式读取、分片、对象存储 |
| 请求超时 | 长时间无响应 | 超时分层、流式、重试、降级 |
| 连接池耗尽 | 连接数超限 | 池配置、会话生命周期 |