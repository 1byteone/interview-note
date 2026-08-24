# 第二十一章：生产部署（P0 精通）

> 📖 **参考资料**：[Gunicorn](https://docs.gunicorn.org/) | [Kubernetes Docs](https://kubernetes.io/docs/) | [Pydantic Settings](https://docs.pydantic.dev/latest/concepts/pydantic_settings/)

---

## 21.1 生产架构总览

```
┌─────────────────────────────────────────────────────────┐
│                    Kubernetes Cluster                     │
│                                                          │
│  ┌──────────────┐    ┌──────────────┐    ┌──────────┐   │
│  │   Ingress    │───▶│  Service A   │───▶│   Redis  │   │
│  │  Controller  │    │  (3 replicas)│    │  Cluster │   │
│  └──────┬───────┘    └──────┬───────┘    └──────────┘   │
│         │                   │                            │
│         │            ┌──────▼───────┐    ┌──────────┐   │
│         │            │  Service B   │───▶│ Postgres │   │
│         │            │  (2 replicas)│    │  (HA)    │   │
│         │            └──────────────┘    └──────────┘   │
│         │                                                │
│  ┌──────▼───────┐    ┌──────────────┐                   │
│  │    HPA       │    │   Health     │                   │
│  │  Auto Scale  │    │   Checker    │                   │
│  └──────────────┘    └──────────────┘                   │
└─────────────────────────────────────────────────────────┘
```

**核心原则**：开发环境用 `uvicorn`，生产环境用 `Gunicorn + Uvicorn worker`，通过环境变量区分配置。

---

## 21.2 环境变量配置 — pydantic-settings

```python
# app/config.py
from functools import lru_cache
from enum import Enum
from pydantic_settings import BaseSettings, SettingsConfigDict


class EnvironmentType(str, Enum):
    DEVELOPMENT = "development"
    TESTING = "testing"
    STAGING = "staging"
    PRODUCTION = "production"


class Settings(BaseSettings):
    """全局配置 — 所有环境变量通过 .env 文件或系统环境变量注入。"""

    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        case_sensitive=False,
        extra="ignore",
    )

    # ── 基础 ──
    app_name: str = "myapp"
    environment: EnvironmentType = EnvironmentType.DEVELOPMENT
    debug: bool = False
    secret_key: str = "CHANGE-ME-IN-PRODUCTION"

    # ── 数据库 ──
    database_url: str = "postgresql+asyncpg://user:pass@localhost:5432/mydb"
    db_pool_size: int = 20
    db_max_overflow: int = 10
    db_pool_recycle: int = 3600

    # ── Redis ──
    redis_url: str = "redis://localhost:6379/0"

    # ── Gunicorn ──
    workers: int = 4
    worker_class: str = "uvicorn.workers.UvicornWorker"
    bind: str = "0.0.0.0:8000"
    timeout: int = 120
    keepalive: int = 5

    @property
    def is_production(self) -> bool:
        return self.environment == EnvironmentType.PRODUCTION

    @property
    def async_database_url(self) -> str:
        return self.database_url


@lru_cache()
def get_settings() -> Settings:
    return Settings()
```

```bash
# .env（生产环境通过 K8s ConfigMap / Secret 注入）
ENVIRONMENT=production
DEBUG=false
SECRET_KEY=your-production-secret-key-here
DATABASE_URL=postgresql+asyncpg://app:secret@pg-cluster:5432/mydb
REDIS_URL=redis://redis-cluster:6379/0
WORKERS=4
DB_POOL_SIZE=20
DB_MAX_OVERFLOW=10
```

---

## 21.3 Gunicorn + Uvicorn 生产配置

```python
# gunicorn.conf.py
import os
import multiprocessing

from app.config import get_settings

settings = get_settings()

# ── 服务器绑定 ──
bind = settings.bind

# ── Worker 配置 ──
workers = settings.workers or multiprocessing.cpu_count() * 2 + 1
worker_class = settings.worker_class
worker_connections = 1000
timeout = settings.timeout
keepalive = settings.keepalive
graceful_timeout = 30

# ── 日志 ──
accesslog = "-"
errorlog = "-"
loglevel = "info" if settings.is_production else "debug"
access_log_format = '%(h)s %(l)s %(u)s %(t)s "%(r)s" %(s)s %(b)s "%(f)s" "%(a)s" %(D)sμs'

# ── 进程管理 ──
preload_app = True
max_requests = 1000          # worker 处理 N 个请求后重启，防内存泄漏
max_requests_jitter = 50     # 随机抖动，避免同时重启
pidfile = "/tmp/gunicorn.pid"

# ── SSL（可选）──
# keyfile = "/etc/ssl/private/server.key"
# certfile = "/etc/ssl/certs/server.crt"


def on_starting(server):
    """服务器启动时触发。"""
    server.log.info("🚀 Gunicorn starting...")


def post_fork(server, worker):
    """Worker fork 后触发。"""
    server.log.info(f"Worker spawned (pid: {worker.pid})")


def pre_exec(server):
    """Master 进程 re-exec 时触发。"""
    server.log.info("Master重新fork中...")
```

```bash
# 启动命令
gunicorn app.main:app -c gunicorn.conf.py

# 或者 Dockerfile 中
CMD ["gunicorn", "app.main:app", "-c", "gunicorn.conf.py"]
```

---

## 21.4 连接池调优

```python
# app/database.py
from sqlalchemy.ext.asyncio import create_async_engine, async_sessionmaker, AsyncSession
from sqlalchemy.orm import DeclarativeBase

from app.config import get_settings

settings = get_settings()

engine = create_async_engine(
    settings.async_database_url,
    pool_size=settings.db_pool_size,        # 保持的连接数
    max_overflow=settings.db_max_overflow,   # 允许临时超出的连接数
    pool_recycle=settings.db_pool_recycle,   # 连接回收时间（秒）
    pool_pre_ping=True,                      # 每次取连接前 ping，剔除死连接
    pool_timeout=30,                         # 等待连接的超时时间
    echo=settings.debug,                     # 开发环境打印 SQL
)

async_session_factory = async_sessionmaker(engine, expire_on_commit=False)


class Base(DeclarativeBase):
    pass


async def get_db() -> AsyncSession:
    async with async_session_factory() as session:
        try:
            yield session
            await session.commit()
        except Exception:
            await session.rollback()
            raise
        finally:
            await session.close()
```

**调优参考表**：

| 参数 | 开发环境 | 生产环境 | 说明 |
|------|---------|---------|------|
| `pool_size` | 5 | 20 | 保持的空闲连接数 |
| `max_overflow` | 3 | 10 | 高峰期允许超出的连接数 |
| `pool_recycle` | 1800 | 3600 | 超过此时间的连接会被回收 |
| `pool_pre_ping` | True | True | 防止使用断开的连接 |
| `pool_timeout` | 30 | 30 | 等待连接超时秒数 |

> ⚠️ **总连接数** = `pool_size` × Pod 数量。Postgres 默认 `max_connections=100`，务必计算好。

---

## 21.5 健康检查端点

```python
# app/health.py
import asyncio
import time
from enum import Enum
from fastapi import APIRouter, Depends
from sqlalchemy import text
from sqlalchemy.ext.asyncio import AsyncSession

from app.database import get_db
from app.config import get_settings

router = APIRouter(tags=["health"])
settings = get_settings()

STARTUP_TIME = time.time()


class HealthStatus(str, Enum):
    HEALTHY = "healthy"
    DEGRADED = "degraded"
    UNHEALTHY = "unhealthy"


@router.get("/health")
async def health_check(db: AsyncSession = Depends(get_db)):
    """K8s liveness/readiness 探针 — 返回 200 或 503。"""
    checks = {}
    overall = HealthStatus.HEALTHY

    # 1. 数据库检查
    try:
        await db.execute(text("SELECT 1"))
        checks["database"] = "ok"
    except Exception as e:
        checks["database"] = f"error: {str(e)}"
        overall = HealthStatus.UNHEALTHY

    # 2. 启动时间
    uptime = time.time() - STARTUP_TIME
    checks["uptime_seconds"] = round(uptime, 2)

    # 3. 配置检查
    checks["environment"] = settings.environment.value

    status_code = 200 if overall == HealthStatus.HEALTHY else 503
    return {"status": overall.value, "checks": checks}


@router.get("/ready")
async def readiness_check(db: AsyncSession = Depends(get_db)):
    """K8s readiness probe — 仅当服务完全就绪时返回 200。"""
    try:
        await db.execute(text("SELECT 1"))
        return {"status": "ready"}
    except Exception:
        return {"status": "not ready"}
```

---

## 21.6 Kubernetes 部署清单

### deployment.yaml

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: myapp
  labels:
    app: myapp
spec:
  replicas: 3
  selector:
    matchLabels:
      app: myapp
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxUnavailable: 1
      maxSurge: 1
  template:
    metadata:
      labels:
        app: myapp
    spec:
      containers:
        - name: myapp
          image: registry.example.com/myapp:v1.0.0
          ports:
            - containerPort: 8000
          envFrom:
            - configMapRef:
                name: myapp-config
            - secretRef:
                name: myapp-secret
          resources:
            requests:
              cpu: "250m"
              memory: "256Mi"
            limits:
              cpu: "1000m"
              memory: "512Mi"
          livenessProbe:
            httpGet:
              path: /health
              port: 8000
            initialDelaySeconds: 15
            periodSeconds: 20
            failureThreshold: 3
          readinessProbe:
            httpGet:
              path: /ready
              port: 8000
            initialDelaySeconds: 5
            periodSeconds: 10
```

### service.yaml

```yaml
apiVersion: v1
kind: Service
metadata:
  name: myapp-service
spec:
  selector:
    app: myapp
  ports:
    - port: 80
      targetPort: 8000
  type: ClusterIP
```

### hpa.yaml（自动扩缩容）

```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: myapp-hpa
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: myapp
  minReplicas: 2
  maxReplicas: 10
  metrics:
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: 70
    - type: Resource
      resource:
        name: memory
        target:
          type: Utilization
          averageUtilization: 80
```

---

## 21.7 部署清单

| # | 检查项 | 命令 / 说明 |
|---|-------|------------|
| 1 | 环境变量 | 确认 `.env` 或 ConfigMap 中所有 `SECRET_*` 已设置 |
| 2 | 数据库迁移 | `alembic upgrade head` 已执行 |
| 3 | 镜像构建 | `docker build -t myapp:v1.0.0 .` 成功 |
| 4 | 镜像扫描 | `trivy image myapp:v1.0.0` 无高危漏洞 |
| 5 | 健康检查 | `curl /health` 返回 200 |
| 6 | 资源限制 | CPU/Memory requests/limits 已设置 |
| 7 | 日志输出 | 结构化 JSON 日志输出到 stdout |
| 8 | SSL/TLS | Ingress 已配置 HTTPS |
| 9 | 备份策略 | 数据库自动备份已配置 |
| 10 | 回滚方案 | `kubectl rollout undo deployment/myapp` 测试通过 |

---

## 必读资源

| 资源 | 链接 | 说明 |
|------|------|------|
| Gunicorn 官方文档 | https://docs.gunicorn.org/ | WSGI 服务器配置参考 |
| Kubernetes 文档 | https://kubernetes.io/docs/ | K8s 部署与运维 |
| Pydantic Settings | https://docs.pydantic.dev/latest/concepts/pydantic_settings/ | 类型安全的环境变量管理 |
| SQLAlchemy 引擎配置 | https://docs.sqlalchemy.org/en/20/core/engines.html | 连接池参数详解 |
| Docker 最佳实践 | https://docs.docker.com/build/building/best-practices/ | 多阶段构建与镜像优化 |
