# 全景导读：mall-micro-cloud 微服务电商技术栈深度剖析

> 从一次"下单"请求出发，穿透 12 个微服务、9 大技术栈，理解 Spring Cloud Alibaba 微服务电商的完整架构。
>
> **适用读者：** Java 后端工程师
> **对照体系：** 单体架构 vs 微服务架构
> **项目源码：** `mall-ai/mall-micro-cloud`

---

## 一、架构全景

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           外部客户端 (浏览器/APP)                           │
│                         Vue3 前端 + 手机 H5 页面                            │
└───────────────────────────────────┬─────────────────────────────────────────┘
                                    │ HTTP / HTTPS
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                          Spring Cloud Gateway 网关层                        │
│                      mall-gateway (端口 9000)                               │
│  ┌──────────────────────────────────────────────────────────────────────┐  │
│  │  AuthGatewayFilterFactory  —  JWT 鉴权 + 白名单放行 + Token 刷新      │  │
│  │  RtGlobalFilter            —  全链路请求耗时追踪                       │  │
│  │  KeyResolver               —  Sentinel 限流维度解析                   │  │
│  └──────────────────────────────────────────────────────────────────────┘  │
└───────────────────────────────────┬─────────────────────────────────────────┘
                                    │ 负载均衡 (lb://)
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                          Nacos 注册中心 + 配置中心                          │
│                     192.168.150.101:8848                                    │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
          ┌─────────────────────────┼─────────────────────────────┐
          │                         │                             │
          ▼                         ▼                             ▼
┌─────────────────┐   ┌─────────────────────┐   ┌─────────────────────┐
│   mall-product   │   │    mall-order        │   │    mall-user        │
│   商品服务        │   │    订单服务           │   │    用户服务          │
│  MyBatis-Plus    │   │  Seata 分布式事务     │   │  JWT 鉴权           │
│  SPU / SKU       │   │  RocketMQ 消息发送    │   │  登录/注册/地址     │
│  分类/品牌       │   │  Feign 调用扣库存     │   │  @Login4j 注解      │
└────────┬────────┘   └──────────┬──────────┘   └──────────┬──────────┘
         │                       │                         │
         ▼                       ▼                         ▼
┌─────────────────┐   ┌─────────────────────┐   ┌─────────────────────┐
│  mall-cart       │   │    mall-seckill     │   │    mall-pay         │
│  购物车服务       │   │    秒杀服务          │   │    支付服务          │
│  Redis 缓存      │   │  Redisson 分布式锁   │   │  支付宝沙箱集成      │
│  Hash 结构       │   │  布隆过滤器          │   │  支付回调 RocketMQ  │
│  Feign 商品      │   │  StreamBridge 消息   │   │  支付日志           │
└────────┬────────┘   └──────────┬──────────┘   └──────────┬──────────┘
         │                       │                         │
         ▼                       ▼                         ▼
┌─────────────────┐   ┌─────────────────────┐   ┌─────────────────────┐
│  mall-es         │   │ mall-consumer       │   │  mall-oss           │
│  ES 搜索服务      │   │ 消息消费服务         │   │  OSS 文件服务        │
│  商品搜索/分页   │   │ 清购物车/更新订单    │   │  阿里云 OSS 集成     │
│  关键词+过滤    │   │  同步ES/回滚库存     │   │  文件上传/预览       │
└─────────────────┘   └─────────────────────┘   └─────────────────────┘

┌─────────────────────────────────────────────────────────────────────────────┐
│                          公共模块 (mall-common)                             │
│  Result<T> 统一响应  │  GlobalExceptionHandler  │  GlobalResponseAdvice    │
│  JwtUtil  │  LoginInterceptor  │  AuthorizationInterceptor  │  CacheBloomFilter │
│  MyBatisPlusConfig  │  RedisConfig  │  WebConfig  │  EncryptHandler         │
└─────────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────────┐
│                          基础设施层 (Infrastructure)                        │
│  MySQL 8.0  │  Redis 7.x  │  Elasticsearch  │  RocketMQ  │  Nacos          │
│  Sentinel  │  Seata  │  Redisson  │  Docker  │ 阿里云 OSS  │ 支付宝沙箱     │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 二、一次"下单"请求的完整生命周期

以用户**浏览商品 → 加入购物车 → 下单 → 支付 → 搜索订单**的完整流程为例：

### Step 1: 用户登录 → 获取 JWT Token

```
用户 → POST /api/user/login
  → mall-user-service 验证用户名密码
  → JwtUtil 生成 Token (含 userId + 过期时间)
  → 返回 Token 给前端
  → 前端后续请求携带 Authorization: Bearer <token>
```

### Step 2: 浏览商品 → 网关路由

```
用户 → GET /api/product/page?category=手机
  → Gateway AuthFilter 校验 Token (白名单放行?)
  → RtGlobalFilter 记录开始时间
  → 负载均衡 lb://mall-product-service
  → mall-product-service 返回商品分页数据
  → RtGlobalFilter 记录耗时日志
```

### Step 3: 加入购物车

```
用户 → POST /api/cart/add  {skuId, quantity, userId}
  → mall-cart-service 接收请求
  → 通过 FeignClient 调用 skuInfoFeignClient 获取商品信息
  → Redis Hash 结构存储购物车数据
  → 返回成功
```

### Step 4: 下单 — Seata 分布式事务核心

```
用户 → POST /api/order/create  {address, skuList, ...}
  → mall-order-service 接收请求

  @GlobalTransactional  // Seata AT 模式开启全局事务
  ┌─────────────────────────────────────────────────────┐
  │  1. 生成分布式 ID (雪花算法)                           │
  │  2. 保存订单表 (OrderInfo)                            │
  │  3. 保存订单明细 (OrderItems)                         │
  │  4. Feign 调用 mall-product-service 扣减库存          │
  │  5. Feign 调用 mall-cart-service 清空购物车            │
  │  6. 发送 RocketMQ 消息 (支付状态异步处理)               │
  └─────────────────────────────────────────────────────┘
  → 任一失败，Seata 自动回滚所有分支事务
```

### Step 5: 支付 — 支付宝回调 + 消息驱动

```
用户 → 支付宝支付 → 支付宝异步回调
  → mall-pay-service 接收回调
  → 验证签名 + 更新订单支付状态
  → 发送 RocketMQ 消息 (支付成功事件)

RocketMQ 消息 → mall-consumer-service 消费
  ├── DeleteCartHandler       → 删除已支付订单的购物车项
  ├── UpdateOrderHandler      → 更新订单状态为已支付
  ├── SyncDataToEsHandler     → 同步商品数据到 ES
  └── OrderRecoveryHandler    → 订单超时未支付回滚
```

### Step 6: 搜索订单

```
用户 → GET /api/order/page?userId=xxx&status=PAID
  → mall-order-service 分页查询订单
  → 返回订单列表
```

### 补充链路: 秒杀

```
用户 → GET /api/seckill/product/today
  → mall-seckill-service
  → 布隆过滤器 (缓存穿透防护)
  → Redis 缓存秒杀商品列表
  → 动态生成商品详情静态页

用户 → POST /api/seckill/{activityId}/deduct
  → Redisson RLock 分布式锁 (防止超卖)
  → Redis 预减库存 + StreamBridge 发送异步消息
  → 消息消费: 数据库扣减库存
```

---

## 三、文档体系

| 编号 | 文档 | 核心栈 | 核心考点 | 面试权重 |
|------|------|--------|---------|---------|
| 00 | 本篇·全景导读 | 架构总览 | 微服务拆分原则 | ★★★★★ |
| 01 | [Nacos + Gateway 网关](./01-NACOS-GATEWAY.md) | Nacos, Gateway, Sentinel, 过滤器 | 网关设计、限流熔断 | ★★★★★ |
| 02 | [公共模块与统一架构](./02-COMMON-ARCH.md) | Result, 异常处理, Feign, 拦截器 | 统一架构设计 | ★★★★ |
| 03 | [商品服务与 MyBatis-Plus](./03-PRODUCT-MYBATISPLUS.md) | MyBatis-Plus, SPU/SKU, 分类品牌 | 数据库设计、ORM | ★★★★ |
| 04 | [订单服务与 Seata 分布式事务](./04-ORDER-SEATA.md) | Seata AT, @GlobalTransactional | 分布式事务 | ★★★★★ |
| 05 | [购物车服务与 Redis 缓存](./05-CART-REDIS.md) | Redis Hash, 缓存策略 | 缓存设计、数据结构 | ★★★★ |
| 06 | [秒杀服务与高并发](./06-SECKILL-HIGHCONCUR.md) | Redisson, 布隆过滤器, 限流 | 高并发、分布式锁 | ★★★★★ |
| 07 | [用户服务与 JWT 鉴权](./07-USER-JWT.md) | JWT, 拦截器, 网关鉴权 | 认证授权 | ★★★★ |
| 08 | [ES 搜索服务](./08-ES-SEARCH.md) | Elasticsearch, 索引, 分页 | 搜索、数据分析 | ★★★ |
| 09 | [RocketMQ 消息驱动](./09-ROCKETMQ.md) | RocketMQ, StreamBridge, 消息消费 | 消息队列、异步解耦 | ★★★★★ |
| 10 | [架构复盘与面试题集](./10-ARCHITECTURE.md) | 全栈复盘, 面试题, 跨栈对比 | 综合能力 | ★★★★★ |
| 11 | [定时任务 + 布隆过滤器 + MQ 幂等消费](./11-SCHEDULER-BLOOMFILTER.md) | ElasticJob, Redisson 布隆过滤器 | 分布式任务、缓存穿透 | ★★★★ |
| 12 | [AI 搜索桥接服务](./12-AI-SEARCH-BRIDGE.md) | Feign → Python AI 服务 | Java-Python 桥接 | ★★★★ |

---

## 四、技术栈全景

```
┌───────────────────────────────────────────────────────────────────────────────┐
│                             应用层 (Application)                              │
│   商品服务   │  订单服务   │  用户服务   │  购物车   │  秒杀   │  支付   │  ES  │
├───────────────────────────────────────────────────────────────────────────────┤
│                           微服务层 (Microservice)                             │
│   Spring Cloud Gateway  │  Nacos  │  OpenFeign  │  Sentinel  │  Seata        │
│   Spring Boot 3.3  │  Spring Cloud 2023  │  Spring Cloud Alibaba 2023       │
├───────────────────────────────────────────────────────────────────────────────┤
│                          数据层 (Data)                                       │
│   MySQL 8.0  │  MyBatis-Plus  │  Redis 7.x  │  Elasticsearch  │  RocketMQ   │
│   Redisson  │  Druid 连接池  │  Knife4j  │  JWT  │  阿里云 OSS               │
├───────────────────────────────────────────────────────────────────────────────┤
│                           基础设施层 (Infrastructure)                         │
│   Docker 容器化  │  Linux  │  虚拟机 192.168.150.101  │  支付宝沙箱            │
└───────────────────────────────────────────────────────────────────────────────┘
```

---

## 五、与 mall-ai-search 的关联

| 对比维度 | mall-micro-cloud (本系列) | mall-ai-search (已剖析) |
|---------|-------------------------|----------------------|
| **语言** | Java (Spring Boot 3) | Python (FastAPI) |
| **架构风格** | 微服务 (10+ 服务) | 单体服务 |
| **搜索** | Elasticsearch 关键词搜索 | RedisVL 语义向量搜索 |
| **AI 集成** | 桥接 Python AI 搜索服务 | 原生集成 LangChain Agent |
| **事务** | Seata 分布式事务 | 无事务需求 |
| **消息队列** | RocketMQ 异步解耦 | 无消息队列 |
| **高并发** | 秒杀 + 分布式锁 + 限流 | 无高并发场景 |
| **面试价值** | 微服务 + 分布式 + 高并发 | AI + 向量检索 + Agent |

**共同链路：** mall-micro-cloud 中的 `mall-aisearch-service` 桥接了 mall-ai-search 的 Python AI 搜索能力，两者通过 Gateway 路由 + Feign 调用互相连接。

---

## 六、前置知识要求

| 领域 | 要求 | 不需要 |
|------|------|--------|
| **Java** | 扎实的 Java 8+ 基础 | Java 21 新特性 |
| **Spring Boot** | 自动配置、Starter、IoC、AOP | 源码级 |
| **Spring Cloud** | 了解微服务概念 | 全部组件 |
| **MySQL** | 基础 SQL + 表设计 | 分库分表 |
| **Redis** | 基础数据结构 | Redis 集群 |

---

> **下一篇：** [01-NACOS-GATEWAY.md —— Nacos 注册中心 + Spring Cloud Gateway 网关：路由、鉴权、限流、过滤器](./01-NACOS-GATEWAY.md)
>
> 从"请求到达网关"开始，看 Spring Cloud Gateway 如何实现路由转发、JWT 鉴权、限流熔断和全链路追踪。