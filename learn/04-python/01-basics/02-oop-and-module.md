# 面向对象与模块化

> 👶 新手通道 | 预计阅读：35 分钟

Python 的面向对象比 Java 更灵活也更"不正经"——没有 private/protected 关键字，没有接口，但多继承、魔法方法、装饰器让 OOP 的表达力远超传统语言。

---

## 1. 类与对象

### 1.1 基础定义

```python
class User:
    # 类变量（所有实例共享）
    role = "普通用户"

    # 构造方法
    def __init__(self, name: str, age: int):
        self.name = name      # 实例变量
        self.age = age
        self._email = None    # 约定：单下划线表示"受保护"

    def greet(self) -> str:
        return f"{self.name}，你好！"

    # 属性访问器（替代 getter/setter）
    @property
    def email(self):
        return self._email

    @email.setter
    def email(self, value: str):
        if "@" not in value:
            raise ValueError("无效的邮箱地址")
        self._email = value


user = User("张三", 28)
user.email = "zhangsan@example.com"
print(user.greet())           # 张三，你好！
print(user.email)             # zhangsan@example.com
```

**与 Java 对比**：Python 没有 `private`，用 `_` 前缀表示"内部使用"（只是约定，外部仍可访问）。`@property` 比 Java 的 getter/setter 简洁得多。

### 1.2 继承与多态

```python
class Animal:
    def speak(self) -> str:
        return "..."

class Dog(Animal):
    def speak(self) -> str:
        return "汪汪！"

class Cat(Animal):
    def speak(self) -> str:
        return "喵喵～"

# 多态：同一个接口，不同行为
animals = [Dog(), Cat()]
for a in animals:
    print(a.speak())
# 汪汪！
# 喵喵～
```

### 1.3 多继承与 MRO

Python 支持多继承，通过 **C3 线性化算法**（MRO，Method Resolution Order）决定方法查找顺序：

```python
class A:
    def process(self): print("A")

class B(A):
    def process(self): print("B")

class C(A):
    def process(self): print("C")

class D(B, C):
    pass

d = D()
d.process()          # B（MRO: D → B → C → A）
print(D.__mro__)     # 查看解析顺序
```

**面试重点**：`__mro__` 遵循"深度优先，从左到右，子类优先"原则。菱形继承不会像 C++ 那样出现歧义。

---

## 2. 魔法方法

魔法方法（Magic Methods / Dunder Methods）是 Python 实现**操作符重载**和**内置函数支持**的方式：

```python
class Vector:
    def __init__(self, x, y):
        self.x = x
        self.y = y

    # 字符串表示
    def __str__(self) -> str:
        return f"Vector({self.x}, {self.y})"

    # 开发者调试表示
    def __repr__(self) -> str:
        return f"Vector({self.x!r}, {self.y!r})"

    # 运算符重载
    def __add__(self, other):
        return Vector(self.x + other.x, self.y + other.y)

    # 可调用对象
    def __call__(self):
        return (self.x ** 2 + self.y ** 2) ** 0.5

    # 长度（用于 bool 判断）
    def __bool__(self):
        return self.x != 0 or self.y != 0

v1 = Vector(3, 4)
v2 = Vector(1, 2)
print(v1 + v2)       # Vector(4, 6)
print(v1())          # 5.0（模长）
```

**高频面试题**：`__new__` vs `__init__` —— `__new__` 是真正的构造方法（创建实例），`__init__` 是初始化方法。单例模式用 `__new__` 实现。

---

## 3. 装饰器

装饰器是 Python 最优雅的特性之一——**在不修改函数代码的前提下，为其附加功能**：

```python
import time
from functools import wraps

def timer(func):
    """测量函数执行时间的装饰器"""
    @wraps(func)  # 保留原函数的元信息（name, doc 等）
    def wrapper(*args, **kwargs):
        start = time.time()
        result = func(*args, **kwargs)
        elapsed = time.time() - start
        print(f"{func.__name__} 耗时: {elapsed:.4f}s")
        return result
    return wrapper

@timer
def slow_function():
    time.sleep(0.5)
    return "完成"

print(slow_function())  # slow_function 耗时: 0.5001s
```

**装饰器本质**：`@timer` 等价于 `slow_function = timer(slow_function)`。装饰器接收一个函数，返回一个新函数。

### 带参数的装饰器

```python
def retry(max_attempts=3):
    def decorator(func):
        @wraps(func)
        def wrapper(*args, **kwargs):
            for i in range(max_attempts):
                try:
                    return func(*args, **kwargs)
                except Exception as e:
                    if i == max_attempts - 1:
                        raise
                    print(f"第 {i+1} 次失败，重试...")
            return wrapper
        return decorator

@retry(max_attempts=3)
def unstable_api():
    import random
    if random.random() < 0.7:
        raise ConnectionError("网络波动")
    return "成功"
```

---

## 4. 生成器与迭代器

### 4.1 生成器

用 `yield` 关键字创建惰性求值的序列，**内存友好**：

```python
def fibonacci(n):
    """生成前 n 个斐波那契数（惰性）"""
    a, b = 0, 1
    for _ in range(n):
        yield a
        a, b = b, a + b

for num in fibonacci(10):
    print(num)  # 0 1 1 2 3 5 8 13 21 34

# 生成器表达式
squares = (x**2 for x in range(1000000))
print(next(squares))  # 0
```

与列表推导式不同：`[x**2 for x in range(1000000)]` 一次性创建全部元素，`(x**2 for x in range(1000000))` 是惰性求值。

### 4.2 yield 与协程

```python
def coroutine():
    """接收数据的协程"""
    while True:
        received = yield
        print(f"收到: {received}")

c = coroutine()
next(c)          # 启动协程
c.send("你好")   # 收到: 你好
c.send("世界")   # 收到: 世界
```

---

## 5. 包管理

### 5.1 模块与包

```
my_project/
├── __init__.py      # 标识这是一个包（Python 3.3+ 可省略）
├── main.py
├── utils/
│   ├── __init__.py
│   ├── io_helper.py
│   └── db_helper.py
└── tests/
    └── test_utils.py
```

```python
# main.py
from utils.io_helper import read_file
from utils.db_helper import DatabaseClient
```

### 5.2 requirements.txt vs pyproject.toml

**requirements.txt**：简单直接，适合小项目或部署锁版本。

```txt
fastapi==0.104.0
uvicorn[standard]==0.24.0
pytest==7.4.3
```

**pyproject.toml**：现代 Python 项目标准（PEP 621），支持依赖分组、构建配置：

```toml
[project]
name = "my-project"
version = "0.1.0"
requires-python = ">=3.11"
dependencies = [
    "fastapi>=0.104.0",
    "uvicorn[standard]>=0.24.0",
]

[project.optional-dependencies]
dev = ["pytest>=7.4.3", "pytest-cov>=4.1.0"]
```

### 5.3 虚拟环境最佳实践

```bash
python -m venv .venv
source .venv/bin/activate
pip install -e ".[dev]"   # 开发模式安装（含 dev 依赖）
```

---

## 总结

| 概念 | 关键点 |
|---|---|
| 类与对象 | `@property` 替代 getter/setter，`_` 约定替代 private |
| 继承 | MRO 解决多继承冲突，C3 线性化算法 |
| 魔法方法 | `__str__`/`__repr__`/`__add__`/`__call__` 重载行为 |
| 装饰器 | 本质是函数替换，`@wraps` 保留元信息 |
| 生成器 | `yield` 惰性求值，内存友好的迭代方式 |
| 包管理 | `pyproject.toml` 是现代标准，`requirements.txt` 仍广泛使用 |

下一步：进入 [02-core/01-async-programming.md](../02-core/01-async-programming.md) 学习异步编程。