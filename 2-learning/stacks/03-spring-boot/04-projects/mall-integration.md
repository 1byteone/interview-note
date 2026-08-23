# AI 商城 — Spring Boot 微服务应用

> 等级：🎯 项目实战
> 主题：AI 智能商城（mall-micro-cloud）中 Spring Boot 的实际应用
> 路径：STAR 法则贯穿每个案例

---

## 〇、深度剖析参考

| 主题 | 本 learn 文档 | docs/tech-stack-analysis 深度剖析 |
|------|--------------|--------------------------------|
| 微服务架构总览 | 本文 mall-integration | [00-OVERVIEW.md](../../../../5-research/tech-stack-analysis/mall-micro-cloud/00-OVERVIEW.md) — 12 个微服务全景 |
| 公共模块/统一响应 | 本文 § 二 | [02-COMMON-ARCH.md](../../../../5-research/tech-stack-analysis/mall-micro-cloud/02-COMMON-ARCH.md) — Result/异常处理/Feign |
| Gateway 网关鉴权 | — | [01-NACOS-GATEWAY.md](../../../../5-research/tech-stack-analysis/mall-micro-cloud/01-NACOS-GATEWAY.md) — Nacos+Gateway+Sentinel |
| Seata 分布式事务 | — | [04-ORDER-SEATA.md](../../../../5-research/tech-stack-analysis/mall-micro-cloud/04-ORDER-SEATA.md) — @GlobalTransactional |
| Python FastAPI 对标 | [04-python](../../04-python/04-projects/mall-integration.md) | [02-API-GATEWAY.md](../../../../5-research/tech-stack-analysis/mall-ai-search/02-API-GATEWAY.md) — FastAPI 网关层 |

## 一、AI 商城微服务全景

AI 商城是一个基于 Spring Boot 3 + Spring Cloud Alibaba 的微服务架构系统，包含以下服务：

| 服务 | 模块 | 端口 | 启动类 | 核心 Starter |
|------|------|------|--------|-------------|
| 商品服务 | mall-product-service | 8081 | ProductServiceApplication | web, jpa, redis, seata |
| 订单服务 | mall-order-service | 8082 | OrderServiceApplication | web, mybatis, rocketmq, seata |
| 秒杀服务 | mall-seckill-service | 8083 | SeckillServiceApplication | web, redis, rocketmq |
| 用户服务 | mall-user-service | 8084 | UserServiceApplication | web, jpa, security, redis |
| 搜索服务 | mall-search-service | 8085 | SearchServiceApplication | web, elasticsearch, redis |
| 网关服务 | mall-gateway | 8080 | GatewayApplication | gateway, redis, sentinel |
| AI 服务 | mall-ai-service | 8086 | AiServiceApplication | web, redis, langchain |

所有服务都基于同一个 Spring Boot 父工程（统一版本管理）。

---

## 二、统一父工程

### 2.1 父 POM

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.3.2</version>
        <relativePath/>
    </parent>

    <groupId>com.mall</groupId>
    <artifactId>mall-micro-cloud</artifactId>
    <version>1.0.0</version>
    <packaging>pom</packaging>

    <modules>
        <module>mall-product-service</module>
        <module>mall-order-service</module>
        <module>mall-seckill-service</module>
        <module>mall-user-service</module>
        <module>mall-search-service</module>
        <module>mall-gateway</module>
        <module>mall-ai-service</module>
        <module>mall-common</module>
    </modules>

    <properties>
        <java.version>17</java.version>
        <spring-cloud.version>2023.0.3</spring-cloud.version>
        <spring-cloud-alibaba.version>2023.0.1.0</spring-cloud-alibaba.version>
        <mybatis-spring-boot.version>3.0.3</mybatis-spring-boot.version>
    </properties>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>org.springframework.cloud</groupId>
                <artifactId>spring-cloud-dependencies</artifactId>
                <version>${spring-cloud.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
            <dependency>
                <groupId>com.alibaba.cloud</groupId>
                <artifactId>spring-cloud-alibaba-dependencies</artifactId>
                <version>${spring-cloud-alibaba.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>
</project>
```

### 2.2 子服务 POM（订单服务为例）

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0">
    <parent>
        <groupId>com.mall</groupId>
        <artifactId>mall-micro-cloud</artifactId>
        <version>1.0.0</version>
    </parent>

    <artifactId>mall-order-service</artifactId>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>
        <dependency>
            <groupId>org.mybatis.spring.boot</groupId>
            <artifactId>mybatis-spring-boot-starter</artifactId>
            <version>${mybatis-spring-boot.version}</version>
        </dependency>
        <dependency>
            <groupId>com.alibaba.cloud</groupId>
            <artifactId>spring-cloud-starter-alibaba-seata</artifactId>
        </dependency>
        <dependency>
            <groupId>org.apache.rocketmq</groupId>
            <artifactId>rocketmq-spring-boot-starter</artifactId>
        </dependency>
        <dependency>
            <groupId>com.mall</groupId>
            <artifactId>mall-common</artifactId>
            <version>${project.version}</version>
        </dependency>
    </dependencies>
</project>
```

---

## 三、跨服务配置共享

### 3.1 统一配置中心

使用 Nacos 作为配置中心，所有微服务共享配置：

```yaml
# bootstrap.yml
spring:
  application:
    name: mall-order-service
  cloud:
    nacos:
      config:
        server-addr: 10.0.0.12:8848
        namespace: prod
        file-extension: yaml
        shared-configs:
          - data-id: common.yaml       # 所有服务共享（数据库、Redis 等）
          - data-id: seata.yaml        # 分布式事务配置
```

```yaml
# common.yaml (Nacos 共享配置)
spring:
  datasource:
    type: com.zaxxer.hikari.HikariDataSource
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      idle-timeout: 300000
  data:
    redis:
      host: redis-master.mall.svc
      port: 6379
      timeout: 3000ms
```

### 3.2 服务间调用的配置

```java
@Configuration
public class RestClientConfig {

    @Bean
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder()
            .requestInterceptor(new LoggingInterceptor());
    }
}
```

---

## 四、核心配置类与启动类

### 4.1 订单服务启动类

```java
package com.mall.order;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient          // 注册到 Nacos
@MapperScan("com.mall.order.mapper")
@EnableFeignClients             // 开启 Feign 远程调用（Spring Cloud）
public class OrderServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }
}
```

### 4.2 全局异常处理

```java
package com.mall.common.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(
            BusinessException e, HttpServletRequest request) {
        log.warn("业务异常: {} - {}", e.getCode(), e.getMessage());
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(new ErrorResponse(e.getCode(), e.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception e, HttpServletRequest request) {
        log.error("系统异常: ", e);
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(new ErrorResponse("SYS-500", "系统繁忙，请稍后重试", request.getRequestURI()));
    }
}
```

### 4.3 跨服务 Feign 调用

```java
package com.mall.order.client;

import com.mall.order.dto.ProductDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "mall-product-service", path = "/api/products")
public interface ProductClient {

    @GetMapping("/{id}")
    ProductDTO getProduct(@PathVariable("id") Long id);
}

// 使用
@Service
public class OrderService {

    private final ProductClient productClient;

    public OrderService(ProductClient productClient) {
        this.productClient = productClient;
    }

    public Order createOrder(Long userId, Long productId, int quantity) {
        // 通过 Feign 调用商品服务
        ProductDTO product = productClient.getProduct(productId);
        // 创建订单逻辑...
    }
}
```

---

## 五、健康检查与可观测性

### 5.1 统一健康检查端点

```yaml
# 所有服务共享的 Actuator 配置
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  metrics:
    tags:
      application: ${spring.application.name}
  endpoint:
    health:
      probes:
        enabled: true   # K8s 探针
```

### 5.2 自定义健康检查

```java
// 所有服务共享的 Redis 健康检查
@Component
public class RedisHealthIndicator implements HealthIndicator {

    private final StringRedisTemplate redisTemplate;

    public RedisHealthIndicator(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Health health() {
        try {
            String pong = redisTemplate.getConnectionFactory()
                .getConnection().ping();
            if ("PONG".equals(pong)) {
                return Health.up().build();
            }
            return Health.down().withDetail("ping", pong).build();
        } catch (Exception e) {
            return Health.down(e).build();
        }
    }
}
```

---

## 六、配置加密与安全

```yaml
# 敏感信息使用环境变量
spring:
  datasource:
    password: ${DB_PASSWORD}
  data:
    redis:
      password: ${REDIS_PASSWORD}

# 或使用 Jasypt 加密
jasypt:
  encryptor:
    password: ${JASYPT_KEY}
    algorithm: PBEWithMD5AndDES
```

---

## 七、生产环境配置最佳实践

### 7.1 日志配置

```yaml
logging:
  level:
    com.mall: INFO
    org.springframework.web: WARN
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} [%X{traceId:-}] - %msg%n"
  file:
    path: /var/log/mall
    max-size: 100MB
    max-history: 7
```

### 7.2 线程池配置

```java
@Configuration
public class ThreadPoolConfig {

    @Bean("orderExecutor")
    public ThreadPoolExecutor orderExecutor() {
        return new ThreadPoolExecutor(
            10, 20, 60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(500),
            new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }
}
```

### 7.3 优雅停机

```yaml
server:
  shutdown: graceful
spring:
  lifecycle:
    timeout-per-shutdown-phase: 30s
```

---

## 八、面试要点

| 问题 | 一句话答案 |
|------|-----------|
| AI 商城为什么用 Spring Boot 统一框架？ | 统一版本管理、自动配置开箱即用、Actuator 监控、生态整合 |
| 跨服务配置怎么共享？ | Nacos 配置中心 + shared-configs 共享通用配置 |
| 服务间调用方式？ | Feign 声明式 HTTP 调用 + Nacos 服务发现 |
| 全局异常处理怎么实现？ | @RestControllerAdvice + @ExceptionHandler 统一处理各层异常 |
| 优雅停机配置要点？ | server.shutdown=graceful + 超时时间，确保请求处理完再退出 |

> 进入独立小项目：用 Spring Boot 实现一个完整的博客 API。