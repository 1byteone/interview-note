# Java 快速入门 — 版本演进 · 环境搭建 · 基础语法

> 等级：👶 新手通道
> 目标：从零开始，跑通第一个 Java 程序，并检视面试高频的基础语法点。

---

## 一、Java 发展史与版本特性

Java 最初由 Sun 公司于 1995 年发布，2010 年被 Oracle 收购。经历了经典的演进路径：

| 版本 | 发布时间 | 关键特性 | 现状 |
|------|----------|----------|------|
| Java 8 | 2014 | **Lambda、Stream、Optional、新日期 API** | 企业生产主力，仍是存量最大 |
| Java 11 | 2018 | LTS、var 局部变量、HTTP Client、ZGC 引入 | 第二 LTS，Spring Boot 3 最低要求 |
| Java 17 | 2021 | LTS、Sealed Class、Record 正式化、增强 Switch | 当前主流新项目首选 |
| Java 21 | 2023 | LTS、**Virtual Threads（虚拟线程）**、Record Patterns | 新一代 LTS，高并发利器 |

面试高频问题往往是：

> **"Java 8 和 Java 17 有什么主要区别？""你会给新项目选哪个版本？""

参考回答：生产环境追求稳定选 17（LTS），并开启"record + sealed + switch 模式匹配"；追求极致并发吞吐、需要海量轻量线程时上 21 的虚拟线程。多数公司仍运行 8，所以 8 的语法必须熟练。

---

## 二、环境搭建

### 2.1 安装 JDK

1. 从 [Adoptium](https://adoptium.net) 下载 OpenJDK（或 Oracle JDK）
2. 配置环境变量：
   - `JAVA_HOME` = JDK 安装目录
   - `PATH` 追加 `%JAVA_HOME%\bin`
3. 验证安装：

```bash
java -version      # 输出 JDK 版本
javac -version     # 验证编译器
```

### 2.2 IDE 选择

- **IntelliJ IDEA**：社区版免费，企业项目主流，支持 Maven/Gradle/调试一应俱全
- **Eclipse**：老牌免费，生态成熟但体验稍旧
- **VSCode + Java 插件**：轻量，适合快速编辑

### 2.3 第一个 Java 程序

```java
public class Hello {
    public static void main(String[] args) {
        System.out.println("Hello, Java 核心!");
    }
}
```

编译运行两步走：

```bash
javac Hello.java    # 编译生成 Hello.class 字节码
java Hello          # JVM 加载执行
```

> 记忆点：`javac` 产出字节码，`java` 启动 JVM。这也是 JVM 类加载机制的入口。

---

## 三、基础语法要点

### 3.1 面向对象（OOP）

- **封装**：`private` 字段 + getter/setter，隐藏内部实现
- **继承**：`extends` 单继承，子类复用父类能力
- **多态**：父类引用指向子类对象，`@Override` 方法在运行时动态绑定

```java
abstract class Animal { abstract void speak(); }
class Dog extends Animal { @Override void speak() { System.out.println("汪"); } }
Animal a = new Dog();  // 多态
a.speak();             // 运行时确定调用 Dog.speak()
```

### 3.2 接口（Interface）

- Java 8 后接口可以有 `default` 和 `static` 方法
- 类是单继承、接口可以多实现 —— 这是接口相比抽象类的最大价值

### 3.3 泛型（Generics）

- 编译期类型检查，运行期**类型擦除**（擦除为 Object 或上界）
- 通配符 `? extends T`（上界，读安全）与 `? super T`（下界，写安全）—— **PECS 原则**

```java
List<? extends Number> nums = new ArrayList<Integer>(); // 只能读
List<? super Integer> ints = new ArrayList<Number>();   // 只能写
```

### 3.4 注解（Annotation）

- 元注解：`@Retention`（SOURCE/CLASS/RUNTIME）、`@Target`、`@Documented`、`@Inherited`
- 运行期注解（`@Retention(RUNTIME)`）才有反射读取的价值，Spring 大量依赖它

### 3.5 异常体系

```
Throwable
├── Error          ← 无法恢复：OOM、StackOverflow、NoClassDefFound
└── Exception
    ├── RuntimeException  ← 非受检：NPE、ArrayIndexOutOfBounds
    └── 受检异常          ← 必须处理：IOException、SQLException
```

最佳实践：业务异常用自定义运行时异常；`finally` 中不放 `return`；用 try-with-resources 自动关闭资源：

```java
try (var reader = Files.newBufferedReader(path)) {
    // 自动 close
} catch (IOException e) {
    log.error("读取失败", e);
}
```

---

## 四、面试高频基础题

| 题目 | 一句话答案 |
|------|-----------|
| `==` 和 `equals` 区别？ | `==` 比较引用地址，`equals` 默认同 `==`，但包装类/String 重写为比较值 |
| String 为什么不可变？ | `final` 修饰 char[]、缓存 hashCode、保证线程安全、支持字符串常量池 |
| String vs StringBuilder vs StringBuffer？ | 前者不可变；后两者可变，StringBuilder 线程不安全但更快 |
| 重载和重写的区别？ | 重载编译期多态（相同方法名不同参数），重写运行期多态（子类覆盖父类） |
| `int` 和 `Integer` 区别？ | 基础类型无方法可空；包装类有常量池缓存 [-128,127] |
| 为什么要有包装类？ | 泛型不支持基础类型、集合只能存引用类型、可表达 null |
| `static` 和 `final` 修饰方法区别？ | static 归属类可被继承隐藏；final 禁止重写 |
| 深拷贝和浅拷贝？ | 浅拷贝只拷贝引用，深拷贝复制对象内容；用 `clone()` 或序列化实现 |

---

## 五、动手练习

1. 用 Stream 一行实现：过滤出列表中大于 10 的偶数并求和
2. 写一个自定义 `@ApiInfo` 注解，用反射读取并打印
3. 对比 `Integer a = 127; Integer b = 127; a == b` 与 `128` 场景的运行结果

> 掌握基础语法后，进入下一节：深入封装/继承/多态、接口与抽象类选择、Lambda 与 Stream。