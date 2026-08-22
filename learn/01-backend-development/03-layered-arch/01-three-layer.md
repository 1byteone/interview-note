# Controller → Service → Repository 三层架构详解

## Situation

你打开一个刚接手的新项目，`OrderController` 里写了 200 行业务逻辑——直接操作数据库、计算价格、调用外部 API。三个月后没人敢动这个类，因为一改就崩，没有单元测试，也没有人说得清"这段代码到底该放哪"。

## Task

理解三层架构（Controller → Service → Repository）的职责边界、依赖方向以及 DTO/VO/PO/DO 的区分，让代码可读、可测、可维护。

## Action

### 1. 三层架构总览

```
┌─────────────────────────────────────────────────────────────────┐
│  Presentation Layer（表现层）                                     │
│  Controller / DTO / 异常处理 / 数据校验                           │
│  职责：接收请求、解析参数、返回响应、不包含业务逻辑                │
│  依赖：Service 层                                                 │
└───────────────────────┬─────────────────────────────────────────┘
                        │ 调用
┌───────────────────────▼─────────────────────────────────────────┐
│  Business Layer（业务层）                                         │
│  Service / 业务规则 / 事务管理 / 领域服务                         │
│  职责：编排业务逻辑、协调多个 Repository、事务控制                 │
│  依赖：Repository 层、其他 Service                                │
└───────────────────────┬─────────────────────────────────────────┘
                        │ 调用
┌───────────────────────▼─────────────────────────────────────────┐
│  Persistence Layer（持久层）                                      │
│  Repository / DAO / Mapper / Entity（PO）                        │
│  职责：数据访问，与数据库交互，不包含业务逻辑                      │
│  依赖：无（被 Service 依赖）                                      │
└─────────────────────────────────────────────────────────────────┘
```

**依赖方向**：Controller → Service → Repository，**不能反向**。

### 2. 各层职责详解

#### Controller 层

```java
@RestController
@RequestMapping("/api/v1/orders")
@Validated
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Result<OrderVO> createOrder(@Valid @RequestBody OrderCreateReq req) {
        // 只做三件事：接收请求、调用 Service、返回响应
        OrderVO vo = orderService.createOrder(req);
        return Result.success(vo);
    }

    @GetMapping("/{id}")
    public Result<OrderVO> getOrder(@PathVariable Long id) {
        OrderVO vo = orderService.getOrderDetail(id);
        return Result.success(vo);
    }
}
```

**Controller 层不该做的事**：
- ❌ 写业务逻辑（计算价格、校验库存）
- ❌ 直接调用 Repository
- ❌ 处理事务
- ❌ 直接返回 Entity 给前端

#### Service 层

```java
@Service
@Transactional
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final PaymentClient paymentClient;

    public OrderVO createOrder(OrderCreateReq req) {
        // 1. 参数校验（业务层面的校验）
        req.getItems().forEach(item -> {
            Product product = productRepository.findById(item.getProductId())
                .orElseThrow(() -> new BusinessException(404, "商品不存在"));
            if (product.getStock() < item.getQuantity()) {
                throw new BusinessException(400, "库存不足: " + product.getName());
            }
        });

        // 2. 核心业务逻辑
        Order order = Order.builder()
            .userId(req.getUserId())
            .totalAmount(calculateTotal(req.getItems()))
            .status(OrderStatus.PENDING_PAYMENT)
            .build();

        // 3. 数据持久化
        orderRepository.save(order);

        // 4. 调用外部服务
        paymentClient.initiatePayment(order.getId(), order.getTotalAmount());

        // 5. 返回 VO
        return OrderVO.from(order);
    }
}
```

**Service 层该做的事**：
- 业务逻辑编排与校验
- 事务管理（`@Transactional`）
- 跨 Repository 的协调
- 调用外部服务（通过接口抽象，不直接依赖具体实现）
- 领域对象的转换

#### Repository 层

```java
@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    // JPA 方法命名查询
    List<Order> findByUserIdAndStatusOrderByCreateTimeDesc(Long userId, OrderStatus status);

    // 复杂查询用 @Query
    @Query("SELECT o FROM Order o WHERE o.userId = :userId " +
           "AND o.totalAmount >= :minAmount " +
           "AND o.createTime BETWEEN :start AND :end")
    Page<Order> findOrders(@Param("userId") Long userId,
                           @Param("minAmount") BigDecimal minAmount,
                           @Param("start") LocalDateTime start,
                           @Param("end") LocalDateTime end,
                           Pageable pageable);
}
```

**Repository 层不该做的事**：
- ❌ 写业务逻辑
- ❌ 调用其他 Service 或 Repository
- ❌ 处理 HTTP 请求

### 3. DTO / VO / PO / DO 区分（阿里规范）

| 名称 | 全称 | 作用范围 | 说明 |
|------|------|----------|------|
| **DTO** | Data Transfer Object | 跨层传输 | 按需组装，不包含业务逻辑。如 `OrderCreateReq`、`OrderQueryDTO` |
| **VO** | View Object | 返回给前端 | 展示层专用，可能组合多个数据源。如 `OrderVO` 包含订单信息 + 商品名称 + 物流状态 |
| **PO / Entity** | Persistent Object | 数据库映射 | 与表结构一一对应，如 `Order` 实体对应 `t_order` 表 |
| **DO** | Domain Object | 领域层 | DDD 中的领域对象，包含业务行为。三层架构中较少用 |

**转换关系**：

```
Controller 层          Service 层              Repository 层
  DTO/VO  ←→  DO/领域对象  ←→  PO/Entity
```

**不建议的用法**：
- 直接在 Controller 中暴露 Entity（会暴露数据库结构）
- 在 Entity 上加 `@JsonIgnore` 等展示层注解（耦合）
- 同一个对象既做 Entity 又做 VO

**推荐做法**：使用 MapStruct 或手动转换。

```java
// MapStruct 转换器
@Mapper(componentModel = "spring")
public interface OrderConverter {
    OrderVO toVO(Order order);
    Order toEntity(OrderCreateReq req);
}
```

### 4. 阿里编码规范补充

来自《阿里巴巴 Java 开发手册》的核心分层建议：

- **分层异常处理**：DAO 层异常类型过多，用 `DataAccessException` 包装；Service 层抛出 `BusinessException`；Controller 层统一处理。
- **分层返回**：Service 层返回 DTO，Controller 层组装 VO。
- **事务要在 Service 层控制**：Controller 层不要加 `@Transactional`。
- **禁止跨层调用**：Controller 不能直接调用 Repository；Service 不能直接调用其他 Service 的 Repository（要通过 Service 接口）。
- **循环依赖**：Service 层双向依赖要用接口隔离或引入中间层。

### 5. 三层架构的演进形态

```
CRUD 简单项目                    复杂业务项目
┌──────────┐                   ┌──────────┐
│Controller│                   │Controller│
└────┬─────┘                   └────┬─────┘
     │                              │
┌────▼─────┐                   ┌────▼──────────┐
│ Service  │    ──演进──►      │ Application   │  (Use Case 编排)
│ (CRUD)   │                   │   Service     │
└────┬─────┘                   └────┬──────────┘
     │                              │
┌────▼─────┐                   ┌────▼──────────┐
│Repository│                   │  Domain       │  (领域层，核心逻辑)
└──────────┘                   │   Service     │
                               └────┬──────────┘
                                    │
                               ┌────▼──────────┐
                               │  Repository   │
                               └───────────────┘
```

当业务复杂度上升时，Service 层会自然分化出 **Application Service**（编排）和 **Domain Service**（领域逻辑），这就是向 DDD 演进的信号。

## Result

三层架构的精髓不在于"分三层"，而在于**依赖方向有明确规则**：

- Controller 层薄：只做"接请求、调服务、返响应"
- Service 层厚：业务逻辑编排，事务管理
- Repository 层纯：只做数据读写

> 面试金句："我们项目遵循严格的三层架构，Controller 层负责参数校验和响应返回，Service 层做业务编排和事务控制，Repository 层负责数据访问。DTO/VO/PO 分离，使用 MapStruct 做对象转换，避免 Entity 直接暴露给前端。"

---

## 附：常见分层问题自查

| 症状 | 问题 | 解决 |
|------|------|------|
| Controller 里出现 `if...else` 业务判断 | 业务逻辑上移 | 移到 Service 层 |
| Service 里直接 `@Autowired JdbcTemplate` | 持久化逻辑上移 | 抽到 Repository 层 |
| Entity 上有 `@JsonIgnore` 或 `@JsonFormat` | 展示逻辑入侵数据库层 | 用 VO 隔离，或用 `@JsonView` |
| Service 方法 200 行以上 | 职责过重 | 拆分多个 Service 或引入领域对象 |
| 两个 Service 互相调用 | 循环依赖 | 检查是否应合并，或提取第三方 Service |