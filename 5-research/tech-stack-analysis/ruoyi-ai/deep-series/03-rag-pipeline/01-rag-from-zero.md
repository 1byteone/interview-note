# RAG从零搭建：30分钟跑通第一个知识问答系统

> **深度系列 | 第1篇** | Level 1 入门
>
> 本篇目标：用最少的代码，搭建一个端到端可运行的RAG知识问答系统。理解核心概念后，30分钟内跑通完整链路。

---

## 一、什么是RAG？为什么需要它？

想象一个场景：公司内部有500份产品文档、技术规范和FAQ，产品上线三年积累了海量知识。销售团队每天都在回答客户相似的问题——"这个功能怎么配置？""支持哪些数据源？""定价方案有什么区别？"

传统做法是维护一个知识库网站，客户和客服自己去翻文档。但问题很明显：文档太多，找不准，效率低。而直接把所有文档扔给LLM？Token限制摆在那里，500份文档少说几百万字，根本塞不进去。

**RAG（Retrieval-Augmented Generation，检索增强生成）** 就是这个问题的解法：先从知识库里检索出最相关的几段文字，再把这些文字和用户问题一起交给LLM。LLM"开卷考试"，答案自然又准又有据。

核心价值可以总结为一句话：**让通用大模型瞬间拥有企业私域知识，且知识可更新、可溯源、可控制。**

与纯LLM对话相比，RAG的优势非常明确：

| 维度 | 纯LLM | RAG增强LLM |
|------|-------|------------|
| 知识范围 | 训练数据截止日期 | 实时，文档更新即生效 |
| 企业私域 | 无 | 完整支持 |
| 幻觉控制 | 容易编造 | 基于检索结果回答 |
| 可溯源 | 无 | 每条答案可定位到原文 |
| 成本 | 微调成本高 | 零微调，仅向量存储成本 |

---

## 二、三个核心概念：一图看懂RAG全链路

在写代码之前，先用30秒理解RAG的三个核心步骤。

### 2.1 文档切分（Chunking）

LLM有上下文窗口限制（4K-128K token不等），无法处理整本书。所以我们需要把文档切成小块——每块通常200-500 token。

**关键原则：**
- 切分粒度影响检索质量：太粗有噪音，太细丢失上下文
- 切分边界尽量落在句子或段落边界
- 相邻块之间保留10-20%的重叠（Overlap），防止关键信息被切断

### 2.2 向量检索（Vector Retrieval）

文本块无法直接被数学运算处理。Embedding模型把每个文本块转成一个高维向量（如1536维浮点数），语义相近的文本在向量空间中距离也近。

用户提问同样被向量化，然后通过**余弦相似度**在向量库里找到最相似的Top-K个文档块。

### 2.3 上下文注入（Context Injection）

检索到的相关文档块被拼接成一段Prompt，和用户问题一起发给LLM。LLM"开卷作答"，既利用了自身推理能力，又基于真实文档生成答案。

完整的RAG流程可以用一句话概括：

```
用户提问 → Query向量化 → 向量检索Top-K → 拼接上下文 + 问题 → LLM生成答案
```

---

## 三、实战：用LangChain4j搭建最简RAG

接下来用真实Java代码，搭建一个完整的RAG系统。我们选择 **LangChain4j** 作为框架，它对Java生态友好，API设计简洁。

### 3.1 Maven依赖

```xml
<!-- pom.xml - RAG最简依赖 -->
<dependencies>
    <!-- LangChain4j 核心 -->
    <dependency>
        <groupId>dev.langchain4j</groupId>
        <artifactId>langchain4j</artifactId>
        <version>1.0.0-beta2</version>
    </dependency>

    <!-- OpenAI LLM 调用 -->
    <dependency>
        <groupId>dev.langchain4j</groupId>
        <artifactId>langchain4j-open-ai</artifactId>
        <version>1.0.0-beta2</version>
    </dependency>

    <!-- PDF 文档解析：Apache PDFBox -->
    <dependency>
        <groupId>dev.langchain4j</groupId>
        <artifactId>langchain4j-document-parser-apache-pdfbox</artifactId>
        <version>1.0.0-beta2</version>
    </dependency>

    <!-- 内存向量库：开发测试用，无需额外部署 -->
    <dependency>
        <groupId>dev.langchain4j</groupId>
        <artifactId>langchain4j-embeddings-all-minilm-l6-v2-q</artifactId>
        <version>1.0.0-beta2</version>
    </dependency>

    <!-- Spring Boot Starter（可选，用Spring方式集成） -->
    <dependency>
        <groupId>dev.langchain4j</groupId>
        <artifactId>langchain4j-spring-boot-starter</artifactId>
        <version>1.0.0-beta2</version>
    </dependency>
</dependencies>
```

**依赖说明：**
- `langchain4j`：核心抽象层，定义Document/Embedding/Splitter等基础接口
- `langchain4j-open-ai`：OpenAI Chat模型接入（也可以换成其他LLM）
- `langchain4j-document-parser-apache-pdfbox`：PDF文档解析器，底层用Apache PDFBox
- `langchain4j-embeddings-all-minilm-l6-v2-q`：本地Embedding模型，All-MiniLM-L6-v2量化版，无需API Key

### 3.2 最简RAG：10分钟可运行

```java
package com.example.rag;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.parser.apache.pdfbox.ApachePdfBoxDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import dev.langchain4j.data.document.splitter.DocumentSplitter;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

/**
 * RAG入门实战：从零搭建一个完整的知识问答系统
 *
 * 整个流程分为两阶段：
 * 1. 离线阶段（Indexing）：文档加载 → 切分 → Embedding → 存入向量库
 * 2. 在线阶段（Retrieval）：用户提问 → Query Embedding → 向量检索 → LLM回答
 */
public class RagQuickStart {

    public static void main(String[] args) {

        // =============================================
        // 第一步：准备 Embedding 模型（向量化引擎）
        // =============================================
        // All-MiniLM-L6-v2：开源模型，384维，英文效果好，无需API Key
        // 内存占用约 50MB，首次加载稍慢，后续推理很快
        EmbeddingModel embeddingModel = new AllMiniLmL6V2EmbeddingModel();

        // =============================================
        // 第二步：准备向量库（存储向量和文本）
        // =============================================
        // InMemoryEmbeddingStore：纯内存存储，适合开发和演示
        // 生产环境应换为 Milvus / Qdrant / PGVector 等持久化方案
        EmbeddingStore<TextSegment> embeddingStore = new InMemoryEmbeddingStore<>();

        // =============================================
        // 第三步（离线阶段）：加载文档 → 切分 → 向量化 → 存储
        // =============================================
        loadDocumentsIntoStore(embeddingModel, embeddingStore);

        // =============================================
        // 第四步：准备 LLM（大语言模型）
        // =============================================
        // 使用 OpenAI gpt-4o-mini，也可以换成 DeepSeek / 智谱 等
        ChatLanguageModel chatModel = OpenAiChatModel.builder()
                .apiKey(System.getenv("OPENAI_API_KEY"))  // 从环境变量读取API Key
                .modelName("gpt-4o-mini")                 // 使用性价比高的mini模型
                .temperature(0.3)                          // 低温度，保证回答准确性
                .build();

        // =============================================
        // 第五步：构建内容检索器
        // =============================================
        // EmbeddingStoreContentRetriever：从向量库中检索最相关的文档片段
        // maxResults(5)：最多返回5条
        // minScore(0.75)：最低相似度阈值，低于0.75的结果会被过滤
        ContentRetriever contentRetriever = EmbeddingStoreContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .maxResults(5)
                .minScore(0.75)
                .build();

        // =============================================
        // 第六步（在线阶段）：创建 AI 服务，开始问答
        // =============================================
        // AiServices：LangChain4j的AI服务构建器
        // 内部自动完成：问题向量化 → 检索 → Prompt组装 → LLM调用
        Assistant assistant = AiServices.builder(Assistant.class)
                .chatLanguageModel(chatModel)
                .contentRetriever(contentRetriever)
                .build();

        // =============================================
        // 第七步：测试问答
        // =============================================
        String question = "什么是RAG？它解决了什么问题？";
        String answer = assistant.chat(question);

        System.out.println("问题: " + question);
        System.out.println("回答: " + answer);
        // 输出示例：
        // RAG（Retrieval-Augmented Generation）是一种检索增强生成技术...
    }

    /**
     * 离线阶段：加载文档 → 切分 → 向量化 → 存入向量库
     * 这是RAG系统的"建库"过程，一次执行后可反复检索
     */
    static void loadDocumentsIntoStore(EmbeddingModel embeddingModel,
                                       EmbeddingStore<TextSegment> embeddingStore) {

        // 1. 加载PDF文档
        // ApachePdfBoxDocumentParser：LangChain4j内置的PDF解析器
        // 底层使用 Apache PDFBox，能正确提取文本、表格（纯文本形式）
        ApachePdfBoxDocumentParser parser = new ApachePdfBoxDocumentParser();
        Document document = parser.parse(Path.of("knowledge-base.pdf"));

        System.out.println("文档加载完成，总字符数: " + document.text().length());

        // 2. 文档切分
        // DocumentSplitters.recursive(300, 50)：
        //   - 300 = 每个切片最大300 token
        //   - 50 = 相邻切片重叠50 token（防止边界信息丢失）
        // recursive 会尝试按段落→句子→词的层级递归切分，保持语义完整性
        DocumentSplitter splitter = DocumentSplitters.recursive(300, 50);
        List<TextSegment> segments = splitter.split(document);

        System.out.println("切分完成，共 " + segments.size() + " 个片段");

        // 3. 向量化并存入向量库
        // embedAll 批量调用Embedding API，比逐条调用效率高
        // 每个TextSegment会被转为一个384维的向量
        List<Embedding> embeddings = embeddingModel.embedAll(segments).content();

        // 将向量和对应的文本段一起存入向量库
        for (int i = 0; i < segments.size(); i++) {
            embeddingStore.add(embeddings.get(i), segments.get(i));
        }

        System.out.println("向量化完成，已存入向量库");
    }
}

/**
 * AI服务接口 —— 定义问答能力
 * LangChain4j会自动生成代理实现，内部处理RAG全流程
 */
interface Assistant {
    /**
     * 与LLM对话
     * LangChain4j会自动：
     * 1. 将question向量化
     * 2. 从contentRetriever检索相关文档
     * 3. 组装Prompt（问题+上下文）
     * 4. 调用LLM生成答案
     */
    String chat(String question);
}
```

**运行结果示例：**

```
文档加载完成，总字符数: 128456
切分完成，共 427 个片段
向量化完成，已存入向量库

问题: 什么是RAG？它解决了什么问题？
回答: RAG（Retrieval-Augmented Generation）是一种检索增强生成技术。
它通过从外部知识库中检索相关信息，将其注入LLM的上下文窗口，
让模型能够基于真实文档内容回答问题，而非仅依赖训练数据。
这有效解决了LLM的"幻觉"问题和知识时效性问题...
```

---

## 四、代码逐行解读：理解每个组件的角色

### 4.1 Embedding模型：文本到向量的翻译器

```java
EmbeddingModel embeddingModel = new AllMiniLmL6V2EmbeddingModel();
```

**作用：** 把自然语言文本转成计算机可计算的向量。

All-MiniLM-L6-v2是微软开源的小型Embedding模型：
- 参数量：22M
- 向量维度：384
- 最大输入：512 token
- 优势：轻量、快速、英文效果好
- 局限：中文支持一般，生产环境建议换中文优化模型

### 4.2 InMemoryEmbeddingStore：临时向量仓库

```java
EmbeddingStore<TextSegment> embeddingStore = new InMemoryEmbeddingStore<>();
```

**作用：** 存储所有文档块的向量和文本，支持相似度检索。

InMemoryEmbeddingStore是LangChain4j的内存实现，数据存在JVM堆内存中，进程退出即丢失。**适合开发调试，生产环境务必使用持久化向量库。**

常见的生产级向量库：

| 向量库 | 特点 | 适用场景 |
|--------|------|----------|
| PGVector | PostgreSQL扩展，SQL生态统一 | 已有PG的技术团队 |
| Milvus | 高性能，支持十亿级向量 | 大规模企业级应用 |
| Qdrant | Rust编写，内存效率高 | 追求性价比的场景 |
| Weaviate | 内置多模态，GraphQL接口 | 需要复杂过滤的场景 |

### 4.3 DocumentSplitters.recursive：智能文档切分器

```java
DocumentSplitter splitter = DocumentSplitters.recursive(300, 50);
```

**作用：** 将长文档切分成适合向量化的小片段。

`recursive` 切分器的策略是：
1. 先尝试按段落（双换行符）切分
2. 如果单个段落超过300 token，再按句子切分
3. 如果单个句子仍然超过300 token，再按空格（单词边界）切分

重叠（Overlap）设置为50 token，意味着相邻两个片段之间有50 token的重复内容，确保边界处的信息不会丢失。

### 4.4 ContentRetriever：检索+过滤的桥梁

```java
ContentRetriever contentRetriever = EmbeddingStoreContentRetriever.builder()
        .embeddingStore(embeddingStore)
        .embeddingModel(embeddingModel)
        .maxResults(5)    // 最多返回5条相关文档
        .minScore(0.75)   // 最低相似度阈值
        .build();
```

**作用：** 定义检索策略，包括返回数量和质量门槛。

两个关键参数的含义：
- `maxResults(5)`：即使有100条相似文档，也只取最相关的5条。数量太少会丢信息，太多会引入噪音并占用LLM上下文。
- `minScore(0.75)`：余弦相似度低于0.75的直接过滤掉。这是质量门槛，宁可少返回，也不能返回不相关的内容。

### 4.5 AiServices：声明式AI服务

```java
Assistant assistant = AiServices.builder(Assistant.class)
        .chatLanguageModel(chatModel)
        .contentRetriever(contentRetriever)
        .build();
```

**作用：** 通过Java接口定义AI能力，LangChain4j在运行时代理实现。

这种设计的精妙之处在于：业务代码只定义"做什么"，框架负责"怎么做"。接口方法签名中的`String question`会被框架自动处理为完整RAG流程的入口。

---

## 五、理解向量检索的底层原理

### 5.1 从Embedding到相似度计算

当用户提问"RAG的优势是什么"时，系统内部发生了什么？

```
1. Embedding模型将问题转为向量：
   "RAG的优势是什么" → [0.12, -0.34, 0.56, ..., 0.78] （384维浮点数）

2. 向量库执行近似最近邻搜索（ANN）：
   - 用HNSW索引快速定位候选集
   - 计算候选集与查询向量的余弦相似度
   - 按相似度降序排列，取Top-5

3. 返回最相似的5个文本片段（及其相似度分数）
```

### 5.2 余弦相似度的直觉理解

```java
// 余弦相似度 = 两个向量夹角的余弦值
// 范围：[-1, 1]，1表示完全相同，0表示无关，-1表示相反

// 语义相近的文本，向量方向相近，余弦值高：
// "RAG通过检索增强生成" 和 "检索增强生成技术"
//   余弦相似度 ≈ 0.92

// 语义不同的文本，向量方向差异大：
// "RAG通过检索增强生成" 和 "数据库连接池配置"
//   余弦相似度 ≈ 0.15
```

### 5.3 HNSW索引：百万级向量的高效检索

暴力搜索需要对每个向量都计算距离，时间复杂度O(n)。当向量库达到百万级时，每次查询需要数秒——不可接受。

**HNSW（Hierarchical Navigable Small World）** 是目前最主流的ANN索引算法：
- 构建一个多层图结构，每层是稀疏图
- 搜索时从顶层开始，逐层深入
- 复杂度从O(n)降到O(log n)
- 牺牲少量精度（通常>95%召回率），换取数量级的速度提升

---

## 六、项目对照：ruoyi-ai的RAG管线

ruoyi-ai项目（`ruoyi-chat`模块）在上述基础上做了企业级增强。对照关系如下：

| 最简RAG | ruoyi-ai对应组件 | 增强点 |
|---------|-----------------|--------|
| ApachePdfBoxDocumentParser | DocumentParserFactory（策略模式） | 支持PDF/Word/Markdown/Excel四种格式 |
| DocumentSplitters.recursive | DocumentSplitter（三种策略） | 按Token/字符/Markdown标题切分，按需切换 |
| AllMiniLmL6V2EmbeddingModel | EmbeddingModelFactory（多模型工厂） | 集成4家Embedding服务商，配置切换 |
| InMemoryEmbeddingStore | Milvus/Weaviate/Qdrant | 生产级持久化向量库 |
| ContentRetriever | RagRetrievalService（双阶段检索） | 向量检索Top-50 + Rerank精排Top-5 |
| 无 | Neo4j GraphRAG | 知识图谱增强，实体关系检索 |

**核心设计模式的升级：**

```java
// 最简RAG：硬编码一个Embedding模型
EmbeddingModel embeddingModel = new AllMiniLmL6V2EmbeddingModel();

// ruoyi-ai：工厂模式，运行时配置切换
// @ConfigurationProperties 绑定 yml 中的 provider 配置
@Component
public class EmbeddingModelFactory {
    public EmbeddingModel createEmbeddingModel(String provider) {
        return switch (provider) {
            case "openai" -> createOpenAIEmbedding();
            case "zhipu" -> createZhipuAIEmbedding();
            case "tongyi" -> createTongyiEmbedding();
            case "siliconflow" -> createSiliconFlowEmbedding();
            default -> throw new IllegalArgumentException("不支持: " + provider);
        };
    }
}
```

这种工厂模式的好处非常实际：代码从不依赖具体服务商，切换OpenAI到智谱只需要改一个配置值，零代码改动。对于企业项目来说，这既是成本优化（用便宜模型做日常，贵模型做关键场景），也是容灾保障（服务商故障时快速切换）。

---

## 七、常见误区与最佳实践

### 7.1 误区一："切分越小越好"

**错误：** 每个切片10-20 token，追求"精准"。

**问题：** 切片太小，语义上下文断裂。比如一个完整的句子"RAG通过检索外部知识来增强LLM的回答质量"被切成两半，每半段都无法表达完整意思，检索到也没用。

**正确做法：** 切片200-500 token，保持语义完整性。宁可稍大一点（有overlap补偿），也不要过小。

### 7.2 误区二："向量相似度高就一定相关"

**错误：** 只用向量检索，Top-1直接返回。

**问题：** 向量检索是近似匹配，HNSW索引为了速度牺牲精度。Top-1可能不是最相关的，Top-5中才包含真正有用的内容。

**正确做法：** 召回Top-5到Top-10，后续再用Rerank精排。ruoyi-ai的做法是召回Top-50再精排到Top-5。

### 7.3 误区三："Embedding模型用最贵的就最好"

**错误：** 默认使用OpenAI text-embedding-3-large（0.13美元/百万token）。

**问题：** 对于中文场景，OpenAI模型的中文语义理解不如国产模型。而且Embedding是一次性调用，成本远低于Chat模型，但这不代表要盲目用贵的。

**正确做法：** 根据语言和场景选择。中文场景用BGE-M3或通义embedding-v3效果更好且成本更低。

---

## 八、面试实战

### Q1: 请描述RAG的基本流程，每个环节的作用是什么？

**参考答案：**

RAG分为离线建库和在线检索两个阶段：

**离线阶段（建库）：**
1. **文档加载**：从PDF/Word等格式中提取纯文本
2. **文档切分**：按策略将长文档切成小片段，保留语义完整性
3. **向量化**：Embedding模型将文本片段转为高维向量
4. **向量存储**：将向量和对应文本存入向量数据库（如Milvus、PGVector）

**在线阶段（检索+生成）：**
1. **Query向量化**：将用户问题转为同维度的向量
2. **向量检索**：在向量库中通过ANN算法找到最相似的Top-K文档片段
3. **上下文组装**：将检索到的文档片段和用户问题拼接成Prompt
4. **LLM生成**：大语言模型基于上下文生成有据可查的回答

**加分点：** 可以补充说明Rerank环节——向量检索召回Top-50后，用Cross-Encoder模型做精确重排序，取Top-5，进一步提升检索精度。

### Q2: 文档切分（Chunking）有哪些策略？如何选择？

**参考答案：**

| 策略 | 原理 | 适用场景 |
|------|------|----------|
| 固定Token切分 | 按Token数精确切分，支持重叠 | 通用文本，需控制LLM上下文窗口 |
| 按段落/标题切分 | 以段落或Markdown标题为边界 | 结构化文档 |
| 语义切分 | 用Embedding检测语义边界后切分 | 高质量要求场景 |
| 递归切分 | 先尝试段落，再尝试句子，最后按词 | LangChain4j默认推荐 |

选择原则：保持语义完整性优先，辅以重叠窗口防止信息丢失。不同文档类型可以用不同策略。

### Q3: 如何评估RAG系统的效果？有哪些评估指标？

**参考答案：**

RAGAS（Retrieval Augmented Generation Assessment）框架定义了四个核心指标：

1. **忠实度（Faithfulness）**：LLM的回答是否忠实于检索到的上下文。衡量是否存在"编造"。
2. **答案相关性（Answer Relevance）**：回答与用户问题的相关程度。
3. **上下文精确率（Context Precision）**：检索到的上下文中，有多少是真正相关的。衡量检索的"精准度"。
4. **上下文召回率（Context Recall）**：所有相关文档中，有多少被检索到了。衡量检索的"覆盖度"。

评估方法：构建测试集（问题+标准答案+参考文档），批量运行后计算上述指标，用数据驱动优化切分策略、Embedding模型选择和检索参数。

### Q4: RAG和Fine-tuning分别适用于什么场景？

**参考答案：**

| 维度 | RAG | Fine-tuning |
|------|-----|-------------|
| 知识更新 | 实时，更新文档即可 | 需要重新训练 |
| 成本 | 低，仅向量库成本 | 高，GPU训练成本 |
| 可溯源 | 支持，可定位到原文 | 不支持 |
| 适用场景 | 企业知识库、FAQ、文档问答 | 风格调整、格式统一、领域术语 |
| 知识量级 | 支持海量文档 | 受限于训练数据量 |

**最佳实践：** 两者结合使用——Fine-tuning让模型学会"怎么回答"（风格/格式/领域语言），RAG让模型知道"回答什么"（具体知识）。

---

> **下一篇预告：** 文档解析是RAG的第一道工序。PDF表格怎么提取？Word的复杂排版怎么处理？不同格式如何统一为结构化文本？下一篇《文档解析深潜》将详细拆解每种格式的解析方案。
