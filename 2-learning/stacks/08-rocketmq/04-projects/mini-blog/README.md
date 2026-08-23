# 迷你异步通知系统

> 独立小项目：用 RocketMQ 实现一个异步通知系统，覆盖生产者、消费者、重试、死信队列。
> 建议用时：2-3 小时。

---

## 1. 项目概述

### 1.1 业务场景

在 AI 商城中，用户完成支付后，系统需要发送多种通知：

- **短信通知**：支付成功通知 + 订单号
- **邮件通知**：订单详情 + 物流信息
- **站内信通知**：App 内的消息推送
- **商家通知**：商家收到新订单提醒

这些通知有一个共同特点：**高吞吐、可容忍延迟秒级、可靠性要求高**（不能丢通知）。

### 1.2 技术方案

```
支付服务 ──► RocketMQ ──► 通知服务
                          ├── 短信通道（阿里云短信）
                          ├── 邮件通道（JavaMail）
                          ├── 站内信通道（WebSocket）
                          └── 商家通知通道（Webhook）
```

---

## 2. 项目结构

```
mini-blog-notification/
├── pom.xml
├── src/main/java/com/mall/notification/
│   ├── NotificationApplication.java
│   ├── config/
│   │   └── RocketMQConfig.java
│   ├── producer/
│   │   └── NotificationProducer.java
│   ├── consumer/
│   │   ├── SmsConsumer.java
│   │   ├── EmailConsumer.java
│   │   ├── InAppConsumer.java
│   │   └── DlqConsumer.java          # 死信队列处理
│   ├── service/
│   │   ├── SmsService.java
│   │   ├── EmailService.java
│   │   └── InAppService.java
│   ├── entity/
│   │   ├── NotificationMessage.java
│   │   └── NotificationResult.java
│   └── repository/
│       └── NotificationRecordRepository.java
└── src/main/resources/
    └── application.yml
```

---

## 3. 依赖配置

### pom.xml

```xml
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.2.0</version>
</parent>

<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
        <groupId>org.apache.rocketmq</groupId>
        <artifactId>rocketmq-spring-boot-starter</artifactId>
        <version>2.3.0</version>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-jpa</artifactId>
    </dependency>
    <dependency>
        <groupId>com.mysql</groupId>
        <artifactId>mysql-connector-j</artifactId>
    </dependency>
    <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
    </dependency>
</dependencies>
```

### application.yml

```yaml
server:
  port: 8088

spring:
  datasource:
    url: jdbc:mysql://localhost:3306/mini_blog?useSSL=false&serverTimezone=Asia/Shanghai
    username: root
    password: root

rocketmq:
  name-server: 127.0.0.1:9876
  producer:
    group: mini-notification-producer-group
    send-message-timeout: 3000
  consumer:
    group: mini-notification-consumer-group
```

---

## 4. 核心代码

### 4.1 消息实体

```java
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NotificationMessage implements Serializable {
    private String messageId;          // 全局唯一消息 ID
    private String bizId;              // 业务 ID（订单号）
    private String type;               // 通知类型：SMS / EMAIL / IN_APP
    private String userId;             // 目标用户
    private String title;              // 通知标题
    private String content;            // 通知内容
    private String destination;        // 目标地址（手机号/邮箱）
    private LocalDateTime createTime;  // 创建时间
}
```

### 4.2 生产者

```java
@Component
@Slf4j
public class NotificationProducer {

    @Resource
    private RocketMQTemplate rocketMQTemplate;

    /**
     * 发送通知消息
     */
    public SendResult sendNotification(NotificationMessage msg) {
        // 按类型选择 Tag
        String tag = switch (msg.getType()) {
            case "SMS" -> "sms";
            case "EMAIL" -> "email";
            case "IN_APP" -> "inapp";
            default -> "general";
        };
        String destination = "mall-notification-topic:" + tag;

        SendResult result = rocketMQTemplate.syncSend(
            destination,
            MessageBuilder.withPayload(msg)
                .setHeader(RocketMQHeaders.KEYS, msg.getMessageId())
                .build(),
            3000
        );

        if (result.getSendStatus() == SendStatus.SEND_OK) {
            log.info("通知消息发送成功: msgId={}, type={}", msg.getMessageId(), msg.getType());
        } else {
            log.error("通知消息发送失败: msgId={}, status={}",
                msg.getMessageId(), result.getSendStatus());
        }
        return result;
    }

    /**
     * 批量发送通知（适合批量通知场景）
     */
    public void batchSendNotifications(List<NotificationMessage> messages) {
        for (NotificationMessage msg : messages) {
            sendNotification(msg);
        }
    }
}
```

### 4.3 消费者：短信通知

```java
@Component
@RocketMQMessageListener(
    topic = "mall-notification-topic",
    consumerGroup = "mini-notification-consumer-group",
    selectorExpression = "sms",
    maxReconsumeTimes = 5
)
@Slf4j
public class SmsConsumer implements RocketMQListener<NotificationMessage> {

    @Resource
    private SmsService smsService;
    @Resource
    private NotificationRecordRepository recordRepository;

    @Override
    public void onMessage(NotificationMessage msg) {
        // 1. 幂等校验
        if (recordRepository.existsByMessageId(msg.getMessageId())) {
            log.info("短信已发送, 跳过: {}", msg.getMessageId());
            return;
        }
        // 2. 发送短信
        try {
            smsService.sendSms(msg.getDestination(), msg.getContent());
            // 3. 记录发送记录
            recordRepository.save(new NotificationRecord(msg.getMessageId(), "SMS", "SUCCESS"));
            log.info("短信发送成功: userId={}, phone={}", msg.getUserId(), msg.getDestination());
        } catch (Exception e) {
            log.error("短信发送失败: userId={}, phone={}", msg.getUserId(), msg.getDestination(), e);
            // 抛出异常 → 触发重试
            throw new RuntimeException("短信发送失败", e);
        }
    }
}
```

### 4.4 消费者：邮件通知

```java
@Component
@RocketMQMessageListener(
    topic = "mall-notification-topic",
    consumerGroup = "mini-notification-consumer-group",
    selectorExpression = "email",
    maxReconsumeTimes = 3
)
@Slf4j
public class EmailConsumer implements RocketMQListener<NotificationMessage> {

    @Resource
    private EmailService emailService;

    @Override
    public void onMessage(NotificationMessage msg) {
        try {
            emailService.sendEmail(msg.getDestination(), msg.getTitle(), msg.getContent());
            log.info("邮件发送成功: userId={}, email={}", msg.getUserId(), msg.getDestination());
        } catch (Exception e) {
            log.error("邮件发送失败: {}", msg.getDestination(), e);
            throw e;
        }
    }
}
```

### 4.5 死信队列消费者

```java
@Component
@RocketMQMessageListener(
    topic = "%DLQ%mini-notification-consumer-group",  // 死信队列 Topic
    consumerGroup = "mini-notification-dlq-group"
)
@Slf4j
public class DlqConsumer implements RocketMQListener<MessageExt> {

    @Resource
    private NotificationProducer notificationProducer;

    @Override
    public void onMessage(MessageExt msg) {
        // 1. 解析死信消息
        String body = new String(msg.getBody(), StandardCharsets.UTF_8);
        NotificationMessage notification = JSON.parseObject(body, NotificationMessage.class);

        log.warn("收到死信消息: messageId={}, type={}, retryCount={}",
            notification.getMessageId(), notification.getType(), msg.getReconsumeTimes());

        // 2. 记录死信原因
        recordDeadLetter(notification, msg.getReconsumeTimes());

        // 3. 人工介入：存入异常表，后续补偿
        saveToDeadLetterTable(notification);

        // 4. 可选：尝试其他通道降级（如短信失败则发邮件）
        if ("SMS".equals(notification.getType())) {
            // 降级为站内信
            notification.setType("IN_APP");
            notification.setContent("【短信通道异常，此为站内信通知】" + notification.getContent());
            notificationProducer.sendNotification(notification);
        }
    }
}
```

---

## 5. 幂等表设计

```sql
-- 通知发送记录表
CREATE TABLE notification_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    message_id VARCHAR(64) NOT NULL UNIQUE,   -- 消息 ID（唯一约束实现幂等）
    biz_id VARCHAR(64),                        -- 业务 ID（订单号）
    type VARCHAR(20) NOT NULL,                 -- 通知类型：SMS/EMAIL/IN_APP
    status VARCHAR(20) NOT NULL,               -- SUCCESS / FAILED
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_biz_id (biz_id),
    INDEX idx_create_time (create_time)
);

-- 死信记录表（人工介入）
CREATE TABLE dead_letter_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    message_id VARCHAR(64) NOT NULL,
    biz_id VARCHAR(64),
    type VARCHAR(20),
    content TEXT,
    retry_count INT DEFAULT 0,
    status VARCHAR(20) DEFAULT 'PENDING',       -- PENDING / PROCESSED
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    process_time DATETIME,
    INDEX idx_status (status)
);
```

---

## 6. 测试与验证

### 6.1 启动项目

```bash
# 1. 启动 RocketMQ（Docker）
docker start rmqnamesrv rmqbroker

# 2. 启动项目
mvn spring-boot:run

# 3. 调用测试接口
curl -X POST http://localhost:8088/api/notification/send \
  -H "Content-Type: application/json" \
  -d '{
    "messageId": "MSG001",
    "bizId": "ORD20260822001",
    "type": "SMS",
    "userId": "user001",
    "title": "支付成功通知",
    "content": "您的订单已支付成功，订单号：ORD20260822001",
    "destination": "13800138000"
  }'
```

### 6.2 验证点

| 验证项 | 方法 | 预期 |
|--------|------|------|
| 消息发送 | 查看控制台 Topic 消息数 | 消息数 +1 |
| 消息消费 | 查看应用日志 | 消费端打印成功日志 |
| 幂等性 | 重复发送同一条消息 | 第二次跳过，不重复发送 |
| 重试机制 | 停掉 SMS 服务，发消息 | 重试 5 次后进入死信 |
| 死信处理 | 查看死信队列 | 死信消费者收到消息，降级处理 |

---

## 7. 扩展思路

完成基础功能后，可以尝试以下扩展：

1. **多通道降级**：短信通道失败 → 自动降级为邮件或站内信
2. **通知模板**：引入模板引擎（Thymeleaf），支持动态渲染通知内容
3. **通知频率控制**：同一用户 1 分钟内最多收到 N 条通知
4. **通知聚合**：多条通知合并为一条发送（如"您有 3 条未读消息"）
5. **定时通知**：结合延迟消息，实现定时发送（如明日提醒）

---

## 项目总结

通过本项目，你实践了：

- RocketMQ 生产者发送消息（同步发送 + 批量发送）
- RocketMQ 消费者的 Tag 过滤消费
- 消费重试机制与死信队列处理
- 幂等消费（唯一键表）
- 多通道通知的降级策略

**项目代码已就绪，动手实践吧！**