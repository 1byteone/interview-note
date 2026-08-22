# 深挖题 — 源码级深入分析

> 等级：🎯 面试进阶
> 目标：深入理解 Spring Boot 自动配置源码、条件注解底层、内嵌 Tomcat 生命周期。

---

## 一、从 @SpringBootApplication 到 Bean 注入的完整链路

### 1.1 核心链路图

```
@SpringBootApplication
    │
    ▼
@EnableAutoConfiguration
    │
    ▼
@Import(AutoConfigurationImportSelector.class)
    │
    ▼
AutoConfigurationImportSelector.selectImports()
    │ 1. getCandidateConfigurations() → 读取 AutoConfiguration.imports
    │ 2. removeDuplicates() → 去重
    │ 3. sort() → 按 @AutoConfigureOrder / @AutoConfigureBefore / @AutoConfigureAfter 排序
    │ 4. removeExclusions() → 排除 @SpringBootApplication(exclude=...) 指定的类
    │
    ▼
ConfigurationClassParser.processImports()
    │ 解析 @Configuration 类，处理 @Bean、@Import、@ComponentScan
    │
    ▼
ConditionEvaluator.shouldSkip()
    │ 逐条执行 @Conditional 注解，不满足条件的配置类跳过
    │
    ▼
ConfigurationClassBeanDefinitionReader
    │ 将满足条件的 @Bean 方法注册到 BeanDefinitionRegistry
    │
    ▼
AbstractApplicationContext.refresh() → finishBeanFactoryInitialization()
    │ 实例化所有非懒加载的单例 Bean
    │
    ▼
BeanPostProcessor 前置 → 初始化 → BeanPostProcessor 后置（AOP 代理）
    │
    ▼
就绪可用
```

### 1.2 AutoConfigurationImportSelector 源码分析

```java
public class AutoConfigurationImportSelector
        implements DeferredImportSelector, BeanClassLoaderAware, ... {

    @Override
    public String[] selectImports(AnnotationMetadata annotationMetadata) {
        // 核心：读取候选配置类
        AutoConfigurationEntry autoConfigurationEntry =
            getAutoConfigurationEntry(annotationMetadata);
        return autoConfigurationEntry.getConfigurations()
            .toArray(new String[0]);
    }

    protected AutoConfigurationEntry getAutoConfigurationEntry(
            AnnotationMetadata annotationMetadata) {
        // 1. 检查是否开启（默认开启）
        if (!isEnabled(annotationMetadata)) {
            return EMPTY_ENTRY;
        }

        // 2. 获取所有候选配置类（从 AutoConfiguration.imports 读取）
        AnnotationAttributes attributes = getAttributes(annotationMetadata);
        List<String> configurations = getCandidateConfigurations(annotationMetadata, attributes);

        // 3. 去重
        configurations = removeDuplicates(configurations);

        // 4. 解析 @AutoConfigureOrder / @AutoConfigureBefore / @AutoConfigureAfter
        Set<String> exclusions = getExclusions(annotationMetadata, attributes);
        configurations = sort(configurations, autoConfigurationMetadata);

        // 5. 排除指定的类
        configurations = removeExclusions(configurations, exclusions);

        // 6. 条件过滤（此时只读取配置，条件在后续解析时执行）
        return new AutoConfigurationEntry(configurations, exclusions);
    }
}
```

### 1.3 为什么是 DeferredImportSelector？

```java
// 普通 ImportSelector：在处理当前 @Configuration 时立即执行
// DeferredImportSelector：在所有 @Configuration 处理完后执行

// 关键影响：
// 1. 用户自定义 @Configuration 优先处理
// 2. 用户的 @Bean 先注册到容器
// 3. 自动配置的 @ConditionalOnMissingBean 才能正确判断

// 面试回答：自动配置用 DeferredImportSelector 保证用户自定义 Bean 优先。
```

---

## 二、条件注解底层实现

### 2.1 @Conditional 接口

所有条件注解最终都基于 `@Conditional` 元注解：

```java
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Conditional {
    Class<? extends Condition>[] value();
}

// Condition 接口
@FunctionalInterface
public interface Condition {
    boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata);
}
```

### 2.2 @ConditionalOnClass 的实现

```java
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Conditional(OnClassCondition.class)
public @interface ConditionalOnClass {
    Class<?>[] value() default {};
    String[] name() default {};
}

// OnClassCondition 的核心逻辑
class OnClassCondition extends FilteringSpringBootCondition {

    @Override
    protected ConditionOutcome[] getOutcomes(String[] autoConfigurationClasses,
            AutoConfigurationMetadata autoConfigurationMetadata) {
        // 遍历所有自动配置类，检查其 @ConditionalOnClass 指定的类是否存在
        // 如果不存在，返回 ConditionOutcome.match(false)
        // 实际上是通过 ClassUtils.isPresent() 判断 classpath 上有没有指定类
    }
}
```

```java
// 核心判断逻辑简化
public static boolean isPresent(String className, ClassLoader classLoader) {
    try {
        Class.forName(className, false, classLoader);
        return true;
    } catch (ClassNotFoundException | NoClassDefFoundError e) {
        return false;
    }
}
```

### 2.3 @ConditionalOnMissingBean 的实现

```java
class OnBeanCondition extends SpringBootCondition {

    @Override
    public ConditionOutcome getMatchOutcome(ConditionContext context,
            AnnotatedTypeMetadata metadata) {
        // 1. 获取 @ConditionalOnMissingBean 的 value / name / type
        // 2. 从 BeanFactory 查询是否有匹配的 Bean
        // 3. 检查 BeanDefinition（包括尚未实例化的）
        // 4. 如果找到匹配的 Bean → 不满足条件（跳过自动配置）
        // 5. 如果没找到 → 满足条件（创建自动配置的 Bean）
    }
}
```

> **关键点**：`@ConditionalOnMissingBean` 检查的是**BeanDefinition**，不是实例化后的 Bean。所以即使 Bean 还没实例化，只要定义已经存在，也能判断出来。

---

## 三、内嵌 Tomcat 生命周期

### 3.1 ServletWebServerApplicationContext 中的容器管理

```java
public class ServletWebServerApplicationContext extends GenericWebApplicationContext {

    // 1. onRefresh() → 创建 WebServer
    @Override
    protected void onRefresh() {
        super.onRefresh();
        try {
            createWebServer();  // 创建 Tomcat 实例
        } catch (Throwable ex) {
            throw new ApplicationContextException("无法启动 Web 服务器", ex);
        }
    }

    private void createWebServer() {
        // 获取 ServletWebServerFactory（默认是 TomcatServletWebServerFactory）
        ServletWebServerFactory factory = getWebServerFactory();

        // 通过 getSelfInitializer() 注册 Servlet、Filter、ServletContextInitializer
        // factory.getWebServer(getSelfInitializer()) 创建并返回 WebServer
        this.webServer = factory.getWebServer(getSelfInitializer());
    }

    // 2. finishRefresh() → 启动 WebServer
    @Override
    protected void finishRefresh() {
        super.finishRefresh();
        WebServer webServer = startWebServer();  // 启动 Tomcat
        if (webServer != null) {
            // 发布 ServletWebServerInitializedEvent
        }
    }
}
```

### 3.2 TomcatServletWebServerFactory 创建 Tomcat

```java
public class TomcatServletWebServerFactory extends AbstractServletWebServerFactory
        implements ConfigurableTomcatWebServerFactory, ResourceLoaderAware {

    @Override
    public WebServer getWebServer(ServletContextInitializer... initializers) {
        // 1. 创建 Tomcat 实例
        Tomcat tomcat = new Tomcat();

        // 2. 设置端口、Host、Connector
        File baseDir = createTempDir("tomcat");
        tomcat.setBaseDir(baseDir.getAbsolutePath());
        Connector connector = new Connector("org.apache.coyote.http11.Http11NioProtocol");
        connector.setPort(getPort());
        tomcat.setConnector(connector);

        // 3. 创建 Context（Web 应用上下文）
        Context context = tomcat.addContext("", baseDir.getAbsolutePath());

        // 4. 注册所有 ServletContextInitializer（DispatcherServlet、Filter 等）
        configureContext(context, initializers);

        // 5. 返回包装后的 TomcatWebServer
        TomcatWebServer server = new TomcatWebServer(tomcat, getPort() >= 0);
        return server;
    }
}
```

### 3.3 Tomcat 启动过程

```java
// TomcatWebServer.start() 调用
@Override
public void start() throws WebServerException {
    try {
        // 添加 ShutdownHook，确保优雅关闭
        addInstanceRecordToMBeanServer();

        // 启动 Tomcat 引擎
        tomcat.start();

        // 等待 Tomcat 完全启动（端口就绪）
        tomcat.getServer().await();
    } catch (Exception ex) {
        throw new WebServerException("无法启动 Tomcat", ex);
    }
}
```

### 3.4 优雅关闭

```java
// server.shutdown=graceful 时，Spring Boot 注册了一个 ShutdownHook
// 收到 SIGTERM 信号 → 执行以下流程：
// 1. TomcatWebServer.stop() → 停止接受新连接
// 2. 等待活跃请求处理完成（最多等待 timeout-per-shutdown-phase 秒）
// 3. 销毁 ServletContext
// 4. 关闭 ApplicationContext
// 5. 释放资源
```

---

## 四、面试关键点总结

| 主题 | 源码级别 | 面试价值 |
|------|---------|---------|
| AutoConfigurationImportSelector | selectImports 四步法 | 展现自动配置全链路理解 |
| AutoConfiguration.imports 加载 | SpringFactoriesLoader.loadFactoryNames | 知道 2.x vs 3.x 区别 |
| ConditionEvaluator | 逐条执行 @Conditional | 理解条件注解执行时机 |
| OnClassCondition | ClassUtils.isPresent | 理解 classpath 判断 |
| OnBeanCondition | 检查 BeanDefinition | 理解 @ConditionalOnMissingBean 如何判断 |
| TomcatServletWebServerFactory | getWebServer → Tomcat 实例化 | 理解内嵌容器创建过程 |

> 进入场景题篇：多环境配置管理、应用启动慢优化、内存泄漏排查。