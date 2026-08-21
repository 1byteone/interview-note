# API 网关 (Gateway) 面试题大全

## 📚 知识体系

```
Gateway 核心概念
├── 路由 (Route)
├── 断言 (Predicate)
├── 过滤器 (Filter)
├── 路由定位器 (RouteLocator)
├── WebFlux 响应式
└── 过滤器链 (Filter Chain)

Gateway 功能
├── 路由转发
├── 负载均衡
├── 限流熔断
├── 认证鉴权
├── 请求/响应改写
├── 跨域 (CORS)
├── 灰度发布
├── 日志/监控
└── 协议转换

Gateway 对比
├── Zuul 1.x（阻塞 IO）
├── Zuul 2.x（异步，未成熟）
├── Spring Cloud Gateway（响应式）
├── Nginx（高性能，非 Java）
└── Kong（基于 OpenResty）
```

---

## 🎯 Level 1：基础题

### 1. Spring Cloud Gateway 是什么？为什么用 API 网关？
**答案**：
Spring Cloud Gateway 是基于 Spring WebFlux 的 API 网关，提供路由、过滤、限流、鉴权等功能。

**API 网关的作用**：
1. **统一入口**：所有服务统一从网关接入
2. **路由转发**：根据配置将请求转发到对应服务
3. **横切关注点**：鉴权、日志、限流、监控统一处理
4. **协议转换**：内外协议转换（HTTP → gRPC）
5. **安全防护**：防 SQL 注入、XSS、CSRF
6. **灰度发布**：按权重/Header 路由到不同版本

### 2. Gateway 和 Zuul 的区别？
**答案**：

| 特性 | Spring Cloud Gateway | Zuul 1.x |
|------|---------------------|----------|
| 底层 | WebFlux（Reactor） | Servlet（阻塞 IO） |
| 性能 | 高（非阻塞+异步） | 低（每个请求一个线程） |
| 异步 | 原生支持 | 不支持 |
| 配置方式 | YAML + Java DSL | YAML + Filter |
| 长连接 | WebSocket 支持 | 不支持 |
| 社区 | 活跃 | 已停止维护 |

---

## 🎯 Level 2：进阶题

### 3. Gateway 的工作流程是什么？
**答案**：

```text
客户端请求
    ↓
Gateway Handler Mapping
    ↓ 匹配路由 (Predicate)
    ↓
Gateway Web Handler
    ↓ 执行过滤器链
    ├── Pre Filters（前置）
    │   ├── 鉴权
    │   ├── 限流
    │   ├── 请求改写
    │   └── 日志
    ├── 转发到目标服务
    └── Post Filters（后置）
        ├── 响应改写
        ├── 错误处理
        └── 日志
    ↓
返回客户端
```

**代码示例**：
```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: order-service
          uri: lb://order-service
          predicates:
            - Path=/order/**
          filters:
            - StripPrefix=1
            - name: RequestRateLimiter
              args:
                key-resolver: "#{@userKeyResolver}"
                redis-rate-limiter:
                  replenishRate: 100
                  burstCapacity: 200
```

### 4. Gateway 的过滤器有哪些类型？
**答案**：

**按作用范围**：
| 类型 | 接口 | 说明 |
|------|------|------|
| GatewayFilter | 路由过滤器 | 只对特定路由生效 |
| GlobalFilter | 全局过滤器 | 对所有路由生效 |

**按执行顺序**：
| 类型 | 说明 | 示例 |
|------|------|------|
| Pre Filter | 请求转发前执行 | 鉴权、限流 |
| Post Filter | 收到响应后执行 | 日志、响应改写 |

**常见内置过滤器**：
```yaml
filters:
  - StripPrefix=1           # 去除路径前缀
  - PrefixPath=/api        # 添加路径前缀
  - AddRequestHeader=X-Request-Id, 123  # 添加请求头
  - AddResponseHeader=X-Response-Time, 100  # 添加响应头
  - SetStatus=200          # 设置状态码
  - Retry=3                # 重试
  - CircuitBreaker=myCircuitBreaker  # 熔断
  - RequestRateLimiter=... # 限流
```

---

## 🎯 Level 3：高级题

### 5. 如何实现 Gateway 的动态路由？
**答案**：
**问题**：默认路由配置在 `application.yml` 中，修改需要重启。

**方案：Nacos 动态路由**

```java
@Component
public class NacosDynamicRouteService implements ApplicationEventPublisherAware {
    
    @Autowired
    private RouteDefinitionWriter routeDefinitionWriter;
    
    private ApplicationEventPublisher publisher;
    
    // 监听 Nacos 配置变更
    @NacosConfigListener(dataId = "gateway-routes", groupId = "DEFAULT_GROUP")
    public void onRouteChange(String config) {
        // 解析 JSON 路由配置
        List<RouteDefinition> routes = JSON.parseArray(config, RouteDefinition.class);
        
        // 清空旧路由
        this.routeDefinitionWriter.delete(Mono.just("order-service")).subscribe();
        
        // 添加新路由
        routes.forEach(route -> {
            routeDefinitionWriter.save(Mono.just(route)).subscribe();
            publisher.publishEvent(new RefreshRoutesEvent(this));
        });
    }
}
```

### 6. Gateway 如何实现限流？
**答案**：

**内置 RequestRateLimiter 过滤器**：
```yaml
filters:
  - name: RequestRateLimiter
    args:
      key-resolver: "#{@userKeyResolver}"   # 限流 key（用户 ID / IP）
      redis-rate-limiter:
        replenishRate: 100    # 每秒令牌数
        burstCapacity: 200    # 令牌桶容量
```

**自定义 KeyResolver**：
```java
@Bean
public KeyResolver userKeyResolver() {
    return exchange -> {
        // 按用户 ID 限流
        String userId = exchange.getRequest().getHeaders().getFirst("X-User-Id");
        if (userId == null) {
            return Mono.just(exchange.getRequest().getRemoteAddress().getAddress().getHostAddress());
        }
        return Mono.just(userId);
    };
}
```

**结合 Sentinel**：
```yaml
filters:
  - name: SentinelGatewayFilter
    args:
      fallback: "fallbackUri"
```

---

## 🎯 Level 4：专家题

### 7. Gateway 的高可用如何设计？
**答案**：

**架构方案**：
```
DNS（轮询）
    ↓
Nginx 集群（负载均衡）
    ↓
Gateway 集群（多实例）
    ├── 实例1 → 路由到服务
    ├── 实例2 → 路由到服务
    └── 实例3 → 路由到服务
    ↓
后端微服务集群
```

**高可用策略**：
1. **多实例部署**：至少 2 个 Gateway 实例
2. **无状态设计**：Gateway 不存储会话状态
3. **Nacos 健康检查**：自动剔除不健康实例
4. **熔断降级**：下游服务故障时返回 fallback
5. **限流保护**：防止流量洪峰击穿 Gateway

### 8. Gateway 性能优化有哪些方法？
**答案**：
1. **响应式编程**：避免阻塞操作（数据库、同步 IO）
2. **连接池优化**：合理配置 HTTP 连接池
3. **路由缓存**：路由规则缓存到本地
4. **过滤器优化**：精简过滤器链，耗时操作异步化
5. **线程池隔离**：不同服务使用不同线程池
6. **指标监控**：监控 QPS、延迟、错误率


## 📖 学习资源

### 推荐项目
- [Spring Cloud Gateway 官方文档](https://spring.io/projects/spring-cloud-gateway)
- [Spring Cloud Alibaba Gateway 集成](https://sca.aliyun.com/)

### 最佳实践
1. 生产环境至少 2 个 Gateway 实例
2. 核心路由规则配置在 Nacos 动态下发
3. 所有服务统一从 Gateway 接入
4. 配置合理的超时和重试策略
5. 监控 Gateway 的 QPS、错误率、延迟