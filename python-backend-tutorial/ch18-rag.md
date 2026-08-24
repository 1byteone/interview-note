# 第十八章：RAG 检索增强生成（P2 实战）

> 📖 **参考资料**：[LangChain RAG](https://python.langchain.com/docs/tutorials/rag/) | [pgvector](https://github.com/pgvector/pgvector) | [OpenAI Embeddings](https://platform.openai.com/docs/guides/embeddings)

---

## 18.1 RAG Pipeline 架构

RAG（Retrieval-Augmented Generation）通过检索外部知识增强 LLM 生成质量，是企业级 AI 应用的核心范式。

```
┌─────────────────────────────────────────────────────────────────┐
│                     RAG Pipeline 全流程                          │
│                                                                 │
│  ┌─────────┐   ┌─────────┐   ┌──────────┐   ┌──────────┐     │
│  │  Parse  │──▶│  Chunk  │──▶│  Embed   │──▶│  Store   │     │
│  │ 文档解析 │   │ 分块策略 │   │ 向量嵌入 │   │ pgvector │     │
│  └─────────┘   └─────────┘   └──────────┘   └──────────┘     │
│                                                        │       │
│  ┌──────────┐   ┌──────────┐   ┌──────────┐          │       │
│  │ Generate │◀──│  Rerank  │◀──│ Retrieve │◀─────────┘       │
│  │ LLM 生成 │   │ 重排序   │   │ 混合检索 │                   │
│  └──────────┘   └──────────┘   └──────────┘                   │
└─────────────────────────────────────────────────────────────────┘
```

### 核心组件对照表

| 组件 | 职责 | 技术选型 |
|------|------|----------|
| Parse | 多格式文档解析 | PyPDFLoader, Unstructured |
| Chunk | 文本分块 | RecursiveCharacterTextSplitter |
| Embed | 向量嵌入 | OpenAI / BGE / Jina |
| Store | 向量存储 | pgvector (HNSW/IVFFlat) |
| Retrieve | 混合检索 | BM25 + 向量相似度 |
| Rerank | 重排序 | bge-reranker / Cohere |
| Generate | LLM 生成 | GPT-4o / Claude |

---

## 18.2 文档解析与分块

```python
# rag/chunker.py
from dataclasses import dataclass, field
from langchain_text_splitters import RecursiveCharacterTextSplitter


@dataclass
class DocumentChunk:
    """文档分块数据模型"""
    content: str
    metadata: dict = field(default_factory=dict)
    chunk_id: str = ""
    embedding: list[float] | None = None


class DocumentChunker:
    """智能文档分块器 — 支持多种分块策略"""

    def __init__(
        self,
        chunk_size: int = 512,
        chunk_overlap: int = 64,
        separators: list[str] | None = None,
    ):
        self.splitter = RecursiveCharacterTextSplitter(
            chunk_size=chunk_size,
            chunk_overlap=chunk_overlap,
            length_function=len,
            separators=separators or ["\n\n", "\n", "。", "！", "？", ".", " "],
        )

    def chunk_text(self, text: str, metadata: dict | None = None) -> list[DocumentChunk]:
        """将文本切分为带元数据的分块"""
        metadata = metadata or {}
        splits = self.splitter.split_text(text)

        chunks = []
        for i, split in enumerate(splits):
            chunks.append(DocumentChunk(
                content=split,
                metadata={**metadata, "chunk_index": i, "total_chunks": len(splits)},
                chunk_id=f"{metadata.get('doc_id', 'unknown')}_{i}",
            ))
        return chunks


# --- 使用示例 ---
if __name__ == "__main__":
    sample = """RAG 检索增强生成是现代 AI 应用的核心技术。它通过检索外部知识库来增强大语言模型的生成质量。
    在企业级场景中，RAG 可以有效减少幻觉，提升回答准确性。分块策略直接影响检索质量。"""

    chunker = DocumentChunker(chunk_size=100, chunk_overlap=20)
    chunks = chunker.chunk_text(sample, metadata={"doc_id": "rag_intro", "source": "tutorial"})
    for c in chunks:
        print(f"[{c.chunk_id}] {c.content[:50]}...")
```

---

## 18.3 pgvector 存储与索引

### SQLAlchemy pgvector 模型

```python
# rag/models.py
from datetime import datetime
from sqlalchemy import String, Text, Float, Index, text
from sqlalchemy.orm import DeclarativeBase, Mapped, mapped_column
from pgvector.sqlalchemy import Vector


class Base(DeclarativeBase):
    pass


class DocumentEmbedding(Base):
    """pgvector 向量存储模型"""
    __tablename__ = "document_embeddings"

    id: Mapped[int] = mapped_column(primary_key=True, autoincrement=True)
    doc_id: Mapped[str] = mapped_column(String(64), index=True)
    chunk_id: Mapped[str] = mapped_column(String(128), unique=True)
    content: Mapped[str] = mapped_column(Text)
    embedding: Mapped[list[float]] = mapped_column(Vector(1536))
    metadata_json: Mapped[str | None] = mapped_column(Text, default=None)
    created_at: Mapped[datetime] = mapped_column(default=datetime.utcnow)

    __table_args__ = (
        # HNSW 索引 — 适合高召回场景（推荐）
        Index(
            "ix_embedding_hnsw",
            "embedding",
            postgresql_using="hnsw",
            postgresql_with={"m": 16, "ef_construction": 64},
            postgresql_ops={"embedding": "vector_cosine_ops"},
        ),
    )


# IVFFlat 备选索引（适合数据量 < 100 万）
# CREATE INDEX ix_embedding_ivf ON document_embeddings
#   USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);
```

### HNSW vs IVFFlat 对比

| 索引类型 | 查询速度 | 构建速度 | 内存占用 | 适用场景 |
|----------|----------|----------|----------|----------|
| HNSW | ⚡ 极快 | 中等 | 高 | 生产环境推荐，百万级数据 |
| IVFFlat | 快 | 快 | 低 | 数据量 < 100 万，内存受限 |
| Flat | 慢 | 无需构建 | 中 | 小数据集 (< 10 万) |

---

## 18.4 混合检索（向量 + BM25）

```python
# rag/hybrid_search.py
import numpy as np
from sqlalchemy import select, text, func
from sqlalchemy.ext.asyncio import AsyncSession

from .models import DocumentEmbedding


class HybridRetriever:
    """混合检索器：向量相似度 + BM25 关键词匹配"""

    def __init__(self, session: AsyncSession, alpha: float = 0.7):
        """
        Args:
            alpha: 向量检索权重 (0~1)，1-alpha 为 BM25 权重
        """
        self.session = session
        self.alpha = alpha

    async def vector_search(
        self, query_embedding: list[float], top_k: int = 10
    ) -> list[tuple[str, float]]:
        """pgvector 余弦相似度检索"""
        stmt = (
            select(
                DocumentEmbedding.chunk_id,
                DocumentEmbedding.content,
                (1 - DocumentEmbedding.embedding.cosine_distance(query_embedding)).label("score"),
            )
            .order_by(DocumentEmbedding.embedding.cosine_distance(query_embedding))
            .limit(top_k)
        )
        result = await self.session.execute(stmt)
        return [(row.chunk_id, float(row.score)) for row in result.all()]

    async def bm25_search(self, query: str, top_k: int = 10) -> list[tuple[str, float]]:
        """BM25 关键词检索（使用 PostgreSQL 全文搜索模拟）"""
        stmt = (
            select(
                DocumentEmbedding.chunk_id,
                DocumentEmbedding.content,
                func.ts_rank_cd(
                    func.to_tsvector("simple", DocumentEmbedding.content),
                    func.plainto_tsquery("simple", query),
                ).label("score"),
            )
            .where(
                func.to_tsvector("simple", DocumentEmbedding.content).match(query)
            )
            .order_by(text("score DESC"))
            .limit(top_k)
        )
        result = await self.session.execute(stmt)
        return [(row.chunk_id, float(row.score)) for row in result.all()]

    async def hybrid_search(
        self,
        query: str,
        query_embedding: list[float],
        top_k: int = 10,
    ) -> list[dict]:
        """融合检索：RRF (Reciprocal Rank Fusion) + 加权分数"""
        vector_results = await self.vector_search(query_embedding, top_k=top_k * 2)
        bm25_results = await self.bm25_search(query, top_k=top_k * 2)

        # RRF 融合
        scores: dict[str, float] = {}
        for rank, (chunk_id, _) in enumerate(vector_results):
            scores[chunk_id] = scores.get(chunk_id, 0) + self.alpha / (60 + rank)
        for rank, (chunk_id, _) in enumerate(bm25_results):
            scores[chunk_id] = scores.get(chunk_id, 0) + (1 - self.alpha) / (60 + rank)

        ranked = sorted(scores.items(), key=lambda x: x[1], reverse=True)[:top_k]
        return [{"chunk_id": cid, "score": s} for cid, s in ranked]
```

---

## 18.5 Reranker 重排序

```python
# rag/reranker.py
from dataclasses import dataclass
from sentence_transformers import CrossEncoder


@dataclass
class RerankResult:
    chunk_id: str
    content: str
    original_score: float
    rerank_score: float


class CrossEncoderReranker:
    """基于 CrossEncoder 的重排序器"""

    def __init__(self, model_name: str = "BAAI/bge-reranker-v2-m3"):
        self.model = CrossEncoder(model_name, max_length=512)

    def rerank(
        self,
        query: str,
        candidates: list[dict],
        top_k: int = 5,
    ) -> list[RerankResult]:
        """
        对候选文档进行精排

        Args:
            query: 用户查询
            candidates: [{"chunk_id": ..., "content": ..., "score": ...}]
            top_k: 返回前 k 条
        """
        if not candidates:
            return []

        pairs = [(query, c["content"]) for c in candidates]
        scores = self.model.predict(pairs)

        reranked = sorted(
            zip(candidates, scores),
            key=lambda x: x[1],
            reverse=True,
        )[:top_k]

        return [
            RerankResult(
                chunk_id=cand["chunk_id"],
                content=cand["content"],
                original_score=cand.get("score", 0.0),
                rerank_score=float(score),
            )
            for cand, score in reranked
        ]
```

---

## 18.6 完整 QA 流程

```python
# rag/pipeline.py
import openai
from sqlalchemy.ext.asyncio import create_async_engine, AsyncSession, async_sessionmaker

from .models import Base, DocumentEmbedding
from .chunker import DocumentChunker
from .hybrid_search import HybridRetriever
from .reranker import CrossEncoderReranker


class RAGPipeline:
    """端到端 RAG 流水线"""

    def __init__(self, db_url: str, openai_key: str):
        self.engine = create_async_engine(db_url)
        self.session_factory = async_sessionmaker(self.engine, class_=AsyncSession)
        self.embed_client = openai.AsyncOpenAI(api_key=openai_key)
        self.chunker = DocumentChunker(chunk_size=512, chunk_overlap=64)
        self.reranker = CrossEncoderReranker()

    # ── 索引阶段 ──

    async def ingest(self, doc_id: str, text: str, source: str = ""):
        """文档摄入：解析 → 分块 → 嵌入 → 存储"""
        chunks = self.chunker.chunk_text(text, metadata={"doc_id": doc_id, "source": source})

        # 批量嵌入（OpenAI 限制每批 2048 条）
        contents = [c.content for c in chunks]
        resp = await self.embed_client.embeddings.create(
            model="text-embedding-3-small", input=contents
        )
        embeddings = [item.embedding for item in resp.data]

        async with self.session_factory() as session:
            for chunk, emb in zip(chunks, embeddings):
                session.add(DocumentEmbedding(
                    doc_id=doc_id,
                    chunk_id=chunk.chunk_id,
                    content=chunk.content,
                    embedding=emb,
                ))
            await session.commit()

    # ── 查询阶段 ──

    async def query(self, question: str, top_k: int = 3) -> str:
        """完整 QA：检索 → 重排 → 生成"""
        # 1. 嵌入查询
        resp = await self.embed_client.embeddings.create(
            model="text-embedding-3-small", input=[question]
        )
        q_emb = resp.data[0].embedding

        # 2. 混合检索
        async with self.session_factory() as session:
            retriever = HybridRetriever(session, alpha=0.7)
            raw_results = await retriever.hybrid_search(question, q_emb, top_k=10)

            # 补充 content
            for r in raw_results:
                stmt = select(DocumentEmbedding).where(
                    DocumentEmbedding.chunk_id == r["chunk_id"]
                )
                doc = (await session.execute(stmt)).scalar_one_or_none()
                r["content"] = doc.content if doc else ""

        # 3. Rerank
        reranked = self.reranker.rerank(question, raw_results, top_k=top_k)
        context = "\n\n---\n\n".join(r.content for r in reranked)

        # 4. LLM 生成
        llm_resp = await self.embed_client.chat.completions.create(
            model="gpt-4o",
            messages=[
                {"role": "system", "content": "你是专业助手。基于以下上下文回答用户问题，若信息不足请明确说明。"},
                {"role": "user", "content": f"上下文：\n{context}\n\n问题：{question}"},
            ],
            temperature=0.1,
        )
        return llm_resp.choices[0].message.content


# --- 运行示例 ---
if __name__ == "__main__":
    import asyncio

    pipeline = RAGPipeline(
        db_url="postgresql+asyncpg://user:pass@localhost:5432/rag_db",
        openai_key="sk-xxx",
    )

    async def main():
        # 索引
        await pipeline.ingest(
            doc_id="rag_guide",
            text="RAG 检索增强生成通过外部知识库减少大模型幻觉...",
            source="tutorial",
        )
        # 查询
        answer = await pipeline.query("什么是 RAG？它解决了什么问题？")
        print(f"回答：{answer}")

    asyncio.run(main())
```

---

## 必读资源

| 资源 | 链接 | 说明 |
|------|------|------|
| LangChain RAG 教程 | https://python.langchain.com/docs/tutorials/rag/ | 官方 RAG 入门 |
| pgvector 文档 | https://github.com/pgvector/pgvector | PostgreSQL 向量扩展 |
| HNSW 论文 | https://arxiv.org/abs/1603.09320 | 近似最近邻算法原理 |
| LangChain Hybrid Search | https://python.langchain.com/docs/how_to/ensemble_retriever/ | 混合检索实现 |
| bge-reranker | https://huggingface.co/BAAI/bge-reranker-v2-m3 | 开源重排序模型 |
| OpenAI Embeddings Guide | https://platform.openai.com/docs/guides/embeddings | 嵌入模型使用指南 |
