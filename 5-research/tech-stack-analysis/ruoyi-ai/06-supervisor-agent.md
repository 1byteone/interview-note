# 06 · Supervisor Agent 多智能体编排：1 个 Supervisor + 4 个子 Agent

> Supervisor Agent 是 ruoyi-ai 多智能体架构的核心调度器，负责协调 Skills Agent、WebSearch Agent、SQL Agent 和 Chart Agent 四个子智能体，实现"感知-决策-执行"的完整智能体协作链路。每个子智能体专注于特定领域的能力，Supervisor 根据用户意图动态路由到合适的智能体，必要时进行多智能体协同完成任务。
>
> **对应项目模块：** `ruoyi-chat/agent/`（Supervisor Agent 调度）

---

## 一、你必须知道的 3 个核心概念

### 1.1 Agent 编排（Agent Orchestration）

Agent 编排是指**将多个独立的 AI 智能体组合成一个协作系统**，让它们各司其职、协同完成复杂任务。区别于单一 Agent 包揽所有工作，编排模式让每个 Agent 专注于自己的领域，通过 Supervisor 协调调度。

| 特性 | 单一 Agent 模式 | Supervisor 多 Agent 编排模式 |
|------|----------------|---------------------------|
| **能力边界** | 所有工具集中在一个 Agent 中，prompt 臃肿 | 每个 Agent 专注一个领域，prompt 轻量化 |
| **扩展性** | 新增工具需要修改已有 Agent 的 tool 列表 | 新增子 Agent 无需修改 Supervisor 核心逻辑 |
| **容错性** | 一个工具出错可能影响整个 Agent | 子 Agent 故障可被隔离，Supervisor 降级处理 |
| **可维护性** | 工具数量增多后，LLM 的 tool 选择准确率下降 | 每个子 Agent 工具少，LLM 选择更精准 |
| **可观测性** | 难以追踪"哪个工具被调用、为什么" | 每个子 Agent 独立日志，调用链路清晰 |

**通俗理解：** 单一 Agent 就像"全能打杂"——什么都会但什么都不精；Supervisor 多 Agent 编排就像"一个项目经理 + 四个专家"——项目经理（Supervisor）理解用户需求，分派给对应专家（子 Agent），专家完成后汇总结果。

### 1.2 工具调用（Tool Calling）

工具调用是 LLM **根据用户请求动态选择并调用外部能力**的机制，也称为 Function Calling。在 Supervisor 多智能体架构中，工具调用分两层：

**第一层：Supervisor 层 —— 选择子 Agent**

Supervisor Agent 的 Tool 列表就是"四个子 Agent 的入口方法"。LLM 分析用户意图后，决定调用哪个子 Agent。例如用户说"查一下昨天的销售数据"，Supervisor 判断需要 SQL 查询，于是调用 SQL Agent 的入口 Tool。

**第二层：子 Agent 层 —— 执行具体操作**

每个子 Agent 内部又有自己的 Tool 列表。例如 SQL Agent 有 `query_database`、`get_schema` 等工具；Skills Agent 通过 MCP 协议集成了文件系统读写、Python 脚本执行等工具。

```
用户请求
    │
    ▼
Supervisor Agent（第一层：选择子 Agent）
    │
    ├── 调用 Skills Agent Tool → Skills Agent 内部工具（第二层：MCP 文件系统、Python 脚本）
    ├── 调用 WebSearch Agent Tool → WebSearch Agent 内部工具（第二层：搜索引擎 API）
    ├── 调用 SQL Agent Tool → SQL Agent 内部工具（第二层：数据库查询、Schema 感知）
    └── 调用 Chart Agent Tool → Chart Agent 内部工具（第二层：ECharts 配置生成）
```

### 1.3 Supervisor 调度（Supervisor Scheduling）

Supervisor 调度是**多智能体协同的核心决策机制**，包含三个关键步骤：

1. **意图识别**：分析用户请求，判断需要哪个（些）子 Agent 来处理
2. **任务分派**：调用对应子 Agent 的入口方法，传入上下文
3. **结果聚合**：收集子 Agent 的返回结果，组合成最终回复

**调度策略对比：**

| 策略 | 说明 | 适用场景 |
|------|------|----------|
| **单路调度** | Supervisor 选择其中一个子 Agent 执行 | 用户意图明确，单一子 Agent 可完成 |
| **多路调度** | Supervisor 同时调用多个子 Agent，结果合并 | 需要多源信息综合分析 |
| **链式调度** | 子 Agent A 的输出作为子 Agent B 的输入 | 数据处理流水线（如：查询数据 → 生成图表） |
| **反馈调度** | 子 Agent 执行结果不满足时，Supervisor 重新调度 | 首次结果不理想，需要补充信息 |

---

## 二、项目中的实战应用

### 2.1 解决了什么问题

ruoyi-ai 作为一个企业级 AI 应用平台，面临的核心挑战是"用户需求多样且复杂"——用户可能问技术问题（需要查本地文档），也可能问实时信息（需要联网搜索），还可能查数据库（需要写 SQL）或看数据图表。单一 Agent 无法同时胜任这么多场景：

| 问题 | 描述 | Supervisor 多 Agent 的解决方案 |
|------|------|-------------------------------|
| **工具数量膨胀** | 所有工具堆在一个 Agent 中，LLM 选择准确率下降 | 每个子 Agent 只管理 3-5 个工具，专注领域 |
| **Prompt 冲突** | 不同场景的指令相互干扰（如"查数据库"和"画图表"的 prompt 不同） | 每个子 Agent 有独立的 system prompt，互不干扰 |
| **能力隔离** | 文件系统操作失败不应影响搜索功能 | 子 Agent 独立运行，异常隔离 |
| **扩展性差** | 新增一个能力（如新增数据分析 Agent）需要修改整个 Agent 代码 | 新增子 Agent 只需注册到 Supervisor，不修改现有代码 |
| **可观测性差** | 所有工具调用混在一起，难以定位问题 | 每个子 Agent 独立日志 + 采样链路追踪 |

### 2.2 Supervisor 多智能体架构图

```dot
digraph SupervisorAgent {
    rankdir=TB;
    node [fontname="Microsoft YaHei", style="filled", rounded];
    edge [fontname="Microsoft YaHei", fontsize=10];

    subgraph cluster_supervisor {
        label="Supervisor Agent（调度器）";
        style="rounded,dashed";
        color="#E74C3C";
        fontcolor="#E74C3C";
        node [fillcolor="#E74C3C", fontcolor="white", shape="box"];
        Supervisor [label="Supervisor Agent\n意图识别 + 任务分派 + 结果聚合"];
    }

    subgraph cluster_agents {
        label="子智能体（Sub-Agents）";
        style="rounded,dashed";
        color="#3498DB";
        fontcolor="#3498DB";

        node [fillcolor="#3498DB", fontcolor="white", shape="box"];
        Skills [label="Skills Agent\n本地文档技能执行器\n(docx/pdf/xlsx + Python)"];
        WebSearch [label="WebSearch Agent\n联网搜索实时信息\n(搜索引擎 API)"];
        SQL [label="SQL Agent\n自然语言数据库查询\n(自动感知 Schema + 生成 SQL)"];
        Chart [label="Chart Agent\n数据可视化图表生成\n(ECharts 配置)"];
    }

    subgraph cluster_tools {
        label="各 Agent 的工具（Tools）";
        style="rounded,dashed";
        color="#2ECC71";
        fontcolor="#2ECC71";

        node [fillcolor="#2ECC71", fontcolor="white", shape="folder"];
        skills_tools [label="read_file\nwrite_file\nexecute_python\nlist_directory"];
        web_tools [label="search_web\nfetch_url\nextract_content"];
        sql_tools [label="get_schema\ngenerate_sql\nexecute_query"];
        chart_tools [label="generate_echarts\nrender_chart"];
    }

    // 连接线
    Supervisor -> Skills [label="调用 Skills Tool", fontcolor="#E74C3C", color="#E74C3C"];
    Supervisor -> WebSearch [label="调用 WebSearch Tool", fontcolor="#E74C3C", color="#E74C3C"];
    Supervisor -> SQL [label="调用 SQL Tool", fontcolor="#E74C3C", color="#E74C3C"];
    Supervisor -> Chart [label="调用 Chart Tool", fontcolor="#E74C3C", color="#E74C3C"];

    Skills -> skills_tools [style="dashed", color="#3498DB"];
    WebSearch -> web_tools [style="dashed", color="#3498DB"];
    SQL -> sql_tools [style="dashed", color="#3498DB"];
    Chart -> chart_tools [style="dashed", color="#3498DB"];

    // 用户输入
    node [fillcolor="#9B59B6", fontcolor="white", shape="ellipse"];
    User [label="用户输入"];
    User -> Supervisor [color="#9B59B6"];

    // 结果输出
    node [fillcolor="#F39C12", fontcolor="white", shape="ellipse"];
    Result [label="最终回复"];
    Supervisor -> Result [color="#F39C12"];
}
```

**架构说明：**

- **Supervisor Agent**（红色）：多智能体系统的"大脑"，负责意图识别、任务分派和结果聚合。它本身不执行具体业务逻辑，而是通过 Tool Calling 机制调用子 Agent
- **四个子 Agent**（蓝色）：每个子 Agent 是领域专家，有独立的 system prompt 和工具列表
- **各 Agent 的工具**（绿色）：每个子 Agent 内部管理的具体工具，子 Agent 的 LLM 负责在这些工具中选择合适的调用
- **调用流程**：用户输入 → Supervisor 意图识别 → 调用对应子 Agent → 子 Agent 内部执行工具 → 结果返回 Supervisor → 聚合后回复用户

### 2.3 核心实现（关键代码片段，带逐行中文注释）

#### 2.3.1 Supervisor Agent 调度逻辑

```java
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Supervisor Agent —— 多智能体系统的核心调度器
 * 
 * 职责：
 * 1. 分析用户意图，决定调用哪个子智能体
 * 2. 将用户请求路由到合适的子智能体
 * 3. 收集子智能体的返回结果，组合成最终回复
 * 4. 处理子智能体调用失败时的降级方案
 * 
 * Supervisor 本身不执行业务逻辑，它通过 LangChain4j 的
 * Tool Calling 机制调用四个子 Agent 的入口方法。
 */
@Component
public class SupervisorAgent {

    private static final Logger log = LoggerFactory.getLogger(SupervisorAgent.class);

    /** 四个子智能体的引用 */
    private final SkillsAgent skillsAgent;
    private final WebSearchAgent webSearchAgent;
    private final SqlAgent sqlAgent;
    private final ChartAgent chartAgent;

    /** LangChain4j 的 AI 服务接口 */
    private final SupervisorAssistant assistant;

    /**
     * 构造函数 —— 注入所有子 Agent 并构建 AI 服务
     * 
     * SupervisorAgent 不直接管理子 Agent 的调用逻辑，
     * 而是将子 Agent 注册为 Tool，让 LLM 自行决定何时调用哪个子 Agent。
     * 
     * @param skillsAgent    本地文档技能执行子智能体
     * @param webSearchAgent 联网搜索子智能体
     * @param sqlAgent       数据库查询子智能体
     * @param chartAgent     图表生成子智能体
     * @param chatModel      LLM 模型实例（由 Spring 注入，支持多厂商切换）
     */
    public SupervisorAgent(SkillsAgent skillsAgent,
                           WebSearchAgent webSearchAgent,
                           SqlAgent sqlAgent,
                           ChartAgent chartAgent,
                           ChatLanguageModel chatModel) {
        this.skillsAgent = skillsAgent;
        this.webSearchAgent = webSearchAgent;
        this.sqlAgent = sqlAgent;
        this.chartAgent = chartAgent;

        // 使用 AiServices 构建 Supervisor 的 AI 助手
        // 关键：将 this（SupervisorAgent 自身）注册为 Tool 对象
        // 这样 LLM 就能看到 SupervisorAgent 中的 @Tool 方法
        // 从而决定调用哪个子 Agent
        this.assistant = AiServices.builder(SupervisorAssistant.class)
                .chatModel(chatModel)
                .tools(this)  // 将 SupervisorAgent 自身作为 Tool 注册
                .build();
    }

    /**
     * 处理用户请求 —— 入口方法
     * 
     * 流程：
     * 1. 将用户请求传递给 LLM
     * 2. LLM 分析意图，决定是否调用子 Agent 的 Tool
     * 3. 如果调用 Tool，执行对应子 Agent 的逻辑
     * 4. 子 Agent 返回结果，LLM 继续处理
     * 5. 最终 LLM 生成完整的回复
     * 
     * @param userMessage 用户输入的消息
     * @return 经过多智能体协作后的最终回复
     */
    public String processUserRequest(String userMessage) {
        log.info("Supervisor 收到用户请求：{}", userMessage);
        long startTime = System.currentTimeMillis();

        try {
            // 调用 LLM 处理用户请求
            // LLM 内部会分析意图，决定是否调用 @Tool 方法
            String result = assistant.chat(userMessage);

            long duration = System.currentTimeMillis() - startTime;
            log.info("Supervisor 处理完成，耗时：{}ms", duration);

            return result;
        } catch (Exception e) {
            log.error("Supervisor 处理请求失败", e);
            // 降级处理：返回友好的错误提示
            return "抱歉，系统处理您的请求时出现异常，请稍后重试。";
        }
    }

    // ==================== 子 Agent 入口方法（作为 Tool 暴露给 LLM）====================

    /**
     * 调用 Skills Agent —— 执行本地文档技能
     * 
     * 当用户需要处理本地文档（docx/pdf/xlsx）或执行 Python 脚本时，
     * LLM 会调用此方法。Skills Agent 通过 MCP 协议集成了文件系统工具。
     * 
     * @param request 用户的具体技能请求，例如"读取 report.docx 并提取关键数据"
     * @return 技能执行结果
     */
    @Tool("当用户需要处理本地文档（docx/pdf/xlsx/Excel）或执行 Python 脚本时，调用 Skills Agent")
    public String callSkillsAgent(@P("用户的技能请求描述，包含文件路径和需要执行的操作") String request) {
        log.info("Supervisor 调度 Skills Agent：request={}", request);
        try {
            return skillsAgent.execute(request);
        } catch (Exception e) {
            log.error("Skills Agent 执行失败", e);
            return "Skills Agent 执行失败：" + e.getMessage();
        }
    }

    /**
     * 调用 WebSearch Agent —— 联网搜索实时信息
     * 
     * 当用户需要获取实时信息（如新闻、天气、最新技术动态）时，
     * LLM 会调用此方法。WebSearch Agent 通过搜索引擎 API 获取最新数据。
     * 
     * @param query 搜索关键词，例如"2026年 AI 发展趋势"
     * @return 搜索结果摘要
     */
    @Tool("当用户需要最新的实时信息或联网搜索时，调用 WebSearch Agent")
    public String callWebSearchAgent(@P("搜索关键词，尽可能精确") String query) {
        log.info("Supervisor 调度 WebSearch Agent：query={}", query);
        try {
            return webSearchAgent.search(query);
        } catch (Exception e) {
            log.error("WebSearch Agent 执行失败", e);
            return "WebSearch Agent 执行失败：" + e.getMessage();
        }
    }

    /**
     * 调用 SQL Agent —— 自然语言数据库查询
     * 
     * 当用户需要查询数据库中的数据时，LLM 会调用此方法。
     * SQL Agent 自动感知数据库 Schema，将自然语言转换为 SQL 并执行。
     * 
     * @param request 用户的自然语言查询请求，例如"查询上个月销售额前10的产品"
     * @return 数据库查询结果
     */
    @Tool("当用户需要查询数据库中的数据（如销售数据、用户信息、订单记录）时，调用 SQL Agent")
    public String callSqlAgent(@P("用户的自然语言数据库查询请求") String request) {
        log.info("Supervisor 调度 SQL Agent：request={}", request);
        try {
            return sqlAgent.query(request);
        } catch (Exception e) {
            log.error("SQL Agent 执行失败", e);
            return "SQL Agent 执行失败：" + e.getMessage();
        }
    }

    /**
     * 调用 Chart Agent —— 数据可视化图表生成
     * 
     * 当用户需要将数据以图表形式展示时，LLM 会调用此方法。
     * Chart Agent 根据数据生成 ECharts 配置，前端渲染为可视化图表。
     * 
     * @param request 图表生成请求，包含数据说明和图表类型偏好
     * @return ECharts 配置 JSON
     */
    @Tool("当用户需要生成数据可视化图表（柱状图、折线图、饼图等）时，调用 Chart Agent")
    public String callChartAgent(@P("图表生成请求，包含数据描述和图表类型") String request) {
        log.info("Supervisor 调度 Chart Agent：request={}", request);
        try {
            return chartAgent.generateChart(request);
        } catch (Exception e) {
            log.error("Chart Agent 执行失败", e);
            return "Chart Agent 执行失败：" + e.getMessage();
        }
    }

    /**
     * Supervisor 的 AI 助手接口
     * 
     * 通过 LangChain4j 的 AiServices 动态代理实现。
     * 定义 LLM 的行为规范（system message）和输入输出格式。
     */
    public interface SupervisorAssistant {

        /**
         * 处理用户聊天消息
         * 
         * system message 定义了 Supervisor 的行为准则：
         * 1. 分析意图，选择正确的子 Agent
         * 2. 必要时组合多个子 Agent 的结果
         * 3. 用中文回复
         * 
         * @param userMessage 用户输入的消息
         * @return 经过多智能体协作后的回复
         */
        @SystemMessage("""
            你是一个多智能体系统的 Supervisor（调度器），你的职责是：

            1. 分析用户意图，判断需要调用哪个子智能体
            2. 可用的子智能体：
               - Skills Agent：处理本地文档（docx/pdf/xlsx）和执行 Python 脚本
               - WebSearch Agent：联网搜索实时信息
               - SQL Agent：查询数据库中的数据
               - Chart Agent：生成数据可视化图表
            3. 如果用户请求涉及多个方面，可以依次调用多个子智能体
            4. 子智能体返回结果后，用中文整理成完整的回复
            5. 如果子智能体执行失败，尝试用其他方式弥补，或告知用户

            注意：不要尝试自己回答问题，应该调用对应的子智能体来处理。
            """)
        String chat(@UserMessage String userMessage);
    }
}
```

#### 2.3.2 Skills Agent 定义

```java
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Skills Agent —— 本地文档技能执行子智能体
 * 
 * 职责：
 * 1. 读取和处理本地文档（docx、pdf、xlsx 等格式）
 * 2. 执行 Python 脚本进行数据处理
 * 3. 通过 MCP 协议集成文件系统工具
 * 4. 从文档中提取关键信息并返回给 Supervisor
 * 
 * Skills Agent 是 ruoyi-ai 中与 MCP 协议集成的核心子智能体，
 * 通过 MCP Server 暴露文件读写、脚本执行等能力。
 */
@Component
public class SkillsAgent {

    private static final Logger log = LoggerFactory.getLogger(SkillsAgent.class);

    /** Skills Agent 内部使用的 LLM 模型 */
    private final ChatLanguageModel chatModel;

    /** Skills Agent 的 AI 服务接口 */
    private final SkillsAssistant assistant;

    /** MCP 工具管理器，管理文件系统和 Python 脚本工具 */
    private final McpToolManager mcpToolManager;

    /**
     * 构造函数 —— 注入 LLM 模型和 MCP 工具管理器
     * 
     * Skills Agent 使用独立的 LLM 实例（与 Supervisor 不同），
     * 这样 Supervisor 的 system prompt 不会干扰 Skills Agent 的行为。
     * 
     * @param chatModel      Skills Agent 专用的 LLM 模型
     * @param mcpToolManager MCP 工具管理器，提供文件系统和 Python 脚本工具
     */
    public SkillsAgent(ChatLanguageModel chatModel,
                       McpToolManager mcpToolManager) {
        this.chatModel = chatModel;
        this.mcpToolManager = mcpToolManager;

        // 构建 Skills Agent 的 AI 服务
        // 将 MCP 工具注册为 @Tool，供内部 LLM 调用
        this.assistant = AiServices.builder(SkillsAssistant.class)
                .chatModel(chatModel)
                .tools(mcpToolManager.createToolObject())  // 注册 MCP 工具
                .build();
    }

    /**
     * 执行技能请求 —— 由 Supervisor 调用
     * 
     * @param request 技能请求描述，例如"读取 report.docx 中的表格数据"
     * @return 技能执行结果
     */
    public String execute(String request) {
        log.info("Skills Agent 开始执行：request={}", request);
        long startTime = System.currentTimeMillis();

        try {
            // Skills Agent 内部 LLM 会决定调用哪个 MCP 工具
            // 例如：读取文件 → 调用 read_file 工具
            //       执行脚本 → 调用 execute_python_script 工具
            String result = assistant.process(request);

            long duration = System.currentTimeMillis() - startTime;
            log.info("Skills Agent 执行完成，耗时：{}ms", duration);

            return result;
        } catch (Exception e) {
            log.error("Skills Agent 执行异常", e);
            throw new RuntimeException("Skills Agent 执行失败: " + request, e);
        }
    }

    /**
     * Skills Agent 的 AI 助手接口
     * 
     * 定义了 Skills Agent 的行为规范：
     * - 专注于本地文档处理
     * - 使用 MCP 工具进行操作
     * - 返回结构化结果
     */
    public interface SkillsAssistant {

        /**
         * 处理技能请求
         * 
         * @param request 用户的技能请求
         * @return 处理结果
         */
        @SystemMessage("""
            你是一个本地文档技能执行助手。你可以：
            1. 读取 docx、pdf、xlsx 等格式的本地文档
            2. 执行 Python 脚本来处理数据
            3. 列出目录内容，查找文件
            4. 从文档中提取关键信息

            请使用可用的 MCP 工具来完成用户的请求。
            将结果整理成清晰的中文描述返回。
            """)
        String process(@dev.langchain4j.service.UserMessage String request);
    }
}
```

#### 2.3.3 WebSearch Agent 定义

```java
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * WebSearch Agent —— 联网搜索子智能体
 * 
 * 职责：
 * 1. 根据用户查询调用搜索引擎 API 获取实时信息
 * 2. 对搜索结果进行摘要和整理
 * 3. 返回结构化的搜索结果给 Supervisor
 * 
 * WebSearch Agent 让 ruoyi-ai 具备"实时信息获取"能力，
 * 弥补 LLM 训练数据截止日期带来的知识滞后问题。
 */
@Component
public class WebSearchAgent {

    private static final Logger log = LoggerFactory.getLogger(WebSearchAgent.class);

    /** WebSearch Agent 内部使用的 LLM 模型 */
    private final ChatLanguageModel chatModel;

    /** 搜索引擎服务（封装了外部搜索 API 的调用） */
    private final SearchService searchService;

    /** WebSearch Agent 的 AI 服务接口 */
    private final WebSearchAssistant assistant;

    /**
     * 构造函数 —— 注入 LLM 模型和搜索服务
     * 
     * @param chatModel     WebSearch Agent 专用的 LLM 模型
     * @param searchService 搜索引擎服务，封装了搜索 API 调用逻辑
     */
    public WebSearchAgent(ChatLanguageModel chatModel,
                          SearchService searchService) {
        this.chatModel = chatModel;
        this.searchService = searchService;

        // 构建 WebSearch Agent 的 AI 服务
        // 将 searchService 中的 @Tool 方法注册为可调用工具
        this.assistant = AiServices.builder(WebSearchAssistant.class)
                .chatModel(chatModel)
                .tools(searchService)  // 注册搜索工具
                .build();
    }

    /**
     * 执行搜索 —— 由 Supervisor 调用
     * 
     * @param query 搜索关键词
     * @return 搜索结果摘要
     */
    public String search(String query) {
        log.info("WebSearch Agent 开始搜索：query={}", query);
        long startTime = System.currentTimeMillis();

        try {
            // WebSearch Agent 内部 LLM 会：
            // 1. 分析搜索关键词，优化查询语句
            // 2. 调用 searchService 的搜索工具
            // 3. 对搜索结果进行摘要和整理
            String result = assistant.search(query);

            long duration = System.currentTimeMillis() - startTime;
            log.info("WebSearch Agent 搜索完成，耗时：{}ms", duration);

            return result;
        } catch (Exception e) {
            log.error("WebSearch Agent 搜索异常", e);
            throw new RuntimeException("WebSearch Agent 搜索失败: " + query, e);
        }
    }

    /**
     * WebSearch Agent 的 AI 助手接口
     * 
     * 定义了搜索行为规范：
     * - 专注于联网搜索和信息整理
     * - 提供来源引用
     * - 标注信息的时效性
     */
    public interface WebSearchAssistant {

        /**
         * 执行搜索并返回整理后的结果
         * 
         * @param query 搜索关键词
         * @return 整理后的搜索结果
         */
        @SystemMessage("""
            你是一个联网搜索助手。你的职责是：
            1. 根据用户的查询关键词调用搜索引擎
            2. 对搜索结果进行摘要和整理
            3. 标注信息来源的时效性（如发布日期）
            4. 用中文返回整理后的结果

            注意：如果搜索无结果，请如实告知用户，不要编造信息。
            """)
        String search(@dev.langchain4j.service.UserMessage String query);
    }
}
```

#### 2.3.4 SQL Agent 定义

```java
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * SQL Agent —— 自然语言数据库查询子智能体
 * 
 * 职责：
 * 1. 自动感知数据库 Schema（表结构、字段、索引）
 * 2. 将自然语言查询转换为 SQL 语句
 * 3. 执行 SQL 查询并返回结果
 * 4. 对查询结果进行解释和整理
 * 
 * SQL Agent 让非技术人员也能通过自然语言查询数据库，
 * 无需编写 SQL 语句。
 */
@Component
public class SqlAgent {

    private static final Logger log = LoggerFactory.getLogger(SqlAgent.class);

    /** SQL Agent 内部使用的 LLM 模型 */
    private final ChatLanguageModel chatModel;

    /** 数据库查询服务（封装了 Schema 感知和 SQL 执行） */
    private final DatabaseQueryService databaseQueryService;

    /** SQL Agent 的 AI 服务接口 */
    private final SqlAssistant assistant;

    /**
     * 构造函数 —— 注入 LLM 模型和数据库查询服务
     * 
     * @param chatModel           SQL Agent 专用的 LLM 模型
     * @param databaseQueryService 数据库查询服务，提供 Schema 感知和 SQL 执行能力
     */
    public SqlAgent(ChatLanguageModel chatModel,
                    DatabaseQueryService databaseQueryService) {
        this.chatModel = chatModel;
        this.databaseQueryService = databaseQueryService;

        // 构建 SQL Agent 的 AI 服务
        // 将 databaseQueryService 中的 @Tool 方法注册为可调用工具
        this.assistant = AiServices.builder(SqlAssistant.class)
                .chatModel(chatModel)
                .tools(databaseQueryService)  // 注册数据库查询工具
                .build();
    }

    /**
     * 执行自然语言查询 —— 由 Supervisor 调用
     * 
     * 流程：
     * 1. 自动获取数据库 Schema 信息
     * 2. LLM 根据 Schema 和用户查询生成 SQL
     * 3. 执行 SQL 获取结果
     * 4. LLM 整理结果返回
     * 
     * @param request 用户的自然语言查询请求
     * @return 查询结果
     */
    public String query(String request) {
        log.info("SQL Agent 开始处理查询：request={}", request);
        long startTime = System.currentTimeMillis();

        try {
            // SQL Agent 内部 LLM 会：
            // 1. 调用 get_schema 工具获取数据库表结构
            // 2. 根据 Schema 和用户请求生成 SQL
            // 3. 调用 execute_query 工具执行 SQL
            // 4. 整理结果并返回
            String result = assistant.process(request);

            long duration = System.currentTimeMillis() - startTime;
            log.info("SQL Agent 查询完成，耗时：{}ms", duration);

            return result;
        } catch (Exception e) {
            log.error("SQL Agent 查询异常", e);
            throw new RuntimeException("SQL Agent 查询失败: " + request, e);
        }
    }

    /**
     * SQL Agent 的 AI 助手接口
     * 
     * 定义了 SQL 查询行为规范：
     * - 先获取 Schema 再生成 SQL
     * - 只执行 SELECT 查询（安全限制）
     * - 对结果进行解释
     */
    public interface SqlAssistant {

        /**
         * 处理自然语言查询
         * 
         * @param request 用户的自然语言查询
         * @return 查询结果和解释
         */
        @SystemMessage("""
            你是一个数据库查询助手。你可以：
            1. 查看数据库的表结构（Schema）
            2. 根据自然语言描述生成 SQL 查询
            3. 执行 SQL 查询并返回结果
            4. 对查询结果进行解释

            安全规则：
            - 只允许执行 SELECT 查询
            - 不允许修改数据库的 DML/DLL 操作
            - 查询结果包含数据行数，如结果过多只返回前 20 条

            请用中文解释查询结果。
            """)
        String process(@dev.langchain4j.service.UserMessage String request);
    }
}
```

#### 2.3.5 Chart Agent 定义

```java
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Chart Agent —— 数据可视化图表生成子智能体
 * 
 * 职责：
 * 1. 根据数据生成 ECharts 配置 JSON
 * 2. 支持多种图表类型（柱状图、折线图、饼图、散点图等）
 * 3. 自动选择合适的图表类型
 * 4. 优化图表配置（颜色、标签、图例等）
 * 
 * Chart Agent 让用户无需手动配置 ECharts，
 * 只需描述"想看什么数据"，即可生成专业的可视化图表。
 */
@Component
public class ChartAgent {

    private static final Logger log = LoggerFactory.getLogger(ChartAgent.class);

    /** Chart Agent 内部使用的 LLM 模型 */
    private final ChatLanguageModel chatModel;

    /** 图表生成服务（封装了 ECharts 配置生成逻辑） */
    private final ChartGenerationService chartGenerationService;

    /** Chart Agent 的 AI 服务接口 */
    private final ChartAssistant assistant;

    /**
     * 构造函数 —— 注入 LLM 模型和图表生成服务
     * 
     * @param chatModel             Chart Agent 专用的 LLM 模型
     * @param chartGenerationService 图表生成服务，提供 ECharts 配置生成能力
     */
    public ChartAgent(ChatLanguageModel chatModel,
                      ChartGenerationService chartGenerationService) {
        this.chatModel = chatModel;
        this.chartGenerationService = chartGenerationService;

        // 构建 Chart Agent 的 AI 服务
        // 将 chartGenerationService 中的 @Tool 方法注册为可调用工具
        this.assistant = AiServices.builder(ChartAssistant.class)
                .chatModel(chatModel)
                .tools(chartGenerationService)  // 注册图表生成工具
                .build();
    }

    /**
     * 生成图表 —— 由 Supervisor 调用
     * 
     * @param request 图表生成请求，包含数据说明和图表类型偏好
     * @return ECharts 配置 JSON
     */
    public String generateChart(String request) {
        log.info("Chart Agent 开始生成图表：request={}", request);
        long startTime = System.currentTimeMillis();

        try {
            // Chart Agent 内部 LLM 会：
            // 1. 分析数据特征，选择合适的图表类型
            // 2. 调用 generate_echarts 工具生成 ECharts 配置
            // 3. 验证配置的正确性
            String result = assistant.process(request);

            long duration = System.currentTimeMillis() - startTime;
            log.info("Chart Agent 图表生成完成，耗时：{}ms", duration);

            return result;
        } catch (Exception e) {
            log.error("Chart Agent 图表生成异常", e);
            throw new RuntimeException("Chart Agent 生成图表失败: " + request, e);
        }
    }

    /**
     * Chart Agent 的 AI 助手接口
     * 
     * 定义了图表生成行为规范：
     * - 自动选择图表类型
     * - 生成标准的 ECharts 配置
     * - 对数据进行适当格式化
     */
    public interface ChartAssistant {

        /**
         * 处理图表生成请求
         * 
         * @param request 图表生成请求
         * @return ECharts 配置 JSON
         */
        @SystemMessage("""
            你是一个数据可视化图表生成助手。你可以：
            1. 根据数据特征自动选择合适的图表类型（柱状图、折线图、饼图、散点图、雷达图等）
            2. 生成标准的 ECharts 5 配置 JSON
            3. 优化图表颜色、标签、图例、提示框等视觉元素
            4. 对数据进行排序和格式化

            图表类型选择建议：
            - 趋势变化：折线图
            - 分类对比：柱状图
            - 占比分布：饼图
            - 相关性：散点图

            请返回完整的 ECharts option 配置 JSON。
            """)
        String process(@dev.langchain4j.service.UserMessage String request);
    }
}
```

### 2.4 设计亮点

**1. 双层 Tool Calling 架构，职责清晰**

Supervisor 的 Tool 是"子 Agent 入口"，子 Agent 的 Tool 是"具体执行工具"。这种双层架构让每一层的 LLM 只需要关注自己层级的决策，避免单一 LLM 面对大量工具时的选择困难。

**2. 每个子 Agent 独立 LLM 实例，行为隔离**

每个子 Agent 使用独立的 `ChatLanguageModel` 实例和独立的 `SystemMessage`，互不干扰。Supervisor 的 system prompt 专注于"调度决策"，子 Agent 的 system prompt 专注于"领域执行"。修改一个子 Agent 的行为不会影响其他子 Agent。

**3. 异常隔离与降级，系统健壮**

每个子 Agent 的调用都有独立的 try-catch 包裹。一个子 Agent 的异常不会影响其他子 Agent 和 Supervisor 的正常运行。Supervisor 可以捕获子 Agent 失败后尝试降级方案（如：SQL Agent 查询失败，提示用户稍后重试，而不是整个系统崩溃）。

**4. 基于 LangChain4j 的 AiServices 动态代理**

所有 Agent 通过 `AiServices.builder()` 构建，使用 JDK 动态代理自动处理 Tool Calling 的完整流程（LLM 决策 → 工具调用 → 结果返回 → LLM 继续处理），开发者只需要定义 `@Tool` 方法和接口，无需手动管理调用链路。

**5. 可插拔扩展，新增子 Agent 成本低**

新增一个子 Agent 只需三步：① 创建新的 Agent 类（定义 `@Tool` 入口方法）② 在 Supervisor 的构造函数中注入 ③ 在 Supervisor 中添加对应的 `@Tool` 方法。无需修改框架代码。

---

## 三、面试高频题

### Q1: Supervisor 多智能体模式和单一 Agent 相比有什么优势？

**考察点：** 多智能体架构的设计思想、何时选择多 Agent 而非单 Agent、实际项目中的收益。

**回答思路：**

**背景：** 在项目初期，我们尝试过将所有工具放在一个 Agent 中。但随着工具数量增加（从 5 个增加到 20+ 个），LLM 的 Tool 选择准确率明显下降，而且不同场景的 prompt 指令相互冲突。比如"查数据库"和"画图表"的 prompt 要求完全不同，放在一个 system prompt 中很难兼顾。

**Supervisor 多 Agent 模式的核心优势：**

1. **Tool 选择准确率更高**：单一 Agent 有 20+ 个工具，LLM 需要从大量候选中选择，容易选错。Supervisor 只需要从 4 个子 Agent 中选择，每个子 Agent 内部只有 3-5 个工具，选择范围大幅缩小，准确率显著提升。

2. **Prompt 隔离，行为互不干扰**：每个子 Agent 有独立的 system prompt。Skills Agent 的 prompt 专注于"文件操作和文档处理"，SQL Agent 的 prompt 专注于"数据库 Schema 感知和 SQL 生成"。不会出现"搜索的 prompt 影响了 SQL 查询行为"的情况。

3. **异常隔离，系统更健壮**：子 Agent 各自独立运行，一个子 Agent 的故障不会影响其他子 Agent 和 Supervisor。例如文件系统 MCP Server 宕机，Skills Agent 不可用，但 WebSearch Agent 和 SQL Agent 仍然正常工作。

4. **水平扩展，独立优化**：每个子 Agent 可以使用不同的 LLM 模型。SQL Agent 可以用对 SQL 生成更擅长的模型，Skills Agent 可以用对中文文档理解更好的模型，各自独立优化。

**深度扩展：**

- **何时应该从单 Agent 切换到多 Agent？** 当出现以下信号时：① Agent 的 Tool 超过 10 个 ② System Prompt 超过 1000 tokens ③ 不同工具的使用场景差异很大 ④ 某些工具的调用频率远高于其他工具
- **多 Agent 的代价**：增加系统复杂度（需要 Supervisor 调度）、增加 LLM 调用次数（多了一层 LLM 调用）、增加延迟（额外的网络开销）

### Q2: 如何避免多个 Agent 间的冲突和资源竞争？

**考察点：** 多智能体系统中的资源隔离策略、并发控制、状态管理。

**回答思路：**

**背景：** 多 Agent 系统中，多个子 Agent 可能同时访问同一资源（如数据库连接池、文件系统、LLM 配额），如果不加控制，可能导致资源竞争、数据不一致、限流被触发等问题。

**解决方案：**

1. **资源隔离 —— 每个子 Agent 独立 LLM 实例**

```java
// 每个子 Agent 使用独立的 ChatLanguageModel 实例
// 这样即使一个子 Agent 的 LLM 调用超时，也不会阻塞其他子 Agent
public SupervisorAgent(
    @Qualifier("supervisorChatModel") ChatLanguageModel supervisorModel,
    @Qualifier("skillsAgentChatModel") ChatLanguageModel skillsModel,
    @Qualifier("sqlAgentChatModel") ChatLanguageModel sqlModel) {
    // 每个 Agent 使用不同的模型实例
}
```

2. **连接池隔离 —— 数据库连接池按 Agent 分组**

SQL Agent 和其他 Agent 使用不同的数据库连接池配置，避免 SQL Agent 的大量查询消耗完所有连接，导致其他模块无法访问数据库。

3. **超时控制 —— 每个子 Agent 有独立的超时时间**

```java
// 为每个子 Agent 设置独立的超时阈值
// 避免某个子 Agent 长时间挂起，阻塞整个请求
public String processUserRequest(String userMessage) {
    // 使用 CompletableFuture 实现超时控制
    CompletableFuture<String> future = CompletableFuture
        .supplyAsync(() -> assistant.chat(userMessage));
    
    try {
        // 设置 30 秒超时
        return future.get(30, TimeUnit.SECONDS);
    } catch (TimeoutException e) {
        log.warn("Supervisor 处理超时");
        future.cancel(true);
        return "请求处理超时，请简化您的问题后重试。";
    }
}
```

4. **请求级别的会话隔离 —— 不同用户的请求不共享状态**

通过 `@MemoryId` 注解 + `chatMemoryProvider` 实现每个用户/会话独立的 Chat Memory，不同用户的对话历史不会互相干扰。

**深度扩展：**

- **LLM 配额管理**：如果多个子 Agent 使用同一个 LLM API Key，需要限流（Rate Limiting）防止触发 API 配额限制
- **文件锁**：Skills Agent 操作文件时，需要考虑文件锁，避免并发写入导致数据损坏
- **事务隔离**：SQL Agent 的查询涉及事务时，需要明确事务边界，避免长事务

### Q3: Agent 执行失败时如何容错和恢复？

**考察点：** 异常处理策略、降级方案、重试机制、用户友好提示。

**回答思路：**

**背景：** 在 AI 应用中，Agent 执行失败的原因很多：LLM API 超时、MCP Server 连接断开、数据库查询超时、搜索结果为空等。一个好的容错方案需要考虑"失败后怎么办"。

**容错策略分层：**

**第一层：重试（Retry）**

对于瞬时故障（网络抖动、连接超时），自动重试是最高效的容错手段：

```java
/**
 * 带重试的 Agent 调用
 * 
 * 使用 Spring Retry 实现自动重试
 * 只对瞬时故障重试，业务逻辑异常不重试
 */
@Retryable(
    value = {TransientException.class},  // 瞬时异常才重试
    maxAttempts = 3,                     // 最多重试 3 次
    backoff = @Backoff(delay = 1000, multiplier = 2)  // 指数退避：1s, 2s, 4s
)
public String callSkillsAgent(String request) {
    return skillsAgent.execute(request);
}
```

**第二层：降级（Fallback）**

当重试仍失败时，提供降级方案，而不是直接报错：

```java
/**
 * 降级处理 —— 当子 Agent 不可用时，提供替代方案
 */
@Recover
public String recoverSkillsAgent(TransientException e, String request) {
    log.warn("Skills Agent 重试后仍失败，启用降级方案");
    
    // 降级方案：告知用户当前技能不可用，建议使用其他方式
    return "抱歉，本地文档技能服务暂时不可用。您可以尝试：\n" +
           "1. 稍后重试\n" +
           "2. 使用联网搜索获取相关信息\n" +
           "3. 联系管理员检查 MCP 服务状态";
}
```

**第三层：隔离（Bulkhead）**

使用舱壁隔离模式，防止一个子 Agent 的故障拖垮整个系统：

```java
/**
 * 舱壁隔离 —— 每个子 Agent 使用独立的线程池
 * 
 * 这样即使 Skills Agent 的线程池满了（MCP Server 卡死），
 * 其他子 Agent 仍然可以正常处理请求。
 */
// Skills Agent 的线程池
private final ExecutorService skillsExecutor = 
    Executors.newFixedThreadPool(5, new ThreadFactoryBuilder()
        .setNameFormat("skills-agent-%d")
        .build());

// WebSearch Agent 的线程池
private final ExecutorService searchExecutor = 
    Executors.newFixedThreadPool(5, new ThreadFactoryBuilder()
        .setNameFormat("websearch-agent-%d")
        .build());
```

**第四层：熔断（Circuit Breaker）**

当某个子 Agent 持续失败时，熔断器自动打开，快速失败而不是继续重试浪费资源。

**深度扩展：**

- **幂等性**：重试的前提是操作是幂等的。SQL Agent 的 SELECT 查询天然幂等，但 Skills Agent 的 `write_file` 操作不能随意重试（可能重复写入）
- **渐进式降级**：Supervisor 可以尝试"降级链路"——例如 SQL Agent 失败，可以降级为 WebSearch Agent 搜索公开数据；Chart Agent 失败，可以降级为用文本描述数据趋势

### Q4: 项目中 Agent 的 Tool 是怎么管理的？MCP Server 和本地工具怎么区分？

**考察点：** 工具注册机制、MCP 协议与本地工具的关系、LangChain4j 的 Tool 管理方式。

**回答思路：**

**背景：** ruoyi-ai 中 Agent 的 Tool 来源有两类：本地 Java 方法（通过 `@Tool` 注解定义）和 MCP Server 注册的外部工具。两者在 Agent 看来都是"可调用的工具"，但注册方式和生命周期管理不同。

**工具管理架构：**

**1. 本地工具（@Tool 注解）**

通过 LangChain4j 的 `@Tool` 注解将 Java 方法暴露为 LLM 可调用的工具：

```java
/**
 * 本地工具 —— 直接在 Java 类中定义
 * 
 * @Tool 注解中的 description 告诉 LLM 何时调用此工具
 * @P 注解描述每个参数的含义
 * 这些描述信息会被 LLM 用来决定是否调用该工具
 */
@Tool("当用户需要查询数据库 Schema 时，调用此工具获取表结构信息")
public String getSchema(@P("数据库表名，可选，不传则返回所有表") String tableName) {
    // 执行 SQL：SHOW TABLES 或 DESCRIBE tableName
    return databaseQueryService.getSchema(tableName);
}
```

**2. MCP 工具（外部注册）**

通过 MCP 协议动态注册的外部工具，由 MCP Server 提供：

```java
/**
 * MCP 工具 —— 通过 MCP 协议动态发现
 * 
 * MCP 工具的生命周期：
 * 1. Spring 启动时，MCP Client 连接 MCP Server
 * 2. 通过 list_tools 获取工具列表
 * 3. 自动转换为 LangChain4j 的 @Tool 格式
 * 4. 注册到对应 Agent 的 AiServices 中
 * 
 * 新增 MCP 工具：只需启动新 MCP Server，无需修改代码
 */
// 在 Skills Agent 中，通过 MCP 协议注册的工具
this.assistant = AiServices.builder(SkillsAssistant.class)
    .chatModel(chatModel)
    .tools(mcpToolManager.createToolObject())  // MCP 工具包装为 @Tool
    .build();
```

**3. 两者的区别与选择**

| 维度 | 本地工具（@Tool） | MCP 工具 |
|------|------------------|----------|
| **定义方式** | 在 Java 代码中用 `@Tool` 注解定义 | 在 MCP Server 中定义，Agent 动态发现 |
| **注册时机** | 编译时确定，代码写死 | 运行时动态发现，可热插拔 |
| **修改成本** | 需要修改代码、重新编译部署 | 修改 MCP Server 配置，无需重启 Agent |
| **适用场景** | 核心业务逻辑（数据库查询、图表生成） | 通用能力（文件操作、脚本执行） |
| **性能** | 直接 Java 方法调用，无网络开销 | 通过网络传输（SSE 或 stdio） |
| **生态** | 只能使用 Java 生态的工具 | 任何语言实现的 MCP Server 都可以集成 |

**管理方式：**

- **统一注册中心**：所有工具（包括本地和 MCP）最终都注册到 `AiServices` 的 `tools()` 方法中
- **工具描述自动生成**：LangChain4j 自动将 `@Tool` 注解的 description 和 `@P` 注解的参数描述转换为 LLM 可理解的 Function Calling 格式
- **工具列表动态更新**：MCP 工具支持运行时动态刷新（新增/移除工具），通过监听 MCP Server 的 `tools/list_changed` 通知

**深度扩展：**

- **工具优先级**：当本地工具和 MCP 工具功能重叠时，优先使用本地工具（性能更好，无网络开销）
- **工具鉴权**：MCP 工具需要额外的鉴权机制（MCP Server 的 API Key），本地工具通过 Spring Security 统一管理
- **工具可观测性**：所有工具调用（无论本地还是 MCP）都记录日志，包含：调用时间、耗时、参数、结果、是否成功

---

## 四、面试避坑指南

### 4.1 不要混淆 Supervisor 和子 Agent 的职责

**常见错误：** 面试时说"Supervisor 直接执行文件操作"或"Supervisor 直接写 SQL 查数据库"。

**纠正：** Supervisor 的职责是**调度**，不是**执行**。它只负责"判断用户意图，调用对应的子 Agent"，具体的业务逻辑在子 Agent 内部完成。Supervisor 的 `@Tool` 方法内部调用子 Agent 的入口方法，子 Agent 内部再调用具体工具。典型的"三层职责分离"：Supervisor（调度层）→ 子 Agent（领域层）→ 具体工具（执行层）。

### 4.2 不要忽略每个子 Agent 的独立 LLM 实例

**常见错误：** 面试时只说"Supervisor 调用子 Agent"，不提每个子 Agent 有自己的 LLM 实例。

**关键点：** 每个子 Agent 使用独立的 `ChatLanguageModel` 实例是架构的关键设计。如果所有子 Agent 共享同一个 LLM 实例，它们的 system prompt 会相互干扰，Tool 列表也会合并，失去了多 Agent 隔离的意义。在 Spring 中通过 `@Qualifier` 注入不同的模型 Bean，每个 Agent 的 prompt 和 tool 列表只对自己可见。

### 4.3 不要忘记 Tool @Tool 注解的 description 至关重要

**常见错误：** 面试时只关注代码逻辑，不提 Tool 描述对 LLM 决策的影响。

**关键点：** `@Tool` 注解的 `description` 和 `@P` 注解的参数描述，是 LLM 决定"何时调用、传入什么参数"的唯一依据。描述写得好，LLM 就选得准；描述写得模糊，LLM 就会选错或传错参数。例如：
- 好的描述：`@Tool("当用户需要查询数据库中的销售数据、订单记录时，调用 SQL Agent")`
- 差的描述：`@Tool("SQL Agent")`

在项目中，Tool 描述通常需要经过多次迭代优化，根据实际使用中 LLM 选择错误的情况不断调整描述文本。

### 4.4 不要混淆 @Tool 注解和 @SystemMessage 的职责

**常见错误：** 面试时把 Tool 描述和 System Prompt 混为一谈，认为它们做的事情一样。

**关键点：** `@Tool` 注解的 description 告诉 LLM"这个工具是做什么的、什么时候该用它"；`@SystemMessage` 告诉 LLM"你是一个什么样的角色、应该遵循什么行为准则"。职责不同：
- `@Tool` 描述：对外（告诉 LLM 工具的功能）
- `@SystemMessage`：对内（定义 Agent 自身的行为规范）

### 4.5 不要忽略 Supervisor 未调用任何子 Agent 的场景

**常见错误：** 面试时假设 Supervisor 一定会调用某个子 Agent。

**关键点：** 存在 Supervisor 不需要调用任何子 Agent 的场景，例如用户说"你好"、"谢谢"等简单问候。此时 Supervisor 应该直接回复，不需要调度子 Agent。这就要求 Supervisor 的 system prompt 要明确说明"只有需要特定能力时才调用子 Agent，简单问候直接回复"。如果 Supervisor 的 prompt 写得太强制（"必须调用一个子 Agent"），会导致"你好"这种简单问题也被路由到某个子 Agent，浪费资源和时间。

### 4.6 不要忽略多 Agent 调用的延迟叠加

**常见错误：** 面试时只说"多 Agent 更好"，不提多 Agent 带来的延迟问题。

**关键点：** 多 Agent 架构比单 Agent 多了一层 LLM 调用（Supervisor 的 LLM 判断 + 子 Agent 的 LLM 执行）。如果两个 Agent 都调用 LLM，总延迟是两次 LLM 调用的和。解决办法：
- 简单的问候直接回复，不经过 LLM
- 链式调用时，如果后续 Agent 不需要 LLM 推理（如 Chart Agent 的 ECharts 生成），可以用更小、更快的模型
- 考虑使用 LLM 缓存（Semantic Cache）减少重复调用

---

## 五、参考资料与扩展阅读

### 项目源码
- [ruoyi-ai GitHub 仓库](https://github.com/1byteone/ruoyi-ai) — `ruoyi-modules/ruoyi-chat/agent/` 模块（Supervisor Agent 调度）

### LangChain4j Agentic API 官方资源
- [LangChain4j 官方文档](https://docs.langchain4j.dev) — AiServices、@Tool 注解、Agent 构建
- [LangChain4j GitHub 仓库](https://github.com/langchain4j/langchain4j) — 1.13.0 版本，Agentic API 模块
- [LangChain4j Agent 示例](https://docs.langchain4j.dev/tutorials/ai-services) — AiServices 和 Tool 调用的完整示例

### 多智能体架构参考
- [LangChain Multi-Agent 架构](https://langchain-ai.github.io/langgraph/tutorials/multi_agent/) — 多智能体系统的设计模式
- [LangGraph Supervisor 模式](https://langchain-ai.github.io/langgraph/tutorials/multi_agent/supervisor/) — Supervisor Agent 的设计思路（ruoyi-ai 的设计灵感来源）

### 相关技术文档
- [07 · MCP 协议集成：让 AI Agent 拥有无限工具生态](./07-mcp-protocol.md) — Skills Agent 的 MCP 工具集成细节
- [05 · langgraph4j 流程编排引擎](./05-langgraph-flow-engine.md) — AI 工作流的图编排引擎

### 设计模式参考
- 调度器模式（Scheduler Pattern）—— Supervisor 调度多 Agent 的设计模式归类
- 微内核架构（Microkernel Architecture）—— Supervisor 作为核心，子 Agent 作为插件
- 策略模式（Strategy Pattern）—— 不同子 Agent 对应不同策略