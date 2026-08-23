# Elasticsearch 速查卡 📋

> 面试前30分钟扫一遍，一页纸覆盖核心考点

## 🏷️ 核心概念速记

| 概念 | 一句话解释 | 常见陷阱 |
|------|-----------|----------|
| 倒排索引 | 词条(term) → 文档ID 列表，正排的反向 | 中文分词需 IK，词条粒度决定召回率 |
| 文档/索引 | 索引(index)=逻辑库，类型(_type) 已废弃(7.0 移除) | 一个索引一种映射，别把不同类型混一个索引 |
| 分片 (shard) | 数据物理分片，主分片数量建索引时定死 | 主分片数不可改，新建索引需规划好 |
| 副本 (replica) | 分片的冗余副本，提供读 + 故障恢复 | 副本过多浪费资源，写性能下降 |
| DSL 查询 | JSON 风格查询语言，match(分词)/term(精确) | term 查 text 字段永远不命中，必须 keyword 字段 |
| match vs match_phrase | match 分词后任意命中；phrase 要求词序连续 | 精确短语/高亮场景用 phrase，注意 slop 参数 |
| 聚合 (agg) | 对结果分组统计：terms/bucket、avg/metrics | bucket 默认返回 top10，size 调大开销大增 |
| 集群状态 | green(全正常) / yellow(主分片可用，副本缺) / red(有主分片不可用) | yellow/red 都要查原因：磁盘/副本/重启中 |
| 写入流程 | 客户端 → 协调节点 → 主分片 (写) → 同步副本 → refresh 可见 | refresh(1s) 才是"索引可见"；delete 是标记删除 |
| 段 (segment) | Lucene 最小存储单元，不可变，合并释放空间 | 段合并消耗 IO，须监控 merge 速率 |
| 集群拓扑 | master 选举(master-eligible) + 数据节点 + 协调节点 + 可独立拆分 | 集群 3 节点以下无脑用 all-in-one，规模再拆分角色 |

## 🔧 常用命令/API

```json
// bool 查询模板（组合过滤）
{
  "query": {
    "bool": {
      "must": [
        { "match": { "title": "elasticsearch 入门" } }
      ],
      "filter": [
        { "term": { "status": "PUBLISHED" } },
        { "range": { "publish_time": { "gte": "2024-01-01" } } }
      ],
      "must_not": [
        { "term": { "deleted": true } }
      ],
      "should": [
        { "match": { "tags": "后端" } }
      ],
      "minimum_should_match": 1
    }
  },
  "from": 0,
  "size": 10,
  "sort": [ { "publish_time": "desc" }, "_score" ],
  "_source": ["id", "title", "publish_time"]
}
```

```json
// 聚合查询：按分类统计 + 每组 top5
{
  "size": 0,
  "aggs": {
    "by_category": {
      "terms": { "field": "category.keyword", "size": 10 },
      "aggs": {
        "top_products": {
          "top_hits": { "size": 5, "_source": ["name", "price"] }
        },
        "avg_price": { "avg": { "field": "price" } }
      }
    }
  }
}
```

```json
// mapping 设计关键点（new index 一次到位）
{
  "mappings": {
    "properties": {
      "id":         { "type": "keyword" },
      "title":      { "type": "text", "analyzer": "ik_max_word", "fields": { "keyword": { "type": "keyword" } } },
      "content":    { "type": "text", "analyzer": "ik_max_word" },
      "status":     { "type": "keyword" },
      "price":      { "type": "double" },
      "publish_time": { "type": "date", "format": "yyyy-MM-dd HH:mm:ss||epoch_millis" },
      "tags":       { "type": "keyword" }
    }
  }
}
```

```bash
# 常见运维命令
GET  _cluster/health                     # 集群状态
GET  _cat/indices?v                      # 索引状态
GET  _cat/shards?v                       # 分片分布
GET  _cat/nodes?v&h=name,heap.percent,disk.percent,cpu
POST _cluster/reroute?retry_failed=true  # 重试失败分片分配
POST index/_search?scroll=1m             # 翻页查询(scroll)
POST _aliases                            # 索引别名(滚动/切换)
```

## 🎯 面试高频 TOP10

1. **Q: 倒排索引原理？** **A:** 分词器切词 → 词条字典(排序) → 每个词条对应 Posting List(文档ID链表) → 再用 FST 词典加速查找，O(1)~O(logN) 定位词条。
2. **Q: ES 写入流程？** **A:** client → 协调节点算路由 → 主分片写内存 buffer(同时写 translog) → refresh 生成 segment(可搜索) → 周期 flush 落盘 + translog 截断 → 副本异步复制；删除/更新是写 tombstone 新版本。
3. **Q: 查询优化思路？** **A:** ① filter 代替 must(缓存 + 不计分快) ② 精准字段用 keyword/term ③ 避免深分页 from+size(用 search_after) ④ 大文本用 match 控制分片扫描 ⑤ 合理索引数量、减少字段数、禁用动态 mapping。
4. **Q: 集群变 red 的原因？** **A:** ① 主分片所在的节点宕机/磁盘满 ② 分片分配失败(路由异常、磁盘水位) ③ 恢复期间(5 分钟默认超时未恢复) ④ 配置错误导致副本复制失败；逐项用 _cat/shards 定位。
5. **Q: 数据同步方案？** **A:** ① Canal/DeBezium 监听 binlog → 投递写入 ES ② 应用双写(事务后同步) ③ MQ 消费同步 ④ Logstash 定时全量/增量；核心保证最终一致 + 幂等写入。
6. **Q: term vs match 区别？** **A:** term 精确匹配整个词条(适合 keyword)；match 分析器分词后匹配任意词(doc 得分)；查询 text 字段必须 match，keyword 字段必须 term。
7. **Q: 深分页问题？** **A:** from+size 在大偏移量时开销大(每个分片都要排 to 序)；用 search_after(游标)+PIT 实现无限深滚动，scroll 适合全量导出。
8. **Q: ES 为什么近实时？** **A:** 写入先到 buffer → 1s 后 refresh 到 segment 才可搜；translog 只保证持久不保证立刻可见。
9. **Q: 分词器怎么选？** **A:** ik_max_word(最大细粒度，索引) / ik_smart(粗粒度，搜索)；英文 standard；自定义词典加 domain 专有词，避免切错。
10. **Q: 如何做高亮和模糊搜索？** **A:** 高亮 highlight(match + fragment)；模糊 fuzziness(AUTO/1/2 字符编辑距离)、通配符 wildcard 慎用(性能)、nGram 前缀索引。

## ⚠️ 常见坑 & 最佳实践

| ❌ 坑 | ✅ 正确做法 |
|-------|------------|
| 新加字段后期再补 mapping | 建索引前一次性设计好 mapping，映射字段一旦定义不可改类型(只能 add) |
| 把所有字段 dynamic: true | 显式声明字段，dynamic: false/strict 防脏数据膨胀 mapping |
| term 查 text 字段 | keyword 字段用 term，text 用 match |
| 深分页 from+size 100万 | search_after + PIT，禁止大偏移 |
| 分词器随便选导致召回差 | IK + 领域词典 + 索引/搜索分词不对称设计 |
| 大文本全存 _source | 大文档拆出去，_source 只存必要；禁用 mapping 索引部分字段(excludes) |
| 忽略段合并 | 监控 merge，错峰合并平衡 CPU/IO |
| 副本数撑满 | 读写分离：写入少副本(热) / 读多适当加副本，数节点容量上限 |
| 直接删索引重建 | 用别名(alias)+滚动索引(reindex) 平滑切换 |

## 📐 架构设计要点

- **容量规划**：单分片 30-50GB 为佳 → 预估数据量 → 主分片数 = 目标 / 单分片容量(定死) → 副本按读需求。
- **集群角色**：中小集群 3 主(master-eligible) + 数据/协调分离，热温冷架构分数据冷热。
- **索引管理**：按时间分索引(如 log-2024-01) + 别名查询 + retention 定期删除/冷存储。
- **写入链路**：业务系统直写 + 幂等；大批量用 bulk(5000-10000/批次) 单线程调优。
- **监控告警**：cluster health、节点磁盘水位(85% 警戒)、慢查询、queue 堆积、段数。

## 🔗 关联技术

- **Logstash/Beats**：数据采集管道，日志 → ES。
- **Kibana**：可视化、监控、开发工具(DLS)。
- **Canal/DeBezium**：binlog 同步组件，实现 DB → ES 增量同步。
- **Redis**：热门搜索词/搜索建议前缀，与 ES 组合。
- **RocketMQ**：数据变更事件投递，触发 ES 更新，削峰处理。