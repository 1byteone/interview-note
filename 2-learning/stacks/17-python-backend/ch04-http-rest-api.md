# 第四章：HTTP / REST API 工程规范（P0 精通）

> 📖 **参考资料**：[RESTful API Design Best Practices](https://restfulapi.net/) | [HTTP Status Codes](https://httpstatuses.com/) | [FastAPI Best Practices](https://auth0.com/blog/fastapi-best-practices/)

---

## 4.1 HTTP 方法与状态码

| 方法 | 语义 | 成功状态码 | 示例 |
|------|------|-----------|------|
| GET | 读取资源 | 200 OK | `GET /users/1` |
| POST | 创建资源 | 201 Created | `POST /users` |
| PUT | 全量更新 | 200 OK | `PUT /users/1` |
| PATCH | 部分更新 | 200 OK | `PATCH /users/1` |
| DELETE | 删除资源 | 204 No Content | `DELETE /users/1` |
| HEAD | 获取资源元数据 | 200 OK | `HEAD /users/1` |
| OPTIONS | 获取支持的方法 | 200 OK | `OPTIONS /users` |

### 状态码完整参考

```text
2xx 成功
200 OK                → 一般性成功
201 Created           → 资源创建成功
204 No Content        → 成功但无返回体（如删除）

4xx 客户端错误
400 Bad Request       → 请求参数格式错误
401 Unauthorized      → 未认证（需要登录）
403 Forbidden         → 已认证但无权限
404 Not Found         → 资源不存在
409 Conflict          → 资源冲突（如重复创建）
422 Validation Error  → 数据校验失败（Pydantic 验证错误）
429 Too Many Requests → 触发限流

5xx 服务端错误
500 Internal Server Error → 服务端内部错误
502 Bad Gateway           → 网关错误
503 Service Unavailable   → 服务不可用（过载/维护）
```

## 4.2 RESTful 命名规范

```text
✅ 正确                          ❌ 错误
GET /users                       GET /getUsers
GET /users/1                     GET /getUser?id=1
POST /users                      POST /createUser
PUT /users/1                     PUT /updateUser
DELETE /users/1                  DELETE /deleteUser

✅ 资源层级                       ❌ 过深嵌套
GET /users/1/orders              GET /users/1/orders/2/items/3
GET /users/1/orders/2
```

### 规则

```text
- 使用复数名词: /users, /orders, /products
- URL 小写: /user-profiles, 不是 /UserProfiles
- URL 不可变: 一旦发布不要修改
- 版本化: /v1/users, /v2/users
- 过滤用查询参数: /users?role=admin&is_active=true
```

## 4.3 统一错误响应

```python
from fastapi import Request
from fastapi.responses import JSONResponse

class ErrorResponse(BaseModel):
    code: str
    message: str
    request_id: str | None = None
    data: Any = None

@app.exception_handler(AppException)
async def error_handler(request: Request, exc: AppException):
    return JSONResponse(
        status_code=exc.status_code,
        content=ErrorResponse(
            code=exc.code,
            message=exc.message,
            request_id=getattr(request.state, "request_id", None),
        ).model_dump(),
    )
```

**响应格式**：

```json
{
  "code": "USER_NOT_FOUND",
  "message": "User with id 42 not found",
  "request_id": "req_abc123def456",
  "data": null
}
```

## 4.4 分页 / 过滤 / 排序

```python
from pydantic import BaseModel, Field
from enum import Enum

class SortOrder(str, Enum):
    ASC = "asc"
    DESC = "desc"

class PaginationParams(BaseModel):
    page: int = Field(1, ge=1, description="页码，从 1 开始")
    size: int = Field(20, ge=1, le=100, description="每页数量")
    sort_by: str = Field("created_at", description="排序字段")
    sort_order: SortOrder = SortOrder.DESC

class UserFilter(BaseModel):
    role: UserRole | None = None
    is_active: bool | None = None
    search: str | None = None

class PaginatedResponse(BaseModel, Generic[T]):
    items: list[T]
    total: int
    page: int
    size: int
    pages: int  # 总页数
```

### Offset 分页 vs Cursor 分页

```text
Offset 分页（简单场景）
GET /users?page=2&size=20
→ SELECT * FROM users OFFSET 20 LIMIT 20

Cursor 分页（大数据集 / 实时数据）
GET /users?cursor=eyJpZCI6MTAwfQ&size=20
→ SELECT * FROM users WHERE id > 100 ORDER BY id LIMIT 20
→ 无偏移性能问题，适合无限滚动
```

## 4.5 幂等性

```text
天然幂等: GET, PUT, DELETE
需要幂等键: POST（通过 Idempotency-Key header）

POST /payments
Headers:
  Idempotency-Key: uuid-12345

服务端逻辑:
1. 收到请求，检查 Idempotency-Key 是否已处理
2. 如果已处理 → 返回缓存的结果
3. 如果未处理 → 处理请求，缓存结果
```

## 4.6 API 版本控制

```text
方式 1: URL 路径（推荐）
/api/v1/users
/api/v2/users

方式 2: Header
Accept: application/vnd.myapp.v1+json

方式 3: 查询参数
/api/users?version=1
```

## 4.7 Request ID 与 Trace ID

```python
import uuid
from starlette.middleware.base import BaseHTTPMiddleware

class RequestIDMiddleware(BaseHTTPMiddleware):
    async def dispatch(self, request, call_next):
        request_id = request.headers.get("X-Request-ID", str(uuid.uuid4()))
        request.state.request_id = request_id
        response = await call_next(request)
        response.headers["X-Request-ID"] = request_id
        return response
```

## 4.8 安全 Headers

```text
X-Content-Type-Options: nosniff
X-Frame-Options: DENY
X-XSS-Protection: 1; mode=block
Strict-Transport-Security: max-age=31536000; includeSubDomains
Content-Security-Policy: default-src 'self'
Cache-Control: no-store, no-cache, must-revalidate
```

## 4.9 HTTP 协议进阶

### 条件请求

```text
If-None-Match: "etag-value"
If-Modified-Since: Wed, 21 Oct 2025 07:28:00 GMT

→ 304 Not Modified（资源未变化，节省带宽）
```

### 内容协商

```text
Accept: application/json
Accept-Language: zh-CN,zh;q=0.9,en;q=0.8
Accept-Encoding: gzip, deflate, br
```

### 速率限制

```text
Rate Limit 响应头:
X-RateLimit-Limit: 100         # 窗口内总请求数
X-RateLimit-Remaining: 95      # 剩余请求数
X-RateLimit-Reset: 1698765432  # 窗口重置时间 (Unix timestamp)
Retry-After: 60                # 被限流后等待秒数
```
