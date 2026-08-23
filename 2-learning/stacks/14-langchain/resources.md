# 资源推荐与学习路线

> 面向后端开发者的 LangChain 学习资源，涵盖官方文档、书籍、视频课程、开源项目与学习路线建议。

---

## 官方文档

| 资源 | 链接 | 说明 |
|------|------|------|
| LangChain 官方文档 | https://python.langchain.com/docs | 最权威的源码级文档，含 Get Started、Cookbook、API Reference |
| LangChain 官方 API 参考 | https://python.langchain.com/api_reference | 类与方法签名、参数说明、继承关系 |
| LCEL 文档 | https://python.langchain.com/docs/how_to/#langchain-expression-language-lcel | LCEL 声明式语法详解，含 Runnable 系列详解 |
| LangGraph 文档 | https://langchain-ai.github.io/langgraph/ | Agent 状态机编排框架，与 LangChain 无缝集成 |
| LangSmith 平台 | https://smith.langchain.com | 可观测性平台，用于 Trace、调试、评测、Prompt Hub |
| GitHub 仓库 | https://github.com/langchain-ai/langchain | 源码学习、Issue 追踪、社区贡献 |

---

## 书籍推荐

| 书名 | 作者 | 推荐理由 |
|------|------|----------|
| 大语言模型应用指南（LangChain 实战） | 国内社区 | 案例贴近中文场景，适合国内开发者入门 |
| Building LLM Apps | O'Reilly | 英文原版，LangChain + OpenAI 完整项目，偏实战 |
| LangChain in Action | Manning | 从 Chain 到 Agent 再到 RAG 的完整路线，代码量大 |
| 大模型应用开发：LangChain 实战 | 作者：黄佳 | 中文版，示例丰富，适合零基础入门 |

> 提示：AI 框架更新极快，书籍出版时可能落后 1-2 个版本。建议将书籍当作**概念理解**的辅助材料，实际开发以官方文档和 API 参考为准。

---

## 视频课程

| 课程 | 平台 | 说明 |
|------|------|------|
| LangChain 官方教程 | YouTube @LangChain | 官方出品，含 LCEL、Agent、RAG 等核心模块 |
| LangChain for LLM Application Development | DeepLearning.AI | 吴恩达与 LangChain 创始人 Harrison Chase 合作，短小精悍 |
| LangGraph 入门 | YouTube @LangChain | 引入了若 Agent 庭状态机与持久化 |
| 中文 LangChain 实战 | Bilibili | 中文社区，搜索"LangChain 实战"可找到多个连载系列 |

---

## 开源项目参考

| 项目 | 链接 | 学习要点 |
|------|------|----------|
| 本项目 AI 商城 | `projects/ai-mall/` | 看 Agent + vector_search_tool + 结构化输出的实际落地 |
| LangChain 官方 Cookbook | GitHub langchain-ai/langchain cookbook 目录 | 130+ 场景示例，含 Agent、RAG、Tool 等 |
| ChatLangChain | 官方 Demo | 基于 LangChain 的文档问答，含完整链路 |
| LLM 应用工程模板 | GitHub 搜索 "llm-app-template" | 多项目结构参考，含项目布局、测试、部署 |

---

## 学习路线建议

### 路线 A：快速上手（面向有 LLM 调用经验的开发者，1-2 天）

```
安装 → 核心概念 → 第一个 Chain → LCEL 基础 → 简单 Agent → 读取项目源码
```

1. 安装 `langchain` + `langchain-openai`，跑通第一个 LLM Chain
2. 理解 `PromptTemplate`、`ChatModel`、`OutputParser` 三个核心原语
3. 用 LCEL 管道符重写 Chain，对比与手写 Python 的区别
4. 用 `create_agent` + 一个自定义工具做一个简单 Agent
5. 阅读 `projects/ai-mall/` 中的 Agent 源码，理解 vector_search_tool 和结构化输出

### 路线 B：系统学习（面向后端开发者，1-2 周）

```
Python 基础 ─▶ FastAPI ─▶ LangChain 基础 ─▶ LCEL ─▶ Chain ─▶
    Memory ─▶ RAG 集成 ─▶ Agent ─▶ Tool ─▶ LangGraph ─▶ 项目实战
```

**第一阶段（Day 1-2）：基础**
- 完成 01-basics 的两个文档（快速入门 + LCEL）
- 跑通本地第一个 Chain 和第一个 LCEL 管道

**第二阶段（Day 3-5）：核心能力**
- 学习 02-core 系列：输出解析器、记忆管理、RAG 集成
- 理解 LLM 与 ChatModel 的区别，掌握流式输出

**第三阶段（Day 6-8）：Agent 与工具**
- 学习 03-advanced 系列：Agent 原理、Tool 定义、自定义工具
- 理解 ReAct 循环、工具选择策略、容错机制
- 学习 LangGraph 状态机，理解 thread_id 与 Checkpoint 机制

**第四阶段（Day 9-12）：项目实战**
- 完成 04-projects 的 AI 商城集成文档
- 动手实现迷你智能客服 Agent
- 理解结构化输出、条件提取、记忆持久化

**第五阶段（Day 13-14）：面试冲刺**
- 完成 05-interview 的速记、深挖、场景、代码题
- 熟悉商城项目中的 Agent 相关面试考点

### 路线 C：面试冲刺（面向已有基础，靠前突击）

```
面试高频考点 → 商城项目源码分析 → 场景题 → 代码题
```

1. 快速过一遍「面试高频考点一览表」
2. 阅读 `projects/ai-mall/mall-ai-search-knowledge-map.md` 中的 Agent 相关知识点
3. 理解 Agent 与固定 Chain 的职责区别、vector_search_tool 的 top-k 参数
4. 练习场景题：Agent 空召回处理、thread_id 会话修复、条件提取失败

---

## 版本选择建议

LangChain 目前处于快速迭代期，版本差异较大：

| 版本 | 建议 |
|------|------|
| 0.3.x（当前最新） | 推荐。LCEL 作为一等公民，Agent 基于 LangGraph，`create_agent` 替代旧版 `AgentExecutor` |
| 0.2.x | 兼容，但部分 API 已标记为 deprecated |
| 0.1.x 及以下 | 不推荐学习。Chain 式 API 是旧风格，新项目已全面转向 LCEL |

> 本教程基于 LangChain 0.3.x / 1.0 系列编写。若使用旧版本，部分 API 调用方式可能不同，建议以官方文档为准。

---

## 社区与生态

- **LangChain Discord**：官方社区，提问可获得核心开发者响应
- **Reddit r/LangChain**：英文社区，讨论实现方案与最佳实践
- **知乎 / 微信公众号**：中文社区，搜索"LangChain 实战"可找到大量经验分享
- **Hugging Face**：大量基于 LangChain 的 Space 演示，可 fork 学习