# 性能调优

> RocketMQ 在百万级 TPS 场景下仍有优异表现，但需要正确的配置和调优。
> 本章覆盖批量发送、消费端并发配置、消息积压排查、监控告警。

---

## 1. 批量发送与消费

### 1.1 批量发送最优配置

```java
// 1. 批量发送（单批 ≤ 4MB）
List<Message> batch = new ArrayList<>();
for (int i = 0; i < 100; i++) {
    batch.add(new Message("mall-log-topic", logEntry[i].getBytes()));
}
producer.send(batch);

// 2. 压缩后发送（推荐：gzip 压缩，压缩比可达 10:1）
byte[] compressed = compressWithGzip(batchData);
Message msg = new Message("mall-log-topic", compressed);
msg.putUserProperty("compressType", "gzip");
producer.send(msg);
```

### 1.2 批量发送调优参数

| 参数 | 说明 | 建议值 |
|------|------|--------|
| **batchSize** | 单批消息数量 | 建议 20-100 |
| **压缩** | 大消息先压缩 | gzip 压缩，阈值 1KB 以上 |
| **maxMessageSize** | 单条消息大小限制 | 4MB（默认），超过则报错 |
| **Producer 并发** | 生产者线程数 | 建议 2-4 个线程 |

### 1.3 批量消费

```java
// 消费端批量拉取（一次拉取多条消息，减少网络开销）
@RocketMQMessageListener(topic = "mall-log-topic",
    consumerGroup = "mall-log-group",
    consumeMessageBatchMaxSize = 50      // 一次拉取最多 50 条
)
public class LogBatchConsumer implements RocketMQListener<List<LogEntry>> {
    // 注意：批量消费时泛型是 List<LogEntry>
    @Override
    public void onMessage(List<LogEntry> logs) {
        // 批量入库
        logMapper.batchInsert(logs);
    }
}
```

---

## 2. 消费端并发配置

### 2.1 核心参数

| 参数 | 说明 | 默认值 | 建议值 |
|------|------|--------|--------|
| **consumeThreadMin** | 最小消费线程数 | 20 | CPU 核数 * 2 |
| **consumeThreadMax** | 最大消费线程数 | 64 | 视业务处理耗时而定 |
| **pullBatchSize** | 单次拉取消息数 | 32 | 32-64 |
| **consumeMessageBatchMaxSize** | 批量消费最大条数 | 1 | 10-50（批量处理时） |
| **maxReconsumeTimes** | 最大重试次数 | 16 | 5-10 |

### 2.2 配置示例

```yaml
rocketmq:
  consumer:
    group: mall-order-consumer
    # 消费线程数配置
    consume-thread-min: 4
    consume-thread-max: 8
    # 拉取配置
    pull-batch-size: 32
    # 重试次数
    max-reconsume-times: 5
```

### 2.3 消费并发度与 Queue 的关系

```
Topic 有 8 个 Queue
  ├── 消费组有 2 个实例
  │     ├── 实例 1：消费 Queue 0,1,2,3
  │     └── 实例 2：消费 Queue 4,5,6,7
  │
  └── 每个实例配置 4 个消费线程
        ├── 线程 1：Queue 0
        ├── 线程 2：Queue 1
        ├── 线程 3：Queue 2
        └── 线程 4：Queue 3
```

**结论**：消费并发度 = min(消费线程数, Queue 数)。提升消费能力的关键是**增加 Queue 数量**，而不是无限增加线程数。

### 2.4 消费端性能调优清单

| 方案 | 效果 | 说明 |
|------|------|------|
| 增加 Queue 数量 | 提升并行度 | 建议 Queue 数 >= 消费实例数 * 线程数 |
| 增加消费实例数 | 水平扩展 | 最多到 Queue 数量 |
| 批量消费 | 减少网络开销 | 适合日志类、批量写入 |
| 异步处理 | 解耦消费线程 | 消费线程只做消息投递，业务逻辑异步处理 |
| 预创建索引 | 加快 DB 查询 | 根据消息关键字段建立索引 |

---

## 3. 消息积压排查与处理

### 3.1 积压识别

```bash
# 通过控制台查看
1. 打开 RocketMQ Dashboard → Consumer
2. 查看 Diff Total（积压量）= 生产总量 - 消费总量
3. 正常情况 Diff Total 在几百以内
4. 异常情况 Diff Total 持续增长，积压成千上万
```

### 3.2 积压原因分析

| 原因 | 典型表现 | 排查方向 |
|------|----------|----------|
| **消费能力不足** | Diff Total 持续增长 | 检查 Queue 数、消费线程数、实例数 |
| **消费失败重试** | 消费日志报错，重试队列堆积 | 排查消费端异常（DB 慢查询、接口超时） |
| **生产者暴增** | 瞬时流量暴增（如秒杀） | 消费端能否跟上，是否需要限流 |
| **Broker 瓶颈** | 磁盘 IO 100%、CPU 高 | 检查磁盘、网络、内存 |
| **死信积压** | 重试 16 次后进死信，未被处理 | 检查死信队列，人工介入 |

### 3.3 积压处理方案

```java
// 方案 1：临时扩容消费端（适合消费能力不足）
// 增加消费实例，注意 Queue 数必须 >= 实例数
// 如果 Queue 不够，需要先增加 Queue 数量

// 方案 2：跳过不可重试的消息（适合消费失败积压）
@RocketMQMessageListener(topic = "mall-order-topic",
    consumerGroup = "mall-order-group")
public class ConsumerWithSkip implements RocketMQListener<Order> {
    @Override
    public void onMessage(Order order) {
        try {
            orderService.process(order);
        } catch (BizException e) {
            // 业务异常，记录日志后跳过（不触发重试）
            log.warn("业务异常，跳过处理: {}", e.getMessage());
        }
    }
}
```

### 3.4 积压预防

```yaml
# 积压预防体系
一、容量规划：
  - 预估峰值 TPS，预留 2 倍 Buffer
  - 根据 TPS 计算 Queue 数量
  - 消费端实例数 = Queue 数 / 每实例线程数

二、监控告警：
  - 积压量 > 1000 告警（黄色预警）
  - 积压量 > 10000 告警（红色预警，紧急处理）
  - 消费失败率 > 1% 告警

三、限流保护：
  - 生产端：秒杀场景限流，控制入队速率
  - 消费端：消费失败指数退避，避免重复压 DB
```

---

## 4. 监控与告警

### 4.1 关键监控指标

| 指标 | 说明 | 告警阈值 |
|------|------|----------|
| **积压量** | 未消费消息数 | > 1000 告警 |
| **消费 TPS** | 每秒消费消息数 | 低于预期的 50% 告警 |
| **消费失败率** | 失败消息 / 总消息 | > 1% 告警 |
| **生产 TPS** | 每秒生产消息数 | 异常突增告警 |
| **Broker 磁盘使用率** | Broker 存储磁盘 | > 80% 告警 |
| **Broker CPU 使用率** | Broker 进程 CPU | > 80% 告警 |
| **死信队列数量** | 死信消息数 | > 0 告警 |

### 4.2 通过 JMX 获取监控数据

```bash
# 使用 jconsole 或 jmc 连接 Broker 进程
# 关键 MBean：
# - org.apache.rocketmq.common:type=BrokerStats
#   └── getInTPS, getOutTPS, getMsgPutTotalToday, getMsgGetTotalToday
# - org.apache.rocketmq.store:type=StoreStats
#   └── getGetMessageEntireTimeMax, getPutMessageEntireTimeMax
```

### 4.3 整合 Prometheus + Grafana

```yaml
# 使用 rocketmq-exporter 导出指标
# 部署方式：
docker run -d --name rocketmq-exporter -p 5557:5557 \
  -e "ROCKETMQ_NAMESRV_ADDR=192.168.1.1:9876;192.168.1.2:9876" \
  -e "ROCKETMQ_CONFIG_BROKER_TPS_ENABLE=true" \
  apache/rocketmq-exporter:latest

# Prometheus 配置
scrape_configs:
  - job_name: 'rocketmq'
    static_configs:
      - targets: ['localhost:5557']
```

### 4.4 Shell 快速排查命令

```bash
# 1. 查看 Broker 磁盘使用
df -h /data/rocketmq/

# 2. 查看 Broker 日志中的错误
tail -100f ~/logs/rocketmqlogs/broker.log | grep ERROR

# 3. 查看消费进度
# 使用 mqadmin 工具
mqadmin consumerProgress -n localhost:9876 -g mall-order-consumer-group

# 4. 查看 Topic 路由
mqadmin topicRoute -n localhost:9876 -t mall-order-topic
```

---

## 6. 性能调优总结

```yaml
# 终极性能调优清单
生产端：
  - 同步发送：重要业务
  - 异步发送：对延迟敏感的业务
  - 批量发送：日志类
  - 压缩：大消息先压缩

Broker 端：
  - 异步刷盘：性能优先场景
  - 同步刷盘：可靠性优先场景
  - 适当增加 Queue 数量
  - 调整线程池大小

消费端：
  - 消费线程数 = Queue 数 / 实例数
  - 批量消费：IO 密集型场景
  - 异步处理：耗时操作分离
  - 幂等兜底：防止重复消费
```

---

## 总结

本章你学会了：

- 批量发送与消费的最佳实践
- 消费端并发度与 Queue 的关系
- 消息积压的识别、排查、处理方案
- 监控指标体系与告警配置
- 性能调优终极清单

下一步：学习 [MQ 对比](03-mq-compare.md)，了解 RocketMQ vs Kafka vs RabbitMQ 的选型依据。