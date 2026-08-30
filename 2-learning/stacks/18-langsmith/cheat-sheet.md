# LangSmith 速查表（Cheat Sheet）

> 一页掌握 LangSmith 核心 API 与概念，开发时随手翻。

---

## 环境变量（接入）

```bash
export LANGCHAIN_TRACING_V2=true
export LANGCHAIN_API_KEY="lsv2_xxx"
export LANGCHAIN_PROJECT="my-project"           # 可选，归属项目
export LANGCHAIN_TRACING_SAMPLING_RATE=0.1      # 可选，采样控制成本
```

## 核心概念对照

| 概念 | 一句话 | 层级 |
|------|--------|------|
| Thread | 跨多次调用的会话维度 | 会话 |
| Trace | 单次执行的完整记录（树） | 执行 |
| Run | 树中一次原子步骤（模型/工具/节点） | 步骤 |
| Dataset | 评测数据集（考卷） | 数据 |
| Experiment | 目标函数在 Dataset 上的评测运行（成绩单） | 评测 |
| Evaluator | 给输出打分（规则/LLM/人工） | 判卷 |
| Annotation Queue | 人工标注队列（评审工作流） | 评审 |
| Automation | 条件+动作规则引擎（告警/入队/入集） | 自动化 |

## 常用 Python API

```python
from langsmith import Client, traceable
from langsmith.schemas import Run, Example
from langsmith.evaluation import evaluate, LangChainStringEvaluator

# 客户端
client = Client()

# 上报反馈
client.create_feedback(run_id=..., key="user_reaction", score=0, comment="...")

# 建数据集 + 样本
ds = client.create_dataset(dataset_name="qa-v1", description="...")
client.create_example(inputs={"q": "..."}, outputs={"a": "..."}, dataset_id=ds.id)

# 跑评测
results = evaluate(predict, data="qa-v1", evaluators=[code_evaluator, llm_judge])

# 追踪任意函数
@traceable(name="my_step")
def my_step(x): ...

# 评估器签名
def my_evaluator(run: Run, example: Example | None = None) -> dict:
    return {"key": "metric", "score": 0.0, "comment": "..."}
```

## RAG 官方评估器

| 评估器 | 维度 | 用途 |
|--------|------|------|
| `rag_context_accuracy` | 检索质量 | 检索上下文是否相关 |
| `rag_answer_faithfulness` | 忠实度 | 回答是否忠于证据（防幻觉） |
| `rag_answer_relevance` | 相关性 | 回答是否切题 |

## 三层监控指标

| 层 | 指标 | 发现问题 |
|----|------|---------|
| 质量 | 在线评估分 / 用户反馈 / 错误样本率 | 隐性回归 |
| 健康 | 错误率 / p95 延迟 / 工具失败率 | 显性问题 |
| 成本 | 每 run 价格 / token 趋势 / 单用户成本 | 费用失控 |

## 选型速查（LangSmith vs Langfuse）

- LangSmith：LangChain 生态零侵入 + 评测最全 + 供应商绑定（闭源平台）
- Langfuse：开源可自托管 + 框架无关 + 数据自主（评测/部署较弱）

## 高频问答

- **接入要几行？** 0 行——2 个环境变量，LangChain 自动上报
- **纯函数怎么接入？** `@traceable`
- **评测集哪来？** 手工 + Trace 沉淀 + 批量导入，**生产坏例回流是核心**
- **裁判不可靠怎么办？** 锚点 + few-shot + 人类反馈回灌 + 固定模型防漂移
- **评测怎么守门？** CI 里跑 evaluate 对比 baseline，低于阈值阻断合并

---

> 完整教程：`01-basics/` → `02-core/` → `03-advanced/` → `04-project/` → `05-interview/`；题库见《LangChain_LangGraph_LangSmith_面试QA_详解版.md》第三章。