# 第二十二章：架构设计（P0 精通）

> 📖 **参考资料**：[Microservices Patterns](https://microservices.io/patterns/) | [Event Sourcing](https://martinfowler.com/eaaDev/EventSourcing.html) | [Clean Architecture](https://blog.cleancoder.com/uncle-bob/2012/08/13/the-clean-architecture.html)

---

## 22.1 微服务架构

```
                        ┌─────────────┐
                        │   API GW    │
                        │  (Kong/     │
                        │   Traefik)  │
                        └──────┬──────┘
                               │
              ┌────────────────┼────────────────┐
              │                │                │
       ┌──────▼──────┐  ┌─────▼──────┐  ┌──────▼──────┐
       │  User Svc   │  │ Order Svc  │  │ Product Svc │
       │  (FastAPI)  │  │ (FastAPI)  │  │  (FastAPI)  │
       └──────┬──────┘  └─────┬──────┘  └──────┬──────┘
              │                │                │
       ┌──────▼──────┐  ┌─────▼──────┐  ┌──────▼──────┐
       │  PostgreSQL │  │ PostgreSQL │  │  MongoDB    │
       │  (Users DB) │  │ (Orders DB)│  │ (Products)  │
       └─────────────┘  └────────────┘  └─────────────┘
```

**微服务拆分原则**：

| 原则 | 说明 | 示例 |
|------|------|------|
| 单一职责 | 每个服务只负责一个业务域 | User Service 只管用户 |
| 数据自治 | 每个服务拥有独立数据库 | 订单服务不能直接查用户表 |
| 通信解耦 | 服务间通过 API 或消息通信 | REST 同步 / MQ 异步 |
| 独立部署 | 可独立构建、测试、部署 | 更新用户服务不影响订单服务 |

---

## 22.2 服务发现

```python
# registry/service_registry.py
"""简易服务注册表 — 生产环境请使用 Consul / etcd / Nacos。"""

import time
import httpx
from dataclasses import dataclass, field
from datetime import datetime


@dataclass
class ServiceInstance:
    name: str
    host: str
    port: int
    health_path: str = "/health"
    metadata: dict = field(default_factory=dict)
    last_heartbeat: datetime = field(default_factory=datetime.utcnow)


class ServiceRegistry:
    """内存版服务注册表，用于演示服务发现核心逻辑。"""

    def __init__(self):
        self._services: dict[str, list[ServiceInstance]] = {}

    def register(self, instance: ServiceInstance) -> None:
        """注册服务实例。"""
        self._services.setdefault(instance.name, []).append(instance)

    def deregister(self, name: str, host: str, port: int) -> None:
        """注销服务实例。"""
        if name in self._services:
            self._services[name] = [
                s for s in self._services[name]
                if not (s.host == host and s.port == port)
            ]

    def discover(self, name: str) -> list[ServiceInstance]:
        """发现所有健康的实例（简易版：过滤心跳超时的）。"""
        instances = self._services.get(name, [])
        now = datetime.utcnow()
        return [
            s for s in instances
            if (now - s.last_heartbeat).seconds < 30
        ]

    def heartbeat(self, name: str, host: str, port: int) -> None:
        """更新心跳时间。"""
        for instance in self._services.get(name, []):
            if instance.host == host and instance.port == port:
                instance.last_heartbeat = datetime.utcnow()


# ── 使用示例 ──
registry = ServiceRegistry()
registry.register(ServiceInstance(name="order-svc", host="10.0.0.2", port=8000))
registry.register(ServiceInstance(name="order-svc", host="10.0.0.3", port=8000))

# 简单轮询负载均衡
instances = registry.discover("order-svc")
target = instances[0] if instances else None
```

---

## 22.3 事件驱动架构

```python
# core/event_bus.py
"""轻量级事件总线 — 生产环境可替换为 RabbitMQ / Kafka。"""

import asyncio
import logging
from dataclasses import dataclass, field
from datetime import datetime
from typing import Any, Callable, Coroutine
from uuid import uuid4

logger = logging.getLogger(__name__)


@dataclass
class Event:
    """事件基类。"""
    event_type: str
    payload: dict[str, Any]
    event_id: str = field(default_factory=lambda: str(uuid4()))
    timestamp: str = field(default_factory=lambda: datetime.utcnow().isoformat())


# 事件类型常量
class EventTypes:
    USER_CREATED = "user.created"
    USER_DELETED = "user.deleted"
    ORDER_CREATED = "order.created"
    ORDER_COMPLETED = "order.completed"
    INVENTORY_UPDATED = "inventory.updated"


EventHandler = Callable[[Event], Coroutine[Any, Any, None]]


class EventBus:
    """异步事件总线 — 支持发布/订阅模式。"""

    def __init__(self):
        self._handlers: dict[str, list[EventHandler]] = {}

    def subscribe(self, event_type: str, handler: EventHandler) -> None:
        """订阅事件。"""
        self._handlers.setdefault(event_type, []).append(handler)
        logger.info(f"Subscribed: {handler.__name__} -> {event_type}")

    async def publish(self, event: Event) -> None:
        """发布事件，同步调用所有订阅者。"""
        logger.info(f"Publishing event: {event.event_type} [{event.event_id}]")
        handlers = self._handlers.get(event.event_type, [])
        for handler in handlers:
            try:
                await handler(event)
            except Exception as e:
                logger.error(f"Handler {handler.__name__} failed: {e}")


# ── 全局事件总线实例 ──
event_bus = EventBus()


# ── 事件处理器示例 ──
async def on_user_created(event: Event) -> None:
    """用户创建后，发送欢迎邮件。"""
    user_email = event.payload["email"]
    logger.info(f"📧 Sending welcome email to {user_email}")


async def on_order_created(event: Event) -> None:
    """订单创建后，扣减库存。"""
    product_id = event.payload["product_id"]
    quantity = event.payload["quantity"]
    logger.info(f"📦 Deducting stock: product={product_id}, qty={quantity}")


async def on_order_completed(event: Event) -> None:
    """订单完成后，更新用户积分。"""
    user_id = event.payload["user_id"]
    logger.info(f"⭐ Updating loyalty points for user {user_id}")


# ── 注册处理器 ──
event_bus.subscribe(EventTypes.USER_CREATED, on_user_created)
event_bus.subscribe(EventTypes.ORDER_CREATED, on_order_created)
event_bus.subscribe(EventTypes.ORDER_COMPLETED, on_order_completed)
```

---

## 22.4 CQRS 模式

```python
# cqrs/commands.py
"""Command 端 — 处理写操作。"""

from pydantic import BaseModel, EmailStr
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import text


class CreateUserCommand(BaseModel):
    username: str
    email: EmailStr
    password: str


class CreateUserHandler:
    """处理用户创建命令。"""

    def __init__(self, db: AsyncSession):
        self.db = db

    async def handle(self, command: CreateUserCommand) -> dict:
        result = await self.db.execute(
            text("INSERT INTO users (username, email) VALUES (:u, :e) RETURNING id"),
            {"u": command.username, "e": command.email},
        )
        row = result.fetchone()
        await self.db.commit()
        return {"id": row[0], "username": command.username}


# cqrs/queries.py
"""Query 端 — 处理读操作（可使用独立的只读副本）。"""

from pydantic import BaseModel
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import text


class GetUserQuery(BaseModel):
    user_id: int


class ListUsersQuery(BaseModel):
    page: int = 1
    page_size: int = 20


class GetUserHandler:
    """处理用户查询 — 可指向只读副本。"""

    def __init__(self, db: AsyncSession):
        self.db = db

    async def handle(self, query: GetUserQuery) -> dict | None:
        result = await self.db.execute(
            text("SELECT id, username, email FROM users WHERE id = :id"),
            {"id": query.user_id},
        )
        row = result.fetchone()
        if row:
            return {"id": row[0], "username": row[1], "email": row[2]}
        return None
```

```text
CQRS 读写分离示意：

  Write DB (Primary)          Read DB (Replica)
  ┌──────────────┐           ┌──────────────┐
  │  Commands    │ ──复制──▶ │   Queries    │
  │  INSERT      │           │  SELECT      │
  │  UPDATE      │           │  (优化索引)  │
  │  DELETE      │           │              │
  └──────────────┘           └──────────────┘
```

---

## 22.5 DDD 分层结构

```
src/
├── domain/                    # 领域层 — 纯业务逻辑，无外部依赖
│   ├── models/
│   │   ├── user.py           # 聚合根
│   │   ├── order.py          # 聚合根
│   │   └── value_objects/
│   │       ├── email.py      # 值对象
│   │       └── money.py
│   ├── events/
│   │   ├── user_created.py
│   │   └── order_placed.py
│   └── repositories/
│       └── user_repository.py  # Repository 接口（抽象）
│
├── application/               # 应用层 — 编排用例
│   ├── services/
│   │   ├── user_service.py
│   │   └── order_service.py
│   ├── commands/
│   │   ├── create_user.py
│   │   └── place_order.py
│   └── queries/
│       ├── get_user.py
│       └── list_orders.py
│
├── infrastructure/            # 基础设施层 — 技术实现
│   ├── persistence/
│   │   ├── user_repository_impl.py
│   │   └── sqlalchemy_models.py
│   ├── messaging/
│   │   └── kafka_event_publisher.py
│   └── external/
│       └── payment_gateway.py
│
└── interfaces/                # 接口层 — API / CLI / MQ 消费者
    ├── api/
    │   ├── routes/
    │   │   ├── user_routes.py
    │   │   └── order_routes.py
    │   └── schemas/
    │       └── user_schema.py
    └── cli/
        └── commands.py
```

**依赖规则**：`interfaces → application → domain ← infrastructure`

```python
# domain/models/user.py — 纯领域模型，零框架依赖
from dataclasses import dataclass, field
from datetime import datetime
from uuid import UUID, uuid4


@dataclass
class User:
    """用户聚合根。"""
    username: str
    email: str
    id: UUID = field(default_factory=uuid4)
    is_active: bool = True
    created_at: datetime = field(default_factory=datetime.utcnow)
    _domain_events: list = field(default_factory=list, repr=False)

    def deactivate(self) -> None:
        """停用用户 — 领域逻辑封装在聚合根内。"""
        if not self.is_active:
            raise ValueError(f"User {self.id} is already inactive")
        self.is_active = False
        self._domain_events.append({
            "type": "user.deactivated",
            "payload": {"user_id": str(self.id)},
        })

    def get_domain_events(self) -> list:
        events = self._domain_events.copy()
        self._domain_events.clear()
        return events
```

---

## 必读资源

| 资源 | 链接 | 说明 |
|------|------|------|
| 微服务模式 | https://microservices.io/patterns/ | 微服务架构模式目录 |
| DDD 社区 | https://dddcommunity.org/ | 领域驱动设计资源 |
| Event Sourcing | https://martinfowler.com/eaaDev/EventSourcing.html | Martin Fowler 的事件溯源 |
| CQRS 模式 | https://cqrs.files.wordpress.com/2010/11/cqrs_documents.pdf | CQRS 原始论文 |
| Clean Architecture | https://blog.cleancoder.com/uncle-bob/2012/08/13/the-clean-architecture.html | Bob Uncle 的整洁架构 |
