# 05 · Embedding 向量化：BGE-M3 + SiliconFlow + OpenRouter

> 大模型理解语义的前提是先"看懂"文本。Embedding 模型将自然语言变成高维向量，让机器可以计算语义相似度——这是"智能搜索"区别于"关键词搜索"的技术基石。
>
> **对应项目：** `src/smart_search/config/tools.py`（`_build_siliconflow_embedding` / `_build_openrouter_embedding`）

---

## 一、基础概念

### 1.1 什么是 Embedding

**Embedding（嵌入）** 是将文本映射到高维向量空间的过程。语义相似的文本在向量空间中距离更近：

```
"5000元以下华为手机"  →  [0.12, -0.34, 0.78, ..., 0.01]  (1024维)
"华为Pura 70 4999元"  →  [0.11, -0.33, 0.79, ..., 0.02]  ← 距离近 ✓
"苹果14寸MacBook"    →  [-0.45, 0.67, -0.12, ..., 0.88]  ← 距离远 ✗
```

**关键认知：** Embedding 不是"搜索"，而是"理解"。搜索是计算向量距离后排序的结果。

### 1.2 为什么需要 Embedding

传统搜索策略（ES 倒排索引）只能匹配关键词：

```
查询: "5000元以下续航强的华为手机"
ES 匹配: 包含"5000"或"华为"或"手机" → 结果可能包含"华为充电器"（噪音）
Embedding 匹配: 语义理解 → "续航强的手机" → 匹配到"华为Pura 70 5000mAh电池"
```

**本质区别：** 关键词匹配看"字面"，向量匹配看"意思"。

### 1.3 BGE-M3 是什么

[BGE-M3](https://huggingface.co/BAAI/bge-m3) 是北京智源人工智能研究院（BAAI）开源的 Embedding 模型：

| 特性 | 说明 |
|------|------|
| **多语言 (Multi-Linguality)** | 支持 100+ 语言，中文效果极佳 |
| **多功能 (Multi-Functionality)** | 支持 Dense(稠密) + Sparse(稀疏) + Multi-Vector 三种检索 |
| **多粒度 (Multi-Granularity)** | 支持 8192 token 输入长度 |

**为什么选 BGE-M3？** 电商场景商品名/属性包含中英文混合，BGE-M3 的多语言能力是关键。

---

## 二、进阶机制

### 2.1 双 Embedding 供应商

项目支持两个 Embedding 供应商，通过 `EMBED_PROVIDER` 环境变量切换：

```python
class EmbeddingProvider(str, Enum):
    SILICONFLOW = "siliconflow"   # 默认，BGE-M3 模型
    OPENROUTER = "openrouter"     # 备选，免费模型
```

**SiliconFlow 配置：**

```python
class SiliconFlowEmbeddingConfig(BaseModel):
    base_url: str = "https://api.siliconflow.cn/v1"
    api_key: str = ""
    model: str = "BAAI/bge-m3"  # 中文 Embedding 标杆
```

**OpenRouter 配置：**

```python
class OpenRouterEmbeddingConfig(BaseModel):
    base_url: str = "https://openrouter.ai/api/v1"
    api_key: str = ""
    model: str = "liquid/lfm-2.5-embedding-350m:free"  # 免费模型
    http_referer: str = "https://mall-ai.example.com"
    openrouter_title: str = "Mall-AI Search"
```

### 2.2 工厂方法解析

```python
def _build_siliconflow_embedding(self, cfg: SiliconFlowEmbeddingConfig) -> OpenAIEmbeddings:
    return OpenAIEmbeddings(
        base_url=cfg.base_url,
        api_key=cfg.api_key,
        model=cfg.model,
    )

def _build_openrouter_embedding(self, cfg: OpenRouterEmbeddingConfig) -> OpenAIEmbeddings:
    return OpenAIEmbeddings(
        base_url=cfg.base_url,
        api_key=cfg.api_key,
        model=cfg.model,
        check_embedding_ctx_length=False,  # ← 关键：OpenRouter 嵌入接口只接受原始文本
        default_headers={
            "HTTP-Referer": cfg.http_referer,      # 用于 OpenRouter 排名页统计
            "X-OpenRouter-Title": cfg.openrouter_title,  # 标识应用名
        },
    )
```

**关键参数 `check_embedding_ctx_length=False`：**

LangChain 的 `OpenAIEmbeddings` 默认会把文本切分编码成 token id 数组再发送。但 OpenRouter 的 Embedding 接口只接受原始文本字符串，不接受整数 token id。关闭这个检查后，发送原始字符串，兼容 OpenRouter。

**`default_headers` 参数：**

从 `langchain-openai >= 1.6.0` 开始支持，用于向 OpenRouter 传递排名标识信息，无需手动构造 HTTP 客户端。

### 2.3 缓存机制

```python
def get_embeddings(self):
    if self._embedding_cache is not None:
        return self._embedding_cache
    # ... 构建并缓存
    self._embedding_cache = self._build_xxx_embedding(cfg)
    return self._embedding_cache
```

**为什么需要缓存？** `OpenAIEmbeddings` 实例内部持有 HTTP 连接池（httpx.AsyncClient / requests.Session），每次新建都会创建新连接。缓存后：
- 复用连接池，避免 TCP 握手开销
- 减少重复鉴权，API Key 只需鉴权一次
- 全局单例的 Embedding 实例

**对比 Spring Boot：**

```java
@Bean
@Scope("singleton")  // 默认单例，等价于缓存
public EmbeddingClient embeddingClient() {
    return new OpenAiEmbeddingClient(openAiApi, embeddingOptions);
}
```

### 2.4 Embedding 调用链路

```
调用方                              工厂类                    远程服务
  │                                 │                         │
  │  vector_store.similarity_search  │                         │
  │  (query, k=10)                  │                         │
  │  ──────────────────────────────→│                         │
  │                                 │  get_embeddings()        │
  │                                 │  ──────────────────→     │
  │                                 │    返回 cached instance  │
  │                                 │  ←──────────────────     │
  │                                 │  embed_query(query)      │
  │                                 │  ──────────────────→     │
  │                                 │    SiliconFlow API       │
  │                                 │    POST /v1/embeddings   │
  │                                 │    {input: "华为手机"}   │
  │                                 │  ←──────────────────     │
  │                                 │  返回 [0.12, -0.34, ...] │
  │  ← 返回 TOP-10 相似商品 ───────│                         │
```

---

## 三、项目现场

### 3.1 Embedding 在整个项目中的位置

```
┌──────────────────────────────────────────────────────────────────┐
│                     离线阶段（数据准备）                           │
│                                                                  │
│  MySQL 商品数据                                                  │
│    → 文本拼接 (sku_name + sku_attribute + brand_name + ...)      │
│    → RecursiveCharacterTextSplitter (256 tokens/chunk)          │
│    → OpenAIEmbeddings(client).embed_documents(chunks)  ← 本篇   │
│    → RedisVectorStore.add_documents(embeddings)                  │
│    → Redis Stack (HNSW 索引)                                    │
└──────────────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────────┐
│                     在线阶段（搜索查询）                           │
│                                                                  │
│  用户查询 "5000元以下续航强的华为手机"                             │
│    → OpenAIEmbeddings(client).embed_query(query)  ← 本篇        │
│    → RedisVectorStore.similarity_search(query_vector, k=10)     │
│    → 返回 TOP-10 相似商品                                       │
└──────────────────────────────────────────────────────────────────┘
```

### 3.2 为什么选择 SiliconFlow 作为默认 Embedding 供应商

| 对比项 | SiliconFlow | OpenRouter |
|--------|-------------|-----------|
| 模型水平 | BGE-M3 (SOTA 多语言) | liquid/lfm-2.5-embedding-350m (免费) |
| 中文效果 | 极佳（BGE 系列中文最强） | 一般（英文优化模型） |
| 价格 | 按量付费 | 免费但有速率限制 |
| 延迟 | 国内加速，低延迟 | 海外节点，相对较高 |
| 稳定性 | 高 | 中 |

**项目选择：** 默认 SiliconFlow（BGE-M3），因为电商场景中文为主，且 BGE-M3 在中文语义相似度任务上表现最好。OpenRouter 作为备选/降级方案。

### 3.3 向量维度说明

不同 Embedding 模型输出的向量维度不同：

| 模型 | 向量维度 | 单条存储成本 |
|------|---------|------------|
| BGE-M3 | 1024 | 4KB |
| text-embedding-3-small | 1536 | 6KB |
| text-embedding-3-large | 3072 | 12KB |
| lfm-2.5-embedding-350m | 768 | 3KB |

维度越高，语义区分度越强，但存储和检索成本也越高。BGE-M3 的 1024 维是质量和成本的平衡点。

---

## 四、Java 对照

### 4.1 Spring AI EmbeddingClient 对照

```java
// pom.xml
// <dependency>
//     <groupId>org.springframework.ai</groupId>
//     <artifactId>spring-ai-starter-model-openai</artifactId>
// </dependency>

@Configuration
public class EmbeddingConfig {

    @Bean
    @ConditionalOnProperty(name = "ai.embed-provider", havingValue = "siliconflow")
    public EmbeddingClient siliconflowEmbeddingClient(AppSettings settings) {
        var api = new OpenAiApi(
            settings.getSiliconflowEmbedding().getBaseUrl(),
            settings.getSiliconflowEmbedding().getApiKey()
        );
        var options = OpenAiEmbeddingOptions.builder()
            .withModel(settings.getSiliconflowEmbedding().getModel())
            .build();
        return new OpenAiEmbeddingClient(api, options);
    }

    @Bean
    @ConditionalOnProperty(name = "ai.embed-provider", havingValue = "openrouter")
    public EmbeddingClient openrouterEmbeddingClient(AppSettings settings) {
        var api = new OpenAiApi(
            settings.getOpenrouterEmbedding().getBaseUrl(),
            settings.getOpenrouterEmbedding().getApiKey()
        );
        var options = OpenAiEmbeddingOptions.builder()
            .withModel(settings.getOpenrouterEmbedding().getModel())
            .build();
        return new OpenAiEmbeddingClient(api, options);
    }
}

// 使用
@Service
public class VectorSearchService {
    private final EmbeddingClient embeddingClient;

    public List<Double> embedQuery(String query) {
        // 将查询转为向量
        EmbeddingResponse response = embeddingClient.embed(query);
        return response.getResult().getEmbedding();
    }
}
```

### 4.2 对照总结

| 维度 | Python (LangChain) | Java (Spring AI) |
|------|-------------------|-----------------|
| 核心类 | `OpenAIEmbeddings` | `OpenAiEmbeddingClient` |
| 方法 | `embed_query(text)` | `.embed(text)` |
| 批量 | `embed_documents(texts)` | `.embed(List)` |
| 配置 | `base_url + api_key + model` | `OpenAiApi + OpenAiEmbeddingOptions` |
| 生命周期 | 手写缓存 | `@Bean @Scope("singleton")` |

---

## 五、最小可复现示例

### 5.1 测试不同 Embedding 模型的语义相似度

```python
# test_embedding.py
# 需要: pip install langchain-openai
import os
from langchain_openai import OpenAIEmbeddings
from sklearn.metrics.pairwise import cosine_similarity
import numpy as np

def test_semantic_search():
    """验证 Embedding 能理解语义而非关键词"""

    # 创建 Embedding 实例（SiliconFlow BGE-M3）
    embeddings = OpenAIEmbeddings(
        base_url="https://api.siliconflow.cn/v1",
        api_key=os.getenv("SILICONFLOW_API_KEY"),
        model="BAAI/bge-m3",
    )

    # 商品库
    products = [
        "华为Pura 70 Ultra 512GB 星芒黑",
        "华为Mate 60 Pro 昆仑玻璃版",
        "苹果iPhone 16 Pro Max 256GB",
        "小米14 Ultra 徕卡光学",
        "华为充电器 66W 快充",
    ]

    # 查询
    query = "5000元以下华为手机"
    query_vec = np.array(embeddings.embed_query(query))
    product_vecs = np.array(embeddings.embed_documents(products))

    # 计算相似度
    similarities = cosine_similarity([query_vec], product_vecs)[0]
    ranked = sorted(zip(products, similarities), key=lambda x: x[1], reverse=True)

    print(f"查询: '{query}'")
    print("匹配结果（按相似度排序）：")
    for prod, score in ranked:
        print(f"  {score:.4f}  {prod}")

    # 验证：华为手机应该排在最前面
    assert "华为" in ranked[0][0], f"期望华为手机排第一，实际: {ranked[0][0]}"
```

### 5.2 验证 Embedding 的关键词无关性

```python
def test_embedding_vs_keyword():
    """验证：Embedding 理解语义，不依赖关键词匹配"""

    embeddings = OpenAIEmbeddings(
        base_url="https://api.siliconflow.cn/v1",
        api_key=os.getenv("SILICONFLOW_API_KEY"),
        model="BAAI/bge-m3",
    )

    query = "能打电话的智能设备"
    docs = [
        "智能手机支持通话功能",
        "座机电话固定电话",
        "今天的天气很好",
    ]

    query_vec = np.array(embeddings.embed_query(query))
    doc_vecs = np.array(embeddings.embed_documents(docs))
    similarities = cosine_similarity([query_vec], doc_vecs)[0]

    # "智能手机"应该有最高分，"天气"最低分
    assert similarities[0] > similarities[1], "语义上'智能手机'应更接近'智能设备'"
    assert similarities[0] > similarities[2], "'天气'与'智能设备'语义最远"
```

---

## 六、面试要点

### Q1: Embedding 在大模型应用中扮演什么角色？

**回答思路：** 它是大模型与外部知识库的桥梁。大模型的知识截止于训练数据，无法回答最新/私有信息。Embedding 将外部知识向量化后，在推理时召回相关内容注入上下文，让 LLM 能"基于事实"回答。没有 Embedding，RAG 就无法实现。

### Q2: 为什么选择 BGE-M3 而不是其他 Embedding 模型？

**回答思路：** 电商场景的几个关键需求：中文为主（BGE-M3 中文 SOTA）、中英文混合（BGE-M3 多语言）、商品信息长（支持 8192 token）、成本可控（1024 维平衡质量和存储）。

### Q3: check_embedding_ctx_length=False 是什么意思？

**回答思路：** LangChain 默认为了安全会检查上下文长度，方法是将文本编码为 token id 数组再发送。但 OpenRouter 的 Embedding 接口只接受原始文本，不接受 token id。关闭后发送原始字符串，兼容非 OpenAI 标准实现。

### Q4: 为什么 Embedding 需要缓存为单例？

**回答思路：** `OpenAIEmbeddings` 内部持有 HTTP 连接池，每次创建都会建立新连接。缓存为单例后：复用连接池避免 TCP 握手、减少重复鉴权、全局唯一实例降低资源消耗。

### Q5: Embedding 和 LLM 是什么关系？协同还是独立？

**回答思路：** 协同关系。Embedding 负责"理解"和"检索"（将文本转化为向量，计算相似度），LLM 负责"生成"和"推理"（基于检索到的上下文生成回答）。在 RAG 架构中，Embedding 是检索器，LLM 是生成器，两者缺一不可。

---

> **下一篇：** [06-VECTOR-STORE.md —— RedisVL 向量存储与检索：从 Redis Stack 到 HNSW 索引](./06-VECTOR-STORE.md)
>
> Embedding 产出的向量需要被高效存储和检索。看 RedisVL 如何实现毫秒级的语义搜索。