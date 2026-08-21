# Spring MVC 面试题大全

## 📚 知识体系

```
Spring MVC 核心
├── 前端控制器 DispatcherServlet
├── HandlerMapping（处理器映射）
├── HandlerAdapter（处理器适配器）
├── ViewResolver（视图解析器）
├── 拦截器 (Interceptor)
├── 异常处理 (@ControllerAdvice)
├── 数据绑定
├── 参数校验 (@Valid)
├── 消息转换 (HttpMessageConverter)
├── 文件上传 (MultipartFile)
└── RESTful 支持

Spring MVC 执行流程
├── 请求 → DispatcherServlet
├── HandlerMapping → 找到处理器
├── HandlerAdapter → 执行处理器
├── 返回 ModelAndView
├── ViewResolver → 解析视图
└── 渲染响应
```

---

## 🎯 Level 1：基础题

### 1. Spring MVC 的工作流程？
**答案**：

```text
① 客户端发送请求
    ↓
② DispatcherServlet（前端控制器）接收请求
    ↓
③ HandlerMapping（处理器映射器）
   → 根据请求 URL 找到对应的 Handler（Controller 方法）
    ↓
④ HandlerAdapter（处理器适配器）
   → 调用 Handler（执行 Controller 方法）
    ↓
⑤ Controller 执行完成，返回 ModelAndView
    ↓
⑥ ViewResolver（视图解析器）
   → 解析 View 名称 + 渲染 Model
    ↓
⑦ 返回响应给客户端
```

### 2. @RestController 和 @Controller 的区别？
**答案**：

| 注解 | 作用 | 返回 |
|------|------|------|
| `@Controller` | 声明控制器，配合 @ResponseBody | 视图页面（JSP/Thymeleaf） |
| `@RestController` | = @Controller + @ResponseBody | JSON/XML（REST API） |

**结论**：写 REST API 用 `@RestController`，页面渲染用 `@Controller`。

---

## 🎯 Level 2：进阶题

### 3. 拦截器 (Interceptor) 和过滤器 (Filter) 的区别？
**答案**：

| 特性 | Filter | Interceptor |
|------|--------|-------------|
| 规范 | Servlet 规范 | Spring 框架 |
| 触发时机 | 请求进入 Servlet 前 | 请求进入 Handler 前/后 |
| 作用范围 | 所有 URL | 匹配的 Handler |
| 依赖 | 依赖 Servlet 容器 | 依赖 Spring IOC |
| 方法 | doFilter | preHandle/postHandle/afterCompletion |
| 获取 Bean | 不能直接获取 | 可以注入 Spring Bean |

**执行顺序**：
```text
Filter → Interceptor.preHandle → Handler → Interceptor.postHandle → 视图渲染 → Interceptor.afterCompletion
```

### 4. 统一异常处理如何实现？
**答案**：

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ValidationException.class)
    public Result<Void> handleValidation(ValidationException e) {
        return Result.error(400, e.getMessage());
    }

    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusiness(BusinessException e) {
        return Result.error(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        log.error("系统异常", e);
        return Result.error(500, "系统繁忙，请稍后重试");
    }
}
```

---

## 🎯 Level 3：高级题

### 5. 数据绑定中如何处理日期格式？
**答案**：

**方式一：全局配置**
```java
@Configuration
public class WebConfig implements WebMvcConfigurer {
    @Override
    public void addFormatters(FormatterRegistry registry) {
        DateTimeFormatterRegistrar registrar = new DateTimeFormatterRegistrar();
        registrar.setDateFormatter(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        registrar.setDateTimeFormatter(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        registrar.registerFormatters(registry);
    }
}
```

**方式二：注解指定**
```java
@RequestMapping("/user")
public User getUser(@DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate birthDate) {
    return userService.findByBirthDate(birthDate);
}
```

### 6. 异步请求如何处理？
**答案**：

```java
@RestController
public class AsyncController {

    @GetMapping("/async")
    public Callable<String> handleAsync() {
        return () -> {
            // 在单独线程执行
            Thread.sleep(5000);
            return "完成";
        };
    }

    @GetMapping("/deferred")
    public DeferredResult<String> handleDeferred() {
        DeferredResult<String> result = new DeferredResult<>(10000L);
        // 其他线程设置 result.setResult(value)
        return result;
    }
}
```

---

## 📖 学习资源

### 推荐项目
- [Spring MVC 官方文档](https://docs.spring.io/spring-framework/reference/web.html)

### 最佳实践
1. REST API 统一用 @RestController
2. 参数校验用 @Valid + 全局异常处理
3. 拦截器做鉴权/日志，过滤器做编码/跨域
4. 文件上传配置 MultipartFile 大小限制