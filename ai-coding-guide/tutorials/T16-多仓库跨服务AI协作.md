# T16: 多仓库跨服务 AI 协作实践

> **[← 教程目录](README.md) | 工具: Claude Code + Cursor + Codex | 时长: ~60min**

---

## Goal

在**多仓库（Multi-Repo）微服务架构**中，用 AI 工具协调跨服务的变更，确保一致性。

真实场景：修改一个 FeignClient 接口，需要同步更新调用方、提供方、文档。

## 前置条件

- 多个独立 Git 仓库的微服务
- 各仓库都有自己的 CLAUDE.md / AGENTS.md

## Step 1: 跨仓库架构映射

用 Claude Code 的 Subagent 并行分析：

```
我需要修改 order-service 的 FeignClient 接口。

请：

Subagent 1: 分析 order-service 中 PaymentApi 接口的定义
  - 找到所有方法签名
  - 找到所有使用这个接口的地方

Subagent 2: 分析 payment-service 中对应的 Controller
  - 找到实现逻辑
  - 找到调用方列表

Subagent 3: 分析 api 模块中的接口定义
  - 找到共享的 DTO
  - 找到版本兼容性约束

输出：跨服务影响分析报告
```

## Step 2: 用 Codex 在提供方实现变更

```bash
cd payment-service && codex --full-auto
```

```
修改 PaymentApi 接口，新增 refundOrder 方法。

要求：
1. 修改 api 模块中的 PaymentApi.java
2. 修改 payment-service 中的实现
3. 确保向后兼容（旧方法不删除）
4. 运行 mvn verify 验证
```

## Step 3: 用 Cursor 更新调用方

```bash
# 在 Cursor 中打开 order-service
```

```
payment-service 新增了 refundOrder 方法。

请：
1. 更新 order-service 中的 PaymentFeignClient
2. 在 OrderService 中添加退款调用逻辑
3. 添加对应的单元测试
4. 确保旧接口调用不受影响
```

## Step 4: 用 Hermes 更新文档

```bash
hermes
```

```
记录本次跨服务变更：
1. 更新 docs/api/payment-api.md
2. 更新 docs/api/order-api.md  
3. 记录到 docs/ADR/006-payment-refund.md
4. 更新 CHANGELOG.md
```

## Step 5: 提交与验证

```bash
# 各仓库分别提交
cd payment-service && git add -A && git commit -m "feat(payment): add refundOrder API"
cd order-service && git add -A && git commit -m "feat(order): integrate refundOrder"
cd api && git add -A && git commit -m "feat(api): add refundOrder to PaymentApi"
```

---

## 常见问题

| 问题 | 解决方案 |
|------|---------|
| 跨仓库引用断裂 | 用 Claude Code Subagent 做影响分析 |
| DTO 不一致 | 在 api 模块中统一管理共享 DTO |
| 文档遗漏 | 用 Hermes 记忆确保文档更新 |
| CI 跨仓库依赖 | 使用 Git Submodule 或 Artifactory 版本管理 |

## 延伸

- → [T15: 项目工程化实践](T15-项目工程化AI编程完整实践.md)
- → [T14: 五工具协作](T14-五工具协作全流程.md)
