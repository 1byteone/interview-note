# 高级 RAG 模式

> 朴素 RAG 存在检索质量不稳、缺乏自我反思等问题。本章系统讲解四种前沿的 RAG 模式：Self-RAG、Corrective RAG、Adaptive RAG 和 Agentic RAG，帮助构建更智能、更可靠的 RAG 系统。

---

## 1. 朴素 RAG 的痛点

在进入高级模式之前，先回顾朴素 RAG 的四大痛点：

| 痛点 | 表现 |
|------|------|
| **检索不可控** | 无论检索到什么都直接生成，检索质量差时回答也差 |
| **无自我反思** | 无法判断自己是否缺知识、是否答错了 |
| **无纠错机制** | 发现检索结果有问题也无法修正 |
| **单一固定路径** | 所有问题都走同一条检索生成路径，不够灵活 |

高级 RAG 模式针对这些痛点进行了系统性的改进。

---

## 2. Self-RAG（自我反思 RAG）

### 2.1 核心思想

Self-RAG 在生成过程中加入**自我反思（Self-Reflection）**机制，模型会生成反思 token 来评估检索和生成的质量。

```
Self-RAG 的四个反思检查：
1. 是否需要检索？（Relevant）
2. 检索到的文档支持答案吗？（Supported）
3. 回答是否完整？（Complete）
4. 回答中是否有幻觉？（Groundness）
```

### 2.2 工作流程

```
用户问题
    ↓
[反思 1] 是否需要检索？
    ├── 不需要 → 直接生成（通用知识问题）
    └── 需要 → 执行检索
        ↓
[反思 2] 检索结果是否相关？
    ├── 相关 → 基于文档生成
    └── 不相关 → 重新检索 或 直接生成
        ↓
[反思 3] 回答是否完整、有无幻觉？
    ├── 通过 → 输出回答
    └── 不通过 → 补充检索 / 修正回答
```

### 2.3 代码实现

```python
from langchain.prompts import PromptTemplate
from langchain.chat_models import ChatOpenAI

class SelfRAG:
    """Self-RAG：带自我反思的 RAG"""
    
    def __init__(self, llm, retriever):
        self.llm = llm
        self.retriever = retriever
    
    def should_retrieve(self, query: str) -> bool:
        """反思 1：是否需要检索？"""
        prompt = PromptTemplate.from_template(
            "判断以下问题是否需要检索外部知识才能回答。\n"
            "如果问题涉及事实、数据、专业知识，需要检索外部知识 → 回答 'yes'\n"
            "如果问题是常识、观点、简单推理 → 回答 'no'\n\n"
            "问题：{query}\n"
            "判断："
        )
        resp = self.llm.invoke(prompt.format(query=query))
        return resp.content.strip().lower() == "yes"
    
    def check_relevance(self, query: str, docs: list) -> bool:
        """反思 2：检索结果是否相关？"""
        doc_text = "\n".join([d.page_content[:100] for d in docs[:3]])
        prompt = f"以下检索到的文档是否与问题相关？\n问题：{query}\n文档：{doc_text}\n回答（yes/no）："
        resp = self.llm.invoke(prompt)
        return resp.content.strip().lower() == "yes"
    
    def check_answer(self, query: str, answer: str) -> bool:
        """反思 3：回答是否完整无幻觉？"""
        prompt = f"检查以下回答是否完整且没有幻觉（编造内容）。\n问题：{query}\n回答：{answer}\n回答（good/bad）："
        resp = self.llm.invoke(prompt)
        return resp.content.strip().lower() == "good"
    
    def generate(self, query: str) -> str:
        """完整 Self-RAG 流程"""
        if not self.should_retrieve(query):
            # 不需要检索，直接生成
            return self.llm.invoke(f"回答以下问题：{query}").content
        
        # 需要检索
        docs = self.retriever.get_relevant_documents(query)
        
        if not self.check_relevance(query, docs):
            print("反思：检索结果不相关，尝试重新表述查询")
            # 简化处理：扩大检索范围再试一次
            docs = self.retriever.get_relevant_documents(query, k=10)
        
        # 基于文档生成
        context = "\n\n".join([d.page_content for d in docs])
        answer = self.llm.invoke(
            f"基于以下文档回答问题：\n\n{context}\n\n问题：{query}"
        ).content
        
        # 反思 3：检查回答质量
        if not self.check_answer(query, answer):
            print("反思：回答质量不佳，重新生成")
            answer = self.llm.invoke(
                f"基于以下文档重新回答问题，要求更完整、更准确：\n\n{context}\n\n问题：{query}"
            ).content
        
        return answer

# 使用
self_rag = SelfRAG(llm=llm, retriever=retriever)
answer = self_rag.generate("苹果手机怎么设置无线充电？")
```

---

## 3. Corrective RAG（纠错 RAG）

### 3.1 核心思想

Corrective RAG（CRAG）在检索后增加**评估与纠错**环节：如果检索结果质量不佳，不直接使用，而是执行**知识精炼**或**网络检索兜底**。

```
用户问题
    ↓
检索
    ↓
[评估] 检索结果质量如何？
    ├── 高 → 直接用于生成
    ├── 中 → 知识精炼（过滤、去噪）
    └── 低 → 网络检索兜底 / 查询重写后重新检索
    ↓
生成
```

### 3.2 代码实现

```python
from langchain_community.utilities import DuckDuckGoSearchAPIWrapper

class CorrectiveRAG:
    """Corrective RAG：带纠错机制的 RAG"""
    
    def __init__(self, llm, retriever, web_search=None):
        self.llm = llm
        self.retriever = retriever
        self.web_search = web_search or DuckDuckGoSearchAPIWrapper()
    
    def evaluate_retrieval(self, query: str, docs: list) -> str:
        """评估检索结果质量：high / medium / low"""
        doc_text = "\n".join([d.page_content[:150] for d in docs[:3]])
        prompt = f"""评估以下检索结果对回答该问题的相关程度。
问题：{query}
检索结果：
{doc_text}
输出：high（高度相关）/ medium（部分相关）/ low（不相关）"""
        resp = self.llm.invoke(prompt)
        return resp.content.strip().lower()
    
    def knowledge_refine(self, query: str, docs: list) -> list:
        """知识精炼：过滤无关片段，去重"""
        prompt = f"""从以下文档片段中，筛选出与问题最相关的片段，只输出相关片段。
问题：{query}
文档：
{"\n".join([f"[{i}] {d.page_content}" for i, d in enumerate(docs)])}
输出格式：输出筛选后的片段编号列表，如 [1,3,5]"""
        resp = self.llm.invoke(prompt)
        import re
        indices = [int(x) for x in re.findall(r"\d+", resp.content)]
        return [docs[i] for i in indices if i < len(docs)]
    
    def generate(self, query: str) -> str:
        docs = self.retriever.get_relevant_documents(query)
        quality = self.evaluate_retrieval(query, docs)
        print(f"检索质量评估：{quality}")
        
        if quality == "high":
            context = "\n\n".join([d.page_content for d in docs])
        elif quality == "medium":
            refined = self.knowledge_refine(query, docs)
            context = "\n\n".join([d.page_content for d in refined])
            print(f"知识精炼：{len(docs)} 条 → {len(refined)} 条")
        else:
            # 网络检索兜底
            print("检索质量低，启用网络检索兜底")
            web_results = self.web_search.run(query)
            context = f"网络检索结果：\n{web_results}"
        
        return self.llm.invoke(
            f"基于以下信息回答问题：\n\n{context}\n\n问题：{query}"
        ).content

# 使用
crag = CorrectiveRAG(llm=llm, retriever=retriever)
answer = crag.generate("2026 年最新的智能手机销量排行")
```

### 3.3 与 Self-RAG 的区别

| 对比 | Self-RAG | Corrective RAG |
|------|---------|---------------|
| 反思时机 | 生成前、中、后全流程 | 主要在检索后 |
| 核心能力 | 判断"要不要检索、答得好不好" | 评估并"修正"检索结果 |
| 兜底策略 | 重新生成 | 知识精炼、网络检索 |

---

## 4. Adaptive RAG（自适应 RAG）

### 4.1 核心思想

Adaptive RAG 核心是**按问题复杂度动态选择策略**：简单问题直接生成，中等问题单次检索，复杂问题多次检索并路由。

```python
class AdaptiveRAG:
    """Adaptive RAG：按复杂度自适应选择策略"""
    
    def __init__(self, llm, retriever):
        self.llm = llm
        self.retriever = retriever
    
    def assess_complexity(self, query: str) -> str:
        """评估问题复杂度：simple / moderate / complex"""
        prompt = f"""评估以下问题的复杂度。
问题：{query}
输出：simple（简单，常识即可回答）/ moderate（中等，需单次检索）/ complex（复杂，需多步推理和多路检索）"""
        resp = self.llm.invoke(prompt)
        return resp.content.strip().lower()
    
    def generate(self, query: str) -> str:
        complexity = self.assess_complexity(query)
        print(f"问题复杂度：{complexity}")
        
        if complexity == "simple":
            # 直接生成，不检索
            return self.llm.invoke(f"回答：{query}").content
        
        if complexity == "moderate":
            # 单次检索
            docs = self.retriever.get_relevant_documents(query)
            context = "\n\n".join([d.page_content for d in docs])
            return self.llm.invoke(
                f"基于文档回答：\n\n{context}\n\n问题：{query}"
            ).content
        
        # complex：多次检索 + 多路召回 + 融合
        docs = self.retriever.get_relevant_documents(query, k=10)
        # 多路检索（向量 + 关键词），此处简化
        context = "\n\n".join([d.page_content for d in docs])
        return self.llm.invoke(
            f"""请基于以下检索结果，分步骤回答这个复杂问题。

检索结果：
{context}

问题：{query}

要求：先梳理关键信息，再逐步推理回答。"""
        ).content

# 使用
adaptive_rag = AdaptiveRAG(llm=llm, retriever=retriever)
answer = adaptive_rag.generate("对比 2025 年和 2026 年 AI 商城的技术架构变化")
```

### 4.2 策略路由表

| 复杂度 | 策略 | 延迟 | 效果 |
|--------|------|------|------|
| simple | 直接生成 | 低 | 快 |
| moderate | 单次检索 + 生成 | 中 | 平衡 |
| complex | 多路检索 + 多步推理 | 高 | 最准确 |

---

## 5. Agentic RAG（Agent 驱动检索）

### 5.1 核心思想

Agentic RAG 将 RAG 与 LLM Agent 结合，让模型自主决定**检索什么、用什么工具、何时停止**。

```
用户问题
    ↓
Agent 循环：
  ├── 决定：需要检索吗？用哪个检索器？
  ├── 执行：调用检索工具 / 数据库工具 / 计算工具
  ├── 观察：查看检索结果
  └── 反思：结果够了吗？继续还是生成？
    ↓
生成最终回答
```

### 5.2 代码实现

```python
from langchain.agents import create_react_agent, AgentExecutor
from langchain.tools import Tool
from langchain.prompts import PromptTemplate

def build_agentic_rag(llm, vector_retriever, bm25_retriever, policy_retriever):
    """构建 Agentic RAG"""
    
    # 定义工具
    tools = [
        Tool(
            name="vector_search",
            func=lambda q: "\n".join([d.page_content for d in vector_retriever.get_relevant_documents(q)]),
            description="语义向量检索。适合理解语义的查询，如'哪个手机拍照好'",
        ),
        Tool(
            name="bm25_search",
            func=lambda q: "\n".join([d.page_content for d in bm25_retriever.get_relevant_documents(q)]),
            description="关键词检索。适合精确匹配的查询，如'iPhone 15 Pro'型号参数",
        ),
        Tool(
            name="policy_search",
            func=lambda q: "\n".join([d.page_content for d in policy_retriever.get_relevant_documents(q)]),
            description="政策文档检索。适合退换货、保修、退款等政策查询",
        ),
    ]
    
    # Agent 提示词
    prompt = PromptTemplate.from_template(
        """你是一个智能检索助手。回答用户问题时：
1. 先思考应该使用哪个工具检索
2. 检索后检查结果是否足够回答
3. 如果不足，尝试使用其他工具
4. 基于检索结果生成最终回答，并标注来源

可用工具：{tools}
工具名：{tool_names}
用户问题：{input}

{agent_scratchpad}"""
    )
    
    agent = create_react_agent(llm, tools, prompt)
    return AgentExecutor(
        agent=agent,
        tools=tools,
        verbose=True,
        max_iterations=5,  # 最多 5 轮工具调用
    )

# 使用
agent = build_agentic_rag(llm, vector_retriever, bm25_retriever, policy_retriever)
result = agent.invoke({
    "input": "iPhone 15 Pro 支持快充吗？如果支持，功率是多少？"
})
print(result["output"])
```

### 5.3 Agentic RAG 的优势

| 优势 | 说明 |
|------|------|
| **工具选择自主** | 根据问题性质自动选择向量/关键词/图检索 |
| **多步推理** | 可多次检索、组合信息回答复杂问题 |
| **可扩展** | 可接入数据库、API、计算器等更多工具 |
| **自我纠错** | 发现结果不足时自动换工具重试 |

---

## 6. 四种模式对比与选型

| 模式 | 核心机制 | 复杂度 | 适用场景 |
|------|---------|--------|---------|
| Self-RAG | 自我反思检索与生成质量 | 中 | 知识密集型问答，需控制幻觉 |
| Corrective RAG | 检索后评估，精炼或兜底 | 中 | 知识库质量参差不齐 |
| Adaptive RAG | 按复杂度动态选策略 | 中 | 查询复杂度差异大的混合场景 |
| Agentic RAG | Agent 自主决策检索工具 | 高 | 复杂多步任务、多知识源场景 |

### 选型建议

```python
def select_rag_pattern(size, quality, query_diversity):
    """根据场景选择 RAG 模式"""
    if query_diversity == "high" and size == "large":
        return "Agentic RAG"     # 多知识源、复杂查询
    if quality == "low":
        return "Corrective RAG"  # 知识库质量差，需纠错
    if query_diversity == "high":
        return "Adaptive RAG"    # 查询复杂度差异大
    return "Self-RAG"            # 通用场景，控制幻觉
```

---

## 总结

本章你学会了：

- 朴素 RAG 的四大痛点：检索不可控、无反思、无纠错、路径固定
- Self-RAG：生成过程的自我反思机制
- Corrective RAG：检索结果评估与纠错兜底
- Adaptive RAG：按问题复杂度自适应选择策略
- Agentic RAG：Agent 驱动的自主检索决策
- 四种模式的对比与选型方法

下一步：学习 [评估与生产化](../03-advanced/04-evaluation-and-production.md)，掌握 RAG 系统的质量评估与生产部署。