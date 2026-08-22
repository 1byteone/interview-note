# RocketMQ — 面试抽认卡

> 来源：`learn/08-rocketmq/05-interview/`

---

### Card 1: 事务消息原理
**维度**: 🔬深挖 | **难度**: ⭐⭐⭐

> **Q: RocketMQ 事务消息如何保证分布式事务的最终一致性？**

**A:** 流程：① 发送半消息（Half Message）到 Broker，消息暂不可见（存在 `RMQ_SYS_TRANS_HALF_TOPIC`）；② 执行本地事务；③ 提交/回滚（Commit/Rollback）；④ 若超时未提交，Broker 回调 Producer 的 `checkLocalTransaction()` 查询事务状态，根据状态决定提交或回滚。半消息对消费者不可见，Commit 后消息才投递到原 Topic。对比本地消息表：事务消息 Broker 参与协调，实现更简洁，业务侵入小。

---

### Card 2: 顺序消息保证
**维度**: 🔬深挖 | **难度**: ⭐⭐

> **Q: RocketMQ 如何保证消息的顺序消费？**

**A:** 全局顺序：Topic 只建 1 个 Queue（吞吐极低，不推荐）。分区顺序：按业务 key（如订单号）hash 到固定 Queue，同一 Queue 内串行消费。Producer 端：`MessageQueueSelector` 选择器实现 `select` 方法，用 `orderId % queueNum` 计算队列。Consumer 端：`MessageListenerOrderly` 顺序消费，消费线程在队列粒度加锁，保证同一 Queue 的消息串行处理。注意：顺序消费失败时重试到原队列，乱序可能影响后续消息。

---

### Card 3: 幂等消费方案
**维度**: 🎯场景 | **难度**: ⭐⭐

> **Q: RocketMQ 默认至少一次投递，如何保证幂等消费？**

**A:** ① 唯一键去重（业务流水号做唯一索引，`INSERT IGNORE` 或 `SELECT FOR UPDATE` 判断）；② 版本号（乐观锁，`UPDATE ... WHERE version=old_version`）；③ 状态机（判断业务状态，如"已支付"不可重复支付）；④ Redis 去重（`SETNX msg_id uuid`，已存在则跳过）；⑤ 去重表（建立消息去重流水表，`msg_id` 唯一索引）。推荐方案：业务唯一键（如 order_id）+ 去重表，简单可靠。

---

### Card 4: 消息丢失防护
**维度**: 🎯场景 | **难度**: ⭐⭐⭐

> **Q: RocketMQ 如何防止消息丢失？Producer/Broker/Consumer 三方分别怎么做？**

**A:** Producer：同步发送（`producer.send(msg, timeout)`，等待 Broker 确认），失败重试（默认 2 次）。Broker：同步刷盘（`FlushDiskType.SYNC_FLUSH`，确认写入磁盘后才返回 Ack）+ 主从同步（`SYNC_MASTER`，等待从节点同步完成）。Consumer：消费完业务逻辑后再 Ack（`consumeOrderly` 返回 `ConsumeConcurrentlyStatus.CONSUME_SUCCESS`），避免先 Ack 后处理失败丢失。全链路同步配置下，消息丢失概率极低。

---

### Card 5: 重试机制
**维度**: 📝速记 | **难度**: ⭐⭐

> **Q: RocketMQ 的重试机制是怎样的？重试队列和死信队列的关系？**

**A:** 集群消费模式下，消息消费失败后进入重试队列（`%RETRY%{consumerGroup}`），默认重试 16 次，间隔时间递增（1s → 5s → 10s → ... → 2h）。重试 16 次仍失败进入死信队列（`%DLQ%{consumerGroup}`）。重试队列可被原始消费者组重新消费，死信队列需要手动处理（通常告警后人工介入）。`setMaxReconsumeTimes` 可自定义最大重试次数。

---

### Card 6: 死信队列处理
**维度**: 🎯场景 | **难度**: ⭐⭐

> **Q: 消息进入死信队列后如何处理？**

**A:** ① 告警通知（钉钉/邮件，通知开发人员排查）；② 手动重投（CLI 命令 `mqadmin retryMessage -g {group} -t {topic} -m {msgId}`）；③ 死信队列独立消费（创建额外消费者订阅 `%DLQ%{group}` 处理死信）；④ 分析原因并修复：检查消费逻辑是否有 BUG，消息格式是否异常，依赖服务是否正常。重要：死信队列是"最后一道防线"，不能依赖它，应在上游保证消费质量。

---

### Card 7: 消息积压处理
**维度**: 🎯场景 | **难度**: ⭐⭐⭐

> **Q: RocketMQ 消息积压的原因和解决方案是什么？**

**A:** 原因：消费者处理能力不足（慢 SQL、远程调用超时）、消费者宕机、Queue 数少于消费者数。排查：`mqadmin consumerProgress -g {group}` 查看积压量。解决：① 增加消费者实例（消费者数 ≤ Queue 数）；② 增加 Queue 数（需重建 Topic，或在积压时临时拆分 Topic）；③ 优化消费逻辑（批量处理、异步非阻塞、减少远程调用）；④ 紧急扩容：临时加 Queue + 消费者，积压消化后恢复；⑤ 跳过无关消息：直接 Ack 不重要的消息。

---

### Card 8: 刷盘机制
**维度**: 🔬深挖 | **难度**: ⭐⭐

> **Q: RocketMQ 的同步刷盘和异步刷盘有什么区别？如何选择？**

**A:** 同步刷盘（`SYNC_FLUSH`）：消息写入 PageCache 后立即调 `flush()` 刷到磁盘，确认磁盘写入完成才返回 Ack，可靠性最高，延迟约 1-10ms。异步刷盘（`ASYNC_FLUSH`）：消息写入 PageCache 即返回 Ack，后台线程定时刷盘（默认 500ms），延迟极低（<1ms），但宕机可能丢 PageCache 中未刷盘的数据。选型：关键业务（订单/支付）用同步刷盘，日志/监控用异步刷盘。

---

### Card 9: 主从同步
**维度**: 🔬深挖 | **难度**: ⭐⭐

> **Q: RocketMQ 主从同步的两种模式（SYNC_MASTER 和 ASYNC_MASTER）有什么区别？**

**A:** SYNC_MASTER：主节点等待从节点同步完成后再返回写入成功，保证主从数据一致，主节点宕机后从节点数据完整。ASYNC_MASTER：主节点写入后立即返回，从节点异步拉取，主节点宕机可能丢数据（从节点未同步的消息）。生产中推荐 SYNC_MASTER + 同步刷盘，最高可靠性。HA 机制：从节点主动向主节点发起连接，拉取 CommitLog 增量数据。

---

### Card 10: 负载均衡与 Queue 分配
**维度**: 🔬深挖 | **难度**: ⭐⭐

> **Q: RocketMQ 消费者端的负载均衡策略有哪些？如何分配 Queue？**

**A:** 集群模式下，同一个 ConsumerGroup 内的消费者共同消费 Topic 的 Queue。分配策略：① 平均分配（`AllocateMessageQueueAveragely`）：每个消费者分配 `Queue数/消费者数` 个 Queue，多出的给前几个消费者；② 环形分配（`AllocateMessageQueueAveragelyByCircle`）：轮询分配；③ 一致性哈希（`AllocateMessageQueueConsistentHash`）：按虚拟节点分配，消费者增减影响小。一个 Queue 最多被一个消费者的一个线程消费，消费并发度 = min(线程数, Queue 数)。

---

### Card 11: 消息过滤
**维度**: 📝速记 | **难度**: ⭐⭐

> **Q: RocketMQ 的消息过滤方式有哪些？Tag 过滤和 SQL 过滤的区别？**

**A:** Tag 过滤：Topic 下的二级分类，简单等值匹配，在 Broker 端过滤（基于 Hash 比较），性能高。SQL 过滤：消息属性 SQL92 表达式（如 `a > 5 AND b = 'test'`），支持复杂条件，但需要 Broker 解析表达式，性能略低。Tag 适用于简单分类（如 `TagA`、`TagB`），SQL 过滤适用于复杂条件。消费者订阅时通过 `subExpression` 指定：`consumer.subscribe("TopicTest", "TagA || TagB")`。

---

### Card 12: 批量消息
**维度**: 💻代码 | **难度**: ⭐⭐

> **Q: RocketMQ 批量发送消息有什么限制？如何实现？**

**A:** 批量发送限制：① 每条消息 Topic 相同；② 不能是延迟消息或事务消息；③ 一批消息总大小不超过 1MB（默认）。实现：`producer.send(Collection<Message> messages)`。如果超过 1MB 限制，需拆分发送。批量消息减少网络 RTT，提升吞吐量（批量发送 100 条比单条发送 100 次快 10 倍+）。消费端使用 `MessageListenerConcurrently` 消费，批量消费默认一次拉取 32 条。

---

### Card 13: 延迟消息
**维度**: 📝速记 | **难度**: ⭐⭐

> **Q: RocketMQ 的延迟消息是如何实现的？支持哪些延迟等级？**

**A:** 18 个固定延迟等级：`1s/5s/10s/30s/1m/2m/3m/4m/5m/6m/7m/8m/9m/10m/20m/30m/1h/2h`。`message.setDelayTimeLevel(level)` 设置。实现原理：Broker 将延迟消息写入延迟 Topic（`SCHEDULE_TOPIC_XXXX`），每个延迟等级对应一个队列，定时任务每秒扫描，到期后转投到原 Topic。注意：延迟消息不支持二次修改延迟等级，时间只能精确到秒级。

---

### Card 14: MQ 选型对比
**维度**: 📝速记 | **难度**: ⭐⭐

> **Q: RocketMQ、Kafka、RabbitMQ 的核心差异是什么？如何选型？**

**A:** RocketMQ：事务消息、延迟消息、死信队列、顺序消息、TB 级消息堆积，适合 Java 生态、金融交易。Kafka：超高性能（百万级 TPS）、日志持久化、流式处理（Kafka Streams），适合日志收集、大数据管道、事件溯源。RabbitMQ：轻量级、灵活路由（Exchange + Binding）、管理界面友好，适合中小规模、业务系统集成。选型：需要事务消息→RocketMQ；需要大数据流处理→Kafka；简单消息路由→RabbitMQ。

---

### Card 15: 消息轨迹
**维度**: 📝速记 | **难度**: ⭐⭐

> **Q: RocketMQ 的消息轨迹功能是什么？如何开启？**

**A:** 消息轨迹记录消息的完整生命周期：生产时间、存储 Broker、消费状态、消费时间、重试次数。开启方式：`producer.setEnableMsgTrace(true)` + `consumer.setEnableMsgTrace(true)`，Broker 端 `traceTopicEnable=true`。轨迹数据存储在单独的 Topic（`RMQ_SYS_TRACE_TOPIC`）中，通过 `mqadmin queryMsgTraceById -i {msgId}` 查询。轨迹功能帮助排查消息丢失、重复消费、积压等问题。