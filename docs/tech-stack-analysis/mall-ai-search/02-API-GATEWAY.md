# 02 · FastAPI 网关层：Pydantic + 路由 + 异常处理

> 请求到达后端的第一站，看 FastAPI 如何组织路由、统一响应体、处理全局异常，以及它与 Spring Boot 的设计模式对应关系。
>
> **对应项目：** `src/smart_search/main.py` + `api/v1.py` + `models/schemas.py`

---

## 一、基础概念

### 1.1 FastAPI 是什么

FastAPI 是一个现代 Python Web 框架，专为构建 API 而生。

| 特性 | FastAPI | Spring Boot 对照 |
|------|---------|-----------------|
| 类型安全 | Pydantic v2 模型 | Bean Validation (JSR-380) |
| 自动文档 | 自动生成 Swagger/OpenAPI | SpringDoc OpenAPI |
| 异步支持 | 原生 `async def` | `@Async` / WebFlux |
| 依赖注入 | `Depends()` | `@Autowired` + `@Bean` |
| 路由声明 | `@router.get()` | `@GetMapping` / `@RequestMapping` |

### 1.2 项目中的 FastAPI 应用

```python
app = FastAPI(
    title="商品智能搜索",
    description="商品智能搜索",
    version="1.0.0"
)

# 注册 v1 路由
app.include_router(v1_router)
```

应用层非常薄——只做三件事：
1. 注册路由
2. 统一异常处理
3. 启动服务

---

## 二、进阶机制

### 2.1 Pydantic v2 —— 类型安全的响应体

项目中定义了通用的泛型响应体：

```python
from typing import Generic, Optional, TypeVar

T = TypeVar("T")

class Result(BaseModel, Generic[T]):
    """接口通用返回体"""
    code: int = Field(default=200)
    msg: str = Field(default="操作成功")
    data: Optional[T] = None
```

**设计要点：**

| 要素 | 作用 | Spring Boot 对照 |
|------|------|-----------------|
| `Generic[T]` | 泛型，不同类型接口复用同一包装类 | `Result<T>` 泛型类 |
| `Field(default=200)` | 默认值，未传参时自动填充 | `@Builder.Default` 或构造函数默认值 |
| `Optional[T]` | 可能为 null 的字段 | `@Nullable` 注解 |

**Java 对照：**

```java
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Result<T> {
    private int code = 200;
    private String msg = "操作成功";
    private T data;
}
```

### 2.2 APIRouter —— 模块化路由

```python
from fastapi import APIRouter

router = APIRouter(prefix="/api/v1", tags=["商品智能搜索接口"])

@router.get("/recommend", summary="商品智能推荐")
async def recommend(query: str, thread_id: int = 0) -> Result[ProductRecommendResponse]:
    ...

@router.get("/extract", summary="商品查询条件拆解")
async def extract(query: str) -> Result[SearchCondition]:
    ...

@router.get("/sync", summary="同步商品向量")
def sync() -> Result[str]:
    ...

@router.get("/test", summary="示例")
def home():
    return {"message": "智能搜索服务启动成功"}
```

**对比 Spring Boot：**

| Python (FastAPI) | Java (Spring Boot) |
|-----------------|-------------------|
| `APIRouter(prefix="/api/v1")` | `@RequestMapping("/api/v1")` |
| `@router.get("/recommend")` | `@GetMapping("/recommend")` |
| `query: str` 参数绑定 | `@RequestParam("query") String query` |
| `async def` | `CompletableFuture` / `Mono` |

### 2.3 全局异常处理

```python
@app.exception_handler(Exception)
async def global_exception_handler(request: Request, e: Exception):
    logger.error(f"全局异常 - 请求路径: {request.url.path} - 错误信息: {str(e)}", exc_info=True)
    resp = Result(code=500, msg=f"服务器内部错误:{str(e)}")
    return JSONResponse(content=resp.model_dump())
```

**设计要点：**

1. **统一捕获** — 所有未被方法捕获的异常最终落在这里
2. **统一格式** — 异常响应也走 `Result` 包装，客户端解析逻辑不变
3. **记录上下文** — 打印请求路径，便于排查
4. **不泄露敏感信息** — 生产环境应替换为不暴露堆栈的通用消息

**对比 Spring Boot：**

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(HttpServletRequest request, Exception e) {
        log.error("全局异常 - 请求路径: {} - 错误信息: ", request.getRequestURI(), e);
        return new Result<>(500, "服务器内部错误");
    }
}
```

---

## 三、项目现场

### 3.1 惰性单例模式

项目中一个值得注意的设计是**惰性单例**：

```python
# 不在 import 时实例化，避免模块加载阶段触发真实连接
searchService: SearchService | None = None
productVectorSyncService: ProductVectorSyncService | None = None

def get_search_service() -> SearchService:
    global searchService
    if searchService is None:
        searchService = SearchService()
    return searchService

def get_product_vector_sync_service() -> ProductVectorSyncService:
    global productVectorSyncService
    if productVectorSyncService is None:
        productVectorSyncService = ProductVectorSyncService()
    return productVectorSyncService
```

**为什么要这样设计？**

`SearchService` 的构造函数会调用 `tools.get_model()` 和 `tools.get_vector_store()`，这两个方法会建立 LLM 连接和 Redis 向量库连接。如果模块加载时立即实例化：

1. 单元测试时即使不测试搜索功能，也会触发真实连接
2. 应用启动时环境变量未就绪，连接可能失败
3. 首次请求延迟被前置到启动阶段，但启动后不需要的模块白占资源

**对比 Spring Boot：**

```java
@Component
@Lazy  // Spring 的惰性初始化
public class SearchService {
    // 首次被注入时才会创建实例
}
```

### 3.2 路由设计分析

| 路由 | 方法 | 职责 | 调用方 |
|------|------|------|--------|
| `GET /api/v1/test` | 同步 | 健康检查 | 前端/监控 |
| `GET /api/v1/sync` | 同步 | 触发数据同步 | 管理员手动调用 |
| `GET /api/v1/recommend` | 异步 | AI 商品推荐 | 前端用户搜索 |
| `GET /api/v1/extract` | 异步 | 查询条件提取 | 前端用户搜索 |

**同步 vs 异步的选择：**
- `/sync` 数据同步是 CPU 密集型操作，但使用同步方法，因为调用方（管理员）不关心高并发
- `/recommend` 和 `/extract` 使用 `async def`，因为涉及 LLM 网络调用（IO 密集型），异步能释放线程资源

---

## 四、Java 对照 —— 完整等价实现

### 4.1 Spring Boot 对照

```java
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;
    private final ProductVectorSyncService syncService;

    @GetMapping("/test")
    public Map<String, String> home() {
        return Map.of("message", "智能搜索服务启动成功");
    }

    @GetMapping("/sync")
    public Result<String> sync() {
        String result = syncService.loadSkuFromMysql();
        return Result.success(result);
    }

    @GetMapping("/recommend")
    public Result<ProductRecommendResponse> recommend(
            @RequestParam String query,
            @RequestParam(defaultValue = "0") int threadId) {
        ProductRecommendResponse data = searchService.recommendProduct(query, threadId);
        return Result.success(data);
    }

    @GetMapping("/extract")
    public Result<SearchCondition> extract(@RequestParam String query) {
        SearchCondition condition = searchService.extractSearchCondition(query);
        return Result.success(condition);
    }
}
```

### 4.2 主要差异

| 维度 | FastAPI | Spring Boot | 备注 |
|------|---------|-------------|------|
| 参数校验 | 类型注解自动校验 | `@Valid` + `@NotBlank` | 两者都声明式 |
| 异步 | `async def` 原生 | `@Async` + `CompletableFuture` | FastAPI 更简洁 |
| 响应序列化 | `.model_dump()` | Jackson 自动序列化 | 概念相同 |
| 依赖注入 | 手写惰性单例 | `@Autowired` 容器管理 | Spring 更自动化 |
| 路由前缀 | `prefix="/api/v1"` | `@RequestMapping("/api/v1")` | 几乎一致 |

---

## 五、最小可复现示例

### 5.1 FastAPI 基础模板

```python
# main.py
from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse
from pydantic import BaseModel, Field
from typing import Generic, Optional, TypeVar

T = TypeVar("T")

class Result(BaseModel, Generic[T]):
    code: int = Field(default=200)
    msg: str = Field(default="操作成功")
    data: Optional[T] = None

app = FastAPI(title="API 模板", version="1.0.0")

@app.exception_handler(Exception)
async def global_handler(request: Request, e: Exception):
    return JSONResponse(
        content=Result(code=500, msg=f"服务器内部错误").model_dump()
    )

@app.get("/hello")
async def hello(name: str = "World") -> Result[str]:
    return Result(data=f"Hello, {name}!")

if __name__ == "__main__":
    import uvicorn
    uvicorn.run("main:app", host="0.0.0.0", port=9010, reload=True)
```

### 5.2 Spring Boot 对照

```java
@SpringBootApplication
@RestController
public class DemoApplication {

    @GetMapping("/hello")
    public Result<String> hello(@RequestParam(defaultValue = "World") String name) {
        return Result.success("Hello, " + name + "!");
    }

    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }
}
```

---

## 六、面试要点

### Q1: FastAPI 和 Spring Boot 的核心差异是什么？

**回答思路：** FastAPI 是轻量级异步框架，Python 类型注解即校验；Spring Boot 是重量级 IoC 容器，通过注解和配置驱动。FastAPI 适合 AI 推理/数据类服务，Spring Boot 适合复杂业务逻辑和企业级中间件集成。

### Q2: 为什么项目中的 SearchService 用惰性单例而不是模块级单例？

**回答思路：** 避免 import 时触发真实连接（LLM/Redis），遵循"按需加载"原则。模块加载应只做声明，连接放在首次调用时建立。

### Q3: 全局异常处理的设计要点是什么？

**回答思路：** 统一捕获、统一格式、记录上下文、不泄露敏感信息。关键是一定要保证异常响应也符合 `Result<T>` 结构，让客户端解析逻辑一致。

### Q4: 哪些路由用 async，哪些用同步，依据是什么？

**回答思路：** IO 密集型（网络调用 LLM/Redis）用 async，释放线程；CPU 密集型（数据同步）用同步。FastAPI 的 async 只对 IO 密集型有意义，CPU 密集型用 async 反而增加开销。

---

> **下一篇：** [03-CONFIG-MULTI-PROVIDER.md —— 多 Provider 配置体系：pydantic-settings + 策略模式](./03-CONFIG-MULTI-PROVIDER.md)
>
> 深入配置层，看项目如何用 pydantic-settings 和策略模式实现多 AI 供应商的无缝切换。