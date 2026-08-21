# mall-micro-cloud 项目深度面试分析

> 项目路径：`D:\code\codeClaudeCode\demo-practicalTrainingProject\mall-ai\mall-micro-cloud`
> 作者：Yjson | 生成时间：2026-08-20

---

## 📊 项目全景分析

### 项目架构

```
mall-micro-cloud (Spring Boot 3.3.2 + Spring Cloud 2023.0.1 + Alibaba 2023.0.1.0)
│
├── mall-gateway          → Spring Cloud Gateway + Nacos 路由 → 统一入口
├── mall-common           → 公共模块：JWT/Redis/MyBatis-Plus/全局异常/布隆过滤器
├── mall-api              → OpenFeign 接口定义 + Sentinel 熔断
├── mall-services         → 业务服务聚合
│   ├── mall-user-service     → 用户服务
│   ├── mall-product-service  → 商品服务 + Seata 分布式事务
│   ├── mall-order-service    → 订单服务 + Seata 分布式事务 + RocketMQ
│   ├── mall-cart-service     → 购物车服务（MongoDB 存储）
│   ├── mall-seckill-service  → 秒杀服务（Redis 预扣 + RocketMQ 异步 + 乐观锁）
│   ├── mall-es-service       → 搜索服务（Elasticsearch + MySQL 同步）
│   ├── mall-pay-service      → 支付服务（支付宝 SDK）
│   ├── mall-oss-service      → 文件服务（阿里云 OSS）
│   ├── mall-consumer-service → 消息消费服务
│   └── mall-scheduler-service → 定时任务服务（ElasticJob + Zookeeper）
└── mall-exercise          → 练习模块
```

### 技术栈全景

| 分类 | 技术 | 用途 |
|------|------|------|
| 框架 | Spring Boot 3.3.2 + Spring Cloud 2023.0.1 | 基础框架 |
| 注册/配置 | Nacos | 服务发现 + 配置中心 |
| 网关 | Spring Cloud Gateway | 统一路由 + 鉴权 |
| 远程调用 | OpenFeign + LoadBalancer | 服务间调用 |
| 熔断限流 | Sentinel | 流量控制 + 熔断降级 |
| 分布式事务 | Seata | 订单+库存跨服务事务 |
| 消息队列 | RocketMQ | 异步解耦 + 削峰填谷 |
| 缓存 | Redis + Redisson | 秒杀库存 + 分布式锁 |
| 数据库 | MySQL + Druid + MyBatis-Plus | 持久化存储 |
| 搜索 | Elasticsearch | 商品全文搜索 |
| 文档 | Knife4j (OpenAPI3) | API 接口文档 |
| 鉴权 | JWT (jjwt) + 拦截器 | 身份认证 |
| 定时任务 | ElasticJob + Zookeeper | 分布式调度 |
| 对象存储 | 阿里云 OSS | 文件上传 |
| 支付 | 支付宝 SDK | 在线支付 |
| 密码 | jbcrypt | 密码加密 |
| 工具 | Fastjson2 + Lombok | 序列化 + 代码简化 |

---

## 🎯 项目深挖题（面试官必问）

### 第 1 组：秒杀系统设计

#### 题目 1：秒杀库存扣减是怎么设计的？为什么用 Redis + RocketMQ + 乐观锁三层？

**答案**：
```
用户请求 → Redis 预扣库存（Lua/incrBy）
    ↓ 成功 → 发送 RocketMQ 消息（异步）
    ↓
StockDeductConsumer 消费
    ↓ 幂等校验（Redis setIfAbsent + 30s 过期）
    ↓
recordStockFlow() @Transactional
    ├── 1. 写入流水表（SeckillStockFlow，status=0）
    ├── 2. 乐观锁更新 DB 库存（WHERE beforeStock = 旧值）
    └── 3. 更新流水状态（status=1 成功 / 2 失败）
```

**三层目的**：
- **Redis 预扣**：扛住瞬时高并发（10w QPS 级别）
- **RocketMQ 异步**：削峰填谷，防止 MySQL 被打垮
- **乐观锁（DB 层）**：防止 Redis 和 DB 数据不一致，保证最终一致性

**追问**：如果 Redis 扣减成功但 MQ 消息丢失怎么办？
- 答：MQ 生产者同步发送 + 重试机制保证不丢；Redis 库存可设置过期自动回补

**追问**：Redis 和 DB 的数据一致性怎么保证？
- 答：Redis 预扣不保证绝对准确，DB 乐观锁兜底（`WHERE storeCount = beforeStock`），每分钟定时任务同步 Redis 库存到 DB

#### 题目 2：幂等性怎么做的？transactionId 怎么保证不重复消费？

**答案**：
```java
// 幂等 key：que:lock:stock:{transactionId}
String lockKey = "que:lock:stock:" + dto.getTransactionId();
Boolean acquired = redisTemplate.opsForValue().setIfAbsent(lockKey, "1", 30, TimeUnit.SECONDS);
if (!Boolean.TRUE.equals(acquired)) {
    log.debug("重复消息跳过: {}", dto.getTransactionId());
    return;
}
```

**追问**：Redis 挂了怎么办？幂等性还成立吗？
- 答：Redis 宕机 → 幂等失效 → 重复消费 → 乐观锁拦截（`WHERE beforeStock = 旧值` 更新失败）+ 流水表生成重复记录（status=2）

**追问**：为什么不用数据库唯一键做幂等？
- 答：Redis 性能更高，30s 窗口足够覆盖 RocketMQ 重试间隔

---

### 第 2 组：分布式事务

#### 题目 3：Seata 用在哪些地方？为什么选 AT 模式？

**答案**：
- **mall-product-service** 和 **mall-order-service** 引入了 `spring-cloud-starter-alibaba-seata`
- 典型场景：下单扣库存跨服务事务

**为什么选 AT**：
- 对业务代码侵入最小（自动生成镜像、自动回滚）
- 性能比 XA 高，实现比 TCC 简单
- 秒杀场景已经通过 MQ 解耦，Seata 主要用于非秒杀的下单流程

**追问**：Seata AT 模式的原理？
- 答：一阶段：业务 SQL 直接提交 + 生成 beforeImage/afterImage 写入 undo_log
- 二阶段：全局提交 → 删除 undo_log；全局回滚 → 根据 undo_log 生成反向 SQL 恢复数据

#### 题目 4：为什么不用 TCC 或 Saga？

**答案**：
- **TCC**：需要手写 Try/Confirm/Cancel 三个接口，代码侵入大
- **Saga**：适合长事务（如跨多服务编排），当前场景只是 2-3 个服务，AT 足够
- 当秒杀场景的最终一致性已经是 MQ 异步 + 乐观锁方案，不需要强一致事务

---

### 第 3 组：微服务架构

#### 题目 5：Nacos 的配置中心怎么用的？配置如何动态刷新？

**答案**：
```yaml
spring:
  cloud:
    nacos:
      server-addr: 192.168.150.101:8848
      config:
        namespace: ${spring.profiles.active:public}
```

- 配置按环境隔离（dev/prod 不同 namespace）
- 配置变更通过 Nacos 长轮询实时推送
- `@RefreshScope` 注解标记的 Bean 自动刷新

**追问**：Nacos 挂掉会影响服务运行吗？
- 答：客户端有本地缓存（`snapshot` 目录），配置值不会丢失；服务注册信息也缓存到本地，不影响运行中的服务调用

#### 题目 6：Gateway 怎么配置的？路由规则？

**答案**：
```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: mall-seckill
          uri: lb://mall-seckill-service
          predicates:
            - Path=/api/seckill/**
```

- 使用 `lb://` 前缀集成 Nacos 服务发现
- 结合 Sentinel 做网关级别限流
- 结合 AuthorizationInterceptor 做统一鉴权

#### 题目 7：OpenFeign + Sentinel 的降级怎么配置的？

**答案**：
```java
@FeignClient(
    name = "mall-order-service",
    fallbackFactory = OrderFallbackFactory.class
)
public interface OrderClient {
    @GetMapping("/order/{id}")
    Result<OrderDTO> getOrder(@PathVariable("id") Long id);
}
```

- Sentinel 监控 Feign 调用的 QPS/异常比例
- 达到阈值触发熔断 → 调用 FallbackFactory 返回默认值
- 熔断恢复：半开探测 → 成功则关闭熔断

---

### 第 4 组：中间件深度

#### 题目 8：Redis 缓存用到了哪些场景？缓存策略是什么？

**答案**：
- **秒杀库存预扣**：`seckill:stock:{activityId}:{skuId}`
- **分布式锁**：Redisson + WatchDog
- **幂等校验**：`que:lock:stock:{transactionId}`（30s 过期）
- **商品缓存**：@Cacheable + @CacheEvict 注解

**缓存策略**：
- 秒杀库存：Redis 做主存储，DB 做最终持久化
- 商品信息：Cache-Aside 模式（先查缓存 → 未命中查 DB → 回填缓存）
- 热点数据：@Cacheable 注解 + Spring Cache 抽象

**追问**：秒杀时 Redis 库存怎么预热？
- 答：活动开始前，定时任务将 DB 库存数加载到 Redis，`SET seckill:stock:1:100 10000`

#### 题目 9：Elasticsearch 商品搜索怎么设计的？MySQL 怎么同步到 ES？

**答案**：
- **mall-es-service** 提供搜索 API
- 商品数据存储在 MySQL（`SkuInfo`），通过业务代码同步到 ES
- 搜索接口：关键词匹配 + 分页 + 排序

**追问**：不做全量同步？用什么方案？
- 答：当前用业务双写（MySQL 写完后调用 ES 接口写入），适合当前数据量
- 数据量大后建议改用 Canal 监听 MySQL binlog → RocketMQ → ES 写入

#### 题目 10：ElasticJob 定时任务怎么用的？为什么用 Zookeeper？

**答案**：
- **mall-scheduler-service** 引入 `elasticjob-lite-spring-boot-starter` + `curator-recipes`
- 典型任务：秒杀活动状态变更、库存同步、订单超时取消
- Zookeeper 做分布式协调，保证任务分片不重复执行

**追问**：为什么不直接用 @Scheduled？
- 答：@Scheduled 在多实例下每个节点都执行，会导致重复；ElasticJob 通过 Zookeeper 做分片，每个任务只在一个节点执行

---

### 第 5 组：安全与设计

#### 题目 11：JWT 鉴权怎么实现的？

**答案**：
- `JwtUtil.java`：生成和验证 JWT Token（jjwt 库）
- `LoginInterceptor.java`：从请求头提取 Token，解析用户信息
- `AuthorizationInterceptor.java`：权限校验
- `@Login4j` 注解：标记需要登录的接口

**追问**：Token 过期怎么处理？刷新机制？
- 答：JWT 设置合理过期时间（如 2h），前端在过期前调用刷新接口获取新 Token

#### 题目 12：全局异常处理怎么设计的？

**答案**：
```java
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(BusinessException.class) → 业务异常（自定义 code + msg）
    @ExceptionHandler(Exception.class) → 系统异常（500 + 友好提示）
}
```

- `GlobalResponseAdvice`：统一响应体包装（`Result<T>` 格式）
- 所有异常统一输出格式：`{ code, message, data, timestamp }`

#### 题目 13：布隆过滤器（CacheBloomFilter）用在什么地方？

**答案**：
- 用于**缓存穿透**防护
- 在查询商品前先检查布隆过滤器，不存在直接返回，避免 DB 被打垮
- 布隆过滤器存储在 Redis 中（`BloomFilter`）

---

## 🎯 场景设计题（面试必考）

### 场景 1：商品搜索响应慢，怎么优化？

**要求**：结合 ES 服务、缓存、数据库

**方案**：
1. **ES 索引优化**：合理设置分片数（数据量/30GB）、IK 分词器
2. **缓存热门搜索结果**：Redis 缓存 Top N 搜索词的结果（TTL 60s）
3. **搜索结果缓存**：`@Cacheable` 缓存商品搜索结果
4. **分页优化**：使用 `search_after` 代替深分页

### 场景 2：订单超时未支付，怎么取消？

**要求**：结合 RocketMQ 延迟消息或定时任务

**方案**：
1. RocketMQ 延迟消息：下单时发送延迟消息（30分钟），消费时检查订单状态
2. 定时任务兜底：ElasticJob 每分钟扫描超时订单
3. 状态机确保只处理一次

### 场景 3：购物车数据量大，怎么设计？

**要求**：结合 MongoDB 特性

**答案**：
- 当前使用 MongoDB 存储购物车数据（`mall-cart-service`）
- 用户维度存储：`{ userId, items: [{skuId, count, checked}] }`
- 过期策略：未登录购物车 7 天过期，登录后永久保存

---

## 🎯 项目简历包装建议

### 项目介绍（30 秒版本）

> 这是一个基于 Spring Cloud Alibaba 的微服务电商系统，采用 **Spring Boot 3.3.2 + Spring Cloud 2023.0.1 + Alibaba 2023.0.1.0** 技术栈，包含 **11 个微服务**（用户/商品/订单/秒杀/搜索/购物车/支付/OSS/定时任务等）。

### 技术亮点（面试关键词）

| 亮点 | 关键词 | 对应面试题 |
|------|--------|------------|
| 秒杀高并发 | Redis 预扣 + MQ 异步 + 乐观锁 | 秒杀设计 |
| 分布式事务 | Seata AT 模式 | 跨服务一致性 |
| 消息削峰 | RocketMQ 事务消息 + 幂等 | 消息可靠性 |
| 缓存防护 | 布隆过滤器 + 多级缓存 | 缓存穿透 |
| 分布式调度 | ElasticJob + Zookeeper | 任务分片 |
| API 统一 | Gateway + Sentinel + JWT | 网关设计 |
| 搜索 | Elasticsearch + IK 分词 | 全文搜索 |
| 幂等设计 | Redis 锁 + 乐观锁 | 重复消费 |

### 常见面试追问链

```
面试官：你们的秒杀怎么防止超卖？
    ↓
你：Redis 预扣 + 乐观锁兜底
    ↓
面试官：Redis 和 DB 数据不一致怎么办？
    ↓
你：乐观锁 WHERE beforeStock + 定时任务同步
    ↓
面试官：MQ 消息丢了怎么办？
    ↓
你：生产者同步发送 + 重试 + 消费者幂等
    ↓
面试官：如果 Redis 在秒杀期间宕机了？
    ↓
你：降级方案：直接走 DB 乐观锁（性能下降但可用）
```

---

## 📎 配套文件

- 自动生成的基础题（60 题）：`interview-project-qa/mall-micro-cloud-interview-questions.md`
- 面试准备计划：`interview-note/interview-preparation-plan.md`
- 自动出题工具：`interview-note/interview-tools/question-generator/question_generator.py`

---

> 💡 这份分析基于你的真实项目代码，建议面试前把每个追问链自己讲一遍，录下来复盘效果最佳。