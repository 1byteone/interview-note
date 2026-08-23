# 07 · 用户服务与 JWT 鉴权：登录、Token 刷新、双拦截器

> 电商系统的第一道门——用户认证。看 JWT 如何在网关层统一鉴权、在服务层无感传递用户信息、以及 Token 无感知续期机制。
>
> **对应项目：** `mall-services/mall-user-service` + `mall-gateway` + `mall-common`

---

## 一、基础概念

### 1.1 为什么不用 Session 而用 JWT？

| 对比项 | Session | JWT (JSON Web Token) |
|--------|---------|---------------------|
| 存储位置 | 服务端内存/Redis | 客户端 Token 字符串 |
| 扩展性 | 水平扩展需共享 Session | 天然无状态，不需要存储 |
| 跨域 | 受限于 Cookie 域 | 请求头携带，跨域无限制 |
| 安全 | 随机 Session ID，不可篡改 | 签名验证，可携带用户信息 |
| 主动失效 | 服务端删除 Session | 需维护黑名单 |

**JWT = 无状态认证。** 服务端不需要存储 Session，Token 本身包含了用户信息和签名，服务端只需验证签名即可。

### 1.2 JWT 结构

```
header.payload.signature

header:  {"alg": "HS256", "typ": "JWT"}       // 算法 + 类型
payload: {"sub": "1001", "username": "ffy",   // 用户信息
          "exp": 1712345678}                   // 过期时间
signature: HMACSHA256(base64(header) + "." + base64(payload), secret)
```

---

## 二、进阶机制

### 2.1 网关层：AuthGatewayFilterFactory 的 JWT 鉴权

```java
// 从请求头提取 Token
String token = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

// 1. 空校验
if (token == null || token.isEmpty()) {
    return writeErrorResponse(response, ResultCodeEnum.UNAUTHORIZED);
}

// 2. 签名 + 过期校验
jwtUtil.validateTokenWithRedis(token);  // 含 Redis 黑名单校验
if (!jwtUtil.validateToken(token)) {
    return writeErrorResponse(response, ResultCodeEnum.TOKEN_EXPIRED);
}

// 3. 解析 Token → 提取用户信息
Claims claims = jwtUtil.parseToken(token);
String userId = claims.getSubject();

// 4. 注入请求头传递给下游服务
ServerHttpRequest newRequest = request.mutate()
    .header("X-User-Id", userId)
    .header("X-Gateway-Secret", "mall-micro-8080")
    .build();

// 5. Token 无感知续期
String newToken = jwtUtil.createTokenAndStore(Long.parseLong(userId), claims);
return chain.filter(exchange.mutate().request(newRequest).build())
    .then(Mono.fromRunnable(() -> {
        response.getHeaders().set(HttpHeaders.AUTHORIZATION, newToken);
    }));
```

### 2.2 JwtUtil 工具类

```java
@Component
public class JwtUtil {
    private static final String SECRET = "your-256-bit-secret";
    private static final long EXPIRATION = 30 * 60 * 1000; // 30 分钟

    // 创建 Token
    public String createToken(Long userId, Map<String, Object> claims) {
        return Jwts.builder()
            .setClaims(claims)
            .setSubject(userId.toString())
            .setIssuedAt(new Date())
            .setExpiration(new Date(System.currentTimeMillis() + EXPIRATION))
            .signWith(SignatureAlgorithm.HS256, SECRET)
            .compact();
    }

    // 验证 Token（签名 + 过期）
    public boolean validateToken(String token) {
        try {
            Jwts.parser().setSigningKey(SECRET).parseClaimsJws(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // 解析 Token
    public Claims parseToken(String token) {
        return Jwts.parser().setSigningKey(SECRET).parseClaimsJws(token).getBody();
    }
}
```

### 2.3 服务层：LoginInterceptor 获取用户信息

```java
@Component
public class LoginInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request, ...) {
        // 从请求头获取网关传递的用户信息
        String userId = request.getHeader("X-User-Id");
        if (userId != null) {
            // 存入 ThreadLocal，供当前线程的业务代码使用
            UserContextHolder.setUserId(userId);
        }
        return true;
    }

    @Override
    public void afterCompletion(...) {
        // 请求结束清理 ThreadLocal，防止内存泄漏
        UserContextHolder.clear();
    }
}
```

### 2.4 服务层：AuthorizationInterceptor 网关来源校验

```java
@Component
public class AuthorizationInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request, ...) {
        String secret = request.getHeader("X-Gateway-Secret");
        if (secret == null || !secret.equals("mall-micro-8080")) {
            // 没有网关密钥 → 403 禁止访问
            response.setStatus(403);
            return false;
        }
        return true;
    }
}
```

---

## 三、面试要点

### Q1: JWT 的缺点是什么？Token 过期了怎么办？

**回答思路：** JWT 缺点：1) 无法主动失效（除非维护黑名单）；2) Token 体积较大，每次请求都携带；3) 过期时间不好平衡——太短体验差，太长不安全。项目解决方案：**无感知续期**——每次请求通过网关时，如果 Token 有效就自动生成新 Token 放在响应头，前端更新本地存储。这样用户持续操作就不会过期，长时间不操作则自动过期。

### Q2: 网关层解析 Token 后，下游服务怎么获取用户信息？

**回答思路：** 网关将 Token 解析得到的 userId 放入 `X-User-Id` 请求头，传递给下游服务。下游服务的 `LoginInterceptor` 从请求头取出 userId 存入 `ThreadLocal`（`UserContextHolder`），业务代码通过 `UserContextHolder.getUserId()` 获取。请求结束后清理 ThreadLocal。

---

> **下一篇：** [08-ES-SEARCH.md —— ES 搜索服务：Elasticsearch 商品搜索与分页](./08-ES-SEARCH.md)
>
> 电商搜索的另一种范式——ES 倒排索引。看传统关键词搜索的实现。