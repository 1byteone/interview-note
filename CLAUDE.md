# CLAUDE.md — Java & AI 面试笔记 | 知识库

> 本文件是 Claude Code 的项目级上下文指南。每次会话开始时自动加载。
> 请先阅读本文件，再执行任何操作。

---

## 一、项目概况

本项目是一个面向 **Java 后端工程师 + AI 全栈开发者** 的面试笔记与知识库，覆盖 1000+ 面试题、17 个技术栈教程、28 篇生态深度教程、8 个项目深度剖析，以及一套完整的 AI 编程工具实战指南。

### 核心定位

从 Java 后端到 AI Agent 的全栈面试知识体系，帮助开发者系统性准备技术面试，同时掌握 AI 编程工具在企业级项目中的工程化落地能力。

---

## 二、目录结构

```
interview-note/
├── 1-knowledge/          # 📚 面试题库（按技术领域组织）
│   ├── 01-java/          # Java 核心 + Spring + Spring Cloud
│   ├── 02-infrastructure/ # 中间件 + DevOps
│   └── 03-ai/            # AI 技术栈
├── 2-learning/           # 🎯 系统教程（17 技术栈 × 5 层）
│   ├── roadmap/          # 学习路线图
│   ├── stacks/           # 17 个技术栈教程
│   └── projects/         # 项目实战
├── 3-ecosystem/          # 🗺️ 生态索引（AI Agent 六大生态）
│   ├── tutorials/        # 28 篇深度教程
│   ├── categories/       # 生态分类
│   └── repositories/     # 27 个仓库详情
├── 4-interview/          # 💼 面试准备
│   ├── preparation-plan.md  # 3 个月面试计划
│   ├── project-interview-guide.md  # 项目面试说辞手册
│   └── projects/         # 4 个项目的深度面试文档
├── 5-research/           # 🔬 项目分析 + 研究
│   ├── tech-stack-analysis/ # 8 个项目深度剖析
│   └── liyupi/           # 鱼皮分析
├── ai-coding-guide/      # 🤖 AI 编程工具实战指南
│   ├── 01-*.md ~ 12-*.md # 12 章知识体系
│   └── tutorials/        # 17 个实操教程（T01-T17）
├── _assets/              # 🎨 共享资源
├── _scripts/             # 🔧 工具脚本
└── docs/                 # 项目元文档
```

---

## 三、技术栈

| 领域 | 技术 | 定位 |
|------|------|------|
| Java 后端 | Spring Boot 3, Spring Cloud Alibaba, MyBatis-Plus | 微服务架构 |
| 中间件 | Redis/Redisson, RocketMQ, Elasticsearch, Seata, Sentinel | 高并发与分布式 |
| AI 应用 | LangChain, LangGraph, Spring AI Alibaba, FastAPI | RAG/Agent 开发 |
| 数据库 | MySQL 8.0, PostgreSQL, Elasticsearch, ChromaDB, Neo4j | 存储与检索 |
| 工具链 | Docker, Git, GitHub Actions, Maven | CI/CD 与协作 |

---

## 四、文件编写规范

### 4.1 通用规范

- Markdown 语法，UTF-8 编码，LF 换行
- 中英文混排时，中文与英文之间加空格
- 代码块标注语言（java, bash, yaml, json, xml 等）
- 技术术语保留英文（如 Redis、Spring Boot、AGENTS.md）
- 图片使用绝对路径或相对仓库根目录的相对路径

### 4.2 面试题目录规范

每个面试题目录包含：
- `README.md` — 面试题汇总索引（按 Level 1-4 难度分级，包含题目和答案）
- 题目按难度分级：L1=初级 / L2=中级 / L3=高级 / L4=架构师
- 每个答案包含：核心要点 + 展开说明 + 代码示例（可选）

### 4.3 教程目录规范

每个教程按「入门→核心→进阶→项目→面试」5 层组织：
- `01-basics/` — 入门基础
- `02-core/` — 核心概念
- `03-advanced/` — 进阶技巧
- `04-project/` — 项目实战
- `05-interview/` — 面试准备

### 4.4 生态索引规范

- `guide_repos.json` 是单一数据源，所有 Stars 数据从GitHub API 同步
- 仓库详情页在 `repositories/{name}.md`，与 JSON 保持严格一致
- `ecosystem-index.md` 是生态总索引表，自动汇总各生态数据
- SVG 图表在 `assets/` 目录，手动更新 Stars 数据

---

## 五、AI 编程工具使用约定

### 5.1 工具选择

| 任务 | 推荐工具 | 原因 |
|------|---------|------|
| 日常编辑、小范围修改 | Cursor | IDE 原生集成，响应快 |
| 大型架构重构、跨模块分析 | Claude Code | 大上下文 + Subagent 并行 |
| 长时间自动修复循环 | Codex | Full Auto 模式自主执行 |
| Agent 系统研究 | DeepSeek Harness | 插件化架构，可实验 |
| 日常自动化工作流 | Hermes | 持久记忆 + 自进化 Skills |

### 5.2 任务执行流程

```
1. 先理解需求（有问题先问清楚，不假设）
2. 分析项目结构（读 README / CLAUDE.md / 相关文件）
3. 制定计划（复杂任务先输出 Plan）
4. 执行（从最小改动开始，逐步推进）
5. 验证（运行测试 / 检查 diff / 确认结果）
6. 提交（git add + commit + push，附完整 commit message）
```

### 5.3 编码规范

- 这条规则用于编辑本项目中的 `.md` 文件
- 面试题答案必须准确、完整，不确定时标注"待确认"
- 所有链接使用相对路径，不以 `/` 开头
- 新增文件必须更新上级目录的索引文件
- 引用外部资料时必须附来源链接

---

## 六、关键数据

| 维度 | 数据 |
|------|------|
| 面试题 | 1000+ 道（L1-L4 四级难度） |
| 技术栈教程 | 17 个（每栈 5 层体系） |
| 生态深度教程 | 28 篇（覆盖 Claude Code / Codex / MCP / Harness 等） |
| 项目深度剖析 | 8 个项目（38+ 篇系列文章） |
| 收录仓库 | 27 个（总 Stars 186k+） |
| AI 编程实战教程 | 12 章知识体系 + 17 个实操教程 |

---

## 七、常用命令

```bash
# 同步 Stars 数据
python3 _scripts/sync_stars.py

# 预览 Stars 变更
python3 _scripts/sync_stars.py --dry-run

# 数据一致性校验
python3 _scripts/validate.py

# 检查链接有效性
python3 _scripts/check_links.py

# 提交与推送
git add -A && git commit -m "type(scope): description" && git push
```

---

## 八、项目源码路径（面试项目）

| 项目 | 本地路径 | GitHub |
|------|---------|--------|
| 分布式微云商城 | `D:/code/codeClaudeCode/demo-practicalTrainingProject/mall-ai/mall-micro-cloud` | [mall-micro-cloud](https://github.com/1byteone/mall-micro-cloud) |
| 灵犀智能写作 | `D:/code/codeJava/codeYuJavaAi/ai-passage-creator` | [ai-passage-creator](https://github.com/1byteone/ai-passage-creator) |
| 农业知识库问答 | `D:/code/codeByCursor/AI_EXAM/agri-qa-assistant` | [agri-qa-assistant](https://github.com/1byteone/agri-qa-assistant) |
| 智颐养老护理系统 | `D:/code/codeJava/heima-phase4/zznursing` | [zznursing](https://github.com/1byteone/zznursing) |
| 传习教育实习 | `D:/code/实习/传习教育` | [ai-search-rag-internship](https://github.com/1byteone/ai-search-rag-internship) |

---

## 九、注意事项

- 不要修改 `_scripts/` 中的工具脚本，除非明确要求
- 不要删除 `.nojekyll` 文件（GitHub Pages 需要）
- 面试题答案涉及技术判断时，优先引用权威文档而非个人经验
- 本项目是面试知识库，每个答案都应假设读者需要"深入理解"而非"表面了解"
- 代码示例优先使用 Java 和 Python，两者切换时标注语言