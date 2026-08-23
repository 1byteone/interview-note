# RAG 快速入门

> 面向 Python 后端开发者的 RAG 入门指南，目标是让你理解 RAG 核心理念、掌握朴素 RAG 流程，并完成一个文档问答的最小案例。

---

## 1. 什么是 RAG？

RAG（Retrieval-Augmented Generation）检索增强生成，是一种**在生成回答前先检索相关文档作为上下文**的技术方案。

### 1.1 为什么需要 RAG？

大语言模型（LLM）存在两个核心问题：

| 问题 | 说明 | RAG 的解法 |
|------|------|-----------|
| **知识截止** | 训练数据停留在某个时间点，不知道最新信息 | 检索最新文档，补充到上下文中 |
| **幻觉** | 模型会"编造"不确定的事实 | 用检索到的真实文档约束生成范围 |
| **领域知识不足** | 通用模型在垂直领域（医疗、法律）表现差 | 注入领域文档作为参考 |
| **数据隐私** | 不能把私域数据上传给模型训练 | 私域数据存储在本地向量库，仅检索片段 |

### 1.2 RAG vs 微调 vs 纯 Prompt

| 方案 | 优势 | 劣势 | 适用场景 |
|------|------|------|---------|
| **纯 Prompt** | 简单、无需额外数据 | 受限于模型知识边界 | 通用问答 |
| **RAG** | 知识实时更新、可解释、低成本 | 检索质量影响生成效果 | 知识库问答、文档检索 |
| **微调** | 模型学会领域语言风格 | 成本高、更新慢、可能遗忘 | 特定格式生成、风格迁移 |

**核心结论：RAG 是知识密集型场景的首选方案。**

---

## 2. 朴素 RAG 流程

朴素 RAG（Naive RAG）是 RAG 的最基础形态，包含三个步骤：

```
用户问题
    ↓
[检索] 从向量库中检索相关文档片段
    ↓
[增强] 将检索到的文档片段拼接到 Prompt 中
    ↓
[生成] LLM 基于增强后的 Prompt 生成回答
    ↓
最终回答
```

### 2.1 检索阶段

```python
# 1. 将用户问题转化为向量
question_embedding = embedding_model.embed_query("商品退换货政策是什么？")

# 2. 在向量库中搜索最相似的文档片段
results = vector_store.similarity_search_by_vector(
    question_embedding, k=3  # 返回 top-3
)
```

### 2.2 增强阶段

```python
# 3. 将检索到的文档片段拼接成上下文
context = "\n\n".join([doc.page_content for doc in results])

# 4. 构建增强后的 Prompt
prompt = f"""请基于以下文档内容回答问题。

文档内容：
{context}

问题：{question}

回答："""
```

### 2.3 生成阶段

```python
# 5. 调用 LLM 生成回答
response = llm.invoke(prompt)
print(response)
```

---

## 3. 最小案例：文档问答系统

### 场景

AI 商城有一份商品退换货政策文档，用户想知道"7 天无理由退货的条件是什么？"

### 3.1 准备数据

```python
# 示例文档片段
documents = [
    "7 天无理由退货：商品签收后 7 天内，在不影响二次销售的前提下，可申请无理由退货。",
    "15 天质量问题换货：商品签收后 15 天内，如出现非人为质量问题，可申请换货。",
    "保修政策：电子类商品享受 1 年官方保修，保修期内免人工费。",
    "退货流程：登录商城 APP → 我的订单 → 申请售后 → 选择退货退款 → 填写原因 → 提交。",
    "退款时效：审核通过后，退款将在 1-3 个工作日原路返回。",
]
```

### 3.2 完整代码

```python
from langchain_community.embeddings import OpenAIEmbeddings
from langchain_community.vectorstores import Chroma
from langchain.text_splitter import RecursiveCharacterTextSplitter
from langchain.chat_models import ChatOpenAI
from langchain.chains import RetrievalQA

# 1. 初始化 Embedding 模型
embeddings = OpenAIEmbeddings(model="text-embedding-ada-002")

# 2. 文档分块
text_splitter = RecursiveCharacterTextSplitter(
    chunk_size=200,
    chunk_overlap=20,
)
docs = text_splitter.create_documents(documents)

# 3. 存入向量库
vector_store = Chroma.from_documents(docs, embeddings)

# 4. 创建检索器
retriever = vector_store.as_retriever(search_kwargs={"k": 2})

# 5. 创建 RAG 链
llm = ChatOpenAI(model="gpt-4o-mini", temperature=0)
qa_chain = RetrievalQA.from_chain_type(
    llm=llm,
    retriever=retriever,
    return_source_documents=True,
)

# 6. 提问
question = "7 天无理由退货的条件是什么？"
result = qa_chain({"query": question})

print(f"回答：{result['result']}")
print(f"\n来源文档：")
for doc in result['source_documents']:
    print(f"  - {doc.page_content}")
```

### 3.3 预期输出

```
回答：商品签收后 7 天内，在不影响二次销售的前提下，可申请无理由退货。
具体流程：登录商城 APP → 我的订单 → 申请售后 → 选择退货退款 → 提交。

来源文档：
  - 7 天无理由退货：商品签收后 7 天内，在不影响二次销售的前提下，可申请无理由退货。
  - 退货流程：登录商城 APP → 我的订单 → 申请售后 → 选择退货退款 → 填写原因 → 提交。
```

---

## 4. RAG 的关键组件

| 组件 | 作用 | 常用工具 |
|------|------|---------|
| **文档加载器** | 从各种格式加载文档（PDF、HTML、Markdown） | LangChain Document Loaders |
| **文本分割器** | 将长文档切分为适合检索的片段 | RecursiveCharacterTextSplitter |
| **Embedding 模型** | 将文本转化为向量 | OpenAI, BGE, text2vec |
| **向量数据库** | 存储向量并支持相似度检索 | Chroma, Milvus, FAISS |
| **检索器** | 根据查询返回相关文档 | VectorStoreRetriever |
| **LLM** | 基于检索到的文档生成回答 | GPT-4, Claude, 国产模型 |

---

## 5. 朴素 RAG 的局限性

| 局限性 | 说明 | 改进方案 |
|--------|------|---------|
| **检索质量依赖分块** | 分块太大噪声多，太小信息不全 | 优化分块策略（见 02-core/01） |
| **单一路径检索** | 仅靠向量检索，精确匹配弱 | 混合检索（见 02-core/02） |
| **查询表达单一** | 用户问题可能表述不清 | 查询转换（见 02-core/03） |
| **无证据校验** | LLM 可能忽略检索结果 | 证据门控（见 03-advanced/02） |
| **无评估机制** | 不知道检索和生成的质量 | RAGAS 评估（见 03-advanced/04） |

---

## 总结

本章你学会了：

- RAG 的核心理念：检索 + 增强 + 生成
- 朴素 RAG 的三步流程：检索文档 → 增强 Prompt → 生成回答
- 使用 LangChain 实现一个最小文档问答系统
- RAG 的关键组件及其作用
- 朴素 RAG 的局限性及后续改进方向

下一步：学习 [Embedding 与向量数据库](02-embedding-and-vector-store.md)，深入理解文本向量化的原理与实践。