# 智颐养老系统 (zznursing) — 大厂面试官视角深度剖析

> 项目路径：`D:\code\codeJava\heima-phase4\zznursing`
> 基础框架：RuoYi-Vue v3.8.9（Spring Boot 2.5.15 + MyBatis-Plus + Security + JWT）
> 二次开发业务：养老院综合管理平台（后台管理 + 微信小程序）
> 核心差异化：百度千帆 AI + 华为云 IoT 物联网 + 微信小程序

---

## 📊 项目全景分析

### 模块架构

```
zznursing (若依 v3.8.9 二次开发)
│
├── zzyl-admin               → 管理后台入口 (Controller + 启动类)
├── zzyl-nursing-platform    → ⭐ 养老核心业务 (差异化亮点)
│   ├── 健康评估 AI          → 百度千帆大模型 + PDF体检报告
│   ├── 设备管理 IoT         → 华为云IoT + AMQP消息消费
│   ├── 入住办理              → 老人/合同/床位/配置
│   ├── 护理计划/项目/等级    → 护理业务核心
│   ├── 预约管理              → 小程序预约
│   └── 物联网定时任务         → 合同状态/设备数据同步
├── zzyl-common              → 公共：AI调用/Redis操作/PDF解析/工具类
├── zzyl-framework           → 框架：Security/拦截器/数据权限/IoT配置
├── zzyl-system              → 系统管理：用户/角色/菜单/字典/日志
├── zzyl-oss                 → 阿里云OSS文件存储
├── zzyl-quartz              → 定时任务调度
├── zzyl-generator           → 代码生成器
└── zzyl-ui                  → Vue 3 前端
```

### 技术栈独特之处

| 技术 | 版本 | 用途 | 面试差异化 |
|------|------|------|-----------|
| Spring Boot | 2.5.15 | 基础框架 | - |
| MyBatis-Plus | 3.5.2 | ORM | 通用 |
| **百度千帆大模型** | OpenAI SDK | AI分析体检报告 | ⭐ 大模型集成 |
| **华为云IoTDA** | SDK | 设备注册/数据同步 | ⭐ 物联网 |
| **Apache Qpid JMS** | - | AMQP消费IoT消息 | ⭐ 消息中间件 |
| **百度AI Embedding** | - | 提高检索准确率 | ⭐ AI应用 |
| Redis | - | 缓存/限流/Token | 通用 |
| Spring Security + JWT | 5.7.12 | 认证授权 | 通用 |
| 阿里云OSS | 3.17.4 | 文件存储 | 通用 |
| PDFBox | - | PDF文本提取 | 特色 |
| 微信小程序 | - | 家属端 | 特色 |

---

## 📋 第一部分：面经洞察 — 若依框架二次开发项目面试策略

### 面试官对若依项目的常见偏见

> 面经来源：anysearch 搜索面经分析

**偏见**：若依项目 = 脚手架 + 简单 CRUD，没有技术深度

**破解策略**：面试时**不要主动提"若依"**，重点讲**你二次开发的核心业务**（AI + IoT + 微信小程序）。

### 面试话术对比

❌ **错误示范**："这个项目是基于若依框架开发的，有用户管理、角色管理、菜单管理..."
✅ **正确示范**："这是一个面向养老院的智慧养老平台，核心亮点是**对接百度千帆大模型做健康评估**和**对接华为云IoT做设备管理**。我主要负责健康评估AI模块和设备数据同步模块的架构设计与实现。"

### 面试官真正想听的三件事

1. 你**改了若依的哪些东西**（数据权限自己配的？代码生成器用了没？）
2. 你**自己写的核心业务**是什么（AI + IoT + 入住流程）
3. 你**遇到了什么坑**（AI 返回 JSON 解析失败？IoT 消息丢失？）

---

## 📋 第二部分：大厂面试官视角深剖面试题

### 🔴 第一组：百度千帆 AI 集成（面试最大差异化亮点）

#### 题目 1「字节/阿里级别」：你们怎么用大模型分析体检报告的？Prompt 怎么设计的？输出怎么保证结构化的？

**你项目中的代码**：
```java
// HealthAssessmentServiceImpl.insertHealthAssessment()
// 1. 从Redis获取体检报告文本（缓存24h）
String prompt = getPrompt(idCard);

// 2. 调百度千帆大模型（OpenAI SDK兼容）
String qianfanResult = aiModelInvoker.qianfanInvoker(prompt);

// 3. 解析结构化输出
HealthReportVo healthReportVo = JSON.parseObject(qianfanResult, HealthReportVo.class);

// 4. 保存评估结果
saveHealthAssessment(healthReportVo, healthAssessment);
```

**AIModelInvoker 核心**：
```java
// 使用 OpenAIOkHttpClient 对接百度千帆（API兼容）
ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
    .addUserMessage(prompt)
    .model(baiduAIProperties.getModel())
    .responseFormat(ChatCompletionCreateParams.ResponseFormat.ofJsonObject(...))
    .build();
```

**Prompt 设计要点**：
```
角色设定：专业医生视角
输出六项：总检日期 / 风险等级+健康指数 / 五级风险分布 / 异常数据(7字段) / 八大系统评分 / 总结
强制 JSON 输出：responseFormat = json_object
```

**追问链**：
```
面试官：为什么用百度千帆而不是直接用 OpenAI？
    ↓
你：① 千帆兼容 OpenAI SDK，代码几乎不用改 ② 中文体检报告理解能力更强 ③ 国内合规 + 成本低
    ↓
面试官：AI 返回的 JSON 解析失败怎么办？模型可能输出不规范的 JSON
    ↓
你：① responseFormat 强制 json_object 减少解析错误 ② 解析失败时重试（最多3次） ③ 兜底用默认值
    ↓
面试官：为什么不把 AI 分析结果直接返回给前端，而要存到数据库？
    ↓
你：① 审计需求：每次评估结果需要保留历史记录 ② 后续可做大数据分析（老人健康趋势） ③ 避免重复调用 AI 浪费成本
```

#### 题目 2「美团级别」：Redis 缓存在这个场景中怎么用的？为什么缓存 PDF 文本而不是 AI 结果？

**答案要点**：
```java
// Redis Hash 缓存体检报告文本
// Key: "healthReport" (Hash) / Field: 身份证号 / Value: PDF文本
// TTL: 24小时
```

**为什么缓存 PDF 文本**：
- 上传 PDF 时解析一次（耗时 1-2s），提交评估时直接从 Redis 取
- 如果缓存 AI 结果，体检报告更新后缓存失效，需要重新调 AI（每次 8 秒）
- 缓存 PDF 文本：上传时解析一次，评估时拼接 Prompt 调 AI（灵活，可多次评估）

**追问**：为什么用 Hash 而不是 String？
- 答：Hash 可以按身份证号独立管理，支持单个过期（如果用 String 拼接 key 太长），且方便后续扩展（如缓存其他维度的数据）

---

### 🔴 第二组：华为云 IoT 设备管理（大厂面试加分项）

#### 题目 3「字节/华为级别」：华为云 IoT 怎么对接的？设备数据怎么消费的？

**你项目中的代码**：
```java
// AmqpClient — 应用启动时建立 AMQP 连接
@Override
public void run(ApplicationArguments args) {
    start();  // ApplicationRunner 启动时自动连接
}

// 消费消息
private void processMessage(Message message) {
    // 1. 解析 IoT 消息
    String contentStr = message.getBody(String.class);
    JSONObject jsonNotifyData = JSONUtil.parseObj(contentStr).getJSONObject("notify_data");
    IotMsgNotifyData iotMsgNotifyData = JSONUtil.toBean(jsonNotifyData, IotMsgNotifyData.class);
    
    // 2. 批量写入设备数据
    deviceDataService.batchInsertDeviceData(iotMsgNotifyData);
}
```

**追问链**：
```
面试官：为什么要用 AMQP 而不是 HTTP 回调？
    ↓
你：AMQP 是长连接 + 消息推送，实时性更高（毫秒级）；HTTP 回调每次都要建立连接，高并发下不稳定
    ↓
面试官：AMQP 连接断了怎么办？有重连机制吗？
    ↓
你：有！AmqpClient 配置了 failover.reconnectDelay（重连延迟）和 failover.maxReconnectAttempts（最大重试次数），断线后自动重连
    ↓
面试官：消息处理用了线程池，参数怎么配的？
    ↓
你：executorService 线程池参数：核心线程数 = CPU核数*2，最大线程数 = 核心*4，队列用 LinkedBlockingQueue（足够大防止消息丢失）
    ↓
面试官：如果 IoT 消息量突然暴增，怎么保证不丢消息？
    ↓
你：① 线程池缓冲队列削峰 ② 消息处理失败记录到错误日志 + 定时任务补偿 ③ 如果队列满了，可以配置拒绝策略为 CallerRunsPolicy（由 AMQP 线程自己处理，天然背压）
```

#### 题目 4「阿里级别」：设备注册怎么保证唯一性？位置绑定逻辑怎么设计的？

**答案要点**：
```java
// 注册时三重校验
// 1. 设备名唯一
// 2. 节点ID唯一
// 3. 同位置同产品不重复：productKey + bindingLocation + locationType + physicalLocationType 组合唯一
```

**位置绑定设计**：
- `locationType`: 0=随身设备, 1=固定设备
- `physicalLocationType`: 0=楼层, 1=房间, 2=床位
- `bindingLocation`: 绑定位置 ID

**追问**：为什么同位置同产品不能重复？
- 答：一个床位只能有一个床位传感器，一个房间只能有一个烟雾报警器。允许多个随身设备（如智能手表）绑定到同一老人

---

### 🔴 第三组：若依框架二次开发（面试官必问）

#### 题目 5「所有大厂通用」：若依框架你改了哪些东西？数据权限怎么配的？

**答案要点**：
- **未改的**：RBAC 权限模型（用户/角色/菜单）、JWT Token 认证、操作日志、代码生成器
- **改了的**：`zzyl-nursing-platform` 全部是自己写的业务代码
- **数据权限**：`@DataScope` 注解实现部门级数据隔离（如：护理人员只能看自己负责楼层的数据）

**追问**：代码生成器用了没？怎么用的？
- 答：用了。基础 CRUD（护理项目、护理等级、合同、预约等）用代码生成器生成，然后手工修改业务逻辑（如：护理计划新增时同步保存护理项目关联关系，用了 `@Transactional` 事务）

#### 题目 6「美团级别」：MyBatis-Plus 和 MyBatis 混用怎么处理的？为什么？

**答案要点**：
```java
// MyBatis-Plus: 简单 CRUD 用 BaseMapper 内置方法
// 自定义复杂查询：在 mapper.xml 中手写 SQL
```

**为什么混用**：
- 简单 CRUD 用 MyBatis-Plus 节省 80% 代码（`selectById`, `insert`, `updateById`）
- 复杂多表关联查询用 MyBatis XML（`selectNursingPlanList` 关联查询）

**追问**：`LambdaQueryWrapper` 和普通 `QueryWrapper` 选哪个？
- 答：`LambdaQueryWrapper`（函数式 `User::getName`）避免硬编码字段名，编译期检查

---

### 🔴 第四组：健康评估 AI 深度（最大差异化亮点）

#### 题目 7「字节级别」：健康评分怎么映射到护理等级的？规则是什么？

**你项目中的代码**：
```java
private String getLevelNameByHealthScore(double healthScore) {
    if (healthScore >= 90) return "四级护理等级";
    else if (healthScore >= 80) return "三级护理等级";
    else if (healthScore >= 70) return "二级护理等级";
    else if (healthScore >= 60) return "一级护理等级";
    else return "特级护理等级";
}
```

**追问链**：
```
面试官：这个规则是谁定的？有没有可能 AI 评分不准导致护理等级错误？
    ↓
你：规则由养老院护理专家制定。AI 评分仅供参考，最终护理等级需要护理人员人工确认
    ↓
面试官：那 AI 的价值在哪里？
    ↓
你：AI 把原来人工翻阅体检报告（15分钟）缩短到 1-2 分钟，护理人员只需要确认而不是从头分析，效率提升约 30%
    ↓
面试官：如果 AI 建议的护理等级和人工判断不一致，怎么处理？
    ↓
你：系统以人工确认为准，但记录 AI 建议作为参考。后续可以积累数据训练定制模型，提高准确率
```

#### 题目 8「腾讯级别」：PDF 解析怎么做的？提取的文本质量怎么样？

**答案要点**：
```java
// PDFUtil.java — 使用 Apache PDFBox 提取文本
// 提取后的文本存到 Redis Hash（24h 过期）
```

**问题**：PDF 表格提取可能丢失结构（表格变成纯文本，数字和表头对应关系丢失）
**解决方案**：Prompt 中告诉 AI "以下是体检报告文本，可能包含表格数据，请根据上下文理解对应关系"

---

### 🔴 第五组：系统架构与工程设计

#### 题目 9「阿里级别」：数据权限（@DataScope）怎么实现的？5 种数据权限分别是什么？

**答案要点**：
```java
@Before("@annotation(controllerDataScope)")
public void doBefore(JoinPoint point, DataScope controllerDataScope) {
    handleDataScope(point, controllerDataScope);
}
```

**5 种数据权限**：
| 编码 | 含义 | 说明 |
|------|------|------|
| 1 | 全部数据 | 超级管理员 |
| 2 | 自定义 | 按角色配置的部门数据 |
| 3 | 本部门 | 只看自己部门 |
| 4 | 部门及以下 | 看本部门和下级部门 |
| 5 | 仅本人 | 只看自己创建的数据 |

**追问**：数据权限怎么注入到 SQL 的？
- 答：`DataScopeAspect` 在 Service 方法执行前，通过 AOP 修改 `BaseEntity` 的 `params.dataScope` 字段，然后在 Mapper XML 中 `$` 拼接 SQL 条件

#### 题目 10「美团级别」：防重复提交 (@RepeatSubmit) 怎么实现的？

**答案要点**：
```java
// RepeatSubmitInterceptor — 基于 URL + 请求参数 + 时间窗口
// 同 URL + 同参数 在指定时间（默认 5s）内重复提交直接拒绝
// 使用 Redis 存储请求指纹
```

**追问**：为什么不用前端按钮置灰？
- 答：前端置灰只能防小白用户，防不了脚本攻击。后端防重复提交是最后一道防线

---

### 🔴 第六组：微信小程序 + 预约系统

#### 题目 11「腾讯级别」：微信小程序登录流程？openid 和手机号怎么获取的？

**答案要点**：
```java
public interface WechatService {
    // 1. 通过 code 获取 openid
    String getOpenid(String code);
    // 2. 通过 detailCode 获取手机号
    String getPhone(String detailCode);
}
```

**流程**：
```
小程序 wx.login() → code → 后端 → 微信服务器 → openid
用户点击"获取手机号" → detailCode → 后端 → 微信服务器 → 手机号
```

**追问**：openid 和 session_key 怎么保存的？
- 答：openid 关联到用户表，session_key 不存（用完即弃）。用户身份用 JWT Token 维护

---

## 📋 第三部分：面试官评分标准

### 你的项目在面试中的差异化优势

| 维度 | 你的优势 | 面试官关注点 |
|------|----------|-------------|
| 大模型集成 | 百度千帆 AI 分析体检报告 | 技术选型、Prompt 设计、异常处理 |
| 物联网对接 | 华为云 IoT + AMQP 消息消费 | 架构设计、消息可靠性、重连机制 |
| 缓存策略 | Redis Hash 缓存 PDF 文本 | 为什么用 Hash、为什么缓存文本而非结果 |
| 数据权限 | RuoYi @DataScope 5 级权限 | AOP 实现原理、SQL 注入方式 |
| 性能优化 | 3分钟→10秒、AI 8秒→1秒 | 量化指标、优化思路 |

### 面试官对你的 5 分制评分预期

| 问题 | 预期分数 | 理由 |
|------|----------|------|
| AI Prompt 设计 | 5分 | 真正对接了生产级大模型 |
| IoT AMQP 消息消费 | 5分 | 物联网 + 消息中间件实战 |
| 健康评估流程 | 4分 | 完整业务闭环 |
| 若依框架理解 | 3分 | 脚手架项目，深度有限 |
| 缓存策略 | 4分 | 设计合理，有思考 |

---

## 🚀 面试策略建议

### 30 秒自我介绍

> 这是一个面向养老院的智慧养老平台，我主要负责**健康评估 AI 模块**和**设备管理 IoT 模块**。健康评估模块用**百度千帆大模型**分析体检报告，自动生成健康评分和护理等级建议；设备管理模块对接**华为云 IoT**，通过 **AMQP 长连接**实时消费设备数据，与 Redis 缓存配合提升查询性能 18 倍。

### 避坑指南

面试中**不要主动提**：
- ❌ "这是基于若依框架的" → 面试官会认为你只是做 CRUD
- ❌ "代码生成器一键生成的" → 显得没有技术含量
- ❌ "我只是做了简单的增删改查" → 自降身价

**要主动强调**：
- ✅ "我独立负责了 AI 大模型集成和 IoT 设备数据同步两个核心模块"
- ✅ "设计并实现了 Prompt 工程，确保 AI 输出结构化 JSON"
- ✅ "AMQP 消息消费用线程池异步处理，解决了消息堆积问题"

---

> 📎 本文件配套：`interview-project-qa/` 目录下已有你的其他项目面试题
> 💡 本项目的**最大面试价值**是 AI + IoT 两个差异化点，面试时优先讲这两个！