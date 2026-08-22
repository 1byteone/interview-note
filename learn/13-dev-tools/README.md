# 开发工具 — Git · Conda · Jupyter

> 本模块是 16 个技术栈学习体系中的**第十三站**，面向 AI 商城全栈开发场景，系统讲解 Git、Conda、Jupyter 三大开发工具的核心用法与最佳实践。

---

## 为什么需要学习开发工具？

在 AI 智能商城开发中，高效的开发工具链直接影响团队协作效率与个人生产力：

- **Git**：管理代码版本、支撑团队协作、保障代码回溯与发布安全
- **Conda**：隔离 Python 环境、管理 AI 依赖包、避免环境冲突
- **Jupyter**：交互式数据分析、模型原型验证、数据可视化探索

这三个工具构成了 AI 开发者日常工作的"工具箱三角"，熟练掌握它们是从"能写代码"到"能高效交付"的关键一步。

---

## 学习路径（建议顺序）

```
┌──────────────────────────────────────────────────────────────────────┐
│              13 开发工具 · 技术栈总览 (本文档)                          │
└──────────────────────────────────────────────────────────────────────┘
                                  │
          ┌───────────────────────┼───────────────────────────┐
          ▼                       ▼                           ▼
┌─────────────────────┐ ┌─────────────────┐ ┌──────────────────────┐
│  01-git             │ │  02-conda       │ │  03-jupyter          │
│  ├── 01-quick-start │ │  ├── 01-quick-  │ │  ├── 01-quick-start  │
│  ├── 02-advanced    │ │  │   start      │ │  ├── 02-advanced     │
│  └── 03-troubleshoot│ │  └── 02-advanced│ │  └──                 │
└─────────────────────┘ └─────────────────┘ └──────────────────────┘
          │                       │                       │
          └───────────────────────┼───────────────────────┘
                                  ▼
          ┌─────────────────────────────────────────────────┐
          │  04-projects  AI 商城项目实战集成                │
          └─────────────────────────────────────────────────┘
                                  │
                                  ▼
          ┌─────────────────────────────────────────────────┐
          │  05-interview  Git 面试高频题速记                │
          └─────────────────────────────────────────────────┘
```

---

## 前置知识

- **无硬性前置要求**，但建议具备基本的命令行操作能力
- 了解 Python 基础语法（Conda 和 Jupyter 相关）
- 了解基本的编程概念（版本管理、依赖管理等）

---

## 各章节速览

| 章节 | 核心内容 | 学习目标 |
|------|----------|----------|
| 01-git | 从基础操作到团队协作、误操作恢复 | 能独立管理项目版本、参与团队 Git 工作流 |
| 02-conda | 环境隔离、包管理、环境迁移 | 能为每个项目创建独立 Python 环境 |
| 03-jupyter | 交互式编程、数据分析、Magic 命令 | 能用 Jupyter 完成数据探索和模型实验 |
| 04-projects | AI 商城场景中的工具链集成 | 能在真实项目中灵活组合三个工具 |
| 05-interview | Git 高频面试题 | 能从容应对 Git 相关的面试考察 |

---

## 本模块在 AI 商城项目中的角色

以一个典型的 AI 智能商城开发流程为例：

```
# 1. Conda 创建隔离环境
conda create -n mall-ai python=3.11
conda activate mall-ai

# 2. Git 克隆仓库并创建功能分支
git clone https://github.com/xxx/mall-ai.git
git checkout -b feat/ai-search

# 3. 在 Jupyter 中实验 AI 推荐算法
jupyter lab notebooks/ai-recommendation.ipynb

# 4. 将实验代码迁移到生产代码后提交
git add src/ai/
git commit -m "feat: add AI recommendation engine"
git push origin feat/ai-search
```

---

## 目录结构

```
learn/13-dev-tools/
├── README.md                        ← 本文档（总览）
├── 01-git/
│   ├── 01-git-quick-start.md        Git 基础入门
│   ├── 02-git-advanced.md           Git 进阶与协作
│   └── 03-git-troubleshooting.md    误操作恢复与冲突解决
├── 02-conda/
│   ├── 01-conda-quick-start.md      Conda 环境管理入门
│   └── 02-conda-advanced.md         Conda 进阶与生产实践
├── 03-jupyter/
│   ├── 01-jupyter-quick-start.md    Jupyter 基础与数据分析
│   └── 02-jupyter-advanced.md       Jupyter 进阶与生产环境
├── 04-projects/
│   └── mall-integration.md          开发工具在 AI 商城中的集成
├── 05-interview/
│   └── quick-revision.md            Git 面试高频题速记
└── resources.md                     推荐资源
```

---

## 学习建议

- **Git 部分**：建议边学边动手，在本地创建测试仓库练习所有操作
- **Conda 部分**：结合 AI 商城项目实际创建环境，每次实验后 `conda export` 保存环境
- **Jupyter 部分**：用商城真实数据（如 CSV 导出）进行分析练习，产出可视化报告
- **整体时间**：每篇文章约 20-30 分钟，总耗时约 4-6 小时

> 工欲善其事，必先利其器。从 Git 的基础操作开始，打造你的高效开发工具链。

---

## 📖 导航

| ← 上一篇 | 📚 目录 | 下一篇 → |
|----------|---------|----------|
| [← 基础设施](../12-infrastructure/README.md) | [📚 总目录](../README.md) | [LangChain →](../14-langchain/README.md) |

**相关技术栈：**
- [02-Java 核心](../02-java/README.md) — Git 和 IDE 调试工具是 Java 后端开发的日常必备
- [04-Python 基础](../04-python/README.md) — Conda 和 Jupyter 是 Python AI 开发的标配工具链