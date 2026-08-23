package com.example.concurrency;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * ThreadPoolDemo —— 线程池与 CompletableFuture 示例
 *
 * 演示内容：
 * 1. 手动创建 ThreadPoolExecutor（理解核心参数）
 * 2. 提交 Callable 任务并通过 Future 获取结果
 * 3. CompletableFuture 异步编排：thenCompose / thenCombine / allOf
 * 4. 线程池的正确关闭
 */
public class ThreadPoolDemo {

    public static void main(String[] args) throws Exception {
        System.out.println("=== 1. 手动创建 ThreadPoolExecutor ===");

        // 手动创建线程池，理解 7 大核心参数
        // 为什么不用 Executors 工厂？生产环境建议手动创建，避免默认参数陷阱
        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                2,                          // corePoolSize: 核心线程数（常驻）
                4,                          // maximumPoolSize: 最大线程数
                60L, TimeUnit.SECONDS,      // keepAliveTime: 非核心线程空闲存活时间
                new LinkedBlockingQueue<>(10),  // workQueue: 任务队列（有界）
                new ThreadFactory() {
                    // 自定义线程工厂：给线程起有意义的名字，便于排查问题
                    private final AtomicInteger seq = new AtomicInteger(1);
                    @Override
                    public Thread newThread(Runnable r) {
                        Thread t = new Thread(r);
                        t.setName("demo-pool-" + seq.getAndIncrement());
                        t.setDaemon(false);
                        return t;
                    }
                },
                // 拒绝策略：任务爆满时的处理方式
                // AbortPolicy（默认，抛异常）| CallerRunsPolicy（调用者执行）
                // DiscardPolicy（静默丢弃）| DiscardOldestPolicy（丢弃最旧）
                new ThreadPoolExecutor.AbortPolicy()
        );

        System.out.println("初始状态 -> 核心线程数: " + pool.getPoolSize());

        // ========== 2. 提交 Callable 任务获取 Future ==========

        System.out.println("\n=== 2. 提交 Callable 任务 + Future ===");

        // Callable 与 Runnable 的区别：Callable 有返回值且可抛异常
        List<Future<Integer>> futures = new ArrayList<>();
        for (int i = 1; i <= 6; i++) {
            int taskId = i;
            // 提交一个返回平方值的任务
            Future<Integer> future = pool.submit(() -> {
                Thread.sleep(500);  // 模拟耗时任务
                System.out.println(Thread.currentThread().getName() + " 完成任务 #" + taskId);
                return taskId * taskId;
            });
            futures.add(future);
        }

        // get() 会阻塞等待任务完成；带超时的 get 避免无限等待
        int sum = 0;
        for (Future<Integer> f : futures) {
            sum += f.get(5, TimeUnit.SECONDS);  // 最多等待 5 秒
        }
        System.out.println("Future 求和结果: " + sum);  // 91

        // ========== 3. CompletableFuture 异步编排 ==========

        System.out.println("\n=== 3. CompletableFuture 组合编排 ===");

        // 使用带线程池的 CompletableFuture，避免默认 ForkJoinPool
        ExecutorService asyncPool = Executors.newFixedThreadPool(3);

        // --- 3.1 thenCompose：串行依赖（前一个结果作为后一个的输入）---
        CompletableFuture<Integer> composeFuture = CompletableFuture
                .supplyAsync(() -> {
                    System.out.println("步骤1: 计算 10 + 5");
                    return 10 + 5;
                }, asyncPool)
                .thenCompose(result -> CompletableFuture.supplyAsync(() -> {
                    System.out.println("步骤2: 将上一步结果 " + result + " 乘以 2");
                    return result * 2;
                }, asyncPool));
        // 注意：thenCompose 返回的是「展开后的」CompletableFuture，避免嵌套 CompletableFuture<CompletableFuture>
        System.out.println("thenCompose 结果: " + composeFuture.join());  // 30

        // --- 3.2 thenCombine：并行合并（两个独立任务同时执行，最后合并结果）---
        CompletableFuture<List<Integer>> numbers = CompletableFuture
                .supplyAsync(() -> {
                    System.out.println("并行任务A: 生成数字列表");
                    return Arrays.asList(3, 7, 2);
                }, asyncPool)
                .thenCombine(
                        CompletableFuture.supplyAsync(() -> {
                            System.out.println("并行任务B: 计算额外值");
                            return 100;
                        }, asyncPool),
                        (list, extra) -> {
                            System.out.println("合并: 把 " + extra + " 加进列表");
                            List<Integer> merged = new ArrayList<>(list);
                            merged.add(extra);
                            return merged;
                        });
        System.out.println("thenCombine 结果: " + numbers.join());  // [3, 7, 2, 100]

        // --- 3.3 allOf：等待多个任务全部完成 ---
        CompletableFuture<Integer> f1 = CompletableFuture.supplyAsync(() -> {
            sleep(300); return 1;
        }, asyncPool);
        CompletableFuture<Integer> f2 = CompletableFuture.supplyAsync(() -> {
            sleep(200); return 2;
        }, asyncPool);
        CompletableFuture<Integer> f3 = CompletableFuture.supplyAsync(() -> {
            sleep(100); return 3;
        }, asyncPool);

        // allOf 本身不返回结果，需要配合 join() 汇总每个 future 的结果
        CompletableFuture<Void> all = CompletableFuture.allOf(f1, f2, f3);
        all.join();  // 阻塞等待全部完成
        int total = f1.join() + f2.join() + f3.join();
        System.out.println("allOf 三个任务结果之和: " + total);  // 6

        // anyOf：任一任务完成即返回（如：多个冗余接口调用，取最快返回）
        CompletableFuture<Object> any = CompletableFuture.anyOf(f1, f2, f3);
        System.out.println("anyOf 最快返回: " + any.join());  // 3（f3 最快）

        // --- 3.4 异常处理 ---
        CompletableFuture<String> withException = CompletableFuture
                .supplyAsync(() -> {
                    if (Math.random() > 0.5) throw new IllegalStateException("模拟异常");
                    return "成功";
                }, asyncPool)
                .exceptionally(ex -> {
                    System.out.println("捕获异常: " + ex.getMessage());
                    return "恢复默认值";
                });
        System.out.println("exceptionally 结果: " + withException.join());

        // ========== 4. 线程池正确关闭 ==========

        System.out.println("\n=== 4. 线程池正确关闭 ===");

        // shutdown(): 不再接收新任务，等待已提交任务执行完（优雅关闭）
        pool.shutdown();
        try {
            // awaitTermination: 等待所有任务执行完，超时返回 false
            if (!pool.awaitTermination(10, TimeUnit.SECONDS)) {
                // 超时后仍有关闭不掉的线程，强制中断
                pool.shutdownNow();
                System.out.println("线程池超时，已强制 shutdownNow");
            } else {
                System.out.println("线程池已优雅关闭，池大小: " + pool.getPoolSize());
            }
        } catch (InterruptedException e) {
            pool.shutdownNow();
            Thread.currentThread().interrupt();
        }

        // 使用 try-with-resources 风格（Java 19+ 支持 AutoCloseable，此处演示手动模式）
        asyncPool.shutdown();
        boolean done = asyncPool.awaitTermination(5, TimeUnit.SECONDS);
        System.out.println("asyncPool 关闭完成: " + done);

        // Executors 工具类常用方法（了解，生产环境谨慎使用）：
        // newFixedThreadPool(n): 固定大小线程池（无界队列，可能堆积任务）
        // newCachedThreadPool(): 可缓存线程池（最大 Integer.MAX_VALUE，风险大）
        // newSingleThreadExecutor(): 单线程串行执行
        // newScheduledThreadPool(n): 可调度线程池（延迟/周期任务）

        System.out.println("\n主线程结束");
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}