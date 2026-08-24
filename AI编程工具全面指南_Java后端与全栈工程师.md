# AI 编程工具全面实战指南：Java 后端工程师 & AI 全栈开发者

> **版本**: 2026-08 | **适用人群**: 有经验的 Java 后端工程师、AI 全栈开发者、技术负责人
> **核心目标**: 建立一套 **AI-Native Software Engineering（AI 原生软件工程）** 工作体系，让 AI Agent 在大型 Java 项目中稳定地完成 理解→分析→设计→编码→测试→调试→Review→重构→文档→Git→交付 全流程。

---

## 目录

- [第一部分 认知升级](#第一部分-认知升级)
  - [1.1 从"会用工具"到"设计 Agent 工作流"](#11-从会用工具到设计-agent-工作流)
  - [1.2 AI 开发生产力公式](#12-ai-开发生产力公式)
  - [1.3 五个工具的真实定位差异](#13-五个工具的真实定位差异)
- [第二部分 Claude Code：大型 Java 项目主力 Agent](#第二部分-claude-code大型-java-项目主力-agent)
  - [2.1 核心架构：Agentic Loop](#21-核心架构agentic-loop)
  - [2.2 CLAUDE.md：你的 Agent 知识库](#22-claudemd你的-agent-知识库)
  - [2.3 Hooks：让设置自我进化](#23-hooks让设置自我进化)
  - [2.4 Skills：按需加载专业能力](#24-skills按需加载专业能力)
  - [2.5 Subagents：拆分探索与编辑](#25-subagents拆分探索与编辑)
  - [2.6 Plugins：跨团队分发最佳实践](#26-plugins跨团队分发最佳实践)
  - [2.7 LSP 集成：符号级导航](#27-lsp-集成符号级导航)
  - [2.8 实战：Spring Boot 企业级项目全流程](#28-实战spring-boot-企业级项目全流程)
- [第三部分 Codex（CLI + ChatGPT App）：终端与桌面双形态 Agent](#第三部分-codexcli--chatgpt-app终端与桌面双形态-agent)
  - [3.1 Codex CLI：终端编码 Agent](#31-codex-cli终端编码-agent)
  - [3.2 ChatGPT App：Codex 合并后的桌面工作台](#32-chatgpt-appcodex-合并后的桌面工作台)
  - [3.3 Workspace Agents：企业级自动化](#33-workspace-agents企业级自动化)
  - [3.4 Automations：定时任务与后台 Agent](#34-automations定时任务与后台-agent)
  - [3.5 实战：Spring Boot 项目自动修复循环](#35-实战spring-boot-项目自动修复循环)
- [第四部分 DeepSeek Harness：Agent 工程实验场](#第四部分-deepseek-harnessagent-工程实验场)
  - [4.1 核心理念：Everything is a Plugin](#41-核心理念everything-is-a-plugin)
  - [4.2 四种运行模式详解](#42-四种运行模式详解)
  - [4.3 Cordis 内核：插件化 Agent 架构](#43-cordis-内核插件化-agent-架构)
  - [4.4 Code Mode 深度解析：模型生成编排程序](#44-code-mode-深度解析模型生成编排程序)
  - [4.5 Trajectory：可追溯的执行轨迹](#45-trajectory可追溯的执行轨迹)
- [第五部分 Hermes Agent：长期自主 Agent](#第五部分-hermes-agent长期自主-agent)
  - [5.1 核心架构：CLI + TUI + Desktop + Messaging](#51-核心架构cli--tui--desktop--messaging)
  - [5.2 持久记忆系统：MEMORY.md + USER.md](#52-持久记忆系统memorymd--usermd)
  - [5.3 自进化 Skills：从经验中学习](#53-自进化-skills从经验中学习)
  - [5.4 Session Search：跨会话搜索](#54-session-search跨会话搜索)
  - [5.5 Learning Journey：学习时间线](#55-learning-journey学习时间线)
  - [5.6 外部记忆提供商集成](#56-外部记忆提供商集成)
- [第六部分 Cursor：日常开发 IDE + Agent](#第六部分-cursor日常开发-ide--agent)
  - [6.1 四种模式：Agent / Ask / Manual / Custom](#61-四种模式agent--ask--manual--custom)
  - [6.2 Cursor Rules：.mdc 规则体系](#62-cursor-rulesmdc-规则体系)
  - [6.3 Cloud Agents：云端后台任务](#63-cloud-agents云端后台任务)
  - [6.4 YOLO 模式与权限控制](#64-yolo-模式与权限控制)
  - [6.5 MCP 集成：连接外部工具](#65-mcp-集成连接外部工具)
- [第七部分 Context Engineering：从 Prompt 到 Context](#第七部分-context-engineering从-prompt-到-context)
  - [7.1 为什么 Context 比 Prompt 重要](#71-为什么-context-比-prompt-重要)
  - [7.2 AGENTS.md：跨工具统一规范](#72-agentsmd跨工具统一规范)
  - [7.3 记忆体系设计](#73-记忆体系设计)
  - [7.4 四大 Context 策略](#74-四大-context-策略)
- [第八部分 MCP 与 Skills：能力扩展层](#第八部分-mcp-与-skills能力扩展层)
  - [8.1 MCP 协议：AI 工具的 USB-C](#81-mcp-协议ai-工具的-usb-c)
  - [8.2 用 Spring Boot 构建 MCP Server](#82-用-spring-boot-构建-mcp-server)
  - [8.3 Skills 设计模式](#83-skills-设计模式)
  - [8.4 Java 后端推荐的 20 个 Skills](#84-java-后端推荐的-20-个-skills)
- [第九部分 企业级落地案例与 ROI](#第九部分-企业级落地案例与-roi)
  - [9.1 大规模企业部署模式](#91-大规模企业部署模式)
  - [9.2 高价值回报案例集](#92-高价值回报案例集)
  - [9.3 ROI 计算框架](#93-roi-计算框架)
  - [9.4 领域落地：金融/医疗/电商/制造](#94-领域落地金融医疗电商制造)
- [第十部分 Java 后端全流程实战](#第十部分-java-后端全流程实战)
  - [10.1 工程 Prompt 模板](#101-工程-prompt-模板)
  - [10.2 Spring Boot 全流程 Agent 工作流](#102-spring-boot-全流程-agent-工作流)
  - [10.3 Redis 高并发场景：优惠券秒杀](#103-redis-高并发场景优惠券秒杀)
  - [10.4 MySQL 设计场景](#104-mysql-设计场景)
  - [10.5 RocketMQ 场景](#105-rocketmq-场景)
- [第十一部分 安全治理与风险控制](#第十一部分-安全治理与风险控制)
  - [11.1 操作风险分级表](#111-操作风险分级表)
  - [11.2 企业安全策略](#112-企业安全策略)
  - [11.3 代码审查与合规](#113-代码审查与合规)
- [第十二部分 个人能力建设与学习路线](#第十二部分-个人能力建设与学习路线)
  - [12.1 五级能力模型](#121-五级能力模型)
  - [12.2 推荐学习路线](#122-推荐学习路线)
  - [12.3 终极目标：AI Java 软件工厂](#123-终极目标ai-java-软件工厂)
- [附录](#附录)
  - [A. 完整配置模板合集](#a-完整配置模板合集)
  - [B. 官方资源入口](#b-官方资源入口)

---

# 第一部分 认知升级

## 1.1 从"会用工具"到"设计 Agent 工作流"

如果你的目标不是"会用几个 AI 编程工具"，而是要达到：

> **让 AI Agent 稳定理解大型 Java 项目 → 自主分析 → 设计方案 → 编码 → 测试 → 调试 → Review → 重构 → 文档化 → Git 提交 → 持续交付**

那么学习重点不是某个工具的快捷键，而是一套 **AI-Native Software Engineering 工作体系**。

### 传统程序员 vs AI Agent 程序员

**传统流程：**
```
需求 → 人分析 → 人设计 → 人编码 → 人测试 → 人调试 → 上线
```

**AI Agent 流程：**
```
              人类（目标/约束）
                   ↓
              Agent（理解/规划）
                   ↓
         ┌─────────┼─────────┐
       Search     Read      Web
         ↓         ↓         ↓
      Analyze   Context  Research
         └─────────┼─────────┘
                   ↓
                 Plan
                   ↓
         ┌─────────┴─────────┐
       Edit             Execute
         ↓                 ↓
       Code            Build/Test
         └─────────┬─────────┘
                   ↓
                Verify
                   ↓
              Review/Diff
                   ↓
              Git Commit
```

真正厉害的 AI 开发者不是"Prompt 写得特别长的人"，而是**能设计 Agent 工作流的人**。

## 1.2 AI 开发生产力公式

```
AI 开发生产力
=
模型能力
× 上下文质量
× 工具能力
× 任务拆解能力
× 项目规范
× 验证能力
× 自动化程度
× 人的判断力
```

很多人只关注**模型**（Claude 还是 GPT？），但对于大型项目，**Context + Tools + Rules + Skills + Verification** 往往比单纯换模型更重要。

## 1.3 五个工具的真实定位差异

不要把它们理解成"五个 Cursor"。它们在不同层级：

| 工具 | 核心定位 | 2026 年最新形态 | 最适合场景 |
|------|---------|----------------|-----------|
| **Cursor** | AI IDE | Agent Mode + Cloud Agents + .mdc Rules + YOLO 模式 | 日常开发、快速修改、前后端联调 |
| **Claude Code** | Terminal Agent | Skills + Hooks + Subagents + Plugins + LSP 集成 | 大型项目、复杂重构、架构分析 |
| **Codex** | Coding Agent | CLI + ChatGPT App 桌面版 + Workspace Agents + Automations | 自动修复、定时任务、企业自动化 |
| **DeepSeek Harness** | Agent Harness | Cordis 插件内核 + Code Mode + Standard/Minimal/Creator 模式 | Agent 工程研究、插件开发、Benchmark |
| **Hermes** | 自进化 Agent | MEMORY.md + 自进化 Skills + Session Search + 外部记忆 | 长期 Agent、自动化工作流、知识积累 |

**推荐分工矩阵：**

| 工作 | 首选 | 原因 |
|------|------|------|
| 日常 Java 开发 | Cursor | IDE 原生集成，快 |
| 代码学习/理解 | Cursor Ask | 不改代码，纯分析 |
| 大型架构重构 | Claude Code | 大上下文 + Subagent 并行 |
| 长时间自动任务 | Claude Code / Codex | 可运行数小时 |
| 自动修复测试失败 | Codex Full Auto | 自主循环直到通过 |
| Agent 架构实验 | DeepSeek Harness | 插件化思维，研究 Agent 系统 |
| 每日定时工作流 | Hermes | 持久记忆 + 自进化 Skills |
| CI 自动修复 | Codex / Claude Code | 后台运行，自动提 PR |
| MCP Server 开发 | Spring Boot + 任意工具 | 所有工具都支持 MCP |
| Skills 建设 | Claude / Hermes / DSH | 按需加载专业能力 |

---

# 第二部分 Claude Code：大型 Java 项目主力 Agent

Claude Code 的核心不是"聊天"，而是 **Agentic Loop**：收集上下文 → 执行操作 → 验证 → 循环。

截至 2026 年 8 月，Claude Code 已在数百万行级 monorepo、数十年历史的遗留系统、跨数十个仓库的分布式架构中投入使用。([Anthropic][1])

## 2.1 核心架构：Agentic Loop

Claude Code 导航代码库的方式与软件工程师相同：遍历文件系统、读取文件、使用 grep 精确查找、跨代码库跟踪引用。它在开发者本地运行，**不需要构建/维护/上传代码索引到服务器**。

与 RAG 驱动的 AI 编程工具不同（RAG 在大型项目中会因嵌入管道跟不上工程团队的提交速度而失败），Agentic Search 直接从活代码库工作，每次都是最新状态。

但这种方式有代价：**Claude 需要足够的起始上下文来知道去哪里找**。所以 CLAUDE.md 的质量直接决定了导航效果。

## 2.2 CLAUDE.md：你的 Agent 知识库

CLAUDE.md 是 Claude Code 最重要的扩展点。Claude 在每次会话开始时自动读取，**根目录文件提供全局视角，子目录文件提供本地约定**。

### 核心原则

- **保持精简且分层**：根文件只放指针和关键注意事项，其他内容会变成噪音
- **在子目录初始化而非仓库根**：Claude 在相关子代码库中效果最好
- **按子目录限定测试和 lint 命令**：避免运行全量测试浪费上下文
- **使用 .ignore 文件排除生成文件、构建产物和第三方代码**
- **运行 LSP 服务器让 Claude 按符号而非字符串搜索**

### 完整模板：Spring Boot 企业级项目 CLAUDE.md

```markdown
# CLAUDE.md - 项目级 AI 编程指南

## 项目概况
这是一个基于 Spring Boot 3 + Spring Cloud Alibaba 的微服务电商平台。
- Java 21, Maven 多模块
- Nacos (服务发现 + 配置中心)
- Spring Cloud Gateway (网关)
- MyBatis-Plus (数据访问)
- Redis (缓存 + 分布式锁)
- RocketMQ (异步消息)
- MySQL 8.0 (主数据存储)
- Elasticsearch (搜索)
- MinIO (文件存储)

## 架构约束
- Controller 不允许写业务逻辑，只做参数校验和响应封装
- Service 负责业务编排，一个 Service 方法不超过 30 行
- Repository/Mapper 只做数据访问
- DTO 不允许直接作为 Entity 返回给前端
- 跨服务调用使用 OpenFeign + Sentinel 熔断
- 所有分布式操作必须考虑幂等性

## 编码规范
- 使用 Java 21 特性（Record、Pattern Matching、Virtual Threads）
- 异常处理：业务异常用 BusinessException，系统异常不暴露给前端
- 日志：使用 @Slf4j，关键操作必须打印 traceId
- 配置：敏感信息放 Nacos 配置中心，禁止硬编码

## 构建与测试
```bash
# 编译
mvn clean compile -DskipTests

# 运行单模块测试
mvn test -pl backend/user-service

# 运行全量测试
mvn verify

# 启动单个服务
mvn spring-boot:run -pl backend/user-service
```

## 分层架构
```
backend/
├── gateway/          # API 网关
├── user-service/     # 用户服务
├── order-service/    # 订单服务
├── product-service/  # 商品服务
├── payment-service/  # 支付服务
├── common/           # 公共模块
│   ├── common-core/  # 工具类、常量
│   ├── common-redis/ # Redis 工具
│   ├── common-mq/    # MQ 工具
│   └── common-db/    # 数据库工具
└── api/              # Feign 接口定义
```

## Git 规范
- 禁止直接修改 main/master
- 功能开发使用 feature/xxx 分支
- 提交信息格式：`type(scope): description`
  - feat(order): 新增订单超时自动取消
  - fix(payment): 修复支付回调重复处理问题
  - refactor(user): 重构用户认证模块

## 任务等级
- **L0 问答**: 不修改代码
- **L1 只读分析**: Read/Search，不修改
- **L2 小修改**: 单文件 Edit
- **L3 完整功能**: Plan + Edit + Test
- **L4 复杂重构**: Architecture + Plan + Multi-file + Test + Review
- **L5 自主任务**: Full Auto 长时间执行
```

## 2.3 Hooks：让设置自我进化

Hooks 是在 Claude Code 生命周期关键节点运行的脚本。大多数团队只把 Hooks 当"阻止错误"的工具，但**更有价值的用途是持续改进**：

- **Stop Hook**：会话结束时反思发生了什么，提议 CLAUDE.md 更新（上下文还在）
- **Start Hook**：动态加载团队特定上下文，让每个开发者为自己的模块获得正确设置
- **自动检查**：lint、格式化等确定性规则用 Hook 执行比靠 Claude 记住更稳定

```json
// .claude/settings.json
{
  "hooks": {
    "PostToolUse": [
      {
        "matcher": "Write|Edit",
        "hooks": [
          {
            "type": "command",
            "command": "npx prettier --write $CLAUDE_FILE_PATH"
          }
        ]
      }
    ],
    "Stop": [
      {
        "hooks": [
          {
            "type": "command",
            "command": "echo '请检查是否需要更新 CLAUDE.md'"
          }
        ]
      }
    ]
  }
}
```

## 2.4 Skills：按需加载专业能力

Skills 解决的问题：大型代码库有数十种任务类型，不需要所有专业知识都出现在每个会话中。通过**渐进式披露**，只在任务相关时加载。

Skills 可以**限定路径**，只在代码库的相关部分激活。

### Skill 结构

```
.claude/skills/
├── java-code-review/
│   └── SKILL.md
├── spring-boot-engineer/
│   ├── SKILL.md
│   └── references/
│       ├── web.md
│       ├── data.md
│       ├── security.md
│       └── testing.md
├── mysql-optimization/
│   └── SKILL.md
└── api-contract-review/
    └── SKILL.md
```

### Skill 模板：spring-boot-engineer

```markdown
---
name: spring-boot-engineer
description: 用于构建、配置和调试企业级 Spring Boot 应用。
  当需要实现 REST 端点、优化 JPA 查询、配置 Spring Security
  或解决微服务中的问题时激活此 Skill。
metadata:
  version: "1.0.0"
  domain: java
  triggers: Spring Boot, Java, microservices, Spring Cloud, JPA, Hibernate
  role: engineer
  scope: implementation
  output-format: code
---

# Spring Boot Engineer

企业级 Spring Boot 开发专家，聚焦 Java 21 LTS。

## 核心工作流
1. **架构分析** - 检查项目结构、依赖、Spring 配置
2. **领域设计** - 按 DDD 和 Clean Architecture 创建模型
3. **实现** - 使用 Spring Boot 最佳实践构建服务
4. **数据层** - 优化 JPA 查询，实现 Repository
5. **安全** - 应用 Spring Security，外部化配置
6. **质量保证** - 运行 mvn verify 确保所有测试通过

## 代码规范
- DTO 与 Entity 严格分离
- Service 方法不超过 30 行
- 使用 @Transactional 注解管理事务
- 所有 API 必须有参数校验（@Valid）
- 异常统一通过 @RestControllerAdvice 处理

## 测试要求
- 每个 Service 必须有单元测试（Mockito）
- 每个 API 必须有集成测试（Testcontainers）
- 覆盖率不低于 85%
```

## 2.5 Subagents：拆分探索与编辑

Subagent 是一个独立的 Claude 实例，拥有自己的上下文窗口。接受任务 → 执行 → 只返回最终结果给父 Agent。

**关键模式**：只读 Subagent 映射子系统并将发现写入文件，然后主 Agent 在完整画面下编辑代码。

```
                  主 Agent
                     │
         ┌───────────┼───────────┐
         ↓           ↓           ↓
     Java Agent   DB Agent  Security Agent
         │           │           │
      Spring      MySQL       JWT/OAuth2
      Redis       Index       权限
      MQ          Tx          审计
         └───────────┼───────────┘
                     ↓
                  Reviewer
                     ↓
                   Human
```

### Subagent 定义示例

```markdown
# .claude/agents/java-architect.md

你是 Java 架构师 Agent。你的职责：
1. 分析 Spring Boot 项目结构
2. 评估领域模型设计
3. 检查服务间依赖关系
4. 识别潜在的架构问题
5. 输出改进建议

约束：
- 只读分析，不修改代码
- 输出结构化报告（Markdown）
- 每个发现必须包含：位置、问题、影响、建议
```

## 2.6 Plugins：跨团队分发最佳实践

Plugin 将 Skills、Hooks、MCP 配置打包成可安装的包。新工程师安装 Plugin 的第一天就拥有与老手相同的能力。

**插件分发层次：**
```
Plugin = Skills + Hooks + MCP Configs + Agents
  ↓
通过 Managed Marketplace 分发给团队
  ↓
每个开发者获得一致的上下文和能力
```

## 2.7 LSP 集成：符号级导航

没有 LSP 时，Claude 用 grep 搜索常见函数名，可能返回数千个匹配。有 LSP 后：

- 跟踪函数调用到定义
- 跨文件跟踪引用
- 区分不同语言中的同名函数
- 多语言代码库中，这是**最高价值的投资之一**

## 2.8 实战：Spring Boot 企业级项目全流程

### 阶段一：让 Agent 成为项目专家

```
分析这个 Spring Boot 项目的整体架构。

不要修改任何代码。

输出：
1. 模块结构和依赖关系
2. 服务间调用方式（OpenFeign/gRPC）
3. 核心业务流程（订单、支付、库存）
4. 数据库结构和索引
5. Redis 使用方式（缓存/锁/计数器）
6. MQ 使用方式（Topic/Tag/消费组）
7. 安全认证架构
8. 潜在性能问题
9. 潜在架构问题
10. 推荐的改造方向

先阅读项目，不要急于给结论。
```

### 阶段二：Plan → Execute → Verify

```
基于刚才的分析，实现订单超时自动取消功能。

# Context
- 订单服务使用 RocketMQ 延迟消息
- Redis 用于分布式锁
- MySQL 存储订单数据

# Requirements
1. 订单创建后 30 分钟未支付自动取消
2. 取消时释放库存
3. 退还优惠券
4. 幂等性保证

# Constraints
- 不修改现有数据库表结构
- 不引入新的中间件
- 遵循现有项目架构

# Process
1. 先输出实施计划
2. 列出修改文件
3. 等我确认后实现
4. 实现后执行对应模块测试

# Verification
完成后执行：
mvn test -pl backend/order-service

并检查：
- 编译通过
- 单元测试通过
- 幂等性
- 并发安全
- 异常处理
```

### 阶段三：自主修复循环

```
修复所有当前测试失败的问题。

要求：
1. 分析每个失败的测试
2. 定位根因
3. 修复代码（不是修改测试）
4. 运行测试验证
5. 如果还有失败，继续修复
6. 最后输出修复报告：根因、修改文件、风险

你可以长时间运行，直到所有测试通过。
```

---

# 第三部分 Codex（CLI + ChatGPT App）：终端与桌面双形态 Agent

截至 2026 年 7 月，Codex 已从独立 CLI 工具演进为 **ChatGPT 桌面应用的核心编码引擎**，同时保留 CLI 形态。OpenAI 官方将 Codex 定位为"Software Engineering Agent"，强调自主读取、修改、执行和验证代码的能力。([OpenAI][2])

## 3.1 Codex CLI：终端编码 Agent

Codex CLI 是一个基于 Rust 构建的轻量级开源编码 Agent，运行在终端中。它读取、修改并直接在你机器上执行代码。

### 三种自主程度模式

| 模式 | 行为 | 适用场景 |
|------|------|---------|
| **Suggest** | 只建议修改，不自动执行 | 保守场景、生产相关 |
| **Auto Edit** | 自动修改文件，执行前确认 | 日常开发 |
| **Full Auto** | 自主读取、修改、执行全部操作 | 长时间自动任务 |

### 安装与配置

```bash
# 安装
npm install -g @openai/codex

# 基础使用
codex "分析这个 Spring Boot 项目的启动失败原因并修复"

# Full Auto 模式（谨慎使用）
codex --full-auto "修复所有失败的测试"

# 指定模型
codex --model o3 "重构这个 Service 层"
```

### 权限控制（2026 最新）

Codex CLI 现在支持**逐终端权限配置**，可以自动批准安全操作，同时阻止 push、force-push、merge 和 branch delete：

```json
// codex.config.json
{
  "permissions": {
    "allow": ["bash(mvn *)", "bash(git diff*)", "bash(git status*)", "read", "edit"],
    "deny": ["bash(git push*)", "bash(rm -rf*)", "bash(git force-push*)"]
  }
}
```

## 3.2 ChatGPT App：Codex 合并后的桌面工作台

2026 年 7 月，Codex 应用正式合并到 ChatGPT 桌面应用。Codex 仍然是同一个强大的编码 Agent，但现在拥有更多能力：

- **Computer Use**：通过浏览器反馈
- **Memory**：跨会话记忆
- **Automations**：后台定时任务
- **Plugins**：角色专用插件（6 种新角色插件于 2026 年 6 月发布）
- **文件预览**：直接在界面中查看代码变更

### Preview 系统（2026 新特性）

Codex 现在可以生成 **2-4 种不同的实现方案**，让你在执行前选择最佳方案。这对架构决策非常有价值。

## 3.3 Workspace Agents：企业级自动化

2026 年 4 月，OpenAI 发布了 Workspace Agents —— Codex 驱动的共享 Agent，可以在团队间自动化复杂工作流：

- **创建**：定义 Agent 的目标、工具和权限
- **共享**：发布给团队成员使用
- **调度**：设置定时执行
- **监控**：查看执行历史和结果

```
Workspace Agent 用例：
├── Issue Triage Agent：自动分类 GitHub Issue
├── Code Review Agent：自动审查 PR
├── Test Generation Agent：为新代码生成测试
├── Documentation Agent：自动更新 API 文档
└── Deployment Agent：自动化部署流程
```

## 3.4 Automations：定时任务与后台 Agent

Codex 的 Automations 功能允许 Agent 在后台按计划运行：

```bash
# 通过 ChatGPT 创建定时任务
"每天早上 9 点检查所有服务的健康状态，
 如果有异常则创建 GitHub Issue 并通知 Slack"

# 通过 CLI 创建
codex automation create \
  --name "daily-health-check" \
  --schedule "0 9 * * *" \
  --prompt "检查所有微服务健康状态，异常则报警"
```

## 3.5 实战：Spring Boot 项目自动修复循环

```
# 场景：CI 测试失败，自动修复

codex --full-auto "
这个 Spring Boot 项目在 CI 中有 3 个测试失败。

要求：
1. 运行 mvn test 找到所有失败的测试
2. 分析每个失败的根因
3. 修复代码（不是修改测试）
4. 重新运行测试验证
5. 如果还有失败，继续修复循环
6. 最后创建一个 commit，包含所有修复

注意：不要修改测试的预期行为，只修复实现代码。
"
```

---

# 第四部分 DeepSeek Harness：Agent 工程实验场

DeepSeek Harness（DSH）不是又一个"AI 编程工具"。它的官方定位是 **Agent Harness** —— 一个让你研究和构建 Agent 系统的开源框架。核心理念：**Everything is a plugin**。([DeepSeek][4])

## 4.1 核心理念：Everything is a Plugin

```
Agent = Model + Harness
```

每个 Agent 能力都是可替换的插件：

```
Agent
├── Model Plugin（模型适配器）
├── Tool Plugin（工具注册）
├── Skill Plugin（技能）
├── Session Plugin（会话管理）
├── Sandbox Plugin（沙箱执行）
├── Storage Plugin（存储）
├── Loop Plugin（Agent 循环）
├── Scheduler Plugin（调度）
└── UI Plugin（界面）
```

开发者可以在配置中选择、替换或扩展任何能力，**无需修改 DSH 源代码**。

## 4.2 四种运行模式详解

### Standard Mode — 完整 Coding Agent

```
文件编辑 + Shell + 文件搜索 + Web 搜索
+ Skills + 规划 + 目标
+ Subagents + Workflow
```

适合：正常的 AI 编程任务。

### Code Mode — 模型生成编排程序（最值得研究）

这是 DSH 最独特的能力。传统 Agent 模式：

```
LLM → Tool Call → LLM → Tool Call → LLM → Tool Call
```

Code Mode：

```
LLM → 生成 TypeScript 程序 → 程序执行：
  Search → Read → Analyze → Edit → Test
```

**价值**：一次模型调用生成完整的多步工具编排程序，减少大量中间往返。

```typescript
// DSH Code Mode 示例：模型生成的编排程序
import { search, read, edit, exec } from '@deepseek-ai/dsh-sdk';

async function refactorService() {
  // 1. 搜索所有引用
  const refs = await search('UserService', { type: 'references' });

  // 2. 读取相关文件
  const files = await Promise.all(refs.map(r => read(r.path)));

  // 3. 分析依赖关系
  const deps = analyzeDependencies(files);

  // 4. 批量编辑
  for (const change of deps.refactoringPlan) {
    await edit(change.path, change.newContent);
  }

  // 5. 运行测试验证
  const result = await exec('mvn test -pl user-service');
  return { success: result.exitCode === 0, changes: deps.refactoringPlan };
}
```

### Minimal Mode — 极简 Benchmark 环境

只提供 bash + str_replace_editor，非常适合：
- 模型能力 Benchmark
- Agent 能力研究
- 最小环境下的能力测试

### Creator Mode — 自定义 Agent Preset

可以检查当前运行时、在内存中测试 Cordis 插件、组合成新模式。这已经进入 **Agent Engineering** 领域。

## 4.3 Cordis 内核：插件化 Agent 架构

Cordis 是 DSH 的插件内核，负责管理插件的挂载、卸载和依赖。它基于服务和事件机制让插件协作：

```
Cordis Kernel
├── 服务注册/发现
├── 依赖注入
├── 事件总线
├── 生命周期管理
└── 配置热更新
```

## 4.4 Code Mode 深度解析

Code Mode 的核心是通过 **Code Mode SDK** 将所有工具暴露为可编程接口：

| SDK 方法 | 功能 |
|---------|------|
| `search(query, options)` | 搜索代码库 |
| `read(path)` | 读取文件 |
| `edit(path, content)` | 编辑文件 |
| `exec(command)` | 执行命令 |
| `write(path, content)` | 写入文件 |
| `webSearch(query)` | Web 搜索 |

**为什么 Code Mode 重要？**

对于需要多步工具调用的任务（如"找到所有使用旧 API 的文件并批量替换"），传统模式需要 10+ 次 LLM 往返，而 Code Mode 只需要 1 次模型调用生成程序。

## 4.5 Trajectory：可追溯的执行轨迹

所有模型看到的内容都记录在**只追加的会话日志**中：
- 系统提示
- 推理过程
- 工具调用和结果
- Subagent 调度
- 每次上下文注入

在 Trajectory 视图中，可以按来源检查这些记录。**Resume、Fork、Search、Replay** 都操作同一事件流。

---

# 第五部分 Hermes Agent：长期自主 Agent

Hermes Agent 是 Nous Research 开发的**自进化 AI Agent**。它的独特之处：内置学习循环，能从经验中创建 Skills，在使用中改进 Skills，并跨会话保持记忆。([Hermes Docs][6])

## 5.1 核心架构：CLI + TUI + Desktop + Messaging

```
Hermes Agent
├── CLI（命令行界面）
├── TUI（终端用户界面）
├── Desktop（Electron 桌面应用）
├── Messaging（多平台消息）
├── Memory（持久记忆）
├── Skills（可复用技能）
├── Subagents（子 Agent）
├── Terminal（终端执行）
├── Browser（浏览器操作）
└── Scheduler（任务调度）
```

## 5.2 持久记忆系统：MEMORY.md + USER.md

Hermes 的记忆系统由两个文件组成：

| 文件 | 用途 | 字符限制 |
|------|------|---------|
| **MEMORY.md** | Agent 的个人笔记：环境事实、约定、学到的东西 | 2,200 字符（~800 tokens） |
| **USER.md** | 用户画像：偏好、沟通风格、期望 | 1,375 字符（~500 tokens） |

存储在 `~/.hermes/memories/`，在每次会话开始时注入系统提示。

### 记忆条目示例

```
# 好的条目：信息密度高
用户运行 macOS 14，使用 Homebrew，有 Docker Desktop 和 Podman。
Shell: zsh with oh-my-zsh。编辑器: VS Code with Vim keybindings。

# 好的条目：具体可执行
项目 ~/code/api 使用 Go 1.22，sqlc 做 DB 查询，chi 路由。
测试命令: 'make test'。CI: GitHub Actions。

# 好的条目：带上下文的经验
staging 服务器 (10.0.1.50) 需要 SSH 端口 2222，不是 22。
密钥在 ~/.ssh/staging_ed25519。

# 差的条目：太模糊
用户有一个项目。

# 差的条目：太冗长
2026年1月5日，用户让我查看位于 ~/code/api 的项目，
我发现了它使用 Go 1.22...
```

### 记忆管理

```bash
# 查看当前记忆
/memory list

# 添加记忆
/memory add "项目使用 Spring Boot 3 + MyBatis-Plus"

# 替换记忆
/memory replace "dark mode" "用户在 VS Code 中偏好浅色模式"

# 删除记忆
/memory remove "过时的约定"

# 审批待定的记忆写入
/memory pending
/memory approve <id>
```

## 5.3 自进化 Skills：从经验中学习

这是 Hermes 最独特的能力。当 Agent 完成一个涉及 5+ 工具调用的复杂任务后，它会**自动创建可复用的 Skill**：

```
Agent 完成复杂任务
     ↓
分析任务模式
     ↓
提取可复用步骤
     ↓
生成 SKILL.md
     ↓
下次遇到类似任务时自动加载
```

### 8 种自进化 Skills 类型

1. **Error Recovery** - 从错误中学习恢复策略
2. **Workflow Optimization** - 优化重复工作流
3. **Tool Usage** - 改进工具使用方式
4. **Code Patterns** - 提取代码模式
5. **Debug Strategies** - 调试策略
6. **Research Methods** - 研究方法
7. **Communication** - 沟通风格
8. **Domain Knowledge** - 领域知识

### Skill 写入控制

```yaml
# ~/.hermes/config.yaml
skills:
  write_approval: false  # false = 自由写入 | true = 需要审批
```

当 `write_approval: true` 时，所有 Skill 写入都会被暂存等待审批：

```bash
/skills pending    # 列出暂存的 Skill 写入
/skills diff <id>  # 查看完整差异
/skills approve <id>  # 批准
/skills reject <id>   # 拒绝
```

## 5.4 Session Search：跨会话搜索

除了 MEMORY.md 和 USER.md，Agent 可以搜索过去的对话：

```bash
# 浏览过去的会话
hermes sessions list

# 搜索特定内容
hermes sessions search "订单超时取消的实现"
```

所有 CLI 和消息平台的会话都存储在 SQLite（`~/.hermes/state.db`）中，支持 FTS5 全文搜索。

| 特性 | 持久记忆 | Session Search |
|------|---------|---------------|
| 容量 | ~1,300 tokens | 无限 |
| 速度 | 即时（在系统提示中） | ~20ms FTS5 查询 |
| 成本 | 每次提示都有 token 成本 | 免费 |
| 用途 | 关键事实始终可用 | "上周我们讨论了 X？" |

## 5.5 Learning Journey：学习时间线

`/journey` 命令展示 Hermes 学习的一切——保存的 Skills 和记忆条目按时间排列：

```bash
# 查看学习时间线
hermes journey

# 动画回放
hermes journey --play

# 列出所有节点
hermes journey list

# 删除节点
hermes journey delete <node-id>

# 编辑节点
hermes journey edit <node-id>
```

## 5.6 外部记忆提供商集成

Hermes 内置 8 个外部记忆提供商插件：Honcho、OpenViking、Mem0、Hindsight、Holographic、RetainDB、ByteRover、Supermemory。

外部提供商**与内置记忆并行运行**，添加知识图谱、语义搜索、自动事实提取等能力：

```bash
hermes memory setup   # 选择提供商并配置
hermes memory status  # 检查活跃状态
```

---

# 第六部分 Cursor：日常开发 IDE + Agent

Cursor 最大优势是 **IDE + Agent + Codebase Search + Terminal + Rules + MCP** 的原生集成。截至 2026 年，Cursor 已从"代码补全工具"演进为具备 Agent Mode、Cloud Agents、MCP 集成的完整 AI IDE。([Cursor Docs][3])

## 6.1 四种模式：Agent / Ask / Manual / Custom

| 模式 | 用途 | 示例 |
|------|------|------|
| **Ask** | 理解、分析、学习、规划（不改代码） | "为什么这个 Redis 分布式锁会失效？" |
| **Agent** | 实现、重构、调试、测试（自主执行） | "修复订单超卖问题。自行分析代码、修改并测试。" |
| **Manual** | 精准修改（只改指定位置） | "只修改这个方法，不要修改其他文件。" |
| **Custom** | 自定义工作流 | 特定团队流程 |

**关键区分**：
- 找到 Cursor 纠正同一问题两次 → 把纠正写入 Rules
- Agent Mode 用于"准备行动"（代码生成/编辑）
- Ask Mode 用于"理解"（不修改代码）

## 6.2 Cursor Rules：.mdc 规则体系

Cursor Rules 使用 `.mdc` 文件（Markdown with Config），支持 YAML frontmatter：

```markdown
---
description: Spring Boot 编码规范
globs: **/*.java
alwaysApply: false
---

# Spring Boot 编码规范

## 分层约束
- Controller 只做参数校验和响应封装
- Service 负责业务编排
- 不允许在 Controller 中写业务逻辑

## 命名规范
- Entity: PascalCase，如 UserOrder
- DTO: xxxDTO，如 UserCreateDTO
- Service: xxxService，如 OrderService
- Controller: xxxController，如 OrderController

## 异常处理
- 业务异常: BusinessException
- 统一异常处理: @RestControllerAdvice
- 不允许 catch Exception 后不处理
```

### 规则激活模式

| 模式 | 说明 |
|------|------|
| **Always** | 始终应用 |
| **Auto Attached** | 匹配 glob 模式时自动附加 |
| **Agent Requested** | Agent 按需请求 |
| **Manual** | 手动激活 |

### AGENTS.md 与 Cursor Rules 统一

2026 年的最佳实践：**AGENTS.md 作为单一事实来源**，各工具的规则文件只做薄包装：

```
AGENTS.md（核心规范）
  ├── .cursor/rules/java.mdc（导入 AGENTS.md + Cursor 特定配置）
  ├── CLAUDE.md（导入 AGENTS.md + Claude Code 特定配置）
  └── .codex/rules.md（导入 AGENTS.md + Codex 特定配置）
```

## 6.3 Cloud Agents：云端后台任务

Cursor 的 Cloud Agents 可以在云端运行长时间任务：

- 不占用本地资源
- 支持后台执行
- 完成后通知

## 6.4 YOLO 模式与权限控制

Cursor 提供多级权限控制：

| 级别 | 行为 |
|------|------|
| **Normal** | 每次操作都确认 |
| **Auto-review** | 自动执行，但保留审查能力 |
| **YOLO** | 全自动（谨慎使用） |

推荐策略：
- 读操作（grep、cat、git diff）：Auto
- 写操作（编辑代码）：Auto-review
- 高危操作（git push、删除）：Confirm

## 6.5 MCP 集成：连接外部工具

Cursor Agent 通过 MCP 连接外部服务：

```json
// .cursor/mcp.json
{
  "mcpServers": {
    "github": {
      "command": "npx",
      "args": ["-y", "@modelcontextprotocol/server-github"],
      "env": { "GITHUB_TOKEN": "${GITHUB_TOKEN}" }
    },
    "postgres": {
      "command": "npx",
      "args": ["-y", "@modelcontextprotocol/server-postgres"],
      "env": { "DATABASE_URL": "${DATABASE_URL}" }
    },
    "redis": {
      "command": "npx",
      "args": ["-y", "@modelcontextprotocol/server-redis"],
      "env": { "REDIS_URL": "${REDIS_URL}" }
    }
  }
}
```

---

# 第七部分 Context Engineering：从 Prompt 到 Context

2026 年，AI 工程领域最重要的概念转变是 **从 Prompt Engineering 到 Context Engineering**。([Anthropic][5])

## 7.1 为什么 Context 比 Prompt 重要

以前：写更好的 Prompt
现在：设计更好的 Context

一个 Agent 的上下文应该包括：

```
Context
=
项目规范（CLAUDE.md / AGENTS.md）
+ 架构文档
+ 当前任务
+ 相关代码
+ 历史决策（ADR）
+ 测试覆盖
+ 工具能力（MCP）
+ Skills
+ Git 状态
+ CI/CD 状态
+ 监控数据
```

## 7.2 AGENTS.md：跨工具统一规范

AGENTS.md 是 2026 年出现的**跨工具标准规范文件**，所有 AI 编程工具都能读取：

```markdown
# AGENTS.md - 跨工具 AI 编程规范

## 项目概述
电商平台后端，Spring Boot 3 微服务架构。

## 架构原则
- 单一职责
- 关注点分离
- 依赖倒置
- 领域驱动设计

## 编码标准
- Java 21 LTS
- 不使用 Lombok
- 所有公共 API 必须有 Javadoc
- 异常处理必须有日志记录

## 测试标准
- 单元测试覆盖率 ≥ 85%
- 所有 API 必须有集成测试
- 使用 Testcontainers 做集成测试
- 禁止 mock 数据库连接

## 安全标准
- 禁止硬编码密钥/密码
- 所有输入必须校验
- SQL 查询必须使用参数化
- 敏感数据必须加密存储

## Git 标准
- 分支策略: Git Flow
- 提交信息: Conventional Commits
- PR 必须通过 CI + Code Review
```

## 7.3 记忆体系设计

### 三级记忆架构

```
Session Memory（会话内）
    ↓ 转存
Short-term Memory（跨会话，Hermes MEMORY.md / Claude Auto Memory）
    ↓ 精炼
Long-term Memory（永久，文档/AGENTS.md/知识库）
```

### Claude Code 的双记忆系统

| 机制 | 来源 | 用途 |
|------|------|------|
| **CLAUDE.md** | 人工编写 | 项目规范、架构约束 |
| **Auto Memory** | Claude 自动记录 | 发现的模式、纠正、经验 |

## 7.4 四大 Context 策略

根据 Anthropic 和业界实践，Context Engineering 有四大策略：

| 策略 | 说明 | 示例 |
|------|------|------|
| **Write** | 写入上下文 | CLAUDE.md、AGENTS.md、Memory |
| **Select** | 选择相关上下文 | Skills 按需加载、Subagent 结果 |
| **Compress** | 压缩上下文 | 代码摘要、历史压缩 |
| **Isolate** | 隔离上下文 | Subagent 独立上下文窗口 |

---

# 第八部分 MCP 与 Skills：能力扩展层

## 8.1 MCP 协议：AI 工具的 USB-C

MCP（Model Context Protocol）是一个开放协议，标准化了 Agent 如何发现和调用外部工具：

```
LLM
 ↓
Agent
 ↓
MCP（标准化接口）
 ↓
External Tools
  ├── GitHub MCP（Issues, PRs, Actions）
  ├── PostgreSQL MCP（查询、Schema）
  ├── Redis MCP（缓存操作）
  ├── Docker MCP（容器管理）
  ├── Jira MCP（任务管理）
  └── 自定义 MCP Server
```

## 8.2 用 Spring Boot 构建 MCP Server

Spring AI 提供了 MCP Server Boot Starter，支持注解式开发：

```java
// 用 Spring Boot 构建自定义 MCP Server
@SpringBootApplication
public class OrderMcpServer {

    @Tool(description = "查询订单状态")
    public OrderDTO getOrderStatus(@Param("orderId") String orderId) {
        return orderService.getOrderStatus(orderId);
    }

    @Tool(description = "取消订单")
    public CancelResult cancelOrder(
        @Param("orderId") String orderId,
        @Param("reason") String reason
    ) {
        return orderService.cancelOrder(orderId, reason);
    }
}
```

```xml
<!-- pom.xml -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-mcp-server-spring-boot-starter</artifactId>
</dependency>
```

构建完成后，任何支持 MCP 的 AI 工具（Claude Code、Cursor、Codex）都可以直接调用这些工具。

## 8.3 Skills 设计模式

### Skill 不是简单 Prompt

```
Skill
├── SKILL.md          # 指令文档
├── checklist.md      # 检查清单
├── examples/         # 示例
├── scripts/          # 辅助脚本
└── templates/        # 代码模板
```

### Java Code Review Skill 示例

```markdown
---
name: java-code-review
description: Java 代码审查，检查常见问题
metadata:
  triggers: code review, PR review, Java review
---

# Java Code Review Skill

## 检查清单

### 正确性
□ 无 NPE 风险（Optional 使用）
□ 无并发问题（线程安全）
□ 事务边界正确（@Transactional）
□ 异常处理完整

### 性能
□ 无 N+1 查询
□ 索引合理
□ Redis 缓存策略正确
□ 无内存泄漏

### 安全
□ 无 SQL 注入
□ 无 XSS 漏洞
□ 敏感信息未硬编码
□ 权限校验完整

### 可维护性
□ 命名清晰
□ 注释适当
□ 方法长度合理（≤30行）
□ 圈复杂度合理
```

## 8.4 Java 后端推荐的 20 个 Skills

```text
01 java-code-review         # Java 代码审查
02 springboot-development   # Spring Boot 开发
03 springcloud-development  # Spring Cloud 微服务
04 api-design               # API 设计
05 mysql-design             # MySQL 设计
06 mysql-performance        # MySQL 性能优化
07 redis-design             # Redis 设计
08 rocketmq-design          # RocketMQ 设计
09 elasticsearch-design     # ES 设计
10 distributed-system       # 分布式系统设计

11 concurrency-review       # 并发审查
12 security-review          # 安全审查
13 performance-review       # 性能审查
14 docker-deployment        # Docker 部署
15 nginx-deployment         # Nginx 部署

16 frontend-development     # 前端开发
17 ui-design                # UI 设计
18 ai-agent-development     # AI Agent 开发
19 rag-development          # RAG 开发
20 production-readiness     # 生产就绪审查
```

---

# 第九部分 企业级落地案例与 ROI

## 9.1 大规模企业部署模式

Anthropic 官方观察到的成功部署模式（Claude Code at Scale）：([Anthropic][1])

### 模式一：让代码库可导航

- **CLAUDE.md 分层且精简**：根文件只放指针，子目录放本地约定
- **在子目录初始化而非仓库根**：Claude 自动向上遍历加载所有 CLAUDE.md
- **按子目录限定测试命令**：避免全量测试超时
- **用 .ignore 排除噪音**：生成文件、构建产物、第三方代码
- **运行 LSP 服务器**：按符号而非字符串搜索

### 模式二：主动维护配置

随着模型进化，为当前模型写的指令可能对下一个模型产生反作用。建议每 3-6 个月做一次配置审查。

### 模式三：指定专人管理

成功的部署都有一个**Agent Manager**角色：
- 一个人或小团队负责 Claude Code 配置
- 拥有设置、权限策略、Plugin 市场、CLAUDE.md 约定的决策权
- 负责保持配置最新

## 9.2 高价值回报案例集

### 案例 1：Stripe — "Minions" 系统

Stripe 构建了名为"Minions"的 AI 编码 Agent 系统：

- **产出**：每周生成 1,300+ PR
- **规模**：全公司产品经理参与"AI Staycation"集中培训
- **采用率**：内部工具上线后，33% 员工在数天内采用
- **效果**：Intercom 类似实践报告 20% 生产力提升

### 案例 2：大型零售组织 — Claude Code 插件

Anthropic 合作案例：
- 构建了连接 Claude 到内部分析平台的 Skill
- 业务分析师无需离开工作流即可拉取性能数据
- 作为 Plugin 在广泛推广前分发给业务团队

### 案例 3：企业软件公司 — LSP 集成

一家企业软件公司在 Claude Code 全面推广前：
- 部署了 LSP 集成（C/C++ 代码库）
- 使 Claude Code 在大型代码库中的导航可靠性大幅提升
- 特别是多语言代码库，LSP 是最高价值的投资

### 案例 4：Anthropic 内部

Anthropic 工程师和研究人员使用 Claude 最频繁的场景：([Anthropic][7])
- 修复代码错误
- 学习代码库
- 代码重构
- 文档生成

### 案例 5：Meta 前员工观察

> "当我在 Meta 推进生产力计划时，2% 的提升需要数百人花一年时间。Claude Code 在公司规模上交付了 150% 的生产力提升。"

### 案例 6：Duolingo — 企业级 Copilot 部署

GitHub 的受控研究显示：
- 常规编码任务完成速度提升 55%
- Duolingo 等企业报告显著的开发者生产力改善

## 9.3 ROI 计算框架

### 行业基准数据

| 指标 | 数据 | 来源 |
|------|------|------|
| 任务完成速度提升 | 26-55% | GitHub Copilot 研究 |
| 每周代码提交量增长 | 13.5% | GitHub 研究 |
| 企业 AI 编码 3 年 ROI | 376% | Forrester GitHub Copilot 分析 |
| Claude Code 单任务加速 | ~80% | Anthropic 研究 |
| AI 编码工具市场年增长率 | 24-27% | 2025-2030 市场报告 |

### ROI 计算公式

```
年度 ROI = 
  (节省的开发时间 × 开发者平均时薪 × 年工作小时数)
  + (减少的 Bug 修复成本)
  + (更快的上市时间价值)
  - (工具订阅成本)
  - (培训和配置成本)
  - (Token/计算成本)
```

### 示例计算

```
假设：
- 10 人 Java 团队
- 平均时薪 ¥300
- AI 工具带来 30% 效率提升
- 每人每周 40 小时开发时间

年度节省 = 10人 × 40h × 52周 × 30% × ¥300
        = ¥1,872,000

年度工具成本 = 10人 × ¥500/月 × 12月 = ¥60,000

净 ROI = ¥1,812,000（约 30 倍回报）
```

## 9.4 领域落地：金融/医疗/电商/制造

### 金融行业

```
AI Agent 在金融 Java 系统中的应用：
├── 合规检查 Agent：自动审查代码是否符合 PCI DSS
├── 风控规则引擎：AI 辅助设计和验证风控规则
├── 交易系统 Review：检查并发安全、幂等性、事务一致性
└── 审计日志：自动生成合规报告
```

**注意事项**：
- 生产数据库操作必须人工批准
- 敏感数据不能发送到外部 AI 服务
- 使用企业版 AI 工具（数据不用于训练）

### 医疗行业

```
AI Agent 在医疗 Java 系统中的应用：
├── HL7/FHIR 接口开发：AI 辅助实现医疗数据标准
├── 数据脱敏：自动识别和脱敏 PII/PHI 数据
├── 审计追踪：HIPAA 合规性检查
└── 系统集成：EMR/EHR 系统对接
```

### 电商行业

```
AI Agent 在电商 Java 系统中的应用：
├── 秒杀系统设计：Redis + MQ + 分布式锁
├── 订单状态机：复杂状态流转设计与验证
├── 库存系统：分布式库存扣减、超卖防护
├── 推荐系统：搜索和推荐算法集成
└── 性能优化：高并发场景下的系统调优
```

### 制造业

```
AI Agent 在制造业 Java 系统中的应用：
├── MES 系统集成：制造执行系统接口开发
├── IoT 数据处理：设备数据采集与分析
├── 工单系统：复杂的工单流转和排产逻辑
├── 质量追溯：产品全生命周期追溯
└── ERP 对接：SAP/Oracle 等 ERP 系统集成
```

---

# 第十部分 Java 后端全流程实战

## 10.1 工程 Prompt 模板

### ❌ 低效 Prompt

```
帮我写一个登录功能。
```

### ✅ 工程级 Prompt

```markdown
# Goal
实现用户登录功能。

# Context
项目：Spring Boot 3 + MyBatis-Plus + Redis + JWT
相关模块：user-service
数据库：MySQL 8.0，user 表已有 phone, password, status 字段

# Requirements
1. 手机号 + 密码登录
2. JWT Token（Access + Refresh）
3. Redis 存储 Session 信息
4. 登录失败 5 次锁定 30 分钟
5. Token 刷新机制
6. 注销（清除 Redis）

# Constraints
1. 不修改现有数据库表结构
2. 不引入新的依赖
3. 遵循现有项目架构
4. 不修改无关代码

# Process
1. 先分析项目现有认证体系
2. 找到相关的 Entity、DTO、Service
3. 输出实施计划
4. 等计划确认后实现

# Verification
完成后执行：mvn test -pl backend/user-service

并检查：
- 编译通过
- 单元测试通过
- Redis 连接正确
- JWT 签发和验证
- 异常处理完整
- 并发安全（登录锁定）

# Deliverables
1. 修改文件列表
2. 核心设计说明
3. 测试结果
4. 风险点
5. 后续建议
```

## 10.2 Spring Boot 全流程 Agent 工作流

```
需求分析
  ↓
Agent Ask：理解现有架构
  ↓
Agent Plan：输出实施方案
  ↓
人工确认方案
  ↓
Agent Implement：
  ├── Controller + DTO
  ├── Service + 业务逻辑
  ├── Repository + Mapper
  ├── Redis 缓存/锁
  ├── MQ 消息
  └── 异常处理
  ↓
Agent Test：
  ├── 单元测试
  ├── 集成测试
  └── 边界测试
  ↓
Agent Review：
  ├── 代码质量
  ├── 性能分析
  ├── 安全检查
  └── Git Diff
  ↓
人工验收
  ↓
Git Commit + PR
  ↓
CI/CD 自动化
```

## 10.3 Redis 高并发场景：优惠券秒杀

### ❌ 错误做法

```
写一个优惠券秒杀功能。
```

### ✅ 正确做法：分阶段

**阶段一：架构设计（不写代码）**

```
分析优惠券秒杀需求。

重点考虑：
1. Redis 数据结构设计（Hash 存库存、Set 存已购用户）
2. 库存扣减方案（Lua 脚本原子操作）
3. 超卖防护（Redis + 数据库双重校验）
4. 并发控制（分布式锁 + 原子操作）
5. 幂等性（用户 ID + 优惠券 ID 去重）
6. MQ 异步下单（削峰填谷）
7. 最终一致性（Redis → MQ → MySQL）
8. Redis 宕机降级（数据库兜底）
9. 预热策略（活动前加载数据到 Redis）
10. 监控告警

先输出架构方案，不写代码。
```

**阶段二：基于方案实现**

```
基于刚才确定的架构开始实现。

要求：
- 每完成一个模块执行对应测试
- Lua 脚本必须有单元测试
- MQ 消费者必须处理重复消费
- 所有操作必须幂等
```

**阶段三：验证**

```
对秒杀功能进行压力测试和验证。

检查：
- 1000 并发下是否超卖
- Redis 宕机时是否能降级
- MQ 消费失败是否重试
- 幂等性是否保证
- 数据一致性是否保证
```

## 10.4 MySQL 设计场景

### ❌ 错误做法

```
设计一个订单表。
```

### ✅ 正确做法

```
不要直接设计表。

先分析：
1. 核心查询模式（按用户查、按状态查、按时间查）
2. 写入模式（订单创建、状态变更、支付回调）
3. 数据增长速度（日均订单量、保留周期）
4. QPS（读写比、峰值 QPS）
5. 事务边界（哪些操作必须在一个事务内）
6. 并发场景（库存扣减、余额更新）
7. 索引需求（查询条件、排序字段）
8. 唯一约束（防重复）
9. 数据一致性（与缓存、MQ 的一致性）
10. 数据生命周期（热数据/冷数据/归档）

然后给出数据库设计方案，包括：
- 表结构
- 索引设计
- 分库分表策略（如需要）
- 读写分离方案
```

## 10.5 RocketMQ 场景

```
设计订单系统的 MQ 方案。

要求回答：
1. Producer 设计：同步/异步、重试、事务消息
2. Message 设计：Topic、Tag、Key 规范
3. Broker 设计：刷盘策略、同步/异步复制
4. Consumer 设计：推/拉、消费组、并发度
5. 重复消费：幂等性保证方案
6. 消息丢失：从 Producer 到 Consumer 的全链路保障
7. 消息积压：监控和应急方案
8. 顺序消息：如何保证订单状态变更的顺序
9. 事务消息：下单 + 扣库存的事务消息方案
10. 延迟消息：订单超时取消的延迟消息方案

先输出设计方案，再实现。
```

---

# 第十一部分 安全治理与风险控制

## 11.1 操作风险分级表

| 操作 | 策略 | 工具支持 |
|------|------|---------|
| grep / cat / find | Auto | 所有工具 |
| mvn test / npm test | Auto | 所有工具 |
| git diff / git status | Auto | 所有工具 |
| 编辑代码 | Auto/Review | Cursor Rules + Claude Hooks |
| git commit | Review | Codex / Claude Code |
| git push | Confirm | 所有工具 |
| 修改配置文件 | Review | Hook 拦截 |
| 删除文件 | Confirm | 权限控制 |
| rm -rf | 禁止 | 权限控制 |
| 生产数据库操作 | Human Only | 硬性规则 |
| 生产部署 | Human Only | CI/CD Gate |

## 11.2 企业安全策略

```json
// .claude/settings.json - 企业安全配置
{
  "permissions": {
    "deny": [
      "bash(rm -rf*)",
      "bash(curl*|*token*)",
      "bash(kubectl delete*)",
      "bash(terraform destroy*)",
      "read(**/.env*)",
      "read(**/*password*)",
      "read(**/*secret*)",
      "read(**/*credential*)"
    ],
    "allow": [
      "bash(mvn *)",
      "bash(git diff*)",
      "bash(git status*)",
      "bash(git log*)",
      "bash(grep *)",
      "bash(find *)",
      "read(**/*.java)",
      "read(**/*.xml)",
      "read(**/*.yaml)",
      "read(**/*.yml)",
      "read(**/*.md)"
    ]
  }
}
```

## 11.3 代码审查与合规

### AI 生成代码的审查清单

```
□ 功能正确性：是否满足需求？
□ 编译：是否通过 mvn compile？
□ 测试：是否通过 mvn test？
□ 安全：是否存在注入、XSS、敏感信息泄露？
□ 性能：是否存在 N+1、慢查询、内存泄漏？
□ 并发：是否线程安全？
□ 异常：是否完整处理？
□ 日志：是否关键操作有日志？
□ 文档：是否需要更新 API 文档？
□ 依赖：是否引入了不必要的新依赖？
□ 规范：是否遵循项目编码规范？
□ Git Diff：修改范围是否合理？
```

---

# 第十二部分 个人能力建设与学习路线

## 12.1 五级能力模型

### Level 1：AI 使用者
```
会 ChatGPT / 会 Cursor / 会 Claude Code
```

### Level 2：AI Programmer
```
会 Agent / 会 Context / 会 Rules / 会 MCP / 会 Skills
```

### Level 3：AI Engineer
```
会 Agent Workflow / 会 Subagent / 会 Memory
会 Tool Calling / 会 RAG / 会 Evaluation
```

### Level 4：AI Software Engineer
```
AI + Java + Python + Frontend + DevOps + Agent + Architecture
```

### Level 5：Agent Engineer
```
Model + Harness + Tools + Skills + Memory
Sandbox + Workflow + Evaluation
```

## 12.2 推荐学习路线

### 第一阶段：AI Coding（1-2 月）

```
Cursor（入门）
  → Claude Code（进阶）
  → Codex（自动化）

掌握：
  Agent / Ask / Plan / Rules / Context / Diff / Terminal / Git
```

### 第二阶段：Context Engineering（2-3 月）

```
CLAUDE.md 编写
  → AGENTS.md 跨工具规范
  → .cursor/rules
  → Skills 设计
  → Memory 管理
  → MCP 集成
```

### 第三阶段：AI Java Engineering（3-6 月）

```
让 AI 按企业级标准完成：
  Spring Boot / Spring Cloud / Redis / MySQL / RocketMQ / ES / Docker / K8s
```

### 第四阶段：Agent Engineering（6-12 月）

```
Tool Calling / ReAct / Planning / Memory / Subagent
  → MCP / Skills / Workflow / Evaluation / Sandbox
```

### 第五阶段：Harness Engineering（12+ 月）

```
DeepSeek Harness / Hermes
  → Agent = Model + Harness + Tools + Skills + Memory + Runtime
```

## 12.3 终极目标：AI Java 软件工厂

```
                    产品需求
                       ↓
                  Product Agent
                       ↓
                  Architecture
                       ↓
              ┌────────┴────────┐
              ↓                 ↓
        Backend Agent       Frontend Agent
              ↓                 ↓
          Java/Spring          UI
              ↓                 ↓
           Database           API
              ↓                 ↓
            Redis             Test
              ↓                 ↓
             MQ                 │
              └────────┬────────┘
                       ↓
                  Integration
                       ↓
                  QA Agent
                       ↓
                Security Agent
                       ↓
              Performance Agent
                       ↓
                Code Reviewer
                       ↓
                    Human
                       ↓
                     Git
                       ↓
                    CI/CD
                       ↓
                  Production
```

你的核心竞争力从：

> "我会不会写 Java？"

逐渐变成：

> **"我能不能设计一个可靠的软件工程系统，让 AI 持续、高质量、可验证地完成软件开发？"**

---

# 附录

## A. 完整配置模板合集

### A.1 .claude/settings.json

```json
{
  "permissions": {
    "deny": ["bash(rm -rf*)", "read(**/.env*)"],
    "allow": ["bash(mvn *)", "bash(git diff*)"]
  },
  "hooks": {
    "PostToolUse": [
      {
        "matcher": "Write|Edit",
        "hooks": [
          { "type": "command", "command": "npx prettier --write $CLAUDE_FILE_PATH" }
        ]
      }
    ]
  }
}
```

### A.2 .cursor/rules/java.mdc

```markdown
---
description: Java Spring Boot 编码规范
globs: **/*.java
alwaysApply: false
---

# Spring Boot 编码规范

## 分层
- Controller: 参数校验 + 响应封装
- Service: 业务编排，≤30 行/方法
- Repository: 数据访问

## 异常
- 业务异常: BusinessException
- 统一处理: @RestControllerAdvice

## 日志
- 使用 @Slf4j
- 关键操作打印 traceId
```

### A.3 AGENTS.md（跨工具）

```markdown
# AGENTS.md

## 架构
Spring Boot 3 + Spring Cloud Alibaba 微服务

## 标准
- Java 21
- 不使用 Lombok
- 公共 API 必须有 Javadoc
- 测试覆盖率 ≥ 85%

## Git
- Conventional Commits
- PR 必须通过 CI + Review
```

### A.4 codex.config.json

```json
{
  "permissions": {
    "allow": ["bash(mvn *)", "bash(git diff*)", "read", "edit"],
    "deny": ["bash(git push*)", "bash(rm -rf*)"]
  }
}
```

### A.5 Hermes config.yaml

```yaml
memory:
  memory_enabled: true
  user_profile_enabled: true
  memory_char_limit: 2200
  user_char_limit: 1375
  write_approval: false

skills:
  write_approval: false

auxiliary:
  background_review:
    enabled: true
    provider: openrouter
    model: google/gemini-3-flash-preview

display:
  memory_notifications: on
```

## B. 官方资源入口

| 工具 | 资源 | 链接 |
|------|------|------|
| Claude Code | 官方文档 | https://code.claude.com/docs/en/overview |
| Claude Code | 大型代码库最佳实践 | https://claude.com/blog/how-claude-code-works-in-large-codebases |
| Claude Code | Skills 文档 | https://code.claude.com/docs/en/skills |
| Claude Code | Hooks 文档 | https://code.claude.com/docs/en/hooks |
| Claude Code | Subagents 文档 | https://code.claude.com/docs/en/sub-agents |
| Claude Code | Memory 文档 | https://code.claude.com/docs/en/memory |
| Claude Code | Spring Boot 模板 | https://github.com/piomin/claude-ai-spring-boot |
| Codex CLI | 官方文档 | https://help.openai.com/en/articles/11096431 |
| Codex CLI | GitHub | https://github.com/openai/codex |
| Codex App | ChatGPT Codex | https://chatgpt.com/codex/ |
| Codex App | Workspace Agents | https://openai.com/index/introducing-workspace-agents-in-chatgpt/ |
| Cursor | 官方文档 | https://cursor.com/docs |
| Cursor | Agent Best Practices | https://cursor.com/blog/agent-best-practices |
| Cursor | Rules Guide | https://www.morphllm.com/cursor-rules-best-practices |
| DeepSeek Harness | 官方页面 | https://deepseek.com/harness/en/ |
| DeepSeek Harness | GitHub | https://github.com/deepseek-ai/deepseek-harness |
| DeepSeek Harness | 开发者文档 | https://deepseek-harness.github.io/deepseek-harness/en/guide/quickstart |
| Hermes | 官方文档 | https://hermes-agent.nousresearch.com/docs/ |
| Hermes | GitHub | https://github.com/nousresearch/hermes-agent |
| Hermes | Memory 文档 | https://hermes-agent.nousresearch.com/docs/user-guide/features/memory |
| Hermes | Skills 文档 | https://hermes-agent.nousresearch.com/docs/user-guide/features/skills |
| MCP | 协议规范 | https://modelcontextprotocol.io |
| Spring AI MCP | MCP Server Starter | https://docs.spring.io/spring-ai/reference/api/mcp/mcp-server-boot-starter-docs.html |
| Spring AI MCP | 教程 | https://www.baeldung.com/spring-ai-model-context-protocol-mcp |

---

> **文档版本**: v1.0 | **最后更新**: 2026-08-23
> **作者**: AI 全栈开发者实战指南
> **许可**: 自由使用，请注明出处
