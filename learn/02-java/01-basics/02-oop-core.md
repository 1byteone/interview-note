# OOP 核心 — 封装/继承/多态 · 接口/抽象类 · Lambda/Stream

> 等级：👶 新手通道
> 目标：深入理解 OOP 三大特性，掌握接口 vs 抽象类的选择策略，熟练使用 Lambda 和 Stream API。

---

## 一、封装（Encapsulation）

### 1.1 封装的意义

封装隐藏内部实现，对外暴露稳定的接口。核心是"高内聚、低耦合"。

```java
public class BankAccount {
    private BigDecimal balance;          // 私有，外部不可直接修改

    public void deposit(BigDecimal amount) {
        // 校验逻辑封装在内部
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("金额必须为正");
        }
        balance = balance.add(amount);
    }

    public BigDecimal getBalance() { return balance; }  // 只读暴露
}
```

### 1.2 访问权限控制

| 修饰符 | 本类 | 同一包 | 子类 | 任意 |
|--------|------|--------|------|------|
| `private` | Yes | No | No | No |
| 默认（package-private） | Yes | Yes | No | No |
| `protected` | Yes | Yes | Yes | No |
| `public` | Yes | Yes | Yes | Yes |

---

## 二、继承（Inheritance）

### 2.1 继承的陷阱

Java 是单继承，但多层继承容易导致"菱形问题"——虽然 Java 接口支持多继承，但类继承的单一性意味着：

```java
class A { void foo() { print("A"); } }
class B extends A { void foo() { print("B"); } }
class C extends A { void foo() { print("C"); } }
// 如果 Java 支持多继承，class D extends B, C 则 foo() 有歧义
```

最佳实践：**组合优于继承**。当"是"（is-a）关系不明确时，用组合 + 接口替代。

### 2.2 构造器链

子类构造器隐式调用 `super()`，如果父类没有无参构造器则必须显式指定：

```java
class Parent {
    Parent(String name) { ... }
}
class Child extends Parent {
    Child() { super("default"); }  // 必须显式
}
```

---

## 三、多态（Polymorphism）

### 3.1 编译时多态 vs 运行时多态

- **编译时多态**（方法重载）：根据参数类型、数量在编译期确定调用哪个方法
- **运行时多态**（方法重写）：根据实际对象类型在运行时确定调用哪个方法

### 3.2 动态绑定原理

JVM 通过**虚方法表（vtable）**实现动态绑定，每个类有一张 vtable 存储方法入口地址，调用时按实际类型查表。

```java
Animal a = new Dog();
a.speak();  // 字节码为 invokevirtual，运行期查 vtable 定位到 Dog.speak
```

---

## 四、接口 vs 抽象类

### 4.1 对比表

| 维度 | 抽象类 | 接口 |
|------|--------|------|
| 关键字 | `abstract class` | `interface` |
| 构造器 | 有 | 无 |
| 字段 | 任意 | 只能是 `public static final` |
| 方法实现 | 可以有抽象 + 具体方法 | `default`/`static` 方法有实现 |
| 多继承 | 单继承 | 多实现 |
| 语义 | "是什么"（is-a） | "能做什么"（can-do） |

### 4.2 选择策略

- **有共享状态或构造器逻辑** → 抽象类
- **完全不相关类需要同一种能力** → 接口（如 `Serializable`、`Comparable`）
- **描述行为契约** → 接口（如 `Runnable`、`Callable`）
- **模板方法模式** → 抽象类（骨架实现）

### 4.3 实战：接口隔离原则

```java
// 不好的设计：胖接口
interface Worker { void work(); void eat(); void sleep(); }

// 好的设计：接口隔离
interface Workable { void work(); }
interface Eatable { void eat(); }
interface Sleepable { void sleep(); }

class Robot implements Workable { }  // 机器人不需要 eat 和 sleep
```

---

## 五、内部类（Inner Class）

### 5.1 四种内部类

| 类型 | 定义位置 | 特点 |
|------|----------|------|
| 成员内部类 | 类内部 | 持有外部类引用，可访问外部所有成员 |
| 静态内部类 | `static class` | 不持有外部类引用，相当于顶层类 |
| 局部内部类 | 方法内 | 作用域局限，可访问 `final`/`effectively final` 变量 |
| 匿名内部类 | `new 接口/类(){}` | 一次性实现，Lambda 的替代 |

### 5.2 为什么局部内部类要求变量是 final？

```java
void process() {
    int x = 10;  // 必须是 final 或 effectively final
    new Thread(() -> System.out.println(x)).start();
}
```

原因：内部类对象存活时，外部方法栈帧可能已弹出，局部变量已销毁。Java 通过复制（capture）机制将变量复制到内部类中，复制版与原始版必须一致，要求 `final` 防止不一致。

---

## 六、Lambda 表达式

Lambda 是函数式接口的实例，语法：`(参数) -> { 方法体 }`。

### 6.1 常用函数式接口

| 接口 | 输入 | 输出 | 用途 |
|------|------|------|------|
| `Predicate<T>` | T | boolean | 过滤 |
| `Function<T,R>` | T | R | 转换 |
| `Consumer<T>` | T | void | 消费 |
| `Supplier<T>` | 无 | T | 生产 |
| `UnaryOperator<T>` | T | T | 同类型转换 |
| `BinaryOperator<T>` | T,T | T | 合并 |

### 6.2 方法引用

| 形式 | 示例 | 等价 Lambda |
|------|------|-------------|
| 静态方法 | `Integer::parseInt` | `s -> Integer.parseInt(s)` |
| 实例方法 | `String::length` | `s -> s.length()` |
| 对象方法 | `this::process` | `x -> this.process(x)` |
| 构造器 | `ArrayList::new` | `() -> new ArrayList<>()` |

---

## 七、Stream API

### 7.1 流水线三阶段

```
源数据 → 中间操作（惰性） → 终端操作（触发执行）
```

### 7.2 常用操作

```java
List<Order> orders = ...;

// 过滤 + 映射 + 排序 + 收集
List<String> names = orders.stream()
    .filter(o -> o.getAmount() > 100)          // 中间：过滤
    .map(Order::getCustomerName)               // 中间：转换
    .distinct()                                // 中间：去重
    .sorted()                                  // 中间：排序
    .collect(Collectors.toList());             // 终端：收集

// 分组
Map<String, List<Order>> byCategory = orders.stream()
    .collect(Collectors.groupingBy(Order::getCategory));

// 分区
Map<Boolean, List<Order>> partitioned = orders.stream()
    .collect(Collectors.partitioningBy(o -> o.getAmount() > 500));

// 并行流
long sum = orders.parallelStream()
    .mapToLong(Order::getAmount)
    .sum();
```

### 7.3 并行流注意事项

- `parallelStream` 使用 `ForkJoinPool.commonPool()`，默认线程数 = CPU 核心数
- 不适合 IO 密集型操作，适合 CPU 密集型纯计算
- 线程池不可自定义（Java 20+ 允许自定义，需注意版本）

---

## 八、面试高频题

| 题目 | 核心要点 |
|------|---------|
| 接口和抽象类怎么选？ | 有状态/构造器逻辑 → 抽象类；行为契约 → 接口 |
| 内部类为什么持有外部类引用？ | 成员内部类隐式持有 `this$0`，可能导致内存泄漏 |
| Lambda 表达式捕获了什么？ | 捕获了 `final`/`effectively final` 的局部变量副本 |
| Stream 为什么支持惰性求值？ | 中间操作不执行，终端操作触发；避免遍历中间结果 |
| 并行流能用吗？ | 数据量大且无状态依赖时可用，注意线程池不可控 |

> 掌握 OOP 核心后，进入集合框架篇：从 ArrayList 到 ConcurrentHashMap 的源码级分析。