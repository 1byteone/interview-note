# Supervisor Agent 入门：从零搭建多智能体协作系统

> 本文是 ruoyi-ai 项目技术栈深度剖析系列的第6篇，面向 Java 后端开发者，旨在帮助读者从零搭建一个 Supervisor Agent 多智能体协作系统，涵盖 Supervisor/Sub-agent 架构设计、双层 Tool Calling 机制、独立 LLM 实例管理、异常隔离与容错，并对照分析 ruoyi-ai 项目中的 Supervisor Agent 真实实现。

---

## 一、项目背景：该技术栈在项目中的角色

### 1.1 为什么需要 Supervisor Agent

在复杂的企业级 AI 应用中，单一智能体往往无法胜任所有任务。例如，一个 AI 助手可能需要同时处理：

- **知识问答**：需要检索技术文档、FAQ 等内部知识库
- **实时搜索**：需要查询互联网上的最新信息
- **数据分析**：需要执行 SQL 查询从数据库中提取数据
- **图表生成**：需要将数据转化为可视化图表

如果把这些能力全部塞进一个智能体，会出现几个问题：

- **工具爆炸**：一个智能体可能有几十个工具，LLM 难以在这么多工具中做出正确选择
- **上下文混乱**：不同任务的指令和上下文混在一起，干扰 LLM 的判断
- **故障扩散**：一个工具调用失败可能导致整个智能体无法正常工作
- **资源争抢**：所有任务共享同一个 LLM 实例，无隔离，性能相互影响

Supervisor Agent 模式正是为了解决这些问题而生。它的核心思想是：**一个 Supervisor 负责"调度"和"决策"，多个 Sub-agent 各自负责一个特定领域，各司其职**。这就像一家公司，CEO 负责决策，各个部门负责执行。

### 1.2 在 ruoyi-ai 项目中的位置

在 ruoyi-ai 的 AI 层中，Supervisor Agent 位于多智能体协作的核心位置：

```
用户请求
    ↓
Supervisor Agent（调度决策层）
    ├── Skills Agent（技能执行 → MCP 工具）
    ├── WebSearch Agent（联网搜索 → SearchService）
    ├── SQL Agent（数据库查询 → DatabaseQueryService）
    └── Chart Agent（图表生成 → ChartGenerationService）
    ↓
返回结果
```

每个 Sub-agent 拥有独立的 LLM 实例、独立的工具集和独立的异常处理机制。Supervisor 不直接执行具体任务，而是分析用户请求，分派给最合适的 Sub-agent。

### 1.3 本文目标

本文的目标是帮助读者从零搭建一个最简的 Supervisor Agent 系统，实现"用户提问 -> Supervisor 分析 -> 分派 Sub-agent -> 执行任务 -> 返回结果"的完整流程。通过这个最小可行示例，读者将理解：

1. Supervisor Agent 的核心架构设计思想
2. 双层 Tool Calling 机制：Supervisor 层选子智能体，子智能体层执行具体工具
3. 独立 LLM 实例管理的优势和实现方式
4. 异常隔离和容错机制（retry / fallback / circuit breaker）

---

## 二、核心概念：3个，用生活类比解释

### 概念 1：Supervisor Agent —— 就像"项目经理"

**生活类比**：想象一个软件开发项目。项目经理（Supervisor）不直接写代码，而是分析需求，然后分派给合适的团队成员。如果需求是"写一个登录功能"，项目经理会分派给后端开发（负责 API）和前端开发（负责页面）。如果团队中有人请假，项目经理会安排其他人接手，或者通知用户"这个任务暂时无法完成"。

**技术映射**：Supervisor Agent 是一个智能体，它不直接执行具体任务，而是：

- **分析用户请求**：理解用户想要什么
- **选择 Sub-agent**：根据请求类型分派给最合适的子智能体
- **处理异常**：如果子智能体执行失败，返回友好的错误信息
- **组合结果**：将子智能体的结果返回给用户

**关键点**：Supervisor 的核心能力是"决策"，而不是"执行"。它通过 @Tool 方法暴露子智能体的调用入口，LLM 自行决定调用哪个工具。

### 概念 2：双层 Tool Calling —— 就像"公司组织架构"

**生活类比**：想象一家公司。CEO（Supervisor）有四个部门经理的电话（工具）。当有客户需求时，CEO 判断需求属于哪个部门，然后打电话给对应的部门经理。部门经理（Sub-agent）接到任务后，再调用自己团队的具体工具完成工作。CEO 不需要知道每个部门内部是怎么工作的，只需要知道每个部门能做什么。

**技术映射**：双层 Tool Calling 是 Supervisor Agent 的核心机制：

- **第一层（Supervisor 层）**：Supervisor 的 @Tool 是"调用 SkillsAgent"、"调用 WebSearchAgent"、"调用 SqlAgent"、"调用 ChartAgent"——每个工具对应一个 Sub-agent 的入口
- **第二层（Sub-agent 层）**：每个 Sub-agent 有自己的 @Tool 方法，如 SkillsAgent 下的 MCP 工具调用、WebSearchAgent 下的搜索 API 调用

**关键点**：两层之间完全解耦。Supervisor 只需要知道"SkillsAgent 能处理技能类任务"，不需要知道 SkillsAgent 内部如何调用 MCP 工具。每个 Sub-agent 是一个独立的智能体，拥有自己的工具集和 LLM 实例。

### 概念 3：异常隔离与容错 —— 就像"保险丝"

**生活类比**：想象你家中的电路系统。每个房间都有一个独立的保险丝（Bulkhead），如果厨房的电路短路了，只有厨房的保险丝会跳闸，其他房间的灯还亮着。同时，每个电器还有一个过载保护器（Circuit Breaker），如果某个电器连续故障，保护器会暂时切断电源，防止火灾。

**技术映射**：Supervisor Agent 的异常隔离与容错机制：

- **Bulkhead（舱壁隔离）**：每个 Sub-agent 有独立的线程池，一个 Sub-agent 的资源耗尽不影响其他 Sub-agent
- **Timeout（超时控制）**：每个工具调用有超时限制（如 30 秒），超时自动中断
- **Retry（重试）**：调用失败时自动重试（如 @Retryable 注解），重试次数耗尽后触发 fallback
- **Fallback（降级）**：重试失败后返回友好的降级消息，而不是抛出异常
- **Circuit Breaker（熔断）**：连续失败达到阈值后，暂时切断调用，避免级联故障

---

## 三、从零搭建：完整代码

### 3.1 项目结构

```
agent-demo/
├── pom.xml
├── src/main/java/com/agentdemo/
│   ├── AgentDemoApplication.java        # 启动类
│   ├── supervisor/
│   │   ├── SupervisorAgent.java         # Supervisor 智能体
│   │   └── SupervisorAssistant.java     # Supervisor 接口定义
│   ├── subagent/
│   │   ├── SkillsAgent.java            # 技能执行智能体
│   │   ├── WebSearchAgent.java         # 联网搜索智能体
│   │   ├── SqlAgent.java               # 数据库查询智能体
│   │   └── ChartAgent.java             # 图表生成智能体
│   ├── service/
│   │   ├── SearchService.java          # 搜索服务
│   │   ├── DatabaseQueryService.java   # 数据库查询服务
│   │   └── ChartGenerationService.java # 图表生成服务
│   └── controller/
│       └── AgentController.java        # REST 控制器
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

    <groupId>com.agentdemo</groupId>
    <artifactId>agent-demo</artifactId>
    <version>1.0.0</version>
    <name>agent-demo</name>
    <description>Supervisor Agent 多智能体协作示例</description>

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

        <!-- OpenAI 模型 -->
        <dependency>
            <groupId>dev.langchain4j</groupId>
            <artifactId>langchain4j-open-ai-spring-boot-starter</artifactId>
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
    name: agent-demo

# LangChain4j 配置
langchain4j:
  open-ai:
    chat-model:
      api-key: ${OPENAI_API_KEY}
      model-name: gpt-4o-mini
      temperature: 0.0

# 各子智能体独立配置（使用不同的模型或参数）
agent:
  supervisor:
    model: gpt-4o-mini
    temperature: 0.0
  skills:
    model: gpt-4o-mini
    temperature: 0.1
  websearch:
    model: gpt-4o-mini
    temperature: 0.0
  sql:
    model: gpt-4o-mini
    temperature: 0.0
  chart:
    model: gpt-4o-mini
    temperature: 0.2
```

### 3.4 核心代码实现

#### 3.4.1 Supervisor 智能体

```java
package com.agentdemo.supervisor;

import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * Supervisor 智能体 —— 多智能体协作系统的调度中心
 *
 * 职责：
 * 1. 分析用户请求，判断需要调用哪个 Sub-agent
 * 2. 将任务分派给对应的 Sub-agent 执行
 * 3. 处理 Sub-agent 的异常，返回友好提示
 *
 * 设计模式：双层 Tool Calling
 * - 第一层：Supervisor 的 @Tool 方法（本类）
 * - 第二层：Sub-agent 内部的 @Tool 方法（各 Sub-agent 类）
 *
 * Supervisor 的每个 @Tool 方法对应一个 Sub-agent 的入口
 * LLM 自行决定调用哪个工具，实现"智能分派"
 */
@Component
public class SupervisorAgent {

    private static final Logger log = LoggerFactory.getLogger(SupervisorAgent.class);

    @Resource
    private SkillsAgent skillsAgent;
    @Resource
    private WebSearchAgent webSearchAgent;
    @Resource
    private SqlAgent sqlAgent;
    @Resource
    private ChartAgent chartAgent;

    /**
     * 调用技能执行智能体
     *
     * 适用场景：用户需要执行预定义的技能，如"生成周报"、"发送邮件通知"
     * 每个技能对应一个具体的业务操作，由 SkillsAgent 内部通过 MCP 工具执行
     *
     * @param skillName 技能名称，如 "generate_report"、"send_email"
     * @param params    技能参数，JSON 格式的参数字符串
     * @return 技能执行结果
     */
    @Tool("调用技能执行智能体，执行预定义的业务技能")
    public String callSkillsAgent(String skillName, String params) {
        log.info("Supervisor 分派任务给 SkillsAgent：skill={}", skillName);
        try {
            // 调用 SkillsAgent 执行技能
            // SkillsAgent 内部可能调用 MCP 工具完成具体操作
            return skillsAgent.executeSkill(skillName, params);
        } catch (Exception e) {
            log.error("SkillsAgent 执行失败", e);
            // 返回友好的降级消息，而不是抛出异常
            return "技能执行失败：" + skillName + "，原因：" + e.getMessage();
        }
    }

    /**
     * 调用联网搜索智能体
     *
     * 适用场景：用户需要实时信息，如"今天天气怎么样"、"最新新闻"
     * WebSearchAgent 内部调用搜索 API 获取实时数据
     *
     * @param query 搜索关键词
     * @return 搜索结果
     */
    @Tool("调用联网搜索智能体，获取实时互联网信息")
    public String callWebSearchAgent(String query) {
        log.info("Supervisor 分派任务给 WebSearchAgent：query={}", query);
        try {
            return webSearchAgent.search(query);
        } catch (Exception e) {
            log.error("WebSearchAgent 执行失败", e);
            return "联网搜索失败，请稍后重试。";
        }
    }

    /**
     * 调用数据库查询智能体
     *
     * 适用场景：用户需要查询数据库数据，如"上个月销售额是多少"
     * SqlAgent 内部将自然语言转为 SQL 并执行（只读查询）
     *
     * @param question 用户的数据库查询问题
     * @return 查询结果
     */
    @Tool("调用数据库查询智能体，执行数据库查询操作（只读）")
    public String callSqlAgent(String question) {
        log.info("Supervisor 分派任务给 SqlAgent：question={}", question);
        try {
            return sqlAgent.query(question);
        } catch (Exception e) {
            log.error("SqlAgent 执行失败", e);
            return "数据库查询失败，请稍后重试。";
        }
    }

    /**
     * 调用图表生成智能体
     *
     * 适用场景：用户需要将数据可视化，如"生成销售额趋势图"
     * ChartAgent 内部调用 ECharts 生成图表
     *
     * @param chartType    图表类型，如 bar / line / pie
     * @param chartConfig  图表配置，JSON 格式
     * @return 图表生成结果
     */
    @Tool("调用图表生成智能体，生成数据可视化图表")
    public String callChartAgent(String chartType, String chartConfig) {
        log.info("Supervisor 分派任务给 ChartAgent：type={}", chartType);
        try {
            return chartAgent.generateChart(chartType, chartConfig);
        } catch (Exception e) {
            log.error("ChartAgent 执行失败", e);
            return "图表生成失败，请稍后重试。";
        }
    }
}
```

```java
package com.agentdemo.supervisor;

import dev.langchain4j.service.SystemMessage;

/**
 * Supervisor 智能体接口 —— 定义 LLM 的 System Prompt 和交互方式
 *
 * 使用 LangChain4j 的 @AiService 注解
 * 框架自动生成实现类，将 LLM 调用封装为接口方法
 * @SystemMessage 定义了 LLM 的角色和行为规则
 */
public interface SupervisorAssistant {

    /**
     * 处理用户消息，返回响应
     *
     * @SystemMessage 定义了 Supervisor 的行为规则：
     * 1. 告诉 LLM 它有哪些工具可用（四个 @Tool 方法）
     * 2. 要求 LLM 分析用户请求并选择最合适的工具
     * 3. 定义工具调用失败时的处理方式
     *
     * @param userMessage 用户输入的消息
     * @return Supervisor 的响应
     */
    @SystemMessage("""
            你是一个智能助手调度中心（Supervisor Agent）。
            你的职责是分析用户的请求，然后调用最合适的工具来完成任务。

            可用的工具：
            1. callSkillsAgent - 执行预定义的业务技能（如生成报告、发送通知）
            2. callWebSearchAgent - 搜索互联网获取实时信息
            3. callSqlAgent - 查询数据库（只读）
            4. callChartAgent - 生成图表

            规则：
            - 分析用户请求，选择最合适的工具
            - 如果用户请求涉及多个领域，可以依次调用多个工具
            - 如果工具调用失败，向用户返回友好的错误提示
            - 不要编造信息，只使用工具返回的结果
            """)
    String chat(String userMessage);
}
```

#### 3.4.2 Sub-agent 实现

```java
package com.agentdemo.supervisor;

import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * 技能执行智能体 —— 执行预定义的业务技能
 *
 * 职责：
 * 1. 接收 Supervisor 分派的技能名称和参数
 * 2. 查找对应的技能处理器
 * 3. 调用技能处理器执行具体操作
 *
 * 每个 Sub-agent 都是独立的智能体
 * 拥有自己的 LLM 实例和工具集
 * 通过 @Tool 方法暴露能力给 Supervisor
 */
@Component
public class SkillsAgent {

    private static final Logger log = LoggerFactory.getLogger(SkillsAgent.class);

    /**
     * 执行指定技能
     *
     * 技能可以是预定义的业务流程，如：
     * - generate_report：生成周报
     * - send_notification：发送通知
     * - analyze_data：数据分析
     *
     * 每个技能对应一个具体的业务操作
     * 实际项目中可以通过 MCP 工具或服务调用实现
     *
     * @param skillName 技能名称
     * @param params    技能参数（JSON 格式）
     * @return 执行结果
     */
    public String executeSkill(String skillName, String params) {
        log.info("SkillsAgent 执行技能：{}", skillName);

        // 根据技能名称路由到对应的处理器
        // 实际项目中，这里会调用 MCP 工具或业务服务
        return switch (skillName) {
            case "generate_report" -> generateReport(params);
            case "send_notification" -> sendNotification(params);
            case "analyze_data" -> analyzeData(params);
            default -> "未知技能：" + skillName;
        };
    }

    private String generateReport(String params) {
        // 实际项目中，这里会调用模板引擎 + LLM 生成报告
        return "报告生成完成，包含 " + params + " 相关数据。";
    }

    private String sendNotification(String params) {
        // 实际项目中，这里会调用消息服务发送通知
        return "通知已发送：" + params;
    }

    private String analyzeData(String params) {
        // 实际项目中，这里会调用数据分析服务
        return "数据分析完成：" + params;
    }
}
```

```java
package com.agentdemo.supervisor;

import com.agentdemo.service.SearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * 联网搜索智能体 —— 获取实时互联网信息
 *
 * 职责：
 * 1. 接收 Supervisor 分派的搜索请求
 * 2. 调用搜索服务获取实时数据
 * 3. 返回搜索结果
 *
 * 独立 LLM 实例：可以独立处理搜索结果
 * 与 Supervisor 的 LLM 实例隔离，互不影响
 */
@Component
public class WebSearchAgent {

    private static final Logger log = LoggerFactory.getLogger(WebSearchAgent.class);

    @Resource
    private SearchService searchService;

    /**
     * 执行联网搜索
     *
     * @param query 搜索关键词
     * @return 搜索结果摘要
     */
    public String search(String query) {
        log.info("WebSearchAgent 搜索：{}", query);

        // 调用搜索服务执行实际搜索
        // SearchService 封装了搜索 API 的调用逻辑
        String searchResult = searchService.search(query);

        // 返回搜索结果
        // 实际项目中，可以在此处用 LLM 对搜索结果做摘要总结
        return "搜索结果：" + searchResult;
    }
}
```

```java
package com.agentdemo.supervisor;

import com.agentdemo.service.DatabaseQueryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * 数据库查询智能体 —— 执行数据库查询操作
 *
 * 职责：
 * 1. 接收 Supervisor 分派的查询问题
 * 2. 将自然语言转换为 SQL 查询
 * 3. 执行查询并返回结果
 *
 * 安全限制：仅支持 SELECT 查询，防止数据被修改
 * 权限控制：通过数据库用户权限限制写操作
 */
@Component
public class SqlAgent {

    private static final Logger log = LoggerFactory.getLogger(SqlAgent.class);

    @Resource
    private DatabaseQueryService databaseQueryService;

    /**
     * 执行数据库查询
     *
     * @param question 用户的自然语言查询问题
     * @return 查询结果
     */
    public String query(String question) {
        log.info("SqlAgent 查询：{}", question);

        // 调用数据库查询服务
        // 实际项目中，这里会先通过 LLM 将自然语言转为 SQL
        // 然后执行 SQL 并返回结果
        String result = databaseQueryService.query(question);

        return "查询结果：" + result;
    }
}
```

```java
package com.agentdemo.supervisor;

import com.agentdemo.service.ChartGenerationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * 图表生成智能体 —— 生成数据可视化图表
 *
 * 职责：
 * 1. 接收 Supervisor 分派的图表类型和配置
 * 2. 调用图表生成服务
 * 3. 返回图表生成结果
 *
 * 支持图表类型：bar（柱状图）、line（折线图）、pie（饼图）
 */
@Component
public class ChartAgent {

    private static final Logger log = LoggerFactory.getLogger(ChartAgent.class);

    @Resource
    private ChartGenerationService chartGenerationService;

    /**
     * 生成图表
     *
     * @param chartType   图表类型：bar / line / pie
     * @param chartConfig 图表配置，包含数据、标题、颜色等
     * @return 图表生成结果
     */
    public String generateChart(String chartType, String chartConfig) {
        log.info("ChartAgent 生成图表：type={}", chartType);

        // 调用图表生成服务
        // 实际项目中，这里会生成 ECharts 配置或图片
        String result = chartGenerationService.generate(chartType, chartConfig);

        return "图表已生成：" + result;
    }
}
```

#### 3.4.3 基础服务实现

```java
package com.agentdemo.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 搜索服务 —— 封装搜索 API 的调用
 *
 * 实际项目中会接入具体的搜索服务
 * 如：百度搜索 API、Bing Search API、SerpAPI 等
 */
@Service
public class SearchService {

    private static final Logger log = LoggerFactory.getLogger(SearchService.class);

    /**
     * 执行搜索
     *
     * 本例使用模拟实现，返回固定结果
     * 实际项目中通过 RestTemplate 或 WebClient 调用搜索 API
     *
     * @param query 搜索关键词
     * @return 搜索结果的文本摘要
     */
    public String search(String query) {
        log.info("执行搜索：{}", query);

        // 模拟搜索：实际项目中调用搜索 API
        // 例如：RestTemplate.getForObject("https://api.search.com?q=" + query, String.class)
        return "【模拟搜索结果】关于 \"" + query + "\" 的搜索结果：\n"
                + "1. " + query + " 相关技术介绍\n"
                + "2. " + query + " 最佳实践指南\n"
                + "3. " + query + " 常见问题解答";
    }
}
```

```java
package com.agentdemo.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 数据库查询服务 —— 封装数据库查询逻辑
 *
 * 实际项目中，此处会先通过 LLM 将自然语言转为 SQL
 * 然后使用 JdbcTemplate 或 MyBatis 执行查询
 * 只支持 SELECT 操作，确保数据安全
 */
@Service
public class DatabaseQueryService {

    private static final Logger log = LoggerFactory.getLogger(DatabaseQueryService.class);

    /**
     * 执行数据库查询
     *
     * 本例使用模拟实现
     * 实际项目中：
     * 1. 调用 LLM 将自然语言转为 SQL
     * 2. 验证 SQL 为只读查询（仅 SELECT）
     * 3. 使用 JdbcTemplate 执行查询
     * 4. 将结果转为可读格式
     *
     * @param question 自然语言查询问题
     * @return 查询结果
     */
    public String query(String question) {
        log.info("执行数据库查询：{}", question);

        // 模拟查询结果
        // 实际项目中，会执行 SQL 并返回真实数据
        return "【模拟查询结果】根据 \"" + question + "\" 查询到：\n"
                + "总记录数：100 条\n"
                + "示例数据：{id: 1, name: \"示例\"}";
    }
}
```

```java
package com.agentdemo.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 图表生成服务 —— 封装图表生成逻辑
 *
 * 实际项目中，此处会生成 ECharts 配置 JSON
 * 前端接收到配置后直接渲染图表
 * 支持 bar / line / pie 三种基本图表类型
 */
@Service
public class ChartGenerationService {

    private static final Logger log = LoggerFactory.getLogger(ChartGenerationService.class);

    /**
     * 生成图表
     *
     * 本例使用模拟实现
     * 实际项目中会生成 ECharts 配置：
     * {
     *   "title": {"text": "销售额趋势"},
     *   "xAxis": {"data": ["1月", "2月", "3月"]},
     *   "series": [{"data": [120, 200, 150]}]
     * }
     *
     * @param chartType   图表类型
     * @param chartConfig 图表配置
     * @return 图表生成结果
     */
    public String generate(String chartType, String chartConfig) {
        log.info("生成图表：type={}, config={}", chartType, chartConfig);

        // 模拟图表生成
        // 实际项目中会生成 ECharts 配置 JSON
        return "【模拟图表】类型：" + chartType
                + "，配置：" + chartConfig
                + "，图表已生成并返回前端渲染。";
    }
}
```

#### 3.4.4 配置中心 —— 创建独立 LLM 实例

```java
package com.agentdemo.config;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * 智能体配置 —— 为每个 Sub-agent 创建独立的 LLM 实例
 *
 * 设计要点：
 * 1. 每个 Sub-agent 拥有独立的 LLM 实例，实现资源隔离
 * 2. 不同的 Sub-agent 可以使用不同的模型和参数
 * 3. 一个 Sub-agent 的 LLM 调用不会影响其他 Sub-agent
 *
 * 这种隔离设计是 Supervisor Agent 的核心优势之一
 * 避免了"所有任务共享一个 LLM"带来的性能干扰问题
 */
@Configuration
public class AgentConfig {

    @Value("${langchain4j.open-ai.chat-model.api-key}")
    private String apiKey;

    @Value("${agent.supervisor.model:gpt-4o-mini}")
    private String supervisorModel;

    @Value("${agent.skills.model:gpt-4o-mini}")
    private String skillsModel;

    @Value("${agent.websearch.model:gpt-4o-mini}")
    private String webSearchModel;

    @Value("${agent.sql.model:gpt-4o-mini}")
    private String sqlModel;

    @Value("${agent.chart.model:gpt-4o-mini}")
    private String chartModel;

    /**
     * Supervisor LLM 实例
     * 负责分析用户请求，选择 Sub-agent
     * 使用较低温度（0.0），确保决策的确定性
     */
    @Bean("supervisorChatModel")
    public ChatLanguageModel supervisorChatModel() {
        return OpenAiChatModel.builder()
                .apiKey(apiKey)
                .modelName(supervisorModel)
                .temperature(0.0)
                .timeout(Duration.ofSeconds(60))
                .build();
    }

    /**
     * Skills Agent LLM 实例
     * 负责执行具体技能
     * 使用稍高温度（0.1），允许一定的创造性
     */
    @Bean("skillsChatModel")
    public ChatLanguageModel skillsChatModel() {
        return OpenAiChatModel.builder()
                .apiKey(apiKey)
                .modelName(skillsModel)
                .temperature(0.1)
                .timeout(Duration.ofSeconds(30))
                .build();
    }

    /**
     * WebSearch Agent LLM 实例
     * 负责搜索和摘要
     * 使用低温度（0.0），确保搜索结果准确
     */
    @Bean("webSearchChatModel")
    public ChatLanguageModel webSearchChatModel() {
        return OpenAiChatModel.builder()
                .apiKey(apiKey)
                .modelName(webSearchModel)
                .temperature(0.0)
                .timeout(Duration.ofSeconds(30))
                .build();
    }

    /**
     * SQL Agent LLM 实例
     * 负责将自然语言转为 SQL
     * 使用低温度（0.0），确保 SQL 生成的准确性
     */
    @Bean("sqlChatModel")
    public ChatLanguageModel sqlChatModel() {
        return OpenAiChatModel.builder()
                .apiKey(apiKey)
                .modelName(sqlModel)
                .temperature(0.0)
                .timeout(Duration.ofSeconds(30))
                .build();
    }

    /**
     * Chart Agent LLM 实例
     * 负责生成图表配置
     * 使用稍高温度（0.2），允许多样化的图表设计
     */
    @Bean("chartChatModel")
    public ChatLanguageModel chartChatModel() {
        return OpenAiChatModel.builder()
                .apiKey(apiKey)
                .modelName(chartModel)
                .temperature(0.2)
                .timeout(Duration.ofSeconds(30))
                .build();
    }
}
```

#### 3.4.5 控制器 —— 暴露 REST API

```java
package com.agentdemo.controller;

import com.agentdemo.supervisor.SupervisorAssistant;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * 智能体控制器 —— 提供多智能体协作的 REST API
 *
 * 端点说明：
 * - POST /api/agent/chat：向 Supervisor 发送消息，自动分派到合适的 Sub-agent
 */
@RestController
@RequestMapping("/api/agent")
public class AgentController {

    private static final Logger log = LoggerFactory.getLogger(AgentController.class);

    @Resource
    private ChatLanguageModel supervisorChatModel;

    /**
     * 与智能体对话
     *
     * 流程：
     * 1. 接收用户消息
     * 2. 创建 SupervisorAssistant 实例（绑定 SupervisorAgent 的 @Tool 方法）
     * 3. LLM 分析用户请求，选择调用哪个 @Tool 方法
     * 4. 对应的 Sub-agent 执行任务
     * 5. 返回执行结果
     *
     * @param message 用户消息
     * @return 智能体响应
     */
    @PostMapping("/chat")
    public String chat(@RequestParam("message") String message) {
        log.info("收到用户消息：{}", message);

        // 创建 SupervisorAssistant 实例
        // 绑定 SupervisorAgent 的 @Tool 方法作为可用工具
        SupervisorAssistant assistant = dev.langchain4j.service.AiServices.builder(SupervisorAssistant.class)
                .chatLanguageModel(supervisorChatModel)          // 使用独立的 LLM 实例
                .tools(new SupervisorAgent())                    // 注册 Supervisor 的工具
                .build();

        // 发送消息给 Supervisor
        // LLM 内部会分析消息，决定是否调用工具
        String response = assistant.chat(message);

        log.info("智能体响应：{}", response);
        return response;
    }
}
```

---

## 四、运行验证

### 4.1 配置 API Key

```bash
# Windows PowerShell
$env:OPENAI_API_KEY="your-api-key-here"
```

### 4.2 启动应用

```bash
# 使用 Maven 编译并启动
mvn spring-boot:run

# 看到以下日志表示启动成功：
# 2026-08-22T10:00:00.000+08:00  INFO 12345 --- [agent-demo] [main] c.a.AgentDemoApplication: Started AgentDemoApplication in 3.5 seconds
```

### 4.3 测试智能体对话

```bash
# 测试联网搜索
curl -X POST http://localhost:8080/api/agent/chat \
  -d "message=查询今天北京的天气"

# 期望输出（Supervisor 分析后调用 WebSearchAgent）：
# 搜索结果：【模拟搜索结果】关于"今天北京天气"的搜索结果：
# 1. 今天北京天气相关介绍
# 2. 今天北京天气最佳实践指南
# 3. 今天北京天气常见问题解答

# 测试数据库查询
curl -X POST http://localhost:8080/api/agent/chat \
  -d "message=查询上个月的销售数据"

# 期望输出（Supervisor 分析后调用 SqlAgent）：
# 查询结果：【模拟查询结果】根据"上个月的销售数据"查询到：
# 总记录数：100 条
# 示例数据：{id: 1, name: "示例"}

# 测试图表生成
curl -X POST http://localhost:8080/api/agent/chat \
  -d "message=生成一个柱状图展示各月销售额"

# 期望输出（Supervisor 分析后调用 ChartAgent）：
# 图表已生成：【模拟图表】类型：bar，配置：各月销售额，图表已生成并返回前端渲染。

# 测试技能执行
curl -X POST http://localhost:8080/api/agent/chat \
  -d "message=生成一份本周工作总结报告"

# 期望输出（Supervisor 分析后调用 SkillsAgent）：
# 报告生成完成，包含 本周工作总结 相关数据。
```

### 4.4 验证异常隔离

```bash
# 测试异常情况：传入非法参数
curl -X POST http://localhost:8080/api/agent/chat \
  -d "message=@#$% 无效输入"

# 期望输出：Supervisor 返回友好的提示信息，不会崩溃
```

---

## 五、项目对照：对应 ruoyi-ai 项目中的真实代码位置

### 5.1 核心文件对照表

| 本示例中的类 | ruoyi-ai 中的对应类 | 所在模块 | 核心差异 |
|-------------|-------------------|---------|---------|
| `SupervisorAgent` | `SupervisorAgent` | `ruoyi-chat/agent/supervisor/` | ruoyi-ai 使用 @Retryable 重试机制 |
| `SupervisorAssistant` | `SupervisorAssistant` | `ruoyi-chat/agent/supervisor/` | System Prompt 内容更详细，规则更严格 |
| `SkillsAgent` | `SkillsAgent` | `ruoyi-chat/agent/subagent/` | ruoyi-ai 集成 MCP 工具管理 |
| `WebSearchAgent` | `WebSearchAgent` | `ruoyi-chat/agent/subagent/` | 实际搜索 API 调用 |
| `SqlAgent` | `SqlAgent` | `ruoyi-chat/agent/subagent/` | 真实 SQL 生成 + 执行 |
| `ChartAgent` | `ChartAgent` | `ruoyi-chat/agent/subagent/` | 真实 ECharts 配置生成 |
| `AgentConfig` | `AgentConfig` | `ruoyi-chat/config/` | 配置项更丰富 |

### 5.2 ruoyi-ai 中的进阶实现

ruoyi-ai 的 Supervisor Agent 在本文示例的基础上增加了以下进阶特性：

**1. 重试与降级机制**

```java
// ruoyi-ai 中使用 Spring Retry 实现自动重试
@Service
public class SupervisorAgent {

    @Retryable(
        value = {Exception.class},       // 捕获所有异常
        maxAttempts = 3,                  // 最多重试 3 次
        backoff = @Backoff(delay = 1000)  // 重试间隔 1 秒
    )
    @Tool("调用技能执行智能体，执行预定义的业务技能")
    public String callSkillsAgent(String skillName, String params) {
        // 执行技能逻辑
    }

    @Recover  // 所有重试都失败后调用此方法
    public String recover(Exception e, String skillName, String params) {
        // 返回友好的降级消息
        return "技能 [" + skillName + "] 执行失败，已自动降级处理。";
    }
}
```

**2. 超时控制**

```java
// ruoyi-ai 中使用 CompletableFuture 实现超时控制
// 每个 Sub-agent 调用都有独立的超时时间
@Service
public class SupervisorAgent {

    @Tool("调用联网搜索智能体")
    public String callWebSearchAgent(String query) {
        try {
            // 使用 CompletableFuture 实现超时控制
            // 30 秒内未返回结果，自动超时
            return CompletableFuture.supplyAsync(() -> {
                return webSearchAgent.search(query);
            }).orTimeout(30, TimeUnit.SECONDS).join();
        } catch (TimeoutException e) {
            return "联网搜索超时，请稍后重试。";
        }
    }
}
```

**3. 舱壁隔离（Bulkhead）**

```java
// ruoyi-ai 中为每个 Sub-agent 分配独立的线程池
// 一个 Sub-agent 的线程耗尽不会影响其他 Sub-agent

@Configuration
public class ThreadPoolConfig {

    // 每个 Sub-agent 有独立的线程池，实现舱壁隔离
    @Bean("skillsExecutor")
    public ExecutorService skillsExecutor() {
        return Executors.newFixedThreadPool(2);  // 最多 2 个并发
    }

    @Bean("webSearchExecutor")
    public ExecutorService webSearchExecutor() {
        return Executors.newFixedThreadPool(3);  // 最多 3 个并发
    }

    @Bean("sqlExecutor")
    public ExecutorService sqlExecutor() {
        return Executors.newFixedThreadPool(2);  // 最多 2 个并发
    }

    @Bean("chartExecutor")
    public ExecutorService chartExecutor() {
        return Executors.newFixedThreadPool(2);  // 最多 2 个并发
    }
}
```

**4. 熔断器模式（Circuit Breaker）**

```java
// ruoyi-ai 中使用 Resilience4j 实现熔断器
// 连续失败达到阈值后，暂时切断调用，避免级联故障

// 熔断器配置：
// - 滑动窗口大小：10 次调用
// - 失败率阈值：50%（10 次中 5 次失败则熔断）
// - 熔断持续时间：30 秒
// - 半开状态测试：3 次调用
```

### 5.3 从示例到项目的进阶之路

| 维度 | 本文示例 | ruoyi-ai 项目 |
|------|---------|--------------|
| **重试机制** | 无 | @Retryable + @Recover 自动重试与降级 |
| **超时控制** | 无 | CompletableFuture.orTimeout() 精确超时 |
| **舱壁隔离** | 无 | 每个 Sub-agent 独立线程池 |
| **熔断器** | 无 | Resilience4j Circuit Breaker |
| **MCP 集成** | 无 | SkillsAgent 集成 MCP 工具调用 |
| **LLM 实例** | 独立实例 | 独立实例 + 独立超时 + 独立模型配置 |
| **监控** | 无 | 调用日志 + 性能指标 + 异常告警 |

---

## 六、面试实战：3道面试题 + 回答框架

### Q1：Supervisor Agent 模式解决了什么问题？和单智能体相比有哪些优势？

**考察点：** 面试官想考察候选人对多智能体架构设计理念的理解，以及是否能清晰阐述单智能体 vs 多智能体的取舍。

**回答框架：**

- **背景**：单智能体模式将所有工具注册到同一个 LLM 实例中，随着工具数量增多，会出现"工具爆炸"问题——LLM 在几十个工具中做出正确选择的难度指数级增长。

- **单智能体的痛点**：
  1. **工具选择困难**：工具越多，LLM 选错工具的概率越大。研究表明，当工具超过 10 个时，LLM 的准确率开始显著下降。
  2. **上下文污染**：不同任务的指令和上下文混在一起，互相干扰。例如，SQL 查询的指令可能干扰搜索结果的判断。
  3. **故障扩散**：一个工具调用失败可能影响整个智能体的状态，导致后续任务全部失败。
  4. **资源争抢**：所有任务共享同一个 LLM 实例，一个耗时的 LLM 调用会阻塞其他任务。

- **Supervisor Agent 的优势**：
  1. **职责分离**：每个 Sub-agent 只负责一个领域，工具数量少，LLM 选择准确率高
  2. **上下文隔离**：每个 Sub-agent 有自己的 System Prompt，不会互相干扰
  3. **故障隔离**：一个 Sub-agent 失败不影响其他 Sub-agent，Supervisor 可以切换或降级
  4. **资源隔离**：每个 Sub-agent 有独立的 LLM 实例和线程池，资源互不竞争
  5. **可扩展性**：新增一个 Sub-agent 不影响现有系统，只需在 Supervisor 中添加一个 @Tool 方法

- **适用场景**：当一个系统需要处理多个不同类型的任务（如搜索 + 查询 + 生成 + 分析），且每种任务需要不同的工具和指令时，Supervisor Agent 模式是最佳选择。

- **注意**：不是所有场景都需要 Supervisor Agent。如果系统只有 2-3 个简单工具，单智能体更简单，无需引入多智能体的复杂度。

### Q2：双层 Tool Calling 机制是如何实现的？有什么好处？

**考察点：** 面试官想考察候选人对 Supervisor Agent 核心机制的理解深度，以及是否能说清楚"为什么需要两层"。

**回答框架：**

- **定义**：双层 Tool Calling 是 Supervisor Agent 的核心机制，分为两层：

  - **第一层（Supervisor 层）**：Supervisor 的 @Tool 方法对应的是"调用 Sub-agent"这一操作。例如 `callSkillsAgent()`、`callWebSearchAgent()`、`callSqlAgent()`、`callChartAgent()`。Supervisor 的 LLM 通过分析用户请求，决定调用哪个 @Tool 方法。
  - **第二层（Sub-agent 层）**：每个 Sub-agent 内部有自己的 @Tool 方法，对应具体的业务操作。例如 SkillsAgent 内部有 `executeMcpTool()` 方法，WebSearchAgent 内部有 `callSearchApi()` 方法。

- **实现方式**：
  ```java
  // 第一层：Supervisor 的 @Tool
  @Tool("调用联网搜索智能体")
  public String callWebSearchAgent(String query) {
      return webSearchAgent.search(query);
  }

  // 第二层：Sub-agent 内部
  public class WebSearchAgent {
      @Tool("执行搜索")
      public String search(String query) {
          return searchService.search(query);
      }
  }
  ```

- **好处**：
  1. **降低决策复杂度**：Supervisor 只需要从 4 个工具中选择，而不是从几十个工具中选择，准确率大幅提升
  2. **解耦**：Supervisor 不需要知道 Sub-agent 内部的实现细节，只需要知道"这个 Sub-agent 能做什么"
  3. **独立迭代**：可以独立修改 Sub-agent 内部的工具集，不影响 Supervisor 的决策逻辑
  4. **粒度控制**：可以在不同层级设置不同的安全策略和异常处理逻辑

- **与单层对比**：单层设计中，所有工具（搜索 + SQL + 图表 + 技能）都在同一个 @Tool 集合中，LLM 要从几十个工具中选择。双层设计中，每个 Sub-agent 只暴露 1 个入口给 Supervisor，Sub-agent 内部再管理自己的工具集，决策路径更清晰。

### Q3：如何保证多智能体系统的稳定性？有哪些容错策略？

**考察点：** 面试官想考察候选人在生产环境中保障系统稳定性的工程能力，以及对容错设计模式的掌握程度。

**回答框架：**

- **背景**：多智能体系统涉及多个 LLM 调用和多个服务调用，任何一个环节失败都可能导致整个请求失败。因此，容错设计是多智能体系统的关键组成部分。

- **容错策略**：

  1. **重试（Retry）**：对于临时性故障（如网络波动、API 限流），自动重试可以有效恢复。使用 Spring Retry 的 @Retryable 注解，设置重试次数和退避策略。注意：重试只适用于幂等操作，非幂等操作重复执行可能导致数据不一致。

  2. **降级（Fallback）**：当重试耗尽后，返回友好的降级消息，而不是抛出异常让用户看到 500 错误。例如 "联网搜索暂时不可用，请稍后重试"。降级消息应该包含足够的信息，让用户知道发生了什么，以及可以做什么。

  3. **超时控制（Timeout）**：每个 Sub-agent 调用必须有超时限制。使用 CompletableFuture.orTimeout() 实现，超时后自动中断并返回降级消息。超时时间根据 Sub-agent 的特性设置：搜索 30 秒，SQL 查询 15 秒，图表生成 20 秒。

  4. **舱壁隔离（Bulkhead）**：每个 Sub-agent 使用独立的线程池。一个 Sub-agent 的线程耗尽不会影响其他 Sub-agent。使用 Java 的 Executors.newFixedThreadPool() 为每个 Sub-agent 创建独立的线程池。

  5. **熔断器（Circuit Breaker）**：当某个 Sub-agent 连续失败达到阈值时，熔断器打开，后续请求直接返回降级消息，不再尝试调用。熔断器有三种状态：Closed（正常）、Open（熔断）、Half-Open（半开测试）。使用 Resilience4j 实现。

- **优先级**：重试 -> 超时 -> 降级 -> 舱壁 -> 熔断。这是一个层层递进的容错体系，从轻量级的恢复措施到重量级的保护措施逐层升级。

- **深度（项目经验）**：在 ruoyi-ai 中，我们为每个 Sub-agent 配置了不同的超时时间（SkillsAgent 30 秒、WebSearchAgent 30 秒、SqlAgent 15 秒、ChartAgent 20 秒），并根据历史调用数据动态调整熔断器的阈值。同时，所有异常都会被记录到日志系统，用于后续分析和优化。

---

## 七、总结

本文从零搭建了一个完整的 Supervisor Agent 多智能体协作系统，涵盖了"用户请求 -> Supervisor 分析 -> 分派 Sub-agent -> 执行任务 -> 返回结果"的完整流程。通过这个最小可行示例，我们学习了以下核心知识点：

1. **Supervisor Agent 架构设计**：一个 Supervisor 负责调度决策，多个 Sub-agent 各自负责一个特定领域，各司其职
2. **双层 Tool Calling 机制**：Supervisor 层选择 Sub-agent，Sub-agent 层执行具体工具，两层解耦，降低决策复杂度
3. **独立 LLM 实例管理**：每个 Sub-agent 拥有独立的 LLM 实例，实现资源隔离和上下文隔离
4. **异常隔离与容错**：通过重试、超时、降级、舱壁隔离、熔断器等多层机制保障系统稳定性
5. **项目对照**：理解了 ruoyi-ai 项目中 Supervisor Agent 的真实实现，以及从示例到生产环境的进阶路径

在下一篇文章中，我们将深入分析 ruoyi-ai 的 MCP 协议实现，学习如何通过 MCP 协议扩展智能体的能力边界。

---

## 参考资料

- [LangChain4j AI Services 文档](https://docs.langchain4j.dev/tutorials/ai-services) — 使用 @AiService 创建智能体
- [LangChain4j Tools 文档](https://docs.langchain4j.dev/tutorials/tools) — 智能体工具调用机制
- [Spring Retry 官方文档](https://docs.spring.io/spring-retry/docs/current/reference/) — 重试与降级机制
- [Resilience4j 官方文档](https://resilience4j.readme.io/docs/circuitbreaker) — 熔断器模式实现
- [Java CompletableFuture 官方文档](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/CompletableFuture.html) — 异步编程与超时控制
- [ruoyi-ai GitHub 仓库](https://github.com/1byteone/ruoyi-ai) — 项目源码，查看完整的 Supervisor Agent 实现