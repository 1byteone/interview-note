# RAG 全链路入门：从文档解析到智能检索的完整实现

> 本文是 ruoyi-ai 项目技术栈深度剖析系列的第3篇，面向 Java 后端开发者，旨在帮助读者从零搭建一个 RAG 全链路应用，涵盖文档解析、智能切分、向量化、检索与重排序的完整流程，并对照分析 ruoyi-ai 项目中的 RAG 管线真实实现。

---

## 一、项目背景：该技术栈在项目中的角色

### 1.1 为什么需要 RAG 管线

在企业级 AI 应用中，一个核心场景是"让 AI 理解企业内部知识库"。用户上传 PDF 合同、Word 报告、技术文档，期望 AI 能够基于这些文档回答问题。然而，直接使用 LLM 面临几个关键挑战：

- **上下文窗口限制**：LLM 的上下文窗口有限（如 128K tokens），无法一次性读完整个知识库
- **知识滞后**：LLM 的训练数据有截止日期，不了解企业内部最新文档
- **幻觉问题**：LLM 可能编造不存在的"事实"，回答缺乏可靠依据
- **格式多样**：PDF、Word、Excel 等格式需要统一处理才能被 LLM 理解

RAG（Retrieval-Augmented Generation，检索增强生成）正是为解决这些问题而生。它的核心思想是：**不依赖 LLM 内部知识，而是先从外部知识库中检索与用户问题相关的文档片段，将这些片段作为上下文注入 LLM，让 LLM 基于检索到的内容生成回答**。这样既解决了知识时效性问题，又大幅降低了幻觉风险。

### 1.2 在 ruoyi-ai 项目中的位置

在 ruoyi-ai 的四层架构中，RAG 管线位于 **AI 层** 的核心位置，是连接知识库和 LLM 的桥梁：

```
展现层（Vue 3 / Vben）
    ↓ HTTP/SSE
应用层（ruoyi-admin / ruoyi-chat）
    ↓
AI 层 → RAG 管线（文档解析 → 切分 → 向量化 → 检索 → Rerank）
    ↓
基础设施层（向量数据库 / Neo4j 图数据库 / LLM API）
```

具体来说，RAG 管线在项目中承担了以下职责：

- **文档解析**：统一处理 PDF、Word、Markdown、Excel 四种格式，提取纯文本内容
- **智能切分**：将长文档切割为语义完整的短文本片段，保持上下文连贯
- **向量化存储**：将文本片段通过 Embedding 模型转为向量，存入向量数据库
- **检索增强**：用户提问时，从向量库召回相关文档，同时结合 Neo4j 知识图谱增强检索
- **重排序**：对召回结果进行二次精排，确保注入 LLM 的上下文质量最高

### 1.3 本文目标

本文的目标是帮助读者从零搭建一个最简的 RAG 管线应用，实现"上传文档 -> 解析 -> 切分 -> 向量化 -> 检索 -> 回答"的完整流程。通过这个最小可行示例，读者将理解：

1. 四种文档解析器的实现原理和选择策略
2. 三种切分策略（Token、Character、Markdown）的适用场景
3. Embedding 模型的选择和调用方式
4. 检索 + Rerank 双阶段设计的精妙之处

---

## 二、核心概念：3个，用生活类比解释

### 概念 1：Chunking（文档切分）—— 就像"切蛋糕"

**生活类比**：想象你有一个大蛋糕（整篇文档），需要分给很多人吃。如果切得太小，每个人只能吃到一小块碎片，看不到蛋糕的全貌（语义断裂）；如果切得太大，一个人吃不完（超出 LLM 上下文窗口）。最好的方式是：按蛋糕的天然分层（标题、段落）来切，每块保留完整的一层，并在相邻两块之间留一点重叠的奶油（Overlap），确保不会漏掉任何信息。

**技术映射**：Chunking 就是将长文档按策略切割为短文本片段，每个片段用于 Embedding 和检索：

- **Token 切分**：按 Token 数精确切分，适合精确控制上下文窗口大小
- **Character 切分**：按固定字符数切分，在句号处智能截断，简单高效
- **Markdown 切分**：按标题层级分割，保持章节语义完整性，适合技术文档

**关键点**：切分粒度直接影响检索质量。切分太粗，一个片段包含多主题，检索时命中噪音；切分太细，语义上下文断裂，丢失关键信息。

### 概念 2：Embedding（向量化）—— 就像"给文档拍语义照片"

**生活类比**：想象你有一个巨大的图书馆，你需要在其中找到与"人工智能在医疗领域的应用"相关的书。传统方式是靠图书分类卡片（关键词匹配），但你用"AI"这个词搜不到"机器学习"的书籍——因为关键词匹配无法理解语义。而 Embedding 就像给每本书拍一张"语义照片"，照片上记录了这本书的"核心主题"。当你搜索时，你的问题也被拍成照片，系统通过比较照片的相似度，找到语义最相关的书，即使关键词不完全匹配。

**技术映射**：Embedding 模型将文本转为高维向量（如 1536 维浮点数数组），语义相近的文本在向量空间中距离更近：

- **向量维度**：维度越高信息越丰富，但存储和计算成本也越高
- **语义搜索**：不再依赖关键词匹配，而是理解文本的语义含义
- **多服务商**：项目集成 OpenAI、智谱、通义千问、SiliconFlow 四家 Embedding 服务商

### 概念 3：Rerank（重排序）—— 就像"海选后的专家评审"

**生活类比**：想象一档选秀节目。海选阶段，评委需要在 1000 名选手中快速选出 50 人（要求速度快，可以粗略筛选）。到了复赛阶段，评委要对这 50 人进行更细致的评审，最终选出 5 人进入决赛（精度要求高，可以花更多时间）。海选阶段追求"不能漏掉好苗子"（高召回），复赛阶段追求"选出的都是最好的"（高精度）。

**技术映射**：Rerank 就是 RAG 管线中的"复赛评审"：

- **第一阶段（向量检索）**：用 HNSW 索引从海量文档中快速召回 Top-50，追求高召回率
- **第二阶段（Rerank）**：用 Cross-Encoder 模型对候选文档与 Query 进行精确相关性打分，精排取 Top-5，追求高精度
- **为什么需要 Rerank**：向量检索是"近似"匹配，HNSW 索引为了速度牺牲了精度，Rerank 用更精确的模型做二次过滤

---

## 三、从零搭建：完整代码

### 3.1 项目结构

```
rag-demo/
├── pom.xml
├── src/main/java/com/ragdemo/
│   ├── RagDemoApplication.java        # 启动类
│   ├── parser/
│   │   ├── DocumentParser.java        # 文档解析器接口
│   │   ├── PdfDocumentParser.java     # PDF 解析器
│   │   ├── WordDocumentParser.java    # Word 解析器
│   │   ├── MarkdownDocumentParser.java # Markdown 解析器
│   │   └── DocumentParserFactory.java # 解析器工厂
│   ├── splitter/
│   │   └── DocumentSplitter.java      # 文档切分器
│   ├── embedding/
│   │   ├── EmbeddingModelFactory.java # Embedding 模型工厂
│   │   └── EmbeddingService.java      # 向量化服务
│   ├── retrieval/
│   │   ├── RagRetrievalService.java   # RAG 检索服务
│   │   └── RerankService.java         # Rerank 接口
│   └── controller/
│       └── RagController.java         # REST 控制器
└── src/main/resources/
    └── application.yml                # 配置文件
```

### 3.2 pom.xml —— 基础依赖配置

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.5.0</version>
        <relativePath/>
    </parent>

    <groupId>com.ragdemo</groupId>
    <artifactId>rag-demo</artifactId>
    <version>1.0.0</version>
    <name>rag-demo</name>
    <description>RAG 全链路入门示例</description>

    <properties>
        <java.version>21</java.version>
        <langchain4j.version>1.13.0</langchain4j.version>
    </properties>

    <dependencies>
        <!-- Spring Boot Web -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>

        <!-- LangChain4j 核心依赖 -->
        <dependency>
            <groupId>dev.langchain4j</groupId>
            <artifactId>langchain4j</artifactId>
            <version>${langchain4j.version}</version>
        </dependency>

        <!-- LangChain4j Spring Boot Starter -->
        <dependency>
            <groupId>dev.langchain4j</groupId>
            <artifactId>langchain4j-spring-boot-starter</artifactId>
            <version>${langchain4j.version}</version>
        </dependency>

        <!-- 文档解析依赖 -->
        <!-- PDF 解析：Apache PDFBox -->
        <dependency>
            <groupId>org.apache.pdfbox</groupId>
            <artifactId>pdfbox</artifactId>
            <version>3.0.3</version>
        </dependency>
        <!-- Word 和 Excel 解析：Apache POI -->
        <dependency>
            <groupId>org.apache.poi</groupId>
            <artifactId>poi-ooxml</artifactId>
            <version>5.3.0</version>
        </dependency>

        <!-- 向量数据库：使用内存 EmbeddingStore 简化示例 -->
        <dependency>
            <groupId>dev.langchain4j</groupId>
            <artifactId>langchain4j-core</artifactId>
            <version>${langchain4j.version}</version>
        </dependency>

        <!-- 测试 -->
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

### 3.3 application.yml —— 应用配置

```yaml
server:
  port: 8080

spring:
  application:
    name: rag-demo

# LangChain4j 配置
langchain4j:
  # 默认使用 OpenAI 兼容的 Embedding 模型
  embedding-model:
    provider: openai
    model: text-embedding-3-small
    dimensions: 1536
    api-key: ${OPENAI_API_KEY}

# RAG 配置
rag:
  chunking:
    # 默认切分策略：token / character / markdown
    strategy: token
    # 最大 Token 数（按 Token 切分时）
    max-tokens: 512
    # 重叠 Token 数
    overlap-tokens: 64
  retrieval:
    # 向量检索召回数量
    top-k: 50
    # Rerank 后保留数量
    top-n: 5
```

### 3.4 核心代码实现

#### 3.4.1 文档解析器接口和工厂

```java
package com.ragdemo.parser;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.InputStream;
import java.util.List;

/**
 * 文档解析器接口 —— 所有文档解析器统一实现的接口
 *
 * 职责：将不同格式的文档解析为统一的文本片段列表
 * 每个片段保留页码信息，便于溯源
 */
public interface DocumentParser {

    /**
     * 解析文档输入流，返回文本片段列表
     *
     * @param inputStream 文档的输入流
     * @return 解析后的文本片段列表，每个片段包含文本内容和页码
     */
    List<DocumentSegment> parse(InputStream inputStream);
}

/**
 * 文档片段 —— 解析后的最小单位
 * 包含文本内容和其来源页码
 */
@Data
@AllArgsConstructor
class DocumentSegment {
    /** 提取的文本内容 */
    private String text;
    /** 来源页码（从 1 开始） */
    private int pageNumber;
}
```

```java
package com.ragdemo.parser;

import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.InputStream;
import java.util.List;

/**
 * 文档解析器工厂 —— 根据文件后缀自动选择对应的解析策略
 *
 * 设计模式：工厂模式 + 策略模式
 * 新增文件格式只需添加 Parser 实现类并注册到工厂
 */
@Component
public class DocumentParserFactory {

    @Resource
    private PdfDocumentParser pdfParser;
    @Resource
    private WordDocumentParser wordParser;
    @Resource
    private MarkdownDocumentParser markdownParser;

    /**
     * 根据文件名获取对应的文档解析器
     *
     * @param fileName 文件名（含后缀）
     * @return 匹配的文档解析器
     * @throws IllegalArgumentException 如果不支持该文件格式
     */
    public DocumentParser getParser(String fileName) {
        // 提取文件后缀并转为小写，用于匹配解析器
        String suffix = fileName.substring(fileName.lastIndexOf(".")).toLowerCase();
        return switch (suffix) {
            case ".pdf", ".PDF" -> pdfParser;                // PDF 文件 → PDF 解析器
            case ".docx", ".doc" -> wordParser;              // Word 文件 → Word 解析器
            case ".md", ".markdown" -> markdownParser;       // Markdown 文件 → Markdown 解析器
            case ".xlsx", ".xls" -> excelParser;             // Excel 文件 → Excel 解析器
            default -> throw new IllegalArgumentException("不支持的文件格式: " + suffix);
        };
    }

    /**
     * 解析文档，返回统一文本片段列表
     *
     * @param fileName    文件名
     * @param inputStream 文件输入流
     * @return 解析后的文本片段列表
     */
    public List<DocumentSegment> parse(String fileName, InputStream inputStream) {
        DocumentParser parser = getParser(fileName);
        return parser.parse(inputStream);
    }
}
```

```java
package com.ragdemo.parser;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * PDF 解析器 —— 使用 Apache PDFBox 从 PDF 文档中提取文本
 *
 * 实现原理：
 * 1. 使用 PDFBox 的 Loader 加载 PDF 文档
 * 2. 使用 PDFTextStripper 逐页提取文本内容
 * 3. 每页作为一个独立片段，保留页码信息便于溯源
 */
@Component
public class PdfDocumentParser implements DocumentParser {

    @Override
    public List<DocumentSegment> parse(InputStream inputStream) {
        List<DocumentSegment> segments = new ArrayList<>();
        try (PDDocument document = Loader.loadPDF(inputStream.readAllBytes())) {
            // 创建 PDF 文本提取器
            PDFTextStripper stripper = new PDFTextStripper();
            // 遍历每一页，逐页提取文本
            for (int i = 1; i <= document.getNumberOfPages(); i++) {
                stripper.setStartPage(i);      // 设置起始页
                stripper.setEndPage(i);        // 设置结束页（只提取当前页）
                String pageText = stripper.getText(document); // 提取当前页文本
                // 每页作为一个独立段落，保留页码信息便于溯源
                segments.add(new DocumentSegment(pageText.trim(), i));
            }
        } catch (Exception e) {
            throw new RuntimeException("PDF 解析失败", e);
        }
        return segments;
    }
}
```

```java
package com.ragdemo.parser;

import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.List;

/**
 * Word 解析器 —— 使用 Apache POI 从 docx 文档中提取文本
 *
 * 实现原理：
 * 1. 使用 POI 的 XWPFDocument 加载 Word 文档
 * 2. 使用 XWPFWordExtractor 提取全部文本
 * 3. 由于 Word 文档页数信息不易精确获取，整体作为一个片段
 */
@Component
public class WordDocumentParser implements DocumentParser {

    @Override
    public List<DocumentSegment> parse(InputStream inputStream) {
        try (XWPFDocument document = new XWPFDocument(inputStream)) {
            // 使用 POI 的文本提取器提取全部文本
            XWPFWordExtractor extractor = new XWPFWordExtractor(document);
            String text = extractor.getText();
            // Word 文档整体作为一个片段，页码设为 1
            return List.of(new DocumentSegment(text.trim(), 1));
        } catch (Exception e) {
            throw new RuntimeException("Word 解析失败", e);
        }
    }
}
```

```java
package com.ragdemo.parser;

import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Markdown 解析器 —— 直接读取 Markdown 文本内容
 *
 * 实现原理：
 * 1. 直接读取文件内容为字符串
 * 2. Markdown 本身是纯文本，无需特殊解析
 * 3. 后续由 DocumentSplitter 按标题层级切分
 */
@Component
public class MarkdownDocumentParser implements DocumentParser {

    @Override
    public List<DocumentSegment> parse(InputStream inputStream) {
        try {
            // 直接读取所有字节，转为 UTF-8 字符串
            String text = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            // Markdown 整体作为一个片段，保留标题结构
            return List.of(new DocumentSegment(text.trim(), 1));
        } catch (Exception e) {
            throw new RuntimeException("Markdown 解析失败", e);
        }
    }
}
```

#### 3.4.2 文档切分器 —— 支持三种策略

```java
package com.ragdemo.splitter;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.splitter.DocumentByParagraphSplitter;
import dev.langchain4j.data.segment.TextSegment;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 文档切分器 —— 支持三种切分策略
 *
 * 设计模式：策略模式，运行时根据配置选择切分方式
 * 三种策略各有适用场景，可根据文档类型灵活选择
 */
@Component
public class DocumentSplitter {

    /**
     * 策略 1：按 Token 切分
     *
     * 使用 LangChain4j 的 DocumentByParagraphSplitter
     * 按段落边界切分，每个片段不超过 maxTokens 个 Token
     * 相邻片段之间保留 overlapTokens 个 Token 的重叠
     *
     * 适用场景：通用文本，需要精确控制 LLM 上下文窗口
     *
     * @param text          待切分的文本
     * @param maxTokens     每个片段的最大 Token 数
     * @param overlapTokens 相邻片段的重叠 Token 数
     * @return 切分后的文本片段列表
     */
    public List<TextSegment> splitByToken(String text, int maxTokens, int overlapTokens) {
        // 将文本包装为 LangChain4j 的 Document 对象
        Document doc = Document.from(text);
        // 使用 LangChain4j 内置的段落级切分器
        // 自动按段落边界切分，避免切断语义完整的段落
        DocumentByParagraphSplitter splitter = new DocumentByParagraphSplitter(maxTokens, overlapTokens);
        return splitter.split(doc);
    }

    /**
     * 策略 2：按字符数切分
     *
     * 按固定字符数切分，并在句号处智能截断
     * 避免切断语义完整的句子
     *
     * 适用场景：英文等单字节文本，简单高效
     * 不依赖外部依赖，纯字符串操作
     *
     * @param text         待切分的文本
     * @param maxChars     每个片段的最大字符数
     * @param overlapChars 相邻片段的重叠字符数
     * @return 切分后的文本片段列表
     */
    public List<TextSegment> splitByCharacter(String text, int maxChars, int overlapChars) {
        List<TextSegment> segments = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            // 计算当前片段的结束位置
            int end = Math.min(start + maxChars, text.length());
            // 如果还没到文本末尾，尝试在句号处截断
            if (end < text.length()) {
                // 从 end 位置向前查找最近的句号
                int lastPeriod = text.lastIndexOf("。", end);
                // 只在前半段范围内查找句号，避免切分过短
                if (lastPeriod > start + maxChars / 2) {
                    end = lastPeriod + 1; // 在句号处截断（包含句号）
                }
            }
            // 添加当前片段
            segments.add(TextSegment.from(text.substring(start, end)));
            // 计算下一次切分的起点：当前位置减去重叠字符数
            // 重叠窗口保证边界信息不丢失
            start = end - overlapChars;
        }
        return segments;
    }

    /**
     * 策略 3：按 Markdown 标题层级切分
     *
     * 以 # / ## / ### 等标题为边界进行分割
     * 每个标题及其跟随的内容作为一个独立片段
     * 保持标题和内容的语义完整性
     *
     * 适用场景：Markdown 技术文档、博客文章
     * 依赖文档有正确的标题层级结构
     *
     * @param markdownText 待切分的 Markdown 文本
     * @return 切分后的文本片段列表
     */
    public List<TextSegment> splitByMarkdown(String markdownText) {
        List<TextSegment> segments = new ArrayList<>();
        // 使用正则匹配所有标题行（以 # 开头）
        Pattern pattern = Pattern.compile("^(#+ .+)$", Pattern.MULTILINE);
        Matcher matcher = pattern.matcher(markdownText);

        // 收集所有标题行及其位置
        List<String> titles = new ArrayList<>();
        List<Integer> positions = new ArrayList<>();
        while (matcher.find()) {
            titles.add(matcher.group(1));     // 标题文本
            positions.add(matcher.start());   // 标题在原文中的位置
        }

        // 如果没找到标题，整体作为一个片段返回
        if (titles.isEmpty()) {
            segments.add(TextSegment.from(markdownText));
            return segments;
        }

        // 按标题位置分割文本：每个标题到下一个标题之间的内容
        for (int i = 0; i < titles.size(); i++) {
            int startPos = positions.get(i);
            // 下一个标题的位置作为当前片段的结束位置
            int endPos = (i + 1 < positions.size()) ? positions.get(i + 1) : markdownText.length();
            // 提取标题 + 内容
            String segment = markdownText.substring(startPos, endPos).trim();
            segments.add(TextSegment.from(segment));
        }

        return segments;
    }
}
```

#### 3.4.3 Embedding 模型工厂和向量化服务

```java
package com.ragdemo.embedding;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Embedding 模型工厂 —— 统一封装多家 Embedding 服务商的调用
 *
 * 设计模式：工厂模式
 * 通过配置中的 provider 参数切换底层实现
 * 业务代码只依赖 EmbeddingModel 接口，无需感知具体实现
 */
@Component
public class EmbeddingModelFactory {

    @Value("${langchain4j.embedding-model.api-key}")
    private String apiKey;

    /**
     * 根据配置创建对应的 Embedding 模型实例
     *
     * 支持 OpenAI / ZhipuAI / 通义千问 / SiliconFlow 四家服务商
     * 通过 switch 表达式选择，新增服务商只需添加一个 case
     *
     * @param provider 服务商标识：openai / zhipu / tongyi / siliconflow
     * @return EmbeddingModel 实例
     */
    public EmbeddingModel createEmbeddingModel(String provider) {
        return switch (provider) {
            case "openai" -> OpenAiEmbeddingModel.builder()
                    .apiKey(apiKey)
                    .modelName("text-embedding-3-small")  // 1536 维，性价比最优
                    .build();
            case "zhipu" -> OpenAiEmbeddingModel.builder()
                    .baseUrl("https://open.bigmodel.cn/api/paas/v4")
                    .apiKey(apiKey)
                    .modelName("embedding-3")              // 智谱自研 Embedding 模型
                    .build();
            case "tongyi" -> OpenAiEmbeddingModel.builder()
                    .baseUrl("https://dashscope.aliyuncs.com/compatible-mode/v1")
                    .apiKey(apiKey)
                    .modelName("text-embedding-v3")        // 通义千问 Embedding 模型
                    .build();
            case "siliconflow" -> OpenAiEmbeddingModel.builder()
                    .baseUrl("https://api.siliconflow.cn/v1")
                    .apiKey(apiKey)
                    .modelName("BAAI/bge-m3")              // 北京智源 BGE-M3 开源模型
                    .build();
            default -> throw new IllegalArgumentException("不支持的 Embedding 服务商: " + provider);
        };
    }
}
```

```java
package com.ragdemo.embedding;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.UUID;

/**
 * Embedding 向量化服务 —— 将文本片段转为向量并存入向量库
 *
 * 职责：
 * 1. 将文档切片批量转换为向量
 * 2. 将向量及其对应的文本存入向量数据库
 * 3. 提供检索接口，根据查询向量召回相似文档
 *
 * 本例使用内存向量库 InMemoryEmbeddingStore 简化演示
 * 生产环境可替换为 Milvus / Weaviate / Qdrant
 */
@Service
public class EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);

    @Resource
    private EmbeddingModelFactory embeddingFactory;

    @Value("${langchain4j.embedding-model.provider:openai}")
    private String provider;

    /** 内存向量库 —— 存储所有文档的向量和文本 */
    private final EmbeddingStore<TextSegment> embeddingStore = new InMemoryEmbeddingStore<>();

    /**
     * 将文档切片批量向量化并存入向量库
     *
     * 流程：
     * 1. 根据配置创建 Embedding 模型实例
     * 2. 批量调用 Embedding API 将所有切片转为向量
     * 3. 为每个切片分配唯一 ID，存入向量库
     *
     * @param segments 文档切片列表（来自 DocumentSplitter）
     */
    public void embedAndStore(List<TextSegment> segments) {
        // 1. 创建 Embedding 模型实例
        EmbeddingModel embeddingModel = embeddingFactory.createEmbeddingModel(provider);

        // 2. 批量 Embedding：一次 API 调用处理所有切片
        //    embedAll 比逐个 embed 更高效，减少了 API 往返次数
        List<Embedding> embeddings = embeddingModel.embedAll(segments).content();

        // 3. 为每个切片和向量分配唯一 ID，存入向量库
        for (int i = 0; i < segments.size(); i++) {
            String segmentId = UUID.randomUUID().toString(); // 生成唯一 ID
            embeddingStore.add(segmentId, embeddings.get(i), segments.get(i));
        }

        log.info("成功向量化 {} 个文档切片并存入向量库", segments.size());
    }

    /**
     * 根据查询文本检索最相似的文档片段
     *
     * 流程：
     * 1. 将查询文本转为向量
     * 2. 在向量库中执行近似最近邻搜索（ANN）
     * 3. 返回 Top-K 个最相似的结果
     *
     * @param query 查询文本
     * @param topK  返回的最相似结果数量
     * @return 检索结果列表，按相似度降序排列
     */
    public List<EmbeddingMatch<TextSegment>> search(String query, int topK) {
        // 1. 将查询文本向量化
        EmbeddingModel embeddingModel = embeddingFactory.createEmbeddingModel(provider);
        Embedding queryEmbedding = embeddingModel.embed(query).content();

        // 2. 在向量库中搜索最相似的 Top-K 个结果
        //    使用余弦相似度计算向量距离
        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)  // 查询向量
                .maxResults(topK)                // 最大返回数量
                .minScore(0.0)                   // 最低相似度阈值
                .build();
        EmbeddingSearchResult<TextSegment> result = embeddingStore.search(request);
        return result.matches();
    }
}
```

#### 3.4.4 RAG 检索服务（含 Rerank）

```java
package com.ragdemo.retrieval;

import com.ragdemo.embedding.EmbeddingService;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * RAG 检索服务 —— 完整的检索增强生成流程
 *
 * 流程：
 * 1. Query Embedding：将用户问题向量化
 * 2. 向量检索：从向量库召回 Top-K 候选文档
 * 3. Rerank 重排序：对候选文档做精确相关性打分
 * 4. 上下文构建：将精排后的文档拼接为 LLM 上下文
 *
 * 这是 RAG 管线中决定回答质量的核心组件
 */
@Service
public class RagRetrievalService {

    private static final Logger log = LoggerFactory.getLogger(RagRetrievalService.class);

    @Resource
    private EmbeddingService embeddingService;

    @Value("${rag.retrieval.top-k:50}")
    private int topK;

    @Value("${rag.retrieval.top-n:5}")
    private int topN;

    /**
     * 执行 RAG 检索主流程
     *
     * 步骤详解：
     * 1. 向量检索召回 Top-50：保证高召回率，不遗漏相关文档
     * 2. Rerank 精排取 Top-5：用更精确的模型做二次打分
     * 3. 构建上下文：将精排后的文档注入 LLM
     *
     * @param query 用户查询
     * @return 检索结果上下文，可直接注入 LLM 的 prompt
     */
    public String retrieve(String query) {
        // 1. 向量检索：从向量库中召回 Top-K 个候选文档
        //    使用余弦相似度计算语义相似度
        List<EmbeddingMatch<TextSegment>> searchResults = embeddingService.search(query, topK);
        log.info("向量检索召回 {} 个候选文档", searchResults.size());

        // 2. 提取候选文档的文本内容，准备 Rerank
        List<ScoredDocument> candidates = searchResults.stream()
                .map(result -> new ScoredDocument(
                        result.embedded().text(),        // 文档文本
                        result.score()))                 // 向量相似度分数
                .collect(Collectors.toList());

        // 3. Rerank 重排序：对候选文档做精确相关性打分
        //    这里使用简单实现（按原分数排序），生产环境接入 Rerank API
        List<ScoredDocument> rerankedResults = rerank(query, candidates, topN);

        // 4. 构建 LLM 上下文：将精排后的文档拼接为 prompt 上下文
        String context = buildContext(rerankedResults);
        log.info("RAG 检索完成，注入 {} 个参考文档", rerankedResults.size());

        return context;
    }

    /**
     * Rerank 重排序 —— 对候选文档做精确相关性打分
     *
     * 生产环境中，此处应调用 Rerank API（如 SiliconFlow BGE-Reranker）：
     * - 使用 Cross-Encoder 架构，对 (query, doc) 逐对打分
     * - 比向量检索的余弦相似度更精确
     * - 计算量更大，仅对少量候选执行
     *
     * 本例简化实现：按向量相似度降序取 Top-N
     *
     * @param query      原始查询
     * @param candidates 候选文档列表
     * @param topN       返回的精排结果数量
     * @return 精排后的文档列表
     */
    private List<ScoredDocument> rerank(String query, List<ScoredDocument> candidates, int topN) {
        // 按分数降序排列，取 Top-N
        return candidates.stream()
                .sorted(Comparator.comparingDouble(ScoredDocument::getScore).reversed())
                .limit(topN)
                .collect(Collectors.toList());
    }

    /**
     * 构建 LLM 上下文 —— 将精排文档拼接为结构化提示
     *
     * 格式：
     * 请基于以下参考资料回答用户问题：
     *
     * 【参考1】（相关性：0.95）
     * 文档内容...
     *
     * 【参考2】（相关性：0.88）
     * 文档内容...
     *
     * @param documents 精排后的文档列表
     * @return 结构化的上下文文本
     */
    private String buildContext(List<ScoredDocument> documents) {
        StringBuilder context = new StringBuilder();
        context.append("请基于以下参考资料回答用户问题：\n\n");
        for (int i = 0; i < documents.size(); i++) {
            ScoredDocument doc = documents.get(i);
            context.append("【参考").append(i + 1).append("】")
                    .append("（相关性：").append(String.format("%.2f", doc.getScore())).append("）\n")
                    .append(doc.getText()).append("\n\n");
        }
        return context.toString();
    }

    /**
     * 带分数的文档 —— 内部数据结构
     * 记录文档文本及其相关性分数
     */
    static class ScoredDocument {
        private final String text;
        private final double score;

        public ScoredDocument(String text, double score) {
            this.text = text;
            this.score = score;
        }

        public String getText() { return text; }
        public double getScore() { return score; }
    }
}
```

#### 3.4.5 RAG 控制器

```java
package com.ragdemo.controller;

import com.ragdemo.embedding.EmbeddingService;
import com.ragdemo.parser.DocumentParserFactory;
import com.ragdemo.retrieval.RagRetrievalService;
import com.ragdemo.splitter.DocumentSplitter;
import dev.langchain4j.data.segment.TextSegment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.util.List;

/**
 * RAG 控制器 —— 提供文档上传和智能问答的 REST API
 *
 * 端点说明：
 * - POST /api/rag/upload：上传文档并建立索引
 * - POST /api/rag/ask：基于已索引的文档回答问题
 */
@RestController
@RequestMapping("/api/rag")
public class RagController {

    private static final Logger log = LoggerFactory.getLogger(RagController.class);

    @Resource
    private DocumentParserFactory parserFactory;

    @Resource
    private DocumentSplitter documentSplitter;

    @Resource
    private EmbeddingService embeddingService;

    @Resource
    private RagRetrievalService ragRetrievalService;

    @Value("${rag.chunking.max-tokens:512}")
    private int maxTokens;

    @Value("${rag.chunking.overlap-tokens:64}")
    private int overlapTokens;

    /**
     * 上传文档并建立索引
     *
     * 流程：
     * 1. 接收上传的文件
     * 2. 根据文件后缀选择对应的解析器
     * 3. 解析文档，提取文本内容
     * 4. 将文本切分为语义完整的片段
     * 5. 将片段向量化并存入向量库
     *
     * @param file 上传的文件（支持 PDF、docx、md）
     * @return 处理结果，包含切分片段数
     */
    @PostMapping("/upload")
    public String uploadDocument(@RequestParam("file") MultipartFile file) {
        String fileName = file.getOriginalFilename();
        log.info("收到文档上传请求：{}", fileName);

        try {
            // 1. 解析文档：根据文件后缀选择解析器
            //    支持 PDF、Word、Markdown 三种格式
            List<com.ragdemo.parser.DocumentSegment> segments =
                    parserFactory.parse(fileName, file.getInputStream());
            log.info("文档解析完成，共 {} 个原始片段", segments.size());

            // 2. 将解析后的文本合并为完整字符串
            StringBuilder fullText = new StringBuilder();
            for (var segment : segments) {
                fullText.append(segment.getText()).append("\n");
            }

            // 3. 智能切分：将长文本切分为语义完整的片段
            //    默认使用 Token 切分策略
            List<TextSegment> chunks = documentSplitter.splitByToken(
                    fullText.toString(), maxTokens, overlapTokens);
            log.info("文档切分完成，共 {} 个片段", chunks.size());

            // 4. 向量化并存储：将每个片段转为向量并存入向量库
            embeddingService.embedAndStore(chunks);

            return String.format("文档处理成功：解析 %d 个片段，切分为 %d 个块，已全部向量化",
                    segments.size(), chunks.size());

        } catch (Exception e) {
            log.error("文档处理失败", e);
            throw new RuntimeException("文档处理失败: " + e.getMessage());
        }
    }

    /**
     * 基于已索引的文档回答问题
     *
     * 流程：
     * 1. 接收用户问题
     * 2. 从向量库检索相关文档片段
     * 3. Rerank 精排后构建上下文
     * 4. 返回检索到的参考内容（实际生产环境中接入 LLM 生成回答）
     *
     * @param query 用户问题
     * @return 检索到的参考上下文
     */
    @PostMapping("/ask")
    public String askQuestion(@RequestParam("query") String query) {
        log.info("收到查询请求：{}", query);

        // 执行 RAG 检索：向量检索 + Rerank 双阶段
        String context = ragRetrievalService.retrieve(query);

        // 返回检索到的上下文（生产环境会注入 LLM 生成最终回答）
        return context;
    }
}
```

---

## 四、运行验证

### 4.1 配置 API Key

```bash
# Windows PowerShell
$env:OPENAI_API_KEY="your-api-key-here"

# 或者使用 SiliconFlow（免费额度）
$env:OPENAI_API_KEY="your-siliconflow-api-key"
```

修改 `application.yml` 中的 Embedding 服务商配置：

```yaml
langchain4j:
  embedding-model:
    provider: siliconflow    # 改为 siliconflow 使用免费额度
    model: BAAI/bge-m3
    api-key: ${OPENAI_API_KEY}
```

### 4.2 启动应用

```bash
# 使用 Maven 编译并启动
mvn spring-boot:run

# 看到以下日志表示启动成功：
# 2026-08-22T10:00:00.000+08:00  INFO 12345 --- [rag-demo] [main] c.r.RagDemoApplication: Started RagDemoApplication in 3.2 seconds
```

### 4.3 上传文档并测试

```bash
# 上传一个 Markdown 文档
curl -X POST http://localhost:8080/api/rag/upload \
  -F "file=@/path/to/your/document.md"

# 期望输出：
# 文档处理成功：解析 1 个原始片段，切分为 8 个块，已全部向量化

# 基于文档提问
curl -X POST http://localhost:8080/api/rag/ask \
  -d "query=请说明文档中关于数据库设计的内容"

# 期望输出：
# 请基于以下参考资料回答用户问题：
#
# 【参考1】（相关性：0.92）
# ## 数据库设计
# 本系统采用 MySQL 作为主数据库...
#
# 【参考2】（相关性：0.85）
# ### 表结构设计
# user 表：id, name, email, created_at...
```

### 4.4 测试不同格式的文档

```bash
# 上传 PDF 文档
curl -X POST http://localhost:8080/api/rag/upload \
  -F "file=@/path/to/report.pdf"

# 上传 Word 文档
curl -X POST http://localhost:8080/api/rag/upload \
  -F "file=@/path/to/report.docx"
```

---

## 五、项目对照：对应 ruoyi-ai 项目中的真实代码位置

### 5.1 核心文件对照表

| 本示例中的类 | ruoyi-ai 中的对应类 | 所在模块 | 核心差异 |
|-------------|-------------------|---------|---------|
| `DocumentParserFactory` | `DocumentParserFactory` | `ruoyi-chat/rag/parser/` | ruoyi-ai 额外支持 Excel 解析 |
| `PdfDocumentParser` | `PdfDocumentParser` | `ruoyi-chat/rag/parser/` | 实现方式相同，均使用 PDFBox |
| `DocumentSplitter` | `DocumentSplitter` | `ruoyi-chat/rag/splitter/` | ruoyi-ai 的 Markdown 切分更精细 |
| `EmbeddingModelFactory` | `EmbeddingModelFactory` | `ruoyi-chat/rag/embedding/` | ruoyi-ai 使用 LangChain4j 原生模型 |
| `EmbeddingService` | `EmbeddingService` | `ruoyi-chat/rag/embedding/` | ruoyi-ai 支持多向量库切换 |
| `RagRetrievalService` | `RagRetrievalService` | `ruoyi-chat/rag/retrieval/` | ruoyi-ai 增加了 GraphRAG 融合 |
| `RerankService` | `RerankService` 接口 | `ruoyi-chat/rag/rerank/` | ruoyi-ai 有三家 Rerank 实现 |

### 5.2 ruoyi-ai 中的进阶实现

ruoyi-ai 的 RAG 管线在本文示例的基础上增加了以下增强特性：

**1. Excel 文档解析**

```java
// ruoyi-ai 中增加了 Excel 解析器，支持 .xlsx 和 .xls 格式
@Component
public class ExcelDocumentParser implements DocumentParser {
    @Override
    public List<DocumentSegment> parse(InputStream inputStream) {
        // 使用 Apache POI 读取 Excel（XSSFWorkbook / HSSFWorkbook）
        // 将表头作为列名，每行数据转为 "列名: 值" 的文本
        // 逐行解析，每行作为一个独立片段
    }
}
```

**2. Neo4j GraphRAG 知识图谱增强**

```java
// ruoyi-ai 中增加了知识图谱增强检索
// 在向量检索的同时，从 Neo4j 图数据库中查询实体关系
@Service
public class RagRetrievalService {
    public RagContext retrieve(String query, String embedProvider, String rerankProvider) {
        // 1. 向量检索：召回 Top-50
        List<ScoredDocument> vectorResults = vectorSearch(query);

        // 2. 知识图谱检索：实体关系查询，捕获向量检索容易遗漏的关联信息
        List<GraphDocument> graphResults = graphRagService.searchByQuery(query);

        // 3. 多路融合：向量结果 + 图谱结果合并
        List<ScoredDocument> fusedResults = fuseResults(vectorResults, graphResults);

        // 4. Rerank 精排取 Top-5
        List<ScoredDocument> rerankedResults = reranker.rerank(query, fusedResults, 5);

        return buildContext(rerankedResults);
    }
}
```

**3. 多家 Rerank 服务商切换**

```java
// ruoyi-ai 支持多家 Rerank 服务商，通过配置切换
public interface RerankService {
    List<ScoredDocument> rerank(String query, List<ScoredDocument> candidates, int topN);
}

// 实现类：AlibaiLianRerank、SiliconFlowRerank、ZhipuAIRerank 等
// 通过 RerankService 接口统一抽象，业务代码无感知切换
```

### 5.3 从示例到项目的进阶之路

| 维度 | 本文示例 | ruoyi-ai 项目 |
|------|---------|--------------|
| **向量库** | 内存存储（InMemoryEmbeddingStore） | Milvus / Weaviate / Qdrant 生产级向量库 |
| **检索增强** | 纯向量检索 | 向量 + Neo4j 知识图谱双路融合 |
| **Rerank** | 简单排序（模拟） | 多家 Rerank API 真实调用 |
| **文档格式** | PDF / Word / Markdown | 额外支持 Excel |
| **切分策略** | 三种基础策略 | 增加语义切分、混合策略 |
| **并发处理** | 单线程 | 异步处理 + 批量 Embedding 优化 |
| **监控** | 无 | RAGAS 评估框架、检索质量监控 |

---

## 六、面试实战：3道面试题 + 回答框架

### Q1：RAG 管线中，Chunking 策略如何选择？有哪些优化技巧？

**考察点：** 面试官想考察候选人对文档切分的理解深度，以及面对不同场景时的策略选择能力。

**回答框架：**

- **背景**：Chunking 策略的选择直接影响检索质量。切分太粗导致片段包含多主题，检索时命中噪音；切分太细导致语义断裂，丢失关键信息。没有"万能"的切分策略，需要根据文档类型和检索需求选择。

- **三种策略对比**：
  - **Token 切分**：按 Token 数精确切分，适合通用文本，需要精确控制 LLM 上下文窗口的场景。使用 LangChain4j 的 `DocumentByParagraphSplitter`，自动按段落边界切分，避免切断语义完整的段落。
  - **Character 切分**：按固定字符数切分，在句号处智能截断。适合英文等单字节文本，实现简单高效。核心技巧是：截断时优先在句号、换行、段落边界处截断，而不是硬切。
  - **Markdown 切分**：按标题层级（# / ## / ###）分割，保持章节语义完整性。适合 Markdown 格式的技术文档，每个标题及其内容作为一个独立片段。

- **优化技巧**：
  1. **重叠窗口（Overlap）**：相邻切片之间保留 10-20% 的重叠内容，防止切断的关键信息丢失。例如 Token 切分时设置 `overlapTokens = maxTokens * 0.2`
  2. **智能截断**：优先在句号、换行、段落边界处截断，不是硬切固定长度
  3. **文档类型感知**：Markdown 文档用 Markdown 切分，技术文档可以按章节切分，各取所长
  4. **动态调整**：根据检索结果的召回率动态调整切片大小。召回率低时缩小切片，精度低时增大切片

- **深度（项目经验）**：在 ruoyi-ai 中，我们根据文档类型自动选择切分策略：Markdown 文档用 Markdown 切分，普通文档用 Token 切分。同时支持配置化的切分参数（maxTokens、overlapTokens），方便不同场景调优。切分效果通过 RAGAS 框架的 Context Recall 指标量化评估。

- **扩展**：如果切片后检索结果不理想，先检查切片是否打断了语义完整性，然后尝试调整切片大小和重叠比例，最后考虑引入语义切分（检测语义边界后切分）。

### Q2：Rerank 的作用是什么？为什么向量检索后还需要 Rerank？

**考察点：** 面试官想考察候选人对 RAG 管线中"检索 + Rerank 双阶段"设计的理解，以及对 Bi-Encoder 和 Cross-Encoder 架构差异的认知。

**回答框架：**

- **背景**：Rerank 是 RAG 管线中决定回答质量的关键设计。很多人误以为向量检索的结果可以直接用，但实际项目中向量检索只是"粗筛"，Rerank 才是"精排"。

- **Rerank 的核心作用**：在向量检索召回 Top-K 候选文档后，用更精确的模型对文档与 Query 的相关性进行二次打分，重新排序后取 Top-N。

- **为什么需要 Rerank**：
  - **模型架构差异**：向量检索使用 Bi-Encoder（双塔架构），文档和 Query 各自独立编码，然后计算余弦相似度。这种"各自编码后比距离"的方式是近似计算，牺牲了精度换取速度。Rerank 使用 Cross-Encoder（交互式架构），Query 和每个候选文档拼接后输入模型，让 Token 之间可以直接交互注意力，打分更精确。
  - **HNSW 索引的精度损失**：向量检索使用的 HNSW 索引是近似最近邻搜索，为了速度牺牲了精度。Rerank 用精确模型对少量候选做二次过滤，两者互补。
  - **召回率和精确率的平衡**：第一阶段召回 Top-50（保证高召回率，不遗漏相关文档），第二阶段 Rerank 精排取 Top-5（保证高精确率，只保留最相关的）。

- **协同工作方式**：

  向量检索（第一阶段）→ 召回 Top-50（追求高召回）

  Rerank（第二阶段）→ 精排 Top-5（追求高精度）

  注入 LLM 上下文 → 生成回答

- **对比表格**：

  | 对比维度 | 向量检索（Bi-Encoder） | Rerank（Cross-Encoder） |
  |---------|----------------------|-----------------------|
  | 模型架构 | 双塔，各自独立编码 | 交互式，拼接后编码 |
  | 计算精度 | 中等（近似匹配） | 高（精确匹配） |
  | 处理速度 | 快（毫秒级，HNSW 索引） | 慢（候选越多越慢） |
  | 处理量级 | 百万级 | Top-50 到 Top-100 |

- **深度（项目经验）**：在 ruoyi-ai 中，我们设置向量检索召回 Top-50，Rerank 精排取 Top-5。这个比例经过实践验证，在效率和精度之间取得平衡。同时尽量选择同一系列的 Embedding 和 Rerank 模型（如 BGE-M3 做 Embedding + BGE-Reranker 做 Rerank），保证判断标准一致性。

### Q3：Embedding 模型的选择依据是什么？有哪些注意事项？

**考察点：** 面试官想考察候选人对 Embedding 模型的选型能力，以及在多服务商环境下的架构设计。

**回答框架：**

- **背景**：Embedding 模型是 RAG 管线的核心组件，直接影响检索质量。选择 Embedding 模型需要综合考虑维度、语言、成本、性能等多个维度。

- **选择依据**：
  1. **向量维度**：维度越高信息越丰富，但计算和存储成本也越高。OpenAI text-embedding-3-small 支持 256/512/1536 三档维度，可以按需选择。维度减半，存储成本减半，检索速度翻倍，但精度可能下降 1-3%。
  2. **中文能力**：中文场景优先选择中文优化模型（如 BGE-M3、通义 text-embedding-v3、智谱 embedding-3）。通用模型（如 OpenAI 的 ada-002）在中文场景的效果可能不如专门的中文模型。
  3. **最大输入长度**：越长单次能处理的文本越多，切分可以更粗。BGE-M3 支持 8192 tokens，适合长文档场景。
  4. **成本与性能**：API 调用按 Token 计费，自部署需要 GPU 资源。日常使用性价比高的模型（如 SiliconFlow 的 BGE-M3），关键场景切换高精度模型。

- **注意事项**：
  1. **多服务商容灾**：不要只依赖一个 Embedding 服务商。集成多个服务商，通过配置切换，某家服务商不可用时自动切换，提高系统可用性。
  2. **模型一致性**：Embedding 和 Rerank 尽量选择同一系列或同一厂商的模型，保证判断标准一致性。如果 Embedding 用 BGE-M3，Rerank 最好也用 BGE-Reranker。
  3. **维度统一**：不同 Embedding 模型产生的向量维度不同（如 OpenAI 1536 维，BGE-M3 1024 维）。如果同时使用多个模型，需要处理维度不统一的问题。方案一：统一使用同一维度；方案二：不同模型存入不同索引字段；方案三：分别查询后融合结果。
  4. **定期评估**：使用 RAGAS 等评估框架，定期评估不同 Embedding 模型在业务数据集上的召回率，用数据驱动选型决策。

- **深度（项目经验）**：在 ruoyi-ai 中，我们采用工厂模式封装了四家 Embedding 服务商，通过配置的 `provider` 参数切换。这样做的优势是：业务代码只依赖 `EmbeddingModel` 接口，新增服务商只需添加一个实现类，不修改检索主流程。日常使用 SiliconFlow 的 BGE-M3（性价比高），关键场景切换 OpenAI 的 text-embedding-3-small（精度更高）。

---

## 七、总结

本文从零搭建了一个完整的 RAG 管线应用，涵盖了"文档上传 -> 解析 -> 切分 -> 向量化 -> 检索 -> 重排序 -> 上下文注入"的完整流程。通过这个最小可行示例，我们学习了以下核心知识点：

1. **多格式文档解析**：通过工厂模式 + 策略模式封装 PDF、Word、Markdown 三种格式的解析器，新增格式只需添加一个实现类
2. **三种切分策略**：Token 切分适合精确控制窗口，Character 切分简单高效，Markdown 切分保持章节完整性，各有适用场景
3. **Embedding 模型多服务商封装**：通过工厂模式统一四家服务商调用，业务代码零感知切换
4. **检索 + Rerank 双阶段**：向量检索召回 Top-50（高召回），Rerank 精排取 Top-5（高精度），两者互补
5. **项目对照**：理解了 ruoyi-ai 项目中 RAG 管线的真实实现，以及从示例到生产环境的进阶路径

在下一篇文章中，我们将深入分析 ruoyi-ai 的向量数据库存储策略，学习 Milvus、Weaviate、Qdrant 三种向量库的选型与配置。

---

## 参考资料

- [LangChain4j RAG 文档](https://docs.langchain4j.dev/tutorials/rag) — LangChain4j 官方 RAG 教程
- [LangChain4j Easy RAG](https://docs.langchain4j.dev/tutorials/easy-rag) — 简化 RAG 实现
- [Apache PDFBox 官方文档](https://pdfbox.apache.org/docs/) — PDF 解析库的使用文档
- [Apache POI 官方文档](https://poi.apache.org/components/) — Word/Excel 解析库的使用文档
- [BGE-M3 模型介绍](https://huggingface.co/BAAI/bge-m3) — 北京智源 BGE-M3 多语言 Embedding 模型
- [RAGAS 评估框架](https://docs.ragas.io/) — RAG 系统质量评估
- [ruoyi-ai GitHub 仓库](https://github.com/1byteone/ruoyi-ai) — 项目源码，查看完整的 RAG 管线实现