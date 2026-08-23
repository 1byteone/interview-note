# Redis 面试题大全

## 📚 知识体系

```
Redis 数据结构
├── String
├── Hash
├── List
├── Set
├── Sorted Set
├── Bitmap
├── HyperLogLog
├── GEO
└── Stream

Redis 核心机制
├── 持久化 (RDB / AOF)
├── 主从复制
├── 哨兵模式
├── 集群模式
├── 事务
├── 管道
├── Lua 脚本
└── 发布订阅

Redis 应用场景
├── 缓存
├── 分布式锁
├── 限流
├── 计数器
├── 排行榜
├── 消息队列
├── 延迟队列
├── 布隆过滤器
├── 位图统计
└── 附近的人

Redis 高级特性
├── 内存淘汰策略
├── 过期策略
├── 慢查询日志
├── Pipeline
├── RedisModule
├── Redisson
├── Redis Cluster
└── Redis 6.0 多线程
```

---

## 🎯 Level 1：基础题

### 1. Redis 有哪些数据结构？
**答案**：
- String（字符串）
- Hash（哈希）
- List（列表）
- Set（集合）
- Sorted Set（有序集合）
- Bitmap（位图）
- HyperLogLog（基数统计）
- GEO（地理空间）
- Stream（流）

### 2. Redis 为什么这么快？
**答案**：
1. **纯内存操作**：所有数据存储在内存中，读写速度极快
2. **单线程模型**：避免上下文切换和锁竞争
3. **IO 多路复用**：使用 epoll 实现高并发 IO
4. **高效的数据结构**：使用跳表、压缩列表等高效数据结构
5. **C 语言实现**：底层使用 C 语言，性能高

### 3. 什么是缓存穿透、缓存击穿、缓存雪崩？
**答案**：

| 问题 | 描述 | 解决方案 |
|------|------|----------|
| **缓存穿透** | 查询一个不存在的数据，缓存和数据库都没有 | ① 布隆过滤器 ② 缓存空值 |
| **缓存击穿** | 热点 key 过期，大量请求打到数据库 | ① 互斥锁 ② 逻辑过期时间 |
| **缓存雪崩** | 大量 key 同时过期，导致数据库压力暴增 | ① 随机过期时间 ② 多级缓存 |

---

## 🎯 Level 2：进阶题

### 4. Redis 的持久化机制有哪些？
**答案**：

**RDB（快照）**：
- 原理：在指定时间间隔内生成数据快照
- 优点：文件紧凑，恢复速度快
- 缺点：可能丢失最后一次快照后的数据

**AOF（追加文件）**：
- 原理：记录所有写操作命令
- 优点：数据安全性高，最多丢失 1 秒数据
- 缺点：文件体积大，重放速度慢

**混合持久化（Redis 4.0+）**：
- RDB 做全量快照 + AOF 做增量日志
- 兼顾恢复速度和数据安全性

### 5. Redis 的过期策略有哪些？
**答案**：
1. **定期删除**：每隔 100ms 随机抽取一些 key 检查并删除过期 key
2. **惰性删除**：访问 key 时检查是否过期，过期则删除
3. **内存淘汰策略**：
   - `noeviction`：不淘汰，返回错误
   - `allkeys-lru`：淘汰最近最少使用的 key
   - `volatile-lru`：淘汰设置了过期时间中最近最少使用的 key
   - `allkeys-random`：随机淘汰 key
   - `volatile-ttl`：淘汰即将过期的 key
   - `volatile-lfu`：淘汰最不经常使用的 key（Redis 4.0+）
   - `allkeys-lfu`：淘汰所有 key 中最不经常使用的（Redis 4.0+）

### 6. Redis 分布式锁如何实现？
**答案**：

**基本实现**：
```bash
SET key value NX EX 30
```
- `NX`：key 不存在时才设置
- `EX 30`：过期时间 30 秒

**Redisson 实现**：
```java
RLock lock = redissonClient.getLock("myLock");
try {
    // 尝试加锁，最多等待 100 秒，锁自动释放 30 秒
    if (lock.tryLock(100, 30, TimeUnit.SECONDS)) {
        // 业务逻辑
    }
} finally {
    lock.unlock();
}
```

**Redlock 算法**（多节点分布式锁）：
- 在大多数 Redis 节点上获取锁
- 计算获取锁的时间是否小于过期时间
- 确保锁的容错性

### 7. Redis Cluster 原理是什么？
**答案**：
- **数据分片**：16384 个哈希槽
- **节点通信**：Gossip 协议
- **高可用**：主从复制 + 自动故障转移
- **无中心化**：每个节点都保存集群状态

---

## 🎯 Level 3：高级题

### 8. 如何设计 Redis 缓存架构？
**答案**：

**多级缓存架构**：
```
客户端
  ↓
CDN 层 → 缓存静态资源
  ↓
Nginx 层 → 本地缓存
  ↓
Redis 集群 → 分布式缓存
  ↓
MySQL 主从 → 持久化存储
```

**缓存策略**：
1. **旁路缓存（Cache-Aside）**：
   - 读：先查缓存，未命中查数据库，回填缓存
   - 写：先更新数据库，再删除缓存

2. **读写穿透（Read/Write Through）**：
   - 缓存层作为数据访问的主要入口

3. **异步缓存（Write Behind）**：
   - 异步更新缓存，提高写性能

### 9. 如何处理 Redis 大 Key 问题？
**答案**：
**大 Key 问题**：
- 单个 String 类型 value > 10KB
- 集合类型元素数量 > 5000 个

**影响**：
- 阻塞 Redis 操作
- 网络传输延迟高
- 内存分布不均

**解决方案**：
1. **拆分大 Key**：将大 Key 拆分为多个小 Key
2. **压缩存储**：对 value 进行压缩
3. **使用 Hash 代替 String**：将大 String 拆分为 Hash 字段
4. **异步删除**：使用 `UNLINK` 命令代替 `DEL`

### 10. 如何实现 Redis 延迟队列？
**答案**：
**方案一：Sorted Set 实现**
```java
// 添加延迟任务
redis.zadd("delay_queue", System.currentTimeMillis() + delay, taskId);

// 轮询获取到期任务
Set<String> tasks = redis.zrangeByScore("delay_queue", 0, System.currentTimeMillis());
for (String task : tasks) {
    // 使用 zrem 原子性获取任务
    if (redis.zrem("delay_queue", task) > 0) {
        // 执行任务
    }
}
```

**方案二：Redisson RDelayedQueue**
```java
RQueue<String> queue = redisson.getQueue("myQueue");
RDelayedQueue<String> delayedQueue = redisson.getDelayedQueue(queue);

// 添加延迟任务
delayedQueue.offer("task1", 10, TimeUnit.SECONDS);
delayedQueue.offer("task2", 30, TimeUnit.SECONDS);

// 消费任务
while (true) {
    String task = queue.take(); // 阻塞获取
    // 执行任务
}
```

---

## 🎯 Level 4：专家题

### 11. 如何设计 Redis 高可用架构？
**答案**：

**方案一：Redis Cluster + 主从 + 哨兵**
```
             Client
               ↓
         LVS/Nginx 负载均衡
               ↓
    ┌──────────┼──────────┐
    ↓          ↓          ↓
  Redis-1    Redis-2    Redis-3
  (Master)   (Master)   (Master)
    ↓          ↓          ↓
  Redis-1S   Redis-2S   Redis-3S
  (Slave)    (Slave)    (Slave)
```

**方案二：Codis/Twemproxy 代理方案**
```
            Client
              ↓
    ┌─────────┼─────────┐
    ↓         ↓         ↓
  Proxy-1   Proxy-2   Proxy-3
    ↓         ↓         ↓
    └─────────┼─────────┘
              ↓
    ┌─────────┼─────────┐
    ↓         ↓         ↓
  Redis-1   Redis-2   Redis-3
```

**方案三：云原生方案**
- AWS ElastiCache Redis
- 阿里云 Redis
- 腾讯云 Redis

### 12. Redis 6.0 多线程机制？
**答案**：
- 默认只开启多线程处理 IO 读写
- 命令执行仍然是单线程
- 通过 `io-threads` 配置线程数
- 提高网络 IO 处理能力
- 避免多线程竞争带来的复杂度

---

## 📖 学习资源

### 推荐项目
- [Redisson](https://github.com/redisson/redisson) - Java Redis 客户端
- [Redis 官方文档](https://redis.io/documentation)
- [Redis 命令参考](https://redis.io/commands)

### 最佳实践
1. 合理设置过期时间，避免内存泄漏
2. 使用连接池管理 Redis 连接
3. 监控慢查询，优化性能
4. 定期检查大 Key，及时拆分
5. 使用 Pipeline 批量操作提高性能