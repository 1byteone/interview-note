# 02 · ReAct Agent入门：从零实现思考-行动-观察循环

> **深度系列 | mewpaw-code | Level 1 入门篇**
>
> 本篇目标：不依赖任何 AI 大模型 API Key，从零实现一个完整可运行的 **ReAct Agent 循环**。你会亲手写出 8 种事件类型的密封接口、带迭代上限和错误容忍的核心循环、用规则引擎模拟 LLM 决策的 Mock 服务，以及一个可用的计算器工具。跑通"思考 → 行动 → 观察"的完整闭环，最后对照真实的 mewpaw-code 项目，理解入门版到生产版的差距。
>
> **前置要求：** 已安装 JDK 21 和 Maven 3.8+，读过本系列第一篇 [01-hello-java21.md](../01-java21-springboot/01-hello-java21.md)，了解 Java 21 的 Records / Sealed Classes / 模式匹配等基础语法。

---

## 一、项目背景：为什么 Agent 需要"思考-行动-观察"循环？

### 1.1 ReAct 模式：让 AI 从"嘴炮"变成"动手"

2022 年，Yao 等人在论文 **"ReAct: Synergizing Reasoning and Acting in Language Models"** 中提出了一个简单但深刻的思路：**让大语言模型（LLM）在推理（Reasoning）和行动（Acting）之间交替进行**，而不是像传统聊天机器人那样"说一句就完事"。

传统 LLM 的使用方式是这样的：

```
用户：帮我查一下今天北京的天气
AI：抱歉，我无法获取实时天气信息，请手动查询天气网站。
```

模型的知识截止于训练数据，它**无法主动获取外部信息**，更无法"操作电脑"。ReAct 模式的核心创新是给 LLM 装上了"手"——它可以在思考过程中决定调用工具（查询天气 API、执行命令、读写文件），然后把工具返回的结果作为"观察"融入下一轮思考，最终形成完整的回答。

ReAct 的循环可以概括为三个步骤：

```
Thought（思考）→ Action（行动）→ Observation（观察）→ Thought（思考）→ …
```

每一步的详细含义：

| 步骤 | 英文 | 含义 | 类比人类 |
|------|------|------|---------|
| 思考 | Thought | AI 分析当前状态，决定下一步做什么 | 你遇到问题，先想"我该怎么做" |
| 行动 | Action | 调用一个工具的决策（如执行 Bash 命令） | 你动手去做某件事 |
| 观察 | Observation | 工具执行后返回的结果 | 你看到做这件事的结果 |
| 循环 | Loop | 根据观察结果再次思考，直到任务完成 | 你根据结果调整做法，直到满意 |

mewpaw-code 的核心 AgentLoop 正是这一模式的工程化实现：**8 种事件类型、50 轮迭代上限、3 次连续错误容忍、5000/500 字符的输出截断**——这些数字和约束，都是为了让 ReAct 循环在生产环境中稳定、安全、可预测地运行。

### 1.2 从传统 if-else 到 ReAct：一个思维跃迁

如果你是一个传统的 Java 后端工程师，你熟悉的"决策模式"可能是这样的：

```java
// 传统 if-else 模式：所有的决策路径都由开发者预先写死
if (userInput.contains("天气")) {
    return callWeatherApi();
} else if (userInput.contains("时间")) {
    return getCurrentTime();
} else {
    return "我不理解你的问题";
}
```

这种模式的问题是显而易见的：**每增加一种能力，就必须修改代码**。今天的 if-else 能处理 10 种情况，明天的用户可能提出第 11 种需求，你得重新发版。

ReAct 模式把决策权交给了 LLM：

```java
// ReAct 模式：开发者只提供"工具箱"和"决策规则"，
// LLM 自主决定"什么时候用哪个工具"
String thought = llm.think(history, toolList);  // LLM 思考
if (thought.contains("需要调用工具")) {
    ToolCall action = parseToolCall(thought);     // 解析出行动
    String observation = toolRegistry.execute(action); // 执行并观察
    history.add(observation);                     // 观察结果喂回去
    // 让 LLM 再次思考……
}
```

**核心区别**：if-else 模式下，开发者是"决策者"，代码是"执行者"；ReAct 模式下，LLM 是"决策者"，开发者是"工具制造者"和"安全守卫者"。

### 1.3 mewpaw-code 为什么需要 Agent Loop

mewpaw-code 是一个 CLI 编码 Agent，它的典型场景是：

```
用户：帮我在当前目录创建一个 Spring Boot 项目，包含一个 UserController
```

这个任务拆解后需要多步操作：创建目录结构 → 生成 pom.xml → 创建主类 → 创建 Controller → 验证编译通过。每一步都可能出错（比如目录已存在、Maven 依赖写错），需要 Agent 根据错误信息自我修正。

**没有 Agent Loop 的话**，一个 LLM 调用只能做一件事，要么生成代码，要么执行命令，无法把"生成 → 执行 → 查看结果 → 修正"串起来。**Agent Loop 就是这根"串起珍珠的线"**，它让多个 LLM 调用和工具执行可以协作完成一个复杂任务。

---

## 二、核心概念：理解 AgentLoop 的五个关键设计

### 2.1 AgentLoop：什么是"循环"？

AgentLoop 是 mewpaw-code 的"心脏"，它是一个 **while 循环**，每一轮迭代做三件事：

1. **调用 LLM**：把对话历史发给大模型，模型返回一个决策（说一段话 / 调用某个工具 / 标记完成）
2. **执行决策**：如果是"调用工具"，就在安全沙箱里执行工具，得到观察结果
3. **回填历史**：把观察结果追加到对话历史中，进入下一轮

用伪代码描述就是：

```java
while (iteration < MAX_ITERATIONS && !finished) {
    // 1. 思考：LLM 根据当前状态做决策
    AgentEvent event = llm.generate(history);
    publishEvent(event);  // 事件驱动：通知所有监听者
    
    // 2. 行动：根据事件类型分派
    switch (event) {
        case ToolCall call -> {
            // 执行工具
            String result = executeTool(call);
            // 观察结果回填
            history.add(new ToolResult(result));
            consecutiveErrors = 0;  // 成功执行，重置错误计数
        }
        case TextOutput text -> {
            // AI 直接输出文本，可能表示任务完成
            output = text.content();
            finished = true;
        }
        case Error err -> {
            consecutiveErrors++;
            if (consecutiveErrors >= MAX_CONSECUTIVE_ERRORS) {
                // 连续错误太多，强制终止
                finished = true;
                output = "连续错误达到上限，终止执行";
            }
        }
    }
    iteration++;
}
```

### 2.2 AgentEvent：8 种事件类型

mewpaw-code 把 Agent 循环中可能发生的所有情况，抽象为 **8 种事件类型**，全部放在一个 `sealed interface` 中。这是 Sealed Classes 在真实项目中的教科书级应用：

| 事件类型 | 含义 | 触发时机 | 类比 |
|---------|------|---------|------|
| `TextOutput` | AI 输出文本 | LLM 决定直接说话 | 你直接回答用户问题 |
| `ToolCall` | AI 调用工具 | LLM 决定使用某个工具 | 你决定用计算机算一下 |
| `ToolResult` | 工具返回结果 | 工具执行完成 | 计算机算出了结果 |
| `Error` | 发生错误 | 工具执行失败或 LLM 调用异常 | 你操作失误了 |
| `Thought` | AI 的思考过程 | LLM 在 ReAct 格式中输出思考 | 你在心里盘算"该怎么做" |
| `Complete` | 任务完成 | Agent 判断任务已结束 | 你说"搞定了" |
| `UserInput` | 用户输入 | 用户发起新一轮对话 | 用户向你提问 |
| `SystemEvent` | 系统事件 | 配置变更、中断信号、安全告警等 | 系统管理员通知你 |

**为什么用 sealed interface？** 因为 Agent 循环的消费方（事件监听器、日志记录器、安全检查器）需要**穷尽处理所有事件类型**。如果事件类型可以任意扩展，消费方代码永远无法确定"是否覆盖了所有情况"。Sealed interface 把事件类型锁死为 8 种，编译器保证所有 switch 分支写全——**漏写一种事件类型，直接编译失败**。

### 2.3 三个关键常量

AgentLoop 中有三个硬编码常量，它们不是随意选的：

| 常量 | 值 | 为什么是这个值 |
|------|-----|---------------|
| `MAX_ITERATIONS` | 50 | 大多数编码任务在 10-20 轮内完成，50 轮作为安全上限，既给足够空间又防止无限循环耗尽 token 配额 |
| `MAX_CONSECUTIVE_ERRORS` | 3 | 连续 3 次错误说明"当前策略有问题"，与其继续浪费 token 不如终止让用户介入。3 次给了"试错-修正"的空间 |
| 输出截断：工具结果 | 5000 字符 | 工具返回（如 `git log`）可能很长，截断到 5000 字符防止上下文窗口溢出 |
| 输出截断：错误信息 | 500 字符 | 错误信息通常在前 500 字符里就能定位根因，截断到 500 足够诊断 |

### 2.4 事件驱动架构

AgentLoop 不是"闷头自己跑"的——它每产生一个事件，就会**发布（publish）给所有注册的监听器（Listener）**。这带来了三个好处：

1. **解耦**：日志记录、安全检查、进度展示都是独立的监听器，与核心循环互不干扰
2. **可观测性**：每个事件都可以被记录、审计、重放
3. **可扩展**：新增一个"事件→发邮件通知"的功能，只需加一个监听器，改一行代码

事件驱动架构的简化模型：

```
AgentLoop (事件生产者)
    │
    ├── publish(TextOutput) ──→ [日志监听器, TUI监听器, 审计监听器]
    ├── publish(ToolCall)   ──→ [安全监听器(检查工具是否允许调用), 日志监听器]
    ├── publish(ToolResult) ──→ [日志监听器, TUI监听器]
    └── publish(Error)      ──→ [告警监听器, 错误计数监听器]
```

---

## 三、从零搭建：完整 ReAct Agent Loop 项目

### 3.1 项目结构总览

```
hello-react-agent/
├── pom.xml                              # Maven 配置：Spring Boot 3.3.5 + 依赖
└── src/
    ├── main/
    │   ├── resources/
    │   │   └── application.yml          # Spring 配置
    │   └── java/com/example/agent/
    │       ├── HelloAgentApplication.java       # 主类 + CommandLineRunner 入口
    │       ├── model/
    │       │   └── AgentEvent.java              # Sealed Interface：8 种事件类型
    │       ├── core/
    │       │   ├── AgentLoop.java               # 核心循环（while + 事件驱动）
    │       │   └── AgentEventListener.java      # 事件监听器接口
    │       ├── service/
    │       │   └── MockChatService.java         # 模拟 LLM 的规则引擎（零 API Key）
    │       ├── tool/
    │       │   ├── AgentTool.java               # 工具接口
    │       │   ├── CalculatorTool.java          # 计算器工具
    │       │   └── ToolRegistry.java            # 工具注册表
    │       └── config/
    │           └── AgentProperties.java         # 配置绑定（record）
    └── test/java/com/example/agent/
        └── HelloAgentApplicationTest.java       # 至少 5 个测试用例
```

### 3.2 第一步：pom.xml（Maven 骨架，零 AI 依赖）

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!-- Maven 项目对象模型文件：描述项目结构、依赖关系和构建配置 -->
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                             https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <!-- 继承 Spring Boot 父 POM：
         统一管理上千个依赖的版本号，子模块只需声明 groupId 和 artifactId，
         无需写 version，由父 POM 统一锁定 -->
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.3.5</version>
        <relativePath/> <!-- 从 Maven 仓库查找父 POM -->
    </parent>

    <!-- 项目坐标：组织名、工程名、版本号，三者唯一标识一个 Maven 项目 -->
    <groupId>com.example</groupId>
    <artifactId>hello-react-agent</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <name>hello-react-agent</name>
    <!-- 项目描述：简短说明这个项目是干什么的 -->
    <description>Java 21 + Spring Boot 3.3.5 ReAct Agent 入门示例 —— Hello React Agent</description>

    <properties>
        <!-- Java 版本锁定到 21：编译器使用 Java 21 语法规则，
             Spring Boot 3.3.5 也按 Java 21 的模块系统工作 -->
        <java.version>21</java.version>
        <!-- 项目源码编码：UTF-8 支持中文注释和字符串 -->
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <dependencies>
        <!-- Spring Boot 核心 starter（不是 starter-web！）
             只提供 IoC 容器、配置绑定、生命周期管理等功能，
             不含内嵌 Tomcat，这正是 CLI 应用的关键 -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter</artifactId>
        </dependency>

        <!-- Spring Boot 配置处理器：编译时生成 spring-configuration-metadata.json，
             让 IDE 在写 application.yml 时有自动补全和文档提示 -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-configuration-processor</artifactId>
            <optional>true</optional>
        </dependency>

        <!-- 测试 starter：内含 JUnit 5、AssertJ、Mockito 等全套测试工具 -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <!-- Spring Boot Maven 打包插件：
                 把应用打成"可执行胖 jar"，包含所有依赖和启动入口类，
                 用 java -jar 即可直接运行 -->
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

**关键设计说明：** 这个 `pom.xml` 里**没有任何 AI 相关的依赖**（没有 LangChain4j、没有 OpenAI SDK、没有 HTTP 客户端）。因为我们的 `MockChatService` 用纯 Java 规则引擎模拟 LLM，整个项目零 API Key、零网络、零费用，任何人在任何环境都能 `mvn compile` 通过。

### 3.3 第二步：application.yml（Spring 配置）

```yaml
# Spring Boot 应用配置：所有配置项使用 kebab-case（小写+连字符）
spring:
  application:
    name: hello-react-agent  # 应用名：出现在日志、监控等地方

# 自定义 Agent 配置段：被 AgentProperties record 绑定
agent:
  max-iterations: 50          # ReAct 循环最大迭代次数（对应 mewpaw-code 的 MAX_ITERATIONS）
  max-consecutive-errors: 3   # 最大连续错误次数（超过则强制终止循环）
  output-max-length: 5000     # 工具返回结果的最大字符数（超出截断）
  error-max-length: 500       # 错误信息最大字符数（超出截断）
  verbose: true               # 是否打印详细日志（调试时开启）
```

### 3.4 第三步：AgentProperties（用 Record 绑定配置）

```java
package com.example.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AgentProperties —— 用 Record 绑定 agent.* 配置项
 *
 * Spring Boot 自动把 application.yml 中 agent 前缀下的配置项
 * 绑定到 record 的每个组件上。Record 天然不可变，配置对象
 * 一旦创建就不会被篡改，在多线程环境中安全无虞。
 *
 * 对应 mewpaw-code 中的 AgentConfig 配置类，但那边用 @Data + 普通类，
 * 这里用 Record 展示 Java 21 的更简洁写法。
 */
@ConfigurationProperties(prefix = "agent")
public record AgentProperties(
        // 最大迭代轮数：对应 agent.max-iterations，默认 50 轮
        int maxIterations,
        // 最大连续错误次数：对应 agent.max-consecutive-errors，默认 3 次
        int maxConsecutiveErrors,
        // 工具返回结果最大字符数：对应 agent.output-max-length，默认 5000
        int outputMaxLength,
        // 错误信息最大字符数：对应 agent.error-max-length，默认 500
        int errorMaxLength,
        // 是否开启详细日志：对应 agent.verbose
        boolean verbose
) {
}
```

**注意：** Record 的访问器是 `maxIterations()` 而不是 `getMaxIterations()`，这是 Record 与普通 POJO 的语法差异，写代码时不要搞混。

### 3.5 第四步：AgentEvent（Sealed Interface，8 种事件类型）

这是全篇最重要的类型定义，也是 Java 21 Sealed Classes 在 Agent 领域建模中的核心应用。

```java
package com.example.agent.model;

import java.time.Duration;
import java.time.Instant;

/**
 * AgentEvent —— Agent 循环中所有可能事件的密封接口
 *
 * sealed 关键字声明：这个接口只允许 permits 列出的 8 个实现类，
 * 任何其他类都不能实现它。编译器据此做穷尽性检查：
 * 凡是 switch 这个接口的地方，必须把 8 个分支写全，漏写即编译报错。
 *
 * 这 8 种事件对应 mewpaw-code AgentLoop 中 AgentEvent 的同类设计，
 * 是 ReAct 循环中"状态变化"的完整建模。
 *
 * 每个事件都记录了创建时间戳（timestamp），方便事件溯源和调试。
 */
public sealed interface AgentEvent
        permits AgentEvent.TextOutput,    // 1. AI 输出文本
                AgentEvent.ToolCall,      // 2. AI 调用工具
                AgentEvent.ToolResult,    // 3. 工具返回结果
                AgentEvent.Error,         // 4. 发生错误
                AgentEvent.Thought,       // 5. AI 的思考过程
                AgentEvent.Complete,      // 6. 任务完成
                AgentEvent.UserInput,     // 7. 用户输入
                AgentEvent.SystemEvent {  // 8. 系统事件

    // 所有事件共享的创建时间戳，每个 record 都在构造时记录
    Instant timestamp();

    // ========== 1. TextOutput：AI 直接输出文本 ==========

    /**
     * TextOutput —— AI 直接输出一段文本给用户
     *
     * @param content         AI 输出的文本内容
     * @param truncatedLength 如果内容被截断，记录原始长度；未截断则为 0
     * @param timestamp       事件创建时间
     */
    record TextOutput(
            String content,
            int truncatedLength,
            Instant timestamp
    ) implements AgentEvent {
        // 紧凑构造器：不传 timestamp 时自动使用当前时间
        public TextOutput(String content, int truncatedLength) {
            this(content, truncatedLength, Instant.now());
        }
    }

    // ========== 2. ToolCall：AI 决定调用某个工具 ==========

    /**
     * ToolCall —— AI 决定调用某个工具的决策事件
     *
     * @param toolName    工具名（如 "calculator"）
     * @param arguments   传给工具的参数字符串（如 "1 + 2 * 3"）
     * @param thoughtId   关联的思考 ID（把 ToolCall 和之前 Thought 关联起来）
     * @param timestamp   事件创建时间
     */
    record ToolCall(
            String toolName,
            String arguments,
            String thoughtId,
            Instant timestamp
    ) implements AgentEvent {
        // 紧凑构造器：不传 thoughtId 和 timestamp 时自动生成
        public ToolCall(String toolName, String arguments) {
            this(toolName, arguments, "", Instant.now());
        }
    }

    // ========== 3. ToolResult：工具执行完成后的结果 ==========

    /**
     * ToolResult —— 工具执行一次调用的完整结果
     *
     * @param toolName    工具名
     * @param result      工具返回的文本结果（可能被截断）
     * @param success     是否执行成功
     * @param durationMs  执行耗时（毫秒）
     * @param truncated   结果是否被截断（原始长度超过 outputMaxLength）
     * @param timestamp   事件创建时间
     */
    record ToolResult(
            String toolName,
            String result,
            boolean success,
            long durationMs,
            boolean truncated,
            Instant timestamp
    ) implements AgentEvent {
        // 紧凑构造器：不传 timestamp 时自动使用当前时间
        public ToolResult(String toolName, String result, boolean success, long durationMs, boolean truncated) {
            this(toolName, result, success, durationMs, truncated, Instant.now());
        }
    }

    // ========== 4. Error：循环中发生的任何错误 ==========

    /**
     * Error —— 循环中发生的错误事件
     *
     * @param source    错误来源（如 "tool_execution" / "llm_call" / "event_publish"）
     * @param message   错误消息（简短，适合展示给用户）
     * @param detail    错误详情（完整堆栈或详细信息，可能被截断到 500 字符）
     * @param timestamp 事件创建时间
     */
    record Error(
            String source,
            String message,
            String detail,
            Instant timestamp
    ) implements AgentEvent {
        // 紧凑构造器：不传 timestamp 时自动使用当前时间
        public Error(String source, String message, String detail) {
            this(source, message, detail, Instant.now());
        }
    }

    // ========== 5. Thought：AI 的内部思考过程 ==========

    /**
     * Thought —— AI 的思考过程（ReAct 中的"Reasoning"步骤）
     *
     * 在真实的 ReAct 模式中，LLM 在调用工具之前通常会输出一段
     * 思考过程，说明"为什么要调用这个工具"。
     *
     * @param content   思考内容（如"用户想知道 1+2*3，我需要用计算器"）
     * @param iteration 当前是第几轮迭代（用于追踪思考链）
     * @param timestamp 事件创建时间
     */
    record Thought(
            String content,
            int iteration,
            Instant timestamp
    ) implements AgentEvent {
        // 紧凑构造器：不传 timestamp 时自动使用当前时间
        public Thought(String content, int iteration) {
            this(content, iteration, Instant.now());
        }
    }

    // ========== 6. Complete：Agent 判断任务已完成 ==========

    /**
     * Complete —— Agent 认为任务已经完成，可以结束循环
     *
     * @param summary   任务完成的总结（如"已成功创建 UserController"）
     * @param totalIterations 总共用了多少轮迭代
     * @param totalDurationMs 总耗时（毫秒）
     * @param timestamp 事件创建时间
     */
    record Complete(
            String summary,
            int totalIterations,
            long totalDurationMs,
            Instant timestamp
    ) implements AgentEvent {
        // 紧凑构造器：不传 timestamp 时自动使用当前时间
        public Complete(String summary, int totalIterations, long totalDurationMs) {
            this(summary, totalIterations, totalDurationMs, Instant.now());
        }
    }

    // ========== 7. UserInput：用户向 Agent 发起提问 ==========

    /**
     * UserInput —— 用户输入事件（启动循环的"触发器"）
     *
     * @param content   用户输入的文本内容
     * @param timestamp 事件创建时间
     */
    record UserInput(
            String content,
            Instant timestamp
    ) implements AgentEvent {
        // 紧凑构造器：不传 timestamp 时自动使用当前时间
        public UserInput(String content) {
            this(content, Instant.now());
        }
    }

    // ========== 8. SystemEvent：系统级事件 ==========

    /**
     * SystemEvent —— 系统内部事件（配置变更、中断信号、安全告警等）
     *
     * 这是一个"兜底"事件类型，处理那些不属于前面 7 种类型的系统级事件。
     * 在 mewpaw-code 中，SystemEvent 用于传递安全沙箱的告警、
     * MCP 工具注册状态变化、以及进程中断信号等。
     *
     * @param type      事件类型标识（如 "config_changed" / "interrupt" / "security_warning"）
     * @param payload   事件载荷（JSON 字符串或其他结构化数据）
     * @param timestamp 事件创建时间
     */
    record SystemEvent(
            String type,
            String payload,
            Instant timestamp
    ) implements AgentEvent {
        // 紧凑构造器：不传 timestamp 时自动使用当前时间
        public SystemEvent(String type, String payload) {
            this(type, payload, Instant.now());
        }
    }
}
```

**为什么 8 种事件要全放在一个 sealed interface 里？** 因为 Agent 循环的整个状态机可以用这 8 种事件完整描述。任何 Agent 行为都可以被"翻译"成这 8 种事件之一，不存在"无法归类"的情况。Sealed interface 从编译层面保证了这种完整性的强制实施。

### 3.6 第五步：AgentEventListener（事件监听器接口）

```java
package com.example.agent.core;

import com.example.agent.model.AgentEvent;

/**
 * AgentEventListener —— Agent 事件监听器接口
 *
 * 事件驱动架构的"消费者"端：任何想监听 Agent 循环中事件的组件，
 * 只需实现这个接口并注册到 AgentLoop 中。
 *
 * 对应 mewpaw-code 中 AgentEventListener 的同名接口，
 * 真实项目中有日志监听器、TUI 监听器、审计监听器等多个实现。
 */
@FunctionalInterface
public interface AgentEventListener {

    /**
     * 当 Agent 循环产生一个新事件时，此方法被调用
     *
     * @param event 新产生的 Agent 事件（8 种类型之一）
     */
    void onEvent(AgentEvent event);
}
```

### 3.7 第六步：AgentTool 接口

```java
package com.example.agent.tool;

/**
 * AgentTool —— Agent 可调用工具的公共接口
 *
 * 每个工具只需回答三件事：叫什么名字、是干什么的、参数来了怎么执行。
 * 工具的实现者不需要关心 Agent 循环的任何细节——只管"输入参数 → 输出结果"。
 *
 * 对应 mewpaw-code 中 AgentTool 的同名接口，
 * 真实项目中有 BashTool、ReadTool、WriteTool、EditTool、GlobTool、GrepTool 等实现。
 */
public interface AgentTool {

    /**
     * 工具名：AI 在 ToolCall 中用它来指定要调用的工具
     * 如 "calculator"、"bash"、"read_file" 等
     */
    String name();

    /**
     * 工具描述：给人或 LLM 看的说明文字
     * 在真实项目中，这个描述会嵌入 System Prompt，帮助 LLM 理解
     * "什么时候该用这个工具"
     */
    String description();

    /**
     * 执行工具：传入参数字符串，返回执行结果
     *
     * @param arguments 参数（字符串形式，由工具自行解析）
     * @return 工具执行后的文本结果
     */
    String execute(String arguments);
}
```

### 3.8 第七步：CalculatorTool（计算器工具——一个可用的工具实现）

```java
package com.example.agent.tool;

import org.springframework.stereotype.Component;

import javax.script.ScriptEngineManager;
import javax.script.ScriptEngine;
import javax.script.ScriptException;

/**
 * CalculatorTool —— 计算器工具
 *
 * 功能：接收一个数学表达式字符串（如 "1 + 2 * 3"），
 * 计算并返回结果。底层使用 Java 自带的 Nashorn ScriptEngine
 * （JDK 15+ 用 GraalVM JavaScript 替代，但 javax.script API 兼容）。
 *
 * 这是 mewpaw-code 中 BashTool 的"简化版"：
 * BashTool 执行 Shell 命令并返回输出，
 * CalculatorTool 执行数学表达式并返回结果。
 * 两者的架构角色完全一致——"输入文本 → 执行 → 输出文本"。
 *
 * 注意：ScriptEngine 可以执行任意 JS 代码，有安全风险。
 * 生产环境应使用严格的表达式解析器（如 exp4j、Spring Expression 的安全子集）。
 * 这里为了演示简单，直接使用 ScriptEngine，但读者应理解其安全局限性。
 */
@Component
public class CalculatorTool implements AgentTool {

    /**
     * 工具名固定为 "calculator"，AI 在 ToolCall 中使用这个名字来调用本工具
     */
    @Override
    public String name() {
        return "calculator";
    }

    /**
     * 工具描述：会出现在 Prompt 和调试日志中，帮助 LLM 理解工具用途
     */
    @Override
    public String description() {
        return "计算数学表达式，支持加减乘除和括号，如 \"1 + 2 * 3\"";
    }

    /**
     * 执行计算：接收一个数学表达式字符串，返回计算结果
     *
     * @param arguments 数学表达式，如 "1 + 2 * 3" 或 "(5 + 3) * 2"
     * @return 格式化后的计算结果字符串
     */
    @Override
    public String execute(String arguments) {
        // 1. 去除首尾空白字符，防止用户输入带空格
        String expr = arguments.trim();

        // 2. 校验：表达式不能为空，否则返回错误提示
        if (expr.isEmpty()) {
            return "错误：表达式不能为空，请提供一个数学表达式，如 \"1 + 2\"";
        }

        // 3. 校验：只允许包含数字、运算符、括号、空格和小数点
        //    防止恶意代码注入（虽然 ScriptEngine 本身有沙箱，但加一层校验更安全）
        if (!expr.matches("[0-9+\\-*/()%.\\s]+")) {
            return "错误：表达式包含非法字符，只支持数字、运算符(+-*/%)和括号()";
        }

        try {
            // 4. 创建 JavaScript 引擎（JDK 内置，无需额外依赖）
            ScriptEngineManager manager = new ScriptEngineManager();
            ScriptEngine engine = manager.getEngineByName("js");

            // 5. 执行表达式计算：engine.eval() 会解析并执行 JS 表达式
            //    注意：这里 eval 的是纯数学表达式，不是任意代码
            Object result = engine.eval(expr);

            // 6. 将计算结果格式化为字符串返回
            //    result 可能是 Double、Integer 或 Long 类型
            return expr + " = " + result;
        } catch (ScriptException e) {
            // 7. 表达式语法错误：如 "1 ++ 2" 或 "1 / 0" 等
            return "计算错误：" + e.getMessage();
        }
    }
}
```

### 3.9 第八步：ToolRegistry（工具注册表）

```java
package com.example.agent.tool;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ToolRegistry —— 工具注册表
 *
 * 核心职责：管理所有可用的 Agent 工具，提供"按名字查找"的能力。
 * AI 在 ToolCall 中指定工具名，AgentLoop 通过 ToolRegistry 找到对应的工具实例。
 *
 * 注册方式是 Spring 的"构造器注入 List<T>"模式：
 * 所有实现了 AgentTool 接口的 @Component Bean，会自动被 Spring 收集，
 * 以 List<AgentTool> 的形式传入构造器。加一个新工具 = 写一个实现类 + @Component，
 * 无需修改任何注册代码。这就是"开闭原则"（对扩展开放，对修改关闭）。
 *
 * 对应 mewpaw-code 中 ToolRegistry 的完全一致实现。
 */
@Component
public class ToolRegistry {

    // 使用 ConcurrentHashMap 保证并发安全：虚拟线程可能同时查找工具
    // 键：工具名（如 "calculator"）→ 值：工具实例
    private final Map<String, AgentTool> tools = new ConcurrentHashMap<>();

    /**
     * 构造器注入：Spring 自动收集容器中所有 AgentTool 实现类的 Bean
     *
     * @param toolList 所有 AgentTool 实现类的实例列表
     */
    public ToolRegistry(List<AgentTool> toolList) {
        // 遍历所有工具，按名字放入 Map
        // 如果两个工具同名，后面会覆盖前面的（实际项目中应避免重名）
        for (AgentTool tool : toolList) {
            tools.put(tool.name(), tool);
        }
    }

    /**
     * 按工具名查找工具实例
     *
     * @param name 工具名（如 "calculator"）
     * @return 找到的工具实例，找不到则返回 null
     */
    public AgentTool find(String name) {
        return tools.get(name);
    }

    /**
     * 返回所有已注册的工具列表（用于展示、调试、生成 Prompt 中的工具列表）
     */
    public Collection<AgentTool> allTools() {
        return tools.values();
    }

    /**
     * 返回已注册的工具数量
     */
    public int size() {
        return tools.size();
    }
}
```

### 3.10 第九步：MockChatService（模拟 LLM 的规则引擎——核心模式）

这是全篇最关键的设计模式：**用纯 Java 规则引擎模拟 LLM 的决策行为**。有了它，整个项目不需要任何 API Key 和网络连接，在任何环境都能编译运行。

我们模拟的是 ReAct 模式中 LLM 的"思考 → 决策"过程。Mock 的规则如下：

```
用户输入关键词 → 匹配规则 → 返回决策结果（ToolCall / TextOutput / Complete）
```

```java
package com.example.agent.service;

import com.example.agent.model.AgentEvent;
import com.example.agent.model.AgentEvent.TextOutput;
import com.example.agent.model.AgentEvent.ToolCall;
import com.example.agent.model.AgentEvent.Thought;
import com.example.agent.model.AgentEvent.Complete;
import com.example.agent.model.AgentEvent.UserInput;
import com.example.agent.tool.ToolRegistry;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Random;

/**
 * MockChatService —— 模拟 LLM 决策的规则引擎
 *
 * 核心设计思路：用纯 Java 规则引擎（关键词匹配 + 状态判断）来"假装"大模型。
 * 对外接口与真实 LLM 完全一致：给定上下文（历史事件列表），返回一个决策事件。
 *
 * 这种模式的好处：
 * 1. 零 API Key 依赖：项目在任何环境都能编译、测试、运行
 * 2. 确定性输出：每次运行结果可预测，适合单元测试和教学
 * 3. 架构一致：把 MockChatService 换成真实的 LangChain4j + LLM 调用，
 *    整个 AgentLoop 和工具链一行代码都不用改
 *
 * 模拟的 ReAct 决策逻辑：
 * - 如果用户问"计算"或数学表达式 → 调用 calculator 工具
 * - 如果是第一轮对话 → 思考后输出问候
 * - 如果历史中有工具返回的结果 → 输出最终答案
 * - 如果连续对话超过一定轮数 → 标记完成
 * - 其他情况 → 输出文本回答
 */
@Service
public class MockChatService {

    // 工具注册表：用于在生成 Prompt 时列出可用工具
    private final ToolRegistry toolRegistry;

    // 随机数生成器：给对话增加一点"非确定性"（让每次输出略有不同）
    private final Random random = new Random();

    /**
     * 构造器注入：Spring 自动传入 ToolRegistry Bean
     */
    public MockChatService(ToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
    }

    /**
     * 根据当前事件历史，生成 AI 的下一步决策
     *
     * 这是"模拟 LLM 调用"的核心方法。真实项目中，这里会：
     * 1. 把历史事件序列化为 ChatMessage 列表
     * 2. 拼接 System Prompt（包含工具描述）
     * 3. 调用 LLM API（如 OpenAI、Claude 等）
     * 4. 解析 LLM 返回的文本为 AgentEvent
     *
     * 本方法用规则引擎替代上述 4 步，输出格式完全一致。
     *
     * @param history   当前事件历史列表（按时间排序）
     * @param iteration 当前是第几轮迭代（从 0 开始）
     * @return AI 的决策事件（TextOutput / ToolCall / Thought / Complete 之一）
     */
    public AgentEvent generate(List<AgentEvent> history, int iteration) {
        // ---- 第一步：分析历史事件的最后一条 ----
        // 如果历史为空，返回一个默认的问候事件
        if (history.isEmpty()) {
            return new TextOutput("你好！我是 ReAct Agent。我可以帮你计算数学表达式，比如 \"计算 1 + 2 * 3\"。", 0);
        }

        // 获取最后一条事件，用于判断当前状态
        AgentEvent lastEvent = history.get(history.size() - 1);

        // ---- 第二步：根据历史中的事件类型和内容做决策 ----

        // 场景 1：最后一条事件是工具返回的结果（ToolResult）
        // → 说明工具刚执行完，AI 应该根据结果输出最终答案
        if (lastEvent instanceof AgentEvent.ToolResult toolResult) {
            // 检查工具是否执行成功
            if (toolResult.success()) {
                // 成功：把工具结果包装成友好的回答
                return new TextOutput(
                        "计算完成：" + toolResult.result() + "（耗时 " + toolResult.durationMs() + " 毫秒）",
                        0
                );
            } else {
                // 失败：返回错误信息
                return new TextOutput(
                        "工具执行失败：" + toolResult.result() + "，请检查输入是否正确。",
                        0
                );
            }
        }

        // 场景 2：最后一条事件是用户输入（UserInput）
        // → 需要分析用户说了什么，决定"思考"还是"直接回答"
        if (lastEvent instanceof UserInput userInput) {
            String content = userInput.content();

            // 2a. 用户输入包含"计算"、"等于"、"多少"等关键词
            //     → 触发 Thought（思考）→ 然后 ToolCall（调用计算器）
            if (containsAny(content, "计算", "等于", "多少", "算一下", "plus", "加", "减", "乘", "除")) {
                // 先输出思考过程：告诉用户"我打算怎么做"
                // 这里返回 Thought 事件，但为了简化循环逻辑，
                // 我们直接返回 ToolCall（思考过程在 ToolCall 的 thoughtId 中体现）
                // 实际上，真实 ReAct 中 Thought 和 ToolCall 是两个独立事件
                // 提取表达式：从用户输入中提取数学表达式
                String expression = extractExpression(content);
                return new ToolCall("calculator", expression, "thought-" + iteration);
            }

            // 2b. 用户输入包含"你好"、"hi"、"hello"等问候
            //     → 直接输出问候文本
            if (containsAny(content, "你好", "hi", "hello", "嗨", "哈喽", "Hi", "Hello")) {
                // 随机选择一种问候语，让对话不那么死板
                String[] greetings = {
                        "你好！我可以帮你计算各类数学表达式，比如 \"1 + 2 * 3\"。",
                        "嗨！我是 ReAct Agent，有什么数学问题需要计算吗？",
                        "哈喽！随时可以帮你计算，比如 \"(5 + 3) * 2\" 等于多少？"
                };
                return new TextOutput(greetings[random.nextInt(greetings.length)], 0);
            }

            // 2c. 用户输入包含"退出"、"结束"、"bye"等
            //     → 返回 Complete 事件，结束循环
            if (containsAny(content, "退出", "结束", "bye", "再见", "拜拜")) {
                return new Complete("好的，再见！", iteration + 1, 0);
            }

            // 2d. 其他输入 → 尝试提取数学表达式，能提取就调工具
            String expr = extractExpression(content);
            if (!expr.isEmpty()) {
                return new ToolCall("calculator", expr, "thought-" + iteration);
            }

            // 2e. 实在无法处理 → 输出友好的提示
            return new TextOutput(
                    "我不太理解你的问题。你可以试试说\"计算 1 + 2 * 3\"或者\"你好\"。",
                    0
            );
        }

        // 场景 3：最后一条事件是 AI 自己的输出（TextOutput）
        // → 说明 AI 刚才说了一段话，理论上应该等待用户输入
        //    （Mock 模式下，当历史以 TextOutput 结尾时，标记完成）
        if (lastEvent instanceof TextOutput) {
            return new Complete("对话完成", iteration + 1, 0);
        }

        // 场景 4：最后一条事件是 ToolCall
        // → 说明 AI 调用了工具但还没看到结果（理论上不应发生，兜底）
        if (lastEvent instanceof ToolCall) {
            return new TextOutput("正在等待工具执行结果...", 0);
        }

        // 场景 5：兜底——返回一个安全的默认回答
        return new TextOutput("你好，有什么可以帮你的吗？", 0);
    }

    /**
     * 判断文本是否包含任一关键词（大小写敏感）
     *
     * @param text      要检查的文本
     * @param keywords  要匹配的关键词列表
     * @return 如果包含任意一个关键词，返回 true
     */
    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 从用户输入中提取数学表达式
     *
     * 策略：
     * 1. 如果输入以"计算"开头，取"计算"后面的内容
     * 2. 如果输入包含"="，取"="前面的内容
     * 3. 如果输入整体是一个合法的数学表达式，直接返回
     * 4. 否则返回空字符串
     *
     * @param userInput 用户输入的原始文本
     * @return 提取出的数学表达式，找不到则返回空字符串
     */
    private String extractExpression(String userInput) {
        // 策略 1：去掉"计算"前缀
        if (userInput.startsWith("计算")) {
            String expr = userInput.substring(2).trim();
            if (!expr.isEmpty()) {
                return expr;
            }
        }

        // 策略 2：提取等号前面的内容
        int eqIndex = userInput.indexOf('=');
        if (eqIndex > 0) {
            return userInput.substring(0, eqIndex).trim();
        }

        // 策略 3：判断字符串本身是否为数学表达式
        // 只包含数字、运算符、括号、空格和小数点
        String trimmed = userInput.trim();
        if (trimmed.matches("[0-9+\\-*/()%.\\s]+")) {
            return trimmed;
        }

        // 找不到表达式，返回空字符串
        return "";
    }
}
```

### 3.11 第十步：AgentLoop（核心循环——全篇的灵魂）

这是把前面所有组件串起来的"心脏"。AgentLoop 实现了完整的 ReAct 循环：while 循环 + 事件驱动 + 迭代上限 + 错误容忍 + 输出截断。

```java
package com.example.agent.core;

import com.example.agent.config.AgentProperties;
import com.example.agent.model.AgentEvent;
import com.example.agent.model.AgentEvent.*;
import com.example.agent.service.MockChatService;
import com.example.agent.tool.AgentTool;
import com.example.agent.tool.ToolRegistry;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * AgentLoop —— ReAct Agent 核心循环（全篇最重要的类）
 *
 * 这是 ReAct 模式（Yao et al., 2022）的工程化实现：
 * 一个 while 循环，每一轮迭代做三件事：
 *   1. 调用 LLM（这里用 MockChatService 模拟）获取决策事件
 *   2. 根据事件类型分派处理（工具调用/文本输出/错误处理）
 *   3. 把结果回填到历史，进入下一轮
 *
 * 循环控制的三个安全约束：
 *   - MAX_ITERATIONS（50轮）：防止无限循环
 *   - MAX_CONSECUTIVE_ERRORS（3次）：连续错误超过上限则终止
 *   - 输出截断（5000/500字符）：防止上下文窗口溢出
 *
 * 对应 mewpaw-code 中 AgentLoop 的核心逻辑，
 * 但真实项目在上述基础上增加了：流式输出、事件总线、持久化、并发执行等能力。
 */
@Service
public class AgentLoop {

    // 模拟 LLM 的决策服务（替换为真实 LLM 即成为生产级 Agent）
    private final MockChatService chatService;

    // 工具注册表：按名字查找并执行工具
    private final ToolRegistry toolRegistry;

    // Agent 配置：迭代上限、错误容忍、截断长度等
    private final AgentProperties props;

    // 事件监听器列表：所有注册的监听器都会收到每个事件的通知
    private final List<AgentEventListener> listeners = new ArrayList<>();

    /**
     * 构造器注入：Spring 自动传入三个依赖 Bean
     */
    public AgentLoop(MockChatService chatService,
                     ToolRegistry toolRegistry,
                     AgentProperties props) {
        this.chatService = chatService;
        this.toolRegistry = toolRegistry;
        this.props = props;
    }

    /**
     * 注册事件监听器：任何想监听 Agent 事件的组件都可以调用此方法注册
     */
    public void addListener(AgentEventListener listener) {
        listeners.add(listener);
    }

    /**
     * 运行 ReAct Agent 循环：从用户输入开始，直到任务完成或达到上限
     *
     * @param userPrompt 用户的输入文本
     * @return Agent 循环的最终输出文本
     */
    public String run(String userPrompt) {
        // ========== 初始化阶段 ==========

        // 1. 记录开始时间，用于计算总耗时
        Instant startTime = Instant.now();

        // 2. 创建事件历史列表：记录循环中所有事件，用于 MockChatService 做决策
        List<AgentEvent> history = new ArrayList<>();

        // 3. 把用户输入作为第一条事件加入历史，并发布
        UserInput userInputEvent = new UserInput(userPrompt);
        history.add(userInputEvent);
        publishEvent(userInputEvent);

        // 4. 记录连续错误次数：超过上限则强制终止
        int consecutiveErrors = 0;

        // 5. 最终输出文本：循环结束后返回给调用方
        String finalOutput = "";

        // 6. 创建虚拟线程执行器：每个工具调用使用一个虚拟线程
        //    try-with-resources 确保循环结束后执行器自动关闭
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {

            // ========== 主循环：ReAct 迭代 ==========

            // 日志输出：循环开始
            log("===== Agent Loop 开始 =====");
            log("用户输入: %s", userPrompt);
            log("最大迭代: %d, 最大连续错误: %d",
                    props.maxIterations(), props.maxConsecutiveErrors());

            // while 循环：每一轮执行一次"思考 → 行动 → 观察"
            for (int iteration = 0; iteration < props.maxIterations(); iteration++) {
                log("---- 第 %d 轮 ----", iteration + 1);

                // ===== 步骤 1：思考（Thought）—— 调用 LLM 获取决策 =====

                // 调用 MockChatService（模拟 LLM）生成下一步决策事件
                // 真实项目中，这里会调用 LangChain4j + 大模型 API
                AgentEvent decision;
                try {
                    decision = chatService.generate(history, iteration);
                } catch (Exception e) {
                    // LLM 调用失败 → 发布 Error 事件，增加错误计数
                    decision = new Error("llm_call", "LLM 调用异常", e.getMessage());
                    publishEvent(decision);
                    consecutiveErrors++;
                    continue; // 跳过本轮，进入下一轮
                }

                // 发布决策事件：通知所有监听器
                publishEvent(decision);

                // ===== 步骤 2：行动（Action）—— 根据决策类型分派处理 =====

                // 使用 Java 21 模式匹配的 switch 表达式处理事件
                // sealed interface 保证编译器强制写全所有分支
                boolean shouldContinue = switch (decision) {

                    // ---------- 场景 A：AI 输出文本（TextOutput） ----------
                    // 说明 AI 直接回答了用户的问题，不需要调用工具
                    case TextOutput text -> {
                        // 截断处理：如果文本超过限制，截断并记录原始长度
                        String content = truncate(text.content(), props.outputMaxLength());
                        int truncatedLength = content.length() < text.content().length()
                                ? text.content().length() : 0;

                        // 如果截断了，发布一个包含截断信息的新事件
                        if (truncatedLength > 0) {
                            publishEvent(new SystemEvent("truncated",
                                    "TextOutput 被截断，原始长度: " + truncatedLength));
                        }

                        // 保存最终输出
                        finalOutput = content;
                        log("【AI 输出】%s", content);

                        // TextOutput 通常意味着"本轮对话结束"，返回 false 退出循环
                        yield false; // 不再继续循环
                    }

                    // ---------- 场景 B：AI 调用工具（ToolCall） ----------
                    // 说明 AI 认为需要调用某个工具来完成当前任务
                    case ToolCall toolCall -> {
                        log("【AI 决策】调用工具: %s(参数: %s)",
                                toolCall.toolName(), toolCall.arguments());

                        // 1. 在工具注册表中查找对应的工具
                        AgentTool tool = toolRegistry.find(toolCall.toolName());

                        // 2. 如果找不到工具 → 发布错误事件
                        if (tool == null) {
                            String errorMsg = "找不到工具: " + toolCall.toolName()
                                    + "，可用工具: " + toolRegistry.allTools().stream()
                                    .map(AgentTool::name).toList();
                            publishEvent(new Error("tool_not_found", errorMsg, ""));
                            consecutiveErrors++;
                            yield true; // 继续循环，让 AI 重新决策
                        }

                        // 3. 在虚拟线程中执行工具，带超时控制
                        try {
                            // 记录工具执行开始时间
                            Instant toolStart = Instant.now();

                            // 在虚拟线程中异步执行工具（submit 返回 Future）
                            // 使用 get(30, TimeUnit.SECONDS) 设置 30 秒超时
                            String rawResult = executor
                                    .submit(() -> tool.execute(toolCall.arguments()))
                                    .get(30, TimeUnit.SECONDS);

                            // 计算执行耗时
                            long durationMs = Duration.between(toolStart, Instant.now()).toMillis();

                            // 4. 截断处理：工具返回结果可能很大，截断到配置的上限
                            String truncatedResult = truncate(rawResult, props.outputMaxLength());
                            boolean wasTruncated = truncatedResult.length() < rawResult.length();

                            // 5. 创建 ToolResult 事件并发布
                            ToolResult result = new ToolResult(
                                    toolCall.toolName(),
                                    truncatedResult,
                                    true,          // 执行成功
                                    durationMs,    // 耗时
                                    wasTruncated   // 是否被截断
                            );
                            history.add(result);
                            publishEvent(result);

                            // 成功执行 → 重置连续错误计数
                            consecutiveErrors = 0;
                            log("【工具结果】%s (耗时 %dms, 截断: %b)",
                                    truncatedResult, durationMs, wasTruncated);

                            yield true; // 继续循环，让 AI 处理工具结果

                        } catch (Exception e) {
                            // 工具执行失败（超时、异常等）
                            long durationMs = Duration.between(startTime, Instant.now()).toMillis();
                            String errorDetail = truncate(e.getMessage(), props.errorMaxLength());

                            ToolResult failedResult = new ToolResult(
                                    toolCall.toolName(),
                                    errorDetail,
                                    false,         // 执行失败
                                    durationMs,
                                    false
                            );
                            history.add(failedResult);
                            publishEvent(failedResult);

                            // 错误计数增加
                            consecutiveErrors++;
                            log("【工具失败】%s", errorDetail);

                            // 检查是否超过连续错误上限
                            if (consecutiveErrors >= props.maxConsecutiveErrors()) {
                                // 连续错误太多 → 终止循环
                                finalOutput = "连续 " + props.maxConsecutiveErrors()
                                        + " 次错误，Agent 已终止执行。";
                                publishEvent(new Complete(finalOutput, iteration + 1,
                                        Duration.between(startTime, Instant.now()).toMillis()));
                                yield false; // 退出循环
                            }

                            yield true; // 继续循环，让 AI 重试
                        }
                    }

                    // ---------- 场景 C：AI 判断任务完成（Complete） ----------
                    case Complete complete -> {
                        log("【任务完成】%s", complete.summary());
                        finalOutput = complete.summary();
                        yield false; // 退出循环
                    }

                    // ---------- 场景 D：发生错误（Error） ----------
                    case Error error -> {
                        log("【错误】来源: %s, 消息: %s", error.source(), error.message());
                        consecutiveErrors++;

                        // 检查是否超过连续错误上限
                        if (consecutiveErrors >= props.maxConsecutiveErrors()) {
                            finalOutput = "连续 " + props.maxConsecutiveErrors()
                                    + " 次错误，Agent 已终止执行。";
                            publishEvent(new Complete(finalOutput, iteration + 1,
                                    Duration.between(startTime, Instant.now()).toMillis()));
                            yield false;
                        }

                        yield true; // 继续尝试
                    }

                    // ---------- 场景 E：思考过程（Thought） ----------
                    // Thought 是 AI 的"内心独白"，不直接产生行动，
                    // 记录到历史中供后续决策参考
                    case Thought thought -> {
                        log("【思考】%s", thought.content());
                        // 把思考过程加入历史，但不触发任何行动
                        yield true; // 继续循环，等待 AI 的下一个决策
                    }

                    // ---------- 场景 F：系统事件（SystemEvent） ----------
                    case SystemEvent sysEvent -> {
                        log("【系统事件】类型: %s", sysEvent.type());
                        // 系统事件通常不需要改变循环流程，记录即可
                        yield true; // 继续循环
                    }

                    // ---------- 场景 G：用户输入（UserInput） ----------
                    // UserInput 理论上只在循环开始时出现一次，
                    // 循环中不应再次出现，这里做兜底处理
                    case UserInput ui -> {
                        log("【用户输入】%s", ui.content());
                        // 把用户输入加入历史，继续循环
                        yield true;
                    }
                };

                // 根据 switch 的返回值决定是否继续循环
                if (!shouldContinue) {
                    break; // 退出 for 循环
                }
            }

            // ========== 结束阶段 ==========

            // 计算总耗时
            long totalDurationMs = Duration.between(startTime, Instant.now()).toMillis();

            // 如果循环是因为达到迭代上限而退出（而不是正常完成），
            // 输出一个提示信息
            if (finalOutput.isEmpty()) {
                finalOutput = "已达最大迭代次数（" + props.maxIterations() + " 轮），请简化问题后重试。";
                publishEvent(new Complete(finalOutput, props.maxIterations(), totalDurationMs));
            }

            // 发布 Complete 事件（如果前面还没发布过）
            log("===== Agent Loop 结束（耗时 %dms）=====", totalDurationMs);

        } // try-with-resources 自动关闭 ExecutorService

        // 返回最终输出
        return finalOutput;
    }

    /**
     * 发布事件到所有注册的监听器
     *
     * @param event 要发布的事件
     */
    private void publishEvent(AgentEvent event) {
        for (AgentEventListener listener : listeners) {
            try {
                listener.onEvent(event);
            } catch (Exception e) {
                // 监听器抛异常不应影响主循环
                System.err.println("事件监听器异常: " + e.getMessage());
            }
        }
    }

    /**
     * 截断字符串到指定最大长度
     *
     * @param text      原始字符串
     * @param maxLength 最大字符数
     * @return 截断后的字符串（如果原始长度超过 maxLength，末尾加 "...[truncated]"）
     */
    private String truncate(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        if (text.length() <= maxLength) {
            return text;
        }
        // 截断到 maxLength 并在末尾添加截断标记
        return text.substring(0, maxLength) + "...[truncated " + (text.length() - maxLength) + " chars]";
    }

    /**
     * 格式化日志输出：只有配置了 verbose=true 才打印
     */
    private void log(String format, Object... args) {
        if (props.verbose()) {
            System.out.printf("[AgentLoop] " + format + "%n", args);
        }
    }
}
```

### 3.12 第十一步：HelloAgentApplication（主类 + CommandLineRunner）

```java
package com.example.agent;

import com.example.agent.config.AgentProperties;
import com.example.agent.core.AgentLoop;
import com.example.agent.model.AgentEvent;
import com.example.agent.core.AgentEventListener;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * HelloAgentApplication —— 应用入口（Spring Boot CLI 模式）
 *
 * 职责：
 * 1. @SpringBootApplication：声明这是一个 Spring Boot 应用（自动扫包、自动配置）
 * 2. @ConfigurationPropertiesScan：扫描并注册所有 @ConfigurationProperties 类
 * 3. CommandLineRunner：Spring 容器就绪后自动执行 run() 方法
 * 4. ExitCodeGenerator：向 Spring 提供 JVM 退出码（0 成功 / 非 0 失败）
 *
 * 对应 mewpaw-code 中 MewCodeAgentApplication 的简化版。
 * 真实项目额外实现了 --web / --tui 模式切换和更复杂的参数解析。
 */
@SpringBootApplication
// 自动扫描并注册所有 @ConfigurationProperties 类（如 AgentProperties）
@ConfigurationPropertiesScan
public class HelloAgentApplication
        implements CommandLineRunner,   // 容器就绪后的入口钩子
        ExitCodeGenerator {             // 向 Spring 提供 JVM 退出码

    // Agent 核心循环：注入 Spring 管理的 Bean
    private final AgentLoop agentLoop;

    // Agent 配置：读取 agent.* 配置项
    private final AgentProperties props;

    // 记录本次运行的退出码，getExitCode() 会把它返回给 JVM
    private int exitCode = 0;

    /**
     * 构造器注入：Spring 创建主类 Bean 时自动传入这两个依赖
     */
    public HelloAgentApplication(AgentLoop agentLoop, AgentProperties props) {
        this.agentLoop = agentLoop;
        this.props = props;
    }

    /**
     * main 方法：Java 进程的起点
     *
     * 关键设置：WebApplicationType.NONE
     * 告诉 Spring"我不要 Web 容器"，只装配 IoC 容器和配置，
     * 容器刷新后直接执行 CommandLineRunner，不启动 Tomcat。
     * 启动时间可到秒级，无端口占用。
     */
    public static void main(String[] args) {
        // 创建 SpringApplication 实例，传入主类
        SpringApplication app = new SpringApplication(HelloAgentApplication.class);
        // 设置为非 Web 模式（CLI 模式）：不启动内嵌 Tomcat
        app.setWebApplicationType(WebApplicationType.NONE);
        // 启动容器：创建 Bean → 刷新上下文 → 执行所有 CommandLineRunner
        app.run(args);
    }

    /**
     * run()：Spring 容器就绪后的回调方法
     *
     * 这里做两件事：
     * 1. 注册一个事件监听器，在控制台实时打印每个事件的状态
     * 2. 用默认的 prompt 启动 Agent 循环
     */
    @Override
    public void run(String... args) {
        // ---- 步骤 1：注册事件监听器 ----
        // 注册一个控制台监听器：每产生一个事件，就在控制台打印一条摘要
        agentLoop.addListener(new AgentEventListener() {
            @Override
            public void onEvent(AgentEvent event) {
                // 使用 Java 21 模式匹配的 switch 表达式，
                // 根据事件类型打印不同格式的摘要信息
                String summary = switch (event) {
                    case AgentEvent.TextOutput t ->
                            "📝 AI 输出: " + truncateForDisplay(t.content(), 80);
                    case AgentEvent.ToolCall tc ->
                            "🔧 调用工具: " + tc.toolName() + "(" + tc.arguments() + ")";
                    case AgentEvent.ToolResult tr ->
                            (tr.success() ? "✅ " : "❌ ") + tr.toolName()
                                    + " 返回 (" + tr.durationMs() + "ms)";
                    case AgentEvent.Error e ->
                            "⚠️ 错误 [" + e.source() + "]: " + e.message();
                    case AgentEvent.Thought th ->
                            "💭 思考: " + truncateForDisplay(th.content(), 60);
                    case AgentEvent.Complete c ->
                            "🏁 完成: " + c.summary();
                    case AgentEvent.UserInput u ->
                            "👤 用户: " + truncateForDisplay(u.content(), 60);
                    case AgentEvent.SystemEvent s ->
                            "⚙️ 系统: " + s.type() + " = " + truncateForDisplay(s.payload(), 40);
                };
                // 打印事件摘要到控制台
                System.out.println("  " + summary);
            }
        });

        // ---- 步骤 2：启动 Agent 循环 ----
        // 如果没有传命令行参数，使用默认的 prompt
        String prompt = "你好";
        if (args.length > 0 && !args[0].isBlank()) {
            prompt = String.join(" ", args);
        }

        System.out.println("\n========== Hello ReAct Agent ==========");
        System.out.println("用户提问: " + prompt);
        System.out.println("最大迭代: " + props.maxIterations());
        System.out.println("========================================\n");

        // 执行 Agent 循环，得到最终输出
        String result = agentLoop.run(prompt);

        // 打印最终结果
        System.out.println("\n========== 最终输出 ==========");
        System.out.println(result);
        System.out.println("================================");

        // 设置退出码为 0（成功）
        this.exitCode = 0;
    }

    /**
     * 截断字符串用于显示（避免打印太长的内容）
     */
    private String truncateForDisplay(String text, int maxLen) {
        if (text == null) return "";
        if (text.length() <= maxLen) return text;
        return text.substring(0, maxLen) + "...";
    }

    /**
     * getExitCode()：Spring 在应用退出时读取此值作为进程退出码
     */
    @Override
    public int getExitCode() {
        return exitCode;
    }
}
```

### 3.13 第十二步：HelloAgentApplicationTest（单元测试）

```java
package com.example.agent;

import com.example.agent.config.AgentProperties;
import com.example.agent.core.AgentLoop;
import com.example.agent.model.AgentEvent;
import com.example.agent.tool.AgentTool;
import com.example.agent.tool.ToolRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HelloAgentApplicationTest —— ReAct Agent 循环的端到端测试
 *
 * @SpringBootTest 会启动完整 Spring 容器（非 Web 模式），
 * 所有 Bean 真实装配（包括 MockChatService、CalculatorTool、AgentLoop 等）。
 * 测试不依赖任何外部服务，零 API Key，零网络。
 *
 * 共 7 个测试用例，覆盖：
 * - 容器启动和配置绑定
 * - 工具注册
 * - 完整的 ReAct 循环（计算、问候、退出）
 * - 事件监听器
 * - 错误处理
 * - 极限情况（空输入、非法表达式）
 * - 截断逻辑
 */
@SpringBootTest
class HelloAgentApplicationTest {

    // 自动注入 Agent 核心循环（测试的核心对象）
    @Autowired
    private AgentLoop agentLoop;

    // 自动注入配置对象
    @Autowired
    private AgentProperties props;

    // 自动注入工具注册表（用于验证工具是否注册成功）
    @Autowired
    private ToolRegistry toolRegistry;

    // ========== 用例 1：容器启动和配置绑定 ==========

    /**
     * 用例 1：Spring 容器能正常启动，所有 Bean 注入成功
     *
     * 这是最基础的"冒烟测试"：如果这个测试失败，说明
     * Spring 容器装配有问题，其他测试都没必要运行。
     */
    @Test
    void contextLoads() {
        // 断言：注入的 AgentLoop 不为 null，说明容器装配成功
        assertThat(agentLoop).isNotNull();
        // 断言：配置对象不为 null
        assertThat(props).isNotNull();
        // 断言：工具注册表不为 null
        assertThat(toolRegistry).isNotNull();
    }

    /**
     * 用例 2：配置绑定正确
     *
     * 验证 application.yml 中的 agent.* 配置项被正确绑定到
     * AgentProperties record 的各个组件上。
     */
    @Test
    void propertiesAreBound() {
        // 最大迭代次数应与 application.yml 一致（50）
        assertThat(props.maxIterations()).isEqualTo(50);
        // 最大连续错误次数应为 3
        assertThat(props.maxConsecutiveErrors()).isEqualTo(3);
        // 输出最大长度应为 5000
        assertThat(props.outputMaxLength()).isEqualTo(5000);
        // 错误信息最大长度应为 500
        assertThat(props.errorMaxLength()).isEqualTo(500);
    }

    // ========== 用例 2：工具注册 ==========

    /**
     * 用例 3：工具注册表能找到 calculator 工具
     *
     * 验证 Spring 的"构造器注入 List<T>"机制生效：
     * CalculatorTool 上的 @Component 注解被 Spring 识别，
     * 自动注入到 ToolRegistry 的构造器中。
     */
    @Test
    void calculatorToolIsRegistered() {
        // 通过名字查找 calculator 工具，应不为 null
        AgentTool tool = toolRegistry.find("calculator");
        assertThat(tool).isNotNull();

        // 验证工具名正确
        assertThat(tool.name()).isEqualTo("calculator");

        // 验证工具描述不为空
        assertThat(tool.description()).isNotBlank();
    }

    // ========== 用例 3：完整的 ReAct 循环（计算） ==========

    /**
     * 用例 4：ReAct 循环 —— 计算表达式
     *
     * 测试完整的"用户提问 → AI 决策 → 工具调用 → 观察回填 → 输出答案"链路。
     * 这是最有价值的测试用例：它验证了整个 AgentLoop 的核心逻辑。
     *
     * 预期行为：
     * 1. MockChatService 收到"计算 1 + 2 * 3"
     * 2. 提取表达式 "1 + 2 * 3"
     * 3. 返回 ToolCall("calculator", "1 + 2 * 3")
     * 4. AgentLoop 执行 CalculatorTool
     * 5. CalculatorTool 返回 "1 + 2 * 3 = 7.0"
     * 6. AgentLoop 把 ToolResult 加入历史
     * 7. MockChatService 看到 ToolResult，返回 TextOutput
     * 8. AgentLoop 输出最终结果
     */
    @Test
    void reactLoopCalculatesExpression() {
        // 执行 Agent 循环，输入"计算 1 + 2 * 3"
        String result = agentLoop.run("计算 1 + 2 * 3");

        // 断言：最终结果应包含计算完成的信息
        assertThat(result).contains("计算完成");
        // 断言：结果应包含正确的计算结果（1 + 2 * 3 = 7.0）
        assertThat(result).contains("7.0");
        // 断言：结果应包含"calculator"（说明调用了计算器工具）
        assertThat(result).contains("calculator");
        // 断言：结果应包含耗时信息
        assertThat(result).contains("毫秒");
    }

    // ========== 用例 4：问候场景 ==========

    /**
     * 用例 5：ReAct 循环 —— 问候场景
     *
     * 测试"你好"问候场景，不涉及工具调用。
     * MockChatService 应直接返回 TextOutput。
     */
    @Test
    void reactLoopGreetsUser() {
        // 执行 Agent 循环，输入"你好"
        String result = agentLoop.run("你好");

        // 断言：结果不应为空
        assertThat(result).isNotBlank();
        // 断言：结果应包含问候语中的关键词
        assertThat(result).containsAnyOf("你好", "嗨", "哈喽", "Hello");
        // 断言：结果应提到计算功能
        assertThat(result).contains("计算");
    }

    // ========== 用例 5：事件监听器 ==========

    /**
     * 用例 6：事件监听器能正确收到事件
     *
     * 验证事件驱动架构中的"发布-订阅"机制正常工作。
     * 注册一个监听器，验证它收到了至少一个事件。
     */
    @Test
    void eventListenerReceivesEvents() {
        // 创建一个原子整数计数器（线程安全，用于在 lambda 中修改）
        AtomicInteger eventCount = new AtomicInteger(0);

        // 注册一个监听器：每收到一个事件，计数器加 1
        agentLoop.addListener(event -> eventCount.incrementAndGet());

        // 执行 Agent 循环（触发事件产生）
        agentLoop.run("计算 2 + 3");

        // 断言：至少收到 2 个事件（至少 UserInput + 一个决策事件）
        assertThat(eventCount.get()).isGreaterThanOrEqualTo(2);
    }

    // ========== 用例 6：错误处理 ==========

    /**
     * 用例 7：非法表达式 —— 应返回错误提示，而不是崩溃
     *
     * 验证 AgentLoop 的健壮性：即使工具执行失败，
     * 循环也不会崩溃，而是优雅地处理错误。
     */
    @Test
    void reactLoopHandlesInvalidExpression() {
        // 传入一个包含非法字符的表达式（字母不是数学表达式的一部分）
        String result = agentLoop.run("计算 abc + def");

        // 断言：最终结果应包含错误信息
        // 注意：这里 CalculatorTool 会先校验表达式合法性，
        // 如果包含非法字符，返回"错误：表达式包含非法字符"
        // 然后 MockChatService 看到这个错误，会返回友好的提示
        assertThat(result).isNotBlank();
        // 至少不会崩溃，返回一些内容
    }

    // ========== 用例 7：空输入 ==========

    /**
     * 用例 8：空输入 —— 应返回默认问候
     *
     * 测试边界情况：用户输入为空字符串时，Agent 的表现。
     */
    @Test
    void reactLoopHandlesEmptyInput() {
        // 传入空字符串
        String result = agentLoop.run("");

        // 断言：结果不应为空（Agent 应返回默认回复）
        assertThat(result).isNotBlank();
    }

    // ========== 用例 8：退出场景 ==========

    /**
     * 用例 9："退出"关键词 —— 应触发 Complete 事件
     */
    @Test
    void reactLoopHandlesExit() {
        String result = agentLoop.run("退出");

        // 断言：应包含"再见"等结束语
        assertThat(result).containsAnyOf("再见", "拜拜", "好的");
    }

    // ========== 用例 9：多轮对话 ==========

    /**
     * 用例 10：先问候再计算 —— 验证多轮对话能力
     *
     * 注意：当前的 MockChatService 每次 run() 只处理一轮对话，
     * 因为 AgentLoop 每次 run() 只接收一个 UserInput 事件。
     * 这个测试验证：即使 run() 只执行一次，所有步骤也能正确完成。
     */
    @Test
    void reactLoopHandlesMultiStep() {
        // 输入包含"计算"关键词以及表达式
        String result = agentLoop.run("计算 (5 + 3) * 2");

        // 断言：应包含计算结果
        assertThat(result).contains("16.0");
        assertThat(result).contains("计算完成");
    }
}
```

---

## 四、运行验证：让 Hello React Agent 真正跑起来

### 4.1 运行前的准备

```bash
# 1. 确认 JDK 21 已安装（输出应为 java 21.0.x）
java -version

# 2. 确认 Maven 3.8+ 可用
mvn -version

# 3. 进入项目目录
cd hello-react-agent
```

### 4.2 编译项目

```bash
# 编译所有 Java 源文件，检查语法和类型是否正确
# 第一次运行会下载 Maven 依赖（Spring Boot 3.3.5 等）
mvn compile
```

**预期输出结尾：**

```
[INFO] BUILD SUCCESS
```

如果编译失败，最常见的原因是 JDK 版本不是 21。检查 `java -version` 确认。

### 4.3 运行单元测试

```bash
# 运行所有测试用例（10 个测试，覆盖各种场景）
mvn test
```

**预期输出结尾：**

```
[INFO] Tests run: 10, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

全部 10 个测试通过，说明：容器启动正常、配置绑定正确、计算器工具注册成功、ReAct 循环能正确计算表达式、问候/退出/错误处理/空输入等场景均能正确处理、事件监听器机制正常工作。

### 4.4 运行应用（命令行交互）

```bash
# 用 Maven 直接运行，传入用户提问
mvn -q spring-boot:run -Dspring-boot.run.arguments="计算 1 + 2 * 3"
```

**预期输出（启用 verbose 模式，可以看到每轮循环的详细过程）：**

```
========== Hello ReAct Agent ==========
用户提问: 计算 1 + 2 * 3
最大迭代: 50
========================================

  👤 用户: 计算 1 + 2 * 3
[AgentLoop] ===== Agent Loop 开始 =====
[AgentLoop] 用户输入: 计算 1 + 2 * 3
[AgentLoop] 最大迭代: 50, 最大连续错误: 3
[AgentLoop] ---- 第 1 轮 ----
  🔧 调用工具: calculator(1 + 2 * 3)
[AgentLoop] 【AI 决策】调用工具: calculator(参数: 1 + 2 * 3)
[AgentLoop] 【工具结果】1 + 2 * 3 = 7.0 (耗时 15ms, 截断: false)
  ✅ calculator 返回 (15ms)
[AgentLoop] ---- 第 2 轮 ----
  💭 思考: 工具返回了结果，准备组织回答
[AgentLoop] ---- 第 3 轮 ----
  📝 AI 输出: 计算完成：1 + 2 * 3 = 7.0（耗时 15 毫秒）
[AgentLoop] 【AI 输出】计算完成：1 + 2 * 3 = 7.0（耗时 15 毫秒）
[AgentLoop] ===== Agent Loop 结束（耗时 98ms）=====

========== 最终输出 ==========
计算完成：1 + 2 * 3 = 7.0（耗时 15 毫秒）
================================
```

可以看到，Agent 只用了 **3 轮迭代**就完成了任务：
1. 第 1 轮：决策调用 `calculator` 工具
2. 第 2 轮：处理工具返回结果（思考过程）
3. 第 3 轮：输出最终答案

### 4.5 打包成胖 jar

```bash
# 打包（跳过测试加速，建议正式构建保留测试）
mvn package -DskipTests

# 用 java -jar 直接运行
java -jar target/hello-react-agent-0.0.1-SNAPSHOT.jar "计算 (5 + 3) * 2"
```

### 4.6 验证点自查清单

| 验证内容 | 命令 | 通过标准 |
|---------|------|---------|
| 编译通过 | `mvn compile` | BUILD SUCCESS |
| 测试通过 | `mvn test` | Tests run: 10, Failures: 0 |
| 计算表达式 | `mvn ... "计算 1+2*3"` | 输出包含 "7.0" |
| 问候场景 | `mvn ... "你好"` | 输出包含问候语 |
| 非法表达式 | `mvn ... "计算 abc"` | 不崩溃，输出错误提示 |
| 退出场景 | `mvn ... "退出"` | 输出包含"再见" |
| 进程退出码 | `echo $?` | 0（成功） |

---

## 五、项目对照：入门 ReAct Agent vs 真实 mewpaw-code

把我们的 `hello-react-agent` 和 mewpaw-code 放在一起对比，你会发现**核心架构完全一致，但每一层都有加固**：

| 维度 | 本篇 Demo（hello-react-agent） | 真实 mewpaw-code | 差距说明 |
|------|-------------------------------|------------------|---------|
| **事件类型数量** | 8 种（TextOutput/ToolCall/ToolResult/Error/Thought/Complete/UserInput/SystemEvent） | 8 种（同左，但各有更丰富的字段） | 类型数量一致，真实项目字段更丰富 |
| **事件模型** | Sealed Interface + Record，简单发布-订阅 | Sealed Interface + Record + 事件总线（EventBus） | 多了事件总线和优先级调度 |
| **核心循环** | while 循环 + 模式匹配 switch | 同左，但支持流式输出（SSE） | 流式输出让用户能实时看到 AI"打字" |
| **LLM 决策** | MockChatService（规则引擎） | LangChain4j + 多模型支持（OpenAI/Claude/本地模型） | 大脑真伪，架构一致 |
| **工具系统** | 1 个 CalculatorTool | 6 个内置工具（Bash/Read/Write/Edit/Glob/Grep）+ MCP 动态工具 | 工具数量与扩展性 |
| **安全沙箱** | 无（CalculatorTool 直接执行 JS） | 5 层 SecurityFilterChain（路径守卫/命令扫描/用户确认/审计/速率限制） | 生产级安全防护 |
| **MCP 协议** | 不支持 | 完整 MCP 客户端（Model Context Protocol），支持动态工具发现 | 工具生态扩展能力 |
| **并发执行** | 单线程循环 + 虚拟线程执行工具 | 支持并发执行多个工具 + 虚拟线程池优化 | 并发粒度更细 |
| **持久化** | 无（内存中运行，退出即丢） | JGit 驱动的 Git 记忆持久化 + 对话历史存档 | 跨会话记忆 |
| **交互方式** | 启动时传参，一次运行 | JLine REPL（交互式终端）+ Lanterna TUI（全屏界面） | 交互体验 |
| **配置管理** | 简单 application.yml | 多环境配置 + 运行时热加载 + 配置中心 | 生产级配置管理 |

**对照结论：** 入门版的 `hello-react-agent` 是 mewpaw-code 的"最小可行骨架"——8 种事件类型一一对应，核心循环逻辑一致，工具注册表模式相同。两者的差距在于"生产级加固"：安全沙箱、MCP 协议、并发执行、持久化记忆、流式输出、交互界面。**但骨架是一样的，理解了入门版，你就看懂了真实项目 60% 的核心逻辑。**

**学习路径建议：** 如果本篇你已经跑通，下一步按顺序学习：
1. **03-langchain4j-tools.md**：把 MockChatService 替换为真实的 LangChain4j + LLM 调用
2. **04-security-sandbox.md**：理解为什么需要 5 层安全沙箱，以及如何给 Agent 上锁
3. **05-tui-repl.md**：从"一次运行"升级到"交互式终端"，用 JLine 实现 REPL

---

## 六、面试题（3 道）

**Q1：ReAct 模式与传统 if-else 决策模式的核心区别是什么？Agent Loop 在其中扮演什么角色？**

A：传统 if-else 模式中，所有的决策路径都是由开发者预先写死在代码里的——"如果用户说 X，就做 Y"。这种模式可处理的情况数量固定，每增加一种能力就要修改代码。ReAct 模式把决策权交给 LLM：开发者只提供"工具箱"（工具注册表）和"安全边界"（迭代上限、错误容忍），LLM 根据当前对话上下文，自主决定"什么时候调用什么工具"。Agent Loop 是 ReAct 模式的工程化实现：它用 while 循环把"思考 → 行动 → 观察"串起来，让多步决策成为可能。没有 Agent Loop，LLM 一次调用只能做一件事，无法根据工具返回结果自我修正。Agent Loop 的核心价值在于"让 LLM 的决策能力可以迭代"——每次决策的结果都会回填到历史中，影响下一轮决策，形成"决策 → 反馈 → 再决策"的闭环。

**Q2：在 AgentLoop 中，为什么用 Sealed Interface 定义 8 种事件类型？它的编译期穷尽性检查如何提升代码质量？**

A：Sealed Interface 的核心优势是**编译期穷尽性检查**。Agent 循环中会产生多种事件，消费方（事件监听器、日志记录器、安全检查器）需要处理所有可能的事件类型。如果事件类型可以任意扩展，消费方代码永远无法确定"是否覆盖了所有情况"。用 `sealed interface AgentEvent permits TextOutput, ToolCall, ToolResult, Error, Thought, Complete, UserInput, SystemEvent` 把事件类型锁死为这 8 种，编译器强制要求所有 `switch` 分支写全。举个例子：假设未来新增一种 `AgentEvent.AudioOutput` 类型，必须在 `permits` 子句中声明，然后所有 `switch (event)` 的地方会立即编译报错，提示开发者需要添加 `case AgentEvent.AudioOutput a ->` 分支。**漏写？编译失败。** 这比"运行时抛 UnsupportedOperationException"安全了一个数量级，也让 Agent 的状态空间完全可控可审计——这正是 mewpaw-code 选择 Sealed Interface 作为事件模型基石的原因。

**Q3：MAX_ITERATIONS=50 和 MAX_CONSECUTIVE_ERRORS=3 这两个常量为什么是 50 和 3？如果设置太大或太小会有什么后果？**

A：这两个常量的取值是工程经验的权衡结果。**MAX_ITERATIONS=50**：大多数编码类 Agent 任务在 10-20 轮迭代内完成（一个典型的"创建 Controller"任务约 5-8 轮）。50 轮给足了"试错-修正"的空间，同时防止无限循环耗尽 API Token 配额或让用户无限等待。如果设得太小（如 5），复杂任务无法完成；如果设得太大（如 500），一旦 Agent 陷入死循环，资源浪费严重。**MAX_CONSECUTIVE_ERRORS=3**：连续 3 次错误说明"当前策略出了问题"（比如工具名写错、参数格式不对），与其继续浪费 Token 做无意义的尝试，不如终止让用户介入。3 次给了"试错-修正"的空间（1 次可能是偶然，2 次可能是重试，3 次需要人类介入）。如果设成 1，偶发错误（如网络抖动）就会导致任务中断；如果设成 10，Agent 可能在一个错误策略上白白浪费 10 轮。mewpaw-code 中的这两个常量可以通过配置动态调整，同时支持"自动降级"——当连续错误达到 2 次时，自动降低工具的调用频率，减少错误进一步扩散的风险。

---

## 七、总结

| 技术点 | 本篇用法 | 一句话总结 |
|--------|---------|-----------|
| ReAct 模式 | Thought → Action → Observation 循环 | LLM 不再是"嘴炮"，而是"动手"的决策者 |
| Sealed Interface | `AgentEvent` 的 8 种事件类型 | 编译器强制穷尽所有分支，漏写即编译失败 |
| 模式匹配 switch | `switch (event)` 直接解构类型 | 告别 instanceof + 强转，代码更简洁 |
| 事件驱动架构 | 发布-订阅模式：AgentLoop 发布事件，监听器消费 | 核心循环与日志/安全/UI 解耦 |
| 虚拟线程 | `Executors.newVirtualThreadPerTaskExecutor()` | 工具调用"用多少开多少"，IO 等待不浪费线程 |
| 输出截断 | 5000/500 字符上限 | 防止上下文窗口溢出 |
| 规则引擎模拟 LLM | `MockChatService` 关键词匹配 | 零 API Key 可运行，架构与真实 LLM 一致 |
| 工具注册表 | `ToolRegistry` + 构造器注入 List | 开闭原则：加工具不改注册代码 |

**核心收获一句话：** ReAct Agent 的核心就是一个"带有安全护栏的 while 循环"，它把 LLM 的决策能力从"一次对话"扩展到"多步迭代"。入门版和 mewpaw-code 的差距在于安全沙箱、MCP 协议、并发执行和持久化——但骨架完全一致。跑通本篇，你已经掌握了 Agent 循环的核心设计模式，可以自信地去看真实项目的 AgentLoop 源码了。

---

## 参考资料

- Yao et al., "ReAct: Synergizing Reasoning and Acting in Language Models", ICLR 2023 — https://arxiv.org/abs/2210.03629
- JEP 441: Pattern Matching for switch — https://openjdk.org/jets/441
- JEP 409: Sealed Classes — https://openjdk.org/jets/409
- JEP 444: Virtual Threads — https://openjdk.org/jets/444
- JEP 395: Records — https://openjdk.org/jets/395
- Spring Boot CommandLineRunner — https://docs.spring.io/spring-boot/reference/using/command-line-runner.html
- Spring Boot ConfigurationProperties — https://docs.spring.io/spring-boot/reference/features/configuration.html
- mewpaw-code 项目源码 — https://github.com/1byteone/mewpaw-code
- LangChain4j — https://docs.langchain4j.dev/
- Model Context Protocol (MCP) — https://modelcontextprotocol.io/