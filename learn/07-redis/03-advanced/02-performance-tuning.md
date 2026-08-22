# Redis 性能调优 — 内存优化 · 大 Key 热 Key · 慢查询 · Pipeline

> 等级：🎯 面试进阶
> 目标：掌握 Redis 内存优化的核心方法，学会排查和处理大 Key、热 Key、慢查询，并了解 Pipeline 与异步删除等高级技巧。

---

## 一、内存优化

### 1.1 内存淘汰策略

Redis 内存满了之后，根据配置的策略淘汰数据：

```bash
# redis.conf
maxmemory 4gb
maxmemory-policy allkeys-lru
```

| 策略 | 说明 | 适用场景 |
|------|------|---------|
| `noeviction` | 不淘汰，写操作直接报错 | 数据库级使用 |
| `allkeys-lru` | 从所有 key 中淘汰最久未使用的 | **最常用** |
| `volatile-lru` | 从设置了过期时间的 key 中淘汰 | 混合使用 |
| `allkeys-lfu` | 淘汰访问频率最低的 key | 热点数据 |
| `volatile-ttl` | 淘汰剩余生存时间最短的 | 特定场景 |
| `allkeys-random` | 随机淘汰 | 缓存无差别 |

**LRU vs LFU**：
- **LRU**（Least Recently Used）：最近最少使用，关注最后一次访问时间
- **LFU**（Least Frequently Used）：最不经常使用，关注访问频率
- Redis 4.0+ 支持 LFU，适合"刷短视频"场景（LRU 可能误淘汰高频短命 key）

### 1.2 内部编码优化

Redis 对每种数据结构都有多种编码，小数据量时用紧凑编码节省内存：

| 数据结构 | 紧凑编码 | 升级条件 | 升级后编码 |
|---------|----------|----------|-----------|
| String | int / embstr | 整数或 < 44B 字符串 | raw |
| Hash | ziplist | 字段数 > 512 或值 > 64B | hashtable |
| List | quicklist | 默认即 quicklist（ziplist 节点组合） | — |
| Set | intset | 全整数且成员数 < 512 | hashtable |
| ZSet | ziplist | 成员数 < 128 且值 < 64B | skiplist |

```bash
# 查看 key 的编码
> OBJECT ENCODING product:1001
"hashtable"

> OBJECT ENCODING counter:visit
"int"
```

**优化建议**：
- 控制 Hash 字段数 < 512，利用 ziplist 压缩
- 控制 ZSet 成员数 < 128，利用 ziplist 压缩
- 用 `HASH-max-ziplist-entries 1024` 适当调大阈值（内存更省但 CPU 略多）

### 1.3 内存分析

```bash
# 查看 Redis 内存使用
> INFO memory
# Memory
used_memory_human:1.2G
used_memory_rss_human:1.5G
maxmemory_human:4.0G
mem_fragmentation_ratio:1.25

# 查看单个 key 的占用（不准确，只适合大致判断）
> MEMORY USAGE product:1001
(integer) 1520
```

---

## 二、大 Key 排查与处理

### 2.1 什么是大 Key？

| 类型 | 阈值 | 危害 |
|------|------|------|
| 单个 String > 10MB | 10MB | 网络传输慢，阻塞其他命令 |
| Hash/Set/ZSet 成员 > 5000 个 | 5000 个 | 操作耗时（HGETALL 可能阻塞秒级） |
| List 长度 > 10000 | 10000 个 | 内存占用大 |

### 2.2 危害

- **阻塞 Redis**：操作大 Key（如 `HGETALL`、`LRANGE 0 -1`）耗时过长，阻塞 Redis 单线程
- **网络卡顿**：大 Key 传输占用带宽，影响其他客户端
- **数据倾斜**：Cluster 模式下，大 Key 所在节点内存和请求量远超其他节点
- **慢查询**：大 Key 操作时间长，直接表现为慢查询

### 2.3 排查方法

```bash
# 方法一：redis-cli --bigkeys（扫描所有 key，推荐）
$ redis-cli --bigkeys
# Scanning the entire keyspace...
# Largest string found: product:1001 has 5.2 MB
# Largest hash found: cart:user:1001 has 3422 fields

# 方法二：自定义 Lua 脚本随机采样
> EVAL "local k = redis.call('RANDOMKEY'); return {k, redis.call('MEMORY USAGE', k)}" 0

# 方法三：开通 slowlog 定期分析
> SLOWLOG GET 10
```

### 2.4 处理方案

| 场景 | 方案 | 代码示例 |
|------|------|---------|
| 大 String | 拆分为多个小 key | `product:1001:basic` + `product:1001:detail` |
| 大 Hash | 分桶（hash tag） | `user:1001:info:1`、`user:1001:info:2` |
| 大 List | 拆分或改用 List + 分页 | `LRANGE key 0 99` 而不是 `0 -1` |
| 大 ZSet | 分片 + 定期修剪 | `ZREMRANGEBYRANK key 0 -10000` |

---

## 三、热 Key 排查与处理

### 3.1 什么是热 Key？

某个 key 在短时间内被大量请求访问（如双 11 爆款商品、微博热搜第一条），QPS 可能达到几十万甚至上百万。

### 3.2 危害

- **单节点瓶颈**：Redis 单线程处理，热 Key 占用了大量 CPU 时间片，拖慢其他请求
- **Redis 节点过载**：在 Cluster 模式下，热 Key 所在节点成为性能瓶颈（数据倾斜 + 流量倾斜）

### 3.3 排查方法

```bash
# 方法一：hotkeys 参数（Redis 4.0+，需开启 LFU）
redis-cli --hotkeys

# 方法二：monitor 命令（采样，生产慎用，会降低性能）
redis-cli monitor | grep "product:1001"

# 方法三：客户端统计（在业务代码中统计）
```

### 3.4 处理方案

| 方案 | 说明 | 复杂度 |
|------|------|--------|
| 本地缓存 | Caffeine 在应用层缓存，减少 Redis 读请求 | 低 |
| 读写分离 | 多个从节点分担读请求 | 中 |
| 散列打散 | 热 key 加后缀（如 `hot:product:1001:0~99`），随机读 | 中 |
| 二级缓存 | 多级缓存逐层保护 | 高 |

**散列打散示例**：

```java
// 热 key 拆分为 100 个副本
String hotKey = "product:1001";
int bucket = ThreadLocalRandom.current().nextInt(100);
String bucketKey = hotKey + ":" + bucket;
String productStr = redisTemplate.opsForValue().get(bucketKey);
```

**注意**：散列打散只有在读多写少时有效（写时需要更新所有副本）。

---

## 四、慢查询日志

### 4.1 配置

```bash
# redis.conf
slowlog-log-slower-than 10000    # 慢查询阈值（微秒），超过 10ms 记录
slowlog-max-len 128              # 最多保留 128 条
```

### 4.2 查看

```bash
> SLOWLOG GET 5
1) 1) (integer) 42            # 唯一 ID
   2) (integer) 1724313600     # Unix 时间戳
   3) (integer) 15000           # 执行耗时（微秒），15ms
   4) 1) "HGETALL"             # 命令
      2) "product:1001"        # key
   5) "127.0.0.1:6379"         # 客户端地址

> SLOWLOG LEN                 # 慢查询数量
(integer) 128

> SLOWLOG RESET               # 清空慢查询
```

### 4.3 慢查询原因

| 原因 | 示例 | 解决方案 |
|------|------|---------|
| 大 Key 操作 | `HGETALL` 大 Hash | 拆分 / 改用 `HSCAN` |
| 复杂命令 | `SORT`、`ZUNIONSTORE` | 业务层计算 |
| 大量 key 返回 | `KEYS *`（生产禁用） | 改用 `SCAN` |
| 批量操作 | `MGET` 10 万个 key | 控制批量大小 |

---

## 五、Pipeline 与批量操作

### 5.1 原理

Pipeline 将多条命令打包发送，减少网络 RTT（往返时间）。1000 条命令，常规方式 1000 次 RTT，Pipeline 只需 1 次 RTT。

### 5.2 代码示例

```java
// 不使用 Pipeline
long start = System.currentTimeMillis();
for (int i = 0; i < 10000; i++) {
    redisTemplate.opsForValue().set("key:" + i, "value:" + i);
}
// 耗时：约 5000ms（10000 次网络往返）

// 使用 Pipeline
RedisCallback<Object> callback = (connection) -> {
    connection.openPipeline();  // 开启 Pipeline
    for (int i = 0; i < 10000; i++) {
        connection.set(("key:" + i).getBytes(), ("value:" + i).getBytes());
    }
    connection.closePipeline(); // 关闭并提交
    return null;
};
redisTemplate.execute(callback);
// 耗时：约 50ms（1 次网络往返）
```

### 5.3 注意事项

- Pipeline 一次性发送的命令越多，响应缓冲区越大，建议单次 < 5000 条
- Pipeline 中的命令不是原子执行——只是批量发送，Redis 仍逐条执行
- 需要原子性时用 Lua 脚本，Lua 在服务端并发执行，比 Pipeline 更优

---

## 六、异步删除（lazy-free）

### 6.1 问题

删除大 Key（如 `DEL` 一个包含 1000 万成员的 ZSet）会阻塞 Redis 单线程，导致其他命令无法执行。

### 6.2 解决方案

```bash
# redis.conf
lazyfree-lazy-eviction yes      # 淘汰时异步释放
lazyfree-lazy-expire yes        # 过期时异步释放
lazyfree-lazy-server-del yes    # 删除时异步释放
replica-lazy-flush yes          # 从节点清空数据时异步
```

`UNLINK` 命令替代 `DEL`：
```bash
> UNLINK large:hash
(integer) 1
```

`UNLINK` 在后台异步释放内存，主线程继续处理其他命令。

---

## 七、性能监控指标

```bash
# 实时监控
redis-cli --stat

# 查看 Redis 进程状态
> INFO stats
uptime_in_seconds:864000
total_commands_processed:100000000
instantaneous_ops_per_sec:8500    # 当前 QPS
rejected_connections:0             # 拒绝连接数

# 延迟监控
> INFO latency
latency_percentiles_usec: ...      # Redis 7.2+ 延迟百分位
```

**关键指标**：
- 命中率（`keyspace_hits / (keyspace_hits + keyspace_misses)`）> 90% 为良好
- 内存碎片率（`mem_fragmentation_ratio`）> 1.5 时考虑重启或 `MEMORY PURGE`
- 当前 QPS（`instantaneous_ops_per_sec`）> 8 万需关注

---

## 八、面试速记

| 问题 | 原因 | 一句话方案 |
|------|------|-----------|
| 内存过大 | 数据量大 | 淘汰策略 + 紧凑编码 + 分片 |
| 大 Key | 一个 key 存了太多数据 | 拆分 + 分桶 + 限制单个 key 大小 |
| 热 Key | 单个 key 访问量极高 | 本地缓存 + 散列打散 + 读写分离 |
| 慢查询 | 原子命令耗时 > 10ms | 优化命令 + 拆分大 Key + 异步删除 |
| 网络延迟高 | 大量 RTT | Pipeline 批量操作 + 连接池 |

> 进入下一节：Redis 与 Spring Boot 整合——RedisTemplate、@Cacheable、自定义序列化。