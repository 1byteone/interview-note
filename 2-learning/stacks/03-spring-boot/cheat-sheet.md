# Spring Boot 速查卡 📋

> 面试前30分钟扫一遍，一页纸覆盖核心考点

## 🏷️ 核心概念速记

| 概念 | 一句话解释 | 常见陷阱 |
|------|-----------|----------|
| @SpringBootApplication | @Configuration + @EnableAutoConfiguration + @ComponentScan 组合注解 | 小人启动类要放在包根路径，否则扫描不到 Bean |
| 自动配置 | META-INF/spring.factories 或 AutoConfiguration.imports 找配置类 | 条件是 @ConditionalOnXxx 满足才生效，不是无条件执行 |
| @EnableAutoConfiguration | 开启自动配置的开关 | 配合 @Import(AutoConfigurationImportSelector) 加载 |
| 条件注解 | @ConditionalOnClass/OnBean/OnProperty/OnMissingBean | OnMissingBean 判断"当前容器没有才创建"用于覆盖默认配置 |
| Starter | 依赖 + 自动配置的组合，简化引入 | 自定义 starter 要保证自动配置类被扫描到 |
| 内嵌容器 | Tomcat(默认)、Jetty、Undertow | 内嵌 Tomcat 无独立 server.xml，用配置项替代 |
| Actuator | 生产监控端点：health/metrics/info/env | /actuator/shutdown 等敏感端点线上必须关闭或鉴权 |
| ConfigurationProperties | 类型安全的配置绑定 | 用 @ConfigurationProperties 代替散落的 @Value |
| @Autowired vs 构造注入 | 推荐构造器注入，可测试 & 不可变 | @Autowired 字段注入被 IDEA 警告，循环依赖也靠它掩盖 |
| 循环依赖 | A 依赖 B、B 依赖 A | Spring 只解决单例 setter/字段注入的循环依赖(三缓存)，构造器循环依赖直接报错 |
| Bean 生命周期 | 实例化→属性填充→Aware→BeanPostProcessor→init→使用→destroy | BeanPostProcessor 是 AOP 织入点，顺序敏感 |
| ApplicationContext | BeanFactory + 高级特性(事件/资源/国际化) | 容器刷新 refresh() 是启动核心方法 |
| 三级缓存 | 单例池(singletonObjects)、早期引用(earlySingletonObjects)、工厂(singletonFactories) | 解决循环依赖：先暴露工厂→代理时判断是否需提前包装 |

## 🔧 常用命令/API

```java
// 自动配置类模板：声明式注册自定义 Bean
@Configuration
@ConditionalOnClass({StringRedisTemplate.class})       // classpath 有才生效
@ConditionalOnProperty(prefix = "demo", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(DemoProperties.class)   // 绑定自定义配置
public class DemoAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean                       // 用户自定义过就不覆盖
    public DemoService demoService(DemoProperties props) {
        return new DemoService(props);
    }
}
```

```properties
# 注册文件：resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
com.demo.config.DemoAutoConfiguration
```

```xml
<!-- 自定义 Starter 标准结构 -->
<artifactId>demo-spring-boot-starter</artifactId>
<!--
  1. pom 引入 autoconfigure 模块
  2. 自动配置类上有 @AutoConfiguration
  3. spring.factories 或 AutoConfiguration.imports 注册
  4. properties 配 prefix 绑定（如 demo.enabled=true）
-->
```

```java
// 启动流程关键步骤（背下顺序）
// 1. 识别 @SpringBootApplication，确定主类
// 2. 创建 SpringApplication，准备 Environment
// 3. 创建 ApplicationContext（Servlet → AnnotationConfigServletWebServerApplicationContext）
// 4. 执行 BeanDefinition 扫描注册（含自动配置类）
// 5. refresh(): 实例化单例 → 注册内嵌 WebServer → 启动 Tomcat
// 6. 发布 ApplicationReadyEvent，执行 CommandLineRunner
```

```yaml
spring:
  application:
    name: demo-app
  datasource:
    url: jdbc:mysql://localhost:3306/demo
    hikari:
      maximum-pool-size: 20      # HikariCP 连接池
management:
  endpoints:
    web:
      exposure:
        include: health,metrics,info  # Actuator 暴露范围（别开 * / shutdown）
```

## 🎯 面试高频 TOP10

1. **Q: Spring Boot 自动配置原理？** **A:** 启动类 @EnableAutoConfiguration → 导入 AutoConfigurationImportSelector → 加载 `META-INF/spring/...AutoConfiguration.imports` → 按 @ConditionalOnXxx 条件创建 Bean，用户可用 @Bean 覆盖。
2. **Q: Spring Boot 启动流程？** **A:** new SpringApplication 初始化(推断主类、加载 listener) → run(): 准备 Environment → refresh 容器 → 注册内嵌服务器并启动 → 发布事件回调 Runner。
3. **Q: Spring 如何解决循环依赖？** **A:** 三级缓存：一级存成品、二级存早期暴露对象、三级存 ObjectFactory；A 创建时提前暴露工厂，B 拿到未完成的 A 的代理引用，A 完成后容器里其实还是那一个代理，只能解单例 setter/字段注入的环，构造器循环依赖不可解。
4. **Q: Spring 事务失效的场景？** **A:** 自调用(同类方法直接调不走代理)、非 public、异常被 catch、抛出非 Runtime 异常、类没被 Spring 管理、@Transactional 标在接口方法、传播/回滚传播配置错误。
5. **Q: 内嵌 Tomcat 和外部 Tomcat 区别？** **A:** 内嵌是依赖引入、随应用启动/销毁、配置项化、易容器化部署；外部由运维管理、支持热部署替换、JNDI 等容器特性。
6. **Q: @ConfigurationProperties vs @Value？** **A:** 前者类型安全、支持校验(如 @Validated + @Email)、可自动提示、批量绑定；后者单个注入、不支持提示校验，复杂结构不友好。
7. **Q: Spring 的 Bean 作用域有哪些？** **A:** singleton(默认)、prototype(每次新)、request/session/application(Web 作用域)；注意 prototype 不受懒加载管理(容器不管理其销毁)。
8. **Q: Spring AOP 原理？** **A:** JDK 动态代理(接口)或 CGLIB(子类) 生成代理对象，切点匹配+通知织入；Spring 默认 CGLIB 代理(SpringBoot2+)，@EnableAspectJAutoProxy 开启。
9. **Q: Spring 事件机制(ApplicationEvent)？** **A:** 发布 ApplicationEvent → 由 ApplicationEventMulticaster 分发给 listener；@EventListener 异步需 @Async + @EnableAsync，事务事件用 @TransactionalEventListener。
10. **Q: Boot 3.x 与 Boot 2.x 差异？** **A:** JDK17 基线、Jakarta EE9 命名空间(javax→jakarta)、Spring Framework 6、可观测框架、AOT+GraalVM 原生镜像支持。

## ⚠️ 常见坑 & 最佳实践

| ❌ 坑 | ✅ 正确做法 |
|-------|------------|
| 生产环境 Actuator 暴露所有端点 | 只暴露 health/metrics/info，shutdown 永不开，网关层鉴权 |
| 用 @Autowired 字段注入满天飞 | 构造器注入 + final，显式依赖 |
| 事务内做 RPC/走网络 | 事务只包 DB；本地消息表+MQ 异步化 |
| 盲目开 @EnableAsync | 配线程池 executor，拒绝策略合理，避免无界队列 |
| 配置写死/硬编码在代码里 | @ConfigurationProperties + profile(`application-dev.yml`) |
| 启动类放子包导致扫描不到 | 启动类放根包；需扩展时用 @ComponentScan 显式指定 |
| 没配线程池就调用 @Async | 默认 SimpleAsyncTaskExecutor 每任务新线程，必配自定义池 |
| 数据库连接池默认值盲信 | 按压测结论调 HikariCP: maxPoolSize、connectionTimeout |
| 信任默认 security 配置 | 显式 SecurityFilterChain + 密码编码器(BCrypt) |

## 📐 架构设计要点

- **模块划分**：starter(依赖/配置) 与 autoconfigure(自动装配) 分离，业务模块按垂直切片组织。
- **配置管理**：三层配置 profile → Nacos/ConfigServer 中心化 → 本地默认值，支持动态刷新。
- **可观测性三件套**：Actuator 指标 + Micrometer 打点 + OpenTelemetry 链路追踪(traceId)。
- **健壮性**：全局异常、参数校验、幂等、限流降级、优雅停机(graceful-shutdown)、《spring-ai → 多环境隔离》。
- **性能**：懒加载视场景、连接/线程池显式配置、SQL 防止 N+1、响应压缩、静态资源缓存头。

## 🔗 关联技术

- **Spring Framework**：Boot 是 Framework 的自动配置封装，IOC/AOP 是其内核。
- **Spring Cloud**：基于 Boot 的微服务生态(Gateway/Nacos/Sentinel)。
- **MySQL/Redis**：数据源自动配置(Hikari/Jedis-Lettuce)。
- **Docker**：Boot 应用做镜像多阶段构建，GraalVM 原生映像减小体量、加快启动。
- **JVM**：容器内 JVM 内存判断依赖 -XX:UseContainerSupport 与 cgroup 限制。