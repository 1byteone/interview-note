# 面试题生成器 — 从项目自动生成面试题

## 📋 概述

本文档介绍如何从 Java/AI 项目自动生成多类型面试题，包括选择题、简答题、代码题、Bug题、场景题、系统设计题等。

---

## 🚀 核心思路

### 项目分析流程

```
输入：Java/AI 项目
    ↓
1. 扫描项目结构
   ├── pom.xml / build.gradle  → 技术栈分析
   ├── application.yml        → 配置分析
   ├── Dockerfile             → 部署分析
   ├── docker-compose.yml     → 架构分析
   ├── src/main/java          → 源码分析
   └── README.md              → 项目概述
    ↓
2. 技术栈识别
   ├── Spring Boot / Cloud
   ├── Nacos / Gateway / OpenFeign / Sentinel
   ├── Redis / MySQL / ES / RocketMQ
   ├── Docker / Nginx
   └── 其他中间件
    ↓
3. 知识图谱构建
   ├── 技术依赖关系图
   ├── 核心模块拓扑
   ├── 数据流分析
   └── 架构设计模式
    ↓
4. 面试题生成
   ├── Level 1: 知识题（技术栈相关）
   ├── Level 2: 原理题（底层实现）
   ├── Level 3: 场景题（业务场景）
   ├── Level 4: 项目深挖（项目特定的问题）
   └── Level 5: 架构题（系统设计）
```

---

## 🎯 基于 Java 项目生成面试题

### 示例项目：Spring Boot + Spring Cloud Alibaba 电商系统

#### 项目结构分析
```
my-ecommerce/
├── pom.xml                    # Spring Boot + Spring Cloud + Nacos + Gateway + Sentinel + RocketMQ + Redis + ES
├── application.yml
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/ecommerce/
│   │   │       ├── product/       # 商品服务
│   │   │       ├── order/         # 订单服务
│   │   │       ├── payment/       # 支付服务
│   │   │       ├── inventory/     # 库存服务
│   │   │       └── common/        # 公共模块
│   │   └── resources/
│   └── test/
├── Dockerfile
└── docker-compose.yml
```

#### 自动生成 Level 1：知识题

```text
1. Nacos 有哪些核心功能？

2. OpenFeign 底层使用了什么技术？

3. Gateway 为什么适合做 API 网关？

4. Sentinel 如何实现限流？

5. Redisson 分布式锁怎么实现？

6. RocketMQ 如何保证消息不丢失？

7. Elasticsearch 的倒排索引是什么？

8. Redis 的缓存穿透、击穿、雪崩如何解决？
```

#### 自动生成 Level 2：原理题

```text
1. Nacos 服务发现与健康检查的机制是什么？

2. OpenFeign 的动态代理是如何实现的？

3. Gateway 的 WebFlux 原理是什么？

4. Sentinel 的滑动时间窗口算法怎么实现？

5. Redisson 分布式锁的 WatchDog 机制是什么？

6. RocketMQ 的 CommitLog 和 ConsumeQueue 关系？

7. Elasticsearch 的分片与副本机制？

8. Redis Cluster 的哈希槽分配原理？
```

#### 自动生成 Level 3：场景题

```text
场景一：突然有 10 万用户同时抢购商品
1. 你如何设计秒杀系统？
2. Redis 放在哪里？如何扣减库存？
3. RocketMQ 如何削峰？
4. 如何保证最终一致性？
5. 如何防止超卖？
6. 如何解决消息重复消费？

场景二：商品搜索响应慢
1. 如何优化搜索性能？
2. ES 的索引如何设计？
3. 搜索热词如何缓存？
4. 多条件组合查询如何优化？

场景三：订单状态不一致
1. 分布式事务如何实现？
2. Seata 的 AT 模式原理？
3. 如何用 RocketMQ 实现最终一致性？
4. 消息重试和死信如何处理？
```

#### 自动生成 Level 4：项目深挖题

```text
1. 为什么选择 Nacos 而不是 Eureka / Consul / Zookeeper？

2. 为什么使用 Redis 而不是本地缓存？

3. 为什么选择 RocketMQ 而不是 Kafka / RabbitMQ？

4. 为什么选择 Sentinel 而不是 Hystrix / Resilience4j？

5. Gateway 出现单点故障怎么办？

6. 你们的数据库是如何分库分表的？

7. 你们的 Docker 部署方案是怎样的？

8. Nginx 如何配置负载均衡？
```

#### 自动生成 Level 5：架构题

```text
设计一个亿级商品搜索系统，要求：
- MySQL 存储商品数据
- Redis 缓存热数据
- Elasticsearch 实现全文搜索
- RocketMQ 同步数据
- Spring Cloud 微服务架构
- Nacos 服务发现
- Gateway 统一网关
- Sentinel 流量控制
- Docker 容器化部署

请设计：
1. 整体架构图
2. 数据流设计
3. 索引设计
4. 缓存策略
5. 高可用方案
6. 容灾方案
```

---

## 🤖 基于 AI 项目生成面试题

### 示例项目：RAG + Agent 知识问答系统

#### 项目结构分析
```
my-rag-agent/
├── requirements.txt
├── app/
│   ├── main.py              # FastAPI 入口
│   ├── rag/
│   │   ├── loader.py        # 文档加载
│   │   ├── chunker.py       # 文档分块
│   │   ├── embedder.py      # 向量化
│   │   └── retriever.py     # 检索
│   ├── agent/
│   │   ├── planning.py      # 任务规划
│   │   ├── tools.py         # 工具定义
│   │   └── executor.py      # 执行器
│   └── utils/
│       └── memory.py        # 记忆模块
├── Dockerfile
└── config.yaml
```

#### 自动生成 Level 1：知识题

```text
1. 什么是 RAG？它的工作原理是什么？

2. 什么是向量数据库？有哪些常用的向量数据库？

3. AI Agent 的核心组件有哪些？

4. 什么是 Prompt Engineering？

5. LangChain 和 LangGraph 的区别是什么？

6. 什么是 Embedding？

7. 什么是 Chunking？

8. 什么是 Reranking？
```

#### 自动生成 Level 2：原理题

```text
1. RAG 的检索策略有哪些？如何选择？

2. Chunking 策略有哪些？如何优化？

3. Agent 的 ReAct 循环是什么？

4. 向量数据库的检索原理是什么？

5. Multi-Agent 的协作机制是什么？

6. Agent 的规划与执行流程是什么？

7. 如何评估 RAG 系统的性能？

8. LangGraph 的状态图机制是什么？
```

#### 自动生成 Level 3：场景题

```text
场景一：知识库问答系统
1. 如何设计文档加载流程？
2. 如何选择 Chunking 策略？
3. 如何优化检索效果？
4. 如何保证回答的准确性？

场景二：Agent 自动化工作流
1. 如何设计任务规划？
2. 如何实现工具调用？
3. 如何处理 Agent 错误？
4. 如何实现多 Agent 协作？

场景三：RAG 系统性能优化
1. 如何提高检索速度？
2. 如何降低 LLM 调用成本？
3. 如何优化上下文窗口？
4. 如何实现缓存策略？
```

#### 自动生成 Level 4：项目深挖题

```text
1. 为什么选择这个 Chunking 策略？

2. 为什么选择这个 Embedding 模型？

3. 为什么选择这个向量数据库？

4. 如何评估你的 RAG 系统的效果？

5. 你的 Agent 如何处理复杂任务？

6. 你的 Agent 如何处理错误和异常？

7. 如何保证 Agent 的安全性？

8. 如何优化 Agent 的响应速度？
```

#### 自动生成 Level 5：架构题

```text
设计一个企业级知识库系统，支持：
- 多格式文档上传（PDF、Word、Markdown）
- 智能分块和向量化
- 多轮对话问答
- 多 Agent 协作
- 权限管理
- 日志审计
- 高并发支持

请设计：
1. 整体架构
2. 数据流设计
3. 检索策略
4. 缓存策略
5. 部署方案
6. 可扩展性方案
```

---

## 🛠️ 实现方案

### 方案一：基于 LangChain 的 Agent 自动出题

```python
from langchain.agents import AgentExecutor, create_openai_tools_agent
from langchain_core.tools import tool
from langchain_openai import ChatOpenAI

@tool
def analyze_project_structure(repo_path: str) -> str:
    """分析项目结构"""
    # 扫描项目文件
    # 识别技术栈
    # 返回项目概述
    pass

@tool
def generate_choice_questions(tech_stack: str) -> list:
    """生成选择题"""
    # 根据技术栈生成选择题
    pass

@tool
def generate_scenario_questions(project_analysis: str) -> list:
    """生成场景题"""
    # 根据项目分析生成场景题
    pass

@tool
def generate_design_questions(architecture: str) -> list:
    """生成设计题"""
    # 根据架构设计生成设计题
    pass
```

### 方案二：基于 Harness 的自动出题循环

```text
Phase 1: Project Analysis
    Agent 扫描项目 → 输出技术栈分析报告

Phase 2: Question Generation
    Agent 根据技术栈生成各类题目

Phase 3: Quality Review
    Agent 交叉评审题目质量

Phase 4: Difficulty Calibration
    Agent 标识题目难度级别

Phase 5: Answer Generation
    Agent 生成参考答案和解析
```

---

## 📦 推荐开源项目

### 可用作知识源的 Java 项目

| 项目 | 特点 | 技术栈 |
|------|------|--------|
| mall | 电商系统 | Spring Boot + MyBatis + Redis + ES + MongoDB |
| RuoYi | 权限管理系统 | Spring Boot + MyBatis + Redis + Shiro |
| eladmin | 后台管理系统 | Spring Boot + JPA + Redis + ES |
| PassJava | 面试刷题系统 | Spring Cloud + MyBatis + Redis + ES |

### 可用作知识源的 AI 项目

| 项目 | 特点 | 技术栈 |
|------|------|--------|
| langchain | LLM 应用框架 | Python + LangChain |
| langgraph | Agent 编排 | Python + LangGraph |
| chroma | 向量数据库 | Python |
| haystack | RAG 框架 | Python + Haystack |

---

> 💡 **核心价值**：从真实项目生成的面试题比普通题库更有针对性，能真正考察候选人的实战能力。