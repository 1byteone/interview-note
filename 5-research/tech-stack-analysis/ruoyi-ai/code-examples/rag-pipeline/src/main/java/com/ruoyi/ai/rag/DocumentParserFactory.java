package com.ruoyi.ai.rag;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.parser.TextDocumentParser;
import dev.langchain4j.data.document.parser.apache.pdfbox.ApachePdfBoxDocumentParser;
import dev.langchain4j.data.document.parser.apache.tika.ApacheTikaDocumentParser;

import java.io.InputStream;
import java.nio.file.Path;

/**
 * DocumentParserFactory - 多格式文档解析工厂
 *
 * 职责：根据文件类型自动选择合适的文档解析器
 *
 * 支持的文件格式及解析策略：
 * 1. TXT  → TextDocumentParser（纯文本直接读取）
 * 2. PDF  → ApachePdfBoxDocumentParser（PDFBox 提取文字）
 * 3. DOCX → ApacheTikaDocumentParser（Tika 解析 Office 格式）
 * 4. MD   → TextDocumentParser（Markdown 当作纯文本处理）
 *
 * 设计模式：工厂模式 + 策略模式
 * - 工厂：根据文件后缀名选择解析器
 * - 策略：每个解析器是独立的解析策略
 */
public class DocumentParserFactory {

    // 枚举：文件类型，用于解析器选择
    public enum FileType {
        TXT, PDF, DOCX, MARKDOWN
    }

    /**
     * 根据文件后缀名判断文件类型
     *
     * @param fileName 文件名（如 report.pdf、readme.md）
     * @return 对应的 FileType 枚举值
     */
    public static FileType getFileType(String fileName) {
        if (fileName == null) {
            throw new IllegalArgumentException("文件名不能为 null");
        }
        // 转换为小写并提取后缀名
        String lowerName = fileName.toLowerCase();
        if (lowerName.endsWith(".pdf")) {
            return FileType.PDF;
        } else if (lowerName.endsWith(".docx") || lowerName.endsWith(".doc")) {
            return FileType.DOCX;
        } else if (lowerName.endsWith(".md")) {
            return FileType.MARKDOWN;
        } else if (lowerName.endsWith(".txt")) {
            return FileType.TXT;
        } else {
            // 不支持的格式抛出异常
            throw new IllegalArgumentException(
                "不支持的文件格式: " + fileName + "，支持: .txt .pdf .docx .md"
            );
        }
    }

    /**
     * 根据文件类型创建对应的文档解析器
     *
     * @param fileType 文件类型枚举
     * @return LangChain4j DocumentParser 实例
     */
    public static dev.langchain4j.data.document.DocumentParser createParser(FileType fileType) {
        return switch (fileType) {
            case TXT, MARKDOWN -> new TextDocumentParser();          // 纯文本/Md：直接读取
            case PDF -> new ApachePdfBoxDocumentParser();            // PDF：使用 PDFBox 提取
            case DOCX -> new ApacheTikaDocumentParser();             // Office：使用 Tika 解析
        };
    }

    /**
     * 解析文件为 LangChain4j Document 对象
     *
     * @param fileName 文件名
     * @param inputStream 文件输入流
     * @return 解析后的 Document 对象，包含文本内容和元数据
     */
    public static Document parse(String fileName, InputStream inputStream) {
        // 1. 根据文件名判断类型
        FileType fileType = getFileType(fileName);
        // 2. 创建对应解析器
        dev.langchain4j.data.document.DocumentParser parser = createParser(fileType);
        // 3. 执行解析并返回结果
        return parser.parse(inputStream);
    }
}
