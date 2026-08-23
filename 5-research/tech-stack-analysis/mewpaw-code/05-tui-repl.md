# 05 · CLI/TUI 交互层：JLine REPL + Picocli 参数解析 + Lanterna 终端

> 一个 CLI 编码 Agent 的"门面"就是交互层。mewpaw-code 用三套技术栈覆盖了完整的终端交互场景：**JLine 3** 提供 REPL 行编辑和命令补全，**Picocli** 负责 CLI 启动参数解析，**Lanterna** 支持全屏 TUI 模式。
>
> **对应模块：** `mewcode-interaction` → `com.mewcode.interaction` + `mewcode-app` → `com.mewcode.app`

---

## 一、基础概念

### 1.1 三个库的分工

| 库 | 版本 | 职责 | 类比 |
|------|------|------|------|
| JLine | 3.26.3 | REPL 行编辑、命令补全、历史记录 | 类似 Bash 的 Readline |
| Picocli | 4.7.6 | CLI 启动参数解析（`--workdir` / `--tui` / `--web`） | 类似 Python 的 argparse |
| Lanterna | 3.2.0-alpha1 | 全屏 TUI 终端界面（窗口、按钮、表格） | 类似 ncurses（纯 Java 实现） |

### 1.2 为什么 CLI Agent 需要三套交互库

一个 CLI Agent 在不同阶段需要不同的交互能力：

```
启动阶段: Picocli 解析命令行参数 → 决定运行模式
    │
    ▼
运行阶段: JLine REPL 提供交互式输入 → 命令补全 / 历史记录
    │
    ▼
展示阶段: Lanterna TUI 提供全屏界面 → 多面板展示 Agent 思考过程
    │
    ▼
安全阶段: JLine REPL 显示确认提示 → 用户 y/n 决策
```

- **Picocli** 只在启动时用一次，负责解析 `--workdir ./my-project --tui` 这类参数
- **JLine** 贯穿整个会话，负责所有用户输入的处理
- **Lanterna** 是可选的增强模式，提供更丰富的终端 UI

---

## 二、进阶机制

### 2.1 EnhancedRepl：JLine 3 REPL 完整实现

**源码位置：** `com.mewcode.interaction.EnhancedRepl`

```java
package com.mewcode.interaction;

import org.jline.reader.*;
import org.jline.reader.impl.DefaultParser;
import org.jline.reader.impl.history.DefaultHistory;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Paths;

/**
 * 增强型 REPL：基于 JLine 3 构建的交互式终端
 * 提供命令补全、历史记录、行编辑等 Readline 风格功能
 * 当检测到非真实终端时自动回退到 BufferedReader 标准输入
 */
public class EnhancedRepl {

    private static final Logger log = LoggerFactory.getLogger(EnhancedRepl.class);

    // 历史记录文件路径：用户主目录下的 .mewcode_history
    private static final String HISTORY_FILE = Paths.get(
            System.getProperty("user.home"), ".mewcode_history").toString();
    // 历史记录最大条数
    private static final int MAX_HISTORY_SIZE = 1000;

    // JLine Terminal 实例（系统终端）
    private Terminal terminal;
    // JLine LineReader 实例（行编辑器）
    private LineReader lineReader;
    // 命令补齐器（处理 /slash 命令）
    private Completer completer;

    // 终端状态管理
    private final TuiState tuiState = new TuiState();

    /**
     * 初始化 REPL 环境
     * 如果当前环境有真实终端，使用 JLine 增强交互
     * 否则回退到标准输入
     */
    public void init() {
        try {
            // ① 检测是否为真实终端
            // TuiState.hasConsole() 检查 System.console() 是否可用
            // 如果运行在 IDE 中或通过管道重定向，System.console() 返回 null
            if (!tuiState.hasConsole()) {
                log.info("No real terminal detected, falling back to stdin mode");
                return;
            }

            // ② 构建系统终端
            // TerminalBuilder 自动检测当前终端类型
            // Windows 下使用 JLine 内置的 WindowsTerminal（通过 JNI 调用 Win32 API）
            // Linux/macOS 下使用 UnixTerminal（通过 terminfo 数据库）
            terminal = TerminalBuilder.builder()
                    .system(true)    // 使用系统终端（自动检测类型）
                    .build();

            // ③ 构建 LineReader
            // LineReader 是 JLine 的核心组件，提供行编辑、历史记录、补全等功能
            lineReader = LineReaderBuilder.builder()
                    .terminal(terminal)                              // 绑定终端
                    .completer(completer)                            // 设置命令补全器
                    .parser(new DefaultParser().escapeChars(null))   // 禁用转义字符
                    .history(history)                                // 设置历史记录
                    .variable(LineReader.KEYMAP, LineReader.EMACS)   // Emacs 键位
                    .build();

            log.info("REPL initialized with terminal: {}", terminal.getType());
        } catch (IOException e) {
            log.warn("Failed to initialize JLine terminal, falling back to stdin", e);
        }
    }

    /**
     * 读取用户输入
     * 优先使用 JLine LineReader，失败时回退到 BufferedReader
     * @return 用户输入字符串（去除首尾空白）
     */
    public String readLine() {
        if (lineReader != null) {
            try {
                // ① 使用 JLine 增强行读取
                // 支持：光标移动、历史记录检索（↑↓）、Tab 补全、行内编辑
                return lineReader.readLine("mewcode> ").trim();
            } catch (UserInterruptException e) {
                // Ctrl+C：返回空字符串，由上层处理
                log.debug("User interrupted (Ctrl+C)");
                return "";
            } catch (EndOfFileException e) {
                // Ctrl+D：返回 null，表示 EOF
                log.debug("User pressed Ctrl+D (EOF)");
                return null;
            } catch (Exception e) {
                log.warn("LineReader error, falling back to stdin", e);
            }
        }

        // ② 回退方案：标准 BufferedReader
        // 在 IDE 运行、管道输入、无终端等场景下使用
        try {
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(System.in));
            return reader.readLine();
        } catch (IOException e) {
            log.error("Failed to read from stdin", e);
            return null;
        }
    }
}
```

**逐行注释：**

```java
// 行 1：TerminalBuilder.builder().system(true)
// system(true) 让 JLine 自动检测当前终端类型并创建合适的实现
// Windows 上使用 WindowsTerminal（通过 JNA 调用 kernel32.dll 的 Console API）
// 不传 system(true) 会创建哑终端（dumb terminal），失去行编辑能力

// 行 2：new DefaultParser().escapeChars(null)
// escapeChars(null) 禁用 JLine 的转义字符处理
// 默认情况下 JLine 会处理 \n、\t 等转义，但在 Agent 输入场景中
// 用户可能希望输入原始文本，禁用转义避免干扰

// 行 3：variable(LineReader.KEYMAP, LineReader.EMACS)
// 设置 Emacs 键位映射（Ctrl+A 行首、Ctrl+E 行尾、Ctrl+K 删除到行尾等）
// 可选 VI 模式：LineReader.VI
```

### 2.2 命令补全器（Completer）

```java
/**
 * 自定义命令补全器：处理 /slash 命令的 Tab 补全
 * 实现 JLine 的 Completer 接口
 */
public class SlashCommandCompleter implements Completer {

    // 可用的 /slash 命令列表
    // 这些命令在 Agent 运行时可动态增减
    private static final String[] COMMANDS = {
            "/help",      // 显示帮助信息
            "/exit",      // 退出 Agent
            "/clear",     // 清屏
            "/history",   // 显示历史记录
            "/tools",     // 列出可用工具
            "/status",    // 显示当前状态
            "/debug",     // 切换调试模式
            "/reset"      // 重置 Agent 会话
    };

    @Override
    public void complete(LineReader reader, ParsedLine parsedLine,
                         List<Candidate> candidates) {
        // 获取当前已输入的内容
        String buffer = parsedLine.line();
        // 只补全以 / 开头的命令
        if (buffer.startsWith("/")) {
            for (String cmd : COMMANDS) {
                if (cmd.startsWith(buffer)) {
                    // 添加匹配的候选命令
                    // 参数：命令文本、显示文本、分组、描述、是否完成、是否可排序
                    candidates.add(new Candidate(cmd, cmd, null, null, null, null, true));
                }
            }
        }
    }

    /**
     * 获取命令列表（用于动态更新）
     */
    public String[] getCommands() {
        return COMMANDS;
    }
}
```

**补全流程：**

```
用户输入: /h → Tab
    ↓
SlashCommandCompleter.complete() 被调用
    ↓
匹配：/help（/h 是 /help 的前缀）
    ↓
展示候选: /help
    ↓
用户 Enter → 执行 /help 命令
```

### 2.3 历史记录管理

```java
/**
 * 历史记录管理器
 * 将命令历史持久化到 ~/.mewcode_history 文件
 * 最大 1000 条，支持跨会话检索
 */
public class ReplHistory {

    // JLine 默认历史记录实现
    private final DefaultHistory history = new DefaultHistory();

    /**
     * 初始化历史记录
     * 从文件加载历史，设置最大条数
     */
    public void init() {
        // 设置历史记录文件（持久化路径）
        history.setHistoryFile(new java.io.File(HISTORY_FILE));
        // 设置最大历史记录条数
        // 超过 1000 条时自动丢弃最旧的条目
        history.setMaxSize(MAX_HISTORY_SIZE);
    }

    /**
     * 获取历史记录迭代器
     * 用于遍历所有历史命令
     */
    public History getHistory() {
        return history;
    }
}
```

**历史记录交互：**

```
mewcode> 帮我创建一个 Spring Boot 项目        ← 用户输入第一条命令
mewcode> 添加一个 REST Controller              ← 用户输入第二条命令
mewcode>                                       ← 按 ↑ 键
    ↓
显示上一条命令: "添加一个 REST Controller"
    ↓
再按 ↑
    ↓
显示: "帮我创建一个 Spring Boot 项目"
    ↓
按 Ctrl+R 进入反向搜索:
(reverse-i-search) 'Spring': 帮我创建一个 Spring Boot 项目
```

### 2.4 TuiState：终端状态检测

```java
/**
 * 终端状态管理
 * 检查是否为真实终端，决定使用 JLine 增强模式还是 BufferedReader 回退模式
 */
public class TuiState {

    /**
     * 检查当前环境是否有真实终端
     * 返回 true 的条件：
     *   1. System.console() != null（有真实控制台）
     *   2. 非 IDE 运行环境（IDE 通常不提供原生控制台）
     *   3. 非管道/重定向输入（stdin 没有被重定向）
     * @return 是否有真实终端
     */
    public boolean hasConsole() {
        // System.console() 在以下情况返回 null：
        // - 在 IDE 中运行（如 IntelliJ IDEA、Eclipse）
        // - 标准输入被重定向（如 echo "hello" | java -jar app.jar）
        // - 在后台运行（nohup）
        return System.console() != null;
    }

    /**
     * 检查是否处于管道输入模式
     * 如果 stdin 来自管道，不能使用交互式 REPL
     * @return 是否来自管道
     */
    public boolean isPiped() {
        try {
            // System.in.available() 在管道模式下行为不同
            return System.in.available() == 0;
        } catch (IOException e) {
            return true;
        }
    }
}
```

### 2.5 Picocli 参数解析

**源码位置：** `com.mewcode.app.MewCodeAgentApplication`

```java
package com.mewcode.app;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.util.Arrays;

/**
 * 应用入口：Picocli + Spring Boot 双模式 CLI 应用
 *
 * 启动示例：
 *   java -jar mewpaw-code.jar --prompt "创建用户模块" --workdir ./my-project
 *   java -jar mewpaw-code.jar --tui
 *   java -jar mewpaw-code.jar --web                                    # Web 模式
 *   java -jar mewpaw-code.jar --prompt "帮我写个 Spring Boot 应用" --web  # Web + 初始 prompt
 */
@SpringBootApplication
@Command(name = "mewcode",                                             // 命令名（用于 --help 显示）
        mixinStandardHelpOptions = true,                               // 自动提供 --help / --version
        description = "MewCode Agent - AI-powered coding assistant")   // 命令描述
public class MewCodeAgentApplication
        implements CommandLineRunner,                                  // Spring Boot CLI 入口
        ExitCodeGenerator {                                            // JVM 退出码

    @Option(names = {"-p", "--prompt"},                                // 短名称 + 长名称
            description = "Initial prompt for the agent",              // 帮助描述
            paramLabel = "<text>")                                     // 参数占位符
    private String prompt;                                             // 初始提示词

    @Option(names = {"--workdir"},
            description = "Working directory (default: current dir)",
            paramLabel = "<path>")
    private String workdir;                                            // 工作目录

    @Option(names = {"--tui"},
            description = "Enable TUI mode (Lanterna full-screen)")
    private boolean tui = false;                                       // 是否启用 TUI（默认关闭）

    @Option(names = {"--web"},
            description = "Enable web mode (start embedded Tomcat)")
    private boolean web = false;                                       // 是否启用 Web（默认关闭）

    @Option(names = {"-m", "--model"},
            description = "LLM model name (default: deepseek-chat)",
            paramLabel = "<model>")
    private String model;                                              // LLM 模型名

    @Option(names = {"-t", "--temperature"},
            description = "LLM temperature (default: 0.7)",
            paramLabel = "<double>")
    private Double temperature = 0.7;                                  // LLM 温度参数

    @Parameters(paramLabel = "<prompt>",                               // 位置参数（无需 -- 前缀）
            description = "Prompt as positional argument",
            arity = "0..1")                                            // 可选，最多一个
    private String positionalPrompt;                                   // 位置参数形式的 prompt

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(MewCodeAgentApplication.class);

        // 双模式决策：无 --web 参数时关闭 Web 容器
        if (!hasWebArg(args)) {
            app.setWebApplicationType(WebApplicationType.NONE);
        }

        app.run(args);
    }

    private static boolean hasWebArg(String[] args) {
        return Arrays.stream(args)
                .anyMatch(arg -> "--web".equals(arg));
    }

    @Override
    public void run(String... args) throws Exception {
        // 优先使用 --prompt 参数，其次使用位置参数
        String finalPrompt = prompt != null ? prompt : positionalPrompt;
        // 默认工作目录为当前目录
        String finalWorkdir = workdir != null ? workdir : System.getProperty("user.dir");

        // 启动 Agent 主流程
        runAgent(finalPrompt, finalWorkdir, tui);
    }

    private void runAgent(String prompt, String workdir, boolean tui) {
        // 1. 初始化 LlmProvider（LLM 调用封装）
        // 2. 构建 ToolRegistry（注册 6 种内置工具）
        // 3. 组装 SecurityFilterChain（5 层安全过滤）
        // 4. 启动 AgentLoop（ReAct 循环）
        // 5. 如果指定了 prompt，直接执行；否则进入 EnhancedRepl 交互模式
        // ...
    }

    @Override
    public int getExitCode() {
        return 0; // 正常退出
    }
}
```

**逐行注释：**

```java
// 行 1：@Command(name = "mewcode", mixinStandardHelpOptions = true)
// Picocli 会扫描这个类的 @Option 注解，自动生成 --help 和 --version 选项
// 运行 java -jar mewpaw-code.jar --help 会输出：
//   Usage: mewcode [-hV] [--tui] [--web] [-m=<model>] [-p=<text>] [-t=<double>]
//                  [--workdir=<path>] [<prompt>]
//   MewCode Agent - AI-powered coding assistant
//     -h, --help          Show this help message and exit.
//     -p, --prompt=<text> Initial prompt for the agent
//         --workdir=<path> Working directory (default: current dir)
//         --tui            Enable TUI mode (Lanterna full-screen)
//         --web            Enable web mode (start embedded Tomcat)
//   -m, --model=<model>   LLM model name (default: deepseek-chat)
//   -t, --temperature=<double> LLM temperature (default: 0.7)

// 行 2：@Option(names = {"-p", "--prompt"})
// 支持短名称（-p）和长名称（--prompt）两种形式
// 短名称适合快速输入，长名称适合脚本和文档

// 行 3：@Parameters 位置参数
// 与 @Option 不同，位置参数不需要 -- 前缀
// 用法：java -jar mewpaw-code.jar "帮我创建项目"
// 相当于：java -jar mewpaw-code.jar --prompt "帮我创建项目"
```

### 2.6 Lanterna TUI 终端

**Lanterna** 是一个纯 Java 的全屏终端 UI 库，类似 ncurses 但无需 JNI 绑定。

```java
// Lanterna 使用示例（项目中的 TUI 模式）
// 注意：以下代码展示 Lanterna 的核心概念，非项目实际源码

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.DefaultTerminalFactory;
import com.googlecode.lanterna.terminal.Terminal;

// ① 创建终端和屏幕
Terminal terminal = new DefaultTerminalFactory().createTerminal();
Screen screen = new TerminalScreen(terminal);
screen.startScreen();               // 进入全屏模式

// ② 绘制界面
TextGraphics graphics = screen.newTextGraphics();
graphics.setForegroundColor(TextColor.ANSI.GREEN);
graphics.putString(1, 1, "MewCode Agent - TUI Mode");
graphics.putString(1, 3, "Thought: 需要创建一个Spring Boot项目");
graphics.putString(1, 4, "Action: bash(\"mvn archetype:generate...\")");
graphics.putString(1, 5, "Observation: 项目创建成功");

// ③ 刷新屏幕
screen.refresh();

// ④ 退出时恢复终端
screen.stopScreen();
```

**Lanterna vs JLine 对比：**

| 维度 | JLine 3 | Lanterna |
|------|---------|----------|
| 模式 | 行模式（单行输入） | 全屏模式（占据整个终端） |
| 核心能力 | 行编辑、补全、历史 | 窗口、按钮、表格、颜色 |
| 适用场景 | REPL 交互式输入 | 仪表盘、监控面板、多面板展示 |
| 依赖体积 | 小（~500KB） | 中（~1.5MB） |
| 跨平台 | 内置 Windows 支持 | 纯 Java，通过 ANSI 或 JNA |

**为什么项目同时使用 JLine 和 Lanterna？**

- **JLine 处理"输入"**：REPL 交互、命令补全、历史记录、Tab 补全——这些是"用户向 Agent 输入"的场景
- **Lanterna 处理"展示"**：全屏面板、多窗口布局、实时更新——这些是"Agent 向用户展示"的场景
- **两者互补**：JLine 管"行"，Lanterna 管"屏"

### 2.7 CLI 模式 vs Web 模式对比

```
┌─────────────────────────────────────────────────────┐
│                    MewCode Agent                      │
├─────────────────────────────────────────────────────┤
│                     启动入口                          │
│         MewCodeAgentApplication.main()               │
├─────────────────────┬───────────────────────────────┤
│     CLI 模式         │         Web 模式               │
│  (默认，无 --web)    │     (传 --web 参数)             │
├─────────────────────┼───────────────────────────────┤
│ WebApplicationType  │ WebApplicationType             │
│ .NONE               │ .SERVLET (默认)                │
├─────────────────────┼───────────────────────────────┤
│ 无嵌入式 Tomcat     │ 启动嵌入式 Tomcat              │
│ 秒级启动 (~1-2s)    │ 数秒启动 (~3-5s)              │
├─────────────────────┼───────────────────────────────┤
│ 交互方式:           │ 交互方式:                      │
│ JLine REPL          │ Web UI (HTTP 接口)            │
│ 或 Lanterna TUI     │ (mewcode-webui 模块)           │
├─────────────────────┴───────────────────────────────┤
│                核心引擎共享                          │
│        AgentLoop + ToolRegistry + SecurityChain      │
└─────────────────────────────────────────────────────┘
```

---

## 三、面试题

**Q1：为什么 CLI Agent 需要三套交互库（JLine + Picocli + Lanterna）？不能只用一套吗？**

A：三个库解决不同阶段的问题：**Picocli** 负责"启动时"的参数解析（`--workdir`、`--tui`），只在进程启动时执行一次；**JLine** 负责"运行时"的行编辑交互（命令补全、历史记录、行内编辑），贯穿整个会话；**Lanterna** 负责"展示时"的全屏 TUI 界面（多面板展示 Agent 思考过程），是可选的增强模式。只用一套无法覆盖全部场景——比如说 Picocli 不能做行编辑，JLine 不做参数解析，Lanterna 不适合 REPL 输入。

**Q2：JLine 的 TerminalBuilder 和 LineReader 分别是什么？**

A：TerminalBuilder 是终端抽象层的工厂，自动检测当前终端类型（Windows Terminal、xterm、iTerm2 等），创建对应的 Terminal 实现。LineReader 是行编辑器，包装 Terminal 提供 Readline 风格的行编辑能力——光标移动、历史记录检索（↑↓）、Tab 补全、行内编辑（Ctrl+A/E 等）。Terminal 是"终端硬件抽象"，LineReader 是"行编辑软件层"。

**Q3：项目为什么用 `escapeChars(null)` 禁用 JLine 的转义字符处理？**

A：JLine 默认会对 `\n`、`\t` 等转义序列做特殊处理，但在 Agent 使用场景中，用户输入的是原始文本（可能包含代码片段、路径等），禁用转义可以避免 JLine 对用户输入做不必要的变换，确保 Agent 收到的就是用户键入的原始内容。

**Q4：位置参数（@Parameters）和命名参数（@Option）有什么区别？**

A：命名参数需要 `--` 或 `-` 前缀，如 `--prompt "hello"` 或 `-p "hello"`，顺序可以任意；位置参数不需要前缀，按声明顺序解析，如 `mewcode "hello"`。项目同时支持两种方式：`-p "hello"` 和直接 `"hello"` 均可，提升了 CLI 使用的灵活性。

**Q5：TuiState.hasConsole() 检查 System.console() 有什么意义？**

A：System.console() 在真实终端环境中返回非 null 的 Console 对象，在 IDE 运行、管道输入（`echo "hello" | java -jar`）、后台运行（`nohup`）等场景中返回 null。检查这个值可以让程序自动降级——有真实终端时使用 JLine 增强交互，无终端时回退到 BufferedReader 标准输入，避免在无终端环境中使用 JLine 导致异常。

**Q6：Lanterna 和 JLine 在终端交互上有什么本质区别？**

A：JLine 是"行模式"——它占据终端的一行，处理输入输出；Lanterna 是"全屏模式"——它占据整个终端窗口，可以绘制窗口、面板、表格等复杂 UI。JLine 适合"用户输入→Agent 回复"的对话式交互，Lanterna 适合"展示 Agent 思考过程"的仪表盘式交互。

---

## 四、总结

| 设计点 | 技术 | 价值 |
|--------|------|------|
| 启动参数解析 | Picocli @Command / @Option | 声明式参数定义，自动生成 --help |
| 双模式切换 | `--web` 参数 + WebApplicationType | 一个 jar 两种用途 |
| REPL 行编辑 | JLine LineReader | 命令补全 / 历史记录 / 行编辑 |
| 命令补全 | Completer 接口 + /slash 命令 | 快速发现可用命令 |
| 历史记录 | DefaultHistory + 文件持久化 | 跨会话命令检索 |
| 终端检测 | TuiState.hasConsole() | 自动降级，避免无终端异常 |
| 全屏 TUI | Lanterna Screen | 多面板展示 Agent 状态 |
| 自动回退 | BufferedReader | 管道 / IDE 场景兼容 |

**核心收获：** CLI Agent 的交互层设计遵循"**合适的工具做合适的事**"原则——Picocli 管启动、JLine 管输入、Lanterna 管展示，三层各司其职。同时通过终端检测和自动回退机制，确保在无终端环境（IDE、管道、后台）中也能正常工作。

---

## 参考资料

- JLine 3 文档：https://github.com/jline/jline3
- Picocli 文档：https://picocli.info/ (v4.7.6)
- Picocli Spring Boot Starter：https://picocli.info/#_spring_boot
- Lanterna 文档：https://github.com/mabe02/lanterna
- Spring Boot WebApplicationType：https://docs.spring.io/spring-boot/reference/using/command-line-runner.html