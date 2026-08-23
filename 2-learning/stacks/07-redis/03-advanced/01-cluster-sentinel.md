# Redis 高可用 — 主从复制 · 哨兵 · Cluster 集群

> 等级：🎯 面试进阶
> 目标：理解 Redis 主从复制、哨兵模式的自动故障转移、Cluster 集群的数据分片原理，并掌握选型方法。

---

## 一、主从复制

### 1.1 为什么需要主从？

单机 Redis 一旦宕机，服务不可用。主从复制让数据有多个副本：
- **读写分离**：读请求走从节点，主节点专注写
- **数据冗余**：主节点宕机后，从节点仍有完整数据
- **故障转移基础**：哨兵/集群的基础能力

### 1.2 原理

```
主节点 (Master) ──────── 全量复制 ────────▶ 从节点 (Slave)
    │                                              │
    └── 增量复制（命令传播）◀────────────────┘
```

**复制流程的三阶段**：

```
1. 握手阶段：从节点执行 SLAVEOF 命令，建立主从关系
2. 全量复制：主节点执行 BGSAVE 生成 RDB 快照 → 传给从节点 → 从节点加载
3. 增量复制：之后的主节点写命令通过复制积压缓冲区（repl_backlog）增量传播
```

### 1.3 配置

```bash
# 从节点执行
> SLAVEOF 192.168.1.10 6379

# 查看主从状态
> INFO replication
# Replication
role:slave
master_host:192.168.1.10
master_port:6379
master_link_status:up

# 断开主从
> SLAVEOF NO ONE
```

### 1.4 全量复制 → 增量复制（Redis 2.8+）

重连后优先尝试**部分重同步**：从节点发送自己的复制偏移量 `master_repl_offset`，主节点检查 `repl_backlog`（默认 1MB 的环形缓冲区）中是否还有该偏移量之后的数据：
- 还在 → 增量同步（快）
- 已被覆盖 → 全量同步（慢）

### 1.5 面试高频问题

> **主从复制有延迟怎么办？**
> 主从复制是异步的，从节点数据可能落后主节点。应对：短 TTL 缓存容忍、强制读主节点、`WAIT` 命令等待同步。

> **主从模式可以写从节点吗？**
> 从节点默认只读（`slave-read-only yes`），写从节点会被拒绝。所有写操作必须走主节点。

---

## 二、哨兵模式（Sentinel）— 自动故障转移

### 2.1 为什么需要哨兵？

主从复制解决了数据冗余，但**主节点宕机后不会自动切换**，需要人工干预。哨兵（Sentinel）就是"监工"，自动监控主节点状态，宕机时自动切换。

### 2.2 架构

```
                    ┌─────────────────────────┐
                    │  Sentinel 哨兵集群 (3+ 节点) │
                    └──────┬──────┬──────┬─────┘
                           │      │      │
              ┌────────────▼─┐  ┌─▼──────────┐
              │  Master       │  │  Slave 1    │
              │  (写)         │──│  (读)       │
              └──────────────┘  └─────────────┘
```

### 2.3 核心机制

**3 个哨兵节点以上部署**，防止哨兵自身单点故障。

**主观下线（sdown）**：单个哨兵发现主节点在 `down-after-milliseconds` 内没有响应心跳。

**客观下线（odown）**：足够多数量的哨兵（quorum，如 2/3）都判定主节点主观下线。

**选举新的主节点**（raft 类似算法）：
1. 哨兵集群选举 leader 哨兵
2. leader 在从节点中选出新主（优先选复制偏移量最大、runid 最小的）
3. 其余从节点执行 `SLAVEOF new_master`
4. 通知客户端新主节点地址

### 2.4 配置

```bash
# sentinel.conf
sentinel monitor mymaster 127.0.0.1 6379 2   # 监控主节点，quorum=2
sentinel down-after-milliseconds mymaster 5000  # 5 秒无响应判定宕机
sentinel failover-timeout mymaster 15000        # 故障转移超时
sentinel parallel-syncs mymaster 1              # 同时允许几个从节点同步新主
```

```bash
# 启动哨兵
redis-server sentinel.conf --sentinel
```

### 2.5 客户端接入

```java
// Spring Data Redis 配置哨兵
spring:
  redis:
    sentinel:
      master: mymaster
      nodes: 127.0.0.1:26379,127.0.0.1:26380,127.0.0.1:26381
```

---

## 三、Redis Cluster 集群 — 数据分片

### 3.1 为什么需要集群？

单机 Redis 内存有限（通常最大 64GB），且无法水平扩展。Cluster 模式将数据**分片存储**在多个节点上：
- **水平扩展**：容量随节点数增加
- **高可用**：每个分片有主从副本
- **去中心化**：所有节点互联，无中心节点

### 3.2 数据分片：哈希槽（Hash Slot）

```
CRC16(key) % 16384 = 哈希槽号
```

**16384 个哈希槽**均匀分配到所有主节点：

```
节点 A：槽 0-5460
节点 B：槽 5461-10922
节点 C：槽 10923-16383
```

```
> CLUSTER INFO
cluster_state:ok
cluster_slots_assigned:16384
cluster_slots_ok:16384

> CLUSTER KEYSLOT user:1001
(integer) 12456        # 该 key 属于槽 12456，即节点 C
```

### 3.3 重定向（Redirect）

客户端请求的 key 不在当前节点时，返回 `MOVED` 错误并告知正确节点。智能客户端（如 JedisCluster、Lettuce）会缓存槽位映射表，直接路由。

```bash
# 请求打到节点 A，但 key 属于节点 C
(error) MOVED 12456 127.0.0.1:7003
```

**集群不可用场景**：
- 一个主节点宕机且没有从节点
- 故障转移期间，涉及该主节点槽的请求全部失败

### 3.4 集群命令操作限制

**跨槽多 key 操作失败**（`MSET`、`MGET` 多 key 在不同槽时），需要：
- **hash tag**：`{user1001}.cart ` 和 `{user1001}.orders` 会分到同一槽，支持事务/Lua

```bash
> MSET user:1:name "a" user:2:name "b"
(error) CROSSSLOT Keys in request don't hash to the same slot

> MSET {user}:name "a" {user}:email "b"
OK    # 使用 hash tag，同一槽
```

### 3.5 集群搭建

```bash
# 6 节点（3 主 3 从）+ 修改每个 redis.conf
cluster-enabled yes
cluster-config-file nodes-7001.conf
port 7001

# 创建集群（Redis 5.0+）
redis-cli --cluster create \
  127.0.0.1:7001 127.0.0.1:7002 127.0.0.1:7003 \
  127.0.0.1:7004 127.0.0.1:7005 127.0.0.1:7006 \
  --cluster-replicas 1
```

---

## 四、集群 vs 哨兵选型

| 对比维度 | 哨兵模式 | Cluster 集群 |
|----------|---------|-------------|
| 数据容量 | 单节点内存上限 | 分片扩展，容量可随节点增加 |
| 高可用 | 自动故障转移 | 分片内自动故障转移 |
| 写入扩展 | 不支持（单写入点） | 支持多主写入 |
| 运维复杂度 | 低 | 高（槽位、跨槽限制） |
| 多 key 操作 | 支持 | 依赖 hash tag |
| 适用场景 | 数据量 < 100GB，读写分离 | 数据量 > 100GB，需要水平扩展 |

**选型建议**：
- 数据量小（< 10GB）、读多写少 → 主从 + 哨兵
- 数据量大、需要水平扩展 → Cluster
- 高并发写入（> 10 万 QPS）→ Cluster 多主

---

## 五、其他高可用方案

### 5.1 Codis / Proxy 方案

将多个 Redis 实例通过中间件代理，对应用透明。已逐渐被 Cluster 取代。

### 5.2 云服务方案

阿里云 Tair、腾讯云 Redis、AWS ElastiCache——底层用 Cluster 或类似分片技术，提供自动扩缩容，是中小团队的首选。

---

## 六、面试速记

| 方案 | 解决的问题 | 核心机制 | 一句话总结 |
|------|-----------|---------|-----------|
| 主从复制 | 数据冗余、读写分离 | RDB 全量 + 增量复制 | 数据备份，人工切换 |
| 哨兵模式 | 自动故障转移 | 主观/客观下线 + 选举 | 监控 + 自动切换 |
| Cluster | 水平扩展 | 16384 哈希槽 + 分片 | 分片存储 + 分片内高可用 |

> 进入下一节：性能调优——内存优化、大 Key 热 Key、慢查询。