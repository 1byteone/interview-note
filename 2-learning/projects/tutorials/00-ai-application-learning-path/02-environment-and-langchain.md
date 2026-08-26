# 开发环境与 LangChain 生态

> 来源：`三大组件.docx`、`第1章 概述.docx`。本章介绍职责边界，不将快速变化的 API 写成无版本前提的事实。

## 学习目标

- 为 Python AI 项目建立隔离、可复现的环境；
- 区分 Prompt、RAG、Tool 和 Agent 的适用场景；
- 理解 LangChain、LangGraph、LangSmith 的职责边界；
- 识别版本漂移和密钥泄露风险。

## 1. 环境基线

建议使用项目级虚拟环境，并将直接依赖锁定到 `requirements.txt` 或 `pyproject.toml`。不要在同一环境中无记录地混用 Conda、pip 和系统 Python。

```bash
python -m venv .venv
# Windows PowerShell
.\.venv\Scripts\Activate.ps1
python -m pip install --upgrade pip
```

实际项目应根据目标 LangChain、LangGraph、模型提供商和向量库版本生成锁定文件；Python 版本需先验证第三方依赖兼容性。

## 2. API Key 管理

只提交 `.env.example`，不提交真实 `.env`：

```dotenv
MODEL_API_KEY=replace-me
LANGSMITH_TRACING=false
```

生产环境应使用密钥管理服务，日志中不得输出完整 Key、用户隐私和检索原文。模型服务、LangSmith 等外部追踪系统还需要评估数据合规和采样策略。

## 3. 三种开发范式

| 范式 | 解决的问题 | 典型场景 |
|---|---|---|
| Prompt Engineering | 约束一次模型输出 | 总结、改写、分类 |
| RAG | 注入外部知识 | 企业文档、农业知识库 |
| Agent | 协调多步工具和状态 | 检索、天气、计算和审批 |

## 4. LangChain 生态边界

- **LangChain Core / 集成**：模型、Prompt、输出解析、检索器和工具等可组合组件；
- **LangGraph**：有状态、可循环、可中断和可恢复的工作流编排；
- **LangSmith**：调用链追踪、评估、调试和生产观测。

经典教程常用“Models、Prompts、Indexes、Memory、Chains、Agents”六组件分类；现代版本的包拆分和 Agent API 可能不同，阅读代码时必须结合锁定版本。

## 5. 版本核验规则

以下内容在本教程体系中必须绑定版本后再落地：

- `create_agent` 的导入路径和参数；
- LangGraph 检查点和流式事件 API；
- LangSmith 环境变量及 SDK；
- 向量库和 Embedding 模型的维度与距离定义。

不能运行验证的代码应明确标记为概念伪代码，不应给读者造成“复制即可运行”的误导。

## 来源

- `AI_EXAM/docs/三大组件.docx`
- `AI_EXAM/docs/第1章 概述.docx`
- [技术审核清单](../../../../docs/ai-exam-tech-review.md)
