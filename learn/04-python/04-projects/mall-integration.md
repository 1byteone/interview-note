# AI 商城中的 Python 应用场景

> 🛠 项目实战 | 预计阅读：30 分钟

本文展示 Python 在 AI 智能商城中的具体落地位置。你已经在 01-backend-development 中看到了整体架构，这里聚焦 Python 负责的**三个关键服务**。

---

## 1. Python 在 AI 商城中的定位

```
┌─────────────────────────────────────────────────────────────┐
│                      AI 智能商城                              │
├─────────────────┬───────────────────┬───────────────────────┤
│    Java 服务层   │    Python 服务层   │     AI 能力层          │
│                 │                   │                       │
│  ┌───────────┐  │  ┌─────────────┐  │  ┌───────────────┐   │
│  │ 用户服务   │  │  │ AI 搜索服务  │  │  │ 大模型 API    │   │
│  ├───────────┤  │  │ (FastAPI)   │  │  │ (OpenAI/本地)  │   │
│  │ 订单服务   │  │  ├─────────────┤  │  ├───────────────┤   │
│  ├───────────┤  │  │ 数据清洗服务  │  │  │ 向量数据库    │   │
│  │ 商品服务   │  │  │ (Pandas)    │  │  │ (Milvus/ES)   │   │
│  ├───────────┤  │  ├─────────────┤  │  ├───────────────┤   │
│  │ 支付服务   │  │  │ 异步任务处理  │  │  │ Embedding 模型│   │
│  └───────────┘  │  │ (Celery)   │  │  └───────────────┘   │
│                 │  └─────────────┘  │                      │
│  Java 管交易    │  Python 管智能    │  AI 能力底座         │
└─────────────────┴───────────────────┴───────────────────────┘
```

**职责边界**：Java 负责交易链路的稳定性与一致性，Python 负责 AI 能力的编排与数据处理。

---

## 2. AI 搜索服务（FastAPI + LangChain）

### 2.1 架构

```
用户请求 → Spring Cloud Gateway → Python AI 搜索服务 → 多路召回 → Rerank → 响应
                    │                          │
                    ↓                          ↓
               Java 商品服务             向量数据库 + ES
               (提供商品元数据)           (相似度搜索)
```

### 2.2 核心代码片段

```python
# search_service.py
from fastapi import FastAPI, Query
from langchain_openai import OpenAIEmbeddings
from typing import List
import asyncio

app = FastAPI(title="AI 搜索服务")
embeddings = OpenAIEmbeddings(model="text-embedding-3-small")

@app.get("/api/v1/search")
async def search(
    q: str = Query(..., description="用户搜索词"),
    top_k: int = Query(10, ge=1, le=50),
):
    # 1. 生成查询向量
    query_vector = await embeddings.aembed_query(q)

    # 2. 多路召回（向量搜索 + ES 关键词搜索）
    vector_results, keyword_results = await asyncio.gather(
        vector_search(query_vector, top_k),
        keyword_search(q, top_k),
    )

    # 3. 合并去重
    combined = merge_results(vector_results, keyword_results)

    # 4. Rerank 排序
    ranked = await rerank(q, combined)

    # 5. 补充商品元数据（调用 Java 商品服务）
    enriched = await enrich_with_metadata(ranked)

    return {"query": q, "results": enriched, "total": len(ranked)}
```

### 2.3 关键设计点

- **异步优先**：所有外部调用（向量库、ES、Java 商品服务）都用 `await` 实现并发
- **多路召回**：向量搜索 + 关键词搜索取长补短，提升召回率
- **Rerank**：用一个轻量级 cross-encoder 模型对召回结果重新排序，提升精度

---

## 3. 数据清洗服务（Pandas + NumPy）

商品数据质量决定 AI 搜索效果。数据清洗服务定期运行，将原始商品数据转化为高质量语料：

```python
# data_cleaning.py
import pandas as pd
import numpy as np
from typing import Dict, Any

def clean_products(raw_path: str, output_path: str) -> Dict[str, Any]:
    """清洗商品数据，为向量化做准备"""
    df = pd.read_csv(raw_path)

    stats = {
        "total": len(df),
        "removed": 0,
        "cleaned": 0,
    }

    # 1. 删除关键字段缺失的行
    before = len(df)
    df = df.dropna(subset=["title", "price"])
    stats["removed"] += before - len(df)

    # 2. 价格异常值处理
    df = df[df["price"].between(0.01, 100000)]
    df["price"] = df["price"].astype(float)

    # 3. 文本清洗（为 embedding 做准备）
    df["clean_title"] = (
        df["title"]
        .str.strip()
        .str.replace(r"\s+", " ", regex=True)
        .str[:512]  # 截断到 embedding 模型最大长度
    )

    # 4. 文本拼接：汇聚多个字段为一条语料
    df["embedding_text"] = (
        df["clean_title"] + "。" +
        df["category"].fillna("") + " " +
        df["brand"].fillna("") + " " +
        df["description"].fillna("")[:200]
    )

    # 5. 去重
    df = df.drop_duplicates(subset=["embedding_text"])

    # 6. 输出
    df.to_csv(output_path, index=False, encoding="utf-8")

    stats["cleaned"] = len(df)
    stats["duplicate_rate"] = round(
        (stats["total"] - stats["removed"] - len(df)) / stats["total"] * 100, 2
    )
    return stats

# 运行
stats = clean_products("products_raw.csv", "products_clean.csv")
print(f"清洗完成：共 {stats['total']} 条，移除 {stats['removed']} 条，"
      f"去重率 {stats['duplicate_rate']}%")
```

---

## 4. 异步任务处理

AI 商城中的耗时操作（批量生成商品描述、批量 embedding、更新索引）都通过异步任务处理：

```python
# tasks.py
import asyncio
from typing import List, Dict

class AsyncTaskProcessor:
    """批量异步任务处理器"""

    def __init__(self, max_concurrency: int = 5):
        self.semaphore = asyncio.Semaphore(max_concurrency)

    async def process_batch(
        self, items: List[Dict], process_func
    ) -> List[Dict]:
        """并发处理一批任务，控制并发度"""

        async def bounded(item):
            async with self.semaphore:
                return await process_func(item)

        tasks = [bounded(item) for item in items]
        results = await asyncio.gather(*tasks, return_exceptions=True)

        # 处理异常
        successes, failures = [], []
        for item, result in zip(items, results):
            if isinstance(result, Exception):
                failures.append({"item": item, "error": str(result)})
            else:
                successes.append(result)

        return {"successes": successes, "failures": failures}

# 使用场景：批量生成商品 embedding
async def generate_embedding(product):
    """调用 embedding 模型"""
    text = product["embedding_text"]
    vector = await embedding_model.aembed_query(text)
    return {**product, "vector": vector}

async def main():
    processor = AsyncTaskProcessor(max_concurrency=10)
    products = load_products()  # 1000 条商品

    result = await processor.process_batch(products, generate_embedding)
    print(f"成功: {len(result['successes'])} 条")
    print(f"失败: {len(result['failures'])} 条")

    # 将向量写入向量数据库
    bulk_write_to_vector_db(result["successes"])
```

---

## 5. 部署与运维

CI/CD 流水线中的 Python 相关步骤：

```yaml
# python-service-ci.yml（片段）
- name: 运行 Python 测试
  run: |
    cd python-services
    pip install -e ".[dev]"
    pytest --cov=src --cov-fail-under=80

- name: 数据清洗
  run: |
    python scripts/clean_products.py \
      --input raw_data/products.csv \
      --output clean_data/products.csv

- name: 生成 embedding
  run: |
    python scripts/generate_embeddings.py \
      --input clean_data/products.csv \
      --output vector_data/embeddings.npy
```

---

## 总结

| 服务 | 技术栈 | 职责 |
|---|---|---|
| AI 搜索 | FastAPI + LangChain + asyncio | 搜索意图理解、多路召回、Rerank |
| 数据清洗 | Pandas + NumPy | 商品数据清洗、语料构建 |
| 异步任务 | asyncio + Celery | 批量 embedding、索引更新 |
| 部署 | Docker + CI/CD | 容器化、测试、持续发布 |

下一步：进入 [mini-blog/README.md](mini-blog/README.md) 亲手实现一个纯标准库的异步 API 服务。