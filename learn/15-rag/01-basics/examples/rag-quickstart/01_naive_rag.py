"""
Naive RAG（检索增强生成）完整流程演示
========================================

演示内容：
1. 文档加载（TextLoader / 手动创建示例文档）
2. 文本分割（RecursiveCharacterTextSplitter）
3. 文本嵌入（Embedding — 使用 HuggingFace 免费模型作为备选）
4. 向量存储（FAISS 本地索引）
5. 检索 + 生成（Retrieval QA Chain）
6. 完整 RAG 流水线

运行方式：
    python 01_naive_rag.py

需要安装：
    pip install -r requirements.txt

注意：
- 本示例默认使用 HuggingFaceEmbeddings（免费，离线可用）
- 如需使用 OpenAI 嵌入，设置 USE_OPENAI=true 环境变量并配置 OPENAI_API_KEY
"""

import os
from pathlib import Path

# ============================================================
# 0. 准备示例文档
# ============================================================
SAMPLE_DOC = Path(__file__).parent / "sample_knowledge.txt"

# 如果示例文档不存在，自动创建
if not SAMPLE_DOC.exists():
    SAMPLE_DOC.write_text(
        "Python 是一种高级编程语言，由 Guido van Rossum 于 1991 年创建。\n"
        "Python 的设计哲学强调代码的可读性和简洁性，使用缩进来定义代码块。\n"
        "Python 是一种动态类型语言，支持面向对象、函数式和过程式编程范式。\n"
        "Python 拥有丰富的标准库和第三方库生态，被广泛应用于 Web 开发、数据科学、\n"
        "人工智能、自动化运维等领域。\n"
        "Python 的包管理工具是 pip，虚拟环境管理工具推荐使用 venv 或 conda。\n"
        "Python 3.x 是目前的主流版本，Python 2.x 已于 2020 年停止维护。\n"
        "Python 的 GIL（全局解释器锁）在多线程 CPU 密集型任务中可能成为性能瓶颈。\n"
        "Python 的异步编程支持通过 asyncio 库实现，适合 I/O 密集型任务。\n"
        "FastAPI 是一个基于 Python 的现代 Web 框架，支持异步请求处理和自动 API 文档生成。\n",
        encoding="utf-8",
    )
    print(f"✅ 已创建示例文档: {SAMPLE_DOC}")


# ============================================================
# 1. 文档加载
# ============================================================
print("=" * 60)
print("1. 文档加载 / Document Loading")
print("=" * 60)

from langchain_community.document_loaders import TextLoader

loader = TextLoader(str(SAMPLE_DOC), encoding="utf-8")
documents = loader.load()
print(f"  加载了 {len(documents)} 个文档，每个文档 {len(documents[0].page_content)} 字符")


# ============================================================
# 2. 文本分割
# ============================================================
print("\n" + "=" * 60)
print("2. 文本分割 / Text Splitting")
print("=" * 60)

from langchain_text_splitters import RecursiveCharacterTextSplitter

text_splitter = RecursiveCharacterTextSplitter(
    chunk_size=100,        # 每个块的大小（字符数）
    chunk_overlap=20,      # 块之间的重叠字符数，保持上下文连贯
    separators=["\n\n", "\n", "。", "，", " ", ""],  # 分割优先级
    length_function=len,
)

chunks = text_splitter.split_documents(documents)
print(f"  分割后得到 {len(chunks)} 个文本块")
for i, chunk in enumerate(chunks):
    print(f"  Chunk {i + 1}: {chunk.page_content[:50]}...")


# ============================================================
# 3. 嵌入模型（Embedding）
# ============================================================
print("\n" + "=" * 60)
print("3. 嵌入模型 / Embedding")
print("=" * 60)

# 策略：优先使用 HuggingFace 免费嵌入（离线可用），备选 OpenAI
USE_OPENAI = os.getenv("USE_OPENAI", "").lower() in ("true", "1", "yes")

if USE_OPENAI:
    # 需要 pip install langchain-openai
    from langchain_openai import OpenAIEmbeddings

    embeddings = OpenAIEmbeddings(model="text-embedding-3-small")
    print("  使用 OpenAI Embeddings（text-embedding-3-small）")
    print("  ⚠️ 确保已设置 OPENAI_API_KEY 环境变量")
else:
    # 使用 Sentence Transformers 免费模型（离线可用）
    # 首次运行会自动下载模型到本地缓存
    from langchain_community.embeddings import HuggingFaceEmbeddings

    embeddings = HuggingFaceEmbeddings(
        model_name="sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2",
        model_kwargs={"device": "cpu"},
        encode_kwargs={"normalize_embeddings": True},
    )
    print("  使用 HuggingFace Embeddings（paraphrase-multilingual-MiniLM-L12-v2）")
    print("  免费离线可用，支持中文")

# 测试嵌入
test_vec = embeddings.embed_query("Python 是什么？")
print(f"  嵌入向量维度: {len(test_vec)}")


# ============================================================
# 4. 向量存储（FAISS）
# ============================================================
print("\n" + "=" * 60)
print("4. 向量存储 / Vector Store (FAISS)")
print("=" * 60)

from langchain_community.vectorstores import FAISS

# 创建 FAISS 索引（内存中）
vector_store = FAISS.from_documents(chunks, embeddings)
print("  ✅ FAISS 向量索引已创建（内存中）")

# 保存到磁盘（可选）
# vector_store.save_local("faiss_index")
# 加载方式: FAISS.load_local("faiss_index", embeddings)


# ============================================================
# 5. 检索测试
# ============================================================
print("\n" + "=" * 60)
print("5. 检索测试 / Retrieval Test")
print("=" * 60)

query = "Python 的异步编程如何实现？"
retrieved_docs = vector_store.similarity_search(query, k=3)

print(f"  查询: {query}")
print(f"  检索到 {len(retrieved_docs)} 个相关文档:")
for i, doc in enumerate(retrieved_docs):
    print(f"  [{i + 1}] {doc.page_content}")


# ============================================================
# 6. 完整 RAG Pipeline（检索 + 生成）
# ============================================================
print("\n" + "=" * 60)
print("6. 完整 RAG Pipeline / Retrieval-Augmented Generation")
print("=" * 60)

# 使用 LLM 生成回答（需要 API Key）
OPENAI_API_KEY = os.getenv("OPENAI_API_KEY")

if OPENAI_API_KEY:
    from langchain.chains import RetrievalQA
    from langchain_openai import ChatOpenAI

    llm = ChatOpenAI(
        model=os.getenv("OPENAI_MODEL", "gpt-4o-mini"),
        temperature=0,
        api_key=OPENAI_API_KEY,
    )

    # 方式一：使用 RetrievalQA 链（简洁）
    qa_chain = RetrievalQA.from_chain_type(
        llm=llm,
        chain_type="stuff",  # 将所有检索结果拼入 prompt
        retriever=vector_store.as_retriever(search_kwargs={"k": 3}),
        return_source_documents=True,
        verbose=True,
    )

    print("\n--- 问题1: Python 的异步编程如何实现？---")
    result = qa_chain.invoke({"query": "Python 的异步编程如何实现？"})
    print(f"回答: {result['result']}")

    print("\n--- 问题2: Python 有哪些应用领域？---")
    result2 = qa_chain.invoke({"query": "Python 有哪些应用领域？"})
    print(f"回答: {result2['result']}")

else:
    print("  ⚠️ 未设置 OPENAI_API_KEY，跳过 LLM 生成步骤")
    print("  请创建 .env 文件并添加: OPENAI_API_KEY=sk-xxx")
    print("  或直接运行以下代码模拟生成结果:\n")

    # 模拟 RAG 流水线输出（仅用于演示流程）
    print("  [模拟 RAG 输出]")
    print("  基于以下检索结果:")
    query2 = "Python 的异步编程如何实现？"
    docs = vector_store.similarity_search(query2, k=2)
    for d in docs:
        print(f"    - {d.page_content}")
    print(f"\n  [模拟回答] Python 通过 asyncio 库实现异步编程，"
          f"支持 async/await 语法，适合 I/O 密集型任务。\n")


# ============================================================
# 7. MMR 检索（最大边际相关性，增加结果多样性）
# ============================================================
print("=" * 60)
print("7. MMR 检索 / Maximal Marginal Relevance")
print("=" * 60)

mmr_docs = vector_store.max_marginal_relevance_search(
    query="Python 的特点",
    k=3,
    fetch_k=10,  # 先取 10 个候选，再从中选 3 个最不同的
)
print(f"  MMR 检索到 {len(mmr_docs)} 个多样化的结果:")
for i, doc in enumerate(mmr_docs):
    print(f"  [{i + 1}] {doc.page_content[:60]}...")


print("\n" + "=" * 60)
print("RAG 流水线演示完成！")
print("=" * 60)
print(f"\n示例文档路径: {SAMPLE_DOC}")
print("如需清理，直接删除该文件即可。")