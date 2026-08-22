# 01 · TUI/REPL 入门：搭建第一个命令行交互界面

> **深度系列 | mewpaw-code | Level 1 入门篇**
>
> 本篇目标：从零搭建一个"跑得起来"的命令行交互界面（REPL），让你在终端里像用 Python 的 `python` 命令一样，与 Java 程序进行实时对话。你会亲手写出 pom.xml、application.yml、Java 源码和单元测试，掌握 JLine 3 REPL 的完整搭建流程，最后对照真实的 mewpaw-code 项目，理解从入门版到生产版的差距。
>
> **前置要求：** 已安装 JDK 21 和 Maven 3.8+，会基本的中文 Java 语法，了解 Spring Boot 基础概念。

---

## 一、项目背景：为什么 CLI Agent 需要一个 REPL 界面？

### 1.1 什么是 REPL

REPL 是 **Read-Eval-Print-Loop**（读取-求值-打印-循环）的缩写。它是最古老、最直观的人机交互范式之一：程序启动后进入一个循环，每轮读取用户的一行输入，解析执行，打印结果，然后等待下一轮输入。

你每天都在用 REPL 而没意识到：
- 在终端里敲 `python` 后看到的 `>>>` 提示符——这是 Python REPL
- 在终端里敲 `node` 后看到的 `>` 提示符——这是 Node.js REPL
- 在终端里敲 `redis-cli` 后看到的 `127.0.0.1:6379>`——这是 Redis REPL

**REPL 的核心特征**是"有状态的持续对话"：程序不会因为一次输入执行完就退出，而是保持运行状态，让你可以连续输入、反复调试。这和传统的"一次性"命令行工具（如 `ls`、`grep`、`java -jar myapp.jar`）有本质区别。

### 1.2 为什么 CLI Agent 需要 REPL

mewpaw-code 是一个 **CLI 编码 Agent**——用户在终端里输入一句中文，比如"帮我在当前目录创建一个 Spring Boot 项目"，AI Agent 就会自主决策、调用工具、一步步完成任务。

如果只是一个"一次性"的 CLI 工具，用户每次都要输入完整的命令参数：

```bash
java -jar mewpaw-code.jar --prompt "帮我创建一个 Spring Boot 项目"
```

然后等结果，再输下一条。这体验非常糟糕，原因有三：

1. **启动开销大**：每次都要重新加载 Spring 容器、初始化 LLM 连接、加载工具注册表——至少 2-3 秒的启动时间
2. **无状态**：上一轮对话的上下文完全丢失，Agent 无法记住用户之前的意图
3. **无法连续交互**：Agent 可能需要追问用户"项目放在哪个目录"，一次性的 CLI 模型无法支持这种对话

**REPL 完美解决了这三个问题**：程序启动一次，进入无限循环的 Read-Eval-Print-Loop，用户连续输入，Agent 连续响应，上下文保持完整。这就是 mewpaw-code 选择 REPL 作为主要交互界面的根本原因。

### 1.3 mewpaw-code 的三库协同方案

mewpaw-code 的交互层由三个专业库协同完成，各司其职：

| 库 | 版本 | 职责 | 类比 |
|----|------|------|------|
| **JLine 3** | 3.26.3 | REPL 循环核心：行编辑、Tab 补全、历史记录、按键绑定 | 交互引擎 |
| **Picocli** | 4.7.6 | 命令行参数解析：`@Option`、`@Command`、自动生成 `--help` | 参数解析器 |
| **Lanterna** | 3.1.1 | 全屏 TUI 模式：终端全屏绘制、多区域布局、鼠标支持 | 界面渲染器 |

**三者的分工清晰：**

- **Picocli** 负责"进场"时的参数解析：用户敲 `mewpaw --model gpt-4 --workdir /projects --tui`，Picocli 把这些参数绑定到 Java 字段，决定启动模式（CLI 还是 Web），然后功成身退。
- **JLine 3** 负责"常规"交互模式：用户进入 REPL 后，JLine 接管终端的行编辑能力——支持方向键移动光标、Ctrl+A 跳转到行首、Ctrl+E 跳转到行尾、Tab 自动补全 `/help` 等斜杠命令、上下键翻历史记录。
- **Lanterna** 负责"高阶"全屏模式：当用户传入 `--tui` 参数时，Lanterna 接管整个终端屏幕，绘制出类似 Vim 的分栏界面——左侧是对话列表，右侧是内容区，底部是输入框，支持鼠标交互。

### 1.4 从简单 stdin/stdout 到交互式 REPL 的演化

理解 REPL 的最好方式，是看它"从简单到复杂"的演化路径：

**阶段 1：原始 stdin/stdout 循环**

```java
Scanner scanner = new Scanner(System.in);
while (true) {
    System.out.print("> ");
    String line = scanner.nextLine();
    if ("/exit".equals(line)) break;
    System.out.println("你说的是：" + line);
}
```

这是最简单的 REPL——一个 `while(true)` 循环，用 `Scanner` 读取标准输入，用 `System.out.print` 打印提示符。功能没问题，但用户体验极差：不能编辑输入（退格键可能不工作）、没有历史记录、没有 Tab 补全、Ctrl+C 直接退出程序。

**阶段 2：加入 JLine 的 REPL**

```java
Terminal terminal = TerminalBuilder.builder().system(true).build();
LineReader reader = LineReaderBuilder.builder()
    .terminal(terminal)
    .completer(new MyCompleter())
    .history(history)
    .build();
while (true) {
    String line = reader.readLine("> ");
    if ("/exit".equals(line)) break;
    System.out.println("你说的是：" + line);
}
```

JLine 接管了终端控制，提供了：行内编辑、Emacs 键绑定、Tab 补全、历史记录翻查、Ctrl+C 捕获。这就是 mewpaw-code 的 `EnhancedRepl` 类做的事。

**阶段 3：加入 Lanterna 的全屏 TUI**

Lanterna 把终端变成一个"可编程的图形界面"：你可以定义窗口、面板、按钮、文本框，支持鼠标事件，甚至可以做分栏布局。mewpaw-code 的 `TuiApp` 类利用 Lanterna 实现了类似 VS Code 的终端分栏界面。

本篇专注于 **阶段 2**——用 JLine 3 搭建一个功能完整的 REPL 界面，这是理解 mewpaw-code 交互层的最短路径。

---

## 二、核心概念：JLine 3 的六块积木

在写代码之前，先花 10 分钟理解 JLine 3 的核心概念。这些概念是搭建 REPL 的"六块积木"，带着它们去读第三节的代码，你会豁然开朗。

### 2.1 Terminal——终端的"抽象层"

JLine 的 `Terminal` 接口封装了物理终端的全部能力。它不是直接操作 `System.in` / `System.out`，而是通过 JNI（Java Native Interface）调用终端底层的 `termios`（类 Unix）或 Windows Console API 来获取原生能力：

- **原始模式（Raw Mode）**：关闭终端的行缓冲和回显，让程序可以逐字符读取输入
- **尺寸查询**：`terminal.getWidth()` / `terminal.getHeight()` 获取终端的行列数
- **光标控制**：`terminal.puts(Capability.cursor_up)` 移动光标
- **颜色支持**：`terminal.puts(Capability.enter_bold_mode)` 启用粗体

`TerminalBuilder.builder().system(true).build()` 是创建 Terminal 的标准方式——它会自动检测当前系统的终端类型，选择最合适的实现。

### 2.2 LineReader——REPL 的"心脏"

`LineReader` 是 JLine 最核心的接口：它负责读取一行用户输入，并在读取过程中提供完整的行编辑体验。

关键行为：
- 输出指定的提示符（如 `> `、`mewpaw> `）
- 等待用户输入，直到按下回车键
- 在输入过程中，处理方向键、退格键、Ctrl 组合键等编辑操作
- 如果有 completer，在输入过程中自动触发 Tab 补全
- 如果有 history，上下键翻查历史记录

`LineReaderBuilder` 提供了构造 LineReader 的构建器模式，可以配置 completer、parser、history、keymap 等。

### 2.3 Completer——Tab 补全的"大脑"

`Completer` 接口定义了 Tab 补全的逻辑。当用户按下 Tab 键时，JLine 会调用已注册的 Completer 来生成补全候选列表。

核心接口只有一个方法：

```java
void complete(LineReader reader, ParsedLine line, List<Candidate> candidates);
```

- `ParsedLine` 包含当前光标位置、输入的单词、整个行
- `Candidate` 是候选列表，JLine 会根据候选项自动决定是直接补全还是显示候选列表

JLine 内置了多种 Completer 实现：
- `StringsCompleter`：从固定字符串列表中补全
- `FileNameCompleter`：补全文件路径
- `EnumCompleter`：补全枚举值
- `AggregateCompleter`：组合多个 Completer

### 2.4 History——命令历史的"档案馆"

`History` 接口管理用户的命令历史。JLine 的 `DefaultHistory` 实现会将历史记录存储在内存中，并支持持久化到文件。

关键能力：
- 上下键翻查历史
- `Ctrl+R` 反向搜索历史
- `history()` 方法获取全部历史记录
- `addHistory(line)` 手动添加记录
- `save()` / `load()` 持久化到文件

### 2.5 Parser——输入解析的"分诊台"

`Parser` 负责将原始的输入字符串解析为结构化的 `ParsedLine` 对象。`ParsedLine` 包含：
- `word()`：当前正在输入的单词
- `words()`：整行拆分成单词
- `wordCursor()`：光标在单词内的位置

JLine 默认的 `DefaultParser` 按空格分词，但 JLine 在调用 completer 时会自动对输入进行转义处理。在 REPL 场景中，为了确保斜杠命令（如 `/help`）不被 JLine 的转义机制干扰，需要设置 `escapeChars(null)`。

### 2.6 按键绑定与 Keymap

JLine 支持两套经典的按键绑定方案：

- **Emacs 模式**（默认）：Ctrl+A 行首、Ctrl+E 行尾、Ctrl+K 删到行尾、Ctrl+U 删整行、Ctrl+P/N 上/下历史
- **Vi 模式**：支持 Vi 的插入模式和命令模式切换

Emacs 模式是大多数开发者的首选，也是 JLine 的默认配置。在 `LineReaderBuilder` 中通过 `.keymap(KeyMap.EMACS)` 或 `.keymap(KeyMap.VI)` 设置。

---

## 三、从零搭建：Hello TUI REPL 完整代码

### 3.1 项目结构总览

我们创建一个名为 `hello-tui-repl` 的 Maven 单模块工程，核心文件如下：

```
hello-tui-repl/
├── pom.xml                                  # Maven 配置：依赖 + 构建
└── src/
    ├── main/
    │   ├── resources/
    │   │   └── application.yml              # Spring 配置
    │   └── java/com/example/repl/
    │       ├── HelloCliAgentApplication.java  # 主类 + Picocli 参数 + 入口
    │       ├── repl/
    │       │   ├── TuiState.java              # 终端状态检测
    │       │   ├── SlashCommandCompleter.java  # 斜杠命令 Tab 补全
    │       │   ├── ReplHistory.java           # 命令历史持久化
    │       │   └── EnhancedRepl.java          # 主 REPL 循环（核心）
    └── test/java/com/example/repl/
        └── HelloCliAgentApplicationTest.java  # 单元测试
```

### 3.2 第一步：pom.xml（Maven 骨架）

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                             https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <!-- 继承 Spring Boot 父 POM：统一管理依赖版本号 -->
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.3.5</version>
        <relativePath/>
    </parent>

    <!-- 项目坐标 -->
    <groupId>com.example</groupId>
    <artifactId>hello-tui-repl</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <name>hello-tui-repl</name>
    <description>JLine 3 REPL 入门示例 —— Hello TUI REPL</description>

    <properties>
        <!-- Java 版本锁定到 21 -->
        <java.version>21</java.version>
        <!-- JLine 版本 -->
        <jline.version>3.26.3</jline.version>
        <!-- Picocli 版本 -->
        <picocli.version>4.7.6</picocli.version>
    </properties>

    <dependencies>
        <!-- Spring Boot 核心 starter（非 Web 模式，不启动 Tomcat） -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter</artifactId>
        </dependency>

        <!-- JLine 3：REPL 行编辑、Tab 补全、历史记录 -->
        <dependency>
            <groupId>org.jline</groupId>
            <artifactId>jline</artifactId>
            <version>${jline.version}</version>
        </dependency>

        <!-- Picocli：命令行参数解析 -->
        <dependency>
            <groupId>info.picocli</groupId>
            <artifactId>picocli</artifactId>
            <version>${picocli.version}</version>
        </dependency>

        <!-- Picocli Spring Boot Starter：Picocli 与 Spring 生态集成，自动装配 -->
        <dependency>
            <groupId>info.picocli</groupId>
            <artifactId>picocli-spring-boot-starter</artifactId>
            <version>${picocli.version}</version>
        </dependency>

        <!-- 测试 starter：JUnit 5 + AssertJ + Mockito -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <!-- Spring Boot 打包插件：打成可执行胖 jar -->
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

**逐行要点：**
- `jline` 依赖引入的是 JLine 3 的完整发行包（类名空间 `org.jline.*`），包含 `terminal`、`reader`、`history`、`completer` 等全部子模块。注意 JLine 2 的包名是 `jline.console.*`，两者互不兼容，本篇使用 JLine 3。
- `picocli-spring-boot-starter` 是 Picocli 官方提供的 Spring Boot 集成库，它能自动将 `@Command` 注解的类注册为 Spring Bean，并处理 Picocli 与 Spring 的生命周期对齐。入门版从纯 Picocli 开始（前一篇的做法），本篇使用 Starter 更贴近 mewpaw-code 的生产实践。
- 注意没有 `spring-boot-starter-web`：我们强制 `WebApplicationType.NONE`，Spring 只做 IoC 容器，不启动 Tomcat。

### 3.3 第二步：application.yml（配置文件）

```yaml
# Spring Boot 应用基础配置
spring:
  application:
    name: hello-tui-repl

# REPL 交互层配置
repl:
  prompt: "repl> "           # 默认提示符
  history-file: "~/.mewcode_history"  # 历史记录文件路径
  max-history-size: 1000      # 历史记录最大条数
```

**逐行要点：**
- `repl.prompt` 定义提示符字符串，可以在启动时通过 `--prompt` 参数覆盖。
- `repl.history-file` 使用 `~` 表示用户主目录，JLine 的文件操作会自动展开 `~`。mewpaw-code 的真实历史文件路径也是 `~/.mewcode_history`。
- `repl.max-history-size` 限制历史记录数量，防止历史文件无限膨胀。

### 3.4 第三步：TuiState.java（终端状态检测）

```java
package com.example.repl.repl;

import org.springframework.stereotype.Component;

/**
 * TuiState —— 终端状态检测工具类
 *
 * 功能：判断当前程序运行在什么"终端环境"中：
 *   1. 是否有真正的物理终端（System.console() != null）
 *   2. 标准输入是否被管道重定向（如 echo "hello" | java -jar app.jar）
 *   3. 标准输出是否被管道重定向
 *
 * 这些检测在 REPL 启动时至关重要：
 * - 没有物理终端时，JLine 的 TerminalBuilder 无法创建原始模式终端
 * - 输入被管道重定向时，应退化为非交互式的一次性处理模式
 * - 输出被管道重定向时，不应输出 ANSI 控制字符（颜色、光标移动等）
 *
 * 对应 mewpaw-code 中 TuiState 的核心逻辑，用于决定"启动 JLine REPL 还是直接读 stdin"。
 */
@Component
public class TuiState {

    // 缓存 System.console() 的引用（它在程序运行期间不会变化）
    private final Console console = System.console();

    /**
     * 是否有真正的物理终端？
     *
     * System.console() 在以下情况返回 null：
     * - 标准输入/输出被重定向（管道或文件）
     * - 在 IDE 中运行（如 IntelliJ IDEA 的 Run 窗口）
     * - 在没有终端的环境中运行（如 Docker 后台进程）
     *
     * 注意：System.console() 在 IDE 中大概率返回 null，
     * 所以在 IDE 中调试 REPL 时，需要手动切换到"终端模式"运行。
     */
    public boolean hasConsole() {
        // console != null 意味着有真实的物理终端交互
        return console != null;
    }

    /**
     * 标准输入是否被管道重定向？
     *
     * System.in.available() > 0 表示管道里有数据等待读取，
     * 这意味着用户不是在用键盘交互，而是通过管道把内容传了进来。
     * 例如：echo "hello" | java -jar app.jar
     *
     * 这种情况下不能启动 REPL（JLine 会报错），
     * 而应该直接读取管道内容，处理完毕后退出。
     */
    public boolean isPiped() {
        try {
            // available() 返回可读字节数，大于 0 说明有管道数据
            return System.in.available() > 0;
        } catch (Exception e) {
            // 异常时保守地返回 true，确保不会在非交互模式下启动 JLine
            return true;
        }
    }

    /**
     * 标准输出是否被管道重定向？
     *
     * 如果 System.out 被重定向到文件或另一个管道，
     * 那么输出 ANSI 控制字符（颜色、光标移动）会导致目标文件被污染。
     * 此时应输出纯文本。
     */
    public boolean isOutputPiped() {
        // System.console() 为 null 且 System.in 没有管道数据时，
        // 大概率是输出被重定向了（粗糙的判断，但够用）
        return !hasConsole() && !isPiped();
    }

    /**
     * 获取终端宽度（如果无法获取，默认 80 列）
     */
    public int getTerminalWidth() {
        // 尝试从 System.console() 获取终端宽度
        if (console != null) {
            // 某些平台的 console.writer() 可能返回 null
            return 80; // 简化版本：默认 80 列
        }
        return 80;
    }

    /**
     * 获取终端高度（如果无法获取，默认 24 行）
     */
    public int getTerminalHeight() {
        if (console != null) {
            return 24; // 简化版本：默认 24 行
        }
        return 24;
    }
}
```

**逐行要点：**
- `System.console()` 是 REPL 开发的"第一道守卫"：它返回的 `Console` 对象提供与物理终端交互的能力，包含 `readPassword()`、`printf()`、`reader()` 等方法。如果 `console()` 返回 null，说明没有物理终端，JLine 无法进入原始模式。
- `isPiped()` 用 `System.in.available()` 检测管道输入：当来自管道时，`available()` 可能返回管道中可读的字节数。这个判断不是 100% 精确（某些终端实现可能在有用户输入时也返回非零值），但在实际使用中足够可靠。
- mewpaw-code 的 `TuiState` 在 `TuiState` 类的基础上，还加入了更多检测：`isAttached()` 检查是否在 tmux/screen 会话中、`isSSH()` 检查是否通过 SSH 连接等，用于决定是否需要启用颜色输出。

### 3.5 第四步：SlashCommandCompleter.java（Tab 补全）

```java
package com.example.repl.repl;

import org.jline.reader.Candidate;
import org.jline.reader.Completer;
import org.jline.reader.LineReader;
import org.jline.reader.ParsedLine;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * SlashCommandCompleter —— 斜杠命令的 Tab 补全器
 *
 * 实现 JLine 的 Completer 接口，为以 "/" 开头的命令提供 Tab 补全。
 * 当用户输入 "/" 后按下 Tab 键时，JLine 会调用本类的 complete() 方法，
 * 传入当前已输入的内容，我们返回匹配的候选命令列表。
 *
 * 支持的命令列表（对应 mewpaw-code 的 /slash 命令的简化版）：
 *   /help    - 显示帮助信息
 *   /exit    - 退出程序
 *   /clear   - 清屏
 *   /history - 显示命令历史
 *   /tools   - 列出可用工具
 *   /status  - 显示当前状态
 *   /debug   - 切换调试模式
 *   /reset   - 重置对话上下文
 */
@Component
public class SlashCommandCompleter implements Completer {

    // 所有可用的斜杠命令列表（按字母排序，美观）
    private static final List<String> COMMANDS = List.of(
            "/clear",   // 清屏：清除终端屏幕内容
            "/debug",   // 调试：切换调试模式开关
            "/exit",    // 退出：退出 REPL 程序
            "/help",    // 帮助：显示所有可用命令的说明
            "/history", // 历史：显示此前输入过的命令列表
            "/reset",   // 重置：清空对话上下文，开始新会话
            "/status",  // 状态：显示当前运行状态信息
            "/tools"    // 工具：列出所有已注册的工具
    );

    /**
     * JLine 在用户按下 Tab 键时自动调用的补全方法
     *
     * @param reader     当前 LineReader 实例（可用于获取终端尺寸等）
     * @param line       当前已解析的输入行（包含光标位置、单词、整行内容）
     * @param candidates 候选列表：把补全建议添加到此列表中
     */
    @Override
    public void complete(LineReader reader, ParsedLine line, List<Candidate> candidates) {
        // 获取当前正在输入的单词（从光标位置向左截取到最近的空格或行首）
        String word = line.word();

        // 只补全以 "/" 开头的输入（非斜杠开头的输入不做补全）
        if (!word.startsWith("/")) {
            return;
        }

        // 遍历所有预定义的斜杠命令
        for (String cmd : COMMANDS) {
            // 如果当前输入是命令的前缀，就将其加入候选列表
            // 例如：输入 "/h" 会匹配 "/help" 和 "/history"
            if (cmd.startsWith(word)) {
                // Candidate 构造参数依次为：
                //   value    - 补全后的完整文本
                //   display  - 显示在候选列表中的文本（可为 null，使用 value）
                //   group    - 分组名（可为 null）
                //   desc     - 描述文字（可为 null）
                //   key      - 补全后的光标相对偏移（0 表示光标在末尾）
                //   complete - 是否自动补全（true 表示直接补全）
                candidates.add(new Candidate(cmd, cmd, null, null, null, null, true));
            }
        }
    }
}
```

**逐行要点：**
- `Completer` 接口只有一个方法 `complete()`，JLine 在用户按下 Tab 键时以同步方式调用。注意这个方法在 LineReader 的"事件循环"中执行，不应有阻塞操作（如网络 IO）。
- `ParsedLine.word()` 返回当前光标所在的单词，不包括前面的空格。例如输入 `/h` 时 word 为 `/h`，输入 `/help  ` 时 word 会根据光标位置变化。
- `Candidate` 的构造器有 7 个参数，最后一个是 `boolean complete`：为 `true` 表示 JLine 直接补全（替换掉当前单词），为 `false` 表示只列在候选列表里让用户选择。这里全部设为 `true` 让 JLine 自动补全。
- mewpaw-code 的 `SlashCommandCompleter` 更为复杂：它还会根据当前是否在 Agent 循环中来动态调整补全列表（如正在执行工具时，不显示 `/reset` 命令），并且支持参数补全（如 `/help bash` 可以补全具体命令的文档）。

### 3.6 第五步：ReplHistory.java（历史记录持久化）

```java
package com.example.repl.repl;

import org.jline.reader.History;
import org.jline.reader.impl.DefaultHistory;
import org.jline.terminal.Terminal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * ReplHistory —— 命令历史记录管理器
 *
 * 职责：
 *   1. 创建 JLine 的 DefaultHistory 实例（内存中的历史记录）
 *   2. 从文件加载历史记录到内存
 *   3. 在程序退出时将内存中的历史记录保存到文件
 *   4. 限制历史记录条数，防止文件无限膨胀
 *
 * 历史记录文件路径由 application.yml 的 repl.history-file 配置，
 * 默认为 ~/.mewcode_history（用户主目录下的隐藏文件）。
 * 文件格式为纯文本，每行一条命令。
 */
@Component
public class ReplHistory {

    // 日志记录器
    private static final Logger log = LoggerFactory.getLogger(ReplHistory.class);

    // JLine 的 DefaultHistory：内存中的历史记录存储
    private final DefaultHistory history;

    // 历史记录文件路径（从配置注入）
    private final Path historyPath;

    // 最大历史记录条数（从配置注入）
    private final int maxSize;

    /**
     * 构造器注入：Spring 在创建 Bean 时自动注入配置值
     *
     * @param historyFile 配置中的 repl.history-file 值
     * @param maxSize     配置中的 repl.max-history-size 值
     */
    public ReplHistory(
            @Value("${repl.history-file:~/.mewcode_history}") String historyFile,
            @Value("${repl.max-history-size:1000}") int maxSize) {
        // 创建 DefaultHistory 实例（此时还没有关联 Terminal，稍后由 EnhancedRepl 设置）
        this.history = new DefaultHistory();
        // 将文件路径字符串转为 Path 对象，展开 ~ 为用户主目录
        this.historyPath = expandHome(historyFile);
        // 记录最大条数限制
        this.maxSize = maxSize;
        // 设置 DefaultHistory 的最大容量
        this.history.setMaxSize(maxSize);
    }

    /**
     * 将历史记录与 Terminal 关联
     *
     * 注意：DefaultHistory 必须在 Terminal 创建后才能设置 terminal，
     * 因为历史记录在保存时需要用 Terminal 的尺寸来做格式化。
     * 这个方法由 EnhancedRepl 在创建完 Terminal 后调用。
     *
     * @param terminal JLine 的 Terminal 实例
     */
    public void setTerminal(Terminal terminal) {
        // 将 Terminal 绑定到 DefaultHistory
        history.attach(terminal);
    }

    /**
     * 从历史文件加载历史记录到内存
     *
     * JLine 的 DefaultHistory 支持从 Reader 读取历史记录，
     * 每一行对应一条命令。文件不存在时静默跳过（第一次运行）。
     */
    public void load() {
        // 检查历史文件是否存在（第一次运行通常没有）
        if (!Files.exists(historyPath)) {
            log.debug("历史记录文件不存在，跳过加载：{}", historyPath);
            return;
        }
        try {
            // 从文件读取所有行，逐条添加到历史记录中
            // JLine 的 DefaultHistory.read() 需要传入 Reader
            history.read(historyPath.toFile(), maxSize);
            log.info("从 {} 加载了历史记录", historyPath);
        } catch (IOException e) {
            // 加载失败不影响程序运行，只是少了一次历史记录
            log.warn("加载历史记录失败：{}", e.getMessage());
        }
    }

    /**
     * 将内存中的历史记录保存到文件
     *
     * 在程序退出时调用，确保用户本次会话的命令历史被持久化。
     * 调用方（EnhancedRepl）应在退出前调用此方法。
     */
    public void save() {
        try {
            // 确保父目录存在（~ 目录通常存在，但防御性编程）
            Files.createDirectories(historyPath.getParent());
            // 将历史记录写入文件，JLine 的 write() 方法会按格式写入
            history.write(historyPath.toFile());
            log.info("历史记录已保存到 {}", historyPath);
        } catch (IOException e) {
            // 保存失败不会影响程序退出，但会丢失本次会话的历史
            log.warn("保存历史记录失败：{}", e.getMessage());
        }
    }

    /**
     * 获取 JLine 的 DefaultHistory 实例，供 LineReader 使用
     *
     * @return DefaultHistory 实例
     */
    public DefaultHistory getHistory() {
        return history;
    }

    /**
     * 展开 ~ 为用户主目录路径
     *
     * 例如：~/.mewcode_history -> /home/user/.mewcode_history
     * 或者：~/.mewcode_history -> C:\Users\username\.mewcode_history
     *
     * @param path 可能包含 ~ 的路径字符串
     * @return 展开后的绝对路径
     */
    private static Path expandHome(String path) {
        // 如果路径以 ~ 开头，替换为系统属性 user.home 的值
        if (path.startsWith("~")) {
            // System.getProperty("user.home") 返回用户主目录路径
            return Paths.get(System.getProperty("user.home"),
                    path.substring(1).replace("\\", "/").replaceFirst("^/", ""));
        }
        // 不以 ~ 开头，直接返回绝对路径
        return Paths.get(path).toAbsolutePath();
    }
}
```

**逐行要点：**
- `DefaultHistory` 是 JLine 内置的历史记录实现，内部用 `LinkedList` 存储，支持 `setMaxSize()` 限制最大容量，超出时自动移除最旧的记录。
- `history.attach(terminal)` 是关键的绑定操作：DefaultHistory 在保存时需要知道终端的宽度，以便在写入文件时正确格式化（例如较长的行可能被自动折行）。
- `history.read(file, maxSize)` 从文件读取历史记录，第二个参数限制读取条数。文件格式是纯文本，每行一条命令，空行被忽略。
- `expandHome()` 方法手动处理 `~` 展开，因为 JLine 的 `Paths.get()` 不会自动展开 `~`。注意 Windows 下 `~` 通常不常用，但 `System.getProperty("user.home")` 在所有平台上都能正确返回用户主目录。
- mewpaw-code 的 `ReplHistory` 还支持多会话历史隔离（通过 `--session` 参数指定不同的历史文件），以及历史记录的"脏标记"机制（只在有变更时保存，减少磁盘 IO）。

### 3.7 第六步：EnhancedRepl.java（主 REPL 循环，全篇核心）

这是全篇最重要的文件：完整的 REPL 循环实现，整合了 Terminal、LineReader、Completer、History，并提供了优雅的退出处理。

```java
package com.example.repl.repl;

import org.jline.keymap.KeyMap;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.reader.impl.DefaultParser;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * EnhancedRepl —— 增强型 REPL 循环（全篇核心）
 *
 * 这是 JLine 3 REPL 的完整实现，整合了：
 *   1. Terminal 创建（物理终端抽象）
 *   2. LineReader 创建（行编辑 + 补全 + 历史）
 *   3. 主循环（read -> eval -> print -> loop）
 *   4. 异常处理（Ctrl+C 中断、Ctrl+D 退出）
 *   5. 回退机制（无终端时退化为 BufferedReader）
 *
 * 主循环逻辑：
 *   while (running) {
 *       String line = readLine();      // 读取一行输入
 *       if (line == null) break;       // EOF 处理
 *       String result = evaluate(line); // 执行命令
 *       print(result);                  // 打印结果
 *   }
 *
 * 对应 mewpaw-code 的 EnhancedRepl 类，但简化了 Agent 集成部分。
 */
@Component
public class EnhancedRepl {

    // 日志记录器
    private static final Logger log = LoggerFactory.getLogger(EnhancedRepl.class);

    // 依赖的组件
    private final SlashCommandCompleter completer;  // Tab 补全器
    private final ReplHistory replHistory;          // 历史记录管理器
    private final TuiState tuiState;                // 终端状态检测

    // 配置注入
    private final String prompt;                    // 提示符字符串

    // JLine 核心对象
    private Terminal terminal;                      // 物理终端抽象
    private LineReader reader;                      // 行编辑器

    // 运行状态
    private volatile boolean running = true;        // 运行标志（volatile 保证跨线程可见性）

    /**
     * 构造器注入：Spring 将所有依赖传入
     *
     * @param completer   斜杠命令补全器
     * @param replHistory 历史记录管理器
     * @param tuiState    终端状态检测
     * @param prompt      提示符（从配置注入，默认 "repl> "）
     */
    public EnhancedRepl(
            SlashCommandCompleter completer,
            ReplHistory replHistory,
            TuiState tuiState,
            @Value("${repl.prompt:repl> }") String prompt) {
        this.completer = completer;
        this.replHistory = replHistory;
        this.tuiState = tuiState;
        this.prompt = prompt;
    }

    /**
     * 启动 REPL 循环
     *
     * 这是 REPL 的入口方法，包含完整的初始化 + 循环 + 清理流程：
     *   1. 检查终端状态（是否有物理终端）
     *   2. 初始化 JLine（Terminal + LineReader）
     *   3. 加载历史记录
     *   4. 进入主循环
     *   5. 退出时保存历史记录 + 关闭终端
     */
    public void start() {
        // ---------- 前置检查 ----------
        // 判断是否有物理终端（JLine 需要原始终端模式）
        if (!tuiState.hasConsole()) {
            // 没有物理终端：退化为简单的 BufferedReader 模式
            log.warn("没有检测到物理终端，退化为非交互式模式");
            runFallback();  // 简单读取 stdin 后退出
            return;
        }

        // 判断输入是否被管道重定向
        if (tuiState.isPiped()) {
            // 管道输入模式：也退化为非交互式
            log.info("检测到管道输入，退化为非交互式模式");
            runFallback();
            return;
        }

        // ---------- JLine 初始化 ----------
        try {
            // 1. 创建 Terminal：JLine 与物理终端的桥梁
            //    system(true) 表示使用系统终端（而非内嵌终端模拟器）
            terminal = TerminalBuilder.builder()
                    .system(true)       // 使用系统终端
                    .build();           // 自动检测并创建合适的实现

            // 2. 将历史记录与 Terminal 绑定
            replHistory.setTerminal(terminal);

            // 3. 加载持久化的历史记录
            replHistory.load();

            // 4. 创建 Parser：解析输入行
            DefaultParser parser = new DefaultParser();
            // escapeChars(null) 是关键：禁用 JLine 默认的转义处理
            // 默认情况下 JLine 会把 \ 当作转义字符，导致 /slash 命令中的
            // 反斜杠被吃掉（如 /tools\list 会变成 /toolslist）
            // 传入 null 表示"不转义任何字符"，保持原始输入
            parser.setEscapeChars(null);

            // 5. 构建 LineReader：REPL 的心脏
            reader = LineReaderBuilder.builder()
                    .terminal(terminal)                     // 绑定终端
                    .completer(completer)                   // 绑定 Tab 补全器
                    .parser(parser)                         // 绑定解析器
                    .history(replHistory.getHistory())      // 绑定历史记录
                    .keymap(KeyMap.EMACS)                   // 使用 Emacs 键绑定
                    .build();

            // 6. 打印启动提示
            System.out.println("Hello TUI REPL 已启动！输入 /help 查看帮助，/exit 退出。");

            // ---------- 主循环 ----------
            // 循环执行：读取 -> 求值 -> 打印
            while (running) {
                try {
                    // readLine() 是核心方法：
                    //   - 输出提示符 prompt
                    //   - 等待用户输入
                    //   - 支持行编辑、Tab 补全、历史搜索
                    //   - 用户按回车时返回完整输入行
                    String line = reader.readLine(prompt);

                    // 空行处理：跳过空输入（只按了回车）
                    if (line == null || line.isBlank()) {
                        continue;
                    }

                    // 调用 evaluate() 执行命令，打印结果
                    String result = evaluate(line);
                    // 结果为 null 表示退出信号（/exit 命令）
                    if (result == null) {
                        break;
                    }
                    // 打印执行结果
                    System.out.println(result);

                } catch (UserInterruptException e) {
                    // Ctrl+C 捕获：用户想中断当前输入但不退出
                    // 处理方式：换行后重新显示提示符，等待下一次输入
                    // JLine 在收到 Ctrl+C 后会抛出 UserInterruptException，
                    // 但不会关闭终端，我们只需继续循环即可
                    System.out.println();  // 换行，使提示符另起一行
                    // 继续循环，重新显示提示符

                } catch (EndOfFileException e) {
                    // Ctrl+D 捕获：用户想退出程序
                    // EndOfFileException 表示"不再有输入"（EOF）
                    // 在 Unix 终端中，Ctrl+D 发送 EOF 信号
                    // 处理方式：优雅退出
                    System.out.println();  // 换行
                    break;                  // 退出循环
                }
            }

        } catch (IOException e) {
            // Terminal 创建失败：可能是终端类型不支持
            log.error("JLine 终端初始化失败：{}", e.getMessage());
            // 退化为非交互模式
            runFallback();
        } finally {
            // ---------- 清理工作 ----------
            // 保存历史记录（确保即使异常退出也能保存）
            saveHistory();
            // 关闭终端（恢复终端的原始模式设置）
            closeTerminal();
        }
    }

    /**
     * evaluate —— 执行用户输入的命令
     *
     * 输入处理逻辑：
     *   1. 以 "/" 开头 -> 斜杠命令（/help, /exit, /clear 等）
     *   2. 其他输入 -> 普通文本（这里简单回显，实际项目交给 Agent 处理）
     *
     * @param line 用户输入的完整行
     * @return 执行结果字符串（null 表示退出）
     */
    private String evaluate(String line) {
        // 去除首尾空白
        String trimmed = line.trim();

        // 斜杠命令处理
        if (trimmed.startsWith("/")) {
            return handleSlashCommand(trimmed);
        }

        // 普通文本：简单回显（真实项目会交给 Agent 处理）
        return "你说的是：" + trimmed;
    }

    /**
     * handleSlashCommand —— 处理斜杠命令
     *
     * @param cmd 用户输入的完整命令字符串（含斜杠）
     * @return 命令执行结果（null 表示退出）
     */
    private String handleSlashCommand(String cmd) {
        // 按空格拆分命令和参数
        // 例如 "/help" -> ["/help"]，"/debug on" -> ["/debug", "on"]
        String[] parts = cmd.split("\\s+", 2);  // 最多拆成 2 段
        String command = parts[0];               // 命令名（含斜杠）
        // String args = parts.length > 1 ? parts[1] : "";  // 参数（暂未使用）

        // switch 表达式按命令名分派处理
        return switch (command) {
            case "/help" -> handleHelp();       // 显示帮助
            case "/exit" -> handleExit();       // 退出程序
            case "/clear" -> handleClear();     // 清屏
            case "/history" -> handleHistory(); // 显示历史
            case "/tools" -> handleTools();     // 列出工具
            case "/status" -> handleStatus();   // 显示状态
            case "/debug" -> handleDebug();     // 切换调试
            case "/reset" -> handleReset();     // 重置上下文
            // 未知命令的兜底处理
            default -> "未知命令：" + command + "，输入 /help 查看可用命令。";
        };
    }

    // ---------- 斜杠命令处理实现 ----------

    /**
     * /help —— 显示所有可用命令的帮助信息
     */
    private String handleHelp() {
        // 使用文本块构建格式化的帮助信息
        return """
                === 可用命令 ===
                /help    - 显示本帮助信息
                /exit    - 退出 REPL 程序
                /clear   - 清屏
                /history - 显示命令历史记录
                /tools   - 列出可用工具
                /status  - 显示当前状态
                /debug   - 切换调试模式
                /reset   - 重置对话上下文
                ================
                """;
    }

    /**
     * /exit —— 退出程序
     * 返回 null 通知主循环退出
     */
    private String handleExit() {
        // 设置运行标志为 false
        running = false;
        // 返回 null 表示"退出信号"
        return null;
    }

    /**
     * /clear —— 清空终端屏幕
     * 使用 JLine 的终端能力发送清屏控制序列
     */
    private String handleClear() {
        // 检查终端是否可用
        if (terminal != null) {
            // terminal.puts(Capability.clear_screen) 发送清屏 ANSI 控制序列
            // 这会发送 \033[2J\033[H（清除屏幕并移动光标到左上角）
            terminal.puts(org.jline.terminal.Terminal.Mode.ANSI, "\033[2J\033[H");
            // 刷新终端输出缓冲区，确保控制序列立即生效
            terminal.flush();
        }
        // 清屏后不需要额外的输出文本
        return "";
    }

    /**
     * /history —— 显示历史记录
     * 从 DefaultHistory 中获取所有历史记录并格式化输出
     */
    private String handleHistory() {
        // 获取历史记录对象
        org.jline.reader.History history = replHistory.getHistory();
        // 使用 StringBuilder 构建输出
        StringBuilder sb = new StringBuilder();
        sb.append("=== 命令历史记录（共 ").append(history.size()).append(" 条） ===\n");
        // 遍历历史记录，使用 iterator() 按时间顺序（从旧到新）
        int index = 1;
        for (org.jline.reader.History.Entry entry : history) {
            // 每行格式：序号 + 命令内容
            sb.append(String.format("%4d  %s%n", index++, entry.line()));
        }
        // 去除末尾换行符
        return sb.toString().stripTrailing();
    }

    /**
     * /tools —— 列出可用工具（当前为占位，真实项目会列出所有注册的工具）
     */
    private String handleTools() {
        // 入门版本只有"回显"工具，真实项目会从 ToolRegistry 获取
        return """
                当前可用工具：
                  - echo   : 回显用户输入的内容
                """;
    }

    /**
     * /status —— 显示当前运行状态
     */
    private String handleStatus() {
        // 使用文本块返回状态信息
        return """
                状态信息：
                  - 运行模式：JLine REPL
                  - 提示符：%s
                  - 历史记录文件：%s
                  - 历史记录条数：%d
                """.formatted(prompt, replHistory.getHistory(), replHistory.getHistory().size());
    }

    /**
     * /debug —— 切换调试模式（当前为占位）
     */
    private String handleDebug() {
        // 入门版本只是打印提示，真实项目会切换日志级别或打印更多信息
        return "调试模式切换功能将在后续版本中实现。";
    }

    /**
     * /reset —— 重置会话上下文（当前为占位）
     */
    private String handleReset() {
        // 入门版本只是打印提示，真实项目会清空 Agent 的对话历史
        return "会话已重置（当前为占位，实际项目会清空 Agent 上下文）。";
    }

    // ---------- 回退模式 ----------

    /**
     * runFallback —— 非交互式回退模式
     *
     * 当没有物理终端或输入被管道重定向时使用。
     * 使用标准的 BufferedReader 读取 stdin，处理完立即退出。
     * 这保证了程序在管道输入和 CI/CD 环境中也能正常工作。
     */
    private void runFallback() {
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
            String line;
            // 逐行读取 stdin，直到 EOF
            while ((line = br.readLine()) != null) {
                // 处理每一行（和交互式模式使用相同的 evaluate 逻辑）
                String result = evaluate(line);
                if (result == null) {
                    break;  // /exit 命令
                }
                // 输出结果
                System.out.println(result);
            }
        } catch (IOException e) {
            log.error("非交互式模式读取失败：{}", e.getMessage());
        }
    }

    // ---------- 生命周期管理 ----------

    /**
     * 关闭 REPL 循环（外部调用，用于优雅关闭）
     * 设置 running 标志为 false，让主循环在下一轮退出
     */
    public void stop() {
        running = false;
    }

    /**
     * 保存历史记录到文件
     * 在 finally 块中调用，确保即使异常也能保存
     */
    private void saveHistory() {
        try {
            replHistory.save();
        } catch (Exception e) {
            // 保存失败不影响退出，只记录日志
            log.warn("保存历史记录时发生异常：{}", e.getMessage());
        }
    }

    /**
     * 关闭终端，恢复原始模式
     * 在 finally 块中调用，确保资源释放
     */
    private void closeTerminal() {
        if (terminal != null) {
            try {
                // 关闭终端：恢复回显、行缓冲等原始设置
                terminal.close();
            } catch (IOException e) {
                log.warn("关闭终端时发生异常：{}", e.getMessage());
            }
        }
    }
}
```

**逐行要点（这是全篇最重要的代码段）：**

1. **Terminal 创建**：`TerminalBuilder.builder().system(true).build()` 是 JLine 3 的标准初始化方式。`system(true)` 表示"使用真实的系统终端"，而不是内嵌的测试终端模拟器。在 Windows 上，JLine 3 会使用 JNA 调用 Windows Console API；在 Linux/Mac 上，会通过 JNI 调用 `termios`。

2. **Parser 配置**：`parser.setEscapeChars(null)` 是 REPL 配置中**最容易踩坑的一行**。JLine 的 `DefaultParser` 默认会将 `\` 视为转义字符（`\n` 表示换行、`\t` 表示制表符），这在斜杠命令场景下会出问题：用户输入 `/tools\list` 时，JLine 会吃掉反斜杠，使命令变成 `/toolslist` 而无法匹配。设置 `escapeChars(null)` 后，JLine 不再对任何字符做转义，保持原始输入。

3. **Emacs 键绑定**：`KeyMap.EMACS` 提供开发者熟悉的行编辑快捷键：
   - `Ctrl+A` / `Home`：跳转到行首
   - `Ctrl+E` / `End`：跳转到行尾
   - `Ctrl+K`：删除从光标到行尾的文本
   - `Ctrl+U`：删除整行
   - `Ctrl+P` / `Up`：上一条历史
   - `Ctrl+N` / `Down`：下一条历史
   - `Ctrl+R`：反向搜索历史
   - `Tab`：触发补全

4. **异常处理**：
   - `UserInterruptException`（Ctrl+C）：不退出程序，只是中断当前输入，重新显示提示符。这是 REPL 应有的行为——用户偶尔按错 Ctrl+C 不应该退出程序。
   - `EndOfFileException`（Ctrl+D）：优雅退出。在 Unix 终端中，Ctrl+D 发送 EOF 信号；在 Windows 中，Ctrl+Z + Enter 也是 EOF。JLine 统一将其包装为 `EndOfFileException`。

5. **回退模式**：`runFallback()` 在三种情况下被调用：没有物理终端、输入被管道重定向、JLine 初始化失败。这保证了程序在 IDE 中运行、CI/CD 管道中、以及 `echo "hello" | java -jar app.jar` 等场景下都能正常工作。

6. **mewpaw-code 对照**：真实的 `EnhancedRepl` 在此基础上做了大量扩展：
   - `evaluate()` 方法不直接处理命令，而是交给 `AgentLoop` 执行完整的 ReAct 循环
   - 支持 `AgentOutputMode` 的流式输出（逐字打印 AI 的回答）
   - 支持 `--tui` 参数启动 Lanterna 全屏模式
   - 支持 `MultiLine` 多行输入模式（输入 `{` 后自动进入多行编辑）
   - 斜杠命令有更丰富的参数支持（如 `/help bash` 显示 Bash 工具的具体文档）

### 3.8 第七步：HelloCliAgentApplication.java（主类：Spring Boot + Picocli + REPL 合体）

```java
package com.example.repl;

import com.example.repl.repl.EnhancedRepl;
import com.example.repl.repl.TuiState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.Arrays;

/**
 * HelloCliAgentApplication —— 应用入口（Spring Boot + Picocli + REPL 三合一）
 *
 * 职责拆分：
 *  - @SpringBootApplication：声明这是一个 Spring Boot 应用
 *  - @Command：声明这是一个 Picocli 命令行命令（自带 --help / --version）
 *  - CommandLineRunner：Spring 容器就绪后自动执行 run()
 *  - ExitCodeGenerator：向 Spring 提供 JVM 退出码
 *
 * 启动流程：
 *   1. main() 检查是否有 --web 参数，决定 CLI 模式还是 Web 模式
 *   2. Spring 容器启动，创建所有 Bean（包括 EnhancedRepl、TuiState 等）
 *   3. 容器就绪后，CommandLineRunner.run() 被调用
 *   4. Picocli 解析命令行参数，绑定到 @Option 字段
 *   5. 根据参数决定：直接输出结果（非交互）还是启动 REPL 循环（交互）
 *
 * 对应 mewpaw-code 的 MewCodeAgentApplication，同样实现
 * CommandLineRunner + ExitCodeGenerator，支持 --web 双模式切换。
 */
@SpringBootApplication
// Picocli 命令声明：命令名 hello-repl，自动提供 --help 和 --version
@Command(name = "hello-repl",
        mixinStandardHelpOptions = true,
        version = "Hello TUI REPL 1.0.0",
        description = "一个基于 JLine 3 的 REPL 交互式命令行工具")
public class HelloCliAgentApplication
        implements CommandLineRunner,   // Spring 容器就绪后的入口钩子
        ExitCodeGenerator {             // 向 Spring 提供 JVM 退出码

    // 日志记录器
    private static final Logger log = LoggerFactory.getLogger(HelloCliAgentApplication.class);

    // ---------- Picocli 命令行参数 ----------

    /** -p / --prompt：指定 REPL 提示符 */
    @Option(names = {"-p", "--prompt"}, description = "REPL 提示符（默认：repl> ）")
    private String prompt;

    /** -w / --workdir：指定工作目录 */
    @Option(names = {"-w", "--workdir"}, description = "工作目录路径")
    private String workdir;

    /** --tui：启用全屏 TUI 模式（当前为占位，后续与 Lanterna 集成） */
    @Option(names = {"--tui"}, description = "启用全屏 TUI 模式（实验性功能）")
    private boolean tui;

    /** --model：指定 AI 模型名称（当前为占位，后续与 LLM 集成） */
    @Option(names = {"--model"}, description = "AI 模型名称")
    private String model;

    /** --temperature：指定 AI 模型温度参数（当前为占位） */
    @Option(names = {"--temperature"}, description = "AI 模型温度参数（0.0 ~ 1.0）")
    private Double temperature;

    /** --web：以 Web 模式启动（传了则启动 Tomcat） */
    @Option(names = {"--web"}, description = "以 Web 模式启动")
    private boolean web;

    /**
     * 位置参数：可选的默认提示文本
     * 不指定选项名时，第一个参数会自动绑定到此字段
     */
    @picocli.CommandLine.Parameters(index = "0", description = "可选的默认提示消息")
    private String defaultMessage;

    // 依赖注入：Spring 自动创建这些 Bean 并传入
    private final EnhancedRepl repl;    // 主 REPL 循环
    private final TuiState tuiState;    // 终端状态检测

    // 记录本次运行的退出码，getExitCode() 会把它返回给 JVM
    private int exitCode = 0;

    /**
     * 构造器注入：Spring 创建主类 Bean 时自动传入依赖
     */
    public HelloCliAgentApplication(EnhancedRepl repl, TuiState tuiState) {
        this.repl = repl;
        this.tuiState = tuiState;
    }

    // ---------- 程序入口 ----------

    /**
     * main 方法：Java 进程的起点
     *
     * @param args 命令行参数（透传给 Picocli 解析）
     */
    public static void main(String[] args) {
        // 创建 SpringApplication 实例
        SpringApplication app = new SpringApplication(HelloCliAgentApplication.class);
        // 检查是否有 --web 参数
        if (!hasWebArg(args)) {
            // 没有 --web 参数：强制非 Web 模式（不启动 Tomcat，秒级启动）
            app.setWebApplicationType(WebApplicationType.NONE);
        }
        // 启动 Spring 容器：创建 Bean -> 刷新上下文 -> 执行 CommandLineRunner
        app.run(args);
    }

    /**
     * 扫描参数数组：判断是否包含 --web 参数
     *
     * @param args 原始命令行参数数组
     * @return 如果包含 --web 返回 true，否则 false
     */
    private static boolean hasWebArg(String[] args) {
        // 使用 Stream API 的 anyMatch 进行短路判断
        return Arrays.stream(args).anyMatch(arg -> "--web".equals(arg));
    }

    // ---------- CommandLineRunner：Spring 容器就绪后的回调 ----------

    /**
     * run()：Spring 框架在容器刷新完成后调用
     *
     * 此时所有 Bean 已就绪，我们可以根据命令行参数决定交互方式：
     *   1. 如果有 --prompt 参数且没有其他交互需求 -> 非交互模式，输出结果后退出
     *   2. 其他情况 -> 启动 REPL 循环，等待用户交互
     *
     * 注意：这里没有直接使用 Picocli 的 Callable 模式，
     * 而是手动解析参数（因为我们在 CommandLineRunner 层面启动 REPL，
     * 需要更灵活的控制权）。
     */
    @Override
    public void run(String... args) {
        // 打印启动信息
        log.info("Hello TUI REPL 启动中...");
        log.info("终端状态：hasConsole={}, isPiped={}",
                tuiState.hasConsole(), tuiState.isPiped());

        // 打印参数信息（如果有）
        if (defaultMessage != null && !defaultMessage.isBlank()) {
            System.out.println("默认消息：" + defaultMessage);
        }

        // 打印工作目录信息（如果有）
        if (workdir != null && !workdir.isBlank()) {
            System.out.println("工作目录：" + workdir);
        }

        // 打印模型信息（如果有）
        if (model != null && !model.isBlank()) {
            System.out.println("AI 模型：" + model);
        }

        // 检查是否指定了 --tui 参数
        if (tui) {
            System.out.println("TUI 模式：当前为占位，后续版本将集成 Lanterna 全屏界面");
            // 真实项目会在这里启动 Lanterna 的 TuiApp
        }

        // 启动 REPL 循环（这是核心交互方式）
        // EnhancedRepl.start() 会自行判断终端状态，
        // 有物理终端则启动 JLine REPL，否则退化为 BufferedReader 模式
        repl.start();

        // REPL 退出后，设置退出码为 0（正常退出）
        exitCode = 0;
    }

    // ---------- ExitCodeGenerator：向 JVM 提供退出码 ----------

    /**
     * getExitCode()：Spring 在应用退出时读取此值
     * 配合 SpringApplication.exit() 使用即可控制进程返回码
     *
     * @return 退出码（0 = 成功，非 0 = 失败）
     */
    @Override
    public int getExitCode() {
        return exitCode;
    }
}
```

**逐行要点：**
- `main()` 中的双模式判断是 mewpaw-code 的"招牌设计"：同一个 jar，没有 `--web` 就是 CLI 应用（REPL 交互），有 `--web` 就是 Web 应用（启动 Tomcat 提供 REST API）。`app.setWebApplicationType(WebApplicationType.NONE)` 是关键行。
- `@Option` 注解的参数中，`--model` 和 `--temperature` 在当前入门版中只是占位，真实 mewpaw-code 中它们会传递给 `LlmProvider` 来配置 LLM 调用参数。
- `@Parameters` 注解的位置参数是 Picocli 的一个便利特性：用户不指定选项名时，第一个非选项参数会自动绑定到 `defaultMessage` 字段。例如 `java -jar app.jar "你好"` 会把 `"你好"` 绑定到该字段。
- `run()` 方法中调用了 `repl.start()`，这是整个应用的"心脏"——控制权从 Spring 转交到 JLine REPL 循环。`start()` 方法会阻塞直到用户输入 `/exit` 或 Ctrl+D。

### 3.9 第八步：HelloCliAgentApplicationTest.java（单元测试）

```java
package com.example.repl;

import com.example.repl.repl.EnhancedRepl;
import com.example.repl.repl.ReplHistory;
import com.example.repl.repl.SlashCommandCompleter;
import com.example.repl.repl.TuiState;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HelloCliAgentApplicationTest —— 端到端单元测试
 *
 * @SpringBootTest 会启动完整 Spring 容器（非 Web 模式），
 * 所有 Bean 真实装配。测试不依赖物理终端（JLine 在无终端时不会崩溃）。
 */
@SpringBootTest
class HelloCliAgentApplicationTest {

    // 自动注入所有核心 Bean，验证 Spring 容器装配正确
    @Autowired
    private HelloCliAgentApplication app;

    @Autowired
    private EnhancedRepl repl;

    @Autowired
    private TuiState tuiState;

    @Autowired
    private ReplHistory replHistory;

    @Autowired
    private SlashCommandCompleter completer;

    /** 用例 1：Spring 容器能正常启动 */
    @Test
    void contextLoads() {
        // 断言所有注入的 Bean 不为 null
        assertThat(app).isNotNull();
        assertThat(repl).isNotNull();
        assertThat(tuiState).isNotNull();
        assertThat(replHistory).isNotNull();
        assertThat(completer).isNotNull();
    }

    /** 用例 2：TuiState 在没有终端时返回正确的状态 */
    @Test
    void tuiStateDetectsNoConsole() {
        // 在测试环境中，System.console() 通常返回 null（没有物理终端）
        // 所以 hasConsole() 应该返回 false
        assertThat(tuiState.hasConsole()).isFalse();
    }

    /** 用例 3：ReplHistory 配置正确 */
    @Test
    void replHistoryConfiguration() {
        // 验证历史记录对象不为 null
        assertThat(replHistory.getHistory()).isNotNull();
        // 验证历史记录最大条数（默认 1000）
        assertThat(replHistory.getHistory().getMaxSize()).isEqualTo(1000);
    }

    /** 用例 4：SlashCommandCompleter 不为 null */
    @Test
    void completerExists() {
        // 验证补全器 Bean 存在
        assertThat(completer).isNotNull();
    }

    /** 用例 5：主类 Picocli 注解存在 */
    @Test
    void applicationHasPicocliCommand() {
        // 验证主类上有 @Command 注解
        picocli.CommandLine.Command command =
                app.getClass().getAnnotation(picocli.CommandLine.Command.class);
        assertThat(command).isNotNull();
        // 命令名应该是 hello-repl
        assertThat(command.name()).isEqualTo("hello-repl");
    }

    /** 用例 6：TuiState 的管道检测在测试环境中不抛异常 */
    @Test
    void tuiStatePipedDetectionDoesNotThrow() {
        // isPiped() 在测试环境中可能返回 true 或 false，
        // 但无论如何不应该抛出异常
        assertThat(tuiState.isPiped()).isIn(true, false);
    }

    /** 用例 7：ReplHistory 加载和保存不抛异常 */
    @Test
    void replHistoryLoadSaveDoesNotThrow() {
        // 在测试环境中，load() 和 save() 不应该抛出异常
        // 即使历史文件不存在，load() 也应静默跳过
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> {
            replHistory.load();
        });
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> {
            replHistory.save();
        });
    }
}
```

**逐行要点：**
- `tuiStateDetectsNoConsole` 测试了 TuiState 在 IDE/测试环境中的行为：`System.console()` 在 IDE 的 Run 窗口中通常返回 null，所以 `hasConsole()` 返回 false。这符合预期——测试环境没有物理终端。
- `replHistoryConfiguration` 验证了配置绑定：`replHistory.getHistory().getMaxSize()` 应该等于 1000（application.yml 中配置的值）。
- `replHistoryLoadSaveDoesNotThrow` 使用了 `assertDoesNotThrow` 来验证历史记录加载/保存不会抛出异常。这是重要的防御性验证——REPL 的启动流程中，历史记录加载失败不应该导致程序崩溃。
- 注意测试中没有使用任何 Mock（Mockito），因为所有组件都是"可测试的"：它们不依赖外部资源，不依赖物理终端。这正是良好设计的标志——组件边界清晰，依赖可被替换。

---

## 四、运行验证：让 Hello TUI REPL 真正跑起来

### 4.1 运行前的准备

```bash
# 1. 确认 JDK 21 已安装
java -version          # 期望输出 java 21.0.x 字样

# 2. 确认 Maven 可用
mvn -version           # 期望输出 Maven 3.8+ 和 JAVA_HOME 指向 21

# 3. 确保在项目根目录
cd hello-tui-repl
```

### 4.2 方式一：Maven 直接运行（开发期最常用）

```bash
# 用 Maven 启动，进入交互式 REPL
mvn -q spring-boot:run
```

**预期输出：**

```
Hello TUI REPL 已启动！输入 /help 查看帮助，/exit 退出。
repl>
```

此时程序进入 REPL 循环，等待输入。你可以在提示符后输入命令并体验交互：

```text
repl> 你好
你说的是：你好
repl> /help
=== 可用命令 ===
/help    - 显示本帮助信息
/exit    - 退出 REPL 程序
/clear   - 清屏
/history - 显示命令历史记录
/tools   - 列出可用工具
/status  - 显示当前状态
/debug   - 切换调试模式
/reset   - 重置对话上下文
================
repl> /status
状态信息：
  - 运行模式：JLine REPL
  - 提示符：repl>
  - 历史记录文件：ReplHistory@...
  - 历史记录条数：2
repl> /history
=== 命令历史记录（共 2 条） ===
   1  你好
   2  /help
   3  /status
repl> /exit
```

### 4.3 Tab 补全体验

在 REPL 提示符下输入 `/` 然后按 Tab 键：

```text
repl> /          <- 输入 / 后按 Tab
/clear   /debug   /exit    /help    /history  /reset   /status  /tools
```

输入 `/h` 然后按 Tab，JLine 会自动补全到匹配的命令：

```text
repl> /h          <- 输入 /h 后按 Tab
repl> /help       <- 自动补全为 /help
```

### 4.4 历史记录体验

- 输入 `/help` 然后按回车
- 按 `Up` 键（上箭头），刚才输入的 `/help` 会重新显示在提示符后
- 按 `Down` 键（下箭头），回到空输入状态
- 按 `Ctrl+R` 进入反向搜索模式，输入 `hel` 可以搜索到 `/help` 命令

### 4.5 方式二：打包成胖 jar（生产期标准做法）

```bash
# 1. 打包（跳过测试）
mvn -q package -DskipTests

# 2. 用 java -jar 直接运行
java -jar target/hello-tui-repl-0.0.1-SNAPSHOT.jar

# 3. 查看自动生成的帮助文档（Picocli 白送的能力）
java -jar target/hello-tui-repl-0.0.1-SNAPSHOT.jar --help
```

`--help` 预期输出：

```
Usage: hello-repl [-hV] [--tui] [--web] [-p=<prompt>] [-w=<workdir>]
                  [--model=<model>] [--temperature=<temperature>]
                  [<defaultMessage>]
一个基于 JLine 3 的 REPL 交互式命令行工具
      [<defaultMessage>]   可选的默认提示消息
  -h, --help               Show this help message and exit.
      --model=<model>      AI 模型名称
  -p, --prompt=<prompt>    REPL 提示符（默认：repl> ）
      --temperature=<temperature>
                            AI 模型温度参数（0.0 ~ 1.0）
      --tui                 启用全屏 TUI 模式（实验性功能）
  -V, --version            Print version information and exit.
  -w, --workdir=<workdir>  工作目录路径
      --web                 以 Web 模式启动
```

### 4.6 方式三：非交互模式（管道输入）

```bash
# 管道模式：echo 内容通过管道传给程序
echo "你好" | java -jar target/hello-tui-repl-0.0.1-SNAPSHOT.jar
```

**预期输出：**

```
你说的是：你好
```

注意：程序没有进入交互式 REPL，而是直接读取管道内容、输出结果、退出。这是 `TuiState.isPiped()` 检测到管道输入后自动切换到的回退模式。

### 4.7 方式四：运行单元测试

```bash
mvn test
```

**预期输出结尾：**

```
[INFO] Tests run: 7, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

7 个用例全部通过，说明：Spring 容器装配正确、TuiState 检测逻辑正常、ReplHistory 操作不抛异常、Completer Bean 存在、Picocli 注解正确。

### 4.8 验证点自查清单

| 验证内容 | 操作 | 通过标准 |
|---------|------|---------|
| REPL 启动 | `mvn spring-boot:run` | 显示 "Hello TUI REPL 已启动！" |
| 提示符显示 | 启动后观察 | 出现 `repl>` 提示符 |
| 普通输入 | 输入 "你好" | 回显 "你说的是：你好" |
| 斜杠命令 | 输入 `/help` | 显示帮助信息 |
| Tab 补全 | 输入 `/` 后按 Tab | 显示补全候选列表 |
| 历史记录 | 按 Up 键 | 上次输入的命令重新出现 |
| Ctrl+D 退出 | 按 Ctrl+D | 程序优雅退出 |
| 管道模式 | `echo "hi" | java -jar app.jar` | 输出 "你说的是：hi" 后退出 |
| 帮助文档 | `java -jar app.jar --help` | 显示格式化的帮助信息 |
| 全部测试通过 | `mvn test` | Tests run: 7, Failures: 0 |

---

## 五、项目对照：入门 Demo vs 真实 mewpaw-code

现在，把我们的 Hello TUI REPL 和真实项目摆在一起看，你会发现**架构骨架完全一致，但每个零件的"马力"完全不同**：

| 维度 | 本篇 Demo（Hello TUI REPL） | 真实 mewpaw-code | 差距在哪 |
|------|---------------------------|------------------|---------|
| 交互模式 | 仅 JLine 3 REPL | JLine 3 REPL + Lanterna 全屏 TUI | 双模式支持 |
| 斜杠命令 | 8 个基本命令，无参数 | 20+ 命令，支持参数和子命令 | 命令丰富度 |
| 命令补全 | 固定列表补全 | 动态补全（根据上下文、工具列表、文件系统） | 上下文感知 |
| 历史记录 | 基础持久化 | 多会话隔离 + 脏标记 + 文件锁 | 并发安全 |
| 终端检测 | 基础检测 | 附加 tmux/SSH/颜色支持检测 | 环境适配 |
| 输入处理 | 回显模式 | 完整 Agent 集成（AgentLoop + ToolRegistry） | 业务集成 |
| 退出处理 | 基础 | 确认提示 + 未完成任务清理 | 用户体验 |
| 参数解析 | 基础 Picocli | 额外 --config, --session, --log-level 等 | 参数丰富度 |
| 双模式 | --web 占位 | 真实 Web 模式（Spring MVC REST API） | 双模式实现 |
| 测试覆盖 | 7 个用例 | 集成测试 + 终端的模拟测试 | 测试深度 |

**对照结论：** 入门 Demo 是 mewpaw-code 交互层的"骨架"——JLine 3 的 Terminal、LineReader、Completer、History 四大组件全部到位，终端检测、回退模式、异常处理全部覆盖。真实项目在这个骨架上做了三件事：

1. **横向扩展**：增加了更多斜杠命令、更丰富的补全逻辑、Lanterna 全屏 TUI 模式
2. **纵向集成**：把 `evaluate()` 从"简单回显"替换为"AgentLoop 完整 ReAct 循环"
3. **工程加固**：加入了多会话隔离、文件锁、并发安全、用户确认等生产级特性

**学习路径建议：** 如果本篇你已经跑通，下一步按顺序读：
1. [02-react-agent-loop.md](../../02-react-agent-loop.md)：把 REPL 的 `evaluate()` 替换为 Agent 循环
2. [03-langchain4j-tools.md](../../03-langchain4j-tools.md)：把"回显"替换为真实的 LLM 调用
3. [04-security-sandbox.md](../../04-security-sandbox.md)：给工具执行加上安全沙箱
4. 回看本篇的 `EnhancedRepl`，理解 Agent 集成后的完整交互流程

---

## 六、面试题（3 道）

**Q1：JLine 3 的 Terminal 和 LineReader 分别扮演什么角色？为什么需要 TerminalBuilder 先创建 Terminal，再创建 LineReader？**

A：Terminal 是操作系统中"物理终端"的抽象层，负责管理终端的原始模式（Raw Mode）、尺寸查询、光标控制、颜色输出等底层能力，通过 JNI 调用 termios（Linux/Mac）或 Windows Console API 实现。LineReader 则是在 Terminal 之上提供"行编辑"的高层接口——它负责读取一行输入、处理方向键/退格键/Ctrl 组合键、触发 Tab 补全、管理历史记录等。两者的关系是"基础设施 vs 业务逻辑"：Terminal 提供"能和终端对话"的能力，LineReader 在此基础上提供"能友好地和终端对话"的能力。先创建 Terminal 再创建 LineReader 是因为：LineReader 依赖于 Terminal 的底层能力（如原始模式读取、光标位置查询），Terminal 是其构造参数。如果直接创建 LineReader 而没有 Terminal，JLine 无法知道终端的尺寸、无法进入原始模式，行编辑和补全都无法工作。

**Q2：JLine 的 DefaultParser 中 `setEscapeChars(null)` 是什么意思？为什么在 REPL 场景中必须设置？**

A：JLine 的 DefaultParser 默认将 `\` 视为转义字符，这意味着输入 `\n` 会被解释为换行符、`\t` 被解释为制表符、`\/` 被解释为普通斜杠（去掉转义作用）。在 REPL 场景中，斜杠命令（如 `/help`、`/tools`、`/debug`）是核心交互方式，用户可能无意中输入带有反斜杠的文本（如 Windows 路径 `C:\Users\name`），或未来斜杠命令需要参数中包含反斜杠。如果保留默认的转义行为，JLine 会在解析阶段"吃掉"反斜杠，导致 `/tools\list` 变成 `/toolslist` 而无法匹配，或者 `C:\Users\name` 变成 `C:Usersname` 被错误处理。`setEscapeChars(null)` 告诉 JLine "不要对任何字符做转义处理"，保持用户输入的原始模样，确保斜杠命令和路径参数不被意外篡改。这是 JLine REPL 配置中"最容易踩坑"的一行，也是 mewpaw-code 在早期版本中曾遇到并修复的一个 Bug。

**Q3：REPL 需要处理 Ctrl+C 和 Ctrl+D，两者的语义区别是什么？JLine 分别如何抛出异常？**

A：Ctrl+C 和 Ctrl+D 在终端语义上有本质区别。Ctrl+C 发送 SIGINT 信号（在 Unix 中），语义是"中断当前操作但不退出程序"——用户可能输入了一半内容想取消，或者命令执行时间太长想中断。JLine 将其捕获并抛出 `UserInterruptException`，REPL 的正确处理方式是：换行后重新显示提示符，等待下一次输入，而不是退出程序。Ctrl+D（在 Unix 中）发送 EOF（End of File）信号，语义是"不再有输入了，请退出"——用户想结束对话。JLine 将其捕获并抛出 `EndOfFileException`，REPL 的正确处理方式是：保存历史记录、关闭终端、退出主循环。两者的核心区别在于：Ctrl+C 是"暂停/取消"，Ctrl+D 是"结束/退出"。REPL 必须区分这两种语义，给用户符合直觉的体验——偶尔按错 Ctrl+C 不应该丢失整个会话，而 Ctrl+D 则应该被尊重为退出意愿。另需注意，Windows 的终端行为略有不同：Ctrl+Z + Enter 产生 EOF，而 Ctrl+C 的行为与 Unix 一致。

---

## 七、总结

| 技术点 | 本篇用法 | 通俗一句话 |
|--------|---------|-----------|
| REPL 概念 | Read-Eval-Print-Loop | 有状态的持续对话，不是一次性命令 |
| JLine 3 Terminal | `TerminalBuilder.builder().system(true).build()` | 封装的物理终端，提供原始模式 |
| JLine 3 LineReader | `LineReaderBuilder.builder()...build()` | 行编辑器，方向键/Ctrl 键/补全/历史 |
| Completer | `SlashCommandCompleter implements Completer` | Tab 键触发，自动补全斜杠命令 |
| History | `DefaultHistory` + 文件持久化 | 上下键翻历史，文件保存跨会话 |
| Parser | `setEscapeChars(null)` | 禁用反斜杠转义，保持原始输入 |
| 键绑定 | `KeyMap.EMACS` | Ctrl+A/E/K/U 等 Emacs 快捷键 |
| 终端检测 | `TuiState.hasConsole()` / `isPiped()` | 判断是否有物理终端、是否管道输入 |
| 回退模式 | `BufferedReader` 代替 JLine | 无终端/管道输入时也能工作 |
| 异常处理 | `UserInterruptException` / `EndOfFileException` | Ctrl+C 中断不退出，Ctrl+D 优雅退出 |
| Picocli | `@Command` + `@Option` + `mixinStandardHelpOptions` | 声明式参数解析，白送 --help |
| 双模式 | `--web` 参数切换 `WebApplicationType` | 同一个 jar，CLI 或 Web 按需切换 |

**核心收获一句话：** REPL 是 CLI Agent 的"交互底座"，JLine 3 提供了生产级的行编辑、补全、历史、终端检测能力；入门版和 mewpaw-code 之间只差"Agent 循环集成 + Lanterna 全屏 TUI + 生产级加固"，而本篇教你掌握的 JLine 3 四大组件（Terminal + LineReader + Completer + History）和回退/异常处理模式，正是理解那个真实项目交互层的地基。

---

## 参考资料

- JLine 3 官方文档 — https://github.com/jline/jline3
- JLine 3 TerminalBuilder — https://www.javadoc.io/doc/org.jline/jline/latest/org/jline/terminal/TerminalBuilder.html
- JLine 3 LineReaderBuilder — https://www.javadoc.io/doc/org.jline/jline/latest/org/jline/reader/LineReaderBuilder.html
- Picocli 官方文档 — https://picocli.info/
- Picocli Spring Boot Integration — https://picocli.info/#_spring_boot
- Lanterna 官方文档 — https://github.com/mabe02/lanterna
- Spring Boot WebApplicationType — https://docs.spring.io/spring-boot/docs/current/api/org/springframework/boot/WebApplicationType.html
- GNU Readline 库（Emacs 键绑定标准） — https://tiswww.case.edu/php/chet/readline/rltop.html
- mewpaw-code 项目源码 — https://github.com/1byteone/mewpaw-code