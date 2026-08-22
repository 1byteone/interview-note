# 🧭 学习路线建议 — 生态视角

> **场景驱动 · 生态优先 · 交叉引用**  
> 版本: v1.0 | 日期: 2026-08-22

---

## 路线 1：AI Agent 初学者 → 求职者

> 适合从零开始，12 周内达到求职水平

```
Phase 1: 通识基础（E06，2 周）
├── Prompt-Engineering-Guide（核心：CoT, ReAct, Function Calling）
├── MCP-Chinese-Getting-Started-Guide（理解 Agent 工具调用协议）
└── 产出：理解 Agent = LLM + Tools + Memory 的范式

Phase 2: Agent 求职体系（E06，2 周）
├── AgentGuide（双线路径：算法岗 vs 开发岗）
├── ai-agents-from-zero（LangChain/LangGraph 实践）
└── 产出：确定目标岗位，完成学习路线规划

Phase 3: 选择一个生态深入（E01 或 E02，3 周）
├── 选 Claude Code → claudecode-guide → claude-code-guide → claude-code-ultimate-guide
├── 选 Codex → CodexGuide
└── 产出：能用 Coding Agent 完成日常开发

Phase 4: 系统设计（E06，2 周）
├── ai-system-design-guide（RAG 设计、Agentic 系统、案例研究）
└── 产出：能画出 3 个系统架构图

Phase 5: 面试冲刺（E06，2 周）
├── ai-agent-interview-guide（200+ 八股文，三语言项目）
├── AgentGuide（面试准备、简历优化、投递策略）
└── 产出：完成面试题，准备好 STAR 话术
```

---

## 路线 2：后端工程师 → AI Agent 开发

> 适合已有 Java/Go 后端基础，转型 AI 方向

```
Phase 1: 语言补强（E06，1 周）
├── python-guide（Python 工程化、包管理、测试）
└── 产出：能用 Python 写生产级代码

Phase 2: 理解 Agent 本质（E03，3 周）
├── harness_engineering_guide Part 1-2（Harness 原理、架构全景）
├── deepeseek-harness-guide（DSH 框架实操）
└── 产出：理解智能体 = 大模型 + Harness 的工程范式

Phase 3: Coding Agent 工具链（E01 或 E02，2 周）
├── 选 E01 → claude-code-ultimate-guide（Hooks, Skills, Agent Teams）
├── 选 E02 → CodexGuide（AGENTS.md, 沙盒, 团队协作）
└── 产出：用 Coding Agent 提升日常开发效率 3 倍

Phase 4: 开源框架探索（E04，2 周）
├── hermes-agent-guide（架构、记忆系统、多平台接入）
├── openclaw-security-practice-guide（安全设计）
└── 产出：能在开源框架上搭建 Agent 服务

Phase 5: 面试准备（E06，1 周）
├── ai-agent-interview-guide（Java 版 Agent 项目）
└── 产出：安全 + 面试双准备
```

---

## 路线 3：资深开发者 → Agent 架构师

> 适合已有 AI 基础，想深入架构和系统设计

```
Phase 1: 系统设计深度（E06，3 周）
├── ai-system-design-guide（全部 20 章节 + 15 案例研究）
├── Prompt-Engineering-Guide（高级技术：ToT, APE, DSP）
└── 产出：能设计生产级 AI 系统架构

Phase 2: Harness 理论 + 实践（E03，3 周）
├── harness_engineering_guide（全部 14 章 + MiniHarness 实战）
├── deepeseek-harness-guide（8 步 Agent 开发、Cordis 机制）
└── 产出：能开发 DSH 插件或自定义 Harness

Phase 3: 安全与评估（E04 + E01，2 周）
├── openclaw-security-practice-guide（三层防御矩阵实现）
├── claude-code-ultimate-guide（安全威胁数据库、Red Teaming）
└── 产出：能设计 Agent 安全体系

Phase 4: 框架选型与对比（E01 + E02 + E04，2 周）
├── hermes-agent-guide（12 维度 OpenClaw 对比）
├── claude-code-guide（全部功能对比）
├── CodexGuide（Codex 能力边界）
└── 产出：能根据场景选择最优 Agent 框架
```

---

## 路线 4：快速选型指南 — 按生态

| 我想用哪个工具/框架 | 学哪个生态 | 从哪里开始 |
|---------------------|-----------|-----------|
| Claude Code | **E01** | claudecode-guide（入门）→ claude-code-guide（功能）→ claude-code-ultimate-guide（深度） |
| OpenAI Codex | **E02** | CodexGuide start/（快速上手）→ advanced/（进阶）→ recipes/（实战） |
| DeepSeek Harness | **E03** | deepeseek-harness-guide Quick Start → GUIDE.md → 插件开发 |
| Harness 工程理论 | **E03** | harness_engineering_guide（14 章专著从头到尾） |
| Hermes Agent | **E04** | hermes-agent-guide 阅读路线 1（快速入门）→ 路线 2（技术深度） |
| OpenClaw 安全 | **E04** | openclaw-security-practice-guide（三层防御） |
| MCP 协议 | **E05** | MCP-Chinese-Getting-Started-Guide（从头到尾） |
| 通识入门 | **E06** | Prompt-Engineering-Guide → AgentGuide → ai-agents-from-zero |
| 面试准备 | **E06** | AgentGuide（求职体系）→ ai-agent-interview-guide（八股文）→ ai-system-design-guide（系统设计题） |

---

## 路线 5：按角色推荐

| 角色 | 推荐路径 | 预计时间 |
|------|----------|----------|
| 🎓 学生/转行者 | E06 通识（4 周）→ E01 或 E02（2 周）→ E06 面试（2 周） | 8 周 |
| 💻 后端工程师 | E06 python-guide（1 周）→ E03（3 周）→ E01/E02（2 周）→ E04（2 周） | 8 周 |
| 🏗️ 架构师 | E06 系统设计（3 周）→ E03 理论（3 周）→ E04 安全（2 周）→ 对比（2 周） | 10 周 |
| 🔒 安全工程师 | E04 安全实践（2 周）→ E01 安全威胁库（2 周）→ E03 安全体系（2 周） | 6 周 |
| 👨‍💼 产品经理 | E06 通识概览（1 周）→ E05 MCP（1 周）→ E01 入门（1 周） | 3 周 |
| 💰 独立开发者 | E04 变现路径（1 周）→ E01/E02（2 周）→ E05 MCP（1 周） | 4 周 |