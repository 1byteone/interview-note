# AI 商城集成

> 将 RocketMQ 应用到 AI 智能商城的完整实战方案，覆盖订单异步处理、秒杀削峰、分布式事务、消息追踪。

---

## 深度剖析参考

| 主题 | 本 learn 文档 | docs/tech-stack-analysis 深度剖析 |
|------|--------------|--------------------------------|
| RocketMQ 消息驱动 | 本文 | [09-ROCKETMQ.md](../../../docs/tech-stack-analysis/mall-micro-cloud/09-ROCKETMQ.md) — 订单回调/库存同步/最终一致性 |
| 秒杀库存 MQ 削峰 | 本文 § 秒杀 | [06-SECKILL-HIGHCONCUR.md](../../../docs/tech-stack-analysis/mall-micro-cloud/06-SECKILL-HIGHCONCUR.md) — Redis 扣减+MQ 双写 |
| MQ 幂等消费 | — | [11-SCHEDULER-BLOOMFILTER.md](../../../docs/tech-stack-analysis/mall-micro-cloud/11-SCHEDULER-BLOOMFILTER.md) — transactionId 幂等 |
| 购物车清空(消费端) | — | [05-CART-MONGODB.md](../../../docs/tech-stack-analysis/mall-micro-cloud/05-CART-MONGODB.md) — MongoDB 购物车 |

---

## 1. 整体消息架构

### 1.1 AI 商城消息拓扑

```
┌─────────────────────────────────────────────────────────────────────┐
│                        AI 智能商城消息架构                           │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  ┌──────────┐    ┌──────────┐    ┌──────────┐    ┌──────────┐      │
│  │ 订单服务  │    │ 支付服务  │    │ 库存服务  │    │ AI 推荐  │      │
│  └────┬─────┘    └────┬─────┘    └────┬─────┘    └────┬─────┘      │
│       │               │               │               │            │
│       ▼               ▼               ▼               ▼            │
│  ┌──────────────────────────────────────────────────────────────┐  │
│  │                    RocketMQ 集群                              │  │
│  │  mall-order-topic │ mall-payment-topic │ mall-stock-topic    │  │
│  │  mall-seckill-topic │ mall-log-topic   │ mall-notify-topic   │  │
│  └──────────────────────────────────────────────────────────────┘  │
│       │               │               │               │            │
│       ▼               ▼               ▼               ▼            │
│  ┌──────────┐    ┌──────────┐    ┌──────────┐    ┌──────────┐      │
│  │ 库存服务  │    │ 积分服务  │    │ 短信服务  │    │ 日志服务  │      │
│  └──────────┘    └──────────┘    └──────────┘    └──────────┘      │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

### 1.2 Topic 规划

| Topic | 消息类型 | 生产者 | 消费者 | 说明 |
|-------|----------|--------|--------|------|
| `mall-order-topic` | 事务消息 | 订单服务 | 库存服务、积分服务 | 下单+扣库存+加积分 |
| `mall-payment-topic` | 普通消息 | 支付服务 | 订单服务、短信服务 | 支付回调通知 |
| `mall-seckill-topic` | 普通消息 | 秒杀服务 | 库存服务 | 秒杀削峰 |
| `mall-order-timeout-topic` | 延迟消息 | 订单服务 | 订单服务 | 超时关单 |
| `mall-notify-topic` | 普通消息 | 各服务 | 短信/邮件服务 | 通知 |
| `mall-log-topic` | 批量消息 | 各服务 | 日志服务 | 操作日志 |

---

## 2. 订单异步处理

### 2.1 业务链路

```
用户下单
  ↓
① 订单服务接收请求
② 入参校验（商品是否存在、库存是否充足）
③ 创建订单（状态=待支付）
④ 发送"订单创建"消息到 mall-order-topic
⑤ 响应前端"下单成功，请支付"
  ↓
⑥ 库存服务消费消息 → 预扣库存（Redis 锁定）
⑦ 积分服务消费消息 → 准备积分发放
⑧ AI 推荐服务消费消息 → 更新用户画像
```

### 2.2 订单服务实现

```java
@Service
public class OrderService {

    @Resource
    private RocketMQTemplate rocketMQTemplate;

    @Transactional
    public Order createOrder(OrderCreateRequest request) {
        // 1. 校验
        Product product = productClient.getProduct(request.getSkuId());
        if (product.getStock() < request.getQuantity()) {
            throw new BizException("库存不足");
        }
        // 2. 创建订单
        Order order = new Order();
        order.setOrderId("ORD" + System.currentTimeMillis());
        order.setUserId(request.getUserId());
        order.setTotalAmount(calculateAmount(request));
        order.setStatus(OrderStatus.CREATED);
        orderMapper.insert(order);
        // 3. 发送消息（异步通知）
        OrderCreatedEvent event = new OrderCreatedEvent(order);
        SendResult result = rocketMQTemplate.syncSend(
            "mall-order-topic:order-created", event, 3000);
        if (result.getSendStatus() != SendStatus.SEND_OK) {
            // 补偿：写本地消息表
            saveMessageToCompensationTable("mall-order-topic", event);
        }
        return order;
    }
}
```

### 2.3 库存服务消费

```java
@Service
@RocketMQMessageListener(topic = "mall-order-topic",
    consumerGroup = "mall-stock-group",
    selectorExpression = "order-created")
@Slf4j
public class StockConsumer implements RocketMQListener<OrderCreatedEvent> {

    @Resource
    private StringRedisTemplate redisTemplate;

    @Override
    public void onMessage(OrderCreatedEvent event) {
        String orderId = event.getOrderId();

        // 1. Redis 幂等锁
        String lockKey = "stock:deduct:" + orderId;
        Boolean locked = redisTemplate.opsForValue()
            .setIfAbsent(lockKey, "1", 30, TimeUnit.SECONDS);
        if (!Boolean.TRUE.equals(locked)) {
            log.info("重复消息跳过: {}", orderId);
            return;
        }
        try {
            // 2. 扣减库存（乐观锁：update stock set quantity=quantity-? 
            //    where sku_id=? and quantity>=?）
            int updated = stockMapper.deductStock(event.getSkuId(), event.getQuantity());
            if (updated == 0) {
                log.warn("库存不足, orderId={}, skuId={}", orderId, event.getSkuId());
                // 触发订单取消逻辑
                rocketMQTemplate.syncSend("mall-order-topic:stock-fail", event);
            }
        } finally {
            // 注意：不要立即释放锁，等业务处理完再释放
            // 或者使用 TTL 自动过期
        }
    }
}
```

---

## 3. 秒杀削峰（请求排队）

### 3.1 秒杀架构

```
用户请求 ──► Nginx 负载均衡
              │
              ▼
          秒杀服务 ── 前置校验（Redis 预扣库存）
              │
              ▼
          RocketMQ 消息队列 ──► 削峰：请求排队，后端按能力消费
              │
              ▼
          库存服务 ── 幂等 + 乐观锁写入 DB
              │
              ▼
          发送结果通知
```

### 3.2 秒杀消息入队

```java
@Service
public class SeckillService {

    @Resource
    private StringRedisTemplate redisTemplate;
    @Resource
    private RocketMQTemplate rocketMQTemplate;

    public SeckillResult handleSeckill(Long userId, Long skuId) {
        // 1. Redis 预扣库存（先判断再扣减，原子操作）
        String stockKey = "seckill:stock:" + skuId;
        Long stock = redisTemplate.opsForValue().decrement(stockKey);
        if (stock == null || stock < 0) {
            // 回滚（如果扣成负数了）
            redisTemplate.opsForValue().increment(stockKey);
            return SeckillResult.fail("库存不足");
        }
        // 2. 发送消息到队列（限流削峰）
        rocketMQTemplate.asyncSend("mall-seckill-topic",
            new SeckillOrderEvent(userId, skuId),
            new SendCallback() {
                @Override
                public void onSuccess(SendResult result) {
                    log.info("秒杀消息入队成功: {}", result.getMsgId());
                }
                @Override
                public void onException(Throwable e) {
                    // 发送失败，回滚 Redis 预扣库存
                    redisTemplate.opsForValue().increment(stockKey);
                    log.error("秒杀消息入队失败", e);
                }
            });
        return SeckillResult.success("排队中，请稍后查看结果");
    }
}
```

### 3.3 秒杀消费端

```java
@Service
@RocketMQMessageListener(topic = "mall-seckill-topic",
    consumerGroup = "mall-seckill-consumer-group",
    consumeThreadMax = 8)           // 限制消费线程数，防止 DB 被打满
@Slf4j
public class SeckillConsumer implements RocketMQListener<SeckillOrderEvent> {

    @Override
    public void onMessage(SeckillOrderEvent event) {
        // 1. 幂等：防止重复创建订单
        if (orderMapper.existsByUserIdAndSkuId(event.getUserId(), event.getSkuId())) {
            return;
        }
        // 2. 数据库扣减库存（乐观锁，与 Redis 二次校验）
        int updated = stockMapper.deductStock(event.getSkuId(), 1);
        if (updated == 0) {
            log.warn("DB 库存不足: skuId={}", event.getSkuId());
            return;
        }
        // 3. 创建订单
        orderMapper.insert(createOrder(event));
        // 4. 发送成功通知
        rocketMQTemplate.syncSend("mall-seckill-topic:success",
            new SeckillSuccessEvent(event.getUserId(), event.getSkuId()));
    }
}
```

### 3.4 削峰三大策略

| 策略 | 说明 | 实现 |
|------|------|------|
| **请求排队** | 秒杀请求先入队，后端异步处理 | RocketMQ 普通消息 |
| **消费限流** | 控制消费端线程数，不冲垮 DB | `consumeThreadMax` 参数 |
| **熔断降级** | 消费失败率过高时自动降级 | 配合 Sentinel 熔断 |

---

## 4. 分布式事务（订单+库存+积分）

### 4.1 三步最终一致性

```
Step 1: 订单服务 → 事务消息
  ├── 发送半消息
  ├── 本地事务：创建订单（状态=待支付）
  └── COMMIT 消息

Step 2: 库存服务 → 幂等消费
  ├── 消费"订单创建"消息
  ├── 扣减库存（乐观锁）
  └── 发送"库存扣减成功"消息

Step 3: 积分服务 → 异步通知
  ├── 消费"库存扣减成功"消息
  ├── 发放积分
  └── 完成
```

### 4.2 完整代码

```java
// 订单服务：事务消息
public class OrderTransactionListener implements TransactionListener {
    @Override
    public LocalTransactionState executeLocalTransaction(Message msg, Object arg) {
        OrderCreateRequest request = (OrderCreateRequest) arg;
        try {
            orderService.createOrder(request);  // 本地事务
            return LocalTransactionState.COMMIT_MESSAGE;
        } catch (Exception e) {
            return LocalTransactionState.ROLLBACK_MESSAGE;
        }
    }
    @Override
    public LocalTransactionState checkLocalTransaction(MessageExt msg) {
        // 回查：订单是否存在
        boolean exists = orderService.isOrderExists(msg.getKeys());
        return exists ? COMMIT_MESSAGE : ROLLBACK_MESSAGE;
    }
}

// 库存服务：幂等消费 + 乐观锁
public void onMessage(OrderCreatedEvent event) {
    // 幂等锁
    if (idempotentService.isProcessed(event.getOrderId())) return;
    // 乐观锁扣减
    stockService.deductStock(event.getSkuId(), event.getQuantity());
    idempotentService.markProcessed(event.getOrderId());
}

// 积分服务：异步消费
public void onMessage(StockDeductedEvent event) {
    // 幂等：同一订单不重复发积分
    if (pointService.isPointsGranted(event.getOrderId())) return;
    // 发放积分（金额的 1%）
    pointService.grantPoints(event.getUserId(), event.getAmount() * 0.01);
    pointService.markGranted(event.getOrderId());
}
```

---

## 5. 消息追踪

### 5.1 用户视角的消息链路

用户在 AI 商城操作后，可以通过消息追踪系统查看完整链路：

```
订单 ORD1234567890
  ├── 2026-08-22 10:00:01   订单创建（生产者）
  ├── 2026-08-22 10:00:01   消息发送到 mall-order-topic
  ├── 2026-08-22 10:00:01   消息确认（Broker）
  ├── 2026-08-22 10:00:02   库存服务消费（成功，耗时 12ms）
  ├── 2026-08-22 10:00:03   积分服务消费（成功，耗时 5ms）
  └── 2026-08-22 10:00:04   短信服务消费（成功，耗时 120ms）
```

### 5.2 消息追踪表（数据库）

```sql
CREATE TABLE message_trace (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    msg_id VARCHAR(64) NOT NULL,                -- 消息 ID
    order_id VARCHAR(64) NOT NULL,              -- 业务订单号
    topic VARCHAR(100) NOT NULL,                -- Topic
    producer_app VARCHAR(50),                   -- 生产者服务
    producer_time DATETIME,                     -- 生产时间
    broker_time DATETIME,                       -- Broker 存储时间
    consumer_app VARCHAR(50),                   -- 消费者服务
    consumer_time DATETIME,                     -- 消费时间
    consumer_status VARCHAR(20),               -- 消费状态
    retry_count INT DEFAULT 0,                 -- 重试次数
    cost_time_ms INT,                          -- 消费耗时
    INDEX idx_order_id (order_id),
    INDEX idx_msg_id (msg_id)
);
```

### 5.3 追踪日志集成

```java
// 在消息中携带链路追踪 ID
Message msg = MessageBuilder.withPayload(event)
    .setHeader("traceId", TraceContext.getTraceId())   // 透传 Trace ID
    .setHeader(RocketMQHeaders.KEYS, event.getOrderId())
    .build();

// 消费端打印追踪日志
@Slf4j
public class TraceAwareConsumer implements RocketMQListener<OrderEvent> {
    @Override
    public void onMessage(OrderEvent msg) {
        String traceId = msg.getTraceId();    // 从消息头获取
        MDC.put("traceId", traceId);          // 设置到 MDC
        log.info("开始处理订单: {}", msg.getOrderId());
        // ... 业务处理
        log.info("订单处理完成: {}", msg.getOrderId());
        MDC.clear();
    }
}
```

---

## 总结

本章你学会了：

- AI 商城的完整消息架构与 Topic 规划
- 订单异步处理链路：订单服务 → 库存/积分/AI 推荐
- 秒杀削峰方案：Redis 预扣 + RocketMQ 排队 + 限流消费
- 分布式事务：事务消息 + 幂等消费 + 最终一致性
- 消息追踪：链路追踪表 + 透传 Trace ID

**最佳实践**：所有消息消费端必须幂等，所有重要消息必须有补偿机制，所有消息链路必须可追踪。

下一步：完成 [迷你异步通知系统项目](mini-blog/README.md)，巩固所学知识。