# 测试与调试

> 适用：🎯 进阶
> 目标：掌握 FastAPI 测试框架，学会编写单元测试、集成测试和性能测试

---

## 1. TestClient — 基础测试

FastAPI 内置 `TestClient`，基于 `httpx`，无需启动服务即可测试。

```python
from fastapi import FastAPI
from fastapi.testclient import TestClient

app = FastAPI()


@app.get("/")
def read_root():
    return {"message": "Hello FastAPI"}


client = TestClient(app)


def test_read_root():
    response = client.get("/")
    assert response.status_code == 200
    assert response.json() == {"message": "Hello FastAPI"}
```

> Java 对比：类似 `MockMvc`（Spring MVC Test），但无需启动 Servlet 容器。

---

## 2. 测试 CRUD 接口

```python
# app.py
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel

app = FastAPI()


class Item(BaseModel):
    name: str
    price: float


items_db = {}


@app.post("/items/", status_code=201)
def create_item(item: Item):
    item_id = len(items_db) + 1
    items_db[item_id] = item.model_dump()
    return {"id": item_id, **item.model_dump()}


@app.get("/items/{item_id}")
def get_item(item_id: int):
    if item_id not in items_db:
        raise HTTPException(404, "商品不存在")
    return {"id": item_id, **items_db[item_id]}


# test_app.py
from fastapi.testclient import TestClient
from app import app

client = TestClient(app)


def test_create_item():
    response = client.post("/items/", json={
        "name": "iPhone 15",
        "price": 6999,
    })
    assert response.status_code == 201
    data = response.json()
    assert data["name"] == "iPhone 15"
    assert data["price"] == 6999
    assert "id" in data


def test_get_item_not_found():
    response = client.get("/items/999")
    assert response.status_code == 404
    assert response.json()["detail"] == "商品不存在"


def test_create_item_invalid_price():
    response = client.post("/items/", json={
        "name": "iPhone",
        "price": -100,  # 无效价格
    })
    assert response.status_code == 422  # 验证错误
```

---

## 3. pytest + pytest-asyncio

对于异步路由，需要安装 `pytest-asyncio`：

```bash
pip install pytest pytest-asyncio
```

```python
import pytest
from httpx import AsyncClient, ASGITransport
from fastapi import FastAPI

app = FastAPI()


@app.get("/async")
async def async_endpoint():
    return {"message": "async"}


@pytest.mark.asyncio
async def test_async_endpoint():
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as client:
        response = await client.get("/async")
        assert response.status_code == 200
        assert response.json() == {"message": "async"}
```

### 使用 `TestClient` 测试异步路由

```python
from fastapi.testclient import TestClient


@app.get("/async-items")
async def async_items():
    return {"items": ["iPhone", "MacBook"]}


client = TestClient(app)


def test_async_items():
    # TestClient 内部自动处理异步
    response = client.get("/async-items")
    assert response.status_code == 200
```

> 注意：`TestClient` 内部会处理异步调用，普通测试函数无需 `@pytest.mark.asyncio`。

---

## 4. 测试依赖覆盖

### 覆盖依赖

```python
from fastapi import FastAPI, Depends
from fastapi.testclient import TestClient

app = FastAPI()


# 生产依赖
def get_db():
    return {"type": "production", "url": "postgresql://..."}


# 依赖注入
@app.get("/items/")
def list_items(db: dict = Depends(get_db)):
    return {"db_type": db["type"], "items": []}


# 测试依赖
def get_test_db():
    return {"type": "test", "url": "sqlite:///:memory:"}


# 测试——覆盖依赖
client = TestClient(app)
app.dependency_overrides[get_db] = get_test_db


def test_list_items():
    response = client.get("/items/")
    assert response.status_code == 200
    assert response.json()["db_type"] == "test"
```

### 认证依赖测试

```python
from fastapi import FastAPI, Depends, HTTPException, Header
from fastapi.testclient import TestClient

app = FastAPI()


def get_current_user(authorization: str = Header(...)):
    if not authorization.startswith("Bearer "):
        raise HTTPException(401)
    return {"user_id": 1, "username": "test_user"}


@app.get("/profile/")
def get_profile(user: dict = Depends(get_current_user)):
    return user


# 测试覆盖
def mock_user():
    return {"user_id": 999, "username": "mock_user"}


client = TestClient(app)
app.dependency_overrides[get_current_user] = mock_user


def test_profile():
    response = client.get("/profile/")
    assert response.status_code == 200
    assert response.json()["username"] == "mock_user"
```

---

## 5. 性能测试 — Locust

### 安装

```bash
pip install locust
```

### 编写测试脚本

```python
# locustfile.py
from locust import HttpUser, task, between


class MallUser(HttpUser):
    """模拟商城用户行为"""
    wait_time = between(1, 5)  # 请求间隔 1-5 秒

    @task(3)
    def search_products(self):
        """搜索商品（权重 3）"""
        self.client.get("/products/search?keyword=iPhone&page=1&size=10")

    @task(2)
    def get_product_detail(self):
        """查看商品详情（权重 2）"""
        self.client.get("/products/1")

    @task(1)
    def create_order(self):
        """创建订单（权重 1）"""
        self.client.post("/orders/", json={
            "product_id": 1,
            "quantity": 1,
            "address": "测试地址",
        })

    def on_start(self):
        """用户启动时执行"""
        self.client.post("/auth/login", json={
            "username": "test_user",
            "password": "test_pass",
        })
```

### 运行

```bash
locust -f locustfile.py --host=http://localhost:8000 --web-port=8089
```

访问 `http://localhost:8089`，设置并发用户数和 spawn rate 后启动测试。

---

## 6. 调试技巧

### 使用 PDB 调试

```python
@app.get("/debug/{item_id}")
def debug_endpoint(item_id: int):
    import pdb
    pdb.set_trace()  # 设置断点
    result = {"id": item_id, "name": f"Item {item_id}"}
    return result
```

### 使用 uvicorn 日志

```python
import logging
from fastapi import FastAPI

logging.basicConfig(level=logging.DEBUG)
logger = logging.getLogger("mall-api")

app = FastAPI()


@app.get("/products/{product_id}")
def get_product(product_id: int):
    logger.debug(f"查询商品: {product_id}")
    try:
        # 业务逻辑
        logger.info(f"商品 {product_id} 查询成功")
        return {"id": product_id}
    except Exception as e:
        logger.error(f"查询商品 {product_id} 失败: {e}", exc_info=True)
        raise
```

### 请求/响应日志中间件

```python
from fastapi import FastAPI, Request
import logging
import time

logger = logging.getLogger("access")


@app.middleware("http")
async def log_requests(request: Request, call_next):
    start = time.time()

    body = await request.body()
    logger.info(f"请求: {request.method} {request.url.path} 参数: {body}")

    response = await call_next(request)

    elapsed = time.time() - start
    logger.info(f"响应: {response.status_code} 耗时: {elapsed:.3f}s")

    return response
```

---

## 7. 完整测试示例

```python
import pytest
from fastapi.testclient import TestClient
from app import app, get_db, get_current_user

client = TestClient(app)


# Fixture：设置测试数据库
@pytest.fixture(autouse=True)
def override_deps():
    app.dependency_overrides[get_db] = lambda: {"type": "test"}
    yield
    app.dependency_overrides.clear()


# Fixture：设置测试用户
@pytest.fixture
def auth_headers():
    return {"Authorization": "Bearer test_token"}


class TestProducts:
    """商品接口测试"""

    def test_list_products(self):
        response = client.get("/products/")
        assert response.status_code == 200
        assert "results" in response.json()

    def test_create_product(self):
        response = client.post("/products/", json={
            "name": "测试商品",
            "price": 99.9,
            "category": "电子产品",
        })
        assert response.status_code == 201

    def test_create_product_invalid(self):
        response = client.post("/products/", json={
            "name": "",
            "price": -1,
        })
        assert response.status_code == 422
        errors = response.json()["detail"]
        assert len(errors) > 0

    @pytest.mark.parametrize("page,size,expected", [
        (1, 10, 10),
        (2, 5, 5),
        (100, 10, 0),
    ])
    def test_pagination(self, page, size, expected):
        response = client.get(
            "/products/",
            params={"page": page, "size": size},
        )
        assert response.status_code == 200
        data = response.json()
        assert data["page"] == page
        assert data["size"] == size
        assert len(data["results"]) <= size
```

---

## 本章小结

| 测试类型 | 工具 | 用途 |
|----------|------|------|
| 单元测试 | pytest | 测试单个函数/类 |
| API 测试 | TestClient | 测试 HTTP 接口 |
| 异步测试 | pytest-asyncio | 测试异步路由 |
| 依赖覆盖 | dependency_overrides | 替换 Mock 依赖 |
| 性能测试 | Locust | 并发压测 |
| 调试 | PDB / logging | 运行时调试 |

下一章将介绍生产环境部署。