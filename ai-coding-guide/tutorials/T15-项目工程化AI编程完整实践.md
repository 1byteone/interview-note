# T15: 项目工程化 AI 编程完整实践

> **[← 教程目录](README.md) | 工具: 全部五个 | 时长: ~90min**

---

## Goal

将 AI 编程工具**工程化地集成到一个真实 Java 微服务项目**中，建立从团队规范、开发流程、代码审查到 CI/CD 的完整 AI 工程化体系。

这是本教程集中**最核心、最有价值**的一个教程——不是演示某个工具的单个功能，而是展示如何在真实项目中**系统性地**使用 AI 编程工具。

---

## 前置条件

- 已有 Spring Boot 微服务项目（或用 T01 生成）
- 五个工具全部安装
- Git + GitHub 仓库
- 基本的 CI/CD 环境（GitHub Actions / Jenkins）

## Phase 1: 建立团队 AI 规范（30min）

### Step 1.1: 创建 AGENTS.md（跨工具统一规范）

```bash
cat > AGENTS.md << 'ENDOFFILE'
# AGENTS.md - 团队 AI 编程统一规范

> 本文件是所有 AI 编程工具的单一事实来源。
> Claude Code 读 CLAUDE.md，Cursor 读 .mdc，Codex 读本文件。
> 所有工具的规则都从这里派生。

## 项目概述
电商微服务后端，Spring Boot 3.3 + Java 21。
- user-service: 用户认证、注册、信息管理
- order-service: 订单创建、状态流转、超时取消
- product-service: 商品管理、库存、搜索
- payment-service: 支付、退款、回调
- gateway: API 网关（Spring Cloud Gateway）

## 编码标准

### 分层约束
- Controller: 只做参数校验（@Valid）和响应封装
- Service: 业务编排，单方法 ≤30 行
- Repository: 数据访问，不包含业务判断
- DTO: 与 Entity 严格分离

### 命名规范
- Entity: PascalCase (UserOrder)
- DTO: XxxDTO (UserCreateDTO)
- Service: XxxService / XxxServiceImpl
- Controller: XxxController
- Mapper: XxxMapper

### 异常处理
- 业务异常: BusinessException(message, code)
- 统一处理: @RestControllerAdvice
- 禁止 catch(Exception) 后不处理
- 不允许向客户端暴露堆栈

### 日志规范
- 关键操作: log.info
- 异常: log.error("操作失败", e)
- 禁止循环中打印日志
- 敏感信息脱敏（手机号中间4位 *）

## 测试标准
- 单元测试覆盖率 ≥ 85%
- 每个 API 必须有集成测试
- 使用 Testcontainers 做集成测试
- 禁止 mock 数据库连接

## 安全标准
- 禁止硬编码密码/密钥/Token
- SQL 必须参数化
- 所有输入必须 @Valid 校验
- 密码 BCrypt 加密存储

## Git 标准
- 分支: feature/xxx, fix/xxx, refactor/xxx
- 提交: type(scope): description
- PR 必须通过 CI + Code Review
- 禁止直接推 main

## Code Review 规则
- 禁止修改 REST API 字段名（除非版本迁移）
- 禁止日志打印密码/Token/手机号明文
- 库存操作必须在事务内
- Redis 缓存更新考虑一致性
- MQ 消费者必须幂等
- 禁止循环中调用外部服务
ENDOFFILE
```

### Step 1.2: 派生各工具的规则文件

```bash
# Claude Code 的 CLAUDE.md（从 AGENTS.md 派生 + Claude 特定配置）
cat > CLAUDE.md << 'ENDOFFILE'
# CLAUDE.md

## 使用 AGENTS.md
@AGENTS.md

## Claude Code 特定配置
- 进入 Plan 模式处理非简单任务（3+ 步骤）
- 每次修改后运行对应测试验证
- 使用 Subagent 处理探索性任务
- 复杂重构用 /agents 指定专业 Agent

## 构建命令
- 编译: mvn clean compile
- 单服务测试: mvn test -pl <service>
- 全量验证: mvn verify
ENDOFFILE

# Cursor 的 .mdc 规则
mkdir -p .cursor/rules

cat > .cursor/rules/java.mdc << 'ENDOFFILE'
---
description: Java Spring Boot 编码规范（派生自 AGENTS.md）
globs: **/*.java
alwaysApply: true
---
@AGENTS.md 中的「编码标准」和「测试标准」部分
ENDOFFILE

cat > .cursor/rules/security.mdc << 'ENDOFFILE'
---
description: 安全编码规范（派生自 AGENTS.md）
globs: **/*.java
alwaysApply: true
---
@AGENTS.md 中的「安全标准」部分
ENDOFFILE

# Codex 的 AGENTS.md 已经在根目录（直接使用）
```

### Step 1.3: 验证规范一致性

```bash
# 确认所有规则文件都指向同一来源
echo "=== AGENTS.md (源) ==="
wc -l AGENTS.md

echo "=== CLAUDE.md (Claude Code) ==="
wc -l CLAUDE.md

echo "=== .cursor/rules/ (Cursor) ==="
ls -la .cursor/rules/

echo "=== AGENTS.md (Codex) ==="
# Codex 直接读根目录的 AGENTS.md
```

---

## Phase 2: 开发流程工程化（30min）

### Step 2.1: 需求分析阶段——用 Cursor Ask

```
# 在 Cursor Ask Mode 中：
分析订单服务的当前架构。

需求: 给订单服务增加"优惠券抵扣"功能。

请分析：
1. 订单创建流程中哪些环节需要插入优惠券逻辑
2. 现有的金额计算方式（是否已预留扩展点）
3. 数据库需要新增哪些表
4. Redis 需要新增哪些数据结构
5. MQ 需要新增哪些 Topic/Tag
6. 对现有 API 的影响

输出架构分析报告，不修改代码。
```

### Step 2.2: 方案设计阶段——用 Claude Code

```bash
cd order-service && claude
```

```
基于 docs/architecture-analysis.md（Cursor Ask 已生成），
设计优惠券抵扣功能的完整实施方案。

# 设计范围
1. 领域模型（优惠券类型、使用规则、抵扣记录）
2. 数据库设计（表结构 + 索引 + 分区策略）
3. Redis 设计（库存、用户已领集合、限流）
4. MQ 设计（领券事件、核销事件、对账事件）
5. API 设计（REST 接口 + 请求/响应格式）
6. 安全设计（防刷、防超领、幂等）
7. 实施计划（分阶段，每阶段可独立上线）

先输出完整设计文档，不写代码。
等我 review 后再实现。
```

### Step 2.3: 代码实现阶段——用 Codex

```bash
cd order-service && codex --full-auto
```

```
按照 docs/coupon-design.md 的设计方案实现。

# Phase 1: 数据层
- CouponEntity + CouponType 枚举
- CouponRecordEntity（领取记录）
- CouponDiscountRecordEntity（抵扣记录）
- 对应的 Mapper + DTO

# Phase 2: 核心服务
- CouponService（领券、查询可用券、核销）
- Redis Lua 脚本（库存原子扣减）
- 幂等性保证（用户+优惠券唯一键）

# Phase 3: API 层
- CouponController（REST API）
- OrderService 集成（下单时选券抵扣）

# Phase 4: 测试
- 每个 Phase 完成后运行 mvn test
- 集成测试用 Testcontainers
- 包含并发安全测试

分阶段实现，每阶段测试通过后继续。
```

### Step 2.4: 代码审查阶段——用 Cursor Agent + Codex Review

**Cursor Agent 审查（开发时）：**
```
审查优惠券功能的所有新增代码（coupon-service/ 目录）。

按照 AGENTS.md 中的 Code Review 规则逐条检查：
□ 分层约束
□ 异常处理
□ 日志规范
□ 安全标准
□ 测试覆盖

输出审查报告，标注每个问题的严重度。
```

**Codex Review（PR 阶段）：**
```bash
# 在 GitHub PR 中触发 Codex Review
# Codex 会自动读取 AGENTS.md 中的 Review 规则
@codex review
```

### Step 2.5: 文档化阶段——用 Hermes

```bash
hermes
```

```
1. 记录优惠券功能的设计决策到 docs/ADR/005-coupon-feature.md
2. 更新 API 文档 docs/api/coupon-api.md
3. 更新 README.md 中的服务清单
4. 记住这次实现中的关键经验：
   - Redis Lua 脚本的调试方法
   - 幂等性设计的最佳实践
   - Testcontainers 的配置技巧
```

---

## Phase 3: CI/CD 集成（30min）

### Step 3.1: GitHub Actions 集成 Codex Review

```yaml
# .github/workflows/ai-review.yml
name: AI Code Review
on:
  pull_request:
    types: [opened, synchronize]

jobs:
  ai-review:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: Codex Review
        uses: openai/codex-review-action@v1
        with:
          agents-md-path: AGENTS.md
          fail-on: error
```

### Step 3.2: 用 Codex Automations 做每日质量检查

```yaml
# 在 Codex App 中配置 Automations
# 名称: Daily Quality Gate
# 调度: 每天 08:00
# 任务: 
"""
检查过去 24 小时的所有提交：

1. 运行 mvn verify 确认构建通过
2. 检查测试覆盖率是否下降
3. 扫描新增代码的安全问题
4. 检查是否有硬编码的敏感信息
5. 输出每日质量报告到 Slack
"""
```

### Step 3.3: 用 Hermes 做定时巡检

```yaml
# ~/.hermes/config.yaml
schedules:
  - name: weekly-tech-debt
    cron: "0 10 * * 1"
    skill: tech-debt-scanner
    working_dir: ~/code/order-service
    notify:
      - type: chat

  - name: daily-dependency-check
    cron: "0 09 * * 1-5"
    skill: dependency-audit
    working_dir: ~/code/order-service
```

---

## 完整工程化流程图

```
                    需求
                     │
    ┌────────────────┼────────────────┐
    │ Phase 1: 规范   │ Phase 2: 开发   │ Phase 3: CI/CD  │
    │                │                │                │
    │ AGENTS.md      │ Cursor Ask     │ GitHub Actions │
    │ CLAUDE.md      │ (需求分析)      │ (自动 Review)  │
    │ .cursor/rules  │                │                │
    │                │ Claude Code    │ Codex Automations│
    │ 规范一致性检查   │ (方案设计)      │ (每日质量门)    │
    │                │                │                │
    │                │ Codex Full Auto│ Hermes         │
    │                │ (代码实现)      │ (定时巡检)      │
    │                │                │                │
    │                │ Cursor Agent   │                │
    │                │ (代码审查)      │                │
    │                │                │                │
    │                │ Hermes         │                │
    │                │ (文档化/记忆)    │                │
    └────────────────┼────────────────┘
                     │
                  生产上线
```

---

## 各阶段工具分工矩阵

| 阶段 | 工具 | 模式 | 输入 | 输出 |
|------|------|------|------|------|
| 规范建立 | 手动 | - | 团队约定 | AGENTS.md + 各工具规则 |
| 需求分析 | Cursor | Ask | 需求文档 | 架构分析报告 |
| 方案设计 | Claude Code | Plan | 架构分析 | 设计文档 |
| 代码实现 | Codex | Full Auto | 设计文档 | 可运行代码 |
| 代码审查 | Cursor + Codex | Agent + Review | PR Diff | 审查报告 |
| 文档化 | Hermes | 对话 | 实现过程 | ADR + API 文档 |
| CI 检查 | Codex | Automations | Git Push | 质量报告 |
| 定时巡检 | Hermes | Schedule | 定时触发 | 巡检报告 |

---

## 常见问题

| 问题 | 解决方案 |
|------|---------|
| 各工具规则不一致 | 所有规则从 AGENTS.md 派生，定期同步检查 |
| AI 生成代码不符合规范 | 强化 CLAUDE.md 中的约束，用 Hooks 自动格式化 |
| Code Review 噪音太多 | 精简 AGENTS.md 中的 Review 规则，只保留关键项 |
| CI 中 AI Review 太慢 | 只在 PR 时触发，不阻塞 push |
| 团队成员不习惯 | 从 T01-T03 开始，逐步引入 |

## 延伸

- → [T14: 五工具协作](T14-五工具协作全流程.md)
- → [02-Claude Code 详解](../02-Claude-Code.md)
- → [11-安全治理](../11-安全治理.md)
