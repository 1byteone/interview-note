# 🔗 Guide 生态 ↔ Learn 教程双向映射表

> 一图一表，打通「知识索引」与「系统学习」的闭环  
> 版本: v1.0 | 日期: 2026-08-22

---

## 一图胜千言

![生态-教程映射图](../assets/ecosystem-learn-mapping.svg)

> 左半：Guide 六大生态（知识索引） | 右半：Learn 16 技术栈（系统学习）  
> 连线粗细表示关联强度，颜色对应生态/技术栈分组

---

## 完整映射表

### E01 · Claude Code 生态 → Learn

| Guide 仓库 | 关联 Learn 技术栈 | 关联点 | 推荐阅读顺序 |
|-----------|------------------|--------|-------------|
| claude-code-ultimate-guide | L14 LangChain | Agent 编排、工具调用 | Guide → Learn 并行 |
| claude-code-ultimate-guide | L15 RAG | RAG 实践、检索增强 | Guide 理论 → Learn 实操 |
| claude-code-ultimate-guide | L16 OpenAI API | Prompt 工程、Function Calling | Guide 深度 → Learn 基础 |
| claude-code-guide | L03 Spring Boot | Agent 开发环境配置 | 先用 Guide 上手，再学 Learn |
| zebbern/claude-code-guide | L14 LangChain | 命令参考、Hooks 系统 | 按需查阅 |

### E02 · Codex 生态 → Learn

| Guide 仓库 | 关联 Learn 技术栈 | 关联点 | 推荐阅读顺序 |
|-----------|------------------|--------|-------------|
| CodexGuide | L04 Python | Codex 的 Python 工程实践 | 先学 Python 基础 |
| CodexGuide | L05 FastAPI | Codex 的 API 开发 | 先学 FastAPI |
| CodexGuide | L16 OpenAI API | Codex 调用 OpenAI 接口 | Guide 实践 → Learn 理论 |

### E03 · DSH / Harness 生态 → Learn

| Guide 仓库 | 关联 Learn 技术栈 | 关联点 | 推荐阅读顺序 |
|-----------|------------------|--------|-------------|
| harness_engineering_guide | L12 Infrastructure | 服务治理、架构设计 | Guide 理论 → Learn 实操 |
| harness_engineering_guide | L14 LangChain | Agent 框架底层原理 | Guide 深度 → Learn 应用 |
| deepeseek-harness-guide | L14 LangChain | DSH 的 Agent 编排 | 并行学习 |
| awesome-harness-engineering | L12 Infrastructure | 生态工具汇总 | 按需查阅 |

### E04 · Hermes / OpenClaw 生态 → Learn

| Guide 仓库 | 关联 Learn 技术栈 | 关联点 | 推荐阅读顺序 |
|-----------|------------------|--------|-------------|
| hermes-agent-guide | L14 LangChain | 多 Agent 编排、记忆系统 | Guide 深度 → Learn 基础 |
| hermes-agent-guide | L15 RAG | 知识检索、记忆分层 | Guide 理论 → Learn 实操 |
| hermes-agent-guide | L16 OpenAI API | 工具调用、模型接入 | 并行学习 |
| openclaw-security-practice-guide | L12 Infrastructure | Agent 安全体系 | 独立学习 |

### E05 · MCP 协议生态 → Learn

| Guide 仓库 | 关联 Learn 技术栈 | 关联点 | 推荐阅读顺序 |
|-----------|------------------|--------|-------------|
| MCP-Chinese-Getting-Started-Guide | L14 LangChain | MCP Tool Calling | 先学 MCP 再学 LangChain 集成 |
| MCP-Chinese-Getting-Started-Guide | L05 FastAPI | MCP Server 开发 | 先学 FastAPI 再学 MCP |
| MCP-Chinese-Getting-Started-Guide | L16 OpenAI API | MCP 与 LLM 集成 | 并行学习 |

### E06 · 通识与基础 → Learn

| Guide 仓库 | 关联 Learn 技术栈 | 关联点 | 推荐阅读顺序 |
|-----------|------------------|--------|-------------|
| Prompt-Engineering-Guide | L16 OpenAI API | Prompt 理论 → API 实践 | Guide 基础 → Learn 实操 |
| Prompt-Engineering-Guide | L14 LangChain | Prompt Template | Guide 理论 → Learn 应用 |
| Prompt-Engineering-Guide | L15 RAG | RAG 中的 Prompt 策略 | 并行学习 |
| AgentGuide | L01 Backend | 架构设计思维 | 先学后端基础 |
| AgentGuide | L02 Java | Agent 后端开发 | 先学 Java |
| AgentGuide | L14 LangChain | Agent 框架实战 | Guide 求职 → Learn 技术 |
| ai-system-design-guide | L15 RAG | 系统设计中的 RAG | Guide 架构 → Learn 实现 |
| ai-system-design-guide | L16 OpenAI API | 系统设计中的 LLM 接入 | Guide 架构 → Learn 实现 |
| python-guide | L04 Python | Python 工程最佳实践 | 先学 Python 基础 |
| awesome-agent-skills | L14 LangChain | Skills 开发 | 按需查阅 |
| ai-agent-interview-guide | 全部 | 面试准备 | 学完 Learn 再刷题 |

---

## 使用建议

### 我是初学者（先学再查）

```
① Learn 技术栈（系统学习，打好基础）
    ↓
② Guide 生态（拓展视野，深度理解）
    ↓
③ 映射表（找到对应关系，巩固知识）
```

### 我是进阶者（先查再学）

```
① Guide 生态（发现新知识，了解前沿）
    ↓
② 映射表（找到对应的 Learn 技术栈）
    ↓
③ Learn 技术栈（系统学习，动手实践）
```

### 我是面试准备者（双向冲刺）

```
① Guide 生态（了解行业全景，建立知识框架）
    ↓
② 映射表定位薄弱环节
    ↓
③ Learn 面试模块（专项突破）
    ↓
④ ai-agent-interview-guide（刷题冲刺）
```