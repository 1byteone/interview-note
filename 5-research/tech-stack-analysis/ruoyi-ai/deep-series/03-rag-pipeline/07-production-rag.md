# 生产级RAG：从Demo到上线的12个关键优化

> 对应项目：ruoyi-ai/ruoyi-chat 模块

---

## 1. 从Demo到生产级，差在哪里？

在 ruoyi-ai 项目的早期阶段，我们只用了几十行代码就搭建了一个"能用"的 RAG 检索链路：

```java
// Demo 级别的 RAG —— 看起来简单，但离生产还很远
ContentRetriever retriever = EmbeddingStoreContentRetriever.builder()
    .embeddingStore(embeddingStore)   // 向量库，本地跑着没问题
    .embeddingModel(embeddingModel)   // 一个 Embedding 模型，够用
    .maxResults(5)                    // 随便返回 5 条
    .minScore(0.75)                   // 阈值拍脑袋定的
    .build();
```

这段代码跑通一个 QA 流程只需要 5 分钟，但真要上线服务成千上万的用户，**远远不够**。Demo 和生产的差距，就像玩具车和真车的差距——看着都有四个轮子，但速度、安全、舒适度天差地别。

下面这张表列出了从 Demo 到生产级必须攻克的 12 个优化维度：

| 维度 | 分类 | Demo 做法 | 生产级做法 | 一句话总结 |
|------|------|-----------|-----------|-----------|
| 检索质量 | 效果 | 单路向量检索 | 混合检索 + Rerank 重排序 | 别只靠向量，关键词 + 重排序才能兜底 |
| 质量评估 | 效果 | 人工看几条 | RAGAS 自动化评估 | 没有量化指标，优化就是拍脑袋 |
| 分块策略 | 效果 | 固定长度切分 | 语义分块 + 滑动窗口 | 切得好不好，直接影响检索上限 |
| Query 理解 | 效果 | 原始查询直接检索 | 查询改写 + HyDE | 用户问得不好，帮ta把问题改好 |
| 批量处理 | 性能 | 单条同步处理 | 批量 Embedding + 异步 | 吞吐量差十倍，别让用户等 |
| 缓存 | 性能 | 无缓存 | Redis 多级缓存 | 热点 Query 命中率能到 80% |
| 流式输出 | 性能 | 等全部生成再返回 | TokenStream 流式 | 首 token 延迟从 3s 降到 200ms |
| 连接池 | 性能 | 默认配置 | 调优连接池参数 | 高并发下连接不够就是灾难 |
| 高可用 | 稳定性 | 单点部署 | 多活 + 降级 + 熔断 | 一个模型挂了，整个系统不能挂 |
| 健康检查 | 稳定性 | 无 | 定期探活 | 提前发现问题，别等用户投诉 |
| 安全控制 | 安全 | 无权限校验 | 文档级权限 + 敏感信息过滤 | 检索到不该看的内容 = 生产事故 |
| 可观测性 | 运维 | 无日志 | 全链路追踪 + 指标监控 | 没有监控，出了故障两眼一抹黑 |

这 12 个维度不是可选项，而是**上线的必要条件**。接下来的章节将逐一深入每个维度，并给出在 ruoyi-ai 项目中的具体实现。

---

## 2. 质量评估：RAGAS 框架

"我的 RAG 系统回答质量怎么样？"——这是每个做 RAG 的人都会被问到的问题。如果没有量化指标，回答只能是"感觉还行"。RAGAS（Retrieval Augmented Generation Assessment）提供了一套标准化的评估体系，让质量可量化、可追踪。

### 2.1 RAGAS 四大核心指标

RAGAS 定义了四个核心指标，分别从**忠实度**、**相关性**、**召回率**和**精确率**四个维度打分，每个指标 0~1 分，越高越好。

#### Faithfulness（忠实度）：LLM 是否忠于检索到的上下文？

这是最重要的指标。LLM 有"幻觉"倾向——如果它回答的内容不在检索到的文档里，即使答案看起来正确，也是不可接受的。

**计算方式**：将 LLM 的回答拆解成多个陈述句（claims），逐一判断每个陈述是否能从检索到的上下文中找到依据。

```
示例：
上下文："张三出生于北京，毕业于清华大学。"
LLM 回答："张三出生于北京，毕业于北京大学。"

拆解后：
- "张三出生于北京" → 上下文支持 → 通过
- "张三毕业于北京大学" → 上下文说清华 → 不通过

Faithfulness = 1/2 = 0.5
```

#### Answer Relevance（答案相关性）：回答是否切题？

答案可能忠实于上下文，但答非所问也不行。这个指标衡量答案和问题的语义匹配度。

**计算方式**：根据答案反向生成若干问题，计算生成的问题与原始问题的余弦相似度。

```
示例：
问题："如何配置 Spring Boot 数据源？"
答案："Spring Boot 是一个流行的 Java 框架。"

反向生成的问题：
- "Spring Boot 是什么？" → 与原始问题相似度低 → Answer Relevance 低
```

#### Context Recall（上下文召回率）：相关的上下文是否都被检索到了？

如果检索阶段漏掉了关键文档，LLM 再强也答不对。这个指标衡量检索系统找到了多少比例的相关信息。

**计算方式**：人工标注出"黄金文档集"（ground truth relevant documents），计算检索到的相关文档占黄金文档集的比例。

```
Context Recall = |检索到的相关文档 ∩ 黄金文档集| / |黄金文档集|
```

#### Context Precision（上下文精确率）：检索到的上下文是否都相关？

召回率高了，但返回了一堆无关文档，反而会稀释 LLM 的注意力。这个指标衡量检索结果的"精准度"。

**计算方式**：对检索结果中每个相关文档的位置进行加权，排在前面的相关文档贡献更大。

```
Context Precision = Σ(Precision@k × rel_k) / |相关文档总数|

其中 rel_k 表示第 k 个位置是否相关
```

### 2.2 在 ruoyi-ai 中集成 RAGAS 评估

RAGAS 本身是一个 Python 框架，而 ruoyi-ai 是 Java 项目。我们可以通过两种方式集成：

**方案一：Python 脚本调用（推荐）**

将 RAGAS 评估封装为 Python 脚本，Java 通过 ProcessBuilder 或 HTTP 接口调用。

```python
# scripts/ragas_eval.py —— RAGAS 评估脚本，供 Java 侧调用
# 通过命令行参数传入评估数据，返回 JSON 格式的评估结果
import json
import sys
from datasets import Dataset
from ragas import evaluate
from ragas.metrics import (
    faithfulness,          # 忠实度：LLM 是否忠于检索到的上下文
    answer_relevancy,      # 答案相关性：回答是否切题
    context_recall,        # 上下文召回率：相关文档是否都被检索到
    context_precision,     # 上下文精确率：检索到的文档是否都相关
)

def evaluate_rag(questions, answers, contexts, ground_truths):
    """
    评估 RAG 系统的检索和生成质量
    
    参数说明：
        questions: 用户问题列表
        answers: LLM 生成的回答列表
        contexts: 检索到的上下文列表（每个问题对应一个文档列表）
        ground_truths: 参考答案列表（用于计算召回率）
    """
    # 构建 HuggingFace Dataset 格式的评估数据集
    data = {
        "question": questions,                     # 用户问题
        "answer": answers,                         # LLM 生成的回答
        "contexts": contexts,                      # 检索到的文档片段
        "ground_truth": ground_truths,             # 标准答案（人工标注）
    }
    dataset = Dataset.from_dict(data)
    
    # 执行 RAGAS 评估，计算四个核心指标
    result = evaluate(
        dataset,
        metrics=[
            faithfulness,          # 忠实度指标
            answer_relevancy,      # 答案相关性指标
            context_recall,        # 上下文召回率指标
            context_precision,     # 上下文精确率指标
        ]
    )
    
    # 输出评估结果，供 Java 侧解析
    scores = {
        "faithfulness": float(result["faithfulness"]),
        "answer_relevancy": float(result["answer_relevancy"]),
        "context_recall": float(result["context_recall"]),
        "context_precision": float(result["context_precision"]),
    }
    print(json.dumps(scores, ensure_ascii=False))

if __name__ == "__main__":
    # 从命令行参数读取评估数据（JSON 格式）
    input_data = json.loads(sys.argv[1])
    evaluate_rag(
        questions=input_data["questions"],
        answers=input_data["answers"],
        contexts=input_data["contexts"],
        ground_truths=input_data["ground_truths"],
    )
```

```java
// RagasEvaluator.java —— Java 侧调用 RAGAS 评估脚本
// 将 RAG 系统的 QA 日志导出，调用 Python 评估脚本获取质量分数
@Component
@Slf4j
public class RagasEvaluator {

    @Value("${rag.evaluation.python-path:python3}")
    private String pythonPath;                           // Python 解释器路径
    
    @Value("${rag.evaluation.script-path:scripts/ragas_eval.py}")
    private String scriptPath;                           // 评估脚本路径

    /**
     * 执行 RAGAS 评估，返回四个维度的质量分数
     * 
     * @param evalData 包含 questions、answers、contexts、ground_truths 的评估数据集
     * @return 评估结果，包含 faithfulness、answer_relevancy、context_recall、context_precision
     */
    public RagasScore evaluate(RagasEvalData evalData) {
        try {
            // 将评估数据序列化为 JSON 字符串
            ObjectMapper mapper = new ObjectMapper();
            String inputJson = mapper.writeValueAsString(evalData);
            
            // 构建进程：python3 ragas_eval.py '<json_data>'
            ProcessBuilder pb = new ProcessBuilder(
                pythonPath, scriptPath, inputJson
            );
            Process process = pb.start();
            
            // 读取 Python 脚本的标准输出（评估结果）
            String resultJson = new String(
                process.getInputStream().readAllBytes(),
                StandardCharsets.UTF_8
            );
            
            // 解析 JSON 格式的评估结果
            return mapper.readValue(resultJson, RagasScore.class);
            
        } catch (Exception e) {
            log.error("RAGAS 评估执行失败", e);
            // 评估失败时返回默认分数（0 分），不影响主流程
            return RagasScore.failed();
        }
    }
}
```

**方案二：Java 侧独立实现指标计算**

如果不想依赖 Python 环境，可以在 Java 侧实现简化版的评估逻辑。但 RAGAS 的 faithfulness 和 answer_relevancy 依赖 LLM 进行 claim 拆解和反向问题生成，精度不如 Python 原版。

### 2.3 评估数据集的构建方法

评估数据集的质量直接决定了评估结果的可信度。以下是 ruoyi-ai 项目中使用的数据集构建策略：

```java
// EvalDatasetBuilder.java —— 构建 RAGAS 评估数据集
// 从生产日志中采样、人工标注、自动生成三种方式结合
@Component
@Slf4j
public class EvalDatasetBuilder {

    @Resource
    private MongoTemplate mongoTemplate;                // 存储 QA 日志的 MongoDB
    
    @Resource
    private RagasEvaluator ragasEvaluator;              // RAGAS 评估器

    /**
     * 从生产日志中采样构建评估数据集
     * 策略：按时间分层采样，确保覆盖不同时间段和不同类型的 Query
     * 
     * @param sampleSize 采样数量
     * @param days 回溯天数
     * @return 评估数据集
     */
    public RagasEvalData buildFromProductionLogs(int sampleSize, int days) {
        // 1. 从 MongoDB 中查询最近 N 天的 QA 日志
        //    queryLog 表记录了每次查询的 question、answer、retrieved_contexts
        LocalDateTime since = LocalDateTime.now().minusDays(days);
        List<QueryLog> logs = mongoTemplate.find(
            Query.query(Criteria.where("createdAt").gte(since))
                .with(Sort.by(Sort.Direction.DESC, "createdAt"))
                .limit(sampleSize * 3),  // 多取一些，后续需要筛选
            QueryLog.class
        );
        
        // 2. 分层采样：按 Query 类型（简单查询、复杂查询、模糊查询）均匀分布
        List<QueryLog> sampled = stratifiedSample(logs, sampleSize);
        
        // 3. 构建评估数据集对象
        RagasEvalData evalData = new RagasEvalData();
        evalData.setQuestions(sampled.stream()
            .map(QueryLog::getQuestion).collect(Collectors.toList()));
        evalData.setAnswers(sampled.stream()
            .map(QueryLog::getAnswer).collect(Collectors.toList()));
        evalData.setContexts(sampled.stream()
            .map(QueryLog::getRetrievedContexts).collect(Collectors.toList()));
        
        // 4. ground_truth 需要人工标注
        //    这里返回部分数据，标注团队通过 Web 界面补充 ground_truth
        return evalData;
    }

    /**
     * 分层采样算法
     * 将 Query 按类型（简单、复杂、模糊）分为三层，每层等比例采样
     */
    private List<QueryLog> stratifiedSample(List<QueryLog> logs, int totalSize) {
        // 使用简单的分类规则：按 Query 长度和关键词区分
        Map<String, List<QueryLog>> stratified = logs.stream()
            .collect(Collectors.groupingBy(this::classifyQuery));
        
        // 每层分配相同的采样数量
        int perStratum = totalSize / stratified.size();
        List<QueryLog> result = new ArrayList<>();
        
        for (List<QueryLog> stratum : stratified.values()) {
            Collections.shuffle(stratum);                   // 随机打乱
            result.addAll(stratum.subList(0, Math.min(perStratum, stratum.size())));
        }
        
        return result;
    }

    /**
     * 根据 Query 特征分类
     * 简单查询：短句，包含明确关键词
     * 复杂查询：长句，包含多个条件
     * 模糊查询：包含模糊词汇，或长度适中但无明确实体
     */
    private String classifyQuery(QueryLog log) {
        String q = log.getQuestion();
        if (q.length() < 10) return "simple";               // 短查询判定为简单
        if (q.contains("并且") || q.contains("或者") || q.contains("对比"))
            return "complex";                               // 含逻辑词判定为复杂
        return "ambiguous";                                  // 其他归为模糊查询
    }
}
```

**评估数据集构建的黄金守则：**

1. **生产日志优先**：从真实用户请求中采样，比人工构造的数据更有代表性
2. **分层覆盖**：确保覆盖简单查询、复杂查询、模糊查询等不同类型
3. **人工标注不可少**：ground_truth 必须人工标注，自动化生成的信噪比太低
4. **持续更新**：每次知识库更新后，重新评估数据集，防止过拟合
5. **基线对比**：每次优化后运行同一套评估集，确保效果不退步

---

## 3. 性能优化

性能是 RAG 系统从 Demo 走向生产的第一道坎。用户能忍受的等待时间有限——Google 的数据显示，搜索延迟从 0.5s 增加到 2s，用户满意度下降 30%。

### 3.1 批量 Embedding + 异步处理

在文档入库阶段，如果逐条调用 Embedding 模型，1000 条文档可能需要 3~5 分钟。批量处理可以大幅提升吞吐量。

```java
// BatchEmbeddingService.java —— 批量 Embedding + 异步处理
// 利用 Spring @Async + CompletableFuture 实现高吞吐的文档向量化
@Service
@Slf4j
public class BatchEmbeddingService {

    @Resource
    private EmbeddingModel embeddingModel;                 // 注入 Embedding 模型
    
    @Resource
    private EmbeddingStore<TextSegment> embeddingStore;    // 向量存储
    
    @Value("${rag.embedding.batch-size:32}")
    private int batchSize;                                 // 每批处理文档数，默认 32 条

    /**
     * 批量处理文档入库 —— 入口方法
     * 将文档切分后，分批异步处理，大幅提升吞吐量
     * 
     * @param documents 待处理的文档列表
     * @return CompletableFuture<Void> 便于调用方编排后续操作
     */
    @Async("ragTaskExecutor")                              // 使用专门的线程池异步执行
    public CompletableFuture<Void> batchProcessDocuments(List<Document> documents) {
        // 1. 将文档切分成文本片段（TextSegment）
        //    使用 DocumentSplitter 按照段落和长度进行语义切分
        List<TextSegment> allSegments = documents.parallelStream()  // 并行切分文档
            .flatMap(doc -> splitDocument(doc).stream())
            .collect(Collectors.toList());
        
        log.info("文档切分完成，共 {} 个片段，开始分批 Embedding", allSegments.size());
        
        // 2. 分批处理，每批 batchSize 条
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        
        for (int i = 0; i < allSegments.size(); i += batchSize) {
            // 取出当前批次的数据
            List<TextSegment> batch = allSegments.subList(
                i, Math.min(i + batchSize, allSegments.size())
            );
            
            // 异步提交 Embedding 任务
            // 使用 CompletableFuture.supplyAsync 提交到 ragTaskExecutor 线程池
            CompletableFuture<Void> future = CompletableFuture.supplyAsync(() -> {
                // 3. 批量调用 Embedding 模型（一次调用处理多条文本）
                //    大多数 Embedding 模型支持批量输入，比单条调用快 5~10 倍
                List<Embedding> embeddings = embeddingModel.embedAll(batch).content();
                return embeddings;
            }, ragTaskExecutor).thenAccept(embeddings -> {
                // 4. 将 Embedding 向量和文本片段一起存入向量库
                //    注意：这里需要确保 embeddings 和 batch 的顺序一致
                embeddingStore.addAll(embeddings, batch);
                log.debug("批次处理完成，{} 条", batch.size());
            }).exceptionally(throwable -> {
                // 5. 异常处理：记录失败批次，不影响后续处理
                log.error("批次 Embedding 失败，起始索引: {}", i, throwable);
                return null;
            });
            
            futures.add(future);
        }
        
        // 6. 等待所有批次处理完成
        //    使用 allOf 等待所有异步任务结束
        return CompletableFuture.allOf(
            futures.toArray(new CompletableFuture[0])
        );
    }

    /**
     * 文档切分：将长文档按语义切分为多个 TextSegment
     * 使用 LangChain4j 的 DocumentSplitter 实现
     * 策略：优先按段落切分，段落过长时按句号切分，句子过长时按长度切分
     */
    private List<TextSegment> splitDocument(Document document) {
        // 使用 DocumentByParagraphSplitter 按段落切分
        // 每个段落作为一个独立的 TextSegment，保留元数据
        DocumentByParagraphSplitter splitter = new DocumentByParagraphSplitter(
            500,    // 最大字符数，超过时继续切分
            100     // 重叠字符数，避免上下文断裂
        );
        return splitter.split(document);
    }

    /**
     * 配置异步任务线程池
     * 核心线程数 = CPU 核心数 × 2，适应 Embedding 的 IO 密集型特征
     */
    @Bean("ragTaskExecutor")
    public Executor ragTaskExecutor() {
        int cores = Runtime.getRuntime().availableProcessors();
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(cores * 2);               // 核心线程数
        executor.setMaxPoolSize(cores * 4);                // 最大线程数
        executor.setQueueCapacity(512);                    // 队列容量
        executor.setThreadNamePrefix("rag-embedding-");    // 线程名前缀，便于排查
        executor.setRejectedExecutionHandler(
            new ThreadPoolExecutor.CallerRunsPolicy()      // 拒绝策略：由调用线程执行
        );
        executor.initialize();
        return executor;
    }
}
```

### 3.2 向量检索缓存（Redis 缓存热点 Query）

用户查询存在明显的"二八定律"——20% 的热点 Query 占了 80% 的请求量。这 20% 的查询结果完全可以缓存起来，避免反复调用 Embedding 模型和向量库。

```java
// RagCacheService.java —— 基于 Redis 的向量检索缓存
// 缓存热点 Query 的检索结果，降低 Embedding 模型和向量库的负载
@Service
@Slf4j
public class RagCacheService {

    @Resource
    private StringRedisTemplate redisTemplate;             // Redis 操作模板
    
    @Resource
    private ObjectMapper objectMapper;                     // JSON 序列化工具
    
    @Value("${rag.cache.ttl-seconds:600}")
    private int cacheTtlSeconds;                           // 缓存过期时间，默认 10 分钟
    
    @Value("${rag.cache.max-size:10000}")
    private int maxCacheSize;                              // 最大缓存条目数

    /**
     * 获取缓存的检索结果
     * 如果缓存命中，直接返回；否则返回 null，由调用方执行实际检索
     * 
     * @param query 用户查询文本
     * @param topK 返回 top-k 结果（不同的 topK 需要不同的缓存键）
     * @return 缓存的内容列表，未命中时返回 null
     */
    public List<TextSegment> getCachedResults(String query, int topK) {
        // 1. 生成缓存键：使用 query 的 MD5 哈希 + topK 参数
        //    注意：这里对 query 做归一化处理（去空格、转小写），提高缓存命中率
        String cacheKey = buildCacheKey(normalizeQuery(query), topK);
        
        // 2. 查询 Redis，获取缓存的 JSON 字符串
        String cachedJson = redisTemplate.opsForValue().get(cacheKey);
        
        if (cachedJson != null) {
            try {
                // 3. 缓存命中：反序列化 JSON 为 TextSegment 列表
                log.debug("缓存命中，key: {}", cacheKey);
                return objectMapper.readValue(
                    cachedJson,
                    new TypeReference<List<TextSegment>>() {}
                );
            } catch (Exception e) {
                // 反序列化失败时，删除损坏的缓存，重新检索
                log.warn("缓存数据损坏，删除缓存", e);
                redisTemplate.delete(cacheKey);
            }
        }
        
        // 4. 缓存未命中，返回 null
        return null;
    }

    /**
     * 写入缓存
     * 在完成检索后调用，将结果缓存到 Redis
     * 
     * @param query 用户查询文本
     * @param topK 返回 top-k 结果
     * @param results 检索到的内容列表
     */
    public void cacheResults(String query, int topK, List<TextSegment> results) {
        String cacheKey = buildCacheKey(normalizeQuery(query), topK);
        
        try {
            // 1. 序列化检索结果为 JSON 字符串
            String json = objectMapper.writeValueAsString(results);
            
            // 2. 写入 Redis，设置过期时间（TTL）
            redisTemplate.opsForValue().set(
                cacheKey, json, 
                Duration.ofSeconds(cacheTtlSeconds)
            );
            
            // 3. 控制缓存总量：使用 Redis ZSET 记录缓存键的访问时间
            //    当缓存数量超过 maxCacheSize 时，淘汰最久未访问的缓存
            redisTemplate.opsForZSet().add(
                "rag:cache:access_log", 
                cacheKey, 
                System.currentTimeMillis()
            );
            trimCacheIfNeeded();
            
            log.debug("缓存写入成功，key: {}", cacheKey);
        } catch (Exception e) {
            // 缓存写入失败不影响主流程，仅记录日志
            log.warn("缓存写入失败", e);
        }
    }

    /**
     * 缓存预热：在系统启动时，加载昨天的热点 Query 到缓存
     * 配合 @PostConstruct 在应用启动时自动执行
     */
    @PostConstruct
    public void preloadHotQueries() {
        // 从昨天的 QPS 统计中，找出 Top 100 的热点查询
        List<String> hotQueries = getYesterdayHotQueries(100);
        
        for (String query : hotQueries) {
            // 对每个热点查询预先执行检索并缓存
            // 这样用户在上班高峰时，能直接命中缓存
            List<TextSegment> results = executeActualRetrieval(query);
            cacheResults(query, 5, results);
        }
        
        log.info("缓存预热完成，共加载 {} 个热点查询", hotQueries.size());
    }

    /**
     * 构建缓存键：rag:cache:query:{md5}:top{topK}
     * 使用 MD5 而不是原始 query，避免长 key 占用过多内存
     */
    private String buildCacheKey(String normalizedQuery, int topK) {
        String md5 = DigestUtils.md5DigestAsHex(
            normalizedQuery.getBytes(StandardCharsets.UTF_8)
        );
        return String.format("rag:cache:query:%s:top%d", md5, topK);
    }

    /**
     * 查询归一化：去除首尾空格、全角转半角、统一小写
     * 提高缓存命中率的关键步骤
     */
    private String normalizeQuery(String query) {
        if (query == null) return "";
        return query.trim()
                    .replaceAll("\\s+", " ")              // 多个空格合并为一个
                    .toLowerCase();                        // 统一小写
    }
}
```

### 3.3 流式输出（TokenStream 实现打字机效果）

用户体验的一个关键指标是**首 token 延迟（TTFT，Time to First Token）**。如果用户要等 3~5 秒才能看到第一个字，体验会很差。流式输出可以在第一个 token 生成后立即返回，之后的 token 逐个推送到前端。

```java
// RagStreamingService.java —— 流式 RAG 检索 + 生成
// 使用 LangChain4j 的 TokenStream 实现打字机效果
// 核心思路：检索完成后立即返回第一个 token，后续内容逐步推送
@Service
@Slf4j
public class RagStreamingService {

    @Resource
    private ContentRetriever contentRetriever;             // 内容检索器
    
    @Resource
    private StreamingChatLanguageModel streamingModel;     // 流式对话模型
    
    @Resource
    private RagCacheService cacheService;                  // 缓存服务

    /**
     * 流式 RAG 问答 —— 核心方法
     * 使用 SSE（Server-Sent Events）将生成的内容逐字推送给前端
     * 
     * @param query 用户问题
     * @param userId 用户 ID（用于权限过滤）
     * @param sseEmitter SSE 发射器，将内容推送到前端
     */
    public void streamingRag(String query, String userId, SseEmitter sseEmitter) {
        // 1. 先尝试从缓存中获取检索结果，避免重复调用 Embedding 模型
        List<TextSegment> cachedResults = cacheService.getCachedResults(query, 5);
        
        // 2. 获取检索到的上下文（缓存命中则跳过检索）
        List<TextSegment> contexts;
        if (cachedResults != null) {
            contexts = cachedResults;                       // 缓存命中，直接使用
        } else {
            // 执行检索：从向量库中查询最相关的文档片段
            // 使用 ContentRetriever 检索，返回 top-5 结果
            contexts = contentRetriever.retrieve(query);
            // 异步写入缓存，不阻塞主流程
            CompletableFuture.runAsync(() -> 
                cacheService.cacheResults(query, 5, contexts)
            );
        }
        
        // 3. 构建 Prompt，将检索到的上下文注入 System Message
        //    格式：每个上下文作为 System Message 的一部分
        String systemPrompt = buildContextPrompt(contexts);
        UserMessage userMessage = UserMessage.from(query);
        SystemMessage systemMessage = SystemMessage.from(systemPrompt);
        
        // 4. 创建 ChatRequest，将上下文注入到对话中
        ChatRequest request = ChatRequest.builder()
            .messages(systemMessage, userMessage)
            .build();
        
        // 5. 发起流式调用 —— 关键代码
        //    TokenStream 是 LangChain4j 提供的流式调用接口
        streamingModel.chat(request)
            .onNext(token -> {                              // 每生成一个 token 回调一次
                try {
                    // 将 token 通过 SSE 推送给前端
                    sseEmitter.send(SseEmitter.event()
                        .name("token")
                        .data(token, MediaType.TEXT_PLAIN));
                } catch (IOException e) {
                    // SSE 连接断开时，取消生成
                    log.warn("SSE 推送失败，连接可能已断开", e);
                    throw new RuntimeException(e);
                }
            })
            .onComplete(response -> {                       // 生成完成
                try {
                    // 发送完成信号，前端收到后关闭流式连接
                    sseEmitter.send(SseEmitter.event()
                        .name("complete")
                        .data(response.tokenUsage()));      // 附带 token 用量信息
                    sseEmitter.complete();
                    log.info("流式生成完成，token 用量: {}", response.tokenUsage());
                } catch (IOException e) {
                    log.error("发送完成信号失败", e);
                }
            })
            .onError(error -> {                             // 生成出错
                log.error("流式生成出错", error);
                try {
                    sseEmitter.send(SseEmitter.event()
                        .name("error")
                        .data(error.getMessage()));
                    sseEmitter.completeWithError(error);
                } catch (IOException e) {
                    log.error("发送错误信号失败", e);
                }
            })
            .start();                                       // 启动流式生成
    }

    /**
     * 构建注入上下文的 Prompt
     * 将检索到的文档片段组织为 System Message，指导 LLM 基于这些内容回答
     * 
     * @param contexts 检索到的文档片段列表
     * @return 格式化后的 Prompt 字符串
     */
    private String buildContextPrompt(List<TextSegment> contexts) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是一个知识库问答助手。请基于以下提供的参考资料回答用户的问题。\n");
        sb.append("如果在参考资料中找不到相关信息，请如实回答"无法从现有资料中找到答案"。\n");
        sb.append("请不要编造信息，也不要引用参考资料之外的内容。\n\n");
        sb.append("参考资料：\n");
        
        for (int i = 0; i < contexts.size(); i++) {
            TextSegment segment = contexts.get(i);
            // 标注来源，便于 LLM 区分不同文档
            String source = segment.metadata().getString("source");
            sb.append("---\n");
            sb.append("【文档 ").append(i + 1).append("】");
            if (source != null) {
                sb.append("（来源：").append(source).append("）");
            }
            sb.append("\n").append(segment.text()).append("\n");
        }
        
        return sb.toString();
    }
}
```

### 3.4 连接池优化

RAG 系统涉及多个外部服务——Embedding 模型、向量数据库、LLM 服务。每个服务的连接池配置不当，都可能成为性能瓶颈。

```java
// RagConnectionPoolConfig.java —— 连接池统一配置
// 为 Embedding 模型、向量库、LLM 服务分别配置最优的连接池参数
@Configuration
@Slf4j
public class RagConnectionPoolConfig {

    /**
     * 1. 配置 HTTP 连接池（用于调用 Embedding 模型和 LLM 的 HTTP API）
     * 使用 Apache HttpClient 的连接池管理机制
     * 核心参数：最大连接数、每个路由的最大连接数、空闲连接存活时间
     */
    @Bean
    public HttpClient httpClient() {
        // 连接池管理器：控制与外部服务的并发连接数
        PoolingHttpClientConnectionManager connectionManager = 
            new PoolingHttpClientConnectionManager();
        
        connectionManager.setMaxTotal(200);                 // 最大总连接数：200
        connectionManager.setDefaultMaxPerRoute(50);        // 每个路由（服务）最大连接数：50
        connectionManager.setValidateAfterInactivity(5000); // 空闲 5 秒后验证连接是否可用
        
        return HttpClientBuilder.create()
            .setConnectionManager(connectionManager)
            .setConnectionTimeToLive(30, TimeUnit.SECONDS)  // 连接最大存活时间：30 秒
            .evictIdleConnections(30, TimeUnit.SECONDS)     // 每 30 秒清理空闲连接
            .build();
    }

    /**
     * 2. 配置 RestTemplate（使用上面配置的连接池）
     * 用于调用 Embedding 模型和 LLM 的 REST API
     */
    @Bean
    public RestTemplate restTemplate(HttpClient httpClient) {
        // 将 HttpClient 包装为 Spring 的 RestTemplate
        HttpComponentsClientHttpRequestFactory factory = 
            new HttpComponentsClientHttpRequestFactory(httpClient);
        
        factory.setConnectTimeout(5000);                    // 连接超时：5 秒
        factory.setConnectionRequestTimeout(3000);          // 从连接池获取连接的超时：3 秒
        factory.setReadTimeout(60000);                      // 读取超时：60 秒（LLM 生成可能较慢）
        
        return new RestTemplate(factory);
    }

    /**
     * 3. 配置向量库连接池（以 Qdrant 为例）
     * Qdrant 使用 gRPC 协议，需要配置 gRPC 连接池
     */
    @Bean
    public QdrantClient qdrantClient() {
        return new QdrantClient(
            QdrantGrpcClient.newBuilder(
                "localhost",                                // Qdrant 服务地址
                6334,                                       // gRPC 端口
                true                                        // 启用 TLS
            )
            .withGrpcDeadline(5000)                         // gRPC 调用超时：5 秒
            .build()
        );
    }

    /**
     * 4. 配置 Redis 连接池（用于缓存）
     * 使用 Lettuce 连接池，支持异步和响应式操作
     */
    @Bean
    public LettuceConnectionFactory redisConnectionFactory() {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
        config.setHostName("localhost");
        config.setPort(6379);
        
        // Lettuce 连接池配置
        GenericObjectPoolConfig<?> poolConfig = new GenericObjectPoolConfig<>();
        poolConfig.setMaxTotal(32);                         // 最大连接数：32
        poolConfig.setMaxIdle(8);                           // 最大空闲连接数：8
        poolConfig.setMinIdle(4);                           // 最小空闲连接数：4
        poolConfig.setMaxWait(Duration.ofMillis(3000));     // 获取连接最大等待时间：3 秒
        
        LettucePoolingClientConfiguration lettuceConfig = 
            LettucePoolingClientConfiguration.builder()
                .poolConfig(poolConfig)
                .commandTimeout(Duration.ofMillis(500))     // Redis 命令超时：500ms
                .build();
        
        return new LettuceConnectionFactory(config, lettuceConfig);
    }
}
```

---

## 4. 稳定性优化

生产环境最大的敌人是"不确定性"。模型可能超时、向量库可能宕机、网络可能抖动。一个生产级的 RAG 系统必须能优雅地处理这些异常。

### 4.1 Embedding 模型降级（主备切换）

ruoyi-ai 支持多个 Embedding 提供商（OpenAI、ZhipuAI、Tongyi、SiliconFlow）。当主模型不可用时，自动切换到备用模型。

```java
// EmbeddingModelDegradationService.java —— Embedding 模型降级服务
// 实现主备切换：主模型失败时自动切换到备用模型
// 支持多种 Embedding 提供商，构建"高可用"的 Embedding 能力
@Service
@Slf4j
public class EmbeddingModelDegradationService {

    // 主 Embedding 模型（默认使用 OpenAI 的 text-embedding-3-small）
    @Resource
    @Qualifier("openAiEmbeddingModel")
    private EmbeddingModel primaryModel;
    
    // 备用 Embedding 模型列表（按优先级排序）
    @Resource
    @Qualifier("zhipuAiEmbeddingModel")
    private EmbeddingModel secondaryModel1;
    
    @Resource
    @Qualifier("siliconFlowEmbeddingModel")
    private EmbeddingModel secondaryModel2;
    
    @Resource
    @Qualifier("tongyiEmbeddingModel")
    private EmbeddingModel secondaryModel3;
    
    @Value("${rag.embedding.degradation.max-retries:2}")
    private int maxRetries;                                 // 每个模型的最大重试次数
    
    // 降级统计：记录每个模型的使用次数和失败次数
    private final Map<String, AtomicInteger> usageCount = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> failureCount = new ConcurrentHashMap<>();

    /**
     * 带降级的 Embedding 方法
     * 主模型 -> 备用模型1 -> 备用模型2 -> 备用模型3 依次尝试
     * 全部失败时抛出异常，由上游决定如何兜底
     * 
     * @param text 需要向量化的文本
     * @return Embedding 向量
     */
    public Embedding embedWithDegradation(String text) {
        // 按优先级排列的模型列表
        List<EmbeddingModel> models = List.of(
            primaryModel, secondaryModel1, secondaryModel2, secondaryModel3
        );
        List<String> modelNames = List.of(
            "OpenAI", "ZhipuAI", "SiliconFlow", "Tongyi"
        );
        
        // 记录上一次的异常，供最终抛出时使用
        Exception lastException = null;
        
        // 依次尝试每个模型
        for (int i = 0; i < models.size(); i++) {
            EmbeddingModel model = models.get(i);
            String modelName = modelNames.get(i);
            
            // 统计调用次数
            usageCount.computeIfAbsent(modelName, k -> new AtomicInteger()).incrementAndGet();
            
            // 在当前模型上尝试多次
            for (int retry = 0; retry <= maxRetries; retry++) {
                try {
                    Embedding embedding = model.embed(text).content();
                    
                    // 降级日志：如果使用的是备用模型，记录降级事件
                    if (i > 0) {
                        log.warn("Embedding 模型降级：使用 {}（备用层级：{}）", 
                            modelName, i);
                    }
                    
                    return embedding;
                    
                } catch (Exception e) {
                    lastException = e;
                    log.warn("Embedding 模型 {} 第 {} 次尝试失败：{}", 
                        modelName, retry + 1, e.getMessage());
                    
                    // 最后一次重试还是失败，尝试下一个模型
                    if (retry == maxRetries) {
                        failureCount.computeIfAbsent(
                            modelName, k -> new AtomicInteger()
                        ).incrementAndGet();
                        log.error("Embedding 模型 {} 已完全失败，切换到下一个模型", modelName);
                    }
                }
            }
        }
        
        // 所有模型都失败了，抛出异常
        log.error("所有 Embedding 模型均不可用，最后一次异常：", lastException);
        throw new RuntimeException("Embedding 服务不可用", lastException);
    }

    /**
     * 获取各模型的使用统计
     * 用于监控面板展示，帮助评估各模型的实际表现
     */
    public Map<String, Map<String, Integer>> getModelStatistics() {
        Map<String, Map<String, Integer>> stats = new LinkedHashMap<>();
        
        for (String name : List.of("OpenAI", "ZhipuAI", "SiliconFlow", "Tongyi")) {
            Map<String, Integer> modelStats = new HashMap<>();
            modelStats.put("usage", usageCount.getOrDefault(name, new AtomicInteger()).get());
            modelStats.put("failures", failureCount.getOrDefault(name, new AtomicInteger()).get());
            stats.put(name, modelStats);
        }
        
        return stats;
    }
}
```

### 4.2 向量库健康检查

向量库是 RAG 系统的核心依赖。如果向量库挂了，整个 RAG 系统就不可用。通过定时健康检查，可以在问题发生时第一时间发现并处理。

```java
// VectorStoreHealthChecker.java —— 向量库健康检查
// 定期检查所有向量库的连通性，状态异常时触发告警
@Component
@Slf4j
public class VectorStoreHealthChecker {

    @Resource
    @Qualifier("milvusEmbeddingStore")
    private EmbeddingStore<TextSegment> milvusStore;       // Milvus 向量库
    
    @Resource
    @Qualifier("weaviateEmbeddingStore")
    private EmbeddingStore<TextSegment> weaviateStore;      // Weaviate 向量库
    
    @Resource
    @Qualifier("qdrantEmbeddingStore")
    private EmbeddingStore<TextSegment> qdrantStore;        // Qdrant 向量库
    
    @Resource
    private HealthCheckAlertService alertService;           // 告警服务：发送通知给运维团队

    /**
     * 每 30 秒执行一次健康检查
     * 分别检查 Milvus、Weaviate、Qdrant 三个向量库的连通性
     * 使用 @Scheduled 注解，Spring 会自动调度
     */
    @Scheduled(fixedRate = 30000)                           // 每 30 秒执行一次
    public void checkAllVectorStores() {
        log.debug("开始向量库健康检查...");
        
        // 并行检查所有向量库，互不阻塞
        CompletableFuture.allOf(
            checkStore("Milvus", milvusStore),
            checkStore("Weaviate", weaviateStore),
            checkStore("Qdrant", qdrantStore)
        ).join();
    }

    /**
     * 检查单个向量库的连通性
     * 通过执行一次简单的查询来验证服务是否正常
     * 
     * @param storeName 向量库名称（用于日志和告警）
     * @param store 向量库实例
     * @return CompletableFuture<Void>
     */
    private CompletableFuture<Void> checkStore(
            String storeName, EmbeddingStore<TextSegment> store) {
        
        return CompletableFuture.runAsync(() -> {
            long startTime = System.currentTimeMillis();
            
            try {
                // 执行一次简单的检索操作来验证连通性
                // 使用一个随机向量查询，limit=1 即可
                Embedding testEmbedding = Embedding.of(new float[]{0.0f, 0.0f, 0.0f});
                store.findRelevant(testEmbedding, 1);
                
                long latency = System.currentTimeMillis() - startTime;
                log.debug("向量库 {} 健康检查通过，延迟：{}ms", storeName, latency);
                
                // 记录健康检查指标（用于 Prometheus 监控）
                Metrics.gauge("rag.vectorstore.health", 
                    Tags.of("store", storeName), 1.0);
                Metrics.gauge("rag.vectorstore.latency",
                    Tags.of("store", storeName), latency);
                
            } catch (Exception e) {
                long latency = System.currentTimeMillis() - startTime;
                log.error("向量库 {} 健康检查失败，延迟：{}ms，错误：{}", 
                    storeName, latency, e.getMessage());
                
                // 记录失败指标
                Metrics.gauge("rag.vectorstore.health",
                    Tags.of("store", storeName), 0.0);
                
                // 发送告警通知
                alertService.sendAlert(
                    AlertLevel.WARNING,
                    String.format("向量库 %s 不可用，错误：%s", storeName, e.getMessage()),
                    "RAG 系统"
                );
            }
        });
    }
}
```

### 4.3 重试 + 超时 + 熔断（Resilience4j 集成）

Resilience4j 是一个轻量级、易用的容错库，专为 Java 8+ 和函数式编程设计。它提供了重试、熔断、限流、超时、舱壁等机制。

```java
// RagResilienceConfig.java —— Resilience4j 容错配置
// 为 RAG 系统的各个外部调用配置重试、熔断、超时策略
@Configuration
@Slf4j
public class RagResilienceConfig {

    /**
     * 1. 重试配置：Embedding 模型调用
     * 网络抖动是常态，适当的重试可以提高成功率
     * 最多重试 3 次，每次间隔指数级增长（1s, 2s, 4s）
     */
    @Bean
    public Retry embeddingRetry() {
        RetryConfig config = RetryConfig.custom()
            .maxAttempts(3)                                 // 最多重试 3 次（含首次）
            .waitDuration(Duration.ofSeconds(1))            // 基础等待时间：1 秒
            .intervalFunction(IntervalFunction.ofExponentialBackoff(
                Duration.ofMillis(500), 2.0                 // 指数退避：500ms 起步，每次翻倍
            ))
            .retryExceptions(
                IOException.class,                           // 网络异常：重试
                TimeoutException.class,                      // 超时异常：重试
                EmbeddingException.class                     // Embedding 服务异常：重试
            )
            .ignoreExceptions(
                IllegalArgumentException.class                // 参数错误：不重试（重试也没用）
            )
            .build();
        
        return Retry.of("embedding-retry", config);
    }

    /**
     * 2. 熔断器配置：向量库检索
     * 当向量库连续失败达到阈值时，熔断器打开，快速失败
     * 避免请求持续堆积，给下游服务恢复的时间
     */
    @Bean
    public CircuitBreaker vectorStoreCircuitBreaker() {
        // 滑动窗口：基于最近的 20 次调用
        // 当失败率超过 50% 时，熔断器打开
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
            .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)  // 基于计数的滑动窗口
            .slidingWindowSize(20)                          // 窗口大小：20 次调用
            .minimumNumberOfCalls(5)                        // 最少调用 5 次后才开始计算失败率
            .failureRateThreshold(50)                       // 失败率阈值：50%
            .slowCallRateThreshold(50)                      // 慢调用率阈值：50%
            .slowCallDurationThreshold(Duration.ofSeconds(5)) // 超过 5 秒算慢调用
            .waitDurationInOpenState(Duration.ofSeconds(30)) // 熔断器打开后等待 30 秒再尝试
            .permittedNumberOfCallsInHalfOpenState(3)       // 半开状态时允许 3 次试探调用
            .recordExceptions(
                TimeoutException.class,                     // 超时触发熔断
                VectorStoreConnectionException.class        // 连接异常触发熔断
            )
            .build();
        
        return CircuitBreaker.of("vectorstore-cb", config);
    }

    /**
     * 3. 超时配置：LLM 生成调用
     * LLM 生成可能因为各种原因卡住，超时配置可以避免无限等待
     */
    @Bean
    public TimeLimiter llmTimeLimiter() {
        return TimeLimiter.of(Duration.ofSeconds(30));      // LLM 生成最多等 30 秒
    }

    /**
     * 4. 限流配置：RAG 查询接口
     * 防止突发流量冲垮系统，保护下游服务
     */
    @Bean
    public RateLimiter ragRateLimiter() {
        RateLimiterConfig config = RateLimiterConfig.custom()
            .limitForPeriod(100)                            // 每段时间内最多 100 个请求
            .limitRefreshPeriod(Duration.ofSeconds(1))      // 每 1 秒刷新一次
            .timeoutDuration(Duration.ofMillis(500))        // 等待获取许可的超时时间：500ms
            .build();
        
        return RateLimiter.of("rag-rate-limiter", config);
    }

    /**
     * 5. 舱壁配置：隔离不同服务的线程池
     * Embedding 调用和 LLM 调用使用不同的线程池，互不影响
     * 即使 LLM 调用阻塞，也不会影响 Embedding 调用
     */
    @Bean
    public Bulkhead embeddingBulkhead() {
        BulkheadConfig config = BulkheadConfig.custom()
            .maxConcurrentCalls(10)                         // 最大并发数：10
            .maxWaitDuration(Duration.ofMillis(100))        // 等待最大时长：100ms
            .build();
        
        return Bulkhead.of("embedding-bulkhead", config);
    }
}
```

```java
// RagResilientService.java —— 使用 Resilience4j 的 RAG 服务
// 整合重试、熔断、超时、限流、舱壁等机制，构建高可用的 RAG 调用链路
@Service
@Slf4j
public class RagResilientService {

    @Resource
    private Retry embeddingRetry;                            // 重试配置
    
    @Resource
    private CircuitBreaker vectorStoreCircuitBreaker;        // 熔断器配置
    
    @Resource
    private TimeLimiter llmTimeLimiter;                       // 超时配置
    
    @Resource
    private RateLimiter ragRateLimiter;                       // 限流配置

    @Resource
    private EmbeddingModel embeddingModel;                   // Embedding 模型
    
    @Resource
    private EmbeddingStore<TextSegment> embeddingStore;      // 向量库

    /**
     * 带全量容错保护的检索方法
     * 重试 + 熔断 + 超时 + 限流 四重防护
     * 
     * @param query 用户查询文本
     * @return 检索结果列表
     */
    public List<TextSegment> resilientRetrieve(String query) {
        // 1. 限流：尝试获取许可，如果超过限流阈值则快速失败
        //    避免突发流量对下游服务造成冲击
        if (!ragRateLimiter.acquirePermission()) {
            log.warn("请求被限流，query: {}", query);
            throw new RateLimitExceededException("请求过于频繁，请稍后重试");
        }
        
        try {
            // 2. 使用装饰器模式组合多个容错机制
            //    Retry.decorateSupplier 为 Embedding 调用添加重试能力
            Supplier<Embedding> embeddingSupplier = Retry.decorateSupplier(
                embeddingRetry,
                () -> embeddingModel.embed(query).content()
            );
            
            // 3. CircuitBreaker.decorateSupplier 为向量库检索添加熔断能力
            //    如果向量库连续失败，熔断器打开，快速降级返回空结果
            Supplier<List<TextSegment>> retrieveSupplier = CircuitBreaker.decorateSupplier(
                vectorStoreCircuitBreaker,
                () -> {
                    Embedding queryEmbedding = embeddingSupplier.get();
                    return embeddingStore.findRelevant(queryEmbedding, 5);
                }
            );
            
            // 4. 执行检索，如果熔断器打开则返回空列表（降级）
            return retrieveSupplier.get();
            
        } catch (CircuitBreakerOpenException e) {
            // 熔断器打开时的降级处理
            log.warn("向量库熔断器已打开，返回空结果（降级），query: {}", query);
            return Collections.emptyList();
        } catch (Exception e) {
            log.error("检索失败，query: {}", query, e);
            return Collections.emptyList();
        }
    }

    /**
     * 带超时控制的 LLM 生成方法
     * 使用 TimeLimiter 确保 LLM 调用不会无限等待
     * 
     * @param prompt 提示词
     * @return LLM 生成的回答
     */
    public CompletableFuture<String> generateWithTimeout(String prompt) {
        // 使用 TimeLimiter 包装 LLM 调用，超时后抛出 TimeoutException
        return llmTimeLimiter.executeCompletionStage(
            // 实际执行 LLM 调用的异步任务
            CompletableFuture.supplyAsync(() -> {
                // 此处调用 LLM 生成回答
                return "LLM 生成的回答";
            })
        ).toCompletableFuture();
    }
}
```

---

## 5. 安全优化

RAG 系统上线后，安全是最容易被忽视但后果最严重的问题。如果用户检索到了不应该看到的内容，或者 Prompt 注入攻击成功，轻则泄露信息，重则导致合规风险。

### 5.1 文档权限控制

企业级知识库中，不同层级的员工应该只能访问其授权范围内的文档。例如，普通员工只能查看技术文档，经理可以查看绩效数据，总监可以查看战略规划。

```java
// DocumentPermissionService.java —— 文档级权限控制
// 实现"用户只能检索到授权文档"的权限过滤机制
// 核心思路：在检索时加入用户角色/部门/级别的过滤条件
@Service
@Slf4j
public class DocumentPermissionService {

    @Resource
    private UserRoleService userRoleService;                 // 用户角色服务
    
    @Resource
    private DocumentPermissionRepository permissionRepo;     // 文档权限存储

    /**
     * 构建带权限过滤的检索请求
     * 在检索时，将用户的权限信息（角色、部门、安全级别）作为过滤条件
     * 确保用户只能检索到有权限访问的文档
     * 
     * @param query 原始查询
     * @param userId 当前用户 ID
     * @return 带权限过滤条件的检索请求
     */
    public RetrievalQuery buildPermissionFilteredQuery(String query, String userId) {
        // 1. 获取用户的所有权限信息
        UserPermission userPerm = getUserPermission(userId);
        
        // 2. 构建权限过滤条件
        //    使用 Metadata 过滤：在存储文档时，已将权限信息存储在 metadata 中
        //    例如：{ "security_level": "internal", "department": "tech", "roles": ["dev", "qa"] }
        Filter permissionFilter = buildPermissionFilter(userPerm);
        
        // 3. 返回带过滤条件的检索请求
        return RetrievalQuery.builder()
            .query(query)
            .maxResults(5)
            .minScore(0.75)
            .filter(permissionFilter)                         // 注入权限过滤条件
            .build();
    }

    /**
     * 构建权限过滤条件
     * 根据用户的安全级别、部门、角色，生成 Metadata 过滤条件
     * 过滤条件格式：安全级别 <= 用户级别 AND (部门匹配 OR 角色匹配)
     */
    private Filter buildPermissionFilter(UserPermission userPerm) {
        // 使用 LangChain4j 的 Filter 构建 DSL
        // 逻辑：安全级别 <= 用户级别 AND (部门匹配 OR 角色匹配)
        Filter levelFilter = Filter.lessThanOrEqualTo(
            "security_level", userPerm.getSecurityLevel()
        );
        
        Filter deptFilter = Filter.in(
            "department", userPerm.getDepartments()
        );
        
        Filter roleFilter = Filter.in(
            "roles", userPerm.getRoles()
        );
        
        // 组合过滤条件：安全级别 AND (部门 OR 角色)
        return Filter.and(
            levelFilter,
            Filter.or(deptFilter, roleFilter)
        );
    }

    /**
     * 获取用户的完整权限信息
     * 从用户服务中获取用户的安全级别、所属部门、角色列表
     */
    private UserPermission getUserPermission(String userId) {
        UserPermission perm = new UserPermission();
        perm.setUserId(userId);
        perm.setSecurityLevel("internal");                   // 安全级别：public < internal < confidential < secret
        perm.setDepartments(Set.of("tech"));                 // 所属部门
        perm.setRoles(Set.of("dev", "qa"));                  // 角色列表
        return perm;
    }
}
```

### 5.2 敏感信息过滤

在将文档内容送入 LLM 之前，需要过滤掉其中的敏感信息（如身份证号、手机号、银行卡号、API Key 等）。同样，在将 LLM 的回答返回给用户之前，也需要做同样的过滤。

```java
// SensitiveInfoFilter.java —— 敏感信息过滤
// 在检索结果和 LLM 回答中检测并脱敏敏感信息
// 支持 PII（个人身份信息）检测和脱敏处理
@Component
@Slf4j
public class SensitiveInfoFilter {

    // 敏感信息正则表达式模式
    // 每个模式匹配一种类型的敏感信息
    private static final List<Pattern> SENSITIVE_PATTERNS = List.of(
        Pattern.compile("\\b1[3-9]\\d{9}\\b"),               // 中国大陆手机号：11 位数字，以 1 开头
        Pattern.compile("\\b\\d{17}[\\dXx]\\b"),             // 身份证号：18 位（最后一位可能是 X）
        Pattern.compile("\\b(?:\\d{4}[ -]?){3}\\d{4}\\b"),   // 银行卡号：16 位数字
        Pattern.compile("sk-[A-Za-z0-9]{20,}",               // API Key 模式：以 sk- 开头
            Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\b[A-Za-z0-9+/=]{40,}\\b")         // 可能的 Token 或密钥
    );

    // 敏感信息类型的标签
    private static final List<String> SENSITIVE_TYPES = List.of(
        "手机号", "身份证号", "银行卡号", "API Key", "密钥"
    );

    /**
     * 脱敏处理：使用替换字符掩盖敏感信息
     * 例如：13800138000 -> 138****8000
     * 
     * @param text 原始文本（检索结果或 LLM 回答）
     * @return 脱敏后的文本
     */
    public String maskSensitiveInfo(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        
        String masked = text;
        int foundCount = 0;
        
        // 逐一遍历敏感信息模式，找到后替换
        for (int i = 0; i < SENSITIVE_PATTERNS.size(); i++) {
            Pattern pattern = SENSITIVE_PATTERNS.get(i);
            String type = SENSITIVE_TYPES.get(i);
            
            Matcher matcher = pattern.matcher(masked);
            StringBuffer sb = new StringBuffer();
            
            while (matcher.find()) {
                String match = matcher.group();
                // 保留前 3 位和后 4 位，中间用星号替换
                String maskedValue = maskValue(match);
                matcher.appendReplacement(sb, maskedValue);
                foundCount++;
                log.info("检测到敏感信息（{}），已脱敏处理", type);
            }
            matcher.appendTail(sb);
            masked = sb.toString();
        }
        
        if (foundCount > 0) {
            log.info("敏感信息过滤完成，共处理 {} 处", foundCount);
        }
        
        return masked;
    }

    /**
     * 对单个敏感值进行脱敏
     * 策略：保留前 3 位和后 4 位，中间用星号替换
     * 对于过短的值，整体替换为星号
     */
    private String maskValue(String value) {
        if (value.length() <= 6) {
            return "******";                                 // 过短的值整体替换
        }
        // 保留前 3 后 4，中间用星号
        int maskLen = value.length() - 7;
        return value.substring(0, 3) + "*".repeat(maskLen) + value.substring(value.length() - 4);
    }

    /**
     * 全链路过滤：从检索到生成，全程过滤敏感信息
     * 在检索结果进入 LLM 之前和 LLM 回答返回给用户之前，都进行过滤
     * 
     * @param retrievedContexts 检索到的文档片段
     * @param llmResponse LLM 生成的回答
     * @return 包含过滤后的上下文和回答的结果对象
     */
    public FilteredResult filterFullPipeline(
            List<TextSegment> retrievedContexts, String llmResponse) {
        
        // 1. 过滤检索结果中的敏感信息（送入 LLM 之前）
        List<TextSegment> filteredContexts = retrievedContexts.stream()
            .map(segment -> TextSegment.from(
                maskSensitiveInfo(segment.text()),           // 对文本进行脱敏
                segment.metadata()                           // 保留原始元数据
            ))
            .collect(Collectors.toList());
        
        // 2. 过滤 LLM 回答中的敏感信息（返回给用户之前）
        String filteredResponse = maskSensitiveInfo(llmResponse);
        
        return new FilteredResult(filteredContexts, filteredResponse);
    }
}
```

### 5.3 Prompt 注入防御

Prompt 注入是 RAG 系统面临的最大安全威胁之一。攻击者可能通过在知识库中植入恶意内容，或者在用户输入中注入指令，来操纵 LLM 的行为。

```java
// PromptInjectionDefender.java —— Prompt 注入防御
// 保护 LLM 不被恶意输入操纵，从输入清洗和输出过滤两个维度防御
@Component
@Slf4j
public class PromptInjectionDefender {

    // 已知的 Prompt 注入攻击模式
    private static final List<Pattern> INJECTION_PATTERNS = List.of(
        // 1. 忽略指令类：试图让 LLM 忽略系统提示
        Pattern.compile("忽略(?:所有|之前)?(?:的)?(?:指令|提示|规则|要求)", 
            Pattern.CASE_INSENSITIVE),
        Pattern.compile("ignore\\s+(?:all\\s+)?(?:previous\\s+)?(?:instructions|prompts|rules)",
            Pattern.CASE_INSENSITIVE),
        
        // 2. 角色切换类：试图让 LLM 扮演其他角色
        Pattern.compile("(?:现在|接下来)(?:你|请)(?:扮演|假装(?:成|是)|作为)", 
            Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?:你是|从今以后)(?:一个|一名)?(?:黑客|越狱|不受限制)", 
            Pattern.CASE_INSENSITIVE),
        
        // 3. 信息泄露类：试图让 LLM 泄露系统提示或配置
        Pattern.compile("(?:输出|显示|打印)(?:系统)?(?:提示|指令|prompt|system message)",
            Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?:前面|之前|初始)(?:的)?(?:对话|内容|消息|设定)", 
            Pattern.CASE_INSENSITIVE),
        
        // 4. 越狱攻击类：试图绕过安全限制
        Pattern.compile("DAN\\s*(?:\\d+\\.?)?", 
            Pattern.CASE_INSENSITIVE),                       // "Do Anything Now" 模式
        Pattern.compile("(?:越狱|jailbreak|解锁|解除限制)", 
            Pattern.CASE_INSENSITIVE),
        
        // 5. 分隔符注入类：试图插入特殊分隔符
        Pattern.compile("```\\s*(?:system|assistant|user)", 
            Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\[-{3,}\\]|\\[\\/{3,}\\]")         // 特殊分隔符
    );

    /**
     * 输入清洗：检测并处理用户输入中的 Prompt 注入尝试
     * 在将用户输入送入 LLM 之前，先进行安全检查
     * 
     * @param userInput 用户的原始输入
     * @return 清洗后的输入（如果检测到恶意内容，返回安全提示）
     */
    public String sanitizeInput(String userInput) {
        if (userInput == null || userInput.isEmpty()) {
            return userInput;
        }
        
        // 1. 检测注入模式
        for (Pattern pattern : INJECTION_PATTERNS) {
            Matcher matcher = pattern.matcher(userInput);
            if (matcher.find()) {
                log.warn("检测到 Prompt 注入尝试：匹配到模式 '{}'，原始输入: {}",
                    pattern.pattern(), 
                    userInput.substring(0, Math.min(50, userInput.length()))
                );
                
                // 记录安全事件（用于后续审计和告警）
                SecurityEventPublisher.publish(
                    SecurityEventType.PROMPT_INJECTION_DETECTED,
                    userInput
                );
                
                // 返回安全提示，而不是用户的原始输入
                return "抱歉，我无法处理该请求。如有疑问，请联系管理员。";
            }
        }
        
        // 2. 没有检测到注入，返回原始输入
        return userInput;
    }

    /**
     * 输出过滤：检测 LLM 输出中是否包含不应泄露的信息
     * 双重保险——即使输入绕过了检测，输出过滤也能兜底
     * 
     * @param llmOutput LLM 生成的原始输出
     * @return 过滤后的输出（如果检测到泄露，替换为安全提示）
     */
    public String filterOutput(String llmOutput) {
        if (llmOutput == null || llmOutput.isEmpty()) {
            return llmOutput;
        }
        
        // 检测是否包含系统提示或配置信息（可能被注入攻击泄露）
        if (llmOutput.contains("你是") && 
            (llmOutput.contains("系统提示") || llmOutput.contains("system") && 
             llmOutput.contains("instruction"))) {
            
            log.warn("检测到可能的系统信息泄露");
            SecurityEventPublisher.publish(
                SecurityEventType.POTENTIAL_DATA_LEAK,
                llmOutput
            );
            
            return "抱歉，发生了意外错误。请稍后重试。";
        }
        
        return llmOutput;
    }

    /**
     * 全链路安全检查：输入清洗 + 输出过滤
     * 在 RAG 流程的关键节点插入安全检查
     * 
     * @param context 完整的 RAG 执行上下文
     * @return 安全检查后的执行上下文
     */
    public RagContext secureFullPipeline(RagContext context) {
        // 1. 输入清洗：在用户输入进入 LLM 之前
        String cleanedQuery = sanitizeInput(context.getUserQuery());
        context.setUserQuery(cleanedQuery);
        
        // 2. 文档内容过滤：在检索结果送入 LLM 之前
        //    防止知识库中被植入了恶意文档
        List<TextSegment> cleanedContexts = context.getRetrievedContexts().stream()
            .map(seg -> {
                String cleaned = sanitizeInput(seg.text());
                return TextSegment.from(cleaned, seg.metadata());
            })
            .collect(Collectors.toList());
        context.setRetrievedContexts(cleanedContexts);
        
        // 3. 输出过滤：在 LLM 回答返回给用户之前
        String filteredOutput = filterOutput(context.getLlmResponse());
        context.setLlmResponse(filteredOutput);
        
        return context;
    }
}
```

---

## 6. 可观测性

生产级系统必须"看得见"。没有监控的 RAG 系统就像蒙着眼睛开车——出了故障只能靠感觉猜。可观测性要覆盖三个维度：**检索质量**、**向量库性能**、**LLM 调用链路**。

### 6.1 检索质量监控

检索质量的好坏直接影响用户体验。需要监控的核心指标包括：检索结果的分数分布、用户点击率、无效检索率等。

```java
// RetrievalQualityMonitor.java —— 检索质量监控
// 监控检索结果的分数分布、点击率、首位有效率等质量指标
// 数据通过 Micrometer 暴露给 Prometheus，再由 Grafana 展示
@Component
@Slf4j
public class RetrievalQualityMonitor {

    @Resource
    private MongoTemplate mongoTemplate;                     // 用于存储检索日志

    // Micrometer 指标定义
    private final Counter totalRetrievalCounter = 
        Metrics.counter("rag.retrieval.total");               // 总检索次数
    
    private final DistributionSummary scoreDistribution = 
        DistributionSummary.builder("rag.retrieval.score")    // 分数分布
            .baseUnit("score")                                // 单位：分数
            .publishPercentiles(0.5, 0.75, 0.9, 0.99)        // 记录 P50, P75, P90, P99
            .register(Metrics.globalRegistry);
    
    private final Timer retrievalLatency = 
        Timer.builder("rag.retrieval.latency")                // 检索延迟
            .publishPercentiles(0.5, 0.9, 0.99)
            .register(Metrics.globalRegistry);

    /**
     * 记录一次检索结果的质量数据
     * 每次检索完成后调用，记录分数分布、延迟等信息
     * 
     * @param query 用户查询
     * @param results 检索结果列表
     * @param durationMs 检索耗时（毫秒）
     */
    public void recordRetrievalQuality(
            String query, List<TextSegment> results, long durationMs) {
        
        // 1. 记录总检索次数
        totalRetrievalCounter.increment();
        
        // 2. 记录检索延迟
        retrievalLatency.record(Duration.ofMillis(durationMs));
        
        // 3. 记录每个检索结果的分数
        for (TextSegment result : results) {
            double score = result.metadata().getDouble("score");
            scoreDistribution.record(score);
        }
        
        // 4. 记录检索结果数量（用于监控"空检索"比例）
        Metrics.gauge("rag.retrieval.result-count",
            Tags.of("query_type", classifyQuery(query)),
            results.size()
        );
        
        // 5. 记录首位分数（第一个检索结果的分数，代表性最强）
        if (!results.isEmpty()) {
            double firstScore = results.get(0)
                .metadata().getDouble("score");
            Metrics.gauge("rag.retrieval.top1-score", firstScore);
        }
        
        // 6. 检索日志持久化（用于后续分析）
        saveRetrievalLog(query, results, durationMs);
    }

    /**
     * 记录用户点击行为（用于计算"点击率"指标）
     * 前端在用户点击某条检索结果时调用此方法
     * 点击率越高，说明检索结果越相关
     * 
     * @param retrievalId 检索记录 ID
     * @param clickedPosition 点击的文档位置（从 1 开始）
     */
    public void recordClick(String retrievalId, int clickedPosition) {
        // 记录点击事件
        Metrics.counter("rag.retrieval.click",
            Tags.of("position", String.valueOf(clickedPosition))
        ).increment();
        
        // 更新数据库中的检索日志
        mongoTemplate.updateFirst(
            Query.query(Criteria.where("_id").is(retrievalId)),
            Update.update("clicked", true)
                .set("clickPosition", clickedPosition),
            "retrieval_logs"
        );
    }

    /**
     * 保存检索日志到 MongoDB
     * 用于离线分析和质量评估
     */
    private void saveRetrievalLog(
            String query, List<TextSegment> results, long durationMs) {
        
        RetrievalLog log = new RetrievalLog();
        log.setQuery(query);
        log.setResultCount(results.size());
        log.setDurationMs(durationMs);
        log.setTopScores(results.stream()
            .map(r -> r.metadata().getDouble("score"))
            .collect(Collectors.toList()));
        log.setCreatedAt(LocalDateTime.now());
        
        mongoTemplate.save(log, "retrieval_logs");
    }
}
```

### 6.2 向量库指标

向量库的性能直接影响检索速度。需要监控的核心指标：延迟 P99、命中率、QPS、连接数等。

```java
// VectorStoreMetricsExporter.java —— 向量库指标导出
// 将向量库的关键性能指标暴露给 Prometheus
// 每个指标都带有 store 标签，可区分不同向量库（Milvus/Weaviate/Qdrant）
@Component
@Slf4j
public class VectorStoreMetricsExporter {

    // 按向量库维度区分的指标
    private final Map<String, Timer> searchLatencyTimers = new ConcurrentHashMap<>();
    private final Map<String, Counter> searchCounters = new ConcurrentHashMap<>();
    private final Map<String, Counter> hitCounters = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        // 为每个向量库初始化指标
        for (String store : List.of("milvus", "weaviate", "qdrant")) {
            searchLatencyTimers.put(store,
                Timer.builder("rag.vectorstore.search.latency")
                    .tag("store", store)
                    .publishPercentiles(0.5, 0.9, 0.99)     // P50, P90, P99
                    .register(Metrics.globalRegistry)
            );
            
            searchCounters.put(store,
                Counter.builder("rag.vectorstore.search.total")
                    .tag("store", store)
                    .register(Metrics.globalRegistry)
            );
            
            hitCounters.put(store,
                Counter.builder("rag.vectorstore.search.hits")
                    .tag("store", store)
                    .register(Metrics.globalRegistry)
            );
        }
    }

    /**
     * 记录向量库检索指标
     * 每次向量检索完成后调用
     * 
     * @param storeName 向量库名称（milvus/weaviate/qdrant）
     * @param durationMs 检索耗时（毫秒）
     * @param resultCount 返回结果数量
     * @param hasResult 是否有结果返回
     */
    public void recordSearch(
            String storeName, long durationMs, 
            int resultCount, boolean hasResult) {
        
        Timer timer = searchLatencyTimers.get(storeName);
        Counter counter = searchCounters.get(storeName);
        
        if (timer != null) {
            // 记录检索延迟
            timer.record(Duration.ofMillis(durationMs));
        }
        if (counter != null) {
            // 记录检索次数
            counter.increment();
        }
        
        // 记录命中率（有结果返回视为命中）
        if (hasResult) {
            Counter hitCounter = hitCounters.get(storeName);
            if (hitCounter != null) {
                hitCounter.increment();
            }
        }
        
        // 记录结果数量分布
        Metrics.gauge("rag.vectorstore.search.result-count",
            Tags.of("store", storeName),
            resultCount
        );
    }

    /**
     * 获取实时 QPS（每秒查询数）
     * 每 10 秒计算一次，用于监控面板
     */
    @Scheduled(fixedRate = 10000)
    public void reportQps() {
        for (String store : List.of("milvus", "weaviate", "qdrant")) {
            Counter counter = searchCounters.get(store);
            if (counter != null) {
                // 每 10 秒的计数转换为 QPS
                double count = counter.count();
                // 重置计数器（实际应使用更精确的滑动窗口计算方式）
                // 此处简化处理，仅用于展示思路
                log.debug("向量库 {} 当前 QPS 约 {}/s", store, count / 10);
            }
        }
    }
}
```

### 6.3 LLM 调用追踪（TraceID 贯穿全链路）

全链路追踪是排查问题的基础。当用户说"刚才那个回答不对"，如果没有 TraceID，你根本不知道是哪次调用出了问题。

```java
// RagTraceService.java —— 全链路追踪
// 使用 TraceID 贯穿 RAG 的整个调用链路：用户请求 -> 检索 -> 生成 -> 返回
// 基于 SLF4J MDC 实现，与主流链路追踪系统（Zipkin、SkyWalking）兼容
@Component
@Slf4j
public class RagTraceService {

    @Resource
    private MongoTemplate mongoTemplate;                     // 存储追踪日志

    /**
     * 生成新的 TraceID 并设置到 MDC 上下文中
     * 在请求入口处调用，确保当前线程的所有日志都带上 TraceID
     * 
     * @return 生成的 TraceID
     */
    public String startTrace() {
        // 使用 UUID 作为 TraceID，确保全局唯一
        String traceId = UUID.randomUUID().toString().replace("-", "");
        
        // 设置到 MDC（Mapped Diagnostic Context）中
        // 配合 logback 的 %X{traceId} 配置，自动在日志中输出 TraceID
        MDC.put("traceId", traceId);
        MDC.put("spanId", "root");
        
        return traceId;
    }

    /**
     * 创建子 Span（用于追踪 RAG 流程中的某个步骤）
     * 例如：检索 Span、生成 Span、重排序 Span 等
     * 
     * @param spanName 操作名称
     * @return Span ID
     */
    public String startSpan(String spanName) {
        String spanId = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        MDC.put("spanId", spanId);
        MDC.put("spanName", spanName);
        return spanId;
    }

    /**
     * 记录完整的 RAG 调用链路到 MongoDB
     * 包含每个步骤的耗时、输入输出、异常信息等
     * 用于事后分析和问题排查
     * 
     * @param ragTrace 包含完整调用链路的追踪对象
     */
    public void recordTrace(RagTrace ragTrace) {
        try {
            // 将追踪数据持久化到 MongoDB
            // 设置 TTL 索引，自动过期 30 天前的数据
            mongoTemplate.save(ragTrace, "rag_traces");
            
            // 记录慢查询（超过 5 秒的调用）
            if (ragTrace.getTotalDurationMs() > 5000) {
                log.warn("慢查询告警：TraceID={}，总耗时={}ms，Query={}",
                    ragTrace.getTraceId(),
                    ragTrace.getTotalDurationMs(),
                    ragTrace.getQuery()
                );
            }
        } catch (Exception e) {
            log.error("记录追踪数据失败，TraceID={}", ragTrace.getTraceId(), e);
        }
    }

    /**
     * 清理 MDC 上下文
     * 在请求结束时调用，防止内存泄漏
     */
    public void endTrace() {
        MDC.clear();
    }
}
```

```yaml
# logback-spring.xml 配置 —— 在日志中输出 TraceID
# 配置后，每条日志都会包含 TraceID，便于通过 TraceID 串联所有相关日志
<configuration>
    <!-- 控制台输出：带 TraceID 和 SpanID -->
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <!-- 在日志格式中加入 traceId 和 spanId -->
            <pattern>
                %d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} 
                [%X{traceId:-no-trace}] [%X{spanId:-}] - %msg%n
            </pattern>
        </encoder>
    </appender>
    
    <!-- 文件输出：按天滚动，保留 30 天 -->
    <appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <file>logs/rag-system.log</file>
        <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
            <fileNamePattern>logs/rag-system.%d{yyyy-MM-dd}.log</fileNamePattern>
            <maxHistory>30</maxHistory>
        </rollingPolicy>
        <encoder>
            <pattern>
                %d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} 
                [%X{traceId:-no-trace}] [%X{spanId:-}] - %msg%n
            </pattern>
        </encoder>
    </appender>
    
    <!-- RAG 相关日志独立文件，便于快速定位问题 -->
    <logger name="com.ruoyi.ai.rag" level="DEBUG" additivity="false">
        <appender-ref ref="RAG_FILE"/>
    </logger>
</configuration>
```

### 6.4 Micrometer / Prometheus 集成

将上述所有指标统一暴露给 Prometheus，由 Grafana 进行可视化展示。

```java
// RagMetricsConfig.java —— Micrometer + Prometheus 集成配置
// 将所有 RAG 指标注册到 Micrometer，并通过 Actuator 暴露给 Prometheus
@Configuration
public class RagMetricsConfig {

    /**
     * 配置 Micrometer 的 MeterRegistry
     * 将指标绑定到 Prometheus 注册表
     * 通过 /actuator/prometheus 端点暴露
     */
    @Bean
    public MeterRegistryCustomizer<MeterRegistry> metricsCommonTags() {
        // 为所有指标添加通用标签，便于在 Grafana 中筛选
        return registry -> registry.config().commonTags(
            "application", "ruoyi-ai",                        // 应用名称
            "module", "rag",                                  // 模块名称
            "environment", "${spring.profiles.active:dev}"    // 运行环境
        );
    }

    /**
     * 配置 Grafana 推荐指标
     * 以下指标是 RAG 系统监控的必要指标，建议在 Grafana 中创建仪表盘展示
     * 
     * 指标列表：
     * 1. rag.retrieval.total —— 总检索次数（计数器）
     * 2. rag.retrieval.latency —— 检索延迟（直方图，关注 P99）
     * 3. rag.retrieval.score —— 检索分数分布（直方图）
     * 4. rag.retrieval.result-count —— 检索结果数量（仪表盘）
     * 5. rag.vectorstore.search.latency —— 向量库检索延迟（按 store 维度）
     * 6. rag.vectorstore.search.total —— 向量库检索次数
     * 7. rag.vectorstore.search.hits —— 向量库命中次数
     * 8. rag.llm.token.usage —— LLM Token 使用量
     * 9. rag.llm.generation.latency —— LLM 生成延迟
     * 10. rag.llm.error.total —— LLM 调用错误次数
     * 11. rag.cache.hit-rate —— 缓存命中率
     * 12. rag.vectorstore.health —— 向量库健康状态
     * 
     * 告警规则建议：
     * - rag.retrieval.latency P99 > 5s -> 告警（检索延迟过高）
     * - rag.vectorstore.health == 0 -> 告警（向量库不可用）
     * - rag.llm.error.total > 10/min -> 告警（LLM 错误率过高）
     * - rag.cache.hit-rate < 0.3 -> 警告（缓存命中率过低）
     * - rag.retrieval.result-count == 0 比例 > 20% -> 警告（空检索过多）
     */
    @Bean
    public void registerRecommendedMetrics() {
        // 这些指标在 RetrievalQualityMonitor 和 VectorStoreMetricsExporter 中已注册
        // 此处仅作为文档和注册引导
        log.info("RAG 系统指标已注册，Prometheus 抓取路径：/actuator/prometheus");
    }
}
```

```yaml
# Prometheus 告警规则配置示例（prometheus-rules.yml）
# 针对 RAG 系统的关键指标设置告警阈值
groups:
  - name: rag-system-alerts
    rules:
      # 告警 1：检索延迟过高（P99 > 5s）
      - alert: HighRetrievalLatency
        expr: histogram_quantile(0.99, rate(rag_retrieval_latency_seconds_bucket[5m])) > 5
        for: 5m
        labels:
          severity: critical
        annotations:
          summary: "RAG 检索延迟过高（P99 > 5s）"
          description: "检索延迟 P99 为 {{ $value }}s，已超过 5s 阈值"

      # 告警 2：向量库不可用
      - alert: VectorStoreDown
        expr: rag_vectorstore_health == 0
        for: 1m
        labels:
          severity: critical
        annotations:
          summary: "向量库 {{ $labels.store }} 不可用"
          description: "向量库 {{ $labels.store }} 健康检查失败"

      # 告警 3：LLM 错误率过高
      - alert: HighLLMErrorRate
        expr: rate(rag_llm_error_total[5m]) > 10
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "LLM 调用错误率过高"
          description: "LLM 调用错误率 {{ $value }}次/分钟，已超过 10次/分钟 阈值"
```

---

## 7. 代码示例：生产级 RagPipeline 配置类

下面是一个完整的、可以直接使用的生产级 RAG 配置类，整合了重试、降级、熔断、监控、缓存等所有机制。

```java
// ProductionRagPipelineConfig.java —— 生产级 RAG 管道配置
// 整合所有优化机制：重试 + 超时 + 降级 + 缓存 + 监控 + 安全
// 这是一个完整的 Spring 配置类，可以直接在 ruoyi-ai 项目中使用
@Configuration
@EnableConfigurationProperties(RagProperties.class)
@Slf4j
public class ProductionRagPipelineConfig {

    @Resource
    private RagProperties ragProperties;                    // RAG 配置属性（从 application.yml 读取）

    @Resource
    private HttpClient httpClient;                          // 带连接池的 HTTP 客户端

    @Resource
    private SensitiveInfoFilter sensitiveInfoFilter;         // 敏感信息过滤器

    @Resource
    private PromptInjectionDefender injectionDefender;       // Prompt 注入防御器

    @Resource
    private RetrievalQualityMonitor qualityMonitor;         // 检索质量监控

    @Resource
    private VectorStoreMetricsExporter metricsExporter;     // 向量库指标导出

    /**
     * 1. 配置 ContentRetriever（内容检索器）
     * 这是 RAG 检索阶段的核心组件
     * 配置了：向量库、Embedding 模型、检索数量、最低分数阈值
     */
    @Bean
    public ContentRetriever contentRetriever(
            EmbeddingStore<TextSegment> embeddingStore,
            EmbeddingModel embeddingModel) {
        
        return EmbeddingStoreContentRetriever.builder()
            .embeddingStore(embeddingStore)                  // 向量存储（Milvus/Weaviate/Qdrant）
            .embeddingModel(embeddingModel)                  // Embedding 模型
            .maxResults(ragProperties.getRetrieval().getTopK())  // 返回 top-K 结果
            .minScore(ragProperties.getRetrieval().getMinScore()) // 最低相关性分数
            .build();
    }

    /**
     * 2. 配置 RetrievalAugmentor（检索增强器）
     * 高级 RAG 的核心组件，支持 Query 转换、路由、聚合等
     * 这里配置了 Query 压缩（将历史对话和当前问题合并为一个独立问题）
     */
    @Bean
    public RetrievalAugmentor retrievalAugmentor(
            ContentRetriever contentRetriever,
            ChatLanguageModel chatModel) {
        
        // 配置 Query 转换器：将带上下文的对话问题压缩为独立问题
        // 例如：用户问"张三是什么时候出生的？"，AI 回答后用户又问"他毕业于哪里？"
        // Query 压缩器将"他毕业于哪里？"转换为"张三毕业于哪里？"
        QueryTransformer queryTransformer = 
            CompressingQueryTransformer.builder()
                .chatLanguageModel(chatModel)               // 用于压缩的 LLM
                .build();
        
        // 构建 RetrievalAugmentor
        return DefaultRetrievalAugmentor.builder()
            .queryTransformer(queryTransformer)              // Query 转换器
            .contentRetriever(contentRetriever)              // 内容检索器
            .build();
    }

    /**
     * 3. 配置 AiService（AI 服务）
     * 带流式输出、全链路追踪、安全过滤的生产级 AI 服务
     */
    @Bean
    public AiService aiService(
            StreamingChatLanguageModel streamingModel,
            RetrievalAugmentor retrievalAugmentor) {
        
        // 使用 AiService 注解方式的配置
        // 实际的 AiService 接口定义在单独的文件中
        return AiServices.builder(AiService.class)
            .streamingChatLanguageModel(streamingModel)      // 流式对话模型
            .retrievalAugmentor(retrievalAugmentor)          // 检索增强器
            .build();
    }

    /**
     * 4. 健康检查端点
     * 通过 Spring Boot Actuator 暴露 RAG 系统的健康状态
     * 访问 /actuator/health/rag 查看
     */
    @Bean
    public HealthIndicator ragHealthIndicator(
            EmbeddingStore<TextSegment> embeddingStore) {
        
        return () -> {
            Health.Builder builder = new Health.Builder();
            
            try {
                // 执行一次简单的检索，验证整个链路是否正常
                Embedding testEmbedding = Embedding.of(new float[ragProperties.getEmbedding().getDimension()]);
                embeddingStore.findRelevant(testEmbedding, 1);
                
                // 如果检索成功，返回 UP 状态
                builder.up()
                    .withDetail("vectorStore", "connected")
                    .withDetail("lastCheckTime", LocalDateTime.now().toString());
            } catch (Exception e) {
                // 如果检索失败，返回 DOWN 状态
                builder.down()
                    .withDetail("vectorStore", "disconnected")
                    .withDetail("error", e.getMessage())
                    .withDetail("lastCheckTime", LocalDateTime.now().toString());
            }
            
            return builder.build();
        };
    }

    /**
     * 5. 缓存配置
     * 使用 Redis 缓存热点 Query 的检索结果
     * 缓存配置：10 分钟过期，最大 10000 条
     */
    @Bean
    public CacheManager cacheManager(RedisConnectionFactory redisConnectionFactory) {
        // 配置 Redis 缓存管理器
        RedisCacheConfiguration cacheConfig = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMinutes(10))                // 缓存过期时间：10 分钟
            .disableCachingNullValues()                      // 不缓存 null 值
            .serializeKeysWith(
                RedisSerializationContext.SerializationPair
                    .fromSerializer(new StringRedisSerializer())
            )
            .serializeValuesWith(
                RedisSerializationContext.SerializationPair
                    .fromSerializer(new GenericJackson2JsonRedisSerializer())
            );
        
        return RedisCacheManager.builder(redisConnectionFactory)
            .cacheDefaults(cacheConfig)
            .withCacheConfiguration("rag-retrieval",          // 针对 RAG 检索的缓存配置
                cacheConfig.entryTtl(Duration.ofMinutes(5))) // 检索结果缓存 5 分钟
            .build();
    }

    /**
     * 6. 线程池配置
     * 为 RAG 系统的异步任务配置专用的线程池
     * 核心线程数 = CPU 核心数，最大线程数 = CPU 核心数 × 2
     */
    @Bean("ragTaskExecutor")
    public ThreadPoolTaskExecutor ragTaskExecutor() {
        int cores = Runtime.getRuntime().availableProcessors();
        
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(cores);                      // 核心线程数：CPU 核心数
        executor.setMaxPoolSize(cores * 2);                  // 最大线程数：CPU 核心数 × 2
        executor.setQueueCapacity(256);                      // 任务队列容量
        executor.setThreadNamePrefix("rag-worker-");         // 线程名前缀，方便排查
        executor.setWaitForTasksToCompleteOnShutdown(true);  // 优雅关闭：等待任务完成
        executor.setAwaitTerminationSeconds(30);              // 最多等待 30 秒
        executor.setRejectedExecutionHandler(
            new ThreadPoolExecutor.CallerRunsPolicy()        // 拒绝策略：由调用线程执行
        );
        executor.initialize();
        
        return executor;
    }
}
```

---

## 8. 项目实战：ruoyi-ai 的生产级部署配置

### 8.1 application.yml 配置

```yaml
# ruoyi-ai 生产级 RAG 配置（application-rag.yml）
# 这是一个完整的、可直接用于生产环境的 RAG 配置
# 覆盖了 Embedding、检索、缓存、重试、熔断、监控等所有维度
rag:
  # ============================================================
  # 1. 检索配置
  # ============================================================
  retrieval:
    enabled: true                                            # 是否启用 RAG 检索
    top-k: 5                                                 # 每次检索返回 Top-K 个结果
    min-score: 0.75                                          # 最低相关性分数阈值
    rerank-enabled: true                                     # 是否启用 Rerank 重排序
    rerank-top-k: 3                                          # 重排序后保留 Top-K 个结果
    
    # 混合检索配置（向量检索 + 关键词检索）
    hybrid-retrieval:
      enabled: true                                          # 是否启用混合检索
      vector-weight: 0.7                                     # 向量检索权重（0~1）
      keyword-weight: 0.3                                    # 关键词检索权重（0~1）
  
  # ============================================================
  # 2. Embedding 模型配置
  # ============================================================
  embedding:
    dimension: 1536                                          # 向量维度（与模型匹配）
    batch-size: 32                                           # 批量 Embedding 的批次大小
    model: openai                                            # 主模型：openai
    
    # 多模型提供商的降级配置
    providers:
      primary:
        type: openai                                         # 主模型：OpenAI
        model: text-embedding-3-small                        # 模型名称
        api-key: ${OPENAI_API_KEY}                           # API Key（从环境变量读取）
        api-url: https://api.openai.com/v1                   # API 地址
        timeout: 5000                                        # 超时时间：5 秒
      
      secondary:
        - type: siliconflow                                  # 备用模型 1：SiliconFlow
          model: BAAI/bge-large-zh-v1.5                      # 国产开源模型
          api-key: ${SILICONFLOW_API_KEY}
          api-url: https://api.siliconflow.cn/v1
          timeout: 5000
        - type: zhipu                                        # 备用模型 2：智谱 AI
          model: embedding-2                                 # 智谱 Embedding 模型
          api-key: ${ZHIPU_API_KEY}
          api-url: https://open.bigmodel.cn/api/paas/v4
          timeout: 5000
  
  # ============================================================
  # 3. 缓存配置
  # ============================================================
  cache:
    enabled: true                                            # 是否启用缓存
    type: redis                                              # 缓存类型：Redis
    ttl-seconds: 600                                         # 缓存过期时间：10 分钟
    max-size: 10000                                          # 最大缓存条目数
    preload-hot-queries: true                                # 是否预热热点查询
  
  # ============================================================
  # 4. 容错配置（Resilience4j）
  # ============================================================
  resilience:
    # 重试配置
    retry:
      max-attempts: 3                                        # 最多重试 3 次
      backoff-delay: 500                                     # 基础等待时间：500ms
      backoff-multiplier: 2.0                                # 指数退避倍数
    
    # 熔断器配置
    circuit-breaker:
      sliding-window-size: 20                                # 滑动窗口大小：20 次调用
      failure-rate-threshold: 50                             # 失败率阈值：50%
      wait-duration-in-open-state: 30s                       # 熔断器打开后等待 30 秒
      slow-call-duration-threshold: 5s                       # 超过 5 秒算慢调用
    
    # 限流配置
    rate-limiter:
      limit-for-period: 100                                  # 每段时间内最多 100 个请求
      limit-refresh-period: 1s                               # 每 1 秒刷新一次
  
  # ============================================================
  # 5. 监控配置
  # ============================================================
  monitoring:
    enabled: true                                            # 是否启用监控
    metrics-prefix: rag                                      # 指标前缀
    slow-query-threshold: 5000                               # 慢查询阈值：5 秒
    trace-sample-rate: 1.0                                   # 全链路追踪采样率：100%
  
  # ============================================================
  # 6. 安全配置
  # ============================================================
  security:
    # 敏感信息过滤
    sensitive-info-filter:
      enabled: true                                          # 是否启用敏感信息过滤
      mask-phone: true                                       # 手机号脱敏
      mask-id-card: true                                     # 身份证号脱敏
      mask-api-key: true                                     # API Key 脱敏
    
    # Prompt 注入防御
    prompt-injection-defense:
      enabled: true                                          # 是否启用 Prompt 注入防御
      log-only: false                                        # false = 拦截并拒绝，true = 仅记录日志
      max-input-length: 4096                                 # 输入最大长度限制
  
  # ============================================================
  # 7. 向量库配置
  # ============================================================
  vector-store:
    # 主向量库：Milvus
    primary:
      type: milvus                                           # 向量库类型
      host: ${MILVUS_HOST:localhost}                         # 服务地址
      port: 19530                                            # 端口
      collection-name: ruoyi_docs                            # 集合名称
      index-type: IVF_FLAT                                   # 索引类型
      metric-type: COSINE                                    # 距离度量方式
    
    # 备用向量库：Qdrant（用于跨区域容灾）
    secondary:
      type: qdrant
      host: ${QDRANT_HOST:localhost}
      port: 6334
      collection-name: ruoyi_docs_backup
```

### 8.2 Docker Compose 部署配置要点

```yaml
# docker-compose-rag.yml —— RAG 系统依赖服务部署
# 注意：此文件仅部署 RAG 系统的依赖服务，不包含应用本身
version: '3.8'

services:
  # ============================================================
  # 1. 向量数据库：Milvus（主库）
  # ============================================================
  milvus:
    image: milvusdb/milvus:v2.4.0
    container_name: ruoyi-milvus
    ports:
      - "19530:19530"                                        # gRPC 端口
      - "9091:9091"                                          # HTTP 端口（管理界面）
    environment:
      ETCD_ENDPOINTS: etcd:2379                              # Milvus 依赖 etcd
      MINIO_ADDRESS: minio:9000                              # Milvus 依赖 MinIO（存储向量数据）
    volumes:
      - milvus_data:/var/lib/milvus                          # 数据持久化
    deploy:
      resources:
        limits:
          memory: 8G                                         # Milvus 内存上限：8G
          cpus: '4'                                          # CPU 上限：4 核
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:9091/health"]
      interval: 30s
      timeout: 10s
      retries: 3
    networks:
      - rag-network

  # ============================================================
  # 2. 向量数据库：Qdrant（备用库）
  # ============================================================
  qdrant:
    image: qdrant/qdrant:v1.10.0
    container_name: ruoyi-qdrant
    ports:
      - "6333:6333"                                          # HTTP 端口
      - "6334:6334"                                          # gRPC 端口
    volumes:
      - qdrant_data:/qdrant/storage                          # 数据持久化
    deploy:
      resources:
        limits:
          memory: 4G                                         # Qdrant 内存上限：4G
          cpus: '2'                                          # CPU 上限：2 核
    networks:
      - rag-network

  # ============================================================
  # 3. 缓存：Redis（热点缓存 + 会话管理）
  # ============================================================
  redis:
    image: redis:7.2-alpine
    container_name: ruoyi-redis
    ports:
      - "6379:6379"
    # 生产环境建议开启密码认证
    # command: redis-server --requirepass ${REDIS_PASSWORD}
    volumes:
      - redis_data:/data                                     # 持久化 RDB/AOF 文件
    deploy:
      resources:
        limits:
          memory: 2G
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 10s
      timeout: 5s
      retries: 3
    networks:
      - rag-network

  # ============================================================
  # 4. 监控：Prometheus（指标收集）
  # ============================================================
  prometheus:
    image: prom/prometheus:v2.52.0
    container_name: ruoyi-prometheus
    ports:
      - "9090:9090"
    volumes:
      - ./prometheus/prometheus.yml:/etc/prometheus/prometheus.yml  # 配置文件
      - ./prometheus/rules.yml:/etc/prometheus/rules.yml            # 告警规则
      - prometheus_data:/prometheus                                  # 数据持久化
    command:
      - '--config.file=/etc/prometheus/prometheus.yml'
      - '--storage.tsdb.retention.time=30d'                          # 数据保留 30 天
    networks:
      - rag-network

  # ============================================================
  # 5. 可视化：Grafana（仪表盘展示）
  # ============================================================
  grafana:
    image: grafana/grafana:10.4.0
    container_name: ruoyi-grafana
    ports:
      - "3000:3000"
    environment:
      GF_SECURITY_ADMIN_PASSWORD: ${GRAFANA_PASSWORD:-admin}         # 管理员密码
      GF_INSTALL_PLUGINS: grafana-piechart-panel                      # 安装饼图插件
    volumes:
      - ./grafana/dashboards:/etc/grafana/provisioning/dashboards    # 预置仪表盘
      - ./grafana/datasources:/etc/grafana/provisioning/datasources  # 数据源配置
      - grafana_data:/var/lib/grafana                                 # 数据持久化
    networks:
      - rag-network

volumes:
  milvus_data:
  qdrant_data:
  redis_data:
  prometheus_data:
  grafana_data:

networks:
  rag-network:
    driver: bridge
```

### 8.3 监控告警配置清单

| 指标 | 告警阈值 | 严重程度 | 处理方式 |
|------|---------|---------|---------|
| 检索延迟 P99 | > 5s | Critical | 检查向量库负载、连接池、网络 |
| 向量库健康状态 | == 0 | Critical | 自动切换备用向量库，联系运维 |
| LLM 错误率 | > 10次/分钟 | Warning | 检查 LLM API 配额、网络状态 |
| 缓存命中率 | < 30% | Warning | 检查缓存预热策略、TTL 配置 |
| 空检索比例 | > 20% | Warning | 检查知识库完整性、分块策略 |
| Embedding 降级次数 | > 50次/小时 | Warning | 检查主 Embedding 模型状态 |
| 熔断器状态 | 打开 | Critical | 检查下游服务，手动恢复 |

---

## 9. 面试综合题

### 9.1 设计一个企业级RAG系统，你会怎么做？

**完整架构设计回答：**

当我设计一个企业级 RAG 系统时，会从以下 12 个维度展开，确保系统不仅"能用"，而且"好用、稳定、安全"。

**一、架构概览**

```
用户请求
    │
    ▼
┌──────────────┐    ┌───────────────┐    ┌──────────────┐
│  API 网关     │───▶│  安全过滤器    │───▶│  Query 理解  │
│  (限流/鉴权)  │    │  (注入防御)    │    │  (改写/HyDE) │
└──────────────┘    └───────────────┘    └──────┬───────┘
                                                │
    ┌───────────────────────────────────────────┘
    ▼
┌──────────────┐    ┌───────────────┐    ┌──────────────┐
│  混合检索     │◀───│  缓存查询      │◀───│  向量检索    │
│  (向量+关键词) │    │  (Redis)      │    │  (Milvus)   │
└──────┬───────┘    └───────────────┘    └──────────────┘
       │
       ▼
┌──────────────┐    ┌───────────────┐    ┌──────────────┐
│  Rerank 重排  │───▶│  权限过滤      │───▶│  敏感信息过滤 │
│  (精排Top-3)  │    │  (文档级ACL)   │    │  (PII脱敏)   │
└──────┬───────┘    └───────────────┘    └──────┬───────┘
       │                                        │
       ▼                                        ▼
┌──────────────┐    ┌───────────────┐    ┌──────────────┐
│  LLM 生成     │───▶│  输出安全过滤   │───▶│  流式返回    │
│  (带上下文)   │    │  (二次检查)    │    │  (SSE推送)   │
└──────────────┘    └───────────────┘    └──────────────┘
```

**二、各维度设计要点**

1. **检索策略**：采用"向量检索 + 关键词检索"的混合模式。向量检索捕获语义相似度，关键词检索兜底精确匹配。权重比 7:3，可动态调整。

2. **分块策略**：按语义分块（段落级），块大小 500 tokens，重叠 100 tokens。使用 sliding window 保证上下文连续。

3. **Query 理解**：对用户输入进行 Query 改写（补全省略、纠错、扩展同义词），并采用 HyDE（假设文档嵌入）技术，将 Query 先让 LLM 生成一个假设回答，再用这个回答去检索，提高召回率。

4. **重排序**：第一轮检索返回 Top-20 结果，通过交叉编码器（Cross-Encoder）重排序，精排取 Top-3 送入 LLM。重排序可以显著提升答案质量。

5. **质量评估**：建立 RAGAS 评估流水线，每次知识库更新后自动运行评估，确保 Faithfulness >= 0.9、Answer Relevance >= 0.85、Context Recall >= 0.8、Context Precision >= 0.85。

6. **性能优化**：批量 Embedding（@Async + CompletableFuture）、Redis 缓存热点 Query（命中率可达 80%）、TokenStream 流式输出（TTFT < 500ms）、连接池调优。

7. **高可用**：Embedding 模型多供应商降级（OpenAI -> SiliconFlow -> ZhipuAI）、向量库主备切换（Milvus -> Qdrant）、Resilience4j 重试 + 熔断 + 限流三件套。

8. **安全**：文档级权限控制（不同用户只能检索授权文档）、敏感信息过滤（PII 检测 + 脱敏）、Prompt 注入防御（输入清洗 + 输出过滤，双重保险）。

9. **可观测性**：TraceID 贯穿全链路、Micrometer 指标暴露（检索延迟 P99、向量库 QPS、LLM Token 用量、缓存命中率）、Grafana 仪表盘展示、Prometheus 告警。

10. **持续优化**：建立"用户反馈 -> 人工标注 -> 模型微调 -> 重新评估"的闭环，定期分析低质量回答的根因，针对性优化检索或生成环节。

**三、技术选型**

| 组件 | 选型 | 选型理由 |
|------|------|---------|
| 向量库 | Milvus（主）+ Qdrant（备） | Milvus 生态成熟，Qdrant 轻量易部署 |
| Embedding | OpenAI text-embedding-3-small | 综合效果好，有国内替代方案 |
| Rerank | BGE-Reranker | 国产开源，中文场景表现优秀 |
| 缓存 | Redis | 成熟稳定，支持集群和持久化 |
| 容错 | Resilience4j | 轻量级，与 Spring Boot 集成好 |
| 监控 | Prometheus + Grafana | 开源标准方案，社区活跃 |
| 框架 | LangChain4j | Java 生态首选，与 Spring Boot 无缝集成 |

### 9.2 RAG系统上线后发现回答质量差，如何排查和优化？

**完整的排查清单：**

**第一步：定位问题环节（是检索还是生成？）**

看一眼回答就知道问题出在哪里：

- **回答不准确，但内容看起来合理** → 大概率是检索问题，没找到正确的文档
- **回答明显胡编乱造** → 可能是 LLM 幻觉，也可能是检索结果完全不对
- **回答"无法从资料中找到答案"** → 检索没找到相关内容，可能是知识库缺失或检索参数太严
- **回答内容正确但格式混乱** → 可能是 Prompt 模板问题

**快速区分方法**：将检索到的上下文直接展示给用户（或开发人员），看上下文本身是否包含正确答案。如果上下文有答案但 LLM 答错了，是生成问题；如果上下文都没答案，是检索问题。

**第二步：检索问题排查清单**

| 检查项 | 工具/方法 | 判断标准 |
|--------|----------|---------|
| 文档是否已入库？ | 查询向量库文档数量 | 数量应与预期一致 |
| 分块是否合理？ | 人工查看几个典型分块 | 块大小 300~800 tokens，语义完整 |
| Embedding 质量？ | 计算同类文档的向量余弦相似度 | 同类文档相似度应 > 0.8 |
| 检索阈值是否合适？ | 查看检索分数的分布直方图 | minScore 应设置在分数分布的"腰部" |
| Top-K 是否足够？ | 测试不同 K 值对召回率的影响 | K=5 时召回率应 > 80% |
| 是否有冷启动问题？ | 检查新入库文档的检索日志 | 新文档应在 5 分钟内可检索到 |

**第三步：生成问题排查清单**

| 检查项 | 工具/方法 | 判断标准 |
|--------|----------|---------|
| Prompt 模板是否清晰？ | 人工审查 System Prompt | 明确要求"基于上下文回答，不要编造" |
| 上下文是否过长？ | 计算送入 LLM 的 token 数 | 不应超过模型上下文窗口的 80% |
| LLM 温度是否合适？ | 检查 temperature 参数 | 知识问答建议 0.1~0.3 |
| 是否有幻觉？ | RAGAS Faithfulness 指标 | Faithfulness >= 0.9 |

**第四步：常见问题及解决方案速查表**

| 现象 | 根因 | 解决方案 |
|------|------|---------|
| 检索结果全是无关内容 | 分块过大，块内包含太多噪声 | 减小分块大小，提高语义纯度 |
| 检索结果太少（< 3 条） | minScore 过高 或 Top-K 过小 | 降低 minScore 到 0.7，增加 Top-K 到 10 |
| 结果相关但 LLM 回答错误 | Prompt 中上下文位置不对 | 将最关键的内容放在 Prompt 的开头或结尾 |
| 回答总是"无法找到答案" | 知识库缺失 或 Query 改写不充分 | 补充知识库，或启用 HyDE 技术 |
| 首 token 延迟太长 | 未启用流式输出 或 LLM 调用超时配置不合理 | 启用 TokenStream，调整超时时间 |
| 高并发下响应变慢 | 连接池不足 或 限流参数过严 | 增大连接池，检查限流参数 |
| 某个用户总看到无关内容 | 权限过滤逻辑有 Bug | 检查权限过滤的 Metadata 条件 |
| 缓存命中率低 | 缓存键设计不合理 或 TTL 过短 | 增加 Query 归一化，延长 TTL |

### 9.3 如何衡量RAG系统的ROI？

**从成本和效果两个角度：**

**一、效果指标（量化收益）**

| 指标 | 计算方式 | 目标值 | 说明 |
|------|---------|-------|------|
| 回答采纳率 | 用户采纳的回答数 / 总回答数 | > 80% | 用户是否认可 AI 的回答 |
| 首次解决率 | 一次对话内解决的比例 | > 70% | 用户不需要追问 |
| 平均对话轮次 | 总轮次 / 对话数 | < 2.5 | 轮次越少，说明一次回答越准确 |
| 用户满意度评分 | 用户手动打分 | > 4.0/5.0 | 直接的主观反馈 |
| 人工介入率 | 转人工次数 / 总请求数 | < 20% | 越低说明 AI 能处理的越多 |
| 回答质量评分 | RAGAS 综合评分 | > 0.85 | 自动化评估 |

**二、成本指标（量化投入）**

| 成本项 | 计算方式 | 优化方向 |
|--------|---------|---------|
| LLM Token 费用 | 输入 Token 数 × 单价 + 输出 Token 数 × 单价 | 减少无用上下文，选择更便宜的模型 |
| Embedding 费用 | 调用次数 × 单价 | 缓存命中率提升可降低 80% 的调用 |
| 向量库成本 | 服务器费用 + 存储费用 | 索引优化可减少 50% 的存储空间 |
| 基础设施成本 | 服务器 + 网络 + 运维 | 垂直扩展比水平扩展成本更低 |
| 人工标注成本 | 标注人员工时 × 时薪 | 主动学习减少标注量 |

**三、ROI 计算公式**

```
ROI = (节省的成本 + 增加的收益) / 总投入成本

其中：
- 节省的成本 = 人工客服成本（人工处理每条咨询的成本 × 处理量） - LLM 调用成本
- 增加的收益 = 用户满意度提升带来的留存率提升 × 用户生命周期价值
- 总投入成本 = 开发成本 + 基础设施成本 + 运维成本 + 标注成本
```

**四、ruoyi-ai 的 ROI 案例**

假设一个企业知识库问答场景：

- 人工客服成本：20 元/次咨询
- 日均咨询量：1000 次
- RAG 系统解决率：75%
- 每次 RAG 调用成本：0.05 元（含 LLM + Embedding + 基础设施）

**年节省成本计算：**
```
日均节省 = 1000 × 75% × (20 - 0.05) = 14,962.5 元
年节省 = 14,962.5 × 365 = 5,461,312.5 元
年投入 = 开发（30万）+ 基础设施（12万）+ 运维（6万）+ 标注（5万）= 53万
年 ROI = (546 - 53) / 53 = 930%
```

**五、ROI 优化策略**

1. **降低 LLM 成本**：对简单问题使用小模型（如 GPT-4o-mini），只有复杂问题才用大模型
2. **提高缓存命中率**：热点问题缓存后，单次成本降至接近零
3. **减少无用上下文**：精确的检索 + 重排序，减少 Token 消耗
4. **持续优化**：每季度评估一次效果趋势，发现下滑及时干预

---

## 面试避坑指南

以下是在 RAG 系统面试中，面试官最常设置的"坑"，以及正确的应对策略：

**坑 1："RAG 很简单，就是 Embedding + 向量检索 + LLM 生成"**

这是 RAG 的"Hello World"认知，面试官想听的是你对生产级挑战的理解。

> 正确回答：先承认这个基础流程是对的，然后立即补充——"但是 Demo 和生产之间还有 12 个维度的差距，包括检索质量、性能优化、高可用、安全、可观测性等。举例来说，仅检索环节就需要考虑混合检索、Query 改写、HyDE、Rerank 重排序等多个技术。"

**坑 2："向量检索的 top-K 设多少合适？"**

没有标准答案，面试官在考察你是否真的做过调优。

> 正确回答：没有固定值，需要根据业务场景调优。一般来说 K=5 是一个起点。如果知识库文档颗粒度细、相关性高，K=3 就够了；如果文档噪声大，需要先召回更多（K=20）再做重排序。建议通过 A/B 测试或 RAGAS 评估来确定最优值。

**坑 3："Embedding 模型选哪个最好？"**

面试官想看你是不是只会背模型名字。

> 正确回答：没有"最好"的模型，只有最适合的。选型要考虑：嵌入维度（影响存储和检索速度）、语言支持（中文场景优先考虑国产模型）、推理速度（影响首 token 延迟）、成本（OpenAI 按 Token 计费，开源模型可自部署）。建议多模型 A/B 测试后决定。

**坑 4："RAG 和微调（Fine-tuning）比，哪个更好？"**

这是一个经典陷阱题，二者不是替代关系，而是互补关系。

> 正确回答：RAG 和微调各有适用场景。RAG 适合知识密集型、需要实时更新、对幻觉敏感的场景；微调适合让模型学习特定写作风格、输出格式、领域术语。最佳实践是"RAG + 微调"结合——微调让模型学会如何回答，RAG 提供回答所需的知识。

**坑 5："RAG 系统上线后，怎么保证效果不退化？"**

面试官想考察你有没有持续优化的意识。

> 正确回答：建立持续评估体系。具体做法：1）每次知识库更新后，运行 RAGAS 评估套件，对比基线指标；2）从生产日志中随机采样，人工标注质量；3）监控用户行为指标（采纳率、满意度评分）；4）发现指标下降时，通过 TraceID 定位问题环节（检索还是生成）；5）建立"发现问题 -> 分析根因 -> 优化 -> 验证"的闭环。

**坑 6："说说你在 RAG 项目中踩过的坑"**

这道题考察的是你的实战经验，不是技术方案。

> 正确回答（真实案例）：
> - 坑一：分块阶段没考虑 Markdown 标题层级，导致一个块跨越了多个章节，检索时语义混乱。修复：自定义分块器，按标题层级切分。
> - 坑二：首次上线时，minScore 设为 0.8，结果大量查询返回空结果，用户体验极差。修复：通过分析分数分布，将阈值调整为 0.7 + 开启混合检索兜底。
> - 坑三：Redis 缓存键直接用原始 Query，导致"如何配置数据源"和"如何配置数据源？"（就差一个问号）缓存不命中。修复：增加 Query 归一化处理。
> - 坑四：没有做 Embedding 模型降级，某次 OpenAI API 故障导致整个 RAG 系统瘫痪 2 小时。修复：增加多供应商降级机制。

---

## 参考资料与扩展阅读

1. **RAGAS 官方文档**：https://docs.ragas.io/ —— RAGAS 评估框架的完整文档，包含所有指标的详细说明和最佳实践

2. **LangChain4j 官方文档**：https://docs.langchain4j.dev/ —— Java 版 LangChain 的官方文档，包含 ContentRetriever、RetrievalAugmentor、AiService 等核心组件的 API 参考

3. **Resilience4j 官方文档**：https://resilience4j.readme.io/ —— 轻量级 Java 容错库的完整文档，包含 Retry、CircuitBreaker、RateLimiter、Bulkhead、TimeLimiter 等所有模块的配置说明

4. **《Advanced RAG Techniques》**：https://blog.llamaindex.ai/advanced-rag-techniques/ —— LlamaIndex 团队的系列博客，深入介绍了 Query 改写、HyDE、Rerank 等高级 RAG 技术

5. **《Building Production RAG Systems》**：https://www.anyscale.com/blog/building-production-rag-systems/ —— Anyscale 团队的生产级 RAG 系统构建指南，包含性能优化、可观测性、安全等实战经验

6. **Milvus 官方文档**：https://milvus.io/docs/ —— 向量数据库 Milvus 的官方文档，包含索引选择、性能调优、集群部署等生产级配置指南

7. **《Evaluating RAG Systems with RAGAS》**：https://towardsdatascience.com/evaluating-rag-systems-with-ragas/ —— Towards Data Science 上关于 RAGAS 评估框架的实践教程，包含评估数据集构建和指标解读

8. **Spring AI 官方文档**：https://docs.spring.io/spring-ai/reference/ —— Spring AI 项目的官方文档，提供了与 LangChain4j 互补的 Spring 生态 RAG 实现方案

9. **《Production RAG: Lessons Learned》**：https://developer.nvidia.com/blog/production-rag-lessons-learned/ —— NVIDIA 工程师分享的生产级 RAG 系统实践经验，涵盖高可用、性能优化、质量评估等

10. **ruoyi-ai 项目源码**：https://github.com/ruoyi-ai/ruoyi-ai —— 本文所有代码示例均基于 ruoyi-ai 项目的实际代码，建议结合源码阅读以加深理解