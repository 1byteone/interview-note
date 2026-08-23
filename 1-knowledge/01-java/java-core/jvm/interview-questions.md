# JVM 面试题大全

## 📚 知识体系

```
JVM 内存区域
├── 堆 (Heap)
│   ├── 新生代 (Eden / S0 / S1)
│   └── 老年代
├── 方法区 / 元空间
├── 虚拟机栈
├── 本地方法栈
├── 程序计数器
└── 直接内存

垃圾回收 (GC)
├── 可达性分析
├── 引用类型（强/软/弱/虚）
├── 标记清除 / 标记复制 / 标记整理
├── 分代收集
├── Serial / Parallel / CMS / G1 / ZGC
└── 调优参数

类加载机制
├── 加载 / 验证 / 准备 / 解析 / 初始化
├── 双亲委派模型
├── 类加载器（Bootstrap / Extension / App / 自定义）
└── 打破双亲委派

JVM 调优
├── 内存参数
├── GC 日志分析
├── 堆 dump 分析
├── 性能监控工具
└── OOM 排查
```

---

## 🎯 Level 1：基础题

### 1. JVM 内存区域有哪些？哪些是线程共享的？
**答案**：

| 内存区域 | 作用 | 线程共享 |
|----------|------|----------|
| 堆（Heap） | 存放对象实例 | ✅ 共享 |
| 方法区/元空间 | 类信息、常量、静态变量 | ✅ 共享 |
| 虚拟机栈 | 每个线程的栈帧（局部变量） | ❌ 私有 |
| 本地方法栈 | native 方法调用栈 | ❌ 私有 |
| 程序计数器 | 记录当前执行的字节码行号 | ❌ 私有 |

**Java 8+ 变化**：方法区改为**元空间（Metaspace）**，使用本地内存（默认无上限）。

### 2. 什么是双亲委派模型？
**答案**：
当一个类加载器收到类加载请求时，**先交给父加载器加载**，父加载器加载失败才自己加载。

```text
Bootstrap ClassLoader（核心库）
    ↑
Extension ClassLoader（扩展库）
    ↑
Application ClassLoader（classpath）
    ↑
自定义 ClassLoader
```

**好处**：
1. 防止重复加载（唯一性）
2. 防止核心类被篡改（安全）

**打破双亲委派**：重写 `findClass` 或 `loadClass`（如 Tomcat、SPI）。

---

## 🎯 Level 2：进阶题

### 3. GC 如何判断对象可回收？
**答案**：

**可达性分析（主流）**：
- 以 GC Roots 为起点，向下搜索
- 不可达的对象判定为可回收

**GC Roots 包括**：
- 虚拟机栈中引用的对象
- 方法区静态属性引用的对象
- 方法区常量引用的对象
- native 方法引用的对象
- 活跃线程、同步锁 monitor 持有的对象

**引用类型**：
| 引用 | 回收时机 | 典型应用 |
|------|----------|----------|
| 强引用 | 永不回收（OOM） | new Object() |
| 软引用 | 内存不足回收 | 缓存（图片） |
| 弱引用 | 下次 GC 回收 | ThreadLocal |
| 虚引用 | 随时可回收 | 对象回收跟踪 |

### 4. 分代收集算法是什么？
**答案**：

**新生代**（Eden : S0 : S1 = 8:1:1）
- **标记-复制算法**：将存活对象复制到另一块 S，Eden+S0 清空
- Minor GC：频繁、快

**老年代**
- **标记-清除**：不移动对象，有碎片
- **标记-整理**：移动存活对象，无碎片
- Major GC / Full GC：较少、慢

```text
新生代:
┌─────┬─────┬─────┐
│ Eden│ S0  │ S1  │   ← Minor GC 对象复制
└─────┴─────┴─────┘
    ↓ 年龄达到阈值（默认 15 岁）
老年代:
┌─────────────────┐
│   老年代对象     │   ← Major/Full GC
└─────────────────┘
```

---

## 🎯 Level 3：高级题

### 5. G1 收集器的原理？
**答案**：
G1（Garbage First）：Java 9+ 默认收集器，**区域化（Region）分代 + 可预测停顿**。

**核心思想**：
- 堆划分为多个 Region（2048 个左右）
- Region 分为：Eden、Survivor、Old、Humongous（大对象）
- 优先回收**垃圾最多的 Region**（Garbage First）

**执行过程**：
```text
初始标记 → 并发标记 → 最终标记 → 筛选回收（可预测停顿）
```

**优点**：
- 可预测停顿（指定目标停顿时间）
- 不产生连续碎片（Region 内复制整理）
- 并发执行（低 STW）

**参数**：
```bash
-XX:+UseG1GC
-XX:MaxGCPauseMillis=200
-XX:G1HeapRegionSize=16m
-XX:InitiatingHeapOccupancyPercent=45
```

### 6. 如何排查 OOM / 内存泄漏？
**答案**：

**排查步骤**：
```bash
# 1. 启动时开启堆转储
java -Xms512m -Xmx512m \
     -XX:+HeapDumpOnOutOfMemoryError \
     -XX:HeapDumpPath=/data/dumps/ \
     -jar app.jar

# 2. OOM 后分析 heap dump
# jvisualvm / MAT / JProfiler 分析
# 查看大对象、内存泄漏聚集点
```

**常见泄漏场景**：
1. ThreadLocal 未 remove（线程池复用导致）
2. 静态集合不断存对象
3. 未关闭的资源（连接、IO）
4. 大 List 长期持有引用
5. 缓存无过期策略

---

## 🎯 Level 4：专家题

### 7. ZGC 的原理？为什么延迟低？
**答案**：
ZGC（Java 11+ 实验性，15 正式）：**染色指针（Colored Pointers）+ 读屏障（Load Barrier）**。

**关键设计**：
1. **染色指针**：指针未使用的 4 位存储 GC 标记信息
2. **读屏障**：读对象时检查指针颜色，需要时处理
3. **并发整理**：标记、转移、重定位全部并发执行
4. **目标停顿 < 10ms**（与堆大小无关）

**JDK 17+ 建议**：
```bash
-XX:+UseZGC
-XX:ZCollectionInterval=30
```

### 8. Full GC 频繁如何定位？
**答案**：

**排查思路**：
```bash
# 1. 查看 GC 日志
-verbose:gc -XX:+PrintGCDetails

# 2. 确认老年代占用率
# 持续监控：jstat -gcutil <pid> 1000

# 3. 分析原因
┌─ 老年代容量不足 → -Xmx 调大
├─ 大对象直接进老年代 → 检查代码（一次性大 List）
├─ 内存泄漏 → heap dump 分析
├─ 元空间不足 → -XX:MaxMetaspaceSize
└─ 不合理的系统调用（如频繁 Full GC 的 concurrent mode)
```

**常用监控工具**：
- `jps` / `jstat` / `jmap` / `jstack`
- `jvisualvm` / `MAT` / `Arthas`（阿里）
- Prometheus + Grafana

---

## 📖 学习资源

### 推荐项目
- [JavaGuide JVM 部分](https://javaguide.cn/java/jvm/)
- [深入理解 Java 虚拟机（周志明）](https://book.douban.com/subject/34907497/)
- [Arthas](https://github.com/alibaba/arthas) - Java 诊断工具

### 最佳实践
1. 线上容器必须设置 `-Xmx` 限制，避免 OOM Kill
2. 合理设置堆大小（约容器内存的 50-70%）
3. 日志保留 GC 参数，便于排查
4. 新版本优先 G1/ZGC
5. 注意元空间、直接内存（NIO 大量使用）的配置