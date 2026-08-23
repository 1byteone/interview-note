# 05 · MyBatis-Plus 高级查询：多表关联、批量操作、统计聚合、链式查询

> MyBatis-Plus 是 Java 后端最常用的 ORM 框架。看 mall-exercise 如何用 Wrapper、Lambda、批量操作、统计聚合解决真实业务场景。
>
> **对应项目：** `mall-exercise/src/main/java/itcast/cloud/mall/exercise/mp/`

---

## 一、基础概念

### 1.1 五个高级查询练习

| 练习类 | 解决的问题 | 核心 API | 面试价值 |
|--------|-----------|---------|---------|
| **ProductQueryService** | 多条件动态查询 | `LambdaQueryWrapper`, `Page` | ★★★★ |
| **ProductChainQueryService** | 多表关联查询 | `@TableName`, 自定义 Mapper | ★★★★ |
| **ProductBatchService** | 批量插入/更新 | `saveBatch`, `updateBatchById` | ★★★★ |
| **ProductStatisticsService** | 分组统计聚合 | `groupBy`, `selectCount`, `CustomWrapper` | ★★★★ |
| **CategoryQueryService** | 分类树查询 | `LambdaQueryWrapper`, 递归 | ★★★★ |

---

## 二、进阶机制

### 2.1 多条件动态查询 —— LambdaQueryWrapper

```java
@Service
public class ProductQueryService {
    public Page<Product> queryProducts(ProductQueryDTO dto) {
        LambdaQueryWrapper<Product> wrapper = Wrappers.lambdaQuery();

        // 动态拼接条件：只有传入的值才拼接
        if (dto.getCategoryId() != null) {
            wrapper.eq(Product::getCategoryId, dto.getCategoryId());
        }
        if (dto.getBrandId() != null) {
            wrapper.eq(Product::getBrandId, dto.getBrandId());
        }
        if (dto.getMinPrice() != null) {
            wrapper.ge(Product::getPrice, dto.getMinPrice());
        }
        if (dto.getMaxPrice() != null) {
            wrapper.le(Product::getPrice, dto.getMaxPrice());
        }
        if (dto.getKeyword() != null) {
            wrapper.like(Product::getSkuName, dto.getKeyword());
        }

        // 排序
        wrapper.orderByDesc(Product::getCreateTime);

        // 分页
        Page<Product> page = new Page<>(dto.getPageNum(), dto.getPageSize());
        return productMapper.selectPage(page, wrapper);
    }
}
```

### 2.2 批量操作 —— saveBatch

```java
@Service
public class ProductBatchService {
    // 批量插入（自动分批，默认 1000 条一批）
    public void batchInsert(List<Product> products) {
        productService.saveBatch(products);  // MyBatis-Plus IService 内置
    }

    // 批量更新
    public void batchUpdate(List<Product> products) {
        productService.updateBatchById(products);
    }

    // 手动分批（适合大数据量）
    public void batchInsertManual(List<Product> products, int batchSize) {
        List<List<Product>> batches = Lists.partition(products, batchSize);
        for (List<Product> batch : batches) {
            productMapper.insertBatch(batch); // 自定义 Mapper 批量插入
        }
    }
}
```

### 2.3 统计聚合 —— groupBy + selectCount

```java
@Service
public class ProductStatisticsService {

    // 按品牌统计商品数量
    public List<Map<String, Object>> countByBrand() {
        QueryWrapper<Product> wrapper = new QueryWrapper<>();
        wrapper.select("brand_id, COUNT(*) as count, AVG(price) as avg_price")
            .groupBy("brand_id")
            .orderByDesc("count");
        return productMapper.selectMaps(wrapper);
    }

    // 按分类统计销售额
    public List<Map<String, Object>> sumSalesByCategory() {
        QueryWrapper<Product> wrapper = new QueryWrapper<>();
        wrapper.select("category_id, SUM(price * stock) as total_value")
            .groupBy("category_id")
            .having("total_value > 10000");
        return productMapper.selectMaps(wrapper);
    }
}
```

---

## 三、面试要点

### Q1: LambdaQueryWrapper 和普通 QueryWrapper 的区别？

**回答思路：** LambdaQueryWrapper 使用 Lambda 表达式引用实体类的 getter 方法（`Product::getPrice`），编译时类型安全——如果字段名拼错或重构改名，编译器会报错。普通 QueryWrapper 使用字符串（`"price"`），运行时才报错。Lambda 方式更推荐。

### Q2: MyBatis-Plus 的 saveBatch 是怎么实现批量插入的？

**回答思路：** `saveBatch` 内部使用 `SqlSession` 的 `batch` 模式，将多条 INSERT 语句合并为一次批量提交，默认 1000 条一批。相比逐条插入，减少了网络往返和 SQL 解析开销，性能提升 10-100 倍。但注意：批量操作无法利用数据库自增 ID 回填（需要 `@TableId(type = IdType.ASSIGN_ID)`）。

### Q3: selectMaps 和 selectList 返回结果有什么区别？

**回答思路：** `selectList` 返回实体对象列表，需要字段映射到实体属性。`selectMaps` 返回 `List<Map<String, Object>>`，适合**统计查询**——因为聚合字段（`COUNT(*)`、`AVG(price)`）没有对应的实体属性，用 Map 接收更灵活。

---

> **下一篇：** [06-TESTING.md —— 单元测试与代码质量：18 个测试类的模式总结](./06-TESTING.md)
>
> 好代码必须经过测试验证。看 18 个测试类的设计模式、断言技巧和覆盖策略。