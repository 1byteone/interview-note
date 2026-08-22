# 📋 面试抽认卡系统 — 16 栈 × 4 维度

> 把 16 个技术栈的 `05-interview/` 内容，提炼成 250+ 张抽认卡（Flashcard），随时随地用碎片时间刷面试题。

---

## 🎯 这是什么

一套基于 **Markdown Q&A 对** 的面试抽认卡体系，覆盖本仓库全部 16 个技术栈：

| # | 技术栈 | 文件 | 卡片数 |
|---|--------|------|--------|
| 01 | 后端开发 | [01-backend-development.md](01-backend-development.md) | 15 |
| 02 | Java | [02-java.md](02-java.md) | 20 |
| 03 | Spring Boot | [03-spring-boot.md](03-spring-boot.md) | 18 |
| 04 | Python | [04-python.md](04-python.md) | 15 |
| 05 | FastAPI | [05-fastapi.md](05-fastapi.md) | 15 |
| 06 | MySQL | [06-mysql.md](06-mysql.md) | 20 |
| 07 | Redis | [07-redis.md](07-redis.md) | 18 |
| 08 | RocketMQ | [08-rocketmq.md](08-rocketmq.md) | 15 |
| 09 | Elasticsearch | [09-elasticsearch.md](09-elasticsearch.md) | 15 |
| 10 | Docker | [10-docker.md](10-docker.md) | 15 |
| 11 | Linux | [11-linux.md](11-linux.md) | 15 |
| 12 | 基础设施 | [12-infrastructure.md](12-infrastructure.md) | 18 |
| 13 | 开发工具 | [13-dev-tools.md](13-dev-tools.md) | 12 |
| 14 | LangChain | [14-langchain.md](14-langchain.md) | 18 |
| 15 | RAG | [15-rag.md](15-rag.md) | 18 |
| 16 | OpenAI | [16-openai.md](16-openai.md) | 15 |

每一张卡片都从对应技术栈的 `05-interview/`（速记 / 深挖 / 场景 / 代码 四个文件）中提炼而来，答案**面向面试官**：先给结论、再给要点、可口头转述。

---

## 🚀 怎么用

### 方式一：手机/电脑直接刷（零成本）

- 按 [study-plan.md](study-plan.md) 的 **7 天计划** 每天刷一组
- 看 **Q** → 心里默答 → 展开 **A** 对照
- 卡片短小（A 约 2-3 句），通勤、排队时都能刷

### 方式二：导入 Anki（间隔重复算法）

1. 下载 [Anki](https://apps.ankiweb.net/)
2. 创建卡片类型：正面字段 `Question`，背面字段 `Answer`（按需加 `Tag`）
3. 选择 **导入 → [anki-import.txt](anki-import.txt)**，分隔符选 **制表符（Tab）**
4. 勾选「允许 HTML」并确认字段映射：`字段1→Question，字段2→Answer，字段3→Tags`
5. 导入后即可开始每日复习，Anki 会自动安排复习节奏

---

## 🃏 卡片格式

每张卡片包含 **5 个字段**，一眼识别：

```markdown
---
### Card {编号}: {主题}
**维度**: 📝速记 | 🔬深挖 | 🎯场景 | 💻代码
**难度**: ⭐ | ⭐⭐ | ⭐⭐⭐

> **Q: {问题}**

**A:** {答案，2-3 句，面试官视角}

---
```

**维度说明（4 维度）**：

| 维度 | 图标 | 考察方向 | 来自 |
|------|------|----------|------|
| 速记 | 📝 | 一句话说清概念，高频基础分 | `quick-revision.md` |
| 深挖 | 🔬 | 源码 / 原理级追问，展现深度 | `deep-dive.md` |
| 场景 | 🎯 | 真实业务场景设计题，考察方案能力 | `scenario.md` |
| 代码 | 💻 | 手写 / API 使用，考察动手能力 | `coding.md` |

**难度说明**：

- ⭐ 基础必答 —— 背下来就是送分题
- ⭐⭐ 进阶常问 —— 需要理解原理而非背诵
- ⭐⭐⭐ 压轴加分 —— 一面/二面深挖题，答好拉开差距

---

## 🗓️ 配套学习计划

👉 详见 [study-plan.md](study-plan.md)：**7 天 × 每天 2 小时 × 3 轮**（学习 → 回忆 → 测试）的冲刺计划。

---

## 📁 目录结构

```
learn/flashcards/
├── README.md              ← 本文件（总览 + 使用说明）
├── study-plan.md          ← 7 天刷题计划
├── anki-import.txt        ← Anki 导入文件（Tab 分隔，100+ 张高价值卡）
├── 01-backend-development.md
├── 02-java.md
├── ...                    ← 16 个技术栈各一张卡片文件
└── 16-openai.md
```

---

## ✅ 刷卡建议

1. **先速记后深挖**：第一遍只看 ⭐ 卡建立全局，第二遍攻 ⭐⭐ / ⭐⭐⭐
2. **出声回答**：默背容易高估自己，出声讲一遍才暴露漏洞
3. **关联场景**：能举出自己项目里的例子才是真掌握
4. **周复盘**：把 Anki 里「重来」的卡摘出来，重读对应 05-interview/ 原文