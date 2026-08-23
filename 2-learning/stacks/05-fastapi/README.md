# FastAPI — 异步 · 依赖注入 · 类型安全 · 高性能

> 学习目标：从 Java Spring Boot 开发者的视角，快速掌握 FastAPI 的核心概念与 AI 项目实战。

---

## 学习路径图

```
👶 入门                                🎯 进阶
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
01-basics/01-quick-start.md            02-core/01-dependency-injection.md
    └── 第一个 API、路径参数、              └── Depends 机制、作用域、实战
        请求体、Pydantic                  │
        商品搜索 API                    02-core/02-pydantic-and-validation.md
        │                                └── Pydantic v2、Field、验证器、
01-basics/02-routing-and-middleware.md      响应模型、配置管理
    └── 路由装饰器、中间件、               │
        CORSMiddleware、HTTPException    02-core/03-async-and-concurrency.md
        │                                └── async def、异步驱动、后台任务
        │                                │    AI 异步调用 LLM
        │                                │
        ▼                                03-advanced/01-websocket-and-streaming.md
        │                                └── WebSocket、SSE、流式 AI 对话
        │                                │
        │                                03-advanced/02-testing-and-debugging.md
        │                                └── TestClient、pytest、locust
        │                                │
        │                                03-advanced/03-production-deployment.md
        │                                └── Docker、Nginx、Gunicorn+Uvicorn
        │                                │
        │                                04-projects/mall-integration.md
        │                                └── AI 商城搜索服务架构
        │                                │
        │                                04-projects/mini-blog/
        │                                └── 独立博客 API 项目
        │                                │
        ▼                                05-interview/
        └── quick-revision.md ── deep-dive.md ── scenario.md ── coding.md
```

---

## 前置知识