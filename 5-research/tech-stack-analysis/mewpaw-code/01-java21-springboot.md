# 01 · Java 21 + Spring Boot 3.3.5：CLI 编码 Agent 的运行时底座

> mwepaw-code 没走 Web 路线，而是把 Spring Boot 当"依赖注入容器"用在纯 CLI 场景。本篇拆解 Java 21 三大特性（虚拟线程、Records、Sealed Classes）如何落地，以及 Spring Boot CLI 模式（WebApplicationType.NONE）的完整玩法。
>
> **对应项目：** `com.mewcode` (mewcode-app / mewcode-core / mewcode-tools)

---

## 一、基础概念

### 1.1 为什么选 Java 21

| 特性 | 解决问题 | 在项目中的落点 |
|------|---------|---------------|
| 虚拟线程 (Virtual Threads) | 平台线程阻塞浪费、并发受限 | BashTool 异步执行 |
| Records | POJO 样板代码多、可变性风险 | SecurityResult / ToolDescriptor |
| Sealed Classes | 继承失控、switch 缺乏穷尽性检查 | AgentEvent 8 种事件类型 |
| 模式匹配 for switch | instanceof + 强转样板 | 事件分发 |
| 文本块 (Text Blocks) | 多行 Prompt 拼接难 | System Prompt |
| 实用 NPE 消息 | 空指针排障慢 | 调试期诊断 |

### 1.2 为什么用 Spring Boot 做 CLI 应用

传统直觉是"Spring Boot = Web 框架"，但本项目反其道而行：

- **IoC 容器管理 Bean 生命周期**：8 个模块的组件（AgentLoop、ToolRegistry、Filter 链）统一交给容器管理
- **配置注入**：`application.yml` 集中管理 LLM 配置、超时参数等
- **BOM 依赖管理**：Spring Boot 3.3.5 BOM 统一版本，父子模块继承
- **CommandLineRunner 天然入口**：容器就绪后自动执行，无需手写 main 逻辑
- **可插拔双模式**：同一次启动，`--web` 参数决定是否拉起 Web 容器

---

## 二、进阶机制

### 2.1 Java 21 虚拟线程：BashTool 的异步执行

虚拟线程 (Virtual Thread, JEP 444) 是 JDK 19 预览、JDK 21 正式的特性。核心差异：

| 维度 | 平台线程 (Platform Thread) | 虚拟线程 (Virtual Thread) |
|------|---------------------------|---------------------------|
| 调度单位 | OS 线程（1:1 映射内核） | JVM 调度的轻量任务（M:1 与内核映射） |
| 创建成本 | 高（~1MB 栈 + 系统调用） | 极低（KB 级，可创建百万级） |
| 阻塞代价 | 阻塞即占用 OS 线程 | 阻塞时自动让出载体线程 |
| 适用场景 | CPU 密集 / 需固定 OS 线程 | IO 密集（网络、磁盘、进程） |

**源码示例（BashTool 执行命令）：**

```java
// ① 创建虚拟线程执行器：每个任务一个虚拟线程
ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

// ② 提交 Bash 命令执行任务
Future<String> future = executor.submit(() -> {
    // 注意：这里只是个简单的执行回调
    // 实际执行逻辑会调用 ProcessBuilder 启动子进程
    return executeProcess(command, timeoutSeconds);
});

try {
    // ③ 带超时等待结果
    // 虚拟线程在 get() 阻塞时自动让出载体线程，不浪费 OS 线程
    return future.get(timeoutSeconds, TimeUnit.SECONDS);
} catch (TimeoutException e) {
    // ④ 超时则强制销毁子进程
    process.destroyForcibly();
    throw new ToolExecutionException("Command timeout after " + timeoutSeconds + "s");
}
```

**逐行注释：**

```java
// 行 1：Java 21 提供的虚拟线程执行器工厂方法
// 每次 submit 创建一个新的虚拟线程，任务结束自动回收，几乎零成本
ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

// 行 3：executor.submit(callable) 返回 Future
// 与普通线程池 API 完全一致，老代码迁移成本低
Future<String> future = executor.submit(() -> { ... });

// 行 5：get(timeout) 带超时等待
// 虚拟线程阻塞时底层载体线程被释放，可以继续服务其他虚拟线程
return future.get(timeoutSeconds, TimeUnit.SECONDS);
```

**为什么这里用虚拟线程而非 CompletableFuture + 线程池？**

- 平台线程池需要显式配置池大小，Bash 工具调用可能是 1 次也可能是 20 次并发
- 虚拟线程"用多少开多少"，无需池化，避免池满排队导致的级联超时
- CLI 场景下 LLM 可能连续要求执行多个命令，虚拟线程天然支持高并发 IO 等待

### 2.2 Records：不可变数据载体

Record (JEP 395) 是 Java 16 正式的特性：`record` 声明的类型自动生成构造器、`equals()`、`hashCode()`、`toString()` 和 accessor，字段默认 `final`。

**源码示例（SecurityResult）：**

```java
// Record 声明：三个组件 allowed / needsConfirm / reason
// 自动生成：全参构造器、getter（allowed()、needsConfirm()、reason()）、equals、hashCode、toString
public record SecurityResult(
        boolean allowed,      // 是否允许执行
        boolean needsConfirm, // 是否需要用户确认
        String reason         // 拒绝/确认原因
) {
    // 静态工厂方法：语义化构造"放行"结果
    // 相比直接 new SecurityResult(true, false, null) 可读性更强
    public static SecurityResult allow() {
        return new SecurityResult(true, false, null);
    }

    // 静态工厂方法：语义化构造"拒绝"结果
    public static SecurityResult deny(String reason) {
        return new SecurityResult(false, false, reason);
    }

    // 静态工厂方法：语义化构造"需要确认"结果
    public static SecurityResult confirm(String reason) {
        return new SecurityResult(false, true, reason);
    }
}
```

**源码示例（ToolDescriptor）：**

```java
// 工具元数据 Record：name / description / parameters / dangerous / version
// 用于生成 LLM 可读的工具描述并驱动安全检查
public record ToolDescriptor(
        String name,                          // 工具名称，如 "bash"
        String description,                   // 工具功能描述
        Map<String, ParameterSchema> parameters, // 参数 Schema（参数名 → 参数定义）
        boolean dangerous,                    // 是否危险工具（bash = true）
        String version                        // 工具版本号
) {
    // 把工具描述格式化为 LLM 可读的文本（替代 JSON Schema）
    // 例如: "bash - Execute shell commands (dangerous)"
    public String toPromptString() {
        return name + " - " + description + (dangerous ? " (dangerous)" : "");
    }
}
```

**为什么用 Record 而不是普通 Class？**

| 方面 | 普通 Class | Record |
|------|-----------|--------|
| 样板代码 | getter/setter/toString/equals 全手写 | 编译期自动生成 |
| 可变性 | 字段可 setter 修改 | 字段 final，天然线程安全 |
| 语义 | 无法表达"纯数据"意图 | `record` 关键字即声明意图 |
| 序列化 | 需手动适配 | Jackson 自动支持（2.17.2） |

SecurityResult 在 5 层安全链中会反复传递，不可变性保证任何一层都不会被后续层篡改，这对安全审计至关重要。

### 2.3 Sealed Classes：约束 AgentEvent 事件类型

Sealed Class (JEP 409) 是 Java 17 正式的特性：用 `sealed ... permits` 声明"这个接口/类的实现者必须是这 8 个"，编译器强制穷尽性检查。

**源码示例（AgentEvent）：**

```java
// sealed interface：只允许 8 个实现类
// permits 关键字显式列出所有允许的继承者，禁止外部扩展
public sealed interface AgentEvent
        permits TurnStarted,      // 一轮对话开始
                StepUpdated,      // ReAct 循环步骤更新
                AssistantDelta,   // LLM 流式增量输出
                AssistantCompleted, // LLM 回复完成
                ToolCallStarted,  // 工具调用开始
                ToolOutputDelta,  // 工具输出流式增量
                ToolCallCompleted, // 工具调用完成
                TurnFailed {      // 一轮对话失败
    // 所有事件实现类必须提供：轮次 ID
    String turnId();
}
```

**配合模式匹配 for switch（Java 21）：**

```java
// Swtich 模式匹配：编译器保证 sealed 的 8 种子类型被穷尽覆盖
// 漏写任何一个实现类，编译直接报错（穷尽性检查 exhaustiveness）
switch (event) {
    case TurnStarted e       -> handleTurnStart(e);        // 处理对话开始
    case ToolCallStarted e   -> handleToolCallStart(e);    // 处理工具调用开始
    case ToolCallCompleted e -> handleToolCallEnd(e);      // 处理工具调用结束
    case TurnFailed e        -> handleFailure(e);          // 处理失败
    case TurnCompleted ignored -> {}                        // 其他事件忽略
    case StepUpdated e       -> handleStep(e);
    case AssistantDelta e    -> streamToConsole(e.delta());
    case AssistantCompleted e -> handleAssistantDone(e);
    case ToolOutputDelta e   -> streamToConsole(e.delta());
}
```

**逐行注释：**

```java
// 行 2-9：case 标签直接绑定类型 + 解构变量 e
// 无需 instanceof + 强转，模式匹配自动完成类型转换
// 编译器静态检查所有分支，漏掉事件类型直接编译失败
```

**Sealed Class 三个价值：**

1. **穷尽性检查**：新增事件类型必须同时更新所有 switch，否则编译错误
2. **领域边界声明**：8 种事件覆盖 Agent 完整生命周期，外部无法随意添加"非法事件"
3. **可维护性**：IDE 自动提示所有未覆盖分支，重构安全

### 2.4 Spring Boot CLI 模式：WebApplicationType.NONE

**核心代码（MewCodeAgentApplication.java）：**

```java
package com.mewcode.app;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.Arrays;

/**
 * 应用入口：基于 Picocli + Spring Boot 的 CLI 应用
 * 双模式启动：默认 CLI 模式，--web 参数切换 Web 模式
 */
@SpringBootApplication                                  // ① 声明 Spring Boot 应用
@Command(name = "mewcode",                             // ② Picocli 命令名
        mixinStandardHelpOptions = true)               //   自动提供 --help / --version
public class MewCodeAgentApplication
        implements CommandLineRunner,                  // ③ 容器就绪后执行 run()
        ExitCodeGenerator {                            // ④ 提供 JVM 退出码

    @Option(names = {"-p", "--prompt"},                // ⑤ 初始提示词参数
            description = "Initial prompt")
    private String prompt;                             //    例如: -p "创建用户模块"

    @Option(names = {"--workdir"},                     // ⑥ 工作目录参数
            description = "Working directory")
    private String workdir;                            //    例如: --workdir ./my-project

    @Option(names = {"--tui"},                         // ⑦ 是否启用 TUI 模式
            description = "Enable TUI mode")
    private boolean tui;

    @Option(names = {"--web"},                         // ⑧ 是否启用 Web 模式
            description = "Enable web mode")
    private boolean web;

    // 主函数：Spring Boot 双模式启动
    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(MewCodeAgentApplication.class);
        // 无 --web 参数 → 强制 NONE 模式（不启动 Tomcat）
        // 有 --web 参数 → 保持默认 SERVLET 模式（启动 Web 容器）
        if (!hasWebArg(args)) {
            app.setWebApplicationType(WebApplicationType.NONE);
        }
        // 启动 Spring 容器并执行 CommandLineRunner
        app.run(args);
    }

    // 扫描参数数组是否包含 --web
    private static boolean hasWebArg(String[] args) {
        // 逐参数判断是否匹配 "--web"
        return Arrays.stream(args)
                .anyMatch(arg -> "--web".equals(arg));
    }

    // CommandLineRunner：容器启动完成后自动回调
    @Override
    public void run(String... args) throws Exception {
        // 此处执行 Agent 主流程：
        // 1. 创建 LlmProvider
        // 2. 构建 ToolRegistry 注册 6 种工具
        // 3. 组装 5 层 SecurityFilterChain
        // 4. 启动 AgentLoop
        // 5. 未指定 prompt 时进入 EnhancedRepl 交互模式
        runAgent(prompt, workdir, tui);
    }

    // ExitCodeGenerator：返回 JVM 退出码
    @Override
    public int getExitCode() {
        // 0 = 正常退出；非 0 = 异常退出
        // SpringApplication.exit() 会读取该值作为 System.exit 参数
        return success ? 0 : 1;
    }

    private void runAgent(String prompt, String workdir, boolean tui) {
        // Agent 启动逻辑（省略细节）
    }
}
```

**逐行注释：**

```java
// ③ implements CommandLineRunner：容器创建完成后自动调用 run()
//    这是 Spring Boot 提供的 CLI 应用"入口点"接口，返回值 void
// ④ implements ExitCodeGenerator：SpringApplication.exit() 时读取退出码
//    两者配合实现"启动即执行 + 退出码可控"的标准 CLI 生命周期
// ⑤ @Option：Picocli 注解声明命令行参数
//    解析结果自动注入到字段（与 Spring 字段注入无冲突）
// ⑧ app.setWebApplicationType(WebApplicationType.NONE)：
//    该设置只影响当前应用实例，容器构建阶段生效
//    与 WebApplicationType.SERVLET 对应（SERVLET 为默认）
```

**CLI 模式关键行为：**

| 配置项 | CLI 模式 (NONE) | Web 模式 (SERVLET) |
|--------|----------------|-------------------|
| 嵌入式 Tomcat | 不启动 | 启动 |
| 启动时间 | 秒级（~1-2s） | 数秒（初始化 Web 容器） |
| 端口监听 | 无 | 8080 |
| 适用场景 | 纯命令行工具 | Web UI / API |

**为什么 Spring Boot 自动识别为非 Web？** Spring Boot 3.x 在 classpath 中没有 `spring-boot-starter-web` 时会自动推断为 NONE，本项目仍显式设置是为了在 `mewcode-app` 同时依赖 web starter 时仍能按参数切换，做到"一个 jar 两种模式"。

### 2.5 依赖配置：kebab-case yml

**pom.xml 关键依赖：**

```xml
<!-- Spring Boot BOM：统一版本管理 -->
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.3.5</version>
</parent>

<!-- Picocli 集成 Spring Boot -->
<dependency>
    <groupId>info.picocli</groupId>
    <artifactId>picocli-spring-boot-starter</artifactId>
    <version>4.7.6</version>
</dependency>

<!-- JLine 终端交互（REPL） -->
<dependency>
    <groupId>org.jline</groupId>
    <artifactId>jline-reader</artifactId>
    <version>3.26.3</version>
</dependency>
```

**application.yml（kebab-case）：**

```yaml
# Spring Boot 3.x 推荐 kebab-case 配置命名
spring:
  application:
    name: mewpaw-code   # 应用名（kebab-case）

mewcode:
  agent:
    max-iterations: 50        # ReAct 循环最大轮次（kebab-case）
    max-consecutive-errors: 3 # 连续错误上限（kebab-case）
  bash:
    timeout-seconds: 60       # Bash 命令超时（kebab-case）
    max-output-chars: 50000   # 输出截断长度（kebab-case）
  llm:
    model: deepseek-chat      # LLM 模型名（kebab-case）
    temperature: 0.7          # 采样温度
```

**注意：** Spring Boot 3 推荐 kebab-case（`max-iterations`），但 `@ConfigurationProperties` 字段名仍用 camelCase（`maxIterations`），Spring 会自动做松散绑定（relaxed binding）。

---

## 三、面试题

**Q1：Java 21 的虚拟线程和平台线程有什么区别？为什么 Bash 工具执行适合用虚拟线程？**

A：虚拟线程由 JVM 调度，创建成本极低（KB 级），阻塞时自动让出载体线程；平台线程由 OS 调度，创建成本高（MB 级栈），阻塞即占用 OS 线程。Bash 命令执行是典型的 IO 等待场景（等待子进程退出），虚拟线程在 `Future.get()` 阻塞时不会浪费 OS 线程，且无需配置线程池大小，适合不可预知数量的命令执行。

**Q2：Record 和普通 Class 的区别？项目里哪些场景用了 Record？**

A：Record 是语义化的不可变数据载体，编译期自动生成构造器、equals、hashCode、toString；普通 Class 需要手写样板代码且默认可变。本项目在 SecurityResult（安全链结果传递）和 ToolDescriptor（工具元数据）使用 Record，因为这两个对象在整个链路中被高频传递，不可变性能保证安全决策不被后续层篡改。

**Q3：Sealed Interface 解决了什么问题？AgentEvent 为什么用 8 种事件类型？**

A：Sealed Interface 限制实现类集合，配合 switch 模式匹配实现编译期穷尽性检查，新增类型漏处理直接编译失败。AgentEvent 的 8 种事件覆盖了 Agent 完整生命周期：TurnStarted（开始）→ StepUpdated（步骤）→ AssistantDelta/Completed（LLM 回复）→ ToolCallStarted/OutputDelta/Completed（工具调用）→ TurnFailed（失败），与前端流式展示一一对应。

**Q4：Spring Boot 做 CLI 应用和做 Web 应用有什么不同？**

A：核心差异在 WebApplicationType：CLI 模式设为 NONE 不启动 Tomcat，启动更快占用更少；Web 模式保持 SERVLET。入口不同：CLI 用 CommandLineRunner 的 run() 作为主流程入口，Web 用 DispatcherServlet 处理 HTTP 请求。本项目通过 `--web` 参数实现双模式切换，一个 jar 两种用途。

**Q5：CommandLineRunner 和 ApplicationRunner 的区别？**

A：两者都在 ApplicationContext 刷新完成后执行。区别仅在参数类型：CommandLineRunner.run(String... args) 接收原始字符串参数；ApplicationRunner.run(ApplicationArguments args) 接收封装对象，可区分 option 和 nonOption 参数（如 `--workdir=x` 与 `my-project`）。

---

## 四、总结

| 设计点 | 实现 | 价值 |
|--------|------|------|
| 虚拟线程 | `newVirtualThreadPerTaskExecutor()` | 高并发 IO 等待零成本 |
| Records | SecurityResult / ToolDescriptor | 不可变数据 + 语义化工厂 |
| Sealed Classes | AgentEvent 8 类型 | 编译期穷尽性检查 |
| CLI 模式 | `WebApplicationType.NONE` | 秒级启动 + 双模式切换 |
| Picocli | `@Command` + `@Option` | 声明式参数解析 |
| kebab-case | application.yml | 符合 Spring Boot 3 规范 |

**核心收获：** Java 21 不是"新语法堆砌"，而是解决实际问题的工具——虚拟线程解决 CLI 并发 IO、Records 保证安全链数据不被篡改、Sealed Classes 让事件系统可编译期校验。Spring Boot 作为 CLI 应用底座，本质是**借用它的 IoC + 生命周期管理能力，而不是它的 Web 能力**。

---

## 参考资料

- JEP 444: Virtual Threads (https://openjdk.org/jeps/444)
- JEP 395: Records (https://openjdk.org/jeps/395)
- JEP 409: Sealed Classes (https://openjdk.org/jeps/409)
- Spring Boot CommandLineRunner (https://docs.spring.io/spring-boot/reference/using/command-line-runner.html)
- Picocli Spring Boot Integration (https://picocli.info/#_spring_boot)