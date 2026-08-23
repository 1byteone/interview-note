"""
FastAPI 进阶示例 — Pydantic 数据模型

包含：
  - 基础/创建/更新/响应模型
  - 统一 API 响应包装器
"""

from datetime import datetime
from pydantic import BaseModel, ConfigDict, Field


# ========== 基础模型 ==========

class ItemBase(BaseModel):
    """商品基础字段"""
    name: str = Field(..., min_length=1, max_length=100, description="商品名称")
    description: str | None = Field(None, max_length=500, description="商品描述")
    price: float = Field(..., gt=0, description="价格（必须大于 0）")
    is_on_sale: bool = Field(default=False, description="是否在售")
    tags: list[str] = Field(default_factory=list, description="标签列表")


class ItemCreate(ItemBase):
    """创建商品 — 请求体模型"""
    pass


class ItemUpdate(BaseModel):
    """更新商品 — 所有字段可选"""
    name: str | None = Field(None, min_length=1, max_length=100)
    description: str | None = None
    price: float | None = Field(None, gt=0)
    is_on_sale: bool | None = None
    tags: list[str] | None = None


class ItemResponse(ItemBase):
    """商品响应模型 — 包含服务端生成的字段"""
    id: int
    created_at: datetime

    model_config = ConfigDict(from_attributes=True)


# ========== 统一响应包装器 ==========

class ApiResponse(BaseModel):
    """标准 API 响应格式"""
    code: int = Field(default=0, description="业务状态码，0 表示成功")
    message: str = Field(default="ok", description="提示信息")
    data: object | None = None  # 实际使用时可用泛型


class PaginatedResponse(BaseModel):
    """分页响应格式"""
    items: list[ItemResponse]
    total: int
    skip: int
    limit: int


# ========== 用户相关模型 ==========

class UserInfo(BaseModel):
    """用户信息"""
    user_id: int
    username: str
    role: str

    model_config = ConfigDict(from_attributes=True)


class MessageEvent(BaseModel):
    """WebSocket 消息事件"""
    event: str           # 事件类型: message | join | leave
    data: str            # 消息内容
    timestamp: datetime | None = None


# ========== 启动参数 ==========

class StartupConfig(BaseModel):
    """应用启动配置"""
    title: str = "FastAPI 进阶示例"
    version: str = "1.0.0"
    debug: bool = False
