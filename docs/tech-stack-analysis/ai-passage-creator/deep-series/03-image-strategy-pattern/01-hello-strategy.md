# 03 策略模式入门：从配图系统理解策略模式

> 本文是 ai-passage-creator 项目技术栈深度剖析系列的第 3 篇（入门篇）。面向 Java 初学者，手把手带你从零搭建一个策略模式示例项目，理解"如何用策略模式优雅地处理多种算法"。
>
> **对应项目：** `ai-passage-creator/ai-passage-creator-java` 模块 `image` 包
> **难度等级：** Level 1 入门
> **预计阅读时间：** 20 分钟（含代码实操）

---

## 一、项目背景

### 1.1 什么是策略模式

策略模式（Strategy Pattern）是一种**行为型设计模式**，它定义了一系列算法，将每个算法封装起来，使它们可以相互替换。策略模式让算法的变化独立于使用算法的客户端。

用大白话说：**做同一件事有多种方式，策略模式让你可以自由切换这些方式，而不需要改调用方的代码。**

在 ai-passage-creator 项目中，配图系统需要从多个来源获取图片：

- 从 Pexels 免费图库搜索照片
- 生成 Mermaid 图表作为配图
- 调用 AI 生图 API 生成定制图片
- 用 Picsum 随机图片作为兜底

每种方式获取图片的代码完全不同——有的调 REST API，有的生成图表代码再渲染，有的调 AI 接口。但如果把这些代码直接写在业务逻辑里，代码会变得混乱且难以维护。

策略模式完美解决了这个问题：**定义一个统一的接口，每种配图方式实现这个接口，业务代码只面向接口编程。**

### 1.2 为什么选择策略模式而不是 if-else

初学者最容易想到的方案是用 if-else 或 switch：

```java
// 坏味道：if-else 满天飞
public String getImage(String prompt, String method) {
    if ("pexels".equals(method)) {
        // 调用 Pexels API ... 50 行代码
    } else if ("mermaid".equals(method)) {
        // 生成 Mermaid 图表 ... 50 行代码
    } else if ("ai".equals(method)) {
        // 调用 AI 生图 ... 50 行代码
    } else {
        // Picsum 兜底 ... 10 行代码
    }
}
```

这种写法的四大问题：

| 问题 | 说明 | 后果 |
|------|------|------|
| **难以扩展** | 新增配图方式要改现有方法 | 违反开闭原则，容易改出 bug |
| **代码臃肿** | 所有逻辑挤在一个方法里 | 方法几百行，难以阅读和测试 |
| **重复代码** | 每个分支都有相似的错误处理逻辑 | 修改错误处理方式要改 4 处 |
| **难以测试** | 一个方法测试所有分支 | 测试用例复杂，覆盖率低 |

策略模式将这 200 行 if-else 拆分为：

- 1 个接口（5 行）
- 4 个实现类（每个 50 行）
- 1 个上下文（80 行）

总共 285 行，看似更多了，但每个类职责单一，可独立测试、独立修改、独立扩展。

### 1.3 本文的目标

读完本文，你将能够：
- 理解策略模式的三要素（接口、实现、上下文）
- 使用策略模式重构 if-else 代码
- 实现带降级链路的策略调用
- 编写策略模式的单元测试
- 理解 ai-passage-creator 项目中的策略模式应用

---

## 二、核心概念

### 2.1 策略模式三要素

策略模式由三个核心角色组成：

**角色一：策略接口（Strategy）**

定义所有策略必须实现的方法，是策略模式的"契约"。

```java
// 策略接口 —— 所有配图方式都必须实现这个接口
public interface ImageStrategy {
    // 根据提示词配图，返回图片 URL
    String generateImage(String prompt);
    // 获取策略名称
    String getName();
    // 检查策略是否可用（如 API Key 是否配置）
    boolean isAvailable();
}
```

**角色二：具体策略（ConcreteStrategy）**

实现策略接口的具体算法类。每个类只负责一种配图方式。

| 具体策略 | 配图方式 | 是否依赖外部服务 |
|----------|---------|----------------|
| PexelsImageStrategy | Pexels 免费图库搜索 | 依赖 Pexels API + API Key |
| MermaidImageStrategy | Mermaid 图表渲染 | 依赖 mermaid.ink 渲染服务 |
| AiImageStrategy | AI 生图（VIP 专属） | 依赖 OpenAI API + VIP 权限 |
| PicsumFallback | 随机图片兜底 | 无依赖，100% 可用 |

**角色三：上下文（Context）**

持有策略引用，负责调用策略。上下文可以维护一个策略列表，实现降级链。

```java
// 上下文 —— 持有策略列表，负责调用和降级
public class ImageContext {
    private final List<ImageStrategy> strategies;  // 策略列表
    private int primaryStrategyIndex;              // 当前主策略索引

    // 获取图片（带自动降级）
    public String getImage(String prompt) {
        // 1. 尝试主策略
        // 2. 主策略失败，尝试备用策略
        // 3. 全部失败，使用兜底方案
    }
}
```

### 2.2 三段式降级链路

降级链路是策略模式在配图场景中的核心应用。三段式设计确保文章生成不因配图失败而中断：

```
┌─────────────────────────────────────────────┐
│ 第一级：主策略优先尝试                        │
│ → 可用 & 成功 → 返回结果                      │
│ → 不可用或失败 → 进入第二级                   │
├─────────────────────────────────────────────┤
│ 第二级：遍历备用策略                          │
│ → 按优先级依次尝试                            │
│ → 某个策略成功 → 返回结果                     │
│ → 全部失败 → 进入第三级                       │
├─────────────────────────────────────────────┤
│ 第三级：Picsum 兜底                          │
│ → 返回 Picsum 随机图片 URL（100% 可用）        │
└─────────────────────────────────────────────┘
```

**降级触发条件：**

| 条件 | 说明 | 处理方式 |
|------|------|----------|
| API Key 未配置 | isAvailable() 返回 false | 跳过该策略 |
| 网络超时/异常 | HTTP 请求失败 | 捕获异常，进入下一级 |
| 搜索结果为空 | 未找到匹配图片 | 空结果判断，进入下一级 |
| 权限不足 | 非 VIP 用户使用 VIP 策略 | isAvailable() 返回 false |

### 2.3 策略模式 vs 其他设计模式

| 对比维度 | 策略模式 | 工厂模式 | 模板方法模式 |
|----------|---------|----------|-------------|
| 核心目的 | 算法相互替换 | 创建对象 | 定义算法骨架 |
| 变化点 | 算法实现 | 创建逻辑 | 步骤实现 |
| 调用方式 | 运行时切换 | 编译时确定 | 继承重写 |
| 项目中的使用 | 配图策略 | ImageStrategyFactory | 暂未使用 |

---

## 三、从零搭建代码

### 3.1 创建项目结构

我们先创建一个全新的 Maven 项目，目录结构如下：

```
strategy-demo/
├── pom.xml
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/
│   │   │       └── passage/
│   │   │           └── strategy/
│   │   │               ├── StrategyDemoApplication.java    # Spring Boot 启动类
│   │   │               ├── ImageStrategy.java              # 策略接口
│   │   │               ├── ImageAcquisitionException.java  # 自定义异常
│   │   │               ├── PexelsImageStrategy.java        # Pexels 策略
│   │   │               ├── MermaidImageStrategy.java       # Mermaid 策略
│   │   │               ├── ImageContext.java               # 上下文（核心编排）
│   │   │               ├── ImageStrategyFactory.java       # 工厂类
│   │   │               └── ImageController.java            # REST 控制器
│   │   └── resources/
│   │       └── application.yml                             # 配置文件
│   └── test/
│       └── java/
│           └── com/
│               └── passage/
│                   └── strategy/
│                       └── ImageStrategyTest.java          # 单元测试
```

### 3.2 配置 Maven 依赖（pom.xml）

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!-- pom.xml —— Maven 项目配置文件 -->
<!-- 策略模式配图示例的 Maven 构建配置 -->
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <!-- 父项目：Spring Boot 3.2.x -->
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.2.5</version>
        <relativePath/>
    </parent>

    <!-- 项目坐标信息 -->
    <groupId>com.passage</groupId>
    <artifactId>strategy-demo</artifactId>       <!-- 项目名：strategy-demo -->
    <version>1.0.0-SNAPSHOT</version>
    <name>Strategy Pattern Demo</name>
    <description>策略模式入门示例：配图系统中的多策略与降级链路</description>

    <!-- 版本属性 -->
    <properties>
        <java.version>17</java.version>          <!-- 使用 Java 17 -->
    </properties>

    <dependencies>
        <!-- Spring Boot Web 起步依赖 -->
        <!-- 提供 RestTemplate、Tomcat 等 Web 能力 -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>

        <!-- Spring Boot Test 测试框架 -->
        <!-- 提供 JUnit 5、Mockito 等测试能力 -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>                  <!-- 仅测试时使用 -->
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <!-- Spring Boot Maven 打包插件 -->
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

**依赖说明：**

| 依赖 | 作用 | 关键点 |
|------|------|--------|
| spring-boot-starter-web | 提供 Web 能力（RestTemplate） | 策略模式本身不依赖 Web，但我们需要 RestTemplate 调 API |
| spring-boot-starter-test | 单元测试 | JUnit 5 + Mockito |

### 3.3 配置文件（application.yml）

```yaml
# application.yml —— 应用配置文件
# 策略模式的配图参数配置
server:
  port: 8080                               # 服务端口号

spring:
  application:
    name: strategy-demo                     # 应用名称

# 配图策略配置
image:
  strategy:
    # 默认主策略名称（对应 ImageStrategy.getName() 返回值）
    primary: pexels
    # 策略优先级列表（越靠前优先级越高）
    priority:
      - pexels
      - mermaid
      - ai-image

  # Pexels 图库配置
  pexels:
    # Pexels API Key（从环境变量获取，避免硬编码）
    api-key: ${PEXELS_API_KEY:}
    # 每次搜索返回的图片数量
    per-page: 5

  # AI 生图配置
  ai:
    # AI 生图 API Key（从环境变量获取）
    api-key: ${AI_API_KEY:}
    # 生成的图片尺寸
    size: 1024x1024
```

**配置说明：**

- `image.strategy.primary`：指定默认主策略名称
- `image.strategy.priority`：策略优先级列表，按优先级从高到低排列
- `image.pexels.api-key`：从环境变量读取 Pexels API Key，留空时策略不可用
- `image.ai.api-key`：AI 生图 API Key，同样从环境变量读取

### 3.4 策略接口（ImageStrategy.java）

```java
package com.passage.strategy;

/**
 * ImageStrategy - 图片获取策略接口
 * <p>
 * 策略模式的核心接口，定义了所有图片获取策略必须实现的方法。
 * 每种策略代表一种图片来源方式。
 * <p>
 * 策略模式的核心思想：定义算法家族，分别封装，使它们可以相互替换。
 * 本接口就是"算法家族"的统一契约。
 *
 * @author AI-Passage-Creator
 */
public interface ImageStrategy {

    /**
     * 根据提示词生成/获取图片 URL
     * <p>
     * 核心业务方法，每个策略以自己的方式实现：
     * - PexelsImageStrategy：调用 Pexels REST API 搜索图片
     * - MermaidImageStrategy：生成 Mermaid 图表并渲染为图片
     * - AiImageStrategy：调用 AI 图片生成 API 生图
     *
     * @param prompt 图片描述提示词（如"春日樱花树下读书的少女"）
     * @return 图片的 URL 字符串
     * @throws ImageAcquisitionException 当图片获取失败时抛出
     */
    String generateImage(String prompt);

    /**
     * 获取策略名称
     * <p>
     * 用于日志记录、监控和调试，方便追踪当前使用的是哪种策略。
     * 命名规则：小写英文字母，单词间用连字符分隔。
     * 示例："pexels"、"mermaid"、"ai-image"
     *
     * @return 策略名称
     */
    String getName();

    /**
     * 检查当前策略是否可用
     * <p>
     * 在调用 generateImage 之前由 ImageContext 调用此方法进行前置检查。
     * 检查条件包括：
     * - API Key 是否已配置
     * - 是否为 VIP 用户（AI 生图策略需要）
     *
     * @return true 表示策略可用，false 表示不可用
     */
    boolean isAvailable();
}
```

**接口设计要点：**

- `generateImage`：核心业务方法，每个策略独立实现
- `getName`：返回策略唯一标识，用于日志和动态切换
- `isAvailable`：前置检查方法，避免不可用策略的无效调用

### 3.5 自定义异常（ImageAcquisitionException.java）

```java
package com.passage.strategy;

/**
 * ImageAcquisitionException - 图片获取异常
 * <p>
 * 图片获取过程中可能出现的异常封装。
 * 与 ImageContext 的降级机制配合使用：
 * - 各策略在 generateImage() 中抛出此异常
 * - ImageContext 捕获此异常后触发降级
 * - 最终兜底（Picsum）不抛异常，保证 100% 返回
 *
 * @author AI-Passage-Creator
 */
public class ImageAcquisitionException extends RuntimeException {

    /**
     * 构造方法 —— 仅传入异常描述
     *
     * @param message 异常描述信息
     */
    public ImageAcquisitionException(String message) {
        super(message);
    }

    /**
     * 构造方法 —— 带原始异常（用于异常链）
     *
     * @param message 异常描述信息
     * @param cause   原始异常（如网络超时、IO 异常等）
     */
    public ImageAcquisitionException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

### 3.6 Pexels 策略实现（PexelsImageStrategy.java）

```java
package com.passage.strategy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * PexelsImageStrategy - Pexels 免费图库图片搜索策略
 * <p>
 * 策略模式的具体策略实现之一。
 * 调用 Pexels 开放 API 搜索与提示词匹配的免费图片。
 * <p>
 * 策略说明：
 * - 优先级：首选策略（免费、无需 AI 算力）
 * - 适用场景：需要实景照片、自然风光、人物等真实感图片
 * - 局限性：无法生成特定构图或概念性图片，不适合图表/示意图
 * <p>
 * 降级处理：
 * - API Key 未配置时，isAvailable() 返回 false，触发自动降级
 * - API 调用失败时，抛出 ImageAcquisitionException 由上层处理
 *
 * @author AI-Passage-Creator
 */
public class PexelsImageStrategy implements ImageStrategy {

    /** 日志记录器 */
    private static final Logger log = LoggerFactory.getLogger(PexelsImageStrategy.class);

    /** Pexels API 基础地址 */
    private static final String PEXELS_API_BASE = "https://api.pexels.com/v1";

    /** 搜索接口路径 */
    private static final String SEARCH_ENDPOINT = "/search";

    /** 每次搜索返回的图片数量 */
    private static final int PER_PAGE = 5;

    /** Pexels API Key（从配置文件或环境变量注入） */
    private final String apiKey;

    /** Spring RestTemplate 用于发送 HTTP 请求 */
    private final RestTemplate restTemplate;

    /**
     * 构造方法
     *
     * @param apiKey Pexels API Key，从 application.yml 或环境变量注入
     */
    public PexelsImageStrategy(String apiKey) {
        this.apiKey = apiKey;
        this.restTemplate = new RestTemplate();
        log.info("PexelsImageStrategy 初始化完成");
    }

    /**
     * 从 Pexels 搜索与提示词匹配的图片
     * <p>
     * 实现步骤：
     * 1. 构建查询参数，调用 Pexels /search 接口
     * 2. 解析返回的 JSON 响应，提取第一张图片的原始尺寸 URL
     * 3. 如果搜索结果为空，抛出异常触发降级
     *
     * @param prompt 图片描述提示词（如"technology workspace"）
     * @return 图片的原始尺寸 URL
     * @throws ImageAcquisitionException 当搜索失败或结果为空时抛出
     */
    @Override
    public String generateImage(String prompt) {
        log.info("【Pexels策略】开始搜索图片，关键词: {}", prompt);

        try {
            // 步骤1：构建请求 URL
            // 对查询参数进行 URL 编码，避免中文等特殊字符导致请求失败
            String encodedQuery = URLEncoder.encode(prompt, StandardCharsets.UTF_8);
            String url = PEXELS_API_BASE + SEARCH_ENDPOINT
                    + "?query=" + encodedQuery
                    + "&per_page=" + PER_PAGE;

            // 步骤2：设置认证请求头
            // Pexels 要求通过 Authorization 头传递 API Key
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", apiKey);
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            // 步骤3：发送 GET 请求
            ResponseEntity<Map> response = restTemplate.exchange(
                    url,
                    org.springframework.http.HttpMethod.GET,
                    entity,
                    Map.class
            );

            // 步骤4：检查响应状态
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new ImageAcquisitionException(
                        "Pexels API 返回异常状态码: " + response.getStatusCode()
                );
            }

            // 步骤5：从响应体中提取图片列表
            List<Map<String, Object>> photos =
                    (List<Map<String, Object>>) response.getBody().get("photos");

            // 步骤6：检查搜索结果是否为空
            if (photos == null || photos.isEmpty()) {
                throw new ImageAcquisitionException(
                        "Pexels 搜索未找到匹配图片，关键词: " + prompt
                );
            }

            // 步骤7：取第一张图片的原始尺寸 URL
            Map<String, Object> firstPhoto = photos.get(0);
            Map<String, String> src = (Map<String, String>) firstPhoto.get("src");
            String imageUrl = src.get("original");

            log.info("【Pexels策略】成功获取图片: {}", imageUrl);
            return imageUrl;

        } catch (ImageAcquisitionException e) {
            // 业务异常直接抛出，由上层 ImageContext 处理降级
            throw e;
        } catch (Exception e) {
            // 网络异常等非业务异常，包装后抛出
            throw new ImageAcquisitionException(
                    "Pexels API 调用失败: " + e.getMessage(), e
            );
        }
    }

    /**
     * 获取策略名称
     *
     * @return "pexels"
     */
    @Override
    public String getName() {
        return "pexels";
    }

    /**
     * 检查策略是否可用
     * <p>
     * 可用条件：API Key 已配置（非 null 且非空）
     *
     * @return true 表示 Pexels 策略可用
     */
    @Override
    public boolean isAvailable() {
        return apiKey != null && !apiKey.isEmpty();
    }
}
```

**代码要点：**

| 要点 | 说明 |
|------|------|
| URL 编码 | 使用 `URLEncoder.encode` 对中文提示词编码，避免请求失败 |
| 异常处理 | 业务异常直接抛出，网络异常包装后抛出，统一由上层处理 |
| isAvailable | 通过 API Key 判空决定策略是否可用，避免无效调用 |

### 3.7 Mermaid 策略实现（MermaidImageStrategy.java）

```java
package com.passage.strategy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * MermaidImageStrategy - Mermaid 图表生成策略
 * <p>
 * 策略模式的具体策略实现之一。
 * 将提示词解析为 Mermaid 图表定义，然后通过 mermaid.ink 渲染服务生成图片。
 * <p>
 * 策略说明：
 * - 优先级：备选策略（当 Pexels 无法满足需求时使用）
 * - 适用场景：技术架构图、流程图、时序图、数据流转图等示意图
 * - 优势：精确控制图表内容，适合技术类文章的配图需求
 * - 局限性：只能生成图表类图片，无法生成实景照片
 *
 * @author AI-Passage-Creator
 */
public class MermaidImageStrategy implements ImageStrategy {

    /** 日志记录器 */
    private static final Logger log = LoggerFactory.getLogger(MermaidImageStrategy.class);

    /** Mermaid 渲染服务地址（mermaid.ink 免费服务，支持 SVG 和 PNG 格式） */
    private static final String MERMAID_RENDER_URL = "https://mermaid.ink/img/";

    /** 图表类型正则匹配模式 */
    private static final Pattern CHART_TYPE_PATTERN = Pattern.compile(
            "(流程图|时序图|类图|架构图|状态图|甘特图|ER图|饼图)"
    );

    /**
     * 生成 Mermaid 图表图片
     * <p>
     * 实现步骤：
     * 1. 解析提示词，识别图表类型
     * 2. 根据提示词生成 Mermaid 定义语法
     * 3. 将 Mermaid 定义进行 Base64 编码
     * 4. 拼接 mermaid.ink 渲染 URL
     *
     * @param prompt 图片描述提示词（如"用户登录的流程图"）
     * @return Mermaid 图表的渲染图片 URL
     * @throws ImageAcquisitionException 当 Mermaid 定义生成失败时抛出
     */
    @Override
    public String generateImage(String prompt) {
        log.info("【Mermaid策略】开始生成图表，描述: {}", prompt);

        try {
            // 步骤1：根据提示词生成 Mermaid 图表定义
            // 根据提示词中的关键词（"流程图"、"时序图"等）选择图表类型
            String mermaidDefinition = generateMermaidDefinition(prompt);
            log.debug("【Mermaid策略】生成的定义: {}", mermaidDefinition);

            // 步骤2：对 Mermaid 定义进行 Base64 编码
            // mermaid.ink 要求将 Mermaid 定义编码后拼在 URL 中
            String encoded = Base64.getUrlEncoder().encodeToString(
                    mermaidDefinition.getBytes(java.nio.charset.StandardCharsets.UTF_8)
            );

            // 步骤3：拼接渲染 URL
            String imageUrl = MERMAID_RENDER_URL + encoded;

            log.info("【Mermaid策略】成功生成图表图片 URL: {}", imageUrl);
            return imageUrl;

        } catch (Exception e) {
            throw new ImageAcquisitionException(
                    "Mermaid 图表生成失败: " + e.getMessage(), e
            );
        }
    }

    /**
     * 根据提示词智能生成 Mermaid 图表定义
     * <p>
     * 解析逻辑：
     * 1. 检测提示词中的图表类型关键词，选择对应的 Mermaid 图表类型
     * 2. 提取关键实体和关系描述，转换为 Mermaid 语法
     * 3. 如果无法识别具体图表类型，默认使用流程图（graph TD）
     *
     * @param prompt 用户输入的提示词
     * @return Mermaid 语法定义的字符串
     */
    private String generateMermaidDefinition(String prompt) {
        // 检测图表类型关键词
        Matcher matcher = CHART_TYPE_PATTERN.matcher(prompt);
        String chartType = matcher.find() ? matcher.group(1) : "流程图";

        // 根据图表类型调用对应的生成方法
        return switch (chartType) {
            case "时序图" -> generateSequenceDiagram(prompt);
            case "类图" -> generateClassDiagram(prompt);
            case "架构图" -> generateArchitectureDiagram(prompt);
            case "状态图" -> generateStateDiagram(prompt);
            case "甘特图" -> generateGanttDiagram(prompt);
            case "ER图" -> generateErDiagram(prompt);
            case "饼图" -> generatePieChart(prompt);
            default -> generateFlowchart(prompt); // 流程图作为默认兜底
        };
    }

    /**
     * 生成流程图（graph TD）定义
     * <p>
     * 使用 TD（Top-Down，自上而下）布局。
     * 将提示词中的关键步骤解析为流程图节点和连接关系。
     */
    private String generateFlowchart(String prompt) {
        // 从提示词中提取关键步骤，构造流程图
        // 实际项目中会接入 NLP 或 LLM 进行更精确的解析
        return String.format("""
                graph TD
                    A[开始] --> B[%s]
                    B --> C[处理中]
                    C --> D[完成]
                    D --> E[结束]
                """, prompt.length() > 20 ? prompt.substring(0, 20) + "..." : prompt);
    }

    /**
     * 生成时序图（sequenceDiagram）定义
     */
    private String generateSequenceDiagram(String prompt) {
        return """
                sequenceDiagram
                    participant 用户
                    participant 系统
                    participant 服务端
                    用户->>系统: 发起请求
                    系统->>服务端: 转发请求
                    服务端-->>系统: 返回结果
                    系统-->>用户: 展示结果
                """;
    }

    /**
     * 生成类图（classDiagram）定义
     */
    private String generateClassDiagram(String prompt) {
        return """
                classDiagram
                    class 抽象类 {
                        +接口方法()
                    }
                    class 实现类1 {
                        +实现方法1()
                    }
                    class 实现类2 {
                        +实现方法2()
                    }
                    抽象类 <|-- 实现类1
                    抽象类 <|-- 实现类2
                """;
    }

    /**
     * 生成架构图（graph LR）定义
     * <p>
     * 使用 LR（Left-Right，从左到右）布局，适合展示分层架构
     */
    private String generateArchitectureDiagram(String prompt) {
        return """
                graph LR
                    subgraph 接入层
                        A[API网关]
                    end
                    subgraph 业务层
                        B[服务A] --> C[服务B]
                    end
                    subgraph 数据层
                        D[(数据库)]
                    end
                    A --> B
                    C --> D
                """;
    }

    /**
     * 生成状态图（stateDiagram-v2）定义
     */
    private String generateStateDiagram(String prompt) {
        return """
                stateDiagram-v2
                    [*] --> 待审核
                    待审核 --> 审核中
                    审核中 --> 已通过
                    审核中 --> 已驳回
                    已通过 --> [*]
                    已驳回 --> 待审核
                """;
    }

    /**
     * 生成甘特图（gantt）定义
     */
    private String generateGanttDiagram(String prompt) {
        return """
                gantt
                    title 项目进度
                    dateFormat  YYYY-MM-DD
                    section 阶段一
                    需求分析     :a1, 2024-01-01, 30d
                    系统设计     :a2, after a1, 20d
                    section 阶段二
                    开发实现     :a3, after a2, 40d
                    测试部署     :a4, after a3, 15d
                """;
    }

    /**
     * 生成 ER 图（erDiagram）定义
     */
    private String generateErDiagram(String prompt) {
        return """
                erDiagram
                    用户 ||--o{ 订单 : 拥有
                    订单 ||--|{ 订单项 : 包含
                    商品 ||--o{ 订单项 : 属于
                """;
    }

    /**
     * 生成饼图（pie）定义
     */
    private String generatePieChart(String prompt) {
        return """
                pie title 数据分布
                    "类别A" : 45
                    "类别B" : 30
                    "类别C" : 25
                """;
    }

    /**
     * 获取策略名称
     *
     * @return "mermaid"
     */
    @Override
    public String getName() {
        return "mermaid";
    }

    /**
     * 检查策略是否可用
     * <p>
     * Mermaid 策略不依赖外部 API Key，始终可用。
     * 渲染依赖 mermaid.ink 外部服务，如果网络不可达，
     * 在 generateImage 阶段会抛出异常由上层降级处理。
     *
     * @return 始终返回 true
     */
    @Override
    public boolean isAvailable() {
        return true;
    }
}
```

**Mermaid 策略特点：**

- 不依赖 API Key，始终可用
- 通过正则匹配提示词中的图表类型关键词，自动选择图表类型
- 支持 8 种图表类型：流程图、时序图、类图、架构图、状态图、甘特图、ER 图、饼图
- 默认使用流程图作为兜底图表类型

### 3.8 上下文类（ImageContext.java）

```java
package com.passage.strategy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * ImageContext - 图片获取策略上下文（核心编排类）
 * <p>
 * 策略模式中的 Context 角色，负责：
 * 1. 管理策略列表（按优先级排序）
 * 2. 执行策略选择与调用
 * 3. 实现失败降级链路
 * 4. 提供最终兜底方案（Picsum 随机图片）
 * <p>
 * 降级链路（三段式容错）：
 * 第一级：主策略优先尝试
 * 第二级：遍历备用策略
 * 第三级：Picsum 兜底
 *
 * @author AI-Passage-Creator
 */
public class ImageContext {

    /** 日志记录器 */
    private static final Logger log = LoggerFactory.getLogger(ImageContext.class);

    /** Picsum 兜底图片基础 URL（生成随机图片，100% 可用） */
    private static final String PICSUM_BASE_URL = "https://picsum.photos";

    /** 兜底图片宽度 */
    private static final int FALLBACK_WIDTH = 800;

    /** 兜底图片高度 */
    private static final int FALLBACK_HEIGHT = 600;

    /** 策略列表（按优先级从高到低排序） */
    private final List<ImageStrategy> strategies;

    /** 默认主策略索引（默认为 0，即列表第一个） */
    private int primaryStrategyIndex;

    /**
     * 构造方法
     *
     * @param strategies 按优先级排序的策略列表（索引0为最高优先级）
     */
    public ImageContext(List<ImageStrategy> strategies) {
        this.strategies = new ArrayList<>(strategies);
        this.primaryStrategyIndex = 0;
        log.info("ImageContext 初始化完成，共 {} 个策略", strategies.size());
    }

    /**
     * 获取图片（带完整降级链路）
     * <p>
     * 执行流程：
     * 1. 尝试主策略（primaryStrategyIndex 指向的策略）
     * 2. 如果主策略不可用或失败，遍历所有备用策略
     * 3. 如果所有策略都失败，使用 Picsum 兜底图片
     *
     * @param prompt 图片描述提示词
     * @return 获取到的图片 URL（保证不会返回 null）
     */
    public String getImage(String prompt) {
        log.info("====== 开始获取图片，提示词: {} ======", prompt);

        // 第一阶段：尝试主策略
        String result = tryPrimaryStrategy(prompt);
        if (result != null) {
            return result;
        }

        // 第二阶段：尝试备用策略（降级）
        result = tryFallbackStrategies(prompt);
        if (result != null) {
            return result;
        }

        // 第三阶段：Picsum 兜底（保证成功）
        return usePicsumFallback(prompt);
    }

    /**
     * 尝试主策略
     * <p>
     * 主策略通常是当前最合适的策略。如果主策略不可用或调用失败，
     * 自动进入降级链路。
     *
     * @param prompt 图片描述提示词
     * @return 图片 URL，如果主策略失败返回 null
     */
    private String tryPrimaryStrategy(String prompt) {
        ImageStrategy primary = strategies.get(primaryStrategyIndex);

        log.info("【阶段一】尝试主策略: {}", primary.getName());

        // 检查主策略是否可用（如 API Key 是否配置）
        if (!primary.isAvailable()) {
            log.warn("主策略 {} 不可用（如 API Key 未配置），进入降级", primary.getName());
            return null;
        }

        try {
            // 调用主策略的 generateImage 方法
            String imageUrl = primary.generateImage(prompt);
            log.info("主策略 {} 成功获取图片", primary.getName());
            return imageUrl;
        } catch (ImageAcquisitionException e) {
            // 主策略执行失败，记录日志后进入降级
            log.warn("主策略 {} 执行失败: {}，进入降级", primary.getName(), e.getMessage());
            return null;
        }
    }

    /**
     * 尝试备用策略（降级链路）
     * <p>
     * 遍历所有非主策略，按优先级依次尝试：
     * 1. 跳过主策略（已失败）
     * 2. 跳过不可用的策略
     * 3. 遇到第一个成功的策略立即返回
     * 4. 所有策略都失败则返回 null
     *
     * @param prompt 图片描述提示词
     * @return 图片 URL，所有备用策略都失败返回 null
     */
    private String tryFallbackStrategies(String prompt) {
        log.info("【阶段二】尝试备用策略（降级）");

        for (int i = 0; i < strategies.size(); i++) {
            // 跳过主策略（已在第一阶段尝试过）
            if (i == primaryStrategyIndex) {
                continue;
            }

            ImageStrategy strategy = strategies.get(i);

            // 检查备用策略是否可用
            if (!strategy.isAvailable()) {
                log.warn("备用策略 {} 不可用，跳过", strategy.getName());
                continue;
            }

            try {
                // 尝试调用备用策略
                log.info("尝试备用策略: {}", strategy.getName());
                String imageUrl = strategy.generateImage(prompt);
                log.info("备用策略 {} 成功获取图片", strategy.getName());
                return imageUrl;
            } catch (ImageAcquisitionException e) {
                // 备用策略失败，继续尝试下一个
                log.warn("备用策略 {} 执行失败: {}", strategy.getName(), e.getMessage());
            }
        }

        // 所有备用策略都已失败
        log.warn("所有备用策略均已失败，进入 Picsum 兜底");
        return null;
    }

    /**
     * Picsum 兜底方案
     * <p>
     * 当所有策略都失败时，使用 Picsum 提供的随机图片作为最终兜底。
     * Picsum 是一个免费图片占位符服务，返回随机的高质量图片，
     * 不需要任何 API Key，100% 可用。
     * <p>
     * 使用 seed 参数保证相同提示词生成相同的兜底图片（缓存友好）。
     *
     * @param prompt 图片描述提示词（用于生成 seed，保证一致性）
     * @return Picsum 随机图片 URL
     */
    private String usePicsumFallback(String prompt) {
        // 使用提示词的哈希值作为 seed，保证相同提示词获得相同兜底图片
        int seed = prompt.hashCode();
        String fallbackUrl = String.format(
                "%s/seed/%d/%d/%d",
                PICSUM_BASE_URL, seed, FALLBACK_WIDTH, FALLBACK_HEIGHT
        );

        log.info("【阶段三】使用 Picsum 兜底图片: {}", fallbackUrl);
        return fallbackUrl;
    }

    /**
     * 设置主策略（动态切换）
     * <p>
     * 允许动态调整主策略，例如：
     * - VIP 用户可以将 AI 生图设为主策略
     * - 非 VIP 用户默认以 Pexels 为主策略
     *
     * @param strategyName 策略名称（对应 getName() 返回值）
     * @throws IllegalArgumentException 如果找不到对应策略
     */
    public void setPrimaryStrategy(String strategyName) {
        for (int i = 0; i < strategies.size(); i++) {
            if (strategies.get(i).getName().equals(strategyName)) {
                this.primaryStrategyIndex = i;
                log.info("主策略已切换为: {}", strategyName);
                return;
            }
        }
        throw new IllegalArgumentException("未找到名为 " + strategyName + " 的策略");
    }

    /**
     * 获取当前主策略名称
     *
     * @return 主策略名称
     */
    public String getPrimaryStrategyName() {
        return strategies.get(primaryStrategyIndex).getName();
    }

    /**
     * 获取所有策略名称列表
     *
     * @return 策略名称列表
     */
    public List<String> getStrategyNames() {
        return strategies.stream()
                .map(ImageStrategy::getName)
                .toList();
    }
}
```

**ImageContext 核心逻辑：**

| 阶段 | 方法 | 说明 |
|------|------|------|
| 阶段一 | tryPrimaryStrategy | 尝试主策略，前置检查 isAvailable() |
| 阶段二 | tryFallbackStrategies | 遍历备用策略，跳过不可用和已失败的 |
| 阶段三 | usePicsumFallback | Picsum 兜底，100% 可用，永不抛异常 |

### 3.9 工厂类（ImageStrategyFactory.java）

```java
package com.passage.strategy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * ImageStrategyFactory - 图片策略工厂
 * <p>
 * 根据配置创建并组装 ImageContext 实例。
 * 工厂负责：
 * 1. 读取配置参数（API Key 等）
 * 2. 创建所有策略实例
 * 3. 按优先级排序并注入 ImageContext
 * 4. 根据用户等级（VIP/普通）设置默认主策略
 * <p>
 * 使用示例：
 * // 普通用户：Pexels 为主策略，Mermaid 为备选
 * ImageContext context = ImageStrategyFactory.createDefaultContext("pexels_key_xxx");
 * <p>
 * // VIP 用户：AI 生图为主策略
 * ImageContext vipContext = ImageStrategyFactory.createVipContext(
 *     "pexels_key_xxx", "openai_key_xxx"
 * );
 *
 * @author AI-Passage-Creator
 */
public class ImageStrategyFactory {

    /** 日志记录器 */
    private static final Logger log = LoggerFactory.getLogger(ImageStrategyFactory.class);

    /**
     * 私有构造方法，防止实例化
     * 工厂类不需要实例化，直接通过静态方法调用
     */
    private ImageStrategyFactory() {
    }

    /**
     * 创建默认的 ImageContext（普通用户）
     * <p>
     * 策略优先级：
     * 1. PexelsImageStrategy（主策略 - 免费图库搜索）
     * 2. MermaidImageStrategy（备选 - 图表生成）
     * 3. Picsum 兜底（在 ImageContext 内部实现）
     *
     * @param pexelsApiKey Pexels API Key
     * @return 配置好的 ImageContext 实例
     */
    public static ImageContext createDefaultContext(String pexelsApiKey) {
        log.info("创建默认 ImageContext（普通用户）");

        // 按优先级创建策略列表
        List<ImageStrategy> strategies = new ArrayList<>();
        strategies.add(new PexelsImageStrategy(pexelsApiKey));  // 主策略：Pexels
        strategies.add(new MermaidImageStrategy());              // 备选：Mermaid

        ImageContext context = new ImageContext(strategies);
        log.info("默认 ImageContext 创建完成，策略: {}", context.getStrategyNames());
        return context;
    }

    /**
     * 创建 VIP 用户的 ImageContext
     * <p>
     * 策略优先级：
     * 1. AiImageStrategy（主策略 - AI 生图，VIP 专属）
     * 2. PexelsImageStrategy（备选 - 免费图库搜索）
     * 3. MermaidImageStrategy（备选 - 图表生成）
     * 4. Picsum 兜底（在 ImageContext 内部实现）
     *
     * @param pexelsApiKey Pexels API Key
     * @param aiApiKey     AI 图片生成 API Key
     * @return 配置好的 ImageContext 实例
     */
    public static ImageContext createVipContext(String pexelsApiKey, String aiApiKey) {
        log.info("创建 VIP ImageContext");

        // 按优先级创建策略列表
        List<ImageStrategy> strategies = new ArrayList<>();
        strategies.add(new AiImageStrategy(aiApiKey, true));     // 主策略：AI 生图（VIP）
        strategies.add(new PexelsImageStrategy(pexelsApiKey));   // 备选：Pexels
        strategies.add(new MermaidImageStrategy());               // 备选：Mermaid

        ImageContext context = new ImageContext(strategies);
        // 将主策略设置为 AI 生图（索引0）
        context.setPrimaryStrategy("ai-image");
        log.info("VIP ImageContext 创建完成，策略: {}", context.getStrategyNames());
        return context;
    }

    /**
     * 自定义策略列表创建 ImageContext
     * <p>
     * 允许调用方完全自定义策略列表和顺序，灵活性最高。
     *
     * @param strategies 自定义策略列表（按优先级排序）
     * @return 配置好的 ImageContext 实例
     */
    public static ImageContext createCustomContext(List<ImageStrategy> strategies) {
        log.info("创建自定义 ImageContext，策略数量: {}", strategies.size());
        return new ImageContext(strategies);
    }
}
```

### 3.10 AI 生图策略（AiImageStrategy.java）

```java
package com.passage.strategy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * AiImageStrategy - AI 图片生成策略（VIP 功能）
 * <p>
 * 策略模式的具体策略实现之一。
 * 调用 AI 图片生成 API（如 DALL-E 3）生成图片。
 * 此策略为 VIP 专属功能，需要用户具有 VIP 身份。
 * <p>
 * 策略说明：
 * - 优先级：高级策略（需要 VIP 权限）
 * - 适用场景：需要特定构图、风格统一的配图
 * - 优势：可精确控制图片内容、风格和构图
 * - 局限性：生成速度较慢，消耗算力额度，仅 VIP 用户可用
 *
 * @author AI-Passage-Creator
 */
public class AiImageStrategy implements ImageStrategy {

    /** 日志记录器 */
    private static final Logger log = LoggerFactory.getLogger(AiImageStrategy.class);

    /** AI 图片生成 API 地址（支持 DALL-E 3 / Stable Diffusion 等） */
    private static final String AI_IMAGE_API_URL = "https://api.openai.com/v1/images/generations";

    /** 默认生成图片尺寸 */
    private static final String DEFAULT_SIZE = "1024x1024";

    /** 默认图片风格（vivid 为更鲜艳生动的风格） */
    private static final String DEFAULT_STYLE = "vivid";

    /** API Key */
    private final String apiKey;

    /** 是否为 VIP 用户 */
    private final boolean isVip;

    /** Spring RestTemplate */
    private final RestTemplate restTemplate;

    /**
     * 构造方法
     *
     * @param apiKey AI 图片生成 API 的密钥
     * @param isVip  当前用户是否为 VIP（VIP 才可使用此策略）
     */
    public AiImageStrategy(String apiKey, boolean isVip) {
        this.apiKey = apiKey;
        this.isVip = isVip;
        this.restTemplate = new RestTemplate();
        log.info("AiImageStrategy 初始化完成，VIP状态: {}", isVip);
    }

    /**
     * 调用 AI API 生成图片
     * <p>
     * 实现步骤：
     * 1. 构建请求体（包含提示词、尺寸、风格等参数）
     * 2. 设置认证头（Bearer Token）
     * 3. 发送 POST 请求到 AI 图片生成 API
     * 4. 解析返回的 JSON 响应，提取生成的图片 URL
     *
     * @param prompt 图片描述提示词
     * @return AI 生成的图片 URL
     * @throws ImageAcquisitionException 当 API 调用失败时抛出
     */
    @Override
    public String generateImage(String prompt) {
        log.info("【AI生图策略】开始生成图片，提示词: {}", prompt);

        // 前置检查：VIP 权限校验
        if (!isVip) {
            throw new ImageAcquisitionException("AI 生图为 VIP 专属功能，当前用户非 VIP");
        }

        try {
            // 步骤1：构建请求体
            // 使用 DALL-E 3 API 格式，支持 prompt、size、style 等参数
            Map<String, Object> requestBody = Map.of(
                    "model", "dall-e-3",
                    "prompt", prompt,
                    "n", 1,
                    "size", DEFAULT_SIZE,
                    "style", DEFAULT_STYLE,
                    "quality", "standard"
            );

            // 步骤2：设置 HTTP 请求头
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            // 步骤3：发送 POST 请求
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    AI_IMAGE_API_URL,
                    request,
                    Map.class
            );

            // 步骤4：解析响应
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new ImageAcquisitionException(
                        "AI 图片生成 API 返回异常: " + response.getStatusCode()
                );
            }

            // 从响应中提取生成的图片数据
            List<Map<String, Object>> data =
                    (List<Map<String, Object>>) response.getBody().get("data");
            if (data == null || data.isEmpty()) {
                throw new ImageAcquisitionException("AI 图片生成返回为空");
            }

            // 取第一张图片的 URL
            String imageUrl = (String) data.get(0).get("url");
            log.info("【AI生图策略】成功生成图片: {}", imageUrl);
            return imageUrl;

        } catch (ImageAcquisitionException e) {
            throw e;
        } catch (Exception e) {
            throw new ImageAcquisitionException(
                    "AI 图片生成调用失败: " + e.getMessage(), e
            );
        }
    }

    /**
     * 获取策略名称
     *
     * @return "ai-image"
     */
    @Override
    public String getName() {
        return "ai-image";
    }

    /**
     * 检查策略是否可用
     * <p>
     * 可用条件（同时满足）：
     * 1. API Key 已配置
     * 2. 当前用户为 VIP
     *
     * @return true 表示 AI 生图策略可用
     */
    @Override
    public boolean isAvailable() {
        boolean available = isVip && apiKey != null && !apiKey.isEmpty();
        if (!available) {
            log.warn("【AI生图策略】不可用 - VIP状态: {}, API Key已配置: {}",
                    isVip, apiKey != null && !apiKey.isEmpty());
        }
        return available;
    }
}
```

### 3.11 Spring Boot 启动类（StrategyDemoApplication.java）

```java
package com.passage.strategy;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * StrategyDemoApplication - Spring Boot 应用启动类
 * <p>
 * 策略模式示例项目的入口。
 * 启动后可通过 REST API 测试策略模式的功能。
 *
 * @author AI-Passage-Creator
 */
@SpringBootApplication
public class StrategyDemoApplication {

    public static void main(String[] args) {
        // 启动 Spring Boot 应用
        SpringApplication.run(StrategyDemoApplication.class, args);
    }
}
```

### 3.12 REST 控制器（ImageController.java）

```java
package com.passage.strategy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * ImageController - 图片获取 REST 控制器
 * <p>
 * 提供 HTTP API 接口，方便测试策略模式的配图功能。
 * 通过 URL 参数控制使用哪种策略和提示词。
 *
 * @author AI-Passage-Creator
 */
@RestController
@RequestMapping("/api/image")
public class ImageController {

    /** 日志记录器 */
    private static final Logger log = LoggerFactory.getLogger(ImageController.class);

    /** Pexels API Key（从配置文件注入） */
    @Value("${image.pexels.api-key:}")
    private String pexelsApiKey;

    /** AI API Key（从配置文件注入） */
    @Value("${image.ai.api-key:}")
    private String aiApiKey;

    /**
     * 获取图片 —— 默认策略链路
     * <p>
     * GET /api/image?prompt=春天的樱花
     * <p>
     * 使用默认策略链路：
     * 1. 如果是 VIP 用户，AI 生图为主策略
     * 2. 普通用户，Pexels 为主策略
     * 3. 失败后自动降级到 Mermaid
     * 4. 最终兜底 Picsum
     *
     * @param prompt 图片描述提示词
     * @param vip    是否为 VIP 用户（可选，默认 false）
     * @return 包含图片 URL 的 JSON 响应
     */
    @GetMapping
    public Map<String, Object> getImage(
            @RequestParam String prompt,
            @RequestParam(defaultValue = "false") boolean vip) {

        log.info("收到图片请求，提示词: {}, VIP: {}", prompt, vip);

        // 根据用户等级创建对应的 ImageContext
        ImageContext context;
        if (vip) {
            // VIP 用户：AI 生图为主策略
            context = ImageStrategyFactory.createVipContext(pexelsApiKey, aiApiKey);
        } else {
            // 普通用户：Pexels 为主策略
            context = ImageStrategyFactory.createDefaultContext(pexelsApiKey);
        }

        // 执行策略模式获取图片
        String imageUrl = context.getImage(prompt);

        // 构建响应
        Map<String, Object> result = new HashMap<>();
        result.put("prompt", prompt);
        result.put("imageUrl", imageUrl);
        result.put("primaryStrategy", context.getPrimaryStrategyName());
        result.put("availableStrategies", context.getStrategyNames());
        result.put("vip", vip);

        log.info("图片获取完成: {}", imageUrl);
        return result;
    }

    /**
     * 切换策略 —— 动态切换主策略
     * <p>
     * POST /api/image/switch?strategy=mermaid
     * <p>
     * 演示策略模式的运行时切换能力。
     * 注意：此接口仅用于演示，实际项目中策略切换由 ImageContext 内部管理。
     *
     * @param strategy 新的主策略名称
     * @return 切换结果
     */
    @PostMapping("/switch")
    public Map<String, Object> switchStrategy(@RequestParam String strategy) {
        Map<String, Object> result = new HashMap<>();
        result.put("message", "策略切换接口仅用于演示，实际切换由 ImageContext 内部管理");
        result.put("requestedStrategy", strategy);
        return result;
    }
}
```

### 3.13 单元测试（ImageStrategyTest.java）

```java
package com.passage.strategy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ImageStrategyTest - 图片策略单元测试
 * <p>
 * 测试覆盖场景：
 * 1. 默认策略链路测试（Pexels + Mermaid + Picsum 兜底）
 * 2. VIP 策略链路测试（AI 生图 + Pexels + Mermaid + Picsum 兜底）
 * 3. 空 API Key 降级测试（Pexels 不可用 → Mermaid 降级）
 * 4. Picsum 最终兜底测试
 * 5. 动态切换主策略测试
 * 6. 策略可用性状态测试
 *
 * @author AI-Passage-Creator
 */
class ImageStrategyTest {

    /** 测试用 Pexels API Key（模拟值，非真实 Key） */
    private static final String TEST_PEXELS_KEY = "test_pexels_api_key_12345";

    /** 测试用 AI API Key（模拟值，非真实 Key） */
    private static final String TEST_AI_KEY = "test_ai_api_key_67890";

    /** 测试用提示词 */
    private static final String TEST_PROMPT = "春日樱花树下读书的少女";

    /** 测试用技术类提示词 */
    private static final String TECH_PROMPT = "微服务架构流程图";

    // ==================== 测试用例 1：默认策略链路 ====================

    /**
     * 测试默认策略链路（普通用户）
     * <p>
     * 验证场景：
     * - 普通用户使用 Pexels 为主策略 + Mermaid 为备选
     * - 由于 Pexels API Key 是模拟值，实际调用会失败
     * - 预期降级到 Mermaid 策略（Mermaid 不依赖 API Key）
     * - 最终返回的图片 URL 应该是 Mermaid 图表的渲染 URL
     */
    @Test
    @DisplayName("测试默认策略链路：Pexels失败 -> Mermaid降级成功")
    void testDefaultStrategyChain() {
        // 创建默认的 ImageContext（普通用户模式）
        // 策略顺序：Pexels（主） -> Mermaid（备选）
        ImageContext context = ImageStrategyFactory.createDefaultContext(TEST_PEXELS_KEY);

        // 执行图片获取（会触发降级链路）
        String imageUrl = context.getImage(TEST_PROMPT);

        // 验证：虽然 Pexels 会失败，但 Mermaid 降级成功
        assertNotNull(imageUrl, "图片 URL 不应为 null");
        assertTrue(imageUrl.contains("mermaid.ink"),
                "降级后应返回 Mermaid 图片 URL，实际: " + imageUrl);

        System.out.println("【测试1】默认策略链路结果: " + imageUrl);
    }

    // ==================== 测试用例 2：VIP 策略链路 ====================

    /**
     * 测试 VIP 策略链路
     * <p>
     * 验证场景：
     * - VIP 用户使用 AI 生图为主策略 + Pexels + Mermaid 为备选
     * - 由于 AI API Key 是模拟值，AI 生图会失败
     * - Pexels API Key 也是模拟值，Pexels 也会失败
     * - 预期最终降级到 Mermaid 策略
     */
    @Test
    @DisplayName("测试VIP策略链路：AI生图失败 -> Pexels失败 -> Mermaid降级成功")
    void testVipStrategyChain() {
        // 创建 VIP 的 ImageContext
        ImageContext context = ImageStrategyFactory.createVipContext(
                TEST_PEXELS_KEY, TEST_AI_KEY
        );

        // 执行图片获取
        String imageUrl = context.getImage(TECH_PROMPT);

        // 验证：AI 生图和 Pexels 都失败后，Mermaid 降级成功
        assertNotNull(imageUrl, "图片 URL 不应为 null");
        assertTrue(imageUrl.contains("mermaid.ink"),
                "降级后应返回 Mermaid 图片 URL，实际: " + imageUrl);

        System.out.println("【测试2】VIP策略链路结果: " + imageUrl);
    }

    // ==================== 测试用例 3：空 API Key 降级 ====================

    /**
     * 测试 API Key 为空时的降级行为
     * <p>
     * 验证场景：
     * - Pexels API Key 为空字符串
     * - PexelsStrategy.isAvailable() 返回 false（因为 API Key 为空）
     * - 直接跳过 Pexels，使用 Mermaid 作为降级
     * - 验证 isAvailable() 前置检查机制有效
     */
    @Test
    @DisplayName("测试空API Key降级：Pexels不可用 -> Mermaid降级成功")
    void testEmptyApiKeyFallback() {
        // 使用空字符串作为 API Key，模拟未配置的情况
        ImageContext context = ImageStrategyFactory.createDefaultContext("");

        // 执行图片获取
        String imageUrl = context.getImage(TEST_PROMPT);

        // 验证：Pexels 因 API Key 为空不可用，直接降级到 Mermaid
        assertNotNull(imageUrl, "图片 URL 不应为 null");
        assertTrue(imageUrl.contains("mermaid.ink"),
                "Pexels不可用时应降级到 Mermaid，实际: " + imageUrl);

        System.out.println("【测试3】空API Key降级结果: " + imageUrl);
    }

    // ==================== 测试用例 4：Picsum 最终兜底 ====================

    /**
     * 测试 Picsum 最终兜底方案
     * <p>
     * 验证场景：
     * - 创建一个只有 Pexels 策略的 ImageContext
     * - Pexels API Key 为空，isAvailable() 返回 false
     * - 所有策略都不可用/失败
     * - 最终降级到 Picsum 随机图片
     * <p>
     * 此测试验证"三段式容错"的最后一道防线是否正常工作。
     */
    @Test
    @DisplayName("测试Picsum最终兜底：所有策略失败 -> Picsum兜底成功")
    void testPicsumFallback() {
        // 创建一个只有 Pexels 策略的上下文，且 API Key 为空
        // 这样所有策略都不可用，触发 Picsum 兜底
        PexelsImageStrategy pexels = new PexelsImageStrategy("");
        ImageContext context = ImageStrategyFactory.createCustomContext(
                List.of(pexels)
        );

        // 执行图片获取
        String imageUrl = context.getImage(TEST_PROMPT);

        // 验证：最终返回 Picsum 图片 URL
        assertNotNull(imageUrl, "Picsum 兜底图片 URL 不应为 null");
        assertTrue(imageUrl.contains("picsum.photos"),
                "所有策略失败后应返回 Picsum 兜底图片，实际: " + imageUrl);

        // 验证：URL 包含 seed 参数（基于提示词哈希生成）
        assertTrue(imageUrl.contains("/seed/"),
                "Picsum URL 应包含 seed 参数，实际: " + imageUrl);

        System.out.println("【测试4】Picsum兜底结果: " + imageUrl);
    }

    // ==================== 测试用例 5：策略切换 ====================

    /**
     * 测试动态切换主策略
     * <p>
     * 验证场景：
     * - 默认主策略为 Pexels
     * - 动态切换主策略为 Mermaid
     * - 验证切换后主策略名称正确更新
     * - 验证切换后能正确获取图片
     */
    @Test
    @DisplayName("测试动态切换主策略")
    void testSwitchPrimaryStrategy() {
        // 创建默认上下文
        ImageContext context = ImageStrategyFactory.createDefaultContext(TEST_PEXELS_KEY);

        // 验证默认主策略是 Pexels
        assertEquals("pexels", context.getPrimaryStrategyName(),
                "默认主策略应为 pexels");

        // 动态切换主策略为 Mermaid
        context.setPrimaryStrategy("mermaid");
        assertEquals("mermaid", context.getPrimaryStrategyName(),
                "切换后主策略应为 mermaid");

        // 验证切换后能正确获取图片
        String imageUrl = context.getImage(TEST_PROMPT);
        assertNotNull(imageUrl, "切换主策略后图片 URL 不应为 null");
        assertTrue(imageUrl.contains("mermaid.ink"),
                "主策略为 Mermaid 时应返回 Mermaid 图片，实际: " + imageUrl);

        System.out.println("【测试5】策略切换结果: " + imageUrl);
    }

    // ==================== 测试用例 6：策略可用性状态 ====================

    /**
     * 测试策略的 isAvailable() 方法
     * <p>
     * 验证场景：
     * - Pexels 策略：有 API Key 时可用，无 API Key 时不可用
     * - Mermaid 策略：始终可用
     * - AI 生图策略：VIP + 有 API Key 时可用，非 VIP 时不可用
     */
    @Test
    @DisplayName("测试策略可用性状态")
    void testStrategyAvailability() {
        // 验证 Pexels 策略可用性
        PexelsImageStrategy pexelsWithKey = new PexelsImageStrategy(TEST_PEXELS_KEY);
        assertTrue(pexelsWithKey.isAvailable(), "有 API Key 时 Pexels 应可用");

        PexelsImageStrategy pexelsNoKey = new PexelsImageStrategy("");
        assertFalse(pexelsNoKey.isAvailable(), "无 API Key 时 Pexels 应不可用");

        // 验证 Mermaid 策略可用性（始终可用）
        MermaidImageStrategy mermaid = new MermaidImageStrategy();
        assertTrue(mermaid.isAvailable(), "Mermaid 策略应始终可用");

        // 验证 AI 生图策略可用性
        AiImageStrategy aiVip = new AiImageStrategy(TEST_AI_KEY, true);
        assertTrue(aiVip.isAvailable(), "VIP + 有 Key 时 AI 生图应可用");

        AiImageStrategy aiNonVip = new AiImageStrategy(TEST_AI_KEY, false);
        assertFalse(aiNonVip.isAvailable(), "非 VIP 时 AI 生图应不可用");

        System.out.println("【测试6】策略可用性验证完成");
    }
}
```

---

## 四、运行验证

### 4.1 启动项目

在项目根目录执行：

```bash
# 使用 Maven 编译并启动
mvn spring-boot:run
```

启动成功后，控制台输出：

```
2024-XX-XX 10:00:00 - StrategyDemoApplication 启动成功
2024-XX-XX 10:00:00 - ImageContext 初始化完成，共 2 个策略
```

### 4.2 测试 API 接口

**测试 1：普通用户获取图片**

```bash
GET http://localhost:8080/api/image?prompt=春天的樱花
```

预期响应（由于 Pexels API Key 为模拟值，会降级到 Mermaid）：

```json
{
    "prompt": "春天的樱花",
    "imageUrl": "https://mermaid.ink/img/...",
    "primaryStrategy": "pexels",
    "availableStrategies": ["pexels", "mermaid"],
    "vip": false
}
```

**测试 2：VIP 用户获取图片**

```bash
GET http://localhost:8080/api/image?prompt=未来城市概念图&vip=true
```

预期响应（AI 生图失败 -> Pexels 失败 -> Mermaid 降级）：

```json
{
    "prompt": "未来城市概念图",
    "imageUrl": "https://mermaid.ink/img/...",
    "primaryStrategy": "ai-image",
    "availableStrategies": ["ai-image", "pexels", "mermaid"],
    "vip": true
}
```

### 4.3 运行单元测试

```bash
# 运行所有测试
mvn test

# 运行指定测试类
mvn -Dtest=ImageStrategyTest test
```

预期测试结果：

```
[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0
```

**测试通过说明：**

| 测试用例 | 预期结果 | 验证点 |
|---------|----------|--------|
| testDefaultStrategyChain | 返回 mermaid.ink URL | Pexels 失败 -> Mermaid 降级 |
| testVipStrategyChain | 返回 mermaid.ink URL | AI 失败 -> Pexels 失败 -> Mermaid 降级 |
| testEmptyApiKeyFallback | 返回 mermaid.ink URL | isAvailable 前置检查生效 |
| testPicsumFallback | 返回 picsum.photos URL | 兜底机制生效 |
| testSwitchPrimaryStrategy | 切换后返回 Mermaid 图片 | 动态切换生效 |
| testStrategyAvailability | 各策略可用性状态正确 | isAvailable 逻辑正确 |

### 4.4 降级流程验证

通过修改 API Key 配置，可以验证不同的降级场景：

| 场景 | Pexels Key | AI Key | 预期结果 |
|------|-----------|--------|---------|
| 全部配置 | 有效 Key | 有效 Key | Pexels 成功（普通用户） |
| Pexels 失败 | 模拟 Key | 有效 Key | Mermaid 降级 |
| 未配置 Pexels | 空字符串 | 有效 Key | Mermaid 降级 |
| 全部失败 | 空字符串 | 空字符串 | Picsum 兜底 |

---

## 五、项目对照

### 5.1 与 ai-passage-creator 项目的对比

| 对比维度 | 入门示例（本文） | ai-passage-creator 项目 |
|----------|----------------|----------------------|
| 策略接口 | ImageStrategy（3 个方法） | ImageSearchService（2 个方法） |
| 具体策略 | 3 个（Pexels、Mermaid、AI） | 6 个（Pexels、Mermaid、Iconify、BingEmoji、NanoBanana、SVG） |
| 上下文 | ImageContext（手动管理） | ImageServiceStrategy（Spring 自动注入） |
| 降级机制 | 三段式（主策略 -> 备用 -> Picsum） | 多级降级链 + 递归降级 |
| 配置方式 | 工厂类硬编码策略列表 | application.yml 配置策略优先级 |
| 注册方式 | 工厂类手动 new | @Service 自动注册 + Map 注入 |
| 权限控制 | isAvailable() 前置检查 | ImageMethodEnum.AccessLevel 枚举 |
| 并行处理 | 同步单线程 | CompletableFuture 并行 + 虚拟线程 |
| 缓存 | 无 | Caffeine 本地缓存（30 分钟 TTL） |
| 监控 | 日志输出 | 日志 + 降级事件统计 |

### 5.2 入门示例的简化点

为了使初学者更容易理解，入门示例做了以下简化：

1. **更少的策略数量**：项目中有 6 种配图策略，入门示例只实现了 3 种最核心的
2. **更简单的策略注册**：项目使用 Spring 的 `@Service` + `Map<String, ImageSearchService>` 自动注入，入门示例使用工厂类手动创建
3. **更简单的降级链**：项目使用递归降级链，入门示例使用三段式顺序降级
4. **无并行处理**：项目中 6 张配图并行生成，入门示例同步单线程
5. **无缓存**：项目使用 Caffeine 缓存相同关键词的搜索结果，入门示例每次重新调用

### 5.3 从入门到项目实战的进阶路径

```
入门示例（本文）
  │
  ├── Step 1: 改用 Spring 管理策略
  │     @Service 注册 + @Autowired Map 注入
  │
  ├── Step 2: 增加策略数量
  │     从 3 个扩展到 6 个（Iconify、Emoji、SVG）
  │
  ├── Step 3: 引入降级链配置
  │     application.yml 配置优先级和降级参数
  │
  ├── Step 4: 并行化
  │     CompletableFuture + 虚拟线程并行生成
  │
  ├── Step 5: 加入缓存
  │     Caffeine 本地缓存减少重复请求
  │
  └── Step 6: 加入监控
        降级事件统计、报警、熔断
```

---

## 六、面试题

### Q1: 策略模式的核心三要素是什么？在配图系统中如何体现？

**参考答案：**

策略模式的核心三要素是**策略接口（Strategy）、具体策略（ConcreteStrategy）和上下文（Context）**。

**1. 策略接口（Strategy）**

定义所有策略必须实现的方法，是策略模式的核心契约。

```java
// 策略接口 —— 定义统一的方法签名
public interface ImageStrategy {
    String generateImage(String prompt);  // 核心业务方法
    String getName();                      // 策略标识
    boolean isAvailable();                 // 可用性检查
}
```

**2. 具体策略（ConcreteStrategy）**

实现策略接口的具体算法类，每个类只负责一种算法。

```java
// 具体策略 A —— 从 Pexels 搜索图片
public class PexelsImageStrategy implements ImageStrategy {
    public String generateImage(String prompt) {
        // 调用 Pexels REST API
    }
    public boolean isAvailable() {
        return apiKey != null && !apiKey.isEmpty();
    }
}

// 具体策略 B —— 生成 Mermaid 图表
public class MermaidImageStrategy implements ImageStrategy {
    public String generateImage(String prompt) {
        // 生成 Mermaid 定义并渲染
    }
    public boolean isAvailable() {
        return true; // 始终可用
    }
}
```

**3. 上下文（Context）**

持有策略引用，负责调用策略和管理降级。

```java
// 上下文 —— 持有策略列表，负责调用和降级
public class ImageContext {
    private List<ImageStrategy> strategies;

    public String getImage(String prompt) {
        // 尝试主策略 -> 降级备用策略 -> Picsum 兜底
    }
}
```

**在配图系统中的体现：**

| 三要素 | 配图系统中的实现 | 对应的类 |
|--------|----------------|---------|
| Strategy | 配图策略接口 | ImageStrategy |
| ConcreteStrategy | 具体配图方式 | PexelsImageStrategy、MermaidImageStrategy、AiImageStrategy |
| Context | 策略编排与降级 | ImageContext |

### Q2: 策略模式中的降级链路是如何实现的？有哪些设计要点？

**参考答案：**

**降级链路的实现原理：**

降级链路的本质是**try-catch + 顺序遍历**。上下文维护一个策略列表，按优先级排序。调用时从主策略开始，失败后顺次尝试下一个策略，直到成功或用尽所有策略。

```java
// 降级链路的核心实现
public String getImage(String prompt) {
    // 阶段一：主策略
    if (tryPrimaryStrategy(prompt)) return result;

    // 阶段二：备用策略
    if (tryFallbackStrategies(prompt)) return result;

    // 阶段三：兜底方案
    return usePicsumFallback(prompt);
}
```

**四个设计要点：**

**要点 1：前置检查（isAvailable）**

在调用策略之前，先检查策略是否可用，避免无效调用。

```java
if (!strategy.isAvailable()) {
    // 跳过不可用的策略，直接进入下一级
    continue;
}
```

**要点 2：异常隔离**

每个策略的异常在自己的 try-catch 中处理，不影响其他策略。

```java
try {
    return strategy.generateImage(prompt);
} catch (ImageAcquisitionException e) {
    // 本策略失败，继续尝试下一个
    log.warn("策略失败", e);
}
```

**要点 3：兜底保证**

兜底方案必须 100% 可用，不依赖外部服务。

```java
// Picsum 兜底：不需要 API Key，不需要网络（仅生成 URL）
private String usePicsumFallback(String prompt) {
    int seed = prompt.hashCode();
    return String.format("https://picsum.photos/seed/%d/800/600", seed);
}
```

**要点 4：日志记录**

每个降级步骤都记录日志，便于监控和问题排查。

```java
log.warn("主策略 {} 不可用，进入降级", primary.getName());
log.warn("备用策略 {} 执行失败: {}", strategy.getName(), e.getMessage());
log.info("使用 Picsum 兜底图片: {}", fallbackUrl);
```

**降级链路的设计原则：**

| 原则 | 说明 | 配图系统中的应用 |
|------|------|----------------|
| 有序性 | 按优先级从高到低尝试 | 免费策略优先，付费策略后置 |
| 隔离性 | 单策略失败不影响其他策略 | 每个策略独立 try-catch |
| 可观测性 | 降级事件可监控可追踪 | 日志记录每个降级步骤 |
| 最终性 | 保证一定有结果返回 | Picsum 兜底 + seed 生成 |

### Q3: 如果要在配图系统中新增一种配图方式（如 Unsplash 图库），需要修改哪些代码？策略模式为什么能减少改动？

**参考答案：**

**使用策略模式后，新增配图方式只需要三步：**

**第 1 步：实现策略接口**

```java
// 新增文件：UnsplashImageStrategy.java
public class UnsplashImageStrategy implements ImageStrategy {
    private final String apiKey;

    public UnsplashImageStrategy(String apiKey) {
        this.apiKey = apiKey;
    }

    @Override
    public String generateImage(String prompt) {
        // 调用 Unsplash API 搜索图片
        // ...
    }

    @Override
    public String getName() {
        return "unsplash";
    }

    @Override
    public boolean isAvailable() {
        return apiKey != null && !apiKey.isEmpty();
    }
}
```

**第 2 步：在工厂类中注册新策略**

```java
// 修改：ImageStrategyFactory.java
public static ImageContext createDefaultContext(String pexelsApiKey) {
    List<ImageStrategy> strategies = new ArrayList<>();
    strategies.add(new PexelsImageStrategy(pexelsApiKey));
    strategies.add(new UnsplashImageStrategy(unsplashApiKey));  // ← 新增一行
    strategies.add(new MermaidImageStrategy());
    return new ImageContext(strategies);
}
```

**第 3 步：配置 API Key**

```yaml
# application.yml
image:
  unsplash:
    api-key: ${UNSPLASH_API_KEY:}
```

**无需修改的代码：**

| 组件 | 无需修改的原因 |
|------|--------------|
| ImageStrategy 接口 | 新策略实现已有接口，接口不变 |
| ImageContext 上下文 | 上下文只面向接口编程，不关心具体策略 |
| ImageController 控制器 | 控制器只调用 context.getImage()，不关心策略细节 |
| ImageStrategyTest 测试 | 已有测试不用改，新增策略的测试单独编写 |

**不用策略模式会怎样？**

如果使用 if-else 方式：

```java
// 坏味道：每次新增都要改这里
public String getImage(String prompt, String method) {
    if ("pexels".equals(method)) {
        // ...
    } else if ("mermaid".equals(method)) {
        // ...
    } else if ("ai".equals(method)) {
        // ...
    } else if ("unsplash".equals(method)) {  // ← 新增，要改现有代码
        // ...
    }
}
```

每新增一种配图方式，都要修改这个方法，增加一个 else-if 分支。如果这个方法有 200 行，你需要在 200 行的代码中找到合适的位置插入新分支，很容易引入 bug。

**策略模式的核心优势：**

| 维度 | 策略模式 | if-else 方式 |
|------|---------|-------------|
| 扩展方式 | 新增一个类文件 | 修改现有方法 |
| 修改风险 | 零修改现有代码 | 修改稳定代码，可能引入 bug |
| 测试难度 | 独立测试单个策略类 | 需要测试整个方法的所有分支 |
| 代码复用 | 策略可独立复用 | 逻辑耦合在方法中，难以复用 |
| 团队协作 | 多人可并行开发不同策略 | 多人修改同一方法，冲突风险高 |

这就是策略模式的核心价值——**对扩展开放，对修改关闭**（开闭原则）。

---

> **下期预告：** 第 4 篇将介绍 SSE 流式推送技术，带你理解如何实现 AI 对话的"打字机效果"。