# Redis 快速入门 — 安装 · 5 大数据结构 · 商品缓存最小案例

> 等级：👶 新手通道
> 目标：用 Docker 启动 Redis，亲手敲遍 5 大基础数据结构的命令，并完成一个"商品缓存"最小案例。

---

## 一、安装 Redis（Docker 一键启动）

### 1.1 拉取并启动

```bash
docker pull redis:7.2
docker run -d --name redis-dev \
  -p 6379:6379 \
  -v redis-data:/data \
  redis:7.2 redis-server --appendonly yes
```

| 参数 | 说明 |
|------|------|
| `-p 6379:6379` | 宿主机 6379 映射容器内 6379 |
| `-v redis-data:/data` | 数据卷持久化到磁盘 |
| `--appendonly yes` | 开启 AOF 持久化（防重启丢数据） |

### 1.2 进入客户端

```bash
docker exec -it redis-dev redis-cli
# 或者宿主机没有客户端时：
docker exec -it redis-dev bash
redis-cli -h 127.0.0.1 -p 6379 -a 密码(若有)
```

### 1.3 常用通用命令

```bash
SET key value       # 写
GET key             # 读
EXISTS key          # 判断是否存在 -> (integer) 1
TTL key             # 查看剩余生存时间，-1 永不过期，-2 已过期/不存在
EXPIRE key 60       # 设置 60 秒过期
DEL key             # 删除
SELECT 0            # 切换数据库（默认 0-15）
DBSIZE              # 查看当前库 key 数量
FLUSHALL            # 清空所有库（生产禁用！）
```

> 记忆点：Redis 的一切命令都是**键值对操作**，`EXPIRE + TTL` 是缓存最核心的生命周期管理。

---

## 二、5 大基础数据结构

### 2.1 String — 字符串/数值（最常用）

| 命令 | 说明 | 示例 |
|------|------|------|
| `SET / GET` | 赋值取值 | `SET user:1:name "张三"` |
| `SETNX` | 不存在才设置（分布式锁基础） | `SETNX lock:order 1` |
| `INCR / DECR` | 自增/自减（原子） | `INCR counter:visit` |
| `INCRBY / DECRBY` | 按步长增减 | `INCRBY user:1:score 10` |
| `SETEX / SETNX` | 带过期时间 / 不存在才设 | `SETEX token:abc 3600 "xxxx"` |
| `MSET / MGET` | 批量读写 | `MSET a 1 b 2` |
| `GETSET` | 先返回旧值再设置新值 | `GETSET counter 100` |

**内部编码**：int（整数）、embstr（短字符串 <44B）、raw（长字符串）。

**使用场景**：计数器（访问量）、分布式锁、Session、缓存 JSON 序列化结果。

```bash
> SET seckill:stock:1001 100
OK
> DECR seckill:stock:1001
(integer) 99
> INCR visit:2026-08-22
(integer) 2048
```

### 2.2 Hash — 键值对映射（缓存对象）

| 命令 | 说明 | 示例 |
|------|------|------|
| `HSET / HGET` | 设字段 / 取字段 | `HSET product:1 name "手机" price 3999` |
| `HMSET / HMGET` | 批量 | `HMGET product:1 name price` |
| `HGETALL` | 取全部字段 | `HGETALL product:1` |
| `HINCRBY` | 字段自增（购物车数量） | `HINCRBY cart:u1 item:2 1` |
| `HEXISTS / HDEL` | 判断 / 删除字段 | `HDEL product:1 price` |
| `HLEN` | 字段数量 | `HLEN product:1` |

**内部编码**：ziplist（字段少且值小时）→ hashtable（字段多后升级）。

**使用场景**：缓存对象（商品、用户）、购物车、会话信息。

```bash
# 缓存商品对象（比 String 存 JSON 更精细：可只改一个字段，不用整体反序列化）
> HSET product:1001 name "AI 智能手环" price 299 stock 500
OK
> HINCRBY product:1001 stock -1
(integer) 499
```

### 2.3 List — 双向链表（队列/栈）

| 命令 | 说明 | 示例 |
|------|------|------|
| `LPUSH / RPUSH` | 左/右插入 | `LPUSH queue:order 1001` |
| `LPOP / RPOP` | 左/右弹出 | `LPOP queue:order` |
| `LRANGE key 0 -1` | 取区间 | `LRANGE queue:order 0 -1` |
| `LLEN` | 长度 | `LLEN queue:order` |
| `BLPOP / BRPOP` | **阻塞弹出**（可实现消息队列） | `BLPOP queue:order 5` |

**内部编码**：quicklist（双向链表 + ziplist 压缩节点组合）。

**使用场景**：消息队列（LPUSH + BRPOP）、最新消息列表、日志流、订阅通知。

```bash
# 最新公告：先塞入左侧，LRANGE 0 4 取最新 5 条
> LPUSH news "公告3: 双11预热" "公告2: 新版本上线" "公告1: AI客服上线"
(integer) 3
> LRANGE news 0 4
1) "公告3: 双11预热"
```

### 2.4 Set — 无序去重集合

| 命令 | 说明 | 示例 |
|------|------|------|
| `SADD / SREM` | 增 / 删成员 | `SADD tags:1001 "数码" "智能"` |
| `SMEMBERS` | 所有成员 | `SMEMBERS tags:1001` |
| `SISMEMBER` | 判断存在 | `SISMEMBER blacklist u999` |
| `SCARD` | 成员数量 | `SCARD tags:1001` |
| `SINTER` | **交集**（共同关注） | `SINTER user:1:follow user:2:follow` |
| `SUNION / SDIFF` | 并集 / 差集 | `SDIFF all:user blacklist` |

**内部编码**：intset（全整数且少时）→ hashtable。

**使用场景**：去重（UV、标签）、共同好友/关注、抽奖（随机弹出一个 `SPOP`）、黑白名单。

```bash
> SADD activity:2026:join u1 u2 u3 u2      # 重复加入 u2 不生效
(integer) 3                                  # 实际只有 3 个成员（去重）
> SCARD activity:2026:join
(integer) 3
> SPOP activity:2026:join                   # 抽奖随机一人
```

### 2.5 ZSet — 有序集合（排行榜之王）

| 命令 | 说明 | 示例 |
|------|------|------|
| `ZADD key score member` | 添加（含分数） | `ZADD hot:products 100 "手机"` |
| `ZINCRBY key inc member` | 分数增加 | `ZINCRBY hot:products 10 "手机"` |
| `ZRANGE key 0 -1` | 升序排名 | `ZRANGE hot:products 0 -1 WITHSCORES` |
| `ZREVRANGE key 0 -1` | **降序排名**（排行榜） | `ZREVRANGE hot:products 0 9 WITHSCORES` |
| `ZRANK / ZREVRANK` | 排名（从 0 开始） | `ZREVRANK hot:products "手机"` |
| `ZSCORE` | 查分数 | `ZSCORE hot:products "手机"` |
| `ZCARD` | 成员数 | `ZCARD hot:products` |
| `ZRANGEBYSCORE` | 按分数区间取 | `ZRANGEBYSCORE hot:products 50 100` |

**内部编码**：ziplist（成员少且值小时）→ skiplist（跳表 + dict 复合结构）。

**使用场景**：排行榜、延迟队列（score=执行时间戳）、优先级任务、TopN 统计。

```bash
# 商品热度榜：每次被点击 +1
> ZINCRBY hot:products 1 "AI 智能手环"
> ZINCRBY hot:products 1 "AI 智能手环"
> ZREVRANGE hot:products 0 2 WITHSCORES
1) "AI 智能手环"
2) "2"
```

---

## 三、最小案例：商品缓存（Redis 手动版）

### 3.1 缓存策略（Cache-Aside 旁路缓存）

```
读：先查 Redis → 命中直接返回 → 未命中查 MySQL → 回写 Redis
写：先更 MySQL → 再删 Redis（下次读时重建）
```

### 3.2 命令行演示

```bash
# 1. 模拟第一次读：缓存未命中
> GET product:1001:info
(nil)

# 2. 模拟回源 MySQL（假想查询结果 3999 元）
> SET product:1001:info '{"id":1001,"name":"AI智能手环","price":299,"stock":500}' EX 3600
OK

# 3. 第二次读：直接命中缓存
> TTL product:1001:info
(integer) 3599

# 4. 库存变动：更新 MySQL 后删除缓存，保持一致性
> DEL product:1001:info
(integer) 1
```

### 3.3 为什么要"删缓存"而不是"更新缓存"？

- **更新缓存**存在并发写导致旧值覆盖的问题（两个线程写顺序乱）
- **删除缓存**让下一次读重建，天然规避写写竞争，还省一次写 DB 后的 Redis 写操作
- 删除失败兜底：用 MQ 重试删除 或 给缓存加短 TTL

---

## 四、动手练习

1. 用 Hash 实现一个用户购物车（`cart:{userId}` 商品ID → 数量），练习 `HINCRBY` 加减数量
2. 用 ZSet 实现班级成绩榜：添加 5 个学生成绩，然后取出前三名
3. 用 Set 求两个用户的共同关注
4. 用 List + `BRPOP` 模拟一个简单的消息队列（一个终端 push，一个终端 pop）
5. 理解 `SETNX` 加锁 + `EXPIRE` 设置过期时间，这将是分布式锁的地基

> 掌握 5 大结构后，进入下一节：Bitmap、HyperLogLog、GEO、Stream 四大进阶数据结构。