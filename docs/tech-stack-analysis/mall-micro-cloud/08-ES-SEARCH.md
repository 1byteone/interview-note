# 08 · ES 搜索服务：Elasticsearch 商品搜索与分页

> AI 搜索之外，电商系统还需要传统的关键词搜索。Elasticsearch 提供倒排索引、分词、聚合分析等能力。
>
> **对应项目：** `mall-services/mall-es-service`

---

## 一、基础概念

### 1.1 ES 搜索 vs 向量搜索

| 对比项 | ES 关键词搜索 | RedisVL 向量搜索 |
|--------|-------------|-----------------|
| 匹配方式 | 倒排索引，关键词精确匹配 | 向量距离，语义相似度 |
| 搜索"手机" | 匹配"手机"字段 | 匹配"移动设备"、"智能手机"等 |
| 搜索"5000元以下华为" | 匹配价格 + 品牌字段 | 理解语义，匹配"中高端华为手机" |
| 适用场景 | 精确搜索、属性过滤 | 模糊搜索、语义理解 |
| 项目位置 | mall-es-service | mall-ai-search (Python) |

### 1.2 ES 搜索服务结构

```java
// Controller
@RestController
@RequestMapping("/api/search")
public class ProductController {
    @GetMapping("/product/page")
    public Result<Page<SkuInfo>> page(ProductQueryDTO queryDTO) {
        // 关键词 + 品牌 + 分类 + 价格范围 + 分页
        return productService.search(queryDTO);
    }
}
```

---

## 二、面试要点

### Q1: ES 搜索和向量搜索各自适合什么场景？

**回答思路：** ES 搜索**精确匹配+属性过滤**——用户知道要什么，通过品牌/分类/价格范围精确筛选。向量搜索**语义理解+模糊推荐**——用户用自然语言表达需求，系统理解意图后推荐。项目中两者互补：AI 搜索模式用向量检索做语义推荐，传统搜索模式用 ES 做精确筛选。

### Q2: ES 的倒排索引比 MySQL 的 like 查询快在哪里？

**回答思路：** MySQL 的 `LIKE '%keyword%'` 无法使用索引，需要全表扫描。ES 在写入时对文本分词，构建倒排索引（词 → 文档列表），查询时直接定位到包含该词的文档列表，不需要扫描。时间复杂度从 O(n) 降到 O(1)。

---

> **下一篇：** [09-ROCKETMQ.md —— RocketMQ 消息驱动：订单支付回调、库存同步、数据一致性](./09-ROCKETMQ.md)
>
> 看 RocketMQ 如何在订单支付、库存同步、搜索索引更新等场景中实现异步解耦和最终一致性。