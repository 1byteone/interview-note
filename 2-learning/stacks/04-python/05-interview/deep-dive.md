# Python 深挖题

> 🎯 面试冲刺 | 深入原理

本篇文章覆盖 Python 面试中**最容易被追问到底**的三个主题：GIL 原理、装饰器实现原理、元类。掌握这些，你就从"会用"升级为"懂原理"。

---

## 1. GIL 原理

### 1.1 什么是 GIL

GIL（Global Interpreter Lock）是 CPython 解释器的一个互斥锁，保证**同一时刻只有一个线程执行 Python 字节码**。

### 1.2 为什么需要 GIL

**核心原因：CPython 的引用计数内存管理不是线程安全的。**

```python
# CPython 对每个对象维护一个引用计数
# obj->ob_refcnt += 1
# 如果两个线程同时操作引用计数，可能发生:
# 1. 计数减少到 0 → 对象被释放
# 2. 另一个线程还在用这个对象 → 崩溃/内存错误
```

GIL 方案：让整个解释器加锁，牺牲多核并行，换取内存管理的简单与安全。**这是设计取舍，不是缺陷**。

### 1.3 GIL 的调度机制

- **字节码切换**：每执行一定数量的字节码（默认 100 个 tick），检查是否切换线程
- **系统级切换**：线程被阻塞（I/O 等待）时主动释放 GIL
- **信号机制**：线程持有 GIL 超过 5ms 会被强制切换

```python
import sys
print(sys.getswitchinterval())  # 0.005，即 5ms 切换间隔
```

### 1.4 GIL 的影响

| 场景 | GIL 影响 |
|---|---|
| CPU 密集型 | 严重。多线程无法利用多核，性能甚至更差 |
| I/O 密集型 | 基本无影响。等待 I/O 时让出 GIL |
| C 扩展（NumPy） | 无影响。C 层可显式释放 GIL |
| 单线程 | 无影响 |

### 1.5 面试追问

**Q：Python 3.12 的 GIL 还在吗？**
A：还在。3.13 引入 free-threaded（无 GIL）构建作为实验特性，但默认（官方二进制）仍带 GIL，且生态兼容性问题未完全解决。

**Q：多线程中 `counter += 1` 安全吗？**
A：不安全。`+=` 包含读、加、写三个步骤，字节码层面可能被切换。

**Q：怎么让 `counter += 1` 线程安全？**
A：使用 `threading.Lock`，或 `queue.Queue`，或原子操作如 `itertools.count`、`multiprocessing.Value`。

---

## 2. 装饰器实现原理

### 2.1 装饰器的本质

装饰器**只是语法糖**，本质是函数调用：

```python
@decorator
def func():
    pass

# 等价于
def func():
    pass
func = decorator(func)
```

### 2.2 无参装饰器的完整剖析

```python
def decorator(func):
    # ① 接收一个函数
    def wrapper(*args, **kwargs):
        # ③ 包装逻辑
        print("调用前")
        result = func(*args, **kwargs)
        print("调用后")
        return result
    return wrapper  # ② 返回一个新函数
```

- Python 解析 `@decorator` 时，将 `decorator` 当作**可调用对象**调用，传入被装饰函数
- 必须返回一个**可调用对象**（通常是一个函数或类）
- 装饰器在**模块导入时执行一次**，不是在调用函数时执行

### 2.3 带参装饰器：三层嵌套

```python
def with_args(arg):
    # 第一层：接收装饰器参数
    def decorator(func):
        # 第二层：接收被装饰函数
        def wrapper(*args, **kwargs):
            # 第三层：实际包装
            print(f"参数: {arg}")
            return func(*args, **kwargs)
        return wrapper
    return decorator

@with_args("hello")
def f():
    pass

# 等价于
# f = with_args("hello")(f)
```

**口诀**：无参装饰器两层（外收函数、内收参数），带参装饰器三层（外收参数、中收函数、内收调用参数）。

### 2.4 functools.wraps 为什么必须加

```python
import functools

def decorator(func):
    @functools.wraps(func)  # 关键
    def wrapper(*args, **kwargs):
        return func(*args, **kwargs)
    return wrapper

@decorator
def f():
    """我是 f"""
    pass

print(f.__name__)  # 没加 wraps: "wrapper"；加了 wraps: "f"
print(f.__doc__)   # 没加 wraps: None；加了 wraps: "我是 f"
```

`functools.wraps` 通过 `__name__`、`__doc__`、`__wrapped__` 等属性复制，保证调试、文档、序列化工具正常工作。

### 2.5 装饰器进阶变体

```python
# 类装饰器（实现 __call__）
class CountCalls:
    def __init__(self, func):
        self.func = func
        self.count = 0
    def __call__(self, *args, **kwargs):
        self.count += 1
        print(f"第 {self.count} 次调用")
        return self.func(*args, **kwargs)

@CountCalls
def hello():
    print("hi")

# 装饰器实现单例
def singleton(cls):
    instance = None
    def get_instance(*args, **kwargs):
        nonlocal instance
        if instance is None:
            instance = cls(*args, **kwargs)
        return instance
    return get_instance

@singleton
class Config:
    pass
```

---

## 3. 元类

### 3.1 什么是元类

**元类（Metaclass）是创建类的类。类是创建对象的模板，元类是创建类的模板。**

```
对象 ← 类的实例（type(obj) 返回类）
类   ← 元类的实例（type(cls) 返回元类，通常是 type）
```

```python
class Dog:
    pass

dog = Dog()
print(type(dog))   # <class '__main__.Dog'>  对象由类创建
print(type(Dog))   # <class 'type'>          类由 type 元类创建
```

### 3.2 type 的两副面孔

```python
# 1. 返回类型
print(type(42))  # <class 'int'>

# 2. 动态创建类（type(name, bases, namespace)）
def speak(self):
    return "汪汪"

Dog = type("Dog", (object,), {"speak": speak})
d = Dog()
print(d.speak())  # 汪汪
```

### 3.3 自定义元类

```python
class SingletonMeta(type):
    """单例元类：所有使用它的类都会成为单例"""
    _instances = {}

    def __call__(cls, *args, **kwargs):
        # 拦截类的实例化过程
        if cls not in cls._instances:
            cls._instances[cls] = super().__call__(*args, **kwargs)
        return cls._instances[cls]

class Config(metaclass=SingletonMeta):
    def __init__(self):
        self.debug = False

c1 = Config()
c2 = Config()
print(c1 is c2)  # True 单例生效
```

### 3.4 元类的创建时机

- Python 在遇到 `class` 语句时，调用 `type(name, bases, namespace)`（或其子类）
- `__new__` 在类创建前执行（还未生成类对象）
- `__init__` 在类创建后执行

```python
class TraceMeta(type):
    def __new__(mcs, name, bases, namespace):
        print(f"创建类: {name}")
        # TODO: 可以在此批量添加方法、校验命名、注册类
        return super().__new__(mcs, name, bases, namespace)

    def __init__(cls, name, bases, namespace):
        print(f"初始化类: {name}")
        super().__init__(name, bases, namespace)

class MyClass(metaclass=TraceMeta):
    pass
# 输出:
# 创建类: MyClass
# 初始化类: MyClass
```

### 3.5 元类的实际应用

1. **单例模式**（如上）
2. **ORM**：SQLAlchemy / Django 用元类把类属性转换为数据库字段
3. **接口注册**：自动发现子类并注册到注册表
4. **API 框架**：FastAPI 用装饰器+元类做路由注册
5. **pydantic**：用元类生成字段校验逻辑

### 3.6 面试常问：什么时候用元类

**黄金法则：当你需要"在类创建时自动修改类"时**。大多数场景用装饰器或类装饰器就够了，元类是最后的手段——"如果没有明确的理由，就不要用元类"（The Zen of Python）。

**参考框架实现**（SQLAlchemy ORM）：

```python
class ModelMeta(type):
    def __new__(mcs, name, bases, namespace):
        # 收集类属性中的 Column 对象
        columns = {
            k: v for k, v in namespace.items()
            if hasattr(v, "is_column")
        }
        namespace["_columns"] = columns
        return super().__new__(mcs, name, bases, namespace)

class Model(metaclass=ModelMeta):
    pass

class User(Model):
    # 这是一个"列"，会被元类收集
    is_column = True
    name = "用户表"
```

---

## 总结

| 主题 | 核心结论 |
|---|---|
| GIL | 引用计数内存管理的设计取舍；CPU 密集用多进程，I/O 密集用协程 |
| 装饰器 | 语法糖，本质是 `func = decorator(func)`；掌握三层嵌套与 @wraps |
| 元类 | 创建类的类；`type(name, bases, ns)` 动态建类；适合框架级场景 |

接下来：查看 [scenario.md](scenario.md) 场景题与 [coding.md](coding.md) 代码题。