# RocketMQ 面试题大全

## 📚 知识体系

```
RocketMQ 核心概念
├── Topic（主题）
├── Message Queue（消息队列）
├── Producer（生产者）
├── Consumer（消费者）
├── NameServer（命名服务）
├── Broker（消息代理）
├── Message Model（消息模型）
│   ├── Cluster（集群消费）
│   └── Broadcasting（广播消费）
└── Message Type（消息类型）
    ├── Normal（普通消息）
    ├── Ordered（顺序消息）
    ├── Transaction（事务消息）
    └── Scheduled（延迟/定时消息）

RocketMQ 高级特性
├── 消息存储结构
│   ├── CommitLog
│   ├── ConsumeQueue
│   └── IndexFile
├── 消息可靠性
│   ├── 同步刷盘
│   ├── 异步刷盘
│   ├── 同步复制
│   └── 异步复制
├── 消息去重
├── 消息回溯
├── 消息轨迹
└── 消息过滤
```

---

## 🎯 Level 1：基础题

### 1. RocketMQ 的核心组件有哪些？
**答案**：

| 组件 | 作用 | 特点 |
|------|------|------|
| **NameServer** | 路由注册中心 | 无状态，可集群部署 |
| **Broker** | 消息存储与转发 | 主从架构，高可用 |
| **Producer** | 消息发送者 | 支持多种发送方式 |
| **Consumer** | 消息消费者 | 支持集群/广播消费 |
| **Topic** | 消息主题 | 逻辑分类 |
| **Queue** | 消息队列 | 物理分区，并发消费 |

### 2. RocketMQ 与 Kafka 的区别？
**答案**：

| 特性 | RocketMQ | Kafka |
|------|----------|-------|
| 消息可靠性 | 更高（同步双写） | 高 |
| 消息延迟 | 毫秒级 | 毫秒级 |
| 消息顺序 | 强顺序 | 分区内顺序 |
| 事务消息 | 原生支持 | 需额外实现 |
| 延迟消息 | 18 个等级 | 不支持 |
| 消息回溯 | 按时间精确回溯 | 按 offset |
| Topic 数 | 数千级别 | 数百级别 |
| 运维复杂度 | 中等 | 中等 |
| 阿里生态 | 完美集成 | 一般 |

---

## 🎯 Level 2：进阶题

### 3. 如何保证消息不丢失？
**答案**：

**消息生命周期**：
```
Producer → Broker → Consumer
```

**各阶段保障**：

**1. 生产端**：
- 同步发送：`producer.send(msg)` 等待 Broker 确认
- 失败重试：默认重试 2 次
- 发送回调：异步发送回调处理失败

**2. Broker 端**：
- 同步刷盘：`FlushDiskType.SYNC_FLUSH`（写入磁盘才返回 ack）
- 同步复制：主从同步复制（主写从同步后才返回）
- 集群部署：主从 + 多副本

**3. 消费端**：
- 消费完成后才提交 offset
- 业务处理失败则重试
- 死信队列处理最终失败的消息

### 4. RocketMQ 的消息存储结构？
**答案**：

**CommitLog**：单个文件，所有消息按顺序写入

```
CommitLog（顺序写）
┌─────────────────────────────────┐
│ Msg1  │ Msg2  │ Msg3  │ ...    │
└─────────────────────────────────┘
```

**ConsumeQueue**：逻辑队列，存 CommitLog 的物理偏移量

```
ConsumeQueue（Topic/Queue）
┌──────────────────────┐
│ offset │ size │ tag  │
├──────────────────────┤
│ 0x0001 │ 1024  │ A   │
├──────────────────────┤
│ 0x0005 │ 2048  │ B   │
└──────────────────────┘
```

**IndexFile**：索引文件，根据 key 快速查找消息

**优点**：
- CommitLog 顺序写，性能极高
- ConsumeQueue 逻辑隔离，便于水平扩展
- 文件大小固定（1GB），便于内存映射

---

## 🎯 Level 3：高级题

### 5. 事务消息的实现原理？
**答案**：

**2PC（两阶段提交）+ 事务回查**

```text
Producer
    │
    │  ① 发送半消息（prepare）
    ▼
Broker（半消息，不可见）
    │
    │  ② 返回半消息确认
    ▼
Producer（执行本地事务）
    │
    │  ③ 提交/回滚事务
    ▼
Broker（提交 → 可见；回滚 → 删除）
    │
    └── ④ 回查：若未收到 ③，反查生产者事务状态
```

**代码示例**：
```java
// 1. 实现事务监听器
public class OrderTransactionListener implements TransactionListener {
    @Override
    public LocalTransactionState executeLocalTransaction(Message msg, Object arg) {
        // 执行本地事务（如：创建订单）
        try {
            orderService.createOrder(arg);
            return LocalTransactionState.COMMIT_MESSAGE;
        } catch (Exception e) {
            return LocalTransactionState.ROLLBACK_MESSAGE;
        }
    }
    
    @Override
    public LocalTransactionState checkLocalTransaction(MessageExt msg) {
        // 事务回查：检查本地事务是否成功
        return orderService.checkOrder(msg.getKeys()) 
            ? COMMIT_MESSAGE : ROLLBACK_MESSAGE;
    }
}
```

### 6. 消息重复消费如何解决？
**答案**：

**消息重复的原因**：
- 生产者重试发送（网络抖动，发送成功但 ack 丢失）
- 消费者重试（消费成功但 offset 提交失败）

**解决方案：幂等性设计**

```java
// 方案一：数据库唯一约束
public void consumeOrder(Message msg) {
    String orderId = msg.getKeys();
    // 利用数据库唯一索引防止重复插入
    orderService.insertOrder(orderId, msg.getBody());
    // 重复插入会抛异常，捕获后视为已消费
}

// 方案二：Redis 去重
public void consumeMessage(Message msg) {
    String msgId = msg.getMsgId();
    // 设置 1 分钟过期，同一条消息 1 分钟内不重复消费
    Boolean success = redis.setIfAbsent("msg:" + msgId, "1", 60);
    if (!success) {
        return; // 已消费过
    }
    // 执行业务逻辑
    process(msg);
}

// 方案三：业务状态机
public void consumePayment(Message msg) {
    Payment payment = parsePayment(msg);
    // 只处理"待支付"状态的订单
    boolean updated = paymentMapper.updateStatus(
        payment.getOrderId(), 
        "待支付", "已支付"
    );
    if (updated == 0) {
        // 状态已变更，说明已处理
        return;
    }
}
```

---

## 🎯 Level 4：专家题

### 7. RocketMQ 消息堆积如何处理？
**答案**：

**堆积原因**：
- 消费速度 < 生产速度
- 消费者异常（宕机、阻塞）
- 路由策略不均衡

**处理方案**：

**1. 临时扩容（最快）**：
```
问题：单个 Queue 堆积
方案：增加 Queue 数量 + 增加消费者
```

**2. 排查消费慢的原因**：
- 检查消费者是否频繁 GC
- 检查业务逻辑是否耗时（数据库慢查询、外部 API）
- 检查是否批量消费（`consumeMessageBatchMaxSize`）

**3. 紧急处理**：
```java
// 堆积超过阈值，转到临时队列快速消费
if (queueDepth > 100000) {
    // 跳过耗时逻辑，直接记录到临时表
    temporaryStorage.save(msgs);
    // 后续异步处理
    return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
}
```

**4. 代码层面优化**：
```java
// 批量消费，提高吞吐
consumer.setConsumeMessageBatchMaxSize(64);  // 一次拉取 64 条

// 异步处理
consumer.registerMessageListener((List<MessageExt> msgs, ConsumeConcurrentlyContext context) -> {
    asyncExecutor.execute(() -> processBatch(msgs));
    return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
});
```

### 8. 如何保证消息的顺序性？
**答案**：
**顺序消息要求**：消息发送到同一个 Queue，消费者单线程消费同一个 Queue。

**实现**：
```java
// 1. 生产者：将同一业务的消息发送到同一个 Queue
producer.send(msg, (mqs, msg, arg) -> {
    // 按订单 ID 哈希选择 Queue，保证同一订单发送到同一 Queue
    String orderId = (String) arg;
    int queueIndex = orderId.hashCode() % mqs.size();
    return mqs.get(queueIndex);
}, orderId);

// 2. 消费者：顺序消费
consumer.registerMessageListener((List<MessageExt> msgs, ConsumeOrderlyContext context) -> {
    // 自动加锁，同一 Queue 的消息串行消费
    for (MessageExt msg : msgs) {
        processOrder(msg);
    }
    return ConsumeOrderlyStatus.SUCCESS;
});
```

---

## 📖 学习资源

### 推荐项目
- [RocketMQ 官方文档](https://rocketmq.apache.org/)
- [RocketMQ 示例](https://github.com/apache/rocketmq/tree/master/example)
- [RocketMQ-Exporter](https://github.com/apache/rocketmq-exporter) - 监控

### 最佳实践
1. 生产环境 NameServer 至少 2 节点
2. Broker 主从部署，同步复制
3. 核心业务消息使用同步发送
4. 消费端实现幂等
5. 监控 Topic 堆积量，及时告警