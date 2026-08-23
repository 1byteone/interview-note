# 高级 RAG 实战：从朴素检索到生产级管线

> **生态**: E06 · 通识与基础 | **等级**: 高级 | **前置要求**: 了解 RAG 基础与向量检索概念（建议先阅读 03-production-system-design.md）

朴素 RAG（Naive RAG）——"文档切块 → 向量化 → 相似度检索 → 拼入 Prompt"——在 Demo 阶段表现良好，一旦进入生产环境就会暴露出一系列问题：检索召回不精准、切块破坏语义、查询歧义导致错配、以及上下文注入后 LLM 仍产生幻觉。业界在 2024-2026 年将 RAG 演进为一条**可评估、可优化、可路由**的生产级管线。

本教程系统梳理去朴素化（De-naive）的技术路线：从现代 RAG 管线架构出发，逐一拆解切块、混合检索、重排序、查询改写、元数据过滤、自适应路由与图增强 RAG，并给出每个环节的可运行代码。

---

## 1. 朴素 RAG 的困境

### 1.1 什么是朴素 RAG

朴素 RAG 是最早被广泛使用的形式，流程简单直接：

```
用户查询 → 向量化 → 向量库 Top-K 检索 → 拼接上下文 → LLM 生成
```

在原型阶段它足够"能跑"，但生产环境的真实数据（PDF 长文、表格、代码仓库、多语言文档）会让它迅速失稳。

### 1.2 常见失败模式

| 失败模式 | 根因 | 典型表现 |
|---------|------|---------|
| **召回不精准** | 单一向量检索对专有名词、缩写、组合查询不够敏感 | 相关文档排名靠后，噪声文档挤进 Top-K |
| **语义碎片化** | 固定长度切块切断语义完整单元（如表格、代码函数） | 检索结果信息残缺，无法回答 |
| **查询歧义** | 用户查询口语化、隐含上下文 | 检索方向错误，返回无关内容 |
| **位置偏置** | 长上下文下 LLM 更关注首尾内容 | 正确答案被淹没，LLM 幻觉填充 |
| **无可观测性** | 无法量化"检索质量"与"生成质量" | 上线后只能靠人工抽查，问题定位困难 |

这五大失败模式的解决路径，正是本教程将要逐个攻克的进阶技术。

---

## 2. 生产级 RAG 管线架构

现代 RAG 将上游（Ingestion）、中游（Retrieval）、下游（Generation）分离为独立可优化的三段：

```
┌─────────────── 入库阶段（Ingestion）────────────────┐
│ 原始文档 → 解析（PDF/HTML/Word）→ 清洗 → 切块 → 嵌入 → 索引 │
└─────────────────────────┬───────────────────────────┘
                          ▼
┌─────────────── 检索阶段（Retrieval）────────────────┐
│ 用户查询 → 查询改写 → 向量检索 + 关键词检索 → 融合 → 重排序 │
└─────────────────────────┬───────────────────────────┘
                          ▼
┌─────────────── 生成阶段（Generation）────────────────┐
│ 指令 + 精选上下文 → LLM 生成 → 引用校验 → 答案        │
└─────────────────────────────────────────────────────┘
```

### 2.1 各阶段职责与优化杠杆

| 阶段 | 核心职责 | 主要优化杠杆 | 影响 |
|------|---------|-------------|------|
| **Ingestion** | 将非结构化文档转为可检索的索引 | 解析质量、切块策略、元数据提取 | 决定检索上限 |
| **Retrieval** | 从索引中召回候选并排序 | 混合搜索、重排序、查询改写 | 决定召回质量 |
| **Generation** | 基于上下文生成可信答案 | 提示设计、引用、幻觉防护 | 决定输出质量 |

**关键认知**：检索阶段（Retrieval）是生产 RAG 中投入产出比（ROI）最高的优化环节。其中混合检索（Hybrid Search）被业界公认是投产的"最高 ROI 起点"——它用最少的改动换回最稳定的召回提升。

### 2.2 完整管线的骨架代码

```python
class ProductionRAGPipeline:
    """生产级 RAG 管线：四阶段结构"""

    def __init__(self, embedder, dense_index, sparse_index, reranker, llm):
        self.embedder = embedder
        self.dense_index = dense_index      # 向量索引（如 Qdrant/Pinecone）
        self.sparse_index = sparse_index    # 稀疏索引（如 Elasticsearch/BM25）
        self.reranker = reranker            # 交叉编码器重排序模型
        self.llm = llm

    # ── 入库阶段 ──
    def ingest(self, file_paths: list[str]):
        for path in file_paths:
            # 1. 解析：按文件类型解析为纯文本
            text = parse_document(path)      # PDF/HTML/Word → text
            # 2. 清洗：去重、去页眉页脚、修正 OCR 噪声
            text = clean_text(text)
            # 3. 切块：语义完整的分块
            chunks = recursive_text_splitter(text)
            # 4. 嵌入 + 元数据
            vectors = self.embedder.embed_texts(chunks)
            # 5. 双通道索引
            self.dense_index.upsert(chunks, vectors, metadata={"source": path})
            self.sparse_index.index(chunks)

    # ── 检索阶段 ──
    def retrieve(self, query: str, top_k: int = 20) -> list[dict]:
        rewritten = self.rewrite_query(query)            # 查询改写
        dense_hits = self.dense_index.search(self.embedder.embed(rewritten), k=top_k)
        sparse_hits = self.sparse_index.search(rewritten, k=top_k)  # BM25
        fused = self.fusion(dense_hits, sparse_hits)     # RRF 融合
        return self.reranker.rerank(query, [h["text"] for h in fused], top_n=5)

    # ── 生成阶段 ──
    def generate(self, query: str, evidences: list[dict]) -> str:
        context = "\n\n".join(
            f"[{i+1}] ({e['source']}) {e['text']}" for i, e in enumerate(evidences)
        )
        prompt = f"""基于以下检索到的证据回答问题。若证据不足，请明确说明。
        要求：仅使用证据中的信息；引用时标注 [来源编号]。

        证据：
        {context}

        问题：{query}
        """
        answer = self.llm.generate(prompt)
        return answer
```

---

## 3. 切块策略：检索单元的决定者

**切块（Chunking）定义了检索的基本单元——糟糕的切块必然导致检索失败**，无论后续重排序多先进都无法弥补。切块技术已从"固定窗口"演进到"语义感知"。

### 3.1 四种主流切块策略对比

| 策略 | 原理 | 优点 | 缺点 | 适用场景 |
|------|------|------|------|---------|
| **Fixed-size（固定大小）** | 按 token 数量硬切，可加重叠窗口 | 实现简单、性能可控 | 切断语义单元 | 语料结构均匀的新闻/论坛文本 |
| **Sentence（句子）** | 按句子边界切分，按窗口聚合 | 语义相对完整 | 长句处理差、丢失上下文 | 对话、邮件、法律条款 |
| **Recursive（递归）** | 按分隔符优先级逐级切分 | 兼顾结构与大小 | 参数调优依赖语料 | 代码库、Markdown、HTML |
| **Semantic（语义）** | 基于 embedding 相似度在语义断点切分 | 语义最完整 | 计算成本高、依赖嵌入模型 | 书籍、论文、长文报告 |

### 3.2 递归切块实现

递归切块（Recursive Character Splitter）是 LangChain 生态的事实标准，核心是按分隔符优先级递归降级：

```python
from typing import List

class RecursiveSplitter:
    """递归分隔符切块器"""

    SEPARATORS = ["\n\n", "\n", "。", "！", "？", ". ", "! ", "? ", " ", ""]

    def __init__(self, chunk_size: int = 800, chunk_overlap: int = 120):
        self.chunk_size = chunk_size
        self.chunk_overlap = chunk_overlap

    def _split_at_separator(self, text: str, seps: List[str]) -> List[str]:
        """在最高优先级可用分隔符处切分"""
        for sep in seps:
            if sep in text:
                parts = text.split(sep)
                # 递归处理过长的部分
                result = []
                for part in parts:
                    if len(part) > self.chunk_size:
                        result.extend(self._split_at_separator(part, seps[seps.index(sep)+1:]))
                    else:
                        result.append(part)
                return result
        return [text]

    def build_chunks(self, text: str) -> List[str]:
        """聚合碎块为定长 chunk，带重叠窗口"""
        pieces = self._split_at_separator(text, self.SEPARATORS)
        chunks = []
        buffer = ""

        for piece in pieces:
            if len(buffer) + len(piece) <= self.chunk_size:
                buffer += piece
            else:
                chunks.append(buffer)
                # 保留尾部重叠，维持跨块上下文
                buffer = buffer[-self.chunk_overlap:] + piece

        if buffer:
            chunks.append(buffer)
        return chunks
```

### 3.3 语义切块实现

语义切块基于 embedding 相似度骤降检测语义断点：

```python
import numpy as np

class SemanticSplitter:
    """基于嵌入相似度的语义切块"""

    def __init__(self, embedder, min_chunk: int = 100, max_chunk: int = 600):
        self.embedder = embedder
        self.min_chunk = min_chunk
        self.max_chunk = max_chunk

    def _embed(self, text: str) -> np.ndarray:
        return self.embedder.embed(text)

    def split(self, paragraphs: List[str]) -> List[str]:
        """在语义边界处切分段落序列"""
        if len(paragraphs) <= 1:
            return paragraphs

        # 对每对相邻段落计算余弦相似度
        embeds = [self._embed(p) for p in paragraphs]
        similarities = [
            float(np.dot(embeds[i], embeds[i+1])
                  / (np.linalg.norm(embeds[i]) * np.linalg.norm(embeds[i+1])))
            for i in range(len(embeds) - 1)
        ]

        # 相似度显著低于均值的点即为语义断点：分段
        threshold = np.mean(similarities) - 0.5 * np.std(similarities)
        boundaries = [0] + [i+1 for i, s in enumerate(similarities) if s < threshold] + [len(paragraphs)]

        return ["\n".join(paragraphs[boundaries[i]:boundaries[i+1]])
                for i in range(len(boundaries) - 1)]
```

---

## 4. 混合检索：密集 + 稀疏

### 4.1 为什么需要混合检索

两种检索机制各有盲区，互补性强：

| 检索类型 | 代表技术 | 优势 | 盲区 |
|---------|---------|------|------|
| **Dense（密集）** | 双塔嵌入（BGE、OpenAI Embedding） | 理解语义、泛化能力强 | 对专有名词/编号/精确匹配不敏感 |
| **Sparse（稀疏）** | BM25、SPLADE | 精确词项匹配、可解释 | 无法理解同义改写 |

产品编号"RDX-2026-0715"、API 方法名`getUserById`这类查询，BM25 召回远优于向量检索；而"哪些客户案例显示降本效果显著"这类语义查询，向量检索明显占优。混合检索将两路结果融合，可同时保证**精确命中**与**语义召回**。

### 4.2 RRF 融合实现

行业标准融合算法是**倒排融合（Reciprocal Rank Fusion, RRF）**——对不同检索结果的排名取倒数的加权和，无需分数归一化：

```python
from typing import List, Dict

def reciprocal_rank_fusion(
    result_sets: List[List[Dict]],
    weights: List[float] | None = None,
    k: int = 60
) -> List[Dict]:
    """RRF：多路检索结果融合

    score(doc) = Σ weight_i / (k + rank_i(doc))
    k 为平滑常数，避免热门文档分数过高
    """
    scores: Dict[str, float] = {}
    doc_map: Dict[str, Dict] = {}
    weights = weights or [1.0] * len(result_sets)

    for weight, results in zip(weights, result_sets):
        for rank, doc in enumerate(results, start=1):
            doc_id = doc["id"]
            scores[doc_id] = scores.get(doc_id, 0.0) + weight / (k + rank)
            doc_map[doc_id] = doc

    # 按融合分数降序排列
    ranked = sorted(scores.items(), key=lambda x: x[1], reverse=True)
    return [doc_map[doc_id] for doc_id, _ in ranked]

# 使用示例
def hybrid_search(query: str, dense_hits: list, sparse_hits: list) -> list:
    """混合检索：向量 + BM25 + RRF"""
    fused = reciprocal_rank_fusion(
        result_sets=[dense_hits, sparse_hits],
        weights=[1.0, 1.0]
    )
    return fused
```

### 4.3 一个端到端的混合检索检索服务

```python
class HybridSearchService:
    """结合 Qdrant（dense）与 Elasticsearch（sparse）的混合检索"""

    def __init__(self, qdrant_client, es_client, embedder):
        self.qdrant = qdrant_client
        self.es = es_client
        self.embedder = embedder

    def search(self, query: str, top_k: int = 20) -> list[dict]:
        # 1. 密集检索：向量相似度
        query_vec = self.embedder.embed(query)
        dense_hits = self.qdrant.search(
            query_vector=query_vec,
            limit=top_k,
            with_payload=True
        )

        # 2. 稀疏检索：BM25 词项匹配
        es_body = {
            "query": {"multi_match": {"query": query, "fields": ["title", "content"]}},
            "size": top_k
        }
        sparse_hits = [
            {"id": h["_id"], "text": h["_source"]["content"], "score": h["_score"]}
            for h in self.es.search(index="docs", body=es_body)["hits"]["hits"]
        ]

        # 3. RRF 融合
        dense_formatted = [{"id": h.id, "text": h.payload["content"]} for h in dense_hits]
        return reciprocal_rank_fusion([dense_formatted, sparse_hits])
```

---

## 5. 重排序：交叉编码器

### 5.1 为什么需要重排序

初检 Top-K 通常召回 20-50 条候选，但其中存在大量噪声。**重排序（Reranking）由交叉编码器（Cross-Encoder）对查询与文档逐对打分**，比双塔（Bi-Encoder）的第一阶段检索更精细，能显著提升头部精度。

| 阶段 | 模型类型 | 打分方式 | 速度 | 精度 |
|------|---------|---------|------|------|
| 初检 | Bi-Encoder（双塔） | 查询/文档独立编码后算相似度 | 快（可离线预计算文档向量） | 中 |
| 重排 | Cross-Encoder（交叉） | 查询与文档拼接后联合编码打分 | 慢（需要逐对推理） | 高 |

### 5.2 重排实现

```python
from sentence_transformers import CrossEncoder

class Reranker:
    """交叉编码器重排序"""

    def __init__(self, model_name: str = "BAAI/bge-reranker-v2-m3"):
        # 交叉编码器：query + doc 拼接打分
        self.model = CrossEncoder(model_name, max_length=512)

    def rerank(self, query: str, candidates: list[dict], top_n: int = 5) -> list[dict]:
        """对初检候选重新打分并截取 Top-N"""
        pairs = [(query, doc["text"]) for doc in candidates]
        scores = self.model.predict(pairs)

        # 按交叉编码器分数排序
        ranked = sorted(zip(candidates, scores), key=lambda x: x[1], reverse=True)
        return [doc for doc, score in ranked[:top_n]]

# 使用：初检 Top-50 → 重排 Top-5
reranker = Reranker()
top5 = reranker.rerank(
    query="LangGraph 支持哪些多 Agent 架构？",
    candidates=initial_50_candidates,
    top_n=5
)
```

**工程注意**：交叉编码器逐对推理耗时高，必须只对初检候选（而非全库）重排。生产系统通常采用"初检召回 50-100 条 → 交叉编码器精排 5-10 条"的两级检索架构，在精度与延迟之间取得平衡。

---

## 6. 查询变换：让检索更精准

用户查询往往口语化、指代模糊、单一问题信息量大。**查询变换（Query Transformation）** 在检索前改写、扩展或分解查询，提升检索命中率。

| 变换技术 | 原理 | 典型场景 |
|---------|------|---------|
| **查询改写** | LLM 将口语查询转成精确检索语句 | "手机上那个绿色的图标" → "微信图标" |
| **查询扩展** | 补充同义词、上下位词、译文 | "AI" → "AI / 人工智能 / 大语言模型" |
| **子查询分解** | 将复杂问题拆为多个简单子查询 | 多条件复合问题 |
| **假设答案** | 让 LLM 先写假设答案再检索 | 提升检索相关性（HyDE） |

### 6.1 查询改写与分解实现

```python
class QueryTransformer:
    """查询改写与分解"""

    def __init__(self, llm):
        self.llm = llm

    def rewrite(self, query: str) -> str:
        """将口语化查询改写为适合检索的形式"""
        prompt = f"""将以下用户查询改写为更精确的数据库检索查询：
        - 补充同义词与专业术语
        - 消除指代歧义
        - 保持查询意图不变
        - 直接输出改写结果，不要解释

        原始查询：{query}
        """
        return self.llm.generate(prompt).strip()

    def decompose(self, query: str) -> list[str]:
        """将复合问题分解为多个子查询"""
        prompt = f"""判断以下问题是否需要拆分为多个独立子问题分别检索。
        如果需要，输出 JSON 数组形式的子问题列表；如果不需要，输出包含原问题本身的数组。

        问题：{query}
        输出：仅返回 JSON，例如 ["子问题1", "子问题2"]
        """
        raw = self.llm.generate(prompt, response_format={"type": "json_object"})
        import json
        return json.loads(raw).get("sub_queries", [query])

# 使用：多子查询并行检索 → 结果合并
def retrieve_with_transformation(transformer, retriever, query: str):
    rewritten = transformer.rewrite(query)
    sub_queries = transformer.decompose(rewritten)
    all_hits = []
    for sq in sub_queries:          # 可并行化
        all_hits.extend(retriever.search(sq, top_k=10))
    return deduplicate_and_rerank(all_hits, rewritten)
```

### 6.2 HyDE（假设性文档嵌入）

HyDE 先让 LLM "凭空"写一段假设答案文档，再用该文档的向量检索——假设答案与目标文档的语义距离通常比查询更近：

```python
def hyde_retrieve(embedder, vector_store, query: str, llm) -> list[dict]:
    """HyDE：假设文档增强检索"""
    hypothetical = llm.generate(
        f"""请针对以下问题写一段假设性的专业解答（500 字以内），
        即使你不知道答案也要写得详细、专业：
        {query}"""
    )
    hyde_vec = embedder.embed(hypothetical)
    return vector_store.search(hyde_vec, top_k=10)
```

---

## 7. 元数据过滤：检索的"硬约束"

纯向量检索是"软匹配"，而 **元数据过滤（Metadata Filtering）** 是检索的"硬约束"——先按结构化条件缩小候选集，再做语义排序。它同时改善召回精度与检索成本。

```python
def search_with_metadata_filter(
    vector_store, embedder, query: str,
    filters: dict = None, top_k: int = 10
) -> list[dict]:
    """带元数据过滤的向量检索

    filters 示例：
      {"source": {"$ne": "draft"},
       "date": {"$gte": "2025-01-01"},
       "category": {"$in": ["架构", "AI"]}}
    """
    query_vec = embedder.embed(query)
    return vector_store.search(
        query_vector=query_vec,
        limit=top_k,
        filter=filters or {}
    )
```

### 常用元数据维度

| 元数据 | 过滤用途 | 示例 |
|--------|---------|------|
| **来源** | 排除草稿/废弃文档 | `source != "draft"` |
| **时间** | 只检索最新版本 | `date >= 2025-01-01` |
| **权限等级** | 租户/用户级数据隔离 | `tenant_id == "u123"` |
| **文档类型** | 缩小到指定语料 | `type in ["FAQ", "手册"]` |
| **语言** | 多语言语料精确命中 | `lang == "zh"` |

**权限过滤是元数据过滤最重要的生产用途**——缺少租户级过滤的 RAG 就是数据泄露事故点。过滤必须在检索层强制执行，而不是依赖 Prompt 约束。

---

## 8. 自适应 RAG：查询路由

**自适应 RAG（Adaptive RAG）** 的核心思想是：并非所有查询都需要 RAG。一个查询分类器（Query Classifier）根据查询的复杂度与新鲜度，将请求路由到最合适的处理管线：

- **简单/常识问题** → 直接 LLM 回答（省成本、降延迟）
- **需要事实支撑的问题** → 走标准检索增强管线
- **多步/复杂问题** → 走多跳检索（Multi-hop）或 Agentic RAG

```
用户查询
   │
   ▼
┌────────────┐
│ 查询分类器  │ ← LLM 评估：复杂度 1-5、是否需要外部事实
└─────┬──────┘
      │
  复杂度 1-2    复杂度 3-4             复杂度 5 / 多步
      │              │                    │
      ▼              ▼                    ▼
  LLM 直答        标准 RAG            多跳 / Agentic RAG
（无检索）      （检索+重排+生成）      （自主规划多轮检索）
```

### 8.1 查询分类器实现

```python
class QueryClassifier:
    """路由决策器：决定查询走哪条处理管线"""

    ROUTES = ["direct", "standard_rag", "multi_hop_rag"]

    def __init__(self, llm):
        self.llm = llm

    def route(self, query: str) -> str:
        prompt = f"""判断以下查询最适合哪条处理路径：

        - direct: 常识性问题、问候、无外部知识需求（如"你好"、"1+1=？"）
        - standard_rag: 需要事实知识，一次性检索即可回答
        - multi_hop_rag: 需要多步推理、跨多个文档关联、需要多轮检索

        查询：{query}
        输出：仅返回 direct / standard_rag / multi_hop_rag 之一
        """
        decision = self.llm.generate(prompt).strip().lower()
        return decision if decision in self.ROUTES else "standard_rag"

# 使用示例
class AdaptiveRAG:
    """自适应 RAG：按路由执行不同管线"""

    def __init__(self, classifier, direct_llm, rag_pipeline, multi_hop_engine):
        self.classifier = classifier
        self.direct_llm = direct_llm
        self.rag = rag_pipeline
        self.multi_hop = multi_hop_engine

    def answer(self, query: str) -> str:
        route = self.classifier.route(query)
        if route == "direct":
            return self.direct_llm.generate(query)          # 零检索
        if route == "standard_rag":
            return self.rag.generate(query)                 # 单轮检索
        return self.multi_hop.answer(query)                 # 多轮检索
```

自适应 RAG 将检索成本与延迟对准查询的实际需求——生产数据显示，多数客服系统中 30-50% 的查询实际无需检索，路由后可显著节省成本。

---

## 9. GraphRAG：知识图谱增强

### 9.1 图增强 RAG 的价值

传统向量 RAG 检索的是"文本片段"；**GraphRAG 将文本内容组织为实体与关系构成的知识图谱**，使 LLM 能够回答跨越多个文档、需要多跳关系推理的问题：

| 对比维度 | 向量 RAG | GraphRAG |
|---------|---------|----------|
| 检索单元 | 文本 chunk | 实体 / 关系 / 子图 |
| 多跳推理 | 弱（依赖文本关联） | 强（图谱天然支持路径推理） |
| 全局性问题 | "总结全部文档主题"几乎不可用 | 社区检测 + 分层摘要可回答 |
| 构建成本 | 低 | 高（实体抽取 + 关系抽取） |
| 适用场景 | 常见问答、文档检索 | 实体密集型数据集（医疗、法律、企业知识库） |

### 9.2 典型管线

```
文档 → LLM 实体抽取（人名/机构/疾病/药物...）
     → LLM 关系抽取（"A 治疗 B"、"A 在 C 任职"）
     → 图谱构建（Neo4j / NetworkX）
     → 图检索：实体扩展 + 一跳/多跳邻居 + 子图剪枝
     → 子图摘要注入上下文 → LLM 生成
```

```python
from neo4j import GraphDatabase

class GraphRAGRetriever:
    """图检索：实体扩展 + 邻居子图抽取"""

    def __init__(self, uri, user, password):
        self.driver = GraphDatabase.driver(uri, auth=(user, password))

    def retrieve_context(self, question: str, entity_extractor, depth: int = 2) -> str:
        # 1. 从问题中抽取关键实体
        entities = entity_extractor.extract(question)   # ["LangGraph", "checkpoint"]

        # 2. 对每个实体扩展邻居，逐层收集子图
        subgraph_texts = []
        for entity in entities:
            cypher = f"""
            MATCH path = (e {{name: $name}})-[*1..{depth}]-(n)
            WHERE e.name = $name
            RETURN path
            LIMIT 50
            """
            with self.driver.session() as session:
                records = session.run(cypher, name=entity)
                subgraph_texts.append(serialize_subgraph(records))

        # 3. 拼接为图谱上下文供 LLM 使用
        return "\n---\n".join(subgraph_texts)
```

**选型建议**：GraphRAG 构建成本高（LLM 抽取实体关系的 token 消耗显著），仅在实体关系密集型、需要多跳推理的业务场景（医疗知识库、法律判例、企业供应链）中引入；常规文档问答优先优化混合检索与重排序。

---

## 10. 评估：让 RAG 可度量

RAG 管线没有评估就无法迭代。**评估（Evaluation）** 是生产 RAG 的必备环节。

### 10.1 核心评估维度

| 维度 | 含义 | 测量方式 | 关键问题 |
|------|------|---------|---------|
| **Faithfulness（忠实度）** | 生成内容是否完全基于检索证据 | LLM-as-Judge、NLI 模型 | "答案有没有编造证据中不存在的内容？" |
| **Relevance（相关性）** | 检索内容是否切题 | LLM-as-Judge、上下文相关性评分 | "检索到的 chunk 对回答问题有帮助吗？" |
| **Context Precision（上下文精确率）** | 检索结果中相关内容的占比与排序 | 基于标注或 LLM 判定 | "检索结果前置位置都是相关的吗？" |
| **Context Recall（上下文召回率）** | 回答所需信息是否都被召回 | 基于标注或 LLM 判定 | "回答所需的信息是否都检索到了？" |

### 10.2 LLM-as-Judge 评估实现

```python
class RAGEvaluator:
    """RAG 评估：忠实度 + 相关性 + 上下文精确率"""

    def __init__(self, judge_llm):
        self.judge = judge_llm       # 建议用比被评估模型更强的模型

    def faithfulness(self, question: str, answer: str, evidence: list[str]) -> float:
        """忠实度：答案是否基于证据（1-5 分）"""
        prompt = f"""评估答案对证据的忠实度（1-5 分）：
        - 5 分：答案完全基于证据，无任何编造
        - 3 分：答案主体基于证据，有少量推断
        - 1 分：答案出现证据中不存在的关键信息（幻觉）

        证据：
        {chr(10).join(evidence)}

        问题：{question}
        答案：{answer}
        输出：仅返回分数
        """
        return int(self.judge.generate(prompt).strip())

    def evidence_relevance(self, question: str, evidence: list[str]) -> float:
        """上下文相关性：证据整体对回答问题是否有用（1-5 分）"""
        prompt = f"""评估以下检索到的证据对回答问题的相关性（1-5 分）：
        问题：{question}
        证据：
        {chr(10).join("[" + str(i+1) + "] " + e for i, e in enumerate(evidence))}
        请评估：证据整体相关性、按位置排序判断是否有无关内容。
        输出：仅返回分数
        """
        return int(self.judge.generate(prompt).strip())

    def context_precision(self, question: str, evidence: list[str]) -> float:
        """上下文精确率：相关 chunk 是否集中在排序前列"""
        # 对每个 chunk 单独判定相关性，计算精确率@k
        relevant = []
        for i, chunk in enumerate(evidence):
            verdict = self.judge.generate(
                f"证据片段是否与问题'{question}'相关？仅回答 是/否：{chunk}"
            )
            relevant.append(verdict.strip() == "是")

        precision_at_k = []
        relevant_count = 0
        for k, is_rel in enumerate(relevant, start=1):
            if is_rel:
                relevant_count += 1
            precision_at_k.append(relevant_count / k)
        return sum(precision_at_k) / len(precision_at_k) if precision_at_k else 0.0
```

### 10.3 评估集建设要点

| 要点 | 说明 |
|------|------|
| **黄金数据集** | 每个测试用例包含：问题 + 期望答案 + 关键证据位置 |
| **难度分层** | 简单/中等/困难问题各占一定比例 |
| **负样本** | 包含检索应该"答不上来"的问题，检验拒答能力 |
| **回归集成** | 每次切块/重排序/提示词变更后全量回归 |
| **分级评估** | 检索质量（Retrieval）、生成质量（Generation）、端到端质量（End-to-end）分开评估 |

**实践建议**：把"检索质量评估"与"生成质量评估"分离——前者定位检索管线问题，后者定位提示与模型问题，避免互相掩盖。

---

## 11. 中文资源：all-in-rag

`datawhalechina/all-in-rag`（Datawhale 开源）是面向中文开发者最完整的一套 RAG 技术全栈教程，覆盖本教程所有主题并附有可运行代码：

- **内容**：RAG 理论、切块、嵌入、向量库、混合检索、重排序、评估、Agentic RAG、GraphRAG、多模态 RAG
- **特征**：中文讲解、Notebook 实战、测试集 + 评估脚本、社区持续更新
- **适用**：从零入门到生产落地的完整学习路径

> 建议搜索引擎检索 `datawhalechina/all-in-rag GitHub` 获取最新仓库地址。

---

## 12. 生产最佳实践清单

### 12.1 管线设计

| 实践 | 说明 |
|------|------|
| **混合检索起步** | 向量 + BM25 + RRF 是生产 RAG 最高 ROI 的第一步优化 |
| **两级检索架构** | 初检 Top-50（Bi-Encoder）→ 重排 Top-5（Cross-Encoder） |
| **重排序只跑候选集** | 交叉编码器绝不直接作用于全库 |
| **元数据过滤先行** | 权限/租户/时间过滤在语义检索之前强制执行 |
| **切块与检索参数联动** | chunk 大小与上下文窗口、重排数量一起调优，不孤立调参 |

### 12.2 质量与运维

| 实践 | 说明 |
|------|------|
| **建立评估集** | 先有评估，再谈优化；每次改动跑回归 |
| **分离检索/生成评估** | 用不同指标定位问题出在哪一段 |
| **引用来源** | 生成要求标注证据来源，便于追溯真相与排障 |
| **拒答兜底** | 证据不足时必须拒绝回答，而不是幻觉补全 |
| **索引版本管理** | 语料更新走索引重建 + 灰度切换，避免新旧混合污染 |

### 12.3 渐进升级路线

```
Level 1: 朴素 RAG（向量检索，固定切块）
    ↓ 加检索质量评估
Level 2: 混合检索（+BM25 + RRF）           ← 最高 ROI 起点
    ↓ 加重排序
Level 3: 重排序 + 查询改写 + 元数据过滤
    ↓ 加路由
Level 4: 自适应 RAG（查询分类路由）
    ↓ 按业务引入
Level 5: Agentic RAG / GraphRAG + 生产评估监控
```

---

## 总结

高级 RAG 的本质是**把"检索"从一次相似度查询升级为一条可评估、可路由、可优化的工程管线**。核心结论：

1. **切块决定检索上限**：切块定义了检索单元，用文档结构（Recursive）或语义（Semantic）切块替代固定窗口
2. **混合检索是最优起步**：密集 + 稀疏 + RRF 融合是投入产出比最高的第一优化
3. **重排序提升头部精度**：交叉编码器对初检候选精排，坚持"初检 Top-50 → 精排 Top-5"的两级架构
4. **查询变换解决歧义**：改写、分解、HyDE 让查询匹配更精准
5. **元数据过滤是安全底线**：租户/权限过滤必须在检索层强制执行
6. **自适应路由控制成本**：按查询复杂度路由到直答 / 标准 RAG / 多跳 RAG
7. **评估是迭代前提**：忠实度、相关性、上下文精确率/召回率全链路度量

### 参考资源

- [all-in-rag（Datawhale RAG 全栈教程）](https://github.com/datawhalechina/all-in-rag)
- 本系列：[Prompt Engineering 实战](./01-prompt-engineering-guide.md) | [AI Agent 设计模式](./02-agent-design-patterns.md) | [生产级 AI Agent 系统设计](./03-production-system-design.md) | [LangGraph 编排实战](./04-langgraph-orchestration.md)
- 仓库参考：[ai-system-design-guide](../../repositories/ombharatiya_ai-system-design-guide.md) | [AgentGuide](../../repositories/adongwanai_AgentGuide.md)