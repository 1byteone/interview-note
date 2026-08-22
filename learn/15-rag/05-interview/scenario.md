# RAG 面试场景题

> 考察 RAG 系统的实际工程问题诊断与解决能力。

---

## 场景 1：检索效果差，召回不到相关文档

### 问题描述

RAG 系统上线后，发现很多用户问题检索不到相关文档，回答质量差。如何诊断和解决？

### 诊断步骤

```
1. 检查检索结果
   → 是检索结果为 0？还是检索到但不相关？
   → 查看向量相似度分数，是否低于阈值？

2. 检查分块质量
   → 分块是否过大（噪声多）？是否过小（信息不全）？
   → 关键信息是否被切分边界截断？

3. 检查 Embedding 质量
   → 使用 Embedding 模型自带的测试用例验证
   → 查看同义词是否能匹配（"手机"→"移动电话"）
```

### 解决方案

| 问题 | 方案 | 优先级 |
|------|------|--------|
| 分块不合理 | 调整 chunk_size（推荐 256~512），增加 overlap（10%~20%） | P0 |
| 仅用向量检索 | 增加 BM25 混合检索，提高精确匹配能力 | P0 |
| 查询表述差 | 加入 Multi-Query 或 HyDE 查询转换 | P1 |
| Embedding 模型弱 | 升级为 BGE-m3 或 OpenAI text-embedding-3-large | P1 |
| 相似度阈值过高 | 降低 score_threshold（如 0.7→0.5） | P2 |
| 缺失 Top-K | 增大 k 值（如 3→10），再用 Reranker 精排 | P2 |

### 诊断检查清单

```python
# 检索质量诊断脚本
def diagnose_retrieval(query: str, retriever, vector_store):
    print(f"===== 检索诊断：{query} =====")
    
    # 1. 检查向量检索
    vector_results = vector_store.similarity_search_with_relevance_scores(query, k=5)
    print(f"向量检索结果数：{len(vector_results)}")
    for doc, score in vector_results:
        print(f"  score={score:.3f} | {doc.page_content[:80]}...")
    
    # 2. 检查 BM25 检索
    bm25_results = bm25_retriever.get_relevant_documents(query)
    print(f"BM25 检索结果数：{len(bm25_results)}")
    
    # 3. 检查是否有结果
    if not vector_results and not bm25_results:
        print("[结论] 知识库中可能没有相关文档，请检查数据量")
    elif all(s < 0.5 for _, s in vector_results):
        print("[结论] 相似度普遍偏低，考虑降低阈值或升级 Embedding 模型")
    else:
        print("[结论] 检索基本正常，可检查 Reranker 和生成环节")
```

---

## 场景 2：RAG 响应慢，延迟高

### 问题描述

RAG 系统 P95 延迟超过 5 秒，用户体验差。如何优化？

### 延迟分布分析

```
优化前延迟分布：
├── Embedding 查询向量化：200ms
├── 向量检索（IVF 索引）：50ms
├── BM25 检索（ES 查询）：100ms
├── RRF 融合：5ms
├── Reranker 精排（top-30→top-5）：300ms
├── LLM 生成（非流式）：3000ms
└── 总延迟：~3655ms
```

### 优化方案

| 优化项 | 措施 | 预期效果 |
|--------|------|---------|
| 缓存 | 高频问题语义缓存（Redis） | 命中时延迟降至 10ms |
| 向量索引 | HNSW 替代 IVF（efSearch=100） | 检索 50ms→10ms |
| 流式输出 | SSE 流式，首 token 延迟 | 首 token 从 3s→1s |
| Reranker 量化 | INT8 量化 | 300ms→150ms |
| 批量 Embedding | 查询向量化批处理 | 200ms→50ms |
| 小模型生成 | 用 gpt-4o-mini 替代 gpt-4 | 3s→1.5s |

### 优化后延迟预算

```
优化后延迟预算（P95 < 2s）：
├── 缓存命中：10ms（L1 内存）~ 50ms（L2 Redis）
├── 未命中：
│   ├── Embedding：50ms
│   ├── 向量检索（HNSW）：10ms
│   ├── BM25 检索：50ms
│   ├── RRF 融合：5ms
│   ├── Reranker（INT8）：150ms
│   └── LLM 生成（流式，首 token）：500ms
└── P95 总延迟：~800ms（首 token）
```

---

## 场景 3：幻觉严重，回答不准确

### 问题描述

RAG 系统经常生成与事实不符的内容，用户投诉多。如何解决？

### 根因分析

```
幻觉根因矩阵：

检索层：
  □ 检索到的文档不相关（召回低）
  □ 检索到的文档包含过时信息
  □ 检索结果太少，LLM 无足够信息

增强层：
  □ 上下文太长，LLM 忽略关键信息
  □ 多文档矛盾，LLM 选择错误
  □ Prompt 未明确"仅基于文档回答"

生成层：
  □ 温度太高（>0.5），随机性强
  □ 模型参数化知识干扰（模型"知道"的覆盖了检索结果）
  □ 模型过于自信，不承认不知道
```

### 解决方案

| 层级 | 措施 | 效果 |
|------|------|------|
| 检索 | 混合检索（向量+BM25）提高召回 | 减少"无相关文档"的幻觉 |
| 检索 | 证据门控（A/B/C 级过滤） | 低质量文档不用于生成 |
| 增强 | Prompt 明确"只基于文档，不编造" | 减少模型自行发挥 |
| 增强 | 要求"文档中没有则回答不知道" | 避免错误猜测 |
| 生成 | 温度调至 0，降低随机性 | 更确定性的输出 |
| 生成 | 引用溯源（标注 [1][2][3]） | 可追溯，便于验证 |
| 后处理 | 幻觉检测 LLM 检查 | 二次校验，拦截幻觉 |
| 监控 | 用户反馈收集（点赞/点踩） | 持续改进 |

### 幻觉检测实现

```python
def detect_and_block(query: str, answer: str, docs: list, llm) -> dict:
    """检测幻觉并拦截"""
    evaluate_prompt = f"""请检查回答是否基于提供的文档，是否有幻觉。

文档内容：
{"\n".join([d.page_content for d in docs])}

问题：{query}

回答：{answer}

请逐句检查：
1. 回答中的每个事实是否都在文档中有依据？
2. 是否有文档中没有的信息？
3. 是否有与文档矛盾的信息？

如果存在幻觉，输出 hallucination=true 并说明原因。
如果全部正确，输出 hallucination=false。"""
    
    result = llm.invoke(evaluate_prompt).content
    if "hallucination=true" in result.lower() or "幻觉" in result:
        return {
            "blocked": True,
            "reason": result,
            "fallback": "抱歉，当前无法从知识库中找到准确信息，请稍后再试或联系人工客服。"
        }
    return {"blocked": False, "answer": answer}
```

---

## 场景 4：知识更新问题

### 问题描述

商品信息更新后，RAG 系统仍返回旧信息。如何保证知识库实时性？

### 更新方案

```python
class KnowledgeUpdateManager:
    """知识更新管理"""
    
    def __init__(self):
        self.update_queue = asyncio.Queue()
    
    async def update_knowledge(self, doc_id: str, new_content: str):
        """更新知识库"""
        # 1. 分块新文档
        chunks = text_splitter.split_text(new_content)
        
        # 2. 更新向量库
        vector_store.delete(ids=[doc_id])  # 删除旧文档
        vector_store.add_texts(chunks, ids=[f"{doc_id}_chunk{i}" for i in range(len(chunks))])
        
        # 3. 更新 BM25 索引
        bm25_retriever.delete(doc_id)
        bm25_retriever.add_texts(chunks)
        
        # 4. 失效缓存
        cache.delete_by_prefix(f"rag:cache:{doc_id}")
        
        # 5. 记录更新日志
        logger.info(f"知识更新：{doc_id}")
    
    async def schedule_update(self, schedule: list):
        """批量定时更新"""
        for item in schedule:
            await asyncio.sleep(item["delay"])
            await self.update_knowledge(item["doc_id"], item["content"])
```

### 更新策略

| 更新频率 | 策略 | 技术方案 |
|---------|------|---------|
| 实时（秒级） | 事件驱动，增量更新 | 消息队列 + 增量索引 |
| 准实时（分钟级） | 定时扫描变更 | 定时任务 + 差异更新 |
| 批量（天级） | 全量重建索引 | 定时重建 Chroma/FAISS 索引 |

### 更新时的一致性保证

```
问题：更新过程中，用户可能检索到新旧混合的结果
方案：
1. 版本号：每个文档记录版本，检索时过滤旧版本
2. 双缓冲：同时保留新旧两个索引，切换时原子交换
3. 灰度更新：先更新部分副本，逐步全量
```

---

> 返回 [速记版](quick-revision.md) | 深挖题 [deep-dive.md](deep-dive.md) | 代码题 [coding.md](coding.md)