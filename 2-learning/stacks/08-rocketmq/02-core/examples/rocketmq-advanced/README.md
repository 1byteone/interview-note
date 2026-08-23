# RocketMQ 事务消息详解

## 什么是事务消息

事务消息解决"**本地数据库事务**"与"**发送消息**"的一致性问题。

典型场景：下单后需要通知其他系统（如积分、库存）。

```java
// 非事务方式的问题：
orderService.createOrder(order);      // 1. 写数据库
mqProducer.send(message);             // 2. 发消息
// 如果步骤 2 失败，订单已创建但消息没发 → 下游系统不知道有新订单
```

## 事务消息流程

```
生产者                      RocketMQ                 消费者
  │                           │                        │
  │── 1. 发送半消息 ──────────→│                        │
  │                           │ (半消息保存，不可见)      │
  │← 2. 发送结果 ─────────────│                        │
  │                           │                        │
  │── 3. 执行本地事务 ────────│                        │
  │    (写数据库)              │                        │
  │                           │                        │
  │── 4. COMMIT/ROLLBACK ────→│                        │
  │   (提交则消息可见)          │── 5. 投递消息 ─────────→│
  │                          │                        │
  │  ⚠ 如果 4 发送失败或超时    │                        │
  │     Broker 定时回查:       │                        │
  │←── 6. 回查请求 ────────────│                        │
  │── 7. 查询本地事务状态 ────→│                        │
  │   (返回 COMMIT/ROLLBACK)   │                        │
```

## 核心回调

| 回调方法 | 触发时机 | 职责 |
|---------|---------|------|
| `executeLocalTransaction` | 半消息发送成功后立即回调 | 执行本地数据库事务，返回 COMMIT/ROLLBACK/UNKNOW |
| `checkLocalTransaction` | Broker 回查（默认 1 分钟）| 查询本地事务真实状态（如按订单号查 DB），回复最终决定 |

## LocalTransactionState 三态

| 状态 | 含义 | Broker 行为 |
|------|------|------------|
| `COMMIT_MESSAGE` | 本地事务成功 | 消息对消费者可见 |
| `ROLLBACK_MESSAGE` | 本地事务失败 | 删除半消息，消费者永远看不到 |
| `UNKNOW` | 状态不明（事务未完成） | 不投递，稍后再次回查（有次数上限） |

## 与分布式事务对比

| 方案 | 一致性 | 性能 | 适用范围 |
|------|--------|------|---------|
| **消息事务（RocketMQ）** | 最终一致 | 高 | 不需要强一致，上下游解耦 |
| **二阶段提交（XA）** | 强一致 | 低（锁资源） | 银行转账等强一致场景 |
| **本地消息表** | 最终一致 | 中 | 不依赖 MQ 事务特性 |
| **TCC 模式** | 最终一致 | 中 | 需要预留资源，适用于 Seata |

## 使用注意事项

1. **幂等消费**：事务消息可能被重复投递，消费者必须做幂等处理
2. **不要做反向操作**：事务提交后，不要在回调里做耗时操作
3. **独立事务监听器**：不同业务使用不同 TransactionListener
4. **回查不可靠**：UNKNOW 状态有回查次数上限（默认 15 次/50 分钟），超时自动回滚
5. **半消息不可见**：测试时注意消费者要在 COMMIT 后才能收到消息

## 编译运行

```bash
# 1. 启动 RocketMQ
cd learn/08-rocketmq/01-basics/examples/rocketmq-quickstart
docker-compose up -d

# 2. 编译
cd java
mvn compile

# 3. 按顺序运行
mvn exec:java -Dexec.mainClass="com.example.rocketmq.transaction.TransactionConsumer"
mvn exec:java -Dexec.mainClass="com.example.rocketmq.transaction.TransactionProducer"
```

## 控制台验证

打开 RocketMQ Dashboard (http://localhost:8080)：
- **消息查询** → Topic 选择 `TopicTransaction` → 查看消息轨迹
- **消息轨迹** 中可见：半消息产生时间 → 本地事务提交 → 投递到消费者