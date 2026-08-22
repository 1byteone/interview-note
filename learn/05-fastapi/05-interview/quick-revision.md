# 速记版 — 20 个高频考点

> 适用：🎯 面试冲刺
> 目标：快速回顾 FastAPI 核心知识点，5 分钟过一遍

---

## 1. FastAPI vs Django vs Flask

| 特性 | FastAPI | Django | Flask |
|------|---------|--------|-------|
| 异步原生 | 是 | 3.0+ 部分支持 | 否 |
| 性能 | 高（Starlette） | 中 | 低 |
| 类型安全 | 是（Pydantic） | 否 | 否 |
| 自动文档 | 内置 OpenAPI | django-rest-framework | flask-restx |
| 依赖注入 | 内置 Depends | 无 | 无 |
| 生态成熟度 | 中 | 高 | 高 |
| 学习曲线 | 低→中 | 中→高 | 低 |

---

## 2. 依赖注入（Depends）

- **定义**：`Depends(callable)` 将依赖函数的返回值注入路由参数
- **作用域**：每次请求创建新实例，`@lru_cache` 模拟单例
- **嵌套**：依赖可以依赖其他依赖，形成依赖链
- **可调用类**：`__call__` 方法使类实例可作依赖
- **资源清理**：生成器 `yield` + `finally` 自动释放资源
- **覆盖**：`app.dependency_overrides[orig] = mock` 用于测试

---

## 3. Pydantic v2

- **核心改进**：Rust 重写（pydantic-core），性能提升 5-50 倍
- **Field**：`ge/gt/le/lt`（数值）、`min_length/max_length`（字符串）、`pattern`（正则）
- **验证器**：`@field_validator`（单字段）、`@model_validator`（跨字段）
- **响应模型**：`response_model` 自动过滤、`response_model_exclude_unset` 排除未设置字段
- **配置管理**：`BaseSettings` + `.env` 文件
- **v2 变化**：`.dict()` → `.model_dump()`，`.json()` → `.model_dump_json()`

---

## 4. 异步处理

- `async def` 路由：事件循环执行，适合 IO 密集型
- `def` 路由：线程池执行，适合同步阻塞操作
- `asyncio.gather`：并发执行多个异步任务
- `BackgroundTasks`：轻量级后台任务，无持久化/重试
- 异步驱动：`asyncpg`（PG）、`motor`（MongoDB）、`httpx.AsyncClient`（HTTP）

---

## 5. WebSocket + SSE

- **WebSocket**：双向通信，`@app.websocket("/ws")`，`await websocket.accept()`
- **SSE**：服务端→客户端，`StreamingResponse(media_type="text/event-stream")`
- **连接管理**：`ConnectionManager` 类管理多个连接
- **心跳检测**：定时发送 Ping/Pong 检测连接存活
- **AI 流式**：`client.stream()` + `aiter_lines()` 实现 LLM 流式输出

---

## 6. 中间件

- **定义**：`@app.middleware("http")`，接收 `request` 和 `call_next`
- **洋葱模型**：先注册的中间件先处理请求，后处理响应
- **CORSMiddleware**：跨域配置，`allow_origins` 指定允许的源
- **执行顺序**：注册顺序决定执行顺序

---

## 7. 异常处理

- **HTTPException**：`raise HTTPException(status_code=400, detail="错误信息")`
- **自定义异常处理器**：`@app.exception_handler(CustomException)`
- **全局异常处理器**：`@app.exception_handler(Exception)` 兜底

---

## 8. 路由

- **装饰器家族**：`@app.get/post/put/patch/delete/options/head/websocket`
- **路径参数**：`/items/{item_id}`，函数参数带类型注解
- **查询参数**：函数参数（可选），自动从 URL 解析
- **路由顺序**：先注册的优先匹配
- **APIRouter**：路由模块化，`app.include_router()`

---

## 9. 自动文档

- Swagger UI：`/docs`
- ReDoc：`/redoc`
- OpenAPI JSON：`/openapi.json`
- 基于 Pydantic 模型和类型注解自动生成

---

## 10. 部署

- **Docker**：多阶段构建，减小镜像体积
- **Gunicorn + Uvicorn**：`gunicorn -k uvicorn.workers.UvicornWorker`
- **Nginx 反向代理**：WebSocket 需要 `Upgrade` 和 `Connection` 头
- **健康检查**：`/health` 端点，K8s 探针
- **项目结构**：按功能模块组织，按层分目录

---

## 11. 项目结构

```
app/
├── main.py           # 应用入口
├── config.py         # 配置
├── api/              # 路由
├── core/             # 核心功能
├── models/           # SQLAlchemy 模型
├── schemas/          # Pydantic 模型
├── services/         # 业务逻辑
└── utils/            # 工具
```

---

## 12. 测试

- **TestClient**：基于 httpx，无需启动服务
- **依赖覆盖**：`app.dependency_overrides[dep] = mock`
- **pytest-asyncio**：测试异步路由
- **Locust**：性能压测

---

## 13. 限流与熔断

- **令牌桶**：`TokenBucket` 控制请求速率
- **熔断器**：`CircuitBreaker` 防止级联故障（CLOSED→OPEN→HALF_OPEN）

---

## 14. ASGI 协议

- **定义**：异步服务器网关接口（PEP 4843）
- **对比 WSGI**：ASGI 支持异步和 WebSocket，WSGI 仅同步 HTTP
- **Starlette**：FastAPI 底层框架，提供 ASGI 基础能力

---

## 15. 类型注解

- Python 3.10+：`str | int | None` 替代 `Optional[str]`
- 泛型：`list[str]`、`dict[str, int]`
- FastAPI 自动基于类型注解解析参数、验证数据、生成文档

---

## 16. 后台任务

- **BackgroundTasks**：轻量级，同一进程
- **Celery**：重量级，独立 worker，支持持久化和重试
- 选择依据：任务复杂度、是否需要分布式

---

## 17. 数据库集成

- **同步**：SQLAlchemy（同步模式）+ 线程池
- **异步**：SQLAlchemy 1.4+（异步模式）+ asyncpg
- **连接池**：`create_engine(pool_size=10, max_overflow=20)`
- **会话管理**：生成器依赖 + `yield` + `finally`

---

## 18. 配置管理

- **BaseSettings**：从环境变量、`.env` 文件加载
- **@lru_cache**：确保配置单例
- **环境分离**：开发/测试/生产使用不同 `.env` 文件

---

## 19. 性能优化

- 使用异步驱动避免阻塞
- 连接池复用
- 缓存（Redis）
- 数据库索引
- Gunicorn 多 worker
- Nginx 反向代理

---

## 20. 常见陷阱

| 陷阱 | 说明 | 解决方案 |
|------|------|----------|
| `async def` 中阻塞 | 阻塞事件循环 | 使用 `asyncio.to_thread` 或 `def` |
| 路由顺序错误 | 路径参数吞精确路径 | 精确路径在前 |
| 共享可变对象 | 多请求共享状态 | 每次请求创建新实例 |
| 忘记 `await` | 异步函数未执行 | 检查 `await` 关键字 |
| CORS 配置过松 | `allow_origins=["*"]` 生产环境不安全 | 指定具体域名 |