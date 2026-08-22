# Java — 面试抽认卡

> 来源：`learn/02-java/05-interview/`

---

### Card 1: HashMap 扩容机制
**维度**: 🔬深挖 | **难度**: ⭐⭐

> **Q: HashMap 扩容时元素如何迁移？为什么扩容后元素要么在原位置，要么在原位置+旧容量？**

**A:** 扩容为原大小的 2 倍，`n` 变为 `2n`。新索引计算 `hash & (2n-1)`，原来 `hash & (n-1)`，高位多了一位。如果多出的那一位是 0，新索引不变；是 1，新索引 = 原索引 + 旧容量。通过 `e.hash & oldCap` 判断：0 则原位，非 0 则位移。JDK 1.8 避免了 1.7 的 rehash 性能损耗。

---

### Card 2: ConcurrentHashMap 1.7 vs 1.8
**维度**: 🔬深挖 | **难度**: ⭐⭐⭐

> **Q: ConcurrentHashMap 1.7 和 1.8 的实现有什么区别？1.8 为何放弃分段锁？**

**A:** 1.7 使用 Segment 数组 + HashEntry，分段锁继承 ReentrantLock，锁粒度是 Segment（默认 16 个）。1.8 使用 Node 数组 + 链表/红黑树，CAS + synchronized 锁桶（Node），锁粒度更细。1.8 放弃分段锁是因为：① 桶粒度并发度更高（16 段 → 桶数）；② CAS 无锁操作更轻量；③ 红黑树优化哈希冲突。

---

### Card 3: synchronized 锁升级过程
**维度**: 🔬深挖 | **难度**: ⭐⭐⭐

> **Q: synchronized 在 JVM 中的锁升级过程是怎样的？为什么要有偏向锁？**

**A:** 锁升级：无锁 → 偏向锁 → 轻量级锁 → 重量级锁（不可逆）。偏向锁：Mark Word 存线程 ID，同一线程重入无 CAS 开销，适合单线程竞争场景。轻量级锁：CAS 自旋抢锁，适合短时间锁。重量级锁：OS 互斥量，阻塞线程，适合长时间锁。JDK 15 起默认禁用偏向锁（维护成本高，且虚拟线程时代不适用）。

---

### Card 4: AQS 原理
**维度**: 🔬深挖 | **难度**: ⭐⭐⭐

> **Q: AQS 的核心机制是什么？ReentrantLock 如何利用 AQS 实现锁？**

**A:** AQS 核心是 `volatile int state` + CLH 双向队列。`state` 表示资源状态（0 空闲，>0 被占用），通过 CAS 修改。CLH 队列存储等待线程，每个节点封装线程和等待状态。ReentrantLock 的非公平锁：CAS 直接抢 state，失败则入队。公平锁：先检查队列是否有前驱节点，有则排队，无则 CAS 抢锁。

---

### Card 5: 线程池参数
**维度**: 📝速记 | **难度**: ⭐

> **Q: 线程池的 7 个核心参数是什么？工作流程是怎样的？**

**A:** `corePoolSize`（核心线程数）、`maxPoolSize`（最大线程数）、`keepAliveTime`（空闲存活时间）、`workQueue`（阻塞队列）、`threadFactory`（线程工厂）、`handler`（拒绝策略）。流程：核心线程满 → 任务入队 → 队列满 → 创建非核心线程 → 队列满+线程满 → 拒绝策略。拒绝策略：AbortPolicy（抛异常）、CallerRunsPolicy（调用者执行）、DiscardPolicy、DiscardOldestPolicy。

---

### Card 6: CompletableFuture 异步编排
**维度**: 💻代码 | **难度**: ⭐⭐

> **Q: CompletableFuture 如何实现任务编排？常用方法有哪些？**

**A:** 常用方法：`supplyAsync`（异步提交）、`thenApply`（同步转换）、`thenCompose`（异步扁平化）、`thenCombine`（两个结果合并）、`allOf`（等待全部完成）、`anyOf`（任意一个完成）。示例：`CompletableFuture.supplyAsync(() -> queryPrice()).thenCombine(CompletableFuture.supplyAsync(() -> queryStock()), (price, stock) -> new Product(price, stock))`。优点：避免回调地狱，支持异常处理 `exceptionally`。

---

### Card 7: JVM 运行时数据区
**维度**: 📝速记 | **难度**: ⭐

> **Q: JVM 运行时数据区包含哪些部分？哪些是线程共享，哪些是线程私有？**

**A:** 线程共享：堆（Heap，存对象实例）、方法区（Method Area，存类信息/常量/静态变量，JDK 8 后元空间实现）。线程私有：虚拟机栈（栈帧存局部变量表/操作数栈）、本地方法栈（Native 方法）、程序计数器（PC，当前线程执行字节码行号）。私有区域随线程生灭，无需 GC。

---

### Card 8: GC 算法对比
**维度**: 🔬深挖 | **难度**: ⭐⭐

> **Q: 标记-清除、标记-整理、复制算法各自的优缺点是什么？**

**A:** ① 标记-清除：标记可达对象→清除未标记，产生内存碎片，适合老年代 CMS；② 标记-整理：标记→整理到一端，消除碎片但需要移动对象，适合老年代 Parallel Old；③ 复制算法：将内存分为两块，只使用一块，存活对象复制到另一块，效率高但有空间浪费，适合新生代（Eden:S0:S1=8:1:1，只浪费 10%）。

---

### Card 9: G1 vs ZGC
**维度**: 🔬深挖 | **难度**: ⭐⭐⭐

> **Q: G1 和 ZGC 垃圾收集器的主要区别是什么？分别适合什么场景？**

**A:** G1：Region 化（1-32MB），可预测停顿（-XX:MaxGCPauseMillis），JDK 9 默认。ZGC：染色指针（64bit 指针存元数据），Region 大小动态（2MB/32MB/N×2MB），STW 亚毫秒级（<10ms），支持几 TB 堆。G1 适合 4-64GB 堆、可接受百毫秒停顿；ZGC 适合超大堆（几百 GB）、要求低延迟的场景。JDK 21 后 ZGC 默认支持分代。

---

### Card 10: 虚拟线程
**维度**: 📝速记 | **难度**: ⭐⭐

> **Q: 虚拟线程（Virtual Threads）是什么？它能替代线程池吗？**

**A:** 虚拟线程是 JDK 21 正式发布的轻量级线程，由 JVM 管理而非 OS，创建成本极低（百万级）。遇到阻塞操作（IO、锁）时自动挂起，释放载体线程。适合 IO 密集型场景，每个请求一个虚拟线程，无需线程池复用。不适合 CPU 密集型（计算在载体线程执行，无法卸到其他线程）。

---

### Card 11: Stream API 与并行流
**维度**: 💻代码 | **难度**: ⭐⭐

> **Q: Stream API 的中间操作和终端操作有什么区别？使用并行流需要注意什么？**

**A:** 中间操作（filter/map/sorted）是惰性的，构建操作流水线，不触发计算。终端操作（collect/forEach/reduce）触发实际计算。并行流 `parallelStream()` 使用 ForkJoinPool 公共线程池，适合大集合、无状态、CPU 密集型操作。注意：① 数据竞争（非线程安全容器）；② 顺序依赖（findFirst 在并行流中性能差）；③ 阻塞操作会阻塞整个池。

---

### Card 12: Optional 用法
**维度**: 💻代码 | **难度**: ⭐

> **Q: Optional 的正确用法是什么？常见的坑有哪些？**

**A:** 正确用法：作为返回值提示可能为空，强制调用方处理。常用方法：`orElse`、`orElseGet`（惰性求值）、`map`、`flatMap`、`filter`。常见坑：① 不要用 `Optional.of` 传入 null（抛 NPE）；② 不要用作字段类型（不可序列化）；③ 不要用作方法参数（增加复杂度）；④ `orElse` 在值为 null 时也会执行参数表达式（用 `orElseGet` 替代）。

---

### Card 13: Record 类
**维度**: 📝速记 | **难度**: ⭐

> **Q: Java 14+ 的 Record 解决了什么问题？对比 Lombok 有什么优势？**

**A:** Record 是不可变数据载体，自动生成构造器、`equals`/`hashCode`/`toString`/getter。对比 Lombok：① 语言原生，无需编译期注解处理；② 语义明确（语义=数据载体）；③ 不可变性保证（所有字段 `private final`）；④ 支持模式匹配（JDK 21+）。缺点：不能继承、不能添加实例字段（只能加静态字段或方法）。

---

### Card 14: Sealed Class (密封类)
**维度**: 📝速记 | **难度**: ⭐⭐

> **Q: 密封类（Sealed Class）的作用是什么？如何使用？**

**A:** 密封类限制哪些类可以继承或实现它，提供更精确的继承控制。语法：`sealed class Shape permits Circle, Rectangle`，子类必须声明为 `final`、`sealed` 或 `non-sealed`。配合 `switch` 模式匹配（JDK 21+）使用，编译器可穷举所有子类型，无需 `default` 分支。用于领域建模（如表达式 AST、状态机）。

---

### Card 15: 泛型擦除与桥接方法
**维度**: 🔬深挖 | **难度**: ⭐⭐

> **Q: Java 泛型是编译期还是运行期？什么是类型擦除？桥接方法的作用？**

**A:** Java 泛型是编译期，运行期擦除为原始类型（如 `List<String>` → `List`）。擦除后必要的强制转换由编译器生成。桥接方法：当子类重写父类泛型方法时，编译器生成一个返回 Object 的桥接方法，内部调用子类具体类型的方法，保持多态。例如父类 `Comparable<T>` 的 `compareTo(T)`，子类 `compareTo(String)`，编译器生成 `compareTo(Object)` 桥接。

---

### Card 16: 反射性能开销
**维度**: 🔬深挖 | **难度**: ⭐⭐

> **Q: 反射为什么慢？如何优化反射性能？**

**A:** 慢的原因：① 动态类型检查（每次调用检查方法签名、访问权限）；② 自动装箱/拆箱；③ 方法对象无法被 JIT 内联；④ 权限检查。优化：① `setAccessible(true)` 跳过安全检查；② 缓存 `Method` 对象（避免重复查找）；③ `MethodHandle`（轻度反射，JIT 可内联）；④ `LambdaMetafactory` 生成调用点（接近直接调用性能）。

---

### Card 17: Java 序列化与反序列化
**维度**: 📝速记 | **难度**: ⭐⭐

> **Q: Java 序列化的原理是什么？有什么安全风险？如何替代？**

**A:** 序列化将对象转为字节流，通过 `ObjectOutputStream.writeObject` 实现，利用反射递归遍历对象图。`serialVersionUID` 用于版本控制。安全风险：反序列化可执行任意代码（如 `Runtime.exec` 在 `readObject` 中触发）。替代方案：① JSON（Jackson/Gson，文本格式，安全）；② Protobuf（二进制，跨语言，性能好）；③ Kryo（Java 专用，比原生快 10 倍）。

---

### Card 18: NIO Selector 原理
**维度**: 🔬深挖 | **难度**: ⭐⭐⭐

> **Q: NIO 的 Selector 是如何实现多路复用的？select/poll/epoll 有什么区别？**

**A:** Selector 注册 Channel 感兴趣的事件（OP_ACCEPT/OP_READ/OP_WRITE），`select()` 阻塞等待就绪事件。Linux 上：select（1024 连接限制，O(n) 轮询），poll（无限制，O(n) 轮询），epoll（事件驱动，O(1)，注册回调，仅就绪的返回）。epoll 优势：① 无连接数限制；② 无需遍历所有 fd；③ 内存映射减少拷贝。Java NIO 在 Linux 默认使用 epoll。

---

### Card 19: Netty 线程模型
**维度**: 🔬深挖 | **难度**: ⭐⭐⭐

> **Q: Netty 的 Reactor 线程模型是怎样的？Boss 和 Worker 分别做什么？**

**A:** Netty 主从 Reactor 模型：Boss Group（1 个或多个线程）负责 accept 新连接，注册 Channel 到 Worker Group。Worker Group（多个线程）负责 IO 读写和业务处理（可放入业务线程池）。每个 Worker 线程管理多个 Channel，使用 Selector 多路复用。Pipeline 是责任链模式，Handler 分为 Inbound（解码）和 Outbound（编码），Handler 在 Worker 线程中串行执行。

---

### Card 20: Java 异常体系
**维度**: 📝速记 | **难度**: ⭐

> **Q: Java 的异常体系结构是怎样的？受检异常和非受检异常的区别？**

**A:** Throwable 下分 Error（不可恢复，如 OutOfMemoryError、StackOverflowError）和 Exception。Exception 分受检异常（Checked Exception，如 IOException、SQLException，必须 catch 或 throws，违反则编译错误）和非受检异常（RuntimeException，如 NullPointerException、IllegalArgumentException，无需显式处理）。最佳实践：业务异常用非受检异常，避免 `throws` 传播污染接口。