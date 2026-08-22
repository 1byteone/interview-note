package com.example.rocketmq.transaction;

import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyContext;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.common.message.MessageExt;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 事务消息消费者
 *
 * 注意：消费者无法感知消息是否经过事务。
 * 这是 RocketMQ 事务消息的设计目的——只有本地事务提交的消息
 * （COMMIT_MESSAGE）才会被投递给消费者，保证最终一致。
 */
public class TransactionConsumer {

    public static void main(String[] args) throws Exception {
        // 1. 创建消费者
        DefaultMQPushConsumer consumer = new DefaultMQPushConsumer("transaction_consumer_group");

        // 2. 设置 NameServer
        consumer.setNamesrvAddr("localhost:9876");

        // 3. 订阅 Topic
        consumer.subscribe("TopicTransaction", "Order");

        // 4. 注册监听器
        consumer.registerMessageListener(new MessageListenerConcurrently() {
            @Override
            public ConsumeConcurrentlyStatus consumeMessage(
                    List<MessageExt> msgs, ConsumeConcurrentlyContext context) {

                for (MessageExt msg : msgs) {
                    String body = new String(msg.getBody(), StandardCharsets.UTF_8);
                    System.out.printf("[交易系统] 收到订单消息: keys=%s, body=%s%n",
                            new String(msg.getKeys(), StandardCharsets.UTF_8), body);

                    // 此处可以执行下游业务：如发送短信、通知仓库发货等
                }
                return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
            }
        });

        // 5. 启动
        consumer.start();
        System.out.println("事务消息消费者已启动，等待消息...");

        Runtime.getRuntime().addShutdownHook(new Thread(consumer::shutdown));
    }
}