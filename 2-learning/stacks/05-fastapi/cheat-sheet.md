# FastAPI 速查卡 📋

> 面试前30分钟扫一遍，一页纸覆盖核心考点

## 🏷️ 核心概念速记

| 概念 | 一句话解释 | 常见陷阱 |
|------|-----------|----------|
| FastAPI | 基于 Starlette + Pydantic 的高性能异步 Web 框架，类型注解驱动 | 不是更快，是开发体验快；底层靠 Starlette 和 Uvicorn |
| Depends | 依赖注入系统，自动解析函数参数并注入 | 普通 def 和 async def 混用时注意，依赖可以多层嵌套 |
| Pydantic | 数据校验和序列化库，用类型注解定义 Schema | Pydantic v2 用 Rust 重写，API 有变化(BaseModel→model_validator) |
| async def | 异步路由处理函数，不阻塞事件循环 | 只有 async def 内才能 await，普通 def 会在线程池执行 |
| ASGI | 异步服务器网关接口，WSGI 的升级版，支持 WebSocket/SSE | 传统 WSGI 应用(Flask/Django) 不能直接跑在 ASGI 服务器上 |
| WebSocket | 全双工通信，常用于聊天/实时推送 | 需要手动管理连接生命周期，关闭后要清理 |
| BackgroundTasks | 后台任务，响应返回后执行 | 不适合耗时或重度任务，重型异步用 Celery |
| Middleware | 请求/响应拦截处理，可加日志/CORS/鉴权 | 顺序敏感，注意 async 中间件不要阻塞 |
| Router/APIRouter | 路由分组，支持 prefix/tags/dependencies | 大型应用必须用 APIRouter 拆分模块 |
| 响应模型 (response_model) | 自动过滤字段、校验、文档生成 | 可以配置 response_model_exclude_unset 等 |
| 安全 (Security) | OAuth2、JWT、API Key 等鉴权方案内置 | 复杂鉴权需自定义依赖，Security 只是 Depends 的别名 |

## 🔧 常用命令/API

```python
# 路由定义标准模板
from fastapi import FastAPI, Query, Path, Body, Depends, HTTPException, status
from pydantic import BaseModel, Field
from typing import Annotated

app = FastAPI(title="Demo API", version="1.0.0")

# 路由 + 参数校验 + 响应模型
@app.get("/users/{user_id}", response_model=UserOut)
async def get_user(
    user_id: Annotated[int, Path(ge=1)],
    fields: Annotated[list[str] | None, Query(alias="fields")] = None,
    db: Annotated[Database, Depends(get_db)] = None,
):
    user = await db.get_user(user_id)
    if not user:
        raise HTTPException(status_code=404, detail="User not found")
    return user
```

```python
# Pydantic 模型定义 (v2 风格)
from pydantic import BaseModel, EmailStr, Field, model_validator

class UserCreate(BaseModel):
    name: str = Field(..., min_length=2, max_length=50)
    email: EmailStr
    password: str = Field(..., min_length=8)

    @model_validator(mode="after")
    def check_password_strength(self) -> "UserCreate":
        if not any(c.isdigit() for c in self.password):
            raise ValueError("Password must contain a digit")
        return self

class UserOut(BaseModel):
    id: int
    name: str
    email: EmailStr
```

```python
# 依赖注入（Depends）典型用法
from fastapi import Depends, HTTPException
from typing import Annotated

async def get_current_user(token: str = Depends(oauth2_scheme)) -> User:
    user = await verify_token(token)
    if not user:
        raise HTTPException(status_code=401, detail="Invalid token")
    return user

CurrentUser = Annotated[User, Depends(get_current_user)]

@app.get("/me")
async def read_me(current_user: CurrentUser):
    return current_user
```

```python
# 测试 FastAPI 接口
from httpx import AsyncClient, ASGITransport
import pytest

@pytest.mark.anyio
async def test_create_user():
    async with AsyncClient(
        transport=ASGITransport(app=app), base_url="http://test"
    ) as ac:
        resp = await ac.post("/users", json={"name": "Alice", "email": "a@b.com", "password": "pass1234"})
    assert resp.status_code == 201
    assert resp.json()["name"] == "Alice"
```

```bash
# 常用命令
uvicorn main:app --reload              # 开发启动
uvicorn main:app --host 0.0.0.0 --port 8000 --workers 4  # 生产
fastapi dev main.py                    # CLI 开发模式
```

## 🎯 面试高频 TOP10

1. **Q: FastAPI vs Django 区别？** **A:** FastAPI 轻量异步、类型注解驱动、自动文档、适合微服务/API；Django 全栈大而全、ORM 强大、插件生态丰富、适合单体 Web 应用。
2. **Q: Depends 依赖注入原理？** **A:** 路由函数的参数若声明了 Depends，框架会递归解析依赖树，先执行依赖函数，将结果注入对应参数，支持缓存(单次请求内共享)和嵌套。
3. **Q: ASGI 和 WSGI 区别？** **A:** WSGI 同步单请求单响应模型；ASGI 异步、支持 WebSocket/SSE/HTTP2、生命周期事件；FastAPI 基于 ASGI，可处理长连接和实时场景。
4. **Q: FastAPI 异步陷阱有哪些？** **A:** ① 普通 def 在独立线程池执行，异步不充分；② 同步 IO 库阻塞事件循环；③ 数据库连接池不够用；④ 忘记 await 协程；⑤ 日志/打印阻塞。
5. **Q: Pydantic v2 相比 v1 有什么变化？** **A:** Rust 核心提升性能 5-50x；@validator → @field_validator/@model_validator；Config → model_config；validators 用 mode='before'/'after'等。
6. **Q: 如何实现依赖缓存？** **A:** Depends 默认单次请求内缓存：同一个依赖被多次 Depends 引用，只执行一次；`Depends(use_cache=False)` 可禁用。
7. **Q: FastAPI 如何实现限流？** **A:** 中间件+ Redis 计数器(Sliding Window)、依赖注入(每个路由限流)、slowapi 库集成。
8. **Q: WebSocket 怎么管理？** **A:** 连接建立时存储到全局 dict/Redis，业务处理→异常处理断开→finally 清理；用 WebSocketException 管理错误。
9. **Q: 大规模应用如何组织代码？** **A:** APIRouter 按模块拆分 → 依赖文件集中 → 模型分离(models/) → 服务层提取 → 定时任务(Celery/APScheduler) 独立进程。
10. **Q: FastAPI 文档如何定制？** **A:** OpenAPI 配置(title/version/description)、tags_metadata、summary/description 各路由装饰器、自定义 OpenAPI 函数。

## ⚠️ 常见坑 & 最佳实践

| ❌ 坑 | ✅ 正确做法 |
|-------|------------|
| 路由函数用普通 def 却做 async 操作 | 明确区分：IO 密集用 async def，CPU 密集用普通 def |
| 数据库 session 手动管理混乱 | 用 Depends(get_db) 自动获取/关闭，yield 上下文管理 |
| 异常信息返回给客户端 | 生产环境用 HTTPException 自定义消息，不暴露栈信息 |
| 全局中间件过多/顺序乱 | 按依赖先后排列，CORS 放最前，Auth 放中间，日志放最后 |
| 密码明文存储 | 用 passlib 哈希(BCrypt/SHA256) 存储，永不返回原密码 |
| 响应模型未定义，直接返回 ORM 对象 | 定义 response_model，只暴露需要的字段，隐藏敏感数据 |
| 文件上传无大小限制 | 用 max_size 限制上传大小，避免 OOM |
| 生产用 --reload | 生产环境不加 --reload，用多 worker 配合 nginx |

## 📐 架构设计要点

- **项目结构**：app/ ├── routers/ ├── models/ ├── schemas/ ├── services/ ├── dependencies/ ├── core/ └── main.py
- **分层依赖**：routes → dependencies → services → repositories，数据流向单一。
- **错误处理**：全局异常处理器 `@app.exception_handler`，统一响应格式 `{code, msg, data}`。
- **配置管理**：Pydantic Settings 管理环境变量，通过 `BaseSettings` 读取 `pydantic-settings`。
- **中间件顺序**：CORS → TrustedHost → GZip → Auth → 业务处理 → 日志追踪。
- **部署**：Uvicorn + Gunicorn(进程管理) + Nginx(反向代理/SSL/限流)。

## 🔗 关联技术

- **Python**：async/await、类型注解、装饰器，FastAPI 深度依赖 Python 3.10+ 特性。
- **Pydantic**：FastAPI 的核心依赖，数据校验和序列化基础。
- **SQLAlchemy**：数据库 ORM 配合 FastAPI 使用，异步用 asyncpg 驱动。
- **Celery**：异步任务队列，处理 FastAPI 不适合的重型后台任务。
- **Docker**：Uvicorn 多进程 + Nginx 容器化部署，健康检查与优雅关闭。