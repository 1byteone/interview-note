# AI 商城 MySQL 集成 — 订单表设计 · 商品查询优化 · 事务处理

> 🎯 实战项目 · 关联 mall-micro-cloud

---

## 〇、深度剖析参考

| 主题 | 本 learn 文档 | docs/tech-stack-analysis 深度剖析 |
|------|--------------|--------------------------------|
| SPU/SKU 商品表设计 | 本文 | [03-PRODUCT-MYBATISPLUS.md](../../docs/tech-stack-analysis/mall-micro-cloud/03-PRODUCT-MYBATISPLUS.md) — MyBatis-Plus 多表关联 |
| Seata 分布式事务 | — | [04-ORDER-SEATA.md](../../docs/tech-stack-analysis/mall-micro-cloud/04-ORDER-SEATA.md) — 下单+扣库存事务 |
| MyBatis-Plus 高级查询 | — | [05-MYBATISPLUS-ADV.md](../../docs/tech-stack-analysis/mall-exercise/05-MYBATISPLUS-ADV.md) — Wrapper/批量/统计 |
| ES 数据同步到向量库 | — | [09-DATA-SYNC.md](../../docs/tech-stack-analysis/mall-ai-search/09-DATA-SYNC.md) — MySQL→Embedding→RedisVL |

---

## 一、AI 商城中 MySQL 的角色

mall-micro-cloud 是一个基于微服务架构的 AI 商城系统，MySQL 作为核心的关系型数据库，承载着所有业务数据。

### 1.1 业务数据全景

```
┌─────────────────────────────────────────────────────────────────────┐
│                        mall-micro-cloud 数据存储                     │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐            │
│  │ 用户中心  │  │ 商品中心  │  │ 订单中心  │  │ 支付中心  │          │
│  │ 用户信息  │  │ 商品信息  │  │ 订单信息  │  │ 支付记录  │          │
│  │ 收货地址  │  │ 商品分类  │  │ 订单明细  │  │ 退款记录  │          │
│  │ 用户等级  │  │ SKU 信息  │  │ 物流信息  │  │ 对账文件  │          │
│  └──────────┘  └──────────┘  └──────────┘  └──────────┘            │
│                                                                     │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐            │
│  │ 购物车   │  │ 库存中心  │  │ 营销中心  │  │ 评价中心  │          │
│  │ 购物车项  │  │ 库存记录  │  │ 优惠券   │  │ 商品评价  │          │
│  │          │  │ 库存流水  │  │ 活动信息  │  │ 评价图片  │          │
│  └──────────┘  └──────────┘  └──────────┘  └──────────┘            │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 二、订单表设计（含分库分表方案）

### 2.1 订单核心表结构

```sql
-- 订单主表：order_info
CREATE TABLE `order_info` (
    `id` BIGINT NOT NULL COMMENT '订单ID（雪花算法）',
    `order_no` VARCHAR(32) NOT NULL COMMENT '订单号（业务唯一）',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `total_amount` DECIMAL(10,2) NOT NULL COMMENT '订单总金额',
    `discount_amount` DECIMAL(10,2) DEFAULT '0.00' COMMENT '优惠金额',
    `pay_amount` DECIMAL(10,2) NOT NULL COMMENT '实付金额',
    `payment_method` TINYINT DEFAULT NULL COMMENT '支付方式 1=微信 2=支付宝',
    `status` TINYINT NOT NULL DEFAULT '0' COMMENT '订单状态 -1=已取消 0=待支付 1=已支付 2=已发货 3=已完成 4=售后中',
    `consignee` VARCHAR(100) NOT NULL COMMENT '收货人',
    `phone` VARCHAR(20) NOT NULL COMMENT '联系电话',
    `address` VARCHAR(500) NOT NULL COMMENT '收货地址',
    `remark` VARCHAR(500) DEFAULT '' COMMENT '订单备注',
    `payment_time` DATETIME DEFAULT NULL COMMENT '支付时间',
    `delivery_time` DATETIME DEFAULT NULL COMMENT '发货时间',
    `receive_time` DATETIME DEFAULT NULL COMMENT '确认收货时间',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_order_no` (`order_no`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_status` (`status`),
    KEY `idx_create_time` (`create_time`),
    KEY `idx_user_status` (`user_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单表';
```

### 2.2 订单明细表

```sql
CREATE TABLE `order_item` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `order_id` BIGINT NOT NULL COMMENT '订单ID',
    `product_id` BIGINT NOT NULL COMMENT '商品ID',
    `product_name` VARCHAR(200) NOT NULL COMMENT '商品名称（下单时快照）',
    `product_image` VARCHAR(500) DEFAULT '' COMMENT '商品图片',
    `price` DECIMAL(10,2) NOT NULL COMMENT '单价',
    `quantity` INT NOT NULL COMMENT '数量',
    `subtotal` DECIMAL(10,2) NOT NULL COMMENT '小计',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_order_id` (`order_id`),
    KEY `idx_product_id` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单明细表';
```

### 2.3 设计要点

1. **订单号用雪花算法**：全局唯一，趋势递增，支持分布式
2. **商品名和数据快照**：下单时复制商品信息，避免商品信息变更影响历史订单
3. **金额用 DECIMAL(10,2)**：精确计算，避免 FLOAT/DOUBLE 精度问题
4. **状态字段用 TINYINT**：支持扩展，比 VARCHAR 更高效
5. **索引设计**：用户查询、状态查询、时间范围查询都有对应索引

### 2.4 分库分表方案

```yaml
# ShardingSphere 配置
rules:
  sharding:
    tables:
      order_info:
        actual-data-nodes: ds$->{0..3}.order_info_$->{0..15}
        database-strategy:
          standard:
            sharding-column: user_id
            sharding-algorithm-name: db_mod
        table-strategy:
          standard:
            sharding-column: user_id
            sharding-algorithm-name: table_mod
      order_item:
        actual-data-nodes: ds$->{0..3}.order_item_$->{0..15}
        # 绑定表：保证 order_info 和 order_item 在同一分片
        database-strategy:
          standard:
            sharding-column: order_id
            sharding-algorithm-name: db_mod
        table-strategy:
          standard:
            sharding-column: order_id
            sharding-algorithm-name: table_mod
    binding-tables:
      - order_info, order_item
```

---

## 三、商品查询优化

### 3.1 商品多维度查询

```sql
-- 商品列表查询（含分类、价格区间、排序）
SELECT p.id, p.name, p.price, p.main_image, p.sales
FROM product p
WHERE p.status = 1                    -- 上架商品
  AND p.category_id = 100             -- 指定分类
  AND p.price BETWEEN 100 AND 1000    -- 价格区间
  AND p.name LIKE '%手机%'            -- 名称搜索（配合搜索引擎更佳）
ORDER BY p.sales DESC                 -- 按销量排序
LIMIT 20 OFFSET 0;
```

### 3.2 索引设计

```sql
-- 商品查询的核心索引
ALTER TABLE product ADD INDEX idx_category_status (category_id, status);
ALTER TABLE product ADD INDEX idx_status_sales (status, sales DESC);
ALTER TABLE product ADD INDEX idx_status_price (status, price);

-- 搜索引擎替代方案
-- 如果商品名称需要全文搜索，建议使用 Elasticsearch
-- 创建全文索引是备选方案
ALTER TABLE product ADD FULLTEXT INDEX ft_name (name);
```

### 3.3 Redis 缓存加速

```java
@Service
public class ProductService {
    @Autowired
    private StringRedisTemplate redisTemplate;

    public ProductVO getProductById(Long id) {
        // 1. 查缓存
        String cacheKey = "product:" + id;
        String json = redisTemplate.opsForValue().get(cacheKey);
        if (json != null) {
            return JSON.parseObject(json, ProductVO.class);
        }

        // 2. 缓存未命中，查数据库
        Product product = productMapper.selectById(id);
        ProductVO vo = convertToVO(product);

        // 3. 写入缓存（设置过期时间 30 分钟，防缓存雪崩加随机值）
        redisTemplate.opsForValue().set(cacheKey, JSON.toJSONString(vo),
            1800 + new Random().nextInt(600), TimeUnit.SECONDS);
        return vo;
    }
}
```

---

## 四、订单事务处理

### 4.1 下单事务流程

```java
@Service
@Transactional(rollbackFor = Exception.class)
public class OrderService {
    public Order createOrder(OrderCreateRequest request) {
        // 1. 校验库存
        Product product = productMapper.selectById(request.getProductId());
        if (product.getStock() < request.getQuantity()) {
            throw new BusinessException("库存不足");
        }

        // 2. 扣减库存（乐观锁）
        int affected = productMapper.deductStock(request.getProductId(),
            request.getQuantity(), product.getVersion());
        if (affected == 0) {
            throw new BusinessException("库存扣减失败，请重试");
        }

        // 3. 创建订单
        Order order = new Order();
        order.setOrderNo(generateOrderNo());
        order.setUserId(request.getUserId());
        order.setTotalAmount(product.getPrice().multiply(
            BigDecimal.valueOf(request.getQuantity())));
        order.setStatus(0); // 待支付
        orderMapper.insert(order);

        // 4. 创建订单明细
        OrderItem item = new OrderItem();
        item.setOrderId(order.getId());
        item.setProductId(product.getId());
        item.setPrice(product.getPrice());
        item.setQuantity(request.getQuantity());
        orderItemMapper.insert(item);

        // 5. 清空购物车
        cartMapper.deleteByUserIdAndProductId(
            request.getUserId(), request.getProductId());

        return order;
    }
}
```

### 4.2 库存扣减的 SQL

```sql
-- 乐观锁扣库存（CAS）
UPDATE product
SET stock = stock - #{quantity},
    version = version + 1
WHERE id = #{productId}
  AND stock >= #{quantity}
  AND version = #{version};
```

### 4.3 分布式事务

在微服务架构中，下单可能涉及多个服务：

```java
@GlobalTransactional
public Order createOrderDistributed(OrderCreateRequest request) {
    // 1. 扣库存（商品服务）
    inventoryClient.deduct(request.getProductId(), request.getQuantity());

    // 2. 创建订单（订单服务）
    Order order = orderClient.create(request);

    // 3. 扣余额（用户服务）
    accountClient.deduct(request.getUserId(), order.getPayAmount());

    return order;
}
```

---

## 五、SQL 监控与告警

### 5.1 慢查询监控

```sql
-- 开启慢查询日志
SET GLOBAL slow_query_log = ON;
SET GLOBAL long_query_time = 0.5;  -- 500ms

-- 创建监控表
CREATE TABLE `slow_query_log` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `query_time` FLOAT NOT NULL,
    `sql_text` TEXT NOT NULL,
    `database_name` VARCHAR(64),
    `rows_examined` INT,
    `timestamp` DATETIME DEFAULT CURRENT_TIMESTAMP
);
```

### 5.2 连接池监控

```yaml
# HikariCP 配置
spring:
  datasource:
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      idle-timeout: 300000
      max-lifetime: 1200000
      connection-timeout: 30000
      leak-detection-threshold: 60000  # 连接泄露检测
```

---

## 总结

- mall-micro-cloud 中 MySQL 承载所有核心业务数据
- 订单表设计考虑分库分表、数据快照、索引优化
- 商品查询通过合理索引和 Redis 缓存加速
- 事务处理使用 @Transactional 和乐观锁控制并发
- 监控慢查询和连接池，保障数据库稳定运行

> 下一步：[mini-blog/README.md](./mini-blog/README.md) — 博客系统数据库设计实战