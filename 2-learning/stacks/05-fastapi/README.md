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

| 领域 | 要求 | 说明 |
|------|------|------|
| Python 基础 | 必备 | 类型注解、装饰器、async/await、上下文管理器 |
| HTTP 协议 | 必备 | RESTful API 设计、状态码、请求/响应模型 |
| JSON | 必备 | 序列化/反序列化、嵌套结构 |
| Java 后端基础 | 加分 | Spring Boot 对比理解 DI、AOP、拦截器 |
| Docker | 推荐 | 容器化部署 |

---

## 面试高频考点一览表

| 考点 | 难度 | 频率 | 说明 |
|------|------|------|------|
| FastAPI vs Django/Flask | ⭐⭐ | ⭐⭐⭐⭐⭐ | 异步、性能、类型安全、生态 |
| 依赖注入 Depends | ⭐⭐⭐ | ⭐⭐⭐⭐⭐ | 作用域、嵌套、可调用类 |
| Pydantic v2 模型 | ⭐⭐⭐ | ⭐⭐⭐⭐⭐ | Field、validator、配置管理 |
| async/await 异步处理 | ⭐⭐⭐⭐ | ⭐⭐⭐⭐ | 异步路由、数据库驱动、并发 |
| WebSocket + SSE | ⭐⭐⭐⭐ | ⭐⭐⭐⭐ | 连接管理、流式响应、AI 对话 |
| 中间件与 CORS | ⭐⭐ | ⭐⭐⭐ | CORSMiddleware、自定义中间件 |
| 异常处理 | ⭐⭐ | ⭐⭐⭐ | HTTPException、自定义处理器 |
| 路由与路径匹配 | ⭐ | ⭐⭐⭐ | 装饰器、路由顺序、参数 |
| 后台任务 | ⭐⭐⭐ | ⭐⭐⭐ | BackgroundTasks、Celery 集成 |
| 测试与调试 | ⭐⭐⭐ | ⭐⭐⭐ | TestClient、pytest-asyncio、locust |
| Docker 部署 | ⭐⭐⭐ | ⭐⭐⭐⭐ | Dockerfile、docker-compose、Nginx |
| 项目结构 | ⭐⭐ | ⭐⭐⭐ | 分层架构、模块化、配置管理 |
| ASGI 协议 | ⭐⭐⭐⭐ | ⭐⭐ | Starlette 底层、ASGI 应用 |
| 请求限流与熔断 | ⭐⭐⭐⭐ | ⭐⭐⭐ | 限流策略、熔断器模式 |
| 性能优化 | ⭐⭐⭐⭐⭐ | ⭐⭐⭐ | 连接池、缓存、异步非阻塞 |

---

## FastAPI 在 AI 商城的角色

在 AI 商城架构中，FastAPI 承担以下核心职责：

### 1. AI 搜索服务
- 接收用户查询请求，异步调用 LLM（OpenAI / 本地模型）
- 实时流式响应（SSE），边生成边推送
- 多模型路由，根据请求类型自动选择最优模型

### 2. 客服 API
- WebSocket 长连接，支持实时对话
- 上下文管理，会话状态保持
- 后台任务处理（工单创建、日志记录）

### 3. 推荐 API
- 高性能查询接口，响应时间 < 50ms
- 依赖注入管理推荐算法策略
- 异步缓存更新（Redis）

---

## 目录结构

```
learn/05-fastapi/
├── README.md                     # 本文件 — 技术栈总览
├── resources.md                  # 推荐资源
├── 01-basics/                    # 入门基础
│   ├── 01-quick-start.md         # 快速开始
│   └── 02-routing-and-middleware.md  # 路由与中间件
├── 02-core/                      # 核心进阶
│   ├── 01-dependency-injection.md    # 依赖注入
│   ├── 02-pydantic-and-validation.md # Pydantic 与验证
│   └── 03-async-and-concurrency.md   # 异步与并发
├── 03-advanced/                  # 高级专题
│   ├── 01-websocket-and-streaming.md # WebSocket 与流式
│   ├── 02-testing-and-debugging.md   # 测试与调试
│   └── 03-production-deployment.md   # 生产部署
├── 04-projects/                  # 实战项目
│   ├── mall-integration.md       # AI 商城集成
│   └── mini-blog/                # 迷你博客项目
│       └── README.md             # 博客项目说明
└── 05-interview/                 # 面试备战
    ├── quick-revision.md         # 速记版
    ├── deep-dive.md              # 深挖题
    ├── scenario.md               # 场景题
    └── coding.md                 # 代码题

---

## 📖 导航

| ← 上一篇 | 📚 目录 | 下一篇 → |
|----------|---------|----------|
| [← Python 基础](../04-python/README.md) | [📚 总目录](../../README-learning.md) | [MySQL →](../06-mysql/README.md) |

**相关技术栈：**
- [14-LangChain](../14-langchain/README.md) — FastAPI 作为 AI 网关，路由 LangChain 编排的 AI 服务
- [16-OpenAI](../16-openai/README.md) — FastAPI 的 AI 服务依赖 OpenAI API 提供底层模型能力

---

## 项目剖析深度参考

本 learn 文档提供理论基础，以下 `docs/tech-stack-analysis/` 文档提供**真实项目中的落地代码**：

| 本 learn 核心内容 | 对应项目剖析 | 重点看什么 |
|------------------|------------|-----------|
| FastAPI 路由/异常处理 | [02-API-GATEWAY.md](../../docs/tech-stack-analysis/mall-ai-search/02-API-GATEWAY.md) | FastAPI 路由 + Pydantic v2 + 全局异常 |
| Pydantic 多 Provider 配置 | [03-CONFIG-MULTI-PROVIDER.md](../../docs/tech-stack-analysis/mall-ai-search/03-CONFIG-MULTI-PROVIDER.md) | pydantic-settings 嵌套配置 + 策略模式 |
| Java→Python 桥接 | [12-AI-SEARCH-BRIDGE.md](../../docs/tech-stack-analysis/mall-micro-cloud/12-AI-SEARCH-BRIDGE.md) | FeignClient 调用 Python 服务 |
```