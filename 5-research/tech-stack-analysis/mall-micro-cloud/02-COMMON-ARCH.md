# 02 · 公共模块与统一架构：Result、异常处理、Feign 接口设计

> 10 个微服务如何保持统一的代码风格、响应格式和接口规范？mall-common 和 mall-api 两个公共模块提供了答案。
>
> **对应项目：** `mall-common` + `mall-api`

---

## 一、基础概念

### 1.1 公共模块的职责

| 模块 | 职责 | 被依赖方 |
|------|------|---------|
| **mall-common** | 统一响应体、全局异常处理、拦截器、工具类、配置类 | 所有微服务 |
| **mall-api** | Feign 接口定义、DTO 数据传输对象、Fallback 容错 | 服务间调用 |

**设计原则：** 公共模块只放"所有服务都需要的"代码，不放业务逻辑。

### 1.2 项目中的公共模块清单

```
mall-common/
├── advice/              # 全局异常处理 + 响应包装
├── annotation/          # 自定义注解 (@Login4j)
├── config/              # 公共配置 (MyBatis-Plus, Redis, Web)
├── constant/            # 常量枚举 (ResultCodeEnum, TradeEnum)
├── exception/           # 自定义异常 (BusinessException)
├── filter/              # 布隆过滤器 (CacheBloomFilter)
├── interceptor/         # 拦截器 (LoginInterceptor, AuthorizationInterceptor)
├── util/                # 工具类 (JwtUtil, EncryptHandler, JsonUtils)
└── vo/                  # 统一响应体 (Result<T>)

mall-api/
├── cart/                # 购物车 Feign 接口
├── order/               # 订单 Feign 接口
├── product/             # 商品 Feign 接口 + Fallback
├── pay/                 # 支付 Feign 接口
├── seckill/             # 秒杀 Feign 接口
├── aisearch/            # AI 搜索 Feign 接口
├── oss/                 # OSS Feign 接口
├── config/              # Feign 自动扫描配置
└── interceptor/         # Feign 请求头传递拦截器
```

---

## 二、进阶机制

### 2.1 Result<T> —— 统一响应体

```java
@Data
public class Result<T> {
    private Integer code;
    private String msg;
    private T data;

    // 静态工厂方法
    public static <T> Result<T> success() { ... }
    public static <T> Result<T> success(T data) { ... }
    public static <T> Result<T> fail(Integer code, String msg) { ... }
    public static <T> Result<T> fail(ResultCodeEnum enums) { ... }
}
```

**为什么这么设计？**

| 字段 | 类型 | 说明 | 与 FastAPI 的对照 |
|------|------|------|-----------------|
| `code` | Integer | 业务状态码（200 成功，其他失败） | `code: int` |
| `msg` | String | 提示信息 | `msg: str` |
| `data` | T | 泛型数据体 | `data: Optional[T]` |

**对比 mall-ai-search 的 Python Result：**

```python
# Python 版
class Result(BaseModel, Generic[T]):
    code: int = Field(default=200)
    msg: str = Field(default="操作成功")
    data: Optional[T] = None
```

两者几乎完全一样——这是统一响应体的"标准模式"。

### 2.2 GlobalResponseAdvice —— 统一响应包装

```java
@ControllerAdvice
public class GlobalResponseAdvice implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        // 不包装: 返回值已经是 Result 类型 或 OpenAPI 请求
        return !returnType.getParameterType().equals(Result.class) && !isOpenApiRequest();
    }

    @Override
    public Object beforeBodyWrite(Object body, ...) {
        if (body instanceof String) {
            return JSON.toJSONString(Result.success(body));  // String 特殊处理
        } else if (body instanceof Boolean) {
            return Result.success();  // Boolean 返回空成功
        }
        return Result.success(body);  // 其他类型自动包装
    }
}
```

**核心价值：** 业务 Controller 只需返回业务数据，**无需手动包装 Result**：

```java
// 不需要写 return Result.success(data)
@GetMapping("/product/detail")
public ProductDTO detail(Long id) {      // 返回业务对象
    return productService.getById(id);   // 自动被包装为 Result<ProductDTO>
}
```

**String 特殊处理的原因：** Spring MVC 的 `StringHttpMessageConverter` 会直接写入响应体，不经过 JSON 序列化。如果 `ResponseBodyAdvice` 返回 `Result<String>`，会被 `StringHttpMessageConverter` 直接 toString 为 `Result(code=200, msg=OK, data=hello)` 字符串，而不是 JSON。所以 String 类型需要提前序列化为 JSON 字符串。

### 2.3 GlobalExceptionHandler —— 分层异常处理

```java
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // 1. 自定义业务异常
    @ExceptionHandler(BusinessException.class)
    public Result<?> businessException(BusinessException e, HttpServletRequest req) {
        log.error("【{}】{}", req.getRequestURI(), e.getMessage());
        return Result.fail(e.getCode(), e.getMessage());
    }

    // 2. 参数校验失败（@Valid / @NotBlank）
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<?> validException(MethodArgumentNotValidException e) {
        String errMsg = e.getBindingResult().getFieldError().getDefaultMessage();
        return Result.fail(ResultCodeEnum.PARAM_ERROR.getCode(), errMsg);
    }

    // 3. 404 资源未找到
    @ExceptionHandler(NoHandlerFoundException.class)
    public Result<?> notFound(NoHandlerFoundException e) {
        return Result.fail(ResultCodeEnum.NOT_FOUND);
    }

    // 4. 权限不足
    @ExceptionHandler(AccessDeniedException.class)
    public Result<?> accessDenied() {
        return Result.fail(ResultCodeEnum.UNAUTHORIZED);
    }

    // 5. 兜底：所有未捕获的未知异常
    @ExceptionHandler(Exception.class)
    public Result<?> globalException(Exception e) {
        log.error("全局异常：{}", e.getMessage());
        e.printStackTrace();
        return Result.fail(ResultCodeEnum.FAIL);
    }
}
```

**异常处理的分层策略：**

| 异常类型 | 捕获方式 | 响应码 | 说明 |
|---------|---------|--------|------|
| 业务异常 | `BusinessException` | 自定义 | 主动抛出的业务错误 |
| 参数校验 | `MethodArgumentNotValidException` | 400 | 参数校验框架自动抛出 |
| 404 | `NoHandlerFoundException` | 404 | 资源不存在 |
| 权限 | `AccessDeniedException` | 401 | Spring Security 抛出 |
| 兜底 | `Exception` | 500 | 未知异常，不暴露堆栈 |

### 2.4 Feign 接口设计 —— mall-api 模块

**Feign 接口定义：**

```java
@FeignClient(value = "mall-product-service", fallbackFactory = ProductFallbackFactory.class)
public interface ProductFeignClient {
    @GetMapping("/api/product/page")
    Result<PageResult<ProductDTO>> page(@SpringQueryMap ProductQueryDTO queryDTO);

    @GetMapping("/api/product/{id}")
    Result<ProductDTO> getById(@PathVariable("id") Long id);
}
```

**Fallback 工厂：**

```java
@Component
public class ProductFallbackFactory implements FallbackFactory<ProductFeignClient> {
    @Override
    public ProductFeignClient create(Throwable cause) {
        return new ProductFeignClient() {
            @Override
            public Result<PageResult<ProductDTO>> page(ProductQueryDTO queryDTO) {
                log.error("商品分页查询失败", cause);
                return Result.fail(ResultCodeEnum.REMOTE_CALL_FAIL);
            }
        };
    }
}
```

**请求头传递——Feign 拦截器：**

```java
@Component
public class HeaderInterceptor implements RequestInterceptor {
    @Override
    public void apply(RequestTemplate template) {
        // 从当前请求上下文获取请求头，传递给 Feign 调用
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            String userId = attributes.getRequest().getHeader("X-User-Id");
            if (userId != null) {
                template.header("X-User-Id", userId);
                template.header("X-Gateway-Secret", "mall-micro-8080");
            }
        }
    }
}
```

---

## 三、面试要点

### Q1: 统一响应体 Result<T> 的设计要点是什么？

**回答思路：** 三个字段：code（业务状态码）、msg（提示信息）、data（泛型数据）。静态工厂方法 `success(data)` 和 `fail(code, msg)` 保证创建方式统一。泛型 T 保证不同接口返回不同类型的数据，但包装结构一致。

### Q2: GlobalResponseAdvice 的 supports 方法里为什么排除 Result 类型和 OpenAPI 请求？

**回答思路：** 排除 Result 类型防止重复包装（如果 Controller 已经返回了 `Result<T>`，再包装一次就变成 `Result<Result<T>>`）。排除 OpenAPI 请求是因为 Swagger 接口文档的响应不需要统一包装，会影响文档生成。

### Q3: String 类型的响应体为什么需要特殊处理？

**回答思路：** Spring MVC 的 `StringHttpMessageConverter` 优先级高于 `MappingJackson2HttpMessageConverter`。如果 `ResponseBodyAdvice` 返回的 `Result<String>` 被 `StringHttpMessageConverter` 处理，会直接调用 `toString()` 输出字符串，而不是序列化为 JSON。所以 String 类型需要提前转成 JSON 字符串。

### Q4: Feign 的 fallbackFactory 和 fallback 有什么区别？

**回答思路：** fallback 只指定降级实现类，无法获取异常原因。fallbackFactory 传入 `Throwable cause`，可以在降级时记录失败原因，实现更精细的容错处理。项目中使用 fallbackFactory 来记录远程调用失败日志。

---

> **下一篇：** [03-PRODUCT-MYBATISPLUS.md —— 商品服务与 MyBatis-Plus：SPU/SKU 设计、分类体系、品牌管理](./03-PRODUCT-MYBATISPLUS.md)
>
> 深入电商核心——商品服务，看 SPU/SKU 的数据库设计、MyBatis-Plus 多表关联和分类体系。