# 01 · Java 微服务设计与实现

> 本文深入 AI 商城的 Java 微服务集群，展示每个业务服务的核心设计、表结构、缓存策略和异步消息模式。这是整个贯穿项目中 Java 技术栈（Spring Boot, MySQL, Redis, RocketMQ, ES）的集中实践。

---

## 一、微服务总览

AI 商城采用 **Spring Cloud Alibaba** 微服务架构，8 个核心业务服务 + 3 个辅助服务，通过 Nacos 注册发现，Gateway 统一网关，Sentinel 限流熔断，Seata 分布式事务。

```
┌──────────────────────────────────────────────────────────────────┐
│                    Spring Cloud Gateway (8080)                    │
│  /api/mall/user/**  /api/mall/order/**  /api/mall/product/**   │
└──────────────────────────────────────────────────────────────────┘
         │                │                │
         ▼                ▼                ▼
┌──────────────┐  ┌──────────────┐  ┌──────────────┐
│ mall-user     │  │ mall-order   │  │ mall-product  │
│ 8081          │  │ 8083         │  │ 8082          │
├──────────────┤  ├──────────────┤  ├──────────────┤
│ JWT 鉴权      │  │ Seata 事务   │  │ 分类/品牌管理  │
│ 用户信息 CRUD  │  │ 订单状态机   │  │ 商品 CRUD     │
│ Redis 缓存    │  │ RocketMQ 消息│  │ 布隆过滤器    │
└──────────────┘  └──────────────┘  └──────────────┘
```

---

## 二、mall-user-service（用户服务）

### 2.1 核心表设计

```sql
-- 用户表
CREATE TABLE `user` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `username` varchar(50) NOT NULL COMMENT '用户名',
  `password` varchar(128) NOT NULL COMMENT '加密密码(bcrypt)',
  `phone` varchar(20) DEFAULT NULL COMMENT '手机号',
  `email` varchar(100) DEFAULT NULL COMMENT '邮箱',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态 1正常 0禁用',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`),
  KEY `idx_phone` (`phone`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- 收货地址表
CREATE TABLE `user_address` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `name` varchar(50) NOT NULL COMMENT '收件人',
  `phone` varchar(20) NOT NULL COMMENT '联系电话',
  `province` varchar(50) NOT NULL COMMENT '省',
  `city` varchar(50) NOT NULL COMMENT '市',
  `district` varchar(50) NOT NULL COMMENT '区',
  `detail` varchar(200) NOT NULL COMMENT '详细地址',
  `is_default` tinyint NOT NULL DEFAULT 0 COMMENT '是否默认 1是',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='收货地址表';
```

### 2.2 缓存策略

```
用户信息缓存 (Cache-Aside 模式):
  查询: getUser(id) → 查 Redis → 未命中 → 查 MySQL → 回填 Redis
  更新: updateUser(id) → 更新 MySQL → 删除 Redis 缓存

JWT Token 管理:
  生成: 登录成功 → 生成 JWT (2h 过期) → 存入 Redis (key: token:{userId})
  验证: 请求 → 解析 Token → 校验 Redis 中是否存在 → 放行
  刷新: 过期前 → 调用刷新接口 → 生成新 Token
```

### 2.3 Spring Boot 最佳实践应用

```java
@RestController
@RequestMapping("/api/mall/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return Result.success(userService.login(request));
    }

    @GetMapping("/info")
    @LoginRequired  // 自定义注解 + 拦截器实现 JWT 鉴权
    public Result<UserInfo> getUserInfo(@UserId Long userId) {
        return Result.success(userService.getUserInfo(userId));
    }
}

@Service
public class UserService {

    @Cacheable(value = "user", key = "#id", unless = "#result == null")
    public UserInfo getUserInfo(Long id) {
        return userMapper.selectById(id);
    }

    @CacheEvict(value = "user", key = "#id")
    public void updateUser(Long id, UserUpdateRequest request) {
        userMapper.updateById(request.toEntity(id));
    }
}
```

---

## 三、mall-product-service（商品服务）

### 3.1 核心表设计

```sql
-- 商品 SPU 表
CREATE TABLE `spu_info` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `spu_name` varchar(200) NOT NULL COMMENT '商品名称',
  `category_id` bigint NOT NULL COMMENT '分类ID',
  `brand_id` bigint DEFAULT NULL COMMENT '品牌ID',
  `description` text COMMENT '商品描述',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '上架状态 1上架 0下架',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_category` (`category_id`),
  KEY `idx_brand` (`brand_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='SPU表';

-- SKU 表
CREATE TABLE `sku_info` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `spu_id` bigint NOT NULL COMMENT 'SPU ID',
  `sku_name` varchar(200) NOT NULL COMMENT 'SKU名称',
  `sku_attribute` varchar(500) DEFAULT NULL COMMENT 'SKU属性(JSON)',
  `price` decimal(10,2) NOT NULL COMMENT '价格',
  `stock` int NOT NULL DEFAULT 0 COMMENT '库存',
  `sku_default_img` varchar(500) DEFAULT NULL COMMENT '默认图片',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  KEY `idx_spu` (`spu_id`),
  KEY `idx_price` (`price`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='SKU表';
```

### 3.2 布隆过滤器防缓存穿透

```java
@Component
public class CacheBloomFilter {

    private final RedisTemplate<String, Object> redisTemplate;

    // 初始化布隆过滤器（商品ID维度）
    @PostConstruct
    public void init() {
        // 从数据库加载所有商品ID
        List<Long> allProductIds = productMapper.selectAllIds();
        allProductIds.forEach(id ->
            redisTemplate.opsForValue().setBit("bloom:product", id, true)
        );
    }

    // 查询前检查
    public boolean mightContain(Long productId) {
        return Boolean.TRUE.equals(
            redisTemplate.opsForValue().getBit("bloom:product", productId)
        );
    }
}

@Service
public class ProductService {

    public SkuInfo getSkuById(Long skuId) {
        // 布隆过滤器拦截：不存在直接返回
        if (!bloomFilter.mightContain(skuId)) {
            return null;
        }
        // Cache-Aside 查询
        return cacheService.getSku(skuId);
    }
}
```

---

## 四、mall-order-service（订单服务）

### 4.1 订单状态机

```
订单状态流转:
                      ┌─────────────┐
                      │  待支付      │
                      │  (PENDING)   │
                      └──────┬──────┘
                             │
              ┌──────────────┼──────────────┐
              │              │              │
              ▼              ▼              ▼
        ┌──────────┐  ┌──────────┐  ┌──────────┐
        │ 已支付    │  │ 已取消    │  │ 已超时    │
        │(PAID)    │  │(CANCELED) │  │(TIMEOUT)  │
        └────┬─────┘  └──────────┘  └──────────┘
             │
             ▼
        ┌──────────┐
        │ 已发货    │
        │(SHIPPED)  │
        └────┬─────┘
             │
             ▼
        ┌──────────┐
        │ 已收货    │
        │(RECEIVED) │
        └────┬─────┘
             │
             ▼
        ┌──────────┐
        │ 已完成    │
        │(COMPLETED)│
        └──────────┘
```

### 4.2 RocketMQ 异步消息

```java
@Service
public class OrderService {

    // 创建订单
    @GlobalTransactional  // Seata 分布式事务
    public OrderCreateResponse createOrder(OrderCreateRequest request) {
        // 1. 参数校验（价格、库存）
        // 2. 扣减库存（调用 inventory-service）
        // 3. 创建订单（状态=待支付）
        // 4. 发送延迟消息（30分钟后检查支付状态）
        sendDelayMessage(order.getId(), 30);  // 30 分钟
        // 5. Seata 全局提交
        return response;
    }

    // 支付回调
    @Transactional
    public void payCallback(PayCallbackRequest request) {
        // 幂等校验
        Boolean acquired = redisTemplate.opsForValue()
            .setIfAbsent("pay:callback:" + request.getTransactionId(), "1", 30, TimeUnit.SECONDS);
        if (!Boolean.TRUE.equals(acquired)) {
            log.info("重复支付回调: {}", request.getTransactionId());
            return;
        }
        // 更新订单状态
        orderMapper.updateStatus(request.getOrderId(), OrderStatus.PAID);
        // 发送支付成功消息
        rocketMQTemplate.send("order-paid", MessageBuilder.withPayload(orderId).build());
    }

    // 延迟消息消费（订单超时取消）
    @RocketMQMessageListener(topic = "order-timeout", consumerGroup = "order-timeout-group")
    public void onTimeout(String orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order.getStatus() == OrderStatus.PENDING) {
            orderMapper.updateStatus(orderId, OrderStatus.TIMEOUT);
            // 恢复库存（调用 inventory-service）
            restoreStock(order);
        }
    }
}
```

---

## 五、mall-seckill-service（秒杀服务）

### 5.1 三层库存扣减

```
用户抢购请求
    │
    ▼
┌──────────────────────────────────────────────────────────────────────┐
│  Layer 1: Redis 预扣（扛高并发）                                       │
│  Lua 脚本: if redis.call('get', key) - quantity >= 0                  │
│            then redis.call('decrby', key, quantity) return 1          │
│            else return 0 end                                          │
│  QPS: ~10w/实例                                                      │
└──────────────────────────────────────────────────────────────────────┘
    │ 预扣成功
    ▼
┌──────────────────────────────────────────────────────────────────────┐
│  Layer 2: RocketMQ 异步削峰                                          │
│  发送消息 → 异步消费 → 写入库存流水表                                   │
│  目的：防止 MySQL 被打垮                                               │
└──────────────────────────────────────────────────────────────────────┘
    │ 消息消费
    ▼
┌──────────────────────────────────────────────────────────────────────┐
│  Layer 3: 乐观锁兜底（保证最终一致性）                                  │
│  UPDATE sku_info SET stock = stock - #{quantity},                     │
│         before_stock = stock                                         │
│  WHERE id = #{skuId} AND stock >= #{quantity}                        │
│  目的：防止 Redis 和 MySQL 数据不一致                                  │
└──────────────────────────────────────────────────────────────────────┘
```

### 5.2 幂等消费设计

```java
@Component
@RocketMQMessageListener(topic = "seckill-stock", consumerGroup = "seckill-stock-group")
public class StockDeductConsumer implements RocketMQListener<StockDeductMessage> {

    @Override
    public void onMessage(StockDeductMessage message) {
        // 幂等校验：Redis 30s 锁
        String lockKey = "que:lock:stock:" + message.getTransactionId();
        Boolean acquired = redisTemplate.opsForValue()
            .setIfAbsent(lockKey, "1", 30, TimeUnit.SECONDS);
        if (!Boolean.TRUE.equals(acquired)) {
            log.debug("重复消息跳过: {}", message.getTransactionId());
            return;
        }

        // 乐观锁更新数据库
        stockDeductMapper.deductStock(message.getSkuId(), message.getQuantity());
    }
}
```

---

## 六、mall-es-service（搜索服务）

### 6.1 ES 索引设计

```json
{
  "mappings": {
    "properties": {
      "skuName": { "type": "text", "analyzer": "ik_max_word" },
      "skuAttribute": { "type": "text", "analyzer": "ik_smart" },
      "brandName": { "type": "keyword" },
      "categoryName": { "type": "keyword" },
      "price": { "type": "double" },
      "skuDefaultImg": { "type": "keyword", "index": false }
    }
  }
}
```

### 6.2 MySQL → ES 数据同步

```
当前方案（业务双写适用于小规模）:
  商品服务更新 MySQL → 调用 ES 服务写入 → 双写保证

改进方案（Canal 监听 binlog，适用于大规模）:
  MySQL 变更 → Canal 监听 binlog → RocketMQ 消息 → ES 服务消费写入
  优点：解耦，不侵入业务代码
```

---

> **下一篇：** [02-distributed-system.md](02-distributed-system.md) — 分布式系统组件