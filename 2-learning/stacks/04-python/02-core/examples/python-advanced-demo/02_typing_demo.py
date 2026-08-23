"""
Python 类型提示高级演示
========================
涵盖：typing 模块、Literal、TypedDict、Callable、Protocol、泛型
"""

from typing import (
    Any,
    Callable,
    Dict,
    Generic,
    List,
    Literal,
    Optional,
    Protocol,
    TypedDict,
    TypeVar,
    Union,
)


# ==================== 1. typing 模块基础 ====================
print("=" * 60)
print("1. typing 基础 / Basic Typing")
print("=" * 60)

# List / Dict 泛型容器
names: List[str] = ["Alice", "Bob", "Charlie"]
scores: Dict[str, int] = {"Alice": 92, "Bob": 78}

# Optional = Union[T, None]
def find_user(user_id: int) -> Optional[str]:
    """可能返回 None 的查找函数"""
    users = {1: "Alice", 2: "Bob"}
    return users.get(user_id)  # 可能返回 None

# Union：多种类型
def parse_value(value: Union[int, str, float]) -> float:
    """接受多种类型的输入"""
    return float(value)

# Any：任意类型（尽量少用）
def debug_print(value: Any) -> None:
    """接受任何类型"""
    print(f"  Debug: {value} ({type(value).__name__})")

print(f"  find_user(1): {find_user(1)}")
print(f"  find_user(99): {find_user(99)}")
print(f"  parse_value('3.14'): {parse_value('3.14')}")
print(f"  parse_value(42): {parse_value(42)}")
debug_print([1, 2, 3])


# ==================== 2. Literal 和 TypedDict ====================
print("\n" + "=" * 60)
print("2. Literal & TypedDict")
print("=" * 60)

# Literal：限制值为特定字面量
Status = Literal["pending", "processing", "completed", "failed"]


def update_status(new_status: Status) -> str:
    """只接受限定的字符串值"""
    return f"状态已更新为: {new_status}"


print(f"  {update_status('pending')}")
print(f"  {update_status('completed')}")
# update_status("invalid")  # mypy 会报错，但运行时不会


# TypedDict：字典的结构化类型（带键名的 dict）
class Movie(TypedDict):
    """结构化字典类型"""
    title: str
    year: int
    rating: float
    tags: List[str]


movie: Movie = {
    "title": "Interstellar",
    "year": 2014,
    "rating": 8.7,
    "tags": ["Sci-Fi", "Drama"],
}
print(f"  Movie: {movie['title']} ({movie['year']}) 评分 {movie['rating']}")
print(f"  类型: {Movie.__annotations__}")


# ==================== 3. Callable ====================
print("\n" + "=" * 60)
print("3. Callable / 函数类型")
print("=" * 60)

# Callable[[参数类型...], 返回值类型]
Operation = Callable[[int, int], int]


def apply_operation(a: int, b: int, operation: Operation) -> int:
    """接收函数作为参数"""
    return operation(a, b)


def add(x: int, y: int) -> int:
    return x + y


def multiply(x: int, y: int) -> int:
    return x * y


print(f"  apply_operation(5, 3, add): {apply_operation(5, 3, add)}")
print(f"  apply_operation(5, 3, multiply): {apply_operation(5, 3, multiply)}")
print(f"  apply_operation(5, 3, lambda a,b: a-b): {apply_operation(5, 3, lambda a, b: a - b)}")


# ==================== 4. Protocol（结构化子类型） ====================
print("\n" + "=" * 60)
print("4. Protocol / 结构子类型（鸭子类型）")
print("=" * 60)


class Drawable(Protocol):
    """定义协议：任何实现了这些方法的类都符合该协议"""
    def draw(self) -> str: ...

    @property
    def name(self) -> str: ...


class Circle:
    """圆 — 并未显式继承 Drawable"""
    def __init__(self, radius: float):
        self.radius = radius
        self.name = "Circle"

    def draw(self) -> str:
        return f"绘制半径 {self.radius} 的圆"


class Square:
    """正方形 — 并未显式继承 Drawable"""
    def __init__(self, side: float):
        self.side = side
        self.name = "Square"

    def draw(self) -> str:
        return f"绘制边长 {self.side} 的正方形"


class BrokenShape:
    """不完整的类 — 不符合 Drawable 协议"""
    def __init__(self):
        self.name = "Broken"
    # 缺少 draw() 方法


def render_all(shapes: List[Drawable]) -> list[str]:
    """接受任何实现 Drawable 协议的对象（结构化子类型）"""
    return [s.draw() for s in shapes]


circle = Circle(5.0)
square = Square(4.0)
# broken = BrokenShape()  # mypy 会报错: 缺少 draw() 方法

print(f"  结构子类型: Circle 和 Square 都符合 Drawable 协议")
for result in render_all([circle, square]):
    print(f"  🎨 {result}")


# ==================== 5. 泛型 ====================
print("\n" + "=" * 60)
print("5. 泛型 / Generics")
print("=" * 60)

T = TypeVar("T")  # 类型变量
K = TypeVar("K")
V = TypeVar("V")


class Stack(Generic[T]):
    """泛型栈 — 可以存储任意类型"""
    def __init__(self):
        self._items: List[T] = []

    def push(self, item: T) -> None:
        self._items.append(item)

    def pop(self) -> T:
        return self._items.pop()

    def peek(self) -> T:
        return self._items[-1]

    def is_empty(self) -> bool:
        return len(self._items) == 0

    def __len__(self) -> int:
        return len(self._items)


int_stack = Stack[int]()
int_stack.push(1)
int_stack.push(2)
int_stack.push(3)
print(f"  Stack[int]: {int_stack.pop()} popped, 剩余 {len(int_stack)} 个")

str_stack = Stack[str]()
str_stack.push("hello")
str_stack.push("world")
print(f"  Stack[str]: {str_stack.pop()} popped")

# 泛型函数
def first_element(items: list[T]) -> Optional[T]:
    """泛型函数：返回列表第一个元素"""
    return items[0] if items else None


print(f"  first_element([1,2,3]): {first_element([1, 2, 3])}")
print(f"  first_element(['a','b']): {first_element(['a', 'b'])}")
print(f"  first_element([]): {first_element([])}")


class Pair(Generic[K, V]):
    """多类型参数泛型"""
    def __init__(self, key: K, value: V):
        self.key = K
        self.value = value

    def get_pair(self) -> tuple[K, V]:
        return (self.key, self.value)


pair = Pair[str, int]("count", 42)
print(f"  Pair[str, int]: {pair.get_pair()}")


# 受限泛型
Number = TypeVar("Number", int, float)


def add_numbers(a: Number, b: Number) -> Number:
    """只接受数字类型的泛型函数"""
    return a + b


print(f"  add_numbers(1, 2): {add_numbers(1, 2)}")
print(f"  add_numbers(1.5, 2.5): {add_numbers(1.5, 2.5)}")


# ==================== 6. 综合示例 ====================
print("\n" + "=" * 60)
print("6. 综合示例 / Combined Example")
print("=" * 60)


class APIResponse(TypedDict):
    """API 响应结构"""
    code: int
    message: str
    data: Any


class Repository(Protocol):
    """数据仓库协议"""
    def get(self, id: int) -> Optional[Dict[str, Any]]: ...

    def save(self, data: Dict[str, Any]) -> bool: ...


class UserRepository:
    """内存用户仓库"""
    def __init__(self):
        self._data: Dict[int, Dict[str, Any]] = {}

    def get(self, id: int) -> Optional[Dict[str, Any]]:
        return self._data.get(id)

    def save(self, data: Dict[str, Any]) -> bool:
        self._data[data["id"]] = data
        return True


def handle_request(repo: Repository, user_data: Dict[str, Any]) -> APIResponse:
    """依赖抽象（Protocol），不依赖具体实现"""
    if repo.get(user_data["id"]):
        return {"code": 409, "message": "用户已存在", "data": None}

    if repo.save(user_data):
        return {"code": 201, "message": "创建成功", "data": repo.get(user_data["id"])}

    return {"code": 500, "message": "保存失败", "data": None}


repo = UserRepository()
response = handle_request(repo, {"id": 1, "name": "Alice", "email": "alice@example.com"})
print(f"  创建用户: code={response['code']}, message={response['message']}")

response2 = handle_request(repo, {"id": 1, "name": "Alice Again"})
print(f"  重复创建: code={response2['code']}, message={response2['message']}")

print("\n" + "=" * 60)
print("类型检查方式: mypy 02_typing_demo.py")
print("=" * 60)

# 运行方式: python 02_typing_demo.py
# 可选: pip install mypy 然后运行 mypy 进行静态类型检查