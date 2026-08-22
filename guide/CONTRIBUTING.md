# 🤝 Guide 仓库收录贡献指南

> 欢迎推荐有价值的 AI Agent 领域 guide 仓库！  
> 本文档规范收录标准、提交流程和质量要求。

---

## 1. 收录标准（Checklist）

### 必须满足

| # | 条件 | 说明 |
|---|------|------|
| 1 | 仓库名含 `guide`/`tutorial`/`roadmap`/`learning` 教育属性 | 或内容实质为教程/指南（不是个人配置文件） |
| 2 | 主题与 AI Agent / Coding Agent / Harness / MCP / Prompt Engineering 相关 | 与本仓库技术栈匹配 |
| 3 | Stars ≥ 100（教程类可放宽至 ≥ 50） | 社区认可度基本门槛 |
| 4 | 最后更新 ≤ 12 个月 | 知识时效性；里程碑级经典项目可放宽 |
| 5 | 有 README 且 ≥ 2,000 字或 ≥ 3 个章节 | 内容有实质深度 |
| 6 | 非 yupi/* 的 Fork 项目（暂排除） | 避免收录同源重复内容 |

### 强烈推荐

| # | 条件 | 原因 |
|---|------|------|
| 7 | 有中英双语或中文版本 | 中文读者友好 |
| 8 | 有可运行的代码示例或实战项目 | 动手价值高 |
| 9 | 被社区广泛引用或提及 | 生态影响力 |

---

## 2. 生态归属判定

提交时需指定仓库归属的生态。按以下优先级判定：

| 优先级 | 规则 | 生态 |
|--------|------|------|
| P1 | 仓库主题为 Claude Code / Anthropic 模型 | E01 Claude Code |
| P2 | 仓库主题为 OpenAI Codex | E02 Codex |
| P3 | 仓库主题为 DeepSeek Harness / DSH / Cordis | E03 DSH/Harness |
| P4 | 仓库主题为 Hermes Agent / OpenClaw | E04 Hermes/OpenClaw |
| P5 | 仓库主题为 MCP 协议（Model Context Protocol） | E05 MCP |
| P6 | 跨生态通识（Prompt/面试/系统设计/Python 工程） | E06 通识与基础 |

如果一个仓库同时涉及多个生态，在交叉引用表中标记，但**归入主生态**。

---

## 3. 提交流程

### 方式一：PR（推荐）

1. Fork 仓库
2. 运行收录脚本生成详情页：
   ```bash
   python3 scripts/add_repo.py owner/repo --eco=E06
   ```
3. 编辑 `guide/repositories/{owner}_{repo}.md`，完善「内容分析」和「阅读建议」
4. 手动更新对应分类文件 `guide/categories/0X-ecosystem-*.md`
5. 提交 PR，包含以下变更：
   - `guide/guide_repos.json`（+1 仓库）
   - `guide/repositories/{owner}_{repo}.md`（新详情页）
   - `guide/categories/0X-ecosystem-*.md`（新增分类条目）
   - 如涉及新生态，需新建 `guide/categories/0X-ecosystem-*.md`

### 方式二：Issue

在 GitHub Issues 中提交，格式：

```
标题: [Guide 收录] owner/repo

仓库链接: https://github.com/owner/repo
推荐生态: E06
推荐理由: 一句话说明
```

维护者会在 48 小时内处理。

---

## 4. 详情页模板

`guide/repositories/{owner}_{repo}.md` 必须包含以下结构：

```markdown
# owner/repo

> ⭐ {Stars} | 🗣 {Language} | [GitHub](link) | 收录: YYYY-MM-DD

---

## Metadata

| 字段 | 值 |
|------|-----|
| Stars | |
| 语言 | |
| Topics | |
| 生态 | E0X · {生态名} |

## 内容分析

### 核心定位
（一句话说明仓库的核心价值）

### 内容覆盖
（列出主要章节/主题）

### 独特价值
（这个仓库相比同类的独特贡献）

## 阅读建议

- **适合人群**:
- **前置要求**:
- **预计耗时**:
- **配合阅读**:（其他生态关联仓库）

---

## 生态交叉引用

- **主生态**: E0X · {生态名}
- **交叉引用**:（关联其他生态的仓库链接）

> 📖 完整矩阵见 [data/cross-reference.md](../data/cross-reference.md)
```

---

## 5. 质量评分标准

收录后由维护者按 5 维度评分（1-5），总分 ≥ 15 分方可正式收录：

| 维度 | 权重 | 1 分 | 3 分 | 5 分 |
|------|------|------|------|------|
| 内容深度 | 5 | 仅有标题索引 | 有章节结构和示例 | 从原理到实践层层递进 |
| 社区影响力 | 4 | Stars < 100 | Stars 1,000~10,000 | Stars > 50,000 |
| 时效性 | 3 | 2 年未更新 | 半年内更新 | 持续活跃（月更新） |
| 可操作性 | 4 | 纯理论 | 有代码示例 | 含完整可运行项目 |
| 独特性 | 3 | 与其他指南大量重复 | 有独特视角 | 填补空白或首创 |

---

## 6. 脚本工具

| 脚本 | 用途 | 命令 |
|------|------|------|
| `scripts/add_repo.py` | 30 秒收录新仓库 | `python3 scripts/add_repo.py owner/repo` |
| `scripts/sync_stars.py` | 同步全部仓库 Stars | `python3 scripts/sync_stars.py` |
| `scripts/validate.py` | 数据一致性校验 | `python3 scripts/validate.py` |