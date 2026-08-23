# RocketMQ 快速入门

> 面向 Java 后端开发者的 RocketMQ 入门指南，目标是让你能启动 RocketMQ、发送并消费第一条消息。

---

## 1. 什么是 RocketMQ

RocketMQ 是阿里巴巴开源的一款高性能、低延迟的分布式消息中间件，核心设计目标是 **削峰填谷** 和 **异步解耦**。它基于 Java 编写，天然适合 Java 生态，支持事务消息、顺序消息、延迟消息等高级特性。

RocketMQ 的核心优势：

| 特性 | 说明 |
|------|------|
| 高吞吐 | 单 Broker 可支撑十万级 TPS |
| 低延迟 | 毫秒级消息投递 |
| 消息可靠性 | 同步刷盘 + 主从复制，保证不丢消息 |
| 事务支持 | 分布式事务消息（2PC + 回查） |
| 顺序保证 | 分区内严格有序 |
| 亿级堆积 | 基于顺序写文件，堆积能力强 |

---

## 2. 安装（Docker）

### 2.1 使用 Docker 快速部署单机版

```bash
# 创建网络
docker network create rocketmq-net

# 启动 NameServer（路由注册中心）
docker run -d --name rmqnamesrv --network rocketmq-net -p 9876:9876 \
  apache/rocketmq:5.2.0 sh mqnamesrv

# 启动 Broker（消息存储与转发）
docker run -d --name rmqbroker --network rocketmq-net -p 10911:10911 -p 10909:10909 \
  -e "NAMESRV_ADDR=rmqnamesrv:9876" \
  apache/rocketmq:5.2.0 sh mqbroker \
  -n rmqnamesrv:9876 \
  -c /home/rocketmq/rocketmq-5.2.0/conf/broker.conf \
  --enable-proxy

# 启动控制台（可视化查看 Topic、消费进度）
docker run -d --name rmqdashboard --network rocketmq-net -p 8080:8080 \
  -e "JAVA_OPTS=-Drocketmq.namesrv.addr=rmqnamesrv:9876" \
  apacherocketmq/rocketmq-dashboard:latest
```

> 注意：5.x 版本使用了 gRPC 代理，客户端可以基于 `rocketmq-client-java`（gRPC 协议）或兼容 4.x 的 `rocketmq-client`（Remoting 协议）接入。

验证安装：

```bash
# 查看 NameServer 是否启动
docker logs rmqnamesrv | tail -5

# 打开控制台 http://localhost:8080，查看 Broker 状态为在线
```

---

## 3. 核心概念

### 3.1 RocketMQ 架构

```
┌─────────────┐  注册/发现   ┌──────────────────┐
│  Producer   │─────────────►│   NameServer     │
│  (生产者)    │              │  (路由注册中心)   │
└─────────────┘              └────────▲─────────┘
       │                             │
       │ 发送消息                      │ 路由
       ▼                             │
┌─────────────┐         ┌────────────┴─────────┐
│   Broker    │◄────────┤      Consumer        │
│ (存储与转发) │────────►│      (消费者)        │
└─────────────┘         └──────────────────────┘
```

### 3.2 核心组件

| 组件 | 作用 | 类比 |
|------|------|------|
| **Producer** | 消息生产者，负责发送消息 | 寄信人 |
| **Consumer** | 消息消费者，负责消费消息 | 收信人 |
| **Broker** | 消息存储与转发，负责接收、存储、投递消息 | 邮局/中转站 |
| **NameServer** | 路由注册中心，维护 Broker 的地址信息，Producer/Consumer 通过它找到 Broker | 电话簿 |
| **Topic** | 消息主题，一类消息的逻辑分类 | 信箱的地址 |
| **Queue** | 消息队列（分区），Topic 下的物理单元，**消息有序性的保证单位** | 信箱里的格子 |
| **Tag** | 消息标签，Topic 内的二级分类，用于精细化过滤 | 信件上的标签 |

### 3.3 关系总结

```
Topic（主题）
  └── Queue 0 ──► 存储消息 A1, A4, A7
  └── Queue 1 ──► 存储消息 A2, A5, A8
  └── Queue 2 ──► 存储消息 A3, A6, A9

一条消息只能属于一个 Queue；一个 Topic 可以包含多个 Queue；
Consumer 的并发度由 Queue 数量决定（一个 Queue 最多被一个消费者线程消费，
但一个消费者可以消费多个 Queue）。
```

---

## 4. 添加依赖

Maven 依赖（选择一个即可）：

```xml
<!-- RocketMQ 4.x 经典客户端（Remoting 协议） -->
<dependency>
    <groupId>org.apache.rocketmq</groupId>
    <artifactId>rocketmq-client</artifactId>
    <version>4.9.7</version>
</dependency>

<!-- RocketMQ Spring Boot Starter（推荐） -->
<dependency>
    <groupId>org.apache.rocketmq</groupId>
    <artifactId>rocketmq-spring-boot-starter</artifactId>
    <version>2.3.0</version>
</dependency>
```

Spring Boot 配置：

```yaml
rocketmq:
  name-server: 127.0.0.1:9876
  producer:
    group: mall-order-producer-group
    send-message-timeout: 3000
```

---

## 5. 消息发送方式

### 5.1 三种发送方式对比

| 方式 | 可靠性 | 性能 | 使用场景 |
|------|--------|------|----------|
| **同步发送** | 最高，Broker 返回结果 | 低 | 关键业务（订单、支付）、需确认送成功 |
| **异步发送** | 高，回调确认 | 中 | 对延迟敏感的业务 |
| **单向发送** | 最低，不关心结果 | 最高 | 日志上报、不重要的采集数据 |

### 5.2 同步发送

```java
@Resource
private RocketMQTemplate rocketMQTemplate;   // Spring Boot Starter 方式

// 同步发送：Broker 确认后才返回
SendResult result = rocketMQTemplate.syncSend(
    "mall-order-topic:order-created",        // topic:tag
    order,                                   // 消息内容（对象会自动序列化）
    3000                                     // 超时时间 ms
);
if (result.getSendStatus() == SendStatus.SEND_OK) {
    log.info("订单消息发送成功, msgId={}", result.getMsgId());
}
```

### 5.3 异步与单向发送

```java
// 异步发送：不阻塞主线程，通过回调获取结果
rocketMQTemplate.asyncSend("mall-log-topic", logEvent, new SendCallback() {
    @Override
    public void onSuccess(SendResult result) {
        log.info("异步发送成功, msgId={}", result.getMsgId());
    }
    @Override
    public void onException(Throwable e) {
        log.error("异步发送失败", e);
        // 这里需要做补偿：落库失败表或重试
    }
});

// 单向发送：只发不管，性能最高
rocketMQTemplate.sendOneWay("mall-monitor-topic", metricData);
```

---

## 6. 消费消息

### 6.1 两种消费模式

| 模式 | 说明 | 场景 |
|------|------|------|
| **集群消费（Clustering）** | 同一条消息只被消费组内**一个**实例消费（负载均衡） | 默认模式，业务解耦、削峰 |
| **广播消费（Broadcasting）** | 每条消息被消费组内**所有**实例消费 | 数据同步、配置更新 |

```yaml
rocketmq:
  # application.yml 中配置
  consumer:
    group: mall-order-consumer-group
    # 广播模式设置为 BROADCASTING，默认 CLUSTERING
```

### 6.2 集群消费（Spring 注解方式）

```java
@Service
@RocketMQMessageListener(
    topic = "mall-order-topic",
    consumerGroup = "mall-order-consumer-group",
    selectorExpression = "order-created"      // 只消费 Tag 为 order-created 的消息
)
@Slf4j
public class OrderCreatedConsumer implements RocketMQListener<Order> {

    @Override
    public void onMessage(Order order) {
        log.info("接收到订单消息: {}", order.getOrderId());
        // 业务处理：异步通知库存扣减、发送短信、更新推荐数据等
    }
}
```

### 6.3 核心消费语义

- **消费进度（Offset）**：每个消费组独立记录消费位点；消费成功后提交，失败则重试。
- **消息去重**：RocketMQ 保证"至少一次投递"（At-Least-Once），因此**消费端必须幂等**。
- **消费失败重试**：默认重试 16 次，间隔递增（1s、5s、10s... 2 小时），超过后进死信队列。

---

## 7. 最小案例：订单创建发送消息

### 场景

用户下单成功后，订单服务发送一条"订单已创建"消息，库存服务异步扣减库存、短信服务异步发通知。

### 7.1 订单服务（生产者）

```java
@Service
public class OrderService {

    @Resource
    private RocketMQTemplate rocketMQTemplate;

    public Order createOrder(OrderCreateDTO dto) {
        // 1. 本地事务：写入订单表（此处简化）
        Order order = new Order();
        order.setOrderId("ORD" + System.currentTimeMillis());
        order.setUserId(dto.getUserId());
        order.setStatus(OrderStatus.CREATED);
        orderMapper.insert(order);

        // 2. 发送消息：topic = mall-order-topic，tag = order-created
        SendResult result = rocketMQTemplate.syncSend(
            "mall-order-topic:order-created", order, 3000);
        if (result.getSendStatus() != SendStatus.SEND_OK) {
            // 3. 失败补偿：写本地消息表或重试表，定时任务补偿发送
            saveMessageToCompensationTable(order, result);
        }
        return order;
    }
}
```

> ⚠ 注意：上面的例子"先写库再发消息"存在消息与库不一致窗口，严格方案用 **事务消息**（见 02-core/01）或 **本地消息表**（见 02-core/02）。

### 7.2 库存服务（消费者）

```java
@Service
@RocketMQMessageListener(
    topic = "mall-order-topic",
    consumerGroup = "mall-stock-consumer-group",
    selectorExpression = "order-created"
)
@Slf4j
public class StockConsumer implements RocketMQListener<Order> {

    @Resource
    private StockService stockService;

    @Override
    public void onMessage(Order order) {
        // 1. 幂等校验：防止重复消费
        if (stockService.isProcessed(order.getOrderId())) {
            return;
        }
        // 2. 扣减库存
        stockService.deductStock(order.getOrderId(), order.getSkuId());
        // 3. 记录处理流水（幂等表）
        stockService.markProcessed(order.getOrderId());
    }
}
```

### 7.3 运行效果

```
用户下单 ──► OrderService.createOrder()
                │
                ▼ (发送消息)
         ┌─ mall-order-topic ─┐
         │ Queue0 │ Queue1 │ Queue2 │
         └────────────────────┘
                │ (消费)
                ▼
         StockConsumer 扣减库存     SmsConsumer 发通知
```

---

## 总结

本章你学会了：

- RocketMQ 架构与核心概念（Producer/Consumer/Broker/NameServer/Topic/Queue/Tag）
- 使用 Docker 一键部署单机版 RocketMQ
- 三种消息发送方式（同步/异步/单向）及选型
- 集群消费与广播消费的区别
- 完整的订单创建 + 消息异步处理最小案例

下一步：学习 [消息类型详解](02-message-types.md)，掌握顺序、延迟、批量、过滤消息。