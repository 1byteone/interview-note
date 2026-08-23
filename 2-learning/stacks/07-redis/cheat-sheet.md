# Redis 速查卡 📋

> 面试前30分钟扫一遍，一页纸覆盖核心考点

## 🏷️ 核心概念速记

| 概念 | 一句话解释 | 常见陷阱 |
|------|-----------|----------|
| 5 大数据结构 | String(缓存/计数)、Hash(对象)、List(队列)、Set(去重/交集)、ZSet(排行榜) | 过期的 key 在被动/主动删除才释放，不保证实时 |
| 单线程模型 | 一个线程处理命令队列，事件循环(epoll)多路复用 | 单线程指处理命令，持久化/异步删除由子进程/线程完成 |
| 跳表 (SkipList) | ZSet 底层有序结构，多级索引加速，平均 O(log N) | 跳表替代红黑树，实现简单、范围查询友好、并发易控制 |
| 持久化 RDB | 全量快照，fork 子进程写磁盘，恢复快 | 可能丢数据，fork 耗时与内存正相关，大实例慎用 |
| 持久化 AOF | 追加写日志，三种刷盘策略(always/everysec/no) | AOF 文件过大需 bgrewrite，重写时 fork 子进程 |
| 缓存穿透 | 查不到的数据(不存在 key) 一直打 DB | 布隆过滤器 + 缓存空结果(短过期) |
| 缓存击穿 | 热点 key 过期，高并发全打到 DB | 互斥锁(MUTEX) / 逻辑过期(不更新，后台刷新) |
| 缓存雪崩 | 大量 key 同时过期 / Redis 宕机 | 过期时间打散 + 降级 + 集群 + 本地缓存兜底 |
| 分布式锁 | SETNX + EXPIRE / RedLock / Redisson | 释放锁要校验是否自己的锁(防止误删)，Lua 原子化 |
| Redis Cluster | 16384 个槽自动分片，无中心化，最少 3 主 3 从 | 不支持多 key 操作(跨槽)，mget 需要 hash tag |
| 主从+哨兵 | 主从复制 + 哨兵自动故障转移，保证高可用 | 异步复制有概率丢数据，哨兵不保证强一致 |
| 事务 (MULTI/EXEC) | 命令队列+一次性执行，乐观锁(WATCH)，不保证原子 | 中间出错继续执行剩余命令，不是 ACID 回滚 |
| 管道 (Pipeline) | 批量发命令减少 RTT，不保证原子 | 大量命令内存堆积，需合理分片 |
| 发布/订阅 | 消息广播，不持久化，客户端离线即丢失 | 业务消息别用 Pub/Sub，用 Stream 持久化确认 |

## 🔧 常用命令/API

```bash
# String 基础
SET key value [NX|XX] [EX seconds] [PX ms]   # NX 不存在才设(分布式锁)
SETNX key value                                # 不存在才设
GET key
STRLEN key
INCR key                                       # 原子自增
```

```bash
# 分布式锁模板（Lua 脚本保证原子性）
-- 加锁: SET key uuid NX EX 30
-- 释放锁（Lua 保证原子）
if redis.call("GET", KEYS[1]) == ARGV[1] then
    return redis.call("DEL", KEYS[1])
else
    return 0
end
```

```bash
# Lua 限流脚本（滑动窗口）
-- KEYS[1] 限流key, ARGV[1] 窗口ms, ARGV[2] 限制次数
local now = redis.call('TIME')[1] * 1000
redis.call('ZREMRANGEBYSCORE', KEYS[1], 0, now - ARGV[1])
local cnt = redis.call('ZCARD', KEYS[1])
if cnt < tonumber(ARGV[2]) then
    redis.call('ZADD', KEYS[1], now, now)
    return 1   -- 允许
else
    return 0   -- 拒绝
end
```

```bash
# 缓存策略模板
# 读: 查缓存 → 有? 返回 : 查DB → 写缓存 → 返回
# 写: 更新DB → 删除缓存(延迟双删) / 更新缓存
# 先写DB后删缓存，容忍短暂不一致
# 延迟双删: 先删缓存 → 写DB → 休眠 → 再删缓存(防脏读)
```

```bash
# 常用管理命令
INFO memory                        # 内存使用
INFO replication                   # 主从状态
SLOWLOG GET 10                     # 慢查询
CLIENT LIST                        # 客户端列表
MONITOR                            # 调试用，线上禁用
KEYS *                             # 线上禁止！用 SCAN 替代
SCAN 0 MATCH user:* COUNT 100      # 游标遍历
MEMORY USAGE key                   # 查看 key 内存
```

## 🎯 面试高频 TOP10

1. **Q: Redis 为什么这么快？** **A:** 内存操作 + 单线程无锁竞争 + 多路复用 IO(epoll/select) + 高效数据结构(sds/ziplist/skiplist)。
2. **Q: 跳表原理？** **A:** 多层有序链表，上层"快车道"跳跃降低层级，插入时随机层高，平均 O(logN)；ZSet 同时维护 dict(哈希给 O(1) 查分) + skip list(范围/排序)。
3. **Q: 分布式锁怎么实现？** **A:** SET key uuid NX EX 30 加锁；释放时 Lua 校验 uuid 再 DEL；更复杂用 Redisson(看门狗自动续期)，RedLock 多节点写入。
4. **Q: 缓存穿透/击穿/雪崩的区别和解决方案？** **A:** 穿透(不存在数据) → 布隆过滤+空值缓存；击穿(热点过期) → 互斥锁+逻辑过期；雪崩(大面积过期) → 过期随机化+集群+降级。
5. **Q: Redis Cluster 数据分片原理？** **A:** 16384 个槽，CRC16(key) % 16384 映射到槽→节点；客户端直连任一节点，moved 重定向；resharding 在线迁移槽。
6. **Q: 大 Key 问题怎么处理？** **A:** 大 String(>10KB) 压缩/拆分；大 Hash(>万字段) 拆分为多个小 hash；大 List/Set/ZSet 分段/分桶；用 UNLINK 异步删除。
7. **Q: 缓存一致性怎么保证？** **A:** 先更新 DB 再删缓存(最终一致)；Binlog 监听(Canal) + 同步删除；读写锁(Cache-Aside)；延迟双删防并发脏读；强一致性走 DB 旁路。
8. **Q: Redis 淘汰策略？** **A:** noeviction(默认，超内存报错)、allkeys-lru、volatile-lru、allkeys-lfu、volatile-lfu、allkeys-random、volatile-ttl；业务常用 allkeys-lru。
9. **Q: 热 key 怎么解决？** **A:** 本地缓存(Guava/Caffeine)分担 + 多副本分散读 + 读写分离 + 热点 key 监测(Redis 4.0 hotkeys 命令/客户端统计)。
10. **Q: RDB 和 AOF 选哪个？** **A:** 可丢少量数据用 RDB(快恢复)；丢不起数据用 AOF everysec；高可靠性 AOF + RDB 混用(Redis 4.0+ AOF 重写后基础是 RDB 格式)。

## ⚠️ 常见坑 & 最佳实践

| ❌ 坑 | ✅ 正确做法 |
|-------|------------|
| 生产环境用 KEYS 扫全库 | 用 SCAN 游标遍历，避免阻塞 |
| 大 key 未拆，慢查询/阻塞 | 超过 10KB 拆分，超过百万元素分桶 |
| 分布式锁只 SETNX 不设过期 | SET key uuid NX EX 30，原子操作，防止死锁 |
| 释放锁时直接 DEL 他人锁 | Lua 先校验 uuid 再 DEL，确认是自己的锁 |
| 缓存穿透盲目布隆过滤 | 布隆+空值缓存组合，要定期重建 |
| 大量 key 同时过期(雪崩) | 过期时间加随机值(±30%)，错开失效时间 |
| 事务里混用 WATCH 不重试 | WATCH 检测到变化就放弃，重试整个事务 |
| 生产环境用 MONITOR 或 DEBUG 命令 | 线上禁用，压垮性能 |
| 业务消息用 Pub/Sub | 改用 Stream 支持持久化、消费组、ACK 确认 |

## 📐 架构设计要点

- **缓存分层**：本地缓存(1ms) → 分布式 Redis(5ms) → DB(10ms+)，逐层降级。
- **容量规划**：内存 < 可用内存 * 60% (留 CAP 给 RDB fork 写时复制和缓冲)，预留 maxmemory。
- **高可用**：生产必配 Cluster(3主3从) 或 主从 + 哨兵，避免单点。
- **监控**：INFO 指标(内存/命中率/慢查询/主从延迟/连接数) + 慢查询日志 + 大 key 扫描。
- **数据安全**：AOF everysec + RDB 日备份 + 密码 + 命令重命名(FLUSHALL/KEYS)。

## 🔗 关联技术

- **MySQL**：缓存层，处理热点数据，配合 Canal 同步 binlog 保证一致性。
- **RocketMQ**：Redis 做轻量队列(Stream) 替代 MQ 场景有局限，Stream 适合低吞吐量场景。
- **Sentinel**：流量控制、熔断降级，保护 Redis 不被击穿。
- **Docker/K8s**：Redis 容器化部署注意持久化、网络延迟、内存限制。
- **Redisson**：Java 客户端，封装分布式锁、限流器、信号量、布隆过滤器。