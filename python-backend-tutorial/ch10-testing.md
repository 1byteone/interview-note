# 第十章：测试（P0 精通）

> 📖 **参考资料**：[pytest 官方文档](https://docs.pytest.org/) | [pytest-asyncio](https://github.com/pytest-dev/pytest-asyncio) | [FastAPI Testing](https://fastapi.tiangolo.com/tutorial/testing/) | [Testcontainers Python](https://testcontainers-python.readthedocs.io/)

---

## 10.1 测试金字塔

```
         ╱╲
        ╱  ╲           E2E：用户完整流程（最慢最少）
       ╱    ╲
      ╱──────╲
     ╱        ╲       Integration：模块协作 + 真实数据库/队列
    ╱          ╲
   ╱────────────╲
  ╱              ╲      Unit：纯函数/逻辑，最快最多
 ╱────────────────╲
```

| 层级 | 速度 | 数量占比 | 覆盖目标 | 依赖 |
|------|------|----------|----------|------|
| Unit | <10ms | ~70% | 单个函数/类 | Mock 外部依赖 |
| Integration | 100ms–1s | ~20% | 组件协作、数据访问 | Testcontainers |
| E2E | 秒–分钟 | ~10% | 关键用户旅程 | 完整环境 |

**原则**：底层多而快，顶层少而全；先覆盖核心逻辑的单元测试，再补集成与关键 E2E。

## 10.2 pytest 基础与 fixtures

```python
# tests/conftest.py
import asyncio
import pytest
import pytest_asyncio
from httpx import AsyncClient, ASGITransport
from sqlalchemy.ext.asyncio import create_async_engine, async_sessionmaker, AsyncSession

from main import app
from database import Base, get_db

TEST_DATABASE_URL = "sqlite+aiosqlite:///:memory:"

@pytest.fixture(scope="session")
def event_loop():
    loop = asyncio.new_event_loop()
    yield loop
    loop.close()

@pytest_asyncio.fixture(scope="session")
async def test_engine():
    engine = create_async_engine(TEST_DATABASE_URL)
    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)
    yield engine
    await engine.dispose()

@pytest_asyncio.fixture
async def db_session(test_engine):
    maker = async_sessionmaker(test_engine, class_=AsyncSession, expire_on_commit=False)
    async with maker() as session:
        yield session
```

| fixture 特性 | 说明 |
|--------------|------|
| `scope="session"` | 整个会话复用（如引擎） |
| `scope="function"`（默认） | 每个测试独立（如事务/会话） |
| `autouse=True` | 无需显式声明即自动注入 |
| fixture 依赖 | 可依赖其他 fixture 按需构建 |

## 10.3 FastAPI TestClient + Dependency Override

```python
# tests/test_users.py
@pytest_asyncio.fixture
async def client(db_session):
    async def override_get_db():
        yield db_session
    app.dependency_overrides[get_db] = override_get_db
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as ac:
        yield ac
    app.dependency_overrides.clear()

@pytest.mark.asyncio
async def test_create_user(client: AsyncClient):
    resp = await client.post("/users/", json={
        "username": "alice", "email": "alice@example.com"})
    assert resp.status_code == 201
    data = resp.json()
    assert data["username"] == "alice"
    assert "id" in data

@pytest.mark.asyncio
async def test_get_user_not_found(client: AsyncClient):
    resp = await client.get("/users/99999")
    assert resp.status_code == 404
    assert resp.json()["detail"] == "User not found"
```

```python
# 覆盖认证依赖
async def fake_current_user():
    return {"sub": "testuser", "roles": ["admin"]}

app.dependency_overrides[get_current_user] = fake_current_user
```

| 依赖 | 覆盖方式 |
|------|----------|
| 数据库会话 | `get_db` → 测试 session |
| 当前用户 | `get_current_user` → 固定测试用户 |
| 外部 HTTP | 替换为 `responses` / `httpx_mock` |
| Redis | 替换为 `fakeredis` |

## 10.4 Mock 与 monkeypatch

```python
# tests/test_emails.py
from unittest.mock import AsyncMock, patch

@pytest.mark.asyncio
async def test_send_welcome_email(client, monkeypatch):
    sent: list[dict] = []

    async def fake_send(to: str, body: str):
        sent.append({"to": to, "body": body})

    monkeypatch.setattr("services.email.send_welcome", fake_send)

    await client.post("/users/", json={"username": "bob",
                                       "email": "bob@example.com"})
    assert len(sent) == 1
    assert sent[0]["to"] == "bob@example.com"

@pytest.mark.asyncio
async def test_payment_gateway_timeout(client):
    with patch("services.payment.charge",
               new=AsyncMock(side_effect=TimeoutError("gateway down"))):
        resp = await client.post("/orders/", json={"amount": 100})
    assert resp.status_code == 503

def test_discount_calculator(monkeypatch):
    monkeypatch.setenv("DISCOUNT_RATE", "0.2")
    assert calculate_discount(100) == 80.0
```

| 工具 | 适用场景 |
|------|----------|
| `unittest.mock.patch` | 替换对象属性/方法 |
| `AsyncMock` | mock 异步函数并 `assert_awaited_once` |
| `monkeypatch` | 替换函数、设置环境变量 |
| `responses` / `respx` | Mock HTTP 请求 |

## 10.5 参数化测试 parametrize

```python
# tests/test_validation.py
from pydantic import BaseModel, EmailStr, ValidationError

class UserCreate(BaseModel):
    username: str
    email: EmailStr
    age: int

@pytest.mark.parametrize("username, email, age, expect_valid", [
    ("alice",   "alice@example.com", 25, True),
    ("ab",      "a@b.com",           18, True),
    ("",        "a@b.com",           20, False),
    ("alice",   "not-an-email",      25, False),
    ("alice",   "a@b.com",           -1, False),
    ("alice",   "a@b.com",           0,  False),
    ("a" * 51,  "a@b.com",           25, False),
])
def test_user_create_validation(username, email, age, expect_valid):
    try:
        UserCreate(username=username, email=email, age=age)
        assert expect_valid is True
    except ValidationError:
        assert expect_valid is False
```

> 给用例加 `pytest.param(..., id="case-name")` 能让失败报告一目了然。

## 10.6 Testcontainers 集成测试

```python
# tests/integration/test_db.py
from testcontainers.postgres import PostgresContainer
from sqlalchemy import text

@pytest.fixture(scope="module")
def postgres():
    with PostgresContainer("postgres:16-alpine") as pg:
        yield pg  # 自动拉镜像、启动、清理

@pytest.mark.asyncio
async def test_real_postgres_read_write(postgres):
    url = postgres.get_connection_url().replace(
        "postgresql://", "postgresql+asyncpg://")
    engine = create_async_engine(url)
    async with engine.begin() as conn:
        await conn.execute(text(
            "CREATE TABLE items (id SERIAL PRIMARY KEY, name TEXT NOT NULL)"))
        await conn.execute(text("INSERT INTO items (name) VALUES ('apple')"))
    async with engine.connect() as conn:
        rows = (await conn.execute(text("SELECT name FROM items"))).all()
    assert rows[0][0] == "apple"
    await engine.dispose()
```

| 容器 | 用途 |
|------|------|
| `PostgresContainer` | CRUD / 事务 / 索引行为 |
| `RedisContainer` | 缓存 / 队列集成 |
| `RabbitMqContainer` | 消息队列集成 |
| `LocalStackContainer` | AWS S3/SQS/SNS 模拟 |
| `KafkaContainer` | 流处理集成 |

> SQLite 无法复现的 PG 特性（JSONB、GIN 索引、并发事务）必须在真实容器中验证。

## 10.7 覆盖率

```bash
pip install pytest-cov pytest-asyncio testcontainers

# 终端报告 + HTML 报告
pytest --cov=app --cov-report=term-missing --cov-report=html:coverage_html

# 覆盖率门禁：低于 80% 直接失败（CI 中常用）
pytest --cov=app --cov-fail-under=80
```

```toml
# pyproject.toml
[tool.coverage.run]
source = ["app"]
omit = ["tests/*", "app/migrations/*", "app/main.py"]

[tool.coverage.report]
fail_under = 80
show_missing = true
exclude_lines = ["pragma: no cover", "if TYPE_CHECKING:"]
```

| 层级 | 建议最低覆盖率 |
|------|----------------|
| 核心业务逻辑 | ≥ 95% |
| API 路由 | ≥ 85% |
| 数据访问层 | ≥ 80% |
| 整体 | ≥ 80% |

> 覆盖率是**指南针而非目标**：重点覆盖分支与异常路径，可用 `--cov-branch` 查看分支覆盖。

---

## 必读资源

| 资源 | 说明 |
|------|------|
| [pytest 官方文档](https://docs.pytest.org/en/stable/) | fixtures / parametrize / 插件系统 |
| [pytest-asyncio](https://github.com/pytest-dev/pytest-asyncio) | asyncio 测试支持 |
| [FastAPI Testing](https://fastapi.tiangolo.com/tutorial/testing/) | TestClient + Dependency Override |
| [Testcontainers Python](https://testcontainers-python.readthedocs.io/) | Docker 容器化集成测试 |
| [pytest-cov](https://github.com/pytest-dev/pytest-cov) | 覆盖率插件 |
| [httpx](https://www.python-httpx.org/) | AsyncClient（TestClient 底层） |
| [real-world-testing](https://github.com/testdrivenio/real-world-testing-python) | 实战测试项目示例 |