# 流程编排入门：从零认识 langgraph4j 状态图引擎

> 本文是 ruoyi-ai 项目技术栈深度剖析系列的第4篇，面向 Java 后端开发者，旨在帮助读者理解状态图（StateGraph）编排引擎的核心概念，从零搭建一个基于 langgraph4j 的最简工作流，并支持 SSE 流式执行，为后续学习复杂 AI 流程编排打下基础。

---

## 一、项目背景：该技术栈在项目中的角色

### 1.1 为什么需要流程编排引擎

在传统的软件开发中，我们习惯用"顺序执行"来思考问题：A 方法调用 B 方法，B 方法调用 C 方法，一切按代码顺序进行。但 AI 应用的流程编排远比这复杂：

- **用户输入** → 先判断意图（分类）→ 根据分类结果走不同分支
- **知识问答** → 需要检索知识库 → 将检索结果注入 Prompt → 调用 LLM 生成回答
- **图片生成** → 需要调用图片生成 API → 生成后可能需要人工审核 → 审核通过后发送通知

如果把这些逻辑全部写在 Java 代码里，会变成一团乱麻：

```java
// 没有流程引擎的"面条式"代码
public String process(String input) {
    String intent = classify(input);           // 意图分类
    if ("knowledge".equals(intent)) {
        String docs = retrieve(input);         // 知识检索
        return llmAnswer(input, docs);         // LLM 回答
    } else if ("image".equals(intent)) {
        String url = generateImage(input);     // 图片生成
        String feedback = waitForReview(url);  // 人工审核（阻塞等待）
        if ("approved".equals(feedback)) {
            sendNotification(url);             // 发送通知
        }
        return url;
    }
    // ... 更多分支
}
```

这种代码的问题在于：
1. **分支逻辑和业务逻辑耦合**：流程走向内嵌在 if-else 中，不易修改
2. **难以可视化**：无法直观地看到流程的全貌
3. **难以扩展**：新增一个分支需要修改核心代码
4. **难以支持人机协作**：人工审核需要阻塞等待，实现复杂

流程编排引擎（如 langgraph4j）的核心价值就是**将"流程"和"节点"解耦**：节点只负责业务逻辑，流程由"图"定义，通过声明式 API 描述谁先谁后、什么条件走什么分支。

### 1.2 在 ruoyi-ai 项目中的位置

在 ruoyi-ai 中，langgraph4j 是 `ruoyi-aiflow` 模块的核心引擎：

```
用户请求
    ↓
[HTTP 接口] 接收请求
    ↓
[StateGraph 编译图] 加载流程定义
    ↓
[流式执行] 逐节点执行，SSE 推送状态
    ↓
[节点 1] Classifier → 意图分类
    ↓
[节点 2] Switcher → 条件路由
    ├── 知识问答 → KnowledgeRetrieval → LLMAnswer → END
    ├── 图片生成 → Image → END
    └── 人工审核 → HumanFeedback → MailSend → END
    ↓
[SSE 响应] 实时推送执行结果
```

ruoyi-aiflow 模块定义了 11 种标准节点类型（LLMAnswer、Classifier、KnowledgeRetrieval、Switcher、HumanFeedback 等），通过 StateGraph 将这些节点编排为可执行的 AI 工作流。

### 1.3 本文目标

本文的目标是帮助读者：

1. 理解 StateGraph（状态图）的核心概念：节点和边
2. 掌握 langgraph4j 的基本用法
3. 从零搭建一个最简的 2 节点工作流（Start → LLMAnswer → End）
4. 实现 SSE 流式执行，实时查看节点状态
5. 为后续学习复杂工作流（条件分支、人机协作）打下基础

---

## 二、核心概念：2-3个，用生活类比解释

### 概念 1：StateGraph（状态图）—— 就像"工厂流水线的设计图"

**生活类比**：想象你要设计一条手机组装流水线。你不会直接把所有工序写在一段代码里，而是先画一张"设计图"：

```
[上料] → [贴片] → [焊接] → [质检] → [包装]
```

这张设计图描述了：
- 有哪些工位（节点）
- 工位之间的顺序（边）
- 质检不合格时走"返修"分支（条件边）

**技术映射**：StateGraph 就是这张"设计图"——它定义了工作流中所有节点以及它们之间的连接关系。调用 `compile()` 编译后，就变成了"可执行的流水线"（CompiledGraph）。

```java
// 创建 StateGraph（设计图）
StateGraph<AgentState> graph = new StateGraph<>(SCHEMA, AgentState::new);

// 注册节点（工位）
graph.addNode("classifier", new ClassifierNode());
graph.addNode("llmAnswer", new LLMAnswerNode());

// 定义边（流水线顺序）
graph.addEdge(START, "classifier");
graph.addEdge("classifier", "llmAnswer");
graph.addEdge("llmAnswer", END);

// 编译（设计图定稿）
CompiledGraph<AgentState> compiledGraph = graph.compile();
```

**关键点**：
- 节点 = 工位上的操作（业务逻辑）
- 边 = 工位之间的传送带（执行顺序）
- 编译 = 设计图定稿，形成可执行流水线
- 状态 = 流水线上传递的"工件"（数据）

### 概念 2：节点（Node）和边（Edge）—— 就像"员工和传送带"

**生活类比**：在工厂流水线上：

- **节点（Node）** = 每个工位上的员工，负责完成一道工序
  - 质检员负责检查质量
  - 焊接工负责焊接
  - 包装工负责打包
  - 每个员工只做自己的事，不关心上下游

- **普通边（Edge）** = 工位之间的传送带，工件加工完自动流到下一站
  - 质检完 → 自动传送到包装工位

- **条件边（ConditionalEdge）** = 质检员的分拣滑道
  - 合格品 → 传送到包装工位
  - 不合格品 → 传送到返修工位

**技术映射**：

```java
// 节点：实现 NodeAction 接口
// 员工的工作内容：接收状态（工件），返回更新后的状态
public class MyNode implements NodeAction<AgentState> {
    @Override
    public Map<String, Object> apply(AgentState state) {
        // 读取状态中的数据
        String input = (String) state.value("input").orElse("");
        // 执行业务逻辑
        String output = doSomething(input);
        // 返回状态更新
        return Map.of("output", output);
    }
}

// 普通边：无条件流转
graph.addEdge("nodeA", "nodeB");  // A 执行完 → 自动到 B

// 条件边：根据状态动态选择
graph.addConditionalEdges("nodeA", state -> {
    String result = (String) state.value("result").orElse("");
    return result.equals("ok") ? "success" : "fail";
}, Map.of(
    "success", "nodeB",  // 合格 → 走 nodeB
    "fail", "nodeC"      // 不合格 → 走 nodeC
));
```

**关键点**：
- 节点实现 `NodeAction` 接口，接收状态、返回状态增量
- 普通边用 `addEdge()`，条件边用 `addConditionalEdges()`
- 条件边通过 `EdgeAction` 函数动态决定下一个节点
- 节点之间不直接调用，全部通过图引擎调度

### 概念 3：AgentState（状态）—— 就像"流水线上的工件"

**生活类比**：在手机组装流水线上，每个手机（工件）都有一张"工单卡"，记录了：

- 当前工序（已贴片、已焊接）
- 加工参数（焊接温度、时间）
- 质检结果（合格/不合格）
- 特殊要求（需要贴膜）

每个工位上的员工：
1. 读取工单卡（获取当前状态）
2. 执行自己的工序（加工）
3. 更新工单卡（写入新状态）
4. 放上传送带到下一站（交给下一个节点）

**技术映射**：`AgentState` 就是"工单卡"——它是一个 `Map<String, Object>` 的包装，所有节点共享这个状态来传递数据：

```java
// 状态定义：继承 AgentState
public class AiFlowState extends AgentState {
    // 常量定义（避免字符串散落在代码中）
    public static final String INPUT = "input";
    public static final String OUTPUT = "output";
    public static final String CLASSIFY_RESULT = "classifyResult";

    public AiFlowState(Map<String, Object> initData) {
        super(initData);
    }
}

// 节点 A：写入状态
public class ClassifierNode implements NodeAction<AiFlowState> {
    @Override
    public Map<String, Object> apply(AiFlowState state) {
        String input = (String) state.value(INPUT).orElse("");
        String classifyResult = classify(input);  // 意图分类
        return Map.of(CLASSIFY_RESULT, classifyResult);  // 写入状态
    }
}

// 节点 B：读取状态（节点 A 写入的数据）
public class LLMAnswerNode implements NodeAction<AiFlowState> {
    @Override
    public Map<String, Object> apply(AiFlowState state) {
        String classifyResult = (String) state.value(CLASSIFY_RESULT).orElse("");
        // 根据分类结果执行不同逻辑
        return Map.of(OUTPUT, generateAnswer(classifyResult));
    }
}
```

**关键点**：
- 状态是节点的"共享黑板"——所有节点通过读写状态来协作
- 每个节点返回"状态增量"（Map），引擎自动合并到全局状态
- 使用常量定义状态键名，避免硬编码字符串
- 状态可以持久化（Checkpoint），支持断点续传

---

## 三、从零搭建：完整代码

### 3.1 项目结构

```
hello-langgraph/
├── pom.xml
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/
│   │   │       └── hellograph/
│   │   │           ├── HelloGraphApplication.java        # 启动类
│   │   │           ├── state/
│   │   │           │   └── SimpleFlowState.java          # 状态定义
│   │   │           ├── node/
│   │   │           │   └── LLMAnswerNode.java            # LLM 回答节点
│   │   │           ├── graph/
│   │   │           │   └── SimpleFlowGraph.java          # 图定义和编译
│   │   │           ├── service/
│   │   │           │   └── FlowExecutionService.java     # 流程执行服务
│   │   │           └── controller/
│   │   │               └── FlowController.java           # REST 控制器
│   │   └── resources/
│   │       └── application.yml
│   └── test/
│       └── java/
│           └── com/
│               └── hellograph/
│                   └── graph/
│                       └── SimpleFlowGraphTest.java
```

### 3.2 pom.xml

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.hellograph</groupId>
    <artifactId>hello-langgraph</artifactId>
    <version>1.0.0</version>
    <name>hello-langgraph</name>
    <description>langgraph4j 入门示例：最简 2 节点状态图工作流</description>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.5.8</version>
        <relativePath/>
    </parent>

    <properties>
        <java.version>17</java.version>
        <langchain4j.version>1.13.0</langchain4j.version>
        <langgraph4j.version>1.5.3</langgraph4j.version>
    </properties>

    <dependencies>
        <!-- Spring Boot Web -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>

        <!-- LangChain4j Spring Boot Starter（用于 LLM 调用） -->
        <dependency>
            <groupId>dev.langchain4j</groupId>
            <artifactId>langchain4j-spring-boot-starter</artifactId>
            <version>${langchain4j.version}</version>
        </dependency>

        <!-- LangChain4j OpenAI Starter -->
        <dependency>
            <groupId>dev.langchain4j</groupId>
            <artifactId>langchain4j-open-ai-spring-boot-starter</artifactId>
            <version>${langchain4j.version}</version>
        </dependency>

        <!-- langgraph4j 核心库 -->
        <dependency>
            <groupId>org.bsc.langgraph4j</groupId>
            <artifactId>langgraph4j-core</artifactId>
            <version>${langgraph4j.version}</version>
        </dependency>

        <!-- langgraph4j Spring Boot Starter（自动配置 + 健康检查） -->
        <dependency>
            <groupId>org.bsc.langgraph4j</groupId>
            <artifactId>langgraph4j-spring-boot-starter</artifactId>
            <version>${langgraph4j.version}</version>
        </dependency>

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

### 3.3 application.yml

```yaml
# =============================================
# langgraph4j 入门示例应用配置
# =============================================
server:
  port: 8080

spring:
  application:
    name: hello-langgraph

# LangChain4j 配置（用于节点内部调用 LLM）
langchain4j:
  open-ai:
    chat-model:
      api-key: ${OPENAI_API_KEY:sk-demo}           # 替换为你的 API Key
      model-name: gpt-4o-mini                       # 或 gpt-3.5-turbo
      temperature: 0.7
      max-tokens: 500
      log-requests: true
      log-responses: true
```

### 3.4 启动类

```java
package com.hellograph;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot 应用启动类。
 */
@SpringBootApplication
public class HelloGraphApplication {

    public static void main(String[] args) {
        SpringApplication.run(HelloGraphApplication.class, args);
    }
}
```

### 3.5 状态定义

```java
package com.hellograph.state;

import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;

import java.util.Map;

/**
 * 简单流程状态类。
 *
 * 这是整个工作流中所有节点共享的"黑板"。
 * 每个节点读取状态中的数据，处理后更新状态，然后传递给下一个节点。
 *
 * 状态键名常量：
 * - INPUT: 用户输入的问题
 * - OUTPUT: LLM 生成的回答
 * - NODE_NAME: 当前执行的节点名称（用于 SSE 推送）
 * - TIMESTAMP: 执行时间戳
 */
public class SimpleFlowState extends AgentState {

    /** 用户输入的问题 */
    public static final String INPUT = "input";

    /** LLM 生成的回答 */
    public static final String OUTPUT = "output";

    /** 当前执行的节点名称 */
    public static final String NODE_NAME = "nodeName";

    /** 执行时间戳 */
    public static final String TIMESTAMP = "timestamp";

    /**
     * Schema 定义 —— 规定每个状态字段的更新策略。
     *
     * Channels.overwrite()：新值直接覆盖旧值（适合单值字段）
     * 这是默认策略，每个字段只保留最新的值。
     *
     * 在更复杂的场景中，还可以使用：
     * - Channels.appender()：追加到列表（如消息历史）
     * - Channels.identity()：只写入一次，不可修改
     */
    public static final Map<String, Channel<?>> SCHEMA = Map.of(
        INPUT,      Channels.overwrite(),
        OUTPUT,     Channels.overwrite(),
        NODE_NAME,  Channels.overwrite(),
        TIMESTAMP,  Channels.overwrite()
    );

    public SimpleFlowState(Map<String, Object> initData) {
        super(initData);
    }
}
```

### 3.6 LLM 回答节点

```java
package com.hellograph.node;

import com.hellograph.state.SimpleFlowState;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.bsc.langgraph4j.action.NodeAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * LLM 回答节点 —— 工作流中的核心处理节点。
 *
 * 职责：
 * 1. 从状态中读取用户输入 (INPUT)
 * 2. 调用 ChatLanguageModel（大模型）生成回答
 * 3. 将回答写入状态 (OUTPUT)
 *
 * 这是最简工作流中的唯一业务节点。
 * 在 ruoyi-ai 项目中，这是 11 种标准节点之一。
 *
 * NodeAction<SimpleFlowState> 接口：
 * - 泛型参数 S 表示状态类型
 * - apply(S state) 方法接收当前状态，返回状态更新 Map
 * - 返回的 Map 会被引擎自动合并到全局状态中
 */
public class LLMAnswerNode implements NodeAction<SimpleFlowState> {

    private static final Logger log = LoggerFactory.getLogger(LLMAnswerNode.class);

    /** ChatLanguageModel：LangChain4j 的统一 LLM 接口 */
    private final ChatLanguageModel model;

    /**
     * 构造器注入 LLM 模型。
     * 在实际项目中，这个模型由 Spring 容器注入，
     * 支持多厂商切换（OpenAI、DeepSeek、通义千问等）。
     */
    public LLMAnswerNode(ChatLanguageModel model) {
        this.model = model;
    }

    /**
     * 节点执行逻辑。
     *
     * 流程：
     * 1. 从状态中读取用户输入
     * 2. 调用 LLM 生成回答
     * 3. 记录日志
     * 4. 返回状态更新（包含回答、节点名称、时间戳）
     *
     * @param state 当前状态（包含 INPUT 字段）
     * @return 状态更新 Map（引擎自动合并到全局状态）
     */
    @Override
    public Map<String, Object> apply(SimpleFlowState state) {
        // 第 1 步：从状态中读取用户输入
        // state.value(key) 返回 Optional，orElse 提供默认值
        String input = (String) state.value(SimpleFlowState.INPUT).orElse("你好");

        log.info("LLMAnswerNode 开始执行，输入: {}", input);

        // 第 2 步：调用 LLM 生成回答
        // ChatLanguageModel.chat() 是同步调用
        // 实际项目中可能是异步调用或流式调用
        String answer = model.chat(input);

        log.info("LLMAnswerNode 执行完成，输出: {}", answer);

        // 第 3 步：返回状态更新
        // 引擎会将这个 Map 合并到全局状态中
        // 后续节点可以通过 state.value(OUTPUT) 读取这里写入的值
        return Map.of(
            SimpleFlowState.OUTPUT,    answer,              // LLM 的回答
            SimpleFlowState.NODE_NAME, "LLMAnswerNode",     // 当前节点名称
            SimpleFlowState.TIMESTAMP, System.currentTimeMillis()  // 执行时间
        );
    }
}
```

### 3.7 图定义和编译

```java
package com.hellograph.graph;

import com.hellograph.node.LLMAnswerNode;
import com.hellograph.state.SimpleFlowState;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.StateGraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;

/**
 * 简单流程图构建器 —— 定义最简的 2 节点工作流。
 *
 * 图结构：
 *   START → LLMAnswerNode → END
 *
 * 这是 langgraph4j 中最简单的工作流：
 * 只有一个业务节点（LLMAnswerNode），输入直接经过 LLM 处理输出。
 *
 * 构建流程：
 * 1. 创建 StateGraph 实例（传入 Schema 和构造函数）
 * 2. 注册节点（addNode）
 * 3. 定义边（addEdge）
 * 4. 编译（compile）→ 生成不可变的 CompiledGraph
 */
public class SimpleFlowGraph {

    private static final Logger log = LoggerFactory.getLogger(SimpleFlowGraph.class);

    /**
     * 构建并编译最简工作流。
     *
     * @param model ChatLanguageModel 实例，由调用方注入
     * @return 编译后的可执行图
     */
    public static CompiledGraph<SimpleFlowState> build(ChatLanguageModel model) {
        log.info("开始构建 SimpleFlowGraph...");

        // 第 1 步：创建 StateGraph 实例
        //
        // StateGraph<SimpleFlowState> 有两个参数：
        // - 第一个参数：Schema 定义（Map<String, Channel<?>>）
        //   规定每个状态字段的更新策略（覆盖/追加等）
        // - 第二个参数：状态构造函数引用（Function<Map, SimpleFlowState>）
        //   用于从初始数据创建状态实例
        StateGraph<SimpleFlowState> graph = new StateGraph<>(
            SimpleFlowState.SCHEMA,     // Schema 定义
            SimpleFlowState::new        // 构造函数引用
        );

        // 第 2 步：注册节点
        //
        // addNode(nodeName, nodeAction)
        // - nodeName: 节点名称（在边定义中引用）
        // - nodeAction: 实现了 NodeAction 接口的节点实例
        //
        // 这里的节点名称 "llmAnswer" 用来在 addEdge 中引用
        graph.addNode("llmAnswer", new LLMAnswerNode(model));

        // 第 3 步：定义边
        //
        // addEdge(fromNode, toNode)
        // - START 是 langgraph4j 内置的入口节点
        // - END 是 langgraph4j 内置的出口节点
        // - 普通边表示"无条件流转"：fromNode 执行完后自动到 toNode
        //
        // 流程：START → llmAnswer → END
        graph.addEdge(START, "llmAnswer");   // 从入口到 LLM 节点
        graph.addEdge("llmAnswer", END);     // 从 LLM 节点到出口

        // 第 4 步：编译图
        //
        // compile() 生成不可变的 CompiledGraph 实例
        // 编译后的图不能再添加节点或边
        // 如果需要不同的配置，应该重新 build
        CompiledGraph<SimpleFlowState> compiledGraph = graph.compile();

        log.info("SimpleFlowGraph 构建完成");
        log.info("图结构: START → llmAnswer → END");

        return compiledGraph;
    }
}
```

### 3.8 流程执行服务

```java
package com.hellograph.service;

import com.hellograph.graph.SimpleFlowGraph;
import com.hellograph.state.SimpleFlowState;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.RunnableConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 流程执行服务 —— 编译图、执行工作流、SSE 推送节点状态。
 *
 * 核心职责：
 * 1. 管理 CompiledGraph 实例（单例，编译一次复用）
 * 2. 执行工作流，逐节点处理
 * 3. 通过 SSE 实时推送每个节点的执行状态
 */
@Service
public class FlowExecutionService {

    private static final Logger log = LoggerFactory.getLogger(FlowExecutionService.class);

    /** 编译后的可执行图（单例，线程安全） */
    private final CompiledGraph<SimpleFlowState> compiledGraph;

    /**
     * 构造器注入 ChatLanguageModel，构建并编译图。
     *
     * 图在服务启动时编译一次，后续所有请求复用同一个编译后的图。
     * 每个请求通过不同的 threadId 隔离执行状态。
     */
    public FlowExecutionService(ChatLanguageModel model) {
        log.info("正在编译 SimpleFlowGraph...");
        this.compiledGraph = SimpleFlowGraph.build(model);
        log.info("SimpleFlowGraph 编译完成");
    }

    /**
     * 执行工作流（同步方式）。
     *
     * @param input    用户输入
     * @param threadId 线程 ID（用于隔离不同会话的执行状态）
     * @return 最终状态（包含 LLM 的回答）
     */
    public SimpleFlowState execute(String input, String threadId) {
        log.info("开始执行工作流, threadId: {}, input: {}", threadId, input);

        // 第 1 步：构建执行配置
        // threadId 用于隔离不同会话的状态
        // 同一个 threadId 的多次执行会共享状态历史
        RunnableConfig config = RunnableConfig.builder()
                .threadId(threadId)
                .build();

        // 第 2 步：构建初始输入
        // 初始状态包含用户输入
        Map<String, Object> initialInput = Map.of(
                SimpleFlowState.INPUT, input
        );

        // 第 3 步：执行工作流
        // graph.stream() 返回一个 Iterable，每执行一个节点就返回一个事件
        // 每个事件包含：节点名称 + 执行后的状态快照
        SimpleFlowState finalState = null;
        for (var event : compiledGraph.stream(initialInput, config)) {
            String nodeName = event.node();
            SimpleFlowState state = (SimpleFlowState) event.state();
            log.info("节点执行完成: {}, 状态: {}", nodeName, state);

            // 最后一个事件就是最终状态
            finalState = state;
        }

        log.info("工作流执行完成, threadId: {}", threadId);
        return finalState;
    }

    /**
     * 执行工作流（SSE 流式方式）。
     *
     * 每个节点执行完成后，通过 SSE 实时推送到前端。
     * 前端可以实时看到："正在执行 LLMAnswerNode...", "LLMAnswerNode 执行完成"
     *
     * @param input    用户输入
     * @param threadId 线程 ID
     * @param emitter  SSE 发射器（用于推送实时状态）
     */
    public void executeWithSSE(String input, String threadId, SseEmitter emitter) {
        log.info("开始 SSE 流式执行工作流, threadId: {}, input: {}", threadId, input);

        RunnableConfig config = RunnableConfig.builder()
                .threadId(threadId)
                .build();

        Map<String, Object> initialInput = Map.of(
                SimpleFlowState.INPUT, input
        );

        try {
            // 遍历每个节点的事件
            for (var event : compiledGraph.stream(initialInput, config)) {
                String nodeName = event.node();
                SimpleFlowState state = (SimpleFlowState) event.state();

                // 获取当前节点的输出（如果有）
                String output = (String) state.value(SimpleFlowState.OUTPUT).orElse("");

                // 构建 SSE 事件数据
                Map<String, Object> eventData = Map.of(
                        "node", nodeName,                    // 当前执行的节点名称
                        "output", output,                    // 节点输出内容
                        "timestamp", System.currentTimeMillis()  // 执行时间戳
                );

                // 通过 SSE 推送到前端
                // 事件名称：node-executed（前端通过此名称监听）
                emitter.send(SseEmitter.event()
                        .name("node-executed")
                        .data(eventData));

                log.info("SSE 推送节点状态: {}", nodeName);
            }

            // 所有节点执行完毕，发送完成事件
            emitter.send(SseEmitter.event()
                    .name("flow-complete")
                    .data(Map.of(
                            "status", "completed",
                            "timestamp", System.currentTimeMillis()
                    )));

            // 完成 SSE 连接
            emitter.complete();

        } catch (IOException e) {
            log.error("SSE 推送失败", e);
            emitter.completeWithError(e);
        }
    }
}
```

### 3.9 REST 控制器

```java
package com.hellograph.controller;

import com.hellograph.service.FlowExecutionService;
import com.hellograph.state.SimpleFlowState;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

/**
 * 工作流 REST 控制器。
 *
 * 提供两个接口：
 * 1. POST /api/flow/execute — 同步执行工作流
 * 2. GET /api/flow/stream — SSE 流式执行工作流
 */
@RestController
@RequestMapping("/api/flow")
public class FlowController {

    private final FlowExecutionService flowExecutionService;

    public FlowController(FlowExecutionService flowExecutionService) {
        this.flowExecutionService = flowExecutionService;
    }

    /**
     * 同步执行工作流。
     *
     * POST /api/flow/execute
     * Content-Type: application/json
     * Body: { "input": "你好，请介绍一下你自己" }
     *
     * @param request 请求体（包含 input 字段）
     * @return 最终状态（包含 LLM 的回答）
     */
    @PostMapping("/execute")
    public SimpleFlowState execute(@RequestBody FlowRequest request) {
        String threadId = UUID.randomUUID().toString();
        return flowExecutionService.execute(request.input(), threadId);
    }

    /**
     * SSE 流式执行工作流。
     *
     * GET /api/flow/stream?input=你好
     *
     * SSE 事件流格式：
     * event: node-executed
     * data: {"node":"LLMAnswerNode","output":"...","timestamp":...}
     *
     * event: flow-complete
     * data: {"status":"completed","timestamp":...}
     *
     * @param input 用户输入
     * @return SseEmitter 用于流式推送
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestParam("input") String input) {
        String threadId = UUID.randomUUID().toString();

        // 创建 SseEmitter
        // 参数：超时时间（毫秒），-1 表示不超时
        SseEmitter emitter = new SseEmitter(-1L);

        // 异步执行工作流，通过 SSE 推送节点状态
        // 这里使用新线程执行，避免阻塞 HTTP 连接
        new Thread(() -> {
            flowExecutionService.executeWithSSE(input, threadId, emitter);
        }).start();

        return emitter;
    }

    /**
     * 请求体 POJO。
     *
     * @param input 用户输入
     */
    public record FlowRequest(String input) {}
}
```

### 3.10 单元测试

```java
package com.hellograph.graph;

import com.hellograph.node.LLMAnswerNode;
import com.hellograph.state.SimpleFlowState;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.RunnableConfig;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 最简工作流测试。
 *
 * 测试点：
 * 1. 图编译是否正确
 * 2. 工作流执行是否正常
 * 3. 节点是否能正确读取和写入状态
 * 4. 最终状态是否包含 LLM 的回答
 *
 * 注意：如果使用真实的 LLM API，需要配置 API Key。
 * 如果不想消耗 API 额度，可以使用 Mock 或本地模型。
 */
@SpringBootTest
class SimpleFlowGraphTest {

    /** 编译后的图实例（在所有测试方法间共享） */
    private static CompiledGraph<SimpleFlowState> compiledGraph;

    /**
     * 在所有测试之前，编译一次图。
     *
     * 这里使用 OpenAiChatModel 作为 LLM 模型。
     * 测试时请确保环境变量 OPENAI_API_KEY 已设置。
     *
     * 如果你不想使用真实的 API，可以替换为 Mock：
     * ChatLanguageModel mockModel = mock(ChatLanguageModel.class);
     * when(mockModel.chat(anyString())).thenReturn("模拟的回答");
     */
    @BeforeAll
    static void setUp() {
        // 创建 LLM 模型（从环境变量读取 API Key）
        ChatLanguageModel model = OpenAiChatModel.builder()
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .modelName("gpt-4o-mini")
                .temperature(0.7)
                .build();

        // 编译图
        compiledGraph = SimpleFlowGraph.build(model);
    }

    /**
     * 测试图编译结果。
     *
     * 验证点：
     * 1. 编译后的图不为 null
     * 2. 图包含正确的节点
     */
    @Test
    void testGraphCompilation() {
        assertNotNull(compiledGraph, "编译后的图不应为 null");
    }

    /**
     * 测试工作流执行。
     *
     * 验证点：
     * 1. 执行完成后，状态中包含 OUTPUT
     * 2. OUTPUT 不为空字符串
     * 3. OUTPUT 是对用户输入的合理回答
     */
    @Test
    void testFlowExecution() {
        // 准备测试数据
        String input = "你好，请用一句话介绍 Java 编程语言";
        String threadId = "test-thread-001";

        // 构建执行配置
        RunnableConfig config = RunnableConfig.builder()
                .threadId(threadId)
                .build();

        // 构建初始输入
        Map<String, Object> initialInput = Map.of(
                SimpleFlowState.INPUT, input
        );

        // 执行工作流
        // 遍历所有节点事件，获取最终状态
        SimpleFlowState finalState = null;
        for (var event : compiledGraph.stream(initialInput, config)) {
            finalState = (SimpleFlowState) event.state();

            // 打印每个节点的执行状态
            String nodeName = event.node();
            System.out.println("节点执行: " + nodeName);
        }

        // 验证最终状态
        assertNotNull(finalState, "最终状态不应为 null");

        // 验证 OUTPUT 字段
        String output = (String) finalState.value(SimpleFlowState.OUTPUT).orElse("");
        assertNotNull(output, "OUTPUT 不应为 null");
        assertFalse(output.isEmpty(), "OUTPUT 不应为空");
        System.out.println("LLM 回答: " + output);

        // 验证回答的语义相关性
        // 由于 LLM 的输出不确定，这里只验证基本格式
        assertTrue(output.length() > 10, "回答长度应大于 10 个字符");
    }

    /**
     * 测试不同输入的工作流执行。
     *
     * 验证点：
     * 1. 不同输入能得到不同的回答
     * 2. 多次执行互不干扰
     */
    @Test
    void testMultipleExecutions() {
        // 第一次执行
        String input1 = "请用一句话介绍 Java";
        String threadId1 = "test-thread-002";

        RunnableConfig config1 = RunnableConfig.builder()
                .threadId(threadId1)
                .build();

        SimpleFlowState finalState1 = null;
        for (var event : compiledGraph.stream(
                Map.of(SimpleFlowState.INPUT, input1), config1)) {
            finalState1 = (SimpleFlowState) event.state();
        }

        String output1 = (String) finalState1.value(SimpleFlowState.OUTPUT).orElse("");

        // 第二次执行（不同输入）
        String input2 = "请用一句话介绍 Python";
        String threadId2 = "test-thread-003";

        RunnableConfig config2 = RunnableConfig.builder()
                .threadId(threadId2)
                .build();

        SimpleFlowState finalState2 = null;
        for (var event : compiledGraph.stream(
                Map.of(SimpleFlowState.INPUT, input2), config2)) {
            finalState2 = (SimpleFlowState) event.state();
        }

        String output2 = (String) finalState2.value(SimpleFlowState.OUTPUT).orElse("");

        // 验证：不同输入得到不同的回答
        assertNotNull(output1);
        assertNotNull(output2);
        assertFalse(output1.isEmpty());
        assertFalse(output2.isEmpty());

        // 两个回答应该不同（因为输入不同）
        // 注意：LLM 的回答可能偶尔相同，但大概率不同
        System.out.println("输入1回答: " + output1);
        System.out.println("输入2回答: " + output2);
    }

    /**
     * 测试节点状态传递。
     *
     * 验证点：
     * 1. LLMAnswerNode 能正确读取 INPUT
     * 2. LLMAnswerNode 能正确写入 OUTPUT
     * 3. OUTPUT 的内容与 INPUT 相关
     */
    @Test
    void testNodeStateTransfer() {
        // 直接测试 LLMAnswerNode
        ChatLanguageModel model = OpenAiChatModel.builder()
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .modelName("gpt-4o-mini")
                .temperature(0.7)
                .build();

        LLMAnswerNode node = new LLMAnswerNode(model);

        // 创建模拟状态
        SimpleFlowState state = new SimpleFlowState(
                Map.of(SimpleFlowState.INPUT, "什么是线程池？")
        );

        // 执行节点
        Map<String, Object> stateUpdate = node.apply(state);

        // 验证节点返回了正确的状态更新
        assertNotNull(stateUpdate);
        assertTrue(stateUpdate.containsKey(SimpleFlowState.OUTPUT));
        assertTrue(stateUpdate.containsKey(SimpleFlowState.NODE_NAME));

        String output = (String) stateUpdate.get(SimpleFlowState.OUTPUT);
        assertNotNull(output);
        assertFalse(output.isEmpty());

        String nodeName = (String) stateUpdate.get(SimpleFlowState.NODE_NAME);
        assertEquals("LLMAnswerNode", nodeName);
    }
}
```

---

## 四、运行验证

### 4.1 启动应用

```bash
# 设置 OpenAI API Key（如果使用真实模型）
export OPENAI_API_KEY=sk-your-api-key-here

# 使用 Maven 编译并启动
mvn spring-boot:run

# 看到以下日志表示启动成功：
# 正在编译 SimpleFlowGraph...
# SimpleFlowGraph 构建完成
# 图结构: START → llmAnswer → END
# Tomcat started on port 8080
```

### 4.2 测试同步执行

```bash
# 同步执行工作流
curl -X POST "http://localhost:8080/api/flow/execute" \
  -H "Content-Type: application/json" \
  -d '{"input": "你好，请用一句话介绍 Spring Boot"}'

# 期望输出（示例）：
# {
#   "input": "你好，请用一句话介绍 Spring Boot",
#   "output": "Spring Boot 是一个基于 Spring 框架的快速开发框架...",
#   "nodeName": "LLMAnswerNode",
#   "timestamp": 1693123456789
# }
```

### 4.3 测试 SSE 流式执行

```bash
# SSE 流式执行工作流
curl -N "http://localhost:8080/api/flow/stream?input=Java%E7%BA%BF%E7%A8%8B%E6%B1%A0%E7%9A%84%E4%BD%9C%E7%94%A8"

# 期望输出（SSE 事件流）：
# event: node-executed
# data: {"node":"LLMAnswerNode","output":"","timestamp":1693123456789}
#
# event: node-executed
# data: {"node":"LLMAnswerNode","output":"Java 线程池用于管理和复用线程...","timestamp":1693123456790}
#
# event: flow-complete
# data: {"status":"completed","timestamp":1693123456791}
```

### 4.4 运行单元测试

```bash
# 运行所有测试
mvn test

# 期望输出：
# [INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0
```

### 4.5 验证流程执行

通过日志观察工作流的执行过程：

```
开始执行工作流, threadId: xxx, input: 你好
LLMAnswerNode 开始执行，输入: 你好
LLMAnswerNode 执行完成，输出: 你好！我是 AI 助手...
节点执行完成: LLMAnswerNode, 状态: SimpleFlowState{...}
工作流执行完成, threadId: xxx
```

执行流程一目了然：
1. 引擎收到用户输入
2. 引擎调度 LLMAnswerNode 执行
3. LLMAnswerNode 读取 INPUT，调用 LLM，写入 OUTPUT
4. 引擎检查是否有下一个节点（没有，到达 END）
5. 返回最终状态

---

## 五、项目对照：对应 ruoyi-ai 项目中的真实代码位置

### 5.1 核心文件对照表

| 本文示例 | ruoyi-ai 项目位置 | 说明 |
|---------|-------------------|------|
| `SimpleFlowState.java` | `ruoyi-ai/ruoyi-aiflow/src/main/java/.../state/AiFlowState.java` | 状态定义（含 Schema） |
| `LLMAnswerNode.java` | `ruoyi-ai/ruoyi-aiflow/src/main/java/.../node/LLMAnswerNode.java` | LLM 回答节点 |
| `SimpleFlowGraph.java` | `ruoyi-ai/ruoyi-aiflow/src/main/java/.../graph/AiFlowGraphBuilder.java` | 图构建器 |
| `FlowExecutionService.java` | `ruoyi-ai/ruoyi-aiflow/src/main/java/.../service/AiFlowExecutionService.java` | 流程执行服务 |
| `FlowController.java` | `ruoyi-ai/ruoyi-aiflow/src/main/java/.../controller/AiFlowController.java` | 流程控制器 |

### 5.2 ruoyi-ai 中的实际增强

ruoyi-ai 项目中，langgraph4j 流程编排模块做了以下增强：

1. **11 种标准节点类型**：除了 LLMAnswerNode，还有 ClassifierNode、KeywordExtractorNode、KnowledgeRetrievalNode、SwitcherNode、HttpRequestNode、ImageNode、MailSendNode、HumanFeedbackNode 等
2. **条件边**：通过 SwitcherNode + ConditionalEdge 实现多分支路由
3. **人机协作**：通过 InterruptedFlow（interruptBefore） + HumanFeedbackNode 实现人工审核断点
4. **Checkpoint 持久化**：使用 MemorySaver/PostgresSaver 持久化状态，支持断点续传
5. **SSE 流式推送**：实时推送节点执行状态到前端

### 5.3 从 2 节点到 11 节点

```java
// 本文的 2 节点图
graph.addNode("llmAnswer", new LLMAnswerNode(model));
graph.addEdge(START, "llmAnswer");
graph.addEdge("llmAnswer", END);

// ruoyi-ai 的 11 节点图（简化版）
graph.addNode("classifier", new ClassifierNode(model));
graph.addNode("keywordExtractor", new KeywordExtractorNode(model));
graph.addNode("knowledgeRetrieval", new KnowledgeRetrievalNode());
graph.addNode("llmAnswer", new LLMAnswerNode(model));
graph.addNode("switcher", new SwitcherNode());
graph.addNode("httpRequest", new HttpRequestNode());
graph.addNode("image", new ImageNode());
graph.addNode("mailSend", new MailSendNode());
graph.addNode("humanFeedback", new HumanFeedbackNode());

graph.addEdge(START, "classifier");
graph.addEdge("classifier", "switcher");

// 条件边：根据分类结果路由到不同分支
graph.addConditionalEdges("switcher", switchRoute, Map.of(
    "knowledge", "knowledgeRetrieval",
    "image", "image",
    "human", "humanFeedback",
    "default", "llmAnswer"
));
```

---

## 六、面试实战：3道面试题 + 回答框架

### Q1：StateGraph 的工作原理是什么？和传统工作流引擎（如 Activiti）有什么区别？

**考察点：** 面试官想考察候选人对状态图引擎核心原理的理解，以及与传统流程引擎的对比认知。

**回答框架：**

- **背景**：StateGraph 是 langgraph4j 的核心入口类，用于定义 AI 工作流的图结构。它通过"节点 + 边"的方式构建有向图，编译后即可执行。

- **方案**：工作原理分为四步：
  1. **定义图结构**：通过 `addNode()` 注册节点，`addEdge()` / `addConditionalEdges()` 定义边
  2. **编译**：调用 `compile()` 生成不可变的 `CompiledGraph`，编译时检查图结构合法性（是否有孤立节点、是否有环等）
  3. **执行**：从 START 节点出发，按边的定义依次执行节点，每个节点接收当前状态，返回状态增量
  4. **状态管理**：引擎根据 `Channel.Reducer` 策略将节点返回的状态增量合并到全局状态

- **深度（与传统工作流引擎的区别）**：
  - **Activiti/Camunda**：基于 BPMN 规范，流程图静态定义，适合审批流等结构化流程
  - **StateGraph**：专为 AI 工作流设计，条件边可以在运行时根据 LLM 输出动态决策
  - **核心区别**：StateGraph 支持循环图（ReAct 模式的 Agent-Tool 循环），BPMN 不支持；StateGraph 的状态是共享的 AgentState（黑板模式），BPMN 的变量是流程实例的局部变量

- **扩展**：StateGraph 的 Checkpoint 机制支持断点续传和时间旅行（回溯到任意历史状态），这在 AI 工作流的人机协作场景中非常关键。

### Q2：NodeAction 接口如何实现节点之间的数据传递？状态是如何管理的？

**考察点：** 面试官想考察候选人对节点间数据流和状态管理的理解。

**回答框架：**

- **背景**：在 langgraph4j 中，节点之间通过共享的 `AgentState` 传递数据，而不是通过方法参数或返回值直接传递。

- **方案**：数据传递分为三步：
  1. **节点写入**：每个节点实现 `NodeAction.apply(AgentState)` 方法，返回 `Map<String, Object>` 作为状态增量
  2. **引擎合并**：引擎将节点返回的 Map 合并到全局状态中，合并策略由 `Channel.Reducer` 决定
  3. **后续节点读取**：后续节点通过 `state.value(key)` 读取之前节点写入的数据

- **深度（Channel.Reducer 策略）**：
  - `Channels.overwrite()`：新值覆盖旧值（适合单值字段，如 input、output）
  - `Channels.appender()`：新值追加到列表（适合需要累积的字段，如消息历史、关键词列表）
  - `Channels.identity()`：只写入一次，后续写入忽略（适合初始化字段）
  - 选择错误的 Reducer 会导致数据丢失或重复

- **扩展**：状态管理的关键设计要点：
  - 使用常量定义状态键名，避免硬编码字符串散落在代码中
  - Schema 在创建 StateGraph 时定义，编译后不可修改
  - 状态可以持久化到 Checkpoint（MemorySaver / PostgresSaver），支持服务重启后恢复
  - 每个节点只返回"增量"，不关心全局状态的完整结构——这符合"最小知识原则"

### Q3：SSE 流式执行如何与 langgraph4j 配合？graph.stream() 的执行模型是怎样的？

**考察点：** 面试官想考察候选人对流式执行的理解，以及 SSE 与图引擎的协同设计。

**回答框架：**

- **背景**：AI 工作流通常需要实时展示执行进度（"正在执行分类节点"、"正在检索知识库"），而不是用户提交后干等结果。SSE（Server-Sent Events）是服务端推送给前端的标准协议，Spring 通过 `SseEmitter` 支持。

- **方案**：`graph.stream()` 是 langgraph4j 的流式执行方法，每执行一个节点就返回一个事件：
  ```java
  // 流式执行：每执行一个节点就返回一个事件
  for (var event : graph.stream(initialInput, config)) {
      String nodeName = event.node();           // 当前执行的节点名称
      AgentState state = event.state();         // 执行后的状态快照
      
      // 通过 SSE 推送到前端
      emitter.send(SseEmitter.event()
          .name("node-executed")
          .data(Map.of("node", nodeName, "state", state)));
  }
  ```

- **深度（执行模型）**：
  1. `graph.stream()` 返回一个惰性迭代器（Iterable），每次迭代执行一个节点
  2. 每个事件包含 `node()`（节点名称）和 `state()`（执行后的状态快照）
  3. 遇到条件边时，引擎自动评估 `EdgeAction` 函数，确定下一个节点
  4. 遇到 interruptBefore 断点时，循环自动退出，等待恢复
  5. 到达 END 节点时，循环结束

- **扩展**：SSE 与 langgraph4j 的协同设计要点：
  - **节点级推送**：每个节点执行完成后推送一次，粒度适中，既不会太频繁也不会太久
  - **状态快照**：推送的是节点执行后的状态快照，前端可以展示完整上下文
  - **事件命名**：使用 `event.name()` 区分不同事件类型（node-executed、flow-complete、node-error）
  - **异步处理**：SSE 通常使用异步线程或 CompletableFuture 执行，避免阻塞 HTTP 连接
  - **生产优化**：使用线程池而非 `new Thread()`，增加超时处理和错误恢复逻辑

---

## 七、总结

本文从零搭建了一个基于 langgraph4j 的最简 2 节点工作流，涉及以下知识点：

1. **StateGraph 核心概念**：节点（NodeAction）、边（Edge/ConditionalEdge）、状态（AgentState）
2. **图构建流程**：创建 StateGraph → 注册节点 → 定义边 → 编译 → 执行
3. **节点间数据传递**：通过共享的 AgentState（黑板模式）传递数据
4. **SSE 流式执行**：`graph.stream()` 逐节点执行，SSE 实时推送节点状态
5. **项目对照**：从最简 2 节点图到 ruoyi-ai 的 11 节点复杂工作流

在后续文章中，我们将深入分析 ruoyi-ai 的条件分支路由、人机协作断点、Checkpoint 持久化等高级特性。

---

## 参考资料

- [langgraph4j GitHub 仓库](https://github.com/langgraph4j/langgraph4j) — Java 语言的状态图引擎
- [langgraph4j 官方文档](https://bsorrentino.github.io/langgraph4j/) — StateGraph、Checkpoint、InterruptedFlow 详解
- [LangChain4j 官方文档](https://docs.langchain4j.dev) — 节点内部 LLM 调用的底层能力
- [Python LangGraph 官方文档](https://langchain-ai.github.io/langgraph/) — 概念参考（langgraph4j 设计灵感来源）
- [Spring SSE 文档](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-async.html#mvc-ann-async-sse) — SseEmitter 使用指南
- [ruoyi-ai GitHub 仓库](https://github.com/1byteone/ruoyi-ai) — 查看完整的 AI 流程编排模块