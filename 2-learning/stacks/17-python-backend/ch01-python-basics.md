# 第一章：Python 语言基础（P0 精通）

> 📖 **参考资料**：[Developer Roadmaps - Python](https://roadmap.sh/python) | [Python 官方文档](https://docs.python.org/3/) | [Fluent Python](https://www.oreilly.com/library/view/fluent-python-2nd/9781492056348/)

---

## 1.1 基础数据类型与控制流

```python
# 数据类型
name: str = "Python"
age: int = 30
salary: float = 15000.50
is_active: bool = True

# 集合类型
users: list[dict[str, any]] = [{"name": "Alice", "age": 30}]
config: dict[str, str] = {"host": "localhost", "port": "5432"}
unique_ids: set[int] = {1, 2, 3}
record: tuple[str, int] = ("Alice", 30)

# 列表推导式
active_users = [u for u in users if u.get("is_active")]
user_names = {u["name"]: u for u in users}

# f-string
log_msg = f"User {name} logged in at {age} years old"
```

## 1.2 函数进阶

```python
# *args / **kwargs
def create_user(username: str, *roles: str, **metadata: str) -> dict:
    return {
        "username": username,
        "roles": list(roles),
        "metadata": metadata,
    }

create_user("alice", "admin", "editor", department="engineering")

# 类型提示 (Type Hint)
def get_user(user_id: int) -> dict[str, any] | None:
    ...

def process_items(items: list[str], callback: callable[[str], str]) -> list[str]:
    return [callback(item) for item in items]

# 仅关键字参数
def register(*, username: str, email: str, password: str) -> dict:
    return {"username": username, "email": email}
```

## 1.3 面向对象编程（OOP）

```python
from abc import ABC, abstractmethod
from dataclasses import dataclass, field

# 抽象类
class BaseRepository(ABC):
    @abstractmethod
    async def get(self, id: int) -> dict | None:
        ...

    @abstractmethod
    async def create(self, data: dict) -> dict:
        ...

# dataclass
@dataclass
class UserCreate:
    username: str
    email: str
    age: int | None = None

# Protocol（结构化子类型）
class CacheBackend(Protocol):
    async def get(self, key: str) -> str | None: ...
    async def set(self, key: str, value: str, ttl: int) -> None: ...

class RedisCache:
    async def get(self, key: str) -> str | None: ...
    async def set(self, key: str, value: str, ttl: int) -> None: ...

# RedisCache 隐式满足 CacheBackend，无需显式继承
def save_to_cache(cache: CacheBackend, key: str, value: str):
    cache.set(key, value, ttl=3600)
```

## 1.4 高级 Python 特性

```python
# 装饰器 (Decorator)
def timer(func):
    import time
    async def wrapper(*args, **kwargs):
        start = time.perf_counter()
        result = await func(*args, **kwargs)
        elapsed = time.perf_counter() - start
        print(f"{func.__name__} took {elapsed:.4f}s")
        return result
    return wrapper

# 生成器 / 迭代器
def paginate_items(total: int, page_size: int = 100):
    for offset in range(0, total, page_size):
        yield offset, page_size

# Context Manager
class DatabaseConnection:
    async def __aenter__(self):
        self.conn = await create_connection()
        return self.conn

    async def __aexit__(self, exc_type, exc_val, exc_tb):
        await self.conn.close()

# closure
def create_validator(pattern: str):
    import re
    def validate(value: str) -> bool:
        return bool(re.match(pattern, value))
    return validate

email_validator = create_validator(r"^[\w.-]+@[\w.-]+\.\w+$")
```

## 1.5 Python 类型系统（重点）

```python
from typing import Optional, Union, Literal, TypedDict, TypeVar, Generic

# Optional / Union
def find_user(user_id: int) -> Optional[dict]:
    return None  # 或返回 dict

# Literal（限定取值范围）
HTTPMethod = Literal["GET", "POST", "PUT", "DELETE"]

# TypedDict（精确类型字典）
class UserResponse(TypedDict):
    id: int
    username: str
    email: str
    is_active: bool

# TypeVar / Generic
T = TypeVar("T")

class Result(Generic[T]):
    def __init__(self, data: T | None = None, error: str | None = None):
        self.data = data
        self.error = error

user_result: Result[UserResponse] = Result(
    data={"id": 1, "username": "alice", "email": "a@b.com", "is_active": True}
)

# Python 3.12+ 泛型语法
def first[T](items: list[T]) -> T | None:
    return items[0] if items else None
```

## 1.6 异步基础（asyncio）

```python
import asyncio

# Coroutine / Task / Event Loop
async def fetch_user(user_id: int) -> dict:
    await asyncio.sleep(0.1)  # 模拟 IO
    return {"id": user_id, "name": f"user_{user_id}"}

async def main():
    # 并发执行多个协程
    tasks = [fetch_user(i) for i in range(10)]
    results = await asyncio.gather(*tasks)
    print(results)

asyncio.run(main())

# IO-bound vs CPU-bound 理解
# IO-bound: HTTP 请求、数据库查询、文件读写 → asyncio
# CPU-bound: 计算密集型 → multiprocessing / ProcessPoolExecutor
```

## 1.7 文件 IO / JSON / 环境变量

```python
import json
import os
from pathlib import Path

# 文件读写
config_path = Path("config.json")
data = json.loads(config_path.read_text(encoding="utf-8"))
config_path.write_text(json.dumps(data, indent=2), encoding="utf-8")

# 环境变量
db_url = os.getenv("DATABASE_URL", "sqlite:///./dev.db")
debug = os.getenv("DEBUG", "false").lower() == "true"
```

---

## 必读资源

- [Python's asyncio: A Hands-On Walkthrough (Real Python)](https://realpython.com/async-io-python/)
- [Mastering Python's Asyncio: Writing High Performance Code](https://levelup.gitconnected.com/mastering-pythons-asyncio-the-unspoken-secrets-of-writing-high-performance-code-3d7483518894)
- [asyncio 官方文档](https://docs.python.org/3/library/asyncio.html)
- [Developer Roadmap - Python](https://roadmap.sh/python)
