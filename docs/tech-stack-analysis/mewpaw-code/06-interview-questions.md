# 06 · 面试题库：mewpaw-code 技术栈深度考察

> 27 道面试题，覆盖 Java 21、ReAct Agent Loop、LangChain4j 工具调用、5 层安全沙箱、CLI/TUI 交互层五大模块。每题附答案和解析，帮助面试官快速评估候选人深度，也帮助候选人自检知识盲区。
>
> **题型分布：** 选择题 8 + 判断题 4 + 简答题 8 + 场景题 4 + 深挖题 3 = 27 题

---

## 一、选择题（8 题）

### Q1：Java 21 虚拟线程的核心优势是什么？

A. 提高 CPU 密集型计算的性能
B. 由 JVM 调度，创建成本极低，适合 IO 密集型任务
C. 替代平台线程，所有场景都应使用虚拟线程
D. 虚拟线程不需要载体线程

**答案：B**

**解析：** 虚拟线程 (JEP 444) 由 JVM 调度而非 OS 内核，创建成本为 KB 级（平台线程为 MB 级），在 `Future.get()` 阻塞时自动让出载体线程。它们特别适合 IO 密集型任务（如 Bash 命令执行等待子进程退出），但不适合 CPU 密集型计算（JVM 仍需映射到平台线程执行）。选项 A 错误——CPU 密集任务应该用平台线程；C 错误——虚拟线程不适合需要 `synchronized` 固定或 JNI 调用的场景；D 错误——虚拟线程必须挂载到载体线程（平台线程）上执行。

---

### Q2：ReAct Agent 循环中，LLM 何时终止循环？

A. 达到 MAX_ITERATIONS 时
B. 连续错误达到 MAX_CONSECUTIVE_ERRORS 时
C. LLM 返回的 AiMessage 没有 ToolExecutionRequests 时
D. 以上都是

**答案：D**

**解析：** AgentLoop 有三种终止条件：(1) LLM 不再需要调用工具（`!aiResponse.hasToolExecutionRequests()`）——正常终止；(2) 迭代次数达到 MAX_ITERATIONS=50——防止无限循环；(3) 连续错误达到 MAX_CONSECUTIVE_ERRORS=3——避免无效重试。三种条件覆盖正常完成、资源耗尽、错误失控三种场景。

---

### Q3：mewpaw-code 的 5 层安全链中，哪一层负责检测 `rm -rf /` 这种危险命令？

A. ToolFilter
B. PathGuardFilter
C. CommandScannerFilter
D. UserConfirmFilter

**答案：C**

**解析：** CommandScannerFilter 是第 3 层过滤器，专门负责扫描 bash 工具的命令参数。它采用双层保护：完全匹配"危险模式列表"（如 `rm -rf /`、`mkfs.`、fork 炸弹）直接 deny；前缀匹配"危险前缀列表"（如 `sudo`、`su`）返回 confirm 要求用户确认。ToolFilter 只检查工具是否注册，PathGuardFilter 只检查路径是否越权，UserConfirmFilter 处理用户确认交互。

---

### Q4：JLine 3 中，LineReader 和 Terminal 的关系是什么？

A. Terminal 是 LineReader 的子类
B. LineReader 是 Terminal 的包装，提供行编辑能力
C. 两者没有关系，各自独立工作
D. Terminal 依赖 LineReader 才能输出

**答案：B**

**解析：** Terminal 是终端抽象层（JLine 自动检测终端类型并创建相应实现），负责底层 I/O 和终端属性控制。LineReader 包装 Terminal，提供 Readline 风格的行编辑能力——光标移动、历史记录检索、Tab 补全、行内编辑键绑定。两者是"硬件抽象"和"软件层"的关系。

---

### Q5：以下哪个不是 Picocli 的注解？

A. `@Command`
B. `@Option`
C. `@Parameters`
D. `@RequestMapping`

**答案：D**

**解析：** `@RequestMapping` 是 Spring MVC 的注解，用于 HTTP 请求映射。Picocli 的核心注解包括 `@Command`（声明命令类）、`@Option`（声明命名参数，如 `--workdir`）、`@Parameters`（声明位置参数）。`@Command(mixinStandardHelpOptions = true)` 可自动添加 `--help` 和 `--version` 选项。

---

### Q6：SecurityResult 的三种状态中，confirm 状态的处理流程是什么？

A. 直接放行
B. 立即拒绝并记录日志
C. 挂起等待用户确认，确认后放行，拒绝则转为 deny
D. 跳过当前层，继续下一层

**答案：C**

**解析：** SecurityResult 定义了三种决策：allow（放行，继续下一层）、deny（拒绝，立即阻断，不再执行后续层）、confirm（需要用户确认）。confirm 的处理流程在 SecurityFilterChain 中：挂起等待用户输入 y/n，超时或拒绝转为 deny，确认后继续下一层。confirm 是"人类监督"机制的关键——LLM 可以建议，但人类拥有最终决策权。

---

### Q7：项目为什么使用 Records 而不是普通类来定义 SecurityResult？

A. Records 支持继承
B. Records 自动生成 equals/hashCode/toString，且字段默认不可变
C. Records 性能更好
D. Records 可以直接序列化为 JSON

**答案：B**

**解析：** Record (JEP 395) 编译期自动生成构造器、equals()、hashCode()、toString()，字段默认 final。SecurityResult 在 5 层安全链中高频传递，不可变性保证任何一层都不会被后续层篡改——这对安全审计至关重要。选项 A 错误——Records 不支持继承（extends 任何类，包括 Record）；C 不准确——Records 性能与普通类相当；D 不准确——Jackson 同时支持普通类和 Records 的序列化。

---

### Q8：以下哪个不是 AgentEvent 的事件类型？

A. TurnStarted
B. ToolCallStarted
C. AgentError
D. TurnCompleted

**答案：C**

**解析：** AgentEvent 是 sealed interface，限定 8 种实现：TurnStarted、StepUpdated、AssistantDelta、AssistantCompleted、ToolCallStarted、ToolOutputDelta、ToolCallCompleted、TurnCompleted、TurnFailed。不存在 "AgentError" 类型——失败场景使用 TurnFailed。Sealed Classes 限制实现类集合，编译器保证穷尽性检查，新增事件类型漏处理直接编译失败。

---

## 二、判断题（4 题）

### Q9：Spring Boot 的 CLI 模式（WebApplicationType.NONE）仍然会启动嵌入式 Tomcat。

**答案：错误**

**解析：** WebApplicationType.NONE 明确告诉 Spring Boot 不要启动任何 Web 容器。在该模式下，Spring Boot 只初始化 ApplicationContext、加载 Bean、执行 CommandLineRunner，不启动嵌入式 Tomcat。启动时间从数秒缩短到 1-2 秒。项目通过 `--web` 参数动态切换 NONE 和 SERVLET 模式，实现"一个 jar 两种用途"。

---

### Q10：在 5 层安全链中，AuditLogFilter 放在链尾意味着它只记录通过安全检查的操作。

**答案：正确**

**解析：** 责任链的顺序敏感：前面 4 层（ToolFilter → PathGuardFilter → CommandScannerFilter → UserConfirmFilter）如果任何一层返回 deny，链就不会继续执行到 AuditLogFilter。所以 AuditLogFilter 只记录"最终通过安全检查"的操作，避免记录被拒绝的操作（节省日志空间）。如果需求变更需要记录所有操作（包括拒绝的），应当将 AuditLogFilter 移到链首。

---

### Q11：CommandScannerFilter 的危险模式匹配使用前缀匹配，危险前缀使用完整匹配。

**答案：错误**

**解析：** 正好相反：危险模式列表（DANGEROUS_PATTERNS）使用 `contains()` 完整匹配，命中直接 deny；危险前缀列表（CONFIRM_PREFIXES）使用 `startsWith()` 前缀匹配，命中要求用户 confirm。原因：危险模式是"绝对危险操作"，必须完整匹配才拒绝（如 `rm -rf /` 需要精确匹配才拦截，避免误伤 `rm -rf target/` 这种合法操作）；危险前缀是"潜在危险操作"，前缀匹配更宽松（如任何以 `sudo` 开头的命令都要求确认）。

---

### Q12：mewpaw-code 的 MCP 客户端使用 HTTP 传输层与 MCP 服务器通信。

**答案：错误**

**解析：** MCP 客户端使用 StdioTransport（标准输入/输出）作为传输层，通过子进程的标准输入/输出进行 JSON-RPC 2.0 通信。MCP 规范支持多种传输层（stdio、SSE、WebSocket 等），本项目选择了 stdio 方式，因为 CLI Agent 天然适合启动子进程并通过管道通信。

---

## 三、简答题（8 题）

### Q13：简述 ReAct Agent 循环的核心流程，并说明与 Plan-and-Execute 的区别。

**答案：**

ReAct（Reason + Act）循环流程：
1. 构建 SystemMessage + UserMessage + 历史消息
2. 调用 LLM 获取回复（AiMessage）
3. 判断 LLM 是否请求工具调用：
   - 无工具调用 -> 终止循环，返回最终回复
   - 有工具调用 -> 执行安全链检查，执行工具，将结果回填到消息列表
4. 回到步骤 2，迭代计数 +1

与 Plan-and-Execute 的区别：
- **ReAct**：边想边做，观察中间结果后动态调整下一步，适合探索性任务（写代码、调试）
- **Plan-and-Execute**：先制定完整计划再逐步执行，路径确定但变更成本高，适合确定性任务（ETL、批处理）
- 编码任务是典型的探索性工作，所以本项目选择 ReAct

---

### Q14：解释 5 层安全链的设计原则，以及为什么使用责任链模式。

**答案：**

5 层安全链的设计原则是"**纵深防御（Defense in Depth）**"，从五个维度逐层递进：
1. **ToolFilter**：工具注册检查，防 LLM 幻觉调用不存在的工具
2. **PathGuardFilter**：路径规范化，防路径遍历攻击
3. **CommandScannerFilter**：危险命令扫描，防破坏性系统操作
4. **UserConfirmFilter**：用户确认，Human-in-the-loop 监督
5. **AuditLogFilter**：审计日志，全量操作留痕

使用责任链模式的原因：
- **解耦**：每层过滤器只关注自己的职责（单一职责原则）
- **灵活**：增删过滤器不影响其他层，新安全策略只需添加过滤器
- **顺序敏感**：由外到内（工具→路径→命令→用户→审计），任何一层 deny 立即终止

---

### Q15：项目为什么自建 ToolRegistry 体系，而不是使用 LangChain4j 的 @Tool 注解？

**答案：**

| 维度 | @Tool 注解 | 自建 ToolRegistry |
|------|-----------|-----------------|
| 安全性 | 无内置安全机制 | 每个工具调用都经过 5 层安全链 |
| 灵活性 | 编译期静态绑定 | 运行时动态注册/注销（ConcurrentHashMap） |
| 元数据 | name/description/params | 额外携带 dangerous / version / prompt 模板 |
| 扩展性 | 仅支持 Java 方法 | 支持 MCP 协议远程工具 |

核心原因：项目需要**安全链内嵌在工具调用链路中**，@Tool 注解无法做到；需要**运行时动态注册**（MCP 工具随时加入），@Tool 是编译期静态绑定。

---

### Q16：Java 21 虚拟线程在 BashTool 中如何应用？为什么适合这个场景？

**答案：**

BashTool 使用 `Executors.newVirtualThreadPerTaskExecutor()` 创建虚拟线程执行器，每个 Bash 命令提交为一个虚拟线程任务，通过 `Future.get(timeout, TimeUnit)` 带超时等待结果。

适合原因：
- Bash 命令执行是典型的 IO 等待场景——提交任务后在等待子进程退出，CPU 几乎不占用
- 虚拟线程在 `Future.get()` 阻塞时自动让出载体线程，不浪费 OS 线程资源
- 无需配置线程池大小（虚拟线程"用多少开多少"），避免池满排队导致的级联超时
- CLI 场景下 LLM 可能连续要求执行多个命令，虚拟线程天然支持高并发等待

---

### Q17：mewpaw-code 的双模式启动（CLI/Web）是如何实现的？

**答案：**

在 `MewCodeAgentApplication.main()` 中：
1. 扫描命令行参数是否包含 `--web`
2. 无 `--web` 参数：`app.setWebApplicationType(WebApplicationType.NONE)`，不启动嵌入式 Tomcat
3. 有 `--web` 参数：保持默认 `WebApplicationType.SERVLET`，启动 Web 容器

两种模式共享核心引擎：AgentLoop、ToolRegistry、SecurityFilterChain 等核心组件完全复用，差异只在交互层（CLI 用 JLine REPL，Web 用 HTTP 接口）和是否启动 Tomcat。

---

### Q18：Sealed Classes 在 AgentEvent 事件系统中起到了什么作用？

**答案：**

AgentEvent 声明为 `sealed interface`，限定 8 个实现类（TurnStarted、StepUpdated、AssistantDelta 等）。三个核心作用：

1. **穷尽性检查**：配合 `switch` 模式匹配，编译器强制检查所有事件类型被覆盖，新增类型漏处理直接编译失败
2. **领域边界声明**：8 种事件覆盖 Agent 完整生命周期，外部无法随意添加"非法事件"
3. **可维护性**：IDE 自动提示所有未覆盖分支，重构安全

这是 Java 21 模式匹配 + sealed classes 的经典组合用法，在需要"类型集合固定"的场景中非常有用。

---

### Q19：JLine 的 TerminalBuilder 如何实现跨平台终端兼容？

**答案：**

TerminalBuilder 自动检测操作系统和终端类型：
- **Windows**：使用 JLine 内置的 WindowsTerminal（通过 JNA 调用 kernel32.dll 的 Console API），支持原生控制台操作
- **Linux/macOS**：使用 UnixTerminal（通过 terminfo 数据库查询终端能力）
- 自动回退：如果无法检测到终端类型，创建哑终端（dumb terminal），提供最基本的 I/O 能力

`TerminalBuilder.builder().system(true)` 让 JLine 自动完成这一切，开发者无需关心底层终端类型。

---

### Q20：PathGuardFilter 为什么要做路径规范化？可能被什么攻击绕过？

**答案：**

路径规范化是为了防止路径遍历攻击（Path Traversal）。如果不规范化，攻击者（或 LLM）可以用以下方式绕过简单的字符串前缀检查：
- 相对路径：`../../etc/passwd` 实际上指向 `/etc/passwd`
- 符号链接：`/workdir/link_to_etc` 指向 `/etc`
- 大小写差异：`C:\Users\FFY` 和 `c:\users\ffy` 在 Windows 是同一目录
- 用户目录缩写：`~/secret` 和 `/Users/ffy/secret` 相同

PathGuardFilter 的处理流程：`normalize()` + `replace('\\','/')` + `toLowerCase()` + `~` 替换为 `user.home`，确保路径对比在"同一基准线"上进行。

---

## 四、场景题（4 题）

### Q21：场景：LLM 在 Agent 循环中生成了一个 bash 命令 `rm -rf /tmp/build-cache`，你认为安全链会如何处理？

**答案：**

逐层检查：
1. **ToolFilter**：bash 已注册 -> allow
2. **PathGuardFilter**：bash 非文件工具 -> allow
3. **CommandScannerFilter**：危险模式列表包含 `rm -rf /`，但 `rm -rf /tmp/build-cache` 不包含 `rm -rf /`（注意危险模式是 `rm -rf /` 带空格，而 `/tmp` 前没有空格，所以不匹配。但如果危险模式只写了 `rm -rf /` 且用 contains 匹配，`rm -rf /tmp/build-cache` 中的 `rm -rf /` 确实被包含 -> 实际上 `rm -rf /tmp/build-cache` 包含子串 `rm -rf /` 吗？ `rm -rf /tmp/build-cache` 中 `/` 后面是 `t` 不是空格，所以 `contains("rm -rf /")` 在这个字符串中匹配的是 `rm -rf /tmp` 中的 `rm -rf /` 部分？不，`contains("rm -rf /")` 会匹配 `rm -rf /tmp` 因为 `rm -rf /` 是 `rm -rf /tmp` 的子串。所以实际上 `rm -rf /tmp/build-cache` 会命中危险模式 `rm -rf /`？）
4. 实际上 `rm -rf /tmp/build-cache` 包含 `rm -rf /` 子串，所以会命中危险模式 -> deny

这说明危险模式列表可能过于严格——`rm -rf /tmp` 是合法的清理操作。安全设计在这里做了权衡：宁可误杀也不能放过 `rm -rf /`。实际项目中可能需要更精细的匹配（如 `rm -rf /$` 或 `rm -rf /\s`），或者允许用户通过配置调整危险模式列表。

---

### Q22：场景：你需要为 mewpaw-code 添加一个新的 MCP 工具，实现从外部 API 获取天气信息。请描述实现步骤。

**答案：**

1. **编写 MCP 服务器**：实现一个支持 JSON-RPC 2.0 的 stdio 服务器，通过 tools/list 声明天气工具，通过 tools/call 处理工具调用
2. **配置 MCP 服务器启动命令**：在项目配置中指定 MCP 服务器的启动命令（如 `node weather-mcp-server.js`）
3. **McpClient 连接**：项目启动时，McpClient 通过 StdioTransport 启动子进程，完成 initialize 握手
4. **工具注册**：McpClient 收到 tools/list 响应后，将天气工具动态注册到 ToolRegistry
5. **安全检查**：天气工具经过 5 层安全链（ToolFilter 检查注册状态，PathGuardFilter 跳过，CommandScannerFilter 跳过，UserConfirmFilter 跳过，AuditLogFilter 记录）
6. **Agent 调用**：AgentLoop 中 LLM 决定调用天气工具，经安全链检查后，通过 McpClient 发送 tools/call 请求

---

### Q23：场景：AgentLoop 中连续 3 次工具调用都失败了（例如 LLM 反复用错误的参数调用 bash 工具）。系统会如何处理？

**答案：**

AgentLoop 维护 `consecutiveErrors` 计数器：
1. 每次工具执行失败（安全链拒绝或执行异常），`consecutiveErrors++`
2. 工具执行成功时，`consecutiveErrors = 0`（归零）
3. 当 `consecutiveErrors >= MAX_CONSECUTIVE_ERRORS(3)` 时，循环强制终止

处理流程：
- 第 1 次失败：错误回填为 ToolExecutionResultMessage，LLM 可以调整行为
- 第 2 次失败：同上，计数器继续增加
- 第 3 次失败：计数器达到 3，AgentLoop 发出 `TurnFailed` 事件，返回错误消息 "Agent terminated: 3 consecutive errors exceeded"
- 终止后，用户需要重新发起请求

设计意图：连续 3 次错误说明 LLM 在当前状态下无法正确使用工具，继续下去只会浪费 token，不如终止让用户重新输入。

---

### Q24：场景：用户在 IntelliJ IDEA 中运行 mewpaw-code，没有真实终端。JLine 的 REPL 会如何行为？

**答案：**

1. `EnhancedRepl.init()` 调用 `TuiState.hasConsole()` 检查 `System.console()`
2. 在 IntelliJ IDEA 中，`System.console()` 返回 null（IDE 不提供原生控制台）
3. 日志输出："No real terminal detected, falling back to stdin mode"
4. JLine 的 LineReader 不会被初始化（`lineReader` 保持 null）
5. `EnhancedRepl.readLine()` 检测到 `lineReader == null`，回退到 `BufferedReader` 标准输入
6. 用户失去历史记录、命令补全、行编辑等 JLine 特性，但基本输入功能仍然可用

这种自动降级机制确保 Agent 在不同的运行环境（IDE、管道、后台、真实终端）中都能正常工作。

---

## 五、深挖题（3 题）

### Q25：深挖：mewpaw-code 的安全链设计本质上是"让 LLM 有执行能力，但所有风险操作都在人类的监督和授权范围内"。请分析这种设计哲学的适用边界和潜在问题。

**答案：**

**适用边界：**
- 安全链适用于"LLM 作为执行者"的场景——Agent 自主调用工具，安全链作为监督者
- 对于"LLM 仅作为建议者"的场景（如 ChatGPT 只生成文本，不执行命令），安全链是多余的
- 安全链的严格程度与应用风险成正比：代码生成 Agent 需要严格安全链，而简单的问答 Agent 不需要

**潜在问题：**
1. **误杀率**：危险模式匹配可能过于严格，`rm -rf /tmp` 被误杀为 `rm -rf /`。需要持续优化匹配规则，或允许用户自定义白名单
2. **用户确认疲劳**：高频的 confirm 提示会让用户习惯性按 y，失去 Human-in-the-loop 的意义。需要平衡安全等级和用户体验
3. **LLM 行为扭曲**：过于严格的安全链可能导致 LLM 学会"规避"——为了完成任务使用更曲折的方式绕过检查
4. **审计日志膨胀**：全量日志记录在大规模使用中可能产生海量日志，需要合理配置日志轮转策略
5. **安全链自身安全**：安全链的配置本身也需要保护，防止被恶意修改

**改进方向：**
- 引入"安全级别"概念：根据操作危险程度设置不同级别的安全策略
- 机器学习辅助：基于历史安全决策训练模型，自动判断风险等级
- 动态规则：根据项目上下文自动调整安全策略的严格程度

---

### Q26：深挖：项目选择自定义 AgentLoop 而非使用 LangChain4j 内置 Agent 循环，这在架构上意味着什么？什么情况下你会选择使用 LangChain4j 内置循环？

**答案：**

**选择自定义 AgentLoop 的架构含义：**
1. **完全控制权**：项目掌控每一次迭代的生命周期，可以自由插入安全链、事件推送、输出截断等自定义逻辑
2. **框架解耦**：核心引擎不依赖 LangChain4j 的 Agent 实现，未来更换 LLM 框架（如 Spring AI）时只需要替换 LlmProvider 层
3. **复杂度转移**：项目需要自己管理循环终止条件、错误处理、消息列表构建等，这部分代码原本由框架提供

**什么情况下选择 LangChain4j 内置循环：**
1. **快速原型**：不需要安全链、事件驱动等定制逻辑，快速验证 Agent 可行性
2. **标准 Agent 场景**：简单的问答 + 工具调用，不需要特殊的迭代控制
3. **团队规模小**：维护自定义 AgentLoop 需要持续投入，小团队可能更愿意使用框架现成功能
4. **框架深度绑定**：项目中大量使用 LangChain4j 的其他特性（如 RAG、向量存储），Agent 循环的定制需求不高

**权衡本质：** 这是"框架控制 vs 自主控制"的经典架构决策。自定义 AgentLoop 获得了灵活性，但代价是维护成本；使用框架内置循环降低了初始开发成本，但可能在未来被框架限制。

---

### Q27：深挖：mewpaw-code 的 CLI 架构设计（Spring Boot + Picocli + JLine + Lanterna）与传统 Spring Boot Web 应用相比，有哪些关键差异和架构启示？

**答案：**

**关键差异：**

| 维度 | 传统 Web 应用 | mewpaw-code CLI 应用 |
|------|-------------|-------------------|
| 入口 | DispatcherServlet → Controller | CommandLineRunner.run() |
| 交互 | HTTP 请求/响应 | JREPL 行输入/输出 |
| 容器 | 嵌入式 Tomcat 必须启动 | WebApplicationType.NONE，无容器 |
| 生命周期 | 持续运行等待请求 | 执行完毕即退出 |
| 并发模型 | 每个请求一个线程 | 虚拟线程 + 单线程 REPL |
| 状态管理 | 无状态（HTTP 无状态） | 有状态（Agent 会话） |

**架构启示：**

1. **Spring Boot 的本质是 IoC 容器，不是 Web 框架**：本项目把 Spring Boot 当"依赖注入容器"用，借用它的 Bean 管理、配置注入、生命周期控制能力，而非 Web 能力。这对很多非 Web 应用（批处理、CLI 工具、消息消费者）有参考价值。

2. **"合适的工具做合适的事"**：CLI 场景下，Spring Boot 提供 IoC 和配置管理，Picocli 处理参数解析，JLine 处理行交互，Lanterna 处理全屏展示——每个库只做自己擅长的事，通过组合实现完整解决方案。

3. **双模式架构的可插拔性**：通过 `--web` 参数在启动时切换模式，核心引擎完全复用，只有交互层不同。这种"共享核心 + 可插拔前端"的模式降低了维护成本，一个 jar 包可以部署为 CLI 工具或 Web 服务。

4. **CLI 应用的"降级"设计**：JLine 的终端检测 + BufferedReader 回退机制，确保即使在 IDE 或无终端环境中也能工作。这种"优雅降级"的设计思路适用于所有需要跨环境运行的应用。

---

## 六、答案速查表

| 题号 | 题型 | 答案 | 核心知识点 |
|------|------|------|-----------|
| Q1 | 选择 | B | 虚拟线程特性 |
| Q2 | 选择 | D | AgentLoop 终止条件 |
| Q3 | 选择 | C | 安全链分层职责 |
| Q4 | 选择 | B | JLine Terminal vs LineReader |
| Q5 | 选择 | D | Picocli 注解 |
| Q6 | 选择 | C | SecurityResult 三种状态 |
| Q7 | 选择 | B | Records 特性 |
| Q8 | 选择 | C | AgentEvent 事件类型 |
| Q9 | 判断 | 错误 | CLI 模式不启动 Tomcat |
| Q10 | 判断 | 正确 | 审计层在链尾的意义 |
| Q11 | 判断 | 错误 | 危险模式完整匹配 vs 危险前缀前缀匹配 |
| Q12 | 判断 | 错误 | MCP 使用 StdioTransport |
| Q13 | 简答 | — | ReAct 流程与 Plan-and-Execute 对比 |
| Q14 | 简答 | — | 责任链模式 + 纵深防御 |
| Q15 | 简答 | — | 自建 ToolRegistry vs @Tool |
| Q16 | 简答 | — | 虚拟线程在 IO 等待场景的应用 |
| Q17 | 简答 | — | 双模式启动实现 |
| Q18 | 简答 | — | Sealed Classes + 穷尽性检查 |
| Q19 | 简答 | — | JLine 跨平台终端兼容 |
| Q20 | 简答 | — | 路径规范化防御路径遍历 |
| Q21 | 场景 | — | 安全链逐层执行分析 |
| Q22 | 场景 | — | MCP 工具集成流程 |
| Q23 | 场景 | — | 连续错误处理机制 |
| Q24 | 场景 | — | 终端检测 + 自动降级 |
| Q25 | 深挖 | — | 安全设计哲学的边界与问题 |
| Q26 | 深挖 | — | 自定义 AgentLoop 的架构权衡 |
| Q27 | 深挖 | — | CLI 架构与传统 Web 架构对比 |

---

## 参考资料

- Java 21 虚拟线程 (JEP 444): https://openjdk.org/jeps/444
- ReAct 论文: Yao et al., "ReAct: Synergizing Reasoning and Acting in Language Models", 2022
- LangChain4j Tool Calling: https://docs.langchain4j.dev/tutorials/tools
- JLine 3: https://github.com/jline/jline3
- Picocli: https://picocli.info/
- Spring Boot CLI 模式: https://docs.spring.io/spring-boot/reference/using/command-line-runner.html
- MCP 规范: https://spec.modelcontextprotocol.io/
- 项目源码: https://github.com/1byteone/mewpaw-code