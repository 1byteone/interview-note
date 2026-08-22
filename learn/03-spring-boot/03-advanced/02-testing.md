# 测试框架 — @SpringBootTest · MockMvc · Testcontainers

> 等级：🎯 面试进阶
> 目标：掌握 Spring Boot 测试体系，从单元测试到集成测试，覆盖各层级的测试策略。

---

## 一、Spring Boot 测试概述

### 1.1 测试金字塔

```
          /\           端到端测试（E2E）
         /  \          少，慢，贵
        /    \
       /______\
      /        \       集成测试（@SpringBootTest）
     /          \      中等数量，Testcontainers
    /____________\
   /              \    切片测试（@WebMvcTest, @DataJpaTest）
  /                \   较多，聚焦特定层
 /__________________\
/                    \  单元测试（JUnit + Mockito）
基础，最多，最快
```

### 1.2 依赖

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
</dependency>
```

`spring-boot-starter-test` 包含：JUnit 5、Mockito、AssertJ、Hamcrest、JSONassert、JsonPath。

---

## 二、@SpringBootTest 集成测试

### 2.1 完整启动测试

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrderServiceIntegrationTest {

    @Autowired
    private OrderService orderService;

    @Test
    void shouldCreateOrder() {
        // 给定
        CreateOrderRequest request = new CreateOrderRequest();
        request.setUserId(1L);
        request.setProductId(100L);
        request.setQuantity(2);

        // 当
        Order order = orderService.createOrder(request);

        // 则
        assertThat(order).isNotNull();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CREATED);
        assertThat(order.getTotalPrice()).isGreaterThan(0);
    }
}
```

### 2.2 测试环境配置

```yaml
# src/test/resources/application-test.yml
spring:
  datasource:
    url: jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1
    driver-class-name: org.h2.Driver
  jpa:
    hibernate:
      ddl-auto: create-drop
    show-sql: true
```

### 2.3 指定 Profile

```java
@SpringBootTest
@ActiveProfiles("test")
class ProductServiceTest {
    // ...
}
```

---

## 三、MockMvc 测试 Controller

### 3.1 测试 Controller 层

```java
@SpringBootTest
@AutoConfigureMockMvc
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductService productService;

    @Test
    void shouldGetProductById() throws Exception {
        // 准备 Mock 数据
        Product product = new Product(1L, "Spring Boot 实战", 59.9, 100);
        given(productService.findById(1L)).willReturn(Optional.of(product));

        // 执行请求并验证
        mockMvc.perform(get("/api/products/1")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Spring Boot 实战"))
            .andExpect(jsonPath("$.price").value(59.9));
    }

    @Test
    void shouldReturn404WhenProductNotFound() throws Exception {
        given(productService.findById(999L)).willReturn(Optional.empty());

        mockMvc.perform(get("/api/products/999"))
            .andExpect(status().isNotFound());
    }
}
```

### 3.2 测试 POST 请求

```java
@Test
void shouldCreateProduct() throws Exception {
    CreateProductRequest request = new CreateProductRequest("新商品", 99.9, 50);
    Product created = new Product(1L, "新商品", 99.9, 50);
    given(productService.create(any())).willReturn(created);

    mockMvc.perform(post("/api/products")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"新商品\",\"price\":99.9,\"stock\":50}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(1));
}
```

### 3.3 @WebMvcTest 切片测试

只启动 Controller 层，比 @SpringBootTest 快得多：

```java
@WebMvcTest(ProductController.class)  // 只加载 ProductController 相关的 Bean
class ProductControllerSliceTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductService productService;  // 自动配置 Mock

    @Test
    void shouldListProducts() throws Exception {
        given(productService.findAll()).willReturn(List.of(
            new Product(1L, "商品A", 29.9, 100),
            new Product(2L, "商品B", 49.9, 200)
        ));

        mockMvc.perform(get("/api/products"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2));
    }
}
```

---

## 四、数据层切片测试

### 4.1 @DataJpaTest

```java
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
class ProductRepositoryTest {

    @Autowired
    private ProductRepository productRepository;

    @Test
    void shouldFindByCategory() {
        // 准备数据
        Product product = new Product("测试商品", 99.9, 100, "电子产品");
        productRepository.save(product);

        // 验证
        List<Product> found = productRepository.findByCategory("电子产品");
        assertThat(found).hasSize(1);
        assertThat(found.get(0).getName()).isEqualTo("测试商品");
    }
}
```

### 4.2 @DataJdbcTest 和 @MybatisTest

```java
@MybatisTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class OrderMapperTest {

    @Autowired
    private OrderMapper orderMapper;

    @Test
    void shouldFindOrderById() {
        Order order = orderMapper.findById(1L);
        assertThat(order).isNotNull();
        assertThat(order.getStatus()).isEqualTo("CREATED");
    }
}
```

---

## 五、Testcontainers 集成测试

### 5.1 引入依赖

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-testcontainers</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>mysql</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>junit-jupiter</artifactId>
    <scope>test</scope>
</dependency>
```

### 5.2 使用 Testcontainers 启动 MySQL

```java
@SpringBootTest
@Testcontainers
class OrderServiceTestcontainersTest {

    // 启动 MySQL 容器
    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
        .withDatabaseName("testdb")
        .withUsername("test")
        .withPassword("test");

    // 动态数据源配置
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
    }

    @Autowired
    private OrderService orderService;

    @Test
    void shouldPersistOrder() {
        CreateOrderRequest request = new CreateOrderRequest(1L, 100L, 2);
        Order order = orderService.createOrder(request);

        // 验证数据真正写入了 MySQL 容器
        assertThat(order.getId()).isNotNull();
        assertThat(orderService.findById(order.getId())).isPresent();
    }
}
```

### 5.3 复用容器（提高测试速度）

```java
// 定义抽象基类，所有集成测试继承
@SpringBootTest
@Testcontainers
abstract class AbstractIntegrationTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
        .withExposedPorts(6379);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }
}
```

---

## 六、测试最佳实践

### 6.1 测试分层策略

| 层 | 测试方式 | 关注点 | 速度 |
|----|---------|--------|------|
| Repository | @DataJpaTest / @MybatisTest | SQL 正确性 | 快 |
| Service | @SpringBootTest + @MockitoBean | 业务逻辑 | 中 |
| Controller | @WebMvcTest | 路由、参数校验、响应格式 | 快 |
| 集成 | @SpringBootTest + Testcontainers | 端到端流程 | 慢 |

### 6.2 常用断言库

```java
// AssertJ + 链式断言
assertThat(order.getStatus()).isEqualTo(OrderStatus.CREATED);
assertThat(orders)
    .hasSize(3)
    .extracting(Order::getStatus)
    .containsOnly(OrderStatus.CREATED);

// 异常断言
assertThatThrownBy(() -> orderService.createOrder(null))
    .isInstanceOf(IllegalArgumentException.class)
    .hasMessageContaining("请求不能为空");
```

### 6.3 测试覆盖率目标

- 业务 Service 层：> 90%
- Controller 层：> 80%（MockMvc 验证）
- 工具类/配置类：> 70%
- 整体项目：> 80%

---

## 七、面试要点

| 问题 | 一句话答案 |
|------|-----------|
| @SpringBootTest 和 @WebMvcTest 区别？ | 前者启动完整容器，后者只加载 Controller 层和相关 Bean |
| MockMvc 测试什么？ | Controller 层的路由、参数绑定、响应格式、状态码 |
| @DataJpaTest 测试什么？ | Repository 层的 SQL 正确性和数据映射 |
| Testcontainers 解决了什么？ | 在测试中使用真实中间件（MySQL/Redis），避免 H2 模拟差异 |
| @DynamicPropertySource 做什么？ | 在测试中动态覆盖配置属性 |
| 怎么 Mock Service 层？ | @MockitoBean 自动创建并注入 Mock 对象 |

> 掌握了测试框架，进入 GraalVM 原生编译篇，看 Spring Boot 3 如何实现毫秒级启动。