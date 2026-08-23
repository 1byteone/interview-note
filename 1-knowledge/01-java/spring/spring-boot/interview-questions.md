# Spring Boot 面试题大全

## 📚 知识体系

```
Spring Boot 核心
├── 自动配置原理
├── 启动流程
├── 内嵌容器
├── 外部化配置
├── Profiles 多环境
├── Actuator 监控
├── 日志系统
├── 异常处理
├── 测试框架
└── 部署运维

Spring Boot 集成
├── JPA / MyBatis
├── Redis
├── Elasticsearch
├── RocketMQ
├── Spring Security
├── Spring Cloud
└── Actuator + Prometheus
```

---

## 🎯 Level 1：基础题

### 1. Spring Boot 自动配置原理是什么？
**答案**：
Spring Boot 自动配置的核心是 `@EnableAutoConfiguration` 注解，它通过 `AutoConfigurationImportSelector` 加载 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 文件中定义的自动配置类。

**关键流程**：
```text
@SpringBootApplication
  ├── @EnableAutoConfiguration
  │     ↓
  │   AutoConfigurationImportSelector
  │     ↓
  │   加载 META-INF/spring/*.imports 文件
  │     ↓
  │   条件判断（@ConditionalOnClass / @ConditionalOnMissingBean 等）
  │     ↓
  │   符合条件的自动配置类生效
  │     ↓
  │   创建 Bean 注入容器
  └── @ComponentScan → 扫描当前包下的 Component
```

**常用条件注解**：
| 注解 | 说明 |
|------|------|
| `@ConditionalOnClass` | 类路径存在指定类时生效 |
| `@ConditionalOnMissingBean` | 容器中没有指定 Bean 时生效 |
| `@ConditionalOnProperty` | 配置中存在指定属性时生效 |
| `@ConditionalOnWebApplication` | Web 应用时生效 |

### 2. Spring Boot 启动流程是怎样的？
**答案**：

```text
① SpringApplication.run()
    ↓
② 判断应用类型（Web / Reactive / Non-Web）
    ↓
③ 加载初始化器（ApplicationContextInitializer）
    ↓
④ 加载监听器（ApplicationListener）
    ↓
⑤ 准备 Environment（配置属性）
    ↓
⑥ 打印 Banner
    ↓
⑦ 创建 ApplicationContext
    ├── Web → AnnotationConfigServletWebServerApplicationContext
    └── Reactive → AnnotationConfigReactiveWebServerApplicationContext
    ↓
⑧ 准备上下文（设置 Environment、执行初始化器）
    ↓
⑨ 刷新上下文（refresh）→ 核心
    ├── BeanFactory 后置处理
    ├── 注册 BeanPostProcessor
    ├── 初始化 MessageSource
    ├── 初始化事件广播器
    ├── 注册特殊 Bean
    ├── 实例化所有非懒加载单例 Bean
    └── 完成刷新（启动内嵌容器）
    ↓
⑩ 启动完成，返回 ApplicationContext
```

---

## 🎯 Level 2：进阶题

### 3. Spring Boot 如何实现多环境配置？
**答案**：

**文件命名规则**：
```text
application.yml              # 公共配置
application-dev.yml          # 开发环境
application-test.yml         # 测试环境
application-prod.yml         # 生产环境
```

**激活方式**：
```yaml
# application.yml
spring:
  profiles:
    active: dev
```

```bash
# 启动参数
java -jar app.jar --spring.profiles.active=prod

# 环境变量
export SPRING_PROFILES_ACTIVE=prod
```

### 4. Spring Boot 如何自定义 Starter？
**答案**：

**项目结构**：
```
my-spring-boot-starter/
├── pom.xml
├── src/main/java/...
│   └── MyAutoConfiguration.java
└── src/main/resources/
    └── META-INF/
        └── spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
```

**自动配置类**：
```java
@Configuration
@ConditionalOnClass(RedisClient.class)
@EnableConfigurationProperties(RedisProperties.class)
public class RedisAutoConfiguration {
    
    @Bean
    @ConditionalOnMissingBean
    public RedisTemplate redisTemplate(RedisProperties properties) {
        RedisTemplate template = new RedisTemplate();
        // 配置连接
        return template;
    }
}
```

**配置属性**：
```java
@ConfigurationProperties(prefix = "my.redis")
public class RedisProperties {
    private String host = "localhost";
    private int port = 6379;
    // getter / setter
}
```

---

## 🎯 Level 3：高级题

### 5. Spring Boot 如何做性能优化？
**答案**：

**启动优化**：
```yaml
spring:
  main:
    lazy-initialization: true        # 懒加载（减少启动时间）
  autoconfigure:
    exclude:                          # 排除不需要的自动配置
      - org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration
      - org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration
```

**JVM 参数优化**：
```bash
java -jar app.jar \
  -Xms512m -Xmx512m \
  -XX:+UseG1GC \
  -XX:MaxGCPauseMillis=200 \
  -XX:+HeapDumpOnOutOfMemoryError
```

**运行时优化**：
1. 连接池配置（HikariCP 参数调优）
2. Redis 缓存热点数据
3. 异步处理（@Async）
4. 消息队列削峰（RocketMQ）
5. CDN 加速静态资源

### 6. Spring Boot Actuator 如何集成监控？
**答案**：

**配置**：
```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      show-details: always
  metrics:
    tags:
      application: ${spring.application.name}
```

**集成 Prometheus + Grafana**：
```yaml
# 添加 micrometer-registry-prometheus 依赖
# 暴露 /actuator/prometheus 端点
```

**健康检查**：
```java
@Component
public class RedisHealthIndicator implements HealthIndicator {
    @Autowired
    private RedisTemplate redisTemplate;
    
    @Override
    public Health health() {
        try {
            redisTemplate.opsForValue().get("health");
            return Health.up().build();
        } catch (Exception e) {
            return Health.down(e).build();
        }
    }
}
```

---

## 📖 学习资源

### 推荐项目
- [Spring Boot 官方文档](https://spring.io/projects/spring-boot)
- [Spring Boot 示例](https://github.com/spring-projects/spring-boot/tree/main/spring-boot-samples)

### 最佳实践
1. 遵循分层架构（Controller/Service/Repository）
2. 使用 DTO 模式隔离 API 边界
3. 全局异常处理（@RestControllerAdvice）
4. 参数校验（@Validated）
5. 日志规范（SLF4J + Logback）