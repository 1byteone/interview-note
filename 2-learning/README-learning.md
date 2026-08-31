# 🚀 22 技术栈教程体系 — 企业数字化平台贯穿实战

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

### 🏢 企业数字化平台专项路线

**MES → OA → ERP → 三域整合**：从制造业执行系统到企业经营中枢的完整链路，最终整合成可写进简历的"企业数字化运营平台"项目。

学习顺序：`19-MES` → `20-OA` → `21-ERP` → `22-企业数字化平台`（整合主线）

适合：Java 后端工程师准备"资深/高级"岗位，需要企业级项目经验的求职者。

---

## 📊 22 技术栈总览

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
| 17 | 🐍 Python 后端 | `17-python-backend/` | Python 全栈 · FastAPI · 部署 | ✅ Python |
| 18 | 🛰️ LangSmith | `18-langsmith/` | 可观测 · 评测 · Prompt 管理 | ✅ Python |
| 19 | 🏭 MES 制造执行 | `19-mes/` | 工单 · 排产 · 报工 · 追溯 · ERP 集成 | ✅ Java |
| 20 | 📋 OA 办公自动化 | `20-oa/` | 审批流 · Flowable · RBAC · 数据权限 | ✅ Java |
| 21 | 💰 ERP 企业资源计划 | `21-erp/` | 销售/采购/库存/财务 · BOM · MRP | ✅ Java |
| 22 | 🏢 企业数字化平台 | `22-enterprise-platform/` | 三域整合 · 数据闭环 · AI 增强 | ✅ Java |

---

## 🏪 贯穿项目：企业数字化运营平台

```
┌─────────────────────────────────────────────────────────────────────┐
│          企业数字化运营平台（OA + ERP + MES 三域整合）                  │
├───────────────┬─────────────────┬─────────────────┬────────────────┤
│  OA 审批域     │  ERP 经营域      │  MES 生产域     │  AI 增强        │
│  Flowable     │  进销存/财务     │  工单/排产/质检  │  智能排产/问答  │
│  RBAC/权限    │  BOM/MRP        │  追溯/设备      │  AI BI/预测     │
├───────────────┴─────────────────┴─────────────────┴────────────────┤
│  Spring Boot 3 · MyBatis-Plus · RocketMQ · Redis · MySQL · Vue3   │
│  Flowable 6.8 · Elasticsearch · MinIO · Prometheus · Grafana      │
└─────────────────────────────────────────────────────────────────────┘
```

📖 详见 [企业数字化平台整合主线](stacks/22-enterprise-platform/README.md)

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
├── 17-python-backend/                ← 🐍 Python 全栈
├── 18-langsmith/                     ← 🛰️ 可观测/评测/Prompt
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

## 🗺️ Guide 生态导航（反向入口）

> 学完 16 技术栈后，想探索 AI Agent 领域的前沿生态？

本仓库的 [`guide/`](../3-ecosystem/) 目录是按**技术栈生态**分类的 AI Agent 知识索引，覆盖 27 个精选仓库（186K+ stars）、6 大生态。

| 生态 | 名称 | 与本教程的关联 | 入口 |
|------|------|----------------|------|
| **E01** | 🤖 Claude Code | Coding Agent 实践，你学的 Spring Boot/Python/LangChain 都能用上 | [`categories/01`](../3-ecosystem/categories/01-ecosystem-claude-code.md) |
| **E02** | 🐙 Codex | OpenAI Coding Agent，和 OpenAI API 篇直接相关 | [`categories/02`](../3-ecosystem/categories/02-ecosystem-codex.md) |
| **E03** | ⚙️ DSH/Harness | Agent 运行时框架，LangChain 篇的底层原理延伸 | [`categories/03`](../3-ecosystem/categories/03-ecosystem-dsh-harness.md) |
| **E04** | 🔗 Hermes/OpenClaw | 开源 Agent 框架，多平台接入 + MCP 集成 | [`categories/04`](../3-ecosystem/categories/04-ecosystem-hermes-openclaw.md) |
| **E05** | 🌐 MCP 协议 | 跨平台工具调用，LangChain/Agent 篇的必修协议 | [`categories/05`](../3-ecosystem/categories/05-ecosystem-mcp.md) |
| **E06** | 📚 通识与基础 | Prompt/Agent/系统设计，面试必备 | [`categories/06`](../3-ecosystem/categories/06-ecosystem-general-agent.md) |

**一键跳转**：[guide 总览](../3-ecosystem/README.md) · [生态总索引](../3-ecosystem/ecosystem-index.md) · [生态-教程映射图](../3-ecosystem/guide-learn-mapping.md)

---

> 🎉 祝你学习顺利，面试成功！
