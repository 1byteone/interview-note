# Mini-Blog：用 ES 实现博客全文搜索

## 项目简介

这是一个独立的练手小项目：不依赖任何业务后端，直接使用 Elasticsearch 为博客文章提供全文搜索能力，覆盖索引设计、数据同步、搜索 API 开发全流程，是理解 ES 核心概念的轻量实战。

- 技术栈：Spring Boot 3.x + Elasticsearch 7.17 + Lombok
- 功能范围：全文搜索、关键词高亮、分页、标签聚合
- 难度：★★☆☆☆，适合作为 ES 入门后的第一个完整项目

---

## 索引设计

blog 索引 mapping 设计如下：

```json
{
  "settings": {
    "number_of_shards": 3,
    "number_of_replicas": 1,
    "analysis": {
      "analyzer": {
        "ik_analyzer": {
          "type": "custom",
          "tokenizer": "ik_max_word"
        }
      }
    }
  },
  "mappings": {
    "properties": {
      "id":        { "type": "keyword" },
      "title":     { "type": "text", "analyzer": "ik_analyzer", "fields": { "keyword": { "type": "keyword" } } },
      "content":   { "type": "text", "analyzer": "ik_analyzer" },
      "tags":      { "type": "keyword" },
      "author":    { "type": "keyword" },
      "createdAt": { "type": "date", "format": "yyyy-MM-dd HH:mm:ss" }
    }
  }
}
```

设计要点：

- `title` / `content` 使用 text 类型配合 IK 分词器，支持中文全文检索
- `title` 增加 `.keyword` 子字段，兼顾精确排序与聚合
- `tags`、`author` 用 keyword，用于过滤和聚合
- `createdAt` 用 date 类型，支持按时间范围查询和排序

---

## 数据同步方案

### 方案一：Logstash 同步（推荐演示用）

```conf
input {
  jdbc {
    jdbc_driver_library => "/path/mysql-connector.jar"
    jdbc_driver_class => "com.mysql.jdbc.Driver"
    jdbc_connection_string => "jdbc:mysql://localhost:3306/blog"
    jdbc_user => "root"
    jdbc_password => "123456"
    jdbc_paging_enabled => true
    statement => "SELECT id, title, content, tags, author, created_at FROM article"
  }
}
output {
  elasticsearch {
    hosts => ["http://localhost:9200"]
    index => "blog"
    document_id => "%{id}"
  }
}
```

### 方案二：代码写入（推荐项目内使用）

在服务中封装 `BlogIndexService`，文章发布或更新时同步写入：

```java
public void indexArticle(Article article) {
    BlogDoc doc = BlogDoc.from(article);
    IndexRequest request = new IndexRequest("blog")
            .id(doc.getId())
            .source(doc.toJson(), XContentType.JSON);
    esClient.index(request, RequestOptions.DEFAULT);
}
```

生产环境建议：全量同步（定时） + 增量同步（Logstash 定时轮询或 Canal 监听 binlog）双保险。

---

## 搜索 API

### 多字段搜索 + 高亮 + 分页

```json
{
  "query": {
    "multi_match": {
      "query": "Spring Boot",
      "fields": ["title^3", "content"]
    }
  },
  "highlight": {
    "pre_tags": ["<b>"], "post_tags": ["</b>"],
    "fields": { "title": {}, "content": {} }
  },
  "from": 0,
  "size": 10,
  "sort": [ { "createdAt": { "order": "desc" } } ]
}
```

### 标签聚合

```json
{
  "aggs": { "tag_agg": { "terms": { "field": "tags", "size": 20 } } }
}
```

### Java 代码示例（RestHighLevelClient）

```java
SearchRequest request = new SearchRequest("blog");
SearchSourceBuilder source = new SearchSourceBuilder();
source.query(QueryBuilders.multiMatchQuery(keyword, "title^3", "content"));
source.highlighter(new HighlightBuilder()
        .preTags("<b>").postTags("</b>")
        .field("title").field("content"));
source.from(page * size).size(size);
source.sort("createdAt", SortOrder.DESC);
request.source(source);
SearchResponse response = esClient.search(request, RequestOptions.DEFAULT);
```

---

## 项目结构建议

```
mini-blog/
├── pom.xml
└── src/main/java/com/example/miniblog/
    ├── MiniBlogApplication.java
    ├── config/          # ES 客户端配置（连接信息、RestClient）
    ├── doc/             # ES 文档实体（BlogDoc）
    ├── repository/      # 数据访问层（封装搜索、聚合 DSL）
    ├── service/         # 业务层（索引同步、搜索逻辑）
    ├── controller/      # REST API（/search、/suggest、/suggest/tags）
    └── dto/             # 请求/响应对象、分页对象
```

建议 API 设计：

- `GET /api/blog/search?keyword=&page=&size=` 搜索 + 高亮 + 分页
- `GET /api/blog/tags` 返回标签聚合统计（博客分类侧边栏）
- `POST /api/blog/index` 手动触发单篇文章索引（调试用）

完成本项目的关键是先手写 DSL 在 Kibana Dev Tools 里验证，再翻译为 Java 代码，避免直接写代码调试成本过高的问题。