# 依赖注入

> 适用：👶→🎯 入门至进阶
> 目标：理解 FastAPI 依赖注入机制，掌握 DI 在数据库、认证、分页等场景的实战应用

---

## 1. 什么是依赖注入

依赖注入（Dependency Injection, DI）是一种设计模式：**将依赖的创建和使用分离**，由框架负责提供依赖实例。

```python
# 不使用 DI（硬编码依赖）
def get_users():
    db = Database()  # 手动创建
    return db.query("SELECT * FROM users")


# 使用 FastAPI DI
def get_users(db: Database = Depends(get_db)):
    return db.query("SELECT * FROM users")
```

> Java 对比：Spring Boot 中 `@Autowired` / 构造器注入 → FastAPI 的 `Depends()`。不同的是 FastAPI 显式通过函数参数声明依赖，而非隐式注入。

---

## 2. Depends 基础

```python
from fastapi import FastAPI, Depends

app = FastAPI()


# 定义一个依赖（就是一个普通函数）
def common_parameters(q: str | None = None, page: int = 1, size: int = 10):
    return {"q": q, "page": page, "size": size}


# 使用依赖
@app.get("/items/")
def list_items(params: dict = Depends(common_parameters)):
    return params


@app.get("/users/")
def list_users(params: dict = Depends(common_parameters)):
    return params
```

`Depends` 的工作原理：
1. FastAPI 解析路由函数参数，发现有 `Depends()` 标记
2. 执行依赖函数，传入需要的参数
3. 将返回值注入到路由函数的参数中
4. 依赖函数本身也可以有 `Depends()`，形成依赖链

---

## 3. 依赖的作用域

FastAPI 依赖默认是**每次请求**创建一次，不存在 Spring 那样的 singleton / prototype / request 作用域区分。

| 模式 | 说明 | 实现方式 |
|------|------|----------|
| 每次请求 | 每个请求创建新实例 | 默认行为 |
| 单例 | 全局共享一个实例 | 模块级变量 + `lru_cache` |
| 临时 | 按需创建 | 普通函数 |

### 模拟单例依赖

```python
from functools import lru_cache


class Settings:
    def __init__(self):
        self.app_name = "AI 商城"
        self.database_url = "postgresql://localhost:5432/mall"


@lru_cache()
def get_settings():
    return Settings()


@app.get("/info")
def get_info(settings: Settings = Depends(get_settings)):
    return {"app_name": settings.app_name}
```

`@lru_cache()` 确保 `get_settings()` 函数只执行一次，后续调用直接返回缓存结果。

---

## 4. 可调用类作为依赖

依赖不限于函数——类实例也可以作为依赖：

```python
from fastapi import FastAPI, Depends, HTTPException

app = FastAPI()


class Pagination:
    """分页依赖——可调用类"""
    def __init__(self, default_page: int = 1, default_size: int = 20):
        self.default_page = default_page
        self.default_size = default_size

    def __call__(self, page: int = 1, size: int = 20):
        if page < 1:
            raise HTTPException(400, "页码必须 ≥ 1")
        if size > 100:
            size = 100
        return {"page": page, "size": size, "offset": (page - 1) * size}


pagination = Pagination()


@app.get("/items/")
def list_items(p: dict = Depends(pagination)):
    return p
```

---

## 5. 依赖的依赖（嵌套依赖）

依赖可以嵌套，形成依赖链：

```python
from fastapi import FastAPI, Depends, HTTPException, Header

app = FastAPI()


# 第一层：配置
@lru_cache()
def get_settings():
    return {"db_url": "postgresql://...", "api_key": "sk-xxx"}


# 第二层：数据库会话（依赖配置）
def get_db(settings: dict = Depends(get_settings)):
    db = {"connected": True, "url": settings["db_url"]}
    try:
        yield db  # 生成器：请求结束时自动关闭
    finally:
        db["connected"] = False


# 第三层：认证（依赖数据库）
def get_current_user(
    db: dict = Depends(get_db),
    authorization: str = Header(...),
):
    if not authorization.startswith("Bearer "):
        raise HTTPException(401, "无效的认证头")
    return {"user_id": 1, "name": "张三"}


# 路由：使用依赖链
@app.get("/profile/")
def get_profile(user: dict = Depends(get_current_user)):
    return user
```

---

## 6. 实战：数据库会话管理

```python
from fastapi import FastAPI, Depends
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker, Session

DATABASE_URL = "postgresql://user:pass@localhost/mall"
engine = create_engine(DATABASE_URL)
SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)

app = FastAPI()


# 数据库会话依赖
def get_db():
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()


# 路由中使用
@app.get("/products/")
def list_products(db: Session = Depends(get_db)):
    return db.query("Product").all()
```

---

## 7. 实战：认证依赖

```python
import jwt
from fastapi import FastAPI, Depends, HTTPException, Header
from pydantic import BaseModel

app = FastAPI()
SECRET_KEY = "your-secret-key"


class User(BaseModel):
    id: int
    username: str
    role: str


def verify_token(authorization: str = Header(...)) -> User:
    """认证依赖——验证 JWT Token"""
    if not authorization.startswith("Bearer "):
        raise HTTPException(401, "无效的认证头")

    token = authorization.replace("Bearer ", "")
    try:
        payload = jwt.decode(token, SECRET_KEY, algorithms=["HS256"])
        return User(id=payload["sub"], username=payload["name"], role=payload["role"])
    except jwt.ExpiredSignatureError:
        raise HTTPException(401, "Token 已过期")
    except jwt.InvalidTokenError:
        raise HTTPException(401, "无效的 Token")


def require_admin(user: User = Depends(verify_token)) -> User:
    """权限依赖——要求管理员角色"""
    if user.role != "admin":
        raise HTTPException(403, "权限不足")
    return user


@app.get("/profile/")
def get_profile(user: User = Depends(verify_token)):
    """任意登录用户可访问"""
    return user


@app.get("/admin/products/")
def admin_products(user: User = Depends(require_admin)):
    """仅管理员可访问"""
    return {"message": f"欢迎管理员 {user.username}"}
```

---

## 8. 实战：分页依赖

```python
from fastapi import FastAPI, Depends, HTTPException
from pydantic import BaseModel


class PageParams(BaseModel):
    page: int = 1
    size: int = 20


def get_pagination(
    page: int = 1,
    size: int = 20,
    sort: str = "created_at",
    order: str = "desc",
) -> dict:
    """统一的分页依赖"""
    if page < 1:
        raise HTTPException(400, "页码必须 ≥ 1")
    if size < 1 or size > 100:
        raise HTTPException(400, "每页条数必须在 1-100 之间")
    if order not in ("asc", "desc"):
        raise HTTPException(400, "排序方式必须是 asc 或 desc")

    return {
        "page": page,
        "size": size,
        "offset": (page - 1) * size,
        "sort": sort,
        "order": order,
    }


@app.get("/products/")
def list_products(pagination: dict = Depends(get_pagination)):
    """统一分页的商品列表"""
    return {
        "page": pagination["page"],
        "size": pagination["size"],
        "offset": pagination["offset"],
        "results": [],  # 实际查询数据库
        "total": 0,
    }
```

---

## 本章小结

FastAPI 的依赖注入机制是其区别于其他 Python Web 框架的核心特性。

| 场景 | 实现 | 类比 Spring Boot |
|------|------|-----------------|
| 简单依赖 | `Depends(func)` | `@Autowired` |
| 配置单例 | `@lru_cache` + `Depends` | `@Configuration` + `@Bean` |
| 可调用类 | `class.__call__` | 工厂模式 |
| 嵌套依赖 | 依赖也有 `Depends` | `@Autowired` 链 |
| 资源清理 | `yield` + `finally` | `@PreDestroy` |
| 认证 | 依赖函数返回 User | `SecurityContextHolder` |

下一章将介绍 Pydantic v2 的数据验证与配置管理。