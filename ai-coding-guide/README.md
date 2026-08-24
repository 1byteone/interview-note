# AI 编程工具全面实战指南

> **版本**: 2026-08 | **适用人群**: Java 后端工程师、AI 全栈开发者、技术负责人
> **核心目标**: 建立 AI-Native Software Engineering 工作体系

---

## 文档索引

| # | 文件 | 内容概要 | 适合场景 |
|---|------|---------|---------|
| 01 | [认知升级](01-认知升级.md) | AI Agent 工作流、生产力公式、五工具定位 | 入门必读 |
| 02 | [Claude Code](02-Claude-Code.md) | CLAUDE.md / Hooks / Skills / Subagents / Plugins / LSP | 大型项目主力 |
| 03 | [Codex](03-Codex.md) | CLI 三模式 + ChatGPT App + Workspace Agents + Automations | 自动修复 / 定时任务 |
| 04 | [DeepSeek Harness](04-DeepSeek-Harness.md) | Everything is a Plugin / Code Mode / Cordis 内核 | Agent 工程研究 |
| 05 | [Hermes](05-Hermes.md) | 持久记忆 / 自进化 Skills / Session Search / Learning Journey | 长期自主 Agent |
| 06 | [Cursor](06-Cursor.md) | Agent Mode / .mdc Rules / Cloud Agents / YOLO / MCP | 日常 IDE 开发 |
| 07 | [Context Engineering](07-Context-Engineering.md) | AGENTS.md / 四大策略 / 三级记忆架构 | 能力进阶必读 |
| 08 | [MCP 与 Skills](08-MCP与Skills.md) | Spring Boot MCP Server / Skill 设计 / 20 个推荐 Skills | 扩展能力层 |
| 09 | [企业级案例与 ROI](09-企业级案例与ROI.md) | Stripe / Meta / Duolingo 案例 + ROI 计算 + 四领域落地 | 技术决策 / 汇报 |
| 10 | [Java 全流程实战](10-Java全流程实战.md) | 工程 Prompt / Redis 秒杀 / MySQL / RocketMQ 设计 | 实战参考 |
| 11 | [安全治理](11-安全治理.md) | 风险分级 / 企业安全配置 / 代码审查清单 | 生产环境 |
| 12 | [学习路线与配置模板](12-学习路线与配置模板.md) | 五级能力模型 + 五阶段路线 + 完整配置模板 + 官方资源 | 规划与落地 |

---

## 快速导航

**我是新手，从哪里开始？**
→ [01-认知升级](01-认知升级.md) → [06-Cursor](06-Cursor.md) → [02-Claude Code](02-Claude-Code.md)

**我要搭建团队 AI 开发规范？**
→ [07-Context Engineering](07-Context-Engineering.md) → [08-MCP 与 Skills](08-MCP与Skills.md) → [11-安全治理](11-安全治理.md)

**我要向领导汇报 ROI？**
→ [09-企业级案例与 ROI](09-企业级案例与ROI.md)

**我要做 Spring Boot 实战？**
→ [10-Java 全流程实战](10-Java全流程实战.md)

**我要做实战跟练？**
→ [tutorials/README.md](tutorials/README.md) — 14 个完整实操教程，从搭建到协作

**我要研究 Agent 架构？**
→ [04-DeepSeek Harness](04-DeepSeek-Harness.md) → [05-Hermes](05-Hermes.md)

---

## 实战教程索引

| # | 教程 | 工具 | 场景 |
|---|------|------|------|
| T01 | [搭建 Spring Boot 项目](tutorials/T01-Claude-Code搭建Spring-Boot项目.md) | Claude Code | 从 Prompt 到可运行项目 |
| T02 | [CLAUDE.md 企业级配置](tutorials/T02-Claude-Code企业级CLAUDE-MD配置.md) | Claude Code | 多模块微服务上下文工程 |
| T03 | [遗留代码 50K 行重构](tutorials/T03-Claude-Code遗留代码重构.md) | Claude Code | Spring Boot 2→3 渐进迁移 |
| T04 | [Full Auto 自动修复](tutorials/T04-Codex自动修复循环.md) | Codex CLI | CI 失败自主修复 |
| T05 | [AGENTS.md Review 规则](tutorials/T05-Codex自定义Review规则.md) | Codex App | PR 自动审查 |
| T06 | [Workspace Agent 定时任务](tutorials/T06-Codex定时自动化.md) | Codex App | 每日 Issue 分诊 |
| T07 | [DSH 四模式体验](tutorials/T07-DeepSeek-Harness模式对比.md) | DSH | Standard/Code/Minimal/Creator |
| T08 | [DSH 自定义插件](tutorials/T08-DeepSeek-Harness自定义插件.md) | DSH | Cordis 插件开发 |
| T09 | [Hermes 记忆与 Skill](tutorials/T09-Hermes记忆与Skill.md) | Hermes | 持久记忆 + 自进化 Skill |
| T10 | [Hermes 定时工作流](tutorials/T10-Hermes定时工作流.md) | Hermes | 每日代码质量检查 |
| T11 | [Cursor .mdc Rules](tutorials/T11-Cursor-Rules实战.md) | Cursor | Java 规则配置 |
| T12 | [Cursor Agent 多文件重构](tutorials/T12-Cursor-Agent多文件重构.md) | Cursor | Service 层拆分 |
| T13 | [MCP Server 实现](tutorials/T13-MCP-Server实战.md) | Spring Boot | 自定义 MCP Server |
| T14 | [五工具协作全流程](tutorials/T14-五工具协作全流程.md) | 全部 | 优惠券功能全流程 |

---

> 原始完整文档: [AI编程工具全面指南_Java后端与全栈工程师.md](../AI编程工具全面指南_Java后端与全栈工程师.md)
