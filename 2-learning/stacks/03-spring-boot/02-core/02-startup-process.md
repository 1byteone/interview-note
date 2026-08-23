# 启动流程 — SpringApplication.run() 完整分析

> 等级：👶→🎯 新手进阶
> 目标：深入理解 Spring Boot 应用启动的全流程，从 main 方法到内嵌容器启动。

---

## 一、10 步启动流程图

```
① SpringApplication.run(主类, args)
    │
    ▼
② 判断应用类型（Reactive / Servlet / None）
    │
    ▼
③ 加载所有 SpringApplicationRunListener（spring.factories）
    │
    ▼
④ 准备 Environment（系统属性 + 环境变量 + 配置文件）
    │
    ▼
⑤ 打印 Banner（ASCII Art / 关闭）
    │
    ▼
⑥ 创建 ApplicationContext（根据类型创建）
    │
    ▼
⑦ 准备上下文：设置 Environment、BeanFactoryPostProcessor、LoadSources
    │
    ▼
⑧ 刷新上下文（AbstractApplicationContext.refresh()）← 最核心
    │
    ▼
⑨ 内嵌容器启动（Tomcat/Jetty/Undertow 启动）
    │
    ▼
⑩ 返回 ApplicationContext（调用 ApplicationRunner / CommandLineRunner）
```

---

## 二、关键步骤详解

### 2.1 步骤①：SpringApplication 初始化

```java
@SpringBootApplication
public class MallApplication {
    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(MallApplication.class);
        app.setBannerMode(Banner.Mode.OFF);  // 关闭 Banner
        app.setAdditionalProfiles("dev");     // 指定额外 Profile
        app.run(args);
    }
}

// 或者一句话
SpringApplication.run(MallApplication.class, args);
```

`SpringApplication` 构造器内部做了两件关键事：

```java
public SpringApplication(ResourceLoader resourceLoader, Class<?>... primarySources) {
    // 1. 推断应用类型：Servlet / Reactive / None
    this.webApplicationType = WebApplicationType.deduceFromClasspath();

    // 2. 从 spring.factories 加载所有 ApplicationContextInitializer
    setInitializers((Collection) getSpringFactoriesInstances(ApplicationContextInitializer.class));

    // 3. 从 spring.factories 加载所有 ApplicationListener
    setListeners((Collection) getSpringFactoriesInstances(ApplicationListener.class));

    // 4. 推断主启动类
    this.mainApplicationClass = deduceMainApplicationClass();
}
```

### 2.2 步骤②：判断应用类型

```java
// WebApplicationType.deduceFromClasspath()
static WebApplicationType deduceFromClasspath() {
    if (ClassUtils.isPresent("org.springframework.web.reactive.DispatcherHandler", null)
            && !ClassUtils.isPresent("org.springframework.web.servlet.DispatcherServlet", null)) {
        return REACTIVE;  // Reactive Web（WebFlux）
    }
    if (ClassUtils.isPresent("javax.servlet.Servlet", null)
            || ClassUtils.isPresent("jakarta.servlet.Servlet", null)) {
        return SERVLET;   // Servlet Web（Spring MVC）
    }
    return NONE;  // 非 Web 应用
}
```

### 2.3 步骤③：事件监听机制

Spring Boot 在启动过程中按顺序发布以下事件：

| 事件 | 发布时机 | 典型用途 |
|------|---------|---------|
| `ApplicationStartingEvent` | 启动开始 | 日志初始化 |
| `ApplicationEnvironmentPreparedEvent` | Environment 准备完毕 | 动态配置、环境检查 |
| `ApplicationContextInitializedEvent` | Context 创建完毕 | 准备操作 |
| `ApplicationPreparedEvent` | refresh 前 | 资源准备 |
| `ApplicationStartedEvent` | refresh 后、Runner 前 | 启动后处理 |
| `AvailabilityChangeEvent` | 可用状态变化 | 健康检查 |
| `ApplicationReadyEvent` | Runner 执行完毕 | 应用就绪通知 |
| `ApplicationFailedEvent` | 启动失败 | 失败告警 |

```java
// 监听启动事件
@Component
public class ApplicationEventListener {

    @EventListener(ApplicationReadyEvent.class)
    public void onReady(ApplicationReadyEvent event) {
        System.out.println("应用已就绪，端口：" +
            event.getApplicationContext().getEnvironment()
                .getProperty("server.port"));
    }
}
```

### 2.4 步骤④：准备 Environment

```java
// 加载配置文件的顺序
// 1. 默认配置（application.yml / application.properties）
// 2. profile 特定配置（application-dev.yml）
// 3. 命令行参数（--server.port=9090）
// 4. OS 环境变量
// 5. 随机值（random.*）
```

### 2.5 步骤⑧：refresh() 方法——容器的核心

`AbstractApplicationContext.refresh()` 是 Spring 容器初始化的核心，包含 13 个子步骤：

```java
@Override
public void refresh() throws BeansException, IllegalStateException {
    synchronized (this.startupShutdownMonitor) {
        // 1. 准备刷新上下文：设置时间、初始化属性源
        prepareRefresh();

        // 2. 获取 BeanFactory（DefaultListableBeanFactory）
        ConfigurableListableBeanFactory beanFactory = obtainFreshBeanFactory();

        // 3. BeanFactory 预准备：设置类加载器、SpEL 解析器、注册环境 Bean
        prepareBeanFactory(beanFactory);

        // 4. BeanFactory 后置处理（子类模板方法）
        postProcessBeanFactory(beanFactory);

        // 5. 调用 BeanFactoryPostProcessor（处理 @Configuration、@PropertySource）
        invokeBeanFactoryPostProcessors(beanFactory);

        // 6. 注册 BeanPostProcessor（@Conditional 在此生效）
        registerBeanPostProcessors(beanFactory);

        // 7. 初始化 MessageSource（国际化）
        initMessageSource();

        // 8. 初始化应用事件广播器
        initApplicationEventMulticaster();

        // 9. 模板方法：子类初始化特殊 Bean（如内嵌容器）
        onRefresh();

        // 10. 注册监听器
        registerListeners();

        // 11. 实例化所有非懒加载的单例 Bean
        finishBeanFactoryInitialization(beanFactory);

        // 12. 完成刷新：发布事件、启动内嵌容器
        finishRefresh();

        // 13. 清除缓存
        resetCommonCaches();
    }
}
```

> **关键点**：第 5 步 `invokeBeanFactoryPostProcessors` 处理 @Configuration、@Import、@ComponentScan；第 6 步注册的 BeanPostProcessor 在第 11 步实例化 Bean 时执行（@PostConstruct、AOP 代理等）。

---

## 三、内嵌 Tomcat 启动原理

### 3.1 内嵌容器自动配置

```java
// ServletWebServerFactoryAutoConfiguration 是自动配置的入口
@AutoConfiguration
@ConditionalOnClass(Servlet.class)
@EnableConfigurationProperties(ServerProperties.class)
public class ServletWebServerFactoryAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public TomcatServletWebServerFactory tomcatServletWebServerFactory() {
        return new TomcatServletWebServerFactory();
    }
}
```

### 3.2 Tomcat 启动流程

```java
// refresh() 第 9 步：onRefresh() → 创建 WebServer
// 第 12 步：finishRefresh() → 启动 WebServer

// ServletWebServerApplicationContext.onRefresh()
protected void onRefresh() {
    super.onRefresh();
    try {
        createWebServer();  // 创建 Tomcat 实例
    } catch (Throwable ex) {
        throw new ApplicationContextException("Unable to start web server", ex);
    }
}

private void createWebServer() {
    // 1. 获取 ServletWebServerFactory（TomcatServletWebServerFactory）
    ServletWebServerFactory factory = getWebServerFactory();

    // 2. 通过 getSelfInitializer() 注册 Servlet、Filter
    // 3. factory.getWebServer(getSelfInitializer()) 创建并返回 WebServer
    this.webServer = factory.getWebServer(getSelfInitializer());

    // 4. finishRefresh() 时调用 webServer.start()
}
```

### 3.3 定制内嵌 Tomcat

```java
// 方式一：配置属性
server:
  port: 8080
  tomcat:
    max-connections: 10000
    max-threads: 200
    accept-count: 100
    connection-timeout: 5000ms
    uri-encoding: UTF-8

// 方式二：WebServerFactoryCustomizer
@Component
public class TomcatCustomizer implements WebServerFactoryCustomizer<TomcatServletWebServerFactory> {

    @Override
    public void customize(TomcatServletWebServerFactory factory) {
        factory.setPort(9090);
        factory.setContextPath("/mall");
        factory.addErrorPages(new ErrorPage(HttpStatus.NOT_FOUND, "/404.html"));
    }
}
```

### 3.4 切换 Jetty / Undertow

```xml
<!-- 排除 Tomcat，引入 Jetty -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
    <exclusions>
        <exclusion>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-tomcat</artifactId>
        </exclusion>
    </exclusions>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-jetty</artifactId>
</dependency>
```

---

## 四、启动性能优化

### 4.1 常见瓶颈

| 瓶颈 | 原因 | 优化方案 |
|------|------|---------|
| 类路径扫描 | @ComponentScan 扫描范围过大 | 指定具体包路径 |
| 自动配置类过多 | 加载了不需要的自动配置 | 排除不需要的配置 |
| Bean 初始化过慢 | @PostConstruct 中做耗时操作 | 延迟加载 @Lazy |
| 配置加载慢 | 远程配置中心拉取慢 | 缓存、超时设置 |

### 4.2 实践优化

```java
// 排除不需要的自动配置
@SpringBootApplication(exclude = {
    DataSourceAutoConfiguration.class,
    SecurityAutoConfiguration.class
})

// 延迟加载非核心 Bean
@Service
@Lazy(true)
public class ReportService { ... }

// 指定扫描范围（缩小扫描路径）
@ComponentScan(basePackages = "com.example.mall.order")
```

---

## 五、面试要点

| 问题 | 一句话答案 |
|------|-----------|
| SpringApplication.run() 做了哪些事？ | 创建 SpringApplication → 推断类型 → 加载监听器 → 准备 Environment → 创建 Context → 刷新 → 启动容器 → 运行 Runner |
| refresh() 方法的核心步骤是什么？ | 预处理 → 获取 BeanFactory → 执行 BeanFactoryPostProcessor → 注册 BeanPostProcessor → onRefresh → 实例化单例 → finishRefresh |
| 内嵌 Tomcat 什么时候启动？ | refresh() 的 onRefresh 创建 Tomcat，finishRefresh 启动 Tomcat |
| 怎么切换内嵌容器？ | 排除 tomcat-starter，引入 jetty-starter 或 undertow-starter |
| 如何定制内嵌容器？ | 配置属性 + WebServerFactoryCustomizer |
| 应用启动完成怎么通知？ | @EventListener(ApplicationReadyEvent.class) |

> 理解了启动流程，进入高级篇：Actuator 可观测性、测试框架、GraalVM 原生编译。