package com.ruoyi.ai.rag;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.redis.RedisEmbeddingStore;
import lombok.Builder;
import lombok.Data;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

/**
 * RagPipeline - RAG（检索增强生成）完整管线
 *
 * RAG 工作流程图：
 *
 *   ┌──────────────────────────────────────────────────────────────────┐
 *   │                        RAG 管线架构图                            │
 *   ├──────────────────────────────────────────────────────────────────┤
 *   │                                                                  │
 *   │  [索引阶段]                                                      │
 *   │   原始文档 ──→ 文档解析 ──→ 分块 ──→ 向量化 ──→ 向量数据库存储    │
 *   │   (.pdf/.txt)  ParserFactory  ChunkStrategy  EmbeddingModel  Redis│
 *   │                                                                  │
 *   │  [检索阶段]                                                      │
 *   │   用户问题 ──→ 问题向量化 ──→ 向量相似度检索 ──→ Top-K 相关片段   │
 *   │   Query         EmbeddingModel     Redis            Context      │
 *   │                                                                  │
 *   │  [生成阶段]                                                      │
 *   │   问题 + 上下文 ──→ LLM 生成回答 ──→ 返回给用户                   │
 *   │   Prompt Template  ChatModel        Response                    │
 *   │                                                                  │
 *   └──────────────────────────────────────────────────────────────────┘
 *
 * 本类实现：
 * 1. 文档导入与索引（ingest）
 * 2. 相似度检索（retrieve）
 * 3. 端到端问答（query）
 */
@Data
@Builder
public class RagPipeline {

    /**
     * 嵌入模型 - 将文本转换为向量
     * 使用 OpenAI 或 DashScope 的 Embedding API
     */
    private EmbeddingModel embeddingModel;

    /**
     * 向量存储 - 使用 Redis 存储和检索向量
     * Redis 支持向量相似度搜索（Redis Stack / RediSearch 模块）
     */
    private EmbeddingStore<TextSegment> embeddingStore;

    /**
     * 文档分块策略配置
     */
    private ChunkingStrategy.StrategyType strategyType;
    private int maxChunkSize;
    private int overlap;

    /**
     * 检索时返回的 Top-K 相关片段数量
     * 越大返回上下文越多，但可能引入噪音
     */
    private int topK;

    // ==================== 索引阶段 ====================

    /**
     * 将单个文档导入 RAG 索引
     *
     * 完整流程：
     * 1. 解析文档内容 → Document 对象
     * 2. 分块处理 → List<TextSegment>
     * 3. 向量化 → List<Embedding>
     * 4. 存入向量数据库
     *
     * @param documentPath 文档路径
     * @param inputStream  文档输入流
     * @param fileName     文件名（用于自动识别格式）
     * @return 成功导入的 chunk 数量
     */
    public int ingestDocument(Path documentPath, InputStream inputStream, String fileName) {
        // 步骤 1：根据文件类型选择解析器，解析文档
        Document document = DocumentParserFactory.parse(fileName, inputStream);

        // 步骤 2：创建分块器，将文档切分为多个 TextSegment
        DocumentSplitter splitter = ChunkingStrategy.createSplitter(
                strategyType,    // 使用配置的分块策略
                maxChunkSize,    // chunk 最大长度
                overlap          // 重叠窗口大小
        );
        List<TextSegment> segments = splitter.split(document);

        // 步骤 3：调用嵌入模型，将所有 TextSegment 转换为向量
        // embedAll 批量处理，减少 API 调用次数
        List<Embedding> embeddings = embeddingModel.embedAll(segments).content();

        // 步骤 4：将向量和原文一起存入向量数据库
        embeddingStore.addAll(embeddings, segments);

        // 返回成功导入的 chunk 数量
        return segments.size();
    }

    // ==================== 检索阶段 ====================

    /**
     * 根据用户问题检索最相关的文档片段
     *
     * 检索流程：
     * 1. 将用户问题转换为向量
     * 2. 在向量数据库中执行相似度搜索
     * 3. 返回 Top-K 最相关的 TextSegment
     *
     * @param queryText 用户的问题文本
     * @return 最相关的文档片段列表
     */
    public List<TextSegment> retrieve(String queryText) {
        // 步骤 1：将问题文本转换为查询向量
        Embedding queryEmbedding = embeddingModel.embed(queryText).content();

        // 步骤 2：在向量数据库中执行相似度搜索
        // findRelevant 返回最相似的 Top-K 个结果
        List<EmbeddingMatch<TextSegment>> matches = embeddingStore.findRelevant(
                queryEmbedding,     // 查询向量
                topK                // 返回最相似的 K 个结果
        );

        // 步骤 3：提取 TextSegment 内容并返回
        return matches.stream()
                .map(EmbeddingMatch::embedded)  // 从匹配结果中提取原始 TextSegment
                .collect(Collectors.toList());
    }

    /**
     * 获取格式化的上下文字符串（用于 Prompt 模板）
     *
     * 将检索到的文档片段格式化为：
     * [1] 片段内容一
     * [2] 片段内容二
     * ...
     *
     * @param queryText 用户问题
     * @return 格式化的上下文字符串
     */
    public String getFormattedContext(String queryText) {
        List<TextSegment> segments = retrieve(queryText);
        // 使用编号格式化，方便 LLM 引用来源
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < segments.size(); i++) {
            sb.append(String.format("[%d] %s\n", i + 1, segments.get(i).text()));
        }
        return sb.toString();
    }
}
