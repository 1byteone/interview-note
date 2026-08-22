# 📋 收录标准与质量评估

> 版本: v1.0 | 日期: 2026-08-22

---

## 1. 核心收录原则

本指南的仓库收录遵循以下原则：

### 1.1 主题相关性

| 条件 | 说明 |
|------|------|
| ✅ 必须 | 仓库名称或描述中包含 `guide` / `tutorial` / `learning` / `roadmap` 等教育属性关键词 |
| ✅ 必须 | 内容与 AI Agent / Coding Agent / Harness / Prompt Engineering / MCP 等专业领域相关 |
| ❌ 排除 | yupi/* 系列仓库（暂不收录） |
| ❌ 排除 | 仅个人博客/配置文件仓库，无教学价值 |

### 1.2 质量标准

| 指标 | 门槛 | 说明 |
|------|------|------|
| ⭐ Stars | ≥ 100 | 社区认可度基本指标 |
| 📝 文档完整性 | ≥ 3 个章节或 ≥ 2,000 字 | 有实质内容而非仅有标题 |
| 🔄 最后更新 | ≤ 12 个月 | 知识时效性，或里程碑级经典项目可放宽 |
| 🏷️ 话题标签 | 推荐 ≥ 3 个 | 便于分类和检索 |

### 1.3 质量评分体系

每仓库按 5 维度评分（1-5 分），总分 ≥ 15 分方可收录：

| 维度 | 权重 | 1 分 | 3 分 | 5 分 |
|------|------|------|------|------|
| **内容深度** | 5 | 仅有标题索引 | 有章节结构和示例 | 从原理到实践层层递进 |
| **社区影响力** | 4 | Stars < 100 | Stars 1,000~10,000 | Stars > 50,000 |
| **时效性** | 3 | 2 年未更新 | 半年内更新 | 持续活跃（月更新） |
| **可操作性** | 4 | 纯理论 | 有代码示例 | 含完整可运行项目 |
| **独特性** | 3 | 与其他指南大量重复 | 有独特视角 | 填补空白或首创 |

### 1.4 本次收录评分

| 生态 | 仓库 | 内容深度 | 社区影响力 | 时效性 | 可操作性 | 独特性 | 总分 | 评级 |
|------|------|---------|-----------|-------|---------|-------|------|------|
| E06 | dair-ai/Prompt-Engineering-Guide | 5 | 5 | 4 | 4 | 5 | 23 | ⭐⭐⭐⭐⭐ |
| E06 | adongwanai/AgentGuide | 5 | 4 | 5 | 5 | 4 | 23 | ⭐⭐⭐⭐⭐ |
| E01 | FlorianBruniaux/claude-code-ultimate-guide | 5 | 4 | 5 | 5 | 5 | 24 | ⭐⭐⭐⭐⭐ |
| E06 | ombharatiya/ai-system-design-guide | 5 | 3 | 5 | 4 | 5 | 22 | ⭐⭐⭐⭐⭐ |
| E01 | zebbern/claude-code-guide | 4 | 4 | 5 | 5 | 3 | 21 | ⭐⭐⭐⭐ |
| E03 | yeasy/harness_engineering_guide | 5 | 1 | 4 | 4 | 5 | 19 | ⭐⭐⭐⭐ |
| E05 | liaokongVFX/MCP-Chinese-Getting-Started-Guide | 4 | 4 | 3 | 5 | 4 | 20 | ⭐⭐⭐⭐ |
| E04 | slowmist/openclaw-security-practice-guide | 4 | 3 | 4 | 4 | 5 | 20 | ⭐⭐⭐⭐ |
| E04 | jwangkun/hermes-agent-guide | 5 | 2 | 4 | 4 | 4 | 19 | ⭐⭐⭐⭐ |
| E02 | freestylefly/CodexGuide | 4 | 3 | 4 | 4 | 3 | 18 | ⭐⭐⭐⭐ |
| E06 | bcefghj/ai-agent-interview-guide | 4 | 3 | 4 | 5 | 4 | 20 | ⭐⭐⭐⭐ |
| E06 | realpython/python-guide | 3 | 5 | 2 | 3 | 3 | 16 | ⭐⭐⭐ |
| E01 | mshadmanrahman/claudecode-guide | 3 | 1 | 4 | 4 | 2 | 14 | ⭐⭐⭐ |
| E06 | didilili/ai-agents-from-zero | 4 | 4 | 5 | 4 | 3 | 20 | ⭐⭐⭐⭐ |
| E03 | flaqai/deepeseek-harness-guide | 3 | 1 | 4 | 4 | 4 | 16 | ⭐⭐⭐ |
| E06 | Marcos-wu/ai-agent-daily-mentor | 2 | 1 | 3 | 3 | 3 | 12 | ⭐⭐ |
| E04 | walkinglabs/awesome-hermes-agent | 3 | 4 | 4 | 3 | 3 | 17 | ⭐⭐⭐⭐ |
| E03 | walkinglabs/awesome-harness-engineering | 3 | 4 | 4 | 3 | 3 | 17 | ⭐⭐⭐⭐ |
| E06 | heilcheng/awesome-agent-skills | 3 | 4 | 4 | 3 | 3 | 17 | ⭐⭐⭐⭐ |
| E06 | libukai/awesome-agent-skills | 3 | 4 | 4 | 3 | 3 | 17 | ⭐⭐⭐⭐ |

---

## 2. 分层标准

### 2.1 必读（⭐⭐⭐⭐⭐）

每日必读，构建核心知识体系：
- **Prompt-Engineering-Guide** — 理论基础
- **AgentGuide** — Agent 求职体系
- **claude-code-ultimate-guide** — Coding Agent 深度
- **ai-system-design-guide** — 系统设计

### 2.2 选读（⭐⭐⭐⭐）

按需阅读，深化特定领域：
- **harness_engineering_guide** — Harness 理论
- **claude-code-guide** — 功能参考
- **MCP-Chinese-Getting-Started-Guide** — MCP 入门
- **openclaw-security-practice-guide** — 安全
- **hermes-agent-guide** — Hermes 框架
- **CodexGuide** — Codex 实践
- **ai-agent-interview-guide** — 面试准备

### 2.3 参考（⭐⭐⭐）

作为补充资源：
- **python-guide** — Python 最佳实践
- **claudecode-guide** — 入门级
- **deepeseek-harness-guide** — DSH 参考
- **ai-agents-from-zero** — 初学者补充

---

## 3. 更新策略

### 3.1 更新频率

| 维度 | 频率 | 方式 |
|------|------|------|
| Stars 数据 | 每月 | 自动刷新 |
| 内容分析 | 每季度 | 检查 README 变更 |
| 新增收录 | 持续 | 随 GitHub Star 动作 |
| 淘汰评估 | 半年 | 检查活跃度和质量 |

### 3.2 淘汰条件

- 2 年以上未更新且无里程碑意义
- 被官方文档或更优资源取代
- 内容严重过时或错误未修正