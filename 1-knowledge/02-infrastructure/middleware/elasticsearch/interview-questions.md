# Elasticsearch 面试题大全

## 📚 知识体系

```
Elasticsearch 核心概念
├── 文档 (Document)
├── 索引 (Index)
├── 分片 (Shard)
├── 副本 (Replica)
├── 倒排索引 (Inverted Index)
├── 分析器 (Analyzer)
├── 映射 (Mapping)
└── Query DSL

Elasticsearch 高级特性
├── 全文搜索 (Full-text Search)
├── 聚合分析 (Aggregations)
├── 分词器 (Analyzer)
│   ├── Standard
│   ├── IK 中文分词
│   ├── SmartCN
│   └── 自定义分词
├── 集群架构
│   ├── Node 角色
│   ├── Discover
│   ├── 主节点选举
│   └── 分片分配
├── 性能优化
├── 数据写入原理
└── 与 MySQL/Redis 同步 (Canal/Logstash)
```

---

## 🎯 Level 1：基础题

### 1. 什么是倒排索引？
**答案**：
倒排索引（Inverted Index）是一种以**词项**为索引键，指向**文档**的索引结构，用于快速全文检索。

**正向索引 vs 倒排索引**：
```
正向索引（文档 → 词）
Doc1: {I, love, Java}
Doc2: {I, love, Spring}

倒排索引（词 → 文档）：
I     → Doc1, Doc2
love  → Doc1, Doc2
Java  → Doc1
Spring→ Doc2
```

**核心结构**：
```json
{
  "词项字典": {
    "java": {"文档频率": 1, "倒排列表": [{"docId": 1, "频率": 1, "位置": [3]}]},
    "spring": {"文档频率": 1, "倒排列表": [{"docId": 2, "频率": 1, "位置": [3]}]}
  }
}
```

### 2. 为什么 Elasticsearch 搜索比 MySQL LIKE 快？
**答案**：

| 特性 | ES 倒排索引 | MySQL LIKE |
|------|-------------|------------|
| 索引结构 | 词项 → 文档 | 全表扫描 |
| 时间复杂度 | O(词项查找) | O(N) 全表 |
| 相关性评分 | 支持（TF-IDF/BM25） | 不支持 |
| 分词匹配 | 支持 | 不支持 |
| 模糊搜索 | 支持 | 弱 |
| 高亮 | 原生支持 | 需前端实现 |

**原理**：ES 建立词项到文档的映射，搜索时直接定位词项，无需扫描全部文档；MySQL 的 `LIKE '%xxx%'` 无法使用索引，只能全表扫描。

---

## 🎯 Level 2：进阶题

### 3. ES 的写入原理是什么？
**答案**：

```text
写入请求
    ↓
① 路由到主分片（hash 决定 shard）
    ↓
② 写入内存 buffer
    ↓
③ 写入 translog（磁盘，防止宕机丢失）
    ↓
④ 每秒 refresh → 生成 segment（可被搜索）
    ↓
⑤ 达到阈值 flush → segment 落盘 + 合并
```

**关键概念**：
- **Refresh**：每秒将 buffer 中数据生成 segment（近实时搜索）
- **Translog**：操作日志，防止数据丢失
- **Flush**：强制将 segment 落盘，清空 translog
- **Segment Merge**：后台合并小 segment，提高查询效率

### 4. 如何实现 MySQL 与 ES 的数据同步？
**答案**：

**方案一：Canal（binlog 监听）**

```
MySQL binlog
    ↓
Canal（伪装成 MySQL Slave）
    ↓
监听 binlog 变更
    ↓
同步到 ES
    ↓
RocketMQ/直连 ES
```

**方案二：Logstash（定时全量+增量）**
```yaml
input {
  jdbc {
    jdbc_driver_library: "mysql-connector-java.jar"
    jdbc_connection_string: "jdbc:mysql://localhost:3306/orders"
    jdbc_user: "root"
    jdbc_password: "123456"
    statement: "SELECT * FROM orders WHERE update_time > :sql_last_value"
    tracking_column: "update_time"
    schedule: "*/5 * * * * *"  # 每 5 秒增量同步
  }
}
output {
  elasticsearch {
    hosts: ["localhost:9200"]
    index: "orders"
  }
}
```

**方案三：业务双写**
- 代码中先写 MySQL，再写 ES（或发 MQ 异步写 ES）

---

## 🎯 Level 3：高级题

### 5. ES 集群架构如何设计？
**答案**：

**节点角色**：
| 角色 | 作用 | 配置 |
|------|------|------|
| Master 节点 | 集群管理、索引创建 | `node.master: true` |
| Data 节点 | 数据存储和检索 | `node.data: true` |
| Ingest 节点 | 数据预处理 | `node.ingest: true` |
| Coordinating | 查询路由（默认所有节点） | 默认 |

**生产集群设计**：
```
┌────────────────────────────┐
│  Master Nodes x3（HA）     │
│  (Master-eligible only)    │
└────────────────────────────┘
           ↓ discovery
┌────────────────────────────┐
│  Data Nodes x3+            │
│  （数据分片存储）            │
└────────────────────────────┘
           ↑
┌────────────────────────────┐
│  Ingest + Coordinating     │
│  （可选独立节点）            │
└────────────────────────────┘
```

**分片设计原则**：
- 分片数 = 数据量 / 单分片容量（建议 30-50GB/分片）
- 副本数 ≥ 1（容灾，可读）
- 分片过多 → 查询开销大；过少 → 无法横向扩展

### 6. 如何优化 ES 查询性能？
**答案**：

**索引层优化**：
```json
{
  "settings": {
    "number_of_shards": 5,
    "number_of_replicas": 1,
    "refresh_interval": "30s",
    "translog": {
      "durability": "async",
      "sync_interval": "30s"
    }
  }
}
```

**查询层优化**：
1. **使用 filter 代替 query**（可缓存）
2. **合理使用 keyword vs text**
3. **分页优化**：`search_after` / `scroll` 代替深分页
4. **减少返回字段**：`_source` 过滤
5. **聚合优化**：避免全量聚合，使用 `filter` + 分桶

---

## 🎯 Level 4：专家题

### 7. 设计一个亿级商品搜索系统（ES 部分）
**答案**：

**索引设计**：
```json
PUT /products
{
  "mappings": {
    "properties": {
      "productId": {"type": "keyword"},
      "name": {"type": "text", "analyzer": "ik_max_word"},
      "categoryName": {"type": "keyword"},
      "price": {"type": "float"},
      "brandName": {"type": "keyword"},
      "tags": {"type": "keyword"},
      "salesCount": {"type": "integer"},
      "onSale": {"type": "boolean"},
      "createTime": {"type": "date"}
    }
  },
  "settings": {
    "number_of_shards": 10,
    "number_of_replicas": 1
  }
}
```

**查询设计（多路检索 + 排序）**：
```json
{
  "query": {
    "bool": {
      "must": [
        {"match": {"name": "iPhone 15 手机"}},
        {"term": {"onSale": true}}
      ],
      "filter": [
        {"range": {"price": {"gte": 3000}}},
        {"terms": {"categoryName": ["手机", "数码"]}}
      ]
    }
  },
  "sort": [
    {"salesCount": {"order": "desc"}},
    {"_score": {"order": "desc"}}
  ],
  "from": 0,
  "size": 20,
  "highlight": {
    "fields": {"name": {}}
  }
}
```

**架构**：
```
商品写入 → MySQL → Canal → RocketMQ → ES
查询 → Gateway → SearchService → Redis(热词/结果缓存) → ES
                                              ↑
                                        结果缓存 60s
```

---

## 📖 学习资源

### 推荐项目
- [Elasticsearch 官方文档](https://www.elastic.co/guide/)
- [elasticsearch-analysis-ik](https://github.com/medcl/elasticsearch-analysis-ik) - 中文分词
- [Canal](https://github.com/alibaba/canal) - MySQL 同步

### 最佳实践
1. 生产环境至少 3 个主节点
2. 中文搜索必须集成 IK 分词器
3. 禁止 `LIKE '%xx%'` 查询（改用 match）
4. 深分页使用 search_after
5. 监控集群健康状态与分片分配