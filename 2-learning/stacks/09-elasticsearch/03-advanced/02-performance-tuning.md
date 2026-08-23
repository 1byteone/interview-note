# Elasticsearch 性能调优实战

## 1. 概述

Elasticsearch 的性能调优贯穿索引写入、查询检索、数据生命周期管理以及硬件配置等多个层面。本文从写入优化、查询优化、索引生命周期管理、冷热数据架构和 JVM 调优五个维度，结合实际场景给出可落地的调优方案。

---

## 2. 写入优化

### 2.1 批量写入（Bulk API）

单条写入效率极低，应始终使用 Bulk API 批量提交：

```java
BulkRequest bulkRequest = new BulkRequest();
for (Product product : products) {
    bulkRequest.add(new IndexRequest("products")
        .id(product.getId().toString())
        .source(convertToJson(product), XContentType.JSON));
}
BulkResponse response = client.bulk(bulkRequest, RequestOptions.DEFAULT);
```

建议每批 500-1000 条或 5-15MB 数据，具体通过压测确定最佳值。

### 2.2 调整 refresh_interval

Elasticsearch 默认每秒 refresh 一次（生成新段），频繁 refresh 降低写入速度。批量导入时可临时关闭或调大间隔：

```json
PUT /products/_settings
{
  "index": {
    "refresh_interval": "30s",
    "number_of_replicas": 0
  }
}
```

导入完成后恢复设置：

```json
PUT /products/_settings
{
  "index": {
    "refresh_interval": "1s",
    "number_of_replicas": 1
  }
}
```

### 2.3 Translog 调优

Translog 用于保证数据不丢失，但 fsync 操作是写入瓶颈之一：

```json
PUT /products/_settings
{
  "index": {
    "translog": {
      "durability": "async",
      "sync_interval": "5s",
      "flush_threshold_size": "1024mb"
    }
  }
}
```

- `durability: async`：异步刷盘，写入性能提升数倍，但可能丢失 5 秒内的数据
- `flush_threshold_size`：增大触发 flush 的阈值，减少 segment 合并频率

### 2.4 多线程并发写入

利用多线程并行 Bulk 写入，每个线程独立创建 BulkRequest，注意控制线程数不超过 CPU 核数的 2-3 倍，避免 ES 端线程池打满。

---

## 3. 查询优化

### 3.1 Shard Request Cache

对重复的聚合查询启用分片请求缓存，显著提升查询性能：

```json
PUT /products/_settings
{
  "index": {
    "requests.cache.enable": true
  }
}
```

适用于聚合（aggregation）和过滤（filter）查询，但全文检索（query）默认不缓存。

### 3.2 路由（Routing）

自定义路由将数据路由到指定分片，避免广播查询：

```java
SearchRequest searchRequest = new SearchRequest("products");
searchRequest.routing("category_123");  // 按分类路由
```

创建索引时指定路由字段，写入和查询都使用相同的 routing key，可大幅降低查询涉及的 shard 数量。

### 3.3 字段过滤

仅返回需要的字段，减少网络传输和序列化开销：

```java
SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
sourceBuilder.fetchSource(new String[]{"id", "name", "price"}, null);
```

```json
GET /products/_search
{
  "_source": ["id", "name", "price"],
  "stored_fields": ["title"]
}
```

- `_source`：控制返回哪些 JSON 字段
- `stored_fields`：仅当字段在 mapping 中设置了 `store: true` 时生效

### 3.4 只返回必要字段

在搜索结果中不要返回大字段（如商品详情描述），只返回搜索列表页需要的字段，详情数据通过应用层缓存或二次查询获取。

---

## 4. 索引生命周期管理（ILM）

### 4.1 ILM Policy 配置

```json
PUT _ilm/policy/products_policy
{
  "policy": {
    "phases": {
      "hot": {
        "min_age": "0ms",
        "actions": {
          "rollover": {
            "max_size": "50gb",
            "max_age": "30d"
          }
        }
      },
      "warm": {
        "min_age": "30d",
        "actions": {
          "forcemerge": {
            "max_num_segments": 1
          },
          "shrink": {
            "number_of_shards": 1
          }
        }
      },
      "cold": {
        "min_age": "90d",
        "actions": {
          "freeze": {}
        }
      },
      "delete": {
        "min_age": "180d",
        "actions": {
          "delete": {}
        }
      }
    }
  }
}
```

### 4.2 各阶段说明

| 阶段 | 存储 | 操作 | 说明 |
|------|------|------|------|
| Hot | SSD | 读写 | 接收写入和查询，达到条件后 rollover |
| Warm | HDD | 只读 | forcemerge 减少段数，shrink 减少分片 |
| Cold | 低频存储 | 极少查询 | freeze 冻结索引，降低内存开销 |
| Delete | - | 删除 | 数据过期自动清理 |

### 4.3 创建索引模板关联 ILM

```json
PUT _index_template/products_template
{
  "index_patterns": ["products-*"],
  "template": {
    "settings": {
      "number_of_shards": 3,
      "number_of_replicas": 1,
      "index.lifecycle.name": "products_policy",
      "index.lifecycle.rollover_alias": "products"
    }
  }
}
```

---

## 5. 冷热数据架构

### 5.1 节点角色规划

| 节点类型 | 硬件 | 用途 |
|----------|------|------|
| Hot Node | NVMe SSD, 高 CPU | 接收实时写入，索引最近 30 天数据 |
| Warm Node | SATA SSD, 大内存 | 存储 30-90 天数据，只读查询 |
| Cold Node | HDD, 低频访问 | 存储 90 天以上数据，冻结索引 |

### 5.2 节点标签分配

```yaml
# elasticsearch.yml
node.roles: ["data", "ingest"]
node.attr.data_type: "hot"   # 或 "warm" / "cold"
```

ILM 政策中通过 `allocate` 动作将数据迁移到对应节点：

```json
"warm": {
  "actions": {
    "allocate": {
      "require": { "data_type": "warm" }
    }
  }
}
```

---

## 6. 硬件与 JVM 调优

### 6.1 Heap 50% 规则

ES 的 Heap 堆内存不要超过物理内存的 50%，剩余内存留给 Lucene 的 OS Page Cache（文件缓存）：

```yaml
# jvm.options
-Xms16g
-Xmx16g
```

### 6.2 不要超过 32GB

JVM 指针压缩在堆内存超过 32GB 时失效，导致内存浪费。即使机器有 128GB 内存，ES heap 也不应超过 31GB，剩余内存作为 OS Cache 加速查询。

### 6.3 禁用 Swap

Swap 会导致 ES 性能急剧下降，必须禁用：

```bash
# 临时禁用
sudo swapoff -a

# 永久禁用（注释 /etc/fstab 中的 swap 条目）
# 或通过系统配置
vm.swappiness = 1
```

### 6.4 其他 JVM 参数

```yaml
# 推荐配置
-XX:+UseG1GC                    # 使用 G1 垃圾回收器
-XX:MaxGCPauseMillis=200        # 最大 GC 暂停时间
-XX:InitiatingHeapOccupancyPercent=70
```

---

## 7. 总结

ES 性能调优需要从写入、查询、存储、硬件四个维度系统性地进行。写入阶段善用 Bulk 和异步 translog；查询阶段充分利用缓存和路由减少开销；通过 ILM 和冷热架构平衡性能与成本；JVM 层面遵循 50% 规则并禁用 Swap。实际调优应结合业务场景和压测数据，避免盲目套用参数。