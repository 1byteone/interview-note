# 迷你博客项目 — FastAPI 实战

> 适用：🎯 进阶
> 目标：独立实现一个完整的博客 API，综合运用 FastAPI 核心特性

---

## 项目概述

实现一个博客 API 系统，包含用户认证、文章 CRUD、评论、标签等功能。

### 技术栈

| 组件 | 技术 |
|------|------|
| Web 框架 | FastAPI |
| 数据库 | SQLite（开发）/ PostgreSQL（生产） |
| ORM | SQLAlchemy（异步模式） |
| 认证 | JWT |
| 测试 | pytest + TestClient |
| 部署 | Docker |

---

## 项目结构

```
mini-blog/
├── app/
│   ├── __init__.py
│   ├── main.py                  # FastAPI 应用入口
│   ├── config.py                # 配置管理
│   ├── database.py              # 数据库连接
│   ├── models/
│   │   ├── __init__.py
│   │   ├── user.py              # 用户模型
│   │   ├── post.py              # 文章模型
│   │   └── comment.py           # 评论模型
│   ├── schemas/
│   │   ├── __init__.py
│   │   ├── user.py              # 用户 Pydantic 模型
│   │   ├── post.py              # 文章 Pydantic 模型
│   │   └── comment.py           # 评论 Pydantic 模型
│   ├── api/
│   │   ├── __init__.py
│   │   ├── auth.py              # 认证路由
│   │   ├── posts.py             # 文章路由
│   │   └── comments.py          # 评论路由
│   ├── services/
│   │   ├── __init__.py
│   │   ├── user_service.py      # 用户服务
│   │   ├── post_service.py      # 文章服务
│   │   └── comment_service.py   # 评论服务
│   └── core/
│       ├── __init__.py
│       ├── security.py          # 安全/认证
│       ├── dependencies.py      # 公共依赖
│       └── exceptions.py        # 异常处理
├── tests/
│   ├── __init__.py
│   ├── conftest.py              # 测试配置
│   ├── test_auth.py
│   ├── test_posts.py
│   └── test_comments.py
├── Dockerfile
├── requirements.txt
├── pyproject.toml
└── README.md
```

---

## 核心代码实现

### 1. 配置管理

```python
# app/config.py
from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    app_name: str = "Mini Blog"
    debug: bool = False
    database_url: str = "sqlite+aiosqlite:///./blog.db"
    secret_key: str = "your-secret-key-change-in-production"
    jwt_algorithm: str = "HS256"
    access_token_expire_minutes: int = 30

    class Config:
        env_file = ".env"


settings = Settings()
```

### 2. 数据库模型

```python
# app/models/user.py
from sqlalchemy import Column, Integer, String, Boolean, DateTime
from sqlalchemy.orm import relationship
from sqlalchemy.sql import func
from app.database import Base


class User(Base):
    __tablename__ = "users"

    id = Column(Integer, primary_key=True, index=True)
    username = Column(String(50), unique=True, index=True, nullable=False)
    email = Column(String(100), unique=True, index=True, nullable=False)
    hashed_password = Column(String(255), nullable=False)
    is_active = Column(Boolean, default=True)
    created_at = Column(DateTime(timezone=True), server_default=func.now())

    posts = relationship("Post", back_populates="author")
    comments = relationship("Comment", back_populates="author")
```

```python
# app/models/post.py
from sqlalchemy import Column, Integer, String, Text, DateTime, ForeignKey
from sqlalchemy.orm import relationship
from sqlalchemy.sql import func
from app.database import Base


class Post(Base):
    __tablename__ = "posts"

    id = Column(Integer, primary_key=True, index=True)
    title = Column(String(200), nullable=False)
    content = Column(Text, nullable=False)
    tags = Column(String(500), default="")  # 逗号分隔
    author_id = Column(Integer, ForeignKey("users.id"), nullable=False)
    created_at = Column(DateTime(timezone=True), server_default=func.now())
    updated_at = Column(DateTime(timezone=True), onupdate=func.now())

    author = relationship("User", back_populates="posts")
    comments = relationship("Comment", back_populates="post", cascade="all, delete-orphan")
```

### 3. Pydantic Schema

```python
# app/schemas/post.py
from pydantic import BaseModel, Field
from datetime import datetime
from typing import Optional


class PostCreate(BaseModel):
    title: str = Field(..., min_length=1, max_length=200)
    content: str = Field(..., min_length=1)
    tags: str = ""


class PostUpdate(BaseModel):
    title: Optional[str] = Field(None, min_length=1, max_length=200)
    content: Optional[str] = Field(None, min_length=1)
    tags: Optional[str] = None


class PostResponse(BaseModel):
    id: int
    title: str
    content: str
    tags: str
    author_id: int
    author_name: str
    created_at: datetime
    updated_at: Optional[datetime] = None

    model_config = {"from_attributes": True}


class PostListResponse(BaseModel):
    posts: list[PostResponse]
    total: int
    page: int
    size: int
```

### 4. 依赖注入

```python
# app/core/dependencies.py
from fastapi import Depends, HTTPException, Header
from sqlalchemy.ext.asyncio import AsyncSession
from app.database import get_session
from app.core.security import verify_token
from app.models.user import User
import jwt


async def get_current_user(
    session: AsyncSession = Depends(get_session),
    authorization: str = Header(...),
) -> User:
    """认证依赖——获取当前用户"""
    if not authorization.startswith("Bearer "):
        raise HTTPException(status_code=401, detail="无效的认证头")

    token = authorization.replace("Bearer ", "")
    payload = verify_token(token)
    if payload is None:
        raise HTTPException(status_code=401, detail="Token 无效或已过期")

    user = await session.get(User, payload["sub"])
    if user is None:
        raise HTTPException(status_code=401, detail="用户不存在")
    return user


async def get_pagination(
    page: int = 1,
    size: int = 10,
) -> dict:
    """分页依赖"""
    if page < 1:
        raise HTTPException(status_code=400, detail="页码必须 ≥ 1")
    if size < 1 or size > 100:
        raise HTTPException(status_code=400, detail="每页条数必须在 1-100 之间")
    return {"page": page, "size": size, "offset": (page - 1) * size}
```

### 5. API 路由

```python
# app/api/posts.py
from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy import select, func
from sqlalchemy.ext.asyncio import AsyncSession
from app.database import get_session
from app.models.post import Post
from app.models.user import User
from app.schemas.post import PostCreate, PostUpdate, PostResponse, PostListResponse
from app.core.dependencies import get_current_user, get_pagination

router = APIRouter(prefix="/posts", tags=["文章"])


@router.post("/", response_model=PostResponse, status_code=201)
async def create_post(
    post_data: PostCreate,
    session: AsyncSession = Depends(get_session),
    current_user: User = Depends(get_current_user),
):
    """创建文章"""
    post = Post(
        title=post_data.title,
        content=post_data.content,
        tags=post_data.tags,
        author_id=current_user.id,
    )
    session.add(post)
    await session.commit()
    await session.refresh(post)
    return PostResponse(
        id=post.id,
        title=post.title,
        content=post.content,
        tags=post.tags,
        author_id=post.author_id,
        author_name=current_user.username,
        created_at=post.created_at,
        updated_at=post.updated_at,
    )


@router.get("/", response_model=PostListResponse)
async def list_posts(
    pagination: dict = Depends(get_pagination),
    session: AsyncSession = Depends(get_session),
):
    """文章列表（分页）"""
    query = select(Post).offset(pagination["offset"]).limit(pagination["size"])
    result = await session.execute(query)
    posts = result.scalars().all()

    count_query = select(func.count(Post.id))
    count_result = await session.execute(count_query)
    total = count_result.scalar()

    return PostListResponse(
        posts=[
            PostResponse(
                id=p.id,
                title=p.title,
                content=p.content,
                tags=p.tags,
                author_id=p.author_id,
                author_name=p.author.username,
                created_at=p.created_at,
                updated_at=p.updated_at,
            )
            for p in posts
        ],
        total=total,
        page=pagination["page"],
        size=pagination["size"],
    )


@router.get("/{post_id}", response_model=PostResponse)
async def get_post(
    post_id: int,
    session: AsyncSession = Depends(get_session),
):
    """文章详情"""
    post = await session.get(Post, post_id)
    if post is None:
        raise HTTPException(status_code=404, detail="文章不存在")
    return PostResponse(
        id=post.id,
        title=post.title,
        content=post.content,
        tags=post.tags,
        author_id=post.author_id,
        author_name=post.author.username,
        created_at=post.created_at,
        updated_at=post.updated_at,
    )


@router.put("/{post_id}", response_model=PostResponse)
async def update_post(
    post_id: int,
    post_data: PostUpdate,
    session: AsyncSession = Depends(get_session),
    current_user: User = Depends(get_current_user),
):
    """更新文章"""
    post = await session.get(Post, post_id)
    if post is None:
        raise HTTPException(status_code=404, detail="文章不存在")
    if post.author_id != current_user.id:
        raise HTTPException(status_code=403, detail="只能编辑自己的文章")

    if post_data.title is not None:
        post.title = post_data.title
    if post_data.content is not None:
        post.content = post_data.content
    if post_data.tags is not None:
        post.tags = post_data.tags

    await session.commit()
    await session.refresh(post)
    return PostResponse(
        id=post.id,
        title=post.title,
        content=post.content,
        tags=post.tags,
        author_id=post.author_id,
        author_name=current_user.username,
        created_at=post.created_at,
        updated_at=post.updated_at,
    )


@router.delete("/{post_id}", status_code=204)
async def delete_post(
    post_id: int,
    session: AsyncSession = Depends(get_session),
    current_user: User = Depends(get_current_user),
):
    """删除文章"""
    post = await session.get(Post, post_id)
    if post is None:
        raise HTTPException(status_code=404, detail="文章不存在")
    if post.author_id != current_user.id:
        raise HTTPException(status_code=403, detail="只能删除自己的文章")

    await session.delete(post)
    await session.commit()
```

### 6. 测试

```python
# tests/conftest.py
import pytest
from httpx import AsyncClient, ASGITransport
from app.main import app
from app.database import get_session, engine, Base
from sqlalchemy.ext.asyncio import create_async_engine, AsyncSession
from sqlalchemy.orm import sessionmaker


@pytest.fixture
async def async_client():
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as client:
        yield client


@pytest.fixture
async def db_session():
    # 使用内存数据库
    test_engine = create_async_engine("sqlite+aiosqlite:///:memory:")
    async with test_engine.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)

    TestSession = sessionmaker(test_engine, class_=AsyncSession)
    async with TestSession() as session:
        yield session


# 覆盖依赖
@pytest.fixture(autouse=True)
def override_deps(db_session):
    app.dependency_overrides[get_session] = lambda: db_session
    yield
    app.dependency_overrides.clear()
```

```python
# tests/test_posts.py
import pytest


@pytest.mark.asyncio
async def test_create_post(async_client, auth_headers):
    response = await async_client.post(
        "/posts/",
        json={
            "title": "测试文章",
            "content": "这是文章内容",
            "tags": "python,fastapi",
        },
        headers=auth_headers,
    )
    assert response.status_code == 201
    data = response.json()
    assert data["title"] == "测试文章"
    assert data["author_name"] == "testuser"


@pytest.mark.asyncio
async def test_list_posts(async_client):
    response = await async_client.get("/posts/")
    assert response.status_code == 200
    data = response.json()
    assert "posts" in data
    assert "total" in data
```

---

## 功能清单

| 功能 | 状态 | 说明 |
|------|------|------|
| 用户注册 | 待实现 | POST /auth/register |
| 用户登录 | 待实现 | POST /auth/login，返回 JWT |
| 文章 CRUD | 已实现 | POST/GET/PUT/DELETE /posts/ |
| 文章列表分页 | 已实现 | GET /posts/?page=1&size=10 |
| 评论功能 | 待实现 | 关联文章和用户 |
| 标签过滤 | 待实现 | GET /posts/?tags=python |
| 搜索文章 | 待实现 | GET /posts/search?q=keyword |
| 用户权限 | 已实现 | 仅作者可编辑/删除 |
| 认证 | 已实现 | JWT Bearer Token |
| 测试 | 已实现 | pytest + TestClient |

---

## 扩展建议

1. **全文搜索**：集成 Elasticsearch 或使用 SQLite FTS5
2. **缓存**：使用 Redis 缓存热门文章列表
3. **文件上传**：支持文章封面图上传
4. **Markdown 渲染**：文章内容支持 Markdown
5. **RSS 订阅**：生成博客 RSS feed
6. **Rate Limiting**：防止爬虫和滥用
7. **异步任务**：使用 Celery 处理邮件通知

---

## 本章小结

此迷你博客项目覆盖了 FastAPI 的核心特性：
- 异步 SQLAlchemy ORM
- 依赖注入（认证、分页、数据库会话）
- Pydantic 请求/响应模型
- 路由组织（APIRouter）
- JWT 认证
- 单元测试与依赖覆盖

建议按此项目结构自行实现完整代码，作为 FastAPI 学习成果的检验。