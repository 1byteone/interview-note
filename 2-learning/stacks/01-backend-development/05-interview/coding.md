# 代码题 — API 设计题、分层代码题

> 面试中手写代码题不仅考察语法正确，更考察**代码组织、异常处理、边界条件**的意识。这里给出几道典型题目及其解答。

## 题型一：API 设计题

### 题目

设计一个"商品 API"的 Controller 层代码，包含列表查询、详情查询、创建商品三个接口。要求：
1. 使用合理的 RESTful 风格
2. 处理参数校验和异常
3. 统一返回格式

### 解答

```java
@RestController
@RequestMapping("/api/v1/products")
@Validated
public class ProductController {

    private final ProductService productService;

    @GetMapping
    public Result<PageResult<ProductVO>> list(@Valid ProductQuery query) {
        // 查询参数校验、分页查询
        PageResult<ProductVO> page = productService.list(query);
        return Result.success(page);
    }

    @GetMapping("/{id}")
    public Result<ProductVO> getById(@PathVariable Long id) {
        if (id == null || id <= 0) {
            return Result.error(400, "无效的商品ID");
        }
        ProductVO vo = productService.getById(id);
        return Result.success(vo);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Result<ProductVO> create(@Valid @RequestBody ProductCreateReq req) {
        ProductVO vo = productService.create(req);
        return Result.success(vo);
    }
}
```

### 加分项

- 使用 `@Valid` 或 `@Validated` 做参数校验
- 统一返回 `Result<T>`，带上 `code` / `message` / `data`
- 遵循 RESTful 语义（GET 查、POST 创建）
- 分页参数统一为 `page` / `size` / `sort`

## 题型二：分层代码题

### 题目

请用三层架构写一个"创建订单"的功能，包含 Controller → Service → Repository 三层，以及对应的 DTO/VO/Entity。

### 解答

**Entity（PO，与数据库表对应）**：

```java
@Entity
@Table(name = "t_order")
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private BigDecimal totalAmount;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    @Column(nullable = false)
    private LocalDateTime createTime;

    @OneToMany(cascade = CascadeType.ALL, mappedBy = "order")
    private List<OrderItem> items;

    // getter / setter
}
```

**DTO（请求）**：

```java
@Data
public class OrderCreateReq {
    @NotNull(message = "用户ID不能为空")
    private Long userId;

    @NotEmpty(message = "订单项不能为空")
    @Valid
    private List<OrderItemReq> items;

    @Data
    public static class OrderItemReq {
        @NotNull(message = "商品ID不能为空")
        private Long productId;

        @Min(value = 1, message = "数量至少为1")
        @Max(value = 999, message = "单次最多购买999件")
        private Integer quantity;
    }
}
```

**VO（响应）**：

```java
@Data
@Builder
public class OrderVO {
    private Long id;
    private Long userId;
    private BigDecimal totalAmount;
    private String status;
    private LocalDateTime createTime;
    private List<OrderItemVO> items;

    public static OrderVO from(Order order) {
        return OrderVO.builder()
            .id(order.getId())
            .userId(order.getUserId())
            .totalAmount(order.getTotalAmount())
            .status(order.getStatus().name())
            .createTime(order.getCreateTime())
            .items(order.getItems().stream()
                .map(OrderItemVO::from)
                .collect(Collectors.toList()))
            .build();
    }
}
```

**Repository**：

```java
@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    Page<Order> findByUserIdOrderByCreateTimeDesc(Long userId, Pageable pageable);
}
```

**Service**：

```java
@Service
@Transactional
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final InventoryServiceClient inventoryClient;

    public OrderVO createOrder(OrderCreateReq req) {
        // 1. 校验参数
        List<OrderItem> orderItems = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;

        for (OrderCreateReq.OrderItemReq itemReq : req.getItems()) {
            // 查询商品
            Product product = productRepository.findById(itemReq.getProductId())
                .orElseThrow(() -> new BusinessException(404, "商品不存在: " + itemReq.getProductId()));

            // 校验库存
            if (product.getStock() < itemReq.getQuantity()) {
                throw new BusinessException(400, "库存不足: " + product.getName());
            }

            // 构建订单项
            OrderItem item = OrderItem.builder()
                .productId(product.getId())
                .productName(product.getName())
                .price(product.getPrice())
                .quantity(itemReq.getQuantity())
                .subtotal(product.getPrice().multiply(BigDecimal.valueOf(itemReq.getQuantity())))
                .build();
            orderItems.add(item);
            total = total.add(item.getSubtotal());
        }

        // 2. 创建订单（聚合根）
        Order order = Order.builder()
            .userId(req.getUserId())
            .totalAmount(total)
            .status(OrderStatus.PENDING_PAYMENT)
            .createTime(LocalDateTime.now())
            .items(orderItems)
            .build();

        // 3. 保存订单
        orderRepository.save(order);

        // 4. 扣减库存（调用远程服务，或本地事务）
        inventoryClient.deductStock(req.getItems());

        // 5. 返回 VO
        return OrderVO.from(order);
    }
}
```

**Controller**：

```java
@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Result<OrderVO> createOrder(@Valid @RequestBody OrderCreateReq req) {
        OrderVO vo = orderService.createOrder(req);
        return Result.success(vo);
    }
}
```

**全局异常处理**：

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleValidation(MethodArgumentNotValidException ex) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
            .map(e -> e.getField() + ": " + e.getDefaultMessage())
            .collect(Collectors.joining(", "));
        return Result.error(400, msg);
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusiness(BusinessException ex) {
        return ResponseEntity
            .status(HttpStatus.valueOf(ex.getCode()))
            .body(Result.error(ex.getCode(), ex.getMessage()));
    }
}
```

### 面试官可能追问

- **Q**：这里库存扣减是远程调用，如果远程调用失败怎么办？
  **A**：增加重试机制（`@Retryable`）和补偿方案（定时任务对账）；也可以用 Seata TCC 模式保证分布式事务。
- **Q**：如果没有库存服务，怎么处理？
  **A**：可以在本地通过 `UPDATE ... WHERE stock >= n` 原子扣减，数据库层面兜底防超卖。
- **Q**：Order 和 OrderItem 为什么用 `@OneToMany`？
  **A**：订单项是订单聚合的一部分，通常一起读取和写入。使用 `cascade = ALL` 可以让保存订单时自动保存订单项。

## 题型三：设计一个全局异常处理器

### 题目

请设计一个 Spring Boot 全局异常处理器，包含：
- 参数校验异常
- 业务异常
- 未捕获异常
- 返回统一格式

### 解答

```java
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // 参数校验异常
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleValidation(MethodArgumentNotValidException ex) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
            .map(e -> String.format("%s=%s: %s", e.getField(), e.getRejectedValue(), e.getDefaultMessage()))
            .collect(Collectors.joining("; "));
        log.warn("参数校验失败: {}", msg);
        return Result.error(400, "参数校验失败: " + msg);
    }

    // 业务异常
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusiness(BusinessException ex) {
        log.warn("业务异常: code={}, msg={}", ex.getCode(), ex.getMessage());
        return ResponseEntity
            .status(HttpStatus.valueOf(ex.getCode()))
            .body(Result.error(ex.getCode(), ex.getMessage()));
    }

    // 未捕获异常（兜底）
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleException(Exception ex, HttpServletRequest request) {
        log.error("未知异常: uri={}, method={}", request.getRequestURI(), request.getMethod(), ex);
        return Result.error(500, "系统繁忙，请稍后重试");
    }
}
```

## 题型四：并发安全的库存扣减

### 题目

用 Java 写一个高并发环境下的库存扣减方法，要求：
1. 不能超卖
2. 性能和并发安全兼顾

### 解答

```java
@Service
public class InventoryService {

    @Autowired
    private StringRedisTemplate redisTemplate;

    // 方案一：Redis Lua 脚本（预扣减）
    public boolean deductStock(Long productId, int quantity) {
        String script = "" +
            "local stock = redis.call('GET', KEYS[1]) " +
            "if not stock or tonumber(stock) < tonumber(ARGV[1]) then " +
            "    return 0 " +
            "end " +
            "redis.call('DECRBY', KEYS[1], ARGV[1]) " +
            "return 1";

        Long result = redisTemplate.execute(
            new DefaultRedisScript<>(script, Long.class),
            Arrays.asList("stock:" + productId),
            String.valueOf(quantity)
        );
        return result != null && result == 1L;
    }

    // 方案二：数据库原子扣减（兜底）
    @Transactional
    public boolean deductStockDb(Long productId, int quantity) {
        int affected = jdbcTemplate.update(
            "UPDATE t_inventory SET stock = stock - ? WHERE product_id = ? AND stock >= ?",
            quantity, productId, quantity
        );
        return affected > 0;
    }
}
```

## 代码题通用检查清单

在面试中写完代码后，用这 5 个问题自己检查一遍：

1. **边界条件**：ID 为空怎么处理？参数为 null 会怎样？数量为 0 或负数？
2. **异常处理**：数据库查询不到怎么办？远程调用超时怎么办？
3. **幂等性**：重复提交会不会创建重复数据？
4. **并发安全**：多线程同时扣库存会不会超卖？
5. **代码组织**：业务逻辑有没有放在 Controller 层？有没有跨层调用？