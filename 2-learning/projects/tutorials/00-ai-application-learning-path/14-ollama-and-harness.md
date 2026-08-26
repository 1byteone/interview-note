# Ollama 与 Harness 本地 Agent 专题

> 来源：`DeepSeek-Harness1.docx`。本专题不将未验证的 CLI、仓库或插件市场写成稳定操作指南。

## 架构理解

```text
本地模型运行时 + Harness 编排层 + 插件能力 = Agent 应用
```

Ollama 负责本地模型运行，Harness 负责会话、工具和插件编排；文件、Shell、Git 和沙箱插件都需要独立的权限边界。

## 部署原则

模型目录使用持久化卷；服务只暴露必要端口；不要在镜像或命令中写密钥；Shell 和文件工具限制工作目录、命令集合、资源配额并记录审计。

## 验证清单

使用前确认官方仓库、CLI 版本、模型兼容矩阵、插件来源、沙箱隔离能力和许可证。命令若未在目标版本实际运行，应标为“待确认”，改用概念步骤描述。

## 与 LangGraph 的关系

Harness 是 Agent 运行和扩展路线之一；LangGraph 更关注业务状态图和可恢复流程。二者可以组合，但不能把插件编排自动等同于业务工作流治理。

## 来源

- `AI_EXAM/docs/DeepSeek-Harness1.docx`
- [技术审核清单](../../../../docs/ai-exam-tech-review.md)
