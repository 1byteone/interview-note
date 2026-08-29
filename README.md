# Java & AI 面试笔记 | 知识库

> **从 Java 后端到 AI Agent 的全栈面试知识体系**
> 1000+ 面试题 · 17 技术栈教程 · 28 篇生态深度教程 · 8 个项目深度剖析

---

## ⚡ 快速决策

> **回答 3 个问题，找到最适合你的入口。**

```
Q1: 你现在需要什么？
    │
    ├── 🎯 刷面试题 ──────────────────────────→ 选 Q2
    │
    ├── 📚 系统学习 ──→ [学习路线图](2-learning/roadmap/)
    │
    ├── 🔍 查具体技术 ──→ [面试题速查表](#面试题速查)（按技术名搜索）
    │
    ├── 🤖 了解 AI Agent ──→ [生态教程](#ai-agent-生态)
    │
    ├── 🔬 看真实项目 ──→ [项目深度剖析](#项目深度剖析)
    │
    └── 💼 面试冲刺 ──→ [3 个月计划](4-interview/preparation-plan.md)

Q2: 你面什么方向？
    │
    ├── ☕ Java 后端 ──→ [Java 核心面试题](1-knowledge/01-java/java-core/)
    │                     [Spring 全家桶](1-knowledge/01-java/spring/)
    │                     [Spring Cloud 微服务](1-knowledge/01-java/spring-cloud/)
    │
    ├── 🗄️ 中间件 ──→ [Redis](1-knowledge/02-infrastructure/middleware/redis/)
    │                   [MySQL](1-knowledge/02-infrastructure/middleware/mysql/)
    │                   [ES / Kafka](1-knowledge/02-infrastructure/middleware/)
    │
    ├── 🐳 DevOps ──→ [Docker / Nginx / CI-CD](1-knowledge/02-infrastructure/devops/)
    │
    └── 🐍 Python 后端 ──→ [Python 全栈教程](2-learning/stacks/17-python-backend/)（24 章 · P0→P3）
    │
    └── 🤖 AI ─────→ [LLM / RAG / Agent](1-knowledge/03-ai/)

Q3: 你的水平？
    │
    ├── 🌱 初级 (L1-L2) ──→ [快速入门教程](2-learning/stacks/01-backend-development/)
    │                         [基础面试题](1-knowledge/01-java/java-core/)
    │
    ├── 🔧 中级 (L2-L3) ──→ [进阶教程](2-learning/stacks/03-spring-boot/)
    │                         [项目剖析](5-research/tech-stack-analysis/mall-ai-search/)
    │
    └── 🏗️ 高级 (L3-L4) ──→ [架构设计](2-learning/stacks/12-infrastructure/)
                               [分布式事务](1-knowledge/01-java/spring-cloud/seata/)
                               [Agent 深度](3-ecosystem/tutorials/e06-general/02-agent-design-patterns.md)
```

---

## 🔍 我要找什么？

> **按你的需求直接跳转，不需要理解目录结构。**

### 面试题速查

| 技术领域 | 面试题入口 | 难度 | 题数 |
|----------|-----------|------|------|
| **Java 核心** | [JVM / JUC / 集合 / IO](1-knowledge/01-java/java-core/) | L1-L4 | 100+ |
| **Spring Boot** | [Boot 核心](1-knowledge/01-java/spring/spring-boot/) | L1-L4 | 30+ |
| **Spring MVC** | [MVC 原理](1-knowledge/01-java/spring/spring-mvc/) | L1-L3 | 20+ |
| **Spring Data** | [数据访问](1-knowledge/01-java/spring/spring-data/) | L1-L3 | 15+ |
| **Nacos** | [服务发现与配置](1-knowledge/01-java/spring-cloud/nacos/) | L2-L4 | 20+ |
| **Gateway** | [网关鉴权](1-knowledge/01-java/spring-cloud/gateway/) | L2-L4 | 15+ |
| **OpenFeign** | [远程调用](1-knowledge/01-java/spring-cloud/openfeign/) | L2-L3 | 10+ |
| **Sentinel** | [流量控制](1-knowledge/01-java/spring-cloud/sentinel/) | L2-L4 | 15+ |
| **Seata** | [分布式事务](1-knowledge/01-java/spring-cloud/seata/) | L3-L4 | 15+ |
| **RocketMQ** | [消息队列](1-knowledge/01-java/spring-cloud/rocketmq/) | L2-L4 | 20+ |
| **Redis** | [缓存 / 分布式锁 / Redisson](1-knowledge/02-infrastructure/middleware/redis/) | L1-L4 | 40+ |
| **MySQL** | [索引 / 事务 / 优化](1-knowledge/02-infrastructure/middleware/mysql/) | L1-L4 | 50+ |
| **Elasticsearch** | [搜索 / 集群 / 高可用](1-knowledge/02-infrastructure/middleware/elasticsearch/) | L1-L4 | 25+ |
| **Kafka** | [消息队列](1-knowledge/02-infrastructure/middleware/kafka/) | L1-L3 | 15+ |
| **Docker** | [容器化部署](1-knowledge/02-infrastructure/devops/docker/) | L1-L3 | 15+ |
| **Nginx** | [反向代理 / 负载均衡](1-knowledge/02-infrastructure/devops/nginx/) | L1-L3 | 10+ |
| **CI/CD** | [流水线](1-knowledge/02-infrastructure/devops/ci-cd/) | L1-L3 | 10+ |
| **Python** | [Python 基础](1-knowledge/03-ai/python/) | L1-L2 | 10+ |
| **LLM** | [大语言模型](1-knowledge/03-ai/llm/) | L2-L4 | 20+ |
| **RAG** | [检索增强生成](1-knowledge/03-ai/rag/) | L2-L4 | 15+ |
| **AI 应用开发专项** | [Word 教材整合教程](2-learning/projects/tutorials/00-ai-application-learning-path/README.md) | 入门→生产 | 15 章 |
| **AI Agent** | [Agent 原理](1-knowledge/03-ai/agent/) | L2-L4 | 20+ |
| **Agentic AI** | [Agentic 系统](1-knowledge/03-ai/agentic/) | L3-L4 | 15+ |
| **LangGraph** | [图编排](1-knowledge/03-ai/langgraph/) | L2-L3 | 10+ |
| **Harness** | [Harness 工程](1-knowledge/03-ai/harness/) | L3-L4 | 10+ |

> 难度说明：L1=初级 / L2=中级 / L3=高级 / L4=架构师

---

### 系统教程

> 每个技术栈按「入门→核心→进阶→项目→面试」五层组织。

| 技术栈 | 教程入口 | 章节数 | 预计时间 |
|--------|---------|--------|---------|
| **后端开发通识** | [01-backend-development/](2-learning/stacks/01-backend-development/) | 5 | 1 周 |
| **Java 核心** | [02-java/](2-learning/stacks/02-java/) | 5 | 2 周 |
| **Spring Boot** | [03-spring-boot/](2-learning/stacks/03-spring-boot/) | 5 | 2 周 |
| **Python** | [04-python/](2-learning/stacks/04-python/) | 5 | 1 周 |
| **FastAPI** | [05-fastapi/](2-learning/stacks/05-fastapi/) | 5 | 1 周 |
| **MySQL** | [06-mysql/](2-learning/stacks/06-mysql/) | 5 | 2 周 |
| **Redis** | [07-redis/](2-learning/stacks/07-redis/) | 5 | 1.5 周 |
| **RocketMQ** | [08-rocketmq/](2-learning/stacks/08-rocketmq/) | 5 | 1 周 |
| **Elasticsearch** | [09-elasticsearch/](2-learning/stacks/09-elasticsearch/) | 5 | 1.5 周 |
| **Docker** | [10-docker/](2-learning/stacks/10-docker/) | 5 | 1 周 |
| **Linux** | [11-linux/](2-learning/stacks/11-linux/) | 5 | 1.5 周 |
| **基础设施** | [12-infrastructure/](2-learning/stacks/12-infrastructure/) | 5 | 1 周 |
| **Dev 工具** | [13-dev-tools/](2-learning/stacks/13-dev-tools/) | 5 | 1 周 |
| **LangChain** | [14-langchain/](2-learning/stacks/14-langchain/) | 5 | 1.5 周 |
| **RAG** | [15-rag/](2-learning/stacks/15-rag/) | 5 | 1 周 |
| **OpenAI API** | [16-openai/](2-learning/stacks/16-openai/) | 5 | 1 周 |
| **Python 后端全栈** 🆕 | [17-python-backend/](2-learning/stacks/17-python-backend/) | 26 | 8-12 周 |

> 📌 **Python 后端全栈教程**：完整的 24 章 + 2 附录体系，从 Python 基础到 AI Agent，覆盖 FastAPI / Pydantic / SQLAlchemy / PostgreSQL / Redis / asyncio / 测试 / Docker / 可观测性 / LLM / RAG / Agent / MCP 全链路。适合目标为 **Python AI 后端工程师** 的学习者。

> 📌 **学习路线图**：不确定从哪开始？看 [roadmap/](2-learning/roadmap/)

---

### 项目深度剖析

> 8 个项目 × 多篇系列文章，含真实代码 + 面试题 + 架构图。

| 项目 | 类型 | 入口 | 系列篇数 |
|------|------|------|---------|
| **mall-ai-search** | AI 智能搜索 | [tech-stack-analysis/mall-ai-search/](5-research/tech-stack-analysis/mall-ai-search/) | 11 篇 |
| **mall-micro-cloud** | 微服务电商 | [tech-stack-analysis/mall-micro-cloud/](5-research/tech-stack-analysis/mall-micro-cloud/) | 11 篇 |
| **text2sql** | Java Text2SQL+RAG | [tech-stack-analysis/text2sql/](5-research/tech-stack-analysis/text2sql/) | 7 篇 |
| **ruoyi-ai** | 企业级 AI 应用框架 | [tech-stack-analysis/ruoyi-ai/](5-research/tech-stack-analysis/ruoyi-ai/) | 16+ 篇 |
| **ai-passage-creator** | AI 文章生成器 | [tech-stack-analysis/ai-passage-creator/](5-research/tech-stack-analysis/ai-passage-creator/) | 12+ 篇 |
| **mewpaw-code** | CLI 编码 Agent | [tech-stack-analysis/mewpaw-code/](5-research/tech-stack-analysis/mewpaw-code/) | 8+ 篇 |
| **zznursing** | 养老物联网平台 | [tech-stack-analysis/zznursing/](5-research/tech-stack-analysis/zznursing/) | 10+ 篇 |
| **mall-integration** | 学习项目（多栈整合） | [projects/ai-mall/](2-learning/projects/ai-mall/) | 5 模块 |

---

### AI Agent 生态

> 6 大生态 + 27 个仓库 + 28 篇深度教程。

| 生态 | 入口 | 教程数 | 核心仓库 |
|------|------|--------|---------|
| **E01 Claude Code** | [e01-claude-code/](3-ecosystem/tutorials/e01-claude-code/) | 6 篇 | claude-code-ultimate-guide (5.8k⭐) |
| **E02 Codex** | [e02-codex/](3-ecosystem/tutorials/e02-codex/) | 4 篇 | CodexGuide (3.2k⭐) |
| **E03 DSH/Harness** | [e03-dsh-harness/](3-ecosystem/tutorials/e03-dsh-harness/) | 3 篇 | harness_engineering_guide |
| **E04 Hermes/OpenClaw** | [e04-hermes-openclaw/](3-ecosystem/tutorials/e04-hermes-openclaw/) | 3 篇 | awesome-hermes-agent (5.4k⭐) |
| **E05 MCP 协议** | [e05-mcp/](3-ecosystem/tutorials/e05-mcp/) | 4 篇 | MCP-Chinese-Getting-Started-Guide (3.6k⭐) |
| **E06 通识与基础** | [e06-general/](3-ecosystem/tutorials/e06-general/) | 6 篇 | Prompt-Engineering-Guide (77.7k⭐) |
| **跨生态对比** | [cross-ecosystem/](3-ecosystem/tutorials/cross-ecosystem/) | 1 篇 | Claude Code vs Codex vs Gemini CLI |

---

### AI 编程工具实战指南 🆕

> 12 章知识体系 + 17 个实操教程，覆盖 Claude Code / Codex / DeepSeek Harness / Hermes / Cursor 全部五大工具。

| 内容 | 入口 | 说明 |
|------|------|------|
| **知识体系（12 章）** | [ai-coding-guide/](ai-coding-guide/) | 认知升级 / Claude Code / Codex / DSH / Hermes / Cursor / Context Engineering / MCP Skills / 企业案例 / Java 实战 / 安全治理 / 学习路线 |
| **实操教程（17 个）** | [ai-coding-guide/tutorials/](ai-coding-guide/tutorials/) | 从零搭建 / CLAUDE.md 配置 / 遗留重构 / Full Auto / AGENTS.md Review / Workspace Agent / DSH 四模式 / 插件开发 / Hermes 记忆 Skill / 定时工作流 / Cursor Rules / 多文件重构 / MCP Server / 五工具协作 / 项目工程化 / 多仓库协作 / 生产排查 |

---

### 面试准备

| 内容 | 入口 |
|------|------|
| **3 个月系统面试计划** | [4-interview/preparation-plan.md](4-interview/preparation-plan.md) |
| **面试题生成器** | [4-interview/tools/question-generator/](4-interview/tools/question-generator/) |
| **鱼皮系列深度分析** | [5-research/liyupi/](5-research/liyupi/) |
| **面试工具集** | [4-interview/tools/](4-interview/tools/) |

### 简历定制面试材料

| 简历 | 详解版（含面试官意图+深挖方向） | 背诵版（考前速查） |
|------|-------------------------------|-------------------|
| 模板覆盖版 | [技术经理100问 详解版](AI应用开发岗_技术经理面试100问_QA_详解版.md) | [技术经理100问 背诵版](AI应用开发岗_技术经理面试100问_QA.md) |
| 纯 AI 简历（STAR 优化版） | [纯AI简历100问 详解版](AI应用开发岗_纯AI简历_面试100问_QA详解版.md) | [纯AI简历100问 背诵版](AI应用开发岗_纯AI简历_面试100问_背诵版.md) |

---

## 🗂️ 目录结构（供参考）

> 上面的内容索引已覆盖全部主要入口。如果你需要理解内部组织逻辑，参考下图。

```
interview-note/
├── 1-knowledge/          # 📚 面试题库（按技术领域组织）
│   ├── 01-java/          # Java 核心 + Spring + Spring Cloud
│   ├── 02-infrastructure/ # 中间件 + DevOps
│   └── 03-ai/            # AI 技术栈
├── 2-learning/           # 🎯 系统教程（17 技术栈 × 5 层）
│   ├── roadmap/          # 学习路线图
│   └── stacks/           # 17 个技术栈教程
│       └── 17-python-backend/  # 🐍 Python 后端全栈（24 章 + 2 附录）🆕
├── 3-ecosystem/          # 🗺️ 生态索引（AI Agent 六大生态）
│   ├── tutorials/        # 28 篇深度教程
│   ├── categories/       # 生态分类
│   └── repositories/     # 27 个仓库详情
├── 4-interview/          # 💼 面试准备（计划 + 工具）
├── 5-research/           # 🔬 项目分析 + 研究
│   ├── tech-stack-analysis/ # 8 个项目深度剖析
│   ├── projects/         # 项目实战
│   └── liyupi/           # 鱼皮分析
├── ai-coding-guide/      # 🤖 AI 编程工具实战指南（12 章 + 17 教程）🆕
│   ├── 01-*.md ~ 12-*.md # 知识体系章节
│   └── tutorials/        # 实操教程（T01-T17）
├── _assets/              # 🎨 共享资源
├── _scripts/             # 🔧 工具脚本
└── docs/                 # 项目元文档
```

---

## 📊 规模

| 维度 | 数据 |
|------|------|
| 面试题 | 1000+ 道（L1-L4 四级难度） |
| 技术栈教程 | 17 个（每栈 5 层：入门→核心→进阶→项目→面试） |
| 生态深度教程 | 28 篇（覆盖 Claude Code / Codex / MCP / Harness 等） |
| 项目深度剖析 | 8 个项目（38+ 篇系列文章） |
| 收录仓库 | 27 个（总 Stars 186k+） |

---

## 🔧 维护

- 面试题组织在 `1-knowledge/` 各目录的 `README.md` 中，按 Level 1-4 难度分级
- 教程位于 `2-learning/stacks/`，每个技术栈有独立的目录结构
- 生态数据同步：运行 `_scripts/sync_stars.py`
- 链接校验：运行 `_scripts/check_links.py`