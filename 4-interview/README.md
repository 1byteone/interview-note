# 💼 面试准备

> **策略与工具** — 3 个月系统面试计划

---

## 目录

| 子目录/文件 | 说明 |
|------------|------|
| [preparation-plan.md](preparation-plan.md) | 3 个月系统面试计划（原根目录 `interview-preparation-plan.md`） |
| [project-interview-guide.md](project-interview-guide.md) | 项目经历 & 实习经历 — 面试回答与说辞手册 |
| [projects/](projects/) | 4 个项目的深度面试文档 + 项目速记卡（快速复习/面试要点/面试追问） |
| [tools/](tools/) | 面试题生成器 + 模拟面试（原 `interview-tools/`） |
| [jd-qa/](jd-qa/) | 招聘 JD × 简历 对齐的 50 题标准专业 QA（AI 应用开发方向） |

### 项目深度面试文档

| 项目 | 深度面试 | 速记卡 | 大厂面试官 QA | 面试 QA 精编版 |
|------|---------|--------|--------------|--------------|
| 分布式微云商城 | [项目深度面试.md](projects/mall-micro-cloud/项目深度面试.md) | [项目速记卡.md](projects/mall-micro-cloud/项目速记卡.md) | [大厂面试官QA.md](projects/mall-micro-cloud/大厂面试官QA.md) | [微云商城面试QA精编版.md](projects/mall-micro-cloud/微云商城面试QA精编版.md) |
| 灵犀智能写作 | [项目深度面试.md](projects/ai-passage-creator/项目深度面试.md) | [项目速记卡.md](projects/ai-passage-creator/项目速记卡.md) | [大厂面试官QA.md](projects/ai-passage-creator/大厂面试官QA.md) |
| 农业知识库问答 | [项目深度面试.md](projects/agri-qa-assistant/项目深度面试.md) | [项目速记卡.md](projects/agri-qa-assistant/项目速记卡.md) | [大厂面试官QA.md](projects/agri-qa-assistant/大厂面试官QA.md) |
| 智颐养老护理系统 | [项目深度面试.md](projects/zznursing/项目深度面试.md) | [项目速记卡.md](projects/zznursing/项目速记卡.md) | [大厂面试官QA.md](projects/zznursing/大厂面试官QA.md) |

> 各项目递进追问补充：
> [商城](projects/mall-micro-cloud/项目深度面试-递进追问补充.md) · [智能写作](projects/ai-passage-creator/项目深度面试-递进追问补充.md) · [农业问答](projects/agri-qa-assistant/项目深度面试-递进追问补充.md) · [养老护理](projects/zznursing/项目深度面试-递进追问补充.md)

> 商城面试 QA 精编版（93 题）：[微云商城面试QA精编版.md](projects/mall-micro-cloud/微云商城面试QA精编版.md) — 基于实训项目源码，6 域分章，含自我介绍 / Java 基础 / 数据库 / 中间件 / 项目实战 / 软技能
>
> 商城 AI 检索模块 LangChain/LangGraph/LangSmith 落地问答：[LangChain_LangSmith落地面试QA.md](projects/mall-micro-cloud/LangChain_LangSmith落地面试QA.md) — 11 题深挖 `create_agent` / 工具 / 结构化输出 / Checkpointer 记忆 / 防幻觉 / 评测演进，与三合一 QA 互链

## 各技术栈面试题

面试题分布在 `1-knowledge/` 各技术栈目录下的 `README.md` 中：
- [Java 核心](../1-knowledge/01-java/java-core/) — JVM / JUC / 集合 / IO
- [Spring Boot](../1-knowledge/01-java/spring/spring-boot/) — Boot 核心
- [Spring Cloud 各组件](../1-knowledge/01-java/spring-cloud/) — Nacos / Gateway / Sentinel / Seata / RocketMQ
- [中间件](../1-knowledge/02-infrastructure/middleware/) — Redis / MySQL / ES / Kafka
- [DevOps](../1-knowledge/02-infrastructure/devops/) — Docker / Nginx / CI-CD
- [AI 技术](../1-knowledge/03-ai/) — LLM / RAG / Agent / LangGraph / Harness