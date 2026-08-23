# 生产部署

> 适用：🎯 进阶
> 目标：掌握 FastAPI 生产部署的最佳实践，从项目结构到 Docker、Nginx 全流程

---

## 1. 项目结构最佳实践

```
mall-api/
├── app/
│   ├── __init__.py
│   ├── main.py              # FastAPI 应用实例
│   ├── config.py            # 配置管理
│   ├── api/                 # 路由层
│   │   ├── __init__.py
│   │   └── v1/
│   │       ├── __init__.py
│   │       ├── products.py
│   │       ├── orders.py
│   │       └── users.py
│   ├── core/                # 核心功能
│   │   ├── __init__.py
│   │   ├── database.py      # 数据库连接
│   │   ├── dependencies.py  # 公共依赖
│   │   ├── security.py      # 认证/授权
│   │   └── exceptions.py    # 异常处理
│   ├── models/              # Pydantic 模型
│   │   ├── __init__.py
│   │   ├── product.py
│   │   └── order.py
│   ├── services/            # 业务逻辑层
│   │   ├── __init__.py
│   │   ├── product_service.py
│   │   └── order_service.py
│   └── utils/               # 工具函数
│       ├── __init__.py
│       └── logger.py
├── tests/
│   ├── __init__.py
│   ├── conftest.py          # pytest fixtures
│   ├── test_products.py
│   └── test_orders.py
├── Dockerfile
├── docker-compose.yml
├── requirements.txt
├── pyproject.toml
└── .env
```

> Java 对比：类似 Spring Boot 的 `controller/service/repository` 三层架构。FastAPI 推荐按功能模块组织，而非按技术层组织。

### 主入口文件

```python
# app/main.py
from fastapi import FastAPI
from app.api.v1 import products, orders, users
from app.core.exceptions import register_exception_handlers
from app.core.database import lifespan

app = FastAPI(
    title="AI 商城 API",
    version="1.0.0",
    lifespan=lifespan,  # 启动/关闭事件
)

# 注册路由
app.include_router(products.router, prefix="/api/v1", tags=["商品"])
app.include_router(orders.router, prefix="/api/v1", tags=["订单"])
app.include_router(users.router, prefix="/api/v1", tags=["用户"])

# 注册异常处理器
register_exception_handlers(app)
```

---

## 2. Docker 部署

### Dockerfile

```dockerfile
# 多阶段构建
# 第一阶段：构建
FROM python:3.12-slim AS builder

WORKDIR /app

# 安装依赖
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# 第二阶段：运行
FROM python:3.12-slim

WORKDIR /app

# 复制依赖
COPY --from=builder /usr/local/lib/python3.12/site-packages /usr/local/lib/python3.12/site-packages
COPY --from=builder /usr/local/bin /usr/local/bin

# 复制应用代码
COPY . .

# 非 root 用户运行
RUN useradd -m -u 1000 appuser && chown -R appuser:appuser /app
USER appuser

EXPOSE 8000

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
```

### 更精简的 Dockerfile（单阶段）

```dockerfile
FROM python:3.12-slim

WORKDIR /app

# 安装系统依赖
RUN apt-get update && apt-get install -y --no-install-recommends \
    curl \
    && rm -rf /var/lib/apt/lists/*

# 安装 Python 依赖
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY . .

EXPOSE 8000

HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
    CMD curl -f http://localhost:8000/health || exit 1

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
```

### docker-compose.yml

```yaml
version: "3.8"

services:
  api:
    build: .
    ports:
      - "8000:8000"
    environment:
      - DATABASE_URL=postgresql://user:pass@db:5432/mall
      - REDIS_URL=redis://redis:6379/0
    depends_on:
      db:
        condition: service_healthy
      redis:
        condition: service_started
    restart: unless-stopped
    volumes:
      - ./logs:/app/logs

  db:
    image: postgres:16-alpine
    environment:
      - POSTGRES_USER=user
      - POSTGRES_PASSWORD=pass
      - POSTGRES_DB=mall
    volumes:
      - postgres_data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U user -d mall"]
      interval: 10s
      timeout: 5s
      retries: 5

  redis:
    image: redis:7-alpine
    volumes:
      - redis_data:/data

  nginx:
    image: nginx:alpine
    ports:
      - "80:80"
      - "443:443"
    volumes:
      - ./nginx.conf:/etc/nginx/nginx.conf:ro
    depends_on:
      - api

volumes:
  postgres_data:
  redis_data:
```

---

## 3. Nginx 反向代理

```nginx
# nginx.conf
upstream fastapi_backend {
    server api:8000;
    keepalive 32;
}

server {
    listen 80;
    server_name api.mall.com;

    # 请求大小限制
    client_max_body_size 10M;

    # API 反向代理
    location /api/ {
        proxy_pass http://fastapi_backend;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;

        # 长连接配置
        proxy_http_version 1.1;
        proxy_set_header Connection "";
    }

    # WebSocket 代理
    location /ws/ {
        proxy_pass http://fastapi_backend;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;

        # WebSocket 超时
        proxy_read_timeout 86400s;
    }

    # SSE 流式响应（禁用缓冲）
    location /chat/ {
        proxy_pass http://fastapi_backend;
        proxy_set_header Host $host;
        proxy_buffering off;
        proxy_cache off;
        chunked_transfer_encoding on;
        proxy_read_timeout 300s;
    }

    # 静态文件
    location /static/ {
        root /var/www;
        expires 30d;
    }

    # 健康检查
    location /health {
        proxy_pass http://fastapi_backend;
        access_log off;
    }
}
```

---

## 4. Gunicorn + Uvicorn 多进程

生产环境建议使用 `Gunicorn` 作为进程管理器，`Uvicorn` 作为 ASGI worker。

```bash
pip install gunicorn uvicorn
```

```bash
# 启动命令
gunicorn app.main:app \
    --worker-class uvicorn.workers.UvicornWorker \
    --workers 4 \
    --bind 0.0.0.0:8000 \
    --timeout 120 \
    --keep-alive 5 \
    --max-requests 1000 \
    --max-requests-jitter 50 \
    --access-logfile ./logs/access.log \
    --error-logfile ./logs/error.log \
    --log-level info
```

### 参数说明

| 参数 | 说明 | 建议值 |
|------|------|--------|
| `--workers` | worker 进程数 | `2 * CPU核心数 + 1` |
| `--worker-class` | worker 类型 | `uvicorn.workers.UvicornWorker` |
| `--timeout` | 请求超时 | 120 秒 |
| `--keep-alive` | 长连接保持 | 5 秒 |
| `--max-requests` | 最大请求数后重启 | 1000（防内存泄漏） |
| `--max-requests-jitter` | 重启随机偏移 | 50 |

### 使用 Uvicorn 直接部署（单进程）

```bash
uvicorn app.main:app \
    --host 0.0.0.0 \
    --port 8000 \
    --workers 4 \
    --loop uvloop \
    --http httptools \
    --log-level info
```

---

## 5. 日志与监控

### 结构化日志

```python
import json
import logging
from datetime import datetime


class JSONFormatter(logging.Formatter):
    """JSON 日志格式化器"""
    def format(self, record: logging.LogRecord) -> str:
        log_entry = {
            "timestamp": datetime.utcnow().isoformat(),
            "level": record.levelname,
            "logger": record.name,
            "message": record.getMessage(),
        }
        if hasattr(record, "request_id"):
            log_entry["request_id"] = record.request_id
        if record.exc_info:
            log_entry["exception"] = self.formatException(record.exc_info)
        return json.dumps(log_entry)


# 配置
logger = logging.getLogger("mall-api")
handler = logging.StreamHandler()
handler.setFormatter(JSONFormatter())
logger.addHandler(handler)
logger.setLevel(logging.INFO)
```

### 健康检查端点

```python
from fastapi import FastAPI
import psutil

app = FastAPI()


@app.get("/health")
def health_check():
    """健康检查"""
    return {
        "status": "healthy",
        "timestamp": datetime.utcnow().isoformat(),
        "version": "1.0.0",
        "metrics": {
            "cpu_percent": psutil.cpu_percent(),
            "memory_percent": psutil.virtual_memory().percent,
            "disk_usage": psutil.disk_usage("/").percent,
        },
    }


@app.get("/ready")
def readiness_check():
    """就绪检查——检查依赖服务"""
    try:
        # 检查数据库连接
        db_ok = check_database()
        # 检查 Redis 连接
        redis_ok = check_redis()
        return {
            "status": "ready" if db_ok and redis_ok else "not_ready",
            "database": "ok" if db_ok else "error",
            "redis": "ok" if redis_ok else "error",
        }
    except Exception as e:
        return {"status": "error", "message": str(e)}
```

---

## 6. 环境配置

```python
# app/config.py
from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    # 应用
    app_name: str = "AI 商城 API"
    debug: bool = False
    api_prefix: str = "/api/v1"

    # 数据库
    database_url: str
    database_pool_size: int = 10
    database_max_overflow: int = 20

    # Redis
    redis_url: str = "redis://localhost:6379/0"

    # 安全
    secret_key: str
    jwt_algorithm: str = "HS256"
    access_token_expire_minutes: int = 30

    # 限流
    rate_limit_per_minute: int = 60

    class Config:
        env_file = ".env"
        env_file_encoding = "utf-8"
```

---

## 本章小结

| 部署环节 | 方案 | 说明 |
|----------|------|------|
| 项目结构 | 功能模块化 | 类似 Spring Boot 分层 |
| 容器化 | Docker 多阶段构建 | 减小镜像体积 |
| 编排 | docker-compose | 本地和 CI/CD 环境 |
| 反向代理 | Nginx | 负载均衡、SSL、WebSocket |
| 进程管理 | Gunicorn + Uvicorn | 多 worker、优雅重启 |
| 日志 | JSON 结构化日志 | 便于日志收集和分析 |
| 监控 | 健康检查端点 | K8s 探针 / 负载均衡 |

下一章将介绍 AI 商城搜索服务集成。