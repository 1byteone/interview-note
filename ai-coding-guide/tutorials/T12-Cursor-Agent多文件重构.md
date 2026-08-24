# T12: Cursor Agent 多文件重构

> **[← 教程目录](README.md) | 工具: Cursor Agent Mode | 时长: ~25min**

---

## Goal

用 Cursor Agent Mode 将一个**臃肿的 Service 类**拆分为符合单一职责原则的多个 Service。

## 前置条件

- Cursor IDE 打开 Java 项目
- 有一个超过 500 行的 Service 类（如 OrderService.java）

## Step 1: 先用 Ask Mode 分析

切换到 **Ask Mode**（Ctrl+Shift+P → "Toggle Agent Mode" 或点击 Ask 按钮）：

```
分析 OrderService.java 的职责。

列出：
1. 它目前承担的所有职责
2. 每个职责涉及的方法
3. 建议拆分成哪些独立 Service
4. 拆分后的依赖关系

不要修改代码，只做分析。
```

Claude 会分析并输出：

```
OrderService 当前职责：
1. 订单创建（createOrder, validateOrder）
2. 订单状态管理（cancelOrder, completeOrder, updateStatus）
3. 库存管理（checkStock, deductStock, restoreStock）
4. 支付处理（processPayment, handleCallback）
5. 查询（findById, findByUserId, search）

建议拆分为：
- OrderService（核心订单逻辑）
- OrderStatusService（状态流转）
- StockService（库存操作）
- PaymentService（支付相关）
```

## Step 2: 切换到 Agent Mode 执行重构

按 `Shift+Tab` 切换到 **Agent Mode**：

```
基于刚才的分析，重构 OrderService.java。

要求：
1. 拆分为 OrderService / OrderStatusService / StockService
2. 保持所有现有测试通过
3. 保持 API 层（Controller）不变
4. 每个新 Service 单独一个文件
5. 在拆分完成后运行 mvn test -pl order-service 验证

先用 Plan 模式输出实施计划。
```

## Step 3: 审查 Plan

Agent 会生成类似：

```
## 重构计划

### 阶段 1: 创建新 Service
- StockService.java - 库存检查/扣减/恢复
- OrderStatusService.java - 状态流转

### 阶段 2: 重构 OrderService
- 移除库存相关方法
- 移除状态管理方法
- 注入新 Service 的依赖

### 阶段 3: 更新测试
- OrderServiceTest - 移除已迁移的测试
- StockServiceTest - 新增
- OrderStatusServiceTest - 新增

### 阶段 4: 验证
- mvn test -pl order-service
- 检查 Controller 层无需修改
```

确认计划后，Agent 开始执行。

## Step 4: 监控执行过程

Agent 会自动：
1. 创建新文件
2. 移动方法
3. 更新 import
4. 调整依赖注入
5. 运行测试
6. 如果测试失败，自动修复

## Step 5: 验证重构结果

```bash
# 测试必须全部通过
mvn test -pl order-service

# 检查旧方法是否已移除
grep -c "deductStock" OrderService.java
# 应该返回 0

# 检查新 Service 是否存在
ls StockService.java OrderStatusService.java
```

## Step 6: 检查 Git Diff

```
git diff --stat
git diff
```

确认：
- 修改范围符合预期
- 没有修改无关文件
- API 层（Controller）没有变化

## 常见问题

| 问题 | 解决方案 |
|------|---------|
| Agent 修改了 Controller | 回退并明确: `不要修改 Controller，只重构 Service 层` |
| 测试失败无法自动修复 | 切换到 Ask Mode 分析失败原因，再回来修复 |
| 重构范围失控 | 用 `@file` 限定: `只修改 @OrderService.java @StockService.java` |
| Agent 生成了不需要的文件 | `删除 StockRepository.java，库存操作复用现有 ProductMapper` |

## 延伸

- → [T11: Cursor Rules](T11-Cursor-Rules实战.md)
- → [T14: 五工具协作](T14-五工具协作全流程.md)
