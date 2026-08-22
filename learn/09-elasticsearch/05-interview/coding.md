# Elasticsearch 代码面试题

## 代码题 1：DSL 查询综合编写

要求：搜索标题包含"手机"且价格在 1000-5000 的商品，按销量降序排序，每页 20 条，返回前 2 页，并高亮标题中的关键词。

```json
GET /product/_search
{
  "query": {
    "bool": {
      "must": [
        { "match": { "title": "手机" } }
      ],
      "filter": [
        { "range": { "price": { "gte": 1000, "lte": 5000 } } }
      ]
    }
  },
  "highlight": {
    "pre_tags": ["<span class='highlight'>"],
    "post_tags": ["</span>"],
    "fields": { "title": {} }
  },
  "sort": [
    { "sales_count": { "order": "desc" } }
  ],
  "from": 0,
  "size": 20
}
```

**考点**：bool 查询组合（must + filter）、范围查询、高亮配置、排序、分页。

**延伸**：如果要求第二页，`from` 改为 20；如果数据量过大（超过 10000 条），需改用 `search_after` 避免深度分页性能问题。

---

## 代码题 2：Mapping 设计

### 商品索引 Mapping

```json
PUT /product
{
  "settings": {
    "number_of_shards": 5,
    "number_of_replicas": 1,
    "analysis": {
      "analyzer": {
        "ik_analyzer": {
          "tokenizer": "ik_max_word"
        }
      }
    }
  },
  "mappings": {
    "properties": {
      "productId":   { "type": "keyword" },
      "title":       { "type": "text", "analyzer": "ik_analyzer" },
      "description": { "type": "text", "analyzer": "ik_analyzer" },
      "category":    { "type": "keyword" },
      "brand":       { "type": "keyword" },
      "price":       { "type": "double" },
      "tags":        { "type": "keyword" },
      "salesCount":  { "type": "integer" },
      "createTime":  { "type": "date", "format": "yyyy-MM-dd HH:mm:ss" },
      "isActive":    { "type": "boolean" }
    }
  }
}
```

### 博客索引 Mapping

```json
PUT /blog
{
  "settings": {
    "number_of_shards": 3,
    "number_of_replicas": 1,
    "analysis": {
      "analyzer": {
        "ik_analyzer": {
          "tokenizer": "ik_max_word"
        }
      }
    }
  },
  "mappings": {
    "properties": {
      "id":         { "type": "keyword" },
      "title":      { "type": "text", "analyzer": "ik_analyzer" },
      "content":    { "type": "text", "analyzer": "ik_analyzer" },
      "author":     { "type": "keyword" },
      "tags":       { "type": "keyword" },
      "viewCount":  { "type": "integer" },
      "status":     { "type": "keyword" },
      "publishAt":  { "type": "date", "format": "yyyy-MM-dd HH:mm:ss" }
    }
  }
}
```

**考点**：字段类型选择（text vs keyword）、中文分词配置、日期格式、settings 中 shards/replicas 配置。

---

## 代码题 3：聚合分析

### 价格区间统计

```json
GET /product/_search
{
  "size": 0,
  "aggs": {
    "price_ranges": {
      "range": {
        "field": "price",
        "ranges": [
          { "key": "平价",     "from": 0,     "to": 100 },
          { "key": "中等",     "from": 100,   "to": 500 },
          { "key": "高端",     "from": 500,   "to": 2000 },
          { "key": "旗舰",     "from": 2000 }
        ]
      }
    }
  }
}
```

### 按时间统计订单数

```json
GET /orders/_search
{
  "size": 0,
  "aggs": {
    "orders_over_time": {
      "date_histogram": {
        "field": "orderDate",
        "calendar_interval": "month",
        "format": "yyyy-MM",
        "min_doc_count": 1
      },
      "aggs": {
        "total_amount": {
          "sum": { "field": "amount" }
        }
      }
    }
  }
}
```

**考点**：range 聚合区间定义、date_histogram 时间间隔与格式、子聚合嵌套、`size: 0` 只返回聚合结果。

---

## 代码题 4：Spring Data Elasticsearch 代码

### 实体定义

```java
@Document(indexName = "product")
public class ProductDoc {
    @Id
    private String productId;
    @MultiField(
        mainField = @Field(type = FieldType.Text, analyzer = "ik_max_word"),
        otherFields = {
            @InnerField(suffix = "keyword", type = FieldType.Keyword)
        }
    )
    private String title;
    @Field(type = FieldType.Text, analyzer = "ik_max_word")
    private String description;
    @Field(type = FieldType.Keyword)
    private String brand;
    @Field(type = FieldType.Double)
    private Double price;
    @Field(type = FieldType.Date, format = DateFormat.custom, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
```

### Repository 层

```java
public interface ProductRepository extends ElasticsearchRepository<ProductDoc, String> {
    List<ProductDoc> findByTitle(String title);
    Page<ProductDoc> findByPriceBetween(double min, double max, Pageable pageable);
}
```

### Service 层搜索

```java
@Service
public class ProductSearchService {
    @Autowired
    private ElasticsearchRestTemplate template;

    public Page<ProductDoc> search(String keyword, int page, int size) {
        NativeSearchQuery query = new NativeSearchQueryBuilder()
            .withQuery(QueryBuilders.multiMatchQuery(keyword, "title^3", "description"))
            .withFilter(QueryBuilders.rangeQuery("price").gte(0))
            .withPageable(PageRequest.of(page, size))
            .withSorts(Sort.by(Sort.Direction.DESC, "salesCount"))
            .withHighlightBuilder(new HighlightBuilder()
                .preTags("<em>").postTags("</em>")
                .field("title"))
            .build();
        return template.search(query, ProductDoc.class);
    }
}
```

**考点**：注解映射（`@Document`、`@Field`、`@MultiField`）、Repository 继承、NativeSearchQuery 构建、高亮在 Spring Data 中的配置方式。