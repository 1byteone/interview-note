# Elasticsearch 深度面试题

## 倒排索引存储结构

倒排索引不是简单的"词条 → 文档列表"映射，它在 Lucene 层面有精细的存储结构：

### Term Dictionary（词条字典）
存储所有词条（Term）的有序列表，按字典序排列，用于快速定位词条是否存在。当数据量极大时，Term Dictionary 无法完全加载到内存，因此 Lucene 构建了 **Term Index** 进行加速。

### Term Index（词条索引，.tip 文件）
以 FST（Finite State Transducer，有限状态转换器）格式存储在 `.tip` 文件中，可以完全加载到内存。FST 在内存中根据前缀快速定位到 Term Dictionary 中的近似位置，实现 O(len(term)) 的查找复杂度。

### Term Dictionary 文件（.tim 文件）
存储在 `.tim` 文件中，包含每个词条的详情：词条文本、文档频率（DF）、指向 Posting List 的指针。`.tim` 文件通常按 Block 分块压缩存储。

### Posting List 文件（.doc 文件）
存储在 `.doc` 文件中，记录每个词条对应的文档 ID 列表。Lucene 使用 **Frame of Reference（FOR）** 或 **Roaring Bitmaps** 压缩差分编码后的文档 ID 序列，极大减少存储空间。

### 总结
- `.tip`：Term Index（FST，常驻内存）→ 快速定位
- `.tim`：Term Dictionary（块压缩）→ 词条详情 + 偏移量
- `.doc`：Posting List（差分编码压缩）→ 文档 ID 列表

---

## 分片路由算法

### 路由公式

```
shard_num = hash(_routing) % number_of_primary_shards
```

- `_routing` 默认取文档 `_id`，也可自定义路由值
- 相同的 routing 值保证文档落在同一分片

### 为什么创建后不能修改主分片数？

因为路由算法依赖 `number_of_primary_shards` 作为分母，一旦分片数变化，同一个文档 ID 的哈希值取模结果会改变，导致数据全部错位，相当于完全重建索引。虽然 ES 内部可以通过 `_shrink` 和 `_split` API 调整，但本质是创建一个新索引并重新路由数据。

### 自定义路由的注意事项

- 自定义 routing 时需指定 `routing=xxx`，否则请求会广播到所有分片
- 索引时指定 routing 后，查询时也必须传相同的 routing 值，否则可能找不到文档
- 推荐将 routing 设为业务主键（如 userId、商户 ID），实现同一用户数据集中在同一分片

---

## 写入与查询流程

### 写入流程（Index / Update / Delete）

1. **协调节点**：客户端发送请求，协调节点计算 `hash(_id) % 分片数` 确定目标分片
2. **主分片**：转发到主分片所在节点，主分片写入 Lucene 并记录 translog
3. **副本同步**：主分片并行转发到所有副本分片，副本写入完成后返回确认
4. **响应客户端**：主分片收到所有副本确认后，返回成功给客户端

> 注意：写入成功后数据并未立即可见，需等待 refresh（默认 1s）后，segment 才会打开供搜索。

### 查询流程（Search）

1. **Query Phase**：协调节点将请求转发到索引的所有分片（主分片 + 副本分片），各分片本地执行查询，返回匹配文档的 ID 列表及评分，协调节点合并排序取 Top N
2. **Fetch Phase**：协调节点取回 Top N 文档的完整数据（`_source`），返回给客户端

### 关键特征

- 查询分发到所有分片，副本分片分担读压力
- 两次网络往返（Query + Fetch），第一次轻量（仅 ID 和评分），第二次全量（完整文档）
- ES 的"准实时"特性来自 refresh 间隔，而非查询延迟

---

## 集群选举

### 7.x 之前的 Zen Discovery

- 节点通过多播或单播发现彼此
- 使用 `discovery.zen.minimum_master_nodes` 防止脑裂，推荐值为 `(master-eligible 节点数 / 2) + 1`
- 节点启动时 ping 其他节点，收集投票信息

### 7.x 之后的投票选举

从 7.0 开始，ES 引入了全新的集群协调子系统，基于 **Raft 共识算法** 的变体：

- **投票机制**：每个 master-eligible 节点持有一票，获得 `(总候选节点数 / 2) + 1` 票即当选
- **集群引导**：首次启动时通过 `cluster.initial_master_nodes` 指定候选节点列表
- **脑裂防护**：`discovery.zen.minimum_master_nodes` 不再需要，新机制内置了 quorum 判定
- **故障检测**：节点通过定期的探活请求检测其他节点存活状态，超时未响应则发起重新选举

### 选举过程

1. 节点发现当前 master 不可用（或启动时无 master）
2. 节点发起投票，推举自己或其他节点
3. 收集到多数票的节点成为新 master
4. 新 master 发布集群状态，更新所有节点

### 生产建议

- master-eligible 节点数设为奇数（3 或 5），避免平票
- 小集群（< 3 节点）允许单节点模式下运行
- 监控 cluster health 状态，及时发现选举异常