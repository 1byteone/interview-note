# 第六章：Redis 数据结构与缓存模式（P0 精通）

> 📖 **参考资料**：[Redis 官方文档](https://redis.io/docs/) | [Redis-Py Async](https://redis-py.readthedocs.io/)

Redis 不仅是缓存，更是数据结构服务器。本章从数据类型选型出发，覆盖三大缓存问题模式、分布式锁、滑动窗口限流与 Pipeline 事务，全部使用 `redis.asyncio` 异步客户端实现。

---

## 6.1 Redis 数据类型与使用场景

| 类型 | 底层编码 | 典型场景 | 时间复杂度 |
|------|---------|---------|-----------|
| **String** | int / embstr / raw | 缓存、计数器、分布式锁 | O(1) |
| **Hash** | listpack / hashtable | 对象存储（用户信息、商品详情） | O(1) |
| **List** | listpack / quicklist | 消息队列、最新动态列表 | O(1) push/pop |
| **Set** | intset / hashtable | 标签系统、共同好友、去重 | O(1) add/contains |
| **Sorted Set** | listpack / skiplist | 排行榜、延迟队列、滑动窗口限流 | O(log N) |
| **Stream** | radix tree / listpack | 事件溯源、可靠消息队列 | O(1) append |

```python
import redis.asyncio as redis

async def data_type_demo():
    """演示五种基本数据类型的核心操作"""
    r = redis.Redis(host="localhost", port=6379, db=0)

    # --- String: 缓存 + 原子计数 ---
    await r.set("user:1001:name", "张三", ex=3600)
    await r.incr("page:home:views")  # 原子自增，适合计数器

    # --- Hash: 对象存储，节省内存 ---
    await r.hset("user:1001", mapping={
        "name": "张三", "age": "28", "dept": "技术部"
    })
    user = await r.hgetall("user:1001")  # {b'name': b'张三', ...}

    # --- Sorted Set: 排行榜 ---
    await r.zadd("leaderboard", {"player_A": 9500, "player_B": 8700, "player_C": 9200})
    top3 = await r.zrevrange("leaderboard", 0, 2, withscores=True)

    # --- List: 简易消息队列 ---
    await r.rpush("msg:queue", "task_1", "task_2")
    task = await r.lpop("msg:queue")  # b'task_1'

    # --- Set: 标签交集 ---
    await r.sadd("post:1:tags", "python", "redis", "async")
    await r.sadd("post:2:tags", "python", "fastapi")
    common = await r.sinter("post:1:tags", "post:2:tags")  # {b'python'}

    await r.aclose()
```

---

## 6.2 缓存穿透 / 击穿 / 雪崩 三种模式

```
  用户请求 ──→ [应用层] ──→ [缓存层 Redis] ──→ 缓存命中？──→ 是 ──→ 返回
                                 │
                                 │ 未命中
                                 ▼
                         [数据库 / DB] ──→ 写回缓存 ──→ 返回

  ┌─────────────────────────────────────────────────────────────┐
  │  穿透(Penetration)    查不存在的 key → DB 也被穿透            │
  │  击穿(Breakdown)      热点 key 过期瞬间 → 大量请求打到 DB      │
  │  雪崩(Avalanche)      大批 key 同时过期 → DB 瞬时压力飙升      │
  └─────────────────────────────────────────────────────────────┘
```

### 6.2.1 Cache-Aside 模式（旁路缓存）

```python
import asyncio
import redis.asyncio as redis
from typing import Optional

r = redis.Redis(host="localhost", port=6379, db=0)

async def get_user_cache_aside(user_id: int) -> Optional[dict]:
    """Cache-Aside 读：先缓存 → 未命中回源 → 写回缓存"""
    cache_key = f"user:{user_id}"

    cached = await r.get(cache_key)
    if cached:
        import json
        return json.loads(cached)

    # 回源查询（模拟 DB）
    user = {"id": user_id, "name": f"user_{user_id}", "score": 100}

    # 写回缓存，加随机偏移防止雪崩
    import random
    ttl = 3600 + random.randint(0, 600)  # 1h ± 10min
    await r.set(cache_key, json.dumps(user), ex=ttl)
    return user
```

### 6.2.2 布隆过滤器防穿透

```python
# 使用 redis-py 的 BF.ADD / BF.EXISTS（Redis Stack 模块）
async def init_bloom_filter():
    """初始化布隆过滤器，预热已存在的 user_id"""
    for uid in range(1, 100_001):
        await r.execute_command("BF.ADD", "bf:user", str(uid))

async def safe_get_user(user_id: int):
    """布隆过滤器前置拦截"""
    exists = await r.execute_command("BF.EXISTS", "bf:user", str(user_id))
    if not exists:
        return None  # 肯定不存在，直接拦截
    return await get_user_cache_aside(user_id)
```

### 6.2.3 互斥锁防击穿

```python
async def get_hot_key(key: str):
    """热点 key 过期时，用分布式锁防止击穿"""
    val = await r.get(key)
    if val:
        return val

    lock_key = f"lock:{key}"
    locked = await r.set(lock_key, "1", nx=True, ex=10)  # 10s 锁
    if locked:
        try:
            val = await r.get(key)  # double-check
            if val:
                return val
            # 回源查询并写入
            val = "computed_value"
            await r.set(key, val, ex=3600)
            return val
        finally:
            await r.delete(lock_key)
    else:
        # 未获取锁，等待后重试
        await asyncio.sleep(0.1)
        return await get_hot_key(key)
```

---

## 6.3 分布式锁（Redlock 算法）

```python
import time
import uuid
import asyncio
import redis.asyncio as redis

class DistributedLock:
    """基于 Redis 的分布式锁（简化版 Redlock）"""

    def __init__(self, client: redis.Redis, resource: str, ttl_ms: int = 10000):
        self.client = client
        self.resource = resource
        self.ttl_ms = ttl_ms
        self.owner = str(uuid.uuid4())  # 唯一标识，防止误释放

    async def acquire(self, retry: int = 3, delay_ms: float = 100) -> bool:
        """尝试获取锁，支持重试"""
        for _ in range(retry):
            ok = await self.client.set(
                f"lock:{self.resource}", self.owner,
                nx=True, px=self.ttl_ms
            )
            if ok:
                return True
            await asyncio.sleep(delay_ms / 1000)
        return False

    async def release(self) -> bool:
        """Lua 脚本保证原子释放：只释放自己的锁"""
        lua_script = """
        if redis.call('GET', KEYS[1]) == ARGV[1] then
            return redis.call('DEL', KEYS[1])
        else
            return 0
        end
        """
        result = await self.client.eval(lua_script, 1, f"lock:{self.resource}", self.owner)
        return result == 1

# 使用示例
async def critical_section():
    r = redis.Redis(host="localhost", port=6379, db=0)
    lock = DistributedLock(r, "order:pay:10001", ttl_ms=5000)

    if await lock.acquire():
        try:
            print("获取锁成功，执行临界区操作")
            # ... 扣减库存、更新订单等
        finally:
            await lock.release()
    else:
        print("获取锁失败，返回繁忙提示")
    await r.aclose()
```

---

## 6.4 滑动窗口限流

```python
import time
import asyncio
import redis.asyncio as redis

class SlidingWindowRateLimiter:
    """基于 Sorted Set 的滑动窗口限流器"""

    def __init__(self, client: redis.Redis, window_sec: int = 60, max_requests: int = 100):
        self.client = client
        self.window_sec = window_sec
        self.max_requests = max_requests

    async def is_allowed(self, key: str) -> bool:
        """判断请求是否被允许"""
        now = time.time()
        window_start = now - self.window_sec
        pipe = self.client.pipeline()

        # 移除窗口外的过期记录
        pipe.zremrangebyscore(key, 0, window_start)
        # 统计窗口内的请求数
        pipe.zcard(key)
        # 添加当前请求
        pipe.zadd(key, {f"{now}:{id(key)}": now})
        # 设置 key 过期（兜底清理）
        pipe.expire(key, self.window_sec + 10)

        results = await pipe.execute()
        current_count = results[1]  # zcard 返回值

        if current_count >= self.max_requests:
            # 超限，移除刚添加的
            await self.client.zrem(key, f"{now}:{id(key)}")
            return False
        return True

# 使用
async def rate_limit_demo():
    r = redis.Redis(host="localhost", port=6379, db=0)
    limiter = SlidingWindowRateLimiter(r, window_sec=60, max_requests=30)

    for i in range(35):
        allowed = await limiter.is_allowed("api:/users")
        status = "✅ 放行" if allowed else "🚫 限流"
        print(f"  请求 {i+1}: {status}")
    await r.aclose()
```

---

## 6.5 Pipeline 与事务

```python
async def pipeline_demo():
    """Pipeline 批量操作：减少网络往返"""
    r = redis.Redis(host="localhost", port=6379, db=0)

    pipe = r.pipeline(transaction=False)  # 非事务 pipeline，更高吞吐

    for i in range(1000):
        pipe.set(f"batch:demo:{i}", f"value_{i}")

    await pipe.execute()  # 一次性发送 1000 条命令
    print("Pipeline 写入 1000 条完成")

    # 事务模式（WATCH + MULTI）
    pipe_tx = r.pipeline(transaction=True)
    await pipe_tx.multi()
    pipe_tx.incr("counter:a")
    pipe_tx.incr("counter:b")
    results = await pipe_tx.execute()
    print(f"事务结果: {results}")  # [1, 1]
    await r.aclose()
```

---

## 必读资源

| 资源 | 链接 | 说明 |
|------|------|------|
| Redis 官方文档 | https://redis.io/docs/ | 数据结构与命令参考 |
| Redis-Py Async | https://redis-py.readthedocs.io/ | Python 异步客户端 |
| Redis 设计与实现 | https://redisbook.com/ | 底层数据结构原理 |
| Redlock 论文 | https://redis.io/topics/distlock | Martin Kleppmann 的质疑也值得阅读 |
| 《Redis 深度历险》 | — | 国内实战佳作 |
