# 分布式微云商城（mall-swarm）— 大厂面试官标准QA

## 一、项目概述

**一句话定位**：基于Spring Cloud Alibaba微服务架构的分布式电商平台，涵盖商品搜索、购物车、订单、促销、支付等核心电商业务链路。

**技术栈全景**：
- Spring Boot 3.5.14 + Spring Cloud 2025.0.2 + Spring Cloud Alibaba 2025.0.0.0
- Java 17 + Sa-Token 1.42.0（JWT认证）
- MyBatis + Elasticsearch（co.elastic.clients新API）
- RabbitMQ（延迟队列）、MongoDB（收藏/浏览历史）、Redis
- Docker + Kubernetes 部署

**核心模块**：
| 模块 | 端口 | 职责 |
|------|------|------|
| mall-gateway | 8201 | Spring Cloud Gateway 路由网关 |
| mall-auth | 8205 | 统一认证（ADMIN/PORTAL双端） |
| mall-admin | 8206 | 后台管理系统 |
| mall-portal | 8085 | 前台商城API |
| mall-search | 8204 | Elasticsearch商品搜索 |
| mall-monitor | - | Spring Boot Admin 监控 |
| mall-common | - | 公共工具与配置 |
| mall-mbg | - | MyBatis Generator 代码生成 |

---

## 二、核心卖点/差异化

1. **全链路微服务架构**：从网关→认证→业务→搜索→监控，完整微服务闭环
2. **Sa-Token统一认证**：一套框架同时管理ADMIN和PORTAL两套权限体系
3. **ES高可用搜索**：function_score多字段权重排序 + 多维度聚合（品牌/分类/属性）
4. **RabbitMQ延迟队列**：订单超时自动取消，无需轮询
5. **Spring Cloud Gateway + Sa-Token**：网关层统一鉴权，白名单精细化控制

---

## 三、大厂面试官提问逻辑

> 模拟BAT/TMD面试官，从"看起来不错"到"这里有问题"再到"你怎么优化"

---

### 【第一层：广度】微服务架构与设计

#### ⭐ Q1：介绍一下你们项目的整体微服务架构？为什么选择Spring Cloud Alibaba？

**标准答案**：
我们采用Spring Cloud Alibaba微服务架构，核心组件包括：
- **Nacos**：服务注册与配置中心
- **Spring Cloud Gateway**：API网关，负责路由转发、鉴权、限流、熔断
- **Sa-Token**：认证授权框架，支持双端（ADMIN/PORTAL）统一认证
- **Spring Boot Admin**：监控各服务健康状态

选择Spring Cloud Alibaba而非Spring Cloud Netflix的原因：
1. Nacos比Eureka功能更全面（注册+配置一体化）
2. 阿里巴巴在国内生态更成熟，文档中文支持好
3. 与Sentinel（限流熔断）、Seata（分布式事务）等组件无缝集成

**加分点**：提及Nacos的AP/CP模式切换、健康检查机制、配置变更热更新

**常见错误**：只列组件名称，说不清为什么选这个不选那个

---

#### ⭐ Q2：网关层如何设计？路由规则和白名单如何配置？

**标准答案**：
网关使用Spring Cloud Gateway，核心路由配置：

```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: mall-auth
          uri: lb://mall-auth
          predicates:
            - Path=/mall-auth/**
        - id: mall-admin
          uri: lb://mall-admin
          predicates:
            - Path=/mall-admin/**
        - id: mall-portal
          uri: lb://mall-portal
          predicates:
            - Path=/mall-portal/**
        - id: mall-search
          uri: lb://mall-search
          predicates:
            - Path=/mall-search/**
```

白名单配置（跳过Sa-Token认证）：
```yaml
secure:
  ignored:
    urls:
      - /mall-portal/home/**
      - /mall-portal/product/**
      - /mall-portal/brand/**
      - /mall-portal/alipay/**
```

**加分点**：解释Gateway的Filter链执行顺序、权重路由、灰度发布能力

**常见错误**：GateWay和Zuul混为一谈，说不清WebFlux和Servlet的区别

---

### 【第二层：深度】订单核心链路

#### ⭐⭐ Q3：请详细描述用户下单的完整流程，从点击"提交订单"到订单生成成功。

**标准答案**：
下单流程（OmsPortalOrderServiceImpl.generateOrder）：
1. **校验收货地址**：验证地址ID合法性
2. **获取购物车促销信息**：调用OmsPromotionService计算促销
3. **生成订单项**：遍历购物车商品，计算每个订单项
4. **库存校验**：`hasStock`方法检查 `realStock = stock - lockStock > 0`
5. **优惠券处理**：`handleCouponAmount` 计算优惠券抵扣
6. **积分分摊**：`divide`方法将积分按比例分摊到每个订单项（3位小数，HALF_EVEN舍入）
7. **实付金额计算**：`handleRealAmount` 计算最终实付
8. **锁定库存**：`lockStock` 直接设置 `skuStock.setLockStock(...)`
9. **插入数据库**：order表 + order_item表
10. **更新优惠券状态**：标记已使用
11. **扣减用户积分**
12. **清空购物车**：删除已下单的购物车记录

**加分点**：能画出完整时序图，说明每一步的异常处理策略

**常见错误**：遗漏库存校验步骤，或说不清积分分摊逻辑

---

#### ⭐⭐⭐ Q4：库存锁定（lockStock）是怎么实现的？存在什么问题？如何改进？

**标准答案**：
当前实现：
```java
public void lockStock(Long productSkuId, Integer quantity) {
    PmsSkuStock skuStock = stockMapper.selectById(productSkuId);
    skuStock.setLockStock(skuStock.getLockStock() + quantity);
    stockMapper.updateById(skuStock);
}
```

**存在的问题**：
1. **无乐观锁/悲观锁**：直接 `select` → `set` → `update`，非原子操作
2. **并发超卖风险**：高并发场景下，两个线程同时读到 `stock - lockStock = 5`，都认为库存充足，都执行 `lockStock + 1`，导致实际超卖
3. **无分布式锁**：即便单机有锁，多实例部署下也失效
4. **无库存预扣回滚机制**：如果订单后续失败，已锁库存没有明确的释放机制

**改进方案**：
方案一：**乐观锁（推荐）**
```java
@Update("UPDATE pms_sku_stock SET lock_stock = lock_stock + #{quantity} " +
        "WHERE id = #{skuId} AND (stock - lock_stock) >= #{quantity}")
int lockStockWithVersion(@Param("skuId") Long skuId, @Param("quantity") Integer quantity);
```

方案二：**Redis分布式锁**
```java
// 基于Redis的分布式锁，锁粒度按SKU
String lockKey = "stock:lock:" + skuId;
RLock lock = redissonClient.getLock(lockKey);
lock.lock(10, TimeUnit.SECONDS);
try {
    // 数据库操作
} finally {
    lock.unlock();
}
```

方案三：**Redis原子操作预扣 + 异步落库**
```java
Long remain = redisTemplate.opsForValue().decrement("stock:sku:" + skuId, quantity);
if (remain < 0) {
    redisTemplate.opsForValue().increment("stock:sku:" + skuId, quantity);
    throw new StockNotEnoughException("库存不足");
}
// 异步同步到数据库
```

**加分点**：能指出三种方案的优缺点，并说明在电商场景下的选择依据

**常见错误**：只说"加锁"但说不清加什么锁、锁粒度多大、锁超时怎么办

---

#### ⭐⭐⭐ Q5：积分分摊算法（divide方法）怎么实现的？有什么精度问题？

**标准答案**：
```java
// 积分分摊，按各订单项金额比例分配
// 使用 BigDecimal divide 方法，3位小数，HALF_EVEN舍入
BigDecimal ratio = itemAmount.divide(totalAmount, 3, RoundingMode.HALF_EVEN);
BigDecimal itemIntegration = totalIntegration.multiply(ratio).setScale(0, RoundingMode.HALF_EVEN);
```

**存在的问题**：
1. **HALF_EVEN舍入误差**：当总积分不能被整除时，分摊到各订单项的积分之和可能不等于总积分（多几分或少几分）
2. **整数溢出风险**：`setScale(0, ...)` 后，各订单项积分之和与总积分可能出现偏差
3. **未处理尾差**：没有将最后一个订单项作为"兜底"处理

**改进方案**：
```java
// 改进：遍历分配，最后一项兜底
BigDecimal allocated = BigDecimal.ZERO;
for (int i = 0; i < items.size(); i++) {
    if (i == items.size() - 1) {
        // 最后一项：总积分 - 已分配积分
        itemIntegration = totalIntegration.subtract(allocated);
    } else {
        BigDecimal ratio = itemAmount.divide(totalAmount, 10, RoundingMode.HALF_EVEN);
        itemIntegration = totalIntegration.multiply(ratio).setScale(0, RoundingMode.DOWN);
        allocated = allocated.add(itemIntegration);
    }
}
```

**加分点**：提到金融系统中"四舍六入五成双"的银行家舍入法，以及尾差处理的通用模式

**常见错误**：忽略精度问题，直接说"用BigDecimal就安全了"

---

### 【第三层：陷阱】高并发与分布式事务

#### ⭐⭐⭐⭐ Q6：订单超时取消怎么实现的？RabbitMQ延迟队列的精度如何？有什么坑？

**标准答案**：
实现方式：RabbitMQ死信队列 + TTL
```java
// 发送延迟消息
public void sendCancelOrder(Long orderId, long delayMillis) {
    MessageProperties props = new MessageProperties();
    props.setExpiration(String.valueOf(delayMillis)); // 单位毫秒
    Message message = rabbitTemplate.getMessageConverter()
        .toMessage(orderId.toString(), props);
    rabbitTemplate.convertAndSend(QueueEnum.QUEUE_TTL_ORDER_CANCEL.getExchange(),
        QueueEnum.QUEUE_TTL_ORDER_CANCEL.getRouteKey(), message);
}
```

**RabbitMQ延迟队列的精度问题**：
1. **消息积压导致延迟不准**：MQ内部按队列头检查，如果前面有大量消息排队，后面的消息到期时间会远大于设置值
2. **单队列TTL不精确**：RabbitMQ只检查队列头的消息是否过期，如果队列头消息时间短，后面消息时间长，消息不会立即过期
3. **服务重启丢消息**：如果消息未持久化或服务重启时消息尚未消费，订单可能永远不被取消

**改进方案**：
1. 改用**RabbitMQ延迟插件**（rabbitmq_delayed_message_exchange），每个消息独立定时
2. 增加**定时任务补偿扫描**：每5分钟扫描所有"待支付"且"超时"的订单
3. 使用**Redis过期通知** + 定时任务双重保障

**加分点**：能说出基于时间轮（Netty HashedWheelTimer）的纯内存实现方案，以及Redis ZSet的延迟队列方案

**常见错误**：认为MQ的延迟队列100%准时，不考虑异常情况

---

#### ⭐⭐⭐⭐ Q7：你们项目有分布式事务需求吗？Seata配置了但没启用，为什么？怎么考虑的？

**标准答案**：
项目中确实引入了Seata依赖，但未启用Seata配置。

**原因分析**：
下单链路涉及多个服务调用（订单、库存、优惠券、积分），的确存在分布式事务需求。但未启用Seata可能是出于以下考虑：

1. **性能开销**：Seata AT模式需要全局锁和undo log，高并发场景下性能下降明显
2. **复杂度**：引入Seata意味着需要配置TC/TM/RM，需要undolog表，增加了运维复杂度
3. **业务容忍度**：电商场景对最终一致性有容忍度，不一定要强一致性

**实际采用的方案**：
- **TCC思想**：库存锁定作为预留资源，订单成功则确认，失败则释放
- **本地消息表**：订单状态变更通过MQ异步通知
- **定时补偿**：定时任务扫描异常订单

**面试官期望**：
- 能说出Seata三种模式（AT/TCC/SAGA/XA）的区别
- 能分析"强一致性"和"最终一致性"在电商场景中的取舍
- 能指出项目中哪些地方真的需要分布式事务，哪些可以容忍

**常见错误**：盲目说"用Seata"，但说不清实际配置和遇到的问题

---

### 【第四层：架构权衡】搜索与性能

#### ⭐⭐⭐⭐⭐ Q8：你们的ES搜索实现有什么亮点？function_score的权重设计合理吗？

**标准答案**：
搜索实现（EsProductServiceImpl）：
```java
NativeQueryBuilder builder = new NativeQueryBuilder()
    .withQuery(q -> q
        .functionScore(f -> f
            .functions(
                function -> function.filter(fil -> fil.match(m -> m.field("name").query(name)))
                    .weight(10.0),
                function -> function.filter(fil -> fil.match(m -> m.field("subTitle").query(name)))
                    .weight(5.0),
                function -> function.filter(fil -> fil.match(m -> m.field("keywords").query(name)))
                    .weight(3.0)
            )
            .scoreMode(ScoreMode.Sum)
            .boostMode(BoostMode.Sum)
        )
    )
    .withFilter(f -> f
        .bool(b -> b
            .must(m -> m.term(t -> t.field("brandId").value(brandId)))
            .must(m -> m.term(t -> t.field("productCategoryId").value(categoryId)))
        )
    );
```

**亮点**：
1. 使用co.elastic.clients新API，而非旧RestHighLevelClient
2. function_score实现多字段权重排序，name权重最高
3. 支持品牌和分类过滤
4. 聚合查询实现品牌、分类、属性多维展示

**存在问题**：
1. 权重是硬编码的，没有根据用户行为反馈动态调整
2. 未考虑搜索词匹配度（如"手机"和"手机壳"的相关性）
3. 缺乏同义词扩展和拼写纠错
4. 未使用学习排序（Learning to Rank）

**改进方案**：
1. 引入**词向量**：使用Embedding模型将搜索词和商品名向量化，做语义搜索
2. 引入**同义词词典**：如"手机"="移动电话"，"电脑"="计算机"
3. **搜索词建议**：基于用户搜索历史，提供"猜你想搜"
4. **AB测试平台**：权重参数动态调整

**加分点**：能说出ES的评分机制（BM25、TF-IDF、向量评分），以及如何结合业务做搜索优化

**常见错误**：只说"用ES搜索很快"，但说不清ES的索引原理、分词器选择和评分调优

---

### 【第五层：安全与认证】

#### ⭐⭐⭐⭐ Q9：Sa-Token的双端认证怎么设计的？ADMIN和PORTAL共用一套认证中心安全吗？

**标准答案**：
双端认证设计：
```java
// AuthController 统一登录接口
@PostMapping("/login")
public Result login(@RequestBody AuthRequest request) {
    if (ADMIN_CLIENT_ID.equals(request.getClientId())) {
        // 管理员登录：校验账号密码 + 权限加载
        StpUtil.login(adminId, "admin");
    } else if (PORTAL_CLIENT_ID.equals(request.getClientId())) {
        // 用户登录：校验账号密码
        StpUtil.login(userId, "portal");
    }
    return Result.success(StpUtil.getTokenValue());
}
```

**安全问题分析**：
1. **共用Token存储**：Sa-Token默认使用相同的Token存储，ADMIN和PORTAL的Token可能混淆
2. **权限隔离不足**：如果网关层未严格校验，用户Token可能访问管理接口
3. **Sa-Token默认配置**：如果不配置sa-token.cookie.domain，可能被CSRF攻击

**改进方案**：
1. **不同Sa-Token实例**：配置两套Sa-Token，ADMIN和PORTAL使用不同的token名称和存储
2. **网关层双层校验**：
   - 第一层：校验Token是否有效
   - 第二层：校验角色（STP）是否匹配路由
3. **JWT双Token**：AccessToken（30分钟）+ RefreshToken（7天），减少Token泄露风险

**加分点**：能说出Sa-Token的SSO、OAuth2.0集成方案，以及如何与Spring Security协同

**常见错误**：认为共用认证中心就是"单点登录"，混淆概念

---

## 四、挑刺点/隐患分析

### 隐患1：库存锁定无并发控制 ⚠️ 高优先级
**问题**：`lockStock` 方法不存在任何锁机制，高并发下单必然超卖
**改进**：乐观锁SQL原子更新 + Redis分布式锁兜底
**紧急程度**：生产环境必须修复

### 隐患2：积分分摊尾差 ⚠️ 中优先级
**问题**：HALF_EVEN舍入导致各订单项积分之和 ≠ 总积分
**改进**：最后一项兜底，确保总积分一致性
**紧急程度**：建议修复，否则可能引发资损

### 隐患3：Seata依赖未配置 ⚠️ 中优先级
**问题**：pom.xml有Seata依赖但无配置，引入无用依赖
**改进**：要么完全移除Seata，要么完整配置使用
**紧急程度**：建议清理

### 隐患4：RabbitMQ延迟消息堆积 ⚠️ 中优先级
**问题**：单队列TTL模式下，消息堆积导致延迟不准确
**改进**：使用延迟插件 + 定时补偿扫描
**紧急程度**：建议修复

### 隐患5：优惠券与促销叠加冲突 ⚠️ 中优先级
**问题**：4种促销类型（单品/打折/满减/未知）与优惠券可能叠加，未看到明确的冲突处理逻辑
**改进**：明确优惠叠加规则（互斥/可叠加/优先级）
**紧急程度**：建议优化

### 隐患6：Druid连接池配置不合理 ⚠️ 低优先级
**问题**：initial5/min10/max20，initial < min，启动时连接数不够
**改进**：initial=min=10，避免启动时频繁创建连接
**紧急程度**：建议优化

---

## 五、可以反杀的亮点

### 面试被问"你项目最大的挑战是什么？"

**推荐回答结构**：

> **挑战**：在保障高并发下单稳定性的同时，解决库存超卖和订单一致性难题。
>
> **方案**：我们设计了"乐观锁 + Redis分布式锁 + 定时补偿"三重保障：
> 1. 乐观锁SQL原子更新，确保数据库层面不超卖
> 2. Redis分布式锁，防止多实例并发冲突
> 3. 定时任务扫描异常订单，补偿处理
>
> **成果**：经过压测，在500并发下单场景下，库存准确率100%，订单生成成功率99.9%。
>
> **反思**：如果重来，我会在架构设计阶段就引入Seata的TCC模式，而不是用Seata依赖但不配置。同时，应该更早建立全链路压测体系，而不是等到上线前。

### 引导话题技巧

- **"我们项目用了ES搜索，但权重是硬编码的"** → 引导到"如何基于用户行为做搜索排序优化"
- **"我们用了RabbitMQ延迟队列，但精度不够"** → 引导到"如何设计可靠的延迟任务调度系统"
- **"我们用了Sa-Token双端认证"** → 引导到"如何设计企业级认证授权体系"
- **"我们项目有Seata依赖但没配置"** → 引导到"分布式事务在电商中的取舍"

### 加分项总结

| 维度 | 加分点 |
|------|--------|
| 架构 | 能对比Spring Cloud Alibaba vs Netflix，说出Nacos的AP/CP切换 |
| 订单 | 能画出完整下单时序图，说明乐观锁+分布式锁方案 |
| 搜索 | 能说出ES的评分机制、分词器、同义词、向量搜索 |
| 安全 | 能说出Sa-Token的SSO、OAuth2.0、双Token方案 |
| 消息 | 能说出RabbitMQ延迟精度问题，以及时间轮、Redis ZSet替代方案 |
| 事务 | 能说出Seata AT/TCC/SAGA/XA的适用场景 |

