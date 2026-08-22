# Python 场景题

> 🎯 面试冲刺 | 真实项目场景

场景题考察的不只是语法，而是**你把 Python 能力应用到实际工程的能力**。每个题都从"分析 → 方案 → 代码 → 面试加分点"的结构来解。

---

## 场景一：大量数据处理

### 题目

商品数据有 2000 万条（每条含 title、price、category、brand 等字段），存储在 CSV 文件中。需要统计各品类平均价格，并找出价格最高的 100 个商品。**内存只有 8GB**，如何高效处理？

### 分析

- CSV 约 2-3GB，不能一次性全部读入内存
- 2000 万条 × 20 字段，直接 `pd.read_csv()` 可能 OOM

### 方案：分块处理

```python
import pandas as pd

CHUNK_SIZE = 1_000_000

# 1. 分块读取，避免 OOM
chunks = pd.read_csv("products.csv", chunksize=CHUNK_SIZE)

# 2. 聚合状态累积
category_sum = {}
category_count = {}
top_items = []  # 维护 Top 100 候选

for i, chunk in enumerate(chunks):
    # 只保留需要的列，减少内存
    chunk = chunk[["title", "price", "category"]]

    # 类型压缩
    chunk["price"] = pd.to_numeric(chunk["price"], downcast="float")

    # 按品类累计总和与数量
    grouped = chunk.groupby("category")["price"].agg(["sum", "count"])
    for cat, (s, c) in grouped.iterrows():
        category_sum[cat] = category_sum.get(cat, 0) + s
        category_count[cat] = category_count.get(cat, 0) + c

    # 维护全局 Top 100
    top_items.extend(chunk.nlargest(100, "price")[["title", "price"]].values.tolist())
    top_items = sorted(top_items, key=lambda x: x[1], reverse=True)[:100]

    if i % 10 == 0:
        print(f"已处理 {i * CHUNK_SIZE} 条...")

# 3. 汇总输出
avg_price = {cat: category_sum[cat] / category_count[cat]
             for cat in category_sum}
top100 = top_items

print("各品类平均价格:", avg_price)
print("价格 Top 100:", top100)
```

### 面试加分点

- 主动说 `dtype` 压缩、只取需要的列，体现内存意识
- 提及 `pyarrow` 或 `modin`（并行 pandas）作为更大数据量的备选
- 知道分块、流式处理是"大数据"的通用思路（不只是 Python 独有）

---

## 场景二：API 性能优化

### 题目

一个 Python API 服务每天调用量 100 万+。当前 QPS 只有 50，响应时间 P99 为 800ms。用户反映搜索接口很慢，如何优化？

### 分析

先测量，再优化。80% 的性能问题可以通过定位 → 针对性优化解决。

### 渐进的优化路线

```python
import time
import asyncio
from functools import lru_cache

# Step 1: 定位瓶颈 —— 用装饰器测量每个环节
def timing(func):
    async def wrapper(*args, **kwargs):
        start = time.perf_counter()
        result = await func(*args, **kwargs)
        elapsed = (time.perf_counter() - start) * 1000
        if elapsed > 50:  # 只记录慢请求
            print(f"SLOW {func.__name__}: {elapsed:.1f}ms")
        return result
    return wrapper

# Step 2: 数据库查询优化
# 1. 加索引：WHERE 条件列、JOIN 列、ORDER BY 列
# 2. 只查需要的字段：SELECT title, price FROM ...
# 3. 分页拉取，避免一次性返回 10000 条

# Step 3: 增加缓存层（最有效的优化）
@lru_cache(maxsize=1024)
def get_category_name(category_id: int) -> str:
    """热点数据缓存（进程内）"""
    # SELECT name FROM categories WHERE id = ?
    return db_query(category_id)

# 分布式缓存用 Redis（见 07-redis 技术栈）
async def search_with_cache(query: str):
    cache_key = f"search:{query}"
    cached = await redis.get(cache_key)
    if cached:
        return json.loads(cached)  # 缓存命中，直接返回
    result = await search_engine(query)
    await redis.setex(cache_key, 300, json.dumps(result))  # 缓存 5 分钟
    return result

# Step 4: 异步化 —— 把同步阻塞变成并发等待
async def handle_request(query: str):
    # 并行执行两个独立查询
    es_result, db_result = await asyncio.gather(
        search_es(query),
        get_recommendations(query),
    )
    return merge(es_result, db_result)
```

### 优化效果预估

| 优化手段 | 预期提升 |
|---|---|
| 加索引 + 只查必要字段 | QPS ×3~5 |
| Redis 缓存热点 | QPS ×5~20 |
| 异步化（并发 I/O） | 单请求响应时间 ↓60% |
| 连接池（DB/Redis） | 避免连接抖动 |

### 面试加分点

- 强调"先测量后优化"，用 cProfile / 慢日志定位
- 能说清楚"为什么加索引能提升查询"（B+ 树、索引覆盖）
- 缓存策略：TTL、缓存穿透（空值缓存）、缓存雪崩（过期时间加随机抖动）
- 知道 Python web 框架的性能差异（FastAPI ≈ Starlette 底层，比 Flask/Django 快）

---

## 场景三：内存泄漏排查

### 题目

线上 Python 服务运行几天后内存持续增长，最终 OOM 被 k8s 杀掉重启。如何排查？

### 标准排查流程

```python
# Step 1: 确认是否真的泄露（对比不同时间点的内存）
# 观察：内存一直涨不回落 → 泄露；涨到峰值回落 → 正常波动

# Step 2: 用 tracemalloc 定位
import tracemalloc
import objgraph

tracemalloc.start(25)  # 只跟踪前 25 层调用栈

def run_service():
    # 模拟服务主循环
    while True:
        handle_request()
        # 每 100 次请求输出一次内存快照
        if request_count % 100 == 0:
            snapshot = tracemalloc.take_snapshot()
            top = snapshot.statistics("lineno")
            print("\n=== Top 内存占用 ===")
            for stat in top[:10]:
                print(stat)

# 输出示例:
# D:\code\service.py:137: 52.4 MiB
# D:\code\cache.py:22:  31.2 MiB
# ← 定位到具体文件和行号!

# Step 3: 分析引用链（找到为什么没被释放）
objgraph.show_most_common_types(limit=10)
# dict   ~ 数量暴增的标准信号

# 找谁在引用这个对象
objgraph.show_backrefs(some_object, max_depth=8)
```

### 高频泄露原因对照表

| 线索 | 常见根因 |
|---|---|
| `dict` 数量暴增 | 全局 dict 缓存没清、循环引用无法回收 |
| 每个请求都新增对象 | 长生命周期对象持有 request 引用 |
| 线程数缓慢增长 | 线程池没复用、漏关连接 |
| 事件循环 tasks 增多 | asyncio task 没被 await 完（悬挂任务） |

```python
# 典型案发现场

# 案例 1: 全局缓存无限增长
CACHE = {}
def get_price(product_id):
    if product_id not in CACHE:
        CACHE[product_id] = query_db(product_id)  # 永不清理！
    return CACHE[product_id]
# 修复: 用 lru_cache(maxsize=10000) 或 Redis 代替

# 案例 2: asyncio 悬挂任务（最隐蔽）
async def handler():
    # 错误：创建 task 后没持有引用
    asyncio.create_task(long_job())  # 任务泄漏！
    return "ok"
# 修复: 保存到全局 set，task.add_done_callback(set.discard)

# 案例 3: 缓存键无界增长
# 用商品 ID 做 key，商品 SKU 上千万 → 必然 OOM
```

### 标准答案模板（背诵）

> "我会先用 `tracemalloc` 定位分配最密集的代码位置；再用 `objgraph` 分析对象引用链，确认是'该回收没回收'还是'根本没释放'；最后针对根因修复：全局缓存加 TTL 或上限、asyncio 任务登记管理、连接池复用。修复后部署观察一星期，确认内存曲线平稳。"

### 面试加分点

- 能区分"内存峰值波动"和"直线增长"（后者才是真正的泄露）
- 知道 `objgraph.show_backrefs` 用于找引用链
- 会主动提 Redis/外部缓存（生产环境的正确姿势）
- 了解 k8s OOMKilled 与 `memory_limit` 的关系

---

## 通用答题框架

场景题没有标准答案，但有标准套路：

1. **先问清约束**：数据量多大？延迟要求？可用资源？
2. **先测量再优化**：用数据说话，不做盲目的"过早优化"
3. **优先级排序**：索引 → 缓存 → 异步 → 架构升级
4. **给出可验证的改进计划**：明确要观测的指标（QPS、P99、内存曲线）
5. **考虑副作用**：缓存一致性、冷启动、故障恢复

接下来：进入 [coding.md](coding.md) 手写代码题。