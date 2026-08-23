# RAG 面试题大全

## 📚 知识体系

```
RAG 架构
├── 数据预处理
│   ├── 文档解析 (PDF/Word/Markdown/HTML)
│   ├── 去重清洗
│   ├── Chunking 分块策略
│   └── Metadata 元数据
├── 向量化
│   ├── Embedding 模型
│   ├── 向量维度
│   └── 向量归一化
├── 存储与索引
│   ├── 向量数据库 (FAISS/Pinecone/Chroma/Qdrant/Milvus)
│   ├── HNSW / IVF 索引
│   └── 混合检索
├── 检索增强
│   ├── 语义相似度检索
│   ├── 关键词检索 (BM25)
│   ├── 混合检索 (Hybrid)
│   ├── Reranking 重排序
│   └── Query Rewriting
└── 生成回答
    ├── Prompt 组装
    ├── 上下文窗口管理
    ├── 引用溯源
    └── 幻觉控制
```

---

## 🎯 Level 1：基础题

### 1. 什么是 RAG？为什么要用 RAG？
**答案**：
RAG（Retrieval-Augmented Generation，检索增强生成）是一种结合信息检索与大语言模型生成能力的技术。

**RAG 解决的核心问题**：
1. **知识更新**：LLM 训练数据有截止时间，RAG 可以接入最新知识
2. **幻觉问题**：通过检索真实文档降低模型编造内容的概率
3. **知识私有化**：免训练接入企业私有知识库
4. **成本控制**：比 Fine-tuning 成本更低，无需训练

**适用场景**：
- 智能客服（FAQ）
- 企业知识库问答
- 法律/医疗文档问答
- 代码检索与问答
- 产品说明书助手

### 2. RAG 的整体流程是什么？
**答案**：

```text
【离线索引阶段】
文档 → 解析 → 清洗 → 分块(Chunking) → Embedding → 向量库

【在线问答阶段】
用户问题
    ↓
查询改写(可选) → 生成向量
    ↓
向量检索 + 关键词检索(BM25)
    ↓
混合 → Reranking 重排序
    ↓
Top-K 上下文拼接
    ↓
Prompt + LLM
    ↓
回答

```

### 3. 如何选择 Embedding 模型？
**答案**：

| 考量维度 | 说明 |
|----------|------|
| 中文能力 | 选择针对中文优化的模型（如 bge-large-zh） |
| 最大序列长度 | 与 Chunk 大小匹配（512/1024/4096） |
| 向量维度 | 影响存储和检索性能（768/1024/1536） |
| 检索效果 | MTEB 等基准测试分数 |
| 推理性能 | 延迟和吞吐（CPU/GPU） |
| 成本 | 自部署 vs API 调用 |

**常用 Embedding 模型**：
- BAAI/bge-m3（中文友好）
- text-embedding-3-large（OpenAI）
- text-embedding-ada-002（OpenAI）
- m3e（中文）
- text2vec（中文）

### 4. 如何选择 Chunking 策略？
**答案**：

**常用分块方式**：
1. **固定大小分块**：按 token 数量切分（如 500 token，50 overlap）
2. **按段落分块**：按自然段落切分（Markdown 标题）
3. **按语义分块**：语义完整性切分（LLM 辅助）
4. **按结构分块**：按章节/标题/表格结构

**分块策略选择**：
```
文档类型 → 分块策略
Markdown → 按标题层级分块
PDF 论文 → 按段落 + 摘要
代码 → 按函数/类分块
长文档 → 父子分块 (Parent-Child Retriever)
```

---

## 🎯 Level 2：进阶题

### 5. 什么是混合检索？为什么要用混合检索？
**答案**：
混合检索（Hybrid Search）结合**关键词检索（BM25）**和**向量语义检索**。

**为什么需要混合检索**：
- **关键词检索**：擅长精确匹配（型号、人名、编号）
- **向量检索**：擅长语义相似（同义表达）
- 单一检索方式召回不全，混合可以互补

**两种实现方案**：
1. **两路检索 + 合并**：
```
query → BM25 → 结果A
query → Vector → 结果B
    ↓
合并去重 → 加权 → Rerank → 输出
```

2. **稀疏+稠密混合向量**：
- SPLADE 稀疏向量 + Dense 稠密向量
- 一体化模型同时支持两种检索

### 6. 什么是 Reranking？为什么要 Rerank？
**答案**：
Reranking 是对检索出的 Top-K 结果进行精细化重排序的过程。

**为什么需要**：
- First-stage 检索（向量/BM25）追求召回率
- 嵌入向量无法完美建模词项级交互
- 交叉编码器（Cross-Encoder）直接计算 query-doc 相关度更准

**常用 Rerank 模型**：
- bge-reranker-v2-m3
- Cohere Rerank
- 阿里 gte-rerank

### 7. 如何评估 RAG 系统的效果？
**答案**：

**评估维度**：

| 维度 | 指标 | 说明 |
|------|------|------|
| 检索质量 | Recall@K / Precision@K | 相关文档是否被召回 |
| 检索质量 | MRR / NDCG | 排序质量 |
| 生成质量 | 忠实度 (Faithfulness) | 回答是否忠于文档 |
| 生成质量 | 相关性 (Relevance) | 回答是否切题 |
| 生成质量 | 幻觉率 | 是否有编造内容 |
| 端到端 | 答案准确率 | 人工标注评估 |

**评估工具**：
- RAGAS
- LlamaIndex Evals
- TruLens

---

## 🎯 Level 3：高级题

### 8. 什么是 Query Rewriting（查询改写）？
**答案**：
Query Rewriting 是在检索前对用户原始问题进行处理的技术。

**改写策略**：
1. **多查询扩展（Multi-Query）**：将一个问题拆解为多个子问题
2. **HyDE（虚构文档增强）**：先用 LLM 生成假设答案，再进行检索
3. **子问题切分（Sub-question）**：复杂问题拆解
4. **同义扩展**：补充同义词、缩写、实体

```python
# Multi-Query 示例
from langchain.retrievers.multi_query import MultiQueryRetriever

retriever = MultiQueryRetriever.from_llm(
    retriever=base_retriever,
    llm=llm
)
```

### 9. 什么是 Agentic RAG？与普通 RAG 有什么区别？
**答案**：

**普通 RAG**：检索 → 生成（固定 Pipeline）

**Agentic RAG**：将 Agent 能力引入 RAG 流程

| 对比 | 普通 RAG | Agentic RAG |
|------|----------|-------------|
| 流程 | 固定 | 动态决策 |
| 是否多跳 | 单次检索 | 多跳检索 |
| 工具调用 | 无 | 可调用工具 |
| 自我纠错 | 无 | 有（自我反思） |
| 复杂度 | 简单 | 复杂 |

**Agentic RAG 典型模式**：
1. **多跳检索（Multi-hop）**：逐步细化查询
2. **自主判断**：判断是否需要检索、检索什么
3. **路由**：将问题路由到不同知识库
4. **工具调用**：检索外部数据库/API
5. **反馈循环**：检索结果不满意时重新检索

---

## 🎯 Level 4：专家题

### 10. 如何解决 RAG 的幻觉问题？
**答案**：

**幻觉来源**：
1. 检索到了不相关内容
2. 上下文冲突
3. LLM 本身倾向生成

**解决方案**：
1. **检索端**：
   - 提高检索质量（混合检索 + Rerank）
   - 加入相关性判定，相关性不足不进入上下文
2. **生成端**：
   - Prompt 限定"只能基于文档回答"
   - 引用溯源（回答标注来源文档）
   - 温度降低、限制发散
3. **验证端**：
   - Faithfulness 检验（NLI 判断）
   - 对比校验：回答与文档的蕴含关系
4. **拒绝回答**：低置信度时提示"知识库中未找到"

### 11. 生产级 RAG 架构如何设计？
**答案**：

```
【数据层】
文档 → 解析 → Chunking → Embedding → 向量库(Milvus/Qdrant)
                              ↕ 增量更新
              CDC (Canal) / 定时任务同步

【服务层】
用户请求
    ↓
API Gateway
    ↓
RAG Service (FastAPI)
    ├── Query Understanding (意图/路由)
    ├── Hybrid Search (Vector + BM25)
    ├── Rerank
    ├── Context Assembly
    ├── Prompt Template
    └── LLM Call (模型路由/缓存)
    ↓
响应返回（含引用）

【观测层】
- Langfuse / LangSmith 链路追踪
- 检索质量监控 (Recall@K)
- 回答质量抽样评估
- 缓存命中率
```

**关键设计决策**：
1. **向量库选型**：数据量、并发、可用性
2. **模型路由**：简单问题用小模型，复杂用大模型
3. **高可用**：多实例 + 队列削峰
4. **流式输出**：SSE 流式返回
5. **引用溯源**：回答可追溯

---

## 📖 学习资源

### 推荐项目
- [LangChain](https://github.com/langchain-ai/langchain) - LLM 应用框架
- [LlamaIndex](https://github.com/run-llama/llama_index) - RAG 框架
- [Qdrant](https://github.com/qdrant/qdrant) - 向量数据库
- [Milvus](https://github.com/milvus-io/milvus) - 向量数据库
- [RAGAS](https://github.com/explodinggradients/ragas) - RAG 评估

### 框架对比
| 框架 | 特点 | 适用场景 |
|------|------|----------|
| LangChain | 生态丰富 | 快速原型 |
| LlamaIndex | 数据索引强 | 文档问答 |
| Haystack | 生产级 | 企业应用 |
| Dify | 可视化低代码 | 业务快速落地 |

### 最佳实践
1. 先建立评估集（golden dataset）再优化
2. Chunk 大小与模型输入窗口匹配
3. Metadata 设计（来源、标题、章节）
4. 冷启动先跑通最小闭环
5. 上线后持续监控检索质量