package com.example.collections;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * HashMapDemo —— Java 集合框架核心示例：Map 体系
 *
 * 演示内容：
 * 1. HashMap 的 put / get / remove 基本操作
 * 2. HashMap 的遍历方式（4 种）
 * 3. TreeMap 排序特性
 * 4. ConcurrentHashMap 线程安全
 * 5. HashMap 内部原理关键点（注释说明）
 */
public class HashMapDemo {

    public static void main(String[] args) {
        // ============================================================
        // 1. HashMap 基本操作
        // ============================================================
        System.out.println("=== 1. HashMap 基本操作 ===");

        // 创建 HashMap：默认初始容量 16，负载因子 0.75
        // 初始容量建议设置为预估元素数 / 0.75 + 1，避免频繁扩容
        Map<String, Integer> scores = new HashMap<>();

        // put: 添加键值对，返回旧值（如果 key 已存在）
        scores.put("张三", 85);
        scores.put("李四", 92);
        scores.put("王五", 78);
        Integer old = scores.put("张三", 88);  // 覆盖，返回旧值 85
        System.out.println("张三旧值: " + old);  // 85

        // get: 根据 key 获取 value，不存在返回 null
        Integer zhangScore = scores.get("张三");
        System.out.println("张三分数: " + zhangScore);  // 88

        // getOrDefault: 不存在时返回默认值
        Integer zhaoScore = scores.getOrDefault("赵六", 0);
        System.out.println("赵六分数（默认）: " + zhaoScore);  // 0

        // containsKey / containsValue: 判断是否存在
        System.out.println("包含张三? " + scores.containsKey("张三"));  // true
        System.out.println("包含100分? " + scores.containsValue(100));  // false

        // remove: 移除键值对
        scores.remove("王五");
        System.out.println("移除王五后: " + scores);

        // putIfAbsent: 仅当 key 不存在时插入
        scores.putIfAbsent("张三", 100);  // 已存在，不生效
        scores.putIfAbsent("赵六", 95);
        System.out.println("putIfAbsent 后: " + scores);

        // size / isEmpty / clear
        System.out.println("Map 大小: " + scores.size());  // 3
        System.out.println("是否为空: " + scores.isEmpty());

        // ============================================================
        // 2. HashMap 遍历方式（4 种）
        // ============================================================
        System.out.println("\n=== 2. HashMap 遍历方式 ===");

        // 重新填充数据
        Map<String, String> capitals = new HashMap<>();
        capitals.put("China", "Beijing");
        capitals.put("USA", "Washington DC");
        capitals.put("UK", "London");
        capitals.put("Japan", "Tokyo");
        capitals.put("France", "Paris");

        // 方式一：使用 entrySet 遍历（推荐，同时获取 key 和 value）
        System.out.println("方式1 - entrySet 遍历:");
        for (Map.Entry<String, String> entry : capitals.entrySet()) {
            System.out.println("  " + entry.getKey() + " -> " + entry.getValue());
        }

        // 方式二：使用 keySet 遍历（只取 key，需要时再 get value）
        System.out.println("方式2 - keySet 遍历:");
        for (String key : capitals.keySet()) {
            System.out.println("  " + key + " -> " + capitals.get(key));
        }
        // 注意：方式二每次 get 都会重新计算 hash，性能低于方式一

        // 方式三：使用 values() 遍历（只取 value）
        System.out.println("方式3 - values 遍历:");
        for (String value : capitals.values()) {
            System.out.println("  " + value);
        }

        // 方式四：使用 forEach (Java 8+ Lambda)
        System.out.println("方式4 - forEach Lambda:");
        capitals.forEach((key, value) ->
                System.out.println("  " + key + " -> " + value));

        // ============================================================
        // 3. TreeMap：排序的 Map（红黑树实现）
        // ============================================================
        System.out.println("\n=== 3. TreeMap 排序特性 ===");

        // 自然排序（按 key 的 Comparable 顺序）
        Map<String, Integer> treeMap = new TreeMap<>();
        treeMap.put("Banana", 2);
        treeMap.put("Apple", 5);
        treeMap.put("Cherry", 3);
        treeMap.put("Date", 1);

        System.out.println("TreeMap 自然排序（按字母顺序）:");
        treeMap.forEach((k, v) -> System.out.println("  " + k + " -> " + v));

        // 自定义排序：按字符串长度排序
        Map<String, String> customTree = new TreeMap<>(
                (a, b) -> {
                    int lenCmp = Integer.compare(a.length(), b.length());
                    return lenCmp != 0 ? lenCmp : a.compareTo(b);
                }
        );
        customTree.put("zoo", "动物园");
        customTree.put("zebra", "斑马");
        customTree.put("monkey", "猴子");
        customTree.put("cat", "猫");
        customTree.put("dog", "狗");

        System.out.println("TreeMap 自定义排序（按长度）:");
        customTree.forEach((k, v) -> System.out.println("  " + k + "(" + k.length() + ") -> " + v));

        // TreeMap 的导航方法
        TreeMap<String, Integer> nav = new TreeMap<>(treeMap);
        System.out.println("第一个 key: " + nav.firstKey());
        System.out.println("最后一个 key: " + nav.lastKey());
        System.out.println("小于 Cherry 的最大 key: " + nav.lowerKey("Cherry"));
        System.out.println("大于等于 Cherry 的最小 key: " + nav.ceilingKey("Cherry"));

        // ============================================================
        // 4. ConcurrentHashMap：线程安全的 HashMap
        // ============================================================
        System.out.println("\n=== 4. ConcurrentHashMap 线程安全 ===");

        // 创建 ConcurrentHashMap：分段锁（Java 7）/ CAS + synchronized（Java 8+）
        ConcurrentMap<String, Integer> concurrentMap = new ConcurrentHashMap<>();

        // 基本操作线程安全
        concurrentMap.put("key1", 1);
        concurrentMap.put("key2", 2);

        // 原子操作：putIfAbsent
        concurrentMap.putIfAbsent("key1", 100);  // 不生效，key1 已存在

        // 原子操作：replace
        concurrentMap.replace("key1", 1, 10);    // 仅当当前值为 1 时才替换为 10
        System.out.println("ConcurrentHashMap: " + concurrentMap);

        // 原子操作：computeIfAbsent（常用于缓存模式）
        Integer computed = concurrentMap.computeIfAbsent("key3", k -> {
            System.out.println("计算 key: " + k);
            return 3;
        });
        System.out.println("computeIfAbsent 结果: " + computed);

        // forEach 遍历（ConcurrentHashMap 支持并行阈值版本，此处用标准 forEach）
        concurrentMap.forEach((k, v) ->
                System.out.println("  " + k + " = " + v));

        // ============================================================
        // 5. HashMap 内部原理关键点（注释说明）
        // ============================================================
        System.out.println("\n=== 5. HashMap 内部原理（仅供理解，非代码） ===");

        /*
         * HashMap 底层原理（JDK 8+）：
         *
         * 1. 数据结构：数组 + 链表 + 红黑树
         *    - 数组：Node<K,V>[] table，默认容量 16
         *    - 链表：当多个 key 的 hash 冲突时，用链表存储（尾插法）
         *    - 红黑树：当链表长度 >= 8 且数组长度 >= 64 时，链表树化
         *
         * 2. hash 计算：
         *    - key.hashCode() 的高 16 位与低 16 位异或运算（扰动函数）
         *    - 使高位也参与寻址，减少碰撞
         *    - (n - 1) & hash 确定数组索引（n 为 2 的幂次）
         *
         * 3. 扩容机制：
         *    - 阈值 threshold = 容量 * 负载因子（默认 0.75）
         *    - 当元素个数超过阈值时，扩容为原来的 2 倍
         *    - 元素重新 hash 并分配到新数组
         *
         * 4. 红黑树退化为链表：
         *    - 当树中节点数 <= 6 时，退化为链表
         *
         * 5. 线程不安全：
         *    - 多线程同时 put 可能导致死循环（JDK 7 头插法）
         *    - JDK 8 改为尾插法，但仍有数据丢失问题
         *    - 多线程场景使用 ConcurrentHashMap
         */
        System.out.println("HashMap 原理说明请查看上方注释");
    }
}