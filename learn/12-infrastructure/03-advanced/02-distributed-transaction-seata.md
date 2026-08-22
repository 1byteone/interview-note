# Seata 分布式事务 — 全局一致性从入门到实战

> 🎯 进阶路线 · 预计阅读时间：45 分钟
> 目标：理解分布式事务核心问题与 Seata 的 AT 模式原理，掌握四种模式选型，能在 AI 商城的订单+库存+积分链路中正确使用。

---

## 一、分布式事务问题场景

### 1.1 下单全链路

在微服务架构中，一个"下单"操作涉及多个独立服务：

```
用户下单
  │
  ├── 订单服务：创建订单（状态=待支付）
  ├── 库存服务：扣减库存（预扣）
  └── 积分服务：增加用户积分（下单即送）
```

**问题**：如果订单创建成功、库存扣减成功，但积分服务因网络超时失败，就出现了**数据不一致**——用户下单成功了但没拿到积分。更严重的是：库存已经扣了但订单没生成，商品就凭空少了。

### 1.2 分布式事务的 CAP 权衡

| 理论 | 含义 | 分布式事务取舍 |
|------|------|---------------|
| C（一致性） | 所有节点同一时刻数据一致 | 追求最终一致性，而非强一致 |
| A（可用性） | 系统始终对外提供服务 | 优先保证用户能下单成功 |
| P（分区容忍性） | 网络分区时系统仍能运行 | 必须满足，微服务间网络不可靠 |

分布式事务的核心矛盾：**业务上需要强一致，技术上只能做最终一致**。Seata AT 模式通过全局锁 + 回滚日志，在**AP 与 CP 之间取得平衡**。

---

## 二、AT 模式两阶段提交原理

### 2.1 整体流程

```
第一阶段（Branch Commit）
  参与者执行本地事务，生成前镜像和后镜像，写入 undo_log 表
  返回"执行成功"给 TC

第二阶段（Global Commit / Rollback）
  TC 收到所有参与者成功 → 通知 Commit（异步清理 undo_log）
  TC 收到任一失败 → 通知 Rollback（用 undo_log 回滚）
```

### 2.2 核心概念

| 角色 | 说明 |
|------|------|
| TC（Transaction Coordinator） | 事务协调器，Seata Server，管理全局事务状态 |
| TM（Transaction Manager） | 事务管理器，@GlobalTransactional 注解所在的方法 |
| RM（Resource Manager） | 资源管理器，每个参与本地事务的微服务 |

### 2.3 关键数据结构

**BeforeImage（前镜像）**：执行 SQL 之前查询当前数据快照。

```sql
-- 库存表：sku_id = 1001，stock = 10
-- 执行 UPDATE stock SET stock = stock - 1 WHERE sku_id = 1001 之前
-- 前镜像：{sku_id: 1001, stock: 10}
```

**AfterImage（后镜像）**：执行 SQL 之后查询数据快照。

```sql
-- 执行 UPDATE 后
-- 后镜像：{sku_id: 1001, stock: 9}
```

**undo_log 表**：每张业务表对应的回滚日志表（与业务表同库）。

```sql
CREATE TABLE `undo_log` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `branch_id` bigint(20) NOT NULL,
  `xid` varchar(100) NOT NULL,
  `context` varchar(128) NOT NULL,
  `rollback_info` longblob NOT NULL,
  `log_status` int(11) NOT NULL,
  `log_created` datetime NOT NULL,
  `log_modified` datetime NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_unionkey` (`xid`,`branch_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

**回滚原理**：发现需要回滚时，根据 `undo_log` 中的 `rollback_info`（JSON 序列化的 beforeImage）构造反向 SQL，将数据恢复为**前镜像**状态。如果回滚时发现当前数据与后镜像不一致（脏写），则记录异常并人工介入。

---

## 三、四种模式对比

| 模式 | 一致性 | 业务侵入性 | 性能 | 适用场景 |
|------|--------|------------|------|----------|
| **AT** | 最终一致 | 低（自动生成回滚 SQL） | 中 | 大多数业务场景，优先选择 |
| **TCC** | 最终一致 | 高（需实现 Try/Confirm/Cancel） | 高 | 性能要求高、资源锁定时间长的场景 |
| **Saga** | 最终一致 | 中（需实现正向/补偿） | 高 | 长事务、不确定执行时间的场景 |
| **XA** | 强一致 | 低（数据库原生支持） | 低 | 对一致性要求极高、并发不高的场景 |

### 3.1 选型建议

- **优先 AT**：90% 的业务场景 AT 模式足够，代码侵入最小；
- **TCC 用于资源预留**：如库存预扣（Try 冻结库存，Confirm 实际扣减，Cancel 解冻）；
- **Saga 用于长流程**：如订单审批链（每个节点都有正向 + 补偿操作）；
- **XA 少用**：因为是数据库 2PC，会锁资源，高并发下性能差。

---

## 四、@GlobalTransactional 注解用法

```java
@Service
public class OrderService {

    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private InventoryFeignClient inventoryClient;
    @Autowired
    private PointsFeignClient pointsClient;

    /**
     * 下单全链路：创建订单 + 扣库存 + 送积分
     * 任意一步失败，全局回滚
     */
    @GlobalTransactional(
        name = "mall-create-order",
        rollbackFor = Exception.class,
        timeoutMills = 30000           // 全局事务超时 30s
    )
    @Transactional(rollbackFor = Exception.class)  // 本地事务
    public Order createOrder(CreateOrderRequest request) {
        // 1. 创建订单
        Order order = new Order();
        order.setUserId(request.getUserId());
        order.setSkuId(request.getSkuId());
        order.setQuantity(request.getQuantity());
        order.setStatus(OrderStatus.PENDING_PAYMENT);
        orderMapper.insert(order);

        // 2. 远程调用扣减库存（RM 参与全局事务）
        inventoryClient.deduct(request.getSkuId(), request.getQuantity());

        // 3. 远程调用增加积分（RM 参与全局事务）
        pointsClient.addPoints(request.getUserId(), request.getQuantity() * 10);

        // 4. 如果 2 或 3 失败，Seata 自动回滚并清理 undo_log
        return order;
    }
}
```

**执行流程**：

1. TM（订单服务）向 TC 发起全局事务，获取 `xid`；
2. 订单服务本地事务执行，生成 undo_log，提交本地事务（此时数据已改变）；
3. 远程调用库存服务时，Seata 代理自动把 `xid` 传播过去，库存服务 RM 加入全局事务；
4. 同理积分服务 RM 加入；
5. 全部成功 → TC 通知各 RM 异步清理 undo_log（全局提交）；
6. 任一失败 → TC 通知各 RM 用 undo_log 回滚数据（全局回滚）。

---

## 五、实战：订单+库存+积分完整链路

### 5.1 关键配置

```yaml
seata:
  enabled: true
  application-id: order-service
  tx-service-group: mall_tx_group
  config:
    type: nacos
    nacos:
      server-addr: 127.0.0.1:8848
      data-id: seata.properties
  registry:
    type: nacos
    nacos:
      server-addr: 127.0.0.1:8848
      group: SEATA_GROUP
```

### 5.2 库存服务接口

```java
@FeignClient(name = "inventory-service")
public interface InventoryFeignClient {

    /**
     * 扣减库存
     * 注意：feign 调用时 Seata 通过 xid 传递上下文
     */
    @PostMapping("/api/inventory/deduct")
    Void deduct(@RequestParam Long skuId, @RequestParam Integer quantity);
}
```

### 5.3 异常测试

```java
@SpringBootTest
class SeataOrderTest {

    @Autowired
    private OrderService orderService;

    @Test
    void testOrderRollback() {
        // 模拟：库存扣减故意抛异常（如库存不足）
        // 期望：订单表没有新增记录，积分没有增加
        assertThrows(BizException.class, () -> {
            orderService.createOrder(new CreateOrderRequest(100L, 99999L, 1));
        });
        // 验证：订单表无记录、库存恢复、积分无变化 → 事务回滚成功
    }
}
```

---

## 六、面试要点

| 问题 | 一句话答案 |
|------|-----------|
| 分布式事务的核心矛盾？ | 业务需要强一致，微服务间只能做最终一致 |
| AT 模式的 beforeImage/afterImage 做什么？ | 记录执行 SQL 前后的数据快照，用于回滚时恢复数据 |
| undo_log 表存什么？ | 序列化后的前镜像和后镜像，回滚时根据前镜像构造反向 SQL |
| AT 和 TCC 怎么选？ | AT 侵入低，适合大多数场景；TCC 适合资源预留（如库存冻结） |
| Saga 适用什么场景？ | 长事务、审批链、不确定执行时间的流程 |
| @GlobalTransactional 做了什么？ | 注册全局事务、传播 xid、协调各分支事务提交或回滚 |
| Seata 能保证强一致吗？ | 最终一致，AT 用全局锁 + 回滚日志尽量逼近强一致；XA 才是强一致 |
| 脏写怎么处理？ | 回滚时校验后镜像，不一致则记录异常、人工介入 |