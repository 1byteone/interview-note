# 03 · RAG 检索增强：混合检索 + 融合排序

> RAG 的核心是"检索"。看项目如何用向量检索 + 关键词检索 + 融合排序三层策略，精准找到与用户查询最相关的数据库表。
>
> **对应项目：** `text2sql/text2sql-ai`

---

## 一、基础概念

### 1.1 为什么需要混合检索

| 检索方式 | 优点 | 缺点 | 场景 |
|---------|------|------|------|
| **向量检索** | 语义理解，能匹配同义词 | 需要 Embedding 模型，有冷启动 | 用户用自然语言描述 |
| **关键词检索** | 精确匹配，无需模型 | 无法理解语义 | 用户明确提到表名 |

**混合检索 = 向量 + 关键词，取两者之长。**

---

## 二、进阶机制

### 2.1 RAGRetrievalService —— 混合检索

```java
@Service
@RequiredArgsConstructor
public class RAGRetrievalService {
    private final VectorStoreService vectorStoreService;
    private final TableRetrievalService tableRetrievalService;
    private final SQLExampleLibrary sqlExampleLibrary;
    private final SchemaEnhancer schemaEnhancer;

    @Cacheable(value = "rag-retrieval", key = "#query + '-' + #topK")
    public List<MSchema> retrieveRelevantSchemas(String query, int topK) {
        log.info("RAG检索相关Schema: {}", query);

        // 1. 向量检索（语义相似）
        List<RetrievedSchema> vectorResults =
            vectorStoreService.searchSimilarSchemas(query, topK);

        // 2. 关键词检索（精确匹配）
        List<String> keywordResults =
            tableRetrievalService.retrieveRelevantTables(query, topK);

        // 3. 融合结果（去重 + 排序）
        Set<String> uniqueTables = new LinkedHashSet<>();
        // 向量结果优先
        vectorResults.stream()
            .map(RetrievedSchema::getTableName)
            .forEach(uniqueTables::add);
        // 关键词结果补充
        keywordResults.stream()
            .filter(t -> !uniqueTables.contains(t))
            .forEach(uniqueTables::add);

        // 4. 获取完整 Schema
        return uniqueTables.stream()
            .map(schemaEnhancer::generateMSchema)
            .collect(Collectors.toList());
    }
}
```

### 2.2 SQL 示例检索

```java
// 检索相似的 SQL 示例（Few-shot）
public List<PromptBuilder.SQLExample> retrieveRelevantExamples(
        String query, int topK) {
    return sqlExampleLibrary.searchSimilarExamples(query, topK);
}
```

---

## 三、面试要点

### Q1: RAGRetrievalService 的 @Cacheable 缓存解决什么问题？

**回答思路：** 同一查询短时间内多次请求时，避免重复 Embedding 和检索。缓存 key 是 `query + '-' + topK`，不同查询不同缓存。但缓存粒度需要权衡——太细（每次查询都不同）缓存命中率低，太粗（粗泛的 key）可能返回过期数据。

### Q2: 向量检索和关键词检索的结果怎么融合排序？

**回答思路：** 项目使用**向量优先 + 关键词补充**策略：向量结果排在前面（语义匹配更准确），关键词去重后补充在后面（提高召回率）。更复杂的融合可以用 RRF（Reciprocal Rank Fusion）加权排序。

---

> **下一篇：** [04-PROMPT-SCHEMA.md —— Prompt 工程与 Schema 增强：M-Schema、Few-shot、SQL 生成](./04-PROMPT-SCHEMA.md)
>
> 有了检索结果，如何让 LLM 生成正确的 SQL？看 Prompt 工程和 Schema 增强的设计。