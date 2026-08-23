# Spring 生态面试题

## 📚 知识点概览

Spring 生态是 Java 后端开发的核心框架，包括 Spring Boot、Spring MVC、Spring Data 等。

## 🎯 面试题分类

### Level 1: 基础题

#### Spring Boot 基础
1. **Spring Boot 核心特性**
   - 问题：Spring Boot 的核心特性有哪些？它解决了什么问题？
   - 答案：Spring Boot 核心特性包括自动配置、起步依赖、内嵌服务器、Actuator 监控。它解决了传统 Spring 应用配置繁琐、依赖管理复杂、部署不便的问题。
   - 解析：自动配置通过 @EnableAutoConfiguration 按条件自动装配 Bean；起步依赖通过预定义依赖组合简化 Maven 配置；内嵌 Tomcat/Jetty 使应用可独立运行；Actuator 提供健康检查、指标收集等生产级监控能力。这些特性显著降低了 Spring 应用的入门门槛和运维成本。

2. **自动配置原理**
   - 问题：Spring Boot 的自动配置原理是什么？
   - 答案：自动配置基于 @EnableAutoConfiguration 注解，通过 SpringFactoriesLoader 加载 META-INF/spring.factories 中的配置类，结合 @Conditional 条件注解按需装配 Bean。
   - 解析：启动时 SpringApplication.run() 会触发 @EnableAutoConfiguration，它通过 spring-boot-autoconfigure 模块中的 AutoConfigurationImportSelector 读取所有候选配置类。每个配置类使用 @ConditionalOnClass、@ConditionalOnMissingBean 等条件判断是否生效，实现"约定优于配置"。开发者可通过 application.properties 覆盖默认配置或排除特定自动配置。

3. **启动流程**
   - 问题：Spring Boot 应用的启动流程是怎样的？
   - 答案：启动流程为 SpringApplication.run() → 推断应用类型 → 加载 spring.factories 中的初始化器 → 触发 ApplicationRunner → 创建 ApplicationContext → 执行自动配置 → 刷新容器启动内嵌服务器。
   - 解析：核心流程分三个阶段：(1) 准备阶段：通过 SpringFactoriesLoader 加载 ApplicationContextInitializer 和 ApplicationListener；(2) 刷新阶段：调用 AbstractApplicationContext.refresh() 完成 Bean 定义加载、实例化、依赖注入；(3) 启动阶段：根据应用类型启动内嵌 Servlet 容器，触发 CommandLineRunner 和 ApplicationRunner。整个过程遵循 Spring 容器生命周期管理。

#### Spring MVC 基础
4. **MVC 架构**
   - 问题：什么是 MVC 架构？Spring MVC 的工作流程是怎样的？
   - 答案：MVC 是 Model-View-Controller 三层架构模式。Spring MVC 工作流程：请求 → DispatcherServlet → HandlerMapping → HandlerAdapter → Controller → 返回 Model → ViewResolver → 渲染响应。
   - 解析：DispatcherServlet 作为前端控制器统一接收请求；HandlerMapping 根据 URL 找到对应的 Handler（Controller 方法）；HandlerAdapter 负责调用具体 Controller；Controller 处理业务逻辑并返回 ModelAndView；ViewResolver 解析视图名称并渲染最终响应。在前后端分离架构中，Controller 通过 @ResponseBody 直接返回 JSON，跳过视图解析步骤。

5. **常用注解**
   - 问题：Spring MVC 中常用的注解有哪些？它们的作用是什么？
   - 答案：核心注解包括 @Controller/@RestController、@RequestMapping、@RequestBody、@ResponseBody、@PathVariable、@RequestParam。分别用于声明控制器、映射请求、接收请求体、返回响应、路径变量绑定、查询参数绑定。
   - 解析：@RestController 是 @Controller + @ResponseBody 组合，直接返回 JSON。@RequestMapping 支持指定 HTTP Method、Consumes/Produces 媒体类型。@RequestBody 通过 HttpMessageConverter 将 JSON 反序列化为对象。@PathVariable 从 URI 模板提取参数，@RequestParam 绑定查询字符串或表单参数。此外，@Valid 用于参数校验，@CrossOrigin 处理跨域请求。

### Level 2: 进阶题

#### Spring Boot 进阶
6. **Starter 原理**
   - 问题：Spring Boot Starter 是什么？如何自定义一个 Starter？
   - 答案：Starter 是一组预定义的依赖和自动配置的集合，引入后自动完成相关组件配置。自定义 Starter 需创建配置类、属性类，并在 META-INF/spring.factories 中注册。
   - 解析：Starter 本质是 Maven 依赖聚合 + 自动配置。例如引入 spring-boot-starter-data-redis 会自动配置 RedisTemplate。自定义 Starter 步骤：(1) 创建 xxx-spring-boot-autoconfigure 模块编写配置类；(2) 使用 @ConfigurationProperties 绑定配置前缀；(3) 添加 @Conditional 条件注解；(4) 在 META-INF/spring.factories 或 META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports（Spring Boot 3.x）中注册。命名规范：官方 starter 为 spring-boot-starter-xxx，第三方为 xxx-spring-boot-starter。

7. **配置文件加载**
   - 问题：Spring Boot 配置文件的加载顺序是怎样的？
   - 答案：加载顺序从高到低：命令行参数 → JNDI 属性 → 系统属性 → 系统环境变量 → application-{profile}.yml → application.yml → @PropertySource → 默认属性。
   - 解析：Spring Boot 按优先级加载配置，高优先级覆盖低优先级。核心加载源包括：(1) 命令行参数 --server.port=8081；(2) JNDI 和系统属性；(3) 环境变量 SPRING_APPLICATION_JSON；(4) profile 特定配置文件 application-dev.yml；(5) 主配置文件 application.yml。此外，外部配置文件优先于 jar 包内配置，这使得运维人员无需重新打包即可修改配置。可使用 spring.config.additional-location 指定额外配置路径。

8. **多环境配置**
   - 问题：如何在 Spring Boot 中实现多环境配置？
   - 答案：通过 spring.profiles.active 激活指定环境，结合 application-{profile}.yml 文件实现。可通过命令行、配置文件、环境变量等方式指定激活的 profile。
   - 解析：创建 application-dev.yml、application-test.yml、application-prod.yml 分别定义不同环境的配置。在主配置文件中设置 spring.profiles.active: dev 指定默认环境。激活方式包括：(1) 命令行 --spring.profiles.active=prod；(2) 环境变量 SPRING_PROFILES_ACTIVE；(3) JVM 参数 -Dspring.profiles.active=dev。Spring Boot 2.4+ 引入 spring.config.activate.on-profile 支持更灵活的条件激活，可结合 group 机制分组管理配置。

#### Spring MVC 进阶
9. **拦截器**
   - 问题：Spring MVC 拦截器是什么？如何实现？
   - 答案：拦截器是基于 AOP 的请求拦截机制，实现 HandlerInterceptor 接口，通过 preHandle/postHandle/afterCompletion 三个方法在请求处理前后进行拦截处理。
   - 解析：Interceptor 类似 Servlet Filter，但工作在 DispatcherServlet 层面，可获取 Handler 信息。三个核心方法：(1) preHandle 在 Controller 执行前调用，返回 false 终止请求；(2) postHandle 在 Controller 执行后、视图渲染前调用；(3) afterCompletion 在整个请求完成后调用，常用于资源清理。通过 WebMvcConfigurer.addInterceptors() 注册拦截器并指定拦截路径。典型应用：登录认证、权限校验、请求日志、接口耗时统计。

10. **异常处理**
    - 问题：Spring MVC 中如何进行全局异常处理？
    - 答案：使用 @RestControllerAdvice + @ExceptionHandler 注解实现全局异常处理。定义异常处理类，针对不同异常类型返回统一的错误响应格式。
    - 解析：@RestControllerAdvice 是 @ControllerAdvice + @ResponseBody 组合，拦截所有 Controller 抛出的异常。通过 @ExceptionHandler(XxxException.class) 指定处理的异常类型，返回自定义错误信息。最佳实践：(1) 定义统一响应对象包含 code、message、data；(2) 针对不同异常类型（参数校验、业务逻辑、系统异常）返回不同状态码；(3) 使用 @ResponseStatus 指定 HTTP 状态码；(4) 记录异常日志便于排查。也可结合 Spring 的 ResponseStatusException 抛出 HTTP 异常。

11. **数据验证**
    - 问题：如何在 Spring MVC 中实现请求参数验证？
    - 答案：使用 Bean Validation（JSR 380）规范，在实体类上添加 @NotNull、@Size、@Pattern 等校验注解，Controller 方法参数添加 @Valid/@Validated 触发校验。
    - 解析：Spring Boot 默认集成 Hibernate Validator。使用步骤：(1) 实体字段添加约束注解如 @NotBlank(message="用户名不能为空")；(2) Controller 方法参数前加 @Valid 触发校验；(3) 校验失败会抛出 MethodArgumentNotValidException 或 BindException；(4) 在全局异常处理器中捕获并返回错误信息。分组校验通过 @Validated(UserDTO.Create.class) 支持不同场景校验不同字段。嵌套校验通过在对象字段上加 @Valid 实现级联校验。

#### Spring Data
12. **JPA 与 MyBatis**
    - 问题：Spring Data JPA 和 MyBatis 的区别是什么？如何选择？
    - 答案：JPA 是 ORM 框架，以实体为中心自动管理 SQL；MyBatis 是半自动 ORM，需手写 SQL。JPA 适合简单 CRUD，MyBatis 适合复杂查询和性能敏感场景。
    - 解析：JPA 基于 Hibernate，通过 Entity 映射自动生成 SQL，支持 JPQL、Criteria API，提供事务管理和一级/二级缓存。优点是开发效率高、对象关系映射清晰，缺点是复杂 SQL 控制力弱。MyBatis 通过 XML Mapper 或注解直接编写原生 SQL，对 SQL 有完全控制权，学习成本低。选型建议：(1) 快速原型/简单业务用 JPA；(2) 复杂统计报表、已有 SQL 资产、需要 SQL 调优用 MyBatis；(3) 可混合使用，JPA 处理简单操作，MyBatis 处理复杂查询。

13. **Redis 集成**
    - 问题：如何在 Spring Boot 中集成 Redis？常用的 API 有哪些？
    - 答案：引入 spring-boot-starter-data-redis 依赖，配置 spring.data.redis.host/port/password，注入 RedisTemplate 或 StringRedisTemplate 操作 Redis。
    - 解析：Spring Boot 自动配置 RedisConnectionFactory（Lettuce/Jedis）。RedisTemplate<K,V> 提供序列化操作，StringRedisTemplate 是其 String 序列化子类。常用 API：(1) opsForValue().set/get 操作 String；(2) opsForHash().put/getAll 操作 Hash；(3) opsForList().leftPush/rightPop 操作 List；(4) opsForSet().add/members 操作 Set；(5) opsForZSet().add/range 操作 ZSet。推荐使用 Redisson 客户端提供分布式锁、布隆过滤器、MapReduce 等高级功能。序列化建议自定义 RedisSerializer 配置 JSON 序列化。

### Level 3: 高级题

#### Spring Boot 高级
14. **性能优化**
    - 问题：如何优化 Spring Boot 应用的启动速度和运行性能？
    - 答案：启动优化包括延迟初始化、减少组件扫描、使用 GraalVM 原生镜像；运行优化包括连接池调优、缓存策略、异步处理、JVM 参数优化。
    - 解析：启动优化：(1) spring.main.lazy-initialization=true 开启延迟初始化；(2) 明确指定 @ComponentScan 包路径减少扫描范围；(3) Spring Boot 3.x 支持 GraalVM Native Image 实现毫秒级启动。运行优化：(1) HikariCP 连接池参数调优（maximumPoolSize、idleTimeout）；(2) 使用 Caffeine/Redis 多级缓存；(3) @Async + 线程池实现异步任务；(4) 合理设置 JVM 参数 -Xms/-Xmx/-XX:+UseG1GC。还可通过 spring-boot-starter-actuator 监控慢查询和内存使用，定位瓶颈。

15. **监控与运维**
    - 问题：Spring Boot Actuator 的作用是什么？如何实现应用监控？
    - 答案：Actuator 提供生产级监控端点，暴露应用健康状态、指标、环境信息、Bean 生命周期等运行时数据，集成 Prometheus/Grafana 实现可视化监控。
    - 解析：引入 spring-boot-starter-actuator 后，默认暴露 /health、/info、/metrics 等端点。核心端点：(1) /health 查看应用及依赖（DB、Redis）健康状态；(2) /metrics 暴露 JVM、HTTP、自定义指标；(3) /env 查看环境变量；(4) /loggers 动态调整日志级别。安全配置：management.endpoints.web.exposure.include 指定暴露端点，结合 Spring Security 保护敏感端点。集成方案：通过 micrometer-registry-prometheus 导出指标到 Prometheus，Grafana 构建监控面板，实现告警和性能分析。

#### Spring MVC 高级
16. **RESTful API 设计**
    - 问题：如何设计一个符合 RESTful 规范的 API？
    - 答案：遵循资源导向设计，使用名词复数表示资源，HTTP 方法表示操作，状态码表示结果，支持 HATEOAS、分页、过滤。
    - 解析：核心原则：(1) URL 使用名词复数如 /api/users，避免动词；(2) HTTP 方法语义化：GET 查询、POST 创建、PUT 全量更新、PATCH 部分更新、DELETE 删除；(3) 合理使用状态码：200 成功、201 创建、204 无内容、400 参数错误、401 未认证、404 不存在、500 服务器异常；(4) 统一响应格式包含 code、message、data、timestamp；(5) 版本控制：/api/v1/users 或 Accept Header；(6) 分页使用 ?page=1&size=20，响应包含 totalElements；(7) 资源间关系通过嵌套 URI 表达如 /users/1/orders。

17. **文件上传与下载**
    - 问题：Spring MVC 中如何实现大文件上传和断点续传？
    - 答案：大文件上传采用分片上传（前端切割 + 后端合并），断点续传通过记录已上传分片实现。配置 multipart 参数限制单片大小，使用流式处理避免内存溢出。
    - 解析：实现方案：(1) 前端使用 File.slice() 将文件分割为固定大小分片（如 2MB），逐片上传；(2) 后端通过 MultipartFile 接收，配置 spring.servlet.multipart.max-file-size 限制单片大小；(3) 使用文件 MD5 作为唯一标识，结合 Redis 存储已上传分片序号；(4) 后端接收完所有分片后，按序号合并写入目标文件；(5) 断点续传时查询 Redis 返回已上传分片列表，前端跳过已传分片。生产环境建议使用 OSS 直传方案，避免服务端成为瓶颈。

#### Spring Security
18. **认证与授权**
    - 问题：Spring Security 的核心组件有哪些？如何实现 JWT 认证？
    - 答案：核心组件包括 SecurityFilterChain、AuthenticationManager、UserDetailsService、Authentication。JWT 认证流程：登录获取 Token → 请求携带 Header → 过滤器验证并设置认证上下文。
    - 解析：核心过滤链：SecurityFilterChain 定义拦截规则，ExceptionTranslationFilter 处理认证异常，FilterSecurityInterceptor 执行授权。JWT 实现步骤：(1) 自定义 JwtAuthenticationFilter 继承 OncePerRequestFilter；(2) 从 Authorization Header 提取 Bearer Token；(3) 解析 JWT 验证签名、过期时间；(4) 从 Claims 中提取用户信息构建 Authentication 对象；(5) 调用 SecurityContextHolder.getContext().setAuthentication() 设置上下文；(6) 配置 SecurityFilterChain 放行登录接口，拦截其他请求。Token 存储方案：内存、Redis（支持主动失效）、数据库。

19. **OAuth2 集成**
    - 问题：如何在 Spring Boot 中集成 OAuth2？
    - 答案：引入 spring-boot-starter-oauth2-client 依赖，配置第三方登录提供商信息，通过 OAuth2LoginConfigurer 配置登录流程，使用 Spring Security 的 OAuth2User 获取用户信息。
    - 解析：配置步骤：(1) 在 application.yml 中配置 spring.security.oauth2.client.registration（客户端 ID/Secret、回调 URL、授权范围）和 spring.security.oauth2.client.provider（授权/Token URL）；(2) 配置 SecurityFilterChain 启用 oauth2Login()；(3) 系统自动处理授权码流程：重定向授权页 → 获取授权码 → 换取 Token → 获取用户信息；(4) 通过 @AuthenticationPrincipal OAuth2User 获取用户信息完成登录/注册。常用提供商：GitHub（spring.security.oauth2.client.registration.github）、Google、微信等。实现单点登录（SSO）可结合 Spring Authorization Server 或 Keycloak。

### Level 4: 专家题

#### Spring Boot 专家
20. **源码分析**
    - 问题：Spring Boot 启动过程的源码是怎样的？关键类有哪些？
    - 答案：关键类包括 SpringApplication、SpringApplicationRunListener、ApplicationEnvironmentPreparedEvent。启动流程：创建 SpringApplication → run() → prepareEnvironment → refreshContext → afterRefresh → 启动内嵌服务器。
    - 解析：核心源码路径：(1) SpringApplication 构造器推断应用类型（Servlet/Reactive/None），加载 ApplicationContextInitializer 和 ApplicationListener；(2) run() 方法通过 SpringApplicationRunListener（如 EventPublishingRunListener）发布启动事件；(3) prepareEnvironment() 加载所有配置源并绑定 spring.profiles；(4) refreshContext() 调用 AbstractApplicationContext.refresh() 执行 Bean 生命周期管理；(5) ServletWebServerApplicationContext.createWebServer() 创建并启动内嵌 Tomcat/Jetty。关键设计模式：观察者模式（事件机制）、模板方法模式（生命周期钩子）、工厂模式（Bean 创建）。

21. **自定义 Starter**
    - 问题：如何从零开始构建一个生产级的 Spring Boot Starter？
    - 答案：采用双模块结构（autoconfigure + starter），编写自动配置类、属性绑定类，添加条件注解，配置 spring.factories，编写文档和单元测试。
    - 解析：生产级 Starter 开发规范：(1) 模块划分：xxx-spring-boot-starter（仅包含依赖声明 POM）和 xxx-spring-boot-autoconfigure（包含自动配置逻辑）；(2) 属性类：使用 @ConfigurationProperties(prefix="xxx") 绑定配置，支持 IDE 提示；(3) 自动配置类：添加 @AutoConfiguration 注解（Spring Boot 3.x），使用 @ConditionalOnClass/@ConditionalOnMissingBean/@ConditionalOnProperty 条件注解；(4) 注册配置：Spring Boot 3.x 使用 META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports；(5) 支持自定义事件监听器、健康检查指示器；(6) 提供 @EnableXxx 注解支持手动启用；(7) 编写 README 文档和 Spring Boot Test 单元测试。

#### Spring 生态专家
22. **Spring Cloud 集成**
    - 问题：Spring Boot 如何与 Spring Cloud 深度集成？
    - 答案：通过 spring-cloud-dependencies BOM 管理版本，集成服务注册发现（Nacos/Eureka）、配置中心、网关（Gateway）、负载均衡（LoadBalancer）、熔断（Resilience4j）等组件。
    - 解析：集成架构：(1) 服务注册发现：引入 spring-cloud-starter-alibaba-nacos-discovery，配置 Nacos 地址实现服务注册和发现；(2) 配置中心：spring-cloud-starter-alibaba-nacos-config 实现配置集中管理和动态刷新；(3) API 网关：Spring Cloud Gateway 路由转发、限流、鉴权；(4) 负载均衡：spring-cloud-starter-loadbalancer 替代 Ribbon 实现客户端负载；(5) 服务调用：OpenFeign 声明式 HTTP 客户端 + LoadBalancer 负载均衡；(6) 熔断降级：Resilience4j 提供 CircuitBreaker、RateLimiter、Retry；(7) 链路追踪：Micrometer Tracing + Zipkin/SkyWalking。通过 Spring Cloud Bootstrap Context 实现配置中心优先加载。

23. **微服务架构**
    - 问题：基于 Spring Boot 的微服务架构应该如何设计？
    - 答案：采用分层架构设计，包括接入层（Gateway）、业务服务层、数据层、基础设施层。核心设计包括服务拆分、注册发现、配置中心、网关路由、服务间通信、分布式事务。
    - 解析：架构设计要点：(1) 服务拆分：按业务领域划分（DDD），单一职责原则，每个服务独立数据库；(2) 接入层：Spring Cloud Gateway 统一入口，路由转发、限流熔断、日志记录；(3) 服务注册发现：Nacos 提供注册中心和配置中心双重能力；(4) 服务间通信：同步用 OpenFeign + LoadBalancer，异步用 RocketMQ/Kafka；(5) 分布式事务：强一致性用 Seata（AT/TCC 模式），最终一致性用 RocketMQ 事务消息；(6) 数据隔离：每个服务独立数据库，跨服务查询通过 API 组合或数据同步；(7) 可观测性：Micrometer + Prometheus + Grafana 监控，SkyWalking 链路追踪，ELK 日志聚合；(8) 容器化：Docker + Kubernetes 实现弹性伸缩和自动化部署。

## 📖 学习资源

### 书籍推荐
- 《Spring Boot 编程思想》 - 柏杨
- 《Spring 实战》 - Craig Walls
- 《Spring Cloud 微服务实战》 - 翟永超

### 在线资源
- [Spring 官方文档](https://spring.io/projects/spring-boot)
- [Spring Boot 入门教程](https://www.tutorialspoint.com/spring_boot/)
- [JavaGuide Spring Boot 部分](https://javaguide.cn/system-design/framework/spring/spring-boot-questions-01.html)

## 🔗 相关链接

- [Spring Boot 专题](spring-boot/)
- [Spring MVC 专题](spring-mvc/)
- [Spring Data 专题](spring-data/)
