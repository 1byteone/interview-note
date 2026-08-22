# 代码题 — 手写 Starter · 条件注解 · HealthIndicator · 测试

> 等级：🎯 面试冲刺
> 目标：面试中高频出现的 Spring Boot 代码题，考察自动配置、条件判断、健康检查、测试框架的综合能力。

---

## 一、自定义 Starter（完整四件套）

### 题目

实现一个"限流 Starter"：任何服务引入该依赖后，通过配置即可开启接口限流（基于令牌桶），并暴露到 Actuator 健康检查。

### 第一步：属性绑定类

```java
package com.example.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 限流配置属性：对应 spring.ratelimit.*
 * spring.ratelimit.enabled=true
 * spring.ratelimit.qps=100
 */
@ConfigurationProperties(prefix = "spring.ratelimit")
public class RateLimitProperties {

    /** 是否启用限流 */
    private boolean enabled = true;

    /** 每秒允许的请求数（QPS） */
    private int qps = 100;

    /** 令牌桶容量 */
    private int capacity = 100;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public int getQps() { return qps; }
    public void setQps(int qps) { this.qps = qps; }
    public int getCapacity() { return capacity; }
    public void setCapacity(int capacity) { this.capacity = capacity; }
}
```

### 第二步：核心服务

```java
package com.example.ratelimit;

import com.google.common.util.concurrent.RateLimiter;

/**
 * 基于令牌桶的限流服务
 */
public class RateLimitService {

    private final RateLimiter rateLimiter;
    private final RateLimitProperties properties;

    public RateLimitService(RateLimitProperties properties) {
        this.properties = properties;
        this.rateLimiter = RateLimiter.create(properties.getQps());
    }

    /**
     * 尝试获取一个许可
     * @return true = 允许通过，false = 被限流
     */
    public boolean tryAcquire() {
        return rateLimiter.tryAcquire();
    }

    public RateLimitProperties getProperties() {
        return properties;
    }
}
```

### 第三步：自动配置类

```java
package com.example.ratelimit;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 限流自动配置
 * 条件：
 *  1. classpath 存在 RateLimiter（引入了 guava）
 *  2. 配置 spring.ratelimit.enabled=true（默认 true）
 */
@AutoConfiguration
@ConditionalOnClass(RateLimiter.class)
@ConditionalOnProperty(prefix = "spring.ratelimit", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(RateLimitProperties.class)
public class RateLimitAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public RateLimitService rateLimitService(RateLimitProperties properties) {
        return new RateLimitService(properties);
    }
}
```

### 第四步：注册文件

```
# src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
com.example.ratelimit.RateLimitAutoConfiguration
```

### 第五步：使用方配置

```yaml
spring:
  ratelimit:
    enabled: true
    qps: 200
    capacity: 200
```

```java
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final RateLimitService rateLimitService;

    public OrderController(RateLimitService rateLimitService) {
        this.rateLimitService = rateLimitService;
    }

    @GetMapping
    public ResponseEntity<?> listOrders() {
        if (!rateLimitService.tryAcquire()) {
            return ResponseEntity.status(429).body("请求过于频繁，请稍后重试");
        }
        return ResponseEntity.ok(orderService.findAll());
    }
}
```

### 面试要点

> **问：为什么要 @ConditionalOnMissingBean？**
> 答：允许使用者替换默认实现。如果用户自定义了 RateLimitService，自动配置就不创建（双保险：用户 Bean 优先级更高 + 自动配置检查容器中已有 Bean）。

---

## 二、自定义条件注解

### 题目

实现一个 `@ConditionalOnWindows`，只有当运行环境是 Windows 时才生效。

### 实现方式一：继承 SpringBootCondition

```java
package com.example.ratelimit.condition;

import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * 自定义条件：仅限 Windows 环境
 */
public class OnWindowsCondition extends SpringBootCondition {

    @Override
    public ConditionOutcome getMatchOutcome(ConditionContext context,
            AnnotatedTypeMetadata metadata) {
        String osName = context.getEnvironment().getProperty("os.name", "");
        if (osName.toLowerCase().contains("windows")) {
            return ConditionOutcome.match("运行在 Windows 上");
        }
        return ConditionOutcome.noMatch("当前系统不是 Windows: " + osName);
    }
}
```

### 组合注解

```java
package com.example.ratelimit.condition;

import org.springframework.context.annotation.Conditional;

import java.lang.annotation.*;

@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Conditional(OnWindowsCondition.class)  // 绑定到自定义 Condition
public @interface ConditionalOnWindows {
}
```

### 使用

```java
@Configuration
public class PlatformConfig {

    @Bean
    @ConditionalOnWindows   // 只有 Windows 才注册
    public WindowsCleanerService windowsCleanerService() {
        return new WindowsCleanerService();
    }
}
```

### 面试要点

> **问：SpringBootCondition 和 Condition 接口区别？**
> 答：SpringBootCondition 是 Boot 对 Condition 的封装，提供 `getMatchOutcome` 抽象（返回匹配 + 原因），更友好；自定义简单条件可实现 Condition 接口的 `matches`。

---

## 三、自定义 HealthIndicator

### 题目

实现一个内存监控的健康检查：当内存使用率超过 90% 时，健康状态为 DOWN；超过 70% 为 WARN（默认 STATUS）。

```java
package com.example.ratelimit.health;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * 内存健康检查
 */
@Component
public class MemoryHealthIndicator implements HealthIndicator {

    private static final double CRITICAL_THRESHOLD = 0.9;
    private static final double WARN_THRESHOLD = 0.7;

    @Override
    public Health health() {
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
        double usage = (double) usedMemory / maxMemory;

        // 内存使用详情
        Health.Builder builder = Health.status("MEMORY")
            .withDetail("used", formatMB(usedMemory))
            .withDetail("max", formatMB(maxMemory))
            .withDetail("usage_percent", String.format("%.1f%%", usage * 100));

        if (usage >= CRITICAL_THRESHOLD) {
            // 超过 90%：DOWN，触发告警
            return builder.down().withDetail("reason", "内存使用率超过 90%，即将 OOM").build();
        } else if (usage >= WARN_THRESHOLD) {
            // 超过 70%：OUT_OF_SERVICE（网关会摘除流量）
            return builder.outOfService().withDetail("reason", "内存使用率超过 70%，需要关注").build();
        }
        return builder.up().build();
    }

    private String formatMB(long bytes) {
        return String.format("%.1f MB", bytes / 1024.0 / 1024.0);
    }
}
```

### 验证

```bash
curl http://localhost:8080/actuator/health

# 响应示例
{
  "status": "UP",
  "components": {
    "memory": {
      "status": "UP",
      "details": {
        "used": "1250.0 MB",
        "max": "2048.0 MB",
        "usage_percent": "61.0%"
      }
    }
  }
}
```

### 面试要点

> **问：Health 四种状态的区别？**
> `UP` 正常；`DOWN` 异常（触发告警、摘除流量）；`OUT_OF_SERVICE` 可用但不处理流量（如磁盘读）/暂不可用；`UNKNOWN` 未知状态。

---

## 四、编写测试（MockMvc + Testcontainers）

### 题目

为限流 API 编写 Controller 测试，并编写一个 Testcontainers 集成测试验证真实数据库。

### 4.1 MockMvc 测试

```java
package com.example.ratelimit.controller;

import com.example.ratelimit.service.RateLimitService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class RateLimitControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RateLimitService rateLimitService;

    @Test
    void shouldPassWhenTokenAvailable() throws Exception {
        // 令牌充足，放行
        given(rateLimitService.tryAcquire()).willReturn(true);

        mockMvc.perform(get("/api/orders"))
            .andExpect(status().isOk());
    }

    @Test
    void shouldReturn429WhenLimited() throws Exception {
        // 被限流，返回 429
        given(rateLimitService.tryAcquire()).willReturn(false);

        mockMvc.perform(get("/api/orders"))
            .andExpect(status().isTooManyRequests())
            .andExpect(jsonPath("$.message").value("请求过于频繁，请稍后重试"));
    }
}
```

### 4.2 Testcontainers 集成测试

```java
package com.example.ratelimit.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
class OrderRepositoryIntegrationTest {

    // @ServiceConnection：自动将容器连接信息绑定到 DataSource
    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
        .withDatabaseName("testdb");

    @Autowired
    private OrderRepository orderRepository;

    @Test
    void shouldSaveAndFindOrderInRealMySQL() {
        // 验证数据真正写入 MySQL 容器
        Order order = new Order(1L, 100L, 2, "CREATED");
        Order saved = orderRepository.save(order);

        assertThat(saved.getId()).isNotNull();
        assertThat(orderRepository.findById(saved.getId())).isPresent();
    }
}
```

```xml
<!-- pom.xml 需要引入 -->
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

---

## 五、代码题总结

| 题目 | 核心考点 | 考察能力 |
|------|---------|---------|
| 自定义 Starter | 自动配置完整链路 | @AutoConfiguration + @Conditional + imports 注册 |
| 自定义条件注解 | SpringBootCondition | 条件判断机制、组合注解 |
| 自定义 HealthIndicator | 健康检查协议 | Health.up()/down()/outOfService() |
| 编写测试 | MockMvc + Testcontainers | 分层测试、真实环境验证 |

> 进入推荐资源篇：书籍、官方文档、视频、开源项目。