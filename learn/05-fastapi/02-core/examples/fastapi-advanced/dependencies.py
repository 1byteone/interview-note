"""
依赖注入函数集合

FastAPI 的依赖注入系统：
  - 可复用的公共逻辑（数据库连接、鉴权、分页）
  - 通过 Depends() 声明式注册
  - 支持嵌套依赖与异步
"""

from collections.abc import AsyncGenerator, Generator
from typing import Annotated

from fastapi import Depends, HTTPException, Query, status


# ========== 数据库 Session（Mock）==========

class MockDBSession:
    """模拟的数据库会话对象"""

    def __init__(self):
        self.is_active = True

    def execute(self, query: str) -> list[dict]:
        """模拟执行 SQL 查询"""
        return [{"id": 1, "name": "mock_data"}]

    def close(self) -> None:
        self.is_active = False

    def __enter__(self):
        return self

    def __exit__(self, *args):
        self.close()


def get_db() -> Generator[MockDBSession, None, None]:
    """
    数据库会话依赖 — 每个请求创建一个会用、请求结束后自动关闭

    用法：def route(db: MockDBSession = Depends(get_db))
    """
    db = MockDBSession()
    try:
        yield db           # yield 之前的代码在"进入"阶段执行
    finally:
        db.close()         # yield 之后的代码在"退出"阶段执行（相当于 finally）


# ========== 鉴权依赖 ==========

# 简化的 token -> 用户映射
_FAKE_TOKENS: dict[str, dict] = {
    "admin-token-123": {"user_id": 1, "username": "admin", "role": "admin"},
    "user-token-456": {"user_id": 2, "username": "zhangsan", "role": "user"},
}


def verify_token(token: str | None = None) -> dict:
    """
    鉴权依赖 — 校验 Bearer Token

    从 Header 中取 token（在实际项目中由 Security(HTTPBearer()) 实现）。
    此处简化为直接传入字符串。

    用法：def route(user: dict = Depends(verify_token))
    """
    if token is None:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="未提供认证 Token",
            headers={"WWW-Authenticate": "Bearer"},
        )
    user_info = _FAKE_TOKENS.get(token)
    if user_info is None:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="无效的 Token",
        )
    return user_info


# ========== 分页依赖 ==========

# 用 Annotated + Depends 组合，实现声明式分页
# FastAPI 会自动将 Query 参数解析并注入
PaginatedParams = Annotated[
    dict,  # 返回 skip 和 limit 的字典
]


def pagination(
    skip: int = Query(default=0, ge=0, description="跳过的记录数"),
    limit: int = Query(default=20, ge=1, le=100, description="返回记录数上限"),
) -> dict[str, int]:
    """
    分页依赖 — 封装 skip / limit 逻辑

    用法：def route(paging: dict = Depends(pagination))
    返回：{"skip": 0, "limit": 20}
    """
    return {"skip": skip, "limit": limit}


# ========== 业务逻辑依赖 ==========

def get_current_active_user(
    token_info: dict = Depends(verify_token),
) -> dict:
    """
    获取当前登录用户 — 组合依赖示例

    依赖链：verify_token → get_current_active_user
    你只需要把 get_current_active_user 注入路由，
    FastAPI 会自动沿着依赖链依次调用。
    """
    if token_info["role"] not in ("admin", "user"):
        raise HTTPException(status_code=403, detail="权限不足")
    return token_info
