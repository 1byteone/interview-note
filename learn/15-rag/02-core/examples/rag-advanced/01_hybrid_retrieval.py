"""
混合检索（Hybrid Retrieval）演示
=================================

演示内容：
1. BM25 稀疏检索（关键词匹配）
2. 向量密集检索（语义匹配）
3. EnsembleRetriever 加权组合
4. RRF（Reciprocal Rank Fusion）结果融合实现
5. Reranker 重排序（cross-encoder 或 mock）

运行方式：
    python 01_hybrid_retrieval.py

说明：
- 本示例完全离线可运行（使用免费 HuggingFace 嵌入）
- 无需 API Key；Reranker 部分如需真实模型需安装 sentence-transformers
"""

from pathlib import Path

# ============================================================
# 0. 准备示例语料
# ============================================================
documents = [
    "Python 是一种解释型高级编程语言，强调代码可读性。",
    "Python 的 GIL 限制了多线程在 CPU 密集型任务中的性能。",
    "FastAPI 基于 Python 类型提示，自动生成 OpenAPI 文档。",
    "asyncio 是 Python 的异步编程库，使用 async/await 语法。",
    "Django 是一个功能完整的企业级 Python Web 框架。",
    "SQLAlchemy 是 Python 中流行的 ORM 库，支持多种数据库。",
    "pandas 是 Python 数据分析的核心库，提供 DataFrame 结构。",
    "NumPy 提供 Python 的多维数组对象和数学运算函数。",
    "机器学习中，Python 是最流行的编程语言之一。",
    "Web 开发中，Python 既可以写后端 API，也可以做数据展示。",
]

print("=" * 60)
print("0. 准备示例语料")
print("=" * 60)
print(f"  {len(documents)} 条文档（关于 Python 技术栈）\n")


# ============================================================
# 1. 向量检索器（密集检索）
# ============================================================
print("1. 构建向量检索器（FAISS + 免费嵌入）")
print("-" * 60)

from langchain_community.embeddings import HuggingFaceEmbeddings
from langchain_community.vectorstores import FAISS

embeddings = HuggingFaceEmbeddings(
    model_name="sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2",
    model_kwargs={"device": "cpu"},
    encode_kwargs={"normalize_embeddings": True},
)

vector_store = FAISS.from_texts(documents, embeddings)
vector_retriever = vector_store.as_retriever(search_kwargs={"k": 5})
print("  ✅ 向量检索器就绪（TOP-5）\n")


# ============================================================
# 2. BM25 检索器（稀疏检索）
# ============================================================
print("2. 构建 BM25 检索器（关键词匹配）")
print("-" * 60)

from langchain_community.retrievers import BM25Retriever

# BM25 是一种经典的稀疏检索算法，基于词频统计
bm25_retriever = BM25Retriever.from_texts(documents)
bm25_retriever.k = 5  # 返回前 5 条
print("  ✅ BM25 检索器就绪（TOP-5）\n")


# ============================================================
# 3. EnsembleRetriever 加权组合
# ============================================================
print("3. EnsembleRetriever 加权组合")
print("-" * 60)

from langchain.retrievers import EnsembleRetriever

# 权重配置：向量（语义）0.7 + BM25（关键词）0.3
ensemble_retriever = EnsembleRetriever(
    retrievers=[bm25_retriever, vector_retriever],
    weights=[0.3, 0.7],
)

query = "Python 异步和高性能相关的内容"
print(f"  查询: {query}\n")

for retriever_name, retriever in [
    ("BM25 单独检索", bm25_retriever),
    ("向量单独检索", vector_retriever),
    ("Ensemble 组合检索", ensemble_retriever),
]:
    docs = retriever.invoke(query)
    print(f"  【{retriever_name}】")
    for i, doc in enumerate(docs, 1):
        print(f"    [{i}] {doc.page_content}")
    print()


# ============================================================
# 4. RRF（Reciprocal Rank Fusion）实现
# ============================================================
print("4. RRF 手动实现（Reciprocal Rank Fusion）")
print("-" * 60)


def reciprocal_rank_fusion(retriever_results: list[list], k: int = 60) -> list:
    """
    RRF 融合算法：
    对每个结果列表，按排名给分 1/(k + rank)，然后汇总所有来源的分数。
    优点：不需要像 EnsembleRetriever 那样调权重，结果较稳定。

    参数:
        retriever_results: 多个检索器的结果列表
        k: 平滑常数（一般取 60）
    """
    # 收集所有文档的累计分数
    fused_scores: dict[str, float] = {}
    doc_contents: dict[str, str] = {}

    for results in retriever_results:
        for rank, doc in enumerate(results, start=1):
            content = doc.page_content
            doc_contents[content] = content
            fused_scores[content] = fused_scores.get(content, 0) + 1.0 / (k + rank)

    # 按分数降序排列
    sorted_docs = sorted(fused_scores.items(), key=lambda x: x[1], reverse=True)
    return [
        (doc_contents[content], score)
        for content, score in sorted_docs
    ]


# 用两个检索器的结果做 RRF 融合
bm25_results = bm25_retriever.invoke(query)
vector_results = vector_retriever.invoke(query)

rrf_results = reciprocal_rank_fusion([bm25_results, vector_results])
print(f"  RRF 融合结果（前 5 条）:")
for i, (content, score) in enumerate(rrf_results[:5], 1):
    print(f"    [{i}] score={score:.4f} | {content}")
print()


# ============================================================
# 5. Reranker 重排序
# ============================================================
print("5. Reranker 重排序")
print("-" * 60)
print("  Reranker 把候选结果重新打分排序，提高相关性\n")

try:
    from sentence_transformers import CrossEncoder

    # 使用 CrossEncoder 重排序（需要安装 sentence-transformers）
    cross_encoder = CrossEncoder(
        "cross-encoder/ms-marco-MiniLM-L-6-v2"  # 英文模型，替换为中文模型可支撑中文
    )

    def rerank_with_cross_encoder(query: str, docs: list, top_k: int = 3) -> list:
        """用 CrossEncoder 对候选文档重排序"""
        pairs = [(query, doc.page_content) for doc in docs]
        scores = cross_encoder.predict(pairs)
        ranked = sorted(zip(docs, scores), key=lambda x: x[1], reverse=True)
        return ranked[:top_k]

    # 先取候选集（用 ensemble 结果），再重排序
    candidate = ensemble_retriever.invoke(query)[:5]
    reranked = rerank_with_cross_encoder(query, candidate, top_k=3)

    print(f"  查询: {query}")
    print(f"  候选集 {len(candidate)} 条，重排序后 TOP-3:")
    for i, (doc, score) in enumerate(reranked, 1):
        print(f"    [{i}] score={score:.4f} | {doc.page_content}")

except ImportError:
    print("  ⚠️ 未安装 sentence-transformers，使用 Mock Reranker 演示\n")

    def mock_reranker(query: str, docs: list, top_k: int = 3) -> list:
        """Mock 重排序：按关键词覆盖率打分（示范用途）"""
        ranked = []
        for doc in docs:
            keywords = set(query.replace("的", "").split())
            content = doc.page_content
            score = sum(kw in content for kw in keywords)
            ranked.append((doc, score))
        ranked.sort(key=lambda x: x[1], reverse=True)
        return ranked[:top_k]

    candidate = ensemble_retriever.invoke(query)[:5]
    reranked = mock_reranker(query, candidate, top_k=3)
    print(f"  查询: {query}")
    print(f"  [Mock] 重排序后 TOP-3:")
    for i, (doc, score) in enumerate(reranked, 1):
        print(f"    [{i}] score={score:.4f} | {doc.page_content}")


# ============================================================
# 6. 总结
# ============================================================
print("\n" + "=" * 60)
print("6. 检索策略总结")
print("=" * 60)
print("""
┌──────────────┬──────────────────┬──────────────────────┐
│ 策略          │ 优势              │ 劣势                  │
├──────────────┼──────────────────┼──────────────────────┤
│ BM25 (稀疏)   │ 精确关键词匹配    │ 无法理解语义/同义词    │
│ 向量 (密集)   │ 语义相似度        │ 依赖嵌入质量、易丢失   │
│              │                  │ 精确匹配              │
│ Ensemble     │ 两者兼顾，可调权重│ 权重需要调参           │
│ RRF          │ 无需调权重，稳定  │ 忽略分数绝对值         │
│ Reranker     │ 精度最高          │ 额外计算开销           │
└──────────────┴──────────────────┴──────────────────────┘

生产最佳实践:
  1. BM25 + 向量 组合召回（Ensemble/RRF）
  2. Reranker 精排 TOP-20 -> TOP-5
  3. 结合元数据过滤（时间、来源、类型）
""")