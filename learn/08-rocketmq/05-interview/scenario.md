# 场景题：消息积压、重复消费、顺序消息乱序、分布式事务失败

> 面试场景题，考察你面对真实 MQ 问题时的分析和解决能力。

---

## 场景 1：消息积压

### 题目

双十一大促期间，AI 商城秒杀订单量暴增 10 倍，RocketMQ 控制台显示 `mall-seckill-topic` 的 Diff Total 达到 50 万，且持续增长，消费者消费速度跟不上。

### 分析

**第一步：确认积压原因**

```bash
# 1. 查看消费端日志
grep "ERROR" consumer.log | tail -50

# 2. 查看消费 TPS
mqadmin consumerProgress -n localhost:9876 -g mall-seckill-consumer-group

# 3. 查看 Broker 资源
# CPU > 80%？磁盘 IO 100%？内存不足？
```

**第二步：快速处理**

| 方案 | 操作 | 效果 |
|------|------|------|
| **临时扩容消费端** | 增加消费实例（水平扩展） | 立即提升消费能力 |
| **增加 Queue 数量** | 修改 Topic 的 Queue 数（需重启） | 提升并行度 |
| **跳过异常消息** | 业务异常不重试，直接跳过 | 防止重试堆叠 |
| **死信快速清理** | 暂存死信，大促后处理 | 清理阻塞 |

### 解决方案

**方案一：临时扩容消费端（最快，5 分钟）**

```bash
# 1. 增加消费实例（从 3 台扩展到 10 台）
kubectl scale deployment mall-seckill-consumer --replicas=10

# 2. 确保 Queue 数 >= 实例数（否则扩容无效）
# 如果 Topic 只有 8 个 Queue，10 个实例中有 2 个会空闲
```

**方案二：跳过不可重试的业务异常**

```java
@Override
public void onMessage(SeckillOrderEvent event) {
    try {
        processSeckill(event);
    } catch (BizException e) {
        // 业务异常（如重复秒杀），跳过不重试
        log.warn("业务异常，跳过: {}", e.getMessage());
        // 不抛异常，RocketMQ 认为消费成功，不会重试
    } catch (Exception e) {
        // 系统异常（如 DB 超时），需要重试
        log.error("系统异常，触发重试", e);
        throw e;  // 触发重试
    }
}
```

**方案三：大促结束后补偿**

```java
// 大促后，重新消费积压消息
// 方法：重置消费 offset 到积压开始时间点
mqadmin resetOffsetByTime -n localhost:9876 \
  -g mall-seckill-consumer-group \
  -t mall-seckill-topic \
  -s now-1h
```

### 预防措施

```
1. 容量规划：提前预估 TPS，预留 2 倍 Buffer
2. 监控告警：积压 > 1000 → 黄色预警，> 10000 → 红色预警
3. 压测：大促前做全链路压测，确认消费能力
4. 限流：生产端限流，防止瞬间暴增
5. 熔断：消费失败率 > 10% 触发熔断，降级到简化处理
```

---

## 场景 2：重复消费

### 题目

用户反馈支付成功后收到了两次积分发放通知，排查发现 RocketMQ 的 `mall-payment-topic` 消息被消费了两次，积分多发了。

### 分析

**原因**：RocketMQ 保证"至少一次投递"，网络抖动或 Rebalance 可能导致重复投递。

**根本原因排查**：

```java
// 1. 检查消费端是否已实现幂等
if (pointService.isPointsGranted(orderId)) {
    log.info("积分已发放，跳过重复: {}", orderId);
    return;
}

// 2. 检查消费端是否提交了 offset 但处理失败
// 消费成功 → 提交 offset 超时 → 下次拉取重复投递
```

### 解决方案

**方案一：唯一键表（推荐）**

```sql
CREATE TABLE idempotent_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    biz_id VARCHAR(64) NOT NULL UNIQUE,  -- 业务键（订单号+场景）
    status TINYINT DEFAULT 0,
    create_time DATETIME
);
```

```java
public void consume(PaymentEvent event) {
    try {
        // 利用唯一约束，重复插入抛异常
        idempotentMapper.insert(new IdempotentRecord("point:" + event.getOrderId()));
        // 发放积分
        pointService.grantPoints(event.getUserId(), event.getAmount());
    } catch (DuplicateKeyException e) {
        log.info("已处理，跳过: {}", event.getOrderId());
    }
}
```

**方案二：Redis 分布式锁**

```java
public void consumeWithLock(PaymentEvent event) {
    String lockKey = "point:grant:" + event.getOrderId();
    Boolean locked = redisTemplate.opsForValue()
        .setIfAbsent(lockKey, "1", 30, TimeUnit.SECONDS);
    if (!Boolean.TRUE.equals(locked)) {
        return;  // 正在处理或已处理
    }
    try {
        pointService.grantPoints(event.getUserId(), event.getAmount());
    } finally {
        redisTemplate.delete(lockKey);
    }
}
```

**方案三：数据库乐观锁**

```java
// 更新订单状态时使用条件（状态机）
int updated = orderMapper.updateStatus(
    orderId, 
    OrderStatus.CREATED,   // 当前状态
    OrderStatus.PAID       // 目标状态
);
if (updated == 0) {
    // 已处理过，跳过
    return;
}
```

---

## 场景 3：顺序消息乱序

### 题目

AI 商城的订单状态流转消息（CREATED → PAID → SHIPPED → COMPLETED）出现了乱序，消费者收到"PAID"比"CREATED"更早，导致订单状态机异常。

### 分析

**原因一：消息发送端没有使用 Queue 选择器**

```
❌ 错误：所有消息随机发送到不同 Queue
Topic Queue 0: CREATED
Topic Queue 1: PAID      ← 不同 Queue 没有顺序保证
Topic Queue 2: SHIPPED
```

**原因二：Rebalance 导致消费线程切换**

```
消费端 Rebalance 后，同一个订单的消息被分配到不同消费线程
```

### 解决方案

**方案一：按订单号分区发送（保证同一订单入同一 Queue）**

```java
// 发送端：使用 MessageQueueSelector
SendResult result = rocketMQTemplate.syncSend(
    "mall-order-status-topic",
    MessageBuilder.withPayload(event)
        .setHeader(RocketMQHeaders.KEYS, event.getOrderId())
        .build(),
    3000,
    (msg, queueList) -> {
        // 按订单号 hash 选择固定 Queue
        int index = Math.abs(event.getOrderId().hashCode()) % queueList.size();
        return queueList.get(index);
    }
);
```

**方案二：消费端使用单线程消费组**

```java
// 配置：消费线程数设为 1，确保同一时间只消费一条消息
@RocketMQMessageListener(
    topic = "mall-order-status-topic",
    consumerGroup = "mall-order-status-group",
    consumeThreadMax = 1   // 单线程串行消费
)
```

**方案三：消费端实现幂等状态机**

```java
// 即使乱序，通过状态机校验也能保证逻辑正确
public void updateStatus(OrderStatusEvent event) {
    // 状态机：允许的状态转换
    // CREATED → PAID → SHIPPED → COMPLETED
    // 如果收到 PAID 但当前状态是 CREATED，可以处理
    // 如果收到 CREATED 但当前状态是 PAID，说明是重复的旧消息，跳过
    Order current = orderMapper.selectById(event.getOrderId());
    if (!isValidTransition(current.getStatus(), event.getTargetStatus())) {
        log.warn("状态转换无效: {} -> {}",
            current.getStatus(), event.getTargetStatus());
        return;
    }
    orderMapper.updateStatus(event.getOrderId(), event.getTargetStatus());
}
```

---

## 场景 4：分布式事务失败

### 题目

AI 商城的订单服务使用事务消息创建订单，本地事务执行成功，但提交 COMMIT 消息时网络异常，导致消息一直处于半消息状态，消费者无法消费，订单最终状态不一致。

### 分析

```
半消息发送成功
    ↓
本地事务执行成功（订单已创建）
    ↓
COMMIT 请求发送失败（网络异常）
    ↓
消息一直不可见 → 库存没扣减 → 订单状态不一致
    ↓
依赖回查机制恢复（最长 60s 延迟）
```

### 解决方案

**方案一：等待回查（RocketMQ 内置保证）**

```
Broker 每 60s 扫描半消息
  → 发现未提交的半消息
  → 回调 Producer 的 checkLocalTransaction()
  → 查询订单存在 → COMMIT
  → 消息可见
  → 库存扣减
```

**方案二：手动补偿（兜底）**

```java
// 定时任务：扫描未处理的订单，主动处理
@Scheduled(fixedDelay = 30000)
public void compensatePendingOrders() {
    // 查询创建时间超过 5 分钟且状态为"待支付"的订单
    List<Order> pendingOrders = orderMapper.findByStatusAndTime(
        OrderStatus.CREATED, LocalDateTime.now().minusMinutes(5));
    
    for (Order order : pendingOrders) {
        // 检查是否已发送消息（查询消息表）
        if (!messageService.isMessageSent(order.getOrderId())) {
            // 重新发送事务消息
            sendTransactionMessage(order);
        }
    }
}
```

**方案三：本地消息表 + 定时任务**

如果事务消息或回查机制仍不可靠，可以用本地消息表兜底：

```java
// 1. 本地事务：写订单表 + 写本地消息表（同一个事务）
@Transactional
public void createOrder(OrderCreateRequest request) {
    orderMapper.insert(order);
    localMessageMapper.insert(new LocalMessage(
        "mall-order-stock-topic", 
        JSON.toJSONString(order), 
        0    // 待发送
    ));
}

// 2. 定时任务：扫描未发送的消息，补偿发送
@Scheduled(fixedDelay = 5000)
public void compensateMessages() {
    List<LocalMessage> pending = localMessageMapper.findByStatus(0);
    for (LocalMessage msg : pending) {
        try {
            rocketMQTemplate.syncSend(msg.getTopic(), msg.getBody(), 3000);
            msg.setStatus(1);  // 已发送
            localMessageMapper.update(msg);
        } catch (Exception e) {
            log.error("补偿发送失败", e);
        }
    }
}
```

---

## 场景 5：Broker 宕机

### 题目

线上 Broker 节点突然宕机，发现该节点上的 Topic 无法消费，消息发送超时，业务受损。

### 分析

| 部署模式 | 影响 | 恢复方式 |
|----------|------|----------|
| 单节点 | 完全不可用 | 重启 Broker |
| 主从（非 Dledger） | 生产者不可写，消费者只读 | 手动切换 Slave 为 Master |
| Dledger 集群 | 自动选举新 Leader，短暂中断 | 自动恢复 |

### 解决方案

**Dledger 模式自动恢复（推荐）**

```
1. 监控发现 Broker 宕机
2. 心跳超时（1-3s）
3. Raft 选举（1-2s）
4. 新 Leader 产生
5. 生产者恢复写入，消费者恢复消费
6. 总中断时间：3-5s
```

**非 Dledger 模式手动恢复**

```bash
# 1. 检查 Slave 节点状态
mqadmin clusterInfo -n localhost:9876

# 2. 将 Slave 升为 Master（修改 broker.conf 后重启）
brokerRole=SYNC_MASTER
brokerId=0

# 3. 更新 DNS 或配置中心的 Broker 地址
# 4. 原 Master 恢复后作为 Slave 加入集群
```

---

## 总结

| 场景 | 核心考点 | 一句话回答 |
|------|----------|------------|
| 消息积压 | 扩容 + 限流 + 跳过异常 | 先扩容应急，后排查根因 |
| 重复消费 | 幂等方案 | 唯一键表是最实用的方案 |
| 顺序消息乱序 | Queue 选择器 + 状态机 | 分区有序 + 幂等兜底 |
| 分布式事务失败 | 回查机制 + 补偿 | 等待回查或本地消息表补偿 |
| Broker 宕机 | Dledger 自动选主 | 生产环境必须用 Dledger 集群 |