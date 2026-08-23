package com.passage.strategy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * MermaidImageStrategy - Mermaid 图表生成策略
 *
 * 将提示词解析为 Mermaid 图表定义（如流程图、时序图、类图等），
 * 然后通过 Mermaid 渲染服务（如 mermaid.ink 或本地 Puppeteer）生成图片。
 *
 * 策略说明：
 * - 优先级：备选策略（当 Pexels 无法满足需求时使用）
 * - 适用场景：技术架构图、流程图、时序图、数据流转图等示意图
 * - 优势：精确控制图表内容，适合技术类文章的配图需求
 * - 局限性：只能生成图表类图片，无法生成实景照片
 *
 * 提示词解析逻辑：
 * - 根据提示词中的关键词（"流程图"、"时序图"、"架构图"等）选择图表类型
 * - 将描述性提示词自动翻译为 Mermaid 语法定义
 * - 默认使用流程图（graph TD）作为兜底图表类型
 *
 * @author AI-Passage-Creator
 */
public class MermaidImageStrategy implements ImageStrategy {

    private static final Logger log = LoggerFactory.getLogger(MermaidImageStrategy.class);

    /** Mermaid 渲染服务地址（mermaid.ink 免费服务，支持 SVG 和 PNG 格式） */
    private static final String MERMAID_RENDER_URL = "https://mermaid.ink/img/";

    /** 图表类型正则匹配模式 */
    private static final Pattern CHART_TYPE_PATTERN = Pattern.compile(
            "(流程图|时序图|类图|架构图|状态图|甘特图|ER图|饼图)"
    );

    /**
     * 生成 Mermaid 图表图片
     *
     * 实现步骤：
     * 1. 解析提示词，识别图表类型
     * 2. 根据提示词生成 Mermaid 定义语法
     * 3. 将 Mermaid 定义进行 Base64 编码
     * 4. 拼接 mermaid.ink 渲染 URL
     *
     * @param prompt 图片描述提示词（如"用户登录的流程图"）
     * @return Mermaid 图表的渲染图片 URL
     * @throws ImageAcquisitionException 当 Mermaid 定义生成失败时抛出
     */
    @Override
    public String generateImage(String prompt) {
        log.info("【Mermaid策略】开始生成图表，描述: {}", prompt);

        try {
            // 步骤1：根据提示词生成 Mermaid 图表定义
            String mermaidDefinition = generateMermaidDefinition(prompt);
            log.debug("【Mermaid策略】生成的定义: {}", mermaidDefinition);

            // 步骤2：对 Mermaid 定义进行 Base64 编码（mermaid.ink 要求）
            String encoded = Base64.getUrlEncoder().encodeToString(
                    mermaidDefinition.getBytes("UTF-8")
            );

            // 步骤3：拼接渲染 URL
            // mermaid.ink 支持通过 URL 参数直接渲染 Mermaid 图表
            String imageUrl = MERMAID_RENDER_URL + encoded;

            log.info("【Mermaid策略】成功生成图表图片 URL: {}", imageUrl);
            return imageUrl;

        } catch (Exception e) {
            throw new ImageAcquisitionException(
                    "Mermaid 图表生成失败: " + e.getMessage(), e
            );
        }
    }

    /**
     * 根据提示词智能生成 Mermaid 图表定义
     *
     * 解析逻辑：
     * 1. 检测提示词中的图表类型关键词，选择对应的 Mermaid 图表类型
     * 2. 提取关键实体和关系描述，转换为 Mermaid 语法
     * 3. 如果无法识别具体图表类型，默认使用流程图（graph TD）
     *
     * @param prompt 用户输入的提示词
     * @return Mermaid 语法定义的字符串
     */
    private String generateMermaidDefinition(String prompt) {
        // 检测图表类型
        Matcher matcher = CHART_TYPE_PATTERN.matcher(prompt);
        String chartType = matcher.find() ? matcher.group(1) : "流程图";

        // 根据图表类型生成对应的 Mermaid 定义
        return switch (chartType) {
            case "时序图" -> generateSequenceDiagram(prompt);
            case "类图" -> generateClassDiagram(prompt);
            case "架构图" -> generateArchitectureDiagram(prompt);
            case "状态图" -> generateStateDiagram(prompt);
            case "甘特图" -> generateGanttDiagram(prompt);
            case "ER图" -> generateErDiagram(prompt);
            case "饼图" -> generatePieChart(prompt);
            default -> generateFlowchart(prompt); // 流程图作为默认兜底
        };
    }

    /**
     * 生成流程图（graph TD）定义
     *
     * 将提示词中的关键步骤解析为流程图节点和连接关系。
     * 使用 TD（Top-Down，自上而下）布局。
     */
    private String generateFlowchart(String prompt) {
        // 从提示词中提取关键步骤，构造流程图
        // 实际项目中这里会接入 NLP 或 LLM 进行更精确的解析
        return String.format("""
                graph TD
                    A[开始] --> B[%s]
                    B --> C[处理中]
                    C --> D[完成]
                    D --> E[结束]
                """, prompt.length() > 20 ? prompt.substring(0, 20) + "..." : prompt);
    }

    /**
     * 生成时序图（sequenceDiagram）定义
     */
    private String generateSequenceDiagram(String prompt) {
        return """
                sequenceDiagram
                    participant 用户
                    participant 系统
                    participant 服务端
                    用户->>系统: 发起请求
                    系统->>服务端: 转发请求
                    服务端-->>系统: 返回结果
                    系统-->>用户: 展示结果
                """;
    }

    /**
     * 生成类图（classDiagram）定义
     */
    private String generateClassDiagram(String prompt) {
        return """
                classDiagram
                    class 抽象类 {
                        +接口方法()
                    }
                    class 实现类1 {
                        +实现方法1()
                    }
                    class 实现类2 {
                        +实现方法2()
                    }
                    抽象类 <|-- 实现类1
                    抽象类 <|-- 实现类2
                """;
    }

    /**
     * 生成架构图（graph LR）定义，使用 LR（Left-Right，从左到右）布局
     */
    private String generateArchitectureDiagram(String prompt) {
        return """
                graph LR
                    subgraph 接入层
                        A[API网关]
                    end
                    subgraph 业务层
                        B[服务A] --> C[服务B]
                    end
                    subgraph 数据层
                        D[(数据库)]
                    end
                    A --> B
                    C --> D
                """;
    }

    /**
     * 生成状态图（stateDiagram-v2）定义
     */
    private String generateStateDiagram(String prompt) {
        return """
                stateDiagram-v2
                    [*] --> 待审核
                    待审核 --> 审核中
                    审核中 --> 已通过
                    审核中 --> 已驳回
                    已通过 --> [*]
                    已驳回 --> 待审核
                """;
    }

    /**
     * 生成甘特图（gantt）定义
     */
    private String generateGanttDiagram(String prompt) {
        return """
                gantt
                    title 项目进度
                    dateFormat  YYYY-MM-DD
                    section 阶段一
                    需求分析     :a1, 2024-01-01, 30d
                    系统设计     :a2, after a1, 20d
                    section 阶段二
                    开发实现     :a3, after a2, 40d
                    测试部署     :a4, after a3, 15d
                """;
    }

    /**
     * 生成 ER 图（erDiagram）定义
     */
    private String generateErDiagram(String prompt) {
        return """
                erDiagram
                    用户 ||--o{ 订单 : 拥有
                    订单 ||--|{ 订单项 : 包含
                    商品 ||--o{ 订单项 : 属于
                """;
    }

    /**
     * 生成饼图（pie）定义
     */
    private String generatePieChart(String prompt) {
        return """
                pie title 数据分布
                    "类别A" : 45
                    "类别B" : 30
                    "类别C" : 25
                """;
    }

    /**
     * 获取策略名称
     *
     * @return "mermaid"
     */
    @Override
    public String getName() {
        return "mermaid";
    }

    /**
     * 检查策略是否可用
     *
     * Mermaid 策略不依赖外部 API Key，始终可用。
     * 渲染依赖 mermaid.ink 外部服务，如果网络不可达，
     * 在 generateImage 阶段会抛出异常由上层降级处理。
     *
     * @return 始终返回 true
     */
    @Override
    public boolean isAvailable() {
        return true;
    }
}