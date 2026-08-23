# FastAPI — 面试抽认卡

> 来源：`learn/05-fastapi/05-interview/`

---

### Card 1: FastAPI vs Django vs Flask
**维度**: 📝速记 | **难度**: ⭐

> **Q: FastAPI 相比 Django 和 Flask 的核心优势是什么？**

**A:** FastAPI 异步原生（基于 Starlette），性能高（接近 Node.js/Go），内置 Pydantic 类型校验和自动 OpenAPI 文档生成。Django 生态成熟但同步模型为主（3.0+ 部分异步），Flask 轻量但无类型安全和异步原生支持。FastAPI 适合高性能 API 和 AI 项目（LLM 流式输出），Django 适合大型全栈项目，Flask 适合微服务。

---

### Card 2: 依赖注入原理
**维度**: 🔬深挖 | **难度**: ⭐⭐

> **Q: FastAPI 的 Depends 是如何工作的？如何实现依赖覆盖用于测试？**

**A:** `Depends(callable)` 将依赖函数的返回值注入路由参数。依赖链：A 依赖 B，B 依赖 C，FastAPI 自动解析并缓存结果（同一请求中多次依赖同一个函数，只执行一次）。`app.dependency_overrides[orig_dep] = mock_dep` 可在测试时替换依赖，无需修改路由代码。生成器依赖（`yield`）用于资源管理，在请求结束时自动清理（`finally` 块）。

---

### Card 3: Pydantic v2 新特性
**维度**: 📝速记 | **难度**: ⭐⭐

> **Q: Pydantic v2 相比 v1 有哪些核心改进？API 变更有哪些？**

**A:** 核心改进：Rust 重写（pydantic-core），性能提升 5-50 倍。API 变更：`.dict()` → `.model_dump()`，`.json()` → `.model_dump_json()`，`@validator` → `@field_validator`，`@root_validator` → `@model_validator`。新功能：`ConfigDict` 替代 `Config` 类，`Field` 的 `validate_default` 参数，`model_config` 的 `frozen`（不可变模型）。`BaseSettings` 从 `.env` 加载配置，支持 `model_config` 配置。

---

### Card 4: async def 与 def 路由的区别
**维度**: 🔬深挖 | **难度**: ⭐⭐

> **Q: FastAPI 中 async def 和 def 路由的执行方式有什么区别？**

**A:** `async def` 路由直接在事件循环中执行，适合 IO 密集型（异步数据库查询、HTTP 请求）。`def` 路由在线程池中执行，适合同步阻塞操作（CPU 计算、同步 ORM 操作的 SQLAlchemy）。如果 `def` 路由中调用 `await` 会报错。最佳实践：使用异步驱动（`asyncpg`、`httpx.AsyncClient`、`motor`）时用 `async def`；使用同步库（`psycopg2`、`requests`）时用 `def`。

---

### Card 5: ASGI vs WSGI
**维度**: 🔬深挖 | **难度**: ⭐⭐

> **Q: ASGI 和 WSGI 的区别是什么？FastAPI 为什么选择 ASGI？**

**A:** WSGI（Python Web Server Gateway Interface）是同步协议，请求-响应模型，不支持 WebSocket 和 HTTP/2。ASGI（Asynchronous Server Gateway Interface）是异步协议，支持 WebSocket、SSE、HTTP/2 和长连接。FastAPI 基于 Starlette（ASGI 框架），原生支持异步，可以处理 WebSocket 连接、SSE 流式输出和 AI 对话流场景。生产部署用 Uvicorn（ASGI 服务器）。

---

### Card 6: 中间件执行顺序
**维度**: 🔬深挖 | **难度**: ⭐⭐

> **Q: FastAPI 中间件的执行顺序是怎样的？洋葱模型如何理解？**

**A:** FastAPI 中间件遵循洋葱模型：先注册的中间件先处理请求，后处理响应。每个中间件调用 `call_next(request)` 将请求传给下一个中间件或路由处理函数。响应返回时逆向执行。例如：中间件 A → 中间件 B → 路由处理 → 中间件 B（响应）→ 中间件 A（响应）。注册顺序决定执行顺序，`CORSMiddleware` 通常放在最外层。

---

### Card 7: WebSocket 连接管理
**维度**: 🎯场景 | **难度**: ⭐⭐⭐

> **Q: FastAPI 中如何管理 WebSocket 连接？如何实现广播和心跳检测？**

**A:** 使用 `ConnectionManager` 类管理连接池：`connect` 方法将 `websocket` 加入列表，`disconnect` 移除，`broadcast` 遍历列表发送消息。心跳检测：定时发送 Ping/Pong 帧，超时未响应则断开连接。AI 场景中，WebSocket 用于流式输出 LLM 回复，每次生成一个 token 就发送一次。`@app.websocket("/ws")` 路由处理，`await websocket.accept()` 接受连接，`await websocket.receive_text()` 接收消息。

---

### Card 8: 流式响应（SSE）
**维度**: 💻代码 | **难度**: ⭐⭐

> **Q: FastAPI 如何实现流式响应？SSE 和 WebSocket 的适用场景有什么不同？**

**A:** 使用 `StreamingResponse(content, media_type="text/event-stream")`，`content` 是生成器，每次 `yield` 数据给客户端。SSE 是单向（服务端→客户端），适合实时推送（AI 回复流、日志流）。WebSocket 双向，适合聊天、游戏。AI 流式：`HTTPX` 请求 LLM API，`client.stream()` 接收流，`for chunk in response.aiter_lines(): yield f"data: {chunk}\n\n"`。

---

### Card 9: 依赖作用域与缓存
**维度**: 🔬深挖 | **难度**: ⭐⭐

> **Q: FastAPI 中依赖的缓存机制是怎样的？如何实现单例依赖？**

**A:** FastAPI 默认在同一请求中缓存依赖结果——多次调用同一个依赖函数只执行一次。`@lru_cache` 可跨请求缓存（如单例数据库连接池）。生成器依赖（`yield`）在请求结束时自动清理。`fastapi.Depends(..., use_cache=False)` 可禁用缓存，使每次调用都执行新实例。作用域：函数级（每次请求）、应用级（生命周期）。

---

### Card 10: 异常处理机制
**维度**: 💻代码 | **难度**: ⭐⭐

> **Q: FastAPI 如何统一处理异常？自定义异常处理器如何实现？**

**A:** `@app.exception_handler(HTTPException)` 捕获 HTTP 异常，`@app.exception_handler(ValueError)` 捕获自定义异常。示例：`@app.exception_handler(MyBusinessError); async def handler(request, exc): return JSONResponse(status_code=400, content={"code": exc.code, "msg": exc.msg})`。`HTTPException` 可带 `headers` 参数（如重定向）。生产环境建议统一异常处理，返回格式一致的错误响应。

---

### Card 11: 请求验证与校验
**维度**: 📝速记 | **难度**: ⭐

> **Q: FastAPI 如何实现请求参数验证？路径参数、查询参数、请求体如何校验？**

**A:** 路径参数：`@app.get("/items/{id}")`，`id: int` 自动校验类型。查询参数：`q: str = Query(None, min_length=3, max_length=50)`。请求体：`item: Item`（Pydantic 模型），自动校验字段类型、约束（`Field(ge=0)`）、正则。`Path`、`Query`、`Body` 等函数可添加额外校验（如 `alias`、`deprecated`、`example`）。校验失败自动返回 422 错误，包含详细错误信息。

---

### Card 12: 响应模型与过滤
**维度**: 💻代码 | **难度**: ⭐⭐

> **Q: FastAPI 的 response_model 如何实现数据过滤？**

**A:** `response_model=ItemOut` 自动过滤返回字段，只包含模型定义的字段。`response_model_exclude_unset=True` 排除未设置的字段（适合 PATCH 更新）。`response_model_include={'name', 'price'}` 或 `response_model_exclude={'id'}` 精确控制。内嵌 Pydantic 模型也递归过滤。`response_model_by_alias=True` 使用字段别名。安全：避免意外返回敏感字段（如 `password_hash`）。

---

### Card 13: 后台任务
**维度**: 📝速记 | **难度**: ⭐⭐

> **Q: FastAPI 的 BackgroundTasks 和 Celery 有什么区别？**

**A:** BackgroundTasks：轻量级，请求响应后执行，无持久化、无重试机制、无任务队列，适合发送邮件、日志记录等轻量任务。Celery：分布式任务队列，支持持久化、重试、定时任务、任务优先级、结果存储，适合大量或需要可靠性的后台任务。选择：简单场景用 BackgroundTasks，需要可靠性/分布式/定时任务用 Celery。

---

### Card 14: 测试策略
**维度**: 💻代码 | **难度**: ⭐⭐

> **Q: FastAPI 如何编写测试？TestClient 的用法和常见技巧？**

**A:** 使用 `from fastapi.testclient import TestClient`，`client = TestClient(app)`。用法：`response = client.get("/items/1", headers={"Authorization": "Bearer xxx"})`，`assert response.status_code == 200`，`assert response.json()["name"] == "item"`。技巧：① `app.dependency_overrides` 替换依赖；② 异步测试用 `pytest-asyncio` + `AsyncClient`；③ `response.text` 获取原始响应；④ 文件上传用 `client.post("/upload", files={"file": ("test.txt", b"content")})`。

---

### Card 15: 性能优化
**维度**: 🎯场景 | **难度**: ⭐⭐⭐

> **Q: FastAPI 生产环境性能优化有哪些关键点？**

**A:** ① 使用异步数据库驱动（`asyncpg`、`databases`）避免阻塞事件循环；② Gunicorn + Uvicorn Workers（多进程，`workers=4`）；③ 连接池（数据库/Redis 连接复用）；④ 响应压缩（`GZipMiddleware`）；⑤ 缓存（`@lru_cache` / Redis 缓存热点数据）；⑥ 数据库查询优化（N+1 问题、懒加载）；⑦ 限制请求体大小（`max_body_size`）；⑧ 使用 Pydantic v2（Rust 核心，更快校验）。