"""
FastAPI 进阶示例 — 路由模块

演示内容：
  - APIRouter 分组路由
  - Depends 依赖注入
  - BackgroundTasks 后台任务
  - WebSocket 双向通信
  - StreamingResponse (SSE) 服务端推送
  - 统一异常处理与自定义错误
  - 中间件：CORS、请求耗时统计
"""

import asyncio
import json
import time
import uuid
from collections.abc import AsyncGenerator
from datetime import datetime
from typing import Annotated

from fastapi import (
    APIRouter,
    BackgroundTasks,
    Depends,
    FastAPI,
    HTTPException,
    Query,
    Request,
    WebSocket,
    WebSocketDisconnect,
)
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse, StreamingResponse
from starlette.middleware.base import BaseHTTPMiddleware

from dependencies import get_current_active_user, get_db, pagination, verify_token
from models import (
    ApiResponse,
    ItemCreate,
    ItemResponse,
    ItemUpdate,
    MessageEvent,
    PaginatedResponse,
    UserInfo,
)

# =============================================================================
# 应用初始化
# =============================================================================

app = FastAPI(
    title="FastAPI 进阶示例",
    description="依赖注入 / 中间件 / 后台任务 / WebSocket / SSE / 统一异常处理",
    version="1.0.0",
)

# =============================================================================
# 中间件
# =============================================================================

# ----- 1. CORS 中间件 -----
# 在开发阶段放行所有来源；生产环境应配置白名单
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],             # 允许的前端域名（* 仅限开发）
    allow_credentials=True,          # 允许携带 Cookie
    allow_methods=["*"],             # 允许的 HTTP 方法
    allow_headers=["*"],             # 允许的请求头
)


# ----- 2. 自定义耗时统计中间件 -----
class TimingMiddleware(BaseHTTPMiddleware):
    """在每个响应头中添加服务器处理耗时"""

    async def dispatch(self, request: Request, call_next):
        start = time.perf_counter()
        response = await call_next(request)
        elapsed_ms = round((time.perf_counter() - start) * 1000, 2)
        response.headers["X-Process-Time"] = f"{elapsed_ms}ms"
        return response


app.add_middleware(TimingMiddleware)

# =============================================================================
# 全局异常处理器
# =============================================================================

# ----- 自定义业务异常 -----
class BusinessError(Exception):
    """业务逻辑异常"""

    def __init__(self, code: int = 10000, message: str = "业务异常"):
        self.code = code
        self.message = message


@app.exception_handler(BusinessError)
async def business_error_handler(request: Request, exc: BusinessError):
    """捕获 BusinessError，返回统一格式"""
    return JSONResponse(
        status_code=200,                    # HTTP 层面返回 200
        content={
            "code": exc.code,
            "message": exc.message,
            "data": None,
        },
    )


@app.exception_handler(HTTPException)
async def http_exception_handler(request: Request, exc: HTTPException):
    """捕获所有 HTTPException，返回统一格式"""
    return JSONResponse(
        status_code=exc.status_code,
        content={
            "code": exc.status_code,
            "message": exc.detail,
            "data": None,
        },
    )


# =============================================================================
# 内存存储
# =============================================================================

items_db: list[ItemResponse] = []
next_id: int = 1


# =============================================================================
# 公共路由（不需要鉴权）
# =============================================================================

@app.get("/")
def root():
    return {"message": "FastAPI 进阶示例 🚀", "docs": "/docs"}


@app.get("/health")
def health_check():
    """健康检查"""
    return {"status": "ok", "timestamp": datetime.now().isoformat()}


# =============================================================================
# 模块化路由 — 商品管理（需要鉴权）
# =============================================================================

items_router = APIRouter(
    prefix="/items",
    tags=["商品管理"],
    dependencies=[Depends(get_current_active_user)],  # 整个 router 统一鉴权
)


@items_router.get("/", response_model=PaginatedResponse)
def list_items(
    # 分页参数 — 声明在路由参数中，由 pagination 依赖解析
    skip: int = Query(default=0, ge=0),
    limit: int = Query(default=20, ge=1, le=100),
    # 注入数据库会话（模拟）
    db=Depends(get_db),
):
    """
    获取商品列表 — 需要鉴权 + 数据库连接

    Query 参数：
      - skip / limit: 分页
    Headers:
      - Authorization: Bearer <token>
    """
    total = len(items_db)
    items = items_db[skip : skip + limit]
    return PaginatedResponse(
        items=items,
        total=total,
        skip=skip,
        limit=limit,
    )


@items_router.get("/{item_id}", response_model=ItemResponse)
def get_item(item_id: int):
    """根据 ID 获取单个商品"""
    for item in items_db:
        if item.id == item_id:
            return item
    raise HTTPException(status_code=404, detail=f"商品 {item_id} 不存在")


@items_router.post("/", response_model=ItemResponse, status_code=201)
def create_item(
    payload: ItemCreate,
    background_tasks: BackgroundTasks,
):
    """
    创建商品 + 后台任务

    创建成功后，后台会异步执行"通知"逻辑（模拟发消息），
    不会阻塞主流程，用户立即收到响应。
    """
    global next_id
    new_item = ItemResponse(
        id=next_id,
        created_at=datetime.now(),
        **payload.model_dump(),
    )
    items_db.append(new_item)
    next_id += 1

    # ---- 后台任务 ----
    # add_task: 第一个参数是函数，后面是位置/关键字参数
    background_tasks.add_task(_send_creation_notification, new_item.id, new_item.name)

    return new_item


def _send_creation_notification(item_id: int, name: str) -> None:
    """
    模拟后台通知逻辑

    实际项目中可替换为：
      - 发送消息队列（RocketMQ / Kafka）
      - 发送邮件 / 钉钉通知
      - 写操作日志
    """
    print(f"[后台任务] 商品创建通知 — ID: {item_id}, 名称: {name}")


@items_router.put("/{item_id}", response_model=ItemResponse)
def update_item(item_id: int, payload: ItemUpdate):
    """更新商品 — 仅更新请求中非 None 的字段"""
    for index, item in enumerate(items_db):
        if item.id == item_id:
            updated = item.model_copy(update=payload.model_dump(exclude_unset=True))
            items_db[index] = updated
            return updated
    raise HTTPException(status_code=404, detail=f"商品 {item_id} 不存在")


@items_router.delete("/{item_id}", status_code=204)
def delete_item(item_id: int):
    """删除商品"""
    for index, item in enumerate(items_db):
        if item.id == item_id:
            items_db.pop(index)
            return
    raise HTTPException(status_code=404, detail=f"商品 {item_id} 不存在")


# 注册路由
app.include_router(items_router)

# =============================================================================
# 模拟业务异常
# =============================================================================

@app.get("/test/business-error")
def test_business_error():
    """触发自定义业务异常"""
    raise BusinessError(code=50001, detail="余额不足，请先充值")


@app.get("/test/http-error")
def test_http_error():
    """触发 HTTP 400 错误"""
    raise HTTPException(status_code=400, detail="参数校验失败")


# =============================================================================
# WebSocket 双向通信
# =============================================================================

# 简单的连接池：room -> [websocket]
ws_connections: dict[str, list[WebSocket]] = {}


@app.websocket("/ws/{room}")
async def websocket_endpoint(websocket: WebSocket, room: str):
    """
    WebSocket Echo 示例

    - 客户端连接时自动加入房间
    - 收到消息后广播给同房间所有人
    - 断开连接自动清理

    连接地址：ws://localhost:8000/ws/general
    测试工具：https://websocketking.com/
    """
    # 接受连接并加入房间
    await websocket.accept()
    ws_connections.setdefault(room, []).append(websocket)
    print(f"[WS] 用户加入房间 {room}，当前在线: {len(ws_connections[room])}")

    try:
        while True:
            # 接收客户端消息（文本帧）
            data = await websocket.receive_text()
            print(f"[WS] 收到消息: {data}")

            # 构造响应
            event = MessageEvent(
                event="message",
                data=f"[{room}] {data}",
                timestamp=datetime.now(),
            )
            # 广播给房间内所有连接
            payload = event.model_dump_json()
            for conn in ws_connections[room]:
                await conn.send_text(payload)

    except WebSocketDisconnect:
        # 断开连接时从房间中移除
        ws_connections[room].remove(websocket)
        print(f"[WS] 用户离开房间 {room}，当前在线: {len(ws_connections[room])}")
        if not ws_connections[room]:
            del ws_connections[room]


# =============================================================================
# StreamingResponse — Server-Sent Events (SSE)
# =============================================================================

@app.get("/sse/events")
async def sse_events(
    # 可选：通过鉴权保护 SSE 接口
    token: str | None = Query(default=None),
):
    """
    Server-Sent Events (SSE) 示例

    SSE 是一种单向、服务端主动推送的通信方式，适合：
      - 实时日志流
      - AI 流式输出（LLM chat）
      - 进度通知

    用法（浏览器端）：
      const source = new EventSource('/sse/events');
      source.onmessage = (e) => console.log(e.data);
    """
    # 简单鉴权检查
    if token and token not in ("admin-token-123", "user-token-456"):
        raise HTTPException(status_code=401, detail="无效 Token")

    async def event_generator() -> AsyncGenerator[str, None]:
        """SSE 事件生成器 — 每条消息遵循 SSE 协议格式"""
        for i in range(10):
            # SSE 协议：每个事件以 "data: xxx\n\n" 结尾
            payload = json.dumps(
                {
                    "id": i,
                    "message": f"第 {i+1} 条推送消息",
                    "timestamp": datetime.now().isoformat(),
                },
                ensure_ascii=False,
            )
            yield f"data: {payload}\n\n"
            # 模拟异步推送间隔
            await asyncio.sleep(1)

        # SSE 协议：发送结束事件
        yield "data: [DONE]\n\n"

    return StreamingResponse(
        event_generator(),
        media_type="text/event-stream",       # SSE 标准 MIME 类型
        headers={
            "Cache-Control": "no-cache",        # 禁用缓存
            "Connection": "keep-alive",
            "X-Accel-Buffering": "no",          # Nginx 代理时禁用缓冲
        },
    )


# =============================================================================
# 启动时填充示例数据
# =============================================================================

@app.on_event("startup")
def seed_data():
    """应用启动时创建示例数据"""
    global next_id
    samples = [
        {"name": "机械键盘", "description": "Cherry 青轴 87 键", "price": 599.0, "is_on_sale": True, "tags": ["外设", "键盘"]},
        {"name": "无线鼠标", "description": "蓝牙 5.0 人体工学", "price": 199.0, "is_on_sale": True, "tags": ["外设", "鼠标"]},
        {"name": "4K 显示器", "description": "27 英寸 IPS 面板", "price": 2499.0, "is_on_sale": False, "tags": ["显示器"]},
    ]
    for s in samples:
        items_db.append(
            ItemResponse(id=next_id, created_at=datetime.now(), **s)
        )
        next_id += 1
