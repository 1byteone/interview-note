# 06 · 单元测试与代码质量：18 个测试类的模式总结

> mall-exercise 的每个练习都有配套的单元测试。看 18 个测试类的设计模式、断言技巧和覆盖策略。
>
> **对应项目：** `mall-exercise/src/test/java/`

---

## 一、基础概念

### 1.1 测试覆盖矩阵

| 模块 | 测试类数 | 核心验证点 |
|------|---------|-----------|
| AOP 切面 | 4 个 | 切面是否生效、缓存命中、权限拦截、性能记录 |
| 集合框架 | 5 个 | 去重结果、统计分布、树结构、聚合结果、批量分页 |
| 反射 | 4 个 | SQL 生成正确性、验证结果、代理拦截、Bean 转换 |
| Redis 缓存 | 1 个 | 缓存命中、分布式锁、缓存穿透防御 |
| MyBatis-Plus | 5 个 | 多条件查询、批量操作、统计结果、分类树 |

---

## 二、进阶机制

### 2.1 测试模式总结

**模式 1：验证切面拦截**

```java
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

**模式 2：验证异常抛出**

```java
@Test
void testPermissionAspect_ShouldDenyWhenNoPermission() {
    assertThrows(PermissionDeniedException.class, () -> {
        permissionTestService.deleteProduct();  // 无权限
    });
}
```

**模式 3：验证集合结果**

```java
@Test
void testDeduplicate_ShouldMergeSameId() {
    List<SkuProduct> merged = deduplicator.mergeAndDeduplicate(input);
    assertEquals(3, merged.size());  // 从 5 条合并为 3 条
    assertTrue(merged.get(0).getTags().contains("华为"));  // 标签合并
}
```

**模式 4：验证反射生成的 SQL**

```java
@Test
void testBuildSelectSql_ShouldGenerateCorrectSql() {
    String sql = DynamicSqlBuilder.buildSelectSql(Product.class, 1L);
    assertEquals("SELECT * FROM product WHERE id = ?", sql);
}
```

---

## 三、面试要点

### Q1: 单元测试应该覆盖哪些边界条件？

**回答思路：** 正常路径（输入合法值，验证正确结果）、边界值（空列表、null 输入、超大值）、异常路径（权限不足、缓存未命中、参数非法）。项目中的测试覆盖了这三种情况。

### Q2: 如何测试 AOP 切面是否生效？

**回答思路：** 通过验证切面的**副作用**——缓存切面验证方法执行次数（执行一次 vs 两次），权限切面验证异常是否抛出，性能监控切面验证统计信息是否记录。这些都在 Spring 容器中运行，确保切面被正确织入。

---

> **下一篇：** [07-ARCHITECTURE.md —— Java 核心技能复盘：与四个项目串联](./07-ARCHITECTURE.md)
>
> 将 mall-exercise 的 Java 核心技能与前面三个项目串联，形成完整的 Java 后端 + AI 面试体系。