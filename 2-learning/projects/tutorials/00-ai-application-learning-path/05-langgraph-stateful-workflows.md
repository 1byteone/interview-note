# LangGraph 状态化工作流

> 来源：`第 4 章 LangGraph 框架.docx`。本章先固定工作流契约，再绑定具体 LangGraph 版本。

## 为什么需要图

线性 Chain 难以表达循环、条件路由、中断和恢复。状态图把状态、节点和边显式化，便于测试和审计。

## CropWise 工作流

```text
Domain Guard → Query Router → Retrieval → Evidence Check
      ├─ 证据充分 → Generate → Done
      ├─ 证据不足 → Refuse
      └─ 可补充条件 → Ask Clarification
```

## 状态契约

```python
from typing import TypedDict

class QAState(TypedDict, total=False):
    question: str
    route: str
    evidence: list[dict]
    answer: str
    refusal_reason: str
    step_count: int
```

每个节点只修改自己负责的字段；节点应尽量无副作用，外部写操作使用幂等键。

## 循环和恢复

所有循环必须有最大步数、总超时和明确终止状态。`thread_id` 用于隔离会话，不能由用户随意复用他人的线程。检查点用于暂停、恢复和故障排查，不代表检查点内容可信或无需鉴权。

## 故障处理

- 检索服务超时：返回缓存证据或安全拒答；
- 模型超时：发送 `error` 事件并保留请求状态；
- 工具部分失败：标注证据来源，不隐藏缺失；
- 恢复重复执行：为副作用节点使用幂等键。

## 面试要点

State 与 Context 的差异、条件边如何保证安全、检查点如何恢复、为什么不能无限循环、如何测试每个节点和整条图。

## 来源

- `AI_EXAM/docs/第 4 章 LangGraph 框架.docx`
- [现有 LangGraph 项目教程](../14-langgraph-agent/README.md)
