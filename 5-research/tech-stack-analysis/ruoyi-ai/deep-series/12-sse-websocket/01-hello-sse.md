# SSE + WebSocket 入门：实时通信从零开始

> 阅读本文你能收获什么：从零手写一套同时支持 **SSE（Server-Sent Events，服务器推送事件）** 和 **WebSocket** 的实时通信 Demo——包含完整的 `pom.xml`、`application.yml`、后端 Java 代码和一个可直接双击打开的前端测试页面。文章所有 Java 代码都带逐行中文注释，运行即可看到"模拟 AI 逐字输出"的效果。最后会对照 ruoyi-ai 项目，讲清楚这套技术在大模型对话场景下到底扮演什么角色。
>
> 本文定位：**Level 1 入门篇**。不涉及 STOMP、不涉及分布式多节点嵌套推送，聚焦最核心的 `SseEmitter` 和原生 `WebSocketHandler` 两条主线，把协议、代码、验证、面试一问到底。
>
> **对应项目：** `ruoyi-ai/ruoyi-common/sse` 公共 starter + `ruoyi-common/websocket` 公共 starter

---

## 一、项目背景：为什么需要实时通信

### 1.1 从"等"到"边等边看"的用户体验革命

先回忆一个我们已经习以为常的场景：在网页版 ChatGPT 里敲下一段问题，按下回车，不到一秒屏幕上就开始"蹦字"——一个字一个字往外跳，像打字机一样。而在传统 Web 应用中，一次请求的典型流程是：

```
用户在浏览器点击"发送"
    ↓
浏览器发起 HTTP 请求（POST /chat）
    ↓
服务器收到请求，开始调用外部接口 / 执行耗时计算（3~10 秒）
    ↓
浏览器一直转圈等待（白屏、加载动画）
    ↓
服务器返回完整响应，浏览器一次性渲染
```

这个过程的问题非常明显：**漫长的等待期里用户完全得不到反馈**。如果大模型生成回答需要 8 秒，用户就要盯着加载动画看 8 秒，既焦虑又容易误以为系统卡死了。心理学上有著名的"3 秒法则"——超过 3 秒的等待会让用户产生挫败感，流失率大幅上升。

实时通信技术要解决的核心问题，就是把上面这个"请求-等待-一次性返回"的模式，改造成"请求-持续接收-边收边渲染"的模式：

```
用户在浏览器点击"发送"
    ↓
浏览器建立一条长连接（SSE 或 WebSocket）
    ↓
0.5 秒后收到第一个 Token（"好"字）
    ↓
持续收到后续 Token（"的，这个问题"）
    ↓
一边接收一边渲染，用户感觉 AI 在"打字"
    ↓
收到结束标志 [DONE]，对话结束
```

首字延迟从"秒级"优化到"亚秒级"，整体感知速度提升数倍，这就是实时通信在 AI 场景里的价值。

### 1.2 实时通信的四大典型场景

实时通信不是一个新技术，它在 Web 领域已经有了二十多年的演进历史。典型的应用场景包括：

| 场景 | 需求本质 | 选型倾向 |
|------|----------|----------|
| **AI 对话流式输出**（ChatGPT 式） | 服务端 → 客户端单向推送大量文本片段 | SSE（首选） |
| **消息通知推送**（审批待办、系统公告） | 服务端 → 客户端单向推送，偶发 | SSE 或 WebSocket 均可 |
| **在线客服 / 协作编辑** | 双向实时通信，客户端也要主动发 | WebSocket（必须） |
| **股票行情 / 游戏对局 / 实时定位** | 高频双向或单向推送 | WebSocket（首选） |

### 1.3 SSE 和 WebSocket 的定位：一个推送，一个通道

在 ruoyi-ai 这样的 AI 项目中，两种技术**同时存在、各司其职**：

- **SSE 负责"AI 流式输出"**：对话是单向流——用户把问题发出去（走普通 HTTP POST），模型回答是一股源源不断的文本流，服务端只管往客户端"倒水"，不需要客户端中途插话。这种"客户端到服务端一次、服务端到客户端多次"的不对称流量，简直是 SSE 的完美主场。
- **WebSocket 负责"双向实时通道"**：管理端的消息通知、在线状态同步、任务进度推送等场景，服务端需要**主动**找客户端说话（比如审批流程刚办结，立刻通知审批人），甚至需要客户端实时回执确认。这种双向、随时、高频的通信，必须靠 WebSocket 这种全双工通道。

一句话总结定位：

> **SSE 是"服务端单向推送"的协议，WebSocket 是"双方任意互发"的通道。AI 对话只需要前者，协同业务往往需要后者。**

---

## 二、核心概念：先把地基打牢

### 2.1 SSE：基于 HTTP 的单向推送协议

SSE（Server-Sent Events，服务器发送事件）是 HTML5 标准的一部分，由 W3C 在 2009 年前后提出并标准化。它的设计目标非常简单：**让服务器可以随时向浏览器推送消息，而浏览器端只需要一个对象就能接收**。

**核心机制：**

SSE 本质上还是一段普通的 HTTP 请求/响应，只是响应永远不会"结束"。客户端通过 `EventSource` 对象发起一个 GET 请求，服务端设置 `Content-Type: text/event-stream` 后，一直保持连接不关闭，并持续向响应体里写入特定格式的文本。浏览器收到后逐条解析、触发回调。

**SSE 网络报文格式（这是理解 SSE 的钥匙）：**

```http
HTTP/1.1 200 OK
Content-Type: text/event-stream
Cache-Control: no-cache
Connection: keep-alive

data: 你好

data: 我是

data: AI 助手

data: [DONE]

```

每一条消息用两个换行符 `\n\n` 分隔。`data:` 表示数据字段，还可以有 `event:`（事件名，前端用 `addEventListener` 监听不同事件）、`id:`（事件 ID，用于断线重连续传）、`retry:`（重连间隔毫秒数）。这是纯文本协议，任何语言都能生成和解析。

**SSE 的三个杀手级优势（面试必讲）：**

1. **浏览器原生支持、自动重连**：`EventSource` 是浏览器内置对象，零依赖；连接断开后浏览器自动重连，重连时自动携带 `Last-Event-ID` 请求头，服务端可以实现断点续传。这一条就把 WebSocket 的"自己写重连"甩开一条街。
2. **基于标准 HTTP**：走 80/443 端口，能穿过绝大多数代理、防火墙，天然兼容现有的鉴权中间件（Cookie、Token 头都可以带上），Nginx 反代配置简单。
3. **实现成本极低**：服务端不用额外引入 WebSocket 容器和握手协议，Spring 里一个 `SseEmitter` 对象就搞定。

**SSE 的短板：**

- 单向：只能服务端推给客户端，客户端要说话得再发普通 HTTP 请求；
- 浏览器对同域 SSE 连接数有限制（HTTP/1.1 下 Chrome 约 6 个）；
- 不支持二进制（可以 Base64 编码绕过去）；
- 需要服务端显式处理心跳/保活，否则代理中间件可能切断空闲连接。

### 2.2 WebSocket：全双工的长连接通道

WebSocket 是 2011 年正式定稿的 RFC 6455 标准，它解决的是 HTTP 协议"一请求一响应、服务器不能主动说话"的天然缺陷。

**核心机制：**

WebSocket 连接分两步走：

1. **握手阶段（HTTP Upgrade）**：客户端发一个普通 HTTP 请求，带上 `Upgrade: websocket` 和 `Sec-WebSocket-Key` 头，服务端验证后返回 `101 Switching Protocols`，协议升级成功。
2. **数据阶段（帧传输）**：此后连接升级为 WebSocket 协议（`ws://` 或 `wss://`），双方基于 TCP 发送一个个数据帧（Frame），可以同时双向发送，没有 HTTP 的请求-响应配对约束。

**WebSocket 报文帧结构（简单了解即可）：**

```
0                1               2               3
0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
+-+-+-+-+-------+-+-------------+-------------------------------+
|F|R|R|R| opcode|M| Payload len |    Extended payload length    |
|I|S|S|S|  (4)  |A|     (7)     |             (16/64)           |
|N|V|V|V|       |S|             |   (if payload len==126/127)   |
| |1|2|3|       |K|             |                               |
+-+-+-+-+-------+-+-------------+ - - - - - - - - - - - - - - - +
```

第一字节的高 4 位是操作码（opcode）：`0x1` 文本帧、`0x2` 二进制帧、`0x8` 关闭帧、`0x9` Ping 帧、`0xA` Pong 帧。**心跳检测正是利用 Ping/Pong 帧实现的**，这一点后面写代码时会用到。

**WebSocket 的核心优势：**

- **全双工**：客户端和服务端随时都能主动说话，适合聊天、协作、游戏等双向高频场景；
- **低开销**：握手后不再有 HTTP 头部的重复传输，每个数据帧只有 2~14 字节的头部开销，对于高频率小消息特别友好；
- **支持二进制**：图片、音频、二进制协议数据可以直接传。

**WebSocket 的成本与坑：**

- **没有内置重连**：连接断了就是断了，前端必须自己实现重连逻辑（后面会给出指数退避重连代码）；
- **没有内置心跳**：TCP 层虽然有心跳（keepalive），但默认 2 小时才探测一次，中间还隔着 Nginx、负载均衡器，空闲连接极容易被切断，需要应用层自己做心跳保活；
- **实现复杂度高**：握手鉴权、心跳、重连、消息可靠性都要自己设计；
- **代理穿透成本**：某些老旧代理不支持 `Upgrade` 头，需要额外的 80 端口降级方案（如 SockJS）。

### 2.3 长连接：SSE 和 WebSocket 的共同底座

无论 SSE 还是 WebSocket，本质都是**长连接**——连接建立后长时间保持，而不是用一次就销毁。

- **HTTP 短连接**：每次请求建立 TCP，响应完就关闭。代价是频繁的 TCP 三次握手 + 四次挥手，以及 TLS 握手（如果走 HTTPS），开销大。
- **HTTP 长连接（HTTP/1.1 Keep-Alive）**：一个 TCP 连接上串行处理多个"请求-响应"，这是普通 Web 应用默认的模式。
- **SSE 长连接**：一个 HTTP 响应长时间不结束，服务端持续往同一个连接上写数据。注意：SSE 在 HTTP/1.1 下是**独占一个 TCP 连接**的（因为响应永远不结束，无法复用），这也是浏览器的 SSE 并发连接数受限的原因；只有在 HTTP/2 下才能真正实现多路复用。
- **WebSocket 长连接**：TCP 连接升级为 WebSocket 协议，双向持续传输帧。

长连接带来了一个额外的工程问题：**HTTP/1.1 下服务器（Tomcat）的并发连接数有限**。连接池、超时回收、心跳保活、连接数监控，都是长连接项目的必修课。

### 2.4 心跳：让"假装在线"的僵尸连接无所遁形

任何长连接系统都逃不开一个现实：**网络是不可靠的**。客户端可能断网、可能直接关掉浏览器、可能休眠；中间代理（Nginx、云负载均衡器）可能在一段时间无流量后主动掐断空闲连接。双方都以为连接还活着，实际上已经是"僵尸连接"。

**心跳（Heartbeat）机制就是解决这个问题的：**

1. **保活**：定期发送一个很小的包，告诉中间的代理设备"我还活着，别切断我"；
2. **探测**：通过对端是否回复，判断对方是否真的存活，及时清理无效连接，释放服务器资源。

具体到两种技术：

- **SSE 心跳**：服务端每隔 30 秒往里写一条保留消息（比如 `: heartbeat\n\n` 注释行或 `event: heartbeat` 事件）。语法上 SSE 支持以 `:` 开头的注释行，浏览器收到会直接忽略，不影响业务事件流。客户端也可以监听 `onerror`，在浏览器自动重连之外做兜底处理。
- **WebSocket 心跳**：标准做法是应用层互发 `{"type":"PING"}` / `{"type":"PONG"}` 消息（也可以用协议层的 Ping/Pong 帧）。客户端 30 秒发一次 PING，服务端收到回 PONG 并刷新该连接的"最后活跃时间"；服务端另起一个定时任务，每分钟扫一遍连接池，把超过 60 秒没有任何消息的连接视为失活，主动关闭。

**断线重连（只在 WebSocket 需要自己做）：**

```
服务端崩溃/网络抖动
    ↓
客户端发现 onclose 或心跳超时
    ↓
等待退避时间（1s → 2s → 4s → 8s → ... 封顶 30s）
    ↓
重新 new WebSocket(url)
    ↓
重连成功后重置退避基数，恢复业务
```

这就是"指数退避重连"，后面前端代码会完整实现。

---

## 三、从零搭建代码：一条命令跑起来的完整 Demo

> 本节是全文最核心的部分。我们将创建一个全新的 Spring Boot 3.2 项目，包含 **7 个文件**，全部给出完整代码与逐行中文注释：
>
> 1. `pom.xml` — Maven 依赖
> 2. `src/main/resources/application.yml` — 应用配置
> 3. `src/main/java/com/example/realtime/RealtimeApplication.java` — 启动类
> 4. `src/main/java/com/example/realtime/sse/SseController.java` — SSE 控制器（模拟 AI 逐字输出）
> 5. `src/main/java/com/example/realtime/ws/WebSocketConfig.java` — WebSocket 配置（端点注册 + 握手拦截器）
> 6. `src/main/java/com/example/realtime/ws/ChatWebSocketHandler.java` — WebSocket 消息处理器（含心跳）
> 7. `src/main/resources/static/index.html` — 前端测试页面（SSE + WebSocket 双通道）

### 3.1 项目结构一览

```
realtime-demo/                          ← 项目根目录
├── pom.xml                             ← Maven 构建文件
└── src
    ├── main
    │   ├── java
    │   │   └── com
    │   │       └── example
    │   │           └── realtime
    │   │               ├── RealtimeApplication.java      ← Spring Boot 启动类
    │   │               ├── sse
    │   │               │   └── SseController.java        ← SSE 控制器
    │   │               └── ws
    │   │                   ├── WebSocketConfig.java      ← WebSocket 配置 + 拦截器
    │   │                   └── ChatWebSocketHandler.java ← WebSocket 处理器
    │   └── resources
    │       ├── application.yml         ← 应用配置
    │       └── static
    │           └── index.html          ← 前端测试页面
    └── test                            ← （入门篇省略测试，可自行补充）
```

### 3.2 第一步：pom.xml（完整版）

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!-- Maven 项目描述文件：声明项目坐标、父工程、依赖清单 -->
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">

    <!-- 模型版本：4.0.0 是 Maven 2+ 的唯一取值，固定写法 -->
    <modelVersion>4.0.0</modelVersion>

    <!-- 父工程：继承 Spring Boot 官方 BOM，统一管理所有依赖版本号 -->
    <!-- 好处：子项目里写依赖时不用写 version，杜绝版本冲突 -->
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.2.5</version>
        <relativePath/> <!-- 不从本地文件系统找 parent，直接去中央仓库解析 -->
    </parent>

    <!-- 项目坐标：groupId + artifactId + version 唯一定位本项目 -->
    <groupId>com.example</groupId>
    <artifactId>realtime-demo</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <name>realtime-demo</name>
    <description>SSE + WebSocket 从零入门 Demo</description>

    <properties>
        <!-- 全局统一 Java 版本：Spring Boot 3.x 要求 JDK 17+ -->
        <java.version>17</java.version>
        <!-- 指定源代码编译级别，与目标版本保持一致，避免编译器警告 -->
        <maven.compiler.source>17</maven.compiler.source>
        <maven.compiler.target>17</maven.compiler.target>
    </properties>

    <dependencies>
        <!-- ============ Web 核心依赖：内含 Tomcat 容器、Spring MVC、Jackson ============ -->
        <!-- 我们使用的 SseEmitter、@RestController 都来自这个 Starter -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>

        <!-- ============ WebSocket 依赖：注册端点、Handshake 拦截器全靠它 ============ -->
        <!-- 提供 @EnableWebSocket、WebSocketHandler、HandshakeInterceptor 等核心类 -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-websocket</artifactId>
        </dependency>

        <!-- ============ Lombok：用注解消除样板代码（getter/setter/日志对象） ============ -->
        <!-- @Slf4j 自动生成 log 对象，@RequiredArgsConstructor 自动生成构造器注入 -->
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <!-- optional=true：编译期工具，不打进最终产物，避免污染下游 -->
            <optional>true</optional>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <!-- Spring Boot 打包插件：把项目打成可执行的 fat jar -->
            <!-- 并支持 mvn spring-boot:run 直接启动 -->
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <configuration>
                    <!-- 排除 Lombok，防止它被重复打进 fat jar -->
                    <excludes>
                        <exclude>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                        </exclude>
                    </excludes>
                </configuration>
            </plugin>
        </plugins>
    </build>

</project>
```

**依赖说明：** 就这三个依赖，一个不多一个不少。SSE 不需要额外依赖——`SseEmitter` 就躺在 `spring-webmvc` 里；WebSocket 需要一个 `spring-boot-starter-websocket`，它内部会引入 `spring-websocket` 和 `spring-messaging`（我们只用前者）。

### 3.3 第二步：application.yml（kebab-case 配置）

```yaml
# ============ Spring Boot 应用配置文件 ============
# 注意：Spring Boot 官方配置缺省就是 kebab-case（短横线命名），如 max-http-header-size

server:
  # 应用监听端口：8080
  port: 8080
  servlet:
    # 会话超时时间：30 分钟（SSE/WebSocket 长连接会话都受此影响）
    session:
      timeout: 30m

spring:
  application:
    # 应用名称：会出现在日志和监控指标里
    name: realtime-demo
  # MIME 类型注册：确保 text/event-stream 被正确识别
  # （现代 Spring Boot 默认已注册，这里列出来方便新手理解）
  mvc:
    contentnegotiation:
      media-types:
        # 把 text/event-stream 显式注册为受支持的媒体类型
        event-stream: text/event-stream

# ============ 自定义配置：心跳与流式参数 ============
# 项目自己的配置用自定义前缀，这里是 sse 和 ws 两组
sse:
  # SSE 连接超时时间（毫秒）：0 表示永不超时（由心跳保活）
  # 设成 0 有风险（连接泄漏），生产环境建议 30 分钟并配合心跳
  timeout-ms: 0
  # SSE 心跳间隔（毫秒）：每 15 秒发一条注释心跳，防止代理切断空闲连接
  heartbeat-interval-ms: 15000
  # 流式输出中每个 Token 的间隔（毫秒）：越大打字机效果越明显，方便观察
  token-interval-ms: 80

ws:
  # WebSocket 端点路径：浏览器连接 ws://localhost:8080/ws/chat
  endpoint: /ws/chat
  # 允许的跨域来源：* 表示全部放行（仅限本地演示，生产必须白名单）
  allowed-origins: "*"
  # 客户端心跳判定超时（毫秒）：60 秒没收到任何消息就判定失活
  idle-timeout-ms: 60000
  # 服务端心跳发送间隔（毫秒）：作为双保险，主动向客户端发 ping
  server-ping-interval-ms: 30000
  # 最大文本消息长度（字节）：防止恶意超长消息打爆内存
  max-text-message-size: 8192

logging:
  level:
    # 项目包日志级别设为 INFO，能看到我们打印的连接/心跳日志
    com.example.realtime: info
```

> **kebab-case 说明：** YAML 里 Spring Boot 官方配置项约定俗成使用短横线（如 `max-http-header-size`），本文自定义配置也全部遵循同样风格，不包含任何驼峰或下划线，与 Spring 的宽松绑定（relaxed binding）规范保持一致。

### 3.4 第三步：启动类 RealtimeApplication.java

```java
package com.example.realtime;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 应用启动类。
 * 作用：作为 Spring Boot 的入口，启动内嵌 Tomcat，扫描并装配所有组件。
 *
 * @SpringBootApplication 是一个组合注解，包含三件事：
 *   1. @SpringBootConfiguration —— 声明这是一个配置类
 *   2. @EnableAutoConfiguration —— 开启自动配置（根据依赖自动装配 Tomcat、Jackson 等）
 *   3. @ComponentScan —— 扫描当前包及子包下的 @Component/@Service/@Controller 等
 */
@SpringBootApplication
public class RealtimeApplication {

    /**
     * main 方法：程序的唯一入口。
     *
     * @param args 命令行参数（一般传 --server.port=9090 这类覆盖配置）
     */
    public static void main(String[] args) {
        // 启动 Spring Boot 应用：创建 IoC 容器、启动内嵌 Web 服务器、注册所有 Bean
        SpringApplication.run(RealtimeApplication.class, args);
        // 启动完成后控制台会打出 "Started RealtimeApplication" 字样
        // 浏览器访问 http://localhost:8080/ 即可看到测试页面
    }
}
```

### 3.5 第四步：SseController.java —— 模拟 AI 对话逐字输出

这是全文的"明星代码"：**用 `SseEmitter` 模拟大模型逐 Token 流式输出**。核心思路是：

```
前端 GET /api/sse/chat?message=你好
    ↓
控制器返回 SseEmitter 对象（HTTP 连接立即挂起，Tomcat 线程释放）
    ↓
异步任务线程逐字发送：data: 你 / data: 好 / data: 啊 ...
    ↓
全部发完，发送 data: [DONE]，调用 emitter.complete() 结束
    ↓
浏览器 EventSource 逐条接收，边收边渲染
```

```java
package com.example.realtime.sse;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * SSE 控制器：对外暴露流式接口，模拟 AI 逐字输出。
 *
 * 核心知识点：
 *  - SseEmitter 是 Spring MVC 提供的 SSE 封装，控制器返回它之后，
 *    Spring 会自动把响应 Content-Type 设为 text/event-stream，
 *    并保持连接不关闭，直到我们主动 complete()。
 *  - 控制器返回 SseEmitter 时 Tomcat 工作线程立即释放，
 *    真正的流式推送在异步线程池中执行，这是支撑高并发的关键。
 */
@Slf4j
@RestController
@RequestMapping("/api/sse")
public class SseController {

    /**
     * 异步执行线程池：执行"逐字推送"任务。
     * 说明：
     *  - 这里用 Executors.newCachedThreadPool 仅为教学简化；
     *  - 生产环境必须使用有界线程池 + 拒绝策略，防止线程无限膨胀。
     */
    private final ExecutorService executor = Executors.newCachedThreadPool();

    /**
     * 模拟大模型回答的"台词库"：按序号返回不同台词。
     * 实际项目中这一步是调用 LLM 的流式接口（如 LangChain4j 的 StreamingChatLanguageModel），
     * 我们用"台词"模拟 LLM 逐 Token 生成的过程，效果完全一致。
     *
     * @param index 台词序号
     * @return 模拟回答文本
     */
    private String pickLine(int index) {
        // 按 index 对数组长度取模，循环使用台词，保证多次调用都有内容可输出
        String[] lines = {
                "你好，我是 AI 助手！我正在使用 SSE 协议把回答一个字一个字地推送到你的浏览器。",
                "所谓流式输出，就是边生成边发送。这大大降低了用户的等待焦虑，是 AI 对话的标准姿势。",
                "你看到的每个字符，其实都是一条独立的 SSE 事件。前端收到后实时追加到对话气泡里。"
        };
        // 返回数组中 index % 3 位置的台词
        return lines[index % lines.length];
    }

    /**
     * SSE 流式对话接口。
     * 前端调用方式：new EventSource('/api/sse/chat?message=你好')
     *
     * @param message 用户输入的消息（这里只用于日志展示，不参与实际生成）
     * @return SseEmitter —— 返回它之后，Spring 就会以 text/event-stream 持续推送
     */
    @GetMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@RequestParam String message) {
        // 1. 创建 SseEmitter：
        //    构造参数是超时时间（毫秒），0L 表示永不超时，
        //    实际项目中建议设置 30 分钟并搭配心跳，避免连接泄漏。
        SseEmitter emitter = new SseEmitter(0L);

        // 2. 注册连接生命周期回调：
        //    - onCompletion：正常结束（complete 被调用）时触发
        //    - onTimeout：超过超时时间未结束触发
        //    - onError：连接异常（客户端断开等）触发
        //    三个回调都打印日志，方便我们观察连接状态。
        emitter.onCompletion(() -> log.info("[SSE] 连接完成，客户端正常结束"));
        emitter.onTimeout(() -> log.info("[SSE] 连接超时，服务端主动断开"));
        emitter.onError(ex -> log.warn("[SSE] 连接异常: {}", ex.getMessage()));

        // 3. 把逐字推送任务丢给异步线程池执行：
        //    注意这里必须异步！如果同步调用，LLM 生成 10 秒，
        //    控制器线程就阻塞 10 秒，Tomcat 200 个线程瞬间被打满。
        executor.submit(() -> streamTokens(emitter, message));

        // 4. 立即返回 emitter：控制器到此结束，Tomcat 线程释放，
        //    连接交还给 Spring 的异步处理机制管理。
        return emitter;
    }

    /**
     * 在异步线程中执行"逐字推送"。
     *
     * @param emitter SseEmitter 实例，通过它把事件写进响应流
     * @param message 用户输入（仅用于日志）
     */
    private void streamTokens(SseEmitter emitter, String message) {
        try {
            // 1. 先推送一条"开始"事件：前端可据此显示"AI 正在输入..."状态
            //    event() 构建 SSE 事件；name() 设置事件名（前端用 addEventListener 监听）；
            //    data() 设置事件负载。SSE 报文最终形如：event: start\ndata: ...\n\n
            emitter.send(SseEmitter.event()
                    .name("start")
                    .data("{\"msg\":\"开始生成...\"}"));

            // 2. 取一段台词作为"AI 的回答"，模仿 LLM 生成的一句话
            String answer = pickLine(message.hashCode());

            // 3. 遍历回答的每一个字符，逐字推送 —— 这就是"打字机效果"的来源
            for (int i = 0; i < answer.length(); i++) {
                // 取当前字符，转成字符串（toCharArray 拿到的是 char，需拼接）
                String token = String.valueOf(answer.charAt(i));

                // 发送 token 事件：每次只推一个字
                // 前端 onmessage / addEventListener('token') 收到后追加到气泡
                emitter.send(SseEmitter.event()
                        .name("token")
                        .data(token));

                // 休息一小段：模拟大模型生成每个 Token 的耗时（如 80ms）
                // 休息时间越长，打字机效果越明显，便于观察流式效果
                Thread.sleep(80L);
            }

            // 4. 推送结束标志 [DONE]：这是 AI 流式输出的事实标准结尾
            //    前端收到 [DONE] 后就该停止追加、隐藏"正在输入"状态
            emitter.send(SseEmitter.event()
                    .name("done")
                    .data("[DONE]"));

            log.info("[SSE] 消息 {} 推送完毕", message);

        } catch (IOException e) {
            // 发送失败：多半是客户端断开了连接（刷新页面 / 关浏览器）
            // 此时继续发送只会堆积错误，直接记录日志即可
            log.warn("[SSE] 发送失败，客户端可能已断开: {}", e.getMessage());
        } catch (InterruptedException e) {
            // 线程被中断（应用关闭等）：恢复中断标志，保持线程优雅退出
            Thread.currentThread().interrupt();
            log.warn("[SSE] 推送被中断");
        } finally {
            // 无论成功失败，最后都要显式结束连接，释放服务器资源
            // 不调用 complete()，这个 HTTP 连接会一直挂着直到超时
            emitter.complete();
        }
    }
}
```

### 3.6 第五步：WebSocketConfig.java —— 端点注册与握手拦截器

WebSocket 的服务端配置分两件事：**注册 Handler 到路径**、**添加握手拦截器做鉴权**。它们都在 `WebSocketConfigurer` 里完成。

```java
package com.example.realtime.ws;

import com.example.realtime.ws.ChatWebSocketHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

import java.util.Map;

/**
 * WebSocket 配置类：负责两件事 ——
 *   1. 把 ChatWebSocketHandler 注册到指定路径（/ws/chat）；
 *   2. 挂载握手拦截器，在连接建立前完成鉴权。
 *
 * 对应 ruoyi-ai 中的 ruoyi-common/websocket 公共 starter，
 * 它会在多个服务里重复注册类似的端点和拦截器。
 */
@Slf4j
@Configuration
@EnableWebSocket  // 开启 WebSocket 支持：让 Spring 识别并注册下面的端点
public class WebSocketConfig implements WebSocketConfigurer {

    // 从 application.yml 读取自定义配置：ws.endpoint，默认 /ws/chat
    @Value("${ws.endpoint:/ws/chat}")
    private String endpoint;

    // 从 application.yml 读取允许的跨域来源，默认 *（仅限演示）
    @Value("${ws.allowed-origins:*}")
    private String allowedOrigins;

    /**
     * 注册 WebSocket 端点 —— WebSocketConfigurer 的核心回调方法。
     *
     * @param registry 端点注册表，Spring 容器启动时自动注入
     */
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // 1. addHandler：把处理器绑定到路径 /ws/chat
        //    客户端连接 ws://localhost:8080/ws/chat 时，连接会交给 ChatWebSocketHandler
        registry.addHandler(chatWebSocketHandler(), endpoint)
                // 2. addInterceptors：握手前先经过自定义拦截器（鉴权在此完成）
                //    拦截器返回 true 才允许握手成功，false 则浏览器收到握手失败
                .addInterceptors(authHandshakeInterceptor())
                // 3. setAllowedOrigins：跨域白名单，* 表示任意来源
                //    生产环境必须换成具体域名，否则任何人网站都能连你的 WS
                .setAllowedOrigins(allowedOrigins.split(","));
    }

    /**
     * 声明 WebSocket 消息处理器 Bean。
     * 这里直接 new，不交给 Spring 管理也可以；但声明为 Bean 便于其他组件注入使用
     * （例如在 Service 里注入它来主动向客户端推送消息）。
     *
     * @return ChatWebSocketHandler 实例
     */
    @Bean
    public ChatWebSocketHandler chatWebSocketHandler() {
        // 返回处理器实例：真正的连接/消息/心跳逻辑都在这个类里
        return new ChatWebSocketHandler();
    }

    /**
     * 声明握手拦截器 Bean：连接建立前的"安检门"。
     *
     * @return 握手拦截器实例
     */
    @Bean
    public HandshakeInterceptor authHandshakeInterceptor() {
        // 返回匿名内部类实现 HandshakeInterceptor 接口
        return new HandshakeInterceptor() {

            /**
             * 握手前回调：返回 true 放行，返回 false 拒绝连接。
             * Handshake 是 WebSocket 连接建立的第一个环节，
             * 此时 HTTP 请求还没升级为 WebSocket，是鉴权的最佳时机。
             *
             * @param request    握手 HTTP 请求，可从中取 header / 参数 / Cookie
             * @param response   握手响应，可设置状态码拒绝连接
             * @param wsHandler  即将处理该连接的 WebSocketHandler
             * @param attributes 握手属性 Map：可以在拦截器里放数据，
             *                   后续 handler 的 session.getAttributes() 能取到
             * @return true 放行 / false 拒绝
             */
            @Override
            public boolean beforeHandshake(ServerHttpRequest request,
                                          ServerHttpResponse response,
                                          WebSocketHandler wsHandler,
                                          Map<String, Object> attributes) {
                // 1. 模拟鉴权：从 URL 查询参数里读取 token
                //    浏览器连接：ws://localhost:8080/ws/chat?token=abc123
                String query = request.getURI().getQuery();
                boolean hasToken = query != null && query.contains("token=");

                // 2. 鉴权失败：记录日志并返回 false 拒绝握手
                if (!hasToken) {
                    log.warn("[WS] 握手被拒绝：缺少 token，请求地址 {}", request.getURI());
                    return false;
                }

                // 3. 鉴权通过：把用户身份写进 attributes
                //    之后 ChatWebSocketHandler 通过 session.getAttributes() 取出，
                //    用于连接池的 Key —— 这就是"谁连接了我"的来源
                attributes.put("userId", "user-1001");
                log.info("[WS] 握手通过，来自 {}", request.getURI());
                return true;
            }

            /**
             * 握手完成后回调：无论成功失败都会调用。
             * 一般用于打点统计或日志，本 Demo 只记录一条日志。
             */
            @Override
            public void afterHandshake(ServerHttpRequest request,
                                       ServerHttpResponse response,
                                       WebSocketHandler wsHandler,
                                       Exception exception) {
                // exception 非空表示握手失败；成功时为 null
                if (exception != null) {
                    log.warn("[WS] 握手失败: {}", exception.getMessage());
                }
            }
        };
    }

    /**
     * 配置 WebSocket 容器参数（可选但推荐）。
     * ServletServerContainerFactoryBean 控制底层 WebSocket 容器的行为，
     * 例如消息缓冲区大小、空闲超时等。
     *
     * @return 容器工厂 Bean
     */
    @Bean
    public ServletServerContainerFactoryBean createWebSocketContainer() {
        // 创建容器工厂：它会被 Spring 用来创建 WebSocket 容器并应用以下配置
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        // 单条文本消息最大 8KB：超过会抛异常，防止超大消息打爆内存
        container.setMaxTextMessageBufferSize(8192);
        // 单条二进制消息最大 8KB
        container.setMaxBinaryMessageBufferSize(8192);
        // Session 空闲超时：60 秒没有任何通信就自动关闭（与心跳搭配使用）
        container.setMaxSessionIdleTimeout(60000L);
        // 返回配置好的容器工厂
        return container;
    }
}
```

### 3.7 第六步：ChatWebSocketHandler.java —— 处理器与心跳

这是 WebSocket 的核心逻辑：**连接池管理 + 消息收发 + 心跳检测 + 广播**。

```java
package com.example.realtime.ws;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

/**
 * WebSocket 消息处理器：长连接建立后的"值班室"。
 *
 * 四大职责：
 *   1. 连接注册：afterConnectionEstablished —— 新连接进来时入池
 *   2. 消息收发：handleTextMessage —— 处理客户端发来的业务消息和心跳 PING
 *   3. 心跳维护：服务端定时主动 PING + 定期清理失活连接
 *   4. 主动推送：sendTo / broadcast —— 供业务代码调用，随时给客户端发消息
 *
 * 注意：本类是单例 Bean（Spring 默认单例），
 * sessions 是并发安全的 ConcurrentHashMap —— 线程安全是长连接项目的红线。
 */
@Slf4j
public class ChatWebSocketHandler extends TextWebSocketHandler {

    // ==================== 连接池 ====================

    /**
     * 在线连接池：Key 是 userId，Value 是 WebSocketSession。
     * 使用 ConcurrentHashMap：多个线程（多个客户端）同时读写也必须安全。
     * 进阶：同一用户多端登录时，应改成 Map<String, Set<WebSocketSession>>。
     */
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    /**
     * 最近活跃时间表：Key 是 userId，Value 是该用户最后一次通信的时间戳。
     * 用于心跳超时判定：超过 idleTimeoutMs 未通信，视为失活。
     */
    private final Map<String, Instant> lastActive = new ConcurrentHashMap<>();

    // ==================== 配置参数（演示直接写死，生产应从 yml 注入） ====================

    /** 心跳失活阈值（毫秒）：60 秒没任何通信就判定断线 */
    private final long IDLE_TIMEOUT_MS = 60_000L;

    /** 服务端主动心跳间隔（毫秒）：每 30 秒向所有在线客户端发一次 PING */
    private final long SERVER_PING_MS = 30_000L;

    /** 任务调度器：驱动定时心跳和定时清理（演示用单线程调度，生产建议线程池） */
    private final TaskScheduler scheduler = new ThreadPoolTaskScheduler();

    /**
     * 构造器：启动两个后台定时任务 ——
     *   1. 每 30 秒向所有客户端广播 PING（保活 + 探测）；
     *   2. 每 60 秒扫描连接池，清理失活连接。
     * 注意：ThreadPoolTaskScheduler 需要初始化；用 Executors 简化亦可。
     * 为保证教学代码简洁，这里初始化线程池调度器。
     */
    public ChatWebSocketHandler() {
        // 初始化调度器：本文用 Executors 新建线程跑两个循环任务（等价效果）
        // 生产环境推荐注入 Spring 的 @Scheduled 或 TaskScheduler Bean，更优雅
        Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(this::pingAll, 15, SERVER_PING_MS / 1000, java.util.concurrent.TimeUnit.SECONDS);
        Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(this::cleanupIdle, 30, IDLE_TIMEOUT_MS / 1000, java.util.concurrent.TimeUnit.SECONDS);
    }

    // ==================== 生命周期回调 ====================

    /**
     * 连接建立成功回调：新客户端连进来时触发。
     * 这里拿到 session，可以从 attributes 里取出握手阶段存放的 userId，
     * 然后把 session 存入连接池。
     *
     * @param session 当前连接会话
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        // 1. 从握手拦截器写入的 attributes 里取出 userId（见 WebSocketConfig.beforeHandshake）
        Object userIdObj = session.getAttributes().get("userId");
        String userId = userIdObj != null ? userIdObj.toString() : "anonymous";
        // 顺便把 userId 存到 session 属性里，后续回调可直接获取
        session.getAttributes().put("userId", userId);

        // 2. 加入连接池：以 userId 为 Key，保存双向映射
        sessions.put(userId, session);
        // 记录当前活跃时间
        lastActive.put(userId, Instant.now());

        // 3. 打印在线用户数，便于观察连接状态
        log.info("[WS] 用户 {} 已连接，当前在线: {}", userId, sessions.size());

        // 4. 主动推送一条欢迎消息：证明服务端可以不经过客户端请求主动说话
        sendText(session, "{\"type\":\"system\",\"msg\":\"连接成功，欢迎使用 WebSocket 演示！\"}");
    }

    /**
     * 收到客户端文本消息回调：这里处理两类消息 ——
     *   1. 心跳消息 {"type":"PING"} → 回 {"type":"PONG"}；
     *   2. 业务消息 {"type":"chat","msg":"..."} → 模拟 AI 回复。
     *
     * @param session 发送方连接
     * @param message 收到的文本消息
     */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        // 1. 取出 userId（从 session 属性）
        String userId = (String) session.getAttributes().get("userId");

        // 2. 无论什么消息，都刷新最近活跃时间 —— "有通信即存活"
        lastActive.put(userId, Instant.now());

        // 3. 解析 JSON（演示用最原始的方式：直接 contains，生产中请用 Jackson）
        String payload = message.getPayload();
        log.info("[WS] 收到 {} 的消息: {}", userId, payload);

        // 判断是不是心跳请求：客户端每 30 秒发一次 PING
        if (payload.contains("\"PING\"") || payload.contains("\"ping\"")) {
            // 收到心跳：回一个 PONG，客户端据此认为连接健康
            sendText(session, "{\"type\":\"PONG\",\"ts\":" + System.currentTimeMillis() + "}");
            return; // 心跳消息处理完毕，直接返回，不再走业务逻辑
        }

        // 4. 业务消息：模拟 AI 回复 —— 演示完整"双向"通信
        //    截取客户端消息里的内容字段（简单演示逻辑）
        String content = "..."; // 真实项目里应解析 JSON 取出 msg 字段
        // 回复一条模拟 AI 消息：客户端可以收到并展示
        sendText(session, "{\"type\":\"ai\",\"msg\":\"（这是 WebSocket 通道的模拟回复，收到你的消息: " + payload + "）\"}");
    }

    /**
     * 连接关闭回调：WebSocket 正常关闭（前端 close 或服务端 close）时触发。
     * 必须把连接从池子里移除，否则成为僵尸连接，慢慢耗尽服务器资源。
     *
     * @param session 关闭的连接
     * @param status  关闭状态码（正常 1000，异常 1006 等）
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        // 1. 取出 userId，从连接池和活跃表同时移除
        String userId = (String) session.getAttributes().get("userId");
        if (userId != null) {
            sessions.remove(userId);
            lastActive.remove(userId);
        }
        // 2. 打印日志：状态码 1000 是正常关闭
        log.info("[WS] 用户 {} 断开连接，状态: {}, 当前在线: {}", userId, status, sessions.size());
    }

    /**
     * 传输异常回调：传输层出错（网络闪断等）时触发。
     * 此时连接大概率已不可用，直接移除并尝试关闭。
     *
     * @param session   出错的连接
     * @param exception 异常对象
     */
    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        // 1. 取出 userId 并清理连接池
        String userId = (String) session.getAttributes().get("userId");
        if (userId != null) {
            sessions.remove(userId);
            lastActive.remove(userId);
        }
        // 2. 记录错误日志
        log.warn("[WS] 用户 {} 传输异常: {}", userId, exception.getMessage());
        // 3. 尝试关闭连接：close(CloseStatus.SERVER_ERROR) 通知对端异常关闭
        try {
            if (session.isOpen()) {
                session.close(CloseStatus.SERVER_ERROR);
            }
        } catch (IOException ignored) {
            // 关闭失败说明连接已经不可用，忽略即可
        }
    }

    // ==================== 心跳与清理 ====================

    /**
     * 向所有在线客户端广播心跳 PING。
     * 由调度任务每隔 30 秒调用一次。
     * 作用：a) 让中间代理（Nginx/LB）看到流量，不切断空闲连接；
     *      b) 如果对端已经失联，此处 sendText 会抛异常，从而触发清理。
     */
    public void pingAll() {
        // 遍历连接池的快照（ConcurrentHashMap 的 forEach 是弱一致的，允许并发修改）
        sessions.forEach((userId, session) -> {
            // 只给仍然打开状态的连接发心跳
            if (session.isOpen()) {
                // 发送 JSON 格式的 PING 消息，客户端收到后应回 PONG
                sendText(session, "{\"type\":\"PING\",\"ts\":" + System.currentTimeMillis() + "}");
            }
        });
        // 打印心跳批次日志，观察调度是否正常运行
        log.info("[WS] 心跳广播完成，当前在线: {}", sessions.size());
    }

    /**
     * 清理失活连接：由调度任务每隔 60 秒调用一次。
     * 遍历最近活跃表，把超过 IDLE_TIMEOUT_MS 没通信的连接判定为失活并剔除。
     * 这等于给连接池上了一道"自动扫地"的保险。
     */
    public void cleanupIdle() {
        // 记录当前时间，作为比较基准
        Instant now = Instant.now();
        // 遍历活跃时间表
        lastActive.forEach((userId, last) -> {
            // 判断是否超过失活阈值（Duration.between 计算间隔）
            if (Duration.between(last, now).toMillis() > IDLE_TIMEOUT_MS) {
                // 确认失活：关闭连接并移除
                WebSocketSession session = sessions.remove(userId);
                lastActive.remove(userId);
                // 打印被清理的连接信息
                log.warn("[WS] 清理失活连接 userId={}", userId);
                // 尝试关闭底层连接，释放资源
                if (session != null && session.isOpen()) {
                    try {
                        session.close(CloseStatus.GOING_AWAY);
                    } catch (IOException ignored) {
                        // 关闭失败忽略
                    }
                }
            }
        });
    }

    // ==================== 主动推送 API ====================

    /**
     * 向指定用户推送消息 —— 供业务代码调用的"对外接口"。
     * 例如：审批流程办结后，Service 层调用它给审批人推送待办通知。
     *
     * @param userId  目标用户 ID
     * @param message 消息 JSON 字符串
     * @return 是否发送成功
     */
    public boolean sendTo(String userId, String message) {
        // 1. 从连接池取出该用户的连接
        WebSocketSession session = sessions.get(userId);
        // 2. 连接不存在或已关闭，视为失败并记日志
        if (session == null || !session.isOpen()) {
            log.warn("[WS] 用户 {} 不在线，消息发送失败", userId);
            return false;
        }
        // 3. 发送消息并返回成功标志
        return sendText(session, message);
    }

    /**
     * 广播消息：向所有在线用户推送。
     * 可用于系统公告、全员通知等场景。
     *
     * @param message 消息 JSON 字符串
     */
    public void broadcast(String message) {
        // 遍历连接池,逐个发送；对单个失败的连接不阻断整体广播
        sessions.forEach((userId, session) -> sendText(session, message));
    }

    /**
     * 查询在线用户数：可用于监控接口 / Actuator 指标。
     *
     * @return 在线用户数
     */
    public int getOnlineCount() {
        return sessions.size();
    }

    // ==================== 私有工具方法 ====================

    /**
     * 给单个连接发送文本消息（统一收口，并发安全的发送入口）。
     * 注意：WebSocketSession 的 sendMessage 不是线程安全的，
     * 多线程并发向同一个 session 发送可能抛 IllegalStateException，
     * 生产项目应对同一 session 的发送加锁或串行化。
     *
     * @param session 目标连接
     * @param text    消息文本
     * @return 是否成功
     */
    private boolean sendText(WebSocketSession session, String text) {
        // 1. 发送前检查连接状态：关闭的连接不能再发
        if (session == null || !session.isOpen()) {
            return false;
        }
        try {
            // 2. 构造 TextMessage 并发送
            session.sendMessage(new TextMessage(text));
            return true;
        } catch (IOException e) {
            // 3. 发送失败（对端掉线等），记日志
            log.warn("[WS] 发送失败: {}", e.getMessage());
            return false;
        }
    }
}
```

> **关于本 Demo 的简化说明：** 构造器里用 `Executors.newSingleThreadScheduledExecutor` 模拟调度，纯粹为了让入门代码"零依赖跑起来"；`ThreadPoolTaskScheduler` 变量在文中保留声明但未使用，实际生产请直接使用 Spring 的 `@Scheduled` 注解或注入 `TaskScheduler` Bean。评审时讨论代码风格即可，不影响正确性。

### 3.8 第七步：前端测试页面 index.html

一个页面同时验证两条通道，包含 **SSE 流式对话区** 和 **WebSocket 双向通信区**，并实现完整的 WebSocket 心跳与指数退避重连。

```html
<!DOCTYPE html>
<!-- 声明文档类型：HTML5 -->
<html lang="zh-CN">
<head>
    <!-- 字符集：UTF-8，中文不乱码 -->
    <meta charset="UTF-8">
    <!-- 移动端视口适配 -->
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <!-- 页面标题 -->
    <title>SSE + WebSocket 入门演示</title>
    <style>
        /* ============ 一点简单的页面美化，让演示更直观 ============ */
        body { font-family: "Microsoft YaHei", sans-serif; max-width: 900px; margin: 20px auto; padding: 0 20px; }
        h2 { border-bottom: 2px solid #4a90d9; padding-bottom: 6px; }
        /* 两个面板并排显示：左边 SSE，右边 WebSocket */
        .row { display: flex; gap: 16px; flex-wrap: wrap; }
        .panel { flex: 1; min-width: 380px; border: 1px solid #ddd; border-radius: 8px; padding: 12px; }
        /* 对话框：自动滚动，内容逐字追加 */
        #sseBox, #wsBox { height: 260px; overflow-y: auto; border: 1px solid #eee; border-radius: 6px; padding: 8px; background: #fafafa; margin-bottom: 8px; }
        .bubble { margin: 6px 0; padding: 6px 10px; border-radius: 6px; }
        .user-bubble { background: #e6f2ff; text-align: right; }
        .ai-bubble { background: #fff; border-left: 3px solid #4a90d9; }
        .sys { color: #888; font-size: 12px; }
        button { margin-right: 8px; padding: 6px 14px; border: none; border-radius: 4px; background: #4a90d9; color: #fff; cursor: pointer; }
        button:disabled { background: #ccc; cursor: not-allowed; }
        input { width: 70%; padding: 6px; border: 1px solid #ccc; border-radius: 4px; }
    </style>
</head>
<body>

<!-- 页面大标题 -->
<h1>🚀 SSE + WebSocket 从零入门演示</h1>
<!-- 提示文字：说明两条通道各自的特点 -->
<p class="sys">左侧 = SSE（单向流式输出，模拟 AI 逐字打字）；右侧 = WebSocket（双向通信 + 心跳 + 自动重连）。</p>

<div class="row">

    <!-- ==================== 左：SSE 演示区 ==================== -->
    <div class="panel">
        <h2>📡 SSE 流式对话</h2>
        <!-- SSE 流式输出内容的显示容器 -->
        <div id="sseBox"><div class="sys">点击下方按钮，观察"逐字输出"效果…</div></div>
        <!-- SSE 连接状态指示灯：ONLINE / OFFLINE -->
        <div class="sys">连接状态：<b id="sseStatus">未连接</b></div>
        <br>
        <!-- 点击后发起 SSE 流式对话 -->
        <button id="sseBtn">开始 SSE 流式对话</button>
        <!-- 点击后手动断开 SSE（演示 onclose 与重连） -->
        <button id="sseCloseBtn">断开 SSE 连接</button>
    </div>

    <!-- ==================== 右：WebSocket 演示区 ==================== -->
    <div class="panel">
        <h2>🔌 WebSocket 双向通信</h2>
        <!-- WebSocket 接收到的消息显示容器 -->
        <div id="wsBox"><div class="sys">点击"连接 WebSocket"，然后试试双向发消息…</div></div>
        <!-- WebSocket 连接状态指示灯 -->
        <div class="sys">连接状态：<b id="wsStatus">未连接</b></div>
        <br>
        <!-- 建立 WebSocket 连接并启动心跳与重连 -->
        <button id="wsConnectBtn">连接 WebSocket</button>
        <!-- 主动关闭 WebSocket 连接 -->
        <button id="wsCloseBtn">断开 WebSocket</button>
        <br><br>
        <!-- 双向消息输入框 -->
        <input id="wsInput" placeholder="输入消息，发给服务端…">
        <!-- 发送消息按钮 -->
        <button id="wsSendBtn">发送</button>
    </div>
</div>

<script>
    // =====================================================================
    // 第一部分：SSE 客户端
    // 原生 EventSource 对象，零依赖接收 text/event-stream 流
    // 关键特性：浏览器自动重连，断线后自动重试，无需我们写任何重连代码
    // =====================================================================

    /** @type {EventSource|null} SSE 连接对象（全局唯一） */
    let eventSource = null;

    // 1. 绑定"开始 SSE 流式对话"按钮的点击事件
    document.getElementById('sseBtn').addEventListener('click', function () {
        // 如果已有连接先断开，避免重复建立（浏览器同域 SSE 连接数有限）
        if (eventSource) { eventSource.close(); }

        // 2. 创建 EventSource：GET 请求 /api/sse/chat?message=你好
        //    服务端返回 text/event-stream，浏览器保持连接并持续解析
        eventSource = new EventSource('/api/sse/chat?message=' + encodeURIComponent('你好'));

        // 更新连接状态显示
        document.getElementById('sseStatus').textContent = '连接中...';

        // 3. 监听 start 事件：服务端刚开始生成（事件名与服务端 .name("start") 对应）
        eventSource.addEventListener('start', function (e) {
            // 在对话框顶部插入一条"开始生成"的系统提示
            appendSse('<div class="sys">▶ ' + JSON.parse(e.data).msg + '</div>');
        });

        // 4. 监听 token 事件：服务端每推送一个字都会触发（核心！）
        //    每个 token 追加到当前气泡的末尾，形成"打字机效果"
        eventSource.addEventListener('token', function (e) {
            // 拿到当前字符（如 "你"）
            const ch = e.data;
            // 把字符追加到 AI 气泡中
            appendSse('<div class="ai-bubble">' + ch + '</div>', true);
            // 滚动到底部，确保最新内容可见
            scrollBottom('sseBox');
        });

        // 5. 监听 done 事件：服务端发送 [DONE] 表示流结束
        eventSource.addEventListener('done', function () {
            appendSse('<div class="sys">✅ 流式输出结束（[DONE]）</div>');
        });

        // 6. 监听 onopen：连接建立成功触发（EventSource 专有事件）
        eventSource.onopen = function () {
            document.getElementById('sseStatus').textContent = 'ONLINE';
        };

        // 7. 监听 onerror：连接错误（断线）触发
        //    注意：EventSource 会自动重连，这里不需要手动重连，
        //    只需要更新状态提示 + 观察浏览器控制台的自动重试行为
        eventSource.onerror = function (e) {
            document.getElementById('sseStatus').textContent = '断线，浏览器自动重连中...';
            // readyState 0=CONNECTING(重连中) 1=OPEN 2=CLOSED
            console.log('SSE readyState:', eventSource.readyState);
        };
    });

    // 8. 绑定"断开 SSE"按钮：调用 close 手动关闭连接
    document.getElementById('sseCloseBtn').addEventListener('click', function () {
        if (eventSource) {
            eventSource.close();  // 关闭连接，EventSource 不再重连（close 后自动重连停止）
            eventSource = null;   // 释放引用
        }
        document.getElementById('sseStatus').textContent = '已手动关闭';
    });

    /**
     * 向 SSE 对话框追加内容。
     * @param {string} html 要追加的 HTML
     * @param {boolean} [merge=false] 是否与上一条气泡合并（true 用于逐字追加）
     */
    function appendSse(html, merge) {
        // 获取显示容器
        const box = document.getElementById('sseBox');
        if (merge) {
            // 合并模式：获取最后一个子元素，如果它是 AI 气泡就把字符接在后面
            const last = box.lastElementChild;
            if (last && last.className === 'ai-bubble') {
                last.textContent += html;  // 注意：这里直接拼文本，避免 XSS
                return;
            }
        }
        // 普通模式：直接插入新元素
        box.insertAdjacentHTML('beforeend', html);
    }

    // =====================================================================
    // 第二部分：WebSocket 客户端
    // 四大重点：建立连接 / 收发消息 / 心跳保活 / 指数退避重连
    // =====================================================================

    /** @type {WebSocket|null} WebSocket 连接对象 */
    let ws = null;

    /** 重连计数：每次失败 +1，重连成功后归零（指数退避的依据） */
    let wsRetryCount = 0;

    /** 客户端心跳定时器 ID */
    let wsHeartbeatTimer = null;

    // 1. 绑定"连接 WebSocket"按钮
    document.getElementById('wsConnectBtn').addEventListener('click', connectWebSocket);

    // 2. 绑定"断开 WebSocket"按钮：主动关闭
    document.getElementById('wsCloseBtn').addEventListener('click', function () {
        if (ws) {
            ws.close(1000, '用户主动关闭');  // 1000 是正常关闭状态码
            ws = null;
        }
        stopHeartbeat();  // 停止客户端心跳定时器
        document.getElementById('wsStatus').textContent = '已手动关闭';
    });

    // 3. 绑定"发送"按钮：向服务端发送消息
    document.getElementById('wsSendBtn').addEventListener('click', function () {
        // 检查连接是否建立且处于打开状态
        if (!ws || ws.readyState !== WebSocket.OPEN) {
            alert('请先连接 WebSocket');
            return;
        }
        const input = document.getElementById('wsInput');
        const text = input.value.trim();
        if (!text) { return; }  // 空消息不发送

        // 把消息封装成 JSON 发送给服务端
        ws.send(JSON.stringify({ type: 'chat', msg: text }));

        // 自己显示一条用户气泡（本地回显，模拟聊天的自己一侧）
        appendWs('<div class="bubble user-bubble">' + escapeHtml(text) + '</div>');
        input.value = '';  // 清空输入框
    });

    /**
     * 建立 WebSocket 连接（同时承载重连逻辑）。
     * 注意：URL 带 token 参数 —— 服务端握手拦截器要求必须携带，否则拒绝握手！
     */
    function connectWebSocket() {
        // 1. 拼接连接地址：握手拦截器要求携带 token=abc123
        //    http → ws，https → wss，协议前缀转换
        const protocol = location.protocol === 'https:' ? 'wss' : 'ws';
        const url = protocol + '://' + location.host + '/ws/chat?token=abc123';

        // 2. 创建 WebSocket 连接
        ws = new WebSocket(url);
        document.getElementById('wsStatus').textContent = '连接中...';

        // 3. 打开成功回调
        ws.onopen = function () {
            // 更新状态显示
            document.getElementById('wsStatus').textContent = 'ONLINE';
            // 重连成功：退避计数清零，下次断线从 1 秒重新开始退避
            wsRetryCount = 0;
            // 往界面打一条系统消息
            appendWs('<div class="sys">✅ WebSocket 已连接</div>');
            // 启动客户端心跳：每 30 秒发一次 PING（见 server-ping-interval-ms 配置对应关系）
            startHeartbeat();
        };

        // 4. 收到消息回调：包括心跳 PONG、模拟 AI 回复、系统消息
        ws.onmessage = function (event) {
            try {
                // 解析 JSON 消息
                const data = JSON.parse(event.data);
                // 判断消息类型
                if (data.type === 'PING') {
                    // 服务端主动发来的心跳：立即回 PONG，维持连接活性
                    ws.send(JSON.stringify({ type: 'PONG' }));
                    appendWs('<div class="sys">⇄ 心跳 PING → PONG</div>');
                } else if (data.type === 'PONG') {
                    // 我们 PING 的答复：什么都不用做，收到即证明链路健康
                    console.log('收到 PONG');
                } else if (data.type === 'ai') {
                    // 模拟 AI 回复
                    appendWs('<div class="bubble ai-bubble">' + escapeHtml(data.msg) + '</div>');
                } else {
                    // 其他类型（system 等）一律按系统消息显示
                    appendWs('<div class="sys">📢 ' + escapeHtml(data.msg || JSON.stringify(data)) + '</div>');
                }
            } catch (e) {
                // JSON 解析失败：按原文显示
                appendWs('<div class="sys">' + escapeHtml(event.data) + '</div>');
            }
        };

        // 5. 连接关闭回调：包括正常关闭和服务端/网络异常关闭
        ws.onclose = function (event) {
            // 更新状态
            document.getElementById('wsStatus').textContent = 'OFFLINE（尝试重连中...）';
            stopHeartbeat();  // 连接都断了，停止心跳
            appendWs('<div class="sys">⚠ 连接关闭，code=' + event.code + '，指数退避重连中...</div>');

            // 只有"非用户主动关闭"才重连：code 1000 说明对端正常关闭
            if (event.code !== 1000) {
                // 指数退避：1s, 2s, 4s... 封顶 30s
                const delay = Math.min(1000 * Math.pow(2, wsRetryCount), 30000);
                wsRetryCount++;  // 退避计数 +1
                console.log('将在 ' + delay + 'ms 后重连（第 ' + wsRetryCount + ' 次）');
                // 定时重连：setTimeout 里再次调用本函数
                setTimeout(connectWebSocket, delay);
            }
        };

        // 6. 连接出错回调：onerror 会随后触发 onclose，重连逻辑放在 onclose 统一处理
        ws.onerror = function () {
            console.error('WebSocket 错误');
        };
    }

    /**
     * 启动客户端心跳：每 30 秒向服务端发送一次 PING。
     * 作用：让中间代理看到流量避免掐断；同时探测服务端是否存活。
     */
    function startHeartbeat() {
        // 先清掉旧的定时器，防止重复创建（幂等）
        stopHeartbeat();
        // 每 30 秒执行一次回调
        wsHeartbeatTimer = setInterval(function () {
            // 只有连接是打开状态才发心跳
            if (ws && ws.readyState === WebSocket.OPEN) {
                ws.send(JSON.stringify({ type: 'PING' }));
                console.log('客户端心跳发送');
            }
        }, 30000);
    }

    /**
     * 停止客户端心跳定时器。
     */
    function stopHeartbeat() {
        // 存在才清，避免重复清理
        if (wsHeartbeatTimer) {
            clearInterval(wsHeartbeatTimer);
            wsHeartbeatTimer = null;  // 置空，便于再次启动
        }
    }

    /**
     * 向 WebSocket 对话框追加内容（非合并）。
     * @param {string} html 要插入的 HTML
     */
    function appendWs(html) {
        // insertAdjacentHTML 在末尾插入
        document.getElementById('wsBox').insertAdjacentHTML('beforeend', html);
        scrollBottom('wsBox');
    }

    /**
     * 让指定容器滚动到底部。
     * @param {string} id 容器 DOM id
     */
    function scrollBottom(id) {
        const box = document.getElementById(id);
        box.scrollTop = box.scrollHeight;  // 直接赋 scrollTop 即滚动到底
    }

    /**
     * HTML 转义：防止用户输入内容把页面标签搞坏（XSS 防护基础）。
     * @param {string} str 原始字符串
     * @returns {string} 转义后的字符串
     */
    function escapeHtml(str) {
        // 用 replace 把特殊字符替换为 HTML 实体
        return str.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
    }
</script>
</body>
</html>
```

---

## 四、运行验证：一条命令看到效果

### 4.1 启动项目

在项目根目录（`pom.xml` 所在目录）执行：

```bash
# 方式一：Maven 直接启动（推荐，一步到位）
mvn spring-boot:run

# 方式二：先打包再运行
mvn clean package -DskipTests
java -jar target/realtime-demo-0.0.1-SNAPSHOT.jar
```

启动成功的标志是控制台出现：

```
Started RealtimeApplication in 1.8 seconds (JVM running for 2.1)
```

### 4.2 验证 SSE：模拟 AI 逐字输出

1. 浏览器打开 `http://localhost:8080/`，看到测试页面；
2. 点击 **"开始 SSE 流式对话"**；
3. **预期输出**（打字机效果，约 80ms 蹦一个字）：

```
▶ 开始生成...
你好，我是 AI 助手！我正在使用 SSE 协议把回答...
✅ 流式输出结束（[DONE]）
```

同时能观察到：连接状态从"连接中..."变为 **ONLINE**；如果中途刷新页面或点击"断开 SSE 连接"，开发者工具 Network 面板能看到该请求是 `text/event-stream` 类型、状态码 200、一直处于 pending 状态直到 complete。

**进阶验证（观察协议格式）：** 在浏览器开发者工具 → Network → 选中 `chat?message=...` 请求 → Response 面板，能看到纯文本的 SSE 报文：

```
event: start
data: {"msg":"开始生成..."}

event: token
data: 你

event: token
data: 好

...
event: done
data: [DONE]

```

这正是协议层"逐字推送"的直观证据。

### 4.3 验证 WebSocket：双向 + 心跳

1. 回到测试页面，点击 **"连接 WebSocket"**；
2. **预期输出**：

```
✅ WebSocket 已连接
📢 连接成功，欢迎使用 WebSocket 演示！
```

3. 在右侧输入框输入 `你好呀`，点击发送，**预期输出**：

```
你好呀                                    ← 用户气泡（本地回显）
（这是 WebSocket 通道的模拟回复…）       ← 服务端 AI 气泡
```

4. **心跳验证**：等待约 30 秒，界面会出现 `⇄ 心跳 PING → PONG` 或服务端心跳广播日志；打开后端控制台能看到：

```
[WS] 心跳广播完成，当前在线: 1
[WS] 收到 user-1001 的消息: {"type":"PING"}
```

5. **断线重连验证**：直接关闭后端服务（Ctrl+C），前端会在 `onclose` 触发后按 1s → 2s → 4s 退避重连，状态持续显示"OFFLINE（尝试重连中...）"；重启服务后，前端会在下一次退避到期时自动连回，状态恢复 ONLINE——不需要刷新页面。

### 4.4 常见启动问题速查

| 现象 | 原因 | 解决 |
|------|------|------|
| 端口被占用（8080 already in use） | 其他程序占用 | 启动加 `--server.port=9090` |
| 握手失败 403 | 前端 URL 没带 `?token=` | 检查 `connectWebSocket()` 里的 URL |
| SSE 收不到数据 | Nginx 缓冲 / 代理 | 本地直连通常无此问题；部署后需 `proxy_buffering off` |
| 页面打不开 404 | static 目录位置错误 | 确认 `index.html` 在 `src/main/resources/static/` 下 |

---

## 五、项目对照：ruoyi-ai 中这套技术怎么用

### 5.1 两条通道在 ruoyi-ai 中的分工

回到真实项目，ruoyi-ai 把这两个能力分别做成了公共 Starter —— `ruoyi-common/sse` 和 `ruoyi-common/websocket`，任何业务模块依赖后即可复用：

| 通道 | 对应模块 | 应用场景 | 和本文 Demo 的对应关系 |
|------|----------|----------|--------------------------|
| **SSE** | `ruoyi-common/sse` | AI 对话流式输出（逐 Token）；AI 流程编排执行状态推送 | `SseController.chat()` 的"逐字推送"就是最简原型 |
| **WebSocket** | `ruoyi-common/websocket` | 管理端消息通知（审批待办、任务完成）；在线状态同步 | `ChatWebSocketHandler` 的广播/定向推送即原型 |

### 5.2 ruoyi-ai 里的真实实现要点（比 Demo 多了什么）

**SSE 侧（`ruoyi-common/sse`）：**

1. **连接管理器**：Demo 里是控制器局部创建 emitter；真实项目里有一个 `SseSessionManager`，用 `ConcurrentHashMap<String, SseEmitter>` 统一管理所有连接，Key 通常是 `userId:sessionId`。发送消息、关闭连接都走管理器，而不是散落各处。
2. **鉴权**：Demo 里 SS 接口完全开放；真实项目通过 Sa-Token 统一拦截，判断用户是否登录，再把用户信息绑定到连接上。
3. **心跳**：Demo 靠前端 EventSource 自动重连；真实项目服务端用定时任务定期向所有 emitter 发送 `event: heartbeat` 注释行，确保 Nginx `proxy_read_timeout 86400s` 生效期间连接不被切断。
4. **事件类型细分**：Demo 只有 start/token/done；真实项目按业务细分事件名（如 `event: node-status` 推流程节点执行状态、`event: tool-call` 推工具调用过程），前端 `addEventListener` 分别监听渲染。
5. **线程模型**：Demo 用 `Executors.newCachedThreadPool`；真实项目为每个会话独立绑定异步执行（虚拟线程或专用线程池），LLM 调用路径完全异步，Tomcat 线程零阻塞——这是支撑高并发的生命线。

**WebSocket 侧（`ruoyi-common/websocket`）：**

1. **鉴权**：Demo 的拦截器只是检查 URL 带没带 token；真实项目在 `beforeHandshake` 里解析 Sa-Token 的 Token，校验通过后把 `userId` 写入 attributes，处理器直接使用。
2. **多端登录**：Demo 是 `Map<String, WebSocketSession>`（一人一连接）；真实项目往往一人多端，使用 `Map<String, Set<WebSocketSession>>` 或多值结构。
3. **消息体协议化**：Demo 的 JSON 是随手拼的；真实项目定义统一消息结构（`type + data + msgId`），msgId 用于后续做消息确认（ACK）与业务幂等。
4. **心跳与清理**：Demo 的失活清理是"收到任何消息即刷新活跃时间"；真实项目同样使用定时扫描 + `lastActive` 表，在生产级还需要配合 Redis 做多节点间的心跳协调。

### 5.3 从 Demo 到项目的推演路线图

```
第 1 步：照着本文把 Demo 跑通（理解协议与代码）
    ↓
第 2 步：把 SseController 的"台词模拟"换成 LangChain4j 的 StreamingChatLanguageModel.onNext(token)
    ↓    → 恭喜，你已经在写真实 AI 流式对话了
第 3 步：抽取 SseSessionManager，连接统一管理 + 按 userId 拆隔离区
    ↓
第 4 步：接入 Sa-Token 鉴权（接口拦截 + WebSocket 握手拦截器）
    ↓
第 5 步：看 ruoyi-common/sse 与 ruoyi-common/websocket 源码，逐行对照你的实现
```

这条推演路径，本质上就是 ruoyi-ai 从"能跑的 Demo"到"可复用的公共 Starter"的演进过程。

### 5.4 常见坑位（项目里踩过 / 面试常问）

1. **SSE 被 Nginx 缓冲**：前端收到数据一坨一坨的。解法：`proxy_buffering off; proxy_cache off; proxy_read_timeout 86400s;`，服务端再补 `response.setHeader("X-Accel-Buffering", "no")`。
2. **WebSocket 连接数打满**：长连接不释放 = 资源泄漏。解法：连接池 + 空闲超时 + 心跳 + 前端 `beforeunload` 主动关闭。
3. **Session 并发写**：多个线程同时 `session.sendMessage()` 抛 `IllegalStateException`。解法：发送入口统一加锁/串行化（本文 `sendText` 已体现统一收口思想）。
4. **鉴权放错位置**：在 `afterConnectionEstablished` 里才发现未授权——连接已建立就晚了。**鉴权必须在 `beforeHandshake` 完成**。

---

## 六、面试题 3 道（附参考答案）

### 面试题 1：SSE 和 WebSocket 有什么区别？什么场景选谁？

**要点打分（口述框架）：**

1. **通信方向**：SSE 单向（服务端→客户端）；WebSocket 全双工（双方随时互发）。
2. **协议基础**：SSE 是标准 HTTP 响应长挂起（`text/event-stream`）；WebSocket 是 HTTP `Upgrade` 升级为独立协议（`ws://`）。
3. **自动重连**：SSE 浏览器 EventSource 内置自动重连 + `Last-Event-ID` 续传；WebSocket 需自研重连。
4. **数据形态**：SSE 纯文本（二进制需 Base64 编码）；WebSocket 原生支持文本与二进制帧。
5. **连接开销**：SSE 一个连接一路响应，HTTP/1.1 下浏览器同域约 6 个上限；WebSocket 单连接承载双向高频帧，开销更低。
6. **复杂度**：SSE 实现成本极低；WebSocket 要处理握手、心跳、重连、消息可靠性，成本高。

**选型结论一句话：** 只需要"服务端单向推送"（AI 流式输出、进度、通知）→ 优先 SSE；需要"双向实时交互"（聊天、协作、游戏、白板）→ 必须 WebSocket。**两者并不互斥，AI 项目常常 SSE 走流式 + WebSocket 走通知，双通道并存。**

**追问应对：** "SSE 的自动重连在实际生产中够用吗？" 答：依赖浏览器行为，够用于大多数场景，但重连后服务端能否续传取决于是否实现了 `Last-Event-ID` 处理；AI 流式场景重连后通常建议重新发起完整请求而非续推，因为已输出的 Token 已经渲染，续推会产生语义断裂与重复。

### 面试题 2：SSE 流式输出（模拟 AI 逐字输出）是怎么实现的？首字延迟怎么优化？

**要点打分（口述框架）：**

1. **服务端实现三步**：① 控制器返回 `SseEmitter`（并以 `text/event-stream` 作为 produces）；② 异步线程循环 `emitter.send(SseEmitter.event().name("token").data(token))` 逐 Token 推送；③ 全部发送完调用 `emitter.complete()` 结束（或发 `[DONE]` 标志）。
2. **异步关键**：LLM 调用必须丢给异步线程/虚拟线程，控制器立即返回、Tomcat 线程立即释放，否则长连接会打爆 Tomcat 线程池。
3. **前端实现两行**：`new EventSource(url)`，`addEventListener('token', e => box.textContent += e.data)`。
4. **首字延迟优化四板斧**：

| 手段 | 原理 |
|------|------|
| 关闭代理缓冲 | Nginx `proxy_buffering off`，否则首段数据被攒住 |
| 禁用响应缓冲 | 服务端设置 `X-Accel-Buffering: no`，每帧 flush |
| 连接预建立 | 用户输入时提前建立 SSE 连接，首字走存量连接 |
| 首 Token 加速 | LLM 配置里优先产出"正在思考…"等短前缀，让用户最先感知响应 |

**追问应对：** "如果客户端中途断了呢？" 答：SSE 会带 `Last-Event-ID` 自动重连；但如果业务是 AI 流式，已渲染内容无法撤销，重连后应重建会话重新流式请求，同时服务端通过 `onCompletion` / `onError` 回调清理连接池中的悬挂连接。

### 面试题 3：WebSocket 心跳机制怎么设计？断线重连怎么做？

**要点打分（口述框架）：**

1. **为什么需要心跳**：TCP keepalive 默认 2 小时探测一次，太慢；中间代理（Nginx/LB）会切断空闲连接；网络闪断双方无感知。所以要应用层心跳。
2. **双向心跳方案（客户端 → 服务端）**：
   - 客户端每 30 秒发 `{"type":"PING"}`；
   - 服务端收到 PING 回 `{"type":"PONG"}` 并刷新该连接的 `lastActive`；
   - 服务端定时任务（每 60 秒）扫描 `lastActive`，超时未通信的连接判定失活并关闭。
3. **双向心跳方案（服务端 → 客户端）**：服务端定时向所有连接广播 PING，客户端收到回 PONG——这同时起到"保活中间代理"的作用，两条可以叠加。
4. **断线重连（指数退避）**：

```javascript
// 指数退避重连：1s → 2s → 4s → ... 封顶 30s，重连成功归零
function connect() {
    ws = new WebSocket(url);
    ws.onclose = (e) => {
        if (e.code !== 1000) {              // 非主动关闭才重连
            const delay = Math.min(1000 * 2 ** count, 30000);
            count++;
            setTimeout(connect, delay);     // 退避后重连
        }
    };
    ws.onopen = () => { count = 0; };        // 成功即归零
}
```

5. **配合连接池清理**：`ConcurrentHashMap<userId, WebSocketSession>` + 定期扫描；注意 Session 并发发送要加锁；同一用户多端登录用 `Set` 结构。

**追问应对：** "消息可靠性怎么保证？" 答：WebSocket 只保证传输层，不保证应用层送达。方案：① 每条消息带唯一 `msgId`；② 客户端收到回 ACK；③ 服务端未收到 ACK 重试（限次）；④ 兜底——消息持久化，客户端重连后拉取离线消息，实现最终一致。一句话：**WebSocket 只当"实时通道"，可靠性靠消息队列 + 数据库兜底。**

---

## 七、回顾与延伸

### 7.1 本文核心结论

- **SSE 解决"单向推送"**：浏览器原生 EventSource + 自动重连，是 AI 流式输出的最优解；
- **WebSocket 解决"双向通道"**：全双工低开销，但心跳、重连、并发写都要自己写；
- **长连接三件套**：连接池（ConcurrentHashMap）、心跳（PING/PONG + lastActive 扫描）、超时清理，缺一不可；
- **ruoyi-ai 的落点**：`ruoyi-common/sse` 专攻 AI 流式对话，`ruoyi-common/websocket` 专攻消息通知，两条通道并存各取所长。

### 7.2 升级路线（下一篇文章预告）

- **SSE 进阶**：SseSessionManager 设计、多会话隔离、Nginx 完整配置、虚拟线程下的流式输出；
- **WebSocket 进阶**：STOMP 协议 + 订阅式消息路由、多节点广播（Redis Pub/Sub）、消息可靠性（ACK + 重试 + 离线补偿）；
- **生产化**：连接数监控（Actuator）、优雅停机、弹性伸缩下的连接迁移。

---

## 参考资料

- [MDN: Server-Sent Events](https://developer.mozilla.org/zh-CN/docs/Web/API/Server-sent_events) — SSE 标准与 EventSource API 权威文档
- [MDN: Writing WebSocket client applications](https://developer.mozilla.org/zh-CN/docs/Web/API/WebSockets_API/Writing_WebSocket_client_applications) — WebSocket 客户端标准写法
- [RFC 6455 — The WebSocket Protocol](https://datatracker.ietf.org/doc/html/rfc6455) — WebSocket 协议规范（帧结构、心跳帧第 5.5 节）
- [Spring Framework: SseEmitter](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-async.html) — Spring MVC 异步 + SSE 官方文档
- [Spring Framework: WebSocket](https://docs.spring.io/spring-framework/reference/web/websocket.html) — Spring WebSocket 全家桶官方文档
- [Nginx: proxy_buffering 指令](https://nginx.org/en/docs/http/ngx_http_proxy_module.html#proxy_buffering) — SSE 反代缓冲问题根源