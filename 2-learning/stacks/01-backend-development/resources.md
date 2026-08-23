# 资源推荐 — 后端开发学习路径

## 推荐书籍

### 架构设计

| 书名 | 作者 | 推荐理由 | 难度 |
|------|------|----------|------|
| 《大型网站技术架构》 | 李智慧 | 国内网站架构入门最佳，从单体到分布式演进路径清晰 | 入门 |
| 《微服务架构设计模式》 | Chris Richardson | 微服务设计的"圣经"，每个模式都有代码示例（Java） | 进阶 |
| 《企业应用架构模式》 | Martin Fowler | 虽老但不朽，分层、领域逻辑模式至今适用 | 进阶 |
| 《领域驱动设计》 | Eric Evans | DDD 原版，"蓝皮书"，概念密度高，建议有经验后再读 | 高阶 |
| 《实现领域驱动设计》 | Vaughn Vernon | DDD 落地的实操指南，比蓝皮书好读 | 高阶 |

### API 设计

| 书名 | 作者 | 推荐理由 |
|------|------|----------|
| 《RESTful Web Services》 | Leonard Richardson | RESTful 的奠基之作，教你"什么是真正的 REST" |
| 《API Design Patterns》 | JJ Geewax | Google 的 API 设计实践，涵盖版本、分页、错误处理等模式 |

### 基础与编码

| 书名 | 作者 | 推荐理由 |
|------|------|----------|
| 《阿里巴巴 Java 开发手册》 | 阿里巴巴 | 国内 Java 开发的最佳实践集，每周翻一遍 |
| 《重构：改善既有代码的设计》 | Martin Fowler | 代码坏味道与重构手法，每个后端开发都应读 |
| 《代码整洁之道》 | Robert C. Martin | 命名、函数、注释、异常处理，基本功 |

## 推荐开源项目

### 学习项目

- **[Spring PetClinic](https://github.com/spring-petclinic/spring-petclinic-microservices)**：Spring 官方微服务示例，看架构设计和代码组织
- **[Macro](https://gitee.com/macro/mall)**：国内最流行的电商微服务项目，Spring Cloud Alibaba 栈
- **[RuoYi](https://gitee.com/yangzongr/RuoYi-Vue-Cloud)**：微服务版后台管理系统，适合学习 RBAC 权限

### 基础组件

- **[Spring Cloud Alibaba](https://github.com/alibaba/spring-cloud-alibaba)**：Nacos + Sentinel + Seata + RocketMQ — 国内微服务首选
- **[Sentinel](https://github.com/alibaba/Sentinel)**：限流熔断降级，阿里开源的流量防卫兵
- **[Seata](https://github.com/seata/seata)**：分布式事务解决方案，AT / TCC / Saga 三种模式
- **[MyBatis-Plus](https://github.com/baomidou/mybatis-plus)**：国内最流行的 MyBatis 增强工具
- **[MapStruct](https://github.com/mapstruct/mapstruct)**：对象转换工具，比 BeanUtils 快 10 倍

## 推荐文章与博客

### 必读系列

- **Martin Fowler 的 Blog**：`martinfowler.com`，微服务、DDD、重构概念的源头
- **美团技术团队 Blog**：`tech.meituan.com`，大量实战文章（秒杀、分布式 ID、限流）
- **阿里云开发者社区**：`developer.aliyun.com`，阿里中间件和架构设计实战
- **Spring 官方 Blog**：`spring.io/blog`，Spring 生态的最新动态和最佳实践

### 单篇推荐

- [《The Clean Architecture》](https://blog.cleancoders.com/) - Robert C. Martin 关于 Clean Architecture 的原版博客
- [《如何设计一个秒杀系统》](https://github.com/doocs/advanced-java/blob/main/docs/high-concurrency/how-to-design-seckill-system.md) - 高级 Java 必知必会的秒杀设计
- [《分布式 ID 生成器方案对比》](https://tech.meituan.com/2017/04/21/mt-leaf.html) - 美团 Leaf 分布式 ID 原理

## 学习路线建议

### 第一阶段：基本功（1-2 个月）
- 精读《阿里巴巴 Java 开发手册》
- 手写一个 Spring Boot CRUD 项目
- 理解三层架构各层职责

### 第二阶段：深入（2-3 个月）
- 阅读 Spring Cloud 官方文档和示例
- 实现一个 RESTful API 项目（带分页、校验、异常处理）
- 学习 CAP 定理和 BASE 理论
- 用 JUnit + Mockito 写单元测试

### 第三阶段：实战（3-4 个月）
- 用 Spring Cloud Alibaba 实现一个微服务项目
- 集成 Sentinel 做限流、Seata 做分布式事务
- 参与开源项目或自己做一个完整项目（如电商）

### 第四阶段：面试冲刺（2-3 周）
- 过一遍本模块 05-interview 的所有题目
- 每天写一道场景题（框架+细节）
- 把项目经验按照 STAR 方式组织好

## 面试准备清单

- [ ] 理解 CAP 和 BASE，能用自己的话解释
- [ ] 掌握 RESTful 规范（状态码、版本、分页）
- [ ] 能写出三层架构的完整代码（Controller → Service → Repository）
- [ ] 知道 DDD 战术设计的基本概念
- [ ] 能设计一个秒杀系统（从限流到削峰到防超卖）
- [ ] 能设计一个短链系统（发号器 + 62 进制转换）
- [ ] 熟悉 JWT 和 Session 的区别
- [ ] 准备一个 STAR 项目经验故事