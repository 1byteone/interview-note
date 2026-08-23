# 文档分块策略

> 文档分块是 RAG 系统中最关键的预处理步骤之一。分块质量直接影响检索效果，进而影响最终生成质量。本章从基础策略到高级实战，系统讲解文档分块技术。

---

## 1. 为什么需要分块？

LLM 的上下文窗口有限（4K~200K tokens），而原始文档可能长达数十页。分块解决两个核心问题：

| 问题 | 说明 |
|------|------|
| **上下文窗口限制** | 长文档无法一次性放入 LLM 的上下文窗口 |
| **检索精度** | 整篇文档作为检索单元，噪声大、相关性低 |
| **细粒度匹配** | 用户问题通常只涉及文档的某一段落，需要精确定位 |

**好的分块 = 每个片段是一个自包含的、语义完整的知识单元。**

---

## 2. 分块策略对比

### 2.1 固定大小分块（Fixed Size Chunking）

最基础的分块方式，按固定字符数或 token 数切分。

```python
from langchain.text_splitter import TokenTextSplitter

splitter = TokenTextSplitter(
    chunk_size=256,      # 每个块 256 tokens
    chunk_overlap=20,    # 重叠 20 tokens
)
chunks = splitter.split_text(long_text)
```

| 优点 | 缺点 |
|------|------|
| 实现简单，速度快 | 可能切在句子中间，破坏语义 |
| 分块大小可控 | 上下文边界不自然 |

### 2.2 递归字符分块（Recursive Character Text Splitter）

**LangChain 默认推荐的分块方式**，按优先级列表递归切分，尽量保持语义完整。

```python
from langchain.text_splitter import RecursiveCharacterTextSplitter

splitter = RecursiveCharacterTextSplitter(
    chunk_size=500,
    chunk_overlap=50,
    separators=["\n\n", "\n", "。", "！", "？", "，", " ", ""],  # 中文优化
)
chunks = splitter.split_documents(documents)
```

**切分逻辑**：先尝试按段落（`\n\n`）切分，如果段落太大，再按行（`\n`）切分，以此类推。

### 2.3 语义分块（Semantic Chunking）

基于语义相似度或主题边界进行分块，每个块内语义一致。

```python
from langchain_experimental.text_splitter import SemanticChunker
from langchain_openai.embeddings import OpenAIEmbeddings

# 基于 Embedding 相似度判断分块边界
splitter = SemanticChunker(
    embeddings=OpenAIEmbeddings(),
    breakpoint_threshold_type="percentile",  # 相似度低于阈值时切分
    breakpoint_threshold_amount=95,          # 百分位阈值
)
chunks = splitter.split_documents(documents)
```

| 优点 | 缺点 |
|------|------|
| 语义边界自然，块内主题一致 | 需要调用 Embedding 模型，速度慢 |
| 检索效果通常更好 | 实现复杂度高 |

### 2.4 特定文档分块

针对特定文档格式的优化分块策略。

```python
# Markdown 分块 — 按标题层级切分
from langchain.text_splitter import MarkdownHeaderTextSplitter

splitter = MarkdownHeaderTextSplitter(
    headers_to_split_on=[
        ("#", "H1"),
        ("##", "H2"),
        ("###", "H3"),
    ]
)
chunks = splitter.split_text(markdown_text)

# Python 代码分块 — 按函数/类定义切分
from langchain.text_splitter import PythonCodeTextSplitter
splitter = PythonCodeTextSplitter(chunk_size=300, chunk_overlap=30)
```

---

## 3. 分块大小与重叠

### 3.1 分块大小选择

| 分块大小 | 适用场景 | 效果 |
|---------|---------|------|
| **128 tokens** | 短文本问答（FAQ、商品名） | 精确但可能缺少上下文 |
| **256~512 tokens** | 通用文档问答（推荐） | 平衡精度和上下文 |
| **512~1024 tokens** | 长文档摘要、复杂推理 | 上下文丰富但噪声增加 |
| **1024+ tokens** | 需要大上下文的场景 | 易超窗口，检索精度下降 |

### 3.2 分块重叠

重叠分块可以避免关键信息被切分边界截断：

```python
# 无重叠：关键信息可能被切在边界
"7 天无理由退货：商品签收后" | "7 天内，在不影响二次销售的前提下"

# 有重叠：关键信息完整保留
"7 天无理由退货：商品签收后 7 天内，在不影响二次销售的前提下"
"7 天内，在不影响二次销售的前提下，可申请无理由退货。"
```

**推荐重叠比例**：chunk_size 的 10%~20%。例如 chunk_size=500，overlap=50~100。

---

## 4. 元数据保留

分块时保留原始文档的元数据，便于溯源和过滤。

```python
from langchain_community.document_loaders import PyPDFLoader

# 加载 PDF 并保留元数据
loader = PyPDFLoader("./product_manual.pdf")
documents = loader.load()

# 分块时保留元数据
text_splitter = RecursiveCharacterTextSplitter(
    chunk_size=500,
    chunk_overlap=50,
)
chunks = text_splitter.split_documents(documents)

# 每个 chunk 保留 source, page 等元数据
for chunk in chunks[:2]:
    print(f"内容：{chunk.page_content[:50]}...")
    print(f"元数据：{chunk.metadata}")
    # {'source': 'product_manual.pdf', 'page': 3}
```

**常用元数据字段**：

| 字段 | 说明 | 用途 |
|------|------|------|
| `source` | 文档来源（文件名/URL） | 溯源 |
| `page` | 页码 | 引用定位 |
| `title` | 文档标题 | 上下文 |
| `author` | 作者 | 可信度评估 |
| `created_at` | 创建时间 | 时效性过滤 |
| `category` | 文档分类 | 过滤/路由 |

---

## 5. 实战：PDF 文档分块

### 场景

AI 商城有一份 50 页的商品质检报告 PDF，需要分块后存入向量库。

```python
import os
from langchain_community.document_loaders import PyPDFLoader
from langchain.text_splitter import RecursiveCharacterTextSplitter
from langchain_community.embeddings import HuggingFaceEmbeddings
from langchain_community.vectorstores import Chroma

# 1. 加载 PDF
loader = PyPDFLoader("./quality_report.pdf")
documents = loader.load()
print(f"PDF 共 {len(documents)} 页")

# 2. 分块策略
text_splitter = RecursiveCharacterTextSplitter(
    chunk_size=500,
    chunk_overlap=50,
    separators=["\n\n", "\n", "。", "！", "？", "；", "，", " ", ""],
    # 保留元数据
    add_start_index=True,
)
chunks = text_splitter.split_documents(documents)
print(f"切分为 {len(chunks)} 个片段")

# 3. 检查分块效果
for i, chunk in enumerate(chunks[:3]):
    print(f"\n--- 片段 {i+1} ---")
    print(f"来源：第 {chunk.metadata.get('page', '?')} 页")
    print(f"内容：{chunk.page_content[:150]}...")

# 4. 存入向量库
embeddings = HuggingFaceEmbeddings(model_name="BAAI/bge-small-zh-v1.5")
vector_store = Chroma.from_documents(
    documents=chunks,
    embedding=embeddings,
    persist_directory="./quality_report_db",
)
vector_store.persist()
print("\n分块完成，已存入向量库")

# 5. 验证检索效果
query = "质检报告中提到的不合格项有哪些？"
results = vector_store.similarity_search(query, k=3)
for doc in results:
    print(f"第 {doc.metadata.get('page', '?')} 页：{doc.page_content[:100]}")
```

---

## 6. 分块策略选型指南

```python
def choose_chunk_strategy(doc_type: str, max_context_length: int):
    """根据文档类型和上下文长度选择分块策略"""
    strategies = {
        "markdown": MarkdownHeaderTextSplitter,
        "code": PythonCodeTextSplitter,
        "pdf": RecursiveCharacterTextSplitter,  # 通用
        "html": RecursiveCharacterTextSplitter,
        "csv": RecursiveCharacterTextSplitter,
    }
    
    if max_context_length <= 512:
        chunk_size = 128
    elif max_context_length <= 2048:
        chunk_size = 256
    else:
        chunk_size = 512
    
    return strategies.get(doc_type, RecursiveCharacterTextSplitter)(
        chunk_size=chunk_size,
        chunk_overlap=int(chunk_size * 0.1),
    )
```

| 文档类型 | 推荐策略 | chunk_size |
|---------|---------|-----------|
| Markdown 文档 | MarkdownHeaderTextSplitter | 按标题层级 |
| PDF 文档 | RecursiveCharacterTextSplitter | 500 |
| HTML 文档 | RecursiveCharacterTextSplitter | 500 |
| 代码文件 | PythonCodeTextSplitter / 按函数 | 300 |
| 长篇文章 | SemanticChunker | 语义自适应 |
| 短文本/FAQ | 不分块或固定大小 | 128 |

---

## 总结

本章你学会了：

- 文档分块的必要性：上下文窗口限制 + 检索精度需求
- 四种分块策略：固定大小、递归字符、语义分块、特定文档分块
- 分块大小与重叠的选择原则
- 元数据保留的方法与用途
- PDF 文档分块的完整实战流程
- 不同文档类型的分块策略选型

下一步：学习 [检索策略](../02-core/02-retrieval-strategies.md)，掌握向量检索、BM25、混合检索和 Reranker 精排。