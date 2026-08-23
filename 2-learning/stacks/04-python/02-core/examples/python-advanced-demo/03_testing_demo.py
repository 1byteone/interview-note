"""
pytest 测试演示
================
涵盖：基础测试函数、Fixtures、Parametrize、Mock、异步测试

运行方式: python -m pytest 03_testing_demo.py -v
需要安装: pip install pytest pytest-asyncio
"""

import asyncio
from unittest import mock

import pytest


# ==================== 被测代码 ====================

class Calculator:
    """计算器 — 被测对象"""

    def add(self, a: float, b: float) -> float:
        return a + b

    def divide(self, a: float, b: float) -> float:
        if b == 0:
            raise ValueError("除数不能为 0！")
        return a / b


class UserService:
    """用户服务 — 被测对象"""

    def __init__(self):
        self.users = {}

    def create_user(self, name: str, email: str) -> dict:
        if not name or not email:
            raise ValueError("name 和 email 不能为空")
        user = {"id": len(self.users) + 1, "name": name, "email": email}
        self.users[user["id"]] = user
        return user

    def get_user(self, user_id: int) -> dict | None:
        return self.users.get(user_id)


calculator = Calculator()


# ==================== 1. 基础测试函数 ====================
print_separator = lambda: None  # 仅标记


class TestBasic:
    """基础测试 — 以 def test_ 开头即可被 pytest 发现"""

    def test_add_positive_numbers(self):
        assert calculator.add(1, 2) == 3

    def test_add_negative_numbers(self):
        assert calculator.add(-1, -2) == -3

    def test_add_zero(self):
        assert calculator.add(0, 0) == 0

    def test_divide_normal(self):
        assert calculator.divide(10, 2) == 5

    def test_divide_by_zero_raises(self):
        """测试异常抛出"""
        with pytest.raises(ValueError) as exc_info:
            calculator.divide(1, 0)
        assert "除数不能为 0" in str(exc_info.value)

    def test_create_user_success(self):
        service = UserService()
        user = service.create_user("Alice", "alice@example.com")
        assert user["id"] == 1
        assert user["name"] == "Alice"

    def test_create_user_validation(self):
        service = UserService()
        with pytest.raises(ValueError):
            service.create_user("", "alice@example.com")


# ==================== 2. Fixtures ====================
print_fixture = None  # 仅标记


@pytest.fixture
def user_service():
    """
    Fixture: 提供测试前置条件和清理
    - 每个测试函数都会获得一个新的实例
    - 自动管理生命周期
    """
    print("\n    [fixture] 创建 UserService...")
    service = UserService()
    service.create_user("Seed User", "seed@example.com")  # 预置数据
    yield service
    print("    [fixture] 清理 UserService...")


class TestWithFixture:
    def test_get_seed_user(self, user_service):
        """使用 fixture 注入"""
        user = user_service.get_user(1)
        assert user is not None
        assert user["name"] == "Seed User"

    def test_create_after_seed(self, user_service):
        """fixture 的预置数据在每次测试都是独立的"""
        user = user_service.create_user("Bob", "bob@example.com")
        assert user["id"] == 2  # 因为 seed 用户占用了 id=1


@pytest.fixture(scope="session")
def shared_data():
    """
    scope="session": 整个测试会话只创建一次
    其他选项: function(默认) / class / module / session
    """
    print("\n    [session fixture] 只初始化一次...")
    return {"config": {"timeout": 30, "retries": 3}}


class TestSharedFixture:
    def test_shared_1(self, shared_data):
        assert shared_data["config"]["timeout"] == 30

    def test_shared_2(self, shared_data):
        assert "retries" in shared_data["config"]


# ==================== 3. Parametrize ====================
print_parametrize = None  # 仅标记


@pytest.mark.parametrize(
    "a,b,expected",
    [
        (1, 2, 3),
        (5, 7, 12),
        (10, 15, 25),
        (-5, 5, 0),
        (100, 200, 300),
    ],
    ids=["small", "medium", "larger", "negative", "hundreds"],  # 可选：测试 ID
)
def test_add_parametrized(a: float, b: float, expected: float):
    """同一个测试逻辑，多组数据"""
    assert calculator.add(a, b) == expected


# 多个参数组合（笛卡尔积）
@pytest.mark.parametrize("a", [1, 2])
@pytest.mark.parametrize("b", [10, 20])
def test_combined_params(a: int, b: int):
    """两个 parametrize 组合会生成 2x2=4 个测试"""
    assert calculator.add(a, b) == a + b


# ==================== 4. Mock ====================
print_mock = None  # 仅标记


class ExternalAPI:
    """外部 API 客户端 — 测试时要 mock 掉"""

    def __init__(self, api_key: str):
        self.api_key = api_key

    def call(self, endpoint: str, payload: dict) -> dict:
        """真正的网络调用（测试时不应执行）"""
        # 假设这里真的调用了外部 API
        raise NotImplementedError("真实调用不应在测试中执行")


class BusinessLogic:
    """业务逻辑 — 依赖外部 API"""

    def __init__(self, api: ExternalAPI):
        self.api = api

    def process_order(self, order_id: int, amount: float) -> dict:
        """处理订单：先调用外部支付 API，再更新状态"""
        result = self.api.call(
            "/payments",
            {"order_id": order_id, "amount": amount},
        )
        return {"order_id": order_id, "status": "paid", "payment": result}


class TestWithMock:
    def test_process_order_with_mock(self):
        """使用 mock 替换外部 API，避免真实网络调用"""
        # 创建 mock API 对象
        mock_api = mock.Mock(spec=ExternalAPI)
        # 配置 mock 的返回值
        mock_api.call.return_value = {"transaction_id": "TXN123", "status": "success"}

        logic = BusinessLogic(mock_api)
        result = logic.process_order(1001, 299.9)

        # 验证结果
        assert result["status"] == "paid"
        assert result["payment"]["transaction_id"] == "TXN123"

        # 验证 mock 被正确调用（参数验证）
        mock_api.call.assert_called_once_with(
            "/payments",
            {"order_id": 1001, "amount": 299.9},
        )

    def test_mock_side_effect(self):
        """side_effect: 模拟异常或动态返回值"""
        mock_api = mock.Mock(spec=ExternalAPI)
        mock_api.call.side_effect = RuntimeError("网络超时")

        logic = BusinessLogic(mock_api)
        with pytest.raises(RuntimeError, match="网络超时"):
            logic.process_order(1002, 99.9)

    def test_patch_decorator(self, monkeypatch):
        """monkeypatch: 替换模块中的函数"""
        # 假设我们有一个外部库函数
        import time

        calls = []

        def fake_sleep(seconds):
            calls.append(seconds)  # 记录调用，不真正睡眠

        monkeypatch.setattr(time, "sleep", fake_sleep)

        time.sleep(5)  # 不会真正睡 5 秒
        assert calls == [5]


# ==================== 5. 异步测试 ====================
print_async = None  # 仅标记


async def fetch_user(user_id: int) -> dict:
    """异步函数：模拟从数据库获取用户"""
    await asyncio.sleep(0.01)
    users = {
        1: {"id": 1, "name": "Alice"},
        2: {"id": 2, "name": "Bob"},
    }
    return users.get(user_id, {})


class TestAsync:
    """异步测试 — 需要 pytest-asyncio"""

    @pytest.mark.asyncio
    async def test_fetch_user_exists(self):
        user = await fetch_user(1)
        assert user["name"] == "Alice"

    @pytest.mark.asyncio
    async def test_fetch_user_not_exists(self):
        user = await fetch_user(999)
        assert user == {}

    @pytest.mark.asyncio
    async def test_concurrent_fetch(self):
        """并发获取多个用户"""
        results = await asyncio.gather(
            fetch_user(1),
            fetch_user(2),
        )
        assert [r["id"] for r in results] == [1, 2]

    @pytest.mark.asyncio
    async def test_async_side_effect(self):
        """异步函数也可以被 mock"""
        with mock.patch(
            "learn_04_python_examples.fetch_user",
            new=mock.AsyncMock(return_value={"id": 1, "name": "Mocked"}),
        ):
            user = await fetch_user(1)
            assert user["name"] == "Mocked"


# ==================== 运行说明 ====================
# 方式1: python -m pytest 03_testing_demo.py -v
# 方式2: pytest 03_testing_demo.py -v --tb=short
#
# 需要安装:
#   pip install pytest pytest-asyncio