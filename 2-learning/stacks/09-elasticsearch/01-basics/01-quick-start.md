# Elasticsearch 快速入门

> 面向 Java 后端开发者的 ES 入门指南，目标是让你能启动 Elasticsearch + Kibana、创建索引并完成 CRUD、最后实现一个商品搜索的完整案例。

---

## 1. 什么是 Elasticsearch

Elasticsearch 是一个基于 Lucene 的分布式搜索和分析引擎，核心设计目标是 **全文搜索** 和 **实时分析**。它以 RESTful API 作为交互接口，天然适合作为微服务架构中的搜索层。

ES 的核心优势：

| 特性 | 说明 |
|------|------|
| 全文搜索 | 基于倒排索引，毫秒级返回搜索结果 |
| 分布式 | 自动分片、副本机制，支持 PB 级数据水平扩展 |
| 实时分析 | 聚合框架支持多维统计、时序分析 |
| Schema 灵活 | 动态映射（Dynamic Mapping）自动推断字段类型 |
| 生态完善 | 与 Logstash、Kibana 组成 ELK 技术栈 |

---

## 2. 安装（Docker）

### 2.1 使用 Docker Compose 部署 ES + Kibana

创建 `docker-compose.yml`：

```yaml
version: '3.8'
services:
  elasticsearch:
    image: docker.elastic.co/elasticsearch/elasticsearch:8.15.0
    container_name: es-node
    environment:
      - cluster.name=es-cluster
      - node.name=es-node
      - discovery.type=single-node          # 单节点模式
      - bootstrap.memory_lock=true
      - "ES_JAVA_OPTS=-Xms512m -Xmx512m"   # 内存限制
      - xpack.security.enabled=false        # 禁用安全认证（开发环境）
    ulimits:
      memlock:
        soft: -1
        hard: -1
    volumes:
      - es-data:/usr/share/elasticsearch/data
    ports:
      - "9200:9200"                         # ES  REST API
      - "9300:9300"                         # ES  节点间通信
    networks:
      - es-net

  kibana:
    image: docker.elastic.co/kibana/kibana:8.15.0
    container_name: kibana
    environment:
      - ELASTICSEARCH_HOSTS=http://elasticsearch:9200
    ports:
      - "5601:5601"
    networks:
      - es-net
    depends_on:
      - elasticsearch

volumes:
  es-data:

networks:
  es-net:
```

启动：

```bash
docker-compose up -d
```

验证安装：

```bash
# 检查 ES 是否启动
curl http://localhost:9200

# 预期返回
# {
#   "name" : "es-node",
#   "cluster_name" : "es-cluster",
#   "cluster_uuid" : "...",
#   "version" : { "number" : "8.15.0" },
#   "tagline" : "You Know, for Search"
# }

# 打开 Kibana http://localhost:5601
```

---

## 3. 核心概念

### 3.1 与关系型数据库的类比

| Elasticsearch | 关系型数据库（MySQL） | 说明 |
|---------------|----------------------|------|
| Index（索引） | Database（数据库） | 一个逻辑命名空间，包含一类文档 |
| Type（类型） | Table（表） | **ES 7.x 起废弃**，一个 Index 仅一个类型 `_doc` |
| Document（文档） | Row（行） | 最小的数据单元，JSON 格式 |
| Field（字段） | Column（列） | 文档中的一个键值对 |
| Mapping（映射） | Schema（表结构） | 定义字段类型和分析规则 |
| Shard（分片） | 分区表 | 物理分片，水平扩展的基础单元 |
| Replica（副本） | 主从同步 | 分片副本，提供高可用和读扩展 |

### 3.2 核心架构概念

```
Index（索引）                      ← 逻辑概念
  └── Shard 0（主分片）            ← 物理数据，实际存储
        ├── Segment 1              ← Lucene 段（不可变）
        ├── Segment 2
        └── ...
  └── Shard 1（主分片）
        └── ...
  └── Replica 0（Shard 0 的副本）  ← 只读，故障时升主
  └── Replica 1（Shard 1 的副本）
```

**关键概念说明：**

| 概念 | 说明 |
|------|------|
| **Index（索引）** | 一类文档的集合，相当于 MySQL 的 Database。命名全小写 |
| **Document（文档）** | JSON 格式的数据单元，相当于 MySQL 的一行记录 |
| **Shard（分片）** | 索引水平拆分的物理单元。ES 7.x 默认每索引 1 个分片 |
| **Replica（副本）** | 分片的冗余副本，用于故障转移和读负载均衡 |
| **Mapping（映射）** | 定义字段类型、分词器、是否索引等元信息 |
| **倒排索引** | ES 的核心数据结构，词项 → 文档的映射关系 |

---

## 4. REST API 操作

ES 所有操作通过 RESTful API 进行，使用 HTTP 动词表示操作语义。

### 4.1 索引操作

```bash
# 创建索引（指定 3 个分片、2 个副本）
PUT /products
{
  "settings": {
    "number_of_shards": 3,
    "number_of_replicas": 2
  }
}

# 查看所有索引
GET /_cat/indices?v

# 删除索引
DELETE /products
```

### 4.2 文档 CRUD

```bash
# 创建文档（自动生成 ID）
POST /products/_doc
{
  "name": "Apple iPhone 15",
  "category": "手机",
  "price": 6999,
  "brand": "Apple",
  "stock": 100,
  "description": "Apple iPhone 15 128GB 蓝色 支持 Face ID"
}

# 创建文档（指定 ID）= 如 ID 已存在则覆盖
PUT /products/_doc/1
{
  "name": "Apple iPhone 15 Pro",
  "category": "手机",
  "price": 8999,
  "brand": "Apple",
  "stock": 50
}

# 获取文档
GET /products/_doc/1

# 更新文档（局部更新，仅修改指定字段）
POST /products/_update/1
{
  "doc": {
    "price": 8499,
    "stock": 45
  }
}

# 删除文档
DELETE /products/_doc/1
```

### 4.3 搜索文档

```bash
# 简单搜索（带查询参数）
GET /products/_search?q=name:iPhone

# 按条件搜索
GET /products/_search
{
  "query": {
    "match": {
      "name": "iPhone"
    }
  }
}
```

---

## 5. 最小案例：商品索引创建与搜索

### 场景

AI 商城需要一个商品搜索功能，用户输入关键词后能快速找到匹配的商品，支持按品牌、价格区间筛选。

### 5.1 创建索引并设置 Mapping

```bash
# 创建商品索引，显式定义 Mapping
PUT /products
{
  "settings": {
    "number_of_shards": 2,
    "number_of_replicas": 1
  },
  "mappings": {
    "properties": {
      "name":        { "type": "text", "analyzer": "ik_max_word" },
      "category":    { "type": "keyword" },
      "brand":       { "type": "keyword" },
      "price":       { "type": "double" },
      "stock":       { "type": "integer" },
      "description": { "type": "text", "analyzer": "ik_max_word" },
      "tags":        { "type": "keyword" },
      "created_at":  { "type": "date" },
      "rating":      { "type": "float" }
    }
  }
}
```

> 注：`ik_max_word` 是 IK 中文分词器，需先安装（见 02-core/02 章节）。未安装时可先用 `standard` 分词器。

### 5.2 批量写入商品数据

```bash
# 批量写入多条商品数据
POST /_bulk
{ "index": { "_index": "products", "_id": 1 } }
{ "name": "Apple iPhone 15 Pro Max", "category": "手机", "brand": "Apple", "price": 9999, "stock": 30, "description": "6.7英寸 OLED 屏幕 A17 Pro 芯片 钛金属", "tags": ["旗舰", "5G"], "rating": 4.8, "created_at": "2025-01-15" }
{ "index": { "_index": "products", "_id": 2 } }
{ "name": "Huawei Mate 60 Pro", "category": "手机", "brand": "Huawei", "price": 7999, "stock": 80, "description": "华为旗舰手机 卫星通话 昆仑玻璃 鸿蒙系统", "tags": ["旗舰", "卫星通信"], "rating": 4.9, "created_at": "2025-01-10" }
{ "index": { "_index": "products", "_id": 3 } }
{ "name": "Xiaomi 14 Ultra", "category": "手机", "brand": "Xiaomi", "price": 5999, "stock": 120, "description": "徕卡光学镜头 骁龙8 Gen3 澎湃OS", "tags": ["旗舰", "拍照"], "rating": 4.7, "created_at": "2025-02-01" }
{ "index": { "_index": "products", "_id": 4 } }
{ "name": "Sony WH-1000XM5 头戴式降噪耳机", "category": "耳机", "brand": "Sony", "price": 2499, "stock": 200, "description": "旗舰级降噪耳机 30小时续航 佩戴舒适", "tags": ["降噪", "蓝牙"], "rating": 4.9, "created_at": "2025-01-20" }
{ "index": { "_index": "products", "_id": 5 } }
{ "name": "MacBook Pro 14英寸 M3 Pro", "category": "笔记本", "brand": "Apple", "price": 14999, "stock": 25, "description": "M3 Pro 芯片 18GB内存 512GB存储 Liquid Retina XDR", "tags": ["笔记本", "生产力"], "rating": 4.9, "created_at": "2025-03-01" }
```

### 5.3 搜索商品

```bash
# 搜索名称包含"手机"的商品
GET /products/_search
{
  "query": {
    "match": {
      "name": "手机"
    }
  }
}

# 按品牌精确查询 + 价格范围过滤
GET /products/_search
{
  "query": {
    "bool": {
      "filter": [
        { "term": { "brand": "Apple" } },
        { "range": { "price": { "gte": 5000, "lte": 15000 } } }
      ]
    }
  }
}

# 全文搜索：搜索"旗舰降噪耳机"
GET /products/_search
{
  "query": {
    "match": {
      "description": "旗舰降噪耳机"
    }
  }
}
```

### 5.4 使用 Java 客户端操作

```xml
<!-- Maven 依赖（ES 8.x Java Client） -->
<dependency>
    <groupId>co.elastic.clients</groupId>
    <artifactId>elasticsearch-java</artifactId>
    <version>8.15.0</version>
</dependency>
```

```java
@Configuration
public class ESConfig {

    @Bean
    public ElasticsearchClient elasticsearchClient() {
        RestClient restClient = RestClient.builder(
            HttpHost.create("http://localhost:9200")
        ).build();
        ElasticsearchTransport transport = new RestClientTransport(
            restClient, new JacksonJsonpMapper()
        );
        return new ElasticsearchClient(transport);
    }
}

// 搜索示例
@Service
public class ProductSearchService {

    @Resource
    private ElasticsearchClient client;

    public List<Product> searchByName(String keyword) throws IOException {
        SearchResponse<Product> response = client.search(s -> s
            .index("products")
            .query(q -> q
                .match(t -> t
                    .field("name")
                    .query(keyword)
                )
            ),
            Product.class
        );
        return response.hits().hits().stream()
            .map(Hit::source)
            .collect(Collectors.toList());
    }
}
```

---

## 总结

本章你学会了：

- Elasticsearch 核心概念：Index / Document / Shard / Replica / Mapping / 倒排索引
- 使用 Docker Compose 部署 ES + Kibana
- REST API 的 CRUD 操作（PUT / GET / POST / DELETE）
- 创建商品索引，设置 Mapping，批量写入数据并搜索
- Java 客户端的配置与基本使用

下一步：学习 [DSL 搜索语法](02-dsl-search.md)，掌握全文搜索、精确查询、复合查询和聚合分析。