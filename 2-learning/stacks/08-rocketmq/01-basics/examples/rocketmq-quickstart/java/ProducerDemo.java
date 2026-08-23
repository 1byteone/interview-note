package com.example.rocketmq;

import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.message.Message;

import java.nio.charset.StandardCharsets;

/**
 * RocketMQ 消息生产者演示
 * 功能：同步发送 / 异步发送 / 单向发送
 *
 * 运行前确保 RocketMQ 已启动（docker-compose up -d）
 * NameServer 地址: localhost:9876
 */
public class ProducerDemo {

    public static void main(String[] args) throws Exception {
        // 1. 创建生产者，指定生产者组名
        DefaultMQProducer producer = new DefaultMQProducer("producer_group_quickstart");

        // 2. 设置 NameServer 地址
        producer.setNamesrvAddr("localhost:9876");

        // 3. 启动生产者
        producer.start();
        System.out.println("生产者已启动");

        try {
            // ========== 同步发送（可靠性最高） ==========
            System.out.println("\n=== 同步发送 ===");
            Message msg = new Message(
                    "TopicQuickstart",          // Topic
                    "TagA",                     // 标签（用于消息过滤）
                    "Hello RocketMQ 同步消息".getBytes(StandardCharsets.UTF_8)
            );
            SendResult result = producer.send(msg);
            System.out.printf("发送成功: msgId=%s, queueId=%d, offset=%d%n",
                    result.getMsgId(), result.getMessageQueue().getQueueId(), result.getQueueOffset());

            // ========== 异步发送（高吞吐，回调通知） ==========
            System.out.println("\n=== 异步发送 ===");
            Message asyncMsg = new Message("TopicQuickstart", "TagB", "Hello RocketMQ 异步消息".getBytes());
            producer.send(asyncMsg, new SendCallback() {
                @Override
                public void onSuccess(SendResult sendResult) {
                    System.out.printf("异步发送成功: msgId=%s%n", sendResult.getMsgId());
                }

                @Override
                public void onException(Throwable e) {
                    System.err.printf("异步发送失败: %s%n", e.getMessage());
                }
            });
            // 等待异步发送完成（实际生产不建议 Thread.sleep）
            Thread.sleep(1000);

            // ========== 单向发送（不关心结果，最高吞吐） ==========
            System.out.println("\n=== 单向发送 ===");
            Message onewayMsg = new Message("TopicQuickstart", "TagC", "Hello RocketMQ 单向消息".getBytes());
            producer.sendOneway(onewayMsg);
            System.out.println("单向发送完成（不关心结果）");

            // ========== 批量发送 ==========
            System.out.println("\n=== 批量发送（3条消息）===");
            for (int i = 0; i < 3; i++) {
                Message batchMsg = new Message("TopicQuickstart", "TagA",
                        ("批量消息-" + i).getBytes(StandardCharsets.UTF_8));
                producer.send(batchMsg);
            }
            System.out.println("批量发送完成");

        } finally {
            // 4. 关闭生产者（释放资源）
            producer.shutdown();
            System.out.println("\n生产者已关闭");
        }
    }
}