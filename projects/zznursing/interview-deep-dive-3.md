# 智颐养老系统 — 面试题总索引与补充深剖（第三弹）

> 交付日期：2026-08-21
> 定位：整合 docs/review（30 题）+ 我生成的三份文档（27 题）+ 本次新增代码深剖
> 目标：一份在手，面试全有

---

## 📋 第一部分：全部面试题总索引

### 现有 30 题（docs/review/）vs 我生成的 27 题

```
docs/review 30题（6章×5题）             我的文档 27题（3份）
─────────────────────────────────────────────────────────────
01-项目架构与设计篇                       interview-qa.md（11题）
  第1题 模块分层架构设计                    ✅ 架构分析
  第2题 单体vs微服务架构                    ✅ 单体选择
  第3题 Redis缓存设计与应用                 ✅ 多级缓存
  第4题 数据库索引设计与优化                — 
  第5题 定时任务设计与实现                  ✅ Quartz深剖（deep-dive-2）

02-健康评估模块篇                         interview-resume-matching.md（8题）
  第6题 PDF体检报告上传与解析               ✅ OSS+PDF解析
  第7题 百度千帆AI大模型集成               ✅ Prompt设计
  第8题 健康评估业务逻辑实现                ✅ 评分→护理等级
  第9题 健康评估结果缓存策略                ✅ Redis Hash策略
  第10题 健康评估性能优化与异常处理          ✅ 8秒→1秒

03-微信小程序登录与认证篇                  interview-qa.md（11题）
  第11题 微信小程序登录完整链路             ✅ 无感登录流程
  第12题 ThreadLocal用户上下文管理          ✅ 零内存泄漏
  第13题 JWT Token设计与实现               — 本次补充
  第14题 拦截器链设计与实现                 ✅ MemberInterceptor
  第15题 小程序登录安全性考虑               ✅ Token刷新

04-IoT设备管理模块篇                      interview-deep-dive-2.md（8题）
  第16题 华为云IoT平台集成                 ✅ IoTDAClient配置
  第17题 产品列表同步与Redis缓存            ✅ 3分钟→10秒
  第18题 设备注册与位置绑定                 ✅ 三重唯一校验
  第19题 AMQP消息消费与设备数据同步          ✅ saveBatch+Redis
  第20题 设备数据查询与展示                 — 本次补充

05-入住办理与护理管理篇
  第21题 老人入住多表联动业务               ✅ 多表事务（deep-dive-2）
  第22题 护理等级与护理计划管理             — 
  第23题 合同状态定时更新                   ✅ ContractTask
  第24题 护理项目分页查询与详情             — 
  第25题 预约管理功能                       ✅ 6-count(1)超卖风险

06-综合实战与面试模拟篇
  第26题 全局异常处理设计                   — 本次补充
  第27题 MyBatis-Plus高级用法              — 本次补充
  第28题 Spring Security与RBAC权限         — 本次补充
  第29题 项目性能优化总结                   ✅ 三个量化指标
  第30题 项目面试模拟-完整自我介绍          ✅ STAR话术
```

### 本次补充覆盖（6 个空白）

| 原题号 | 主题 | 补充内容 |
|--------|------|----------|
| 第13题 | JWT Token 设计与实现 | TokenService 完整源码 + 刷新机制 |
| 第20题 | 设备数据查询与展示 | DeviceData 分页/时间范围/模糊查询 |
| 第22题 | 护理等级与护理计划管理 | 缓存删除策略 + 事务批量保存 |
| 第26题 | 全局异常处理设计 | 权限/业务/参数/系统 4 层异常处理 |
| 第27题 | MyBatis-Plus 高级用法 | 乐观锁/分页/防全表操作/自动填充 |
| 第28题 | Spring Security + RBAC | 权限链配置 + 动态数据源切换 |

---

## 🔴 补充 1：JWT Token 设计与实现（原第13题）

### 代码事实

```java
// TokenService.createToken() — 双Token机制
// 1. 生成 UUID 作为 token 标识
String token = IdUtils.fastUUID();
loginUser.setToken(token);
// 2. 用户信息存 Redis（30分钟过期）
redisCache.setCacheObject(userKey, loginUser, expireTime, TimeUnit.MINUTES);
// 3. JWT 只存 uuid + username（不存敏感信息）
Map<String, Object> claims = new HashMap<>();
claims.put(Constants.LOGIN_USER_KEY, token);     // Redis key
claims.put(Constants.JWT_USERNAME, loginUser.getUsername());
return Jwts.builder().setClaims(claims).signWith(SignatureAlgorithm.HS512, secret).compact();

// JwtAuthenticationTokenFilter — 每次请求校验
LoginUser loginUser = tokenService.getLoginUser(request);
// 验证 Token，剩余不足20分钟自动刷新
tokenService.verifyToken(loginUser);
// 设置到 SecurityContext
SecurityContextHolder.getContext().setAuthentication(authenticationToken);
```

### 面试官深剖

#### 题目 1「阿里/字节级别」：你们的 JWT 存了什么东西？和 Redis 怎么配合的？

**答案**：
- JWT 本身只存 uuid（Redis key）+ username（显示用）
- 用户详细信息（角色/权限/登录IP/浏览器）存 Redis
- 优点：JWT 体积小（header+payload+signature），用户信息变更时只需要改 Redis 不需要重新签发 JWT

**追问链**：
```
面试官：JWT 过期了但 Redis 没过期，以谁为准？
    ↓
你：以 Redis 为准。JWT 过期时间设置得比 Redis 长，真正校验时先用 parseToken 解析 JWT，再从 Redis 取用户信息。如果 Redis 查不到，说明 Token 已过期需要重新登录。
    ↓
面试官：为什么不用 JWT 存所有信息，这样就不用查 Redis 了？
    ↓
你：① JWT 无法主动失效（除非改密钥） ② 用户权限变更后，旧 JWT 仍然有效 ③ JWT 体积越大，每次请求的 header 开销越大。Redis 方案可以主动踢人、修改权限即时生效。
    ↓
面试官：verifyToken 里的"不足20分钟自动刷新"是怎么实现的？
    ↓
你：判断 expireTime - currentTime <= 20min，如果满足则重新设置 Redis 过期时间（refreshToken），相当于延长了会话有效期。用户如果连续操作，Token 永远不会过期；但如果离开 30 分钟，Redis 过期后需要重新登录。
```

---

## 🔴 补充 2：设备数据查询与展示（原第20题）

### 代码事实

```java
// DeviceDataServiceImpl.selectDeviceDataList() — 多条件分页查询
public TableDataInfo selectDeviceDataList(DeviceDataPageReqDto dto) {
    LambdaQueryWrapper<DeviceData> queryWrapper = new LambdaQueryWrapper<>();
    Page<DeviceData> page = new Page(dto.getPageNum(), dto.getPageSize());
    // 模糊查询设备名称
    if (dto.getDeviceName() != null) 
        queryWrapper.like(DeviceData::getDeviceName, dto.getDeviceName());
    // 精确查询功能ID
    if (dto.getFunctionId() != null)
        queryWrapper.eq(DeviceData::getFunctionId, dto.getFunctionId());
    // 时间范围查询
    if (dto.getStartTime() != null && dto.getEndTime() != null)
        queryWrapper.between(DeviceData::getAlarmTime, dto.getStartTime(), dto.getEndTime());
    page = page(page, queryWrapper);
    return getTableDataInfo(page);
}
```

### 面试官深剖

#### 题目 2「美团级别」：设备数据表数据量会很大（每天几百条），分页查询怎么优化？

**答案**：
- 当前用 MyBatis-Plus Page 分页，小数据量够用
- 数据量大后：① 强制时间范围查询（避免全表扫描） ② 按时间降序 + 索引（alarm_time） ③ 按月分表

**追问链**：
```
面试官：查询条件里用了 like，会不会导致索引失效？
    ↓
你：like 前置通配符（%xxx）会导致索引失效，后置通配符（xxx%）不影响。建议设备名称查询改为精确匹配或后置模糊
    ↓
面试官：设备数据表需要哪些索引？怎么设计？
    ↓
你：① iot_id（设备查询） ② alarm_time（时间范围） ③ function_id（功能过滤） ④ (iot_id, alarm_time) 联合索引
```

---

## 🔴 补充 3：护理等级与护理计划管理（原第22题）

### 代码事实

```java
// 新增护理计划时：事务 + 缓存删除 + 批量保存关联关系
@Transactional(rollbackFor = Exception.class)
public int insertNursingPlan(NursingPlanDto dto) {
    // 1. 保存护理计划
    NursingPlan nursingPlan = new NursingPlan();
    BeanUtils.copyProperties(dto, nursingPlan);
    nursingPlan.setCreateTime(DateUtils.getNowDate());
    nursingPlanMapper.insert(nursingPlan);
    deleteCache();  // 删除缓存
    
    // 2. 批量保存护理项目和护理计划的关联
    int count = nursingProjectPlanMapper.batchInsert(dto.getProjectPlans(), nursingPlan.getId());
    return count == 0 ? 0 : 1;
}
```

### 面试官深剖

#### 题目 3「阿里级别」：为什么修改护理计划时先删除再插入关联关系？

**代码事实**：
```java
// 修改护理计划
public int updateNursingPlan(NursingPlanDto dto) {
    if (dto.getProjectPlans() != null && !dto.getProjectPlans().isEmpty()) {
        // 先删除全部关联
        nursingProjectPlanMapper.deleteByNursingPlanId(dto.getId());
        // 再重新批量插入
        nursingProjectPlanMapper.batchInsert(dto.getProjectPlans(), dto.getId());
    }
    // 修改护理计划主表
    ...
}
```

**答案**：
- 关联关系是"多对多"，用户可能增删改任意项目。直接全删全插比逐条 diff 更简单、更可靠
- 加上 `@Transactional` 保证原子性，不会出现"删了没插"的情况

**追问链**：
```
面试官：为什么不逐条 diff（找出新增/删除/修改的，只操作变更的）？
    ↓
你：全删全插代码简单，在数据量不大（一个护理计划关联 5-10 个项目）时性能差异可以忽略。如果关联数很大（>100），才需要逐条 diff 优化
    ↓
面试官：缓存删除策略（deleteCache）为什么是"删除"而不是"更新"？
    ↓
你：Cache-Aside 模式："先更新数据库，再删除缓存"。下次查询时自动回填，保证缓存一致性。如果更新缓存而不是删除，可能出现并发读写导致缓存和数据库不一致的问题
```

---

## 🔴 补充 4：全局异常处理设计（原第26题）

### 代码事实

```java
@RestControllerAdvice
public class GlobalExceptionHandler {
    // 1. 权限异常 → 403
    @ExceptionHandler(AccessDeniedException.class)
    public AjaxResult handleAccessDenied(AccessDeniedException e, HttpServletRequest request) {
        return AjaxResult.error(HttpStatus.FORBIDDEN, "没有权限，请联系管理员授权");
    }
    
    // 2. 业务异常 → 自定义 code + msg
    @ExceptionHandler(ServiceException.class)
    public AjaxResult handleServiceException(ServiceException e) {
        return StringUtils.isNotNull(e.getCode()) 
            ? AjaxResult.error(e.getCode(), e.getMessage()) 
            : AjaxResult.error(e.getMessage());
    }
    
    // 3. 参数异常 → 400
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public AjaxResult handleMethodArgumentTypeMismatch(...) { ... }
    
    // 4. 系统异常 → 500
    @ExceptionHandler(Exception.class)
    public AjaxResult handleException(Exception e, HttpServletRequest request) {
        return AjaxResult.error(HttpStatus.ERROR, "系统繁忙，请稍后重试");
    }
}
```

### 面试官深剖

#### 题目 4「所有大厂必问」：你们项目异常怎么处理的？4 种异常分别对应什么状态码？

**答案**：
| 异常类型 | 状态码 | 说明 |
|----------|--------|------|
| `AccessDeniedException` | 403 | 权限不足 |
| `ServiceException` | 自定义 | 业务异常（如余额不足） |
| `MethodArgumentTypeMismatchException` | 400 | 参数类型错误 |
| `Exception` | 500 | 系统异常（兜底） |

**追问链**：
```
面试官：为什么业务异常（ServiceException）要自定义 code？
    ↓
你：前端可以按 code 做差异化处理（如：4001=余额不足弹提示，4002=登录超时跳登录页），而不仅仅看 HTTP 状态码
    ↓
面试官：自定义异常继承 RuntimeException 还是 Exception？
    ↓
你：RuntimeException（非受检异常），因为 Spring 事务默认只回滚 RuntimeException，用 Exception 需要手动配置 rollbackFor
```

---

## 🔴 补充 5：MyBatis-Plus 高级用法（原第27题）

### 代码事实

```java
// MybatisPlusConfig — 3 个插件
public MybatisPlusInterceptor mybatisPlusInterceptor() {
    MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
    interceptor.addInnerInterceptor(paginationInnerInterceptor());    // 分页
    interceptor.addInnerInterceptor(optimisticLockerInnerInterceptor());  // 乐观锁
    interceptor.addInnerInterceptor(blockAttackInnerInterceptor());  // 防全表操作
    return interceptor;
}

// MyMetaObjectHandler — 自动填充
public void insertFill(MetaObject metaObject) {
    this.strictInsertFill(metaObject, "createTime", Date.class, new Date());
    if (!isExclude()) {
        this.strictInsertFill(metaObject, "createBy", String.class, loadUserId() + "");
    }
}
```

### 面试官深剖

#### 题目 5「美团/阿里级别」：你们项目里用了 MyBatis-Plus 的哪些高级功能？乐观锁和防全表操作分别解决了什么问题？

**答案**：

| 插件 | 注解 | 解决的问题 |
|------|------|-----------|
| 分页 | `Page` | 物理分页，自动拼接 limit |
| 乐观锁 | `@Version` | 并发更新时防止覆盖 |
| 防全表 | — | 拦截 `update(null)` 或 `delete()` 不带 where |
| 自动填充 | `MetaObjectHandler` | 自动注入 createTime/createBy/updateTime/updateBy |

**追问链**：
```
面试官：自动填充的 isExclude() 判断 /member 路径，为什么要排除小程序端？
    ↓
你：管理端用 SecurityUtils.getLoginUser() 获取管理员 ID，小程序端走 MemberInterceptor + UserThreadLocal，数据源不同。如果混用会导致 createBy 填错（管理员ID写到家属记录里）
    ↓
面试官：乐观锁用 @Version，你们的实体里有 version 字段吗？
    ↓
你：当前的 entity 中部分没有加 @Version（如 elder、check_in）。乐观锁插件已配置但实际没生效——这是可以改进的点。建议在核心业务（如库存、床位、预约）上加上 @Version，防止并发问题
    ↓
面试官：防全表操作（BlockAttack）具体怎么拦截的？
    ↓
你：执行 update(table) 或 delete(table) 没有 where 条件时，会抛出异常。防止程序员手抖执行了全表更新/删除
```

**加分回答**：主动承认"乐观锁配置了但没在实体上使用"——显示对代码的诚实评估。

---

## 🔴 补充 6：Spring Security + RBAC 权限（原第28题）

### 代码事实

```java
// SecurityConfig — 权限链
@Bean
protected SecurityFilterChain filterChain(HttpSecurity httpSecurity) throws Exception {
    return httpSecurity
        .csrf(csrf -> csrf.disable())
        .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests((requests) -> {
            permitAllUrl.getUrls().forEach(url -> requests.antMatchers(url).permitAll());
            // 登录/注册/验证码/小程序接口 允许匿名
            requests.antMatchers("/login", "/register", "/captchaImage", "/member/**").permitAll()
                .anyRequest().authenticated();
        })
        .addFilterBefore(authenticationTokenFilter, UsernamePasswordAuthenticationFilter.class)
        .addFilterBefore(corsFilter, JwtAuthenticationTokenFilter.class)
        .build();
}

// 接口权限注解
@PreAuthorize("@ss.hasPermi('nursing:healthAssessment:list')")
@GetMapping("/list")
public TableDataInfo list(...) { ... }
```

### 面试官深剖

#### 题目 6「阿里/字节级别」：Spring Security 配置里，为什么 CSRF 要禁用？为什么 session 要设置成 STATELESS？

**答案**：
- **CSRF 禁用**：项目使用 JWT Token（放在 header 中），不是 Cookie。CSRF 攻击利用的是 Cookie 自动携带的特性，Token 不存在此问题
- **STATELESS**：不用 Session，每次请求通过 JWT 校验身份。适合前后端分离 + 微服务架构

**追问链**：
```
面试官：`@PreAuthorize("@ss.hasPermi('xxx:xxx:xxx')")` 是怎么实现的？
    ↓
你：Spring Security 的注解式权限控制。@ss 是 PermissionService 的 Bean 名，hasPermi 方法检查当前用户是否有指定权限标识。权限数据存在 Redis 中，用户登录时加载
    ↓
面试官：动态数据源切换（DynamicDataSource）用在哪里？
    ↓
你：配置了主从数据源，但从库开关是 false（没启用）。主要用于读写分离预留，@DataSource 注解可以切换数据源
```

---

## 🔴 补充 7：Redis 限流（Lua 脚本）—— 隐藏亮点

### 代码事实

```java
// 限流 Lua 脚本（Redis 原子操作）
local key = KEYS[1]
local count = tonumber(ARGV[1])
local time = tonumber(ARGV[2])
local current = redis.call('get', key)
if current and tonumber(current) > count then
    return tonumber(current);
end
current = redis.call('incr', key)
if tonumber(current) == 1 then
    redis.call('expire', key, time)
end
return tonumber(current);

// RateLimiterAspect 使用
@RateLimiter(count = 10, time = 60, limitType = LimitType.IP)
```

### 面试官深剖

#### 题目 7（赠品）：你们的限流怎么实现的？Lua 脚本为什么能保证原子性？

**答案**：
- 基于 Redis + Lua 脚本的滑动窗口限流
- Lua 脚本在 Redis 中原子执行，不会被打断
- 支持按 IP 限流（`LimitType.IP`）和按接口限流

**追问链**：
```
面试官：为什么不用 Java 代码做计数，要用 Lua 脚本？
    ↓
你：① Lua 脚本在 Redis 中原子执行，无需加锁 ② 减少网络往返（一次 Redis 调用完成 get+incr+expire） ③ 限流数据在 Redis 中，多实例共享
    ↓
面试官：这个限流方案有什么缺点？
    ↓
你：① 不是真正的滑动窗口（是固定窗口），在窗口边界可能突发双倍流量 ② 改用 Redis Sorted Set 可以实现真滑动窗口，但代码更复杂 ③ 如果 Redis 挂了，限流会失效（可以降级为本地限流）
```

---

## 📋 第二部分：三份文档 + 本篇 = 完整面试题矩阵

| 面试场景 | 用哪份文档 | 重点题目 |
|----------|-----------|----------|
| **自我介绍** | resume-matching | STAR 话术 + 30 秒版本 |
| **项目深挖** | interview-qa | AI/IoT/登录 11 题 |
| **代码细节** | deep-dive-2 | 入住/预约/OSS 8 题 |
| **框架原理** | **本篇** | JWT/MyBatis/Security 6 题 |
| **综合模拟** | review 六篇 | 30 题完整练习 |

### 面试官最可能问的 5 道杀手题

```
1. 超卖/超约问题（6-count(1) + Redis 预扣）    → 展示改进意识
2. 事务失效场景（this.apply() + try-catch）      → 展示代码功底
3. ThreadLocal 内存泄漏（afterCompletion remove） → 展示细节控制
4. 乐观锁未生效（@Version 没加）                → 展示诚实评估
5. 定时任务多实例重复执行（Quartz 集群）         → 展示架构视野
```

---

> 📎 配套文档栈：
> - `interview-qa.md` — 第一弹：11 道深剖题
> - `interview-resume-matching.md` — 简历匹配 + 8 道题
> - `interview-deep-dive-2.md` — 第二弹：8 道代码题
> - **本篇** — 第三弹：6 道补充题 + 总索引 + 5 道杀手题
> - `docs/review/` — 六篇完整复习题（30 题）
> 
> 💡 **面试心法**：不要试图背下所有题目。把 5 道杀手题练到脱口而出，其他题能做到"知道问题在哪、知道怎么改进"就够了。