# 第十四章：代码质量工具链（P1 进阶）

> 📖 **参考资料**：[Ruff](https://docs.astral.sh/ruff/) | [Mypy](https://mypy-lang.org/) | [Pre-commit](https://pre-commit.com/)

## 14.1 Ruff：超快 Linter + Formatter

[Ruff](https://github.com/astral-sh/ruff) 用 Rust 编写，速度比 Flake8 + Black 快 **10~100 倍**，同时覆盖 Lint 和 Format 两大功能。它兼容 Flake8、isort、pyupgrade 等 **80+ 规则集**，已成为 Python 社区的主流选择。

```bash
# 安装
pip install ruff

# 检查代码（Linter）
ruff check .

# 自动修复可修复的问题
ruff check --fix .

# 格式化代码（Formatter）
ruff format .

# 检查格式化差异（不修改文件）
ruff format --check .
```

### Before vs After

```python
# ❌ Before — ruff check 报告 5 个问题
import json, os          # E401: 多个 import 合并
from typing import *      # F403: 通配符导入

def process_user(name,age,email):   # E231: 逗号后缺空格
    x = {"name": name, "age": age, "email": email}  # UP031: f-string 可替代 %
    print(  "Processing: " + name   )  # E211/E251: 括号和缩进问题
    return json.dumps(x)
```

```python
# ✅ After — ruff format & check 全部通过
import json
import os

def process_user(name: str, age: int, email: str) -> str:
    """处理用户数据并返回 JSON 字符串。"""
    user = {"name": name, "age": age, "email": email}
    print(f"Processing: {name}")
    return json.dumps(user)
```

## 14.2 Mypy 类型检查

[Mypy](https://mypy-lang.org/) 是 Python 的静态类型检查器，能在运行前捕获类型错误。

```bash
# 安装
pip install mypy

# 检查项目
mypy src/

# 严格模式（推荐新项目启用）
mypy src/ --strict
```

### 类型注解示例

```python
# src/models/user.py
from dataclasses import dataclass


@dataclass
class User:
    id: int
    name: str
    email: str
    age: int

    def is_adult(self) -> bool:
        return self.age >= 18


def find_user(users: list[User], user_id: int) -> User | None:
    """根据 ID 查找用户"""
    for user in users:
        if user.id == user_id:
            return user
    return None


def get_user_email(user: User | None) -> str:
    """获取用户邮箱，None 时返回空字符串"""
    # Mypy 能检测到此处的 None 检查
    if user is None:
        return ""
    return user.email  # ✅ Mypy 知道此处 user 不为 None
```

**常见 Mypy 错误修复：**

| 错误码 | 含义 | 修复方式 |
|--------|------|----------|
| `arg-type` | 参数类型不匹配 | 添加类型注解 / 类型守卫 |
| `return-value` | 返回值类型不匹配 | 检查返回语句 |
| `union-attr` | 联合类型访问属性 | 先做 `None` 检查或用 `assert` |
| `no-untyped-def` | 缺少类型注解 | 给参数和返回值加注解 |
| `import-untyped` | 第三方库无类型 | 安装 `types-xxx` 或写 `py.typed` |

## 14.3 Pre-commit Hooks

[Pre-commit](https://pre-commit.com/) 在每次 `git commit` 前自动运行检查，防止不合格代码入库。

```bash
# 安装 pre-commit
pip install pre-commit

# 初始化并安装 hooks
pre-commit install

# 手动运行所有 hooks（检查全部文件）
pre-commit run --all-files
```

### 配置文件

```yaml
# .pre-commit-config.yaml
repos:
  # ========== Ruff Linter + Formatter ==========
  - repo: https://github.com/astral-sh/ruff-pre-commit
    rev: v0.11.0
    hooks:
      - id: ruff
        args: [--fix, --exit-non-zero-on-fix]
        stages: [pre-commit]
      - id: ruff-format
        stages: [pre-commit]

  # ========== Mypy 类型检查 ==========
  - repo: https://github.com/pre-commit/mirrors-mypy
    rev: v1.15.0
    hooks:
      - id: mypy
        additional_dependencies:
          - types-requests
          - types-PyYAML
        args: [--config-file=pyproject.toml]
        stages: [pre-commit]

  # ========== 通用检查 ==========
  - repo: https://github.com/pre-commit/pre-commit-hooks
    rev: v5.0.0
    hooks:
      - id: trailing-whitespace      # 去除行尾空格
      - id: end-of-file-fixer        # 确保文件以换行结尾
      - id: check-yaml               # 验证 YAML 语法
      - id: check-toml               # 验证 TOML 语法
      - id: check-added-large-files  # 阻止添加 > 1MB 文件
        args: [--maxkb=1024]
      - id: detect-private-key       # 检测私钥泄露
```

**工作流示意：**

```
$ git commit -m "feat: add user API"
# ruff trailing-whitespace detected .............. Passed
# ruff format check .............................. Failed
#   - files were modified by this hook
# mypy type check ................................. Passed

# ⚠️ ruff format 自动修复了格式问题，需重新 stage：
$ git add -u
$ git commit -m "feat: add user API"
# ✅ All hooks passed.
```

## 14.4 pyproject.toml 统一配置

将 Ruff、Mypy、项目元数据统一写在 `pyproject.toml` 中，一个文件管理所有工具配置：

```toml
# pyproject.toml（节选）

[project]
name = "my-fastapi-app"
version = "0.1.0"
requires-python = ">=3.11"

# ========== Ruff ==========
[tool.ruff]
target-version = "py311"
line-length = 88
src = ["src", "tests"]

[tool.ruff.lint]
select = [
    "E",     # pycodestyle errors
    "W",     # pycodestyle warnings
    "F",     # pyflakes
    "I",     # isort
    "UP",    # pyupgrade
    "B",     # flake8-bugbear
    "SIM",   # flake8-simplify
    "S",     # flake8-bandit (安全检查)
    "C4",    # flake8-comprehensions
    "PT",    # flake8-pytest-style
    "RUF",   # Ruff 特有规则
]
ignore = [
    "S101",  # 允许 assert（测试用）
    "E501",  # 行长度由 formatter 控制
]

[tool.ruff.lint.per-file-ignores]
"tests/**/*.py" = ["S101", "S106"]  # 测试中允许 assert 和硬编码密码
"__init__.py" = ["F401"]           # __init__.py 允许未使用导入

[tool.ruff.format]
quote-style = "double"
indent-style = "space"
line-ending = "lf"

# ========== Mypy ==========
[tool.mypy]
python_version = "3.11"
strict = true
warn_return_any = true
warn_unused_configs = true
disallow_untyped_defs = true

[[tool.mypy.overrides]]
module = "tests.*"
disallow_untyped_defs = false

[[tool.mypy.overrides]]
module = ["celery.*", "redis.*"]
ignore_missing_imports = true
```

## 14.5 Ruff 规则选择

Ruff 收录了 **80+ 规则集**，无需全部启用。以下是 Python 后端项目的推荐组合：

| 规则集 | 前缀 | 说明 | 推荐度 |
|--------|------|------|--------|
| pycodestyle | `E`, `W` | PEP 8 风格规范 | ⭐⭐⭐ 必选 |
| pyflakes | `F` | 未使用变量、导入等 | ⭐⭐⭐ 必选 |
| isort | `I` | import 排序 | ⭐⭐⭐ 必选 |
| pyupgrade | `UP` | 自动使用新语法 | ⭐⭐⭐ 强烈推荐 |
| flake8-bugbear | `B` | 常见 Bug 检测 | ⭐⭐⭐ 强烈推荐 |
| flake8-bandit | `S` | 安全漏洞检查 | ⭐⭐ 推荐 |
| flake8-simplify | `SIM` | 代码简化建议 | ⭐⭐ 推荐 |
| flake8-pytest-style | `PT` | pytest 规范 | ⭐⭐ 推荐（有测试） |
| flake8-comprehensions | `C4` | 推导式优化 | ⭐ 可选 |
| Ruff 特有 | `RUF` | Ruff 专属增强规则 | ⭐ 可选 |

### CI 集成示例

```yaml
# .github/workflows/quality.yml
name: Code Quality

on: [push, pull_request]

jobs:
  lint:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-python@v5
        with:
          python-version: "3.11"

      - name: Install tools
        run: pip install ruff mypy

      - name: Ruff Lint
        run: ruff check .

      - name: Ruff Format
        run: ruff format --check .

      - name: Mypy
        run: mypy src/
```

## 必读资源

| 资源 | 链接 | 说明 |
|------|------|------|
| Ruff 官方文档 | https://docs.astral.sh/ruff/ | 规则列表、配置参考、集成指南 |
| Ruff 规则搜索 | https://docs.astral.sh/ruff/rules/ | 100+ 规则的完整说明与示例 |
| Mypy 官方文档 | https://mypy-lang.org/ | 类型系统、配置、常见错误 |
| Pre-commit 文档 | https://pre-commit.com/ | Hook 配置、自定义 Hook 开发 |
| Real Python: Type Hints | https://realpython.com/python-type-checking/ | Python 类型注解入门到进阶 |
| Ruff vs Flake8+Black | https://docs.astral.sh/ruff/faq/#how-does-ruff-compare-to-flake8-isort-black-isort | 官方对比分析 |
