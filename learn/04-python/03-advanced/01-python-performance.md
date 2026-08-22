# Python 性能优化

> 🎯 进阶 | 预计阅读：40 分钟

Python 慢吗？**慢，但大多数时候你感受不到**。真正的瓶颈往往不是语言本身，而是错误的姿势。本篇从"如何发现瓶颈"开始，到"如何绕过 GIL"，再到"如何加速计算密集型代码"。

---

## 1. 性能分析（Profiling）

### 1.1 cProfile（内置工具）

不看数据就优化，等于盲人摸象：

```python
# 方法一：命令行
# python -m cProfile -o profile.out my_script.py
python -m cProfile -s cumulative my_script.py

# 方法二：代码内嵌
import cProfile
import pstats

def main():
    # ... 业务逻辑 ...
    pass

if __name__ == "__main__":
    profiler = cProfile.Profile()
    profiler.runcall(main)
    profiler.dump_stats("profile.out")

    # 分析结果
    stats = pstats.Stats("profile.out")
    stats.sort_stats("cumtime")  # 按累计耗时排序
    stats.print_stats(20)        # 显示前 20 行
```

输出解读：

```
ncalls  tottime  percall  cumtime  percall  filename:lineno(function)
   100    0.002    0.000    0.500    0.005  data_processing.py:42(process_row)
```

- **ncalls**：调用次数
- **tottime**：函数自身耗时（不含子调用）
- **cumtime**：总耗时（含子调用）
- **percall**：平均每次耗时

### 1.2 py-spy（采样分析器）

适合生产环境——**不需要修改代码，不需要停顿程序**：

```bash
pip install py-spy

# 分析正在运行的进程
py-spy top --pid 12345

# 生成火焰图
py-spy record -o flame.svg --pid 12345 --duration 30
```

### 1.3 时间装饰器（快速定位）

```python
import time
from functools import wraps

def profile(func):
    @wraps(func)
    def wrapper(*args, **kwargs):
        start = time.perf_counter()
        result = func(*args, **kwargs)
        elapsed = time.perf_counter() - start
        print(f"[PERF] {func.__name__}: {elapsed:.4f}s")
        return result
    return wrapper

@profile
def slow_function():
    # 怀疑这里有性能问题
    pass
```

---

## 2. 多进程 vs 多线程 vs 协程

```
┌──────────────┬───────────────┬───────────────┬───────────────┐
│              │    多进程      │    多线程      │    协程       │
├──────────────┼───────────────┼───────────────┼───────────────┤
│ 适用场景     │ CPU 密集型     │ I/O 密集型     │ 高并发 I/O    │
│ 内存开销     │ 高（独立进程）  │ 中（共享内存）  │ 低（单线程）   │
│ 数据共享     │ 难（需 IPC）   │ 易（锁冲突）    │ 易（无需锁）   │
│ Python 支持  │ multiprocessing│ threading     │ asyncio       │
│ 受 GIL 影响  │ 否            │ 是            │ 否（单线程）   │
└──────────────┴───────────────┴───────────────┴───────────────┘
```

### 选择原则

```python
# 1. CPU 密集型 → 多进程
# 场景：图像处理、数值计算、大文件压缩
from multiprocessing import Pool

def cpu_intensive(n):
    return sum(i * i for i in range(n))

with Pool(4) as pool:
    results = pool.map(cpu_intensive, [10000000] * 4)

# 2. 高并发 I/O → 协程
# 场景：大量 API 调用、数据库查询、网络爬虫
import asyncio

async def io_intensive():
    await asyncio.sleep(1)  # 模拟 I/O
    return "done"

# 3. 少量 I/O → 多线程（简化 API）
# 场景：混合计算与 I/O，已有同步代码库
from concurrent.futures import ThreadPoolExecutor

with ThreadPoolExecutor(max_workers=4) as executor:
    futures = [executor.submit(some_io_func, url) for url in urls]
    results = [f.result() for f in futures]
```

---

## 3. GIL 原理与绕过

### 3.1 GIL 是什么

**GIL（Global Interpreter Lock，全局解释器锁）** 是 CPython 的机制：**同一时刻只能有一个线程执行 Python 字节码**。

```python
import threading
import time

counter = 0

def increment():
    global counter
    for _ in range(1000000):
        counter += 1  # 即使有 GIL，这也不是原子的！

threads = [threading.Thread(target=increment) for _ in range(10)]
for t in threads:
    t.start()
for t in threads:
    t.join()

print(counter)  # 结果 < 10000000！多线程争抢导致数据竞争
```

### 3.2 为什么要有 GIL

- 简化 CPython 的内存管理（引用计数不需要加锁）
- 保证 C 扩展库的线程安全
- 单线程性能受益（不需要频繁加锁解锁）

### 3.3 如何绕过 GIL

```python
# 方式一：多进程（最推荐）
from multiprocessing import Process, Queue

def worker(q: Queue, data):
    result = expensive_cpu_work(data)
    q.put(result)

# 方式二：C 扩展（如 NumPy）
# NumPy 的矩阵运算在 C 层释放 GIL
import numpy as np
# 以下操作不受 GIL 限制
arr = np.random.rand(1000, 1000)
result = np.dot(arr, arr.T)

# 方式三：协程 + 异步 I/O
# 虽然单线程，但 I/O 复用避免了 GIL 问题
```

**Python 3.13 的 free-threaded 模式**：Python 3.13 开始支持无 GIL 构建（`--disable-gil`），但仍是实验性特性。生产环境仍以 GIL 为主。

---

## 4. Cython / Numba 加速

### 4.1 Numba：JIT 编译

Numba 是**零成本投入**的加速方案——只需加一个装饰器：

```python
from numba import jit
import time

# 纯 Python 版本
def sum_python(n: int) -> float:
    total = 0.0
    for i in range(n):
        total += i ** 0.5
    return total

# JIT 编译版本
@jit(nopython=True)
def sum_numba(n: int) -> float:
    total = 0.0
    for i in range(n):
        total += i ** 0.5
    return total

# 耗时对比
n = 10_000_000
start = time.time()
sum_python(n)
print(f"Python: {time.time() - start:.2f}s")  # ~2.5s

start = time.time()
sum_numba(n)
print(f"Numba:  {time.time() - start:.2f}s")  # ~0.1s（首次含编译）
```

### 4.2 Cython：编译为 C 扩展

适合需要精细控制或与 C 库交互的场景：

```python
# cython_example.pyx
def cython_sum(int n):
    cdef double total = 0.0
    cdef int i
    for i in range(n):
        total += i ** 0.5
    return total
```

在 `pyproject.toml` 中配置：

```toml
[build-system]
requires = ["setuptools", "cython"]
```

---

## 5. 内存优化

### 5.1 常见内存泄漏

```python
# 1. 循环引用（GC 会处理，但延迟回收）
class Node:
    def __init__(self):
        self.ref = None

a, b = Node(), Node()
a.ref = b
b.ref = a  # 循环引用

# 2. 全局变量持有大型对象
CACHE = {}  # 慎用全局缓存，注意清理

# 3. 闭包捕获大对象
def create_processor():
    large_data = load_big_file()  # 被闭包长期持有
    def process(item):
        return large_data[item]
    return process
```

### 5.2 内存分析工具

```python
# tracemalloc（内置）
import tracemalloc

tracemalloc.start()
# 运行业务代码
snapshot = tracemalloc.take_snapshot()
top_stats = snapshot.statistics("lineno")

for stat in top_stats[:10]:
    print(stat)  # 显示内存占用最大的代码位置

# objgraph（第三方）
import objgraph
objgraph.show_most_common_types()  # 显示最常驻的对象类型
objgraph.show_growth()             # 显示增长趋势
```

### 5.3 内存友好写法

```python
# 坏：一次性加载全部
with open("large_file.txt") as f:
    lines = f.readlines()  # 全部读入内存

# 好：惰性逐行处理
with open("large_file.txt") as f:
    for line in f:  # 迭代器，每次只读一行
        process(line)

# 坏：大列表推导式
data = [process(x) for x in huge_iterable]

# 好：生成器
data = (process(x) for x in huge_iterable)

# 使用 __slots__ 减少实例内存开销
class Point:
    __slots__ = ("x", "y")  # 禁止 __dict__，每个实例节省 ~32 字节
    def __init__(self, x, y):
        self.x = x
        self.y = y
```

---

## 总结

| 问题 | 工具 / 方案 |
|---|---|
| 性能瓶颈定位 | cProfile → py-spy（生产） → 火焰图 |
| CPU 密集型 | 多进程（multiprocessing）或 Numba JIT |
| 高并发 I/O | 协程（asyncio） |
| 数据竞争 | 多进程（完全隔离）或锁（threading.Lock） |
| 数值计算 | NumPy（释放 GIL）或 Numba |
| 内存泄漏 | tracemalloc / objgraph |
| 大文件处理 | 迭代器 / 生成器代替一次性加载 |

下一步：进入 [02-python-for-ai.md](02-python-for-ai.md) 学习 Python 在 AI 生态中的角色。