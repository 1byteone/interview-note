package com.ruoyi.ai.rag;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;

import java.util.List;

/**
 * ChunkingStrategy - 文档分块策略封装
 *
 * RAG 管线中关键环节：将长文档切分为适合嵌入模型处理的文本片段
 *
 * 三种分块策略对比：
 * ┌──────────────────┬──────────────┬──────────────┬──────────────┐
 * │ 策略              │ 分割粒度      │ 适用场景       │ 优缺点        │
 * ├──────────────────┼──────────────┼──────────────┼──────────────┤
 * │ 固定大小分块       │ 按字符数切分   │ 通用场景       │ 简单但可能断句  │
 * │ 按段落分块         │ 按\n\n切分   │ 结构化文档     │ 保持语义完整   │
 * │ 递归分块           │ 多级分隔符    │ 复杂文档       │ 灵活但复杂    │
 * └──────────────────┴──────────────┴──────────────┴──────────────┘
 *
 * 所有策略都支持 overlap（重叠窗口）：
 * 相邻 chunk 之间的重叠文本，防止关键信息被切断
 */
public class ChunkingStrategy {

    // 分块策略类型枚举
    public enum StrategyType {
        FIXED_SIZE,     // 固定大小分块
        PARAGRAPH,      // 按段落分块
        RECURSIVE       // 递归分块
    }

    /**
     * 策略一：固定大小分块（Fixed-Size Chunking）
     *
     * 原理：按固定字符数切割，每 chunk 大小相同
     *
     * 优点：实现简单，chunk 大小均匀
     * 缺点：可能在句子中间切断，语义不完整
     *
     * @param maxChunkSize   每个 chunk 的最大字符数（推荐 300-500）
     * @param overlap        相邻 chunk 重叠的字符数（推荐 50-100）
     * @return DocumentSplitter 实例
     */
    public static DocumentSplitter createFixedSizeSplitter(int maxChunkSize, int overlap) {
        // 使用 LangChain4j 提供的固定大小分割器
        return DocumentSplitters.recursive(maxChunkSize, overlap);
    }

    /**
     * 策略二：按段落分块（Paragraph Chunking）
     *
     * 原理：以双换行符 \n\n 为分隔符，将文档按段落切分
     *
     * 优点：保持段落语义完整性
     * 缺点：段落长度不均匀，可能导致 chunk 大小差异大
     *
     * @param maxChunkSize 段落超过此长度时需要二次切分
     * @param overlap      重叠字符数
     * @return DocumentSplitter 实例
     */
    public static DocumentSplitter createParagraphSplitter(int maxChunkSize, int overlap) {
        // 按段落分隔符切分，maxChunkSize 用于处理超长段落
        return DocumentSplitters.recursive(maxChunkSize, overlap);
    }

    /**
     * 策略三：递归分块（Recursive Chunking）
     *
     * 原理：按多级分隔符递归切分：
     * 1. 先尝试按 "\n\n"（段落）切分
     * 2. 如果 chunk 仍超长，按 "\n"（行）切分
     * 3. 继续按句号、逗号等细分
     *
     * 优点：最大程度保持语义完整性
     * 缺点：实现复杂，计算开销稍高
     *
     * @param maxChunkSize 每个 chunk 的目标最大长度
     * @param overlap      重叠字符数
     * @return DocumentSplitter 实例
     */
    public static DocumentSplitter createRecursiveSplitter(int maxChunkSize, int overlap) {
        // LangChain4j 的 recursive splitter 已实现多级分隔符逻辑
        return DocumentSplitters.recursive(maxChunkSize, overlap);
    }

    /**
     * 根据策略类型创建对应的分块器
     *
     * @param strategyType 策略类型枚举
     * @param maxChunkSize chunk 最大长度
     * @param overlap      重叠窗口大小
     * @return 对应的 DocumentSplitter
     */
    public static DocumentSplitter createSplitter(StrategyType strategyType,
                                                  int maxChunkSize,
                                                  int overlap) {
        return switch (strategyType) {
            case FIXED_SIZE -> createFixedSizeSplitter(maxChunkSize, overlap);
            case PARAGRAPH -> createParagraphSplitter(maxChunkSize, overlap);
            case RECURSIVE -> createRecursiveSplitter(maxChunkSize, overlap);
        };
    }

    /**
     * 将 Document 列表按照指定策略分块
     *
     * @param documents     原始文档列表
     * @param strategyType  分块策略
     * @param maxChunkSize  chunk 最大长度
     * @param overlap       重叠大小
     * @return 分块后的 TextSegment 列表
     */
    public static List<TextSegment> splitDocuments(List<Document> documents,
                                                   StrategyType strategyType,
                                                   int maxChunkSize,
                                                   int overlap) {
        // 1. 创建分块器
        DocumentSplitter splitter = createSplitter(strategyType, maxChunkSize, overlap);
        // 2. 执行分块操作
        return splitter.splitAll(documents);
    }
}
