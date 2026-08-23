# 服务间调用 — OpenFeign

## 声明式 HTTP 客户端

OpenFeign 让微服务之间的 HTTP 调用变得像调用本地方法一样简单。你只需要定义一个接口，加上注解，Feign 就会自动生成实现类。

```java
@FeignClient(name = "order-service", path = "/order")
public interface OrderFeignClient {
    @GetMapping("/{id}")
    OrderDTO getOrderById(@PathVariable Long id);

    @PostMapping("/create")
    Long createOrder(@RequestBody CreateOrderRequest request);
}
```

调用方只需要注入 `OrderFeignClient`，底层自动完成 HTTP 请求、序列化、反序列化。

---

## 对比 OpenFeign vs RestTemplate

| 特性 | OpenFeign | RestTemplate |
|------|-----------|-------------|
| 编程方式 | 声明式（接口 + 注解） | 命令式（手动构造请求） |
| 代码量 | 少 | 多 |
| 可读性 | 高 | 低 |
| 负载均衡 | 内置集成 Ribbon / Spring Cloud LoadBalancer | 需手动配置 |
| 熔断降级 | 内置 `fallback` 支持 | 需手动 try-catch |
| 耦合度 | 解耦 | 耦合 |

**结论：新项目首选 OpenFeign。**

---

## 动态代理原理

OpenFeign 的核心是 **JDK 动态代理**。流程如下：

```
1. 启动时 @EnableFeignClients 扫描 @FeignClient 接口
2. 为每个接口创建 JDK 动态代理对象
3. InvocationHandler 拦截方法调用
4. 解析方法上的 @GetMapping / @PostMapping 等注解
5. 拼接 URL、构造请求参数
6. 通过 HTTP 客户端（Apache HttpClient / OkHttp）发送请求
7. 反序列化响应为返回类型
```

关键源码链路：

```
FeignClientFactoryBean.getObject()
    → Targeter.target()
        → ReflectiveFeign.newInstance()
            → FeignInvocationHandler (InvocationHandler)
                → MethodHandler.invoke()
                    → SynchronousMethodHandler.invoke()
                        → Client.execute()  // 实际 HTTP 调用
```

---

## 超时 / 重试 / 熔断配置

### 超时配置

```yaml
feign:
  client:
    config:
      default:
        connectTimeout: 5000       # 连接超时，5 秒
        readTimeout: 10000         # 读取超时，10 秒
      order-service:               # 针对特定服务
        connectTimeout: 3000
        readTimeout: 5000
```

### 重试配置

```java
@Bean
public Retryer feignRetryer() {
    return new Retryer.Default(
        100,     // 重试间隔起始 100ms
        1000,    // 最大间隔 1s
        3        // 最多重试 3 次
    );
}
```

### 熔断降级（集成 Sentinel / Resilience4j）

```yaml
feign:
  sentinel:
    enabled: true
```

```java
@FeignClient(name = "order-service", fallback = OrderFallback.class)
public interface OrderFeignClient {
    @GetMapping("/{id}")
    OrderDTO getOrderById(@PathVariable Long id);
}

@Component
public class OrderFallback implements OrderFeignClient {
    @Override
    public OrderDTO getOrderById(Long id) {
        return new OrderDTO();  // 返回默认空对象
    }
}
```

---

## 拦截器传递 Token

在微服务中，调用链需要传递用户身份。通过 `RequestInterceptor` 自动将 Token 写入每个 Feign 请求的 Header。

```java
@Component
public class FeignAuthInterceptor implements RequestInterceptor {

    @Override
    public void apply(RequestTemplate template) {
        // 从 RequestContext 获取当前请求的 Token
        ServletRequestAttributes attributes =
            (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();
            String token = request.getHeader("Authorization");
            if (token != null) {
                template.header("Authorization", token);
            }
        }

        // 传递请求来源
        template.header("X-Request-From", "gateway");
    }
}
```

这样，A 服务调用 B 服务时，B 服务也能拿到原始的 Token 进行鉴权，实现 **Token 链路传递**。

---

## 服务降级：FallbackFactory

`Fallback` 只能提供默认返回值，但无法知道异常原因。`FallbackFactory` 可以拿到异常信息，方便排查问题。

```java
@FeignClient(name = "order-service", fallbackFactory = OrderFallbackFactory.class)
public interface OrderFeignClient {
    @GetMapping("/{id}")
    OrderDTO getOrderById(@PathVariable Long id);
}

@Component
@Slf4j
public class OrderFallbackFactory implements FallbackFactory<OrderFeignClient> {
    @Override
    public OrderFeignClient create(Throwable cause) {
        log.error("调用 order-service 失败", cause);
        return new OrderFeignClient() {
            @Override
            public OrderDTO getOrderById(Long id) {
                // 根据异常类型决定返回策略
                if (cause instanceof TimeoutException) {
                    return new OrderDTO();  // 超时返回空
                }
                throw new RuntimeException("服务不可用", cause);  // 其他异常抛错
            }
        };
    }
}
```

---

## 关键要点速记

| 问题 | 答案 |
|------|------|
| OpenFeign 底层 | JDK 动态代理 |
| 熔断注解 | `@FeignClient(fallback = ...)` |
| 拦截器接口 | `RequestInterceptor` |
| 超时配置 | `connectTimeout` / `readTimeout` |
| 重试类 | `Retryer.Default` |
| 降级增强 | `FallbackFactory` 可获取异常原因 |
| 调用链路追踪 | 配合 `RequestInterceptor` 传递 TraceId |