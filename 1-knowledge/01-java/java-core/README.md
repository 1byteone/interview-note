# Java 核心面试题

## 📚 知识点概览

Java 核心是 Java 后端工程师的基础，涵盖 JVM、并发编程、集合框架、IO/NIO 等核心知识。

## 🎯 面试题分类

### Level 1: 基础题

#### JVM 基础
1. **JVM 内存模型**
   - 问题：请描述 JVM 的内存模型，包括堆、栈、方法区等。
   - 答案：JVM 内存分为线程私有的程序计数器、虚拟机栈、本地方法栈，和线程共享的堆、方法区。JDK 8 后方法区由元空间（MetaSpace）取代，使用直接内存。
   - 解析：堆是 GC 主要区域，分为新生代（Eden/Survivor）和老年代。虚拟机栈存储栈帧（局部变量表、操作数栈、动态链接、方法出口）。程序计数器记录当前线程执行的字节码行号。元空间使用本地内存，不再受 JVM 参数限制，避免了永久代 OOM。

2. **垃圾回收机制**
   - 问题：JVM 的垃圾回收机制是怎样的？常见的垃圾回收器有哪些？
   - 答案：GC 自动回收不再被引用的对象内存。常见回收器：Serial（单线程）、ParNew（多线程）、Parallel Scavenge（吞吐量优先）、CMS（低延迟）、G1（大堆默认）、ZGC（亚毫秒级）。
   - 解析：GC 判断对象存活用可达性分析（GC Roots 包括栈帧引用、静态变量、JNI 引用）。回收算法：标记-清除（碎片）、标记-复制（新生代）、标记-整理（老年代）。G1 是 JDK 9+ 默认，Region 化 + 预测停顿。ZGC 染色指针 + 读屏障，停顿 < 10ms。

#### 并发编程基础
3. **线程与进程**
   - 问题：线程和进程有什么区别？
   - 答案：进程是资源分配的最小单位，线程是 CPU 调度的最小单位。同一进程的线程共享堆和方法区，私有的有程序计数器、虚拟机栈和本地方法栈。
   - 解析：进程间通信（IPC）需要管道、消息队列、共享内存等机制，开销大。线程间通信通过共享内存，效率高但需同步机制（synchronized、Lock、volatile）。线程切换比进程切换开销小得多，因为不需要切换页表。

4. **synchronized 关键字**
   - 问题：synchronized 关键字的作用是什么？它是如何实现的？
   - 答案：synchronized 保证原子性、可见性和有序性，基于 Monitor 对象实现。JDK 6 后引入锁升级机制：偏向锁 → 轻量级锁 → 重量级锁。
   - 解析：synchronized 编译后生成 monitorenter/monitorexit 指令。锁存储在对象头 Mark Word 中，JDK 15 起默认开启偏向锁。偏向锁减少同一线程重复获取锁的 CAS 开销；轻量级锁通过 CAS 自旋避免线程阻塞；重量级锁依赖操作系统 Mutex，线程阻塞。

#### 集合框架
5. **ArrayList vs LinkedList**
   - 问题：ArrayList 和 LinkedList 的区别是什么？分别适用于什么场景？
   - 答案：ArrayList 基于动态数组，随机访问 O(1)，尾部插入 O(1)，中间插入/删除 O(n)。LinkedList 基于双向链表，随机访问 O(n)，头尾插入 O(1)。
   - 解析：ArrayList 扩容时 Arrays.copyOf 为 1.5 倍，批量插入预分配 ensureCapacity 可减少扩容次数。LinkedList 每个节点需存储前后指针，内存占用约 3 倍 ArrayList。实际开发中 90% 场景用 ArrayList，只在频繁头尾插入删除时用 LinkedList。

6. **HashMap 原理**
   - 问题：HashMap 的底层实现原理是什么？如何解决哈希冲突？
   - 答案：JDK 8 采用数组 + 链表 + 红黑树实现。哈希冲突用链地址法解决，当链表长度 ≥ 8 且数组长度 ≥ 64 时转为红黑树。
   - 解析：put 流程：hash(key) → (n-1) & hash 定位桶 → 遍历链表/树。红黑树查询 O(log n) 优于链表 O(n)。负载因子 0.75，扩容为 2 倍，rehash 时元素位置要么在原位置，要么在原位置 + 原容量。扩容阈值 = capacity * loadFactor。

### Level 2: 进阶题

#### JVM 进阶
7. **类加载机制**
   - 问题：JVM 的类加载机制是怎样的？什么是双亲委派模型？
   - 答案：类加载分加载、链接（验证/准备/解析）、初始化三个阶段。双亲委派：加载类时先委派给父加载器，父加载器无法加载才由子加载器尝试。
   - 解析：三层类加载器：Bootstrap（JAVA_HOME/lib）、Extension（lib/ext）、Application（classpath）。双亲委派保证核心类库安全（如 java.lang.String 不会被篡改）。打破双亲委派可通过自定义 ClassLoader 重写 loadClass 方法，Tomcat 和 SPI 机制均打破了此模型。

8. **JVM 调优**
   - 问题：如何进行 JVM 调优？常用的 JVM 参数有哪些？
   - 答案：常用参数：-Xms/-Xmx（堆大小）、-Xmn（新生代）、-XX:MetaspaceSize、-XX:+UseG1GC、-XX:MaxGCPauseMillis=200。调优步骤：监控 GC 日志 → 分析问题 → 调整参数 → 验证效果。
   - 解析：堆大小建议设为物理内存的 50%-70%。Xms 和 Xmx 设为相同值避免运行时扩容。G1 定位最大停顿时间，不适合小堆（<4GB）。GC 日志分析工具：GCeasy、GCEasy、jstat。关键指标：Young GC 频率、Full GC 频率、STW 时间、吞吐量。

#### 并发编程进阶
9. **volatile 关键字**
   - 问题：volatile 关键字的作用是什么？它和 synchronized 有什么区别？
   - 答案：volatile 保证可见性和有序性（禁止指令重排），不保证原子性。synchronized 保证原子性、可见性和有序性。
   - 解析：volatile 写操作插入 StoreLoad 屏障，读操作插入 LoadLoad 屏障，禁止编译器/CPU 重排序。适合状态标记位（如 boolean flag），不适合复合操作（如 count++）。synchronized 通过 Monitor 互斥锁保证原子性，但开销更大。两者可配合使用：volatile 控制可见性，synchronized 保证原子性。

10. **线程池**
    - 问题：线程池的核心参数有哪些？如何合理配置线程池？
    - 答案：核心参数：corePoolSize（核心线程数）、maximumPoolSize（最大线程数）、keepAliveTime（空闲存活时间）、workQueue（阻塞队列）、handler（拒绝策略）。
    - 解析：CPU 密集型线程数 = CPU 核数 + 1，IO 密集型 = 2 * CPU 核数。拒绝策略：AbortPolicy（抛异常）、CallerRunsPolicy（调用者线程执行）、DiscardPolicy（丢弃）、DiscardOldestPolicy（丢弃最旧）。任务队列：LinkedBlockingQueue（无界）、ArrayBlockingQueue（有界）、SynchronousQueue（直接提交）。ThreadPoolExecutor 是核心实现，建议手动创建而非使用 Executors。

11. **ConcurrentHashMap**
    - 问题：ConcurrentHashMap 的实现原理是什么？它如何保证线程安全？
    - 答案：JDK 8 采用 Node 数组 + CAS + synchronized 实现线程安全。CAS 用于插入头节点，synchronized 锁住链表头节点处理冲突。
    - 解析：JDK 7 采用 Segment 分段锁，JDK 8 改为粒度更细的桶锁。sizeCtl 控制扩容状态（-1 表示正在初始化，-N 表示正在扩容）。扩容时多线程协同（transfer 任务分片），每个线程负责一段范围的桶迁移。get 操作全程无锁。查询效率与 HashMap 相当，写操作性能远优于 HashTable。

#### 集合框架进阶
12. **HashMap 线程安全**
    - 问题：HashMap 为什么不是线程安全的？如何实现线程安全的 Map？
    - 答案：HashMap 在并发 put 时可能死循环（JDK 7 头插法导致环形链表）或数据丢失。线程安全方案：ConcurrentHashMap、Collections.synchronizedMap、HashTable。
    - 解析：JDK 7 扩容时头插法在并发下形成环形链表，导致 get 死循环。JDK 8 改为尾插法避免此问题。synchronizedMap 通过 synchronized 包装所有方法，性能差但简单。HashTable 全表锁，已淘汰。ConcurrentHashMap 是生产环境首选，读多写少场景也可用 ImmutableMap（Guava）。

13. **红黑树**
    - 问题：什么是红黑树？HashMap 为什么使用红黑树？
    - 答案：红黑树是自平衡二叉查找树，满足 5 条性质：节点红或黑、根黑、叶黑、红节点子必黑、任意节点到叶的黑节点数相同。
    - 解析：HashMap 在哈希冲突严重时，链表查询退化为 O(n)，红黑树保证 O(log n)。树化阈值 8 基于泊松分布，链表长度达到 8 的概率极低。退化阈值 6，避免频繁树化/退化。红黑树插入/删除操作通过旋转（左旋/右旋）和变色维持平衡，比 AVL 树旋转更少，写操作性能更好。

### Level 3: 高级题

#### JVM 高级
14. **GC 算法**
    - 问题：常见的 GC 算法有哪些？它们各自的特点是什么？
    - 答案：三种算法：标记-清除（产生碎片）、标记-复制（空间浪费，新生代用）、标记-整理（无碎片，老年代用）。
    - 解析：标记-复制将内存分为两块，每次只使用一块，回收时将存活对象复制到另一块，适合存活率低的新生代。HotSpot 默认 Eden:Survivor = 8:1，保证 90% 空间利用率。标记-整理移动存活对象以消除碎片，但需要 STW 整理。CMS 使用标记-清除，需预留空间给浮动垃圾，失败则退化为 Serial Old 整理。G1 和 ZGC 使用 Region 化 + 局部复制。

15. **JVM 内存溢出**
    - 问题：什么情况下会发生 JVM 内存溢出？如何排查和解决？
    - 答案：堆溢出（OOM: Java heap space）、栈溢出（StackOverflowError）、元空间溢出、直接内存溢出。排查：dump 堆转储 → MAT 分析 → 定位泄漏点。
    - 解析：堆溢出常见原因：内存泄漏（对象未释放）、大对象过多、数据量超预期。排查步骤：jmap -dump:live,format=b,file=heap.hprof <pid> → MAT 分析 Dominator Tree → 找出 GC Root 引用链。启动参数 -XX:+HeapDumpOnOutOfMemoryError 自动 dump。使用 jvisualvm 或 Arthas 实时监控。

#### 并发编程高级
16. **AQS 原理**
    - 问题：什么是 AQS（AbstractQueuedSynchronizer）？它在并发包中扮演什么角色？
    - 答案：AQS 是 Java 并发包的基础框架，提供同步状态管理、CLH 阻塞队列、条件队列。ReentrantLock、CountDownLatch、Semaphore 等均基于 AQS 实现。
    - 解析：AQS 核心是 state（volatile int）和 FIFO 双向队列。独占模式（ReentrantLock）和共享模式（CountDownLatch）通过 tryAcquire/tryRelease 模板方法实现。CLH 队列每个节点包含 waitStatus、前驱/后继指针。线程通过 LockSupport.park/unpark 阻塞/唤醒，避免忙等。条件队列实现 await/signal，与阻塞队列协同工作。

17. **锁优化**
    - 问题：Java 中有哪些锁优化技术？什么是偏向锁、轻量级锁、重量级锁？
    - 答案：锁升级：无锁 → 偏向锁（同一线程重入）→ 轻量级锁（CAS 自旋）→ 重量级锁（OS Mutex）。其他优化：锁消除、锁粗化、自适应自旋。
    - 解析：偏向锁在对象头存储线程 ID，减少同一线程重复获取锁的 CAS 开销。轻量级锁用 CAS 抢锁，失败则自旋。重量级锁依赖操作系统互斥量，线程阻塞。锁消除基于逃逸分析，锁粗化合并相邻同步块。JDK 15 默认开启偏向锁，JDK 21 引入虚拟线程后偏向锁被标记为废弃。偏向锁撤销需等待全局安全点，在高并发场景可能成为性能瓶颈。

#### IO/NIO
18. **BIO vs NIO vs AIO**
    - 问题：BIO、NIO、AIO 的区别是什么？分别适用于什么场景？
    - 答案：BIO 同步阻塞，一连接一线程。NIO 同步非阻塞，多路复用器（Selector）单线程管理多连接。AIO 异步非阻塞，回调通知。
    - 解析：BIO 适合连接数少且固定的场景，代码简单。NIO 核心组件：Channel（双向）、Buffer（缓冲）、Selector（多路复用）。select 返回就绪事件（OP_ACCEPT/OP_READ/OP_WRITE）。AIO（Proactor 模式）JDK 7 引入，实际使用较少，Netty 基于 NIO 封装。NIO 的零拷贝（transferTo/transferFrom）减少用户态内核态切换。

19. **Netty 框架**
    - 问题：Netty 是什么？它的核心组件有哪些？
    - 答案：Netty 是高性能 NIO 网络框架，封装了 Java NIO 的复杂性。核心组件：Bootstrap/ServerBootstrap、EventLoopGroup、ChannelPipeline、ChannelHandler。
    - 解析：Netty 采用 Reactor 线程模型，BossGroup 处理 accept，WorkerGroup 处理读写。ChannelPipeline 是责任链模式，Handler 分 Inbound/Outbound。ByteBuf 替代 ByteBuffer，支持池化和零拷贝。Netty 的拆包粘包解决：LineBasedFrameDecoder、FixedLengthFrameDecoder、LengthFieldBasedFrameDecoder。心跳机制通过 IdleStateHandler 实现。

### Level 4: 专家题

#### JVM 专家
20. **G1 收集器**
    - 问题：G1 收集器的工作原理是什么？它有哪些优势？
    - 答案：G1 将堆划分为 2048 个 Region（1-32MB），通过跟踪各 Region 的垃圾价值（回收收益），优先回收收益最大的 Region。
    - 解析：G1 是 JDK 9+ 默认收集器，设计目标大堆（6GB+）且可预测停顿（默认 200ms）。GC 阶段：Young GC（STW 复制存活对象）→ Concurrent Marking（并发标记）→ Mixed GC（混合回收部分老年代）。G1 的 Remembered Set 维护 Region 间引用，卡表（Card Table）记录跨 Region 引用变更。G1 的劣势是小堆下吞吐量不如 Parallel。

21. **ZGC 收集器**
    - 问题：ZGC 收集器是什么？它如何实现低延迟？
    - 答案：ZGC 是低延迟垃圾收集器，停顿时间 < 10ms，与堆大小无关。核心技术：染色指针（Colored Pointers）、读屏障（Load Barrier）、多阶段并发。
    - 解析：ZGC 在对象指针的 64 位中借用 4 位存储状态（Finalizable/Remapped/Mark0/Mark1），无需对象头标记。读屏障在 load 指令时拦截，若对象已被移动则转发到新地址。ZGC 所有阶段几乎全并发（初始标记和最终标记仅短暂 STW）。JDK 21 的分代 ZGC 支持新生代/老年代分区，提升吞吐量。支持 TB 级堆。

#### 并发编程专家
22. **Fork/Join 框架**
    - 问题：什么是 Fork/Join 框架？它的工作窃取算法是什么？
    - 答案：Fork/Join 将大任务递归拆分为子任务，并行执行后合并结果。工作窃取：空闲线程从其他队列尾部窃取任务执行。
    - 解析：核心类 ForkJoinPool 和 ForkJoinTask（RecursiveAction 无返回值，RecursiveTask 有返回值）。WorkQueue 采用双端队列，Worker 从头部取任务，窃取者从尾部取，减少竞争。ForkJoinPool.commonPool() 是 JVM 全局池。适用场景：数组排序、大文件处理、递归计算。ForkJoinPool 不同于 ThreadPoolExecutor，适用于计算密集型分治任务。

23. **CompletableFuture**
    - 问题：CompletableFuture 的核心 API 有哪些？如何实现异步编排？
    - 答案：核心方法：supplyAsync/runAsync、thenApply/thenAccept、thenCompose、thenCombine、allOf/anyOf、exceptionally/handle。
    - 解析：thenApply 转换结果，thenAccept 消费结果，thenCompose 扁平化组合。thenCombine 合并两个 CF 结果。allOf 等待所有完成，anyOf 任一完成。exceptionally 处理异常，handle 无论成功失败都执行。默认使用 ForkJoinPool.commonPool，可指定自定义线程池。异步编排实现：userService.findUser() 与 orderService.findOrders() 并行 → thenCombine 合并结果 → thenApply 组装响应。

## 📖 学习资源

### 书籍推荐
- 《深入理解 Java 虚拟机》 - 周志明
- 《Java 并发编程的艺术》 - 方腾飞
- 《Java 并发编程实战》 - Brian Goetz

### 在线资源
- [Java 官方文档](https://docs.oracle.com/javase/)
- [JavaGuide](https://javaguide.cn/java/basis/java-basic-questions-01.html)
- [深入理解 JVM](https://gitee.com/moxun163/java-learn)

## 🔗 相关链接

- [JVM 专题](./jvm/)
- [并发编程专题](./juc/)
- [集合框架专题](./collections/)
- [IO/NIO 专题](./io/)
