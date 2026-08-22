# 07 · Java 核心技能复盘：与四个项目串联

> 将 mall-exercise 的 Java 核心技能与前面三个项目串联，形成完整的 Java 后端 + AI 面试体系。
>
> **系列定位：** 四个项目形成"Java 核心 → Java 微服务 → Python AI → Java AI"的完整面试覆盖。

---

## 一、四个项目的面试价值矩阵

| 面试考点 | mall-exercise | mall-micro-cloud | mall-ai-search | text2sql |
|---------|-------------|-----------------|---------------|----------|
| **Java 核心（AOP/反射/集合）** | ⭐⭐⭐⭐⭐ | ⭐⭐⭐ | — | — |
| **微服务架构** | — | ⭐⭐⭐⭐⭐ | — | ⭐⭐⭐ |
| **分布式事务** | — | ⭐⭐⭐⭐⭐ | — | — |
| **高并发/秒杀** | — | ⭐⭐⭐⭐⭐ | — | — |
| **消息队列** | — | ⭐⭐⭐⭐⭐ | — | — |
| **Redis 缓存策略** | ⭐⭐⭐⭐ | ⭐⭐⭐⭐ | ⭐⭐⭐ | — |
| **MyBatis-Plus 高级** | ⭐⭐⭐⭐ | ⭐⭐⭐⭐ | — | — |
| **LLM 集成** | — | — | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ |
| **RAG 实现** | — | — | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ |
| **向量检索** | — | — | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐ |
| **Agent 机制** | — | — | ⭐⭐⭐⭐⭐ | — |
| **Java AI 融合** | — | ⭐⭐ | ⭐⭐ | ⭐⭐⭐⭐⭐ |
| **搜索** | — | ⭐⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐⭐ |
| **SQL 安全** | — | — | — | ⭐⭐⭐⭐⭐ |

---

## 二、面试话术体系

### 10 秒版本

> "我做了四个项目：一个 Java 微服务电商、一个 Java 核心技能练习模块、一个 Python AI 搜索、一个 Java Text2SQL。覆盖了从 Java 基础到微服务到 AI 的全栈技能。"

### 60 秒版本

> "Java 核心方面，我写过自定义 AOP 注解做缓存和权限控制，用反射实现过动态 SQL 生成和验证框架。微服务方面，我参与过 12 个服务的电商系统，处理过分布式事务、秒杀高并发、消息队列。AI 方面，我用 Python 做过 LangChain Agent 的智能搜索，也用 Java 的 Spring AI 做过 Text2SQL。三个方向都有实战经验。"

### 5 分钟版本

按四个项目逐一展开：
1. **mall-exercise** → AOP/反射/集合/Redis 缓存/MP 高级查询
2. **mall-micro-cloud** → 微服务/网关/分布式事务/秒杀/消息队列
3. **mall-ai-search** → LangChain Agent/向量检索/多供应商/Embedding
4. **text2sql** → Spring AI/DeepSeek/RAG/SQL 验证器

---

## 三、30 道面试高频题速查

### Java 核心

| # | 问题 | 答案位置 |
|---|------|---------|
| 1 | AOP 四种通知类型区别 | 01-AOP-PRACTICE.md |
| 2 | 自定义注解 + AOP 完整流程 | 01-AOP-PRACTICE.md |
| 3 | 缓存切面怎么处理穿透 | 01-AOP-PRACTICE.md |
| 4 | toMap 的 mergeFunction 作用 | 02-COLLECTIONS.md |
| 5 | groupingBy 自定义分组 | 02-COLLECTIONS.md |
| 6 | 递归构建树性能问题 | 02-COLLECTIONS.md |
| 7 | 反射读取注解生成 SQL | 03-REFLECTION.md |
| 8 | 反射性能优化 | 03-REFLECTION.md |
| 9 | JDK Proxy vs CGLIB | 03-REFLECTION.md |
| 10 | Cache-Aside 模式 | 04-REDIS-CACHE.md |
| 11 | 缓存击穿解决方案 | 04-REDIS-CACHE.md |
| 12 | 缓存穿透解决方案 | 04-REDIS-CACHE.md |
| 13 | LambdaQueryWrapper vs QueryWrapper | 05-MYBATISPLUS-ADV.md |
| 14 | saveBatch 原理 | 05-MYBATISPLUS-ADV.md |
| 15 | selectMaps 场景 | 05-MYBATISPLUS-ADV.md |

### 微服务与分布式

| # | 问题 | 答案位置 |
|---|------|---------|
| 16 | 微服务拆分原则 | mall-micro-cloud/00-OVERVIEW.md |
| 17 | Gateway 自定义过滤器 | mall-micro-cloud/01-NACOS-GATEWAY.md |
| 18 | Seata AT 原理 | mall-micro-cloud/04-ORDER-SEATA.md |
| 19 | 秒杀四层防护 | mall-micro-cloud/06-SECKILL-HIGHCONCUR.md |
| 20 | Redisson 看门狗 | mall-micro-cloud/06-SECKILL-HIGHCONCUR.md |
| 21 | 布隆过滤器原理 | mall-micro-cloud/06-SECKILL-HIGHCONCUR.md |
| 22 | JWT 无感知续期 | mall-micro-cloud/07-USER-JWT.md |
| 23 | 消息最终一致性 | mall-micro-cloud/09-ROCKETMQ.md |

### AI 与 RAG

| # | 问题 | 答案位置 |
|---|------|---------|
| 24 | 什么是 OpenAI 兼容协议 | mall-ai-search/04-LLM-PROVIDER.md |
| 25 | 为什么用 Embedding + 向量检索 | mall-ai-search/05-EMBEDDING.md |
| 26 | HNSW 索引原理 | mall-ai-search/06-VECTOR-STORE.md |
| 27 | Agent vs Chain 区别 | mall-ai-search/07-LANGCHAIN-AGENT.md |
| 28 | 防幻觉四层防护 | mall-ai-search/07-LANGCHAIN-AGENT.md |
| 29 | 混合检索设计 | text2sql/03-RAG-RETRIEVAL.md |
| 30 | SQL 四层验证 | text2sql/05-SQL-VALIDATOR.md |

---

## 四、技能栈总览

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         面试技能全栈图                                      │
│                                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  Java 核心 (mall-exercise)                                          │   │
│  │  AOP · 反射 · 集合 · Redis · MyBatis-Plus · 单元测试                │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                    │                                        │
│                                    ▼                                        │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  微服务架构 (mall-micro-cloud)                                      │   │
│  │  Spring Cloud · Nacos · Gateway · Seata · Redisson · RocketMQ      │   │
│  │  JWT · Sentinel · MongoDB · ES                                     │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                    │                                        │
│           ┌────────────────────────┼────────────────────────┐              │
│           ▼                        ▼                        ▼              │
│  ┌──────────────────┐  ┌──────────────────┐  ┌──────────────────┐         │
│  │ Python AI         │  │ Java AI           │  │  面试应用        │         │
│  │ (mall-ai-search)  │  │ (text2sql)        │  │                 │         │
│  │                   │  │                   │  │  "讲一个项目"    │         │
│  │ LangChain         │  │ Spring AI         │  │  → 四个项目串联  │         │
│  │ FastAPI           │  │ DeepSeek          │  │                 │         │
│  │ RedisVL           │  │ RAG               │  │  "技术难点"      │         │
│  │ Agent             │  │ SQL 验证器        │  │  → 秒杀/Agent/   │         │
│  │ BGE-M3            │  │ 混合检索          │  │    SQL 验证     │         │
│  └──────────────────┘  └──────────────────┘  └──────────────────┘         │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

> **本系列完成！** 四个项目，36 篇技术栈深度剖析文档，覆盖 Java 核心 → 微服务 → Python AI → Java AI 的完整面试技能体系。
>
> **祝你面试顺利！**