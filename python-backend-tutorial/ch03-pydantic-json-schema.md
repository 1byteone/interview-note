# 第三章：Pydantic v2 + JSON Schema（P0 精通）

> 📖 **参考资料**：[Pydantic 官方文档](https://pydantic.dev/docs/) | [LLM Structured Output with Pydantic](https://www.openlegion.ai/en/learn/llm-structured-output) | [Structured Outputs with OpenAI and Pydantic](https://dida.do/blog/structured-outputs-with-openai-and-pydantic) | [Pydantic Output Documentation](https://pydantic.dev/docs/ai/core-concepts/output/)

---

## 3.1 Pydantic v2 核心

```python
from pydantic import BaseModel, Field, EmailStr, field_validator, ConfigDict
from datetime import datetime
from enum import Enum

class UserRole(str, Enum):
    ADMIN = "admin"
    USER = "user"
    EDITOR = "editor"

class UserCreate(BaseModel):
    username: str = Field(..., min_length=3, max_length=50, examples=["alice"])
    email: EmailStr
    password: str = Field(..., min_length=8)
    role: UserRole = UserRole.USER
    age: int | None = Field(None, ge=0, le=150)

    @field_validator("username")
    @classmethod
    def validate_username(cls, v: str) -> str:
        if not v.isalnum():
            raise ValueError("Username must be alphanumeric")
        return v.lower()

class UserResponse(BaseModel):
    model_config = ConfigDict(from_attributes=True)  # ORM 兼容

    id: int
    username: str
    email: str
    role: UserRole
    created_at: datetime
    is_active: bool = True

class UserListResponse(BaseModel):
    items: list[UserResponse]
    total: int
    page: int
    size: int
```

## 3.2 JSON Schema 生成

```python
# 生成 JSON Schema（用于 LLM Structured Output、API 文档）
schema = UserCreate.model_json_schema()
print(schema)
# {
#   "properties": {
#     "username": {"type": "string", "minLength": 3, "maxLength": 50},
#     "email": {"type": "string", "format": "email"},
#     "password": {"type": "string", "minLength": 8},
#     "role": {"enum": ["admin", "user", "editor"], "type": "string"},
#     "age": {"anyOf": [{"type": "integer"}, {"type": "null"}], "minimum": 0, "maximum": 150}
#   },
#   "required": ["username", "email", "password"]
# }
```

## 3.3 高级用法

### 模型继承与组合

```python
class BaseUser(BaseModel):
    username: str
    email: EmailStr

class UserCreate(BaseUser):
    password: str = Field(..., min_length=8)

class UserUpdate(BaseModel):
    username: str | None = None
    email: EmailStr | None = None
    age: int | None = Field(None, ge=0, le=150)

class UserInDB(BaseUser):
    id: int
    hashed_password: str
    created_at: datetime
    is_active: bool = True
```

### 自定义序列化

```python
from pydantic import field_serializer

class Money(BaseModel):
    amount: float
    currency: str = "USD"

    @field_serializer("amount")
    def serialize_amount(self, value: float) -> str:
        return f"{value:.2f} {self.currency}"
```

### 模型转换

```python
# Pydantic v2 模型转换
user_create = UserCreate(username="alice", email="alice@test.com", password="pass1234")

# 转为字典
user_dict = user_create.model_dump()
user_dict_excl = user_create.model_dump(exclude={"password"})

# 从字典创建
user = UserCreate.model_validate(user_dict)

# 从 JSON 字符串创建
user = UserCreate.model_validate_json('{"username":"alice","email":"alice@test.com","password":"pass1234"}')
```

### 配置与 Settings Management

```python
from pydantic_settings import BaseSettings

class AppSettings(BaseSettings):
    app_name: str = "MyApp"
    database_url: str = "postgresql+asyncpg://localhost/mydb"
    redis_url: str = "redis://localhost:6379"
    debug: bool = False

    model_config = {"env_file": ".env", "env_prefix": "APP_"}

settings = AppSettings()
```

## 3.4 Pydantic → JSON Schema → LLM 结构化输出 桥梁

```text
Python Type  →  Pydantic Model  →  JSON Schema  →  API Contract / LLM Structured Output
```

```python
# AI 后端场景：LLM 结构化输出
from typing import Literal

class AnalysisResult(BaseModel):
    sentiment: Literal["positive", "negative", "neutral"]
    confidence: float = Field(ge=0.0, le=1.0)
    key_points: list[str]
    summary: str

# 将 schema 传给 LLM API
schema_json = AnalysisResult.model_json_schema()

# 验证 LLM 返回的结果
llm_output = {
    "sentiment": "positive",
    "confidence": 0.92,
    "key_points": ["fast", "scalable"],
    "summary": "Great framework"
}
result = AnalysisResult.model_validate(llm_output)  # 自动校验

# 如果验证失败，Pydantic 会抛出详细的 ValidationError
# 包含每个字段的错误信息
```

## 3.5 与 FastAPI 的深度集成

```python
from fastapi import FastAPI, Query
from pydantic import BaseModel, Field

app = FastAPI()

class FilterParams(BaseModel):
    """查询参数模型"""
    page: int = Field(1, ge=1, description="页码")
    size: int = Field(20, ge=1, le=100, description="每页数量")
    search: str | None = Field(None, description="搜索关键词")
    sort_by: str = Field("created_at", description="排序字段")
    sort_order: Literal["asc", "desc"] = Field("desc", description="排序方向")

# FastAPI 自动从 Query 参数解析并验证
@app.get("/items/")
async def list_items(filters: FilterParams = Depends()):
    # filters 已经是验证过的 Pydantic 对象
    return {"page": filters.page, "size": filters.size}
```

---

## 核心理念

> **Pydantic + JSON Schema 是传统 Web 后端和 AI 后端之间非常重要的桥梁。**

```text
Web 后端:  Pydantic → 校验 API 输入输出 → OpenAPI 文档
AI 后端:   Pydantic → JSON Schema → LLM Structured Output → 校验 LLM 返回
```
