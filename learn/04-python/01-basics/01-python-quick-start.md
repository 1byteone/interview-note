# Python 快速入门

> 👶 新手通道 | 预计阅读：40 分钟

本篇文章带你从零开始搭建 Python 开发环境，掌握核心语法，并完成第一个 CLI 计算器。

---

## 1. 环境搭建

### 1.1 安装 Python 3.11+

从 [python.org](https://python.org) 下载 3.11+ 版本。安装时勾选 **"Add Python to PATH"**。

```bash
python --version
# Python 3.11.x
```

### 1.2 虚拟环境与包管理

venv 是 Python 官方自带的环境隔离工具，每个项目有独立的依赖，互不干扰。

```bash
# 创建虚拟环境
python -m venv .venv

# 激活（Windows）
.venv\Scripts\activate

# 激活（macOS / Linux）
source .venv/bin/activate

# 退出
deactivate
```

pip 是 Python 官方包管理器：

```bash
pip install requests
pip list           # 查看已安装的包
pip freeze > requirements.txt   # 导出依赖
pip install -r requirements.txt # 安装依赖
```

---

## 2. 基本语法

### 2.1 变量与类型

Python 是**动态类型**语言，变量无需声明类型：

```python
name = "Python"       # str
version = 3.11        # float
is_awesome = True     # bool
count = 42            # int
tags = ["简洁", "灵活"]  # list
```

与 Java 的对比：Java 的 `String name = "Python"` 在 Python 中只需 `name = "Python"`，类型由运行时推断。

### 2.2 控制流

```python
# if-elif-else
score = 85
if score >= 90:
    grade = "A"
elif score >= 80:
    grade = "B"
else:
    grade = "C"

# for 循环
for i in range(5):
    print(i)  # 0 1 2 3 4

# while 循环
count = 0
while count < 3:
    count += 1

# 列表推导式（Python 标志性语法）
squares = [x**2 for x in range(10) if x % 2 == 0]
# [0, 4, 16, 36, 64]
```

### 2.3 函数

```python
def greet(name: str, greeting: str = "你好") -> str:
    """简单的问候函数"""
    return f"{greeting}, {name}!"

# 调用
print(greet("小明"))           # 你好, 小明!
print(greet("Bob", "Hello"))  # Hello, Bob!

# *args 和 **kwargs 接收不定参数
def log(format, *args, **kwargs):
    print(format % args, kwargs)
```

---

## 3. 数据结构

### 3.1 list（列表）

可变、有序、可存放任何类型：

```python
fruits = ["苹果", "香蕉", "橘子"]
fruits.append("葡萄")       # 追加
fruits.insert(0, "草莓")    # 插入
fruits.remove("香蕉")       # 删除
item = fruits.pop()         # 弹出末尾
print(fruits[0])            # 索引访问
print(fruits[-1])           # 倒数第一个
```

### 3.2 dict（字典）

键值对映射，Python 中最常用的数据结构：

```python
user = {
    "name": "张三",
    "age": 28,
    "skills": ["Java", "Python"]
}

print(user["name"])         # 张三
print(user.get("email", "未设置"))  # 安全访问
user["age"] = 29            # 修改
del user["age"]             # 删除

# 遍历
for k, v in user.items():
    print(f"{k}: {v}")
```

### 3.3 set（集合）

无序、不重复：

```python
a = {1, 2, 3}
b = {2, 3, 4}
print(a & b)  # {2, 3}  交集
print(a | b)  # {1, 2, 3, 4} 并集
print(a - b)  # {1}     差集
```

### 3.4 tuple（元组）

不可变，适合做常量或字典的键：

```python
point = (10, 20)
x, y = point           # 解包
print(x)               # 10
```

---

## 4. 文件操作

```python
# 写入
with open("hello.txt", "w", encoding="utf-8") as f:
    f.write("你好，世界！")

# 读取
with open("hello.txt", "r", encoding="utf-8") as f:
    content = f.read()
    print(content)

# 逐行读取大文件
with open("big.log", "r", encoding="utf-8") as f:
    for line in f:
        process(line)  # 按行处理，内存友好
```

`with` 语句（上下文管理器）确保文件自动关闭，是 Python 推荐的写法。

---

## 5. 异常处理

```python
try:
    result = 10 / 0
except ZeroDivisionError as e:
    print(f"除零错误: {e}")
except Exception as e:
    print(f"未知错误: {e}")
else:
    print(f"计算成功: {result}")
finally:
    print("总会执行到这里")
```

---

## 6. 最小案例：CLI 计算器

```python
# calculator.py
import sys

def calculate(a: float, op: str, b: float) -> float:
    operations = {
        "+": lambda x, y: x + y,
        "-": lambda x, y: x - y,
        "*": lambda x, y: x * y,
        "/": lambda x, y: x / y if y != 0 else ValueError("除数不能为0"),
    }
    if op not in operations:
        raise ValueError(f"不支持的操作符: {op}")
    return operations[op](a, b)

def main():
    if len(sys.argv) != 4:
        print("用法: python calculator.py <数字1> <操作符> <数字2>")
        print("示例: python calculator.py 10 + 20")
        sys.exit(1)

    try:
        a = float(sys.argv[1])
        op = sys.argv[2]
        b = float(sys.argv[3])
        result = calculate(a, op, b)
        print(f"{a} {op} {b} = {result}")
    except ValueError as e:
        print(f"输入错误: {e}")
        sys.exit(1)

if __name__ == "__main__":
    main()
```

运行：

```bash
python calculator.py 10 + 20
# 输出: 10.0 + 20.0 = 30.0
```

---

## 总结

| 概念 | 要点 |
|---|---|
| 环境 | venv 隔离 + pip 管理依赖 |
| 语法 | 动态类型、缩进作用域、列表推导式 |
| 数据结构 | list/dict/set/tuple 各有适用场景 |
| 文件 | `with open` 是标准写法 |
| 异常 | try/except/else/finally 完整结构 |

下一步：进入 [02-oop-and-module](02-oop-and-module.md) 学习面向对象与模块化。