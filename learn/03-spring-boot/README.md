# Spring Boot — 自动配置 · 启动原理 · 生态整合

> 本模块是 16 个技术栈学习体系中的**第三个技术栈**，也是 AI 商城所有微服务的基础框架。
> Spring Boot 是 Java 后端开发的"事实标准"，面试中几乎 100% 涉及。

---

## 什么是 Spring Boot？

Spring Boot 是 Spring 生态的**一站式框架**，核心价值在于：

- **自动配置**：根据 classpath 依赖自动注入 Bean，大幅减少 XML 配置
- **Starter 依赖**：一键引入场景依赖（如 `spring-boot-starter-web`）
- **内嵌容器**：内嵌 Tomcat/Jetty/Undertow，jar 包直接运行
- **生产就绪**：Actuator 端点、Metrics 监控、健康检查
- **生态整合**：无缝对接 Spring Cloud、MyBatis、Redis、RocketMQ

一句话概括：**Spring Boot 用"约定大于配置"的理念，让 Spring 应用开发从"配置地狱"变成"开箱即用"。**

---

## 学习路径图

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│               03 Spring Boot · 技术栈总览 (本文档)                                      │
└─────────────────────────────────────────────────────────────────────────────────────┘
                                       │
          ┌────────────────────────────┼────────────────────────────────┐
          ▼                            ▼                                ▼
┌─────────────────────┐   ┌─────────────────────┐   ┌─────────────────────────┐
│  01-basics  👶      │   │  02-core  👶→🎯      │   │  03-advanced  🎯        │
│  ├─ 快速入门         │   │  ├─ 自动配置原理      │   │  ├─ Actuator 可观测性    │
│  └─ IoC/DI 容器     │   │  └─ 启动流程          │   │  ├─ 测试框架            │
│                     │   │                     │   │  ├─ GraalVM 原生编译     │
│                     │   │                     │   │  └─ Spring Boot 3 新特性  │
└─────────────────────┘   └─────────────────────┘   └─────────────────────────┘
                                       │
                                       ▼
          ┌──────────────────────────────────────────────────────────────┐
          │  04-projects  🎯 项目实战                                    │
          │  ├─ mall-integration    AI 商城 Spring Boot 实践             │
          │  └─ mini-blog           用 Spring Boot 实现博客 API          │
          └──────────────────────────────────────────────────────────────┘
                                       │
                                       ▼
          ┌──────────────────────────────────────────────────────────────┐
          │  05-interview  🎯 面试冲刺                                   │
          │  ├─ quick-revision  速记版 (30 个高频考点)                   │
          │  ├─ deep-dive       深挖题 (源码级分析)                      │
          │  ├─ scenario        场景题 (实战问题)                        │
          │  └─ coding          代码题 (手写 Starter/测试)                │
          └──────────────────────────────────────────────────────────────┘
```

---

## 前置知识

| 前置要求 | 说明 |
|---------|------|
| Java 基础 | 熟悉 OOP、注解、反射、泛型（详见 02-java 模块） |
| Spring 基础 | 理解 IoC 容器、DI 依赖注入、AOP 概念 |
| Maven/Gradle | 熟悉依赖管理、构建生命周期 |
| 数据库基础 | SQL、JDBC 基本使用 |

---

## 面试高频考点一览表

| 考点 | 重要度 | 频次 | 说明 |
|------|--------|------|------|
| 自动配置原理 | ★★★★★ | 必问 | @EnableAutoConfiguration、spring.factories、条件注解 |
| 启动流程 | ★★★★★ | 必问 | SpringApplication.run() 10 步流程 |
| 条件注解 | ★★★★☆ | 高频 | @ConditionalOnClass、@ConditionalOnMissingBean |
| 内嵌容器 | ★★★★☆ | 高频 | Tomcat 启动原理、定制化、切换 Jetty/Undertow |
| Actuator | ★★★★☆ | 高频 | 端点、健康检查、Metrics 集成 |
| 自定义 Starter | ★★★★☆ | 高频 | 自动配置 + 条件注解 + 配置属性绑定 |
| 多环境配置 | ★★★☆☆ | 中频 | profile、@Profile、配置优先级 |
| 日志框架 | ★★★☆☆ | 中频 | Logback 配置、日志级别、链路追踪 |
| 测试 | ★★★★☆ | 高频 | @SpringBootTest、MockMvc、Testcontainers |
| Spring Boot 3 | ★★★★☆ | 高频 | Jakarta EE、虚拟线程、Observability |
| GraalVM Native | ★★★☆☆ | 中频 | AOT 编译、性能对比、局限性 |

---

## Spring Boot 在 AI 商城的角色

AI 商城（mall-micro-cloud）是一个典型的微服务架构，所有微服务都基于 Spring Boot 构建：

| 服务 | 模块名 | 启动类 | 核心依赖 |
|------|--------|--------|---------|
| 商品服务 | mall-product-service | ProductServiceApplication | Spring Web, JPA, Redis |
| 订单服务 | mall-order-service | OrderServiceApplication | Spring Web, MyBatis, RocketMQ |
| 秒杀服务 | mall-seckill-service | SeckillServiceApplication | Spring Web, Redis, RocketMQ |
| 用户服务 | mall-user-service | UserServiceApplication | Spring Web, JPA, Security |
| 搜索服务 | mall-search-service | SearchServiceApplication | Spring Web, Elasticsearch |
| 网关服务 | mall-gateway | GatewayApplication | Spring Cloud Gateway, Redis |

每个服务都遵循相同的 Spring Boot 模式：统一父工程管理版本、自动配置开箱即用、Actuator 暴露健康端点、多环境配置分离。

---

## 三个贯穿全文的核心思想

1. **约定大于配置**：Spring Boot 的默认值已经覆盖了 80% 的场景，只有特殊需求才需要自定义。
2. **自动配置背后是条件**：每个 @Conditional 注解都是一次"if 判断"，理解条件判断链就是理解自动配置。
3. **启动即生产**：从内嵌容器到 Actuator，Spring Boot 设计的每一个环节都在为"上线"做准备。

> 让我们从快速入门开始：从零搭建第一个 Spring Boot 应用。