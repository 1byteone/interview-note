# 01 · Nacos 注册中心 + Spring Cloud Gateway 网关

> 请求到达微服务的第一站。Nacos 负责服务注册与发现，Gateway 负责路由转发、鉴权过滤、限流熔断和全链路追踪。
>
> **对应项目：** `mall-gateway` + `mall-common` + Nacos 配置

---

## 一、基础概念

### 1.1 为什么需要注册中心和网关

| 问题 | 单体架构 | 微服务架构 |
|------|---------|-----------|
| 服务地址管理 | 硬编码 localhost:8080 | Nacos 注册中心动态管理 |
| 路由转发 | 统一入口 | Gateway 按路径分发到不同服务 |
| 鉴权 | 一次拦截 | 网关统一鉴权，下游服务信任 |
| 限流熔断 | 无 | Sentinel 在网关层集中控制 |
| 负载均衡 | 无 | 网关层 LB 分发到多个实例 |

**注册中心**解决"服务在哪"的问题，**网关**解决"请求怎么到服务"的问题。

### 1.2 Nacos 两大核心能力

| 能力 | 说明 | 类比 |
|------|------|------|
| **服务注册与发现** | 服务启动时向 Nacos 注册，下线时注销 | 电话黄页 |
| **配置中心** | 统一管理多环境的配置文件 | 集中配置服务器 |

### 1.3 Spring Cloud Gateway 核心组件

```
请求 →  Gateway Handler Mapping →  Route Predicate(匹配路由)
        ↓
      Gateway Filter Chain(过滤器链)
        ↓
      Proxy Filter(代理到目标服务)
        ↓
      目标微服务响应 → 返回客户端
```

| 组件 | 说明 | 本项目中的体现 |
|------|------|--------------|
| **Route** | 路由规则 (id + uri + predicate + filter) | 12+ 条路由规则 |
| **Predicate** | 匹配条件 (Path, Method, Header...) | `Path=/api/product/**` |
| **Filter** | 拦截处理 (Auth, Rt, Sentinel...) | `AuthGatewayFilterFactory` |
| **LoadBalancer** | 负载均衡 (轮询、权重) | `lb://mall-product-service` |

---

## 二、进阶机制

### 2.1 项目中的路由配置

```yaml
spring:
  cloud:
    gateway:
      routes:
        # 商品服务
        - id: mall-product-service
          uri: lb://mall-product-service          # 负载均衡到 product-service
          predicates:
            - Path=/api/product/**                # 路径匹配
          filters:
            - StripPrefix=1                       # 去掉 /api 前缀

        # 秒杀活动
        - id: mall-seckill-product-service
          uri: lb://mall-seckill-service
          predicates:
            - Path=/api/seckill/product/**
          filters:
            - StripPrefix=1

        # 订单服务
        - id: mall-order-service
          uri: lb://mall-order-service
          predicates:
            - Path=/api/order/**
          filters:
            - StripPrefix=1
```

**路由匹配流程：** 请求 `GET /api/product/page?category=手机` → 网关遍历所有路由规则 → 匹配 `Path=/api/product/**` → `StripPrefix=1` 去掉 `/api` → 转发到 `http://mall-product-service/product/page?category=手机`

### 2.2 AuthGatewayFilterFactory —— 自定义过滤器工厂

Spring Cloud Gateway 支持自定义过滤器工厂，通过继承 `AbstractGatewayFilterFactory<Config>` 实现。

**配置方式：**

```yaml
spring:
  cloud:
    gateway:
      default-filters:
        - Auth=/api/v1/**,/api/user/login,/api/pay/**,...
```

**`Auth=` 这个短格式是如何工作的？**

```java
@Component
public class AuthGatewayFilterFactory extends AbstractGatewayFilterFactory<AuthGatewayFilterFactory.Config> {

    // 定义短格式参数顺序
    @Override
    public List<String> shortcutFieldOrder() {
        return Arrays.asList("patterns", "matchTrailingSlash");
    }

    // 定义短格式解析类型：逗号分隔列表 + 尾部布尔标记
    @Override
    public ShortcutType shortcutType() {
        return ShortcutType.GATHER_LIST_TAIL_FLAG;
    }
```

**`Auth=/api/v1/**,/api/user/login,...` 会被解析为：**
- `patterns = ["/api/v1/**", "/api/user/login", "/api/pay/**", ...]`
- `matchTrailingSlash = true`（默认）

**过滤器核心逻辑：**

```java
@Override
public GatewayFilter apply(Config config) {
    return new GatewayFilter() {
        @Override
        public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
            String path = request.getURI().getPath();

            // 1. 白名单放行（登录、注册、接口文档等不校验 Token）
            for (String pattern : config.getPatterns()) {
                if (PATH_MATCHER.match(pattern, path)) {
                    // 注入 X-Gateway-Secret 供下游服务鉴权
                    ServerHttpRequest newRequest = request.mutate()
                            .header("X-Gateway-Secret", "mall-micro-8080")
                            .build();
                    return chain.filter(exchange.mutate().request(newRequest).build());
                }
            }

            // 2. Token 校验（从 Authorization 头提取）
            String token = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
            // 校验 Token 有效性、过期时间、Redis 黑名单
            jwtUtil.validateTokenWithRedis(token);

            // 3. 解析 Token → 提取用户信息 → 注入请求头
            Claims claims = jwtUtil.parseToken(token);
            ServerHttpRequest newRequest = request.mutate()
                    .header("X-User-Id", userId)
                    .header("X-Gateway-Secret", "mall-micro-8080")
                    .build();

            // 4. Token 刷新（无感知续期）
            String newToken = jwtUtil.createTokenAndStore(userId, claims);
            return chain.filter(exchange.mutate().request(newRequest).build())
                    .then(Mono.fromRunnable(() -> {
                        response.getHeaders().set(HttpHeaders.AUTHORIZATION, newToken);
                    }));
        }
    };
}
```

**核心设计要点：**

| 步骤 | 做了什么 | 为什么 |
|------|---------|--------|
| 白名单放行 | 匹配路径模式，跳过 Token 校验 | 登录/注册/文档不需要认证 |
| 注入网关密钥 | 添加 `X-Gateway-Secret` 请求头 | 下游服务验证请求确实来自网关 |
| Token 校验 | 验证 JWT 签名 + 过期时间 + Redis 黑名单 | 三重防护 |
| 用户信息传递 | 解析 Token 后放 `X-User-Id` 请求头 | 下游服务无需再解析 Token |
| Token 刷新 | 响应头返回新 Token | 无感知续期，用户体验好 |

### 2.3 RtGlobalFilter —— 全链路请求耗时追踪

```java
@Component
@Slf4j
public class RtGlobalFilter implements GlobalFilter, Ordered {

    @Override
    public int getOrder() { return 0; }  // 最高优先级

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 记录请求信息
        String requestInfo = String.format("%s %s%s", method, path, query);

        // 记录开始时间
        long startTime = System.currentTimeMillis();
        log.info("=== 请求开始 ===> [{}] [{}]", requestInfo, startTime);

        // 执行后续过滤器链，结束后计算耗时
        return chain.filter(exchange)
                .doFinally(signalType -> {
                    long duration = System.currentTimeMillis() - startTime;
                    HttpStatusCode statusCode = exchange.getResponse().getStatusCode();
                    // 4xx/5xx 用 warn 级别，其他用 info 级别
                    if (statusCode != null && (statusCode.is4xxClientError() || statusCode.is5xxServerError())) {
                        log.warn("<=== 请求结束 [{}] 状态码: {} 耗时: {}ms", requestInfo, statusCode.value(), duration);
                    } else {
                        log.info("<=== 请求结束 [{}] 状态码: {} 耗时: {}ms", requestInfo, statusCode.value(), duration);
                    }
                });
    }
}
```

**设计要点：**

| 要素 | 说明 |
|------|------|
| `Ordered.getOrder() = 0` | 最高优先级，最先执行 |
| `doFinally` | 无论成功失败都会执行，保证日志记录 |
| 4xx/5xx 用 warn | 异常请求突出显示，便于排查 |
| 打印耗时 | 全链路性能监控的入口数据 |

### 2.4 下游服务的网关密钥校验

下游服务通过 `AuthorizationInterceptor` 验证请求是否来自网关：

```java
@Component
public class AuthorizationInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String secret = request.getHeader("X-Gateway-Secret");
        if (secret == null || !secret.equals("mall-micro-8080")) {
            // 返回 403 禁止访问
            return false;
        }
        return true;
    }
}
```

**为什么需要这个设计？** 防止外部请求绕过网关直接访问微服务。只有经过网关的请求才会携带 `X-Gateway-Secret` 头。

---

## 三、项目现场

### 3.1 网关的完整配置分析

```yaml
spring:
  application:
    name: mall-gateway
  main:
    web-application-type: reactive    # 强制响应式（WebFlux）
  cloud:
    nacos:
      server-addr: 192.168.150.101:8848
    gateway:
      default-filters:
        - Auth=/api/v1/**,/api/user/login,...
      routes:
        # 12+ 条路由规则，覆盖所有微服务
        - id: mall-aisearch-service
          uri: lb://mall-aisearch-service
          predicates:
            - Path=/api/v1/**
          filters:
            - StripPrefix=2
```

**`web-application-type: reactive` 很重要：** Spring Cloud Gateway 基于 WebFlux（响应式编程），不能和传统的 Spring MVC 共存。如果误引入 `spring-boot-starter-web`，启动会冲突。

**`StripPrefix=1` vs `StripPrefix=2`：**

```
请求: /api/product/page       → StripPrefix=1 → /product/page
请求: /api/v1/recommend       → StripPrefix=2 → /recommend
```

### 3.2 网关 + 注册中心 + 负载均衡的完整流程

```
1. 服务启动时，向 Nacos 注册
   mall-product-service → Nacos: {serviceName, ip, port}

2. 网关配置路由 lb://mall-product-service
   lb:// 前缀触发 LoadBalancer

3. 请求到达网关
   GET /api/product/page?category=手机

4. 路由匹配
   Path=/api/product/** → 命中 mall-product-service 路由

5. 负载均衡
   Nacos 返回所有 mall-product-service 实例列表
   LoadBalancer 按轮询策略选择一个实例

6. 过滤器链执行
   AuthGatewayFilterFactory → RtGlobalFilter → ...

7. 转发到目标服务
   http://192.168.150.101:8081/product/page?category=手机
```

---

## 四、面试要点

### Q1: 为什么微服务需要统一网关？

**回答思路：** 四个核心作用：1) **统一入口**——所有请求经过网关，客户端不需要知道每个服务的地址；2) **横切关注点集中处理**——鉴权、限流、日志、跨域等都在网关层完成，下游服务只需关注业务逻辑；3) **安全隔离**——下游服务不暴露公网，通过 `X-Gateway-Secret` 头验证请求来源；4) **路由+负载均衡**——按路径分发到不同服务，支持 lb:// 负载均衡。

### Q2: AuthGatewayFilterFactory 的 Token 刷新机制是怎么设计的？

**回答思路：** 每次请求通过网关时，如果 Token 有效，网关会用当前用户信息重新生成一个新 Token 放在响应头中返回。前端收到新 Token 后更新本地存储。这样用户只要持续操作，Token 就不会过期，实现"无感知续期"。如果 Token 已过期，则返回 401 让前端跳转登录页。

### Q3: Spring Cloud Gateway 和传统 Zuul 网关的区别？

**回答思路：** Gateway 基于 WebFlux（响应式），非阻塞 IO，性能更好；Zuul 1.x 基于 Servlet（阻塞），性能较差。Gateway 原生支持 WebSocket、长连接，Zuul 需要额外配置。Spring Cloud 官方推荐 Gateway 替代 Zuul。

### Q4: 下游服务的 AuthorizationInterceptor 解决了什么问题？

**回答思路：** 防止请求绕过网关直接访问微服务。所有外部请求必须经过网关，网关会注入 `X-Gateway-Secret` 头。下游服务拦截器校验这个头，没有的请求直接返回 403。**双层防护：网关层做 Token 鉴权，服务层做来源校验。**

---

> **下一篇：** [02-COMMON-ARCH.md —— 公共模块与统一架构：Result、异常处理、Feign 接口设计](./02-COMMON-ARCH.md)
>
> 看 mall-common 和 mall-api 两个公共模块如何实现 10 个微服务的统一响应、异常处理和 Feign 接口规范。