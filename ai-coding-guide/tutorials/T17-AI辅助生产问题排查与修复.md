# T17: AI 辅助生产问题排查与修复

> **[← 教程目录](README.md) | 工具: Claude Code + Cursor | 时长: ~45min**

---

## Goal

用 AI 工具排查一个**生产环境的真实 Bug**——从日志分析到根因定位到修复验证的完整流程。

## 前置条件

- 有一个可复现的生产问题（或模拟场景）
- 日志可访问（本地日志文件或 Loki/ELK）

## 场景描述

```
生产环境报告：订单偶发性重复扣款。
- 用户反馈：支付了两次
- 日志显示：回调接口被调用了两次
- 时间间隔：两次调用相差 3 秒
```

## Phase 1: 用 Cursor Ask 分析日志

```
分析以下生产日志，定位订单重复扣款的根因：

日志片段（已脱敏）：
---
2026-08-20 14:23:01 [order-service] INFO  createOrder: orderId=ORD001, userId=10086, amount=299.00
2026-08-20 14:23:02 [payment-service] INFO  processPayment: orderId=ORD001, amount=299.00
2026-08-20 14:23:03 [payment-service] INFO  callback received: orderId=ORD001, status=SUCCESS
2026-08-20 14:23:03 [order-service] INFO  updateStatus: orderId=ORD001, status=PAID
2026-08-20 14:23:06 [payment-service] INFO  callback received: orderId=ORD001, status=SUCCESS
2026-08-20 14:23:06 [order-service] INFO  updateStatus: orderId=ORD001, status=PAID
2026-08-20 14:23:06 [order-service] INFO  deductStock: orderId=ORD001 (第二次!)
---

请分析：
1. 为什么回调被调用了两次？
2. 为什么第二次没有被拦截？
3. 根因是什么？
4. 需要修改哪些代码？
```

## Phase 2: 用 Claude Code 深度分析

```bash
claude
```

```
分析 PaymentCallbackController 和 OrderService 的回调处理逻辑。

重点检查：
1. 幂等性保证机制
2. 并发安全（两次回调同时到达时的锁机制）
3. 数据库唯一约束
4. Redis 分布式锁的使用

根因假设：
- 第一次回调成功，状态变为 PAID
- 第二次回调到达时，应该检查当前状态已经是 PAID，跳过处理
- 但可能存在并发窗口：两次回调同时通过状态检查

请验证这个假设，并给出修复方案。
```

## Phase 3: 用 Cursor Agent 修复

```
修复订单回调重复处理的问题。

要求：
1. 在 PaymentCallbackController 中增加幂等性检查
2. 使用 Redis 分布式锁防止并发回调
3. 数据库层面增加唯一约束兜底
4. 修复后运行 mvn test 验证
5. 添加并发测试用例（模拟两次回调同时到达）
```

## Phase 4: 验证修复

```bash
# 运行测试
mvn test -pl order-service -Dtest=PaymentCallbackTest

# 检查并发测试
mvn test -pl order-service -Dtest=ConcurrentCallbackTest

# 检查修改范围
git diff
```

## 修复代码示例

```java
// 修复后的 PaymentCallbackController
@RestController
@RequestMapping("/api/payment")
@Tag(name = "支付回调")
public class PaymentCallbackController {

    @PostMapping("/callback")
    public CallbackResult handleCallback(@RequestBody CallbackRequest request) {
        String lockKey = "lock:payment:callback:" + request.getOrderId();
        
        // 1. 分布式锁防并发
        RLock lock = redissonClient.getLock(lockKey);
        try {
            lock.lock(5, TimeUnit.SECONDS);
            
            // 2. 幂等性检查：查询当前状态
            Order order = orderService.getOrder(request.getOrderId());
            if (OrderStatus.PAID.equals(order.getStatus())) {
                log.info("订单已支付，跳过重复回调: orderId={}", request.getOrderId());
                return CallbackResult.duplicate();
            }
            
            // 3. 更新状态
            orderService.updateStatus(request.getOrderId(), OrderStatus.PAID);
            
            // 4. 扣减库存（在事务内）
            stockService.deductStock(request.getOrderId());
            
            return CallbackResult.success();
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
```

---

## 常见问题

| 问题 | 解决方案 |
|------|---------|
| 日志量太大无法分析 | 让 AI 先过滤：只看 ERROR + 特定 orderId 的日志 |
| 根因不确定 | 让 Claude Code 列出所有可能的根因，逐个排查 |
| 修复引入新问题 | 用 Codex Full Auto 运行全量测试验证 |
| 不确定影响范围 | 用 Cursor Ask 分析修改的影响链 |

## 延伸

- → [T15: 项目工程化实践](T15-项目工程化AI编程完整实践.md)
- → [10-Java 全流程实战](../10-Java全流程实战.md)
