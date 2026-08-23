# Java 并发编程 (JUC) 面试题大全

## 📚 知识体系

```
线程基础
├── 线程创建（Thread / Runnable / Callable）
├── 线程状态
├── 线程协作（wait/notify/join）
└── 线程安全（synchronized / volatile）

JUC 并发工具
├── Lock 体系（ReentrantLock / ReentrantReadWriteLock）
├── AbstractQueuedSynchronizer (AQS)
├── 并发集合（ConcurrentHashMap / CopyOnWriteArrayList）
├── 并发工具类（CountDownLatch / CyclicBarrier / Semaphore / Exchanger）
├── 阻塞队列（BlockingQueue）
├── 原子类（AtomicInteger / LongAdder）
└── 线程池（ThreadPoolExecutor / ForkJoinPool / CompletableFuture）

并发机制
├── 内存模型（JMM）
├── happens-before 原则
├── 锁优化（偏向锁/轻量级锁/重量级锁）
├── 伪共享 / 缓存行
└── CAS / volatile / 原子性
```

---

## 🎯 Level 1：基础题

### 1. 线程的创建方式有哪些？
**答案**：
| 方式 | 示例 | 特点 |
|------|------|------|
| 继承 Thread | `new Thread(){ run() }` | 简单，单继承限制 |
| 实现 Runnable | `new Thread(() -> {})` | 常用，无返回值 |
| 实现 Callable | `FutureTask` | 有返回值，可抛异常 |
| 线程池 | `ExecutorService.submit()` | 生产推荐 |
| CompletableFuture | `supplyAsync()` | 异步编排 |

### 2. 线程的状态有哪些？
**答案**：
```text
NEW（新建）
  ↓ start()
RUNNABLE（可运行，含就绪/运行）
  ↓ 等待锁
BLOCKED（阻塞）
  ↓
WAITING（等待）← wait() / join() / park()
  ↓
TIMED_WAITING（超时等待）← sleep() / wait(timeout)
  ↓
TERMINATED（终止）
```

### 3. volatile 关键字的作用？
**答案**：
1. **可见性**：volatile 变量修改后立即刷新到主内存
2. **禁止指令重排**：内存屏障防止重排序

**局限**：不保证**原子性**（i++ 仍需加锁）。

---

## 🎯 Level 2：进阶题

### 4. synchronized 和 ReentrantLock 的区别？
**答案**：

| 特性 | synchronized | ReentrantLock |
|------|--------------|---------------|
| 实现 | JVM 内置（Monitor） | JDK 类（AQS） |
| 锁获取 | 自动 | 手动 lock/unlock |
| 可中断 | 不支持 | lockInterruptibly() |
| 公平锁 | 非公平 | 支持公平/非公平 |
| 条件队列 | 1 个 | 多个 Condition |
| 尝试获取 | 不支持 | tryLock() |
| 性能（现代） | 接近 | 持平 |

### 5. ConcurrentHashMap 的实现原理？
**答案**：
**JDK 7**：Segment 分段锁 + HashEntry
**JDK 8+**：CAS + synchronized + Node 数组 + 红黑树

**JDK 8 关键点**：
1. 数组初始化为空，首次 put 通过 CAS 初始化
2. put：数组为空 CAS；哈希冲突 synchronized 锁链头
3. 链表长度 > 8 → 转红黑树
4. size()：通过 baseCount + CounterCell 累加

### 6. 线程池的核心参数是什么？
**答案**：

```java
public ThreadPoolExecutor(
    int corePoolSize,        // 核心线程数
    int maximumPoolSize,     // 最大线程数
    long keepAliveTime,      // 非核心线程存活时间
    TimeUnit unit,           // 时间单位
    BlockingQueue<Runnable> workQueue,  // 任务队列
    ThreadFactory threadFactory,        // 线程工厂
    RejectedExecutionHandler handler    // 拒绝策略
)
```

**执行流程**：
```text
任务提交
  ↓
核心线程未满 → 创建核心线程执行
  ↓ 已满
任务队列未满 → 放入队列
  ↓ 已满
线程数 < 最大线程数 → 创建新线程
  ↓ 已满
触发拒绝策略：
  AbortPolicy（默认，抛异常）
  CallerRunsPolicy（调用者执行）
  DiscardPolicy（丢弃）
  DiscardOldestPolicy（丢弃最旧）
```

---

## 🎯 Level 3：高级题

### 7. AQS 的原理是什么？
**答案**：
**AQS（AbstractQueuedSynchronizer）**：构建锁和同步器的**基石**。

```text
核心数据结构：
  volatile int state          // 同步状态
  Node head / tail            // CLH 双向队列（等待队列）

核心方法：
  acquire(int arg)            // 获取同步状态（失败入队自旋/阻塞）
  release(int arg)            // 释放同步状态（唤醒队首）
```

**应用**：
- ReentrantLock / ReentrantReadWriteLock
- Semaphore / CountDownLatch / CyclicBarrier
- 自定义同步器

### 8. CAS 是什么？有什么问题？
**答案**：
**CAS（Compare And Swap）**：比较内存值是否等于预期值，是则更新为新值（原子操作）。

**ABA 问题**：
- A → B → A，CAS 误认为未修改
- 解决：`AtomicStampedReference` / `AtomicMarkableReference` 加版本号

**其他问题**：
- 自旋消耗 CPU（
- 只能保证单个变量原子性）

---

## 🎯 Level 4：专家题

### 9. 如何避免死锁？
**答案**：

**产生条件**（4 项同时满足）：
1. 互斥：资源只能一个线程占用
2. 占有且等待
3. 不可剥夺
4. 循环等待

**避免手段**：
1. **锁顺序**：所有线程按同一顺序加锁
2. **单锁代替多锁**：降低锁粒度到必要时
3. **超时放弃**：tryLock(timeout) 失败释放已持锁
4. **锁排序（代码审查）**：对象按 ID 排序加锁

### 10. LongAdder 为什么比 AtomicLong 快？
**答案**：
- AtomicLong：所有线程 CAS 同一个变量（高并发争抢严重）
- LongAdder：**分段累加**（Cell[] 数组），最后 sum

```text
LongAdder:
  base（热点值）
  Cell[0]  Cell[1]  Cell[2]  Cell[3]  ← 每个线程分散累加
    ↓ sum() 汇总
```

**适用**：读少写多、统计计数（如计数器、指标）。

---

## 📖 学习资源

### 推荐项目
- [JavaGuide 并发部分](https://javaguide.cn/java/concurrent/)
- [Java 并发编程实战（书籍）](https://book.douban.com/subject/10484692/)
- [JUC 源码分析系列（美团技术博客）](https://tech.meituan.com/2019/12/05/aqs-theory-and-apply.html)

### 最佳实践
1. 禁止直接 new Thread，使用线程池
2. 线程池参数根据业务场景设置
3. 高并发计数用 LongAdder
4. 多把锁时统一加锁顺序
5. 用 CompletableFuture 做异步编排