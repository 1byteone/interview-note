"""
Python 面向对象编程演示
========================
涵盖：类定义、继承、抽象基类、@property、@staticmethod/@classmethod、魔术方法
"""

from abc import ABC, abstractmethod


# ==================== 1. 基础类定义 ====================
print("=" * 60)
print("1. 基础类定义 / Basic Class Definition")
print("=" * 60)


class Person:
    """人类 — 展示 __init__, __str__, __repr__"""

    # 类变量（所有实例共享）
    species: str = "Homo sapiens"
    population: int = 0

    def __init__(self, name: str, age: int):
        """构造函数：初始化实例属性"""
        self.name = name
        self.age = age
        Person.population += 1

    def __str__(self) -> str:
        """给用户看的字符串表示（print 时调用）"""
        return f"{self.name} ({self.age}岁)"

    def __repr__(self) -> str:
        """给开发者看的字符串表示（调试时调用）"""
        return f"Person(name='{self.name}', age={self.age})"

    def introduce(self) -> str:
        """实例方法"""
        return f"Hi, I'm {self.name}, {self.age} years old."


# 测试
p1 = Person("Alice", 25)
p2 = Person("Bob", 30)

print(f"str(p1): {str(p1)}")
print(f"repr(p1): {repr(p1)}")
print(f"p1.introduce(): {p1.introduce()}")
print(f"Person.species: {Person.species}")
print(f"Person.population: {Person.population}")


# ==================== 2. 继承 ====================
print("\n" + "=" * 60)
print("2. 继承与 super() / Inheritance")
print("=" * 60)


class Student(Person):
    """学生类 — 继承自 Person"""

    def __init__(self, name: str, age: int, student_id: str, major: str):
        # super() 调用父类构造函数
        super().__init__(name, age)
        self.student_id = student_id
        self.major = major

    # 方法覆盖
    def introduce(self) -> str:
        return f"{super().introduce()} I'm a {self.major} major (ID: {self.student_id})."

    def study(self, subject: str) -> str:
        return f"{self.name} is studying {subject}."


s1 = Student("Charlie", 20, "S2024001", "Computer Science")
print(f"str(s1): {str(s1)}")
print(f"s1.introduce(): {s1.introduce()}")
print(f"s1.study('Python'): {s1.study('Python')}")

# 多重继承（MRO — Method Resolution Order）
print("\n多重继承 MRO 示例:")


class Teacher(Person):
    def __init__(self, name: str, age: int, subject: str):
        super().__init__(name, age)
        self.subject = subject

    def introduce(self) -> str:
        return f"{super().introduce()} I teach {self.subject}."


class TeachingAssistant(Student, Teacher):
    """
    同时继承 Student 和 Teacher
    MRO: TA → Student → Teacher → Person → object
    """
    def __init__(self, name: str, age: int, student_id: str, major: str, subject: str):
        # 在多重继承中，通常调用第一个父类的 __init__
        Student.__init__(self, name, age, student_id, major)
        self.subject = subject

    def introduce(self) -> str:
        return f"{Student.introduce(self)} Also TA for {self.subject}."


ta = TeachingAssistant("Diana", 22, "S2024002", "Math", "Calculus")
print(f"TA: {ta}")
print(f"TA.introduce(): {ta.introduce()}")
print(f"TA MRO: {[c.__name__ for c in TeachingAssistant.__mro__]}")


# ==================== 3. 抽象基类 ====================
print("\n" + "=" * 60)
print("3. 抽象基类 / Abstract Base Class (ABC)")
print("=" * 60)


class Animal(ABC):
    """抽象基类 — 不能直接实例化"""

    def __init__(self, name: str):
        self.name = name

    @abstractmethod
    def make_sound(self) -> str:
        """抽象方法：子类必须实现"""
        pass

    @abstractmethod
    def move(self) -> str:
        """抽象方法：子类必须实现"""
        pass

    def eat(self, food: str) -> str:
        """具体方法：子类可以直接使用"""
        return f"{self.name} is eating {food}."


class Dog(Animal):
    def make_sound(self) -> str:
        return "Woof!"

    def move(self) -> str:
        return "Running on four legs"


class Bird(Animal):
    def make_sound(self) -> str:
        return "Chirp!"

    def move(self) -> str:
        return "Flying in the sky"


# animal = Animal("Generic")  # TypeError! 不能实例化抽象类
dog = Dog("Buddy")
bird = Bird("Tweety")

print(f"{dog.name}: {dog.make_sound()} → {dog.move()}")
print(f"{dog.eat('bone')}")
print(f"{bird.name}: {bird.make_sound()} → {bird.move()}")


# ==================== 4. @property 装饰器 ====================
print("\n" + "=" * 60)
print("4. @property 装饰器 / Property Decorator")
print("=" * 60)


class Temperature:
    """温度类 — 展示 @property 的 getter/setter"""

    def __init__(self, celsius: float = 0):
        self._celsius = celsius  # 私有属性（约定）

    @property
    def celsius(self) -> float:
        """getter — 像属性一样访问"""
        return self._celsius

    @celsius.setter
    def celsius(self, value: float):
        """setter — 赋值时进行验证"""
        if value < -273.15:
            raise ValueError("Temperature cannot be below absolute zero!")
        self._celsius = value

    @property
    def fahrenheit(self) -> float:
        """只读属性 — 没有 setter"""
        return self._celsius * 9 / 5 + 32

    @property
    def kelvin(self) -> float:
        """只读属性"""
        return self._celsius + 273.15


temp = Temperature(25)
print(f"25°C = {temp.fahrenheit:.1f}°F = {temp.kelvin:.2f}K")

temp.celsius = 100
print(f"100°C = {temp.fahrenheit:.1f}°F")

# temp.celsius = -300  # 会抛出 ValueError


# ==================== 5. @staticmethod 和 @classmethod ====================
print("\n" + "=" * 60)
print("5. @staticmethod / @classmethod")
print("=" * 60)


class MathUtils:
    """数学工具类 — 展示静态方法和类方法"""

    pi: float = 3.1415926535

    @staticmethod
    def add(a: float, b: float) -> float:
        """
        静态方法：
        - 不接收 self 或 cls
        - 就像普通函数，只是放在类命名空间里
        """
        return a + b

    @staticmethod
    def multiply(a: float, b: float) -> float:
        return a * b

    @classmethod
    def circle_area(cls, radius: float) -> float:
        """
        类方法：
        - 接收 cls（类本身）作为第一个参数
        - 可以访问类变量
        - 常用于工厂方法
        """
        return cls.pi * radius ** 2

    @classmethod
    def from_radius(cls, radius: float) -> dict:
        """工厂方法：根据半径创建圆的信息"""
        return {
            "radius": radius,
            "area": cls.circle_area(radius),
            "circumference": 2 * cls.pi * radius,
        }


# 静态方法可以直接通过类调用
print(f"MathUtils.add(3, 5): {MathUtils.add(3, 5)}")
print(f"MathUtils.multiply(4, 7): {MathUtils.multiply(4, 7)}")

# 类方法
print(f"MathUtils.circle_area(5): {MathUtils.circle_area(5):.2f}")
circle = MathUtils.from_radius(10)
print(f"from_radius(10): {circle}")


# ==================== 6. 魔术方法 ====================
print("\n" + "=" * 60)
print("6. 魔术方法 / Magic Methods (Dunder Methods)")
print("=" * 60)


class CustomList:
    """自定义类 — 展示多种魔术方法"""

    def __init__(self, *items):
        self._items = list(items)

    def __len__(self) -> int:
        """len(obj) 时调用"""
        return len(self._items)

    def __getitem__(self, index):
        """obj[index] 时调用"""
        return self._items[index]

    def __setitem__(self, index, value):
        """obj[index] = value 时调用"""
        self._items[index] = value

    def __call__(self, *args, **kwargs):
        """obj() 时调用 — 使实例可调用"""
        return f"CustomList with {len(self)} items: {self._items}"

    def __enter__(self):
        """with 语句进入时调用"""
        print("  [Entering context]")
        return self  # 返回的值赋给 as 变量

    def __exit__(self, exc_type, exc_val, exc_tb):
        """with 语句退出时调用"""
        print("  [Exiting context]")
        if exc_type:
            print(f"  Exception: {exc_type.__name__}: {exc_val}")
        return False  # 返回 False 则异常继续传播

    def __iter__(self):
        """iter(obj) 时调用 — 使对象可迭代"""
        return iter(self._items)

    def __contains__(self, item):
        """item in obj 时调用"""
        return item in self._items

    def __add__(self, other):
        """obj + other 时调用"""
        if isinstance(other, CustomList):
            return CustomList(*(self._items + other._items))
        return NotImplemented

    def __repr__(self) -> str:
        return f"CustomList({self._items})"


# 测试魔术方法
cl = CustomList(1, 2, 3, 4, 5)
print(f"cl: {cl}")
print(f"len(cl): {len(cl)}")
print(f"cl[2]: {cl[2]}")

cl[2] = 99
print(f"After cl[2] = 99: {cl}")

print(f"cl(): {cl()}")

print(f"3 in cl: {3 in cl}")
print(f"100 in cl: {100 in cl}")

print("\n迭代:")
for item in cl:
    print(f"  {item}", end=" ")
print()

cl2 = CustomList(10, 20)
cl3 = cl + cl2
print(f"\ncl + cl2: {cl3}")

print("\nwith 语句 (上下文管理器):")
with CustomList("a", "b") as ctx:
    print(f"  Inside with: {ctx}")

print("\n" + "=" * 60)
print("全部演示完成！")
print("=" * 60)

# 运行方式: python 02_oop_demo.py
# 无需安装第三方依赖