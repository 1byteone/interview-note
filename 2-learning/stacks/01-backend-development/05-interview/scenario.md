# 场景题 — 高并发设计、秒杀系统、短链设计

> 场景题考察的是"遇到真实问题怎么思考"，面试官期待的不是标准答案，而是**结构化的分析过程**：先明确边界 → 再分析瓶颈 → 给出方案 → 说明取舍。

## 通用答题框架

```
1. 明确问题边界（QPS 多少？数据量多大？可靠性要求？）
2. 拆解请求链路（哪一步是瓶颈？）
3. 分层解决（网关 → 缓存 → 队列 → 数据库）
4. 权衡取舍（一致性 vs 可用性？）并说明
5. 给出可落地细节（不只是概念）
```

## 场景一：高并发设计

### 题目

设计一个支持 10 万 QPS 的商品详情页系统。

### 回答框架

**1. 明确边界**：10 万 QPS，热点商品集中，读多写少（读：写约为 100:1）。

**2. 链路拆解与瓶颈**：
- 网关 → 应用 → 数据库，数据库是必然瓶颈（单库单表撑不过几千 QPS）
- 热点数据集中刷库是最大风险（缓存击穿）

**3. 分层方案**：

| 层 | 方案 | 说明 |
|----|------|------|
| CDN | 静态资源（图片、CSS、JS）上 CDN | 承担 70% 流量 |
| 浏览器缓存 | Cache-Control / ETag | 减少回源 |
| Nginx 层 | 静态页面缓存（open_file_cache） | 页面级缓存，命中即返回 |
| 本地缓存 | Caffeine（单机内存缓存） | 扛热点，毫秒级 |
| 分布式缓存 | Redis（先查 Redis，未命中再查 DB） | 缓存商品详情 JSON |
| 数据库 | MySQL 主从 + 读写分离 | 读走从库，写走主库 |
| 限流降级 | Sentinel 热点参数限流 | 热点 QPS 过高时降级为静态页 |

**4. 缓存击穿 / 穿透 / 雪崩**：

```java
// 击穿：热点 key 失效瞬间大量请求打到 DB
// 方案：互斥锁重建缓存
public ProductDTO getProduct(Long id) {
    String key = "product:" + id;
    ProductDTO product = redisTemplate.opsForValue().get(key);
    if (product != null) return product;

    // 互斥锁：只有一个请求去 DB 查询并重建缓存
    if (redisTemplate.opsForValue().setIfAbsent("lock:" + key, "1", 30, TimeUnit.SECONDS)) {
        try {
            product = productMapper.selectById(id);
            redisTemplate.opsForValue().set(key, product, 1, TimeUnit.HOURS);
        } finally {
            redisTemplate.delete("lock:" + key);
        }
    } else {
        // 获取锁失败，短暂 sleep 后重试读缓存
        try { Thread.sleep(50); } catch (InterruptedException ignored) {}
        product = redisTemplate.opsForValue().get(key);
    }
    return product;
}
```

```java
// 穿透：查不存在的 id 也会打到 DB
// 方案：缓存空值（设置较短过期时间）或布隆过滤器

// 雪崩：大量 key 同时失效，DB 被打爆
// 方案：过期时间加随机值（1h + random(0, 5min)），避免同时失效
```

**5. 权衡取舍**：缓存与 DB 最终一致（先更新 DB 再删缓存）；热点数据允许秒级陈旧。

## 场景二：秒杀系统设计

### 题目

设计一个秒杀系统：10 万用户抢 1000 件商品，要求不能超卖，系统不能崩。

### 回答框架

**1. 明确边界**：QPS 峰值 10 万+，并发集中在开卖瞬间（读放大：1 秒 10 万人刷新）。

**2. 核心矛盾**：**超卖**（1000 件不能卖 1001 件）与**削峰**（瞬间流量必须平滑）。

**3. 分层方案**：

```
用户请求
    │
    ▼
① Nginx + CDN：静态促销页上 CDN，扛静态流量
    │
    ▼
② 网关层：Sentinel 限流（令牌桶），超阈值直接拒绝
    │
    ▼
③ 前端限流+倒计时：按钮置灰，提前请求一律拦截
    │
    ▼
④ 应用层：Redis 预扣库存（原子操作）
    │    Lua 脚本：DECR 成功才允许继续
    ▼
⑤ 生成秒杀令牌（Token），带 Token 才允许下单
    │
    ▼
⑥ 下单请求入 RocketMQ（削峰），异步落库
    │
    ▼
⑦ DB 层：最终扣减，WHERE stock > 0 兜底防超卖
```

**4. 防超卖的三个关键点**：

```sql
-- ① 数据库原子扣减（兜底）
UPDATE t_inventory
SET stock = stock - #{num}
WHERE product_id = #{pid} AND stock >= #{num};
-- 影响行数 = 1 才成功，否则超卖
```

```java
// ② Redis 预扣库存（Lua 原子脚本）
String script = "local stock = redis.call('GET', KEYS[1]) " +
                "if tonumber(stock) <= 0 then return -1 end " +
                "redis.call('DECR', KEYS[1]) " +
                "return 1";
Long result = redisTemplate.execute(
    new DefaultRedisScript<>(script, Long.class),
    Arrays.asList("seckill:stock:" + productId));
```

```java
// ③ 幂等：订单号（用户ID+商品ID+秒杀场次）唯一索引，防止重复下单
public void createSeckillOrder(Long userId, Long productId) {
    Order order = orderRepository.findByBuyerIdAndProductId(userId, productId);
    if (order != null) {
        throw new BusinessException(400, "您已参与过该秒杀");
    }
    // ...
}
```

**5. 削峰：为什么用 MQ？**
- 秒杀瞬间 10 万请求，DB 只能扛几千
- MQ 作为缓冲：应用层快速消费 10 万请求并返回"抢购中"，真正落库的只有 1000 单
- 消费者根据库存数量提前确认：库存为 0 时消费者直接丢弃剩余消息

**6. 高可用**：
- Redis 哨兵/集群部署，防止缓存挂了
- DB 主从 + Seata 事务确保扣库存和订单状态一致
- 秒杀链路独立部署，失败不影响主链路

**7. 面试亮点补充**：
- 前端倒计时从**服务器时间**取，避免客户端作弊
- 同一用户限购 1 件用 **Redis 幂等标记 + 数据库唯一索引**双重保证
- 秒杀商品详情用**静态页 + CDN**，避免 10 万用户刷新把应用打挂

## 场景三：短链系统设计

### 题目

设计一个短链系统：把长 URL 转换为难记的短 URL，支持点击跳转，需支持高并发。

### 回答框架

**1. 明确边界**：日千万访问，短链长度 6-8 字符，永久有效或有时效。

**2. 核心问题**：
- 短码如何生成（唯一、不可预测）
- 如何存（key-value 存储最合适）
- 跳转如何做（302 临时跳转 vs 301 永久跳转）

**3. 方案**：

```
用户点击短链 → DNS → Nginx → 网关 → 短链服务
                                        │
                                        ▼
                           Redis 查映射（热点短链命中缓存）
                                        │
                                        ▼
                           MySQL 兜底（缓存未命中）
                                        │
                                        ▼
                           302 跳转长 URL
```

**短码生成方案对比**：

| 方案 | 原理 | 缺点 |
|------|------|------|
| MD5 截取 | 长 URL MD5 后截取 8 位 | 碰撞率高 |
| 进制转换 | 自增 ID → 62 进制（a-zA-Z0-9） | ID 可被穷举猜测 |
| 发号器（推荐） | 分布式 ID 生成器（雪花/Redis INCR）→ 62 进制转换 | 需要发号器，ID 递增可泄露数量 |
| 预生成 + 随机 | 预生成随机字符串存库去重 | 需要维护储备池 |

**推荐方案**：分布式发号器（Redis INCR 或美团 Leaf 号段）生成 64 位 ID → 62 进制转换得到短码。

```java
// 62 进制转换（基础版）
private static final char[] BASE62 = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();

public String encode(long id) {
    StringBuilder sb = new StringBuilder();
    while (id > 0) {
        sb.append(BASE62[(int) (id % 62)]);
        id /= 62;
    }
    return sb.reverse().toString();
}
```

**4. 302 vs 301**：
- **301 永久跳转**：浏览器缓存跳转结果，减少服务器压力；但**无法统计点击量**、无法做运营干预
- **302 临时跳转**：每次真实请求服务器，可统计、可追溯、可干预（比如安全拦截）

**推荐：302**。虽然多了服务器开销，但能拿到完整点击数据。

**5. 优化点**：
- 短链映射用 Redis **热点缓存**，过期时间动态设置（访问越频繁，TTL 越长）
- 数据库用**分表**（按短码 hash 分 64 张表）
- 短码列建**唯一索引**，防止重复
- 恶意点击防护：限流 + 安全检测（拦截钓鱼链接）

## 场景四：设计一个日志系统

### 题目（补充练习）

海量日志收集（每日 10 亿条），要支持实时检索和聚合分析。

**框架要点**：
- 采集：Filebeat / Fluentd（日志采集器）
- 缓冲：Kafka（削峰，解耦）
- 存储与检索：Elasticsearch（倒排索引）
- 可视化：Kibana
- 分布式追踪：关联 traceId，串联调用链

## 场景题加分项总结

| 行为 | 加分 |
|------|------|
| 先问清 QPS、数据量、一致性要求再设计 | 展示"边界意识" |
| 方案给出数字和量级（几百 QPS → 10 万 QPS 各层能力不同） | 展示"量化思维" |
| 每个方案都说出 trade-off | 展示"架构取舍能力" |
| 能落到 SQL/Lua/配置等具体细节 | 展示"可落地性" |
| 最后补充"如何验证/压测" | 展示"全面性" |