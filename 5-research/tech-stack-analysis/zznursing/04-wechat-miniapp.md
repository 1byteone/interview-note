# 微信小程序开发

> zznursing 项目微信小程序面向老人家属和护工，提供健康监测、智能问答、告警通知等核心功能。

---

## 一、微信小程序架构

### 1.1 整体架构

```
┌──────────────────────────────────────────────────────────────────┐
│                       微信小程序客户端                             │
│                                                                  │
│  ┌───────────────────────────────────────────────────────────┐   │
│  │                     页面层 (Pages)                         │   │
│  │  ┌───────┐ ┌───────┐ ┌───────┐ ┌───────┐ ┌──────────┐   │   │
│  │  │首页    │ │健康    │ │告警    │ │我的    │ │AI 问答   │   │   │
│  │  │仪表盘  │ │监测    │ │中心    │ │(个人)  │ │(智能客服)│   │   │
│  │  └───────┘ └───────┘ └───────┘ └───────┘ └──────────┘   │   │
│  └───────────────────────────────────────────────────────────┘   │
│                              │                                    │
│  ┌───────────────────────────────────────────────────────────┐   │
│  │                     服务层 (Services)                      │   │
│  │  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌────────────┐   │   │
│  │  │API 请求  │ │WebSocket │ │登录鉴权  │ │消息推送    │   │   │
│  │  │封装      │ │连接管理  │ │模块      │ │订阅管理    │   │   │
│  │  └──────────┘ └──────────┘ └──────────┘ └────────────┘   │   │
│  └───────────────────────────────────────────────────────────┘   │
│                              │                                    │
│  ┌───────────────────────────────────────────────────────────┐   │
│  │                     工具层 (Utils)                         │   │
│  │  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌────────────┐   │   │
│  │  │日期格式化│ │数据图表  │ │缓存管理  │ │健康数据    │   │   │
│  │  │工具      │ │渲染      │ │(Storage) │ │工具类      │   │   │
│  │  └──────────┘ └──────────┘ └──────────┘ └────────────┘   │   │
│  └───────────────────────────────────────────────────────────┘   │
└──────────────────────────────┬───────────────────────────────────┘
                               │ HTTPS / WebSocket
                               ▼
┌──────────────────────────────────────────────────────────────────┐
│                      Spring Boot 后端                             │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌────────────┐         │
│  │用户认证  │ │健康数据  │ │告警通知  │ │AI 智能     │         │
│  │接口      │ │接口      │ │接口      │ │问答接口    │         │
│  └──────────┘ └──────────┘ └──────────┘ └────────────┘         │
└──────────────────────────────────────────────────────────────────┘
```

### 1.2 小程序配置文件

```json
// app.json —— 小程序全局配置
{
  "pages": [
    "pages/index/index",
    "pages/health/monitor",
    "pages/health/detail",
    "pages/alert/list",
    "pages/ai/chat",
    "pages/profile/index",
    "pages/elderly/bind",
    "pages/elderly/archive"
  ],
  "window": {
    "navigationBarBackgroundColor": "#4A90D9",
    "navigationBarTitleText": "智能养老",
    "navigationBarTextStyle": "white",
    "backgroundColor": "#F5F7FA"
  },
  "tabBar": {
    "color": "#999999",
    "selectedColor": "#4A90D9",
    "list": [
      {
        "pagePath": "pages/index/index",
        "text": "首页",
        "iconPath": "images/tab/home.png",
        "selectedIconPath": "images/tab/home-active.png"
      },
      {
        "pagePath": "pages/health/monitor",
        "text": "健康",
        "iconPath": "images/tab/health.png",
        "selectedIconPath": "images/tab/health-active.png"
      },
      {
        "pagePath": "pages/alert/list",
        "text": "告警",
        "iconPath": "images/tab/alert.png",
        "selectedIconPath": "images/tab/alert-active.png"
      },
      {
        "pagePath": "pages/ai/chat",
        "text": "AI助手",
        "iconPath": "images/tab/ai.png",
        "selectedIconPath": "images/tab/ai-active.png"
      },
      {
        "pagePath": "pages/profile/index",
        "text": "我的",
        "iconPath": "images/tab/profile.png",
        "selectedIconPath": "images/tab/profile-active.png"
      }
    ]
  },
  "permission": {
    "scope.userLocation": {
      "desc": "您的位置信息将用于老人定位服务"
    }
  },
  "requiredBackgroundModes": ["audio"],
  "sitemapLocation": "sitemap.json"
}
```

---

## 二、后端接口设计

### 2.1 微信登录

```java
// WeChatAuthController.java
// 微信登录控制器 —— 处理小程序登录、手机号获取、用户信息绑定
package com.zznursing.wechat.controller;

import com.zznursing.wechat.dto.WeChatLoginRequest;
import com.zznursing.wechat.dto.WeChatLoginResponse;
import com.zznursing.wechat.service.WeChatAuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 微信登录控制器
 * 处理小程序登录流程：code 换 session_key → 自定义登录态 → 返回 token
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/wechat")
@RequiredArgsConstructor
public class WeChatAuthController {

    private final WeChatAuthService weChatAuthService;

    /**
     * 微信小程序登录
     * 流程：前端调用 wx.login() 获取 code → 后端用 code 换取 session_key
     * → 生成自定义 token → 返回给前端
     *
     * @param request 登录请求，包含 code 和用户信息
     * @return 登录响应，包含 token 和用户信息
     */
    @PostMapping("/login")
    public WeChatLoginResponse login(@Valid @RequestBody WeChatLoginRequest request) {
        log.info("微信登录请求 - code: {}", request.getCode());
        return weChatAuthService.login(request.getCode());
    }

    /**
     * 获取手机号
     * 使用微信加密数据进行解密，获取用户手机号
     */
    @PostMapping("/phone")
    public WeChatLoginResponse getPhoneNumber(@RequestParam String encryptedData,
                                              @RequestParam String iv,
                                              @RequestHeader("Authorization") String token) {
        return weChatAuthService.getPhoneNumber(token, encryptedData, iv);
    }
}
```

### 2.2 登录请求/响应 DTO

```java
// WeChatLoginRequest.java
package com.zznursing.wechat.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 微信登录请求体
 */
@Data
public class WeChatLoginRequest {

    /** 微信登录临时 code，由 wx.login() 获取，有效期 5 分钟 */
    @NotBlank(message = "登录 code 不能为空")
    private String code;

    /** 用户昵称（可选） */
    private String nickName;

    /** 用户头像 URL（可选） */
    private String avatarUrl;
}
```

```java
// WeChatLoginResponse.java
package com.zznursing.wechat.dto;

import lombok.Data;

/**
 * 微信登录响应体
 */
@Data
public class WeChatLoginResponse {

    /** 自定义登录态 Token，用于后续接口鉴权 */
    private String token;

    /** Token 过期时间（秒） */
    private Long expiresIn;

    /** 用户唯一标识（系统内 openId） */
    private String userId;

    /** 是否新用户 */
    private Boolean isNewUser;

    /** 绑定的老人信息（如有） */
    private ElderlyBriefInfo elderlyInfo;
}
```

### 2.3 微信登录服务

```java
// WeChatAuthService.java
// 微信登录服务 —— 核心登录逻辑：code 换 session、生成 token
package com.zznursing.wechat.service;

import com.zznursing.wechat.dto.WeChatLoginResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 微信登录服务
 * 实现微信小程序登录流程：
 * 1. 前端 wx.login() 获取临时 code
 * 2. 后端用 code 向微信服务器换取 session_key 和 openId
 * 3. 生成自定义 token 并缓存到 Redis
 * 4. 返回 token 给前端，后续请求携带 token 鉴权
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WeChatAuthService {

    private final RestTemplate restTemplate;
    private final StringRedisTemplate stringRedisTemplate;

    @Value("${wechat.appid}")
    private String appId;

    @Value("${wechat.secret}")
    private String appSecret;

    /** Token 在 Redis 中的 Key 前缀 */
    private static final String TOKEN_PREFIX = "wechat:token:";

    /** Token 过期时间（7 天） */
    private static final long TOKEN_EXPIRE_SECONDS = 7 * 24 * 3600;

    /**
     * 微信登录核心逻辑
     *
     * @param code 微信临时 code
     * @return 登录响应
     */
    public WeChatLoginResponse login(String code) {
        // 步骤1：用 code 向微信服务器换取 session_key 和 openId
        Map<String, String> sessionInfo = getSessionKeyAndOpenId(code);

        String openId = sessionInfo.get("openid");
        String sessionKey = sessionInfo.get("session_key");

        log.info("微信登录成功 - openId: {}", openId);

        // 步骤2：查找或创建用户
        // 实际项目中：查询数据库，如果 openId 不存在则创建新用户
        String userId = findOrCreateUser(openId);

        // 步骤3：生成自定义 Token
        String token = UUID.randomUUID().toString().replace("-", "");

        // 步骤4：将 Token 和用户信息缓存到 Redis
        // 存储格式：token -> {userId, openId, sessionKey}
        String tokenValue = String.format("{\"userId\":\"%s\",\"openId\":\"%s\",\"sessionKey\":\"%s\"}",
                userId, openId, sessionKey);
        stringRedisTemplate.opsForValue().set(
                TOKEN_PREFIX + token, tokenValue, TOKEN_EXPIRE_SECONDS, TimeUnit.SECONDS);

        // 步骤5：构建响应
        WeChatLoginResponse response = new WeChatLoginResponse();
        response.setToken(token);
        response.setExpiresIn(TOKEN_EXPIRE_SECONDS);
        response.setUserId(userId);
        response.setIsNewUser(false);

        log.info("Token 已生成 - userId: {}, token: {}...", userId, token.substring(0, 8));
        return response;
    }

    /**
     * 用 code 换取 session_key 和 openId
     * 调用微信官方接口：https://api.weixin.qq.com/sns/jscode2session
     */
    private Map<String, String> getSessionKeyAndOpenId(String code) {
        // 微信接口 URL
        String url = String.format(
                "https://api.weixin.qq.com/sns/jscode2session?appid=%s&secret=%s&js_code=%s&grant_type=authorization_code",
                appId, appSecret, code
        );

        try {
            // 调用微信接口
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            Map<String, Object> body = response.getBody();

            if (body == null || body.containsKey("errcode")) {
                Object errcode = body != null ? body.get("errcode") : "unknown";
                Object errmsg = body != null ? body.get("errmsg") : "unknown";
                log.error("微信登录失败 - errcode: {}, errmsg: {}", errcode, errmsg);
                throw new RuntimeException("微信登录失败: " + errmsg);
            }

            // 提取 openId 和 session_key
            String openId = (String) body.get("openid");
            String sessionKey = (String) body.get("session_key");

            if (openId == null || sessionKey == null) {
                throw new RuntimeException("微信登录返回数据异常");
            }

            return Map.of("openid", openId, "session_key", sessionKey);

        } catch (Exception e) {
            log.error("调用微信 jscode2session 接口异常", e);
            throw new RuntimeException("微信登录服务异常", e);
        }
    }

    /**
     * 查找或创建用户
     * 实际项目中查询数据库，这里简化处理
     */
    private String findOrCreateUser(String openId) {
        // 实际项目：userRepository.findByOpenId(openId)
        // 如果不存在则创建新用户
        return openId; // 简化：用 openId 作为 userId
    }

    /**
     * 获取手机号（通过微信加密数据解密）
     */
    public WeChatLoginResponse getPhoneNumber(String token, String encryptedData, String iv) {
        // 1. 从 Redis 获取 session_key
        String tokenValue = stringRedisTemplate.opsForValue().get(TOKEN_PREFIX + token);
        // 2. 使用 AES 解密 encryptedData
        // 3. 获取手机号并保存到数据库
        // 4. 返回更新后的用户信息

        log.info("获取手机号 - token: {}", token);
        // 返回简化结果
        WeChatLoginResponse response = new WeChatLoginResponse();
        response.setToken(token);
        return response;
    }
}
```

---

## 三、接口鉴权

### 3.1 Token 拦截器

```java
// WeChatAuthInterceptor.java
// 微信 Token 鉴权拦截器 —— 验证请求头中的 Token 有效性
package com.zznursing.wechat.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 微信 Token 鉴权拦截器
 * 验证每个请求的 Authorization 头中的 Token 是否有效
 * Token 存储在 Redis 中，过期自动失效
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WeChatAuthInterceptor implements HandlerInterceptor {

    private final StringRedisTemplate stringRedisTemplate;

    /** Token 在 Redis 中的 Key 前缀 */
    private static final String TOKEN_PREFIX = "wechat:token:";

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {

        // 从请求头中获取 Token
        String authHeader = request.getHeader("Authorization");

        // 校验 Token 是否存在
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            response.setStatus(401);
            response.getWriter().write("{\"code\":401,\"message\":\"未登录或登录已过期\"}");
            return false;
        }

        // 提取 Token 值
        String token = authHeader.substring(7);

        // 验证 Token 是否在 Redis 中
        String tokenValue = stringRedisTemplate.opsForValue().get(TOKEN_PREFIX + token);
        if (tokenValue == null) {
            response.setStatus(401);
            response.getWriter().write("{\"code\":401,\"message\":\"Token 已过期，请重新登录\"}");
            return false;
        }

        // Token 有效，将用户信息存入请求属性，供后续处理使用
        // 实际项目应解析 tokenValue JSON 获取 userId
        request.setAttribute("userId", "extracted_user_id");

        // 每次请求刷新 Token 过期时间（续期）
        stringRedisTemplate.expire(TOKEN_PREFIX + token,
                java.time.Duration.ofSeconds(7 * 24 * 3600));

        return true;
    }
}
```

### 3.2 拦截器注册

```java
// WebMvcConfig.java
// Web MVC 配置 —— 注册拦截器，配置放行路径
package com.zznursing.wechat.config;

import com.zznursing.wechat.interceptor.WeChatAuthInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置
 * 注册微信 Token 鉴权拦截器，配置哪些路径不需要鉴权
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final WeChatAuthInterceptor weChatAuthInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 注册拦截器，拦截所有 /api/v1/ 下的请求
        registry.addInterceptor(weChatAuthInterceptor)
                .addPathPatterns("/api/v1/**")
                // 放行登录接口和 IoTDA 回调接口
                .excludePathPatterns(
                        "/api/v1/wechat/login",      // 微信登录
                        "/api/v1/wechat/phone",       // 获取手机号
                        "/api/v1/iotda/callback",     // IoTDA 数据回调
                        "/api/v1/iotda/status-callback" // IoTDA 状态回调
                );
    }
}
```

---

## 四、消息推送

### 4.1 微信订阅消息推送

```java
// WeChatMessageService.java
// 微信消息推送服务 —— 通过微信订阅消息向用户推送告警通知
package com.zznursing.wechat.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * 微信消息推送服务
 * 使用微信小程序订阅消息能力，向用户推送告警通知
 * 支持：心率异常告警、跌倒检测告警、设备离线告警等
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WeChatMessageService {

    private final RestTemplate restTemplate;

    @Value("${wechat.appid}")
    private String appId;

    @Value("${wechat.secret}")
    private String appSecret;

    /** 微信订阅消息 API */
    private static final String SUBSCRIBE_MSG_URL =
            "https://api.weixin.qq.com/cgi-bin/message/subscribe/send?access_token={}";

    /**
     * 发送心率异常告警通知
     *
     * @param openId 用户微信 openId
     * @param elderlyName 老人姓名
     * @param heartRate 心率值
     * @param time 告警时间
     */
    public void sendHeartRateAlert(String openId, String elderlyName,
                                   int heartRate, String time) {
        // 获取微信接口调用凭证
        String accessToken = getAccessToken();

        // 构建消息体
        Map<String, Object> message = new HashMap<>();
        message.put("touser", openId);                          // 接收者 openId
        message.put("template_id", "HEART_RATE_ALERT_TEMPLATE_ID"); // 模板ID
        message.put("page", "pages/alert/list");                // 点击跳转页面

        // 模板数据
        Map<String, Object> data = new HashMap<>();
        data.put("thing1", Map.of("value", elderlyName + "的心率异常"));
        data.put("number2", Map.of("value", String.valueOf(heartRate)));
        data.put("thing3", Map.of("value", "请及时查看并联系护工"));
        data.put("time4", Map.of("value", time));
        message.put("data", data);

        try {
            // 发送请求
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(message, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(
                    SUBSCRIBE_MSG_URL, request, Map.class, accessToken);

            Map<String, Object> responseBody = response.getBody();
            if (responseBody != null && Integer.valueOf(0).equals(responseBody.get("errcode"))) {
                log.info("心率告警推送成功 - openId: {}", openId);
            } else {
                log.warn("心率告警推送失败 - errcode: {}, errmsg: {}",
                        responseBody.get("errcode"), responseBody.get("errmsg"));
            }

        } catch (Exception e) {
            log.error("发送微信订阅消息异常", e);
        }
    }

    /**
     * 发送跌倒检测告警通知（高优先级）
     */
    public void sendFallAlert(String openId, String elderlyName, String location) {
        String accessToken = getAccessToken();

        Map<String, Object> message = new HashMap<>();
        message.put("touser", openId);
        message.put("template_id", "FALL_ALERT_TEMPLATE_ID");
        message.put("page", "pages/alert/list");

        Map<String, Object> data = new HashMap<>();
        data.put("thing1", Map.of("value", "紧急：" + elderlyName + "疑似跌倒"));
        data.put("thing2", Map.of("value", location));
        data.put("thing3", Map.of("value", "请立即联系护工确认"));
        data.put("time4", Map.of("value", "请立即处理"));
        message.put("data", data);

        // 发送请求（简化）
        log.warn("跌倒告警推送 - openId: {}, elderlyName: {}", openId, elderlyName);
    }

    /**
     * 获取微信接口调用凭证（access_token）
     * 与用户登录的 session_key 不同，这是接口调用凭证
     */
    private String getAccessToken() {
        String url = String.format(
                "https://api.weixin.qq.com/cgi-bin/token?grant_type=client_credential&appid=%s&secret=%s",
                appId, appSecret
        );

        try {
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            Map<String, Object> body = response.getBody();
            if (body != null && body.containsKey("access_token")) {
                return (String) body.get("access_token");
            }
            throw new RuntimeException("获取 access_token 失败: " + body);
        } catch (Exception e) {
            log.error("获取微信 access_token 异常", e);
            throw new RuntimeException("获取微信凭证失败", e);
        }
    }
}
```

---

## 五、面试题

### 问题 1：微信小程序架构特点

**架构特点：**

1. **双线程模型**：渲染层（WebView）和逻辑层（JsCore）分离，渲染不阻塞逻辑执行
2. **数据驱动**：通过 `setData` 驱动视图更新，避免直接操作 DOM
3. **原生组件**：部分组件（如 canvas、video）使用原生组件渲染，性能优于 H5
4. **离线能力**：通过 Storage 和 Service Worker 提供有限离线能力
5. **安全沙箱**：小程序运行在沙箱环境中，无法访问系统 API，安全性高

### 问题 2：微信登录流程详解

**完整登录流程：**

1. 前端调用 `wx.login()` 获取临时 code（有效期 5 分钟）
2. 前端将 code 发送到后端
3. 后端用 code 调用微信 `jscode2session` 接口，换取 `openId` 和 `session_key`
4. 后端生成自定义 token，将 `{token, userId, openId, sessionKey}` 存入 Redis
5. 后端返回 token 给前端
6. 前端将 token 存入 Storage，后续请求携带在 Authorization 头中
7. 后端拦截器验证 Token 有效性

### 问题 3：小程序性能优化策略

**优化策略：**

1. **分包加载**：将首页、健康监测等核心页面放在主包，AI 问答、历史记录等放在分包，减少首包体积
2. **数据预加载**：在页面跳转前通过 `preload` 预拉取数据，减少页面加载等待时间
3. **setData 优化**：合并多次 setData 为一次，避免频繁更新；只更新变化的数据路径
4. **图片优化**：使用 WebP 格式、CDN 加速、懒加载、预加载关键图片
5. **缓存策略**：健康数据缓存到 Storage，离线时展示缓存数据；网络恢复后增量更新
6. **WebSocket 长连接**：AI 问答使用 WebSocket 长连接接收流式回复，避免频繁 HTTP 请求