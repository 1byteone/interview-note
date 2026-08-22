# RocketMQ 速查卡 📋

> 面试前30分钟扫一遍，一页纸覆盖核心考点

## 🏷️ 核心概念速记

| 概念 | 一句话解释 | 常见陷阱 |
|------|-----------|----------|
| 消息模型 | Producer → Topic → (MessageQueue) → Consumer，topic 下多队列并行 | 顺序只在一个 Queue 内保证，跨 Queue 无序 |
| 架构组件 | NameServer(路由)、Broker(存储主从)、Producer、Consumer | NameServer 无状态，Broker 心跳注册；Topic 路由由客户端本地缓存 |
| 事务消息 | 半消息 + 本地事务 + 回查补偿，保证最终一致 | 本地事务回调里 openTransaction 提交确认，否则回查 |
| 顺序消息 | 全局/分区顺序，MessageQueueSelector 按业务键选队列 | 消费端也要保证单线程/串行处理，否则乱序 |
| 幂等消费 | 消费端处理重复消息不产生副作用 | 重试/重复投递不可避免，消费必须幂等 |
| 削峰填谷 | MQ 缓存瞬时流量，下游按能力消费 | 消费速度 < 生产速度会造成积压，需评估 |
| 消息重试 | 默认 16 次；超过进死信队列(DMQ) | 消息重试有延迟等级，业务故障和代码 bug 要区分处理 |
| 死信队列 | 重试耗尽的消息进 %DLQ% 队列，人工处理 | 死信要监控和告警，不能静默丢弃 |
| 消息轨迹 | 消息全链路(生产/存储/消费)追踪 | 消费失败但已重试的轨迹要能定位到原因 |
| 广播模式 | 每个消费者都收到全量消息 | 消费组内共享的是集群模式，广播是各消费一份 |
| 消费进度 | 消费位点 offset 存储，重置 offset 可回溯消费 | 消费组新增实例会重新负载均衡，offset 竞态要处理 |

## 🔧 常用命令/API

```java
// 事务消息流程（核心考点）
// 1. Producer 发送半消息(HALF)，Broker 暂存不可见
// 2. RocketMQ 回调 executeLocalTransaction：执行本地事务并返回状态
// 3. COMMIT → 半消息变为可见消息
// 4. ROLLBACK → 半消息删除
// 5. Broker 长时间没收到确认 → 回查 checkLocalTransaction
public class TransactionOrderProducer {
    public static void main(String[] args) throws Exception {
        TransactionMQProducer producer = new TransactionMQProducer("order_producer_group");
        producer.start();

        producer.setTransactionListener(new TransactionListener() {
            @Override
            public LocalTransactionState executeLocalTransaction(Message msg, Object arg) {
                // 1. 本地业务（订单表插入）
                // 2. 成功 → COMMIT_MESSAGE；失败 → ROLLBACK_MESSAGE；未知 → UNKNOW
                return LocalTransactionState.COMMIT_MESSAGE;
            }

            @Override
            public LocalTransactionState checkLocalTransaction(MessageExt msg) {
                // 回查：查本地订单是否存在 → 存在 COMMIT，不存在 ROLLBACK
                return LocalTransactionState.COMMIT_MESSAGE;
            }
        });
        // 发半消息
        producer.sendMessageInTransaction(new Message("order-topic", body), null);
    }
}
```

```java
// 幂等消费模板（防重复处理）
@Service
public class OrderMsgConsumer {

    @Autowired
    private IdempotentService idempotentService;   // Redis / DB 唯一表

    @RocketMQMessageListener(topic = "ORDER_TOPIC", consumerGroup = "order-consume-group")
    public static class OrderListener implements RocketMQListener<OrderMsg> {
        @Override
        public void onMessage(OrderMsg msg) {
            // 用业务唯一键（如 orderNo）加锁/去重
            if (!idempotentService.tryLock("msg:" + msg.getOrderNo())) {
                return;           // 已处理过，直接跳过
            }
            try {
                process(msg);     // 业务处理
                idempotentService.markDone("msg:" + msg.getOrderNo());
            } finally {
                idempotentService.releaseLock("msg:" + msg.getOrderNo());
            }
            // 处理失败默认抛异常 → 触发 RocketMQ 重试(16次) → 死亡队列
        }
    }
}
```

```java
// 顺序消息发送（按订单号选择同一队列）
MessageQueueSelector selector = new MessageQueueSelector() {
    @Override
    public MessageQueue select(List<MessageQueue> mqs, Message msg, Object arg) {
        long orderId = (Long) arg;                       // 业务键
        int queueIndex = (int) (orderId % mqs.size());   // 哈希到固定队列
        return mqs.get(queueIndex);
    }
};
producer.send(msg, selector, orderId);
```

```yaml
# RocketMQ 关键配置
brokerClusterName: DefaultCluster
brokerName: broker-a
brokerId: 0                                    # 0=master, >0=slave
deleteWhen: 04                                 # 4点删过期消息
fileReservedTime: 72                           # 文件保留72小时
flushDiskType: ASYNC_FLUSH                     # 安全性考虑用 SYNC_FLUSH
autoCreateTopicEnable: false                   # 生产禁自动建
```

## 🎯 面试高频 TOP10

1. **Q: 事务消息原理？** **A:** 半消息(对消费者不可见)→ 本地事务 → COMMIT/ROLLBACK → 超时无结果回查本地事务状态兜底，实现分布式事务最终一致。
2. **Q: 消息幂等方案？** **A:** 业务唯一键(订单号)+DB 唯一约束兜底、Redis SETNX、乐观锁版本号；消费前判空/去重，失败重试不重复入库。
3. **Q: 消息丢失怎么保证？** **A:** 生产：send 同步确认(retry)；存储：SYNC_FLUSH + 主从复制；消费：手动 ACK(commit) + 消费组 offset 持久化；三端配合才不丢。
4. **Q: 消息积压处理？** **A:** 临时扩容临时消费者(批量拉取)、消费端并发拉大、topic 多队列并行、定位是生产暴涨还是消费慢(看监控)后对症处理。
5. **Q: 顺序消息实现？** **A:** 生产者 MessageQueueSelector 按业务键选同一队列 + 消费者单队列串行消费(一个队列一个线程/手动 ACK)；全局顺序只有一个队列，吞吐低。
6. **Q: RocketMQ 和 Kafka 区别？** **A:** RocketMQ 主题丰富(事务/延迟/死信/广播)、Java 生态友好、支持亿级；Kafka 吞吐更高、生态(流处理) 更强；RocketMQ 社区模型工具链更贴近业务系统。
7. **Q: 延迟消息怎么实现？** **A:** 消息带延迟等级(DELAY=3) → Broker 按时间投递到真实队列；**注意** 只有固定 18 个等级(RocketMQ 4)，任意秒级延迟需自己实现(存 DB+扫描投递)。
8. **Q: 消息重试机制？** **A:** 消费异常自动重试(默认 16 次)，间隔按延迟等级递增；重试耗尽进死信队列 %DLQ% 供人工处理；可手动重置 offset 重新消费。
9. **Q: 如何保证消息不重复消费？** **A:** 必须做幂等：唯一约束、状态机、Redis 标记、去重表；MQ 至少一次语义，重复是常态。
10. **Q: 消息队列的作用？** **A:** 解耦(上下游独立)、削峰(缓冲瞬时流量)、异步(非关键路径提速)、广播(多系统订阅同步)、日志/事件流承载。

## ⚠️ 常见坑 & 最佳实践

| ❌ 坑 | ✅ 正确做法 |
|-------|------------|
| 消费失败不抛异常也不 ACK | 监听器抛 Throwable 交由框架处理；捕获业务异常做归属审计 |
| 消息里塞大对象/大文本 | 消息体瘦身，正文存 DB/OSS，消息只带 ID |
| 消费端单队列串行导致慢 | 按业务拆分多个队列/consumerGroup，并行为主 |
| 事务消息事务内做远程调用 | 本地事务只做 DB 操作，回查步骤分离 |
| 盲目把 DB 数据直接同步广播 | 评估一致性和数据量，必要时扩容/延迟补偿 |
| 忘记处理死信队列 | 监控 %DLQ% 增长并告警，配置人工重投机制 |
| 本地事务执行超时导致无法回查 | 控制事务时长，回查接口必须可重入且幂等 |
| 生产环境 topic 无限制创建 | 配置 autoCreateTopicEnable=false，按申请建 topic 并评估分区数 |

## 📐 架构设计要点

- **Topic 设计**：按业务域拆 Topic(订单/支付/库存)，一个 Topic 的队列数 = 预期并发消费度，可在线扩容。
- **消费组隔离**：不同业务系统独立 consumerGroup，互不影响 offset。
- **可靠链路**：同步 send + 主从同步复制 + offset 管理 + 死信审计，环环校验。
- **监控体系**：生产 TPS、积压量、消费延迟、死信队列数、Broker 磁盘/内存，配套 Prometheus+Grafana。
- **优雅停机**：Producer 先停止发送，Consumer 拉完处理完再关闭，避免 offset 漂移。

## 🔗 关联技术

- **Spring Boot**：spring-boot-starter-rocketmq 简化对接，RocketMQMessageListener 注解式消费。
- **Seata/分布式事务**：事务消息是 RocketMQ 生态中最终一致方案的核心组件。
- **Redis**：幂等去重/分布式锁配合消息消费，防重复处理。
- **Kafka**：同赛道对比，选型看场景：RocketMQ 业务消息、Kafka 大数据流式。
- **监控/APM**：SkyWalking 等链路追踪 RocketMQ 消息轨迹。