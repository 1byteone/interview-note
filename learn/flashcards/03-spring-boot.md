# Spring Boot — 面试抽认卡

> 来源：`learn/03-spring-boot/05-interview/`

---

### Card 1: 自动配置原理
**维度**: 🔬深挖 | **难度**: ⭐⭐⭐

> **Q: Spring Boot 自动配置的完整加载链路是怎样的？**

**A:** `@SpringBootApplication` → `@EnableAutoConfiguration` → `AutoConfigurationImportSelector` → 读取 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 文件（每行一个自动配置类）→ `DeferredImportSelector` 延迟导入（等用户配置类处理完）→ 条件注解过滤（如 `@ConditionalOnClass`）→ 注册符合条件的 Bean。`@ConditionalOnMissingBean` 保证用户自定义 Bean 优先。

---

### Card 2: @SpringBootApplication 组合注解
**维度**: 📝速记 | **难度**: ⭐

> **Q: @SpringBootApplication 由哪些注解组成？各有什么作用？**

**A:** 三个注解组合：`@SpringBootConfiguration`（标记为配置类，继承 `@Configuration`）、`@EnableAutoConfiguration`（开启自动配置）、`@ComponentScan`（默认扫描启动类所在包及其子包）。启动类推荐放在根包下，避免手动指定 `scanBasePackages`。

---

### Card 3: 条件注解体系
**维度**: 📝速记 | **难度**: ⭐⭐

> **Q: Spring Boot 常用的条件注解有哪些？分别在什么场景使用？**

**A:** `@ConditionalOnClass`（classpath 存在指定类，判断是否引入 Starter）、`@ConditionalOnMissingBean`（容器无指定 Bean，用户可覆盖默认配置）、`@ConditionalOnProperty`（存在配置属性，用于特性开关）、`@ConditionalOnWebApplication`（Web 应用环境，区分 MVC 和 WebFlux）、`@ConditionalOnExpression`（SpEL 表达式，复杂条件组合）。条件注解在容器 refresh 阶段执行，非运行时。

---

### Card 4: Starter 原理
**维度**: 🔬深挖 | **难度**: ⭐⭐

> **Q: 如何自定义一个 Spring Boot Starter？自动配置类如何被加载？**

**A:** ① 建 `xxx-spring-boot-starter` 模块，添加 `AutoConfiguration` 类；② 在 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 注册自动配置类；③ 使用 `@ConditionalOnClass` 等条件注解控制生效条件；④ 通过 `@EnableConfigurationProperties` 绑定配置属性（`@ConfigurationProperties` 前缀）。核心：Starter 本质是自动配置 + 依赖管理的封装。

---

### Card 5: Spring Boot 启动流程
**维度**: 🔬深挖 | **难度**: ⭐⭐⭐

> **Q: Spring Boot 启动的核心流程（10 步）是什么？**

**A:** ① 推断应用类型（WebFlux/Servlet/None）；② 加载 `ApplicationContextInitializer` 和 `ApplicationListener`（从 `spring.factories`）；③ 准备 `Environment`（解析配置文件）；④ 创建 `ApplicationContext`；⑤ 调用 `Initializer`；⑥ 加载 Bean 定义；⑦ `refresh()` 容器（获取 BeanFactory → 执行 BeanFactoryPostProcessor → 注册 BeanPostProcessor → `onRefresh` 创建 WebServer → 实例化单例 Bean → `finishRefresh`）；⑧ 启动内嵌 Web 容器；⑨ 运行 `CommandLineRunner` / `ApplicationRunner`；⑩ 发布 `ApplicationReadyEvent`。

---

### Card 6: 内嵌 Tomcat 原理
**维度**: 🔬深挖 | **难度**: ⭐⭐

> **Q: Spring Boot 如何启动内嵌 Tomcat？如何切换为 Undertow？**

**A:** `refresh()` 的 `onRefresh()` 阶段调用 `ServletWebServerFactory` 创建 `WebServer`。`TomcatServletWebServerFactory` 创建 Tomcat 实例，配置端口/连接器/线程池，`getTomcatWebServer()` 启动。切换为 Undertow：排除 `spring-boot-starter-tomcat`，引入 `spring-boot-starter-undertow`。定制容器实现 `WebServerFactoryCustomizer` 接口，修改配置（如连接器、线程池参数）。

---

### Card 7: 循环依赖解决方案
**维度**: 🔬深挖 | **难度**: ⭐⭐⭐

> **Q: Spring 如何解决循环依赖？为什么构造器注入无法解决？**

**A:** 三级缓存解决：一级 `singletonObjects`（已完成）、二级 `earlySingletonObjects`（半成品）、三级 `singletonFactories`（工厂方法）。Bean 创建时提前暴露工厂，A 依赖 B 时从三级缓存取工厂创建 A 的代理对象，注入 B 完成后再创建 B，B 注入 A 的早期引用。构造器注入不行：因为构造器执行时 Bean 尚未实例化，无法放入三级缓存。推荐用 `@Lazy` 或重构拆分。

---

### Card 8: Bean 生命周期
**维度**: 🔬深挖 | **难度**: ⭐⭐⭐

> **Q: 描述 Spring Bean 的完整生命周期（从加载到销毁）。**

**A:** ① 实例化（构造器/工厂方法）；② 属性赋值（populateBean）；③ Aware 接口回调（BeanNameAware、BeanFactoryAware、ApplicationContextAware）；④ BeanPostProcessor#postProcessBeforeInitialization；⑤ InitializingBean#afterPropertiesSet；⑥ 自定义 init-method（@PostConstruct）；⑦ BeanPostProcessor#postProcessAfterInitialization（AOP 在此创建代理）；⑧ Bean 就绪，可用了；⑨ 容器关闭时：@PreDestroy → DisposableBean#destroy → 自定义 destroy-method。

---

### Card 9: AOP 原理
**维度**: 🔬深挖 | **难度**: ⭐⭐⭐

> **Q: Spring AOP 的实现原理是什么？JDK 动态代理和 CGLIB 有什么区别？**

**A:** Spring AOP 基于动态代理，在 BeanPostProcessor#postProcessAfterInitialization 阶段创建代理对象。JDK 动态代理：目标类必须实现接口，利用 `InvocationHandler` + `Proxy.newProxyInstance` 生成代理类。CGLIB：通过 ASM 生成目标类的子类，重写非 final 方法。Spring Boot 2.x+ 默认 CGLIB（`spring.aop.proxy-target-class=true`）。JDK 代理只能代理接口方法，CGLIB 可代理类方法（但 final 方法不行）。

---

### Card 10: 事务传播行为
**维度**: 📝速记 | **难度**: ⭐⭐

> **Q: Spring 事务的 7 种传播行为分别是什么？REQUIRED 和 REQUIRES_NEW 的区别？**

**A:** REQUIRED（默认，有事务则加入，无则新建）、REQUIRES_NEW（挂起当前事务，新建独立事务，内外事务互不影响）、NESTED（嵌套事务，利用 Savepoint，内层回滚不影响外层）、SUPPORTS（有事务则加入，无则非事务执行）、NOT_SUPPORTED（非事务执行，挂起当前事务）、MANDATORY（必须在事务中，否则抛异常）、NEVER（不能在事务中，否则抛异常）。REQUIRES_NEW 完全独立，NESTED 可部分回滚。

---

### Card 11: @Async 异步执行
**维度**: 💻代码 | **难度**: ⭐⭐

> **Q: @Async 的工作原理是什么？使用时需要注意什么？**

**A:** 原理：`@EnableAsync` 开启，`AsyncAnnotationBeanPostProcessor` 为 `@Async` 方法创建代理，将方法提交到 `TaskExecutor` 线程池执行。注意：① 自调用不生效（不走代理）；② 默认使用 SimpleAsyncTaskExecutor（每次新建线程，生产环境需自定义线程池）；③ 返回 `void` 或 `Future`（`CompletableFuture` 推荐）；④ 事务和 `@Async` 一起使用时，事务上下文不传播到异步线程。

---

### Card 12: Actuator 端点
**维度**: 📝速记 | **难度**: ⭐⭐

> **Q: Spring Boot Actuator 的核心端点有哪些？如何与 K8s 探针集成？**

**A:** 核心端点：`/health`（健康检查）、`/metrics`（指标，Micrometer 门面）、`/info`（应用信息）、`/env`（环境属性）、`/loggers`（动态改日志级别）。K8s 探针集成：`/actuator/health/liveness`（存活探针，容器是否活着）和 `/actuator/health/readiness`（就绪探针，能否接收流量）。安全建议：用 `management.server.port` 独立端口 + Spring Security 保护。

---

### Card 13: 配置加载顺序
**维度**: 📝速记 | **难度**: ⭐⭐

> **Q: Spring Boot 配置文件的加载顺序是什么？哪个优先级最高？**

**A:** 优先级从高到低：① 命令行参数（`--server.port=8081`）；② JNDI 属性；③ 系统属性（System.getProperties）；④ 操作系统环境变量；⑤ `application-{profile}.properties`（profile 特定）；⑥ `application.properties`（默认）；⑦ `@PropertySource` 注解。最后一个加载的覆盖前面。重要：命令行参数 > 环境变量 > 配置文件，Always 用最高优先级覆盖测试。

---

### Card 14: 多环境配置
**维度**: 📝速记 | **难度**: ⭐

> **Q: Spring Boot 如何管理多环境配置？Profile 如何切换？**

**A:** 文件命名 `application-{profile}.yml`（如 `application-dev.yml`、`application-prod.yml`）。激活方式：① `application.yml` 中 `spring.profiles.active=dev`；② 命令行 `--spring.profiles.active=prod`；③ 环境变量 `SPRING_PROFILES_ACTIVE=prod`。支持多文档块（`---` 分隔 YAML）和 `@Profile` 条件注解控制 Bean 在指定 Profile 生效。推荐用 `-Dspring.profiles.active` 部署时指定。

---

### Card 15: GraalVM 原生编译
**维度**: 🔬深挖 | **难度**: ⭐⭐⭐

> **Q: Spring Boot 3 + GraalVM 原生镜像解决了什么问题？有什么限制？**

**A:** 原生镜像通过 AOT（Ahead-of-Time）编译将应用编译为本地可执行文件，启动时间从秒级降到毫秒级（50-100ms），内存占用降低 50%+。限制：① 反射/动态代理需要提前配置（`hints` 文件）；② 不支持条件注解动态决策（AOT 编译时已确定）；③ 部分第三方库不兼容；④ 调试困难（无 JIT 编译日志）。适合 Serverless/FaaS/边缘计算场景。

---

### Card 16: Spring Boot 3 迁移要点
**维度**: 📝速记 | **难度**: ⭐⭐

> **Q: 从 Spring Boot 2.x 迁移到 3.x 需要关注哪些变更？**

**A:** ① Java 17 基线，需升级 JDK；② `javax.*` 迁移到 `jakarta.*`（Servlet/JPA 包名变更）；③ Spring Security 配置方法链式 API 变更；④ 移除 `spring.factories` 自动配置（改用 `AutoConfiguration.imports`）；⑤ RestTemplate 弃用，推荐 WebClient；⑥ `HttpStatus` 枚举变接口；⑦ 支持虚拟线程（`spring.threads.virtual.enabled=true`）。

---

### Card 17: MockMvc 测试
**维度**: 💻代码 | **难度**: ⭐⭐

> **Q: @WebMvcTest 和 @SpringBootTest 在测试 Controller 时有什么区别？**

**A:** `@WebMvcTest` 只加载 Controller 层、Filter 和 AOP 相关组件，Service 和 Repository 需要 `@MockBean` 模拟，速度快且隔离好。`@SpringBootTest` 启动完整上下文，集成测试但慢。MockMvc 用法：`mockMvc.perform(get("/api/users/1")).andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Alice"))`。推荐：`@WebMvcTest` 测 Controller 逻辑，`@SpringBootTest` 测端到端流程。

---

### Card 18: Testcontainers 集成测试
**维度**: 💻代码 | **难度**: ⭐⭐

> **Q: Testcontainers 相比 H2 内存数据库做测试有什么优势？**

**A:** Testcontainers 使用真实中间件容器（MySQL/Redis/ES）运行测试，避免 H2 模拟的方言差异和功能缺失。优势：① 真实 SQL 语法和特性（H2 不支持 MySQL 全部语法）；② 可测试 Redis/ES/RocketMQ（H2 只能测 DB）；③ 测试结果更可靠（与生产环境一致）。劣势：需要 Docker 环境，启动稍慢。使用 `@Testcontainers` + `@Container` 注解，`@DynamicPropertySource` 注入动态端口。