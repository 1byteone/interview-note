# RAG 速查卡 📋

> 面试前30分钟扫一遍，一页纸覆盖核心考点

## 🏷️ 核心概念速记

| 概念 | 一句话解释 | 常见陷阱 |
|------|-----------|----------|
| RAG (检索增强生成) | 检索外部知识 + 注入 LLM 上下文，减少幻觉 | 不是万能的，检索质量决定生成质量，Garbage in Garbage out |
| Embedding | 文本→向量(语义编码)，相似度搜索的基础 | 不同模型向量维度不同(如 text-embedding-3-small=1536)，无法混用 |
| 向量库 | 存储向量 + 近似最近邻搜索(ANN)，如 FAISS/Pinecone/Chroma | 向量库不存源文本，生产需同时存储向量+原文 |
| 分块 (Chunking) | 长文档切小段，块大小/重叠度影响检索精度 | 块太小(上下文缺)块太大(噪声多)都不好，300-500 token 常用 |
| 混合检索 | 向量检索(语义) + 关键词检索(精确) 加权融合 | 单纯向量检索可能漏掉精确关键词匹配，混合更鲁棒 |
| RRF (Reciprocal Rank Fusion) | 多路检索结果排序融合，按排名倒数求和 | 对排名靠后的结果惩罚大，适合多路精排 |
| Reranker | 重排序模型，对候选结果精排(交叉编码器) | 比向量检索慢(需要 pairwise 计算)，但精度提升显著 |
| 分块策略 | 固定大小(简单)、语义分块(按段落/句号)、递归分块 | 一种策略不适用所有文档，需按文档类型测试 |
| 幻觉 (Hallucination) | LLM 生成的不基于事实的内容 | RAG 检索到正确资料但 LLM 可能忽略，需 prompt 强约束 |
| RAGAS 评估 | 评估 RAG 系统：忠实性(faithfulness) + 答案相关性 + 上下文精度/召回 | 评估需要标注数据或 LLM 作为裁判，成本不可忽视 |
| 上下文压缩 | 检索后压缩/过滤不相关片断，减少 LLM 上下文长度 | 压缩可能丢失关键信息，需平衡 |

## 🔧 常用命令/API

```python
# 混合检索 + RRF 实现（核心考点）
import numpy as np
from typing import List, Dict, Any

def reciprocal_rank_fusion(
    results: List[List[Dict[str, Any]]],
    k: int = 60
) -> List[Dict[str, Any]]:
    """
    多路检索结果 RRF 融合
    results: [[{doc_id, score}...], ...] 每路检索结果
    k: 常数，默认 60
    """
    scores = {}
    for rank_list in results:
        for rank, item in enumerate(rank_list, start=1):
            doc_id = item["doc_id"]
            # RRF 公式: score = 1 / (k + rank)
            scores[doc_id] = scores.get(doc_id, 0) + 1 / (k + rank)

    # 按得分降序排序
    sorted_docs = sorted(scores.items(), key=lambda x: x[1], reverse=True)
    return [{"doc_id": doc_id, "score": score} for doc_id, score in sorted_docs]
```

```python
# 完整的 RAG 管线（LCEL 版）
from langchain_community.vectorstores import Chroma
from langchain_openai import OpenAIEmbeddings, ChatOpenAI
from langchain_core.prompts import ChatPromptTemplate
from langchain_core.runnables import RunnablePassthrough
from langchain_core.output_parsers import StrOutputParser
from langchain.retrievers import ContextualCompressionRetriever
from langchain.retrievers.document_compressors import LLMChainExtractor

# 1. 向量库
vectorstore = Chroma(
    embedding_function=OpenAIEmbeddings(model="text-embedding-3-small"),
    persist_directory="./chroma_db"
)

# 2. 基础检索器
base_retriever = vectorstore.as_retriever(search_kwargs={"k": 5})

# 3. 上下文压缩检索器（可选）
compressor = LLMChainExtractor.from_llm(ChatOpenAI(temperature=0))
retriever = ContextualCompressionRetriever(
    base_compressor=compressor,
    base_retriever=base_retriever
)

# 4. RAG Prompt
template = """你是一个专业的问答助手。请基于以下上下文回答用户问题。
如果上下文不足以回答问题，直接说"这个问题我无法从参考资料中找到答案"。

上下文:
{context}

问题: {question}

回答:"""
prompt = ChatPromptTemplate.from_template(template)

# 5. LCEL 管线
def format_docs(docs):
    return "\n\n".join([d.page_content for d in docs])

rag_chain = (
    {"context": retriever | format_docs, "question": RunnablePassthrough()}
    | prompt
    | ChatOpenAI(model="gpt-4o-mini", temperature=0)
    | StrOutputParser()
)

# 使用
result = rag_chain.invoke("什么是 RAG 的核心原理？")
```

```python
# 分块策略示例
from langchain_text_splitters import RecursiveCharacterTextSplitter

# 递归分块（推荐，按段落/句子/字符逐级切）
text_splitter = RecursiveCharacterTextSplitter(
    chunk_size=500,          # 块大小(字符数)
    chunk_overlap=50,        # 块重叠(防止断句)
    separators=["\n\n", "\n", "。", ".", " ", ""],  # 优先级由高到低
    length_function=len,
)

# 语义分块（按 NLTK 句子分割）
from langchain_text_splitters import NLTKTextSplitter
nltk_splitter = NLTKTextSplitter(chunk_size=500)

chunks = text_splitter.split_documents(documents)
```

```python
# RAGAS 评估（简化版）
from ragas import evaluate
from ragas.metrics import faithfulness, answer_relevancy, context_precision

# 需要准备: question, answer, contexts, ground_truth
result = evaluate(
    dataset={
        "question": ["什么是RAG?"],
        "answer": ["RAG是检索增强生成..."],
        "contexts": [["RAG全称Retrieval Augmented Generation..."]],
        "ground_truth": ["RAG是检索增强生成，核心是检索+生成"]
    },
    metrics=[faithfulness, answer_relevancy, context_precision]
)
print(result)
```

## 🎯 面试高频 TOP10

1. **Q: RAG vs 微调？** **A:** RAG 动态知识+低成本+无幻觉(前提检索准)，适合知识库/文档问答；微调固定知识+模型能力转变，适合风格/格式/领域术语学习；两者可互补。
2. **Q: 分块策略怎么选？** **A:** 固定大小(简单，适合通用)、递归分块(推荐，按分隔符逐级)、语义分块(按段落/句子，精度高)；需按文档类型测试，500 token 是常见起点。
3. **Q: 向量库选型？** **A:** FAISS(本地/轻量)、Chroma(开发友好)、Pinecone(云托管/高可用)、Weaviate(自托管)、Milvus(大规模)；选型看数据量、延迟要求、部署方式。
4. **Q: 如何控制幻觉？** **A:** ① 检索质量(高精度分块+reranker) ② Prompt 强约束(无资料则说不知道) ③ 忠实性评估(RAGAS) ④ 生成后验证(事实核对) ⑤ 引用原文。
5. **Q: RAGAS 评估指标有哪些？** **A:** Faithfulness(忠实于上下文)、Answer Relevancy(答案相关)、Context Precision(上下文精确性)、Context Recall(上下文召回率)；各指标 0-1 越接近 1 越好。
6. **Q: 混合检索怎么实现？** **A:** 向量检索(语义) + 关键词检索(BM25/Elasticsearch) 分别检索 → RRF 融合排名 → Reranker 精排；兼顾语义相似和精确匹配。
7. **Q: Reranker 的作用？** **A:** 对初选结果(50-100条) 交叉编码器精排，大幅提升 top-k 精度；但计算量大，只能作为精排阶段，不能做全库检索。
8. **Q: 上下文压缩是什么？** **A:** 检索结果后，用 LLM 或规则过滤掉与问题无关的片段，精简上下文(减少 token 和噪声)，LLMChainExtractor 是典型实现。
9. **Q: RAG 的延迟瓶颈在哪？** **A:** Embedding 生成(API 调用) + 向量检索(索引构建) + Reranker 计算 + LLM 生成；优化：缓存+并行+流式+轻量检索模型。
10. **Q: 如何做多轮对话 RAG？** **A:** 带历史上下文的检索：先用 LLM 改写原问题(结合历史) → 检索 → 注入完整上下文(历史+检索结果) → 生成回答。

## ⚠️ 常见坑 & 最佳实践

| ❌ 坑 | ✅ 正确做法 |
|-------|------------|
| 检索质量差还在 prompt 上死磕 | 先优化分块/检索策略，再调 prompt |
| 不区分文档类型，统一分块策略 | 按文档类型(代码/论文/手册) 测试不同 chunk_size 和 separator |
| 向量库只存向量不存原文 | 同时存向量 + 原文 + 元信息，便于展示和溯源 |
| 检索只取 top-1，遗漏相关信息 | 取 top-5~10，配合 reranker 精排，再注入 LLM |
| 忽略 prompt 对 RAG 的约束 | 明确"没有上下文就直说不知道"，防止 LLM 自由发挥 |
| 不评估就在生产用 | 先离线评估(RAGAS) + 人工标注，达标再上线 |
| 全文直接塞进 LLM 上下文 | 分块+检索+压缩，控制上下文在合理长度(4K-8K token) |
| 混合检索权重手动调 | 让 RRF 自动融合，或用学习权重(如动态加权) |

## 📐 架构设计要点

- **数据管道**：文档清洗 → 分块(chunk) → Embedding → 入库(向量库+原文) → 元信息索引。
- **检索流程**：query → 改写/扩展 → 多路检索(向量+关键词) → RRF 融合 → Reranker 精排 → 注入 LLM。
- **质量监控**：RAGAS 指标持久化 + 用户反馈收集 + A/B 测试，持续迭代。
- **性能优化**：向量检索用 HNSW 索引加速、Embedding 缓存/Cache、Reranker 异步并行、LLM 流式输出。
- **安全**：文档权限过滤(检索时注入用户权限)、敏感内容过滤、内容审核。

## 🔗 关联技术

- **LangChain**：RAG 的常用实现框架，提供文档加载/分割/检索/生成全链路组件。
- **LangGraph**：复杂 RAG 流程(多步检索/条件分支/多轮对话) 用图编排。
- **向量数据库**：Chroma/Pinecone/Weaviate/Milvus，RAG 的存储核心。
- **Elasticsearch**：关键词检索+向量检索混合(ES 8.0+ 已支持向量)。
- **OpenAI Embeddings**：text-embedding-3-small/large 向量化接口。