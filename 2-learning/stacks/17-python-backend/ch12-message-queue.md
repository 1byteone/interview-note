# 第十二章：消息队列（P1 进阶）

> 📖 **参考资料**：[RabbitMQ Tutorial](https://www.rabbitmq.com/tutorials) | [Kafka Python](https://github.com/aio-libs/aiokafka) | [aio-pika](https://aio-pika.readthedocs.io/)

## 12.1 消息队列架构

消息队列（Message Queue）是分布式系统中实现**异步解耦**、**流量削峰**的核心中间件。典型架构如下：

```
┌──────────┐     ┌─────────────────────────┐     ┌──────────┐
│ Producer │────▶│         Broker          │────▶│ Consumer │
│ (生产者)  │     │ ┌─────────┐ ┌─────────┐ │     │ (消费者)  │
│          │     │ │  Queue   │ │ Topic   │ │     │          │
│ 发送消息  │     │ │(RabbitMQ)│ │ (Kafka) │ │     │ 处理消息  │
└──────────┘     │ └─────────┘ └─────────┘ │     └──────────┘
                 │ ┌─────────────────────┐  │
                 │ │   Dead Letter Queue  │  │
                 │ └─────────────────────┘  │
                 └─────────────────────────┘
```

**核心概念对比：**

| 概念 | RabbitMQ | Kafka |
|------|----------|-------|
| 模型 | Queue（队列） | Topic（主题分区） |
| 消息确认 | ACK / NACK / Reject | Offset Commit |
| 持久化 | 消息 + 队列可持久化 | 日志追加，天然持久化 |
| 消费模式 | Push（推模式） | Pull（拉模式） |
| 吞吐量 | 万级 QPS | 百万级 QPS |
| 适用场景 | 业务解耦、任务队列 | 日志收集、事件溯源、大数据 |

## 12.2 RabbitMQ + aio-pika

[aio-pika](https://github.com/mosquito/aio-pika) 是 RabbitMQ 的异步 Python 客户端，基于 `aiormq`。

### 安装

```bash
pip install aio-pika
```

### 生产者

```python
# producer.py
import asyncio
import json
from aio_pika import connect, Message, DeliveryMode


async def publish_order(order_id: int, user: str):
    """发布订单消息到 RabbitMQ"""
    connection = await connect("amqp://guest:guest@localhost/")
    
    async with connection:
        channel = await connection.channel()
        
        # 声明持久化队列
        queue = await channel.declare_queue(
            "order_queue",
            durable=True,
            arguments={
                # 绑定死信交换器
                "x-dead-letter-exchange": "dlx_exchange",
                "x-dead-letter-routing-key": "dead_letter",
            },
        )
        
        body = json.dumps({
            "order_id": order_id,
            "user": user,
            "status": "created",
        })
        
        # 发布持久化消息
        message = Message(
            body=body.encode(),
            delivery_mode=DeliveryMode.PERSISTENT,
            content_type="application/json",
            headers={"x-retry-count": "0"},
        )
        
        await channel.default_exchange.publish(
            message,
            routing_key=queue.name,
        )
        print(f"✅ 已发送订单: {order_id}")


if __name__ == "__main__":
    asyncio.run(publish_order(order_id=1001, user="alice"))
```

### 消费者（含死信处理）

```python
# consumer.py
import asyncio
import json
from aio_pika import connect, ExchangeType


MAX_RETRIES = 3


async def process_order(message):
    """处理单条订单消息"""
    async with message.process():
        body = json.loads(message.body)
        retry = int(message.headers.get("x-retry-count", 0))
        
        print(f"📩 收到订单: {body['order_id']} (重试 #{retry})")
        
        # 模拟处理失败
        if body["order_id"] == 1002 and retry < MAX_RETRIES:
            raise ValueError("订单处理失败，稍后重试")
        
        print(f"✅ 订单 {body['order_id']} 处理完成")


async def dead_letter_handler(message):
    """死信队列处理器"""
    async with message.process():
        body = json.loads(message.body)
        print(f"💀 死信消息: {body}，需人工介入")


async def main():
    connection = await connect("amqp://guest:guest@localhost/")
    
    async with connection:
        channel = await connection.channel()
        await channel.set_qos(prefetch_count=10)
        
        # 1. 声明死信交换器和队列
        dlx_exchange = await channel.declare_exchange(
            "dlx_exchange", ExchangeType.DIRECT, durable=True
        )
        dead_letter_queue = await channel.declare_queue(
            "dead_letter_queue", durable=True
        )
        await dead_letter_queue.bind(dlx_exchange, routing_key="dead_letter")
        await dead_letter_queue.consume(dead_letter_handler)
        
        # 2. 声明业务队列并消费
        order_queue = await channel.declare_queue(
            "order_queue",
            durable=True,
            arguments={
                "x-dead-letter-exchange": "dlx_exchange",
                "x-dead-letter-routing-key": "dead_letter",
            },
        )
        await order_queue.consume(process_order)
        
        print("🚀 Consumer 启动，等待消息...")
        await asyncio.Future()  # 永久运行


if __name__ == "__main__":
    asyncio.run(main())
```

## 12.3 Kafka + aiokafka

[aiokafka](https://github.com/aio-libs/aiokafka) 是 Apache Kafka 的异步 Python 客户端。

### 安装

```bash
pip install aiokafka
```

### 生产者

```python
# kafka_producer.py
import asyncio
import json
from aiokafka import AIOKafkaProducer


async def publish_events():
    producer = AIOKafkaProducer(
        bootstrap_servers="localhost:9092",
        value_serializer=lambda v: json.dumps(v).encode(),
        acks="all",  # 等待所有副本确认
        enable_idempotence=True,  # 开启幂等，防重复
    )
    await producer.start()
    
    try:
        for i in range(5):
            event = {"event_id": i, "type": "order_created", "user": "bob"}
            # 发送到指定分区（按 user 哈希，保证同一用户消息有序）
            partition = i % 3
            await producer.send_and_wait(
                topic="order_events",
                value=event,
                key=f"user_{i}".encode(),
                partition=partition,
            )
            print(f"✅ 事件 {i} 已发送到分区 {partition}")
    finally:
        await producer.stop()
```

### 消费者（手动提交 Offset）

```python
# kafka_consumer.py
import asyncio
import json
from aiokafka import AIOKafkaConsumer


async def consume_events():
    consumer = AIOKafkaConsumer(
        "order_events",
        bootstrap_servers="localhost:9092",
        group_id="order-service",
        auto_offset_reset="earliest",       # 从最早消息开始
        enable_auto_commit=False,           # 手动提交 offset
        consumer_timeout_ms=5000,           # 5秒无消息则退出轮询
    )
    await consumer.start()
    
    try:
        async for msg in consumer:
            event = json.loads(msg.value)
            print(
                f"📩 分区={msg.partition} 偏移={msg.offset} "
                f"键={msg.key} 数据={event}"
            )
            # 处理成功后手动提交 offset
            await consumer.commit()
            print(f"✅ 已提交 offset {msg.offset + 1}")
    finally:
        await consumer.stop()


if __name__ == "__main__":
    asyncio.run(consume_events())
```

## 12.4 消息可靠性：三种语义

| 语义 | 含义 | 实现方式 | 适用场景 |
|------|------|----------|----------|
| **At-Most-Once** | 消息最多投递一次，可能丢失 | 发送即忘 / 自动提交 offset | 日志采集、监控指标 |
| **At-Least-Once** | 消息至少投递一次，可能重复 | 手动 ACK + 重试机制 | 订单处理、支付通知 |
| **Exactly-Once** | 消息恰好投递一次，不丢不重 | 幂等消费 + 事务消息 + 唯一 ID 去重 | 资金转账、库存扣减 |

> ⚠️ **生产建议**：绝大多数场景选择 **At-Least-Once + 幂等消费**。通过业务唯一 ID（如 `order_id`）在消费端做去重表校验，兼顾可靠性与性能。

**幂等消费示例（Redis 方案）：**

```python
import redis.asyncio as redis

async def idempotent_consume(message_id: str, handler, redis_client: redis.Redis):
    """幂等消费：利用 Redis SETNX 去重"""
    lock_key = f"msg:consumed:{message_id}"
    # SETNX + 24h 过期
    acquired = await redis_client.set(lock_key, "1", ex=86400, nx=True)
    if not acquired:
        print(f"⏭️ 消息 {message_id} 已处理，跳过")
        return
    try:
        await handler()
    except Exception:
        # 处理失败，删除锁以便重试
        await redis_client.delete(lock_key)
        raise
```

## 12.5 死信队列（DLQ）

死信队列用于存放**无法正常消费**的消息。常见触发条件：

| 死信原因 | RabbitMQ 配置 | Kafka 策略 |
|----------|---------------|------------|
| 消费超时 | `x-message-ttl` | `max.poll.interval.ms` |
| 重试耗尽 | `x-dead-letter-exchange` | 自定义 Consumer 转发 |
| 消息过大 | `x-max-length` | `max.bytes` 限制 |
| 拒绝消费 | `reject(nack, requeue=False)` | Seek 回退 + 重试计数 |

**DLQ 处理流程：**

```
正常队列 order_queue
    │
    ├── 重试 3 次成功 ──▶ 处理完成 ✅
    │
    └── 重试 3 次失败 ──▶ 死信队列 dead_letter_queue 💀
                              │
                              ├── 告警通知（邮件/钉钉）
                              ├── 写入数据库待处理表
                              └── 人工排查修复
```

## 必读资源

| 资源 | 链接 | 说明 |
|------|------|------|
| RabbitMQ 官方教程 | https://www.rabbitmq.com/tutorials | 七篇经典教程，从 Hello World 到发布确认 |
| aio-pika 文档 | https://aio-pika.readthedocs.io/ | 异步 RabbitMQ 客户端完整 API |
| aiokafka GitHub | https://github.com/aio-libs/aiokafka | Kafka 异步客户端，支持精确一次语义 |
| Apache Kafka 文档 | https://kafka.apache.org/documentation/ | Kafka 核心概念与配置参考 |
| 《RabbitMQ 实战指南》 | — | 朱忠华 著，深入 AMQP 协议与集群运维 |
| 消息队列选型对比 | https://www.confluent.io/kafka-vs-rabbitmq/ | Confluent 官方对比分析 |
