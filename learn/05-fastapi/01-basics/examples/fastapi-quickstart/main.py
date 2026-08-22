"""
FastAPI 入门示例 — 完整 CRUD

功能:
  - GET    /items/{id}              路径参数：获取单个商品
  - GET    /items/?skip=0&limit=10  查询参数：分页获取商品列表
  - POST   /items/                  请求体：创建新商品
  - PUT    /items/{id}              更新商品
  - DELETE /items/{id}              删除商品

启动方式:
  uvicorn main:app --reload
"""

from datetime import datetime
from fastapi import FastAPI, HTTPException, Query

from models import Item, ItemCreate, ItemUpdate

# ========== 初始化应用 ==========

app = FastAPI(
    title="FastAPI 入门示例",
    description="一个带有完整 CRUD 和内存存储的演示应用",
    version="1.0.0",
)

# ========== 内存存储 ==========

items_db: list[Item] = []   # 用列表模拟数据库
next_id: int = 1            # 自增 ID 计数器


# ========== 路由 ==========

@app.get("/")
def root():
    """根路径 — 简单的健康检查"""
    return {"message": "欢迎使用 FastAPI 入门示例 🚀"}


# ----- 查询参数示例 -----

@app.get("/items/", response_model=list[Item])
def list_items(
    skip: int = Query(default=0, ge=0, description="跳过的记录数"),
    limit: int = Query(default=10, ge=1, le=100, description="返回的记录数上限"),
):
    """
    分页获取商品列表

    Query 参数：
      - skip: 跳过前 N 条
      - limit: 最多返回 N 条（上限 100）
    """
    return items_db[skip : skip + limit]


# ----- 路径参数示例 -----

@app.get("/items/{item_id}", response_model=Item)
def get_item(item_id: int):
    """根据 ID 获取单个商品"""
    for item in items_db:
        if item.id == item_id:
            return item
    # 商品不存在时抛出 404
    raise HTTPException(status_code=404, detail=f"商品 {item_id} 不存在")


# ----- 创建 -----

@app.post("/items/", response_model=Item, status_code=201)
def create_item(payload: ItemCreate):
    """
    创建新商品

    请求体示例：
    {
      "name": "机械键盘",
      "description": "青轴 87 键",
      "price": 399.0,
      "is_on_sale": true
    }
    """
    global next_id
    new_item = Item(
        id=next_id,
        created_at=datetime.now(),
        **payload.model_dump(),  # model_dump() 是 v2 的标准序列化方法
    )
    items_db.append(new_item)
    next_id += 1
    return new_item


# ----- 更新 -----

@app.put("/items/{item_id}", response_model=Item)
def update_item(item_id: int, payload: ItemUpdate):
    """更新商品 — 仅更新请求中非 None 的字段（部分更新）"""
    for index, item in enumerate(items_db):
        if item.id == item_id:
            # 获取请求体中实际传入的字段
            update_data = payload.model_dump(exclude_unset=True)
            # 创建更新后的对象（v2 推荐方式）
            updated = item.model_copy(update=update_data)
            items_db[index] = updated
            return updated
    raise HTTPException(status_code=404, detail=f"商品 {item_id} 不存在")


# ----- 删除 -----

@app.delete("/items/{item_id}", status_code=204)
def delete_item(item_id: int):
    """删除商品 — 成功返回 204 No Content"""
    for index, item in enumerate(items_db):
        if item.id == item_id:
            items_db.pop(index)
            return  # 204 响应体为空
    raise HTTPException(status_code=404, detail=f"商品 {item_id} 不存在")


# ========== 启动时自动填充示例数据 ==========

@app.on_event("startup")
def seed_data():
    """应用启动时创建几条示例数据，方便直接测试"""
    global next_id
    samples = [
        {"name": "机械键盘", "description": "Cherry 青轴", "price": 599.0, "is_on_sale": True},
        {"name": "无线鼠标", "description": "蓝牙 5.0", "price": 199.0, "is_on_sale": True},
        {"name": "4K 显示器", "description": "27 英寸 IPS", "price": 2499.0, "is_on_sale": False},
    ]
    for s in samples:
        items_db.append(Item(id=next_id, created_at=datetime.now(), **s))
        next_id += 1
