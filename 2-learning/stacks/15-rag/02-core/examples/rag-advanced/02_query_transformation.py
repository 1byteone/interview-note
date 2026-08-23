"""
查询转换（Query Transformation）演示
======================================

演示内容：
1. Multi-Query：生成多个查询变体，扩大召回范围
2. HyDE：假设文档嵌入（Hypothetical Document Embeddings）
3. 查询分解（Query Decomposition）：复杂问题拆解为子问题

运行方式：
    python 02_query_transformation.py

说明：
- 生成部分需要 LLM（需要 API Key），检索部分可离线运行
- 未配置 API Key 时，会使用模拟输出来演示流程
"""

import os
from dotenv import load_dotenv

load_dotenv()

# ============================================================
# 0. 准备示例语料和检索器
# ============================================================
print("=" * 60)
print("0. 准备基础检索器")
print("=" * 60)

documents = [
    "Python 的 asyncio 库提供了 async/await 语法用于异步编程。",
    "FastAPI 是一个高性能的异步 Web 框架，自动生成 OpenAPI 文档。",
    "SQLAlchemy 支持同步和异步两种数据库操作模式。",
    "Celery 是 Python 的分布式任务队列，用于异步任务处理。",
    "Redis 可以作为消息队列，支持发布/订阅模式。",
    "Kafka 是一个分布式流处理平台，适合高吞吐量消息系统。",
    "Docker 容器化部署可以简化微服务架构的运维。",
    "Kubernetes 提供了容器编排、自动伸缩和服务发现功能。",
    "PostgreSQL 支持事务、窗口函数和 JSONB 数据类型。",
    "MongoDB 是一个 NoSQL 文档数据库，适合灵活的数据模型。",
    "Elasticsearch 提供全文搜索和实时数据分析能力。",
    "RabbitMQ 实现了 AMQP 协议，支持可靠的消息传递。",
]

from langchain_community.embeddings import HuggingFaceEmbeddings
from langchain_community.vectorstores import FAISS

embeddings = HuggingFaceEmbeddings(
    model_name="sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2",
    model_kwargs={"device": "cpu"},
    encode_kwargs={"normalize_embeddings": True},
)

vector_store = FAISS.from_texts(documents, embeddings)
retriever = vector_store.as_retriever(search_kwargs={"k": 3})
print(f"  ✅ 检索器就绪（{len(documents)} 条文档）\n")

# 检查是否配置了 API Key
OPENAI_API_KEY = os.getenv("OPENAI_API_KEY")
USE_REAL_LLM = bool(OPENAI_API_KEY)


# ============================================================
# 1. Multi-Query：生成多个查询变体
# ============================================================
print("=" * 60)
print("1. Multi-Query：多查询变体")
print("=" * 60)

original_query = "Python 中如何做异步数据库操作？"


def generate_multi_queries(query: str) -> list[str]:
    """生成多个查询变体，从不同角度描述同一问题"""
    if USE_REAL_LLM:
        from langchain_openai import ChatOpenAI
        from langchain_core.prompts import PromptTemplate
        from langchain_core.output_parsers import StrOutputParser

        llm = ChatOpenAI(model="gpt-4o-mini", temperature=0.7, api_key=OPENAI_API_KEY)

        prompt = PromptTemplate.from_template(
            "你是一个搜索助手。请为以下用户问题生成 3 个不同的查询变体，"
            "每个变体从不同角度描述同一个信息需求。\n"
            "直接输出查询，每行一个，不要序号。\n\n问题: {query}"
        )

        chain = prompt | llm | StrOutputParser()
        result = chain.invoke({"query": query})
        queries = [q.strip() for q in result.strip().split("\n") if q.strip()]
        return queries[:3]
    else:
        # 模拟输出（离线演示）
        return [
            "Python 异步 ORM 使用方法",
            "async SQLAlchemy 异步数据库查询",
            "Python 数据库异步操作 with asyncio",
        ]


multi_queries = generate_multi_queries(original_query)
print(f"  原始查询: {original_query}")
print(f"  变体查询:")
for q in multi_queries:
    print(f"    → {q}")

# 用所有变体分别检索，合并结果
print(f"\n  合并检索结果:")
all_docs = []
for q in multi_queries:
    docs = retriever.invoke(q)
    for doc in docs:
        content = doc.page_content
        if content not in [d.page_content for d in all_docs]:
            all_docs.append(doc)
            print(f"    [{q[:20]}...] → {content}")
print(f"  去重后共 {len(all_docs)} 条结果\n")


# ============================================================
# 2. HyDE：假设文档嵌入
# ============================================================
print("=" * 60)
print("2. HyDE（Hypothetical Document Embeddings）")
print("=" * 60)


def generate_hypothetical_document(query: str) -> str:
    """根据问题先生成一个假设的理想文档，再用它去检索"""
    if USE_REAL_LLM:
        from langchain_openai import ChatOpenAI
        from langchain_core.prompts import PromptTemplate
        from langchain_core.output_parsers import StrOutputParser

        llm = ChatOpenAI(model="gpt-4o-mini", temperature=0.3, api_key=OPENAI_API_KEY)

        prompt = PromptTemplate.from_template(
            "请根据以下问题，写一段包含答案的假设性文档片段。\n"
            "要求：语言专业、简洁，就像写在技术文档中一样。\n\n问题: {query}\n\n假设文档:"
        )

        chain = prompt | llm | StrOutputParser()
        return chain.invoke({"query": query})
    else:
        return (
            "Python 异步数据库操作可以通过 async SQLAlchemy 实现。"
            "使用 async with AsyncSession 创建异步会话，"
            "并使用 await session.execute() 执行异步查询。"
            "同时需要安装 asyncpg 或 aiosqlite 作为异步数据库驱动。"
        )


hyde_query = "Python 异步数据库操作的最佳实践"
hyde_doc = generate_hypothetical_document(hyde_query)
print(f"  原始问题: {hyde_query}")
print(f"  假设文档: {hyde_doc[:80]}...")

# 用假设文档去检索
hyde_results = vector_store.similarity_search(hyde_doc, k=3)
print(f"  HyDE 检索结果:")
for i, doc in enumerate(hyde_results, 1):
    print(f"    [{i}] {doc.page_content}")

# 对比直接用原问题检索
direct_results = retriever.invoke(hyde_query)
print(f"  \n  直接检索结果（对比）:")
for i, doc in enumerate(direct_results, 1):
    print(f"    [{i}] {doc.page_content}")
print()


# ============================================================
# 3. 查询分解
# ============================================================
print("=" * 60)
print("3. 查询分解（Query Decomposition）")
print("=" * 60)


def decompose_query(complex_query: str) -> list[str]:
    """将复杂问题分解为多个简单的子问题"""
    if USE_REAL_LLM:
        from langchain_openai import ChatOpenAI
        from langchain_core.prompts import PromptTemplate
        from langchain_core.output_parsers import StrOutputParser

        llm = ChatOpenAI(model="gpt-4o-mini", temperature=0, api_key=OPENAI_API_KEY)

        prompt = PromptTemplate.from_template(
            "将以下复杂问题分解为 3 个简单的子问题，每个子问题应该独立可检索。\n"
            "直接输出子问题，每行一个，不要序号。\n\n问题: {query}"
        )

        chain = prompt | llm | StrOutputParser()
        result = chain.invoke({"query": complex_query})
        sub_queries = [q.strip() for q in result.strip().split("\n") if q.strip()]
        return sub_queries[:3]
    else:
        return [
            "Python 异步编程有哪些库？",
            "消息队列如何实现异步任务？",
            "数据库异步操作怎么做？",
        ]


complex_query = "如何用 Python 构建一个支持异步处理的微服务架构？"
sub_queries = decompose_query(complex_query)
print(f"  复杂问题: {complex_query}")
print(f"  分解为子问题:")
for q in sub_queries:
    print(f"    → {q}")

# 对每个子问题分别检索
print(f"\n  子问题检索结果:")
for sq in sub_queries:
    docs = retriever.invoke(sq)
    print(f"  [{sq[:30]}...]")
    for doc in docs:
        print(f"    → {doc.page_content}")
    print()


# ============================================================
# 总结
# ============================================================
print("=" * 60)
print("查询转换策略总结")
print("=" * 60)
print("""
┌──────────────────┬────────────────────────────────┐
│ 策略              │ 适用场景                        │
├──────────────────┼────────────────────────────────┤
│ Multi-Query      │ 用户查询表达模糊，需要扩大召回    │
│ HyDE             │ 查询<->文档存在词汇鸿沟时         │
│ Query Decompose  │ 复杂多跳问题，需要分步推理        │
└──────────────────┴────────────────────────────────┘

最佳实践:
  1. Multi-Query 适合大多数场景，简单有效
  2. HyDE 在有"查询-文档"语义差距时特别有效
  3. 查询分解适合需要多步推理的复杂问题
""")