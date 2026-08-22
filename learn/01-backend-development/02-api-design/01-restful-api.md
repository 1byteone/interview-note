# RESTful API 设计最佳实践

## Situation

你是 AI 商城的后端开发者，需要设计一套商品查询 API。如果返回格式不统一、分页参数不一致、错误码靠猜，前端同学每次对接都要翻聊天记录找你要字段说明。这不是"风格问题"，而是**协作效率问题**。

## Task

掌握 RESTful API 的核心设计规范，能在面试中完整描述一个"好 API"长什么样，并在实际项目中落地统一的 API 契约。

## Action

### 1. 资源命名与 URL 设计

**核心规则**：URL 代表资源（名词），HTTP 方法代表操作（动词）。

| 资源 | GET | POST | PUT | PATCH | DELETE |
|------|-----|------|-----|-------|--------|
| /api/v1/products | 获取商品列表 | 创建新商品 | 批量替换 | 批量更新 | 删除所有（谨慎） |
| /api/v1/products/{id} | 获取单个商品 | 405 | 全量更新商品 | 部分更新 | 删除商品 |
| /api/v1/products/{id}/reviews | 获取商品评论 | 创建评论 | 405 | 405 | 405 |

**命名规范**：
- 使用**小写 + 中划线**：`/api/v1/new-arrivals` 而非 `/api/v1/newArrivals` 或 `/api/v1/new_arrivals`
- 路径用**复数名词**：`/api/v1/products` 而非 `/api/v1/product`
- 子资源用嵌套，但不超过两层：`/api/v1/categories/{id}/products` 而非 `/api/v1/categories/{id}/products/{pid}/reviews`
- 筛选 / 排序 / 分页用**查询参数**，不要放路径里：`/api/v1/products?category=phone&sort=price_asc&page=1&size=20`

### 2. HTTP 方法语义

Spring Boot 示例：

```java
@RestController
@RequestMapping("/api/v1/products")
public class ProductController {

    @GetMapping
    public Result<Page<ProductDTO>> list(@Valid ProductQuery query) { ... }

    @GetMapping("/{id}")
    public Result<ProductDTO> getById(@PathVariable Long id) { ... }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Result<ProductDTO> create(@Valid @RequestBody ProductCreateReq req) { ... }

    @PutMapping("/{id}")
    public Result<ProductDTO> update(@PathVariable Long id,
                                     @Valid @RequestBody ProductUpdateReq req) { ... }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) { ... }
}
```

### 3. 状态码选择

| 场景 | 状态码 | 说明 |
|------|--------|------|
| 获取成功 | 200 OK | |
| 创建成功 | 201 Created | 同时返回 Location header |
| 删除成功、无内容 | 204 No Content | |
| 请求参数错误 | 400 Bad Request | 校验失败、参数缺失 |
| 未认证 | 401 Unauthorized | 缺少或无效的 Token |
| 未授权 | 403 Forbidden | 有 Token 但无权限 |
| 资源不存在 | 404 Not Found | |
| 请求冲突 | 409 Conflict | 重复创建、版本冲突 |
| 请求过多 | 429 Too Many Requests | 限流触发 |
| 服务端异常 | 500 Internal Server Error | 未捕获的异常 |
| 服务不可用 | 503 Service Unavailable | 熔断、降级 |

**不要滥用 200**：不管成功还是失败都返回 200 然后在 body 里放一个 `code=1` 表示错误——这是 RPC 风格的遗留习惯，RESTful 应该用 HTTP 状态码表达语义。

### 4. 版本管理

| 策略 | 示例 | 优缺点 |
|------|------|--------|
| URL 路径版本 | `/api/v1/products` | 最常用，直观，破坏性变更不影响旧版本 |
| Header 版本 | `Accept: application/vnd.ai-shop.v1+json` | 干净 URL，但调试不直观 |
| 查询参数版本 | `/api/products?version=1` | 容易污染缓存，不推荐 |

**推荐**：URL 路径版本 + 6-12 个月过渡期，过期版本返回 410 Gone。

### 5. 分页、过滤与排序

统一的分页请求与响应格式：

```java
// 请求：推荐 page + size（从 1 开始）
// GET /api/v1/products?page=1&size=20&sort=createTime,desc&category=phone

// 响应
{
  "code": 200,
  "message": "success",
  "data": {
    "content": [ ... ],
    "page": 1,
    "size": 20,
    "totalElements": 156,
    "totalPages": 8,
    "first": true,
    "last": false
  }
}
```

**分页策略对比**：

| 策略 | 原理 | 优点 | 缺点 |
|------|------|------|------|
| offset/limit | `OFFSET 100 LIMIT 20` | 实现简单，可跳页 | 大 offset 性能差 |
| cursor-based | `WHERE id > :lastId LIMIT 20` | 稳定高性能，适合实时数据 | 不能跳页，适合无限滚动 |

**适合场景**：后台管理用 offset 分页；C 端商品列表、朋友圈用 cursor 分页。

### 6. 统一错误响应格式

```java
public class Result<T> {
    private int code;
    private String message;
    private T data;
    private long timestamp;
    private String traceId;  // 用于链路追踪
}

// 错误时
{
  "code": 400,
  "message": "请求参数校验失败",
  "data": {
    "field": "price",
    "error": "价格不能为负数"
  },
  "timestamp": 1712345678000,
  "traceId": "a1b2c3d4e5f6"
}
```

Spring Boot 全局异常处理：

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleValidation(MethodArgumentNotValidException ex) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
            .map(e -> e.getField() + ": " + e.getDefaultMessage())
            .collect(Collectors.joining(", "));
        return Result.error(400, msg);
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusiness(BusinessException ex) {
        HttpStatus status = ex.getHttpStatus();
        return ResponseEntity.status(status)
            .body(Result.error(status.value(), ex.getMessage()));
    }
}
```

### 7. HATEOAS 与超媒体（进阶）

RESTful 的终极形态是 HATEOAS（Hypermedia As The Engine Of Application State）：每个响应中包含可用的下一步操作链接。但在实际项目中，除了一些公共 API 平台，很少完全实现。知道概念即可，面试中提一下能加分。

## Result

一个好的 RESTful API 设计应该满足：

1. URL 是名词 + 复数，方法表达语义
2. 状态码正确表达 HTTP 语义
3. 一致的分页 / 过滤 / 排序规范
4. 统一的错误响应格式 + traceId 可追踪
5. 版本管理策略明确，旧版本有退役计划

> 面试金句："RESTful 不是 URL 长得像 REST 就完了，关键在于**资源导向**——每个 URL 代表一个资源，HTTP 方法代表对这个资源的操作，状态码告知操作结果。我们的项目统一用 `/api/v1/{resource}` 路径，错误响应带 traceId 方便排查。"

---

## 附：常见 REST "反模式"

| 反模式 | 示例 | 改进 |
|--------|------|------|
| 动词路径 | `/api/getProductById` | `GET /api/v1/products/{id}` |
| 只有 GET 和 POST | 所有操作都用 GET 或 POST | 按语义使用 PUT/PATCH/DELETE |
| 全部返回 200 | 错误也在 body 里放 `code: 1` | 用 HTTP 状态码 |
| 嵌套过深 | `/api/v1/users/{uid}/orders/{oid}/items/{iid}` | 用查询参数或子资源查询 |
| 忽略缓存 | 没有设置 ETag 或 Last-Modified | 添加缓存头，减少重复查询 |