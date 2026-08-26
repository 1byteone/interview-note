# Agent 基础与工具调用

> 来源：`第2章 构建智能体.docx`。示例以 CropWise 农业问答为主线；具体 Agent API 必须绑定项目依赖版本。

## 学习目标

理解 Agent 闭环、工具 Schema、结构化输出、线程状态和失败处理。

## 执行闭环

```text
问题 → 判断意图 → 选择工具 → 执行 → 观察结果 → 继续、拒答或完成
```

工具调用不是权限授权。服务端必须再次校验参数、用户权限、调用次数和超时时间。

## 工具契约

一个农业检索工具至少应声明：作物、症状、地区、生育期等字段的类型和枚举约束，并返回来源、页码、内容和检索分数。工具异常应返回结构化错误，不能让模型猜测成功结果。

```python
from pydantic import BaseModel, Field

class PestQuery(BaseModel):
    crop: str = Field(min_length=1, max_length=40)
    symptom: str = Field(min_length=1, max_length=200)
    region: str | None = Field(default=None, max_length=40)
```

## 结构化输出

使用 Pydantic 或等价 Schema 校验模型输出；校验失败时重试次数必须有限，连续失败应转为人工可理解的错误或安全拒答。

## Memory 边界

教学版可使用进程内保存器演示线程消息；多实例部署必须使用共享持久化检查点。检查点只负责状态恢复，不自动产生可信的长期用户记忆。

## 安全与可靠性

- 工具采用白名单，不允许模型任意拼接 URL 或 SQL；
- 设置单次请求最大步数、总超时和响应大小；
- 有副作用的工具要求幂等键和审计日志；
- 农药剂量等高风险建议必须经过证据门控；
- 工具失败时明确降级，不把错误当作事实。

## 面试要点

Agent 与 Chain 的边界、工具参数为何二次校验、如何防止循环、如何隔离 `thread_id`、结构化输出失败如何处理。

## 来源

- `AI_EXAM/docs/第2章 构建智能体.docx`
- [基础概念](01-ai-llm-prompt-agent-basics.md)
- [技术审核清单](../../../../docs/ai-exam-tech-review.md)
