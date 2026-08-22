# Elasticsearch 场景面试题

## 场景题 1：集群状态 red

**现象**：Kibana 监控面板显示集群健康状态为红色（red），部分索引不可用，搜索请求部分失败。

**原因分析**：
- 至少有一个主分片未分配，可能是因为节点宕机、磁盘满、分片数据损坏
- 常见触发场景：节点重启后数据恢复失败、新节点加入后分片迁移超时

**排查步骤**：

1. 查看集群健康状态
   ```
   GET _cluster/health
   ```

2. 定位未分配分片
   ```
   GET _cat/shards?v&h=index,shard,prirep,state,node,unassigned.reason
   ```

3. 获取未分配详细原因（最关键一步）
   ```
   GET _cluster/allocation/explain
   ```
   返回结果中包含 `explanation` 字段，说明为什么分片无法分配，例如磁盘空间不足、节点不在线、分配策略限制等。

**常见修复方案**：

| 原因 | 修复 |
|------|------|
| 磁盘空间不足 | 清理磁盘或扩容，触发 reroute |
| 节点宕机 | 重启节点或减少副本数 `index.number_of_replicas=0` |
| 分片数据损坏 | 执行 reroute 重新分配 |
| 分配策略限制 | 调整 `cluster.routing.allocation` 配置 |

**应急命令**：未分配分片需要进行手动分配时，使用 `_cluster/reroute` API 分配，但优先让 ES 自动恢复。

---

## 场景题 2：查询慢

**现象**：用户反馈搜索结果返回时间超过 5 秒，日常 QPS 高时尤为明显。

**排查步骤**：

1. **启用慢查询日志**
   ```
   PUT /my_index/_settings
   {
     "index.search.slowlog.threshold.query.warn": "2s",
     "index.search.slowlog.threshold.fetch.warn": "1s"
   }
   ```

2. **使用 Profile API 分析查询**
   ```
   GET /my_index/_search
   {
     "profile": true,
     "query": { ... }
   }
   ```
   返回结果中的 `profile` 部分会详细展示每个分片在每个查询阶段的耗时，帮助定位瓶颈在 query 阶段还是 fetch 阶段。

3. **检查索引设计**
   - 分片数是否合理？分片过多（> 20GB/分片）或过少（< 1GB/分片）都影响性能
   - 是否需要减少 `_source` 返回字段，使用 `_source_includes` 按需加载
   - 是否存在无索引的字段查询（未建立索引的字段无法被搜索）

**优化方案**：

| 优化方向 | 具体操作 |
|----------|----------|
| 查询优化 | 使用 filter 代替 must（filter 不参与评分、可缓存） |
| 索引优化 | 合理设置分片数、关闭不需要的字段的 `_source` 索引 |
| 硬件优化 | 使用 SSD、增加内存（JVM heap 不超过 50% 物理内存，不超过 32GB） |
| 缓存优化 | 启用查询缓存（`index.requests.cache.enable: true`） |

---

## 场景题 3：写入性能差

**现象**：大量商品数据导入时，ES 写入速度远低于预期，出现拒绝（429）错误。

**原因分析**：
- 单条写入而非批量写入，网络开销过大
- `refresh_interval` 过短，频繁刷新产生大量小 segment
- 副本数过多，写入时每个分片都要同步到所有副本
- 磁盘 IO 成为瓶颈

**解决方案**：

1. **使用 Bulk API 批量写入**
   ```json
   POST _bulk
   { "index": { "_index": "product", "_id": "1" } }
   { "name": "商品1", ... }
   { "index": { "_index": "product", "_id": "2" } }
   { "name": "商品2", ... }
   ```
   建议每批 1000-5000 条或 5-15MB。

2. **临时调整 refresh_interval**
   ```
   PUT /product/_settings
   { "index": { "refresh_interval": "30s" } }
   ```
   全量导入后恢复为 `1s` 或 `-1`（关闭自动 refresh）。

3. **降低副本数（导入时）**
   ```
   PUT /product/_settings
   { "index": { "number_of_replicas": 0 } }
   ```
   导入完成后恢复副本数。

4. **硬件优化**
   - 使用 SSD 磁盘，避免机械磁盘 IO 瓶颈
   - 确保 translog 落盘策略为异步（`index.translog.durability: async`）

---

## 场景题 4：数据不一致

**现象**：刚写入的数据搜索不到，或主备节点数据不一致。

**原理分析**：

ES 的一致性模型是**最终一致性**，并非强一致性：

- **refresh 机制**：新写入的文档先写入内存 buffer，每秒 refresh 一次后生成 segment 才可被搜索。如果写入后立即搜索，数据可能不可见
- **flush 机制**：segment 持久化到磁盘，同时清空 translog，保证宕机后数据可恢复
- **主备延迟**：主分片写入成功后，副本分片异步同步，极端情况下（网络延迟、高负载）副本可能落后于主分片

**解决方案**：

| 场景 | 方案 |
|------|------|
| 写入后需立即搜索 | 设置 `refresh=wait_for` 等待 refresh 完成 |
| 容忍较高延迟 | 默认 `refresh_interval=1s` 即可 |
| 主备强一致读 | 设置 `preference=_primary` 强制从主分片读取 |
| 批量导入 | 关闭 refresh 或设大间隔，导入完成后手动 refresh |

**预防措施**：

- 监控集群健康状态，及时发现副本未分配情况
- 配置 `min_shards_per_node` 确保副本分布均匀
- 使用 `wait_for_active_shards` 参数指定写入前至少需要多少副本确认
- 区分业务场景：关键数据（如订单）建议写入后主动 refresh，非关键数据（如日志）默认即可