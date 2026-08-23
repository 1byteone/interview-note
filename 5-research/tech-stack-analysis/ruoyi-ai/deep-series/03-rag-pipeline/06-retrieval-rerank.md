# 检索+Rerank双阶段架构：从高召回到高精度的进化

> **深度系列 | 第6篇** | Level 3 生产级
>
> **对应项目：ruoyi-ai/ruoyi-chat 模块**
>
> 本篇目标：吃透 RAG 系统中"召回-精排"双阶段架构的全貌。从 Naive RAG 的局限出发，先后拆解向量检索的调参与踩坑、Cross-Encoder Rerank 的原理与调用、LangChain4j RetrievalAugmentor 的模块化机制，最后给出 ruoyi-ai 中"向量 + GraphRAG 双路检索"的生产级实现与排查方法论。

---

## 一、Naive RAG vs Advanced RAG 的差距在哪？

### 1.1 先搞清楚：什么算 Naive RAG？

第1篇《RAG从零搭建》里我们写过一个最简实现，它的在线流程只有四步：

```
用户提问 → Query向量化 → 向量库检索Top-K → 拼接上下文 + 问题 → LLM生成
```

这就是典型的 **Naive RAG（朴素RAG）**。它把"检索"当成一个黑盒：问什么就检索什么，检索到什么就给 LLM 什么。整个流程只有三个固定动作，没有任何中间环节去"优化"这条链路。

它的局限性在真实场景中暴露得非常彻底：

| 局限 | 具体表现 | 后果 |
|------|----------|------|
| 固定检索 | 问 1 次就检 1 次，Query 不做任何改写 | 问题表述不精确时，检索结果直接跑偏 |
| 无查询优化 | 忽略对话历史，追问"那它呢？"这种指代性问题检索为空 | 多轮对话场景基本不可用 |
| 无多路召回 | 只有向量一路，实体关系类问题（"A和B什么关系"）答不了 | 召回率天花板低 |
| 无后处理 | Top-K 直接进 Prompt，中间混入的噪音片段没人管 | LLM 被噪音带偏，答案质量方差大 |
| 上下文无控 | 所有片段都堆进 Prompt，不计算、不去重、不排序 | Token 浪费 + 关键信息被淹没 |
| 无质量兜底 | 相似度低的"凑数"结果也进上下文 | 幻觉率居高不下 |

一句话总结：**Naive RAG 的问题不在检索本身，而在于链路是"死"的——没有查询增强、没有多路协同、没有精排兜底。**

### 1.2 Advanced RAG 到底"高级"在哪？

Advanced RAG 的本质，是在"检索"这个环节上做**一连串的模块化增强**。它把 Naive RAG 的一个黑盒，拆成了五个可插拔的组件：

```
用户提问
   │
   ▼
[QueryTransformer]   查询改写/扩展：消解指代、多角度扩展、Step-back
   │
   ▼
[QueryRouter]        查询路由：按意图分发到不同检索器
   │
   ▼
[ContentRetriever×N] 多路召回：向量检索 + 知识图谱 + 关键词，各自返回排序列表
   │
   ▼
[ContentAggregator]  结果融合：去重、归一化、RRF 或 Rerank 精排
   │
   ▼
[ContentInjector]    上下文注入：构建最优的 Prompt 上下文（带引用、带来源）
   │
   ▼
LLM 生成
```

每一个组件只做一件事，彼此解耦、可单独替换，这就是 LangChain4j `RetrievalAugmentor` 的模块化思路——后面第四部分会展开讲。

### 1.3 一张表看透差距

| 维度 | Naive RAG | Advanced RAG |
|------|-----------|--------------|
| 查询处理 | 原样检索 | 指代消解、Step-back、Multi-Query 多角度改写 |
| 召回路数 | 单路向量 | 向量 + 知识图谱 + BM25 多路并行 |
| 召回数量 | Top-5 直接返回 | Top-50 ~ Top-100 广撒网，追求高召回 |
| 排序机制 | 余弦相似度（Bi-Encoder 近似） | Cross-Encoder Rerank / RRF 融合精排 |
| 上下文构建 | 简单拼接 | 去重、截断、按分数/来源组织、引用溯源 |
| 多轮对话 | 不支持 | CompressingQueryTransformer 压缩指代 |r
| 效果确定性 | "碰运气"，方差大 | 链路可控，每个环节可观测、可调优 |
| 排障难度 | 无从下手 | 每个组件可单独打点、单独评测 |

> 关键认知：**"高召回 + 高精度"不是技术炫技，而是两条不可兼得的指标被拆到两个阶段分别逼近。** 向量检索保证"别漏掉"，Rerank 保证"别选错"。

---

## 二、第一阶段：向量检索（高召回）

### 2.1 EmbeddingStoreContentRetriever 配置详解

向量检索是召回阶段的核心。在 LangChain4j 里，我们通常用 `EmbeddingStoreContentRetriever` 把它接入 `AiServices`：

```java
// 构建内容检索器：连接向量库 + Embedding 模型
ContentRetriever contentRetriever = EmbeddingStoreContentRetriever.builder()
        .embeddingStore(embeddingStore)      // 向量库：Milvus / Weaviate / Qdrant
        .embeddingModel(embeddingModel)      // Embedding 模型：必须在索引文档时用同一个
        .maxResults(5)                       // 召回条数：配置为 5（简单场景）或 50（配合 Rerank）
        .minScore(0.75)                      // 最低相似度阈值：过滤低分噪音
        .build();
```

**`EmbeddingStoreContentRetriever` 的内部工作原理**（理解它才能调好它）：

```
1. 用户提问文本 incoming
2. 调用 embeddingModel.embed(question) 得到查询向量
3. 调用 embeddingStore.search(searchRequest) 执行 ANN 搜索（HNSW 索引，毫秒级）
4. minScore 过滤：相似度低于阈值的候选被丢弃
5. maxResults 截断：按相似度降序取前 N 条
6. 每条候选包装为 Content，返回给上层
```

如果你不想走 `AiServices` 的声明式封装，也可以直接用底层 API 手动检索，这在做多路融合或自定义逻辑时更灵活：

```java
// 手动向量检索的底层 API 示例
EmbeddingSearchResult<TextSegment> result = embeddingStore.search(
        EmbeddingSearchRequest.builder()
                .queryEmbedding(questionEmbedding)   // 查询向量：由 Embedding 模型编码用户问题得到
                .maxResults(3)                        // 召回调数
                .build());

// 遍历结果，取出文本片段和相似度分数
result.matches().forEach(match -> {
    TextSegment segment = match.embedded();    // 命中的文本片段
    double score = match.score();              // 余弦相似度（已归一化为 0~1 区间）
});
```

### 2.2 minScore 过滤：为什么要过滤低分结果？

`minScore` 是一条"质量红线"。它回答一个问题：**相似度低到多少，宁可空着也不给 LLM？**

噪音片段进上下文的代价远比"少召回一条"更贵：

- **污染注意力**：LLM 会把不相关的片段也"读进去"，被带偏的概率远高于人类
- **浪费 Token**：4K/8K 上下文里的每一千 token 都是钱，噪音片段挤占了真正有用内容的空间
- **置信度误导**：低分结果往往不是"相关但分低"，而是"根本不相关"，比如数字恰好接近

**调优经验（ruoyi-ai 生产总结）：**

| minScore 取值 | 效果 | 适用场景 |
|---------------|------|----------|
| 0.7 ~ 0.8 | 严格，宁缺毋滥 | 法律、合同等高风险问答，宁可答不出不能答错 |
| 0.5 ~ 0.7 | 均衡 | 一般企业知识库问答（ruoyi-ai 默认 0.5） |
| 0.3 ~ 0.5 | 宽松 | 配合强 Rerank，让 Rerank 去兜底过滤 |
| 不设 | 全收 | 只在做评测/调参对比实验时用 |

> 注意：**minScore 不解决"排序不准"的问题**——它只砍掉低于红线的，砍不掉"排名靠前但相关度一般"的。真正的排序优化要靠 Rerank。所以生产配置一般是：`minScore` 放宽松（0.4~0.5）+ `maxResults` 放大（50）+ Rerank 精排，让每一个组件各司其职。

### 2.3 maxResults 控制：召回数量的选择艺术

`maxResults` 是召回广度与上下文质量的博弈：

- **太少（如 3~5）**：漏召回风险大。一个主题分散在多段文档里时，只取 Top-5 可能挤掉了后面的关键段落。且没有给 Rerank 留出"筛选空间"——5 进 5 出等于没精排。
- **太多（如 1000）**：噪音爆炸。多数向量库的相关分数长尾分布严重，Top-1000 里 95% 都是无关内容，Rerank 也要花大量时间逐条打分。

**ruoyi-ai 的实践取值链路：**

```
向量库 Top-50 召回 → 融合图谱结果 → Rerank 精排 Top-5 → 注入 LLM
```

这个"**50 进 5 出**"的比例是有依据的：

1. **召回率拐点**：线上评测显示，候选数从 10 增至 50 时，Top-5 命中率提升约 15%；从 50 增至 100 时提升不足 2%——50 是性价比拐点
2. **Rerank 成本**：bge-reranker-v2-m3 对单文档打分约 30~80ms，50 条约 1.5~4 秒，处于可接受区间；100 条就要 3~8 秒，首字延迟不可接受
3. **上下文预算**：Top-5 片段 × 每片段 300~500 token ≈ 1500~2500 token，给系统提示和回答留足余量

### 2.4 检索参数调优方法论（不只是拍脑袋）

调参不是玄学，ruoyi-ai 的调参流程是一个**闭环实验**：

```
1. 构建评测集：50~100 个 (问题, 标准答案, 相关文档ID) 三元组
2. 固定 minScore / maxResults 之外的变量（Embedding 模型、切分策略不换）
3. 跑 Grid Search：minScore ∈ {0.3,0.4,0.5,0.6,0.7} × maxResults ∈ {10,20,50,100}
4. 计算每组的 Recall@N 和 Precision@N
5. 选 Recall 拐点处的参数组合，再微调
6. 上线后用真实 Query 流量做 A/B 观测
```

**常见翻车点及对策：**

| 现象 | 根因 | 对策 |
|------|------|------|
| 检索结果离题万里 | Query 表述不精确 | 上 QueryTransformer 改写，或换个更强的 Embedding |
| 分数普遍偏低（<0.4） | Embedding 模型与文档语言不匹配 | 中文文档务必用 BGE-M3/通义等中文优化模型 |
| 召回稳定但排秩不准 | Bi-Encoder 表征能力有限 | 加 Rerank 精排，而不是继续调 maxResults |
| 同主题片段扎堆 | 文档切分太细导致冗余 | 调整切分粒度 + 在聚合阶段做去重 |

---

## 三、第二阶段：Rerank 精排（高精度）

### 3.1 Cross-Encoder vs Bi-Encoder：注意力机制差异

这是 RAG 面试里最常被追问的底层原理。两者都是把"句子"编码成向量，但**信息交互方式完全不同**。

#### Bi-Encoder（双塔）：各自编码，最后比一下

```
        Query 文本                       文档文本
          │                               │
      [BERT 模型]                    [BERT 模型]     ← 两个模型共享或不共享参数
          │                               │
    Query 向量 [cls]                 文档向量 [cls]
          │                               │
          └───────── 余弦相似度 / 点积 ─────┘
                     得到相关性分数
```

关键点：**Query 和文档在编码阶段完全没有交互**。各自过一遍模型得到句向量（如 768 维 [CLS] 输出），再用余弦相似度衡量。优点是文档向量可以**离线预计算**、百万级文档只算一次，在线只需编码 Query 然后做 ANN 搜索——这就是为什么向量检索能做到毫秒级。缺点很明显：**"你好吗？"和"你身体还好吗？"在双塔眼里，句向量碰撞到的语义细节有限，词序、否定、数字等敏感信息容易丢**。

#### Cross-Encoder（交互式）：拼一起，全 Token 交互注意力

```
       "[CLS] Query 文本 [SEP] 文档文本 [SEP]"
                     │
              [BERT 模型]        ← 同一模型，一次前向传播
                     │
         [CLS] 表示向量（融合了双方全部 token 的信息）
                     │
              线性层 → sigmoid → 相关性分数（0~1）
```

关键点：**Query 和文档拼接成一个序列输入模型，Transformer 的自注意力机制让 Query 的每个 token 都能"看到"文档的每个 token**。在自注意力层里，第 i 个 token 的表示会加权融合序列中所有 token 的信息——所以 Cross-Encoder 能捕捉精确的语义对齐（"不是"的否定、"提高了 3 倍"的数字关系）。代价是：**每个 (Query, 文档) 对都要跑一次完整前向传播，且无法预计算**，只能在线逐条打分，所以只适合对少量候选精排。

#### 一张表对比

| 维度 | Bi-Encoder（召回阶段） | Cross-Encoder（精排阶段） |
|------|------------------------|---------------------------|
| 输入方式 | Query、文档分别编码 | 拼接为一个序列，联合编码 |
| 注意力范围 | 各自内部 | **交叉注意力，全 token 互见** |
| 是否可离线 | 文档向量可预计算 | 否，必须在线逐对打分 |
| 单条耗时 | 文档已算好，仅算 Query：~2ms | 50~200ms（取决于模型与长度） |
| 精度 | 中（近似匹配） | 高（精确语义对齐） |
| 可处理量 | 百万级（ANN 索引） | Top-50 ~ Top-100 候选 |
| 适用 | 第一阶段宽召回 | 第二阶段精排 |

这就是"宽召回 + 精排"的根本原因：**用 Bi-Encoder 的吞吐做广度，用 Cross-Encoder 的精度做深度。**

### 3.2 Rerank API 调用：三家服务商实现

ruoyi-ai 把三家 Rerank 服务商抽象成统一的 `RerankService` 接口，业务侧只依赖接口：

```java
/**
 * Rerank 服务统一接口 —— 三家服务商（阿里百炼/硅基流动/智谱）各自实现
 * 业务代码只面向接口编程，切换服务商零改动
 */
public interface RerankService {

    /**
     * 对候选文档重排序，返回精排后的 Top-N
     *
     * @param query      原始用户提问
     * @param candidates 召回阶段的候选文档（向量 + 图谱融合结果）
     * @param topN       返回的精排数量（如 5）
     * @return 精排后的文档列表（按相关性降序）
     */
    List<ScoredDocument> rerank(String query, List<ScoredDocument> candidates, int topN);
}
```

#### 实现一：SiliconFlow（硅基流动）—— 走 OpenAI 兼容 /rerank 接口

```java
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * SiliconFlow Rerank 实现 —— 使用 BAAI/bge-reranker-v2-m3
 * SiliconFlow 以 API 方式托管开源模型，免自部署 GPU
 */
@Component
public class SiliconFlowRerankService implements RerankService {

    // 线上建议注入 HttpClient 连接池，而不是每次 new RestTemplate()
    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public List<ScoredDocument> rerank(String query, List<ScoredDocument> candidates, int topN) {
        // 1. 如果候选为空，直接返回空列表，避免无效调用
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }

        // 2. 组装请求体：model + query + documents 列表
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", "BAAI/bge-reranker-v2-m3");   // 精排模型：多语言，中文效果好
        body.put("query", query);                        // 原始提问
        body.put("documents", candidates.stream()
                .map(ScoredDocument::getText)            // 只传文本，按索引对应
                .toList());
        body.put("top_n", topN);                         // 只取前 topN 个结果

        // 3. 调用 SiliconFlow Rerank API
        String url = "https://api.siliconflow.cn/v1/rerank";
        RerankApiResponse response = restTemplate.postForObject(
                url, body, RerankApiResponse.class);

        // 4. 将 API 返回结果映射回 ScoredDocument
        //    API 返回结构：{"results":[{"index":3,"relevance_score":0.95},...]}
        return response.results().stream()
                .filter(r -> r.index() < candidates.size())   // 防御：index 越界保护
                .map(r -> ScoredDocument.of(
                        candidates.get(r.index()).getText(),  // 取候选原文
                        r.relevanceScore()))                  // 用 Rerank 得分覆盖原分
                .toList();
    }
}
```

#### 实现二：AliBaiLian（阿里云百炼）—— 通用文本排序模型

```java
/**
 * 阿里云百炼 Rerank 实现 —— 使用 gte-rerank 模型
 * 百炼平台的文本排序（Rerank）API，兼容 OpenSearch Rerank 协议
 */
@Component
public class AliBaiLianRerankService implements RerankService {

    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public List<ScoredDocument> rerank(String query, List<ScoredDocument> candidates, int topN) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }

        // 1. 百炼 Rerank API：POST https://dashscope.aliyuncs.com/api/v1/services/rerank/text-rerank/text-rerank
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("model", "gte-rerank");                 // 百炼上的排序模型
        params.put("query", query);
        params.put("return_documents", false);             // 不需要回传文档原文，省流量
        params.put("top_n", topN);
        params.put("input", candidates.stream()
                .map(ScoredDocument::getText)              // 候选文档列表
                .toList());

        // 2. 走 OpenAI 兼容网关：dashscope 支持 OpenAI 风格的 headers
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(System.getenv("DASHSCOPE_API_KEY"));   // 百炼 Api-Key

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(params, headers);
        RerankApiResponse response = restTemplate.postForObject(
                "https://dashscope.aliyuncs.com/api/v1/services/rerank/text-rerank/text-rerank",
                request, RerankApiResponse.class);

        // 3. 结果映射（百炼返回顺序即按分排序，仍按 index 映射回原文）
        return response.results().stream()
                .filter(r -> r.index() < candidates.size())
                .map(r -> ScoredDocument.of(
                        candidates.get(r.index()).getText(),
                        r.relevanceScore()))
                .toList();
    }
}
```

#### 实现三：ZhipuAI（智谱）—— 重排序接口

```java
/**
 * 智谱 AI Rerank 实现 —— 使用 rerank 模型
 * 智谱开放平台提供 rerank 接口，对候选文本按与 Query 的相关性重新打分排序
 */
@Component
public class ZhipuAIRerankService implements RerankService {

    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public List<ScoredDocument> rerank(String query, List<ScoredDocument> candidates, int topN) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }

        // 1. 智谱 Rerank API：POST https://open.bigmodel.cn/api/paas/v4/rerank
        //    使用 JWT Bearer Token 鉴权（组织/API Key）
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", "rerank");                       // 智谱重排序模型
        body.put("query", query);
        body.put("documents", candidates.stream()
                .map(ScoredDocument::getText)
                .toList());
        body.put("top_n", topN);
        body.put("return_documents", false);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(System.getenv("ZHIPUAI_API_KEY"));   // 智谱 API Key

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        RerankApiResponse response = restTemplate.postForObject(
                "https://open.bigmodel.cn/api/paas/v4/rerank",
                request, RerankApiResponse.class);

        // 2. 结果映射
        return response.results().stream()
                .filter(r -> r.index() < candidates.size())
                .map(r -> ScoredDocument.of(
                        candidates.get(r.index()).getText(),
                        r.relevanceScore()))
                .toList();
    }
}
```

**三家服务商选型建议：**

| 服务商 | 模型 | 优势 | 注意点 |
|--------|------|------|--------|
| SiliconFlow | BAAI/bge-reranker-v2-m3 | 多语言强、中文效果标杆、可走 OpenAI 兼容 | 付费 API，需评估成本 |
| AliBaiLian | gte-rerank | 与通义生态集成好、稳定性高 | Rerank 端点与 Chat 端点不同 |
| ZhipuAI | rerank | 国内合规、接入简单 | 模型参数（如 top_k）与别家略有差异 |

### 3.3 候选集大小选择：Top-50 召回 → Top-5 精排的依据

这个比例是生产实践的结晶，背后有三个权衡维度：

**1. 召回率曲线（Recall vs Candidate Size）**

实测（ruoyi-ai 企业知识库，约 10 万块切片）形态大致如下：

| 候选数 | Recall@5 相对基线 | Rerank 耗时（单 Query） |
|--------|-------------------|------------------------|
| 10 | +0% | 0.3~0.8s |
| 20 | +8% | 0.6~1.6s |
| **50** | **+15%** | **1.5~4s** |
| 100 | +17% | 3~8s |
| 500 | +18% | 15~40s（不可接受） |

50 之后召回率提升趋缓、延迟却线性飙升，**50 是"召回收益 / 延迟成本"的拐点**。

**2. 数据集规模是否影响？**

- 知识库 < 1 万块：候选 20~30 足够（本来就没什么可漏）
- 知识库 10 万~百万块：50~100 更稳妥
- 知识库 > 百万块：核心靠 ANN 索引质量，Rerank 前建议先用过滤条件（元数据、分类）粗筛一轮，避免全量喂给 Rerank

**3. 一个反直觉的细节：为什么 Top-N 不取 1？**

精排后只留 1 条虽然最"精准"，但单条片段往往信息不全（答案分散在多段里），也失去了交叉验证。Top-3~5 的组合既覆盖信息完整性，又给 LLM 留了"从多段中综合"的空间。生产上取 **5** 是综合信息完整度与 Token 成本的最优解。

### 3.4 Rerank 模型选型：BGE-Reranker 系列横评

| 模型 | 特点 | 适用场景 |
|------|------|----------|
| BAAI/bge-reranker-v2-m3 | 多语言（含中文）、支持 100+ 语言，1024 token 上下文 | **ruoyi-ai 主选**，中英混合知识库首选 |
| BAAI/bge-reranker-base | 轻量（英文为主），延迟低 | 纯英文、对延迟敏感的场景 |
| BAAI/bge-reranker-large | 精度更高、更慢 | 强精度需求（法律/医疗） |
| gte-rerank（阿里） | 与通义生态配套 | 已深度使用阿里云生态的团队 |
| jina-reranker-v2-base-multilingual | 多语种、长文本支持好 | 超长片段（>512 token）场景 |

> **模型一致性原则（避坑重点）：** 尽量让 Embedding 与 Rerank 同源。ruoyi-ai 的默认组合是 **BGE-M3 做 Embedding + BGE-Reranker-v2-m3 做 Rerank**。如果 Embedding 来自 OpenAI、Rerank 用 BGE，两个模型的"相关性判断标准"不一致，Rerank 可能把向量检索排在前面的好结果打下去——出现"越精排越差"的诡异现象时，优先检查这一点。

---

## 四、高级 RAG：RetrievalAugmentor 组件解剖

LangChain4j 把上面讲的所有环节都抽象成了 `RetrievalAugmentor`。它才是 "Advanced RAG" 在框架层面的落地。

### 4.1 DefaultRetrievalAugmentor 各组件总览

```java
// 高级 RAG 组装示例：把各组件插进 RetrievalAugmentor
RetrievalAugmentor retrievalAugmentor = DefaultRetrievalAugmentor.builder()
        .queryTransformer(queryTransformer)   // 查询改写（可选）
        .queryRouter(queryRouter)             // 查询路由（可多检索器）
        .contentAggregator(contentAggregator) // 结果融合（去重/RRF/Rerank）
        .contentInjector(contentInjector)     // 上下文注入（可选）
        .executor(executor)                   // 并发执行器（可选）
        .build();

// 通过 AiServices 接入，替代笨重的 .contentRetriever(...)
Assistant assistant = AiServices.builder(Assistant.class)
        .chatLanguageModel(chatModel)              // 生成模型
        .retrievalAugmentor(retrievalAugmentor)    // 高级检索增强器
        .build();
```

完整调用链（理解它，面试就能画出 LangChain4j 的 RAG 时序图）：

```
1. RetrievalAugmentor.augment(userMessage, chatMemory)
2.   ├─ QueryTransformer.transform(query)
3.   │    把 1 个 Query 变成 1 个或多个 Query（改写/压缩/多路扩展）
4.   ├─ QueryRouter.route(queries)
5.   │    把每个 Query 路由到 1 个或多个 ContentRetriever
6.   ├─ 各 ContentRetriever.retrieve(queries)  → 每个 retriever 返回一个排序列表
7.   ├─ ContentAggregator.aggregate(列表集合)
8.   │    多路多排序列表 → 去重 + 融合 + 精排 → 一条排序列表
9.   └─ ContentInjector.inject(originalMessage, contents)
10.       按策略把内容拼装进 UserMessage → 返回给 ChatModel
```

### 4.2 QueryTransformer：查询改写与扩展

**职责**：接收原始 Query，返回一个或多个改写后的 Query。LangChain4j 内置三类实现：

| 实现 | 策略 | 场景 |
|------|------|------|
| `DefaultQueryTransformer` | 原样返回，不处理 | 默认行为、或者你自己重写增强逻辑 |
| `CompressingQueryTransformer` | 结合对话历史压缩出"独立完整"的查询 | 多轮对话追问的指代消解 |
| `RepeatingQueryTransformer` | 配套 prompt-repetition 插件重复查询 | 查询阶段增强检索信号 |

**CompressingQueryTransformer 是必考核心**，它的逻辑值得逐行走读：

```java
// 构造：只需要一个 ChatModel（内部用 LLM 改写查询）
CompressingQueryTransformer transformer = new CompressingQueryTransformer(chatModel);

// 原理：把 [对话历史 + 当前问题] 交给 LLM，要求输出一条独立完整、适合检索的查询
// 内部 Prompt 本质是：
//   "Read and understand the conversation... Reformulate this query into a clear,
//    concise, and self-contained format suitable for information retrieval."
```

它解决的真实问题：用户先问"RAG 的优点是什么？"，再追问"那它的缺点呢？"——第二条如果不结合历史，"它"指代不明，检索必然跑偏。压缩后变成"RAG 的缺点是什么"，检索质量立刻恢复。

**Step-back（退一步提问）策略**：虽然 LangChain4j 没有内置同名实现，但它是生产中最常用的衍生策略，实现思路是自定义 Transformer：

```java
/**
 * Step-back 查询改写：当高层级概念问题检索不到时，
 * 退一步生成更抽象的问题再次检索，与原文共同召回
 */
public class StepBackQueryTransformer implements QueryTransformer {

    // 注入一个 ChatModel 用于生成抽象问题
    private final ChatLanguageModel chatModel;

    @Override
    public Collection<Query> transform(Query query) {
        // 1. 提示 LLM 生成"退一步"的抽象问题
        //    例：原文"bge-m3 相比 m3e 在中文长文档上的表现"
        //    step-back："中文 Embedding 模型对比评测"
        Prompt prompt = PromptTemplate.from(
                "你是资深文献检索专家。请把用户问题抽象为更高层次的原理性问题，"
                        + "仅输出改写后的问题：\n{{query}}")
                .apply(Map.of("query", query.text()));

        String stepBackQuestion = chatModel.chat(prompt.text());
        // 2. 返回两条查询：原文 + Step-back 问题（扩大召回面，让 Rerank 裁决）
        return List.of(query, Query.from(stepBackQuestion));
    }
}
```

> **为什么 Multi-Query / Step-back 有效？** 因为它们把"一次检索"变成"多次互补检索"，用 Rerank 统一裁决。代价是检索次数成倍增加、延迟上升，所以只对**检索质量差的 Query 子集**启用，不要让所有流量都走改写。

### 4.3 ContentAggregator：多路结果融合

**职责**：把多个 Retriever、多个 Query 产生的多个排序列表，融合成一条有序列表。LangChain4j 提供两种开箱实现：

**`DefaultContentAggregator`：两阶段 RRF（Reciprocal Rank Fusion，倒数排名融合）**

```java
ContentAggregator defaultAggregator = new DefaultContentAggregator();
```

RRF 的原理（面试手写级别）：

```
对每个候选文档 d，计算 RRF 得分：
    RRFscore(d) = Σ 1 / (k + rank_i(d))

其中：
  - rank_i(d) 是 d 在第 i 个排序列表中的名次（从 1 开始）
  - k 是平滑常数（通常取 60）
```

**RRF 的巧妙之处**：它只看"名次"不看"分数"。因为多路检索的分数体系完全不可比（向量检索是 0~1 余弦值、图谱检索是路径权重、BM25 是词频分），直接加权重比分数是错的；而名次是相对顺序，天然可比。一个文档在 3 路里都排第 2（贡献 3×1/62），会赢过在某一路排第 1 的文档（贡献 1/61）——这就是"多路共识优于单路自信"的数学表达。

**`ReRankingContentAggregator`：ScoringModel 精排**

```java
// Rerank 阶段在框架层的官方用法：直接作为 ContentAggregator 注册
ContentAggregator rerankAggregator = ReRankingContentAggregator.builder()
        .scoringModel(scoringModel)      // 任意实现了 ScoringModel 接口的重排序模型
        .build();

// 组装进 RetrievalAugmentor
RetrievalAugmentor augmentor = DefaultRetrievalAugmentor.builder()
        .contentRetriever(contentRetriever)
        .contentAggregator(rerankAggregator)   // 用 Rerank 代替朴素融合
        .build();
```

`ReRankingContentAggregator` 内部做的事：把 RRF 融合后的候选列表，逐条 `(query, doc)` 喂给 ScoringModel（即 Cross-Encoder），按返回分数重排、截断。**这正好覆盖 ruoyi-ai 的"Top-50 召回 → Top-5 精排"设计**——只是 ruoyi-ai 因为要用自家三家 Rerank 服务商（不走 LangChain4j 内置积分模型），把这一层封装成了自有的 `RerankService`。

**融合策略对比：**

| 策略 | 机制 | 优点 | 缺点 |
|------|------|------|------|
| 分数加权 | 归一化后加权重比分数 | 简单直观 | 跨路分数不可比，需仔细归一化 |
| RRF | 按名次倒数求和 | 无需归一化，鲁棒 | 丢失分数信息，对"名次密集"场景不敏感 |
| Rerank 精排 | Cross-Encoder 逐对打分 | 精度最高 | 延迟高、成本高，只能处理小候选集 |

生产上最稳的组合：**RRF 做粗融合（几十条） → Rerank 做精排（取 5 条）**，两层各司其职。

### 4.4 ContentInjector：上下文注入策略

**职责**：把排好序的 Content 注入 UserMessage，拼装成发给 LLM 的最终 Prompt。默认实现是简单的 `DefaultContentInjector`，把检索内容直接附加到用户消息后面。

生产级上下文构建的三个要点（ruoyi-ai 经验）：

1. **带序号与来源**：每条内容标注 `【参考1】（来源：xxx.pdf 第3页）`，LLM 回答时可以做引用溯源，也符合企业内部审计要求
2. **顺序即权重**：精排第一的内容放最前——大模型对开头内容注意更集中（注意力的位置偏差）
3. **长度阈值**：检索内容超过上下文预算时，只保留高分段、裁剪低分段，避免"塞了 20 段但全被截断"的尴尬

自定义 ContentInjector 的骨架：

```java
/**
 * 自定义上下文注入器：构建"带引用标注"的最优上下文
 */
public class TraceableContentInjector implements ContentInjector {

    @Override
    public UserMessage inject(UserMessage userMessage, List<Content> contents) {
        // 1. 为空时不注入，避免污染原始问题
        if (contents == null || contents.isEmpty()) {
            return userMessage;
        }

        // 2. 构建带引用序号的上下文块
        StringBuilder sb = new StringBuilder("请基于以下参考资料回答，并标注引用编号：\n");
        for (int i = 0; i < contents.size(); i++) {
            TextSegment segment = (TextSegment) contents.get(i).textSegment();
            // 从 metadata 中取来源信息（切分入库时写入）
            String source = String.valueOf(
                    segment.metadata().getString("source"));   // 如 "手册.pdf"
            sb.append("【参考").append(i + 1).append("】来源:").append(source)
                    .append("\n").append(segment.text()).append("\n\n");
        }
        sb.append("用户问题：").append(userMessage.singleText());

        // 3. 返回注入后的消息
        return UserMessage.from(sb.toString());
    }
}
```

> **完整组装示例（面试极可能要求手写）：**
> ```java
> RetrievalAugmentor augmentor = DefaultRetrievalAugmentor.builder()
>         .queryTransformer(new CompressingQueryTransformer(chatModel)) // 多轮指代消解
>         .contentRetriever(contentRetriever)                            // 向量检索器
>         .contentAggregator(rerankAggregator)                           // Rerank 精排
>         .contentInjector(new TraceableContentInjector())               // 带溯源注入
>         .build();
> ```

---

## 五、代码示例：RagRetrievalService 完整实现

ruoyi-ai 的检索服务把"向量检索 → 图谱增强 → 多路融合 → Rerank → 上下文构建"串成一条生产线。下面给出**完整可运行的生产级版本**，含异常处理与降级逻辑：

```java
package com.ruoyi.ai.rag.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * RAG 检索服务 —— 双阶段（召回+精排）检索流水线
 *
 * 数据流：
 *   Query
 *    → ① 向量检索 Top-50（Bi-Encoder 宽召回）
 *    → ② GraphRAG 图谱检索（实体关系增强，失败可降级）
 *    → ③ 多路融合（RRF 粗融合 + 去重）
 *    → ④ Rerank 精排 Top-5（Cross-Encoder 精排）
 *    → ⑤ 构建带引用的上下文
 */
@Slf4j
@Service
public class RagRetrievalService {

    // 向量库检索器：负责第一阶段召回
    private final EmbeddingStoreContentRetriever vectorRetriever;
    // 知识图谱检索器：可降级的可选之路
    private final Optional<Neo4jGraphRagService> graphRagService;
    // Rerank 服务：按 provider 选实现（AliBaiLian / SiliconFlow / ZhipuAI）
    private final RerankService rerankService;

    // 第一、二阶段的召回/精排数量配置
    private static final int TOP_K_RECALL = 50;   // 召回阶段：Top-50 广撒网
    private static final int TOP_N_RERANK = 5;    // 精排阶段：Top-5 高精度
    private static final double MIN_SCORE = 0.5;  // 召回最低相似度（宽松，交给 Rerank 兜底）

    // 并行执行向量检索与图谱检索的线程池（两条路互不阻塞）
    private final ExecutorService retrievalExecutor = Executors.newFixedThreadPool(4);

    public RagRetrievalService(EmbeddingStoreContentRetriever vectorRetriever,
                               Optional<Neo4jGraphRagService> graphRagService,
                               RerankService rerankService) {
        this.vectorRetriever = vectorRetriever;
        this.graphRagService = graphRagService;
        this.rerankService = rerankService;
    }

    /**
     * 检索主流程入口
     *
     * @param question 用户提问
     * @return RagContext（精排后的文档 + 构建好的上下文）
     */
    public RagContext retrieve(String question) {
        // ============ 第一步：向量召回（第一阶段，宽召回） ============
        List<RetrievedDoc> vectorDocs = vectorRetrieve(question);
        log.info("向量召回完成，命中 {} 条", vectorDocs.size());

        // ============ 第二步：GraphRAG 知识图谱增强（并行、可降级） ============
        List<RetrievedDoc> graphDocs = graphRetrieve(question);

        // ============ 第三步：多路融合（RRF + 去重） ============
        List<RetrievedDoc> fused = fuseResults(vectorDocs, graphDocs);
        log.info("多路融合完成，融合后 {} 条", fused.size());

        // ============ 第四步：Rerank 精排（第二阶段，高精度） ============
        List<RetrievedDoc> reranked = rerank(question, fused);
        log.info("Rerank 精排完成，输出 Top-{}", reranked.size());

        // ============ 第五步：构建上下文 ============
        // 没有检索到任何内容时，直接返回空上下文（Prompt 端会触碰"无参考"提示）
        String context = reranked.isEmpty() ? "" : buildContext(reranked);

        return RagContext.builder()
                .documents(reranked)      // 精排后的文档（供前端展示引用）
                .context(context)         // 注入 LLM 的上下文文本
                .build();
    }

    /**
     * 向量检索：使用 EmbeddingStoreContentRetriever（内部自动完成
     * Query Embedding → ANN 搜索 → minScore 过滤 → maxResults 截断）
     */
    private List<RetrievedDoc> vectorRetrieve(String question) {
        try {
            // 调用检索器的底层检索方法（langchain4j 1.x 标准 API）
            List<Content> contents = vectorRetriever.retrieve(
                    Query.from(question)).content();

            // Content → 自定义包装对象（携带文本 + 来源元数据 + 相似度）
            return contents.stream()
                    .map(content -> {
                        TextSegment seg = content.textSegment();
                        // 从片段元数据中取出文档来源，构建可溯源文档对象
                        return RetrievedDoc.of(
                                seg.text(),                                  // 正文
                                seg.metadata().getString("source"),         // 来源文件
                                content.score() == null ? 0.0 : content.score()); // 相似度
                    })
                    .toList();
        } catch (Exception e) {
            // 向量库故障时降级：记录告警，返回空列表（避免整个接口 500）
            log.error("向量检索失败，已降级跳过此路，原因: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * GraphRAG 图谱检索：从问题中抽取实体，在 Neo4j 中查找关联知识
     * 使用 CompletableFuture 与向量检索并行执行；图谱不可用时降级为空
     */
    private List<RetrievedDoc> graphRetrieve(String question) {
        // Optional 空值即降级：项目未启用图谱时干净地短路
        if (graphRagService.isEmpty()) {
            return List.of();
        }

        // 异步执行，与主流程并行；异常时降级为空列表
        CompletableFuture<List<RetrievedDoc>> future =
                CompletableFuture.supplyAsync(() ->
                        graphRagService.get().searchByQuery(question), retrievalExecutor);

        try {
            // 2 秒超时保护：图谱检索慢或挂死时，不让用户干等
            return future.get(2, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("图谱检索超时或失败，已降级跳过，原因: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 多路融合：采用 RRF（倒数排名融合）+ 文本去重
     * 不比较跨路分数（不可比），只看"名次"，多路共识胜于单路自信
     */
    private List<RetrievedDoc> fuseResults(List<RetrievedDoc> vectorDocs,
                                           List<RetrievedDoc> graphDocs) {
        // 1. 合并两条路，计算每条文档的 RRF 得分
        //    k 取 60（业界标准平滑常数），名次从 1 开始
        Map<String, RetrievedDoc> docMap = new LinkedHashMap<>();   // 按文本去重
        Map<String, Double> rrfScore = new HashMap<>();             // 文本 → RRF 得分

        // 向量路：名次 = 列表下标 + 1
        addToRrf(vectorDocs, 0, docMap, rrfScore);
        // 图谱路：名次 = 列表下标 + 1（两条路各自排序，互不干扰）
        addToRrf(graphDocs, 1, docMap, rrfScore);

        // 2. 按 RRF 得分降序排列
        return docMap.values().stream()
                .sorted(Comparator.comparingDouble(
                        d -> -rrfScore.getOrDefault(d.getText(), 0.0)))
                .toList();
    }

    /** RRF 辅助方法：把一路结果按名次计入总分 */
    private void addToRrf(List<RetrievedDoc> docs, int listIndex,
                          Map<String, RetrievedDoc> docMap,
                          Map<String, Double> rrfScore) {
        for (int rank = 0; rank < docs.size(); rank++) {
            RetrievedDoc doc = docs.get(rank);
            // 按文本去重：同名文本只保留第一次出现的文档对象
            docMap.putIfAbsent(doc.getText(), doc);
            // RRF 累加：1 / (60 + 名次)
            rrfScore.merge(doc.getText(),
                    1.0 / (60 + rank + 1), Double::sum);
        }
    }

    /**
     * Rerank 精排：调用三家服务商之一的 Cross-Encoder 对候选逐条打分
     * 候选为空则不调用 API（省钱且避免无效请求）
     */
    private List<RetrievedDoc> rerank(String question, List<RetrievedDoc> fused) {
        // 候选为空或只有 1 条时，无需精排
        if (fused == null || fused.size() <= 1) {
            return fused;
        }

        try {
            // RerankService 内部完成：组装请求 → 调用 API → 按 index 映射回原文
            List<ScoredDocument> scored = rerankService.rerank(
                    question, toScoredDocuments(fused), TOP_N_RERANK);

            // 把 Rerank 返回的分数写回 RetrievedDoc（覆盖原相似度）
            return scored.stream()
                    .map(s -> RetrievedDoc.of(
                            s.getText(), findSource(fused, s.getText()), s.getScore()))
                    .toList();
        } catch (Exception e) {
            // Rerank 服务故障时的降级：直接取 RRF 融合头部的 Top-N，保证不空
            log.error("Rerank 服务调用失败，降级取融合结果头部 Top-{}: {}",
                    TOP_N_RERANK, e.getMessage());
            return fused.size() > TOP_N_RERANK
                    ? fused.subList(0, TOP_N_RERANK)
                    : fused;
        }
    }

    /** 构建最终注入 LLM 的上下文：带序号、来源与相关性标注 */
    private String buildContext(List<RetrievedDoc> docs) {
        StringBuilder sb = new StringBuilder("请基于以下参考资料回答用户问题：\n\n");
        // 遍历精排后的文档，逐条拼接
        for (int i = 0; i < docs.size(); i++) {
            RetrievedDoc doc = docs.get(i);
            sb.append("【参考").append(i + 1).append("】")
                    .append("（来源:").append(doc.getSource()).append("，")
                    .append("相关度:").append(
                            String.format("%.2f", doc.getScore())).append("）\n")
                    .append(doc.getText()).append("\n\n");
        }
        return sb.toString();
    }

    // ============ 辅助方法（省略 DTO 内部实现细节） ============

    private List<ScoredDocument> toScoredDocuments(List<RetrievedDoc> docs) {
        // 包装成 RerankService 需要的 DTO
        return docs.stream()
                .map(d -> ScoredDocument.of(d.getText(), d.getScore()))
                .toList();
    }

    private String findSource(List<RetrievedDoc> docs, String text) {
        // 按文本反查来源元数据（Rerank API 不回传文档原文时用）
        return docs.stream()
                .filter(d -> d.getText().equals(text))
                .map(RetrievedDoc::getSource)
                .findFirst().orElse("unknown");
    }
}
```

**这段代码里有 5 个值得在面试里讲清楚的设计决策：**

1. **双路并行**：向量检索与图谱检索用 `CompletableFuture` 并行，是谁都不拖累谁；2 秒超时保护防挂死
2. **三级降级**：图谱挂了（空列表）、Rerank 挂了（取融合头部）、向量库挂了（空上下文），每一级都有兜底，接口永不 500
3. **RRF 而非分数加权**：跨路分数不可比，名次才可比，这是多路融合的正确姿势
4. **去重**：`Map.putIfAbsent` 按文本去重，防止同一切片被两条路同时召回后重复占用上下文
5. **空上下文显式处理**：检索为空时不注入任何内容，由 Prompt 端用"无参考"策略兜底，而不是硬塞噪音

---

## 六、项目实战：ruoyi-ai 的"向量 + GraphRAG"双路检索

### 6.1 双路检索的架构设计

ruoyi-ai 的检索架构可以用一句话概括：**语义之路负责"像不像"，图谱之路负责"连不连得上"。**

```
用户提问
   │
   ├─────────────┬────────────────────┐
   ▼             ▼                    ▼
Query Embedding  实体抽取             （若启用了图谱）
   │             │
   ▼             ▼
向量库 ANN 检索   Neo4j Cypher 查询
（Top-50）       （实体邻居 + 多跳关系）
   │             │
   └──────┬──────┘
          ▼
    RRF 粗融合（名次归一化）
          │
          ▼
    Rerank 精排 Top-5（Cross-Encoder）
          │
          ▼
    上下文注入（带源头标注） → LLM
```

**两条路各自解决的问题：**

| 问题类型 | 代表问题 | 正确路径 | 错误路径会怎样 |
|----------|----------|----------|----------------|
| 语义相似 | "关于缓存穿透的解决方案有哪些文档？" | 向量 | 图谱几乎无匹配 |
| 关系推理 | "订单服务和积分服务之间有什么调用关系？" | 图谱 | 向量按文本相似度很难命中结构化关系 |
| 语义 + 关系 | "Spring 事务失效的场景和解决方案" | 向量 + 图谱 | 单路必然漏掉一半信息 |

**架构设计的三个关键点：**

1. **可插拔**：图谱组件用 `Optional<Neo4jGraphRagService>` 注入——没部署 Neo4j 时项目照常运行（自动降级），部署后自动启用
2. **异步并行**：两条路耗时不同（向量毫秒级、图谱可能上百毫秒），必须并行而不是串行
3. **融合后统一裁决**：两路结果一律进 RRF 融合 + Rerank 精排，不搞"图谱结果永远优先"的拍脑袋权重

### 6.2 结果融合算法：从"拍脑袋权重"到"RRF"

初版融合代码（典型的错误示范）长这样：

```java
// 错误示范：把图谱结果的分数硬赋 0.95，然后按分数排
fused.add(ScoredDocument.from(gd, 0.95));  // 图谱结果"高置信度"
fused.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
```

问题：**0.95 是哪来的？** 这是"拍脑袋权重"——图谱结果并不总比向量结果相关，硬赋高分会让图谱的垃圾结果压过向量的好结果。

ruoyi-ai 生产版改为 **RRF + Rerank 两层融合**：

```java
// 第一层：RRF 按名次融合（不用分数，天然可比）
// 第二层：Rerank 输出最终裁决（Cross-Encoder 统一打分）
```

线上对比数据（内部评测集，50 组问题）：

| 融合方案 | Recall@5 | 首字时延 | 备注 |
|----------|----------|----------|------|
| 单向量路（基线） | 62% | 1.8s | 无图谱、无精排 |
| 向量+图谱（权重融合） | 67% | 2.1s | 拍脑袋权重，偶发图谱噪音压过好结果 |
| 向量+图谱（RRF） | 71% | 2.1s | 名次融合，稳健提升 |
| 向量+图谱（RRF+Rerank） | **76%** | **2.9s** | 精排兜底，效果好且稳定 |

结论很直白：**每加一层，Recall 提升约 4~5 个百分点，代价是 ~0.8s 延迟。** Rerank 的延迟换精度是否划算，取决于业务场景——对首字延迟敏感的客服场景，可以只保留 RRF 层。

### 6.3 线上效果：双路相对单路的收益

以"系统架构师如何查询微服务依赖关系"这类问题为例：

- **单向量路**：按语义相似度召回的是"微服务架构介绍"类文本，答不出具体依赖关系——**骨架缺失**
- **双路 + Rerank**：图谱路直接命中"order-service → user-service (feign)"的关系三元组，向量路补充架构描述，Rerank 把最相关的"依赖关系 + 架构说明"排到前面——**答案既有结构又有血肉**

成本值得说清：Neo4j 不是免费的。图谱构建要消耗**实体抽取的算力 + 人工复核成本**，且实体抽取准确率直接影响图谱质量。ruoyi-ai 的建议是：**只有"关系类问题占比高"的知识库才值得上图谱**，纯文档问答（FAQ 型）单向量路 + Rerank 已经够用。

---

## 七、进阶话题

### 7.1 混合检索：BM25 + 向量

向量检索擅长语义相似，但有一个盲区：**专有名词、编号、精确匹配**。比如用户搜"文档编号 ABC-2024-001"，向量相似度未必高，但 BM25 词频匹配能一击命中。

```java
/**
 * 混合检索：BM25 关键词召回 + 向量语义召回，两路结果交给 RRF 融合
 * 命中"精确编号类"查询时，BM25 是向量路的强力补位
 */
public List<RetrievedDoc> hybridRetrieve(String question) {
    // 第一路：BM25 关键词检索（可用 Elasticsearch / Lucene / Qdrant 内置全文索引）
    List<RetrievedDoc> bm25Results = bm25Retriever.retrieve(question);

    // 第二路：向量语义检索（复用 vectorRetriever）
    List<RetrievedDoc> vectorResults = vectorRetrieve(question);

    // 两路交给同一套 RRF 融合（名次归一化，天然适配）
    return fuseResults(bm25Results, vectorResults);
}
```

**经验**：混合检索 + RRF 的增益主要来自"互补"——BM25 保精确、向量保语义。选型注意：Qdrant 原生支持全文索引（BM25 类词项匹配），Milvus 也提供了 BM25 稀疏向量方案。如果你的向量库不支持全文检索，Spring Data Elasticsearch 也是成熟选择。

### 7.2 查询路由：按 Query 类型选择检索策略

不是所有问题都需要走完整流水线。查询路由的价值是**避免浪费**：

```java
/**
 * 查询路由：按问题意图分发到不同检索策略
 * 意图识别可以用 LLM 分类，也可以用规则 + 关键词
 */
public RagContext routeAndRetrieve(String question) {
    QueryType type = classifyQuery(question);   // 意图分类

    return switch (type) {
        // 关系类问题：走双路（向量 + 图谱），图谱是主力
        case RELATIONSHIP -> retrieveWithGraph(question);
        // 文档类问题：单向量路 + Rerank 即可，速度快
        case DOCUMENT -> retrieveVectorOnly(question);
        // 闲聊/开放性问题：无需检索，直接交给 LLM
        case GENERAL -> RagContext.empty();
    };
}

/** 意图分类：规则 + 关键词优先，命中不了再用 LLM 兜底 */
private QueryType classifyQuery(String question) {
    // 规则层：关系类关键词快速识别（低延迟、零成本）
    if (question.matches(".*(什么关系|依赖|调用链|关联|负责).*")) {
        return QueryType.RELATIONSHIP;
    }
    if (question.matches(".*(是什么|包括哪些|怎么使用|如何配置).*")) {
        return QueryType.DOCUMENT;
    }
    // 规则层不置信时，走 LLM 分类兜底（可缓存结果）
    return llmClassify(question);
}
```

**路由 vs 不路由的对比**：不路由时，所有流量都付出图谱检索和 Rerank 的延迟；路由后，关系类问题才走重流水线，文档类问题提速 30%+。代价是**多一次意图分类的准确性风险**——分错类比不分类更糟。所以分类做保守：规则能明确就用规则，模棱两可的走完整流水线。

### 7.3 Multi-Query：多角度查询扩展

一个 Query 往往不足以覆盖问题的全部角度。Multi-Query 的策略是：**把原始问题扩展成多个视角不同的问题**，分别检索后合并。

```java
/**
 * Multi-Query 查询扩展：一个 Query → 多个角度的问题
 * 例：原始"RAG 如何降低幻觉？"
 * 扩展："RAG 幻觉的原因", "RAG 幻觉的评估方法", "RAG 幻觉的缓解手段"
 */
public class MultiQueryTransformer implements QueryTransformer {

    private final ChatLanguageModel chatModel;
    private static final int MAX_QUERIES = 3;   // 扩展数量上限（越多越慢）

    @Override
    public Collection<Query> transform(Query query) {
        // 1. 让 LLM 生成多个角度的检索问题
        Prompt prompt = PromptTemplate.from(
                "你是一名检索专家。请从不同角度生成 {{n}} 个与用户问题相关的检索查询，"
                        + "每个查询单独一行，不要编号：\n{{query}}")
                .apply(Map.of("query", query.text(), "n", MAX_QUERIES));

        String response = chatModel.chat(prompt.text());

        // 2. 按行拆分，与原始问题一起返回
        List<Query> queries = response.lines()
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .limit(MAX_QUERIES)
                .map(Query::from)
                .collect(Collectors.toList());
        queries.add(0, query);   // 原始问题永远保留在第一位

        return queries;
    }
}
```

**Multi-Query 的成本账**：扩展 3 个 Query × 每个走完整召回 = 检索成本 ×3。合理用法是：
- 只对**检索质量差**的 Query 子集启用（先用一个轻量检索打分判断）
- 扩展 Query 的检索**并行执行**（CompletableFuture），只把延迟从 1 次变成 max(多次)，而不是累加
- 结果进入 RRF + Rerank 统一裁决，Multi-Query 只是"喂了更多候选给 Rerank"

---

## 八、面试题深度

### Q1：检索结果不理想时，如何系统性排查？

**参考答案：** 排查不能靠猜，要按"链路分层"逐级定位。ruoyi-ai 的排查流程如下：

```
检索结果差（答非所问 / 漏关键信息）
   │
   ├─ 第1层：看 Query 本身
   │    ├─ 是不是多轮追问导致指代不明？     → 上 CompressingQueryTransformer
   │    ├─ 问题表述是否过短或过泛？          → 上 Multi-Query / Step-back 扩展
   │    └─ 是否含专有名词/编号？             → 上 BM25 混合检索
   │
   ├─ 第2层：查召回（打印 Top-50 的分数分布）
   │    ├─ 分数普遍 < 0.3？                  → Embedding 模型与语言/领域不匹配，换模型
   │    ├─ 分数高但内容不相关？              → Embedding 表征能力瓶颈，交给 Rerank 也没用，
   │    │                                     需要换更强的 Embedding 或重新切分
   │    ├─ 相关文档压根没被召回？            → 查切分粒度（太粗/太细）、
   │    │                                     查文档是否真的入库（建库链路问题）
   │    └─ minScore 阈值过高把好结果滤掉了？ → 调低 minScore，让 Rerank 兜底
   │
   ├─ 第3层：查精排（对比 Rerank 前后 Top-5 差异）
   │    ├─ 好结果被 Rerank 排后？            → 查 Embedding 与 Rerank 是否同源（模型一致性）
   │    ├─ Rerank 分数过于接近？             → 候选本身相似度高，检查要不要去重/截断
   │    └─ Rerank 服务挂了走了降级？         → 查服务健康与告警
   │
   └─ 第4层：查上下文组装（看最终进 LLM 的 Prompt）
        ├─ 上下文超长被截断导致关键段丢失？  → 调整 ContentInjector 截断策略
        ├─ 引用标注混乱让 LLM 误读？          → 修正引用格式
        └─ 检索全空但 LLM 硬答？             → 加"无参考时如何作答"的系统提示
```

**排查的工具化落地**：给每个环节加打点日志，输出 `query → 改写后query → 各路召回数/分数分布 → RRF分数 → Rerank分数 → 最终Top-5ID`。有了这条链路日志，90% 的问题 5 分钟内定位，而不是靠"感觉像是 Embedding 的问题"。

### Q2：Rerank 的候选集大小如何确定？

**参考答案（四步定参）：**

1. **画召回率曲线**：在评测集上分别测候选数 {10, 20, 50, 100} 的 Recall@5，找到"增益拐点"（通常 30~60）
2. **对齐延迟预算**：Rerank 单条约 30~80ms，用 `候选数 × 单条耗时 ≤ 延迟预算` 反推上限。预算 3s 时，50 条约 1.5~4s，已经吃紧
3. **考虑知识库量级**：小库（<1万块）20~30 条够；大库（>百万块）先元数据过滤再进 Rerank，候选控制在 50~100
4. **A/B 验证**：候选 50 vs 100 线上跑一周，看答案准确率（人工标注或 LLM-as-Judge）有没有显著差异——没有差异就取小值省钱

**追问应对：** "候选集大了 Rerank 为什么变慢？" 答：Cross-Encoder 对每个 (Query, Doc) 对做一次完整前向传播，候选从 50 到 100，调用量翻倍，且长文本 token 也翻倍，延迟线性恶化。

### Q3：如何评估 RAG 系统的效果？

**参考答案（分层评估）：**

**第一层：检索质量（只检不算）—— 决定召回的上限**

| 指标 | 含义 | 计算方式 |
|------|------|----------|
| Recall@K | 相关文档有多少被召回 | 召回的命中数 / 总相关数 |
| Precision@K | 召回里有多少是相关的 | 命中数 / 召回总数 |
| MRR / NDCG | 相关文档排得有多靠前 | 基于排名位置加权 |

**第二层：端到端质量（检索+生成）—— 用 RAGAS 四件套**

1. **忠实度（Faithfulness）**：回答是否忠于检索到的上下文，衡量幻觉
2. **答案相关性（Answer Relevance）**：回答是否贴题
3. **上下文精确率（Context Precision）**：进上下文的片段有多少真有用
4. **上下文召回率（Context Recall）**：该进上下文的有没有都进

**第三层：工程指标**

- 首字时延 P95（检索+Rerank+生成）
- 检索失败率 / 降级率（图谱、Rerank 的可用性）
- 单 Query 成本（Embedding + Rerank + LLM 的 token 费用）

**评估闭环：** 构建 50~100 条评测集 → 批量跑流水线 → 计算三层指标 → 定位短板（是召回不行还是生成不行）→ 针对优化 → 回归对比。**关键原则：先评检索再评生成**——检索 Recall@5 只有 40% 时做 Prompt 优化是缘木求鱼。

### Q4：Bi-Encoder 和 Cross-Encoder 的本质区别？为什么不能只用其中一个？

**参考答案：** 本质区别在**信息交互时机**。Bi-Encoder 是双塔结构，Query 和文档各自独立编码后算相似度，token 级信息互不可见；优点是文档向量可离线预计算 + ANN 索引，百万级文档毫秒级检索。Cross-Encoder 把 Query 和文档拼成一个序列，自注意力让两边 token 充分交互，能捕捉精确语义对齐（否定、数字、词序），精度高，但每个文档对都要在线跑一次前向传播，无法预计算。

**为什么不能只用其一：**

- **只用 Bi-Encoder**：精度天花板低，长尾语义细节（"不是 A 而是 B"）抓不住，也没有"精排"环节，Top-K 里混噪音
- **只用 Cross-Encoder**：10 万文档就要在线打 10 万分，延迟和成本天文数字，根本跑不动

所以工业界标准做法是**两道流水线**：Bi-Encoder 快速筛出 Top-50（宽召回）→ Cross-Encoder 精排 Top-5（高精度）。这也是 ruoyi-ai 双阶段架构设计的底层逻辑。

---

## 面试避坑指南

### 坑 1：把 minScore 和 Rerank 混为一谈

**错误说法：**"我们调高 minScore 不就有精度了？"
**真相：** minScore 只是过滤低分，不改变排序；它是"一刀切的线"，而 Rerank 是"逐个判案的法官"。高分无关和低分相关，minScore 都管不了。**正确姿势：** 召回阶段 minScore 放宽松保召回，精度交给 Rerank。

### 坑 2：Rerank 候选集拍脑袋

**错误做法：** 候选随便设 1000 或者 10，不测召回率曲线。
**后果：** 候选太大延迟爆炸（首字 10s+），太小 Rerank 没有筛选空间（10 进 5 出）。**正确做法：** 候选 30~60 起步，用评测集画曲线找拐点，再对齐延迟预算。

### 坑 3：Embedding 和 Rerank 模型不同源

**错误做法：** Embedding 用 OpenAI，Rerank 用 BGE，然后发现"越精排越差"。
**原理：** 两个模型的"相关性判断标准"不一致，Rerank 的排序观念和 Embedding 的排序观念打架。**正确做法：** 尽量同源同系列（BGE-M3 + BGE-Reranker-v2-m3），或至少在评测集上验证两者排序的 Kendall 相关性。

### 坑 4：多路融合直接比分数

**错误做法：** 向量分数 0.9、图谱分数 0.8，直接按分数排——**0.9 和 0.8 根本不是一个尺度！**
**后果：** 图谱的"高置信度 0.95"直接碾压向量的好结果，好的被埋没。**正确做法：** 用 RRF 按名次融合（名次天然可比），或归一化后再加权。

### 坑 5：把"检索没结果"当成命运，不区分原因

**错误做法：** 检索为空就返回"找不到相关文档"，不排查。
**正确做法：** 区分四种情况：① Query 改写失败（指代没消解）② 召回被 minScore 滤光（阈值问题）③ 文档压根没入库（建库链路断了）④ 库真的没有相关内容（合理空结果）。**前三种是 bug，第四种才是正常业务**。

### 坑 6：忽略 Rerank 的降级与超时

**错误做法：** Rerank API 一崩溃整个检索接口 500。
**正确做法：** Rerank 必须有超时 + 降级（ruoyi-ai 做法：超时后取 RRF 融合头部 Top-N），并配置服务健康监控与告警。

---

## 参考资料与扩展阅读

- [LangChain4j RAG 教程（Advanced RAG 模块化管线）](https://docs.langchain4j.dev/tutorials/rag) — QueryTransformer / ContentAggregator / ContentInjector 官方文档
- [LangChain4j Re-Rank 集成文档](https://docs.langchain4j.dev/tutorials/ai-services) — ReRankingContentAggregator 与 ScoringModel 用法
- [LangChain4j CompressingQueryTransformer 源码](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-core/src/main/java/dev/langchain4j/rag/query/transformer/CompressingQueryTransformer.java) — 多轮对话查询压缩实现
- [BAAI/bge-reranker-v2-m3 模型卡](https://huggingface.co/BAAI/bge-reranker-v2-m3) — 精排模型技术细节与评测结果
- [Rerankers 模型研究报告（Qdrant 博客）](https://qdrant.tech/articles/rerankers/) — Rerank 模型横评与选型参考
- [RAGAS 评估框架](https://docs.ragas.io/) — 忠实度 / 答案相关性 / 上下文精确率 / 上下文召回率指标定义
- [Cohere Rerank 官方博客：Rerank 为什么必要](https://txt.cohere.com/rerank/) — Rerank 在 RAG 生产链路中的作用精讲
- [Elastic 博客：多阶段检索（Retrieve then Re-rank）](https://www.elastic.co/blog/multi-stage-retrieval-lexical-to-semantic) — 工业界"多阶段检索"范式综述

---

> **下一篇预告：** 检索只是"找得到"，生成才是"答得好"。RAG 的 Prompt 是如何设计才能让模型既忠实原文又不生硬复述？上下文越长效果一定越好吗？下一篇《RAG 生成侧优化：Prompt 工程与上下文窗口经济学》将拆解生成阶段的所有细节。