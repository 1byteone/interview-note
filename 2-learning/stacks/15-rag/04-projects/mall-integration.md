# AI 商城 RAG 集成

> 将 RAG 技术落地到 AI 商城的完整方案，覆盖商品文档智能问答、售后政策检索、客服对话增强三大核心场景，并给出完整的架构设计与代码实现。

---

## 1. AI 商城知识库 RAG 架构

### 1.1 总体架构

```
┌─────────────────────────────────────────────────────────────────┐
│                      用户（APP / Web / 客服）                       │
└──────────────────────────────┬──────────────────────────────────┘
                               ▼
┌──────────────────────────────────────────────────────────────────┐
│                     API 网关（Spring Cloud Gateway）              │
└──────────────────────────────┬──────────────────────────────────┘
                               ▼
┌──────────────────────────────────────────────────────────────────┐
│              RAG 服务（Python FastAPI，独立微服务）                 │
├──────────────────────────────────────────────────────────────────┤
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌────────────────┐      │
│  │ 领域守卫   │→│ 查询路由   │→│ 检索层    │→│ Reranker 精排   │      │
│  │ 意图识别   │ │ 知识库分类 │ │ 多路召回  │ │ 证据门控        │      │
│  └──────────┘ └──────────┘ └────┬─────┘ └────────┬───────┘      │
│                                 │                 │              │
│                                 ▼                 ▼              │
│  ┌──────────────────────────────────────────────────────┐       │
│  │  LLM 生成 + 引用溯源 + 流式输出                         │       │
│  └──────────────────────────┬───────────────────────────┘       │
└─────────────────────────────┬─────────────────────────────────────┘
                              ▼
┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐
│ Chroma/     │  │ Elasticsearch│  │ Neo4j       │  │ Redis 缓存  │
│ Milvus      │  │ BM25 索引    │  │ 知识图谱     │  │             │
│ 语义向量库   │  │ 全文检索      │  │ 关系推理     │  │ 高频缓存     │
└─────────────┘  └─────────────┘  └─────────────┘  └─────────────┘
```

### 1.2 微服务划分

| 微服务 | 技术栈 | 职责 |
|--------|--------|------|
| **rag-service** | Python FastAPI | RAG 核心流程：检索、融合、精排、生成 |
| **vector-store** | Milvus/Chroma | 向量存储与相似度检索 |
| **search-service** | Java + ES | 商品 BM25 检索（复用现有搜索服务） |
| **knowledge-graph** | Neo4j | 商品关联关系、知识图谱查询 |
| **llm-gateway** | Python | LLM 调用封装、缓存、限流 |

### 1.3 数据流

```
离线流程（数据管道）：
商品文档 → 清洗 → 分块 → 向量化 → 存入向量库
                        → 存入 ES（BM25）
                        → 实体提取 → 存入 Neo4j

在线流程（RAG 问答）：
用户问题 → 领域守卫 → 查询路由 → 多路检索 → RRF 融合
       → Reranker 精排 → 证据门控 → LLM 生成 → 引用溯源 → 返回
```

---

## 2. 商品文档智能问答

### 2.1 场景描述

用户询问商品详情，例如："iPhone 15 Pro 支持无线充电吗？电池容量多大？"系统需要从商品文档库检索准确信息回答。

```python
# rag-service/app/main.py — 商品问答接口
from fastapi import FastAPI
from pydantic import BaseModel

app = FastAPI(title="AI 商城 RAG 服务")

class QuestionRequest(BaseModel):
    question: str
    session_id: str = None

class RAGEngine:
    """AI 商城 RAG 引擎"""
    
    def __init__(self):
        # 向量检索器（商品文档库）
        self.product_retriever = vector_store.as_retriever(search_kwargs={"k": 5})
        # BM25 检索器
        self.bm25_retriever = BM25Retriever.from_documents(product_docs)
        self.bm25_retriever.k = 5
        # 融合检索器
        self.ensemble_retriever = EnsembleRetriever(
            retrievers=[self.bm25_retriever, self.product_retriever],
            weights=[0.3, 0.7],
        )
        self.llm = ChatOpenAI(model="gpt-4o-mini", temperature=0)
    
    def answer(self, question: str) -> dict:
        """回答商品问题"""
        # 1. 多路检索 + RRF 融合
        docs = self.ensemble_retriever.get_relevant_documents(question)
        
        # 2. 证据门控
        docs = evidence_gate(docs, min_level="C")
        
        # 3. 构建上下文（带引用编号）
        context = "\n".join([f"[{i+1}] {d.page_content}" for i, d in enumerate(docs)])
        
        # 4. 生成（带引用）
        prompt = f"""你是 AI 商城的智能客服，请基于商品文档回答用户问题。

商品文档：
{context}

问题：{question}

要求：
- 只基于文档回答，不编造
- 相关结论后标注来源编号 [1][2]
- 文档没有的信息回答"文档中未提及"

回答："""
        response = self.llm.invoke(prompt)
        
        return {
            "answer": response.content,
            "sources": [
                {"content": d.page_content[:100], "product_id": d.metadata.get("product_id")}
                for d in docs
            ],
        }

@app.post("/api/rag/product/qa")
async def product_qa(req: QuestionRequest):
    rag = RAGEngine()
    return rag.answer(req.question)
```

### 2.2 接口返回示例

```json
{
  "answer": "iPhone 15 Pro 支持 MagSafe 无线充电，最大功率 15W，同时支持最高 27W 有线快充 [1]。电池容量为 3274mAh [2]。",
  "sources": [
    {"content": "iPhone 15 Pro 支持 MagSafe 无线充电 15W...", "product_id": "P1001"},
    {"content": "iPhone 15 Pro 内置 3274mAh 电池...", "product_id": "P1001"}
  ]
}
```

---

## 3. 售后政策检索

### 3.1 场景描述

用户咨询退换货、保修、退款等政策问题。政策条款需要**精确检索**（条款编号）并配合**证据门控**（政策是高风险话题）。

```python
class PolicyRAG:
    """售后政策 RAG"""
    
    def __init__(self):
        # 政策文档检索器（精确匹配优先）
        self.policy_retriever = policy_vector_store.as_retriever(
            search_kwargs={"k": 3, "score_threshold": 0.7}
        )
        self.es_retriever = ESRetriever(index="policies")  # BM25 精确检索
        self.llm = ChatOpenAI(model="gpt-4o-mini", temperature=0)
    
    def answer(self, question: str) -> dict:
        # 1. 混合检索（政策条款精确匹配）
        vector_docs = self.policy_retriever.get_relevant_documents(question)
        es_docs = self.es_retriever.get_relevant_documents(question)
        docs = ensemble_fusion(vector_docs, es_docs, k=60)
        
        # 2. 高风险闸门：政策问题只允许 A/B 级证据
        docs = high_risk_gate(question, docs, min_level="B")
        if not docs:
            return {
                "answer": "抱歉，未找到匹配的政策条款，建议联系人工客服。",
                "sources": [],
            }
        
        # 3. 生成（严格引用条款）
        context = "\n".join([f"[{i+1}] {d.page_content}" for i, d in enumerate(docs)])
        prompt = f"""请基于以下政策条款回答用户问题，必须标注引用条款编号。

政策条款：
{context}

问题：{question}

回答："""
        response = self.llm.invoke(prompt)
        
        return {
            "answer": response.content,
            "policy_clauses": [d.metadata.get("clause_id") for d in docs],
        }

# 使用
policy_rag = PolicyRAG()
result = policy_rag.answer("手机保修期内屏幕碎了可以免费维修吗？")
print(result["answer"])
# "根据保修政策第 3.2 条 [1]，人为损坏（如屏幕碎屏）不在免费保修范围内..."
```

### 3.2 政策知识库结构

```
政策文档示例：
条款 3.1：主要零部件自购买日起 1 年免费保修
条款 3.2：人为损坏、进液、私自拆修不享受免费保修
条款 4.1：7 天无理由退货（不影响二次销售）
条款 4.2：15 天质量问题换货
条款 5.1：退款 1-3 个工作日原路返回
```

---

## 4. 客服对话增强

### 4.1 场景描述

客服人员在处理用户工单时，系统自动检索相关知识辅助回复。此处引入**多轮对话**能力。

```python
class CustomerServiceAssistant:
    """客服对话增强助手"""
    
    def __init__(self):
        self.rag = RAGEngine()
        # 历史工单检索器
        self.ticket_retriever = ticket_vector_store.as_retriever(
            search_kwargs={"k": 3}
        )
        self.llm = ChatOpenAI(model="gpt-4o-mini", temperature=0)
        self.conversation_history = {}  # session_id -> messages
    
    def chat(self, session_id: str, message: str) -> dict:
        """处理客服对话"""
        # 1. 获取历史
        history = self.conversation_history.get(session_id, [])
        
        # 2. 结合历史生成检索查询（处理指代）
        refined_query = self._resolve_reference(message, history)
        
        # 3. 多路检索
        rag_result = self.rag.answer(refined_query)
        ticket_docs = self.ticket_retriever.get_relevant_documents(message)
        
        # 4. 生成增强回答
        context_parts = [rag_result["answer"]] + [t.page_content for t in ticket_docs]
        context = "\n".join(context_parts[:6])
        
        prompt = f"""你是客服辅助助手，请结合历史对话和检索信息，生成给用户的回复建议。

历史对话：{history[-4:]}

检索信息：
{context}

用户问题：{message}

请生成：
1. 建议回复（可直接复制给用户）
2. 简明事由（客服备注用）"""
        
        response = self.llm.invoke(prompt)
        
        # 5. 保存历史
        history.append({"role": "user", "content": message})
        history.append({"role": "assistant", "content": response.content[:50]})
        self.conversation_history[session_id] = history[-10:]  # 最多保留 10 条
        
        return {"reply_suggestion": response.content}
    
    def _resolve_reference(self, message, history):
        """处理指代消解：'那款手机' → 上文的商品名"""
        if not history:
            return message
        recent = history[-2:]  # 最近一轮
        user_msgs = [m["content"] for m in recent if m["role"] == "user"]
        if any(k in message for k in ["这个", "那款", "它", "该商品"]):
            if user_msgs:
                return f"{message}（用户上文提到：{user_msgs[-1]}）"
        return message

# 使用
assistant = CustomerServiceAssistant()
result = assistant.chat("session_123", "之前问的 iPhone 15 Pro 怎么开无线充电？")
print(result["reply_suggestion"])
```

### 4.2 客服增强效果

| 场景 | 无 RAG 增强 | RAG 增强后 |
|------|------------|-----------|
| 政策咨询 | 客服人工翻文档，耗时长 | 自动检索条款，秒级返回 |
| 商品参数 | 依赖记忆，可能出错 | 检索商品文档，准确引用 |
| 复杂工单 | 信息不全 | 自动关联历史相似工单 |
| 多轮对话 | 指代混乱 | 结合上下文消解指代 |

---

## 5. 性能与稳定性方案

### 5.1 缓存策略

```python
# Redis 缓存设计
def cache_layer(question: str):
    """两级缓存：内存 + Redis"""
    # 语义缓存：相似问题复用答案（相似度 > 0.95）
    query_vec = embeddings.embed_query(question)
    cached_embed = redis_vector_cache.search(query_vec, threshold=0.95)
    
    if cached_embed:
        return cached_embed["answer"]  # 命中语义缓存
    
    # 未命中：走完整 RAG 流程
    answer = full_rag_pipeline(question)
    redis_vector_cache.add(question, answer)  # 写入缓存
    return answer
```

### 5.2 降级方案

| 故障场景 | 降级策略 |
|---------|---------|
| 向量库不可用 | 降级为 BM25 检索（ES 独立部署） |
| LLM 不可用 | 返回检索到的原始文档片段 |
| 检索全部失败 | 兜底回复 + 转人工客服 |
| 图数据库不可用 | 降级为纯向量检索（去掉图路） |

### 5.3 监控指标

```
RAG 服务核心指标：
- RAG 调用量、成功率
- P50/P95 延迟
- 检索命中率（有结果率）
- 证据门控通过率
- 缓存命中率
- 用户点赞/点踩比例（质量信号）
- 人工客服转接率（需转接说明 RAG 未解决）
```

---

## 总结

本章你学会了：

- AI 商城 RAG 系统的总体架构设计
- 商品文档智能问答的实现（多路检索 + 融合 + 精排）
- 售后政策检索的实现（精确匹配 + 证据门控）
- 客服对话增强的实现（多轮 + 指代消解 + 历史工单）
- 缓存、降级、监控等稳定性方案

下一步：动手实践 [迷你知识库 RAG 系统](../04-projects/mini-blog/README.md)，独立完成一个完整的 RAG 项目。