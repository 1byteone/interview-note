# 集合框架 — ArrayList · LinkedList · HashMap · ConcurrentHashMap

> 等级：👶 新手入门 → 🎯 面试进阶
> 目标：从源码层面理解 Java 集合框架的核心实现，面试高频占比 80% 以上。

---

## 一、集合体系总览

```
Collection  (接口)
├── List          ← 有序、可重复
│   ├── ArrayList      ← 数组实现，读快写慢
│   ├── LinkedList     ← 双向链表，写快读慢
│   └── Vector         ← 线程安全已过时（Stack 也不推荐）
├── Set           ← 无序、不可重复
│   ├── HashSet        ← 基于 HashMap
│   ├── LinkedHashSet  ← 保持插入顺序
│   └── TreeSet        ← 自然排序 / Comparator
└── Queue         ← 队列
    ├── PriorityQueue  ← 堆实现
    ├── ArrayDeque     ← 双端队列
    └── LinkedList     ← 也是 Queue

Map  (接口)
├── HashMap         ← 最常用，数组+链表+红黑树
├── LinkedHashMap   ← 保持插入/访问顺序
├── TreeMap         ← 排序
├── ConcurrentHashMap ← 线程安全（必问）
└── Hashtable       ← 过时，全表锁
```

---

## 二、ArrayList vs LinkedList

### 2.1 数据结构

- **ArrayList**：`Object[] elementData`，连续内存，支持随机访问
- **LinkedList**：`Node<E>` 双向链表，离散内存，不支持随机访问

### 2.2 性能对比

| 操作 | ArrayList | LinkedList |
|------|-----------|------------|
| 尾部插入 | O(1) 均摊 | O(1) |
| 指定位置插入 | O(n) 移动元素 | O(n) 遍历查找 |
| 随机访问 get(i) | **O(1)** | O(n) |
| 内存占用 | 数组本身+空闲容量 | 每个元素多 2 个指针（24+ 字节） |

### 2.3 ArrayList 扩容机制

```java
// 关键源码 (JDK 17)
private Object[] grow(int minCapacity) {
    int oldCapacity = elementData.length;
    int newCapacity = oldCapacity + (oldCapacity >> 1);  // 1.5 倍
    if (newCapacity - minCapacity < 0)
        newCapacity = minCapacity;
    if (newCapacity - MAX_ARRAY_SIZE > 0)
        newCapacity = hugeCapacity(minCapacity);
    return elementData = Arrays.copyOf(elementData, newCapacity);
}
```

扩容 1.5 倍，调用 `Arrays.copyOf` 完成数组复制。预分配容量可避免频繁扩容：

```java
// 已知 1000 个元素，预先指定容量
List<String> list = new ArrayList<>(1000);
```

### 2.4 面试题

> **ArrayList 的 `subList` 返回的是什么？**

返回的是 `SubList` 视图，不是独立副本。修改 SubList 会影响原 ArrayList。

> **ArrayList 和 LinkedList 各适合什么场景？**

ArrayList 适合"读多写少"的尾部追加场景；LinkedList 适合"频繁头尾操作"的场景（如队列、双端队列）。

---

## 三、HashMap 原理（JDK 1.8 版本）

### 3.1 数据结构

```
数组 + 链表 + 红黑树
```

- **数组**：`Node<K,V>[] table`，长度总是 2 的幂
- **链表**：hash 冲突时用链表存储（拉链法）
- **红黑树**：链表长度 >= 8 且数组长度 >= 64 时树化

### 3.2 put 流程

```java
public V put(K key, V value) {
    return putVal(hash(key), key, value, false, true);
}

final V putVal(int hash, K key, V value, ...) {
    // 1. 数组为空则 resize 初始化
    // 2. 计算索引 (n - 1) & hash
    // 3. 如果位置为空，直接插入
    // 4. 如果位置不为空：
    //    a. 如果 key 相等，覆盖
    //    b. 如果是红黑树节点，插入树
    //    c. 如果是链表，遍历链表，找到尾部插入
    // 5. 如果链表长度 >= 8，尝试树化（需数组长度 >= 64）
    // 6. 如果 size > threshold，resize 扩容
}
```

### 3.3 hash 函数

```java
static final int hash(Object key) {
    int h;
    return (key == null) ? 0 : (h = key.hashCode()) ^ (h >>> 16);
}
```

高 16 位不变，低 16 位与高 16 位异或，让高 16 位也参与寻址计算，减少冲突。

### 3.4 resize 扩容

- 容量翻倍（2 倍），`threshold = 容量 * 0.75`
- 旧数据迁移：重新计算索引 `(n - 1) & hash`
- **1.8 优化**：元素在新数组的位置要么在原位置，要么在"原位置 + 旧容量"
  - 因为扩容后 `n-1` 多了一位 1，看 hash 的这一位是 0 还是 1 即可

### 3.5 红黑树

- 链表长度 >= 8 树化，长度 <= 6 退化为链表（中间值 7 防抖动）
- 树化前提：数组长度 >= 64；如果数组长度 < 64 但链表 >= 8，优先扩容

### 3.6 容量为什么是 2 的幂？

```java
// 取模运算 hash % n 等价于 (n - 1) & hash，前提 n 是 2 的幂
// 位运算比取模快得多
```

---

## 四、ConcurrentHashMap

### 4.1 1.7 vs 1.8 核心区别

| 维度 | JDK 1.7 | JDK 1.8 |
|------|---------|---------|
| 数据结构 | Segment 数组 + HashEntry 数组 | Node 数组 + 链表/红黑树 |
| 并发粒度 | Segment（分段锁，默认 16） | 数组元素（桶粒度） |
| 锁机制 | ReentrantLock | **CAS + synchronized** |
| 扩容 | 每个 Segment 独立扩容 | 整个数组一起扩容 |
| 读操作 | 不加锁（volatile 保证可见性） | 不加锁（volatile 保证可见性） |

### 4.2 1.8 put 流程

```java
final V putVal(K key, V value, boolean onlyIfAbsent) {
    // 1. 计算 hash
    // 2. 死循环（CAS 自旋保证）
    // 3. 数组为空 -> initTable()
    // 4. 当前桶为空 -> CAS 直接插入（乐观锁）
    // 5. 正在进行扩容 -> helpTransfer() 协助扩容
    // 6. 当前桶有元素 -> synchronized 锁住桶头节点
    //    a. 链表遍历
    //    b. 红黑树插入
    // 7. 超过转换阈值 -> treeifyBin()
}
```

### 4.3 sizeCtl 关键变量

- `-1`：正在初始化
- `-N`：有 N-1 个线程正在扩容
- 正数：下一次扩容的阈值（`容量 * 0.75`）
- 负数（低位）：`RESIZE_STAMP_SHIFT` 相关，扩容标记

### 4.4 并发扩容原理

1. 多个线程可**协助扩容**（`helpTransfer`）
2. 扩容时每个线程负责一个"区间"（stride），`transfer` 方法用 `ForwardingNode` 标记已迁移的桶
3. 新桶访问时，发现 `ForwardingNode` 则通过 `find` 方法转发到新数组

### 4.5 面试高频题

> **ConcurrentHashMap 的 size() 如何统计？**

通过 `sumCount()` 方法，累加 `baseCount` 和 `CounterCell[]` 数组，避免并发修改时的计数偏差。

> **ConcurrentHashMap 的 key 和 value 为什么不能为 null？**

作者 Doug Lea 的解释：如果 `map.get(key)` 返回 null，无法区分是 key 不存在还是 value 本身为 null。HashMap 允许 null 是因为单线程环境可以 `containsKey` 检查，而并发环境下 `containsKey` 和 `get` 之间可能被修改。

---

## 五、Collections 工具类

### 5.1 常用方法

```java
// 不可变集合
List<String> unmodifiable = Collections.unmodifiableList(list);

// 同步包装
List<String> syncList = Collections.synchronizedList(new ArrayList<>());

// 空集合
List<String> empty = Collections.emptyList();

// 单元素集合
Set<String> singleton = Collections.singleton("only");

// 排序
Collections.sort(list);

// 二分查找（需先排序）
int idx = Collections.binarySearch(list, key);
```

---

## 六、面试高频题

| 题目 | 核心要点 |
|------|---------|
| HashMap 为什么用红黑树？ | 链表 O(n) 太慢，红黑树 O(log n)，树化阈值 8 来自泊松分布 |
| HashMap 扩容时死循环？ | 1.7 头插法在多线程下成环，1.8 尾插法修复 |
| 为什么重写 equals 必须重写 hashCode？ | 保证 HashMap 中相同对象有相同桶索引，否则找不到 |
| ConcurrentHashMap 读不需要锁？ | volatile 数组 + 链表节点本身 volatile 保证可见性 |
| LinkedHashMap 如何实现 LRU？ | `accessOrder=true` 时，`afterNodeAccess` 将访问节点移到尾部 |

> 进入下一节：JVM 内存模型与 GC 原理。