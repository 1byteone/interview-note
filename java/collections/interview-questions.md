# Java 集合框架 (Collections) 面试题大全

## 📚 知识体系

```
Java 集合框架
├── Collection 接口
│   ├── List（有序可重复）
│   │   ├── ArrayList（数组）
│   │   ├── LinkedList（双向链表）
│   │   ├── Vector（线程安全，已废弃）
│   │   └── CopyOnWriteArrayList（读写分离）
│   ├── Set（无序不可重复）
│   │   ├── HashSet（HashMap）
│   │   ├── LinkedHashSet（双向链表+HashMap）
│   │   └── TreeSet（红黑树）
│   └── Queue（队列）
│       ├── PriorityQueue（堆）
│       ├── ArrayDeque（双端队列）
│       └── BlockingQueue（阻塞队列）
├── Map 接口
│   ├── HashMap（数组+链表+红黑树）
│   ├── LinkedHashMap（双向链表+HashMap）
│   ├── TreeMap（红黑树）
│   ├── Hashtable（线程安全，已废弃）
│   └── ConcurrentHashMap（分段锁->CAS+synchronized）
└── 工具类
    ├── Collections（排序/查找/同步/不可变）
    └── Arrays（数组操作）
```

---

## 🎯 Level 1：基础题

### 1. ArrayList 和 LinkedList 的区别？
**答案**：

| 特性 | ArrayList | LinkedList |
|------|-----------|------------|
| 底层 | 动态数组 | 双向链表 |
| 随机访问 | O(1) | O(n) |
| 插入/删除 | O(n)（移动元素） | O(1)（修改指针） |
| 内存 | 连续内存 | 分散节点+额外指针 |
| 尾部插入 | O(1)（均摊） | O(1) |
| 适用场景 | 查多写少 | 写多查少 |

### 2. HashMap 的 put 流程？
**答案**：

```text
put(key, value)
    ↓
hash = hash(key)  // 扰动函数：高16位异或低16位
    ↓
i = (n - 1) & hash  // 计算桶索引
    ↓
桶为空 → 直接 new Node
    ↓ 已存在节点
equals 比较 key
    ├── 相同 → 覆盖 value
    └── 不同 → 链表尾插（JDK 8+）→ 长度>8 转红黑树
    ↓
检查负载因子（size > threshold）→ 扩容
```

**JDK 7 vs 8**：
| 区别 | JDK 7 | JDK 8+ |
|------|-------|--------|
| 插入 | 头插 | 尾插 |
| 红黑树 | 无 | 链表>8 转红黑树 |
| 扩容死锁 | 有（头插导致循环） | 无（尾插） |
| 扰动函数 | 4次 | 1次 |

---

## 🎯 Level 2：进阶题

### 3. HashMap 扩容机制？
**答案**：

**触发条件**：`size > threshold = capacity * loadFactor`（默认 16 × 0.75 = 12）

**扩容步骤**：
1. 新数组 = 旧数组容量 × 2
2. 重新计算每个元素的索引（`e.hash & (newCap - 1)`）
3. JDK 8+ 优化：元素在新数组的位置为 `原位置` 或 `原位置 + 旧容量`

**为什么是 2 的幂**：
- 用 `(n-1) & hash` 替代 `% n` 取模（位运算更快）
- 扩容后元素位置要么不变，要么 +oldCap

### 4. ConcurrentHashMap 如何保证线程安全？
**答案**：

**JDK 7**：Segment 分段锁（继承 ReentrantLock）
- 默认 16 个 Segment，每个 Segment 保护一个 HashEntry 数组
- 不同 Segment 可并发写入

**JDK 8+**：CAS + synchronized
- **put**：数组位置为空 → CAS 插入；不为空 → synchronized 锁链头
- **get**：不加锁（volatile 保证可见性）
- **size**：baseCount + CounterCell 分段计数

### 5. 红黑树的特点？
**答案**：
1. 节点是红色或黑色
2. 根节点是黑色
3. 叶子节点（NIL）是黑色
4. 红色节点的子节点都是黑色（不能连续红色）
5. 任一节点到所有叶子节点的路径包含相同数量黑节点

**HashMap 为什么用红黑树**：
- 链表长度 > 8 时，红黑树 O(log n) 优于链表 O(n)
- TreeNodes 占用空间是 Node 的 2 倍，所以长度 < 6 时转回链表

---

## 🎯 Level 3：高级题

### 6. HashMap 死循环（JDK 7）的原因？
**答案**：

**原因**：JDK 7 头插法 + 多线程并发扩容，导致**循环链表**

**场景**：两个线程同时扩容，线程 A 执行到 `next = e.next` 后挂起，线程 B 完成扩容（头插），A 恢复后形成循环

**JDK 8 修复**：改用尾插法，扩容后元素的相对顺序不变，避免死循环

### 7. LinkedHashMap 实现 LRU 缓存？
**答案**：

```java
class LRUCache<K, V> extends LinkedHashMap<K, V> {
    private final int capacity;

    public LRUCache(int capacity) {
        super(capacity, 0.75f, true);  // accessOrder = true
        this.capacity = capacity;
    }

    @Override
    protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
        return size() > capacity;  // 超过容量删除最旧的
    }
}
```

**原理**：`accessOrder = true` 开启访问顺序，每次 get 将节点移到链表尾部，`removeEldestEntry` 删除头部（最久未访问）。

---

## 📖 学习资源

### 推荐项目
- [JavaGuide 集合部分](https://javaguide.cn/java/collection/)
- [HashMap 源码分析（美团技术）](https://tech.meituan.com/2016/06/24/java-hashmap.html)

### 最佳实践
1. 明确知道初始容量（避免扩容损耗）
2. 多线程环境用 ConcurrentHashMap 而非 Hashtable
3. 迭代删除用 iterator.remove() 而非 for-each
4. 集合返回空用 `Collections.emptyList()` 而非 null