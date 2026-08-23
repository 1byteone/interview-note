# Python 代码题

> 🎯 面试冲刺 | 手写代码（白板 / 在线编辑器）

代码题是 Python 面试的**必经关卡**。下面是三道最高频的题目，先自己写一遍，再对照答案和"考察点"。

---

## 题目 1：手写装饰器

### 题目描述

实现一个 `retry` 装饰器：当被装饰函数抛异常时自动重试，最多重试 `attempts` 次，每次重试间隔 `delay` 秒。要求支持同步和异步函数（加分项）。

### 参考答案

```python
import asyncio
import time
from functools import wraps

def retry(attempts: int = 3, delay: float = 0.5, exceptions=(Exception,)):
    """
    重试装饰器：函数抛异常时自动重试。

    :param attempts: 最大尝试次数（含第一次）
    :param delay:    重试间隔秒数
    :param exceptions: 需要重试的异常类型元组
    """
    def decorator(func):
        # 分支一：异步函数
        if asyncio.iscoroutinefunction(func):

            @wraps(func)
            async def async_wrapper(*args, **kwargs):
                last_exc = None
                for i in range(attempts):
                    try:
                        return await func(*args, **kwargs)
                    except exceptions as e:
                        last_exc = e
                        print(f"[retry] 第 {i+1} 次失败: {e}")
                        if i < attempts - 1:
                            await asyncio.sleep(delay)
                raise last_exc

            return async_wrapper

        # 分支二：同步函数
        @wraps(func)
        def wrapper(*args, **kwargs):
            last_exc = None
            for i in range(attempts):
                try:
                    return func(*args, **kwargs)
                except exceptions as e:
                    last_exc = e
                    print(f"[retry] 第 {i+1} 次失败: {e}")
                    if i < attempts - 1:
                        time.sleep(delay)
            raise last_exc

        return wrapper
    return decorator


# ===== 使用 =====
@retry(attempts=3, delay=0.2)
def unstable_sync():
    import random
    if random.random() < 0.6:
        raise ConnectionError("网络错误")
    return "同步成功"

@retry(attempts=3, delay=0.2)
async def unstable_async():
    await asyncio.sleep(0.1)  # 模拟 I/O
    import random
    if random.random() < 0.6:
        raise TimeoutError("超时")
    return "异步成功"

# 测试
print(unstable_sync())
print(asyncio.run(unstable_async()))
```

### 考察点

- 装饰器的两层/三层嵌套结构（带参数装饰器）
- `functools.wraps` 保留元信息
- `asyncio.iscoroutinefunction` 区分同步/异步（加分）
- 异常处理与重试语义（最后一次失败要抛出）

---

## 题目 2：实现 LRU 缓存

### 题目描述

实现一个 LRU（Least Recently Used）缓存，支持 `get(key)` 与 `put(key, value)`，容量固定，超过容量淘汰最久未使用的。要求 get/put 都是 O(1)。

### 参考答案（双向链表 + 哈希表）

```python
class Node:
    """双向链表节点"""
    __slots__ = ("key", "value", "prev", "next")

    def __init__(self, key=None, value=None):
        self.key = key
        self.value = value
        self.prev = None
        self.next = None


class LRUCache:
    """LRU 缓存：哈希表 O(1) 查找 + 双向链表 O(1) 移动"""

    def __init__(self, capacity: int):
        self.capacity = capacity
        self.cache = {}              # key -> Node
        self.head = Node()           # 哨兵头（最久未使用的一侧）
        self.tail = Node()           # 哨兵尾（最近使用的一侧）
        self.head.next = self.tail
        self.tail.prev = self.head

    def _remove(self, node: Node) -> None:
        """从链表中摘除节点"""
        node.prev.next = node.next
        node.next.prev = node.prev

    def _add_to_tail(self, node: Node) -> None:
        """插入到链表尾部（最近使用）"""
        node.prev = self.tail.prev
        node.next = self.tail
        self.tail.prev.next = node
        self.tail.prev = node

    def get(self, key) -> int:
        if key not in self.cache:
            return -1
        node = self.cache[key]
        # 访问即移动：先摘除，再插到尾部
        self._remove(node)
        self._add_to_tail(node)
        return node.value

    def put(self, key, value) -> None:
        if key in self.cache:
            # 已存在：更新值并移动到尾部
            node = self.cache[key]
            node.value = value
            self._remove(node)
            self._add_to_tail(node)
            return

        if len(self.cache) >= self.capacity:
            # 淘汰头部（最久未使用）
            oldest = self.head.next
            self._remove(oldest)
            del self.cache[oldest.key]

        node = Node(key, value)
        self.cache[key] = node
        self._add_to_tail(node)


# ===== 测试 =====
cache = LRUCache(2)
cache.put(1, 1)
cache.put(2, 2)
print(cache.get(1))   # 1（1 变为最近使用）
cache.put(3, 3)       # 淘汰 key=2（最久未使用）
print(cache.get(2))   # -1
print(cache.get(3))   # 3
```

### 标准库一行版（回答后的加分展示）

```python
from functools import lru_cache

@lru_cache(maxsize=128)
def expensive(n: int) -> int:
    return n ** 2

print(expensive(10))  # 100
print(expensive.cache_info())  # CacheInfo(hits=0, misses=1, ...)
```

### 考察点

- 为什么是链表+哈希表？（O(1) 的查找与移动）
- 哨兵节点技巧：避免头尾操作的空指针判断
- `__slots__` 内存优化意识
- 是否知道 `functools.lru_cache` 这个现成实现

---

## 题目 3：异步队列（生产者-消费者）

### 题目描述

实现一个多生产者-多消费者的异步任务队列：生产者产生消息（商品 ID），多个消费者并发处理（如调用 embedding API）。要求消费失败时重试，且能优雅关闭。

### 参考答案

```python
import asyncio
import random
from typing import Awaitable, Callable

class AsyncTaskQueue:
    """
    异步生产者-消费者队列
    """

    def __init__(
        self,
        worker_func: Callable[..., Awaitable],
        num_workers: int = 3,
        max_retries: int = 2,
    ):
        self.worker_func = worker_func
        self.num_workers = num_workers
        self.max_retries = max_retries
        self.queue = asyncio.Queue()
        self.results = []
        self._workers = []

    async def _worker(self, worker_id: int) -> None:
        """单个消费者"""
        while True:
            task = await self.queue.get()
            try:
                for attempt in range(1, self.max_retries + 1):
                    try:
                        result = await self.worker_func(task)
                        self.results.append((task, result, "ok"))
                        break
                    except Exception as e:
                        print(f"[worker-{worker_id}] 任务 {task} 第 {attempt} 次失败: {e}")
                        if attempt == self.max_retries:
                            self.results.append((task, str(e), "failed"))
            finally:
                # 无论成功失败都要标记完成，保证 join 能返回
                self.queue.task_done()

    async def start(self) -> None:
        """启动所有消费者"""
        self._workers = [
            asyncio.create_task(self._worker(i))
            for i in range(self.num_workers)
        ]

    async def add_task(self, task) -> None:
        """生产者投递任务"""
        await self.queue.put(task)

    async def close(self) -> None:
        """优雅关闭：等待所有任务处理完，再取消 worker"""
        await self.queue.join()          # 等待队列清空
        for w in self._workers:
            w.cancel()                   # 取消空闲的 worker
        await asyncio.gather(*self._workers, return_exceptions=True)


# ===== 使用示例 =====

async def async_lookup(product_id: int) -> str:
    """模拟进价较高的任务（如调用大模型）"""
    await asyncio.sleep(random.uniform(0.05, 0.3))
    if random.random() < 0.2:
        raise ConnectionError("服务暂不可用")
    return f"产品 {product_id} 的处理结果"

async def main():
    queue = AsyncTaskQueue(async_lookup, num_workers=5, max_retries=2)
    await queue.start()

    # 生产者：投递 20 个任务
    for i in range(20):
        await queue.add_task(i + 1)

    await queue.close()

    ok = sum(1 for *_, st in queue.results if st == "ok")
    print(f"成功 {ok}/{len(queue.results)} 个任务")
    # 成功 18/20 个任务（随机失败被重试处理的统计）

if __name__ == "__main__":
    asyncio.run(main())
```

### 考察点

- `asyncio.Queue` 的 `get()` 阻塞语义（队列空时挂起，不放空转）
- `task_done()` + `join()` 的协作机制（优雅关闭的基础）
- 多个 worker 通过 `create_task` 并发运行
- 失败重试逻辑
- `gather(return_exceptions=True)` 处理取消异常

---

## 进阶自测（梯队题）

| 难度 | 题目 | 考点 |
|---|---|---|
| ★☆☆ | 手写 `map` / `filter` / `reduce` | 迭代器、函数式 |
| ★☆☆ | 手写上下文管理器（`@contextmanager`） | yield 与 with |
| ★★☆ | 实现 `defaultdict` 的替代品 | dict 魔法方法 `__missing__` |
| ★★☆ | 手写单例（`__new__` 版） | 构造流程 |
| ★★★ | 实现简单的异步 `gather`（不用标准库） | 熟悉 Task 与事件循环 |
| ★★★ | 实现线程安全的计数器（Lock 版） | 并发安全 |
| ★★★ | 用装饰器实现参数校验/重试/缓存三者组合 | 综合能力 |

---

## 答题技巧

1. **先讲思路再写码**：面试官更看重你的思考过程，先画状态/流程再说复杂度
2. **主动说明复杂度**：写完输出一句"get/put 都是 O(1)"，这是专业素养
3. **考虑边界**：空输入、重复 key、容量为 0、并发竞争
4. **写完跑测试**：至少口述 1-2 个用例的执行轨迹
5. **切忌死背**：理解链表指针的移动逻辑，而不是背代码

至此，Python 技术栈全部学完。最终检查清单：

- [ ] 能独立写出异步爬虫（asyncio + aiohttp）
- [ ] 能解释 GIL 并选择正确的并行方案
- [ ] 会用 pytest + mock 测试异步服务
- [ ] 能定位与修复内存泄漏
- [ ] 手写 LRU、装饰器、异步队列无障碍

恭喜完成 Python 技术栈！下一步建议进入 **05-fastapi** 或 **14-langchain**。