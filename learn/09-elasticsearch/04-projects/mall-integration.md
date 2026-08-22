# AI 商城商品搜索架构

## 系统架构概述

mall-es-service 是商城系统的核心搜索服务，基于 Elasticsearch 7.x 构建，提供商品全文搜索、搜索建议、聚合统计等功能。整体架构分为三层：

- **接入层**：Spring Boot 微服务，通过 Spring Data Elasticsearch 或 RestHighLevelClient 与 ES 集群交互
- **索引层**：商品索引（product_index）承载所有搜索与聚合请求
- **存储层**：ES 集群（3 节点，主分片 5 个，副本分片 1 个）

数据同步采用 Canal 监听 MySQL binlog，实时写入 ES，配合全量定时重建索引确保数据一致性。

---

## 多字段搜索与权重

商品搜索需要在多个字段中匹配关键词，且不同字段的重要程度不同：

```json
{
  "query": {
    "multi_match": {
      "query": "手机",
      "fields": [
        "name^3",
        "tags^2",
        "category_name^1.5",
        "description"
      ],
      "type": "best_fields",
      "tie_breaker": 0.3
    }
  }
}
```

- `name^3`：商品名称权重最高，匹配度最关键
- `tags^2`：标签次之，反映商品核心属性
- `category_name^1.5`：分类名称辅助匹配
- `description`：描述字段权重最低，作为补充召回

`type: best_fields` 取单个字段最高分，`tie_breaker` 将其他字段的得分部分累加，兼顾精确匹配与多字段覆盖。

---

## 搜索结果高亮

高亮让用户直观看到匹配位置，配置如下：

```json
{
  "highlight": {
    "pre_tags": ["<em class='hl-keyword'>"],
    "post_tags": ["</em>"],
    "fields": {
      "name": { "fragment_size": 50, "number_of_fragments": 1 },
      "description": { "fragment_size": 100, "number_of_fragments": 2 }
    }
  }
}
```

- `pre_tags` / `post_tags`：自定义高亮标签，便于前端样式控制
- `fragment_size`：片段长度，名称字段较短设为 50，描述字段较长设为 100
- `number_of_fragments`：最多返回的片段数，名称取 1 个，描述取 2 个

前端通过 CSS 设置 `.hl-keyword { color: #f50; font-weight: bold; }` 即可渲染红色高亮。

---

## 搜索建议（Completion Suggester）

搜索框输入时实时展示补全建议，基于 Completion Suggester 实现：

```json
{
  "mappings": {
    "properties": {
      "suggest": {
        "type": "completion",
        "analyzer": "ik_smart",
        "search_analyzer": "ik_smart",
        "preserve_separators": true,
        "preserve_position_increments": false
      }
    }
  }
}
```

写入数据时构建 suggest 字段：

```json
{
  "suggest": [
    { "input": ["华为手机", "华为 Mate", "Huawei"], "weight": 10 },
    { "input": ["苹果手机", "iPhone", "Apple"], "weight": 8 }
  ]
}
```

查询时使用：

```json
{
  "suggest": {
    "product_suggest": {
      "prefix": "华为",
      "completion": {
        "field": "suggest",
        "size": 5,
        "skip_duplicates": true
      }
    }
  }
}
```

`weight` 字段控制排序权重，热门商品设置更高权重，确保优先展示。

---

## 聚合统计

### 品牌聚合（terms）

```json
{
  "aggs": {
    "brand_agg": {
      "terms": { "field": "brand_name.keyword", "size": 20 }
    }
  }
}
```

### 价格区间聚合（range）

```json
{
  "aggs": {
    "price_range": {
      "range": {
        "field": "price",
        "ranges": [
          { "key": "0-500", "from": 0, "to": 500 },
          { "key": "500-1000", "from": 500, "to": 1000 },
          { "key": "1000-3000", "from": 1000, "to": 3000 },
          { "key": "3000+", "from": 3000 }
        ]
      }
    }
  }
}
```

### 销量统计（stats）

```json
{
  "aggs": {
    "sales_stats": {
      "stats": { "field": "sales_count" }
    }
  }
}
```

`stats` 聚合一次返回 count、min、max、avg、sum 五个统计值，适用于销量、评分等数值指标的概览分析。