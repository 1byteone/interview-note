# 09 · RocketMQ 消息驱动：订单支付回调、库存同步、搜索索引更新

> 异步解耦是微服务架构的核心设计原则。RocketMQ 在项目中承担了订单支付回调、库存同步、搜索索引更新等关键链路的消息传递职责。
>
> **对应项目：** `mall-services/mall-order-service` + `mall-services/mall-pay-service` + `mall-services/mall-consumer-service`

---

## 一、基础概念

### 1.1 消息队列在微服务中的角色

```
下单 → 扣库存 → 清购物车 → 生成订单 → 返回"订单创建成功"
                                 ↓ 异步
                          支付 → 更新订单状态 → 发送 RocketMQ
                                              ├── 清空购物车 (DeleteCartHandler)
                                              ├── 更新订单状态 (UpdateOrderHandler)
                                              ├── 同步 ES 索引 (SyncDataToEsHandler)
                                              └── 超时回滚 (OrderRecoveryHandler)
```

**同步 vs 异步：** 下单流程中，扣库存、清购物车等核心操作是**同步**的（通过 Seata 保证一致性）。支付成功后的"更新搜索索引""清理购物车"等**非核心操作**通过 RocketMQ 异步处理，不阻塞支付回调。

### 1.2 消息发送 —— StreamBridge

```java
@Service
public class MessageSendServiceImpl implements IMessageSendService {

    @Autowired
    private StreamBridge streamBridge;

    // 发送订单消息
    public void sendOrderMessage(OrderInfo orderInfo) {
        // 将订单信息转为消息
        String message = JsonUtils.toJson(orderInfo);
        // 通过 StreamBridge 发送到 "orderOutput-out-0" 通道
        streamBridge.send("orderOutput-out-0", message);
    }
}
```

---

## 二、进阶机制

### 2.1 消息消费方 —— mall-consumer-service

项目中有 4 个 Handler 处理不同消息：

```java
// 1. 支付成功 → 清空购物车
@Component
public class DeleteCartHandler {
    @Bean
    public Consumer<String> deleteCart() {
        return message -> {
            // 解析消息
            // Feign 调用购物车服务删除已购商品
            cartFeignClient.removeCart(cartIds);
        };
    }
}

// 2. 支付成功 → 更新订单状态
@Component
public class UpdateOrderHandler {
    @Bean
    public Consumer<String> updateOrder() {
        return message -> {
            // 更新订单状态为"已支付"
            orderInfoService.updateStatus(orderId, "PAID");
        };
    }
}

// 3. 商品变更 → 同步 ES 搜索索引
@Component
public class SyncDataToEsHandler {
    @Bean
    public Consumer<String> syncDataToEs() {
        return message -> {
            // 将商品数据同步到 Elasticsearch 索引
            esService.saveOrUpdate(skuInfo);
        };
    }
}

// 4. 订单超时未支付 → 回滚库存
@Component
public class OrderRecoveryHandler {
    @Bean
    public Consumer<String> orderRecovery() {
        return message -> {
            // 订单超时未支付，恢复库存
            productFeignClient.restoreStock(stockDTO);
            // 更新订单状态为"已取消"
            orderInfoService.updateStatus(orderId, "CANCELLED");
        };
    }
}
```

---

## 三、面试要点

### Q1: 项目中哪些场景用了消息队列？为什么？

**回答思路：** 三个核心场景：1) **支付成功回调**——异步更新订单状态、清空购物车、同步搜索索引；2) **秒杀库存扣减**——Redis 预扣减后异步同步到 MySQL；3) **订单超时回滚**——延迟消息处理超时未支付订单。使用消息队列的核心原因是**异步解耦**——非核心业务不阻塞主流程，同时削峰填谷、保证最终一致性。

### Q2: 如果消息消费失败，怎么保证数据最终一致性？

**回答思路：** 三个层面：1) **RocketMQ 重试机制**——消费失败自动重试，默认 16 次；2) **幂等消费**——消息处理器设计为幂等，重复消费不产生脏数据；3) **补偿机制**——兜底定时任务扫描未处理订单，人工补偿。这是"最多一次 + 幂等 + 补偿"的最终一致性方案。

---

> **下一篇：** [10-ARCHITECTURE.md —— 架构复盘与面试题集](./10-ARCHITECTURE.md)
>
> 全链路复盘，12 个微服务的技术栈横向对比，以及 20+ 面试高频题与回答思路。