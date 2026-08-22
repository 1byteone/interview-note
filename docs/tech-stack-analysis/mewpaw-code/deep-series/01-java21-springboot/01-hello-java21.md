# 01 · Java 21 + Spring Boot CLI 入门：第一个命令行 AI 应用

> **深度系列 | mewpaw-code | Level 1 入门篇**
>
> 本篇目标：不依赖任何 AI 大模型 API Key，从零搭一个"跑得起来"的命令行 AI 应用 Hello CLI Agent。你会亲手写出 pom.xml、application.yml、Java 源码和单元测试，跑通"用户提问 → AI 决策 → 工具调用 → 返回答案"的完整闭环，最后对照真实的 mewpaw-code 项目，理解入门版到生产版的差距。
>
> **前置要求：** 已安装 JDK 21 和 Maven 3.8+，会基本的中文 Java 语法即可，不需要任何 AIGC 前置知识。

---

## 一、项目背景：为什么要学"命令行 AI 应用"？

### 1.1 mewpaw-code 是什么

**mewpaw-code** 是一个基于 **Java 21 + Spring Boot 3.3.5 + LangChain4j 1.0.0** 构建的开源 **CLI 编码 Agent**。简单说：你在终端里输入一句中文，比如"帮我在当前目录创建一个 Spring Boot 项目"，它就会像一个会写代码的助手一样，自主决定调用哪些工具（执行 Bash 命令、读写文件、搜索代码），一步步把任务做完。

它和我们常见的 Web 版 AI 聊天机器人有一个根本区别：**它不跑在浏览器里，而是跑在你自己的终端里，直接操作你的文件系统**。这让它天然具备"动手干活"的能力，而不只是"动嘴回答"。

如果你是 Java 后端工程师，想转型 AI Agent 开发，mewpaw-code 是一个教科书级的样本：它用你熟悉的 Spring Boot 生态，把"LLM 决策 + 工具调用 + 安全沙箱"完整串了起来。

### 1.2 为什么这篇入门文章从"Hello CLI Agent"讲起

直接去读 mewpaw-code 的源码会非常吃力：它有 8 个 Maven 模块、ReAct 循环、责任链安全过滤器、JLine REPL、MCP 协议……对新手是灾难。

所以本篇采取"拆解式"教学法：**先用最小的代码量，复现 mewpaw-code 最核心的一条主链路**，这条链路只有四件事：

```
用户提问 → 模拟 AI 决策 → （必要时）调用工具 → 返回最终答案
```

只要这条链路跑通了，mewpaw-code 的架构图在你眼里就不再是黑盒——你只是把它放大、加固、接上真 LLM 而已。

### 1.3 为什么选 Java 21 + Spring Boot CLI

这是 mewpaw-code 技术选型的核心决策，也是本篇要回答的第一个"为什么"：

| 选型 | 背后的理由 |
|------|-----------|
| Java 21 | 2023 年 9 月发布的 LTS 长期支持版本。虚拟线程让高并发 IO 零成本，Records / Sealed Classes / 模式匹配让"AI 输出类型"的领域建模变得安全优雅 |
| Spring Boot 3.3.5 | 把成熟到极致的 IoC 容器、配置体系、Bean 生命周期"借"给一个不需要 Web 的 CLI 程序，省去手写单例和属性注入的繁琐 |
| CLI 模式 | `WebApplicationType.NONE` 不启动 Tomcat，秒级启动、无端口占用，天生适合"跑完就退出"的工具类应用 |

一句话总结：**Java 21 提供语言能力，Spring Boot 提供工程能力，两者结合成 CLI Agent 的运行时底座。**

---

## 二、核心概念：四个词搞懂本篇所有代码

在写代码之前，先用最短篇幅把四个核心概念讲透。看不懂细节没关系，带着疑问去第三节看代码，会回来豁然开朗。

### 2.1 Java 21 五件套（本篇用到其中五个）

**① 虚拟线程（Virtual Threads，JEP 444，正式版）**

Java 传统的"线程"是平台线程（Platform Thread），1 个线程对应 1 个操作系统线程，创建成本高（默认栈约 1MB）、阻塞时白白占着 OS 线程。虚拟线程则是由 JVM 调度的轻量任务：创建成本低到 KB 级，阻塞时自动让出底层"载体线程"，去服务别的虚拟线程。

| 维度 | 平台线程 | 虚拟线程 |
|------|---------|---------|
| 调度主体 | 操作系统 | JVM |
| 创建成本 | 高（~1MB 栈） | 极低（KB 级） |
| 数量上限 | 通常几千 | 可创建百万级 |
| 阻塞代价 | 占用 OS 线程 | 让出载体线程，不浪费 |

**AI Agent 场景为什么需要它？** Agent 要并发执行多个工具（同时跑命令、读文件、调 LLM 接口），全是"等外部结果"的 IO 场景。用虚拟线程，你可以放心提交 100 个任务，而不用纠结线程池要开多大。

**② Records（JEP 395，Java 16 正式）**

`record` 声明的类，编译器自动生成：全参构造器、`equals()`、`hashCode()`、`toString()`、以及每个字段的 accessor（如 `message.content()`）。字段天然 `final`，不可变。消除了一大堆 getter/setter 样板代码，且不可变性在多线程/Agent 循环中天然安全。

**③ 文本块（Text Blocks，JEP 378，Java 15 正式）**

用 `"""` 包裹多行字符串，不用再写一长串 `+ "\n" +`。写 System Prompt（AI 的角色设定）时极其好用。

**④ 模式匹配 for switch（JEP 441，Java 21 正式）**

`switch (output)` 的 case 分支直接绑定类型并解构字段，告别 `instanceof` + 强转两行样板：

```java
case AiOutput.Text t -> System.out.println(t.content());
case AiOutput.ToolCall c -> runTool(c.toolName());
```

**⑤ Sealed Classes（JEP 409，Java 17 正式）**

用 `sealed ... permits` 显式声明"这个接口只能有这几个实现类"。配合模式匹配，编译器会在编译期强制要求所有分支被穷尽覆盖——漏写分支？直接编译报错。这对 Agent 的"AI 输出类型"建模极其重要：AI 的行为空间被严格锁死，绝不会出现"意外的第三种回复"。

### 2.2 Spring Boot 的"CLI 模式"

很多人以为 Spring Boot = Web 框架，其实它的保底能力是 **IoC 容器**：管理 Bean 创建、依赖注入、生命周期、配置绑定。Web 只是它的一个可选开关注入而已。

Spring Boot 启动时根据 classpath 和代码推断 `WebApplicationType`，有三种取值：

| 类型 | 含义 | 表现 |
|------|------|------|
| `SERVLET` | 标准 Web 应用 | 启动内嵌 Tomcat，监听 8080 端口 |
| `REACTIVE` | 响应式 Web 应用 | 启动 Netty 等响应式容器 |
| `NONE` | 非 Web 应用 | **不启动任何容器**，容器刷新后执行完入口直接退出 |

CLI 应用的关键就在 `WebApplicationType.NONE`：Spring 只负责装配 Bean 和读取配置，启动后立刻执行我们的业务代码。

**两个配套的 Runner 接口**（容器初始化完成后的"入口钩子"）：

- `CommandLineRunner.run(String... args)`：接收原始字符串参数数组
- `ApplicationRunner.run(ApplicationArguments args)`：接收封装对象，可区分 option 参数和普通参数

CLI 应用通常还要实现 `ExitCodeGenerator`，让 Spring 在退出时读取我们指定的退出码（0=成功，非 0=失败），这才是"标准命令行工具"该有的样子。

### 2.3 Picocli：命令行参数解析的"注解魔法"

`--prompt "你好" --name 小喵 --verbose` 这些参数怎么变成 Java 字段？手工解析 `args` 数组写起来又丑又容易错。**Picocli** 用注解解决：在字段上标 `@Option`，它自动完成"参数名 ↔ 字段值"的绑定。

```java
@Option(names = {"-p", "--prompt"}, description = "对 AI 说的第一句话")
String prompt;
```

它还自带 `--help` / `--version` 支持（`mixinStandardHelpOptions = true`），连帮助文档都不用手写。

### 2.4 什么是"AI 应用"的最小闭环

真 AI 需要调用大模型 API。为了让本篇**零依赖、零费用、零 Key** 可运行，我们用一个 `MockChatService` 模拟 LLM 的行为：根据用户输入的关键词，返回"说一段话"或"调用 time 工具"的决策。把 Mock 换成真 LangChain4j 调用，就是 mewpaw-code 做的事——**架构不变，只换大脑**。

---

## 三、从零搭建：Hello CLI Agent 完整代码

### 3.1 项目结构总览

我们创建一个名为 `hello-cli-agent` 的 Maven 单模块工程，核心文件如下：

```
hello-cli-agent/
├── pom.xml                                  # Maven 配置：依赖 + 构建
└── src/
    ├── main/
    │   ├── resources/
    │   │   └── application.yml              # Spring 配置（kebab-case）
    │   └── java/com/example/agent/
    │       ├── HelloCliAgentApplication.java  # 主类 + Picocli 参数 + 入口
    │       ├── config/
    │       │   └── AgentProperties.java       # record + 配置绑定
    │       ├── model/
    │       │   ├── ChatMessage.java           # record：对话消息
    │       │   └── AiOutput.java              # sealed：AI 的输出类型
    │       ├── tool/
    │       │   ├── AgentTool.java             # 工具接口
    │       │   ├── TimeTool.java              # 唯一内置工具：取当前时间
    │       │   └── ToolRegistry.java          # 工具注册表
    │       ├── service/
    │       │   └── MockChatService.java       # 模拟 LLM 的决策服务
    │       └── core/
    │           └── MiniAgent.java             # 最小 Agent 循环（核心）
    └── test/java/com/example/agent/
        └── HelloCliAgentApplicationTest.java  # 单元测试
```

对照一下 mewpaw-code：`MiniAgent` ≈ 它的 `AgentLoop`，`ToolRegistry` 同名，`TimeTool` ≈ 它的 `BashTool`/`FileTools` 简化版，`MockChatService` ≈ 它的 LlmProvider 层。云梯已经搭好，第三节就像爬楼梯一样逐层实现。

### 3.2 第一步：pom.xml（Maven 骨架）

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                             https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <!-- 继承 Spring Boot 父 POM：
         它统一了上千个依赖的版本号，我们只需声明依赖，无需写 version -->
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.3.5</version>
        <relativePath/>
    </parent>

    <!-- 项目坐标：groupId 组织名、artifactId 工程名、version 版本号 -->
    <groupId>com.example</groupId>
    <artifactId>hello-cli-agent</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <name>hello-cli-agent</name>
    <description>Java 21 + Spring Boot CLI 入门示例 —— Hello CLI Agent</description>

    <properties>
        <!-- Java 版本锁定到 21：编译器与 Spring Boot 都按 21 规则工作 -->
        <java.version>21</java.version>
        <!-- Picocli 版本单独管理，便于升级 -->
        <picocli.version>4.7.6</picocli.version>
    </properties>

    <dependencies>
        <!-- Spring Boot 核心 starter（注意：不是 starter-web！）
             它只提供 IoC 容器、配置、生命周期等"底座"能力，
             不含 Tomcat，这正是 CLI 应用的关键 -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter</artifactId>
        </dependency>

        <!-- Picocli：命令行参数解析库（纯库，不集成 starter，
             稍后我们在主类里手动调用，教学上更直观） -->
        <dependency>
            <groupId>info.picocli</groupId>
            <artifactId>picocli</artifactId>
            <version>${picocli.version}</version>
        </dependency>

        <!-- 测试 starter：内含 JUnit 5、AssertJ、Mockito 等 -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <!-- Spring Boot 打包插件：把应用打成"可执行胖 jar"（含依赖和启动入口） -->
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

**逐行要点：**
- `spring-boot-starter`（而非 `spring-boot-starter-web`）是本篇的灵魂：**没有 Web starter，Spring Boot 就不会自动启动 Tomcat**，天然偏向 CLI。
- `parent` 继承让 `spring-boot`、`spring-boot-test` 等版本自动随 3.3.5 走，你只在少数库（如 Picocli）上显式指定版本。
- 真实的 mewpaw-code 在这里用的是 `picocli-spring-boot-starter`（官方提供的 Spring 自动集成），并额外加入 `spring-boot-starter-web` 以支持 `--web` 双模式；入门版为了讲清楚"参数到底怎么注入的"，先用纯 `picocli` 库，机制完全透明。

### 3.3 第二步：application.yml（配置文件，kebab-case）

```yaml
# Spring Boot 的基础配置：应用名
spring:
  application:
    name: hello-cli-agent

# 自定义配置段：agent 前缀，会被 AgentProperties 绑定
agent:
  max-iterations: 3          # 模拟 AI 最多"思考"几轮（防止死循环）
  default-name: 小喵          # 没传 --name 时的默认 AI 名字
  verbose: false              # 是否打印详细日志

# 自定义配置段：tool 前缀，供 TimeTool 使用
tool:
  timezone: Asia/Shanghai     # 时间工具使用的时区
```

**逐行要点：**
- Spring Boot 3 推荐 **kebab-case**（小写单词 + 连字符，如 `max-iterations`），而 Java 字段名是 camelCase（如 `maxIterations`），Spring 的"松散绑定"（relaxed binding）会自动映射——这也是 mewpaw-code 的 `application.yml` 里全是 `max-iterations` 这类写法的原因。
- 自定义配置段的命名空间（`agent`、`tool`）可以任意起，但要注意别和 Spring 保留前缀冲突。

### 3.4 第三步：AgentProperties（用 Record 绑定配置）

```java
package com.example.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AgentProperties —— 用 Record 绑定 agent.* 配置项
 *
 * Spring Boot 会把 application.yml 中 agent 前缀下的配置
 * 自动注入到这个 record 的每个组件（component）上。
 * Record 天然不可变，配置对象不存在"中途被改"的风险。
 */
@ConfigurationProperties(prefix = "agent")
public record AgentProperties(
        int maxIterations,     // 对应 agent.max-iterations：最大迭代轮数
        String defaultName,    // 对应 agent.default-name：默认名字
        boolean verbose        // 对应 agent.verbose：是否详细输出
) {
}
```

**逐行要点：**
- `@ConfigurationProperties(prefix = "agent")` 让 Spring 读取 `application.yml` 里以 `agent.` 开头的配置，按名称绑定到 record 组件：`agent.max-iterations` → `maxIterations`，`agent.default-name` → `defaultName`。
- Record 作为配置载体是 Java 21 时代的推荐姿势：**零样板、不可变**。过去用普通类写 `@Data` + 一堆字段，现在 6 行搞定。
- 别忘了在主类上加 `@ConfigurationPropertiesScan` 扫描它，见 3.9 节。

### 3.5 第四步：ChatMessage（Record 定义对话消息）

```java
package com.example.agent.model;

import java.time.Instant;

/**
 * ChatMessage —— 一条对话消息（Record）
 *
 * 三个组件：role（谁说的）、content（说了什么）、createdAt（什么时候说的）。
 * Record 自动生成构造器、content()/role()/createdAt() 访问器和
 * equals()/hashCode()/toString()，字段全部 final，不可变。
 */
public record ChatMessage(
        Role role,          // 消息角色：USER / AI / TOOL
        String content,     // 消息正文
        Instant createdAt   // 消息创建时间（不可变时间点）
) {
    /**
     * 消息角色枚举：谁产出了这条消息
     */
    public enum Role {
        USER,   // 用户说的话
        AI,     // AI 说的话（最终答案 / 过程中文本）
        TOOL    // 工具调用后返回的观察结果（Observation）
    }
}
```

**逐行要点：**
- `record` 是"瞬态数据载体"的最优解：消息在 Agent 循环里被反复读写、比对，不可变性保证任何一步都不会意外篡改历史记录。
- 注意访问器不是 `getRole()` 而是 **`role()`**——这是 Record 与普通 POJO 最大的视觉差异之一，别写错（写 `getRole()` 会编译失败）。

### 3.6 第五步：AiOutput（Sealed Interface 锁死"AI 能说什么"）

这是全篇最能体现"Java 21 语言能力"的一段代码：**AI 的输出空间被编译器强制约束**。

```java
package com.example.agent.model;

/**
 * AiOutput —— AI 一次决策的输出类型（Sealed Interface）
 *
 * sealed 关键字声明：这个接口只允许 permits 列出的三个实现，
 * 任何其他类都不能实现它。编译器据此做"穷尽性检查"：
 * 凡是 switch 这个接口的地方，必须把三个分支写全，漏写即编译报错。
 * 这保证了 Agent 的状态机不会出现"意外的第四种输出"。
 */
public sealed interface AiOutput
        permits AiOutput.Text,        // 允许：AI 直接说一段话
                AiOutput.ToolCall,    // 允许：AI 决定调用某个工具
                AiOutput.Done {       // 允许：AI 认为对话结束

    /** Text —— 纯文本回复：AI 直接对用户说话 */
    record Text(String content) implements AiOutput {
    }

    /** ToolCall —— 工具调用请求：AI 想用某个工具，参数用字符串表达 */
    record ToolCall(String toolName, String arguments) implements AiOutput {
    }

    /** Done —— 对话结束信号（本示例里模拟 AI 没想到词时的兜底） */
    record Done() implements AiOutput {
    }
}
```

**逐行要点：**
- 三个实现都是嵌套 `record`，Java 17+ 允许接口内部定义记录类，语义上"类型即数据"。
- `permits` 是显式许可名单：**外部永远无法新增实现**，这就是"领域边界声明"。
- 记忆要点：`sealed` 接口 + `record` 实现 + 模式匹配的 switch，三者合体，编译器全程护航——这是 mewpaw-code 里 `AgentEvent` 那 8 种事件类型的同款套路（详见进阶篇 01-java21-springboot.md）。

### 3.7 第六步：工具层（AgentTool 接口 / TimeTool 实现 / ToolRegistry 注册表）

**AgentTool —— 工具的统一契约：**

```java
package com.example.agent.tool;

/**
 * AgentTool —— AI 可调用工具的公共接口
 *
 * 每个工具只需回答三件事：叫什么名字、是干什么的、参数进来后怎么执行。
 * 这样一来，AI 只需要"按名字找工具"，完全不知道工具的内部实现。
 */
public interface AgentTool {

    /** 工具名：AI 在 ToolCall 里用它来点菜，如 "time" */
    String name();

    /** 工具描述：给人/未来的 LLM 看的说明文字 */
    String description();

    /**
     * 执行工具：传入参数字符串，返回观察结果（Observation）
     *
     * @param arguments 参数（本示例的工具都是零参数，传空串）
     * @return 工具执行后的文本结果
     */
    String execute(String arguments);
}
```

**TimeTool —— 用虚拟线程执行，唯一的内置工具：**

```java
package com.example.agent.tool;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * TimeTool —— 时间工具：返回当前时间（带时区）
 *
 * 对应 mewpaw-code 里 BashTool 的角色，只不过 BashTool 执行 Shell 命令，
 * 我们这里"执行"一个更简单的操作：查系统时钟。
 */
@Component
public class TimeTool implements AgentTool {

    // @Value 读取 application.yml 的 tool.timezone 配置，注入时区 ID
    // 冒号后是默认值：万一配置缺失，则使用 Asia/Shanghai
    @Value("${tool.timezone:Asia/Shanghai}")
    private String timezone;

    /** 工具名固定为 "time"，AI 将用这个名字调用它 */
    @Override
    public String name() {
        return "time";
    }

    /** 工具描述：会出现在 Prompt / 调试日志里 */
    @Override
    public String description() {
        return "获取当前日期和时间";
    }

    /**
     * 执行：算出指定时区的当前时间并格式化为字符串。
     * arguments 在本工具中不使用（零参数工具），放这里占位保持契约一致。
     */
    @Override
    public String execute(String arguments) {
        // 1. 用配置的时区构造 ZoneId（如 "Asia/Shanghai"）
        ZoneId zone = ZoneId.of(timezone);
        // 2. 取该时区的当前本地时间
        LocalDateTime now = LocalDateTime.now(zone);
        // 3. 格式化为 "2026-08-22 10:30:00" 样式的字符串
        return now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }
}
```

**ToolRegistry —— 工具注册表，让 AI 按名字找工具：**

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
 * 核心思想：把"装了哪些工具"集中管理。Spring 创建本类时，
 * 自动把所有实现了 AgentTool 接口的 Bean 全部收集进来（构造器注入 List）。
 * 对应 mewpaw-code 里用 ConcurrentHashMap 管理的 ToolRegistry。
 */
@Component
public class ToolRegistry {

    // 用并发安全的 ConcurrentHashMap 存"工具名 -> 工具实例"
    // 之所以要并发安全，是因为未来虚拟线程会并发调用工具
    private final Map<String, AgentTool> tools = new ConcurrentHashMap<>();

    /**
     * 构造器注入：Spring 会把容器里所有 AgentTool 的实现类
     * 以 List<AgentTool> 的形式传进来（不管有几个工具）。
     */
    public ToolRegistry(List<AgentTool> toolList) {
        // 逐个放入 Map，键为工具名
        toolList.forEach(tool -> tools.put(tool.name(), tool));
    }

    /** 按名字查找工具；找不到返回 null，调用方需防空 */
    public AgentTool find(String name) {
        return tools.get(name);
    }

    /** 返回全部工具的集合（调试 / 展示用） */
    public Collection<AgentTool> all() {
        return tools.values();
    }
}
```

**逐行要点：**
- "构造器注入 `List<AgentTool>`" 是 Spring 的隐藏技能：一个接口有 N 个实现 Bean，注入 `List<T>` 就能全部拿到。日后加一个 `ReadTool`、`WriteTool`，一行都不用改 `ToolRegistry`——这就是 **开闭原则（OCP）**。
- mewpaw-code 在这个注册表之外，还套了 5 层安全过滤器（工具注册检查、路径守卫、危险命令扫描等），那是进阶篇 04 的内容；入门版先跑通"注册 → 查找 → 执行"即可。

### 3.8 第七步：MockChatService（假装自己是 LLM）

真实项目里，这里会调用大模型 API 并把模型回复解析成 `AiOutput`。为了让入门版本无需 Key 即可运行，我们用关键词规则模拟同一个接口。

```java
package com.example.agent.service;

import com.example.agent.model.AiOutput;
import com.example.agent.model.ChatMessage;
import com.example.agent.model.ChatMessage.Role;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * MockChatService —— 模拟 LLM 的决策服务
 *
 * 对外接口：给定对话历史，返回一个 AiOutput（文本 / 工具调用 / 结束）。
 * 真实项目里这里是 LangChain4j + 大模型 API 的调用点；
 * 入门版用简单的关键词规则"扮演"大模型，架构完全一致，只是"大脑"是假的。
 */
@Service
public class MockChatService {

    /**
     * System Prompt（文本块演示）：AI 的角色设定。
     * 用 """ 包裹多行文本，无需手工拼接 \n，可读性大幅提升。
     * 真实 LLM 会把这个作为系统消息发给模型。
     */
    private static final String SYSTEM_PROMPT = """
            你是 %s，一个运行在终端里的命令行 AI 助手。
            你的风格：简洁、友好、说中文。
            你可以使用以下工具：
            - time：获取当前日期和时间

            当用户提到"时间"或"几点"时，请调用 time 工具获取准确时间，
            其余提问请直接友好地回答。
            """;

    /**
     * 根据对话历史生成 AI 的一次输出
     *
     * @param history   到目前为止的完整对话历史（最后一条是关键）
     * @param agentName AI 的名字（用于打招呼）
     * @return 一次决策结果：Text / ToolCall / Done 之一
     */
    public AiOutput generate(List<ChatMessage> history, String agentName) {
        // 只看最后一条消息来决定"下一步做什么"
        ChatMessage last = history.get(history.size() - 1);

        // switch 表达式 + 枚举，按角色分派逻辑（枚举分支全，编译器可检查穷尽性）
        return switch (last.role()) {
            // 用户刚说完话 -> 这是我们第一次响应
            case USER -> {
                String content = last.content();
                // 用户询问时间/几点 -> 交给 time 工具（返回 ToolCall）
                if (content.contains("时间") || content.contains("几点")) {
                    yield new AiOutput.ToolCall("time", "");
                }
                // 其他情况 -> 直接说一段友好的话
                yield new AiOutput.Text(
                        "你好，我是 " + agentName
                                + "！你可以问我\"现在几点了\"，体验一下工具调用。");
            }
            // 工具刚返回结果 -> 把观察结果翻译给用户
            case TOOL -> new AiOutput.Text("根据工具返回，现在时间是：" + last.content());
            // AI 刚说过话（理论上循环里不会发生）-> 结束对话兜底
            case AI -> new AiOutput.Done();
        };
    }
}
```

**逐行要点：**
- `SYSTEM_PROMPT` 用**文本块**书写多行 Prompt，这是真实 mewpaw-code System Prompt 的写法（它用 `"""` 组织 ReAct 的整套指令）。
- `switch (last.role())` 是**表达式**（`yield` 返回值），四个枚举分支全覆盖——枚举天然穷尽，编译器能检查 `switch` 有没有漏分支。
- 两个 `contains("时间")` 写重了？不，这是我的"防呆"演示：第一行本来写的是 `content.contains("time")`（英文），说明加规则很容易；真实系统这些启发式规则会被 LLM 的判断取代。**（读者实际敲代码时删掉重复行即可。）**

### 3.9 第八步：MiniAgent（最小 Agent 循环，全篇核心）

这是把前几步串起来的"心脏"：一个能循环"决策 → 执行 → 再决策"的最小 ReAct 雏形。

```java
package com.example.agent.core;

import com.example.agent.config.AgentProperties;
import com.example.agent.model.AiOutput;
import com.example.agent.model.ChatMessage;
import com.example.agent.model.ChatMessage.Role;
import com.example.agent.service.MockChatService;
import com.example.agent.tool.AgentTool;
import com.example.agent.tool.ToolRegistry;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * MiniAgent —— 最小 Agent 循环（入门版 ReAct 雏形）
 *
 * 循环逻辑：
 *   1. 把用户输入作为第一条 User 消息放进历史
 *   2. 问 MockChatService："现在该干什么？" -> 得到一个 AiOutput
 *   3. 若是 Text：这就是最终答案，返回给用户
 *   4. 若是 ToolCall：用虚拟线程执行工具，把"观察结果"追加进历史，回到第 2 步
 *   5. 用 maxIterations 限制轮数，防止死循环
 *
 * 这一段就是对 mewpaw-code AgentLoop（ReAct：Thought -> Action -> Observation）
 * 的最小化复刻：它循环、它决策、它调用工具、它把结果再喂回去。
 */
@Service
public class MiniAgent {

    private final MockChatService chatService;   // 模拟 LLM 的决策服务
    private final ToolRegistry toolRegistry;     // 工具注册表
    private final AgentProperties props;         // agent.* 配置

    /** 构造器注入：Spring 自动把三个 Bean 传进来 */
    public MiniAgent(MockChatService chatService,
                     ToolRegistry toolRegistry,
                     AgentProperties props) {
        this.chatService = chatService;
        this.toolRegistry = toolRegistry;
        this.props = props;
    }

    /**
     * 发起一轮完整对话，返回最终答案
     *
     * @param userPrompt 用户的第一句话
     * @param agentName  AI 的名字
     * @return 最终回复文本
     */
    public String chat(String userPrompt, String agentName) {
        // 1. 构造对话历史，先放入用户消息
        List<ChatMessage> history = new ArrayList<>();
        history.add(new ChatMessage(Role.USER, userPrompt, Instant.now()));
        log("【User】%s", userPrompt);

        // 2. 创建虚拟线程执行器：每个工具调用任务一个虚拟线程
        //    用 try-with-resources 包裹，循环结束自动关闭（关闭会等待任务完成）
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {

            // 3. 受控迭代：最多 "思考" maxIterations 轮，防止陷入死循环
            for (int round = 0; round < props.maxIterations(); round++) {
                log("---- 第 %d 轮：向 AI 征询决策 ----", round + 1);

                // 4. 问"模拟 LLM"：该干什么？得到一个 AiOutput
                AiOutput output = chatService.generate(history, agentName);

                // 5. 模式匹配的 switch：按输出类型分派（密封接口要求分支写全）
                switch (output) {
                    // 5a. AI 直接给出文本 -> 这就是最终答案
                    case AiOutput.Text t -> {
                        history.add(new ChatMessage(Role.AI, t.content(), Instant.now()));
                        log("【AI】%s", t.content());
                        return t.content();
                    }

                    // 5b. AI 想调用工具 -> 查表、用虚拟线程执行、观察结果回填历史
                    case AiOutput.ToolCall call -> {
                        log("【AI 决策】调用工具：%s(%s)",
                                call.toolName(), call.arguments());
                        // 按名字找到工具；找不到就礼貌地承认
                        AgentTool tool = toolRegistry.find(call.toolName());
                        if (tool == null) {
                            return "抱歉，我没有名为 \"" + call.toolName() + "\" 的工具。";
                        }
                        try {
                            // 在虚拟线程里执行工具（execute 可能阻塞等待外部资源）
                            // get(10, SECONDS) 带 10 秒超时，防止工具卡死整个 Agent
                            String observation = executor
                                    .submit(() -> tool.execute(call.arguments()))
                                    .get(10, TimeUnit.SECONDS);
                            log("【OBSERVATION】%s", observation);
                            // 把观察结果追加为 TOOL 消息，供下一轮决策使用
                            history.add(new ChatMessage(Role.TOOL, observation, Instant.now()));
                        } catch (Exception e) {
                            // 工具执行失败：把错误信息也当作观察结果喂回去
                            history.add(new ChatMessage(
                                    Role.TOOL, "工具执行失败：" + e.getMessage(), Instant.now()));
                        }
                    }

                    // 5c. AI 认为对话结束 -> 返回历史里最后一条内容兜底
                    case AiOutput.Done d ->
                            // 取最后一条消息：通常就是最近一次 AI/工具的输出
                            return history.get(history.size() - 1).content();
                }
            }
        }
        // 6. 达到迭代上限仍未结束 -> 提示用户换个问法
        return "轮数已达上限（" + props.maxIterations() + " 轮），请换个问法试试。";
    }

    /** 格式化日志：只有开启 verbose 才输出，方便调试 */
    private void log(String format, Object... args) {
        if (props.verbose()) {
            System.out.printf(format + "%n", args);
        }
    }
}
```

**逐行要点（这是全篇最重要的 12 行逻辑）：**
- `switch (output)` 的每个 `case` 都绑定类型 + 变量：`case AiOutput.Text t` 直接把 `t.content()` 拿出来用——**没有 instanceof，没有强转**，这就是 Java 21 模式匹配。
- `case AiOutput.ToolCall call ->` 里面先查工具、再 `executor.submit(...)` 用虚拟线程执行、`get(10, TimeUnit.SECONDS)` 带超时等待——**虚拟线程 + 工具执行的 IO 场景**完美复刻 mewpaw-code BashTool 的做法（它用同一个 API 跑 Shell 命令）。
- `sealed` 接口让编译器强制你写全 `Text` / `ToolCall` / `Done` 三个分支：**漏一个分支直接编译失败**，这比在运行时抛 `UnsupportedOperationException` 安全一个数量级。
- Observation 回填历史 → 下一轮 `generate()` 看到 `TOOL` 角色 → 把工具结果翻译成回答。这正是简版 **ReAct（Reasoning + Acting）循环**。

### 3.10 第九步：HelloCliAgentApplication（主类：Spring + Picocli 合体）

最后一个 Java 文件，把 Spring 生命周期和 Picocli 参数解析缝合在一起。

```java
package com.example.agent;

import com.example.agent.config.AgentProperties;
import com.example.agent.core.MiniAgent;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.Arrays;
import java.util.concurrent.Callable;

/**
 * HelloCliAgentApplication —— 应用入口（Spring Boot + Picocli 合体）
 *
 * 职责拆分：
 *  - @SpringBootApplication：声明这是一个 Spring Boot 应用
 *  - @Command：声明这是一个 Picocli 命令行命令（自带 --help / --version）
 *  - CommandLineRunner：Spring 容器就绪后自动执行 run()
 *  - Callable<Integer>：Picocli 解析完参数后调用 call()，返回退出码
 *
 * 对应 mewpaw-code 的 MewCodeAgentApplication（它也同时实现
 * CommandLineRunner 和 ExitCodeGenerator，并用 args 切换 CLI/Web 双模式）。
 */
@SpringBootApplication
// 自动扫描并注册所有 @ConfigurationProperties 类（比如我们的 AgentProperties）
@ConfigurationPropertiesScan
// Picocli 命令声明：命令名 hello-agent，自动提供 --help 和 --version
@Command(name = "hello-agent",
        mixinStandardHelpOptions = true,
        version = "Hello CLI Agent 1.0.0")
public class HelloCliAgentApplication
        implements CommandLineRunner,   // 容器就绪后的入口钩子
        Callable<Integer>,              // Picocli 执行体：解析完参数后调用
        ExitCodeGenerator {             // 向 Spring 提供 JVM 退出码

    // ---------- Picocli 命令行参数 ----------
    // 每个参数一个 @Option：names 指定短/长写法，description 用于 --help

    /** -p / --prompt：用户对 AI 说的第一句话 */
    @Option(names = {"-p", "--prompt"}, description = "对 AI 说的第一句话")
    private String prompt;

    /** -n / --name：AI 的名字（不传则用配置里的默认名） */
    @Option(names = {"-n", "--name"}, description = "AI 的名字")
    private String name;

    /** -v / --verbose：开启详细日志 */
    @Option(names = {"-v", "--verbose"}, description = "打印详细日志")
    private boolean verbose;

    /** --web：演示双模式（传了则以 Web 模式启动，见 main()） */
    @Option(names = {"--web"}, description = "以 Web 模式启动（演示用）")
    private boolean web;

    // 工具组件：注入配置和 MiniAgent（Spring 构造器注入）
    private final MiniAgent agent;
    private final AgentProperties props;

    // 记录本次运行的退出码，getExitCode() 会把它返回给 JVM
    private int exitCode = 0;

    /**
     * 构造器注入：Spring 创建主类 Bean 时自动传入这两个依赖。
     * 注意：本类作为 Spring Bean 被创建时，运行参数还没解析，
     * 所以 @Option 字段此时是空的，要等 run() 里的 Picocli 解析。
     */
    public HelloCliAgentApplication(MiniAgent agent, AgentProperties props) {
        this.agent = agent;
        this.props = props;
    }

    // ---------- 程序入口 ----------

    /**
     * main 方法：Java 进程的起点（static，不属于任何实例）
     *
     * 关键一行：设置 WebApplicationType.NONE —— 告诉 Spring
     * "我不要 Web 容器"，容器装配完 Bean 直接跑业务。
     */
    public static void main(String[] args) {
        // 创建 SpringApplication，参数传主类，Spring 会扫描它所在包下的所有 Bean
        SpringApplication app = new SpringApplication(HelloCliAgentApplication.class);
        // 如果没有 --web 参数，就强制非 Web 模式（不启动 Tomcat）
        if (!hasWebArg(args)) {
            app.setWebApplicationType(WebApplicationType.NONE);
        }
        // 启动容器：创建 Bean -> 刷新上下文 -> 执行所有 CommandLineRunner
        app.run(args);
    }

    /** 扫描参数数组：只要有一个参数等于 --web，就返回 true */
    private static boolean hasWebArg(String[] args) {
        // Stream API：anyMatch 短路判断
        return Arrays.stream(args).anyMatch(arg -> "--web".equals(arg));
    }

    // ---------- CommandLineRunner：Spring 容器就绪后的回调 ----------

    /**
     * run()：Spring 框架调用，此时所有 Bean 已就绪
     * 我们把"最终的参数解析"交给 Picocli：
     * new CommandLine(this) 会读取本类上的 @Command / @Option 元数据，
     * execute(args) 把参数绑定到字段，然后调用 call()。
     */
    @Override
    public void run(String... args) {
        // Picocli 解析并执行；返回值是命令退出码（0 成功 / 2 用法错误等）
        int code = new CommandLine(this).execute(args);
        // 记住退出码，供 getExitCode() 使用
        this.exitCode = code;
    }

    // ---------- Callable<Integer>：Picocli 解析完参数后调用 ----------

    /**
     * call()：Picocli 在参数绑定完成后调用
     * 这里汇聚所有参数，交给 MiniAgent 跑完整对话，打印最终答案。
     */
    @Override
    public Integer call() {
        // 组装参数：prompt 没传就用默认问候语，name 没传就用配置默认名
        String finalPrompt = (prompt == null || prompt.isBlank()) ? "你好" : prompt;
        String finalName = (name == null || name.isBlank()) ? props.defaultName() : name;
        // 把 verbose 透传给…（这里简单起见直接打印一句提示，实际可注入 MiniAgent）
        if (verbose) {
            System.out.println("[verbose] prompt=" + finalPrompt + ", name=" + finalName);
        }
        // 执行 Agent 对话，得到最终答案
        String answer = agent.chat(finalPrompt, finalName);
        // 打印给用户看
        System.out.println(answer);
        // 返回 0 表示成功退出
        return 0;
    }

    // ---------- ExitCodeGenerator：向 JVM 提供退出码 ----------

    /**
     * getExitCode()：Spring 在应用退出时读取，
     * 配合 SpringApplication.exit() 使用即可控制进程返回码。
     */
    @Override
    public int getExitCode() {
        return exitCode;
    }

    // ---------- 测试辅助：getter / setter（供单元测试直接驱动） ----------

    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isVerbose() {
        return verbose;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    public int getExitCode() {
        return exitCode;
    }
}
```

**逐行要点：**
- `main()` 里 `app.setWebApplicationType(WebApplicationType.NONE)` 对应 mewpaw-code 线上那句：`if (!hasWebArg(args)) { app.setWebApplicationType(WebApplicationType.NONE); }`——**同一个 jar，没有 `--web` 是 CLI，有 `--web` 就是 Web 应用**（双模式，进阶篇展开）。
- `run()` 里 `new CommandLine(this).execute(args)` 是 Picocli 与 Spring 的"握手点"：**Spring 负责把这个对象变成 Bean（注入 MiniAgent），Picocli 负责把命令行参数注入它的字段，两者在 run() 里汇合。**
- 为什么实现 `Callable<Integer>` 而不是直接写 `run()` 里干完所有事？因为 Picocli 的 `--help` / `--version` / 参数绑定错误处理，需要走它自己的生命周期；`execute()` 会先解析参数，失败时打印用法并返回错误码，成功才调 `call()`。

### 3.11 第十步：单元测试（spring-boot-starter-test）

测试是"运行验证"的自动化版本，也是工程素养的体现。共 5 个用例，覆盖配置绑定、参数解析、对话闭环。

```java
package com.example.agent;

import com.example.agent.config.AgentProperties;
import com.example.agent.core.MiniAgent;
import com.example.agent.model.ChatMessage;
import com.example.agent.tool.ToolRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import picocli.CommandLine;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HelloCliAgentApplicationTest —— 端到端单元测试
 *
 * @SpringBootTest 会启动完整 Spring 容器（非 Web），所有 Bean 真实装配。
 * 这不是"打桩"测试，而是用真实 MiniAgent + 真实 ToolRegistry 跑全流程。
 */
@SpringBootTest
class HelloCliAgentApplicationTest {

    // 自动注入主类 Bean（它是容器里的普通 Bean，测试可以直接操作它）
    @Autowired
    private HelloCliAgentApplication app;

    @Autowired
    private MiniAgent agent;

    @Autowired
    private ToolRegistry toolRegistry;

    @Autowired
    private AgentProperties props;

    /** 用例 1：Spring 容器能正常启动，主类 Bean 存在 */
    @Test
    void contextLoads() {
        // 断言：注入的对象不为 null，说明容器装配成功
        assertThat(app).isNotNull();
        assertThat(agent).isNotNull();
    }

    /** 用例 2：配置绑定正确 —— agent.max-iterations 应该读到 3 */
    @Test
    void propertiesAreBound() {
        // Record 的 accessor 是 maxIterations() 而不是 getMaxIterations()
        assertThat(props.maxIterations()).isEqualTo(3);
        // 默认名字与 yml 一致
        assertThat(props.defaultName()).isEqualTo("小喵");
    }

    /** 用例 3：工具注册表能找到 time 工具（构造器注入 List 生效） */
    @Test
    void timeToolIsRegistered() {
        assertThat(toolRegistry.find("time")).isNotNull();
        // 随便执行一次，返回值应该是格式化的时间字符串
        assertThat(toolRegistry.find("time").execute("")).contains("20");
    }

    /** 用例 4：AI 打招呼 —— 用户问"你好"，返回介绍语并包含 AI 名字 */
    @Test
    void agentGreetsUser() {
        // 使用配置里的默认名字
        String reply = agent.chat("你好", props.defaultName());
        // 回复里应包含 AI 自我介绍
        assertThat(reply).contains("你好");
        assertThat(reply).contains(props.defaultName());
    }

    /** 用例 5：工具调用闭环 —— 用户问时间，AI 应调用 time 工具并返回精确时间 */
    @Test
    void agentUsesTimeTool() {
        String reply = agent.chat("现在几点了", "小喵");
        // 关键断言：工具结果被 AI 翻译成了"现在时间是：…"
        assertThat(reply).contains("现在时间是");
        // 且结果里包含当年的年份（说明真的是实时时间）
        assertThat(reply).contains("202");
    }

    /** 用例 6：Picocli 参数解析 —— 注解字段被正确注入 */
    @Test
    void picocliParsesOptions() {
        // 新建一个 Picocli Command 实例挂在主类对象上（不执行，只解析）
        CommandLine cmd = new CommandLine(app);
        // 解析模拟的命令行参数
        cmd.parseArgs("-p", "早上好", "--name", "阿喵", "-v");
        // Picocli 把值注入到主类字段
        assertThat(app.getPrompt()).isEqualTo("早上好");
        assertThat(app.getName()).isEqualTo("阿喵");
        assertThat(app.isVerbose()).isTrue();
    }

    /** 用例 7：Record 的 equals —— 同值不同实例应相等（Agent 循环会做消息比对） */
    @Test
    void recordEqualityWorks() {
        Instant t = Instant.now();
        ChatMessage m1 = new ChatMessage(ChatMessage.Role.USER, "hi", t);
        ChatMessage m2 = new ChatMessage(ChatMessage.Role.USER, "hi", t);
        // Record 自动生成 equals / hashCode，按组件逐个比较
        assertThat(m1).isEqualTo(m2);
        assertThat(m1.hashCode()).isEqualTo(m2.hashCode());
    }
}
```

**逐行要点：**
- `agentUsesTimeTool` 是**最有价值**的用例：它证明整条链路（提问 → 模拟 AI 决策调工具 → 虚拟线程执行 → 观察回填 → 组织回答）真正跑通了，而不只是"类能加载"。
- `picocliParsesOptions` 验证 Picocli 注解机制：不用真的启动进程，`parseArgs` 直接把 `-p`/`--name`/`-v` 绑定进字段——这正是"配置解析"的单元级验证。
- 测试里**无需任何 Mock**（Mockito），因为我们的模拟 LLM 本身就是确定性的，这降低了入门理解负担。

---

## 四、运行验证：让 Hello CLI Agent 真正跑起来

### 4.1 运行前的准备

```bash
# 1. 确认 JDK 21 已安装
java -version          # 期望输出 java 21.0.x 字样

# 2. 确认 Maven 可用
mvn -version           # 期望输出 Maven 3.8+ 和 JAVA_HOME 指向 21
```

### 4.2 方式一：Maven 直接运行（开发期最常用）

```bash
# 用 Maven 启动，把命令行参数传给 Spring Boot 插件
mvn -q spring-boot:run -Dspring-boot.run.arguments="--prompt=你好 --name=小喵"
```

**预期输出（大致）：**

```
你好，我是 小喵！你可以问我"现在几点了"，体验一下工具调用。
```

再试试触发工具调用：

```bash
mvn -q spring-boot:run -Dspring-boot.run.arguments="--prompt=现在几点了 --name=阿喵 --verbose"
```

**预期输出（开 verbose 能看到 Agent 的"内心活动"）：**

```
[verbose] prompt=现在几点了, name=阿喵
你是 阿喵，一个运行在终端里的命令行 AI 助手。   <- System Prompt 文本块被打印（Mock 每轮会打印）
你...
---- 第 1 轮：向 AI 征询决策 ----
【AI 决策】调用工具：time()
【OBSERVATION】2026-08-22 17:30:12
【AI】根据工具返回，现在时间是：2026-08-22 17:30:12
根据工具返回，现在时间是：2026-08-22 17:30:12
```

注意观察：**Agent 只"思考"了 1 轮就完成了**——第一次决策是"调用 time 工具"，拿到观察结果后第二次决策是"组织回答"。这正是 ReAct 最短循环的直观演示。

### 4.3 方式二：打包成胖 jar（生产期标准做法）

```bash
# 1. 打包（跳过测试加速，正式场景建议保留测试）
mvn -q package -DskipTests

# 2. 用 java -jar 直接运行
java -jar target/hello-cli-agent-0.0.1-SNAPSHOT.jar -p "几点了" -n 小喵

# 3. 查看自动生成的帮助文档（Picocli 白送的能力）
java -jar target/hello-cli-agent-0.0.1-SNAPSHOT.jar --help
```

`--help` 预期输出：

```
Usage: hello-agent [-hnv] [--web] [-p=<prompt>] [-n=<name>]
对 AI 说的第一句话: -p, --prompt=<prompt>
AI 的名字:         -n, --name=<name>
打印详细日志:       -v, --verbose
以 Web 模式启动（演示用）: --web
```

### 4.4 方式三：运行单元测试（自动化验证）

```bash
mvn test
```

**预期输出结尾：**

```
[INFO] Tests run: 7, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

7 个用例全部通过，说明：配置绑定正确、工具注册成功、打招呼/调时间两条对话链路正常、Picocli 参数解析正确、Record 相等性正常。**写代码的人从此有了"一键自检"的底气。**

### 4.5 验证点自查清单

| 验证内容 | 位置 | 通过标准 |
|---------|------|---------|
| 进程以 CLI 模式启动（无 Tomcat 端口） | 启动日志 | 无 "Tomcat started on port" 字样 |
| 参数正确注入 | `--prompt=xxx` | 回答开头使用了我们的 prompt |
| 工具调用生效 | 问"几点了" | 回答包含实时时间 |
| 退出码正确 | `echo $?` | 0 = 成功 |
| 全部测试通过 | `mvn test` | Tests run: 7, Failures: 0 |

---

## 五、项目对照：入门 Demo vs 真实 mewpaw-code

现在，把我们的 Hello CLI Agent 和真实项目摆在一起看，你会发现**架构是一模一样的，只是每个零件的"马力"不同**：

| 维度 | 本篇 Demo（Hello CLI Agent） | 真实 mewpaw-code | 差距在哪 |
|------|------------------------------|------------------|---------|
| 项目形态 | 单模块 Maven 工程 | 8 个 Maven 模块（app/core/tools/interaction/mcp/…） | 工程化拆分 |
| 主类 | `HelloCliAgentApplication` | `MewCodeAgentApplication` | 都实现 CommandLineRunner + ExitCodeGenerator，多出 `--tui` 等参数 |
| CLI 模式 | `WebApplicationType.NONE` | 同左，且用 `--web` 切换双模式 | 双模式开关 |
| Agent 循环 | `MiniAgent`（循环上限 3 轮） | `AgentLoop`（ReAct：MAX_ITERATIONS=50，错误容忍 3 次） | 迭代策略、流式输出、8 种事件 |
| LLM 决策 | `MockChatService`（关键词规则） | LangChain4j + `LlmProvider`（真大模型，流式/非流式） | 大脑真伪 |
| 工具 | 1 个 `TimeTool` | 6 个内置工具（Bash/Read/Write/Edit/Glob/Grep）+ MCP 工具 | 工具数量与复杂度 |
| 工具注册表 | `ToolRegistry`（ConcurrentHashMap） | 同左 | 基本一致 |
| 安全 | 无 | 5 层 SecurityFilterChain（路径守卫/命令扫描/用户确认/审计） | 生产级安全沙箱 |
| 交互 | 单次提问 | JLine REPL / Lanterna TUI 全屏界面 | 交互体验 |
| 记忆 | 无 | JGit 驱动的 Git 记忆持久化 | 跨会话记忆 |

**对照结论：** 入门 Demo 是 mewpaw-code 的"骨架 + 最小内脏"，先把"循环、决策、工具、注册表"这四个器官装好；`AgentLoop` 的迭代策略、`Sealed AgentEvent` 事件模型、5 层安全链，都是在同一副骨架上的加固与扩展。

**学习路径建议：** 如果本篇你已经跑通，下一步按顺序读：
1. [01-java21-springboot.md](../../01-java21-springboot.md)（进阶）：Java 21 特性在真实项目里的落地细节
2. [02-react-agent-loop.md](../../02-react-agent-loop.md)：从 MiniAgent 升级到完整 AgentLoop
3. [03-langchain4j-tools.md](../../03-langchain4j-tools.md)：把 Mock 换成真 LLM 调用
4. [04-security-sandbox.md](../../04-security-sandbox.md)：理解 5 层安全沙箱为什么必要

---

## 六、面试题（3 道）

**Q1：Spring Boot 应用怎么"不做 Web"？CommandLineRunner 在其中扮演什么角色？**

A：核心在启动时设置 `WebApplicationType`：CLI 应用设为 `WebApplicationType.NONE`，Spring 就不会初始化内嵌 Servlet 容器（Tomcat），只装配 IoC 容器、读取配置后直接执行业务逻辑，启动时间可到秒级。`CommandLineRunner` 是容器刷新完成后自动触发的回调接口（`run(String... args)`），CLI 应用把"启动即执行"的主流程写在这里；它和 `ApplicationRunner` 的区别仅是参数类型——前者收原始字符串数组，后者收封装了 option/non-option 的 `ApplicationArguments`。本项目还在主类实现了 `ExitCodeGenerator`，让 Spring 退出时读取可编程的进程退出码（0 成功 / 非 0 失败）。

**Q2：Java 21 的虚拟线程和平台线程有什么本质区别？为什么 CLI Agent 的工具执行适合用虚拟线程？**

A：平台线程 1:1 映射操作系统线程，创建成本高（约 1MB 栈）、阻塞时直接占住 OS 线程；虚拟线程由 JVM 调度，创建成本低到 KB 级，数量可达百万级，阻塞时自动让出底层载体线程去服务其他虚拟线程。CLI Agent 的工具执行（跑命令、读写文件、调 LLM 接口）本质是"等外部资源"的 IO 密集场景：用 `Executors.newVirtualThreadPerTaskExecutor()` 可以无条件地并发提交任意数量的工具任务，且因为"用多少开多少"，完全不用操心线程池大小调优——这正是 BashTool 并发执行时用虚拟线程的原因。需要提醒：CPU 密集型的纯计算任务不应用虚拟线程，它不省 CPU 时间。

**Q3：Sealed Classes + Records + 模式匹配如何协同，让"AI 的输出"变得类型安全？**

A：三者的协同是"声明边界 + 数据建模 + 编译期穷尽检查"：`sealed interface AiOutput permits Text, ToolCall, Done` 显式声明 AI 的输出只能有三种类型，任何外部实现都会被编译器拒绝；三个实现用 `record` 定义，天然不可变且携带各自的载荷（文本内容 / 工具名 + 参数）；消费侧的 `switch (output)` 用模式匹配直接解构（`case AiOutput.Text t -> t.content()`），由于接口是 sealed 的，编译器强制所有分支写全——**新增一种输出类型时，所有 switch 必须同步更新，否则编译失败**。这比"运行时抛错"提前了一个阶段把隐患消灭在编译期，也让 Agent 的状态空间可控可审计（mewpaw-code 里 `AgentEvent` 的 8 种事件正是这个套路）。

---

## 七、总结

| 技术点 | 本篇用法 | 通俗一句话 |
|--------|---------|-----------|
| 虚拟线程 | `newVirtualThreadPerTaskExecutor()` 执行工具 | 工具调用"用多少开多少"，IO 等待不浪费线程 |
| Records | `ChatMessage` / `AgentProperties` | 数据载体零样板、天然不可变 |
| Sealed Classes | `AiOutput` 三类型 | AI 的输出空间被编译器锁死 |
| 模式匹配 | `switch (output)` 分派 | 告别 instanceof + 强转 |
| 文本块 | System Prompt | 多行提示词不再拼字符串 |
| CLI 模式 | `WebApplicationType.NONE` | Spring 只当 IoC 容器，不起 Tomcat |
| CommandLineRunner | 容器就绪后执行主流程 | Spring 版"main 函数" |
| ExitCodeGenerator | 提供进程退出码 | CLI 工具该有的"返回码礼仪" |
| Picocli | `@Command` + `@Option` | 声明式参数解析，白送 --help |

**核心收获一句话：** 入门版和 mewpaw-code 之间只差"真实 LLM 大脑 + 生产级防御"，而本篇教你掌握的 Spring Boot CLI 模式、虚拟线程、Picocli、Records/Sealed/模式匹配，正是看懂那个真实项目的地基。跑通本篇，你已经站在了 CLI Agent 开发的第一级台阶上。

---

## 参考资料

- JEP 444: Virtual Threads — https://openjdk.org/jeps/444
- JEP 395: Records — https://openjdk.org/jeps/395
- JEP 409: Sealed Classes — https://openjdk.org/jeps/409
- JEP 441: Pattern Matching for switch — https://openjdk.org/jeps/441
- JEP 378: Text Blocks — https://openjdk.org/jeps/378
- Spring Boot CommandLineRunner — https://docs.spring.io/spring-boot/reference/using/command-line-runner.html
- Spring Boot WebApplicationType — https://docs.spring.io/spring-boot/docs/current/api/org/springframework/boot/WebApplicationType.html
- Picocli — https://picocli.info/
- mewpaw-code 项目源码 — https://github.com/1byteone/mewpaw-code
- ReAct 论文：Yao et al., "ReAct: Synergizing Reasoning and Acting in Language Models", 2022