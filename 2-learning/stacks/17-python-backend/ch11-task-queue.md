# 第十一章：任务队列（P0 精通）

> 📖 **参考资料**：[ARQ Documentation](https://arq-docs.helpmanual.io/) | [Celery Documentation](https://docs.celeryq.dev/) | [Redis Task Queue Patterns](https://redis.io/docs/manual/patterns/) | [FastAPI Background Tasks](https://fastapi.tiangolo.com/tutorial/background-tasks/)

---

## 11.1 后台任务架构

```
┌──────────┐      ┌─────────────┐      ┌──────────────┐
│ FastAPI  │ ───▶ │    Redis    │ ◀─── │  ARQ Worker  │
│(Producer)│ Job  │   (Broker)  │ Pop  │ (Consumer)   │
└──────────┘      └─────┬───────┘      └──────────────┘
                        │
                 ┌──────┴───────┐
                 │ Result/State │  任务状态 + 返回值
                 │   (Redis/DB) │
                 └──────────────┘
```

| 组件 | 职责 | 选型 |
|------|------|------|
| Producer | 提交任务并返回 task_id | FastAPI 端点 → enqueue |
| Broker | 任务队列存储 | Redis（ARQ/Celery 默认） |
| Worker | 异步执行任务（独立进程） | ARQ / Celery |
| Result Backend | 存储结果与状态 | Redis / PostgreSQL |
| Dead Letter Queue | 兜底永久失败任务 | 独立 Redis List / DB 表 |

> **何时用队列**：超过请求生命周期的操作（发邮件、生成报表、慢速第三方调用）都应异步化，避免阻塞 HTTP 请求。

## 11.2 ARQ：异步原生

ARQ 是 async-native 队列，与 FastAPI 同为 asyncio 模型。

```python
# worker.py
import asyncio
from arq import create_pool
from arq.connections import RedisSettings

async def send_email(ctx, user_id: int, subject: str, body: str):
    """模拟发送邮件的耗时 IO"""
    await asyncio.sleep(2)
    return {"status": "sent", "user_id": user_id}

async def generate_report(ctx, report_id: str):
    import random
    if random.random() < 0.3:           # 模拟 30% 失败率
        raise ValueError("Insufficient data")
    return {"report_id": report_id, "status": "completed"}

class WorkerSettings:
    functions = [send_email, generate_report]
    redis_settings = RedisSettings(host="localhost", port=6379)
    max_tries = 3          # 失败任务最多重试 3 次
    job_timeout = 300      # 单任务超时（秒）
    keep_result = 3600     # 结果保留 1 小时
```

```python
# main.py —— FastAPI 中提交任务
pool = await create_pool(RedisSettings())

@app.post("/emails")
async def trigger_email(user_id: int, subject: str, body: str):
    job = await pool.enqueue_job("send_email", user_id, subject, body)
    return {"task_id": job.job_id, "status": "queued"}   # 202 语义
```

> **运行 Worker**：`arq worker.WorkerSettings`。设置 `retry_jobs=True` 后默认开启重试，重试延迟指数递增并可加抖动。

## 11.3 Celery：成熟生态

Celery 历史更久、生态更全：Beat 定时任务、Canvas 工作流（group/chain）、多 Broker、Flower 监控。

```python
# celery_app.py
from celery import Celery

celery_app = Celery(
    "tutorial",
    broker="redis://localhost:6379/0",
    backend="redis://localhost:6379/1",
    include=["tasks"],
)

celery_app.conf.update(
    task_serializer="json",
    accept_content=["json"],
    result_serializer="json",
    timezone="UTC",
    enable_utc=True,
    task_track_started=True,
    task_acks_late=True,          # 失败时给其他 worker 重试机会
    worker_prefetch_multiplier=1, # 每次只预取 1 个任务
    task_annotations={"*": {"rate_limit": "100/m"}},
)
```

```python
# tasks.py
@celery_app.task(bind=True, max_retries=3, default_retry_delay=60)
def send_push_notification(self, user_id: int, message: str):
    try:
        if user_id % 5 == 0:                     # 模拟 20% 失败
            raise ConnectionError("Push service unavailable")
        return {"user_id": user_id, "status": "sent"}
    except Exception as exc:
        raise self.retry(exc=exc)                # 进入重试
```

```bash
celery -A celery_app worker --loglevel=info --concurrency=4
celery -A celery_app beat --loglevel=info   # 定期任务调度
```

| 对比项 | ARQ | Celery |
|--------|-----|--------|
| 异步模型 | 原生 asyncio | 常需配合异步补丁 |
| 学习曲线 | 低 | 较高 |
| 定时任务 | 需额外实现 | Beat 内置 |
| 任务编排 | 手动 | group / chain / chord |
| 监控 | Redis 命令排查 | Flower 面板 |
| 重量级 | 轻 | 重（依赖 Kombu/Billiard） |

## 11.4 重试与指数退避

> 网络抖动、第三方超时是常态——任务必须可重试，且退避策略要防"惊群"。

```python
# ARQ：ctx 提供重试信息，配合 WorkerSettings 指数递增
async def critical_job(ctx, order_id: str):
    result = await call_third_party(order_id)   # 失败时：
    return result                               # 1st: 10s, 2nd: 20s…

class WorkerSettings:
    functions = [critical_job]
    redis_settings = RedisSettings()
    max_tries = 5
    retry_jobs = True
```

```python
# Celery 一键启用指数退避
@celery_app.task(
    bind=True,
    max_retries=5,
    autoretry_for=(ConnectionError, TimeoutError),  # 指定异常自动重试
    retry_backoff=True,        # 指数退避：1s, 2s, 4s, 8s…
    retry_backoff_max=600,     # 上限 10 分钟
    retry_jitter=True,         # 加随机抖动，防惊群
)
def unreliable_task(self, data):
    return risky_operation(data)
```

| 策略 | 延迟序列 | 适用场景 |
|------|----------|----------|
| 固定间隔 | 60s × N | 下游行为可预测 |
| 指数退避 | 1, 2, 4, 8…s | 网络/资源竞争 |
| 指数退避 + 抖动 | 1, 3, 5, 11…s | 高并发重试风暴 |
| 手动 countdown | 按业务 | 特殊重试节奏 |

## 11.5 任务状态追踪

```python
# models/task_record.py
class TaskStatus(str, enum.Enum):
    PENDING = "pending"; RUNNING = "running"
    SUCCESS = "success"; FAILED  = "failed"
    RETRYING = "retrying"

class TaskRecord(Base):
    __tablename__ = "task_records"

    id            = Column(String(36), primary_key=True)   # task_id (UUID)
    task_name     = Column(String(100), nullable=False, index=True)
    status        = Column(Enum(TaskStatus), default=TaskStatus.PENDING, nullable=False)
    args          = Column(JSON, default=list)
    result        = Column(JSON, nullable=True)
    error_message = Column(Text, nullable=True)
    retry_count   = Column(Integer, default=0)
    created_at    = Column(DateTime, default=datetime.utcnow)
    started_at    = Column(DateTime, nullable=True)
    completed_at  = Column(DateTime, nullable=True)
```

```python
# 在 ARQ 任务内同步状态
async def process_order(ctx, order_id: str):
    job_id = ctx["job_id"]
    await set_status(job_id, TaskStatus.RUNNING)
    try:
        result = await do_work(order_id)
        await set_status(job_id, TaskStatus.SUCCESS, result=result)
        return result
    except Exception as e:
        if ctx["job_try"] >= 3:
            await set_status(job_id, TaskStatus.FAILED, error=str(e))
        else:
            await set_status(job_id, TaskStatus.RETRYING, error=str(e))
        raise
```

> 追踪是**排障的前提**：接口 `/tasks/{task_id}` 返回状态供前端轮询，失败可一键重放。

## 11.6 死信队列

超过最大重试次数的任务进入 **Dead Letter Queue（DLQ）**，与主队列隔离，供人工/自动兜底。

```python
# dlq.py
DLQ_KEY = "task_queue:dead_letter"

async def move_to_dlq(redis: Redis, task_name: str, args: tuple, error: str):
    entry = {
        "task_name": task_name,
        "args": list(args),
        "error": error,
        "failed_at": datetime.utcnow().isoformat(),
    }
    await redis.lpush(DLQ_KEY, json.dumps(entry))

async def requeue_from_dlq(redis, target_enqueue, limit: int = 100):
    """从 DLQ 取回任务并重新入队（人工触发兜底）"""
    requeued = 0
    while requeued < limit:
        raw = await redis.rpop(DLQ_KEY)
        if not raw:
            break
        data = json.loads(raw)
        await target_enqueue(data["task_name"], *data["args"])
        requeued += 1
    return {"requeued": requeued}
```

```python
# worker 内集成 DLQ
async def task_with_dlq(ctx, task_id: int):
    try:
        return await do_work(task_id)
    except Exception as e:
        if ctx["job_try"] >= ctx["max_tries"]:
            pool = await create_pool(RedisSettings())
            await move_to_dlq(pool, "task_with_dlq", (task_id,), str(e))
            await pool.close()
        raise
```

| 环节 | 实现 | 说明 |
|------|------|------|
| 主队列 | Redis List（默认） | 正常任务流转 |
| 死信队列 | 独立 Redis List / DB 表 | 隔离失败任务 |
| 监控 | 统计 DLQ 长度 | 超阈值触发告警（如 >50） |
| 兜底 | 人工/定时从 DLQ 重放 | 幂等 + ack 防重复消费 |
| 审计 | 失败快照落库 | 含 error、重试次数、时间戳 |

---

## 必读资源

| 资源 | 说明 |
|------|------|
| [ARQ Documentation](https://arq-docs.helpmanual.io/) | ARQ 异步任务队列官方文档 |
| [Celery Documentation](https://docs.celeryq.dev/en/stable/) | Celery 完整教程与配置参考 |
| [FastAPI Background Tasks](https://fastapi.tiangolo.com/tutorial/background-tasks/) | FastAPI 内置轻量后台任务 |
| [Redis Task Queue Patterns](https://redis.io/docs/manual/patterns/) | 队列/优先级/延迟队列模式 |
| [Celery Best Practices](https://denibertovic.com/posts/celery-best-practices/) | Celery 生产实践建议 |
| [Flower](https://github.com/mher/flower) | Celery 实时监控面板 |
| [Redis Streams](https://redis.io/docs/data-types/streams/) | 用 Stream 实现可靠任务队列（消费组） |