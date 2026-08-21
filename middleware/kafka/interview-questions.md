# Kafka 面试题大全

## 📚 知识体系

```
Kafka 核心概念
├── Producer / Consumer / Broker
├── Topic / Partition / Offset
├── Partitioner（分区策略）
├── ISR（In-Sync Replica）
├── Leader / Follower
├── Consumer Group
├── Rebalance（重平衡）
└── Ack（确认机制）

Kafka 存储与性能
├── 顺序写磁盘
├── 页缓存（Page Cache）
├── 零拷贝（sendfile）
├── 稀疏索引
├── 消息压缩（LZ4/ZSTD）
└── 批量（batch）

Kafka 可靠性
├── 幂等性（Idempotence）
├── 事务（Transactions）
├── 副本机制
├── 消息不丢失
├── 消息不重复
└── 顺序保证
```

---

## 🎯 Level 1：基础题

### 1. Kafka 是什么？核心组件有哪些？
**答案**：
Kafka 是分布式、分区、多副本、多订阅者的**高吞吐量消息队列**。

**核心组件**：
| 组件 | 作用 |
|------|------|
| **Producer** | 消息生产者 |
| **Consumer** | 消息消费者 |
| **Broker** | 消息服务器（集群节点） |
| **Topic** | 消息主题（逻辑分类） |
| **Partition** | 分区（物理存储，并行消费） |
| **ZooKeeper/KRaft** | 集群协调（新版用 KRaft 替代） |

### 2. Kafka 为什么吞吐量高？
**答案**：

| 技术 | 原理 |
|------|------|
| **顺序写磁盘** | 磁盘顺序写 > 随机写（内存级） |
| **页缓存** | 依赖 OS Page Cache，读写命中内存 |
| **零拷贝** | sendfile 直接从内核发到网卡 |
| **批量处理** | 批量发送（batch.size）+ 批量消费 |
| **消息压缩** | LZ4/ZSTD 压缩减少 IO |
| **稀疏索引** | 日志按 Offset 索引，快速定位 |
| **分区并行** | 多 Partition 并行读写 |

---

## 🎯 Level 2：进阶题

### 3. 消费者组 (Consumer Group) 机制？
**答案**：
Consumer Group 是**多个消费者协作消费同一 Topic** 的机制，保证消息总被组内**一个**消费者消费。

**分区分配策略**：
| 策略 | 说明 |
|------|------|
| Range（范围） | 按 Topic 顺序切分（默认） |
| RoundRobin（轮询） | 轮流分配 |
| Sticky（粘性） | 尽量保持上次分配 |

**触发 Rebalance 的场景**：
- 消费者加入/退出
- Topic 分区数变化
- 消费组成员变化（心跳超时）

**消费模式**：
- **集群模式**：多个消费组各自消费全量消息
- **单播**：一组内只有 1 个消费者消费
- **广播**：每个消费者独立消费全量

### 4. 消息确认机制 (Ack)？
**答案**：

**生产端 Ack**：
| 级别 | 含义 | 可靠性 |
|------|------|--------|
| ack=0 | 不等确认 | 可能丢 |
| ack=1 | Leader 写入 | 主挂掉丢 |
| ack=-1/all | 所有 ISR 写入 | 最可靠 |

```java
properties.put("acks", "all");  // 生产推荐 ack=all
```

**消费端手动提交**：
```java
// 处理完业务后再提交
consumer.commitSync();  // 同步提交（推荐）
consumer.commitAsync(); // 异步提交（配合重试）
```

---

## 🎯 Level 3：高级题

### 5. 如何保证消息不丢失？
**答案**：

**生产端**：
1. `acks=all`（等所有副本写入）
2. 开启重试 + `retries` 配置
3. 开启幂等（`enable.idempotence=true`）

**Broker 端**：
1. `replication.factor >= 2`（至少 2 副本）
2. `min.insync.replicas = 2`（写入需 2 个副本同步）
3. 关闭 unclean 选举（`unclean.leader.election.enable=false`）

**消费端**：
1. 处理完业务再提交 offset
2. 关闭自动提交 `enable.auto.commit=false`

### 6. 如何保证消息不重复消费？
**答案**：
Kafka 的**至少一次**（At Least Once）语义天然可能重复，消费端必须**幂等**。

**幂等方案**：
```java
// 方案一：数据库唯一约束
public void consume(ConsumerRecord<String, String> record) {
    String msgId = record.key();  // 消息唯一 ID
    // 利用唯一索引防重
    orderMapper.insertWithUniqueKey(msgId, record.value());
    // 重复插入抛 DuplicateKeyException → 视为已消费
}

// 方案二：Redis 去重
Boolean first = redis.setIfAbsent("kafka:msg:" + record.offset(), "1", 60);
if (first) {
    process(record.value());  // 只处理一次
}

// 方案三：状态机
// 只处理特定状态 → 已处理则跳过
```

### 7. 如何保证消息的顺序性？
**答案**：
**Kafka 保证分区内有序**（单分区内顺序写）。

**方案**：
1. **单分区**：整个 Topic 有序（损失并行度）
2. **按 Key 路由**：同一 Key 进同一分区

```java
// 同一订单号的订单事件进入同一分区
ProducerRecord<String, String> record = new ProducerRecord<>(
    "order-events",
    orderId,      // key 相同 → 同一分区
    messageBody
);
```

**严格顺序注意**：
- 分区内：顺序写保证
- 重试可能乱序 → 开幂等 + `max.in.flight.requests.per.connection=1`（或开幂等时可 5）
- 消费端避免并行消费同一分区

---

## 🎯 Level 4：专家题

### 8. 消息积压如何解决？
**答案**：

**方案一：临时扩容消费者**
```text
积压 Topic: 3 分区 / 3 消费者
扩容：3 分区 / 10 消费者  →  多余的消费者闲置
```
- 需增加分区数（`Kafka 分区只能增不能减`）

**方案二：跳过非关键逻辑 + 异步落库**
```java
// 优先消费积压，业务逻辑异步处理
listener.onMessage(msg -> {
    // 只做幂等写入临时表
    tempTable.insert(msg);
    // 异步慢慢处理
});
```

**方案三：补偿 + 死信**
- 消费失败退避重试 N 次
- 超过次数进死信 Topic（单独处理）

### 9. Kafka 和 RocketMQ 的选型？
**答案**：

| 场景 | 推荐 | 理由 |
|------|------|------|
| 大数据/日志采集 | Kafka | 高吞吐、生态好 |
| 业务消息（订单/支付） | RocketMQ | 事务消息、延迟消息 |
| 需要顺序消息 | R 只保证分区内顺序 | M 支持全局顺序 |
| 需要定时/延迟消息 | RocketMQ | 原生支持 |
| 阿里生态（Spring Cloud Alibaba） | RocketMQ | 集成方便 |
| 亿万级吞吐 | Kafka | 性能强 |

---

## 📖 学习资源

### 推荐项目
- [Kafka 官方文档](https://kafka.apache.org/documentation/)
- [Kafka 学习视频/文章（leofee-labs/kafka-combat）](https://github.com/leofee-labs/kafka)
- [JavaGuide Kafka 部分](https://javaguide.cn/high-performance/message-queue/kafka-questions-01.html)

### 最佳实践
1. 生产开启幂等 + acks=all
2. 消费端手动提交 + 幂等设计
3. 分区数设置合理（与并行度匹配）
4. 监控消费者 lag
5. 禁止用 Kafka 传递大量小消息