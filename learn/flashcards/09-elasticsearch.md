# Elasticsearch — 面试抽认卡

> 来源：`learn/09-elasticsearch/05-interview/`

---

### Card 1: 倒排索引原理
**维度**: 🔬深挖 | **难度**: ⭐⭐⭐

> **Q: Elasticsearch 的倒排索引是如何工作的？为什么比正向索引快？**

**A:** 倒排索引以词条（Term）为键，映射到包含该词条的文档 ID 列表（Posting List）和词频/位置等信息。搜索时，先分词得到 Term，直接定位到包含该 Term 的文档，无需遍历所有文档。正向索引（文档→词条）需要遍历每篇文档再分词匹配，效率低。倒排索引还支持 TF-IDF/BM25 评分、短语查询（通过位置信息）。ES 还使用 FST（Finite State Transducer）压缩 Term Dictionary，支持高效的前缀搜索。

---

### Card 2: 分词器选择
**维度**: 📝速记 | **难度**: ⭐⭐

> **Q: Elasticsearch 中文场景下如何选择分词器？**

**A:** 默认 Standard 分词器对中文逐字切分（一个字一个 Term），搜索效果差。生产推荐 IK 分词器（`ik_smart` 粗粒度，`ik_max_word` 细粒度）。`ik_max_word` 穷尽所有可能词（如"中华人民共和国"→中华/华人/人民/共和国等），用于索引；`ik_smart` 最粗粒度切分，用于搜索。其他分词器：`pinyin`（拼音搜索，如"zgr"→"中国人"）、`jieba`（结巴分词）、`THULAC`（清华分词）。自定义词典：`IKAnalyzer.cfg.xml` 配置扩展词典或停用词典。

---

### Card 3: DSL 查询基础
**维度**: 💻代码 | **难度**: ⭐⭐

> **Q: ES 的 Query DSL 有哪些常见查询类型？**

**A:** 叶子查询：`match`（全文匹配，分词后匹配）、`term`（精确匹配，不分词）、`range`（范围查询）、`exists`（字段存在性）、`prefix`（前缀查询）、`wildcard`（通配符，性能差）。复合查询：`bool`（must：AND，should：OR，must_not：NOT，filter：过滤不评分）。`match` 和 `term` 的区别：`match` 对查询值分词，`term` 整体匹配倒排索引中的精确 Term。

---

### Card 4: bool 查询组合
**维度**: 💻代码 | **难度**: ⭐⭐

> **Q: bool 查询中 must / should / filter / must_not 的执行顺序和评分影响是什么？**

**A:** 执行顺序：filter/must_not（先过滤，无评分）→ must（计算评分，贡献分数）→ should（可选，minimum_should_match 控制最少匹配数）。filter 的结果被缓存（bitset 缓存），适合范围过滤。示例：`{"bool": {"must": [{"match": {"title": "手机"}}], "filter": [{"term": {"status": 1}}, {"range": {"price": {"gte": 1000, "lte": 5000}}}]}}`。filter 不评分减少算力消耗，must 评分用于排序。

---

### Card 5: 聚合分析
**维度**: 💻代码 | **难度**: ⭐⭐⭐

> **Q: ES 的聚合分析有哪些类型？如何实现嵌套聚合？**

**A:** 桶聚合（Bucket）：`terms`（词条分组）、`range`（范围分组）、`date_histogram`（日期直方图）。指标聚合（Metric）：`avg`、`sum`、`max`、`min`、`stats`（统计摘要）、`cardinality`（去重计数，近似值）。管道聚合（Pipeline）：`bucket_script`（在聚合结果上执行脚本）。嵌套聚合：`{"aggs": {"group_by_color": {"terms": {"field": "color"}, "aggs": {"avg_price": {"avg": {"field": "price"}}}}}}`。先按 color 分组，再计算每组平均价格。

---

### Card 6: 分片路由原理
**维度**: 🔬深挖 | **难度**: ⭐⭐

> **Q: ES 中文档是如何路由到分片的？自定义路由有什么作用？**

**A:** 默认路由：`shard = hash(_routing) % number_of_shards`，`_routing` 默认等于 `_id`。自定义路由：索引时指定 `?routing=user123`，同一个 routing 值的文档落入同一分片，支持批量查询（`search?routing=user123` 只查一个分片）。优点：查询效率高（只查一个分片）、支持 Join 类型（同一分片内关联）。缺点：数据可能分布不均，需要合理设计 routing 值的基数。注意：主分片数创建后不可修改，否则路由公式变化导致数据错位。

---

### Card 7: 集群状态与选举
**维度**: 📝速记 | **难度**: ⭐⭐

> **Q: ES 集群的健康状态有哪些？Master 节点如何选举？**

**A:** 健康状态：green（所有主分片和副本分片正常）、yellow（主分片正常，部分副本未分配）、red（存在未分配的主分片，数据丢失风险）。Master 选举：7.x 之前基于 Zen Discovery（`discovery.zen.minimum_master_nodes`），7.x 之后基于投票机制（`cluster.initial_master_nodes`），需要半数以上节点同意才能成为 Master。Master 节点负责集群元数据管理（创建/删除索引、分配分片），不参与数据读写。

---

### Card 8: 写入流程
**维度**: 🔬深挖 | **难度**: ⭐⭐⭐

> **Q: ES 写入一条文档的完整流程是怎样的？**

**A:** ① 协调节点接收请求，路由到主分片节点；② 主分片写入内存 buffer 并记录 translog；③ 主分片转发到副本分片；④ 副本分片写入 buffer 并记录 translog，返回确认；⑤ 主分片收到所有副本确认后返回响应给客户端；⑥ 默认每秒 refresh（buffer → segment，使文档可搜索）；⑦ 默认 30 分钟或 translog 达到阈值时 flush（segment 持久化到磁盘，清空 translog）。refresh 使文档近实时可见（NRT），flush 保证持久化。

---

### Card 9: 查询优化
**维度**: 🎯场景 | **难度**: ⭐⭐⭐

> **Q: ES 查询性能优化有哪些关键策略？**

**A:** ① 减少查询字段（只返回需要的字段，`_source` 过滤，禁止返回 `*`）；② 使用 filter 而非 must（filter 不评分且缓存 bitset）；③ 批量查询（`_msearch` 或 `mget` 替代多次查询）；④ 索引优化（合理分片数、SSD 磁盘、`index.refresh_interval` 调大减少写入时刷新）；⑤ 路由优化（自定义 routing 减少查询分片数）；⑥ 避免深度分页（`search_after` 替代 `from + size`）；⑦ 开启慢查询日志（`index.search.slowlog.threshold.query.warn`）定位慢查询。

---

### Card 10: 数据同步方案
**维度**: 🎯场景 | **难度**: ⭐⭐⭐

> **Q: MySQL 与 ES 数据同步有哪些方案？各有什么优缺点？**

**A:** ① Logstash 定时轮询（JDBC Input Plugin，`schedule => "* * * * *"`，简单但延迟高，分钟级）；② Canal 监听 binlog 实时推送（低延迟，秒级，需部署 Canal 组件）；③ 应用层双写（写 MySQL 同时写 ES，简单但耦合度高，一致性问题）；④ MQ 异步同步（写 MySQL 后发 MQ 消息，消费者写 ES，解耦但需要保证幂等）。推荐：Canal + MQ 方案，低延迟、解耦、可靠。

---

### Card 11: Mapping 设计
**维度**: 📝速记 | **难度**: ⭐⭐

> **Q: ES 的 Mapping 设计有哪些注意事项？**

**A:** ① 禁用不必要的 `text` 字段的 `fielddata`（内存消耗大）；② 精确值用 `keyword`（不分词），全文搜索用 `text`（分词）；③ `index: false` 禁用不需要搜索的字段（节省存储）；④ `doc_values` 默认开启（排序/聚合用），不需要的字段可关闭（节省磁盘）；⑤ `nested` 类型用于数组对象（保留对象内部关联，但查询性能差）；⑥ 生产环境用显式 Mapping（避免动态映射推断了错误的类型，如时间戳被映射为 text）。

---

### Card 12: 冷热架构
**维度**: 🎯场景 | **难度**: ⭐⭐⭐

**Q: ES 的冷热架构是什么？如何配置？**

**A:** 冷热架构将节点按硬件配置分为热节点（hot，SSD + 高内存，处理近实时数据）和冷节点（warm/cold，HDD + 低内存，存储历史数据）。配置：`node.roles: ["data_hot"]`（热节点），`node.roles: ["data_warm"]`（温节点）。通过索引生命周期管理（ILM）自动迁移：`{"phases": {"hot": {"min_age": "0ms", "actions": {"rollover": {"max_age": "1d"}}}, "warm": {"min_age": "7d", "actions": {"allocate": {"require": {"data": "warm"}}}, "cold": {"min_age": "30d", "actions": {"allocate": {"require": {"data": "cold"}}}}}}`。

---

### Card 13: 索引生命周期管理
**维度**: 📝速记 | **难度**: ⭐⭐

> **Q: ES 的索引生命周期管理（ILM）包含哪些阶段？各阶段可执行什么操作？**

**A:** 四个阶段：hot（热，读写活跃，rollover 滚动到新索引）、warm（温，只读不写，收缩到更少分片、强制合并 segment）、cold（冷，只读不写，降低副本数）、delete（删除，到达保留期后删除索引）。每个阶段可配置 `actions`：`rollover`（滚动索引）、`shrink`（收缩分片）、`forcemerge`（合并段）、`allocate`（分配节点）、`set_priority`（设置优先级）、`delete`（删除）。ILM 策略通过 `PUT _ilm/policy/my_policy` 创建，索引模板绑定。

---

### Card 14: NRT 近实时性
**维度**: 📝速记 | **难度**: ⭐⭐

> **Q: ES 写入后为什么不能立即搜索到？refresh 和 flush 的区别是什么？**

**A:** 写入的数据先进入 buffer（内存），每 1 秒 refresh 一次，将 buffer 数据生成 segment 并清空 buffer，文档变为可搜索，这就是"近实时"（Near Real Time）。flush 将 segment 从内存持久化到磁盘，并清空 translog。refresh 频率可调（`index.refresh_interval: 30s` 适合批量写入场景），flush 默认 30 分钟或 translog 达到 512MB 触发。`?refresh=wait_for` 等待 refresh 后再返回写入响应。

---

### Card 15: 并发控制
**维度**: 📝速记 | **难度**: ⭐⭐

> **Q: ES 如何进行并发控制？乐观锁和悲观锁如何实现？**

**A:** ES 使用乐观锁，基于 `_version` 或 `_seq_no` + `_primary_term` 实现。`PUT /index/_doc/id?if_seq_no=5&if_primary_term=1`，版本匹配才更新，不匹配返回 409 Conflict，应用层重试。ES 不支持悲观锁。另外，`update` API 内部先读后写，自动重试冲突（`retry_on_conflict` 参数，默认 0 次）。`index` 操作（全量覆盖）不适合高并发更新，推荐 `update` 执行部分更新。