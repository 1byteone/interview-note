# 🤖 yu-ai-code-mother 深度剖析 — 鱼皮最具技术深度的项目

> Spring Boot 3 + LangChain4j + LangGraph4j + Spring Cloud 微服务 AI 应用生成平台

---

## 一、项目概览

| 维度 | 信息 |
|------|------|
| **仓库** | [github.com/liyupi/yu-ai-code-mother](https://github.com/liyupi/yu-ai-code-mother) |
| **Stars** | 1,892 ⭐ / 409 Fork |
| **语言** | Java |
| **框架** | Spring Boot 3 + Spring Cloud |
| **AI 框架** | LangChain4j + LangGraph4j |
| **前端** | Vue 3 |
| **定位** | 大厂级 AI 应用生成平台 — 一站式生成 AI 应用代码 |
| **最近更新** | 2026-08-21 (持续维护) |

---

## 二、技术架构

```
┌─────────────────────────────────────────────────────────────────┐
│                        前端层 (Vue 3)                           │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────────┐   │
│  │ 可视化编辑 │  │ 实时预览  │  │ AI 路由   │  │ 一键部署面板  │   │
│  └──────────┘  └──────────┘  └──────────┘  └──────────────┘   │
└──────────────────────────┬──────────────────────────────────────┘
                           │ HTTP / SSE
┌──────────────────────────▼──────────────────────────────────────┐
│                     API Gateway (Spring Cloud Gateway)          │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────────┐   │
│  │ 路由转发   │  │ 负载均衡  │  │ 认证鉴权  │  │ 限流熔断     │   │
│  └──────────┘  └──────────┘  └──────────┘  └──────────────┘   │
└──────────────────────────┬──────────────────────────────────────┘
                           │
┌──────────────────────────▼──────────────────────────────────────┐
│                    微服务层 (Spring Cloud Alibaba)               │
│                                                                 │
│  ┌─────────────────┐  ┌─────────────────┐  ┌────────────────┐ │
│  │ Nacos (注册/配置) │  │ Sentinel (熔断)  │  │ Seata (事务)    │ │
│  └─────────────────┘  └─────────────────┘  └────────────────┘ │
│                                                                 │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │              AI 核心服务 (LangChain4j)                    │  │
│  │  ┌──────────┐  ┌──────────────┐  ┌──────────────────┐  │  │
│  │  │ Chat模型  │  │ Tool Calling │  │ SSE 流式输出      │  │  │
│  │  └──────────┘  └──────────────┘  └──────────────────┘  │  │
│  │  ┌──────────────────┐  ┌──────────────────────────┐    │  │
│  │  │ LangGraph4j 工作流 │  │ RAG (检索增强生成)       │    │  │
│  │  └──────────────────┘  └──────────────────────────┘    │  │
│  └──────────────────────────────────────────────────────────┘  │
│                                                                 │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │              业务微服务                                    │  │
│  │  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌────────┐  │  │
│  │  │ 代码生成  │  │ 可视化编辑 │  │ 部署管理  │  │ 用户中心 │  │  │
│  │  └──────────┘  └──────────┘  └──────────┘  └────────┘  │  │
│  └──────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
                           │
┌──────────────────────────▼──────────────────────────────────────┐
│                    基础设施层                                    │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────────┐   │
│  │  MySQL    │  │  Redis   │  │  MQ      │  │  监控体系     │   │
│  └──────────┘  └──────────┘  └──────────┘  └──────────────┘   │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │  ARMS / Prometheus / Grafana 全链路监控                    │  │
│  └──────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
```

---

## 三、核心技术点拆解

### 3.1 LangChain4j 集成

```java
// AI 服务接口 — LangChain4j 声明式 AI Service
@AiService
public interface AiCodeService {

    @SystemMessage("你是一个专业的AI应用代码生成助手，根据用户需求生成完整代码")
    String generateCode(@UserMessage String requirement);

    // Streaming 支持
    @SystemMessage("你是一个AI应用架构师")
    TokenStream generateCodeStream(@UserMessage String requirement);
}
```

### 3.2 LangGraph4j 工作流

```java
// AI 工作流编排 — 多步骤代码生成流程
StateGraph<AgentState> workflow = new StateGraph<>(AgentState::new)
    .addNode("analyze", this::analyzeRequirement)    // 需求分析
    .addNode("design", this::designArchitecture)     // 架构设计
    .addNode("generate", this::generateCode)         // 代码生成
    .addNode("review", this::reviewCode)             // 代码审查
    .addEdge(START, "analyze")
    .addEdge("analyze", "design")
    .addEdge("design", "generate")
    .addConditionalEdge("generate", this::needsReview,
        Map.of(true, "review", false, END))
    .addEdge("review", END);
```

### 3.3 Tool Calling

```java
// AI 工具注册 — 让 LLM 能调用外部能力
@Component
public class CodeTools {

    @Tool("查询数据库表结构")
    public String getTableSchema(@P("表名") String tableName) {
        return schemaService.getSchema(tableName);
    }

    @Tool("执行 SQL 查询")
    public String executeQuery(@P("SQL语句") String sql) {
        return queryService.execute(sql);
    }

    @Tool("部署应用到服务器")
    public String deployApp(@P("应用名") String appName) {
        return deployService.deploy(appName);
    }
}
```

### 3.4 SSE 流式输出

```java
// Server-Sent Events — 实时推送生成进度
@GetMapping(value = "/generate", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public SseEmitter generate(@RequestBody CodeRequest request) {
    SseEmitter emitter = new SseEmitter(60_000L);

    TokenStream tokenStream = aiCodeService.generateCodeStream(request.getRequirement());
    tokenStream
        .onPartialResponse(token -> emitter.send(
            SseEmitter.event().data(token, MediaType.APPLICATION_JSON)))
        .onCompleteResponse(response -> emitter.complete())
        .onError(e -> emitter.completeWithError(e))
        .start();

    return emitter;
}
```

### 3.5 Spring Cloud 微服务治理

```yaml
# Nacos 服务注册
spring:
  cloud:
    nacos:
      discovery:
        server-addr: nacos:8848
        namespace: dev
      config:
        server-addr: nacos:8848
        file-extension: yml

    # Sentinel 熔断降级
    sentinel:
      transport:
        dashboard: sentinel:8080

    # Gateway 路由
    gateway:
      routes:
        - id: ai-service
          uri: lb://yu-ai-code-mother-ai
          predicates:
            - Path=/api/ai/**
        - id: code-service
          uri: lb://yu-ai-code-mother-code
          predicates:
            - Path=/api/code/**
```

---

## 四、微服务拆分

| 服务名 | 职责 | 技术 |
|--------|------|------|
| `gateway-service` | API 网关、路由、鉴权 | Spring Cloud Gateway |
| `user-service` | 用户注册、登录、权限 | Spring Security + JWT |
| `ai-service` | AI 模型调用、Prompt 管理 | LangChain4j + SSE |
| `code-service` | 代码生成、模板管理 | LangGraph4j + FreeMarker |
| `preview-service` | 代码实时预览 | WebSocket + Docker |
| `deploy-service` | 一键部署、环境管理 | Docker + K8s |
| `monitor-service` | 监控告警 | ARMS + Prometheus + Grafana |

---

## 五、学习价值评估

| 维度 | 评分 | 说明 |
|------|------|------|
| **架构设计** | ⭐⭐⭐⭐⭐ | 完整的微服务拆分 + AI 框架集成 |
| **AI 落地** | ⭐⭐⭐⭐⭐ | LangChain4j/LangGraph4j 真实生产级用法 |
| **代码质量** | ⭐⭐⭐⭐ | 分层清晰，DTO/VO/Entity 规范 |
| **面试亮点** | ⭐⭐⭐⭐⭐ | "我做了一个 AI + 微服务的全栈平台" |
| **可复用性** | ⭐⭐⭐⭐ | 架构模板可直接套用于其他 AI 项目 |

---

*此分析可作为面试中"介绍你做过的最有深度的项目"的模板参考*
