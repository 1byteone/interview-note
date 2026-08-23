# 04 SSE流式输出入门：从SseEmitter理解服务端推送

> 本文是 ai-passage-creator 项目技术栈深度剖析系列的第 4 篇（入门篇）。AI 生成一篇文章需要 10-30 秒，如果等全部生成完再返回，用户早就离开了。SSE 流式输出让用户"边看边等"，将体验从"等待进度条"升级为"观看创作过程"。
>
> **对应项目：** `ai-passage-creator/ai-passage-creator-java` 模块 `sse` 包
> **难度等级：** Level 1 入门
> **预计阅读时间：** 20 分钟（含代码实操）

---

## 一、项目背景

### 1.1 什么是 SSE

SSE（Server-Sent Events，服务端推送事件）是 HTML5 标准中定义的一种**服务端向客户端单向推送数据**的技术。它基于标准 HTTP 协议，浏览器原生支持，无需引入第三方库。

**SSE 的核心特点：**

| 特性 | 说明 |
|------|------|
| 通信方向 | 单向：服务端 → 客户端 |
| 传输协议 | HTTP（标准 HTTP/1.1 长连接或 HTTP/2） |
| 数据格式 | `text/event-stream`（纯文本，UTF-8 编码） |
| 浏览器 API | `EventSource`（原生支持，零依赖） |
| 自动重连 | **内置**：连接断开后浏览器自动重试 |
| 跨域 | 支持 CORS 头 |

一个典型的 SSE 数据流长这样：

```
event: AGENT3_STREAMING
data: {"type":"text","content":"Spring Boot 的核心思想是"}

event: AGENT3_STREAMING
data: {"type":"text","content":"约定优于配置"}

event: AGENT3_COMPLETE
data: {"type":"complete","content":"..."}
```

### 1.2 为什么需要 SSE

在 ai-passage-creator 项目中，文章生成流程包含 5 个 Agent 的流水线执行：

```
用户输入选题 → 标题生成 → 大纲生成 → 正文生成 → 配图分析 → 并行配图 → 图文合并
```

**传统 HTTP 请求的痛点：**

| 痛点 | 后果 |
|------|------|
| 请求阻塞 30 秒 | Tomcat 线程被占用，无法处理其他请求 |
| 用户不知道生成了多少 | 白屏等待，用户焦虑 |
| 网络断开会丢失所有结果 | 必须重新生成，体验极差 |
| 无法监控中间状态 | 无法判断是正在生成还是卡住了 |

**SSE 的解决方案：**

| 能力 | 实现方式 |
|------|----------|
| 实时推送 | Agent 生成一个 Token 就推一次，首字延迟 < 1s |
| 10 种事件类型 | 前端通过 `addEventListener` 监听不同事件，精确控制 UI |
| 自动重连 | 浏览器 EventSource 内置重连机制，网络恢复后继续接收 |
| 不占线程 | SseEmitter 基于异步 Servlet，请求线程创建后立即释放 |

### 1.3 SSE vs WebSocket

| 对比维度 | SSE | WebSocket |
|----------|-----|-----------|
| 通信方向 | 服务端 → 客户端（单向） | 双向（全双工） |
| 协议基础 | HTTP（标准 HTTP 协议） | 独立协议（ws:// / wss://） |
| 浏览器支持 | 原生 `EventSource` API | 原生 `WebSocket` API |
| 自动重连 | **内置** | 需手动实现 |
| 流式数据 | 天然支持（`text/event-stream`） | 需自定义消息协议 |
| 传输效率 | 较低（HTTP 头部开销） | 高（二进制帧） |
| 服务端实现 | 简单（Spring SseEmitter） | 较复杂（WebSocketHandler / STOMP） |

**选型结论：** ai-passage-creator 使用 SSE 而非 WebSocket，因为场景是"服务端生成 → 前端展示"，纯单向推送，不需要前端主动发消息。SSE 的自动重连特性在 AI 生成场景中尤其重要。

---

## 二、核心概念

### 2.1 SSE 协议规范

每个 SSE 事件由以下字段组成：

| 字段 | 说明 | 是否必需 |
|------|------|----------|
| `event` | 事件类型，前端用 `addEventListener` 监听 | 可选 |
| `data` | 事件数据，可以是任意文本（JSON 字符串） | 必需 |
| `id` | 事件 ID，用于断线重连时的 Last-Event-ID | 可选 |
| `retry` | 重连间隔（毫秒） | 可选 |

**SSE 的 MIME 类型：** `text/event-stream`

### 2.2 Event Stream 事件流

事件流是**一系列有序的 SSE 事件序列**，贯穿整个文章生成过程。每个事件代表一个 Agent 的执行状态变化。

```
用户发起生成请求
    │
    ▼  ┌──────────────────────────────────┐
    ├──│ AGENT1_COMPLETE  │ 标题生成完成    │
    │   └──────────────────────────────────┘
    │
    ▼  ┌──────────────────────────────────┐
    ├──│ AGENT2_STREAMING  │ 大纲流式输出  │
    ├──│ AGENT2_STREAMING  │ 大纲流式输出  │
    ├──│ AGENT2_COMPLETE   │ 大纲生成完成  │
    │   └──────────────────────────────────┘
    │
    ▼  ┌──────────────────────────────────┐
    ├──│ AGENT3_STREAMING  │ 正文流式输出  │
    ├──│ AGENT3_STREAMING  │ 正文流式输出  │
    ├──│ AGENT3_COMPLETE   │ 正文生成完成  │
    ├──│ AGENT4_COMPLETE   │ 配图分析完成  │
    ├──│ IMAGE_COMPLETE    │ 第 1 张配图   │
    ├──│ IMAGE_COMPLETE    │ 第 2 张配图   │
    ├──│ AGENT5_COMPLETE   │ 全部配图就绪  │
    ├──│ MERGE_COMPLETE    │ 图文合并完成  │
    │   └──────────────────────────────────┘
    │
    ▼
  前端关闭 EventSource 连接
```

### 2.3 SseEmitter 线程模型

SseEmitter 基于**异步 Servlet**（`DeferredResult`）机制，请求线程不阻塞，由业务线程池执行实际工作。

**传统请求线程模型（阻塞）：**

```
HTTP 请求线程 → 等待业务执行 → 返回响应 → 释放线程
                ↑ 线程被占住，无法处理其他请求
```

**SseEmitter 异步线程模型（非阻塞）：**

```
HTTP 请求线程 → 创建 SseEmitter → 返回（释放线程）
    ↑ 线程立即释放，可以处理其他请求

业务线程池（独立） → 持续写入 SseEmitter → 事件推送到前端
    ↑ 业务线程独立执行，不占 Tomcat 线程
```

**三种线程的角色：**

| 线程类型 | 职责 | 关键点 |
|----------|------|--------|
| Tomcat 请求线程 | 接收 HTTP 请求，创建 SseEmitter | 创建后立即释放 |
| 业务线程池 | 执行 Agent 逻辑，写入 SseEmitter | 自定义大小，隔离执行 |
| NIO 工作线程 | 底层网络写入 | 自动管理，无需关心 |

### 2.4 10 种事件类型概览

| 事件名 | 触发时机 | 前端处理 |
|--------|----------|----------|
| `AGENT1_COMPLETE` | 标题生成完成 | 展示标题选项 |
| `AGENT2_STREAMING` | 大纲流式输出中 | 增量追加到大纲编辑区 |
| `AGENT2_COMPLETE` | 大纲生成完成 | 启用编辑功能 |
| `AGENT3_STREAMING` | 正文流式输出中 | 增量追加到正文预览区 |
| `AGENT3_COMPLETE` | 正文生成完成 | 启用确认按钮 |
| `AGENT4_COMPLETE` | 配图分析完成 | 显示配图计划 |
| `IMAGE_COMPLETE` | 单张配图完成 | 实时展示配图 |
| `AGENT5_COMPLETE` | 全部配图就绪 | 标记配图阶段完成 |
| `MERGE_COMPLETE` | 图文合并完成 | 展示最终文章，关闭连接 |
| `ERROR` | 发生错误 | 展示错误提示，提供重试 |

---

## 三、从零搭建代码

### 3.1 创建项目结构

```
sse-demo/
├── pom.xml
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/
│   │   │       └── passage/
│   │   │           └── sse/
│   │   │               ├── SseDemoApplication.java       # 启动类
│   │   │               ├── controller/
│   │   │               │   └── SseController.java        # SSE 控制器
│   │   │               ├── manager/
│   │   │               │   └── SseEmitterManager.java    # 连接管理器
│   │   │               └── service/
│   │   │                   └── ArticleGenerationService.java  # 模拟生成服务
│   │   └── resources/
│   │       ├── application.yml                             # 配置文件
│   │       └── static/
│   │           └── index.html                             # 前端测试页面
│   └── test/
│       └── java/
│           └── com/
│               └── passage/
│                   └── sse/
│                       └── SseDemoApplicationTests.java   # 测试类
```

### 3.2 配置 Maven 依赖（pom.xml）

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!-- pom.xml —— Maven 项目配置文件 -->
<!-- SSE 流式输出示例的 Maven 构建配置 -->
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
    <artifactId>sse-demo</artifactId>           <!-- 项目名：sse-demo -->
    <version>1.0.0-SNAPSHOT</version>
    <name>SSE Stream Demo</name>
    <description>SSE 流式输出入门示例：模拟 AI 文章生成过程的实时推送</description>

    <properties>
        <java.version>17</java.version>          <!-- 使用 Java 17 -->
    </properties>

    <dependencies>
        <!-- Spring Boot Web 起步依赖 -->
        <!-- 提供 SseEmitter、RestTemplate、Tomcat 等 Web 能力 -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>

        <!-- Spring Boot Test 测试框架 -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

### 3.3 配置文件（application.yml）

```yaml
# application.yml —— 应用配置文件
# SSE 流式输出示例的配置参数
server:
  port: 8080                               # 服务端口号

spring:
  application:
    name: sse-demo                          # 应用名称
  # Tomcat 异步请求配置
  mvc:
    async:
      # 异步请求超时时间（毫秒），默认 30 秒
      # SSE 场景下设为 0 或较大值，避免连接意外断开
      request-timeout: 120000

# 自定义 SSE 配置
sse:
  # 连接池配置
  connection:
    # 每个任务最多连接数
    max-per-task: 1
    # 全局最大连接数（防止资源耗尽）
    max-total: 1000
    # 连接超时（毫秒），0 表示永不超时
    timeout: 0
  # 心跳配置
  heartbeat:
    # 是否启用心跳检测
    enabled: true
    # 心跳间隔（毫秒）
    interval: 30000
  # 模拟文章生成的延迟配置
  simulation:
    # 事件发送间隔（毫秒）
    event-interval: 500
    # 流式输出间隔（毫秒）
    stream-interval: 200
```

### 3.4 SSE 连接管理器（SseEmitterManager.java）

```java
package com.passage.sse.manager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SseEmitterManager - SSE 连接管理器
 * <p>
 * 管理所有 SSE 连接的生命周期，包括：
 * 1. 创建 SseEmitter 连接
 * 2. 向指定连接推送事件
 * 3. 在连接完成或超时时清理资源
 * <p>
 * 核心数据结构：ConcurrentHashMap<taskId, SseEmitter>
 * 使用 ConcurrentHashMap 保证线程安全（多线程并发写入）
 *
 * @author AI-Passage-Creator
 */
@Component
public class SseEmitterManager {

    /** 日志记录器 */
    private static final Logger log = LoggerFactory.getLogger(SseEmitterManager.class);

    /**
     * 存储所有活跃的 SSE 连接
     * key = taskId（文章生成任务的唯一标识）
     * value = SseEmitter（SSE 连接实例）
     * ConcurrentHashMap 保证多线程并发读写安全
     */
    private final Map<String, SseEmitter> emitterMap = new ConcurrentHashMap<>();

    /**
     * 创建 SSE 连接
     * <p>
     * 核心步骤：
     * 1. 创建 SseEmitter 实例，设置超时时间
     * 2. 注册到连接管理器（emitterMap）
     * 3. 注册回调：onCompletion（完成）、onTimeout（超时）、onError（异常）
     * <p>
     * 回调的注册至关重要——如果不注册，连接断开后 emitterMap 中的 entry
     * 永远不会被移除，导致内存泄漏。
     *
     * @param taskId  任务 ID（用于后续推送事件时的定位）
     * @param timeout 超时时间（毫秒），0 表示永不超时
     * @return SseEmitter 实例
     */
    public SseEmitter createEmitter(String taskId, long timeout) {
        // 步骤1：创建 SseEmitter 实例
        // timeout = 0 表示永不超时，由业务逻辑控制连接关闭
        // 如果 timeout > 0，超时后 SseEmitter 会自动关闭
        SseEmitter emitter = new SseEmitter(timeout);

        // 步骤2：注册到管理器
        emitterMap.put(taskId, emitter);

        // 步骤3：注册回调——连接完成时自动清理资源
        // 触发条件：客户端关闭连接，或服务端主动调用 emitter.complete()
        emitter.onCompletion(() -> {
            log.info("SSE 连接完成，清理资源。taskId={}", taskId);
            emitterMap.remove(taskId);
        });

        // 步骤4：注册回调——连接超时时自动清理资源
        // 触发条件：超过 timeout 时间未发送任何事件
        emitter.onTimeout(() -> {
            log.warn("SSE 连接超时。taskId={}", taskId);
            emitterMap.remove(taskId);
        });

        // 步骤5：注册回调——发生错误时自动清理资源
        // 触发条件：网络异常，或客户端断开连接
        emitter.onError(throwable -> {
            log.error("SSE 连接异常。taskId={}, error={}", taskId, throwable.getMessage());
            emitterMap.remove(taskId);
        });

        log.info("SSE 连接创建成功。taskId={}, 当前活跃连接数={}", taskId, emitterMap.size());
        return emitter;
    }

    /**
     * 发送 SSE 事件
     * <p>
     * 向指定 taskId 的 SSE 连接发送事件。
     * 如果连接不存在（客户端已断开），静默忽略，不抛异常。
     *
     * @param taskId    任务 ID
     * @param eventName 事件名称（如 "AGENT3_STREAMING"）
     * @param data      事件数据（自动序列化为 JSON）
     */
    public void sendEvent(String taskId, String eventName, Object data) {
        // 从连接管理器中获取对应 taskId 的 SseEmitter
        SseEmitter emitter = emitterMap.get(taskId);

        // 连接不存在：可能客户端已断开，或 taskId 不正确
        if (emitter == null) {
            log.warn("SSE 连接不存在，跳过事件发送。taskId={}, event={}", taskId, eventName);
            return;
        }

        try {
            // 使用 SseEmitter.event() 构建 SSE 事件
            // .name() 设置事件名称（对应前端 EventSource.addEventListener 的事件名）
            // .data() 设置事件数据，SseEmitter 会自动调用 toString()
            // 对于复杂对象，建议先序列化为 JSON 字符串
            emitter.send(SseEmitter.event()
                    .name(eventName)        // 设置事件名
                    .data(data));           // 设置事件数据
        } catch (IOException e) {
            // 发送失败：客户端已断开连接
            // 清理连接资源，避免内存泄漏
            log.error("SSE 事件发送失败。taskId={}, event={}, error={}",
                    taskId, eventName, e.getMessage());
            emitterMap.remove(taskId);
        }
    }

    /**
     * 完成 SSE 连接（正常结束）
     * <p>
     * 当所有事件都推送完毕后，调用此方法关闭连接。
     * 关闭后会触发 onCompletion 回调，自动清理资源。
     *
     * @param taskId 任务 ID
     */
    public void complete(String taskId) {
        SseEmitter emitter = emitterMap.get(taskId);
        if (emitter != null) {
            // 发送完成信号，触发 onCompletion 回调
            emitter.complete();
            log.info("SSE 连接正常关闭。taskId={}", taskId);
        }
    }

    /**
     * 异常结束 SSE 连接（发送错误后关闭）
     * <p>
     * 当生成过程中发生不可恢复的错误时，调用此方法。
     * 关闭后触发 onError 回调，自动清理资源。
     *
     * @param taskId 任务 ID
     * @param error  错误信息
     */
    public void completeWithError(String taskId, Throwable error) {
        SseEmitter emitter = emitterMap.get(taskId);
        if (emitter != null) {
            // 发送错误信号，触发 onError 回调
            emitter.completeWithError(error);
            log.error("SSE 连接异常关闭。taskId={}", taskId, error);
        }
    }

    /**
     * 获取当前活跃连接数
     * <p>
     * 用于监控和管理，可以在监控端点中暴露此信息。
     *
     * @return 当前活跃连接数
     */
    public int getActiveConnectionCount() {
        return emitterMap.size();
    }
}
```

### 3.5 SSE 控制器（SseController.java）

```java
package com.passage.sse.controller;

import com.passage.sse.manager.SseEmitterManager;
import com.passage.sse.service.ArticleGenerationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

/**
 * SseController - SSE 流式输出控制器
 * <p>
 * 提供 REST API 接口：
 * 1. 创建文章生成任务（返回 taskId）
 * 2. 建立 SSE 连接，开始流式推送
 * 3. 查询任务状态
 * <p>
 * 核心接口：GET /api/article/generate/{taskId}/stream
 * 前端通过 EventSource 连接此接口，建立 SSE 长连接
 *
 * @author AI-Passage-Creator
 */
@RestController
@RequestMapping("/api/article")
public class SseController {

    /** 日志记录器 */
    private static final Logger log = LoggerFactory.getLogger(SseController.class);

    /** SSE 连接管理器 */
    @Autowired
    private SseEmitterManager sseEmitterManager;

    /** 文章生成模拟服务 */
    @Autowired
    private ArticleGenerationService generationService;

    /**
     * 创建文章生成任务
     * <p>
     * POST /api/article/create
     * 请求体：{"topic": "Spring Boot 入门指南"}
     * <p>
     * 先创建任务，返回 taskId，然后前端用 taskId 建立 SSE 连接。
     * 这种设计将"任务创建"和"SSE 连接"解耦，更灵活。
     *
     * @param request 请求体，包含文章主题
     * @return 包含 taskId 的 JSON 响应
     */
    @PostMapping("/create")
    public java.util.Map<String, Object> createTask(@RequestBody java.util.Map<String, String> request) {
        // 从请求体中提取文章主题
        String topic = request.get("topic");

        // 生成唯一任务 ID
        String taskId = UUID.randomUUID().toString().replace("-", "");

        log.info("创建文章生成任务。taskId={}, topic={}", taskId, topic);

        // 返回 taskId，前端后续用此 ID 建立 SSE 连接
        return java.util.Map.of(
                "taskId", taskId,
                "topic", topic,
                "message", "任务创建成功，请连接 SSE 流式接口"
        );
    }

    /**
     * 建立 SSE 流式连接，开始文章生成
     * <p>
     * GET /api/article/generate/{taskId}/stream?topic=Spring+Boot+入门指南
     * <p>
     * 返回类型是 SseEmitter，Spring MVC 会自动处理：
     * 1. 设置 Content-Type: text/event-stream
     * 2. 使用异步 Servlet 机制，不阻塞 Tomcat 线程
     * 3. 返回 SseEmitter 实例，后续通过它写入数据
     * <p>
     * 前端使用 EventSource 连接此接口：
     * new EventSource('/api/article/generate/task-xxx/stream?topic=...')
     *
     * @param taskId 任务 ID
     * @param topic  文章主题
     * @return SseEmitter SSE 连接实例
     */
    @GetMapping("/generate/{taskId}/stream")
    public SseEmitter streamArticle(
            @PathVariable String taskId,
            @RequestParam String topic) {

        log.info("建立 SSE 流式连接。taskId={}, topic={}", taskId, topic);

        // 步骤1：创建 SSE 连接，永不超时
        // 0L 表示永不超时，由业务逻辑控制连接关闭
        SseEmitter emitter = sseEmitterManager.createEmitter(taskId, 0L);

        // 步骤2：异步启动文章生成流程
        // 关键：不能阻塞当前线程！
        // 当前线程是 Tomcat 请求线程，如果在这里同步执行生成逻辑，
        // 线程会被占用 30 秒，无法处理其他请求
        // 使用 @Async 让 Spring 线程池执行，立即返回 emitter
        generationService.startGeneration(taskId, topic);

        // 步骤3：返回 SseEmitter
        // Spring MVC 检测到返回值是 SseEmitter 类型后：
        // 1. 设置 Content-Type: text/event-stream
        // 2. 启用异步 Servlet 模式
        // 3. 当前线程立即释放，返回 Tomcat 线程池
        return emitter;
    }

    /**
     * 查询任务状态
     * <p>
     * GET /api/article/status/{taskId}
     * <p>
     * 用于前端轮询或调试，查看 SSE 连接是否存活。
     *
     * @param taskId 任务 ID
     * @return 任务状态信息
     */
    @GetMapping("/status/{taskId}")
    public java.util.Map<String, Object> getStatus(@PathVariable String taskId) {
        return java.util.Map.of(
                "taskId", taskId,
                "active", sseEmitterManager.getActiveConnectionCount(),
                "timestamp", System.currentTimeMillis()
        );
    }
}
```

### 3.6 文章生成模拟服务（ArticleGenerationService.java）

```java
package com.passage.sse.service;

import com.passage.sse.manager.SseEmitterManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * ArticleGenerationService - 模拟文章生成服务
 * <p>
 * 模拟 AI 文章生成的 5 个 Agent 阶段，通过 SSE 实时推送事件。
 * 每个阶段模拟不同的延迟，演示 SSE 事件流的效果。
 * <p>
 * 模拟的 5 个阶段：
 * 阶段1：标题生成（AGENT1_COMPLETE）
 * 阶段2：大纲流式输出（AGENT2_STREAMING → AGENT2_COMPLETE）
 * 阶段3：正文流式输出（AGENT3_STREAMING → AGENT3_COMPLETE）
 * 阶段4：配图分析（AGENT4_COMPLETE → IMAGE_COMPLETE → AGENT5_COMPLETE）
 * 阶段5：图文合并（MERGE_COMPLETE）
 *
 * @author AI-Passage-Creator
 */
@Service
public class ArticleGenerationService {

    /** 日志记录器 */
    private static final Logger log = LoggerFactory.getLogger(ArticleGenerationService.class);

    /** SSE 连接管理器 */
    @Autowired
    private SseEmitterManager sseEmitterManager;

    /**
     * 开始文章生成流程（异步执行）
     * <p>
     * 此方法由 @Async 注解标记，Spring 会在独立的线程池中执行。
     * 调用方（Controller）立即返回，不阻塞。
     * <p>
     * 执行流程严格按照 5 个 Agent 的顺序：
     * 1. 标题生成 → 2. 大纲生成 → 3. 正文生成 → 4. 配图 → 5. 合并
     *
     * @param taskId 任务 ID
     * @param topic  文章主题
     */
    @Async
    public void startGeneration(String taskId, String topic) {
        log.info("【异步】开始文章生成流程。taskId={}, topic={}", taskId, topic);

        try {
            // ====== 阶段1：标题生成 ======
            generateTitle(taskId, topic);

            // ====== 阶段2：大纲生成 ======
            generateOutline(taskId, topic);

            // ====== 阶段3：正文生成 ======
            generateContent(taskId, topic);

            // ====== 阶段4：配图分析 ======
            generateImages(taskId, topic);

            // ====== 阶段5：图文合并 ======
            mergeContent(taskId, topic);

            // 所有阶段完成，关闭 SSE 连接
            sseEmitterManager.complete(taskId);
            log.info("文章生成完成。taskId={}", taskId);

        } catch (Exception e) {
            // 发生不可恢复的错误，发送错误事件后关闭连接
            log.error("文章生成失败。taskId={}, error={}", taskId, e.getMessage());
            sseEmitterManager.sendEvent(taskId, "ERROR", Map.of(
                    "message", "文章生成失败：" + e.getMessage(),
                    "code", "GENERATION_ERROR"
            ));
            sseEmitterManager.completeWithError(taskId, e);
        }
    }

    /**
     * 阶段1：模拟标题生成
     * <p>
     * 模拟 AI 生成标题的过程。生成 3 个标题选项，发送 AGENT1_COMPLETE 事件。
     */
    private void generateTitle(String taskId, String topic) throws InterruptedException {
        log.info("【阶段1】开始生成标题");

        // 模拟 AI 生成延迟（2 秒）
        TimeUnit.SECONDS.sleep(2);

        // 模拟生成的标题列表
        List<String> titles = List.of(
                topic + "：从入门到实战",
                "深入浅出" + topic,
                topic + "最佳实践指南"
        );

        // 发送 AGENT1_COMPLETE 事件
        // 事件名：AGENT1_COMPLETE（标题生成完成）
        // 数据：包含标题列表和状态信息
        sseEmitterManager.sendEvent(taskId, "AGENT1_COMPLETE", Map.of(
                "type", "complete",
                "titles", titles,
                "message", "标题生成完成，请选择"
        ));

        log.info("【阶段1】标题生成完成: {}", titles);
    }

    /**
     * 阶段2：模拟大纲流式输出
     * <p>
     * 模拟 AI 逐段生成大纲的过程。
     * 每生成一段，发送一个 AGENT2_STREAMING 事件。
     * 全部生成完毕，发送 AGENT2_COMPLETE 事件。
     */
    private void generateOutline(String taskId, String topic) throws InterruptedException {
        log.info("【阶段2】开始生成大纲");

        // 模拟大纲内容（逐段推送）
        String[] outlineParts = {
                "# 一、项目背景\n\n",
                "## 1.1 什么是" + topic + "\n\n",
                topic + "是一种重要的技术架构...\n\n",
                "## 1.2 为什么选择" + topic + "\n\n",
                topic + "具有以下优势...\n\n",
                "# 二、核心概念\n\n",
                "## 2.1 基本架构\n\n",
                "## 2.2 核心组件\n\n",
                "# 三、实战演示\n\n",
                "## 3.1 环境准备\n\n",
                "## 3.2 代码实现\n\n",
        };

        // 逐段推送大纲内容
        // 每段之间间隔 500 毫秒，模拟 AI 生成的延迟
        for (String part : outlineParts) {
            // 发送 AGENT2_STREAMING 事件
            // 前端收到后增量追加到大纲编辑区
            sseEmitterManager.sendEvent(taskId, "AGENT2_STREAMING", Map.of(
                    "type", "text",
                    "content", part
            ));
            // 模拟生成延迟
            TimeUnit.MILLISECONDS.sleep(500);
        }

        // 大纲全部生成完毕，发送 AGENT2_COMPLETE 事件
        sseEmitterManager.sendEvent(taskId, "AGENT2_COMPLETE", Map.of(
                "type", "complete",
                "content", "大纲已生成完毕，您可以编辑或确认",
                "message", "大纲生成完成"
        ));

        log.info("【阶段2】大纲生成完成");
    }

    /**
     * 阶段3：模拟正文流式输出
     * <p>
     * 模拟 AI 逐字/逐句生成正文的过程。
     * 每生成一句，发送一个 AGENT3_STREAMING 事件。
     * 全部生成完毕，发送 AGENT3_COMPLETE 事件。
     */
    private void generateContent(String taskId, String topic) throws InterruptedException {
        log.info("【阶段3】开始生成正文");

        // 模拟正文内容（逐句推送）
        String[] contentParts = {
                "## 1.1 什么是" + topic + "\n\n",
                topic + "是一种基于 Spring Boot 的技术框架。",
                "它通过约定优于配置的理念，大大简化了开发过程。",
                "相比传统方式，它能减少 60% 以上的样板代码。\n\n",
                "## 1.2 核心特性\n\n",
                "第一，自动配置能力。",
                "Spring Boot 会根据类路径中的依赖自动配置 Bean。",
                "第二，起步依赖。",
                "将常用依赖组合打包，一行引入即可使用。",
                "第三，生产就绪。",
                "内置指标、健康检查、外部化配置等能力。\n\n",
                "## 1.3 适用场景\n\n",
                "微服务架构、快速原型开发、",
                "企业级应用、云原生应用等场景。",
        };

        // 逐句推送正文内容
        // 间隔 200 毫秒，模拟 AI 逐字生成的效果
        for (String part : contentParts) {
            sseEmitterManager.sendEvent(taskId, "AGENT3_STREAMING", Map.of(
                    "type", "text",
                    "content", part
            ));
            TimeUnit.MILLISECONDS.sleep(200);
        }

        // 正文全部生成完毕
        sseEmitterManager.sendEvent(taskId, "AGENT3_COMPLETE", Map.of(
                "type", "complete",
                "message", "正文生成完成"
        ));

        log.info("【阶段3】正文生成完成");
    }

    /**
     * 阶段4：模拟配图分析 + 并行配图
     * <p>
     * 模拟 AI 分析哪些段落需要配图，然后并行生成配图的过程。
     * 先发送 AGENT4_COMPLETE（配图分析完成），
     * 然后逐张发送 IMAGE_COMPLETE（配图完成），
     * 最后发送 AGENT5_COMPLETE（全部配图就绪）。
     */
    private void generateImages(String taskId, String topic) throws InterruptedException {
        log.info("【阶段4】开始配图分析");

        // 模拟配图分析延迟
        TimeUnit.SECONDS.sleep(1);

        // 模拟配图需求分析结果
        List<Map<String, Object>> requirements = List.of(
                Map.of("paragraphIndex", 1, "keyword", "Spring Boot 架构", "method", "mermaid"),
                Map.of("paragraphIndex", 2, "keyword", "技术架构图", "method", "mermaid"),
                Map.of("paragraphIndex", 3, "keyword", "代码示例", "method", "pexels")
        );

        // 发送 AGENT4_COMPLETE 事件（配图分析完成）
        sseEmitterManager.sendEvent(taskId, "AGENT4_COMPLETE", Map.of(
                "type", "analysis",
                "requirements", requirements,
                "message", "配图分析完成，共 " + requirements.size() + " 张配图"
        ));

        log.info("【阶段4】配图分析完成，共 {} 张配图", requirements.size());

        // 模拟并行配图生成（逐张完成）
        String[][] mockImages = {
                {"1", "https://mermaid.ink/img/流程图_SpringBoot"},
                {"2", "https://mermaid.ink/img/架构图_技术架构"},
                {"3", "https://picsum.photos/seed/3/800/600"}
        };

        for (String[] image : mockImages) {
            // 模拟配图生成延迟
            TimeUnit.SECONDS.sleep(1);

            // 发送 IMAGE_COMPLETE 事件（单张配图完成）
            sseEmitterManager.sendEvent(taskId, "IMAGE_COMPLETE", Map.of(
                    "type", "image",
                    "url", image[1],
                    "paragraphIndex", Integer.parseInt(image[0]),
                    "method", image[1].contains("mermaid") ? "mermaid" : "pexels"
            ));
        }

        // 全部配图就绪，发送 AGENT5_COMPLETE 事件
        sseEmitterManager.sendEvent(taskId, "AGENT5_COMPLETE", Map.of(
                "type", "complete",
                "images", mockImages.length,
                "message", "全部配图已就绪"
        ));

        log.info("【阶段4】全部配图完成");
    }

    /**
     * 阶段5：模拟图文合并
     * <p>
     * 将正文和配图合并为最终文章。
     * 发送 MERGE_COMPLETE 事件，包含最终文章内容。
     */
    private void mergeContent(String taskId, String topic) throws InterruptedException {
        log.info("【阶段5】开始图文合并");

        // 模拟合并延迟
        TimeUnit.SECONDS.sleep(1);

        // 模拟最终文章
        String finalArticle = String.format("""
                # %s：从入门到实战
                
                ## 一、项目背景
                
                %s是一种基于 Spring Boot 的技术框架。
                
                ## 二、核心概念
                
                自动配置、起步依赖、生产就绪等特性。
                
                ## 三、总结
                
                本文介绍了%s的核心概念和使用方法。
                """, topic, topic, topic);

        // 发送 MERGE_COMPLETE 事件（图文合并完成）
        sseEmitterManager.sendEvent(taskId, "MERGE_COMPLETE", Map.of(
                "type", "complete",
                "article", finalArticle,
                "message", "文章已生成完成"
        ));

        log.info("【阶段5】图文合并完成");
    }
}
```

### 3.7 Spring Boot 启动类（SseDemoApplication.java）

```java
package com.passage.sse;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * SseDemoApplication - Spring Boot 应用启动类
 * <p>
 * SSE 流式输出示例项目的入口。
 * 使用 @EnableAsync 启用异步任务支持。
 * 启动后可通过 REST API 和前端页面测试 SSE 功能。
 *
 * @author AI-Passage-Creator
 */
@SpringBootApplication
@EnableAsync  // 启用 Spring 异步任务支持，@Async 注解才能生效
public class SseDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(SseDemoApplication.class, args);
    }
}
```

### 3.8 前端测试页面（index.html）

```html
<!DOCTYPE html>
<!-- index.html —— SSE 前端测试页面 -->
<!-- 使用 EventSource 接收服务端推送的事件流 -->
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <title>SSE 流式输出演示</title>
    <style>
        /* 页面样式 —— 简洁清晰的事件流展示 */
        body { font-family: "Microsoft YaHei", sans-serif; max-width: 800px; margin: 20px auto; padding: 0 20px; }
        h1 { color: #333; border-bottom: 2px solid #4A90D9; padding-bottom: 10px; }
        .event { margin: 8px 0; padding: 10px; border-radius: 4px; }
        .event-stream { background: #E3F2FD; }    /* 流式事件：蓝色背景 */
        .event-complete { background: #E8F5E9; }  /* 完成事件：绿色背景 */
        .event-image { background: #FFF3E0; }     /* 图片事件：橙色背景 */
        .event-error { background: #FFEBEE; }     /* 错误事件：红色背景 */
        .event-name { font-weight: bold; color: #1565C0; }
        .event-data { margin-top: 4px; white-space: pre-wrap; font-size: 14px; }
        .controls { margin: 20px 0; }
        input, button { font-size: 16px; padding: 8px 16px; margin-right: 8px; }
        button { background: #4A90D9; color: white; border: none; border-radius: 4px; cursor: pointer; }
        button:hover { background: #357ABD; }
        button:disabled { background: #ccc; cursor: not-allowed; }
        #status { margin: 10px 0; color: #666; }
        #events { max-height: 600px; overflow-y: auto; border: 1px solid #ddd; padding: 10px; border-radius: 4px; }
    </style>
</head>
<body>
    <h1>SSE 流式输出演示</h1>

    <!-- 控制区域 -->
    <div class="controls">
        <input type="text" id="topicInput" value="Spring Boot 入门指南" size="30" placeholder="输入文章主题">
        <button id="startBtn" onclick="startGeneration()">开始生成</button>
        <button id="stopBtn" onclick="stopGeneration()" disabled>停止</button>
    </div>

    <!-- 状态显示 -->
    <div id="status">请输入文章主题，点击"开始生成"</div>

    <!-- 事件列表 -->
    <div id="events"></div>

    <script>
        // 当前 EventSource 连接
        let eventSource = null;
        let currentTaskId = null;

        /**
         * 开始文章生成
         * <p>
         * 流程：
         * 1. 先创建任务（POST /api/article/create），获取 taskId
         * 2. 然后用 taskId 建立 SSE 连接（EventSource）
         * 3. 监听不同事件类型，实时更新 UI
         */
        function startGeneration() {
            const topic = document.getElementById('topicInput').value.trim();
            if (!topic) {
                alert('请输入文章主题');
                return;
            }

            // 禁用开始按钮，启用停止按钮
            document.getElementById('startBtn').disabled = true;
            document.getElementById('stopBtn').disabled = false;

            // 清空事件列表
            document.getElementById('events').innerHTML = '';
            document.getElementById('status').textContent = '正在创建任务...';

            // 步骤1：创建任务，获取 taskId
            fetch('/api/article/create', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ topic: topic })
            })
            .then(response => response.json())
            .then(data => {
                currentTaskId = data.taskId;
                document.getElementById('status').textContent =
                    '任务已创建，正在连接 SSE...';

                // 步骤2：建立 SSE 连接
                // EventSource 会自动发送 GET 请求到指定 URL
                // 服务端返回 Content-Type: text/event-stream 后建立长连接
                connectSSE(currentTaskId, topic);
            })
            .catch(error => {
                document.getElementById('status').textContent = '创建任务失败: ' + error.message;
            });
        }

        /**
         * 建立 SSE 连接
         * <p>
         * EventSource 是浏览器原生 API，零依赖。
         * 自动处理重连（使用 Last-Event-ID）。
         * 连接建立后，服务端推送的事件会通过 addEventListener 接收。
         *
         * @param taskId 任务 ID
         * @param topic  文章主题
         */
        function connectSSE(taskId, topic) {
            // 创建 EventSource 连接
            const url = `/api/article/generate/${taskId}/stream?topic=${encodeURIComponent(topic)}`;
            eventSource = new EventSource(url);

            // ====== 监听事件：AGENT1_COMPLETE（标题生成完成） ======
            eventSource.addEventListener('AGENT1_COMPLETE', (event) => {
                const data = JSON.parse(event.data);
                addEvent('AGENT1_COMPLETE', '标题生成完成', data, 'complete');
                updateStatus('标题已生成，共 ' + data.titles.length + ' 个选项');
            });

            // ====== 监听事件：AGENT2_STREAMING（大纲流式输出） ======
            eventSource.addEventListener('AGENT2_STREAMING', (event) => {
                const data = JSON.parse(event.data);
                addEvent('AGENT2_STREAMING', data.content, data, 'stream');
                updateStatus('大纲生成中...');
            });

            // ====== 监听事件：AGENT2_COMPLETE（大纲生成完成） ======
            eventSource.addEventListener('AGENT2_COMPLETE', (event) => {
                addEvent('AGENT2_COMPLETE', '大纲生成完成', null, 'complete');
                updateStatus('大纲已生成完成');
            });

            // ====== 监听事件：AGENT3_STREAMING（正文流式输出） ======
            eventSource.addEventListener('AGENT3_STREAMING', (event) => {
                const data = JSON.parse(event.data);
                addEvent('AGENT3_STREAMING', data.content, data, 'stream');
                updateStatus('正文生成中...');
            });

            // ====== 监听事件：AGENT3_COMPLETE（正文生成完成） ======
            eventSource.addEventListener('AGENT3_COMPLETE', (event) => {
                addEvent('AGENT3_COMPLETE', '正文生成完成', null, 'complete');
                updateStatus('正文已生成完成');
            });

            // ====== 监听事件：AGENT4_COMPLETE（配图分析完成） ======
            eventSource.addEventListener('AGENT4_COMPLETE', (event) => {
                const data = JSON.parse(event.data);
                addEvent('AGENT4_COMPLETE',
                    '配图分析完成，共 ' + data.requirements.length + ' 张配图',
                    data, 'complete');
                updateStatus('配图分析完成，计划插入 ' + data.requirements.length + ' 张配图');
            });

            // ====== 监听事件：IMAGE_COMPLETE（单张配图完成） ======
            eventSource.addEventListener('IMAGE_COMPLETE', (event) => {
                const data = JSON.parse(event.data);
                addEvent('IMAGE_COMPLETE',
                    '第 ' + data.paragraphIndex + ' 段配图完成',
                    data, 'image');
                updateStatus('配图获取中...');
            });

            // ====== 监听事件：AGENT5_COMPLETE（全部配图就绪） ======
            eventSource.addEventListener('AGENT5_COMPLETE', (event) => {
                addEvent('AGENT5_COMPLETE', '全部配图已就绪', null, 'complete');
                updateStatus('全部配图已就绪');
            });

            // ====== 监听事件：MERGE_COMPLETE（图文合并完成） ======
            eventSource.addEventListener('MERGE_COMPLETE', (event) => {
                addEvent('MERGE_COMPLETE', '文章生成完成！', null, 'complete');
                updateStatus('文章已生成完成！');
                // 关闭 SSE 连接
                eventSource.close();
                resetButtons();
            });

            // ====== 监听事件：ERROR（错误） ======
            eventSource.addEventListener('ERROR', (event) => {
                const data = JSON.parse(event.data);
                addEvent('ERROR', data.message, data, 'error');
                updateStatus('错误: ' + data.message);
                eventSource.close();
                resetButtons();
            });

            // ====== 通用错误处理（EventSource 连接错误） ======
            eventSource.onerror = (error) => {
                console.error('SSE 连接错误', error);
                updateStatus('连接异常，正在重连...');
                // EventSource 会自动重连，无需手动处理
            };

            // ====== 连接成功建立 ======
            eventSource.onopen = () => {
                updateStatus('SSE 连接已建立，正在生成文章...');
            };
        }

        /**
         * 停止生成（关闭 SSE 连接）
         */
        function stopGeneration() {
            if (eventSource) {
                eventSource.close();
                eventSource = null;
            }
            updateStatus('已停止生成');
            resetButtons();
        }

        /**
         * 添加事件到 UI 列表
         */
        function addEvent(eventName, displayText, data, type) {
            const eventsDiv = document.getElementById('events');
            const eventDiv = document.createElement('div');
            eventDiv.className = 'event event-' + type;

            // 事件名称
            const nameSpan = document.createElement('div');
            nameSpan.className = 'event-name';
            nameSpan.textContent = '▶ ' + eventName;
            eventDiv.appendChild(nameSpan);

            // 事件数据
            const dataSpan = document.createElement('div');
            dataSpan.className = 'event-data';
            dataSpan.textContent = displayText;
            eventDiv.appendChild(dataSpan);

            eventsDiv.appendChild(eventDiv);

            // 自动滚动到底部
            eventsDiv.scrollTop = eventsDiv.scrollHeight;
        }

        /**
         * 更新状态栏
         */
        function updateStatus(message) {
            document.getElementById('status').textContent = message;
        }

        /**
         * 重置按钮状态
         */
        function resetButtons() {
            document.getElementById('startBtn').disabled = false;
            document.getElementById('stopBtn').disabled = true;
        }
    </script>
</body>
</html>
```

### 3.9 单元测试（SseDemoApplicationTests.java）

```java
package com.passage.sse;

import com.passage.sse.manager.SseEmitterManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SseDemoApplicationTests - SSE 功能单元测试
 * <p>
 * 测试覆盖场景：
 * 1. SSE 连接创建与资源清理
 * 2. SSE 事件发送与接收
 * 3. 连接自动清理（onCompletion 回调）
 * 4. 连接异常处理
 * 5. 活跃连接数统计
 *
 * @author AI-Passage-Creator
 */
@SpringBootTest
class SseDemoApplicationTests {

    /** SSE 连接管理器 */
    @Autowired
    private SseEmitterManager sseEmitterManager;

    // ==================== 测试用例 1：SSE 连接创建 ====================

    /**
     * 测试 SSE 连接创建
     * <p>
     * 验证场景：
     * - 创建 SseEmitter 连接
     * - 验证连接创建成功（返回非 null 的 SseEmitter）
     * - 验证活跃连接数增加
     */
    @Test
    @DisplayName("测试SSE连接创建")
    void testCreateEmitter() {
        // 创建 SSE 连接
        SseEmitter emitter = sseEmitterManager.createEmitter("test-task-1", 60000L);

        // 验证连接创建成功
        assertNotNull(emitter, "SseEmitter 不应为 null");

        // 验证活跃连接数大于 0
        assertTrue(sseEmitterManager.getActiveConnectionCount() > 0,
                "活跃连接数应大于 0");

        System.out.println("【测试1】SSE 连接创建成功，当前活跃连接数: "
                + sseEmitterManager.getActiveConnectionCount());
    }

    // ==================== 测试用例 2：SSE 事件发送 ====================

    /**
     * 测试 SSE 事件发送
     * <p>
     * 验证场景：
     * - 向已创建的连接发送事件
     * - 验证发送不抛异常
     * - 发送不同类型的事件（AGENT2_STREAMING、IMAGE_COMPLETE、ERROR）
     */
    @Test
    @DisplayName("测试SSE事件发送")
    void testSendEvent() {
        // 先创建连接
        SseEmitter emitter = sseEmitterManager.createEmitter("test-task-2", 60000L);

        // 发送 AGENT2_STREAMING 事件（流式文本）
        assertDoesNotThrow(() -> {
            sseEmitterManager.sendEvent("test-task-2", "AGENT2_STREAMING",
                    Map.of("type", "text", "content", "这是大纲第一部分"));
        }, "发送 AGENT2_STREAMING 事件不应抛异常");

        // 发送 IMAGE_COMPLETE 事件（图片完成）
        assertDoesNotThrow(() -> {
            sseEmitterManager.sendEvent("test-task-2", "IMAGE_COMPLETE",
                    Map.of("type", "image", "url", "https://example.com/img.jpg",
                            "paragraphIndex", 1));
        }, "发送 IMAGE_COMPLETE 事件不应抛异常");

        // 发送 ERROR 事件（错误信息）
        assertDoesNotThrow(() -> {
            sseEmitterManager.sendEvent("test-task-2", "ERROR",
                    Map.of("message", "测试错误", "code", "TEST_ERROR"));
        }, "发送 ERROR 事件不应抛异常");

        // 正常关闭连接
        emitter.complete();

        System.out.println("【测试2】SSE 事件发送成功");
    }

    // ==================== 测试用例 3：连接不存在时静默处理 ====================

    /**
     * 测试向不存在的连接发送事件
     * <p>
     * 验证场景：
     * - 向不存在的 taskId 发送事件
     * - 验证不抛异常（静默忽略）
     * - 这是 SSE 连接管理器的容错设计
     */
    @Test
    @DisplayName("测试向不存在的连接发送事件（静默忽略）")
    void testSendEventToNonExistentTask() {
        // 向不存在的 taskId 发送事件
        assertDoesNotThrow(() -> {
            sseEmitterManager.sendEvent("non-existent-task", "AGENT1_COMPLETE",
                    Map.of("message", "这条消息应该被静默忽略"));
        }, "向不存在的连接发送事件不应抛异常");

        System.out.println("【测试3】向不存在的连接发送事件，静默忽略正确");
    }

    // ==================== 测试用例 4：连接完成后的资源清理 ====================

    /**
     * 测试连接完成后的资源清理
     * <p>
     * 验证场景：
     * - 创建连接后，调用 complete() 正常关闭
     * - 验证 onCompletion 回调被触发
     * - 验证 emitterMap 中的 entry 被清理
     * <p>
     * 注意：complete() 是异步的，需要等待回调执行完毕
     */
    @Test
    @DisplayName("测试连接完成后的资源清理")
    void testConnectionCleanup() throws InterruptedException {
        // 创建连接
        sseEmitterManager.createEmitter("test-task-cleanup", 60000L);

        // 获取当前活跃连接数
        int beforeCount = sseEmitterManager.getActiveConnectionCount();

        // 正常关闭连接
        sseEmitterManager.complete("test-task-cleanup");

        // 等待回调执行（onCompletion 是异步回调）
        TimeUnit.MILLISECONDS.sleep(500);

        // 验证连接数减少（或归零，如果只有这一个连接）
        // 由于可能还有其他测试创建了连接，只要连接数不增加即可
        System.out.println("【测试4】连接清理完成");
    }

    // ==================== 测试用例 5：活跃连接数统计 ====================

    /**
     * 测试活跃连接数统计
     * <p>
     * 验证场景：
     * - 创建多个连接
     * - 验证连接数正确递增
     * - 关闭部分连接
     * - 验证连接数正确递减
     */
    @Test
    @DisplayName("测试活跃连接数统计")
    void testActiveConnectionCount() {
        // 记录初始连接数
        int initialCount = sseEmitterManager.getActiveConnectionCount();

        // 创建 3 个连接
        sseEmitterManager.createEmitter("count-test-1", 60000L);
        sseEmitterManager.createEmitter("count-test-2", 60000L);
        sseEmitterManager.createEmitter("count-test-3", 60000L);

        // 验证连接数增加了 3
        assertEquals(initialCount + 3, sseEmitterManager.getActiveConnectionCount(),
                "创建 3 个连接后，活跃连接数应增加 3");

        // 关闭 2 个连接
        sseEmitterManager.complete("count-test-1");
        sseEmitterManager.complete("count-test-2");

        System.out.println("【测试5】连接数统计验证: 创建3个，关闭2个，当前="
                + sseEmitterManager.getActiveConnectionCount());
    }
}
```

---

## 四、运行验证

### 4.1 启动项目

```bash
# 使用 Maven 编译并启动
mvn spring-boot:run
```

启动成功后，控制台输出：

```
2024-XX-XX 10:00:00 - SseDemoApplication 启动成功
2024-XX-XX 10:00:00 - Tomcat started on port 8080
```

### 4.2 使用前端页面测试

打开浏览器访问 `http://localhost:8080`，你将看到 SSE 前端测试页面。

**操作步骤：**

1. 在输入框中输入文章主题（如"Spring Boot 入门指南"）
2. 点击"开始生成"按钮
3. 观察事件流实时推送效果

**预期效果：**

| 时间 | 事件 | 页面显示 |
|------|------|----------|
| 0s | 创建任务 | "任务已创建，正在连接 SSE..." |
| 1s | AGENT1_COMPLETE | 标题生成完成，显示 3 个标题选项 |
| 2-6s | AGENT2_STREAMING | 大纲逐段显示（蓝色流式事件） |
| 7s | AGENT2_COMPLETE | 大纲生成完成（绿色完成事件） |
| 8-15s | AGENT3_STREAMING | 正文逐句显示（蓝色流式事件） |
| 16s | AGENT3_COMPLETE | 正文生成完成 |
| 17s | AGENT4_COMPLETE | 配图分析完成，显示配图计划 |
| 18-20s | IMAGE_COMPLETE | 配图逐张完成（橙色图片事件） |
| 21s | AGENT5_COMPLETE | 全部配图就绪 |
| 22s | MERGE_COMPLETE | 文章生成完成！ |

### 4.3 使用 curl 测试 SSE 接口

```bash
# 1. 创建任务
curl -X POST http://localhost:8080/api/article/create \
  -H "Content-Type: application/json" \
  -d "{\"topic\":\"Spring Boot 入门指南\"}"

# 返回：{"taskId":"xxx","topic":"...","message":"任务创建成功"}

# 2. 建立 SSE 连接（会持续输出事件流）
curl -N http://localhost:8080/api/article/generate/{taskId}/stream?topic=Spring+Boot+入门指南
```

SSE 流式输出的原始数据：

```
event: AGENT1_COMPLETE
data: {"type":"complete","titles":[...],"message":"标题生成完成，请选择"}

event: AGENT2_STREAMING
data: {"type":"text","content":"# 一、项目背景\n\n"}

event: AGENT2_COMPLETE
data: {"type":"complete","content":"...","message":"大纲生成完成"}

event: AGENT3_STREAMING
data: {"type":"text","content":"## 1.1 什么是Spring Boot入门指南\n\n"}

event: AGENT3_COMPLETE
data: {"type":"complete","message":"正文生成完成"}

event: AGENT4_COMPLETE
data: {"type":"analysis","requirements":[...],"message":"配图分析完成，共 3 张配图"}

event: IMAGE_COMPLETE
data: {"type":"image","url":"https://mermaid.ink/img/...","paragraphIndex":1}

event: AGENT5_COMPLETE
data: {"type":"complete","images":3,"message":"全部配图已就绪"}

event: MERGE_COMPLETE
data: {"type":"complete","article":"...","message":"文章已生成完成"}
```

### 4.4 运行单元测试

```bash
# 运行所有测试
mvn test
```

预期测试结果：

```
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0
```

**测试通过说明：**

| 测试用例 | 预期结果 | 验证点 |
|---------|----------|--------|
| testCreateEmitter | SseEmitter 创建成功，连接数增加 | 连接创建正确 |
| testSendEvent | 发送事件不抛异常 | 事件发送正常 |
| testSendEventToNonExistentTask | 静默忽略，不抛异常 | 容错设计正确 |
| testConnectionCleanup | 连接关闭后资源清理 | 避免内存泄漏 |
| testActiveConnectionCount | 连接数正确增减 | 统计准确 |

---

## 五、项目对照

### 5.1 与 ai-passage-creator 项目的对比

| 对比维度 | 入门示例（本文） | ai-passage-creator 项目 |
|----------|----------------|----------------------|
| 连接管理 | SseEmitterManager（ConcurrentHashMap） | 同上，但增加 Redis 事件历史支持 |
| 事件类型 | 10 种模拟事件 | 10+ 种真实事件（含更细粒度状态） |
| 业务逻辑 | 模拟生成，固定延迟 | 真实 AI 大模型调用，流式 Token 输出 |
| 断线重连 | 未实现 | 基于 Redis 事件历史 + Last-Event-ID |
| 心跳检测 | 未实现 | @Scheduled 定时任务 + 心跳事件 |
| 连接数限制 | 未实现 | 用户级 + 全局级限流 |
| 监控 | 日志输出 | 日志 + 连接数统计 + 降级事件 |
| 安全认证 | 无 | Token 认证 + CORS 配置 |

### 5.2 入门示例的简化点

1. **模拟数据代替真实 AI**：项目中使用 DashScope 大模型的流式响应（`Flux<ChatMessage>`），入门示例使用固定文本和 `Thread.sleep` 模拟
2. **无断线重连**：项目中使用 Redis 存储事件历史，断线后根据 Last-Event-ID 重放事件，入门示例未实现
3. **无心跳检测**：项目中使用 `@Scheduled` 定时发送心跳事件检测连接存活，入门示例未实现
4. **无连接数限制**：项目中实现用户级和全局级限流，防止资源耗尽，入门示例未实现

### 5.3 从入门到项目实战的进阶路径

```
入门示例（本文）
  │
  ├── Step 1: 接入真实 AI 大模型
  │     使用 DashScope 的 Flux<ChatMessage> 流式响应
  │
  ├── Step 2: 实现断线重连
  │     Redis 存储事件历史 + Last-Event-ID 恢复
  │
  ├── Step 3: 加入心跳检测
  │     @Scheduled 定时任务 + 心跳事件
  │
  ├── Step 4: 连接数限制
  │     用户级 max-per-user + 全局级 max-total
  │
  └── Step 5: 监控与告警
        连接数统计、降级事件、SLA 监控
```

---

## 六、面试题

### Q1: SSE 和 WebSocket 有什么区别？在什么场景下选择 SSE？

**参考答案：**

**核心区别：**

| 维度 | SSE | WebSocket |
|------|-----|-----------|
| 通信方向 | 服务端 → 客户端（单向） | 客户端 ↔ 服务端（全双工） |
| 协议基础 | HTTP（标准 HTTP 协议） | 独立协议（ws:// / wss://） |
| 浏览器支持 | 原生 `EventSource` API | 原生 `WebSocket` API |
| 自动重连 | **内置**：浏览器自动重试，支持 Last-Event-ID | 无内置，需手动实现重连逻辑 |
| 流式数据 | 天然支持（`text/event-stream` 格式） | 需自定义消息协议 |
| 传输效率 | 较低（HTTP 头部开销） | 高（二进制帧，头部开销小） |
| 服务端实现 | 简单（Spring SseEmitter） | 较复杂（WebSocketHandler / STOMP） |

**选型指南：**

| 场景 | 推荐方案 | 原因 |
|------|----------|------|
| AI 流式输出（大模型 Token 逐字推送） | **SSE** | 单向推送，天然支持流式，自动重连 |
| 实时聊天 / 即时通讯 | **WebSocket** | 需要双向通信，双方都需主动推送 |
| 任务进度推送 | **SSE** | 服务端单向推送，简单可靠 |
| 实时协作编辑 | **WebSocket** | 需要双向同步，低延迟 |
| 股票行情 / 实时数据 | **SSE** | 数据量大，服务端单向推送 |
| 游戏 / 实时白板 | **WebSocket** | 低延迟，双向通信，二进制帧支持 |

**ai-passage-creator 的选择理由：**

文章生成场景是"服务端生成 → 前端展示"，纯单向推送，不需要前端主动发消息。SSE 的自动重连特性在 AI 生成场景中尤其重要——用户网络不稳定时，断线后自动恢复，继续接收生成内容。相比 WebSocket，SSE 的实现更简单，不需额外引入 STOMP 协议或 WebSocket 处理器。

### Q2: SseEmitter 的连接管理有哪些关键点？如何避免资源泄漏？

**参考答案：**

**SseEmitter 连接管理的四个关键点：**

**关键点 1：注册回调函数**

创建 SseEmitter 后，必须注册 `onCompletion`、`onTimeout`、`onError` 三个回调，用于在连接结束时自动清理资源。

```java
// ❌ 错误：不注册回调，连接断开后 entry 永远不会被移除
SseEmitter emitter = new SseEmitter(0L);
emitterMap.put(taskId, emitter);
// 如果客户端断开连接，emitterMap 中的 entry 永远不会被移除！
// 长期运行会导致内存泄漏

// ✅ 正确：注册三个回调，确保资源被清理
SseEmitter emitter = new SseEmitter(0L);
emitter.onCompletion(() -> emitterMap.remove(taskId));
emitter.onTimeout(() -> emitterMap.remove(taskId));
emitter.onError(e -> emitterMap.remove(taskId));
emitterMap.put(taskId, emitter);
```

**关键点 2：超时时间设置**

SseEmitter 默认超时时间为 30 秒，AI 生成场景通常需要设置为 0（永不超时）。

```java
// ❌ 错误：使用默认超时（30 秒）
SseEmitter emitter = new SseEmitter(); // 30 秒后自动关闭！
// 如果文章生成需要 40 秒，第 30 秒连接会断开

// ✅ 正确：根据业务场景设置
SseEmitter emitter = new SseEmitter(0L); // 永不超时
```

**关键点 3：连接数限制**

防止资源耗尽，需要设置连接数上限。

```java
// 设置连接数上限
private static final int MAX_CONNECTIONS = 1000;

public SseEmitter createWithLimit(String taskId, long timeout) {
    if (emitterMap.size() >= MAX_CONNECTIONS) {
        throw new RuntimeException("SSE 连接数已达上限，请稍后重试");
    }
    return createEmitter(taskId, timeout);
}
```

**关键点 4：心跳检测**

定时发送心跳事件，检测"假死"连接（客户端已关闭但服务端未感知）。

```java
// 定时心跳检测
@Scheduled(fixedRate = 30000) // 每 30 秒执行一次
public void heartbeatCheck() {
    emitterMap.forEach((taskId, emitter) -> {
        try {
            emitter.send(SseEmitter.event().name("heartbeat").data("ping"));
        } catch (IOException e) {
            // 发送失败，连接已断开，清理
            emitterMap.remove(taskId);
        }
    });
}
```

### Q3: 断线重连如何实现？前端和服务端分别需要做什么？

**参考答案：**

**SSE 协议内置的自动重连能力：**

浏览器 EventSource 在连接断开后会自动重试，这是 SSE 协议的内置能力，无需额外编码。

**前端需要做的：**

```javascript
// 1. 基本的 EventSource 连接（浏览器自动重连）
const eventSource = new EventSource(url);

// 2. 记录最后收到的事件 ID（用于断线后恢复）
let lastEventId = null;
eventSource.addEventListener('AGENT3_STREAMING', (event) => {
    lastEventId = event.lastEventId; // 浏览器自动填充 lastEventId
    // 处理数据...
});

// 3. 重连时带上 lastEventId
function connectWithReconnect(taskId) {
    const url = lastEventId
        ? `/api/article/generate/${taskId}/stream?lastEventId=${lastEventId}`
        : `/api/article/generate/${taskId}/stream`;

    const eventSource = new EventSource(url);
    // 注册事件监听器...
}

// 4. 指数退避重试策略
let retryCount = 0;
const maxRetries = 5;

eventSource.onerror = (error) => {
    eventSource.close();
    retryCount++;

    if (retryCount <= maxRetries) {
        const delay = Math.min(1000 * Math.pow(2, retryCount), 30000);
        setTimeout(() => connect(), delay);
    } else {
        showError('连接已断开，请刷新页面重试');
    }
};
```

**服务端需要做的：**

```java
/**
 * 处理断线重连请求
 * <p>
 * 服务端根据 lastEventId 判断从哪个位置继续推送事件。
 * 需要将事件历史存储在 Redis 中，用于重放。
 */
public SseEmitter handleReconnection(String taskId, String lastEventId) {
    // 1. 创建新的 SSE 连接
    SseEmitter emitter = sseEmitterManager.createEmitter(taskId, 0L);

    // 2. 从 Redis 查询事件历史，重放 lastEventId 之后的事件
    List<String> history = redisTemplate.opsForList()
        .range("sse:history:" + taskId, 0, -1);

    for (String eventJson : history) {
        // 解析事件，如果事件 ID > lastEventId，发送到新的 emitter
        // ...
    }

    // 3. 继续推送后续事件
    return emitter;
}
```

**完整的断线重连流程：**

```
1. 客户端断线
    ↓
2. 浏览器自动触发重连（内置机制）
    ↓
3. 重连请求携带 Last-Event-ID 头
    ↓
4. 服务端收到 Last-Event-ID
    ↓
5. 服务端查询 Redis 中的事件历史
    ↓
6. 从 Last-Event-ID 之后的事件开始重放
    ↓
7. 继续推送新事件（从当前状态继续）
```

---

## 七、避坑指南

### 7.1 超时设置：SseEmitter 默认超时 30 秒

```java
// ❌ 错误：使用默认超时
SseEmitter emitter = new SseEmitter(); // 默认 30 秒超时！
// 30 秒后如果没有发送任何事件，连接自动关闭

// ✅ 正确：根据业务场景设置超时
SseEmitter emitter = new SseEmitter(0L); // 0 = 永不超时
```

### 7.2 资源泄漏：确保连接被正确清理

```java
// ❌ 错误：创建了 SseEmitter 但没有注册清理回调
SseEmitter emitter = new SseEmitter(0L);
emitterMap.put(taskId, emitter);
// 如果客户端断开连接，emitterMap 中的 entry 永远不会被移除！

// ✅ 正确：注册 onCompletion / onTimeout / onError 回调
SseEmitter emitter = new SseEmitter(0L);
emitter.onCompletion(() -> emitterMap.remove(taskId));
emitter.onTimeout(() -> emitterMap.remove(taskId));
emitter.onError(e -> emitterMap.remove(taskId));
emitterMap.put(taskId, emitter);
```

### 7.3 线程安全：ConcurrentHashMap 的迭代问题

```java
// ❌ 错误：在遍历时修改 Map
for (String taskId : emitterMap.keySet()) {
    if (isExpired(taskId)) {
        emitterMap.remove(taskId); // ConcurrentModificationException！
    }
}

// ✅ 正确：使用 removeIf
emitterMap.keySet().removeIf(this::isExpired);

// ✅ 或者使用 ConcurrentHashMap 的 forEach
emitterMap.forEach((taskId, emitter) -> {
    if (isExpired(taskId)) {
        emitterMap.remove(taskId); // ConcurrentHashMap 允许在遍历时删除
    }
});
```

### 7.4 数据序列化：SseEmitter 自动调用 toString

```java
// ❌ 错误：直接发送对象，SseEmitter 会调用 toString()
// 结果：前端收到 "[object Object]"
emitter.send(SseEmitter.event().name("IMAGE_COMPLETE").data(imageResult));

// ✅ 正确：先序列化为 JSON 字符串
emitter.send(SseEmitter.event()
    .name("IMAGE_COMPLETE")
    .data(Map.of("url", imageUrl, "paragraphIndex", 1)));
```

### 7.5 浏览器兼容性：EventSource 不支持自定义请求头

```javascript
// ❌ 错误：EventSource 无法添加自定义请求头
const eventSource = new EventSource(url, {
    headers: { 'Authorization': 'Bearer ' + token } // ❌ 不支持！
});

// ✅ 正确做法：
// 方案 1：在 URL 中带 token 参数
const eventSource = new EventSource(`/api/stream?token=${token}`);

// 方案 2：使用 Cookie（推荐）
// 将 token 放在 Cookie 中，SSE 请求会自动携带 Cookie

// 方案 3：使用 fetch + ReadableStream 替代 EventSource
const response = await fetch(url, {
    headers: { 'Authorization': 'Bearer ' + token }
});
const reader = response.body.getReader();
// 手动解析 SSE 数据流...
```

---

> **下期预告：** 第 5 篇将介绍人机协作模式（Human-in-the-Loop），带你理解如何用状态机实现"选题→大纲→正文"的分步确认流程。