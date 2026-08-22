# RAG 评估与生产化

> RAG 系统上线前必须经过严格评估，上线后需要持续监控。本章讲解 RAGAS 评估框架、核心评估指标、在线评估方法以及生产化的关键考量。

---

## 1. 为什么需要评估 RAG？

RAG 系统由**检索**和**生成**两个环节组成，任何一环出问题都会影响最终质量：

```
质量 = 检索质量 × 生成质量

如果检索不到相关文档（recall 低）→ 生成必然差
如果检索到但模型没用 → 生成可能差
即使检索很好 → 生成仍可能产生幻觉
```

**没有评估就无法优化。** 评估是 RAG 系统迭代改进的基石。

---

## 2. RAGAS 评估框架

RAGAS（Retrieval-Augmented Generation Assessment）是一个开源的 RAG 评估框架，用 LLM 作为评判器（LLM-as-a-Judge）评估 RAG 系统质量。

### 2.1 安装与使用

```python
# 安装
# pip install ragas langchain-openai

from ragas import evaluate
from ragas.metrics import (
    faithfulness,
    answer_relevancy,
    context_precision,
    context_recall,
    answer_correctness,
)
from datasets import Dataset
import pandas as pd

# 1. 准备评估数据
eval_data = {
    "question": [
        "iPhone 15 Pro 支持快充吗？",
        "7 天无理由退货的条件是什么？",
        "华为 Mate 60 Pro 的电池容量是多少？",
    ],
    "answer": [
        "iPhone 15 Pro 支持最高 27W 快充。",
        "签收后 7 天内，不影响二次销售可退货。",
        "华为 Mate 60 Pro 内置 5000mAh 电池。",
    ],
    "contexts": [
        ["iPhone 15 Pro 支持 27W PD 快充，电池 3274mAh。"],
        ["7 天无理由退货：签收后 7 天内，不影响二次销售可申请退货。"],
        ["华为 Mate 60 Pro：5000mAh 电池，88W 有线快充。"],
    ],
    "ground_truth": [
        "iPhone 15 Pro 支持最高 27W 快充。",
        "商品签收后 7 天内，在不影响二次销售的前提下可申请无理由退货。",
        "华为 Mate 60 Pro 内置 5000mAh 容量电池。",
    ],
}

dataset = Dataset.from_dict(eval_data)

# 2. 运行评估
result = evaluate(
    dataset,
    metrics=[
        faithfulness,
        answer_relevancy,
        context_precision,
        context_recall,
    ],
)

# 3. 查看结果
df = result.to_pandas()
print(df[["question", "faithfulness", "answer_relevancy", "context_precision", "context_recall"]])
```

---

## 3. 评估指标详解

### 3.1 生成质量指标

**Faithfulness（忠实度）**：回答中的每个事实是否都能从检索上下文中找到依据。

```
评估方式：将回答拆分为事实语句，逐条检查是否被上下文支持。
Faithfulness = 被支持的事实数 / 总事实数

示例：
回答："退货期限是 7 天" → 上下文支持 ✓
回答："退货期限是 30 天" → 上下文不支持 ✗
Faithfulness = 0.5
```

**Answer Relevancy（回答相关性）**：回答是否针对问题，而非答非所问。

```
评估方式：让 LLM 从回答反推问题，计算与原问题的相似度。

示例：
问题："手机电池容量是多少？"
回答："手机屏幕是 6.1 英寸" → 相关性低
回答："电池容量为 3274mAh" → 相关性高
```

### 3.2 检索质量指标

**Context Precision（上下文精确率）**：检索结果中真正相关的比例。

```
Context Precision = 相关上下文数量 / 检索出的总上下文数量

示例：检索返回 5 段，其中 3 段相关
Context Precision = 3/5 = 0.6
```

**Context Recall（上下文召回率）**：所有相关知识中被检索到的比例。

```
Context Recall = 检索到的相关知识量 / 真值中所需的总知识量

示例：回答需要 4 条知识，检索到了 3 条
Context Recall = 3/4 = 0.75
```

### 3.3 指标速查表

| 指标 | 评估对象 | 越高越好 | 说明 |
|------|---------|---------|------|
| Faithfulness | 生成 | 是 | 回答是否忠实于上下文（防幻觉） |
| Answer Relevancy | 生成 | 是 | 回答是否切题 |
| Context Precision | 检索 | 是 | 检索结果是否精准 |
| Context Recall | 检索 | 是 | 检索是否完整 |
| Answer Correctness | 生成 | 是 | 与真值对比的整体正确性 |

---

## 4. 在线评估

生产环境的 RAG 需要持续监控。

### 4.1 在线评估方案

```python
from ragas.llms import LangchainLLMWrapper
from langchain_openai import ChatOpenAI
import time

class OnlineRAGEvaluator:
    """在线 RAG 评估器"""
    
    def __init__(self):
        self.evaluator_llm = LangchainLLMWrapper(
            ChatOpenAI(model="gpt-4o-mini", temperature=0)
        )
        self.metrics = [faithfulness, answer_relevancy, context_precision, context_recall]
    
    def evaluate_single(self, query, answer, contexts, ground_truth):
        """评估单次对话"""
        from datasets import Dataset
        dataset = Dataset.from_dict({
            "question": [query],
            "answer": [answer],
            "contexts": [contexts],
            "ground_truth": [ground_truth],
        })
        result = evaluate(dataset, metrics=self.metrics)
        return result.to_pandas().iloc[0].to_dict()
    
    def monitor(self, query, answer, contexts, ground_truth=None):
        """监控入口：记录单次评估 + 日志"""
        metrics = self.evaluate_single(query, answer, contexts, ground_truth)
        
        # 记录日志
        log_entry = {
            "timestamp": time.time(),
            "query": query,
            "faithfulness": metrics["faithfulness"],
            "answer_relevancy": metrics["answer_relevancy"],
            "context_precision": metrics["context_precision"],
            "context_recall": metrics["context_recall"],
        }
        
        # 低于阈值告警
        if metrics["faithfulness"] < 0.7:
            print(f"[警告] Faithfulness 偏低：{metrics['faithfulness']:.2f}")
        
        return log_entry

# 使用
evaluator = OnlineRAGEvaluator()
logs = [
    evaluator.monitor("退货条件？", "签收后 7 天内可退货", ["7 天无理由退货条款..."], "签收后 7 天内可退货"),
]
```

### 4.2 评估数据集设计

| 数据来源 | 用途 |
|---------|------|
| 用户真实问题 + 人工标注 | 最贴近生产，成本高 |
| LLM 生成的合成问题 | 覆盖率高，有噪声 |
| 领域专家标注的黄金数据集 | 权威，数量少 |
| 线上日志抽样回放 | 持续评估 |

---

## 5. 生产化关键点

### 5.1 延迟优化

```python
# RAG 系统延迟预算（目标：P95 < 2s）
LATENCY_BUDGET = {
    "embedding": 100,    # 查询向量化 100ms
    "retrieval": 100,    # 向量检索 100ms
    "rerank": 200,       # 精排 200ms
    "generation": 1500,  # LLM 生成 1500ms（流式）
}

def optimize_latency():
    strategies = {
        "缓存": "高频查询结果的向量缓存到 Redis，命中时省去 embedding+检索",
        "向量索引": "HNSW 索引可达到毫秒级检索（10万级）",
        "批量嵌入": "Embedding 批量调用，利用 GPU 并行",
        "流式输出": "LLM 使用 SSE 流式，首 token 等待时间减半",
        "模型选择": "检索用 fast 模型，生成用小模型（如 gpt-4o-mini）",
    }
    return strategies
```

### 5.2 缓存策略

```python
import redis
import json

class RAGCache:
    """RAG 结果缓存"""
    
    def __init__(self, redis_client, ttl=3600):
        self.redis = redis_client
        self.ttl = ttl
    
    def get_cached(self, query_hash):
        """获取缓存结果"""
        cached = self.redis.get(f"rag:cache:{query_hash}")
        return json.loads(cached) if cached else None
    
    def set_cache(self, query_hash, result):
        """写入缓存"""
        self.redis.setex(
            f"rag:cache:{query_hash}",
            self.ttl,
            json.dumps(result, ensure_ascii=False),
        )
    
    def generate_with_cache(self, query, generate_fn):
        """带缓存的 RAG 调用"""
        import hashlib
        query_hash = hashlib.md5(query.encode()).hexdigest()
        
        # 命中缓存
        cached = self.get_cached(query_hash)
        if cached:
            return cached
        
        # 未命中，调用生成并缓存
        result = generate_fn(query)
        self.set_cache(query_hash, result)
        return result

# 缓存层级设计
CACHE_LEVELS = {
    "L1 内存缓存": "同进程内高频问题缓存，微秒级",
    "L2 Redis 缓存": "跨实例共享，毫秒级",
    "L3 向量库缓存": "weekly 索引重建，支持增量更新",
}
```

### 5.3 监控与告警

```python
# 生产监控指标
MONITORING_METRICS = {
    # 性能指标
    "latency_p50": "P50 延迟",
    "latency_p95": "P95 延迟",
    "retrieval_time": "检索耗时",
    "generation_time": "生成耗时",
    
    # 质量指标
    "faithfulness": "忠实度（防幻觉）",
    "answer_relevancy": "回答相关性",
    "empty_result_rate": "无结果率",
    
    # 业务指标
    "user_satisfaction": "用户满意率",
    "question_volume": "问题量",
    "cache_hit_rate": "缓存命中率",
    
    # 资源指标
    "token_usage": "Token 消耗",
    "api_cost": "API 成本",
}

def setup_monitoring():
    """监控方案设计"""
    alert_rules = {
        "faithfulness < 0.6": "WARN：幻觉风险升高",
        "latency_p95 > 3s": "CRITICAL：延迟超预算",
        "empty_result_rate > 20%": "WARN：检索质量下降",
        "cache_hit_rate < 30%": "INFO：缓存效率低",
        "token_usage > 100万/天": "WARN：成本超预算",
    }
    return alert_rules
```

### 5.4 知识更新机制

```python
class KnowledgeUpdateManager:
    """知识库更新管理"""
    
    def __init__(self):
        self.queues = {
            "real_time": [],   # 实时更新（秒级）
            "near_real_time": [],  # 准实时（分钟级）
            "batch": [],       # 批量更新（小时/天级）
        }
    
    def update_pipeline(self, new_docs):
        """更新流程：
        1. 新文档 → 分块 → 向量化
        2. 增量更新向量库（Chroma/Milvus 支持）
        3. 同步更新 BM25 索引（ES 文档更新）
        4. 清理缓存（涉及更新的查询）
        """
        # 简化示例
        for doc in new_docs:
            # 分块
            chunks = text_splitter.split_documents([doc])
            # 增量添加
            vector_store.add_documents(chunks, ids=[c.metadata["id"] for c in chunks])
            # 更新 ES BM25 索引
            es.index(index="rag_docs", id=doc.metadata["id"], body=doc.dict())
        
        # 让过期缓存失效
        cache_client.flush_by_prefix("rag:cache:")
```

---

## 6. 生产化 Checklist

```
□ 检索层
  □ 混合检索（向量 + BM25）已上线
  □ Reranker 精排已配置
  □ 证据门控已启用
  
□ 生成层
  □ 温度已调低（0~0.3）
  □ 引用溯源已开启
  □ 领域守卫已配置
  
□ 评估层
  □ RAGAS 离线评估通过（Faithfulness > 0.8）
  □ 在线评估流水线已建立
  □ 告警阈值已配置
  
□ 基础设施
  □ 向量库高可用（主从/集群）
  □ Redis 缓存已接入
  □ 监控大盘已上线（Grafana）
  □ 知识更新机制已跑通
```

---

## 总结

本章你学会了：

- RAG 评估的必要性：检索质量 × 生成质量
- RAGAS 评估框架的使用
- 四个核心指标：Faithfulness、Answer Relevancy、Context Precision、Context Recall
- 在线评估与监控方案
- 生产化四大关键点：延迟、缓存、监控、知识更新
- 生产化 Checklist

下一步：进入 [AI 商城项目集成](../04-projects/mall-integration.md)，将 RAG 落地到真实业务场景。