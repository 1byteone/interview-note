# 异步编程

> 👶→🎯 核心进阶 | 预计阅读：45 分钟

异步编程是 Python 后端开发者的**必修课**。在 AI 场景中，你要同时调用多个大模型 API、批量 embedding、并发爬取数据——这些都需要异步。

---

## 1. 同步 vs 异步 vs 并行

```
同步：你点咖啡 → 等咖啡做好 → 再付钱
异步：你点咖啡 → 拿号 → 刷手机 → 震动取餐
并行：你和朋友各点一杯，两个咖啡师同时做
```

**Python 的异步模型是单线程协作式**：一个线程内，多个任务交替执行，遇到 I/O 等待就主动让出 CPU。

```python
import time

# 同步版本
def sync_work():
    time.sleep(1)   # 阻塞等待
    return "完成"

# 异步版本
import asyncio

async def async_work():
    await asyncio.sleep(1)  # 非阻塞，让出控制权
    return "完成"
```

**关键区别**：`time.sleep(1)` 让整个线程停 1 秒；`asyncio.sleep(1)` 让出当前协程，事件循环去执行其他协程。

---

## 2. async/await 语法

### 2.1 基本概念

```python
import asyncio

# 定义一个协程函数
async def fetch_data(url: str) -> dict:
    print(f"开始请求: {url}")
    await asyncio.sleep(1)  # 模拟网络延迟
    return {"url": url, "status": 200}

# 创建协程对象（不会执行）
coro = fetch_data("http://example.com")

# 运行协程
result = asyncio.run(coro)
print(result)
```

### 2.2 并发执行

```python
async def main():
    # 创建多个协程任务
    tasks = [
        fetch_data("http://api.example.com/users"),
        fetch_data("http://api.example.com/orders"),
        fetch_data("http://api.example.com/products"),
    ]

    # 并发执行，等待所有完成
    results = await asyncio.gather(*tasks)
    return results

# 运行
results = asyncio.run(main())
```

`asyncio.gather` 是并发执行的核心 API。三个请求总耗时约等于最慢的那个，而不是三个之和。

### 2.3 超时与取消

```python
async def main():
    try:
        # 超时控制
        result = await asyncio.wait_for(
            slow_request(),
            timeout=5.0  # 超过 5 秒抛 TimeoutError
        )
    except asyncio.TimeoutError:
        print("请求超时！")

    # 手动取消
    task = asyncio.create_task(long_running())
    await asyncio.sleep(1)
    task.cancel()
    try:
        await task
    except asyncio.CancelledError:
        print("任务已取消")
```

---

## 3. asyncio 事件循环

事件循环是 asyncio 的调度器，它运行一个无限循环，不断检查哪些任务可以继续执行：

```python
import asyncio

async def task(name: str, delay: float):
    print(f"{name} 开始")
    await asyncio.sleep(delay)
    print(f"{name} 完成，耗时 {delay}s")
    return name

# 手动操作事件循环（一般不需要，asyncio.run 帮你做了）
async def main():
    # create_task 将协程包装为 Task 并调度到事件循环
    t1 = asyncio.create_task(task("A", 2))
    t2 = asyncio.create_task(task("B", 1))

    # 等待所有任务完成
    done, pending = await asyncio.wait(
        [t1, t2],
        return_when=asyncio.FIRST_COMPLETED
    )
    print(f"已完成: {done}")

asyncio.run(main())
```

**事件循环的常见 API**：

| API | 作用 |
|---|---|
| `asyncio.run(coro)` | 创建事件循环，运行协程，结束后关闭 |
| `asyncio.create_task(coro)` | 将协程包装为 Task 并调度执行 |
| `asyncio.gather(*tasks)` | 并发执行多个协程，返回结果列表 |
| `asyncio.wait(tasks)` | 更灵活地等待任务（可设超时、FIRST_COMPLETED） |
| `asyncio.shield(coro)` | 保护协程不被取消 |

---

## 4. 案例：异步爬虫

```python
import asyncio
import aiohttp
from typing import List, Dict

HEADERS = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
}

async def fetch(session: aiohttp.ClientSession, url: str) -> Dict:
    """异步请求单个 URL"""
    try:
        async with session.get(url, headers=HEADERS, timeout=10) as resp:
            text = await resp.text()
            return {"url": url, "status": resp.status, "length": len(text)}
    except Exception as e:
        return {"url": url, "error": str(e)}

async def crawl(urls: List[str], concurrency: int = 5) -> List[Dict]:
    """控制并发数的异步爬虫"""
    semaphore = asyncio.Semaphore(concurrency)

    async def bounded_fetch(session, url):
        async with semaphore:  # 限制并发数量
            return await fetch(session, url)

    async with aiohttp.ClientSession() as session:
        tasks = [bounded_fetch(session, url) for url in urls]
        results = await asyncio.gather(*tasks)
        return results

async def main():
    urls = [
        "https://httpbin.org/delay/1",
        "https://httpbin.org/delay/2",
        "https://httpbin.org/delay/3",
        "https://httpbin.org/status/200",
        "https://httpbin.org/status/404",
    ]

    print(f"开始爬取 {len(urls)} 个 URL，并发数限制为 3...")
    start = asyncio.get_event_loop().time()
    results = await crawl(urls, concurrency=3)
    elapsed = asyncio.get_event_loop().time() - start

    for r in results:
        if "error" in r:
            print(f"  [失败] {r['url']}: {r['error']}")
        else:
            print(f"  [{r['status']}] {r['url']} ({r['length']} bytes)")

    print(f"总耗时: {elapsed:.2f}s")

if __name__ == "__main__":
    asyncio.run(main())
```

**运行结果**（5 个请求，并发 3，总耗时约 3 秒而不是 1+2+3=6 秒）：

```
开始爬取 5 个 URL，并发数限制为 3...
  [200] https://httpbin.org/delay/1 (300 bytes)
  [200] https://httpbin.org/delay/2 (300 bytes)
  [200] https://httpbin.org/delay/3 (300 bytes)
  [200] https://httpbin.org/status/200 (266 bytes)
  [404] https://httpbin.org/status/404 (264 bytes)
总耗时: 3.05s
```

---

## 5. 坑点：不要在异步中调用同步阻塞

这是异步编程的**头号坑**：

```python
import asyncio
import time

async def bad():
    # 错误：同步阻塞会阻塞整个事件循环
    time.sleep(3)

async def good():
    # 正确：异步等待，让出控制权
    await asyncio.sleep(3)

async def main():
    # 创建 10 个任务
    tasks = [bad() for _ in range(10)]
    start = time.time()
    await asyncio.gather(*tasks)
    print(f"耗时: {time.time() - start:.1f}s")  # 30 秒！不是 3 秒！
```

**解决方案**：

- 同步阻塞操作（如文件读写、CPU 密集计算）用 `asyncio.to_thread()` 放到线程池执行
- 数据库驱动要使用异步版本（如 `asyncpg`、`aiomysql`、`motor` for MongoDB）
- HTTP 请求使用 `aiohttp` 或 `httpx.AsyncClient`

```python
# 正确的做法：把阻塞操作放到线程池
async def correct():
    loop = asyncio.get_running_loop()
    # 在线程池执行同步阻塞
    result = await loop.run_in_executor(None, time.sleep, 3)
    return result
```

---

## 总结

| 概念 | 要点 |
|---|---|
| 协程 | `async def` 定义，`await` 交出控制权 |
| 并发 | `asyncio.gather` 并发执行，总耗时 ≈ 最慢任务 |
| 超时 | `asyncio.wait_for` 设置超时，防止无限等待 |
| 限流 | `asyncio.Semaphore` 控制并发数 |
| 坑点 | 不要在异步中混用同步阻塞 I/O |
| 异步数据库 | 使用 asyncpg / aiomysql / motor |

下一步：进入 [02-typing-and-testing.md](02-typing-and-testing.md) 学习类型注解与测试。