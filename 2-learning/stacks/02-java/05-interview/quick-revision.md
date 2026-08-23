# 速记版 — 高频考点一句话速记

> 等级：🎯 面试冲刺
> 目标：考前 30 分钟快速回顾，集合/JVM/JUC/IO 各 10 个核心考点。

---

## 一、集合（10 个考点）

| # | 考点 | 一句话 |
|---|------|--------|
| 1 | ArrayList 扩容 | 1.5 倍，`Arrays.copyOf` 数组复制，预分配减少扩容 |
| 2 | LinkedList 结构 | 双向链表，Node 存 prev/next/item，随机访问 O(n) |
| 3 | HashMap 数据结构 | 数组 + 链表 + 红黑树，链表 >= 8 且数组 >= 64 树化 |
| 4 | HashMap put 流程 | hash 寻址 → 空插 → 冲突拉链/红黑树 → 超阈值 resize |
| 5 | HashMap 扩容 | 2 倍扩容，元素在新索引 = 原位置 or 原位置+旧容量 |
| 6 | HashMap 容量 2 幂 | `(n-1) & hash` 等价于取模，位运算更快 |
| 7 | ConcurrentHashMap 1.8 | CAS + synchronized，桶粒度锁，sizeCtl 控制状态 |
| 8 | ConcurrentHashMap 扩容 | 多线程协助，ForwardingNode 标记已迁移桶 |
| 9 | ConcurrentHashMap key 不能 null | 并发下无法区分 key 不存在和 value 为 null |
| 10 | LinkedHashMap LRU | `accessOrder=true`，`afterNodeAccess` 移尾节点 |

---

## 二、JVM（10 个考点）

| # | 考点 | 一句话 |
|---|------|--------|
| 1 | 运行时数据区 | 堆、方法区（线程共享）、JVM 栈、本地方法栈、PC（线程私有） |
| 2 | 对象创建过程 | 类加载检查 → 分配内存 → 零值初始化 → 设置对象头 → `<init>` |
| 3 | 对象内存布局 | Mark Word（8B）+ 类型指针 + 实例数据 + 对齐填充 |
| 4 | 堆分代 | 新生代（Eden:S0:S1=8:1:1）+ 老年代，默认 1:2 |
| 5 | 新生代 GC | 复制算法，Eden 存活对象 → S0/S1，年龄 +1，15 到老年代 |
| 6 | CMS 收集器 | 初始标记→并发标记→重新标记→并发清除，产生碎片 |
| 7 | G1 收集器 | Region 化，可预测停顿，JDK 9 默认，局部复制无碎片 |
| 8 | ZGC 收集器 | 染色指针，亚毫秒 STW，支持超大堆，JDK 21 GA |
| 9 | 元空间 vs 永久代 | 元空间用本地内存，不受 -Xmx 限制，JDK 8+ |
| 10 | 内存泄漏排查 | jps → jstat → jmap dump → MAT 分析 Leak Suspects |

---

## 三、JUC（10 个考点）

| # | 考点 | 一句话 |
|---|------|--------|
| 1 | 线程状态 | NEW → RUNNABLE → BLOCKED/WAITING/TIMED_WAITING → TERMINATED |
| 2 | synchronized 锁升级 | 偏向锁 → 轻量锁（CAS 自旋）→ 重量锁（OS 互斥量） |
| 3 | AQS 核心 | `volatile int state` + CLH 双向等待队列，CAS 操作 |
| 4 | ReentrantLock 公平锁 | 非公平默认，直接 CAS 抢锁；公平锁按 CLH 队列顺序 |
| 5 | 线程池参数 | corePoolSize, maxPoolSize, keepAlive, workQueue, threadFactory, handler |
| 6 | 线程池工作流程 | 核心线程 → 工作队列 → 非核心线程 → 拒绝策略 |
| 7 | 拒绝策略 | AbortPolicy（抛异常）、CallerRunsPolicy（调用者执行）、Discard、DiscardOldest |
| 8 | 线程池大小 | CPU 密集 N+1，IO 密集 2N，通用公式 `N * (1 + wait/ compute)` |
| 9 | volatile | 可见性 + 禁止指令重排，不保证原子性 |
| 10 | ThreadLocal 泄漏 | key 弱引用被 GC，value 强引用无法访问，需 `remove()` |

---

## 四、IO（10 个考点）

| # | 考点 | 一句话 |
|---|------|--------|
| 1 | BIO | 阻塞 IO，一个线程一个连接，连接数少时简单 |
| 2 | NIO | 非阻塞 IO，Selector 多路复用，单线程管理大量连接 |
| 3 | AIO | 异步 IO，回调通知，Linux 实现不成熟，Windows IOCP 表现好 |
| 4 | Channel | 双向通道，FileChannel、SocketChannel、ServerSocketChannel |
| 5 | Buffer | capacity/position/limit/mark，flip() 切换读模式，clear() 切换写模式 |
| 6 | Selector | 注册 Channel 事件（accept/read/write），select() 阻塞等待就绪 |
| 7 | 零拷贝 | `transferTo` 直接从文件到网卡，减少内核态/用户态拷贝 |
| 8 | Netty 线程模型 | Boss Group 处理 accept，Worker Group 处理 IO 读写 |
| 9 | Netty 核心组件 | EventLoopGroup、ChannelPipeline、ChannelHandler、ByteBuf |
| 10 | 拆包粘包 | TCP 流式无边界，定长/分隔符/LengthField 解码器解决 |

---

## 五、新特性（5 个考点）

| # | 考点 | 一句话 |
|---|------|--------|
| 1 | Record | 不可变数据载体，自动生成构造器、equals/hashCode/toString |
| 2 | Sealed Class | 限制继承的子类集合，配合 switch 模式匹配穷举检查 |
| 3 | 虚拟线程 | JVM 管理轻量线程，M:N 调度，百万级数量，适合 IO 密集型 |
| 4 | Switch 模式匹配 | case 支持类型匹配 + when 守卫 + null 匹配 |
| 5 | SequencedCollection | `addFirst`/`addLast`/`reversed` 统一有序集合接口 |

> 进入深挖题篇：从源码角度深入理解 ConcurrentHashMap 扩容、对象逃逸、线程池参数设置。