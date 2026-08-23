# Python 面试速记版

> 🎯 面试冲刺 | 5 分钟快速回顾

## 一、语法要点

### 可变与不可变

| 类型 | 是否可变 | 说明 |
|---|---|---|
| int, float, str, tuple | 不可变 | 修改会创建新对象 |
| list, dict, set | 可变 | 原地修改，引用不变 |

### 深拷贝 vs 浅拷贝

```python
import copy
a = [[1, 2], [3, 4]]
b = copy.copy(a)        # 浅拷贝：子列表是引用
c = copy.deepcopy(a)    # 深拷贝：全部递归复制
b[0][0] = 99
print(a)  # [[99, 2], [3, 4]] 浅拷贝影响了原对象
```

### 默认参数陷阱

```python
def append_to(item, target=[]):  # 默认参数在定义时求值
    target.append(item)
    return target

print(append_to(1))  # [1]
print(append_to(2))  # [1, 2] ！同一个列表
```

**修复**：`def append_to(item, target=None): target = target or []`

### 列表推导式 vs 生成器表达式

```python
# 列表推导式：一次性生成全部
squares = [x**2 for x in range(1000)]

# 生成器表达式：惰性求值
squares = (x**2 for x in range(1000))
```

---

## 二、常用库

| 库 | 用途 | 面试常见问题 |
|---|---|---|
| requests | HTTP 请求 | Session 与连接池 |
| aiohttp | 异步 HTTP | 与 requests 的对比 |
| pydantic | 数据校验 | v1 vs v2 区别 |
| pytest | 测试 | fixture / conftest / mock |
| asyncio | 异步 I/O | 事件循环 / gather vs wait |
| multiprocessing | 多进程 | Pool / Queue / 与 threading 对比 |
| numpy | 数值计算 | 向量化 vs 循环 |
| pandas | 数据处理 | DataFrame / groupby / merge |

---

## 三、面试考点

### 高频题

1. **`is` 与 `==` 的区别**：`is` 比较内存地址，`==` 比较值。`256 is 256` 为 True（小整数缓存），`257 is 257` 可能为 False
2. **`__new__` vs `__init__`**：`__new__` 创建实例（返回对象），`__init__` 初始化实例
3. **装饰器执行顺序**：多个装饰器从下往上应用，从上往下执行
4. **`@staticmethod` vs `@classmethod`**：staticmethod 无 self/cls 参数，classmethod 接收 cls
5. **GIL**：全局解释器锁，同一时刻只有一个线程执行 Python 字节码

### 手写代码常考

- 手写装饰器（计时器、重试、缓存）
- 手写 LRU Cache（`@functools.lru_cache` 原理）
- 手写单例模式（`__new__` 实现或模块单例）
- 手写上下文管理器（`__enter__` / `__exit__` 或 `@contextmanager`）
- 手写异步迭代器（`__aiter__` / `__anext__`）

---

## 四、Python 3.11+ 新特性

| 特性 | 版本 | 说明 |
|---|---|---|
| `match` 语句 | 3.10 | 模式匹配，类似 switch-case |
| `Self` 类型 | 3.11 | `from typing import Self` 返回自身类型 |
| `ExceptionGroup` | 3.11 | 同时抛出多个异常 |
| 异常注释 `except*` | 3.11 | 匹配 ExceptionGroup 中的子异常 |
| 更快的解释器 | 3.11 | CPython 3.11 比 3.10 快 10-60% |

---

## 五、常见易错

```python
# 1. 链式比较
print(1 < 2 < 3)   # True（等价于 1 < 2 and 2 < 3）

# 2. 布尔值是 int 的子类
print(True + True)  # 2

# 3. 负索引
print([1, 2, 3][-1])  # 3

# 4. 切片不抛异常
print([1, 2, 3][100:200])  # [] 而不是 IndexError

# 5. 闭包中循环变量捕获
funcs = [lambda: i for i in range(5)]
print([f() for f in funcs])  # [4, 4, 4, 4, 4]
# 修复：lambda i=i: i
```