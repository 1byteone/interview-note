# 第九章：认证与安全（P0 精通）

> 📖 **参考资料**：[FastAPI Security](https://fastapi.tiangolo.com/tutorial/security/) | [OWASP Top 10](https://owasp.org/www-project-top-ten/) | [python-jose](https://github.com/mpdavis/python-jose) | [passlib](https://passlib.readthedocs.io/)

---

## 9.1 密码哈希：bcrypt

明文存储密码是致命错误。使用 **bcrypt** 加盐哈希——不可逆且足够慢，能有效抵御暴力破解与彩虹表攻击。

```python
# password_hasher.py
from passlib.context import CryptContext

pwd_context = CryptContext(schemes=["bcrypt"], deprecated="auto")

def hash_password(password: str) -> str:
    return pwd_context.hash(password)

def verify_password(plain: str, hashed: str) -> bool:
    return pwd_context.verify(plain, hashed)


hashed = hash_password("my-super-secret-123")
print(hashed)                    # $2b$12$9kF...（每次结果不同，因为盐随机）
assert verify_password("my-super-secret-123", hashed)   # True
assert verify_password("wrong-pass", hashed) is False   # False
```

| 算法 | 加盐 | 可调成本 | 推荐度 |
|------|------|----------|--------|
| bcrypt | ✅ | rounds（默认 12） | ✅ 推荐 |
| Argon2 | ✅ | 内存/时间/并行 | ✅ 更安全 |
| scrypt | ✅ | 内存/时间 | ✅ 可选 |
| MD5 / SHA-1 | ❌ | 无 | ❌ 禁止 |

> **成本建议**：生产环境 bcrypt rounds 取 12–14。每 +1 倍耗时翻倍，过高的 rounds 会拖慢登录接口（可用异步执行避免阻塞事件循环）。

## 9.2 JWT Access + Refresh Token

JWT 用于无状态认证：服务端不保存会话，令牌自带签名与过期时间。

```python
# jwt_service.py
from datetime import datetime, timedelta, timezone
from jose import jwt, JWTError

SECRET_KEY = "change-me-in-production"   # 生产环境从环境变量读取
ALGORITHM = "HS256"
ACCESS_TOKEN_EXPIRE_MINUTES = 15
REFRESH_TOKEN_EXPIRE_DAYS = 7

def create_access_token(data: dict) -> str:
    payload = {**data, "type": "access",
               "exp": datetime.now(timezone.utc) + timedelta(minutes=ACCESS_TOKEN_EXPIRE_MINUTES)}
    return jwt.encode(payload, SECRET_KEY, algorithm=ALGORITHM)

def create_refresh_token(data: dict) -> str:
    payload = {**data, "type": "refresh",
               "exp": datetime.now(timezone.utc) + timedelta(days=REFRESH_TOKEN_EXPIRE_DAYS)}
    return jwt.encode(payload, SECRET_KEY, algorithm=ALGORITHM)

def decode_token(token: str) -> dict:
    try:
        return jwt.decode(token, SECRET_KEY, algorithms=[ALGORITHM])
    except JWTError:
        raise ValueError("Invalid or expired token")
```

| 概念 | Access Token | Refresh Token |
|------|--------------|---------------|
| 有效期 | 短（15–30 分钟） | 长（7–30 天） |
| 传输 | 每次请求的 Authorization Header | 仅在刷新端点传输 |
| 存储 | 内存（JS 变量） | HttpOnly Cookie |
| 撤销 | 靠短有效期兜底 | 服务端 Redis 黑名单 / 轮换机制 |

## 9.3 OAuth2 密码模式

FastAPI 内置 `OAuth2PasswordBearer` / `OAuth2PasswordRequestForm`，配合 9.1 与 9.2 的模块即可实现标准 OAuth2 密码流。

```python
# auth_scheme.py
from fastapi import Depends, HTTPException, status
from fastapi.security import OAuth2PasswordBearer
from jwt_service import decode_token

oauth2_scheme = OAuth2PasswordBearer(tokenUrl="/auth/login")

async def get_current_user(token: str = Depends(oauth2_scheme)) -> dict:
    try:
        payload = decode_token(token)
    except ValueError:
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "Invalid or expired token")
    if payload.get("type") != "access":
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "Not an access token")
    return {"sub": payload["sub"], "roles": payload.get("roles", [])}
```

```python
# auth_router.py
from fastapi import APIRouter, Depends, HTTPException
from fastapi.security import OAuth2PasswordRequestForm
from password_hasher import verify_password
from jwt_service import create_access_token, create_refresh_token

router = APIRouter(prefix="/auth", tags=["auth"])

@router.post("/login")
async def login(form_data: OAuth2PasswordRequestForm = Depends()):
    # 生产环境：查询数据库并替换这段模拟逻辑
    user = FAKE_USERS_DB.get(form_data.username)
    if not user or not verify_password(form_data.password, user["password"]):
        raise HTTPException(status_code=401, detail="Incorrect username or password")
    claims = {"sub": user["username"], "roles": user["roles"]}
    return {
        "access_token": create_access_token(claims),
        "refresh_token": create_refresh_token(claims),
        "token_type": "bearer",
    }

@router.post("/refresh")
async def refresh(refresh_token: str):
    try:
        payload = decode_token(refresh_token)
    except ValueError:
        raise HTTPException(status_code=401, detail="Invalid refresh token")
    if payload.get("type") != "refresh":
        raise HTTPException(status_code=401, detail="Not a refresh token")
    claims = {"sub": payload["sub"], "roles": payload.get("roles", [])}
    return {"access_token": create_access_token(claims), "token_type": "bearer"}
```

## 9.4 RBAC 权限模型

```python
# rbac.py
from fastapi import Depends, HTTPException, status
from auth_scheme import get_current_user

# 角色 → 权限集合（扁平化权限，便于判断）
ROLE_PERMISSIONS = {
    "viewer": {"read"},
    "editor": {"read", "write"},
    "admin":  {"read", "write", "delete", "manage"},
}

def require_permission(permission: str):
    def dependency(current_user: dict = Depends(get_current_user)):
        allowed = set()
        for role in current_user.get("roles", []):
            allowed |= ROLE_PERMISSIONS.get(role, set())
        if permission not in allowed:
            raise HTTPException(status.HTTP_403_FORBIDDEN, f"Permission '{permission}' required")
        return current_user
    return dependency


# 使用示例
@router.delete("/items/{item_id}", dependencies=[Depends(require_permission("delete"))])
async def delete_item(item_id: int):
    return {"message": f"Item {item_id} deleted"}
```

**RBAC vs ABAC**：RBAC 按"角色"授权，适合组织型权限；**ABAC（属性基）** 按"用户属性 + 资源属性 + 环境"的规则引擎授权，适合资源粒度细的场景（如"只能编辑自己创建的文章"）。

```python
# abac_policy.py —— ABAC 策略（伪代码）
def can_edit_article(user: dict, article: dict) -> bool:
    return (
        ("admin" in user["roles"])          # 管理员可编辑一切
        or ("editor" in user["roles"] and
            article["author_id"] == user["sub"])   # 编辑只能改自己的
        or (article["status"] == "draft")   # 草稿任何人可编辑
    )
```

## 9.5 安全清单

| 威胁 | 风险描述 | 防护措施 |
|------|----------|----------|
| **SQL 注入** | 拼接 SQL 被注入恶意语句 | 只用 ORM / 参数化查询，禁止字符串拼接 SQL |
| **XSS** | 恶意脚本注入页面窃取数据 | 模板引擎自动转义 + `Content-Security-Policy` 响应头 |
| **CSRF** | 伪造请求利用已登录身份 | SameSite / 需 CSRF Token，JWT 场景通常用 Bearer 规避 |
| **SSRF** | 服务端发起请求到内网 | URL 白名单、禁止解析到内网/云元数据地址（169.254.169.254） |
| **CORS** | 恶意站点跨域读取接口 | `allow_origins` 白名单，禁 `"*"` + 不配 `allow_credentials` |
| **限流** | 暴力破解、爬虫、DDoS | slowapi / 网关限流（如登录 5 次/分钟）、Redis 滑动窗口 |
| **敏感数据** | 日志/异常堆栈泄露密钥 | 关闭 debug、.env 管理密钥、日志脱敏 |
| **依赖漏洞** | 已知 CVE 被利用 | `pip-audit` / `safety` 定期扫描，依赖升级 |

```python
# 限流示例（slowapi）
from slowapi import Limiter, _rate_limit_exceeded_handler
from slowapi.util import get_remote_address

limiter = Limiter(key_func=get_remote_address)
app.state.limiter = limiter
app.add_exception_handler(429, _rate_limit_exceeded_handler)

@app.post("/auth/login")
@limiter.limit("5/minute")
async def login(request: Request, form_data: OAuth2PasswordRequestForm = Depends()):
    ...
```

```python
# CORS 白名单配置
from fastapi.middleware.cors import CORSMiddleware

app.add_middleware(
    CORSMiddleware,
    allow_origins=["https://your-frontend.com"],   # 生产禁 "*"
    allow_credentials=True,
    allow_methods=["GET", "POST", "PUT", "DELETE"],
    allow_headers=["Authorization", "Content-Type"],
)
```

---

## 必读资源

| 资源 | 说明 |
|------|------|
| [FastAPI Security](https://fastapi.tiangolo.com/tutorial/security/) | FastAPI 官方认证/安全教程 |
| [OWASP Top 10](https://owasp.org/www-project-top-ten/) | Web 应用十大安全风险 |
| [OWASP Cheat Sheet Series](https://cheatsheetseries.owasp.org/) | 各主题安全速查表（含 CSRF/SSRF） |
| [passlib](https://passlib.readthedocs.io/) | 密码哈希库（bcrypt/argon2） |
| [python-jose](https://github.com/mpdavis/python-jose) | JOSE/JWT 实现 |
| [RFC 8725: JWT Best Practices](https://datatracker.ietf.org/doc/html/rfc8725) | JWT 生产级最佳实践 |
| [slowapi](https://github.com/laurentS/slowapi) | FastAPI 限流中间件 |
| [pip-audit](https://github.com/pypa/pip-audit) | Python 依赖漏洞扫描 |