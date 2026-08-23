# 01 · AOP 实战：自定义注解 + 四种切面模式

> AOP（面向切面编程）是 Spring 最核心的能力之一。看 mall-exercise 模块如何用自定义注解和四种切面模式，实现缓存、日志、权限、监控四大横切关注点。
>
> **对应项目：** `mall-exercise/src/main/java/itcast/cloud/mall/exercise/aop/`

---

## 一、基础概念

### 1.1 什么是 AOP

AOP（Aspect-Oriented Programming）将**横切关注点**（缓存、日志、权限、性能监控）从业务代码中抽离出来，通过切面统一管理。

```
业务代码（before AOP）:                业务代码（after AOP）:
public Result getProduct(Long id) {    @Cacheable  ← 缓存切面
    // 检查权限                       @OperationLog  ← 日志切面
    if (!hasPermission()) ...          @PerformanceMonitor  ← 监控切面
    // 记录日志                        @RequirePermission  ← 权限切面
    log.info("查询商品: {}", id);       public Result getProduct(Long id) {
    // 性能计时                             return productService.getById(id);
    long start = System.currentTimeMillis();  // 只剩核心业务逻辑
    // 核心业务
    Product p = productService.getById(id);
    // 缓存
    cache.put(id, p);
    // 性能计时
    long cost = System.currentTimeMillis() - start;
    log.info("耗时: {}ms", cost);
    return p;
}
```

### 1.2 四种切面模式一览

| 模式 | 注解 | 通知类型 | 核心功能 | 面试频率 |
|------|------|---------|---------|---------|
| **缓存切面** | `@Cacheable` | `@Around` | 方法结果自动缓存/失效 | ★★★★★ |
| **操作日志** | `@OperationLog` | `@Around` | 自动记录操作人/时间/参数 | ★★★★★ |
| **权限控制** | `@RequirePermission` | `@Before` | AND/OR 权限校验 | ★★★★★ |
| **性能监控** | `@PerformanceMonitor` | `@Around` | 慢查询统计/耗时监控 | ★★★★ |

---

## 二、进阶机制

### 2.1 自定义注解 + Spring AOP 的完整流程

```java
// 步骤 1: 定义注解
@Retention(RetentionPolicy.RUNTIME)  // 运行时保留
@Target(ElementType.METHOD)          // 应用于方法
public @interface Cacheable {
    String value() default "";
    String key() default "";
    long expire() default -1;
    TimeUnit timeUnit() default TimeUnit.SECONDS;
}

// 步骤 2: 定义切面
@Aspect
@Component
public class CacheAspect {
    @Around("@annotation(cacheable)")  // 拦截所有带 @Cacheable 的方法
    public Object cache(ProceedingJoinPoint joinPoint, Cacheable cacheable) throws Throwable {
        // 1. 生成缓存 Key
        // 2. 查缓存 → 命中直接返回
        // 3. 未命中 → 执行方法 → 写入缓存
    }
}

// 步骤 3: 在业务方法上使用
@Cacheable(value = "product", key = "#id", expire = 3600)
public Product getProduct(Long id) {
    return productMapper.selectById(id);
}
```

### 2.2 缓存切面 —— CacheAspect 逐行解析

```java
@Around("@annotation(cacheable)")
public Object cache(ProceedingJoinPoint joinPoint, Cacheable cacheable) throws Throwable {
    // 1. 生成缓存 Key
    String cacheKey = generateCacheKey(joinPoint, cacheable);

    // 2. 尝试从缓存获取
    String cacheValue = redisTemplate.opsForValue().get(cacheKey);
    if (cacheValue != null) {
        log.debug("缓存命中：{}", cacheKey);
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Class<?> returnType = signature.getReturnType();
        return objectMapper.readValue(cacheValue, returnType);  // 反序列化
    }

    // 3. 缓存未命中，执行目标方法
    Object result = joinPoint.proceed();

    // 4. 将结果写入缓存（带过期时间）
    if (result != null) {
        String jsonValue = objectMapper.writeValueAsString(result);
        if (cacheable.expire() > 0) {
            redisTemplate.opsForValue().set(cacheKey, jsonValue, cacheable.expire(), cacheable.timeUnit());
        } else {
            redisTemplate.opsForValue().set(cacheKey, jsonValue);
        }
    }
    return result;
}
```

**Key 生成策略：** `cacheKey = 前缀 + 类名 + 方法名 + 参数值`，参数值通过反射获取，确保同参数命中同一缓存。

### 2.3 权限切面 —— AND/OR 逻辑

```java
@Before("@annotation(requirePermission)")
public void checkPermission(JoinPoint joinPoint, RequirePermission requirePermission) {
    String[] permissions = requirePermission.value();
    RequirePermission.Logical logical = requirePermission.logical();

    Set<String> userPermissions = getCurrentUserPermissions();

    boolean hasPermission;
    if (logical == RequirePermission.Logical.AND) {
        // 必须拥有所有权限
        hasPermission = userPermissions.containsAll(Arrays.asList(permissions));
    } else {
        // 拥有任意一个权限即可
        hasPermission = Arrays.stream(permissions)
                .anyMatch(userPermissions::contains);
    }

    if (!hasPermission) {
        throw new PermissionDeniedException("没有访问权限");
    }
}
```

**用法：**

```java
@RequirePermission(value = {"product:read", "order:read"}, logical = Logical.AND)
public void queryReport() { ... }

@RequirePermission("product:delete")
public void deleteProduct() { ... }
```

### 2.4 性能监控切面 —— 慢查询统计

```java
@Around("@annotation(PerformanceMonitor)")
public Object monitor(ProceedingJoinPoint joinPoint) throws Throwable {
    String methodKey = className + "." + methodName;
    long startTime = System.currentTimeMillis();

    try {
        return joinPoint.proceed();
    } finally {
        long cost = System.currentTimeMillis() - startTime;
        // 更新统计信息（ConcurrentHashMap + AtomicLong）
        statisticsMap.computeIfAbsent(methodKey, k -> new MethodStatistics())
            .record(cost);

        // 慢查询告警
        if (cost > slowQueryThreshold) {
            log.warn("慢查询: {} 耗时: {}ms", methodKey, cost);
        }
    }
}
```

---

## 三、项目现场

### 3.1 四种切面的完整代码结构

```
aop/
├── CacheAspect.java        @Around 缓存切面
├── Cacheable.java          自定义 @Cacheable 注解
├── CacheEvict.java         自定义 @CacheEvict 注解
├── CacheTestService.java   缓存测试服务
│
├── OperationLogAspect.java  @Around 操作日志切面
├── OperationLog.java       自定义 @OperationLog 注解
│
├── PermissionAspect.java    @Before 权限切面
├── RequirePermission.java  自定义 @RequirePermission 注解
├── RequireRole.java        自定义 @RequireRole 注解
├── PermissionDeniedException.java  权限异常
├── PermissionTestService.java  权限测试服务
├── SecurityContext.java    安全上下文
│
├── PerformanceMonitorAspect.java  @Around 性能监控切面
├── PerformanceMonitor.java  自定义 @PerformanceMonitor 注解
├── PerformanceTestService.java  性能测试服务
│
├── LogRecord.java          日志记录实体
├── UserContextHolder.java  ThreadLocal 用户上下文
├── TestService.java        综合测试服务
```

### 3.2 配套测试验证

每个切面都有对应的单元测试，验证切面是否生效：

```java
// CacheAspectTest.java
@Test
void testCacheable_ShouldCacheResult() {
    // 第一次调用：缓存未命中，执行方法
    Product result1 = cacheTestService.getProduct(1L);
    // 第二次调用：缓存命中，不执行方法
    Product result2 = cacheTestService.getProduct(1L);
    // 验证：方法只被调用了一次
    assertEquals(1, executionCount.get());
}
```

---

## 四、面试要点

### Q1: AOP 的四种通知类型分别适用什么场景？

**回答思路：** `@Before` 适合前置校验（权限检查），`@AfterReturning` 适合后置处理（日志记录），`@Around` 适合环绕增强（缓存、性能监控、事务），`@AfterThrowing` 适合异常处理。切面中 `@Around` 功能最强大，但必须手动调用 `joinPoint.proceed()`。

### Q2: 项目中自定义注解 + AOP 的完整流程？

**回答思路：** 三步：1) 定义注解（`@Retention(RUNTIME)` + `@Target(METHOD)`）；2) 写切面（`@Aspect` + `@Component`，通知类型绑定注解）；3) 在业务方法上使用注解。Spring 通过动态代理（JDK Proxy 或 CGLIB）在运行时织入切面逻辑。

### Q3: 缓存切面怎么处理缓存穿透？

**回答思路：** 缓存穿透指查询不存在的数据，每次都会穿透缓存查数据库。方案：1) 缓存空值（设置短过期时间）；2) 布隆过滤器。项目中的 `CacheAspect` 在 `result == null` 时直接跳过缓存写入，实际上没有防御穿透——这是改进方向。

### Q4: 权限切面的 AND/OR 逻辑怎么实现的？

**回答思路：** `@RequirePermission` 注解中定义 `Logical` 枚举（AND 或 OR）。AND 模式检查用户权限集合是否包含所有要求的权限（`containsAll`），OR 模式检查是否存在任意一个（`anyMatch`）。这是 Spring Security `@PreAuthorize` 的简化实现。

---

> **下一篇：** [02-COLLECTIONS.md —— 集合框架高阶应用：去重、统计、树构建、聚合、批量处理](./02-COLLECTIONS.md)
>
> 从 AOP 切面到集合框架，看项目如何用 Stream API 和集合操作解决真实业务问题。