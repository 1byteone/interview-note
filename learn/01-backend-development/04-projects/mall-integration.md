# AI 商城 — 整体架构与请求链路

## 项目背景

AI 商城是一个基于 Spring Cloud Alibaba 的微服务电商系统，在传统电商基础上增加了 AI 推荐、智能搜索、AIGC 商品描述生成等能力。本模块将之前所有章节的架构、API、分层知识落地到一个具体的项目场景中。

## 整体架构图

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         客户端层（Client Layer）                              │
│       Web 端 (Vue.js)       移动端 (Android/iOS)      第三方 OpenAPI        │
└─────────────────────────────────┬───────────────────────────────────────────┘
                                  │
┌─────────────────────────────────▼───────────────────────────────────────────┐
│                       API 网关层（Gateway Layer）                            │
│                     Spring Cloud Gateway / Nginx                            │
│            功能：路由 / 限流 / 认证 / 日志 / 灰度 / 熔断                      │
└─────────────────────────────────┬───────────────────────────────────────────┘
                                  │
          ┌───────────────────────┼───────────────────────┐
          ▼                       ▼                       ▼
┌─────────────────┐   ┌─────────────────┐   ┌─────────────────────┐
│  业务服务层       │   │   业务服务层      │   │    AI 服务层         │
│  (Java/Spring)   │   │  (Java/Spring)   │   │  (Python/Flask)     │
├─────────────────┤   ├─────────────────┤   ├─────────────────────┤
│ 用户服务 (User)  │   │ 订单服务 (Order)  │   │ AI 推荐服务          │
│ - 注册登录       │   │ - 下单/支付       │   │ - 协同过滤 / CTR    │
│ - OAuth2 登录    │   │ - 订单状态机      │   │ - 实时推荐           │
│ - 用户画像       │   │ - 退款/售后       │   │                     │
├─────────────────┤   ├─────────────────┤   ├─────────────────────┤
│ 商品服务 (Prod)  │   │ 支付服务 (Pay)    │   │ AI 搜索服务          │
│ - 商品/类目/SKU  │   │ - 支付宝/微信     │   │ - 向量检索 (ES)      │
│ - 库存管理       │   │ - 对账/退款       │   │ - 语义理解           │
│ - 商品评价       │   │ - 结算           │   │ - 图片搜索           │
├─────────────────┤   ├─────────────────┤   ├─────────────────────┤
│ 营销服务 (Prom)  │   │ 物流服务 (Logis)  │   │ AIGC 服务           │
│ - 优惠券/满减    │   │ - 配送/运费       │   │ - 商品描述生成       │
│ - 秒杀/拼团      │   │ - 物流轨迹查询    │   │ - 客服对话           │
│ - 积分/会员      │   │                  │   │                     │
└───────┬─────────┘   └───────┬─────────┘   └──────────┬──────────┘
        │                     │                        │
        └─────────────────────┼────────────────────────┘
                              │
┌─────────────────────────────▼─────────────────────────────────────────────┐
│                        基础设施层（Infrastructure）                         │
│   MySQL (业务数据)  │  Redis (缓存/Session)  │  Elasticsearch (搜索/日志)  │
│   RocketMQ (消息)   │  Nacos (注册/配置)     │  Sentinel (限流/熔断)        │
│   Seata (分布式事务) │  SkyWalking (链路追踪)  │  Prometheus + Grafana (监控) │
└─────────────────────────────────────────────────────────────────────────────┘
```

## 各服务职责

| 服务 | 技术栈 | 核心职责 | 数据库 |
|------|--------|----------|--------|
| **Gateway** | Spring Cloud Gateway | 路由转发、统一认证、限流、日志、灰度发布 | 无 |
| **User Service** | Spring Boot + Security | 注册/登录、OAuth2、用户画像、地址管理 | `ai_user` |
| **Product Service** | Spring Boot + JPA | 商品/类目/SKU/库存管理、商品评价 | `ai_product` |
| **Order Service** | Spring Boot + MyBatis | 下单、订单状态机、退款/售后 | `ai_order` |
| **Payment Service** | Spring Boot | 支付对接（支付宝/微信）、对账、结算 | `ai_payment` |
| **Inventory Service** | Spring Boot + Redis | 库存扣减、预占、回滚（Redis 缓存库存） | `ai_inventory` + Redis |
| **Promotion Service** | Spring Boot + RocketMQ | 优惠券发放、秒杀/拼团、积分体系 | `ai_promotion` |
| **Logistics Service** | Spring Boot | 配送单管理、运费计算、物流轨迹 | `ai_logistics` |
| **AI Recommend** | Python + Flask | 协同过滤、CTR 预估、实时推荐列表 | ClickHouse + Redis |
| **AI Search** | Python + Flask + ES | 向量检索、语义理解、图片搜索、搜索排序 | Elasticsearch |
| **AIGC Service** | Python + FastAPI | 商品描述生成、智能客服对话、图片生成 | 大模型 API |

## 请求链路示例

### 链路 1：用户浏览商品并下单

```
用户 (浏览器)
    │
    ▼
Gateway ──认证 JWT──► User Service (验证用户)
    │
    ▼
Gateway ──路由──► Product Service (获取商品详情、库存)
    │
    ▼
Gateway ──路由──► AI Recommend (获取推荐列表)
    │
    ▼
用户浏览商品 → 加入购物车 → 提交订单
    │
    ▼
Gateway ──路由──► Order Service (创建订单)
    │                   │
    │                   ▼
    │              Product Service (锁定库存)
    │                   │
    │                   ▼
    │              Payment Service (发起支付请求)
    │                   │
    │                   ▼
    │              RocketMQ ──► Inventory Service (扣减库存)
    │                   │
    │                   ▼
    │              Order Service (更新订单状态为待发货)
    │
    ▼
用户收到下单成功响应
```

### 链路 2：AI 搜索推荐商品

```
用户搜索"白色连衣裙 夏季"
    │
    ▼
Gateway ──路由──► AI Search (Python)
    │                   │
    │                   ▼
    │              语义理解→向量化 (Embedding Model)
    │                   │
    │                   ▼
    │              Elasticsearch (向量检索 + 关键词混合)
    │                   │
    │                   ▼
    │              排序→返回商品 ID 列表
    │
    ▼
Gateway ──路由──► Product Service (根据 ID 批量查商品详情)
    │
    ▼
Gateway ──路由──► AI Recommend (用户画像 + 搜索结果做个性化排序)
    │
    ▼
返回前端渲染搜索结果
```

### 链路 3：秒杀场景

```
用户抢购秒杀商品
    │
    ▼
Gateway ──限流 (Sentinel)──► 超过阈值直接返回"拥挤"
    │
    ▼
Promotion Service
    │
    ├── 请求进入 Redis 队列 (预减库存)
    │       │
    │       ├── 库存不足 → 直接返回"已售罄"
    │       └── 库存充足 → 生成秒杀令牌 (Token)
    │
    ▼
User 携带 Token 进入下单流程
    │
    ▼
Order Service ──RocketMQ──► 异步处理订单
    │
    ▼
Inventory Service (最终扣减库存)
    │
    ▼
返回用户"抢购成功"
```

## 关键设计决策

### 1. 服务间通信

| 通信方式 | 场景 | 协议 |
|----------|------|------|
| 同步 RPC | 查询实时数据（商品详情、用户信息） | OpenFeign + HTTP |
| 异步消息 | 解耦、最终一致性（下单后扣库存、发送通知） | RocketMQ |
| 事件驱动 | 跨服务状态变更通知（订单完成 → 物流发货） | RocketMQ 事务消息 |

### 2. 分布式事务策略

| 场景 | 策略 | 实现 |
|------|------|------|
| 下单 + 扣库存 | TCC（Try-Confirm-Cancel） | Seata TCC 模式 |
| 支付 + 更新订单 | 本地消息表 + 最终一致性 | RocketMQ 事务消息 |
| 纯查询链路 | 不处理 | 读请求直接返回 |

### 3. 数据一致性边界

- 每个服务**拥有自己的数据库**，不跨服务直接访问数据库
- 跨服务的数据一致性通过**消息队列 + 重试 + 对账**保证
- 核心链路（下单 → 支付）使用 Seata AT 模式，非核心链路（下单 → 发送通知）使用最终一致性

## 分层代码结构示例（Order Service）

```
order-service/
├── src/main/java/com/aishop/order/
│   ├── OrderApplication.java
│   ├── interfaces/                    ← 接口层（Controller）
│   │   ├── web/
│   │   │   ├── OrderController.java
│   │   │   └── dto/
│   │   │       ├── OrderCreateReq.java
│   │   │       └── OrderVO.java
│   │   └── mq/
│   │       └── OrderEventConsumer.java
│   ├── application/                   ← 应用层（Use Case）
│   │   ├── CreateOrderUseCase.java
│   │   └── CancelOrderUseCase.java
│   ├── domain/                        ← 领域层（DDD）
│   │   ├── entity/
│   │   │   ├── Order.java
│   │   │   └── OrderItem.java
│   │   ├── vo/
│   │   │   ├── Money.java
│   │   │   └── OrderStatus.java
│   │   ├── repository/
│   │   │   └── OrderRepository.java
│   │   └── service/
│   │       └── OrderDomainService.java
│   └── infrastructure/                ← 基础设施层
│       ├── persistence/
│       │   ├── OrderRepositoryImpl.java
│       │   └── entity/
│       │       └── OrderPO.java
│       ├── client/
│       │   ├── ProductServiceClient.java
│       │   └── PaymentServiceClient.java
│       └── config/
│           ├── RestTemplateConfig.java
│           └── RocketMQConfig.java
```

> 这个架构把之前所有章节的理念串联了起来：01-architecture 的微服务拆分决策、02-api-design 的 RESTful 规范和安全、03-layered-arch 的 DDD 分层——都在 AI 商城中得到体现。