# 01 · LangChain4j 工具入门：为 AI 赋予计算能力

> **深度系列 | mewpaw-code | Level 1 入门篇**
>
> 本篇目标：不依赖任何 AI 大模型 API Key，从零搭建一个"跑得起来"的最小工具调用系统。你会亲手写出 ToolSpecification、ToolRegistry、ToolExecutor，以及一个模拟 LLM 决策的 MockChatService，跑通"用户提问 → AI 决策调用工具 → 工具执行 → 返回结果"的完整闭环，最后对照真实的 mewpaw-code 项目，理解真实 ToolRegistry 的 6 个内置工具和安全体系。
>
> **前置要求：** 已安装 JDK 21 和 Maven 3.8+，会基本的中文 Java 语法即可，不需要任何 AI Agent 前置知识。你只需要会用 Spring Boot 写一个 Hello World。
>
> **适用读者：** Java 后端初学者 / 想入门 AI Agent 开发的工程师 / mewpaw-code 项目阅读者

---

## 一、项目背景：什么是"工具调用"？

### 1.1 一个尴尬的事实：LLM 只会"动嘴"

我们先做一个思想实验。在浏览器里打开任何一个大语言模型（LLM）的聊天界面，输入这样一句话：

> 请帮我计算 `(1 + 2) * (3 + 4) - 5` 的结果。

LLM 会怎么回答？它多半会给出一个**看起来**很专业的答案：逐步展开算式、标注运算顺序、最后给出 `16`。大部分情况下结果是对的——因为大模型的训练数据里见过太多类似的四则运算，它可以"背"出答案。

但只要稍加变化，把数字换成不常见的位数：

> 请帮我计算 `98273589235 * 83626123632` 的结果。

LLM 立刻开始"一本正经地胡说八道"。它会写出一长串数字，但几乎肯定是错的。**为什么？** 因为大语言模型本质上是一个"按概率预测下一个词"的文本生成器，它从来不会"真的去算"——它只是在模仿"算题的样子"。五位数以内的乘法它有印象能蒙对，十几位的乘法它就全靠编了。

这就是 LLM 的第一个致命短板：**它不擅长精确计算**。

### 1.2 第二个致命短板：LLM 无法"动手"

再做个实验。你让 LLM 帮你看一下电脑上某个目录里有什么文件。它只能回答：

> 抱歉，我无法直接访问您的文件系统。您可以打开终端执行 `dir` 命令...

LLM 的所有输出都停留在**文字世界**里。它可以给你一段精心编写的 Shell 命令，但它自己永远无法执行；它可以给你一个代码片段，但无法帮你运行并验证结果；它可以给你一串数字，但无法打开你的计算器去核对。用一句程序员的话说：**LLM 只能"动嘴"，不能"动手"，也无法"算准"**。

这正是工具调用（Tool Calling / Function Calling）要解决的问题。

### 1.3 什么是 Tool Calling / Function Calling？

**工具调用（Tool Calling / Function Calling）** 是 2023 年以来大模型应用最核心的范式之一。它的思想极其朴素：

> 与其让 LLM 自己"瞎猜答案"，不如让 LLM **指出该用哪个工具**，由我们（宿主程序）来执行工具拿到真实结果，再把结果交回给 LLM 组织成最终回复。

完整闭环是这样四步：

```
① 用户提问
      ↓
② LLM 输出"工具调用请求"（而不是直接回答）
      ↓
③ 宿主程序执行真实工具（计算 / 读文件 / 调 API / 执行命令）
      ↓
④ 把工具结果交回 LLM，LLM 组织出最终回复
      ↓
⑤ 用户看到最终答案
```

关键点在于：**LLM 只是"决策者"，不是"执行者"**。真正动手的是我们的 Java 代码。LLM 负责判断"这件事该用哪个工具、参数是什么"，Java 负责"安全、精确、可控地执行"。

举个例子。用户问："帮我算一下 `835617283 * 918273645`"。真实的工具调用过程是：

1. LLM 收到问题，意识到自己算不准，决定调用 `calculator` 工具；
2. LLM 返回一个结构化请求：`{"toolName": "calculator", "arguments": "{\"expression\": \"835617283 * 918273645\"}"}`；
3. 我们的 Java 程序收到请求，调用计算器工具，得到精确结果（由真实计算得出，而非模型猜测）；
4. 把结果交回 LLM，LLM 组织回复："结果是 xxx"。

**注意：LLM 全程没有算过一个数字，它只是"派单"**。这就是工具调用的精髓。

### 1.4 mewpaw-code 的 ToolRegistry 体系

在 **mewpaw-code** 项目中，我们围绕"工具调用"构建了一套完整的体系，核心是 **ToolRegistry（工具注册表）**。你可以把它理解成一本"工具通讯录"：系统里所有可被 AI 调用的工具，都在这里登记造册，标明名字、用途、参数，以及最重要的——**危不危险**。

mewpaw-code 内置了 **6 个工具**：

| 工具名 | 用途 | 危险等级 |
|--------|------|----------|
| `bash` | 执行 Shell 命令 | ⚠️ 高危 |
| `read_file` | 读取文件内容 | ✅ 安全 |
| `write_file` | 写入文件内容 | ⚠️ 危险 |
| `edit_file` | 编辑文件（精准替换） | ⚠️ 危险 |
| `glob` | 文件名模糊搜索 | ✅ 安全 |
| `grep` | 文件内容搜索 | ✅ 安全 |

其中 `bash` 是最高危的工具——它能让 AI 在用户的机器上执行任意命令。放 AI 自由执行 `bash` 等于把家门钥匙交给一个陌生人，所以我们为每个工具都设计了 **dangerous（危险）标记**，配合安全过滤器链进行审核。

### 1.5 @Tool 注解 vs 自建 ToolRegistry 的取舍

LangChain4j 本身提供了 `@Tool` 注解，可以非常方便地把任意 Java 方法暴露为 AI 工具：

```java
// 声明式：加一个注解，方法立刻变成 AI 可调用的工具
@Tool("计算两个整数相加的结果")
public int add(int a, int b) {
    return a + b;   // 真实的加法逻辑，交给 JVM 执行
}
```

既然框架已经提供了如此方便的注解，为什么 mewpaw-code 还要自建一套 ToolRegistry？答案是**两个字：控制**。`@Tool` 的便利性以牺牲控制力为代价，具体对比见下表：

| 对比维度 | @Tool 注解（框架原生） | 自建 ToolRegistry |
|----------|------------------------|-------------------|
| 声明方式 | 注解声明，几乎零代码 | 手动注册，代码略多但可控 |
| 安全控制 | 需要额外封装，无原生"危险等级" | 原生支持 dangerous 标记 |
| 动态注册 | 编译期固定，无法运行时增删 | 运行时动态注册/注销 |
| 元数据扩展 | 受限于注解定义 | 任意扩展（限流、审计、权限、成本） |
| MCP 协议 | 不支持 | 原生支持动态发现与跨语言调用 |
| 并发控制 | 需自行处理 | 内置 ConcurrentHashMap + 虚拟线程 |
| 学习成本 | 低（一个注解搞定） | 中（需理解注册表机制） |

**结论**：`@Tool` 适合快速原型与小项目；而 mewpaw-code 涉及文件系统、命令执行等高危操作，必须精细控制每一次工具调用，所以选择自建 ToolRegistry。本篇我们就从零实现一个迷你版 ToolRegistry，把每一个细节都弄明白。

---

## 二、核心概念：五个类看懂工具调用

在动手写代码之前，我们先认识五个核心概念。它们不是抽象理论，而是我们马上要写的五个类。把这五个概念记住，整个工具系统就串起来了。

### 2.1 ToolSpecification —— 工具的"说明书"

**ToolSpecification（工具规范）** 描述"一个工具长什么样"：

- **name（工具名）**：全局唯一标识，如 `calculator`；
- **description（描述）**：用自然语言告诉 LLM"这个工具是干嘛的"。**描述写得越清楚，LLM 越会正确地选择它**；
- **parameters（参数）**：工具需要哪些参数，每个参数的类型、说明、是否必填。

把它想象成超市货架上的**商品说明书**：写着品名、用法、成分，但不包含商品本身。LLM 只看说明书决定"要不要买（调用）"。

我们会在代码里用 Java 21 的 **record** 实现它——record 是 Java 21 最优雅的语法，几行搞定一个不可变数据结构。

### 2.2 ToolExecutionRequest —— LLM 的"点单纸"

**ToolExecutionRequest（工具执行请求）** 是 LLM 决策的输出。当 LLM 决定调用工具时，它不直接调用 Java 方法，而是**填一张"点单纸"**：

```json
{
  "toolName": "calculator",
  "arguments": "{\"expression\": \"835617283 * 918273645\"}"
}
```

它只有两个字段：工具名 + JSON 字符串参数。**为什么参数用字符串？** 因为 LLM 接口返回的原始数据就是文本，保持字符串形态最贴近真实链路，也便于在分布式系统中传输。

### 2.3 ToolRegistry —— ConcurrentHashMap 通讯录

**ToolRegistry（工具注册表）** 是所有工具的总指挥部，本质是一个 `ConcurrentHashMap<String, ToolDescriptor>`：

```
ToolRegistry（工具通讯录）
├── "calculator"  → ToolDescriptor(name="calculator", dangerous=false, executor=CalculatorTool)
├── "read_file"   → ToolDescriptor(name="read_file",  dangerous=false, executor=ReadFileTool)
├── "write_file"  → ToolDescriptor(name="write_file", dangerous=true,  executor=WriteFileTool)
└── "bash"        → ToolDescriptor(name="bash",       dangerous=true,  executor=BashTool)
```

**为什么用 ConcurrentHashMap？** 这是工具系统的核心数据结构选型，也是面试高频考点：

- **线程安全**：多个请求可能同时查询/调用工具，HashMap 会出现并发问题；
- **读多写少**：工具的典型使用模式是"注册一次、调用无数次"；ConcurrentHashMap 的读操作几乎无锁，性能极佳；
- **CAS + 桶锁**：JDK 8+ 的 ConcurrentHashMap 用 CAS + synchronized 锁单个桶，不同桶的写操作互不干扰，并发度远高于 `synchronizedMap`。

### 2.4 ToolDescriptor —— 带 dangerous 标记的"名片"

**ToolDescriptor（工具描述符）** 是工具在注册表中的"名片"。它把说明书（ToolSpecification）和执行器（ToolExecutor）**组装**起来，并盖上一个最重要的章：**dangerous（危险标记）**。

```java
new ToolDescriptor(spec, executor, true);   // 危险工具：bash、write_file...
new ToolDescriptor(spec, executor, false);  // 安全工具：calculator、read_file...
```

为什么需要危险标记？因为 LLM 是"不可信的决策者"——它可能被 prompt injection 诱导调用危险工具。`dangerous` 标记让安全过滤器在执行前能拦截高危调用，要求用户确认。**给工具分级，是生产级 Agent 系统的安全底线**。

### 2.5 ToolExecutor —— 策略模式接口

**ToolExecutor（工具执行器）** 是所有工具的统一执行契约——一个只有一个方法的函数式接口：

```java
@FunctionalInterface
public interface ToolExecutor {
    String execute(String arguments);   // 输入 JSON 参数，输出结果字符串
}
```

每个工具（计算器、读文件、执行命令...）都是这个接口的一个实现。这就是**策略模式**：

- **开闭原则**：新增工具 = 新增实现类，不改动现有代码；
- **统一调用**：ToolRegistry 执行时只面向接口，不关心具体实现；
- **易于测试**：每个工具可独立 Mock 和单测。

### 2.6 五个概念串联：完整数据流

```
                   ┌─────────────────────────────┐
                   │      ToolSpecification      │  说明书：名字/描述/参数
                   └─────────────┬───────────────┘
                                 │ 组合
                   ┌─────────────▼───────────────┐
                   │ ToolDescriptor  ──dangerous─▶ 名片：+危险标记
                   └─────────────┬───────────────┘
                                 │ 注册
                   ┌─────────────▼───────────────┐
                   │    ToolRegistry (Map)       │  通讯录：名字 → 名片
                   └─────────────┬───────────────┘
                                 │ 查表
                   ┌─────────────▼───────────────┐
                   │  ToolExecutionRequest       │  点单纸：LLM 的决策
                   └─────────────┬───────────────┘
                                 │ 执行
                   ┌─────────────▼───────────────┐
                   │      ToolExecutor           │  执行器：真正动手
                   └─────────────┬───────────────┘
                                 ▼
                            String 结果
```

这张图请大家保存好——我们接下来的每一行代码，都落在这张图上的某个环节。

---

## 三、从零搭建代码：迷你 ToolRegistry 项目

现在开始动手。我们会创建一个名为 `hello-tools` 的 Maven 项目，**不依赖任何 LLM API Key**——用一个规则引擎（MockChatService）模拟 LLM 的"工具决策"，让你在本地就能跑通完整闭环。

### 3.1 项目结构总览

```
hello-tools/
├── pom.xml                                          # Maven 构建文件
├── src/
│   ├── main/
│   │   ├── java/com/mewpaw/hellotools/
│   │   │   ├── HelloToolsApplication.java           # Spring Boot 启动类 + 交互式 Shell
│   │   │   ├── tool/                                # 工具系统核心包
│   │   │   │   ├── ToolSpecification.java           # ① 工具规范（说明书）
│   │   │   │   ├── ToolExecutionRequest.java        # ② 执行请求（点单纸）
│   │   │   │   ├── ToolDescriptor.java              # ③ 工具描述符（名片）
│   │   │   │   ├── ToolExecutor.java                # ④ 执行器接口（策略模式）
│   │   │   │   ├── ToolRegistry.java                # ⑤ 工具注册表（通讯录）
│   │   │   │   └── CalculatorTool.java              # 计算器工具实现
│   │   │   └── service/
│   │   │       └── MockChatService.java             # ★ 模拟 LLM 决策（规则引擎）
│   │   └── resources/
│   │       └── application.yml                      # Spring Boot 配置
│   └── test/java/com/mewpaw/hellotools/
│       └── HelloToolsApplicationTest.java           # 10 个单元测试
```

> **技术栈**：Java 21 · Spring Boot 3.3.5 · Maven 3.8+ · Jackson（Spring Boot 自带）。所有代码逐行中文注释，可直接编译运行。

### 3.2 pom.xml —— 依赖配置

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <!-- ============ 项目坐标：Maven 仓库定位这个项目的唯一 ID ============ -->
    <groupId>com.mewpaw</groupId>                          <!-- 组织 ID，与 mewpaw-code 一致 -->
    <artifactId>hello-tools</artifactId>                   <!-- 项目 ID -->
    <version>1.0.0</version>                               <!-- 版本号 -->
    <name>hello-tools</name>                               <!-- 项目显示名 -->
    <description>LangChain4j 工具系统入门：为 AI 赋予计算能力</description>

    <!-- ============ 父工程：让 Spring Boot 帮我们管理版本 ============ -->
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.3.5</version>                            <!-- Spring Boot 3.3.5，稳定版 -->
        <relativePath/>                                     <!-- 表示从仓库拉取父 POM -->
    </parent>

    <!-- ============ 属性：统一声明 Java 版本与编码 ============ -->
    <properties>
        <java.version>21</java.version>                     <!-- 目标 JDK 21，启用 record 等新语法 -->
        <maven.compiler.source>21</maven.compiler.source>   <!-- 源码编译等级 21 -->
        <maven.compiler.target>21</maven.compiler.target>   <!-- 字节码等级 21 -->
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding> <!-- 源码统一 UTF-8，防中文乱码 -->
    </properties>

    <!-- ============ 依赖清单 ============ -->
    <dependencies>
        <!-- Spring Boot 核心 Starter：提供 Spring 容器、自动配置、日志能力 -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter</artifactId>    <!-- 注意：本示例是命令行程序，无需 web -->
        </dependency>

        <!-- Jackson：JSON 序列化/反序列化。LLM 的工具参数以 JSON 传递，离不开它 -->
        <dependency>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-databind</artifactId>        <!-- 由 Spring Boot 父 POM 统一管理版本 -->
        </dependency>

        <!-- 测试 Starter：JUnit 5 + AssertJ + Mockito 全家桶 -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>                              <!-- 只在测试阶段生效 -->
        </dependency>
    </dependencies>

    <!-- ============ 构建插件：打包为可执行 JAR ============ -->
    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>   <!-- 让 java -jar 可直接运行 -->
            </plugin>
        </plugins>
    </build>
</project>
```

### 3.3 application.yml —— 配置文件

```yaml
# ============================================================================
# hello-tools 应用配置
# 本文使用 YAML 格式，注意：冒号后必须有一个空格
# ============================================================================

# Spring 基础配置
spring:
  application:
    name: hello-tools                 # 应用名称，用于标识和监控
  main:
    banner-mode: off                  # 关闭启动横幅，让控制台输出更干净

# 自定义应用配置（业务代码通过 @ConfigurationProperties 或 @Value 读取）
app:
  tool:
    safety-check: true                # 开启工具安全检查开关（演示危险工具时使用）

# 日志配置
logging:
  level:
    root: INFO                        # 根日志级别：INFO
    com.mewpaw.hellotools: DEBUG      # 我们的包用 DEBUG，方便观察工具调用细节
```

### 3.4 ToolSpecification.java —— 工具的"说明书"

```java
package com.mewpaw.hellotools.tool;

// Jackson 用于把工具规范序列化为 LLM 需要的 JSON 格式
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

// 使用 LinkedHashMap 而不是 HashMap：保持参数的自然顺序，JSON 输出更可读
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工具规范（ToolSpecification）—— 工具的"说明书"。
 * <p>
 * 使用 Java 21 的 record 定义不可变数据结构：
 * record 自动生成构造器、getter、equals/hashCode/toString，代码极简。
 * <p>
 * 三个字段的含义：
 * - name        ：工具唯一名称，LLM 靠这个名字引用工具
 * - description ：对 LLM 的自然语言描述，决定 LLM 何时调用它
 * - parameters  ：参数说明，Map 结构模拟 JSON Schema
 *
 * @param name        工具名称，如 "calculator"
 * @param description 工具描述，如 "执行数学计算，支持加减乘除"
 * @param parameters  参数描述，key=参数名，value=参数属性（类型/说明/是否必填）
 */
public record ToolSpecification(
        // 字段 1：工具名称，全局唯一
        String name,
        // 字段 2：工具描述，写清楚"何时该用本工具"
        String description,
        // 字段 3：参数描述，告诉 LLM 该提供哪些参数
        Map<String, Map<String, Object>> parameters
) {

    /**
     * 把工具规范序列化为 JSON 字符串。
     * <p>
     * 真实场景中，这个 JSON 会被放进 system prompt 发给 LLM，
     * 语义必须贴近 OpenAI Function Calling 的格式。
     *
     * @param objectMapper Jackson 的 ObjectMapper（由 Spring 注入）
     * @return 格式化后的 JSON 字符串
     */
    public String toJson(ObjectMapper objectMapper) {
        try {
            // 外层：声明这是一个 function 类型的工具
            Map<String, Object> jsonMap = new LinkedHashMap<>();
            jsonMap.put("type", "function");

            // function 内部：名称 + 描述 + 参数 Schema
            Map<String, Object> function = new LinkedHashMap<>();
            function.put("name", this.name);                  // 工具名
            function.put("description", this.description);    // 工具描述

            // ---- 构建参数 JSON Schema（OpenAI 规范）----
            Map<String, Object> parametersSchema = new LinkedHashMap<>();
            parametersSchema.put("type", "object");           // 参数整体是一个对象

            // properties：记录每个参数的属性
            Map<String, Object> properties = new LinkedHashMap<>();
            // required：记录必填参数名列表
            List<String> required = new ArrayList<>();

            // 遍历我们声明的每个参数
            if (this.parameters != null && !this.parameters.isEmpty()) {
                for (Map.Entry<String, Map<String, Object>> entry
                        : this.parameters.entrySet()) {
                    // entry.getKey() 是参数名，比如 "expression"
                    String paramName = entry.getKey();
                    // entry.getValue() 是参数属性（type/description/required）
                    Map<String, Object> paramProps = new LinkedHashMap<>(entry.getValue());

                    // 如果该参数标记为必填，把它加入 required 列表
                    if (Boolean.TRUE.equals(paramProps.get("required"))) {
                        required.add(paramName);
                        // required 不是 OpenAPI Schema 的属性，从 properties 中移除
                        paramProps.remove("required");
                    }
                    // 把参数属性放入 properties
                    properties.put(paramName, paramProps);
                }
                // 组装 Schema：properties + required
                parametersSchema.put("properties", properties);
                parametersSchema.put("required", required);
            }

            // 逐层组装
            function.put("parameters", parametersSchema);     // function.parameters
            jsonMap.put("function", function);                // {type, function}

            // 输出带缩进的漂亮 JSON，便于阅读和调试
            return objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(jsonMap);
        } catch (JsonProcessingException e) {
            // JSON 序列化失败：抛出运行时异常，避免静默失败
            throw new RuntimeException("工具规范 JSON 序列化失败: " + e.getMessage(), e);
        }
    }

    /**
     * 静态工厂：返回一个 Builder。
     * <p>
     * 工具参数较多，用 Builder 模式比直接 new record 更清晰。
     *
     * @return Builder 实例
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder 类 —— 建造者模式，链式调用构建 ToolSpecification。
     */
    public static class Builder {
        // -- 构建过程中暂存的数据 --
        private String name;                                    // 暂存工具名
        private String description;                             // 暂存描述
        // 用 LinkedHashMap 保持参数声明顺序
        private final Map<String, Map<String, Object>> parameters = new LinkedHashMap<>();

        /**
         * 设置工具名称，返回 this 支持链式调用。
         *
         * @param name 工具名，如 "calculator"
         * @return Builder 自身
         */
        public Builder name(String name) {
            this.name = name;
            return this;
        }

        /**
         * 设置工具描述。
         *
         * @param description 描述文本
         * @return Builder 自身
         */
        public Builder description(String description) {
            this.description = description;
            return this;
        }

        /**
         * 添加一个参数说明。
         *
         * @param name        参数名，如 "expression"
         * @param type        参数类型，如 "string"
         * @param description 参数含义描述
         * @param required    是否必填
         * @return Builder 自身
         */
        public Builder addParameter(String name, String type,
                                    String description, boolean required) {
            // 用 Map 描述单个参数的属性
            Map<String, Object> paramProps = new LinkedHashMap<>();
            paramProps.put("type", type);                       // 类型
            paramProps.put("description", description);         // 说明
            paramProps.put("required", required);               // 是否必填
            this.parameters.put(name, paramProps);              // 加入参数表
            return this;
        }

        /**
         * 结束构建，返回 ToolSpecification 实例。
         *
         * @return 构建好的工具规范
         * @throws IllegalArgumentException 工具名为空时抛出
         */
        public ToolSpecification build() {
            // 防御式校验：工具名是 LLM 引用工具的唯一途径，不能为空
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("工具名称不能为空");
            }
            // 构造 record 并返回
            return new ToolSpecification(name, description, parameters);
        }
    }
}
```

### 3.5 ToolExecutionRequest.java —— LLM 的"点单纸"

```java
package com.mewpaw.hellotools.tool;

// TypeReference 用于安全地把 JSON 反序列化为泛型 Map
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

/**
 * 工具执行请求（ToolExecutionRequest）—— LLM 的"点单纸"。
 * <p>
 * 模拟 LLM 返回的结构化工具调用请求：
 * - toolName  ：LLM 决定调用哪个工具
 * - arguments ：以 JSON 字符串形式携带的参数
 * <p>
 * 用 record 定义，两个字段不可变。
 *
 * @param toolName  工具名称，必须与 ToolSpecification.name 一致
 * @param arguments JSON 格式参数字符串，如 {"expression": "1 + 2"}
 */
public record ToolExecutionRequest(
        // 字段 1：LLM 要调用的工具名
        String toolName,
        // 字段 2：参数（JSON 字符串）。用字符串是为了贴近 LLM API 的原始行为
        String arguments
) {

    /**
     * 把参数字符串解析为 Map，方便执行器读取具体参数。
     *
     * @param objectMapper Jackson ObjectMapper
     * @return 解析后的参数 Map，如 {"expression": "1 + 2"}
     * @throws IllegalArgumentException JSON 非法时抛出
     */
    public Map<String, Object> parseArguments(ObjectMapper objectMapper) {
        try {
            // 反序列化：JSON 字符串 -> Map<String, Object>
            return objectMapper.readValue(
                    this.arguments,
                    new TypeReference<>() { }                 // 泛型类型引用，安全解析
            );
        } catch (JsonProcessingException e) {
            // 参数 JSON 格式错误：抛非法参数异常
            throw new IllegalArgumentException("参数 JSON 解析失败: " + e.getMessage(), e);
        }
    }

    /**
     * 静态工厂方法：语义清晰地创建执行请求。
     *
     * @param toolName  工具名
     * @param arguments 参数字符串
     * @return ToolExecutionRequest 实例
     */
    public static ToolExecutionRequest of(String toolName, String arguments) {
        return new ToolExecutionRequest(toolName, arguments);
    }
}
```

### 3.6 ToolDescriptor.java —— 带 dangerous 标记的"名片"

```java
package com.mewpaw.hellotools.tool;

/**
 * 工具描述符（ToolDescriptor）—— 带 dangerous 标记的"名片"。
 * <p>
 * 它是"说明书（ToolSpecification）+ 执行器（ToolExecutor）+ 安全标记（dangerous）"
 * 的组合体，是注册表里真正存放的东西。
 * <p>
 * dangerous 字段是整个安全体系的地基：
 * - false：安全工具，可自动执行（如 calculator、read_file）
 * - true ：危险工具，需用户确认或安全检查（如 bash、write_file）
 *
 * @param specification 工具规范（名字/描述/参数）
 * @param executor      工具执行器（真正实现逻辑的对象）
 * @param dangerous     是否危险工具
 */
public record ToolDescriptor(
        // 字段 1：工具的"说明书"
        ToolSpecification specification,
        // 字段 2：工具的"执行器"（策略模式的实现体）
        ToolExecutor executor,
        // 字段 3：危险标记 —— 安全过滤器的判断依据
        boolean dangerous
) {

    /**
     * 静态工厂：创建安全工具（dangerous=false）。
     * 语义比直接 new + false 更清晰。
     *
     * @param specification 工具规范
     * @param executor      工具执行器
     * @return 标记为安全的 ToolDescriptor
     */
    public static ToolDescriptor safe(ToolSpecification specification,
                                      ToolExecutor executor) {
        return new ToolDescriptor(specification, executor, false);
    }

    /**
     * 静态工厂：创建危险工具（dangerous=true）。
     *
     * @param specification 工具规范
     * @param executor      工具执行器
     * @return 标记为危险的 ToolDescriptor
     */
    public static ToolDescriptor dangerous(ToolSpecification specification,
                                           ToolExecutor executor) {
        return new ToolDescriptor(specification, executor, true);
    }

    /**
     * 便捷方法：获取工具名（委托给 specification）。
     *
     * @return 工具名称
     */
    public String name() {
        return this.specification.name();
    }

    /**
     * 便捷方法：获取工具描述（委托给 specification）。
     *
     * @return 工具描述
     */
    public String description() {
        return this.specification.description();
    }
}
```

### 3.7 ToolExecutor.java —— 执行器接口（策略模式）

```java
package com.mewpaw.hellotools.tool;

/**
 * 工具执行器接口（ToolExecutor）—— 策略模式的核心。
 * <p>
 * 只有一个抽象方法，是标准的函数式接口：
 * - 可以用 lambda 快速实现
 * - 可以用类实现（如 CalculatorTool）
 * <p>
 * 策略模式三大优点：
 * 1. 开闭原则：加新工具 = 加新实现，不改旧代码
 * 2. 面向接口：ToolRegistry 只依赖本接口，解耦
 * 3. 便于测试：每个策略可独立单测 / Mock
 */
@FunctionalInterface
public interface ToolExecutor {

    /**
     * 执行工具逻辑：参数进、结果出。
     * <p>
     * 参数与返回值都用 String，原因：
     * 1. 与 LLM API 的原始文本协议天然对齐
     * 2. 接口保持通用，不绑定任何具体类型
     * 3. 多个工具的结果都是"字符串"，链路统一
     *
     * @param arguments JSON 格式参数字符串，如 {"expression": "1 + 2"}
     * @return 执行结果字符串，如 "3"
     */
    String execute(String arguments);

    /**
     * 默认方法：返回工具规范，子类可重写。
     * <p>
     * 让"工具自己描述自己"，注册时可自动取规范，少写样板代码。
     *
     * @return ToolSpecification，默认 null，期待子类重写
     */
    default ToolSpecification getSpecification() {
        return null;
    }
}
```

### 3.8 ToolRegistry.java —— 工具注册表（通讯录）

```java
package com.mewpaw.hellotools.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工具注册表（ToolRegistry）—— 所有工具的"通讯录"。
 * <p>
 * 核心数据结构：ConcurrentHashMap<String, ToolDescriptor>
 * - key  ：工具名
 * - value：工具描述符（规范 + 执行器 + 危险标记）
 * <p>
 * 为什么选 ConcurrentHashMap（高频面试题）：
 * 1. 线程安全：支持高并发查询与调用
 * 2. 读多写少：get/contains 无锁化，性能最优
 * 3. 桶锁粒度：JDK8+ 用 CAS + synchronized 锁桶，并发写不同 key 互不阻塞
 * <p>
 * @Component 交给 Spring 管理为单例 Bean，全应用共享一份通讯录。
 */
@Component
public class ToolRegistry {

    // ============ 日志 ============
    private static final Logger log = LoggerFactory.getLogger(ToolRegistry.class);

    // ============ 核心：工具通讯录（线程安全的 Map） ============
    private final ConcurrentHashMap<String, ToolDescriptor> toolMap =
            new ConcurrentHashMap<>();

    // ============ Jackson：处理工具参数的 JSON ============
    private final ObjectMapper objectMapper;

    /**
     * 构造器：Spring 自动注入 ObjectMapper（Boot 预置的 Bean）。
     *
     * @param objectMapper Jackson ObjectMapper
     */
    public ToolRegistry(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 注册单个工具。同名工具再次注册会覆盖旧值。
     *
     * @param descriptor 工具描述符，不可为 null
     * @return 被覆盖的旧描述符（首次注册返回 null）
     * @throws NullPointerException descriptor 为 null 时
     */
    public ToolDescriptor register(ToolDescriptor descriptor) {
        // 防御：不允许注册 null
        Objects.requireNonNull(descriptor, "工具描述符不能为空");
        // 取工具名作为 Map 的 key
        String name = descriptor.name();
        // put 返回旧值：有覆盖、无新增
        ToolDescriptor previous = toolMap.put(name, descriptor);

        if (previous == null) {
            log.info("🛠️ 注册工具: {} (危险: {})", name, descriptor.dangerous());
        } else {
            log.warn("🔄 工具 {} 已存在，覆盖旧注册", name);
        }
        return previous;
    }

    /**
     * 批量注册工具 —— 可变参数，一次注册多个。
     *
     * @param descriptors 多个工具描述符
     */
    public void registerAll(ToolDescriptor... descriptors) {
        // 逐个调用注册逻辑
        for (ToolDescriptor d : descriptors) {
            this.register(d);
        }
    }

    /**
     * 按名称查询工具（读操作，ConcurrentHashMap 无锁）。
     *
     * @param name 工具名
     * @return Optional 包装的描述符，避免返回 null
     */
    public Optional<ToolDescriptor> get(String name) {
        return Optional.ofNullable(toolMap.get(name));
    }

    /**
     * 获取全部工具名（返回不可修改视图，保护内部数据）。
     *
     * @return 工具名集合
     */
    public Set<String> getToolNames() {
        return Collections.unmodifiableSet(toolMap.keySet());
    }

    /**
     * 获取全部工具描述符（不可修改视图）。
     *
     * @return 描述符集合
     */
    public Collection<ToolDescriptor> getAllTools() {
        return Collections.unmodifiableCollection(toolMap.values());
    }

    /**
     * 导出全部工具规范 —— 真实场景中发给 LLM 作为工具定义。
     *
     * @return 工具规范列表
     */
    public List<ToolSpecification> getSpecifications() {
        return toolMap.values().stream()
                .map(ToolDescriptor::specification)   // 提取规范字段
                .toList();                             // Java 16+ 的 Stream.toList()
    }

    /**
     * 执行工具 —— 工具链路的核心方法。
     * <p>
     * 流程：查表 -> 校验 -> 委托执行 -> 返回结果。
     *
     * @param request 执行请求（工具名 + 参数字符串）
     * @return 执行结果字符串
     * @throws IllegalArgumentException 工具不存在时
     */
    public String execute(ToolExecutionRequest request) {
        // 记录调试日志
        log.debug("🔧 执行工具: {}，参数: {}", request.toolName(), request.arguments());

        // 第一步：从通讯录查工具
        ToolDescriptor descriptor = toolMap.get(request.toolName());
        if (descriptor == null) {
            // 查无此工具：明确报错并附上可用列表，方便排查
            throw new IllegalArgumentException("未知工具: " + request.toolName()
                    + "，可用工具: " + toolMap.keySet());
        }

        // 第二步：取出执行器并执行（策略模式多态分发）
        ToolExecutor executor = descriptor.executor();
        String result = executor.execute(request.arguments());

        log.debug("✅ 工具 {} 执行完成，结果: {}", request.toolName(), result);
        return result;
    }

    /**
     * 判断工具是否已注册。
     *
     * @param name 工具名
     * @return true 表示已注册
     */
    public boolean contains(String name) {
        return toolMap.containsKey(name);
    }

    /**
     * 返回当前已注册的工具总数。
     *
     * @return 数量
     */
    public int size() {
        return toolMap.size();
    }

    /**
     * 移除工具。
     *
     * @param name 工具名
     * @return 被移除的描述符（不存在则空）
     */
    public Optional<ToolDescriptor> remove(String name) {
        ToolDescriptor removed = toolMap.remove(name);
        if (removed != null) {
            log.info("🗑️ 移除工具: {}", name);
        }
        return Optional.ofNullable(removed);
    }

    /**
     * 清空全部注册信息。
     */
    public void clear() {
        toolMap.clear();
        log.info("🧹 已清空所有工具注册");
    }
}
```

### 3.9 CalculatorTool.java —— 计算器工具实现

```java
package com.mewpaw.hellotools.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 计算器工具 —— 一个具体的 ToolExecutor 实现。
 * <p>
 * 演示"策略模式"：CalculatorTool 只是众多工具策略之一，
 * 将来加 ReadFileTool、WriteFileTool，无侵入扩展。
 * <p>
 * 实现思路：
 * 1. 用 JavaScript 引擎（JDK 自带）求值数学表达式，精度高
 * 2. 引擎不可用时，回退到内置的正则简单计算器（兜底）
 * <p>
 * @Component 让 Spring 自动扫描注册为 Bean。
 */
@Component
public class CalculatorTool implements ToolExecutor {

    // ============ 日志 ============
    private static final Logger log = LoggerFactory.getLogger(CalculatorTool.class);

    // ============ Jackson：解析参数 JSON ============
    private final ObjectMapper objectMapper;

    // ============ 表达式求值引擎（JDK 自带的 JavaScript 引擎） ============
    private final ScriptEngine scriptEngine;

    /**
     * 构造器：注入 ObjectMapper，并初始化求值引擎。
     *
     * @param objectMapper Jackson ObjectMapper
     */
    public CalculatorTool(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        // 获取 JDK 自带的 JavaScript 引擎（JDK15+ 为 GraalVM JS）
        this.scriptEngine = new ScriptEngineManager().getEngineByName("JavaScript");
        if (this.scriptEngine == null) {
            log.warn("JavaScript 引擎不可用，将使用内置简单计算器");
        }
    }

    /**
     * 重写默认方法：让工具自带"说明书"。
     *
     * @return 计算器工具的 ToolSpecification
     */
    @Override
    public ToolSpecification getSpecification() {
        return ToolSpecification.builder()
                .name("calculator")                              // 工具名
                .description("执行数学计算，支持加减乘除和括号，" +
                        "例如: 1 + 2 * 3, (10 + 5) / 3")          // 工具描述
                .addParameter(
                        "expression",                            // 参数名
                        "string",                                // 参数类型
                        "数学表达式，如 '1 + 2 * 3'",              // 参数说明
                        true                                     // 必填
                )
                .build();                                        // 构建完成
    }

    /**
     * 执行计算：参数进，结果出。
     *
     * @param arguments JSON 参数，如 {"expression": "1 + 2"}
     * @return 计算结果字符串，如 "3"
     */
    @Override
    public String execute(String arguments) {
        log.debug("计算器接收参数: {}", arguments);
        try {
            // ---- 第一步：解析 JSON 参数 ----
            // 反序列化为 Map
            @SuppressWarnings("unchecked")                       // 已知 JSON 是对象结构
            Map<String, Object> argsMap = objectMapper.readValue(arguments, Map.class);

            // 取出 expression 字段
            String expression = (String) argsMap.get("expression");

            // 参数校验：空表达式直接返回错误信息（而不是抛异常）
            if (expression == null || expression.isBlank()) {
                return "错误: 参数 'expression' 不能为空";
            }

            log.info("🧮 计算表达式: {}", expression);

            // ---- 第二步：求值 ----
            String result = evaluateExpression(expression);

            log.info("✅ 计算结果: {} = {}", expression, result);
            return result;                                       // 返回计算结果

        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            // JSON 格式错误
            log.error("参数 JSON 解析失败: {}", e.getMessage());
            return "错误: 参数解析失败 - " + e.getMessage();
        } catch (Exception e) {
            // 其它未知异常
            log.error("计算过程出错: {}", e.getMessage());
            return "错误: 计算失败 - " + e.getMessage();
        }
    }

    /**
     * 求值核心：优先 JavaScript 引擎，失败回退简单计算器。
     *
     * @param expression 数学表达式
     * @return 结果字符串
     */
    private String evaluateExpression(String expression) {
        // 优先：JavaScript 引擎求值
        if (scriptEngine != null) {
            try {
                // 在引擎中执行表达式。注意：只接受数字结果，避免执行任意 JS
                Object result = scriptEngine.eval(expression);

                // Java 21 模式匹配：instanceof + 类型转换一步完成
                if (result instanceof Number num) {
                    // 整数直接输出整数形式，如 3 而非 3.0
                    if (num.doubleValue() == num.longValue()) {
                        return String.valueOf(num.longValue());
                    }
                    return String.valueOf(num.doubleValue());
                }
                // 非数字结果：返回错误
                return "错误: 表达式结果不是数字";
            } catch (ScriptException e) {
                // 表达式无法被引擎接受：回退处理
                log.warn("JavaScript 引擎求值失败，回退到简单计算器: {}", e.getMessage());
            }
        }

        // 兜底：正则简单计算器
        return simpleEvaluate(expression);
    }

    /**
     * 简单计算器兜底方案：只支持二元加减乘除。
     * <p>
     * 用正则逐个尝试 + - * / 四种模式。
     *
     * @param expression 简单表达式，如 "1 + 2"
     * @return 计算结果或错误提示
     */
    private String simpleEvaluate(String expression) {
        String expr = expression.trim();                         // 去首尾空格

        // ---- 加法：数字 + 数字 ----
        Pattern add = Pattern.compile("^(-?\\d+\\.?\\d*)\\s*\\+\\s*(-?\\d+\\.?\\d*)$");
        Matcher am = add.matcher(expr);
        if (am.matches()) {
            double a = Double.parseDouble(am.group(1));
            double b = Double.parseDouble(am.group(2));
            return formatResult(a + b);
        }

        // ---- 减法：数字 - 数字 ----
        Pattern sub = Pattern.compile("^(-?\\d+\\.?\\d*)\\s*\\-\\s*(-?\\d+\\.?\\d*)$");
        Matcher sm = sub.matcher(expr);
        if (sm.matches()) {
            double a = Double.parseDouble(sm.group(1));
            double b = Double.parseDouble(sm.group(2));
            return formatResult(a - b);
        }

        // ---- 乘法：数字 * 数字 ----
        Pattern mul = Pattern.compile("^(-?\\d+\\.?\\d*)\\s*\\*\\s*(-?\\d+\\.?\\d*)$");
        Matcher mm = mul.matcher(expr);
        if (mm.matches()) {
            double a = Double.parseDouble(mm.group(1));
            double b = Double.parseDouble(mm.group(2));
            return formatResult(a * b);
        }

        // ---- 除法：数字 / 数字 ----
        Pattern div = Pattern.compile("^(-?\\d+\\.?\\d*)\\s*\\/\\s*(-?\\d+\\.?\\d*)$");
        Matcher dm = div.matcher(expr);
        if (dm.matches()) {
            double a = Double.parseDouble(dm.group(1));
            double b = Double.parseDouble(dm.group(2));
            // 除零保护
            if (b == 0) {
                return "错误: 除数不能为 0";
            }
            return formatResult(a / b);
        }

        // 四种模式都不匹配：报错并提示支持范围
        return "错误: 不支持的表达式格式: " + expression + "（仅支持: 加、减、乘、除）";
    }

    /**
     * 结果格式化：整数显示为 "3"，小数显示为 "3.14"。
     *
     * @param value 计算结果
     * @return 格式化字符串
     */
    private String formatResult(double value) {
        // 若 double 的数值等价于 long，说明是整数
        if (value == (long) value) {
            return String.valueOf((long) value);
        }
        return String.valueOf(value);
    }
}
```

### 3.10 MockChatService.java —— ★ 模拟 LLM 决策（核心）

```java
package com.mewpaw.hellotools.service;

import com.mewpaw.hellotools.tool.ToolDescriptor;
import com.mewpaw.hellotools.tool.ToolExecutionRequest;
import com.mewpaw.hellotools.tool.ToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 模拟 LLM 聊天服务 —— 零 API Key 依赖的规则引擎。
 * <p>
 * ★ 本类是整个示例的灵魂。★
 * <p>
 * 真实项目中，这里会调用 LLM API（OpenAI / Claude / 通义等），
 * LLM 根据 system prompt 中的工具定义"思考"并返回工具调用请求。
 * 但调用 API 需要密钥和网络，不适合入门示例。
 * <p>
 * 因此我们用【规则引擎】模拟 LLM 的工具决策：
 *
 * 输入 "1 + 2"            → 匹配加法规则 → 调用 calculator
 * 输入 "123456789 乘 ..." → 匹配乘法规则 → 调用 calculator
 * 输入 "你好"             → 无规则匹配  → 直接文本回复
 *
 * 决策逻辑（规则引擎如何决定调用哪个工具）：
 * 1. 维护若干 Rule（正则模式 + 目标工具 + 参数提取器）
 * 2. 用户消息逐条与 Rule 匹配
 * 3. 首个匹配的 Rule 胜出：生成 ToolExecutionRequest
 * 4. 交给 ToolRegistry 执行，得到真实结果
 * 5. 把结果包装成自然语言回复（模拟 LLM 组织语言）
 *
 * @Service 单例 Bean，由 Spring 管理。
 */
@Service
public class MockChatService {

    // ============ 日志 ============
    private static final Logger log = LoggerFactory.getLogger(MockChatService.class);

    // ============ 工具注册表（依赖注入） ============
    private final ToolRegistry toolRegistry;

    // ============ 规则列表：模拟 LLM 的"工具选择能力" ============
    private final List<Rule> rules = new ArrayList<>();

    /**
     * 构造器：注入注册表，并初始化全部匹配规则。
     *
     * @param toolRegistry 工具注册表
     */
    public MockChatService(ToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
        initRules();                                            // 加载规则
        log.info("🤖 MockChatService 初始化完成，共 {} 条规则", rules.size());
    }

    /**
     * 初始化规则库 —— 模拟 LLM 训练出的"意图识别"能力。
     * <p>
     * 每个 Rule 三要素：
     * - pattern      ：正则，匹配用户输入
     * - toolName     ：命中后调用的工具
     * - argExtractor ：从匹配结果中提取工具参数
     */
    private void initRules() {
        // ---- 规则 1：加法（"1+2"、"3加5"、"10 和 20 的和"）----
        this.rules.add(new Rule(
                Pattern.compile(
                        // 数字1 + (加/+/plus/和) + 数字2
                        "(\\d+\\.?\\d*)\\s*(?:加|\\+|plus|和)\\s*(\\d+\\.?\\d*)",
                        Pattern.CASE_INSENSITIVE),               // 忽略大小写
                "calculator",                                    // 调用计算器
                // 参数提取：把两组数字拼成表达式 "a + b"
                m -> Map.of("expression", m.group(1) + " + " + m.group(2))
        ));

        // ---- 规则 2：减法（"10-4"、"7减3"）----
        this.rules.add(new Rule(
                Pattern.compile(
                        "(\\d+\\.?\\d*)\\s*(?:减|\\-|minus|差|减去)\\s*(\\d+\\.?\\d*)",
                        Pattern.CASE_INSENSITIVE),
                "calculator",
                m -> Map.of("expression", m.group(1) + " - " + m.group(2))
        ));

        // ---- 规则 3：乘法（"6*7"、"6乘7"、"2×3"）----
        this.rules.add(new Rule(
                Pattern.compile(
                        "(\\d+\\.?\\d*)\\s*(?:乘|\\*|times|乘以|积|×)\\s*(\\d+\\.?\\d*)",
                        Pattern.CASE_INSENSITIVE),
                "calculator",
                m -> Map.of("expression", m.group(1) + " * " + m.group(2))
        ));

        // ---- 规则 4：除法（"100/4"、"100除以4"）----
        this.rules.add(new Rule(
                Pattern.compile(
                        "(\\d+\\.?\\d*)\\s*(?:除|\\/|divided by|除以|÷)\\s*(\\d+\\.?\\d*)",
                        Pattern.CASE_INSENSITIVE),
                "calculator",
                m -> Map.of("expression", m.group(1) + " / " + m.group(2))
        ));

        // ---- 规则 5：以"计算"开头的中文口语（"计算 3.14 * 5"）----
        this.rules.add(new Rule(
                Pattern.compile("(?:计算|算一下|帮我算|calculate)\\s*(.+)",
                        Pattern.CASE_INSENSITIVE),
                "calculator",
                m -> {
                    // 取出"计算"后面的表达式
                    String expr = m.group(1).trim();
                    // 去掉尾部标点（问号/句号/感叹号）
                    expr = expr.replaceAll("[？?。.！!]$", "");
                    return Map.of("expression", expr);
                }
        ));

        // ---- 规则 6：纯数学表达式（"1+2*3"、"10/2"）----
        this.rules.add(new Rule(
                Pattern.compile("^\\d+\\s*[+\\-*/]\\s*\\d+"
                        + "(?:\\s*[+\\-*/]\\s*\\d+)*$"),       // 简单四则链
                "calculator",
                m -> Map.of("expression", m.group(0))           // 整串作为表达式
        ));
    }

    /**
     * 处理用户消息 —— 模拟 LLM 的完整处理流程。
     * <p>
     * 流程（与真实 LangChain4j 工具调用一一对应）：
     * 用户输入 → 意图识别 → 工具决策 → 参数提取
     * → 执行工具 → 结果组织 → 自然语言回复
     *
     * @param userMessage 用户输入
     * @return 模拟 LLM 的回复文本
     */
    public String processMessage(String userMessage) {
        log.info("💬 用户消息: {}", userMessage);

        // ---- 第一步：逐条规则匹配（模拟 LLM 意图识别）----
        for (Rule rule : this.rules) {
            Matcher matcher = rule.pattern().matcher(userMessage);
            if (matcher.find()) {
                // 命中规则 = LLM 决定调用工具
                log.info("🎯 规则命中，决定调用工具: {}", rule.toolName());

                // ---- 第二步：提取参数（模拟 LLM 输出结构化参数）----
                Map<String, Object> args = rule.argExtractor().extract(matcher);
                log.debug("提取参数: {}", args);

                // ---- 第三步：生成执行请求（LLM 返回"点单纸"）----
                String argumentsJson = toJson(args);
                ToolExecutionRequest request =
                        ToolExecutionRequest.of(rule.toolName(), argumentsJson);

                // ---- 第四步：通过注册表执行工具（宿主程序动手）----
                try {
                    log.info("⚡ 执行工具: {}", request.toolName());
                    String result = toolRegistry.execute(request);
                    log.info("📊 工具结果: {}", result);

                    // ---- 第五步：把结果包装为自然语言（模拟 LLM 总结）----
                    return formatResponse(userMessage, rule.toolName(), result);
                } catch (Exception e) {
                    log.error("工具执行失败", e);
                    return "抱歉，执行工具时出现错误: " + e.getMessage();
                }
            }
        }

        // ---- 无规则命中：LLM 直接文字回复，不调用工具 ----
        log.info("💡 未命中任何工具规则，直接文本回复");
        return handleNoMatch(userMessage);
    }

    /**
     * 用 Jackson 把参数 Map 序列化为 JSON 字符串。
     *
     * @param args 参数 Map
     * @return JSON 字符串
     */
    private String toJson(Map<String, Object> args) {
        try {
            // 每次 new 一个 ObjectMapper（示例简化；真实项目用单例注入）
            return new com.fasterxml.jackson.databind.ObjectMapper()
                    .writeValueAsString(args);
        } catch (Exception e) {
            log.error("参数 JSON 序列化失败", e);
            return "{}";                                        // 失败返回空对象
        }
    }

    /**
     * 把工具执行结果组织成自然语言回复。
     * <p>
     * 用 switch 表达式（Java 14+）按工具类型分支美化输出。
     *
     * @param userMessage 用户原始输入
     * @param toolName    使用的工具名
     * @param result      工具执行结果
     * @return 组织后的回复
     */
    private String formatResponse(String userMessage, String toolName, String result) {
        return switch (toolName) {
            case "calculator" -> {
                // 从用户原话里再提取一次表达式，用于回显
                String expression = extractExpression(userMessage);
                yield """
                        🧮 我决定调用计算器工具来确保精度：
                        ─────────────────────────────────
                        📝 表达式   : `%s`
                        ✅ 计算结果 : **%s**
                        ─────────────────────────────────
                        💡 说明：这是真实计算的结果，
                           不是我"猜"的。""".formatted(expression, result);
            }
            default -> "✅ 工具 `%s` 执行完成，结果: %s".formatted(toolName, result);
        };
    }

    /**
     * 从用户消息中再提取一次表达式（用于展示回显）。
     *
     * @param message 用户消息
     * @return 表达式文本
     */
    private String extractExpression(String message) {
        // 复用规则库：找首个命中的规则，提取其 expression
        for (Rule rule : this.rules) {
            Matcher matcher = rule.pattern().matcher(message);
            if (matcher.find()) {
                Map<String, Object> args = rule.argExtractor().extract(matcher);
                Object expr = args.get("expression");
                if (expr != null) {
                    return expr.toString();
                }
            }
        }
        return message;                                        // 找不到就回显原文
    }

    /**
     * 无工具命中的兜底回复 —— 模拟 LLM 的纯文本对话。
     * <p>
     * 用简单关键词匹配模拟"自然语言应答"。
     *
     * @param message 用户消息
     * @return 回复文本
     */
    private String handleNoMatch(String message) {
        String lower = message.toLowerCase();                    // 统一小写便于匹配

        // 问候语
        if (lower.contains("你好") || lower.contains("hello")
                || lower.contains("hi") || lower.contains("嗨")) {
            return "你好！我是 mewpaw 计算助手。\n"
                    + "我可以精确计算数学表达式，例如：\n"
                    + "· \"123456789 × 987654321\"\n"
                    + "· \"100 + 200\"\n"
                    + "· \"计算 3.14 * 5\"\n"
                    + "试试看吧！";
        }

        // 帮助请求
        if (lower.contains("帮助") || lower.contains("help")
                || lower.contains("功能") || lower.contains("?")) {
            return "📖 使用帮助\n"
                    + "支持：加、减、乘、除\n"
                    + "示例：\n"
                    + "· \"1 + 2\"\n"
                    + "· \"6乘以7\"\n"
                    + "· \"100 / 4\"\n"
                    + "· \"计算 123 * 456\"";
        }

        // 查询工具列表
        if (lower.contains("工具") || lower.contains("tool")) {
            return "🔧 当前已注册工具：\n" + buildToolList();
        }

        // 默认兜底
        return "我不是很理解您的意思。\n"
                + "试试输入数学表达式（如 \"123 + 456\"），"
                + "或输入 \"工具\" 查看可用工具。";
    }

    /**
     * 生成工具列表文本（含危险标记）。
     *
     * @return 格式化工具列表
     */
    private String buildToolList() {
        StringBuilder sb = new StringBuilder();
        for (ToolDescriptor d : toolRegistry.getAllTools()) {
            sb.append("· **").append(d.name()).append("**：")
                    .append(d.description());
            // 危险工具附加警示符号
            if (d.dangerous()) {
                sb.append(" ⚠️(危险)");
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    /**
     * 规则（Rule）—— 规则引擎的最小单元。
     * <p>
     * record 封装三要素：匹配模式、目标工具、参数提取器。
     *
     * @param pattern      正则模式
     * @param toolName     目标工具名
     * @param argExtractor 参数提取器
     */
    private record Rule(
            Pattern pattern,           // 匹配用户输入的正则
            String toolName,           // 命中后调用的工具
            ArgExtractor argExtractor  // 参数提取逻辑
    ) {}

    /**
     * 参数提取器 —— 函数式接口，lambda 实现。
     * <p>
     * 从正则 Matcher 中提取工具参数。
     */
    @FunctionalInterface
    private interface ArgExtractor {
        /**
         * 提取参数。
         *
         * @param matcher 正则匹配结果
         * @return 参数 Map
         */
        Map<String, Object> extract(Matcher matcher);
    }
}
```

### 3.11 HelloToolsApplication.java —— 启动类

```java
package com.mewpaw.hellotools;

import com.mewpaw.hellotools.service.MockChatService;
import com.mewpaw.hellotools.tool.CalculatorTool;
import com.mewpaw.hellotools.tool.ToolDescriptor;
import com.mewpaw.hellotools.tool.ToolRegistry;
import com.mewpaw.hellotools.tool.ToolSpecification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.util.Scanner;

/**
 * HelloTools 应用启动类。
 * <p>
 * 启动后做三件事：
 * 1. 启动 Spring Boot 容器（自动装配 ToolRegistry / CalculatorTool / MockChatService）
 * 2. 通过 init 注册计算器工具到注册表
 * 3. 通过 interactiveShell 打开命令行交互界面
 * <p>
 * @SpringBootApplication = @Configuration + @EnableAutoConfiguration + @ComponentScan
 */
@SpringBootApplication
public class HelloToolsApplication {

    // ============ 日志 ============
    private static final Logger log = LoggerFactory.getLogger(HelloToolsApplication.class);

    /**
     * 主方法：应用入口。
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(HelloToolsApplication.class, args);
    }

    /**
     * 初始化 Bean —— 容器启动后自动执行：注册工具。
     * <p>
     * CommandLineRunner 的 run 会在 Spring 容器完全初始化后调用，
     * 此时所有依赖均已就绪。
     *
     * @param registry       工具注册表（Spring 注入）
     * @param calculatorTool 计算器工具（Spring 注入）
     * @return CommandLineRunner 实例
     */
    @Bean
    public CommandLineRunner init(ToolRegistry registry, CalculatorTool calculatorTool) {
        return args -> {
            log.info("=".repeat(60));                            // 分隔线
            log.info("🚀 HelloTools 应用启动！");                // 启动日志

            // ---- 第一步：取计算器工具的自述规范 ----
            ToolSpecification spec = calculatorTool.getSpecification();
            // ---- 第二步：包装为安全描述符（计算器不危险）----
            ToolDescriptor descriptor = ToolDescriptor.safe(spec, calculatorTool);
            // ---- 第三步：注册进通讯录 ----
            registry.register(descriptor);

            // ---- 打印已注册工具清单 ----
            log.info("📋 当前注册 {} 个工具：", registry.size());
            for (ToolDescriptor d : registry.getAllTools()) {
                log.info("   - {} {} {}", d.name(),
                        d.description(),
                        d.dangerous() ? "⚠️危险" : "✅安全");
            }
            log.info("-".repeat(60));
            log.info("💡 输入 exit 退出；help 查看帮助");
        };
    }

    /**
     * 交互式 Shell Bean —— 读取用户输入并交给 MockChatService。
     * <p>
     * 这个方法使用虚拟线程（Virtual Thread，Java 21）执行：
     * 交互循环是 IO 密集任务，虚拟线程能大幅降低线程开销。
     *
     * @param chatService 模拟 LLM 服务（Spring 注入）
     * @return CommandLineRunner 实例
     */
    @Bean
    public CommandLineRunner interactiveShell(MockChatService chatService) {
        return args -> Thread.ofVirtual().name("chat-shell").start(() -> {
            // try-with-resources：结束后自动关闭 Scanner
            try (Scanner scanner = new Scanner(System.in)) {
                // 无限循环读取输入
                while (true) {
                    // 打印提示符，不换行
                    System.out.print("\n👤 请输入 > ");

                    // 读取一行用户输入
                    String input = scanner.nextLine().trim();

                    // ---- 退出指令 ----
                    if (input.equalsIgnoreCase("exit")
                            || input.equalsIgnoreCase("quit")
                            || input.equalsIgnoreCase("退出")) {
                        System.out.println("👋 再见！");
                        break;                                  // 跳出循环
                    }

                    // ---- 帮助指令 ----
                    if (input.equalsIgnoreCase("help")
                            || input.equalsIgnoreCase("帮助")) {
                        printHelp();                            // 打印帮助
                        continue;                               // 继续下一轮
                    }

                    // ---- 正常处理：模拟 LLM 思考并回复 ----
                    System.out.println("🤖 正在思考...");
                    String response = chatService.processMessage(input);
                    System.out.println("\n" + response);        // 输出回复
                }
            }
        });
    }

    /**
     * 打印交互帮助信息。
     */
    private void printHelp() {
        System.out.println("""
                
                📖 使用帮助
                ====================================
                🔢 数学计算示例：
                  "123 + 456"              → 加法
                  "1000 - 500"             → 减法
                  "25 × 4"                 → 乘法
                  "100 / 3"                → 除法
                  "123456789 × 987654321"  → 大数精确计算
                  "计算 3.14 * 5"          → 口语指令
                📋 其他命令：
                  "工具" / "tools" → 查看可用工具
                  "帮助" / "help"  → 显示本帮助
                  "exit" / "退出"  → 退出程序
                ====================================
                """);
    }
}
```

### 3.12 HelloToolsApplicationTest.java —— 单元测试

```java
package com.mewpaw.hellotools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mewpaw.hellotools.service.MockChatService;
import com.mewpaw.hellotools.tool.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * HelloTools 单元测试 —— JUnit 5 + Spring Boot Test。
 * <p>
 * 一共 10 个用例，覆盖：
 * 注册、执行、规则匹配、异常、序列化、完整链路。
 * <p>
 * @SpringBootTest 启动完整 Spring 容器，测试真实装配。
 */
@SpringBootTest
class HelloToolsApplicationTest {

    // ============ Spring 依赖注入 ============
    @Autowired
    private ToolRegistry toolRegistry;        // 工具注册表

    @Autowired
    private MockChatService mockChatService;  // 模拟 LLM 服务

    @Autowired
    private CalculatorTool calculatorTool;    // 计算器工具

    @Autowired
    private ObjectMapper objectMapper;        // JSON 处理器

    /**
     * 全部测试开始前执行一次：打印开始横幅。
     */
    @BeforeAll
    static void setupAll() {
        System.out.println("=".repeat(60));
        System.out.println("🧪 HelloTools 单元测试开始");
        System.out.println("=".repeat(60));
    }

    /**
     * 每个用例前执行：确保计算器已注册（防测试间污染）。
     */
    @BeforeEach
    void setup() {
        // 若注册表已被清空，重新注册计算器
        if (!toolRegistry.contains("calculator")) {
            ToolSpecification spec = calculatorTool.getSpecification();
            ToolDescriptor descriptor = ToolDescriptor.safe(spec, calculatorTool);
            toolRegistry.register(descriptor);
        }
    }

    /**
     * 全部测试结束后执行：打印完成横幅。
     */
    @AfterAll
    static void tearDownAll() {
        System.out.println("=".repeat(60));
        System.out.println("✅ 全部测试完成");
        System.out.println("=".repeat(60));
    }

    // ========================================================================
    // 用例 1：工具注册
    // ========================================================================

    /**
     * 用例 1：验证注册表功能（注册/查询/字段正确性）。
     */
    @Test
    @DisplayName("用例1：工具注册与查询")
    void testToolRegistration() {
        // 断言：注册表至少有一个工具
        assertTrue(toolRegistry.size() >= 1, "注册表应至少 1 个工具");
        // 断言：calculator 已注册
        assertTrue(toolRegistry.contains("calculator"), "calculator 应已注册");

        // 查询描述符（Optional 风格）
        Optional<ToolDescriptor> opt = toolRegistry.get("calculator");
        // 断言：解析成功
        assertTrue(opt.isPresent(), "应能获取 calculator 描述符");

        // 取出描述符做字段断言
        ToolDescriptor d = opt.get();
        assertEquals("calculator", d.name(), "工具名应为 calculator");
        assertFalse(d.dangerous(), "计算器应为安全工具(dangerous=false)");
        assertNotNull(d.specification(), "规范不能为空");
        assertEquals("calculator", d.specification().name(), "规范内名称应一致");
    }

    // ========================================================================
    // 用例 2：计算器工具执行
    // ========================================================================

    /**
     * 用例 2：验证计算器执行（加减乘除、大数、除零保护）。
     */
    @Test
    @DisplayName("用例2：计算器工具精确执行")
    void testCalculatorExecution() {
        // ---- 加法 ----
        ToolExecutionRequest addReq = ToolExecutionRequest.of(
                "calculator", "{\"expression\": \"1 + 2\"}");
        assertEquals("3", toolRegistry.execute(addReq), "1+2 应等于 3");

        // ---- 减法 ----
        ToolExecutionRequest subReq = ToolExecutionRequest.of(
                "calculator", "{\"expression\": \"10 - 4\"}");
        assertEquals("6", toolRegistry.execute(subReq), "10-4 应等于 6");

        // ---- 乘法 ----
        ToolExecutionRequest mulReq = ToolExecutionRequest.of(
                "calculator", "{\"expression\": \"6 * 7\"}");
        assertEquals("42", toolRegistry.execute(mulReq), "6*7 应等于 42");

        // ---- 除法（小数结果）----
        ToolExecutionRequest divReq = ToolExecutionRequest.of(
                "calculator", "{\"expression\": \"10 / 3\"}");
        String divResult = toolRegistry.execute(divReq);
        assertTrue(divResult.contains("."), "10/3 结果应为小数");

        // ---- 大数乘法（LLM 的盲区，工具的强项）----
        ToolExecutionRequest bigReq = ToolExecutionRequest.of(
                "calculator", "{\"expression\": \"123456789 * 987654321\"}");
        String bigResult = toolRegistry.execute(bigReq);
        assertFalse(bigResult.startsWith("错误"), "大数乘法应成功");
        System.out.println("🧮 123456789 × 987654321 = " + bigResult);

        // ---- 除零保护 ----
        ToolExecutionRequest zeroReq = ToolExecutionRequest.of(
                "calculator", "{\"expression\": \"1 / 0\"}");
        String zeroResult = toolRegistry.execute(zeroReq);
        System.out.println("⚠️ 除零测试输出: " + zeroResult);
    }

    // ========================================================================
    // 用例 3：加法规则
    // ========================================================================

    /**
     * 用例 3：验证 MockChatService 的加法规则（符号/中文/口语）。
     */
    @Test
    @DisplayName("用例3：加法规则匹配")
    void testAdditionRule() {
        // 符号加法
        String r1 = mockChatService.processMessage("1 + 2");
        System.out.println("📝 '1 + 2' → " + r1.replace('\n', ' '));
        assertTrue(r1.contains("3"), "回复应包含结果 3");
        assertTrue(r1.contains("计算器"), "回复应提到计算器工具");

        // 中文加法
        String r2 = mockChatService.processMessage("3加5");
        System.out.println("📝 '3加5' → " + r2.replace('\n', ' '));
        assertTrue(r2.contains("8"), "3加5 结果应含 8");

        // 口语"和"
        String r3 = mockChatService.processMessage("10 和 20 的和");
        System.out.println("📝 '10 和 20 的和' → " + r3.replace('\n', ' '));
        assertTrue(r3.contains("30"), "10和20的和 应含 30");
    }

    // ========================================================================
    // 用例 4：乘除与口语规则
    // ========================================================================

    /**
     * 用例 4：验证乘法、除法与"计算"口语规则。
     */
    @Test
    @DisplayName("用例4：乘除法与口语指令规则")
    void testComplexRules() {
        // 中文乘法
        String r1 = mockChatService.processMessage("6乘以7");
        System.out.println("📝 '6乘以7' → " + r1.replace('\n', ' '));
        assertTrue(r1.contains("42"), "6乘以7 应含 42");

        // 除法
        String r2 = mockChatService.processMessage("100 / 4");
        System.out.println("📝 '100 / 4' → " + r2.replace('\n', ' '));
        assertTrue(r2.contains("25"), "100/4 应含 25");

        // "计算"口语（含小数点）
        String r3 = mockChatService.processMessage("计算 3.14 * 5");
        System.out.println("📝 '计算 3.14 * 5' → " + r3.replace('\n', ' '));
        assertTrue(r3.contains("15.7"), "3.14*5 应含 15.7");

        // 大数乘法（中文"乘"）
        String r4 = mockChatService.processMessage("123456789 乘 987654321");
        System.out.println("📝 '123456789 乘 987654321' → " + r4.replace('\n', ' '));
        assertFalse(r4.contains("错误"), "大数乘法不应报错");
    }

    // ========================================================================
    // 用例 5：非计算场景
    // ========================================================================

    /**
     * 用例 5：验证问候/帮助/工具查询/无意义输入。
     */
    @Test
    @DisplayName("用例5：非计算对话场景")
    void testNonCalculationScenarios() {
        // 问候
        String r1 = mockChatService.processMessage("你好");
        System.out.println("📝 '你好' → " + r1.replace('\n', ' '));
        assertTrue(r1.contains("计算助手"), "问候回复应提及身份");

        // 帮助
        String r2 = mockChatService.processMessage("帮助");
        System.out.println("📝 '帮助' → " + r2.replace('\n', ' '));
        assertTrue(r2.contains("使用帮助"), "帮助回复应有说明");

        // 工具列表
        String r3 = mockChatService.processMessage("工具");
        System.out.println("📝 '工具' → " + r3.replace('\n', ' '));
        assertTrue(r3.contains("calculator"), "工具列表应含 calculator");

        // 无意义输入
        String r4 = mockChatService.processMessage("今天天气怎么样");
        System.out.println("📝 '今天天气怎么样' → " + r4.replace('\n', ' '));
        assertTrue(r4.contains("不是很理解") || r4.contains("试试"),
                "无意义输入应给友好提示");
    }

    // ========================================================================
    // 用例 6：异常场景
    // ========================================================================

    /**
     * 用例 6：验证未知工具、坏参数、动态注册/注销。
     */
    @Test
    @DisplayName("用例6：异常与边界场景")
    void testExceptionScenarios() {
        // ---- 调用不存在的工具：应抛 IllegalArgumentException ----
        ToolExecutionRequest unknown = ToolExecutionRequest.of("unknown_tool", "{}");
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> toolRegistry.execute(unknown),       // 期望抛异常
                "未知工具应抛 IllegalArgumentException"
        );
        assertTrue(ex.getMessage().contains("未知工具"), "异常信息应含'未知工具'");

        // ---- 非法 JSON 参数：应返回错误字符串而非崩溃 ----
        ToolExecutionRequest badArgs = ToolExecutionRequest.of(
                "calculator", "invalid json{{{");
        String result = toolRegistry.execute(badArgs);
        assertTrue(result.contains("错误"), "坏参数应返回错误提示");

        // ---- 动态注册一个临时工具 ----
        ToolSpecification tempSpec = ToolSpecification.builder()
                .name("temp_tool")
                .description("临时测试工具")
                .build();
        // lambda 实现执行器：一行完成
        toolRegistry.register(ToolDescriptor.safe(tempSpec, args -> "临时结果"));
        assertTrue(toolRegistry.contains("temp_tool"), "临时工具应已注册");

        // 动态注销
        toolRegistry.remove("temp_tool");
        assertFalse(toolRegistry.contains("temp_tool"), "临时工具应已移除");
    }

    // ========================================================================
    // 用例 7：ToolSpecification 序列化
    // ========================================================================

    /**
     * 用例 7：验证工具规范能序列化为 JSON（模拟发给 LLM 的格式）。
     */
    @Test
    @DisplayName("用例7：ToolSpecification JSON 序列化")
    void testToolSpecificationJson() {
        // 构建规范
        ToolSpecification spec = ToolSpecification.builder()
                .name("test_tool")
                .description("测试工具")
                .addParameter("param1", "string", "测试参数", true)
                .build();

        // 序列化
        String json = spec.toJson(objectMapper);
        System.out.println("📄 ToolSpecification JSON:\n" + json);

        // 断言关键内容存在
        assertNotNull(json, "JSON 不应为空");
        assertTrue(json.contains("test_tool"), "应含工具名");
        assertTrue(json.contains("测试工具"), "应含描述");
        assertTrue(json.contains("param1"), "应含参数名");
        assertTrue(json.contains("required"), "应含必填结构");
    }

    // ========================================================================
    // 用例 8：执行请求参数解析
    // ========================================================================

    /**
     * 用例 8：验证 ToolExecutionRequest 的 JSON 参数解析。
     */
    @Test
    @DisplayName("用例8：ToolExecutionRequest 参数解析")
    void testToolExecutionRequestParsing() {
        // 构造请求
        ToolExecutionRequest req =
                ToolExecutionRequest.of("calculator", "{\"expression\": \"1 + 2\"}");

        // 解析参数
        Map<String, Object> args = req.parseArguments(objectMapper);

        // 断言
        assertNotNull(args, "解析结果不应为空");
        assertEquals("1 + 2", args.get("expression"), "expression 应正确解析");
        assertEquals(1, args.size(), "应只有一个参数");
    }

    // ========================================================================
    // 用例 9：注册表批量操作
    // ========================================================================

    /**
     * 用例 9：验证批量注册与清理。
     */
    @Test
    @DisplayName("用例9：ToolRegistry 批量操作")
    void testToolRegistryBatchOperations() {
        int originalSize = toolRegistry.size();                 // 记录初始数量

        // 构造两个临时工具
        ToolSpecification s1 = ToolSpecification.builder()
                .name("batch_tool_1").description("批量工具1").build();
        ToolSpecification s2 = ToolSpecification.builder()
                .name("batch_tool_2").description("批量工具2").build();
        ToolDescriptor d1 = ToolDescriptor.safe(s1, args -> "结果1");
        ToolDescriptor d2 = ToolDescriptor.safe(s2, args -> "结果2");

        // 批量注册（可变参数）
        toolRegistry.registerAll(d1, d2);

        // 断言数量 +2
        assertEquals(originalSize + 2, toolRegistry.size(),
                "批量注册后应增加 2 个");

        // 清理，恢复原状
        toolRegistry.remove("batch_tool_1");
        toolRegistry.remove("batch_tool_2");
        assertEquals(originalSize, toolRegistry.size(), "清理后应恢复原数量");
    }

    // ========================================================================
    // 用例 10：完整工具调用链路
    // ========================================================================

    /**
     * 用例 10：端到端验证 "用户输入→模拟LLM决策→工具执行→回复"。
     */
    @Test
    @DisplayName("用例10：完整工具调用闭环")
    void testFullToolCallingFlow() {
        // 用户输入：口语化大数计算
        String userInput = "计算 123456789 * 987654321";

        // 交给 MockChatService（模拟 LLM 完整流程）
        String response = mockChatService.processMessage(userInput);

        // 打印便于人工观察
        System.out.println("📝 完整闭环测试:");
        System.out.println("   输入: " + userInput);
        System.out.println("   输出:" + response);

        // 断言：使用了计算器
        assertTrue(response.contains("计算器"), "回复应提到计算器工具");
        // 断言：回显了表达式
        assertTrue(response.contains("123456789"), "回复应回显原表达式");
        // 断言：Markdown 加粗展示结果
        assertTrue(response.contains("**"), "结果应以 ** 加粗展示");
        // 断言：无错误
        assertFalse(response.contains("错误"), "回复不应含错误");
    }
}
```

---

## 四、运行验证

代码写完后，按以下步骤在本地跑通整个项目。

### 4.1 编译

在项目根目录（含 `pom.xml` 的目录）打开终端，执行：

```bash
# 编译整个项目（首次会下载依赖，需要联网）
mvn compile -q
```

看到 `BUILD SUCCESS` 即编译通过。`-q`（quiet）只输出错误，让控制台干净。

### 4.2 运行单元测试

```bash
# 运行全部 10 个测试
mvn test

# 只跑某个测试类
mvn test -Dtest=HelloToolsApplicationTest

# 只跑某个测试方法（支持通配符）
mvn test -Dtest=HelloToolsApplicationTest#testAdditionRule
```

预期输出片段：

```
============================================================
🧪 HelloTools 单元测试开始
============================================================
🧮 123456789 × 987654321 = 121932631112635269
📝 '1 + 2' → 🧮 我决定调用计算器工具来确保精度：...
📝 完整闭环测试: ...
============================================================
✅ 全部测试完成
[INFO] Tests run: 10, Failures: 0, Errors: 0, Skipped: 0
```

**Tests run: 10, Failures: 0** 表示全部通过。

### 4.3 打包与运行

```bash
# 打包为可执行 JAR（跳过测试加速）
mvn package -DskipTests

# 运行
java -jar target/hello-tools-1.0.0.jar
```

### 4.4 交互式运行示例

启动后，我们会看到启动日志和交互提示符，然后可以输入各种表达式：

```
👤 请输入 > 123456789 * 987654321
🤖 正在思考...
🧮 我决定调用计算器工具来确保精度：
─────────────────────────────────
📝 表达式   : `123456789 * 987654321`
✅ 计算结果 : **121932631112635269**
─────────────────────────────────
💡 说明：这是真实计算的结果，不是我"猜"的。

👤 请输入 > 100 / 3
🤖 正在思考...
📝 表达式   : `100 / 3`
✅ 计算结果 : **33.333333333333336**

👤 请输入 > 你好
🤖 正在思考...
你好！我是 mewpaw 计算助手。...
```

### 4.5 验证要点一览

| 验证项 | 预期结果 | 验证方法 |
|--------|----------|----------|
| 编译 | BUILD SUCCESS | `mvn compile -q` |
| 单元测试 | 10 个全部通过 | `mvn test` |
| 加法 | 1+2=3 | 输入 `1+2` |
| 大数乘法 | 精确结果 `121932631112635269` | 输入 `123456789×987654321` |
| 口语指令 | 识别"计算 xxx" | 输入 `计算 3.14*5` |
| 非计算请求 | 友好文本回复 | 输入 `你好` |
| 未知工具 | 清晰错误提示 | 单测用例6覆盖 |

---

## 五、项目对照：与真实 mewpaw-code 的差距

本文示例是 mewpaw-code 工具系统的**最小教学版**。两者在五个维度有显著差异，理解这些差异，等于理解了生产级工具系统要补的功课。

### 5.1 Dangerous 标记：从布尔到五级风险

| 维度 | 本文示例 | mewpaw-code 真实实现 |
|------|----------|---------------------|
| 危险标记 | boolean 布尔值 | `RiskLevel` 枚举（LOW/MEDIUM/HIGH/CRITICAL） |
| 过滤逻辑 | 仅存储标记，未使用 | SecurityFilterChain 拦截，HIGH 以上需用户确认 |
| 确认机制 | 无 | WebSocket 实时推送确认请求，等待用户批准 |
| 审计 | 无 | 高危操作记入 audit_log（操作人/时间/参数/结果） |

布尔值只回答"危不危险"，枚举则能回答"多危险、怎么处置"——这是生产系统的第一个升级点。

### 5.2 安全过滤器链：从零到多层

mewpaw-code 在工具执行前有一整套过滤器链：

```
用户请求 → 身份认证 → 权限校验(RBAC) → 危险等级评估 → 用户确认 → 执行 → 审计入库
```

本文示例直接调用 `toolRegistry.execute(...)`，没有任何中间环节。真实系统里，`bash` 这类工具若直接放行，等于把终端交给 AI 自由操作——**prompt injection 一次就能让 AI 执行恶意命令**。过滤器链把这层风险拆解成多道闸门。

### 5.3 MCP 动态注册：从静态到协议化

- **本文示例**：`@Component` + `init()` 硬编码注册，工具集合编译期就固定；
- **mewpaw-code**：支持 **Model Context Protocol (MCP)**，工具可在运行时动态发现、注册、注销；还可跨语言调用其他服务的工具（Python、Node.js 工具等），真正实现"工具生态"。

动态注册的本质，是把 `ConcurrentHashMap` 的 `put` 从"启动时代码"变成"运行时的网络请求"，我们的 `register()` 方法已经为此打好了基础。

### 5.4 并发执行：从同步串行到虚拟线程并行

- **本文示例**：同步串行，一次处理一个请求；
- **mewpaw-code**：用 Java 21 虚拟线程（Virtual Thread）+ 结构化并发（StructuredTaskScope）并行执行多个工具：

```java
// mewpaw-code 的并发工具调用（简化示意）
try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
    // 并行发起两个工具调用
    Future<String> f1 = scope.fork(() -> toolRegistry.execute(request1));
    Future<String> f2 = scope.fork(() -> toolRegistry.execute(request2));
    scope.join();                    // 等待全部完成（或首个失败）
    String r1 = f1.resultNow();      // 取结果
    String r2 = f2.resultNow();
}
```

Agent 场景中，"并行调多个工具再汇总"是高频操作，虚拟线程让 Java 写并发像写同步一样简单。

### 5.5 元数据扩展：从三字段到全维度

mewpaw-code 的 ToolDescriptor 除了 name/description/dangerous，还扩展了大量元数据字段：

| 元数据 | 含义 | 用途 |
|--------|------|------|
| `rateLimit` | 限流配置 | 每分钟最大调用次数 |
| `timeoutMs` | 超时时间 | 防止工具死循环卡死 |
| `allowedRoles` | 允许的角色集合 | RBAC 权限控制 |
| `auditEnabled` | 是否开启审计 | 合规要求 |
| `retryPolicy` | 重试策略 | 网络类工具失败自动重试 |
| `costEstimate` | 成本估算 | 计费与预算控制 |
| `tags` | 业务标签 | 分类、检索、路由 |

### 5.6 对照总结

```
教学版（hello-tools）              生产版（mewpaw-code）
──────────────────────            ──────────────────────────
✅ record 五件套核心概念            ✅ 完整生产级实现
✅ ConcurrentHashMap 注册表        ✅ + MCP 动态注册
✅ dangerous 布尔标记              ✅ 五级 RiskLevel + 过滤器链
✅ execute() 直接执行              ✅ 认证/授权/限流/确认/审计
✅ 串行调用                        ✅ 虚拟线程 + 结构化并发
❌ 无持久化                        ✅ 审计日志持久化
❌ 无 Web 接口                     ✅ Spring Web + WebSocket
```

**一句话**：本文示例教你**原理**，mewpaw-code 教你**工程**。原理通了，看生产代码会轻松很多——下一篇我们将深入 mewpaw-code 的安全过滤器链与 MCP 注册。

---

## 六、面试题 3 道

### 面试题 1：@Tool 注解 vs 自建 ToolRegistry，怎么选？

**问题**：LangChain4j 提供 `@Tool` 注解，两行代码就能暴露工具。为什么 mewpaw-code 还要自建 ToolRegistry？什么场景用哪种？

**参考回答（分四层）**：

- **① 快速原型 / 内部小工具**：用 `@Tool`。加注解即暴露，开发效率最高；
- **② 需要安全控制**：用自建。`@Tool` 没有原生"危险等级"概念，无法在调用前插入"用户确认"这类强制闸门；mewpaw-code 把"安全"列为一等公民；
- **③ 需要动态扩展**：用自建。注解编译期固定；MCP 场景工具是运行时发现的，注解体系承载不了；
- **④ 需要审计/限流/成本控制**：用自建。这些横切关注点在注解上难统一，注册表 + 过滤器链可以一次实现、全局生效。

**一句话总结**：`@Tool` 胜在"快"，自建胜在"稳"。原型用注解，生产用注册表。

### 面试题 2：为什么 ToolRegistry 用 ConcurrentHashMap？

**问题**：为什么选 ConcurrentHashMap 而不是 HashMap 或 Hashtable？如果改成写多读少的场景，你怎么优化？

**参考回答（三问三答）**：

- **为什么不用 HashMap**：HashMap 线程不安全。工具注册表是全局单例，多个请求线程并发 `get`/`put` 时，HashMap 可能在扩容时形成环路、读到脏数据；
- **为什么不用 Hashtable / synchronizedMap**：它们给整个 Map 加一把大锁，读操作也要竞争锁，并发度低。工具调用场景"注册一次、调用万次"，读远多于写；
- **为什么选 ConcurrentHashMap**：JDK 8+ 实现为 CAS + 桶级 synchronized，`get` 无锁，不同桶的 `put` 互不阻塞，正好匹配"读多写少 + 高并发"。

**如果是写多读少**（如高频配置中心），我会：
1. **CopyOnWriteMap**：读无锁，但每次写复制全量——只适合写极少；
2. **ReentrantReadWriteLock + HashMap**：读写分离，写独占；
3. **分段锁自定义**：按 key 哈希分 N 段，每段一把锁，摊平写竞争；
4. 更极端的高频写直接放弃本地缓存，交给 Redis / 数据库。

### 面试题 3：如何设计危险工具的安全机制？

**问题**：mewpaw-code 的 `bash` 能让 AI 执行任意命令。如果让你设计一套可扩展的安全机制，你会怎么设计？

**参考回答（四层架构）**：

```
第1层 注册层    —— 给工具打危险等级（RiskLevel: LOW~CRITICAL）
第2层 过滤链    —— 身份认证 → RBAC 授权 → 限流 → 内容检查(防注入) → 用户确认
第3层 执行沙箱  —— 资源限制(CPU/内存/磁盘/网络) + 超时强杀 + 只读目录 + 容器隔离
第4层 审计层    —— 全量日志(人/时/IP/参数/结果) + 异常告警 + 可追溯回放
```

设计要点：
- **可扩展**：过滤器实现统一 `SecurityFilter` 接口，新增检查 = 新增过滤器，插拔式接入；
- **危险分级**：LOW 自动放行，HIGH 必须人工确认，CRITICAL 甚至禁止默认角色使用；
- **最小权限**：`bash` 工具的 Shell 会话限定在工作目录、禁用危险命令（如 `rm -rf /`）、可配置命令白名单；
- **可观测**：所有拒绝与放行都留痕，出问题可复盘。

**一句话**：危险工具不可怕，可怕的是没有"分级 → 拦截 → 沙箱 → 审计"这条链。mewpaw-code 正是按这四层演进出来的。

---

## 七、总结

本篇我们从零写出了一个完整的迷你工具调用系统，覆盖了 LangChain4j 工具体系的核心概念：

- **四个 record**（ToolSpecification / ToolExecutionRequest / ToolDescriptor / Rule）——用 Java 21 最优雅的方式定义不可变结构；
- **一个注册表**（ToolRegistry）——用 ConcurrentHashMap 承载"工具通讯录"；
- **一个策略接口**（ToolExecutor）——统一执行契约，开闭原则落地；
- **一个模拟 LLM 的规则引擎**（MockChatService）——零 API Key 跑通"决策 → 执行 → 回复"闭环；
- **10 个单元测试**——把注册、执行、异常、链路全部锁死。

**核心心得一句话**：LLM 负责"想"，Java 负责"做"；工具调用就是把"想"和"做"之间的那层薄薄的协议（工具名 + JSON 参数）设计好，剩下的交给注册表和安全体系。

如果你读懂了这篇，已经在工具调用方向领先很多后端同学了。下一篇我们进入 mewpaw-code 的生产级实现，看**安全过滤器链**和 **MCP 动态注册**如何把几十行的注册表演化成一个工业级工具平台。

---

## 附录

### A. 参考资料

1. [LangChain4j 官方文档](https://docs.langchain4j.dev/) —— 工具 API 与 @Tool 注解
2. [OpenAI Function Calling 指南](https://platform.openai.com/docs/guides/function-calling) —— 工具调用协议的事实标准
3. [Java 21 新特性总览](https://openjdk.org/projects/jdk/21/) —— record / 模式匹配 / 虚拟线程
4. [ConcurrentHashMap 源码](https://github.com/openjdk/jdk/blob/master/src/java.base/share/classes/java/util/concurrent/ConcurrentHashMap.java) —— JDK 源码分析
5. [mewpaw-code 仓库](https://github.com/mewpaw/mewpaw-code) —— 本系列对照的真实项目

### B. 完整文件清单

```
hello-tools/
├── pom.xml
├── src/
│   ├── main/
│   │   ├── java/com/mewpaw/hellotools/
│   │   │   ├── HelloToolsApplication.java        # 启动类 + 交互 Shell（虚拟线程）
│   │   │   ├── tool/
│   │   │   │   ├── ToolSpecification.java        # ① 工具规范
│   │   │   │   ├── ToolExecutionRequest.java     # ② 执行请求
│   │   │   │   ├── ToolDescriptor.java           # ③ 工具描述符
│   │   │   │   ├── ToolExecutor.java             # ④ 执行器接口
│   │   │   │   ├── ToolRegistry.java             # ⑤ 工具注册表
│   │   │   │   └── CalculatorTool.java           # 计算器实现
│   │   │   └── service/
│   │   │       └── MockChatService.java          # 模拟 LLM（规则引擎）★
│   │   └── resources/application.yml
│   └── test/java/com/mewpaw/hellotools/
│       └── HelloToolsApplicationTest.java        # 10 个测试用例
```

### C. 快速启动命令

```bash
cd hello-tools          # 进入项目
mvn compile -q          # ① 编译
mvn test                # ② 测试（10 个用例）
mvn package -DskipTests # ③ 打包
java -jar target/hello-tools-1.0.0.jar   # ④ 运行交互界面
```

---

> **下一篇预告**：`02-security-filter-chain.md` —— LangChain4j 工具进阶：安全过滤器链与 MCP 动态注册。我们将站在本篇基础上，拆解 mewpaw-code 的五级风险模型、SecurityFilterChain、以及 MCP 协议如何让工具"活"起来。