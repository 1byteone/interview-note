# Redis 高级数据结构 — Bitmap · HyperLogLog · GEO · Stream

> 等级：👶 新手通道
> 目标：掌握 4 种进阶数据结构的使用场景与核心命令，它们是面试中"让人眼前一亮"的加分项。

---

## 一、Bitmap — 位图（布尔值的极致压缩）

### 1.1 原理

Bitmap 本质上是 String 的位操作，一个字节 8 个 bit，每个 bit 代表一个布尔值（0/1）。1 亿个用户只需要约 12MB 内存。

### 1.2 核心命令

| 命令 | 说明 | 示例 |
|------|------|------|
| `SETBIT key offset value` | 设置某一位为 0 或 1 | `SETBIT sign:20260822 1001 1` |
| `GETBIT key offset` | 获取某一位的值 | `GETBIT sign:20260822 1001` |
| `BITCOUNT key` | 统计 1 的个数 | `BITCOUNT sign:20260822` |
| `BITPOS key bit` | 找第一个 0 或 1 | `BITPOS sign:20260822 0` |
| `BITOP op dest k1 k2` | 位运算（AND/OR/XOR/NOT） | `BITOP AND result d1 d2` |

### 1.3 使用场景

**场景 1：用户签到**
```bash
# 用户 ID 1001 在 2026-08-22 签到
> SETBIT sign:20260822 1001 1
(integer) 0
# 查询该用户是否签到
> GETBIT sign:20260822 1001
(integer) 1
# 统计当天签到人数
> BITCOUNT sign:20260822
(integer) 17520
```

**场景 2：连续签到判断**
```bash
# 对昨天和今天的 bitmap 做 AND 运算，结果中 1 的位数就是连续签到人数
> BITOP AND both sign:20260821 sign:20260822
(integer) 1250000
> BITCOUNT both
(integer) 10234
```

**场景 3：布隆过滤器的底层实现**
布隆过滤器本质上就是多个 hash 函数 + 一个 bitmap，用于快速判断一个元素**不**存在。

### 1.4 面试要点
> 1 亿用户的签到记录，用 MySQL 存需要几张表、几 GB？用 Bitmap 只需要 12MB。这就是位图的魅力——**空间换时间，但空间几乎为零**。

---

## 二、HyperLogLog — 基数统计（UV 终极方案）

### 2.1 原理

HyperLogLog 是一种**概率性数据结构**，用于统计不重复元素的数量（基数）。标准误差 0.81%，但 12KB 内存就能统计 2^64 个元素。

**核心思想**：每个元素哈希后，统计二进制"最长前缀 0 的个数"，用这个值估算基数。

### 2.2 核心命令

| 命令 | 说明 | 示例 |
|------|------|------|
| `PFADD key element [element ...]` | 添加元素 | `PFADD uv:page:home u1 u2 u3` |
| `PFCOUNT key [key ...]` | 估算基数 | `PFCOUNT uv:page:home` |
| `PFMERGE destkey source [source ...]` | 合并多个 | `PFMERGE uv:total uv:page1 uv:page2` |

### 2.3 使用场景：亿级 UV 统计

```bash
# 每个用户访问页面时，PFADD
> PFADD uv:home:20260822 user:1001 user:1002 user:1003
(integer) 1
> PFADD uv:home:20260822 user:1001     # 重复加不影响
(integer) 0

# 查询当天 UV
> PFCOUNT uv:home:20260822
(integer) 3

# 合并一周的 UV（去重后的总用户数）
> PFMERGE uv:week:35 uv:home:20260821 uv:home:20260822 uv:home:20260823
OK
> PFCOUNT uv:week:35
(integer) 10234
```

### 2.4 面试要点

> **HyperLogLog vs Set 做 UV 统计？**
> 1 万 UV 以内推荐 Set（精确），10 万 UV 以上推荐 HyperLogLog。Set 存储每个元素，亿级 UV 内存爆掉；HLL 固定 12KB，适合海量但允许 0.81% 误差的场景。

---

## 三、GEO — 地理空间（附近的人/店）

### 3.1 原理

GEO 基于 ZSet 实现，将经纬度编码为 52 位整数作为 score，底层使用 **Geohash** 算法，支持附近位置查询。

### 3.2 核心命令

| 命令 | 说明 | 示例 |
|------|------|------|
| `GEOADD key lon lat member` | 添加位置 | `GEOADD shops 116.39 39.91 "望京店"` |
| `GEOPOS key member` | 获取经纬度 | `GEOPOS shops "望京店"` |
| `GEODIST key m1 m2 [unit]` | 计算距离 | `GEODIST shops "望京店" "国贸店" km` |
| `GEORADIUS key lon lat rad unit` | 半径查询 | `GEORADIUS shops 116.39 39.91 5 km` |
| `GEORADIUSBYMEMBER key m rad unit` | 以某成员为中心 | `GEORADIUSBYMEMBER shops "望京店" 5 km` |

### 3.3 使用场景：附近门店查询

```bash
# 添加门店坐标
> GEOADD shops 116.391 39.908 "望京SOHO店" 116.460 39.921 "798店" 116.310 39.990 "回龙观店"
(integer) 3

# 用户当前位置：望京 SOHO，查询附近 5km 的门店
> GEORADIUS shops 116.391 39.908 5 km WITHCOORD WITHDIST ASC
1) 1) "望京SOHO店"
   2) "0.000"          # 距离 0
   3) 1) "116.391"
      2) "39.909"
2) 1) "798店"
   2) "4.2"            # 距离 4.2km
   3) 1) "116.460"
      2) "39.921"
```

### 3.4 面试要点

> **GEO 的精度问题？**
> Geohash 编码是近似值，距离越近精度越高。Redis 6.0+ 的 GEO 支持半径 0.1m 到 5000km。如果精度要求极高（米级），建议用专业引擎（如 Elasticsearch GEO）。

---

## 四、Stream — 消息队列（Redis 5.0 原生 MQ）

### 4.1 原理

Stream 是 Redis 5.0 引入的原生消息队列数据结构，弥补了 List 做消息队列的不足（`BLPOP` 无 ACK 机制、无法多消费者组）。它像一个**追加写日志**，每条消息有唯一 ID。

### 4.2 核心命令

| 命令 | 说明 |
|------|------|
| `XADD key * field val [field val]` | 追加消息（自动生成 ID） |
| `XREAD COUNT n BLOCK ms STREAMS key id` | 阻塞读取消息 |
| `XGROUP CREATE key group id` | 创建消费者组 |
| `XREADGROUP GROUP g c COUNT n BLOCK ms STREAMS key >` | 消费者组读取 |
| `XACK key group id` | 确认消息已处理 |
| `XLEN key` | 消息长度 |
| `XRANGE key - +` | 范围查询所有消息 |
| `XTRIM key MAXLEN ~ n` | 裁剪到大致长度 |

### 4.3 使用场景：订单超时取消

```bash
# 生产者：订单 30 分钟未支付，发送超时检查消息
> XADD order:timeout * orderId "1001" userId "u1" createTime "20260822120000"
"1724313600000-0"

# 消费者组：创建组
> XGROUP CREATE order:timeout timeout-group $
OK

# 消费者 A 读取待处理消息
> XREADGROUP GROUP timeout-group consumer-a COUNT 1 BLOCK 5000 STREAMS order:timeout >
1) 1) "order:timeout"
   2) 1) 1) "1724313600000-0"
         2) "orderId" "1001" "userId" "u1" "createTime" "20260822120000"

# 处理完成后确认
> XACK order:timeout timeout-group 1724313600000-0
(integer) 1
```

### 4.4 Stream vs 其他消息队列

| 对比维度 | Redis Stream | RocketMQ / Kafka |
|----------|-------------|------------------|
| 持久化 | 基于 RDB/AOF，有丢数据风险 | 磁盘持久化，几乎不丢 |
| 消息堆积 | 内存有限，XTRIM 维护 | 磁盘存储，堆积能力强 |
| 消费者组 | 支持 | 支持 |
| 延迟消息 | 本身不支持，需配合 ZSet | 原生支持 |
| 适用场景 | 轻量、低延迟、小流量 | 金融级、高吞吐、海量堆积 |

**结论**：Stream 适合**轻量级消息队列**场景（如日志收集、内部通知），不适合生产级核心链路。商城项目中我们仍用 RocketMQ。

### 4.5 面试要点

> **Stream 与 Kafka 的消费者组有何不同？**
> Kafka 按 partition 分片，一个 partition 只能被一个消费者读；Stream 的消费者组内是"轮流消费"，更像队列模式。另外 Stream 的 PEL（Pending Entries List）机制天然支持消息重试。

---

## 五、总结

| 数据结构 | 本质 | 最大特点 | 内存开销 | 经典场景 |
|----------|------|---------|----------|----------|
| Bitmap | 位图 | 1 个 bit 存 1 个布尔值 | 1 亿用户 ≈ 12MB | 签到、布隆过滤器 |
| HyperLogLog | 概率计数器 | 12KB 统计 2^64 个元素 | 固定 12KB | UV 统计 |
| GEO | 有序集合封装 | 地理坐标 + 距离计算 | 取决于条目数 | 附近的人、门店 |
| Stream | 消息队列 | 持久化 + ACK + 消费者组 | 内存受限 | 轻量 MQ、日志 |

> 进入核心篇：持久化原理（RDB + AOF），这是理解 Redis 数据安全的基础。