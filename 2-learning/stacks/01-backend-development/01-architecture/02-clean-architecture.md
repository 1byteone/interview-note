# Clean Architecture / Hexagonal Architecture — 依赖反转的实践

## Situation

你写了一个电商订单服务，核心逻辑是"创建订单 → 扣减库存 → 调用支付 → 发送通知"。一开始用的是 MySQL + Redis，很顺畅。半年后公司决定把 MySQL 换为 PolarDB，同时通知渠道从短信换成 WebSocket + 邮件。你发现——**改基础设施要改核心业务代码**，`OrderService` 里直接写了 `@Autowired JdbcTemplate` 和 `@Value("${sms.url}")`。一旦替换，核心逻辑的单元测试全部重写，而且没有人敢动。

## Task

理解 Clean Architecture 的核心思想——**依赖反转**（Dependency Inversion Principle），让业务核心不依赖技术细节，使技术栈替换、测试、维护都变得更容易。

## Action

### 1. 核心原则：依赖方向

Clean Architecture 又叫 Hexagonal Architecture（六边形架构）或 Ports & Adapters（端口与适配器），核心规则只有一条：

> **源代码依赖只能从外向内，不能从内向外。**

```
┌─────────────────────────────────────────────────────────────────┐
│                     Infrastructure（外圈）                         │
│   MySQL / Redis / Kafka / 第三方API / 框架 / 配置                  │
│   ┌───────────────────────────────────────────────────────────┐  │
│   │                 Adapters（适配器层）                        │  │
│   │    Controller / RepositoryImpl / MessageConsumer           │  │
│   │   ┌───────────────────────────────────────────────────┐  │  │
│   │   │              Use Cases（用例层）                    │  │  │
│   │   │     OrderService / PaymentService / Port 接口      │  │  │
│   │   │   ┌───────────────────────────────────────────┐  │  │  │
│   │   │   │        Entities（实体层）                   │  │  │  │
│   │   │   │   Order / User / Product / 核心业务规则    │  │  │  │
│   │   │   └───────────────────────────────────────────┘  │  │  │
│   │   └───────────────────────────────────────────────────┘  │  │
│   └───────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
```

**依赖方向**：Entities → Use Cases → Adapters → Infrastructure（箭头指向被依赖方）

**关键约束**：内圈不依赖外圈——Entities 层不引入 Spring 注解，不依赖数据库驱动，不依赖 Web 框架。

### 2. 依赖反转（DIP）的实现方式

传统三层架构中，Service 直接依赖 `JdbcTemplate`：

```java
// ❌ 传统三层：Service 直接依赖具体实现
@Service
public class OrderService {
    @Autowired
    private JdbcTemplate jdbcTemplate;  // 直接依赖 MySQL

    public Order getById(Long id) {
        return jdbcTemplate.queryForObject("select * ...", rowMapper, id);
    }
}
```

Clean Architecture 的做法：**定义端口（接口），让适配器去实现**：

```java
// 内圈：只定义端口，不依赖任何技术框架
public interface OrderRepository {
    Order findById(OrderId id);
    void save(Order order);
}

// 内圈：Use Case 只依赖端口
public class CreateOrderUseCase {
    private final OrderRepository repository;
    private final PaymentService paymentService;  // 这也是端口

    public Order execute(CreateOrderCommand cmd) {
        Order order = Order.create(cmd.getUserId(), cmd.getItems());
        repository.save(order);
        paymentService.charge(order.getPayment());
        return order;
    }
}
```

```java
// 外圈：适配器实现端口，依赖具体技术
@Repository
public class OrderRepositoryImpl implements OrderRepository {
    private final JdbcTemplate jdbcTemplate;  // 具体实现只在外圈

    @Override
    public Order findById(OrderId id) {
        // 执行 SQL 并映射为 Order 实体
    }
}
```

**替换 MySQL 到 PolarDB 时**：新建 `OrderRepositoryPolarDbImpl`，改一个 Bean 配置就行。核心代码一行不动。

### 3. 包的划分方式

```
com.aishop.order
├── domain                     ← 内圈：领域层
│   ├── entity
│   │   ├── Order.java
│   │   ├── OrderItem.java
│   │   └── OrderId.java
│   ├── repository             ← 端口（接口）
│   │   ├── OrderRepository.java
│   │   └── PaymentRepository.java
│   └── service
│       └── OrderDomainService.java
├── application                ← 内圈：应用层 / Use Cases
│   ├── CreateOrderUseCase.java
│   ├── CancelOrderUseCase.java
│   └── dto
│       ├── CreateOrderCommand.java
│       └── OrderDTO.java
├── adapter                    ← 外圈：适配器层
│   ├── inbound
│   │   ├── web
│   │   │   └── OrderController.java
│   │   └── mq
│   │       └── OrderMessageConsumer.java
│   └── outbound
│       ├── persistence
│       │   ├── OrderRepositoryImpl.java
│       │   └── JpaOrderMapper.java
│       └── payment
│           └── PaymentAdapter.java
└── shared                     ← 共享基础设施
    └── config
        └── BeanConfig.java
```

### 4. Clean Architecture vs 三层架构

| 对比维度 | 三层架构 | Clean Architecture |
|----------|----------|--------------------|
| 依赖方向 | Controller → Service → DAO，都是接口依赖 | 内向依赖，核心不依赖基础设施 |
| 可测试性 | 需要 Mock DAO、Redis、MQ | 核心单元测试只需 Mock 端口接口 |
| 框架耦合 | 强耦合（Service 里写 @Transactional） | 弱耦合（框架注解不进内圈）|
| 技术替换 | 伤筋动骨 | 只改适配器 |
| 学习成本 | 低 | 中等（概念多一层） |
| 适用场景 | 中小项目、CRUD 为主 | 复杂业务、长期维护、多团队协作 |

### 5. 什么时候用 Clean Architecture

- **业务逻辑复杂**：规则会频繁变化，且规则本身是核心资产（如金融风控、促销引擎）
- **长期维护**：预期寿命 3 年以上的系统
- **多技术栈候选**：可能从 MySQL 切到 TiDB，从 AWS 切到阿里云
- **团队有 DDD 经验**：Clean Architecture 与 DDD 天然契合，Domain 层就是 DDD 的领域模型

**什么时候不用**：CRUD 为主的简单服务、原型验证阶段、团队规模小且没有替换基础设施的预期。此时强行引入只会增加认知负荷。

## Result

依赖反转的本质是**让业务核心拥有最高优先级和最低耦合度**。把技术框架当作"插件"——插上能用，拔掉不伤核心。面试中，当你被问到"你们项目怎么分层的"，可以回答："我们用了六边形架构，核心 domain 层不依赖任何框架，所有基础设施通过端口接口注入……" 这会比"我们是三层架构"更展示架构思维。

> 一个实用的建议：不要一开始就追求纯正的 Clean Architecture。可以从**三层架构 + Repository 接口**开始，逐步把核心业务计算抽离出来，再慢慢引入 Use Case 层。演进式重构比一次性"完美架构"更可行。