# 第五章：PostgreSQL + SQLAlchemy 2.x（P0 精通）

> 📖 **参考资料**：[SQLAlchemy 2.0 Async Documentation](http://docs.sqlalchemy.org/en/latest/orm/extensions/asyncio.html) | [SQLAlchemy 2 In Practice (Miguel Grinberg)](https://blog.miguelgrinberg.com/post/sqlalchemy-2-in-practice---chapter-7-asynchronous-sqlalchemy) | [FastAPI + SQLAlchemy 2 + Alembic (YouTube)](https://www.youtube.com/watch?v=gg7AX1iRnmg)

---

## 5.1 数据库模型设计

```python
# app/models/user.py
from sqlalchemy import String, Boolean, Enum as SAEnum, DateTime, func, Index
from sqlalchemy.orm import Mapped, mapped_column, relationship
from sqlalchemy.ext.asyncio import AsyncAttrs
from app.core.database import Base
import enum
from datetime import datetime

class UserRoleEnum(str, enum.Enum):
    ADMIN = "admin"
    USER = "user"

class User(Base, AsyncAttrs):
    __tablename__ = "users"

    id: Mapped[int] = mapped_column(primary_key=True)
    username: Mapped[str] = mapped_column(String(50), unique=True, index=True)
    email: Mapped[str] = mapped_column(String(255), unique=True, index=True)
    hashed_password: Mapped[str] = mapped_column(String(255))
    role: Mapped[UserRoleEnum] = mapped_column(SAEnum(UserRoleEnum), default=UserRoleEnum.USER)
    is_active: Mapped[bool] = mapped_column(Boolean, default=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now())
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), onupdate=func.now())

    # Relationship
    posts: Mapped[list["Post"]] = relationship(back_populates="author", lazy="selectin")
```

## 5.2 异步数据库会话

```python
# app/core/database.py
from sqlalchemy.ext.asyncio import create_async_engine, async_sessionmaker, AsyncSession
from app.core.config import settings

engine = create_async_engine(
    settings.DATABASE_URL,
    pool_size=20,
    max_overflow=10,
    pool_pre_ping=True,
    pool_recycle=3600,
)

AsyncSessionLocal = async_sessionmaker(
    engine, class_=AsyncSession, expire_on_commit=False
)

class Base:
    pass

async def get_async_session():
    async with AsyncSessionLocal() as session:
        try:
            yield session
            await session.commit()
        except Exception:
            await session.rollback()
            raise
```

## 5.3 Repository 模式

```python
# app/crud/user.py
from sqlalchemy import select, func, update, delete
from sqlalchemy.ext.asyncio import AsyncSession
from app.models.user import User
from app.models.schemas import UserCreate, UserUpdate

class UserRepository:
    def __init__(self, db: AsyncSession):
        self.db = db

    async def get(self, user_id: int) -> User | None:
        result = await self.db.execute(select(User).where(User.id == user_id))
        return result.scalar_one_or_none()

    async def get_by_email(self, email: str) -> User | None:
        result = await self.db.execute(select(User).where(User.email == email))
        return result.scalar_one_or_none()

    async def create(self, data: UserCreate, hashed_password: str) -> User:
        user = User(**data.model_dump(), hashed_password=hashed_password)
        self.db.add(user)
        await self.db.flush()
        await self.db.refresh(user)
        return user

    async def update(self, user_id: int, data: UserUpdate) -> User | None:
        user = await self.get(user_id)
        if not user:
            return None
        update_data = data.model_dump(exclude_unset=True)
        for key, value in update_data.items():
            setattr(user, key, value)
        await self.db.flush()
        await self.db.refresh(user)
        return user

    async def delete(self, user_id: int) -> bool:
        user = await self.get(user_id)
        if not user:
            return False
        await self.db.delete(user)
        return True

    async def list_users(self, page: int = 1, size: int = 20) -> tuple[list[User], int]:
        count_q = select(func.count()).select_from(User)
        total = (await self.db.execute(count_q)).scalar_one()
        q = select(User).offset((page - 1) * size).limit(size).order_by(User.created_at.desc())
        result = await self.db.execute(q)
        return list(result.scalars().all()), total
```

## 5.4 Alembic 迁移

```bash
# 初始化
alembic init alembic

# 配置 alembic.ini
# sqlalchemy.url = postgresql+asyncpg://user:pass@localhost/db

# 生成迁移
alembic revision --autogenerate -m "add users table"

# 应用迁移
alembic upgrade head

# 回滚
alembic downgrade -1

# 查看迁移历史
alembic history
```

```python
# alembic/env.py (async 配置)
from app.core.database import Base
from app.models.user import User  # 确保导入所有模型

target_metadata = Base.metadata
```

## 5.5 关系与关联表

```python
from sqlalchemy import Table, Column, Integer, ForeignKey
from sqlalchemy.orm import relationship

# 多对多关联表
article_tags = Table(
    "article_tags",
    Base.metadata,
    Column("article_id", Integer, ForeignKey("articles.id"), primary_key=True),
    Column("tag_id", Integer, ForeignKey("tags.id"), primary_key=True),
)

class Article(Base):
    __tablename__ = "articles"
    id: Mapped[int] = mapped_column(primary_key=True)
    title: Mapped[str] = mapped_column(String(200))
    author_id: Mapped[int] = mapped_column(ForeignKey("users.id"))
    author: Mapped["User"] = relationship(back_populates="articles")
    tags: Mapped[list["Tag"]] = relationship(secondary=article_tags, back_populates="articles")
```

## 5.6 PostgreSQL 索引与优化

```python
# 复合索引
class Order(Base):
    __tablename__ = "orders"
    __table_args__ = (
        Index("idx_user_status_created", "user_id", "status", "created_at"),
    )

# 部分索引（PostgreSQL 特有）
# CREATE INDEX idx_active_users ON users (email) WHERE is_active = true;

# 全文搜索索引
# CREATE INDEX idx_users_search ON users USING gin(to_tsvector('english', username || ' ' || email));
```

## 5.7 必学 SQL

```sql
-- CTE (Common Table Expression)
WITH active_users AS (
    SELECT id, username, email
    FROM users
    WHERE is_active = true
)
SELECT * FROM active_users WHERE username LIKE 'a%';

-- Window Function
SELECT
    username,
    created_at,
    ROW_NUMBER() OVER (ORDER BY created_at DESC) as rank
FROM users;

-- 子查询
SELECT * FROM orders
WHERE user_id IN (SELECT id FROM users WHERE role = 'admin');

-- JOIN
SELECT u.username, o.id as order_id
FROM users u
INNER JOIN orders o ON u.id = o.user_id;

-- EXPLAIN ANALYZE（性能分析）
EXPLAIN ANALYZE SELECT * FROM users WHERE email = 'alice@test.com';
```

## 5.8 数据库原理

```text
必须理解:
- B+Tree 索引: 为什么用 B+Tree 而不是 Hash
- MVCC: 多版本并发控制如何实现
- 事务隔离级别: Read Committed / Repeatable Read / Serializable
- 锁机制: 行锁 / 表锁 / 意向锁 / 死锁
- 执行计划: EXPLAIN ANALYZE 如何阅读
```
