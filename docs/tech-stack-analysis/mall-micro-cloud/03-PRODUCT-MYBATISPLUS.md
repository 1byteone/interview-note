# 03 · 商品服务与 MyBatis-Plus：SPU/SKU 设计、分类体系、品牌管理

> 电商业务的核心——商品服务。看 SPU/SKU 的数据库设计、MyBatis-Plus 多表关联、分类品牌体系，以及 Thymeleaf 页面静态化。
>
> **对应项目：** `mall-services/mall-product-service`

---

## 一、基础概念

### 1.1 SPU 与 SKU

电商中最核心的两个概念：

| 概念 | 全称 | 含义 | 举例 |
|------|------|------|------|
| **SPU** | Standard Product Unit | 标准化产品单元 | "华为Pura 70" |
| **SKU** | Stock Keeping Unit | 库存量单位 | "华为Pura 70 512GB 星芒黑" |

**关系：** 一个 SPU 包含多个 SKU。SPU 是"商品"，SKU 是"可购买的具体版本"。

```
SPU: "华为Pura 70" (商品详情页)
├── SKU: 512GB 星芒黑  ¥6999  (用户实际购买)
├── SKU: 512GB 雪域白  ¥6999
├── SKU: 1TB 星芒黑    ¥7999
└── SKU: 1TB 雪域白    ¥7999
```

### 1.2 项目中的商品数据库设计

```sql
-- 商品分类（三级分类）
category: id, name, parent_id, level, sort, icon

-- 品牌
brand: id, name, logo, first_char, status

-- 分类品牌关联
category_brand: category_id, brand_id

-- SPU 商品
spu_info: id, spu_name, category_id, brand_id, description, ...

-- SKU 库存单元
sku_info: id, spu_id, price, sku_name, sku_attribute, stock, 
          brand_name, category_name, sku_default_img, deleted

-- 销售属性
sale_attribute: id, name, category_id
sale_attribute_value: id, attribute_id, name, spu_id
```

---

## 二、进阶机制

### 2.1 MyBatis-Plus 配置

```java
@Configuration
@MapperScan("itcast.cloud.mall.**.mapper")  // 扫描所有 mapper
public class MyBatisPlusConfig {
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // 分页插件（支持多数据库方言）
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }
}
```

### 2.2 商品服务的 Controller 层

```java
@RestController
@RequestMapping("/api/product")
public class ProductController {
    // 分页查询商品
    @GetMapping("/page")
    public Result<Page<ProductDTO>> page(ProductQueryDTO queryDTO) { ... }

    // 商品详情
    @GetMapping("/detail/{id}")
    public Result<ProductDetailDTO> detail(@PathVariable Long id) { ... }

    // 按分类查询
    @GetMapping("/category/{categoryId}")
    public Result<List<ProductDTO>> listByCategory(@PathVariable Long categoryId) { ... }

    // 扣减库存（Feign 调用）
    @PostMapping("/deductStock")
    public Result<Void> deductStock(@RequestBody DeductStockDTO dto) { ... }
}
```

### 2.3 分类体系：三级分类树

```java
// 分类实体
@Data
public class Category {
    private Long id;
    private String name;
    private Long parentId;   // 父分类 ID
    private Integer level;   // 层级: 1/2/3
    private Integer sort;    // 排序号
    private String icon;     // 图标
}
```

**三级分类树构建：**

```java
// mall-exercise 中的 CategoryTreeBuilder
public class CategoryTreeBuilder {
    public List<CategoryTree> buildTree(List<Category> allCategories) {
        // 1. 找出所有一级分类 (level=1)
        // 2. 遍历每个一级分类，找出其下级 (parentId = 一级id)
        // 3. 遍历每个二级分类，找出其下级 (parentId = 二级id)
        // 4. 组装成树形结构返回
    }
}
```

---

## 三、面试要点

### Q1: SPU 和 SKU 的区别是什么？为什么这样设计？

**回答思路：** SPU 是"商品"，SKU 是"可购买的库存单元"。SPU 对应商品详情页（共享描述、图片、参数），SKU 对应具体规格版本（价格、库存、颜色）。这样设计减少数据冗余——SPU 的公共信息只存一份，SKU 只存差异字段。

### Q2: MyBatis-Plus 分页插件怎么用？

**回答思路：** 配置 `MybatisPlusInterceptor` 注册 `PaginationInnerInterceptor`，Controller 接收 `Page` 参数，Service 调用 `mapper.selectPage(page, wrapper)`，返回 `Page<T>` 包含 records、total、pages 等信息。

### Q3: 商品分类为什么要设计成三级？

**回答思路：** 平衡用户体验和数据复杂度。一级类目（如"手机数码"）做导航，二级类目（如"手机"）做筛选，三级类目（如"5G手机"）做精确匹配。三级以内是用户能接受的点击深度，超过三级交互复杂度陡增。

---

> **下一篇：** [04-ORDER-SEATA.md —— 订单服务与 Seata 分布式事务](./04-ORDER-SEATA.md)
>
> 下单扣库存是分布式事务的典型场景。看 Seata AT 模式如何保证订单、库存、购物车的数据一致性。