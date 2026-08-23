# Java & AI 面试笔记 | 知识库

> **从 Java 后端到 AI Agent 的全栈面试知识体系**
> 三级架构：知识体系 → 学习路径 → 生态索引 | 28 篇深度教程 | 1000+ 面试题

---

## 📁 目录总览

```
interview-note/
├── 1-knowledge/          # 📚 知识体系 — 核心面试题与知识点
│   ├── 01-java/          # Java 生态：core / JVM / JUC / collections / IO / Spring / Spring Cloud
│   ├── 02-infrastructure/ # 基础设施：DevOps (Docker/Nginx/CI-CD) + 中间件 (Redis/MySQL/ES/Kafka)
│   └── 03-ai/            # AI 技术：LLM / RAG / Agent / LangGraph / Harness / Python
│
├── 2-learning/           # 🎯 学习路径 — 16 技术栈系统化教程
│   ├── roadmap/          # 学习路线图（3 条路径 + 双体系关联）
│   ├── stacks/           # 16 个技术栈（01-Java → 16-OpenAI），每栈按「入门→核心→进阶→项目→面试」组织
│   ├── flashcards/       # 学习闪卡
│   └── projects/         # 学习项目
│
├── 3-ecosystem/          # 🗺️ 生态索引 — AI Agent 六大生态收录指南
│   ├── categories/       # 六大生态分类（E01 Claude Code → E06 通识）
│   ├── repositories/     # 27 个仓库详情
│   ├── tutorials/        # 28 篇深度教程（从安装到生产部署）
│   └── data/             # 全景图 / 学习路线 / 技术雷达 / 交叉引用矩阵
│
├── 4-interview/          # 💼 面试准备 — 策略与工具
│   ├── preparation-plan.md  # 3 个月系统面试计划
│   └── tools/            # 面试题生成器 + 模拟面试
│
├── 5-research/           # 🔬 研究与分析 — 项目深度剖析
│   ├── tech-stack-analysis/  # 8 个项目深度剖析（38 篇系列）
│   ├── projects/         # 项目实战（微服务商城 / AI 搜索 / 养老 IoT）
│   └── liyupi/           # 鱼皮系列深度分析
│
├── _assets/              # 🎨 共享资源
│   └── examples/         # 示例项目（sample-java-project）
│
├── _scripts/             # 🔧 工具脚本
│   ├── add_repo.py       # 收录仓库
│   ├── fix_paths.py      # 路径修复
│   └── check_links.py    # 链接校验
│
├── docs/                 # 项目元文档
│   └── superpowers/      # 设计文档 / 规范
│
└── .github/              # GitHub 配置
```

---

## 🎯 核心特性

| 维度 | 内容 |
|------|------|
| **知识体系** | Java 核心 → Spring 生态 → 微服务 → 中间件 → DevOps → AI |
| **学习路径** | 16 技术栈，每栈 5 层（入门→核心→进阶→项目→面试） |
| **生态索引** | 6 大 AI Agent 生态，27 个仓库，28 篇深度教程 |
| **面试准备** | 1000+ 面试题，3 个月系统计划，AI 面试题生成器 |
| **项目实战** | 8 个项目深度剖析（38 篇系列），微服务/电商/AI 搜索/IoT |
| **研究分析** | 第三方仓库深度分析，技术趋势追踪 |

---

## 🛤️ 推荐入口

| 你的角色 | 从哪开始 |
|----------|----------|
| 👶 初学者 | [`2-learning/roadmap/`](2-learning/roadmap/) → 选择学习路线 |
| 💻 后端工程师 | [`1-knowledge/01-java/`](1-knowledge/01-java/) → Java 核心知识 |
| 🤖 AI 开发者 | [`1-knowledge/03-ai/`](1-knowledge/03-ai/) → AI 技术栈 |
| 🗺️ 生态探索 | [`3-ecosystem/`](3-ecosystem/) → 28 篇深度教程 |
| 💼 面试冲刺 | [`4-interview/preparation-plan.md`](4-interview/preparation-plan.md) |
| 🔬 项目研究 | [`5-research/tech-stack-analysis/`](5-research/tech-stack-analysis/) |

---

## 📊 规模统计

| 目录 | 文件数 | 说明 |
|------|--------|------|
| `1-knowledge/` | 38 | Java 核心 + Spring + 基础设施 + AI 面试题 |
| `2-learning/` | 528 | 16 技术栈系统教程 + 学习路线 |
| `3-ecosystem/` | 75 | 生态索引 + 28 篇深度教程 |
| `4-interview/` | 5 | 面试计划 + 工具 |
| `5-research/` | 281 | 项目分析 + 研究 |
| `_assets/` | 4 | 示例代码 |
| `_scripts/` | 7 | 工具脚本 |
| **总计** | **~940** | |

---

## 🔧 维护

- 面试题以 `README.md` 形式组织在各技术栈目录下，按难度分级（Level 1-4）
- 生态索引数据存储在 `3-ecosystem/guide_repos.json`，运行 `_scripts/sync_stars.py` 同步 Stars
- 目录重构详见 [`docs/superpowers/specs/2026-08-23-directory-refactor-design.md`](docs/superpowers/specs/2026-08-23-directory-refactor-design.md)