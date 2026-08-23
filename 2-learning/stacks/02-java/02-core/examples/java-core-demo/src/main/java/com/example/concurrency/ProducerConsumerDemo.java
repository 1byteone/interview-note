package com.example.concurrency;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ProducerConsumerDemo —— 生产者-消费者模式（阻塞队列实现）
 *
 * 演示内容：
 * 1. BlockingQueue 的使用（ArrayBlockingQueue）
 * 2. 多个生产者与多个消费者并发协作
 * 3. 正确的协调与优雅关闭
 *
 * 这种模式是消息队列的简化版，广泛应用于：
 * - 解耦生产者和消费者
 * - 流量削峰填谷
 * - 异步处理
 */
public class ProducerConsumerDemo {

    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== 生产者-消费者模式演示 ===");
        System.out.println("（生产者 3 个，消费者 2 个，队列容量 5）\n");

        // 阻塞队列：容量为 5，超过则生产者阻塞等待
        // 为什么用阻塞队列？当队列为空时消费者等待，队列满时生产者等待
        // 避免忙等待（busy-waiting），节省 CPU
        BlockingQueue<Integer> queue = new ArrayBlockingQueue<>(5);

        // 生产者与消费者线程池
        ExecutorService producers = Executors.newFixedThreadPool(3);
        ExecutorService consumers = Executors.newFixedThreadPool(2);

        // 停止信号：生产者生产完所有任务后通知消费者
        final int TOTAL_TASKS = 20;
        // AtomicInteger 保证多线程下计数安全
        AtomicInteger produced = new AtomicInteger(0);
        AtomicInteger consumed = new AtomicInteger(0);

        // ========== 启动生产者 ==========
        for (int i = 0; i < 3; i++) {
            int producerId = i + 1;
            producers.submit(() -> {
                try {
                    while (true) {
                        int taskNum = produced.incrementAndGet();  // 原子递增，获取下一个任务编号
                        if (taskNum > TOTAL_TASKS) break;  // 生产完毕

                        // 随机模拟生产耗时（50~200ms）
                        Thread.sleep(ThreadLocalRandom.current().nextLong(50, 200));

                        // put: 如果队列满则阻塞等待，直到有空间
                        // 相比 offer() 需要手动处理队列满的情况，put 更简洁
                        queue.put(taskNum);
                        System.out.println("[生产者-" + producerId + "] 生产了任务 #" + taskNum
                                + " (队列剩余: " + queue.size() + ")");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    System.out.println("[生产者-" + producerId + "] 已停止生产");
                }
            });
        }

        // ========== 启动消费者 ==========
        for (int i = 0; i < 2; i++) {
            int consumerId = i + 1;
            consumers.submit(() -> {
                try {
                    while (true) {
                        // take: 如果队列为空则阻塞等待，直到有元素
                        // 支持中断响应，适合优雅关闭
                        Integer task = queue.poll(2, TimeUnit.SECONDS);
                        if (task == null) {
                            // 如果超过 2 秒没有新任务，且生产者已停止，则退出
                            if (produced.get() >= TOTAL_TASKS) {
                                break;
                            }
                            continue;
                        }

                        // 模拟消费耗时（100~300ms）
                        Thread.sleep(ThreadLocalRandom.current().nextLong(100, 300));
                        int total = consumed.incrementAndGet();
                        System.out.println("  [消费者-" + consumerId + "] 消费了任务 #" + task
                                + " (已消费: " + total + "/" + TOTAL_TASKS + ")");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    System.out.println("  [消费者-" + consumerId + "] 已停止消费");
                }
            });
        }

        // ========== 等待所有任务完成 ==========
        // 先等待生产者完成
        producers.shutdown();
        producers.awaitTermination(10, TimeUnit.SECONDS);
        System.out.println("\n所有生产者已停止");

        // 给消费者一点时间处理队列中剩余的任务
        consumers.shutdown();
        if (!consumers.awaitTermination(5, TimeUnit.SECONDS)) {
            consumers.shutdownNow();
        }

        System.out.println("\n=== 最终统计 ===");
        System.out.println("生产总数: " + produced.get());
        System.out.println("消费总数: " + consumed.get());
        // 因为 AtomicInteger 可能多递增几次（break 前可能已递增），所以消费数可能略小于生产总数
        System.out.println("队列剩余: " + queue.size());
        System.out.println("程序结束");
    }

    // 使用 volatile 标记变量，确保多线程可见性
    // volatile 保证：一个线程写 volatile 变量，其他线程立即可见
    private static volatile boolean volatileFinish = false;
}