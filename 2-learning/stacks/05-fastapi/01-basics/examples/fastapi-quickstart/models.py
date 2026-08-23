"""
Pydantic v2 数据模型定义

用于 FastAPI 的请求体校验和响应序列化。
- ItemBase: 基础字段，不含 id
- ItemCreate: 创建时使用的模型（继承 ItemBase）
- Item: 完整模型，包含 id 和 created_at
"""

from datetime import datetime
from pydantic import BaseModel, ConfigDict


class ItemBase(BaseModel):
    """商品基础字段"""
    name: str                          # 商品名称
    description: str | None = None     # 商品描述（可选）
    price: float                       # 价格
    is_on_sale: bool = False           # 是否在售


class ItemCreate(ItemBase):
    """创建商品时的请求体模型"""
    pass


class ItemUpdate(BaseModel):
    """更新商品时的请求体模型 — 所有字段可选"""
    name: str | None = None
    description: str | None = None
    price: float | None = None
    is_on_sale: bool | None = None


class Item(ItemBase):
    """完整商品模型（含服务端生成的字段）"""
    id: int                                # 唯一标识
    created_at: datetime = datetime.now()  # 创建时间

    # Pydantic v2 核心配置：允许从 ORM 对象属性读取值
    model_config = ConfigDict(from_attributes=True)
