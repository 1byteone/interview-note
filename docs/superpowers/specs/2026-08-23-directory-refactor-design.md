# interview-note 目录重构设计文档

> 版本: v1.0 | 日期: 2026-08-23
> 状态: 已批准，执行中

---

## 1. 背景与问题诊断

### 1.1 当前状态

`interview-note` 仓库是一个 Java 后端 + AI Agent 面试与学习知识库，当前规模：

| 指标 | 数量 |
|------|------|
| 文件总数 | 994 |
| 目录总数 | 619 |
| Markdown 文件 | 641 |
| 顶级目录 | 14 |
| 跨顶级目录相对链接 | 999 |

### 1.2 核心问题

| # | 问题 | 影响 |
|---|------|------|
| 1 | **顶级目录过多且无分组** | `ai/`, `devops/`, `spring/`, `spring-cloud/`, `middleware/`, `java/`, `liyupi/`, `learn/`, `guide/`, `projects/`, `interview-tools/`, `docs/`, `examples/`, `scripts/` 平铺，新人难以快速定位 |
| 2 | **`liyupi/` 扁平垃圾场** | 16 个文件平铺，内容跨度大（面试题/项目分析/趋势分析/知识图谱），无子目录分层 |
| 3 | **`learn/` 与 `guide/` 边界模糊** | `learn/` 526 文件（技术栈教学），`guide/` 75 文件（生态索引），两者都是教程但定位不同，缺少清晰的分层 |
| 4 | **Spring 生态分散** | `spring/` 与 `spring-cloud/` 本质同源，却占两个顶级目录 |
| 5 | **`interview-tools/` 过于单薄** | 4 个文件占一个顶级目录 |
| 6 | **`docs/` 名不副实** | 仅 2 个根级文件 + `tech-stack-analysis/`（实际是项目分析，与 docs 定位不符）|
| 7 | **`examples/` 名存实亡** | 4 个文件仅一个样例项目 |
| 8 | **根级文件散落** | `interview-preparation-plan.md` 散在根目录 |

### 1.3 跨目录链接影响评估

扫描全仓库 641 个 MD 文件的相对链接：

- 相对链接总数：1000
- 跨顶级目录链接：999（几乎全部）
- 当前失效链接：67 个（历史遗留）

**结论**：几乎所有 Markdown 都有跨目录引用，因此重构必须同步修复链接，不能仅靠 `git mv`。

---

## 2. 设计目标

```
从「按技术栈收集」的扁平结构
    ↓
到「按内容属性分层」的层级知识体系
```

### 2.1 设计原则

1. **按内容属性分层，而非按技术栈**：知识体系 / 学习路径 / 生态索引 / 面试 / 研究
2. **数字前缀排序归组**：`1-` 到 `5-` 表示核心内容，`_` 前缀表示辅助资源
3. **合并同源目录**：Java 生态（`java/` + `spring/` + `spring-cloud/`）合并为一个
4. **保留原有子结构**：不重写 `learn/` 16 个技术栈、不改动 `guide/tutorials/` 28 篇教程
5. **git mv 保留历史**：所有移动使用 `git mv`，保留文件历史
6. **链接同步修复**：移动后用脚本扫描并修复所有失效的相对链接

### 2.2 非目标

- 不修改任何文件的内容语义（仅移动位置 + 修复链接）
- 不合并或重写 `learn/` 的技术栈结构
- 不修改 `guide/tutorials/` 的 28 篇教程内容
- 不改变 `examples/` 的示例代码

---

## 3. 目标目录结构

```
interview-note/                        # 仓库根
├── README.md                           # 仓库总览（重写，精简）
├── 1-knowledge/                        # 📚 知识体系（核心知识内容）
│   ├── README.md                       # 知识体系索引
│   ├── 01-java/                        # Java 生态（合并 java/ + spring/ + spring-cloud/）
│   │   ├── core/ juc/ jvm/ collections/ io/    # 原 java/
│   │   ├── spring-boot/ spring-mvc/ spring-data/  # 原 spring/
│   │   └── nacos/ gateway/ openfeign/ sentinel/ seata/ rocketmq/  # 原 spring-cloud/
│   ├── 02-infrastructure/              # 基础设施（合并 devops/ + middleware/）
│   │   ├── docker/ nginx/ ci-cd/ nat-traversal/  # 原 devops/
│   │   └── mysql/ redis/ kafka/ elasticsearch/ kibana/  # 原 middleware/
│   └── 03-ai/                          # AI 技术（原 ai/）
│       ├── agent/ agentic/ harness/ langgraph/ llm/ python/ rag/
│
├── 2-learning/                         # 🎯 学习路径（系统化学习材料）
│   ├── README.md
│   ├── roadmap/                        # 原 learn/00-ROADMAP
│   ├── stacks/                         # 原 learn/ 其余技术栈教程
│   ├── flashcards/                     # 原 learn/flashcards
│   └── projects/                       # 原 learn/projects
│
├── 3-ecosystem/                        # 🗺️ 生态索引（原 guide/ 完整保留）
│   ├── README.md
│   ├── categories/
│   ├── repositories/
│   ├── tutorials/                      # 28 篇深度教程（保留原样）
│   ├── data/
│   └── assets/
│
├── 4-interview/                       # 💼 面试准备
│   ├── README.md
│   ├── preparation-plan.md             # 原根目录 interview-preparation-plan.md
│   ├── tools/                          # 原 interview-tools/
│   └── questions/                      # 面试题库索引（指向各技术栈面试题）
│
├── 5-research/                         # 🔬 研究与分析
│   ├── README.md
│   ├── liyupi/                         # 原根级 liyupi/ 16 个文件
│   └── tech-stack-analysis/            # 原 docs/tech-stack-analysis（项目深度分析）
│
├── _assets/                            # 🎨 共享资源
│   ├── examples/                       # 原 examples/
│   └── diagrams/                       # 共享图表
│
├── _scripts/                           # 🔧 工具脚本（原 scripts/）
│   ├── add_repo.py
│   ├── sync_stars.py
│   ├── validate.py
│   ├── svg-to-png.py
│   └── README.md
│
├── docs/                               # 项目元文档（仅设计文档、规范）
│   ├── superpowers/specs/              # 本设计文档所在
│   └── 16-tech-stack-tutorials-design.md
│
└── .github/                            # GitHub 配置（保留原位）
```

---

## 4. 迁移映射表

| 原路径 | 新路径 | 说明 |
|--------|--------|------|
| `java/` | `1-knowledge/01-java/java-core/` | Java 基础（core/juc/jvm/collections/io） |
| `spring/` | `1-knowledge/01-java/spring/` | Spring 框架 |
| `spring-cloud/` | `1-knowledge/01-java/spring-cloud/` | Spring Cloud |
| `devops/` | `1-knowledge/02-infrastructure/devops/` | DevOps |
| `middleware/` | `1-knowledge/02-infrastructure/middleware/` | 中间件 |
| `ai/` | `1-knowledge/03-ai/` | AI 技术 |
| `learn/` | `2-learning/` | 学习路径 |
| `learn/00-ROADMAP` | `2-learning/roadmap/` | 路线图 |
| `learn/0X-*` | `2-learning/stacks/0X-*` | 技术栈 |
| `learn/flashcards` | `2-learning/flashcards/` | 闪卡 |
| `learn/projects` | `2-learning/projects/` | 学习项目 |
| `guide/` | `3-ecosystem/` | 生态索引 |
| `interview-preparation-plan.md` | `4-interview/preparation-plan.md` | 面试计划 |
| `interview-tools/` | `4-interview/tools/` | 面试工具 |
| `liyupi/` | `5-research/liyupi/` | 鱼皮分析 |
| `docs/tech-stack-analysis/` | `5-research/tech-stack-analysis/` | 技术栈分析 |
| `examples/` | `_assets/examples/` | 示例 |
| `scripts/` | `_scripts/` | 脚本 |

---

## 5. 风险与回滚

### 5.1 风险

| 风险 | 概率 | 影响 | 缓解 |
|------|------|------|------|
| 链接修复遗漏 | 中 | 中 | 脚本扫描 + 人工校验 |
| 移动后内容引用错乱 | 低 | 中 | git mv 保留历史，可 revert |
| git history 丢失 | 低 | 低 | 使用 `git mv` 而非普通 mv |

### 5.2 回滚方案

重构作为一个独立的 git commit。若发现问题：

```bash
git revert <commit-hash>
```

或在移动前创建备份分支：

```bash
git branch backup-before-refactor
```

---

## 6. 执行步骤

1. 创建新顶级目录骨架
2. 用 `git mv` 迁移目录（保留历史）
3. 用脚本扫描所有失效相对链接
4. 批量修复链接
5. 创建新顶级目录的 README 索引
6. 重写根 README
7. 校验链接 + git status 确认 + 提交

---

## 7. 验收标准

- [ ] 顶级目录从 14 个减少到 8 个（5 个数字目录 + `_assets` + `_scripts` + `docs`）
- [ ] 所有相对链接有效（脚本校验，排除代码块后 0 断链）
- [ ] `liyupi/` 不再是扁平结构（移入 `5-research/`）
- [ ] 每个新顶级目录有 README 索引
- [ ] 根 README 反映新结构
- [ ] git commit 清晰记录重构
