# 微信小程序 + Spring Boot 入门：登录与数据接口

> zznursing 养老机构综合运营平台系列 · 微信小程序后端篇（入门 / Level 1）
>
> 面向读者：Java 后端工程师。本文从一个最小可运行的微信小程序后端项目出发，讲透"微信登录换取身份 + 签发 JWT + 接口鉴权"这条最核心的主链路，最后对照 zznursing 真实项目说明生产环境还差哪些工程化能力。
>
> 项目背景：zznursing 是一个"物联网感知 + AI 智能 + 移动互联"三位一体的养老机构平台，本系列已覆盖 Spring Boot IoT 后端（01 篇）与百度千帆 AI（02 篇）。本文聚焦移动互联层的微信小程序后端。

---

## 一、项目背景：为什么是微信小程序

### 1.1 什么是微信小程序

微信小程序（WeChat Mini Program）是微信生态内的一种**免安装、即点即用**的应用形态。用户通过微信扫码、搜索或好友分享即可打开，无需下载 App、无需注册账号，用完即走。它依托微信的账号体系，天然解决了"我是谁"的身份问题——用户打开小程序的那一刻，微信已经替他完成了登录。

对技术人来说，小程序有几个关键词值得注意：

- **双线程架构**：渲染层（WebView）与逻辑层（JsCore 独立线程）分离，`setData` 驱动视图更新，避免直接操作 DOM；
- **能力受控**：小程序运行在微信的沙箱里，不能随意访问系统 API，数据通信必须走微信提供的接口，安全性由平台托管；
- **云端直连**：小程序通过 `wx.request` 直接向业务后端发起 HTTPS 请求，也能用 `wx.connectSocket` 建立 WebSocket 长连接。

### 1.2 zznursing 为什么选小程序做家属端

zznursing 面向两类用户：**机构运营人员**（护工、护士、管理员）使用 Vue3 管理后台，而**老人家属**使用移动端查看健康数据。家属端为什么选微信小程序而不是独立 App？

1. **零安装成本**：家属的典型用户是 40~70 岁的中老年人，让他们下载、注册一个陌生 App 的转化成本极高；小程序扫码即用，微信里顺手就打开了。
2. **微信身份即账号**：家属不需要记密码，`wx.login()` 之后后端就能凭 openid 识别唯一用户，登录摩擦几乎为零。
3. **微信消息触达**：告警通知可以直接走微信订阅消息（订阅消息/模板消息）推送到家属手机，这是独立 App 需要自建推送服务才能做到的，而微信生态内是免费内置的。
4. **微信支付**：未来的床位费、护理费在线缴纳可以直接对接微信支付，天然打通支付闭环。

### 1.3 小程序端承担的核心功能

对照 zznursing 总体架构，家属端小程序主要完成四件事：

| 功能 | 说明 | 后端支撑 |
|------|------|----------|
| 健康数据展示 | 心率、血压、血氧、体温、步数实时查看与趋势曲线 | 设备服务 + 华为 IoTDA 数据链路 |
| 告警通知 | 心率异常、跌倒检测、设备离线等消息推送 | 告警服务 + 微信订阅消息 |
| AI 智能问答 | 家属直接问"父亲血压偏高怎么办"，流式返回建议 | AI 服务 + 百度千帆大模型 |
| 老人档案 | 绑定老人、查看档案与体检报告 | 用户服务 + 健康档案模块 |

而这一切的**入口都是登录**。不知道"你是谁"，就无法知道"你能看哪位老人的数据、该向你推哪条告警"。所以本篇文章用一整个最小项目，把登录链路上最核心的技术点讲清楚。

---

## 二、核心概念：登录、JWT 与接口设计

### 2.1 微信登录完整链路（code → openid → 自定义凭证）

微信小程序**不提供账号密码登录**，它使用一套基于临时凭证（code）的授权换证流程，全程分为三步：

```
家属打开小程序
    │
    ▼
① wx.login() 拿到临时 code（5 分钟有效、一次性）
    │  把 code 发给自己的后端（HTTPS POST /api/auth/login）
    ▼
② 后端拿 appid + appsecret + code 调微信服务器
   GET https://api.weixin.qq.com/sns/jscode2session
    │
    ▼
③ 微信返回 openid（用户唯一标识）+ session_key（会话密钥）
   后端用 openid 查库/建库 → 签发自己的登录凭证（JWT）→ 返回小程序
    │
    ▼
④ 小程序把凭证存 Storage，后续请求带在请求头里
```

几个必须记牢的关键点（也是面试高频点）：

- **code 是一次性的**：任何一个 code 兑换一次后立即失效，有效期 5 分钟，用错立刻报 `40163 code been used` 或 `40029 invalid code`。这保证了即使 code 在网络传输中被截获，攻击者也来不及重放；
- **appSecret 永远不能出现在前端**：换取 openid 的请求必须由后端发起。如果 appSecret 泄露到小程序包或网络里，任何人都能冒充你的小程序向微信要 openid；
- **openid 是"应用级"唯一标识**：同一个用户在不同小程序下 openid 不同。要跨应用识别用户，需要 UnionID（开放平台账号体系），zznursing 单小程序场景用 openid 足够；
- **session_key 用于解密手机号等加密数据**：如 `phoneNumber` 需要前端把 `encryptedData + iv` 发给后端，后端用 `session_key` 做 AES 解密。它不是登录凭证，不能当 token 用。

### 2.2 JWT：后端签发的"通行证"

后端拿到 openid 后，需要给小程序发一个**后续请求都能用的凭证**。本文选用 JWT（JSON Web Token）。

JWT 是一个自包含的令牌，形如 `xxxxx.yyyyy.zzzzz`，由三段组成：

- **Header**：算法信息（如 HS256）；
- **Payload**：业务声明，比如 `sub`（用户 ID）、`openid`、`role`、过期时间 `exp`；
- **Signature**：用密钥对前两段做签名。

```
eyJhbGciOiJIUzI1NiJ9.        ← Header（Base64）
.eyJzdWIiOiIxIiwicm9sZSI6... ← Payload（Base64）
.SflKxwRJSMeKKF2QT4fwpMeJf   ← 签名（密钥计算）
```

其核心价值是**无状态**：服务端不存 session，令牌本身就是"已经登录过"的证明，验签通过即信任其内容。JWT 本身不加密（Base64 可读），所以**绝不能把密码等敏感信息放进 Payload**，且必须设置较短的过期时间并配合 HTTPS 传输。

### 2.3 小程序 ↔ 后端的接口通信约定

小程序端用 `wx.request` 发请求，和后端约定的规矩与 Web 开发一脉相承，但有几个实践要点：

- **统一 RESTful 路径**：`POST /api/auth/login`（登录）、`GET /api/auth/user`（当前用户）、`GET /api/health-data`（健康数据）；
- **统一 JSON**：请求体、响应体都是 JSON，`Content-Type: application/json`；
- **鉴权方式**：登录接口放行；其余接口要求请求头 `Authorization: Bearer <JWT>`；
- **状态码语义化**：200 正常、400 参数错误、401 未登录或凭证过期、403 无权限。

### 2.4 数据安全要点

- code 一次性 + 5 分钟有效（防重放）；
- appSecret 仅存在于后端配置（防冒充）；
- 传输全程 HTTPS（微信要求小程序请求域名必须是备案 + HTTPS 的合法域名）；
- openid 属于敏感个人信息，不应直接返回给前端暴露无遗，本文示例为了教学保留了它，生产环境建议只返回业务侧的用户 ID。

概念说完，下面进入正题：**从零搭一个能编译、能跑、不需要真实微信账号的小程序后端**。

---

## 三、从零搭建代码：最小微信小程序后端

### 3.0 项目总览

我们使用 **Maven + Java 17 + Spring Boot 3.2.x** 搭建，核心依赖只有 Web、JPA、Security、H2 与 JWT 库，数据落 H2 内存库，**不依赖任何真实微信 AppID/AppSecret**——登录走 `MockWeChatAuthService` 本地模拟。

完整目录结构如下：

```
miniapp-backend/
├── pom.xml
└── src
    ├── main
    │   ├── java/com/zznursing/miniapp/
    │   │   ├── MiniappApplication.java          启动类
    │   │   ├── config/
    │   │   │   ├── WeChatProperties.java        微信配置（app-id/app-secret/mock）
    │   │   │   └── SecurityConfig.java          Spring Security 配置
    │   │   ├── controller/
    │   │   │   ├── AuthController.java          登录 / 当前用户接口
    │   │   │   └── HealthDataController.java    健康数据接口
    │   │   ├── dto/
    │   │   │   ├── LoginRequest.java            登录请求（携带 code）
    │   │   │   ├── LoginResponse.java           登录响应（携带 token）
    │   │   │   └── WeChatSession.java           微信会话（openid / session_key）
    │   │   ├── entity/
    │   │   │   └── UserEntity.java              用户 JPA 实体
    │   │   ├── repository/
    │   │   │   └── UserRepository.java          用户数据访问层
    │   │   ├── security/
    │   │   │   ├── JwtUtil.java                 JWT 生成与校验工具
    │   │   │   └── JwtAuthFilter.java           JWT 鉴权过滤器
    │   │   └── service/
    │   │       ├── AuthService.java             登录业务逻辑
    │   │       ├── WeChatAuthService.java       微信登录接口抽象
    │   │       └── MockWeChatAuthService.java   微信登录 Mock 实现
    │   └── resources/
    │       └── application.yml                  配置文件
    └── test/java/com/zznursing/miniapp/
        └── MiniappApplicationTests.java         测试类
```

> 说明：为了保持登录链路完整，示例额外引入了 `LoginRequest`、`LoginResponse`、`WeChatSession` 三个 DTO 记录类和 `WeChatAuthService` 接口，它们都是为了项目能编译运行所必需的，不属于"额外工程"。

下面按依赖 → 配置 → 数据层 → 鉴权层 → 业务层 → 接口层 → 启动与测试的顺序逐文件展开。**所有 Java 代码均带逐行中文注释**。

---

### 3.1 pom.xml：声明依赖

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <!-- Maven 模型版本，固定 4.0.0 -->
    <modelVersion>4.0.0</modelVersion>

    <!-- 继承 Spring Boot 父工程，统一管理依赖版本与插件 -->
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.2.5</version>
        <relativePath/>
    </parent>

    <!-- 本项目坐标：组名 com.zznursing，构件名 miniapp-backend -->
    <groupId>com.zznursing</groupId>
    <artifactId>miniapp-backend</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <name>miniapp-backend</name>
    <description>zznursing 微信小程序后端（登录 + 数据接口入门示例）</description>

    <properties>
        <!-- 统一使用 Java 17 -->
        <java.version>17</java.version>
        <!-- jjwt 三件套统一版本，0.12.x 是 2024 年主流版本 -->
        <jjwt.version>0.12.6</jjwt.version>
    </properties>

    <dependencies>
        <!-- Spring MVC：提供 @RestController、参数绑定等 Web 能力 -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>

        <!-- Spring Data JPA + Hibernate：提供实体持久化能力 -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>

        <!-- Spring Security：提供过滤器链与接口鉴权能力（JwtAuthFilter/SecurityConfig 依赖它） -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-security</artifactId>
        </dependency>

        <!-- H2 内存数据库：演示用零安装，生产环境替换为 MySQL -->
        <dependency>
            <groupId>com.h2database</groupId>
            <artifactId>h2</artifactId>
            <scope>runtime</scope>
        </dependency>

        <!-- jjwt-api：JWT 的编译期 API -->
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-api</artifactId>
            <version>${jjwt.version}</version>
        </dependency>
        <!-- jjwt-impl：JWT 的实现，运行时才需要 -->
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-impl</artifactId>
            <version>${jjwt.version}</version>
            <scope>runtime</scope>
        </dependency>
        <!-- jjwt-jackson：JWT 内容与 Jackson 的序列化桥接 -->
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-jackson</artifactId>
            <version>${jjwt.version}</version>
            <scope>runtime</scope>
        </dependency>

        <!-- Lombok：用注解省略 getter/setter 等样板代码 -->
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>

        <!-- Spring Boot 测试套件：JUnit5 + MockMvc + jsonPath 等 -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <!-- Spring Boot 打包插件：mvn package 后得到可执行 jar -->
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <configuration>
                    <!-- 打包时排除 Lombok，避免打进最终 jar -->
                    <excludes>
                        <exclude>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                        </exclude>
                    </excludes>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

**几个选型说明（面试可能被问到）：**

- `spring-boot-starter-security` 不在需求的最小清单里，但 `SecurityConfig` 依赖它，加入后才可编译运行。真实项目中安全是标配，本文用它展示"过滤器链 + 无状态 JWT"的标准姿势；
- H2 在 `runtime` 作用域，只服务本地演示；生产把 datasource 换成 MySQL 即可，JPA 代码完全不用动；
- jjwt 拆成 api / impl / jackson 三个构件是官方推荐做法：api 供编译，impl 运行期生效，jackson 负责把 Claims 里的复杂类型序列化。

---

### 3.2 application.yml：配置文件

```yaml
# 服务对外端口，微信小程序在微信公众平台配置的合法域名指向的端口
server:
  port: 8080

spring:
  application:
    name: zznursing-miniapp

  datasource:
    # H2 内存库：应用停止数据即清空；DB_CLOSE_DELAY=-1 保证连接保持到 JVM 退出
    url: jdbc:h2:mem:miniapp;DB_CLOSE_DELAY=-1
    # H2 驱动类名
    driver-class-name: org.h2.Driver
    # H2 默认用户名
    username: sa
    # 默认无密码
    password: ""

  jpa:
    hibernate:
      # create-drop：启动建表、停止删表，演示方便；生产环境改 validate + 统一管理 DDL
      ddl-auto: create-drop
    # 控制台打印 SQL，方便观察 Hibernate 生成的语句；生产环境关闭
    show-sql: true

  h2:
    console:
      # 开启 H2 网页控制台，浏览器访问 /h2-console 可用 sa 登录查看表数据
      enabled: true

# 微信小程序配置（自定义前缀 wechat，绑定到 WeChatProperties）
wechat:
  # mock=true：登录走本地模拟实现，无需真实小程序账号（本示例的关键）
  mock: true
  # 真实模式下替换为微信公众平台申请到的 AppID
  app-id: wx-mock-appid
  # 真实模式下替换为微信 AppSecret（绝对不能出现在前端代码中）
  app-secret: mock-secret

# JWT 配置
jwt:
  # HS256 签名密钥，要求至少 32 字节；生产环境改为环境变量注入，防止泄露
  secret: zznursing-miniapp-demo-jwt-secret-key-2024
  # token 有效期（小时），两个普通家属的常用兜底值是 72 小时
  expire-hours: 72
```

要点：`wechat.mock: true` 是让整个项目"脱离微信也能跑"的开关，`MockWeChatAuthService` 会读取它来决定走真微信还是本地模拟。JWT 密钥至少 32 字节是 jjwt 对 HS256 的硬性要求，短了启动即抛错。

---

### 3.3 WeChatProperties.java：微信配置绑定

```java
package com.zznursing.miniapp.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 微信小程序配置
 * 通过 @ConfigurationProperties 把 application.yml 中 wechat.* 前缀的配置
 * 自动绑定到本类的字段上，字段名与配置项遵循"松散绑定"规则
 * （例如 app-id 会绑定到 appId 字段）。
 */
@Data  // Lombok：自动生成 getter/setter/toString 等方法
@Component  // 声明为 Spring Bean，交给容器管理
@ConfigurationProperties(prefix = "wechat")  // 绑定 wechat 前缀的配置
public class WeChatProperties {

    /**
     * 小程序 AppID：微信公众平台上"开发管理-开发设置"中查看
     */
    private String appId;

    /**
     * 小程序 AppSecret：与 AppID 配对的密钥，仅后端持有
     */
    private String appSecret;

    /**
     * 是否启用 mock 模式：true 时登录走本地模拟，false 时调用真实微信接口
     * 默认 true，保证开箱即用
     */
    private boolean mock = true;
}
```

---

### 3.4 UserEntity.java 与 UserRepository.java：数据层

```java
package com.zznursing.miniapp.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户实体：与数据库 app_user 表一一对应
 * 小程序登录成功后，用 openid 查找或创建出一条该记录
 */
@Data  // Lombok：自动生成 getter/setter
@Entity  // JPA 实体注解，Hibernate 会为它管理生命周期
@Table(name = "app_user")  // 映射表名；取名 app_user 是为了避开部分数据库的保留字 user
public class UserEntity {

    /**
     * 主键：数据库自增
     */
    @Id  // 标记主键
    @GeneratedValue(strategy = GenerationType.IDENTITY)  // 主键自增策略
    private Long id;

    /**
     * 微信 openid：用户在当前小程序下的唯一标识，必须唯一、非空
     * 登录时以它为准"查有则用、查无则建"
     */
    @Column(name = "openid", nullable = false, unique = true)
    private String openid;

    /**
     * 用户昵称：登录后可从小程序端补充填写
     */
    @Column(name = "nickname")
    private String nickname;

    /**
     * 头像地址：微信头像或用户主动上传的图片 URL
     */
    @Column(name = "avatar")
    private String avatar;

    /**
     * 角色：family（家属）/ nurse（护工）/ admin（管理员）
     * 真实项目是 RBAC 权限模型的核心字段，这里先以字符串概括
     */
    @Column(name = "user_role")
    private String role;

    /**
     * 创建时间：记录用户首次登录的系统时间
     */
    @Column(name = "create_time")
    private LocalDateTime createTime;
}
```

```java
package com.zznursing.miniapp.repository;

import com.zznursing.miniapp.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 用户数据访问层
 * 继承 JpaRepository 后，Spring Data JPA 会自动生成常见的增删改查实现，
 * 自定义方法只需按"findBy + 字段名"约定声明即可。
 */
public interface UserRepository extends JpaRepository<UserEntity, Long> {

    /**
     * 按 openid 查询用户
     * 方法名 findByOpenid 会被 JPA 解析为：WHERE openid = ?
     * 返回 Optional 表示"可能查不到"，调用方必须处理空值
     *
     * @param openid 微信 openid
     * @return 用户实体（可能为空）
     */
    Optional<UserEntity> findByOpenid(String openid);
}
```

---

### 3.5 JwtUtil.java：签发与校验 JWT

```java
package com.zznursing.miniapp.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 工具类：负责生成 token 和校验 token
 * 对外暴露两个方法：
 *   generateToken —— 登录成功后签发
 *   parseToken   —— 每个受保护请求进来时验签并取出声明
 */
@Component  // 声明为 Spring Bean，AuthService 与 JwtAuthFilter 注入使用
public class JwtUtil {

    /**
     * 签名密钥：从配置 jwt.secret 读取，HS256 要求至少 32 字节
     */
    @Value("${jwt.secret}")
    private String secret;

    /**
     * token 有效期（小时），默认 72 小时
     */
    @Value("${jwt.expire-hours:72}")
    private long expireHours;

    /**
     * 根据配置的字符串密钥构造 HMAC 签名用的 SecretKey
     * Keys.hmacShaKeyFor 会根据字节长度自动选择 HS256/384/512
     *
     * @return 签名密钥对象
     */
    private SecretKey getKey() {
        // 把字符串密钥转成字节数组（UTF-8），再构造成 jjwt 的 SecretKey
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 生成 JWT 令牌：把用户核心信息写进 Payload 并签名
     *
     * @param userId 用户主键，放入标准声明 subject
     * @param openid 微信 openid，放入自定义声明
     * @param role   用户角色，放入自定义声明，用于后续权限判断
     * @return 签好名的 JWT 字符串
     */
    public String generateToken(Long userId, String openid, String role) {
        // 当前时间作为签发时间
        Date now = new Date();
        // 过期时间 = 当前时间 + 配置的小时数换算成毫秒
        Date expiration = new Date(now.getTime() + expireHours * 3600 * 1000);
        // 链式构建：主体(sub)=userId，附加 openid/role，签发时间，过期时间，用密钥签名
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("openid", openid)
                .claim("role", role)
                .issuedAt(now)
                .expiration(expiration)
                .signWith(getKey())
                .compact();
    }

    /**
     * 校验并解析 JWT：验签通过返回 Payload（Claims），失败抛出异常
     *
     * @param token 前端传过来的 JWT 字符串
     * @return JWT 的 Payload，可从中取 subject / openid / role / exp 等
     */
    public Claims parseToken(String token) {
        // parser 用同一个密钥验签，验签失败或过期会抛 JwtException
        return Jwts.parser()
                .verifyWith(getKey())   // 指定用于验签的密钥
                .build()                // 构建解析器
                .parseSignedClaims(token)  // 解析 token 得到签名后的 Claims
                .getPayload();          // 取出 Payload 部分
    }

    /**
     * 暴露过期小时数，供登录响应体返回"有效期"给前端
     *
     * @return 过期小时数
     */
    public long getExpireHours() {
        return expireHours;
    }
}
```

**jwt 关键点**：签名的本质是"防篡改"——任何第三方改了 Payload 里的任何一个字符，验签都会失败；但 JWT 内容不是加密的，所以 Payload 里不放敏感信息，且永远通过 HTTPS 传输。

---

### 3.6 WeChatAuthService 接口 + MockWeChatAuthService：模拟微信登录

先定义一个抽象接口。真实项目里再写一个 `RealWeChatAuthService` 实现它，业务代码一行不用改就能切换（依赖倒置）：

```java
package com.zznursing.miniapp.service;

import com.zznursing.miniapp.dto.WeChatSession;

/**
 * 微信授权服务抽象
 * 定义"用 code 换 openid/session_key"的统一行为，
 * 具体实现可选：
 *   MockWeChatAuthService —— 本地模拟（本文使用，无真实账号也能跑）
 *   RealWeChatAuthService —— 调用微信 jscode2session 接口（真实环境）
 * 依赖抽象而非实现，是面向接口编程的基本功。
 */
public interface WeChatAuthService {

    /**
     * 用登录 code 换取微信会话信息
     *
     * @param code 前端 wx.login() 拿到的一次性登录凭证
     * @return 微信会话信息（openid + sessionKey）
     */
    WeChatSession code2Session(String code);
}
```

```java
package com.zznursing.miniapp.service;

import com.zznursing.miniapp.config.WeChatProperties;
import com.zznursing.miniapp.dto.WeChatSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * 微信登录 Mock 实现
 * 当 wechat.mock=true 时被容器选中（唯一实现），完全在本地模拟微信服务器：
 *   - 随便给一个 code 都能"兑换"成功
 *   - openid 由 code 确定性派生，保证同一 code 登录返回同一用户（幂等）
 * 这样团队里没有申请到小程序 AppID 的同学也能完整联调和开发。
 */
@Slf4j  // Lombok：生成 log 字段，可打日志
@Service  // 声明为业务服务 Bean
@RequiredArgsConstructor  // 为 final 字段生成构造器注入
public class MockWeChatAuthService implements WeChatAuthService {

    // 注入微信配置，用于读取 mock 开关
    private final WeChatProperties weChatProperties;

    /**
     * 模拟"用 code 换 session"：真实微信会校验 code 后返回 openid/session_key
     *
     * @param code 登录 code（任意非空字符串均可，演示用）
     * @return 模拟的微信会话信息
     */
    @Override
    public WeChatSession code2Session(String code) {
        // 防御：非 mock 模式误用本实现时直接报错，避免静默绕过真实登录
        if (!weChatProperties.isMock()) {
            // 抛出运行时异常，提示配置错误
            throw new IllegalStateException("当前不是 mock 模式，请使用真实微信登录服务");
        }
        // 记录 mock 登录日志，方便排查
        log.info("【Mock】微信登录模拟 - code: {}", code);
        // 确定性生成 openid：同一 code 永远得到同一 openid，保证幂等
        String openid = "mock_openid_" + Integer.toUnsignedLong(code.hashCode());
        // 随机生成 session_key：真实场景用于解密手机号等敏感数据
        String sessionKey = "mock_session_key_" + UUID.randomUUID();
        // 模拟微信返回的会话信息
        return new WeChatSession(openid, sessionKey);
    }
}
```

配套的会话 DTO（record 是 Java 16+ 的简洁数据载体，等价于不可变对象 + equals/hashCode/toString 全自动）：

```java
package com.zznursing.miniapp.dto;

/**
 * 微信会话信息 DTO（不可变记录）
 * 对应真实微信 jscode2session 接口返回的 openid 与 session_key
 *
 * @param openid     用户在当前小程序下的唯一标识
 * @param sessionKey 会话密钥，用于解密手机号等加密数据，不是登录凭证
 */
public record WeChatSession(String openid, String sessionKey) {
}
```

---

### 3.7 登录 DTO：LoginRequest 与 LoginResponse

```java
package com.zznursing.miniapp.dto;

/**
 * 登录请求 DTO：小程序前端登录时提交的唯一参数就是 code
 * 真实项目中还可扩展 nickName、avatarUrl 等可选字段
 *
 * @param code 微信 wx.login() 返回的一次性登录凭证
 */
public record LoginRequest(String code) {
}
```

```java
package com.zznursing.miniapp.dto;

/**
 * 登录响应 DTO：登录成功后返回给前端的关键信息
 * token 是主角，前端存入 Storage，后续所有请求携带
 *
 * @param token           签发好的 JWT 令牌
 * @param expiresSeconds  token 有效期（秒），前端可据此做"快过期提前刷新"
 * @param userId          系统内用户主键
 * @param openid          微信 openid（教学演示保留，生产建议脱敏）
 * @param nickname        用户昵称
 * @param role            用户角色：family / nurse / admin
 */
public record LoginResponse(
        String token,
        long expiresSeconds,
        Long userId,
        String openid,
        String nickname,
        String role) {
}
```

---

### 3.8 AuthService.java：登录核心业务

```java
package com.zznursing.miniapp.service;

import com.zznursing.miniapp.dto.LoginResponse;
import com.zznursing.miniapp.dto.WeChatSession;
import com.zznursing.miniapp.entity.UserEntity;
import com.zznursing.miniapp.repository.UserRepository;
import com.zznursing.miniapp.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 认证服务：承载登录链路的核心业务逻辑
 * code → openid → 查库建库 → 签 JWT → 返回前端
 * 这是整条主链路"业务编排"的位置，接口层只做参数透传。
 */
@Slf4j  // 提供日志能力
@Service  // 声明为业务服务 Bean
@RequiredArgsConstructor  // final 字段构造器注入
public class AuthService {

    // 微信授权服务：依赖抽象接口，真实/模拟实现可无缝切换
    private final WeChatAuthService weChatAuthService;
    // 用户数据访问层
    private final UserRepository userRepository;
    // JWT 工具
    private final JwtUtil jwtUtil;

    /**
     * 登录入口：
     * 1. 校验 code 非空
     * 2. 用 code 向微信（或 mock）换 openid
     * 3. 按 openid 查用户，查不到则新建（首次登录）
     * 4. 用用户信息签发 JWT
     *
     * @param code 微信一次性登录凭证
     * @return 登录响应（含 token）
     */
    @Transactional  // 涉及建用户写库，加事务保证一致性
    public LoginResponse login(String code) {
        // 第一步：参数校验，code 为空直接拒绝（对应少量代码的防御式开发）
        if (code == null || code.isBlank()) {
            // 抛出业务异常，由 Controller 统一转成 400 响应
            throw new IllegalArgumentException("登录 code 不能为空");
        }

        // 第二步：用 code 换 openid（真实项目此处会 HTTP 调用微信服务器）
        WeChatSession session = weChatAuthService.code2Session(code);
        // 取出用户的微信唯一标识
        String openid = session.openid();
        // 打印关键日志，方便排查问题
        log.info("微信登录成功 - openid: {}", openid);

        // 第三步：查找用户；Optional.orElseGet 表示"查不到就执行建用户逻辑"
        UserEntity user = userRepository.findByOpenid(openid)
                .orElseGet(() -> createNewUser(openid));

        // 第四步：签发 JWT，把用户主键、openid、角色写进令牌
        String token = jwtUtil.generateToken(user.getId(), user.getOpenid(), user.getRole());
        // 计算 token 有效期（秒），随响应返回给前端
        long expiresSeconds = jwtUtil.getExpireHours() * 3600;

        // 第五步：组装登录响应返回
        return new LoginResponse(
                token,                 // JWT 令牌
                expiresSeconds,        // 有效期（秒）
                user.getId(),          // 用户主键
                user.getOpenid(),      // 微信 openid
                user.getNickname(),    // 昵称
                user.getRole());       // 角色
    }

    /**
     * 首次登录的用户建档：默认角色为家属（family），本示例不做昵称等补充资料
     *
     * @param openid 微信 openid
     * @return 已落库的用户实体
     */
    private UserEntity createNewUser(String openid) {
        // 创建新实体对象
        UserEntity user = new UserEntity();
        // 写入 openid，这是用户的唯一标识
        user.setOpenid(openid);
        // 默认昵称，真实项目允许用户后续在小程序端修改
        user.setNickname("微信用户");
        // 默认角色：家属；真实项目在绑定老人、分配权限时再细化
        user.setRole("family");
        // 记录建档时间
        user.setCreateTime(LocalDateTime.now());
        // 保存到数据库并返回带主键的实体
        return userRepository.save(user);
    }

    /**
     * 按主键查询用户：供"获取当前用户信息"接口使用
     *
     * @param userId 用户主键（取自 JWT 的 subject）
     * @return 用户实体
     */
    public UserEntity getUserById(Long userId) {
        // 查询用户，查不到则抛出业务异常
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
    }
}
```

**这段代码值得反复读**：`findByOpenid(...).orElseGet(() -> createNewUser(openid))` 一行完成了"有则用、无则建"的幂等策略——家属第二次打开小程序，同样登录、拿到同一个用户，不会产生重复账号。这就是微信场景下用户体系的标配写法。

---

### 3.9 AuthController.java：登录与用户接口

```java
package com.zznursing.miniapp.controller;

import com.zznursing.miniapp.dto.LoginRequest;
import com.zznursing.miniapp.dto.LoginResponse;
import com.zznursing.miniapp.entity.UserEntity;
import com.zznursing.miniapp.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 认证接口：
 *   POST /api/auth/login —— 登录（公开接口，无需 token）
 *   GET  /api/auth/user   —— 获取当前登录用户信息（需 JWT）
 */
@Slf4j  // 提供日志
@RestController  // 声明为 REST 控制器
@RequestMapping("/api/auth")  // 接口统一前缀
@RequiredArgsConstructor  // 构造器注入
public class AuthController {

    // 认证业务服务
    private final AuthService authService;

    /**
     * 登录：前端携带 wx.login() 得到的 code 调用本接口换取 token
     *
     * @param request 登录请求体，里面只有 code 字段
     * @return 200 + token 信息；code 为空时返回 400
     */
    @PostMapping("/login")  // 映射到 POST /api/auth/login
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        try {
            // 调用业务层完成登录编排
            LoginResponse response = authService.login(request.code());
            // 成功：返回 200 和登录响应体
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            // 参数类异常：返回 400 和错误信息
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * 获取当前登录用户信息
     * JWT 过滤器已把用户标识写入 SecurityContext，
     * 这里直接从认证对象里取出当前用户主键再查库
     *
     * @return 当前用户实体信息
     */
    @GetMapping("/user")  // 映射到 GET /api/auth/user
    public ResponseEntity<?> getUser() {
        // 从安全上下文取出认证对象中的主体（即用户 ID）
        String userId = (String) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
        // 按主键查询完整用户信息
        UserEntity user = authService.getUserById(Long.valueOf(userId));
        // 返回用户信息
        return ResponseEntity.ok(user);
    }
}
```

---

### 3.10 JwtAuthFilter.java：JWT 鉴权过滤器

`OncePerRequestFilter` 保证每个请求只过滤一次。它的职责是：**认出"带有效 token"的请求，并把用户身份放进 Spring Security 的上下文**；没带或验签失败的请求不设身份，随后会被 `SecurityConfig` 统一拦截成 401。

```java
package com.zznursing.miniapp.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * JWT 鉴权过滤器
 * 处理流程：
 *   1. 取 Authorization 头，形如 "Bearer <jwt>"
 *   2. 验签解析 token，取出 subject（用户ID）与 role
 *   3. 校验通过 → 构造认证对象放入 SecurityContext
 *   4. 校验失败 → 不放入身份，后续由安全配置统一返回 401
 * OncePerRequestFilter 保证过滤器在一次请求中只执行一次。
 */
@Component  // 注册为 Spring Bean，由 SecurityConfig 挂进过滤器链
@RequiredArgsConstructor  // 构造器注入 JwtUtil
public class JwtAuthFilter extends OncePerRequestFilter {

    // JWT 解析工具
    private final JwtUtil jwtUtil;

    /**
     * 核心过滤逻辑
     *
     * @param request      HTTP 请求
     * @param response     HTTP 响应
     * @param filterChain  过滤器链，用于放行到下一个过滤器/控制器
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // 取出请求头中的 Authorization 字段
        String authHeader = request.getHeader("Authorization");

        // 没有 Authorization 头，或不是 Bearer 开头：直接放行，交给安全配置决定是否拦截
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            // 放行到过滤器链的下一个节点
            filterChain.doFilter(request, response);
            return;
        }

        // 截取 "Bearer " 之后的纯 token 部分（"Bearer " 正好 7 个字符）
        String token = authHeader.substring(7);
        try {
            // 验签解析 token，得到 Payload 声明
            Claims claims = jwtUtil.parseToken(token);
            // 从 subject 取用户主键（字符串形式）
            String userId = claims.getSubject();
            // 从自定义声明取角色
            String role = claims.get("role", String.class);
            // 把角色包装成 Spring Security 的权限对象：ROLE_家人 / ROLE_护工 等
            List<SimpleGrantedAuthority> authorities =
                    List.of(new SimpleGrantedAuthority("ROLE_" + role));
            // 构造认证对象：principal=用户ID，credentials=null（无密码），authorities=角色权限
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(userId, null, authorities);
            // 写入安全上下文，后续 Controller 用 SecurityContextHolder 即可取到当前用户
            SecurityContextHolder.getContext().setAuthentication(authentication);
            // 记录调试日志，方便追踪哪个用户访问了哪个接口
            logger.debug("JWT 认证通过 - userId: {}", userId);
        } catch (JwtException | IllegalArgumentException e) {
            // token 无效、被篡改或过期：清空上下文，不设置任何身份
            SecurityContextHolder.clearContext();
        }
        // 无论认证成功与否都放行，是否拦截由 SecurityConfig 的规则裁决
        filterChain.doFilter(request, response);
    }
}
```

---

### 3.11 SecurityConfig.java：Spring Security 配置

```java
package com.zznursing.miniapp.config;

import com.zznursing.miniapp.security.JwtAuthFilter;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security 配置
 * 核心决策：
 *   1. 无状态会话（STATELESS）：不存服务端 session，天然适合 JWT + 小程序
 *   2. 登录接口与 H2 控制台放行，其余接口一律要求认证
 *   3. 把 JwtAuthFilter 挂到用户名密码过滤器之前
 *   4. 未认证访问受保护资源时返回 401 JSON
 */
@Configuration  // 配置类
@EnableWebSecurity  // 开启 Spring Security 并装配其默认链
@RequiredArgsConstructor  // 构造器注入 JwtAuthFilter
public class SecurityConfig {

    // JWT 鉴权过滤器，注入到安全过滤器链中
    private final JwtAuthFilter jwtAuthFilter;

    /**
     * 构建安全过滤器链（Spring Security 6 的推荐写法：HttpSecurity 构建）
     *
     * @param http HttpSecurity 构建器
     * @return 装配好的安全过滤器链
     * @throws Exception 配置可能抛出的构建异常
     */
    @Bean  // 声明为 Bean，供 Spring Security 自动装配
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // 关闭 CSRF：小程序使用 token 鉴权且无 Cookie 会话，CSRF 攻击面小
            .csrf(AbstractHttpConfigurer::disable)
            // 会话策略设为无状态：每次请求都是独立的，不创建 HttpSession
            .sessionManagement(session ->
                    session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // 接口访问规则
            .authorizeHttpRequests(auth -> auth
                    // 登录接口放行：未登录用户必须能调用它
                    .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                    // H2 网页控制台放行（仅演示环境需要）
                    .requestMatchers("/h2-console/**").permitAll()
                    // 其余任何请求都必须已认证（携带有效 JWT）
                    .anyRequest().authenticated())
            // 未认证/认证失败时的统一出口
            .exceptionHandling(ex -> ex.authenticationEntryPoint((request, response, authException) -> {
                // 设置 HTTP 状态码 401
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                // 响应内容类型为 JSON
                response.setContentType("application/json;charset=UTF-8");
                // 写出错误信息
                response.getWriter().write("{\"message\":\"未登录或登录已过期\"}");
            }))
            // 把 JWT 过滤器加到"用户名密码认证过滤器"之前，先于默认认证执行
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
            // 允许同源 iframe：H2 控制台用 iframe 渲染，X-Frame-Options 需放行同源
            .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()));
        // 返回构建完成的安全过滤器链
        return http.build();
    }
}
```

至此，**登录 → 签 token → 带 token 访问受保护接口 → 401 兜底**的闭环已经完整。

---

### 3.12 HealthDataController.java：健康数据接口

这个 Controller 给家属端提供健康数据查询。真实项目里数据来自 IoT 设备链路（华为 IoTDA → MQTT → MySQL/Redis），这里用内存模拟数据，重点展示"受保护接口如何拿到当前用户"。

```java
package com.zznursing.miniapp.controller;

import com.zznursing.miniapp.entity.UserEntity;
import com.zznursing.miniapp.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

/**
 * 健康数据接口（本示例为模拟数据）
 * 仅演示"带上 JWT 才能访问受保护接口"的效果；
 * 真实项目中数据来自 IoTDA 采集链路，且按"家属 ↔ 老人"绑定关系过滤。
 */
@RestController  // 声明为 REST 控制器
@RequestMapping("/api/health-data")  // 接口前缀
@RequiredArgsConstructor  // 构造器注入
public class HealthDataController {

    // 注入认证服务，用于根据当前用户查库（演示用）
    private final AuthService authService;

    /**
     * 健康数据记录（不可变 record）
     * 真实项目中对应 health_data 表的行记录
     *
     * @param elderlyId    老人档案 ID
     * @param elderlyName  老人姓名
     * @param metric       指标：heart_rate / blood_pressure / blood_oxygen / temperature / step
     * @param value        指标数值（字符串便于展示不同格式）
     * @param unit         单位：次/分、mmHg、%、℃、步
     * @param time         采集时间
     */
    public record HealthData(Long elderlyId, String elderlyName,
                             String metric, String value, String unit, LocalDateTime time) {
    }

    /**
     * 模拟数据：一位老人的连续 5 条健康数据
     * 时间依次递减，模拟设备每 1~2 分钟上报一次
     */
    private static final List<HealthData> MOCK_LIST = List.of(
            new HealthData(1001L, "王爷爷", "heart_rate",      "72",   "次/分", LocalDateTime.now().minusMinutes(5)),
            new HealthData(1001L, "王爷爷", "blood_pressure",  "118/76", "mmHg", LocalDateTime.now().minusMinutes(4)),
            new HealthData(1001L, "王爷爷", "blood_oxygen",    "97",   "%",    LocalDateTime.now().minusMinutes(3)),
            new HealthData(1001L, "王爷爷", "temperature",     "36.5", "℃",    LocalDateTime.now().minusMinutes(2)),
            new HealthData(1001L, "王爷爷", "step",            "3284", "步",    LocalDateTime.now().minusMinutes(1))
    );

    /**
     * 查询健康数据列表：GET /api/health-data
     * 必须先登录（带 JWT），否则 SecurityConfig 会返回 401
     *
     * @return 当前家属可见的健康数据列表
     */
    @GetMapping  // 映射到 GET /api/health-data
    public ResponseEntity<?> list() {
        // 从安全上下文取出当前登录用户的 ID（过滤器写入的主体）
        String userId = (String) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
        // 按 ID 查用户，顺带校验该用户确实存在
        UserEntity user = authService.getUserById(Long.valueOf(userId));
        // 真实项目中：按 user 与老人档案的绑定关系查询并返回
        return ResponseEntity.ok(MOCK_LIST);
    }

    /**
     * 查询最新一条健康数据：GET /api/health-data/recent
     * 供首页仪表盘展示"刚刚测的心率是多少"
     *
     * @return 最新一条健康数据
     */
    @GetMapping("/recent")  // 映射到 GET /api/health-data/recent
    public ResponseEntity<?> recent() {
        // 用 Comparator 按时间取最大（最新）的一条
        HealthData latest = MOCK_LIST.stream()
                .max(Comparator.comparing(HealthData::time))  // 方法引用：按 time 字段比较
                .orElse(null);  // 列表为空时返回 null
        // 返回最新记录
        return ResponseEntity.ok(latest);
    }
}
```

---

### 3.13 MiniappApplication.java：启动类

```java
package com.zznursing.miniapp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * 应用启动类
 * @SpringBootApplication 组合了三个注解：
 *   @SpringBootConfiguration —— 标记配置类
 *   @EnableAutoConfiguration  —— 按 classpath 自动装配
 *   @ComponentScan           —— 扫描启动类所在包及其子包的 Bean
 * @ConfigurationPropertiesScan 开启 @ConfigurationProperties 扫描，
 * 使 WeChatProperties 等属性绑定类能被自动发现。
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class MiniappApplication {

    /**
     * 程序入口
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        // 启动 Spring Boot 应用
        SpringApplication.run(MiniappApplication.class, args);
    }
}
```

---

### 3.14 MiniappApplicationTests.java：测试

两个测试：一个验证容器能正常启动，一个走完整登录链路验证"code 换 token"以及"同一 code 幂等"。

```java
package com.zznursing.miniapp;

import com.zznursing.miniapp.dto.LoginResponse;
import com.zznursing.miniapp.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 应用测试类
 * @SpringBootTest 启动完整 Spring 容器（使用 H2 内存库），
 * @AutoConfigureMockMvc 注入 MockMvc，可对接口做全链路测试。
 */
@SpringBootTest
@AutoConfigureMockMvc
class MiniappApplicationTests {

    // MockMvc：模拟 HTTP 请求调用接口
    @Autowired
    private MockMvc mockMvc;

    // 认证服务：用于直接调用业务层验证幂等性
    @Autowired
    private AuthService authService;

    /**
     * 测试 1：容器能正常启动
     * 只要 Spring 上下文能加载所有 Bean 并连接 H2，本用例即通过
     */
    @Test
    void contextLoads() {
        // 空方法体：容器加载本身就是断言
    }

    /**
     * 测试 2：登录接口全链路
     * 模拟小程序 POST /api/auth/login，携带一次性 code，期望返回 token
     */
    @Test
    void loginWithMockCodeReturnsToken() throws Exception {
        // 发起 POST 请求，Content-Type 为 JSON，body 携带 code
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)  // 声明请求体类型
                        .content("{\"code\":\"mock-code-001\"}")) // 请求体 JSON
                // 期望 HTTP 200
                .andExpect(status().isOk())
                // 期望响应 JSON 中存在 token 字段（说明 JWT 已成功签发）
                .andExpect(jsonPath("$.token").exists());
    }

    /**
     * 测试 3：业务层幂等性
     * 同一 code（同一 openid）登录两次，应返回同一个用户（有则用，无则建）
     */
    @Test
    void sameCodeLoginReturnsSameUser() {
        // 第一次登录（会新建用户）
        LoginResponse first = authService.login("mock-code-002");
        // 断言返回的 token 非空
        assertNotNull(first.token());
        // 第二次登录（应命中已存在用户）
        LoginResponse second = authService.login("mock-code-002");
        // 断言两次登录返回同一个用户主键
        assertEquals(first.userId(), second.userId());
    }
}
```

---

## 四、运行验证

### 4.1 启动

```bash
# 在项目根目录（含 pom.xml）执行
mvn spring-boot:run
```

看到类似输出即启动成功：

```
Started MiniappApplication in 2.4 seconds (JVM running for 2.8)
Tomcat started on port 8080 (http) with context path ''
```

也可以先跑测试验证链路：

```bash
mvn test
```

### 4.2 验证登录接口（POST /api/auth/login）

打开另一个终端，模拟小程序前端发登录请求（code 可以是任意非空字符串）：

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d "{\"code\":\"mock-code-001\"}"
```

返回示例：

```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIiwib3BlbmlkIjoibW9ja19vcGVuaWRfMTYxMDY5MDk2OCIsInJvbGUiOiJmYW1pbHkiLCJpYXQiOjE3ODk0NDA4MDAsImV4cCI6MTc5NDI1NDgwMH0.uB6QaHVWkP2VnO_pRFv9cEnLt0IwOCp9qzRj1X27z1A",
  "expiresSeconds": 259200,
  "userId": 1,
  "openid": "mock_openid_xxx",
  "nickname": "微信用户",
  "role": "family"
}
```

可以把 token 存进环境变量，方便下一步用：

```bash
export TOKEN="粘贴上面返回的token"
```

### 4.3 验证受保护接口（GET /api/health-data）

带 token 访问：

```bash
curl http://localhost:8080/api/health-data -H "Authorization: Bearer $TOKEN"
```

返回模拟的健康数据列表：

```json
[
  {"elderlyId":1001,"elderlyName":"王爷爷","metric":"heart_rate","value":"72","unit":"次/分","time":"..."},
  ...
]
```

不带 token 访问（故意去掉 Authorization 头）：

```bash
curl http://localhost:8080/api/health-data
```

预期返回 401：

```json
{"message":"未登录或登录已过期"}
```

### 4.4 查看数据库（可选）

浏览器打开 `http://localhost:8080/h2-console`：
- JDBC URL 填 `jdbc:h2:mem:miniapp`
- 用户名 `sa`，密码留空

登录后能直接看到 `app_user` 表——用刚才的 code 登录过的用户已经在里面，表名与字段和我们定义的实体完全对应。

---

## 五、项目对照：真实 zznursing 小程序后端还差什么

本文示例是为了学习刻意做小的。对照 zznursing 生产环境的微信小程序后端，同样的登录与数据链路，真实项目有六个显著升级：

| 维度 | 本文入门示例 | zznursing 真实项目 |
|------|--------------|--------------------|
| 微信登录 | Mock 模拟 | 调用 `jscode2session` 真实接口换取 session_key 与 openid；支持手机号解密绑定 |
| 用户/权限 | 单角色字符串 | RBAC 权限模型：家属（family）/ 护工（nurse）/ 管理员（admin）多角色，绑定老人档案关系，接口级 + 数据级双重权限 |
| 健康数据来源 | 内存模拟列表 | 华为 IoTDA 采集 → RocketMQ 削峰 → MySQL/Redis 分层存储，接口按家属绑定关系过滤老人数据，支持趋势聚合 |
| 凭证存储 | JWT（无状态） | 真实项目也可换用 Redis 存储自定义 token，支持服务端主动踢人、登出吊销 |
| 告警触达 | 无 | 微信订阅消息（模板消息）推送心率异常、跌倒检测、设备离线告警，走 `cgi-bin/message/subscribe/send` |
| AI 问答 | 无 | 集成百度千帆大模型，SSE 流式输出，AI 结合老人实时健康数据给出个性化建议 |
| 网关与部署 | 单机直连 | 微服务化，前端经 Spring Cloud Gateway 路由鉴权，服务注册到 Nacos |

**关于凭证选型的一句话总结**：真实团队常纠结 JWT 还是 Redis token。JWT 无状态、易水平扩展，适合"纯展示 + 长有效期的家属查询"；Redis token 可主动失效，适合"需要服务端踢人、严格审计"的场景。zznursing 这类包含告警订阅、老人绑定关系频繁变化的平台，两者皆可，关键是**登录链路的前半段（code 换 openid）在任何方案下都是一样的**——这也是本文把重点放在这段的原因。

**关于 mock 的工程价值**：`MockWeChatAuthService` 不只是一个教学技巧。真实项目里，这个模式的正式名字叫"测试替身 / 桩实现"——它让前端开发、接口联调、CI 自动化测试都可以不依赖外部微信服务独立进行。等 AppID 申请下来，新增一个 `RealWeChatAuthService` 实现同一个接口，切换一行配置即可。

---

## 六、面试题 3 道

### 问题 1：请完整描述微信小程序的登录流程，说明为什么 code 是一次性的

**考察点**：登录链路是否清晰，是否理解 code、openid、session_key 三者各自的职责。

**参考答案**：
1. 小程序前端调用 `wx.login()`，微信返回一个临时登录凭证 code（有效期约 5 分钟）；
2. 前端把 code 通过 HTTPS 发给自己的后端；
3. 后端用 `appid + appsecret + code` 请求微信接口 `https://api.weixin.qq.com/sns/jscode2session`，换取 `openid`（用户唯一标识）和 `session_key`（会话密钥）；
4. 后端用 openid 查库，不存在则建档；
5. 后端签发自己的登录凭证（JWT 或 Redis token）返回给前端，前端存入 Storage，后续请求携带在 `Authorization` 头中。

code 设计成一次性且 5 分钟有效，目的是**防重放攻击**：即使 code 在网络传输中被截获，攻击者也无法拿它再次兑换身份。因此换取 openid 的请求绝不能由前端发起——前端不持有 appsecret，且 code 一旦被前端使用就必须失效。

### 问题 2：JWT 和基于 Redis 的自定义 token 如何选型？

**考察点**：对两种主流凭证方案的原理和取舍有理解，而不是只背概念。

**参考答案**：
- **JWT**：自包含、无状态（服务端不用存），验签即可信任内容，天然支持水平扩展（任意实例都能验签）。缺点是签发后无法在到期前主动失效（改密码、踢人、注销都做不到），Payload 大小随内容增长，且内容不加密、只能放非敏感信息。
- **Redis token**：服务端存一份"token → 用户"映射，可以随时删除实现主动失效，适合严格审计、黑名单、踢人等场景；缺点是服务端有状态，需要保证 Redis 高可用，每次请求都多一次 Redis 查询（虽然毫秒级）。
- **选型建议**：纯查询展示类接口（如家属看健康数据）用 JWT 更轻；涉及订阅关系变更、需要登出立即生效的平台（如 zznursing 含告警订阅、多角色权限）用 Redis token 更可控。两者共用的登录前半段（code 换 openid）完全一致。

### 问题 3：小程序接口的数据安全要注意哪些点？

**考察点**：安全意识与微信生态的实践经验。

**参考答案**：
1. **appSecret 绝不进前端**：换取 openid 的请求必须由后端发起，appSecret 只能存在后端配置（或密钥管理服务）里；
2. **全程 HTTPS**：微信要求小程序 request 的合法域名必须备案且支持 HTTPS，防止 token、健康数据在明文网络中泄露；
3. **code 一次一用**：防重放；
4. **不返回敏感字段**：openid、session_key、老人健康明细属于敏感信息，接口只返回业务所需字段，必要时做脱敏；
5. **凭证短期化**：JWT 设置合理过期时间（本项目 72 小时），过期后携带旧 token 访问应返回 401 引导重新登录；
6. **数据级权限**：只让家属访问"与 TA 绑定的老人"的数据，接口层不仅要校验登录态，还要校验数据归属（防越权访问他人老人数据）；
7. **注意区分 access_token 与 session_key**：推送告警用的 `access_token` 是小程序级接口调用凭证（有次数限制、需缓存），与用户登录的 `session_key` 是完全不同的两样东西，不能混用。

---

## 本篇小结

本文用 14 个文件搭出了一个最小但完整的微信小程序后端：`pom.xml + application.yml` 给出可运行的骨架，`UserEntity/UserRepository` 完成数据层，`JwtUtil/JwtAuthFilter/SecurityConfig` 打通无状态鉴权，`MockWeChatAuthService/AuthService/AuthController` 实现"code 换身份"的登录主链路，最后用 MockMvc 测试验证了整条链路。**不需要任何真实微信账号，`mvn spring-boot:run` 即可复现全部行为**。

下一篇预告：真实项目的告警推送如何对接微信订阅消息，以及家属绑定老人后，数据接口的权限控制如何落地。