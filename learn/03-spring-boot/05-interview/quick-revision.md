# 速记版 — 30 个高频考点一句话版

> 等级：🎯 面试冲刺
> 目标：考前 30 分钟快速回顾，自动配置/条件注解/启动流程/内嵌容器/Actuator/测试 各 5 个考点。

---

## 一、自动配置（5 个考点）

| # | 考点 | 一句话 |
|---|------|--------|
| 1 | @SpringBootApplication 组合 | @SpringBootConfiguration + @EnableAutoConfiguration + @ComponentScan |
| 2 | 自动配置加载机制 | @EnableAutoConfiguration → AutoConfigurationImportSelector → 读取 AutoConfiguration.imports → 条件过滤 → 注册 Bean |
| 3 | imports 文件路径 | `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`，每行一个自动配置类 |
| 4 | DeferredImportSelector | 延迟导入，等用户 @Configuration 处理完再导入自动配置，保证用户自定义优先 |
| 5 | 自动配置覆盖原理 | 用户自定义的 Bean 优先（@ConditionalOnMissingBean 判断） |

---

## 二、条件注解（5 个考点）

| # | 考点 | 一句话 |
|---|------|--------|
| 1 | @ConditionalOnClass | classpath 存在指定类时生效，用于判断是否引入了某个 Starter |
| 2 | @ConditionalOnMissingBean | 容器不存在指定 Bean 时生效，用户自定义可覆盖默认配置 |
| 3 | @ConditionalOnProperty | 存在指定配置属性时生效，常用于特性开关 |
| 4 | @ConditionalOnWebApplication | 当前是 Web 应用时生效，区分 Spring MVC 和 WebFlux |
| 5 | 条件注解执行时机 | 容器 refresh 阶段解析配置类时执行，不是运行时 |

---

## 三、启动流程（5 个考点）

| # | 考点 | 一句话 |
|---|------|--------|
| 1 | 核心流程 10 步 | 推断类型 → 加载监听器 → 准备 Environment → 创建 Context → 刷新 → 启动容器 → 运行 Runner |
| 2 | refresh() 核心 | 获取 BeanFactory → 执行 BeanFactoryPostProcessor → 注册 BeanPostProcessor → onRefresh → 实例化单例 → finishRefresh |
| 3 | 应用事件顺序 | ApplicationStartingEvent → EnvironmentPrepared → ContextInitialized → Prepared → Started → Ready |
| 4 | 启动失败怎么通知 | @EventListener + ApplicationFailedEvent，或实现 ApplicationListener |
| 5 | 初始化器 | ApplicationContextInitializer 在 Context 创建后、refresh 前回调 |

---

## 四、内嵌容器（5 个考点）

| # | 考点 | 一句话 |
|---|------|--------|
| 1 | 默认内嵌容器 | Tomcat，通过 spring-boot-starter-tomcat 引入 |
| 2 | 容器启动时机 | refresh() 的 onRefresh 创建 WebServer，finishRefresh 启动 Tomcat |
| 3 | 切换容器 | 排除 tomcat-starter，引入 jetty-starter / undertow-starter |
| 4 | 定制容器 | application.yml 配置 + WebServerFactoryCustomizer 接口 |
| 5 | 优雅停机 | server.shutdown=graceful + spring.lifecycle.timeout-per-shutdown-phase |

---

## 五、Actuator（5 个考点）

| # | 考点 | 一句话 |
|---|------|--------|
| 1 | 核心端点 | /health（健康检查）、/metrics（指标）、/info（应用信息）、/env（环境属性） |
| 2 | 健康检查自定义 | 实现 HealthIndicator 接口，返回 Health.up() / Health.down() |
| 3 | K8s 探针 | /actuator/health/liveness（存活）和 /readiness（就绪） |
| 4 | Micrometer | 指标门面，统一输出到 Prometheus / InfluxDB，支持 Counter / Gauge / Timer |
| 5 | 安全注意 | 用 management.server.port 独立端口 + Spring Security 认证 |

---

## 六、测试（5 个考点）

| # | 考点 | 一句话 |
|---|------|--------|
| 1 | @SpringBootTest | 启动完整应用上下文做集成测试 |
| 2 | @WebMvcTest | 只加载 Controller 层，切片测试，速度快 |
| 3 | @DataJpaTest | 只加载 Repository 层，测试 SQL 正确性 |
| 4 | MockMvc | 模拟 HTTP 请求，测试 Controller 的响应状态码和 JSON 格式 |
| 5 | Testcontainers | 用真实中间件容器做集成测试，避免 H2 模拟差异 |

---

## 速记口诀

```
自动配置三步走：注解 → 读取 → 过滤
条件判断五兄弟：Class / Bean / Property / Web / Expression
启动流程一句话：加载 → 准备 → 创建 → 刷新 → 启动
容器三件套：Tomcat 默认 / Jetty 轻量 / Undertow 高并发
Actuator 四件套：health / metrics / info / env
```

> 进入深挖题篇：自动配置源码级分析、条件注解底层原理。