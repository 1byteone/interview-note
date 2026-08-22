# 文档解析深潜：从PDF到结构化文本的完整路径

> **深度系列 | 第2篇** | Level 2 进阶
>
> 本篇目标：掌握RAG管线第一道工序——多格式文档解析。深入理解PDF、Word、Markdown、Excel四种格式的解析原理，以及如何用策略模式统一处理。

---

## 一、为什么文档解析如此重要？

很多人搭建RAG系统时，把精力都放在Embedding模型选择和向量库调优上，却忽略了一个更基础的问题：**输入给Embedding的文本质量**。

一个残酷的事实：如果你的文档解析器提取出来的文本是乱码、缺失表格数据、或者丢失了标题层级，那么后续的切分、向量检索、LLM回答质量都会大打折扣。垃圾进，垃圾出——这是RAG系统质量的第一个瓶颈。

**文档解析要解决的核心问题：**

| 问题 | 影响 |
|------|------|
| PDF中的表格数据提取 | 表格变成乱码，检索不到结构化信息 |
| Word中的图片和公式 | 关键信息丢失，LLM无法理解 |
| Markdown的标题层级 | 切分时无法保持章节结构 |
| Excel的数据和表头 | 行数据脱离上下文，检索无意义 |

**文档解析的质量直接决定了RAG系统的上限。** 后续的切分、Embedding、检索都建立在解析输出的文本之上。

---

## 二、多格式解析架构：策略模式

ruoyi-ai项目的文档解析采用经典的**策略模式 + 工厂模式**，把格式判断和具体解析逻辑解耦。这是处理多格式输入的标准架构。

### 2.1 架构设计

```
用户上传文件
      ↓
DocumentParserFactory（工厂：根据后缀分发）
      ↓
┌──────────┬──────────┬──────────┬──────────┐
│PDF解析器  │Word解析器 │MD解析器   │Excel解析器│
│PDFBox    │POI+Tika  │flexmark  │POI       │
└──────────┴──────────┴──────────┴──────────┘
      ↓
统一输出：List<DocumentSegment>
```

### 2.2 接口定义

```java
/**
 * 文档解析器接口 —— 策略模式的抽象层
 * 所有格式的解析器都实现此接口，保证输出格式统一
 */
public interface DocumentParser {

    /**
     * 解析文档，返回统一格式的文档片段列表
     * 每个DocumentSegment包含：文本内容 + 页码/行号（用于溯源）
     *
     * @param inputStream 文档输入流（字节流，格式无关）
     * @return 解析后的文档片段列表
     */
    List<DocumentSegment> parse(InputStream inputStream);
}

/**
 * 文档片段 —— 解析输出的统一数据结构
 * 封装文本内容和来源位置信息，便于后续切分和溯源
 */
public record DocumentSegment(
    String text,           // 提取的纯文本内容
    int pageNumber,        // 来源页码或行号（用于回答溯源）
    String sectionTitle    // 所属章节标题（可选，用于保持上下文）
) {}
```

---

## 三、PDF解析：Apache PDFBox的正确用法

PDF是企业文档中最常见的格式，也是最容易出问题的格式。本节深入讲解PDF解析的正确姿势。

### 3.1 核心API：PDFTextStripper，而非PDFRenderer

这是一个高频踩坑点。Apache PDFBox提供了两个文本提取API：

| API | 用途 | 文本提取效果 |
|-----|------|-------------|
| `PDFTextStripper` | **文本提取**（官方推荐） | 正确提取段落、换行、表格 |
| `PDFRenderer` | **图像渲染**（用于截图） | 将页面渲染为图像，不直接提取文本 |

**常见错误：用PDFRenderer提取文本**

```java
// ❌ 错误做法：PDFRenderer 是用于渲染图像的，不是提取文本
// 虽然getText()方法存在，但输出的文本格式经常混乱
PDFRenderer renderer = new PDFRenderer(document);
String text = renderer.getText(i); // 不推荐！可能丢失格式
```

**正确做法：使用PDFTextStripper**

```java
// ✅ 正确做法：PDFTextStripper 是专门用于文本提取的API
// 支持配置编码、页面范围、排序模式等
import org.apache.pdfbox.text.PDFTextStripper;

try (PDDocument document = Loader.loadPDF(pdfBytes)) {
    PDFTextStripper stripper = new PDFTextStripper();

    // 设置提取的页面范围（从第1页到第1页）
    stripper.setStartPage(1);
    stripper.setEndPage(1);

    // 提取文本，PDFTextStripper会自动处理：
    // - 段落分隔
    // - 列布局文本的阅读顺序
    // - 连字符连接的单词
    String pageText = stripper.getText(document);
}
```

### 3.2 完整的PDF解析实现

```java
/**
 * PDF文档解析器
 * 使用Apache PDFBox的PDFTextStripper提取文本
 * 支持：文本提取、页码记录、表格转文本（基础）
 */
@Component
public class PdfDocumentParser implements DocumentParser {

    private static final Logger log = LoggerFactory.getLogger(PdfDocumentParser.class);

    @Override
    public List<DocumentSegment> parse(InputStream inputStream) {
        List<DocumentSegment> segments = new ArrayList<>();

        try {
            // 1. 将输入流读取为字节数组
            //    PDFBox的Loader.loadPDF需要byte[]参数
            byte[] pdfBytes = inputStream.readAllBytes();

            // 2. 加载PDF文档
            //    PDDocument是PDFBox的核心类，代表一个PDF文档对象
            //    使用try-with-resources确保文档被正确关闭（释放内存）
            try (PDDocument document = Loader.loadPDF(pdfBytes)) {

                int totalPages = document.getNumberOfPages();
                log.info("PDF文档共 {} 页", totalPages);

                // 3. 创建PDFTextStripper文本提取器
                PDFTextStripper stripper = new PDFTextStripper();

                // 4. 逐页提取文本
                for (int i = 0; i < totalPages; i++) {
                    // PDFTextStripper的页面编号从1开始（非0）
                    stripper.setStartPage(i + 1);
                    stripper.setEndPage(i + 1);

                    // 提取当前页的纯文本
                    // PDFTextStripper会自动处理：
                    //   - 多列布局的阅读顺序（从左到右，从上到下）
                    //   - 连字符连接的断行单词
                    //   - 制表符和空格的保留
                    String pageText = stripper.getText(document);

                    // 清理空白字符
                    pageText = pageText.trim();

                    // 跳过空白页（如目录页、封面页后的空白页）
                    if (pageText.isEmpty()) {
                        continue;
                    }

                    // 5. 创建文档片段，记录页码用于溯源
                    //    后续检索到这段文字时，可以告诉用户"来自第X页"
                    segments.add(new DocumentSegment(
                        pageText,     // 文本内容
                        i + 1,        // 页码（从1开始）
                        null          // 章节标题（PDF难以自动提取，后续可增强）
                    ));
                }

                log.info("PDF解析完成，提取 {} 个有效页面", segments.size());
            }

        } catch (IOException e) {
            log.error("PDF解析失败", e);
            throw new DocumentParseException("PDF解析失败: " + e.getMessage(), e);
        }

        return segments;
    }
}
```

### 3.3 PDF表格提取：进阶处理

PDF中的表格是文本提取的难点。PDFTextStripper对表格的处理有限，复杂表格会变成混乱的文本。要提取结构化表格数据，需要使用PDFBox的`PDFTableStripper`或第三方库（如Tabula）。

```java
/**
 * PDF表格提取（简化版）
 * 使用Apache PDFBox的底层API提取表格区域
 * 注意：PDFBox原生表格提取能力有限，复杂表格建议用Tabula
 */
private String extractTableFromPage(PDDocument document, int pageIndex) throws IOException {
    PDFTextStripper stripper = new PDFTextStripper();
    stripper.setStartPage(pageIndex + 1);
    stripper.setEndPage(pageIndex + 1);

    // PDFTextStripper提取的文本中，表格内容通常是：
    // 单元格1  单元格2  单元格3
    // 值1     值2     值3
    // 我们可以通过制表符/多空格来检测表格行
    String rawText = stripper.getText(document);

    // 简单的表格检测：按制表符或多个空格分割
    StringBuilder tableText = new StringBuilder();
    String[] lines = rawText.split("\\n");
    for (String line : lines) {
        // 检测是否为表格行（包含制表符或多个连续空格）
        if (line.contains("\t") || line.matches(".*\\s{3,}.*")) {
            // 将表格行转为结构化格式："列1: 值1, 列2: 值2, ..."
            String[] cells = line.split("\\t|\\s{3,}");
            StringBuilder row = new StringBuilder();
            for (String cell : cells) {
                String trimmed = cell.trim();
                if (!trimmed.isEmpty()) {
                    row.append(trimmed).append("; ");
                }
            }
            tableText.append(row).append("\n");
        }
    }

    return tableText.toString();
}
```

**生产环境建议：** 复杂PDF表格推荐使用 **Tabula** 或 **camelot**（Python），或者考虑将PDF转为图片后用多模态LLM（如GPT-4o）直接提取表格内容。

---

## 四、Word文档解析：Apache POI + Tika

### 4.1 为什么需要Tika？

Apache POI是Java处理Office文档的标准库，但它直接解析`.docx`格式时，处理复杂排版（如页眉页脚、图片、表格、嵌套样式）比较繁琐。

**Apache Tika** 是一个内容提取框架，内部封装了POI，但提供了更简洁的API和更好的格式兼容性。LangChain4j的`ApacheTikaDocumentParser`就是基于Tika封装的。

### 4.2 使用LangChain4j内置的Tika解析器

```xml
<!-- pom.xml 添加Tika依赖 -->
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-document-parser-apache-tika</artifactId>
    <version>1.0.0-beta2</version>
</dependency>
```

```java
/**
 * Word文档解析器
 * 使用LangChain4j内置的ApacheTikaDocumentParser
 * Tika内部使用POI解析.docx，同时处理页眉、页脚、图片描述等
 */
@Component
public class WordDocumentParser implements DocumentParser {

    /**
     * TikaDocumentParser的优势：
     * 1. 自动检测编码（GBK/UTF-8等）
     * 2. 正确提取表格内容
     * 3. 提取图片替代文本（alt text）
     * 4. 保留标题层级结构
     */
    private final TikaDocumentParser tikaParser = new TikaDocumentParser();

    @Override
    public List<DocumentSegment> parse(InputStream inputStream) {
        List<DocumentSegment> segments = new ArrayList<>();

        try {
            // TikaDocumentParser.parse() 直接返回Document对象
            Document document = tikaParser.parse(inputStream);

            // Tika解析的文本通常包含标题标记
            // 需要按段落切分，每段作为一个DocumentSegment
            String fullText = document.text();
            String[] paragraphs = fullText.split("\\n\\n"); // 双换行分段

            int lineNumber = 1;
            for (String paragraph : paragraphs) {
                String trimmed = paragraph.trim();
                if (!trimmed.isEmpty()) {
                    segments.add(new DocumentSegment(trimmed, lineNumber, null));
                    lineNumber++;
                }
            }

        } catch (Exception e) {
            throw new DocumentParseException("Word文档解析失败: " + e.getMessage(), e);
        }

        return segments;
    }
}
```

### 4.3 直接用POI提取Word结构化内容

如果需要更精细的控制（比如区分正文和表格），可以直接用POI：

```java
/**
 * 使用Apache POI直接解析Word文档
 * 适合需要区分不同内容类型（正文/表格/列表）的场景
 */
public class WordDocumentParserAdvanced {

    public List<DocumentSegment> parseWord(InputStream inputStream) throws Exception {
        List<DocumentSegment> segments = new ArrayList<>();

        // XWPFDocument：POI处理.docx格式的核心类
        // 注意：.doc格式（旧版Word）需要用HWPFDocument
        try (XWPFDocument document = new XWPFDocument(inputStream)) {

            int elementIndex = 0;

            // 遍历文档中的所有顶层元素
            // Word文档的结构：段落(Paragraph) 和 表格(Table) 交替出现
            for (IBodyElement element : document.getBodyElements()) {

                if (element.getElementType() == BodyElementType.PARAGRAPH) {
                    // 处理段落
                    XWPFParagraph paragraph = (XWPFParagraph) element;
                    String text = paragraph.getText().trim();

                    if (!text.isEmpty()) {
                        // 获取段落样式（标题层级）
                        String styleName = paragraph.getStyle();
                        segments.add(new DocumentSegment(
                            text,
                            elementIndex + 1,
                            isHeading(styleName) ? text : null
                        ));
                        elementIndex++;
                    }

                } else if (element.getElementType() == BodyElementType.TABLE) {
                    // 处理表格
                    XWPFTable table = (XWPFTable) element;
                    String tableText = extractTable(table);
                    if (!tableText.isEmpty()) {
                        segments.add(new DocumentSegment(
                            "【表格】" + tableText,
                            elementIndex + 1,
                            null
                        ));
                        elementIndex++;
                    }
                }
            }
        }

        return segments;
    }

    /**
     * 提取Word表格内容，转为结构化文本
     */
    private String extractTable(XWPFTable table) {
        StringBuilder sb = new StringBuilder();
        // 遍历表格的每一行
        for (XWPFTableRow row : table.getRows()) {
            List<String> cellValues = new ArrayList<>();
            // 遍历行中的每个单元格
            for (XWPFTableCell cell : row.getTableCells()) {
                cellValues.add(cell.getText().trim());
            }
            // 用分号连接同一行的单元格，行之间换行
            sb.append(String.join("; ", cellValues)).append("\n");
        }
        return sb.toString();
    }

    /**
     * 判断Word样式是否为标题
     */
    private boolean isHeading(String styleName) {
        if (styleName == null) return false;
        return styleName.toLowerCase().contains("heading")
            || styleName.matches("Heading \\d")
            || styleName.matches("标题 \\d");
    }
}
```

---

## 五、Markdown解析：flexmark-java

### 5.1 为什么Markdown需要专门解析？

Markdown虽然"看起来就是纯文本"，但直接作为字符串处理会丢失关键信息：

- `# 标题` 中的 `#` 是Markdown语法，不是标题内容的一部分
- `**粗体**` 中的 `**` 不应该出现在提取的文本中
- 代码块 ``` 中的内容应该保持原样，不应该被当作普通文本处理
- 表格语法 `| col1 | col2 |` 应该被转为结构化文本

### 5.2 使用flexmark-java解析

```xml
<!-- pom.xml 添加flexmark依赖 -->
<dependency>
    <groupId>com.vladsch.flexmark</groupId>
    <artifactId>flexmark-all</artifactId>
    <version>0.64.8</version>
</dependency>
```

```java
/**
 * Markdown文档解析器
 * 使用flexmark-java解析Markdown AST，提取结构化内容
 * 保留标题层级信息，便于按章节切分
 */
@Component
public class MarkdownDocumentParser implements DocumentParser {

    /**
     * flexmark Parser：将Markdown源码解析为AST（抽象语法树）
     * Parser是线程安全的，可以复用同一个实例
     */
    private final Parser parser = Parser.builder().build();

    /**
     * NodeRenderer：将AST节点转为可读文本
     * 去除Markdown语法标记，保留纯文本内容
     */
    private final HtmlRenderer textRenderer = HtmlRenderer.builder().build();

    @Override
    public List<DocumentSegment> parse(InputStream inputStream) {
        List<DocumentSegment> segments = new ArrayList<>();

        try {
            // 读取Markdown源文件
            String markdownSource = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);

            // 将Markdown源码解析为AST
            // Document是AST的根节点，包含所有子节点
            Document document = parser.parse(markdownSource);

            // 遍历AST的直接子节点
            // flexmark的AST结构：Document → Heading / Paragraph / FencedCodeBlock / TableBlock ...
            for (Node child : document.getChildren()) {

                if (child instanceof Heading) {
                    // 处理标题节点
                    Heading heading = (Heading) child;
                    String title = extractTextContent(heading);
                    int level = heading.getLevel(); // 标题级别（1-6）

                    // 用 # 号标记标题层级
                    String prefix = "#".repeat(level);
                    segments.add(new DocumentSegment(
                        prefix + " " + title,
                        segments.size() + 1,
                        title  // 章节标题，用于后续切分时保持上下文
                    ));

                } else if (child instanceof Paragraph) {
                    // 处理段落
                    String text = extractTextContent(child);
                    if (!text.trim().isEmpty()) {
                        segments.add(new DocumentSegment(
                            text,
                            segments.size() + 1,
                            null
                        ));
                    }

                } else if (child instanceof FencedCodeBlock) {
                    // 处理代码块（保留原始代码）
                    FencedCodeBlock codeBlock = (FencedCodeBlock) child;
                    String code = codeBlock.getContentChars().toString();
                    String info = codeBlock.getInfo(); // 代码语言标记

                    segments.add(new DocumentSegment(
                        "【代码块" + (info != null ? " - " + info : "") + "】\n" + code,
                        segments.size() + 1,
                        null
                    ));

                } else if (child instanceof TableBlock) {
                    // 处理表格
                    String tableText = extractTableContent(child);
                    segments.add(new DocumentSegment(
                        tableText,
                        segments.size() + 1,
                        null
                    ));
                }
            }

        } catch (IOException e) {
            throw new DocumentParseException("Markdown解析失败: " + e.getMessage(), e);
        }

        return segments;
    }

    /**
     * 从AST节点中提取纯文本（去除Markdown语法标记）
     */
    private String extractTextContent(Node node) {
        // 使用flexmark的NodeVisitor遍历子节点，提取所有文本内容
        StringBuilder sb = new StringBuilder();
        node.accept(nodeVisitor -> {
            if (nodeVisitor instanceof Text) {
                sb.append(((Text) nodeVisitor).getChars());
            } else if (nodeVisitor instanceof Emphasis) {
                // 强调文本（斜体），直接提取内容，去掉*标记
                sb.append(extractTextContent(nodeVisitor));
            }
            return VisitResult.CONTINUE;
        });
        return sb.toString();
    }

    /**
     * 提取Markdown表格内容
     * 将 | col1 | col2 | 格式转为 "col1: 值1, col2: 值2" 格式
     */
    private String extractTableContent(Node tableNode) {
        StringBuilder sb = new StringBuilder();
        tableNode.accept(nodeVisitor -> {
            if (nodeVisitor instanceof TableRow) {
                List<String> cells = new ArrayList<>();
                // 收集行中的所有单元格文本
                nodeVisitor.accept(innerNode -> {
                    if (innerNode instanceof TableCell) {
                        cells.add(extractTextContent(innerNode));
                    }
                    return VisitResult.CONTINUE;
                });
                if (!cells.isEmpty()) {
                    sb.append(String.join(" | ", cells)).append("\n");
                }
            }
            return VisitResult.CONTINUE;
        });
        return sb.toString();
    }
}
```

---

## 六、Excel解析：结构化数据的文本化

### 6.1 Excel解析的特殊性

Excel数据是结构化的（行列结构），但RAG系统需要的是纯文本。关键挑战是：**在保留结构信息的前提下，将表格数据转为可检索的文本。**

### 6.2 完整的Excel解析实现

```java
/**
 * Excel文档解析器
 * 将Excel表格数据转为结构化文本
 * 每行数据转为 "表头1: 值1; 表头2: 值2" 格式，保留上下文
 */
@Component
public class ExcelDocumentParser implements DocumentParser {

    @Override
    public List<DocumentSegment> parse(InputStream inputStream) {
        List<DocumentSegment> segments = new ArrayList<>();

        try {
            // WorkbookFactory.create 自动识别 .xls 和 .xlsx 格式
            // .xls  使用 HSSF（内存占用较大）
            // .xlsx 使用 XSSF（基于ZIP和XML，内存效率更高）
            try (Workbook workbook = WorkbookFactory.create(inputStream)) {

                // 默认只处理第一个Sheet（企业文档通常一个Sheet一个主题）
                Sheet sheet = workbook.getSheetAt(0);
                if (sheet == null || sheet.getPhysicalNumberOfRows() == 0) {
                    return segments; // 空Sheet直接返回
                }

                // 1. 读取表头行（第一行）
                //    表头是数据的"列名"，后续每行数据都会关联表头
                Row headerRow = sheet.getRow(0);
                if (headerRow == null) {
                    return segments;
                }

                List<String> headers = new ArrayList<>();
                for (int i = 0; i < headerRow.getPhysicalNumberOfCells(); i++) {
                    Cell cell = headerRow.getCell(i);
                    headers.add(getCellValue(cell)); // 获取表头文本
                }

                // 2. 逐行解析数据（从第二行开始，跳过表头）
                for (int rowIdx = 1; rowIdx <= sheet.getLastRowNum(); rowIdx++) {
                    Row row = sheet.getRow(rowIdx);
                    if (row == null) continue; // 跳过空行

                    // 将每行数据转为 "列名: 值" 的结构化文本
                    // 这样检索时既能搜到值，也能通过列名理解值的含义
                    StringBuilder lineText = new StringBuilder();
                    for (int colIdx = 0; colIdx < headers.size(); colIdx++) {
                        Cell cell = row.getCell(colIdx);
                        String value = getCellValue(cell);

                        if (!value.isEmpty()) {
                            // 格式："列名: 值；"
                            // 使用分号分隔不同列，便于后续切分
                            lineText.append(headers.get(colIdx))
                                    .append(": ")
                                    .append(value)
                                    .append("; ");
                        }
                    }

                    // 跳过全空行
                    String text = lineText.toString().trim();
                    if (!text.isEmpty()) {
                        segments.add(new DocumentSegment(
                            text,
                            rowIdx + 1,  // 行号（用于溯源）
                            null
                        ));
                    }
                }

            }

        } catch (Exception e) {
            throw new DocumentParseException("Excel解析失败: " + e.getMessage(), e);
        }

        return segments;
    }

    /**
     * 兼容处理不同类型的Excel单元格值
     * Excel单元格有多种类型，需要分别处理
     */
    private String getCellValue(Cell cell) {
        if (cell == null) return "";

        return switch (cell.getCellType()) {
            // 字符串类型：直接获取
            case STRING -> cell.getStringCellValue().trim();

            // 数字类型：转为字符串
            // 注意：日期也是NUMERIC类型，需要判断是否为日期格式
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    // 日期格式：转为可读的日期字符串
                    yield cell.getDateCellValue().toString();
                } else {
                    // 普通数字：避免科学计数法显示
                    double numValue = cell.getNumericCellValue();
                    // 如果是整数，去掉小数点
                    if (numValue == Math.floor(numValue)) {
                        yield String.valueOf((long) numValue);
                    } else {
                        yield String.valueOf(numValue);
                    }
                }
            }

            // 布尔类型：转为"是/否"
            case BOOLEAN -> cell.getBooleanCellValue() ? "是" : "否";

            // 公式类型：获取公式计算后的值
            case FORMULA -> {
                try {
                    yield String.valueOf(cell.getNumericCellValue());
                } catch (Exception e) {
                    yield cell.getStringCellValue();
                }
            }

            // 其他类型（如空白、错误）返回空字符串
            default -> "";
        };
    }
}
```

**解析效果示例：**

原始Excel数据：
| 产品名称 | 价格 | 库存 |
|---------|------|------|
| RAG企业版 | 2999 | 100 |
| 向量检索SDK | 999 | 500 |

解析后文本：
```
产品名称: RAG企业版; 价格: 2999; 库存: 100;
产品名称: 向量检索SDK; 价格: 999; 库存: 500;
```

这种格式保留了列名信息，检索"RAG企业版的价格"时能精准命中第一行。

---

## 七、工厂模式：统一入口

### 7.1 DocumentParserFactory实现

```java
/**
 * 文档解析器工厂 —— 根据文件后缀自动选择解析策略
 *
 * 设计要点：
 * 1. 通过文件后缀路由，业务代码无需感知具体解析器
 * 2. Spring依赖注入，解析器自动装配
 * 3. switch表达式（Java 14+）让路由逻辑清晰简洁
 * 4. 不支持的格式直接抛异常，快速失败
 */
@Component
public class DocumentParserFactory {

    @Resource
    private PdfDocumentParser pdfParser;

    @Resource
    private WordDocumentParser wordParser;

    @Resource
    private MarkdownDocumentParser markdownParser;

    @Resource
    private ExcelDocumentParser excelParser;

    /**
     * 根据文件名获取对应的文档解析器
     *
     * @param fileName 文件名，如 "合同模板.pdf"
     * @return 对应格式的解析器实例
     * @throws UnsupportedDocumentTypeException 不支持的文件格式
     */
    public DocumentParser getParser(String fileName) {
        // 提取文件后缀（小写）
        String suffix = fileName.substring(fileName.lastIndexOf(".")).toLowerCase();

        // 使用Java 14的switch表达式，根据后缀路由
        return switch (suffix) {
            case ".pdf" -> pdfParser;
            case ".docx", ".doc" -> wordParser;
            case ".md", ".markdown" -> markdownParser;
            case ".xlsx", ".xls" -> excelParser;
            default -> throw new UnsupportedDocumentTypeException(
                "不支持的文件格式: " + suffix
            );
        };
    }

    /**
     * 一站式解析入口：输入文件名和流，输出统一格式的文档片段
     *
     * @param fileName    文件名
     * @param inputStream 文件输入流
     * @return 解析后的文档片段列表
     */
    public List<DocumentSegment> parse(String fileName, InputStream inputStream) {
        DocumentParser parser = getParser(fileName);
        List<DocumentSegment> segments = parser.parse(inputStream);

        log.info("文档解析完成: {}, 格式: {}, 片段数: {}",
            fileName,
            getFormatLabel(fileName),
            segments.size()
        );

        return segments;
    }

    /**
     * 获取格式的中文标签（用于日志）
     */
    private String getFormatLabel(String fileName) {
        String suffix = fileName.substring(fileName.lastIndexOf(".")).toLowerCase();
        return switch (suffix) {
            case ".pdf" -> "PDF";
            case ".docx", ".doc" -> "Word";
            case ".md", ".markdown" -> "Markdown";
            case ".xlsx", ".xls" -> "Excel";
            default -> "未知";
        };
    }
}
```

### 7.2 在Spring中使用

```java
/**
 * 文档上传服务 —— 调用工厂完成解析
 */
@Service
public class DocumentUploadService {

    @Resource
    private DocumentParserFactory parserFactory;

    /**
     * 处理用户上传的文档
     */
    public DocumentUploadResult upload(MultipartFile file) {
        String fileName = file.getOriginalFilename();

        // 一行代码完成解析——业务层无需关心具体格式
        List<DocumentSegment> segments = parserFactory.parse(fileName, file.getInputStream());

        // 后续：切分 → Embedding → 存入向量库
        return new DocumentUploadResult(fileName, segments.size());
    }
}
```

**这种设计的好处：** 当需要新增CSV格式支持时，只需三步：
1. 新增 `CsvDocumentParser implements DocumentParser`
2. 在 `DocumentParserFactory` 的switch中加一行 `case ".csv" -> csvParser`
3. 完成。业务代码零修改，符合开闭原则。

---

## 八、性能优化：处理大文档的正确姿势

### 8.1 流式处理：避免内存溢出

```java
/**
 * 流式文档解析器 —— 处理超大PDF时避免内存溢出
 * 原理：使用PDFBox的Stripper流式提取，逐页处理而非全部加载
 */
public class StreamingPdfParser {

    /**
     * 流式解析：逐页提取，边提取边向量化
     * 避免将整个PDF的文本一次性加载到内存
     */
    public void parseAndEmbedStreaming(InputStream pdfStream,
                                       EmbeddingModel embeddingModel,
                                       EmbeddingStore<TextSegment> store) throws IOException {
        byte[] pdfBytes = pdfStream.readAllBytes();

        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            int totalPages = document.getNumberOfPages();

            // 逐页提取，每提取一页就立即向量化并存入向量库
            // 避免所有页的文本同时存在于内存中
            for (int i = 0; i < totalPages; i++) {
                stripper.setStartPage(i + 1);
                stripper.setEndPage(i + 1);

                String pageText = stripper.getText(document).trim();
                if (pageText.isEmpty()) continue;

                // 每页单独切分和向量化
                Document doc = Document.from(pageText);
                DocumentSplitter splitter = DocumentSplitters.recursive(300, 50);
                List<TextSegment> segments = splitter.split(doc);

                // 批量向量化（单页文本量小，一次API调用即可）
                List<Embedding> embeddings = embeddingModel.embedAll(segments).content();

                // 立即存入向量库
                for (int j = 0; j < segments.size(); j++) {
                    store.add(embeddings.get(j), segments.get(j));
                }
            }
        }
    }
}
```

### 8.2 并行处理：利用多核CPU

```java
/**
 * 并行文档解析 —— 多页/多文件并行处理
 * 适合批量导入场景，显著缩短建库时间
 */
public class ParallelDocumentParser {

    private final ExecutorService executor = Executors.newFixedThreadPool(
        Runtime.getRuntime().availableProcessors() // CPU核心数作为线程数
    );

    /**
     * 并行解析多个文件
     */
    public List<List<DocumentSegment>> parseInParallel(
            List<String> fileNames,
            List<InputStream> inputStreams,
            DocumentParserFactory factory) {

        // 使用CompletableFuture实现并行处理
        List<CompletableFuture<List<DocumentSegment>>> futures = new ArrayList<>();

        for (int i = 0; i < fileNames.size(); i++) {
            final String fileName = fileNames.get(i);
            final InputStream stream = inputStreams.get(i);

            // 提交异步任务到线程池
            CompletableFuture<List<DocumentSegment>> future = CompletableFuture
                .supplyAsync(() -> factory.parse(fileName, stream), executor);

            futures.add(future);
        }

        // 等待所有任务完成，收集结果
        return futures.stream()
            .map(CompletableFuture::join)  // 阻塞等待结果
            .toList();
    }
}
```

### 8.3 性能优化对照表

| 优化手段 | 适用场景 | 效果 |
|---------|---------|------|
| 流式处理 | 单个超大文档（>100页） | 内存占用降低80%+ |
| 并行处理 | 批量导入多文件 | 处理速度提升N倍（N=CPU核心数） |
| 增量更新 | 文档更新频繁 | 只处理变更文档，避免全量重建 |
| 缓存机制 | 重复解析同一文档 | 直接返回缓存结果，跳过解析 |

---

## 九、面试实战

### Q1: PDF解析有哪些常见坑？PDFTextStripper和PDFRenderer有什么区别？

**参考答案：**

**核心区别：**
- `PDFTextStripper`：专门用于**文本提取**，自动处理多列布局、连字符断行、制表符等，是官方推荐的文本提取API
- `PDFRenderer`：用于**图像渲染**（将PDF页面渲染为图片），虽然有`getText()`方法，但输出格式不稳定，不推荐用于文本提取

**常见坑：**
1. 使用PDFRenderer提取文本导致格式混乱
2. PDF扫描件（图片PDF）无法提取文本——需要OCR（Tesseract）或直接用多模态LLM
3. 加密PDF需要先解密才能提取
4. 大PDF文件直接`readAllBytes()`可能OOM——应使用流式处理
5. PDF中的表格和双列布局需要特殊处理，否则提取顺序混乱

### Q2: 如何设计一个可扩展的文档解析架构？

**参考答案：**

采用**策略模式 + 工厂模式**：

1. 定义 `DocumentParser` 接口，统一 `parse(InputStream)` 返回值
2. 每种格式实现一个Parser：`PdfDocumentParser`、`WordDocumentParser`等
3. `DocumentParserFactory` 根据文件后缀路由到对应Parser
4. 使用Spring依赖注入，新增格式只需添加一个实现类，不修改现有代码

**关键设计原则：**
- **开闭原则**：新增格式不修改已有代码
- **单一职责**：每个Parser只负责一种格式
- **依赖倒置**：业务层依赖`DocumentParser`接口，不依赖具体实现

### Q3: 如何评估文档解析的质量？

**参考答案：**

可以从三个维度评估：

1. **完整性**：提取的文本是否覆盖了原文的所有有效内容？通过对比原文档页数和提取段落数评估
2. **准确性**：提取的文本是否忠实于原文？抽查比对，检查是否出现乱码、缺失、错误
3. **结构保留**：表格、标题、代码块等结构化元素是否被正确识别和标记？

**量化方法：** 构建评测集（100份文档+人工标注的"标准提取文本"），用字符级别的F1-score衡量提取质量。对于表格，可以额外计算表格单元格的匹配率。

---

> **下一篇预告：** 文档解析完成后，下一步是切分。切分粒度太大检索不精准，太小又丢失上下文。下一篇《切分策略全解》将对比三种主流切分策略，并给出生产环境的最佳实践。
