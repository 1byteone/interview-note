# 第十三章：Docker、Linux 与 Nginx（P1 进阶）

> 📖 **参考资料**：[Dockerfile Best Practices](https://docs.docker.com/develop/develop-images/dockerfile_best-practices/) | [Docker Compose](https://docs.docker.com/compose/) | [Nginx Docs](https://nginx.org/en/docs/)

## 13.1 多阶段 Dockerfile

多阶段构建（Multi-stage Build）将编译阶段与运行阶段分离，最终镜像只包含运行时必需文件，体积可缩减 **60%~90%**。

```dockerfile
# Dockerfile — 多阶段构建示例
# ============ Stage 1: 构建依赖 ============
FROM python:3.12-slim AS builder

WORKDIR /app

# 利用缓存层，先复制依赖文件
COPY pyproject.toml poetry.lock ./
RUN pip install poetry && \
    poetry config virtualenvs.create false && \
    poetry install --no-dev --no-interaction

# 复制源码
COPY . .
RUN poetry build -f wheel
# 或者不用 poetry：
# RUN pip install --prefix=/install .


# ============ Stage 2: 运行时镜像 ============
FROM python:3.12-slim AS runtime

# 安全：创建非 root 用户
RUN groupadd -r appuser && useradd -r -g appuser appuser

WORKDIR /app

# 从 builder 阶段复制编译产物
COPY --from=builder /app/dist/*.whl /tmp/
RUN pip install /tmp/*.whl && rm -rf /tmp/*.whl

COPY --from=builder /app/src /app/src

# 安全：切换到非 root
USER appuser

EXPOSE 8000

HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
    CMD python -c "import urllib.request; urllib.request.urlopen('http://localhost:8000/health')"

CMD ["uvicorn", "src.main:app", "--host", "0.0.0.0", "--port", "8000"]
```

**构建与运行：**

```bash
# 构建镜像（带构建参数，输出阶段名）
docker build --target runtime -t myapp:latest .

# 查看镜像大小对比
docker images myapp
# REPOSITORY   TAG      SIZE
# myapp        latest   89MB    ← runtime 阶段
# <none>       <none>   850MB   ← builder 阶段（自动清理）
```

## 13.2 docker-compose 编排

完整编排 Postgres + Redis + Nginx + Worker 四个服务：

```yaml
# docker-compose.yml
version: "3.9"

services:
  # ========== 数据库 ==========
  postgres:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: myapp
      POSTGRES_USER: admin
      POSTGRES_PASSWORD: ${DB_PASSWORD:-changeme}
    volumes:
      - pgdata:/var/lib/postgresql/data
      - ./init.sql:/docker-entrypoint-initdb.d/init.sql:ro
    ports:
      - "5432:5432"
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U admin -d myapp"]
      interval: 10s
      timeout: 5s
      retries: 5
    restart: unless-stopped

  # ========== 缓存 ==========
  redis:
    image: redis:7-alpine
    command: redis-server --maxmemory 256mb --maxmemory-policy allkeys-lru
    ports:
      - "6379:6379"
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 10s
      timeout: 3s
      retries: 5
    restart: unless-stopped

  # ========== 应用服务 ==========
  api:
    build:
      context: .
      dockerfile: Dockerfile
      target: runtime
    environment:
      DATABASE_URL: postgresql://admin:${DB_PASSWORD:-changeme}@postgres:5432/myapp
      REDIS_URL: redis://redis:6379/0
      ENVIRONMENT: production
    depends_on:
      postgres:
        condition: service_healthy
      redis:
        condition: service_healthy
    expose:
      - "8000"
    restart: unless-stopped

  # ========== 后台 Worker ==========
  worker:
    build:
      context: .
      dockerfile: Dockerfile
      target: runtime
    environment:
      DATABASE_URL: postgresql://admin:${DB_PASSWORD:-changeme}@postgres:5432/myapp
      REDIS_URL: redis://redis:6379/0
    depends_on:
      postgres:
        condition: service_healthy
      redis:
        condition: service_healthy
    command: ["celery", "-A", "src.tasks", "worker", "--loglevel=info", "--concurrency=4"]
    restart: unless-stopped

  # ========== 反向代理 ==========
  nginx:
    image: nginx:alpine
    ports:
      - "80:80"
      - "443:443"
    volumes:
      - ./nginx.conf:/etc/nginx/nginx.conf:ro
      - ./certs:/etc/nginx/certs:ro
    depends_on:
      - api
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost/health"]
      interval: 15s
      timeout: 5s
      retries: 3
    restart: unless-stopped

volumes:
  pgdata:
    driver: local
```

**常用操作：**

```bash
# 启动全部服务（后台运行）
docker compose up -d

# 查看所有服务状态和健康检查
docker compose ps
# NAME      IMAGE              STATUS                  PORTS
# postgres  postgres:16-alpine  Up (healthy)           0.0.0.0:5432->5432
# redis     redis:7-alpine     Up (healthy)           0.0.0.0:6379->6379
# api       myapp:latest       Up                      0.0.0.0:8000->8000
# nginx     nginx:alpine       Up (healthy)           0.0.0.0:80->80
# worker    myapp:latest       Up

# 实时查看日志（按服务过滤）
docker compose logs -f api
docker compose logs --tail=50 postgres

# 重建并重启某个服务
docker compose up -d --build api
```

## 13.3 Nginx 反向代理与 SSL

```nginx
# nginx.conf
worker_processes auto;

events {
    worker_connections 1024;
}

http {
    # ========== 基础配置 ==========
    sendfile        on;
    tcp_nopush      on;
    keepalive_timeout 65;
    client_max_body_size 10m;

    # ========== 上游服务 ==========
    upstream api_backend {
        least_conn;                      # 最少连接负载均衡
        server api:8000 max_fails=3 fail_timeout=30s;
        keepalive 32;
    }

    # ========== HTTP → HTTPS 重定向 ==========
    server {
        listen 80;
        server_name example.com www.example.com;

        # Let's Encrypt 验证路径
        location /.well-known/acme-challenge/ {
            root /var/www/certbot;
        }

        location / {
            return 301 https://$host$request_uri;
        }
    }

    # ========== HTTPS 主站 ==========
    server {
        listen 443 ssl http2;
        server_name example.com www.example.com;

        # SSL 证书
        ssl_certificate     /etc/nginx/certs/fullchain.pem;
        ssl_certificate_key /etc/nginx/certs/privkey.pem;
        ssl_protocols       TLSv1.2 TLSv1.3;
        ssl_ciphers         HIGH:!aNULL:!MD5;
        ssl_prefer_server_ciphers on;

        # 安全头
        add_header Strict-Transport-Security "max-age=31536000; includeSubDomains" always;
        add_header X-Content-Type-Options nosniff;
        add_header X-Frame-Options DENY;

        # 代理到 FastAPI / Flask
        location / {
            proxy_pass         http://api_backend;
            proxy_set_header   Host $host;
            proxy_set_header   X-Real-IP $remote_addr;
            proxy_set_header   X-Forwarded-For $proxy_add_x_forwarded_for;
            proxy_set_header   X-Forwarded-Proto $scheme;
            proxy_http_version 1.1;
            proxy_set_header   Connection "";
        }

        # 健康检查端点（供 Nginx healthcheck 使用）
        location /health {
            proxy_pass http://api_backend/health;
            access_log off;
        }

        # 静态文件缓存
        location /static/ {
            alias /var/www/static/;
            expires 30d;
            add_header Cache-Control "public, immutable";
        }
    }
}
```

## 13.4 健康检查

Docker 健康检查是编排系统判断服务是否可用的关键机制：

| 检查方式 | 命令示例 | 适用场景 |
|----------|----------|----------|
| HTTP 检查 | `curl -f http://localhost:8000/health` | Web 服务（FastAPI / Flask） |
| TCP 检查 | `pg_isready -U admin` | 数据库（Postgres） |
| 命令检查 | `redis-cli ping` | 缓存（Redis） |
| 自定义脚本 | `python /app/check.py` | 复杂依赖检查 |

**Python 应用健康检查端点：**

```python
# src/health.py
import asyncio
from fastapi import APIRouter, Response

router = APIRouter()


@router.get("/health")
async def health_check(response: Response):
    """Docker HEALTHCHECK 用端点"""
    checks = {}
    overall = True

    # 检查数据库连接
    try:
        # await db.execute("SELECT 1")
        checks["database"] = "ok"
    except Exception as e:
        checks["database"] = f"error: {e}"
        overall = False

    # 检查 Redis 连接
    try:
        # await redis_client.ping()
        checks["redis"] = "ok"
    except Exception as e:
        checks["redis"] = f"error: {e}"
        overall = False

    if not overall:
        response.status_code = 503

    return {"status": "healthy" if overall else "degraded", "checks": checks}
```

## 13.5 常用 Linux 命令速查

### 文件与目录

| 命令 | 说明 | 示例 |
|------|------|------|
| `find` | 递归查找文件 | `find /var/log -name "*.log" -mtime -1` |
| `grep` | 内容搜索 | `grep -rn "ERROR" /var/log/app/` |
| `tar` | 压缩/解压 | `tar -czf archive.tar.gz ./data` |
| `chmod` | 修改权限 | `chmod 644 config.yml` |
| `du -sh` | 查看目录大小 | `du -sh /var/lib/docker` |

### 进程与系统

| 命令 | 说明 | 示例 |
|------|------|------|
| `top` / `htop` | 实时进程监控 | `htop -d 5`（刷新间隔 0.5s） |
| `ps aux` | 列出所有进程 | `ps aux \| grep uvicorn` |
| `ss -tlnp` | 查看监听端口 | `ss -tlnp \| grep :8000` |
| `df -h` | 磁盘使用率 | `df -h / /var` |
| `free -h` | 内存使用 | `free -h` |

### Docker 相关

| 命令 | 说明 | 示例 |
|------|------|------|
| `docker exec` | 进入容器 | `docker exec -it postgres psql -U admin` |
| `docker stats` | 容器资源监控 | `docker stats --no-stream` |
| `docker system prune` | 清理无用资源 | `docker system prune -af --volumes` |
| `docker logs` | 查看容器日志 | `docker logs --tail 100 -f api` |

## 必读资源

| 资源 | 链接 | 说明 |
|------|------|------|
| Dockerfile 最佳实践 | https://docs.docker.com/develop/develop-images/dockerfile_best-practices/ | 官方镜像构建优化指南 |
| Docker Compose 文档 | https://docs.docker.com/compose/ | 多服务编排完整参考 |
| Nginx 配置指南 | https://nginx.org/en/docs/ | Nginx 官方文档 |
| Let's Encrypt | https://letsencrypt.org/ | 免费 SSL 证书申请 |
| 《Docker — 从入门到实践》 | https://yeasy.gitbook.io/docker_practice/ | 开源中文 Docker 教程 |
| 《鸟哥的 Linux 私房菜》 | https://linux.vbird.org/ | Linux 系统管理经典教材 |
