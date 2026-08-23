# 深挖题 — 源码级深入分析

> 等级：🎯 面试进阶
> 目标：深入理解 ConcurrentHashMap 扩容细节、JVM 对象逃逸分析、线程池参数设置公式推导。

---

## 一、ConcurrentHashMap 扩容细节

### 1.1 触发条件

当 `size > sizeCtl`（即 `size > 容量 * 0.75`）时触发扩容。

### 1.2 扩容过程

1. **计算扩容戳**（resizeStamp）：`Integer.numberOfLeadingZeros(n) | (1 << 15)`
2. **更新 sizeCtl**：`(rs << RESIZE_STAMP_SHIFT) + 2`，低位表示参与扩容的线程数
3. **transfer 方法**：

```java
private final void transfer(Node<K,V>[] tab, Node<K,V>[] nextTab) {
    // 1. 计算步长 stride（每个线程负责的桶数）
    int stride = (NCPU > 1) ? (n >>> 3) / NCPU : n;
    if (stride < MIN_TRANSFER_STRIDE) stride = MIN_TRANSFER_STRIDE;

    // 2. 创建新数组：2 倍容量
    nextTab = new Node<?,?>[n << 1];

    // 3. ForwardingNode 标记已迁移桶
    ForwardingNode<K,V> fwd = new ForwardingNode<>(nextTab);

    // 4. 循环迁移，从后往前
    while (advance) {
        // 每个线程处理自己的 stride 区间
        // 使用 CAS 更新 transferIndex 分配区间
    }
}
```

### 1.3 迁移中的读操作

- 遇到 `ForwardingNode`：调用 `ForwardingNode.find()` 到新数组继续查找
- 未迁移的桶：正常在旧数组查找
- 保证了迁移过程中读操作始终可用

### 1.4 迁移中的写操作

写操作加锁前会检查 `tabAt(tab, i)` 是否为 `ForwardingNode`，如果是则协助扩容（`helpTransfer`）。

### 1.5 面试关键点

> **扩容时读操作如何保证不读到脏数据？**
> 迁移时先复制节点到新数组，再通过 CAS 将旧数组的桶设为 ForwardingNode。读操作通过 volatile 读取数组引用，保证了可见性，读到 ForwardingNode 时跳转到新数组。

> **多线程如何协助扩容？**
> 每个线程通过 CAS 竞争 `transferIndex`，分配连续的 stride 区间，迁移完成后用 CAS 更新 sizeCtl 的计数，最后一个线程完成时检查所有桶是否都已迁移。

---

## 二、从 JVM 角度分析对象逃逸

### 2.1 逃逸状态

| 逃逸等级 | 含义 | 优化手段 |
|----------|------|----------|
| 不逃逸 | 对象只在方法内使用，不返回、不赋值给外部变量 | 栈上分配、标量替换、锁消除 |
| 方法逃逸 | 对象作为参数传递或返回值 | 部分优化 |
| 线程逃逸 | 对象被赋值到实例变量或静态变量，被其他线程访问 | 无法优化 |

### 2.2 栈上分配

```java
public void process() {
    Point p = new Point(1, 2);  // p 不逃逸
    System.out.println(p.x + p.y);
}
```

JIT 通过逃逸分析发现 `p` 不逃逸，直接在栈上分配，方法结束后自动销毁，不再需要 GC。

### 2.3 标量替换

```java
// 原始代码
public int sum() {
    Point p = new Point(1, 2);
    return p.x + p.y;
}

// JIT 标量替换后，等价于：
public int sum() {
    int x = 1;  // 不创建对象，直接展开为局部变量
    int y = 2;
    return x + y;
}
```

### 2.4 锁消除

```java
public void append(StringBuffer sb) {
    sb.append("a");  // StringBuffer 的方法都是 synchronized
    // JIT 逃逸分析发现 sb 不逃逸，直接消除锁
}
```

### 2.5 面试关键点

> **逃逸分析在哪个阶段？**
> JIT 编译阶段（C1/C2 编译器），不是 javac 编译阶段。

> **所有对象都能栈上分配吗？**
> 不能。只有不逃逸的、大小合适的对象可以。JVM 通过 `-XX:+DoEscapeAnalysis` 开启（默认开启）。

> **如何确认逃逸分析生效？**
> 加 `-XX:+PrintEscapeAnalysis` 查看逃逸分析结果，加 `-XX:+EliminateAllocations` 开启标量替换（默认开启）。

---

## 三、线程池核心线程数设置公式

### 3.1 理论公式

```java
// 通用公式
Nthreads = Ncpu * Ucpu * (1 + W / C)

// 其中：
// Ncpu   = CPU 核心数
// Ucpu   = 目标 CPU 利用率（0~1）
// W/C    = 等待时间 / 计算时间
```

### 3.2 实际应用

```java
// CPU 密集型：W/C ≈ 0
int poolSize = Runtime.getRuntime().availableProcessors() + 1;
// 加 1 是为了补偿页缺失导致的暂停

// IO 密集型：W/C 通常很大
// 假设 80% 时间在等待 IO，20% 在计算，W/C = 4
// Nthreads = 4 * 1 * (1 + 4) = 20
int poolSize = 2 * Runtime.getRuntime().availableProcessors();
```

### 3.3 动态调整（Little's Law）

```java
// 理论最大吞吐量
// Throughput = 线程数 / 平均响应时间

// 最优线程数
// 最优线程数 = 目标 TPS * 平均响应时间（秒）
// 例如：目标 1000 TPS，平均响应 200ms
// 最优线程数 = 1000 * 0.2 = 200
```

### 3.4 监控与调优

```java
// 运行时监控
ThreadPoolExecutor pool = new ThreadPoolExecutor(...);
int poolSize = pool.getPoolSize();
int activeCount = pool.getActiveCount();
long taskCount = pool.getTaskCount();
int queueSize = pool.getQueue().size();

// 动态调整
pool.setCorePoolSize(newSize);
pool.setMaximumPoolSize(newMaxSize);
```

### 3.5 面试关键点

> **核心线程数设置过大或过小有什么后果？**
> 过小：队列积压，响应时间变长，CPU 利用率低。过大：线程上下文切换开销大，内存占用高，甚至 OOM。

> **线程池运行时如何动态调整？**
> 通过 `setCorePoolSize` 和 `setMaximumPoolSize` 调整，调整后线程池会逐步适应新配置。

> **队列大小怎么设？**
> 取决于任务处理速度和任务堆积容忍度。一般公式：`队列大小 = 请求峰值 QPS * 峰值持续秒数`。例如 1000 QPS，持续 10 秒，队列至少 10000。

---

## 四、面试关键点总结

| 主题 | 核心能力 | 面试价值 |
|------|---------|---------|
| ConcurrentHashMap 扩容 | 理解多线程协作机制 | 体现并发编程深度 |
| 逃逸分析 | 理解 JIT 编译优化 | 体现 JVM 底层理解 |
| 线程池参数 | 掌握性能调优公式 | 体现系统设计能力 |

> 进入场景题篇：缓存设计、异步编排、大文件处理。