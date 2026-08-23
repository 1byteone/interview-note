# API 网关 — Spring Cloud Gateway

## 网关的定位

在微服务架构中，网关是 **系统的唯一入口**，所有外部请求先到达网关，再由网关路由到后端服务。它承担了以下职责：

- **统一入口**：客户端只需知道网关地址，无需感知后端服务数量
- **鉴权**：在网关层统一校验 Token，避免各服务重复实现
- **限流**：对突发流量进行控制，保护后端服务
- **日志**：统一记录请求日志、调用链路
- **跨域**：统一处理 CORS 策略
- **协议转换**：外部 HTTP 请求转发为内部 gRPC 或 Dubbo 调用

---

## 核心三要素：Route / Predicate / Filter

```
请求 → Predicate 匹配 → Filter 链处理 → 转发到目标服务
```

### Route（路由）

路由是网关的基本单元，包含 ID、目标 URI、Predicate 集合、Filter 集合。

```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: order-service
          uri: lb://order-service          # 负载均衡
          predicates:
            - Path=/api/order/**
          filters:
            - StripPrefix=1                # 去掉 /api 前缀
```

### Predicate（断言）

匹配条件，决定请求是否走这条路由。

| 断言类型 | 示例 | 说明 |
|----------|------|------|
| Path | `Path=/api/order/**` | 路径匹配 |
| Method | `Method=GET,POST` | HTTP 方法 |
| Header | `Header=X-Token, \d+` | 请求头匹配 |
| Query | `Query=userId` | 参数匹配 |
| Cookie | `Cookie=sessionId, abc` | Cookie 匹配 |
| After/Before | `After=2025-01-01T00:00:00Z` | 时间匹配 |

### Filter（过滤器）

对请求或响应进行加工。分两类：

- **Pre Filter**：路由前执行，用于鉴权、参数校验、添加请求头
- **Post Filter**：路由后执行，用于修改响应体、记录响应日志

---

## 过滤器链

网关的 Filter 执行顺序如下：

```
客户端请求
    → GatewayFilter 链（按 @Order 排序）
    → GlobalFilter 链（按 @Order 排序）
    → 路由到目标服务
    → 响应返回
    → GlobalFilter 链（Post 阶段）
    → GatewayFilter 链（Post 阶段）
    → 返回客户端
```

### 内置 GatewayFilter 示例

```yaml
filters:
  - StripPrefix=1          # 去掉路径前缀
  - AddRequestHeader=X-Request-Source, gateway
  - AddResponseHeader=X-Response-Time, ${#timestamp}
  - PrefixPath=/api        # 添加前缀
  - Retry=3                # 重试 3 次
  - RequestRateLimiter     # 限流
```

---

## 自定义鉴权过滤器

```java
@Component
@Order(-100)  // 优先级最高
public class AuthGlobalFilter implements GlobalFilter {

    private static final List<String> WHITE_LIST = Arrays.asList(
        "/api/user/login", "/api/user/register"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        // 白名单放行
        if (WHITE_LIST.contains(path)) {
            return chain.filter(exchange);
        }

        // 从 Header 获取 Token
        String token = exchange.getRequest().getHeaders()
            .getFirst("Authorization");

        if (token == null || !token.startsWith("Bearer ")) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        // 解析 Token（示例：JWT 解析）
        try {
            Claims claims = Jwts.parser()
                .setSigningKey("secret-key".getBytes())
                .parseClaimsJws(token.replace("Bearer ", ""))
                .getBody();
            // 将用户信息传递给下游
            ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                .header("X-User-Id", claims.getSubject())
                .build();
            return chain.filter(exchange.mutate().request(mutatedRequest).build());
        } catch (Exception e) {
            exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
            return exchange.getResponse().setComplete();
        }
    }
}
```

---

## 网关在微服务架构中的位置

```
                    ┌─────────────┐
                    │  客户端      │
                    └──────┬──────┘
                           │ HTTP
                    ┌──────▼──────┐
                    │  Gateway    │ ← 统一入口、鉴权、限流、日志
                    └──┬───┬───┬──┘
           ┌───────────┘   │   └───────────┐
           ▼               ▼               ▼
     ┌──────────┐   ┌──────────┐   ┌──────────┐
     │ Order     │   │ User     │   │ Payment  │
     │ Service   │   │ Service  │   │ Service  │
     └──────────┘   └──────────┘   └──────────┘
```

### 常见网关选型对比

| 特性 | Spring Cloud Gateway | Zuul 1.x | Kong | Nginx |
|------|---------------------|----------|------|-------|
| 编程模型 | Spring WebFlux（异步非阻塞） | Servlet（同步阻塞） | OpenResty | C 模块 |
| 性能 | 高 | 低 | 高 | 最高 |
| 扩展性 | Java 代码 | Java 代码 | Lua 脚本 | C + Lua |
| 配置管理 | Nacos 动态刷新 | 静态配置 | 数据库 | 静态文件 |

---

## 关键要点速记

| 问题 | 答案 |
|------|------|
| 网关三大核心 | Route、Predicate、Filter |
| 过滤器执行顺序 | `@Order` 值越小越先执行 |
| 自定义鉴权 | 实现 `GlobalFilter` 接口 |
| 负载均衡前缀 | `lb://service-name` |
| 路由匹配方式 | 支持 Path、Method、Header、Query、Cookie |
| 与 Zuul 区别 | Gateway 基于 WebFlux 非阻塞模型，性能更高 |