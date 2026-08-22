# 05 · 购物车服务与 MongoDB 持久化设计

> 购物车数据需要高写入、低延迟、灵活扩展。项目使用 MongoDB 而非 Redis 做购物车持久化，看 NoSQL 选型在购物车场景中的实战考量。
>
> **对应项目：** `mall-services/mall-cart-service`

---

## 一、基础概念

### 1.1 购物车数据的特点

| 特征 | 说明 | 技术选型影响 |
|------|------|-------------|
| **高写入** | 用户频繁增删改商品数量 | 需要高性能写入 |
| **低延迟** | 每次页面操作都要读/写 | 需要内存级速度 |
| **结构灵活** | 不同用户购物车数量不同 | 不需要固定 Schema |
| **数据重要** | 不能丢失 | 需要持久化 |
| **用户关联** | 按 userId 查询 | 需要索引 |

### 1.2 MongoDB 还是 Redis？

| 对比项 | MongoDB | Redis |
|--------|---------|-------|
| 存储模式 | 文档型数据库 | 内存缓存 |
| 持久化 | 默认持久化到磁盘 | 需配置 RDB/AOF |
| 查询能力 | 支持条件查询、聚合、分页 | 主要靠 key 查询 |
| 数据结构 | BSON 文档（类似 JSON） | String/Hash/List/ZSet |
| 本项目中 | 购物车数据存储 | 缓存/分布式锁 |
| 数据重要性 | 购物车数据不能丢 | 可接受缓存丢失 |

**项目选择 MongoDB 的原因：** 购物车数据需要持久化（不能丢）、需要按 userId 查询、需要分页和条件过滤。MongoDB 的文档模型天然适合购物车这种"每个用户 N 条记录"的数据结构。

---

## 二、进阶机制

### 2.1 MongoDB 实体设计

```java
@Data
@Document(collection = "cart")  // MongoDB 集合名
public class Cart {
    @Id
    private String id;              // 文档主键 = userId + "-" + skuId

    @Field("user_id")
    private String userId;          // 用户 ID

    @Field("sku_name")
    private String skuName;         // 商品名称

    private Double price;           // 价格
    private String image;           // 图片
    private String skuId;           // 商品 SKU ID
    private Integer quantity;       // 数量

    @Field("create_time")
    private LocalDateTime createTime; // 创建时间
}
```

**设计要点：**

| 设计 | 说明 |
|------|------|
| `id = userId + "-" + skuId` | 复合主键，防重复。同一用户同一商品只存一条，更新数量 |
| `@Document(collection = "cart")` | 集合名，相当于 MySQL 的表名 |
| `@Field("user_id")` | 字段映射，Java 驼峰 → MongoDB 下划线 |

### 2.2 添加购物车 —— 幂等设计

```java
@Override
public void addToCart(CartCreateDTO cartCreateDTO) {
    Cart cart = new Cart();
    cart.setId(cartCreateDTO.getUserId() + "-" + cartCreateDTO.getSkuId());
    cart.setUserId(cartCreateDTO.getUserId());
    cart.setSkuId(cartCreateDTO.getSkuId());
    cart.setSkuName(cartCreateDTO.getSkuName());
    cart.setPrice(cartCreateDTO.getPrice());
    cart.setImage(cartCreateDTO.getImage());
    cart.setCreateTime(LocalDateTime.now());
    cart.setQuantity(cartCreateDTO.getQuantity() != null ? cartCreateDTO.getQuantity() : 1);

    // 查询购物车中是否存在该用户商品，如果存在，则更新数量
    Cart old = mongoTemplate.findById(cart.getId(), Cart.class);
    if (old != null) {
        int oldQty = old.getQuantity() == null ? 0 : old.getQuantity();
        cart.setQuantity(oldQty + cart.getQuantity());
    }

    mongoTemplate.save(cart);  // save = insert or update
}
```

**幂等逻辑：** 同一用户重复添加同一商品 → 查询已存在 → 数量累加 → `save` 覆盖写入。不会产生重复记录。

### 2.3 购物车列表 —— MongoDB 分页查询

```java
@Override
public Page<CartDTO> page(CartQueryDTO cartQueryDTO) {
    Criteria criteria = new Criteria();

    // 按用户筛选
    if (cartQueryDTO.getUserId() != null) {
        criteria.and("user_id").is(cartQueryDTO.getUserId());
    }
    // 按商品名模糊搜索
    if (cartQueryDTO.getSkuName() != null) {
        criteria.and("sku_name").regex(cartQueryDTO.getSkuName());
    }
    // 价格范围过滤
    if (cartQueryDTO.getMinPrice() != null) {
        criteria.and("price").gt(cartQueryDTO.getMinPrice());
    }
    if (cartQueryDTO.getMaxPrice() != null) {
        criteria.and("price").lt(cartQueryDTO.getMaxPrice());
    }

    // 总数查询
    long total = mongoTemplate.count(Query.query(criteria), Cart.class);

    // 分页查询
    Query query = Query.query(criteria)
            .skip((long) (cartQueryDTO.getPageNum() - 1) * cartQueryDTO.getPageSize())
            .limit(cartQueryDTO.getPageSize())
            .with(Sort.by(Sort.Direction.DESC, "createTime"));

    List<Cart> carts = mongoTemplate.find(query, Cart.class);

    // 组装分页结果
    Page<CartDTO> pageResult = new Page<>(cartQueryDTO.getPageNum(), cartQueryDTO.getPageSize(), total);
    // 注意：使用 MyBatis-Plus 的 Page 对象包装 MongoDB 的查询结果
    // 这种"混合使用"方式需要关注字段映射
    pageResult.setRecords(JsonUtils.toList(JsonUtils.toJson(carts), CartDTO.class));
    return pageResult;
}
```

---

## 三、面试要点

### Q1: 购物车数据为什么用 MongoDB 而不是 Redis？

**回答思路：** 购物车数据需要持久化保证（不能丢），Redis 本质是缓存，持久化需要额外配置且可靠性不如 MongoDB。MongoDB 的文档模型天然适合存储"每个用户 N 条记录"的非结构化数据，且支持按 userId 查询、条件过滤、分页，这些 Redis 做起来比较麻烦。Redis 更适合做纯缓存，MongoDB 更适合做需要持久化的文档数据库。

### Q2: 购物车 ID 设计为 userId + skuId 有什么好处？

**回答思路：** 实现幂等性——同一用户添加同一商品，自动覆盖写入而非新增。避免数据冗余，也简化了"数量累加"的逻辑。查询时也方便：按 userId 前缀查询即可获取用户全部购物车。

### Q3: 加购时如果 MongoDB 查询超时，怎么兜底？

**回答思路：** 可以在 MongoDB 前加一层 Redis 缓存（先查缓存，缓存未命中再查 MongoDB），或者引入熔断降级机制，MongoDB 不可用时改为 MySQL 存储。当前项目中没有实现兜底，这是改进方向。

---

> **下一篇：** [06-SECKILL-HIGHCONCUR.md —— 秒杀服务与高并发：Redisson 分布式锁 + 布隆过滤器 + 库存扣减](./06-SECKILL-HIGHCONCUR.md)
>
> 秒杀是电商系统最大的高并发挑战。看 Redisson 分布式锁、布隆过滤器和异步消息如何实现秒杀抗压。