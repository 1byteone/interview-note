# 切分策略全解：从固定大小到语义边界的进阶之路

> **深度系列 | 第3篇** | Level 2 进阶
>
> 本篇目标：深入理解文档切分（Chunking）——RAG检索质量的根基。系统对比固定Token切分、按段落切分、语义切分三种策略，掌握切分质量的评估方法。

---

## 一、为什么切分如此重要？

在上一篇中，我们完成了文档解析，得到了结构化的文本。但解析后的文本往往是几万字的长文，无法直接喂给Embedding模型和LLM。**切分（Chunking）** 就是把长文档切成合适大小的片段，是整个RAG管线的"地基"。

切分的重要性，用四个字概括：**宁缺毋滥**。

### 1.1 切分不当的两种极端

先看两个反面案例。

**案例一：切分太粗**

```
原文（一个章节，2000 token）：
"公司的数据中台采用Lambda架构。实时计算层使用Flink，离线计算层使用Spark，
统一调度使用DolphinScheduler。在数据治理方面，引入Atlas管理元数据..."

切分结果：一整段2000 token作为单个切片
```

问题：用户问"公司用什么做实时计算？"时，这个切片虽然包含Flink，但也混入了Spark、调度、数据治理等大量无关信息。检索时命中这个切片，LLM需要花力气从2000 token中提取那一句话，准确性下降，上下文浪费严重。

**案例二：切分太细**

```
原文：
"Lambda架构由Nathan Marz提出，是处理海量数据的经典架构。它的核心思想是
将数据流分为批处理层和速度层..."

切分结果（每块50 token）：
- 切片1: "Lambda架构由Nathan Marz提出，是处理"
- 切片2: "海量数据的经典架构。它的核心思想是"
- 切片3: "将数据流分为批处理层和速度层..."
```

问题：第一块包含了"Lambda架构由Nathan Marz提出"这个关键信息，但句子被切断，语义不完整。用户问"Lambda架构是谁提出的"时，虽然命中切片1，但"是处理海量数据的经典架构"这样上下文丢失，检索到的片段无法独立成文。

### 1.2 切分的三个核心目标

| 目标 | 说明 |
|------|------|
| **语义完整性** | 每个切片表达相对完整的意思，避免语义被切断 |
| **粒度适中** | 与Embedding模型的最大输入长度匹配（通常是300-500 token） |
| **上下文保留** | 关键信息不能丢失（通过重叠窗口补偿） |

生产实践共识：**切分粒度在 200-500 token之间** 是最优区间。这与Embedding模型的输入限制（All-MiniLM-L6-v2是512 token，BGE-M3是8192 token）和检索精度需求相关。

---

## 二、策略1：固定Token切分（Recursive Splitting）

### 2.1 原理

LangChain4j的 `DocumentSplitters.recursive()` 是多数场景的默认选择。它采用**递归切分**策略：

1. 先尝试按**段落**（双换行符）切分
2. 如果段落仍超过限制，再按**句子**（标点符号）切分
3. 如果句子仍超过限制，最后按**词/空格**切分

这种由粗到细的递归策略，最大程度保证了在每个层级上的语义边界优先。

```java
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.data.document.splitter.DocumentSplitter;

/**
 * 固定Token切分策略 —— recursize递归切分器
 *
 * DocumentSplitters.recursive(300, 50) 参数含义：
 * - 第一个参数 maxSegmentSizeInChars：每个切片最大字符数
 *   注意：LangChain4j默认按"字符数"而非Token切分
 * - 第二个参数 maxOverlapSizeInChars：相邻切片重叠的字符数
 */
public class FixedTokenChunkingStrategy {

    public List<TextSegment> split(String text) {
        // 将纯文本包装为Document对象
        Document document = Document.from(text);

        // 创建递归切分器：
        // - 每段最多300字符
        // - 相邻两段重叠50字符
        DocumentSplitter splitter = DocumentSplitters.recursive(300, 50);

        // 执行切分
        List<TextSegment> segments = splitter.split(document);

        System.out.println("切分完成，共 " + segments.size() + " 个片段");
        System.out.println("平均片段长度: "
            + segments.stream().mapToInt(s -> s.text().length()).average());
        return segments;
    }
}
```

### 2.2 重叠窗口（Overlap）的原理

**核心问题：** 假设一句关键结论"数据中台采用Lambda架构"恰好跨越两个切片的边界，左半句在切片A末尾，右半句在切片B开头。检索时，无论命中A还是B，都是残缺的信息。

**重叠窗口的解决方案：** 相邻切片之间保留一部分重复内容。

```
原始文本：...[数据中台采用] [Lambda架构，用于处理海量数据]...

切片A: [数据中台采用][Lambda架构，用于处理][海量数据...
切片B: [海量数据][Lambda架构，用于处理][批量与实时...

重叠部分：切片A和切片B共享"Lambda架构，用于处理"
```

这样，无论切片A还是切片B被检索到，都能包含"Lambda架构"这一关键信息。

```java
/**
 * 重叠窗口（Overlap）参数的影响
 */
public class OverlapDemo {

    public static void main(String[] args) {
        String text = "RAG（检索增强生成）是一种将信息检索与生成模型结合的范式。"
            + "它通过检索相关文档来增强LLM的上下文。"

        // 不同重叠大小的对比
        splitWithOverlap(text, 200, 0);   // 无重叠
        splitWithOverlap(text, 200, 50);  // 25%重叠（推荐）
        splitWithOverlap(text, 200, 100); // 50%重叠（冗余较多）
    }

    static void splitWithOverlap(String text, int maxLen, int overlap) {
        DocumentSplitter splitter = DocumentSplitters.recursive(maxLen, overlap);
        List<TextSegment> segments = splitter.split(Document.from(text));

        System.out.println("重叠 " + overlap + " 字符 -> " + segments.size() + " 个片段");
        for (int i = 0; i < segments.size(); i++) {
            System.out.println("  片段" + i + ": " + segmentPreview(segments.get(i)));
        }
    }

    static String segmentPreview(TextSegment segment) {
        String text = segment.text();
        return text.length() > 40 ? text.substring(0, 40) + "..." : text;
    }
}
```

**Overlap大小的经验值：**

| 重叠比例 | 适用场景 | 优缺点 |
|---------|---------|--------|
| 0% | 极简场景 | 省存储和API费用，但容易丢信息 |
| 10-20% | **推荐默认** | 信息完整性和成本的最佳平衡 |
| 超过25% | 高价值文档 | 更保险，但索引体积和费用显著上升 |

---

## 三、策略2：按段落/标题切分

### 3.1 原理

如果文档本身具有清晰的结构（段落、标题、章节），按这些结构边界切分是最"自然"的方式。这种切分天然保持语义完整性，因为**作者在写作时就已经确定了语义边界**。

LangChain4j提供 `DocumentByParagraphSplitter` 和 `DocumentByHeaderSplitter` 等结构感知切分器：

```java
import dev.langchain4j.data.document.splitter.DocumentByParagraphSplitter;
import dev.langchain4j.data.document.splitter.DocumentByLineSplitter;

/**
 * 按段落切分策略 —— 适合结构化的技术文档
 *
 * 优点：切片天然语义完整，因为段落本身就是作者划分的语义单元
 * 注意：段落过长时仍需二次切分
 */
public class ParagraphChunkingStrategy {

    /**
     * 按段落切分，每段最多2000字符
     * DocumentByParagraphSplitter会：
     * 1. 将文本按段落边界（双换行符）切分
     * 2. 若段落超过maxChars，则递归按句子切分
     * 3. 支持重叠参数，防止段落边界丢失信息
     */
    public List<TextSegment> splitByParagraph(String text, int maxChars) {
        Document document = Document.from(text);

        // 最大段落长度2000字符，重叠100字符
        DocumentSplitter splitter = new DocumentByParagraphSplitter(maxChars, 100);

        List<TextSegment> segments = splitter.split(document);

        System.out.println("按段落切分，共 " + segments.size() + " 个片段");
        return segments;
    }

    /**
     * 按行切分 —— 适合每一行都是一个完整句子的文档（如日志、清单）
     */
    public List<TextSegment> splitByLine(String text, int maxChars) {
        Document document = Document.from(text);
        DocumentSplitter splitter = new DocumentByLineSplitter(maxChars);
        return splitter.split(document);
    }
}
```

### 3.2 Markdown标题切分：保持章节结构

Markdown文档天然具有章节结构（`#`、`##`、`###`），按标题层级切分是最优策略。rouyi-ai项目正是这么做的。

```java
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 按Markdown标题层级切分
 *
 * 原理：
 * 1. 识别文档中的所有标题（#、##、###...）
 * 2. 以特定级别的标题为边界，将文档切分为"标题+内容"块
 * 3. 每个切块独立成段，保持章节完整性
 *
 * 优点：
 * - 检索命中后，可以直接返回该章节的完整内容
 * - 切片自带标题，上下文信息丰富
 */
public class MarkdownChunkingStrategy {

    /**
     * 按一级标题（#）切分Markdown文档
     *
     * @param markdownText 原始Markdown文本
     * @return 每个元素为 "[标题]\n[该章节内容]"
     */
    public List<TextSegment> splitByMarkdown(String markdownText) {
        List<TextSegment> segments = new ArrayList<>();

        // 匹配Markdown标题行：行首一个或多个# + 空格 + 标题文字
        // ^(#+ .+)$ 使用MULTILINE模式匹配每行
        Pattern pattern = Pattern.compile("^(#+\\s.+)$", Pattern.MULTILINE);

        // 使用标题作为分隔符切分文本
        String[] parts = pattern.split(markdownText);

        // 提取所有标题文字
        Matcher matcher = pattern.matcher(markdownText);
        List<String> titles = new ArrayList<>();
        while (matcher.find()) {
            titles.add(matcher.group(1));
        }

        // 组合"标题+内容"：第i个标题对应第i+1个内容块
        for (int i = 0; i < titles.size() && i + 1 < parts.length; i++) {
            String title = titles.get(i);
            String content = parts[i + 1].trim();

            if (content.isEmpty()) continue;

            // 将标题和内容合并为一个切片
            // 好处：切片自带章节上下文，检索时可命中更丰富的信息
            String segmentText = title + "\n" + content;
            segments.add(TextSegment.from(segmentText));
        }

        System.out.println("按标题切分，共 " + segments.size() + " 个章节");
        return segments;
    }

    /**
     * 通用版本：可指定切分到几级标题
     * 例如 level=2 时，以 # 和 ## 为边界，### 并入所属二级标题
     */
    public List<TextSegment> splitByMarkdownLevel(String markdownText, int level) {
        List<TextSegment> segments = new ArrayList<>();

        // 构建指定级别的标题匹配模式
        // 例如 level=1: ^#\s， level=2: ^#{1,2}\s
        String regex = "^(#{1," + level + "}\\s.+)$";
        Pattern pattern = Pattern.compile(regex, Pattern.MULTILINE);

        String[] parts = pattern.split(markdownText);
        Matcher matcher = pattern.matcher(markdownText);

        List<String> titles = new ArrayList<>();
        while (matcher.find()) {
            titles.add(matcher.group(1));
        }

        for (int i = 0; i < titles.size() && i + 1 < parts.length; i++) {
            String title = titles.get(i);
            String content = parts[i + 1].trim();
            if (content.isEmpty()) continue;
            segments.add(TextSegment.from(title + "\n" + content));
        }

        return segments;
    }
}
```

---

## 四、策略3：语义切分（Semantic Chunking）

### 4.1 原理

固定Token切分和段落切分的共同缺点是：**它们不"理解"文本内容**。无论怎么切，都有可能把语义相关的句子拆开，或者把无关的句子拼在一起。

**语义切分（Semantic Chunking）** 的思路完全不同：先用Embedding模型计算每个句子（或小段）的语义向量，然后检测句子之间的**语义转折点**——向量距离突然变大的位置，就是天然的语义边界。

```
句子向量序列：s1 → s2 → s3 | s4 → s5 | s6 → s7 → s8

计算相邻句子向量的余弦距离：
dist(s1,s2)=0.12, dist(s2,s3)=0.15   ← 语义连续，同属一个主题
dist(s3,s4)=0.62                     ← 距离突变！语义转折点
dist(s4,s5)=0.18                     ← 新主题的开始
dist(s5,s6)=0.55                     ← 又一个转折点
```

在转折点处切分，得到的每个切片内部语义连贯，切片之间边界清晰。

### 4.2 语义切分实现

```java
import dev.langchain4j.model.embedding.EmbeddingModel;

/**
 * 语义切分策略 —— 基于Embedding的语义边界检测
 *
 * 步骤：
 * 1. 将文本按句子/子句切分为最小单元
 * 2. 用Embedding模型计算每个单元的向量
 * 3. 计算相邻向量之间的距离
 * 4. 在距离突增的位置切分（语义转折点）
 */
public class SemanticChunker {

    private final EmbeddingModel embeddingModel;

    public SemanticChunker(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    /**
     * 执行语义切分
     *
     * @param text          原始文本
     * @param minChunkSize  最小切片大小（避免切得太碎）
     * @param threshold     语义转折阈值（距离超过此值视为新主题）
     */
    public List<TextSegment> split(String text, int minChunkSize, double threshold) {

        // 1. 将文本按句子边界切分为最小单元
        //    中文按句号、问号、感叹号切分；兼容英文句号
        List<String> sentences = splitIntoSentences(text);

        // 2. 批量计算所有句子的向量
        //    embedAll批量调用，比逐条快很多
        List<Embedding> embeddings = embeddingModel
            .embedAll(sentences.stream().map(TextSegment::from).toList())
            .content();

        // 3. 计算相邻句子之间的余弦距离
        //    距离 = 1 - 余弦相似度，值越大语义变化越大
        List<Double> distances = new ArrayList<>();
        for (int i = 1; i < embeddings.size(); i++) {
            double cosine = cosineSimilarity(embeddings.get(i - 1), embeddings.get(i));
            distances.add(1.0 - cosine); // 距离
        }

        // 4. 在语义转折点处切分
        List<TextSegment> segments = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        // 记录当前切片的起始位置，用于判断是否达到最小切片大小
        int segmentSentenceCount = 0;

        for (int i = 0; i < sentences.size(); i++) {
            String sentence = sentences.get(i);
            current.append(sentence);

            // 判断是否为切分点：
            // - 当前句子后面还有句子
            // - 与下一句的距离超过阈值（语义转折）
            // - 且当前切片已满足最小大小
            if (i < distances.size()
                && distances.get(i) > threshold
                && segmentSentenceCount >= minChunkSize) {

                // 到达语义边界，完成一个切片
                segments.add(TextSegment.from(current.toString().trim()));
                current = new StringBuilder(); // 清空，开始新切片
                segmentSentenceCount = 0;
            } else {
                segmentSentenceCount++;
            }
        }

        // 处理最后一小段
        if (!current.toString().isBlank()) {
            segments.add(TextSegment.from(current.toString().trim()));
        }

        System.out.println("语义切分完成，共 " + segments.size() + " 个切片");
        return segments;
    }

    /**
     * 将文本按句子边界切分
     */
    private List<String> splitIntoSentences(String text) {
        List<String> sentences = new ArrayList<>();

        // 匹配中文句号（。）、问号（？）、感叹号（！）和英文句号（.）
        // 使用 (?<=...) 后顾断言，保留标点符号
        Pattern sentencePattern = Pattern.compile("[^。？！.!?]+[。？！.!?]+");

        Matcher matcher = sentencePattern.matcher(text);
        while (matcher.find()) {
            String sentence = matcher.group().trim();
            if (!sentence.isEmpty()) {
                sentences.add(sentence);
            }
        }

        // 处理末尾未匹配的残段
        String remainder = text.substring(Math.min(matcher.regionEnd(), findLastMatchEnd(matcher)));
        // 简化处理：如果匹配结果为空，直接按整段切
        if (sentences.isEmpty()) {
            sentences.add(text);
        }

        return sentences;
    }

    private int findLastMatchEnd(Matcher matcher) {
        return matcher.regionEnd();
    }

    /**
     * 计算两个Embedding的余弦相似度
     */
    private double cosineSimilarity(Embedding a, Embedding b) {
        float[] va = a.vector();
        float[] vb = b.vector();

        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < va.length; i++) {
            dot += va[i] * vb[i];
            normA += va[i] * va[i];
            normB += vb[i] * vb[i];
        }
        // 余弦相似度 = 点积 / (|A| * |B|)
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
```

**语义切分的适用边界：**

| 维度 | 语义切分 | 固定切分 |
|------|---------|---------|
| 语义完整性 | 高 | 中 |
| 计算成本 | 高（需额外Embedding） | 低 |
| 实现复杂度 | 中 | 低 |
| 稳定性 | 依赖Embedding模型质量 | 高 |
| 适用场景 | 高质量问答、法律/论文文档 | 通用场景、快速上线 |

**核心权衡：** 语义切分增加了一次"句子级别的Embedding"计算，建库时间和成本提升约30-50%。但在检索质量要求高的场景（法律合同、医疗文档、学术论文），这个成本值得。

---

## 五、三种策略对比总表

| 维度 | 固定Token切分 | 按段落/标题切分 | 语义切分 |
|------|-------------|---------------|---------|
| 切分依据 | 字符数/Tokencount | 结构边界 | 语义边界 |
| 语义完整性 | 中等（靠Overlap补偿） | 高 | 最高 |
| 计算成本 | 低 | 低 | 高（额外Embedding） |
| 适用文档 | 任意文本 | Markdown、结构化文档 | 长文、高质量文档 |
| 稳定性 | 高 | 高 | 依赖模型质量 |
| 实现难度 | 低（框架内置） | 低 | 中 |
| 典型场景 | 通用RAG快速上线 | 技术文档、博客、Wiki | 法律、学术、合同 |

**选型决策树：**

```
文档是否有清晰结构（标题/段落）？
├── 是 → 按标题/段落切分（策略2）
└── 否 → 是否需要最高检索质量？
        ├── 是 → 语义切分（策略3）
        └── 否 → 固定递归切分 + Overlap（策略1）
```

---

## 六、代码示例：ChunkingStrategy完整实现

将三种策略封装为可配置的统一接口，这是ruoyi-ai的思路。

```java
import org.springframework.stereotype.Component;

/**
 * 切分策略接口 —— 策略模式抽象
 */
public interface ChunkingStrategy {
    /**
     * 执行切分
     * @return 切分后的文本片段列表
     */
    List<TextSegment> split(String text);
}

/**
 * 切分策略：固定Token递归切分
 */
@Component
public class RecursiveChunkingStrategy implements ChunkingStrategy {

    private final int maxChars;
    private final int overlapChars;

    public RecursiveChunkingStrategy() {
        this.maxChars = 300;
        this.overlapChars = 50;
    }

    @Override
    public List<TextSegment> split(String text) {
        DocumentSplitter splitter = DocumentSplitters.recursive(maxChars, overlapChars);
        return splitter.split(Document.from(text));
    }
}

/**
 * 切分策略：Markdown按标题切分
 */
@Component
public class MarkdownHeadingChunkingStrategy implements ChunkingStrategy {

    private final int headingLevel;

    public MarkdownHeadingChunkingStrategy() {
        this.headingLevel = 2; // 默认按二级标题切分
    }

    @Override
    public List<TextSegment> split(String text) {
        // 复用之前的按标题切分逻辑
        return splitByMarkdownLevel(text, headingLevel);
    }

    private List<TextSegment> splitByMarkdownLevel(String md, int level) {
        // ... 实现见前文 MarkdownChunkingStrategy
        return new MarkdownChunkingStrategy().splitByMarkdownLevel(md, level);
    }
}

/**
 * 切分策略：语义切分
 */
@Component
public class SemanticChunkingStrategy implements ChunkingStrategy {

    private final EmbeddingModel embeddingModel;

    public SemanticChunkingStrategy(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    @Override
    public List<TextSegment> split(String text) {
        return new SemanticChunker(embeddingModel).split(text, 2, 0.35);
    }
}

/**
 * 切分策略选择器 —— 根据文档类型和配置选择策略
 * 这是ruoyi-ai中DocumentSplitter的设计思路：
 * 文档类型 → 策略映射，可配置切换
 */
@Component
public class ChunkingStrategySelector {

    // 策略MAP：类型 → 策略实现
    private final Map<DocumentType, ChunkingStrategy> strategyMap;

    public ChunkingStrategySelector(
            RecursiveChunkingStrategy recursiveStrategy,
            MarkdownHeadingChunkingStrategy headingStrategy,
            SemanticChunkingStrategy semanticStrategy) {

        strategyMap = new EnumMap<>(DocumentType.class);
        strategyMap.put(DocumentType.GENERAL, recursiveStrategy);    // 通用文档：递归切分
        strategyMap.put(DocumentType.MARKDOWN, headingStrategy);     // MD文档：按标题切分
        strategyMap.put(DocumentType.QUALITY, semanticStrategy);     // 高质量需求：语义切分
    }

    /**
     * 根据文档类型选择切分策略
     */
    public ChunkingStrategy getStrategy(DocumentType type) {
        return strategyMap.getOrDefault(type, strategyMap.get(DocumentType.GENERAL));
    }

    /**
     * 文档类型枚举
     */
    public enum DocumentType {
        GENERAL,   // 通用文档（默认递归切分）
        MARKDOWN,  // Markdown文档（按标题切分）
        QUALITY    // 需要最高质量的文档（语义切分）
    }
}
```

**配置驱动的切分策略切换：**

```yaml
# application.yml 中的切分配置（ruoyi-ai的设计）
ai:
  rag:
    chunking:
      default-strategy: recursive
      recursive:
        max-chars: 300        # 最大字符数
        overlap-chars: 50     # 重叠字符数
      markdown:
        heading-level: 2      # 按几级标题切分
      semantic:
        threshold: 0.35       # 语义转折阈值
        min-chunk-size: 2     # 最小切片句子数
```

---

## 七、项目实战：ruoyi-ai如何根据文档类型选择切分策略

rouyi-ai项目在文档上传端到检索端，实现了**文档类型感知的切分策略**。

### 7.1 整体流程

```
用户上传文档
    ↓
DocumentParserFactory 解析（按后缀路由）
    ↓
识别文档类型（MD/PDF/Word/Excel + 内容特征）
    ↓
ChunkingStrategySelector 选择切分策略
    ├── .md/.markdown  → 按标题切分（保持章节结构）
    ├── Excel          → 按表格行切分（结构化数据成行）
    └── 其他/通用      → 递归切分 + Overlap
    ↓
生成 TextSegment 列表 → Embedding → 存入向量库
```

### 7.2 典型案例：Markdown技术文档

**为什么MD文档要按标题切分？**

- 技术文档天然按章节组织，"标题+正文"是语义完整单元
- 用户提问"什么是Lambda架构"时，检索命中整个"Lambda架构"章节，而非零散句子
- 切片自带标题上下文，LLM回答时能理解章节关系

**效果对比：**

```
固定切分命中："Lambda架构由Nathan Marz提出，用于处理海量数据..."（零散句子）
按标题切分命中："# Lambda架构\nLambda架构由Nathan Marz提出...它分为批处理和速度层..."（完整章节）
```

显然，第二种检索结果的质量远高于第一种。

---

## 八、高级话题

### 8.1 自适应切分（Adaptive Chunking）

固定参数的问题是：不同文档的句子长度、段落结构差异巨大。自适应切分根据**文档本身的特征**动态调整参数：

```java
/**
 * 自适应切分 —— 根据文档统计特征动态调整切分参数
 */
public class AdaptiveChunker {

    /**
     * 根据文档的平均句子长度动态调整maxChars
     *
     * 思路：
     * - 句子较短的文档（如技术文档）：用较小的切片（250字符）
     * - 句子较长的文档（如论文）：用较大的切片（400字符）
     *
     * 因为切分边界应尽量落在完整的句子上，
     * 切片大小应与句长成正比
     */
    public List<TextSegment> adaptiveSplit(String text) {
        // 统计平均句子长度
        double avgSentenceLen = computeAverageSentenceLength(text);

        // 根据句长动态调整切片大小
        int maxChars;
        if (avgSentenceLen < 20) {
            maxChars = 250;   // 短句为主
        } else if (avgSentenceLen < 40) {
            maxChars = 350;   // 中等句长
        } else {
            maxChars = 450;   // 长句为主
        }

        DocumentSplitter splitter = DocumentSplitters.recursive(maxChars, maxChars / 6);
        return splitter.split(Document.from(text));
    }

    private double computeAverageSentenceLength(String text) {
        // 按句号、问号、感叹号切分，计算平均长度
        String[] sentences = text.split("[。？！.!?]");
        if (sentences.length == 0) return 30;
        int total = 0;
        for (String s : sentences) {
            total += s.length();
        }
        return (double) total / sentences.length;
    }
}
```

### 8.2 上下文感知切分（Context-aware Chunking）

普通切分丢失了文档的全局上下文。上下文感知切分在每个切片中注入所属章节的标题信息（ruoyi-ai的Excel解析正是通过"列名: 值"保留上下文）：

```
普通切片：
"价格: 2999; 库存: 100;"

上下文感知切片：
"产品:RAG企业版 | 价格: 2999; 库存: 100;"
```

实现方式：切分时将`DocumentSegment.sectionTitle`注入每个切片的开头。**注意控制注入信息的长度**，标题太长会稀释正文的向量语义。

---

## 九、面试实战

### Q1: 切分对RAG检索质量有什么影响？常见的切分策略有哪些？

**参考答案：**

切分是RAG的"地基"——它决定了Embedding和检索的输入粒度。切分太粗，一个切片包含多个主题，检索命中噪音信息；切分太细，语义上下文断裂，关键信息丢失。

**三种主流策略：**
1. **固定Token切分**（递归切分）：先按段落、再按句子、最后按词递归切分，配合重叠窗口补偿边界信息。通用性最强。
2. **按结构切分**：以段落、Markdown标题为边界，保持章节完整性。适合结构化文档。
3. **语义切分**：用Embedding检测句子间的语义转折点，在转折处切分。质量最高但计算成本也最高。

**最佳实践：** 根据文档类型选择——MD文档按标题切分，通用文本用递归切分+Overlap，高质量场景用语义切分。重叠窗口通常设为切片大小的10-20%。

### Q2: 重叠窗口（Overlap）的作用是什么？设置多大合适？

**参考答案：**

**作用：** 防止关键信息恰好位于切片边界而被切断。相邻切片共享部分内容，保证无论命中哪个切片，边界处的信息都完整可用。

**设置原则：** 通常为切片大小的10-20%。过小失去补偿作用，过大导致索引体积和数据冗余增加（间接增加Embedding API费用）。

**追问应对：** 如果检索效果仍然不理想，除了调整Overlap，还应检查切分粒度本身（200-500 token区间）、切分边界是否落在语义完整的位置，以及是否需要更换策略（如从固定切分升级为语义切分）。

### Q3: 切分后如何评估质量？

**参考答案：**

切分质量有三种评估方式：

1. **语义完整性评估**：抽样检查切分边界，看是否有句子被截断。可以自动化：检查切片首尾是否以标点结/开始。更严谨的做法是让LLM判断切片是否语义完整，给出分数。

2. **下游检索效果评估**（最实用）：使用RAGAS指标，重点看**上下文召回率（Context Recall）**——标准答案中提到的关键信息，有多少出现在了检索结果中。这个指标直接反映切分是否"切丢了"信息。

3. **端到端对比评估**：同一批问题，分别用不同切分策略（不同粒度/不同策略）跑完整RAG，对比答案的忠实度（Faithfulness）和相关性（Relevance）。用数据选择最优策略。

**实战建议：** 建立固定评测集（30-50个问题+标准答案），每次调整切分参数后跑一遍，用指标对比，避免凭感觉调参。

### Q4: 语义切分相比固定切分的本质区别是什么？什么时候值得用？

**参考答案：**

**本质区别：** 固定切分依据的是**文本外部特征**（字符数、标点、换行），不理解内容；语义切分依据的是**文本的语义边界**——先计算每个句子的向量，检测向量距离突变的"语义转折点"，在转折处切分。后者保证每个切片内部主题一致。

**值得用的场景：**
- 文档主题跳跃频繁（如论文、研究报告、法律合同）
- 检索质量是核心KPI，允许增加30-50%的建库成本
- 切片需要作为独立单元被多轮检索反复命中的场景

**不值得用的场景：** 文档结构清晰（如技术文档有标题层级）、对成本敏感、快速迭代阶段。

---

> **下一篇预告：** 切片准备好之后，需要把文本变成向量——这就是Embedding模型的职责。OpenAI、智谱、通义、BGE-M3该怎么选？维度、语言、成本如何权衡？下一篇《Embedding模型选型指南》给你完整答案。