# 04 · 订单服务与 Seata 分布式事务：下单、扣库存、清购物车

> 一次下单涉及订单服务、商品服务（扣库存）、购物车服务（清空购物车）三个独立服务。Seata AT 模式保证"要么全部成功，要么全部回滚"。
>
> **对应项目：** `mall-services/mall-order-service`

---

## 一、基础概念

### 1.1 分布式事务的挑战

```
下单操作涉及的服务：
┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
│  mall-order      │     │  mall-product    │     │  mall-cart       │
│  订单服务         │     │  商品服务         │     │  购物车服务       │
│                   │     │                   │     │                   │
│  1. 保存订单      │───→│  2. 扣减库存      │───→│  3. 清空购物车    │
│  4. 发消息        │     │                   │     │                   │
└─────────────────┘     └─────────────────┘     └─────────────────┘

如果步骤 2 成功、步骤 3 失败：
  → 库存扣了，购物车没清 → 脏数据
  → 需要回滚步骤 2 的库存
```

### 1.2 Seata AT 模式（Automatic Transaction）

| 角色 | 说明 | 类比 |
|------|------|------|
| **TC** (Transaction Coordinator) | 事务协调器，独立部署 | 交警 |
| **TM** (Transaction Manager) | 事务管理器，@GlobalTransactional 注解 | 指挥官 |
| **RM** (Resource Manager) | 资源管理器，管理本地事务 | 士兵 |

---

## 二、进阶机制

### 2.1 项目中的 Seata 使用

```java
@Service
@GlobalTransactional  // 类级别：此类所有方法都受 Seata 管理
public class OrderServiceImpl implements IOrderService {

    @Autowired
    private IOrderInfoService orderInfoService;
    @Autowired
    private IOrderItemsService orderItemsService;
    @Autowired
    private SkuInfoFeignClient skuInfoFeignClient;  // Feign 调用扣库存
    @Autowired
    private CartFeignClient cartFeignClient;         // Feign 调用清购物车
    @Autowired
    private IMessageSendService messageSendService;  // 发送 RocketMQ 消息

    @Override
    @Transactional  // 本地事务
    public void create(OrderCreateDTO orderCreateDTO) throws Exception {
        // 1. 生成分布式 ID (雪花算法)
        String orderId = IdWorker.getIdStr();

        // 2. 保存订单 + 订单明细
        orderInfoService.save(orderInfo);
        orderItemsService.saveBatch(orderItemsList);

        // 3. Feign 调用商品服务扣减库存 (远程事务分支)
        Result<Void> deductResult = skuInfoFeignClient.deductStock(deductDTO);
        if (!deductResult.isSuccess()) {
            throw new BusinessException("扣减库存失败");
        }

        // 4. Feign 调用购物车服务清空购物车 (远程事务分支)
        cartFeignClient.clearCart(userId, cartIds);

        // 5. 发送 RocketMQ 消息 (支付状态异步处理)
        messageSendService.sendOrderMessage(orderInfo);
    }
}
```

**Seata AT 模式执行流程：**

```
1. TM 开启全局事务 → 注册到 TC
2. 第一个 RM (订单服务): 执行本地事务，生成 undo log，注册分支事务
3. 第二个 RM (商品服务): Feign 调用扣库存，执行本地事务，生成 undo log
4. 第三个 RM (购物车服务): Feign 调用清购物车，执行本地事务，生成 undo log
5. TM 提交全局事务 → TC 协调所有 RM 提交
   如果任一 RM 失败 → TC 协调所有 RM 根据 undo log 回滚
```

### 2.2 雪花算法生成分布式 ID

```java
// MyBatis-Plus 的 IdWorker
String orderId = IdWorker.getIdStr();  // 基于雪花算法的分布式唯一 ID
```

**雪花算法：** 64 位 Long 型 ID，由 1 位符号位 + 41 位时间戳 + 10 位工作机器 ID + 12 位序列号组成。单机每秒可生成 409.6 万个 ID，全局唯一、趋势递增。

---

## 三、面试要点

### Q1: Seata AT 模式和 TCC 模式的区别？

**回答思路：** AT 模式自动生成 undo log，对业务代码无侵入，开发者只需加 `@GlobalTransactional` 注解。TCC 模式需要业务代码实现 Try-Confirm-Cancel 三个接口，侵入性强但灵活性更高。AT 适用于大多数场景，TCC 适用于需要精细控制回滚逻辑的场景。

### Q2: 下单流程中，如果 Feign 调用扣库存超时了怎么办？

**回答思路：** Seata 会根据超时配置回滚整个全局事务。Feign 层面也有超时配置（connectTimeout + readTimeout），加上 Sentinel 熔断降级，防止雪崩。项目中的 `ProductFallbackFactory` 在 Feign 调用失败时返回 `REMOTE_CALL_FAIL` 错误，触发 Seata 回滚。

### Q3: 为什么需要雪花算法生成分布式 ID？

**回答思路：** 分布式环境下，数据库自增 ID 无法保证全局唯一（多个服务实例各自生成）。雪花算法生成 64 位 Long 型 ID，全局唯一、趋势递增、高性能（单机 400 万+/秒），适合分布式订单号生成。

---

> **下一篇：** [05-CART-REDIS.md —— 购物车服务与 Redis 缓存设计](./05-CART-REDIS.md)
>
> 购物车为什么用 Redis 存？Hash 结构为什么比 String 更合适？