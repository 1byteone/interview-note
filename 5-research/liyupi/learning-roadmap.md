# 📚 鱼皮项目学习路线图

> Java 后端工程师从基础 → AI 转型 → 架构进阶 → 面试冲刺的完整路径
> 基于鱼皮 10 个核心项目的串联学习

---

## 知识图谱：项目 × 技术栈映射

```
┌─────────────────────────────────────────────────────────────────┐
│  🧱 基础夯实层 (第1-4周)                                        │
│  ├─ springboot-guide      → 自动装配 / Starter / @Conditional  │
│  ├─ Design-Model          → 策略 / 模板 / 观察者 / 工厂 / SPI   │
│  ├─ java-concurrent       → JUC / AQS / 线程池 / 锁            │
│  └─ sql-mother/sql-father → MySQL 索引 / B+树 / SQL 执行引擎   │
├─────────────────────────────────────────────────────────────────┤
│  🤖 AI 转型层 (第5-8周)                                         │
│  ├─ ai-guide (19K⭐)      → Prompt / RAG / Embedding / MCP    │
│  ├─ yu-ai-agent (2.6K⭐)  → Spring AI / ReAct / Tool Calling  │
│  └─ yu-ai-code-mother     → LangChain4j / LangGraph4j / SSE   │
├─────────────────────────────────────────────────────────────────┤
│  🏗️ 架构进阶层 (第9-14周)                                       │
│  ├─ yu-rpc (603⭐)        → 协议设计 / SPI / 序列化 / 注册中心 │
│  ├─ mianshiya-next (511⭐)→ Sa-Token / Sentinel / ES / HotKey  │
│  └─ yu-ai-code-mother微服务 → Gateway/Nacos/Sentinel/Seata    │
├─────────────────────────────────────────────────────────────────┤
│  🎯 面试冲刺层 (第15-16周)                                       │
│  ├─ mianshiya (5.7K⭐)    → 10,000+ 题库刷题                   │
│  └─ interview-value-guide → STAR 话术 / 简历包装 / 系统设计     │
└─────────────────────────────────────────────────────────────────┘
```

---

## Phase 1：基础夯实（第 1-4 周）

**目标**：Java 后端核心能力就位

| 周 | 项目 | 学什么 | 对应文档 |
|----|------|--------|---------|
| W1 | springboot-guide + Design-Model | 自动装配原理、6 种设计模式 | README §4.5 |
| W2 | java-concurrent | 线程池参数、AQS、ReentrantLock | README §4.5 |
| W3 | sql-mother | Wasm SQL 引擎原理、判题算法 | frontend-projects |
| W4 | yu-rpc（简化版 yu-rpc-easy） | HTTP + JDK 序列化 + 本地注册 | yu-rpc-deep-dive |

**里程碑**：
- ✅ 能解释 Spring Boot 自动装配的完整流程
- ✅ 能说出线程池核心参数和拒绝策略
- ✅ 能通过 Explain 分析慢 SQL
- ✅ 能画出策略模式/模板方法的类图

---

## Phase 2：AI 转型（第 5-8 周）

**目标**：从纯 Java 转 Java + AI 工程师

| 周 | 项目 | 学什么 | 对应文档 |
|----|------|--------|---------|
| W5 | ai-guide | AI 知识体系：Prompt / RAG / Tool Calling | ai-guide-analysis |
| W6 | yu-ai-agent | Spring AI ChatClient、ReAct 循环、4层继承 | yu-ai-agent-deep-dive |
| W7 | yu-ai-code-mother | LangChain4j AiService、Tool 注册、SSE | yu-ai-code-mother-analysis |
| W8 | ai-guide 项目实战部分 | 跟教程做 1-2 个小项目（塔罗牌/翻译器） | ai-guide-analysis §2.1 |

**里程碑**：
- ✅ 能写一个基于 Spring AI 的 Chatbot + Tool Calling
- ✅ 能说出 ReAct 的 Think-Act 循环原理
- ✅ 能用 LangChain4j AiService 声明式调 LLM
- ✅ 能解释 MCP 协议的核心价值

---

## Phase 3：架构进阶（第 9-14 周）

**目标**：微服务 + AI 架构完整能力

| 周 | 项目 | 学什么 | 对应文档 |
|----|------|--------|---------|
| W9-10 | yu-rpc 完整版 | 17字节协议、SPI、Etcd/ZK、一致性哈希 | yu-rpc-deep-dive |
| W11 | yu-ai-code-mother 微服务版 | Gateway/Nacos/Sentinel/Seata/Dubbo | yu-ai-code-mother-analysis |
| W12 | mianshiya-next | Sa-Token认证、Sentinel限流、ES搜索、HotKey | mianshiya-next-deep-dive |
| W13 | yu-picture DDD 版 | DDD 分层、动态分表、WebSocket协同 | frontend-projects |
| W14 | 综合串联 | 画完整架构图、准备追问 | 所有文档 |

**里程碑**：
- ✅ 能画出 yu-ai-code-mother 的完整微服务架构图
- ✅ 能从零描述 RPC 框架的完整调用链
- ✅ 能说出 Nacos AP/CP、Sentinel 滑动窗口原理
- ✅ 能解释 Seata AT 模式的两阶段提交

---

## Phase 4：面试冲刺（第 15-16 周）

**目标**：将项目知识转化为 Offer

| 周 | 动作 | 对应文档 |
|----|------|---------|
| W15 | 打磨 3 个项目 STAR 话术 + 简历包装 | interview-value-guide |
| W16 | 模拟面试 + 系统设计题 + 高频追问 | ai-interview-bank + yu-rpc-interview |

**三大面试项目包装**：

### 项目一：AI 智能代码生成平台
> "基于 Spring Cloud Alibaba + LangChain4j + LangGraph4j，支持 AI 代码生成、可视化编辑、一键部署。AI 应用开发周期从 2 周缩短至 1 天。"

### 项目二：自研 RPC 框架
> "基于 Netty 自定义 17 字节协议，SPI 可插拔序列化（JDK/JSON/Kryo/Hessian），Etcd/ZK 注册中心，三种负载均衡策略，QPS 10,000+。"

### 项目三：企业级面试刷题平台
> "Spring Boot 2.7 + MyBatis-Plus + Redis + Elasticsearch，Sa-Token 认证、Sentinel 参数限流、HotKey 三级缓存、Redis Lua 反爬虫。"

---

## 时间线总览

```
第 1-4 周   🧱 基础夯实
            springboot-guide → Design-Model → java-concurrent → sql-mother

第 5-8 周   🤖 AI 转型
            ai-guide → yu-ai-agent → yu-ai-code-mother

第 9-14 周  🏗️ 架构进阶
            yu-rpc → mianshiya-next → yu-ai-code-mother微服务 → yu-picture

第 15-16 周 🎯 面试冲刺
            STAR 话术 → 简历包装 → 系统设计 → 模拟面试
```

---

## 配套文档索引

| 文档 | 用途 | Phase |
|------|------|-------|
| README.md | 全景了解鱼皮 | 开始前读 |
| ai-guide-analysis.md | AI 知识体系 | Phase 2 |
| yu-ai-agent-deep-dive.md | Agent 架构 | Phase 2 |
| yu-ai-code-mother-analysis.md | AI + 微服务 | Phase 2+3 |
| yu-rpc-deep-dive.md | RPC 底层 | Phase 3 |
| mianshiya-next-deep-dive.md | 企业级实战 | Phase 3 |
| frontend-projects-analysis.md | 前端全栈 | Phase 1+3 |
| ai-projects-interview-bank.md | AI 面试题 20 题 | Phase 4 |
| yu-rpc-interview-questions.md | RPC 面试题 15 题 | Phase 4 |
| interview-value-guide.md | STAR 话术 | Phase 4 |
| tech-trends-analysis.md | 技术趋势 | Phase 2 |

---

> **鱼皮项目最核心的价值**：不是照搬的代码库，而是一条从 **Java 基础 → 微服务架构 → AI 转型** 的完整学习路径。每个项目对应一个面试知识点集群，吃透它们，就拥有了从 P5 到 P7 的知识储备。
