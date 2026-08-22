# 代码题 — 手写 LRU Cache · 生产者消费者 · 单例模式

> 等级：🎯 面试进阶
> 目标：面试中高频出现的代码题，考察对 Java 核心 API 的熟练程度和并发编程能力。

---

## 一、手写 LRU Cache

### 题目

实现一个 LRU（最近最少使用）缓存，支持 `get(key)` 和 `put(key, value)`，时间复杂度 O(1)。

### 解法 1：LinkedHashMap（面试可用的简单实现）

```java
class LRUCache<K, V> extends LinkedHashMap<K, V> {
    private final int capacity;

    public LRUCache(int capacity) {
        // accessOrder=true 表示按访问顺序排序
        super(capacity, 0.75f, true);
        this.capacity = capacity;
    }

    @Override
    protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
        // 超过容量时自动移除最久未访问的条目
        return size() > capacity;
    }

    public V get(Object key) {
        return super.getOrDefault(key, null);
    }

    public V put(K key, V value) {
        return super.put(key, value);
    }
}
```

### 解法 2：手写 HashMap + 双向链表（考察源码理解）

```java
class LRUCache {
    // 双向链表节点
    static class Node {
        int key, value;
        Node prev, next;
        Node(int key, int value) { this.key = key; this.value = value; }
    }

    private final int capacity;
    private final HashMap<Integer, Node> map = new HashMap<>();
    private final Node head = new Node(-1, -1);  // 虚拟头
    private final Node tail = new Node(-1, -1);  // 虚拟尾

    public LRUCache(int capacity) {
        this.capacity = capacity;
        head.next = tail;
        tail.prev = head;
    }

    public int get(int key) {
        Node node = map.get(key);
        if (node == null) return -1;
        moveToHead(node);  // 访问后移到头部
        return node.value;
    }

    public void put(int key, int value) {
        Node node = map.get(key);
        if (node != null) {
            node.value = value;
            moveToHead(node);
        } else {
            node = new Node(key, value);
            map.put(key, node);
            addToHead(node);
            if (map.size() > capacity) {
                Node removed = removeTail();
                map.remove(removed.key);
            }
        }
    }

    private void addToHead(Node node) {
        node.prev = head;
        node.next = head.next;
        head.next.prev = node;
        head.next = node;
    }

    private void removeNode(Node node) {
        node.prev.next = node.next;
        node.next.prev = node.prev;
    }

    private void moveToHead(Node node) {
        removeNode(node);
        addToHead(node);
    }

    private Node removeTail() {
        Node node = tail.prev;
        removeNode(node);
        return node;
    }
}
```

### 面试要点

- 问：为什么用双向链表？答：链表删除需要 O(1) 找到前驱节点，单向链表需要 O(n) 遍历
- 问：HashMap 为什么存 key 而不是用 Node 做 key？答：key 是用户传入的，Node 是内部实现，需要用 key 快速查找节点
- 问：LinkedHashMap 的 accessOrder 原理？答：每次访问节点时调用 `afterNodeAccess`，将该节点移到链表尾部

---

## 二、手写生产者消费者

### 题目

实现一个生产者-消费者模式，生产者生产数据，消费者消费数据，使用阻塞队列或 wait/notify。

### 解法 1：BlockingQueue（推荐）

```java
class ProducerConsumer {
    private static final BlockingQueue<Integer> queue = new LinkedBlockingQueue<>(10);
    private static volatile boolean running = true;

    static class Producer implements Runnable {
        private final String name;
        Producer(String name) { this.name = name; }

        @Override
        public void run() {
            int data = 0;
            try {
                while (running && !Thread.currentThread().isInterrupted()) {
                    int value = data++;
                    queue.put(value);  // 队列满时阻塞
                    System.out.println(name + " 生产: " + value);
                    Thread.sleep(500);  // 模拟生产耗时
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    static class Consumer implements Runnable {
        private final String name;
        Consumer(String name) { this.name = name; }

        @Override
        public void run() {
            try {
                while (running && !Thread.currentThread().isInterrupted()) {
                    Integer value = queue.take();  // 队列空时阻塞
                    System.out.println(name + " 消费: " + value);
                    Thread.sleep(1000);  // 模拟消费耗时
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public static void main(String[] args) throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(5);
        executor.submit(new Producer("P1"));
        executor.submit(new Producer("P2"));
        executor.submit(new Consumer("C1"));
        executor.submit(new Consumer("C2"));
        executor.submit(new Consumer("C3"));

        Thread.sleep(5000);
        running = false;
        executor.shutdownNow();
    }
}
```

### 解法 2：wait/notify（考察底层原理）

```java
class ProducerConsumerWaitNotify {
    private static final List<Integer> buffer = new ArrayList<>();
    private static final int MAX_SIZE = 10;
    private static final Object lock = new Object();

    static class Producer implements Runnable {
        @Override
        public void run() {
            int data = 0;
            try {
                while (true) {
                    synchronized (lock) {
                        while (buffer.size() == MAX_SIZE) {
                            lock.wait();  // 队列满，等待
                        }
                        buffer.add(data++);
                        lock.notifyAll();  // 唤醒消费者
                    }
                    Thread.sleep(500);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    static class Consumer implements Runnable {
        @Override
        public void run() {
            try {
                while (true) {
                    synchronized (lock) {
                        while (buffer.isEmpty()) {
                            lock.wait();  // 队列空，等待
                        }
                        int value = buffer.remove(0);
                        System.out.println("消费: " + value);
                        lock.notifyAll();  // 唤醒生产者
                    }
                    Thread.sleep(1000);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
```

### 面试要点

- 问：为什么 wait 要用 while 而不是 if？答：**防止虚假唤醒**（spurious wakeup），while 再次检查条件
- 问：为什么 notifyAll 而不是 notify？答：notify 可能唤醒同类型线程，导致死锁（如唤醒的全是生产者）
- 问：BlockingQueue 原理？答：内部使用 ReentrantLock + Condition，`put` 等待 notFull，`take` 等待 notEmpty

---

## 三、手写单例模式（双重检查 + volatile）

### 题目

实现线程安全的单例模式，要求延迟加载，且性能最优。

### 解法：双重检查锁（DCL）

```java
public class Singleton {
    // volatile 禁止指令重排，保证 instance 在完全初始化后才可见
    private static volatile Singleton instance;

    private Singleton() {
        // 防止反射攻击
        if (instance != null) {
            throw new IllegalStateException("已初始化");
        }
    }

    public static Singleton getInstance() {
        if (instance == null) {                     // 第一次检查（无锁）
            synchronized (Singleton.class) {
                if (instance == null) {             // 第二次检查（加锁）
                    instance = new Singleton();     // 问题：这一步不是原子操作
                }
            }
        }
        return instance;
    }
}
```

### 为什么需要 volatile？

`instance = new Singleton()` 在 JVM 中分为三步：

```java
memory = allocate();      // 1. 分配内存
init(memory);             // 2. 初始化对象
instance = memory;         // 3. 设置引用指向内存
```

如果没有 volatile，JIT 可能重排为 1-3-2，另一个线程在第一次检查时发现 `instance != null`，直接返回使用的对象可能未初始化完毕。

### 其他单例实现对比

```java
// 饿汉式（类加载时初始化）
public class SingletonEager {
    private static final SingletonEager INSTANCE = new SingletonEager();
    private SingletonEager() {}
    public static SingletonEager getInstance() { return INSTANCE; }
}
// 优点：简单、线程安全
// 缺点：类加载即初始化，可能浪费资源

// 静态内部类（推荐：延迟加载 + 线程安全）
public class SingletonHolder {
    private SingletonHolder() {}
    private static class Holder {
        static final SingletonHolder INSTANCE = new SingletonHolder();
    }
    public static SingletonHolder getInstance() { return Holder.INSTANCE; }
}
// 优点：JVM 保证类加载时初始化，无需 synchronized
// 原理：内部类 Holder 在 getInstance 首次调用时加载，JVM 保证类加载是线程安全的

// 枚举（最安全：防止反射/序列化攻击）
public enum SingletonEnum {
    INSTANCE;
    public void doSomething() { ... }
}
// 优点：JVM 保证枚举单例，反射和序列化都无法破坏
```

### 面试要点

- 问：DCL 为什么两次检查？答：第一次无锁判断提升性能，第二次加锁保证线程安全
- 问：静态内部类为什么线程安全？答：JVM 类加载机制保证 `clinit` 方法执行时只有一个线程
- 问：枚举单例为什么最安全？答：JVM 规范禁止反射创建枚举实例，序列化也保证单例

---

## 代码题总结

| 题目 | 核心考点 | 最佳解法 |
|------|---------|---------|
| LRU Cache | 数据结构设计 + O(1) 操作 | HashMap + 双向链表 |
| 生产者消费者 | 线程通信 + 阻塞同步 | BlockingQueue 或 wait/notify |
| 单例模式 | 并发正确性 + 防止反射 | 枚举或静态内部类 |

> 进入推荐资源篇：书籍、视频、网站推荐。