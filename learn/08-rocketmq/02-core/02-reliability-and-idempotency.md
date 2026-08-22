# 可靠性与幂等

> 消息丢失和重复消费是消息队列中间件最核心的两个问题。本章覆盖从生产端到消费端全链路可靠性保障，以及六种幂等方案。
> 实战：AI 商城支付回调幂等处理。

---

## 1. 消息丢失场景分析

RocketMQ 确保"至少一次投递"（At-Least-Once），但各环节仍有丢失风险：

```
   Producer ──► Broker ──► Consumer
   ① 生产      ② 存储      ③ 消费
   发送丢失     刷盘丢失     处理丢失
   网络异常     磁盘故障     宕机
```

### 1.1 三个环节的丢失窗口

| 阶段 | 丢失原因 | 风险级别 |
|------|----------|----------|
| **① 生产丢失** | 网络超时、Broker 返回失败 | 需要确认重试 |
| **② Broker 存储丢失** | 异步刷盘丢数据、主从复制延迟 | 需同步刷盘 |
| **③ 消费丢失** | 消费后未提交 offset 就宕机、消费逻辑异常 | 需重试机制 |

---

## 2. 生产端可靠性

### 2.1 同步发送 + 重试（标配）

```java
// 1. 同步发送（确保 Broker 确认）
SendResult result = rocketMQTemplate.syncSend(topic, msg, 3000);

// 2. 检查发送状态
if (result.getSendStatus() != SendStatus.SEND_OK) {
    // 3. 失败重试（最多 3 次）
    for (int i = 0; i < 3; i++) {
        result = rocketMQTemplate.syncSend(topic, msg, 3000);
        if (result.getSendStatus() == SendStatus.SEND_OK) break;
    }
}

// 4. 重试仍失败：落库做补偿
if (result.getSendStatus() != SendStatus.SEND_OK) {
    saveToCompensationTable(topic, msg);   // 落地到本地消息表
}
```

### 2.2 本地消息表（兜底方案）

```sql
CREATE TABLE local_message (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    topic VARCHAR(100) NOT NULL,
    message_body TEXT NOT NULL,
    status TINYINT DEFAULT 0,        -- 0=待发送, 1=已发送, 2=已确认
    retry_count INT DEFAULT 0,
    max_retry INT DEFAULT 3,
    create_time DATETIME,
    next_retry_time DATETIME
);
```

```java
// 定时任务：扫描本地消息表，补偿发送
@Scheduled(fixedDelay = 10000)
public void compensateMessages() {
    List<LocalMessage> pending = localMessageMapper.findPending();
    for (LocalMessage msg : pending) {
        if (msg.getRetryCount() >= msg.getMaxRetry()) {
            // 告警人工介入
            alert("消息发送超过最大重试次数", msg);
            continue;
        }
        try {
            rocketMQTemplate.syncSend(msg.getTopic(), msg.getMessageBody(), 3000);
            localMessageMapper.updateStatus(msg.getId(), 1);  // 标记已发送
        } catch (Exception e) {
            localMessageMapper.incrementRetry(msg.getId());   // 更新重试次数
        }
    }
}
```

### 2.3 生产端最佳实践

| 实践 | 说明 |
|------|------|
| 同步发送 | 重要业务必须用同步，不要用 OneWay |
| 设置超时 | 根据业务合理设置 `send-message-timeout`（默认 3s） |
| 重试策略 | 建议 3 次重试，间隔指数退避（1s, 2s, 4s） |
| 补偿表 | 兜底方案：重试仍失败则落库，定时任务扫描补偿 |
| 监控告警 | 发送失败率 > 0.1% 时触发告警 |

---

## 3. Broker 端可靠性

### 3.1 刷盘方式

| 方式 | 配置 | 可靠性 | 性能 |
|------|------|--------|------|
| 同步刷盘 | `flushDiskType=SYNC_FLUSH` | 消息写入物理磁盘后才返回 ACK | 低 |
| 异步刷盘 | `flushDiskType=ASYNC_FLUSH`（默认） | 操作系统页缓存，宕机可能丢 | 高 |

**生产建议**：关键业务消息（支付、订单）用同步刷盘；日志类用异步刷盘。

### 3.2 主从复制

| 方式 | 配置 | 说明 |
|------|------|------|
| 同步复制 | `brokerRole=SYNC_MASTER` | Master 写入后等待 Slave 写入成功才返回 |
| 异步复制 | `brokerRole=ASYNC_MASTER`（默认） | Master 写完即返回，Slave 异步同步 |

**最佳实践**：同步刷盘 + 同步复制，最大程度保证不丢消息（代价是性能下降约 50%）。

---

## 4. 消费端可靠性

### 4.1 消费重试机制

RocketMQ 默认消费失败后重试 16 次，间隔递增：

| 重试次数 | 延迟 | 重试次数 | 延迟 |
|----------|------|----------|------|
| 1 | 10s | 9 | 15min |
| 2 | 30s | 10 | 30min |
| 3 | 1min | 11 | 1h |
| 4 | 2min | 12 | 2h |
| 5 | 3min | 13 | ... 逐步递增 |

```java
@RocketMQMessageListener(
    topic = "mall-order-topic",
    consumerGroup = "mall-order-consumer-group",
    // 最大重试次数，默认 16
    maxReconsumeTimes = 5
)
public class SafeConsumer implements RocketMQListener<Order> {

    @Override
    public void onMessage(Order order) {
        try {
            orderService.process(order);
        } catch (Exception e) {
            log.error("消费失败，等待重试: {}", order.getOrderId(), e);
            throw e;  // 抛出异常 → 触发重试
        }
    }
}
```

### 4.2 死信队列

消息超过最大重试次数后，进入死信队列（Dead Letter Queue, DLQ）：

```
特性：DLQ 的 Topic 格式为 %DLQ%consumerGroupName
示例：%DLQ%mall-order-consumer-group

死信消息在控制台可以看到，需要人工介入排查原因：
  1. 分析死信消息内容
  2. 修复消费端 bug
  3. 重新投递消息（控制台操作）
  4. 确认消费成功
```

### 4.3 消费端最佳实践

```java
@Override
public void onMessage(Order order) {
    try {
        // 1. 幂等校验
        if (isProcessed(order.getOrderId())) return;

        // 2. 执行业务逻辑
        orderService.process(order);

        // 3. 记录处理状态
        markProcessed(order.getOrderId());
    } catch (BizException e) {
        // 业务异常（如订单不存在）：不需要重试，直接记录并跳过
        log.warn("业务异常，跳过消费: {}", e.getMessage());
        // 不抛出异常，RocketMQ 认为消费成功
    } catch (Exception e) {
        // 系统异常（如 DB 连接失败）：需要重试
        log.error("系统异常，触发重试: {}", order.getOrderId(), e);
        throw e;  // 抛出异常 → 触发重试
    }
}
```

---

## 5. 幂等方案详解

### 5.1 为什么需要幂等

RocketMQ 保证**至少一次投递**，意味着：

- 网络抖动导致重复投递
- 消费端处理成功但提交 offset 超时，下次 Rebalance 会重新投递
- 重试期间可能产生多条相同消息

**消费端必须幂等**，同一消息消费多次和一次效果相同。

### 5.2 六种幂等方案

| 方案 | 原理 | 适用场景 | 复杂度 |
|------|------|----------|--------|
| ① 唯一键表 | 用业务 ID 做主键，INSERT 冲突跳过 | 通用 | 低 |
| ② 状态机 | 订单状态流转只能前进，不能回退 | 状态流转 | 中 |
| ③ Redis 锁 | `setIfAbsent` 加锁，处理完释放 | 追求性能 | 中 |
| ④ 去重表 | 专门建一张表记录已处理 ID | 数据敏感 | 低 |
| ⑤ 数据库乐观锁 | `update ... where version = oldVersion` | 更新操作 | 中 |
| ⑥ Token 机制 | 前端生成 Token，后端校验一次 | 防止重复提交 | 高 |

### 5.3 方案一：唯一键表（推荐，最简单）

```sql
-- 幂等表：记录已处理的消息 ID
CREATE TABLE idempotent_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    msg_id VARCHAR(64) NOT NULL UNIQUE,     -- 全局唯一消息 ID
    business_key VARCHAR(128) NOT NULL,     -- 业务键（订单号）
    status TINYINT DEFAULT 0,               -- 处理状态
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_msg_id (msg_id)
);
```

```java
public void consume(Order order) {
    try {
        // INSERT 幂等表：利用唯一键约束，重复插入会抛 DuplicateKeyException
        idempotentRecordMapper.insert(new IdempotentRecord(order.getMsgId()));

        // 执行业务
        orderService.process(order);
    } catch (DuplicateKeyException e) {
        // 已处理过，跳过
        log.info("消息已处理, msgId={}", order.getMsgId());
    }
}
```

### 5.4 方案二：状态机（适合订单状态流转）

订单状态机只允许向前走：

```
CREATED → PAID → SHIPPED → DELIVERED → COMPLETED
          ↓
        CANCELLED
```

```java
public boolean updateOrderStatus(String orderId, OrderStatus from, OrderStatus to) {
    // update ... where order_id=? and status=from
    // 条件匹配才更新，不匹配说明已处理过，跳过
    return orderMapper.updateStatus(orderId, from, to) > 0;
}
```

### 5.5 方案三：Redis 锁

```java
public void consumeWithRedisLock(Order order) {
    String lockKey = "order:deduct:" + order.getOrderId();
    // 加锁 30 秒（正常处理远快于此）
    Boolean locked = redisTemplate.opsForValue()
            .setIfAbsent(lockKey, "1", 30, TimeUnit.SECONDS);
    if (!Boolean.TRUE.equals(locked)) {
        log.info("订单 {} 正在处理，跳过重复消息", order.getOrderId());
        return;
    }
    try {
        orderService.process(order);
    } finally {
        redisTemplate.delete(lockKey);  // 处理完释放锁
    }
}
```

---

## 6. 实战：AI 商城支付回调幂等

### 6.1 场景

```
支付服务 → 发送支付成功消息 → 订单服务消费 → 更新订单状态
                                             → 调用积分服务发放积分
                                             → 调用库存服务锁定库存
```

**关键问题**：支付回调消息可能重复投递，同一个订单多次更新状态可能出错。

### 6.2 幂等实现（唯一键 + 状态机混合）

```java
@Service
@RocketMQMessageListener(topic = "mall-payment-topic",
    consumerGroup = "mall-order-payment-group")
@Slf4j
public class PaymentConsumer implements RocketMQListener<PaymentEvent> {

    @Override
    public void onMessage(PaymentEvent event) {
        String orderId = event.getOrderId();

        // 1. 唯一键防重
        try {
            idempotentMapper.insert(new IdempotentRecord("pay:" + orderId));
        } catch (DuplicateKeyException e) {
            log.info("支付回调已处理, orderId={}", orderId);
            return;
        }

        // 2. 状态机：只有待支付 → 已支付（防止重复消费导致状态跳转异常）
        int updated = orderMapper.updateStatus(orderId,
                OrderStatus.CREATED, OrderStatus.PAID);
        if (updated == 0) {
            log.warn("订单状态非待支付，跳过处理, orderId={}", orderId);
            return;
        }

        // 3. 发放积分（也需要幂等）
        try {
            sendPoints(event.getUserId(), event.getAmount());
        } catch (Exception e) {
            log.error("积分发放失败, orderId={}", orderId, e);
            // 这里不抛异常：订单状态已更新，积分发放可异步补偿
        }
    }
}
```

---

## 总结

本章你学会了：

- 消息丢失的三个环节（生产/存储/消费）及对应保障方案
- 同步刷盘 + 同步复制的最佳实践
- 消费重试机制和死信队列处理
- 六种幂等方案的原理与选型
- AI 商城支付回调的幂等处理实战

**核心原则**：生产端可靠发送 + Broker 端同步刷盘 + 消费端幂等处理 = 消息系统终极可靠性方案。

下一步：进入 [高可用](03-advanced/01-high-availability.md)，了解 Dledger 多副本机制。