# cmcc-business-service + stage1-java-core + spring-ioc-mini 项目面试分析

> 项目路径：`D:\code\codeClaudeCode\demo-practicalTrainingProject`
> 覆盖：cmcc-business-service（移动业务系统）、stage1-java-core（Java 核心实训）、spring-ioc-mini（手写 Spring IoC）

---

## 📊 项目一：cmcc-business-service — 移动公司业务办理系统（MVP）

### 项目概述

Spring Boot 3.2 + MyBatis-Plus + MySQL 的移动业务系统，核心功能为**套餐管理**。

### 技术栈
| 技术 | 版本 | 用途 |
|------|------|------|
| Spring Boot | 3.2.0 | 基础框架 |
| MyBatis-Plus | 3.5.5 | ORM 框架 |
| MySQL | 8.0.33 | 数据库 |
| Spring Security Crypto | 6.2.0 | 密码加密（BCrypt） |
| Bean Validation | - | 参数校验 |
| SLF4J + Logback | 2.x | 日志 |
| Lombok | 1.18.38 | 代码简化 |

### 核心业务模块

```
cmcc-app-v1
├── 套餐管理（核心）
│   ├── ServicePackage（抽象套餐基类）→ 模板方法模式
│   ├── NetPackage（网虫套餐）→ 上网 68元/月
│   ├── TalkPackage（话唠套餐）→ 通话+短信 58元/月
│   └── SupperPackage（超级套餐）→ 通话+短信+上网 78元/月
├── 移动卡管理（MobileCard / MobileCardDO）
├── 话费充值（RechargeController / RechargeRecord）
├── 账单管理（Bill / BillItem）
├── 权益管理（Benefit / UserBenefit）
├── 积分管理（PointsRecord）
├── 流量管理（FlowController / FlowUsage）
└── 用户管理（User / UserLogin / UserRegister）
```

### 🎯 核心考点

#### 题目 1：项目中用了哪些设计模式？怎么用的？

**答案**：

| 设计模式 | 应用位置 | 说明 |
|----------|----------|------|
| **模板方法** | `ServicePackage` 抽象基类 | 定义套餐通用结构，子类实现 `showInfo()` |
| **策略模式** | `NetPackage`/`TalkPackage`/`SupperPackage` | 不同套餐实现不同计费策略 |
| **接口隔离** | `NetService`/`CallService`/`SendService` | 按能力拆分接口，套餐按需实现 |
| **分层架构** | Controller → Service → Mapper | 标准三层架构 |
| **DTO/VO 模式** | dto / vo 包 | 接口边界数据隔离 |

**代码示例**：
```java
// 抽象套餐基类 - 模板方法模式
@Data
public abstract class ServicePackage {
    protected String name;
    protected Integer price;
    public abstract void showInfo();  // 子类实现
}

// 网虫套餐 - 实现上网能力
public class NetPackage extends ServicePackage implements NetService {
    public NetPackage() {
        this.name = "网虫套餐";
        this.price = 68;
    }
    @Override
    public void net(int flowGB, MobileCard card) {
        // 流量扣费逻辑：套餐内免费，超出按 0.5元/M 扣费
        int remainingFlow = this.flow * 1024 - card.getRealFlow();
        if (remainingFlow >= 1) {
            card.setRealFlow(card.getRealFlow() + 1);  // 免费流量
        } else {
            card.setBalance(card.getBalance() - FLOW_OVER_PRICE);  // 扣费
        }
    }
}
```

**追问**：为什么用接口（NetService/CallService）而不是继承实现能力？
- 答：**接口隔离原则**。话唠套餐有通话能力无上网能力，超级套餐三种能力都有；如果用继承会强制所有子类实现不需要的方法（接口污染）

#### 题目 2：计费逻辑怎么设计的？哪些容易出 Bug？

**答案**：
```java
// 通话扣费逻辑（TalkPackage.call）
for (int i = 0; i < time; i++) {
    int remainingTalkTime = this.talkTime - card.getRealTalkTime();
    if (remainingTalkTime >= 1) {
        card.setRealTalkTime(card.getRealTalkTime() + 1);  // 套餐时长内
    } else {
        if (card.getBalance() >= CALL_OVER_PRICE) {
            card.setRealTalkTime(card.getRealTalkTime() + 1);
            card.setBalance(card.getBalance() - CALL_OVER_PRICE);  // 超时扣费
        } else {
            throw new BusinessException(ErrorCode.BALANCE_NOT_ENOUGH, "余额不足");
        }
    }
}
```

**潜在 Bug（面试官爱问）**：
1. **大循环性能**：`for (int i = 0; i < time; i++)` 每 1 分钟循环一次，若通话 1000 分钟则循环 1000 次 —— **可优化为数学计算**
2. **浮点精度**：`FLOW_OVER_PRICE = 0.5` 是 double，金额计算可能精度丢失 —— **应该用 BigDecimal 或分（整数）**
3. **并发问题**：`card.setBalance()` 非原子操作，多线程同时通话会超扣 —— **需要加锁或乐观锁**

**追问**：如果用户同时打电话和上网，余额扣费会冲突吗？
- 答：会！`card` 对象是共享的，`setBalance` 和 `setRealTalkTime` 都不是线程安全的。面试加分点：加 `synchronized` 锁或使用 Redis 分布式锁

#### 题目 3：密码加密怎么做的？为什么用 BCrypt？

**答案**：
```java
// PasswordUtil.java
// 使用 Spring Security Crypto 的 BCryptPasswordEncoder
BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
String encoded = encoder.encode(rawPassword);  // 生成带盐的哈希
boolean matched = encoder.matches(rawPassword, encoded);  // 校验
```

**为什么用 BCrypt 而不是 MD5**：
| 对比 | MD5 | BCrypt |
|------|-----|--------|
| 盐 | 需要额外存储 | 内置随机盐 |
| 抗彩虹表 | 弱 | 强 |
| 计算速度 | 快（易暴力破解） | 慢（故意） |
| 输出格式 | 32位固定 | 60位含盐 |

---

## 📊 项目二：stage1-java-core — Java 核心实训项目

### 项目概述

分模块的 Java 核心训练项目，覆盖 OOP、集合、并发、设计模式、项目实战。

### 模块结构

```
stage1-java-core
├── module1-oop-basics        → 面向对象基础
│   ├── encapsulation/        → 封装（Account/User）
│   ├── inheritance/          → 继承（Employee/Manager/Developer）
│   ├── polymorphism/         → 多态（AlipayStrategy/Animal）
│   ├── abstractinterface/    → 抽象类接口（Shape/Circle/Drawable）
│   └── exception/            → 异常（InsufficientBalanceException）
├── module2-collections-stream → 集合与流
│   ├── list/                 → ArrayListDemo
│   ├── map/                  → HashMapDemo
│   ├── set/                  → SetDemo
│   ├── generic/              → GenericDemo
│   └── stream/               → StreamDemo
├── module3-advanced-concurrent → 进阶与并发
│   ├── thread/               → 线程（ThreadDemo）
│   ├── threadpool/           → 线程池（ThreadPoolDemo）
│   ├── sync/                 → 同步（SynchronizedDemo）
│   ├── proxy/                → 动态代理（JdkProxyDemo）
│   └── reflection/           → 反射（ReflectionDemo）
├── module4-design-patterns   → 设计模式
│   ├── factory/              → 工厂模式
│   ├── singleton/            → 单例模式
│   ├── strategy/             → 策略模式
│   ├── proxy/                → 代理模式
│   └── ioc/                  → 手写 IoC（MiniIocContainer）
├── project1-billing-system   → 计费系统实战（策略+工厂）
└── project2-mini-spring      → 手写迷你 Spring（IoC/DI）
```

### 🎯 核心考点

#### 题目 4：手写线程池（ThreadPoolDemo）和 JDK 线程池区别？

**答案**：JDK `ThreadPoolExecutor` 核心是**七大参数 + BlockingQueue + 拒绝策略**，手写的通常只做简化版。面试重点考察：
- corePoolSize / maximumPoolSize / keepAliveTime
- 任务队列（LinkedBlockingQueue / ArrayBlockingQueue）
- 拒绝策略（4 种）

#### 题目 5：JDK 动态代理（JdkProxyDemo）原理？

**答案**：
```java
// 核心：Proxy.newProxyInstance + InvocationHandler
Proxy.newProxyInstance(
    classLoader,          // 被代理类的类加载器
    interfaces,           // 被代理类实现的接口
    (proxy, method, args) -> {
        // 前置增强（如日志、鉴权）
        System.out.println("前置增强");
        Object result = method.invoke(target, args);  // 反射调用目标
        // 后置增强
        return result;
    }
);
```

**局限**：JDK 动态代理**只能代理接口**，不能代理类（需要 CGLIB）。

#### 题目 6：计费系统（project1-billing-system）怎么设计的？

**答案**：
```
BillingPackage（套餐基类）
├── MonthlyPackage（月套餐）
├── VoicePackage（语音套餐）
└── DataPackage（流量套餐）
        ↓ 工厂模式
PackageFactory（套餐工厂）→ 根据类型创建套餐
        ↓ 策略模式
BillingStrategy（计费策略）
├── VoiceBillingStrategy（语音计费）
└── DataBillingStrategy（流量计费）
```

**设计模式综合应用**：
- **工厂模式**：解耦套餐创建逻辑
- **策略模式**：不同套餐的计费算法可替换
- **模板方法**：计费流程固定，细节由子类实现

---

## 📊 项目三：spring-ioc-mini — 手写 Spring IoC 容器

### 项目概述

从零手写一个迷你 Spring IoC 容器，包含**注解扫描、依赖注入、AOP 代理、单例管理**。

### 核心源码结构

```
spring-ioc-mini
├── annotation/          → 自定义注解
│   ├── @Component       → 组件标记
│   ├── @Autowired       → 依赖注入
│   ├── @Scope           → 作用域（singleton/prototype）
│   └── @Aspect          → 切面标记
├── context/             → 容器核心
│   ├── MiniSpringContext → 容器入口（单例）
│   ├── BeanFactory      → Bean 工厂（单例缓存池 ConcurrentHashMap）
│   ├── BeanDefinition   → Bean 定义（名称/Class/作用域）
│   ├── BeanScanner      → 包扫描器
│   └── AopProxyFactory  → JDK 动态代理工厂
├── aop/                 → AOP 支持
│   ├── BaseAspect       → 切面基类
│   ├── AspectHandler    → 切面处理器
│   └── Log              → 日志切面
└── service/             → 示例业务
    ├── UserService / UserServiceImpl
    └── OrderService / OrderServiceImpl
```

### 🎯 核心考点

#### 题目 7：手写 IoC 容器的核心流程是什么？

**答案**：
```
① BeanScanner.scan(basePackage) → 扫描包下所有 @Component 类 → 生成 BeanDefinition
② BeanFactory.register() → 将 BeanDefinition 注册到 ConcurrentHashMap
③ 循环 BeanDefinition → 通过反射实例化 Bean
④ 处理 @Autowired → 反射注入依赖（getField + setAccessible + set）
⑤ 检查 @Scope → singleton 则存入缓存池，prototype 每次新建
⑥ 检查 @Aspect → AopProxyFactory 生成 JDK 动态代理
⑦ 返回容器
```

**代码要点**：
```java
// BeanFactory 单例缓存池
private static final Map<String, Object> singletonObjects = new ConcurrentHashMap<>();

// BeanScanner 扫描 @Component
if (clazz.isAnnotationPresent(Component.class)) {
    beanDefinitionMap.put(beanName, new BeanDefinition(beanName, clazz));
}

// 依赖注入（@Autowired）
Field[] fields = clazz.getDeclaredFields();
for (Field field : fields) {
    if (field.isAnnotationPresent(Autowired.class)) {
        field.setAccessible(true);  // 私有字段可访问
        field.set(bean, getBean(field.getType()));  // 注入依赖
    }
}
```

**追问**：为什么用 `ConcurrentHashMap` 而不是 HashMap？
- 答：容器可能被多线程并发访问（获取 Bean），ConcurrentHashMap 保证线程安全

#### 题目 8：AOP 是怎么实现的？

**答案**：
```java
// AopProxyFactory - JDK 动态代理
if (beanClass.isAnnotationPresent(Aspect.class)) {
    Object proxy = Proxy.newProxyInstance(
        beanClass.getClassLoader(),
        beanClass.getInterfaces(),
        new AspectHandler(baseAspect, target)
    );
    return proxy;
}
```

**AOP 增强流程**：
```
调用代理方法
    ↓
AspectHandler.invoke()
    ├── 前置增强（before）：日志、鉴权
    ├── 反射调用目标方法（method.invoke）
    ├── 后置增强（after）
    └── 异常增强（exception）
```

**追问**：JDK 动态代理和 CGLIB 有什么区别？
- 答：JDK 代理只能代理接口（本项目的局限，因为 `UserServiceImpl implements UserService`）；CGLIB 通过继承代理类，可以代理无接口的类

#### 题目 9：手写单例（MiniSpringContext）为什么用双重检查锁？

**答案**：
```java
public static MiniSpringContext getInstance(String basePackage) {
    if (INSTANCE == null) {                    // 第一重检查：避免每次加锁
        synchronized (MiniSpringContext.class) {  // 加锁
            if (INSTANCE == null) {            // 第二重检查：防止重复创建
                INSTANCE = new MiniSpringContext(basePackage);
            }
        }
    }
    return INSTANCE;
}
```

**为什么双重检查**：
1. **性能**：第一重检查避免每次都进入同步块
2. **正确性**：第二重检查防止两个线程同时穿过第一重检查
3. **改进**：INSTANCE 应加 `volatile` 防止指令重排（对象创建未完成就被读取）

---

## 🎯 面试组合拳建议

### 这三个项目的定位

| 项目 | 推荐定位 | 面试展示点 |
|------|----------|------------|
| **spring-ioc-mini** | 亮点项目（最能展示功底） | 手写框架源码、反射、动态代理、设计模式 |
| **cmcc-business-service** | 业务项目（展示业务理解） | 套餐计费、设计模式实战、MyBatis-Plus |
| **stage1-java-core** | 基础训练（展示基础扎实） | OOP、集合、并发、设计模式刻意练习 |

### 面试话术示例

> **介绍 spring-ioc-mini**：
> 我手写了一个迷你 Spring IoC 容器，实现了注解扫描（@Component/@Autowired/@Scope/@Aspect）、依赖注入、单例管理和 JDK 动态代理 AOP。核心流程是：包扫描生成 BeanDefinition → 反射实例化 → Autowired 注入 → 切面代理。这个项目让我真正理解了 Spring 的底层原理。

> **介绍 cmcc 业务系统**：
> 这是一个中国移动业务办理系统，我负责套餐模块。用抽象类 + 接口实现模板方法和策略模式，支持网虫/话唠/超级三种套餐的差异化计费。计费逻辑实现了套餐内免费、超出按单价扣费的规则，使用 Spring Security 的 BCrypt 做密码加密。

### 快速追问链

```
面试官（spring-ioc-mini）：Spring 的 Bean 生命周期是怎样的？
    ↓
你：实例化 → 属性填充 → 初始化 → 使用 → 销毁（结合自己写的容器对比）
    ↓
面试官：@Autowired 和构造器注入区别？
    ↓
你：构造器注入更安全（final + 无循环依赖），字段注入不推荐
    ↓
面试官：你写的 AOP 有什么局限？
    ↓
你：只能代理接口（JDK 动态代理），Spring 实际用 CGLIB 兜底
```

---

## 📎 配套文件

- 自动生成的 mall 项目题：`interview-project-qa/*.md`
- 自动出题工具：`interview-note/interview-tools/question-generator/question_generator.py`

---

> 💡 **最高价值建议**：`spring-ioc-mini` 是你**最稀缺的差异化项目**——手写框架源码在面试中非常加分！务必把 Bean 容器流程、反射注入、动态代理三块吃透。