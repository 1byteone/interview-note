# 4个GitHub项目技术栈深度剖析 · Phase 1 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 对 4 个 GitHub 项目（ruoyi-ai、ai-passage-creator、mewpaw-code、zznursing）进行技术栈深度拆解，产出面试导向的教程文档，每个技术栈包含分析文档、面试题、STAR亮点和可运行代码示例。

**Architecture:** 按项目维度独立文件夹组织，每个项目内按技术栈拆解为独立 Markdown 文档。先用 anysearch 搜索项目和技术栈资料，再基于资料产出文档。每个技术栈文档聚焦面试高频考点，绑定项目代码，拒绝纯理论。

**Tech Stack:** Markdown; Graphviz/Vega 图解; 每个项目对应的 Java/Spring Boot/Python 等技术栈

**Spec:** `docs/superpowers/specs/2026-08-22-github-projects-tech-stack-design.md`

## Global Constraints

- 所有文档使用统一模板（见设计文档 3.1 节）
- 每个技术栈文档 ≥ 1 个核心代码片段（带逐行注释）
- 每个技术栈文档 ≥ 2 道面试题（含回答框架）
- 每个项目 ≥ 3 个STAR亮点
- 所有代码示例可运行或完整可编译
- 先搜索资料再写文档，每个项目启动前用 anysearch 搜索
- 采用面试导向方式（Phase 1），聚焦高频考点
- 严格区分"项目中已实现"和"设计/规划"的能力

---

## 文件结构总览

```
docs/tech-stack-analysis/
├── README.md                           # 总索引
├── ruoyi-ai/                           # 项目1
│   ├── 00-PROJECT-OVERVIEW.md
│   ├── 01-spring-boot-langchain4j.md
│   ├── 02-multi-llm-factory.md
│   ├── 03-rag-pipeline.md
│   ├── 04-vector-store-strategy.md
│   ├── 05-langgraph-flow-engine.md
│   ├── 06-supervisor-agent.md
│   ├── 07-mcp-protocol.md
│   ├── 08-mybatis-plus-mysql.md
│   ├── 09-redis-redisson.md
│   ├── 10-sa-token-auth.md
│   ├── 11-bpm-workflow.md
│   ├── 12-sse-websocket.md
│   ├── 13-monitor-deploy.md
│   ├── 14-interview-questions.md
│   ├── 15-star-highlights.md
│   └── code-examples/                  # 子目录
├── ai-passage-creator/                 # 项目2
│   ├── 00-PROJECT-OVERVIEW.md
│   ├── 01-spring-ai-alibaba.md
│   ├── 02-multi-agent-orchestration.md
│   ├── 03-image-strategy-pattern.md
│   ├── 04-sse-streaming.md
│   ├── 05-human-in-loop.md
│   ├── 06-mybatis-flex-mysql.md
│   ├── 07-stripe-payment.md
│   ├── 08-redis-redisson.md
│   ├── 09-vue3-antd.md
│   ├── 10-interview-questions.md
│   ├── 11-star-highlights.md
│   └── code-examples/
├── mewpaw-code/                        # 项目3
│   ├── 00-PROJECT-OVERVIEW.md
│   ├── 01-java21-springboot.md
│   ├── 02-react-agent-loop.md
│   ├── 03-langchain4j-tools.md
│   ├── 04-security-sandbox.md
│   ├── 05-tui-repl.md
│   ├── 06-interview-questions.md
│   ├── 07-star-highlights.md
│   └── code-examples/
├── zznursing/                          # 项目4
│   ├── 00-PROJECT-OVERVIEW.md
│   ├── 01-spring-boot-iot.md
│   ├── 02-baidu-qianfan-ai.md
│   ├── 03-huawei-iotda.md
│   ├── 04-wechat-miniapp.md
│   ├── 05-vue3-admin.md
│   ├── 06-mysql-redis.md
│   ├── 07-iot-device-protocol.md
│   ├── 08-interview-questions.md
│   ├── 09-star-highlights.md
│   └── code-examples/
└── cross-cutting/                      # 跨项目综合
    ├── java-ai-ecosystem-comparison.md
    ├── enterprise-architecture-patterns.md
    └── overall-star-highlights.md
```

---

### Task 1: 初始化目录结构与总索引

**Files:**
- Create: `docs/tech-stack-analysis/README.md`
- Create: `docs/tech-stack-analysis/ruoyi-ai/` (目录)
- Create: `docs/tech-stack-analysis/ai-passage-creator/` (目录)
- Create: `docs/tech-stack-analysis/mewpaw-code/` (目录)
- Create: `docs/tech-stack-analysis/zznursing/` (目录)
- Create: `docs/tech-stack-analysis/cross-cutting/` (目录)

**Interfaces:**
- Consumes: 设计文档中的目录结构
- Produces: 项目总索引，后续所有文档的导航入口

- [ ] **Step 1: 创建所有项目目录**

```bash
mkdir -p docs/tech-stack-analysis/{ruoyi-ai,ai-passage-creator,mewpaw-code,zznursing,cross-cutting}
mkdir -p docs/tech-stack-analysis/ruoyi-ai/code-examples/{multi-llm-factory,rag-pipeline,supervisor-agent}
mkdir -p docs/tech-stack-analysis/ai-passage-creator/code-examples/{state-graph,image-strategy}
mkdir -p docs/tech-stack-analysis/mewpaw-code/code-examples/{react-loop,sandbox}
mkdir -p docs/tech-stack-analysis/zznursing/code-examples/{iot-device,qianfan-integration}
```

- [ ] **Step 2: 创建总索引 README.md**

写入总索引文件，包含：
- 4个项目一览表（名称、定位、技术栈数量、GitHub链接）
- 导航说明
- 阅读顺序建议
- 面试导向教程使用指南

- [ ] **Step 3: 提交目录结构**

```bash
git add docs/tech-stack-analysis/README.md
git commit -m "feat: init tech stack analysis directory structure and index"
```

---

### Task 2: 搜索 ruoyi-ai 项目与技术栈资料

**Files:**
- No new files created in this task

**Interfaces:**
- Consumes: 项目仓库信息（已获取）
- Produces: 搜索结果为后续文档提供资料支撑

- [ ] **Step 1: 使用 anysearch 搜索 ruoyi-ai 项目分析资料**

搜索内容：
- ruoyi-ai 项目架构分析、同类项目对比
- LangChain4j 1.13.0 最佳实践与面试题
- langgraph4j 1.5.3 使用方式
- Sa-Token + JWT 鉴权面试高频题
- MyBatis-Plus + 多数据源面试考点

- [ ] **Step 2: 使用 anysearch 搜索技术栈面试资料**

搜索内容：
- Spring Boot 3.5 + AI 集成面试题
- 多厂商大模型统一接入（工厂模式）面试题
- RAG 全链路面试高频考点
- Milvus/Weaviate/Qdrant 向量数据库对比与面试题
- MCP 协议面试题
- Warm-Flow BPM 引擎面试题
- Redis + Redisson 分布式锁面试题

---

### Task 3: 产出 ruoyi-ai 项目全景概览

**Files:**
- Create: `docs/tech-stack-analysis/ruoyi-ai/00-PROJECT-OVERVIEW.md`

**Interfaces:**
- Consumes: Task 2 搜索资料、项目 README 分析
- Produces: 项目全景文档，后续技术栈文档的入口

- [ ] **Step 1: 撰写项目全景文档**

包含：
- 项目定位一句话（企业级AI应用开发框架）
- 架构全景图（Graphviz 格式）
- 四层架构：展现层 → 应用层 → AI层 → 基础设施层
- 技术栈总表（按层级分类）
- 模块划分与职责（ruoyi-admin, ruoyi-modules/chat, aiflow, workflow, system）
- 核心业务流程（用户请求 → 多模型路由 → Agent调度 → RAG检索 → 响应）
- 面试中如何介绍这个项目（3分钟版本 + 1分钟版本）

- [ ] **Step 2: 提交**

```bash
git add docs/tech-stack-analysis/ruoyi-ai/00-PROJECT-OVERVIEW.md
git commit -m "feat(ruoyi-ai): add project overview document"
```

---

### Task 4: 产出 ruoyi-ai Spring Boot + LangChain4j 集成

**Files:**
- Create: `docs/tech-stack-analysis/ruoyi-ai/01-spring-boot-langchain4j.md`

- [ ] **Step 1: 撰写技术文档**

核心内容：
- **3个核心概念**：Spring Boot 自动配置、LangChain4j 的 Spring Boot Starter、AiService 代理
- **实战应用**：项目中如何通过 `@AiService` 注解定义 AI 接口、如何配置模型工厂
- **核心代码片段**：`ChatAssistant.java` 接口定义 + 配置类
- **面试题**：
  - Q1: Spring Boot 如何集成 LangChain4j？自动配置原理是什么？
  - Q2: @AiService 注解的工作原理是什么？和传统 Service 有什么区别？
  - Q3: 项目中如何实现多模型切换？工厂模式 + 配置中心怎么做？
- **面试避坑指南**
- **与 Spring AI Alibaba 的对比**（为后续跨项目对比铺垫）

- [ ] **Step 2: 提交**

```bash
git add docs/tech-stack-analysis/ruoyi-ai/01-spring-boot-langchain4j.md
git commit -m "feat(ruoyi-ai): add Spring Boot + LangChain4j integration doc"
```

---

### Task 5: 产出 ruoyi-ai 多厂商大模型工厂模式

**Files:**
- Create: `docs/tech-stack-analysis/ruoyi-ai/02-multi-llm-factory.md`

- [ ] **Step 1: 撰写技术文档**

核心内容：
- **3个核心概念**：工厂模式、策略模式、模型实例管理
- **实战应用**：支持 OpenAI/DeepSeek/通义千问/智谱/glm-5.2/Ollama 等，一行配置切换
- **核心代码片段**：`ModelFactory.java` 工厂接口 + 各厂商实现 + 配置类
- **设计亮点**：如何做到"一行配置切换模型，无需修改业务代码"
- **面试题**：
  - Q1: 设计一个多厂商大模型统一接入方案，要考虑哪些方面？
  - Q2: 工厂模式 + 策略模式在这个场景下的优缺点？
  - Q3: 如果新增一个模型厂商，需要修改哪些代码？如何做到开闭原则？
- **面试避坑指南**

- [ ] **Step 2: 提交**

```bash
git add docs/tech-stack-analysis/ruoyi-ai/02-multi-llm-factory.md
git commit -m "feat(ruoyi-ai): add multi-LLM factory pattern doc"
```

---

### Task 6: 产出 ruoyi-ai RAG 全链路

**Files:**
- Create: `docs/tech-stack-analysis/ruoyi-ai/03-rag-pipeline.md`

- [ ] **Step 1: 撰写技术文档**

核心内容：
- **RAG 完整链路图**：文档上传 → 解析 → 切分 → Embedding → 存储 → 检索 → Rerank → LLM
- **3个核心概念**：Chunking策略、Embedding模型选择、检索 + Rerank 双阶段
- **实战应用**：支持 PDF/Word/Markdown/Excel 多格式解析
- **核心代码片段**：文档解析器 + 切分器 + Embedding 调用 + 检索代码
- **面试题**：
  - Q1: 如何选择和优化 Chunking 策略？项目中用了哪几种？
  - Q2: Embedding 模型的选择依据是什么？为什么用多个？
  - Q3: Rerank 的作用是什么？为什么检索后还要 Rerank？
  - Q4: GraphRAG 和传统向量检索有什么区别？项目中怎么结合的？
- **面试避坑指南**

- [ ] **Step 2: 提交**

---

### Task 7: 产出 ruoyi-ai 向量数据库工厂策略

**Files:**
- Create: `docs/tech-stack-analysis/ruoyi-ai/04-vector-store-strategy.md`

- [ ] **Step 1: 撰写技术文档**

核心内容：
- **3个核心概念**：向量数据库、HNSW 索引、工厂策略模式
- **实战应用**：Milvus/Weaviate/Qdrant 三种向量库通过配置切换
- **核心代码片段**：`VectorStoreFactory.java` + 各实现类
- **面试题**：
  - Q1: Milvus、Weaviate、Qdrant 的对比和选型依据？
  - Q2: 项目中如何优雅地支持多种向量数据库？策略模式怎么实现的？
  - Q3: 向量检索的准确率和召回率如何保证？
- **面试避坑指南**

- [ ] **Step 2: 提交**

---

### Task 8: 产出 ruoyi-ai langgraph4j 流程编排引擎

**Files:**
- Create: `docs/tech-stack-analysis/ruoyi-ai/05-langgraph-flow-engine.md`

- [ ] **Step 1: 撰写技术文档**

核心内容：
- **3个核心概念**：StateGraph、节点(Node)、边(Edge)
- **实战应用**：11种节点类型（Start/End/LLMAnswer/Classifier/KnowledgeRetrieval等）
- **核心代码片段**：流程图定义 + 条件边 + 节点执行器
- **面试题**：
  - Q1: langgraph4j 的 StateGraph 工作原理是什么？
  - Q2: 项目中如何实现条件分支？Switcher 节点怎么设计的？
  - Q3: 人机协作（HumanFeedback）怎么实现的？InterruptedFlow 机制？
- **面试避坑指南**

- [ ] **Step 2: 提交**

---

### Task 9: 产出 ruoyi-ai Supervisor 多智能体

**Files:**
- Create: `docs/tech-stack-analysis/ruoyi-ai/06-supervisor-agent.md`

- [ ] **Step 1: 撰写技术文档**

核心内容：
- **架构图**：Supervisor Agent → Skills Agent / WebSearch Agent / SQL Agent / Chart Agent
- **3个核心概念**：Agent 编排、工具调用、Supervisor 调度
- **实战应用**：4个子智能体的职责分工与协作流程
- **核心代码片段**：Supervisor 调度逻辑 + 各 Agent 定义
- **面试题**：
  - Q1: Supervisor 多智能体模式和单一 Agent 相比有什么优势？
  - Q2: 如何避免多个 Agent 间的冲突和资源竞争？
  - Q3: Agent 执行失败时如何容错和恢复？
  - Q4: 项目中 Agent 的 Tool 是怎么管理的？MCP Server 和本地工具怎么区分？
- **面试避坑指南**

- [ ] **Step 2: 提交**

---

### Task 10: 产出 ruoyi-ai MCP 协议实现

**Files:**
- Create: `docs/tech-stack-analysis/ruoyi-ai/07-mcp-protocol.md`

- [ ] **Step 1: 撰写技术文档**

核心内容：
- **3个核心概念**：MCP Server、MCP Client、SSE 传输
- **实战应用**：内置工具（文件系统、Python脚本执行）+ SSE MCP Clients
- **核心代码片段**：MCP Server 注册 + Client 调用
- **面试题**：
  - Q1: MCP 协议解决了什么问题？和传统 API 调用有什么区别？
  - Q2: 项目中如何实现 SSE 传输的 MCP？
  - Q3: MCP 工具的安全性如何保障？
- **面试避坑指南**

- [ ] **Step 2: 提交**

---

### Task 11: 产出 ruoyi-ai MyBatis-Plus + MySQL + 多数据源

**Files:**
- Create: `docs/tech-stack-analysis/ruoyi-ai/08-mybatis-plus-mysql.md`

- [ ] **Step 1: 撰写技术文档**

核心内容：
- **3个核心概念**：MyBatis-Plus 自动映射、Dynamic-Datasource、多数据源事务
- **实战应用**：项目中如何配置多数据源、读写分离
- **核心代码片段**：多数据源配置 + 数据源切换注解
- **面试题**：
  - Q1: MyBatis-Plus 和 MyBatis 的区别？项目为什么选 Plus？
  - Q2: Dynamic-Datasource 多数据源的实现原理？
  - Q3: 多数据源场景下如何保证事务一致性？
- **面试避坑指南**

- [ ] **Step 2: 提交**

---

### Task 12: 产出 ruoyi-ai Redis + Redisson 分布式锁/缓存

**Files:**
- Create: `docs/tech-stack-analysis/ruoyi-ai/09-redis-redisson.md`

- [ ] **Step 1: 撰写技术文档**

核心内容：
- **3个核心概念**：缓存策略、分布式锁、Redisson 可重入锁
- **实战应用**：项目中 Redis 的缓存使用场景 + Lock4j 分布式锁
- **核心代码片段**：Redisson 配置 + 分布式锁使用 + 缓存注解
- **面试题**：
  - Q1: Redis 分布式锁的几种实现方式？Redisson 为什么比 SETNX 好？
  - Q2: 项目中 Redis 有哪些缓存场景？缓存穿透/击穿/雪崩怎么防？
  - Q3: Lock4j 和 Redisson 的关系？项目中怎么结合使用的？
- **面试避坑指南**

- [ ] **Step 2: 提交**

---

### Task 13: 产出 ruoyi-ai Sa-Token + JWT 认证鉴权

**Files:**
- Create: `docs/tech-stack-analysis/ruoyi-ai/10-sa-token-auth.md`

- [ ] **Step 1: 撰写技术文档**

核心内容：
- **3个核心概念**：Sa-Token、JWT、RBAC 权限模型
- **实战应用**：项目中 Sa-Token + JWT 双重认证
- **核心代码片段**：登录认证 + 权限校验 + 路由拦截
- **面试题**：
  - Q1: Sa-Token 和 Spring Security 的对比？项目为什么选 Sa-Token？
  - Q2: JWT 和 Session 认证的区别？项目中为什么用双重认证？
  - Q3: RBAC 权限模型怎么设计的？数据权限如何控制？
- **面试避坑指南**

- [ ] **Step 2: 提交**

---

### Task 14: 产出 ruoyi-ai BPM 审批引擎 + SSE/WebSocket + 监控部署

**Files:**
- Create: `docs/tech-stack-analysis/ruoyi-ai/11-bpm-workflow.md`
- Create: `docs/tech-stack-analysis/ruoyi-ai/12-sse-websocket.md`
- Create: `docs/tech-stack-analysis/ruoyi-ai/13-monitor-deploy.md`

- [ ] **Step 1: 撰写 BPM 审批引擎文档**

核心内容：
- Warm-Flow 工作流引擎
- 审批流程定义、节点流转、会签/或签
- 面试题：BPM 引擎的核心数据结构、流程流转算法

- [ ] **Step 2: 撰写 SSE/WebSocket 实时通信文档**

核心内容：
- SSE 和 WebSocket 的对比
- 项目中 SSE 的流式输出实现
- 面试题：SSE 和 WebSocket 选型依据、连接管理、心跳机制

- [ ] **Step 3: 撰写监控部署文档**

核心内容：
- Spring Boot Admin 监控
- Docker Compose 一键部署
- 面试题：服务监控指标、容器化部署最佳实践

- [ ] **Step 4: 提交**

```bash
git add docs/tech-stack-analysis/ruoyi-ai/1{1,2,3}-*.md
git commit -m "feat(ruoyi-ai): add BPM, SSE/WebSocket, and monitor/deploy docs"
```

---

### Task 15: 产出 ruoyi-ai 面试题汇总 + STAR 亮点

**Files:**
- Create: `docs/tech-stack-analysis/ruoyi-ai/14-interview-questions.md`
- Create: `docs/tech-stack-analysis/ruoyi-ai/15-star-highlights.md`

- [ ] **Step 1: 撰写面试题汇总**

包含：
- 选择题（10道）
- 判断题（5道）
- 简答题（10道）
- 场景题（5道）
- 深挖题（5道）
- 覆盖全部技术栈

- [ ] **Step 2: 撰写 STAR 亮点**

包含至少5个STAR亮点：
- 亮点1：多厂商大模型统一接入（工厂模式）
- 亮点2：RAG 全链路 + GraphRAG 增强
- 亮点3：Supervisor 多智能体调度
- 亮点4：langgraph4j 流程编排引擎
- 亮点5：MCP 协议实现

- [ ] **Step 3: 提交**

```bash
git add docs/tech-stack-analysis/ruoyi-ai/1{4,5}-*.md
git commit -m "feat(ruoyi-ai): add interview questions and STAR highlights"
```

---

### Task 16: 产出 ruoyi-ai 可运行代码示例

**Files:**
- Create: `docs/tech-stack-analysis/ruoyi-ai/code-examples/multi-llm-factory/ModelFactory.java`
- Create: `docs/tech-stack-analysis/ruoyi-ai/code-examples/multi-llm-factory/OpenAiModelProvider.java`
- Create: `docs/tech-stack-analysis/ruoyi-ai/code-examples/multi-llm-factory/DashScopeModelProvider.java`
- Create: `docs/tech-stack-analysis/ruoyi-ai/code-examples/multi-llm-factory/ModelConfig.java`
- Create: `docs/tech-stack-analysis/ruoyi-ai/code-examples/multi-llm-factory/pom.xml`
- Create: `docs/tech-stack-analysis/ruoyi-ai/code-examples/rag-pipeline/DocumentParser.java`
- Create: `docs/tech-stack-analysis/ruoyi-ai/code-examples/rag-pipeline/ChunkingStrategy.java`
- Create: `docs/tech-stack-analysis/ruoyi-ai/code-examples/rag-pipeline/RagPipeline.java`
- Create: `docs/tech-stack-analysis/ruoyi-ai/code-examples/rag-pipeline/pom.xml`
- Create: `docs/tech-stack-analysis/ruoyi-ai/code-examples/supervisor-agent/SupervisorAgent.java`
- Create: `docs/tech-stack-analysis/ruoyi-ai/code-examples/supervisor-agent/SkillsAgent.java`
- Create: `docs/tech-stack-analysis/ruoyi-ai/code-examples/supervisor-agent/WebSearchAgent.java`
- Create: `docs/tech-stack-analysis/ruoyi-ai/code-examples/supervisor-agent/pom.xml`

- [ ] **Step 1: 编写多模型工厂示例**

核心代码：
- `ModelFactory.java`：工厂接口 + 策略模式
- `OpenAiModelProvider.java`：OpenAI 实现
- `DashScopeModelProvider.java`：通义千问实现
- `ModelConfig.java`：配置类
- `pom.xml`：Maven 依赖

- [ ] **Step 2: 编写 RAG 管线示例**

核心代码：
- `DocumentParser.java`：多格式文档解析器
- `ChunkingStrategy.java`：多种切分策略
- `RagPipeline.java`：RAG 完整管线

- [ ] **Step 3: 编写 Supervisor Agent 示例**

核心代码：
- `SupervisorAgent.java`：调度器
- `SkillsAgent.java` / `WebSearchAgent.java`：子Agent实现

- [ ] **Step 4: 提交**

```bash
git add docs/tech-stack-analysis/ruoyi-ai/code-examples/
git commit -m "feat(ruoyi-ai): add runnable code examples for multi-LLM, RAG, and Supervisor Agent"
```

---

### Task 17: 搜索 ai-passage-creator 项目与技术栈资料

**Files:**
- No new files

- [ ] **Step 1: 使用 anysearch 搜索 ai-passage-creator 项目资料**

搜索内容：
- Spring AI Alibaba 1.1.0 使用与面试题
- StateGraph 多智能体编排
- 策略模式配图（6种配图方式）
- SSE 流式输出实现
- MyBatis-Flex 面试题
- Stripe 支付集成面试题

---

### Task 18: 产出 ai-passage-creator 项目全景 + 技术栈文档

**Files:**
- Create: `docs/tech-stack-analysis/ai-passage-creator/00-PROJECT-OVERVIEW.md`
- Create: `docs/tech-stack-analysis/ai-passage-creator/01-spring-ai-alibaba.md`
- Create: `docs/tech-stack-analysis/ai-passage-creator/02-multi-agent-orchestration.md`
- Create: `docs/tech-stack-analysis/ai-passage-creator/03-image-strategy-pattern.md`
- Create: `docs/tech-stack-analysis/ai-passage-creator/04-sse-streaming.md`
- Create: `docs/tech-stack-analysis/ai-passage-creator/05-human-in-loop.md`
- Create: `docs/tech-stack-analysis/ai-passage-creator/06-mybatis-flex-mysql.md`
- Create: `docs/tech-stack-analysis/ai-passage-creator/07-stripe-payment.md`
- Create: `docs/tech-stack-analysis/ai-passage-creator/08-redis-redisson.md`
- Create: `docs/tech-stack-analysis/ai-passage-creator/09-vue3-antd.md`
- Create: `docs/tech-stack-analysis/ai-passage-creator/10-interview-questions.md`
- Create: `docs/tech-stack-analysis/ai-passage-creator/11-star-highlights.md`

- [ ] **Step 1: 撰写项目全景文档**

- [ ] **Step 2: 撰写 Spring AI Alibaba + StateGraph 文档**

核心内容：
- Spring AI Alibaba 和 LangChain4j 的对比（呼应 ruoyi-ai）
- StateGraph 的图编排原理
- 5Agent 协作流程：content_generator → image_analyzer → parallel_image_generator → content_merger

- [ ] **Step 3: 撰写多Agent编排文档**

核心内容：
- Agent 分工设计
- StateGraph 状态流转
- 面试题：Agent 间通信、状态共享、错误传播

- [ ] **Step 4: 撰写配图策略模式文档**

核心内容：
- 6种配图方式（Pexels/Mermaid/Iconify/表情包/AI生图/SVG）
- 策略模式实现 + 降级机制
- 面试题：策略模式优缺点、降级策略设计

- [ ] **Step 5: 撰写 SSE 流式输出文档**

核心内容：
- SSE 事件流设计
- 各阶段事件（AGENT2_STREAMING、IMAGE_COMPLETE 等）
- 前端 EventSource 处理

- [ ] **Step 6: 撰写人机协作文档**

核心内容：
- 三阶段创作流程（选题→大纲→正文配图）
- 每步可介入的设计
- 面试题：人机协作的交互设计、状态管理

- [ ] **Step 7: 撰写 MyBatis-Flex + MySQL 文档**

- [ ] **Step 8: 撰写 Stripe 支付 + VIP 会员体系文档**

- [ ] **Step 9: 撰写 Redis + Redisson 文档**

- [ ] **Step 10: 撰写 Vue 3 + Ant Design Vue 前端概览**

- [ ] **Step 11: 撰写面试题汇总 + STAR 亮点**

- [ ] **Step 12: 提交**

```bash
git add docs/tech-stack-analysis/ai-passage-creator/
git commit -m "feat(ai-passage-creator): add all tech stack analysis docs, interview questions, and STAR highlights"
```

---

### Task 19: 产出 ai-passage-creator 可运行代码示例

**Files:**
- Create: `docs/tech-stack-analysis/ai-passage-creator/code-examples/state-graph/ContentGenerationFlow.java`
- Create: `docs/tech-stack-analysis/ai-passage-creator/code-examples/state-graph/AgentState.java`
- Create: `docs/tech-stack-analysis/ai-passage-creator/code-examples/state-graph/pom.xml`
- Create: `docs/tech-stack-analysis/ai-passage-creator/code-examples/image-strategy/ImageStrategy.java`
- Create: `docs/tech-stack-analysis/ai-passage-creator/code-examples/image-strategy/PexelsStrategy.java`
- Create: `docs/tech-stack-analysis/ai-passage-creator/code-examples/image-strategy/ImageContext.java`
- Create: `docs/tech-stack-analysis/ai-passage-creator/code-examples/image-strategy/pom.xml`

- [ ] **Step 1: 编写 StateGraph 编排示例**

- [ ] **Step 2: 编写配图策略模式示例**

- [ ] **Step 3: 提交**

---

### Task 20: 搜索 mewpaw-code 项目与技术栈资料

**Files:**
- No new files

- [ ] **Step 1: 使用 anysearch 搜索 mewpaw-code 项目资料**

搜索内容：
- Java 21 CLI 应用开发
- ReAct Agent 模式面试题
- LangChain4j Tool 设计
- 安全沙箱实现（5层）
- TUI/REPL 交互模式

---

### Task 21: 产出 mewpaw-code 项目全景 + 技术栈文档

**Files:**
- Create: `docs/tech-stack-analysis/mewpaw-code/00-PROJECT-OVERVIEW.md`
- Create: `docs/tech-stack-analysis/mewpaw-code/01-java21-springboot.md`
- Create: `docs/tech-stack-analysis/mewpaw-code/02-react-agent-loop.md`
- Create: `docs/tech-stack-analysis/mewpaw-code/03-langchain4j-tools.md`
- Create: `docs/tech-stack-analysis/mewpaw-code/04-security-sandbox.md`
- Create: `docs/tech-stack-analysis/mewpaw-code/05-tui-repl.md`
- Create: `docs/tech-stack-analysis/mewpaw-code/06-interview-questions.md`
- Create: `docs/tech-stack-analysis/mewpaw-code/07-star-highlights.md`

- [ ] **Step 1: 撰写项目全景文档**

- [ ] **Step 2: 撰写 Java 21 + Spring Boot CLI 文档**

- [ ] **Step 3: 撰写 ReAct Agent 循环文档**

核心内容：
- Thought → Action → Observation 循环
- 与 LangChain4j 的集成
- 面试题：ReAct 模式原理、规划与执行的平衡

- [ ] **Step 4: 撰写 LangChain4j 6种工具设计文档**

- [ ] **Step 5: 撰写5层安全沙箱文档**

核心内容：
- 5层沙箱架构
- 每层的职责和实现
- 面试题：安全沙箱设计、权限控制、资源隔离

- [ ] **Step 6: 撰写 TUI/REPL 交互模式文档**

- [ ] **Step 7: 撰写面试题汇总 + STAR 亮点**

- [ ] **Step 8: 提交**

---

### Task 22: 产出 mewpaw-code 可运行代码示例

**Files:**
- Create: `docs/tech-stack-analysis/mewpaw-code/code-examples/react-loop/ReActAgent.java`
- Create: `docs/tech-stack-analysis/mewpaw-code/code-examples/react-loop/Tool.java`
- Create: `docs/tech-stack-analysis/mewpaw-code/code-examples/react-loop/AgentState.java`
- Create: `docs/tech-stack-analysis/mewpaw-code/code-examples/react-loop/pom.xml`
- Create: `docs/tech-stack-analysis/mewpaw-code/code-examples/sandbox/SecuritySandbox.java`
- Create: `docs/tech-stack-analysis/mewpaw-code/code-examples/sandbox/SandboxLayer.java`
- Create: `docs/tech-stack-analysis/mewpaw-code/code-examples/sandbox/pom.xml`

- [ ] **Step 1: 编写 ReAct 循环示例**

- [ ] **Step 2: 编写安全沙箱示例**

- [ ] **Step 3: 提交**

---

### Task 23: 搜索 zznursing 项目与技术栈资料

**Files:**
- No new files

- [ ] **Step 1: 使用 anysearch 搜索 zznursing 项目资料**

搜索内容：
- 百度千帆 AI 集成面试题
- 华为云 IoTDA 设备接入
- 微信小程序开发面试题
- IoT 设备协议（MQTT/CoAP）
- 养老行业物联网解决方案

---

### Task 24: 产出 zznursing 项目全景 + 技术栈文档

**Files:**
- Create: `docs/tech-stack-analysis/zznursing/00-PROJECT-OVERVIEW.md`
- Create: `docs/tech-stack-analysis/zznursing/01-spring-boot-iot.md`
- Create: `docs/tech-stack-analysis/zznursing/02-baidu-qianfan-ai.md`
- Create: `docs/tech-stack-analysis/zznursing/03-huawei-iotda.md`
- Create: `docs/tech-stack-analysis/zznursing/04-wechat-miniapp.md`
- Create: `docs/tech-stack-analysis/zznursing/05-vue3-admin.md`
- Create: `docs/tech-stack-analysis/zznursing/06-mysql-redis.md`
- Create: `docs/tech-stack-analysis/zznursing/07-iot-device-protocol.md`
- Create: `docs/tech-stack-analysis/zznursing/08-interview-questions.md`
- Create: `docs/tech-stack-analysis/zznursing/09-star-highlights.md`

- [ ] **Step 1: 撰写项目全景文档**

- [ ] **Step 2: 撰写 Spring Boot IoT 后端架构文档**

- [ ] **Step 3: 撰写百度千帆 AI 集成文档**

- [ ] **Step 4: 撰写华为云 IoTDA 设备接入文档**

- [ ] **Step 5: 撰写微信小程序开发文档**

- [ ] **Step 6: 撰写 Vue 3 管理后台文档**

- [ ] **Step 7: 撰写 MySQL + Redis 数据架构文档**

- [ ] **Step 8: 撰写 IoT 设备协议文档（MQTT/CoAP）**

- [ ] **Step 9: 撰写面试题汇总 + STAR 亮点**

- [ ] **Step 10: 提交**

---

### Task 25: 产出 zznursing 可运行代码示例

**Files:**
- Create: `docs/tech-stack-analysis/zznursing/code-examples/iot-device/DeviceDataHandler.java`
- Create: `docs/tech-stack-analysis/zznursing/code-examples/iot-device/MqttClientConfig.java`
- Create: `docs/tech-stack-analysis/zznursing/code-examples/iot-device/pom.xml`
- Create: `docs/tech-stack-analysis/zznursing/code-examples/qianfan-integration/QianfanAIClient.java`
- Create: `docs/tech-stack-analysis/zznursing/code-examples/qianfan-integration/pom.xml`

- [ ] **Step 1: 编写 IoT 设备数据处理示例**

- [ ] **Step 2: 编写千帆 AI 集成示例**

- [ ] **Step 3: 提交**

---

### Task 26: 产出跨项目综合文档

**Files:**
- Create: `docs/tech-stack-analysis/cross-cutting/java-ai-ecosystem-comparison.md`
- Create: `docs/tech-stack-analysis/cross-cutting/enterprise-architecture-patterns.md`
- Create: `docs/tech-stack-analysis/cross-cutting/overall-star-highlights.md`

- [ ] **Step 1: 撰写 AI 框架对比文档**

核心内容：
- LangChain4j  vs  Spring AI Alibaba  vs  百度千帆
- 三个项目的 AI 技术选型对比表
- 面试题：如何选择 AI 框架？不同场景的选型建议

- [ ] **Step 2: 撰写企业架构模式提炼文档**

核心内容：
- 工厂模式（多模型接入）
- 策略模式（配图方式、向量数据库）
- Agent 模式（Supervisor、ReAct、StateGraph）
- 适配器模式（MCP 协议）
- 面试题：这些模式在实际项目中的应用场景

- [ ] **Step 3: 撰写综合 STAR 亮点文档**

核心内容：
- 面试自我介绍模板（3分钟版本）
- 4个项目的技术亮点对比
- 面试官常见追问及应对策略

- [ ] **Step 4: 提交**

```bash
git add docs/tech-stack-analysis/cross-cutting/
git commit -m "feat(cross-cutting): add AI framework comparison, architecture patterns, and overall STAR highlights"
```

---

## 自检清单

**1. Spec 覆盖度检查：**
- [ ] 设计文档中的全部技术栈已覆盖
- [ ] 每个技术栈文档 ≥ 1个核心代码片段
- [ ] 每个技术栈文档 ≥ 2道面试题
- [ ] 每个项目 ≥ 3个STAR亮点
- [ ] 代码示例可运行或完整可编译
- [ ] 统一模板使用

**2. 占位符检查：**
- [ ] 无 "TBD" / "TODO" / "implement later" 等占位符
- [ ] 所有代码片段实际写入
- [ ] 所有面试题有实际题目和回答框架

**3. 类型一致性检查：**
- [ ] 跨项目技术栈命名一致
- [ ] 文件路径引用正确
- [ ] 技术栈文档之间的交叉引用正确

**4. 范围检查：**
- [ ] Phase 1 聚焦面试导向，不混入深度系列内容
- [ ] 每个项目独立可交付，不依赖其他项目完成