"""
Python 基础语法演示
====================
涵盖：变量与类型、类型提示、控制流、推导式、函数高级用法、Lambda 表达式
"""

# ==================== 1. 变量与类型 ====================
print("=" * 60)
print("1. 变量与类型 / Variables & Types")
print("=" * 60)

# Python 是动态类型语言，变量无需声明类型
name: str = "Alice"          # 类型提示（仅提示，不强制）
age: int = 25
height: float = 1.68
is_student: bool = True
hobbies: list = ["reading", "coding", "hiking"]

print(f"name: {name}, type: {type(name)}")
print(f"age: {age}, type: {type(age)}")
print(f"height: {height}, type: {type(height)}")
print(f"is_student: {is_student}, type: {type(is_student)}")
print(f"hobbies: {hobbies}, type: {type(hobbies)}")

# None 类型
nothing: None = None
print(f"nothing: {nothing}, type: {type(nothing)}")


# ==================== 2. 控制流 ====================
print("\n" + "=" * 60)
print("2. 控制流 / Control Flow")
print("=" * 60)

# if / elif / else
score = 85
if score >= 90:
    grade = "A"
elif score >= 80:
    grade = "B"
elif score >= 70:
    grade = "C"
else:
    grade = "D"
print(f"Score: {score} → Grade: {grade}")

# for 循环
print("\nfor 循环遍历列表:")
fruits = ["apple", "banana", "cherry"]
for fruit in fruits:
    print(f"  - {fruit}")

# for 循环 + enumerate
print("\nfor + enumerate 获取索引:")
for idx, fruit in enumerate(fruits, start=1):
    print(f"  {idx}. {fruit}")

# for 循环 + zip
print("\nfor + zip 并行遍历:")
names = ["Alice", "Bob", "Charlie"]
scores = [92, 78, 85]
for name, score in zip(names, scores):
    print(f"  {name}: {score}")

# while 循环
print("\nwhile 循环:")
count = 3
while count > 0:
    print(f"  countdown: {count}")
    count -= 1

# break / continue
print("\nbreak / continue 示例:")
for i in range(10):
    if i == 3:
        continue  # 跳过 3
    if i == 7:
        break     # 在 7 处停止
    print(f"  {i}", end=" ")
print()  # 换行


# ==================== 3. 推导式 ====================
print("\n" + "=" * 60)
print("3. 推导式 / Comprehensions")
print("=" * 60)

# 列表推导式
squares = [x ** 2 for x in range(10)]
print(f"列表推导式 [x**2 for x in range(10)]: {squares}")

# 带条件
even_squares = [x ** 2 for x in range(10) if x % 2 == 0]
print(f"偶数的平方: {even_squares}")

# 字典推导式
square_dict = {x: x ** 2 for x in range(5)}
print(f"字典推导式: {square_dict}")

# 集合推导式
unique_lengths = {len(word) for word in ["hello", "world", "hi", "python"]}
print(f"集合推导式（单词长度）: {unique_lengths}")

# 元组推导式 → 实际是生成器表达式
tuple_gen = (x ** 2 for x in range(5))
print(f"元组/生成器表达式: {tuple(tuple_gen)}")

# 嵌套推导式
matrix = [[1, 2, 3], [4, 5, 6], [7, 8, 9]]
flattened = [num for row in matrix for num in row]
print(f"嵌套推导式（展平矩阵）: {flattened}")


# ==================== 4. 函数高级用法 ====================
print("\n" + "=" * 60)
print("4. 函数高级用法 / Advanced Functions")
print("=" * 60)


def greet(name: str, greeting: str = "Hello") -> str:
    """默认参数函数"""
    return f"{greeting}, {name}!"


print(greet("Alice"))
print(greet("Bob", "Hi"))


def calculate(a: int, b: int, *args: int, **kwargs) -> dict:
    """
    *args  — 可变位置参数
    **kwargs — 可变关键字参数
    """
    result = {"sum": a + b}
    if args:
        result["extra_sum"] = sum(args)
        result["all_numbers"] = (a, b) + args
    if kwargs:
        result["named_params"] = kwargs
    return result


print(f"\n*args / **kwargs 示例:")
print(calculate(1, 2))
print(calculate(1, 2, 3, 4, 5))
print(calculate(1, 2, x=10, y=20, mode="test"))


def process_items(items: list[int], multiplier: int = 2) -> list[int]:
    """类型提示完整的函数"""
    return [item * multiplier for item in items]


print(f"\n类型提示示例: {process_items([1, 2, 3], 3)}")


# ==================== 5. Lambda 表达式 ====================
print("\n" + "=" * 60)
print("5. Lambda 表达式 / Lambda Functions")
print("=" * 60)

# 基本 lambda
add = lambda x, y: x + y
print(f"lambda add(3, 5): {add(3, 5)}")

# 排序时使用 lambda
students = [
    {"name": "Alice", "score": 92},
    {"name": "Bob", "score": 78},
    {"name": "Charlie", "score": 85},
]
sorted_students = sorted(students, key=lambda s: s["score"], reverse=True)
print(f"按分数排序: {[s['name'] for s in sorted_students]}")

# map / filter / reduce 与 lambda
numbers = [1, 2, 3, 4, 5, 6]
doubled = list(map(lambda x: x * 2, numbers))
evens = list(filter(lambda x: x % 2 == 0, numbers))
print(f"map (double): {doubled}")
print(f"filter (even): {evens}")

# 注意：更推荐使用列表推导式替代 map/filter
print(f"列表推导式等效: {[x * 2 for x in numbers]}")
print(f"列表推导式等效: {[x for x in numbers if x % 2 == 0]}")


# ==================== 运行说明 ====================
# python 01_basics_demo.py
# 无需安装任何第三方依赖