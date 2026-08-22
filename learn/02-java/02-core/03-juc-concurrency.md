# JUC 并发编程 — synchronized · AQS · 线程池 · CompletableFuture

> 等级：👶 新手入门 → 🎯 面试进阶
> 目标：深入理解 Java 并发工具的核心原理，从源码层面掌握 synchronized、AQS、线程池机制。

---

## 一、线程基础

### 1.1 线程创建方式

```java
// 方式 1：继承 Thread
class MyThread extends Thread {
    @Override public void run() { ... }
}
new MyThread().start();

// 方式 2：实现 Runnable
new Thread(() -> { ... }).start();

// 方式 3：实现 Callable（有返回值）
FutureTask<Integer> task = new FutureTask<>(() -> { return 42; });
new Thread(task).start();
Integer result = task.get();  // 阻塞等待

// 方式 4：线程池
ExecutorService pool = Executors.newFixedThreadPool(4);
pool.execute(() -> { ... });
```

### 1.2 线程状态

```
NEW → RUNNABLE → BLOCKED / WAITING / TIMED_WAITING → TERMINATED
```

- **NEW**：`new Thread()` 创建后
- **RUNNABLE**：调用 `start()`，随时可被 CPU 调度
- **BLOCKED**：等待 synchronized 锁
- **WAITING**：`Object.wait()`、`Thread.join()`、`LockSupport.park()`
- **TIMED_WAITING**：`sleep(ms)`、`wait(timeout)`、`parkNanos()`
- **TERMINATED**：`run()` 执行完毕

### 1.3 线程通信

```java
// 经典：wait/notify 模式（必须在 synchronized 块内）
synchronized (lock) {
    while (condition) {   // 必须用 while 防止虚假唤醒
        lock.wait();
    }
    // 执行逻辑
    lock.notifyAll();
}
```

---

## 二、synchronized 原理

### 2.1 锁升级过程

JDK 1.6 之后做了大量优化，锁可以升级（不可降级）：

```
无锁 → 偏向锁 → 轻量级锁 → 重量级锁
```

| 锁状态 | 特性 | 适用场景 |
|--------|------|----------|
| 偏向锁 | 一个线程反复获取，Mark Word 记录线程 ID | 无竞争 |
| 轻量级锁 | CAS 自旋获取，Mark Word 指向栈帧 Lock Record | 低竞争，短同步块 |
| 重量级锁 | 线程阻塞，操作系统互斥量 | 高竞争，长时间等待 |

### 2.2 Mark Word 结构

```
| 锁状态       | 25bit          | 31bit    | 1bit | 4bit       | 1bit(偏向) | 2bit(锁位) |
|-------------|---------------|---------|------|-----------|----------|---------|
| 无锁         | unused        | hashCode | 分代年龄 | 0         | 01       |
| 偏向锁       | ThreadID(54)  | Epoch   | 分代年龄 | 1         | 01       |
| 轻量锁       | 指向 Lock Record 的指针        | 00       |
| 重量锁       | 指向 monitor 的指针            | 10       |
| GC 标记      |                                 | 11       |
```

### 2.3 锁粗化与锁消除

- **锁粗化**：连续加锁同一对象，JIT 合并为一次加锁
- **锁消除**：逃逸分析发现对象不逃逸，JIT 直接去掉锁

---

## 三、AQS 原理（AbstractQueuedSynchronizer）

### 3.1 核心思想

AQS 是 JUC 锁的基石，`ReentrantLock`、`CountDownLatch`、`Semaphore`、`ReentrantReadWriteLock` 都基于它。

```
AQS = volatile int state + CLH 变体等待队列
```

- **state**：同步状态，通过 CAS 修改
- **CLH 队列**：双向链表，每个节点（Node）包含线程引用 + 等待状态

### 3.2 独占模式（ReentrantLock）

```java
// 获取锁流程
public final void acquire(int arg) {
    if (!tryAcquire(arg) &&              // 子类实现：尝试获取
        acquireQueued(addWaiter(Node.EXCLUSIVE), arg))  // 入队 + 自旋
        selfInterrupt();
}

// 释放锁流程
public final boolean release(int arg) {
    if (tryRelease(arg)) {               // 子类实现：尝试释放
        Node h = head;
        if (h != null && h.waitStatus != 0)
            unparkSuccessor(h);          // 唤醒后继节点
        return true;
    }
    return false;
}
```

### 3.3 共享模式（CountDownLatch/Semaphore）

- `acquireShared` / `releaseShared`
- 共享模式下，一个资源释放可以唤醒多个等待线程

### 3.4 Condition 原理

- `Condition` 维护一个单向等待队列
- `await()`：当前线程入队等待队列，释放锁
- `signal()`：将等待队列头节点移到 CLH 同步队列

---

## 四、ReentrantLock

### 4.1 公平锁 vs 非公平锁

```java
// 非公平锁（默认）：tryAcquire 直接 CAS 抢锁，抢不到才排队
ReentrantLock lock = new ReentrantLock(false);

// 公平锁：按照 CLH 队列顺序获取
ReentrantLock lock = new ReentrantLock(true);
```

非公平锁性能更好（减少上下文切换），但可能导致线程饥饿。

### 4.2 ReadWriteLock / StampedLock

```java
// ReadWriteLock：读读不互斥，读写互斥，写写互斥
ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();
rwLock.readLock().lock();   // 多个线程可同时读
rwLock.writeLock().lock();  // 写必须独占

// StampedLock（JDK 8）：乐观读，不阻塞写
StampedLock stampedLock = new StampedLock();
long stamp = stampedLock.tryOptimisticRead();  // 乐观读
// 读取数据...
if (!stampedLock.validate(stamp)) {  // 检查写操作
    stamp = stampedLock.readLock();   // 升级为悲观读
    // 重新读取...
    stampedLock.unlockRead(stamp);
}
```

---

## 五、线程池原理

### 5.1 ThreadPoolExecutor 参数

```java
public ThreadPoolExecutor(
    int corePoolSize,      // 核心线程数
    int maximumPoolSize,   // 最大线程数
    long keepAliveTime,    // 非核心线程空闲存活时间
    TimeUnit unit,         // 时间单位
    BlockingQueue<Runnable> workQueue,  // 工作队列
    ThreadFactory threadFactory,        // 线程工厂
    RejectedExecutionHandler handler    // 拒绝策略
);
```

### 5.2 工作流程

```
新任务提交
    ↓
核心线程数已满？ → 否 → 创建核心线程执行
    ↓ 是
工作队列已满？ → 否 → 入队等待
    ↓ 是
最大线程数已满？ → 否 → 创建非核心线程执行
    ↓ 是
执行拒绝策略
```

### 5.3 拒绝策略

| 策略 | 行为 |
|------|------|
| `AbortPolicy`（默认） | 抛出 `RejectedExecutionException` |
| `CallerRunsPolicy` | 调用者线程执行（限流） |
| `DiscardPolicy` | 静默丢弃 |
| `DiscardOldestPolicy` | 丢弃队列最旧任务，重试提交 |

### 5.4 线程池大小设置

```java
// CPU 密集型：Ncpu + 1
int cpuCount = Runtime.getRuntime().availableProcessors();
int poolSize = cpuCount + 1;

// IO 密集型：2 * Ncpu
int poolSize = 2 * cpuCount;

// 通用公式：Nthreads = Ncpu * (1 + waitTime / computeTime)
```

### 5.5 不建议使用 Executors

```java
// 以下方式有潜在风险：
Executors.newFixedThreadPool(10);     // 队列无界 → OOM
Executors.newCachedThreadPool();      // 线程无界 → OOM
Executors.newScheduledThreadPool(10); // 队列无界 → OOM

// 推荐：手动创建
new ThreadPoolExecutor(10, 20, 60L, TimeUnit.SECONDS,
    new LinkedBlockingQueue<>(1000),
    new ThreadPoolExecutor.CallerRunsPolicy());
```

---

## 六、CompletableFuture 异步编程

### 6.1 创建异步任务

```java
// 无返回值
CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
    System.out.println("异步执行");
}, executor);

// 有返回值
CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
    return "结果";
}, executor);
```

### 6.2 异步编排

```java
// 串行
CompletableFuture.supplyAsync(this::getUserInfo)
    .thenApply(user -> buildOrder(user))
    .thenAccept(order -> saveOrder(order));

// 并行（两个任务并行，完成后合并）
CompletableFuture<String> future1 = CompletableFuture.supplyAsync(() -> "A");
CompletableFuture<String> future2 = CompletableFuture.supplyAsync(() -> "B");
CompletableFuture<String> result = future1.thenCombine(future2, (a, b) -> a + b);

// 多个任务全部完成
CompletableFuture.allOf(f1, f2, f3).join();

// 多个任务任意一个完成
CompletableFuture.anyOf(f1, f2, f3).join();
```

### 6.3 异常处理

```java
CompletableFuture.supplyAsync(() -> {
    if (Math.random() > 0.5) throw new RuntimeException("出错了");
    return "OK";
}).exceptionally(e -> {
    log.error("异步任务失败", e);
    return "默认值";
});
```

---

## 七、并发容器

| 容器 | 代替 | 原理 |
|------|------|------|
| `ConcurrentHashMap` | `HashMap` + `synchronized` | 分段 CAS + synchronized |
| `CopyOnWriteArrayList` | `ArrayList` + `synchronized` | 写时复制，读无锁 |
| `ConcurrentLinkedQueue` | `LinkedList` | CAS 无锁队列 |
| `LinkedBlockingQueue` | 有界阻塞队列 | 双锁（take/put 分离） |
| `ArrayBlockingQueue` | 有界阻塞队列 | 单锁 |
| `DelayQueue` | 延迟队列 | 内部 PriorityQueue + 延迟检查 |

---

## 八、面试高频题

| 题目 | 核心要点 |
|------|---------|
| synchronized 和 ReentrantLock 区别？ | 前者 JVM 自动释放，后者需手动释放；前者不可中断，后者可中断；后者支持公平锁和 Condition |
| 线程池核心线程数怎么设？ | CPU 密集型 N+1，IO 密集型 2N，通用公式见上 |
| 线程池的线程什么时候创建？ | 核心线程懒加载，提交任务时才创建 |
| 拒绝策略怎么选？ | 业务重要用 CallerRunsPolicy（限流），不重要用 DiscardPolicy |
| volatile 能保证原子性吗？ | 不能，只保证可见性和有序性（禁止指令重排） |
| ThreadLocal 内存泄漏？ | key 是弱引用，value 是强引用，key 被回收后 value 无法访问，需 `remove()` |

> 进入下一节：IO 模型与 NIO 核心。