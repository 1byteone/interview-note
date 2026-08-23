# RAG 面试代码题

> 考察 RAG 系统的核心代码实现能力。

---

## 代码题 1：混合检索实现

### 题目

实现一个混合检索器，支持向量检索 + BM25 关键词检索，并使用 RRF 算法融合。

### 参考实现

```python
from typing import List, Tuple
import numpy as np
from rank_bm25 import BM25Okapi
import jieba


class HybridRetriever:
    """混合检索器：向量检索 + BM25 + RRF 融合"""
    
    def __init__(self, vector_store, documents: List[str], k: int = 60):
        """
        Args:
            vector_store: 向量库（需有 similarity_search 方法）
            documents: 原始文档列表（用于 BM25）
            k: RRF 平滑参数
        """
        self.vector_store = vector_store
        self.documents = documents
        self.k = k
        self._build_bm25_index()
    
    def _build_bm25_index(self):
        """构建 BM25 索引（使用 jieba 中文分词）"""
        tokenized_docs = [list(jieba.cut(doc)) for doc in self.documents]
        self.bm25 = BM25Okapi(tokenized_docs)
    
    def _vector_search(self, query: str, top_k: int = 30) -> List[Tuple[int, float]]:
        """
        向量检索
        Returns: [(doc_index, score), ...]
        """
        results = self.vector_store.similarity_search_with_relevance_scores(
            query, k=top_k
        )
        return [(doc.metadata.get("index", i), score) for i, (doc, score) in enumerate(results)]
    
    def _bm25_search(self, query: str, top_k: int = 30) -> List[Tuple[int, float]]:
        """
        BM25 检索
        Returns: [(doc_index, score), ...]
        """
        tokenized_query = list(jieba.cut(query))
        scores = self.bm25.get_scores(tokenized_query)
        # 取 top_k
        top_indices = np.argsort(scores)[::-1][:top_k]
        return [(idx, scores[idx]) for idx in top_indices]
    
    def _rrf_fusion(self, results_a: List[Tuple[int, float]], 
                    results_b: List[Tuple[int, float]]) -> List[Tuple[int, float]]:
        """
        RRF 融合
        score = Σ 1 / (k + rank_i)
        """
        rrf_scores = {}
        
        for rank, (doc_idx, _) in enumerate(results_a):
            rrf_scores[doc_idx] = rrf_scores.get(doc_idx, 0) + 1 / (self.k + rank + 1)
        
        for rank, (doc_idx, _) in enumerate(results_b):
            rrf_scores[doc_idx] = rrf_scores.get(doc_idx, 0) + 1 / (self.k + rank + 1)
        
        # 按 RRF 分数降序排列
        sorted_scores = sorted(rrf_scores.items(), key=lambda x: x[1], reverse=True)
        return sorted_scores
    
    def retrieve(self, query: str, top_k: int = 10) -> List[Tuple[int, float]]:
        """混合检索入口"""
        vector_results = self._vector_search(query)
        bm25_results = self._bm25_search(query)
        fused = self._rrf_fusion(vector_results, bm25_results)
        return fused[:top_k]


# 使用示例
def test_hybrid_retriever():
    documents = [
        "iPhone 15 Pro 支持 MagSafe 无线充电，功率 15W",
        "华为 Mate 60 Pro 支持 88W 有线快充",
        "7 天无理由退货：商品签收后 7 天内",
        "保修政策：电子类商品享受 1 年官方保修",
    ]
    
    # 假设已创建 vector_store
    retriever = HybridRetriever(vector_store, documents)
    results = retriever.retrieve("无线充电", top_k=3)
    
    for idx, score in results:
        print(f"文档 {idx}: {documents[idx]} (RRF score: {score:.3f})")


# 考点
考点 = """
1. RRF 融合公式：score = Σ 1 / (k + rank_i)
2. 为什么 k=60：平滑参数，避免排名高时分数差异过大
3. 中文分词：使用 jieba 提升 BM25 效果
4. 时间复杂度：向量检索 O(1) 近似，BM25 O(n)，总 O(n)
5. 扩展：可加入权重参数 weights
"""
```

---

## 代码题 2：Reranker 集成

### 题目

实现一个 Reranker 模块，将检索结果用交叉编码器精排，并与检索器组合。

### 参考实现

```python
from sentence_transformers import CrossEncoder
from typing import List, Tuple


class Reranker:
    """交叉编码器精排"""
    
    def __init__(self, model_name: str = "BAAI/bge-reranker-v2-m3"):
        """
        初始化 Reranker
        Args:
            model_name: 交叉编码器模型名
        """
        self.model = CrossEncoder(model_name)
    
    def rerank(self, query: str, documents: List[str], top_n: int = 5) -> List[Tuple[int, float]]:
        """
        精排
        Args:
            query: 查询
            documents: 候选文档列表
            top_n: 返回前 N 个
        Returns: [(doc_index, score), ...]
        """
        # 构建 query-doc pairs
        pairs = [(query, doc) for doc in documents]
        
        # 交叉编码器打分
        scores = self.model.predict(pairs)
        
        # 按分数降序排列
        ranked = sorted(
            enumerate(scores), key=lambda x: x[1], reverse=True
        )
        
        return ranked[:top_n]


class RetrieveAndRerank:
    """检索 + 精排流水线"""
    
    def __init__(self, retriever: HybridRetriever, reranker: Reranker):
        self.retriever = retriever
        self.reranker = reranker
    
    def search(self, query: str, retrieve_k: int = 30, rerank_n: int = 5) -> List[dict]:
        """
        检索 + 精排
        Args:
            query: 查询
            retrieve_k: 检索时返回的候选数
            rerank_n: 精排后保留数
        """
        # 1. 混合检索，召回 top-k
        candidates = self.retriever.retrieve(query, top_k=retrieve_k)
        
        # 2. 提取文档内容
        doc_indices = [idx for idx, _ in candidates]
        doc_texts = [self.retriever.documents[idx] for idx in doc_indices]
        
        # 3. Reranker 精排
        reranked = self.reranker.rerank(query, doc_texts, top_n=rerank_n)
        
        # 4. 返回结果
        results = []
        for local_rank, (doc_idx_in_candidates, score) in enumerate(reranked):
            global_idx = doc_indices[doc_idx_in_candidates]
            results.append({
                "rank": local_rank + 1,
                "doc_index": global_idx,
                "content": doc_texts[doc_idx_in_candidates],
                "reranker_score": float(score),
            })
        
        return results


# 使用示例
def test_reranker():
    documents = [
        "iPhone 15 Pro 支持 MagSafe 无线充电",
        "华为 Mate 60 Pro 支持 88W 有线快充",
        "7 天无理由退货政策",
        "15 天质量问题换货政策",
    ]
    
    retriever = HybridRetriever(vector_store, documents)
    reranker = Reranker("BAAI/bge-reranker-v2-m3")
    pipeline = RetrieveAndRerank(retriever, reranker)
    
    results = pipeline.search("充电", retrieve_k=4, rerank_n=2)
    for r in results:
        print(f"#{r['rank']} {r['content']} (score: {r['reranker_score']:.3f})")


# 考点
考点 = """
1. 交叉编码器原理：query 和 doc 拼接输入，完整交互
2. 双编码器 vs 交叉编码器：检索用双编码器（快），精排用交叉编码器（准）
3. 典型流程：检索 top-30 → Reranker → top-5
4. 延迟优化：模型量化、批量推理、截断输入
5. 扩展：可级联多个 Reranker（轻量→重量）
"""
```

---

## 代码题 3：RAG 评估实现

### 题目

实现 RAGAS 评估指标中的 Faithfulness（忠实度）评估，检查回答是否忠实于检索上下文。

### 参考实现

```python
from typing import List, Dict
import re


class FaithfulnessEvaluator:
    """
    Faithfulness 评估器
    评估回答中的每个事实是否都能从检索上下文中找到依据
    """
    
    def __init__(self, llm):
        self.llm = llm
    
    def _extract_claims(self, answer: str) -> List[str]:
        """从回答中提取事实性陈述"""
        prompt = f"""从以下回答中提取所有可验证的事实性陈述。
每个陈述应是一个独立的、可被验证的事实。
输出格式：每行一个陈述，编号。

回答：{answer}

事实性陈述："""
        response = self.llm.invoke(prompt).content
        claims = [line.strip() for line in response.split("\n") 
                  if line.strip() and not line.strip().startswith("事实")]
        return claims
    
    def _check_claim(self, claim: str, context: str) -> bool:
        """检查单个陈述是否被上下文支持"""
        prompt = f"""判断以下陈述是否能从给定的上下文中找到依据。

上下文：{context}

陈述：{claim}

如果能从上下文中找到依据（直接或推断），回答 'supported'
如果不能，回答 'unsupported'
如果上下文与陈述无关，回答 'unsupported'

判断："""
        response = self.llm.invoke(prompt).content.strip().lower()
        return "supported" in response
    
    def evaluate(self, question: str, answer: str, contexts: List[str]) -> Dict:
        """
        评估 Faithfulness
        Returns:
        {
            "faithfulness": 0.8,
            "claims": ["陈述1", "陈述2", ...],
            "supported": ["陈述1", ...],
            "unsupported": ["陈述2", ...],
            "details": "评估详情"
        }
        """
        context = "\n".join(contexts)
        
        # 1. 提取事实性陈述
        claims = self._extract_claims(answer)
        if not claims:
            return {"faithfulness": 1.0, "claims": [], "supported": [], "unsupported": []}
        
        # 2. 逐条检查
        supported = []
        unsupported = []
        for claim in claims:
            if self._check_claim(claim, context):
                supported.append(claim)
            else:
                unsupported.append(claim)
        
        # 3. 计算分数
        faithfulness = len(supported) / len(claims)
        
        return {
            "faithfulness": faithfulness,
            "claims": claims,
            "supported": supported,
            "unsupported": unsupported,
            "details": f"共 {len(claims)} 个事实性陈述，{len(supported)} 个被支持，{len(unsupported)} 个未被支持",
        }


# 使用示例
def test_faithfulness():
    evaluator = FaithfulnessEvaluator(llm)
    
    question = "iPhone 15 Pro 支持快充吗？"
    answer = "iPhone 15 Pro 支持最高 27W 有线快充和 15W 无线充电。"
    contexts = [
        "iPhone 15 Pro 支持 27W PD 快充，MagSafe 无线充电 15W。",
        "iPhone 15 Pro 电池容量为 3274mAh。",
    ]
    
    result = evaluator.evaluate(question, answer, contexts)
    print(f"Faithfulness: {result['faithfulness']:.2f}")
    print(f"不支持的陈述: {result['unsupported']}")


# 考点
考点 = """
1. Faithfulness 定义：回答中事实被上下文支持的占比
2. 评估方法：LLM-as-a-Judge，用 LLM 判断每个事实
3. 与其他指标区别：
   - Faithfulness：回答是否忠实于上下文
   - Answer Relevancy：回答是否切题
   - Context Precision：检索结果是否精准
4. 局限性：LLM 评判可能不准确，建议多模型交叉验证
5. 扩展：可加入答案正确性（Answer Correctness）对比真值
"""
```

---

## 代码题 4：带引用的 RAG 生成

### 题目

实现一个生成函数，要求 LLM 在回答中标注引用来源，并解析引用。

### 参考实现

```python
from typing import List, Dict
import re


class CitedRAGGenerator:
    """带引用的 RAG 生成器"""
    
    def __init__(self, llm):
        self.llm = llm
    
    def generate(self, query: str, documents: List[Dict]) -> Dict:
        """
        生成带引用的回答
        
        Args:
            query: 用户问题
            documents: [{"id": "doc_1", "content": "..."}, ...]
        
        Returns:
            {"answer": "回答... [1][2]", "citations": [...]}
        """
        # 1. 构建带编号的上下文
        context_parts = []
        for i, doc in enumerate(documents, 1):
            context_parts.append(f"[{i}] {doc['content']}")
        context = "\n\n".join(context_parts)
        
        # 2. Prompt 要求引用
        prompt = f"""请基于以下文档回答问题，并在引用内容后标注来源编号。

文档：
{context}

问题：{query}

要求：
1. 只基于文档回答，不编造
2. 每个关键事实后标注来源编号，如 [1] 或 [1][2]
3. 文档中没有的信息回答"文档中未提及"
4. 回答简洁、准确

回答："""
        
        answer = self.llm.invoke(prompt).content
        
        # 3. 解析引用
        citations = self._parse_citations(answer, documents)
        
        return {
            "answer": answer,
            "citations": citations,
        }
    
    def _parse_citations(self, answer: str, documents: List[Dict]) -> List[Dict]:
        """解析回答中的引用编号"""
        # 提取所有 [数字] 引用
        citation_nums = set()
        for match in re.finditer(r"\[(\d+)\]", answer):
            citation_nums.add(int(match.group(1)))
        
        # 映射到文档源
        citations = []
        for num in sorted(citation_nums):
            if 1 <= num <= len(documents):
                doc = documents[num - 1]
                citations.append({
                    "number": num,
                    "source_id": doc.get("id", "unknown"),
                    "content": doc["content"][:100],
                })
        
        return citations


# 使用示例
def test_cited_generation():
    generator = CitedRAGGenerator(llm)
    
    documents = [
        {"id": "policy_1", "content": "7 天无理由退货：商品签收后 7 天内可申请退货。"},
        {"id": "policy_2", "content": "退款时效：审核通过后 1-3 个工作日原路返回。"},
        {"id": "policy_3", "content": "15 天质量问题换货：签收后 15 天内可申请换货。"},
    ]
    
    result = generator.generate("退货后多久能收到退款？", documents)
    print(f"回答：{result['answer']}")
    print(f"引用：{result['citations']}")


# 考点
考点 = """
1. 引用溯源：回答中标注 [1][2] 来源编号，便于追溯
2. Prompt 约束：明确要求"只基于文档、不编造、标注来源"
3. 引用解析：正则提取 [数字]，映射到文档源
4. 扩展：可加入引用验证（检查引用内容是否真的支持回答）
5. 与证据门控配合：低质量文档的引用应标注"参考"而非"依据"
"""
```

---

> 返回 [速记版](quick-revision.md) | 深挖题 [deep-dive.md](deep-dive.md) | 场景题 [scenario.md](scenario.md)