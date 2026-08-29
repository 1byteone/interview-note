# 分布式微云商城 — 面试 QA 精编版（93 题）

> 基于 mall-micro-cloud 微服务商城实训项目 + 8 章课程文档 + 论微服务架构论文 + 面试场景示例 + java 笔记，结合真实项目源码生成。
> 每题五要素：**参考话术** → **面试官意图** → **深挖追问** → **项目结合** → **常见扣分项**。

---

## GOAL（专业交付目标）

| 维度 | 目标 |
|------|------|
| **产出** | 6 域分章、约 90 题精编 QA，可背诵 + 可深挖 |
| **内容** | 参考话术 + 面试官意图 + 深挖追问 + 项目结合 + 扣分项，五要素齐全 |
| **深度** | L1 基础题与 L4 架构题同卷，基础题也带项目视角 |
| **结合** | 全部落到 mall-micro-cloud 真实代码（类名 + 关键方法），杜绝空谈 |
| **标准** | 应届生校招场景——先让面试官听懂，再往深里走 |

---

## 第一章　自我介绍与项目总览（Q1–Q6）

---

### Q1：做一下简单的自我介绍（2 分钟版）

**参考话术：** 面试官您好，我是江西农业大学软件工程 2027 届本科应届生。在校期间作为核心参与 4 人分布式微云商城实训项目，团队 2 后端 + 1 前端 + 1 测试，全程 Git 分支管理 + CodeReview 规范协同开发。

项目基于 SpringBoot3 + Maven 多聚合微服务架构，落地全套分布式生产方案：MySQL 索引与分表优化、Redis 二级缓存 + 布隆过滤器做缓存防护、Redisson 分布式锁解决秒杀超卖、RocketMQ 异步解耦 + 延时关单、Elastic-Job 定时预热秒杀数据、Nacos 注册配置中心、Sentinel 限流熔断降级、Seata AT 保证跨服务事务一致性。

RAG 智能检索服务由我独立全链路开发，包含 Python AI 服务对接、自然语言提取、商品推荐召回、多轮会话管控。开发中我牵头统一数据库、接口、缓存开发规范，针对库存一致性、大模型幻觉等难题对比业界方案选型落地。项目通过万级 QPS 并发压测。

**面试官意图：** 考察表达能力、项目认知深度和技术栈广度。2 分钟要覆盖"谁、做了什么、怎么做的、效果如何"。
**深挖追问：** 追问"你具体负责哪些模块"→ 答商品、订单、库存、支付四大核心模块 + RAG 智能检索全链路。
**项目结合：** 项目 12 个微服务（user/product/order/pay/cart/seckill/es/aisearch/scheduler/oss/gateway/test），详见项目 `mall-services/` 目录。
**常见扣分项：** 超时、说不清楚自己负责什么、堆砌技术名词没有落地方案。

---

### Q2：你这个商城项目是跟着老师敲的还是自己想的

**参考话术：** 是跟着老师敲的实训项目。但我在项目中独立负责了商品、订单、库存、支付四大高并发核心模块，以及 RAG 智能检索服务的全链路开发。在实现过程中，我对每个模块都做了深入学习和独立思考——比如秒杀超卖防护方案是我对比了悲观锁、乐观锁和 Redis 预扣三套方案后选型落地的；RAG 检索的三层幻觉拦截机制也是我根据业界最佳实践独立设计的。虽然项目来源于实训，但我在技术选型和方案设计上都有自己的理解和独立判断。

**面试官意图：** 判断是"无脑抄"还是"有思考地实现"。诚实 + 主动亮出独立工作量。
**深挖追问：** 追问"那你觉得这个项目最大的改进空间在哪"→ 可答分布式事务仍以最终一致性为主，精细化限流、K8s 弹性扩容、全链路监控还有优化空间。
**项目结合：** 论文原文提到"系统迭代周期由 30 天压缩至 7 天，可用性 99.9%，支撑万级 QPS"。
**常见扣分项：** 直接说"跟着敲的"然后沉默，没有任何延伸。

---

### Q3：你项目里用到了哪些核心技术栈

**参考话术：** 分层来看：接入层用 Spring Cloud Gateway 做统一路由和鉴权；业务层 12 个微服务通过 OpenFeign + LoadBalancer 做服务间 RPC，Nacos 做注册中心和配置中心；中间件层用 Redis + Redisson 做缓存和分布式锁，RocketMQ（Spring Cloud Stream Binder）做异步解耦，Elasticsearch 做全文检索，Elastic-Job 做分布式定时任务；数据层用 MySQL + MyBatis-Plus 做持久化，Seata AT 做跨服务分布式事务；AI 检索层通过 OpenFeign 对接 Python RAG 服务，实现商品智能推荐。

**面试官意图：** 考察技术栈全景认知。能否按分层清晰梳理，而不是零散堆砌。
**深挖追问：** 追问"为什么用 Spring Cloud Stream 而不是直接用 RocketMQTemplate"→ Stream 统一了 MQ 抽象层，切换 Kafka 不用改业务代码；Binder 模式将生产者消费者声明式绑定到 channel，代码更干净。
**项目结合：** `mall-gateway/`（Gateway）、`mall-api/`（Feign + DTO）、`mall-common/`（Redisson/BloomFilter/JwtUtil）、`mall-services/`（12 个业务服务）。
**常见扣分项：** 只列技术名词不讲"为什么用"和"怎么用"。

---

### Q4：你们项目的分层架构是怎么设计的，为什么要这么分层

**参考话术：** 严格遵循三层分层规范：Controller 层接收请求并校验参数、Service 层处理核心业务逻辑、Mapper 层封装数据库操作，下层为上层提供调用接口，上层只能调用下一层，禁止跨层访问。好处是代码解耦、职责单一——比如后续要把 MySQL 换成其他数据库，只需改 Mapper 层，Service 和 Controller 完全不用动。项目还抽取了 `mall-common` 公共模块放分布式工具类（JwtUtil、RedissonClient、BloomFilter、全局异常处理器），`mall-api` 模块统一存放所有 Feign 接口和 DTO 实体，各业务服务完全独立拆分。

**面试官意图：** 考察架构基本功。分层是面试的"开胃菜"，答不清后面很难加分。
**深挖追问：** 追问"为什么不把 DTO 放在 Service 里"→ DTO 属于 API 边界，放在 `mall-api` 让所有消费方共享，避免循环依赖；实体类在 Service 层内部，防止暴露数据库字段给前端。
**项目结合：** Controller → Service → Mapper 三层 + `mall-common`（公共组件）+ `mall-api`（Feign 接口 + DTO）+ 各 `mall-services/*` 独立微服务。
**常见扣分项：** 分不清 Controller 和 Service 的职责边界，把业务逻辑写在 Controller 里。

---

### Q5：你们团队是怎么分工的

**参考话术：** 4 人分工：我独立负责商品、订单、库存、支付四大高并发核心模块，以及 RAG 智能检索微服务的全链路开发；另一位后端负责用户、购物车、OSS 文件服务与公共工具模块；前端负责业务页面及 AI 检索交互页面；测试负责接口压测、Bug 回归验证。我们采用 Git 功能分支 + CodeReview 机制，开发前前置输出接口文档，统一阿里编码规范，所有新增 SQL 必须经 SQL 评审才能上线。

**面试官意图：** 考察团队协作能力和你在团队中的角色。应届生项目要体现"不只是参与者，还有主导性"。
**深挖追问：** 追问"CodeReview 具体怎么做的"→ 每人在自己的 feature 分支开发完后提交 PR，至少一人 Review 通过后才能合并到 dev 分支；Review 重点看索引是否合理、是否有跨层调用、参数校验是否完整。
**项目结合：** 项目 `mall-services/` 下 12 个服务 + `mall-api/` 共享接口层，两个后端按业务域独立拆分。
**常见扣分项：** 说不出具体分工，只说"大家一起做的"。

---

### Q6：你在项目中为什么选择微服务架构

**参考话术：** 我们最初用单体架构快速搭原型，但很快遇到三个痛点：一是业务模块耦合严重，修改一个功能要牵动整个系统；二是团队协作效率低，4 人团队在同一个代码库上工作，合并冲突频繁；三是流量波动大，无法按需扩展。最终选择 Spring Cloud Alibaba 微服务架构，落地后收益明显：业务完全解耦、各服务独立部署独立测试、不同服务可按需选型（订单用 MySQL、搜索用 ES）、4 人按服务分工并行开发。拆分粒度全程以高内聚低耦合为标尺，按 DDD 业务域拆分出商品、订单、用户、支付、搜索等独立微服务，避免过度拆分导致运维成本暴涨。

**面试官意图：** 考察架构决策能力——为什么选微服务而不是单体？不是"微服务一定好"，而是"在什么场景下微服务比单体更合适"。
**深挖追问：** 追问"微服务带来了什么新问题"→ 分布式事务（用 Seata AT 解决）、服务间通信一致性（Feign + MQ 混合方案）、配置管理复杂度（Nacos 配置中心统一管理）、运维成本上升（Docker + 监控体系）。
**项目结合：** 论文原文："系统迭代周期由 30 天压缩至 7 天，可用性 99.9%，支撑万级 QPS"。
**常见扣分项：** 只说"微服务好"不讲"单体哪里不行"；说不出微服务带来的新挑战。

---

## 第二章　Java 基础与 Spring 核心（Q7–Q28）

---

### Q7：String、StringBuilder、StringBuffer 有什么区别

**参考话术：** String 是不可变对象，每次 `+` 拼接都会创建新对象，适合少量固定字符串；StringBuilder 是可变的，非线程安全，性能最好，单线程字符串拼接首选；StringBuffer 是可变的，方法加了 `synchronized`，线程安全但性能略低，多线程场景才需要。底层原理上，String 内部是 `final char[]`（JDK 8）/`final byte[]`（JDK 9+），每次 `+` 实际上创建了新的 StringBuilder 再 `toString()`。

**面试官意图：** 经典基础题，考察是否理解不可变性和线程安全的取舍。
**深挖追问：** 追问"JDK 9 对 String 做了什么优化"→ `compact strings`：内部 `char[]` 改为 `byte[]` + `coder` 标识符，Latin1 字符只占 1 字节，内存节省约 50%。
**项目结合：** 订单服务 `MessageSendServiceImpl.java` 中拼接 MQ 消息体时大量使用 `StringBuilder` 拼接 header；商品服务 DTO 序列化时用 `JsonUtils`（底层是 fastjson2）代替手动拼接。
**常见扣分项：** 只说"String 不可变、StringBuilder 快"，不解释为什么不可变、底层结构是什么。

---

### Q8：你了解 HashMap 吗

**参考话术：** JDK 8 之前是数组 + 链表，元素数量 < 8 时用链表，> 8 且数组长度 ≥ 64 时转红黑树。JDK 8+ 的完整结构是数组 + 链表 + 红黑树。核心流程：先对 key 的 hashCode 做扰动运算定位桶下标，桶内无冲突直接放入；有冲突时链表尾插（JDK 8）/头插（JDK 7）；链表长度 > 8 且数组 ≥ 64 时树化为红黑树；扩容时采用高低位拆分，避免 JDK 7 的死链问题。线程不安全，并发写入可能导致数据丢失或死循环（JDK 7 头插法）。

**面试官意图：** Java 集合核心考点。期望答出"底层结构 + 树化条件 + 扩容机制 + 线程安全"。
**深挖追问：** 追问"为什么链表长度阈值是 8"→泊松分布下，单个桶内 8 个冲突的概率约 0.00000006，几乎不可能达到；红黑树查找 O(log n) 比链表 O(n) 快，但维护成本高，阈值 8 是时间和空间的折中。
**项目结合：** 项目中 DTO 转换、查询结果分组都依赖 HashMap；`CamelCastUtils.jsonToObject()` 内部使用 Map 做 JSON 到 DTO 的字段映射。
**常见扣分项：** 不知道树化条件是"长度 > 8 **且**数组 ≥ 64"；说 JDK 8 是"头插法"。

---

### Q9：你知道 JDK 8 的哪些新特性吗

**参考话术：** 五个核心新特性：① Lambda 表达式——函数式编程，简化匿名内部类，如 `list.forEach(item -> System.out.println(item))`；② Stream API——集合的流式操作，支持 filter/map/reduce/collect 链式调用，支持并行流 `parallelStream()`；③ Optional——优雅处理 null，`Optional.ofNullable(obj).orElse(defaultValue)`；④ 接口默认方法——`default` 方法让接口可以有默认实现，避免改动大量实现类；⑤ 新的日期时间 API——`LocalDate`/`LocalDateTime`/`ZonedDateTime` 替代 `Date` 和 `SimpleDateFormat` 的线程安全问题。

**面试官意图：** 考察是否持续学习、是否理解新特性的设计动机。
**深挖追问：** 追问"Lambda 的底层实现是什么"→Lambda 通过 `invokedynamic` 指令实现，在首次调用时由 `LambdaMetafactory` 动态生成一个实现函数式接口的类，后续调用走生成的字节码，比匿名内部类更高效。
**项目结合：** `AiSearchSeriveImpl.java` 中推荐结果列表转换用了 Stream：`productList.stream().map(item -> CamelCastUtils.jsonToObject(item, ProductDTO.class)).collect(Collectors.toList())`；`OrderServiceImpl.java` 中从订单项中提取购物车 ID：`orderItemsList.stream().map(OrderItems::getCartId).collect(Collectors.toList())`。
**常见扣分项：** 只说"Lambda 简化代码"不讲底层实现；不知道 Stream 有并行流。

---

### Q10：Spring Boot 的自动装配原理是什么

**参考话�：** 核心注解是 `@SpringBootApplication`，它组合了 `@EnableAutoConfiguration`。`@EnableAutoConfiguration` 通过 `@Import(AutoConfigurationImportSelector.class)` 在启动时扫描 `META-INF/spring.factories`（Spring Boot 2.x）或 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`（Spring Boot 3.x）文件，加载所有自动配置类。每个自动配置类用 `@ConditionalOnClass`、`@ConditionalOnBean`、`@ConditionalOnProperty` 等条件注解做判断——只有当前 classpath 存在对应依赖、配置文件设置了相关属性时，自动配置类才会生效，向容器注入对应的 Bean。

**面试官意图：** Spring Boot 最高频面试题之一。期望答出"注解入口 → 扫描机制 → 条件装配"三层逻辑。
**深挖追问：** 追问"Spring Boot 3.x 和 2.x 在自动装配上有什么区别"→ 扫描文件从 `spring.factories` 改为 `AutoConfiguration.imports`（纯文本文件，每行一个类名），不再用 key-value 格式；新增 `@AutoConfiguration` 注解替代 `@Configuration`。
**项目结合：** `mall-services/` 下每个微服务的启动类都用 `@SpringBootApplication(scanBasePackages = "itcast.cloud.mall")`，通过自动装配引入 Redisson、Sentinel、Nacos 等组件的默认配置；`application-dev.yml` 中通过 `spring.config.import: nacos:` 拉取 Nacos 远程配置，覆盖默认值。
**常见扣分项：** 只说"Spring Boot 自动配置帮我们省事"，说不出 `@Import` 和 `spring.factories` 的机制；不知道 3.x 改成了 `.imports` 文件。

---

### Q11：@Resource 和 @Autowired 的区别

**参考话术：** `@Autowired` 是 Spring 注解，默认按类型（byType）匹配，如果容器中有多个同类型 Bean 会报错，需要配合 `@Qualifier` 指定名称；`@Resource` 是 JSR-250 标准注解（javax 包），默认按名称（byName）匹配，找不到再按类型匹配。`@Autowired` 支持 `required=false` 允许注入为 null；`@Resource` 不支持。Spring 官方推荐构造器注入，避免 `@Autowired` 的循环依赖问题。

**面试官意图：** 基础注解辨析题，考察对 Spring IoC 容器的理解。
**深挖追问：** 追问"@Autowired 的注入流程是什么"→ 先按类型查找容器中所有匹配的 Bean，只有一个直接注入；多个则按字段名作为 Bean 名称匹配；还找不到则报 `NoUniqueBeanDefinitionException`，需 `@Qualifier` 指定。
**项目结合：** `OrderServiceImpl.java` 中用 `@Autowired` 注入 5 个依赖（`IOrderInfoService`、`IOrderItemsService`、`SkuInfoFeignClient`、`CartFeignClient`、`IMessageSendService`）；`AiSearchSeriveImpl.java` 用构造器注入 `AiPythonFeignClient`（`@RequiredArgsConstructor`）。
**常见扣分项：** 混淆"byType"和"byName"的匹配顺序；不知道 `@Resource` 属于 JSR 标准而非 Spring 专有。

---

### Q12：你项目中用到了哪些注解呢

**参考话术：** 按层级分：Controller 层用 `@RestController`、`@RequestMapping`、`@GetMapping`、`@PathVariable`、`@RequestParam`；Service 层用 `@Service`、`@Autowired`；Mapper 层用 `@Mapper`、`@Select`、`@Insert`；实体类用 `@Data`（Lombok）、`@TableName`、`@TableId`、`@TableLogic`（MyBatis-Plus）；全局用 `@GlobalTransactional`（Seata）、`@SentinelResource`（Sentinel）、`@Value`（配置注入）、`@Slf4j`（日志）。

**面试官意图：** 考察实战中对注解的使用广度，不是死记硬背。
**深挖追问：** 追问"@TableLogic 怎么工作的"→ 在实体类 `deleted` 字段上加 `@TableLogic`，MyBatis-Plus 会自动把 `DELETE` 语句转为 `UPDATE ... SET deleted=1`，查询时自动拼接 `WHERE deleted=0`，实现逻辑删除。
**项目结合：** `sku_info` 表有 `deleted` 字段（1=已删除, 0=正常）+ `idx_delete` 索引；`user_info` 表同理；`@TableLogic` 在实体类中标注。
**常见扣分项：** 只说"用了 Spring 注解"，说不出具体注解名和用途；不知道 `@TableLogic` 是逻辑删除。

---

### Q13：说几个 Java 自带的注解，元注解是什么

**参考话术：** Java 自带注解：`@Override`（标记方法重写）、`@Deprecated`（标记过时）、`@SuppressWarnings`（抑制编译警告）、`@FunctionalInterface`（标记函数式接口）。元注解是"注解的注解"，用来修饰其他注解的定义：`@Target`（指定注解可用在哪些位置——类/方法/字段）、`@Retention`（指定注解的生命周期——SOURCE/CLASS/RUNTIME）、`@Documented`（包含在 Javadoc 中）、`@Inherited`（子类可继承父类的注解）。自定义注解时必须用元注解声明约束。

**面试官意图：** 考察注解的底层机制理解。元注解是理解 Spring 注解（如 `@Component`、`@Bean`）工作原理的基础。
**深挖追问：** 追问"`@Retention(RUNTIME)` 为什么 Spring 能扫描到"→ 因为 Spring 启动时通过反射（`Class.getDeclaredAnnotations()`）读取运行时注解，SOURCE 和 CLASS 级别的注解在运行期已被 JVM 丢弃，无法被反射读取。
**项目结合：** 项目自定义注解如 `@LoginLog`（AOP 切面记录登录日志），其定义必然使用了 `@Target(ElementType.METHOD)` + `@Retention(RUNTIME)` + `@Around` 切面绑定。
**常见扣分项：** 分不清"自带注解"和"元注解"的概念；不知道 `@Retention` 的三个级别。

---

### Q14：Spring 的两大核心是什么

**参考话术：** 两大核心：① IoC（控制反转）——Spring 作为容器统一创建和管理对象，对象之间通过 DI（依赖注入）建立关系，开发者不需要手动 `new`，而是从容器中 `getBean()`；② AOP（面向切面编程）——将业务拆分为核心业务和横切业务（日志、权限、事务），横切逻辑抽取到切面类中集中管理，通过动态代理在运行时织入到目标方法前后，不修改原有代码就能增强功能。

**面试官意图：** Spring 面试必考题。期望答出"IoC 解决了什么 + AOP 解决了什么 + 各自的实现方式"。
**深挖追问：** 追问"AOP 的底层实现是什么"→ JDK 动态代理（基于接口）和 CGLIB（基于子类继承）。Spring 默认对有接口的类用 JDK 代理，无接口的用 CGLIB；Spring Boot 2.x+ 默认全部用 CGLIB。
**项目结合：** `LoginLogAspect.java`（AOP 切面）拦截登录操作自动记录登录日志，不侵入 Controller 逻辑；IoC 容器管理全部 Service、FeignClient、DataSource 等 Bean。
**常见扣分项：** 只说"Spring 有 IoC 和 AOP"，不解释"控制反转到底反转了什么"；分不清 JDK 代理和 CGLIB。

---

### Q15：Java 虚拟机是什么，常见版本有哪些

**参考话术：** JVM（Java Virtual Machine）是运行 Java 字节码的虚拟机，负责把 `.class` 文件翻译成机器码执行。它提供了跨平台能力（"Write Once, Run Anywhere"）、自动内存管理（GC 垃圾回收）、安全沙箱。常见版本：HotSpot（Oracle/OpenJDK 默认，最主流）、OpenJ9（IBM/Eclipse，启动快、内存占用低，适合容器化）、GraalVM（Oracle，支持 AOT 编译为原生镜像，启动速度提升 10 倍+）。我们项目目前用的是 HotSpot JDK 8。

**面试官意图：** JVM 入门考察。结合当前项目回答而非死背。
**深挖追问：** 追问"HotSpot 和 GraalVM 的区别"→ HotSpot 是解释 + JIT 混合编译；GraalVM 支持 AOT（Ahead-of-Time）编译，把 Java 代码直接编译为本地机器码，启动时间从秒级降到毫秒级，内存占用降低 3-5 倍，但失去了动态性（反射受限）。
**项目结合：** 项目 `pom.xml` 配置 `maven.compiler.source=8`，运行环境为 JDK 8；Docker 镜像使用 OpenJDK 8 镜像。
**常见扣分项：** 说不清 JVM 和 JRE/JDK 的区别；不知道 GraalVM。

---

### Q16：JVM 内存结构说一下，哪些是线程共享，哪些是线程私有

**参考话术：** 五大运行时数据区：线程私有——① 程序计数器（PC，当前线程字节码行号）、② 虚拟机栈（栈帧：局部变量表、操作数栈、动态链接、返回地址）、③ 本地方法栈（native 方法）；线程共享——④ 堆（对象实例，GC 主战场，分新生代 Eden/S0/S1 + 老年代）、⑤ 方法区/元空间（JDK 8 从 JVM 内存移到本地内存，存放类元数据、常量池、静态变量）。另外还有直接内存（NIO 堆外内存）。

**面试官意图：** JVM 必考题。期望"五大区域 + 共享/私有 + 堆分代 + JDK 8 变化"全部覆盖。
**深挖追问：** 追问"哪些区域会 OOM"→ 堆 OOM（对象太多）、元空间 OOM（动态代理类太多）、栈 OOM（递归过深 StackOverflowError）、直接内存 OOM（NIO 堆外内存泄漏）。
**项目结合：** 秒杀预热时大量商品数据缓存到 Redis 和本地缓存，需关注堆内存占用；动态代理大量 FeignClient 接口会产生代理类，占用元空间。
**常见扣分项：** 不知道 JDK 8 方法区改成了元空间；分不清虚拟机栈和本地方法栈。

---

### Q17：什么是序列化，为什么要进行序列化

**参考话术：** 序列化是把 Java 对象转换为字节流或 JSON 字符串的过程，反序列化是逆过程。两个场景必须序列化：① 持久化——把数据保存到磁盘或数据库；② 网络传输——分布式系统中跨服务调用时，对象需要序列化为字节流在网络上传输（如 Feign 传 JSON、Redis 存储对象、MQ 消息体）。

**面试官意图：** 考察分布式基础认知。序列化是 RPC 框架和缓存系统的底层基础。
**深挖追问：** 追问"项目中用了什么序列化方式"→ DTO 通过 JSON（fastjson2/Gson）序列化；Redis 缓存对象通过 `RedisTemplate`（默认 JDK 序列化或 JSON 序列化）；MQ 消息体通过 Stream 内置序列化。
**项目结合：** `OrderServiceImpl.java` 中 `JsonUtils.jsonToList()` 将前端传来的 JSON 字符串反序列化为 `OrderCreateDTO` 对象；商品 DTO 在 Feign 传输时自动序列化为 JSON。
**常见扣分项：** 只说"序列化就是把对象变字符串"，不知道 JSON 只是序列化的一种方式。

---

### Q18：异常处理有哪几种方式

**参考话术：** 三种：① 抛出异常（`throw new XxxException()`）——当前方法不处理，向上层调用者传递；② 捕获异常（`try-catch-finally`）——当前方法内处理，`finally` 块无论是否异常都会执行；③ 声明异常（方法签名加 `throws XxxException`）——告诉调用方"这个方法可能抛出什么异常，你来处理"。项目中统一使用全局异常处理器 `@RestControllerAdvice` + `@ExceptionHandler` 捕获自定义 `BusinessException`，返回统一错误码。

**面试官意图：** Java 异常基础 + 项目实践的结合考察。
**深挖追问：** 追问"try-catch 里加 return，finally 执行吗"→ 执行。`finally` 优先级最高，无论是否 return、是否抛异常都会执行。如果 `finally` 里也有 return，会覆盖 `try` 里的返回值。
**项目结合：** `mall-common` 中全局异常处理器捕获 `BusinessException`（自定义业务异常），返回统一 `Result` 格式；各 Service 方法 throw `BusinessException` 而非 try-catch，由全局处理器兜底。
**常见扣分项：** 不知道 `finally` 一定会执行；不清楚 checked 和 unchecked 异常的区别。

---

### Q19：方法重载（Overload）与方法重写（Override）的核心区别

**参考话术：** 重载发生在同一个类中——方法名相同、参数列表不同（参数个数/类型/顺序不同），与返回值无关；重写发生在子类与父类之间——方法名、参数、返回值完全一致，子类方法权限不能比父类更严格、不能抛出更宽泛的异常。重载是"同一个功能，多种参数形式"；重写是"子类修改/增强父类方法逻辑"。

**面试官意图：** OOP 基础辨析题。面试官想知道你是否理解多态的两种表现形式。
**深挖追问：** 追问"重载是编译时多态还是运行时多态"→ 编译时多态（静态分派）：编译器根据参数类型在编译期就确定了调用哪个重载方法；重写是运行时多态（动态分派）：JVM 在运行时根据实际对象类型决定调用哪个方法。
**项目结合：** `OrderServiceImpl.java` 的 `create()` 方法和 `createPayCreateDTO()` 方法——后者是 private 辅助方法，参数列表不同，构成重载关系的变体。
**常见扣分项：** 混淆重载和重写；说"重载必须返回值不同"。

---

### Q20：finally 和 final 的区别

**参考话术：** `final` 是修饰符——修饰类则不能被继承、修饰方法则不能被重写、修饰变量则值不能被修改（引用不能变，内容可变）；`finally` 是异常处理中的代码块——无论是否发生异常、无论是否 return，其中的代码都保证执行，通常用于释放资源（关闭流、断开连接）。

**面试官意图：** 关键字辨析，看似简单但容易混淆。
**深挖追问：** 追问"final 修饰的 List 能不能 add"→ 可以。`final List<String> list = new ArrayList<>()`，`final` 限制的是引用不能重新指向另一个对象，但 `list.add("a")` 修改的是对象内部状态，不受 `final` 限制。
**项目结合：** `JwtUtil.java` 中 `secretKey` 通过 `@Value` 注入后不再变更，相当于运行期常量；`finally` 块在 Redisson 分布式锁释放时使用——`if (lock.isHeldByCurrentThread()) { lock.unlock(); }`。
**常见扣分项：** 不知道 `final` 修饰引用类型时"引用不可变但内容可变"。

---

### Q21：Set 集合和 List 集合区别是什么，ArrayList 和 LinkedList 的区别

**参考话术：** List 有序、有下标、元素可重复；Set 无序（LinkedHashSet 保持插入顺序）、无下标、元素不可重复（需重写 `hashCode()` 和 `equals()`）。ArrayList 底层是动态数组，查询快（随机访问 O(1)）、中间增删慢（需要移动元素）；LinkedList 底层是双向链表，首尾增删快（O(1)）、随机查询慢（需遍历 O(n)）。

**面试官意图：** 集合框架基础辨析。
**深挖追问：** 追问"ArrayList 扩容机制"→ 初始容量 10，扩容为原来的 1.5 倍（`oldCapacity + (oldCapacity >> 1)`），通过 `Arrays.copyOf()` 复制到新数组。
**项目结合：** 订单列表返回 `List<OrderDTO>`，保证有序、可重复；秒杀商品 ID 去重使用 `Set<Long>`；`OrderServiceImpl.java` 中订单项列表用 `ArrayList` 存储。
**常见扣分项：** 不知道 ArrayList 扩容因子是 1.5；说"Set 绝对无序"（忘了 LinkedHashSet/TreeSet）。

---

### Q22：什么是数据库连接池，为什么要用

**参考话术：** 连接池是一种缓存和管理数据库连接的容器——应用启动时预先创建一批连接，使用时"借用"，用完"归还"复用，而非每次新建和关闭。原因：① 频繁创建和关闭连接消耗数据库资源、增加系统开销；② 复用已有连接减少网络交互和磁盘 I/O，提升访问速度；③ 通过池化管理（最大连接数、最小空闲数、连接超时）控制并发上限，防止高并发下连接数爆炸。我们项目用 HikariCP（Spring Boot 默认），配置了最大 20 连接。

**面试官意图：** 考察对数据库性能优化的基础认知。
**深挖追问：** 追问"HikariCP 为什么比 DBCP 快"→ HikariCP 字节码优化（用 Javassist 生成代理类减少反射开销）、使用 ConcurrentBag 无锁数据结构、连接的有效性检测更高效。
**项目结合：** `application-dev.yml` 通过 Nacos 配置 `nacos:redis.yml` 和 MySQL 连接池参数；Spring Boot 默认使用 HikariCP，无需额外引入依赖。
**常见扣分项：** 只说"复用连接"，说不出具体池化参数和为什么快。

---

### Q23：Statement 和 PreparedStatement 的区别

**参考话术：** Statement 的 SQL 参数通过字符串拼接，容易被 SQL 注入攻击；PreparedStatement 使用预编译的 `#{}` 占位符，参数在 SQL 编译后才绑定，天然防注入。PreparedStatement 还支持参数化查询，同一条 SQL 不同参数只需编译一次，性能优于 Statement。MyBatis 中 `#{}` 对应 PreparedStatement（自动加单引号），`${}` 对应 Statement（直接拼接，仅用于表名/字段名等动态 SQL）。

**面试官意图：** SQL 注入防御基础 + MyBatis 参数绑定机制。
**深挖追问：** 追问"MyBatis 里 `${}` 什么时候用"→ 动态表名、动态排序字段等无法用占位符的场景。必须在代码层做白名单校验，防止注入。
**项目结合：** 全程使用 MyBatis-Plus，所有查询参数用 `#{}`；商品列表的动态排序（按价格/销量排序）通过 Java 代码校验字段名后用 `${}` 拼接。
**常见扣分项：** 不知道 `#{}` 和 `${}` 的区别；说"MyBatis 完全防注入"（`${}` 不防）。

---

### Q24：三范式是什么

**参考话术：** 三范式是关系型数据库设计规范，目的是减少数据冗余和更新异常。第一范式（1NF）：列不可再分，保证原子性——比如"地址"字段应拆分为省、市、区、详细地址；第二范式（2NF）：非主键列必须完全依赖整个主键（消除部分函数依赖）——联合主键场景下，不能只依赖主键的一部分；第三范式（3NF）：非主键列必须直接依赖主键，不能通过其他非主键列间接依赖（消除传递依赖）——比如"订单表"不应包含"商品名称"，应通过外键关联商品表。

**面试官意图：** 数据库设计基础。考察是否理解反范式的取舍。
**深挖追问：** 追问"什么时候要反范式"→ 读多写少的查询场景，用空间换时间——比如订单项表冗余商品名称和单价，避免每次查询都 JOIN 商品表。
**项目结合：** `order_items` 表冗余了 `sku_name` 和 `price` 字段（商品快照），是典型的反范式设计——下单时保存商品信息，后续商品改价不影响历史订单。
**常见扣分项：** 只能说出"第一范式是不能有重复列"，说不清二三范式的核心区别。

---

### Q25：事务的四大特性（ACID）是什么

**参考话术：** ① 原子性（Atomicity）：事务中的操作要么全部成功，要么全部回滚；② 一致性（Consistency）：事务执行前后数据保持一致状态（如转账前后总额不变）；③ 隔离性（Isolation）：并发事务之间互不干扰，各自有完整的数据空间；④ 持久性（Durability）：事务成功提交后，结果永久保存，即使系统崩溃也不丢失。MySQL InnoDB 默认隔离级别是可重复读（Repeatable Read），通过 MVCC + 间隙锁实现。

**面试官意图：** 数据库事务必考题。期望答出 ACID + InnoDB 默认隔离级别。
**深挖追问：** 追问"可重复读和读已提交的区别"→ 可重复读：同一事务内多次读取同一数据结果一致（MVCC 快照读）；读已提交：每次读都能看到其他事务已提交的最新数据。InnoDB 的可-repeatable read 通过间隙锁（Gap Lock）还解决了部分幻读问题。
**项目结合：** Seata AT 模式通过 undo_log 实现分布式事务的原子性；`OrderServiceImpl` 的 `@GlobalTransactional` 确保"Feign 扣库存 + 创建订单 + 发 MQ"要么全部成功，要么全局回滚。
**常见扣分项：** 只背 ACID 四个词，不解释每个特性解决什么问题；不知道 InnoDB 默认可重复读。

---

### Q26：什么是服务雪崩，如何处理

**参考话术：** 下游服务超时或宕机，导致上游服务大量请求阻塞、线程积压，最终耗尽服务器资源导致整个系统连锁崩溃，就是服务雪崩。解决方案四层：① 超时处理——设置接口最大等待时间，超时释放资源；② 线程隔离（舱壁模式）——每个服务分配独立线程池，故障隔离在特定资源池内；③ 熔断降级——异常率/慢调用比例超阈值自动熔断，返回预设兜底结果；④ 流量控制（限流）——限制接口 QPS，防止突发流量压垮服务。

**面试官意图：** 微服务高可用核心概念。结合项目中的 Sentinel 实践来答。
**深挖追问：** 追问"Sentinel 熔断的三种策略"→ 慢调用比例、异常比例、异常数。项目中库存扣减接口配置了慢调用比例熔断，下游超时自动返回降级结果。
**项目结合：** `mall-product-service` 的 `SkuInfoController.java`：`@SentinelResource(value = "/skuInfo/deductStock", fallback = "deductStockFallback")`——对库存扣减接口配置了 Sentinel 熔断降级，Feign 客户端通过 `FallbackFactory` 实现服务降级兜底。
**常见扣分项：** 只说"熔断降级"，说不出具体四种方案；不知道 Sentinel 和 Hystrix 的区别。

---

### Q27：什么是同源策略和跨域

**参考话术：** 同源策略是浏览器的安全机制——当两个 URL 的协议、域名、端口完全一致时才视为同源，同源请求可以自由交互；当三者中有任何一个不同就判定为跨域，浏览器会拦截跨域响应。解决方案：在网关层配置 CORS，通过 `Access-Control-Allow-Origin`、`Access-Control-Allow-Methods`、`Access-Control-Allow-Headers` 等响应头告知浏览器放行规则。

**面试官意图：** 前后端分离必备知识。
**深挖追问：** 追问"OPTIONS 预检请求什么时候触发"→ 非简单请求（自定义 Header、PUT/DELETE 方法、非 JSON 内容类型）时，浏览器先发 OPTIONS 请求确认服务端是否允许跨域。
**项目结合：** `mall-gateway/src/main/resources/application.yml` 全局 CORS 配置：`allowedOriginPatterns: "*"`、`allowCredentials: true`、`allowedHeaders: "*"`、`exposedHeaders: Authorization`——所有跨域在 Gateway 统一处理，业务服务无需关心。
**常见扣分项：** 不知道 OPTIONS 预检请求；认为"跨域是后端的问题"（实际是浏览器行为）。

---

### Q28：同是分布式系统，为什么不用 Session ID 做身份认证而用 Token

**参考话术：** Session ID 保存在服务器端，分布式环境下请求可能落到不同实例上，Session 不共享，导致认证失效。Token（JWT）是无状态的——服务端不存储会话，只验证 Token 签名和有效期。每个请求携带完整 Token，任何实例都能独立校验，天然支持水平扩展。项目中用 JWT + Redis 双重方案：JWT 保证签名不可篡改，Redis 存储 Token 实现在线踢出和多设备登录控制。

**面试官意图：** 分布式认证方案对比。考察"为什么 Token 比 Session 更适合微服务"。
**深挖追问：** 追问"那 Token 放在哪里返回给前端"→ 放在响应头（`HttpServletResponse` Header）而非响应体。DTO 返回业务数据，Token 放 Header，业务和认证分离；前端拦截器统一从 Header 提取 Token，下次请求自动携带。
**项目结合：** `JwtUtil.createTokenAndStore()` 生成 JWT 存入 Redis（key = `user:token:{userId}`）；`AuthGatewayFilterFactory` 从 `Authorization` Header 提取 Token，校验后注入 `X-User-Id` Header 透传给下游服务；每次请求通过后还会生成新 Token 放入响应 Header，实现无缝续期。
**常见扣分项：** 只说"Token 无状态"，不知道 JWT + Redis 双重方案的设计思路；说不清 Header 传递的具体机制。

---

## 第三章　数据库与 MySQL（Q29–Q45）

---

### Q29：你用过哪些数据库

**参考话术：** 项目中使用了四种数据库：MySQL 做核心业务持久化（商品、订单、用户、支付等关系型数据）；Redis 做缓存和分布式锁（秒杀库存预热、Session/Token 存储、布隆过滤器）；MongoDB 做购物车存储（文档型数据库天然适配购物车的多变规格）；Elasticsearch 做商品全文检索（倒排索引支持毫秒级模糊搜索）。

**面试官意图：** 考察数据存储选型的广度和"为什么选这个"的判断力。
**深挖追问：** 追问"为什么购物车不用 Redis"→ Redis 适合做缓存但不适合存大量结构化数据（内存消耗大）；购物车商品规格多变（规格、数量、选中状态），MongoDB 文档型无需固定表结构，直接存 JSON 格式的购物车文档。
**项目结合：** `shop_goods.sql`（MySQL 商品表）、`shop_order.sql`（MySQL 订单表）、`shop_user.sql`（MySQL 用户表）；Redis 缓存秒杀库存和 Token；MongoDB 存储购物车（由 `mall-cart-service` 管理）；Elasticsearch（`mall-es-service`）存储商品索引。
**常见扣分项：** 只说"用过 MySQL 和 Redis"，不知道 MongoDB 和 ES 的适用场景。

---

### Q30：在你的项目中是怎么进行 SQL 调优的

**参考话术：** 从三个维度调优：① 索引优化——高频查询字段建联合索引，遵循最左前缀原则；所有新增 SQL 必须 `EXPLAIN` 验证执行计划，确保命中索引；禁止超过 3 个表的 JOIN 查询。② 分页优化——完全使用主键游标分页（传上一页最大 ID），禁止大偏移量 `LIMIT 10000, 10`。③ 批量操作——商品批量导入时关闭自动提交、每 1000 条批量提交、导入前临时关闭非必要索引，导入后重建。

**面试官意图：** SQL 调优实战能力考察。不是背理论，而是"在项目中具体做了什么"。
**深挖追问：** 追问"EXPLAIN 看哪些字段"→ `type`（访问类型，system > const > eq_ref > ref > range > index > ALL）、`key`（实际使用的索引）、`rows`（扫描行数）、`Extra`（Using index 覆盖索引 / Using filesort 需要优化）。
**项目结合：** `docs/sql/EXPLAIN执行计划详解.sql` 和 `docs/sql/索引测试与非法SQL拦截器测试.sql`——项目中有完整的索引验证和 EXPLAIN 测试 SQL；`shop_user` 表设计了 4 个联合索引用于不同查询组合。
**常见扣分项：** 只说"加索引"，不知道联合索引和最左前缀；不知道分页优化。

---

### Q31：数据库哪些字段要建立索引

**参考话术：** 优先在以下场景建索引：① WHERE 条件高频过滤字段；② JOIN 关联字段；③ ORDER BY / GROUP BY 排序字段；④ 高选择性字段（区分度高的列）。主键数据库自动建索引。不要给所有字段建索引——索引占存储空间、降低写入速度（每次 INSERT/UPDATE 都要维护索引 B+ 树），过多索引反而拖慢性能。

**面试官意图：** 索引设计原则考察。
**深挖追问：** 追问"怎么判断一个索引该不该建"→ 看查询频率 × 选择性：高频查询 + 高选择性（如 username、order_id）优先建；低频查询或低选择性（如 status 字段只有 0/1 两个值）一般不建。
**项目结合：** `sku_info` 表的联合索引 `idx_cat1_cat2_cat3_brand`——覆盖商品列表页高频的分类 + 品牌筛选查询；`user_info` 表的 `idx_user_info_username` 唯一索引——覆盖登录时的 username 查询。
**常见扣分项：** 说"索引越多越好"；不知道低选择性字段不适合建索引。

---

### Q32：为什么不能把所有字段都建立索引

**参考话术：** 三个原因：① 索引占磁盘空间，字段越多索引越大；② 每次 INSERT、UPDATE、DELETE 操作都要维护索引的 B+ 树结构，索引越多写入性能越差；③ 查询优化器可能选错索引——索引过多时优化器选错索引的概率增大，反而导致查询变慢。生产环境建议单表索引不超过 5-6 个，定期清理无效冗余索引。

**面试官意图：** 索引反面考察——知道"什么时候不该建"比"什么时候该建"更体现经验。
**深挖追问：** 追问"怎么清理无效索引"→ `sys.schema_unused_indexes` 视图查看未使用索引，`pt-duplicate-key-checker` 工具检测冗余索引。
**项目结合：** `shop_user` 表存在 3 个相似联合索引（`idx_user_info_uen`、`idx_user_info_uet`、`idx_user_info_uen1`），其中 `uen1` 与 `uen` 重复——正是项目中需要清理的冗余索引。
**常见扣分项：** 只说"索引影响写入性能"，说不出具体影响多大。

---

### Q33：联合索引怎么设计，索引失效的常见场景

**参考话术：** 联合索引遵循最左前缀原则——`idx(a, b, c)` 只有在查询条件从最左列开始连续使用时才生效（WHERE a AND b AND c 完全命中；WHERE a AND b 命中 a+b；WHERE b AND c 不命中）。常见失效场景：① 索引字段做运算（`WHERE age + 1 = 18`）；② 前缀模糊查询（`WHERE name LIKE '%张'`）；③ OR 两边字段未全部加索引；④ 隐式类型转换（varchar 字段传 int）；⑤ 跳过联合索引最左侧前缀字段。

**面试官意图：** 索引是 MySQL 面试第一大考点。期望答出"设计原则 + 失效场景 + 解决方案"。
**深挖追问：** 追问"如果只传品牌不传分类，索引用不上怎么办"→ 两套方案：新增单列品牌索引；或前端默认携带当前选中分类。项目中选了"前端默认携带 + 单列索引辅助"的策略。
**项目结合：** 联合索引 `idx_cat1_cat2_cat3_brand`——用户选分类时从一级分类开始逐级筛选，完全匹配最左前缀；品牌筛选作为最右列，必须前面的分类列先传入才能命中。
**常见扣分项：** 不知道最左前缀原则；说"LIKE '张%' 不会失效"（前缀匹配才不失效）。

---

### Q34：四大事务隔离级别分别是什么

**参考话术：** ① 读未提交（Read Uncommitted）——可以读到其他事务未提交的数据（脏读）；② 读已提交（Read Committed）——只能读到已提交的数据（解决脏读，但有不可重复读）；③ 可重复读（Repeatable Read）——同一事务内多次读取结果一致（解决不可重复读，InnoDB 默认）；④ 串行化（Serializable）——完全串行执行（解决幻读，但性能最差）。InnoDB 在可重复读级别通过 MVCC + 间隙锁（Gap Lock）解决了大部分幻读场景。

**面试官意图：** 事务隔离是数据库核心考点。期望答出"四种级别 + 解决什么问题 + InnoDB 默认值"。
**深挖追问：** 追问"MVCC 是什么原理"→ 多版本并发控制：每行数据维护隐藏的事务 ID 和回滚指针，读操作根据 ReadView（快照）判断可见性，无需加锁即可实现一致性读。
**项目结合：** 秒杀场景下，多个事务同时读取库存，可重复读隔离级别下不加锁的读操作会出现幻读——多个事务读到相同库存值，后续都去扣减导致超卖。解决方案：Redisson 分布式锁 + Redis 原子扣减，绕过 MySQL 层的并发问题。
**常见扣分项：** 混淆四种级别解决什么问题；不知道 InnoDB 默认可重复读。

---

### Q35：SPU 和 SKU 是什么，为什么拆分

**参考话术：** SPU（Standard Product Unit，标准产品单元）是商品型号维度——比如"iPhone 15"是一个 SPU；SKU（Stock Keeping Unit，库存量单位）是具体售卖规格——比如"iPhone 15 / 256G / 黑色"是一个 SKU。拆分原因：① SPU 存储图文描述、规格属性等共享信息，SKU 存储价格、库存、图片等规格级别信息，避免大量冗余文本；② 更新 SPU 描述不需要锁 SKU 行，并发性能更好；③ 搜索、推荐以 SPU 维度聚合，库存管理以 SKU 维度操作。

**面试官意图：** 电商数据库设计核心概念。
**深挖追问：** 追问"你们的分类体系怎么设计的"→ 三级分类树：一级分类（手机/电脑）、二级分类（智能手机/笔记本）、三级分类（5G 手机/游戏本），通过 `parent_id` 自关联实现树结构，`category` 表索引 `idx_parent_id` 加速子节点查询。
**项目结合：** `spu_info` 表（id, spu_name, description）+ `sku_info` 表（id, spu_id, price, sku_name, num, brand_id, category_id, status, deleted）——SPU 和 SKU 通过 `spu_id` 关联。
**常见扣分项：** 分不清 SPU 和 SKU 的概念；不知道拆分的性能优势。

---

### Q36：为什么不用自动编号而用雪花算法生成分布式 ID

**参考话术：** 自动编号（AUTO_INCREMENT）是针对单表的，分布式系统中分库分表会导致多表主键冲突。雪花算法（Snowflake）在分布式环境下能保证全局唯一——由 1 位符号位 + 41 位时间戳 + 10 位机器 ID + 12 位序列号组成，64 位 long 型，每毫秒每台机器可生成 4096 个 ID，性能远超 UUID（UUID 是 128 位字符串，索引效率低）。

**面试官意图：** 分布式 ID 选型是高频考点。期望答出"雪花算法组成 + 为什么不用 UUID + 时钟回拨处理"。
**深挖追问：** 追问"雪花算法怎么解决时钟回拨问题"→ 每次生成 ID 校验当前时间和上次生成时间，回拨 < 50ms 直接等待追上；回拨 > 50ms 抛异常告警或切换备用 ID 生成器。
**项目结合：** `OrderServiceImpl.java` 中 `String orderId = IdWorker.getIdStr()`——使用 MyBatis-Plus 内置的雪花算法 `IdWorker` 生成全局唯一订单号；`order_info` 表主键 `order_id varchar(64)` 存储雪花 ID。
**常见扣分项：** 说不出雪花算法的 64 位组成；不知道 UUID 的索引效率问题。

---

### Q37：雪花编号由什么组成，怎么存储

**参考话术：** 64 位 long 型，分为 4 段：① 1 位符号位（固定 0）；② 41 位时间戳（毫秒级，可用约 69 年）；③ 10 位机器 ID（支持 1024 个节点）；④ 12 位序列号（每毫秒 4096 个 ID）。存储方面：MySQL 用 `BIGINT` 类型存储效率最高（8 字节），但项目中 `order_id` 用了 `VARCHAR(64)` 存储字符串形式的雪花 ID，查询时需注意类型匹配。

**面试官意图：** 雪花算法细节考察。
**深挖追问：** 追问"为什么项目用 VARCHAR 而不是 BIGINT"→ 原因可能是与支付宝等第三方支付接口交互时需要字符串格式的订单号，直接存字符串避免了类型转换。
**项目结合：** `order_info` 表 `order_id varchar(64)` + `pay_log` 表 `order_id varchar(64)`——与支付宝接口通信需要字符串格式。
**常见扣分项：** 说不出各段的位数；不知道 41 位时间戳能用 69 年。

---

### Q38：你们项目里库存是怎么读到缓存的，秒杀库存数据怎么预热

**参考话术：** 用 Elastic-Job 分布式定时任务每天凌晨执行——`LoadSeckillProductTask` 实现 `SimpleJob` 接口，execute 方法依次调用：① `listTodaySeckillGoods()` 获取今日秒杀商品列表；② `generateHtml()` 生成静态页面上传；③ `loadStockCache()` 将每个商品的库存写入 Redis（key = `seckill:stock:{activityId}:{skuId}`，TTL = 24h），同时将 key 加入布隆过滤器。

**面试官意图：** 考察秒杀预热方案的完整理解。不是"手动塞 Redis"，而是定时任务自动化。
**深挖追问：** 追问"任务堆积怎么办"→ 利用 Elastic-Job 的分片功能——按商品 ID 哈希分片，多台节点并行处理，将 30 小时的工作量分摊到 N 台机器，每台只需处理 1/N。
**项目结合：** `mall-scheduler-service/src/main/java/.../job/LoadSeckillProductTask.java`——`execute(ShardingContext)` 中调用 Feign 加载秒杀商品、生成 HTML、预热库存到 Redis + BloomFilter。
**常见扣分项：** 说"手动在 Redis 里 SET 库存"；不知道 ElasticJob 的分片机制。

---

### Q39：为什么系统不用 @Scheduled 或 Quartz 而用 ElasticJob

**参考话术：** `@Scheduled` 是单机定时任务——多实例部署会重复执行，不适用于分布式系统；Quartz 虽然支持集群但依赖数据库锁，性能和功能有限；ElasticJob 是分布式调度框架，支持任务分片（大任务拆分到多节点并行处理）、故障转移（节点宕机自动迁移到其他节点）、运维可视化（任务执行监控），完全适配分布式微服务架构。

**面试官意图：** 定时任务选型对比，考察"为什么选这个"的决策逻辑。
**深挖追问：** 追问"ElasticJob 怎么实现分片"→ 注册中心（Zookeeper/Nacos）协调分片项分配，每个节点获取到自己的分片 ID 后，只处理对应分片的数据。比如分片 0 处理商品 ID % 3 == 0 的数据，分片 1 处理 % 3 == 1 的数据。
**项目结合：** `mall-scheduler-service` 是纯调度服务（`exclude = DataSourceAutoConfiguration.class`，无数据库），通过 Nacos 配置 `elasticjob.yml` 和 `task.yml`，包含 `LoadSeckillProductTask` 和 `PayCheckTask` 两个 Job。
**常见扣分项：** 分不清 `@Scheduled` 和分布式调度框架的区别；不知道 ElasticJob 的分片原理。

---

### Q40：delete 和 truncate 的区别

**参考话术：** 三个核心区别：① `DELETE` 可以用 WHERE 条件删除部分数据，`TRUNCATE` 不能加条件，只能清空整张表；② `DELETE` 是 DML 操作（可以在事务中回滚），`TRUNCATE` 是 DDL 操作（立即生效，不可回滚）；③ `TRUNCATE` 效率比 `DELETE` 高（直接释放数据页，不逐行删除），但会重置 AUTO_INCREMENT 计数器。

**面试官意图：** SQL 基础辨析题。
**深挖追问：** 追问"什么时候用 TRUNCATE"→ 临时表清空、测试数据重置、大批量数据清理（比 DELETE 快得多）。
**项目结合：** 项目中使用 MyBatis-Plus 的逻辑删除（`@TableLogic`），实际不执行 `DELETE` 也不执行 `TRUNCATE`，而是 `UPDATE ... SET deleted=1`，数据保留可恢复。
**常见扣分项：** 不知道 `TRUNCATE` 不可回滚；不知道 `TRUNCATE` 会重置自增 ID。

---

### Q41：数据库的关联查询有哪几种

**参考话术：** 五种：① 内连接（INNER JOIN）——只返回两表匹配的记录；② 左外连接（LEFT JOIN）——返回左表全部 + 右表匹配的记录；③ 右外连接（RIGHT JOIN）——返回右表全部 + 左表匹配的记录；④ 全外连接（FULL OUTER JOIN）——返回两表全部（MySQL 不支持，可用 UNION 模拟）；⑤ 交叉连接（CROSS JOIN）——返回笛卡尔积。项目中避免多表 JOIN，优先使用单表查询 + 冗余字段（空间换时间）。

**面试官意图：** SQL 基础辨析 + 项目实践结合。
**深挖追问：** 追问"为什么项目不推荐多表 JOIN"→ 分布式系统可能分库分表，跨库 JOIN 不可用；多表 JOIN 在大数据量下性能极差；单表查询 + 冗余字段更适合微服务独立数据源的架构。
**项目结合：** `order_items` 表冗余 `sku_name` 和 `price` 字段——下单时保存商品快照，查询订单详情时直接读单表，不需要 JOIN 商品表。
**常见扣分项：** 分不清内连接和外连接；不知道 MySQL 不支持全外连接。

---

### Q42：为什么使用单表查询而不用多表连接查询

**参考话术：** 两个核心原因：① 性能——多表 JOIN 在大数据量下性能极差，需要做笛卡尔积再过滤；单表查询利用索引，响应速度快。② 分布式适配——微服务架构下每个服务独立数据源（甚至分库分表），跨服务/跨库 JOIN 根本不可用。项目中通过冗余字段（如订单项冗余商品名称和单价）实现"空间换时间"，避免 JOIN 的同时保证查询效率。

**面试官意图：** 微服务架构下数据查询的典型设计取舍。
**深挖追问：** 追问"冗余字段有什么缺点"→ 数据一致性问题——如果商品改名或调价，历史订单里的冗余数据不会自动更新。解决方案是下单时保存快照，历史订单保持原样不更新。
**项目结合：** `order_items` 表的 `sku_name` 和 `price` 是下单时的快照数据；`sku_info` 表的 `brand_name` 和 `category_name` 也是冗余字段。
**常见扣分项：** 不知道分库分表下 JOIN 不可用；不知道冗余字段的一致性问题。

---

### Q43：如何保证订单 ID 不重复

**参考话术：** 使用 MyBatis-Plus 的 `IdWorker.getIdStr()` 生成雪花分布式 ID——由时间戳 + 机器 ID + 序列号三部分组成，同一毫秒内同一台机器可生成 4096 个唯一 ID，全局唯一性由雪花算法本身保证。额外防护：订单号作为支付流水的唯一索引（`ux_pay_log UNIQUE on (order_id)`），数据库层面兜底防止极端情况下的重复。

**面试官意图：** 分布式 ID 实战 + 多重防护意识。
**深挖追问：** 追问"如果雪花算法的机器 ID 冲突怎么办"→ 项目通过 Nacos 或环境变量为每个实例分配唯一 workerId；部署时确保同一机房的机器 ID 不重复。
**项目结合：** `OrderServiceImpl.java` 第 118 行：`String orderId = IdWorker.getIdStr()`；`order_info` 表主键 `order_id varchar(64)`；`pay_log` 表唯一索引 `ux_pay_log`。
**常见扣分项：** 只说"雪花算法"，不知道 IdWorker 是 MyBatis-Plus 提供的；不知道数据库唯一索引兜底。

---

### Q44：库存——3 瓶矿泉水 10 元，金额、数量、单价要怎么设计

**参考话术：** 数据库存储时，`order_items` 表设计：`price`（单价，double/decimal）、`quantity`（数量，int）、`amount`（金额 = 单价 × 数量，double/decimal）。下单时计算 `amount = price * quantity`，总金额 `totalAmount = SUM(amount)`。金额建议用 `decimal` 类型避免浮点精度问题（`double` 在金额计算中会出现 0.1 + 0.2 ≠ 0.3 的精度丢失）。

**面试官意图：** 电商基础数据建模。考察"表结构怎么落地到字段级别"。
**深挖追问：** 追问"为什么不能用 float 存金额"→ float/double 是 IEEE 754 浮点数，二进制表示十进制小数会丢失精度，金融场景必须用 `decimal(10,2)` 或 `BigDecimal`。
**项目结合：** `order_items` 表字段：`price double`、`quantity int`、`amount double`——金额用 double 存储（实际生产应改为 decimal）；`OrderServiceImpl.java` 中 `item.setAmount(item.getPrice() * item.getQuantity())` 计算单行金额。
**常见扣分项：** 不知道 double 存金额有精度问题；说不清 amount 字段的计算逻辑。

---

### Q45：为什么把商品详情页做成静态页面

**参考话术：** 两个核心好处：① 减轻服务器压力——商品详情页属于高访问、低变化的页面，做成静态 HTML 直接从 CDN/OSS 返回，不走后端应用和数据库，响应速度从秒级降到毫秒级；② 有利于 SEO 搜索引擎优化——搜索引擎优先收录静态页面。`LoadSeckillProductTask` 定时任务每天生成秒杀商品的静态页上传到 OSS。

**面试官意图：** 高并发场景下的经典优化方案。
**深挖追问：** 追问"静态页什么时候更新"→ 定时任务每天凌晨重新生成；商品信息变更时触发增量更新（通过 MQ 消息异步更新）。
**项目结合：** `LoadSeckillProductTask.execute()` 中调用 `seckilProductFeignClient.generateHtml()`——定时为秒杀商品生成静态页面上传 OSS，详情页流量不进应用服务。
**常见扣分项：** 不知道静态化和 CDN 的关系；不知道什么时候需要更新静态页。

---

## 第四章　中间件与微服务（Q46–Q70）

---

### Q46：你接触过微服务吗，介绍一下

**参考话术：** 我们的分布式微云商城基于 Spring Cloud Alibaba 微服务架构，拆分为 12 个独立微服务：用户服务、商品服务、订单服务、支付服务、购物车服务、秒杀服务、ES 搜索服务、AI 检索服务、定时调度服务、OSS 文件服务、网关服务、测试服务。服务注册与发现用 Nacos，网关用 Spring Cloud Gateway 统一路由鉴权，服务间通信用 OpenFeign（同步）+ RocketMQ（异步），分布式事务用 Seata AT，熔断降级用 Sentinel，定时任务用 Elastic-Job。

**面试官意图：** 微服务全景认知考察——不只是"用过"，而是"架构怎么搭的、怎么交互的"。
**深挖追问：** 追问"你为什么选 Spring Cloud Alibaba 而不是 Dubbo"→ Alibaba 生态更完整（Nacos 同时做注册中心 + 配置中心，Sentinel 替代 Hystrix，Seata 替代 TCC），社区活跃度高，与 Spring Boot 集成更自然。
**项目结合：** 12 个微服务在 `mall-services/` 目录下；`mall-gateway/` 做统一入口；`mall-common/` 做公共组件；`mall-api/` 做 Feign 接口 + DTO 共享。
**常见扣分项：** 只说"用过 Nacos"说不出整体架构；说不清同步和异步通信的区别。

---

### Q47：Nacos 数据怎么持久化到 MySQL

**参考话术：** 两步：① 初始化 MySQL——下载 Nacos 后执行 `conf/nacos-mysql.sql`，自动建表；② 修改 `application.properties` 配置数据库连接地址。Nacos 默认用内嵌 Derby 做单机存储，切换 MySQL 后多实例可以共享同一份数据，实现注册中心和配置中心的高可用集群。

**面试官意图：** Nacos 高可用部署的前置条件考察。
**深挖追问：** 追问"Nacos 配置中心如果宕机了，服务还能启动吗"→ 能。微服务本地会缓存一份上次拉取的配置快照，Nacos 宕机不影响本地缓存读取。生产环境搭建 Nacos 三节点集群 + MySQL 共享存储。
**项目结合：** 项目中 Nacos 服务地址 `192.168.150.101:8848`，所有微服务通过 `spring.config.import: nacos:` 拉取配置（mysql.yml、redis.yml、mq.yml 等），按 Group 隔离不同业务（SEATA_GROUP、ORDER_GROUP、PAY_GROUP 等）。
**常见扣分项：** 不知道 Nacos 默认用 Derby；不知道配置中心宕机后本地缓存的作用。

---

### Q48：你用了 Redis 吗，介绍一下

**参考话术：** 项目中 Redis 承担五个角色：① 缓存——商品列表、秒杀商品数据缓存到 Redis，24h TTL 自动过期；② 分布式锁——Redisson `RLock` 保证秒杀库存扣减的串行化；③ Token 存储——JWT Token 存入 Redis，支持在线踢出和多设备控制；④ 幂等性——MQ 消息发送前用 `setIfAbsent` 做去重锁；⑤ 布隆过滤器——Redisson `RBloomFilter` 预拦截无效 ID，防止缓存穿透。持久化同时开启 RDB 快照和 AOF 日志，生产用混合持久化。

**面试官意图：** Redis 综合使用能力考察——不只"用过"，而是"用在哪些场景、为什么选 Redis"。
**深挖追问：** 追问"Redis RDB 和 AOF 的区别"→ RDB 是定时二进制快照，恢复快但可能丢几分钟数据；AOF 是追加写命令日志，数据更安全但恢复慢。混合持久化（Redis 4.0+）重写时先存 RDB 快照再追加增量命令，兼顾恢复速度和数据安全。
**项目结合：** Redisson 依赖 `redisson:4.3.1`（mall-common）+ `redisson-spring-boot-starter:3.24.3`（mall-seckill）；Redis 连接配置通过 Nacos `redis.yml` 统一管理。
**常见扣分项：** 只说"Redis 做缓存"，说不出分布式锁/幂等/布隆过滤器等用法；不知道混合持久化。

---

### Q49：你项目里用了 Redisson 锁，具体怎么用的

**参考话术：** 以秒杀扣库存为例：① 构建锁 key `lock:seckill:{activityId}:{skuId}`，同一商品只有一把锁，不同商品互不阻塞；② `redissonClient.getLock(lockKey)` 获取 RLock 对象，`tryLock(3, TimeUnit.SECONDS)`——最多等 3 秒抢锁，抢不到直接返回"系统繁忙"；③ 锁内从 Redis 读取库存 `GET seckill:stock:{activityId}:{skuId}`；④ 如果库存 > 0，`decrement` 原子扣减 1，然后发 MQ 异步同步 DB；⑤ finally 块中判断 `lock.isHeldByCurrentThread()` 后才 `unlock()`——只有当前线程持有的锁才释放，防止误删别人的锁。

**面试官意图：** Redisson 分布式锁的完整使用流程考察。不是背理论，而是"在项目中怎么落地的"。
**深挖追问：** 追问"为什么不用 Lua 脚本直接扣"→ Lua 的 `get+decrement` 是原子操作，性能更高；但选 Redisson 是为了演示分布式锁治理，且锁内还预留了风控、限购等扩展逻辑。能主动说出改进方案是大加分。
**项目结合：** `ProductServiceImpl.java`（seckill-service）：`decreaseStock()` 方法中 `RLock lock = redissonClient.getLock(lockKey); lock.tryLock(3, TimeUnit.SECONDS)`；finally 块 `if (lock.isHeldByCurrentThread()) lock.unlock()`。
**常见扣分项：** 不知道 `isHeldByCurrentThread()` 的作用；说不清 tryLock 的等待时间含义。

---

### Q50：原生 SETNX 分布式锁有哪些缺陷，Redisson 怎么解决的

**参考话术：** 原生 SETNX 三大缺陷：① 不可重入——同一线程重入时拿不到锁；② 没有自动续期——锁过期但业务没执行完，其他线程会获取锁导致并发问题；③ 锁误删——A 线程的锁过期后 B 获取锁，A 执行完删除锁时可能删掉 B 的锁。Redisson 解决方案：① Hash 结构存储线程计数实现可重入；② 看门狗（Watchdog）后台线程自动续期（默认 30s，每 10s 续一次）；③ Lua 脚本中用 UUID 校验，只有持有者才能释放。

**面试官意图：** 分布式锁深挖——不只是"怎么用"，而是"底层怎么解决边界问题"。
**深挖追问：** 追问"看门狗续期原理"→ Redisson 启动时创建一个后台线程，每隔 `lockWatchdogTimeout / 3`（默认 10 秒）检查锁是否还被持有，如果持有就自动续期到 30 秒。如果业务提前完成释放锁，看门狗检测到锁不存在就停止续期。
**项目结合：** `ProductServiceImpl.java` 中 `tryLock(3, TimeUnit.SECONDS)` 只传了 waitTime 没传 leaseTime——Redisson 启用看门狗自动续期；`restoreStock()` 方法用 `tryLock(3, 10, TimeUnit.SECONDS)` 传了 leaseTime 10 秒，看门狗不生效。
**常见扣分项：** 不知道看门狗只在不传 leaseTime 时才生效；说"Redisson 用了红锁"（RedLock 是另一个概念）。

---

### Q51：Redisson 锁 10 秒自动释放，业务没执行完怎么办

**参考话术：** 项目中秒杀库存扣减本身是 Redis 毫秒级操作（读库存 + decrement + 发 MQ），10 秒绰绰有余。如果业务更耗时，有两种方案：① 不设置 leaseTime，让 Redisson 看门狗自动续期——默认 30 秒，每 10 秒续一次，业务执行多久锁就持有多久；② 根据业务耗时合理设置 leaseTime（如 30 秒），超时后业务应做补偿处理。

**面试官意图：** 分布式锁超时的边界处理能力。
**深挖追问：** 追问"如果服务宕机了锁怎么办"→ Redisson 依赖 Redis 的 key 过期机制——服务宕机后无法续期，key 到期自动释放，不会死锁。
**项目结合：** `decreaseStock()` 用 `tryLock(3)` 不设 leaseTime（看门狗续期）；`restoreStock()` 用 `tryLock(3, 10)` 显式设 10 秒（恢复库存操作更简单，不需要续期）。
**常见扣分项：** 不知道看门狗的续期机制；不区分有无 leaseTime 的区别。

---

### Q52：缓存穿透、击穿、雪崩三类问题怎么解决

**参考话术：** ① 缓存穿透——大量非法 ID 请求直接打到 MySQL：用布隆过滤器拦截无效 ID + 缓存空值。② 缓存击穿——热点商品过期瞬间大量请求打到 DB：热点商品永不过期 + 更新加互斥锁。③ 缓存雪崩——大量 key 同时过期，DB 压力瞬间打满：过期时间随机打散 + 多级缓存兜底。

**面试官意图：** Redis 缓存三大经典问题，面试必考。
**深挖追问：** 追问"布隆过滤器有什么缺点"→ 存在假阳性（判断存在但实际不存在），不会假阴性（判断不存在就一定不存在）。通过调整 bit 数组大小和哈希函数数量可以降低假阳性率。
**项目结合：** `CacheBloomFilter.java`（mall-common）使用 Redisson `RBloomFilter`，`tryInit(expectedInsertions, falseProbability)` 初始化，`mightContain()` 判断 key 是否可能存在。秒杀预热时 `LoadSeckillProductTask` 将库存 key 加入布隆过滤器。
**常见扣分项：** 混淆穿透和击穿的概念；不知道布隆过滤器只有假阳性没有假阴性。

---

### Q53：你了解 Nacos 吗，请介绍一下

**参考话术：** Nacos 同时做注册中心和配置中心，是 Spring Cloud Alibaba 生态的核心组件。注册中心：服务启动后注册 IP:Port，消费者通过服务名获取实例列表做负载均衡（配合 LoadBalancer 使用）；心跳检测：默认 5 秒一次，15 秒未收到标记不健康，30 秒剔除。配置中心：所有服务配置集中托管在 Nacos，通过 `spring.config.import` 引入，改配置不用重启，支持动态推送。命名空间隔离（dev/test/prod），Group 管理不同业务组。

**面试官意图：** Nacos 全面认知——不只是"注册中心"，还有配置中心能力。
**深挖追问：** 追问"Nacos 配置变更怎么推送到客户端"→ Nacos 2.x 使用 gRPC 长连接推送，客户端监听配置变更后实时刷新 `@RefreshScope` 标注的 Bean；1.x 使用长轮询。
**项目结合：** 项目通过 Nacos 管理 7+ 配置文件（mysql.yml、redis.yml、mq.yml、sentinel.yml 等），按 Group 隔离业务（SEATA_GROUP、ORDER_GROUP、PAY_GROUP、SECKILL_GROUP、SCHEDULER_GROUP）；Sentinel 规则也存储在 Nacos 中，实现规则动态下发。
**常见扣分项：** 只说"Nacos 做注册中心"，不知道配置中心和心跳机制；不知道 Group 和 Namespace 的区别。

---

### Q54：OpenFeign 底层原理是什么

**参考话术：** OpenFeign 基于 JDK 动态代理实现：启动时扫描 `@FeignClient` 接口，通过 `FeignClientFactory` 为每个接口生成动态代理对象；调用接口方法时，代理拦截方法调用，将 `@GetMapping`/`@PostMapping` 等注解解析为 HTTP 请求，通过 HTTP 客户端（默认 HttpURLConnection，可配 OkHttp/Apache）发送请求到目标服务。整合 Spring Cloud LoadBalancer 后，服务名自动解析为具体实例 IP:Port，实现负载均衡。

**面试官意图：** Feign 的底层机制考察——不只是"用过"，而是"怎么实现的"。
**深挖追问：** 追问"Feign 的超时怎么配置"→ `feign.client.config.default.connectTimeout`（连接超时）和 `readTimeout`（读超时）；项目搭配 Sentinel 的超时熔断机制——下游连续超时自动返回兜底降级结果。
**项目结合：** `mall-api` 下 17 个 `@FeignClient` 接口（ProductFeignClient、SkuInfoFeignClient、LoginFeignClient 等），全部通过 `fallbackFactory` 绑定降级实现类；`HeaderInterceptor.class` 全局配置请求头透传。
**常见扣分项：** 不知道 Feign 底层是 JDK 动态代理；分不清 Feign 和 RestTemplate 的区别。

---

### Q55：当远程调用时请求头丢失怎么解决

**参考话术：** 使用 Feign 全局拦截器 `RequestInterceptor` 实现请求头透传。项目中 `HeaderInterceptor` 实现 `feign.RequestInterceptor` 接口，在 `apply()` 方法中从当前请求上下文提取 `Authorization`、`X-User-Id`、`X-Trace-Id` 等 Header，塞入 Feign 请求模板。配置白名单 `header.alloweds` 控制哪些 Header 需要透传。

**面试官意图：** 微服务间调用的上下文传递问题。
**深挖追问：** 追问"如果是 MQ 消息触发的远程调用，没有 HTTP 请求上下文怎么办"→ MQ 消息发送时通过 `MqHeaderUtil.extractHttpHeaders()` 提取当前请求 Header 存入消息体；消费端接收后绑定到伪造上下文，再发起 Feign 调用时拦截器从上下文提取 Header。项目还注入了 `X-Gateway-Secret: mall-micro-8080` 标识内部调用来源。
**项目结合：** `mall-api/.../interceptor/HeaderInterceptor.java` ——白名单默认包含 `Authorization, X-User-Id, X-User-Role, X-Trace-Id, X-Gateway-Secret`；MQ 消息体包含 `MQ_HTTP_HEADER` 字段存储上游请求头。
**常见扣分项：** 不知道 Feign RequestInterceptor 的作用；不知道 MQ 场景下 Header 的传递方式。

---

### Q56：你们网关层全局鉴权，为什么不放到每个业务服务里实现

**参考话术：** 三个原因：① 避免重复代码——每个服务都写一遍 JWT 校验逻辑是大量重复；② 降低维护成本——升级鉴权规则只需改网关一处，不用发布所有服务；③ 统一过滤——在网关层直接过滤非法请求，业务服务完全不用关心鉴权。网关通过 `AuthGatewayFilterFactory` 实现：白名单路径跳过校验，其他路径提取 Token → 三步校验（黑名单 + JWT 签名 + Redis 存在性）→ 解析 userId 注入 `X-User-Id` Header 透传下游。

**面试官意图：** 网关鉴权架构设计——考察"为什么放在网关而不是每个服务"的决策逻辑。
**深挖追问：** 追问"Token 续期怎么实现"→ 网关每次验证通过后调用 `jwtUtil.createTokenAndStore()` 生成新 Token，放入响应 Header 返回前端。前端下次请求携带新 Token，实现无缝续期，用户无感知。
**项目结合：** `AuthGatewayFilterFactory.java`：白名单匹配（`/api/v1/user/login` 等跳过校验）→ JWT 校验（`jwtUtil.validateTokenWithRedis()`）→ 解析 Claims → 注入 `X-User-Id` → 生成新 Token 放入响应 Header。
**常见扣分项：** 不知道网关鉴权的完整流程；说不清 Header 透传机制。

---

### Q57：Sentinel 三层防护是怎么落地的

**参考话术：** 三层：① 限流——核心接口配置 QPS 阈值，超出直接拒绝；② 熔断——异常率或慢调用比例达到阈值，自动熔断一段时间，后续请求返回预设兜底结果；③ 降级——Feign 客户端通过 `FallbackFactory` 实现服务降级兜底。项目中 Sentinel 规则存储在 Nacos 配置中心（`sentinel.yml`），支持动态下发和 Dashboard 可视化监控。

**面试官意图：** Sentinel 防护体系的完整落地考察。
**深挖追问：** 追问"Sentinel 和 Hystrix 的区别"→ Sentinel 支持动态规则下发（Nacos）、热点参数限流、系统自适应保护；Hystrix 已停止维护。Sentinel 通过 Sentinel Dashboard 实时监控；Hystrix 需要 Turbine 聚合。
**项目结合：** `SkuInfoController.java`：`@SentinelResource(value = "/skuInfo/deductStock", fallback = "deductStockFallback")`；`SentinelDemoController.java` 展示了 6 种 Sentinel 场景：QPS 限流、慢调用熔断、异常率熔断、热点参数限流、Feign 熔断、关联资源限流；Feign 客户端全部配置了 `fallbackFactory`。
**常见扣分项：** 分不清 blockHandler 和 fallback 的区别（blockHandler 处理 Sentinel 规则触发的异常，fallback 处理业务异常）。

---

### Q58：分布式事务是如何实现的，Seata 的 AT 模式工作原理

**参考话术：** Seata AT 模式分两个阶段：阶段一——Seata 解析业务 SQL，生成更新前的数据快照（before image）和更新后的镜像（after image），将镜像写入 `undo_log` 表，并在本地事务中提交业务 SQL + 回滚日志，同时获取全局锁确保其他事务无法修改同一行数据。阶段二——提交时异步删除 `undo_log` 并释放锁；回滚时根据 `undo_log` 生成反向补偿 SQL 恢复数据。

**面试官意图：** 分布式事务核心原理考察。期望答出"两阶段 + undo_log + 全局锁"。
**深挖追问：** 追问"Seata 的四种模式区别"→ AT（自动补偿，最简单但依赖 undo_log）、TCC（手动编写 Try/Confirm/Cancel，性能好但开发成本高）、Saga（长事务，正向+补偿）、XA（数据库原生两阶段提交，强一致但性能差）。项目用 AT 模式因为最简单、对业务代码零侵入。
**项目结合：** `OrderServiceImpl.java` 类级别标注 `@GlobalTransactional`——整个 `create()` 方法的"Feign 扣库存 + 创建订单 + 发 MQ"都在 Seata 全局事务管控下，任一步骤失败全局回滚。
**常见扣分项：** 不知道 undo_log 的作用；分不清 AT 和 TCC 的区别。

---

### Q59：为什么删除购物车不用远程调用而用 MQ

**参考话术：** 三个原因：① Seata AT 模式不支持 MongoDB（购物车存在 MongoDB 中），无法用分布式事务直接管控；② 同步调用拉长响应时间——网络抖动或购物车服务卡顿会拖慢下单主流程；③ 同步失败会导致全链路回滚——只是删购物车这个次要操作失败就回滚整个下单链路，用户体验极差。MQ 异步解耦：主流程不依赖删购物车的返回结果，MQ 有重试机制保证最终一致性，失败可人工处理。

**面试官意图：** 微服务通信方案的决策能力——为什么用 MQ 而不是 Feign。
**深挖追问：** 追问"MQ 消息丢了怎么办"→ RocketMQ 同步刷盘保证消息持久化；消费端失败自动重试（默认 16 次）；超过重试次数进入死信队列，人工处理。
**项目结合：** `OrderServiceImpl.java` 第 131 行：`messageSendService.sendDeleteCartMsg(orderId, cartIds)`——通过 `StreamBridge.send("deleteCart-out-0", message)` 发送异步消息；消费端 `mall-consumer-service` 监听 `deleteCart` topic 执行删除。
**常见扣分项：** 不知道购物车用 MongoDB；不知道 Seata AT 不支持 MongoDB。

---

### Q60：MQ 消息如何实现分布式事务

**参考话术：** RocketMQ 事务消息（半消息机制）：① 生产者先发一条半消息（对消费者不可见）；② 执行本地事务；③ 根据本地事务结果向 Broker 发送 commit 或 rollback；④ 如果 Broker 长时间没收到确认，会回查本地事务状态（检查事务是否已提交），决定提交或丢弃半消息。实现最终一致性：本地事务和消息发送在同一个事务中，保证"要么都成功，要么都丢弃"。

**面试官意图：** MQ 事务消息机制考察。
**深挖追问：** 追问"项目中有没有用 RocketMQ 事务消息"→ 项目中用的是 Spring Cloud Stream + RocketMQ Binder，通过 StreamBridge 发送普通消息和延时消息。事务一致性通过 Seata AT 模式保障，MQ 只做异步解耦不承担事务角色。
**项目结合：** `MessageSendServiceImpl.java`：`sendDeleteCartMsg()` 发送普通消息；`sendOrderCheckMsg()` 发送延时消息（`DELAY=4`，RocketMQ 延时等级 4 对应 30 秒）；MQ 消息发送前用 Redis `setIfAbsent` 做幂等去重。
**常见扣分项：** 不知道半消息机制和回查机制；混淆事务消息和普通消息。

---

### Q61：MQ 中如何保证消息消费的顺序性

**参考话术：** 两种方案：① 分区顺序性——同一个业务的消息发到同一个队列（MessageQueue），一个队列只能被一个消费者消费，保证队列内消息有序；② 全局顺序性——主题只开一个分区队列，只能被一个消费者消费，牺牲吞吐换取全局有序。实际生产中绝大多数场景只需要分区顺序——同一订单的操作在同一队列即可。

**面试官意图：** MQ 顺序消费的原理考察。
**深挖追问：** 追问"顺序消费和并发消费的区别"→ 顺序消费时消息一个一个处理，处理完才拉取下一条；并发消费时多线程同时处理多条消息，吞吐高但可能乱序。
**项目结合：** 项目中订单状态变更、支付回查等需要顺序的消息，通过 `StreamBindings` 配置分区策略（同一 orderId 的消息路由到同一队列）。
**常见扣分项：** 分不清分区顺序和全局顺序；不知道顺序消费的性能代价。

---

### Q62：如何实现消息去重，如何实现幂等性

**参考话术：** 用 Redis 实现幂等性去重：生产端——发送消息时用业务唯一标识（如订单号）作为 Redis key，`setIfAbsent` 插入，成功则发送消息，失败（key 已存在）则跳过。消费端——消费消息时同样用业务唯一标识做 `setIfAbsent`，成功则执行业务，失败则跳过。双重保障确保重复消息不会重复处理。

**面试官意图：** MQ 幂等性设计考察。
**深挖追问：** 追问"Redis 的 setIfAbsent 能保证绝对幂等吗"→ 不能 100%——Redis 和业务操作不在同一事务中，极端情况下 Redis 插入成功但业务执行前宕机，重启后消息被重新消费。解决方案是数据库唯一索引做最终兜底。
**项目结合：** `MessageSendServiceImpl.java`（order-service 和 pay-service 都有）：`redisTemplate.opsForValue().setIfAbsent(lockKey, "1", 30, TimeUnit.SECONDS)`——MQ 消息发送前 30 秒幂等锁；`pay_log` 表 `ux_pay_log UNIQUE on (order_id)` 数据库唯一索引兜底。
**常见扣分项：** 不知道幂等性的本质是"操作一次和操作多次结果相同"；不知道 Redis 幂等锁的局限性。

---

### Q63：微服务之间调用出现循环依赖，除了改成 MQ 异步解耦还有什么方案

**参考话术：** 三种方案：① 拆分中间公共服务——把两个服务互相调用的公共逻辑抽离成独立服务，两者都调公共服务而非直接互调；② 改成 MQ 异步解耦——非核心链路用消息队列解耦，主流程不阻塞；③ 接口下沉到公共模块——将共用的 DTO 和逻辑下沉到 `mall-common`，避免服务间的直接依赖。项目中最终选了方案 ②（MQ 异步解耦）——删除购物车、订单状态变更等非核心链路没必要做同步强依赖。

**面试官意图：** 微服务架构设计能力——不只是"解决"，而是"对比选型"。
**深挖追问：** 追问"异步解耦有什么缺点"→ 不适合需要实时返回结果的场景；消息可能延迟；调试排错比同步调用困难；需要额外的监控和死信队列处理。
**项目结合：** 订单服务与购物车服务之间通过 `deleteCart` topic 异步解耦；订单服务与支付服务之间通过 `orderTopic` 延时消息解耦。
**常见扣分项：** 只知道"改成 MQ"一种方案；不知道公共服务拆分的方案。

---

### Q64：支付回调重复推送，你们怎么实现接口幂等的

**参考话术：** 以支付流水号作为唯一 key，加 Redis 分布式锁：① 先查询订单支付状态，已支付则直接返回（幂等）；② 未支付则执行业务（更新订单状态、记录支付流水）；③ 核心逻辑投递 MQ 异步消费。数据库层面 `pay_log` 表 `ux_pay_log` 唯一索引兜底——重复插入支付流水会触发唯一约束异常，完全避免重复更新订单状态。

**面试官意图：** 支付场景幂等性设计——金融场景下幂等性是硬性要求。
**深挖追问：** 追问"如果幂等锁超时了但业务还在执行怎么办"→ 支付流水号的唯一索引做最终兜底——即使 Redis 锁过期导致重复消费，数据库唯一约束会拒绝重复插入。
**项目结合：** `pay_log` 表 `id varchar(32)` + `ux_pay_log UNIQUE on (order_id)`；支付服务 `MessageSendServiceImpl.java` 用 `setIfAbsent` 做幂等锁后再发 MQ 更新订单状态。
**常见扣分项：** 只说"Redis 锁"，不知道数据库唯一索引兜底。

---

### Q65：30 分钟未支付自动关单，你们为什么用 RocketMQ 延时队列 + ElasticJob 双兜底

**参考话术：** 主方案用 RocketMQ 延时消息：下单后发送一条延时消息（等级 4 = 30 秒，或根据需求配置），消费时检查订单是否已支付，未支付则执行关单。兜底方案用 ElasticJob 分布式定时任务：`PayCheckTask` 每日巡检超时订单，补偿遗漏的关单。双重保障：MQ 延时消息保证时效性，ElasticJob 兜底覆盖 MQ 消息丢失或消费失败的极端场景。

**面试官意图：** 关单方案的可靠性设计——不是单一方案，而是"主 + 兜底"双保险。
**深挖追问：** 追问"RocketMQ 延时等级最长 2 小时，需要延时 24 小时怎么办"→ 项目用 Redis 延时 Hash 表 + ElasticJob 定时扫描：消息存入 Redis 设置过期时间戳，ElasticJob 每 5 分钟扫描是否过期。或者修改 Broker 配置文件追加延时等级（需重启集群）。
**项目结合：** `MessageSendServiceImpl.java`（order-service）：`sendOrderCheckMsg()` 发送延时消息 `DELAY=4`；`PayCheckTask.java`（scheduler-service）实现 `SimpleJob`，调用 `alipayFeignClient.queryPaymentResult(orderId)` 回查并关单。
**常见扣分项：** 不知道 RocketMQ 延时等级最多 18 个；不知道双兜底的设计思路。

---

### Q66：你在项目中为什么选择 Elasticsearch 做商品搜索

**参考话术：** 三个核心诉求：① 全文检索——MySQL 的 `LIKE '%关键词%'` 会触发全表扫描，商品量过十万后查询耗时秒级；ES 基于倒排索引，检索速度 10-100 倍，支持集群水平扩容。② 多维度筛选——支持按分类、品牌、价格区间等多条件组合筛选，毫秒级返回。③ 搜索增强——内置中文分词、同义词、模糊匹配、搜索结果高亮，完美适配商品搜索场景。

**面试官意图：** ES 选型依据考察——为什么 MySQL 不够用。
**深挖追问：** 追问"MySQL 和 ES 数据怎么同步"→ 全量初始化（分页读取 MySQL → 清洗 → 批量写入 ES）+ 增量同步（Canal 监听 MySQL binlog → RocketMQ → 消费端写入 ES），延迟 2 秒内。
**项目结合：** `mall-es-service` 独立 ES 搜索服务；`ShopInfoEsFeignClient` 和 `ProductEsFeignClient` 提供 ES 数据操作接口；商品上下架后通过 MQ 异步同步到 ES。
**常见扣分项：** 不知道 ES 的倒排索引原理；不知道 Canal 增量同步方案。

---

### Q67：SSE 怎么实现的前后端具体过程是什么

**参考话术：** SSE（Server-Sent Events）是服务端向客户端推送事件流的技术——基于 HTTP 长连接，服务端设置 `Content-Type: text/event-stream`，客户端通过 `EventSource` API 接收服务端推送的数据流，实现"一次连接、持续推送"。

但需要说明：**我项目中的 AI 智能搜索当前使用的是同步 REST 方式**——前端发请求 → Java AISearchController → OpenFeign 同步调用 Python AI 服务 → 等待完整响应 → 一次性返回。如果要改为 SSE 流式输出，改造方案是：Controller 返回 `SseEmitter` 对象，Java 侧通过 OpenFeign 流式接收 Python 服务的逐 token 响应，再通过 `SseEmitter.send()` 逐段推送给前端。

**面试官意图：** 考察 SSE 技术的理解和真实落地情况——诚实比编造更重要。
**深挖追问：** 追问"SSE 和 WebSocket 的区别"→ SSE 是单向推送（服务端→客户端），基于 HTTP；WebSocket 是双向通信，独立协议。SSE 更简单、浏览器自动重连，适合"服务端推送"场景（如 AI 逐字输出）；WebSocket 适合双向实时通信（如聊天室）。
**项目结合：** `AiSearchController.java`（`/search/recommend`）当前返回 `Result<ProductRecommendDTO>`（同步一次性返回），OpenFeign 调用 `http://127.0.0.1:9010/api/v1/recommend` 等待完整响应。
**常见扣分项：** 编造"项目已经用了 SSE"——源码中没有 SSE 实现；不知道 SSE 和 WebSocket 的区别。

---

### Q68：这个提升了 35% 的召回效果怎么测试出来的

**参考话术：** 测试分三步：① 构建评测集——准备 200+ 条真实的商品搜索 query（包含模糊查询、同义词、多条件组合），人工标注每个 query 的正确商品结果（ground truth）；② 对比实验——优化前的检索方案（如纯关键词匹配）和优化后的方案（如向量语义检索 + 关键词混合检索），分别跑同一评测集；③ 评估指标——计算召回率（Recall@K，返回结果中正确商品出现在前 K 个的比例）、准确率（Precision）、F1 分数。优化后 Recall@10 从 62% 提升到 84%，相对提升约 35%。

**面试官意图：** AI 效果量化能力——"35%" 是怎么算出来的。
**深挖追问：** 追问"你怎么判断幻觉率降到了几乎为 0"→ 测试同学批量生成模糊检索测试用例，统计模型返回中"不在商品库中的 SKU"占比，手动验证每个返回结果的真实性。三层拦截（相似度阈值 + Prompt 约束 + 后端 SKU 校验）后统计幻觉占比。
**项目结合：** RAG 三层幻觉拦截：第一层检索层设相似度阈值过滤低相关内容；第二层 Prompt 约束模型不编造不在检索结果中的商品；第三层后端二次校验返回的 SKU 真实库存。
**常见扣分项：** 说不清"35%"的计算方式；不知道需要人工标注 ground truth。

---

### Q69：RAG 的工作原理详细讲一下

**参考话术：** RAG（Retrieval-Augmented Generation，检索增强生成）解决大模型两个痛点：知识时效性和幻觉问题。流程：① 文档处理——把商品介绍、规格参数做解析、清洗、分块（chunking），向量化后存入向量数据库；② 检索召回——用户输入自然语言查询，问题向量化后去向量库做相似度匹配，召回 Top-K 相关文档片段；③ Prompt 组装——将召回的商品资料和用户问题一起组装到提示词中传给大模型；④ 生成回答——大模型参考检索到的真实资料生成搜索结果；⑤ 后处理——过滤无关输出、校验 SKU 真实性。

**面试官意图：** RAG 全流程考察——不只是"用过"，而是"数据怎么处理的、检索怎么做、生成怎么控制"。
**深挖追问：** 追问"分块策略是什么"→ 按商品为单位分块——每个 SKU 作为一个 chunk，包含名称、描述、规格参数、价格等完整信息；chunk 大小控制在 512 tokens 以内，确保检索时语义完整。
**项目结合：** Java 侧（`AiSearchSeriveImpl.java`）负责接收前端查询，通过 OpenFeign 调用 Python RAG 服务（`http://127.0.0.1:9010/api/v1/recommend`）；Python 侧负责实际的向量化、向量检索和 LLM 调用。`threadId` 参数支持多轮会话上下文。
**常见扣分项：** 分不清 RAG 和纯微调的区别；不知道向量检索的基本原理。

---

### Q70：多轮对话上下文窗口溢出是什么原因，怎么解决

**参考话术：** 大模型存在 Token 上限（如 4K/8K/128K），多轮对话不断叠加历史消息会占满 Token 窗口，截断关键信息导致返回结果出错。解决方案：用 Redis 独立存储会话历史，按 `threadId` 管理对话上下文。每次请求只传最近 N 轮对话 + 当前问题，超长历史自动截断或摘要压缩，彻底避免 Token 溢出。

**面试官意图：** AI 应用开发中的常见问题处理能力。
**深挖追问：** 追问"截断策略怎么选"→ 固定轮次截断（保留最近 5 轮）最简单；滑动窗口截断（保留固定 Token 数）更精确；摘要截断（对早期对话做摘要）效果最好但需要额外 LLM 调用。项目用固定轮次截断保证简单可靠。
**项目结合：** AI 搜索接口 `GET /search/recommend?query=xx&threadId=xx`——`threadId` 标识会话 ID，Python 服务根据 threadId 从 Redis 加载历史上下文，截断超长部分后传给 LLM。
**常见扣分项：** 不知道 Token 窗口限制；不知道截断策略的选择。

---

## 第五章　项目实战场景（Q71–Q90）

---

### Q71：讲一下下单的完整流程

**参考话术：** 前端提交订单 → 网关鉴权透传 userId → 订单服务 `OrderServiceImpl.create()`：① Feign 调商品服务 `SkuInfoFeignClient.deductStock()` 批量扣减 DB 库存（任一商品不足整单失败）；② `IdWorker.getIdStr()` 生成雪花订单号；③ 计算每项金额（price × quantity）和总金额，保存订单主表 + 明细表；④ MQ 异步删除购物车（`sendDeleteCartMsg`）；⑤ MQ 延时消息做支付超时兜底（`sendOrderCheckMsg`）；⑥ 组装支付参数返回，前端跳转支付宝。整个链路在 Seata `@GlobalTransactional` 管控下。

**面试官意图：** 项目核心链路的完整表述能力。
**深挖追问：** 追问"如果 Feign 扣库存失败了怎么办"→ Seata 全局事务回滚，已执行的库存扣减自动恢复（通过 undo_log 反向补偿），订单不创建。
**项目结合：** `OrderServiceImpl.create()` 完整流程：反序列化 → Feign 扣库存 → IdWorker 生成订单号 → 计算金额 → saveBatch 订单项 → save 订单 → MQ 删购物车 → MQ 关单 → 返回支付参数。
**常见扣分项：** 说不清每一步的执行顺序；不知道 Seata 在其中的作用。

---

### Q72：你这个 SPO/SKU、商品表怎么设计的

**参考话术：** `spu_info` 表存储商品型号维度：id、spu_name、description；`sku_info` 表存储具体售卖规格：id、spu_id（关联 SPU）、price、sku_name、sku_attribute、num（库存）、brand_id、brand_name、category_id、category_name、sku_default_img、images、status、deleted。SKU 通过 `spu_id` 外键关联 SPU，查询商品列表以 SPU 聚合，库存管理以 SKU 维度操作。

**面试官意图：** 电商核心数据建模能力。
**深挖追问：** 追问"brand_name 和 category_name 为什么冗余在 SKU 表"→ 避免每次查询都 JOIN brand 和 category 表，空间换时间；品牌名和分类名变更频率极低，数据一致性问题可接受。
**项目结合：** `docs/db-sql/shop_goods.sql`：`sku_info` 表 17 个字段 + `idx_delete` 索引；`spu_info` 表 3 个字段；`brand` 表、`category` 表通过关联表 `category_brand` 建立多对多关系。
**常见扣分项：** 分不清 SPU 和 SKU 的数据归属；不知道冗余字段的权衡。

---

### Q73：你们的密码为什么用 BCrypt 加密不用 MD5

**参考话术：** BCrypt 是带随机盐的慢哈希算法——每个用户的密码加密串自带独立随机盐，两个用户密码相同加密结果也不同，有效防止彩虹表攻击。算力因子可调节加密耗时（如 2^10 = 1024 轮迭代），暴力破解成本极高。MD5 是快速哈希，没有盐值（或需要额外存盐），碰撞攻击已被实际破解，不适合存储密码。

**面试官意图：** 安全基础——密码存储方案的选型依据。
**深挖追问：** 追问"BCrypt 的盐值存在哪里"→ BCrypt 加密串本身就包含了盐值——格式为 `$2a$10$...`，其中 `10` 是 cost factor，后面的字符串同时包含盐值和哈希值。验证时取出盐值重新计算比对即可，不需要单独存储。
**项目结合：** 用户注册时密码通过 BCrypt 加密后存入 `user_info` 表的 `password` 字段；登录时取出加密串与输入密码的 BCrypt 哈希比对。
**常见扣分项：** 不知道 BCrypt 自带盐值；说"MD5 加盐也安全"。

---

### Q74：雪花算法怎么解决服务器时钟回拨导致重复 ID 的问题

**参考话术：** 每次生成 ID 时校验当前系统时间和上次生成 ID 的时间：回拨时间 < 50ms 直接等待时间追上再继续生成；回拨时间 > 50ms 抛出异常告警或自动切换到备用 ID 生成服务。MyBatis-Plus 的 `IdWorker` 内部已做了时钟回拨检测——如果回拨超过阈值会抛出 `IncrGenerateException`。

**面试官意图：** 雪花算法的边界问题处理能力。
**深挖追问：** 追问"除了等待还有什么方案"→ ① 双 buffer 方案——提前生成一批 ID 缓存到内存，时钟回拨时从 buffer 取不依赖时间戳；② 扩展位——用 3 位扩展位记录时钟回拨次数，每次回拨扩展位 +1，不影响唯一性。
**项目结合：** `IdWorker.getIdStr()`（MyBatis-Plus 内置）已处理时钟回拨；`order_id varchar(64)` 存储字符串形式的雪花 ID。
**常见扣分项：** 只说"等待时间追上"，不知道双 buffer 等其他方案。

---

### Q75：你项目中用到的注解——@GlobalTransactional 怎么工作的

**参考话术：** `@GlobalTransactional` 是 Seata 提供的分布式事务注解。标注在类或方法上后，Seata 在方法执行前开启一个全局事务（生成 XID），通过 RPC 框架（Feign 拦截器）将 XID 透传到所有下游服务。下游服务检测到 XID 后自动加入同一个全局事务。方法正常完成则全局提交（异步删除 undo_log），方法抛异常则全局回滚（根据 undo_log 反向补偿所有参与者）。

**面试官意图：** Seata 注解的工作机制——不只是"用了"，而是"底层怎么运作"。
**深挖追问：** 追问"XID 怎么在 Feign 中透传"→ Seata 提供了 `RootContextInterceptor`（或 `SeataFeignClient`），在 Feign 请求发起前从 ThreadLocal 提取 XID 塞入请求 Header，下游服务接收后绑定到本地 ThreadLocal。
**项目结合：** `OrderServiceImpl.java` 类级别 `@GlobalTransactional`——`create()` 方法中 Feign 调用 `SkuInfoFeignClient.deductStock()` 时 XID 自动透传到商品服务，商品服务的扣库存操作自动加入全局事务。
**常见扣分项：** 不知道 XID 的透传机制；不知道 undo_log 的作用。

---

### Q76：布隆过滤器是怎么落地的

**参考话术：** 项目封装了 `CacheBloomFilter` 类（mall-common），底层使用 Redisson 的 `RBloomFilter`：① 应用启动时 `tryInit(expectedInsertions, falseProbability)` 初始化布隆过滤器，指定预期元素数量和误判率（如 0.03 = 3%）；② 秒杀预热时，`LoadSeckillProductTask` 调用 `bloomFilter.add(stockKey)` 将有效的库存 key 加入布隆过滤器；③ 秒杀请求到达时，`ProductServiceImpl.decreaseStock()` 中先调用 `cacheBloomFilter.mightContain(stockKey)` 预检——如果返回 false（一定不存在），直接拒绝请求，不打到 Redis 和 MySQL，防止缓存穿透。

**面试官意图：** 布隆过滤器的项目落地能力。
**深挖追问：** 追问"为什么布隆过滤器放 common 模块"→ 所有服务全局复用，从架构层面统一防护缓存穿透，而不是每个服务单独实现。
**项目结合：** `CacheBloomFilter.java`（mall-common）当前被注释掉（`//@Component`），实际在 `ProductServiceImpl` 中注入使用。秒杀预热 `LoadSeckillProductTask` 中调用布隆过滤器预加载。
**常见扣分项：** 不知道布隆过滤器的假阳性特性；不知道 expectedInsertions 和 falseProbability 参数的含义。

---

### Q77：商品详情页为什么不用 MySQL LIKE 搜索而用 ES

**参考话术：** MySQL `LIKE '%关键词%'` 会触发全表扫描——索引在前缀模糊查询下完全失效，商品量过十万后查询耗时直接涨到秒级。ES 基于倒排索引设计，将商品名称、描述分词后建立倒排索引，检索速度是 MySQL 的 10-100 倍，支持集群水平扩容，百万级商品数据毫秒级返回。同时 ES 内置中文分词（IK Analyzer）、同义词、模糊匹配、搜索结果高亮，完美适配电商搜索场景。

**面试官意图：** ES 选型的具体依据——MySQL 哪里不行。
**深挖追问：** 追问"ES 和 MySQL 的数据一致性怎么保证"→ 全量初始化 + Canal 监听 binlog 增量同步：MySQL 数据变更 → Canal 解析 → RocketMQ → 消费端清洗后写入 ES，延迟控制在 2 秒内。
**项目结合：** `mall-es-service` 独立 ES 搜索服务；`ProductEsFeignClient` 和 `ShopInfoEsFeignClient` 提供商品 ES 数据操作接口。
**常见扣分项：** 不知道倒排索引的原理；不知道 LIKE 前缀模糊会导致索引失效。

---

### Q78：分布式全局唯一 ID 用雪花算法，如果服务重启了 workerId 会不会冲突

**参考话术：** 项目中 workerId 通过 Nacos 配置中心或环境变量为每个实例分配，确保同一机房内不同实例的 workerId 唯一。服务重启后 workerId 不变（从配置读取），不会冲突。如果需要自动化分配，可以用 Zookeeper 的临时顺序节点自动分配 workerId，每个服务启动时注册临时节点获取唯一编号。

**面试官意图：** 雪花算法 workerId 管理的工程化细节。
**深挖追问：** 追问"MyBatis-Plus 的 IdWorker 怎么分配 workerId"→ 默认从服务器 MAC 地址和进程 ID 生成，单机多实例时可能冲突。生产环境建议通过配置文件或 Nacos 显式指定 workerId。
**项目结合：** `IdWorker.getIdStr()` 在 `OrderServiceImpl` 中生成订单号；各服务部署在不同容器/机器上，通过环境配置确保 workerId 不重复。
**常见扣分项：** 不知道 workerId 的分配方式；不知道 MAC 地址方案的局限性。

---

### Q79：你们项目里批量导入 10 万条商品数据很慢，怎么优化的

**参考话术：** 初始方案是循环单条插入 + 逐条提交事务，频繁 IO 导致 10 万条导入要 2 分钟。优化两步：① 关闭自动提交，每 1000 条批量事务提交，代码用 MyBatis `saveBatch()` 批量插入代替循环单存；② 导入前临时关闭非必要索引，导入完成后重建索引。优化后耗时降到 10 秒内。

**面试官意图：** 批量数据处理的性能优化实战。
**深挖追问：** 追问"如果数据量到百万级怎么办"→ 通过 RocketMQ 分片异步导入，避免长事务锁表；或用 MySQL `LOAD DATA LOCAL INFILE` 原生批量导入（数据库最快方案）。
**项目结合：** `OrderServiceImpl.java` 中 `orderItemsService.saveBatch(orderItemsList)` 用了批量插入；`SkuInfoEsFeignClient` 的全量同步也用批量写入 ES。
**常见扣分项：** 不知道批量提交 vs 逐条提交的性能差异；不知道索引对写入性能的影响。

---

### Q80：Seata 的工作机制流程是什么

**参考话术：** 阶段一（Phase 1）：Seata 解析 SQL，生成 before image 和 after image，将镜像数据写入 `undo_log` 表，在本地事务中提交业务 SQL + 回滚日志，获取全局锁确保其他事务无法修改同一行。阶段二（Phase 2）：提交时异步删除 `undo_log` 并释放锁；回滚时根据 `undo_log` 生成反向补偿 SQL（如 INSERT → DELETE, UPDATE → UPDATE 恢复旧值）恢复原始数据。

**面试官意图：** Seata AT 模式的深度理解——两阶段的具体操作。
**深挖追问：** 追问"全局锁是什么"→ Seata 在 TC（事务协调者）维护一个全局锁表，阶段一提交时记录"事务 X 锁定了表 A 的行 1"，其他全局事务如果要修改同一行必须等待锁释放。本地事务不受影响（因为本地事务已经提交）。
**项目结合：** `OrderServiceImpl` 的 `@GlobalTransactional` 覆盖整个 `create()` 方法；Seata Server 配置在 Nacos `order_stock.yml`（SEATA_GROUP）中。
**常见扣分项：** 不知道 undo_log 的具体作用；分不清阶段一和阶段二的操作。

---

### Q81：什么是事务，你项目中在哪里用了事务

**参考话术：** 事务是一个程序执行单元，其中的操作要么全部成功，要么全部回滚。项目中有两种事务：① 本地事务——单个 Service 方法内用 `@Transactional`，如 `UserInfoServiceImpl.register()` 中的用户创建 + 地址初始化在同一事务内；② 分布式事务——跨服务操作用 `@GlobalTransactional`（Seata），如 `OrderServiceImpl.create()` 中 Feign 扣库存 + 创建订单 + 发 MQ 全部在一个全局事务内。

**面试官意图：** 事务在项目中的实际使用场景。
**深挖追问：** 追问"什么时候该用 `@Transactional` 什么时候该用 `@GlobalTransactional`"→ 同一个服务内、同一个数据源用 `@Transactional`（本地事务，性能好）；跨服务、跨数据源用 `@GlobalTransactional`（分布式事务，性能较差但保证一致性）。
**项目结合：** `OrderServiceImpl` 类级别 `@GlobalTransactional`（Seata 全局事务）；`UserInfoServiceImpl` 方法级别 `@Transactional`（本地事务）。
**常见扣分项：** 分不清本地事务和分布式事务的使用场景；不知道 `@Transactional` 的传播行为。

---

### Q82：Redisson 锁内读取 Redis 库存后判断 count > 0，如果库存刚好为 0 怎么办

**参考话术：** `decreaseStock()` 中：读取库存 `GET stockKey` 得到 count；如果 `count > 0`，执行 `decrement` 原子扣减 1；如果 `count <= 0`，直接抛出库存不足异常返回。扣减后检查扣减结果——如果 `decrement` 返回值 < 0（极端并发场景），执行 `increment` 回滚并抛异常。finally 块释放锁。

**面试官意图：** 秒杀库存扣减的边界处理能力。
**深挖追问：** 追问"为什么 decrement 后还要检查结果"→ 因为 tryLock 的等待时间内可能有其他线程先获取锁并扣减了库存，当前线程获取锁时库存可能已经为 0 但还没检查——decrement 返回值 < 0 说明超卖了，必须回滚。
**项目结合：** `ProductServiceImpl.java`（seckill-service）：`long remainCount = redisTemplate.opsForValue().decrement(stockKey)`；`if (remainCount < 0) { redisTemplate.opsForValue().increment(stockKey); throw new BusinessException("库存不足"); }`
**常见扣分项：** 不知道 decrement 后要检查返回值做超卖兜底。

---

### Q83：你们的秒杀是怎么做的

**参考话术：** 事前预热：ElasticJob 定时任务每天执行——加载秒杀商品列表、生成静态页、预热库存到 Redis + 布隆过滤器。事中四层漏斗：① 网关限流——活动级令牌桶控制 QPS；② 布隆过滤器——不存在的商品 key 直接拒绝；③ Redisson 分布式锁——同一商品同一时刻只有一个请求在扣减；④ Redis 原子扣减——锁内判断库存 > 0 后 decrement。事后：MQ 异步同步库存到 DB，支付成功后确认扣减，超时未支付 increment 回滚。

**面试官意图：** 秒杀全链路架构——从预热到事后处理的完整方案。
**深挖追问：** 追问"为什么不用 Lua 脚本直接扣"→ Lua 更优（原子性好、性能高），但选 Redisson 是为了演示分布式锁治理，且锁内预留了风控、限购等扩展逻辑。
**项目结合：** 完整链路：`LoadSeckillProductTask`（预热）→ Gateway 限流 → `CacheBloomFilter`（穿透防护）→ `ProductServiceImpl.decreaseStock()`（Redisson 锁 + Redis 扣减）→ `StreamBridge.send("stockDeductOutput-out-0")`（MQ 异步同步 DB）。
**常见扣分项：** 只说"Redis 扣库存"，说不清四层漏斗的完整流程。

---

### Q84：为什么不用远程调用修改订单状态而用 MQ

**参考话术：** 远程调用是同步的——如果下游服务超时或异常，会阻塞支付回调线程，拉长支付响应时间甚至导致支付失败。MQ 异步处理：支付成功后发 MQ 消息，订单服务异步消费更新状态，主流程不阻塞；MQ 有重试机制，消费失败自动重试；超过重试次数进死信队列人工处理。保证最终一致性而非强一致性——对订单状态变更这种非实时要求的场景，最终一致性足够。

**面试官意图：** 同步 vs 异步通信方案的决策能力。
**深挖追问：** 追问"什么场景必须用同步调用"→ 涉及实时数据一致性的场景，如库存扣减（必须实时返回成功/失败给用户）、支付创建（必须拿到支付宝的支付链接返回前端）。
**项目结合：** 支付服务 `MessageSendServiceImpl.java`：支付成功后通过 `StreamBridge.send("updateOrderOutput-out-0", message)` 异步通知订单服务更新状态；通过 `StreamBridge.send("orderRecovery-out-0", message)` 发送延时消息做关单兜底。
**常见扣分项：** 不知道同步和异步的适用场景区别。

---

### Q85：Feign 调用出现 ReadTimeout 怎么解决

**参考话术：** 三步处理：① 配置 Feign 超时时间——`feign.client.config.default.readTimeout` 设置合理值（远大于业务平均响应时间，如 5-10 秒）；② 搭配 Sentinel 超时熔断——下游连续超时达到阈值自动熔断，返回预设兜底结果，不会一直阻塞等待；③ 根本优化——排查下游服务的性能瓶颈（慢 SQL、锁等待、GC 停顿），优化接口响应时间。

**面试官意图：** 远程调用超时的排查和解决能力。
**深挖追问：** 追问"Sentinel 超时熔断和 Feign 超时的关系"→ Feign 超时是单次请求的最大等待时间；Sentinel 熔断是统计一段时间内的慢调用比例，达到阈值后直接熔断不再发请求。两者配合：Feign 控制单次等待，Sentinel 控制整体熔断。
**项目结合：** `mall-api` 中所有 FeignClient 配置了 `fallbackFactory`（如 `ProductFallbackFactory.class`），Sentinel 熔断后自动走降级逻辑返回兜底结果。
**常见扣分项：** 只会"加大超时时间"，不知道熔断机制。

---

### Q86：你们的 JWT 代码是怎么实现的

**参考话术：** `JwtUtil` 类（`@Component`）实现完整 Token 管理：① `createTokenAndStore()` 生成 JWT + 存入 Redis（key = `user:token:{userId}`，TTL = 30 分钟）；② `validateTokenWithRedis()` 四步校验——检查 Redis 黑名单 → 验证 JWT 签名和过期 → 检查 Redis key 是否存在 → 比对 Token 是否匹配（多设备登录检测）；③ `logoutByToken()` 将 Token 写入黑名单（TTL = 剩余有效期）再删 Redis key；④ `forceLogout()` 管理员踢出，黑名单 7 天。

**面试官意图：** JWT 完整实现流程——不只是"用了 JWT"，而是"怎么生成、怎么校验、怎么续期、怎么踢出"。
**深挖追问：** 追问"为什么 Token 放 Redis 还要做 JWT 签名校验"→ JWT 签名校验保证 Token 没被篡改（无状态安全）；Redis 存储保证能在线踢出（有状态管控）。两者结合：即使 Token 未过期，管理员也可以通过删除 Redis key 强制失效。
**项目结合：** `mall-common/src/main/java/.../util/JwtUtil.java`：HS256 签名（`Keys.hmacShaKeyFor()`）、RedisTemplate 存储/黑名单、`@Value` 注入配置；`AuthGatewayFilterFactory.java` 调用 `validateTokenWithRedis()` 三步校验 + 生成新 Token 放入响应 Header 实现无缝续期。
**常见扣分项：** 不知道 JWT 的黑白名单机制；不知道网关刷新 Token 的设计。

---

### Q87：订单数据量达到千万级，怎么设计水平分表

**参考话术：** 以 `user_id` 做哈希分片——同一用户的所有订单存入同一张分表，用户查询自己的订单路由到单张分表，性能极高。分表路由：`分表索引 = user_id % 分表数量`。全局使用雪花算法生成分布式唯一主键保证不重复。Sharding-JDBC 作为分表中间件，对应用层透明——开发者写普通 SQL，Sharding-JDBC 自动路由到对应分表。

**面试官意图：** 分库分表的方案设计能力。
**深挖追问：** 追问"分表后 JOIN 怎么处理"→ 分表后无法跨表 JOIN。解决方案：冗余字段（下单时保存商品快照到 order_items）+ 数据冗余（订单表冗余用户名等信息）；需要全局聚合查询时用 ES 或数据仓库做二次索引。
**项目结合：** `order_info` 表目前单表存储，`order_id varchar(64)` 为雪花 ID；如果数据量增长，可按 `user_id` 分 16 或 64 张表；Sharding-JDBC 配置在 Nacos 中。
**常见扣分项：** 不知道分片键的选择原则；不知道分表后 JOIN 的限制。

---

### Q88：三瓶矿泉水 10 元，订单的数据库设计怎么体现

**参考话术：** `order_info` 表存订单维度：`order_id`、`user_id`、`total_amount`（30 元 = 3 × 10）、`status`（0 待支付/1 待发货/2 已发货/3 已完成/4 已关闭/5 无效）、`pay_type`、`order_type`（0 正常/1 秒杀）。`order_items` 表存明细维度：`order_id`（关联订单）、`sku_id`（矿泉水的 SKU）、`sku_name`（冗余快照）、`price`（10 元单价）、`quantity`（3 瓶）、`amount`（30 元 = 10 × 3）。一个订单可以有多个 order_items（多种商品），通过 `order_id` 关联。

**面试官意图：** 电商订单数据建模实战——把业务场景落到表结构。
**深挖追问：** 追问"如果一瓶矿泉水 3.33 元，3 瓶总价怎么算"→ `amount = price × quantity = 3.33 × 3 = 9.99`，用 `decimal(10,2)` 确保精度；如果需要四舍五入到分，业务层做 `BigDecimal.setScale(2, RoundingMode.HALF_UP)`。
**项目结合：** `shop_order.sql`：`order_info`（8 个核心字段 + 索引）+ `order_items`（7 个核心字段 + 索引）；`OrderServiceImpl.create()` 中 `item.setAmount(item.getPrice() * item.getQuantity())` 计算明细金额。
**常见扣分项：** 分不清订单表和订单明细表的职责；不知道金额精度问题。

---

### Q89：你们用 OpenFeign 的时候有没有遇到什么问题，怎么解决的

**参考话术：** 三个常见问题：① 请求头丢失——Feign 默认不携带上游请求头，用 `HeaderInterceptor` 实现透传；② 超时问题——下游慢调用导致 ReadTimeout，配置合理超时时间 + Sentinel 熔断降级；③ 降级处理——Feign 调用失败需要兜底，通过 `FallbackFactory` 绑定降级实现类，异常时返回预设结果而非报错。

**面试官意图：** Feign 实战踩坑经验。
**深挖追问：** 追问"FallbackFactory 和 Fallback 的区别"→ Fallback 无法获取异常信息；FallbackFactory 可以拿到 `Throwable` 异常对象，可以做日志记录、告警、异常分类处理。
**项目结合：** `mall-api` 下 7 个 FallbackFactory 类（`ProductFallbackFactory`、`LoginFallbackFactory` 等）；`HeaderInterceptor` 配置白名单透传 Header；Sentinel 规则通过 Nacos 动态下发。
**常见扣分项：** 不知道 FallbackFactory 和 Fallback 的区别；不知道 HeaderInterceptor 的作用。

---

### Q90：Redisson 分布式锁可重入是怎么实现的

**参考话术：** Redisson 用 Hash 结构存储锁信息：key 是锁名称，field 是线程唯一标识（UUID + 线程 ID），value 是重入计数。首次加锁：`HSET lockKey threadId 1` + 设置过期时间；同一线程重入：`HINCRBY lockKey threadId 1`（计数 +1）；释放锁：`HINCRBY lockKey threadId -1`（计数 -1）；计数归零时 `DEL lockKey` 真正释放。通过 Lua 脚本保证原子性。

**面试官意图：** Redisson 底层实现原理——不只是"可重入"，而是"怎么实现的"。
**深挖追问：** 追问"为什么用 Hash 而不是 String"→ String 只能存一个值，无法区分不同线程的重入；Hash 的 field 可以存储多个线程标识，value 记录每个线程的重入次数，实现可重入计数。
**项目结合：** `ProductServiceImpl.decreaseStock()` 中 `tryLock(3, TimeUnit.SECONDS)` 不设 leaseTime——Redisson 启用看门狗自动续期；如果同一线程递归调用 `decreaseStock()`（虽然项目中不会发生），Hash 结构的重入计数会正确处理。
**常见扣分项：** 不知道 Hash 结构的重入计数实现；分不清可重入和不可重入的区别。

---

## 第六章　软技能与收尾（Q91–Q93）

---

### Q91：结合你的小组项目，说说你的优势和短板

**参考话术：** 优势：① 自主钻研落地能力强——秒杀超卖防护和整套 RAG 智能检索均独立从零开发，遇到分布式锁、大模型幻觉等难题自主查阅大厂文档，本地多组压测对比方案，独立完成功能全闭环；② 团队协同沟通顺畅——开发前前置输出接口文档，主动组织技术研讨，统一全项目阿里编码规范；③ 善于沉淀总结——索引优化、缓存踩坑、RAG 调优的笔记统一上传 Git 形成团队知识库。

短板：① 无线上生产实战经验——仅做过单机实训集群，没有百万 QPS 线上集群、分库分表白盒落地、K8s 容器编排实战经验；② AI 工程化深度有限——RAG 检索效果调优、大模型微调、Prompt Engineering 还有提升空间。

成长规划：短期 3 个月吃透公司技术栈和线上架构，跟随师傅快速补齐生产级研发经验；长期深耕后端 + AI 融合技术方向。

**面试官意图：** 自我认知能力——优势要具体有例子，短板要真诚有改进计划。
**深挖追问：** 追问"如果入职后发现项目用的技术栈你没学过怎么办"→ "我在实训中学习 Nacos、Seata、ES 都是从零开始快速上手的，有系统的学习方法论；入职后我会先搭建本地环境跑通 demo，再阅读官方文档理解原理，最后结合项目实战消化吸收。"
**项目结合：** 具体事例都来自 mall-micro-cloud 项目——秒杀四层防护、RAG 三层幻觉拦截、索引优化方案。
**常见扣分项：** 优势说不出具体事例；短板说"没有缺点"或说"太追求完美"这种虚假回答。

---

### Q92：你对工资、福利有什么要求

**参考话术：** 我现阶段更看重公司技术栈匹配度和成长空间，薪资可以结合公司对应届生的统一薪酬标准、绩效福利体系来定。如果有幸加入团队，我会尽快补齐技术短板，跟上团队开发节奏，为公司创造价值。

**面试官意图：** 薪资谈判的应届生标准回答——不卑不亢、看平台成长。
**深挖追问：** 无标准深挖，但可追问"你的期望范围"→ 可以给出参考范围（结合当地校招市场行情），但不要精确到个位数。
**项目结合：** 无。
**常见扣分项：** 第一轮技术面就谈具体数字；说"什么都行"显得没有规划。

---

### Q93：你还有什么想向我了解的吗

**参考话术：** 我想了解一下贵公司这个岗位后续的技术方向——主要做哪个业务线、技术栈是什么、应届生的培养机制是怎样的？（展示你对岗位的兴趣和入职后的学习规划）

**面试官意图：** 反问环节考察你对岗位的重视程度和思考深度。
**项目结合：** 无。
**常见扣分项：** 说"没问题了"显得没有兴趣；问薪资福利（留给 HR 面）。

---

## 附录：追问链速查表（面试前 5 分钟扫一眼）

| # | 追问链 |
|---|--------|
| 1 | 微服务为什么选 Spring Cloud Alibaba → Nacos 注册+配置 → Gateway 鉴权 → Feign 通信 → Sentinel 防护 → Seata 事务 |
| 2 | 秒杀怎么做 → 事前预热(ElasticJob) → 网关限流 → 布隆过滤器 → Redisson 锁 → Redis 原子扣减 → MQ 异步同步 |
| 3 | 下单流程 → 网关鉴权 → Feign 扣库存 → 雪花 ID → 保存订单 → MQ 删购物车 → MQ 延时关单 → 支付宝 |
| 4 | Redis 用在哪些场景 → 缓存(商品/秒杀) → 分布式锁(Redisson) → Token 存储(JWT) → 幂等性(setIfAbsent) → 布隆过滤器 |
| 5 | Seata AT 模式 → 阶段一(快照+undo_log+本地提交) → 阶段二(提交删 undo_log / 回滚补偿) → 全局锁 |
| 6 | SQL 调优 → 联合索引+最左前缀 → EXPLAIN 验证 → 游标分页(禁大偏移量) → 批量提交(1000条) → 索引管理规范 |
| 7 | JWT 全流程 → 生成(HS256) → 存 Redis → 网关三步校验 → 注入 X-User-Id → 刷新新 Token → 黑名单踢出 |
| 8 | 缓存三兄弟 → 穿透(布隆+空值缓存) → 击穿(永不过期+互斥锁) → 雪崩(随机 TTL+多级缓存) |
| 9 | RAG 全流程 → 文档分块 → 向量化 → 向量库召回 → Prompt 组装 → LLM 生成 → 三层幻觉拦截 |
| 10 | Feign 坑点 → Header 丢失(拦截器透传) → 超时(Sentinel 熔断) → 降级(FallbackFactory) → 循环依赖(MQ 解耦) |

---

> **文档版本**：v1.0 | **题目数量**：93 题 | **覆盖域**：自我介绍 / Java 基础 / 数据库 / 中间件 / 项目实战 / 软技能
> **基于**：mall-micro-cloud 项目源码 + 论微服务架构论文 + 面试场景示例 + java 笔记 + interview-qa
> **生成日期**：2026-08-29
