# 🔥 2025-2026 AI 技术趋势 — 鱼皮项目中的技术选型分析

> 基于鱼皮近两年项目的 Stack 选型，提炼当前 AI 应用开发的技术趋势

---

## 一、技术栈演进路线

```
2022: Spring Boot 2 + MyBatis + Vue 2
        ↓
2023: Spring Boot 3 + ChatGPT API + Vue 3
        ↓
2024: Spring Boot 3 + Spring AI / LangChain4j + Vue 3
        ↓
2025: Spring Boot 3 + LangChain4j + LangGraph4j + MCP + Agent Skills
        ↓
2026: Spring Boot 3 + LangGraph4j + A2A + Vibe Coding + 多模态
```

---

## 二、当前 AI 应用开发主流技术栈

### 2.1 Java AI 框架对比

| 框架 | 鱼皮采用 | 特点 | 适用场景 |
|------|---------|------|---------|
| **LangChain4j** | ✅ yu-ai-code-mother | Java 原生、AiService 声明式、Tool Calling、RAG | 复杂 AI 应用、多步骤工作流 |
| **Spring AI** | ✅ yu-ai-agent | Spring 官方、ChatClient、Embedding、Function Calling | Spring 生态深度集成 |
| **Semantic Kernel** | ❌ | 微软出品、C#/Java | .NET 为主的团队 |
| **LlamaIndex** | ❌ | Python 原生、RAG 专精 | 纯 Python 数据密集场景 |

**鱼皮的选择逻辑**:
- **LangChain4j**: 用于需要复杂工作流编排的场景（代码生成平台）
- **Spring AI**: 用于需要快速集成 Spring 生态的场景（Agent 应用）

### 2.2 AI Agent 架构模式

| 模式 | 鱼皮项目 | 原理 | 优势 |
|------|---------|------|------|
| **ReAct** | yu-ai-agent | 思考→行动→观察 循环 | 简单可控，适合明确任务 |
| **Plan-and-Execute** | yu-ai-code-mother | 先规划再执行 | 适合复杂多步骤任务 |
| **Multi-Agent** | ai-guide 提及 | 多个 Agent 协作 | 适合需要分工的大型任务 |
| **Tool Use** | 所有 AI 项目 | LLM 调用外部工具 | 扩展 AI 能力边界 |

### 2.3 MCP (Model Context Protocol)

鱼皮在 2026 年的 ai-guide 中重点介绍:

```
MCP = AI 应用的 USB-C 接口
┌──────────┐     MCP      ┌──────────┐
│  AI 模型  │ ◄──────────► │ 外部工具  │
│ (Client)  │   标准协议    │ (Server)  │
└──────────┘              └──────────┘
```

**核心价值**: 标准化 AI 模型与外部工具的通信协议，一次实现，处处可用。

---

## 三、前端技术趋势（从鱼皮项目观察）

| 年份 | 主流选择 | 鱼皮项目 |
|------|---------|---------|
| 2021 | Vue 2 / React | code-nav (Vue) |
| 2022 | Vue 3 / Ant Design | sql-generator (Vue 3 + Ant Design) |
| 2023 | Vue 3 + TypeScript | yuindex, sql-mother (Vue 3 + TS) |
| 2024 | Vue 3 + Ant Design Vue | yu-picture (Vue 3 + Ant Design) |
| 2025-2026 | Vue 3 / React + AI 组件 | yu-ai-code-mother (Vue 3), ai-code-helper (Vue) |

---

## 四、后端基础设施趋势

### 4.1 微服务技术栈

| 组件 | 鱼皮选择 | 替代方案 | 趋势 |
|------|---------|---------|------|
| 服务注册/配置 | **Nacos** | Eureka, Consul, Apollo | Nacos 一统配置+注册 |
| API 网关 | **Spring Cloud Gateway** | Zuul | Gateway 成为唯一选择 |
| 熔断降级 | **Sentinel** | Hystrix, Resilience4j | Sentinel 生态更完整 |
| 分布式事务 | **Seata** | — | Seata 成为标配 |
| 监控 | **ARMS + Prometheus + Grafana** | SkyWalking, Zipkin | Prometheus 体系成主流 |

### 4.2 数据层

| 组件 | 使用场景 | 趋势 |
|------|---------|------|
| **MySQL** | 核心业务数据 | 依然是首选关系型数据库 |
| **Redis** | 缓存/会话/分布式锁 | 7.0+ 新特性（Function、Sharded Pub/Sub） |
| **Elasticsearch** | 全文搜索/日志 | 被 OpenSearch 分流 |
| **Vector DB** | AI RAG 向量检索 | Milvus/PgVector/Weaviate 爆发 |

---

## 五、2026 年 AI 应用开发必备技能

基于鱼皮 ai-guide 19K⭐ 项目的内容体系:

### 5.1 基础层
- [ ] Prompt Engineering（提示词工程）
- [ ] RAG（检索增强生成）
- [ ] Tool Calling / Function Calling
- [ ] Embedding 向量模型

### 5.2 进阶层
- [ ] Agent 开发（ReAct / Plan-and-Execute）
- [ ] LangChain4j / Spring AI 框架
- [ ] LangGraph4j 工作流编排
- [ ] MCP 协议

### 5.3 高阶层
- [ ] Multi-Agent 系统
- [ ] A2A (Agent-to-Agent) 协议
- [ ] Vibe Coding（氛围编程）
- [ ] AI 原生应用架构设计

---

*此分析可作为技术选型参考和学习路线规划依据*
