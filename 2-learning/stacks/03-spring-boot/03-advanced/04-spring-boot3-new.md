# Spring Boot 3.x 新特性 — Jakarta EE · 虚拟线程 · Observability

> 等级：🎯 面试进阶
> 目标：掌握 Spring Boot 3.x 的核心新特性，以及从 2.x 升级到 3.x 的完整迁移指南。

---

## 一、Spring Boot 版本演进

| 版本 | 发布时间 | 核心特性 | Java 要求 |
|------|---------|---------|----------|
| Spring Boot 2.0 | 2018.03 | Spring 5、WebFlux、Metrics | Java 8+ |
| Spring Boot 2.7 | 2022.05 | 自动配置新机制铺垫 | Java 8+ |
| **Spring Boot 3.0** | 2022.11 | **Jakarta EE 9**、AOT、GraalVM | **Java 17+** |
| **Spring Boot 3.1** | 2023.05 | Testcontainers 简洁化、SSL 绑定 | Java 17+ |
| **Spring Boot 3.2** | 2023.11 | **虚拟线程**、RestClient | Java 17+ |
| Spring Boot 3.3 | 2024.05 | CDS 支持、优雅停机改善 | Java 17+ |
| Spring Boot 3.4 | 2024.11 | 结构化解、配置简化 | Java 17+ |

> 2026 年视角：Spring Boot 3.x 已是绝对主流，2.x 已进入安全维护期，**新项目一律用 3.x**。

---

## 二、Spring Boot 3 核心新特性

### 2.1 Jakarta EE 9 —— 包名迁移

Spring Boot 3 从 Java EE（javax.*）迁移到 Jakarta EE（jakarta.*）：

| 迁移内容 | 2.x (javax.*) | 3.x (jakarta.*) |
|---------|--------------|----------------|
| Servlet | `javax.servlet.*` | `jakarta.servlet.*` |
| 持久化 | `javax.persistence.*` | `jakarta.persistence.*` |
| 校验 | `javax.validation.*` | `jakarta.validation.*` |
| 事务 | `javax.transaction.*` | `jakarta.transaction.*` |
| JSON 绑定 | `javax.json.bind.*` | `jakarta.json.bind.*` |

```java
// 2.x 写法
import javax.persistence.Entity;
import javax.validation.constraints.NotNull;

// 3.x 写法
import jakarta.persistence.Entity;
import jakarta.validation.constraints.NotNull;
```

> **注意**：`QuickFix`（一键替换 javax → jakarta）在 IDEA 中是批量替换的最快路径。

### 2.2 虚拟线程（Virtual Threads）

Spring Boot 3.2 支持 `spring.threads.virtual.enabled=true`，用**虚拟线程**替代平台线程处理请求：

```yaml
spring:
  threads:
    virtual:
      enabled: true   # 开启虚拟线程（Tomcat 每个请求跑在虚拟线程上）
```

虚拟线程的优势：

| 对比项 | 平台线程（Platform Thread） | 虚拟线程（Virtual Thread） |
|--------|---------------------------|---------------------------|
| 创建成本 | 高（1MB 栈） | 极低（几十 KB） |
| 支持数量 | 数千 | 百万级 |
| 阻塞代价 | 占用 OS 线程 | 挂起后让出 OS 线程 |
| 调度 | OS 调度 | JVM 调度（M:N） |

```java
// 手动使用虚拟线程
ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
executor.submit(() -> {
    // IO 密集型任务：阻塞时自动让出底层 OS 线程
    String result = httpClient.get("https://api.mall.com/order");
});

// 注意：synchronized 会钉住（pin）虚拟线程，尽量用 ReentrantLock
private final ReentrantLock lock = new ReentrantLock();
```

### 2.3 可观测性（Observability）

Spring Boot 3 引入 `io.micrometer.tracing`（Micrometer Tracing）统一链路追踪：

```xml
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-tracing-bridge-brave</artifactId>
</dependency>
<dependency>
    <groupId>io.zipkin.reporter2</groupId>
    <artifactId>zipkin-reporter-brave</artifactId>
</dependency>
```

```yaml
management:
  tracing:
    sampling:
      probability: 1.0    # 采样率 100%（生产建议 0.1）
  zipkin:
    tracing:
      endpoint: http://zipkin:9411/api/v2/spans
```

自动为每个请求创建 TraceId + SpanId，贯穿整个调用链，配合日志自动携带 traceId：

```java
// 业务代码中手动创建一个 Span
@RestController
public class OrderController {

    private final Tracer tracer;

    public OrderController(Tracer tracer) {
        this.tracer = tracer;
    }

    @GetMapping("/api/orders/{id}")
    public Order getOrder(@PathVariable Long id) {
        Span span = tracer.nextSpan().name("query-order").start();
        try {
            return orderService.findById(id);
        } finally {
            span.end();
        }
    }
}
```

### 2.4 RestClient —— 新的 HTTP 客户端

```java
// 声明式 REST 客户端（Spring Boot 3.2+）
@Configuration
public class RestClientConfig {

    @Bean
    public RestClient productClient(RestClient.Builder builder) {
        return builder
            .baseUrl("http://product-service:8080")
            .defaultHeader("X-Api-Key", "${api.key}")
            .requestInterceptor((req, body, exec) -> {
                log.info("调用商品服务: {}", req.getURI());
                return exec.execute(req, body);
            })
            .build();
    }
}

@RestController
public class OrderController {
    private final RestClient productClient;

    public OrderController(RestClient productClient) {
        this.productClient = productClient;
    }

    public Product getProduct(Long id) {
        // 同步调用
        return productClient.get()
            .uri("/api/products/{id}", id)
            .retrieve()
            .body(Product.class);
    }
}
```

### 2.5 HttpClient / HttpRequest / HttpResponse —— 结构化解

Spring Boot 3.4 新增 `HttpClient`、`HttpRequest`、`HttpResponse` 接口，以及 `RestClient` 的统一结构化 API，降低多模块间的 API 定义成本。

---

## 三、从 2.x 迁移到 3.x 指南

### 3.1 迁移步骤

**第一步：升级 JDK**

- JDK 8/11 → JDK 17+
- 注意：JDK 17 是 Spring Boot 3 的最低要求

**第二步：升级依赖**

```
Spring Boot: 2.7.x → 3.3.x
Spring Framework: 5.3.x → 6.1.x
Java EE → Jakarta EE 9
```

**第三步：全局替换 javax → jakarta**

IDEA 批量替换：`javax.` → `jakarta.`

**第四步：处理破坏性变更**

| 变更点 | 2.x 写法 | 3.x 写法 |
|--------|---------|---------|
| spring.factories | `EnableAutoConfiguration=全类名` | `AutoConfiguration.imports` 每行一个类 |
| `spring.redis.*` | `spring.redis.host=...` | `spring.data.redis.*` |
| `spring.datasource.*` | 不变 | 不变（仅 driver 类注意） |
| Jackson | `com.fasterxml.jackson` | 不变 |
| `@MockBean` | `@MockBean` | `@MockitoBean`（3.4+） |
| Security | `WebSecurityConfigurerAdapter` | SecurityFilterChain Bean |
| Actuator 端点 | `sensitive=true` | 默认全部需认证 |

**第五步：更新自定义 Starter 和测试**

```java
// 自定义 Starter 注册文件迁移
// 旧：META-INF/spring.factories
// 新：META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
// 内容：每行一个自动配置类
com.example.sms.SmsAutoConfiguration
```

### 3.2 兼容性检查清单

```xml
<!-- pom.xml 检查：
1. 所有 jee 相关依赖改为 jakarta 版本（如 jakarta.persistence-api）
2. 检查三方库是否支持 Spring Boot 3（MyBatis-spring-boot-starter 3.x, Redisson 3.2x+）
3. Spring Cloud 升级到 2022.0.x（对应 Boot 3.0）
-->
```

---

## 四、Spring Boot 3 构建依赖关系

```xml
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.3.2</version>
    <relativePath/>
</parent>

<properties>
    <java.version>17</java.version>
</properties>
```

---

## 五、面试要点

| 问题 | 一句话答案 |
|------|-----------|
| Spring Boot 3 最低要求什么 JDK？ | Java 17 |
| javax 和 jakarta 什么关系？ | Spring Boot 3 全面迁移到 Jakarta EE 9，包名从 javax.* 变为 jakarta.* |
| 虚拟线程适合什么场景？ | IO 密集型任务（大量阻塞等待），百万级并发时表现优异 |
| 开启虚拟线程要注意什么？ | synchronized 会钉住虚拟线程，改用 ReentrantLock；限制平台线程 API |
| 可观测性三件套是什么？ | Logging（日志）+ Metrics（指标）+ Tracing（链路追踪） |
| Spring Boot 3 怎么支持链路追踪？ | Micrometer Tracing（Brave/OpenTelemetry 桥接） |

> 掌握了新特性，进入项目实战篇：看 Spring Boot 在 AI 商城中的落地。