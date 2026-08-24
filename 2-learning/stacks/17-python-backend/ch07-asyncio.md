# 第七章：Python asyncio 异步编程深入（P0 精通）

> 📖 **参考资料**：[Python 官方 asyncio 文档](https://docs.python.org/3/library/asyncio.html) | [uvloop 高性能事件循环](https://github.com/MagicStack/uvloop)

asyncio 是 Python 后端的并发基石。本章深入事件循环原理、协程调度、gather/semaphore 选型、阻塞检测，以及 Gunicorn + Uvicorn 生产部署模型。

---

## 7.1 事件循环原理

```
  ┌──────────────────────────────────────────────────────────┐
  │                    Event Loop (单线程)                     │
  │                                                          │
  │   ┌─────────┐    ┌──────────┐    ┌──────────┐           │
  │   │ 协程队列 │───→│ 调度执行  │───→│ 完成回调  │───→ 结果  │
  │   └─────────┘    └──────────┘    └──────────┘           │
  │        ↑                                                 │
  │   ┌─────────────────────────────────┐                    │
  │   │        IO 多路复用 (epoll)       │                    │
  │   │  socket ready → 唤醒对应协程     │                    │
  │   └─────────────────────────────────┘                    │
  └──────────────────────────────────────────────────────────┘

  核心机制：
  1. 协程遇到 await（IO 操作）→ 挂起，注册到 epoll
  2. epoll 检测到 IO 就绪 → 唤醒协程，加入就绪队列
  3. 事件循环下一轮 → 执行就绪队列中的协程
  → 单线程内实现高并发，无需锁（同时间只执行一个协程）
```

---

## 7.2 协程与 Task

```python
import asyncio

# --- 协程定义 ---
async def fetch_data(url: str, delay: float) -> dict:
    """模拟异步 IO 操作"""
    print(f"[START] Fetching {url}")
    await asyncio.sleep(delay)  # 模拟网络请求
    print(f"[DONE]  Fetched {url}")
    return {"url": url, "status": 200, "data": f"result_of_{url}"}

# --- Task: 并发调度 ---
async def main():
    # ❌ 串行：总计 ~3 秒
    r1 = await fetch_data("api/users", 1.0)
    r2 = await fetch_data("api/orders", 1.0)
    r3 = await fetch_data("api/products", 1.0)

    # ✅ 并发：总计 ~1 秒
    r1, r2, r3 = await asyncio.gather(
        fetch_data("api/users", 1.0),
        fetch_data("api/orders", 1.0),
        fetch_data("api/products", 1.0),
    )
    print(r1, r2, r3)

# --- Task 生命周期 ---
async def task_lifecycle():
    task = asyncio.create_task(fetch_data("api/fast", 0.5))
    print(f"Task state: {task.done()}")   # False
    result = await task
    print(f"Task state: {task.done()}")   # True
    print(f"Task result: {result}")

if __name__ == "__main__":
    asyncio.run(main())
```

---

## 7.3 gather vs create_task vs Semaphore

| 方式 | 适用场景 | 是否限制并发 | 异常处理 |
|------|---------|-------------|---------|
| `asyncio.gather(*coros)` | 已知全部任务，等待全部完成 | ❌ 全部同时启动 | 第一个异常抛出（可配置） |
| `asyncio.create_task()` | 动态创建、需要取消控制 | ❌ 全部同时启动 | 需手动 add_done_callback |
| `Semaphore(n)` | 限制最大并发数 | ✅ 最多 n 个同时运行 | 配合 gather 使用 |

### Semaphore 限流池

```python
import asyncio

async def bounded_fetch(url: str, sem: asyncio.Semaphore) -> dict:
    """受 Semaphore 限制的协程"""
    async with sem:  # 限制并发数
        print(f"  → 开始请求 {url} (当前并发: {sem._value})")
        await asyncio.sleep(1)  # 模拟网络 IO
        return {"url": url, "ok": True}

async def main():
    sem = asyncio.Semaphore(3)  # 最多同时 3 个请求

    urls = [f"https://api.example.com/item/{i}" for i in range(20)]
    tasks = [bounded_fetch(url, sem) for url in urls]

    results = await asyncio.gather(*tasks, return_exceptions=True)
    ok = sum(1 for r in results if not isinstance(r, Exception))
    fail = len(results) - ok
    print(f"成功: {ok}, 失败: {fail}")

if __name__ == "__main__":
    asyncio.run(main())
```

---

## 7.4 IO 密集 vs CPU 密集

```python
import asyncio
import time
from concurrent.futures import ProcessPoolExecutor

# --- IO 密集：asyncio 原生支持，直接并发 ---
async def io_bound():
    """数据库查询、HTTP 请求、文件读写 → asyncio.sleep / await"""
    await asyncio.sleep(1)  # 不阻塞事件循环

# --- CPU 密集：必须卸载到子进程，否则阻塞整个事件循环 ---
def cpu_bound_blocking(n: int) -> int:
    """纯计算：斐波那契 → CPU 密集"""
    if n <= 1:
        return n
    return cpu_bound_blocking(n - 1) + cpu_bound_blocking(n - 2)

async def cpu_bound_safe(n: int) -> int:
    """用 ProcessPoolExecutor 将 CPU 计算卸载到子进程"""
    loop = asyncio.get_running_loop()
    with ProcessPoolExecutor() as pool:
        result = await loop.run_in_executor(pool, cpu_bound_blocking, n)
    return result

async def main():
    # IO 密集：10 个请求并发 1 秒完成
    await asyncio.gather(*[io_bound() for _ in range(10)])

    # CPU 密集：卸载到子进程池，不阻塞事件循环
    t0 = time.perf_counter()
    result = await cpu_bound_safe(35)
    elapsed = time.perf_counter() - t0
    print(f"Fib(35)={result}, 耗时 {elapsed:.2f}s")

if __name__ == "__main__":
    asyncio.run(main())
```

> ⚠️ **经验法则**：凡是 `time.sleep()`、`requests.get()`、`json.dumps()` 等同步阻塞调用出现在 async 函数中，都会阻塞事件循环。IO 密集用 `await`，CPU 密集用 `run_in_executor`。

---

## 7.5 常见陷阱：阻塞事件循环

```python
import asyncio
import time

# --- 陷阱 1: 在协程中调用 time.sleep ---
async def trap_blocking():
    # ❌ time.sleep(2) 会阻塞整个事件循环 2 秒！
    # 所有其他协程都被卡住
    await asyncio.sleep(2)  # ✅ 正确：让出控制权给事件循环

# --- 陷阱 2: 用 asyncio.to_thread 保护同步库 ---
import httpx  # 假设某个同步 HTTP 库
async def safe_sync_call():
    # 如果某个库只提供同步 API，用 to_thread 包装
    result = await asyncio.to_thread(httpx.get, "https://httpbin.org/delay/1")
    return result.status_code

# --- 陷阱 3: 检测阻塞事件循环 ---
async def blocking_detector():
    """检测是否有协程阻塞了事件循环"""
    loop = asyncio.get_running_loop()
    start = loop.time()
    await asyncio.sleep(0.1)
    elapsed = loop.time() - start

    if elapsed > 0.15:  # 容差 50ms
        print(f"⚠️ 事件循环被阻塞了 {elapsed*1000:.0f}ms")
    else:
        print(f"✅ 事件循环响应正常: {elapsed*1000:.1f}ms")

if __name__ == "__main__":
    asyncio.run(blocking_detector())
```

---

## 7.6 Gunicorn + Uvicorn 多 Worker 模型

```
  Gunicorn (Master Process)
  ├── Worker 1: Uvicorn Worker ──→ Event Loop (uvloop) ──→ 1000+ 并发连接
  ├── Worker 2: Uvicorn Worker ──→ Event Loop (uvloop) ──→ 1000+ 并发连接
  ├── Worker 3: Uvicorn Worker ──→ Event Loop (uvloop) ──→ 1000+ 并发连接
  └── Worker 4: Uvicorn Worker ──→ Event Loop (uvloop) ──→ 1000+ 并发连接

  Worker 数 = 2 * CPU_CORES + 1  (经验公式)
  每个 Worker 是独立进程，各自运行一个事件循环
```

### gunicorn.conf.py 配置

```python
# gunicorn.conf.py
import multiprocessing

# Worker 类：使用 UvicornWorker 支持 ASGI
worker_class = "uvicorn.workers.UvicornWorker"

# Worker 数量：CPU 核数 * 2 + 1
workers = multiprocessing.cpu_count() * 2 + 1

# 绑定地址
bind = "0.0.0.0:8000"

# 超时：30 秒无响应自动重启
timeout = 30

# 平滑重启：旧 Worker 处理完当前请求后再退出
graceful_timeout = 30

# 日志
accesslog = "-"
errorlog = "-"
loglevel = "info"

# 预加载应用（节省内存，但不支持 reload）
preload_app = True
```

### 启动命令

```bash
# 生产环境
gunicorn main:app -c gunicorn.conf.py

# 开发环境（热重载）
uvicorn main:app --host 0.0.0.0 --port 8000 --reload --workers 1
```

### uvloop 加速

```python
# 在入口文件最前面安装 uvloop（替代默认 asyncio 事件循环）
import uvloop
uvloop.install()

# 之后再 import 其他模块
from fastapi import FastAPI
app = FastAPI()
```

---

## 必读资源

| 资源 | 链接 | 说明 |
|------|------|------|
| Python asyncio 官方文档 | https://docs.python.org/3/library/asyncio.html | 官方权威参考 |
| uvloop | https://github.com/MagicStack/uvloop | 替代默认事件循环，性能提升 2-4 倍 |
| 《Python 异步编程》 | David Beazley 著 | 协程与并发编程深入 |
| ASGI 规范 | https://asgi.readthedocs.io/ | Uvicorn/Hypercorn 的底层协议 |
| How the Event Loop Works | https://realpython.com/async-io-python/ | Real Python 图解异步 IO |
