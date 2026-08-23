"""
Python 装饰器与生成器演示
==========================
涵盖：带参数的装饰器、@functools.wraps、生成器函数(yield)、生成器表达式、yield from
"""

import functools
import time


# ==================== 1. 基础装饰器 ====================
print("=" * 60)
print("1. 基础装饰器 / Basic Decorator")
print("=" * 60)


def my_decorator(func):
    """最简单的装饰器：接收函数，返回包装函数"""
    def wrapper(*args, **kwargs):
        print(f"  [装饰器外层] 调用函数 {func.__name__} 之前...")
        result = func(*args, **kwargs)
        print(f"  [装饰器外层] 调用完成，结果: {result}")
        return result
    return wrapper


@my_decorator
def say_hello(name: str) -> str:
    return f"Hello, {name}!"


print(say_hello("Alice"))


# ==================== 2. 带参数的装饰器 ====================
print("\n" + "=" * 60)
print("2. 带参数的装饰器 / Decorator with Arguments")
print("=" * 60)


def repeat(times: int):
    """装饰器工厂：接收参数，返回真正的装饰器"""
    def decorator(func):
        @functools.wraps(func)  # 保留原函数的元信息
        def wrapper(*args, **kwargs):
            for i in range(times):
                print(f"  [repeat {i + 1}/{times}]")
                func(*args, **kwargs)
        return wrapper
    return decorator


@repeat(3)
def announce_message(msg: str):
    print(f"  📢 {msg}")


print("调用 announce_message:")
announce_message("系统升级中...")


# ==================== 3. @functools.wraps ====================
print("\n" + "=" * 60)
print("3. @functools.wraps / Preserving Function Metadata")
print("=" * 60)


def log_without_wraps(func):
    """不加 wraps 的装饰器"""
    def wrapper(*args, **kwargs):
        print(f"  Calling {func.__name__}")
        return func(*args, **kwargs)
    return wrapper


def log_with_wraps(func):
    """加 wraps 的装饰器"""
    @functools.wraps(func)
    def wrapper(*args, **kwargs):
        print(f"  Calling {func.__name__}")
        return func(*args, **kwargs)
    return wrapper


@log_without_wraps
def func_a():
    """我是 func_a 的文档字符串"""
    pass


@log_with_wraps
def func_b():
    """我是 func_b 的文档字符串"""
    pass


print(f"无 wraps:  函数名={func_a.__name__}, 文档={func_a.__doc__}")
print(f"有 wraps:  函数名={func_b.__name__}, 文档={func_b.__doc__}")
print("→ 使用 @functools.wraps 可以保留原函数的 __name__ 和 __doc__")


# ==================== 4. 计时装饰器（综合应用） ====================
print("\n" + "=" * 60)
print("4. 计时装饰器 / Timing Decorator")
print("=" * 60)


def timer(func):
    """测量函数执行时间"""
    @functools.wraps(func)
    def wrapper(*args, **kwargs):
        start = time.perf_counter()
        result = func(*args, **kwargs)
        elapsed = time.perf_counter() - start
        print(f"  ⏱ {func.__name__} 执行耗时: {elapsed:.6f} 秒")
        return result
    return wrapper


@timer
def slow_sum(n: int) -> int:
    total = 0
    for i in range(n):
        total += i
    return total


print(f"slow_sum(100000) = {slow_sum(100000)}")


# ==================== 5. 生成器函数 ====================
print("\n" + "=" * 60)
print("5. 生成器函数 / Generator Functions (yield)")
print("=" * 60)


def fibonacci(limit: int):
    """斐波那契数列生成器 — 使用 yield 惰性生成"""
    a, b = 0, 1
    count = 0
    while count < limit:
        yield a  # yield 暂停函数，返回当前值
        a, b = b, a + b
        count += 1


print("斐波那契数列（前10个）:")
for num in fibonacci(10):
    print(f"  {num}", end=" ")
print()

# 生成器一次只能迭代一次
fib = fibonacci(5)
print(f"\n用 next() 手动取值: {next(fib)}, {next(fib)}, {next(fib)}")

# 生成器的惰性求值 — 处理大数据时节省内存
print("\n生成器 vs 列表 内存对比（概念演示）:")
print("  range(10**8) 生成器: 几乎不占内存")
print("  list(range(10**8)): 占数百 MB 内存 ❌")


def read_large_file(file_path: str):
    """逐行读取大文件 — 生产中最常用的生成器场景"""
    with open(file_path, "r", encoding="utf-8") as f:
        for line in f:
            yield line.strip()  # 一次只加载一行


# ==================== 6. 生成器表达式 ====================
print("\n" + "=" * 60)
print("6. 生成器表达式 / Generator Expressions")
print("=" * 60)

# 生成器表达式：圆括号，惰性求值
squares_gen = (x ** 2 for x in range(5))
print(f"生成器表达式 (x**2 for x in range(5)): {squares_gen}")
print(f"  → 求和: {sum(squares_gen)}")  # 生成器只能消费一次

# 与列表推导式对比
squares_list = [x ** 2 for x in range(5)]
print(f"列表推导式对比: {squares_list}")

# 生成器表达式用于函数参数（无需额外括号）
total = sum(x for x in range(1, 101) if x % 2 == 0)
print(f"\nsum(1~100 的偶数): {total}")


# ==================== 7. yield from ====================
print("\n" + "=" * 60)
print("7. yield from / Delegating to Sub-generator")
print("=" * 60)


def sub_generator(names: list):
    """子生成器"""
    for name in names:
        yield f"sub: {name}"


def chain_with_yield_from(generator1, generator2):
    """使用 yield from 委托给子生成器"""
    yield from generator1  # 等价于 for x in gen1: yield x
    yield from generator2


def chain_manual(generator1, generator2):
    """不使用 yield from 的等价写法"""
    for item in generator1:
        yield item
    for item in generator2:
        yield item


gen_a = sub_generator(["Alice", "Bob"])
gen_b = sub_generator(["Charlie", "Diana"])

print("yield from 链式生成:")
for item in chain_with_yield_from(gen_a, gen_b):
    print(f"  {item}")


# 嵌套可迭代对象展平 — yield from 的经典用途
def flatten(nested_list):
    """递归展平嵌套列表"""
    for item in nested_list:
        if isinstance(item, (list, tuple)):
            yield from flatten(item)  # 递归委托
        else:
            yield item


nested = [1, [2, [3, 4], 5], [6, [7, 8]], 9]
print(f"\n展平 {nested}:")
print(f"  → {list(flatten(nested))}")


# ==================== 8. 生成器与异常处理 ====================
print("\n" + "=" * 60)
print("8. 生成器与状态 / Generator State")
print("=" * 60)


def countdown(n: int):
    """倒计时生成器 — 展示生成器是有状态的"""
    while n > 0:
        yield f"T-{n}"
        n -= 1
    yield "🚀 发射!"


cd = countdown(3)
print(f"生成器状态: {cd}")

print("\n逐步消费:")
try:
    while True:
        print(f"  {next(cd)}")
except StopIteration:
    print("  （迭代完毕）")

# 生成器的 send() 方法
print("\ngenerator.send() 示例:")


def accumulator():
    """累加器 — 接收外部传入的值"""
    total = 0
    while True:
        value = yield total  # yield 接收外部 send 的值
        if value is None:
            break
        total += value


acc = accumulator()
print(f"  初始值: {next(acc)}")       # 启动生成器
print(f"  send(5) → {acc.send(5)}")   # 发送 5, 得到 total=5
print(f"  send(10) → {acc.send(10)}") # 发送 10, 得到 total=15


# ==================== 9. 综合示例：数据管道 ====================
print("\n" + "=" * 60)
print("9. 综合示例：生成器数据管道 / Data Pipeline")
print("=" * 60)


def read_numbers():
    """数据源"""
    for i in range(1, 21):
        yield i


def filter_even(source):
    """过滤奇数"""
    for num in source:
        if num % 2 == 0:
            yield num


def square(source):
    """平方"""
    for num in source:
        yield num * num


def take(source, count):
    """只取前 count 个"""
    for i, num in enumerate(source):
        if i >= count:
            break
        yield num


# 管道：读入 → 过滤偶数 → 平方 → 取前5个
pipeline = take(square(filter_even(read_numbers())), 5)
print("数据管道结果:", list(pipeline))

print("\n" + "=" * 60)
print("全部演示完成！")
print("=" * 60)

# 运行方式: python 03_decorator_generator_demo.py
# 无需安装第三方依赖