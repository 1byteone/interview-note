package com.ruoyi.ai.rag;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.segment.TextSegment;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RagPipelineTest - RAG 管线单元测试
 *
 * 测试重点：
 * 1. 文档解析器的格式识别
 * 2. 分块策略的正确性
 * 3. 分块参数的影响
 *
 * 注意：本测试不调用外部 API（嵌入模型/向量数据库）
 * 仅测试本地的解析和分块逻辑
 */
class RagPipelineTest {

    // ==================== 文件格式识别测试 ====================

    @Test
    @DisplayName("正确识别 PDF 文件类型")
    void shouldIdentifyPdfFileType() {
        // 验证 .pdf 后缀正确识别
        assertEquals(DocumentParserFactory.FileType.PDF,
                DocumentParserFactory.getFileType("report.pdf"));
        // 验证大小写不敏感
        assertEquals(DocumentParserFactory.FileType.PDF,
                DocumentParserFactory.getFileType("Report.PDF"));
    }

    @Test
    @DisplayName("正确识别 TXT 文件类型")
    void shouldIdentifyTxtFileType() {
        assertEquals(DocumentParserFactory.FileType.TXT,
                DocumentParserFactory.getFileType("readme.txt"));
    }

    @Test
    @DisplayName("正确识别 Markdown 文件类型")
    void shouldIdentifyMarkdownFileType() {
        assertEquals(DocumentParserFactory.FileType.MARKDOWN,
                DocumentParserFactory.getFileType("document.md"));
    }

    @Test
    @DisplayName("正确识别 DOCX 文件类型")
    void shouldIdentifyDocxFileType() {
        assertEquals(DocumentParserFactory.FileType.DOCX,
                DocumentParserFactory.getFileType("report.docx"));
        assertEquals(DocumentParserFactory.FileType.DOCX,
                DocumentParserFactory.getFileType("report.doc"));
    }

    @Test
    @DisplayName("不支持的文件格式应抛出异常")
    void shouldThrowForUnsupportedFileType() {
        // .xlsx 不是文档格式，应该抛出异常
        assertThrows(IllegalArgumentException.class,
                () -> DocumentParserFactory.getFileType("data.xlsx"));
        // .html 也不支持
        assertThrows(IllegalArgumentException.class,
                () -> DocumentParserFactory.getFileType("page.html"));
    }

    @Test
    @DisplayName("null 文件名应抛出异常")
    void shouldThrowForNullFileName() {
        assertThrows(IllegalArgumentException.class,
                () -> DocumentParserFactory.getFileType(null));
    }

    // ==================== 分块策略测试 ====================

    @Test
    @DisplayName("固定大小分块策略应创建有效分割器")
    void shouldCreateFixedSizeSplitter() {
        // 创建固定大小分割器：最大 500 字符，重叠 50 字符
        DocumentSplitter splitter = ChunkingStrategy.createFixedSizeSplitter(500, 50);
        assertNotNull(splitter);  // 验证分割器非空

        // 创建测试文档
        Document doc = Document.from("这是一段测试文本，用于验证分块功能是否正常工作。");
        // 执行分块
        List<TextSegment> segments = splitter.split(doc);
        assertNotNull(segments);
        assertFalse(segments.isEmpty());  // 至少应该产生一个 chunk
    }

    @Test
    @DisplayName("递归分块策略应正确切分长文本")
    void shouldSplitLongTextWithRecursiveStrategy() {
        // 创建较长的测试文本
        StringBuilder longText = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            longText.append("这是第").append(i).append("段内容。");
            if (i % 10 == 9) {
                longText.append("\n\n");  // 每 10 段加一个段落分隔
            }
        }

        Document doc = Document.from(longText.toString());
        // 使用较小的 chunk 大小，确保会产生多个 chunk
        DocumentSplitter splitter = ChunkingStrategy.createRecursiveSplitter(200, 20);
        List<TextSegment> segments = splitter.split(doc);

        // 验证产生了多个 chunk
        assertTrue(segments.size() > 1, "长文本应该被切分为多个 chunk");
        // 每个 chunk 长度不应超过 200 + overlap（边界情况可能略超）
        for (TextSegment segment : segments) {
            assertTrue(segment.text().length() <= 220,
                    "chunk 长度应该在合理范围内: " + segment.text().length());
        }
    }

    @Test
    @DisplayName("重叠大小影响相邻 chunk 的内容重叠")
    void shouldHaveOverlapBetweenChunks() {
        // 创建测试文本
        String text = "AAAAAAAAAA\n\nBBBBBBBBBB\n\nCCCCCCCCCC\n\nDDDDDDDDDD";
        Document doc = Document.from(text);

        // 使用有重叠的分块器
        DocumentSplitter splitterWithOverlap = ChunkingStrategy.createRecursiveSplitter(25, 10);
        List<TextSegment> segmentsWithOverlap = splitterWithOverlap.split(doc);

        // 使用无重叠的分块器
        DocumentSplitter splitterNoOverlap = ChunkingStrategy.createRecursiveSplitter(25, 0);
        List<TextSegment> segmentsNoOverlap = splitterNoOverlap.split(doc);

        // 有重叠时总文本长度应该更长（因为重复内容）
        int totalLengthWithOverlap = segmentsWithOverlap.stream()
                .mapToInt(s -> s.text().length()).sum();
        int totalLengthNoOverlap = segmentsNoOverlap.stream()
                .mapToInt(s -> s.text().length()).sum();

        assertTrue(totalLengthWithOverlap >= totalLengthNoOverlap,
                "有重叠的分块总文本长度应该 >= 无重叠的分块");
    }

    @Test
    @DisplayName("分块策略类型枚举应包含所有策略")
    void shouldHaveAllStrategyTypes() {
        // 验证枚举值数量
        assertEquals(3, ChunkingStrategy.StrategyType.values().length);
        // 验证枚举值名称
        assertNotNull(ChunkingStrategy.StrategyType.FIXED_SIZE);
        assertNotNull(ChunkingStrategy.StrategyType.PARAGRAPH);
        assertNotNull(ChunkingStrategy.StrategyType.RECURSIVE);
    }
}
