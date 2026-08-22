# 检索策略

> 检索是 RAG 的核心环节。本章系统讲解向量检索、关键词检索（BM25）、混合检索（RRF 融合）以及 Reranker 精排，涵盖从基础到高级的完整检索策略体系。

---

## 1. 向量检索

### 1.1 什么是向量检索？

向量检索（Vector Search）是将用户查询和文档都转化为向量，通过计算向量相似度找到最相关的文档。

```python
from langchain_community.vectorstores import Chroma

# 向量检索基础用法
retriever = vector_store.as_retriever(
    search_type="similarity",      # 相似度检索
    search_kwargs={"k": 5},        # 返回 top-5
)
results = retriever.get_relevant_documents(query)
```

### 1.2 相似度阈值检索

```python
# 带阈值的检索 — 只返回相似度超过阈值的文档
retriever = vector_store.as_retriever(
    search_type="similarity_score_threshold",
    search_kwargs={
        "k": 10,
        "score_threshold": 0.7,  # 只返回相似度 >= 0.7 的文档
    },
)
```

### 1.3 MMR 检索（最大边际相关性）

MMR 在保证相关性的同时增加多样性，避免返回结果过于相似。

```python
retriever = vector_store.as_retriever(
    search_type="mmr",
    search_kwargs={
        "k": 5,
        "fetch_k": 20,      # 先取 top-20
        "lambda_mult": 0.5,  # 0=最大多样性, 1=最大相关性
    },
)
```

| 检索方式 | 优点 | 适用场景 |
|---------|------|---------|
| similarity | 相关性最高 | 精确问答 |
| similarity_score_threshold | 可控制质量底线 | 重要场景，宁可无结果也不要错误 |
| mmr | 结果多样性好 | 内容推荐、探索性查询 |

---

## 2. 关键词检索（BM25）

### 2.1 BM25 原理

BM25 是经典的**词袋模型**检索算法，基于词频（TF）和逆文档频率（IDF）计算相关性。

```
BM25 分数 = Σ IDF(q) × (TF(q,d) × (k1 + 1)) / (TF(q,d) + k1 × (1 - b + b × |d|/avgdl))

其中：
- q: 查询词
- d: 文档
- k1: 饱和度参数（默认 1.2~2.0）
- b: 长度归一化参数（默认 0.75）
```

**为什么需要 BM25？**

| 对比 | 向量检索 | BM25 |
|------|---------|------|
| 语义匹配 | "刚入职员工"→"employee" | 无法匹配 |
| 精确匹配 | "型号 A100" 可能匹配到 A101 | 精确命中"型号 A100" |
| 同义词 | 支持（"手机"→"移动电话"） | 不支持 |
| 长尾词/稀有词 | 向量可能不准确 | 精确命中 |

### 2.2 使用 Elasticsearch 实现 BM25

```python
from elasticsearch import Elasticsearch

es = Elasticsearch("http://localhost:9200")

# 创建索引（使用 BM25 相似度）
es.indices.create(
    index="products",
    body={
        "settings": {
            "similarity": {
                "default": {
                    "type": "BM25",
                    "k1": 1.2,
                    "b": 0.75,
                }
            }
        },
        "mappings": {
            "properties": {
                "title": {"type": "text", "analyzer": "ik_max_word"},
                "content": {"type": "text", "analyzer": "ik_max_word"},
            }
        }
    }
)

# BM25 检索
result = es.search(
    index="products",
    body={
        "query": {
            "match": {
                "content": "稻飞虱防治方法"
            }
        }
    }
)
```

### 2.3 使用 rank_bm25 库（纯 Python）

```python
from rank_bm25 import BM25Okapi

# 构建 BM25 索引
tokenized_corpus = [doc.split() for doc in documents]
bm25 = BM25Okapi(tokenized_corpus)

# 检索
query = "稻飞虱防治方法"
tokenized_query = query.split()
scores = bm25.get_scores(tokenized_query)
top_indices = sorted(range(len(scores)), key=lambda i: scores[i], reverse=True)[:5]
```

---

## 3. 混合检索

混合检索 = **向量检索 + BM25 关键词检索**，多路召回取长补短。

### 3.1 混合检索架构

```
用户问题
    ↓
    ├──→ 向量检索（语义相似） → top-30
    └──→ BM25 检索（精确匹配） → top-30
    ↓
    RRF 融合（融合排序）
    ↓
    top-10 候选
    ↓
    Reranker 精排
    ↓
    top-5 最终结果
```

### 3.2 实现代码

```python
from langchain_community.retrievers import BM25Retriever
from langchain.retrievers import EnsembleRetriever

# 1. 向量检索器
vector_retriever = vector_store.as_retriever(
    search_kwargs={"k": 30}
)

# 2. BM25 检索器
bm25_retriever = BM25Retriever.from_documents(documents)
bm25_retriever.k = 30

# 3. 混合检索器（RRF 融合）
ensemble_retriever = EnsembleRetriever(
    retrievers=[bm25_retriever, vector_retriever],
    weights=[0.4, 0.6],  # BM25 权重 0.4，向量权重 0.6
)

# 4. 检索
results = ensemble_retriever.get_relevant_documents("稻飞虱防治方法")
```

---

## 4. RRF 融合算法

### 4.1 RRF 公式

RRF（Reciprocal Rank Fusion）通过排名信息融合多个检索结果，而非分数。

```
score(doc) = Σ 1 / (k + rank_i(doc))

其中 k = 60（平滑参数），rank_i(doc) 是文档在第 i 个检索器中的排名
```

**为什么不加权平均分数？**
- 不同检索器分数尺度不同（余弦 0~1，BM25 0~100）
- 直接加权没意义
- RRF 对排名敏感、对分数不敏感 → 天然鲁棒

### 4.2 Python 实现

```python
def rrf_fusion(vector_results, bm25_results, k=60):
    """RRF 融合：将两个检索结果按排名融合"""
    scores = {}
    
    # 向量检索结果
    for rank, doc in enumerate(vector_results):
        doc_id = doc.metadata.get("id", hash(doc.page_content))
        scores[doc_id] = scores.get(doc_id, 0) + 1 / (k + rank + 1)
        # 记录文档内容
        if doc_id not in doc_map:
            doc_map[doc_id] = doc
    
    # BM25 检索结果
    for rank, doc in enumerate(bm25_results):
        doc_id = doc.metadata.get("id", hash(doc.page_content))
        scores[doc_id] = scores.get(doc_id, 0) + 1 / (k + rank + 1)
        if doc_id not in doc_map:
            doc_map[doc_id] = doc
    
    # 按融合分数排序
    ranked = sorted(scores.items(), key=lambda x: x[1], reverse=True)
    return [(doc_map[doc_id], score) for doc_id, score in ranked]
```

### 4.3 RRF 参数调优

| k 值 | 效果 | 适用场景 |
|------|------|---------|
| k=1 | 对排名差异非常敏感 | 两个检索器效果差异大 |
| k=30 | 平衡敏感度 | 通用场景 |
| k=60 | 平滑，对排名差异不敏感 | 两个检索器效果相近 |

---

## 5. Reranker 精排

### 5.1 为什么需要 Reranker？

向量检索使用**双编码器（Bi-Encoder）**，查询和文档各自独立编码，只能用余弦相似度近似匹配。Reranker 使用**交叉编码器（Cross-Encoder）**，将查询和文档拼接后一起编码，能看到完整的交互信息，精度更高。

```
双编码器（Bi-Encoder）：query → 向量A,  doc → 向量B,  score = cos(A, B)
交叉编码器（Cross-Encoder）：[query; doc] → score
```

### 5.2 BGE-Reranker 使用

```python
from langchain.retrievers import ContextualCompressionRetriever
from langchain_community.cross_encoders import HuggingFaceCrossEncoder
from langchain.retrievers.document_compressors import CrossEncoderReranker

# 1. 基础检索器（先检索 top-30）
base_retriever = vector_store.as_retriever(search_kwargs={"k": 30})

# 2. Reranker 模型
reranker = HuggingFaceCrossEncoder(model_name="BAAI/bge-reranker-v2-m3")

# 3. 压缩/重排序器
compressor = CrossEncoderReranker(
    model=reranker,
    top_n=5,  # 精排后保留 top-5
)

# 4. 重排序检索器
retriever = ContextualCompressionRetriever(
    base_compressor=compressor,
    base_retriever=base_retriever,
)

# 5. 检索+精排
results = retriever.get_relevant_documents("稻飞虱防治方法")
```

### 5.3 Reranker 模型对比

| 模型 | 特点 | 推荐场景 |
|------|------|---------|
| BAAI/bge-reranker-v2-m3 | 多语言，精度高 | 中文场景首选 |
| BAAI/bge-reranker-large | 英文精度更高 | 英文场景 |
| cross-encoder/ms-marco-MiniLM-L6-v2 | 轻量级 | 快速原型 |
| Cohere Rerank | API 调用，无需本地部署 | 不想自己部署模型 |

### 5.4 性能对比

```
朴素向量检索（top-5）        → 准确率：~70%
BM25 检索（top-5）           → 准确率：~60%
混合检索+RRF（top-5）        → 准确率：~80%
混合检索+RRF+Reranker（top-5）→ 准确率：~90%+
```

---

## 6. 证据门控与领域守卫

### 6.1 证据门控

对检索结果进行质量分级，低分证据不用于生成。

```python
def evidence_gate(results, threshold=0.5):
    """证据门控：过滤低质量检索结果"""
    passed = []
    for doc, score in results:
        if score >= threshold:
            passed.append(doc)
        else:
            print(f"证据门控过滤：{doc.page_content[:50]}... (score={score:.2f})")
    return passed
```

### 6.2 领域守卫

防止 RAG 系统回答非领域问题。

```python
# 领域守卫 — 农业领域示例
AGRICULTURE_TERMS = ("水稻", "小麦", "病虫害", "施肥", "农药", "产量", "土壤")
NON_AGRICULTURE_TERMS = ("java", "python", "编程", "代码", "spring")

def classify_query(message):
    agri_hits = [t for t in AGRICULTURE_TERMS if t in message]
    non_agri = [t for t in NON_AGRICULTURE_TERMS if t in message]
    
    if non_agri and not agri_hits:
        return {"allowed": False, "category": "non_agriculture"}
    if agri_hits:
        return {"allowed": True, "category": "agriculture"}
    return {"allowed": False, "category": "ambiguous"}
```

---

## 总结

本章你学会了：

- 向量检索的三种模式：similarity、threshold、MMR
- BM25 关键词检索的原理与实现
- 混合检索的架构设计（向量 + BM25 多路召回）
- RRF 融合算法的公式与实现
- Reranker 精排的原理与使用
- 证据门控与领域守卫的实践

下一步：学习 [查询转换](../02-core/03-query-transformation.md)，掌握查询重写、分解和路由技术。