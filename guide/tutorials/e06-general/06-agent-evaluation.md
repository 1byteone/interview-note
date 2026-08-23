# Agent 评估体系：从 RAG 质量到生产级监控

> **生态**: E06 · 通识与基础 | **等级**: 进阶 | **前置要求**: 了解 RAG 管线与 Agent 系统设计（建议先阅读 03-production-system-design.md 和 05-advanced-rag-systems.md）

"没有评估，就没有优化"——这句话在 Agent 工程中尤为关键。一个生产级 Agent 系统上线后，你如何知道检索质量是否下降？工具调用是否准确？幻觉率是否在可接受范围内？这些问题只有在系统化的评估体系下才能回答。

2025-2026 年，LLM 评估领域从学术研究快速走向工程落地，催生了 DeepEval、RAGAS、LangSmith、MLflow、Arize Phoenix 等一批成熟的评估框架。本教程将系统梳理 Agent 评估的指标体系、框架选型、实战代码和 CI/CD 集成策略，帮助你建立从开发到生产全链路的评估能力。

---

## 1. 为什么需要 Agent 评估

在原型阶段，我们通过人工抽查来判断 Agent 输出质量。但进入生产环境后，这种方式暴露出三个根本问题：

| 问题 | 表现 | 后果 |
|------|------|------|
| **不可规模化** | 人工检查无法覆盖每天数万次对话 | 质量退化无法及时发现 |
| **主观不一致** | 不同评估者标准不同 | 评估结果难以复现和对比 |
| **无回归检测** | 无法判断一次改动是否引入了退化 | 迭代优化缺乏安全网 |

评估体系的目标是：**用可量化、可复现、可自动化的指标，替代人工主观判断，为每一次 Agent 改动提供质量门禁。**

### 评估的三层价值

1. **开发阶段**：在 PR 级别检测质量退化，阻止低质量改动合入主分支
2. **上线前**：验证新版本是否达到发布标准（如忠实度 > 0.85、任务完成率 > 90%）
3. **生产阶段**：持续监控线上质量，及时发现检索漂移、模型退化等问题

---

## 2. 评估框架全景

当前主流的评估框架各有侧重，选择时需要根据你的场景做权衡。

| 框架 | 定位 | 核心优势 | 适用场景 | 开源 |
|------|------|---------|---------|------|
| **DeepEval** | 通用 LLM 评估框架 | 指标最全面（14+ 内置指标），CI/CD 集成，Tracing | 全栈 Agent 评估 | 是 |
| **RAGAS** | RAG 专业评估 | 学术级方法论，RAG 指标最权威 | RAG 管线质量评估 | 是 |
| **LangSmith** | 全生命周期管理 | Tracing + 评估 + 监控一体化 | 使用 LangChain 生态的团队 | 否 |
| **MLflow** | ML 平台扩展 | 与 MLflow 实验跟踪无缝集成 | 已有 MLflow 基础设施的团队 | 是 |
| **Arize Phoenix** | LLM 可观测性 | 生产监控 + 漂移检测 | 线上 Agent 监控 | 是 |

### 选择建议

- **如果你是 RAG 团队**，从 RAGAS 开始建立评估基线，再叠加 DeepEval 的 Agent 特定指标
- **如果你需要全栈评估**（RAG + Agent + 工具调用），DeepEval 是当前最全面的选择
- **如果你已经有 MLflow 基础设施**，直接在 MLflow 上扩展评估能力
- **如果你需要生产监控**，结合 DeepEval（离线评估）+ Arize Phoenix（在线监控）是最强组合

---

## 3. RAG 评估指标深度解析

RAG 评估是 Agent 评估的基础——大多数 Agent 系统依赖 RAG 提供知识，检索质量直接决定了生成质量的上限。

### 3.1 核心指标矩阵

| 指标 | 衡量对象 | 定义 | 取值范围 | 理想值 |
|------|---------|------|---------|-------|
| **Faithfulness（忠实度）** | 生成质量 | 答案中的陈述是否都能从检索到的上下文中找到证据支持 | [0, 1] | > 0.9 |
| **Answer Relevancy（答案相关性）** | 生成质量 | 答案是否与用户问题直接相关 | [0, 1] | > 0.85 |
| **Context Precision（上下文精确率）** | 检索质量 | 检索到的文档中，相关文档的比例 | [0, 1] | > 0.8 |
| **Context Recall（上下文召回率）** | 检索质量 | 所有相关文档中，被检索到的比例 | [0, 1] | > 0.8 |
| **Hallucination Rate（幻觉率）** | 生成质量 | 答案中无证据支持的陈述比例 | [0, 1] | < 0.05 |

### 3.2 指标计算原理

**Faithfulness** 是最核心的生成质量指标。其计算过程为：

1. 将答案分解为多个原子陈述（Claim Extraction）
2. 对每个陈述，检查是否能在检索上下文中找到证据支持
3. 忠实度 = 有证据支持的陈述数 / 总陈述数

```python
# Faithfulness 计算伪代码
def compute_faithfulness(answer: str, context: list[str]) -> float:
    claims = extract_claims(answer)        # 分解为原子陈述
    supported = 0
    for claim in claims:
        if any(evidence_supports(claim, ctx) for ctx in context):
            supported += 1
    return supported / len(claims) if claims else 1.0
```

**Context Precision** 衡量检索结果中"有用"内容的密度。如果 Top-5 检索结果中前 3 个都是相关的，那么 Context Precision 就高；如果相关文档排在后面，分数会被降低（因为位置衰减）。

**Context Recall** 衡量检索是否"漏掉"了重要信息。它需要一个"黄金标准"——即人工标注的全部相关文档集合，然后计算检索结果覆盖了多少。

### 3.3 RAGAS 综合评分

RAGAS 框架将四项核心指标加权平均为综合的 **RAGAS Score**：

```
RAGAS Score = (Faithfulness + Answer Relevancy + Context Precision + Context Recall) / 4
```

这是学术界和工业界广泛认可的 RAG 质量基准。但要注意：**单一数字无法反映所有问题**，建议同时查看各维度得分，定位具体瓶颈。

---

## 4. Agent 特定评估指标

对于超越 RAG 的 Agent 系统（工具调用、多步推理、多 Agent 协作），需要额外的评估维度。

### 4.1 工具调用评估

| 指标 | 定义 | 测量方法 |
|------|------|---------|
| **Tool Call Accuracy** | 工具名称和参数是否正确 | 结构化比对（JSON Schema 校验） |
| **Tool Selection Rate** | 选择了正确工具的比例 | 与预期工具对比 |
| **Parameter Correctness** | 参数值是否在合理范围内 | 类型校验 + 边界值检查 |
| **Unnecessary Tool Call** | 不应调用工具时是否调用了 | 负样本测试 |

### 4.2 任务级评估

| 指标 | 定义 | 适用场景 |
|------|------|---------|
| **Task Completion Rate** | 任务是否成功完成（含多步推理） | 端到端测试 |
| **Step Success Rate** | 多步任务中每一步的完成率 | 排查瓶颈在哪一步 |
| **Reach Goal Rate** | 是否达到最终目标状态 | 复杂长任务 |
| **Recovery Rate** | 出错后是否能自动恢复 | 鲁棒性测试 |

### 4.3 运行效率评估

| 指标 | 定义 | 关注原因 |
|------|------|---------|
| **Latency (P50/P95/P99)** | 各百分位响应时间 | 用户体验 |
| **Total Tokens per Task** | 每次任务消耗的总 token 数 | 成本控制 |
| **LLM Calls per Task** | 每个任务平均调用 LLM 次数 | 设计效率 |
| **Cost per Task** | 每次任务的经济成本 | 业务 ROI |

### 4.4 安全评估

| 指标 | 定义 | 测量方式 |
|------|------|---------|
| **Safety Score** | 是否产生有害/偏见内容 | 安全分类器、红队测试 |
| **Prompt Injection Rate** | 成功被注入攻击的比例 | 对抗性测试集 |
| **Data Leakage Rate** | 是否泄露敏感信息 | 关键词匹配 + LLM 判断 |
| **Refusal Rate** | 对不当请求的正确拒绝率 | 正样本（应拒绝）+ 负样本（不应拒绝）测试 |

---

## 5. 评估类型：三层架构

一个完整的评估体系应覆盖三个层次：组件级、端到端、在线监控。

### 5.1 组件级评估（Component-Level）

单独评估每个模块的质量，用于定位问题根因。

```
┌──────────────┐     ┌──────────────┐     ┌──────────────┐
│  检索评估     │     │  推理评估     │     │  工具评估     │
│ Context Prec. │     │ Faithfulness  │     │ Tool Acc.    │
│ Context Rec.  │     │ Relevancy     │     │ Param. Corr. │
└──────────────┘     └──────────────┘     └──────────────┘
```

**适用场景**：当端到端评估分数下降时，通过组件级评估定位是"检索没找到"还是"LLM 没用好"。

### 5.2 端到端评估（End-to-End）

模拟真实用户场景，评估完整 Agent 系统的输出质量。

```python
# 端到端评估流程
test_suite = [
    {"input": "查询本月销售额最高的三个产品", "expected": "A 产品、B 产品、C 产品"},
    {"input": "为上周的销售数据生成图表", "expected_tool": "generate_chart"},
    # ... 更多测试用例
]

for case in test_suite:
    result = agent.run(case["input"])
    score = evaluate_end_to_end(result, case["expected"])
```

**适用场景**：发布前验证、回归测试、竞品对比。

### 5.3 在线监控（Online/Production Monitoring）

在生产环境中持续监控 Agent 行为，检测质量漂移。

| 监控维度 | 检测目标 | 典型告警阈值 |
|---------|---------|-------------|
| 检索质量漂移 | 上下文精确率/召回率下降 | 连续 1 小时低于基线 10% |
| 生成质量漂移 | 忠实度/相关性下降 | 低于 0.8 持续 10 分钟 |
| 延迟异常 | P95 延迟突增 | 超过基线 2 倍标准差 |
| 错误率飙升 | 工具调用失败、LLM 超时 | 错误率 > 5% |
| 成本异常 | 单任务 token 消耗突增 | 超过基线 50% |

---

## 6. DeepEval 实战：从安装到 CI/CD

DeepEval 是目前 Agent 评估领域最实用的开源框架。下面通过一个完整的例子展示如何集成它。

### 6.1 安装与配置

```bash
pip install deepeval
```

### 6.2 定义评估指标

```python
from deepeval.metrics import (
    FaithfulnessMetric,
    AnswerRelevancyMetric,
    ContextualPrecisionMetric,
    ContextualRecallMetric,
    HallucinationMetric,
    ToxicityMetric,
)
from deepeval.test_case import LLMTestCase

# 初始化指标（默认使用 LLM 作为评判者）
faithfulness = FaithfulnessMetric(threshold=0.85)
answer_relevancy = AnswerRelevancyMetric(threshold=0.8)
context_precision = ContextualPrecisionMetric(threshold=0.8)
context_recall = ContextualRecallMetric(threshold=0.8)
hallucination = HallucinationMetric(threshold=0.1)
toxicity = ToxicityMetric(threshold=0.1)
```

### 6.3 构建测试用例

```python
# 模拟一个 Agent 的输入输出
test_case = LLMTestCase(
    input="2026 年第二季度公司的营收是多少？",
    actual_output="2026 年第二季度公司营收为 12.8 亿元，同比增长 23%，主要受 AI 业务线驱动。",
    retrieval_context=[
        "公司 2026 年 Q2 财报显示，季度营收达 12.8 亿元，同比增长 23%",
        "增长主要来自 AI 业务线，该业务线 Q2 贡献了 4.2 亿元营收",
        "公司 CFO 表示下半年将继续加大 AI 基础设施投入",
    ],
    expected_output="2026 年 Q2 公司营收 12.8 亿元，同比增长 23%",
    context=[
        "公司 2026 年 Q2 财报显示，季度营收达 12.8 亿元，同比增长 23%",
        "增长主要来自 AI 业务线，该业务线 Q2 贡献了 4.2 亿元营收",
    ],
)
```

### 6.4 运行评估

```python
from deepeval import evaluate

# 单指标评估
faithfulness.measure(test_case)
print(f"Faithfulness: {faithfulness.score:.4f}")
print(f"Reason: {faithfulness.reason}")

# 批量评估
results = evaluate(
    test_cases=[test_case],
    metrics=[faithfulness, answer_relevancy, context_precision, context_recall],
)
print(f"Overall Score: {results.test_run_configuration}")
```

### 6.5 集成测试套件

```python
from deepeval.test_run import test_run

# 构建完整的测试套件
test_cases = [
    LLMTestCase(
        input="公司有多少员工？",
        actual_output="截至 2026 年 Q2，公司共有 3,200 名员工。",
        retrieval_context=["2026 年 Q2 员工总数：3,200 人"],
        expected_output="3,200 名员工",
    ),
    LLMTestCase(
        input="上个月的用户活跃度如何？",
        actual_output="上个月 DAU 为 520 万，MAU 为 1,800 万。",
        retrieval_context=["上个月 DAU 520 万，MAU 1,800 万"],
        expected_output="DAU 520 万，MAU 1,800 万",
    ),
    # 更多测试用例...
]

# 使用 test_run 上下文管理器运行并记录
with test_run(name="Agent RAG Evaluation v2.3"):
    evaluate(
        test_cases=test_cases,
        metrics=[
            FaithfulnessMetric(),
            AnswerRelevancyMetric(),
            ContextualPrecisionMetric(),
            ContextualRecallMetric(),
        ],
    )
```

### 6.6 CI/CD 集成

将评估结果作为合入门禁，是保证 Agent 质量的关键实践。

```yaml
# .github/workflows/agent-eval.yml
name: Agent Evaluation

on:
  pull_request:
    branches: [main]

jobs:
  evaluate:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: Set up Python
        uses: actions/setup-python@v5
        with:
          python-version: "3.11"
      - name: Install dependencies
        run: |
          pip install deepeval
          pip install -r requirements.txt
      - name: Run evaluation
        env:
          OPENAI_API_KEY: ${{ secrets.EVALUATOR_API_KEY }}
        run: |
          deepeval test run tests/agent_eval.py
      - name: Check metrics thresholds
        run: |
          python -c "
          import json
          with open('.deepeval/test_run.json') as f:
              results = json.load(f)
          thresholds = {
              'faithfulness': 0.85,
              'answer_relevancy': 0.80,
              'context_precision': 0.80,
              'context_recall': 0.75,
          }
          for metric_name, threshold in thresholds.items():
              score = results['metrics'].get(metric_name, {}).get('score', 0)
              if score < threshold:
                  print(f'FAIL: {metric_name} = {score:.3f} < {threshold}')
                  exit(1)
              print(f'PASS: {metric_name} = {score:.3f} >= {threshold}')
          "
```

**门禁策略建议**：

| 阶段 | 指标 | 门禁阈值 | 违规后果 |
|------|------|---------|---------|
| PR 级别 | Faithfulness, Answer Relevancy | > 0.80 | 禁止合入 |
| 预发布 | 全部 4 项 RAG 指标 | > 0.85 | 阻止上线 |
| 生产监控 | 各项指标漂移 | 不超过基线 10% | 自动回滚 |

---

## 7. 生产监控与告警

离线评估保障了"上线前的质量"，但上线后的质量波动同样需要关注。

### 7.1 监控架构

```
┌──────────────┐      ┌──────────────┐      ┌──────────────┐
│  Agent 日志流  │ ──▶ │  评估流水线    │ ──▶ │  告警系统     │
│  (实时)        │      │  (分钟级采样)  │      │  (PagerDuty)  │
└──────────────┘      └──────────────┘      └──────────────┘
                            │
                            ▼
                     ┌──────────────┐
                     │  指标存储     │
                     │  (Prometheus) │
                     └──────────────┘
                            │
                            ▼
                     ┌──────────────┐
                     │  可视化面板   │
                     │  (Grafana)   │
                     └──────────────┘
```

### 7.2 关键告警规则

```python
# 告警配置示例
ALERT_RULES = {
    "quality_degradation": {
        "condition": "faithfulness_rolling_avg_5min < 0.80",
        "severity": "critical",
        "action": "notify_oncall + auto_rollback",
    },
    "latency_spike": {
        "condition": "p95_latency_5min > 10_000",  # 毫秒
        "severity": "warning",
        "action": "notify_engineering",
    },
    "error_rate": {
        "condition": "tool_call_error_rate_5min > 0.05",
        "severity": "critical",
        "action": "notify_oncall + disable_tool",
    },
    "cost_anomaly": {
        "condition": "cost_per_task_1h > baseline_1h * 1.5",
        "severity": "warning",
        "action": "notify_engineering",
    },
}
```

### 7.3 仪表盘设计

一个生产级的 Agent 监控仪表盘至少应包含以下面板：

| 面板 | 展示内容 | 刷新频率 |
|------|---------|---------|
| **质量概览** | 忠实度/相关性/幻觉率的滚动平均值 | 1 分钟 |
| **检索质量** | 上下文精确率/召回率趋势 | 5 分钟 |
| **延迟分布** | P50/P95/P99 延迟 + 超时率 | 1 分钟 |
| **错误看板** | 工具调用失败率、LLM 错误分类 | 实时 |
| **成本追踪** | 日/周/月成本趋势、各模型成本占比 | 1 小时 |
| **安全事件** | 注入攻击、数据泄露、有害内容 | 实时 |

---

## 8. 如何选择评估框架

没有"最好"的框架，只有"最适合当前阶段"的框架。

| 团队阶段 | 推荐方案 | 理由 |
|---------|---------|------|
| **起步期**（原型验证） | DeepEval + 5 个核心指标 | 快速搭建评估基线，零成本起步 |
| **成长期**（RAG 优化） | DeepEval + RAGAS | RAGAS 的学术指标做深度诊断，DeepEval 做 CI/CD 集成 |
| **成熟期**（生产上线） | DeepEval + Arize Phoenix | 离线评估 + 在线监控，全链路覆盖 |
| **企业级**（全平台） | LangSmith / MLflow | 统一管理评估、Tracing、监控、成本 |

### 迁移路径

```
DeepEval（独立） → DeepEval + RAGAS（RAG 深度优化） → DeepEval + Arize（生产监控）
```

---

## 9. 最佳实践总结

### 9.1 评估集建设

- **质量重于数量**：50 个高质量的人工标注用例优于 500 个自动生成的噪声用例
- **覆盖错误模式**：包含正常用例、边界用例、恶意用例（注入攻击测试）
- **版本管理**：评估集纳入 Git 管理，每次 Agent 改动跑回归

### 9.2 指标选择

- **不要贪多**：核心指标 5-6 个足以覆盖大部分场景
- **分层关注**：组件级指标定位问题，端到端指标衡量整体效果
- **跟踪趋势**：单次数值波动意义有限，连续趋势才有决策价值

### 9.3 评估流程

- **先评估，再优化**：任何改动前先跑评估，建立基线
- **PR 门禁**：评估不通过，不允许合入主分支
- **灰度发布**：新版本先灰度 10% 流量，观察指标后再全量

### 9.4 常见陷阱

| 陷阱 | 说明 | 避免方法 |
|------|------|---------|
| **评估者偏差** | 用 GPT-4 评估 GPT-4 的输出会产生偏差 | 使用不同的 LLM 作为评估者（如 Claude 评估 GPT 输出） |
| **指标对齐失败** | 离线指标与线上体验不一致 | 定期做人工抽检校准，建立用户反馈闭环 |
| **测试集污染** | 模型训练数据包含了测试用例 | 使用生产实际数据构建测试集，而非公开数据集 |
| **过度拟合指标** | 为了提高指标数值而过度调参 | 定期更换测试集，引入对抗性测试用例 |

---

## 总结

Agent 评估不是一个可选的"锦上添花"，而是生产级 Agent 系统的**基础设施**。没有评估，你就无法知道一次 Prompt 修改、一个检索参数调整、一个新工具的引入到底是让系统变好还是变坏。

核心要点：

1. **框架选择**：DeepEval 是当前最全面的开源评估框架，适合作为评估基础设施的核心
2. **指标分层**：用 RAGAS 指标评估检索质量，用 Agent 特定指标评估工具调用和任务完成
3. **三层架构**：组件级 + 端到端 + 在线监控，覆盖开发到生产的全链路
4. **CI/CD 集成**：将评估门禁嵌入 PR 流程，用自动化保障质量基线
5. **持续监控**：生产环境的质量漂移检测是最终防线，不可忽视

### 参考资源

- [DeepEval 官方文档](https://docs.confident-ai.com/)
- [RAGAS 论文与框架](https://github.com/explodinggradients/ragas)
- [LangSmith 评估文档](https://docs.smith.langchain.com/)
- [MLflow LLM Evaluation](https://mlflow.org/docs/latest/llms/index.html)
- [Arize Phoenix](https://github.com/Arize-AI/phoenix)
- 本系列：[Prompt Engineering 实战](./01-prompt-engineering-guide.md) | [AI Agent 设计模式](./02-agent-design-patterns.md) | [生产级 AI Agent 系统设计](./03-production-system-design.md) | [LangGraph 编排实战](./04-langgraph-orchestration.md) | [高级 RAG 实战](./05-advanced-rag-systems.md)
- 仓库参考：[ai-system-design-guide](../../repositories/ombharatiya_ai-system-design-guide.md) | [deepeseek-harness-guide](../../repositories/flaqai_deepeseek-harness-guide.md) | [claude-code-guide](../../repositories/zebbern_claude-code-guide.md)