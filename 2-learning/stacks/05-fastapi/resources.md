# 推荐资源

> FastAPI 学习资源精选，从官方文档到实战项目

---

## 官方文档（首选）

| 资源 | 链接 | 说明 |
|------|------|------|
| FastAPI 官方文档 | https://fastapi.tiangolo.com/ | 教程 + API 参考，中文版：https://fastapi.tiangolo.com/zh/ |
| Pydantic 文档 | https://docs.pydantic.dev/ | Pydantic v2 完整文档 |
| Starlette 文档 | https://www.starlette.io/ | FastAPI 底层框架 |
| Uvicorn 文档 | https://www.uvicorn.org/ | ASGI 服务器 |
| SQLAlchemy 文档 | https://docs.sqlalchemy.org/ | ORM（含异步模式） |
| httpx 文档 | https://www.python-httpx.org/ | 异步 HTTP 客户端 |

---

## 教程与书籍

| 资源 | 类型 | 说明 |
|------|------|------|
| TestDriven.io | 文章 | 高质量 FastAPI 实战教程（GraphQL、Docker、CI/CD） |
| Real Python FastAPI | 教程 | https://realpython.com/fastapi-python-web-apis/ |
| 《FastAPI Web 开发实战》 | 书籍 | 中文实战教程 |
| Awesome FastAPI | GitHub 集合 | https://github.com/mjhea0/awesome-fastapi |

---

## 视频课程

| 平台 | 课程 | 说明 |
|------|------|------|
| YouTube | FastAPI Crash Course（Traversy） | 快速入门 |
| YouTube | FastAPI Full Stack（ArjanCodes） | 架构设计视角 |
| Bilibili | FastAPI 入门到实战 | 中文视频教程 |
| Udemy | FastAPI: The Complete Course | 系统化课程 |

---

## GitHub 项目（最佳实践）

| 项目 | 说明 |
|------|------|
| https://github.com/zhanymkanov/fastapi-best-practices | FastAPI 最佳实践仓库（强烈推荐） |
| https://github.com/tiangolo/full-stack-fastapi-template | FastAPI 官方全栈模板 |
| https://github.com/tiangolo/sqlmodel | SQLAlchemy + Pydantic 结合 |
| https://github.com/uriyyo/fastapi-pagination | FastAPI 分页库 |
| https://github.com/fastapi-admin/fastapi-admin | FastAPI 管理后台 |

---

## 面试准备

| 资源 | 说明 |
|------|------|
| 本教程 05-interview/ 目录 | 速记版 + 深挖题 + 场景题 + 代码题 |
| LeetCode / HackerRank | Python 算法题 |
| 公司面试题库（牛客网） | 搜索 "FastAPI 面试" |
| GitHub - awesome-fastapi | 收集了大量相关资源 |

---

## 推荐学习路径

```
阶段一：基础（1 周）
  ├── 官方教程 Tutorial
  └── 本教程 01-basics/ + 02-core/

阶段二：认证与实践（1-2 周）
  ├── 官方 Advanced 教程
  ├── 本教程 03-advanced/
  └── 仿写一个小项目（如 mini-blog）

阶段三：深挖与面试（2 周）
  ├── zhanymkanov/fastapi-best-practices
  ├── 本教程 05-interview/
  └── 源码阅读（Starlette + FastAPI）
```

---

## 工具推荐

| 工具 | 用途 |
|------|------|
| Ruff | Python 代码检查 + 格式化 |
| mypy | 静态类型检查 |
| pytest + pytest-asyncio | 测试 |
| Locust | 性能压测 |
| Docker | 容器化部署 |
| sqlfluff | SQL 代码检查 |