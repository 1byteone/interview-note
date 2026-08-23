# 类型注解与测试

> 👶→🎯 核心进阶 | 预计阅读：40 分钟

> Python 是动态类型语言，但**现代 Python 工程化必须依赖类型注解**。类型注解 + Pydantic 数据校验 + pytest 测试，是 AI 服务稳定运行的三大保障。

---

## 1. 类型注解（typing 模块）

### 1.1 基础类型注解

```python
# 变量注解
age: int = 28
name: str = "张三"
price: float = 199.9
active: bool = True

# 函数注解
def calculate(price: float, quantity: int = 1) -> float:
    """返回总价"""
    return price * quantity
```

### 1.2 泛型容器

```python
from typing import List, Dict, Tuple, Set, Optional, Union, Any

names: List[str] = ["张三", "李四"]
scores: Dict[str, int] = {"数学": 95, "语文": 88}
point: Tuple[float, float] = (10.0, 20.0)
tags: Set[str] = {"python", "ai"}

# Optional = 可能为 None
def find_user(user_id: int) -> Optional[str]:
    db = {1: "张三", 2: "李四"}
    return db.get(user_id)  # 可能返回 None

# Union = 多种类型之一
def parse(value: Union[int, str]) -> int:
    return int(value)

# Any = 任意类型
def log(msg: Any) -> None:
    print(msg)
```

### 1.3 高级类型

```python
from typing import Callable, TypeVar, Generic, Protocol, Literal

# Callable：函数作为参数
def apply(func: Callable[[int, int], int], a: int, b: int) -> int:
    return func(a, b)

# TypeVar：泛型
T = TypeVar("T")
def first(items: List[T]) -> T:
    return items[0]

# Literal：字面量约束
def set_status(status: Literal["active", "inactive", "banned"]) -> None:
    pass

# Protocol：结构化类型（鸭子类型）——"只要会叫，就是动物"
class Speaker(Protocol):
    def speak(self) -> str: ...

def make_sound(s: Speaker) -> str:
    return s.speak()
```

### 1.4 类型检查工具

```bash
pip install mypy

mypy src/
# 静态检查类型错误，CI 中作为强制门禁
```

---

## 2. Pydantic 数据校验

Pydantic 是 FastAPI 与 LangChain 的底层依赖，用于**运行时数据校验与数据转换**：

```python
from datetime import datetime
from typing import Optional
from pydantic import BaseModel, Field, EmailStr, ValidationError

class User(BaseModel):
    id: int
    name: str = Field(min_length=2, max_length=50)
    email: EmailStr
    age: int = Field(ge=0, le=150)
    tags: list[str] = []
    created_at: datetime = Field(default_factory=datetime.now)

# 合法数据
user = User(
    id=1,
    name="张三",
    email="zhangsan@example.com",
    age=28,
    tags=["python"],
)
print(user.model_dump())

# 非法数据自动校验失败
try:
    User(id=2, name="A", email="invalid-email", age=200)
except ValidationError as e:
    print(e.errors())  # 返回详细的校验错误
```

**Pydantic v2 核心特性**：

| 特性 | 说明 |
|---|---|
| `model_dump()` | 序列化为 dict（替代 v1 的 `.dict()`） |
| `model_validate(obj)` | 从 dict/JSON 校验并创建实例 |
| `Field(...)` | 字段约束：最小值、最大值、默认值工厂 |
| 嵌套模型 | 模型内嵌模型，自动递归校验 |
| ConfigDict | `frozen=True`（不可变）、`extra="forbid"`（拒绝多余字段） |

```python
from pydantic import BaseModel

class Address(BaseModel):
    city: str
    street: str

class Order(BaseModel):
    order_id: str
    items: list[str]
    address: Address  # 嵌套校验

order = Order(
    order_id="20260822001",
    items=["iPhone 17", "AirPods Pro 3"],
    address={"city": "上海", "street": "浦东新区世纪大道1号"},
)
```

**与 Java Bean Validation 对比**：Java 用注解 `@NotNull @Min(0)`，Python 用 Pydantic 的 `Field(...)`。Pydantic 同时负责转换（`"28"` 自动转成 `28`）。

---

## 3. 单元测试（pytest）

### 3.1 基础用法

```python
# test_calculator.py
from calculator import calculate
import pytest

def test_add():
    assert calculate(10, "+", 20) == 30

def test_divide_zero():
    with pytest.raises(ValueError, match="除数不能为0"):
        calculate(10, "/", 0)

# 参数化测试
@pytest.mark.parametrize("a,op,b,expected", [
    (1, "+", 2, 3),
    (5, "*", 5, 25),
    (10, "-", 3, 7),
])
def test_calculate_cases(a, op, b, expected):
    assert calculate(a, op, b) == expected
```

```bash
pytest                     # 运行全部测试（自动发现 test_*.py / *_test.py）
pytest tests/ -v           # 详细模式
pytest --maxfail=1         # 遇到第一个失败即停止
pytest -k "add or async"   # 按名称过滤
```

### 3.2 fixture（夹具）

fixture 用于准备测试环境与清理资源，是 pytest 的核心能力：

```python
import pytest
import asyncio

@pytest.fixture
def sample_data():
    """测试数据准备（每个用例独立调用）"""
    return {"name": "测试商品", "price": 99.9}

@pytest.fixture(scope="session")
def db_pool():
    """会话级 fixture：整个测试过程只创建一次"""
    pool = create_connection_pool()  # 伪代码
    yield pool                        # yield 之前是 setup，之后是 teardown
    pool.close()

# 异步测试
@pytest.fixture
def event_loop():
    loop = asyncio.new_event_loop()
    yield loop
    loop.close()

async def test_async_logic():
    result = await some_async_function()
    assert result is not None
```

### 3.3 Mock（模拟外部依赖）

Python 测试中，外部 API、数据库、第三方服务都使用 mock 隔离：

```python
from unittest.mock import AsyncMock, patch, MagicMock

# 同步 mock
@patch("module.requests.get")
def test_service(mock_get):
    mock_get.return_value.json.return_value = {"status": "ok"}
    result = my_service.call_api()
    assert result == "ok"
    mock_get.assert_called_once()

# 异步 mock（测试异步服务的关键）
async def test_ai_service():
    with patch("module.llm_client.agenerate", new=AsyncMock()) as mock_llm:
        mock_llm.return_value = "模拟的 AI 回答"
        result = await my_ai_service.ask("你好")
        assert result == "模拟的 AI 回答"
```

---

## 4. 测试覆盖率

```bash
pip install pytest-cov
pytest --cov=src --cov-report=term-missing --cov-report=html
```

输出示例：

```
Name              Stmts   Miss  Cover
-------------------------------------
src/calculator.py    25      2    92%
src/models.py        40      8    80%
-------------------------------------
TOTAL                65     10    85%
```

**覆盖率指标解读**：

- **行覆盖（Line）**：执行过的代码行比例
- **Missing**：未覆盖的行号
- 目标：核心业务逻辑 ≥ 90%，整体 ≥ 80%
- 注意：覆盖率是**必要条件而非充分条件**——100% 覆盖率不等于没有 bug，边界断言同样重要

**project.toml 中的覆盖率门槛**：

```toml
[tool.pytest.ini_options]
addopts = "--cov=src --cov-report=term-missing --cov-fail-under=80"
testpaths = ["tests"]
```

---

## 总结

| 工具 | 职责 |
|---|---|
| typing + mypy | 代码编写期的静态检查 |
| Pydantic | 运行期的数据校验与转换 |
| pytest | 单元测试与集成测试 |
| pytest-cov | 覆盖率度量与门槛 |
| Mock | 外部依赖隔离，让测试可重复执行 |

**工作流**：写代码 → mypy 静态检查 → pytest 单元测试 → 覆盖率 ≥ 80% → 合入 CI。

下一步：进入 [03-advanced/01-python-performance.md](../03-advanced/01-python-performance.md) 学习性能优化。