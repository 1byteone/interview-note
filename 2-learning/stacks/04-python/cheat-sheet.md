# Python 速查卡 📋

> 面试前30分钟扫一遍，一页纸覆盖核心考点

## 🏷️ 核心概念速记

| 概念 | 一句话解释 | 常见陷阱 |
|------|-----------|----------|
| GIL (全局解释器锁) | CPython 解释器中的互斥锁，保证同一时刻只能一个线程执行字节码 | GIL 不是 Python 语言特性，是 CPython 实现；I/O 密集受益，CPU 密集受罪 |
| async/await | 基于协程的异步编程，由事件循环调度 | async 函数必须 await 才能执行，直接调用返回 coroutine 对象 |
| 装饰器 | 高阶函数，接收函数返回增强函数 | 带参数装饰器要多一层，functools.wraps 保持元信息 |
| 生成器 (yield) | 懒加载迭代器，每次 yield 返回一个值，状态挂起 | yield 和 return 并存是语法糖(SendException)；yield from 简化委托 |
| 迭代器 vs 可迭代 | 有 __iter__ 的是可迭代，有 __next__ 的是迭代器 | for 循环先调 __iter__ 再 __next__，StopIteration 结束 |
| 列表推导式 | [x*2 for x in range(10) if x%2==0] 简洁高效 | 复杂逻辑别用推导式，可读性差；优先用生成器表达式省内存 |
| 类型注解 | 3.5+ 引入，def add(a: int, b: int) -> int: | 注解是提示，Python 不强制类型检查；mypy 可实现静态检查 |
| 元类 (metaclass) | 类的类，type 是默认元类，控制类创建行为 | 99% 场景用不到，面试常问但慎用，用装饰器/继承替代 |
| \_\_slots\_\_ | 限制实例属性，减少内存占用 | 继承时子类也要定义 \_\_slots\_\_ 才有效，否则被 \_\_dict\_\_ 覆盖 |
| with 语句 | 上下文管理器，__enter__ / __exit__ 自动资源管理 | 自己实现上下文管理器一定要正确处理异常 |
| 浅拷贝 vs 深拷贝 | copy.copy 只复制外层，copy.deepcopy 递归复制全部 | 嵌套可变对象浅拷贝后修改内层会影响原对象 |
| 猴子补丁 | 运行时替换类/模块的属性 | 改变全局行为，调试困难，小心在测试时用 |

## 🔧 常用命令/API

```python
# 装饰器模板（关键考点）
from functools import wraps

def log_execution(func):
    @wraps(func)  # 保留原函数元信息
    def wrapper(*args, **kwargs):
        print(f"Calling {func.__name__}")
        result = func(*args, **kwargs)
        print(f"Finished {func.__name__}")
        return result
    return wrapper

# 带参数装饰器
def repeat(times: int):
    def decorator(func):
        @wraps(func)
        def wrapper(*args, **kwargs):
            for _ in range(times):
                func(*args, **kwargs)
        return wrapper
    return decorator

@log_execution
@repeat(3)
def hello():
    print("Hello")
```

```python
# async/await 异步编程
import asyncio

async def fetch_data(url: str) -> dict:
    # 模拟 IO 操作
    await asyncio.sleep(1)
    return {"url": url, "data": "..."}

async def main():
    # 并发执行多个协程
    tasks = [fetch_data(f"https://api.example.com/{i}") for i in range(5)]
    results = await asyncio.gather(*tasks, return_exceptions=True)
    return results

# 运行
asyncio.run(main())
```

```python
# 列表推导式 vs 生成器表达式
squares = [x**2 for x in range(1000)]          # 列表，立即计算，占内存
squares_gen = (x**2 for x in range(1000))      # 生成器，惰性求值，省内存

# 字典/集合推导式
square_dict = {x: x**2 for x in range(5)}      # {0:0, 1:1, 2:4, 3:9, 4:16}
square_set = {x**2 for x in range(5)}          # {0, 1, 4, 9, 16}
```

```python
# 类型注解（Pydantic 风格）
from typing import Optional, List, Dict

class User:
    def __init__(self, name: str, age: int, tags: Optional[List[str]] = None) -> None:
        self.name = name
        self.age = age
        self.tags = tags or []

def get_user_by_id(uid: int) -> Optional[User]:
    return User("Alice", 30) if uid == 1 else None
```

```python
# 上下文管理器
from contextlib import contextmanager

@contextmanager
def managed_resource(name: str):
    print(f"Acquire {name}")
    try:
        yield name
    finally:
        print(f"Release {name}")

with managed_resource("db-conn") as conn:
    print(f"Using {conn}")
```

## 🎯 面试高频 TOP10

1. **Q: GIL 是什么？怎么绕过？** **A:** CPython 的全局锁，同时间只有一个线程执行字节码；绕过方案：多进程(multiprocessing)、C 扩展(ctypes/Cython)、异步(I/O 密集)。
2. **Q: 装饰器原理？** **A:** 语法糖 `@deco` 等价于 `func = deco(func)`，返回包装函数；@wraps 保持元信息；带参装饰器多一层闭包。
3. **Q: 元类什么场景用？** **A:** ORM(Model 声明)、单例实现、属性校验；日常用类装饰器/继承替代，元类会在创建类时拦截。
4. **Q: async/await 和线程区别？** **A:** 协程单线程内协作式调度，切换无 OS 开销；线程是抢占式，有 OS 上下文切换；I/O 密集用协程，CPU 密集用多进程。
5. **Q: Python 的 typing 模块有什么用？** **A:** 类型注解，mypy 静态检查，IDE 自动补全；但运行时无强制，Pydantic 在运行时做校验。
6. **Q: 生成器和迭代器区别？** **A:** 生成器是特殊的迭代器(yield 语法糖)；迭代器通过 __iter__+__next__ 实现，生成器自动维护状态。
7. **Q: \_\_new\_\_ 和 \_\_init\_\_ 区别？** **A:** __new__(cls) 创建实例(返回对象)，__init__(self) 初始化实例；前者静态方法，后者实例方法；单例模式在 __new__ 实现。
8. **Q: 深拷贝 vs 浅拷贝？** **A:** 浅拷贝只复制外层引用，内层可变对象共享；深拷贝递归复制全部，完全独立；copy.deepcopy 用 memo 字典防循环引用。
9. **Q: Python 是解释型还是编译型？** **A:** 先编译为字节码(.pyc)，再在 CPython 虚拟机解释执行；所以是"编译+解释"混合型。
10. **Q: 内存管理和垃圾回收？** **A:** 引用计数为主(即时回收)+标记清除(循环引用)+分代回收；gc 模块手动触发，weakref 弱引用不增加计数。

## ⚠️ 常见坑 & 最佳实践

| ❌ 坑 | ✅ 正确做法 |
|-------|------------|
| 函数默认参数用可变对象 | 默认用 None，内部判断赋值：`def f(lst=None): lst = lst or []` |
| 循环里用 lambda 捕获变量 | 用默认参数绑定：`lambda x=i: x` 或改用列表推导式 |
| 大量用列表推导式产生巨量内存 | 改用生成器表达式 `(x for x in range(1e7))` |
| 多线程做 CPU 密集计算 | 用 multiprocessing.Pool 或异步+多进程组合 |
| except 裸捕获所有异常 | 捕获具体异常，至少 `except Exception as e`，避免吞 KeyboardInterrupt |
| 忘记关闭文件/连接 | 用 with 语句自动管理上下文 |
| 循环里 import 模块 | 模块导入放文件头部，一次性加载 |
| 用 is 比较字符串 | is 是比较对象身份，== 比较值；字符串驻留只对短字符串有效 |
| 并发修改遍历中的列表 | 遍历副本：`for item in lst[:]:` 或用列表推导式重建 |

## 📐 架构设计要点

- **项目结构**：src/ 根目录，tests/ 同级，pyproject.toml 管理依赖，poetry/uv 做包管理。
- **异步优先**：网络 IO 密集场景用 asyncio + aiohttp/httpx，避免同步阻塞事件循环。
- **类型检查**：mypy 严格模式，CI 中集成，减少运行时类型错误。
- **测试策略**：pytest + pytest-asyncio + mock 测试异步，覆盖率 80%+。
- **性能优化**：CPython 热点用 C 扩展、Cython 编译或 PyPy 运行，先 profile 再优化(cProfile)。

## 🔗 关联技术

- **FastAPI**：基于 async/await 的 Web 框架，Pydantic 做校验，类型注解驱动。
- **LangChain**：AI 编排框架，Python 是主力语言，大量使用异步和装饰器。
- **NumPy/Pandas**：底层用 C 绕过 GIL，处理大数据集的首选。
- **Docker**：Python 镜像基础(Cpython/PyPy/multistage build) 选择。
- **Rust via PyO3**：Python 性能瓶颈可用 Rust 写扩展模块(pyo3/maturin)。