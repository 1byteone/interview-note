# 附录 B：速查手册

> 开发日常高频查阅，建议收藏后随时 `Ctrl+F`。

---

## B.1 Python 版本与关键特性

| 版本 | 发布年份 | 关键特性 |
|------|----------|----------|
| 3.9 | 2020 | 字典合并运算符 `\|`、类型提示 `list[int]` |
| 3.10 | 2021 | **结构化模式匹配** `match/case`、更好的错误消息 |
| 3.11 | 2022 | `ExceptionGroup`、性能提升 10-60%、`Self` 类型 |
| 3.12 | 2023 | **f-string 限制放宽**、`type` 语句、Per-Interpreter GIL |
| 3.13 | 2024 | **实验性 free-threading** (no-GIL)、改进 REPL、JIT 编译器原型 |
| 3.14 | 2025 | **默认延迟引用计数**、`@型` 装饰器、`typing` 增强 |

> **本教程推荐**：Python **3.12+**，搭配 `uv` 管理虚拟环境。

## B.2 核心依赖版本

| 包名 | 最低版本 | 用途 |
|------|----------|------|
| `fastapi` | ≥ 0.115 | Web 框架（含 Starlette、Pydantic V2） |
| `uvicorn[standard]` | ≥ 0.30 | ASGI 服务器（支持 uvloop） |
| `sqlalchemy` | ≥ 2.0 | ORM + Core，async 原生支持 |
| `alembic` | ≥ 1.13 | 数据库迁移管理 |
| `pydantic` | ≥ 2.7 | 数据验证、序列化、Settings 管理 |
| `httpx` | ≥ 0.27 | 异步 HTTP 客户端（测试 / 外部调用） |
| `asyncpg` | ≥ 0.29 | PostgreSQL 异步驱动（性能首选） |
| `redis[hiredis]` | ≥ 5.0 | Redis 异步客户端 + C 加速解析 |
| `structlog` | ≥ 24.1 | 结构化日志 |
| `pytest` | ≥ 8.0 | 测试框架 |
| `pytest-asyncio` | ≥ 0.23 | 异步测试支持 |
| `ruff` | ≥ 0.6 | Linter + Formatter（替代 flake8/black） |
| `mypy` | ≥ 1.10 | 静态类型检查 |

## B.3 HTTP 状态码速查

| 状态码 | 含义 | 使用场景 |
|--------|------|----------|
| `200` | OK | 成功 GET / PATCH / PUT |
| `201` | Created | 成功 POST 创建资源 |
| `204` | No Content | 成功 DELETE（无返回体） |
| `400` | Bad Request | 请求参数格式错误、校验失败 |
| `401` | Unauthorized | 未提供认证凭证或 Token 过期 |
| `403` | Forbidden | 已认证但无权限访问该资源 |
| `404` | Not Found | 资源不存在 |
| `409` | Conflict | 资源冲突（如重复创建） |
| `422` | Unprocessable Entity | 参数格式正确但语义无效（FastAPI 默认） |
| `429` | Too Many Requests | 触发限流，需稍后重试 |
| `500` | Internal Server Error | 服务器未捕获异常 |
| `502` | Bad Gateway | 上游服务无响应（网关场景） |
| `503` | Service Unavailable | 服务暂时过载或维护中 |

## B.4 Redis 命令速查

| 命令 | 语法示例 | 描述 |
|------|----------|------|
| `SET` | `SET key value EX 3600` | 设置键值，`EX` 指定过期秒数 |
| `GET` | `GET key` | 获取值，不存在返回 `nil` |
| `DEL` | `DEL key [key ...]` | 删除一个或多个键 |
| `EXISTS` | `EXISTS key` | 检查键是否存在 |
| `EXPIRE` | `EXPIRE key 3600` | 为已有键设置过期时间 |
| `HSET` | `HSET hash field value` | 设置哈希字段 |
| `HGET` | `HGET hash field` | 获取哈希字段值 |
| `HGETALL` | `HGETALL hash` | 获取哈希所有字段 |
| `LPUSH` | `LPUSH queue item` | 向列表左侧插入元素 |
| `RPOP` | `RPOP queue` | 从列表右侧弹出元素 |
| `SADD` | `SADD set member` | 向集合添加成员 |
| `ZRANGE` | `ZRANGE key 0 -1 WITHSCORES` | 获取有序集合范围及分数 |
| `INCR` | `INCR rate_limit:key` | 原子自增（限流计数器） |
| `PING` | `PING` | 连接保活检测 |

## B.5 FastAPI 常用模式

### 路由与依赖注入

```python
from fastapi import FastAPI, Depends, Query

app = FastAPI()

async def get_db():
    async with AsyncSession(engine) as session:
        yield session

@app.get("/items/", response_model=list[ItemOut])
async def list_items(
    skip: int = Query(0, ge=0),
    limit: int = Query(20, le=100),
    db: AsyncSession = Depends(get_db),
):
    ...
```

### 异常处理

```python
from fastapi import HTTPException

@app.get("/items/{item_id}")
async def get_item(item_id: int, db=Depends(get_db)):
    item = await db.get(Item, item_id)
    if not item:
        raise HTTPException(status_code=404, detail="Item not found")
    return item
```

### 后台任务

```python
from fastapi import BackgroundTasks

def send_email(email: str, body: str):
    ...

@app.post("/send-notification/")
async def notify(email: str, bg: BackgroundTasks):
    bg.add_task(send_email, email, "Welcome!")
    return {"status": "queued"}
```

### 分页响应模型

```python
from pydantic import BaseModel

class PaginatedResponse(BaseModel):
    items: list[ItemOut]
    total: int
    page: int
    page_size: int
```

## B.6 Docker 常用命令

| 命令 | 示例 | 描述 |
|------|------|------|
| `docker build` | `docker build -t myapp:latest .` | 构建镜像 |
| `docker run` | `docker run -d -p 8000:8000 myapp` | 后台运行容器 |
| `docker compose up` | `docker compose up -d --build` | 启动 Compose 服务 |
| `docker compose down` | `docker compose down -v` | 停止并删除卷 |
| `docker compose logs` | `docker compose logs -f api` | 实时查看日志 |
| `docker ps` | `docker ps -a` | 查看所有容器 |
| `docker exec` | `docker exec -it <id> bash` | 进入容器 Shell |
| `docker images` | `docker images` | 列出本地镜像 |
| `docker rmi` | `docker rmi <image>` | 删除镜像 |
| `docker system prune` | `docker system prune -af` | 清理无用资源（慎用） |

## B.7 Git 常用命令

| 命令 | 示例 | 描述 |
|------|------|------|
| `git clone` | `git clone --depth 1 <url>` | 浅克隆（节省带宽） |
| `git branch` | `git branch -a` | 列出所有分支 |
| `git checkout` | `git checkout -b feature/x` | 创建并切换分支 |
| `git add` | `git add -p` | 交互式暂存（逐块选择） |
| `git commit` | `git commit -m "feat: add endpoint"` | 提交（遵循 Conventional Commits） |
| `git push` | `git push -u origin HEAD` | 推送并设置上游 |
| `git pull` | `git pull --rebase` | 拉取并 rebase（保持线性） |
| `git log` | `git log --oneline --graph -10` | 可视化提交历史 |
| `git stash` | `git stash push -m "WIP"` | 保存工作区 |
| `git revert` | `git revert <commit>` | 安全回退单个提交 |
| `git rebase` | `git rebase -i HEAD~3` | 交互式变基（整理提交） |
| `git diff` | `git diff --cached` | 查看已暂存变更 |

---

> **提示**：将此页加入书签，开发时 `Ctrl+F` 搜索关键词即可快速定位。
