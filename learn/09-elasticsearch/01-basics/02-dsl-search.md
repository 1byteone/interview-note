# DSL 搜索语法

> 面向 Java 后端开发者的 ES 查询 DSL 指南，覆盖全文搜索、精确查询、复合查询与聚合分析四大模块。学习后你能编写出 AI 商城商品搜索所需的大部分查询。

---

## 1. 查询 DSL 基础

ES 的查询 DSL（Domain Specific Language）是基于 JSON 的查询语言，所有查询都放在 `query` 关键字下。查询分为两大类：

| 类别 | 特点 | 典型查询 |
|------|------|----------|
| **Query Context**（查询上下文） | 计算 **相关性得分（_score）**，参与排序 | match, multi_match, bool 内 must/should |
| **Filter Context**（过滤上下文） | **不计算得分**，只做包含/排除判断，可走缓存，性能更高 | term, range, exists, bool 内 filter |

```json
GET /products/_search
{
  "query": {
    "match": { "name": "手机" }
  }
}
```

> **性能原则**：能放进 filter 的条件（品牌、价格、状态等精确条件）就不要放 query，过滤查询可被 ES 缓存。

---

## 2. 全文搜索

全文搜索针对 `text` 类型字段，会对查询词和文档字段做**分词**后匹配，适用于商品名称、描述等非结构化文本。

### 2.1 match — 分词匹配（最常用）

`match` 会把查询词分词后再去倒排索引中匹配，返回的文档按相关性得分排序。

```json
GET /products/_search
{
  "query": {
    "match": { "name": "旗舰手机" }
  }
}
```

「旗舰手机」被分词为「旗舰」「手机」，任一命中即可返回（默认 OR 逻辑），命中越多得分越高。

**控制匹配逻辑：**

```json
{
  "query": {
    "match": {
      "name": {
        "query": "旗舰降噪耳机",
        "operator": "and",        // and：所有词项都必须命中，更精确
        "minimum_should_match": "75%"  // 至少 75% 的词项命中
      }
    }
  }
}
```

### 2.2 match_phrase — 短语匹配

要求所有词按**给定顺序、相邻出现**（允许 slop 间隔）。

```json
{
  "query": {
    "match_phrase": {
      "description": {
        "query": "降噪耳机",
        "slop": 2        // 允许词项间最多隔 2 个词
      }
    }
  }
}
```

> 中文场景注意：分词后「降噪耳机」是否相邻取决于分词结果，通常用 match 做召回、match_phrase 做精排加权。

### 2.3 multi_match — 多字段匹配

在多个字段中同时搜索，最常见的应用是「商品名 + 描述」一起搜。

```json
{
  "query": {
    "multi_match": {
      "query": "iPhone 旗舰",
      "fields": ["name^3", "description", "tags^2"],
      "type": "best_fields"   // 默认：取匹配得分最高的字段
    }
  }
}
```

字段后加 `^3` 表示权重，`name` 命中的得分按 3 倍计算——商品名匹配比描述匹配更重要。

### 2.4 相似度排序

相关性得分由 **TF-IDF（词频-逆文档频率）** 或 BM25 算法计算，默认排序方式。理解 `boost` 权重即可应对绝大多数业务场景。

---

## 3. 精确查询

精确查询针对 `keyword`、数值、日期等**不分词**字段，进行完全匹配。

### 3.1 term / terms — 精确词条匹配

```json
// 单个值精确匹配（品牌）
{
  "query": {
    "term": { "brand": "Apple" }
  }
}

// 多值匹配（一个值命中即可）
{
  "query": {
    "terms": { "category": ["手机", "耳机"] }
  }
}
```

> ⚠ **高频坑**：`term` 查询 `text` 字段通常匹配不到——text 字段已被分词存储，词条值不完整。精确匹配必须用 `keyword` 子字段或 keyword 类型字段（见 Mapping 章节）。

### 3.2 range — 范围查询

适用于数值、日期范围过滤，常放在 `bool.filter` 中。

```json
{
  "query": {
    "bool": {
      "filter": [
        {
          "range": {
            "price": { "gte": 500, "lte": 8000 }
          }
        },
        {
          "range": {
            "created_at": {
              "gte": "2025-01-01",
              "lt": "2025-06-01"
            }
          }
        }
      ]
    }
  }
}
```

> 操作符：`gt`（大于）`gte`（大于等于）`lt`（小于）`lte`（小于等于）。

### 3.3 exists — 存在性查询

查询某字段**有值**（非 null、非空数组）的文档，常用于「筛选出有库存的商品」「找出资料缺失的用户」。

```json
{
  "query": {
    "exists": { "field": "description" }
  }
}
```

---

## 4. 复合查询（bool）

`bool` 查询用于组合多个查询条件，是最强大的查询类型，也是面试与本项目的高频考点。

| 子句 | 语义 | 评分 | 类比 SQL |
|------|------|------|----------|
| **must** | 必须满足，多个条件取 **AND** | 参与评分 | WHERE a AND b |
| **should** | 至少满足其一（配合 minimum_should_match） | 参与评分 | OR |
| **filter** | 必须满足，但不评分、可缓存 | 不评分 | WHERE，性能最优 |
| **must_not** | 必须不满足，排除 | 不评分 | WHERE NOT |

### 4.1 完整示例：AI 商城商品筛选

用户需求：「品牌是 Apple 或 Huawei、价格 4000-15000、有库存、名称包含'手机'，额外命中'旗舰'关键词可加分」。

```json
{
  "query": {
    "bool": {
      "must": [
        { "match": { "name": "手机" } }
      ],
      "should": [
        { "match": { "tags": "旗舰" } },
        { "match": { "description": "旗舰" } }
      ],
      "minimum_should_match": 1,       // should 至少命中 1 个（否则 should 变成加分项）
      "filter": [
        { "terms": { "brand": ["Apple", "Huawei"] } },
        { "range": { "price": { "gte": 4000, "lte": 15000 } } },
        { "range": { "stock": { "gt": 0 } } }
      ],
      "must_not": [
        { "term": { "status": "OFF_SHELF" } }
      ]
    }
  }
}
```

### 4.2 minimum_should_match 语义

- **bool 中只有 should**（无 must/filter）：默认至少匹配 1 个 should
- **bool 中有 must/filter**：默认 should 是可选加分项，`minimum_should_match: 1` 强制至少命中一个 should；也可设百分比如 `"75%"`

---

## 5. 聚合分析（Aggregations）

聚合用于对搜索结果做统计分析，**不改变查询结果**，与 query 平级放在 `aggs` 下。

### 5.1 terms — 分组统计（类似 GROUP BY）

统计各品牌的商品数量：

```json
GET /products/_search
{
  "size": 0,                       // 只关心聚合结果，不返回文档
  "aggs": {
    "brand_count": {
      "terms": { "field": "brand", "size": 10 }
    }
  }
}
```

返回：

```json
{
  "aggregations": {
    "brand_count": {
      "buckets": [
        { "key": "Apple", "doc_count": 2 },
        { "key": "Huawei", "doc_count": 1 }
      ]
    }
  }
}
```

### 5.2 avg — 平均值

```json
{
  "size": 0,
  "aggs": {
    "avg_price": {
      "avg": { "field": "price" }
    }
  }
}
```

### 5.3 extended_stats — 扩展统计

一次计算 count / min / max / avg / sum / **方差 / 标准差（std_deviation）**：

```json
{
  "size": 0,
  "aggs": {
    "price_stats": {
      "extended_stats": { "field": "price" }
    }
  }
}
```

### 5.4 date_histogram — 时间桶聚合

按时间分桶，常用于日志分析（按小时/天统计请求量）：

```json
{
  "size": 0,
  "aggs": {
    "daily_orders": {
      "date_histogram": {
        "field": "created_at",
        "calendar_interval": "day"     // minute / hour / day / week / month / year
      }
    }
  }
}
```

### 5.5 嵌套聚合：子聚合（Sub-Aggregation）

聚合可以嵌套，实现「先分组、再组内统计」：

```json
{
  "size": 0,
  "aggs": {
    "by_brand": {
      "terms": { "field": "brand" },
      "aggs": {
        "avg_price": { "avg": { "field": "price" } },
        "min_stock": { "min": { "field": "stock" } }
      }
    }
  }
}
```

---

## 6. 查询 + 聚合组合实战

先过滤出「价格 1000 以上的手机」，再按品牌分组统计数量与均价：

```json
GET /products/_search
{
  "size": 0,
  "query": {
    "bool": {
      "filter": [
        { "range": { "price": { "gt": 1000 } } },
        { "term": { "category": "手机" } }
      ]
    }
  },
  "aggs": {
    "by_brand": {
      "terms": { "field": "brand" },
      "aggs": {
        "avg_price": { "avg": { "field": "price" } }
      }
    }
  }
}
```

---

## 总结

本章你学会了：

- Query vs Filter 上下文：是否计算得分、是否走缓存
- 全文搜索三件套：match（分词匹配）、match_phrase（短语匹配）、multi_match + boost（多字段加权）
- 精确查询四件套：term / terms（词条匹配）、range（范围）、exists（存在性）
- bool 复合查询：must / should / filter / must_not 的组合与 minimum_should_match 语义
- 聚合分析：terms / avg / date_histogram / extended_stats 及嵌套子聚合

下一步：学习 [索引与映射](02-core/01-index-and-mapping.md)，掌握 text vs keyword 的映射原理与显式 Mapping 设计规范。