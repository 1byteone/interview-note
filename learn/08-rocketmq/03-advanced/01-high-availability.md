# 高可用

> RocketMQ 的高可用方案基于 Dledger 多副本机制，实现 Broker 自动故障转移。
> 本章覆盖：Dledger、主从同步、故障转移、消息轨迹。

---

## 1. 多副本机制（Dledger）

### 1.1 为什么需要高可用

单 Broker 架构存在单点故障风险：

```
Broker 宕机 → 该节点的所有 Topic 不可读写 → 消息无法发送/消费 → 业务中断
```

RocketMQ 4.5+ 引入 **Dledger**（基于 Raft 协议的分布式一致性算法），实现 Broker 集群多副本、自动选主、故障转移。

### 1.2 Dledger 架构

```
┌─────────────────────────────────────────────────────┐
│                   Broker 集群                        │
│                                                     │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐
│  │ Leader       │  │ Follower 1   │  │ Follower 2   │
│  │  可读可写     │  │  只读        │  │  只读        │
│  │  Dledger     │  │  Dledger     │  │  Dledger     │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘
│         │                │                │          │
│         └────────────────┼────────────────┘          │
│                     Raft 协议                        │
│               (选主/日志复制/心跳)                    │
└─────────────────────────────────────────────────────┘
```

### 1.3 Raft 协议核心机制

| 机制 | 说明 |
|------|------|
| **Leader 选举** | 集群中选出一个 Leader，负责处理所有写请求 |
| **日志复制** | Leader 将消息日志复制到所有 Follower，多数派写入后返回成功 |
| **心跳保活** | Leader 定期向 Follower 发心跳，丢失心跳触发重新选举 |
| **多数派原则** | 写请求需要超过半数节点（N/2+1）确认才算成功，保证数据一致性 |

### 1.4 Dledger 部署配置

```properties
# broker.conf
# 开启 Dledger
enableDLegerCommitLog=true
dLegerGroup=rocketmq-broker-group
dLegerPeers=n0-192.168.1.1:40911;n1-192.168.1.2:40911;n2-192.168.1.3:40911
dLegerSelfId=n0

# 数据存储路径
storePathRootDir=/data/rocketmq/store
storePathCommitLog=/data/rocketmq/store/commitlog

# 消息同步方式（Dledger 下自动使用同步复制）
flushDiskType=SYNC_FLUSH
```

### 1.5 最小部署：3 节点 Dledger 集群

```bash
# 三台机器的 Docker 部署方式
# 节点 1（192.168.1.1）
docker run -d --name rmqbroker1 --network host \
  -e "NAMESRV_ADDR=192.168.1.1:9876;192.168.1.2:9876;192.168.1.3:9876" \
  -v /data/rocketmq/broker1/conf/broker.conf:/etc/rocketmq/broker.conf \
  apache/rocketmq:5.2.0 sh mqbroker -c /etc/rocketmq/broker.conf

# 节点 2（192.168.1.2）同理，dLegerSelfId=n1
# 节点 3（192.168.1.3）同理，dLegerSelfId=n2
```

---

## 2. 主从同步（非 Dledger 模式）

如果你的生产环境还使用 4.5 之前的版本或非 Dledger 模式，可以配置主从架构：

### 2.1 主从配置

```properties
# Master 节点 broker.conf
brokerClusterName=DefaultCluster
brokerName=broker-a
brokerId=0              # 0 表示 Master
brokerRole=SYNC_MASTER  # 同步复制（推荐）或 ASYNC_MASTER
flushDiskType=SYNC_FLUSH

# Slave 节点 broker.conf
brokerClusterName=DefaultCluster
brokerName=broker-a
brokerId=1              # >0 表示 Slave
brokerRole=SLAVE
```

### 2.2 主从同步方式对比

| 方式 | 说明 | 可靠性 | 性能 |
|------|------|--------|------|
| **同步复制** | Master 写入后等 Slave 写入成功才返回 | 高 | 低 |
| **异步复制** | Master 写入后立即返回，Slave 异步同步 | 中 | 高 |

### 2.3 主从模式下的故障转移

| 场景 | 行为 | 说明 |
|------|------|------|
| Master 宕机 | Slave 变为只读 | 消费者可以从 Slave 消费，但生产者无法写入 |
| Slave 宕机 | 无影响 | Master 继续服务，消息投递后 Slave 上线自动同步 |
| Dledger 模式 | 自动选主 | 集群自动选举新 Leader，对业务透明 |

> 主从模式需要手动切换（如修改 DNS 或配置中心），而 Dledger 模式自动完成。

---

## 3. 故障转移（Dledger 自动选主）

### 3.1 故障转移流程

```
初始状态：
  Leader (n0) ← 发消息 → Follower (n1) ← 只读
                           Follower (n2) ← 只读

n0 宕机（心跳超时）：
  Follower (n1) 发起选举
  Follower (n2) 投票
  多数派达成 → 新 Leader 产生

恢复后：
  Leader (n1) ← 发消息 → Follower (n2)
                           Follower (n0, 原 Leader 恢复后变为 Follower)
```

### 3.2 故障转移时间

| 环节 | 耗时 | 说明 |
|------|------|------|
| 心跳超时检测 | 1-3s | 默认 1s 心跳，3 次未收到即触发选举 |
| Leader 选举 | 1-2s | Raft 选举，多数派投票即完成 |
| 总耗时 | 2-5s | 消费者会有短暂的消息中断 |

### 3.3 对业务的影响

- **生产者**：选举期间发送失败，客户端重试机制自动重试，选举完成后恢复
- **消费者**：选举期间消费暂停，选举完成后自动恢复，消息不会丢失
- **数据一致性**：Raft 保证多数派节点数据一致，不会出现"脑裂"导致数据丢失

---

## 4. 消息轨迹（Trace）

### 4.1 什么是消息轨迹

消息轨迹记录消息从生产到消费的完整生命周期，是排查问题的利器：

```
消息轨迹包含：
  ├── 生产时间、生产者 IP、发送耗时
  ├── Broker 存储时间
  ├── 消费时间、消费者 IP、消费耗时
  └── 消费状态（成功/失败/重试次数）
```

### 4.2 开启消息轨迹

```yaml
# application.yml
rocketmq:
  producer:
    group: mall-producer
    enable-msg-trace: true          # 开启生产者轨迹
  consumer:
    group: mall-consumer
    enable-msg-trace: true          # 开启消费者轨迹
```

### 4.3 使用控制台查看轨迹

```
1. 打开 RocketMQ Dashboard (http://localhost:8080)
2. 进入 Message → Query by Message ID / Key
3. 查看轨迹图：生产时间线 → 存储节点 → 消费时间线
4. 排查问题：消费耗时异常、重试次数过多、积压等
```

### 4.4 存储拓扑

消息轨迹数据存储在专用 Topic `RMQ_SYS_TRACE_TOPIC` 中，默认 8 个 Queue，支持集群部署。

> 注意：开启轨迹会增加约 5%-10% 的写入开销，但排查问题时价值巨大，生产环境建议开启。

---

## 5. 高可用最佳实践

```yaml
# 生产环境高可用配置清单
组件：
  NameServer: 至少 2 个节点（无状态，可水平扩展）
  Broker:     3 节点 Dledger 集群（自动故障转移）
  Producer:   配置多个 NameServer 地址
  Consumer:   配置集群消费模式，至少 2 个实例

配置：
  syncFlushDisk: true          # 同步刷盘
  enableDLegerCommitLog: true  # 开启 Dledger
  brokerRole: SYNC_MASTER      # 同步复制

运维：
  - 控制台监控 Broker 状态
  - 配置告警：Broker 宕机、消息积压、死信消息
  - 定期演练：手动 Kill Broker 节点，验证自动切换
```

---

## 总结

本章你学会了：

- Dledger 多副本机制与 Raft 协议原理
- 主从同步的两种模式及选择
- Dledger 自动故障转移流程
- 消息轨迹的开启与排查方法
- 高可用最佳实践配置清单

下一步：学习 [性能调优](02-performance-tuning.md)，掌握批量发送、积压排查、监控方案。