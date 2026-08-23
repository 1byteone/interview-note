# Sa-Token入门：5分钟为API加上登录认证

> 系列文章：ruoyi-ai 技术栈深度剖析 · 第 10 篇（Level 1 入门篇）
>
> 关键词：Sa-Token、StpUtil、登录认证、路由拦截、@SaCheckLogin、@SaCheckPermission、JWT集成
>
> 前置要求：熟悉 Spring Boot 3.x 基本用法，了解 HTTP 无状态协议与 Cookie/Session/Token 的基本概念

---

## 一、项目背景：认证鉴权的痛点

### 1.1 一个朴素的需求

任何一个面向用户的 Web 系统，几乎都绕不开一个问题：**如何知道"你是你"？**

- 用户登录后，携带凭证访问受保护的 API（比如查看自己的订单、修改个人资料）；
- 未登录的用户直接访问这些 API，系统必须拒绝并返回 401；
- 不同角色的用户（普通用户 / 管理员 / 运营）能访问的资源范围不同；
- 某些操作要二次校验权限（比如"删除他人文章"需要 `article:delete` 权限码）。

这就是经典的**认证（Authentication）**与**授权（Authorization）**问题：

| 概念 | 英文 | 回答的问题 | 举例 |
| --- | --- | --- | --- |
| 认证 | Authentication | 你是谁？ | 校验用户名密码、校验 Token 是否合法 |
| 授权 | Authorization | 你能干什么？ | 校验是否具有 `order:update` 权限码 |

很多初学 Spring Boot 的同学，第一次接触到认证鉴权时，往往会被 Spring Security 那套复杂的过滤器链、`UserDetailsService`、`SecurityFilterChain`、`GrantedAuthority` 搞得晕头转向。明明是"加个登录"这种小事，配置起来却动辄上百行，还夹杂着一堆 `csrf`、`formLogin`、`oauth2Login` 等默认开启、随时可能成为安全隐患的默认配置。

### 1.2 ruoyi-ai 项目里的真实场景

ruoyi-ai 是一个把 **Spring Boot 3 + LangChain4j + MyBatis-Plus + Sa-Token** 组合起来的 AI 对话系统（可以把它理解为拥有智能问答、AI 绘画、知识库等能力的业务系统）。在这个系统里：

- 用户需要注册登录后才能使用 AI 能力（对话、绘画会消耗额度，必须绑定到具体用户）；
- 后台管理员需要登录后才能管理模型配置、审核素材、查看统计报表；
- 前端（Vue3 或移动端 App）是前后端分离的，接口通过 `fetch/axios` 调用，天然不依赖 Session 的 Cookie 机制；
- 系统将来可能接入微信小程序、公众号、第三方 OAuth 登录等更多端，Token 方案要能平滑扩展。

在这种"前后端分离 + 多端接入 + 需要权限细分"的场景下，Spring Security 的"全家桶"式设计反而显得笨重。而 Sa-Token 这个**轻量级 Java 权限认证框架**，只用一两行代码就能完成登录/登出/鉴权，天然契合 ruoyi-ai 这种"重业务、轻框架"的项目气质。

### 1.3 为什么选 Sa-Token 而不是 Spring Security？

这是面试高频问题，我们先把两者的差异讲透。Sa-Token 与 Spring Security 都是 Java 生态里成熟的认证鉴权方案，但设计哲学完全不同：

| 对比维度 | Sa-Token | Spring Security |
| --- | --- | --- |
| 上手难度 | 极低，核心就一个 `StpUtil` 静态类，登录一行、鉴权一行 | 较高，过滤器链、配置类、`UserDetailsService`、`PasswordEncoder` 等概念多 |
| 代码侵入性 | 低，无强制继承，业务代码里直接调用静态方法 | 较高，需要实现大量接口、继承 `WebSecurityConfigurerAdapter`（新版本为组件式配置） |
| 默认行为 | 开箱即用，无安全默认值反噬 | 默认开启 CSRF、表单登录、Session 等，需要显式关闭，配置遗漏易出隐患 |
| 会话存储 | 支持内存 / Redis / JWT 多种模式，切换一行配置 | 主要基于 Session，无状态 JWT 需要额外集成 |
| 多端登录 | 支持同端互斥、多端共存、踢人下线等高阶会话场景 | 需要手写方案 |
| 权限模型 | 基于权限码字符串，`@SaCheckPermission("user:add")` 直白 | 基于权限集合 + 表达式，需要理解 `hasAuthority/hasRole` |
| 二次开发 | 源码简单，几十个类，容易读懂魔改 | 源码庞大，过滤器链晦涩难懂 |
| 生态 | 轻量，聚焦认证鉴权本身 | 生态庞大，OAuth2/OIDC/ACL 等"全家桶" |
| 适用场景 | 前后端分离、单体/微服务的业务系统、中小团队快速迭代 | 大型企业级、强安全合规、需要 OAuth2 协议全套的场景 |

**一句话总结**：Spring Security 是"瑞士军刀"，功能全但复杂；Sa-Token 是"手术刀"，聚焦认证鉴权核心场景，学习成本低、代码量少、可读性极强。ruoyi-ai 选用 Sa-Token，正是看中它"5 分钟接入、一年半载够用、真要换也容易"的务实特性。

### 1.4 本文目标

本文是入门篇，目标只有一个：**用 5 分钟读懂 Sa-Token 的最小闭环**。

我们将手把手从零搭建一个 Spring Boot 3 项目，完成：

1. 引入 sa-token-spring-boot3-starter 依赖；
2. 配置拦截器，让指定路由需要登录后才能访问；
3. 写一个登录接口，一行代码完成签发 Token；
4. 写一个登录后用户信息的接口，通过 `StpUtil` 取出当前登录用户；
5. 用 `@SaCheckPermission` 演示接口级权限校验；
6. 集成 JWT 插件，把无状态化打通。

学完本文，你将**完全理解 ruoyi-ai 里 `StpUtil.login()`、`StpUtil.getLoginId()`、`@SaCheckPermission` 这些代码到底在干什么**，为阅读项目源码和后续深度学习（Level 2/3）打下坚实基础。

---

## 二、核心概念：Token、Session、StpUtil、权限码

在动手写代码前，先建立四个核心概念。它们是理解整个 Sa-Token 体系的"地基"。

### 2.1 Token（令牌）

HTTP 协议是**无状态**的：服务器不会记住上一次请求是谁发的。要让服务器"认出你"，客户端每次请求都得主动出示凭证，这个凭证就是 Token。

Sa-Token 默认的 Token 是一个 32 位的随机字符串（形如 `f0c8a1b2-...`）。它的工作流程是：

```
第一次请求（登录）：
   客户端 ------------------- 用户名 + 密码 -------------------> 服务器
   客户端 <------------------- 返回 token: xxxxxx --------------- 服务器（校验通过，登记 token -> 用户id）

后续请求：
   客户端 --(请求头 Authorization: xxxxxx / Cookie: satoken=xxxxxx)--> 服务器
   客户端 <---------------------------- 业务数据 ------------------- 服务器（根据 token 找到用户id）
```

Token 的存储位置有三种形态，Sa-Token 全部支持，切换只需改一行配置：

| 形态 | 说明 | 适用场景 |
| --- | --- | --- |
| 内存 Map | Token 存服务器内存，默认配置 | 单体小项目、学习 demo |
| Redis | Token 存 Redis（还需要引入 sa-token-redis 插件） | 多实例部署、需要分布式会话 |
| JWT | Token 本身就是一段自包含签名的字符串（需引入 sa-token-jwt 插件） | 无状态化、跨服务认证 |

> 关键点：Sa-Token 的 Token **默认不是 JWT**。它默认是"真随机串 + 服务端存储"模式——Token 本身不携带任何信息，服务器通过 Token 反查会话。只有显式引入 JWT 插件后，Token 才会变成 JWT 格式。这个细节下文第四节会详细展开。

### 2.2 Session（会话）

会话（Session）是"登录状态"的整体抽象。Sa-Token 的会话体系分三层，这是它比很多框架设计更清晰的地方：

| 名称 | 存储内容 | 生命周期 |
| --- | --- | --- |
| Account Session（账号会话） | 一个用户的所有登录会话共享的数据（如：购物车、用户资料缓存） | 跟随账号活跃存活 |
| Token Session（令牌会话） | 一次登录对应的会话数据（如：登录时间、登录 IP、设备） | 跟随 Token 存活 |
| Token 专属 Session | 某个 Token 独有的数据（默认配置下与 Token Session 相同） | 跟随 Token 存活 |

简单记忆：一个账号（UserId=10001）可以在手机、电脑、Pad 上同时登录（产生 3 个 Token），它们共享同一个 **Account Session**（账号级数据），但各自拥有独立的 **Token Session**（会话级数据）。

Sa-Token 提供了两组 API 分别操作这两层会话，都会在后面的代码中用到。

### 2.3 StpUtil：一切的核心

`StpUtil` 是 Sa-Token 的**门面类**（静态工具类），99% 的日常操作都通过它完成。它内部通过 `StpLogic` 编排整套逻辑，但对外暴露的永远是简洁的静态方法。

常用 API 一览（这是本文最重要的表，建议收藏）：

```java
// ==================== 登录与登出 ====================
StpUtil.login(10001);                  // 登录：为 id=10001 的用户签发 Token
StpUtil.logout();                      // 登出：删除当前会话，Token 即刻失效
StpUtil.logout(10001);                 // 强制指定账号登出（管理员踢人用）
StpUtil.kickout(10001);                // 踢人下线：只踢掉该账号最新一次登录

// ==================== 会话查询 ====================
StpUtil.getTokenValue();               // 获取当前 Token 值
StpUtil.getLoginId();                  // 获取当前登录用户 id（未登录会抛异常）
StpUtil.getLoginIdAsLong();            // 获取 id 并转为 Long 类型
StpUtil.isLogin();                     // 判断当前是否登录（不抛异常，返回 boolean）
StpUtil.getTokenInfo();                // 获取 Token 的详细信息（创建时间、登录设备等）

// ==================== 权限校验 ====================
StpUtil.checkPermission("user:add");   // 校验权限码，没有则抛出 NotPermissionException 异常
StpUtil.checkRole("admin");            // 校验角色，没有则抛出异常
StpUtil.hasPermission("user:add");     // 判断是否有权限码（不抛异常，返回 boolean）
StpUtil.hasRole("admin");              // 判断是否有角色

// ==================== 会话数据（保存在 Token Session） ====================
StpUtil.getSession();                  // 获取当前 Token Session
StpUtil.getSession().set("key", val);  // 往会话里存数据
StpUtil.getSession().get("key");       // 从会话里取数据
StpUtil.getSessionByLoginId(10001);    // 获取 id=10001 的 Account Session

// ==================== 切换身份 / 临时身份 ====================
StpUtil.switchTo(10002);               // 临时切换为另一个用户的身份（模拟造数据常用）
StpUtil.endSwitch();                   // 结束临时切换
StpUtil.getTokenValueByLoginId(10001); // 查看指定账号当前活跃的 Token
```

可以看到，Sa-Token 的强大之处在于：**所有高频操作都是这一个类的方法**，几乎没有多余的抽象层。这也是它代码量少、易读性的来源。

### 2.4 权限码（Permission Code）

权限模型有三种主流实现，Sa-Token 用的是最直观的**权限码**模型：

| 模型 | 说明 | Sa-Token 支持 |
| --- | --- | --- |
| RBAC（用户-角色-权限） | 用户绑角色，角色绑权限码 | 原生支持（推荐） |
| 直接给用户绑权限码 | 用户直接持有权限列表 | 原生支持 |
| ABAC（属性权限） | 根据资源属性动态判断 | 需自行扩展 |

权限码是一段有约定的字符串，通常写作 `模块:操作` 的形式：

- `user:add`、`user:edit`、`user:delete` —— 用户模块的增改删；
- `order:query`、`order:update` —— 订单模块查询、修改；
- `ai:chat`、`ai:draw` —— AI 对话、AI 绘画。

权限校验的完整链路是：

```
请求进来
   ↓
获取 Token -> 反查登录用户 id
   ↓
查数据库（或缓存）：用户 id -> 角色列表 -> 权限码列表
   ↓（由 StpInterface 接口的 getPermissionList() 方法提供）
比对：请求需要的权限码 是否 ⊆ 用户拥有的权限码
   ↓
通过 -> 放行；不通过 -> 抛 NotPermissionException -> 全局异常处理器转成 403
```

其中"**如何根据用户 id 查权限码列表**"是 Sa-Token 留给业务方的唯一扩展点：实现 `StpInterface` 接口即可。这正是后面"项目对照"章节里 ruoyi-ai 代码的原型，也是面试官最爱问的点。

---

## 三、从零搭建代码

本节我们创建一个全新的 Spring Boot 3 项目，实现一套完整的"登录 → 鉴权 → 权限校验"最小闭环。项目打在一张表格里的大纲如下：

```
hello-satoken/                          # 项目根目录
├── pom.xml                             # Maven 依赖
├── src
│   └── main
│       ├── resources
│       │   └── application.yml         # 启动配置
│       └── java
│           └── com/example/satoken
│               ├── HelloSatokenApplication.java    # 启动类
│               ├── config
│               │   ├── SaTokenConfig.java         # 注册拦截器 + 放行路径
│               │   └── GlobalExceptionHandler.java # 全局异常处理（401/403）
│               ├── controller
│               │   ├── AuthController.java         # 登录 / 登出 / 当前用户
│               │   └── UserController.java         # 受保护的示例接口
│               ├── service
│               │   └── StpInterfaceImpl.java       # 向 Sa-Token 提供角色/权限码
│               └── entity
│                   └── User.java                   # 用户实体（演示用）
```

### 3.1 pom.xml：三行依赖搞定

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <!-- 继承 Spring Boot 官方父工程，统一管理依赖版本 -->
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.2.5</version>   <!-- Spring Boot 3 系列 -->
        <relativePath/>            <!-- 从本地仓库查找父工程，不联网 -->
    </parent>

    <groupId>com.example</groupId>
    <artifactId>hello-satoken</artifactId>
    <version>1.0.0</version>
    <name>hello-satoken</name>
    <description>Sa-Token 入门 Demo：5 分钟为 API 加上登录认证</description>

    <properties>
        <!-- 指定 Java 17：Spring Boot 3 强制要求 JDK 17+ 才能运行 -->
        <java.version>17</java.version>
    </properties>

    <dependencies>
        <!-- Web 启动器：内嵌 Tomcat，提供 Spring MVC 能力，写 Controller 必备 -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>

        <!-- Sa-Token 核心：注意版本后缀必须与 Spring Boot 大版本匹配 -->
        <!-- 我们用的是 Spring Boot 3，所以要引 spring-boot3 后缀的 starter -->
        <dependency>
            <groupId>cn.dev33</groupId>
            <artifactId>sa-token-spring-boot3-starter</artifactId>
            <version>1.37.0</version>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <!-- Spring Boot Maven 插件：mvn spring-boot:run 或打包成可执行 jar -->
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

逐行看两个关键点：

1. **版本号的后缀是有讲究的**：`sa-token-spring-boot3-starter` 对应 Spring Boot 3.x（JDK 17+）；如果是老项目用的 Spring Boot 2.x，则必须换成 `sa-token-spring-boot-starter` 并搭配 JDK 8。ruoyi-ai 是 Spring Boot 3，所以也用 `spring-boot3` 后缀。
2. **只要引入 starter，配置零即可使用**：Sa-Token 默认是"内存模式 + 无需任何配置"，这也是它"开箱即用"的底气所在。官方文档中接入依赖即可启动，登录不限制密码等（测试阶段连用户表都可以没有）。

> 小贴士：如果你要接入 Redis 或者 JWT，只需再追加两个依赖（`sa-token-redis-jackson` + commons-pool2，或 `sa-token-jwt`），配置方式我们在第四节 JWT 部分演示。

### 3.2 application.yml：kebab-case 风格配置

```yaml
# ==================== 服务端口与上下文 ====================
server:
  port: 8080                      # 启动端口，ruoyi-ai 默认也是 8080

# ==================== Spring 应用名 ====================
spring:
  application:
    name: hello-satoken           # 应用名称（kebab-case：小写 + 连字符）
  main:
    # Spring Boot 3 默认不允许循环依赖，此处默认 false 即可，示例项目无循环依赖
    allow-circular-references: false

# ==================== Sa-Token 配置 ====================
sa-token:
  # token 名称（同时也是前端必须在请求头/Cookie 中携带的 key，如 satoken: xxxxxx）
  token-name: satoken
  # token 有效期（单位：秒），-1 代表永不过期
  timeout: 86400                  # 24 小时 = 86400 秒
  # token 最低活跃频率（单位：秒），如果 token 超过此时间没有访问就会过期
  # 简单说：active-timeout 是"活跃续期"机制，0 则不启用
  active-timeout: -1
  # 是否允许同一账号并发登录（为 true 时：同一账号可多端同时在线）
  is-concurrent: true
  # 在多人登录同一账号时，是否共用一个 token（为 false 时：每次登录都新建一个 token）
  is-share: false
  # token 风格：uuid / simple-uuid / random-32 / random-64 / random-128 / tik
  token-style: uuid
  # 是否输出操作日志
  is-log: false

# ==================== 日志级别 ====================
logging:
  level:
    com.example.satoken: debug    # 打印我们包下的 debug 日志，便于观察拦截器行为
```

YAML 的命名规范这里特别说明一下：**Spring Boot 官方推荐使用 kebab-case（小驼峰转小写加连字符）**，例如 `active-timeout`、`is-concurrent`。如果你写成 `activeTimeout`、`isConcurrent` 驼峰风格，Spring 也能识别（它做了宽松映射），但为了团队规范、可读性和统一性，ruoyi-ai 以及本文全部使用 kebab-case。

### 3.3 启动类：和普通 Spring Boot 无异

```java
package com.example.satoken;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 项目启动类
 *
 * 这只是一个普通的 Spring Boot 启动类，
 * 唯一不同的是：由于引入了 sa-token-spring-boot3-starter，
 * Spring Boot 的自动装配机制会自动创建 SaToken 相关的核心 Bean。
 * 所以我们不需要在这里写任何额外注解或 import。
 */
@SpringBootApplication
public class HelloSatokenApplication {

    /**
     * 程序入口
     *
     * @param args 命令行参数（一般用不到）
     */
    public static void main(String[] args) {
        // 启动 Spring 容器
        SpringApplication.run(HelloSatokenApplication.class, args);
        // 启动成功后打印一行提示，方便确认是否正常起来
        System.out.println(">>> hello-satoken 启动成功！访问 http://localhost:8080/auth/login 进行登录测试");
    }
}
```

### 3.4 SaTokenConfig：注册拦截器 + 放行登录接口

Sa-Token 的核心整合手段之一，是利用 **Spring Boot 拦截器（HandlerInterceptor）** 实现"请求进来先查 Token"。

先看拦截器注册与路由放行规则：

```java
package com.example.satoken.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Sa-Token 配置类
 *
 * 作用：
 * 1. 把 Sa-Token 自带的拦截器（SaInterceptor）注册进 Spring MVC 的拦截器链；
 * 2. 声明哪些路径需要登录鉴权、哪些路径直接放行。
 */
@Configuration
public class SaTokenConfig implements WebMvcConfigurer {

    /**
     * 注册拦截器的回调方法，由 Spring MVC 在初始化时自动调用
     *
     * @param registry 拦截器注册中心
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 注册 Sa-Token 拦截器，并开启基于注解的鉴权（@SaCheckLogin 等）
        registry.addInterceptor(new SaInterceptor()).addPathPatterns("/**")
                // 明确放行的路径：登录接口本身、登出接口等
                // 注意：这里只是"放行到 Controller 层"，
                // 接口内部是否校验登录，由 @SaCheckLogin 注解或业务代码决定
                .excludePathPatterns(
                        "/auth/login",    // 登录接口：未登录才能调用，必须放行
                        "/auth/logout"    // 登出接口：内部自己处理"未登录时直接返回成功"
                );
    }
}
```

接着是**核心且最易忽视的一步**：上面的拦截器只是"开启注解鉴权",真正的登录校验是 `@SaCheckLogin` 注解在 Controller 方法上生效的。`SaInterceptor` 内部会分析目标方法上有没有 `@SaCheckLogin` / `@SaCheckPermission` 等注解，有则调用 `StpUtil.checkLogin()` / `StpUtil.checkPermission("...")` 做校验。

为了对比"拦截器路径级鉴权"与"注解方法级鉴权"两种方式，我们的示例项目**两种都会演示**（见 UserController）。

现在写全局异常处理器，把 Sa-Token 抛出的异常翻译成 HTTP 状态码：

```java
package com.example.satoken.config;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

/**
 * 全局异常处理
 *
 * 统一拦截 Sa-Token 抛出的异常，转换为前端容易判断的
 * HTTP 状态码 + JSON 返回体，避免把异常堆栈直接抛给前端。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 未登录异常：Token 缺失、过期、被顶下线等统一走这里
     *
     * @param e 未登录异常对象（内部包含具体的未登录原因码与提示语）
     * @return 统一的 JSON 错误体
     */
    @ExceptionHandler(NotLoginException.class)
    public Map<String, Object> handleNotLogin(NotLoginException e) {
        // 组装统一的返回结构
        Map<String, Object> result = new HashMap<>();
        // 业务码：4010 表示未登录（ruoyi-ai 中自定义为 401 系列业务码，这里沿用其风格）
        result.put("code", 401);
        // 提示信息：Sa-Token 已经帮我们生成好中文提示，直接透传
        result.put("message", e.getMessage());
        // HTTP 状态码 401：未认证
        result.put("status", 401);
        return result;
    }

    /**
     * 权限不足异常：登录了但缺少某个权限码
     *
     * @param e 无权限异常对象
     * @return 统一的 JSON 错误体
     */
    @ExceptionHandler(NotPermissionException.class)
    public Map<String, Object> handleNotPermission(NotPermissionException e) {
        Map<String, Object> result = new HashMap<>();
        // 业务码 403：禁止访问
        result.put("code", 403);
        // 提示信息：例如"无此权限：user:delete"
        result.put("message", e.getMessage());
        result.put("status", 403);
        return result;
    }

    /**
     * 角色不足异常：登录了但缺少某个角色
     *
     * @param e 无角色异常
     * @return 统一的 JSON 错误体
     */
    @ExceptionHandler(NotRoleException.class)
    public Map<String, Object> handleNotRole(NotRoleException e) {
        Map<String, Object> result = new HashMap<>();
        result.put("code", 403);
        result.put("message", e.getMessage());
        result.put("status", 403);
        return result;
    }
}
```

> 说明：Controller 层返回的 `Map<String, Object>` 在真实项目中一般会用统一响应类 `R<T>`（ruoyi-ai 里就是 `R<T>`），此处为了把注意力聚焦在 Sa-Token 本身，用 Map 简化。第 5 章会对照 ruoyi-ai 的 `R<T>` 包装方式。

### 3.5 用户实体与"伪数据库"

为了聚焦 Sa-Token，我们不用连接数据库，直接在内存里放一个"模拟用户表"：

```java
package com.example.satoken.entity;

/**
 * 用户实体（演示用）
 *
 * 真实项目中对应数据库表 sys_user 的一行记录，
 * 这里为了演示方便，用内存 Map 模拟。
 */
public class User {

    /** 用户主键 id，对应 Sa-Token 的 loginId */
    private Long id;

    /** 登录账号 */
    private String username;

    /** 登录密码（真实项目必须加密存储，例如 BCrypt） */
    private String password;

    /** 展示名 */
    private String nickname;

    /** 角色名，例如 admin / user（Sa-Token 的角色校验用） */
    private String role;

    // ==================== 构造器 ====================

    public User() {
    }

    public User(Long id, String username, String password, String nickname, String role) {
        this.id = id;
        this.username = username;
        this.password = password;
        this.nickname = nickname;
        this.role = role;
    }

    // ==================== getter / setter ====================
    // 说明：真实项目建议使用 Lombok 的 @Data 注解省去这些样板代码，
    // 这里为了不引入额外依赖、方便阅读，手写标准 getter/setter。

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }
}
```

再用一个静态"伪数据库"保存两个账号（一个管理员、一个普通用户）：

```java
package com.example.satoken.service;

import com.example.satoken.entity.User;

import java.util.HashMap;
import java.util.Map;

/**
 * 模拟用户数据源
 *
 * 真实项目中这里应该是 UserMapper 基于 MyBatis-Plus 查询数据库。
 * 为了聚焦认证鉴权主题，这里用 Map 硬编码两个测试账号。
 */
public class MockUserStore {

    /** 模拟用户表：key 为用户名，value 为用户信息 */
    private static final Map<String, User> USER_TABLE = new HashMap<>();

    // 静态代码块：类加载时初始化两个测试账号
    static {
        // 管理员账号：zhangsan，拥有 admin 角色
        USER_TABLE.put("zhangsan", new User(1L, "zhangsan", "123456", "张三", "admin"));
        // 普通用户账号：lisi，仅有 user 角色
        USER_TABLE.put("lisi", new User(2L, "lisi", "123456", "李四", "user"));
    }

    /**
     * 根据用户名查询用户（模拟 selectByUsername）
     *
     * @param username 用户名
     * @return 用户对象；不存在返回 null
     */
    public static User findByUsername(String username) {
        return USER_TABLE.get(username);
    }

    /**
     * 根据用户 id 查询用户（模拟 selectById）
     *
     * @param id 用户 id
     * @return 用户对象；不存在返回 null
     */
    public static User findById(Long id) {
        // 遍历 Map 找到 id 匹配的记录
        for (User user : USER_TABLE.values()) {
            if (user.getId().equals(id)) {
                return user;
            }
        }
        return null;
    }
}
```

### 3.6 登录 / 登出控制器：一行代码完成认证

这是全篇**最重要的代码**——登录就一行 `StpUtil.login(id)`：

```java
package com.example.satoken.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.example.satoken.entity.User;
import com.example.satoken.service.MockUserStore;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 认证控制器：登录、登出、查询当前登录用户
 *
 * 整个类的核心魅力在于：登录/登出都只是 StpUtil 的一行调用，
 * 你完全不用关心 Token 怎么生成、存在哪、怎么校验，Sa-Token 全部帮你搞定。
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    /**
     * 登录接口
     *
     * POST /auth/login  body: {"username":"zhangsan","password":"123456"}
     *
     * @param username 用户名（真实项目中建议使用 DTO + @Valid 校验）
     * @param password 密码
     * @return 登录结果：成功时返回 token 值
     */
    @PostMapping("/login")
    public Map<String, Object> login(@RequestParam String username, @RequestParam String password) {
        // 1. 根据用户名查用户（演示：内存查询；真实项目：userService.getOne(...)）
        User user = MockUserStore.findByUsername(username);

        // 2. 用户不存在 或 密码不匹配，直接抛出业务异常（简单起见这里用 IllegalArgumentException）
        //    真实项目里应抛出自定义业务异常，由全局异常处理器统一转换
        if (user == null || !user.getPassword().equals(password)) {
            throw new IllegalArgumentException("用户名或密码错误");
        }

        // 3. ★ 核心一行：为这个用户签发 Token（Sa-Token 自动完成：
        //    生成 token 字符串 -> 建立 token 与 loginId 的映射 -> 写入会话存储）
        StpUtil.login(user.getId());

        // 4. 组装返回值
        Map<String, Object> result = new HashMap<>();
        // 把 token 值返回给前端，前端保存并在后续请求头中携带
        result.put("token", StpUtil.getTokenValue());
        result.put("userId", user.getId());
        result.put("nickname", user.getNickname());
        result.put("message", "登录成功");
        return result;
    }

    /**
     * 登出接口
     *
     * 登出无需入参：只要请求头携带 token，Sa-Token 就能定位到当前会话并删除
     * 注意：重复调用不会报错（Sa-Token 对"未登录登出"做了幂等处理）
     */
    @PostMapping("/logout")
    public Map<String, Object> logout() {
        // ★ 核心一行：删除当前 Token 对应的会话，Token 即刻失效
        // 若当前未登录，此方法默认也能安全执行（不会抛异常）
        StpUtil.logout();
        // 返回结果
        Map<String, Object> result = new HashMap<>();
        result.put("message", "登出成功");
        return result;
    }

    /**
     * 查询当前登录用户信息
     *
     * 该接口要求登录：没有 token 或 token 失效时，
     * Sa-Token 直接抛 NotLoginException，被全局异常处理器转成 401
     */
    @GetMapping("/info")
    public Map<String, Object> info() {
        // 取当前登录用户 id（未登录时这里就会抛异常，后面的代码不会执行）
        Long loginId = StpUtil.getLoginIdAsLong();

        // 根据 id 查用户（演示：内存查询）
        User user = MockUserStore.findById(loginId);

        // 组装返回：不回传密码，安全第一
        Map<String, Object> result = new HashMap<>();
        result.put("userId", user.getId());
        result.put("username", user.getUsername());
        result.put("nickname", user.getNickname());
        result.put("role", user.getRole());

        // 附带演示 Token Session 的用法：往会话里存一条数据再取出来
        StpUtil.getSession().set("lastVisitTime", System.currentTimeMillis());
        result.put("lastVisitTime", StpUtil.getSession().get("lastVisitTime"));
        return result;
    }
}
```

注意 `login` 接口里我们对参数做了简化处理：直接在方法签名用 `@RequestParam`。真实项目中一定要用 **DTO + Bean Validation**（`@NotBlank`、`@Valid`）做入参校验，ruoyi-ai 也是这么做的。这个点可以放进面试题里考。

### 3.7 测试接口：注解式鉴权演示

现在写一个"受保护的业务接口"，把三种鉴权方式的区别一次看明白：

```java
package com.example.satoken.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 业务接口：演示 Sa-Token 的三种注解鉴权方式
 *
 * 注意：这些接口都使用了 @SaCheckXxx 注解，由 SaTokenConfig 里
 * 注册的 SaInterceptor 在进入 Controller 方法前完成校验。
 */
@RestController
@RequestMapping("/api")
public class UserController {

    /**
     * 游客可访问：未登录也能调用
     *
     * 演示"不写任何注解 = 放行"，适合公开内容接口
     */
    @GetMapping("/public/time")
    public Map<String, Object> publicTime() {
        // 简单的业务返回
        Map<String, Object> result = new HashMap<>();
        result.put("message", "这是一个公开接口，无需登录");
        result.put("serverTime", System.currentTimeMillis());
        return result;
    }

    /**
     * 登录即可访问：登录了就能调用，不区分角色
     *
     * @SaCheckLogin 是频率最高的注解，等价于在方法第一行调用 StpUtil.checkLogin()
     */
    @SaCheckLogin                          // 注解：必须先登录
    @GetMapping("/user/profile")
    public Map<String, Object> profile() {
        // 取出当前登录用户 id
        Long loginId = StpUtil.getLoginIdAsLong();
        // 组装返回
        Map<String, Object> result = new HashMap<>();
        result.put("loginId", loginId);
        result.put("message", "登录用户可以访问的个人资料接口");
        return result;
    }

    /**
     * 需要特定角色：只有 admin 角色能调用
     *
     * @SaCheckRole("admin")：当前登录账号必须拥有 admin 角色
     */
    @SaCheckRole("admin")                  // 注解：必须为 admin 角色
    @GetMapping("/admin/statistics")
    public Map<String, Object> statistics() {
        Map<String, Object> result = new HashMap<>();
        result.put("message", "管理员专属接口：系统统计报表");
        result.put("totalUsers", 1024);
        return result;
    }

    /**
     * 需要特定权限码：拥有 user:delete 权限码才能调用
     *
     * 权限码由我们自己在 StpInterfaceImpl 中提供（见 3.8 节）
     * 注意：有权限码 ≠ 有角色，权限模型更细粒度
     */
    @SaCheckPermission("user:delete")      // 注解：必须拥有 user:delete 权限码
    @DeleteMapping("/user/{id}")
    public Map<String, Object> deleteUser(@PathVariable Long id) {
        Map<String, Object> result = new HashMap<>();
        result.put("message", "删除用户成功");
        result.put("deletedUserId", id);
        return result;
    }
}
```

> 知识提醒：`@DeleteMapping` 需要导入 `org.springframework.web.bind.annotation.DeleteMapping`，上面为了精简 import 省略了，实际编译需要补上（真实项目请用 IDE 自动补全 import）。

### 3.8 StpInterfaceImpl：告诉 Sa-Token"用户的角色和权限码是什么"

这是 Sa-Token 唯一的业务扩展点所在。**不实现这个接口，`@SaCheckPermission` 和 `@SaCheckRole` 永远校验失败**（权限列表为空）。

```java
package com.example.satoken.service;

import cn.dev33.satoken.stp.StpInterface;
import com.example.satoken.entity.User;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 权限数据提供者：实现 Sa-Token 的 StpInterface 接口
 *
 * 这是 Sa-Token 与业务系统对接的"唯一接口"。
 * Sa-Token 在每次校验权限/角色时，会回调下面的两个方法，
 * 获取当前登录用户的【角色列表】与【权限码列表】，再与注解要求的做比对。
 *
 * 真实项目中：这里通常是"查数据库"（关联查询 sys_user_role、sys_role_menu 等表），
 * 性能瓶颈时可以加 Redis 缓存。ruoyi-ai 正是基于此接口结合 MyBatis-Plus 查询。
 */
@Component
public class StpInterfaceImpl implements StpInterface {

    /**
     * 返回指定登录 id 拥有的权限码列表
     *
     * @param loginId   当前登录用户 id
     * @param loginType 登录类型（多账号体系时才用到，本项目只有一个，默认传 "login"）
     * @return 权限码集合，例如 ["user:add", "user:delete", "ai:chat"]
     */
    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        // 根据 id 查用户信息（演示：内存查询）
        Long userId = Long.valueOf(loginId.toString());
        User user = MockUserStore.findById(userId);

        // 定义权限码集合
        List<String> permissionList = new ArrayList<>();
        // 所有登录用户默认拥有基础权限：可以对话
        permissionList.add("ai:chat");
        permissionList.add("user:query");

        // 管理员额外拥有增删改权限
        if (user != null && "admin".equals(user.getRole())) {
            permissionList.add("user:add");
            permissionList.add("user:edit");
            permissionList.add("user:delete");   // 对应 @SaCheckPermission("user:delete")
        }
        return permissionList;
    }

    /**
     * 返回指定登录 id 拥有的角色列表
     *
     * @param loginId   当前登录用户 id
     * @param loginType 登录类型
     * @return 角色集合，例如 ["admin"]
     */
    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        // 根据 id 查用户信息
        Long userId = Long.valueOf(loginId.toString());
        User user = MockUserStore.findById(userId);

        // 角色列表
        List<String> roleList = new ArrayList<>();
        if (user != null) {
            roleList.add(user.getRole());   // 例如 "admin" 或 "user"
        }
        return roleList;
    }
}
```

至此，我们的最小闭环全部代码完成。梳理一下运行时序，帮助记忆：

```
POST /auth/login （放行，无拦截）
   ↓ Controller 校验用户名密码
   ↓ StpUtil.login(1)   -> 生成 token 并登记
   ↓ 返回 token 给前端
----------------------------------------------
GET /api/user/profile （进入 SaInterceptor）
   ↓ 检测到方法上有 @SaCheckLogin
   ↓ StpUtil.checkLogin()：从请求头取 satoken，查会话 -> 未登录抛 NotLoginException
   ↓ 通过 -> 进入方法体
----------------------------------------------
DELETE /api/user/1 （进入 SaInterceptor）
   ↓ 检测到 @SaCheckPermission("user:delete")
   ↓ StpUtil.checkPermission("user:delete")
   ↓ 回调 StpInterfaceImpl.getPermissionList() 取得权限码
   ↓ 包含 "user:delete" -> 放行；否则抛 NotPermissionException
```

### 3.9 二选一：除了拦截器，还能用过滤器（Filter）吗？

不少同学会问：Sa-Token 为什么用拦截器而不是过滤器？两者都能拦请求，区别在哪？

| 对比点 | 拦截器（HandlerInterceptor，Sa-Token 默认） | 过滤器（Filter，Servlet 规范） |
| --- | --- | --- |
| 执行时机 | 在 HandlerMapping 定位到 Controller 方法之后、进入方法之前 | 在最外层，比拦截器更早 |
| 能否拿到 Controller 方法信息 | 能，所以**能解析方法上的注解**（@SaCheckLogin 依赖这一点） | 不能直接拿到方法，注解鉴权实现困难 |
| 被 Spring 管理 | 是，可以注入 Service | 普通 Filter 不受 Spring 管理，需注册为 Bean 或通过 FilterRegistrationBean |
| 影响范围 | 只作用于 Spring MVC 的请求 | 对静态资源、转发、错误页等都生效 |
| Sa-Token 支持 | SaInterceptor（默认推荐） | SaServletFilter（官方也提供） |

**结论**：如果只需要"路径级"鉴权（如 `/admin/**` 必须登录），用 `SaServletFilter` 过滤器写正则一份配置即可；如果要做"注解级"细粒度鉴权（推荐，更优雅），必须用 `SaInterceptor`。ruoyi-ai 用的是注解 + 拦截器方案，我们本文也采用官方推荐做法。

如果确实想用过滤器做路径级拦截，Sa-Token 提供 `SaServletFilter`，示例：

```java
// 伪代码示例：使用 SaServletFilter 做路径级登录校验（了解即可）
@Bean
public SaServletFilter saServletFilter() {
    return new SaServletFilter()
            .addInclude("/**")                              // 拦截所有请求
            .addExclude("/auth/login", "/favicon.ico")      // 放行登录等公开路径
            .setAuth(obj -> StpUtil.checkLogin())           // 校验：必须登录
            .setError(e -> "{\"code\":401,\"msg\":\"请先登录\"}"); // 未登录时的返回体
}
```

---

## 四、运行验证：启动 + curl 全流程测试

### 4.1 启动项目

在项目根目录执行：

```bash
# 方式一：Maven 插件直接启动（推荐，无需先打包）
mvn spring-boot:run

# 方式二：先打包再 java -jar 启动
mvn clean package -DskipTests
java -jar target/hello-satoken-1.0.0.jar
```

看到以下日志即启动成功：

```
2026-08-22T10:00:00.123+08:00  INFO ... Tomcat started on port(s): 8080 (http)
2026-08-22T10:00:00.456+08:00  INFO ... Started HelloSatokenApplication in 1.23 seconds
>>> hello-satoken 启动成功！访问 http://localhost:8080/auth/login 进行登录测试
```

### 4.2 场景一：访问受保护接口，未登录被拦截

第一次测试，我们**不带任何 Token** 直接访问登录用户接口：

```bash
# 未登录访问受保护接口（期望返回 401）
curl -i http://localhost:8080/api/user/profile
```

预期输出（重点看 HTTP 状态码 401 和 JSON 提示）：

```http
HTTP/1.1 200        # 注意：HTTP 状态码是 200！真正的"业务判定"在响应体里
```
（基于我们当前写的全局异常处理器，HTTP 状态码默认仍是 200，业务码在 body 中）

```json
{
  "code": 401,
  "message": "未能读取到有效token",
  "status": 401
}
```

> 实测经验提示：如果前端希望 HTTP 状态码本身也是 401，需要在全局异常处理器上补 `@ResponseStatus(HttpStatus.UNAUTHORIZED)` 注解，或者使用 `ResponseEntity.status(401)` 返回。ruoyi-ai 的 `R<T>` 方案也是"HTTP 200 + 业务码"风格，前端统一判断业务码。两种风格各有利弊，团队统一即可。

### 4.3 场景二：错误密码，登录被拒绝

```bash
# 密码错误（期望返回业务错误）
curl -X POST "http://localhost:8080/auth/login?username=zhangsan&password=wrong"
```

预期输出：

```json
{
  "timestamp": "2026-08-22T10:01:00.000+00:00",
  "status": 500,
  "error": "Internal Server Error",
  "message": "用户名或密码错误"
}
```

说明：我们偷懒直接抛了 `IllegalArgumentException`，走了 Spring Boot 默认的 500 错误页。真实项目应定义 `LoginException extends RuntimeException` 并加对应的异常处理器，返回业务码 400/500 而非系统级 500。

### 4.4 场景三：正确登录，拿到 Token

```bash
# 管理员登录
curl -X POST "http://localhost:8080/auth/login?username=zhangsan&password=123456"
```

预期输出（token 值每次都不一样是正常的）：

```json
{
  "token": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "userId": 1,
  "nickname": "张三",
  "message": "登录成功"
}
```

### 4.5 场景四：携带 Token 访问受保护接口

拿到上面的 token，放进请求头 `satoken: <token值>`（token 名称就是 yml 里配置的 `satoken`）：

```bash
# 携带 token 访问登录用户接口（期望成功）
curl -H "satoken: a1b2c3d4-e5f6-7890-abcd-ef1234567890" \
     http://localhost:8080/api/user/profile
```

预期输出：

```json
{
  "loginId": 1,
  "message": "登录用户可以访问的个人资料接口"
}
```

再把 token 放进 **Cookie** 一样有效（Sa-Token 默认同时支持请求头和 Cookie 两种携带方式）：

```bash
curl --cookie "satoken=a1b2c3d4-e5f6-7890-abcd-ef1234567890" \
     http://localhost:8080/auth/info
```

输出：

```json
{
  "userId": 1,
  "username": "zhangsan",
  "nickname": "张三",
  "role": "admin",
  "lastVisitTime": 1724301660000
}
```

### 4.6 场景五：角色与权限校验

```bash
# 管理员访问 admin 接口（有 admin 角色，成功）
curl -H "satoken: a1b2c3d4-e5f6-7890-abcd-ef1234567890" \
     http://localhost:8080/api/admin/statistics

# 管理员删除用户（拥有 user:delete 权限码，成功）
curl -X DELETE -H "satoken: a1b2c3d4-e5f6-7890-abcd-ef1234567890" \
     http://localhost:8080/api/user/1

# 换成普通用户 lisi 登录
TOKEN_LISI=$(curl -s -X POST "http://localhost:8080/auth/login?username=lisi&password=123456" | sed 's/.*"token":"\([^"]*\)".*/\1/')
# 普通用户访问 admin 接口（无 admin 角色 -> 4003 业务错误）
curl -H "satoken: $TOKEN_LISI" http://localhost:8080/api/admin/statistics
```

普通用户访问 admin 接口的预期输出：

```json
{
  "code": 403,
  "message": "无此角色：admin",
  "status": 403
}
```

### 4.7 场景六：登出后 Token 立即失效

```bash
# 登出
curl -X POST -H "satoken: a1b2c3d4-e5f6-7890-abcd-ef1234567890" \
     http://localhost:8080/auth/logout

# 登出后再访问（期望 401）
curl -H "satoken: a1b2c3d4-e5f6-7890-abcd-ef1234567890" \
     http://localhost:8080/api/user/profile
```

第二次请求预期输出：

```json
{
  "code": 401,
  "message": "Token无效：当前会话已登出",
  "status": 401
}
```

### 4.8 顺手演示：踢人下线 / 多端登录

由于 yml 里配置了 `is-concurrent: true`（同账号多端登录）与 `is-share: false`（每端各分配新 token），同一账号可以同时持有多个 token。Sa-Token 还提供"踢人"能力，控制台里我们可以这样验证：

```java
// 示例：在 Controller 里加一个管理接口，演示管理员强制踢人
StpUtil.kickout(2L);                    // 把 id=2（lisi）的全部会话踢下线
StpUtil.logout(2L);                     // 把 id=2 的会话删除（比 kickout 更彻底，连 token 记录都删）
```

被踢的用户下一次携带旧 token 请求时，将得到类似"Token无效：已被踢下线"的 401 提示。这在"管理员禁用一个用户后强制其下线"的场景非常实用。

---

## 五、项目对照：ruoyi-ai 中 Sa-Token 是怎么用的

至此我们已经完成了入门闭环。现在把视野拉回到 **ruoyi-ai 真实项目**，看看它把这些概念落到了哪些地方。对照阅读时你会发现：**原理完全一样，只是加了业务包装**。

### 5.1 依赖对照

ruoyi-ai 的 `pom.xml` 中与认证相关的核心依赖：

```xml
<!-- 对比第 3.1 节：多了一个 Redis 会话插件 -->
<dependency>
    <groupId>cn.dev33</groupId>
    <artifactId>sa-token-redis-jackson</artifactId>
</dependency>

<!-- 对比第 3.1 节：多了一个 JWT 插件 -->
<dependency>
    <groupId>cn.dev33</groupId>
    <artifactId>sa-token-jwt</artifactId>
</dependency>
```

也就是说，ruoyi-ai 在基础 starter 之上还启用了两种增强：

1. **Redis 插件**：会话不存内存，改存 Redis。这是集群部署（多实例）时的必需品——否则用户在一台机器登录，请求被负载均衡到另一台机器就"不认识"这个 token 了；
2. **JWT 插件**：Token 变成自包含签名的 JWT 字符串，服务端无需存储会话，天然无状态。

### 5.2 配置对照（ruoyi-ai 风格）

```yaml
sa-token:
  token-name: satoken              # 同上文的 satoken
  timeout: 2592000                 # 30 天 = 2592000 秒（对话类系统通常给长一点）
  active-timeout: -1               # 不做活跃续期
  is-concurrent: true              # 同账号可多端在线
  is-share: false                  # 每次登录独立签发 token
  token-style: uuid                # token 风格 uuid
  is-log: false
  # 与 JWT 插件配合的配置（ruoyi-ai 风格）
  jwt-secret-key: ruoyi-ai-jwt-secret-key   # JWT 签名密钥（真实环境务必走环境变量/配置中心）
```

常见误区提醒：`sa-token` 配置项直接写在 `application.yml` 顶层键（`sa-token:`），不是挂在 `spring:` 下面。很多初学者把它写进 `spring:` 里导致不生效，这是排查配置失效时的第一检查项。

### 5.3 代码对照

| 本文示例代码 | ruoyi-ai 中的对应 | 角色 |
| --- | --- | --- |
| `StpInterfaceImpl` | 基于 MyBatis-Plus 查询 `sys_user`、`sys_user_role`、`sys_role_permission` 等表返回角色/权限码列表 | 权限数据来源 |
| `SaTokenConfig` | 注册 SaInterceptor + 全路径拦截 + 放行 `/login`、`/register` 等 | 拦截配置 |
| `AuthController.login()` | `LoginController`，调用 `StpUtil.login(userId)` 后返回 token | 登录入口 |
| `GlobalExceptionHandler` | 统一处理 `NotLoginException`/`NotPermissionException` 并包装成 `R<T>` 返回体 | 异常转换 |
| `/api/user/profile` 的 `@SaCheckLogin` | 业务 Controller 上的 `@SaCheckLogin` / `@SaCheckPermission("ai:chat")` 等 | 方法级鉴权 |

ruoyi-ai 中典型的"AI 对话必须登录"写法，看一眼就懂：

```java
/**
 * AI 对话接口（ruoyi-ai 风格伪码）
 *
 * 登录才能聊天，且消耗用户配额，所以必须拿到登录用户 id
 */
@SaCheckLogin
@PostMapping("/chat")
public R<ChatResponse> chat(@RequestBody ChatRequest request) {
    // 关键：从 Sa-Token 会话中取出当前登录用户 id，用于记账/限流
    Long userId = StpUtil.getLoginIdAsLong();
    // 调用业务服务执行 AI 对话
    ChatResponse response = chatService.call(userId, request.getMessages());
    // 统一响应体 R 包装返回
    return R.ok(response);
}
```

而"判断会话里是否有某个用户配置"这种需求，则使用 Token Session：

```java
// ruoyi-ai 风格伪码：把当前用户的对话上下文快照存进 Token Session
StpUtil.getSession().set("chatContext_" + sessionId, contextSnapshot);

// 读取
Object snapshot = StpUtil.getSession().get("chatContext_" + sessionId);
```

### 5.4 ruoyi-ai 的 JWT 实践（重点）

ruoyi-ai 使用了 `sa-token-jwt` 插件把默认的"随机串"升级为 JWT。集成步骤：

**第一步：pom.xml 增加依赖**

```xml
<!-- JWT 插件：让 Sa-Token 签发的 token 变为 JWT 格式 -->
<dependency>
    <groupId>cn.dev33</groupId>
    <artifactId>sa-token-jwt</artifactId>
    <version>1.37.0</version>
</dependency>
```

**第二步：application.yml 增加密钥**

```yaml
sa-token:
  jwt-secret-key: ruoyi-ai-jwt-secret-key   # 签名密钥
```

**第三步：代码中显式开启 JWT 模式**

引入插件后，Sa-Token 并不会自动切换到 JWT，需要在 `SaManager` 初始化时显式设置。ruoyi-ai 一般用一个配置类或启动时初始化：

```java
package com.example.satoken.config;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.jwt.StpLogicJwtForSimple;
import cn.dev33.satoken.stp.StpLogic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * JWT 配置类：把 Sa-Token 默认的"随机串存取"模式切换为 JWT 模式
 *
 * JWT 模式的差异：
 * 1. token 值为 JWT 字符串（header.payload.signature 三段式）；
 * 2. 服务端不再存储会话，token 自带用户信息（默认只存 loginId）；
 * 3. 天然无状态，适合多服务/网关场景；
 * 4. 代价：token 一旦签发，在过期前无法在服务端主动令其失效（要支持踢人需搭配 Redis 黑名单）。
 */
@Configuration
public class SaTokenJwtConfig {

    /**
     * 注入自定义的 StpLogic 实现（JWT 版）
     *
     * @return StpLogicJwtForSimple 实例
     */
    @Bean
    public StpLogic getStpLogicJwt() {
        // 切到 JWT 模式："Simple" 表示 JWT 内只保存 loginId 等简单信息
        return new StpLogicJwtForSimple();
    }
}
```

**第四步：代码零改动**——`StpUtil.login()` 照常调用，只是这次 `StpUtil.getTokenValue()` 拿到的是一串 JWT：

```java
// 依然是同样的一行登录代码，但返回的 token 已经是 JWT 格式
StpUtil.login(userId);
String jwt = StpUtil.getTokenValue();
// jwt 形如：eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJsb2dpbklkIjoxLCJzYXRva2VuIjoidGVzdCJ9.xxxxxxxx
```

### 5.5 常见坑位清单（ruoyi-ai 实战）

整理几个真实项目里最常见的 Sa-Token 坑，面试聊到"实战经验"时可展开：

| 坑 | 现象 | 解法 |
| --- | --- | --- |
| 拦截器放行了 `/login`，但登录后 token 找不到会话 | 没接 Redis 插件却多实例部署，token 存了内存，另一台机器查不到 | 生产必须接 Redis 插件，`sa-token-redis-jackson` |
| `@SaCheckPermission` 永远失败 | 忘记实现 `StpInterface`，权限列表恒为空 | 实现 `StpInterface.getPermissionList()` |
| 前端带 token 却 401 | 请求头名称与 `token-name` 不一致（默认 `satoken`，不是 `Authorization`） | 前端统一 `satoken: xxx`，或改 `token-name: Authorization` |
| JWT 模式下踢人失效 | JWT 无状态，`logout()` 只是"本地不再认识"，token 在 JWT 校验层仍有效 | Redis 黑名单 / 版本号机制自行实现 |
| 配置写进 `spring:` 下 | 顶层键错了，全部配置不生效 | `sa-token:` 必须与 `server:`、`spring:` 平级 |
| 异常返回 HTTP 200 | 没设置 `@ResponseStatus` / 没用 ResponseEntity | 全局异常处理器统一设置状态码，或前端按业务码判断 |

---

## 六、面试题 3 道

### 面试题 1：Spring Security 和 Sa-Token 的区别是什么？为什么 ruoyi-ai 选 Sa-Token?

**考察点**：框架选型能力、对认证鉴权本质的理解。

**参考回答思路**：

从三个层次回答：

1. **定位不同**：Spring Security 是重量级的安全框架"全家桶"，提供了认证、授权、CSRF、OAuth2/OIDC 等全套能力；Sa-Token 是轻量级权限认证框架，只关注"登录、鉴权、会话管理"这几件核心事。
2. **使用体验不同**：Spring Security 需要理解和配置过滤器链、`UserDetailsService`、`GrantedAuthority` 等大量概念，默认开启的安全策略还容易带来隐患；Sa-Token 核心只有一个 `StpUtil` 静态类，登录一行 `StpUtil.login(id)`、鉴权一个注解 `@SaCheckPermission("...")`，半天即可上手。
3. **结合项目谈**：ruoyi-ai 是前后端分离的 AI 业务系统，认证场景就是"用户登录 + 接口鉴权 + 角色权限"，不需要复杂的 OAuth2 协议；选 Sa-Token 能让团队把精力放在 AI 业务上，代码量少、可读性好、二次开发成本低。当然，如果系统未来要对接标准 OAuth2/OIDC 或者有严格的安全合规审计要求，Spring Security 的生态更完整，可以再评估引入 Spring Security OAuth2 或独立网关统一认证。

### 面试题 2：Sa-Token 处理"用户权限"时，为什么必须实现 StpInterface 接口？如果不实现会怎样？

**考察点**：是否真正理解 Sa-Token 的权限数据来源机制。

**参考回答思路**：

1. **默认行为**：Sa-Token 框架本身只负责"权限的校验"（比对），不负责"权限的数据来源"（即"这个用户到底有哪些权限"）。这个数据只有业务系统自己知道，所以框架定义 `StpInterface` 接口作为约定，要求业务方实现后返回某个 `loginId` 的角色列表与权限码列表。
2. **执行流程**：当 `@SaCheckPermission("user:delete")` 生效时，`StpUtil.checkPermission()` 会调用 `SaStrategy`，内部回调 `StpInterface.getPermissionList(loginId, loginType)` 拿到权限集合，再判断是否包含 `user:delete`。包含则放行，否则抛 `NotPermissionException`。
3. **不实现的后果**：Spring 容器中找不到 `StpInterface` 的 Bean 时，Sa-Token 会使用默认的空实现，即所有用户权限列表为空——那么**任何** `@SaCheckPermission` 和 `@SaCheckRole` 校验都会失败，接口全部 403。换句话说，注解鉴权"形同虚设且全部拒绝"，只有 `@SaCheckLogin`（只判断登录态）不受影响。
4. **加分项**：真实项目中这个接口的实现要注意查询性能——不要每个请求都全量查库，可以先查用户角色，再查角色关联权限，并配合本地缓存或 Redis 缓存权限列表；数据变更时（角色被修改、权限被回收）要有缓存失效机制，否则会出现"权限改了不生效"的延迟问题。

### 面试题 3：Sa-Token 默认的 Token 和 JWT 模式有什么区别？线上如何选择？

**考察点**：无状态 vs 有状态、会话管理的本质、技术选型权衡。

**参考回答思路**：

1. **本质区别**：
   - **默认模式（随机串）**：Token 是一个不携带任何业务信息的随机字符串，服务端在内存或 Redis 中维护 `token -> loginId` 的映射。校验时反查存储。**有状态，可主动控制**（登出、踢人、下线都能立即生效）。
   - **JWT 模式**：Token 是 `Header.Payload.Signature` 三段式签名字符串，用户信息（默认 loginId）直接编码进 Payload 并用密钥签名，服务端校验时只需解签比对，**无需查存储**。**无状态，天然适合分布式**，但签发后过期前无法主动作废。
2. **优劣权衡**：
   - JWT 优点：无状态、多服务共用一套密钥即可互相认证、减轻 Redis 压力、适合网关聚合服务；缺点：无法主动踢人，Payload 不可存放敏感数据（Base64 可解码），密钥泄露风险高。
   - 随机串优点：可随时作废会话（登出/封号/踢人立竿见影）、能力强；缺点：所有服务都必须能访问共享存储（Redis），引入额外依赖。
3. **选型结论**：
   - 单体或小规模集群、需要支持"封号即下线"的管理诉求 -> 默认模式 + Redis 插件（ruoyi-ai 采用的就是这种思路，JWT 通常用于网关侧或对第三方开放的接口）；
   - 大规模无状态微服务、跨团队多系统认证 -> JWT + 黑名单/版本号机制兜底。
4. **加分项**：无论选哪种，Token 都要走 HTTPS 传输防止中间人截获；JWT 的密钥要放配置中心或环境变量而不是写死在代码里；生产环境务必给 `timeout` 设置一个合理的值，避免"永久 token"的账号安全隐患。

---

## 本 文小 结

这篇入门文章把 Sa-Token 的最小闭环完整跑了一遍，你可以单击回看这四个关键认知：

1. **它是谁**：轻量级 Java 权限认证框架，核心就一个 `StpUtil`，对比 Spring Security 胜在简单直接。
2. **它怎么用**：依赖引入 -> 配置拦截器（`SaTokenConfig`）-> 登录一行 `StpUtil.login(id)` -> 鉴权用 `@SaCheckLogin` / `@SaCheckPermission` 注解 -> 权限数据由 `StpInterface` 提供。
3. **它怎么扩展**：Redis 插件解决多实例会话共享；JWT 插件切无状态；`SaServletFilter` 切过滤器模式。
4. **ruoyi-ai 怎么落地的**：同样的核心代码 + MyBatis-Plus 查权限 + `R<T>` 统一返回 + JWT/Redis 双插件，就是你会在源码里看到的全部。

下一步（Level 2）建议：深入 `StpLogic` 源码，理解"登录链路"（`login()` -> 生 token -> 存会话 -> 写 Cookie）以及多账号体系（`StpUtil` 之外还有 `StpKit`）；再看 ruoyi-ai 里"注册/登录/手机验证码登录/三方登录"的完整实现，把认证体系彻底吃透。

---

## 附录：本文完整文件清单速查

```
pom.xml                        # 依赖：web + sa-token-spring-boot3-starter
src/main/resources/application.yml         # sa-token 顶层配置（kebab-case）
src/main/java/com/example/satoken/
├── HelloSatokenApplication.java           # 启动类
├── config/SaTokenConfig.java              # 拦截器 + 注解鉴权 + 放行路径
├── config/GlobalExceptionHandler.java     # 401 / 403 统一转换
├── controller/AuthController.java         # 登录 / 登出 / 当前用户
├── controller/UserController.java         # @SaCheckLogin / @SaCheckRole / @SaCheckPermission 演示
├── service/MockUserStore.java             # 内存"伪用户表"
├── service/StpInterfaceImpl.java          # 权限/角色数据提供者（核心扩展点）
└── entity/User.java                       # 用户实体
```

技术栈关键词：`Spring Boot 3.2`、`Sa-Token 1.37`、`JWT`、`Router Interceptor`、`SaInterceptor`、`StpInterface`、`StpUtil`。