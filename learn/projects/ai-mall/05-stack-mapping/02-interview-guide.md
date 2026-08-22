# 02 · 面试指南

> 本文提供 AI 智能商城项目的面试话术和题库，帮助你用 STAR 法则清晰表达项目经验，并准备了从项目中引出技术深挖的路径。

---

## 一、STAR 法则项目介绍

### 1.1 30 秒版本

```
**S (Situation):** 电商平台需要提升搜索体验，用户只能用关键词精确搜索，无法用自然语言描述需求。

**T (Task):** 构建一个 AI 智能商城系统，集成 AI 搜索、智能客服、秒杀、分布式事务等能力，覆盖 16 个技术栈。

**A (Action):** 采用 Spring Cloud Alibaba 微服务架构 + Python FastAPI AI 服务。Java 负责业务逻辑（用户/商品/订单/秒杀），Python 负责 AI 推理（搜索 Agent + RAG 客服）。数据通过 MySQL → Redis 向量库同步，Agent 通过 LangChain 调用向量检索工具 + LLM 生成推荐。

**R (Result):** 实现了自然语言搜索（"5000元以下续航强的华为手机"）、智能推荐（带推荐理由）、RAG 客服（基于知识库的问答）、秒杀高并发（Redis 预扣 + MQ 异步 + 乐观锁）。系统通过 Docker 容器化部署，GitLab CI 自动测试和部署。
```

### 1.2 3 分钟版本

```
项目背景：
这是一个基于 Spring Cloud Alibaba + Python FastAPI 的 AI 电商系统。我负责整体架构设计和技术选型。

架构亮点：
1. 双语言架构：Java 处理复杂业务逻辑（11 个微服务），Python 处理 AI 推理（4 个 AI 服务），通过 HTTP 通信解耦。
2. AI 搜索：用户输入自然语言，Agent 调用向量检索工具召回 TOP-10 商品，LLM 生成推荐理由。
3. 秒杀系统：Redis 预扣库存（扛 10w QPS）→ RocketMQ 异步削峰 → 乐观锁兜底一致性。
4. 分布式事务：Seata AT 模式保证下单扣库存的跨服务一致性。

我的职责：
- 设计微服务拆分方案和技术选型
- 搭建 AI 搜索 Agent 架构
- 配置 Docker 容器化和 CI/CD 流水线
- 制定监控告警方案

关键成果：
- 搜索体验从"关键词匹配"升级为"语义理解"
- 秒杀系统支持 10w QPS 峰值
- 系统通过 Docker 一键部署，环境一致性 100%
```

### 1.3 10 分钟版本（含追问链）

```
...（先讲 3 分钟版本）

面试官追问 1: "AI 搜索的防幻觉怎么做的？"
我：四层防护——Prompt 约束（"严禁编造"）、上下文约束（只给向量召回的数据）、低温（temperature=0.1）、结构约束（response_format 强制字段）。

面试官追问 2: "Redis 和 MySQL 数据一致性怎么保证？"
我：秒杀场景用 Redis 预扣 + 乐观锁兜底，Cache-Aside 模式用"先更新 DB 再删缓存"。

面试官追问 3: "为什么用 Python 做 AI 服务而不是 Java？"
我：AI 生态成熟度。LangChain/LangGraph 仅在 Python 中有完整实现，Spring AI 还在发展中。AI 应用瓶颈在模型推理延迟，不在框架性能。

面试官追问 4: "这个项目如果上生产，你觉得还有什么改进？"
我：三个方向：1) 增量数据同步（当前是全量同步）；2) RedisSaver 持久化会话记忆（当前是 InMemorySaver）；3) 混合检索（向量 + ES 关键词 + RRF 融合）。
```

---

## 二、高频面试题

### 2.1 架构设计类

**Q1: 讲一下你的 AI 商城整体架构。**

回答思路：分层架构（接入层 → 网关层 → 服务层 → 中间件层 → 基础设施层），双语言分工（Java 业务 + Python AI），服务间通信（同步 Feign + 异步 RocketMQ）。

**Q2: 为什么选择 Spring Cloud Alibaba 而不是 Spring Cloud Netflix？**

回答思路：Nacos（AP+CP 双模式，Netflix 的 Eureka 已停更）、Sentinel（实时监控控制台，Hystrix 已停更）、Seata（分布式事务，Netflix 无对标组件）。

**Q3: 微服务拆分的原则是什么？**

回答思路：按业务领域拆分（用户/商品/订单/库存/支付/秒杀），每个服务独立数据库、独立部署、独立团队维护。服务间通过 API 契约通信，不共享数据库。

### 2.2 秒杀系统类

**Q4: 秒杀库存扣减怎么设计的？**

回答思路：三层——Redis 预扣（Lua 脚本，扛 10w QPS）→ RocketMQ 异步削峰 → MySQL 乐观锁兜底一致性（WHERE stock >= quantity）。

**Q5: 怎么防止超卖？**

回答思路：三层防超卖——Redis Lua 事务保证原子性、RocketMQ 消息幂等（Redis 30s 锁）、MySQL 乐观锁（WHERE beforeStock = 旧值）。

**Q6: Redis 和 DB 数据不一致怎么办？**

回答思路：乐观锁兜底（DB 层最终一致性），定时任务每分钟同步 Redis 库存到 DB，Redis 宕机降级直接走 DB 乐观锁。

### 2.3 AI 搜索类

**Q7: 为什么用 Agent 而不是 Chain 做推荐？**

回答思路：推荐需要"检索 → 判断 → 生成"的循环决策，Agent 的 LangGraph 图执行框架正好满足。条件提取是"原文 → 结构化"的线性映射，用 Chain 就够了。

**Q8: create_agent 的工作原理是什么？**

回答思路：底层是 LangGraph 驱动的有状态图执行——Tool 绑定到模型、构建 Agent 状态图（Agent 节点 + Tool 节点）、条件边（需要工具？→ 进入 Tool 节点 / 不需要 → 直接输出）、response_format 通过 tool_strategy 强制结构化输出。

**Q9: 怎么防止 LLM 编造不存在的商品？**

回答思路：四层防护——Prompt 约束（"严禁编造"）、上下文约束（只给向量召回的数据）、低温（temperature=0.1）、结构约束（response_format 强制字段，空数据兜底）。

### 2.4 分布式事务类

**Q10: Seata AT 模式的原理是什么？**

回答思路：一阶段执行业务 SQL + 生成 beforeImage/afterImage 写入 undo_log；二阶段全局提交删除 undo_log，全局回滚根据 undo_log 生成反向 SQL 恢复数据。

**Q11: 什么场景用 Seata，什么场景用 MQ 最终一致性？**

回答思路：强一致性场景（下单扣库存）用 Seata AT 模式；最终一致性场景（秒杀削峰、支付回调）用 MQ 异步 + 幂等消费。

### 2.5 向量检索类

**Q12: 向量检索和 ES 关键词搜索的区别？**

回答思路：ES 匹配"字面"（倒排索引），向量匹配"意思"（语义相似度）。实际项目中两者互补——ES 做精确匹配和属性过滤，向量做语义召回，最后通过 RRF 融合排序。

**Q13: HNSW 索引的原理是什么？**

回答思路：分层可导航小世界图——高层节点稀疏（长距离跳跃），低层节点稠密（精确细分）。搜索从高层到低层逐层逼近，O(log n) 复杂度，召回率约 95%。

**Q14: 为什么选择 Redis 做向量库？**

回答思路：复用现有 Redis 基础设施，零额外运维；RedisVL 提供官方 LangChain 集成；HNSW 索引支持百万级向量毫秒级检索。预留了迁移到 Milvus 的路径。

---

## 三、从项目引出技术深挖

### 3.1 面试官追问链

```
"讲一下你的项目"
    ↓
"AI 搜索怎么做的？"
    ↓ 你回答提到 Agent
"Agent 和 Chain 的区别？"
    ↓ 你回答提到 LangGraph
"LangGraph 的 Checkpointer 怎么工作的？"
    ↓ 你回答提到 InMemorySaver
"生产环境会用 InMemorySaver 吗？为什么？"
    ↓ 你回答提到 RedisSaver 和持久化会话
"RedisSaver 和 InMemorySaver 的性能差异？"
    ↓ ...
```

### 3.2 技术深挖路径

| 项目技术点 | 可深挖的技术方向 | 面试官想考察 |
|-----------|----------------|------------|
| **AI 搜索 Agent** | LangChain/LangGraph 源码 | 框架理解深度 |
| **向量检索** | HNSW/ANN 算法原理 | 算法基础 |
| **秒杀系统** | 缓存一致性、分布式锁 | 分布式系统理解 |
| **Seata 事务** | AT/TCC/Saga 模式选择 | 分布式事务理解 |
| **Docker 部署** | K8s 编排、服务网格 | 运维能力 |
| **多 Provider 切换** | 策略模式、工厂模式 | 设计模式掌握 |

### 3.3 加分项

```
面试加分项:
1. 主动指出项目的局限和改进方向
   "当前用 InMemorySaver，生产环境应切换到 RedisSaver"
   "当前全量同步，生产环境应改为增量同步"

2. 展示技术选型的权衡思考
   "选 Redis 做向量库是因为复用现有基础设施，但预留了迁移到 Milvus 的路径"
   "选 Python 做 AI 服务是因为 AI 生态成熟，Java 业务逻辑用 Spring Boot 更合适"

3. 用数据说话
   "秒杀 QPS 约 10w"
   "AI 搜索 P95 延迟约 2s，其中 LLM 推理占 76%"
   "向量检索本身仅 ~10ms，瓶颈在 LLM 生成"
```

---

## 四、项目简历包装

### 4.1 项目名称

**AI 智能商城 — 16 技术栈贯穿实战项目**

### 4.2 技术栈关键词

```
Spring Cloud Alibaba · Nacos · Gateway · Sentinel · Seata
Spring Boot 3 · MyBatis-Plus · MySQL · Redis · RocketMQ · Elasticsearch
Python · FastAPI · LangChain · LangGraph · RAG · OpenAI
Docker · Docker Compose · GitLab CI · Prometheus · Grafana
```

### 4.3 项目亮点

```
亮点 1: AI 智能搜索——基于 LangChain Agent + Redis 向量库，用户自然语言搜索，Agent 自主决策并生成带理由的推荐
亮点 2: 秒杀高并发——Redis 预扣 (10w QPS) + RocketMQ 异步削峰 + MySQL 乐观锁兜底，三层防超卖
亮点 3: 双语言架构——Java 11 个微服务处理业务逻辑，Python 4 个 AI 服务处理推理，解耦独立
亮点 4: 全容器化——Docker Compose 一键启动 23 个容器，GitLab CI 自动流水线，Prometheus + Grafana 监控
亮点 5: 分布式事务——Seata AT 模式保证下单扣库存的一致性，RocketMQ 事务消息保证支付回调
```

---

> **下一篇：** [../challenges.md](../challenges.md) — 挑战与扩展