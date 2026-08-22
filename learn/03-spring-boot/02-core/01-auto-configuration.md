# 自动配置原理 — 从注解到 Bean 的完整链路

> 等级：👶→🎯 新手进阶
> 目标：深入理解 Spring Boot 自动配置的完整链路，从注解、条件判断到自定义 Starter。

---

## 一、@SpringBootApplication 组合注解

```java
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@SpringBootConfiguration   // = @Configuration，标记为配置类
@EnableAutoConfiguration   // ← 自动配置的入口
@ComponentScan(excludeFilters = {
    @Filter(type = FilterType.CUSTOM, classes = TypeExcludeFilter.class),
    @Filter(type = FilterType.CUSTOM, classes = AutoConfigurationExcludeFilter.class)
})
public @interface SpringBootApplication {
}
```

三个核心注解：

| 注解 | 作用 |
|------|------|
| `@SpringBootConfiguration` | 本质是 `@Configuration`，声明当前类是配置类 |
| `@EnableAutoConfiguration` | **自动配置开关**，加载 auto-configuration 类 |
| `@ComponentScan` | 扫描当前包及子包的 `@Component`/`@Service` 等组件 |

> 默认扫描路径：**启动类所在包及其子包**。所以启动类通常放在项目根包下。

---

## 二、@EnableAutoConfiguration 的加载机制

### 2.1 两种注册机制（版本差异）

| 版本 | 路径 | 说明 |
|------|------|------|
| Spring Boot 2.7- | `META-INF/spring.factories` | key 为 `org.springframework.boot.autoconfigure.EnableAutoConfiguration` |
| Spring Boot 2.7+ / 3.x | `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` | 每行一个自动配置类全类名 |

```properties
# META-INF/spring.factories (Spring Boot 2.x 写法)
org.springframework.boot.autoconfigure.EnableAutoConfiguration=\
com.example.mystarter.MyAutoConfiguration
```

```java
// META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports (Spring Boot 3.x 写法)
// 每行一个自动配置类，无需 key-value
com.example.mystarter.MyAutoConfiguration
com.example.mystarter.RedisAutoConfiguration
```

### 2.2 自动配置类的标准结构

```java
// 自动配置类：必须用 @AutoConfiguration 标注
@AutoConfiguration
// 条件：classpath 存在某类时才启用
@ConditionalOnClass({RedisTemplate.class})
// 配置属性绑定：把 spring.data.redis.* 属性绑定到 RedisProperties
@EnableConfigurationProperties(RedisProperties.class)
public class RedisAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = "redisTemplate")
    // RedisTemplate 不存在时才创建，允许用户自定义覆盖
    public RedisTemplate<Object, Object> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<Object, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);
        return template;
    }
}
```

### 2.3 AutoConfigurationImportSelector 源码分析

`@EnableAutoConfiguration` 通过 `@Import(AutoConfigurationImportSelector.class)` 引入：

```java
public class AutoConfigurationImportSelector
        implements DeferredImportSelector, BeanClassLoaderAware, ... {

    // 核心方法：返回要注册的自动配置类全类名数组
    @Override
    public String[] selectImports(AnnotationMetadata annotationMetadata) {
        // 1. 读取 AutoConfiguration.imports / spring.factories 中的候选类
        List<String> configurations = getCandidateConfigurations(...);

        // 2. 去重
        configurations = removeDuplicates(configurations);

        // 3. 按 @AutoConfigureBefore/@AutoConfigureAfter/@AutoConfigureOrder 排序
        configurations = sort(configurations, autoConfigurationMetadata);

        // 4. 排除 @SpringBootApplication(exclude=...) 指定的类
        configurations = removeExclusions(configurations, exclusions);

        // 5. 检查类加载器（省略）
        return configurations.toArray(new String[0]);
    }
}
```

> **关键点**：`DeferredImportSelector` 延迟导入——等所有用户自定义 `@Configuration` 处理完后再导入自动配置类，这样用户配置优先于自动配置。

---

## 三、条件注解全家桶

### 3.1 核心条件注解

| 注解 | 判断条件 | 典型用途 |
|------|---------|---------|
| `@ConditionalOnClass` | classpath 存在指定类 | 引入了 starter 才启用配置 |
| `@ConditionalOnMissingClass` | classpath 不存在指定类 | 兜底逻辑 |
| `@ConditionalOnBean` | 容器存在指定 Bean | 依赖已有 Bean |
| `@ConditionalOnMissingBean` | 容器不存在指定 Bean | 允许用户覆盖默认配置 |
| `@ConditionalOnProperty` | 存在指定配置属性 | 开关控制（enabled=true） |
| `@ConditionalOnExpression` | SpEL 表达式为 true | 复杂组合条件 |
| `@ConditionalOnWebApplication` | 当前是 Web 应用 | Web 相关自动配置 |
| `@ConditionalOnResource` | classpath 存在指定资源 | 依赖资源文件 |

### 3.2 @ConditionalOnProperty 详解

```java
// 只有 spring.maxwell.enabled=true 时才注册 Bean（默认关闭）
@Bean
@ConditionalOnProperty(prefix = "spring.maxwell", name = "enabled", havingValue = "true", matchIfMissing = false)
public MaxwellConfig maxwellConfig() {
    return new MaxwellConfig();
}
```

### 3.3 条件注解的执行时机

**重要**：条件注解在**配置类解析时**（refresh 阶段）执行，不是运行时。`@ConditionalOnBean` 只能看到**已经注册**的 Bean——所以要配合 `@AutoConfigureBefore`/`@AutoConfigureAfter` 控制先后顺序。

### 3.4 组合条件：@Conditional 自定义

```java
// 自己实现 Condition 接口
public class LinuxCondition implements Condition {
    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        Environment env = context.getEnvironment();
        return env.getProperty("os.name").toLowerCase().contains("linux");
    }
}

@Configuration
@Conditional(LinuxCondition.class)
public class LinuxConfig { ... }
```

---

## 四、自定义 Starter 实战

### 4.1 Starter 标准三件套

一个完整的自定义 Starter 通常包含 3 个模块（也可以合并）：

```
my-starter/
├── my-spring-boot-autoconfigure/   ← 自动配置模块（核心）
│   ├── src/main/resources/META-INF/
│   │   └── spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
│   └── ...自动配置类
├── my-spring-boot-starter/         ← 空模块，只依赖 autoconfigure
│   └── pom.xml
└── pom.xml（父工程）
```

### 4.2 完整实现：短信发送 Starter

**第一步：属性类**

```java
package com.example.sms;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "sms")
public class SmsProperties {
    /** 短信服务商地址 */
    private String baseUrl = "http://default.aliyun.com";
    /** AccessKey */
    private String accessKey;
    /** 默认签名 */
    private String signName = "mall";
    /** 是否启用 */
    private boolean enabled = true;

    // getter / setter 省略
}
```

**第二步：服务类**

```java
package com.example.sms;

public class SmsService {
    private final SmsProperties properties;

    public SmsService(SmsProperties properties) {
        this.properties = properties;
    }

    public boolean send(String phone, String content) {
        // 实际调用短信服务商 API，这里简化
        System.out.println("发送短信到 " + phone + ": " + content);
        return true;
    }
}
```

**第三步：自动配置类**

```java
package com.example.sms;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass(SmsService.class)
@ConditionalOnProperty(prefix = "sms", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(SmsProperties.class)
public class SmsAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public SmsService smsService(SmsProperties properties) {
        return new SmsService(properties);
    }
}
```

**第四步：注册文件**

```java
// src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
com.example.sms.SmsAutoConfiguration
```

**第五步：使用方配置**

```yaml
# application.yml
sms:
  access-key: ${SMS_ACCESS_KEY}
  sign-name: 智慧商城
  enabled: true
```

```java
@Service
public class OrderNotifyService {
    private final SmsService smsService;  // 开箱即用，直接注入

    public OrderNotifyService(SmsService smsService) {
        this.smsService = smsService;
    }

    public void notifyOrder(Long orderId, String phone) {
        smsService.send(phone, "您的订单 " + orderId + " 已支付成功");
    }
}
```

---

## 五、面试 STAR 案例：解决 Redis 序列化乱码

**Situation**：商城订单服务存入 Redis 的 value 出现乱码，原因：默认 StringRedisSerializer 只序列化 String，对象序列化后是 JDK 二进制。

**Task**：研究自动配置机制，让全局 Redis 序列化器为 JSON，且不影响其他服务。

**Action**：

```java
@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);

        // key 用 String 序列化器
        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);

        // value 用 JSON 序列化器
        Jackson2JsonRedisSerializer<Object> jsonSerializer =
            new Jackson2JsonRedisSerializer<>(Object.class);
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);

        template.afterPropertiesSet();
        return template;
    }
}
```

**Result**：因为自动配置里 `RedisTemplate` 是 `@ConditionalOnMissingBean`，我们自定义的 Bean 优先级更高，全局 JSON 序列化生效，乱码问题解决，测试通过。

---

## 六、面试要点

| 问题 | 一句话答案 |
|------|-----------|
| 自动配置是怎么被加载的？ | @EnableAutoConfiguration → AutoConfigurationImportSelector → 读取 imports 文件 → 条件过滤 → 注册 Bean |
| 为什么叫"延迟导入"？ | DeferredImportSelector 等用户配置处理完再导入，用户自定义优先 |
| 条件注解什么时候执行？ | 容器 refresh 阶段解析配置类时执行 |
| 自动配置为什么可以被覆盖？ | @ConditionalOnMissingBean——用户已定义的 Bean 不再创建 |
| 2.x 和 3.x 配置注册区别？ | spring.factories → AutoConfiguration.imports |
| 怎么控制自动配置顺序？ | @AutoConfigureBefore / @AutoConfigureAfter / @AutoConfigureOrder |

> 理解了自动配置，进入下一节：Spring Boot 启动流程，看 SpringApplication.run() 是如何把这一切串起来的。