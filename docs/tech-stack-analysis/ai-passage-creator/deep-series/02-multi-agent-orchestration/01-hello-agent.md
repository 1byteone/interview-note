# 02 多Agent编排入门：StateGraph第一个协作流程

> 本文是 ai-passage-creator 项目技术栈深度剖析系列的第 2 篇（入门篇）。当单个 AI Agent 无法完成复杂任务时，就需要多个 Agent 协作。本文带你理解 StateGraph 的核心概念，并用代码跑通第一个双 Agent 协作流程。
>
> **对应项目：** `ai-passage-creator/ai-passage-creator-java` 模块 `graph` 包
> **难度等级：** Level 1 入门
> **预计阅读时间：** 18 分钟（含代码实操）

---

## 一、项目背景

### 1.1 为什么需要多 Agent 编排

在 ai-passage-creator 项目中，一篇完整文章的创作流程是：

```
用户输入选题
    ↓
Agent 1（标题生成）→ 生成 3-5 个标题
    ↓
Agent 2（大纲生成）→ 基于标题生成大纲
    ↓
Agent 3（正文生成）→ 基于大纲撰写正文
    ↓
Agent 4（配图分析）→ 分析正文中哪里需要配图
    ↓
Agent 5（并行配图）→ 为每个段落生成配图
    ↓
ContentMerger → 图文合并输出
```

这个流程有 5 个 Agent 参与，每个 Agent 的输入依赖前一个 Agent 的输出。如果只用单一的 `chatClient.call()`，你需要在代码里手工写一堆 `if-else` 来串联调用——这就是"多 Agent 编排"要解决的问题。

**多 Agent 编排的核心诉求：**

| 诉求 | 说明 | 没有编排框架的后果 |
|------|------|--------------------|
| 顺序执行 | 上一步输出是下一步输入 | 手工写变量传递，容易出错 |
| 条件分支 | 根据状态决定走哪个分支 | 大量 if-else 嵌套 |
| 并行执行 | 多个任务同时处理 | 手写线程池，管理复杂 |
| 状态管理 | 所有 Agent 共享一份数据 | 到处传参，参数爆炸 |

### 1.2 StateGraph 是什么

StateGraph 是 Spring AI Alibaba 1.1.0 提供的有向图（DAG，Directed Acyclic Graph）工作流引擎，专门用于编排 AI Agent 的执行流程。

通俗理解：**StateGraph 就像工厂里的流水线**。

- 流水线上有一个"工件"（State），在不同的工位（Node）之间流转
- 每个工位（Node）负责加工工件的一部分
- 传送带（Edge）决定工件流动的方向
- 分拣口（ConditionalEdge）根据工件情况决定送到哪个工位

### 1.3 LangGraph 对标

如果了解 Python 生态，Spring AI Alibaba 的 StateGraph 与 LangChain 的 LangGraph 是对标项目。核心概念几乎一一对应：

| Spring AI Alibaba | LangGraph | 说明 |
|-------------------|-----------|------|
| StateGraph | StateGraph | 图工作流 |
| Node | Node | 节点 |
| Edge | Edge | 边 |
| ConditionalEdge | ConditionalEdge | 条件边 |
| State | State | 状态 |
| KeyStrategy | Reducer | 状态合并策略 |
| CompiledGraph | CompiledGraph | 编译后的图 |
| ParallelNode | Parallel | 并行节点 |

---

## 二、核心概念

### 2.1 状态（State）

State 是所有节点共享的数据结构。在 StateGraph 中，每个节点都能读取和修改 State，修改结果会传给下一个节点。

```java
// 定义聊天状态
@DefaultBean
public class ChatState {
    // 用户消息列表
    private List<String> userMessages;
    // Agent 回复列表
    private List<String> assistantMessages;
    // 当前对话阶段
    private String stage;
}
```

**关键点：** State 是**不可变**的（Immutable）。每次节点处理完，返回的是一个新的 State 对象，而不是修改原对象。

### 2.2 节点（Node）

Node 是图中的一个处理单元，封装一段具体的逻辑。每个节点接收一个 State 作为输入，返回处理后的 State。

```java
// 定义一个节点：接收用户消息
@Node("get_user_message")
public ChatState getUserMessage(ChatState state) {
    // 从 state 中读取用户消息
    List<String> userMessages = state.getUserMessages();
    // 处理：保存用户消息
    state.setUserMessages(userMessages);
    // 返回新 state
    return state;
}
```

### 2.3 边（Edge）

Edge 定义节点之间的流转关系，决定执行顺序。

```java
// 定义图结构
StateGraph<ChatState> graph = new StateGraph<>(ChatState.class)
    .addNode("get_user_message", getUserMessageNode)   // 节点1
    .addNode("call_model", callModelNode)              // 节点2
    .addEdge(START, "get_user_message")                // 开始 → 节点1
    .addEdge("get_user_message", "call_model")         // 节点1 → 节点2
    .addEdge("call_model", END);                       // 节点2 → 结束
```

### 2.4 条件边（ConditionalEdge）

条件边根据 State 的内容决定下一步流向哪个节点，相当于流程图中的判断分支。

```java
// 根据 stage 字段决定流向
graph.addConditionalEdge(
    "call_model",                       // 从哪个节点出发
    state -> {
        // 判断逻辑：返回下一个节点的名称
        if ("complete".equals(state.getStage())) {
            return "end";               // 完成 → 结束
        } else {
            return "continue";          // 未完成 → 继续模型调用
        }
    }
);
```

### 2.5 KeyStrategy（合并策略）

当多个节点（特别是并行节点）同时修改 State 中的同一个字段时，会冲突。KeyStrategy 定义了冲突时的合并策略：

| 策略 | 说明 | 场景 |
|------|------|------|
| `AppendValue` | 追加到已有值后面 | 消息列表合并 |
| `OverWrite` | 直接覆盖 | 最新值覆盖旧值 |
| `Merge` | 合并两个值 | 对象属性合并 |

---

## 三、从零搭建代码

### 3.1 项目结构

```
state-graph-demo/
├── pom.xml
├── src/
│   ├── main/
│   │   ├── java/com/example/agent/
│   │   │   ├── StateGraphDemoApplication.java      # 启动类
│   │   │   ├── state/
│   │   │   │   └── ChatState.java                  # 状态
│   │   │   ├── node/
│   │   │   │   ├── GetUserMessageNode.java         # 节点1
│   │   │   │   ├── CallModelNode.java              # 节点2
│   │   │   │   └── SelectAgentNode.java            # 条件边节点
│   │   │   ├── service/
│   │   │   │   └── ChatGraphService.java           # 图编排服务
│   │   │   └── controller/
│   │   │       └── ChatGraphController.java        # 控制器
│   │   └── resources/
│   │       └── application.yml
│   └── test/
│       └── java/com/example/agent/
│           └── ChatGraphServiceTest.java           # 测试
```

### 3.2 pom.xml

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!-- 多Agent编排示例 —— Maven 配置文件 -->
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <!-- 父工程：Spring Boot -->
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.2.5</version>
        <relativePath/>
    </parent>

    <groupId>com.example</groupId>
    <artifactId>state-graph-demo</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <name>StateGraph Multi-Agent Demo</name>
    <description>基于 Spring AI Alibaba StateGraph 的多Agent编排示例</description>

    <properties>
        <java.version>17</java.version>
        <!-- StateGraph 需要 Spring AI Alibaba 1.1.0+ -->
        <spring-ai-alibaba.version>1.1.0</spring-ai-alibaba.version>
    </properties>

    <!-- 依赖管理 -->
    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>com.alibaba.cloud.ai</groupId>
                <artifactId>spring-ai-alibaba-bom</artifactId>
                <version>${spring-ai-alibaba.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <dependencies>
        <!-- Spring Boot Web -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>

        <!-- Spring AI Alibaba（包含 StateGraph） -->
        <dependency>
            <groupId>com.alibaba.cloud.ai</groupId>
            <artifactId>spring-ai-alibaba-starter</artifactId>
        </dependency>

        <!-- Lombok -->
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
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
                <configuration>
                    <excludes>
                        <exclude>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                        </exclude>
                    </excludes>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

### 3.3 定义状态 —— ChatState.java

```java
package com.example.agent.state;

// 引入 StateGraph 的状态注解
import com.alibaba.cloud.ai.graph.state.AgentState;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

/**
 * 聊天状态 —— StateGraph 中所有节点共享的数据
 *
 * 每个节点都可以读取/修改这个状态，修改结果传递给下一个节点
 * AgentState 是 StateGraph 提供的抽象基类，封装了状态的管理逻辑
 */
@Data                                   // Lombok 注解：自动生成 getter/setter/toString
@EqualsAndHashCode(callSuper = true)    // 重写 equals/hashCode 时包含父类字段
public class ChatState extends AgentState {

    /**
     * 构造器 —— 必须提供，内部调用 super 初始化状态数据
     *
     * @param initData 初始状态数据（Map 格式）
     */
    public ChatState(java.util.Map<String, Object> initData) {
        // 调用父类构造器，保存初始数据
        super(initData);
    }

    /**
     * 获取用户消息列表
     *
     * @return 用户消息列表，如果不存在则返回空列表
     */
    public List<String> getUserMessages() {
        // 从状态数据中读取 userMessages 字段
        // 兼容存储为 String 或 List 的情况
        return (List<String>) this.get("userMessages", new ArrayList<>());
    }

    /**
     * 设置用户消息列表
     *
     * @param userMessages 用户消息列表
     */
    public void setUserMessages(List<String> userMessages) {
        // 写入状态数据
        this.data.put("userMessages", userMessages);
    }

    /**
     * 获取 Agent 回复列表
     *
     * @return Agent 回复列表
     */
    public List<String> getAssistantMessages() {
        // 从状态数据中读取 assistantMessages 字段
        return (List<String>) this.get("assistantMessages", new ArrayList<>());
    }

    /**
     * 追加一条 Agent 回复
     *
     * @param message Agent 的回复内容
     */
    public void addAssistantMessage(String message) {
        // 获取现有回复列表
        List<String> messages = getAssistantMessages();
        // 追加新回复
        messages.add(message);
        // 写回状态
        this.data.put("assistantMessages", messages);
    }

    /**
     * 获取当前对话阶段
     *
     * @return 阶段名称：receive_user（接收用户消息）、call_model（调用模型）等
     */
    public String getStage() {
        // 读取 stage 字段，默认 "init"
        return (String) this.get("stage", "init");
    }

    /**
     * 设置对话阶段
     *
     * @param stage 阶段名称
     */
    public void setStage(String stage) {
        // 写入阶段字段
        this.data.put("stage", stage);
    }

    /**
     * 获取用户的最新消息
     *
     * @return 最后一条用户消息
     */
    public String getLatestUserMessage() {
        // 取出用户消息列表
        List<String> messages = getUserMessages();
        // 如果为空返回空字符串，否则返回最后一条
        return messages.isEmpty() ? "" : messages.get(messages.size() - 1);
    }
}
```

**状态设计要点：**

1. State 必须继承 `AgentState` 抽象类
2. 构造器接收 `Map<String, Object>` 初始数据
3. 通过 `this.get(key, defaultValue)` 读取、`this.data.put(key, value)` 写入
4. 提供业务语义明确的 getter/setter 方法，不直接操作 Map

### 3.4 节点1 —— GetUserMessageNode.java

```java
package com.example.agent.node;

import com.alibaba.cloud.ai.graph.Node;
import com.alibaba.cloud.ai.graph.state.AgentState;
import com.example.agent.state.ChatState;

import java.util.Map;

/**
 * 节点 1：接收用户消息
 *
 * 职责：将外部传入的用户消息写入 State
 * 这是流程的第一步，对应项目中的"用户输入选题"环节
 */
public class GetUserMessageNode implements Node {

    /**
     * 节点执行入口 —— StateGraph 框架会调用此方法
     *
     * @param state 当前状态（包含所有节点共享的数据）
     * @return 处理后返回一个新的状态（部分更新）
     */
    @Override
    public Map<String, Object> apply(AgentState state) {
        System.out.println("===== [节点1] GetUserMessageNode 执行 =====");

        // 1. 向下转型为我们的业务状态类
        // 状态类持有业务语义化的访问方法
        ChatState chatState = (ChatState) state;

        // 2. 模拟接收用户消息
        // 真实场景中，这个值由 Controller 传入
        String userMessage = "请帮我写一篇关于Spring Boot的入门文章";

        // 3. 将用户消息写入状态
        // 状态会在节点间传递，下一个节点可以读取
        chatState.getUserMessages().add(userMessage);

        // 4. 更新阶段标记：告诉后续节点"用户消息已接收"
        chatState.setStage("user_received");

        System.out.println("[节点1] 接收用户消息: " + userMessage);
        System.out.println("[节点1] 当前阶段: " + chatState.getStage());

        // 5. 返回状态数据（部分更新 Map）
        // StateGraph 会合并这个 Map 到全局状态
        return chatState.data;
    }
}
```

**节点开发规范：**

| 要点 | 说明 |
|------|------|
| 实现 `Node` 接口 | Spring AI Alibaba 图框架的节点接口 |
| `apply(AgentState state)` | 唯一方法，接收状态，返回部分更新 Map |
| 返回值 | 返回 `Map<String, Object>`，框架自动合并到全局状态 |
| 幂等性 | 节点最好设计为无副作用，多次重跑结果一致 |

### 3.5 节点2 —— CallModelNode.java

```java
package com.example.agent.node;

import com.alibaba.cloud.ai.graph.Node;
import com.alibaba.cloud.ai.graph.state.AgentState;
import com.example.agent.state.ChatState;

import java.util.List;
import java.util.Map;

/**
 * 节点 2：调用大模型
 *
 * 职责：读取用户消息，调用 AI 模型生成回复，写入状态
 * 对应项目中的 Agent 节点（如 TitleGeneratorAgent）
 *
 * 本示例为演示 StateGraph 的状态流转，
 * 真实项目中这里会注入 ChatClient 并调用通义千问
 */
public class CallModelNode implements Node {

    /**
     * 节点执行入口
     *
     * @param state 当前状态（上层节点传递下来）
     * @return 处理后返回部分更新状态
     */
    @Override
    public Map<String, Object> apply(AgentState state) {
        System.out.println("===== [节点2] CallModelNode 执行 =====");

        // 1. 向下转型
        ChatState chatState = (ChatState) state;

        // 2. 从状态中读取用户消息（上一个节点的产出）
        List<String> userMessages = chatState.getUserMessages();
        String latestMessage = userMessages.get(userMessages.size() - 1);

        // 3. 模拟调用大模型
        // 真实代码：ChatResponse response = chatClient.call(new Prompt(latestMessage));
        // String aiReply = response.getResult().getOutput().getContent();
        String aiReply = "【模拟AI回复】这是一个针对「" + latestMessage + "」的回复。"
                + "在多Agent编排中，这里会调用通义千问模型生成真实内容。";

        // 4. 将 AI 回复写入状态
        chatState.addAssistantMessage(aiReply);

        // 5. 标记阶段
        chatState.setStage("model_replied");

        System.out.println("[节点2] 读取用户消息: " + latestMessage);
        System.out.println("[节点2] AI回复: " + aiReply);
        System.out.println("[节点2] 当前阶段: " + chatState.getStage());

        // 6. 返回状态
        return chatState.data;
    }
}
```

### 3.6 条件边判断 —— SelectAgentNode.java

```java
package com.example.agent.node;

import com.alibaba.cloud.ai.graph.Node;
import com.alibaba.cloud.ai.graph.state.AgentState;
import com.example.agent.state.ChatState;

import java.util.Map;

/**
 * 条件边判断节点
 *
 * 职责：根据状态中的内容决定流程走向
 * 对应项目中的应用：根据用户是否是 VIP 决定配图策略
 *
 * 本节点的返回值决定 ConditionalEdge 将流程导向哪个节点：
 *   - 返回 "complete" → 流程结束（END）
 *   - 返回 "continue" → 继续调用模型
 */
public class SelectAgentNode implements Node {

    /**
     * 节点执行入口
     *
     * @param state 当前状态
     * @return 返回状态数据，其中 stage 字段用于条件边路由
     */
    @Override
    public Map<String, Object> apply(AgentState state) {
        System.out.println("===== [选择节点] SelectAgentNode 执行 =====");

        // 1. 向下转型
        ChatState chatState = (ChatState) state;

        // 2. 模拟判断逻辑：如果 AI 已经回复过，就结束流程
        // 真实场景：根据用户需求、会员等级、错误重试次数等做路由
        boolean hasReply = !chatState.getAssistantMessages().isEmpty();

        // 3. 设置路由结果
        if (hasReply) {
            // AI 已回复 → 流程可以结束
            chatState.setStage("complete");
            System.out.println("[选择节点] AI已回复，流程完成，走向 END");
        } else {
            // 未回复 → 继续流程
            chatState.setStage("retry");
            System.out.println("[选择节点] 无回复，继续流程");
        }

        // 4. 返回状态
        return chatState.data;
    }
}
```

### 3.7 图编排服务 —— ChatGraphService.java

```java
package com.example.agent.service;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.state.AgentState;
import com.example.agent.node.CallModelNode;
import com.example.agent.node.GetUserMessageNode;
import com.example.agent.node.SelectAgentNode;
import com.example.agent.state.ChatState;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 图编排服务 —— 构建并执行 StateGraph
 *
 * 这是 StateGraph 的核心使用方式：
 * 1. 创建 StateGraph，指定状态类型
 * 2. 注册所有节点
 * 3. 定义边的流转（顺序边 + 条件边）
 * 4. 编译生成可执行图 CompiledGraph
 * 5. 执行图，传入初始状态
 */
@Service // Spring 服务 Bean
public class ChatGraphService {

    // 编译后的图（线程安全，可复用）
    private final CompiledGraph compiledGraph;

    /**
     * 构造器 —— 构建 StateGraph
     *
     * 需要依赖注入的两个节点 Bean：
     * - GetUserMessageNode（用户消息接收）
     * - CallModelNode（模型调用）
     * 这里的节点是普通 Java 类，真实项目中会用 @Component 注册后注入
     */
    public ChatGraphService() {
        // ========== 步骤1：创建 StateGraph ==========
        // 指定状态类 ChatState.class
        StateGraph<ChatState> graph = new StateGraph<>(ChatState.class);

        // ========== 步骤2：注册所有节点 ==========
        // addNode(节点名称, 节点实现)
        graph.addNode("get_user_message", new GetUserMessageNode())
             .addNode("call_model", new CallModelNode())
             .addNode("select_next", new SelectAgentNode());

        // ========== 步骤3：定义边 ==========
        // 顺序边：START → 节点1
        graph.addEdge(StateGraph.START, "get_user_message");

        // 顺序边：节点1 → 节点2
        graph.addEdge("get_user_message", "call_model");

        // 顺序边：节点2 → 选择节点
        graph.addEdge("call_model", "select_next");

        // 条件边：从「选择节点」出发，根据 stage 决定流向
        // 第1个参数：起始节点
        // 第2个参数：路由函数，状态 → 目标节点名称
        // 第3个参数（可选）：目标节点集合，用于校验
        graph.addConditionalEdge(
            "select_next",
            (state) -> {
                // 路由逻辑：读取 stage
                ChatState chatState = (ChatState) state;
                String stage = chatState.getStage();
                // 已经完成 → 结束
                if ("complete".equals(stage)) {
                    return "end";           // end 是特殊节点，表示流程结束
                }
                // 否则回到模型节点继续（本例中因已有回复，实际会走 end）
                return "call_model";
            },
            java.util.List.of("end", "call_model")  // 允许的目标节点
        );

        // ========== 步骤4：编译图 ==========
        // compile() 生成可执行图，编译后会做合法性校验（环检测等）
        this.compiledGraph = graph.compile();

        System.out.println("==========================================");
        System.out.println("StateGraph 编译成功！");
        System.out.println("节点: get_user_message → call_model → select_next");
        System.out.println("==========================================");
    }

    /**
     * 执行图流程
     *
     * @return 最终的完整状态
     */
    public Map<String, Object> run() {
        // ========== 步骤5：执行图 ==========
        // invoke() 是同步执行，传入初始状态
        // 初始状态可以是空 Map
        Map<String, Object> result = compiledGraph.invoke(Map.of());

        // 打印执行结果
        System.out.println("===== StateGraph 执行结果 =====");
        System.out.println("最终状态: " + result);

        // 返回结果
        return result;
    }

    /**
     * 异步执行图流程
     *
     * StateGraph 支持异步执行，返回 CompletableFuture
     * 适合放入 Spring 的异步任务或 SSE 场景
     *
     * @return 异步结果
     */
    public CompletableFuture<Map<String, Object>> runAsync() {
        // stream() 方式返回流式执行结果
        // 简化演示：返回一个已经完成的 future
        return CompletableFuture.completedFuture(run());
    }
}
```

### 3.8 控制器 —— ChatGraphController.java

```java
package com.example.agent.controller;

import com.example.agent.service.ChatGraphService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 多 Agent 编排控制器 —— 提供流程执行 API
 */
@RestController // REST 控制器
@RequestMapping("/api/graph") // 请求路径前缀
public class ChatGraphController {

    // 注入图编排服务
    private final ChatGraphService graphService;

    public ChatGraphController(ChatGraphService graphService) {
        this.graphService = graphService;
    }

    /**
     * 执行整个多 Agent 流程
     *
     * 请求示例：GET http://localhost:8080/api/graph/run
     * 响应示例：{"finalStage":"complete","userMessages":["..."],"assistantMessages":["..."]}
     */
    @GetMapping("/run")
    public Map<String, Object> run() {
        // 执行图流程并返回最终状态
        return graphService.run();
    }
}
```

### 3.9 启动类 —— StateGraphDemoApplication.java

```java
package com.example.agent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 多Agent编排演示 —— 主启动类
 */
@SpringBootApplication
public class StateGraphDemoApplication {

    public static void main(String[] args) {
        // 启动 Spring Boot
        SpringApplication.run(StateGraphDemoApplication.class, args);
        System.out.println("========================================");
        System.out.println("StateGraph 多Agent编排演示启动成功！");
        System.out.println("访问 http://localhost:8080/api/graph/run 查看执行过程");
        System.out.println("========================================");
    }
}
```

### 3.10 单元测试 —— ChatGraphServiceTest.java

```java
package com.example.agent;

import com.example.agent.service.ChatGraphService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 图编排服务 —— 单元测试
 *
 * 验证：
 * 1. 图流程能否正确执行
 * 2. 状态能否在节点间正确传递
 * 3. 节点执行顺序是否正确
 */
@SpringBootTest // 加载完整应用上下文
class ChatGraphServiceTest {

    // 自动注入
    @Autowired
    private ChatGraphService graphService;

    /**
     * 测试整个图流程执行
     */
    @Test
    void testGraphExecution() {
        // 1. 执行流程
        Map<String, Object> result = graphService.run();

        // 2. 验证不为空
        assertNotNull(result, "执行结果不能为空");

        // 3. 验证用户消息已被写入状态
        Object userMessagesObj = result.get("userMessages");
        assertNotNull(userMessagesObj, "用户消息应该存在于状态中");

        // 4. 验证 AI 回复存在
        Object assistantMessagesObj = result.get("assistantMessages");
        assertNotNull(assistantMessagesObj, "AI回复应该存在于状态中");

        @SuppressWarnings("unchecked")
        List<String> assistantMessages = (List<String>) assistantMessagesObj;
        assertFalse(assistantMessages.isEmpty(), "AI回复列表不应为空");

        // 5. 打印结果
        System.out.println("= StateGraph 流程图执行测试 =");
        System.out.println("状态字段: " + result.keySet());
        System.out.println("AI回复: " + assistantMessages);
        System.out.println("==============================");
    }

    /**
     * 测试状态流转
     *
     * 验证：用户消息能传递到模型调用节点
     */
    @Test
    void testStateFlow() {
        // 执行流程
        Map<String, Object> result = graphService.run();

        // 验证用户消息列表包含我们的测试消息
        @SuppressWarnings("unchecked")
        List<String> userMessages = (List<String>) result.get("userMessages");
        assertNotNull(userMessages);
        assertFalse(userMessages.isEmpty(), "用户消息列表不应为空");

        // 打印状态流转结果
        System.out.println("= 状态流转测试 =");
        System.out.println("用户消息数量: " + userMessages.size());

        // 验证阶段流转
        Object stage = result.get("stage");
        System.out.println("最终阶段: " + stage);
        assertNotNull(stage, "阶段应该被节点设置");
        System.out.println("==================");
    }
}
```

---

## 四、运行验证

### 4.1 启动项目

```bash
# 编译并启动
mvn spring-boot:run
```

启动输出：

```
==========================================
StateGraph 编译成功！
节点: get_user_message → call_model → select_next
==========================================
StateGraph 多Agent编排演示启动成功！
```

### 4.2 执行流程

```bash
# 调用流程执行接口
curl http://localhost:8080/api/graph/run
```

**预期响应：**

```json
{
  "userMessages": ["请帮我写一篇关于Spring Boot的入门文章"],
  "assistantMessages": ["【模拟AI回复】这是一个针对「请帮我写一篇关于Spring Boot的入门文章」的回复。..."],
  "stage": "complete"
}
```

### 4.3 控制台日志（观察节点执行顺序）

```
===== [节点1] GetUserMessageNode 执行 =====
[节点1] 接收用户消息: 请帮我写一篇关于Spring Boot的入门文章
[节点1] 当前阶段: user_received
===== [节点2] CallModelNode 执行 =====
[节点2] 读取用户消息: 请帮我写一篇关于Spring Boot的入门文章
[节点2] AI回复: ...
[节点2] 当前阶段: model_replied
===== [选择节点] SelectAgentNode 执行 =====
[选择节点] AI已回复，流程完成，走向 END
===== StateGraph 执行结果 =====
最终状态: {userMessages=[...], assistantMessages=[...], stage=complete}
```

### 4.4 运行测试

```bash
mvn test
```

---

## 五、项目对照

### 5.1 ai-passage-creator 中的真实 StateGraph

ai-passage-creator 使用 StateGraph 编排了 5 个 Agent 的完整创作流程：

```java
// 项目中的图定义（结构示意）
StateGraph<PassageState> graph = new StateGraph<>(PassageState.class)
    // 5 个 Agent 节点
    .addNode("title_gen", new TitleGeneratorAgent())      // 标题生成
    .addNode("outline_gen", new OutlineGeneratorAgent())  // 大纲生成
    .addNode("content_gen", new ContentGeneratorAgent())  // 正文生成
    .addNode("image_analyzer", new ImageAnalyzerAgent())  // 配图分析
    .addNode("image_gen", new ParallelImageAgent())       // 并行配图
    .addNode("merger", new ContentMerger())               // 图文合并

    // 顺序执行链
    .addEdge(START, "title_gen")
    .addEdge("title_gen", "outline_gen")
    .addEdge("outline_gen", "content_gen")
    .addEdge("content_gen", "image_analyzer")
    // 条件边：根据配图需求判断是否进入配图
    .addConditionalEdge("image_analyzer", state -> {
        if (needImage(state)) {
            return "image_gen";
        }
        return "merger";
    })
    // 并行配图完成后合并
    .addEdge("image_gen", "merger")
    .addEdge("merger", END);
```

### 5.2 本示例 vs 项目实战

| 本示例 | ai-passage-creator |
|--------|-------------------|
| 2 个 Agent 串行 | 5 个 Agent 流水线 + 并行 |
| 模拟调用（print） | 真实调用 ChatClient + 通义千问 |
| 单一顺序 + 一条条件边 | 条件边 + ParallelNode 并行 |
| 状态只存消息 | 状态存选题、标题、大纲、正文、配图 URL |
| 同步执行 | 同步 + 流式 SSE |

### 5.3 关键设计模式提炼

1. **节点职责单一**：每个节点只做一件事（接收 / 生成 / 判断）
2. **状态解耦**：节点之间不直接通信，只通过 State 传递数据
3. **图即配置**：业务流转逻辑收敛在图的定义处，易读易改
4. **可测试性**：节点是纯函数（输入 State → 输出 State），易于单测

---

## 六、面试题

### 面试题 1：StateGraph 相比手写 if-else 编排 Agent 的优势是什么？

**参考答案：**

主要优势有四点：

1. **可读性**：流程集中定义在图中，一眼看清整体执行顺序；手写 if-else 则散落各处
2. **可扩展性**：新增一个 Agent 只需注册节点 + 添加边；手写代码需要改动调用链
3. **状态管理**：State 在节点间自动传递和合并，无需手工参数传递
4. **并行能力**：ParallelNode 原生支持并行执行；手写需要自己管理线程池

此外，StateGraph 编译时会做合法性校验（DAG 环检测），提前发现配置错误；还提供可视化调试支持。

### 面试题 2：State 为什么要设计成不可变？节点返回 Map 而不是直接修改 State 是为什么？

**参考答案：**

State 不可变的设计有两个核心原因：

1. **并行安全**：并行节点同时处理 State 时，如果可变，会产生数据竞争。不可变 + 返回新对象可以安全地并发执行
2. **可追溯性**：每个节点返回的部分更新完成后，框架可以根据 KeyStrategy 合并，保证每个节点的输入是稳定一致的

节点返回 `Map<String, Object>`（部分更新）而不是直接改 State，是因为：
- StateGraph 内部用 Map 保存所有字段
- 节点只返回自己修改的字段（增量更新），框架负责合并
- 合并时机由 KeyStrategy 决定，支持追加（AppendValue）、覆盖（OverWrite）等策略

### 面试题 3：ConditionalEdge 的路由函数需要注意什么？

**参考答案：**

路由函数（返回节点名的 lambda）有三个要点：

1. **返回的节点名必须已注册**：否则编译或执行时报错。所以 addConditionalEdge 的第三个参数提供了允许的目标节点列表用于校验
2. **兜底分支**：路由条件可能都不满足，务必设计默认分支（返回某个固定节点），避免 NPE
3. ****纯函数**：路由逻辑应只依赖 State 中的内容，不要有副作用（如修改 State），否则会导致分支判断与实际执行不一致

---

## 七、总结

本文从零搭建了第一个 StateGraph 多 Agent 协作流程：

**核心要点：**

1. StateGraph = 有向图工作流，Node（节点）+ Edge（边）+ ConditionalEdge（条件边）+ State（状态）
2. State 是节点间共享的数据，继承 AgentState，通过 get/put 读写
3. 节点实现 Node 接口，返回 Map 部分更新
4. 图构建流程：创建 → 注册节点 → 定义边 → 编译
5. ConditionalEdge 根据状态路由到不同节点

**下一步学习：**

- 多节点并行编排（ParallelNode）
- 流式输出与 SSE 集成
- 真实模型调用（ChatClient 注入）
- 状态持久化（把 State 存入 Redis）