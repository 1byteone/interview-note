# 🦆 mianshiya-next 完整剖析 — 面试鸭后端架构

> Spring Boot 2.7 + MyBatis-Plus + Redis + ES + Sentinel 的企业级实战

---

## 一、项目概览

| 维度 | 信息 |
|------|------|
| **仓库** | [github.com/liyupi/mianshiya-next](https://github.com/liyupi/mianshiya-next) |
| **Stars** | 511 ⭐ / 104 Fork |
| **架构** | **单体应用**（非微服务，但集成微服务组件） |
| **Spring Boot** | 2.7.2 |
| **Java** | 1.8 |
| **ORM** | MyBatis-Plus 3.5.2 |
| **前端** | Next.js 14 + React 18 + Ant Design 5 |

---

## 二、技术栈全景

```
┌─────────────────────────────────────────────────────────────┐
│                      前端层                                 │
│  Next.js 14 (SSR) + React 18 + Ant Design 5 + Redux       │
│  ECharts (日历图) + ByteMD (Markdown) + OpenAPI 代码生成    │
└──────────────────────────┬──────────────────────────────────┘
                           │ REST API (/api/*)
┌──────────────────────────▼──────────────────────────────────┐
│                      后端层                                 │
│  Spring Boot 2.7.2 + MyBatis-Plus 3.5.2                   │
│  Sa-Token (认证) + Sentinel (限流) + Knife4j (文档)        │
└────────┬──────────────┬──────────────┬─────────────────────┘
         │              │              │
    ┌────▼────┐   ┌────▼────┐   ┌────▼────┐
    │  MySQL  │   │  Redis  │   │   ES    │
    │  Druid  │   │Redisson │   │ 搜索    │
    │ 5 张核心表│   │ HotKey  │   │ 题目全文 │
    └─────────┘   └─────────┘   └─────────┘
         │
    ┌────▼────────────────────────────┐
    │  中间件层                         │
    │  Nacos (配置) + Sentinel (熔断)  │
    │  腾讯云 COS + 微信 SDK + DeepSeek│
    └─────────────────────────────────┘
```

---

## 三、数据库设计（5 张核心表）

| 表 | 说明 | 关键设计 |
|----|------|---------|
| `user` | 用户 | userAccount UNIQUE, unionId/mpOpenId (微信), userRole (user/admin/ban) |
| `question` | 题目 | tags JSON 数组, content TEXT, 逻辑删除 |
| `question_bank` | 题库 | 逻辑删除, userId 关联 |
| `question_bank_question` | 题库-题目关联 | **硬删除**, UNIQUE 约束 |
| `mock_interview` | 模拟面试 | messages JSON 字段存对话, status (0/1/2) |

**设计亮点**:
- 核心表逻辑删除（可恢复），关联表硬删除（减少膨胀）
- 模拟面试的对话记录直接存 JSON 到 MySQL，而非独立表
- 复合索引 `(appId, createTime)` 支持游标分页

---

## 四、5 大企业级中间件实战

### 4.1 🔒 Sa-Token 认证鉴权

```java
// 注解式鉴权 — 比 Spring Security 更简洁
@SaCheckRole(UserConstant.ADMIN_ROLE)
@PostMapping("/add")
public BaseResponse<Long> addQuestion(@RequestBody ...) { ... }

// 同端登录冲突检测
StpUtil.login(userId, DeviceUtils.getRequestDevice(request));
// 同设备新登录自动挤掉旧登录（is-concurrent: false）
```

### 4.2 🚦 Sentinel 限流熔断

**参数限流（按 IP）**:
```java
ParamFlowRule rule = new ParamFlowRule("listQuestionVOByPage")
    .setParamIdx(0)    // 按第一个参数（IP）限流
    .setCount(60)      // 每分钟最多 60 次
    .setDurationInSec(60);
```

**熔断降级（两种策略）**:
- 慢调用熔断：响应 >3s 且比例 >20% → 熔断 60s
- 异常率熔断：异常率 >10% → 熔断 60s

**持久化**: 规则写入本地 JSON 文件，支持热更新。

### 4.3 🔥 京东 HotKey 热 Key 探测

```java
String key = "bank_detail_" + id;
if (JdHotKeyStore.isHotKey(key)) {
    // 命中热 Key → 从 Caffeine 本地缓存获取（L1）
    return JdHotKeyStore.get(key);
}
// 未命中 → 查数据库 → smartSet 回写
JdHotKeyStore.smartSet(key, questionBankVO);
```

**三级缓存**: Caffeine (L1, 本地) → Redis (L2, 分布式) → MySQL (L3)

### 4.4 🔍 Elasticsearch 全文搜索

```java
BoolQueryBuilder boolQuery = QueryBuilders.boolQuery()
    .must(QueryBuilders.multiMatchQuery(keyword, "title", "content"));

NativeSearchQuery searchQuery = new NativeSearchQueryBuilder()
    .withQuery(boolQuery)
    .withSorts(SortBuilders.fieldSort("createTime").order(SortOrder.DESC))
    .withPageable(PageRequest.of(current, size))
    .build();

elasticsearchRestTemplate.search(searchQuery, QuestionEsDTO.class);
```

**增量同步**: `IncSyncQuestionToEs` 定时任务将 MySQL 变更同步到 ES。

### 4.5 🎯 Redis BitMap 签到日历

```java
// Redisson BitSet — 空间效率极高（一年 ~46 字节）
String key = String.format("user:signins:%s:%s", year, userId);
RBitSet bitSet = redisson.getBitSet(key);
bitSet.set(dayOfYear);  // 签到
boolean signed = bitSet.get(dayOfYear);  // 查询是否签到
```

### 4.6 🛡️ 反爬虫（Redis Lua 计数器）

```
用户访问题目 → CounterManager 统计 1 分钟内访问次数
  ├── > 10 次 → 警告（错误码 110）
  └── > 20 次 → 踢下线 + 封号 (userRole = "ban")

Redis Lua 脚本保证原子性
```

---

## 五、统一 API 规范

```java
// 统一响应模型
public class BaseResponse<T> {
    private int code;
    private String message;
    private T data;
}

// 统一错误码
public enum ErrorCode {
    SUCCESS(0, "ok"),
    PARAMS_ERROR(40000, "参数错误"),
    NOT_LOGIN_ERROR(40100, "未登录"),
    NO_AUTH_ERROR(40300, "无权限"),
    SYSTEM_ERROR(50000, "系统异常"),
    NOT_FOUND_ERROR(40400, "资源不存在"),
}

// 统一异常处理
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(BusinessException.class)
    public BaseResponse<?> handleBusinessException(BusinessException e) { ... }
}
```

### 三种视图模型

| 模型 | 用途 | 示例 |
|------|------|------|
| **Entity** | 数据库实体 | Question, User |
| **DTO** | 请求参数 | AddRequest, QueryRequest, UpdateRequest |
| **VO** | 响应视图 | QuestionVO, LoginUserVO |

---

## 六、AI 模拟面试

- 基于 **火山引擎 DeepSeek** 大模型
- 三参数: 工作年限 + 岗位 + 难度
- 对话记录存 MySQL JSON 字段
- 状态流转: 0(待开始) → 1(进行中) → 2(已结束)

---

## 七、面试价值评估

| 知识点 | 体现 | 面试怎么说 |
|--------|------|-----------|
| Sa-Token vs Spring Security | 认证鉴权 | "选 Sa-Token 因为更轻量，原生支持同端互斥" |
| Sentinel 参数限流 | 精细化限流 | "按 IP 维度限流，比全局 QPS 更精准" |
| Redis Lua 脚本 | 原子操作 | "计数器用 Lua 保证原子性，避免竞态" |
| HotKey + Caffeine | 多级缓存 | "L1 本地+L2 分布式+L3 数据库三层架构" |
| ES 增量同步 | 搜索架构 | "MySQL→ES 增量同步，保证搜索实时性" |
| MyBatis-Plus | ORM 选型 | "比 JPA 更灵活，复杂手写 SQL 更方便" |
| Druid 连接池 | 监控 | "慢 SQL 日志 + Web 监控面板 + SQL 防火墙" |

---

*此项目适合展示"企业级实战经验"，覆盖 Java 后端开发几乎所有核心知识点*
