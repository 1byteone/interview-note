# LangChain 评估与回归测试

> 面向 Python 后端开发者的 LangChain 评估教程，覆盖数据集创建、评估指标、回归测试与生产监控，确保 LLM 应用的质量与稳定性。

---

## 1. 为什么需要评估？

LLM 应用的输出具有**不确定性**，同样的输入可能得到不同的输出。没有评估体系，就无法回答以下问题：

- 升级模型后，回答质量是提升还是下降了？
- Prompt 修改后，会不会影响其他场景？
- 新增加的工具是否被 Agent 正确调用？
- 系统是否存在幻觉或安全漏洞？

评估体系是 LLM 应用从"能跑"到"可靠"的关键。

---

## 2. LangChain 评估框架

LangChain 提供 `langchain.evaluation` 模块，支持多种评估方式：

| 评估类型 | 方法 | 说明 |
|----------|------|------|
| **对比评估** | `pairwise` | 比较两个输出的优劣 |
| **单输出评估** | `criteria` | 按标准评估单个输出 |
| **嵌入距离** | `embedding_distance` | 语义相似度 |
| **字符串距离** | `string_distance` | 文本相似度 |
| **QA 评估** | `qa` | 问答对正确性 |
| **Agent 评估** | `trajectory` | Agent 工具调用轨迹 |

### 2.1 安装

```bash
pip install langchain langchain-openai langchain-community
```

### 2.2 基础评估示例

```python
from langchain.evaluation import load_evaluator

# 加载评估器
evaluator = load_evaluator(
    "criteria",
    criteria="correctness",  # 评估标准：correctness / helpfulness / conciseness / harmlessness
)

# 评估
result = evaluator.evaluate_strings(
    prediction="iPhone 15 是苹果公司于2023年9月发布的智能手机。",
    reference="iPhone 15 是苹果公司于2023年9月发布的旗舰手机，搭载A16芯片。",
    input="iPhone 15 是什么？",
)

print(f"分数：{result['score']}")
print(f"理由：{result['reasoning']}")
```

---

## 3. 数据集创建

### 3.1 手动创建数据集

```python
from langchain.evaluation import StringEvaluator
from typing import Optional

# 测试数据集：AI 商城客服场景
test_cases = [
    {
        "input": "iPhone 15 多少钱？",
        "expected_output": "iPhone 15 128GB 售价 ￥5999",
        "expected_tools": ["search_product"],
    },
    {
        "input": "我的订单 O20240801 到哪了？",
        "expected_output": "已发货，预计 3 天后送达",
        "expected_tools": ["query_order"],
    },
    {
        "input": "我要退货，怎么操作？",
        "expected_output": "您可以在订单页面申请退货，或联系客服",
        "expected_tools": ["create_return_request"],
    },
    {
        "input": "你们几点上班？",
        "expected_output": "客服工作时间 9:00-21:00",
        "expected_tools": [],  # 不需要工具
    },
    {
        "input": "帮我写一首诗",  # 超出范围的请求
        "expected_output": "我是商城客服助手，无法帮您写诗",
        "expected_tools": [],
    },
]
```

### 3.2 从 LangSmith 导入数据集

```python
from langsmith import Client

# 从 LangSmith 获取已有数据集
client = Client()
dataset = client.get_dataset("ai-mall-customer-service")

# 遍历数据集
for example in dataset:
    print(f"输入：{example.inputs['input']}")
    print(f"期望输出：{example.outputs['output']}")
    print("---")
```

### 3.3 使用 CSV 数据集

```python
import csv
from typing import List, Dict

def load_test_dataset(csv_path: str) -> List[Dict]:
    """从 CSV 加载测试数据集"""
    test_cases = []
    with open(csv_path, "r", encoding="utf-8") as f:
        reader = csv.DictReader(f)
        for row in reader:
            test_cases.append({
                "input": row["input"],
                "expected_output": row["expected_output"],
                "expected_tools": row.get("expected_tools", "").split(","),
            })
    return test_cases

# CSV 格式示例：
# input,expected_output,expected_tools
# "iPhone 15 多少钱？","iPhone 15 128GB 售价 ￥5999","search_product"
# "我的订单到哪了？","已发货，预计3天后送达","query_order"
```

---

## 4. 评估指标

### 4.1 准确率（Exact Match）

```python
from langchain.evaluation import load_evaluator

def evaluate_exact_match(predictions: List[str], references: List[str]) -> float:
    """计算精确匹配率"""
    correct = sum(1 for p, r in zip(predictions, references) if r in p)
    return correct / len(predictions)

# 示例
predictions = [
    "iPhone 15 售价 ￥5999",
    "已发货，预计3天后送达",
    "您可以在订单页面申请退货",
]
references = [
    "iPhone 15 128GB 售价 ￥5999",
    "已发货，预计 3 天后送达",
    "您可以在订单页面申请退货，或联系客服",
]

accuracy = evaluate_exact_match(predictions, references)
print(f"准确率：{accuracy:.2%}")  # 输出：约 66.67%
```

### 4.2 语义相似度（Semantic Similarity）

使用嵌入向量计算语义相似度，对语义相同但措辞不同的输出更友好。

```python
from langchain.evaluation import load_evaluator
from langchain_openai import OpenAIEmbeddings

def evaluate_semantic_similarity(
    predictions: List[str],
    references: List[str],
) -> List[float]:
    """计算语义相似度分数"""
    evaluator = load_evaluator(
        "embedding_distance",
        embeddings=OpenAIEmbeddings(model="text-embedding-3-small"),
    )

    scores = []
    for pred, ref in zip(predictions, references):
        result = evaluator.evaluate_strings(
            prediction=pred,
            reference=ref,
        )
        # embedding_distance 返回的是距离，越小越相似
        # 转换为相似度分数（0-1）
        similarity = 1 - result["score"]
        scores.append(similarity)

    return scores

scores = evaluate_semantic_similarity(predictions, references)
avg_score = sum(scores) / len(scores)
print(f"平均语义相似度：{avg_score:.2%}")
```

### 4.3 工具调用准确率

对于 Agent 场景，需要评估工具选择的正确性。

```python
from langchain.evaluation import load_evaluator

class AgentTrajectoryEvaluator:
    """评估 Agent 的轨迹质量"""

    def __init__(self):
        self.evaluator = load_evaluator("trajectory")

    def evaluate_tool_accuracy(
        self,
        actual_tools: List[str],
        expected_tools: List[str],
    ) -> dict:
        """计算工具调用的准确率和召回率"""
        actual_set = set(actual_tools)
        expected_set = set(expected_tools)

        if not expected_set:
            # 如果期望不调用工具，但实际调用了，视为错误
            return {
                "precision": 1.0 if not actual_set else 0.0,
                "recall": 1.0 if not actual_set else 0.0,
                "f1": 1.0 if not actual_set else 0.0,
            }

        true_positives = actual_set & expected_set
        precision = len(true_positives) / len(actual_set) if actual_set else 0
        recall = len(true_positives) / len(expected_set) if expected_set else 0
        f1 = 2 * precision * recall / (precision + recall) if (precision + recall) > 0 else 0

        return {
            "precision": precision,
            "recall": recall,
            "f1": f1,
        }

# 使用示例
evaluator = AgentTrajectoryEvaluator()
result = evaluator.evaluate_tool_accuracy(
    actual_tools=["search_product", "query_order"],
    expected_tools=["search_product", "query_order", "check_stock"],
)
print(result)  # precision=1.0, recall=0.67, f1=0.8
```

### 4.4 综合评估指标

```python
from dataclasses import dataclass, field
from typing import List

@dataclass
class EvaluationReport:
    """评估报告"""
    exact_match: float = 0.0
    semantic_similarity: float = 0.0
    tool_precision: float = 0.0
    tool_recall: float = 0.0
    tool_f1: float = 0.0
    avg_response_time_ms: float = 0.0
    token_usage: int = 0
    error_rate: float = 0.0
    details: List[dict] = field(default_factory=list)

    def summary(self) -> str:
        return (
            f"评估报告\n"
            f"{'='*40}\n"
            f"精确匹配率：{self.exact_match:.2%}\n"
            f"语义相似度：{self.semantic_similarity:.2%}\n"
            f"工具准确率：{self.tool_precision:.2%}\n"
            f"工具召回率：{self.tool_recall:.2%}\n"
            f"工具 F1：{self.tool_f1:.2%}\n"
            f"平均响应：{self.avg_response_time_ms:.1f}ms\n"
            f"Token 消耗：{self.token_usage}\n"
            f"错误率：{self.error_rate:.2%}"
        )
```

---

## 5. 回归测试

回归测试确保系统修改（Prompt 更新、模型升级、工具变更）不会破坏已有功能。

### 5.1 回归测试框架

```python
import time
import json
from datetime import datetime
from pathlib import Path
from typing import Callable, List, Dict

class RegressionTestSuite:
    """回归测试套件"""

    def __init__(self, agent_fn: Callable, test_cases: List[Dict]):
        self.agent_fn = agent_fn
        self.test_cases = test_cases
        self.results = []

    def run(self) -> EvaluationReport:
        """运行所有测试用例"""
        report = EvaluationReport()
        total_time = 0
        errors = 0
        all_tools = []
        all_expected_tools = []

        for i, case in enumerate(self.test_cases):
            try:
                start = time.perf_counter()
                result = self.agent_fn(case["input"])
                elapsed = time.perf_counter() - start

                total_time += elapsed
                prediction = result["output"]
                actual_tools = result.get("intermediate_steps", [])

                # 记录详细结果
                detail = {
                    "case_id": i,
                    "input": case["input"],
                    "prediction": prediction,
                    "expected": case["expected_output"],
                    "actual_tools": [s[0].tool for s in actual_tools],
                    "expected_tools": case["expected_tools"],
                    "response_time_ms": elapsed * 1000,
                    "passed": case["expected_output"] in prediction,
                }
                self.results.append(detail)
                all_tools.append(detail["actual_tools"])
                all_expected_tools.append(case["expected_tools"])

                if not detail["passed"]:
                    errors += 1

            except Exception as e:
                errors += 1
                self.results.append({
                    "case_id": i,
                    "input": case["input"],
                    "error": str(e),
                    "passed": False,
                })

        # 汇总指标
        n = len(self.test_cases)
        passed = [r for r in self.results if r.get("passed")]
        report.exact_match = len(passed) / n if n > 0 else 0
        report.avg_response_time_ms = (total_time / n) * 1000 if n > 0 else 0
        report.error_rate = errors / n if n > 0 else 0

        return report

    def save_report(self, path: str = "regression_report.json"):
        """保存测试报告"""
        report_data = {
            "timestamp": datetime.now().isoformat(),
            "total_cases": len(self.test_cases),
            "passed": sum(1 for r in self.results if r.get("passed")),
            "failed": sum(1 for r in self.results if not r.get("passed")),
            "details": self.results,
        }
        Path(path).write_text(
            json.dumps(report_data, ensure_ascii=False, indent=2),
            encoding="utf-8",
        )
        print(f"报告已保存：{path}")
```

### 5.2 使用示例

```python
# 假设这是我们的 Agent
def my_agent(input_text: str) -> dict:
    """模拟 Agent 调用"""
    # 实际场景中这里调用 AgentExecutor
    responses = {
        "iPhone 15 多少钱？": {
            "output": "iPhone 15 128GB 售价 ￥5999",
            "intermediate_steps": [("search_product", {})],
        },
        "我的订单 O20240801 到哪了？": {
            "output": "已发货，预计 3 天后送达",
            "intermediate_steps": [("query_order", {})],
        },
    }
    return responses.get(input_text, {
        "output": "抱歉，我不理解您的问题",
        "intermediate_steps": [],
    })

# 运行回归测试
suite = RegressionTestSuite(my_agent, test_cases)
report = suite.run()
print(report.summary())
suite.save_report()
```

### 5.3 CI/CD 集成

```python
# regression_test.py — 可在 CI 流水线中运行
import sys

def main():
    # 加载 Agent
    from your_app.agent import create_agent
    agent = create_agent()

    # 加载测试数据集
    test_cases = load_test_dataset("tests/data/regression_cases.csv")

    # 运行回归测试
    suite = RegressionTestSuite(agent.invoke, test_cases)
    report = suite.run()

    # 输出报告
    print(report.summary())

    # 设置阈值，失败时退出非零
    THRESHOLD_EXACT_MATCH = 0.85
    THRESHOLD_ERROR_RATE = 0.05

    if report.exact_match < THRESHOLD_EXACT_MATCH:
        print(f"FAIL: 准确率 {report.exact_match:.2%} 低于阈值 {THRESHOLD_EXACT_MATCH:.2%}")
        sys.exit(1)

    if report.error_rate > THRESHOLD_ERROR_RATE:
        print(f"FAIL: 错误率 {report.error_rate:.2%} 高于阈值 {THRESHOLD_ERROR_RATE:.2%}")
        sys.exit(1)

    print("PASS: 所有指标通过")
    sys.exit(0)

if __name__ == "__main__":
    main()
```

---

## 6. 生产环境监控

### 6.1 在线评估

```python
from langchain.callbacks.base import BaseCallbackHandler

class OnlineEvaluationCallback(BaseCallbackHandler):
    """生产环境在线评估"""

    def __init__(self, threshold: float = 0.7):
        self.threshold = threshold
        self.evaluator = load_evaluator("criteria", criteria="helpfulness")

    def on_llm_end(self, response, **kwargs):
        """实时评估 LLM 输出质量"""
        generation = response.generations[0][0].text
        if len(generation) < 10:  # 输出过短，可能有问题
            print(f"[WARN] 输出过短：{generation}")

    def on_chain_end(self, outputs, **kwargs):
        """评估 Chain 输出"""
        output = outputs.get("output", "")
        if isinstance(output, str):
            result = self.evaluator.evaluate_strings(
                prediction=output,
                input=kwargs.get("inputs", {}).get("input", ""),
            )
            if result["score"] < self.threshold:
                print(f"[ALERT] 低质量输出：{result['reasoning']}")
```

### 6.2 评估仪表盘

结合 LangSmith 的评估功能，可以构建在线评估仪表盘：

```python
from langsmith import Client
from langsmith.evaluation import evaluate

# 在 LangSmith 上运行评估
client = Client()

# 将数据集与运行结果对比
evaluate(
    dataset="ai-mall-customer-service",
    llm_or_chain_factory=create_agent,
    evaluators=[
        load_evaluator("criteria", criteria="correctness"),
        load_evaluator("criteria", criteria="helpfulness"),
    ],
    experiment_prefix="v2.1.0-regression",
)
```

---

## 总结

- **评估体系**是 LLM 应用从原型到生产的必经之路，覆盖准确率、语义相似度、工具调用轨迹等维度
- **数据集创建**支持手动构造、LangSmith 导入、CSV 加载等多种方式
- **回归测试**确保系统变更不会破坏已有功能，建议集成到 CI/CD 流水线
- **生产监控**通过在线评估和 LangSmith 仪表盘，持续跟踪输出质量
- 推荐设置明确的评估阈值（如准确率 >= 85%，错误率 <= 5%），作为质量门禁

---

> 上一篇：[03-langgraph-intro.md](./03-langgraph-intro.md) — LangGraph 核心概念与状态机实战