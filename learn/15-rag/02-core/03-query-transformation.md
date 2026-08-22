# 查询转换

> 用户原始问题可能表达不清、过于宽泛或包含多个子问题。查询转换技术通过重写、分解、路由等方式优化查询，显著提升检索效果。

---

## 1. 为什么需要查询转换？

| 问题类型 | 用户原始查询 | 转换后的查询 |
|---------|-------------|-------------|
| 表述模糊 | "这个怎么用？" | "iPhone 15 的无线充电功能如何使用？" |
| 复合问题 | "苹果和华为哪个好？续航和拍照怎么样？" | 分解为"iPhone 15 Pro 续航评测"和"华为 Mate 60 Pro 拍照评测" |
| 知识缺口 | "买什么手机好？" | "2025 年 5000 元价位推荐手机" |
| 专业术语不匹配 | "手机卡顿" | "手机运行内存不足导致卡顿的解决方案" |

---

## 2. 查询重写（Query Rewriting）

### 2.1 HyDE（Hypothetical Document Embedding）

HyDE 的核心思想：**先让 LLM 根据问题生成一个假想的理想文档，然后用这个文档的向量去检索真实文档**。

```
用户问题："苹果手机电池不耐用怎么办？"
    ↓ LLM 生成假设文档
"苹果手机电池健康度下降，用户反映 iPhone 15 系列电池续航不足，
建议检查电池健康度、关闭后台应用刷新、降低屏幕亮度..."
    ↓ 用假设文档的 Embedding 去检索
找到真实的相关文档
```

```python
from langchain.chat_models import ChatOpenAI
from langchain.prompts import PromptTemplate
from langchain_community.vectorstores import Chroma

def hyde_retrieve(query: str, retriever, llm):
    """HyDE 检索：先生成假设文档，再检索"""
    # 1. 生成假设文档
    prompt = PromptTemplate.from_template(
        "请根据以下问题，写一段详细的、假设性的回答文档。\n"
        "要求：专业、准确、包含具体细节。\n\n"
        "问题：{query}\n\n"
        "假设文档："
    )
    hypothesis = llm.invoke(prompt.format(query=query))
    
    # 2. 用假设文档检索
    results = retriever.get_relevant_documents(hypothesis.content)
    return results

# 使用
results = hyde_retrieve("苹果手机电池不耐用怎么办？", retriever, llm)
```

### 2.2 Multi-Query（多查询扩展）

Multi-Query 将一个原始问题扩展为多个不同角度的查询，分别检索后合并结果。

```python
from langchain.chat_models import ChatOpenAI
from langchain.prompts import PromptTemplate

def multi_query_retrieve(query: str, retriever, llm, n_queries=3):
    """Multi-Query：生成多个角度的查询并分别检索"""
    # 1. 生成多个查询
    prompt = PromptTemplate.from_template(
        "你是一个 AI 助手。请将用户问题扩展为 {n} 个不同角度的查询，"
        "每个查询用不同的表述方式，以覆盖更多的相关信息。\n\n"
        "用户问题：{query}\n\n"
        "请输出 {n} 个查询，每行一个："
    )
    response = llm.invoke(prompt.format(query=query, n=n_queries))
    queries = response.content.strip().split("\n")
    
    # 2. 分别检索
    all_results = []
    for q in queries:
        docs = retriever.get_relevant_documents(q.strip())
        all_results.extend(docs)
    
    # 3. 去重（按内容 hash）
    seen = set()
    unique_results = []
    for doc in all_results:
        key = hash(doc.page_content[:100])
        if key not in seen:
            seen.add(key)
            unique_results.append(doc)
    
    return unique_results

# 使用
results = multi_query_retrieve("苹果手机电池不耐用", retriever, llm)
```

**Multi-Query 示例**：

```
原始问题："苹果手机电池不耐用"
扩展查询：
1. "iPhone 电池健康度下降原因及解决方法"
2. "苹果手机续航优化技巧"
3. "iPhone 电池更换政策及费用"
```

---

## 3. 查询分解（Query Decomposition）

将复杂问题分解为多个子问题，分别检索后汇总。

### 3.1 顺序分解

```python
def decompose_query(complex_query: str, retriever, llm):
    """顺序分解：先回答子问题，再回答主问题"""
    # 1. 分解问题
    decompose_prompt = PromptTemplate.from_template(
        "将以下复杂问题分解为 2-3 个简单的子问题：\n\n"
        "问题：{query}\n\n"
        "子问题："
    )
    sub_questions = llm.invoke(decompose_prompt.format(query=complex_query))
    
    # 2. 分别检索每个子问题
    contexts = []
    for q in sub_questions.content.strip().split("\n"):
        q = q.strip().lstrip("0123456789. ")
        docs = retriever.get_relevant_documents(q)
        contexts.extend(docs)
    
    # 3. 汇总生成最终答案
    context_text = "\n\n".join([d.page_content for d in contexts])
    final_prompt = f"""请基于以下信息回答问题。

相关信息：
{context_text}

问题：{complex_query}

回答："""
    return llm.invoke(final_prompt)
```

### 3.2 实战示例

```
用户问题："对比 iPhone 15 Pro 和华为 Mate 60 Pro，哪款更适合拍照，价格如何？"

分解后的子问题：
1. "iPhone 15 Pro 相机参数和拍照效果"
2. "华为 Mate 60 Pro 相机参数和拍照效果"
3. "iPhone 15 Pro 价格"
4. "华为 Mate 60 Pro 价格"

分别检索 → 汇总生成对比回答
```

---

## 4. 查询路由（Query Routing）

根据问题类型，将查询路由到不同的检索器或知识库。

### 4.1 路由分类器

```python
from langchain.chat_models import ChatOpenAI
from langchain.prompts import PromptTemplate

class QueryRouter:
    """查询路由：根据问题类型分发到不同检索器"""
    
    def __init__(self, retrievers: dict, llm):
        """
        retrievers: {
            "product": product_retriever,    # 商品知识库
            "policy": policy_retriever,       # 政策文档
            "faq": faq_retriever,             # FAQ 库
        }
        """
        self.retrievers = retrievers
        self.llm = llm
    
    def route(self, query: str) -> str:
        """判断问题类型，返回路由目标"""
        prompt = PromptTemplate.from_template(
            "判断以下问题属于哪个类别，只输出类别名称。\n\n"
            "类别：product（商品咨询）、policy（政策查询）、faq（常见问题）\n\n"
            "问题：{query}\n\n"
            "类别："
        )
        response = self.llm.invoke(prompt.format(query=query))
        route = response.content.strip().lower()
        
        # 映射到有效的路由
        for key in ["product", "policy", "faq"]:
            if key in route:
                return key
        return "product"  # 默认路由
    
    def retrieve(self, query: str):
        """路由并检索"""
        route = self.route(query)
        print(f"路由到：{route}")
        retriever = self.retrievers[route]
        return retriever.get_relevant_documents(query)

# 使用
router = QueryRouter(
    retrievers={
        "product": product_retriever,
        "policy": policy_retriever,
        "faq": faq_retriever,
    },
    llm=llm,
)
results = router.retrieve("7 天无理由退货的条件是什么？")
# 路由到：policy
```

### 4.2 路由表设计

| 问题类型 | 路由目标 | 举例 |
|---------|---------|------|
| 商品参数、规格、功能 | product 知识库 | "iPhone 15 支持快充吗？" |
| 退换货、保修、退款 | policy 知识库 | "保修期内维修免费吗？" |
| 常见操作问题 | faq 知识库 | "如何查看订单物流？" |
| 商品对比、推荐 | product + reranker | "5000 元以内推荐什么手机？" |
| 多跳推理问题 | graph 知识库 | "哪些农药可以治疗水稻稻瘟病？" |

---

## 5. 查询转换策略选型

```python
def query_transform(query: str, strategy: str, retriever, llm):
    """查询转换策略调度"""
    strategies = {
        "none": lambda q, r, _: r.get_relevant_documents(q),
        "hyde": hyde_retrieve,
        "multi_query": multi_query_retrieve,
        "decompose": decompose_query,
    }
    return strategies.get(strategy, strategies["none"])(query, retriever, llm)
```

| 策略 | 适用场景 | 性能开销 | 效果提升 |
|------|---------|---------|---------|
| 无转换 | 简单、明确的问题 | 无 | 基准 |
| HyDE | 查询表述不准确 | 1 次 LLM 调用 | ~10% |
| Multi-Query | 需要多角度覆盖 | N 次检索 + 1 次 LLM | ~15% |
| 查询分解 | 复杂的复合问题 | 多次检索 + 多次 LLM | ~20%+ |
| 查询路由 | 多知识库场景 | 1 次 LLM 分类 | 减少噪声 |

---

## 总结

本章你学会了：

- 查询转换的必要性：解决表述模糊、复合问题、知识缺口
- HyDE 技术：生成假设文档再检索，提升语义匹配
- Multi-Query 技术：多角度扩展查询，提高召回率
- 查询分解：将复杂问题拆解为子问题，分步解决
- 查询路由：根据问题类型分发到不同知识库
- 不同策略的选型指南

下一步：学习 [Graph RAG](../03-advanced/01-graph-rag.md)，探索知识图谱与 RAG 的融合方案。