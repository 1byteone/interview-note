# Python — 面试抽认卡

> 来源：`learn/04-python/05-interview/`

---

### Card 1: GIL 原理与影响
**维度**: 🔬深挖 | **难度**: ⭐⭐⭐

> **Q: Python GIL 是什么？它如何影响多线程和多进程？**

**A:** GIL（全局解释器锁）是 CPython 解释器中的一个互斥锁，保证同一时刻只有一个线程执行 Python 字节码。导致多线程无法利用多核 CPU 并行计算。多线程适合 IO 密集型（网络请求、文件读写——线程等待 IO 时释放 GIL）；多进程适合 CPU 密集型（每个进程有独立解释器，无 GIL 竞争）。绕过 GIL 方案：多进程、C 扩展（如 NumPy 释放 GIL）、asyncio 协程。

---

### Card 2: async/await 原理
**维度**: 🔬深挖 | **难度**: ⭐⭐⭐

> **Q: Python async/await 是如何实现的？事件循环的工作机制是什么？**

**A:** `async def` 定义协程函数，返回协程对象。`await` 挂起当前协程，让出控制权给事件循环。事件循环通过 `asyncio.run()` 启动，维护一个就绪队列和等待队列，执行过程：从就绪队列取协程执行 → 遇到 `await` 挂起协程到等待队列 → 等待完成后放回就绪队列 → 循环往复。`asyncio.gather` 并发执行多个协程，`asyncio.create_task` 将协程包装为 Task 调度。关键区别：`await` 串行，`gather` 并发。

---

### Card 3: 装饰器原理
**维度**: 🔬深挖 | **难度**: ⭐⭐

> **Q: 装饰器的工作原理是什么？functools.wraps 的作用？**

**A:** 装饰器本质是语法糖：`@decorator` 等价于 `func = decorator(func)`。装饰器函数接收被装饰函数作为参数，返回一个新函数。`functools.wraps` 将原函数的元信息（`__name__`、`__doc__`、`__module__`）复制到包装函数，否则调试时看到的是包装函数名。带参数的装饰器需要三层嵌套：外层接收参数 → 中层接收函数 → 内层返回包装函数。

---

### Card 4: 元类（Metaclass）
**维度**: 🔬深挖 | **难度**: ⭐⭐⭐

> **Q: 元类是什么？ORM 框架如何利用元类？**

**A:** 元类是创建类的类，默认是 `type`。`type('MyClass', (Base,), {'attr': value})` 动态创建类。元类通过 `__new__` 和 `__init__` 方法拦截类创建过程，可修改类属性、添加方法、验证类定义。ORM 框架（如 SQLAlchemy、Django ORM）利用元类：在类定义阶段读取 `Field` 类属性，构建表结构映射（Table Meta），生成 `__table__` 和 `__mapper__`。元类也被用于单例模式、注册表模式。

---

### Card 5: 生成器 vs 迭代器
**维度**: 📝速记 | **难度**: ⭐⭐

> **Q: 生成器和迭代器的区别是什么？yield 关键字如何工作？**

**A:** 迭代器实现了 `__iter__` 和 `__next__` 协议，可以用 `for` 循环遍历。生成器是特殊的迭代器，用 `yield` 关键字定义，每次 `yield` 暂停执行并返回值，下次调用 `__next__` 从暂停处继续。生成器函数体中的 `return` 值存储在 `StopIteration.value` 中。`yield from` 可委托给另一个生成器，简化嵌套。生成器懒惰求值，节省内存，适合处理大数据流。

---

### Card 6: with 语句与上下文管理器
**维度**: 💻代码 | **难度**: ⭐⭐

> **Q: with 语句的执行流程是什么？如何自定义上下文管理器？**

**A:** `with` 语句在进入时调用 `__enter__`，退出时（无论是否异常）调用 `__exit__`。`__exit__` 接收 exc_type/exc_val/exc_tb，返回 True 表示忽略异常。自定义方式：① 类实现 `__enter__` 和 `__exit__`；② `@contextmanager` 装饰器 + `yield` 分割 enter 和 exit 逻辑（yield 前的代码是 enter，yield 后的代码是 exit）。常用于资源管理（文件/数据库连接/锁）。

---

### Card 7: 深拷贝 vs 浅拷贝
**维度**: 📝速记 | **难度**: ⭐

> **Q: 深拷贝和浅拷贝的区别是什么？copy 模块的 copy 和 deepcopy 有什么不同？**

**A:** 浅拷贝（`copy.copy`）创建新对象，但嵌套对象是引用，修改嵌套对象会影响原对象。深拷贝（`copy.deepcopy`）递归复制所有嵌套对象，新对象完全独立。`deepcopy` 维护一个 memo 字典记录已复制对象，防止循环引用导致无限递归（如 `a = []; a.append(a)`）。不可变类型（int/str/tuple）浅拷贝不复制，直接返回引用。

---

### Card 8: 列表推导式性能
**维度**: 🔬深挖 | **难度**: ⭐⭐

> **Q: 列表推导式为什么比 for 循环快？生成器表达式有什么优势？**

**A:** 列表推导式比 for 循环快 30-50%：① 在 C 级别执行（Python 字节码在解释器层面优化）；② 避免了 `append` 方法查找开销；③ 减少了 `LOAD_FAST` 等指令数。生成器表达式（圆括号）惰性求值，不一次性生成全部元素，内存占用更低。`sum(x**2 for x in range(10**7))` 用生成器几乎不占内存，列表推导式会占用几百 MB。

---

### Card 9: typing 类型系统
**维度**: 📝速记 | **难度**: ⭐⭐

> **Q: Python typing 模块的类型注解有哪些常用类型？运行时是否生效？**

**A:** 常用类型：`List[int]`、`Dict[str, int]`、`Optional[str]`（等价于 `Union[str, None]`）、`Union[int, str]`、`Tuple[int, ...]`、`Any`、`TypeVar`、`Generic[T]`、`Callable[[int], str]`。Python 3.10+ 支持 `X | Y` 语法（`str | int`）。类型注解在运行时**不生效**，仅用于静态类型检查（mypy/pyright）。`typing.TYPE_CHECKING` 可条件导入仅用于类型检查的模块，避免循环导入。

---

### Card 10: pytest fixtures
**维度**: 💻代码 | **难度**: ⭐⭐

> **Q: pytest fixture 的 scope 有哪些？如何管理 fixture 的清理？**

**A:** Scope：`function`（每个测试函数，默认）、`class`（每个测试类）、`module`（每个模块）、`session`（整个测试会话）。`conftest.py` 中的 fixture 自动对该目录下的所有测试生效。`yield` 分割 setup 和 teardown 逻辑：yield 之前是 setup，yield 之后是 teardown（无论是否异常都执行）。`autouse=True` 让 fixture 自动生效，无需显式引用。`request` 内置 fixture 可获取测试上下文。

---

### Card 11: mock 测试
**维度**: 💻代码 | **难度**: ⭐⭐

> **Q: unittest.mock 的 patch 和 MagicMock 如何使用？side_effect 和 return_value 的区别？**

**A:** `@patch('module.ClassName')` 在测试期间替换目标为 Mock 对象，测试结束后自动恢复。`return_value` 设置固定返回值；`side_effect` 可设为函数（动态返回）、异常（抛出异常）或可迭代对象（每次返回不同值）。`MagicMock` 自动支持魔术方法（如 `__len__`、`__iter__`）。`spec` 参数限制 Mock 只模拟真实对象的方法，防止拼写错误。

---

### Card 12: Python 内存管理
**维度**: 🔬深挖 | **难度**: ⭐⭐

> **Q: Python 的内存分配机制是什么？小对象和大对象的分配有何不同？**

**A:** Python 使用两级分配器：底层 `malloc` 分配大块内存，上层 PyMalloc 管理小对象（<512 字节）的分配。小块内存通过 arena（256KB）→ pool（4KB）→ block（分级大小）三级结构管理，避免频繁系统调用。大对象直接通过 `malloc` 分配。`sys.getsizeof` 查看对象内存大小。`__slots__` 可减少对象内存（固定属性，不再有 `__dict__`）。

---

### Card 13: 垃圾回收机制
**维度**: 🔬深挖 | **难度**: ⭐⭐

> **Q: Python 的垃圾回收机制有哪些？引用计数和循环垃圾回收如何协作？**

**A:** 主要机制：① 引用计数（主，每个对象维护引用计数，归零时立即回收，无法处理循环引用）；② 标记-清除（处理循环引用，分代收集，标记可达对象，清除不可达的）；③ 分代回收（三代：0/1/2，新对象放入 0 代，经历一次回收后升级，阈值触发回收）。`gc` 模块：`gc.collect()` 手动触发，`gc.get_objects()` 查看存活对象，`gc.set_threshold()` 调整分代阈值。

---

### Card 14: 上下文管理器（@contextmanager）
**维度**: 💻代码 | **难度**: ⭐⭐

> **Q: @contextmanager 装饰器如何将一个生成器函数转为上下文管理器？**

**A:** `@contextmanager` 装饰一个生成器函数，`yield` 之前的代码是 `__enter__`，`yield` 返回的值赋给 `as` 变量，`yield` 之后的代码是 `__exit__`（在 `finally` 块中执行，确保清理）。示例：`@contextmanager; def managed_resource(): resource = acquire(); try: yield resource; finally: release(resource)`。优势：比类实现更简洁，不需要定义 `__enter__`/`__exit__`。注意：`yield` 只能有一个，异常需在 `try/finally` 中处理。

---

### Card 15: C 扩展加速
**维度**: 🔬深挖 | **难度**: ⭐⭐⭐

> **Q: Python 性能瓶颈时有哪些 C 扩展加速方案？各有什么优缺点？**

**A:** ① Cython：将 Python 代码编译为 C 扩展，静态类型声明后接近 C 性能，适合数值计算。② C 扩展（Python C API）：直接用 C 写模块，性能最高但开发成本高。③ ctypes/cffi：调用现有 C 动态库，无需修改 C 代码，但有调用开销。④ NumPy/CuPy：向量化操作，底层 C/Fortran 实现，处理大规模数组比 Python 循环快 100 倍+。⑤ Numba：JIT 编译器，装饰器标注函数，自动编译为机器码，适合数值计算循环。