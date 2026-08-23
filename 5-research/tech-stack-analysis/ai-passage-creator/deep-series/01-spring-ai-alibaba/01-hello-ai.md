# 01 Spring AI Alibaba入门：5分钟跑通第一个AI对话

> 本文是 ai-passage-creator 项目技术栈深度剖析系列的第 1 篇（入门篇）。面向 Java 初学者，手把手带你从零搭建一个 Spring AI Alibaba 项目，跑通第一个 AI 对话。
>
> **对应项目：** `ai-passage-creator/ai-passage-creator-java` 模块
> **难度等级：** Level 1 入门
> **预计阅读时间：** 15 分钟（含代码实操）

---

## 一、项目背景

### 1.1 什么是 Spring AI Alibaba

Spring AI Alibaba 是阿里巴巴开源的一款 AI 应用开发框架，它基于 Spring AI 官方规范构建，深度集成了阿里云的大模型服务（通义千问 / DashScope）。

简单来说，它的作用是：**让 Java 开发者用最少的代码调用大模型 API**。

在 ai-passage-creator 项目中，所有 5 个 AI Agent 的对话能力都依赖于 Spring AI Alibaba 的 ChatClient 组件。项目使用通义千问（qwen-max 和 qwen-plus 模型）完成标题生成、大纲撰写、正文创作等任务。

### 1.2 为什么选择 Spring AI Alibaba 而不是 LangChain4j

Java 生态中主流的 AI 框架有两个：

| 对比维度 | Spring AI Alibaba | LangChain4j |
|---------|-------------------|-------------|
| 厂商 | 阿里巴巴 | 社区开源 |
| 对接模型 | 通义千问（DashScope）为主 | OpenAI、通义千问等多家 |
| 工作流引擎 | 内置 StateGraph（DAG 编排） | 需要额外集成 |
| Spring 生态 | 原生 Spring Boot 集成 | 通过 Spring Boot Starter 集成 |
| 中文支持 | 官方中文文档，阿里云原生 | 英文为主 |

ai-passage-creator 选择 Spring AI Alibaba 的核心原因：
1. **StateGraph 工作流引擎**：项目需要编排 5 个 Agent 的协作流程，StateGraph 是原生支持
2. **阿里云生态**：项目部署在阿里云，DashScope 调用延迟低
3. **中文优化**：通义千问对中文内容的理解和生成质量优秀

### 1.3 本文的目标

读完本文，你将能够：
- 创建 Spring Boot + Spring AI Alibaba 项目
- 配置 DashScope（通义千问）API Key
- 使用 ChatClient 调用大模型完成对话
- 编写单元测试验证对话功能
- 理解项目中的实际应用场景

---

## 二、核心概念

### 2.1 ChatClient

ChatClient 是 Spring AI Alibaba 的核心 API，它封装了所有与大模型交互的细节。

**核心方法：**

```java
// 最简调用 —— 发送一句话，得到一句话
String answer = chatClient.call("你好");
// "你好！我是通义千问，有什么可以帮助你的吗？"

// 带参数调用 —— 控制系统提示词
String answer = chatClient.call(new Prompt("请用中文回答：什么是AI？"));
```

**ChatClient 的核心概念：**

| 概念 | 说明 | 类比 |
|------|------|------|
| Prompt | 用户发送给模型的输入消息 | 你问的问题 |
| Message | 单条消息，包含角色和内容 | 对话中的一句话 |
| SystemMessage | 系统角色消息，设定 AI 的行为和身份 | AI 的"人设" |
| UserMessage | 用户角色消息，提出需求 | 你的提问 |
| AssistantMessage | 助手角色消息，模型的回复 | AI 的回答 |
| ChatResponse | 模型的完整响应，包含元数据 | 收到的完整回复 |

### 2.2 DashScope

DashScope（灵积）是阿里云提供的大模型推理服务，通义千问系列模型通过 DashScope API 对外提供服务。

**DashScope 的配置三要素：**

```
api-key: 你的 API Key（从阿里云 DashScope 控制台获取）
model:   模型名称（如 qwen-max、qwen-plus）
base-url: API 地址（默认 https://dashscope.aliyuncs.com）
```

### 2.3 项目中的实际应用

在 ai-passage-creator 中，ChatClient 被封装在每个 Agent 中：

```java
// 项目中的 TitleGeneratorAgent 使用 ChatClient 调用 AI
@Service
public class TitleGeneratorAgent {
    private final ChatClient chatClient;

    public TitleGeneratorAgent(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    public String generateTitles(String topic) {
        // 调用大模型生成标题
        return chatClient.call(new Prompt(
            "你是一个文章标题生成专家。请为以下主题生成 3-5 个吸引人的标题：" + topic
        )).getResult().getOutput().getContent();
    }
}
```

---

## 三、从零搭建代码

### 3.1 创建项目结构

我们先创建一个全新的 Maven 项目，目录结构如下：

```
spring-ai-alibaba-demo/
├── pom.xml
├── src/
│   ├── main/
│   │   ├── java/com/example/ai/
│   │   │   ├── AiDemoApplication.java        # 启动类
│   │   │   ├── controller/
│   │   │   │   └── ChatController.java       # 对话控制器
│   │   │   └── service/
│   │   │       └── AiChatService.java         # AI 对话服务
│   │   └── resources/
│   │       ├── application.yml               # 配置文件
│   │       └── application-dev.yml           # 开发环境配置
│   └── test/
│       └── java/com/example/ai/
│           └── AiChatServiceTest.java         # 单元测试
```

### 3.2 pom.xml —— 引入依赖

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!-- Spring AI Alibaba 入门示例 —— Maven 项目配置文件 -->
<!-- 本文件定义了项目所需的所有依赖 -->
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <!-- 父工程：Spring Boot 3.2.5，提供起步依赖管理 -->
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.2.5</version>
        <relativePath/>
    </parent>

    <!-- 项目坐标 -->
    <groupId>com.example</groupId>
    <artifactId>spring-ai-alibaba-demo</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <name>Spring AI Alibaba Demo</name>
    <description>Spring AI Alibaba 入门示例 —— 5分钟跑通第一个AI对话</description>

    <!-- 版本属性集中管理 -->
    <properties>
        <java.version>17</java.version>
        <!-- Spring AI Alibaba 版本号，和项目使用的版本一致 -->
        <spring-ai-alibaba.version>1.1.0</spring-ai-alibaba.version>
    </properties>

    <!-- 依赖管理：引入 Spring AI Alibaba BOM，统一管理版本 -->
    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>com.alibaba.cloud.ai</groupId>
                <artifactId>spring-ai-alibaba-bom</artifactId>
                <version>${spring-ai-alibaba.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <dependencies>
        <!-- Spring Boot Web 起步依赖 —— 提供 REST API 能力 -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>

        <!-- Spring AI Alibaba 起步依赖 —— 核心依赖，包含 ChatClient、DashScope 等 -->
        <dependency>
            <groupId>com.alibaba.cloud.ai</groupId>
            <artifactId>spring-ai-alibaba-starter</artifactId>
        </dependency>

        <!-- Lombok —— 简化代码，自动生成 getter/setter/构造函数 -->
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>

        <!-- Spring Boot 测试起步依赖 -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <!-- Spring Boot Maven 插件，用于打包可执行 JAR -->
    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <configuration>
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

**关键依赖说明：**

- `spring-boot-starter-parent:3.2.5`：Spring Boot 父工程，管理所有起步依赖的版本
- `spring-ai-alibaba-starter:1.1.0`：Spring AI Alibaba 核心起步依赖，自动配置 DashScope ChatClient、Embedding、Image 等模型客户端
- `spring-ai-alibaba-bom:1.1.0`：BOM（Bill of Materials），统一管理 Spring AI Alibaba 所有子模块的版本

### 3.3 application.yml —— 配置文件

```yaml
# Spring AI Alibaba 入门示例 —— 主配置文件
# 使用 kebab-case（短横线命名法）格式
spring:
  application:
    name: spring-ai-alibaba-demo

  # AI 相关配置
  ai:
    # DashScope（阿里云通义千问）配置
    dashscope:
      # API Key：从阿里云 DashScope 控制台获取
      # 生产环境建议通过环境变量 SPRING_AI_DASHSCOPE_API_KEY 注入
      api-key: ${DASHSCOPE_API_KEY:your-api-key-here}
      # 通义千问模型配置
      chat:
        options:
          # 模型名称：qwen-max 是通义千问最大参数版本
          # 可选：qwen-plus（均衡）、qwen-turbo（快速）、qwen-max（最强）
          model: qwen-max
          # 温度参数：0-2，值越高输出越随机，值越低越确定
          # 创作类任务建议 0.8，事实类任务建议 0.3
          temperature: 0.8
          # 最大 Token 数：单次生成的最大 Token 数量
          max-tokens: 2048

# 服务端口配置
server:
  port: 8080

# 日志配置
logging:
  level:
    # 打印 Spring AI 的请求日志，方便调试
    com.alibaba.cloud.ai: DEBUG
```

**配置项详解：**

| 配置项 | 说明 | 建议值 |
|--------|------|--------|
| `spring.ai.dashscope.api-key` | DashScope API Key | 通过环境变量注入，不要硬编码 |
| `spring.ai.dashscope.chat.options.model` | 模型名称 | qwen-max（最强）、qwen-plus（均衡） |
| `spring.ai.dashscope.chat.options.temperature` | 随机性参数 | 0.3-0.8，创作类调高，事实类调低 |
| `spring.ai.dashscope.chat.options.max-tokens` | 最大生成 Token 数 | 对话 2048，长文 4096+ |

### 3.4 启动类 —— AiDemoApplication.java

```java
package com.example.ai;

// Spring Boot 启动类
// @SpringBootApplication 包含三个注解：
// @Configuration（声明配置类）
// @EnableAutoConfiguration（开启自动配置）
// @ComponentScan（自动扫描组件）
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring AI Alibaba 入门示例 —— 主启动类
 *
 * 启动后，Spring Boot 会自动加载：
 * 1. application.yml 中的 DashScope 配置
 * 2. ChatClient Bean（由 Spring AI Alibaba 自动配置）
 * 3. 扫描所有 @Component、@Service、@Controller 注解
 */
@SpringBootApplication
public class AiDemoApplication {

    public static void main(String[] args) {
        // 启动 Spring Boot 应用
        // 启动成功后，ChatClient 和 DashScope 客户端会自动初始化
        SpringApplication.run(AiDemoApplication.class, args);
        System.out.println("========================================");
        System.out.println("Spring AI Alibaba Demo 启动成功！");
        System.out.println("访问 http://localhost:8080/chat?message=你好 体验 AI 对话");
        System.out.println("========================================");
    }
}
```

### 3.5 服务层 —— AiChatService.java

```java
package com.example.ai.service;

// 引入 Spring AI Alibaba 的核心 API
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * AI 对话服务 —— 封装 ChatClient 的调用逻辑
 *
 * 职责：
 * 1. 提供最简的问答接口（一问一答）
 * 2. 提供带系统提示词的对话接口（设定 AI 角色）
 * 3. 提供带历史记录的对话接口（多轮对话）
 */
@Service // 标记为 Spring 服务 Bean，自动被 @ComponentScan 扫描到
public class AiChatService {

    // ChatClient：Spring AI Alibaba 的核心对话客户端
    // 由 Spring Boot 自动配置注入（DashScopeAutoConfiguration 自动创建）
    private final ChatClient chatClient;

    /**
     * 构造器注入 —— 推荐方式
     *
     * ChatClient.Builder 是 ChatClient 的构建器，
     * Spring AI Alibaba 自动配置了一个 DashScopeChatClient.Builder
     *
     * @param builder ChatClient 构建器，由 Spring 自动注入
     */
    public AiChatService(ChatClient.Builder builder) {
        // 使用默认配置构建 ChatClient
        // 配置来源：application.yml 中的 spring.ai.dashscope 配置
        this.chatClient = builder.build();
    }

    /**
     * 最简问答 —— 一问一答
     *
     * 适用场景：简单的翻译、问答、摘要等一次性任务
     *
     * @param message 用户输入的消息
     * @return AI 的回复内容
     */
    public String simpleChat(String message) {
        // 1. 创建用户消息
        // UserMessage 表示用户发送的消息
        UserMessage userMessage = new UserMessage(message);

        // 2. 创建 Prompt（提示词），包含用户消息
        // Prompt 是 ChatClient 的输入，可以包含多条消息
        Prompt prompt = new Prompt(userMessage);

        // 3. 调用 ChatClient 发送请求
        // call() 方法是同步调用，会阻塞等待 AI 回复
        ChatResponse response = chatClient.call(prompt);

        // 4. 获取 AI 回复内容
        // getResult() 获取第一个生成结果
        // getOutput() 获取输出消息
        // getContent() 获取文本内容
        return response.getResult()
                .getOutput()
                .getContent();
    }

    /**
     * 带系统提示词的对话 —— 设定 AI 角色
     *
     * 适用场景：需要限定 AI 的身份、行为、输出格式
     * 例如：让 AI 扮演翻译官、代码审查员、写作助手等
     *
     * @param message      用户输入
     * @param systemPrompt 系统提示词（角色设定）
     * @return AI 回复
     */
    public String chatWithSystem(String message, String systemPrompt) {
        // 1. 创建系统消息 —— 设定 AI 的角色和行为
        // SystemMessage 告诉 AI 应该以什么身份、什么风格回答问题
        SystemMessage systemMessage = new SystemMessage(systemPrompt);

        // 2. 创建用户消息 —— 用户的提问
        UserMessage userMessage = new UserMessage(message);

        // 3. 创建 Prompt，包含系统消息和用户消息
        // 消息的顺序很重要：系统消息在前，用户消息在后
        Prompt prompt = new Prompt(List.of(systemMessage, userMessage));

        // 4. 调用并返回结果
        ChatResponse response = chatClient.call(prompt);
        return response.getResult()
                .getOutput()
                .getContent();
    }

    /**
     * 带历史记录的对话 —— 多轮对话
     *
     * 适用场景：需要 AI 记住前面聊了什么
     * 例如：一步步修改文章、追问某个话题
     *
     * @param message 用户的最新输入
     * @param history 历史消息列表（包含之前的用户消息和 AI 回复）
     * @return AI 回复
     */
    public String chatWithHistory(String message, List<org.springframework.ai.chat.messages.Message> history) {
        // 1. 创建当前用户消息
        UserMessage userMessage = new UserMessage(message);

        // 2. 合并历史消息和当前消息
        // 让 AI 基于之前的对话上下文来回答
        history.add(userMessage);

        // 3. 创建 Prompt
        Prompt prompt = new Prompt(history);

        // 4. 调用并返回结果
        ChatResponse response = chatClient.call(prompt);
        return response.getResult()
                .getOutput()
                .getContent();
    }
}
```

**代码逐行解读：**

| 代码行 | 说明 |
|--------|------|
| `ChatClient.Builder` | 构建器模式，Spring AI Alibaba 自动配置 |
| `new UserMessage(message)` | 创建用户消息，role=user |
| `new SystemMessage(systemPrompt)` | 创建系统消息，role=system |
| `new Prompt(...)` | 封装消息列表，发送给模型 |
| `chatClient.call(prompt)` | 同步调用，阻塞等待回复 |
| `response.getResult().getOutput().getContent()` | 从响应中提取文本内容 |

### 3.6 控制器层 —— ChatController.java

```java
package com.example.ai.controller;

import com.example.ai.service.AiChatService;
import org.springframework.ai.chat.messages.Message;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * AI 对话控制器 —— 提供 REST API 接口
 *
 * 提供三个接口：
 * 1. GET /chat —— 最简问答
 * 2. GET /chat/system —— 带系统提示词的对话
 * 3. POST /chat/history —— 带历史记录的多轮对话
 */
@RestController // 标记为 REST 控制器，所有方法返回 JSON
@RequestMapping("/chat") // 请求路径前缀
public class ChatController {

    // 注入 AI 对话服务
    private final AiChatService aiChatService;

    public ChatController(AiChatService aiChatService) {
        this.aiChatService = aiChatService;
    }

    /**
     * 最简问答接口
     *
     * 请求示例：GET http://localhost:8080/chat?message=你好
     * 响应示例：{"code":200,"data":"你好！我是通义千问...","message":"success"}
     */
    @GetMapping // 处理 GET 请求
    public Map<String, Object> chat(@RequestParam(defaultValue = "你好") String message) {
        // 调用服务层
        String answer = aiChatService.simpleChat(message);

        // 返回统一格式的响应
        return Map.of(
            "code", 200,
            "data", answer,
            "message", "success"
        );
    }

    /**
     * 带系统提示词的对话接口
     *
     * 请求示例：GET http://localhost:8080/chat/system?message=什么是Spring Boot&system=你是一位Java技术专家
     * 响应示例：AI 会以 Java 技术专家的身份回答
     */
    @GetMapping("/system") // 处理 GET /chat/system 请求
    public Map<String, Object> chatWithSystem(
            @RequestParam(defaultValue = "请介绍一下你自己") String message,
            @RequestParam(defaultValue = "你是一位AI助手，请用中文回答") String system) {
        // 调用带系统提示词的服务方法
        String answer = aiChatService.chatWithSystem(message, system);

        return Map.of(
            "code", 200,
            "data", Map.of(
                "system", system,
                "message", message,
                "answer", answer
            ),
            "message", "success"
        );
    }

    /**
     * 带历史记录的对话接口
     *
     * 请求示例：
     * POST http://localhost:8080/chat/history
     * Body: {"message":"刚才我们聊了什么？","history":["你好","你好！我是AI助手"]}
     *
     * 注意：本示例为简化版本，生产环境建议使用 Redis 或数据库持久化历史记录
     */
    @PostMapping("/history") // 处理 POST /chat/history 请求
    public Map<String, Object> chatWithHistory(@RequestBody Map<String, Object> request) {
        // 提取参数
        String message = (String) request.get("message");
        @SuppressWarnings("unchecked")
        List<String> historyText = (List<String>) request.getOrDefault("history", List.of());

        // 将历史消息转换为 Message 对象
        // 实际项目中建议使用专业的 Message 历史管理
        List<Message> history = new ArrayList<>();
        // 将历史文本转换为 AI 消息格式
        // 简化处理：交替作为用户消息和 AI 消息
        for (int i = 0; i < historyText.size(); i++) {
            if (i % 2 == 0) {
                // 偶数索引：用户消息
                history.add(new org.springframework.ai.chat.messages.UserMessage(historyText.get(i)));
            } else {
                // 奇数索引：AI 回复
                history.add(new org.springframework.ai.chat.messages.AssistantMessage(historyText.get(i)));
            }
        }

        // 调用带历史记录的服务方法
        String answer = aiChatService.chatWithHistory(message, history);

        return Map.of(
            "code", 200,
            "data", Map.of(
                "answer", answer,
                "historySize", history.size() + 1 // +1 包含当前消息
            ),
            "message", "success"
        );
    }
}
```

### 3.7 单元测试 —— AiChatServiceTest.java

```java
package com.example.ai;

import com.example.ai.service.AiChatService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AI 对话服务 —— 单元测试
 *
 * 测试三个核心功能：
 * 1. 最简问答能否正常返回
 * 2. 带系统提示词能否设定角色
 * 3. 返回内容是否非空
 *
 * 注意：运行测试需要配置有效的 DASHSCOPE_API_KEY
 */
@SpringBootTest // Spring Boot 测试注解，自动加载应用上下文
class AiChatServiceTest {

    // 自动注入 ChatService Bean
    @Autowired
    private AiChatService aiChatService;

    /**
     * 测试最简问答
     *
     * 验证：发送"你好"是否能得到非空回复
     * 预期：返回的字符串不为 null，且长度大于 0
     */
    @Test
    void testSimpleChat() {
        // 准备测试数据
        String message = "你好，请用一句话介绍你自己";

        // 执行测试
        String answer = aiChatService.simpleChat(message);

        // 验证结果
        // 1. 回复不为空
        assertNotNull(answer, "AI 回复不能为空");
        // 2. 回复有内容
        assertTrue(answer.length() > 0, "AI 回复长度必须大于 0");
        // 3. 打印回复内容（方便观察）
        System.out.println("= 最简问答测试结果 =");
        System.out.println("用户: " + message);
        System.out.println("AI: " + answer);
        System.out.println("===================");
    }

    /**
     * 测试带系统提示词的对话
     *
     * 验证：设定 AI 为"翻译助手"后，AI 是否按翻译角色回复
     * 预期：AI 应该以翻译助手的身份进行翻译
     */
    @Test
    void testChatWithSystem() {
        // 准备测试数据
        String systemPrompt = "你是一位中英文翻译助手。请将用户输入的中文翻译成英文，只输出翻译结果，不要多余的解释。";
        String message = "今天天气真好";

        // 执行测试
        String answer = aiChatService.chatWithSystem(message, systemPrompt);

        // 验证结果
        assertNotNull(answer, "AI 回复不能为空");
        assertTrue(answer.length() > 0, "AI 回复长度必须大于 0");

        // 打印结果
        System.out.println("= 带系统提示词测试结果 =");
        System.out.println("系统提示词: " + systemPrompt);
        System.out.println("用户: " + message);
        System.out.println("AI: " + answer);
        System.out.println("========================");
    }

    /**
     * 测试带历史记录的对话
     *
     * 验证：在多轮对话中，AI 能否记住之前的上下文
     * 预期：第二次提问时，AI 应该基于第一次的回答来回应
     */
    @Test
    void testChatWithHistory() {
        // 准备测试数据
        // 第一次对话：介绍自己
        String firstMessage = "你好，请介绍一下你自己";
        String firstAnswer = aiChatService.simpleChat(firstMessage);
        assertNotNull(firstAnswer, "第一次回复不能为空");

        // 构建历史记录：包含第一次对话
        List<org.springframework.ai.chat.messages.Message> history = new ArrayList<>();
        history.add(new org.springframework.ai.chat.messages.UserMessage(firstMessage));
        history.add(new org.springframework.ai.chat.messages.AssistantMessage(firstAnswer));

        // 第二次对话：追问，基于历史
        String secondMessage = "我刚才问了你什么问题？";

        // 执行测试
        String secondAnswer = aiChatService.chatWithHistory(secondMessage, history);

        // 验证结果
        assertNotNull(secondAnswer, "第二次回复不能为空");
        assertTrue(secondAnswer.length() > 0, "第二次回复长度必须大于 0");

        // 打印结果
        System.out.println("= 带历史记录对话测试结果 =");
        System.out.println("第一次对话:");
        System.out.println("  用户: " + firstMessage);
        System.out.println("  AI: " + firstAnswer);
        System.out.println("第二次对话:");
        System.out.println("  用户: " + secondMessage);
        System.out.println("  AI: " + secondAnswer);
        System.out.println("============================");
    }
}
```

---

## 四、运行验证

### 4.1 准备工作

在运行项目之前，需要准备以下环境：

1. **JDK 17+**：Spring Boot 3.2 要求 JDK 17 以上
2. **Maven 3.8+**：项目管理工具
3. **DashScope API Key**：从阿里云 DashScope 控制台获取

### 4.2 获取 API Key

1. 访问 [阿里云 DashScope 控制台](https://dashscope.aliyun.com/)
2. 登录阿里云账号（如果没有，需要先注册）
3. 在"API Key 管理"页面创建 API Key
4. 开通通义千问模型服务（qwen-max 等模型）

### 4.3 配置 API Key

**方式一：环境变量（推荐）**

```bash
# Windows 命令提示符
set SPRING_AI_DASHSCOPE_API_KEY=sk-xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx

# Windows PowerShell
$env:SPRING_AI_DASHSCOPE_API_KEY="sk-xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"

# Mac / Linux
export SPRING_AI_DASHSCOPE_API_KEY=sk-xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
```

**方式二：直接修改 application.yml**

```yaml
spring:
  ai:
    dashscope:
      api-key: sk-xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
```

> **注意：** 方式二仅用于本地开发测试，不要将 API Key 提交到 Git 仓库。

### 4.4 启动项目

```bash
# 克隆项目（如果是自己的项目）
# cd spring-ai-alibaba-demo

# 编译并启动
mvn spring-boot:run
```

启动成功后，控制台输出类似：

```
========================================
Spring AI Alibaba Demo 启动成功！
访问 http://localhost:8080/chat?message=你好 体验 AI 对话
========================================
```

### 4.5 测试 API

**测试一：最简问答**

```bash
# 使用 curl 测试
curl "http://localhost:8080/chat?message=你好，请用中文回复"

# 浏览器直接访问
# http://localhost:8080/chat?message=你好，请用中文回复
```

**预期响应：**

```json
{
  "code": 200,
  "data": "你好！我是通义千问，一个由阿里云开发的AI助手。有什么可以帮助你的吗？",
  "message": "success"
}
```

**测试二：带系统提示词**

```bash
curl "http://localhost:8080/chat/system?message=什么是Spring Boot&system=你是一位Java技术专家，请用通俗易懂的语言解释"
```

**预期响应：** AI 将以 Java 技术专家的身份解释 Spring Boot。

**测试三：带历史记录**

```bash
curl -X POST "http://localhost:8080/chat/history" \
  -H "Content-Type: application/json" \
  -d '{"message":"刚才我们聊了什么？","history":["你好，我叫小明","你好小明！我是AI助手，很高兴认识你！"]}'
```

### 4.6 运行测试

```bash
# 运行所有测试
mvn test

# 运行指定测试类
mvn test -Dtest=AiChatServiceTest
```

---

## 五、项目对照

### 5.1 ai-passage-creator 中的实际应用

在 ai-passage-creator 项目中，Spring AI Alibaba 的使用方式比本示例更复杂：

| 本示例 | ai-passage-creator 项目 |
|--------|------------------------|
| 单个 ChatClient | 多个 Agent 各自持有 ChatClient |
| 同步调用 | 同步 + 流式异步调用 |
| 无状态 | 通过 StateGraph 管理状态 |
| 简单问答 | 复杂 Prompt 模板 + Tool Calling |
| 无记忆 | 通过 Memory 接口管理对话历史 |

### 5.2 项目中的 Agent 定义方式

```java
// ai-passage-creator 项目中的 Agent 定义（简化版）
// 每个 Agent 封装了特定的 Prompt 和调用逻辑

// 标题生成 Agent
@Service
public class TitleGeneratorAgent {
    private final ChatClient chatClient;

    public TitleGeneratorAgent(ChatClient.Builder builder) {
        // 构建 ChatClient 时设定默认系统提示词
        this.chatClient = builder
            .defaultSystem("你是一个专业的文章标题生成专家。" +
                          "你的任务是根据用户提供的主题，生成3-5个吸引人的标题。" +
                          "标题要简洁有力，每个标题不超过20个字。")
            .build();
    }

    public String generate(String topic) {
        return chatClient.call(topic);
    }
}

// 大纲生成 Agent
@Service
public class OutlineGeneratorAgent {
    private final ChatClient chatClient;

    public OutlineGeneratorAgent(ChatClient.Builder builder) {
        this.chatClient = builder
            .defaultSystem("你是一个文章大纲规划专家。" +
                          "请根据用户提供的标题，生成结构化的文章大纲。" +
                          "大纲要包含引言、正文要点（至少3个）、结论。" +
                          "每个要点要给出核心观点。")
            .build();
    }

    // 流式生成 —— 通过 Flux 返回，前端可以实时接收
    public Flux<String> generateStream(String title) {
        return chatClient.call(title).stream();
    }
}
```

### 5.3 从入门到项目实战的差距

要从本示例过渡到 ai-passage-creator 的实际开发，需要掌握：

1. **StateGraph 工作流**：将多个 Agent 编排成流水线
2. **流式输出**：使用 Flux 实现 SSE 推送
3. **Tool Calling**：让 AI 调用外部工具（如数据库查询）
4. **Memory 管理**：持久化对话历史
5. **异常处理**：API 调用失败的重试和降级
6. **成本控制**：Token 计数和限流

---

## 六、面试题

### 面试题 1：ChatClient 的 call() 和 stream() 方法有什么区别？各自适用于什么场景？

**参考答案：**

`call()` 是同步阻塞方法，它会等待 AI 完整回复后才返回。适用于：
- 对响应时间不敏感的后台任务
- 需要完整结果才能进行下一步处理的场景
- 简单的一次性问答

`stream()` 是异步流式方法，返回 `Flux<ChatResponse>`，AI 逐 Token 生成时就会推送。适用于：
- 需要实时显示回复内容的场景（如打字机效果）
- 长时间生成任务（如文章生成），让用户边看边等
- 提升用户体验，减少等待焦虑

在 ai-passage-creator 中，大纲生成和正文生成使用 `stream()` 实现 SSE 推送，让前端实时显示生成进度。

### 面试题 2：SystemMessage 和 UserMessage 有什么区别？为什么 SystemMessage 要放在前面？

**参考答案：**

`SystemMessage`（系统消息）设定 AI 的"人设"和行为规则，role 为 system。它告诉 AI 应该以什么身份、什么风格、什么格式来回答。例如："你是一位 Java 技术专家，请用通俗易懂的语言解释技术概念"。

`UserMessage`（用户消息）是用户的具体提问，role 为 user。它告诉 AI 当前需要完成什么任务。

SystemMessage 放在前面的原因：
1. **优先级规则**：大模型在生成回复时，系统消息具有最高优先级，放在前面可以让模型优先处理身份设定
2. **上下文窗口**：大模型的上下文窗口有限（通常 4K-128K tokens），系统消息放在前面可以确保它始终在上下文窗口中
3. **Attention 机制**：Transformer 的注意力机制对序列开头的内容有更好的记忆，系统消息放在开头有助于模型始终记住自己的角色

### 面试题 3：Spring AI Alibaba 的自动配置原理是什么？ChatClient 是怎么被创建出来的？

**参考答案：**

Spring AI Alibaba 的自动配置基于 Spring Boot 的 `@EnableAutoConfiguration` 机制：

1. **自动配置类**：`DashScopeAutoConfiguration` 类上标注了 `@AutoConfiguration`，Spring Boot 启动时会自动加载
2. **条件注解**：配置类上使用 `@ConditionalOnClass(ChatClient.class)` 和 `@ConditionalOnProperty(prefix = "spring.ai.dashscope", name = "api-key")` 等条件，只有当 classpath 中存在 ChatClient 且配置了 api-key 时才会生效
3. **创建 Bean**：配置类中创建 `ChatClient.Builder` Bean，这个 Builder 内部持有 DashScope 的 API Key、模型名称等配置
4. **自动注入**：开发者在 Service 中通过 `@Autowired` 或构造器注入 `ChatClient.Builder`，然后调用 `build()` 创建 ChatClient 实例

关键源码路径（简化）：
```
DashScopeAutoConfiguration
  → 读取 application.yml 中的 spring.ai.dashscope.* 配置
  → 创建 DashScopeChatClientBuilder Bean
  → 开发者通过 builder.build() 获得 ChatClient
```

---

## 七、总结

本文从零开始搭建了一个 Spring AI Alibaba 入门项目，实现了三个核心功能：最简问答、带系统提示词的对话、带历史记录的多轮对话。

**关键要点回顾：**

1. Spring AI Alibaba 是 Java 生态中接入阿里云通义千问的最佳选择
2. ChatClient 是核心 API，提供 call() 和 stream() 两种调用方式
3. SystemMessage 设定 AI 角色，UserMessage 提出具体需求
4. 通过构造器注入 ChatClient.Builder 是最佳实践
5. API Key 通过环境变量注入，避免硬编码到配置文件中

**下一步学习：**

- 学习 StateGraph 多 Agent 编排（下一篇）
- 学习流式输出和 SSE 推送
- 学习 Tool Calling 让 AI 调用外部工具
- 学习 Memory 管理对话历史