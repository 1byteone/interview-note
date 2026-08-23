package com.example.rocketmq;

import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyContext;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.common.message.MessageExt;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * RocketMQ 消息消费者演示
 * 功能：Push 模式消费消息（服务端主动推送）
 *
 * 运行前先启动 ProducerDemo 发送消息
 */
public class ConsumerDemo {

    public static void main(String[] args) throws Exception {
        // 1. 创建消费者，指定消费者组名
        DefaultMQPushConsumer consumer = new DefaultMQPushConsumer("consumer_group_quickstart");

        // 2. 设置 NameServer 地址
        consumer.setNamesrvAddr("localhost:9876");

        // 3. 订阅 Topic（支持 Tag 过滤：TagA || TagB，"*" 表示全部）
        consumer.subscribe("TopicQuickstart", "*");

        // 4. 注册消息监听器（并发消费模式）
        consumer.registerMessageListener(new MessageListenerConcurrently() {
            @Override
            public ConsumeConcurrentlyStatus consumeMessage(
                    List<MessageExt> msgs, ConsumeConcurrentlyContext context) {

                for (MessageExt msg : msgs) {
                    String body = new String(msg.getBody(), StandardCharsets.UTF_8);
                    System.out.printf("收到消息: msgId=%s, topic=%s, tag=%s, body=%s%n",
                            msg.getMsgId(), msg.getTopic(), msg.getTags(), body);
                }

                // 消费成功，ACK 确认（RocketMQ 会删除消息）
                return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;

                // 失败重试（稍后重新投递）
                // return ConsumeConcurrentlyStatus.RECONSUME_LATER;
            }
        });

        // 5. 启动消费者
        consumer.start();
        System.out.println("消费者已启动，等待消息...");

        // 保持运行
        Runtime.getRuntime().addShutdownHook(new Thread(consumer::shutdown));
    }
}