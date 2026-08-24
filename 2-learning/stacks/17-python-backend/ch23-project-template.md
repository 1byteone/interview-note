# 第二十三章：项目模板（P0 精通）

> 📖 **参考资料**：[pyproject.toml](https://packaging.python.org/en/latest/specifications/pyproject-toml/) | [GNU Make](https://www.gnu.org/software/make/) | [Pre-commit](https://pre-commit.com/)

---

## 23.1 项目目录结构

```
myproject/
├── .github/
│   └── workflows/
│       ├── ci.yml                    # CI 流水线
│       └── deploy.yml                # CD 部署
├── alembic/                          # 数据库迁移
│   ├── versions/
│   └── env.py
├── src/
│   └── app/
│       ├── __init__.py
│       ├── main.py                   # FastAPI 应用入口
│       ├── config.py                 # pydantic-settings 配置
│       ├── database.py               # SQLAlchemy 引擎 & 会话
│       ├── dependencies.py           # 通用依赖注入
│       ├── exceptions.py             # 自定义异常 & 全局处理器
│       ├── middleware/                # 中间件
│       │   ├── __init__.py
│       │   ├── logging.py
│       │   └── request_id.py
│       ├── models/                   # SQLAlchemy ORM 模型
│       │   ├── __init__.py
│       │   ├── user.py
│       │   └── order.py
│       ├── schemas/                  # Pydantic 请求/响应模型
│       │   ├── __init__.py
│       │   ├── user.py
│       │   └── common.py
│       ├── api/                      # 路由
│       │   ├── __init__.py
│       │   ├── v1/
│       │   │   ├── __init__.py
│       │   │   ├── router.py         # v1 总路由
│       │   │   ├── users.py
│       │   │   ├── orders.py
│       │   │   └── health.py
│       │   └── deps.py
│       ├── services/                 # 业务逻辑层
│       │   ├── __init__.py
│       │   ├── user_service.py
│       │   └── order_service.py
│       ├── repositories/             # 数据访问层
│       │   ├── __init__.py
│       │   ├── user_repository.py
│       │   └── order_repository.py
│       └── core/                     # 横切关注点
│           ├── __init__.py
│           ├── security.py
│           ├── logging_config.py
│           └── events.py
├── tests/
│   ├── __init__.py
│   ├── conftest.py                   # pytest fixtures
│   ├── factories/                    # 测试数据工厂
│   │   ├── __init__.py
│   │   └── user_factory.py
│   ├── unit/
│   │   └── test_user_service.py
│   ├── integration/
│   │   └── test_user_api.py
│   └── e2e/
│       └── test_full_flow.py
├── scripts/
│   ├── seed_data.py                  # 种子数据
│   └── generate_openapi.py           # 导出 OpenAPI 文档
├── docs/
│   ├── architecture.md
│   └── api.md
├── .env.example                      # 环境变量模板
├── .pre-commit-config.yaml           # Pre-commit 钩子
├── alembic.ini                       # Alembic 配置
├── docker-compose.yml                # 本地开发环境
├── Dockerfile                        # 生产镜像
├── Makefile                          # 任务管理
├── pyproject.toml                    # 项目元数据 & 依赖
├── README.md
└── .gitignore
```

---

## 23.2 完整 Makefile

```makefile
# ============================================================================
#  MyProject Makefile — Python Backend 项目管理
# ============================================================================

.PHONY: help install dev test lint format check migrate seed run clean docker-build docker-up

# 默认目标
help: ## 显示帮助信息
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | \
		awk 'BEGIN {FS = ":.*?## "}; {printf "\033[36m%-20s\033[0m %s\n", $$1, $$2}'

# ── 安装与环境 ──

install: ## 安装生产依赖
	pip install -e .

dev: ## 安装开发依赖（含测试、lint 工具）
	pip install -e ".[dev]"
	pre-commit install

# ── 代码质量 ──

lint: ## 运行 Ruff linter
	ruff check src/ tests/
	ruff check --select I src/ tests/  # 仅检查 import 排序

format: ## 格式化代码（Ruff + Black）
	ruff format src/ tests/
	ruff check --fix src/ tests/

typecheck: ## 运行类型检查（mypy）
	mypy src/app/ --ignore-missing-imports

check: lint typecheck test ## 执行全部检查（lint + typecheck + test）

# ── 测试 ──

test: ## 运行单元测试 + 集成测试
	python -m pytest tests/ -v --tb=short -x

test-unit: ## 仅运行单元测试
	python -m pytest tests/unit/ -v --tb=short

test-integration: ## 仅运行集成测试
	python -m pytest tests/integration/ -v --tb=short

test-e2e: ## 运行端到端测试
	python -m pytest tests/e2e/ -v --tb=long

test-cov: ## 运行测试并生成覆盖率报告
	python -m pytest tests/ \
		--cov=src/app \
		--cov-report=term-missing \
		--cov-report=html:htmlcov/ \
		--cov-fail-under=80

test-watch: ## 监听文件变化，自动重新测试
	ptw tests/ -- --tb=short -q

# ── 数据库 ──

migrate: ## 执行数据库迁移（Alembic）
	alembic upgrade head

migrate-new: ## 创建新迁移文件（需要 MSG 参数）
	alembic revision --autogenerate -m "$(MSG)"

migrate-history: ## 查看迁移历史
	alembic history

migrate-down: ## 回滚一个版本
	alembic downgrade -1

seed: ## 填充种子数据
	python -m scripts.seed_data

# ── 运行 ──

run: ## 本地开发模式运行（热重载）
	uvicorn app.main:app --reload --host 0.0.0.0 --port 8000 --log-level debug

run-prod: ## 生产模式运行（Gunicorn）
	gunicorn app.main:app -c gunicorn.conf.py

# ── Docker ──

docker-build: ## 构建 Docker 镜像
	docker build -t myproject:latest .

docker-up: ## 启动 docker-compose 开发环境
	docker-compose up -d

docker-down: ## 停止 docker-compose
	docker-compose down -v

docker-logs: ## 查看容器日志
	docker-compose logs -f app

# ── 文档 ──

docs-serve: ## 本地启动文档服务
	mkdocs serve

docs-build: ## 构建文档站点
	mkdocs build

openapi: ## 导出 OpenAPI JSON
	python -m scripts.generate_openapi

# ── 清理 ──

clean: ## 清理构建产物与缓存
	find . -type d -name __pycache__ -exec rm -rf {} + 2>/dev/null || true
	find . -type f -name "*.pyc" -delete 2>/dev/null || true
	rm -rf .mypy_cache .pytest_cache .ruff_cache htmlcov/ dist/ *.egg-info
```

```bash
# 使用示例
make help          # 查看所有命令
make dev           # 安装开发依赖
make test-cov      # 测试 + 覆盖率
make check         # lint + typecheck + test 一键执行
make migrate MSG="add user table"  # 创建迁移
```

---

## 23.3 pyproject.toml 完整配置

```toml
[build-system]
requires = ["hatchling"]
build-backend = "hatchling.build"

# ── 项目元数据 ──
[project]
name = "myproject"
version = "1.0.0"
description = "Production-ready Python backend with FastAPI"
readme = "README.md"
license = { text = "MIT" }
requires-python = ">=3.12"
authors = [
    { name = "Your Name", email = "you@example.com" },
]
keywords = ["fastapi", "backend", "api"]
classifiers = [
    "Development Status :: 4 - Beta",
    "Programming Language :: Python :: 3.12",
    "Framework :: FastAPI",
    "Typing :: Typed",
]

dependencies = [
    "fastapi>=0.115,<1.0",
    "uvicorn[standard]>=0.30,<1.0",
    "pydantic>=2.9,<3.0",
    "pydantic-settings>=2.5,<3.0",
    "sqlalchemy[asyncio]>=2.0,<3.0",
    "asyncpg>=0.30,<1.0",
    "alembic>=1.14,<2.0",
    "redis>=5.0,<6.0",
    "httpx>=0.27,<1.0",
    "python-dotenv>=1.0,<2.0",
    "gunicorn>=22.0,<24.0",
]

[project.optional-dependencies]
dev = [
    "pytest>=8.0,<9.0",
    "pytest-asyncio>=0.24,<1.0",
    "pytest-cov>=5.0,<6.0",
    "pytest-watch>=4.2,<5.0",
    "httpx>=0.27,<1.0",          # TestClient 依赖
    "ruff>=0.8,<1.0",
    "mypy>=1.13,<2.0",
    "pre-commit>=4.0,<5.0",
    "factory-boy>=3.3,<4.0",     # 测试工厂
]

docs = [
    "mkdocs>=1.6,<2.0",
    "mkdocs-material>=9.5,<10.0",
    "mkdocstrings[python]>=0.27,<1.0",
]

# ── 项目入口 ──
[project.scripts]
myproject = "app.main:cli"

# ── 工具配置 ──

# Ruff（替代 Black + isort + Flake8）
[tool.ruff]
target-version = "py312"
line-length = 88
src = ["src", "tests"]

[tool.ruff.lint]
select = [
    "E",    # pycodestyle errors
    "W",    # pycodestyle warnings
    "F",    # pyflakes
    "I",    # isort
    "N",    # pep8-naming
    "UP",   # pyupgrade
    "B",    # flake8-bugbear
    "A",    # flake8-builtins
    "SIM",  # flake8-simplify
    "TCH",  # flake8-type-checking
    "RUF",  # ruff-specific
]
ignore = ["E501"]  # 行长度由 formatter 控制

[tool.ruff.lint.isort]
known-first-party = ["app"]

# Mypy
[tool.mypy]
python_version = "3.12"
strict = true
plugins = ["pydantic.mypy", "sqlalchemy.ext.mypy.plugin"]
warn_return_any = true
warn_unused_configs = true
disallow_untyped_defs = true

# Pytest
[tool.pytest.ini_options]
testpaths = ["tests"]
asyncio_mode = "auto"
python_files = ["test_*.py"]
python_classes = ["Test*"]
python_functions = ["test_*"]
markers = [
    "unit: Unit tests (fast, no I/O)",
    "integration: Integration tests (may need DB)",
    "e2e: End-to-end tests (full stack)",
]
addopts = "-ra -q --strict-markers"

# Coverage
[tool.coverage.run]
source = ["src/app"]
omit = ["tests/*", "*/migrations/*"]

[tool.coverage.report]
show_missing = true
skip_covered = true
fail_under = 80
exclude_lines = [
    "pragma: no cover",
    "if TYPE_CHECKING:",
    "if __name__ == .__main__.",
]
```

```yaml
# .pre-commit-config.yaml
repos:
  - repo: https://github.com/astral-sh/ruff-pre-commit
    rev: v0.8.0
    hooks:
      - id: ruff
        args: [--fix]
      - id: ruff-format

  - repo: https://github.com/pre-commit/mirrors-mypy
    rev: v1.13.0
    hooks:
      - id: mypy
        additional_dependencies: [pydantic, sqlalchemy, pydantic-settings]
```

---

## 必读资源

| 资源 | 链接 | 说明 |
|------|------|------|
| pyproject.toml 规范 | https://packaging.python.org/en/latest/specifications/pyproject-toml/ | Python 打包配置标准 |
| GNU Make 手册 | https://www.gnu.org/software/make/manual/ | Makefile 编写参考 |
| Ruff 文档 | https://docs.astral.sh/ruff/ | 高性能 Python linter + formatter |
| Pre-commit | https://pre-commit.com/ | Git 钩子管理框架 |
| Mypy 文档 | https://mypy.readthedocs.io/ | Python 静态类型检查 |
| FastAPI 项目模板 | https://fastapi.tiangolo.com/tutorial/bigger-applications/ | 官方大型项目组织方式 |
