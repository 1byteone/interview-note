# 02 · 集合框架高阶应用：去重、统计、树构建、聚合、批量处理

> 集合框架是 Java 面试的高频考点。看 mall-exercise 如何用 Stream API 和集合操作解决电商业务中的五个典型问题。
>
> **对应项目：** `mall-exercise/src/main/java/itcast/cloud/mall/exercise/collection/`

---

## 一、基础概念

### 1.1 五个集合练习

| 练习类 | 解决的问题 | 核心 API | 面试价值 |
|--------|-----------|---------|---------|
| **ProductDeduplicator** | 商品去重合并（相同 ID 合并标签） | `Collectors.toMap` + 合并函数 | ★★★★ |
| **SkuPriceStatistics** | SKU 价格区间分布统计 | `Collectors.groupingBy` + `counting` | ★★★★ |
| **CategoryTreeBuilder** | 三级分类树构建 | 递归 + 分组 | ★★★★ |
| **ProductAttributeAggregator** | 商品属性聚合 | `flatMap` + `groupingBy` | ★★★ |
| **BatchDataProcessor** | 批量数据处理 | 分页 + 并行流 | ★★★★ |

---

## 二、进阶机制

### 2.1 商品去重 —— toMap 合并函数

```java
public List<SkuProduct> mergeAndDeduplicate(List<SkuProduct> skuList) {
    return skuList.stream()
        .collect(Collectors.toMap(
            SkuProduct::getId,              // key: 按 ID 去重
            sku -> new SkuProduct(          // value: 复制新对象
                sku.getId(), sku.getSkuName(), sku.getPrice(),
                new HashSet<>(sku.getTags()) // 复制标签集合
            ),
            (existing, replacement) -> {     // 合并函数：相同 ID 合并标签
                existing.getTags().addAll(replacement.getTags());
                return existing;
            },
            LinkedHashMap::new              // 保持插入顺序
        ))
        .values().stream().collect(Collectors.toList());
}
```

**`toMap` 的四个参数：**

| 参数 | 类型 | 说明 |
|------|------|------|
| keyMapper | 键提取 | `SkuProduct::getId` |
| valueMapper | 值提取 | 复制新对象 |
| mergeFunction | 冲突合并 | 相同 key 时合并标签 |
| mapSupplier | Map 实现 | `LinkedHashMap::new` 保持顺序 |

### 2.2 价格区间统计 —— groupingBy 自定义分组

```java
public Map<String, Long> statisticsPriceRange(List<SkuItem> skuList) {
    return skuList.stream().collect(
        Collectors.groupingBy(
            this::getPriceRange,      // 自定义分组函数
            LinkedHashMap::new,       // 保持顺序
            Collectors.counting()     // 计数
        ));
}

private String getPriceRange(SkuItem sku) {
    for (PriceRange range : PRICE_RANGES) {
        if (sku.getPrice() >= range.getMin() && sku.getPrice() < range.getMax()) {
            return range.getLabel();
        }
    }
    return "未知";
}
```

### 2.3 三级分类树构建

```java
public List<CategoryTree> buildTree(List<Category> allCategories) {
    // 1. 按 parentId 分组
    Map<Long, List<Category>> grouped = allCategories.stream()
        .collect(Collectors.groupingBy(Category::getParentId));

    // 2. 递归构建（从 parentId=0 的根节点开始）
    return buildChildren(grouped, 0L);
}

private List<CategoryTree> buildChildren(Map<Long, List<Category>> grouped, Long parentId) {
    List<Category> children = grouped.getOrDefault(parentId, List.of());
    return children.stream()
        .map(cat -> new CategoryTree(cat.getId(), cat.getName(),
            buildChildren(grouped, cat.getId())))  // 递归
        .collect(Collectors.toList());
}
```

---

## 三、面试要点

### Q1: `Collectors.toMap` 的 mergeFunction 参数有什么用？

**回答思路：** 当多个元素有相同 key 时，mergeFunction 决定如何合并。不传 mergeFunction 时遇到重复 key 会抛出 `IllegalStateException`。项目中用 mergeFunction 实现"相同 ID 的商品合并标签"——取第一个商品为主体，将后续商品的标签合入。

### Q2: `groupingBy` 除了分组计数还能做什么？

**回答思路：** 下游收集器（downstream collector）可以做多种聚合：`counting()` 计数、`summingDouble()` 求和、`averagingDouble()` 平均、`mapping()` 转换、`reducing()` 归约。项目中用 `counting()` 做价格区间分布统计，用 `mapping()` 做属性聚合。

### Q3: 递归构建分类树有什么性能问题？

**回答思路：** 递归在层级深时可能导致栈溢出。项目中分类只有三级，递归深度可控。如果是无限级分类，应该改用迭代（栈）或数据库递归查询。另一种优化是**一次性查询全部 + 内存分组**，避免 N+1 查询。

---

> **下一篇：** [03-REFLECTION.md —— 反射实战：动态 SQL 生成、验证框架、数据权限、Bean 转换](./03-REFLECTION.md)
>
> 反射是框架的基石。看项目如何用反射实现 MyBatis-Plus 风格 SQL 构建、通用验证框架、数据权限代理和 Bean 转换器。