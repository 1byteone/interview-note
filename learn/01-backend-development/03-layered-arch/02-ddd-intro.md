# DDD 战术设计 — Entity, Value Object, Aggregate, Repository, Domain Service

## Situation

你在设计订单系统，发现 `Order` 同时承担了"跟数据库表映射"和"封装业务规则"两个角色。`Order` 里有 `getStatus()` 来判断能否取消，但 `OrderRepository` 里也有类似的逻辑。业务规则散落在各处——`OrderService` 里有一份，`OrderController` 里有一份，甚至前端也有一份。当规则变化时，改了一处漏了三处。

## Task

理解 DDD 战术设计（Tactical Design）的核心构造块，学会用 Entity、Value Object、Aggregate、Domain Service、Repository 等模式把业务逻辑收拢到领域层，让规则归位。

## Action

### 1. DDD 的两层设计

DDD（领域驱动设计）分为**战略设计**和**战术设计**：

| 维度 | 关注点 | 产出 |
|------|--------|------|
| 战略设计 | 系统边界、子域划分、上下文映射 | 限界上下文、核心域/支撑域/通用域 |
| 战术设计 | 具体代码模型、如何建模 | Entity、Value Object、Aggregate、Repository、Domain Service、Factory、Domain Event |

本文聚焦**战术设计**——即 DDD 在代码层面的落地模式。

### 2. 核心构造块

#### Entity（实体）

有唯一标识且会变化的对象。相同 id 就算其他属性变了也是同一个对象。

```java
public class Order {
    private OrderId id;              // 唯一标识
    private Long userId;
    private Money totalAmount;
    private OrderStatus status;
    private List<OrderItem> items;
    private LocalDateTime createdAt;

    // 业务行为（封装规则）
    public void cancel() {
        if (this.status == OrderStatus.SHIPPED || this.status == OrderStatus.DELIVERED) {
            throw new BusinessException("已发货/已送达的订单不能取消");
        }
        if (this.status == OrderStatus.CANCELLED) {
            throw new BusinessException("订单已取消，请勿重复操作");
        }
        this.status = OrderStatus.CANCELLED;
        // 记录取消时间
    }

    public void pay(PaymentResult payment) {
        if (this.status != OrderStatus.PENDING_PAYMENT) {
            throw new BusinessException("当前状态不可支付");
        }
        this.status = OrderStatus.PAID;
    }
}
```

**关键特征**：有 `id`、有状态变化、有业务行为方法。

#### Value Object（值对象）

描述事物的属性，没有唯一标识，不可变，通过属性值比较相等。

```java
public class Money {
    private final BigDecimal amount;
    private final String currency;  // "CNY", "USD"

    public Money(BigDecimal amount, String currency) {
        this.amount = amount;
        this.currency = currency;
    }

    // 值对象的行为：计算、转换，不改变自身
    public Money add(Money other) {
        if (!this.currency.equals(other.currency)) {
            throw new IllegalArgumentException("货币单位不一致");
        }
        return new Money(this.amount.add(other.amount), this.currency);
    }

    public Money multiply(int quantity) {
        return new Money(this.amount.multiply(BigDecimal.valueOf(quantity)), this.currency);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Money money = (Money) o;
        return amount.compareTo(money.amount) == 0 && currency.equals(money.currency);
    }

    @Override
    public int hashCode() { return Objects.hash(amount, currency); }
}
```

**常见 Value Object 例子**：`Money`、`Address`、`PhoneNumber`、`Email`、`Coordinate`、`DateRange`。

**Value Object 的优势**：不可变意味着线程安全，自带校验逻辑，业务含义清晰（不会出现 `BigDecimal price` 到底是含税还是不含税的问题）。

#### Aggregate（聚合）

一组相关对象的集合，通过**聚合根（Aggregate Root）**访问。聚合根是唯一能被外部引用的入口。

```java
// Order 是聚合根
public class Order {
    private OrderId id;
    private List<OrderItem> items;  // OrderItem 是聚合内的实体

    // 外部只能通过 Order 访问 items
    public void addItem(Product product, int quantity) {
        // 业务规则：同一个商品不能重复添加（合并数量）
        this.items.stream()
            .filter(item -> item.getProductId().equals(product.getId()))
            .findFirst()
            .ifPresentOrElse(
                item -> item.increaseQuantity(quantity),
                () -> this.items.add(new OrderItem(product, quantity))
            );
        this.totalAmount = calculateTotal();
    }
}
```

**聚合规则（摘自 Evans DDD）**：
1. 聚合根有全局唯一标识，内部实体只有本地标识
2. 外部对象只能引用聚合根，不能直接引用内部实体
3. 一个事务只修改一个聚合
4. 聚合内保证最终一致性

#### Repository（仓储）

Repository 是聚合的"存储容器"，提供类似集合的接口。**每个聚合根对应一个 Repository**。

```java
public interface OrderRepository {
    Order findById(OrderId id);
    void save(Order order);
    void delete(OrderId id);
    // 基于业务需求的查询
    Page<Order> findByUserId(Long userId, Pageable pageable);
}
```

**注意**：Repository 操作的是**聚合**（整个对象图），不是单表。如果 `Order` 聚合包含 `OrderItem`，`save(order)` 应该同时保存订单和订单项，并在 Repository 实现中保证一致性。

#### Domain Service（领域服务）

当某个业务逻辑不属于任何 Entity 或 Value Object 时（因为它涉及多个对象的协作），就放在 Domain Service 中。

```java
// 领域服务：涉及 Order 和 Payment 两个聚合的协作
public class PaymentDomainService {

    public void processPayment(Order order, Payment payment) {
        // 调用支付网关
        PaymentResult result = paymentGateway.charge(payment);
        // 更新订单状态
        order.pay(result);
        // 记录支付事件
        DomainEventPublisher.publish(new OrderPaidEvent(order.getId()));
    }
}
```

**Domain Service vs Application Service**：
- Domain Service：包含领域逻辑，放在领域层
- Application Service（Use Case）：编排流程，放在应用层，不包含领域规则

### 3. DDD 与三层架构的对比

```
三层架构                     DDD 分层
┌──────────┐              ┌──────────────┐
│Controller│              │  Interface   │  (Controller, DTO)
└────┬─────┘              │  (API Layer) │
     │                    └──────┬───────┘
┌────▼─────┐              ┌──────▼───────┐
│ Service  │    ──►       │ Application  │  (Use Case 编排)
│ (CRUD)   │              │    Layer     │
└────┬─────┘              └──────┬───────┘
     │                    ┌──────▼───────┐
┌────▼─────┐              │   Domain    │  (Entity, VO, Aggregate,
│Repository│              │    Layer    │   Domain Service)
└──────────┘              └──────┬───────┘
                          ┌──────▼───────┐
                          │Infrastructure│  (RepositoryImpl, DB, MQ)
                          └──────────────┘
```

**核心区别**：

| 对比维度 | 三层架构 | DDD |
|----------|----------|-----|
| 核心关注点 | 技术分层（请求处理流程） | 业务领域（业务规则建模） |
| Service 层角色 | 业务逻辑 + 编排混在一起 | 拆分为 Application Service（编排）+ Domain Service（领域逻辑） |
| 数据模型 | 贫血模型（POJO 只有 getter/setter） | 富血模型（Entity 包含业务行为） |
| 业务规则位置 | 散落在 Service 和 Controller 中 | 集中在 Entity 和 Domain Service 中 |
| 复杂业务适应度 | 低（规则蔓延） | 高（规则有归属） |
| 学习曲线 | 低 | 中到高 |

### 4. 什么时候用 DDD

**适合 DDD 的场景**：
- 业务逻辑复杂且规则频繁变化（如营销引擎、风控系统、定价系统）
- 核心业务是系统的核心竞争力
- 需要领域专家与开发者深度协作
- 系统生命周期长（3 年以上）

**不适合 DDD 的场景**：
- 简单的 CRUD 系统（管理后台、报表系统）
- 原型验证阶段
- 团队没有领域建模经验
- 业务规则简单且很少变化

### 5. DDD 在 AI 商城中的应用

回到 AI 商城项目，DDD 的用处主要体现在：

- **商品域**：商品类目、属性模板、SKU 规格——用 Aggregate 保证商品和 SKU 的一致性
- **订单域**：订单状态流转（支付 → 发货 → 确认）用 Entity 封装，避免状态机散落
- **营销域**：优惠券、满减、拼团规则——Domain Service 处理复杂的规则组合
- **AI 推荐域**：推荐策略模型用 Value Object 描述，策略切换不影响核心逻辑

## Result

DDD 战术设计不是"多个概念"的堆砌，而是一套**让业务规则有归属**的方法论：

- Entity：有 id、会变化、有行为
- Value Object：无 id、不可变、描述属性
- Aggregate：通过聚合根保证一致性边界
- Repository：聚合的集合式存储接口
- Domain Service：跨实体的领域逻辑

> 面试金句："我们在复杂业务模块使用 DDD 战术设计。比如订单模块，Order 作为聚合根封装了取消、支付等状态变更逻辑，OrderItem 作为内部实体，整个订单和订单项通过 OrderRepository 保证一致性。业务规则不再散落在 Service 中，改了规则只需要改一个地方。"

---

## 附：贫血模型 vs 富血模型

```java
// 贫血模型（Anti-Pattern）—— 只存数据，不存行为
@Entity
public class Order {
    private Long id;
    private String status;  // String 而不是枚举
    // 只有 getter/setter，没有业务方法
}

// 业务逻辑写在 Service 里
@Service
public class OrderService {
    public void cancelOrder(Long orderId) {
        Order order = orderRepository.findById(orderId);
        if ("SHIPPED".equals(order.getStatus()) || "DELIVERED".equals(order.getStatus())) {
            throw new BusinessException("不能取消");
        }
        order.setStatus("CANCELLED");
        orderRepository.save(order);
    }
}
```

```java
// 富血模型（DDD 推崇）—— 数据和行为在一起
@Entity
public class Order {
    private OrderId id;
    private OrderStatus status;

    public void cancel() {
        if (status == OrderStatus.SHIPPED || status == OrderStatus.DELIVERED) {
            throw new BusinessException("不能取消");
        }
        this.status = OrderStatus.CANCELLED;
    }
}

@Service
public class OrderService {
    public void cancelOrder(OrderId orderId) {
        Order order = orderRepository.findById(orderId);
        order.cancel();  // 业务规则在 Entity 中
        orderRepository.save(order);
    }
}
```

贫血模型让业务规则散落在各处，改一个规则要改多个 Service 方法；富血模型把规则收归到 Entity 中，改动范围缩小到一个类。