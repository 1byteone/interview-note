# 07 · Stripe 支付集成：Checkout Session + Webhook + VIP 会员体系

> Stripe 是项目支付能力的核心底座，通过 Stripe Checkout Session 托管结账页面，无需自建支付表单；Webhook 异步接收支付结果，实现支付回调处理；VIP 会员体系基于支付记录管理用户权益，配合配额扣减和过期策略实现精细化访问控制。
>
> **对应项目模块：** `ai-passage-creator-server` 支付与会员模块

---

## 一、你必须知道的 3 个核心概念

### 1.1 Stripe API 与 Checkout Session

Stripe 是国际领先的支付服务商（Payment Service Provider, PSP），提供一套完整的支付 API。Java SDK 31.x 版本引入了新的 `StripeClient` 客户端 API（替代了 30.x 的静态方法 API）：

| 概念 | 说明 | 类比 |
|------|------|------|
| **StripeClient** | 31.x 新 API 入口，所有操作通过 `client.v1().xxx()` 调用 | 类似于 Spring 的 RestTemplate |
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
用户在 Stripe 页面完成支付
    ↓
Stripe 异步发送 Webhook 到后端服务器
    ↓
后端处理支付结果，更新用户 VIP 状态
```

### 1.2 Webhook 异步通知

Webhook 是 Stripe 的异步事件通知机制——当支付成功、订阅取消、退款等事件发生时，Stripe 会向开发者预先注册的 URL 发送 HTTP POST 请求，包含事件类型和事件数据。

**Webhook 的核心设计要点：**

| 要点 | 说明 |
|------|------|
| **异步性** | 支付完成和 Webhook 到达之间有时间差（通常几秒到几十秒） |
| **签名验证** | 每个 Webhook 请求都包含 `Stripe-Signature` 头，用于验证来源真实性 |
| **幂等性** | 同一事件可能被多次推送，需要保证重复处理不产生副作用 |
| **重试机制** | 如果 Webhook 返回非 2xx 状态码，Stripe 会按指数退避策略重试（最多 3 天） |
| **事件类型** | 根据 `event.type` 区分不同事件（`checkout.session.completed`、`customer.subscription.deleted` 等） |

### 1.3 VIP 会员体系

项目的 VIP 会员体系基于 Stripe 支付构建，核心数据模型：

```
用户
  ├── isVip: boolean          ← 是否 VIP
  ├── vipExpireTime: datetime ← VIP 过期时间
  ├── dailyQuota: int         ← 每日配额（生成文章次数上限）
  └── usedQuota: int          ← 已用配额

支付记录
  ├── stripeSessionId         ← Stripe 会话 ID（唯一标识）
  ├── stripePaymentIntentId   ← 支付意图 ID
  ├── amount                  ← 支付金额
  └── status                  ← 支付状态

VIP 权益
  ├── AI 生图（Nano Banana）    ← 调用 Gemini AI 生图 API
  ├── SVG 图解                  ← AI 生成 SVG 代码 + 渲染
  └── 更高配额                  ← 每日可生成更多文章
```

**VIP 权益设计原则：**
- **差异化对待**：VIP 用户享有更多配图方式（AI 生图、SVG 图解），普通用户只能使用免费配图方式（Pexels、Mermaid、Iconify）
- **配额管理**：每日配额按用户维度管理，VIP 用户配额更高，过期后自动降级
- **过期自动降级**：VIP 过期后，用户自动降级为普通用户，但已生成的文章内容不受影响

---

## 二、项目中的实战应用

### 2.1 解决了什么问题

| 痛点 | 解决方案 |
|------|----------|
| 需要自建支付表单，开发工作量大 | Stripe Checkout Session 托管结账页面，一行代码跳转 |
| 支付结果需要实时同步到业务系统 | Webhook 异步接收支付回调，更新用户 VIP 状态 |
| 同一 Webhook 可能重复推送 | 数据库唯一索引（`stripeSessionId`）+ 业务幂等校验 |
| 用户付费后权限需要精细控制 | 数据库字段 `isVip` + `vipExpireTime` + `dailyQuota` 三级控制 |
| VIP 过期后需要自动降级 | 每次请求时校验 `vipExpireTime`，过期自动降级 |
| 不同 Stripe 事件的针对性处理 | `switch(event.getType())` 分发到不同处理器 |

### 2.2 VIP 支付体系架构图

```dot
digraph StripePayment {
    rankdir = LR;
    splines = ortho;
    node [fontname = "Microsoft YaHei", fontsize = 11, shape = box, style = rounded];
    edge [fontname = "Microsoft YaHei", fontsize = 10];

    subgraph cluster_frontend {
        label = "前端";
        style = dashed;
        color = "#4A90D9";
        fontcolor = "#4A90D9";
        user [label = "用户点击\n开通 VIP"];
        redirect [label = "跳转 Stripe\n结账页面"];
    }

    subgraph cluster_backend {
        label = "后端服务";
        style = dashed;
        color = "#E67E22";
        fontcolor = "#E67E22";
        createSession [label = "创建 Checkout Session\n返回 sessionUrl"];
        webhook [label = "Webhook 处理器\n验证签名 + 处理事件"];
        quota [label = "配额管理\n每日配额扣减 + 校验"];
    }

    subgraph cluster_stripe {
        label = "Stripe";
        style = dashed;
        color = "#27AE60";
        fontcolor = "#27AE60";
        checkout [label = "Checkout Session\n托管结账页面"];
        paySuccess [label = "支付成功事件\ncheckout.session.completed"];
        subscription [label = "订阅管理\nSubscription"];
    }

    subgraph cluster_db {
        label = "数据库";
        style = dashed;
        color = "#8E44AD";
        fontcolor = "#8E44AD";
        userTable [label = "user 表\nisVip / vipExpireTime\nquota"];
        payRecord [label = "payment_record 表\nstripeSessionId (唯一索引)"];
    }

    user -> createSession;
    createSession -> checkout [label = "返回 sessionUrl"];
    checkout -> redirect [label = "302 跳转"];
    redirect -> user [label = "支付完成"];
    checkout -> paySuccess [label = "异步通知"];
    paySuccess -> webhook;
    webhook -> payRecord [label = "写入支付记录"];
    webhook -> userTable [label = "更新 VIP 状态"];
    user -> quota [label = "每次请求校验配额"];
    quota -> userTable [label = "读写配额"];
}
```

### 2.3 核心代码实现（带逐行中文注释）

#### 2.3.1 Stripe 配置与 Client 初始化

```yaml
# application.yml —— Stripe 支付配置
# 使用 kebab-case 风格（连字符命名）
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

```java
/**
 * Stripe 配置类 —— 构建 StripeClient 实例
 * 
 * StripeClient 是 Stripe Java SDK 31.x 的入口 API
 * 替代了 30.x 版本的静态方法 API（Stripe.apiKey = "sk_test_xxx"）
 * 所有支付操作都通过 client.v1().xxx() 调用
 */
@Configuration
@ConfigurationProperties(prefix = "stripe") // 绑定 application.yml 的 stripe 前缀配置
@Data
public class StripeConfig {

    /** Stripe Secret Key：从 Stripe Dashboard 获取 */
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
     * StripeClient 是线程安全的，建议全局单例
     */
    @Bean
    public StripeClient stripeClient() {
        // 使用 Secret Key 构建 StripeClient
        // StripeClient 是 31.x 的新 API
        return new StripeClient(secretKey);
    }

    /**
     * 定价配置内部类
     */
    @Data
    public static class PriceConfig {
        /** 月卡 Price ID */
        private String monthly;
        /** 年卡 Price ID */
        private String yearly;
    }
}
```

#### 2.3.2 创建 Checkout Session

```java
/**
 * 支付服务 —— 创建 Stripe Checkout Session
 * 
 * Checkout Session 是 Stripe 的托管结账页面
 * 后端创建 Session 后返回 URL，前端跳转到该 URL 完成支付
 * 无需自建支付表单，降低 PCI-DSS 合规负担
 */
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final StripeClient stripeClient;     // Stripe 客户端（31.x 新 API）
    private final StripeConfig stripeConfig;     // Stripe 配置
    private final UserService userService;       // 用户服务（更新 VIP 状态）
    private final PaymentRecordService paymentRecordService; // 支付记录服务

    /**
     * 创建 Checkout Session —— 用户点击"开通 VIP"时调用
     * 
     * @param userId    用户 ID（用于支付成功后关联用户）
     * @param priceType 价格类型：monthly（月卡）/ yearly（年卡）
     * @return Checkout Session URL（前端跳转到此地址完成支付）
     */
    public String createCheckoutSession(Long userId, String priceType) {
        // 根据价格类型选择对应的 Price ID
        String priceId = "monthly".equals(priceType)
                ? stripeConfig.getPrice().getMonthly()
                : stripeConfig.getPrice().getYearly();

        try {
            // 构建 Checkout Session 创建参数
            SessionCreateParams params = SessionCreateParams.builder()
                    // 支付模式：subscription（订阅模式，周期扣费）
                    // 一次性支付使用 payment 模式
                    .setMode(SessionCreateParams.Mode.SUBSCRIPTION)

                    // 成功支付后的跳转 URL
                    .setSuccessUrl(stripeConfig.getSuccessUrl() + "?session_id={CHECKOUT_SESSION_ID}")

                    // 取消支付后的跳转 URL
                    .setCancelUrl(stripeConfig.getCancelUrl())

                    // 关联用户信息：Stripe 会在 Webhook 中回传此标识
                    .setClientReferenceId(String.valueOf(userId))

                    // 关联客户（可选）：如果已有 Customer 则传入，否则 Stripe 自动创建
                    // .setCustomer(customerId)

                    // 添加商品行：指定 Price ID 和数量
                    .addLineItem(LineItem.builder()
                            .setPrice(priceId)   // 商品定价 ID
                            .setQuantity(1L)     // 数量
                            .build())

                    // 元数据：自定义数据，Webhook 回调时可用
                    .putMetadata("user_id", String.valueOf(userId))
                    .putMetadata("price_type", priceType)
                    .build();

            // 调用 Stripe API 创建 Checkout Session
            // stripeClient.v1().checkout().sessions().create() 是 31.x 新 API
            Session session = stripeClient.v1().checkout().sessions().create(params);

            // 记录支付创建记录（状态：pending）
            paymentRecordService.createPendingRecord(userId, session.getId(), priceType);

            // 返回 Session URL，前端跳转至此地址
            return session.getUrl();

        } catch (StripeException e) {
            // Stripe API 调用异常：网络问题、密钥错误、参数错误等
            throw new BusinessException("创建支付会话失败: " + e.getMessage());
        }
    }

    /**
     * 查询支付会话状态
     * 
     * @param sessionId Stripe Session ID
     * @return Session 对象
     */
    public Session retrieveSession(String sessionId) {
        try {
            // 根据 Session ID 查询 Stripe 会话状态
            return stripeClient.v1().checkout().sessions().retrieve(sessionId);
        } catch (StripeException e) {
            throw new BusinessException("查询支付会话失败: " + e.getMessage());
        }
    }
}
```

#### 2.3.3 Webhook 处理 —— 签名验证与事件分发

```java
/**
 * Webhook 控制器 —— 接收 Stripe 的异步事件通知
 * 
 * URL：POST /api/stripe/webhook
 * 该 URL 需要在 Stripe Dashboard > Webhooks 中注册
 * Stripe 会在支付成功、订阅取消等事件发生时推送 HTTP POST 请求到此地址
 * 
 * 关键设计：
 * 1. 签名验证：确保 Webhook 来自 Stripe，而非伪造请求
 * 2. 幂等处理：同一事件可能多次推送，需要保证只处理一次
 * 3. 异步处理：Webhook 应尽快返回 200，避免超时重试
 */
@RestController
@RequestMapping("/api/stripe")
@RequiredArgsConstructor
@Slf4j
public class StripeWebhookController {

    private final PaymentService paymentService;

    /**
     * 接收 Stripe Webhook 回调
     * 
     * 注意：@RequestBody 接收原始请求体（String），不能使用对象接收
     * 因为需要原始请求体来验证签名
     * 
     * @param payload   请求体（原始 JSON 字符串，必须保持原样用于签名验证）
     * @param sigHeader Stripe-Signature 请求头（包含签名信息）
     * @return 200 OK（Stripe 收到 200 后不再重试）
     */
    @PostMapping("/webhook")
    public ResponseEntity<String> handleWebhook(
            @RequestBody String payload,                           // 原始请求体
            @RequestHeader("Stripe-Signature") String sigHeader) { // 签名头

        log.info("收到 Stripe Webhook 回调");

        try {
            // ========== 第一步：签名验证 ==========
            // Webhook.constructEvent() 使用 webhook secret 验证签名
            // 如果签名不匹配或 payload 被篡改，抛出 SignatureVerificationException
            // 这一步确保回调来自 Stripe 而不是恶意攻击者
            Event event = Webhook.constructEvent(
                    payload,                                          // 原始请求体
                    sigHeader,                                        // Stripe-Signature 头
                    stripeConfig.getWebhookSecret()                   // Webhook Secret
            );

            // ========== 第二步：事件分发 ==========
            // 根据 event.type 分发到不同的事件处理器
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
                    handleCheckoutExpired(event);
                    break;

                case "customer.subscription.deleted":
                    // 订阅取消：处理用户 VIP 降级
                    handleSubscriptionDeleted(event);
                    break;

                case "invoice.paid":
                    // 续费成功：延长 VIP 过期时间
                    handleInvoicePaid(event);
                    break;

                case "invoice.payment_failed":
                    // 续费失败：记录失败原因，发送通知
                    handleInvoicePaymentFailed(event);
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
     * 
     * 业务逻辑：
     * 1. 从 Event 中提取 Session 对象
     * 2. 获取用户 ID（从 client_reference_id 或 metadata）
     * 3. 更新用户 VIP 状态（isVip = true, vipExpireTime = 当前时间 + 套餐时长）
     * 4. 更新支付记录状态为 success
     * 5. 重置用户每日配额
     * 
     * 幂等性保证：同一 sessionId 只处理一次
     */
    private void handleCheckoutCompleted(Event event) {
        // 从 Event 中反序列化 Session 对象
        // getDataObjectDeserializer() 提供安全的反序列化方式
        Session session = (Session) event.getDataObjectDeserializer()
                .getObject()
                .orElseThrow(() -> new BusinessException("无法解析 Session 对象"));

        // 获取用户 ID：优先从 metadata 获取，兼容 client_reference_id
        String userIdStr = session.getMetadata().get("user_id");
        if (userIdStr == null) {
            // 兼容方案：从 client_reference_id 获取
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
        if (paymentRecordService.isSessionProcessed(sessionId)) {
            log.warn("支付会话已处理，跳过重复处理: {}", sessionId);
            return;
        }

        // 开通 VIP：更新用户表和支付记录表
        paymentService.activateVip(userId, sessionId, paymentIntentId);
    }

    /**
     * 处理订阅取消事件 —— customer.subscription.deleted
     * 当用户取消订阅或订阅到期未续费时触发
     */
    private void handleSubscriptionDeleted(Event event) {
        Subscription subscription = (Subscription) event.getDataObjectDeserializer()
                .getObject()
                .orElseThrow(() -> new BusinessException("无法解析 Subscription 对象"));

        // 从订阅元数据中获取用户 ID
        String userIdStr = subscription.getMetadata().get("user_id");

        log.info("订阅取消 - 用户ID: {}, 订阅ID: {}", userIdStr, subscription.getId());

        // 降级用户 VIP：标记为过期，但保留已生成的文章
        if (userIdStr != null) {
            paymentService.deactivateVip(Long.parseLong(userIdStr));
        }
    }

    /**
     * 处理续费成功事件 —— invoice.paid
     * 订阅周期扣费成功时触发
     */
    private void handleInvoicePaid(Event event) {
        Invoice invoice = (Invoice) event.getDataObjectDeserializer()
                .getObject()
                .orElseThrow(() -> new BusinessException("无法解析 Invoice 对象"));

        // 续费成功：延长 VIP 过期时间
        String subscriptionId = invoice.getSubscription();
        // 根据 subscriptionId 查询关联用户，延长 VIP 有效期
        log.info("续费成功 - 订阅ID: {}", subscriptionId);
    }

    /**
     * 处理续费失败事件 —— invoice.payment_failed
     * 信用卡扣费失败时触发
     */
    private void handleInvoicePaymentFailed(Event event) {
        Invoice invoice = (Invoice) event.getDataObjectDeserializer()
                .getObject()
                .orElseThrow(() -> new BusinessException("无法解析 Invoice 对象"));

        // 记录失败原因，后续可发送通知提醒用户更新支付方式
        log.warn("续费失败 - 订阅ID: {}, 失败原因: {}",
                invoice.getSubscription(),
                invoice.getLastPaymentError());
    }
}
```

#### 2.3.4 VIP 激活与配额管理

```java
/**
 * 支付处理服务 —— 处理支付成功后的 VIP 激活逻辑
 * 
 * 事务设计：@Transactional 保证 VIP 激活和支付记录写入的原子性
 * 防止出现"VIP 已激活但支付记录未写入"或反之的数据不一致
 */
@Service
@RequiredArgsConstructor
@Transactional // 事务保证：VIP 状态更新 + 支付记录写入要么都成功，要么都失败
public class PaymentProcessingService {

    private final UserMapper userMapper;                   // 用户 Mapper（MyBatis-Flex）
    private final PaymentRecordMapper paymentRecordMapper; // 支付记录 Mapper

    /**
     * 激活 VIP 会员
     * 
     * @param userId          用户 ID
     * @param sessionId       Stripe 会话 ID
     * @param paymentIntentId 支付意图 ID
     */
    public void activateVip(Long userId, String sessionId, String paymentIntentId) {
        // ========== 第一步：更新用户 VIP 状态 ==========
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在: " + userId);
        }

        // 设置 VIP 标志
        user.setIsVip(true);
        // 设置 VIP 过期时间：当前时间 + 30 天
        // 如果是年卡，则 + 365 天
        user.setVipExpireTime(LocalDateTime.now().plusDays(30));
        // 重置每日配额（VIP 用户配额更高）
        user.setDailyQuota(50);  // VIP 用户每天可生成 50 篇文章
        user.setUsedQuota(0);    // 重置已用配额

        // 更新用户信息（MyBatis-Flex BaseMapper 内置方法）
        userMapper.updateById(user);

        // ========== 第二步：记录支付记录 ==========
        PaymentRecord record = new PaymentRecord();
        record.setUserId(userId);
        record.setStripeSessionId(sessionId);           // 唯一标识，用于幂等
        record.setStripePaymentIntentId(paymentIntentId);
        record.setAmount(new BigDecimal("29.99"));      // 实际应从 Session 中获取
        record.setStatus("success");                    // 支付状态
        record.setPayTime(LocalDateTime.now());         // 支付时间

        // 写入支付记录（stripe_session_id 有唯一索引，重复插入会抛异常）
        paymentRecordMapper.insert(record);
    }

    /**
     * 降级 VIP 会员（订阅取消 / 过期）
     * 
     * @param userId 用户 ID
     */
    public void deactivateVip(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            return;
        }

        // 标记为非 VIP
        user.setIsVip(false);
        // 重置配额为普通用户配额
        user.setDailyQuota(10);  // 普通用户每天可生成 10 篇文章
        user.setUsedQuota(0);

        userMapper.updateById(user);

        log.info("用户 VIP 已降级: userId={}", userId);
    }

    /**
     * 检查用户配额 —— 每次生成文章前调用
     * 
     * @param userId 用户 ID
     * @return true 表示配额充足，false 表示配额不足
     */
    public boolean checkQuota(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            return false;
        }

        // 检查 VIP 是否过期
        if (user.getIsVip() && user.getVipExpireTime().isBefore(LocalDateTime.now())) {
            // VIP 已过期，自动降级
            deactivateVip(userId);
            // 重新读取用户信息
            user = userMapper.selectById(userId);
        }

        // 检查每日配额是否用完
        return user.getUsedQuota() < user.getDailyQuota();
    }

    /**
     * 扣减配额 —— 用户成功生成文章后调用
     * 
     * @param userId 用户 ID
     */
    public void deductQuota(Long userId) {
        // 使用 MyBatis-Flex 的 QueryWrapper 实现原子更新
        // UPDATE user SET used_quota = used_quota + 1 WHERE id = ? AND used_quota < daily_quota
        QueryWrapper query = QueryWrapper.create()
                .where(USER.ID.eq(userId))
                .and(USER.USED_QUOTA.lt(USER.DAILY_QUOTA)); // 保证配额充足

        // 原子更新：used_quota + 1
        User update = new User();
        update.setUsedQuota(userMapper.selectById(userId).getUsedQuota() + 1);
        userMapper.updateByQuery(update, query);
    }
}
```

#### 2.3.5 支付记录实体与 Mapper

```java
/**
 * 支付记录实体类 —— 记录每次支付的信息
 * 
 * stripe_session_id 字段设置了唯一索引，用于幂等处理
 * 同一 sessionId 的重复 Webhook 会被数据库约束拦截
 */
@Table(value = "payment_record")
public class PaymentRecord {

    @Column(value = "id", isPrimaryKey = true)
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
}

/**
 * 支付记录 Mapper
 * 继承 BaseMapper 获得通用 CRUD 能力
 */
@Mapper
public interface PaymentRecordMapper extends BaseMapper<PaymentRecord> {

    /**
     * 根据 Stripe Session ID 查询支付记录
     * 用于幂等校验：判断 Webhook 是否已被处理
     */
    default boolean existsBySessionId(String sessionId) {
        // 使用 MyBatis-Flex QueryWrapper 查询
        QueryWrapper query = QueryWrapper.create()
                .where(PAYMENT_RECORD.STRIPE_SESSION_ID.eq(sessionId));
        return this.selectCountByQuery(query) > 0;
    }
}
```

### 2.4 设计亮点

**亮点一：托管结账页面，零 PCI-DSS 合规负担**

使用 Stripe Checkout Session 托管结账页面，用户的支付信息（卡号、CVV、有效期）直接在 Stripe 页面填写，后端服务器**从未接触过敏感支付信息**，降低了 PCI-DSS（支付卡行业数据安全标准）合规负担。这是 Stripe 作为 Payment Service Provider 的核心价值之一。

**亮点二：Webhook 签名验证，防伪造回调**

每个 Webhook 请求都包含 `Stripe-Signature` 头，Stripe 使用 HMAC-SHA256 和 webhook secret 对请求体签名。`Webhook.constructEvent()` 方法自动验证签名，确保回调来自 Stripe 而非恶意攻击者。**这是 Webhook 安全性的第一道防线。**

**亮点三：数据库唯一索引保证幂等**

`payment_record` 表的 `stripe_session_id` 字段设置了唯一索引，配合 Webhook 处理中的 `existsBySessionId()` 校验，形成双重幂等保证。即使 Stripe 重复推送同一事件，数据库约束也会阻止重复写入。

**亮点四：配额管理 + 过期自动降级**

| 控制点 | 逻辑 | 实现 |
|--------|------|------|
| 每次请求检查 | 每次生成文章前调用 `checkQuota()` | 校验配额 + VIP 过期时间 |
| 过期自动降级 | 发现 VIP 过期时自动调用 `deactivateVip()` | 降低配额、取消 VIP 标记 |
| 原子扣减 | 配额扣减使用条件更新 | `WHERE used_quota < daily_quota` |

---

## 三、面试高频题

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
- **前端不接触敏感支付信息**：卡号、CVV 直接在 Stripe 页面填写，后端不处理
- **异步通知为主**：支付结果通过 Webhook 异步通知，而非依赖前端跳转
- **前端跳转仅供参考**：仅用于用户体验，业务逻辑不应依赖前端跳转

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

**项目选择 Checkout Session 的原因：** 项目不需要复杂的支付 UI 定制，采用 Checkout Session 可以快速上线，且完全避免 PCI 合规问题。**"用 30 分钟集成，而不是 3 周自建支付页面。"**

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
Stripe-Signature: t=1492774577,v1=5257a869e7eceb32af9a9e9f1e3b3c1b3c3c3c3c3c3c3c3c3c3c3c3c3c3c3c3c
```

| 字段 | 说明 |
|------|------|
| `t=` | 时间戳，防止重放攻击 |
| `v1=` | 签名值，HMAC-SHA256 计算结果 |

**SDK 中的验证代码：**

```java
// Stripe SDK 的 Webhook.constructEvent() 方法内部做了三件事：
Event event = Webhook.constructEvent(payload, sigHeader, webhookSecret);

// 1. 解析 Stripe-Signature 头，提取时间戳和签名
// 2. 使用 webhookSecret 对 payload 重新计算 HMAC-SHA256
// 3. 比对计算出的签名和头中的签名是否一致
// 4. 检查时间戳是否在合理范围内（防止重放攻击）
// 5. 验证通过后，反序列化 payload 为 Event 对象
```

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

## 四、参考资料与扩展阅读

### 项目源码
- [ai-passage-creator-demo GitHub 仓库](https://github.com/1byteone/ai-passage-creator-demo) — 支付与会员模块

### Stripe 官方文档
- [Stripe Java SDK 31.x 文档](https://stripe.com/docs/api?lang=java) — 完整 API 参考
- [Stripe Checkout Session 文档](https://stripe.com/docs/payments/checkout) — 托管结账页面集成指南
- [Stripe Webhook 文档](https://stripe.com/docs/webhooks) — Webhook 签名验证、重试策略、最佳实践
- [Stripe 订阅管理](https://stripe.com/docs/billing/subscriptions) — 周期性订阅和续费管理

### 支付安全
- [PCI-DSS 合规指南](https://stripe.com/guides/pci-compliance) — Stripe 如何帮助降低 PCI 合规负担
- [Webhook 安全最佳实践](https://stripe.com/docs/webhooks/signatures) — 签名验证和幂等性设计