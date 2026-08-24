# 第二章：FastAPI — 现代 Python API 框架（P0 精通）

> 📖 **参考资料**：[FastAPI Best Practices (Auth0)](https://auth0.com/blog/fastapi-best-practices/) | [FastAPI Best Practices (GitHub)](https://github.com/zhanymkanov/fastapi-best-practices) | [Production-Ready FastAPI Template](https://www.reddit.com/r/Python/comments/1ob3xmq/productionready_fastapi_template_with_cicd_and/) | [FastAPI 官方文档](https://fastapi.tiangolo.com/)

---

## 2.1 项目结构

```
app/
├── api/
│   ├── routes/
│   │   ├── __init__.py
│   │   ├── users.py
│   │   ├── auth.py
│   │   └── health.py
│   └── deps.py              # 依赖注入
├── core/
│   ├── config.py            # 配置管理
│   ├── security.py          # JWT / 密码哈希
│   └── database.py          # 数据库连接
├── models/
│   ├── __init__.py
│   ├── user.py              # SQLAlchemy 模型
│   └── schemas.py           # Pydantic 请求/响应模型
├── crud/
│   ├── __init__.py
│   └── user.py              # 数据库操作
├── middleware/
│   ├── logging.py
│   └── rate_limit.py
├── main.py                  # FastAPI 入口
├── exceptions.py            # 自定义异常
└── lifecycle.py             # 启动/关闭
tests/
├── conftest.py
├── test_api/
├── test_services/
└── test_models/
alembic/
docker/
├── Dockerfile
└── docker-compose.yml
```

## 2.2 应用入口与生命周期

```python
# app/main.py
from fastapi import FastAPI
from contextlib import asynccontextmanager
from app.lifecycle import on_startup, on_shutdown
from app.api.routes import users, auth, health
from app.middleware.logging import RequestLoggingMiddleware

@asynccontextmanager
async def lifespan(app: FastAPI):
    # 启动时
    await on_startup()
    yield
    # 关闭时
    await on_shutdown()

app = FastAPI(
    title="My API",
    version="1.0.0",
    lifespan=lifespan,
)

# 中间件
app.add_middleware(RequestLoggingMiddleware)

# 路由挂载
app.include_router(auth.router, prefix="/api/v1/auth", tags=["auth"])
app.include_router(users.router, prefix="/api/v1/users", tags=["users"])
app.include_router(health.router, prefix="/api/v1/health", tags=["health"])
```

## 2.3 Router 与分层架构

```python
# app/api/routes/users.py
from fastapi import APIRouter, Depends, Query, status
from app.models.schemas import UserCreate, UserResponse, UserListResponse
from app.crud.user import UserRepository
from app.api.deps import get_user_repo, get_current_user

router = APIRouter()

@router.get("/", response_model=UserListResponse)
async def list_users(
    page: int = Query(1, ge=1),
    size: int = Query(20, ge=1, le=100),
    repo: UserRepository = Depends(get_user_repo),
):
    users, total = await repo.list_users(page=page, size=size)
    return UserListResponse(items=users, total=total, page=page, size=size)

@router.post("/", response_model=UserResponse, status_code=status.HTTP_201_CREATED)
async def create_user(
    data: UserCreate,
    repo: UserRepository = Depends(get_user_repo),
):
    user = await repo.create(data)
    return user

@router.get("/{user_id}", response_model=UserResponse)
async def get_user(
    user_id: int,
    repo: UserRepository = Depends(get_user_repo),
):
    user = await repo.get(user_id)
    if not user:
        raise UserNotFoundError(user_id)
    return user
```

## 2.4 依赖注入（Dependency Injection）

这是 FastAPI 最核心的工程思想。

```python
# app/api/deps.py
from fastapi import Depends, HTTPException, status
from fastapi.security import HTTPBearer, HTTPAuthorizationCredentials
from app.core.database import get_async_session
from app.crud.user import UserRepository
from app.core.security import verify_token

security = HTTPBearer()

async def get_db():
    async with get_async_session() as session:
        yield session

async def get_user_repo(db=Depends(get_db)) -> UserRepository:
    return UserRepository(db)

async def get_current_user(
    credentials: HTTPAuthorizationCredentials = Depends(security),
) -> dict:
    token = credentials.credentials
    payload = verify_token(token)
    if payload is None:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid token",
        )
    return payload

# 依赖可以嵌套
async def get_admin_user(user=Depends(get_current_user)):
    if user.get("role") != "admin":
        raise HTTPException(status_code=403, detail="Admin required")
    return user
```

## 2.5 Middleware 与异常处理

```python
# app/exceptions.py
from fastapi import Request, HTTPException
from fastapi.responses import JSONResponse

class AppException(HTTPException):
    def __init__(self, code: str, message: str, status_code: int = 400):
        self.code = code
        self.message = message
        self.status_code = status_code

class UserNotFoundError(AppException):
    def __init__(self, user_id: int):
        super().__init__(code="USER_NOT_FOUND", message=f"User {user_id} not found", status_code=404)

class DuplicateUserError(AppException):
    def __init__(self, username: str):
        super().__init__(code="DUPLICATE_USER", message=f"User {username} already exists", status_code=409)

# app/main.py 中注册
@app.exception_handler(AppException)
async def app_exception_handler(request: Request, exc: AppException):
    return JSONResponse(
        status_code=exc.status_code,
        content={
            "code": exc.code,
            "message": exc.message,
            "request_id": getattr(request.state, "request_id", None),
            "data": None,
        },
    )
```

## 2.6 Background Task

```python
from fastapi import BackgroundTasks

async def send_welcome_email(email: str, username: str):
    # 发送邮件的逻辑
    await asyncio.sleep(2)
    print(f"Email sent to {email}")

@router.post("/users/", status_code=201)
async def create_user(
    data: UserCreate,
    background_tasks: BackgroundTasks,
    repo: UserRepository = Depends(get_user_repo),
):
    user = await repo.create(data)
    background_tasks.add_task(send_welcome_email, user.email, user.username)
    return user
```

## 2.7 WebSocket

```python
from fastapi import WebSocket, WebSocketDisconnect

@router.websocket("/ws/{user_id}")
async def websocket_endpoint(websocket: WebSocket, user_id: int):
    await websocket.accept()
    try:
        while True:
            data = await websocket.receive_text()
            await websocket.send_text(f"Echo: {data}")
    except WebSocketDisconnect:
        print(f"User {user_id} disconnected")
```

---

## 必读资源

- [FastAPI 官方文档](https://fastapi.tiangolo.com/)
- [FastAPI Best Practices (Auth0)](https://auth0.com/blog/fastapi-best-practices/)
- [FastAPI Production Deployment (Render)](https://render.com/articles/fastapi-production-deployment-best-practices)
- [Building Production-Ready APIs with FastAPI + SQLAlchemy + Alembic](https://pub.towardsai.net/building-production-ready-apis-with-fastapi-sqlalchemy-and-alembic-a-complete-guide-a4656b7e700c)
