# API 安全 — 认证、授权、防护

## Situation

你开发了一个 AI 商城的商品 API，上线第一周就发现：有人拿着抓包得到的 Token 不停地调用下单接口；有人猜出了 `/api/v1/admin/products` 路径直接访问管理功能；有人在商品搜索接口传入超长字符串导致数据库 CPU 100%。如果不做安全防护，API 就是裸奔的。

## Task

掌握 API 安全的核心防线：认证（你是谁）、授权（你能做什么）、防护（如何阻止攻击），并能用 Java/Spring Boot 实现。

## Action

### 第一道防线：认证（Authentication）

#### 方案对比

| 方案 | 原理 | 优点 | 缺点 | 适用场景 |
|------|------|------|------|----------|
| Session-Cookie | 服务端存 session，客户端存 cookie | 实现简单，服务端可控 | 扩展需共享 session，不适合移动端 | 传统 Web 应用 |
| JWT（JSON Web Token） | 服务端签发 token，客户端存，每次请求携带 | 无状态，跨域友好，适合移动端 | 无法主动失效，payload 不宜过大 | 分布式系统、前后端分离 |
| OAuth2 | 授权码流程，第三方授权 | 权限范围可控，支持第三方 | 流程复杂，需额外授权服务器 | 开放 API、第三方登录 |

**推荐**：JWT + OAuth2 组合——JWT 做内部服务间认证，OAuth2 做第三方登录。

#### JWT 在 Spring Boot 中的实现简例

```java
// 生成 JWT
public String generateToken(UserDetails user) {
    return Jwts.builder()
        .setSubject(user.getUsername())
        .claim("role", user.getRole())
        .setIssuedAt(new Date())
        .setExpiration(new Date(System.currentTimeMillis() + 3600_000)) // 1h
        .signWith(SignatureAlgorithm.HS256, SECRET_KEY)
        .compact();
}

// JWT 过滤器
public class JwtAuthFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) {
        String token = extractToken(request);
        if (token != null && validate(token)) {
            SecurityContextHolder.getContext()
                .setAuthentication(buildAuthentication(token));
        }
        chain.doFilter(request, response);
    }
}
```

**JWT 安全要点**：密钥定期轮换、payload 不存敏感信息、过期时间合理（access token 15m-1h, refresh token 7d-30d）、使用 HTTPS 传输。

### 第二道防线：授权（Authorization）

#### RBAC（Role-Based Access Control）

```java
@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/products")
    public Result<Page<ProductDTO>> listAllProducts() { ... }

    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    @PutMapping("/products/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id,
                                     @RequestBody StatusUpdateReq req) { ... }
}
```

**权限模型**：用户 → 角色（集合）→ 权限（集合）。Spring Security 提供了 `@PreAuthorize`、`@PostAuthorize`、`@Secured` 等注解，配合 `UserDetailsService` 实现数据级权限控制。

### 第三道防线：限流（Rate Limiting）

防止单个用户或 IP 过度调用 API。常见算法：

| 算法 | 原理 | 特点 |
|------|------|------|
| 令牌桶（Token Bucket） | 固定速率放令牌，消费一个取一个 | 允许突发流量，平滑 |
| 漏桶（Leaky Bucket） | 请求排队，固定速率处理 | 严格控制速率，不适合突发 |
| 滑动窗口（Sliding Window） | 统计窗口内请求数 | 精确限流，但内存占用高 |

Spring Boot 实现（使用 Bucket4j 或 Spring Cloud Gateway Redis RateLimiter）：

```java
// 基于 Redis 的滑动窗口限流（简化版）
public boolean tryAcquire(String key, int maxRequests, long windowMs) {
    String redisKey = "rate_limit:" + key;
    Long count = redisTemplate.opsForValue().increment(redisKey);
    if (count == 1) {
        redisTemplate.expire(redisKey, windowMs, TimeUnit.MILLISECONDS);
    }
    return count <= maxRequests;
}

// 使用示例：只允许每秒 10 次
// GET /api/v1/products?page=1  →  key = "user:123:products"
```

### 第四道防线：输入校验（Input Validation）

永远不要信任客户端输入。校验分两层：

**1. 声明式校验（Bean Validation）**

```java
public class CreateProductReq {
    @NotBlank(message = "商品名称不能为空")
    @Size(max = 100, message = "名称最长 100 字符")
    private String name;

    @NotNull
    @DecimalMin(value = "0.01", message = "价格必须大于 0")
    @DecimalMax(value = "999999", message = "价格不能超过 999999")
    private BigDecimal price;

    @Max(value = 99999, message = "库存不能超过 99999")
    private Integer stock;

    @Pattern(regexp = "^https?://.*", message = "图片链接格式不正确")
    private String imageUrl;
}
```

**2. 业务校验（Service 层）**

```java
public void validateCreateOrder(OrderCreateReq req) {
    if (req.getItems().isEmpty()) {
        throw new BusinessException(400, "订单至少包含一个商品");
    }
    // 校验商品是否存在、库存是否充足
    req.getItems().forEach(item -> {
        Product product = productRepository.findById(item.getProductId())
            .orElseThrow(() -> new BusinessException(404, "商品不存在: " + item.getProductId()));
        if (product.getStock() < item.getQuantity()) {
            throw new BusinessException(400, "库存不足: " + product.getName());
        }
    });
}
```

### 第五道防线：常见 Web 攻击防护

| 攻击类型 | 原理 | 防护措施 |
|----------|------|----------|
| **SQL 注入** | 在输入中拼接 SQL 语句 | 永远使用参数化查询（PreparedStatement / JPA 参数绑定），禁止拼接 SQL |
| **XSS 跨站脚本** | 注入恶意脚本到页面 | 输入过滤 + 输出转义（`HtmlUtils.htmlEscape`），设置 `Content-Security-Policy` 头 |
| **CSRF 跨站请求伪造** | 利用用户已登录状态发起恶意请求 | 使用 CSRF Token（Spring Security 默认开启）、SameSite Cookie 属性 |
| **CORS 跨域** | 浏览器限制跨域请求 | 精确配置允许的 Origin，不要用 `*` |
| **DDoS** | 大量请求耗尽资源 | 限流 + 熔断（Sentinel / Hystrix / Resilience4j）|
| **参数污染** | 传多个同名参数绕过校验 | 框架层面统一处理，只取第一个或报错 |

**Spring Security 配置示例**：

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())  // 如果是纯 API 服务，可关闭 CSRF
            .cors(cors -> cors.configurationSource(corsConfigSource()))
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/public/**").permitAll()
                .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    private CorsConfigurationSource corsConfigSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(Arrays.asList("https://admin.ai-shop.com"));
        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
```

## Result

API 安全是一个**层层递进**的防御体系：

1. **认证**：确认"你是谁"，JWT / OAuth2 / Session，选适合场景的方案
2. **授权**：确认"你能做什么"，RBAC 是最通用的模型
3. **限流**：防止单一来源耗尽资源
4. **校验**：拒绝非法输入，防 SQL 注入 / XSS
5. **配置**：CORS / CSRF / HTTPS 等基础设施配置

> 面试金句："API 安全不是单点问题，而是从认证到限流到入侵检测的纵深防御体系。我们使用 JWT 做无状态认证、Spring Security 做 RBAC 授权、Sentinel 做限流熔断、参数化查询防 SQL 注入，并且统一在网关层做 CORS 和请求清洗。"

---

## 附：安全检查清单

- [ ] 是否所有暴露的 API 都经过认证？（除了 `/public/**`）
- [ ] 是否使用了 HTTPS 而不是 HTTP？
- [ ] JWT 密钥是否定期轮换？是否使用了 RS256 而非 HS256？
- [ ] 是否限制了请求 Body 大小？
- [ ] 是否对文件上传做了类型和大小限制？
- [ ] 是否在日志中屏蔽了敏感信息（密码、Token、手机号）？
- [ ] 是否有统一的限流配置？
- [ ] 是否关闭了框架的默认错误页面？（避免泄露堆栈信息）