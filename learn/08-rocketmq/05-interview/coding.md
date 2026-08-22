# 代码题：事务消息实现、幂等消费、批量发送

> 面试手写代码题，覆盖事务消息、幂等消费、批量发送三大核心场景。
> 代码风格：Spring Boot + RocketMQ（rocketmq-spring-boot-starter）。

---

## 题 1：事务消息实现

### 题目

实现一个"下单 + 扣库存"的事务消息：订单创建后，发送事务消息通知库存服务扣减库存。要求写出完整的事务监听器和发送端代码。

### 参考答案

**事务监听器**

```java
@Component
@Slf4j
public class OrderTransactionListener implements TransactionListener {

    @Resource
    private OrderService orderService;

    /**
     * 执行本地事务
     * 参数 arg 是 sendMessageInTransaction 传入的第三个参数
     */
    @Override
    public LocalTransactionState executeLocalTransaction(Message msg, Object arg) {
        OrderCreateRequest request = (OrderCreateRequest) arg;
        try {
            // 本地事务：创建订单
            orderService.createOrder(request);
            log.info("本地事务执行成功，提交消息, orderId={}", request.getOrderId());
            return LocalTransactionState.COMMIT_MESSAGE;
        } catch (Exception e) {
            log.error("本地事务执行失败，回滚消息, orderId={}", request.getOrderId(), e);
            return LocalTransactionState.ROLLBACK_MESSAGE;
        }
    }

    /**
     * 事务回查：Broker 询问本地事务状态
     * 通过 msg.getKeys() 获取业务 ID，查询订单是否存在
     */
    @Override
    public LocalTransactionState checkLocalTransaction(MessageExt msg) {
        String orderId = msg.getKeys();
        boolean orderExists = orderService.isOrderExists(orderId);
        if (orderExists) {
            log.info("回查：订单存在，提交消息, orderId={}", orderId);
            return LocalTransactionState.COMMIT_MESSAGE;
        } else {
            log.warn("回查：订单不存在，回滚消息, orderId={}", orderId);
            return LocalTransactionState.ROLLBACK_MESSAGE;
        }
    }
}
```

**发送事务消息**

```java
@Service
public class OrderTransactionProducer {

    @Resource
    private RocketMQTemplate rocketMQTemplate;

    @Resource
    private OrderTransactionListener transactionListener;

    public boolean sendOrderTransaction(OrderCreateRequest request) {
        Message<OrderCreateRequest> msg = MessageBuilder
            .withPayload(request)
            .setHeader(RocketMQHeaders.KEYS, request.getOrderId())
            .build();

        TransactionSendResult result = rocketMQTemplate.sendMessageInTransaction(
            "mall-order-stock-topic",
            msg,
            request,
            transactionListener
        );

        log.info("事务消息发送结果: msgId={}, status={}",
            result.getMsgId(), result.getLocalTransactionState());

        return result.getLocalTransactionState() == LocalTransactionState.COMMIT_MESSAGE;
    }
}
```

**消费端**

```java
@Service
@RocketMQMessageListener(
    topic = "mall-order-stock-topic",
    consumerGroup = "mall-stock-consumer-group"
)
@Slf4j
public class StockConsumer implements RocketMQListener<OrderCreateRequest> {

    @Resource
    private StockService stockService;

    @Override
    public void onMessage(OrderCreateRequest request) {
        // 幂等校验
        if (stockService.isStockDeducted(request.getOrderId())) {
            log.info("库存已扣减，跳过: {}", request.getOrderId());
            return;
        }
        // 扣减库存
        boolean success = stockService.deductStock(
            request.getSkuId(), request.getQuantity());
        if (success) {
            stockService.markDeducted(request.getOrderId());
            log.info("库存扣减成功: orderId={}, skuId={}",
                request.getOrderId(), request.getSkuId());
        } else {
            log.warn("库存不足: skuId={}", request.getSkuId());
        }
    }
}
```

---

## 题 2：幂等消费

### 题目

实现一个幂等消费的通用方案，利用唯一键表防止重复消费。要求：1）支持传入业务键自动去重；2）处理逻辑清晰；3）考虑异常处理。

### 参考答案

**幂等工具类**

```java
@Component
public class IdempotentConsumer {

    @Resource
    private JdbcTemplate jdbcTemplate;

    /**
     * 幂等消费：返回 true 表示可以执行业务，false 表示已处理过
     *
     * @param bizKey 业务键（如 orderId + 场景前缀）
     * @param expireHours 过期时间（自动清理历史数据）
     * @return true = 首次处理，false = 已处理过
     */
    public boolean tryProcess(String bizKey, int expireHours) {
        try {
            jdbcTemplate.update(
                "INSERT INTO idempotent_record (biz_key, status, create_time) VALUES (?, 0, NOW())",
                bizKey
            );
            return true;  // 插入成功，首次处理
        } catch (DuplicateKeyException e) {
            return false; // 已存在，重复消息
        }
    }

    /**
     * 标记处理完成
     */
    public void markProcessed(String bizKey) {
        jdbcTemplate.update(
            "UPDATE idempotent_record SET status = 1, process_time = NOW() WHERE biz_key = ?",
            bizKey
        );
    }

    /**
     * 清理过期数据（定时任务调用）
     */
    @Scheduled(cron = "0 0 3 * * ?")  // 每天凌晨 3 点执行
    public void cleanExpiredRecords() {
        jdbcTemplate.update(
            "DELETE FROM idempotent_record WHERE create_time < NOW() - INTERVAL 7 DAY"
        );
    }
}
```

**使用幂等工具类**

```java
@Service
@RocketMQMessageListener(
    topic = "mall-payment-topic",
    consumerGroup = "mall-payment-consumer-group"
)
@Slf4j
public class PaymentConsumer implements RocketMQListener<PaymentEvent> {

    @Resource
    private IdempotentConsumer idempotentConsumer;
    @Resource
    private OrderService orderService;

    @Override
    public void onMessage(PaymentEvent event) {
        String bizKey = "payment:" + event.getOrderId();

        // 1. 幂等校验
        if (!idempotentConsumer.tryProcess(bizKey, 48)) {
            log.info("支付回调已处理, orderId={}", event.getOrderId());
            return;
        }

        try {
            // 2. 执行业务：更新订单状态
            orderService.payOrder(event.getOrderId(), event.getAmount());

            // 3. 标记完成
            idempotentConsumer.markProcessed(bizKey);
            log.info("支付处理成功, orderId={}", event.getOrderId());
        } catch (Exception e) {
            // 注意：异常时删除幂等记录，让下次重试能重新处理
            // 如果这里不删除，幂等表会阻止重试
            log.error("支付处理失败, orderId={}", event.getOrderId(), e);
            throw e;  // 抛出异常触发 RocketMQ 重试
        }
    }
}
```

**幂等表 SQL**

```sql
CREATE TABLE idempotent_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    biz_key VARCHAR(200) NOT NULL UNIQUE,   -- 业务键，唯一约束
    status TINYINT DEFAULT 0,               -- 0=处理中, 1=已完成
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    process_time DATETIME,
    INDEX idx_biz_key (biz_key),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

---

## 题 3：批量发送

### 题目

实现一个批量发送日志消息的工具类，要求：1）支持批量发送（最多 4MB）；2）超过大小自动拆分；3）支持压缩选项。

### 参考答案

```java
@Component
@Slf4j
public class BatchMessageSender {

    @Resource
    private RocketMQTemplate rocketMQTemplate;

    // 单批最大大小：4MB
    private static final int MAX_BATCH_SIZE = 4 * 1024 * 1024;

    /**
     * 批量发送消息
     *
     * @param topic  Topic
     * @param tag    Tag（可选）
     * @param messages 消息列表
     * @param compress 是否压缩
     * @param <T> 消息类型
     */
    public <T> void sendBatch(String topic, String tag,
                              List<T> messages, boolean compress) {
        if (messages == null || messages.isEmpty()) {
            return;
        }

        String destination = tag != null ? topic + ":" + tag : topic;

        if (compress) {
            // 压缩后发送
            sendCompressedBatch(destination, messages);
        } else {
            // 普通批量发送
            sendPlainBatch(destination, messages);
        }
    }

    /**
     * 普通批量发送（按 4MB 自动拆分）
     */
    private <T> void sendPlainBatch(String destination, List<T> messages) {
        List<Message<T>> batch = new ArrayList<>();
        int currentBatchSize = 0;

        for (T msg : messages) {
            byte[] body = JSON.toJSONBytes(msg);
            // 估算大小：消息体 + 20% 的元数据开销
            int estimatedSize = body.length + 512;

            if (currentBatchSize + estimatedSize > MAX_BATCH_SIZE && !batch.isEmpty()) {
                // 发送当前批次
                doSendBatch(destination, batch);
                batch.clear();
                currentBatchSize = 0;
            }

            batch.add(MessageBuilder.withPayload(msg).build());
            currentBatchSize += estimatedSize;
        }

        // 发送最后一批
        if (!batch.isEmpty()) {
            doSendBatch(destination, batch);
        }
    }

    /**
     * 压缩后发送（先压缩再发送，适合大消息）
     */
    private <T> void sendCompressedBatch(String destination, List<T> messages) {
        byte[] jsonBytes = JSON.toJSONBytes(messages);
        byte[] compressed = compress(jsonBytes);

        log.info("压缩: {} bytes → {} bytes (压缩比 {:.2f})",
            jsonBytes.length, compressed.length,
            (double) compressed.length / jsonBytes.length);

        // 单条消息发送（压缩后整体作为一条消息）
        rocketMQTemplate.syncSend(destination,
            MessageBuilder.withPayload(compressed)
                .setHeader("compressType", "gzip")
                .build());
    }

    /**
     * 实际发送
     */
    private <T> void doSendBatch(String destination, List<Message<T>> batch) {
        try {
            SendResult result = rocketMQTemplate.syncSend(destination, batch, 5000);
            if (result.getSendStatus() != SendStatus.SEND_OK) {
                log.warn("批量发送状态异常: {}, size={}",
                    result.getSendStatus(), batch.size());
            }
        } catch (Exception e) {
            log.error("批量发送失败, size={}", batch.size(), e);
            // 失败后逐条发送（降级）
            sendOneByOne(destination, batch);
        }
    }

    /**
     * 降级：逐条发送
     */
    private <T> void sendOneByOne(String destination, List<Message<T>> batch) {
        for (Message<T> msg : batch) {
            try {
                rocketMQTemplate.syncSend(destination, msg, 3000);
            } catch (Exception e) {
                log.error("逐条发送也失败", e);
            }
        }
    }

    /**
     * GZIP 压缩
     */
    private byte[] compress(byte[] data) {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             GZIPOutputStream gzip = new GZIPOutputStream(bos)) {
            gzip.write(data);
            gzip.finish();
            return bos.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("压缩失败", e);
        }
    }
}
```

**使用示例**

```java
// 批量发送日志
List<LogEntry> logs = new ArrayList<>();
for (int i = 0; i < 1000; i++) {
    logs.add(new LogEntry("INFO", "用户操作日志 #" + i));
}
batchMessageSender.sendBatch("mall-log-topic", "access", logs, false);
```

---

## 题 4：消费端重试与死信处理

### 题目

实现一个消费端，对业务异常跳过、系统异常重试、超过重试次数记录并转为死信处理。

### 参考答案

```java
@Service
@RocketMQMessageListener(
    topic = "mall-order-topic",
    consumerGroup = "mall-order-consumer-group",
    maxReconsumeTimes = 5         // 最多重试 5 次
)
@Slf4j
public class RetryConsumer implements RocketMQListener<MessageExt> {

    @Resource
    private OrderService orderService;

    @Override
    public void onMessage(MessageExt msg) {
        OrderEvent event = parseMessage(msg);
        String orderId = event.getOrderId();

        try {
            // 1. 幂等
            if (orderService.isProcessed(orderId)) {
                return;
            }
            // 2. 业务处理
            orderService.process(event);
            // 3. 标记完成
            orderService.markProcessed(orderId);

        } catch (BizException e) {
            // 业务异常：不重试，记录日志后跳过
            log.warn("业务异常，跳过处理: orderId={}, reason={}", orderId, e.getMessage());

        } catch (DataAccessException e) {
            // 数据库异常：需要重试
            log.error("数据库异常，触发重试: orderId={}, retry={}/{}",
                orderId, msg.getReconsumeTimes(), 5, e);
            throw e;

        } catch (Exception e) {
            // 其他异常：判断重试次数
            int retryCount = msg.getReconsumeTimes();
            if (retryCount >= 5) {
                // 超过最大重试次数，记录死信
                log.error("超过最大重试次数，进入死信: orderId={}, retry={}",
                    orderId, retryCount);
                saveToDeadLetter(msg);
                // 不抛异常，消息消费成功（已记录死信）
            } else {
                log.error("未知异常，触发重试: orderId={}, retry={}/{}",
                    orderId, retryCount + 1, 5, e);
                throw e;
            }
        }
    }

    private void saveToDeadLetter(MessageExt msg) {
        // 将死信消息写入死信表，后续人工介入处理
        deadLetterMapper.insert(new DeadLetterRecord(
            msg.getKeys(),           // 业务键
            new String(msg.getBody()),
            msg.getReconsumeTimes()
        ));
    }
}
```

---

## 总结

| 题号 | 考点 | 关键代码 |
|------|------|----------|
| 1 | 事务消息 | `sendMessageInTransaction` + `TransactionListener` |
| 2 | 幂等消费 | `INSERT ... ON DUPLICATE KEY` 或 `DuplicateKeyException` |
| 3 | 批量发送 | 4MB 拆分 + GZIP 压缩 + 失败降级逐条发送 |
| 4 | 重试与死信 | `maxReconsumeTimes` + `msg.getReconsumeTimes()` + 死信表 |