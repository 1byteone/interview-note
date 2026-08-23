# Elasticsearch 高频考点速记

## 1. 倒排索引
倒排索引以词条（Term）为键，映射到包含该词条的文档 ID 列表（Posting List），实现快速全文检索。

## 2. 分词器
分词器将文本拆分为词条，中文场景默认使用 Standard 分词器（逐字切分），生产推荐 IK 分词器（ik_smart 粗粒度 / ik_max_word 细粒度）。

## 3. 分片（Shard）
分片是 ES 数据分布的最小单元，主分片数在索引创建后不可修改，副本分片可动态调整。

## 4. 副本（Replica）
副本分片提供高可用与读扩展，副本数可动态调整，增加副本提升查询吞吐量但降低写入性能。

## 5. 集群健康状态
green（所有分片正常）、yellow（主分片正常、副本未分配）、red（存在未分配的主分片）。

## 6. 路由（Routing）
默认 `hash(_id) % number_of_shards` 决定文档落入哪个分片，自定义 routing 可控制同一类文档分布在同一分片。

## 7. 写入流程
协调节点 → 转发主分片 → 主分片写入并同步副本 → 全部确认后返回成功（refresh 后可见）。

## 8. 查询流程
协调节点转发到所有相关分片 → 各分片本地查询 → 协调节点合并排序 → 取回文档（Query Then Fetch）。

## 9. 集群选举
7.x 之前基于 Zen Discovery，7.x 之后基于投票机制，需要半数以上节点同意才能成为 master。

## 10. 数据同步（ES 与 MySQL）
方案包括：Logstash 定时轮询、Canal 监听 binlog 实时推送、应用层双写（不推荐单独使用）。

## 11. 聚合（Aggregation）
分为桶聚合（terms、range、date_histogram）、指标聚合（avg、sum、stats、cardinality）、管道聚合（bucket_script）。

## 12. 深度分页
`from + size` 超过 10000 行时性能急剧下降，大分页场景使用 `search_after` 或 `scroll` API。

## 13. 全文搜索评分
ES 默认使用 BM25 算法计算相关性评分，影响评分的因素包括词频（TF）、逆文档频率（IDF）和字段长度归一化。

## 14. Mapping 映射
分为动态映射（自动推断类型）和显式映射（手动定义），生产环境建议显式定义 Mapping 避免字段类型错误。

## 15. 索引模板
Index Template 按匹配模式为新索引自动应用 settings 和 mappings，适合多索引场景（如日志按天分索引）。

## 16. 刷新（Refresh）
refresh 使新写入的文档可被搜索，默认每秒触发一次，可调整 `refresh_interval` 平衡写入性能与搜索实时性。

## 17. 刷盘（Flush）
flush 将内存中的 segment 持久化到磁盘并清空 translog，默认 30 分钟或 translog 达到阈值时触发。

## 18. 段合并（Merge）
多个小 segment 合并为大 segment，减少文件句柄数并优化查询性能，合并过程会消耗 IO 和 CPU。

## 19. Spring Boot 集成
使用 Spring Data Elasticsearch 或 RestHighLevelClient（7.17 后推荐 Elasticsearch Java Client），注意版本兼容性。

## 20. 性能优化
包括：批量写入（bulk）、调整 refresh_interval、增加副本数、合理设置分片数、使用 SSD 磁盘、启用慢查询日志。