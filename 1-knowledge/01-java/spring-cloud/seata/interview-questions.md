# Seata 分布式事务面试题大全

## 📚 知识体系

```
Seata 核心概念
├── 事务模式
│   ├── AT 模式（自动补偿）
│   ├── TCC 模式（手工补偿）
│   ├── Saga 模式（长事务）
│   └── XA 模式（强一致）
├── 核心组件
│   ├── Transaction Coordinator (TC) 事务协调器
│   ├── Transaction Manager (TM) 事务管理器
│   ├── Resource Manager (RM) 资源管理器
│   └── Global Lock 全局锁
├── 数据源代理
│   ├── 前置镜像
│   ├── 后置镜像
│   ├── undo_log 表
│   └── 全局锁
└── 与 Spring Cloud 集成
```

---

## 🎯 Level 1：基础题

### 1. 什么是分布式事务？为什么需要？
**答案**：
分布式事务是指涉及多个数据库、多个服务的事务操作，需要保证所有参与的节点要么全部成功、要么全部失败。

**为什么需要**：微服务架构中，一个业务操作往往跨多个服务（如：下单 → 扣库存 → 扣余额），每个服务有自己的数据库，本地事务无法保证整体一致性。

**跨服务场景**：
```
用户下单
 ├── 订单服务 → 创建订单（本地事务 OK）
 ├── 库存服务 → 扣减库存（本地事务 OK）
 └── 用户服务 → 扣减余额（本地事务 OK）
         ↓ 任意一个失败 → 整个下单失败
```

---

## 🎯 Level 2：进阶题

### 2. Seata 的核心组件和角色是什么？
**答案**：

| 角色 | 全称 | 职责 |
|------|------|------|
| **TC** | Transaction Coordinator | 事务协调器，管理全局事务状态 |
| **TM** | Transaction Manager | 事务管理器，发起/提交/回滚全局事务 |
| **RM** | Resource Manager | 资源管理器，管理分支事务、上报状态 |

**执行流程**：
```text
① TM 向 TC 发起全局事务
    ↓ 获取 XID（全局事务ID）
② 各服务通过 XID 关联全局事务
    ↓
③ RM 向 TC 注册分支事务
    ↓
④ 业务执行 → RM 上报分支状态
    ↓
⑤ TM 向 TC 提交/回滚全局事务
    ↓
⑥ TC 协调各 RM 提交/回滚分支事务
```

### 3. AT 模式的原理是什么？
**答案**：
AT 模式：**自动补偿**，基于数据源代理自动生成镜像，实现无侵入。

**两阶段提交**：
```text
【一阶段（本地提交）】
业务 SQL 执行前：
  ↓ 数据源代理：生成前置镜像（beforeImage）→ 写入 undo_log
  ↓ 执行业务 SQL（本地提交）
  ↓ 生成后置镜像（afterImage）→ 写入 undo_log
  ↓ 向 TC 注册分支并上报（分支完成，本阶段即提交）

【二阶段（全局提交/回滚）】
全局提交：
  ↓ 删除 undo_log（实际数据已提交，无需处理）
  
全局回滚：
  ↓ 读取 undo_log 前置镜像
  ↓ 生成反向 SQL（UPDATE → 恢复原值）
  ↓ 校验后置镜像（防止脏写）
  ↓ 执行反向 SQL 回滚数据
  ↓ 删除 undo_log
```

---

## 🎯 Level 3：高级题

### 4. AT、TCC、Saga、XA 四种模式如何选择？
**答案**：

| 模式 | 一致性 | 侵入性 | 性能 | 适用场景 |
|------|--------|--------|------|----------|
| **AT** | 最终一致 | 低（代理 SQL） | 高 | 常规业务，推荐首选 |
| **TCC** | 最终一致 | 高（3个接口） | 中 | 强依赖人工补偿（资金类） |
| **Saga** | 最终一致 | 中（正向+补偿） | 高 | 长事务（订单流程） |
| **XA** | 强一致 | 低（原生态） | 低 | 可靠性要求极高（银行） |

**选择建议**：
- 常规场景 → **AT**
- 资金/余额 → **TCC**
- 长链路/跨多服务 → **Saga**
- 强一致不差性能 → **XA**

### 5. TCC 模式的实现？
**答案**：
TCC（Try-Confirm-Cancel）：三个接口手动实现。

```java
@TccBusinessId("orderId")
public interface OrderTccService {
    
    // Try：资源预留
    @TwoPhaseBusinessAction(
        name = "orderTccAction",
        commitMethod = "confirm",
        rollbackMethod = "cancel"
    )
    void tryCreateOrder(TccActionContext context, @BusinessActionContextParameter(paramName = "orderId") Long orderId);
    
    // Confirm：确认执行
    void confirm(TccActionContext context);
    
    // Cancel：取消/回滚
    void cancel(TccActionContext context);
}
```

**示例：库存预留**
```java
// Try 阶段：冻结库存
public void tryCreateOrder(...) {
    // UPDATE inventory SET frozen = frozen + 1 WHERE product_id = ?
    inventoryService.freezeStock(orderId, productId, count);
}

// Confirm 阶段：正式扣减
public void confirm(...) {
    // UPDATE inventory SET stock = stock - 1, frozen = frozen - 1 WHERE product_id = ?
    inventoryService.confirmDeduct(productId, count);
}

// Cancel 阶段：释放冻结
public void cancel(...) {
    // UPDATE inventory SET frozen = frozen - 1 WHERE product_id = ?
    inventoryService.cancelFreeze(orderId, productId, count);
}
```

---

## 🎯 Level 4：专家题

### 6. 分布式事务之外，还有哪些保证最终一致性的方案？
**答案**：

**方案一：本地消息表 + 消息队列**
```text
① 业务本地事务：写业务表 + 写本地消息表（同事务）
② 定时任务扫描消息表 → 发送到 MQ
③ 消费者处理 → 成功后 ack + 修改消息状态
④ 失败重试 + 死信处理
```

**方案二：事务消息（RocketMQ）**
```text
① Producer 发送半消息
② 执行本地事务
③ 提交/回滚消息
④ 回查机制保证最终一致性
```

**方案三：MQ 重试 + 幂等**
- 消息重试 N 次
- 消费者接口幂等设计
- 最终一致

**方案对比**：
| 方案 | 一致性 | 复杂度 | 适用 |
|------|--------|--------|------|
| Seata AT | 最终一致（2PC） | 中 | 中大型跨服务事务 |
| 本地消息表 | 最终一致 | 中 | 异步解耦场景 |
| RocketMQ 事务消息 | 最终一致 | 中 | 下单/支付等 |
| 状态机+补偿 | 最终一致 | 高 | 复杂长流程 |

---

## 📖 学习资源

### 推荐项目
- [Seata 官方文档](https://seata.io/)
- [Seata 示例](https://github.com/seata/seata-samples)

### 最佳实践
1. 优先使用 AT 模式（侵入性最低）
2. 避免跨服务大事务，能拆就拆（削峰解耦）
3. 同步链路短的事务用 Seata，异步链路用 MQ+补偿
4. 保证业务方法幂等
5. 监控 undo_log、全局事务超时等指标
6. 业务允许的话用"本地事务 + 消息队列 + 最终一致性"