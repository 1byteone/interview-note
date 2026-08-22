package com.example.jvm;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * MemoryDemo —— JVM 内存模型演示
 *
 * 演示内容：
 * 1. 对象创建与垃圾回收（GC）
 * 2. 栈 vs 堆：栈上分配 vs 堆上分配
 * 3. String 常量池（String intern pool）
 * 4. 模拟 OutOfMemoryError（带警告注释，默认不执行）
 *
 * 运行建议：使用 -Xmx 和 -Xms 参数
 *   java -Xmx256m -Xms256m -XX:+PrintGCDetails com.example.jvm.MemoryDemo
 */
public class MemoryDemo {

    // 类变量（静态变量）：存放在方法区（JDK 8+ 元空间 Metaspace）
    // 所有实例共享，随类加载而创建，类卸载而回收
    private static final String TAG = "MemoryDemo";
    private static int classCounter = 0;

    // 实例变量：存放在堆中，随对象创建而分配
    private final String id;
    private final byte[] data;  // 大对象，用于演示堆内存分配

    public MemoryDemo(int size) {
        this.id = UUID.randomUUID().toString().substring(0, 8);
        // 在堆上分配一个字节数组
        this.data = new byte[size];
        classCounter++;
    }

    public static void main(String[] args) {
        MemoryDemo demo = new MemoryDemo(1);  // 创建一个对象，触发堆分配
        demo.demoObjectCreation();
        demo.demoStackVsHeap();
        demo.demoStringIntern();
        // demo.demoOutOfMemory();  // 默认注释，取消注释以模拟 OOM
        System.out.println("\n程序正常结束");
    }

    // ============================================================
    // 1. 对象创建与 GC
    // ============================================================
    public void demoObjectCreation() {
        System.out.println("=== 1. 对象创建与 GC 演示 ===");

        // 在循环中创建大量临时对象，触发 GC
        List<MemoryDemo> tempList = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            // 每次循环创建一个新对象（在堆中分配内存）
            MemoryDemo obj = new MemoryDemo(1024);  // 每个对象带 1KB 数组
            tempList.add(obj);
        }
        System.out.println("创建了 " + tempList.size() + " 个临时对象");

        // 清除引用，对象变为"不可达"，等待 GC 回收
        tempList.clear();
        System.out.println("清空引用后，对象变为 GC Root 不可达，可被回收");

        // 建议 JVM 执行 GC（但不保证立即执行）
        System.gc();
        System.out.println("调用 System.gc() 建议 JVM 执行垃圾回收");

        // 在循环中创建对象，部分对象会在 Eden 区分配，部分会晋升到 Survivor/Old
        for (int i = 0; i < 10; i++) {
            // 每个对象持有 1MB 数据，观察 GC 日志
            MemoryDemo big = new MemoryDemo(1024 * 1024);
            System.out.println("  创建大对象 #" + (i + 1) + " (id=" + big.id + ")");
        }

        // 每个对象都有一个 finalize() 方法（不推荐使用，仅演示）
        // GC 回收前会调用 finalize()，但不应依赖它释放资源
        System.out.println("注意: GC 回收时机不可预测，由 JVM 决定");
    }

    // ============================================================
    // 2. 栈 vs 堆
    // ============================================================
    public void demoStackVsHeap() {
        System.out.println("\n=== 2. 栈 vs 堆 内存分配 ===");

        // 栈（Stack）：
        // - 每个线程私有，存储：局部变量、方法参数、返回地址
        // - 基本类型（int, double, boolean 等）的值直接存在栈上
        // - 引用类型变量的"引用"存在栈上，"对象"存在堆上
        // - 方法调用结束自动释放，不需要 GC
        //
        // 堆（Heap）：
        // - 所有线程共享，存储所有对象实例和数组
        // - 分为：新生代（Eden, Survivor0, Survivor1）和 老年代
        // - 需要 GC 回收不再使用的对象

        int localVar = 42;  // 基本类型，值直接存储在栈上
        MemoryDemo heapObj = new MemoryDemo(1024);  // 引用在栈上，对象在堆上

        System.out.println("栈上分配: int localVar = " + localVar);
        System.out.println("栈上引用 + 堆上对象: MemoryDemo heapObj = " + heapObj.id);

        // 方法调用（栈帧压栈）
        int result = add(7, 8);  // add 方法创建新的栈帧
        System.out.println("方法调用栈帧: add(7, 8) = " + result);

        // 递归调用可能导致栈溢出（StackOverflowError）
        // 可尝试调用：recursiveCall(0);  // 默认递归深度有限
    }

    // 方法调用创建栈帧，存放局部变量和操作数
    private int add(int a, int b) {
        // a, b, result 都在当前栈帧中
        int result = a + b;
        return result;
    }

    // 模拟递归调用，演示栈溢出（默认注释，谨慎使用）
    private void recursiveCall(int depth) {
        System.out.println("递归深度: " + depth);
        // 不设终止条件，最终 StackOverflowError
        recursiveCall(depth + 1);
    }

    // ============================================================
    // 3. String 常量池
    // ============================================================
    public void demoStringIntern() {
        System.out.println("\n=== 3. String 常量池（String Intern Pool）===");

        // String 常量池（JDK 7+ 移至堆中）：
        // - 字符串字面量自动入池
        // - intern() 方法：手动将字符串入池或返回池中已有的引用
        // - 节省内存: 相同的字符串只存储一份

        // 场景1：字面量字符串（自动入池）
        String s1 = "hello";            // 池中创建 "hello"
        String s2 = "hello";            // 直接复用池中已有的 "hello"
        System.out.println("字面量比较: s1 == s2 ? " + (s1 == s2));  // true（同一对象）

        // 场景2：new 创建字符串（堆中新建对象，不自动入池）
        String s3 = new String("hello");  // 堆中创建新对象
        System.out.println("new 比较: s1 == s3 ? " + (s1 == s3));  // false（不同对象）
        System.out.println("equals 比较: s1.equals(s3) ? " + s1.equals(s3));  // true（内容相同）

        // 场景3：intern() 手动入池
        String s4 = s3.intern();  // 返回池中的引用（即 s1 的引用）
        System.out.println("intern 比较: s1 == s4 ? " + (s1 == s4));  // true（池中同一对象）

        // 场景4：字符串拼接
        String s5 = "hel" + "lo";  // 编译期优化，等同于 "hello"
        System.out.println("编译期拼接: s1 == s5 ? " + (s1 == s5));  // true

        String s6 = "hel";
        String s7 = s6 + "lo";  // 运行时拼接，new StringBuilder().toString()
        System.out.println("运行时拼接: s1 == s7 ? " + (s1 == s7));  // false
        System.out.println("运行时拼接 intern: s1 == s7.intern() ? " + (s1 == s7.intern()));  // true

        // 场景5：大量字符串创建优化
        // 不使用 intern：
        long start = System.currentTimeMillis();
        List<String> list = new ArrayList<>();
        for (int i = 0; i < 10000; i++) {
            // 每次创建新对象，有大量重复内容
            list.add(new String("repeated-data-" + (i % 100)));
        }
        long time1 = System.currentTimeMillis() - start;
        System.out.println("不使用 intern: " + list.size() + " 个字符串, 耗时 " + time1 + "ms");

        // 使用 intern：减少内存占用
        start = System.currentTimeMillis();
        List<String> internList = new ArrayList<>();
        for (int i = 0; i < 10000; i++) {
            // 重复内容复用池中对象
            internList.add(new String("repeated-data-" + (i % 100)).intern());
        }
        long time2 = System.currentTimeMillis() - start;
        System.out.println("使用 intern: " + internList.size() + " 个字符串, 耗时 " + time2 + "ms");
    }

    // ============================================================
    // 4. 模拟 OutOfMemoryError（带警告，默认注释）
    // ============================================================
    private void demoOutOfMemory() {
        System.out.println("\n=== 4. 模拟 OutOfMemoryError ===");
        System.out.println("警告: 即将触发 OOM，请确保设置了 -Xmx 参数");

        // 模拟堆内存溢出
        List<byte[]> heapOOM = new ArrayList<>();
        try {
            while (true) {
                // 每次分配 10MB，直到堆满
                heapOOM.add(new byte[10 * 1024 * 1024]);
                System.out.println("已分配: " + heapOOM.size() + "0MB");
            }
        } catch (OutOfMemoryError e) {
            System.err.println("捕获到 OutOfMemoryError: " + e.getMessage());
        }

        // 补充：其他 OOM 场景
        // 1. 栈溢出：StackOverflowError（递归太深）
        // 2. 元空间溢出：Metaspace（CGLIB 动态生成大量类）
        // 3. 直接内存溢出：DirectBuffer（NIO 使用不当）
        // 4. GC 开销超限：GC overhead limit exceeded（98% 时间用于 GC 但回收不到 2% 内存）
    }
}