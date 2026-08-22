# 02 · Spring AI Embedding + 向量存储：Schema 向量化与语义检索

> 文本→向量→检索。看 Spring AI 的 EmbeddingModel 和 VectorStore 如何将数据库 Schema 信息向量化，实现语义级别的表名检索。
>
> **对应项目：** `text2sql/text2sql-ai`

---

## 一、基础概念

### 1.1 为什么需要 Schema 向量化

Text2SQL 的核心挑战是找到"用户查询对应哪些数据库表"：

```
用户输入: "查询本月销售额前10的商品"
→ 需要知道: 涉及 orders(订单表) + order_items(订单明细) + products(商品表)
→ 传统方法: 关键词匹配 "销售" → "sale" 字段，但表名可能是 "orders"
→ 向量方法: "销售" 的语义向量 → 匹配到 "orders" 表的注释 "订单销售记录"
```

**Schema 向量化 = 将表名、字段名、注释转为向量，用语义匹配代替关键词匹配。**

### 2.2 项目中的向量化链路

```
入库阶段:                                  查询阶段:
MySQL Schema                               用户查询
    │                                          │
    ▼                                          ▼
SchemaEnhancer                             EmbeddingService
    │                                          │
    ▼                                          ▼
Schema → 文本拼接 → 向量化 → VectorStore   查询向量 → VectorStore.similarity()
    │                                          │
    ▼                                          ▼
VectorStore (内存/Redis/PGVector)          TOP-K 相似 Schema
```

---

## 二、进阶机制

### 2.1 EmbeddingService

```java
@Service
@RequiredArgsConstructor
public class EmbeddingService {
    private final EmbeddingModel embeddingModel;

    public float[] generateEmbedding(String text) {
        EmbeddingResponse response = embeddingModel.embedForResponse(List.of(text));
        return response.getResults().get(0).getOutput();
    }

    public double cosineSimilarity(float[] v1, float[] v2) {
        // 计算余弦相似度
        double dotProduct = 0.0, norm1 = 0.0, norm2 = 0.0;
        for (int i = 0; i < v1.length; i++) {
            dotProduct += v1[i] * v2[i];
            norm1 += v1[i] * v1[i];
            norm2 += v2[i] * v2[i];
        }
        return dotProduct / (Math.sqrt(norm1) * Math.sqrt(norm2));
    }
}
```

### 2.2 VectorStoreService —— Schema 存储与检索

```java
@Service
@RequiredArgsConstructor
public class VectorStoreService {
    private final VectorStore vectorStore;
    private final EmbeddingModel embeddingModel;

    // 存储 Schema（入库）
    public void storeSchema(MSchema schema) {
        String schemaText = convertSchemaToText(schema);
        Document document = new Document(
            schemaText,
            Map.of("table_name", schema.getTableName(),
                   "table_comment", schema.getTableComment())
        );
        vectorStore.add(List.of(document));
    }

    // 检索相似 Schema（查询）
    public List<RetrievedSchema> searchSimilarSchemas(String query, int topK) {
        SearchRequest request = SearchRequest.builder()
            .query(query)
            .topK(topK)
            .similarityThreshold(0.5)
            .build();
        List<Document> results = vectorStore.similaritySearch(request);
        // 解析结果...
    }

    // Schema 转文本
    private String convertSchemaToText(MSchema schema) {
        StringBuilder sb = new StringBuilder();
        sb.append("表名: ").append(schema.getTableName()).append("\n");
        sb.append("表注释: ").append(schema.getTableComment()).append("\n");
        sb.append("字段:\n");
        for (MSchemaColumn col : schema.getColumns()) {
            sb.append("  - ").append(col.getName())
              .append(" (").append(col.getType()).append(")")
              .append(": ").append(col.getComment()).append("\n");
        }
        return sb.toString();
    }
}
```

---

## 三、面试要点

### Q1: Spring AI 的 VectorStore 支持哪些后端？项目用了哪个？

**回答思路：** Spring AI 的 VectorStore 抽象支持多种后端：内存（SimpleVectorStore）、Redis、PGVector、Milvus、Pinecone、Chroma。项目中使用的是依赖注入的 VectorStore 实现，具体后端取决于配置。这种抽象和 LangChain 的 VectorStore 接口设计思路一致。

### Q2: 为什么需要将 Schema 文本化后再向量化？

**回答思路：** Embedding 模型处理的是自然语言文本，不是结构化数据。将 Schema 拼接为"表名: orders, 表注释: 订单销售记录, 字段: ..."的文本形式，让 Embedding 模型理解表的结构和语义。查询时用户输入的也是自然语言，两者在同一语义空间做相似度匹配。

---

> **下一篇：** [03-RAG-RETRIEVAL.md —— RAG 检索增强：混合检索 + 融合排序](./03-RAG-RETRIEVAL.md)
>
> 检索是 RAG 的核心。看项目如何用向量+关键词双重检索和融合排序，精准找到与用户查询最相关的数据库表。