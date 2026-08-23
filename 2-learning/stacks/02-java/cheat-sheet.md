# Java 速查卡 📋

> 面试前30分钟扫一遍，一页纸覆盖核心考点

## 🏷️ 核心概念速记

| 概念 | 一句话解释 | 常见陷阱 |
|------|-----------|----------|
| JVM 内存区 | 堆(Heap)存对象、栈(Stack)存引用/基本类型、方法区(Metaspace)存类信息、PC寄存器、本地方法栈 | 栈不是存对象的！堆才存对象实例 |
| 堆区 | 新生代(Young/Eden+S0+S1)+老年代(Old) | G1 分区不分代概念，但逻辑上仍是分代 GC |
| 方法区(Metaspace) | 类元信息、常量、静态变量(JDK8+在堆外) | 元空间取代永久代，默认无上限，容易踩 OOM |
| GC 算法 | 标记-清除(碎片多)、标记-复制(新生代)、标记-整理(老年代) | G1 默认用复制，CMS 用标记清除(碎片) |
| CMS | 低停顿并发收集，初始标记→并发标记→重新标记→并发清除 | 浮动垃圾 + CPU 敏感 + 内存碎片 |
| G1 | 分区收集，可预测停顿，JDK9+ 默认 | Region 大对象分配(Humongous)占用连续 Region |
| ZGC | 染色指针+读屏障，亚毫秒停顿，JDK21+ 生产级 | 堆越大优势越明显，小堆不如 G1 |
| 集合体系 | List(有序可重复)、Set(不可重复)、Map(key-value) | HashMap 线程不安全，new ArrayList<>(100) 容量是 100 不是 10 |
| HashMap | 数组+链表+红黑树；扩容2倍；负载因子0.75 | 并发 put 死循环(JDK7)/数据丢失(JDK8) |
| ConcurrentHashMap | 分段锁(CAS+synchronized)，扩容时读并发 | size() 不精确，forEach 不保证顺序 |
| synchronized | 偏向锁→轻量锁→重量锁(锁升级) | 锁降级只在 GC 时发生，正常不会降级 |
| ReentrantLock | 可中断、可超时、支持 Condition、公平/非公平 | tryLock 一定要 finally unlock |
| AQS | AbstractQueuedSynchronizer，JUC 锁的基石，CLH 队列+CAS 状态 | 模板方法模式：tryAcquire/tryRelease 子类实现 |
| 线程池 | corePoolSize → workQueue → maxPoolSize → 拒绝策略 | 队列满才创建核心外的线程，别搞反顺序 |
| NIO | Channel + Buffer + Selector 多路复用 | 直接内存 DirectBuffer 需手动释放，ByteBuffer 的 flip 易忘 |
| 虚拟线程(JDK21+) | 轻量级协程，百万级线程不阻塞 OS 线程 | 同步代码块/synchronized 会钉住(pin)载体线程，pinned 时不释放 |

## 🔧 常用命令/API

```java
// HashMap put 流程（关键考点）
// 1. 计算 hash(key) 高16位异或低16位
// 2. (n-1) & hash 定位桶位置
// 3. 桶空 → 直接 new Node
// 4. 桶不空 → 遍历链表/红黑树，key 相同覆盖，否则尾插
// 5. 链表长度 >=8 且容量 >=64 → 树化；<6 → 退化为链表
// 6. 元素数 > 阈值(capacity*0.75) → 扩容2倍，rehash
```

```java
// 线程池参数（面试默写题）
ThreadPoolExecutor executor = new ThreadPoolExecutor(
        2,               // corePoolSize: 核心线程数
        5,               // maxPoolSize: 最大线程数
        60L,             // keepAliveTime: 超时回收
        TimeUnit.SECONDS,
        new LinkedBlockingQueue<>(100),  // workQueue: 任务队列
        Executors.defaultThreadFactory(),
        new ThreadPoolExecutor.AbortPolicy()  // 拒绝策略: 抛异常
);
// 执行流程: core → queue → max → reject
```

```java
// CompletableFuture 异步编排
CompletableFuture.supplyAsync(() -> queryUser(id))
    .thenApplyAsync(user -> enrich(user))
    .exceptionally(e -> {
        log.error("failed", e);
        return defaultUser();
    })
    .thenAccept(System.out::println);
```

```java
// 虚拟线程（JDK21+）
try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
    executor.submit(() -> {
        // 耗时 IO 操作，不阻塞 OS 线程
        Thread.sleep(1000);
        return "done";
    });
}
```

```bash
# JVM 常用排查命令
jps -l                        # 查看 Java 进程
jstack <pid>                  # 打印线程栈（死锁定位）
jmap -heap <pid>              # 堆内存概况
jmap -histo:live <pid>        # 存活对象统计
jstat -gcutil <pid> 1000 10   # GC 情况 1s 一次共 10 次
jcmd <pid> VM.native_memory   # NMT 本地内存追踪
```

## 🎯 面试高频 TOP10

1. **Q: HashMap 1.7 vs 1.8 区别？** **A:** 1.7 头插(死锁)、1.8 尾插+红黑树；1.7 数组+链表、1.8 数组+链表+红黑树；1.7 扩容重算 hash、1.8 用原位置/原位置+oldCap 规律。
2. **Q: ConcurrentHashMap 如何保证线程安全？** **A:** 1.7 分段锁(16段)；1.8 舍弃分段，用 CAS 插入 + synchronized 锁链表头节点，扩容时多线程协助迁移。
3. **Q: synchronized 锁升级过程？** **A:** 无锁→偏向锁(单线程争用)→轻量锁(CAS 自旋)→重量锁(OS 互斥量)；锁降级在 GC 时发生，日常不会降。
4. **Q: G1 和 ZGC 区别？** **A:** G1 分区+停顿预测，GC 线程并发 vs 停顿；ZGC 染色指针+读屏障+并发，停顿<1ms 与堆大小无关；ZGC 适合大堆低延迟场景。
5. **Q: 虚拟线程和平台线程区别？** **A:** 虚拟线程是 JVM 管理的协程，百万级；平台线程是 OS 线程池；虚拟线程在 IO 阻塞时自动让出 Carrier 线程，但 synchronized 会 pin 住载体。
6. **Q: 线程池拒绝策略有哪些？** **A:** AbortPolicy(抛异常)、CallerRunsPolicy(调用者线程执行)、DiscardPolicy(静默丢弃)、DiscardOldestPolicy(丢弃最老任务)。
7. **Q: ThreadLocal 原理和内存泄漏？** **A:** 每个 Thread 有 ThreadLocalMap，key 是弱引用，value 是强引用；不 remove 会导致 value 一直存在，造成内存泄漏，用完必须 remove。
8. **Q: AQS 原理？** **A:** 状态 volatile int state + CLH 队列(CAS 入队/自旋/阻塞) + 模板方法 tryAcquire/tryRelease；ReentrantLock/CountDownLatch/Semaphore 都基于 AQS。
9. **Q: NIO 的三大组件？** **A:** Channel(双向通道)、Buffer(内存缓冲区)、Selector(多路复用器，一个线程管理多个 Channel 的事件)。
10. **Q: Java 内存模型 JMM 是什么？** **A:** 定义线程内存和主内存间的抽象，happens-before 规则保证可见性(volatile/synchronized/final)，解决指令重排序问题。

## ⚠️ 常见坑 & 最佳实践

| ❌ 坑 | ✅ 正确做法 |
|-------|------------|
| HashMap 并发 put | 用 ConcurrentHashMap，而不是 Collections.synchronizedMap |
| Executors.newFixedThreadPool 默认 Integer.MAX_VALUE 队列 | 自定义 ThreadPoolExecutor 显式指定队列大小和拒绝策略 |
| 线程池 submit(Callable) 忽略异常 | 用 execute 或 Future.get() 捕获异常，或 setUncaughtExceptionHandler |
| 大量使用 synchronized 方法 | 缩小锁粒度，用 ReentrantLock + Condition 或读写锁替代 |
| 未复写 hashCode 只复写 equals | 两个字段都复写，HashSet/HashMap 才正常工作 |
| 异步任务用 Thread 直接 new | 用线程池/CompletableFuture/虚拟线程管理生命周期 |
| 大对象频繁 GC | 考虑直接内存/池化/外部缓存，避免频繁 Young GC 晋升 |
| finally 里 return 覆盖异常 | finally 不 return，异常要么抛要么录日志 |
| 循环里 new 大量短期对象 | 考虑对象池或局部变量复用，减少 GC 压力 |

## 📐 架构设计要点

- **JVM 调优三步走**：明确业务场景(计算/IO/实时) → 选 GC(G1/ZGC) → 压测调参数(-Xms=-Xmx/新老比/GC 线程数)。
- **并发设计原则**：能用不可变对象就别用锁、能用乐观锁不用悲观锁、能用局部变量不用共享变量、缩小锁范围。
- **线程池隔离**：CPU 密集型(核数+1) 和 IO 密集型(核数*2+) 分开线程池，防止互相影响。
- **异步编程**：CompletableFuture 编排 → 虚拟线程简化 IO 密集场景 → 响应式(Reactor/RxJava) 可选但学习成本高。
- **SPI 机制**：接口 + META-INF/services 实现服务发现，Spring Boot 自动配置底层依赖它。

## 🔗 关联技术

- **Spring Boot**：基于 Java 的框架，依赖注入、AOP、自动配置都依赖 Java 反射和注解。
- **MySQL**：JDBC 连接池(HikariCP)、事务管理、MyBatis 映射。
- **Redis**：Jedis/Lettuce 客户端，Java 调用 Redis 实现分布式锁和缓存。
- **RocketMQ**：JMS 思想，Java 客户端生产消费消息，事务消息依赖多线程和状态机。
- **K8s**：Java 应用容器化，JVM 在容器内的内存/CPU 感知需加 -XX:UseContainerSupport。