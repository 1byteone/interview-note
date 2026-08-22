# Pydantic 与验证

> 适用：👶→🎯 入门至进阶
> 目标：掌握 Pydantic v2 模型定义、验证机制、配置管理

---

## 1. Pydantic v2 简介

Pydantic 是 FastAPI 的数据验证核心库。FastAPI 基于 Pydantic 模型自动完成：
- JSON 请求体解析与验证
- 响应数据序列化
- OpenAPI 文档生成
- 配置管理

> 版本注意：Pydantic v2（2023 年发布）相比 v1 有重大改进，核心用 Rust 的 `pydantic-core` 重写，性能提升 5-50 倍。

---

## 2. 模型定义

```python
from pydantic import BaseModel
from datetime import datetime
from decimal import Decimal


class Product(BaseModel):
    id: int
    name: str
    price: Decimal
    category: str
    created_at: datetime = None
    is_active: bool = True
```

> Java 对比：类似 `@Entity` + `@Data` + `@Validated`，但 Pydantic 更轻量，一个类就完成定义、验证、序列化。

### 类型注解自动校验

```python
from pydantic import BaseModel
from uuid import UUID
from enum import Enum


class Status(str, Enum):
    PENDING = "pending"
    ACTIVE = "active"
    DISABLED = "disabled"


class Product(BaseModel):
    id: UUID              # UUID 格式校验
    price: float          # 必须为数字
    quantity: int         # 必须为整数
    tags: list[str]       # 字符串列表
    metadata: dict | None = None  # 可选字典
    status: Status        # 枚举值校验
```

---

## 3. 字段验证 — Field

```python
from pydantic import BaseModel, Field
from typing import Optional


class Product(BaseModel):
    id: int = Field(ge=1, description="商品 ID")
    name: str = Field(..., min_length=1, max_length=100, description="商品名称")
    price: float = Field(gt=0, le=999999, description="价格")
    stock: int = Field(ge=0, default=0, description="库存")
    description: Optional[str] = Field(None, max_length=2000)
    category: str = Field(..., pattern=r"^[A-Z_]+$")
```

`Field` 常用参数：

| 参数 | 说明 | 示例 |
|------|------|------|
| `default` | 默认值 | `default=0` |
| `default_factory` | 默认值工厂 | `default_factory=list` |
| `alias` | JSON 字段别名 | `alias="product_name"` |
| `ge` / `gt` | 大于等于 / 大于 | `ge=0` |
| `le` / `lt` | 小于等于 / 小于 | `le=100` |
| `min_length` / `max_length` | 字符串长度限制 | `min_length=1` |
| `pattern` | 正则表达式 | `pattern=r"^[A-Z]+$"` |
| `description` | 字段描述 | `description="商品名称"` |
| `examples` | 示例值 | `examples=["iPhone"]` |

---

## 4. 验证器

### Pydantic v2 `field_validator`

```python
from pydantic import BaseModel, Field, field_validator


class Order(BaseModel):
    product_id: int
    quantity: int
    coupon_code: str | None = None

    @field_validator("quantity")
    @classmethod
    def validate_quantity(cls, v: int) -> int:
        if v < 1:
            raise ValueError("数量必须 ≥ 1")
        if v > 100:
            raise ValueError("单次购买不能超过 100 件")
        return v

    @field_validator("coupon_code")
    @classmethod
    def validate_coupon(cls, v: str | None) -> str | None:
        if v and not v.startswith("COUPON_"):
            raise ValueError("优惠券格式无效")
        return v
```

### `model_validator` — 跨字段验证

```python
from pydantic import BaseModel, model_validator


class Order(BaseModel):
    items: list[dict]
    total_price: float

    @model_validator(mode="after")
    def check_total(self) -> "Order":
        calculated = sum(item["price"] * item["quantity"] for item in self.items)
        if abs(calculated - self.total_price) > 0.01:
            raise ValueError("总价与明细不匹配")
        return self
```

> Java 对比：`@Valid` + 自定义注解 → `field_validator` / `model_validator`。Pydantic 的验证更灵活，可以同时作用于序列化和反序列化。

---

## 5. 嵌套模型

```python
from pydantic import BaseModel
from datetime import datetime


class Address(BaseModel):
    province: str
    city: str
    district: str
    detail: str


class User(BaseModel):
    id: int
    name: str
    address: Address  # 嵌套模型


class Order(BaseModel):
    id: int
    user: User  # 嵌套模型
    items: list[dict]
    created_at: datetime


# 使用
order_data = {
    "id": 1,
    "user": {
        "id": 100,
        "name": "张三",
        "address": {
            "province": "广东",
            "city": "深圳",
            "district": "南山区",
            "detail": "科技园路 1 号",
        },
    },
    "items": [{"product_id": 1, "quantity": 2}],
    "created_at": "2025-01-15T10:30:00",
}

order = Order(**order_data)
print(order.user.address.city)  # 深圳
```

---

## 6. 响应模型

```python
from fastapi import FastAPI
from pydantic import BaseModel
from datetime import datetime

app = FastAPI()


class UserDB(BaseModel):
    """数据库模型——包含敏感字段"""
    id: int
    username: str
    email: str
    password_hash: str
    created_at: datetime


class UserResponse(BaseModel):
    """响应模型——排除敏感字段"""
    id: int
    username: str
    email: str
    created_at: datetime


# 方案一：显式指定 response_model
@app.get("/users/{user_id}", response_model=UserResponse)
def get_user(user_id: int):
    user = UserDB(
        id=user_id,
        username="zhangsan",
        email="zhangsan@example.com",
        password_hash="***hash***",
        created_at=datetime.now(),
    )
    return user  # 自动过滤 password_hash


# 方案二：使用 response_model_exclude
@app.get("/users/{user_id}/brief")
def get_user_brief(user_id: int):
    user = UserDB(
        id=user_id,
        username="zhangsan",
        email="zhangsan@example.com",
        password_hash="***hash***",
        created_at=datetime.now(),
    )
    return user


# 方案三：response_model_exclude_unset
class ProductCreate(BaseModel):
    name: str
    price: float
    description: str = "暂无描述"


@app.post("/products/", response_model=ProductCreate, response_model_exclude_unset=True)
def create_product(product: ProductCreate):
    return product  # 只返回客户端显式设置的值
```

---

## 7. 配置管理 — Settings

Pydantic 的 `BaseSettings` 从环境变量、`.env` 文件等来源加载配置。

```python
from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    # 应用配置
    app_name: str = "AI 商城"
    debug: bool = False

    # 数据库配置
    database_url: str
    redis_url: str = "redis://localhost:6379/0"

    # AI 服务配置
    openai_api_key: str
    openai_model: str = "gpt-4o"
    max_tokens: int = 2048

    # 安全配置
    secret_key: str
    jwt_algorithm: str = "HS256"
    access_token_expire_minutes: int = 30

    class Config:
        env_file = ".env"
        env_file_encoding = "utf-8"


# 全局单例
from functools import lru_cache


@lru_cache()
def get_settings() -> Settings:
    return Settings()
```

`.env` 文件示例：

```env
DATABASE_URL=postgresql://user:pass@localhost:5432/mall
OPENAI_API_KEY=sk-xxx
SECRET_KEY=your-secret-key-here
```

```python
# 在路由中使用
from fastapi import FastAPI, Depends

app = FastAPI()


@app.get("/config")
def get_config(settings: Settings = Depends(get_settings)):
    return {
        "app_name": settings.app_name,
        "debug": settings.debug,
        "model": settings.openai_model,
    }
```

---

## 8. Pydantic v2 核心改进（vs v1）

| 特性 | Pydantic v1 | Pydantic v2 |
|------|-------------|-------------|
| 核心引擎 | Python | Rust (pydantic-core) |
| 性能 | 慢 | 快 5-50 倍 |
| 验证 API | `@validator` | `@field_validator` / `@model_validator` |
| 配置方式 | 内部 `Config` 类 | `model_config` 字典 |
| 序列化 | `.dict()` | `.model_dump()` |
| JSON | `.json()` | `.model_dump_json()` |
| 泛型 | 有限支持 | 完整支持 |
| 严格模式 | 不支持 | `model_config={"strict": True}` |

---

## 本章小结

Pydantic v2 是 FastAPI 生态的基石，提供类型安全、自动验证、文档生成和配置管理能力。对 Java 开发者而言，Pydantic 相当于 `@Data` + `@Valid` + `@ConfigurationProperties` 一个包搞定，且代码更简洁、性能更高。

下一章将介绍 FastAPI 的异步编程模型。