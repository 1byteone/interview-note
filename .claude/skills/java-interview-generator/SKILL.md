# Java & AI 项目面试题自动生成 Skill

## 概述

扫描 Java/AI 项目结构和技术栈，自动生成 10 种类型的面试题，支持深度追问和模拟面试。

## 触发条件

用户输入以下任意关键词时激活：
- "出题"/"生成面试题"/"根据项目出题"/"给我出题"
- "从项目生成面试题"/"根据代码生成问题"
- "面试准备"/"帮我准备面试"
- "模拟面试"/"练习面试"
- 提到项目路径 + 面试（如 "给这个项目出面试题"）

## 使用方式

```bash
# 1. 从项目生成全部面试题
/load-skill java-interview-generator
然后：根据这个项目给我出面试题

# 2. 指定项目路径
根据 D:/code/my-project 项目生成面试题

# 3. 指定题型
根据项目出选择题和场景题

# 4. 指定难度
根据项目出高级面试题

# 5. 模拟面试
根据这个项目模拟面试，你当面试官
```

## 工作流程

```
Phase 1: 项目分析
    ↓
① 扫描项目结构 (pom.xml / build.gradle / requirements.txt / Dockerfile)
② 识别技术栈 (Spring Boot, Nacos, Gateway, Sentinel, Redis, MySQL, ES, Docker...)
③ 分析项目架构 (模块划分、依赖关系、数据流)
④ 输出技术栈报告

Phase 2: 知识图谱构建
    ↓
⑤ 构建技术依赖关系图
⑥ 识别核心模块和关键代码路径
⑦ 生成知识点覆盖矩阵

Phase 3: 题目生成
    ↓
⑧ 按 5 级难度 + 10 种题型 生成题目
    - Level 1: 知识题（技术栈基础）
    - Level 2: 原理题（底层机制）
    - Level 3: 场景题（业务场景）
    - Level 4: 项目深挖（项目特定）
    - Level 5: 架构设计（系统设计）
    ↓
⑨ 输出结构化面试题文档

Phase 4: 模拟面试 (可选)
    ↓
⑩ 启动交互式面试官模式
    - 逐题提问
    - 根据回答深度追问
    - 给出评分和改进建议
```

## 技术栈识别规则

### Java 项目 (pom.xml/build.gradle 分析)

| 检测项 | 识别标识 | 对应面试题 |
|--------|----------|------------|
| Spring Boot | spring-boot-starter | 自动配置、启动流程 |
| Spring Cloud | spring-cloud-starter | 微服务拆分、服务治理 |
| Nacos | nacos-client / spring-cloud-starter-alibaba-nacos | 服务发现、配置中心 |
| Gateway | spring-cloud-starter-gateway | 路由、过滤器、限流 |
| OpenFeign | spring-cloud-starter-openfeign | 远程调用、动态代理 |
| Sentinel | sentinel-core / spring-cloud-starter-alibaba-sentinel | 限流、熔断、降级 |
| Seata | seata-all / seata-spring-boot-starter | 分布式事务 (AT/TCC/Saga) |
| RocketMQ | rocketmq-spring-boot-starter | 消息队列、事务消息 |
| Redis | spring-boot-starter-data-redis / redisson | 缓存、分布式锁 |
| MySQL | mysql-connector-j | 索引、事务、分库分表 |
| Elasticsearch | elasticsearch-rest-high-level-client | 倒排索引、搜索优化 |
| MyBatis | mybatis-spring-boot-starter | SQL 优化、ORM 映射 |
| Docker | Dockerfile 存在 | 容器化、镜像优化 |
| Nginx | nginx.conf 存在 | 反向代理、负载均衡 |

### AI 项目 (requirements.txt/pyproject.toml 分析)

| 检测项 | 识别标识 | 对应面试题 |
|--------|----------|------------|
| LangChain | langchain | Agent、Chain、RAG |
| LangGraph | langgraph | StateGraph、Agent Loop |
| FastAPI | fastapi | API 设计、异步处理 |
| PyTorch | torch | 模型训练、推理 |
| Transformers | transformers | LLM 微调、推理优化 |
| Chroma/Pinecone/Qdrant | chromadb/pinecone/qdrant-client | 向量数据库、检索 |
| OpenAI | openai | API 调用、Function Calling |
| Anthropic | anthropic | Claude API、Tool Use |

## 面试题生成模板

### 模板 1: 选择题
```markdown
### 题目 XX：[技术点]
**难度**：Level 1
**考察点**：[核心概念]

**问题**：[问题描述]？
- A. [选项A]
- B. [选项B]
- C. [选项C]
- D. [选项D]

**答案**：[正确答案]

**解析**：[详细解析]
```

### 模板 2: 简答题
```markdown
### 题目 XX：[技术点]
**难度**：Level 2
**考察点**：[原理机制]

**问题**：[问题描述]？

**答案**：[参考答案]
1. [要点1]
2. [要点2]
3. [要点3]

**解析**：[深入解析]
```

### 模板 3: 代码题
```markdown
### 题目 XX：[技术点]
**难度**：Level 3
**考察点**：[编程能力]

**问题**：[问题描述]：

```java
[代码框架]
```

**答案**：
```java
[参考答案]
```

**解析**：[关键点说明]
```

### 模板 4: Bug 题
```markdown
### 题目 XX：[技术点]
**难度**：Level 3
**考察点**：[调试能力]

**问题**：以下代码有什么问题？如何修复？

```java
[有 Bug 的代码]
```

**答案**：
1. Bug 原因：[原因分析]
2. 修复方案：[修复代码]

**解析**：[深入分析]
```

### 模板 5: 场景题
```markdown
### 题目 XX：[技术点]
**难度**：Level 3
**考察点**：[问题解决能力]

**场景**：[场景描述]

**问题**：[问题]？

**答案**：
1. [方案1]
2. [方案2]
3. [方案3]

**解析**：[方案对比和选择理由]
```

### 模板 6: 设计题
```markdown
### 题目 XX：[技术点]
**难度**：Level 4
**考察点**：[架构设计能力]

**需求**：[需求描述]

**要求**：[系统约束]

**设计**：
1. 整体架构：[架构图或描述]
2. 核心模块：[模块说明]
3. 数据流：[数据流转]
4. 高可用方案：[容错设计]

**解析**：[设计决策说明]
```

### 模板 7: 深挖题
```markdown
### 题目 XX：[技术点]
**难度**：Level 4
**考察点**：[项目经验]

**问题**：[项目中使用的技术选型]？

**追问链**：
面试官1：为什么选择 [技术A] 而不是 [技术B]？
候选人：[回答]
面试官2：[技术A] 在什么场景下会失效？
候选人：[回答]
面试官3：如果 [技术A] 失效，你的备选方案是什么？
候选人：[回答]
面试官4：生产环境的 [技术A] 遇到过什么问题？怎么解决的？
候选人：[回答]

**评估要点**：[考察的核心能力]
```

## 难度分级

| 级别 | 定位 | 题量建议 | 对应工程师 |
|------|------|----------|------------|
| Level 1 | 基础概念 | 30% | 初级工程师 |
| Level 2 | 原理机制 | 30% | 中级工程师 |
| Level 3 | 场景实战 | 20% | 高级工程师 |
| Level 4 | 项目深挖 | 15% | 资深工程师 |
| Level 5 | 架构设计 | 5% | 架构师 |

## 输出格式

### 完整输出
```markdown
# [项目名称] 面试题

## 技术栈分析
- 后端框架：[Spring Boot / Spring Cloud / ...]
- 注册中心：[Nacos / Eureka / ...]
- 网关：[Gateway / Zuul / ...]
- 数据库：[MySQL / Redis / ES / ...]
- 消息队列：[RocketMQ / Kafka / ...]
- 部署：[Docker / K8s / ...]

## 题目列表

### Level 1: 基础题（共 N 题）
...

### Level 2: 原理题（共 N 题）
...

### Level 3: 场景题（共 N 题）
...

### Level 4: 项目深挖（共 N 题）
...

### Level 5: 架构设计（共 N 题）
...

## 推荐复习路线
...
```

## 模拟面试模式

启动模拟面试后，Skill 按照以下流程工作：

1. **自我介绍**：面试官自我介绍 + 说明面试流程
2. **逐题提问**：从 Level 1 开始，逐步升级难度
3. **倾听回答**：等待用户作答
4. **深度追问**：根据回答内容追问 2-3 轮
5. **评分反馈**：每题给出评分 + 改进建议
6. **总结报告**：面试结束后输出完整评估报告

### 面试官风格
- **严格模式**：连环追问，压力测试
- **温和模式**：引导式，适当提示
- **实战模式**：纯场景题，考察问题解决能力

## 与 interview-note 文档库联动

本 Skill 生成的题目可以持续积累到 `interview-note` 文档库中，输出目录为：

```
interview-note/
├── projects/                    # 项目特定面试题
│   ├── java-projects/
│   │   └── [project-name]/
│   │       ├── interview-questions.md  # 自动生成的题目
│   │       └── project-analysis.md     # 项目分析报告
│   └── ai-projects/
│       └── [project-name]/
│           └── interview-questions.md
└── interview-tools/
    └── question-generator/
        └── generated/               # 自动生成的题目缓存
```

## 示例

### 示例 1: Spring Cloud Alibaba 电商项目

技术栈：Spring Boot + Spring Cloud + Nacos + Gateway + OpenFeign + Sentinel + RocketMQ + Redis + MySQL + ES + Docker

生成的题目涵盖：
- Nacos 服务发现与配置中心原理
- Gateway 过滤器链与动态路由
- Sentinel 限流熔断与滑动窗口
- RocketMQ 事务消息与消息堆积
- Redis 缓存穿透/击穿/雪崩
- MySQL 分库分表与索引优化
- ES 倒排索引与亿级搜索
- Docker 镜像优化与多阶段构建
- 分布式事务 Seata AT 模式
- 高并发秒杀系统设计

### 示例 2: RAG + Agent 知识问答系统

技术栈：Python + FastAPI + LangChain + LangGraph + Chroma + OpenAI + Docker

生成的题目涵盖：
- RAG 检索增强生成原理
- Chunking 策略选择与优化
- Agent ReAct 循环实现
- Multi-Agent 协作模式
- LangGraph StateGraph 状态管理
- 向量数据库检索与索引
- Prompt Engineering 技巧
- LLM 幻觉控制
- RAG 评估指标
- 生产级 Agent 系统设计

## 注意事项

1. 本 Skill 不会读取敏感信息（密码、Token、密钥）
2. 题目生成基于项目技术栈，不依赖具体业务代码
3. 自动生成的题目建议人工审核后使用
4. 支持增量更新：项目变更后只重新生成变更部分