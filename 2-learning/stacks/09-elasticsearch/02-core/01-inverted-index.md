# 倒排索引与映射

> 理解 Elasticsearch 的核心引擎——倒排索引，以及如何通过 Mapping 定义文档结构，是掌握 ES 搜索的基础。

---

## 1. 正向索引 vs 倒排索引

### 1.1 正向索引（Forward Index）

正向索引是传统关系型数据库（如 MySQL）的默认索引方式：**文档 ID → 词项**。以 MySQL 的 B+Tree 为例，每一行数据对应一个文档 ID，通过主键索引快速定位到整行数据。

```
文档1: "Elasticsearch 是搜索引擎"
文档2: "搜索引擎基于倒排索引"
文档3: "Java 后端开发常用 ES"

正向索引（文档 → 词项）：
文档1 → [Elasticsearch, 是, 搜索引擎]
文档2 → [搜索引擎, 基于, 倒排索引]
文档3 → [Java, 后端开发, 常用, ES]
```

正向索引的局限：当需要搜索包含"搜索引擎"的文档时，必须扫描所有文档内容（全表扫描），无法快速定位。

### 1.2 倒排索引（Inverted Index）

倒排索引是**词项 → 文档 ID** 的映射结构。ES 在写入文档时，先对文本内容进行分词，将每个词项（Term）映射到包含该词项的文档列表（Posting List）中。

```
倒排索引（词项 → 文档）：
Elasticsearch  → [文档1]
搜索引擎      → [文档1, 文档2]
倒排索引      → [文档2]
Java          → [文档3]
后端开发      → [文档3]
ES            → [文档3]
```

搜索"搜索引擎"时，ES 直接通过 Term Dictionary 定位到该词项，返回文档1和文档2，复杂度为 O(1) 或 O(logN)，远优于全表扫描。

### 1.3 对比总结

| 维度 | 正向索引（MySQL B+Tree） | 倒排索引（ES） |
|------|--------------------------|----------------|
| 映射方向 | 文档 → 词项 | 词项 → 文档 |
| 适合场景 | 精确查询、范围查询、事务 | 全文搜索、相关性排序 |
| 搜索方式 | 按主键/索引字段定位行 | 按词项定位文档集合 |
| 部分匹配 | 不支持（LIKE 走全表） | 天然支持 |
| 相关性排序 | 无 | TF-IDF / BM25 打分 |
| 数据更新 | 原地更新，支持事务 | 段合并，不可变段 |

**核心结论**：MySQL 适合结构化数据的事务查询，ES 适合非结构化文本的全文搜索。两者互补，而非替代。

---

## 2. 倒排索引的存储结构

### 2.1 Term Dictionary（词项字典）

所有词项（Term）按字典序排序后形成的词典。ES 将 Term Dictionary 按前缀压缩（FST 有限状态转换器）存储在内存中，实现快速查找。

```
Term Dictionary（内存中）：
"elasticsearch"  → 指向 Posting List 的指针
"es"             → 指向 Posting List 的指针
"java"           → 指向 Posting List 的指针
"搜索引擎"       → 指向 Posting List 的指针
"后端开发"       → 指向 Posting List 的指针
```

- **FST 结构**：比 HashMap 更省内存（共享前缀），查询复杂度 O(len(term))
- **内存常驻**：Term Dictionary 常驻内存，保证查询性能
- **延迟加载**：Segment 被打开时，Term Dictionary 才加载到内存

### 2.2 Posting List（倒排列表）

每个词项对应的文档 ID 列表及其在文档中的位置信息。

```
Posting List（磁盘存储）：
"搜索引擎" → [ (docId=1, freq=1, pos=[3]),  (docId=2, freq=1, pos=[1]) ]
"java"     → [ (docId=3, freq=1, pos=[1]) ]
```

Posting List 包含：
- **文档 ID**：包含该词项的文档编号
- **词频（TF）**：词项在文档中出现的次数，用于 BM25 打分
- **位置（Position）**：词项在文档中的偏移位置，支持短语查询（match_phrase）
- **偏移（Offset）**：词项在原文中的字符偏移，用于高亮显示

### 2.3 Segment（段）

ES 将倒排索引写入磁盘时，按 Segment（段）组织。每个 Segment 是一个完整的、独立的倒排索引，包含自己的 Term Dictionary 和 Posting List。

```
索引（Index）
  ├── Segment 0 （已落盘，不可变）
  │   ├── Term Dictionary（内存 FST）
  │   ├── Posting List（磁盘）
  │   └── 其他元数据（字段长、归一化因子等）
  ├── Segment 1 （已落盘，不可变）
  │   └── ...
  ├── Segment 2 （内存中，未刷新）
  │   └── ...
  └── Segment N （合并中）
```

Segment 的特点：
- **不可变性**：写入后不再修改，只有读操作
- **分段合并**：后台定期合并小 Segment 为大 Segment，减少文件数
- **删除标记**：文档删除不会立即移除，而是标记为删除，合并时清理
- **更新策略**：更新文档 = 标记旧文档删除 + 写入新文档

---

## 3. 分词器（Analyzer）

分词器是将文本切分为词项的组件，决定了倒排索引的质量。

### 3.1 分词器组成

一个分词器由三部分组成：

```
Analyzer = Character Filters（字符过滤） + Tokenizer（分词器） + Token Filters（词项过滤）
```

- **Character Filters**：预处理字符（如去掉 HTML 标签）
- **Tokenizer**：核心分词逻辑，将文本切分为词项
- **Token Filters**：对词项做过滤/转换（如转小写、去停用词）

### 3.2 常用分词器

| 分词器 | 特点 | 适用场景 | 示例： "I love Elasticsearch" |
|--------|------|----------|------|
| **Standard** | 默认分词，按空格/标点切分，转小写 | 英文文本，通用场景 | `[i, love, elasticsearch]` |
| **Whitespace** | 仅按空格切分，不转小写 | 精确匹配，代码搜索 | `[I, love, Elasticsearch]` |
| **Keyword** | 不分词，整体作为一个词项 | 精确匹配，ID 字段 | `[I love Elasticsearch]` |
| **Simple** | 按非字母字符切分，转小写 | 纯英文文本 | `[i, love, elasticsearch]` |
| **IK (ik_smart)** | 中文智能切分，最粗粒度 | 中文搜索，兼顾性能 | `[Elasticsearch, 是, 搜索引擎]` |
| **IK (ik_max_word)** | 中文最细粒度切分 | 中文搜索，召回率优先 | `[Elasticsearch, 是, 搜索, 搜索引擎, 引擎]` |
| **Pinyin** | 拼音分词 | 拼音搜索、模糊匹配 | `[elasticsearch, es]` |

### 3.3 IK 中文分词

IK 是 ES 最常用的中文分词器，支持自定义词典。

```json
// 测试 IK 分词效果
POST _analyze
{
  "analyzer": "ik_smart",
  "text": "华为Mate60 Pro是一款高端智能手机"
}
// 结果：["华为", "Mate60", "Pro", "是", "一款", "高端", "智能手机"]

POST _analyze
{
  "analyzer": "ik_max_word",
  "text": "华为Mate60 Pro是一款高端智能手机"
}
// 结果：["华为", "Mate60", "Pro", "是", "一款", "高端", "智能", "智能手机", "手机"]
```

**自定义词典配置**：

```xml
<!-- IKAnalyzer.cfg.xml -->
<properties>
  <!-- 主词典扩展 -->
  <entry key="ext_dict">custom/mydict.dic</entry>
  <!-- 停用词词典 -->
  <entry key="ext_stopwords">custom/stopword.dic</entry>
</properties>
```

```text
# custom/mydict.dic
华为Mate60
鸿蒙系统
大模型
AI智能商城
```

> **注意**：修改词典后需要重启 ES 节点或调用 reload API 热加载。自定义词典的优先级高于内置词典，确保业务专有名词被正确切分。

### 3.4 自定义分词器

```json
PUT my_index
{
  "settings": {
    "analysis": {
      "char_filter": {
        "html_strip": { "type": "html_strip" }
      },
      "tokenizer": {
        "my_ik": { "type": "ik_smart" }
      },
      "filter": [
        "lowercase",
        { "type": "stop", "stopwords": ["的", "了", "是"] }
      ],
      "analyzer": {
        "my_analyzer": {
          "type": "custom",
          "char_filter": ["html_strip"],
          "tokenizer": "my_ik",
          "filter": ["lowercase", "my_stop"]
        }
      }
    }
  }
}
```

---

## 4. 映射（Mapping）

Mapping 定义了文档中每个字段的类型、分词方式、索引策略，决定了 ES 如何存储和检索数据。

### 4.1 Dynamic Mapping（动态映射）

ES 在写入文档时，自动检测字段值并推断类型，无需预先定义 Mapping。

```json
// 写入一条数据，ES 自动创建映射
POST product/_doc/1
{
  "name": "华为手机",
  "price": 5999,
  "stock": 100,
  "created_at": "2026-08-22T10:00:00Z",
  "is_active": true
}

// ES 自动推断的映射
GET product/_mapping
// 结果：
// name       → text（附带 keyword 子字段）
// price      → float
// stock      → integer
// created_at → date
// is_active  → boolean
```

**自动推断规则**：

| JSON 类型 | 推断的 ES 字段类型 |
|-----------|-------------------|
| 字符串（无数字特征） | text + keyword 多字段 |
| 字符串（匹配日期格式） | date |
| 数字 | float 或 long |
| 布尔值 | boolean |
| 对象 | object |
| 数组 | 按第一个元素类型推断 |

**动态映射的坑**：
- 字符串默认同时映射为 text 和 keyword，占用额外存储空间
- 数字类型统一推断为 float/long，可能精度不足
- 已有字段类型不能修改（只能重建索引或添加新字段）
- 字段数量过多会导致映射膨胀（mapping explosion）

### 4.2 Explicit Mapping（显式映射）

显式定义 Mapping，精确控制每个字段的类型和分词器，是生产环境的推荐做法。

```json
PUT product
{
  "mappings": {
    "dynamic": "strict",          // 严格模式：未知字段拒绝写入
    "properties": {
      "product_id": {
        "type": "keyword"         // 商品 ID，精确匹配
      },
      "name": {
        "type": "text",
        "analyzer": "ik_smart",
        "fields": {
          "keyword": {            // 子字段，支持精确匹配和聚合
            "type": "keyword"
          }
        }
      },
      "description": {
        "type": "text",
        "analyzer": "ik_max_word" // 描述字段最细粒度分词，提高召回率
      },
      "price": {
        "type": "float"
      },
      "stock": {
        "type": "integer"
      },
      "tags": {
        "type": "keyword"         // 标签，精确匹配 + 聚合统计
      },
      "created_at": {
        "type": "date",
        "format": "yyyy-MM-dd HH:mm:ss||epoch_millis"
      },
      "is_active": {
        "type": "boolean"
      },
      "location": {
        "type": "geo_point"       // 地理位置，支持距离排序和范围查询
      }
    }
  }
}
```

**dynamic 参数取值**：
| 值 | 行为 | 推荐场景 |
|----|------|----------|
| `true` | 自动检测并添加新字段（默认） | 开发环境、日志场景 |
| `runtime` | 新字段定义为运行时字段，不索引 | 需要灵活 schema 但不想重索引 |
| `false` | 忽略新字段，不索引但可存储 | 中间过渡 |
| `strict` | 拒绝新字段写入，抛出异常 | **生产环境推荐** |

### 4.3 字段类型详解

| 类型 | 说明 | 索引/查询方式 | 适用场景 |
|------|------|--------------|----------|
| **text** | 全文文本，分词后索引 | 全文搜索（match、match_phrase） | 商品名称、描述、文章内容 |
| **keyword** | 精确值，不分词 | 精确查询（term）、聚合（terms）、排序 | ID、状态、标签、邮箱 |
| **integer** | 32 位整数 | 范围查询（range）、精确查询 | 库存、数量、评分 |
| **float** | 单精度浮点数 | 范围查询、聚合（avg、stats） | 价格、评分 |
| **boolean** | 布尔值 | 过滤查询（filter） | 是否上架、是否删除 |
| **date** | 日期类型 | 范围查询、日期直方图聚合 | 创建时间、更新时间 |
| **nested** | 嵌套对象数组 | 嵌套查询（nested query） | 订单商品明细、评论列表 |
| **join** | 父子关系 | has_child、has_parent 查询 | 分类与商品、问题和答案 |
| **object** | JSON 对象（默认） | 点号路径访问 | 地址信息、配置信息 |
| **geo_point** | 经纬度坐标 | 距离查询、地理范围 | 门店位置、用户地址 |

**text vs keyword 多字段设计**：

```json
{
  "name": {
    "type": "text",
    "analyzer": "ik_smart",
    "fields": {
      "keyword": { "type": "keyword" }   // name.keyword 用于精确匹配和聚合
    }
  }
}
```

搜索时 `name` 字段走全文搜索（分词匹配），排序和聚合时使用 `name.keyword`（精确值）。

**nested 类型示例**：

```json
{
  "mappings": {
    "properties": {
      "order_items": {
        "type": "nested",
        "properties": {
          "sku_id": { "type": "keyword" },
          "quantity": { "type": "integer" },
          "price": { "type": "float" }
        }
      }
    }
  }
}
```

> 使用 `nested` 而非 `object` 的原因是：ES 的 `object` 类型会将嵌套对象的字段值扁平化，导致跨对象的错误匹配。`nested` 保持每个对象的独立性。

---

## 总结

- **倒排索引**是 ES 实现全文搜索的核心，将文本切分为词项后建立词项 → 文档的映射，配合 BM25 评分实现相关性排序
- **Term Dictionary + Posting List + Segment** 三级结构，平衡了内存占用和查询性能
- **分词器**决定了索引质量，中文场景推荐 IK 分词器，可自定义词典补充业务专有名词
- **Mapping 设计**是 ES 使用中最关键的环节，生产环境应使用显式映射 + `dynamic: strict`，精确控制字段类型和分词方式

> 下一篇：[02-cluster-architecture.md](02-cluster-architecture.md) — 集群架构、节点角色、分片与副本、故障恢复