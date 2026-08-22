# 向量数据库入门：从零认识Milvus/Weaviate/Qdrant

> 本文是 ruoyi-ai 项目技术栈深度剖析系列的第3篇，面向 Java 后端开发者，旨在帮助读者理解向量数据库的核心概念，从零搭建一个基于 LangChain4j 的向量检索 Demo，并使用 InMemoryEmbeddingStore 实现最简向量检索，为后续学习 Milvus/Weaviate/Qdrant 打下基础。

---

## 一、项目背景：该技术栈在项目中的角色

### 1.1 为什么需要向量数据库

在传统的关系型数据库中，我们通过 SQL 进行精确查询：`WHERE name = '张三'` 或 `WHERE age > 18`。但有一个问题传统数据库无法解决：**语义相似性搜索**。

举个例子，用户问："Java 中如何高效处理大量并发请求？" 你希望找到的知识库文档可能是：

- "Java 线程池最佳实践"
- "使用 CompletableFuture 实现异步编程"
- "虚拟线程在 Spring Boot 中的使用"

这些文档可能都不包含"高效处理大量并发请求"这个精确短语，但从语义上它们非常相关。传统数据库的 `LIKE '%并发%'` 无法匹配"线程池"或"异步"这样的相关概念。这就是向量数据库的用武之地——它通过计算语义相似度，找到"意思相近"的内容，而不是"字面相同"的内容。

### 1.2 在 ruoyi-ai 项目中的位置

在 ruoyi-ai 的 RAG（检索增强生成）管线中，向量数据库是核心存储底座：

```
用户提问
    ↓
[Embedding 模型] 将问题转为向量
    ↓
[向量数据库] 检索最相似的文档切片
    ↓
[LLM] 将检索结果 + 问题合并，生成最终回答
```

ruoyi-ai 支持三种向量数据库的工厂化切换：
- **Milvus**：适合大规模向量检索场景
- **Weaviate**：内置向量化能力，开箱即用
- **Qdrant**：过滤能力强，适合复杂查询

通过工厂策略模式，业务代码只依赖 `VectorStore` 接口，一行配置即可切换底层向量库。

### 1.3 本文目标

本文的目标是帮助读者：

1. 理解向量数据库的核心概念和 HNSW 索引原理
2. 掌握 LangChain4j 的 `EmbeddingStore` 接口
3. 使用 `InMemoryEmbeddingStore` 从零搭建最简向量检索 Demo
4. 为后续学习 Milvus/Weaviate/Qdrant 打下基础

---

## 二、核心概念：2-3个，用生活类比解释

### 概念 1：向量（Embedding）—— 就像"物品的数学指纹"

**生活类比**：想象你在图书馆找一本书。你描述它的内容："一本关于中国古代历史的书，通俗易懂，带插图"。图书管理员不需要逐本翻阅，而是根据你的描述，在脑海中比对每本书的"特征"（历史类、通俗风格、有插图），找到最匹配的几本。

**技术映射**：向量（Embedding）就是文本的"数学特征描述"——将一段文字转换为一串浮点数（如 1536 维的向量）。这个向量编码了文本的语义信息：

```java
// "猫在追老鼠" 的向量（简化版，实际是 1536 维）
[0.12, -0.34, 0.56, 0.78, -0.23, ...]  // 1536 个数字

// "狗在追猫" 的向量（语义相似，向量也相似）
[0.11, -0.33, 0.55, 0.79, -0.22, ...]  // 与上一向量距离很近
```

**关键点**：
- 语义相近的文本，向量也相近
- 向量之间的距离（余弦相似度、欧氏距离）衡量语义相似度
- 维度越高，表达能力越强，但计算量也越大

### 概念 2：向量检索（ANN）—— 就像"在陌生的城市找餐厅"

**生活类比**：你到了一个陌生的城市，想找一家餐厅。你的要求是："环境优雅、人均 100-200 元、有 WiFi"。你不会一家一家地跑遍全城所有餐厅（那是暴力搜索），而是：

1. 先看区域：锁定市中心商圈（索引层，粗筛）
2. 再看类型：筛选出符合价位的餐厅（过滤层）
3. 最后评价：在候选列表中选评分最高的（精排）

**技术映射**：ANN（近似最近邻搜索）就是在海量向量中快速找到最相似的 Top-K 个，而非精确匹配：

```java
// 精确搜索：遍历所有向量，计算距离（O(n) 复杂度，百万级数据不可接受）
// 近似搜索：使用索引结构，快速定位（O(log n) 复杂度，毫秒级响应）
```

**关键点**：
- ANN 是"近似"搜索，不是"精确"搜索——召回率通常在 95%-99%
- 用微小的精度损失换取巨大的速度提升（从秒级到毫秒级）
- 索引结构（如 HNSW）是实现 ANN 的核心

### 概念 3：HNSW 索引 —— 就像"城市地图的导航"

**生活类比**：想象你在一个巨大的城市里。你从 A 点到 B 点，最优的导航方式是这样的：

1. **高速层**：先上高速，快速从 A 区域到达 B 区域附近（粗粒度定位）
2. **主干道**：下高速后，走主干道到达 B 点的街道（中粒度定位）
3. **小巷子**：最后走小巷子精确到达 B 点（细粒度定位）

HNSW（Hierarchical Navigable Small World，分层可导航小世界图）的搜索过程完全类似：它构建了多层图结构，上层是"高速公路"（节点少，连接稀疏），下层是"小巷子"（节点多，连接密集）。

```
第 3 层（顶层）：   A --- B --- C    ← 高速公路，快速定位区域
                     |     |
第 2 层（中层）：   A --- B --- C --- D    ← 主干道，缩小范围
                     |     |     |
第 1 层（底层）：   A --- B --- C --- D --- E    ← 小巷子，精确搜索
```

**三个关键参数**：
- `M`：每个节点最多连接数（类似于高速公路的"出口数量"），越大索引质量越高，内存占用越大
- `efConstruction`：构建索引时的搜索广度（建高速时考虑多少条路线），越大索引越精确，建索引越慢
- `efSearch`：查询时的搜索广度（导航时考虑多少条路线），越大召回率越高，查询越慢

---

## 三、从零搭建：完整代码

### 3.1 项目结构

```
hello-vector-store/
├── pom.xml
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/
│   │   │       └── hellovector/
│   │   │           ├── HelloVectorApplication.java     # 启动类
│   │   │           ├── model/
│   │   │           │   └── KnowledgeDoc.java           # 知识文档 POJO
│   │   │           ├── service/
│   │   │           │   ├── VectorSearchService.java    # 向量检索服务
│   │   │           │   └── KnowledgeService.java       # 知识库服务
│   │   │           └── controller/
│   │   │               └── SearchController.java       # REST 控制器
│   │   └── resources/
│   │       └── application.yml
│   └── test/
│       └── java/
│           └── com/
│               └── hellovector/
│                   └── service/
│                       └── VectorSearchServiceTest.java
```

### 3.2 pom.xml

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.hellovector</groupId>
    <artifactId>hello-vector-store</artifactId>
    <version>1.0.0</version>
    <name>hello-vector-store</name>
    <description>向量数据库入门示例：InMemoryEmbeddingStore 最简实现</description>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.5.8</version>
        <relativePath/>
    </parent>

    <properties>
        <java.version>17</java.version>
        <langchain4j.version>1.13.0</langchain4j.version>
    </properties>

    <dependencies>
        <!-- Spring Boot Web -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>

        <!-- LangChain4j Spring Boot Starter -->
        <dependency>
            <groupId>dev.langchain4j</groupId>
            <artifactId>langchain4j-spring-boot-starter</artifactId>
            <version>${langchain4j.version}</version>
        </dependency>

        <!-- LangChain4j OpenAI Starter（用于 Embedding 模型） -->
        <dependency>
            <groupId>dev.langchain4j</groupId>
            <artifactId>langchain4j-open-ai-spring-boot-starter</artifactId>
            <version>${langchain4j.version}</version>
        </dependency>

        <!--
        本地 Embedding 模型（AllMiniLmL6V2）
        这是一个轻量级本地模型，不需要 API Key，适合开发和测试
        基于 Sentence Transformers 的 all-MiniLM-L6-v2 模型
        输出 384 维向量
        -->
        <dependency>
            <groupId>dev.langchain4j</groupId>
            <artifactId>langchain4j-embeddings-all-minilm-l6-v2</artifactId>
            <version>${langchain4j.version}</version>
        </dependency>

        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

### 3.3 application.yml

```yaml
# =============================================
# 向量检索示例应用配置
# =============================================
server:
  port: 8080

spring:
  application:
    name: hello-vector-store

# LangChain4j 配置（用于 Embedding 模型）
langchain4j:
  # 使用本地 AllMiniLmL6V2 模型（不需要 API Key）
  # 如果使用 OpenAI 的 Embedding 模型，需要配置 API Key
  open-ai:
    embedding-model:
      # 使用本地模型时，不需要配置 OpenAI 的 embedding 模型
      # 这里留空，由 @Bean 手动配置本地模型
      enabled: false
```

### 3.4 启动类

```java
package com.hellovector;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot 应用启动类。
 */
@SpringBootApplication
public class HelloVectorApplication {

    public static void main(String[] args) {
        SpringApplication.run(HelloVectorApplication.class, args);
    }
}
```

### 3.5 配置类 —— 手动创建 EmbeddingModel 和 EmbeddingStore Bean

```java
package com.hellovector.config;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilm_l6_v2.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 向量检索配置类。
 *
 * 手动创建 EmbeddingModel 和 EmbeddingStore 的 Bean。
 * EmbeddingModel：将文本转为向量
 * EmbeddingStore：存储向量并提供检索能力
 *
 * 说明：
 * 1. AllMiniLmL6V2EmbeddingModel 是本地模型，不需要 API Key
 * 2. 它输出 384 维的向量（不是 OpenAI 的 1536 维）
 * 3. InMemoryEmbeddingStore 将向量存储在内存中，重启后数据丢失
 *    生产环境应使用 Milvus/Weaviate/Qdrant 等持久化向量数据库
 */
@Configuration
public class VectorStoreConfig {

    /**
     * Embedding 模型：将文本转为向量。
     *
     * AllMiniLmL6V2EmbeddingModel 是一个轻量级本地模型：
     * - 模型大小：约 23MB（自动下载到本地缓存）
     * - 输出维度：384 维
     * - 语言：主要支持英文，中文支持有限
     * - 不需要 API Key，完全本地运行
     *
     * 生产环境建议使用：
     * - OpenAI 的 text-embedding-3-small（1536 维，中文支持好）
     * - 通义千问的 text-embedding-v2（中文优化）
     */
    @Bean
    public EmbeddingModel embeddingModel() {
        return new AllMiniLmL6V2EmbeddingModel();
    }

    /**
     * 向量存储：存储向量并支持检索。
     *
     * InMemoryEmbeddingStore 是最简单的实现：
     * - 将向量存储在内存的 HashMap 中
     * - 使用暴力搜索（遍历所有向量计算距离）
     * - 适合小规模数据（< 10万条）和测试场景
     * - 重启后数据丢失
     *
     * 生产环境应该使用：
     * - MilvusEmbeddingStore（大规模、分布式）
     * - WeaviateEmbeddingStore（内置向量化）
     * - QdrantEmbeddingStore（强过滤检索）
     */
    @Bean
    public EmbeddingStore embeddingStore() {
        return new InMemoryEmbeddingStore();
    }
}
```

### 3.6 知识文档模型

```java
package com.hellovector.model;

/**
 * 知识文档 POJO（Plain Old Java Object）。
 *
 * 表示知识库中的一条文档记录。
 * 在实际项目中，这对应数据库中的知识库文档表。
 */
public class KnowledgeDoc {

    /** 文档唯一标识 */
    private String id;

    /** 文档标题 */
    private String title;

    /** 文档内容 */
    private String content;

    /** 分类标签，如 "Java", "Spring Boot", "数据库" */
    private String category;

    public KnowledgeDoc() {
    }

    public KnowledgeDoc(String id, String title, String content, String category) {
        this.id = id;
        this.title = title;
        this.content = content;
        this.category = category;
    }

    // ========== getters/setters ==========

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }
}
```

### 3.7 向量检索服务

```java
package com.hellovector.service;

import com.hellovector.model.KnowledgeDoc;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 向量检索服务。
 *
 * 核心职责：
 * 1. 将知识文档向量化并存入向量存储
 * 2. 接收用户问题，转为向量后检索最相似的文档
 *
 * 完整流程：
 * 用户提问 → Embedding 模型转为向量 → 向量检索 Top-K → 返回匹配的文档
 */
@Service
public class VectorSearchService {

    private static final Logger log = LoggerFactory.getLogger(VectorSearchService.class);

    // Embedding 模型：将文本转为向量
    private final EmbeddingModel embeddingModel;

    // 向量存储：存储向量并提供检索
    private final EmbeddingStore<TextSegment> embeddingStore;

    /**
     * 构造器注入。
     */
    public VectorSearchService(
            EmbeddingModel embeddingModel,
            EmbeddingStore<TextSegment> embeddingStore) {
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
    }

    /**
     * 初始化：加载示例知识库数据。
     *
     * @PostConstruct 注解确保在 Bean 初始化完成后自动调用此方法。
     * 这里预置了几条 Java 相关的知识文档，方便演示。
     */
    @PostConstruct
    public void init() {
        log.info("开始初始化示例知识库数据...");

        // 创建示例知识文档列表
        List<KnowledgeDoc> docs = createSampleDocs();

        // 将文档向量化并存入向量存储
        for (KnowledgeDoc doc : docs) {
            // 第 1 步：将文档内容包装为 TextSegment
            // TextSegment 是 LangChain4j 的文本片段封装
            TextSegment segment = TextSegment.from(doc.getContent());

            // 第 2 步：将文本转为向量
            // EmbeddingModel.embed() 方法将文本转为向量
            // .content() 从 EmbeddingResponse 中提取 Embedding 对象
            Embedding embedding = embeddingModel.embed(doc.getContent()).content();

            // 第 3 步：将向量和文本存入向量存储
            // embeddingStore.add() 关联向量和文本片段
            embeddingStore.add(embedding, segment);

            log.info("已添加文档: {} [{}]", doc.getTitle(), doc.getCategory());
        }

        log.info("示例知识库初始化完成，共加载 {} 条文档", docs.size());
    }

    /**
     * 创建示例知识文档。
     */
    private List<KnowledgeDoc> createSampleDocs() {
        List<KnowledgeDoc> docs = new ArrayList<>();

        docs.add(new KnowledgeDoc("1", "Java 线程池详解",
                "Java 线程池通过 ThreadPoolExecutor 实现，核心参数包括核心线程数、最大线程数、"
                + "任务队列和拒绝策略。合理配置线程池可以显著提升系统并发处理能力，"
                + "避免频繁创建和销毁线程的开销。",
                "Java"));

        docs.add(new KnowledgeDoc("2", "Spring Boot 自动配置原理",
                "Spring Boot 通过 @EnableAutoConfiguration 注解和 spring.factories 文件实现"
                + "自动配置。启动时扫描 classpath 下的所有自动配置类，根据条件注解"
                + "（@ConditionalOnClass、@ConditionalOnMissingBean 等）按需装配 Bean。",
                "Spring Boot"));

        docs.add(new KnowledgeDoc("3", "MySQL 索引优化实践",
                "MySQL 使用 B+ 树索引结构，适合范围查询和排序。索引优化原则包括："
                + "为 WHERE 和 JOIN 列建立索引、使用覆盖索引避免回表查询、"
                + "避免在索引列上使用函数或计算操作。",
                "数据库"));

        docs.add(new KnowledgeDoc("4", "Redis 缓存策略",
                "Redis 是一种高性能的键值存储系统，支持 String、Hash、List、Set、ZSet 等数据结构。"
                + "常见的缓存策略包括：缓存穿透（布隆过滤器）、缓存击穿（互斥锁）、"
                + "缓存雪崩（随机过期时间+多级缓存）。",
                "数据库"));

        docs.add(new KnowledgeDoc("5", "微服务架构设计原则",
                "微服务架构将单体应用拆分为多个独立部署的服务，每个服务围绕特定业务能力构建。"
                + "关键原则包括：单一职责、自治性、去中心化数据管理、基础设施自动化。"
                + "常用技术栈包括 Spring Cloud、服务网格、容器化部署等。",
                "架构"));

        docs.add(new KnowledgeDoc("6", "Java 并发编程基础",
                "Java 并发编程的核心机制包括 synchronized 关键字、volatile 关键字、Lock 接口、"
                + "Atomic 原子类、以及 ConcurrentHashMap 等并发容器。"
                + "Java 19+ 引入了虚拟线程（Virtual Thread），大幅简化了并发编程模型。",
                "Java"));

        return docs;
    }

    /**
     * 向量检索：根据用户问题检索最相似的文档。
     *
     * @param query 用户问题
     * @param topK  返回的最相似文档数量
     * @return 匹配的 TextSegment 列表（已按相似度排序）
     */
    public List<TextSegment> search(String query, int topK) {
        // 第 1 步：将用户问题转为向量
        // 使用相同的 Embedding 模型，确保向量在同一语义空间
        log.info("检索问题: {}", query);
        Embedding queryEmbedding = embeddingModel.embed(query).content();

        // 第 2 步：构建检索请求
        // queryEmbedding: 查询向量
        // maxResults: 返回的最大结果数
        // minScore: 最低相似度阈值（可选，过滤低质量结果）
        EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)    // 查询向量
                .maxResults(topK)                  // Top-K 数量
                .minScore(0.5)                     // 最低相似度分数（0-1 之间）
                .build();

        // 第 3 步：执行检索
        // InMemoryEmbeddingStore 使用余弦相似度计算向量距离
        // 返回结果按相似度降序排列
        EmbeddingSearchResult<TextSegment> searchResult = embeddingStore.search(searchRequest);

        // 第 4 步：提取匹配的文本片段
        // embeddingMatch.embedded() 返回之前存入的 TextSegment
        // embeddingMatch.score() 返回相似度分数（0-1，越大越相似）
        List<EmbeddingMatch<TextSegment>> matches = searchResult.matches();

        log.info("检索完成，找到 {} 个匹配结果", matches.size());
        for (EmbeddingMatch<TextSegment> match : matches) {
            log.info("  - 相似度: {:.4f}, 内容: {}",
                    match.score(),
                    truncate(match.embedded().text(), 50));
        }

        // 从匹配结果中提取 TextSegment 列表
        return matches.stream()
                .map(EmbeddingMatch::embedded)
                .collect(Collectors.toList());
    }

    /**
     * 带分数返回的检索方法。
     *
     * @param query 用户问题
     * @param topK  Top-K 数量
     * @return 匹配结果列表（包含相似度分数和文本片段）
     */
    public List<EmbeddingMatch<TextSegment>> searchWithScore(String query, int topK) {
        Embedding queryEmbedding = embeddingModel.embed(query).content();

        EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(topK)
                .minScore(0.5)
                .build();

        EmbeddingSearchResult<TextSegment> searchResult = embeddingStore.search(searchRequest);
        return searchResult.matches();
    }

    /**
     * 截断字符串，用于日志输出。
     */
    private String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }
}
```

### 3.8 知识库服务 —— 演示完整的 RAG 流程

```java
package com.hellovector.service;

import com.hellovector.model.KnowledgeDoc;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 知识库服务 —— 演示"检索增强生成（RAG）"的简化流程。
 *
 * 完整的 RAG 流程：
 * 1. 用户提问
 * 2. 检索相关知识
 * 3. 将知识注入 Prompt
 * 4. 调用 LLM 生成回答（本文演示中省略了 LLM 调用）
 *
 * 本文示例只演示"检索"部分，后续文章会完整实现 RAG。
 */
@Service
public class KnowledgeService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeService.class);

    private final VectorSearchService vectorSearchService;

    public KnowledgeService(VectorSearchService vectorSearchService) {
        this.vectorSearchService = vectorSearchService;
    }

    /**
     * 检索并生成回答模板。
     *
     * 模拟 RAG 流程：
     * 1. 检索相关知识
     * 2. 构建增强 Prompt
     * 3. 返回增强后的 Prompt（实际项目中会调用 LLM）
     *
     * @param query 用户问题
     * @param topK  Top-K 数量
     * @return 增强后的 Prompt 模板
     */
    public String retrieveAndBuildPrompt(String query, int topK) {
        // 第 1 步：检索相关知识
        List<TextSegment> relevantDocs = vectorSearchService.search(query, topK);
        // 第 2 步：将检索结果拼接为上下文
        String context = relevantDocs.stream()
                .map(TextSegment::text)
                .collect(Collectors.joining("\n\n---\n\n"));

        // 第 3 步：构建增强 Prompt
        // 在实际项目中，这个 Prompt 会被发送给 LLM 生成最终回答
        String prompt = String.format("""
                请基于以下知识库内容回答用户的问题。
                如果知识库中没有相关信息，请如实说明。

                === 知识库内容 ===
                %s

                === 用户问题 ===
                %s

                === 回答 ===
                """, context, query);

        log.info("构建增强 Prompt 完成，包含 {} 条相关文档", relevantDocs.size());
        return prompt;
    }

    /**
     * 检索并返回详细信息（包含相似度分数）。
     */
    public String searchWithDetails(String query, int topK) {
        List<EmbeddingMatch<TextSegment>> matches =
                vectorSearchService.searchWithScore(query, topK);

        StringBuilder sb = new StringBuilder();
        sb.append("查询: ").append(query).append("\n\n");
        sb.append("检索结果:\n");

        for (int i = 0; i < matches.size(); i++) {
            EmbeddingMatch<TextSegment> match = matches.get(i);
            sb.append(i + 1).append(". ")
                    .append("[相似度: ").append(String.format("%.4f", match.score())).append("]\n")
                    .append("   ").append(match.embedded().text()).append("\n\n");
        }

        return sb.toString();
    }
}
```

### 3.9 REST 控制器

```java
package com.hellovector.controller;

import com.hellovector.service.KnowledgeService;
import com.hellovector.service.VectorSearchService;
import org.springframework.web.bind.annotation.*;

/**
 * 向量检索 REST 控制器。
 */
@RestController
@RequestMapping("/api/search")
public class SearchController {

    private final VectorSearchService vectorSearchService;
    private final KnowledgeService knowledgeService;

    public SearchController(
            VectorSearchService vectorSearchService,
            KnowledgeService knowledgeService) {
        this.vectorSearchService = vectorSearchService;
        this.knowledgeService = knowledgeService;
    }

    /**
     * 语义检索：根据问题检索最相似的文档。
     * GET /api/search?q=Java并发&topK=3
     */
    @GetMapping
    public String search(
            @RequestParam("q") String query,
            @RequestParam(defaultValue = "3") int topK) {
        return knowledgeService.searchWithDetails(query, topK);
    }

    /**
     * RAG 增强 Prompt 生成。
     * GET /api/search/prompt?q=线程池&topK=2
     */
    @GetMapping("/prompt")
    public String buildPrompt(
            @RequestParam("q") String query,
            @RequestParam(defaultValue = "3") int topK) {
        return knowledgeService.retrieveAndBuildPrompt(query, topK);
    }
}
```

### 3.10 单元测试

```java
package com.hellovector.service;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 向量检索服务的单元测试。
 *
 * 使用 @SpringBootTest 启动完整的 Spring Boot 容器，
 * 自动装配 EmbeddingModel 和 EmbeddingStore Bean。
 *
 * 注意：AllMiniLmL6V2EmbeddingModel 是本地模型，
 * 不需要 API Key，第一次运行时会自动下载模型文件（约 23MB）。
 */
@SpringBootTest
class VectorSearchServiceTest {

    @Autowired
    private VectorSearchService vectorSearchService;

    @Autowired
    private KnowledgeService knowledgeService;

    /**
     * 测试向量检索功能。
     *
     * 验证点：
     * 1. 检索结果不为空
     * 2. 检索结果数量不超过 topK
     * 3. 检索结果与查询语义相关
     */
    @Test
    void testSearch() {
        // 准备测试数据
        String query = "Java 中如何高效处理并发";
        int topK = 3;

        // 执行检索
        List<TextSegment> results = vectorSearchService.search(query, topK);

        // 验证结果
        assertNotNull(results, "检索结果不应为 null");
        assertTrue(results.size() > 0, "应至少返回一个结果");
        assertTrue(results.size() <= topK, "结果数量不应超过 topK");

        // 打印结果
        System.out.println("查询: " + query);
        System.out.println("结果数: " + results.size());
        for (int i = 0; i < results.size(); i++) {
            System.out.println((i + 1) + ". " + results.get(i).text());
        }

        // 验证语义相关性：检索结果应包含"线程池"或"并发"相关的内容
        String firstResult = results.get(0).text().toLowerCase();
        boolean containsRelevant = firstResult.contains("线程") 
                || firstResult.contains("并发") 
                || firstResult.contains("异步");
        assertTrue(containsRelevant, "检索结果应与查询语义相关");
    }

    /**
     * 测试检索结果按相似度排序。
     */
    @Test
    void testSearchWithScore() {
        String query = "Spring Boot 自动配置";
        int topK = 5;

        List<EmbeddingMatch<TextSegment>> matches =
                vectorSearchService.searchWithScore(query, topK);

        assertNotNull(matches);
        assertTrue(matches.size() > 0);

        // 验证：结果按相似度降序排列
        for (int i = 0; i < matches.size() - 1; i++) {
            double currentScore = matches.get(i).score();
            double nextScore = matches.get(i + 1).score();
            // 相似度分数应大于等于下一个结果的分数
            assertTrue(currentScore >= nextScore,
                    "结果应按相似度降序排列");
        }

        // 打印相似度分数
        System.out.println("查询: " + query);
        for (int i = 0; i < matches.size(); i++) {
            EmbeddingMatch<TextSegment> match = matches.get(i);
            System.out.println((i + 1) + ". 相似度: " + String.format("%.4f", match.score())
                    + " | " + truncate(match.embedded().text(), 60));
        }
    }

    /**
     * 测试 RAG Prompt 构建。
     */
    @Test
    void testRetrieveAndBuildPrompt() {
        String query = "MySQL 索引优化";
        int topK = 2;

        String prompt = knowledgeService.retrieveAndBuildPrompt(query, topK);

        // 验证 Prompt 包含检索到的知识
        assertNotNull(prompt);
        assertTrue(prompt.contains(query), "Prompt 应包含用户问题");
        assertTrue(prompt.contains("知识库内容"), "Prompt 应包含知识库上下文");
        assertTrue(prompt.contains("回答"), "Prompt 应包含回答标记");

        System.out.println("生成的 Prompt:\n" + prompt);
    }

    private String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }
}
```

---

## 四、运行验证

### 4.1 启动应用

```bash
# 使用 Maven 编译并启动
mvn spring-boot:run

# 看到以下日志表示启动成功：
# 已添加文档: Java 线程池详解 [Java]
# 已添加文档: Spring Boot 自动配置原理 [Spring Boot]
# 已添加文档: MySQL 索引优化实践 [数据库]
# 已添加文档: Redis 缓存策略 [数据库]
# 已添加文档: 微服务架构设计原则 [架构]
# 已添加文档: Java 并发编程基础 [Java]
# 示例知识库初始化完成，共加载 6 条文档
```

### 4.2 测试 API

**测试语义检索：**

```bash
# 检索"Java并发"相关文档
curl "http://localhost:8080/api/search?q=Java%E5%B9%B6%E5%8F%91&topK=3"

# 期望输出（示例）：
# 查询: Java并发
#
# 检索结果:
# 1. [相似度: 0.8234]
#    Java 并发编程基础：Java 并发编程的核心机制包括 synchronized 关键字...
#
# 2. [相似度: 0.7541]
#    Java 线程池详解：Java 线程池通过 ThreadPoolExecutor 实现...
#
# 3. [相似度: 0.6234]
#    Spring Boot 自动配置原理...
```

**测试 RAG Prompt 生成：**

```bash
# 检索并生成增强 Prompt
curl "http://localhost:8080/api/search/prompt?q=%E7%BA%BF%E7%A8%8B%E6%B1%A0&topK=2"

# 期望输出：
# 请基于以下知识库内容回答用户的问题。
# 如果知识库中没有相关信息，请如实说明。
#
# === 知识库内容 ===
# Java 线程池通过 ThreadPoolExecutor 实现...
# ---
# Java 并发编程基础：Java 并发编程的核心机制包括...
#
# === 用户问题 ===
# 线程池
#
# === 回答 ===
```

### 4.3 运行单元测试

```bash
# 运行所有测试
mvn test

# 期望输出：
# [INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
```

### 4.4 验证语义检索效果

传统的关键词搜索和向量检索的对比：

| 查询 | 关键词搜索（MySQL LIKE） | 向量检索（语义搜索） |
|------|------------------------|---------------------|
| "Java并发" | 匹配"Java 并发编程基础" | 匹配"Java 并发编程基础"（0.82）、"Java 线程池详解"（0.75） |
| "数据库优化" | 可能不匹配任何文档 | 匹配"MySQL 索引优化"（0.85）、"Redis 缓存策略"（0.72） |
| "Spring" | 匹配"Spring Boot 自动配置原理" | 匹配"Spring Boot 自动配置原理"（0.91）、"微服务架构"（0.68） |

向量检索的优势在于：即使文档中没有直接包含查询关键词，只要语义相近，也能被检索出来。

---

## 五、项目对照：对应 ruoyi-ai 项目中的真实代码位置

### 5.1 核心文件对照表

| 本文示例 | ruoyi-ai 项目位置 | 说明 |
|---------|-------------------|------|
| `VectorStoreConfig.java` | `ruoyi-ai/ruoyi-chat/src/main/java/com/ruoyi/chat/vector/config/` | 向量存储配置 |
| `VectorSearchService.java` | `ruoyi-ai/ruoyi-chat/src/main/java/com/ruoyi/chat/rag/retriever/` | RAG 检索器实现 |
| `KnowledgeService.java` | `ruoyi-ai/ruoyi-chat/src/main/java/com/ruoyi/chat/rag/` | RAG 管线服务 |
| `InMemoryEmbeddingStore` | `ruoyi-ai/ruoyi-chat/src/main/java/.../vector/MilvusVectorStore.java` | 生产环境使用 Milvus |

### 5.2 ruoyi-ai 中的实际增强

ruoyi-ai 项目中，向量存储模块做了以下增强：

1. **工厂策略模式**：通过 `VectorStoreFactory` 封装 Milvus/Weaviate/Qdrant 三种实现，一行配置切换
2. **批量处理**：支持大批量文档的异步向量化入库
3. **元数据过滤**：除了向量检索，还支持按分类、标签等标量字段过滤
4. **混合检索**：向量检索 + 关键词检索（BM25）的混合召回
5. **Rerank 重排序**：检索结果经过 Rerank 模型重新排序，提升准确率

### 5.3 从 InMemory 到生产级向量数据库

```java
// 开发阶段：InMemoryEmbeddingStore（内存存储，数据不持久化）
EmbeddingStore<TextSegment> store = new InMemoryEmbeddingStore<>();

// 生产环境：MilvusEmbeddingStore（分布式向量数据库）
// EmbeddingStore<TextSegment> store = MilvusEmbeddingStore.builder()
//         .host("localhost")
//         .port(19530)
//         .collectionName("knowledge_base")
//         .dimension(384)  // 必须与 Embedding 模型输出的维度一致
//         .build();

// 或使用 Qdrant
// EmbeddingStore<TextSegment> store = QdrantEmbeddingStore.builder()
//         .host("localhost")
//         .port(6334)
//         .collectionName("knowledge_base")
//         .build();
```

---

## 六、面试实战：3道面试题 + 回答框架

### Q1：什么是向量数据库？和传统数据库有什么本质区别？

**考察点：** 面试官想考察候选人对向量数据库基本概念的理解，以及与传统数据库的对比认知。

**回答框架：**

- **背景**：向量数据库是专门为高维向量相似度检索设计的存储引擎。它的核心能力是 ANN（近似最近邻）搜索，在海量向量中毫秒级返回 Top-K 最相似结果。

- **方案**：向量数据库与传统数据库的本质区别在于：
  - **查询方式**：传统数据库做精确匹配（WHERE name = '张三'），向量数据库做相似度匹配（找最相似的向量）
  - **索引结构**：传统数据库使用 B+ 树（适合精确查找和范围查询），向量数据库使用 HNSW、IVF 等 ANN 索引
  - **数据模型**：传统数据库存储结构化数据（行/列），向量数据库存储向量 + 标量元数据
  - **距离度量**：传统数据库用 = 或 LIKE 判断是否匹配，向量数据库用余弦相似度、欧氏距离等度量相似程度

- **深度（核心区别）**：
  - 传统数据库回答"是不是"：这条记录是否满足条件
  - 向量数据库回答"像不像"：这条记录和查询有多相似
  - 传统数据库的查询结果是确定的（相同的查询返回相同结果），向量数据库的查询结果是近似的（ANN 索引可能返回不同的结果）
  - 传统数据库无法处理语义相似性，向量数据库天然支持语义搜索

- **扩展**：实际应用中，向量数据库通常和传统数据库配合使用：向量数据库负责语义检索，传统数据库负责存储元数据和业务逻辑。ruoyi-ai 中就是 MySQL 存储知识库元数据，Milvus/Weaviate/Qdrant 存储向量。

### Q2：HNSW 索引的原理是什么？关键参数如何影响性能？

**考察点：** 面试官想考察候选人对 ANN 索引算法的理解深度。

**回答框架：**

- **背景**：HNSW（Hierarchical Navigable Small World）是当前最主流的 ANN 索引算法，被 Milvus、Weaviate、Qdrant 等主流向量数据库采用。

- **方案**：HNSW 构建了多层图结构：
  - **顶层**：节点最少，连接稀疏，相当于"高速公路"，用于快速定位到目标区域
  - **底层**：节点最多，连接密集，相当于"小巷子"，用于精确搜索最近邻
  - **搜索过程**：从顶层入口点出发，每层贪心搜索最近节点，逐层向下，最终在底层找到 Top-K 最近邻

- **深度（三个关键参数）**：
  - **M（每个节点的最大连接数）**：M 越大，图连接越密集，索引质量越高，但内存占用越大，建索引越慢。典型值 16-64。M 过大时，搜索速度反而下降（因为邻居太多需要遍历）。
  - **efConstruction（建索引时的搜索广度）**：efConstruction 越大，建索引时搜索越充分，索引质量越高，但建索引越慢。典型值 100-500。这是建索引时的参数，不影响查询速度。
  - **efSearch（查询时的搜索广度）**：efSearch 越大，查询时搜索越充分，召回率越高，但查询越慢。典型值 50-500。这是查询时的参数，可以动态调整。

- **扩展**：HNSW 的优势在于：
  - 搜索速度快（毫秒级响应）
  - 召回率高（95%-99%）
  - 支持动态增删（不需要重建整个索引）
  - 缺点：内存占用大（所有节点和连接都在内存中）

### Q3：在 RAG 管线中，向量检索返回的结果如何与 LLM 配合使用？

**考察点：** 面试官想考察候选人对 RAG 流程的完整理解。

**回答框架：**

- **背景**：RAG（Retrieval-Augmented Generation）是解决 LLM 知识过时、幻觉问题的核心技术。向量检索是 RAG 管线的关键一环，负责从知识库中检索与用户问题相关的文档。

- **方案**：完整流程分为三步：
  1. **检索（Retrieval）**：用户问题通过 Embedding 模型转为向量，在向量数据库中检索 Top-K 最相似的文档切片
  2. **增强（Augmentation）**：将检索到的文档切片作为上下文，与用户问题一起组装成 Prompt
  3. **生成（Generation）**：将增强后的 Prompt 发送给 LLM，LLM 基于检索到的知识生成回答

- **深度（Prompt 组装策略）**：
  ```java
  // 增强 Prompt 的模板示例
  String prompt = """
          请基于以下知识库内容回答用户的问题。
          如果知识库中没有相关信息，请如实说明："抱歉，我没有找到相关信息"。
          不要编造知识库中没有的信息。
  
          === 知识库内容 ===
          %s
  
          === 用户问题 ===
          %s
  
          === 回答 ===
          """;
  ```
  - **上下文窗口限制**：LLM 的上下文窗口有限（如 128K tokens），需要控制检索结果的长度
  - **结果排序**：按相似度分数降序排列，优先使用高相关性的结果
  - **结果过滤**：设置最低相似度阈值（如 0.5），过滤低质量结果
  - **去重**：去除内容重复的检索结果

- **扩展**：生产环境中的 RAG 优化：
  - **Hybrid Search**：向量检索 + 关键词检索（BM25）的混合召回，兼顾语义和关键词匹配
  - **Rerank**：检索结果经过 Rerank 模型重新排序，提升 Top-K 准确率
  - **Query Rewrite**：将用户问题改写为更利于检索的形式
  - **Multi-Turn RAG**：多轮对话中的 RAG，需要结合历史对话上下文

---

## 七、总结

本文从零搭建了一个基于 LangChain4j 的向量检索 Demo，使用 InMemoryEmbeddingStore 实现了最简向量检索，涉及以下知识点：

1. **向量数据库核心概念**：向量（Embedding）、ANN 近似最近邻搜索、HNSW 索引原理
2. **LangChain4j 的 EmbeddingStore 接口**：`add()` 存入向量，`search()` 检索向量
3. **Embedding 模型**：`AllMiniLmL6V2EmbeddingModel` 本地模型，将文本转为 384 维向量
4. **语义检索流程**：文本→向量→检索→排序→返回结果
5. **RAG 基础**：检索增强生成的简化流程

在后续文章中，我们将深入分析 ruoyi-ai 的向量存储工厂策略模式，学习如何实现 Milvus/Weaviate/Qdrant 三种向量数据库的统一接入和配置切换。

---

## 参考资料

- [LangChain4j Embedding Store 文档](https://docs.langchain4j.dev/tutorials/rag) — EmbeddingStore 接口和 RAG 教程
- [LangChain4j In-Memory Embedding Store](https://docs.langchain4j.dev/integrations/embedding-stores/in-memory) — 内存向量存储
- [HNSW 算法论文](https://arxiv.org/abs/1603.09320) — "Efficient and robust approximate nearest neighbor search using Hierarchical Navigable Small World graphs"
- [Milvus 官方文档](https://milvus.io/docs) — 开源向量数据库
- [Qdrant 官方文档](https://qdrant.tech/documentation/) — 向量搜索引擎
- [Weaviate 官方文档](https://weaviate.io/developers/weaviate) — 向量数据库
- [ruoyi-ai GitHub 仓库](https://github.com/1byteone/ruoyi-ai) — 查看完整的向量存储工厂实现