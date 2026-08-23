# 02 · 分布式系统组件

> 本文介绍 AI 商城中的分布式系统核心组件：Nacos 注册中心、Gateway 网关、Sentinel 限流、Seata 分布式事务。这些组件构成了微服务架构的"骨架"，保证服务间的通信、容错和数据一致性。

---

## 一、Nacos 注册中心与配置中心

### 1.1 服务注册与发现

```
服务启动流程:
  mall-user-service
    → 向 Nacos 注册 (ip:port + 健康检查 URL)
    → Nacos 维护服务列表 + 心跳检测 (5s)
    → 其他服务通过 OpenFeign + 服务名 调用

Nacos 架构:
  ┌──────────────────────────────────────────────────────────────┐
  │                       Nacos 集群 (3 节点)                     │
  │  Leader: 192.168.1.1:8848                                   │
  │  Follower: 192.168.1.2:8848, 192.168.1.3:8848               │
  │  Raft 协议保证一致性                                           │
  └──────────────────────────────────────────────────────────────┘
           │                   │                   │
           ▼                   ▼                   ▼
  ┌──────────────┐   ┌──────────────┐   ┌──────────────┐
  │ mall-user     │   │ mall-order   │   │ mall-product  │
  │ 注册 → Nacos  │   │ 发现 → user  │   │ 发现 → order  │
  └──────────────┘   └──────────────┘   └──────────────┘
```

### 1.2 配置中心

```yaml
# Nacos 配置中心配置
spring:
  cloud:
    nacos:
      server-addr: ${NACOS_HOST:192.168.150.101}:8848
      config:
        namespace: ${spring.profiles.active:dev}
        file-extension: yaml
        refresh-enabled: true
      discovery:
        namespace: ${spring.profiles.active:dev}
```

**配置动态刷新原理：**

```
Nacos 客户端发起长轮询 (Long Polling)
  → 首次拉取全量配置
  → 后续只请求变更的配置 dataId + group
  → Nacos 服务端检测到变更 → 立即返回
  → 客户端收到变更通知 → 重新拉取配置
  → @RefreshScope 标记的 Bean 自动刷新
```

**Nacos 宕机不影响运行中的服务：**
- 客户端本地缓存配置快照（`snapshot` 目录）
- 服务注册信息本地缓存，服务间调用不受影响
- 新服务启动无法注册，但已运行的服务正常通信

---

## 二、Spring Cloud Gateway 网关

### 2.1 路由配置

```yaml
spring:
  cloud:
    gateway:
      routes:
        # Java 微服务路由
        - id: mall-user
          uri: lb://mall-user-service
          predicates:
            - Path=/api/mall/user/**
          filters:
            - StripPrefix=1
            - name: RequestRateLimiter
              args:
                key-resolver: "#{@userKeyResolver}"
                redis-rate-limiter:
                  replenishRate: 100
                  burstCapacity: 200

        - id: mall-order
          uri: lb://mall-order-service
          predicates:
            - Path=/api/mall/order/**
          filters:
            - StripPrefix=1

        - id: mall-product
          uri: lb://mall-product-service
          predicates:
            - Path=/api/mall/product/**
          filters:
            - StripPrefix=1

        # Python AI 服务路由
        - id: ai-search
          uri: lb://ai-search-gateway
          predicates:
            - Path=/api/ai/search/**
          filters:
            - StripPrefix=2
            - name: CircuitBreaker
              args:
                name: aiSearchCircuitBreaker
                fallbackUri: forward:/fallback/ai-search

        - id: ai-rag
          uri: lb://ai-rag-service
          predicates:
            - Path=/api/ai/rag/**
          filters:
            - StripPrefix=2
```

### 2.2 统一鉴权

```java
@Component
public class AuthorizationFilter implements GlobalFilter, Ordered {

    private final List<String> whiteList = Arrays.asList(
        "/api/mall/user/login",
        "/api/mall/user/register",
        "/api/mall/product/list",
        "/api/ai/search/public"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        // 白名单放行
        if (whiteList.stream().anyMatch(path::startsWith)) {
            return chain.filter(exchange);
        }

        // JWT 校验
        String token = exchange.getRequest().getHeaders()
            .getFirst("Authorization");
        if (token == null || !token.startsWith("Bearer ")) {
            return unauthorized(exchange, "未登录");
        }

        try {
            Claims claims = JwtUtil.parseToken(token.substring(7));
            // 将用户信息传递到下游服务
            ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                .header("X-User-Id", claims.get("userId").toString())
                .build();
            return chain.filter(exchange.mutate().request(mutatedRequest).build());
        } catch (Exception e) {
            return unauthorized(exchange, "Token 无效或已过期");
        }
    }
}
```

---

## 三、Sentinel 限流熔断

### 3.1 流控规则

```
网关级别限流:
  API: /api/mall/order/**
  阈值: 100 QPS
  限流效果: 超出返回 429 Too Many Requests

服务级别限流:
  资源: com.mall.order.service.OrderService.createOrder
  阈值: 50 QPS
  限流模式: 直接拒绝（秒杀场景可改为排队等待）

熔断规则:
  资源: mall-inventory-service (Feign 调用)
  阈值: 异常比例 > 50%
  熔断时长: 10 秒
  半开探测: 5 秒后尝试恢复
```

### 3.2 Sentinel + OpenFeign 集成

```java
// API 接口定义
@FeignClient(
    name = "mall-inventory-service",
    fallbackFactory = InventoryFallbackFactory.class
)
public interface InventoryClient {

    @GetMapping("/api/inventory/deduct")
    Result<Void> deductStock(@RequestParam("skuId") Long skuId,
                             @RequestParam("quantity") Integer quantity);
}

// 熔断降级处理
@Component
public class InventoryFallbackFactory implements FallbackFactory<InventoryClient> {

    @Override
    public InventoryClient create(Throwable cause) {
        return (skuId, quantity) -> {
            log.error("库存服务不可用，降级处理: {}", cause.getMessage());
            // 降级策略：返回库存充足标记，由后续流程兜底
            return Result.error("库存服务暂不可用，请稍后重试");
        };
    }
}
```

### 3.3 热点规则

```java
// 秒杀商品 ID 热点参数限流
@SentinelResource(value = "seckill-product", blockHandler = "blockHandler")
public Result seckill(@RequestParam Long productId) {
    // 秒杀逻辑
}

public Result blockHandler(Long productId, BlockException e) {
    return Result.error("当前购买人数过多，请稍后重试");
}
```

---

## 四、Seata 分布式事务

### 4.1 适用场景

```
下单流程中的分布式事务:

  mall-order-service                      mall-inventory-service
  ┌──────────────────┐                   ┌──────────────────────┐
  │ 1. 创建订单(待支付) │                   │ 1. 扣减库存           │
  │ 2. 锁定优惠券      │  Feign 调用       │ 2. 写入库存流水       │
  │ 3. 扣减积分       │──────────────────→│ 3. 返回结果           │
  └──────────────────┘                   └──────────────────────┘
           │                                        │
           └──────────────────┬─────────────────────┘
                              │
                    Seata TC (Transaction Coordinator)
                    全局事务 ID: 192.168.1.1:8091:123456
                    一阶段: 业务 SQL + undo_log 写入
                    二阶段: 全局提交 → 删除 undo_log
                          全局回滚 → 根据 undo_log 恢复数据
```

### 4.2 Seata AT 模式原理

```
一阶段 (业务执行):
  1. 执行业务 SQL (INSERT/UPDATE/DELETE)
  2. 自动生成 beforeImage (执行前数据快照)
  3. 自动生成 afterImage (执行后数据快照)
  4. 将 beforeImage + afterImage 写入 undo_log 表
  5. 业务 SQL 直接提交到数据库

二阶段 (全局提交):
  1. TC 通知所有参与者提交
  2. 删除 undo_log 记录
  3. 完成

二阶段 (全局回滚):
  1. TC 通知所有参与者回滚
  2. 读取 undo_log 中的 beforeImage
  3. 生成反向 SQL 恢复数据
  4. 删除 undo_log 记录
```

### 4.3 配置方式

```yaml
# 订单服务配置
seata:
  enabled: true
  application-id: mall-order-service
  tx-service-group: mall-tx-group
  service:
    vgroup-mapping:
      mall-tx-group: default
    grouplist:
      default: 192.168.150.101:8091
  registry:
    type: nacos
    nacos:
      server-addr: 192.168.150.101:8848
```

```java
@RestController
@RequestMapping("/api/order")
public class OrderController {

    @PostMapping("/create")
    @GlobalTransactional(name = "create-order", rollbackFor = Exception.class)
    public Result<OrderCreateResponse> createOrder(@Valid @RequestBody OrderCreateRequest request) {
        // 1. 扣减库存 (Feign 调用 inventory-service)
        inventoryClient.deductStock(request.getSkuId(), request.getQuantity());

        // 2. 创建订单
        Order order = orderService.createOrder(request);

        // 3. 发送延迟消息
        rocketMQTemplate.syncSend("order-timeout", order.getId(), 30 * 60 * 1000);

        return Result.success(OrderCreateResponse.from(order));
    }
}
```

---

## 五、总结：分布式组件对照表

| 组件 | 角色 | 功能 | 容错机制 |
|------|------|------|---------|
| **Nacos** | 注册中心 + 配置中心 | 服务发现、配置管理 | 本地缓存快照，宕机不影响运行中服务 |
| **Gateway** | 统一网关 | 路由分发、鉴权、限流 | AI 服务降级到 fallback 兜底 |
| **Sentinel** | 限流熔断 | 流量控制、熔断降级 | 熔断 -> 半开探测 -> 恢复 |
| **Seata** | 分布式事务 | 跨服务事务一致性 | AT 模式自动回滚 |
| **OpenFeign** | 服务间调用 | 声明式 HTTP 调用 | 集成 Sentinel 熔断降级 |

---

> **下一篇：** [../03-ai-services/01-ai-search.md](../03-ai-services/01-ai-search.md) — AI 搜索服务