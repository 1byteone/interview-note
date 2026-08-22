# 🚀 16 技术栈教程体系 — AI 智能商城贯穿实战

> 从入门到面试，项目驱动，STAR 法则，干中学

## 🎯 三条学习路径

### 👶 零基础入门路线（8-10 周）

```
后端开发 → Java → Python → Linux → Git/Conda/Jupyter → Spring Boot → MySQL → Redis → Docker → FastAPI → LangChain → RAG → OpenAI
```

适合：编程新手、转行开发者

### 🎯 面试突击路线（2-3 周）

```
Java 面试 → Spring Boot → MySQL → Redis → RocketMQ → ES → 系统设计 → AI 面试
```

适合：有基础、急需面试的同学（聚焦 05-interview/ 目录）

### 🚀 全栈进阶路线（3-4 个月）

```
Phase 1 基础组 → Phase 2 后端核心 → Phase 3 AI 核心 → Phase 4 基础设施 → 全栈实战
```

适合：想系统学习全栈的开发者

---

## 📊 16 技术栈总览

| # | 技术栈 | 目录 | 核心内容 | 示例 |
|---|--------|------|---------|------|
| 01 | 🏗️ 后端开发总纲 | `01-backend-development/` | 架构设计 · API 设计 · 分层 · DDD | ✅ |
| 02 | ☕ Java 核心 | `02-java/` | JVM · JUC · 集合 · IO | ✅ Maven |
| 03 | 🌱 Spring Boot | `03-spring-boot/` | 自动配置 · Actuator · GraalVM | ✅ Spring Boot |
| 04 | 🐍 Python 基础 | `04-python/` | 语法 · 异步 · 类型 · 测试 | ✅ Python |
| 05 | ⚡ FastAPI | `05-fastapi/` | DI · Pydantic · WebSocket | ✅ FastAPI |
| 06 | 🗄️ MySQL | `06-mysql/` | 索引 · 事务 · MVCC · 分库分表 | ✅ SQL |
| 07 | ⚡ Redis | `07-redis/` | 缓存 · 分布式锁 · 集群 | ✅ Lua |
| 08 | 📨 RocketMQ | `08-rocketmq/` | 事务消息 · 幂等 · 削峰 | ✅ Java |
| 09 | 🔍 Elasticsearch | `09-elasticsearch/` | 倒排索引 · DSL · 集群 | ✅ curl |
| 10 | 🐳 Docker | `10-docker/` | 镜像 · Compose · K8s | ✅ Dockerfile |
| 11 | 🐧 Linux | `11-linux/` | 命令 · Shell · 网络排查 | ✅ Shell |
| 12 | 🏛️ 基础设施 | `12-infrastructure/` | Nginx · Nacos · Gateway · Sentinel | ✅ YML |
| 13 | 🛠️ 开发工具 | `13-dev-tools/` | Git · Conda · Jupyter | - |
| 14 | 🔗 LangChain | `14-langchain/` | LCEL · Agent · Tool · Memory | ✅ Python |
| 15 | 📚 RAG | `15-rag/` | 混合检索 · Graph RAG · 评估 | ✅ Python |
| 16 | 🤖 OpenAI | `16-openai/` | API · FC · 微调 · Batch | ✅ Python |

---

## 🏪 贯穿项目：AI 智能商城

```
┌─────────────────────────────────────────────────────────┐
│                    AI 智能商城                             │
├───────────────┬─────────────────┬───────────────────────┤
│  Java 后端     │  Python AI 服务  │  基础设施 & DevOps     │
│  Spring Boot   │  FastAPI        │  Docker + Linux       │
├───────────────┼─────────────────┼───────────────────────┤
│ 用户/商品/订单  │  AI搜索/客服     │  Nginx/Git/CI/CD      │
│ 支付/库存      │  RAG/推荐        │  监控/日志            │
├───────────────┴─────────────────┴───────────────────────┤
│  Redis · RocketMQ · MySQL · Elasticsearch · Nacos       │
└─────────────────────────────────────────────────────────┘
```

📖 详见 [AI 智能商城贯穿手册](projects/ai-mall/README.md)

---

## 📁 目录结构

```
learn/
├── README.md                         ← 你在这里
├── 00-ROADMAP/                       ← 📋 总路线图
├── 01-backend-development/           ← 🏗️ 后端架构
├── 02-java/                          ← ☕ JVM/JUC/集合/IO
├── 03-spring-boot/                   ← 🌱 自动配置/Actuator
├── 04-python/                        ← 🐍 异步/类型/测试
├── 05-fastapi/                       ← ⚡ DI/Pydantic/异步
├── 06-mysql/                         ← 🗄️ 索引/事务/MVCC
├── 07-redis/                         ← ⚡ 缓存/锁/集群
├── 08-rocketmq/                      ← 📨 事务消息/幂等
├── 09-elasticsearch/                 ← 🔍 倒排索引/DSL
├── 10-docker/                        ← 🐳 镜像/Compose/K8s
├── 11-linux/                         ← 🐧 命令/Shell/网络
├── 12-infrastructure/                ← 🏛️ Nginx/监控/CI-CD
├── 13-dev-tools/                     ← 🛠️ Git/Conda/Jupyter
├── 14-langchain/                     ← 🔗 Agent/LangGraph
├── 15-rag/                           ← 📚 混合检索/评估
├── 16-openai/                        ← 🤖 FC/微调/Batch
└── projects/ai-mall/                 ← 🏪 贯穿项目
```

---

## 🔧 如何使用

### 每个技术栈的目录结构

```
03-spring-boot/
├── README.md              ← 技术栈总览 + 学习路径图
├── cheat-sheet.md         ← 📋 速查卡（面试前30分钟扫一眼）
├── 01-basics/             ← 👶 入门篇
│   ├── 01-quick-start.md
│   └── examples/          ← 🏃 可运行代码
├── 02-core/               ← 核心进阶
├── 03-advanced/           ← 🎯 高级篇
├── 04-projects/           ← 项目实战
│   ├── mall-integration.md ← 与 AI 商城集成
│   └── mini-blog/         ← 独立小项目
├── 05-interview/          ← 🎯 面试篇
│   ├── quick-revision.md  ← 速记版
│   ├── deep-dive.md       ← 深挖题
│   ├── scenario.md        ← 场景题
│   └── coding.md          ← 代码题
└── resources.md           ← 推荐资源
```

### 读者类型推荐路径

| 你是谁 | 推荐路径 | 每天投入 |
|--------|---------|---------|
| 编程新手 | `01-backend-dev → 02-java → 04-python → 03-spring-boot` | 2-3 小时 |
| Java 转 AI | `04-python → 05-fastapi → 14-langchain → 15-rag → 16-openai` | 3-4 小时 |
| 面试冲刺 | 各栈 `05-interview/` + `cheat-sheet.md` | 全天 |
| 全栈进阶 | 按编号顺序 01→16 + projects/ai-mall | 持续投入 |

---

> 🎉 祝你学习顺利，面试成功！
