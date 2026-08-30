# 项目实战：农业知识库问答 · 幻觉防控与可观测

> 目标：用 LangSmith 把"农业知识库问答智能体"（个人项目）的**幻觉防控**做成可量化、可回归的工程能力。
> 场景背景：农业知识库问答智能体用 LangChain 双工具 Agent（私有知识库优先 + 通用知识兜底）回答农技问题，核心痛点是**通用大模型会编造农技方案**（如"喷洒 XX 农药"其实是幻觉）。
> 完整项目资料：`5-research/tech-stack-analysis/` 农业知识库问答分析。

---

## 一、项目痛点与防幻觉目标

| 痛点 | 说明 |
|------|------|
| 农技方案不可编造 | 编造施肥/用药方案可能误导农户造成损失 |
| 知识库优先 | 有农技资料时必须基于资料回答 |
| 兜底可识别 | 知识库查不到时，明说"资料不足"，不硬编 |

**LangSmith 落位**：把"防幻觉"从口号变成三件可执行的事——**看得见（Trace）、量得出（忠实度评测）、守得住（CI 门禁）**。

---

## 二、防幻觉的工程化拆解

### 1. 看得见：Trace 追踪"资料是否被用上"

瀑布视图看每个回答的链路：

- 检索节点是否命中知识库（空召回 → 隐患）
- 系统提示词是否完整（约束"仅基于资料"）
- 兜底分支是否被正确触发（低分 → 拒答/说明）

> **面试讲法**："我的防幻觉第一层是'看得见'——每个回答都有完整 trace，回答可疑时我一眼能看出是空召回还是 prompt 被绕过。"

### 2. 量得出：忠实度评估器（LLM-as-Judge）

用 LangSmith 官方 `rag_answer_faithfulness` 评估器，量化"回答是否忠于检索证据"：

```python
from langsmith.evaluation import LangChainStringEvaluator

faithfulness = LangChainStringEvaluator("rag_answer_faithfulness")

# 示例：某回答"用 10% 阿维菌素喷洒"但资料只有"阿维菌素浓度需按说明"
# 裁判判定：事实不符 → 忠实度低分 → 这就是一次幻觉被抓现行
```

评测集专门包含**幻觉坏例**：

```python
hallucination_cases = [
    # (问题, 期望行为)
    ("水稻稻瘟病怎么防治", "必须引用知识库中的防治方案，不得自行编造用药"),
    ("花生缺氮什么症状", "按资料回答症状与施肥建议，资料没有就说明"),
    ("多菌灵能治根腐病吗", "先查资料，查不到明确说明"资料未收录"而非直接回答"),
]
```

**效果**：每次改 prompt / 换模型后跑一遍，忠实度分数**量化了幻觉率变化**——"这次改动让幻觉下降了 X%"变成可陈述的成果。

### 3. 守得住：幻觉门禁进 CI

```python
# ci/eval_agri.py
results = evaluate(agri_predict, data="agri-qa-hallucination", evaluators=[faithfulness])
if results.agg_scores["faithfulness"] < 0.90:   # 忠实度红线
    raise SystemExit("忠实度低于 90%，防幻觉门禁拦截")
```

> 农技场景可设"硬红线"（忠实度必须 ≥ 0.9），因为幻觉代价高——这体现领域判断。

---

## 三、幻觉自动发现：在线评估 + 反馈

生产环境不等人投诉，让系统自己发现问题：

```python
# FastAPI 回答后触发在线评估（抽样）
from langsmith.evaluation import LangChainStringEvaluator

def auto_check(run_id: str, question: str, answer: str, context: str):
    judge = LangChainStringEvaluator(
        "rag_answer_faithfulness",
        config={"llm": judge_llm},   # 便宜裁判模型
    )
    score = judge.evaluate_strings(
        prediction=answer,
        input=question,
        reference=context,           # 检索证据
    )
    client.create_feedback(run_id=run_id, key="faithfulness", score=score)

    # 忠实度低 → 自动入标注队列人工复核
    if score < 0.6:
        client.add_run_to_annotation_queue(
            run_id=run_id,
            queue_id="agri-hallucination-review",
        )
```

**自动化规则**：`faithfulness < 0.6` → 入队复核 → 确认后入评测集 → 下一次评测覆盖新坏例。**幻觉防控是持续进化的，不是一次配好。**

---

## 四、效果数据（可讲的成果口径）

| 维度 | 落地动作 | 可量化成果 |
|------|---------|-----------|
| 可观测 | 全链路 Trace | 空召回 / prompt 异常秒定位 |
| 评测 | 忠实度评估器 + 幻觉坏例集 | 幻觉率从"人肉抽查"变成"量化分数" |
| 回归 | CI 忠实度门禁 ≥ 0.9 | prompt / 模型改动必须过线 |
| 闭环 | 在线评估 + Automation | 生产幻觉自动入队 → 回流评测集 |

---

## 五、小结与面试讲法

> **一句话总结**：农业知识库问答的防幻觉，我拆成三层工程动作——**看得见**（LangSmith 全链路 trace，空召回/兜底一目了然）、**量得出**（忠实度评估器 + 幻觉坏例评测集，把幻觉率变成数字）、**守得住**（CI 忠实度 ≥ 0.9 门禁，prompt/模型改动必须过线才能合并），再用在线评估 + 自动化规则把生产幻觉自动回流评测集，形成持续进化的闭环。

**追问应对**：
- "评估器会不会误判" → 忠实度裁判对照"回答 vs 检索证据"，加上人工抽样复核兜底
- "门禁会不会太严" → 农技场景幻觉代价高，设 0.9 红线是领域判断；日常场景可 0.8
- "成本" → 在线评估抽样 + 便宜裁判模型，只有低分样本才入队人工复核

---

## 六、下一步

进入 [05-interview/quick-revision.md](../05-interview/quick-revision.md) —— LangSmith 面试速记；或直接刷《LangChain_LangGraph_LangSmith_面试QA_详解版.md》第三章。