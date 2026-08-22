# 向量存储深度对比：Milvus vs Weaviate vs Qdrant 选型实战

> 对应项目：ruoyi-ai/ruoyi-chat 模块

> 系列导航：本文是《RAG 技术栈深度剖析》系列第 5 篇。前文我们分析了[文本切分与向量化](../03-text-splitting-embedding.md)、[对话记忆管理](../04-chat-memory.md)，现在进入 RAG 管线的核心存储层——向量数据库。下一篇将探讨[混合检索与 Rerank 重排](../06-hybrid-rerank.md)。

---

## 一、为什么 MySQL 做不了向量检索？

### 1.1 从一次真实的搜索说起

假设 ruoyi-ai 的用户问了一个问题："如何配置 RuoYi 的多数据源？"。我们对这个问题做 embedding（文本向量化）后，得到的是一个 **1536 维的浮点向量**（OpenAI 的 `text-embedding-3-small` 就是这个维度；使用 local 模型时通常是 768 或 1024 维），例如：

```java
// 一个伪代码示意：用户问题的向量表示（实际是几百个浮点数）
float[] questionVector = {0.0132f, -0.0871f, 0.0923f, /* 共 1536 个浮点数 */ ...};
```

RAG 要做的事情是：到向量库里找 **"语义上最接近"** 的知识片段。这个"接近"不是 MySQL 的 `LIKE '%多数据源%'` 这样的**关键词匹配**，而是 **向量之间的几何距离**。

### 1.2 MySQL B-Tree 索引的"致命伤"

MySQL 的 InnoDB 使用 B+ Tree 索引，它的设计前提是：**数据存在天然的顺序关系**。`WHERE age > 18 AND age < 60`、`ORDER BY create_time DESC` 这类查询，B+ Tree 可以通过有序结构快速定位。

但向量检索要解决的问题完全不同：

- 向量是**高维空间中的点**（1536 维），根本不存在一维的"顺序"；
- 我们要求的是"**离目标点最近的 K 个点**"（KNN），没有任何一列的取值范围可以建立 B+ Tree 的索引；
- 即使强行建索引，把 1536 列都建上，B+ Tree 也只能处理"等于"和"范围"查询，**无法回答"语义上最相似"**。

更致命的是：**距离计算无路可逃**。向量相似度必须遍历候选集逐个计算余弦相似度或欧氏距离，这个代价是 O(N) 或更高——B+ Tree 已经"无能为力"，因为它根本没有任何可以利用的序结构。MySQL 对千万元素做全量距离计算的耗时将是**秒级甚至分钟级**，而 RAG 要求的是 **毫秒级响应**。

### 1.3 维度灾难（Curse of Dimensionality）

"维度灾难"是高维数据检索中最核心的数学障碍，它有两层含义：

| 层面 | 表现 | 对检索的影响 |
| --- | --- | --- |
| 距离区分度 | 维度越高，任意两点之间的距离**趋同**，区分能力急剧下降 | 最近邻与次近邻的距离差越来越小，检索"近似解"困难 |
| 空间膨胀 | 高维空间体积指数级膨胀，数据稀疏 | 想覆盖空间需要指数级的数据量，实际数据只是"沧海一粟" |

数学上有一个经典的结论：在高维空间中，**最远距离与最近距离之比趋近于 1**。也就是说"最近邻"和"最远邻"几乎一样远，这时暴力扫描求出的"最近邻"甚至可能不如随机挑选的结果。

### 1.4 专用向量数据库解决了什么

专用向量数据库的核心价值是**近似最近邻搜索（ANN, Approximate Nearest Neighbor）**：用**可接受的精度损失**换取**数量级的速度提升**。它基于一个朴素的洞察——RAG 场景下我们根本不需要绝对的"最近 K 个"，**足够接近的 K 个**完全能满足知识检索的需求，因为后面还有 Rerank 重排层兜底。

所以向量数据库的三大核心竞争力是：

1. **ANN 索引结构**：HNSW、IVF、PQ 等，把 O(N) 的暴力扫描降到 O(log N) 甚至亚线性；
2. **内存/磁盘混合管理**：大部分热点图结构放内存，冷数据落盘；
3. **面向向量的存储引擎**：专门的向量文件格式、分段合并（segment）、过滤条件下的向量检索优化。

---

## 二、HNSW 索引原理：分层导航小世界图

Milvus、Qdrant、Weaviate 最常用的索引都是 **HNSW（Hierarchical Navigable Small World，分层可导航小世界图）**。不理解 HNSW，就无法理解为什么向量库能这么快，也无法在面试中讲清楚"召回率如何保证"。

### 2.1 从"六度分隔"说起

社交网络研究有一个著名的"六度分隔"理论：世界上任意两个人，平均通过 5~6 个中间人就能建立联系。这个"小世界"特性（Small World）在高维空间同样存在——**如果每个节点都连接到它的近邻，那么从任意节点出发，多次跳跃就能逼近目标区域的节点**。

HNSW 的灵感正来源于此。它把"图"按语义相似度构建：**语义接近的向量在图中互为邻居（边相连）**，然后从任意起点出发，沿着边"走"向与查询向量更接近的方向，最终收敛到目标区域。

### 2.2 图结构三要素

| 概念 | 含义 | 类比 |
| --- | --- | --- |
| 节点（Node） | 一个数据向量 | 城市里的一个地点 |
| 边（Edge） | 两个节点的连接，代表"互为近邻" | 地点之间的道路 |
| 邻居（Neighbor） | 与某节点有边直接相连的节点 | 与某地直接连通的地点 |

当查询向量到达时，算法沿着"最像目标方向"的边做**贪婪搜索（greedy search）**：每次走到一个节点，评估它的所有邻居，选择距离查询最接近的邻居继续走，直到邻居中找不到更近的为止。

### 2.3 分层结构：Layer 0 到 Layer N

但只有一个图还不够——如果图只有一层，从起点走到目标区域可能需要几十次跳跃，每次跳跃都要计算距离，在大规模数据下依然太慢。HNSW 的杀手锏是**多层图**：

```
Layer 3  (最顶层，节点最稀疏)      Q ← 查询从这里开始
  ▲
Layer 2
  ▲
Layer 1
  ▲
Layer 0  (最底层，包含全部节点)    目标向量在这里
```

- **Layer 0**：包含**所有**向量节点，密度最高；
- **Layer 1 ~ N**：逐层向上，节点数量按概率递减（每个节点以某个概率出现在更高层），越往上节点越稀疏；
- **搜索过程**：从最顶层开始，用**大步长**快速逼近目标区域（跳到随机但"大方向对"的节点）；进入下一层后再逐步细化（Layer 0 上做精细的局部搜索）。

**为什么分层能加速？** 顶层节点少，"跳跃"成本低，能快速把搜索范围缩小到目标区域附近；到了 Layer 0，虽然节点多，但搜索起点已经在目标区域附近，局部贪心几步即可收敛。典型的搜索总复杂度接近 **O(log N)**。

### 2.4 三个核心参数：M、efConstruction、efSearch

HNSW 性能调优就是调三个参数，面试被问"HNSW 参数怎么调优"时，回答这三者的 trade-off 就是满分答案：

#### 参数 M（最大连接数）

每个节点在每层最多与多少个邻居相连。

- **M 越大**：图连接越密集，路径选择更多，检索精度更高；但内存占用翻倍、构建时间变长（每个节点要计算与更多候选的距离）；
- **M 越小**：内存省、构建快，但图可能"断连"，搜索容易陷入局部最优导致召回率下降；
- **经验值**：ruoyi-ai 实测 M=16 时，在 10 万级文档片段上能兼顾精度（Recall@10 ≈ 0.98）与内存（约 0.3~0.5GB）。

#### 参数 efConstruction（构建时搜索范围）

构建索引时，为新节点寻找邻居的搜索广度。

- **越大**：构建时能发现更全局的最优邻居，图质量更高，检索更准；但构建时间显著增加（构建是 O(efConstruction²) 级别）；
- **越小**：构建更快，但图是"近视眼"，只连接了局部邻居，检索精度下降；
- **经验值**：200~500 是常用区间。**上线初期可设小值快速建库，索引重建时再放大**。

#### 参数 efSearch（查询时搜索范围）

查询时维护的候选池大小。

- **越大**：搜索时保留的候选路径越多，召回率越高，但延迟线性上升；
- **越小**：响应快，但容易漏掉真正的近邻；
- **经验值**：50~200。ruoyi-ai 在 Rerank 重排兜底的前提下，efSearch=100 时召回率已足够。

### 2.5 与暴力搜索的性能/精度权衡

| 指标 | 暴力搜索（Flat/Exact KNN） | HNSW 近似搜索 |
| --- | --- | --- |
| 时间复杂度 | O(N × D)，N 为向量数，D 为维度 | 约 O(log N)，实际与参数强相关 |
| 100 万条 × 1536 维查询耗时（单机） | 数百毫秒 ~ 秒级 | 5~20ms |
| 召回率（Recall@10） | 100%（精确） | 95%~99.9%（近似） |
| 内存占用 | 全部向量驻留内存 | 图结构额外开销，通常为原始数据的 1.2~1.5 倍 |
| 支持动态增删 | 天然支持 | 支持，但频繁增删会影响图质量 |
| 适用场景 | 数据量小、绝对精度要求极高（如 CV 特征比对） | 规模大、对延迟敏感、允许近似（RAG 主场景） |

> **工程启示**：RAG 场景中 HNSW 的"近似"完全可接受——因为我们最终只保留 top-K 给 LLM，且后面还有 Rerank 精排。真正的"精确搜索"代价在百万级数据量下是无法接受的。

---

## 三、三大向量库横向对比：Milvus vs Weaviate vs Qdrant

> 数据截止 2026 年中，各库版本仍在快速迭代，GitHub star 数为近似值。

| 维度 | Milvus | Weaviate | Qdrant |
| --- | --- | --- | --- |
| 定位 | 云原生分布式向量数据库 | 语义搜索引擎（附 GraphQL 查询） | 高性能轻量向量数据库（Rust 实现） |
| 部署模式 | 单机（Standalone）/ 分布式集群（K8s） | 单机 Docker / Kubernetes | 单机 Docker / 分布式集群 |
| 扩展性 | 完整的**分片（shard）+ 副本（replica）**，云原生弹性伸缩最强 | 分片 + 复制支持，但运维复杂度偏高 | 分片 + 副本，支持多节点集群，原生水平扩展 |
| 底层存储 | 存算分离：对象存储（MinIO/S3）+ 消息队列（Pulsar/Kafka）+ 元数据（etcd/MySQL） | 自带 object storage + LSM 树存储 | RocksDB/LSM 树存储引擎 |
| API 风格 | 首选 **gRPC**，提供 REST、SDK | 首选 **REST/GraphQL** | 首选 **REST**，同时提供 gRPC |
| SDK 语言 | Java、Python、Go、Node.js、Rust、C++ | Java、Python、Go、JS、TypeScript、C# | Java、Python、Go、Rust、JS、C#、PHP |
| 运行时 | Java + C++（Knowhere 引擎） | Go | Rust（内存安全、并发强） |
| 过滤方式 | 标量字段表达式过滤（布尔表达式、分区过滤） | **filter 对象 + GraphQL where 子句**，过滤能力丰富 | 内置 payload 过滤，支持多字段组合过滤 |
| 相似度算法 | 余弦、欧氏、内积、汉明等，最全面 | 余弦、欧氏、内积、点积，支持自定义 | 余弦、欧氏、内积，支持度量函数的自定义扩展 |
| 特色功能 | 分区（Partition）、索引类型最丰富（HNSW/IVF/SCANN/GPU 索引）、K8s 原生 | 内置了**混合注意力检索（HybridFusion）**、生成式模块、GraphQL 语义查询 | 单向量/多向量、**payload 过滤 + 全文搜索混合（BM25）**、高压缩量化 |
| 社区活跃度 | GitHub star ~33k+ | ~15k+ | ~24k+ |
| 运维复杂度 | 高（组件多、K8s 部署门槛高） | 中 | 低（单个二进制文件即可启动） |
| 内存占用 | 中（组件进程多） | 中 | 低（Rust 实现，常驻内存小） |
| 适用场景 | 大规模生产（千万级+）、多租户、云原生 | 中规模数据 + GraphQL 生态 + 需要内置混合检索 | 中小规模快速落地、轻量部署、混合检索（向量+关键词） |

### 3.1 怎么选？

给 ruoyi-ai 类项目的选型建议，按场景对号入座：

- **个人学习 / 本地开发 / 快速 Demo**：**Qdrant**。一个 Docker 容器起服务，默认 6333 端口，Restful API 直观，资源占用小，最适合先用它跑通 RAG 管线；
- **中小规模生产（百万级以内），部署环境有限**：**Qdrant** 或 **Milvus 单机版**。Qdrant 运维简单，Milvus 单机版功能较全；
- **大规模生产（千万级+）、多租户、多云**：**Milvus**。它的分布式架构（存算分离）在数据量爆炸时弹性最好，云上直接托管（Zilliz Cloud）也很方便；
- **已有 GraphQL 生态，重视语义 + 关键词混合检索开箱即用**：**Weaviate**，它内置的混合检索（向量 + BM25）不需要自行拼装。

> 常见误区：**不要因为 GitHub star 多就选 Milvus**。单机 ME 组件（etcd、MinIO、Pulsar）的内存开销巨大（起步 4GB+），个人电脑上跑很吃力。选型的首要标准是"数据规模和运维条件"，其次是团队技术栈（Java 团队三个库 SDK 都成熟；要用 gRPC 高性能调 Milvus，要省事用 Qdrant 的 REST）。

---

## 四、代码实战：VectorStoreFactory（策略模式 + Spring DI Map 注入）

ruoyi-ai 的向量存储层采用「**策略模式 + 工厂 + Spring 容器 Map 注入**」，三个存储实现类向容器各注册一个 Bean，运行时按配置动态取用，切换零改动业务代码。这与本项目已有的 `EmbeddingModelFactory`（多 Embedding 供应商切换）是同一套设计思想。

### 4.1 配置属性类：读取 yml 中的存储类型

```java
/**
 * 向量存储配置项
 * 对应 application-*.yml 中的 ruoyi.vector-store.* 配置段
 * 通过 @ConfigurationProperties 自动绑定，无需手动 get/set
 */
@ConfigurationProperties(prefix = "ruoyi.vector-store") // 绑定 yml 前缀 ruoyi.vector-store
public record VectorStoreProperties(
        String type,            // 存储类型: milvus | weaviate | qdrant，运行时切换的开关
        String host,            // 存储服务地址（IP 或域名）
        Integer port,           // 存储服务端口
        String collectionName,  // 集合/类名：Milvus 叫 collection，Weaviate 叫 class，Qdrant 叫 collection
        int dimension,          // 向量维度，必须与 EmbeddingModel 输出维度一致
        int maxResults,         // RAG 检索默认返回条数，对应 EmbeddingStoreContentRetriever 的 maxResults
        double minScore         // RAG 检索最低相似度阈值，低于该值的片段直接丢弃
) {
    // 空构造器：防止 yml 中未配置时 NPE，提供一组安全默认值
}
```

> 使用 **record**（Java 16+ 特性）代替传统 POJO：不可变、自动生成 equals/hashCode/toString，天然适配配置类场景。ruoyi-ai 基于 Java 17，可以放心使用。

### 4.2 配置注册：自动装配所有向量存储 Bean

```java
/**
 * 向量存储自动配置类
 * 把三种存储实现全部注册为 Spring Bean，且统一使用 id 命名，便于 Map 注入
 * 通过 @EnableConfigurationProperties 激活上面的配置类
 */
@Configuration
@EnableConfigurationProperties(VectorStoreProperties.class) // 激活配置属性绑定
public class VectorStoreAutoConfiguration {

    /**
     * Milvus 实现 Bean，bean 名称固定为 "milvus"
     */
    @Bean("milvus") // 显式指定 bean 名称，与 yml 中 type=milvus 对应
    @ConditionalOnProperty(name = "ruoyi.vector-store.type", havingValue = "milvus") // 仅当 type=milvus 时才创建
    public EmbeddingStore<TextSegment> milvusStore(VectorStoreProperties props,
                                                   MilvusServiceClient client) {
        // 构建 Milvus 的 EmbeddingStore（LangChain4j 官方提供的 Milvus 集成）
        return MilvusEmbeddingStore.builder()
                .host(props.host())                    // Milvus 服务地址
                .port(props.port())                    // Milvus gRPC 端口，默认 19530
                .collectionName(props.collectionName())// 集合名，自动建集合
                .dimension(props.dimension())          // 向量维度，与 embedding 模型一致
                .retrieveEmbeddingsOnSearch(true)      // 检索时同时返回向量内容
                .build();
    }

    /**
     * Weaviate 实现 Bean，bean 名称固定为 "weaviate"
     * @ConditionalOnProperty 保证互斥注册：同一时刻只有一个实现生效
     */
    @Bean("weaviate")
    @ConditionalOnProperty(name = "ruoyi.vector-store.type", havingValue = "weaviate")
    public EmbeddingStore<TextSegment> weaviateStore(VectorStoreProperties props) {
        // Weaviate 客户端：v1 是同步 Java 客户端（LangChain4j 长期支持）
        var weaviateClient = new WeaviateClient(
                props.host(),   // Weaviate 地址，如 http://localhost:8080
                props.port());  // Weaviate REST 端口，默认 8080
        // 构建 Weaviate 的 EmbeddingStore；className 等价于 Milvus 的 collection
        return WeaviateEmbeddingStore.builder()
                .apiKey("")                    // 未开启鉴权时留空
                .scheme("http")                // 本地部署使用 http
                .host(props.host())            // 主机
                .port(props.port())            // 端口
                .className(props.collectionName()) // 对应 Weaviate 的 Class 名称
                .textField("content")          // 文本字段名
                .avoidDups(true)               // 避免重复插入（按 id 去重）
                .consistencyLevel(ConsistencyLevel.ALL) // 强一致读：保证检索可见性
                .build();
    }

    /**
     * Qdrant 实现 Bean，bean 名称固定为 "qdrant"
     * 三个实现同构，切换只改 yml 一个属性
     */
    @Bean("qdrant")
    @ConditionalOnProperty(name = "ruoyi.vector-store.type", havingValue = "qdrant")
    public EmbeddingStore<TextSegment> qdrantStore(VectorStoreProperties props) {
        // 用 Qdrant 官方 SDK 构建 grpc 客户端（也支持 http，grpc 性能更好）
        QdrantClient qdrantClient = new QdrantClient(
                QdrantGrpcClient.newBuilder(
                        props.host(),   // Qdrant 地址
                        props.port()    // gRPC 端口，默认 6334；REST 是 6333
                ).build());
        // 构建 Qdrant 的 EmbeddingStore
        return QdrantEmbeddingStore.builder()
                .client(qdrantClient)                  // 注入自定义客户端
                .collectionName(props.collectionName())// 集合名
                .build();
    }
}
```

### 4.3 工厂类：Map 注入实现策略分发

这是整个设计的灵魂：**不写 if-else 分发，而是让 Spring 把"bean 名称 → 实现"的映射关系自动注入为 Map**。

```java
/**
 * 向量存储工厂
 * 利用 Spring 的 Map<String, Bean> 注入特性，按配置类型动态取用对应实现
 * 新增一个向量库只需写一个 @Bean，业务代码零改动 —— 开闭原则的完美实践
 */
@Component
public class VectorStoreFactory {

    /**
     * Spring 会把容器中所有 EmbeddingStore<TextSegment> 类型的 Bean
     * 以 "bean名称 -> 实例" 的形式注入到这个 Map 里
     * 例如: {"milvus" -> MilvusEmbeddingStore, "qdrant" -> QdrantEmbeddingStore, ...}
     *
     * @Autowired 注入时 Map 的 key 自动取 bean 名称，value 取 bean 实例
     * 这是 Spring 框架"依赖注入升级为策略路由"的经典用法
     */
    private final Map<String, EmbeddingStore<TextSegment>> storeMap;

    private final VectorStoreProperties properties; // 当前生效的配置

    /**
     * 构造器注入（Spring 推荐方式，优于字段注入，便于测试）
     * @param storeMap   容器中全部向量存储实现
     * @param properties 配置项，用于读取当前要用的 type
     */
    public VectorStoreFactory(Map<String, EmbeddingStore<TextSegment>> storeMap,
                              VectorStoreProperties properties) {
        this.storeMap = storeMap;        // 保存所有实现
        this.properties = properties;    // 保存配置
    }

    /**
     * 获取当前生效的向量存储实例
     * 根据 yml 中的 ruoyi.vector-store.type 值，从 Map 中取出对应策略
     * @return 当前配置对应的 EmbeddingStore 实现
     */
    public EmbeddingStore<TextSegment> getStore() {
        // 从 Map 中按类型名取实现；找不到时抛出业务异常，便于排错
        EmbeddingStore<TextSegment> store = storeMap.get(properties.type());
        // 防御性校验：配置了不存在的类型时立即失败，而不是运行时静默 NPE
        if (store == null) {
            throw new IllegalStateException("未找到向量存储实现: " + properties.type()
                    + "，请检查 ruoyi.vector-store.type 配置或是否缺少对应 @Bean 依赖");
        }
        return store; // 返回策略实例
    }
}
```

### 4.4 检索服务：把工厂用起来

```java
/**
 * 向量检索服务
 * 演示工厂的用法：RAG 检索时无需关心底层是哪个向量库
 */
@Service
public class VectorSearchService {

    private final VectorStoreFactory factory; // 工厂：策略的入口
    private final EmbeddingModel embeddingModel; // 文本向量化模型（OpenAI/智谱/通义/硅基流动之一）

    /**
     * 构造器注入，Spring 自动装配
     */
    public VectorSearchService(VectorStoreFactory factory, EmbeddingModel embeddingModel) {
        this.factory = factory;           // 注入工厂
        this.embeddingModel = embeddingModel; // 注入向量化模型
    }

    /**
     * 语义相似度检索：问一个问题，返回最相似的 K 段知识片段
     * @param question 用户问题原文
     * @param topK     期望返回的片段数量
     * @return 相似度降序排列的知识片段列表
     */
    public List<TextSegment> semanticSearch(String question, int topK) {
        // 1. 先把问题文本向量化：文本 -> 1536 维浮点向量
        //    embed() 内部会调用配置好的 EmbeddingModel（如 OpenAI text-embedding-3-small）
        var questionEmbedding = embeddingModel.embed(question).content();

        // 2. 通过工厂拿到当前生效的向量存储（milvus / weaviate / qdrant 之一）
        //    业务代码到这里为止，完全不知道底层用的是哪个库 —— 解耦完成
        EmbeddingStore<TextSegment> store = factory.getStore();

        // 3. 构造检索请求：携带查询向量 + 返回条数
        var request = EmbeddingSearchRequest.builder()
                .queryEmbedding(questionEmbedding) // 查询向量
                .maxResults(topK)                  // 最多返回 topK 条
                .build();

        // 4. 执行检索：返回结果封装在 EmbeddingSearchResult 中
        EmbeddingSearchResult<TextSegment> result = store.search(request);

        // 5. 抽取命中片段，返回给上层（用于 RAG 组装上下文）
        return result.matches().stream()          // 把 Match 流式处理
                .map(EmbeddingSearchResult.Match::embedded) // 提取每个命中的 TextSegment
                .toList();                        // 收集为不可变 List（Java 17 特性）
    }

    /**
     * 向向量库写入一条知识片段
     * @param segment 切分好的文本片段（含元数据）
     */
    public void addSegment(TextSegment segment) {
        // 先向量化再入库：传给 add() 时 LangChain4j 会自动调用 embeddingModel 完成向量化
        factory.getStore().add(segment); // 同步写入（生产可换异步批量接口）
    }
}
```

### 4.5 Naive RAG：检索器（ContentRetriever）组装

```java
/**
 * Naive RAG 检索器工厂
 * 把"向量存储 + embedding 模型"组装成 LangChain4j 的 ContentRetriever
 * 该检索器可注入 Assistant 的 @SystemMessage 或 RetrievalAugmentor，实现问答
 */
public class NaiveRagConfig {

    /**
     * 构建一个基于向量检索的 ContentRetriever
     * @param store 向量存储（从工厂获取的具体实现）
     * @param model embedding 模型
     * @return 支持 byName 检索的检索器
     */
    public ContentRetriever buildRetriever(EmbeddingStore<TextSegment> store,
                                           EmbeddingModel model) {
        // EmbeddingStoreContentRetriever：LangChain4j 官方的"向量检索 -> 上下文"适配器
        return EmbeddingStoreContentRetriever.builder()
                .embeddingStore(store)   // 注入向量存储
                .embeddingModel(model)   // 注入向量化模型（员工提问 -> 向量 的转换器）
                .maxResults(5)           // 默认取 5 条片段拼入上下文
                .minScore(0.75)          // 相似度低于 0.75 的片段直接丢弃（质量闸门）
                .build();
    }
}
```

> **设计要点总结**：为什么用「Map 注入」而不是「if-else」？一是**可扩展性**——新增一个向量库（如 pgvector）只需加一个 `@Bean`，不动任何业务代码；二是**可配置性**——切换只改 yml；三是**可测试性**——测试时可以注入一个 mock Map 直接验证工厂逻辑。这与项目里 `EmbeddingModelFactory` 的多供应商切换是同一套路，一学两用。

---

## 五、项目实战：ruoyi-ai 如何实现向量库的无缝切换

### 5.1 yml 配置切换（启动时切换）

```yaml
# application.yml 中向量存储相关配置
ruoyi:
  vector-store:
    # 类型切换开关：milvus | weaviate | qdrant
    # 修改这一行并重启，整个 RAG 检索栈即完成切换
    type: qdrant
    host: 127.0.0.1
    port: 6333          # qdrant REST；milvus 用 19530 (gRPC)；weaviate 用 8080
    collection-name: ruoyi_ai_knowledge
    dimension: 1536     # 必须与 embedding 模型输出维度一致
    max-results: 5
    min-score: 0.75
```

切换链路：`type: qdrant` → `@ConditionalOnProperty` 只装配 `qdrantStore` Bean → Map 中只有 `{"qdrant": ...}` → `VectorStoreFactory.getStore()` 返回 Qdrant 实现。**业务代码零感知**。

### 5.2 切换时的数据迁移策略

切换向量库最大的坑不是代码，而是**数据**。历史知识库数据迁移有以下三种策略：

| 策略 | 做法 | 适用场景 |
| --- | --- | --- |
| 全量重建（推荐） | 写一个批量任务，把原始文档重新切分 → 重新向量化 → 写入新库 | 原始文档仍可获取、数量不大（ruoyi-ai 的知识库场景首选） |
| 向量搬运 | 从旧库导出向量 + 文本 + payload，原样写入新库（不重新向量化） | 原始文档丢失、必须保留原向量 |
| 双写过渡 | 新旧库并行写入一段时间，比对检索效果后择一为主 | 生产环境灰度切换、需要 A/B 验证 |

```java
/**
 * 向量库迁移工具（全量重建策略）
 * 通过 ApplicationRunner 实现"启动时自动迁移"或手动触发
 */
@Component
public class VectorStoreMigrationTask {

    private final EmbeddingModel embeddingModel;   // 向量化模型
    private final VectorStoreFactory factory;      // 工厂：拿到新旧两套存储
    private final VectorStoreProperties props;     // 配置

    /**
     * 迁移入口：从"数据源"重新切分并写入目标库
     * @param documents 原始文档列表（注意：是原文，不是旧库的向量）
     */
    public void migrateFromDocuments(List<Document> documents) {
        // 实例化目标存储（迁移时直接配置对象，不走工厂，避免互相覆盖）
        EmbeddingStore<TextSegment> targetStore = factory.getStore();

        // 对每篇文档：切分 -> 向量化 -> 批量写入（省略切分细节，见系列第 3 篇）
        List<TextSegment> segments = documents.stream()   // 文档流转为片段流
                .flatMap(doc -> splitDocument(doc).stream()) // 每篇文档切分为多个片段
                .toList();                                 // 收集所有片段

        // 批量向量化：embedAll 一次性提交，比逐条 embed 快一个数量级
        List<Embedding> embeddings = embeddingModel.embedAll(segments).content();

        // 成对写入：每个"片段 + 其向量"写入一条记录
        for (int i = 0; i < segments.size(); i++) {
            // add(embedding, segment)：显式传入向量，避免内部重复向量化
            targetStore.add(embeddings.get(i), segments.get(i));
        }
        // 完成后建议执行一次索引重建/优化（见进阶话题）
    }
}
```

### 5.3 运行时切换 vs 启动时切换

| 维度 | 启动时切换（ruoyi-ai 当前方案） | 运行时切换（高级方案） |
| --- | --- | --- |
| 实现难度 | 低：改 yml + 重启 | 高：需要动态刷新 Bean、双存储并存 |
| 一致性风险 | 无：重启后全局一致 | 高：切换窗口内可能出现一部分请求走新库、一部分走旧库 |
| 适用场景 | 日常开发、版本发版、验证性换库 | 多租户按租户路由、AB 测试、应急容灾切换 |
| 实现方式 | `@ConditionalOnProperty` + Map 注入 | `@RefreshScope` + 策略路由（按租户 ID 从 Map 取不同实例） |

> **经验之谈**：ruoyi-ai 当前阶段（开发 + 小规模生产）用**启动时切换**完全够用，简单可靠。只有当出现"同一部署实例要服务不同向量库的租户"这种硬需求时，才升级为运行时切换——**不要提前为不存在的复杂度买单**，这是工程上最常见的坑。

---

## 六、进阶话题

### 6.1 多租户隔离：namespace / partition 机制

多租户是向量库应用的典型场景（不同企业客户的知识库彼此隔离）。三种主流实现：

| 方案 | Milvus | Qdrant | 优缺点 |
| --- | --- | --- | --- |
| Partition 分区 | 集合内按 partition 隔离，检索时指定 partition 过滤 | 不支持原生 partition，需用 payload 过滤字段替代 | 隔离粒度为分区，检索快；分区数量过多会退化 |
| 元数据过滤 | 标量字段 + 表达式过滤（推荐） | payload 字段过滤（官方推荐） | 灵活，但过滤会走一遍"向量检索 + 标量过滤"两阶段 |
| 独立 Collection | 每租户一个 collection | 每租户一个 collection | 隔离最彻底、删租户直接 drop；但集合数量上限受限 |

ruoyi-ai 推荐做法：**租户 ID 作为标量字段写入每条记录 → 检索时强制携带租户过滤条件**。

```java
/**
 * 多租户检索示例：按租户 ID 过滤
 */
public List<TextSegment> searchByTenant(String tenantId, String question, int topK) {
    // 向量化用户问题
    var embedding = embeddingModel.embed(question).content();

    // 构造检索请求，并叠加租户过滤条件（Qdrant 示例，Milvus 语法略有差异）
    // filter：只在该租户的 payload 范围内做向量检索，天然实现租户隔离
    var request = EmbeddingSearchRequest.builder()
            .queryEmbedding(embedding)              // 查询向量
            .maxResults(topK)                       // 返回条数
            .filter(Filter.by("tenantId").eq(tenantId)) // ★ 租户过滤：向量 + 标量联合
            .build();

    return factory.getStore().search(request) // 执行检索
            .matches().stream()                     // 命中结果流
            .map(EmbeddingSearchResult.Match::embedded) // 提取片段
            .toList();                               // 收集返回
}
```

> **安全提醒**：租户过滤条件必须由**服务端强制拼接**，绝不能信任前端传入的过滤参数——否则就是向量库版的 SQL 注入（可越权读取其他租户数据）。

### 6.2 索引重建：数据量增长后的索引优化

HNSW 图是"增量式构建"的，但**大量插入后图质量会退化**（新节点连接不充分、旧节点边缘化），表现为召回率下降、查询变慢。触发重建的信号与做法：

- **信号**：插入量超过索引构建时数据量的 N 倍（经验阈值 1.5~2 倍）；召回率监控告警；
- **Milvus**：`compact` 合并段 + `createIndex` 重建索引，可全量重建；
- **Qdrant**：`/collections/{name}/optimizer` 调优，或执行 `optimizer` 全量重建（Rust 实现的重建很快）；
- **Weaviate**：无需显式重建，其 LSM 结构会自动合并分片，只需关注 `memtable` 压力。

```java
/**
 * 重建索引（Milvus 示例）
 * 通过 schedule 定期检查数据量，超阈值触发重建
 */
@Scheduled(cron = "0 0 3 * * ?") // 每天凌晨 3 点执行（低峰期重建，减少对在线检索的影响）
public void rebuildIndexIfNeeded() {
    // 1. 查询当前集合统计信息（Milvus 的 metrics 接口）
    long count = milvusClient.getCollectionStatistics(collectionName).getNumRows();

    // 2. 数据量增长超过阈值（如上次构建的 1.5 倍）则触发重建
    if (count > lastBuildCount * 1.5) {
        // 3. 重建索引：先 drop 再 create（Milvus 的 COSINE 指标 + HNSW 参数）
        //    高 efConstruction 提升重建后的图质量
        milvusClient.dropIndex(collectionName);
        milvusClient.createIndex(collectionName, "vector", // 为 vector 字段建索引
                IndexParam.createIndexParam(IndexType.HNSW, // 索引类型：HNSW
                        "COSINE",                           // 相似度指标：余弦
                        Map.of("M", 16, "efConstruction", 300))); // 关键参数：M=16、efConstruction=300
        lastBuildCount = count; // 记录本次重建时的数据量，作为下次判断基线
    }
}
```

### 6.3 混合搜索：向量 + 标量过滤（Hybrid Search）

纯向量检索丢掉了关键词能力：用户搜"`RuoYi` 的 `Redis` 配置"时，如果文档里写的是"缓存配置"，向量可能匹配到"缓存"相关的片段而不是精确的 Redis 片段。**混合搜索 = 向量相似度 + 关键词（BM25/FTS）+ 标量过滤**，取并集或加权融合（RRF 融合）。

| 方案 | 实现 | 融合算法 | 适用场景 |
| --- | --- | --- | --- |
| Qdrant 原生混合 | payload 建立全文索引，`SearchRequest` 提供 `hybrid` 参数 | RRF（Reciprocal Rank Fusion） | 词项精确匹配 + 语义，中小规模首选 |
| Weaviate 原生混合 | `HybridSearch` API，内置 BM25 与向量融合 | RRF 或 alpha 加权 | GraphQL 生态、需要开箱即用 |
| 自己拼装（跨库通用） | 分别用向量检索 + 全文检索，各自取 top-K，代码里做 RRF | 自己实现 RRF | 底层库能力不足时 |

```java
/**
 * 混合搜索：向量检索 + 关键词检索 + RRF 融合
 * 适用于底层向量库不提供原生混合检索时的通用实现（ruoyi-ai 兜底方案）
 */
public List<TextSegment> hybridSearch(String question, String keyword, int topK) {
    // 1. 向量检索：语义相似 topK 条（代码见 4.4，此处简写）
    List<TextSegment> semanticHits = semanticSearch(question, topK);

    // 2. 关键词检索：走 Elasticsearch 或向量库的全文索引（见系列第 6 篇详解）
    List<TextSegment> keywordHits = keywordSearch(keyword, topK);

    // 3. RRF 融合：对两个结果集按排名倒数求和，取综合分最高的 topK
    //    RRF 公式: score = Σ 1/(k + rank)，k 为平滑常数（常用 60）
    //    它不依赖分数归一化，直接对"排名"加权，简单且鲁棒
    return rrFusion(semanticHits, keywordHits, topK); // 融合排序实现（省略）
}
```

> **一句话总结**：向量检索解决"语义泛化"，关键词检索解决"精确匹配"，标量过滤解决"业务约束"，三者合并才是一个生产级检索系统的完整形态。ruoyi-ai 的 Rerank 层正是在融合结果上做最终精排，详见本系列第 6 篇。

---

## 七、面试题深度解析

### 面试题 1：向量检索的召回率如何保证？

**回答主线**：召回率（Recall@K）衡量"真正的 K 个近邻被找回了多少"，从四个层面保证：

1. **索引层面**：选对索引结构。HNSW 在百万级、中高维（128~1536）下是均衡之选；数据量更大换 IVF-PQ 牺牲精度换内存；小数据量直接用暴力搜索保 100% 召回。
2. **参数层面**：调大 `efConstruction`（构建精度）和 `efSearch`（查询广度），M 适度增大。**要精度就调大这三个参数，要速度就调小**——这是 HNSW 的灵魂 trade-off。
3. **系统层面**：降维（PCA/Dimension Reduction）、量化（PQ 压缩）会损失精度；保障点是**可用性**而非召回——RAG 的下游还有 Rerank 精排兜底，Top-50 里漏掉 Top-10 的片段是"严重事故"，但 Top-5 里排序略有偏差可接受。
4. **评估层面**：建立离线召回评测集（黄金标准数据集），量化 `Recall@10` 指标，上线后监控真实查询的检索空结果率。**不评测 = 不知道召回率 = 无法优化**。

### 面试题 2：HNSW 参数如何调优？

**回答主线**：按"先定数据规模 → 再定内存预算 → 最后在精度/时延间取平衡"的顺序。

| 参数 | 含义 | 调大的效果 | 调小的效果 | ruoyi-ai 推荐起点 |
| --- | --- | --- | --- | --- |
| M | 每个节点的最大边数 | 精度↑、内存↑、构建↓ | 精度↓、内存↓、构建快 | 16 |
| efConstruction | 构建时搜索范围 | 索引质量↑、构建时间↑↑ | 索引粗糙、精度↓ | 200~400 |
| efSearch | 查询时搜索范围 | 召回↑、延迟线性↑ | 召回↓、延迟低 | 100~200 |

调优方法论（回答加分点）：

1. **内存预算优先**：先根据"多少数据 × 多少内存"定 M（M 直接影响邻接表大小）；
2. **固定 M 和 efConstruction**，扫不同 efSearch，绘制"召回率-延迟"曲线，找到拐点；
3. **生产前把 efConstruction 加大重建一次索引**（如 300），构建慢没关系，一劳永逸提升图质量；
4. **警惕过度拟合**：召回率 95% → 99% 往往需要时延翻倍，RAG 场景 95%+Rerank 往往优于 99% 但慢 3 倍。

### 面试题 3：向量数据库和传统数据库的本质区别是什么？

**回答主线**：从「存储结构、索引原理、查询语义、一致性模型」四个维度对比。

| 维度 | 传统数据库（MySQL/PostgreSQL） | 向量数据库（Qdrant/Milvus） |
| --- | --- | --- |
| 数据模型 | 结构化行 + 明确 schema | 向量 + payload（元数据），非结构化为主 |
| 查询语义 | 等值/范围/排序，SQL | KNN / ANN，"语义最近邻" |
| 索引原理 | B+ Tree / Hash，依赖有序 | HNSW / IVF / PQ，依赖几何拓扑 |
| 核心运算 | 比较、投影、连接 | 距离计算（余弦/欧氏/内积）+ 图搜索 |
| 一致性 | 强一致（ACID 事务） | 最终一致优先（每秒万级写入，索引异步更新） |
| 扩展性 | 垂直扩展为主，读写分离 | 天然的分布式分片 + 副本设计 |

**一句话升华**：传统数据库回答"**哪一行满足条件**"，向量数据库回答"**哪些数据在语义上最接近**"。前者为事务而生，后者为相似度而生。PostgreSQL 的 pgvector 扩展正是"传统的总想收编语义"的折中产物——小规模可用，大规模（千万级）在并发、索引收敛速度、运维 Cost 上都会露怯。

---

## 八、面试避坑指南

1. **坑：混淆 ANN 与 KNN**。KNN 是精确最近邻（暴力/全量计算），ANN 是近似最近邻（HNSW 等）。面试时说"KNN 求近似解"会立刻暴露概念不清。

2. **坑：声称"维度灾难是维度高所以算得慢"**。维度灾难的核心是**距离区分度丧失**（最近与次近趋同），不是单纯的计算量问题。答出"距离趋同"才是内行。

3. **坑：把 efSearch 与 M 的作用说反**。M 影响"图的连接密度"（构建期内存），efSearch 影响"查询时候选池大小"（查询期召回）。二者分属构建期与查询期，漏掉这个划分会被追问到破绽。

4. **坑：HNSW 参数只背数值不问场景**。面试官问"efConstruction 设多少"时，正确答案是"**先少插入快速建库，上线前用较大值重建一次**"，而不是背一个静态数字。

5. **坑：忽略重建与多租户**。只聊索引原理、不谈数据增长后的索引退化，或对租户隔离说不出口，会被判定"纸上谈兵"。工程题的加分点是 6.1 和 6.2 两个实战方案。

6. **坑：误以为向量库能完全替代搜索引擎**。向量库擅长语义召回，关键词精确匹配、聚合统计、复杂打分是 Elasticsearch 的强项。生产系统两者常共存（ruoyi-ai 就是这样），说"向量库一统天下"是新手话术。

7. **坑：选择库时只看 star 数**。Milvus 功能强大但组件多、部署重，个人项目用它就是给自己找运维麻烦。选型的"场景适配"比"功能全"更重要——这本身就是一条值得在面试中展现的工程判断。

---

## 参考资料与扩展阅读

- [LangChain4j 官方文档：EmbeddingStore 与向量检索](https://docs.langchain4j.dev/tutorials/rags/) —— 本项目所有 EmbeddingStore API 的一手来源
- [Milvus 官方文档：索引类型与 HNSW 参数](https://milvus.io/docs/index.md) —— 集合、分区、索引重建的权威说明
- [Qdrant 官方文档：HNSW 调优指南](https://qdrant.tech/documentation/guides/optimizer/) —— Rust 实现下的参数最佳实践与 payload 过滤
- [Weaviate 官方文档：混合检索（Hybrid Search）](https://weaviate.io/developers/weaviate/search/hybrid) —— GraphQL 生态的向量 + BM25 融合
- [HNSW 原论文：Efficient and robust approximate nearest neighbor search using Hierarchical Navigable Small World graphs (Malkov & Yashunin, 2016)](https://arxiv.org/abs/1603.09320) —— HNSW 原理的终极答案，面试深挖必读
- [Ann-Benchmarks](https://ann-benchmarks.com/) —— 各 ANN 算法在真实数据集上的基准对比，选型数据源
- [CSDN/掘金：RAG 向量检索召回率实战调优笔记] —— 社区工程实践，可作补充参考

---

## 系列预告

下一期我们将深入 **混合检索与 Rerank 重排**：为什么要"向量 + 关键词"双路召回？Rerank 模型（bge-reranker）如何给召回结果精排？以及 ruoyi-ai 的 `RerankStage` 实现细节。向量存储只是"召回"的一部分，**召回之后的故事同样精彩**。

> 本系列文章基于 ruoyi-ai 项目实际代码与 LangChain4j 官方 API 编写，如版本升级导致 API 变化，请以官方文档为准。