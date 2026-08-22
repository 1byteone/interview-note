# Java 新特性 — Java 8 到 21 的重要演进

> 等级：🎯 面试进阶
> 目标：掌握 Java 8-21 的核心新特性，重点学会 Record、Sealed Class、Virtual Threads 的使用与原理。

---

## 一、Java 8 核心新特性

Java 8 是 Java 历史上最重大的版本，至今仍是企业存量主力。

### 1.1 Lambda 表达式与函数式接口

```java
// 旧写法
new Thread(new Runnable() {
    @Override public void run() { System.out.println("Hello"); }
}).start();

// Lambda
new Thread(() -> System.out.println("Hello")).start();

// 内置函数式接口
list.stream().filter(x -> x > 5).map(String::valueOf).collect(Collectors.toList());
```

### 1.2 Stream API

- 声明式处理集合，支持惰性求值、并行处理
- 源码依赖 `Spliterator` 实现并行迭代

### 1.3 Optional

```java
// 避免 NPE
Optional.ofNullable(user)
    .map(User::getAddress)
    .map(Address::getCity)
    .orElse("未知");
```

### 1.4 新日期 API

```java
LocalDate date = LocalDate.now();
LocalDateTime dt = LocalDateTime.of(2026, 8, 22, 10, 30);
Instant now = Instant.now();  // 时间戳（UTC）
Duration.between(start, end); // 时间差
Period.between(startDate, endDate); // 日期差
```

---

## 二、Java 9-11 新特性

### 2.1 模块化（JPMS, Java 9）

- `module-info.java` 定义模块的导出和依赖
- 实际应用：Spring Boot 3 开始支持模块化，但多数项目未启用

### 2.2 接口私有方法（Java 9）

```java
public interface Service {
    default void process() {
        log();  // 调用私有方法
    }
    private void log() { System.out.println("log"); }
}
```

### 2.3 var 局部变量推断（Java 10）

```java
var list = new ArrayList<String>();  // 编译器推断 ArrayList<String>
// 注意：var 不能用于方法参数、返回值、成员变量
```

### 2.4 HTTP Client（Java 11）

```java
var client = HttpClient.newHttpClient();
var request = HttpRequest.newBuilder()
    .uri(URI.create("https://api.example.com"))
    .GET()
    .build();
var response = client.send(request, HttpResponse.BodyHandlers.ofString());
System.out.println(response.body());
```

---

## 三、Java 14-17 新特性

### 3.1 Record（Java 14 preview, 16 正式化）

Record 是**不可变数据载体**，自动生成构造器、`equals`、`hashCode`、`toString`：

```java
// 旧写法：需要 IDE 生成大量代码
public class User {
    private final String name;
    private final int age;
    // 构造器、getter、equals、hashCode、toString...
}

// Record 写法：一行搞定
public record User(String name, int age) {}

// 使用
var user = new User("张三", 25);
user.name();   // 注意：不是 getName()，而是 name()
```

**Record 的限制**：
- 不能继承其他类（隐式继承 `java.lang.Record`）
- 字段隐式为 `private final`
- 可以添加静态方法、实例方法，但不能添加实例字段

**Record 的压缩对象头**：JDK 16+ 的 JVM 对 Record 做了优化，内存占用比普通类更小。

### 3.2 Sealed Class（密封类，Java 17 正式化）

限制哪些类可以继承，实现更精确的代数数据类型：

```java
public sealed class Shape permits Circle, Rectangle, Triangle {
    // 只有 Circle、Rectangle、Triangle 可以继承 Shape
}

// 子类必须是 final、sealed 或 non-sealed
final class Circle extends Shape { ... }
sealed class Rectangle extends Shape permits Square { ... }
non-sealed class Triangle extends Shape { ... }
```

**面试问题**：为什么需要 Sealed Class？

- 更精确的领域建模，编译器知道所有子类
- 配合 `switch` 模式匹配实现穷举检查

### 3.3 Switch 模式匹配（Java 17 正式化，Java 21 增强）

```java
// 旧写法
String result;
switch (obj) {
    case Integer i: result = "int " + i; break;
    case String s:  result = "string " + s; break;
    default:        result = "unknown";
}

// 新写法（Java 17+）
String result = switch (obj) {
    case Integer i -> "int " + i;
    case String s  -> "string " + s;
    case null      -> "null";       // Java 17 支持 null 匹配
    default        -> "unknown";
};

// Java 21 Guarded Pattern + Record Pattern
String result = switch (obj) {
    case User(var name, var age) when age > 18 -> "成年用户: " + name;
    case User(var name, var age)               -> "未成年用户: " + name;
    case null                                  -> "null";
    default                                    -> "其他";
};
```

### 3.4 文本块（Text Block, Java 13 preview, 15 正式化）

```java
String html = """
    <html>
        <body>
            <p>Hello, World</p>
        </body>
    </html>
    """;
```

---

## 四、Java 21 核心新特性

### 4.1 Virtual Threads（虚拟线程）

虚拟线程是 JDK 21 的**杀手级特性**，彻底改变了 Java 的并发编程模型。

**背景**：传统物理线程（Platform Thread）是 OS 线程的包装，数量受限于 OS（通常数千），大量线程切换导致上下文切换开销巨大。虚拟线程是 JVM 管理的轻量线程，数量可达百万级。

```java
// 传统线程池
ExecutorService pool = Executors.newFixedThreadPool(200);
pool.submit(() -> handle(request));

// 虚拟线程（JDK 21）
try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
    executor.submit(() -> handle(request));
}

// 或者直接创建
Thread.startVirtualThread(() -> {
    // 每个请求一个虚拟线程，无需池化
    handle(request);
});
```

**原理**：

```
Platform Thread (载体线程) → 执行 → Virtual Thread
                                      ↓ 阻塞（IO/sleep/lock）
                           Virtual Thread 从载体线程中卸载（yield）
                           载体线程去执行其他 Virtual Thread
                                      ↓ IO 完成
                           Virtual Thread 重新挂载到载体线程（可能不同）
```

- **M:N 调度模型**：M 个虚拟线程映射到 N 个物理线程
- **载体线程**（Carrier Thread）：ForkJoinPool 中的物理线程
- **挂载/卸载**（Mount/Unmount）：虚拟线程阻塞时自动从载体线程卸载

**注意事项**：
- 虚拟线程不适用于 CPU 密集型任务（计算密集型仍用物理线程）
- 避免 `synchronized` 固定（pinning）虚拟线程到载体线程，用 `ReentrantLock` 替代
- 虚拟线程不需要池化，每个任务创建新虚拟线程即可

### 4.2 SequencedCollection（有序集合接口）

```java
// 新增接口：SequencedCollection、SequencedSet、SequencedMap
SequencedCollection<String> list = new ArrayList<>();
list.addFirst("a");
list.addLast("b");
list.getFirst();     // 新增
list.getLast();      // 新增
list.reversed();     // 反向视图
```

### 4.3 其他重要特性

- **Record Patterns**（正式化）：在 Record 上做模式匹配
- **Pattern Matching for switch**（正式化）
- **Foreign Function & Memory API**（预览）：替代 JNI
- **Scoped Values**（孵化）：替代 ThreadLocal 的新方案

---

## 五、面试高频题

| 题目 | 核心要点 |
|------|---------|
| Record 和 Lombok 的 `@Data` 区别？ | Record 是 Java 语言特性，不可变，自动实现 equals/hashCode；Lombok 是编译期注解处理，可变 |
| 虚拟线程和平台线程区别？ | 虚拟线程由 JVM 调度，轻量（KB 级），适合 IO 密集型；平台线程由 OS 调度，重量（MB 级） |
| 虚拟线程池化吗？ | 不需要池化，虚拟线程创建成本极低，每个任务创建新线程即可 |
| Sealed Class 的应用场景？ | 领域建模（如 Shape、Payment 类型）、状态机、配合编译期模式的穷举检查 |
| 为什么用 Sealed Class 而不用 enum？ | enum 只能列举单例，Sealed Class 的子类可以有多个实例 |

> 进入项目实战篇：AI 商城中 Java 核心的应用。