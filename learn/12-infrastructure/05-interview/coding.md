# 代码题

## 题 1：Nginx 配置 -- 反向代理 + 负载均衡 + 静态资源

```nginx
# nginx.conf
worker_processes  4;

events {
    worker_connections  1024;
}

http {
    include       mime.types;
    default_type  application/octet-stream;
    sendfile      on;
    keepalive_timeout  65;

    # 上游服务器组（负载均衡）
    upstream backend_servers {
        # 轮询策略，配 weight 权重
        server 192.168.1.10:8080 weight=3;
        server 192.168.1.11:8080 weight=2;
        server 192.168.1.12:8080 weight=1;
        # 健康检查
        check interval=3000 rise=2 fall=3 timeout=1000;
    }

    # 另一个上游组（用户服务）
    upstream user_servers {
        ip_hash;  # 保持 Session
        server 192.168.1.20:8081;
        server 192.168.1.21:8081;
    }

    server {
        listen       80;
        server_name  api.example.com;

        # 动静分离：静态资源由 Nginx 直接处理
        location ~* \.(jpg|jpeg|png|gif|ico|css|js|svg|woff2?)$ {
            root /data/static;
            expires 30d;
            add_header Cache-Control "public, immutable";
            access_log off;
        }

        # 动态请求反向代理到后端
        location /api/ {
            proxy_pass http://backend_servers;
            proxy_set_header Host $host;
            proxy_set_header X-Real-IP $remote_addr;
            proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
            proxy_set_header X-Forwarded-Proto $scheme;

            # 超时配置
            proxy_connect_timeout 5s;
            proxy_read_timeout 10s;
            proxy_send_timeout 10s;
        }

        # 用户服务
        location /user/ {
            proxy_pass http://user_servers/;
            proxy_set_header Host $host;
        }

        # 限流配置
        location /api/order/ {
            # 限制每个 IP 每秒最多 10 个请求
            limit_req zone=api_limit burst=20 nodelay;
            proxy_pass http://backend_servers;
        }

        # 健康检查端点
        location /health {
            return 200 'OK';
            add_header Content-Type text/plain;
        }
    }

    # 限流区域定义
    limit_req_zone $binary_remote_addr zone=api_limit:10m rate=10r/s;
    limit_conn_zone $binary_remote_addr zone=conn_limit:10m;
}
```

---

## 题 2：Gateway 路由配置 -- 多服务路由 + 鉴权过滤器

```yaml
# application.yml
spring:
  cloud:
    gateway:
      routes:
        # 用户服务路由
        - id: user-service
          uri: lb://user-service
          predicates:
            - Path=/api/user/**
          filters:
            - StripPrefix=1
            - name: RequestRateLimiter
              args:
                key-resolver: "#{@ipKeyResolver}"
                redis-rate-limiter.replenishRate: 100
                redis-rate-limiter.burstCapacity: 200

        # 订单服务路由
        - id: order-service
          uri: lb://order-service
          predicates:
            - Path=/api/order/**
            - Method=GET,POST
          filters:
            - StripPrefix=1
            - name: CircuitBreaker
              args:
                name: orderBreaker
                fallbackUri: forward:/fallback/orderError
            - name: Retry
              args:
                retries: 3
                statuses: BAD_GATEWAY, SERVICE_UNAVAILABLE

        # 商品服务路由
        - id: product-service
          uri: lb://product-service
          predicates:
            - Path=/api/product/**
            - Weight=product-group, 80
          filters:
            - StripPrefix=1

      # 全局 CORS 配置
      globalcors:
        cors-configurations:
          '[/**]':
            allowedOrigins: "https://admin.example.com"
            allowedMethods:
              - GET
              - POST
              - PUT
              - DELETE
            allowedHeaders: "*"
            allowCredentials: true
            maxAge: 3600
```

```java
// 自定义鉴权全局过滤器
@Component
@Order(-1)
public class AuthGlobalFilter implements GlobalFilter {

    private static final Set<String> WHITE_LIST = Set.of(
        "/api/user/login", "/api/user/register", "/api/user/refresh"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        // 白名单放行
        if (WHITE_LIST.contains(path)) {
            return chain.filter(exchange);
        }

        // 从 Header 中获取 Token
        String token = exchange.getRequest().getHeaders().getFirst("Authorization");
        if (token == null || !token.startsWith("Bearer ")) {
            return unauthorized(exchange, "Missing or invalid token");
        }

        // 解析 Token（伪代码，实际可调用 auth-service 验证）
        String userId = parseToken(token.substring(7));
        if (userId == null) {
            return unauthorized(exchange, "Token expired or invalid");
        }

        // 将用户信息放入请求头，传递给下游服务
        ServerWebExchange mutatedExchange = exchange.mutate()
            .request(r -> r.header("X-User-Id", userId))
            .build();

        return chain.filter(mutatedExchange);
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String msg) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }

    private String parseToken(String jwt) {
        // 实际项目中解析 JWT 获取 userId
        return "12345";
    }
}
```

---

## 题 3：Sentinel 规则 -- 限流 + 熔断 @SentinelResource

```java
@RestController
@RequestMapping("/api/order")
public class OrderController {

    @Autowired
    private OrderService orderService;

    /**
     * 限流示例：创建订单，QPS 超过 100 时触发限流
     */
    @PostMapping("/create")
    @SentinelResource(
        value = "createOrder",
        blockHandler = "createOrderBlockHandler",
        blockHandlerClass = OrderBlockHandler.class,
        fallback = "createOrderFallback",
        fallbackClass = OrderFallback.class
    )
    public Result<OrderVO> createOrder(@RequestBody @Valid OrderCreateReq req) {
        return Result.success(orderService.createOrder(req));
    }

    /**
     * 热点限流示例：根据商品 ID 限流
     */
    @GetMapping("/detail/{productId}")
    @SentinelResource(
        value = "orderDetail",
        blockHandler = "detailBlockHandler",
        blockHandlerClass = OrderBlockHandler.class
    )
    public Result<OrderVO> detail(@PathVariable Long productId) {
        return Result.success(orderService.getOrderDetail(productId));
    }
}

// 限流降级处理类（blockHandler 处理限流/熔断）
public class OrderBlockHandler {

    public static Result<OrderVO> createOrderBlockHandler(
            OrderCreateReq req, BlockException ex) {
        // 记录限流日志
        log.warn("createOrder blocked: {}, rule={}", req, ex.getRule());
        return Result.error(429, "系统繁忙，请稍后重试");
    }

    public static Result<OrderVO> detailBlockHandler(
            Long productId, BlockException ex) {
        return Result.error(429, "商品查询过于频繁，请稍后重试");
    }
}

// 业务异常降级处理类（fallback 处理业务异常）
public class OrderFallback {

    public static Result<OrderVO> createOrderFallback(
            OrderCreateReq req, Throwable t) {
        log.error("createOrder fallback: {}", req, t);
        return Result.error(500, "订单创建异常，请联系客服");
    }
}
```

```java
// 配置 Sentinel 规则（Nacos 数据源方式）
@Configuration
public class SentinelRuleConfig {

    @PostConstruct
    public void initRules() {
        // 限流规则
        List<FlowRule> flowRules = new ArrayList<>();

        FlowRule createOrderRule = new FlowRule();
        createOrderRule.setResource("createOrder");
        createOrderRule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        createOrderRule.setCount(100);
        createOrderRule.setControlBehavior(RuleConstant.CONTROL_BEHAVIOR_DEFAULT);
        flowRules.add(createOrderRule);

        FlowRule detailRule = new FlowRule();
        detailRule.setResource("orderDetail");
        detailRule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        detailRule.setCount(500);
        detailRule.setControlBehavior(RuleConstant.CONTROL_BEHAVIOR_WARM_UP);
        detailRule.setWarmUpPeriodSec(10);
        flowRules.add(detailRule);

        FlowRuleManager.loadRules(flowRules);

        // 熔断规则
        List<DegradeRule> degradeRules = new ArrayList<>();

        DegradeRule orderDegrade = new DegradeRule();
        orderDegrade.setResource("createOrder");
        orderDegrade.setGrade(RuleConstant.DEGRADE_GRADE_RT);
        orderDegrade.setCount(500);   // RT > 500ms
        orderDegrade.setTimeWindow(10);  // 熔断 10 秒
        orderDegrade.setMinRequestAmount(5);
        orderDegrade.setStatIntervalMs(1000);
        degradeRules.add(orderDegrade);

        DegradeRuleManager.loadRules(degradeRules);
    }
}
```

---

## 题 4：Seata 集成 -- @GlobalTransactional 下单扣库存

```java
@Service
@Slf4j
public class OrderService {

    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private StockFeignClient stockFeignClient;
    @Autowired
    private AccountFeignClient accountFeignClient;

    /**
     * 创建订单：分布式事务示例
     * 涉及：订单服务（本地事务） + 库存服务（远程） + 账户服务（远程）
     */
    @GlobalTransactional(
        name = "create-order",
        rollbackFor = Exception.class,
        timeoutMills = 30000
    )
    public OrderVO createOrder(OrderCreateReq req) {
        // 1. 创建订单（本地事务）
        Order order = new Order();
        order.setUserId(req.getUserId());
        order.setProductId(req.getProductId());
        order.setAmount(req.getAmount());
        order.setStatus(OrderStatus.CREATING);
        orderMapper.insert(order);

        // 2. 扣减库存（远程调用）
        Result stockResult = stockFeignClient.deductStock(req.getProductId(), req.getQuantity());
        if (!stockResult.isSuccess()) {
            throw new BusinessException("库存不足");
        }

        // 3. 扣减账户余额（远程调用）
        Result accountResult = accountFeignClient.deductBalance(req.getUserId(), req.getTotalPrice());
        if (!accountResult.isSuccess()) {
            throw new BusinessException("余额不足");
        }

        // 4. 更新订单状态为成功
        order.setStatus(OrderStatus.SUCCESS);
        orderMapper.updateById(order);

        return OrderVO.from(order);
    }
}

// Feign 客户端：库存服务
@FeignClient(
    name = "stock-service",
    path = "/api/stock",
    fallbackFactory = StockFeignFallbackFactory.class
)
public interface StockFeignClient {

    @PostMapping("/deduct")
    Result<Void> deductStock(@RequestParam("productId") Long productId,
                              @RequestParam("quantity") Integer quantity);
}

// Feign 降级工厂
@Component
@Slf4j
public class StockFeignFallbackFactory implements FallbackFactory<StockFeignClient> {

    @Override
    public StockFeignClient create(Throwable cause) {
        return (productId, quantity) -> {
            log.error("stock-service fallback, productId={}, cause={}", productId, cause.getMessage());
            // 返回失败结果，GlobalTransactional 会自动回滚
            return Result.error("库存服务不可用");
        };
    }
}

// 库存服务端（stock-service）
@Service
public class StockService {

    @Autowired
    private StockMapper stockMapper;

    @Transactional(rollbackFor = Exception.class)
    public void deductStock(Long productId, Integer quantity) {
        Stock stock = stockMapper.selectByProductId(productId);
        if (stock == null || stock.getCount() < quantity) {
            throw new BusinessException("库存不足");
        }
        stock.setCount(stock.getCount() - quantity);
        stockMapper.updateById(stock);
    }
}
```

---

## 题 5：OpenFeign 接口 + 降级配置

```yaml
# application.yml
spring:
  cloud:
    openfeign:
      client:
        config:
          default:
            connectTimeout: 5000
            readTimeout: 5000
            loggerLevel: BASIC
          stock-service:
            connectTimeout: 3000
            readTimeout: 10000
      compression:
        request:
          enabled: true
          mime-types: application/json
          min-request-size: 2048
        response:
          enabled: true
      circuitbreaker:
        enabled: true
```

```java
// Feign 客户端接口
@FeignClient(
    name = "user-service",
    url = "${user-service.url:}",
    path = "/api/user",
    fallbackFactory = UserServiceFallbackFactory.class
)
public interface UserServiceClient {

    @GetMapping("/{id}")
    Result<UserVO> getUserById(@PathVariable("id") Long id);

    @PostMapping("/batch")
    Result<List<UserVO>> getUsersByIds(@RequestBody List<Long> ids);
}

// 降级工厂
@Component
@Slf4j
public class UserServiceFallbackFactory implements FallbackFactory<UserServiceClient> {

    @Override
    public UserServiceClient create(Throwable cause) {
        return new UserServiceClient() {

            @Override
            public Result<UserVO> getUserById(Long id) {
                log.error("getUserById fallback, id={}, cause={}", id, cause.getMessage());
                // 返回兜底数据
                return Result.success(new UserVO(id, "未知用户"));
            }

            @Override
            public Result<List<UserVO>> getUsersByIds(List<Long> ids) {
                log.error("getUsersByIds fallback, ids={}, cause={}", ids, cause.getMessage());
                return Result.success(ids.stream()
                    .map(id -> new UserVO(id, "未知用户"))
                    .collect(Collectors.toList()));
            }
        };
    }
}

// 自定义 Feign 请求拦截器（传递 TraceID）
@Component
public class FeignRequestInterceptor implements RequestInterceptor {

    @Override
    public void apply(RequestTemplate template) {
        // 传递 TraceID
        String traceId = MDC.get("traceId");
        if (traceId != null) {
            template.header("X-Trace-Id", traceId);
        }

        // 传递 Token
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs != null) {
            String token = attrs.getRequest().getHeader("Authorization");
            if (token != null) {
                template.header("Authorization", token);
            }
        }

        // 传递 Seata XID
        String xid = RootContext.getXID();
        if (xid != null) {
            template.header(RootContext.KEY_XID, xid);
        }
    }
}
```

---

## 题 6：GitHub Actions CI/CD 流水线

```yaml
# .github/workflows/deploy.yml
name: CI/CD Pipeline

on:
  push:
    branches: [main, develop]
  pull_request:
    branches: [main]

env:
  REGISTRY: docker.io
  IMAGE_NAME: ${{ secrets.DOCKER_USERNAME }}/mall-service

jobs:
  # 阶段 1：代码检查与测试
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Setup JDK 17
        uses: actions/setup-java@v4
        with:
          distribution: 'temurin'
          java-version: '17'
          cache: 'maven'

      - name: Code Style Check
        run: mvn checkstyle:check -Pcheckstyle

      - name: Run Unit Tests
        run: mvn test

      - name: Run Integration Tests
        run: mvn verify -Pintegration-test

      - name: Upload Test Report
        uses: actions/upload-artifact@v4
        if: always()
        with:
          name: test-reports
          path: target/surefire-reports/

  # 阶段 2：构建与打包
  build:
    needs: test
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Setup JDK 17
        uses: actions/setup-java@v4
        with:
          distribution: 'temurin'
          java-version: '17'

      - name: Build with Maven
        run: mvn clean package -DskipTests

      - name: Build Docker Image
        run: |
          docker build -t ${{ env.IMAGE_NAME }}:${{ github.sha }} .
          docker tag ${{ env.IMAGE_NAME }}:${{ github.sha }} ${{ env.IMAGE_NAME }}:latest

      - name: Login to Docker Hub
        uses: docker/login-action@v3
        with:
          username: ${{ secrets.DOCKER_USERNAME }}
          password: ${{ secrets.DOCKER_PASSWORD }}

      - name: Push Docker Image
        run: |
          docker push ${{ env.IMAGE_NAME }}:${{ github.sha }}
          docker push ${{ env.IMAGE_NAME }}:latest

  # 阶段 3：部署到测试环境
  deploy-staging:
    needs: build
    if: github.ref == 'refs/heads/develop'
    runs-on: ubuntu-latest
    environment: staging
    steps:
      - name: Deploy to Staging
        uses: appleboy/ssh-action@v1.0.3
        with:
          host: ${{ secrets.STAGING_HOST }}
          username: ${{ secrets.STAGING_USER }}
          key: ${{ secrets.STAGING_KEY }}
          script: |
            cd /opt/mall-service
            docker-compose pull
            docker-compose up -d --force-recreate
            docker image prune -f

      - name: Health Check
        run: |
          for i in {1..30}; do
            curl -s http://${{ secrets.STAGING_HOST }}/actuator/health | grep UP && break
            sleep 5
          done

  # 阶段 4：部署到生产环境（金丝雀发布）
  deploy-production:
    needs: build
    if: github.ref == 'refs/heads/main'
    runs-on: ubuntu-latest
    environment: production
    steps:
      - name: Canary Deploy (10% traffic)
        run: |
          kubectl set image deployment/mall-service-canary mall-service=${{ env.IMAGE_NAME }}:${{ github.sha }}
          kubectl scale deployment/mall-service-canary --replicas=1

      - name: Wait for Canary Health Check
        run: |
          sleep 30
          kubectl rollout status deployment/mall-service-canary --timeout=5m

      - name: Promote to Full (100% traffic)
        if: success()
        run: |
          kubectl set image deployment/mall-service mall-service=${{ env.IMAGE_NAME }}:${{ github.sha }}
          kubectl rollout status deployment/mall-service --timeout=5m
          kubectl scale deployment/mall-service-canary --replicas=0

      - name: Rollback on Failure
        if: failure()
        run: |
          kubectl rollout undo deployment/mall-service
          kubectl scale deployment/mall-service-canary --replicas=0
```