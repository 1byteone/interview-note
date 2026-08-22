# 迷你项目：个人知识库 RAG 系统

> 独立构建一个完整的个人知识库 RAG 系统。通过本项目你将掌握：文档分块、向量存储、多路检索、Reranker 精排、LLM 生成的完整 RAG 流水线。

---

## 项目目标

构建一个个人知识库问答系统，支持：

1. 导入 Markdown / PDF / TXT 格式的文档
2. 文档分块并存入向量库
3. 支持混合检索（向量 + BM25 + RRF 融合）
4. 精排后基于 LLM 生成带引用的回答

## 技术栈

| 组件 | 选型 | 说明 |
|------|------|------|
| 语言/框架 | Python + FastAPI | 提供 RESTful API |
| 文档处理 | LangChain Loaders | PyPDFLoader, DirectoryLoader |
| 分块 | RecursiveCharacterTextSplitter | 递归字符分块 |
| Embedding | BAAI/bge-small-zh-v1.5 | 中文语义模型 |
| 向量库 | Chroma | 轻量嵌入式 |
| BM25 | rank_bm25 | 关键词检索 |
| 融合 | RRF | 排名融合 |
| 精排 | BGE-Reranker | 交叉编码器 |
| LLM | OpenAI / 国产模型 | 生成回答 |

---

## 第 1 步：项目结构

```
mini-rag/
├── app/
│   ├── __init__.py
│   ├── main.py            # FastAPI 入口
│   ├── config.py          # 配置
│   ├── ingest.py          # 文档导入与分块
│   ├── retriever.py       # 混合检索器
│   ├── reranker.py        # 精排
│   ├── generator.py       # LLM 生成
│   └── service.py         # 业务编排
├── docs/                  # 知识库文档目录
├── data/
│   └── chroma_db/         # 向量库持久化
├── tests/
│   └── test_rag.py        # 测试
└── requirements.txt
```

## 第 2 步：文档导入与分块（ingest.py）

```python
# app/ingest.py
import os
from langchain_community.document_loaders import DirectoryLoader, TextLoader
from langchain_community.document_loaders import PyPDFLoader, UnstructuredMarkdownLoader
from langchain.text_splitter import RecursiveCharacterTextSplitter
from langchain_community.embeddings import HuggingFaceEmbeddings
from langchain_community.vectorstores import Chroma
from loguru import logger


class DocumentIngestor:
    """文档导入、分块、向量化"""
    
    def __init__(self, docs_dir: str = "docs", persist_dir: str = "data/chroma_db"):
        self.docs_dir = docs_dir
        self.persist_dir = persist_dir
        self.embeddings = HuggingFaceEmbeddings(
            model_name="BAAI/bge-small-zh-v1.5"
        )
        self.vector_store = Chroma(
            embedding_function=self.embeddings,
            persist_directory=self.persist_dir,
            collection_name="personal_kb",
        )
    
    def load_documents(self) -> list:
        """加载多种格式文档"""
        docs = []
        
        for ext in ["**/*.txt", "**/*.md"]:
            loader = DirectoryLoader(
                self.docs_dir,
                glob=ext,
                loader_cls=TextLoader,
                loader_kwargs={"encoding": "utf-8"},
            )
            docs.extend(loader.load())
        
        # PDF 单独处理
        pdf_dir = os.path.join(self.docs_dir, "pdf")
        if os.path.exists(pdf_dir):
            pdf_loader = DirectoryLoader(pdf_dir, glob="*.pdf", loader_cls=PyPDFLoader)
            docs.extend(pdf_loader.load())
        
        logger.info(f"加载 {len(docs)} 个文档")
        return docs
    
    def chunk_and_store(self):
        """分块并存入向量库"""
        documents = self.load_documents()
        
        # 中文优化的递归分块
        splitter = RecursiveCharacterTextSplitter(
            chunk_size=500,
            chunk_overlap=50,
            separators=["\n\n", "\n", "。", "！", "？", "；", "，", " ", ""],
        )
        chunks = splitter.split_documents(documents)
        logger.info(f"切分为 {len(chunks)} 个片段")
        
        # 保存原始文档（供 BM25 使用）
        self.chunks = chunks
        
        # 存入向量库
        self.vector_store.add_documents(chunks)
        self.vector_store.persist()
        logger.info(f"向量库更新完成，共 {self.vector_store._collection.count()} 条")
        
        return len(chunks)


if __name__ == "__main__":
    ingestor = DocumentIngestor()
    ingestor.chunk_and_store()
```

## 第 3 步：混合检索器（retriever.py）

```python
# app/retriever.py
from rank_bm25 import BM25Okapi
import jieba


class HybridRetriever:
    """混合检索器：向量 + BM25 + RRF 融合"""
    
    def __init__(self, vector_store, chunks, k=30, rrf_k=60):
        self.vector_store = vector_store
        self.chunks = chunks
        self.k = k
        self.rrf_k = rrf_k
        self._build_bm25()
    
    def _build_bm25(self):
        """构建 BM25 索引（jieba 分词）"""
        self.tokenized_docs = [list(jieba.cut(c.page_content)) for c in self.chunks]
        self.bm25 = BM25Okapi(self.tokenized_docs)
    
    def _vector_search(self, query: str):
        """向量检索"""
        return self.vector_store.similarity_search_with_relevance_scores(
            query, k=self.k
        )

    def _bm25_search(self, query: str):
        """BM25 检索"""
        tokenized_query = list(jieba.cut(query))
        scores = self.bm25.get_scores(tokenized_query)
        ranked = sorted(
            range(len(scores)), key=lambda i: scores[i], reverse=True
        )[:self.k]
        return [(self.chunks[i], scores[i]) for i in ranked]
    
    def _rrf(self, vector_results, bm25_results):
        """RRF 融合"""
        scores = {}
        doc_map = {}
        
        for rank, (doc, _) in enumerate(vector_results):
            doc_id = id(doc)
            scores[doc_id] = scores.get(doc_id, 0) + 1 / (self.rrf_k + rank + 1)
            doc_map[doc_id] = doc
        
        for rank, (doc, _) in enumerate(bm25_results):
            doc_id = id(doc)
            scores[doc_id] = scores.get(doc_id, 0) + 1 / (self.rrf_k + rank + 1)
            doc_map[doc_id] = doc
        
        ranked = sorted(scores.items(), key=lambda x: x[1], reverse=True)
        return [(doc_map[doc_id], score) for doc_id, score in ranked]
    
    def retrieve(self, query: str, top_k: int = 10):
        """混合检索"""
        vector_results = self._vector_search(query)
        bm25_results = self._bm25_search(query)
        fused = self._rrf(vector_results, bm25_results)
        return fused[:top_k]
```

## 第 4 步：Reranker 精排（reranker.py）

```python
# app/reranker.py
from sentence_transformers import CrossEncoder


class Reranker:
    """交叉编码器精排"""
    
    def __init__(self, model_name: str = "BAAI/bge-reranker-v2-m3"):
        self.model = CrossEncoder(model_name)
    
    def rerank(self, query: str, candidates: list, top_n: int = 5):
        """精排"""
        pairs = [(query, doc.page_content) for doc, _ in candidates]
        scores = self.model.predict(pairs)
        
        ranked = sorted(
            zip(candidates, scores),
            key=lambda x: x[1],
            reverse=True,
        )
        return ranked[:top_n]
```

## 第 5 步：LLM 生成（generator.py）

```python
# app/generator.py
from langchain_openai import ChatOpenAI


class Generator:
    """LLM 生成回答（带引用）"""
    
    def __init__(self, model: str = "gpt-4o-mini", temperature: float = 0):
        self.llm = ChatOpenAI(model=model, temperature=temperature)
    
    def generate(self, query: str, docs: list) -> dict:
        """生成带引用的回答"""
        context = "\n".join(
            [f"[{i+1}] {doc.page_content}" for i, (doc, _) in enumerate(docs)]
        )
        
        prompt = f"""请基于以下知识库文档回答问题，并在引用处标注来源编号。

知识库文档：
{context}

问题：{query}

要求：
1. 只基于文档回答，文档没有的信息请明确说明
2. 引用内容后标注来源编号，如 [1][2]
3. 回答简洁、准确

回答："""
        
        answer = self.llm.invoke(prompt).content
        
        # 解析引用编号
        import re
        citations = sorted(set(int(x) for x in re.findall(r"\[(\d+)\]", answer)))
        
        sources = [
            {
                "source": docs[i-1][0].metadata.get("source", "unknown"),
                "content": docs[i-1][0].page_content[:200],
            }
            for i in citations if 1 <= i <= len(docs)
        ]
        
        return {"answer": answer, "sources": sources}
```

## 第 6 步：业务编排与 API（service.py + main.py）

```python
# app/service.py
from .ingest import DocumentIngestor
from .retriever import HybridRetriever
from .reranker import Reranker
from .generator import Generator


class RAGService:
    """RAG 业务编排"""
    
    def __init__(self):
        self.ingestor = DocumentIngestor()
        self.chunks = self.ingestor.chunks or self.ingestor.load_documents()
        self.retriever = HybridRetriever(self.ingestor.vector_store, self.chunks)
        self.reranker = Reranker()
        self.generator = Generator()
    
    def ask(self, query: str, top_k: int = 10) -> dict:
        """完整 RAG 流程"""
        # 1. 混合检索（向量 + BM25 + RRF）
        candidates = self.retriever.retrieve(query, top_k=top_k)
        
        # 2. Reranker 精排
        reranked = self.reranker.rerank(query, candidates, top_n=5)
        
        # 3. 生成（带引用）
        result = self.generator.generate(query, reranked)
        return result
    
    def ingest_documents(self):
        """导入新文档"""
        return self.ingestor.chunk_and_store()
```

```python
# app/main.py
from fastapi import FastAPI
from pydantic import BaseModel
from .service import RAGService

app = FastAPI(title="个人知识库 RAG 系统")
service = RAGService()


class AskRequest(BaseModel):
    query: str
    top_k: int = 10


@app.post("/ask")
def ask(req: AskRequest):
    return service.ask(req.query, req.top_k)


@app.post("/ingest")
def ingest():
    count = service.ingest_documents()
    return {"status": "ok", "chunks_added": count}


@app.get("/health")
def health():
    return {"status": "ok"}

# 启动：uvicorn app.main:app --reload --port 8000
```

## 第 7 步：测试与验证

```python
# tests/test_rag.py
import requests

BASE_URL = "http://localhost:8000"


def test_ask():
    resp = requests.post(
        f"{BASE_URL}/ask",
        json={"query": "什么是 RAG？", "top_k": 5},
    )
    data = resp.json()
    assert "answer" in data
    assert "sources" in data
    print(f"回答：{data['answer'][:100]}...")
    print(f"来源数量：{len(data['sources'])}")


def test_ingest():
    resp = requests.post(f"{BASE_URL}/ingest")
    data = resp.json()
    assert data["status"] == "ok"
    print(f"新增片段：{data['chunks_added']}")


if __name__ == "__main__":
    test_ask()
    test_ingest()
```

## 验收标准

| 功能 | 验收标准 |
|------|---------|
| 文档导入 | 支持 .md / .txt / .pdf，日志显示分块数量 |
| 混合检索 | 向量 + BM25 结果经过 RRF 融合，返回 top-10 |
| 精排 | Reranker 将最相关文档排在前面 |
| 生成 | 回答带 `[编号]` 引用，`sources` 字段可溯源 |
| 未知问题 | 回答"知识库中没有相关信息"，不编造 |
| API | POST /ask、POST /ingest、GET /health 正常工作 |

## 扩展方向

- 加入 **Graph RAG**：将文档中的实体关系抽取到 Neo4j，支持关系推理
- 加入 **Self-RAG 反思**：生成后自动检查事实一致性
- 加入 **RAGAS 评估**：建立评估数据集，量化检索与生成质量
- 加入 **语义缓存**：相似问题直接复用答案，降低延迟
- 加入 **多轮对话**：支持上下文引用与指代消解

---

## 总结

通过本项目，你完成了：

1. 文档加载与中文分块
2. 向量库存储与混合检索（BM25 + 向量 + RRF）
3. Cross-Encoder 精排
4. LLM 带引用生成
5. FastAPI 服务封装与测试

这是一个完整的、可直接运行的 RAG 系统骨架，之后可在此基础上扩展各种高级能力。