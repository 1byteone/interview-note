# Spring 容器 — IoC · DI · Bean 生命周期

> 等级：👶 新手通道
> 目标：理解 Spring 容器最核心的 IoC（控制反转）与 DI（依赖注入），以及 Bean 的完整生命周期。
> 这是读懂自动配置的前提——自动配置的产物本质上就是"往容器里注入 Bean"。

---

## 一、IoC 与 DI

### 1.1 什么是 IoC（控制反转）

传统模式：对象自己创建依赖（new），控制权在对象手里。

```java
// 传统方式：OrderService 自己 new DAO
public class OrderService {
    private final OrderDao orderDao = new OrderDaoImpl();  // 硬编码耦合
}
```

IoC 模式：对象只声明"我需要什么"，由容器统一创建并注入。

```java
// IoC 方式：OrderService 只声明依赖
@Service
public class OrderService {
    private final OrderDao orderDao;  // 谁来实现？由容器决定

    @Autowired  // 或构造器注入
    public OrderService(OrderDao orderDao) {
        this.orderDao = orderDao;
    }
}
```

**控制反转**：创建对象的控制权从"对象自己"反转给"容器"。
**依赖注入（DI）**：容器把依赖"注入"到对象中，是 IoC 的实现方式。

### 1.2 三种注入方式对比

| 方式 | 代码 | 优点 | 缺点 |
|------|------|------|------|
| 构造器注入 | `@Autowired` 在构造器上 | 不可变性、易测试、防循环依赖 | 参数多时代码长 |
| Setter 注入 | `@Autowired` 在 setter 上 | 可选依赖、可重配置 | 可变性、易漏注入 |
| 字段注入 | `@Autowired` 在字段上 | 代码最简洁 | 难测试、隐藏依赖、破坏不可变性 |

> **推荐**：Spring 官方推荐构造器注入。字段注入在团队规范中通常被禁止使用。

### 1.3 IoC 容器的核心接口

```java
// Spring 容器顶层接口
BeanFactory beanFactory = new AnnotationConfigApplicationContext(AppConfig.class);

// 更丰富的容器（BeanFactory 的子接口，支持 AOP、事件等）
ApplicationContext context = new AnnotationConfigApplicationContext(AppConfig.class);

// 从容器获取 Bean
OrderService orderService = context.getBean(OrderService.class);
```

---

## 二、@Configuration 与 @Bean

### 2.1 @Configuration 类配置

```java
package com.example.quickstart.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration  // 标记为配置类（本身也是 @Component）
public class AppConfig {

    @Bean  // 方法返回的对象放入容器
    public UserService userService() {
        return new UserService();
    }

    @Bean
    public OrderService orderService(UserService userService) {
        // 方法参数会由容器自动注入
        return new OrderService(userService);
    }
}
```

### 2.2 组件扫描 vs 配置类

| 方式 | 用法 | 适用场景 |
|------|------|---------|
| 组件扫描 | `@Component`/`@Service`/`@Repository`/`@Controller` | 自己写的服务类 |
| @Bean 配置 | 手动声明 Bean | 第三方类、需要定制初始化逻辑的类 |
| @Import | 导入其他配置类 | 组合多个配置 |

### 2.3 @Bean 的属性

```java
@Configuration
public class DataSourceConfig {

    // name: 手动指定 Bean 名称，默认是方法名
    // initMethod: 初始化回调
    // destroyMethod: 销毁回调
    @Bean(name = "mainDataSource", initMethod = "init", destroyMethod = "close")
    public DataSource mainDataSource() {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl("jdbc:mysql://localhost:3306/mall");
        ds.setMaximumPoolSize(20);
        return ds;
    }
}
```

---

## 三、Bean 生命周期

### 3.1 完整生命周期

```
实例化 (Instantiation)
    ↓
属性填充 (Populate Properties) ← 依赖注入发生在这里
    ↓
Aware 接口回调 (setBeanName, setBeanFactory, setApplicationContext)
    ↓
BeanPostProcessor 前置处理 (postProcessBeforeInitialization)
    ↓
初始化 (Initialization)
    ├── @PostConstruct / InitializingBean.afterPropertiesSet()
    └── 自定义 initMethod
BeanPostProcessor 后置处理 (postProcessAfterInitialization)  ← AOP 代理在此生成
    ↓
就绪使用
    ↓
销毁 (Destruction)
    ├── @PreDestroy / DisposableBean.destroy()
    └── 自定义 destroyMethod
```

### 3.2 初始化阶段的三种方式

```java
@Component
public class CacheService {

    private Map<String, Object> cache;

    // 方式一：@PostConstruct（推荐，Spring 特定）
    @PostConstruct
    public void init() {
        cache = new ConcurrentHashMap<>();
        System.out.println("CacheService 初始化完成");
    }

    // 方式二：实现 InitializingBean 接口
    // @Override
    // public void afterPropertiesSet() throws Exception { ... }

    // 方式三：@Bean(initMethod = "init") 手动指定
}
```

> **关键面试点**：三种初始化方式的执行顺序——`@PostConstruct` > `InitializingBean.afterPropertiesSet()` > `initMethod`。

### 3.3 BeanPostProcessor —— 容器最强大的扩展点

```java
@Component
public class MyBeanPostProcessor implements BeanPostProcessor {

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) {
        // 每个 Bean 初始化前都会被调用
        if (bean instanceof UserService) {
            System.out.println("UserService 初始化前");
        }
        return bean;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        // 每个 Bean 初始化后被调用 —— AOP 代理在这里生成
        if (bean instanceof UserService) {
            return new UserServiceProxy((UserService) bean);  // 返回代理对象
        }
        return bean;
    }
}
```

**为什么 BeanPostProcessor 重要？** Spring AOP、事务管理（@Transactional）、@Async 的底层都靠它在初始化后生成代理对象。自动配置中大量使用它。

---

## 四、Bean 作用域

### 4.1 五种作用域

| 作用域 | 说明 | 生命周期 |
|--------|------|---------|
| singleton（默认） | 单例，容器内一个 Bean 只创建一个实例 | 与容器相同 |
| prototype | 原型，每次获取都创建新实例 | 由使用者负责销毁 |
| request | 每个 HTTP 请求一个实例（Web 应用） | 请求结束销毁 |
| session | 每个 HTTP Session 一个实例（Web 应用） | Session 结束销毁 |
| application | 每个 ServletContext 一个实例（Web 应用） | 应用关闭销毁 |

### 4.2 指定作用域

```java
@Component
@Scope("prototype")  // 或 @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ShoppingCart {
    // 会话范围：每个用户请求一个新购物车对象
}

@RestController
public class CartController {
    @Autowired
    private ShoppingCart cart;  // 每次都注入新实例（不推荐字段注入）
}
```

### 4.3 单例注入原型的问题

单例 Bean 依赖原型 Bean 时，注入的永远是同一个实例——因为注入发生在容器初始化时。

```java
@Component
@Scope("prototype")
public class ProtoBean { ... }

@Component
public class SingletonBean {
    @Autowired
    private ProtoBean protoBean;  // 陷阱：拿到的是初始化时的那个实例
    // 解决方案：ObjectProvider<ProtoBean> 每次调用 getObject()
    @Autowired
    private ObjectProvider<ProtoBean> protoProvider;
}
```

```java
// 正确做法：使用 ObjectProvider / ApplicationContext.getBean() / @Lookup
@Autowired
private ObjectProvider<ProtoBean> protoProvider;

public ProtoBean getProtoBean() {
    return protoProvider.getObject();  // 每次获取一个新实例
}
```

---

## 五、循环依赖

### 5.1 什么是循环依赖

```java
@Service
public class A {
    @Autowired
    private B b;   // A 依赖 B
}

@Service
public class B {
    @Autowired
    private A a;   // B 依赖 A → 循环了！
}
```

### 5.2 Spring 如何解决

Spring 通过**三级缓存**解决 singleton 作用域的循环依赖：

```java
// DefaultSingletonBeanRegistry 中的三级缓存
Map<String, Object> singletonObjects;           // 一级：成品 Bean（已完成初始化）
Map<String, Object> earlySingletonObjects;      // 二级：半成品（提前暴露引用）
Map<String, ObjectFactory<?>> singletonFactories; // 三级：对象工厂（生成早期引用）
```

创建过程：A 实例化 → 放入三级缓存 → 填充属性发现需要 B → 创建 B → B 填充属性发现需要 A → 从三级缓存拿到 A 的早期引用 → B 完成 → A 拿到 B 完成。

### 5.3 无法解决的循环依赖

| 场景 | 为什么解决不了 |
|------|---------------|
| 构造器注入循环依赖 | 实例化阶段就卡住，没有"半成品"可用 |
| prototype 作用域 | 不缓存，无法提前暴露 |
| @Async 代理 Bean | 代理生成时机在初始化后，提前引用拿到的不是代理 |

> **面试回答模板**：Spring 通过三级缓存解决 singleton + 字段/Setter 注入的循环依赖；构造器注入循环、prototype 循环无法解决，应从设计上避免。

---

## 六、面试要点

| 问题 | 一句话答案 |
|------|-----------|
| IoC 和 DI 什么关系？ | IoC 是思想（控制反转），DI 是实现方式（依赖注入） |
| 为什么推荐构造器注入？ | 不可变、易测试、可发现缺失依赖 |
| Bean 生命周期分几个阶段 | 实例化 → 属性填充 → Aware → 初始化 → 使用 → 销毁 |
| @PostConstruct 和 initMethod 顺序 | @PostConstruct 先执行，initMethod 最后 |
| BeanPostProcessor 有什么用？ | 每个 Bean 初始化前后回调，AOP 代理在此生成 |
| 单例 Bean 注入了原型 Bean 怎么办？ | ObjectProvider / @Lookup 每次获取新实例 |
| 循环依赖怎么解决？ | 三级缓存提前暴露早期引用（仅限 singleton + 字段注入） |

> 理解了 IoC 容器，就能理解自动配置的本质：它就是"往容器里按条件注入 Bean 的工厂"。