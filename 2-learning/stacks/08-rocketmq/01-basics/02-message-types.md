# 消息类型详解

> 掌握 RocketMQ 的五种核心消息类型：普通、顺序、延迟、批量、过滤，以及各自的适用场景。

---

## 1. 消息类型总览

| 类型 | 有序性 | 延迟 | 典型场景 | 难度 |
|------|--------|------|----------|------|
| 普通消息 | 无保证 | 无 | 事件通知、日志 | 👶 |
| 顺序消息 | 严格（分区内） | 无 | 订单状态流转 | 👶→🎯 |
| 延迟消息 | 无 | 支持 | 订单超时取消 | 👶 |
| 批量消息 | 无 | 无 | 日志批量上报 | 👶 |
| 过滤消息 | 无 | 无 | 按业务分类消费 | 👶 |

---

## 2. 普通消息

普通消息不保证顺序，也不保证重复，是最基础的消息类型。

```java
// 生产端
rocketMQTemplate.syncSend("mall-order-topic", order);

// 消费端
@RocketMQMessageListener(topic = "mall-order-topic", consumerGroup = "group-a")
public class ConsumerA implements RocketMQListener<Order> {
    @Override
    public void onMessage(Order order) {
        // ...
    }
}
```

**适用场景**：通知类（短信、站内信）、日志类、事件驱动类，对顺序和可靠性要求不高的场景。

---

## 3. 顺序消息

### 3.1 为什么需要顺序消息

在订单场景中，一个订单的状态流转是有序的：

```
待支付 → 已支付 → 已发货 → 已完成
```

如果三条消息被并发消费，可能出现"已支付"先于"创建"被执行，导致业务错乱。RocketMQ 通过 **Queue 有序性** 解决：

- 同一个 Queue 内的消息是**严格有序**消费的（一个消费线程串行处理）
- 不同 Queue 之间没有顺序保证

### 3.2 全局顺序 vs 分区顺序

| 维度 | 全局顺序 | 分区顺序 |
|------|----------|----------|
| 保证范围 | 所有消息严格有序 | 同一 Queue 内有序 |
| 实现 | 只建 1 个 Queue | 按业务 key hash 到固定 Queue |
| 吞吐 | 极低（单 Queue 单线程） | 高（多个 Queue 并行） |
| 场景 | 极少用（如全局流水） | **业务主流**（按订单号分区） |

### 3.3 分区顺序实现（按订单号选择 Queue）

```java
// 1. 自定义 MessageQueueSelector：同一订单号的消息进入同一个 Queue
SendResult result = rocketMQTemplate.syncSend(
    "mall-order-status-topic",
    MessageBuilder.withPayload(orderStatusEvent)
        .setHeader(RocketMQHeaders.KEYS, orderId)      // 业务键
        .build(),
    3000,
    // 分区选择器：按 orderId 取模选择固定 Queue
    (msg, queueList) -> {
        int index = Math.abs(orderId.hashCode()) % queueList.size();
        return queueList.get(index);
    }
);
```

消费端只要保证同一个 Queue 由一个线程串行消费即可（默认 `consumeThreadMin=20`，一个 Queue 一个消息一堆，天然串行）。

> ⚠ 注意：Consumer 集群模式下存在 Rebalance（重平衡），消费线程可能重新分配 Queue。要保证顺序，需要消费逻辑本身支持"该顺序消息由同一进程串行处理"。实践中通常记录 `lastProcessedOrder` 或使用单线程消费组。

---

## 4. 延迟消息

### 4.1 延迟等级机制

RocketMQ 的延迟消息不是任意延迟时间，而是 **18 个固定延迟等级**：

| 延迟等级 | 延迟时间 | 延迟等级 | 延迟时间 |
|----------|----------|----------|----------|
| 1 | 1s | 10 | 6min |
| 2 | 5s | 11 | 7min |
| 3 | 10s | 12 | 8min |
| 4 | 30s | 13 | 9min |
| 5 | 1min | 14 | 10min |
| 6 | 2min | 15 | 20min |
| 7 | 3min | 16 | 30min |
| 8 | 4min | 17 | 1h |
| 9 | 5min | 18 | 2h |

**实现原理**：Broker 将延迟消息先写入延迟队列（按延迟等级分 18 个 Topic），由定时任务在到达时间后把消息转投到真正的目标 Topic。

### 4.2 发送延迟消息

```java
// 延迟等级 4 = 30 秒后投递
Message<OrderTimeoutEvent> msg = MessageBuilder.withPayload(event)
    .setHeader(RocketMQHeaders.DELAY_LEVEL, 4)   // 关键：设置延迟等级
    .build();
rocketMQTemplate.syncSend("mall-order-timeout-topic", msg);
```

> 自定义任意延迟时间：5.x 版本支持 `setDeliverTimeMs` 精确到毫秒（基于时间轮），社区版对任意延迟处理有限，生产多采用延迟等级或"消息 + 扫描"兜底。

### 4.3 实战：订单超时自动取消

```
① 用户下单 → 发送 30 分钟延迟消息（延迟等级 16）
      ↓
② 30 分钟后 Broker 投递消息
      ↓
③ TimeoutConsumer 消费
      ↓
④ 查询订单状态：若仍为"待支付" → 关单 + 释放库存 + 通知用户
```

```java
@Service
@RocketMQMessageListener(topic = "mall-order-timeout-topic", consumerGroup = "mall-timeout-group")
@Slf4j
public class OrderTimeoutConsumer implements RocketMQListener<OrderTimeoutEvent> {

    @Resource
    private OrderService orderService;

    @Override
    public void onMessage(OrderTimeoutEvent event) {
        // 定时取消订单
        boolean cancelled = orderService.cancelIfUnpaid(event.getOrderId());
        if (cancelled) {
            log.info("订单 {} 超时已取消, 释放库存", event.getOrderId());
        }
    }
}
```

---

## 5. 批量消息

### 5.1 批量发送

批量消息显著提升吞吐，减少网络往返。要求：**同一批量消息的所有消息必须属于同一个 Topic，且总大小不超过 4MB**（超过会报 `MessageTooLargeException`）。

```java
List<Message> batch = new ArrayList<>();
for (LogEntry entry : logEntries) {
    batch.add(
        new Message("mall-access-log-topic", 
                    entry.getContent().getBytes(StandardCharsets.UTF_8))
    );
}
producer.send(batch);   // 一次发送整批
```

### 5.2 批量发送注意事项

| 注意事项 | 说明 |
|----------|------|
| 统一 Topic | 一个批次内不能混用多个 Topic |
| 大小限制 | 单批 ≤ 4MB，可先压缩再发送 |
| 混合 Tag 失败 | 批量消息里加的 Tag 会被忽略 |
| 失败处理 | 批量发送失败通常整体重试或拆小批次 |

> 生产建议：日志类数据用批量发送，配合压缩（如 gzip），单条可塞上万条日志。

---

## 6. 过滤消息

### 6.1 Tag 过滤（简单模式）

Tag 是 Topic 下的二级分类，Broker 在消费端过滤：

```java
// 生产端：topic:tag
rocketMQTemplate.syncSend("mall-order-topic:pay-success", order);

// 消费端 1：只消费支付成功
@RocketMQMessageListener(topic = "mall-order-topic",
    consumerGroup = "pay-consumer-group", selectorExpression = "pay-success")

// 消费端 2：同时消费多个 Tag（用 || 分隔）
@RocketMQMessageListener(topic = "mall-order-topic",
    consumerGroup = "notify-consumer-group",
    selectorExpression = "pay-success || refund-success")

// 消费全部消息
@RocketMQMessageListener(topic = "mall-order-topic",
    consumerGroup = "all-consumer-group", selectorExpression = "*")
```

### 6.2 SQL 过滤（属性过滤）

基于消息属性的 SQL92 表达式过滤，支持 `>`、`<`、`=`、`IS NULL`、`AND`/`OR` 等：

```java
// 生产端：设置消息属性
Message msg = MessageBuilder.withPayload(event)
    .setHeader("payType", "wechat")
    .setHeader("amount", 299)
    .build();
rocketMQTemplate.syncSend("mall-order-topic", msg);

// 消费端：启用 SQL 过滤（需要 Broker 开启 enablePropertyFilter=true）
@RocketMQMessageListener(topic = "mall-order-topic",
    consumerGroup = "vip-order-group",
    selectorType = SelectorType.SQL92,
    selectorExpression = "payType = 'wechat' AND amount > 200")
```

### 6.3 两种过滤对比

| 维度 | Tag 过滤 | SQL 过滤 |
|------|----------|----------|
| 实现位置 | Broker 端简单匹配（client 过滤） | Broker 端解析 SQL92 |
| 性能 | 高 | 较低 |
| 表达能力 | 只支持等值 | 支持范围、组合条件 |
| 使用成本 | 简单 | 需要开启配置 |
| 推荐 | 优先使用 | 复杂筛选才用 |

---

## 总结

本章你学会了：

- 五种消息类型的特性与选型
- 分区顺序消息的 Queue 选择器实现
- 延迟消息的 18 个延迟等级与订单超时实战
- 批量消息的使用与限制
- Tag 过滤与 SQL 过滤的区别

下一步：进入 [事务消息](../02-core/01-transaction-message.md)，掌握分布式事务消息原理。