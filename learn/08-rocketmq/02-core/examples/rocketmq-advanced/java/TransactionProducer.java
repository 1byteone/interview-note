package com.example.rocketmq.transaction;

import org.apache.rocketmq.client.producer.LocalTransactionState;
import org.apache.rocketmq.client.producer.TransactionListener;
import org.apache.rocketmq.client.producer.TransactionMQProducer;
import org.apache.rocketmq.common.message.Message;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 事务消息生产者
 *
 * 流程：
 *  1. 发送半消息（Half Message，消费者暂时不可见）
 *  2. RocketMQ 返回后，框架回调 executeLocalTransaction 执行本地事务
 *  3. 本地事务成功 → COMMIT_MESSAGE，失败 → ROLLBACK_MESSAGE
 *  4. 若本地事务状态未知，RocketMQ 定时回查 checkLocalTransaction
 *
 * 运行：先启动 docker-compose（RocketMQ），然后运行 TransactionProducerDemo
 */
public class TransactionProducer {

    public static void main(String[] args) throws Exception {

        // 1. 创建事务消息生产者
        TransactionMQProducer producer = new TransactionMQProducer("transaction_producer_group");

        // 2. 设置 NameServer
        producer.setNamesrvAddr("localhost:9876");

        // 3. 为回查回调提供独立线程池（生产环境建议自定义）
        ExecutorService executorService = new ThreadPoolExecutor(
                2, 5, 100, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(2000),
                new ThreadFactory() {
                    @Override
                    public Thread newThread(Runnable r) {
                        Thread t = new Thread(r);
                        t.setName("client-transaction-msg-check-thread");
                        return t;
                    }
                }
        );
        producer.setExecutorService(executorService);

        // 4. 注册事务监听器（核心：本地事务 + 回查逻辑）
        producer.setTransactionListener(new TransactionListenerImpl());

        // 5. 启动生产者
        producer.start();
        System.out.println("事务消息生产者已启动");

        try {
            // ======== 模拟创建订单（本地事务 + 消息） ========
            String orderId = "ORDER-" + System.currentTimeMillis();
            String body = String.format("{\"orderId\":\"%s\",\"amount\":6999.00}", orderId);

            Message msg = new Message(
                    "TopicTransaction",          // Topic
                    "Order",                     // Tag
                    orderId,                     // Keys（业务主键，可用于消息检索）
                    body.getBytes(StandardCharsets.UTF_8)
            );

            // 发送事务消息（回调时执行本地事务）
            producer.sendMessageInTransaction(msg,
                // arg：透传给 executeLocalTransaction 的参数
                "{\"productId\":1,\"quantity\":1}"
            );
            System.out.println("事务消息已发送，等待本地事务执行...");

            // 保持运行观察回查
            Thread.sleep(10000);

        } finally {
            producer.shutdown();
            System.out.println("生产者已关闭");
        }
    }
}