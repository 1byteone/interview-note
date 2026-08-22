# Spring Boot Admin + Docker入门：第一个监控面板

> 本文是 ruoyi-ai 技术栈深度剖析系列之监控部署篇（第1篇），定位为 Level 1 入门篇，带领读者从零搭建 Spring Boot Admin 监控面板，并配合 Docker 容器化部署，快速建立对微服务可观测性的直观认知。

---

## 1. 项目背景

### 1.1 为什么需要监控

在单体应用时代，一个应用部署在一台服务器上，出了问题直接登录服务器查看日志、重启服务即可。但随着微服务架构的普及，一个业务系统可能拆分为十几个甚至几十个微服务模块，每个模块又可能部署多个副本。以 ruoyi-ai 项目为例，它包含了 `ruoyi-gateway`、`ruoyi-auth`、`ruoyi-system`、`ruoyi-gen`、`ruoyi-job`、`ruoyi-ai` 等多个服务，如果再算上 Nacos、Redis、MySQL、MinIO 等基础设施，整个系统的组件数量会轻松超过 10 个。

在这种复杂度下，传统运维方式面临以下痛点：

- **服务状态不可见**：某个服务是否在正常运行？是否已经宕机？靠人工轮询检查每个服务是不现实的。
- **性能问题定位困难**：用户反馈"系统变慢了"，但究竟是哪个服务慢？是数据库慢还是网络慢？没有监控数据就只能靠猜。
- **资源耗尽预警缺失**：内存泄漏、CPU 飙升、磁盘写满等问题，如果等到用户反馈才发现，往往已经造成了较大影响。
- **版本变更缺乏反馈**：上线新版本后，服务是否健康？接口响应时间是否有变化？没有监控就无法量化。

### 1.2 Spring Boot Admin 的定位

Spring Boot Admin（简称 SBA）是一个用于管理和监控 Spring Boot 应用程序的开源项目。它由两个核心组件构成：

- **Admin Server**：一个独立的 Web 应用，负责收集和展示各个微服务的监控数据。
- **Admin Client**：嵌入在每个微服务中，负责将自身状态报告给 Admin Server。

SBA 的定位是"轻量级监控解决方案"。与 Prometheus + Grafana 这种重量级监控体系相比，SBA 的优势在于：

- **零额外存储**：Admin Server 本身不存储历史数据，监控数据来自各服务的 Actuator 端点实时采集。
- **开箱即用**：只要 Spring Boot 应用引入了 Actuator，再配置一个 URL 地址，就能接入监控。
- **信息丰富**：除了基本的健康状态，还能查看 JVM 内存、线程、日志级别、环境变量、Bean 信息等。
- **与 Spring Boot 深度集成**：版本兼容性好，配置方式与 Spring Boot 保持一致。

### 1.3 本篇目标

本文的目标是让读者：

1. 理解 Spring Boot Admin + Actuator 的核心原理。
2. 从零搭建一个 Admin Server 项目和一个 Admin Client 项目。
3. 使用 Docker Compose 将监控系统容器化部署。
4. 在浏览器中访问监控面板，观察各项指标。
5. 将所学知识映射到 ruoyi-ai 项目的实际监控需求中。

---

## 2. 核心概念

### 2.1 Actuator 端点

Spring Boot Actuator 是 Spring Boot 提供的一个用于监控和管理应用的模块。它通过一系列 HTTP 端点暴露应用的内部状态信息。

**核心端点一览：**

| 端点路径 | 用途 | 是否默认开放 |
|---------|------|-----------|
| `/actuator/health` | 显示应用健康状态（UP/DOWN） | 是 |
| `/actuator/info` | 显示自定义的应用信息 | 是 |
| `/actuator/metrics` | 显示应用指标（内存、GC、线程等） | 是 |
| `/actuator/env` | 显示环境属性 | 否 |
| `/actuator/beans` | 显示所有 Spring Bean | 否 |
| `/actuator/loggers` | 查看和修改日志级别 | 否 |
| `/actuator/threaddump` | 执行线程转储 | 否 |
| `/actuator/heapdump` | 下载堆转储文件 | 否 |
| `/actuator/httptrace` | 显示 HTTP 请求追踪信息 | 否 |

**关键端点详解：**

**`/actuator/health`**：这是最常用的端点。它返回应用的健康状态，格式如下：

```json
{
  "status": "UP",
  "components": {
    "db": {
      "status": "UP",
      "details": {
        "database": "MySQL",
        "validationQuery": "isValid()"
      }
    },
    "redis": {
      "status": "UP",
      "details": {
        "version": "6.2.6"
      }
    },
    "diskSpace": {
      "status": "UP",
      "details": {
        "total": 499963174912,
        "free": 328718442496,
        "threshold": 10485760
      }
    }
  }
}
```

当所有组件状态均为 `UP` 时，整体状态为 `UP`。如果某个组件异常（如数据库连接失败），整体状态会变为 `DOWN`，负载均衡器或容器编排工具据此可以自动摘除故障节点。

**`/actuator/metrics`**：返回应用的运行时指标。先列出所有可用的指标名称：

```json
{
  "names": [
    "jvm.memory.used",
    "jvm.memory.max",
    "jvm.gc.pause",
    "jvm.threads.live",
    "system.cpu.usage",
    "process.uptime",
    "http.server.requests"
  ]
}
```

然后可以通过 `/actuator/metrics/{name}` 获取具体指标的数值：

```json
{
  "name": "jvm.memory.used",
  "measurements": [
    {
      "statistic": "VALUE",
      "value": 256000000
    }
  ],
  "availableTags": [
    {
      "tag": "area",
      "values": ["heap", "nonheap"]
    }
  ]
}
```

**`/actuator/info`**：返回自定义的应用信息，通常在 `application.yml` 中配置：

```yaml
# 应用信息配置
info:
  # 应用名称
  app:
    # 应用名称
    name: "@project.name@"
    # 应用版本
    version: "@project.version@"
    # 应用描述
    description: "@project.description@"
```

### 2.2 Admin Server / Client 架构

Spring Boot Admin 采用**客户端-服务器（C/S）架构**：

```
┌─────────────────────────────────────────────────────────┐
│                    Admin Server                         │
│  ┌──────────────────────────────────────────────────┐   │
│  │  SBA Web UI (Vue.js)                            │   │
│  │  - 服务列表 / 健康状态 / 指标图表 / 日志级别     │   │
│  └──────────────────────────────────────────────────┘   │
│                         │                                │
│  ┌──────────────────────────────────────────────────┐   │
│  │  SBA Server Core                                 │   │
│  │  - 接收客户端注册 / 定期拉取端点 / 状态管理      │   │
│  └──────────────────────────────────────────────────┘   │
└────────────────────────┬────────────────────────────────┘
                         │ HTTP
    ┌────────────────────┼────────────────────┐
    │                    │                    │
┌───▼──────────┐  ┌─────▼────────┐  ┌───────▼────────┐
│ Admin Client  │  │ Admin Client  │  │  Admin Client  │
│ (ruoyi-auth)  │  │(ruoyi-system) │  │  (ruoyi-ai)    │
│              │  │              │  │                │
│ /actuator/*  │  │ /actuator/*  │  │ /actuator/*    │
└──────────────┘  └──────────────┘  └────────────────┘
```

**工作流程：**

1. **注册阶段**：每个 Admin Client 应用在启动时，会向 Admin Server 的 `/instances` 端点发送注册请求，提交自己的服务地址（如 `http://192.168.1.100:8080/actuator`）。
2. **采集阶段**：Admin Server 定期（默认每 10 秒）通过 HTTP 请求每个已注册客户端的 Actuator 端点，获取健康信息、指标数据、环境属性等。
3. **展示阶段**：Admin Server 将采集到的数据聚合后，通过自身的 Web UI 展示给运维人员。
4. **通知阶段**：当服务状态发生变化（如从 UP 变为 DOWN 或 OFFLINE），Admin Server 可以触发通知（邮件、短信、钉钉等）。

**通信方式：**

Admin Client 向 Admin Server 注册时，需要提供自己的 Actuator 端点 URL。Admin Server 通过这个 URL 主动拉取数据，这要求 Admin Server 能够**网络可达**所有 Admin Client。如果 Client 部署在 Docker 容器中且网络配置不当，可能会出现"注册成功但无法获取数据"的问题，这一点在后面的 Docker 部署部分会重点讨论。

### 2.3 Docker 容器化

Docker 是一种容器化技术，它可以将应用及其依赖打包到一个轻量级、可移植的容器中，确保应用在任何环境中都能以相同的方式运行。

**在监控场景下，Docker 的价值体现在：**

- **环境一致性**：开发环境、测试环境、生产环境的监控配置完全一致，避免"我本地能跑"的问题。
- **快速部署**：一条 `docker-compose up -d` 命令即可启动整个监控栈。
- **资源隔离**：每个容器有独立的 CPU、内存限制，避免监控系统影响业务系统。
- **弹性伸缩**：结合 Kubernetes 或 Docker Swarm，可以自动扩缩容监控组件。

---

## 3. 从零搭建代码

### 3.1 项目结构

```
spring-boot-admin-demo/
├── admin-server/              # Admin Server 项目
│   ├── pom.xml
│   └── src/
│       └── main/
│           ├── java/
│           │   └── com/example/admin/
│           │       └── AdminServerApplication.java
│           └── resources/
│               └── application.yml
├── admin-client/              # Admin Client 项目
│   ├── pom.xml
│   └── src/
│       └── main/
│           ├── java/
│           │   └── com/example/client/
│           │       └── ClientApplication.java
│           └── resources/
│               └── application.yml
├── docker-compose.yml         # Docker Compose 编排文件
└── Dockerfile                 # Admin Server 的 Dockerfile
```

### 3.2 Admin Server 项目

#### 3.2.1 pom.xml

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <!-- 指定 Maven 模型版本，4.0.0 是 Maven 2/3 的标准版本 -->
    <modelVersion>4.0.0</modelVersion>

    <!-- 使用 Spring Boot 父工程，统一管理依赖版本 -->
    <parent>
        <groupId>org.springframework.boot</groupId>
                        <!-- Spring Boot 父工程 -->
        <artifactId>spring-boot-starter-parent</artifactId>
                        <!-- 使用 3.2.0 版本，支持 Java 17 和最新特性 -->
        <version>3.2.0</version>
        <relativePath/> <!-- 从仓库中查找父工程，不依赖本地路径 -->
    </parent>

    <!-- 项目坐标定义 -->
    <groupId>com.example</groupId>
    <!-- 项目名称 -->
    <artifactId>admin-server</artifactId>
    <!-- 版本号 -->
    <version>1.0.0</version>

    <!-- 项目属性配置 -->
    <properties>
        <!-- Java 编译版本 -->
        <java.version>17</java.version>
        <!-- Spring Boot Admin 版本，需与 Spring Boot 3.x 兼容 -->
        <spring-boot-admin.version>3.2.0</spring-boot-admin.version>
    </properties>

    <!-- 项目依赖 -->
    <dependencies>
        <!-- Spring Boot Admin Server 起步依赖 -->
        <!-- 包含服务器端所有功能：接收客户端注册、管理 Web UI -->
        <dependency>
            <groupId>de.codecentric</groupId>
            <artifactId>spring-boot-admin-starter-server</artifactId>
            <version>${spring-boot-admin.version}</version>
        </dependency>

        <!-- Spring Boot Web 起步依赖 -->
        <!-- 提供 Tomcat 嵌入式容器和 RESTful API 支持 -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>

        <!-- Spring Boot Actuator 起步依赖 -->
        <!-- 提供监控端点，Admin Server 自身也需要暴露健康信息 -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>

        <!-- Spring Security 安全防护 -->
        <!-- 为 Admin Server 的管理界面提供登录认证 -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-security</artifactId>
        </dependency>
    </dependencies>

    <!-- 构建配置 -->
    <build>
        <plugins>
            <!-- Spring Boot Maven 插件 -->
            <!-- 将应用打包为可执行的 JAR 文件 -->
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

#### 3.2.2 application.yml

```yaml
# 应用基本配置
spring:
  # 应用名称，Admin Server 用于标识自身
  application:
    name: admin-server
  # 安全配置，为管理界面设置登录认证
  security:
    user:
      # 登录用户名
      name: admin
      # 登录密码，生产环境应使用加密方式存储
      password: admin123

# 服务器配置
server:
  # Admin Server 的监听端口
  port: 9090

# Spring Boot Admin 配置
spring:
  boot:
    admin:
      # Admin Server 自身配置
      server:
        # 启用延迟关闭，确保服务注册信息在重启前完成清理
        eviction:
          # 服务过期检查间隔，单位毫秒
          interval: 5000
          # 服务过期时间，超过此时间未收到心跳则标记为 OFFLINE
          immediate: true

# Actuator 端点配置
management:
  # 端点暴露配置
  endpoints:
    web:
      # Web 方式暴露的端点路径前缀
      base-path: /actuator
      # 暴露所有端点（生产环境应根据需要选择性暴露）
      exposure:
        # 暴露所有端点，包括 health、info、metrics 等
        include: "*"
  # 端点详细配置
  endpoint:
    # 健康端点配置
    health:
      # 显示详细的健康信息（如数据库、Redis 等组件的健康状态）
      show-details: always
    # 信息端点配置
    info:
      # 启用信息端点
      enabled: true

# 应用信息，通过 /actuator/info 端点暴露
info:
  app:
    # 应用名称，从 Maven POM 中读取
    name: "@project.name@"
    # 应用版本
    version: "@project.version@"
    # 应用描述
    description: "Spring Boot Admin Server - 监控服务器"
```

#### 3.2.3 启动类

```java
package com.example.admin;

// 导入 Spring Boot Admin Server 的注解
import de.codecentric.boot.admin.server.config.EnableAdminServer;
// 导入 Spring Boot 启动类注解
import org.springframework.boot.SpringApplication;
// 导入 Spring Boot 自动配置注解
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Admin Server 启动类
 *
 * 职责：启动 Spring Boot Admin Server 应用，
 * 开启 Admin Server 自动配置，接收客户端注册并提供 Web 管理界面。
 *
 * 关键注解说明：
 * - @SpringBootApplication：Spring Boot 核心注解，组合了 @Configuration、
 *   @EnableAutoConfiguration 和 @ComponentScan
 * - @EnableAdminServer：激活 Spring Boot Admin Server 功能，
 *   开启客户端注册端点和管理界面路由
 */
@SpringBootApplication
// 启用 Admin Server 功能
@EnableAdminServer
public class AdminServerApplication {

    /**
     * 应用入口方法
     *
     * @param args 命令行参数，可传入 --server.port=9090 等参数覆盖配置
     */
    public static void main(String[] args) {
        // 启动 Spring Boot 应用
        // 参数：启动类 Class 对象，命令行参数数组
        SpringApplication.run(AdminServerApplication.class, args);
        System.out.println("==============================================");
        System.out.println("  Admin Server 启动成功！");
        System.out.println("  访问地址：http://localhost:9090");
        System.out.println("  登录用户名：admin");
        System.out.println("  登录密码：admin123");
        System.out.println("==============================================");
    }
}
```

#### 3.2.4 安全配置类

```java
package com.example.admin.config;

// 导入安全配置相关类
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

/**
 * Admin Server 安全配置类
 *
 * 职责：配置 Spring Security 安全策略，
 * 保护 Admin Server 管理界面不被未授权访问。
 *
 * 核心逻辑：
 * 1. 所有请求需要经过认证（除静态资源和登录页面外）
 * 2. 启用 CSRF 保护，防止跨站请求伪造
 * 3. 允许 iframe 加载管理界面（默认 X-Frame-Options 会阻止）
 */
@Configuration
// 启用 Web 安全功能
@EnableWebSecurity
public class AdminSecurityConfig {

    /**
     * 配置安全过滤器链
     *
     * 定义哪些路径需要认证、哪些可以公开访问、
     * 以及登录页面和 CSRF 的配置方式。
     *
     * @param http HttpSecurity 对象，用于构建安全配置
     * @return SecurityFilterChain 安全过滤器链实例
     * @throws Exception 配置异常
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        // 开始配置 HTTP 安全策略
        http
            // 配置请求授权规则
            .authorizeHttpRequests(auth -> auth
                // 静态资源路径，允许所有用户访问（无需登录）
                .requestMatchers("/assets/**", "/login/**", "/instances/**").permitAll()
                // 所有其他请求需要认证
                .anyRequest().authenticated()
            )
            // 配置表单登录
            .formLogin(form -> form
                // 登录页面路径
                .loginPage("/login")
                // 登录成功后跳转路径
                .defaultSuccessUrl("/", true)
                // 允许所有用户访问登录页面
                .permitAll()
            )
            // 配置 CSRF 保护
            .csrf(csrf -> csrf
                // 使用 Cookie 存储 CSRF Token
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                // 忽略实例注册端点的 CSRF 保护（客户端注册时需要）
                .ignoringRequestMatchers("/instances/**", "/actuator/**")
            )
            // 配置请求头
            .headers(headers -> headers
                // 允许 iframe 加载管理界面
                // Spring Boot Admin 的 UI 使用了 iframe 来展示内容
                .frameOptions(frame -> frame.sameOrigin()
            ));

        // 构建并返回安全过滤器链
        return http.build();
    }
}
```

### 3.3 Admin Client 项目

#### 3.3.1 pom.xml

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <!-- Maven 模型版本 -->
    <modelVersion>4.0.0</modelVersion>

    <!-- 使用 Spring Boot 父工程 -->
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.2.0</version>
        <relativePath/>
    </parent>

    <!-- 项目坐标 -->
    <groupId>com.example</groupId>
    <artifactId>admin-client</artifactId>
    <version>1.0.0</version>

    <!-- 项目属性 -->
    <properties>
        <java.version>17</java.version>
        <spring-boot-admin.version>3.2.0</spring-boot-admin.version>
    </properties>

    <!-- 依赖配置 -->
    <dependencies>
        <!-- Spring Boot Admin Client 起步依赖 -->
        <!-- 负责将当前应用注册到 Admin Server -->
        <dependency>
            <groupId>de.codecentric</groupId>
            <artifactId>spring-boot-admin-starter-client</artifactId>
            <version>${spring-boot-admin.version}</version>
        </dependency>

        <!-- Spring Boot Web 起步依赖 -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>

        <!-- Spring Boot Actuator 起步依赖 -->
        <!-- 暴露监控端点供 Admin Server 采集 -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>

        <!-- Spring Data Redis 依赖 -->
        <!-- 模拟业务服务中常用的 Redis 集成 -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-redis</artifactId>
        </dependency>

        <!-- MySQL 驱动 -->
        <!-- 模拟业务服务中常用的数据库集成 -->
        <dependency>
            <groupId>com.mysql</groupId>
            <artifactId>mysql-connector-j</artifactId>
            <scope>runtime</scope>
        </dependency>

        <!-- JDBC 起步依赖 -->
        <!-- 提供数据源配置和健康检查支持 -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-jdbc</artifactId>
        </dependency>
    </dependencies>

    <!-- 构建配置 -->
    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

#### 3.3.2 application.yml

```yaml
# 应用基本配置
spring:
  # 应用名称，Admin Server 面板上显示的名称
  application:
    name: admin-client-demo
  # 数据源配置，模拟业务服务连接数据库
  datasource:
    # MySQL 数据库连接 URL
    url: jdbc:mysql://mysql:3306/admin_demo?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai
    # 数据库用户名
    username: root
    # 数据库密码
    password: root123
    # 数据库驱动类
    driver-class-name: com.mysql.cj.jdbc.Driver
  # Redis 配置，模拟业务服务连接 Redis
  data:
    redis:
      # Redis 服务器地址，容器内使用服务名
      host: redis
      # Redis 端口
      port: 6379
      # Redis 密码
      password: redis123
      # Redis 连接超时时间
      timeout: 5000
  # Spring Boot Admin Client 配置
  boot:
    admin:
      client:
        # Admin Server 的访问地址，注册时使用
        url: http://admin-server:9090
        # 注册时使用的用户名（Admin Server 的安全认证）
        username: admin
        # 注册时使用的密码
        password: admin123
        # 注册实例信息
        instance:
          # 客户端自身的主机名，在 Docker 环境中使用容器名
          host: admin-client-demo
          # 客户端自身端口
          port: 8081
          # 客户端自身的基础 URL，优先使用此地址
          service-base-url: http://admin-client-demo:8081
          # 管理端基础 URL，即 Actuator 端点的基础路径
          management-base-url: http://admin-client-demo:8081
          # 健康检查 URL，Admin Server 通过此 URL 检查客户端健康状态
          health-url: http://admin-client-demo:8081/actuator/health

# 服务器配置
server:
  # 客户端应用端口
  port: 8081

# Actuator 端点配置
management:
  # 端点暴露配置
  endpoints:
    web:
      # Actuator 端点基础路径
      base-path: /actuator
      # 暴露端点列表
      exposure:
        # 暴露所有端点
        include: "*"
  # 端点详细配置
  endpoint:
    # 健康端点
    health:
      # 显示详细的健康信息
      show-details: always
    # 关闭端点（生产环境建议禁用）
    shutdown:
      enabled: false
    # 指标端点
    metrics:
      # 启用指标采集
      enabled: true
    # 信息端点
    info:
      enabled: true

# 应用信息
info:
  app:
    name: "@project.name@"
    version: "@project.version@"
    description: "Spring Boot Admin Client 示例应用"
```

#### 3.3.3 启动类

```java
package com.example.client;

// 导入 Spring Boot 相关注解
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
// 导入健康检查相关类
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
// 导入 Bean 注解
import org.springframework.context.annotation.Bean;

/**
 * Admin Client 启动类
 *
 * 职责：启动一个 Spring Boot 应用，并自动注册到 Admin Server。
 * 同时模拟业务服务，提供自定义健康检查指标。
 *
 * 关键点：
 * - 引入 spring-boot-admin-starter-client 后，应用会自动
 *   向配置的 Admin Server 发送注册请求
 * - 自定义 HealthIndicator 可以展示业务相关的健康状态
 */
@SpringBootApplication
public class ClientApplication {

    /**
     * 应用入口方法
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        // 启动 Spring Boot 应用
        SpringApplication.run(ClientApplication.class, args);
        System.out.println("==============================================");
        System.out.println("  Admin Client 启动成功！");
        System.out.println("  服务端口：8081");
        System.out.println("  已注册到 Admin Server：http://admin-server:9090");
        System.out.println("==============================================");
    }

    /**
     * 自定义健康检查指示器
     *
     * 演示如何添加自定义的健康检查逻辑。
     * 在实际业务中，可以检查第三方 API 连通性、
     * 队列积压情况、业务数据完整性等。
     *
     * 健康检查结果会出现在 /actuator/health 端点的
     * components 字段中，并在 Admin Server 面板上展示。
     *
     * @return HealthIndicator 健康检查器实例
     */
    @Bean
    public HealthIndicator customHealthIndicator() {
        // 返回一个 HealthIndicator 的 Lambda 表达式实现
        return () -> {
            // 模拟业务健康检查逻辑
            // 在实际项目中，这里可以检查：
            // 1. 第三方 API 是否可以正常调用
            // 2. 消息队列是否有大量积压
            // 3. 缓存数据是否过期
            // 4. 定时任务是否正常执行等

            // 模拟检查结果：假设一切正常
            boolean businessHealthy = true;

            if (businessHealthy) {
                // 返回 UP 状态，并附带业务详情
                return Health.up()
                    // 添加自定义详情字段
                    .withDetail("service", "用户服务")
                    .withDetail("apiStatus", "正常")
                    .withDetail("responseTime", "15ms")
                    .withDetail("lastCheck", System.currentTimeMillis())
                    // 构建健康对象
                    .build();
            } else {
                // 返回 DOWN 状态，并附带错误信息
                return Health.down()
                    .withDetail("service", "用户服务")
                    .withDetail("error", "第三方 API 连接超时")
                    .withDetail("retryCount", 3)
                    .build();
            }
        };
    }
}
```

#### 3.3.4 测试控制器

```java
package com.example.client.controller;

// 导入 REST 控制器相关注解
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 测试控制器
 *
 * 职责：提供简单的 REST 接口，用于在 Admin Server 面板中
 * 观察 HTTP 请求追踪和指标数据。
 *
 * 启动后，频繁访问此接口可以在 Admin Server 面板的
 * "HTTP 追踪"功能中看到请求记录，并在 "指标" 功能中
 * 观察到 http.server.requests 指标的变化。
 */
@RestController
// 控制器基础路径
@RequestMapping("/api")
public class DemoController {

    /**
     * 健康检查接口
     *
     * 返回当前时间戳和状态信息，
     * 用于验证客户端应用正常运行。
     *
     * @return Map 包含状态信息的键值对
     */
    @GetMapping("/hello")
    public Map<String, Object> hello() {
        // 创建结果 Map
        Map<String, Object> result = new HashMap<>();
        // 设置状态信息
        result.put("message", "Hello from Admin Client!");
        // 设置当前时间
        result.put("timestamp", LocalDateTime.now().toString());
        // 设置应用名称
        result.put("application", "admin-client-demo");
        // 返回结果
        return result;
    }

    /**
     * 模拟耗时操作接口
     *
     * 通过 Thread.sleep 模拟业务处理耗时，
     * 用于观察 Admin Server 面板中的响应时间指标。
     *
     * @return Map 包含处理结果的键值对
     * @throws InterruptedException 线程中断异常
     */
    @GetMapping("/slow")
    public Map<String, Object> slowOperation() throws InterruptedException {
        // 记录开始时间
        long startTime = System.currentTimeMillis();

        // 模拟业务处理耗时，随机等待 500-2000 毫秒
        Thread.sleep(500 + (long) (Math.random() * 1500));

        // 计算处理耗时
        long elapsed = System.currentTimeMillis() - startTime;

        // 创建结果 Map
        Map<String, Object> result = new HashMap<>();
        // 设置处理结果
        result.put("message", "Slow operation completed");
        // 设置耗时
        result.put("elapsedMs", elapsed);
        // 返回结果
        return result;
    }
}
```

### 3.4 Dockerfile

```dockerfile
# ============================================
# Dockerfile - Spring Boot Admin Server 镜像构建
# ============================================

# 第一阶段：构建阶段
# 使用 Maven 镜像作为构建环境，编译 Java 源码为可执行 JAR 包
# 这种多阶段构建方式可以减小最终镜像体积
FROM maven:3.9-eclipse-temurin-17 AS builder

# 设置工作目录，所有后续指令在此目录下执行
WORKDIR /build

# 复制 pom.xml 到工作目录
# 先复制 pom.xml 是为了利用 Docker 的构建缓存：
# 只要 pom.xml 没变，依赖下载步骤就会命中缓存，加快构建速度
COPY admin-server/pom.xml .

# 下载项目依赖（不编译源码）
# -q 表示安静模式，减少输出
# -B 表示批处理模式，不使用交互式输入
RUN mvn dependency:go-offline -q -B

# 复制所有源代码到工作目录
COPY admin-server/src ./src

# 编译并打包项目，跳过测试以加快构建速度
# -DskipTests 跳过单元测试
RUN mvn package -DskipTests -q -B

# ============================================
# 第二阶段：运行阶段
# 使用轻量级的 JRE 镜像，减小最终镜像体积
FROM eclipse-temurin:17-jre-alpine

# 设置工作目录
WORKDIR /app

# 从构建阶段复制编译好的 JAR 文件
# --from=builder 表示从第一阶段复制
COPY --from=builder /build/target/*.jar app.jar

# 暴露应用的监听端口，与 application.yml 中配置的端口一致
EXPOSE 9090

# 设置容器启动时执行的命令
# 使用 exec 形式的 CMD 指令，确保应用能正确处理 SIGTERM 信号
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### 3.5 docker-compose.yml

```yaml
# ============================================
# Docker Compose 编排文件
# 版本：3.8，支持 Docker Compose 的核心功能
# ============================================
version: "3.8"

# ============================================
# 服务定义
# 定义了本文档中所有需要运行的容器服务
# ============================================
services:
  # ------------------------------------------
  # MySQL 数据库服务
  # 模拟业务服务依赖的数据库
  # ------------------------------------------
  mysql:
    # 使用 MySQL 8.0 官方镜像
    image: mysql:8.0
    # 容器名称，便于在 Docker 网络中通过名称访问
    container_name: ruoyi-mysql
    # 环境变量配置
    environment:
      # MySQL root 用户密码
      - MYSQL_ROOT_PASSWORD=root123
      # 自动创建的数据库名称
      - MYSQL_DATABASE=admin_demo
      # 时区设置
      - TZ=Asia/Shanghai
    # 端口映射，宿主机 3306 映射到容器 3306
    ports:
      - "3307:3306"
    # 数据卷挂载，持久化数据库文件
    volumes:
      # 将宿主机目录挂载到容器内的 MySQL 数据目录
      - ./data/mysql:/var/lib/mysql
      # 初始化 SQL 脚本，首次启动时自动执行
      - ./init.sql:/docker-entrypoint-initdb.d/init.sql
    # 健康检查配置，Docker 会定期检查服务是否健康
    healthcheck:
      # 使用 MySQL 自带的健康检查命令
      test: ["CMD", "mysqladmin", "ping", "-h", "localhost"]
      # 检查间隔时间
      interval: 30s
      # 超时时间
      timeout: 10s
      # 重试次数
      retries: 3
      # 启动后等待时间
      start_period: 40s
    # 网络配置
    networks:
      - monitor-net
    # 资源限制
    deploy:
      resources:
        limits:
          # CPU 限制
          cpus: "1.0"
          # 内存限制
          memory: 512M

  # ------------------------------------------
  # Redis 缓存服务
  # 模拟业务服务依赖的缓存
  # ------------------------------------------
  redis:
    # 使用 Redis 7.0 官方镜像
    image: redis:7.0-alpine
    # 容器名称
    container_name: ruoyi-redis
    # 启动命令，带密码认证
    command: redis-server --requirepass redis123 --appendonly yes
    # 端口映射
    ports:
      - "6379:6379"
    # 数据卷挂载，持久化 Redis 数据
    volumes:
      - ./data/redis:/data
    # 健康检查
    healthcheck:
      # 使用 Redis CLI 执行 ping 命令检查连通性
      test: ["CMD", "redis-cli", "-a", "redis123", "ping"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 20s
    # 网络配置
    networks:
      - monitor-net
    deploy:
      resources:
        limits:
          cpus: "0.5"
          memory: 256M

  # ------------------------------------------
  # Spring Boot Admin Server
  # 监控系统的核心，负责接收客户端注册和展示数据
  # ------------------------------------------
  admin-server:
    # 从当前目录下的 Dockerfile 构建镜像
    build:
      # 构建上下文目录
      context: .
      # Dockerfile 路径
      dockerfile: Dockerfile
    # 容器名称
    container_name: admin-server
    # 端口映射
    ports:
      - "9090:9090"
    # 环境变量
    environment:
      # Spring 配置生效的 profile
      - SPRING_PROFILES_ACTIVE=docker
      # 时区
      - TZ=Asia/Shanghai
    # 依赖关系，确保 MySQL 和 Redis 先启动
    depends_on:
      mysql:
        # 等待 MySQL 健康检查通过后才启动
        condition: service_healthy
      redis:
        # 等待 Redis 健康检查通过后才启动
        condition: service_healthy
    # 网络配置
    networks:
      - monitor-net
    deploy:
      resources:
        limits:
          cpus: "1.0"
          memory: 512M

  # ------------------------------------------
  # Spring Boot Admin Client
  # 被监控的示例应用，自动注册到 Admin Server
  # ------------------------------------------
  admin-client-demo:
    # 使用 OpenJDK 17 镜像直接运行 JAR 包
    image: eclipse-temurin:17-jre-alpine
    # 容器名称
    container_name: admin-client-demo
    # 端口映射
    ports:
      - "8081:8081"
    # 启动命令，直接运行 JAR 包
    # 也可以像 admin-server 一样通过 Dockerfile 构建
    command: >
      java -jar /app/admin-client.jar
      --spring.profiles.active=docker
    # 数据卷挂载，将本地的 JAR 包挂载到容器中
    volumes:
      # 将本地编译好的 JAR 包挂载到容器内
      - ./admin-client/target/admin-client-1.0.0.jar:/app/admin-client.jar
    # 环境变量
    environment:
      - TZ=Asia/Shanghai
    # 依赖关系
    depends_on:
      mysql:
        condition: service_healthy
      redis:
        condition: service_healthy
      admin-server:
        condition: service_started
    # 网络配置
    networks:
      - monitor-net
    deploy:
      resources:
        limits:
          cpus: "0.5"
          memory: 256M

# ============================================
# 网络定义
# 创建一个专用的 Docker 桥接网络，使所有容器可以通过
# 服务名称相互通信（如 admin-client-demo 可以通过
# http://mysql:3306 访问数据库）
# ============================================
networks:
  monitor-net:
    # 使用 bridge 驱动，创建隔离的网络环境
    driver: bridge
```

### 3.6 初始化 SQL 脚本

```sql
-- ============================================
-- 初始化 SQL 脚本
-- 首次启动 MySQL 时自动执行，创建演示用的数据库表
-- ============================================

-- 创建演示数据库（如果不存在）
CREATE DATABASE IF NOT EXISTS admin_demo
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

-- 切换到演示数据库
USE admin_demo;

-- 创建用户表（模拟业务数据表）
CREATE TABLE IF NOT EXISTS tb_user (
    -- 用户 ID，自增主键
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '用户ID',
    -- 用户名
    username VARCHAR(50) NOT NULL COMMENT '用户名',
    -- 邮箱
    email VARCHAR(100) COMMENT '邮箱',
    -- 创建时间
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    -- 更新时间
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) COMMENT '用户表';

-- 插入一条测试数据
INSERT INTO tb_user (username, email) VALUES ('admin', 'admin@example.com');
```

### 3.7 启动脚本

为了方便快速启动整个监控系统，可以创建一个 Docker Compose 启动脚本。在项目根目录下创建 `start.sh`：

```bash
#!/bin/bash
# ============================================
# 启动脚本
# 编译 Java 项目并启动 Docker Compose 服务
# ============================================

# 设置脚本在遇到错误时立即退出
set -e

# 打印彩色输出
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # 无颜色

echo -e "${YELLOW}========================================${NC}"
echo -e "${YELLOW}  Spring Boot Admin 监控系统启动脚本  ${NC}"
echo -e "${YELLOW}========================================${NC}"

# 第一步：编译 Admin Server 项目
echo -e "${GREEN}[1/3] 编译 Admin Server 项目...${NC}"
cd admin-server
# 执行 Maven 编译，跳过测试
mvn clean package -DskipTests -q
cd ..

# 第二步：编译 Admin Client 项目
echo -e "${GREEN}[2/3] 编译 Admin Client 项目...${NC}"
cd admin-client
mvn clean package -DskipTests -q
cd ..

# 第三步：启动 Docker Compose
echo -e "${GREEN}[3/3] 启动 Docker Compose 服务...${NC}"
# 使用 docker-compose 启动所有服务
# -d 参数表示后台运行
docker-compose up -d

# 等待服务启动
echo -e "${YELLOW}等待服务启动（约 30 秒）...${NC}"
sleep 30

# 打印访问信息
echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}  监控系统启动完成！${NC}"
echo -e "${GREEN}  Admin Server 面板：http://localhost:9090${NC}"
echo -e "${GREEN}  登录用户名：admin${NC}"
echo -e "${GREEN}  登录密码：admin123${NC}"
echo -e "${GREEN}  Admin Client 接口：http://localhost:8081/api/hello${NC}"
echo -e "${GREEN}========================================${NC}"
```

---

## 4. 运行验证

### 4.1 启动服务

在项目根目录下执行以下命令：

```bash
# 给启动脚本添加可执行权限
chmod +x start.sh

# 运行启动脚本
./start.sh
```

或者分步执行：

```bash
# 第一步：编译 Admin Server
cd admin-server
mvn clean package -DskipTests
cd ..

# 第二步：编译 Admin Client
cd admin-client
mvn clean package -DskipTests
cd ..

# 第三步：启动 Docker Compose
docker-compose up -d
```

### 4.2 预期输出

启动过程中，终端会输出类似以下日志：

```
[1/3] 编译 Admin Server 项目...
[INFO] BUILD SUCCESS
[2/3] 编译 Admin Client 项目...
[INFO] BUILD SUCCESS
[3/3] 启动 Docker Compose 服务...
[+] Running 4/4
 - Container ruoyi-mysql      Healthy
 - Container ruoyi-redis      Healthy
 - Container admin-server     Started
 - Container admin-client-demo Started

========================================
  监控系统启动完成！
  Admin Server 面板：http://localhost:9090
  登录用户名：admin
  登录密码：admin123
  Admin Client 接口：http://localhost:8081/api/hello
========================================
```

### 4.3 访问 Admin Server 面板

打开浏览器，访问 `http://localhost:9090`，你将看到 Spring Boot Admin 的登录页面。

**登录页面：**

- 输入用户名：`admin`
- 输入密码：`admin123`
- 点击 "Sign in" 按钮

**主面板（Dashboard）：**

登录成功后，进入主面板，你将看到以下内容：

1. **服务列表**：页面顶部显示已注册的服务列表，包括 `ADMIN-SERVER`（自身）和 `ADMIN-CLIENT-DEMO`（客户端）。每个服务旁边有一个状态指示灯：
   - 绿色（UP）：服务正常运行
   - 红色（DOWN）：服务异常
   - 灰色（OFFLINE）：服务离线

2. **服务详情**：点击 `admin-client-demo` 服务，进入详情页面，可以看到以下信息：

   **概述（Details）**：
   - 服务状态：UP
   - 运行时间（Uptime）
   - 应用版本
   - 自定义健康检查信息（customHealthIndicator 返回的内容）

   **指标（Metrics）**：
   - JVM 内存使用量（堆内存、非堆内存）
   - JVM 线程数
   - JVM GC 次数和耗时
   - CPU 使用率
   - HTTP 请求计数和响应时间

   **环境属性（Environment）**：
   - 所有 Spring 配置属性
   - 系统属性
   - 环境变量

   **日志级别（Loggers）**：
   - 查看所有 Logger 的当前日志级别
   - 可以动态修改日志级别（无需重启应用）

   **HTTP 追踪（HTTP Traces）**：
   - 最近 100 条 HTTP 请求记录
   - 包括请求方法、路径、状态码、响应时间

   **Bean 列表**：
   - 所有 Spring 管理的 Bean 名称和类型

   **线程转储（Thread Dump）**：
   - 当前所有线程的堆栈信息
   - 用于排查死锁、线程阻塞等问题

### 4.4 验证客户端接口

打开另一个终端，验证客户端应用是否正常运行：

```bash
# 测试客户端接口
curl http://localhost:8081/api/hello

# 预期输出：
# {"message":"Hello from Admin Client!","timestamp":"2026-08-22T10:30:00.123","application":"admin-client-demo"}
```

```bash
# 测试客户端 Actuator 健康端点
curl http://localhost:8081/actuator/health

# 预期输出（格式化后）：
# {
#   "status": "UP",
#   "components": {
#     "customHealthIndicator": {
#       "status": "UP",
#       "details": {
#         "service": "用户服务",
#         "apiStatus": "正常",
#         "responseTime": "15ms",
#         "lastCheck": 1692685800000
#       }
#     },
#     "db": {
#       "status": "UP",
#       "details": {
#         "database": "MySQL",
#         "validationQuery": "isValid()"
#       }
#     },
#     "diskSpace": {
#       "status": "UP",
#       "details": {
#         "total": 499963174912,
#         "free": 328718442496,
#         "threshold": 10485760
#       }
#     },
#     "ping": {
#       "status": "UP"
#     },
#     "redis": {
#       "status": "UP",
#       "details": {
#         "version": "7.0.0"
#       }
#     }
#   }
# }
```

### 4.5 常见问题排查

**问题 1：Admin Server 面板显示客户端为 OFFLINE**

可能原因和解决方案：

- **网络不通**：Docker 容器之间无法通信。检查 `docker-compose.yml` 中的所有服务是否在同一个 `networks` 下。
- **URL 配置错误**：Client 配置的 `service-base-url` 在 Docker 环境中应使用容器名而非 `localhost`。例如，应配置为 `http://admin-client-demo:8081`。
- **安全认证**：Admin Server 开启了安全认证，但 Client 注册时未提供用户名和密码。检查 `spring.boot.admin.client.username` 和 `password` 配置。

**问题 2：健康检查显示 DOWN**

- **数据库未就绪**：MySQL 容器启动较慢，需要配置 `depends_on` 的 `condition: service_healthy`。
- **Redis 连接失败**：检查 Redis 密码配置是否一致。

**问题 3：Maven 编译失败**

- **网络问题**：Maven 无法下载依赖。检查网络连接，或配置阿里云 Maven 镜像。
- **Java 版本不匹配**：确保本地 Java 版本为 17 或以上。

---

## 5. 项目对照

### 5.1 ruoyi-ai 中的监控需求

在 ruoyi-ai 项目中，Spring Boot Admin 的应用场景非常典型。该项目是一个微服务架构的 AI 应用平台，包含多个服务模块，每个模块都需要纳入监控体系。

**ruoyi-ai 项目模块与监控关注点对照：**

| 模块名称 | 端口 | 监控关注点 |
|---------|------|-----------|
| ruoyi-gateway | 8080 | 请求转发量、路由健康、响应时间 |
| ruoyi-auth | 9200 | 登录成功率、Token 刷新次数 |
| ruoyi-system | 9201 | 数据库连接池、用户操作频率 |
| ruoyi-ai | 9202 | AI 模型推理耗时、GPU 使用率 |
| ruoyi-job | 9203 | 定时任务执行成功率、执行时长 |
| ruoyi-gen | 9204 | 代码生成请求量、模板渲染性能 |
| ruoyi-file | 9300 | 文件上传下载量、存储空间使用率 |

### 5.2 从示例到 ruoyi-ai 的迁移步骤

本文提供的示例项目可以作为 ruoyi-ai 引入监控的**起点模板**。以下是将监控系统迁移到 ruoyi-ai 的具体步骤：

**第一步：在 ruoyi-ai 根 POM 中统一管理 Admin 版本**

```xml
<!-- ruoyi-ai 根 pom.xml 中的依赖管理 -->
<properties>
    <!-- 新增 Spring Boot Admin 版本属性 -->
    <spring-boot-admin.version>3.2.0</spring-boot-admin.version>
</properties>

<dependencyManagement>
    <dependencies>
        <!-- 统一管理 Admin Client 版本 -->
        <dependency>
            <groupId>de.codecentric</groupId>
            <artifactId>spring-boot-admin-starter-client</artifactId>
            <version>${spring-boot-admin.version}</version>
        </dependency>
    </dependencies>
</dependencyManagement>
```

**第二步：为每个子模块引入 Admin Client 依赖**

```xml
<!-- 每个需要监控的子模块（ruoyi-auth、ruoyi-system 等）的 pom.xml -->
<dependencies>
    <!-- 引入 Admin Client 依赖 -->
    <dependency>
        <groupId>de.codecentric</groupId>
        <artifactId>spring-boot-admin-starter-client</artifactId>
    </dependency>
    <!-- Actuator 依赖（ruoyi-ai 中可能已存在） -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-actuator</artifactId>
    </dependency>
</dependencies>
```

**第三步：在 Nacos 中配置公共监控参数**

在 ruoyi-ai 项目中，配置是通过 Nacos 配置中心统一管理的，因此只需在共享配置文件中添加：

```yaml
# Nacos 共享配置：application-monitor.yml
# 所有微服务共享的监控配置
spring:
  boot:
    admin:
      client:
        # Admin Server 地址，通过 Nacos 服务发现获取
        # 在生产环境中，Admin Server 应以独立服务部署
        url: http://ruoyi-monitor:9090
        # 安全认证信息
        username: admin
        password: ${ADMIN_SERVER_PASSWORD}
        instance:
          # 使用服务注册到 Nacos 的 IP 地址
          service-base-url: http://${spring.cloud.nacos.discovery.ip}:${server.port}

management:
  endpoints:
    web:
      exposure:
        # 暴露所有端点
        include: "*"
  endpoint:
    health:
      # 显示详细健康信息
      show-details: always
```

**第四步：在 docker-compose.yml 中添加监控服务**

```yaml
# ruoyi-ai 项目的 docker-compose.yml 新增监控服务
services:
  # 新增：Admin Server 服务
  ruoyi-monitor:
    # 构建配置
    build:
      context: ./ruoyi-monitor
      dockerfile: Dockerfile
    # 容器名称
    container_name: ruoyi-monitor
    # 端口映射
    ports:
      - "9090:9090"
    # 环境变量
    environment:
      - SPRING_PROFILES_ACTIVE=docker
      - TZ=Asia/Shanghai
    # 网络配置
    networks:
      - ruoyi-network
    # 健康检查
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:9090/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 3
```

### 5.3 ruoyi-ai 中的扩展监控

除了 Spring Boot Admin 提供的基础监控，ruoyi-ai 项目还可以结合以下监控能力：

**集成 Prometheus + Grafana：**

Spring Boot Admin 适合**实时查看**服务状态，但不存储历史数据。如果需要长期趋势分析和告警，可以集成 Prometheus：

```yaml
# 在每个子模块中添加 Micrometer Prometheus 依赖
# pom.xml 中新增：
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```

配置暴露 Prometheus 端点：

```yaml
# application.yml 中启用 Prometheus 端点
management:
  endpoints:
    web:
      exposure:
        # 暴露 Prometheus 端点
        include: "prometheus,health,info,metrics"
```

然后在 Prometheus 中配置抓取各服务的指标数据，最后用 Grafana 展示可视化面板。

**自定义业务指标：**

在 ruoyi-ai 的 AI 模块中，可以使用 Micrometer 定义业务指标：

```java
// 自定义 AI 推理指标
// 使用 Micrometer 的 MeterRegistry 注册自定义指标
@Service
public class AiInferenceMetrics {

    // 注入 MeterRegistry，用于注册自定义指标
    private final MeterRegistry meterRegistry;
    // AI 推理耗时直方图
    private final Timer inferenceTimer;
    // AI 推理请求计数器
    private final Counter inferenceCounter;

    /**
     * 构造函数，初始化自定义指标
     *
     * @param meterRegistry Micrometer 的指标注册器
     */
    public AiInferenceMetrics(MeterRegistry meterRegistry) {
        // 保存 MeterRegistry 实例
        this.meterRegistry = meterRegistry;

        // 创建 AI 推理耗时直方图
        // 指标名称：ai.inference.duration
        // 标签：model（模型名称）、version（模型版本）
        this.inferenceTimer = Timer.builder("ai.inference.duration")
            .description("AI 模型推理耗时")
            .tag("model", "gpt-3.5")
            .tag("version", "v1.0")
            .register(meterRegistry);

        // 创建 AI 推理请求计数器
        // 指标名称：ai.inference.count
        this.inferenceCounter = Counter.builder("ai.inference.count")
            .description("AI 推理请求总数")
            .tag("model", "gpt-3.5")
            .register(meterRegistry);
    }

    /**
     * 记录一次 AI 推理调用
     *
     * @param runnable 实际的推理逻辑
     */
    public void recordInference(Runnable runnable) {
        // 增加请求计数
        inferenceCounter.increment();
        // 记录推理耗时
        inferenceTimer.record(runnable);
    }
}
```

这些自定义指标会被 Actuator 的 `/actuator/metrics` 端点暴露，并展示在 Spring Boot Admin 面板中，也可以通过 Prometheus 采集后在 Grafana 中绘制趋势图。

---

## 6. 面试题

### 面试题 1：Spring Boot Admin 的通信原理是什么？Admin Server 如何获取各个微服务的状态信息？

**考察点：** 对 Spring Boot Admin 架构原理的理解，特别是 C/S 通信机制。

**参考答案：**

Spring Boot Admin 采用"客户端主动注册 + 服务端定期拉取"的通信模式：

1. **注册阶段**：每个嵌入 Admin Client 的微服务在启动时，会通过 `Spring Boot Admin Client` 自动向 Admin Server 的 `/instances` 端点发送 HTTP POST 请求，提交自己的 Actuator 端点 URL（如 `http://192.168.1.100:8081/actuator`）。注册请求中包含服务名称、基础 URL、健康检查 URL 等元信息。

2. **采集阶段**：Admin Server 收到注册信息后，会定期（默认每 10 秒）通过 HTTP 请求主动拉取每个客户端的 Actuator 端点数据，包括：
   - `/actuator/health`：获取健康状态
   - `/actuator/metrics`：获取指标数据
   - `/actuator/info`：获取应用信息
   - `/actuator/env`：获取环境属性
   - `/actuator/loggers`：获取日志级别配置

3. **状态管理**：Admin Server 在内存中维护每个客户端的注册信息和状态快照。如果连续多次拉取失败（默认 3 次），会将该客户端标记为 `OFFLINE`。

4. **通知机制**：当客户端状态发生变化（如从 `UP` 变为 `OFFLINE`），Admin Server 会触发事件通知，可以配置邮件、钉钉、Webhook 等通知渠道。

**关键点**：Admin Server 是**拉取**模式，而非客户端推送。这意味着 Admin Server 必须能够通过网络访问到每个客户端的 Actuator 端点，在 Docker 或 Kubernetes 环境中需要特别注意网络可达性配置。

### 面试题 2：在 Docker 容器化部署下，Spring Boot Admin Client 注册时容易出现哪些问题？如何解决？

**考察点：** 对 Docker 网络模式和 Spring Boot Admin 配置的理解，以及实际排错能力。

**参考答案：**

在 Docker 环境下，最容易出现的问题是 **"注册成功但面板显示 OFFLINE"**。根本原因是 Client 注册时提交的 URL 在 Admin Server 容器中无法访问。具体场景如下：

**问题场景：**

假设 Client 容器配置了 `spring.boot.admin.client.instance.service-base-url=http://localhost:8081`。Client 向 Admin Server 注册时提交了 `http://localhost:8081/actuator`。但 Admin Server 是另一个容器，它尝试访问 `http://localhost:8081` 时，实际上访问的是 **Admin Server 容器自身的 8081 端口**，而不是 Client 容器的 8081 端口，导致连接失败。

**解决方案：**

1. **使用容器名称作为主机名**：在 Docker Compose 网络中，容器可以通过服务名相互访问，因此应将 Client 的 `service-base-url` 配置为容器名：

```yaml
spring:
  boot:
    admin:
      client:
        instance:
          # 使用容器名称而非 localhost
          service-base-url: http://admin-client-demo:8081
          management-base-url: http://admin-client-demo:8081
          health-url: http://admin-client-demo:8081/actuator/health
```

2. **确保所有服务在同一网络**：在 `docker-compose.yml` 中，所有服务应配置在同一个 `networks` 下，否则无法通过容器名通信。

3. **使用环境变量动态配置**：在 Kubernetes 等更复杂的编排环境中，可以通过环境变量注入 Pod 的 IP 地址：

```yaml
spring:
  boot:
    admin:
      client:
        instance:
          # 使用环境变量动态获取 Pod IP
          service-base-url: http://${POD_IP}:${server.port}
```

4. **配置健康检查超时**：如果应用启动较慢，可适当增加 Admin Server 的检查超时时间：

```yaml
spring:
  boot:
    admin:
      client:
        # 注册超时时间
        connect-timeout: 10s
        # 读取超时时间
        read-timeout: 10s
```

### 面试题 3：Spring Boot Actuator 的 /health 端点是如何聚合多个组件的健康状态的？如果要自定义一个健康检查逻辑，应该怎么做？

**考察点：** 对 Actuator 健康检查机制的理解，以及自定义扩展能力。

**参考答案：**

**健康检查聚合机制：**

Spring Boot Actuator 的健康检查基于 **Composite Health Indicator** 模式，采用"树形聚合"机制：

1. **各组件独立检查**：Spring Boot 内置了多种 `HealthIndicator` 实现，如 `DataSourceHealthIndicator`（检查数据库连接）、`RedisHealthIndicator`（检查 Redis 连通性）、`DiskSpaceHealthIndicator`（检查磁盘空间）等。每个 Indicator 独立检查对应组件的健康状态，返回 `Health` 对象（包含 `UP` 或 `DOWN` 状态和详情信息）。

2. **状态聚合规则**：`CompositeHealthContributor` 将所有组件的检查结果汇总，聚合规则为：
   - 所有组件均为 `UP`，整体状态为 `UP`
   - 任何一个组件为 `DOWN`，整体状态为 `DOWN`
   - 所有组件为 `UNKNOWN`，整体状态为 `UNKNOWN`
   - 状态存在优先级：`DOWN` > `UNKNOWN` > `UP`（即只要有一个 DOWN，整体就是 DOWN）

3. **状态映射**：最终的健康状态通过 `StatusAggregator` 接口进行聚合，默认实现为 `SimpleStatusAggregator`，按优先级排序判断。

**自定义健康检查：**

实现自定义健康检查有两种方式：

**方式一：实现 HealthIndicator 接口**

```java
// 自定义健康检查指示器
// 检查第三方 API 是否可用
@Component
// 设置健康检查的标识名称，在 /actuator/health 中显示为 "thirdPartyApi"
public class ThirdPartyApiHealthIndicator implements HealthIndicator {

    @Override
    public Health health() {
        // 执行健康检查逻辑
        boolean isHealthy = checkThirdPartyApi();

        if (isHealthy) {
            // 返回 UP 状态，附带详情
            return Health.up()
                .withDetail("apiUrl", "https://api.example.com/health")
                .withDetail("responseTime", "120ms")
                .build();
        } else {
            // 返回 DOWN 状态，附带错误信息
            return Health.down()
                .withDetail("apiUrl", "https://api.example.com/health")
                .withDetail("error", "连接超时")
                .withDetail("lastSuccessTime", "2026-08-22 10:00:00")
                .build();
        }
    }

    /**
     * 实际的健康检查逻辑
     *
     * @return true 表示健康，false 表示不健康
     */
    private boolean checkThirdPartyApi() {
        // 这里可以发送 HTTP 请求检查第三方 API 是否可用
        // 实际项目中建议设置超时时间，避免阻塞
        try {
            // 模拟检查
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
```

**方式二：使用 Lambda 表达式（推荐）**

```java
@Configuration
public class HealthCheckConfig {

    @Bean
    public HealthIndicator cacheHealthIndicator() {
        // 使用 Lambda 表达式快速创建健康检查器
        return () -> {
            // 检查缓存服务是否可用
            boolean cacheAvailable = checkCacheService();
            if (cacheAvailable) {
                return Health.up()
                    .withDetail("cacheType", "Redis")
                    .withDetail("hitRate", "95.2%")
                    .build();
            }
            return Health.down()
                .withDetail("cacheType", "Redis")
                .withDetail("error", "无法连接到 Redis 服务器")
                .build();
        };
    }

    private boolean checkCacheService() {
        // 实际的缓存检查逻辑
        return true;
    }
}
```

自定义健康检查在生产环境中非常有用，例如：检查消息队列是否积压过多、检查第三方支付接口是否可用、检查 AI 模型服务是否正常响应等。这些业务层面的健康检查可以帮助运维团队在用户发现问题之前及时介入。

---

## 总结

本文作为 ruoyi-ai 技术栈深度剖析系列之监控部署篇的第一篇，从零搭建了 Spring Boot Admin 监控系统，完成了以下目标：

1. **理解了监控的必要性**：在微服务架构中，监控是保障系统稳定运行的基础设施。
2. **掌握了核心概念**：Actuator 端点、Admin Server/Client 架构、Docker 容器化部署。
3. **完成了代码搭建**：Admin Server 项目、Admin Client 项目、Docker Compose 编排文件。
4. **验证了运行效果**：通过浏览器访问监控面板，实时观察服务状态和各项指标。
5. **建立了项目映射**：将示例中的监控方案对应到 ruoyi-ai 项目的实际需求中。

在下一篇（第 2 篇）中，我们将深入探讨 Spring Boot Admin 的**高级配置**，包括：邮件告警通知、自定义通知渠道、安全认证增强、以及如何集成 Prometheus + Grafana 实现历史数据的持久化存储和趋势分析。

---

> **本系列文章目录：**
>
> 监控部署篇：
> - 第 1 篇：Spring Boot Admin + Docker 入门：第一个监控面板（本文）
> - 第 2 篇：Spring Boot Admin 高级配置与告警（预告）
> - 第 3 篇：Prometheus + Grafana 集成与可视化（预告）
>
> 系列其他篇章正在更新中，敬请期待。