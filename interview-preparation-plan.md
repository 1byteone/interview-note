# Java + AI 面试通关计划

> 目标：3 个月系统准备 Java 后端 + AI 工程师面试
> 周期：2026-08-20 ~ 2026-11-20
> 配套：本仓库全部文档 + 自动出题 Skill + 模拟面试

---

## 📅 总体时间线

```
第 1 个月：知识底座（Java Core + 中间件）
第 2 个月：框架深化（Spring + Spring Cloud + AI 基础）
第 3 个月：项目冲刺（项目包装 + 场景题 + 模拟面试）
```

---

## 🗓️ 第一阶段：知识底座（第 1 个月）

### Week 1：Java 核心

| 天数 | 内容 | 配套文档 |
|------|------|----------|
| Day 1-2 | Java 基础（数据类型/集合/异常） | `java/core/` |
| Day 3-4 | JVM（内存/GC/调优） | `java/jvm/` |
| Day 5-6 | 并发编程（线程池/AQS/CAS） | `java/juc/` |
| Day 7 | 复盘 + 自测 | 用生成器出题自测 |

**通关标准**：
- [ ] 能画出 JVM 内存模型
- [ ] 能讲清 HashMap/ConcurrentHashMap 原理
- [ ] 能说出线程池 7 大参数
- [ ] 能背出 GC 收集器对比

### Week 2：MySQL + Redis

| 天数 | 内容 | 配套文档 |
|------|------|----------|
| Day 8-9 | MySQL（索引/事务/MVCC） | `middleware/mysql/` |
| Day 10 | MySQL 进阶（分库分表/高可用） | `middleware/mysql/` |
| Day 11-12 | Redis（数据结构/持久化/集群） | `middleware/redis/` |
| Day 13 | Redisson（分布式锁/WatchDog） | `middleware/redis/redisson-*` |
| Day 14 | 复盘 + 自测 | 场景题实战 |

**通关标准**：
- [ ] 能画 B+ 树索引结构
- [ ] 能讲清 MVCC 和 READ VIEW
- [ ] 能写 Redis 分布式锁代码
- [ ] 能回答缓存穿透/击穿/雪崩

### Week 3：消息队列 + 搜索

| 天数 | 内容 | 配套文档 |
|------|------|----------|
| Day 15-16 | RocketMQ（高可用/事务/堆积） | `spring-cloud/rocketmq/` |
| Day 17 | Kafka（对比/选型） | `middleware/kafka/` |
| Day 18-19 | Elasticsearch（倒排/B+树/集群） | `middleware/elasticsearch/` |
| Day 20 | 复盘 + 自测 | 消息队列深度题 |

**通关标准**：
- [ ] 能讲 RocketMQ 事务消息原理
- [ ] 能回答消息不丢失/不重复/顺序性
- [ ] 能算出 ES 分片数
- [ ] 能画 MySQL→Canal→ES 架构图

### Week 4：第一阶段总复习

- 用 `question_generator.py` 对自己的项目/知识自测
- 整理错题本（薄弱知识点列表）
- 模拟一次"Java 基础 + 中间件"面试

---

## 🗓️ 第二阶段：框架深化（第 2 个月）

### Week 5：Spring 生态

| 天数 | 内容 | 配套文档 |
|------|------|----------|
| Day 29-30 | Spring Boot（自动配置/启动流程） | `spring/spring-boot/` |
| Day 31 | Spring MVC（DispatcherServlet/拦截器） | `spring/spring-mvc/` |
| Day 32 | Spring Data JPA + MyBatis | `spring/spring-data/` |
| Day 33 | Spring Security + OAuth2 | - |
| Day 34 | 复盘 + 自测 | 场景题 |

### Week 6：Spring Cloud 微服务

| 天数 | 内容 | 配套文档 |
|------|------|----------|
| Day 36-37 | Nacos（注册/配置/集群） | `spring-cloud/nacos/` |
| Day 38 | Gateway（路由/过滤器/限流） | `spring-cloud/gateway/` |
| Day 39 | OpenFeign + Sentinel | `spring-cloud/openfeign/` + `sentinel/` |
| Day 40 | Seata 分布式事务 | `spring-cloud/seata/` |
| Day 41 | 复盘 + 自测 | 微服务面试 |

**通关标准**：
- [ ] 能独立搭一套完整微服务（Nacos + Gateway + Feign + Sentinel）
- [ ] 能画服务注册发现完整流程图
- [ ] 能讲清分布式事务 4 种模式
- [ ] 能写自定义 Gateway 过滤器

### Week 7：DevOps 部署

| 天数 | 内容 | 配套文档 |
|------|------|----------|
| Day 43-44 | Docker（镜像/Compose/优化） | `devops/docker/` |
| Day 45 | Nginx（反向代理/负载均衡） | `devops/nginx/` |
| Day 46-47 | 内网穿透（FRP 实战） | `devops/nat-traversal/` |
| Day 48 | CI/CD（GitLab CI 流水线） | `devops/ci-cd/` |
| Day 49 | 复盘 + 自测 | 实践部署 |

### Week 8：AI 基础

| 天数 | 内容 | 配套文档 |
|------|------|----------|
| Day 50-51 | Python + FastAPI | `ai/python/` |
| Day 52-53 | LLM 基础（Transformer/推理） | `ai/llm/` |
| Day 54-55 | RAG（检索/分块/评估） | `ai/rag/` |
| Day 56 | 复盘 + 自测 | AI 面试题 |

---

## 🗓️ 第三阶段：项目冲刺（第 3 个月）

### Week 9：项目梳理
- 整理 1-2 个核心项目（Java 项目 + AI 项目）
- 用生成器为每个项目生成专属面试题
- 针对每个技术选型准备"为什么"答案

### Week 10：场景题专项
- 秒杀/高并发/分布式事务/缓存一致性
- 消息堆积/幂等/顺序
- RAG 幻觉/Agent 稳定性
- 每天 3 道场景题，写标准答案

### Week 11：系统设计专项
- 亿级搜索系统
- 高并发下单系统
- RAG 知识库系统
- Multi-Agent 系统
- 每 2 天 1 道，画架构图 + 讲方案

### Week 12：模拟面试冲刺
- 每天 1 场完整模拟面试（45-60 分钟）
- 使用 interview-tools 面试官模式
- 记录每次评分，针对性补弱

---

## 📋 面试题型对照表

| 题型 | 考核能力 | 准备重点 |
|------|----------|----------|
| 选择题 | 基础知识 | 概念准确性 |
| 简答题 | 理解深度 | 原理分层阐述 |
| 代码题 | 编码能力 | 手写核心算法 |
| Bug 题 | 调试能力 | 常见问题排查 |
| 场景题 | 问题解决 | 方案对比+选型 |
| 设计题 | 架构能力 | 完整架构图 |
| 深挖题 | 项目经验 | 真实经历+教训 |

---

## 📝 每周复盘模板

```markdown
## Week N 复盘（日期）

### 本周完成
1. ...

### 薄弱知识点
1. [知识点] - 原因：... - 计划：...

### 面试失误记录
1. [问题] - 我的回答：... - 标准答案：... - 差距：...

### 下周计划
1. ...
```

---

## 🎯 自我评分标准

### 面试回答质量评分（1-5 分）

| 分数 | 标准 |
|------|------|
| 5 分 | 概念精准 + 原理深入 + 结合项目 + 主动扩展 |
| 4 分 | 概念准确 + 原理正确 + 少量扩展 |
| 3 分 | 概念基本正确 + 说不出原理细节 |
| 2 分 | 知道概念 + 解释不清 |
| 1 分 | 完全不会 |

**目标**：核心高频题 **4 分以上**，横向题 3 分以上。

---

## 🔗 配套资源

- 自动出题：`interview-tools/question-generator/question_generator.py`
- 面试 Skill：`.claude/skills/java-interview-generator/SKILL.md`
- 全部题库：本仓库各 `interview-questions.md`
- 项目模板：`examples/sample-java-project/`

---

> 💡 提示：不要只是"看"，要"输出"。每道题学完，用自己的话讲一遍（或写一遍），才是真正的掌握。