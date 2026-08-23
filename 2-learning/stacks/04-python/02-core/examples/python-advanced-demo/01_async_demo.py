"""
Python 异步编程演示
====================
涵盖：async/await、asyncio.gather、create_task、timeout、aiohttp 示例
"""

import asyncio
import time


# ==================== 1. 基础 async/await ====================
print("=" * 60)
print("1. 基础 async/await / Basic Async Functions")
print("=" * 60)


async def greet_async(name: str, delay: float) -> str:
    """异步函数：模拟 I/O 操作"""
    await asyncio.sleep(delay)  # 模拟异步 I/O（如网络请求、数据库查询）
    return f"Hello, {name}!"


async def basic_demo():
    """简单异步调用"""
    result = await greet_async("Alice", 0.5)
    print(f"  {result}")
    result = await greet_async("Bob", 0.3)
    print(f"  {result}")


# 运行
start = time.perf_counter()
asyncio.run(basic_demo())
print(f"  顺序执行耗时: {time.perf_counter() - start:.2f}s")


# ==================== 2. asyncio.gather() 并发执行 ====================
print("\n" + "=" * 60)
print("2. asyncio.gather() / Concurrent Execution")
print("=" * 60)


async def fetch_data(url: str, delay: float) -> dict:
    """模拟异步获取数据"""
    await asyncio.sleep(delay)
    return {"url": url, "data": f"data from {url}", "delay": delay}


async def gather_demo():
    """并发执行多个异步任务"""
    tasks = [
        fetch_data("https://api.example.com/users", 1.0),
        fetch_data("https://api.example.com/products", 1.5),
        fetch_data("https://api.example.com/orders", 0.8),
    ]
    # gather() 并发执行所有任务，返回结果列表（顺序与传入顺序一致）
    results = await asyncio.gather(*tasks, return_exceptions=True)
    for r in results:
        print(f"  ✅ {r}")


start = time.perf_counter()
asyncio.run(gather_demo())
print(f"  并发执行耗时: {time.perf_counter() - start:.2f}s (如果顺序执行需要 ~3.3s)")


# ==================== 3. asyncio.create_task() ====================
print("\n" + "=" * 60)
print("3. asyncio.create_task() / Background Tasks")
print("=" * 60)


async def background_worker(name: str, interval: float, count: int):
    """后台工作协程"""
    for i in range(count):
        await asyncio.sleep(interval)
        print(f"  ⚙️ [{name}] 完成第 {i + 1}/{count} 次工作")
    return f"{name} 完成！"


async def task_demo():
    """创建后台任务，主协程继续做其他事"""
    print("  创建后台任务...")
    # create_task() 将协程包装成 Task，在后台调度执行
    task1 = asyncio.create_task(background_worker("Worker-A", 0.3, 3))
    task2 = asyncio.create_task(background_worker("Worker-B", 0.5, 2))

    print("  主协程继续工作...")
    await asyncio.sleep(0.2)
    print("  主协程做了些其他事情")

    # 等待后台任务完成
    result1 = await task1
    result2 = await task2
    print(f"  结果: {result1}, {result2}")


asyncio.run(task_demo())


# ==================== 4. asyncio.timeout() ====================
print("\n" + "=" * 60)
print("4. asyncio.timeout() / Timeout Control")
print("=" * 60)


async def slow_operation(seconds: float, name: str = "操作") -> str:
    """模拟慢操作"""
    await asyncio.sleep(seconds)
    return f"{name} 完成"


async def timeout_demo():
    """超时控制演示"""
    # ✅ 正常完成（在超时时间内）
    try:
        async with asyncio.timeout(2.0):
            result = await slow_operation(1.0, "快速操作")
            print(f"  ✅ {result}")
    except TimeoutError:
        print("  ❌ 超时！")

    # ❌ 超时场景
    try:
        async with asyncio.timeout(1.0):
            result = await slow_operation(3.0, "慢速操作")
            print(f"  ✅ {result}")
    except TimeoutError:
        print("  ❌ 慢速操作超时！（1秒限制）")

    # 使用 timeout_at() 设置绝对时间点
    deadline = asyncio.get_running_loop().time() + 1.5
    try:
        async with asyncio.timeout_at(deadline):
            result = await slow_operation(1.0, "有界操作")
            print(f"  ✅ {result}")
    except TimeoutError:
        print("  ❌ 有界操作超时！")


asyncio.run(timeout_demo())


# ==================== 5. aiohttp 异步 HTTP 客户端 ====================
print("\n" + "=" * 60)
print("5. aiohttp 异步 HTTP 客户端 / Async HTTP Client")
print("=" * 60)
print("  ⚠️ 需要安装 aiohttp: pip install aiohttp")


async def aiohttp_example():
    """
    使用 aiohttp 并发发送多个 HTTP 请求
    先安装: pip install aiohttp
    """
    import aiohttp

    urls = [
        "https://httpbin.org/delay/1",   # 延迟 1 秒
        "https://httpbin.org/delay/2",   # 延迟 2 秒
        "https://httpbin.org/get",       # 无延迟
    ]

    async def fetch(session: aiohttp.ClientSession, url: str) -> dict:
        """异步发送单个 HTTP 请求"""
        try:
            async with session.get(url, timeout=aiohttp.ClientTimeout(total=10)) as response:
                data = await response.json()  # 异步读取 JSON 响应
                return {"url": url, "status": response.status, "data": data}
        except Exception as e:
            return {"url": url, "error": str(e)}

    async with aiohttp.ClientSession() as session:
        # 并发请求所有 URL
        tasks = [fetch(session, url) for url in urls]
        results = await asyncio.gather(*tasks, return_exceptions=True)

        for result in results:
            if isinstance(result, dict):
                print(f"  ✅ {result['url']} → status={result.get('status', '?')}")
            else:
                print(f"  ❌ Error: {result}")


print("\n" + "=" * 60)
print("6. 实践建议 / Best Practices")
print("=" * 60)
print("""
1. 始终使用 asyncio.run() 作为入口点（Python 3.7+）
2. 使用 gather() 处理独立并发的任务
3. 使用 create_task() 将后台任务与主逻辑分离
4. 对 I/O 操作始终设置超时保护
5. 避免在协程中混用阻塞 I/O (如 time.sleep())
6. 使用 asyncio.run() 而不是手动管理事件循环
7. 使用 Semaphore 控制并发数，避免压垮下游服务
""")

# ==================== 异步上下文管理器 ====================
print("=" * 60)
print("7. 异步上下文管理器 / Async Context Manager")
print("=" * 60)


class AsyncResource:
    """模拟异步资源（如数据库连接）"""

    async def __aenter__(self):
        print("  🔗 打开资源连接...")
        await asyncio.sleep(0.2)
        print("  ✅ 资源已连接")
        return self

    async def __aexit__(self, exc_type, exc_val, exc_tb):
        print("  🔗 关闭资源连接...")
        await asyncio.sleep(0.1)
        print("  ✅ 资源已释放")
        return False

    async def query(self, sql: str) -> str:
        await asyncio.sleep(0.1)
        return f"查询结果: {sql}"


async def async_context_manager_demo():
    async with AsyncResource() as resource:
        result = await resource.query("SELECT * FROM users")
        print(f"  {result}")


asyncio.run(async_context_manager_demo())

print("\n" + "=" * 60)
print("全部演示完成！")
print("=" * 60)

# 运行方式: python 01_async_demo.py
# 需要安装: pip install aiohttp（如使用 HTTP 示例）