# 深挖题：核心原理源码级解析

> 面试深挖题，覆盖消息存储设计、刷盘机制、消费队列负载均衡、事务消息源码。
> 适合面试官追问"底层原理"时回答。

---

## 1. 消息存储设计

### 1.1 存储文件结构

RocketMQ 的存储设计极具特色，文件结构如下：

```
${storeRoot}/
├── commitlog/             # 消息存储文件（顺序写）
│   ├── 00000000000000000000
│   ├── 00000000000001073741824
│   └── ...
├── consumequeue/          # 消费队列索引文件
│   └── ${topic}/
│       └── ${queueId}/
│           ├── 00000000000000000000
│           └── ...
├── index/                 # 索引文件（按 Key 查询）
│   └── 20260822100000000
├── config/                # 配置信息
│   ├── topics.json
│   ├── consumerOffset.json
│   └── subscriptionGroup.json
└── abort                  # 异常退出标记文件
```

### 1.2 核心设计思想

**CommitLog + ConsumeQueue 双写机制**：

```
Producer 发送消息
    │
    ▼
CommitLog（顺序写，单个文件，所有 Topic 共享）
    │
    ├── 异步构建 ConsumeQueue（"逻辑队列"）
    │     └── 每个 ConsumeQueue 条目固定 20 字节：
    │         commitLogOffset(8B) + msgSize(4B) + tagsCode(8B)
    │
    └── 异步构建 IndexFile（按 Key 查询）
```

**为什么这样设计？**

| 设计 | 优势 |
|------|------|
| 所有消息写入同一个 CommitLog | 顺序写，磁盘性能最高，无随机写 |
| ConsumeQueue 只存偏移量 | 每个条目仅 20 字节，体积小，可常驻 PageCache |
| CommitLog 和 ConsumeQueue 分离 | 读写分离，存储和消费不互相影响 |

### 1.3 消息存储流程

```
Message → CommitLog.putMessage()
  │
  ├── 1. 写入 ByteBuffer（堆外内存）
  ├── 2. 写入 FileChannel（PageCache）
  ├── 3. 同步刷盘（若配置 SYNC_FLUSH，fsync 到磁盘）
  └── 4. 返回写入位置（offset）
```

### 1.4 消息消费流程

```
Consumer 拉取消息
  │
  ├── 1. 从 ConsumeQueue 读取 commitLogOffset
  ├── 2. 从 CommitLog 的 offset 位置读取消息
  └── 3. 返回给消费者
```

---

## 2. 刷盘机制详解

### 2.1 同步刷盘（SYNC_FLUSH）

```
Producer 发送消息
    │
    ▼
写入 PageCache（内存映射）
    │
    ▼
调用 GroupCommitService.submitFlushRequest()
    │
    ▼
等待 GroupCommitService 刷盘完成（MappedByteBuffer.force()）
    │
    ▼
返回 SEND_OK 给 Producer
```

**关键代码**（简化）：

```java
// CommitLog.java
public CompletableFuture<PutMessageStatus> asyncPutMessage(MessageExtBrokerInner msg) {
    // 1. 写入内存映射文件
    MappedFile mappedFile = this.mappedFileQueue.getLastMappedFile();
    AppendMessageResult result = mappedFile.appendMessage(msg, this.appendMessageCallback);
    
    // 2. 同步刷盘
    if (FlushDiskType.SYNC_FLUSH == this.flushDiskType) {
        FlushResult flushResult = this.flushConsumeQueueService.flush();
        this.groupCommitService.submitFlushRequest(mappedFile);
        this.groupCommitService.waitForFlush(mappedFile);  // 阻塞等待
    }
    return CompletableFuture.completedFuture(PutMessageStatus.PUT_OK);
}
```

### 2.2 异步刷盘（ASYNC_FLUSH）

```
Producer 发送消息
    │
    ▼
写入 PageCache
    │
    ▼
立即返回 SEND_OK（不等待刷盘）
    │
    ▼
后台线程 FlushConsumeQueueService 定时刷盘（默认 500ms 一次）
```

### 2.3 刷盘性能对比

| 配置 | TPS | 延迟 | 可靠性 |
|------|-----|------|--------|
| 异步刷盘 + 异步复制 | ~10万 TPS | 1-2ms | 低（宕机可能丢 PageCache 数据） |
| 同步刷盘 + 异步复制 | ~5万 TPS | 5-10ms | 中 |
| 同步刷盘 + 同步复制 | ~2万 TPS | 10-20ms | 高 |

---

## 3. 消费队列负载均衡

### 3.1 负载均衡时机

Rebalance 触发时机：

1. 消费者实例启动/关闭
2. Topic 的 Queue 数量变更
3. 消费者心跳超时（默认 30s）
4. 主动调用 `Consumer.rebalance()` 接口

### 3.2 负载均衡算法：AllocateMessageQueueStrategy

RocketMQ 提供 6 种分配策略：

| 策略 | 说明 | 特点 |
|------|------|------|
| **AllocateMessageQueueAveragely** | 平均分配（默认） | 最均衡，推荐 |
| AllocateMessageQueueAveragelyByCircle | 轮询分配 | 适合 Queue 数少，实例多 |
| AllocateMessageQueueByConfig | 手动配置 | 不灵活 |
| AllocateMessageQueueByMachineRoom | 按机房 | 多机房场景 |
| AllocateMessageQueueConsistentHash | 一致性哈希 | 增减节点影响小 |
| AllocateMachineRoomNearby | 就近机房 | 同机房优先 |

### 3.3 平均分配算法（默认）

```java
// 示例：8 个 Queue 分配给 3 个消费者
Queue索引:  0  1  2  3  4  5  6  7
实例 C1:    [0, 1, 2]          → 3 个 Queue
实例 C2:    [3, 4, 5]          → 3 个 Queue
实例 C3:    [6, 7]             → 2 个 Queue
```

### 3.4 Rebalance 的问题

| 问题 | 说明 | 解决方案 |
|------|------|----------|
| **Consumer 抖动** | 实例频繁上下线导致 Rebalance 频繁触发 | 使用 `ConsumerGroup` 固定实例数 |
| **消息重复** | Rebalance 期间未提交的 offset 导致重复消费 | 消费端幂等 |
| **暂停消费** | Rebalance 期间暂停消费 | 优化 Rebalance 时间 |

---

## 4. 事务消息源码分析

### 4.1 半消息存储

```java
// TransactionalMessageBridge.java
public PutMessageResult putHalfMessage(MessageExtBrokerInner msgInner) {
    // 1. 将原始 Topic 备份到消息属性中
    msgInner.setProperty(
        MessageConst.PROPERTY_REAL_TOPIC,
        msgInner.getTopic()
    );
    msgInner.setProperty(
        MessageConst.PROPERTY_REAL_QUEUE_ID,
        String.valueOf(msgInner.getQueueId())
    );
    
    // 2. 将 Topic 修改为半消息 Topic
    msgInner.setTopic(TransactionalMessageUtil.buildHalfTopic());
    msgInner.setQueueId(0);
    
    // 3. 写入 CommitLog（此时消息不可见）
    return this.brokerController.getMessageStore().putMessage(msgInner);
}
```

### 4.2 半消息提交

```
Producer 发送 COMMIT 请求
    │
    ▼
Broker 收到 COMMIT 请求
    │
    ├── 1. 从半消息 Topic 中读取原始消息
    ├── 2. 将消息 Topic 恢复为真实 Topic
    ├── 3. 重新写入 CommitLog（此时消息可见）
    └── 4. 删除半消息
```

### 4.3 事务回查机制

```java
// TransactionalMessageCheckService.java —— 定时任务
public void run() {
    while (!this.isStopped()) {
        try {
            Thread.sleep(60000);  // 每 60 秒扫描一次
            this.check();         // 扫描未提交的半消息
        } catch (Exception e) {
            log.error("事务回查异常", e);
        }
    }
}

private void check() {
    // 1. 查询所有未提交的半消息
    List<Long> halfMessages = getHalfMessages();
    for (Long offset : halfMessages) {
        // 2. 检查是否超过检查阈值（默认 6 次）
        if (getCheckCount(msg) > MAX_CHECK_COUNT) {
            // 超过阈值 → 强制回滚
            rollbackMessage(msg);
            continue;
        }
        // 3. 发送回查请求到 Producer
        RequestHeader header = new CheckTransactionStateRequestHeader();
        header.setCommitLogOffset(offset);
        this.brokerController.getBroker2Client()
            .checkProducerTransaction(producerGroup, msg, header);
    }
}
```

### 4.4 回查常见问题

| 问题 | 原因 | 解决方案 |
|------|------|----------|
| 回查超时 | Producer 网络延迟或处理慢 | 回查操作要轻量，避免复杂计算 |
| 回查不幂等 | 回查多次调用，每次状态不同 | 回查逻辑必须幂等，查询数据库唯一状态 |
| 回查异常 | Producer 宕机导致回查失败 | 多次回查，超过阈值强制回滚 |

---

## 5. 消息过滤机制

### 5.1 Tag 过滤（Broker 端）

```
Broker 收到消费请求
    │
    ├── 1. 从 ConsumeQueue 读取条目
    ├── 2. 比对 tagsCode（哈希值，8 字节）
    ├── 3. 匹配则返回消息，不匹配则跳过
    └── 4. 当 tagsCode 为 0 时表示"*"，匹配所有
```

### 5.2 SQL 过滤（Broker 端）

```
Broker 收到消费请求
    │
    ├── 1. 解析 SQL92 表达式（如 "amount > 200 AND payType = 'wechat'"）
    ├── 2. 构建表达式树
    ├── 3. 从 CommitLog 读取消息属性
    ├── 4. 执行表达式求值
    └── 5. 返回匹配的消息
```

---

## 总结

深挖知识点汇总：

| 知识点 | 面试深度 | 一句话回答 |
|--------|----------|------------|
| 存储设计 | 深 | 一个 CommitLog 顺序写所有消息 + ConsumeQueue 索引 |
| 刷盘机制 | 中 | 同步刷盘 fsync 保证不丢，异步刷盘性能高 |
| 负载均衡 | 中 | 平均分配策略，Rebalance 可能导致重复消费 |
| 事务消息源码 | 深 | 半消息存 `RMQ_SYS_TRANS_HALF_TOPIC`，回查每 60s 扫描 |
| 过滤机制 | 中 | Tag 过滤用哈希，SQL 过滤用表达式树 |