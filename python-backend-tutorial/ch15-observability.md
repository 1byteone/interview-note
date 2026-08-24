# 第十五章：可观测性（P1 进阶）

> 📖 **参考资料**：[OpenTelemetry Python](https://opentelemetry.io/docs/languages/python/) | [structlog](https://www.structlog.org/) | [Prometheus Python Client](https://github.com/prometheus/client_python)

---

## 15.1 可观测性三支柱

生产系统必须回答三个问题：**哪里出了问题？为什么慢？影响多大？** 可观测性（Observability）通过三大支柱给出答案：

```
┌─────────────────────────────────────────────────┐
│              可观测性 Observability                │
├───────────┬───────────────────┬──────────────────┤
│   Logs    │     Metrics       │     Traces       │
│  (日志)    │     (指标)         │     (追踪)        │
├───────────┼───────────────────┼──────────────────┤
│ 具体事件   │ 聚合数值            │ 请求链路           │
│ "2026-08  │ p99=120ms         │ GET /users/1      │
│  24 10:00 │ QPS=1500          │ → authsvc(45ms)   │
│  ERROR    │ error_rate=0.1%   │ → dbsvc(80ms)     │
│  timeout" │                   │ → cachesvc(12ms)  │
├───────────┴───────────────────┴──────────────────┤
│ 工具链：structlog + Prometheus + OpenTelemetry    │
│ 可视化：Grafana                                   │
└─────────────────────────────────────────────────┘
```

| 支柱 | 典型问题 | 工具选型 |
|------|----------|----------|
| Logs | 某个请求报了什么错？ | structlog, Loguru |
| Metrics | 系统整体负载多高？ | Prometheus client |
| Traces | 慢请求卡在哪个环节？ | OpenTelemetry |

---

## 15.2 structlog 结构化日志

传统 `print()` 或 logging 的 `%s` 格式化在查询时极其痛苦。**structlog** 将日志输出为 JSON，直接可被 ELK / Loki 索引。

### 安装与配置

```bash
pip install structlog
```

### 全局配置

```python
# app/logging_config.py
import structlog
import logging
import sys

def setup_logging():
    """配置 structlog 结构化日志"""
    timestamper = structlog.processors.TimeStamper(fmt="iso")

    structlog.configure(
        processors=[
            structlog.stdlib.add_log_level,
            structlog.stdlib.PositionalArgumentsFormatter(),
            timestamper,
            structlog.processors.StackInfoRenderer(),
            structlog.processors.format_exc_info,
            structlog.processors.UnicodeDecoder(),
            structlog.processors.JSONRenderer(),
        ],
        context_class=dict,
        logger_factory=structlog.stdlib.LoggerFactory(),
        wrapper_class=structlog.stdlib.BoundLogger,
        cache_logger_on_first_use=True,
    )

    # 让标准 logging 也走 structlog 格式
    logging.basicConfig(format="%(message)s", stream=sys.stdout, level=logging.INFO)
```

### 使用示例

```python
import structlog

logger = structlog.get_logger()

def create_user(name: str, email: str):
    logger.info("user.create.start", name=name, email=email)
    try:
        # 模拟业务逻辑
        user_id = 42
        logger.info("user.create.success", user_id=user_id, name=name)
        return user_id
    except Exception as e:
        logger.error("user.create.failed", name=name, error=str(e))
        raise
```

输出：

```json
{"event": "user.create.success", "level": "info", "timestamp": "2026-08-24T10:00:00Z", "user_id": 42, "name": "alice"}
```

---

## 15.3 Prometheus 指标

Prometheus 通过拉取（pull）HTTP 端点采集指标。FastAPI 可用 `starlette_exporter` 一行接入。

### 安装

```bash
pip install prometheus-client starlette-exporter
```

### FastAPI 集成

```python
# app/metrics.py
from prometheus_client import Counter, Histogram, Gauge
import time

# ── 指标定义 ──────────────────────────────────────
http_requests_total = Counter(
    "http_requests_total",
    "Total HTTP requests",
    labelnames=["method", "endpoint", "status"],
)

http_request_duration_seconds = Histogram(
    "http_request_duration_seconds",
    "HTTP request duration in seconds",
    labelnames=["method", "endpoint"],
    buckets=[0.005, 0.01, 0.025, 0.05, 0.1, 0.25, 0.5, 1, 2.5, 5, 10],
)

active_requests = Gauge("http_active_requests", "Active requests in-flight")


# ── 中间件 ─────────────────────────────────────────
from starlette.middleware.base import BaseHTTPMiddleware
from starlette.requests import Request

class PrometheusMiddleware(BaseHTTPMiddleware):
    async def dispatch(self, request: Request, call_next):
        method = request.method
        endpoint = request.url.path

        active_requests.inc()
        start = time.perf_counter()

        try:
            response = await call_next(request)
            status = response.status_code
            return response
        except Exception:
            status = 500
            raise
        finally:
            duration = time.perf_counter() - start
            http_requests_total.labels(method=method, endpoint=endpoint, status=status).inc()
            http_request_duration_seconds.labels(method=method, endpoint=endpoint).observe(duration)
            active_requests.dec()
```

### 暴露 /metrics 端点

```python
# app/main.py
from fastapi import FastAPI
from starlette_exporter import PrometheusMiddleware, handle_metrics

app = FastAPI()

# 方案 A：使用 starlette_exporter 内置
app.add_middleware(PrometheusMiddleware)
app.add_route("/metrics", handle_metrics)

# 方案 B：使用自定义中间件（见上文）
# app.add_middleware(PrometheusMiddleware)
# from prometheus_client import make_asgi_app
# app.mount("/metrics", make_asgi_app())
```

---

## 15.4 OpenTelemetry 分布式追踪

当请求跨越多个服务（API → Auth → DB → Cache）时，需要追踪（Trace）串联完整链路。

### 安装

```bash
pip install opentelemetry-distro opentelemetry-exporter-otlp
opentelemetry-bootstrap -a install
```

### 初始化

```python
# app/tracing.py
from opentelemetry import trace
from opentelemetry.sdk.trace import TracerProvider
from opentelemetry.sdk.trace.export import BatchSpanProcessor
from opentelemetry.exporter.otlp.proto.http.trace_exporter import OTLPSpanExporter
from opentelemetry.sdk.resources import SERVICE_NAME, Resource

def setup_tracing(service_name: str = "python-backend"):
    """配置 OpenTelemetry 导出到 Jaeger 或 Grafana Tempo"""
    resource = Resource(attributes={SERVICE_NAME: service_name})

    provider = TracerProvider(resource=resource)
    processor = BatchSpanProcessor(
        OTLPSpanExporter(endpoint="http://localhost:4318/v1/traces")
    )
    provider.add_span_processor(processor)
    trace.set_tracer_provider(provider)

    return trace.get_tracer(service_name)
```

### FastAPI 自动埋点

```python
from opentelemetry.instrumentation.fastapi import FastAPIInstrumentor

app = FastAPI()
setup_tracing()

# 自动为所有路由添加追踪
FastAPIInstrumentor.instrument_app(app)
```

### 手动埋点

```python
from opentelemetry import trace

tracer = trace.get_tracer(__name__)

async def get_user(user_id: int):
    with tracer.start_as_current_span("get_user") as span:
        span.set_attribute("user.id", user_id)
        # 数据库查询...
        span.add_event("db.query.start")
        user = await db.fetch_one(...)
        span.add_event("db.query.end")
        return user
```

---

## 15.5 Request ID 中间件

为每个请求生成唯一 ID，串联日志、指标和追踪。

```python
# app/request_id.py
import uuid
from contextvars import ContextVar
from starlette.middleware.base import BaseHTTPMiddleware
from starlette.requests import Request

# 在异步上下文中保存 request_id
request_id_var: ContextVar[str] = ContextVar("request_id", default="")

def get_request_id() -> str:
    return request_id_var.get()

class RequestIDMiddleware(BaseHTTPMiddleware):
    async def dispatch(self, request: Request, call_next):
        # 优先从 X-Request-ID 头读取（上游透传）
        rid = request.headers.get("X-Request-ID", str(uuid.uuid4()))
        request_id_var.set(rid)

        response = await call_next(request)
        response.headers["X-Request-ID"] = rid
        return response
```

在 structlog 中自动绑定：

```python
# 在 logging_config.py 中
from app.request_id import request_id_var, get_request_id

structlog.configure(
    processors=[
        # ... 其他 processors
        structlog.processors.add_log_level,
        # 自动注入 request_id
        lambda _, __, event_dict: {**event_dict, "request_id": get_request_id()},
        structlog.processors.JSONRenderer(),
    ],
)
```

---

## 15.6 Grafana Dashboard 面板

以下 JSON 片段可作为 Grafana 导入的 Dashboard 模型（简化版）：

```json
{
  "title": "Python Backend Dashboard",
  "panels": [
    {
      "title": "QPS / 请求速率",
      "type": "graph",
      "targets": [{
        "expr": "rate(http_requests_total[1m])",
        "legendFormat": "{{method}} {{endpoint}}"
      }]
    },
    {
      "title": "P99 延迟",
      "type": "heatmap",
      "targets": [{
        "expr": "histogram_quantile(0.99, rate(http_request_duration_seconds_bucket[5m]))",
        "legendFormat": "p99"
      }]
    },
    {
      "title": "错误率",
      "type": "stat",
      "targets": [{
        "expr": "sum(rate(http_requests_total{status=~'5..'}[5m])) / sum(rate(http_requests_total[5m]))"
      }]
    },
    {
      "title": "活跃请求数",
      "type": "graph",
      "targets": [{
        "expr": "http_active_requests"
      }]
    }
  ]
}
```

---

## 必读资源

| 资源 | 链接 |
|------|------|
| structlog 官方文档 | https://www.structlog.org/en/stable/ |
| Prometheus 最佳实践 | https://prometheus.io/docs/practices/histograms/ |
| OpenTelemetry Python | https://opentelemetry.io/docs/languages/python/ |
| Grafana 仪表盘入门 | https://grafana.com/docs/grafana/latest/dashboards/ |
| 建议阅读 | *"Observability Engineering"* — Charity Majors 等 |