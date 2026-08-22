# 07 · STAR 亮点：mewpaw-code 技术深潜

> 3 个可深入展开的 STAR 项目亮点，覆盖 Agent 引擎、安全架构、Java 21 实践三个维度。每个亮点包含 Situation/Task/Action/Result 完整叙事，以及面试官可深挖的追问方向。
>
> **适用场景：** 简历亮点描述、技术分享、面试自我介绍、系统设计答辩

---

## 亮点一：ReAct Agent 循环 + 事件驱动架构

### Situation（背景）

在开发 mewpaw-code CLI 编码 Agent 时，我需要一个能够在编码场景中自主决策、调用工具、逐步逼近目标的 Agent 循环引擎。LangChain4j 提供了内置的 Agent 循环，但它在关键能力上存在缺口：没有事件驱动机制（无法实时展示 Agent 思考过程）、没有安全链集成（每次工具调用都需要经过 5 层安全检查）、没有输出截断和连续错误容错。

### Task（任务）

设计并实现一个自定义的 ReAct Agent 循环，满足以下要求：
- 支持 Thought → Action → Observation 完整循环
- 每轮迭代都经过 5 层安全链过滤
- 8 种事件类型覆盖 Agent 完整生命周期，支持流式输出
- 50 次迭代上限 + 3 次连续错误上限
- 成功输出截断 5000 chars / 错误输出截断 500 chars
- 错误回填为 ToolExecutionResultMessage，让 LLM 自行处理

### Action（行动）

1. **自定义 AgentLoop 类**：不依赖 LangChain4j 内置 Agent 循环，从头实现 ReAct 循环，完全掌控迭代控制、安全集成、事件发射

2. **AgentEvent sealed interface 事件系统**：使用 Java 21 Sealed Classes 限定 8 种事件类型（TurnStarted、StepUpdated、AssistantDelta、AssistantCompleted、ToolCallStarted、ToolOutputDelta、ToolCallCompleted、TurnCompleted、TurnFailed），编译器保证穷尽性检查

3. **安全链内嵌**：在每次工具调用前插入 SecurityFilterChain.check()，如果任一过滤器返回 deny，工具调用被阻断，错误信息回填给 LLM

4. **三重终止条件**：正常完成（无工具调用）、MAX_ITERATIONS=50 防止无限循环、MAX_CONSECUTIVE_ERRORS=3 防止错误失控

5. **输出截断**：工具执行结果超过 5000 chars 自动截断，防止 LLM 上下文窗口被撑爆

### Result（结果）

- 成功实现完全可控的 Agent 循环，在编码场景中单次任务平均 3-8 轮迭代即可完成
- 事件驱动架构让前端 / REPL 可以实时展示 Agent 思考过程，用户体验显著提升
- 安全链内嵌确保了每次工具调用都经过安全检查，没有安全盲区
- 连续错误容错机制在实际使用中避免了多次无效 LLM 调用

### 面试官深挖方向

| 追问 | 考察点 | 参考答案要点 |
|------|--------|-------------|
| 为什么不用 LangChain4j 内置循环？ | 框架 vs 自研的权衡 | 需要安全链内嵌 + 事件驱动 + 自定义截断，内置循环无法满足 |
| Sealed Classes 在这里的优势是什么？ | Java 21 新特性理解 | 穷尽性检查 + 领域边界声明 + 重构安全 |
| 50 次迭代和 3 次错误上限怎么来的？ | 经验值设计依据 | 50 次够完成复杂编码任务，3 次连续错误说明 LLM 不理解工具 |
| 事件系统如何实现流式输出？ | 事件驱动架构理解 | 每个事件携带 turnId，前端订阅事件流，实时渲染 |
| 工具执行结果截断后，LLM 如何感知？ | 上下文窗口管理 | 截断后附上原始长度，LLM 能感知到信息被截断 |

---

## 亮点二：5 层安全沙箱（责任链模式）

### Situation（背景）

LLM Agent 的核心优势是"自主执行"——它能调用 bash 命令、读写文件、执行搜索。但这也带来了巨大的安全风险：LLM 可能因为幻觉、prompt 注入或工具理解偏差而执行危险操作，如 `rm -rf /`、路径遍历读取敏感文件、网络外带数据。传统 Web 安全方案（防火墙、WAF）无法应对这种"执行者会犯错"的场景。

### Task（任务）

设计一个多层安全防护体系，满足以下要求：
- 覆盖工具注册、路径访问、命令执行、用户确认、审计日志五个维度
- 每层职责单一，可独立测试、独立扩展
- 任何一层拒绝立即阻断，confirm 挂起等待用户，allow 放行下一层
- 支持 LLM 场景特有的"无意识危险行为"防御

### Action（行动）

1. **责任链模式设计**：SecurityFilter 接口 + SecurityFilterChain 容器，5 层过滤器按顺序执行

2. **第 1 层 ToolFilter**：检查工具是否在 ToolRegistry 中注册，防御 LLM 幻觉调用不存在的工具

3. **第 2 层 PathGuardFilter**：路径规范化（normalize + 大小写统一 + ~ 替换），检查文件路径是否在工作目录内，防御路径遍历攻击

4. **第 3 层 CommandScannerFilter**：双层保护——危险模式完整匹配（`rm -rf /`、`mkfs.`、fork 炸弹）直接 deny，危险前缀前缀匹配（`sudo`、`su`）要求 confirm

5. **第 4 层 UserConfirmFilter**：Human-in-the-loop 机制，LLM 建议但人类拥有最终决策权，30 秒超时默认拒绝

6. **第 5 层 AuditLogFilter**：全量审计日志记录（工具名 + 参数 + 决策结果），只记录通过安全检查的操作

### Result（结果）

- 构建了完整的纵深防御体系，覆盖从工具注册到审计日志的全链路
- 责任链模式让每层过滤器职责单一，新增安全策略只需添加过滤器
- 实际运行中成功拦截了 LLM 幻觉产生的危险命令（如 `rm -rf /`）
- 危险模式列表 + 危险前缀列表的双层设计平衡了"绝对安全"和"用户体验"

### 面试官深挖方向

| 追问 | 考察点 | 参考答案要点 |
|------|--------|-------------|
| 为什么选择责任链模式而不是其他模式？ | 设计模式选型能力 | 解耦、灵活、顺序敏感，天然适合"逐层过滤"场景 |
| CommandScannerFilter 为什么分 deny 和 confirm 两层？ | 安全设计权衡 | 绝对危险操作必须拦截不给用户确认机会，潜在危险操作由人类决策 |
| 路径规范化解决了哪些安全问题？ | 安全攻防理解 | 路径遍历、符号链接、大小写差异、用户目录缩写 |
| 审计日志在链尾有什么优缺点？ | 架构设计权衡 | 优点：只记录通过操作，节省日志空间；缺点：拒绝操作无日志，需额外配置 |
| 这个安全设计能防御 prompt 注入吗？ | 安全边界理解 | 能防御"工具执行层面"的注入，不能防御"信息泄露层面"的注入 |

---

## 亮点三：Java 21 虚拟线程 + CLI 架构设计

### Situation（背景）

mewpaw-code 是一个 CLI 编码 Agent，采用 Spring Boot 3.x 作为 IoC 容器。但传统 Spring Boot Web 应用的设计模式（嵌入式 Tomcat + HTTP 请求/响应 + 无状态）在 CLI 场景中并不适用。同时，项目需要高并发执行多个 Bash 命令（LLM 可能同时请求多个工具调用），但 Bash 命令执行是 IO 等待密集型操作，平台线程池会很快被占满。

### Task（任务）

设计一个 CLI 架构，满足以下要求：
- 利用 Java 21 虚拟线程处理高并发 IO 等待
- 双模式启动（CLI 模式秒级启动，Web 模式可选）
- 三套交互库各司其职（Picocli 启动参数 + JLine REPL 交互 + Lanterna 全屏展示）
- 自动终端检测和优雅降级

### Action（行动）

1. **虚拟线程执行器**：BashTool 使用 `Executors.newVirtualThreadPerTaskExecutor()`，每个 Bash 命令提交为虚拟线程任务，通过 `Future.get(timeout, TimeUnit)` 带超时等待

2. **双模式启动**：`MewCodeAgentApplication.main()` 扫描 `--web` 参数，无 `--web` 时设置 `WebApplicationType.NONE`，启动时间从数秒缩短到 1-2 秒

3. **三套交互库组合**：Picocli 处理启动参数解析（`@Command`、`@Option`、`@Parameters`），JLine 3 提供 REPL 行编辑（LineReader、Completer、DefaultHistory），Lanterna 提供全屏 TUI 展示

4. **终端检测 + 自动降级**：`TuiState.hasConsole()` 检查 `System.console()`，无真实终端时回退到 BufferedReader 标准输入

5. **历史记录持久化**：ReplHistory 使用 DefaultHistory 将命令历史持久化到 `~/.mewcode_history`，最大 1000 条

### Result（结果）

- CLI 模式启动时间从 3-5 秒缩短到 1-2 秒，用户体验显著提升
- 虚拟线程在 Bash 命令执行场景中表现出色，IO 等待时不占用 OS 线程
- 一个 jar 包同时支持 CLI 模式和 Web 模式，核心引擎完全复用
- 自动降级机制确保在 IDE、管道、后台等无终端环境中也能正常工作
- 历史记录持久化支持跨会话检索，Alt+↑ 快速复用历史命令

### 面试官深挖方向

| 追问 | 考察点 | 参考答案要点 |
|------|--------|-------------|
| 虚拟线程为什么适合 Bash 命令执行？ | 虚拟线程原理理解 | IO 等待时自动让出载体线程，不浪费 OS 线程资源 |
| CLI 模式启动时间为什么能从 3-5s 降到 1-2s？ | Spring Boot 启动原理 | 不启动嵌入式 Tomcat，跳过 Web 容器的初始化 |
| 同时使用 JLine 和 Lanterna 不冲突吗？ | 技术选型判断力 | JLine 管"行"（输入），Lanterna 管"屏"（展示），互补而非冲突 |
| 终端检测的边界情况有哪些？ | 工程严谨性 | IDE 运行、管道输入、后台 nohup、SSH 无 PTY |
| 为什么不用 Spring Shell 而用 JLine？ | 框架选型权衡 | Spring Shell 更重，JLine 更底层灵活，本项目需要精细控制 REPL 行为 |

---

## 总结

| 亮点 | 核心技术 | 核心价值 | 最佳展示场景 |
|------|---------|---------|-------------|
| ReAct Agent + 事件驱动 | AgentLoop + Sealed Classes + 事件流 | 可控的自主性 | 系统设计面试、技术分享 |
| 5 层安全沙箱 | 责任链模式 + 纵深防御 | 安全可控的执行力 | 架构设计面试、安全专题 |
| 虚拟线程 + CLI 架构 | Virtual Threads + 双模式 + 三库组合 | 高效的 CLI 体验 | Java 技术面试、项目介绍 |

**核心叙事线索：** mewpaw-code 的架构设计始终围绕一个核心矛盾——**"给 LLM 多大的自主权"**。AgentLoop 给了 LLM 思考/行动的自由，但通过迭代上限和安全链确保可控；安全沙箱给了 LLM 执行能力，但通过 5 层过滤确保安全；CLI 架构给了用户流畅的交互体验，但通过自动降级确保在各类环境中都能工作。这三个亮点共同回答了同一个问题：**如何让 LLM Agent 既强大又安全**。

---

## 参考资料

- ReAct 论文：Yao et al., "ReAct: Synergizing Reasoning and Acting in Language Models", 2022
- 责任链模式：GoF Design Patterns, Chain of Responsibility
- Java 21 Virtual Threads (JEP 444): https://openjdk.org/jeps/444
- Java 21 Sealed Classes (JEP 409): https://openjdk.org/jeps/409
- JLine 3: https://github.com/jline/jline3
- Picocli: https://picocli.info/
- Lanterna: https://github.com/mabe02/lanterna
- 项目源码：https://github.com/1byteone/mewpaw-code