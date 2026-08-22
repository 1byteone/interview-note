# 04-projects/mall-integration.md

> 适用：🎯 进阶
> 目标：设计 AI 商城的搜索服务架构，实现 FastAPI + LangChain 集成

---

## 1. AI 商城搜索服务架构

```
┌──────────────┐         ┌─────────────────┐         ┌──────────────┐
│  客户端       │         │   API 网关       │         │  FastAPI     │
│  Web / App   │ ──────▶ │  Nginx / K8s    │ ──────▶ │  搜索服务     │
└──────────────┘         └─────────────────┘         └──────┬───────┘
                                                            │
                    ┌───────────────────────────────────────┼───────────────────┐
                    │                                       │                   │
                    ▼                                       ▼                   ▼
            ┌──────────────┐                      ┌─────────────────┐
            │  Elasticsearch │                      │  LLM 路由层      │
            │  商品全文检索   │                      │                  │
            └──────────────┘                      │  ┌───────────┐  │
                                                  │  │ OpenAI    │  │
            ┌──────────────┐                      │  ├───────────┤  │
            │  Redis        │                      │  │ 本地模型   │  │
            │  缓存 + 限流   │                      │  ├───────────┤  │
            └──────────────┘                      │  │ 备用模型   │  │
                                                  │  └───────────┘  │
            ┌──────────────┐                      └─────────────────┘
            │ PostgreSQL   │
            │  商品/订单数据  │
            └──────────────┘
```

### 请求流程

1. 客户端发送搜索请求到 FastAPI
2. FastAPI 通过依赖注入获取认证信息和请求参数
3. 查询 Elasticsearch 获取商品匹配结果
4. 同时异步调用 LLM 生成智能推荐
5. 合并结果返回给客户端（或通过 SSE 流式推送）
6. 后台任务记录搜索日志到 PostgreSQL

---

## 2. FastAPI + LangChain 集成

```python
from fastapi import FastAPI, Depends, HTTPException, BackgroundTasks
from pydantic import BaseModel, Field
from typing import Optional
import asyncio

app = FastAPI(title="AI 商城搜索服务")


# ============ 数据模型 ============

class SearchRequest(BaseModel):
    query: str = Field(..., min_length=1, max_length=200)
    category: Optional[str] = None
    page: int = Field(default=1, ge=1)
    size: int = Field(default=10, ge=1, le=50)
    user_id: Optional[int] = None


class SearchResult(BaseModel):
    product_id: int
    name: str
    price: float
    category: str
    score: float
    description: str


class SearchResponse(BaseModel):
    query: str
    results: list[SearchResult]
    total: int
    page: int
    size: int
    ai_recommendation: Optional[str] = None
    suggested_queries: list[str] = []


# ============ 搜索服务 ============

class SearchService:
    """搜索服务——整合 ES + LLM"""

    def __init__(self):
        self.es_client = None  # 实际项目中初始化 ES 客户端
        self.llm_chain = None  # 实际项目中初始化 LangChain

    async def search_products(
        self, query: str, category: str | None,
        page: int, size: int,
    ) -> tuple[list[SearchResult], int]:
        """Elasticsearch 商品搜索"""
        # 模拟查询
        await asyncio.sleep(0.05)
        results = [
            SearchResult(
                product_id=1,
                name="iPhone 15 Pro",
                price=8999,
                category="手机",
                score=0.95,
                description="Apple 最新旗舰手机",
            ),
        ]
        return results, 1

    async def generate_recommendation(
        self, query: str, results: list[SearchResult],
    ) -> str:
        """LangChain 生成 AI 推荐"""
        # 实际项目中调用 LangChain chain
        # chain = LLMChain(llm=llm, prompt=prompt_template)
        # return await chain.arun(query=query, products=results)
        await asyncio.sleep(0.2)
        return f"根据您的搜索'{query}'，推荐 {results[0].name}，性价比很高。"

    async def suggest_queries(self, query: str) -> list[str]:
        """联想搜索建议"""
        return [f"{query} 新款", f"{query} 热销", f"{query} 折扣"]


@lru_cache()
def get_search_service() -> SearchService:
    return SearchService()


# ============ API 端点 ============

@app.post("/api/v1/search", response_model=SearchResponse)
async def search(
    request: SearchRequest,
    search_service: SearchService = Depends(get_search_service),
    background_tasks: BackgroundTasks = BackgroundTasks(),
):
    """AI 智能搜索"""

    # 1. ES 商品搜索
    products, total = await search_service.search_products(
        request.query, request.category,
        request.page, request.size,
    )

    # 2. 并发生成 AI 推荐和搜索建议
    recommendation_task = search_service.generate_recommendation(
        request.query, products,
    )
    suggest_task = search_service.suggest_queries(request.query)
    recommendation, suggestions = await asyncio.gather(
        recommendation_task, suggest_task,
    )

    # 3. 后台记录搜索日志
    background_tasks.add_task(
        log_search, request.query, request.user_id, total,
    )

    return SearchResponse(
        query=request.query,
        results=products,
        total=total,
        page=request.page,
        size=request.size,
        ai_recommendation=recommendation,
        suggested_queries=suggestions,
    )


async def log_search(query: str, user_id: int | None, total: int):
    """后台记录搜索日志"""
    # 写入 PostgreSQL 或日志文件
    pass
```

---

## 3. 多模型路由

```python
from fastapi import FastAPI, Depends, HTTPException
from pydantic import BaseModel
from enum import Enum
import random

app = FastAPI()


class ModelType(str, Enum):
    OPENAI = "openai"
    LOCAL = "local"
    FALLBACK = "fallback"


class ModelRouter:
    """多模型路由——根据请求自动选择模型"""

    def __init__(self):
        self.models = {
            ModelType.OPENAI: {"endpoint": "https://api.openai.com", "cost": 0.01},
            ModelType.LOCAL: {"endpoint": "http://localhost:8080", "cost": 0.001},
            ModelType.FALLBACK: {"endpoint": "http://localhost:8081", "cost": 0.005},
        }

    def select_model(self, query: str, user_tier: str = "normal") -> ModelType:
        """选择模型策略"""
        # VIP 用户使用 OpenAI
        if user_tier == "vip":
            return ModelType.OPENAI

        # 简单查询使用本地模型
        if len(query) < 10:
            return ModelType.LOCAL

        # 复杂查询使用 OpenAI
        return ModelType.OPENAI

    async def call_model(self, model_type: ModelType, prompt: str) -> str:
        """调用指定模型"""
        # 实际项目中调用不同的 API
        await asyncio.sleep(0.1)
        return f"[{model_type.value}] 回复: {prompt}"


router = ModelRouter()


@app.get("/api/v1/chat")
async def chat(
    query: str,
    user_tier: str = "normal",
    model_router: ModelRouter = Depends(lambda: router),
):
    model_type = model_router.select_model(query, user_tier)
    response = await model_router.call_model(model_type, query)
    return {"model": model_type, "response": response}
```

---

## 4. 请求限流与熔断

### 令牌桶限流

```python
import time
import asyncio
from fastapi import FastAPI, Request, HTTPException
from fastapi.responses import JSONResponse

app = FastAPI()


class TokenBucket:
    """令牌桶限流器"""
    def __init__(self, rate: float, capacity: int):
        self.rate = rate          # 每秒产生的令牌数
        self.capacity = capacity  # 桶容量
        self.tokens = capacity    # 当前令牌数
        self.last_refill = time.time()
        self.lock = asyncio.Lock()

    async def consume(self, tokens: int = 1) -> bool:
        async with self.lock:
            now = time.time()
            # 补充令牌
            elapsed = now - self.last_refill
            self.tokens = min(self.capacity, self.tokens + elapsed * self.rate)
            self.last_refill = now

            if self.tokens >= tokens:
                self.tokens -= tokens
                return True
            return False


# 不同 API 的限流策略
search_bucket = TokenBucket(rate=50, capacity=100)   # 搜索 API：50/s
chat_bucket = TokenBucket(rate=10, capacity=20)      # 聊天 API：10/s


@app.middleware("http")
async def rate_limit_middleware(request: Request, call_next):
    path = request.url.path

    if "/api/v1/search" in path:
        bucket = search_bucket
    elif "/api/v1/chat" in path:
        bucket = chat_bucket
    else:
        return await call_next(request)

    if not await bucket.consume():
        return JSONResponse(
            status_code=429,
            content={"code": "RATE_LIMITED", "message": "请求过于频繁，请稍后重试"},
        )

    return await call_next(request)
```

### 熔断器模式

```python
import asyncio
from enum import Enum
from datetime import datetime, timedelta


class CircuitState(Enum):
    CLOSED = "closed"       # 正常
    OPEN = "open"           # 熔断
    HALF_OPEN = "half_open"  # 半开


class CircuitBreaker:
    """熔断器——防止级联故障"""
    def __init__(
        self,
        failure_threshold: int = 5,       # 失败次数阈值
        recovery_timeout: int = 30,        # 恢复超时（秒）
        half_open_max_requests: int = 3,   # 半开状态最大请求数
    ):
        self.failure_threshold = failure_threshold
        self.recovery_timeout = recovery_timeout
        self.half_open_max_requests = half_open_max_requests

        self.state = CircuitState.CLOSED
        self.failure_count = 0
        self.last_failure_time = None
        self.half_open_requests = 0

    async def call(self, func, *args, **kwargs):
        if self.state == CircuitState.OPEN:
            if datetime.now() - self.last_failure_time > timedelta(
                seconds=self.recovery_timeout
            ):
                self.state = CircuitState.HALF_OPEN
                self.half_open_requests = 0
            else:
                raise Exception("电路熔断中")

        if self.state == CircuitState.HALF_OPEN:
            if self.half_open_requests >= self.half_open_max_requests:
                raise Exception("半开状态请求上限")

        try:
            self.half_open_requests += 1
            result = await func(*args, **kwargs)

            # 成功——重置
            self.state = CircuitState.CLOSED
            self.failure_count = 0
            self.half_open_requests = 0
            return result
        except Exception as e:
            self.failure_count += 1
            self.last_failure_time = datetime.now()

            if self.failure_count >= self.failure_threshold:
                self.state = CircuitState.OPEN

            raise e


# 使用
llm_circuit_breaker = CircuitBreaker(
    failure_threshold=3,
    recovery_timeout=30,
)


@app.post("/api/v1/chat")
async def chat_with_llm(query: str):
    try:
        result = await llm_circuit_breaker.call(
            call_llm_api, query,
        )
        return {"response": result}
    except Exception as e:
        # 熔断时返回兜底回复
        return {"response": "AI 服务暂时不可用，请稍后再试", "fallback": True}
```

---

## 本章小结

| 模块 | 技术 | 说明 |
|------|------|------|
| 搜索服务 | FastAPI + Elasticsearch | 商品全文检索 |
| AI 推荐 | LangChain + LLM | 智能推荐生成 |
| 多模型路由 | 策略模式 | 根据查询/用户选择模型 |
| 限流 | 令牌桶 | 防止滥用 |
| 熔断 | 断路器模式 | 防止级联故障 |
| 异步 | asyncio.gather | 并发执行多个任务 |
| 后台任务 | BackgroundTasks | 搜索日志记录 |

下一章将介绍迷你博客项目实战。