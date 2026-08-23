# 06 · RedisVL 向量存储与检索：从 Redis Stack 到 HNSW 索引

> Embedding 模型产出的向量需要被高效存储与检索。项目选用 **Redis Stack** 作为向量数据库，通过 **RedisVL**（Redis Vector Library）与 LangChain 无缝集成，实现毫秒级语义搜索。
>
> **对应项目：** `src/smart_search/config/tools.py`（`get_vector_store`）

---

## 一、基础概念

### 1.1 Redis 的演进：从缓存到向量数据库

```
传统 Redis（单机版）              Redis Stack（扩展版）
┌──────────────────────┐      ┌──────────────────────────────┐
│ String  字符串         │      │ String / Hash / List / Set  │
│ Hash    哈希           │      │ + JSON 文档                  │
│ List    列表           │      │ + Search (倒排全文检索)      │
│ Set     集合           │      │ + Vector (向量检索)  ← 本篇  │
│ ZSet    有序集合        │      │ + Time Series 时间序列      │
└──────────────────────┘      └──────────────────────────────┘
```

**Redis Stack** 在传统 Redis 基础上增加了模块化扩展能力，其中 **RediSearch** 模块提供了向量检索能力。

### 1.2 什么是 RedisVL

[RedisVL](https://redisvl.com/) 是 Redis 官方推出的**向量检索库**，为开发者提供：

| 能力 | 说明 |
|------|------|
| 向量索引管理 | 创建/删除/查询 HNSW、FLAT 索引 |
| 文档管理 | 存储含向量的文档（Hash/JSON） |
| 语义搜索 | 向量相似度搜索（KNN） |
| LangChain 集成 | 作为 LangChain 的 VectorStore 实现 |

### 1.3 为什么选 Redis 做向量库

| 对比项 | Redis Stack | Milvus | Pinecone | ES |
|--------|------------|--------|----------|-----|
| 学习成本 | 低（Redis 已懂） | 高（独立系统） | 低（SaaS） | 中 |
| 部署成本 | 低（单实例） | 高（集群） | 高（付费） | 高 |
| 一致性 | 强 | 事件最终 | 最终 | 最终 |
| 查询延迟 | ~1ms | ~10ms | ~10ms | ~50ms |
| 中文电商场景 | 适合演示/中规模 | 大规模生产 | 适合云端 | 已有总量 |

**项目选择 Redis 的核心原因：** 原有项目已有 Redis 基础设施，复用学习成本低；RedisVL 提供官方 LangChain 集成，接入成本几乎为零。

---

## 二、进阶机制

### 2.1 RedisVectorStore —— LangChain 的统一向量库接口

LangChain 定义了统一的 `VectorStore` 抽象，Redis 是其中一个实现：

```python
from langchain_redis import RedisConfig, RedisVectorStore

config = RedisConfig(
    index_name=settings.INDEX_NAME,   # 索引名，如 sku_idx
    redis_url=settings.REDIS_URL,     # Redis 连接
)
vector_store = RedisVectorStore(
    embeddings=self.get_embeddings(),  # Embedding 实例（上一篇）
    config=config,
)
```

**统一的 VectorStore 接口：**

| 方法 | 功能 | 本项目使用位置 |
|------|------|--------------|
| `add_documents(docs, ids)` | 批量写入文档 | `vector_sync_service.py` |
| `similarity_search(query, k)` | 语义相似搜索 | `search_service.py` |
| `delete(ids)` | 按 ID 删除 | 数据更新时 |

**核心价值：** 上层业务代码只面向 `VectorStore` 接口编程。如果未来把 Redis 换成 Milvus，只需要改 `get_vector_store()` 一行，业务代码零修改。

### 2.2 向量检索的核心原理 —— KNN + HNSW

**暴力搜索（FLAT）：** 遍历所有向量，计算与查询向量的距离（余弦/欧氏），选出最近的 K 个。O(n) 复杂度，数据量大时太慢。

**HNSW（Hierarchical Navigable Small World，分层可导航小世界图）：**

是一种**近似最近邻（ANN）** 索引算法，专门解决海量向量检索的性能问题。

```
层级 2 (最稀疏)     ●─────●
                    │     │
层级 1             ●──●──●──●
                   │  │  │  │
层级 0 (最稠密)    ●─●─●─●─●─●  ← 实际存储所有向量

搜索流程：
  1. 从最高层随机入口开始
  2. 每层贪心搜索，找到最相似的点
  3. 下降到下一层继续搜索
  4. 到达最底层时得到近似最近邻

特点：
  - 检索速度快（对数复杂度）
  - 召回率略低于暴力搜索（~95%）
  - 适合大数据量、低延迟场景
```

**项目配置：** RedisVL 默认使用 HNSW 索引，可通过配置调整 `M`（每层最大连接数）和 `ef_construction`（建索引时的搜索范围），权衡召回率和内存。

### 2.3 Hash vs JSON —— 向量文档的两种存储格式

RedisVL 支持两种存储格式：

| 格式 | 说明 | 查询能力 |
|------|------|---------|
| **Hash** | 字段值平坦存储 | 简单字段过滤 |
| **JSON** | 嵌套结构 | 复杂过滤、聚合 |

项目通过 `RedisConfig(index_name=...)` 创建索引，默认使用 Hash 格式存储商品文档，metadata 以字段形式平铺。

---

## 三、项目现场

### 3.1 向量检索在搜索链路中的位置

```
用户查询 "5000元以下续航强的华为手机"
    │
    ▼
search_service.py 中的 vector_search_tool
    │
    ▼
vector_store.similarity_search(query, k=10)
    │  1. Embedding 把查询转为向量 (BGE-M3, 1024维)
    │  2. Redis Stack 执行 HNSW ANN 搜索
    │  3. 计算与所有商品向量的余弦距离
    │  4. 返回 TOP-10 最相似的商品文档
    ▼
docs = [Document(page_content, metadata), ...]
    │
    ▼
文本拼接后注入 LLM 上下文
```

### 3.2 数据写入（索引构建）

```python
# vector_sync_service.py 中
batch_docs = []
for doc in doc_iterator:
    # ... 文本切片 ...
    if len(batch_docs) >= BATCH_SIZE:  # 每 100 条一批
        doc_ids = [self._generate_doc_id(d) for d in batch_docs]
        self.vector_store.add_documents(documents=batch_docs, ids=doc_ids)  # 批量写入
        batch_docs.clear()

# 收尾不足一批的数据
if batch_docs:
    self.vector_store.add_documents(documents=batch_docs, ids=doc_ids)
```

**设计要点：**
1. **批量写入**（每批 100 条）— 减少网络往返，提高吞吐
2. **幂等 ID**（md5(商品id + 内容)）— 重复同步不会产生重复文档
3. **自动创建索引** — `add_documents` 首次调用时若索引不存在会自动创建

### 3.3 数据查询（语义搜索）

```python
# search_service.py 中
@tool
def vector_search_tool(query: str) -> str:
    """商品向量检索工具，获取相关商品资料。"""
    docs = self.vector_store.similarity_search(query, k=10)
    return "\n".join([f"{doc.page_content} | meta:{doc.metadata}" for doc in docs])
```

**搜索逻辑：**
1. 接收自然语言查询
2. Embedding 转为向量
3. HNSW 索引 ANN 搜索，top-k=10
4. 返回商品文档拼接字符串（page_content + metadata）

**为什么要把检索结果拼接成字符串？** Agent 的 tool 返回需要是文本，LLM 才能理解；拼接的格式便于 LLM 解析商品信息。

---

## 四、Java 对照

### 4.1 Spring Data Redis + 向量检索

Java 生态中没有现成的 RedisVL-for-Java 等价物，但可以通过 Spring Data Redis + RediSearch 命令实现：

```java
// 依赖: RedisOM (Redis Object Mapping) 或直接 Lettuce 命令

@Service
public class VectorSearchService {

    private final StringRedisTemplate redisTemplate;

    // 创建向量索引（SQL 风格的 FT.CREATE 命令）
    public void createIndex() {
        redisTemplate.execute(connection -> {
            connection.execute("FT.CREATE", "sku_idx",
                "ON", "HASH",
                "PREFIX", "1", "sku:",
                "SCHEMA",
                "name", "TEXT", "WEIGHT", "1.0",
                "embedding", "VECTOR", "HNSW", "6", "TYPE", "FLOAT32",
                "DIM", "1024", "DISTANCE_METRIC", "COSINE");
            return null;
        });
    }

    // 向量搜索
    public List<Map<Object, Object>> similaritySearch(List<Float> queryVector, int k) {
        String hex = toHexString(queryVector);  // 向量转 HEX 编码
        return (List<Map<Object, Object>>) redisTemplate.execute(connection -> {
            // FT.SEARCH sku_idx "*=>[KNN 10 @embedding $query_vec]" PARAMS 2 query_vec hex
            List<?> result = connection.execute("FT.SEARCH", "sku_idx",
                "*=>[KNN " + k + " @embedding $query_vec]",
                "PARAMS", "2", "query_vec", hex,
                "SORTBY", "__embedding_score", "LIMIT", "0", String.valueOf(k));
            return parseResult(result);
        });
    }

    private String toHexString(List<Float> vec) {
        // Float32 数组转 HEX 字符串
        ByteBuffer buf = ByteBuffer.allocate(vec.size() * 4);
        vec.forEach(buf::putFloat);
        return Base64.getEncoder().encodeToString(buf.array());
    }
}
```

### 4.2 对照总结

| 维度 | Python (RedisVL) | Java (Spring Data Redis) |
|------|-----------------|------------------------|
| 创建索引 | `RedisConfig(index_name=...)` | `FT.CREATE` 命令 |
| 写入文档 | `add_documents(docs, ids)` | `HSET` + `FT.ADD` |
| 向量搜索 | `similarity_search(query, k)` | `FT.SEARCH ... KNN` |
| 集成方式 | LangChain 生态 | Redis OM / 手写命令 |
| 复杂度 | 低（封装好） | 中（需要自己写命令） |

---

## 五、最小可复现示例

### 5.1 完整向量检索流程

```python
# vector_store_demo.py
# 需要: pip install langchain-redis langchain-openai redisvl
import os
from langchain_redis import RedisConfig, RedisVectorStore
from langchain_openai import OpenAIEmbeddings
from langchain_core.documents import Document

def demo_vector_store():
    """演示 Redis 向量库的写入与查询"""

    # 1. 创建 Embedding 实例
    embeddings = OpenAIEmbeddings(
        base_url="https://api.siliconflow.cn/v1",
        api_key=os.getenv("SILICONFLOW_API_KEY"),
        model="BAAI/bge-m3",
    )

    # 2. 创建向量库
    config = RedisConfig(
        index_name="demo_products",
        redis_url=os.getenv("REDIS_URL", "redis://localhost:6379"),
    )
    store = RedisVectorStore(embeddings=embeddings, config=config)

    # 3. 写入商品数据
    products = [
        Document(page_content="华为Pura 70 Ultra 512GB 星芒黑", metadata={"id": 1, "price": 6999}),
        Document(page_content="华为Mate 60 Pro 昆仑玻璃版", metadata={"id": 2, "price": 6499}),
        Document(page_content="苹果iPhone 16 Pro Max 256GB", metadata={"id": 3, "price": 9999}),
        Document(page_content="小米14 Ultra 徕卡光学", metadata={"id": 4, "price": 6499}),
        Document(page_content="华为充电器 66W 快充", metadata={"id": 5, "price": 129}),
    ]
    store.add_documents(products, ids=[str(i) for i in range(1, 6)])

    # 4. 语义搜索
    results = store.similarity_search("华为旗舰手机", k=3)
    for doc in results:
        print(f"  → {doc.metadata['id']}: {doc.page_content} (price={doc.metadata['price']})")

    # 清理（可选）
    # store.delete_index()
```

### 5.2 向量索引原理验证

```python
def test_vector_distance_concepts():
    """验证向量检索的核心概念：余弦相似度"""
    import numpy as np

    def cosine_sim(a: list, b: list) -> float:
        a, b = np.array(a), np.array(b)
        return float(np.dot(a, b) / (np.linalg.norm(a) * np.linalg.norm(b)))

    # 二维空间演示
    query = [1, 0]          # 查询向量 (相当于"手机")
    phone = [0.9, 0.1]      # 华为手机
    laptop = [0.1, 0.9]     # 笔记本
    banana = [-1, 0]        # 香蕉

    print("余弦相似度:")
    print(f"  query vs phone:  {cosine_sim(query, phone):.4f}  ← 最相似")
    print(f"  query vs laptop: {cosine_sim(query, laptop):.4f}")
    print(f"  query vs banana: {cosine_sim(query, banana):.4f}  ← 最不相似")

    # 语义理解：即使"华为"关键词不匹配，"xel精工手表"也会匹配"手表"
    # 这就是 EMbedding 相对 ES 关键词匹配的核心优势
```

---

## 六、面试要点

### Q1: 为什么选择 Redis 作为向量数据库？

**回答思路：** 分点：1) 复用现有 Redis 基础设施，零额外运维；2) RedisVL 提供官方 LangChain 集成，接入成本低；3) HNSW 索引支持百万级向量、毫秒级检索，满足演示/中规模场景；4) 一致性高于独立向量数据库；5) 缺点是超大吞吐、复杂过滤场景弱于 Milvus（可以留一句"生产数据量大时需评估迁移"）。

### Q2: HNSW 索引和暴力搜索的区别？

**回答思路：** 暴力搜索遍历所有向量精确计算距离，准确率 100% 但 O(n) 复杂度。HNSW 构建多层跳跃图结构，从高层粗筛选逐步下探到低层精确定位，检索速度 O(log n)，召回率约 95%，是"速度 vs 精度"的权衡。

### Q3: LangChain 的 VectorStore 抽象有什么价值？

**回答思路：** 屏蔽向量库差异。业务代码只面向 `add_documents` / `similarity_search` 接口编程，切换底层实现（Redis→Milvus→Pinecone）只改一行工厂代码。这是"面向接口编程"的典型实践。

### Q4: 向量检索存在哪些局限？

**回答思路：**
1. **召回精确度**：语义相近但属性不同（如"华为手机壳"收到"华为手机"查询）
2. **属性过滤弱**：传统向量库难以精确过滤 `price > 5000 AND brand = 华为`
3. **冷启动成本**：新数据需先 Embedding（网络调用）
4. **维度灾难**：高维向量存储和计算开销大

### Q5: 向量检索 + 传统检索如何混合使用？

**回答思路：** Hybrid Search 混合检索：向量检索（召回语义相关）+ ES 关键词检索（精确匹配）+ 倒排/属性过滤，最后用 RRF（Reciprocal Rank Fusion）加权融合，取两个来源的并集重排。这是工业级搜索的标配方案。

---

> **下一篇：** [07-LANGCHAIN-AGENT.md —— LangChain Agent 机制：create_agent + Tool + 结构化输出](./07-LANGCHAIN-AGENT.md)
>
> 从"工具调用"到"Agent 决策"，看 LangChain 如何让 LLM 自主决定何时检索、如何回答。