# 推荐资源

## 书籍

### Nginx
- **《Nginx 实战：基于 Lua 的高性能 Web 开发》** -- 深入讲解 Nginx 核心原理与 Lua 扩展开发，适合进阶学习
- **《深入理解 Nginx：模块开发与架构解析》** -- 淘宝核心团队撰写，从源码层面分析 Nginx 架构设计
- **《精通 Nginx （第 2 版）》** -- 全面覆盖 Nginx 配置、性能优化、安全加固等实战内容

### 微服务架构
- **《微服务架构设计模式》**（Chris Richardson） -- 微服务领域经典之作，涵盖服务拆分、CQRS、Saga、API 网关等核心模式
- **《Spring Cloud Alibaba 微服务原理与实战》** -- 结合 Spring Cloud Alibaba 生态，深入讲解微服务架构实践
- **《凤凰架构：构筑可靠的大型分布式系统》** -- 周志明著，从架构演进角度讲解分布式系统设计

### 基础设施
- **《Sentinel 原理与实战》** -- 阿里中间件团队出品，全面解析流控、熔断、降级原理
- **《Nacos 原理与实战》** -- Nacos 官方核心开发人员撰写，覆盖注册中心与配置中心实现细节
- **《云原生基础架构：构建现代分布式系统》** -- 讲解容器化、Kubernetes、服务网格等云原生基础设施

### DevOps
- **《GitOps 实践指南》** -- 讲解以 Git 为单一事实来源的部署模式
- **《Prometheus 实战》** -- 监控系统实践指南，覆盖 PromQL、告警规则、Grafana 面板设计
- **《云原生 Java：Spring Boot、Spring Cloud 与 Cloud Foundry 弹性系统设计》** -- 云原生 Java 应用完整指南

---

## 在线课程

### 极客时间
- **《Spring Cloud Alibaba 微服务实战》** -- 从零搭建微服务架构，涵盖 Nacos、Sentinel、Seata、Gateway 等组件
- **《Nginx 核心知识 100 讲》** -- 陶辉主讲，系统讲解 Nginx 配置、架构、性能优化
- **《分布式事务原理与实战》** -- 深入讲解 Seata AT、TCC、Saga 等分布式事务方案
- **《云原生架构与 GitOps 实战》** -- 云原生技术栈实战，从 Docker 到 Kubernetes 再到 ArgoCD

### 慕课网
- **《Java 微服务架构实战：Spring Cloud Alibaba 全面解析》** -- 覆盖 15+ 个基础设施组件，附带完整项目代码
- **《Nginx 从入门到精通》** -- 从基础配置到高级应用，包含大量实战案例
- **《Docker + K8s 容器化部署实战》** -- 容器化部署微服务的完整流程

### 其他平台
- **Udemy "Spring Cloud Alibaba - Microservices with Nacos & Sentinel"** -- 英文课程，适合国际视角
- **Pluralsight "Microservices Architecture"** -- 微服务架构设计思维训练

---

## 官方文档

### Spring Cloud Alibaba
- **官方文档**：https://sca.aliyun.com/
- **GitHub 仓库**：https://github.com/alibaba/spring-cloud-alibaba
- **版本说明**：https://github.com/alibaba/spring-cloud-alibaba/wiki/版本说明

### Nacos
- **官方文档**：https://nacos.io/docs/
- **GitHub 仓库**：https://github.com/alibaba/nacos
- **Nacos 配置中心最佳实践**：https://nacos.io/docs/best-practice/
- **Nacos 2.x 一致性协议详解**：https://nacos.io/docs/architecture/

### Sentinel
- **官方文档**：https://sentinelguard.io/zh-cn/docs/
- **GitHub 仓库**：https://github.com/alibaba/Sentinel
- **Sentinel 控制台部署**：https://sentinelguard.io/zh-cn/docs/dashboard.html
- **Sentinel 热点限流**：https://sentinelguard.io/zh-cn/docs/parameter-flow-control.html

### Seata
- **官方文档**：https://seata.apache.org/zh-cn/docs/
- **GitHub 仓库**：https://github.com/seata/seata
- **Seata AT 模式详解**：https://seata.apache.org/zh-cn/docs/dev/mode/at-mode
- **Seata 部署指南**：https://seata.apache.org/zh-cn/docs/ops/deploy-guide/

### Prometheus & Grafana
- **Prometheus 文档**：https://prometheus.io/docs/
- **Grafana 文档**：https://grafana.com/docs/
- **PromQL 速查表**：https://promlabs.com/promql-cheat-sheet/
- **Grafana Dashboards 市场**：https://grafana.com/grafana/dashboards/

### Nginx
- **Nginx 官方文档**：https://nginx.org/en/docs/
- **Nginx 入门指南**：https://nginx.org/en/docs/beginners_guide.html
- **ngx_http_limit_req_module**：https://nginx.org/en/docs/http/ngx_http_limit_req_module.html

---

## 实践平台

### 在线环境（无需安装）
- **Play with Docker**（https://labs.play-with-docker.com/） -- 在线 Docker 环境，提供 4 小时免费会话，适合快速验证 Docker Compose 和容器化部署
- **Katacoda / Killercoda**（https://killercoda.com/） -- 交互式学习平台，提供 Kubernetes、Docker、Nginx 等场景的在线实验环境
- **Instruqt**（https://instruqt.com/） -- 企业级在线实验平台，部分课程免费

### 本地实践
- **Docker Desktop** -- 本地运行容器环境，支持 Windows / Mac / Linux，推荐用于本地开发测试
- **Minikube** -- 本地单节点 Kubernetes 集群，适合学习 K8s 基础概念
- **Kind（Kubernetes in Docker）** -- 在 Docker 容器中运行 Kubernetes 集群，启动速度快，适合 CI/CD 集成测试

### 沙箱环境
- **阿里云开发者实验室**（https://developer.aliyun.com/adc/labs/） -- 免费在线实验环境，包含 Nacos、Sentinel、Seata 等组件的实践沙箱
- **华为云沙箱实验室**（https://lab.huaweicloud.com/） -- 提供云原生相关实验环境

### 源码阅读
- **Nacos 源码**：https://github.com/alibaba/nacos
- **Sentinel 源码**：https://github.com/alibaba/Sentinel
- **Seata 源码**：https://github.com/seata/seata
- **Spring Cloud Gateway 源码**：https://github.com/spring-cloud/spring-cloud-gateway
- **推荐阅读顺序**：先读 Nacos（注册发现逻辑较简单）→ Sentinel（滑动窗口统计）→ Seata（分布式事务二阶段提交）

---

## 博客与社区

- **阿里中间件团队博客**：https://midawin.taobao.com/ -- 官方技术博客，经常发布 Nacos、Sentinel 等组件深度解析
- **Spring 官方博客**：https://spring.io/blog -- Spring Cloud Gateway 新特性发布
- **InfoQ 微服务专题**：https://www.infoq.cn/topic/microservice -- 微服务架构相关技术文章
- **掘金 Nacos 专栏**：https://juejin.cn/tag/Nacos -- 中文社区优质文章合集