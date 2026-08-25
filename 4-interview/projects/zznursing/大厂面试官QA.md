# 智颐养老护理系统（zznursing）— 大厂面试官标准QA

## 一、项目概述

**一句话定位**：面向养老机构的全流程数字化护理管理系统，融合"百度千帆AI健康评估 + 华为云IoT设备管理 + 微信小程序"三大差异化能力，覆盖健康评估、入住办理、护理管理、设备监测全链路。

**技术栈全景**：
- RuoYi-Vue v3.8.9 基础框架（Spring Boot 2.5.15 + MyBatis-Plus + Spring Security + JWT）
- Redis（缓存与状态）、OSS（文件存储）、PDFBox（PDF解析）
- 百度千帆大模型（AI健康评估）
- 华为云IoTDA（设备接入）
- AMQP（异步设备数据接收）、CompletableFuture（异步编排）
- 微信小程序（前端）

**核心模块**：
| 模块 | 核心能力 |
|------|----------|
| 健康评估 | PDF体检报告上传解析 + AI评估 + 护理等级映射 |
| 小程序认证 | code换openid、自动注册、JWT双Token、设备指纹、登录锁定 |
| IoT设备管理 | 华为云IoTDA接入、三重校验、AMQP异步入库、时区转换 |
| 入住办理 | 床位+档案+合同+入住记录多表联动事务 |
| 护理管理 | 预约（每日3次取消限制/每时段10人）、合同状态定时更新 |
| 基础能力 | 全局异常处理、统一响应、RBAC权限 |

---

## 二、核心卖点/差异化

1. **AI+养老**：百度千帆AI健康评估，Prompt三层约束保证结构化JSON输出
2. **IoT+养老**：华为云IoTDA设备管理，AMQP异步批量入库，先云后库一致性
3. **移动端全流程**：微信小程序登录认证闭环（JWT双Token+设备指纹+登录锁定）
4. **业务完整性**：入住办理5表联动事务，预约规则精细（3次/天、10人/时段）
5. **工程规范**：全局异常处理 + 统一响应 + 前后台认证隔离

---

## 三、大厂面试官提问逻辑

> 模拟BAT/TMD面试官，从"看起来不错"到"这里有问题"再到"你怎么优化"

---

### 【第一层：广度】整体架构

#### ⭐ Q1：项目整体架构是什么样的？RuoYi-Vue给你提供了什么，你自己写了什么？

**标准答案**：
基于RuoYi-Vue v3.8.9，它提供了：系统管理（用户/角色/菜单）、代码生成器、通用CRUD、登录认证（Spring Security + JWT）、权限控制（RBAC）等基础能力。

**我自己做的核心差异化**：
1. 健康评估模块：PDF上传→PDFBox解析→百度千帆AI评估→护理等级映射
2. IoT设备管理：华为云IoTDA接入、设备注册校验、AMQP数据管道
3. 微信小程序认证：独立于后台管理的两套认证体系
4. 入住办理多表事务联动、预约规则引擎

**加分点**：明确区分"框架给的"和"我做的"，体现对开源框架的理解深度

**常见错误**：把RuoYi-Vue自带功能说成自己开发的，被追问细节立刻暴露

---

#### ⭐ Q2：前后台认证是怎么隔离的？为什么需要两套拦截器？

**标准答案**：
后台管理（PC端）和前台小程序（移动端）是两类用户，认证方式不同：
- **后台**：Spring Security + JWT，基于RBAC（用户→角色→菜单/权限）
- **前台**：小程序code换openid + 自研JWT双Token + 拦截器

**为什么需要两套**：
1. **用户模型不同**：后台是员工（角色权限），前台是家属/老人（手机号+openid）
2. **风险等级不同**：后台权限敏感，需严格RBAC；前台面向C端，需要防薅羊毛、防冒用等策略（登录锁定、设备指纹）
3. **生命周期不同**：后台Token策略和管理员会话习惯匹配，前台需要AccessToken(30min)+RefreshToken(7天)支撑长期挂机

**加分点**：提到"两个Security FilterChain"的配置方式、或两套拦截器的顺序设计

---

### 【第二层：深度】AI健康评估

#### ⭐⭐⭐ Q3：你们的AI健康评估怎么保证大模型输出是合法JSON？"三层控制"具体是什么？

**标准答案**：
**Prompt强约束的JSON输出三层控制**：
1. **Prompt约束**：系统提示词中明确指定输出格式（字段名、类型、取值范围、示例），并强调"只输出JSON，不要任何解释文字"
2. **SDK JSON模式**：百度千帆SDK开启response_format=json_object模式，模型侧强制JSON输出
3. **后端反序列化校验**：用Jackson/Gson反序列化到DTO，解析失败/字段缺失时抛出业务异常，走降级流程

```java
// 第三层：反序列化校验
try {
    HealthAssessmentDTO dto = objectMapper.readValue(llmResponse, HealthAssessmentDTO.class);
    // 业务校验：必填字段、取值范围
    if (dto.getScore() == null || dto.getScore() < 0 || dto.getScore() > 100) {
        throw new AssessmentFormatException("评估结果字段异常");
    }
} catch (JsonProcessingException e) {
    // 降级：重试 or 返回"请重新上传"
    log.error("AI评估JSON解析失败", e);
}
```

**加分点**：说明"兜底原则"——模型不可控，外层必须有一层自己的校验；并可补充"重试一次+降级提示"策略

**常见错误**：只说"我让它输出JSON"，没有三层防护的工程设计意识

---

#### ⭐⭐⭐ Q4：健康评估是异步的，CompletableFuture怎么编排的？为什么不用线程池直接submit？

**标准答案**：
评估流程涉及：PDF解析 → AI评估（网络IO，耗时长） → 规则映射护理等级 → 结果通知，是典型的**IO密集型+编排型**任务。

CompletableFuture编排：
```java
CompletableFuture.supplyAsync(() -> pdfService.parse(pdfFile), executor)
    .thenApplyAsync(assessService::aiAssess, executor)   // AI评估
    .thenApplyAsync(ruleService::mapLevel, executor)     // 规则映射
    .thenAcceptAsync(notifyService::notify, executor)    // 结果通知
    .exceptionally(ex -> {                               // 异常兜底
        log.error("评估链路异常", ex);
        return fallbackResult(ex);
    });
```

**为什么用CompletableFuture**：
1. **链式编排**：多个有依赖关系的步骤清晰表达，避免回调地狱
2. **异常传递**：exceptionally统一处理链路上任何一步的异常
3. **异步非阻塞**：主线程不等待，接口快速返回"评估中"状态，前端轮询/回调拿结果

**加分点**：指出executor是独立线程池（避免占用Tomcat线程），以及拒绝策略、监控指标

**常见错误**：用CompletableFuture但共享Tomcat线程池；或同步调用导致接口RT飙升

---

#### ⭐⭐⭐ Q5：重复提交防护怎么做的？Redis里存PROCESSING状态有什么问题？

**标准答案**：
方案：用户提交评估请求 → 检查Redis中该用户/该报告的评估状态：
- 不存在 → 设置状态为PROCESSING → 异步执行 → 完成后更新COMPLETED
- 已存在PROCESSING → 直接返回"评估中，请稍候"（幂等）

**潜在问题**：
1. **状态过期/丢失**：Redis没设过期时间，一旦流程异常中断，状态永远卡在PROCESSING，用户再也无法重新提交
2. **Redis不可用**：Redis宕机时检查失效，重复提交防护失效
3. **原子性**：check-then-set非原子，并发同时提交会双双通过（需要用setnx或lua）
4. **流程内事务**：如果异步任务自己调用了本地事务方法，@Transactional可能失效（Spring AOP代理自调用问题）

**改进方案**：
- 状态设置用`setIfAbsent`（SETNX）+ 合理TTL（如30分钟，超时自动释放）
- 增加**重试机制**：PROCESSING超过阈值（如10分钟）允许重新提交
- 数据库慢查询表增加唯一索引兜底（userId+reportId唯一）

**加分点**：主动说出"状态机+超时补偿"的通用模式

---

### 【第三层：陷阱】认证安全

#### ⭐⭐⭐⭐ Q6：JWT双Token（30分钟+7天）方案里，RefreshToken怎么防止被滥用？怎么处理登出和Token吊销？

**标准答案**：
**双Token设计**：
- AccessToken（30分钟）：短时效，频繁携带访问，泄露窗口小
- RefreshToken（7天）：仅用于换新AccessToken，存储更谨慎

**防滥用方案**：
1. **RefreshToken轮换**：每次刷新都签发新RefreshToken，旧的全失效（防止重放）
2. **设备指纹绑定**：RefreshToken签发时绑定设备指纹（User-Agent+IP摘要），换端使用则拒绝
3. **Token版本号/黑名单**：Redis存token jti黑名单，登出时加入黑名单

**登出与吊销**：JWT无状态，天然无法主动吊销，方案是：
- 服务端维护**token黑名单（Redis，带过期时间）**
- 或**Token版本号**：用户表中存token_version，签发时写入，校验时比对，version变更则全部旧token失效

**加分点**：说清"无状态Token的有状态补充手段"，以及双Token和单Token的权衡

**常见错误**：说"JWT可以服务端删除"，这是概念错误

---

#### ⭐⭐⭐⭐ Q7：ThreadLocal存用户上下文，为什么会内存泄漏？你们怎么处理的？

**标准答案**：
**问题复现**：Tomcat线程池复用线程，如果请求结束后没有remove，ThreadLocal里的User对象被线程持有的引用链（Thread→ThreadLocalMap→Entry→value）一直引用，无法被GC回收——**ThreadLocal内存泄漏**。

**处理**：
1. **finally中手动remove**（最可靠）：
```java
try {
    UserContext.set(user);
    // 业务逻辑
} finally {
    UserContext.remove(); // 必须remove
}
```
2. **afterCompletion回调**：在拦截器/过滤器的afterCompletion中统一remove，保证任何路径（含异常）都清理

```java
@Override
public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                            Object handler, Exception ex) {
    UserContext.remove();
}
```

**加分点**：补充说明"ThreadLocalMap的Entry继承WeakReference（key弱引用），但value是强引用"这个底层原理，以及Java 8后ThreadLocalMap的expungeStaleEntry机制

---

#### ⭐⭐⭐⭐ Q8：登录失败5次锁定15分钟，怎么防***？暴力破解还有哪些防护？

**标准答案**：
**锁定实现**：Redis计数（key=login:fail:user:xxx，value=失败次数，TTL=15分钟），失败次数≥5拒绝登录。
- 注意：计数、判断、清零要原子（用Lua脚本或incr+expire）

**进一步防护**：
1. **IP维度限流**：同一IP高频登录限流
2. **验证码/滑块**：失败2次后要求验证码
3. **账号+IP双维度**：避免"5次锁定"被攻击者用多个IP绕过
4. **登录风控**：异IP/异设备登录告警
5. **短信验证码限频**：防短信轰炸

**加分点**：提到"锁定是缓解不是根治"，安全是多层防线（验证码+限流+风控+审计）

---

### 【第四层：架构权衡】IoT与数据管道

#### ⭐⭐⭐⭐⭐ Q9：华为云IoTDA的设备数据是怎么接的？AMQP异步接收和数据库写入的"先云后库"一致性怎么保证？

**标准答案**：
**接入链路**：设备上报 → 华为云IoTDA（规则引擎）→ AMQP消息 → 我们的AMQP消费端（4个连接线程池）→ 批量写入数据库。

**先云后库**：先确认设备在华为云侧注册成功（云侧权威），再落本地库；云侧失败则回滚本地操作——保证**云是事实来源（source of truth）**。

**一致性问题与取舍**：
1. **双写一致性**：云侧成功、本地失败，两边数据不一致。方案：本地失败→记录失败日志/补偿任务→定时对账（拉云侧数据比对）
2. **AMQP消费的幂等性**：消息可能重复投递（at-least-once），消费端必须幂等（如按设备ID+时间戳去重）
3. **批量入库的失败处理**：批量事务里一条失败全部回滚还是部分成功？（折中：按批提交，失败批记录重试）

**加分点**：主动提出"对账任务"这个杀手锏——所有双写系统的兜底方案

---

#### ⭐⭐⭐⭐⭐ Q10：时区转换（UTC→上海）为什么容易踩坑？如果设备在别的时区怎么办？

**标准答案**：
**坑点**：
1. Linux服务器默认UTC，MySQL连接串没配serverTimezone会导致时间差8小时
2. 数据库存储用哪个时区？建议：**DB统一存UTC，展示层转东八区** - 或者DB直接存本地时间但必须全链路约定一致
3. 夏令时国家（欧美）设备上报的时间戳含时区偏移（+02:00等），直接解析为东八区会错乱

**方案**：
- 设备上报带`timestamp`字段+`timezone`偏移量，服务端统一转UTC存储
- 展示层按用户/机构时区转回
- 用`java.time.ZonedDateTime`/`OffsetDateTime`替代过时的`Date`/`SimpleDateFormat`

**加分点**：提到"时间问题的最佳实践是存储UTC、展示本地化、传输带偏移"三原则

---

### 【第五层：业务一致性】

#### ⭐⭐⭐⭐ Q11：入住办理5表联动事务（床位+档案+合同+入住记录+配置），@Transactional够用吗？什么情况会失效？

**标准答案**：
**@Transactional(rollbackFor = Exception.class) 覆盖**：任何RuntimeException和受检异常都回滚。

**会失效的场景**（背锅热点）：
1. **方法自调用**：同类中A方法调B方法（this.method()），Spring AOP代理拦截不到，事务不生效
2. **非public方法**：@Transactional只对public生效
3. **异常被catch吞掉**：方法内catch了异常没重新抛出，事务感知不到
4. **多数据源**：跨数据源事务需要分布式事务（@Transactional只能管单数据源）
5. **传播行为/回滚条件配置错误**：默认只回滚RuntimeException，CheckedException不回滚（除非rollbackFor）

**加分点**：主动说出"事务边界和锁的关系"——5表联动还有并发问题：两个老人同时入住同一张床，需要床位的**唯一状态+行锁/乐观锁**

---

#### ⭐⭐⭐⭐ Q12：预约管理"每天3次取消限制、每时段10人容量"怎么在并发下保证不超卖？

**标准答案**：
**容量10人/时段**的实现：
- 方案A：数据库唯一约束（时段+序号1-10）——刚性但表结构复杂
- 方案B：**Redis原子自增**：`INCR visit:slot:{date}:{time}`，返回值>10则拒绝并`DECR`回滚
- 方案C：数据库加锁（select ... for update 该时段行）——并发低时可用

**每天3次取消限制**：Redis计数（key=user:{id}:cancel:{date}），每次取消`INCR`并检查>3拒绝；注意要设TTL=当天结束

**加分点**：指出"Redis INCR是原子操作，天然抗并发"，并说明"Redis和DB双写的一致性兜底"（如超卖后补偿）

---

## 四、挑刺点/隐患排查

### 隐患1：AI评估PROCESSING状态无TTL ⚠️ 高
**问题**：Redis状态未设置过期，异常中断会永久卡死在PROCESSING
**改进**：setIfAbsent + TTL(30分钟) + 超时允许重试

### 隐患2：CompletableFuture线程池与异步事务 ⚠️ 高
**问题**：异步线程中调用的方法若依赖Spring事务代理，自调用失效；且线程池如果复用默认ForkJoinPool会干扰其他任务
**改进**：独立线程池+事务边界下移到独立Bean方法（通过注入代理调用）

### 隐患3：JWT无状态吊销难题 ⚠️ 中
**问题**：未提黑名单/版本号，Token泄露后无法主动失效
**改进**：Redis黑名单 + refreshToken轮换 + token_version

### 隐患4：双写（云+库）一致性 ⚠️ 中
**问题**：先云后库，本地写失败无补偿
**改进**：失败记录+定时对账任务（拉云侧与本地比对修复）

### 隐患5：预约容量检查非原子 ⚠️ 中
**问题**：如果容量检查用count查询再insert，并发会超卖
**改进**：Redis INCR原子计数或DB唯一约束

### 隐患6：PDF上传安全 ⚠️ 中
**问题**：PDFBox解析恶意PDF可能存在XXE/资源耗尽（billion laughs）风险
**改进**：限制文件大小、禁用外部实体、解析超时、上传文件类型白名单

### 隐患7：设备注册三重校验在分布式下的并发 ⚠️ 低
**问题**：重名校验（名称/NodeId/位置+产品组合）check-then-insert非原子
**改进**：DB加组合唯一索引兜底

---

## 五、可以反杀的亮点

### 面试被问"你项目最大的挑战是什么？"

> **挑战**：养老场景是**交付级系统**——健康评估、设备数据、办理流程任何一环出错都可能影响老人安全，所以我把工程重心放在"可靠性和一致性"上，而不是功能堆叠。
>
> **方案**：
> 1. AI评估输出"三层控制"兜底，模型不可控但业务可控
> 2. 异步链路用CompletableFuture独立线程池+exceptionally兜底
> 3. 双系统（华为云+本地库）用"先云后库+对账补偿"保证最终一致
> 4. 关键业务（入住办理5表联动）事务边界严格管理，预约容量用原子计数
>
> **反思**：最想补的是**全链路监控与对账自动化**——现在对账还是按批跑，如果做成实时对账看板，可靠性会再上一个台阶。

### 引导话题技巧

- "AI评估JSON解析失败走降级" → 引导"LLM输出不可控时如何设计可靠的外层防御"
- "JWT吊销难" → 引导"无状态认证的工程化补充（黑名单/版本号/设备指纹）"
- "ThreadLocal内存泄漏" → 引导"线程池复用下的资源生命周期管理"
- "先云后库一致性" → 引导"跨系统数据一致性的终极方案：对账"

### 加分项总结

| 维度 | 加分点 |
|------|--------|
| AI | Prompt三层约束、异步编排、降级策略 |
| 认证 | 双Token、设备指纹、登录锁定、ThreadLocal治理 |
| IoT | AMQP批量消费、幂等、时区三原则、对账补偿 |
| 事务 | rollbackFor、自调用失效、事务边界与锁 |
| 一致性 | Redis原子计数、唯一索引兜底、状态机+超时补偿 |
| 工程 | 全局异常、统一响应、前后台认证隔离 |
