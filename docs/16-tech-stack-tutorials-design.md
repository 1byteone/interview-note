# 16 技术栈教程体系 — 架构设计文档

> 日期：2026-08-22
> 贯穿项目：AI 智能商城
> 受众：双轨制（👶 新手入门 + 🎯 面试进阶）

---

## 一、设计原则

### 1.1 双轨制（Dual-Track）

每个技术栈的文档分为两层，读者可自行选择路径：

```
👶 新手通道（Basics → Core → Mini-Project）
  → 从零搭建环境、最小可运行案例、图解概念
  → 无需前置知识，动手即学

🎯 面试进阶通道（Advanced → Interview → Mall Integration）
  → 源码级原理、场景题、面试高频考点
  → 需要一定前置经验
```

### 1.2 项目驱动（Project-Driven）

**STAR 法则**贯穿每个案例：

```
Situation — 真实业务场景（大促、流量洪峰、故障恢复）
Task      — 要解决的具体问题（性能指标、功能需求）
Action    — 技术方案选型与实现（含代码）
Result    — 效果数据 + 面试加分点
```

### 1.3 干中学（Learn by Doing）

- 每个技术栈至少 1 个独立小项目（Mini-Project）
- 核心栈有 AI 智能商城集成点（Mall Integration）
- 代码即文档，文档即可运行

---

## 二、目录结构设计

### 2.1 总览

```
learn/                                    ← 新顶层目录
├── 00-ROADMAP.md                         ← 总纲：学习路线 + 导航
├── 01-backend-development/               ← 后端开发总纲
├── 02-java/                              ← Java 核心
├── 03-spring-boot/                       ← Spring Boot (迁移 tutorials/01)
├── 04-python/                            ← Python 基础
├── 05-fastapi/                           ← FastAPI (新建)
├── 06-mysql/                             ← MySQL (迁移 tutorials/10)
├── 07-redis/                             ← Redis (迁移 tutorials/09)
├── 08-rocketmq/                          ← RocketMQ (迁移 tutorials/08)
├── 09-elasticsearch/                     ← ES (迁移 tutorials/11)
├── 10-docker/                            ← Docker (迁移 tutorials/12)
├── 11-linux/                             ← Linux (新建)
├── 12-infrastructure/                    ← 基础设施 (新建)
├── 13-dev-tools/                         ← 开发工具: Git/Conda/Jupyter (新建)
├── 14-langchain/                         ← LangChain (新建)
├── 15-rag/                               ← RAG (迁移 tutorials/13)
├── 16-openai/                            ← OpenAI (新建)
└── projects/ai-mall/                     ← 贯穿大项目 (整合现有资产)
```

### 2.2 每个技术栈标准模板

```
03-spring-boot/
├── README.md                        ← 技术栈总览 & 学习路径图
├── 01-basics/                       ← 👶 入门篇
│   ├── 01-quick-start.md            ← 环境搭建 + 第一个应用
│   ├── 02-core-concepts.md          ← 核心概念图解
│   └── examples/                    ← 最小可运行代码
├── 02-core/                         ← 👶 核心进阶
│   ├── 01-auto-config.md            ← 自动配置原理
│   ├── 02-starters.md
│   └── examples/
├── 03-advanced/                     ← 🎯 高级篇
│   ├── 01-actuator.md
│   ├── 02-observability.md
│   └── 03-graalvm-native.md
├── 04-projects/                     ← 项目实战
│   ├── mini-blog/                   ← 独立小项目：博客 REST API
│   └── mall-integration.md          ← 与 AI 商城集成点
├── 05-interview/                    ← 🎯 面试篇
│   ├── quick-revision.md            ← 速记版（考前 1 小时）
│   ├── deep-dive.md                 ← 深挖题（源码级）
│   ├── scenario.md                  ← 场景题
│   └── coding.md                    ← 代码题
└── resources.md                     ← 推荐资源
```

### 2.3 现有 tutorials 迁移映射

| 现有教程 | 目标位置 | 处理方式 |
|---------|---------|---------|
| 01-spring-boot | `learn/03-spring-boot/` | 迁移，扩展为双轨模板 |
| 02-microservice-arch | `learn/01-backend-development/` | 融合进后端总纲 |
| 03-nacos | `learn/12-infrastructure/` | 融合进基础设施 |
| 04-gateway | `learn/01-backend-development/` | 融合进后端总纲/API 网关 |
| 05-openfeign | `learn/01-backend-development/` | 融合进后端总纲 |
| 06-sentinel | `learn/12-infrastructure/` | 融合进基础设施 |
| 07-seata | `learn/12-infrastructure/` | 融合进基础设施 |
| 08-rocketmq | `learn/08-rocketmq/` | 迁移，扩展 |
| 09-redis | `learn/07-redis/` | 迁移，扩展 |
| 10-mysql | `learn/06-mysql/` | 迁移，扩展 |
| 11-elasticsearch | `learn/09-elasticsearch/` | 迁移，扩展 |
| 12-docker | `learn/10-docker/` | 迁移，扩展 |
| 13-hybrid-rag | `learn/15-rag/` | 迁移，扩展 |
| 14-langgraph-agent | `learn/14-langchain/` | 融合进 LangChain |
| 15-neo4j-graph | `learn/15-rag/` | 融合进 RAG 高级检索 |
| 16-sse-streaming | `learn/05-fastapi/` | 融合进 FastAPI |

---

## 三、AI 智能商城贯穿体系

### 3.1 各栈在商城中的角色

```
┌─────────────────────────────────────────────────────────────────┐
│                     AI 智能商城 (ai-mall)                         │
├───────────────┬───────────────────┬─────────────────────────────┤
│  Java 后端服务  │  Python AI 服务     │  基础设施 & DevOps          │
│  (Spring Boot)  │  (FastAPI)         │                            │
├───────────────┼───────────────────┼─────────────────────────────┤
│  mall-user     │  ai-search        │  Docker Compose 编排         │
│  mall-product  │  ai-customer       │  Nginx 反向代理              │
│  mall-order    │  text2sql         │  Git 分支策略/CI-CD          │
│  mall-payment  │  content-review   │  Linux 部署脚本              │
│  mall-inventory│  recommendation   │  ELK 日志收集                │
├───────────────┴───────────────────┴─────────────────────────────┤
│                    共享中间件层                                   │
│  Redis(缓存/分布式锁/Session)                                    │
│  RocketMQ(订单异步/秒杀削峰/分布式事务)                           │
│  MySQL(业务数据存储/分库分表)                                    │
│  Elasticsearch(商品搜索/日志分析)                                │
└─────────────────────────────────────────────────────────────────┘
```

### 3.2 贯穿学习路径

```
第一阶段：基础组
  backend-dev → Java → Python → Linux → Git/Conda
  ↓
第二阶段：后端核心组
  Spring Boot → MySQL → Redis → RocketMQ → ES
  ↓
第三阶段：AI 核心组
  FastAPI → LangChain → RAG → OpenAI
  ↓
第四阶段：基础设施
  Docker → 基础设施(Nginx/CI-CD/监控)
  ↓
第五阶段：AI 商城全栈实战
  projects/ai-mall/ 整合所有技术栈
```

---

## 四、STAR 案例体系

每个技术栈的 04-projects/ 下包含：

| 案例类型 | 说明 | 示例 |
|---------|------|------|
| Mini-Project | 独立小项目，专注单栈练习 | Spring Boot 博客 API |
| Mall-Integration | 在 AI 商城中的具体应用 | Redis 实现商品缓存 |
| Debug-Story | 真实踩坑与修复记录 | MySQL 死锁排查 |
| Performance | 性能优化实战 | RocketMQ 批量发送优化 |

STAR 格式模板：

```markdown
## 🚀 案例：标题

**Situation**：背景描述（时间、业务场景、问题症状）
**Task**：要达成的目标（量化指标）
**Action**：
  - 方案分析（为什么选这个方案）
  - 核心代码（关键实现片段）
  - 部署/配置要点
**Result**：
  - 量化效果（QPS、延迟、资源消耗）
  - 踩坑记录（什么坑、怎么发现的、怎么修复的）
  - 🎯 面试加分点（面试官看到会给高分的点）
```

---

## 五、交付计划（四阶段分组推进）

### Phase 1：基础组（5 个栈先交付）

| 编号 | 技术栈 | 关键交付物 |
|------|--------|-----------|
| 01 | 后端开发总纲 | 架构设计、API 设计、分层模式 |
| 02 | Java 核心 | JVM/JUC/集合/IO 教程 + 面试题 |
| 04 | Python 基础 | 语法/异步/项目结构 + 面试题 |
| 11 | Linux | 命令/Shell/网络排查 + 面试题 |
| 13 | 开发工具 | Git/Conda/Jupyter + 面试题 |

### Phase 2：后端核心组（5 个栈）

| 编号 | 技术栈 | 关键交付物 |
|------|--------|-----------|
| 03 | Spring Boot | 从现有扩展，补充面试篇 |
| 06 | MySQL | 从现有扩展，补充面试篇 |
| 07 | Redis | 从现有扩展，补充面试篇 |
| 08 | RocketMQ | 从现有扩展，补充面试篇 |
| 09 | ES | 从现有扩展，补充面试篇 |

### Phase 3：AI 核心组（4 个栈）

| 编号 | 技术栈 | 关键交付物 |
|------|--------|-----------|
| 05 | FastAPI | 新建，路由/依赖/异步/WebSocket |
| 14 | LangChain | 新建，LCEL/Agent/Tool/Memory |
| 15 | RAG | 从现有扩展，补充评估/生产化 |
| 16 | OpenAI | 新建，API/FC/微调/成本优化 |

### Phase 4：基础设施组 + 贯穿项目（2 个栈 + 整合）

| 编号 | 技术栈 | 关键交付物 |
|------|--------|-----------|
| 10 | Docker | 从现有扩展，补充 Compose/K8s |
| 12 | 基础设施 | Nginx/CI-CD/监控/日志 |
| 00 | AI 商城 | 所有栈→商城实战手册 |

---

## 六、搜索策略

每个技术栈交付前，使用 anysearch 并行搜索：

1. **官方文档** — 最新 API、最佳实践、迁移指南
2. **中文社区** — 掘金/CSDN 实战踩坑帖
3. **开源项目** — GitHub 高星项目吸收最佳实践
4. **面试资料** — JavaGuide/小林coding 等面试考点

搜索关键词模板：
- `{tech} best practices 2026`
- `{tech} 面试题 高频考点`
- `{tech} 实战项目 踩坑`
- `{tech} 从入门到精通`