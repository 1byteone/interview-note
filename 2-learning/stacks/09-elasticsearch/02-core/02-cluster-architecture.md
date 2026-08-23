# 集群架构与高可用

> 理解 Elasticsearch 的分布式架构——节点角色、分片路由、集群健康与故障恢复，是运维和面试的高频考点。

---

## 1. 节点类型

ES 集群中的每个节点可以承担一个或多个角色，通过角色分离实现职责专一和性能优化。

### 1.1 角色总览

| 节点角色 | 职责 | 资源消耗 | 推荐配置 |
|----------|------|----------|----------|
| **Master** | 集群管理：维护集群状态、处理创建/删除索引、管理节点加入/离开 | CPU 密集（状态变更） | 3 台专用，中低配 |
| **Data** | 数据存储：持有分片，执行 CRUD 和搜索/聚合操作 | IO + 内存密集 | 多台，高配 SSD |
| **Ingest** | 数据预处理：写入前执行管道（pipeline）处理，如拆字段、转换格式 | CPU 密集 | 可复用 Data 节点 |
| **Coordinating** | 请求转发：接收客户端请求，分发到 Data 节点，汇总结果返回 | CPU + 网络 | 可复用，高并发需独立 |
| **Machine Learning** | 机器学习：异常检测、预测分析 | CPU + 内存 | 按需开启 |

### 1.2 Master 节点

Master 节点负责集群层面的元数据管理，不参与数据读写。

**核心职责**：
- 维护集群状态（Cluster State）：索引映射、分片分配、节点列表
- 处理索引创建、删除、别名等元数据操作
- 检测节点心跳，处理节点加入/离开
- 触发分片再平衡（Rebalance）

**选举机制**：基于 Zen Discovery，通过 Bully 算法选主。节点启动后向集群发送投票请求，获得大多数（超过半数）投票的节点成为 Master。

```yaml
# elasticsearch.yml
node.roles: [ master ]
discovery.seed_hosts: ["node1:9300", "node2:9300", "node3:9300"]
cluster.initial_master_nodes: ["node1", "node2", "node3"]
```

> **生产最佳实践**：部署 3 个 Master 节点（奇数个），避免"少数服从多数"时平局。Master 节点不存储数据，使用低配机器即可。

### 1.3 Data 节点

Data 节点是集群的"主力军"，负责分片的存储和查询。

```
Data 节点配置要点：
├── 磁盘：SSD 推荐，容量规划 = 源数据 × (1 + 副本数) × 1.3（膨胀系数）
├── 内存：最大 32GB（超过需配置 .bss 或使用 JDK 17+ 的 ZGC）
├── CPU：核心数 × 1.5~2 = 分片数（经验值）
└── 存储：建议单节点磁盘使用率不超过 85%
```

```yaml
node.roles: [ data ]
```

### 1.4 Coordinating 节点

Coordinating 节点接收客户端请求，将请求分发到各 Data 节点，收集结果后汇总返回。

```
客户端请求 → Coordinating 节点
    ├── 解析请求，确定目标索引
    ├── 将请求分发给所有相关分片（主分片或副本分片）
    ├── 各分片本地执行查询，返回结果
    └── Coordinating 节点合并、排序、返回
```

> 高并发场景下，建议独立部署 Coordinating 节点，避免数据节点因协调请求导致 CPU 满载。

### 1.5 Ingest 节点

数据写入前经过 Ingest 节点执行的 Pipeline（管道）处理：

```json
PUT _ingest/pipeline/html_to_text
{
  "description": "Remove HTML tags from body",
  "processors": [
    { "html_strip": { "field": "body" } },
    { "remove": { "field": "raw_html" } }
  ]
}

// 写入时指定 pipeline
PUT my_index/_doc/1?pipeline=html_to_text
{
  "body": "<p>商品详情</p>",
  "raw_html": "<p>商品详情</p>"
}
```

---

## 2. 分片与副本

### 2.1 分片（Shard）

分片是 ES 数据存储的最小单元，每个分片本质是一个 Lucene 索引实例。

**路由算法**：文档写入或查询时，ES 通过路由算法确定文档属于哪个分片。

```
shard_num = hash(_routing) % number_of_primary_shards
```

- **_routing**：默认取文档的 `_id` 字段，可自定义
- **number_of_primary_shards**：索引创建时指定的主分片数，创建后不可修改

```json
// 自定义路由
PUT product/_doc/1?routing=category_phone
{
  "name": "华为手机",
  "category": "phone"
}
```

> 自定义路由可以将同一类数据路由到同一分片，提高查询效率（如按用户 ID 路由，查询时也可以指定 routing 参数，只扫描一个分片）。

### 2.2 副本（Replica）

每个主分片可以有多个副本分片，副本分片是主分片的完整拷贝。

```json
PUT product
{
  "settings": {
    "number_of_shards": 5,    // 主分片数，创建后不可修改
    "number_of_replicas": 1   // 副本数，可动态调整
  }
}
```

**副本的作用**：
- **高可用**：主分片故障时，副本提升为新的主分片
- **提高读吞吐**：副本分片可以响应查询请求，实现读负载均衡

### 2.3 写流程

```
客户端写入请求
  ↓
① Coordinating 节点接收请求
  ↓
② 计算 hash(_routing) % number_of_shards → 目标主分片
  ↓
③ 将请求转发到主分片所在 Data 节点
  ↓
④ 主分片写入成功后，并行将请求转发到所有副本分片
  ↓
⑤ 所有副本分片写入成功后，回复主分片
  ↓
⑥ 主分片回复 Coordinating 节点 → 返回客户端成功
```

**写流程的关键点**：
- **主分片负责顺序**：同一分片上的写入请求由主分片串行处理，保证顺序
- **副本同步**：主分片等待所有副本写入成功后，才返回客户端成功（可配置 `wait_for_active_shards` 控制等待的副本数量）
- **写入一致性**：默认要求主分片和至少一个副本分片写入成功

### 2.4 读流程

```
客户端查询请求
  ↓
① Coordinating 节点接收请求
  ↓
② 轮询策略：将请求分发到主分片或副本分片（轮询）
  ↓
③ 各分片本地执行查询，返回结果（docId + 得分）
  ↓
④ Coordinating 节点合并各分片结果，排序后取 top N
  ↓
⑤ 再次请求各分片获取完整文档内容（fetch phase）
  ↓
⑥ 返回最终结果给客户端
```

**查询过程的两阶段**：
- **Query Phase**：各分片本地搜索，返回文档 ID 和得分
- **Fetch Phase**：Coordinating 节点根据得分排序，取 top N 后请求各分片获取完整文档

**读负载均衡**：Coordinating 节点在同一分片的主分片和副本分片之间轮询分发请求，充分利用副本提升读吞吐。

---

## 3. 集群健康

ES 通过 `_cluster/health` API 返回集群健康状态，有三种颜色：

```json
GET _cluster/health

// 响应示例
{
  "cluster_name": "mall-es-cluster",
  "status": "yellow",           // green / yellow / red
  "number_of_nodes": 3,
  "number_of_data_nodes": 3,
  "active_primary_shards": 10,
  "active_shards": 15,
  "unassigned_shards": 5,
  "relocating_shards": 0,
  "initializing_shards": 0
}
```

### 3.1 状态含义

| 状态 | 含义 | 原因 | 影响 |
|------|------|------|------|
| **Green** | 所有主分片和副本分片都正常运行 | 正常状态 | 无 |
| **Yellow** | 所有主分片正常，但部分副本分片未分配 | 副本数量 > 可用节点数、节点故障、磁盘不足 | 读可用性降低，但写正常 |
| **Red** | 至少一个主分片未分配 | 节点宕机、磁盘故障、数据损坏 | 部分数据不可读写 |

### 3.2 常见场景

**Yellow 状态**：
```bash
# 最常见的 Yellow 原因：只有 1 个节点，但副本数设置为 1
# 因为没有额外的节点可以放置副本分片
# 解决方案：增加节点，或降低副本数
PUT product/_settings
{
  "number_of_replicas": 0
}
```

**Red 状态**：
```bash
# 主分片不可用，需要重新分配或重新路由
GET _cluster/allocation/explain

# 重新分配分片（已确定节点可恢复时）
POST _cluster/reroute
{
  "commands": [
    {
      "allocate_stale_primary": {
        "index": "product",
        "shard": 0,
        "node": "node-2",
        "accept_data_loss": true
      }
    }
  ]
}
```

---

## 4. 故障恢复

### 4.1 Master 选举

当 Master 节点宕机时，集群中的其他 Master-eligible 节点会触发新的选举。

**选举流程**：
```
① 节点检测到 Master 心跳超时（默认 30 秒，可通过 discovery.zen.ping_timeout 调整）
② 节点发起投票，向所有 Master-eligible 节点发送投票请求
③ 获得超过半数 (n/2 + 1) 投票的节点成为新 Master
④ 新 Master 发布新的 Cluster State，集群恢复正常
```

**候选节点数要求**：
| 集群节点数 | 允许故障数 | 推荐 Master 节点数 |
|-----------|-----------|-------------------|
| 1 | 0 | 1 |
| 2 | 0 | 2（不推荐，无法形成多数） |
| 3 | 1 | 3 |
| 5 | 2 | 3~5 |
| 7 | 3 | 3~5 |

### 4.2 副本提升

当 Data 节点宕机导致主分片丢失时，ES 自动将副本分片提升为新的主分片。

```
① 宕机前：Shard 0 主分片在 Node-A，副本分片在 Node-B
② Node-A 宕机：Coordinating 节点检测到连接断开
③ Master 节点将 Node-B 上的副本分片提升为新的主分片
④ 集群状态更新：Shard 0 主分片在 Node-B
⑤ 如果 Node-A 恢复：旧主分片变为副本分片，与新主分片同步数据
```

### 4.3 分片恢复

节点重启或新节点加入时，ES 会自动进行分片恢复。

**恢复优先级**：
1. 未分配的主分片（最高优先级，保障数据可写）
2. 未分配的副本分片
3. 正在迁移的分片

**恢复限流**：ES 默认限流恢复速度，防止恢复过程影响正常业务。

```yaml
# 调整恢复速度（默认 40MB/s）
indices.recovery.max_bytes_per_sec: 100mb
```

---

## 5. 脑裂问题及解决方案

### 5.1 什么是脑裂

脑裂（Split-Brain）是指集群中同时存在多个 Master 节点，各自维护独立的 Cluster State，导致数据不一致。

```
正常状态：                   脑裂状态：
┌──────┐                    ┌──────┐
│Master│                    │Master│ ← 分区 A
├──────┤                    ├──────┤
│Node-A│                    │Node-A│
│Node-B│                    │Node-B│
│Node-C│                    └──────┘
└──────┘                    ┌──────┐
                             │Master│ ← 分区 B
                             ├──────┤
                             │Node-C│
                             │Node-D│
                             └──────┘
```

### 5.2 脑裂的原因

| 原因 | 说明 | 典型场景 |
|------|------|----------|
| 网络分区 | 节点间网络不通，导致分区内各自选举 Master | 交换机故障、网络抖动 |
| 高 GC 停顿 | JVM Full GC 导致节点长时间无响应，被误判为宕机 | 堆内存不足、GC 配置不当 |
| Master 超时过短 | `discovery.zen.ping_timeout` 设置过小，节点短暂繁忙就被踢出 | 默认 3s 可能过于敏感 |

### 5.3 解决方案

**方案一：最少 Master 节点数（推荐）**

```yaml
# elasticsearch.yml
discovery.zen.minimum_master_nodes: 2   # = (master节点数 / 2) + 1
```

该配置要求集群中 Master-eligible 节点数必须达到最小值，否则不会选举 Master，从根本上防止脑裂。

| 节点数 | minimum_master_nodes | 允许故障数 |
|--------|---------------------|-----------|
| 3 | 2 | 1 |
| 5 | 3 | 2 |
| 7 | 4 | 3 |

**方案二：角色分离 + 专用 Master 节点**

将 Master 节点与 Data 节点分离，Master 节点不参与数据读写，降低 Master 节点因负载过高而失联的概率。

**方案三：合理设置超时和重试**

```yaml
# 适当增加 Master 选举超时时间
discovery.zen.ping_timeout: 10s           # 默认 3s，适当调大
discovery.zen.join_retry_attempts: 5      # 重试次数
discovery.zen.join_retry_delay: 1s        # 重试间隔
```

**方案四：监控告警**

监控集群健康状态和 Master 节点状态，及时发现异常：

```bash
# 查看当前 Master 节点
GET _cat/master?v

# 查看节点列表
GET _cat/nodes?v
```

### 5.4 脑裂恢复

一旦发生脑裂，恢复步骤：

```
1. 停止所有节点上的 ES 服务
2. 选择一个正确的 Master 节点（通常是最新的那个）
3. 删除其他节点上不一致的 Cluster State（谨慎操作！）
4. 先启动选定的 Master 节点
5. 逐个启动其他节点，观察集群状态从 Red → Yellow → Green
6. 检查数据完整性，必要时重新索引
```

---

## 总结

- **节点角色分离**：生产环境推荐部署专用 Master 节点（3 个奇数个），Data 节点按容量规划，Coordinating 节点在高并发场景下独立部署
- **分片与副本**：主分片数创建后不可修改，副本数可动态调整；写流程由主分片负责，读流程在主副分片间轮询
- **集群健康**：Green = 正常，Yellow = 副本未分配，Red = 主分片丢失
- **故障恢复**：Master 选举需多数投票，副本提升保证高可用，分片恢复有限流机制
- **脑裂防范**：设置 `minimum_master_nodes = (n/2) + 1`，角色分离，合理配置超时

> 下一篇：进阶内容见 [03-advanced/01-cluster-architecture.md](../03-advanced/01-data-sync.md) — 集群部署、性能调优、数据同步、选型对比