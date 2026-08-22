# java-basics-demo

Java 基础语法示例项目 —— 对应《02-java/01-basics》教程章节。

纯标准 Java 项目（无任何第三方依赖），Java 17 编译，无需 Spring。

## 目录结构

```
java-basics-demo/
├── pom.xml                          # Maven 配置（Java 17）
└── src/main/java/com/example/
    ├── basics/
    │   ├── HelloWorld.java          # 入门：包、import、main、基本语法
    │   ├── OopDemo.java             # 面向对象：抽象类/接口/继承/多态/泛型/Record/密封类
    │   ├── LambdaStreamDemo.java    # Lambda 与 Stream API、方法引用、Optional
    │   └── ExceptionDemo.java       # 异常处理：try-catch-finally、try-with-resources、自定义异常
    └── collections/
        └── HashMapDemo.java         # Map 体系：HashMap 遍历、TreeMap 排序、ConcurrentHashMap
```

## 构建

```bash
mvn clean package
```

或直接编译运行（无需 Maven 也可）：

```bash
cd src/main/java
javac com/example/basics/*.java com/example/collections/*.java
java com.example.basics.HelloWorld
```

## 运行各示例

每个类都有独立的 `main` 方法，用 `exec` 插件指定主类运行：

```bash
# HelloWorld —— 入门语法
mvn -q exec:java -Dexec.mainClass=com.example.basics.HelloWorld

# 面向对象（抽象类/接口/继承/多态/泛型/Record/密封类）
mvn -q exec:java -Dexec.mainClass=com.example.basics.OopDemo

# Lambda 与 Stream API
mvn -q exec:java -Dexec.mainClass=com.example.basics.LambdaStreamDemo

# 异常处理
mvn -q exec:java -Dexec.mainClass=com.example.basics.ExceptionDemo

# 集合框架（HashMap/TreeMap/ConcurrentHashMap）
mvn -q exec:java -Dexec.mainClass=com.example.collections.HashMapDemo
```

编译后也可直接用 `java` 命令运行（需要 classpath 指向 target/classes 或源文件目录）：

```bash
# 先编译
mvn -q compile
# 再运行
java -cp target/classes com.example.basics.OopDemo
```

## 各文件演示的知识点

| 文件 | 知识点 |
|------|--------|
| HelloWorld | 包声明、import、main 方法、基本类型、String、时间 API |
| OopDemo | 抽象类、接口默认方法、继承、多态、泛型方法/类、Record（16+）、密封类（17+） |
| LambdaStreamDemo | 函数式接口、filter/map/reduce/collect/groupingBy、方法引用、Optional |
| ExceptionDemo | try-catch-finally、multi-catch、try-with-resources、自定义异常 |
| HashMapDemo | HashMap 增删改查、4 种遍历、TreeMap 排序、ConcurrentHashMap 线程安全 |

> 提示：`OopDemo` 中演示了 Java 17 的密封类（sealed class）与模式匹配 switch（`switch` 表达式），
> 需要 Java 17 及以上版本编译运行；`Record` 需要 Java 16 及以上。