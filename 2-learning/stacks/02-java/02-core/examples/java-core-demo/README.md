# java-core-demo

Java 核心技术示例项目 —— 对应《02-java/02-core》教程章节。

纯标准 Java 项目（无任何第三方依赖），Java 17 编译。

## 目录结构

```
java-core-demo/
├── pom.xml                           # Maven 配置（Java 17）
└── src/main/java/com/example/
    ├── concurrency/
    │   ├── ThreadPoolDemo.java       # 线程池：ThreadPoolExecutor、Callable/Future、CompletableFuture
    │   └── ProducerConsumerDemo.java # 生产者-消费者：BlockingQueue、多线程协作
    ├── jvm/
    │   └── MemoryDemo.java           # JVM 内存：对象创建/GC、栈vs堆、String intern、OOM 模拟
    └── io/
        └── NioDemo.java              # NIO：FileChannel、ByteBuffer、Selector 多路复用
```

## 构建

```bash
mvn clean package
```

## 运行各示例

使用 `exec` 插件指定主类运行：

```bash
# 线程池与 CompletableFuture
mvn -q exec:java -Dexec.mainClass=com.example.concurrency.ThreadPoolDemo

# 生产者-消费者模式
mvn -q exec:java -Dexec.mainClass=com.example.concurrency.ProducerConsumerDemo

# JVM 内存模型
mvn -q exec:java -Dexec.mainClass=com.example.jvm.MemoryDemo

# NIO 示例
mvn -q exec:java -Dexec.mainClass=com.example.io.NioDemo
```

## 各文件演示的知识点

| 文件 | 知识点 |
|------|--------|
| ThreadPoolDemo | 自定义 ThreadPoolExecutor 参数、Callable+Future、CompletableFuture 编排（thenCompose/thenCombine/allOf）、异常处理 |
| ProducerConsumerDemo | ArrayBlockingQueue、多生产者多消费者、优雅关闭、AtomicInteger、volatile |
| MemoryDemo | 对象创建与 GC 机制、栈 vs 堆分配、String 常量池 intern()、OOM 模拟（默认注释） |
| NioDemo | FileChannel 读写、ByteBuffer 四大属性（position/limit/capacity）、Scatter/Gather、直接缓冲区、Selector 非阻塞模型 |

## 运行建议

- JVM 内存演示建议加 VM 参数：`-Xmx256m -Xms256m -XX:+PrintGCDetails`
- NIO 的 Selector 演示为原理说明，完整的 EchoServer 在注释中提供
- 生产者消费者演示控制台输出较多，建议用 `-q` 参数减少日志