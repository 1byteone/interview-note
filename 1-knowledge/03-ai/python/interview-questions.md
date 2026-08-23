# Python 面试题大全

## 📚 知识体系

```
Python 基础
├── 数据类型（list/tuple/dict/set/str）
├── 可变 vs 不可变
├── 深浅拷贝
├── 装饰器
├── 生成器/迭代器
├── 上下文管理器
├── 面向对象（MRO/多继承）
└── 异常处理

Python 进阶
├── GIL 全局解释器锁
├── 多线程 vs 多进程 vs 协程
├── asyncio 异步编程
├── 元编程（__new__/__init__/metaclass）
├── 垃圾回收（引用计数/分代）
├── 函数式编程（lambda/map/filter）
└── 性能优化

Python 应用
├── FastAPI / Flask / Django
├── 爬虫（requests/Scrapy）
├── 数据分析（pandas/numpy）
├── AI（PyTorch/Transformers/LangChain）
└── 测试（pytest）
```

---

## 🎯 Level 1：基础题

### 1. Python 和 Java 的区别？
**答案**：

| 特性 | Python | Java |
|------|--------|------|
| 类型 | 动态类型 | 静态类型 |
| 编译 | 解释执行 | 编译+JIT |
| 性能 | 较慢 | 快 |
| 语法 | 简洁（缩进） | 冗长（大括号） |
| 并发 | 多进程/协程（GIL） | 多线程 |
| 生态 | AI/数据分析强 | 后端/中间件强 |

### 2. list 和 tuple 的区别？
**答案**：
- **list（列表）**：可变，可增删改
- **tuple（元组）**：不可变，可哈希（可做 dict 键）
- 性能：tuple 访问更快（固定结构）

---

## 🎯 Level 2：进阶题

### 3. 什么是装饰器？
**答案**：
装饰器是**在不修改原函数代码的前提下，为函数增加功能**的语法糖。

```python
import functools
import time

def timer(func):
    @functools.wraps(func)
    def wrapper(*args, **kwargs):
        start = time.time()
        result = func(*args, **kwargs)
        print(f"{func.__name__} 耗时: {time.time() - start:.4f}s")
        return result
    return wrapper

@timer
def slow_function():
    time.sleep(1)
    return "done"

slow_function()  # 输出: slow_function 耗时: 1.0000s
```

**应用场景**：日志记录、权限校验、缓存、性能监控。

### 4. GIL 是什么？为什么存在？
**答案**：
GIL（Global Interpreter Lock）是 CPython 的**全局解释器锁**，同一时刻只允许一个线程执行 Python 字节码。

**影响**：
- **多线程**：CPU 密集型多线程无法利用多核（串行）
- **IO 密集型**：多线程仍然有效（IO 释放 GIL）

**解决方案**：
1. **多进程**：`multiprocessing`（独立解释器）
2. **协程**：`asyncio`（单线程切换）
3. **JIT/语言实现**：PyPy / Jython（无 GIL）

---

## 🎯 Level 3：高级题

### 5. asyncio 的协程原理？
**答案**：

```python
import asyncio

async def fetch_data(name, delay):
    print(f"{name} 开始")
    await asyncio.sleep(delay)  # 挂起，释放控制权
    print(f"{name} 完成")
    return name

async def main():
    # 并发执行 3 个协程
    results = await asyncio.gather(
        fetch_data("任务A", 2),
        fetch_data("任务B", 1),
        fetch_data("任务C", 0.5)
    )
    print(results)

asyncio.run(main())
```

**原理**：
- 事件循环（Event Loop）调度
- `await` 挂起当前协程，让出控制权
- IO 完成通过**回调**恢复执行

### 6. 生成器和迭代器？
**答案**：
- **迭代器（Iterator）**：实现 `__iter__` + `__next__`，惰性取值
- **生成器（Generator）**：`yield` 函数，自动实现迭代器

```python
# 生成器：每次取一个，不占内存
def fibonacci():
    a, b = 0, 1
    while True:
        yield a  # 暂停并返回值
        a, b = b, a + b

gen = fibonacci()
print(next(gen))  # 0
print(next(gen))  # 1
print(next(gen))  # 1
```

---

## 📖 学习资源

### 推荐项目
- [Python 官方文档](https://docs.python.org/3/)
- [FastAPI 官方文档](https://fastapi.tiangolo.com/)
- [Python 面试题库（jackfrued/Python-100-Days）](https://github.com/jackfrued/Python-100-Days)

### 最佳实践
1. IO 密集型用 asyncio，CPU 密集型用 multiprocessing
2. 循环中序列化操作多用列表推导式
3. 大文件流式读取（with + yield）
4. 避免可变默认参数（`def f(x=[])` → 陷阱）