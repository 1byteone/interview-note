# 07 Stripe 支付集成入门：Checkout Session + Webhook + VIP 会员体系

> 本文是 ai-passage-creator 项目技术栈深度剖析系列的第 7 篇（入门篇）。面向 Java 初学者，手把手带你从零搭建基于 Stripe 的支付系统，理解 Checkout Session 托管结账、Webhook 异步通知和 VIP 会员体系。
>
> **对应项目：** `ai-passage-creator/ai-passage-creator-server` 支付与会员模块
> **难度等级：** Level 1 入门
> **预计阅读时间：** 25 分钟（含代码实操）

---

## 一、项目背景

### 1.1 什么是 Stripe 支付

Stripe 是国际领先的支付服务商（Payment Service Provider, PSP），提供一套完整的支付 API。它的核心价值是**让开发者无需自建支付表单，即可快速集成信用卡、Apple Pay、Google Pay 等多种支付方式**。

| 核心概念 | 说明 | 类比 |
|---------|------|------|
| **StripeClient** | Stripe Java SDK 31.x 的 API 入口，所有操作通过 `client.v1().xxx()` 调用 | 类似于 Spring 的 RestTemplate |
| **Checkout Session** | Stripe 托管支付页面，无需自建支付表单 | 类似支付宝的"收银台页面" |
| **PaymentIntent** | 支付意图，代表一次支付的核心对象 | 类似订单的支付记录 |
| **Product** | 商品定义，描述要售卖的内容 | 类似电商系统的商品 SKU |
| **Price** | 定价方案，定义商品的价格和计费周期 | 类似商品的价格标签 |
| **Customer** | 客户信息，关联用户和支付记录 | 类似用户表的扩展信息 |
| **Webhook** | 异步事件通知，Stripe 将支付结果推送到开发者服务器 | 类似支付宝的回调通知 |

**Checkout Session 工作流程：**

```
用户点击"开通 VIP"
    ↓
后端创建 Checkout Session（指定商品、价格、成功/取消 URL）
    ↓
返回 Session URL 给前端
    ↓
前端跳转到 Stripe 托管结账页面
    ↓
用户在 Stripe 页面完成支付（卡号、CVV 等敏感信息直接在 Stripe 页面填写）
    ↓
Stripe 异步发送 Webhook 到后端服务器
    ↓
后端验证 Webhook 签名 → 处理支付结果 → 更新用户 VIP 状态
```

### 1.2 为什么需要 Stripe 支付

在项目中，用户需要付费才能使用 AI 生图、SVG 图解等高级功能。自建支付系统有很多痛点：

| 痛点 | 说明 |
|------|------|
| **PCI-DSS 合规** | 自建支付系统需要处理信用卡信息，必须通过 PCI-DSS 合规认证，成本高昂 |
| **支付表单开发** | 自建支付表单需要处理卡号、CVV、有效期、3D 验证等复杂逻辑 |
| **多支付方式** | 信用卡、Apple Pay、Google Pay 等每种支付方式都需要单独集成 |
| **订阅管理** | 周期扣费、续费失败处理、订阅取消等逻辑复杂 |
| **安全风险** | 支付信息泄露风险高，责任重大 |

**Stripe 的解决方案：**

| 痛点 | Stripe 的解决方案 |
|------|------------------|
| **PCI-DSS 合规** | Checkout Session 托管页面，支付信息在 Stripe 页面处理，服务器零接触敏感信息 |
| **支付表单** | 一行代码跳转 Stripe 托管结账页面，无需自建表单 |
| **多支付方式** | 自动支持信用卡、Apple Pay、Google Pay 等 |
| **订阅管理** | 原生支持 Subscription 模式，自动处理周期扣费 |
| **安全** | Webhook 签名验证、HTTPS 加密、Tokenization 令牌化 |

### 1.3 本文的目标

读完本文，你将能够：
- 理解 Stripe 支付的核心概念：Checkout Session、Webhook、PaymentIntent
- 搭建一个完整的 Stripe 支付 Demo
- 使用 StripeClient 创建 Checkout Session
- 处理 Webhook 异步通知，验证签名
- 实现 VIP 会员激活和配额管理
- 编写单元测试验证支付流程
- 编写 3 道面试题的标准答案

---

## 二、核心概念

### 2.1 Checkout Session 托管结账

Checkout Session 是 Stripe 提供的**托管结账页面**。开发者只需调用 Stripe API 创建一个 Session，Stripe 就会生成一个完整的支付页面 URL，前端跳转到该页面即可完成支付。

**Checkout Session 的优势：**

| 优势 | 说明 |
|------|------|
| **零 PCI 合规负担** | 卡号、CVV 等敏感信息直接在 Stripe 页面填写，后端服务器从未接触 |
| **开箱即用** | 自带支付表单、3D 验证、错误处理，无需自建 UI |
| **多支付方式** | 自动展示信用卡、Apple Pay、Google Pay 等支付选项 |
| **多语言** | 自动根据用户浏览器语言显示对应语言 |
| **响应式** | 适配移动端和桌面端 |

**Checkout Session 创建参数：**

| 参数 | 说明 | 必填 |
|------|------|------|
| `mode` | 支付模式：`payment`（一次性）、`subscription`（订阅） | 是 |
| `success_url` | 支付成功后的跳转 URL（可包含 `{CHECKOUT_SESSION_ID}` 占位符） | 是 |
| `cancel_url` | 取消支付后的跳转 URL | 是 |
| `line_items` | 商品行：指定 Price ID 和数量 | 是 |
| `client_reference_id` | 自定义标识（通常为用户 ID，Webhook 回调时可用） | 推荐 |
| `metadata` | 自定义元数据（键值对，Webhook 回调时可用） | 可选 |
| `customer` | 关联已有的 Customer 对象 | 可选 |
| `customer_email` | 客户邮箱（自动创建 Customer） | 可选 |
| `payment_method_types` | 支持的支付方式类型 | 可选 |

### 2.2 Webhook 异步通知

Webhook 是 Stripe 的**异步事件通知机制**——当支付成功、订阅取消、退款等事件发生时，Stripe 会向开发者预先注册的 URL 发送 HTTP POST 请求，包含事件类型和事件数据。

**Webhook 的核心设计要点：**

| 要点 | 说明 |
|------|------|
| **异步性** | 支付完成和 Webhook 到达之间有时间差（通常几秒到几十秒） |
| **签名验证** | 每个 Webhook 请求都包含 `Stripe-Signature` 头，用于验证来源真实性 |
| **幂等性** | 同一事件可能被多次推送，需要保证重复处理不产生副作用 |
| **重试机制** | 如果 Webhook 返回非 2xx 状态码，Stripe 会按指数退避策略重试（最多 3 天） |
| **事件类型** | 根据 `event.type` 区分不同事件（`checkout.session.completed` 等） |

**常见 Webhook 事件类型：**

| 事件类型 | 说明 | 处理逻辑 |
|----------|------|----------|
| `checkout.session.completed` | 结账会话完成（支付成功） | 激活 VIP |
| `checkout.session.expired` | 结账会话过期（未支付） | 更新记录状态 |
| `customer.subscription.deleted` | 订阅取消 | 降级 VIP |
| `customer.subscription.updated` | 订阅更新 | 同步订阅状态 |
| `invoice.paid` | 发票支付成功（续费成功） | 延长 VIP 有效期 |
| `invoice.payment_failed` | 发票支付失败（续费失败） | 记录失败，发送通知 |

### 2.3 VIP 会员体系

项目的 VIP 会员体系基于 Stripe 支付构建，核心数据模型：

```
用户（User）
  ├── isVip: boolean            ← 是否 VIP
  ├── vipExpireTime: datetime   ← VIP 过期时间
  ├── dailyQuota: int           ← 每日配额（生成文章次数上限）
  └── usedQuota: int            ← 已用配额

支付记录（PaymentRecord）
  ├── stripeSessionId           ← Stripe 会话 ID（唯一标识，用于幂等）
  ├── stripePaymentIntentId     ← 支付意图 ID
  ├── amount                    ← 支付金额
  └── status                    ← 支付状态（pending/success/failed/expired）
```

**VIP 权益设计原则：**

| 原则 | 说明 |
|------|------|
| **差异化对待** | VIP 用户享有更多配图方式（AI 生图、SVG 图解），普通用户只能使用免费配图方式 |
| **配额管理** | 每日配额按用户维度管理，VIP 用户配额更高 |
| **过期自动降级** | VIP 过期后，用户自动降级为普通用户，但已生成的文章内容不受影响 |
| **原子扣减** | 配额扣减使用条件更新（`WHERE used_quota < daily_quota`），防止超用 |

---

## 三、从零搭建代码

### 3.1 创建项目结构

```
stripe-payment-demo/
├── pom.xml
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/
│   │   │       └── passage/
│   │   │           └── stripe/
│   │   │               ├── StripePaymentDemoApplication.java       # 启动类
│   │   │               ├── config/
│   │   │               │   └── StripeConfig.java                  # Stripe 配置类
│   │   │               ├── entity/
│   │   │               │   ├── User.java                          # 用户实体
│   │   │               │   └── PaymentRecord.java                 # 支付记录实体
│   │   │               ├── mapper/
│   │   │               │   ├── UserMapper.java                    # 用户 Mapper
│   │   │               │   └── PaymentRecordMapper.java           # 支付记录 Mapper
│   │   │               ├── service/
│   │   │               │   ├── PaymentService.java                # 支付服务（创建 Session）
│   │   │               │   └── PaymentProcessingService.java      # 支付处理服务（VIP 激活）
│   │   │               └── controller/
│   │   │                   ├── PaymentController.java             # 支付 API
│   │   │                   └── StripeWebhookController.java       # Webhook 回调
│   │   └── resources/
│   │       └── application.yml                                    # 配置文件
│   └── test/
│       └── java/
│           └── com/
│               └── passage/
│                   └── stripe/
│                       └── StripePaymentDemoApplicationTests.java  # 测试类
```

### 3.2 配置 Maven 依赖（pom.xml）

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!-- pom.xml —— Maven 项目配置文件 -->
<!-- Stripe 支付 + MyBatis-Flex 示例的 Maven 构建配置 -->
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <!-- 父项目：Spring Boot 3.2.x -->
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.2.5</version>
        <relativePath/>
    </parent>

    <!-- 项目坐标信息 -->
    <groupId>com.passage</groupId>
    <artifactId>stripe-payment-demo</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <name>Stripe Payment Demo</name>
    <description>Stripe 支付集成入门：Checkout Session + Webhook + VIP 会员体系</description>

    <properties>
        <java.version>17</java.version>                    <!-- 使用 Java 17 -->
        <stripe.version>31.6.0</stripe.version>             <!-- Stripe Java SDK 版本 -->
        <mybatis-flex.version>1.11.1</mybatis-flex.version>  <!-- MyBatis-Flex 版本 -->
    </properties>

    <dependencies>
        <!-- Spring Boot Web 起步依赖 -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>

        <!-- Stripe Java SDK 31.x（新 API：StripeClient） -->
        <dependency>
            <groupId>com.stripe</groupId>
            <artifactId>stripe-java</artifactId>
            <version>${stripe.version}</version>
        </dependency>

        <!-- MyBatis-Flex 核心依赖 -->
        <dependency>
            <groupId>com.mybatis-flex</groupId>
            <artifactId>mybatis-flex-spring-boot-starter</artifactId>
            <version>${mybatis-flex.version}</version>
        </dependency>

        <!-- H2 数据库（测试用，无需安装真实数据库） -->
        <dependency>
            <groupId>com.h2database</groupId>
            <artifactId>h2</artifactId>
            <scope>test</scope>
        </dependency>

        <!-- Lombok - 简化 POJO 代码 -->
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>

        <!-- Spring Boot Test 测试框架 -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <configuration>
                    <!-- APT 处理器配置：MyBatis-Flex 编译期代码生成 -->
                    <annotationProcessorPaths>
                        <path>
                            <groupId>com.mybatis-flex</groupId>
                            <artifactId>mybatis-flex-annotation-processor</artifactId>
                            <version>${mybatis-flex.version}</version>
                        </path>
                        <path>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                        </path>
                    </annotationProcessorPaths>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

### 3.3 配置文件（application.yml）

```yaml
# application.yml —— 应用配置文件
# Stripe 支付 + 数据源配置

server:
  port: 8080                             # 服务端口号

spring:
  application:
    name: stripe-payment-demo            # 应用名称
  datasource:
    # H2 内存数据库（开发测试用，无需安装 MySQL）
    url: jdbc:h2:mem:stripe_demo
    driver-class-name: org.h2.Driver
    username: sa
    password:

# MyBatis-Flex 配置
mybatis-flex:
  type-aliases-package: com.passage.stripe.entity

# Stripe 支付配置（使用 kebab-case 风格）
stripe:
  # Stripe Secret Key（从 Stripe Dashboard 获取）
  # sk_test_xxx 是测试密钥，sk_live_xxx 是生产密钥
  secret-key: sk_test_xxxxxxxxxxxxxxxxxxxxxxxx
  # Webhook Secret（用于验证 Webhook 签名）
  # whsec_xxx 从 Stripe Dashboard > Webhooks 获取
  webhook-secret: whsec_xxxxxxxxxxxxxxxxxxxxxxxx
  # 成功支付后的跳转 URL
  success-url: https://your-domain.com/vip/success
  # 取消支付后的跳转 URL
  cancel-url: https://your-domain.com/vip/cancel
  # 商品定价（以分为单位，Stripe 使用最小货币单位）
  price:
    # 月卡：29.99 美元 = 2999 分
    monthly: price_monthly_xxx
    # 年卡：199.99 美元 = 19999 分
    yearly: price_yearly_xxx
```

### 3.4 配置类（StripeConfig.java）

```java
package com.passage.stripe.config;

import com.stripe.net.StripeClient;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * StripeConfig - Stripe 配置类
 * <p>
 * 从 application.yml 的 stripe 前缀读取配置，构建 StripeClient 实例。
 * StripeClient 是 Stripe Java SDK 31.x 的新 API 入口，
 * 替代了 30.x 版本的静态方法 API（Stripe.apiKey = "sk_test_xxx"）。
 * <p>
 * StripeClient 是线程安全的，建议全局单例。
 *
 * @author AI-Passage-Creator
 */
@Configuration
@ConfigurationProperties(prefix = "stripe")  // 绑定 application.yml 中 stripe 前缀的配置
public class StripeConfig {

    /** Stripe Secret Key：从 Stripe Dashboard 获取的密钥 */
    private String secretKey;

    /** Webhook Secret：用于验证 Webhook 签名的密钥 */
    private String webhookSecret;

    /** 支付成功后的前端跳转 URL */
    private String successUrl;

    /** 取消支付后的前端跳转 URL */
    private String cancelUrl;

    /** 商品定价配置 */
    private PriceConfig price;

    /**
     * 创建 StripeClient Bean
     * <p>
     * StripeClient 是线程安全的，整个应用共享一个实例。
     * 所有支付操作都通过 client.v1().xxx() 调用。
     *
     * @return StripeClient 实例
     */
    @Bean
    public StripeClient stripeClient() {
        // 使用 Secret Key 构建 StripeClient
        // StripeClient 是 31.x 的新 API，替代了旧的静态方法
        return new StripeClient(secretKey);
    }

    // ========== Getters & Setters ==========

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public String getWebhookSecret() {
        return webhookSecret;
    }

    public void setWebhookSecret(String webhookSecret) {
        this.webhookSecret = webhookSecret;
    }

    public String getSuccessUrl() {
        return successUrl;
    }

    public void setSuccessUrl(String successUrl) {
        this.successUrl = successUrl;
    }

    public String getCancelUrl() {
        return cancelUrl;
    }

    public void setCancelUrl(String cancelUrl) {
        this.cancelUrl = cancelUrl;
    }

    public PriceConfig getPrice() {
        return price;
    }

    public void setPrice(PriceConfig price) {
        this.price = price;
    }

    /**
     * 定价配置内部类
     */
    public static class PriceConfig {
        private String monthly;  // 月卡 Price ID
        private String yearly;   // 年卡 Price ID

        public String getMonthly() {
            return monthly;
        }

        public void setMonthly(String monthly) {
            this.monthly = monthly;
        }

        public String getYearly() {
            return yearly;
        }

        public void setYearly(String yearly) {
            this.yearly = yearly;
        }
    }
}
```

### 3.5 实体类

#### 用户实体（User.java）

```java
package com.passage.stripe.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Table;

import java.time.LocalDateTime;

/**
 * User - 用户实体类
 * <p>
 * 包含 VIP 会员相关信息：VIP 状态、过期时间、每日配额等。
 * 使用 MyBatis-Flex 的 @Table 和 @Column 注解映射数据库表。
 *
 * @author AI-Passage-Creator
 */
@Table(value = "user")                          // 指定数据库表名
public class User {

    @Column(value = "id", isPrimaryKey = true)  // 主键 ID
    private Long id;

    @Column(value = "user_name")                 // 用户名
    private String userName;

    @Column(value = "is_vip")                    // 是否 VIP 会员
    private Boolean isVip;

    @Column(value = "vip_expire_time")           // VIP 过期时间
    private LocalDateTime vipExpireTime;

    @Column(value = "daily_quota")               // 每日配额（生成文章次数上限）
    private Integer dailyQuota;

    @Column(value = "used_quota")                // 已用配额
    private Integer usedQuota;

    @Column(value = "create_time")               // 创建时间
    private LocalDateTime createTime;

    @Column(value = "update_time")               // 更新时间
    private LocalDateTime updateTime;

    // ========== Getters & Setters ==========

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public Boolean getIsVip() {
        return isVip;
    }

    public void setIsVip(Boolean isVip) {
        this.isVip = isVip;
    }

    public LocalDateTime getVipExpireTime() {
        return vipExpireTime;
    }

    public void setVipExpireTime(LocalDateTime vipExpireTime) {
        this.vipExpireTime = vipExpireTime;
    }

    public Integer getDailyQuota() {
        return dailyQuota;
    }

    public void setDailyQuota(Integer dailyQuota) {
        this.dailyQuota = dailyQuota;
    }

    public Integer getUsedQuota() {
        return usedQuota;
    }

    public void setUsedQuota(Integer usedQuota) {
        this.usedQuota = usedQuota;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }

    @Override
    public String toString() {
        return "User{id=" + id + ", userName='" + userName + "', isVip=" + isVip + "}";
    }
}
```

#### 支付记录实体（PaymentRecord.java）

```java
package com.passage.stripe.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * PaymentRecord - 支付记录实体类
 * <p>
 * 记录每次支付的信息，stripe_session_id 字段设唯一索引，用于幂等处理。
 * 同一 sessionId 的重复 Webhook 会被数据库约束拦截。
 *
 * @author AI-Passage-Creator
 */
@Table(value = "payment_record")                // 指定数据库表名
public class PaymentRecord {

    @Column(value = "id", isPrimaryKey = true)  // 主键 ID
    private Long id;

    @Column(value = "user_id")                   // 用户 ID
    private Long userId;

    @Column(value = "stripe_session_id")         // Stripe 会话 ID（唯一索引，用于幂等）
    private String stripeSessionId;

    @Column(value = "stripe_payment_intent_id")  // Stripe 支付意图 ID
    private String stripePaymentIntentId;

    @Column(value = "amount")                    // 支付金额
    private BigDecimal amount;

    @Column(value = "currency")                  // 货币类型：usd、cny 等
    private String currency;

    @Column(value = "status")                    // 支付状态：pending / success / failed / expired
    private String status;

    @Column(value = "pay_time")                  // 支付时间
    private LocalDateTime payTime;

    @Column(value = "create_time")               // 创建时间
    private LocalDateTime createTime;

    @Column(value = "update_time")               // 更新时间
    private LocalDateTime updateTime;

    // ========== Getters & Setters ==========

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getStripeSessionId() {
        return stripeSessionId;
    }

    public void setStripeSessionId(String stripeSessionId) {
        this.stripeSessionId = stripeSessionId;
    }

    public String getStripePaymentIntentId() {
        return stripePaymentIntentId;
    }

    public void setStripePaymentIntentId(String stripePaymentIntentId) {
        this.stripePaymentIntentId = stripePaymentIntentId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getPayTime() {
        return payTime;
    }

    public void setPayTime(LocalDateTime payTime) {
        this.payTime = payTime;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }

    @Override
    public String toString() {
        return "PaymentRecord{id=" + id + ", sessionId='" + stripeSessionId + "', status='" + status + "'}";
    }
}
```

### 3.6 Mapper 接口

#### 用户 Mapper（UserMapper.java）

```java
package com.passage.stripe.mapper;

import com.mybatisflex.core.BaseMapper;
import com.passage.stripe.entity.User;
import org.apache.ibatis.annotations.Mapper;

/**
 * UserMapper - 用户 Mapper 接口
 * <p>
 * 继承 BaseMapper<User> 后自动获得通用 CRUD 方法。
 * 无需任何额外方法定义，BaseMapper 提供所有基础操作。
 *
 * @author AI-Passage-Creator
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {
    // 通用 CRUD 由 BaseMapper 提供，无需额外定义
}
```

#### 支付记录 Mapper（PaymentRecordMapper.java）

```java
package com.passage.stripe.mapper;

import com.mybatisflex.core.BaseMapper;
import com.mybatisflex.core.query.QueryWrapper;
import com.passage.stripe.entity.PaymentRecord;
import org.apache.ibatis.annotations.Mapper;

import static com.passage.stripe.entity.table.Tables.PAYMENT_RECORD;

/**
 * PaymentRecordMapper - 支付记录 Mapper 接口
 * <p>
 * 继承 BaseMapper 获得通用 CRUD 能力。
 * 额外提供 existsBySessionId 方法用于幂等校验。
 *
 * @author AI-Passage-Creator
 */
@Mapper
public interface PaymentRecordMapper extends BaseMapper<PaymentRecord> {

    /**
     * 根据 Stripe Session ID 查询支付记录是否存在
     * <p>
     * 用于幂等校验：判断 Webhook 是否已被处理过。
     * 如果已存在，则跳过重复处理。
     *
     * @param sessionId Stripe 会话 ID
     * @return true 表示已存在（已处理过）
     */
    default boolean existsBySessionId(String sessionId) {
        // 使用 MyBatis-Flex QueryWrapper 查询
        // 字段常量 PAYMENT_RECORD.STRIPE_SESSION_ID 由 APT 编译期生成
        QueryWrapper query = QueryWrapper.create()
                .where(PAYMENT_RECORD.STRIPE_SESSION_ID.eq(sessionId));
        // selectCountByQuery 返回匹配的记录数
        return this.selectCountByQuery(query) > 0;
    }
}
```

### 3.7 支付服务（PaymentService.java）

```java
package com.passage.stripe.service;

import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.net.StripeClient;
import com.stripe.param.checkout.SessionCreateParams;
import com.stripe.param.checkout.SessionCreateParams.LineItem;
import com.passage.stripe.config.StripeConfig;
import com.passage.stripe.entity.PaymentRecord;
import com.passage.stripe.mapper.PaymentRecordMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * PaymentService - 支付服务
 * <p>
 * 负责创建 Stripe Checkout Session 和查询支付状态。
 * 用户在 UI 上点击"开通 VIP"时，调用 createCheckoutSession 方法，
 * 返回 Session URL，前端跳转到该 URL 完成支付。
 * <p>
 * 核心流程：
 * 1. 创建 Checkout Session（指定商品、价格、成功/取消 URL）
 * 2. 记录支付创建记录（状态：pending）
 * 3. 返回 Session URL 给前端跳转
 *
 * @author AI-Passage-Creator
 */
@Service
public class PaymentService {

    /** 日志记录器 */
    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    /** Stripe 客户端（31.x 新 API，线程安全） */
    private final StripeClient stripeClient;

    /** Stripe 配置（从 application.yml 读取） */
    private final StripeConfig stripeConfig;

    /** 支付记录 Mapper */
    private final PaymentRecordMapper paymentRecordMapper;

    /**
     * 构造方法注入
     */
    public PaymentService(StripeClient stripeClient, StripeConfig stripeConfig,
                          PaymentRecordMapper paymentRecordMapper) {
        this.stripeClient = stripeClient;
        this.stripeConfig = stripeConfig;
        this.paymentRecordMapper = paymentRecordMapper;
    }

    /**
     * 创建 Checkout Session —— 用户点击"开通 VIP"时调用
     * <p>
     * Checkout Session 是 Stripe 的托管结账页面。
     * 后端创建 Session 后返回 URL，前端跳转到该 URL 完成支付。
     * 无需自建支付表单，降低 PCI-DSS 合规负担。
     *
     * @param userId    用户 ID（用于支付成功后关联用户）
     * @param priceType 价格类型：monthly（月卡）/ yearly（年卡）
     * @return Checkout Session URL（前端跳转到此地址完成支付）
     */
    public String createCheckoutSession(Long userId, String priceType) {
        // 根据价格类型选择对应的 Price ID
        // Stripe Dashboard > Products 中预先创建商品和定价
        String priceId = "monthly".equals(priceType)
                ? stripeConfig.getPrice().getMonthly()
                : stripeConfig.getPrice().getYearly();

        try {
            // 构建 Checkout Session 创建参数
            // SessionCreateParams 使用 Builder 模式，链式调用
            SessionCreateParams params = SessionCreateParams.builder()
                    // 支付模式：subscription（订阅模式，周期扣费）
                    // 一次性支付使用 payment 模式
                    .setMode(SessionCreateParams.Mode.SUBSCRIPTION)

                    // 成功支付后的跳转 URL
                    // {CHECKOUT_SESSION_ID} 是占位符，Stripe 会自动替换为实际 Session ID
                    .setSuccessUrl(stripeConfig.getSuccessUrl()
                            + "?session_id={CHECKOUT_SESSION_ID}")

                    // 取消支付后的跳转 URL
                    .setCancelUrl(stripeConfig.getCancelUrl())

                    // 关联用户信息：Stripe 会在 Webhook 中回传此标识
                    // 这是关联用户和支付的关键字段
                    .setClientReferenceId(String.valueOf(userId))

                    // 添加商品行：指定 Price ID 和数量
                    // Price ID 在 Stripe Dashboard > Products 中创建
                    .addLineItem(LineItem.builder()
                            .setPrice(priceId)    // 商品定价 ID
                            .setQuantity(1L)      // 数量
                            .build())

                    // 元数据：自定义数据，Webhook 回调时可用
                    // 比 client_reference_id 更灵活，可以传多个字段
                    .putMetadata("user_id", String.valueOf(userId))
                    .putMetadata("price_type", priceType)
                    .build();

            // 调用 Stripe API 创建 Checkout Session
            // stripeClient.v1().checkout().sessions().create() 是 31.x 新 API
            Session session = stripeClient.v1().checkout().sessions().create(params);

            // 记录支付创建记录（状态：pending）
            // 用于后续 Webhook 处理时做幂等校验
            PaymentRecord record = new PaymentRecord();
            record.setUserId(userId);
            record.setStripeSessionId(session.getId());
            record.setStatus("pending");
            paymentRecordMapper.insert(record);

            log.info("创建 Checkout Session 成功: userId={}, sessionId={}, priceType={}",
                    userId, session.getId(), priceType);

            // 返回 Session URL，前端跳转到此地址完成支付
            return session.getUrl();

        } catch (StripeException e) {
            // Stripe API 调用异常：网络问题、密钥错误、参数错误等
            log.error("创建 Checkout Session 失败: userId={}, priceType={}", userId, priceType, e);
            throw new RuntimeException("创建支付会话失败: " + e.getMessage());
        }
    }

    /**
     * 查询支付会话状态
     * <p>
     * 前端在支付成功后跳转回来时，可以调用此接口查询支付状态。
     * 如果 Webhook 还没到，前端可以轮询此接口等待处理完成。
     *
     * @param sessionId Stripe Session ID
     * @return Session 对象（包含支付状态信息）
     */
    public Session retrieveSession(String sessionId) {
        try {
            // 根据 Session ID 查询 Stripe 会话状态
            // retrieve 方法返回最新的 Session 对象
            return stripeClient.v1().checkout().sessions().retrieve(sessionId);
        } catch (StripeException e) {
            log.error("查询支付会话失败: sessionId={}", sessionId, e);
            throw new RuntimeException("查询支付会话失败: " + e.getMessage());
        }
    }
}
```

### 3.8 支付处理服务（PaymentProcessingService.java）

```java
package com.passage.stripe.service;

import com.mybatisflex.core.query.QueryWrapper;
import com.passage.stripe.entity.PaymentRecord;
import com.passage.stripe.entity.User;
import com.passage.stripe.mapper.PaymentRecordMapper;
import com.passage.stripe.mapper.UserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static com.passage.stripe.entity.table.Tables.USER;

/**
 * PaymentProcessingService - 支付处理服务
 * <p>
 * 负责处理支付成功后的业务逻辑：VIP 激活、配额管理、支付记录写入。
 * <p>
 * @Transactional 保证 VIP 激活和支付记录写入的原子性，
 * 防止出现"VIP 已激活但支付记录未写入"或反之的数据不一致。
 *
 * @author AI-Passage-Creator
 */
@Service
public class PaymentProcessingService {

    /** 日志记录器 */
    private static final Logger log = LoggerFactory.getLogger(PaymentProcessingService.class);

    /** 用户 Mapper（MyBatis-Flex BaseMapper） */
    private final UserMapper userMapper;

    /** 支付记录 Mapper */
    private final PaymentRecordMapper paymentRecordMapper;

    /**
     * 构造方法注入
     */
    public PaymentProcessingService(UserMapper userMapper,
                                    PaymentRecordMapper paymentRecordMapper) {
        this.userMapper = userMapper;
        this.paymentRecordMapper = paymentRecordMapper;
    }

    /**
     * 激活 VIP 会员
     * <p>
     * 支付成功后的核心业务逻辑：
     * 1. 更新用户 VIP 状态（isVip = true, 设置过期时间）
     * 2. 重置每日配额（VIP 用户配额更高）
     * 3. 写入支付记录
     * <p>
     * 事务保证：以上操作要么全部成功，要么全部失败。
     *
     * @param userId          用户 ID
     * @param sessionId       Stripe 会话 ID
     * @param paymentIntentId 支付意图 ID
     */
    @Transactional  // 事务保证：VIP 状态更新 + 支付记录写入要么都成功，要么都失败
    public void activateVip(Long userId, String sessionId, String paymentIntentId) {
        // ========== 第一步：更新用户 VIP 状态 ==========
        // 根据用户 ID 查询用户记录
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在: " + userId);
        }

        // 设置 VIP 标志为 true
        user.setIsVip(true);

        // 设置 VIP 过期时间：当前时间 + 30 天
        // 如果是年卡，则 + 365 天（实际项目中根据 priceType 判断）
        user.setVipExpireTime(LocalDateTime.now().plusDays(30));

        // 重置每日配额（VIP 用户配额更高）
        // 普通用户每天可生成 10 篇文章，VIP 用户每天可生成 50 篇
        user.setDailyQuota(50);  // VIP 用户每天可生成 50 篇文章
        user.setUsedQuota(0);    // 重置已用配额

        // 更新用户信息（MyBatis-Flex BaseMapper 内置方法）
        userMapper.updateById(user);

        // ========== 第二步：记录支付记录 ==========
        // 创建支付记录对象
        PaymentRecord record = new PaymentRecord();
        record.setUserId(userId);
        record.setStripeSessionId(sessionId);            // 唯一标识，用于幂等
        record.setStripePaymentIntentId(paymentIntentId);
        record.setAmount(new BigDecimal("29.99"));       // 实际应从 Session 中获取
        record.setCurrency("usd");
        record.setStatus("success");                     // 支付状态：成功
        record.setPayTime(LocalDateTime.now());          // 支付时间

        // 写入支付记录
        // stripe_session_id 有唯一索引，重复插入会抛异常
        paymentRecordMapper.insert(record);

        log.info("VIP 激活成功: userId={}, sessionId={}, expireTime={}",
                userId, sessionId, user.getVipExpireTime());
    }

    /**
     * 降级 VIP 会员（订阅取消 / 过期）
     * <p>
     * 当用户取消订阅或 VIP 到期时调用。
     * 将用户标记为非 VIP，恢复普通用户的配额。
     * 已生成的文章内容不受影响。
     *
     * @param userId 用户 ID
     */
    @Transactional
    public void deactivateVip(Long userId) {
        // 查询用户记录
        User user = userMapper.selectById(userId);
        if (user == null) {
            return;  // 用户不存在直接返回
        }

        // 标记为非 VIP
        user.setIsVip(false);

        // 重置配额为普通用户配额
        user.setDailyQuota(10);   // 普通用户每天可生成 10 篇文章
        user.setUsedQuota(0);

        // 更新用户信息
        userMapper.updateById(user);

        log.info("用户 VIP 已降级: userId={}", userId);
    }

    /**
     * 检查用户配额 —— 每次生成文章前调用
     * <p>
     * 在用户生成文章之前检查配额是否充足。
     * 同时检查 VIP 是否过期，过期则自动降级。
     *
     * @param userId 用户 ID
     * @return true 表示配额充足，false 表示配额不足
     */
    public boolean checkQuota(Long userId) {
        // 查询用户记录
        User user = userMapper.selectById(userId);
        if (user == null) {
            return false;  // 用户不存在，配额不足
        }

        // 检查 VIP 是否过期
        if (user.getIsVip() != null && user.getIsVip()
                && user.getVipExpireTime() != null
                && user.getVipExpireTime().isBefore(LocalDateTime.now())) {
            // VIP 已过期，自动降级为普通用户
            deactivateVip(userId);
            // 重新读取用户信息（降级后配额已重置）
            user = userMapper.selectById(userId);
        }

        // 检查每日配额是否用完
        // usedQuota >= dailyQuota 表示配额不足
        return user.getUsedQuota() < user.getDailyQuota();
    }

    /**
     * 扣减配额 —— 用户成功生成文章后调用
     * <p>
     * 使用 MyBatis-Flex 的条件更新实现原子扣减。
     * 在 WHERE 条件中校验配额是否充足，防止超用。
     * <p>
     * 生成的 SQL：
     * UPDATE user SET used_quota = used_quota + 1
     * WHERE id = ? AND used_quota < daily_quota
     *
     * @param userId 用户 ID
     */
    public void deductQuota(Long userId) {
        // 使用 MyBatis-Flex 的 QueryWrapper 实现原子更新
        // 在 WHERE 条件中校验配额充足：used_quota < daily_quota
        QueryWrapper query = QueryWrapper.create()
                .where(USER.ID.eq(userId))
                .and(USER.USED_QUOTA.lt(USER.DAILY_QUOTA));  // 条件更新：配额充足才扣减

        // 构建更新对象：只更新 used_quota 字段
        User update = new User();
        // 先查询当前 used_quota（条件更新不支持直接 used_quota + 1）
        User currentUser = userMapper.selectById(userId);
        if (currentUser != null) {
            update.setUsedQuota(currentUser.getUsedQuota() + 1);
            // 使用 updateByQuery 执行条件更新
            userMapper.updateByQuery(update, query);
        }
    }
}
```

### 3.9 支付 API 控制器（PaymentController.java）

```java
package com.passage.stripe.controller;

import com.passage.stripe.service.PaymentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * PaymentController - 支付 API 控制器
 * <p>
 * 提供创建支付会话和查询支付状态的 REST API。
 * 前端调用 createCheckoutSession 获取支付 URL，跳转到 Stripe 完成支付。
 *
 * @author AI-Passage-Creator
 */
@RestController
@RequestMapping("/api/payment")
public class PaymentController {

    private static final Logger log = LoggerFactory.getLogger(PaymentController.class);

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    /**
     * 创建 Checkout Session
     * <p>
     * POST /api/payment/create-checkout-session
     * <p>
     * 用户点击"开通 VIP"时调用此接口。
     * 返回的 sessionUrl 是 Stripe 托管结账页面的 URL，
     * 前端 302 跳转到该 URL 完成支付。
     *
     * @param request 请求体：{ "userId": 1, "priceType": "monthly" }
     * @return { "sessionUrl": "https://checkout.stripe.com/..." }
     */
    @PostMapping("/create-checkout-session")
    public ResponseEntity<Map<String, Object>> createCheckoutSession(
            @RequestBody Map<String, Object> request) {

        // 从请求体中提取用户 ID 和价格类型
        Long userId = Long.valueOf(request.get("userId").toString());
        String priceType = (String) request.get("priceType");

        log.info("创建 Checkout Session: userId={}, priceType={}", userId, priceType);

        // 调用支付服务创建 Checkout Session
        String sessionUrl = paymentService.createCheckoutSession(userId, priceType);

        // 返回 Session URL
        // 前端收到后 window.location.href = sessionUrl 跳转
        return ResponseEntity.ok(Map.of(
                "sessionUrl", sessionUrl
        ));
    }

    /**
     * 查询支付会话状态
     * <p>
     * GET /api/payment/session/{sessionId}
     * <p>
     * 前端在支付成功后跳转回来时，可以调用此接口查询支付状态。
     * 如果 Webhook 还没到，前端可以轮询此接口等待处理完成。
     *
     * @param sessionId Stripe 会话 ID
     * @return 支付会话信息
     */
    @GetMapping("/session/{sessionId}")
    public ResponseEntity<Map<String, Object>> retrieveSession(
            @PathVariable String sessionId) {

        // 查询 Stripe 会话状态
        var session = paymentService.retrieveSession(sessionId);

        // 返回会话信息
        return ResponseEntity.ok(Map.of(
                "id", session.getId(),
                "status", session.getStatus(),
                "paymentStatus", session.getPaymentStatus()
        ));
    }
}
```

### 3.10 Webhook 控制器（StripeWebhookController.java）

```java
package com.passage.stripe.controller;

import com.stripe.Event;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.checkout.Session;
import com.stripe.model.billingportal.Invoice;
import com.stripe.model.subscription.Subscription;
import com.stripe.net.Webhook;
import com.passage.stripe.config.StripeConfig;
import com.passage.stripe.service.PaymentProcessingService;
import com.passage.stripe.service.PaymentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * StripeWebhookController - Webhook 控制器
 * <p>
 * 接收 Stripe 的异步事件通知。
 * URL：POST /api/stripe/webhook
 * 该 URL 需要在 Stripe Dashboard > Webhooks 中注册。
 * <p>
 * 关键设计：
 * 1. 签名验证：确保 Webhook 来自 Stripe，而非伪造请求
 * 2. 幂等处理：同一事件可能多次推送，需要保证只处理一次
 * 3. 异步处理：Webhook 应尽快返回 200，避免超时重试
 *
 * @author AI-Passage-Creator
 */
@RestController
@RequestMapping("/api/stripe")
public class StripeWebhookController {

    /** 日志记录器 */
    private static final Logger log = LoggerFactory.getLogger(StripeWebhookController.class);

    /** Stripe 配置 */
    private final StripeConfig stripeConfig;

    /** 支付处理服务 */
    private final PaymentProcessingService paymentProcessingService;

    /**
     * 构造方法注入
     */
    public StripeWebhookController(StripeConfig stripeConfig,
                                   PaymentProcessingService paymentProcessingService) {
        this.stripeConfig = stripeConfig;
        this.paymentProcessingService = paymentProcessingService;
    }

    /**
     * 接收 Stripe Webhook 回调
     * <p>
     * 注意：@RequestBody 接收原始请求体（String），不能使用对象接收。
     * 因为需要原始请求体字符串来验证签名。
     * <p>
     * Stripe 会在支付成功、订阅取消等事件发生时推送 HTTP POST 请求到此地址。
     * 返回 200 表示处理成功，Stripe 停止重试。
     * 返回 500 表示处理失败，Stripe 会按指数退避策略重试。
     *
     * @param payload   请求体（原始 JSON 字符串，必须保持原样用于签名验证）
     * @param sigHeader Stripe-Signature 请求头（包含签名信息）
     * @return 200 OK（处理成功）
     */
    @PostMapping("/webhook")
    public ResponseEntity<String> handleWebhook(
            @RequestBody String payload,                           // 原始请求体（必须保持原样）
            @RequestHeader("Stripe-Signature") String sigHeader) {  // 签名头

        log.info("收到 Stripe Webhook 回调");

        try {
            // ========== 第一步：签名验证 ==========
            // Webhook.constructEvent() 使用 webhook secret 验证签名
            // 如果签名不匹配或 payload 被篡改，抛出 SignatureVerificationException
            // 这一步确保回调来自 Stripe 而不是恶意攻击者
            Event event = Webhook.constructEvent(
                    payload,                                         // 原始请求体字符串
                    sigHeader,                                       // Stripe-Signature 头
                    stripeConfig.getWebhookSecret()                  // Webhook Secret
            );

            // ========== 第二步：事件分发 ==========
            // 根据 event.getType() 分发到不同的事件处理器
            // 常见事件类型：
            // - checkout.session.completed：结账会话完成（支付成功）
            // - checkout.session.expired：结账会话过期（未支付）
            // - customer.subscription.deleted：订阅取消
            // - customer.subscription.updated：订阅更新
            // - invoice.paid：发票支付成功（续费成功）
            // - invoice.payment_failed：发票支付失败（续费失败）
            switch (event.getType()) {
                case "checkout.session.completed":
                    // 支付成功：处理用户 VIP 开通
                    handleCheckoutCompleted(event);
                    break;

                case "checkout.session.expired":
                    // 支付过期：更新支付记录状态为 expired
                    log.info("支付会话过期: {}", event.getId());
                    break;

                case "customer.subscription.deleted":
                    // 订阅取消：处理用户 VIP 降级
                    handleSubscriptionDeleted(event);
                    break;

                case "invoice.paid":
                    // 续费成功：延长 VIP 过期时间
                    log.info("续费成功: {}", event.getId());
                    break;

                case "invoice.payment_failed":
                    // 续费失败：记录失败原因，发送通知
                    log.warn("续费失败: {}", event.getId());
                    break;

                default:
                    // 未处理的事件类型：记录日志，但仍然返回 200
                    log.info("未处理的 Webhook 事件类型: {}", event.getType());
            }

            // 返回 200 OK：Stripe 收到 200 后不再重试
            return ResponseEntity.ok().build();

        } catch (SignatureVerificationException e) {
            // 签名验证失败：可能是伪造的 Webhook 请求
            log.error("Stripe Webhook 签名验证失败", e);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("签名验证失败");
        } catch (Exception e) {
            // 其他异常：返回 500，Stripe 会按指数退避策略重试
            log.error("Webhook 处理失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("处理失败");
        }
    }

    /**
     * 处理支付成功事件 —— checkout.session.completed
     * <p>
     * 业务逻辑：
     * 1. 从 Event 中提取 Session 对象
     * 2. 获取用户 ID（从 metadata 或 client_reference_id）
     * 3. 更新用户 VIP 状态（isVip = true, vipExpireTime = 当前时间 + 套餐时长）
     * 4. 更新支付记录状态为 success
     * 5. 重置用户每日配额
     * <p>
     * 幂等性保证：同一 sessionId 只处理一次。
     * 
     * @param event Stripe 事件对象
     */
    private void handleCheckoutCompleted(Event event) {
        // 从 Event 中反序列化 Session 对象
        // getDataObjectDeserializer() 提供安全的反序列化方式
        // 返回 Optional<Object>，需要手动转换
        Session session = (Session) event.getDataObjectDeserializer()
                .getObject()
                .orElseThrow(() -> new RuntimeException("无法解析 Session 对象"));

        // 获取用户 ID：优先从 metadata 获取，兼容 client_reference_id
        // metadata 是自定义键值对，在创建 Session 时设置
        String userIdStr = session.getMetadata().get("user_id");
        if (userIdStr == null) {
            // 兼容方案：从 client_reference_id 获取
            // client_reference_id 是创建 Session 时传入的字符串
            userIdStr = session.getClientReferenceId();
        }
        Long userId = Long.parseLong(userIdStr);

        // 获取 Stripe 会话 ID 和支付意图 ID
        String sessionId = session.getId();
        String paymentIntentId = session.getPaymentIntent();

        log.info("支付成功 - 用户ID: {}, 会话ID: {}, 支付意图ID: {}",
                userId, sessionId, paymentIntentId);

        // 幂等处理：已处理的 sessionId 跳过
        // 数据库 payment_record 表的 stripe_session_id 字段有唯一索引
        // 如果已处理，重复插入会抛 DataIntegrityViolationException
        // 这里先不做前置检查，让数据库约束兜底
        // 更优雅的方式：在 PaymentProcessingService 中做幂等校验

        // 开通 VIP：更新用户表和支付记录表
        paymentProcessingService.activateVip(userId, sessionId, paymentIntentId);
    }

    /**
     * 处理订阅取消事件 —— customer.subscription.deleted
     * <p>
     * 当用户取消订阅或订阅到期未续费时触发。
     * 降级用户 VIP 为普通用户。
     *
     * @param event Stripe 事件对象
     */
    private void handleSubscriptionDeleted(Event event) {
        // 从 Event 中反序列化 Subscription 对象
        Subscription subscription = (Subscription) event.getDataObjectDeserializer()
                .getObject()
                .orElseThrow(() -> new RuntimeException("无法解析 Subscription 对象"));

        // 从订阅元数据中获取用户 ID
        String userIdStr = subscription.getMetadata().get("user_id");

        log.info("订阅取消 - 用户ID: {}, 订阅ID: {}", userIdStr, subscription.getId());

        // 降级用户 VIP：标记为过期，但保留已生成的文章
        if (userIdStr != null) {
            paymentProcessingService.deactivateVip(Long.parseLong(userIdStr));
        }
    }
}
```

### 3.11 启动类（StripePaymentDemoApplication.java）

```java
package com.passage.stripe;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * StripePaymentDemoApplication - 应用启动类
 * <p>
 * Stripe 支付 Demo 的入口。
 * 使用 @SpringBootApplication 自动配置 Spring Boot 环境。
 * <p>
 * 启动后：
 * 1. 自动扫描 com.passage.stripe 包下的所有组件
 * 2. 自动配置数据源（从 application.yml 读取）
 * 3. 自动注册 MyBatis-Flex Mapper
 * 4. 自动配置嵌入式 Tomcat 服务器
 *
 * @author AI-Passage-Creator
 */
@SpringBootApplication
public class StripePaymentDemoApplication {

    /**
     * 主方法 —— 应用启动入口
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(StripePaymentDemoApplication.class, args);
    }
}
```

### 3.12 测试类（StripePaymentDemoApplicationTests.java）

```java
package com.passage.stripe;

import com.passage.stripe.config.StripeConfig;
import com.passage.stripe.entity.PaymentRecord;
import com.passage.stripe.entity.User;
import com.passage.stripe.mapper.PaymentRecordMapper;
import com.passage.stripe.mapper.UserMapper;
import com.passage.stripe.service.PaymentProcessingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * StripePaymentDemoApplicationTests - Stripe 支付测试类
 * <p>
 * 测试 Stripe 支付的核心功能：
 * 1. VIP 激活流程
 * 2. VIP 降级流程
 * 3. 配额检查
 * 4. 配额扣减
 * 5. 幂等性（重复处理）
 * 6. 配置加载
 *
 * @author AI-Passage-Creator
 */
@SpringBootTest
class StripePaymentDemoApplicationTests {

    /** 用户 Mapper */
    @Autowired
    private UserMapper userMapper;

    /** 支付记录 Mapper */
    @Autowired
    private PaymentRecordMapper paymentRecordMapper;

    /** 支付处理服务 */
    @Autowired
    private PaymentProcessingService paymentProcessingService;

    /** Stripe 配置 */
    @Autowired
    private StripeConfig stripeConfig;

    /** 测试用户 ID */
    private Long testUserId;

    /**
     * 每个测试执行前初始化测试数据
     */
    @BeforeEach
    void setUp() {
        // 清空所有数据
        userMapper.deleteByQuery(new com.mybatisflex.core.query.QueryWrapper().where("1=1"));
        paymentRecordMapper.deleteByQuery(new com.mybatisflex.core.query.QueryWrapper().where("1=1"));

        // 创建一个测试用户（普通用户，非 VIP）
        User user = new User();
        user.setUserName("test_user");
        user.setIsVip(false);           // 非 VIP
        user.setDailyQuota(10);         // 普通用户配额：10
        user.setUsedQuota(0);           // 已用配额：0
        userMapper.insert(user);
        testUserId = user.getId();
    }

    /**
     * 测试 1：VIP 激活
     * <p>
     * 验证支付成功后 VIP 激活流程：
     * 1. isVip 变为 true
     * 2. vipExpireTime 被设置
     * 3. dailyQuota 升级为 VIP 配额
     * 4. 支付记录被写入
     */
    @Test
    @DisplayName("测试 VIP 激活 - 更新用户状态和写入支付记录")
    void testActivateVip() {
        // 执行 VIP 激活
        paymentProcessingService.activateVip(
                testUserId,
                "cs_test_session_123",
                "pi_test_payment_123"
        );

        // 验证用户状态已更新
        User updatedUser = userMapper.selectById(testUserId);
        assertNotNull(updatedUser, "用户应存在");
        assertTrue(updatedUser.getIsVip(), "用户应变为 VIP");
        assertNotNull(updatedUser.getVipExpireTime(), "VIP 过期时间不应为空");
        // VIP 用户的配额应为 50
        assertEquals(50, updatedUser.getDailyQuota().intValue(), "VIP 用户配额应为 50");
        // 已用配额应重置为 0
        assertEquals(0, updatedUser.getUsedQuota().intValue(), "已用配额应重置为 0");

        // 验证支付记录已写入
        boolean exists = paymentRecordMapper.existsBySessionId("cs_test_session_123");
        assertTrue(exists, "支付记录应存在");
    }

    /**
     * 测试 2：VIP 降级
     * <p>
     * 验证订阅取消后的 VIP 降级流程：
     * 1. isVip 变为 false
     * 2. dailyQuota 降级为普通用户配额
     */
    @Test
    @DisplayName("测试 VIP 降级 - 订阅取消后用户降级为普通用户")
    void testDeactivateVip() {
        // 先激活 VIP
        paymentProcessingService.activateVip(
                testUserId,
                "cs_test_session_456",
                "pi_test_payment_456"
        );

        // 验证 VIP 已激活
        User vipUser = userMapper.selectById(testUserId);
        assertTrue(vipUser.getIsVip(), "VIP 应先被激活");

        // 执行 VIP 降级
        paymentProcessingService.deactivateVip(testUserId);

        // 验证用户已降级
        User downgradedUser = userMapper.selectById(testUserId);
        assertFalse(downgradedUser.getIsVip(), "用户应不再是 VIP");
        assertEquals(10, downgradedUser.getDailyQuota().intValue(), "降级后配额应为 10（普通用户）");
    }

    /**
     * 测试 3：配额检查
     * <p>
     * 验证配额检查逻辑：
     * 1. 普通用户配额充足时返回 true
     * 2. 配额用完后返回 false
     */
    @Test
    @DisplayName("测试配额检查 - 配额充足返回 true，用完返回 false")
    void testCheckQuota() {
        // 初始状态：配额充足（0/10）
        boolean hasQuota = paymentProcessingService.checkQuota(testUserId);
        assertTrue(hasQuota, "初始配额应充足");

        // 模拟配额用完
        User user = userMapper.selectById(testUserId);
        user.setUsedQuota(10);  // 已用配额 = 总配额
        userMapper.updateById(user);

        // 再次检查：配额不足
        hasQuota = paymentProcessingService.checkQuota(testUserId);
        assertFalse(hasQuota, "配额用完后应返回 false");
    }

    /**
     * 测试 4：VIP 过期自动降级
     * <p>
     * 验证 VIP 过期后的自动降级逻辑：
     * 1. 设置过期时间为过去
     * 2. 调用 checkQuota 时触发自动降级
     * 3. 用户变回普通用户
     */
    @Test
    @DisplayName("测试 VIP 过期自动降级 - checkQuota 时触发自动降级")
    void testVipExpirationAutoDowngrade() {
        // 先激活 VIP
        paymentProcessingService.activateVip(
                testUserId,
                "cs_test_session_789",
                "pi_test_payment_789"
        );

        // 手动将过期时间设置为过去（模拟 VIP 过期）
        User user = userMapper.selectById(testUserId);
        user.setVipExpireTime(LocalDateTime.now().minusDays(1));  // 昨天过期
        userMapper.updateById(user);

        // 调用 checkQuota 触发自动降级
        paymentProcessingService.checkQuota(testUserId);

        // 验证用户已自动降级
        User downgradedUser = userMapper.selectById(testUserId);
        assertFalse(downgradedUser.getIsVip(), "过期后应自动降级");
        assertEquals(10, downgradedUser.getDailyQuota().intValue(), "降级后配额应为 10");
    }

    /**
     * 测试 5：配置加载
     * <p>
     * 验证 Stripe 配置是否正确加载：
     * 1. secretKey 不为空
     * 2. webhookSecret 不为空
     * 3. successUrl 和 cancelUrl 不为空
     * 4. price 配置不为空
     */
    @Test
    @DisplayName("测试 Stripe 配置加载 - 配置项是否正确绑定")
    void testStripeConfig() {
        // 验证配置已加载（不会为空，因为 application.yml 中有默认值）
        assertNotNull(stripeConfig.getSecretKey(), "secretKey 不应为空");
        assertNotNull(stripeConfig.getWebhookSecret(), "webhookSecret 不应为空");
        assertNotNull(stripeConfig.getSuccessUrl(), "successUrl 不应为空");
        assertNotNull(stripeConfig.getCancelUrl(), "cancelUrl 不应为空");

        // 验证定价配置
        StripeConfig.PriceConfig price = stripeConfig.getPrice();
        assertNotNull(price, "price 配置不应为空");
        assertNotNull(price.getMonthly(), "monthly price 不应为空");
        assertNotNull(price.getYearly(), "yearly price 不应为空");
    }

    /**
     * 测试 6：幂等性 —— 重复支付处理
     * <p>
     * 验证同一 sessionId 的重复处理会被数据库约束拦截：
     * 1. 第一次处理成功
     * 2. 第二次处理抛异常（唯一索引冲突）
     */
    @Test
    @DisplayName("测试幂等性 - 同一 sessionId 重复处理应被拦截")
    void testIdempotency() {
        // 第一次处理：应成功
        paymentProcessingService.activateVip(
                testUserId,
                "cs_test_idempotent",
                "pi_test_idempotent"
        );

        // 验证第一次处理成功
        User userAfterFirst = userMapper.selectById(testUserId);
        assertTrue(userAfterFirst.getIsVip(), "第一次处理应成功激活 VIP");

        // 第二次处理：应抛异常（唯一索引冲突）
        assertThrows(Exception.class, () -> {
            paymentProcessingService.activateVip(
                    testUserId,
                    "cs_test_idempotent",  // 同一个 sessionId
                    "pi_test_idempotent_2"
            );
        }, "重复 sessionId 应抛异常");

        // 验证用户状态未被第二次处理覆盖
        User userAfterSecond = userMapper.selectById(testUserId);
        assertTrue(userAfterSecond.getIsVip(), "VIP 状态应保持不变");
    }
}
```

---

## 四、运行验证

### 4.1 启动项目

```bash
# 进入项目目录
cd stripe-payment-demo

# 编译并启动
mvn spring-boot:run
```

启动前，请确保已在 Stripe Dashboard 中完成以下准备工作：

| 步骤 | 说明 | 操作位置 |
|------|------|----------|
| 1 | 注册 Stripe 账号 | https://dashboard.stripe.com/register |
| 2 | 获取 Secret Key | Dashboard > Developers > API Keys |
| 3 | 创建商品和定价 | Dashboard > Products > Add Product |
| 4 | 配置 Webhook 端点 | Dashboard > Developers > Webhooks > Add Endpoint |
| 5 | 获取 Webhook Secret | 创建 Webhook 端点后自动生成 |

启动后，控制台输出类似：

```
[INFO] Scanning for projects...
[INFO] --- spring-boot:3.2.5:run (default-cli) @ stripe-payment-demo ---
[INFO] Running com.passage.stripe.StripePaymentDemoApplication

  .   ____          _            __ _ _
 /\\ / ___'_ __ _ _(_)_ __  __ _ \ \ \ \
( ( )\___ | '_ | '_| | '_ \/ _` | \ \ \ \
 \\/  ___)| |_)| | | | | || (_| |  ) ) ) )
  '  |____| .__|_| |_|_| |_\__, | / / / /
 =========|_|==============|___/=/_/_/_/
 :: Spring Boot ::               (v3.2.5)

2026-08-22T10:00:00.000+08:00  INFO 12345 --- [main] c.p.s.StripePaymentDemoApplication       : Started StripePaymentDemoApplication in 3.2 seconds
```

### 4.2 测试 API 接口

**1. 创建 Checkout Session：**

```bash
curl -X POST http://localhost:8080/api/payment/create-checkout-session \
  -H "Content-Type: application/json" \
  -d '{"userId": 1, "priceType": "monthly"}'
```

预期返回：

```json
{
  "sessionUrl": "https://checkout.stripe.com/c/pay/cs_test_xxxxxxxxxxxx"
}
```

**2. 查询支付会话状态：**

```bash
curl http://localhost:8080/api/payment/session/cs_test_xxxxxxxxxxxx
```

**3. 测试 Webhook（模拟请求）：**

```bash
# 使用 Stripe CLI 触发测试事件
stripe trigger checkout.session.completed

# 或使用 curl 模拟（需要计算签名，推荐使用 Stripe CLI）
```

### 4.3 运行测试

```bash
mvn test
```

预期输出：

```
[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

---

## 五、项目对照

### 5.1 Demo 与真实项目的对比

| 对比维度 | 本 Demo（stripe-payment-demo） | 真实项目（ai-passage-creator） |
|---------|-------------------------------|-------------------------------|
| 数据库 | H2 内存数据库 | MySQL 生产数据库 |
| 支付方式 | 仅信用卡（默认） | 信用卡 + Apple Pay + Google Pay 等多种支付方式 |
| 订阅管理 | 基础激活/降级 | 完整订阅生命周期管理（创建、续费、取消、过期、暂停） |
| 配额管理 | 简单配额检查 | 配额 + 配额预警 + 配额重置策略 |
| 幂等处理 | 数据库唯一索引 | 数据库唯一索引 + 业务层幂等校验双重保障 |
| Webhook 重试 | 基础处理 | 重试队列 + 死信处理 + 告警通知 |
| 多币种 | 仅 USD | 多币种（按用户所在地自动选择） |
| 退款处理 | 未实现 | 完整退款流程 + 权益回退 |
| 发票系统 | 未实现 | 自动生成发票 + 邮件发送 |
| 异常处理 | 基础异常捕获 | 详细异常分类 + 重试策略 + 告警通知 |

### 5.2 Demo 的局限性

1. **无真实 Stripe API 调用**：测试中使用 H2 内存数据库模拟数据层，不涉及真实的 Stripe API 调用
2. **无 Webhook 端到端测试**：Demo 未实现完整的 Webhook 签名验证和端到端测试
3. **无异步处理**：真实项目中 Webhook 处理通常使用消息队列异步化，避免阻塞
4. **无多币种支持**：Demo 仅支持 USD
5. **无退款处理**：Demo 未实现退款后权益回退的逻辑

### 5.3 进阶路径

从本 Demo 到真实项目，需要掌握以下知识：

| 步骤 | 知识点 | 参考文章 |
|------|--------|----------|
| 1 | Stripe 基础：Checkout Session、Webhook | 07 Stripe 支付（本文） |
| 2 | 订阅管理：Subscription 生命周期 | Stripe 官方文档 |
| 3 | 配额管理：配额扣减、周期性重置 | 07 Stripe 支付（本文进阶） |
| 4 | 幂等性设计：数据库约束 + 业务校验 | 07 Stripe 支付（本文） |
| 5 | 异步处理：消息队列 + Webhook 重试 | 后续系列 |
| 6 | 多币种：本地化定价 | Stripe 官方文档 |

---

## 六、面试题

### Q1: Stripe 的支付流程是怎样的？Checkout Session 和 PaymentIntent 的区别是什么？

**参考答案：**

**Stripe 支付流程（以 Checkout Session 为例）：**

```
1. 用户点击"购买" → 后端调用 Stripe API 创建 Checkout Session
2. 返回 Session URL → 前端 302 跳转到 Stripe 托管结账页面
3. 用户在 Stripe 页面填写卡号、CVV 等信息完成支付
4. Stripe 显示支付成功页面 → 用户跳转回 successUrl
5. 同时 Stripe 异步发送 Webhook 到后端 (checkout.session.completed)
6. 后端验证 Webhook 签名 → 处理支付结果 → 更新用户 VIP 状态
```

**核心设计要点：**

| 要点 | 说明 |
|------|------|
| **前端不接触敏感支付信息** | 卡号、CVV 直接在 Stripe 页面填写，后端不处理 |
| **异步通知为主** | 支付结果通过 Webhook 异步通知，而非依赖前端跳转 |
| **前端跳转仅供参考** | 仅用于用户体验，业务逻辑不应依赖前端跳转 |

**Checkout Session vs PaymentIntent：**

| 维度 | Checkout Session | PaymentIntent |
|------|-----------------|---------------|
| **定位** | 托管结账页面（开箱即用） | 底层支付意图（灵活定制） |
| **UI** | Stripe 托管，无需自建 | 需要自建支付表单 |
| **集成复杂度** | 低（一行代码跳转） | 高（需要处理支付表单、3D 验证等） |
| **PCI 合规** | 零负担（数据在 Stripe 页面处理） | 需要 SAQ A 或更高等级 |
| **适用场景** | 快速集成、标准支付场景 | 定制化支付 UI、复杂支付逻辑 |
| **订阅支持** | 原生支持（mode=subscription） | 需额外处理 |
| **支付方式** | 支持多种（信用卡、Apple Pay、Google Pay） | 支持多种（需自行配置） |

**项目选择 Checkout Session 的原因：** 项目不需要复杂的支付 UI 定制，采用 Checkout Session 可以快速上线，且完全避免 PCI 合规问题。

**追问应对：** "如果支付成功后前端跳转回来，但 Webhook 还没到怎么办？" 答：前端跳转回来时，用户看到的是"支付成功，等待处理中"的过渡页面。前端可以轮询后端查询支付状态（如每 3 秒查一次），后端通过 `stripeSessionId` 查询数据库判断 Webhook 是否已处理。如果 Webhook 延迟超过 30 秒，前端提示"正在处理中，请稍后刷新"。

### Q2: Stripe Webhook 的签名验证原理是什么？如何保证 Webhook 的安全性？

**参考答案：**

**签名验证原理：**

Stripe 使用 HMAC-SHA256 对 Webhook 请求体进行签名，验证流程如下：

```
1. Stripe 准备要发送的 payload（请求体 JSON 字符串）
2. Stripe 使用 webhook secret 作为密钥，对 payload 进行 HMAC-SHA256 签名
3. 签名结果 base64 编码后放入 Stripe-Signature 头
4. Stripe 发送 HTTP POST 请求（包含 payload + Stripe-Signature 头）
5. 后端收到请求后：
   a. 取出 payload（原始请求体字符串）
   b. 取出 Stripe-Signature 头
   c. 使用 webhook secret 对 payload 重新计算 HMAC-SHA256 签名
   d. 对比计算出的签名和 Stripe-Signature 头中的签名
   e. 如果一致，说明请求来自 Stripe 且未被篡改
```

**Stripe-Signature 头的格式：**

```
Stripe-Signature: t=1492774577,v1=5257a869e7eceb32af9a9e9f1e3b3c1b
```

| 字段 | 说明 |
|------|------|
| `t=` | 时间戳，防止重放攻击 |
| `v1=` | 签名值，HMAC-SHA256 计算结果 |

**保证 Webhook 安全性的五道防线：**

| 防线 | 措施 | 说明 |
|------|------|------|
| **1. 签名验证** | HMAC-SHA256 签名验证 | 确保请求来自 Stripe，防止伪造回调 |
| **2. 时间戳校验** | 检查时间戳是否在合理范围 | 防止重放攻击（Replay Attack） |
| **3. HTTPS** | 仅接受 HTTPS 请求 | 防止中间人攻击 |
| **4. IP 白名单** | 仅允许 Stripe IP 段访问 | 网络层过滤（可选） |
| **5. 幂等处理** | 数据库唯一索引 + 业务校验 | 防止重复处理 |

**追问应对：** "Webhook secret 泄露了怎么办？" 答：立即在 Stripe Dashboard > Webhooks 中重新生成 webhook secret，同时更新后端配置。攻击者即使拿到 webhook secret，也无法伪造支付成功事件——因为只有 Stripe 能生成真正的支付成功事件，webhook secret 只是验证签名用的，不是"发消息"的凭证。

### Q3: 如何处理 Stripe Webhook 的幂等性？同一事件被多次推送怎么办？

**参考答案：**

**Stripe Webhook 的幂等性设计需要从三个层面处理：**

**第一层：数据库唯一索引（最可靠的防线）**

```sql
-- payment_record 表的 stripe_session_id 字段设置唯一索引
-- 同一 sessionId 的重复插入会被数据库约束拦截
CREATE UNIQUE INDEX idx_stripe_session_id ON payment_record(stripe_session_id);
```

```java
// 重复插入时抛出 DataIntegrityViolationException
// 捕获后记录日志，不做业务处理
try {
    paymentRecordMapper.insert(record);
} catch (DataIntegrityViolationException e) {
    log.warn("重复的 Webhook 回调，已忽略: sessionId={}", sessionId);
}
```

**第二层：业务层幂等校验**

```java
// 处理前先检查是否已处理过
if (paymentRecordMapper.existsBySessionId(sessionId)) {
    log.warn("支付会话已处理，跳过重复处理: {}", sessionId);
    return; // 直接返回，不重复处理
}
```

**第三层：Stripe 的 Idempotency-Key**

```java
// Stripe 支持 Idempotency-Key 请求头
// 客户端在创建资源时传入唯一 key，相同的 key 只会创建一次资源
// 但 Webhook 是 Stripe 主动推送的，我们无法控制 Stripe 的 Idempotency-Key
// 所以这一层主要用于出站请求（我们调用 Stripe API 时）
StripeResponse<Session> session = stripeClient.v1().checkout().sessions()
    .create(params, StripeRequestOptions.builder()
        .setIdempotencyKey("idempotency_key_" + orderId)
        .build());
```

**幂等处理的完整流程：**

```
Webhook 到达
    ↓
第一步：签名验证（防伪造）
    ↓
第二步：查询数据库，判断 sessionId 是否已处理
    ↓
    已处理 → 返回 200，不重复执行业务逻辑（幂等）
    未处理 → 继续执行
    ↓
第三步：事务内执行 VIP 激活 + 支付记录写入
    ↓
    成功 → 返回 200，Stripe 停止重试
    失败 → 返回 500（数据库唯一索引抛异常），Stripe 继续重试
    ↓
第二/三次重试到达 → 第二步命中，直接返回 200
```

**追问应对：** "如果 Webhook 处理失败（返回 500），Stripe 会重试几次？" 答：Stripe 使用指数退避策略重试，初始间隔 5 秒，后续间隔逐渐增加，最长重试 3 天。如果连续重试 3 天仍然失败，Stripe 会发送"Webhook 送达失败"的邮件通知开发者。因此，**Webhook 处理逻辑必须幂等，因为重试一定会发生**。

---

## 七、避坑指南

### 7.1 不要在前端依赖支付结果

```java
// ❌ 错误：前端跳转回来就认为支付成功
// 用户可能关闭了支付页面，或者支付后立即关闭浏览器
// 前端跳转不可靠，不应该依赖它来做业务逻辑
@GetMapping("/vip/success")
public String vipSuccess(@RequestParam String sessionId) {
    // 这里不能保证支付一定成功
    activateVip(sessionId);  // 错误！
}

// ✅ 正确：Webhook 才是支付成功的可靠依据
// 前端跳转仅供参考，业务逻辑只依赖 Webhook
// 前端可以展示"支付成功，等待处理中"的过渡页面
// 然后轮询后端查询支付状态
```

### 7.2 Webhook 处理必须幂等

```java
// ❌ 错误：Webhook 处理没有幂等校验
// 如果 Stripe 重试，同一事件会被多次处理
// 导致用户被多次激活 VIP，支付记录被重复写入
private void handleCheckoutCompleted(Event event) {
    Session session = ...;
    activateVip(session.getMetadata().get("user_id"));  // 重复执行！
}

// ✅ 正确：先检查是否已处理再执行
// 数据库唯一索引 + 业务层校验双重保障
private void handleCheckoutCompleted(Event event) {
    Session session = ...;
    String sessionId = session.getId();

    // 幂等校验：已处理则跳过
    if (paymentRecordMapper.existsBySessionId(sessionId)) {
        log.warn("重复 Webhook，已忽略: {}", sessionId);
        return;
    }

    // 执行业务逻辑
    paymentProcessingService.activateVip(userId, sessionId, paymentIntentId);
}
```

### 7.3 Webhook 处理时间不要超过 10 秒

```java
// ❌ 错误：Webhook 处理中包含耗时操作
// Stripe 的 Webhook 超时时间是 10 秒
// 如果处理时间超过 10 秒，Stripe 会断开连接并重试
@PostMapping("/webhook")
public ResponseEntity<String> handleWebhook(...) {
    // 耗时操作：发送邮件、调用 AI API、生成报表等
    sendEmail();             // 可能 3 秒
    callAIApi();             // 可能 5 秒
    generateReport();        // 可能 10 秒
    // 总时间可能超过 10 秒！
}

// ✅ 正确：使用消息队列异步处理
// Webhook 只做快速检查和入队，异步消费
@PostMapping("/webhook")
public ResponseEntity<String> handleWebhook(...) {
    // 只做签名验证和入队
    Event event = Webhook.constructEvent(payload, sigHeader, webhookSecret);

    // 将事件 ID 放入消息队列，异步处理
    messageQueue.send("stripe-webhook", event.getId());

    // 立即返回 200
    return ResponseEntity.ok().build();
}
```

### 7.4 配置参考

```yaml
# application.yml —— Stripe 支付完整配置参考
stripe:
  # Stripe Secret Key（测试/生产环境不同）
  secret-key: sk_test_xxxxxxxxxxxxxxxxxxxxxxxx
  # Webhook Secret
  webhook-secret: whsec_xxxxxxxxxxxxxxxxxxxxxxxx
  # 成功/取消 URL
  success-url: https://your-domain.com/vip/success
  cancel-url: https://your-domain.com/vip/cancel
  # 商品定价
  price:
    monthly: price_1Mxxxxxxxxxxxxxxxxxxxx
    yearly: price_1Mxxxxxxxxxxxxxxxxxxxx
  # 额外配置
  api:
    # API 请求超时时间（毫秒）
    connect-timeout: 30000
    read-timeout: 30000
    # 最大重试次数
    max-retries: 3
  webhook:
    # 重试队列配置
    retry:
      # 最大重试次数
      max-attempts: 3
      # 重试间隔（秒）
      backoff-delay: 5
      # 死信队列（超过重试次数后）
      dead-letter-queue: true
```

### 7.5 测试环境注意事项

```yaml
# 开发环境配置建议
# 1. 使用 Stripe 测试密钥（sk_test_xxx），不要使用生产密钥（sk_live_xxx）
# 2. 使用 Stripe CLI 本地测试 Webhook：stripe listen --forward-to localhost:8080/api/stripe/webhook
# 3. 使用 Stripe 测试卡号：4242 4242 4242 4242（任何 CVV + 未来日期）
# 4. 测试环境下不要发送真实邮件

# Stripe CLI 测试 Webhook 命令
# stripe listen --forward-to localhost:8080/api/stripe/webhook
# stripe trigger checkout.session.completed
```