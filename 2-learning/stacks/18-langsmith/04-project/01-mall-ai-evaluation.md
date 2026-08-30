# 项目实战：商城 RAG 评测与可观测落地

> 目标：把 LangSmith 的完整体系（Tracing → 评测 → 反馈闭环 → CI 回归）落地到"电商自然语言 AI 智能检索系统"（mall-ai-search）项目，形成"面试能讲 + 生产能跑"的项目实战能力。
> 场景：商品 RAG 问答——检索商品库回答"性价比高的拍照手机""这款支持 5G 吗"等。

---

## 一、项目背景与痛点

mall-ai-search 是 4 人实训项目（AI 模块负责人独立完成 AI 链路）：LangChain/FastAPI 服务 + RedisStack 向量检索 + 商品 RAG。核心痛点：

| 痛点 | 没有 LangSmith 时 |
|------|------------------|
| 回答错了不知道哪一步错的 | 靠 print 日志大海捞针 |
| 模型幻觉编造价格/规格 | 只能人肉抽查 |
| 换了 prompt 不知道好坏 | 凭感觉上线 |
| 用户投诉无数据支撑 | 无反馈沉淀 |

**LangSmith 的价值落位**：让这条链路"可观测、可评测、可回归"。

---

## 二、第一步：接入 Tracing（10 分钟）

```bash
# .env
LANGCHAIN_TRACING_V2=true
LANGCHAIN_API_KEY=lsv2_xxx
LANGCHAIN_PROJECT=mall-ai-rag
```

业务代码零侵入，所有 LLM / 检索 / 工具调用自动上报。FastAPI 接口：

```python
from fastapi import FastAPI
from langchain_openai import ChatOpenAI

app = FastAPI()

@app.post("/api/v1/ask")
def ask(question: str):
    return {"answer": rag_chain.invoke({"question": question}).content}
```

每个 `/ask` 请求在控制台生成一条完整 Trace，瀑布视图能看到检索 → 生成 → 解析全链路。

---

## 三、第二步：建立评测集（从真实坏例开始）

从生产 trace 的负反馈 / 空召回样本沉淀评测集——**评测集一开始就该覆盖"曾经错过的"**：

```python
from langsmith import Client

client = Client()
dataset = client.create_dataset(
    dataset_name="mall-qa-v1",
    description="商城问答评测集：推荐/参数/售后/边界四类",
)

bad_cases = [
    # (问题, 期望行为, 标签)
    ("性价比高的拍照手机", "推荐 3 款并说明理由", "推荐类"),
    ("iPhone 支持 5G 吗", "查询参数确认，不凭印象", "参数类"),
    ("你们能开发票吗", "引导售后流程，不虚构政策", "售后类"),
    ("（空输入）", "礼貌提示重新提问", "边界类"),
]
for q, expected, tag in bad_cases:
    client.create_example(
        inputs={"question": q},
        outputs={"expected": expected},
        metadata={"category": tag},
        dataset_id=dataset.id,
    )
```

> 面试可讲：评测集"四类覆盖 + 从坏例起步"的设计，体现工程意识。

---

## 四、第三步：跑评测实验（换模型 / 换 prompt 用数据说话）

```python
from langsmith import evaluate
from langsmith.evaluation import LangChainStringEvaluator
from langchain_openai import ChatOpenAI
from langchain_core.prompts import ChatPromptTemplate

# 目标函数（考生）
prompt = ChatPromptTemplate.from_messages([
    ("system", "你是商城客服。仅基于提供的商品资料回答，资料不足就说明，禁止编造价格/规格。"),
    ("human", "{question}"),
])
llm = ChatOpenAI(model="gpt-4o-mini")
chain = prompt | llm

def predict(inputs: dict) -> dict:
    return {"answer": chain.invoke({"question": inputs["question"]}).content}

# 三个评估器组合：规则 + RAG 忠实度 + LLM 裁判
def has_answer(run, example=None):
    return {"key": "has_answer", "score": 1 if run.outputs.get("answer") else 0}

faithfulness = LangChainStringEvaluator("rag_answer_faithfulness")  # 忠实度（防幻觉）
llm_judge = LangChainStringEvaluator("criteria", config={
    "criteria": "回答是否切题且基于资料",
    "llm": ChatOpenAI(model="gpt-4o-mini"),
})

results = evaluate(
    predict,
    data="mall-qa-v1",
    evaluators=[has_answer, faithfulness, llm_judge],
    experiment_prefix="mall-v1-gpt4o-mini",
)
```

**决策场景**：想换 `gpt-4o` 或调检索 top-k → 换参数再跑一次 → 对比矩阵看分数与成本，**用数据拍板而不是感觉**。

---

## 五、第四步：反馈闭环（生产负反馈 → 评测集）

### 后端记录用户反馈

```python
@app.post("/api/v1/feedback")
def feedback(run_id: str, score: int, comment: str = ""):
    client.create_feedback(
        run_id=run_id,
        key="user_reaction",
        score=score,           # 1 赞 / 0 踩
        comment=comment,
    )
```

### Automation 自动沉淀

配置一条 Automation：`user_reaction < 0.5` → 加入 Annotation Queue 人工复核 → 确认后一键入 Dataset。**下一次评测就覆盖了这个坏例**——飞轮转起来了。

---

## 六、第五步：CI 持续评测（防回归）

把评测变成合并门禁：

```bash
# ci/eval_mall.py —— 在 CI 中跑
from langsmith import evaluate

results = evaluate(predict, data="mall-qa-v1", evaluators=[...])
# 与 baseline 对比，平均分下降超阈值 → 失败退出，阻断合并
if results.agg_scores["faithfulness"] < baseline - 0.05:
    raise SystemExit("忠实度回归，阻断合并")
```

```yaml
# .github/workflows/llm-eval.yml
name: LLM Eval Gate
on: [pull_request]
jobs:
  evaluate:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - run: pip install -r requirements.txt
      - run: python ci/eval_mall.py
        env:
          LANGCHAIN_API_KEY: ${{ secrets.LANGCHAIN_API_KEY }}
```

**效果**：prompt / 模型 / 检索参数的改动必须过评测门禁——LLM 质量第一次变成"可自动验证的工程约束"。

---

## 七、面试怎么讲这个项目

> **一句话总结**：我把商品 RAG 从"调 API 的 demo"升级成"可观测、可评测、可回归"的工程——接入 LangSmith 零侵入追踪全链路；从生产坏例沉淀 4 类评测集；用「规则 + 忠实度裁判 + LLM 裁判」三评估器组合量化回答质量；再通过 Feedback + Automation 把用户负反馈自动回流评测集，形成改进飞轮；最后把评测接进 CI 门禁，prompt/模型改动必须过质量线才能合并。

**面试追问应对**：
- 问"评测集哪来的" → 生产坏例沉淀 + 四类覆盖
- 问"忠实度评估器怎么工作" → LLM 裁判对照"回答 vs 检索证据"打分
- 问"换了模型怎么验证" → 同数据集跑对比实验看分数 + 成本
- 问"评测成本怎么控" → 抽样 + 便宜裁判模型 + 规则评估器兜底

---

## 八、小结

- **链路**：Tracing（看到）→ 评测集（测出）→ 三评估器（量化）→ 反馈闭环（飞轮）→ CI 门禁（守线）
- **工程观**：评测集是"活"的、模型选择用数据说话、LLM 质量可自动回归
- **下一步**：见 [02-agri-hallucination.md](02-agri-hallucination.md)，用 LangSmith 落地农业知识库的幻觉防控。