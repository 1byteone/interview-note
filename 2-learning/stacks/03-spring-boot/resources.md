# 推荐资源

> Spring Boot 相关的官方文档、书籍、视频、开源项目。

---

## 一、书籍推荐

| 书名 | 难度 | 推荐理由 | 读完能解决什么问题 |
|------|------|---------|-------------------|
| 《Spring Boot 实战》（Craig Walls） | 入门 | Spring Boot 作者亲笔，覆盖自动配置、Actuator、测试 | 系统掌握 Spring Boot 核心功能 |
| 《Spring Boot 揭秘》（刘庆） | 进阶 | 深入源码分析自动配置和启动流程 | 理解自动配置和条件注解底层 |
| 《Spring 实战》（第 6 版） | 入门 | 全面覆盖 Spring 5 + Boot 2 + 微服务 | Spring 生态全景理解 |
| 《Spring Boot 3 整合开发实战》 | 进阶 | 针对 Spring Boot 3 新特性（Jakarta EE、虚拟线程） | 迁移指南和新特性实践 |
| 《深入浅出 Spring Boot 3.x》 | 进阶 | 源码分析 + 项目实战，涵盖 GraalVM | 面试深挖题和源码级理解 |

---

## 二、官方文档

| 资源 | 链接 | 说明 |
|------|------|------|
| Spring Boot 官方文档 | https://docs.spring.io/spring-boot/index.html | 最权威的参考，覆盖所有特性 |
| Spring Boot 参考指南 | https://docs.spring.io/spring-boot/docs/current/reference/ | 各版本参考文档 |
| Spring Initializr | https://start.spring.io/ | 项目脚手架，快速创建项目 |
| Spring Guides | https://spring.io/guides | 官方入门教程 |
| Spring Boot 3.0 Release Notes | https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-3.0-Release-Notes | 3.0 迁移必读 |

---

## 三、视频推荐

| 名称 | 平台 | 说明 |
|------|------|------|
| Spring Boot 基础教程（尚硅谷） | B站 | 从零开始，适合入门 |
| Spring Boot 源码分析（雷丰阳） | B站 | 深入自动配置和启动流程源码 |
| Spring Boot 3 新特性（江南一点雨） | B站 | 覆盖 Jakarta EE、虚拟线程等新特性 |
| Spring Boot + Vue 全栈项目实战 | B站 | 全栈项目，CRUD + 权限 + 部署 |
| 黑马程序员 Spring Boot 微服务实战 | B站 | 微服务 + Docker + K8s 部署 |

---

## 四、开源项目

| 项目 | 说明 | 推荐理由 |
|------|------|---------|
| Spring Boot 官方 Samples | https://github.com/spring-projects/spring-boot/tree/main/spring-boot-project/spring-boot-samples | 官方示例，覆盖各种场景 |
| mall（MacroZheng） | https://github.com/macrozheng/mall | 电商系统，Spring Boot + MyBatis + Redis |
| RuoYi-Vue | https://github.com/yangzongzhuan/RuoYi-Vue | 若依框架，RBAC 权限系统 |
| eladmin | https://github.com/elunez/eladmin | 后台管理系统，JPA + Redis + JWT |
| Spring Boot Admin | https://github.com/codecentric/spring-boot-admin | Actuator 可视化监控面板 |
| LayUI | https://gitee.com/pear-admin/Pear-Admin-Boot | 后台管理框架，适合快速开发 |

---

## 五、面试刷题

| 平台 | 说明 | 推荐题目 |
|------|------|---------|
| JavaGuide | https://javaguide.cn | Spring Boot 面试题全集 |
| 牛客网 | https://www.nowcoder.com | Spring Boot 专项练习 |
| 万题面试 | 模拟面试 | 按知识点分类的 Spring Boot 面试题 |
| Baeldung | https://www.baeldung.com/spring-boot | 英文教程，Spring Boot 全覆盖 |

---

## 六、学习路线建议

```
第一阶段（1-2 周）：基础入门
  ├── Spring Initializr 创建项目，跑通第一个 REST API
  ├── 掌握配置管理、多环境配置、DevTools
  └── 理解 IoC 容器和 DI 依赖注入

第二阶段（2-3 周）：深入原理
  ├── 阅读自动配置源码（AutoConfigurationImportSelector）
  ├── 理解条件注解家族（@ConditionalOnClass / @ConditionalOnMissingBean 等）
  ├── 掌握启动流程（SpringApplication.run 10 步）
  └── 自定义一个 Starter 并发布

第三阶段（1-2 周）：高级特性
  ├── Actuator + Micrometer + Prometheus + Grafana 监控体系
  ├── @SpringBootTest + MockMvc + Testcontainers 测试
  ├── Spring Boot 3 新特性：虚拟线程、Observability
  └── GraalVM Native Image 编译

第四阶段（1 周）：项目实战
  ├── MiniBlog 博客 API 完整实现（含测试）
  ├── 理解 AI 商城各微服务的 Spring Boot 配置
  └── 多环境配置 + 健康检查 + 优雅停机

第五阶段（考前 1 天）：面试冲刺
  ├── quick-revision 速记 30 个考点
  ├── deep-dive 源码级深挖
  ├── scenario 场景题（启动慢、内存泄漏）
  └── coding 手写 Starter / HealthIndicator / 测试
```

---

## 七、官方推荐工具

| 工具 | 用途 |
|------|------|
| Spring Boot CLI | 命令行快速创建和运行 Spring Boot 应用 |
| Spring Initializr | Web 界面快速生成项目骨架 |
| Spring Boot DevTools | 开发时热部署，提升开发效率 |
| Spring Boot Maven/Gradle Plugin | 打包、运行、构建原生镜像 |
| Spring Boot Actuator | 生产环境监控和管理 |

> 祝你面试顺利，Spring Boot 是 Java 后端的基础设施，值得深入掌握。