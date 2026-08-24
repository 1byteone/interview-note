> **[← 目录](README.md)** | 章节 02/12

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

---

[← 上一章: 01-认知升级](01-认知升级.md) | [目录](README.md) | [下一章: 03-Codex(03-Codex.md)](03-Codex.md)
