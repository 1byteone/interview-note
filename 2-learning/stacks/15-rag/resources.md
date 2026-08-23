# RAG 推荐资源

> 精选 RAG 学习资源，涵盖官方文档、经典论文、开源项目和进阶阅读。

---

## 官方文档

| 资源 | 链接 | 说明 |
|------|------|------|
| LangChain RAG 文档 | https://python.langchain.com/docs/use_cases/rag/ | LangChain 官方 RAG 用例 |
| LlamaIndex RAG 指南 | https://docs.llamaindex.ai/en/stable/ | 另一个 RAG 框架 |
| Chroma 文档 | https://docs.trychroma.com/ | 轻量级向量数据库 |
| Milvus 文档 | https://milvus.io/docs/ | 生产级向量数据库 |
| RAGAS 文档 | https://docs.ragas.io/ | RAG 评估框架 |
| BGE 模型 | https://github.com/FlagOpen/FlagEmbedding | 国产开源 Embedding 系列 |

---

## 经典论文

| 论文 | 年份 | 核心贡献 |
|------|------|---------|
| [Retrieval-Augmented Generation for Knowledge-Intensive NLP Tasks](https://arxiv.org/abs/2005.11401) | 2020 | RAG 原始论文，提出检索+生成框架 |
| [REALM: Retrieval-Augmented Language Model Pre-Training](https://arxiv.org/abs/2002.08909) | 2020 | 将检索预训练引入语言模型 |
| [HyDE: Precise Zero-Shot Dense Retrieval without Relevance Labels](https://arxiv.org/abs/2212.10496) | 2022 | 假设文档 Embedding 检索 |
| [Self-RAG: Learning to Retrieve, Generate, and Critique through Self-Reflection](https://arxiv.org/abs/2310.11511) | 2023 | 自我反思 RAG |
| [Corrective Retrieval Augmented Generation](https://arxiv.org/abs/2401.15884) | 2024 | 纠错式 RAG |
| [RAGAS: Automated Evaluation of Retrieval Augmented Generation](https://arxiv.org/abs/2309.15217) | 2023 | RAG 评估框架 |
| [Searching for Best Practices in Retrieval-Augmented Generation](https://arxiv.org/abs/2407.01219) | 2024 | RAG 最佳实践综述 |
| [GraphRAG: Unlocking LLM Discovery on Narrative Private Data](https://www.microsoft.com/en-us/research/project/graphrag/) | 2024 | 微软 GraphRAG |
| [When Not to Trust LLMs: Uncertainty Estimation in RAG](https://arxiv.org/abs/2406.03356) | 2024 | RAG 不确定性评估 |

---

## 开源项目

| 项目 | 链接 | 说明 |
|------|------|------|
| LangChain | https://github.com/langchain-ai/langchain | 最流行的 LLM 框架，内置 RAG 支持 |
| LlamaIndex | https://github.com/run-llama/llama_index | 专为 RAG 设计的框架 |
| RAGAS | https://github.com/explodinggradients/ragas | RAG 评估工具 |
| Qdrant | https://github.com/qdrant/qdrant | 向量数据库 |
| Milvus | https://github.com/milvus-io/milvus | 分布式向量数据库 |
| Chroma | https://github.com/chroma-core/chroma | 轻量向量数据库 |
| FlagEmbedding | https://github.com/FlagOpen/FlagEmbedding | BGE 系列模型 |
| FastGPT | https://github.com/labring/FastGPT | 知识库问答平台 |
| Dify | https://github.com/langgenius/dify | LLM 应用开发平台 |
| RAGFlow | https://github.com/infiniflow/ragflow | 开源 RAG 引擎 |

---

## 进阶阅读

### 架构设计

| 文章 | 链接 |
|------|------|
| 微软 RAG 模式 | https://learn.microsoft.com/en-us/azure/search/retrieval-augmented-generation-overview |
| Pinecone RAG 指南 | https://www.pinecone.io/learn/retrieval-augmented-generation/ |
| LangChain RAG 从入门到精通 | https://python.langchain.com/docs/tutorials/rag/ |

### 评估与优化

- RAGAS 官方 Notebook：https://docs.ragas.io/en/latest/getstarted/
- 检索质量评估方法：NDCG, MRR, Recall@K, Precision@K
- 向量索引对比：HNSW vs IVF vs PQ vs DiskANN

### 生产化

- 向量数据库选型：Chroma vs Milvus vs Qdrant vs Pinecone vs Weaviate
- 缓存策略：语义缓存 vs 精确缓存 vs 分层缓存
- 监控指标：P50/P95 延迟、缓存命中率、忠实度、用户满意度

---

## 学习路线

```
第 1 阶段：入门（1-2 周）
  - 阅读 RAG 原始论文
  - 运行 LangChain RAG 示例
  - 完成本教程的 01-basics

第 2 阶段：核心（2-3 周）
  - 掌握分块、检索、Reranker
  - 实现混合检索代码
  - 完成本教程的 02-core

第 3 阶段：高级（2-3 周）
  - 学习 Graph RAG / Self-RAG
  - 实现幻觉控制方案
  - 完成本教程的 03-advanced

第 4 阶段：生产化（2-3 周）
  - 搭建 RAG 评估流水线
  - 部署 RAG 服务（FastAPI + Docker）
  - 完成本教程的 04-projects
```