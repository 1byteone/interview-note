# 事务消息

> RocketMQ 事务消息是解决"本地事务 + 消息发送"一致性的核心方案，也是面试最高频考点。
> 应用场景：AI 商城"下单 + 扣库存"跨服务分布式事务。

---

## 1. 为什么需要事务消息

### 1.1 问题背景：跨服务分布式事务

在 AI 商城中，创建订单（订单服务）和扣减库存（库存服务）是**两个独立的微服务**，无法用本地数据库事务完成。传统方案有两大痛点：

```
❌ 方案 A：先发消息，后写库
   发送成功 → 数据库挂 → 消息发了但订单没建 → 数据不一致

❌ 方案 B：先写库，后发消息
   数据库成功 → 发送失败 → 只下单不扣库存 → 数据不一致
```

### 1.2 RocketMQ 的答案：消息与本地事务"同生共死"

**核心思想：把消息发送和本地事务绑定在一起，消息是否对外可见取决于本地事务是否成功。**

- 本地事务成功 → 消息对外可见（消费者能消费）
- 本地事务失败 → 消息被删除（消费者看不到）
- 本地事务状态未知（宕机） → 通过**回查**机制确认

---

## 2. 事务消息完整流程（2PC + 回查）

```
Producer                              Broker
    │                                    │
    │ ① 发送半消息（prepare，此时不可见）──►│ 半消息存到事务 Topic，消费者不可见
    │◄─ ② 返回半消息确认                 │
    │                                    │
    │ ③ 执行本地事务（写订单表）           │
    │    ┌──────────────────┐            │
    │    │ ③④ 本地事务 COMMIT│            │
    │    │ ③⑥ 本地事务 ROLLBACK           │
    │    └──────────────────┘            │
    │                                    │
    │ ④ 提交 Commit ────────────────────►│ 消息变为可见 → 消费者可消费
    │ ⑤ 回滚 Rollback ─────────────────►│ 半消息被删除
    │                                    │
    │ ⑥ 若本地事务执行期间宕机/超时        │
    │◄─ ⑦ 事务回查（checkLocalTransaction）│ 定期主动询问"你的本地事务到底成功没？"
    │ ⑧ 回复 COMMIT / ROLLBACK ────────►│ 决定消息是可见还是删除
```

### 2.1 三步核心机制

| 步骤 | 机制 | 说明 |
|------|------|------|
| ① 半消息 | **Half Message** | 发送时消息先不可见，Broker 拿到后存到专门的半消息队列 |
| ② 本地事务 | **本地事务执行** | Producer 收到半消息确认后，执行本地业务（写订单表） |
| ③ 回查 | **Check-back** | 半消息长时间没收到提交/回滚，Broker 定期回查 Producer 询问结果 |

### 2.2 半消息可见性规则（重要）

| 消息状态 | 消费者可见性 |
|----------|--------------|
| 半消息（未提交） | ❌ 不可见，无法被消费 |
| 已提交（Commit） | ✅ 可见，正常消费 |
| 已回滚（Rollback） | ❌ 被删除，无法被消费 |

---

## 3. 代码实现

### 3.1 事务监听器（核心）

```java
@Component
@Slf4j
public class OrderTransactionListener implements TransactionListener {

    @Resource
    private OrderService orderService;

    /**
     * 第二步：执行本地事务
     */
    @Override
    public LocalTransactionState executeLocalTransaction(Message msg, Object arg) {
        try {
            OrderCreateRequest request = (OrderCreateRequest) arg;
            orderService.createOrder(request);          // 写订单表（本地事务）
            return LocalTransactionState.COMMIT_MESSAGE; // 成功 → 提交消息
        } catch (Exception e) {
            log.error("本地事务执行失败", e);
            return LocalTransactionState.ROLLBACK_MESSAGE; // 失败 → 回滚消息
        }
    }

    /**
     * 第三步：Broker 回查本地事务状态（半消息迟迟未提交时触发）
     */
    @Override
    public LocalTransactionState checkLocalTransaction(MessageExt msg) {
        // 通过消息 Keys（业务单号）查询订单是否存在
        String orderId = msg.getKeys();
        boolean orderExists = orderService.isOrderExists(orderId);
        return orderExists
            ? LocalTransactionState.COMMIT_MESSAGE     // 订单建成了 → 提交
            : LocalTransactionState.ROLLBACK_MESSAGE;  // 订单没建成 → 回滚
    }
}
```

### 3.2 发送事务消息

```java
@Service
public class OrderTransactionProducer {

    @Resource
    private RocketMQTemplate rocketMQTemplate;

    @Resource
    private OrderTransactionListener transactionListener;

    public boolean createOrderWithTransaction(OrderCreateRequest request) {
        // 第一步：发送半消息（事务消息）
        Message<OrderCreateRequest> msg = MessageBuilder
                .withPayload(request)
                .setHeader(RocketMQHeaders.KEYS, request.getOrderId())  // 业务键，用于回查
                .build();

        TransactionSendResult result = rocketMQTemplate.sendMessageInTransaction(
            "mall-order-stock-topic",    // Topic
            msg,                          // 消息
            request,                      // arg：传给 executeLocalTransaction
            transactionListener           // 事务监听器
        );

        return result.getLocalTransactionState() == LocalTransactionState.COMMIT_MESSAGE;
    }
}
```

### 3.3 (5.x 方式) TransactionListener 快捷实现

```java
rocketMQTemplate.sendMessageInTransaction("mall-order-stock-topic", msg, request,
    new TransactionListener() {
        @Override public LocalTransactionState executeLocalTransaction(Message msg, Object arg) {
            // 同 3.1
        }
        @Override public LocalTransactionState checkLocalTransaction(MessageExt msg) {
            // 同 3.1
        }
    });
```

---

## 4. 实战：AI 商城订单创建 + 库存扣减分布式事务

### 4.1 业务链路

```
用户点击"立即购买"
      ↓
① OrderService 发送半消息（订单+商品信息）
      ↓
② executeLocalTransaction: 本地事务
     │  a. 插入订单表（状态=待支付）
     │  b. 插入订单明细表
     │  └── 若有异常（如库存预校验失败）→ ROLLBACK
     ↓
③ COMMIT → 订单消息可见
      ↓
④ StockConsumer 消费消息，扣减库存
      ↓
⑤ 整个链路最终一致
```

### 4.2 消费端：库存扣减

```java
@Service
@RocketMQMessageListener(topic = "mall-order-stock-topic",
    consumerGroup = "mall-stock-group")
@Slf4j
public class StockConsumer implements RocketMQListener<OrderCreateRequest> {

    @Resource
    private StockService stockService;

    @Override
    public void onMessage(OrderCreateRequest request) {
        // 幂等：同一订单只扣一次（重复消费保护，见下一章）
        if (stockService.isStockDeducted(request.getOrderId())) {
            return;
        }
        // 扣减库存（乐观锁 + 流水表）
        stockService.deductStock(request.getOrderId(), request.getSkuId(), request.getQuantity());
        stockService.markDeducted(request.getOrderId());
    }
}
```

### 4.3 关键设计点

| 设计点 | 说明 |
|--------|------|
| 回查必须幂等 | 回查可能被调用多次，`isOrderExists` 查询必须稳定返回 |
| 业务键必须携带 | `msg.getKeys()` 是回查的依据，用订单号命名 |
| 本地事务要短 | 事务消息的半消息可见前有超时窗口，本地事务别做重操作 |
| 消费端仍要幂等 | 消息至少一次投递，消费端幂等是兜底 |
| 回滚消息的善后 | 半消息回滚后，业务上可能需要补偿（如释放 Redis 预占） |

---

## 5. 对比：事务消息 vs Seata TCC

### 5.1 两种思路

| 维度 | RocketMQ 事务消息 | Seata TCC |
|------|------------------|-----------|
| 方案类型 | 消息最终一致性 | 强一致（商业上近似强一致） |
| 参与者状态 | 本地事务独立提交，消息驱动后续 | Try-Confirm/Cancel 三阶段 |
| 一致性时间 | 最终一致（异步） | 同步（调用期间基本一致） |
| 实时性 | 秒级延迟 | 毫秒级 |
| 对业务侵入 | 低：只需实现监听器 | 高：需要实现 Try/Confirm/Cancel 三方法 |
| 空回滚处理 | 无此概念 | 需要处理空回滚、幂等、悬挂 |
| 适用场景 | 允许短暂不一致（下单+扣库存） | 强一致要求（账户余额扣减） |

### 5.2 如何选择

```yaml
# AI 商城链路选型建议
下单 + 扣库存：          RocketMQ 事务消息（用户可接受秒级延迟）
下单 + 积分/优惠券：      RocketMQ 事务消息（异步最终一致）
账户余额扣减（支付）：     Seata TCC / AT（强一致要求高）
购物车清空 + 扣库存：     RocketMQ 事务消息
```

> 关键原则：**能异步就异步、能最终一致就别强一致**。强一致的分布式事务性能损耗大，且跨服务锁定资源容易死锁。

---

## 总结

本章你学会了：

- 事务消息解决"消息与本地事务一致性"问题的原理
- 半消息 → 本地事务 → 提交/回滚 → 回查的完整流程
- 使用 `sendMessageInTransaction` + `TransactionListener` 的实现方式
- AI 商城"下单 + 扣库存"事务消息实战
- RocketMQ 事务消息与 Seata TCC 的选型对比

下一步：学习 [可靠性与幂等](02-reliability-and-idempotency.md)，掌握消息不丢失与重复消费的解决方案。