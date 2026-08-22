# JVM 内存 — 运行时数据区 · GC 算法 · 收集器 · 调优

> 等级：👶 新手入门 → 🎯 面试进阶
> 目标：理解 JVM 内存布局，掌握 GC 原理与常见收集器，能排查内存泄漏。

---

## 一、运行时数据区

```
┌─────────────────────────────────────────────────────┐
│                   线程共享                           │
│  ┌─────────────────────┐ ┌──────────────────────┐  │
│  │       Heap 堆        │ │    Method Area 方法区  │  │
│  │ （对象实例、数组）     │ │（类信息、常量、静态变量）│  │
│  │                     │ │  ┌────────────────┐   │  │
│  │ Eden → S0 → S1 → Old│ │ │ 运行时常量池     │   │  │
│  │                     │ │ │（StringTable）   │   │  │
│  └─────────────────────┘ └──────────────────────┘  │
├─────────────────────────────────────────────────────┤
│                   线程私有                           │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌────────┐│
│  │VM Stack  │ │Native    │ │PC        │ │Direct  ││
│  │JVM 栈    │ │Method    │ │寄存器    │ │Memory  ││
│  │(栈帧)    │ │Stack     │ │(当前指令)│ │(NIO)   ││
│  └──────────┘ └──────────┘ └──────────┘ └────────┘│
└─────────────────────────────────────────────────────┘
```

### 1.1 堆（Heap）

- 所有线程共享，存放对象实例和数组
- 分代设计：新生代（Young） + 老年代（Old）
- 新生代：Eden : S0 : S1 = 8:1:1（默认比例）
- `-Xms` 初始堆大小，`-Xmx` 最大堆大小

### 1.2 JVM 栈（VM Stack）

- 每个线程私有，方法调用时创建栈帧
- 栈帧包含：局部变量表、操作数栈、动态链接、方法出口
- `-Xss` 控制栈大小，默认 1024KB（Linux 64位），递归太深导致 StackOverflowError

### 1.3 方法区（Method Area）

- JDK 8 之前称为"永久代（PermGen）"，JDK 8 之后改为**元空间（Metaspace）**
- 存放类加载信息、常量、静态变量、JIT 编译后的代码
- 元空间使用本地内存（Native Memory），不再受 JVM 堆限制
- `-XX:MaxMetaspaceSize` 限制元空间大小

### 1.4 运行时常量池

- 类文件中的 Constant Pool 在类加载后放入运行时常量池
- String Table（字符串常量池）：JDK 7 移到了堆中，不再是方法区

### 1.5 本地内存（Direct Memory）

- NIO 的 `DirectByteBuffer` 使用 `unsafe.allocateMemory` 分配
- 不受 `-Xmx` 限制，但受物理内存限制
- `-XX:MaxDirectMemorySize` 控制

---

## 二、对象创建过程

```
1. 类加载检查    → 检查 new 指令参数能否在常量池定位到类引用
2. 分配内存      → 指针碰撞（GC 压缩后）或 空闲列表（CMS）
3. 初始化零值    → 字段默认值（0/null/false）
4. 设置对象头    → Mark Word + 类型指针（+ 数组长度）
5. 执行 <init>  → 按照代码顺序执行构造器
```

### 对象内存布局

```
┌──────────────────────────────────────┐
│  Mark Word (8字节)                    │ ← 包含 hashCode、GC 分代年龄、锁状态
├──────────────────────────────────────┤
│  类型指针 (4/8字节，压缩后4字节)       │ ← 指向方法区类元数据
├──────────────────────────────────────┤
│  实例数据                             │ ← 各种字段
├──────────────────────────────────────┤
│  对齐填充 (按8字节对齐)                │
└──────────────────────────────────────┘
```

### 对象访问定位

两种方式：**直接指针**（HotSpot 使用，更快）和 **句柄**（GC 移动时不用改引用）。

---

## 三、GC 算法

### 3.1 标记-清除（Mark-Sweep）

- 标记存活对象，清除未标记对象
- 缺点：**产生内存碎片**

### 3.2 复制（Copying）

- 将内存分成两块，只使用一块，GC 时将存活对象复制到另一块
- 优点：无碎片，分配高效（指针碰撞）
- 缺点：浪费一半空间
- **新生代使用此算法**（Eden:S0:S1 = 8:1:1，只浪费 10%）

### 3.3 标记-整理（Mark-Compact）

- 标记存活对象，将所有存活对象移到一端，清理边界外
- 优点：无碎片，空间利用率高
- 缺点：移动对象需要 STW（Stop The World）
- **老年代常使用此算法**

### 3.4 分代收集（Generational Collection）

- 新生代：复制算法（存活率低，复制成本低）
- 老年代：标记-整理（存活率高，减少移动）

---

## 四、GC 收集器

### 4.1 收集器总览

| 收集器 | 作用区域 | 算法 | 特点 | 适用场景 |
|--------|---------|------|------|---------|
| Serial | 新生代 | 复制 | 单线程 STW | 单核、客户端 |
| ParNew | 新生代 | 复制 | Serial 多线程版 | CMS 搭档 |
| Parallel Scavenge | 新生代 | 复制 | 关注吞吐量 | 后台计算 |
| Serial Old | 老年代 | 标记-整理 | 单线程 | CMS 后备 |
| Parallel Old | 老年代 | 标记-整理 | 关注吞吐量 | 吞吐量优先 |
| **CMS** | 老年代 | 标记-清除 | 并发低停顿 | 延迟敏感 |
| **G1** | 整体 | 局部复制 | 可预测停顿 | JDK 9+ 默认 |
| **ZGC** | 整体 | 染色指针 | 亚毫秒 STW | 超大堆 |

### 4.2 CMS 收集器

**工作流程**：初始标记(STW) → 并发标记 → 重新标记(STW) → 并发清除

特点：
- 并发低停顿，但**产生碎片**
- 浮动垃圾（并发标记期间产生的垃圾，本次 GC 处理不了）
- 预留内存不够时触发 **Concurrent Mode Failure**，退化为 Serial Old 单线程 STW 整理

参数：`-XX:+UseConcMarkSweepGC`（JDK 9 起废弃，JDK 14 移除）

### 4.3 G1 收集器（Garbage First）

**设计思想**：将堆划分为 2048 个 Region，每个 Region 在 Eden/Survivor/Old/Humongous 之间动态切换。

**工作流程**：
1. 初始标记(STW)：标记 GC Roots 直接可达对象
2. 并发标记：从 GC Roots 开始遍历
3. 最终标记(STW)：处理 SATB 缓冲区
4. 筛选回收(STW)：根据 Region 的回收价值（垃圾最多 Region 优先）

**特点**：
- 可预测停顿：`-XX:MaxGCPauseMillis=200` 控制最大停顿时间
- 无碎片：局部复制算法
- JDK 9 默认收集器

### 4.4 ZGC 收集器

**设计思想**：染色指针（Colored Pointers）— 将指针的 64 位中的高 4 位用于标记状态，实现**并发标记、并发重定位、并发重映射**。

**特点**：
- 停顿时间 < 1ms（与堆大小无关）
- 支持 4TB ~ 16TB 超大堆
- JDK 21 正式 GA（生产可用）

---

## 五、GC 调优常见参数

```bash
# 堆大小
-Xms4g -Xmx4g
# 新生代
-Xmn2g
# 元空间
-XX:MetaspaceSize=256m -XX:MaxMetaspaceSize=256m
# GC 收集器
-XX:+UseG1GC
# G1 停顿目标
-XX:MaxGCPauseMillis=200
# GC 日志（JDK 9+）
-Xlog:gc*:file=gc.log:time,uptime
# 内存溢出 dump
-XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/path/dump.hprof
```

---

## 六、内存泄漏排查实战

### 6.1 常见泄漏场景

1. **静态集合类**：`static List<Object>` 只增不减
2. **未关闭资源**：JDBC、IO 流、HTTP 连接未关闭
3. **内部类持有外部类引用**：非静态内部类隐式持有 `this$0`
4. **ThreadLocal 未 remove**：线程池中的线程复用，`ThreadLocalMap` 中的 Entry 仍然存在
5. **String.intern() 滥用**：大量字符串入池导致元空间或堆溢出

### 6.2 排查工具

```bash
# 1. jps 查看 Java 进程
jps -l

# 2. jstat 查看 GC 情况
jstat -gcutil <pid> 1000 10    # 每秒查看 GC 利用率

# 3. jmap 生成堆 dump
jmap -dump:format=b,file=heap.hprof <pid>

# 4. jstack 查看线程栈
jstack <pid> > thread.dump

# 5. 用 MAT (Memory Analyzer Tool) 或 VisualVM 分析 dump 文件
#    查找可疑的大对象和 GC Roots 引用链
```

### 6.3 实战：OOM 排查

```bash
# 启动时加上参数
java -Xms256m -Xmx256m -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=./dump.hprof -jar app.jar
```

拿到 dump.hprof 后用 MAT 打开，查看 `Leak Suspects` 报告。

---

## 七、面试高频题

| 题目 | 核心要点 |
|------|---------|
| 对象在堆中的分配过程？ | 栈上分配 → Eden（TLAB）→ Minor GC → S0/S1 → Old |
| 什么是 TLAB？ | Thread Local Allocation Buffer，线程私有 Eden 区域，减少竞争 |
| 什么是 STW？ | Stop The World，GC 时所有用户线程暂停 |
| 哪些对象进入老年代？ | 大对象（`-XX:PretenureSizeThreshold`）、长期存活（年龄 15 默认） |
| CMS 和 G1 怎么选？ | G1 是 JDK 9+ 默认，CMS 已废弃；G1 可预测停顿，适合大堆 |
| 元空间和永久代区别？ | 元空间用本地内存，不再 OOM（除非物理内存不够） |
| StringTable 在哪里？ | JDK 7+ 在堆中，不在方法区 |

> 进入下一节：JUC 并发编程 — 从 synchronized 到 AQS 再到线程池。