# 基础设施 — 网关 · 注册中心 · 限流 · 监控 · CI/CD

## 学习路径图

```
                         👶 新手路线                           🎯 进阶路线
┌─────────────────────────────────┐   ┌─────────────────────────────────────┐
│  ① 网络基础 (OSI / TCP / DNS)   │   │  ⑥ 微服务网关 (Spring Cloud       │
│  ② Nginx 基础 (反向代理/负载均衡)│   │     Gateway / Kong)                │
│  ③ Docker & Compose 入门        │   │  ⑦ 注册中心 (Nacos / Eureka)       │
│  ④ Linux 常用命令 & Shell 脚本  │   │  ⑧ 限流与熔断 (Sentinel /         │
│  ⑤ CI/CD 概念 (GitHub Actions)  │   │     Resilicence4j)自动扩容         │
│                                 │   │  ⑨ 监控体系 (Prometheus +          │
│                                 │   │     Grafana / ELK)                 │
│                                 │   │  ⑩ CI/CD 实战 (Docker Image       │
│                                 │   │     Build + K8s Deploy)            │
└───────────────┬─────────────────┘   └───────────────┬─────────────────────┘
                │                                      │
                └────────── 并行推进，互相补充 ──────────┘
```

## 前置知识

- **Spring Boot** — 理解微服务的基本开发方式，能够独立编写 REST API
- **Docker** — 掌握 Dockerfile 编写、镜像构建、容器运行等基础操作
- **Linux** — 熟悉常用命令（文件操作、进程管理、网络排查），能够阅读 Shell 脚本

> 若上述前置知识尚未掌握，建议先学习对应内容后再进入本模块。

## 面试高频考点一览表

| 考点 | 重要程度 | 常见问题 |
|------|----------|----------|
| 网关路由与过滤器 | ⭐⭐⭐⭐⭐ | Gateway 如何实现路由转发？过滤器链的执行顺序？ |
| 注册中心选型对比 | ⭐⭐⭐⭐⭐ | Nacos vs Eureka vs Consul 的差异与选型依据？ |
| 限流算法 | ⭐⭐⭐⭐ | 令牌桶 vs 漏桶 vs 滑动窗口？Sentinel 如何实现？ |
| 服务发现与健康检查 | ⭐⭐⭐⭐ | 客户端发现 vs 服务端发现？心跳机制如何设计？ |
| 熔断降级 | ⭐⭐⭐⭐ | 熔断状态机（Closed/Open/Half-Open）原理？ |
| 分布式配置中心 | ⭐⭐⭐⭐ | Nacos 配置管理如何实现动态刷新？ |
| 链路追踪 | ⭐⭐⭐ | Sleuth + Zipkin 的实现原理？ |
| 日志收集 | ⭐⭐⭐ | ELK 架构如何搭建？日志采集的性能损耗？ |
| CI/CD 流水线 | ⭐⭐⭐ | GitHub Actions 如何编排构建、测试、部署？ |
| 容器化部署 | ⭐⭐⭐ | Docker 多阶段构建与 K8s 部署策略？ |

## 基础设施在 AI 商城的角色

AI 商城是一个典型的微服务架构系统，包含用户服务、商品服务、订单服务、AI 推荐服务等多个独立部署的模块。基础设施层贯穿所有服务，扮演着"支撑骨架"的角色：

- **网关** — 所有请求的统一入口，负责路由分发、认证鉴权、跨域处理、请求日志；AI 商城的前端页面、移动端 SDK 以及第三方 API 调用都经过网关中转
- **注册中心** — 服务启动时自动注册，下线时自动摘除；AI 推荐服务调用商品服务时，通过注册中心获取可用的服务实例列表，实现负载均衡与故障转移
- **限流与熔断** — 当 AI 推荐 API 被高频调用或秒杀活动流量突增时，限流保护后端数据库不被击穿；熔断机制防止单点故障的雪崩效应
- **监控体系** — 实时采集各服务的 QPS、响应时间、错误率、JVM 指标；ELK 集中管理日志，方便排查线上问题
- **CI/CD** — 代码提交后自动触发构建、单元测试、镜像打包、部署到测试环境/生产环境，保障交付效率与质量

简言之，没有基础设施，微服务就是一盘散沙；有了基础设施，AI 商城才能稳定、高效、可运维地运行在线上环境之中。

---

## 📖 导航

| ← 上一篇 | 📚 目录 | 下一篇 → |
|----------|---------|----------|
| [← Linux](../11-linux/README.md) | [📚 总目录](../../README-learning.md) | [开发工具 →](../13-dev-tools/README.md) |

**相关技术栈：**
- [03-Spring Boot](../03-spring-boot/README.md) — 基础设施的网关、限流、监控直接服务 Spring Boot 微服务
- [10-Docker](../10-docker/README.md) — Docker 容器化是基础设施 CI/CD 流水线的核心载体

---

## 项目剖析深度参考

本 learn 文档提供理论基础，以下 `docs/tech-stack-analysis/` 文档提供**真实项目中的落地代码**：

| 本 learn 核心内容 | 对应项目剖析 | 重点看什么 |
|------------------|------------|-----------|
| Nacos + Gateway 路由鉴权 | [01-NACOS-GATEWAY.md](../../../5-research/tech-stack-analysis/mall-micro-cloud/01-NACOS-GATEWAY.md) | `AuthGatewayFilterFactory` + `RtGlobalFilter` |
| Sentinel 限流 + Seata 事务 | [04-ORDER-SEATA.md](../../../5-research/tech-stack-analysis/mall-micro-cloud/04-ORDER-SEATA.md) | `@GlobalTransactional` + Feign 分布式事务 |
| 微服务部署架构 | [00-OVERVIEW.md](../../../5-research/tech-stack-analysis/mall-micro-cloud/00-OVERVIEW.md) | 12 个微服务的职责边界与路由配置 |
| ElasticJob 定时任务 | [11-SCHEDULER-BLOOMFILTER.md](../../../5-research/tech-stack-analysis/mall-micro-cloud/11-SCHEDULER-BLOOMFILTER.md) | `LoadSeckillProductTask` / `PayCheckTask` |