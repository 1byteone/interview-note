# Embedding 与向量数据库

> 深入理解文本 Embedding 的原理、主流 Embedding 模型的选型对比、向量数据库的部署与使用，并通过商品文档向量化的实战案例掌握核心技能。

---

## 1. 什么是 Embedding？

Embedding（嵌入）是将文本、图像等非结构化数据转化为**固定维度的浮点数向量**的过程。这些向量在语义空间中具有以下特性：

- **语义相近的文本，向量距离也相近**
- "苹果"和"iPhone"的向量比"苹果"和"拖拉机"更接近
- 支持向量运算：`King - Man + Woman ≈ Queen`

### 1.1 Embedding 的数学原理

```
文本 → Tokenizer 分词 → Token IDs → Embedding Layer → 稠密向量 [0.12, -0.34, ...]
```

- 输出向量的维度通常为 256~3072 维
- 每个维度编码了文本的某些语义特征
- 维度越高，信息容量越大，但计算成本也越高

---

## 2. 主流 Embedding 模型对比

### 2.1 国外模型

| 模型 | 维度 | 最大输入 | 特点 | 适用场景 |
|------|------|---------|------|---------|
| OpenAI text-embedding-ada-002 | 1536 | 8191 tokens | 通用性强，API 调用 | 英文为主，通用场景 |
| OpenAI text-embedding-3-small | 1536 | 8191 tokens | 性价比更高，可降维 | 通用场景，预算敏感 |
| OpenAI text-embedding-3-large | 3072 | 8191 tokens | 精度最高，价格最高 | 高质量检索场景 |
| Cohere Embed v3 | 1024 | 512 tokens | 多语言支持好 | 多语言检索 |
| sentence-transformers/all-MiniLM-L6-v2 | 384 | 256 tokens | 轻量级，本地部署 | 快速原型，资源受限环境 |

### 2.2 国产模型

| 模型 | 维度 | 最大输入 | 特点 | 适用场景 |
|------|------|---------|------|---------|
| BAAI/bge-large-zh-v1.5 | 1024 | 512 tokens | 中文语义理解强，开源可商用 | 中文企业场景首选 |
| BAAI/bge-m3 | 1024 | 8192 tokens | 多语言、多粒度、多功能 | 多语言混合场景 |
| text2vec-large-chinese | 1024 | 512 tokens | 中文语义匹配 | 中文语义搜索 |
| M3E (moka-ai/m3e-base) | 768 | 512 tokens | 轻量中文模型 | 中文场景，资源有限 |
| 智源 BGE-zh-v1.5 | 1024 | 512 tokens | 中文检索任务 SOTA | 中文检索 |

### 2.3 选型建议

```python
# 场景 1：中文企业级应用 → 推荐 BGE-m3
embeddings = HuggingFaceEmbeddings(model_name="BAAI/bge-m3")

# 场景 2：快速原型开发 → 推荐 OpenAI text-embedding-3-small
embeddings = OpenAIEmbeddings(model="text-embedding-3-small")

# 场景 3：本地部署，资源受限 → 推荐 M3E
embeddings = HuggingFaceEmbeddings(model_name="moka-ai/m3e-base")
```

---

## 3. 向量相似度计算

### 3.1 三种常见距离度量

```python
import numpy as np

# 示例向量
vec_a = np.array([0.1, 0.2, 0.3])
vec_b = np.array([0.2, 0.3, 0.4])

# 1. 余弦相似度（Cosine Similarity）— 最常用
# 关注方向而非长度，适合文本语义匹配
cosine_sim = np.dot(vec_a, vec_b) / (np.linalg.norm(vec_a) * np.linalg.norm(vec_b))
# 范围：[-1, 1]，越大越相似

# 2. 内积（Dot Product）— 当向量已归一化时等价于余弦
dot_product = np.dot(vec_a, vec_b)

# 3. 欧氏距离（Euclidean Distance）— 关注绝对距离
euclidean_dist = np.linalg.norm(vec_a - vec_b)
# 范围：[0, ∞)，越小越相似
```

### 3.2 使用场景对比

| 度量方式 | 特点 | 适用场景 |
|---------|------|---------|
| **余弦相似度** | 对向量长度不敏感，关注方向 | 文本检索、语义匹配（默认推荐） |
| **内积** | 当向量已 L2 归一化时等价于余弦 | 高性能场景，已归一化的向量库 |
| **欧氏距离** | 对向量长度敏感 | 聚类、图像 Embedding |

---

## 4. 向量数据库

### 4.1 主流向量数据库对比

| 数据库 | 部署方式 | 索引类型 | 特点 | 适用场景 |
|--------|---------|---------|------|---------|
| **Chroma** | 嵌入式/Python | HNSW | 轻量、零配置、适合原型 | 开发原型、小规模 |
| **FAISS** | 嵌入式/Python | IVF, HNSW, PQ | 高性能、Facebook 开源 | 大规模离线检索 |
| **Milvus** | 分布式服务 | IVF, HNSW, DiskANN | 云原生、水平扩展 | 生产级大规模检索 |
| **Qdrant** | 分布式服务 | HNSW | RESTful API、过滤器丰富 | 生产级，需丰富过滤 |
| **Pinecone** | 云服务 | 托管 | 无需运维、按量付费 | 快速上线，不关心运维 |
| **Weaviate** | 分布式服务 | HNSW | 自带 Embedding 服务 | 一体化方案 |

### 4.2 Chroma 快速上手

```python
from langchain_community.vectorstores import Chroma
from langchain_community.embeddings import HuggingFaceEmbeddings

# 1. 初始化 Embedding
embeddings = HuggingFaceEmbeddings(model_name="BAAI/bge-small-zh-v1.5")

# 2. 创建向量库
vector_store = Chroma(
    embedding_function=embeddings,
    persist_directory="./chroma_db",  # 持久化目录
)

# 3. 添加文档
vector_store.add_texts(
    texts=["苹果手机支持 5G 网络", "华为手机支持卫星通信"],
    metadatas=[{"source": "product_manual"}, {"source": "product_manual"}],
    ids=["doc_1", "doc_2"],
)

# 4. 相似度检索
results = vector_store.similarity_search_with_score(
    "哪款手机支持卫星通信？",
    k=2,
)
for doc, score in results:
    print(f"文档：{doc.page_content}，相似度：{score:.4f}")
```

### 4.3 FAISS 高性能检索

```python
from langchain_community.vectorstores import FAISS

# FAISS 适合大规模离线检索，支持 GPU 加速
vector_store = FAISS.from_documents(docs, embeddings)
vector_store.save_local("./faiss_index")

# 加载并使用
loaded_store = FAISS.load_local("./faiss_index", embeddings)
results = loaded_store.similarity_search("查询内容", k=5)
```

---

## 5. 最小案例：商品文档向量化

### 场景

AI 商城有 1000 份商品规格文档，需要将其向量化存入 Chroma，支持商品的语义搜索。

```python
import os
from langchain_community.document_loaders import DirectoryLoader
from langchain.text_splitter import RecursiveCharacterTextSplitter
from langchain_community.embeddings import HuggingFaceEmbeddings
from langchain_community.vectorstores import Chroma

# 1. 加载文档
loader = DirectoryLoader(
    "./product_docs/",
    glob="**/*.txt",
    show_progress=True,
)
documents = loader.load()
print(f"加载了 {len(documents)} 个文档")

# 2. 文档分块
text_splitter = RecursiveCharacterTextSplitter(
    chunk_size=500,
    chunk_overlap=50,
    separators=["\n\n", "\n", "。", "！", "？", " ", ""],
)
chunks = text_splitter.split_documents(documents)
print(f"切分为 {len(chunks)} 个片段")

# 3. 初始化 Embedding 模型
embeddings = HuggingFaceEmbeddings(model_name="BAAI/bge-m3")

# 4. 创建向量库并持久化
vector_store = Chroma.from_documents(
    documents=chunks,
    embedding=embeddings,
    persist_directory="./product_vector_db",
    collection_name="product_docs",
)
vector_store.persist()
print("向量库已创建并持久化")

# 5. 测试检索
query = "支持无线充电的手机有哪些？"
results = vector_store.similarity_search(query, k=3)
print(f"\n查询：{query}")
for i, doc in enumerate(results, 1):
    print(f"{i}. {doc.page_content[:100]}...")
```

---

## 6. Embedding 最佳实践

### 6.1 输入预处理

```python
# 1. 统一编码（中文文档）
text = text.encode("utf-8").decode("utf-8")

# 2. 去除噪声
import re
text = re.sub(r"\s+", " ", text)  # 合并空白
text = text.strip()

# 3. 长度控制
max_tokens = 512  # BGE 模型限制
# 超出部分需截断或分块
```

### 6.2 性能优化

| 优化手段 | 说明 | 效果 |
|---------|------|------|
| **批量 Embedding** | 一次传入多段文本，利用 GPU 并行 | 吞吐量提升 5-10x |
| **向量归一化** | 提前 L2 归一化，检索时可用内积替代余弦 | 检索速度提升 30%+ |
| **索引类型选择** | HNSW 适合高精度，IVF 适合高吞吐 | 根据场景选择 |
| **缓存** | 高频查询的向量结果缓存到 Redis | 延迟降低 50%+ |

---

## 总结

本章你学会了：

- Embedding 的概念与数学原理
- 主流 Embedding 模型的选型对比（国外 vs 国产）
- 三种向量相似度度量方式及其适用场景
- 向量数据库的选型与 Chroma/FAISS 的使用
- 商品文档向量化的完整实战流程
- Embedding 的最佳实践与性能优化

下一步：学习 [文档分块策略](../02-core/01-document-chunking.md)，掌握将长文档切分为适合检索片段的技术。