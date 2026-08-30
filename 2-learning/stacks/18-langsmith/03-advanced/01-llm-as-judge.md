# LLM-as-Judge 与自定义评估器（进阶技巧）

> 目标：掌握 LLM-as-Judge（LLM 裁判）的原理与校准方法，会写自定义评估器，理解"代码规则 + 裁判 + 人工"三层互补。
> 这是评测体系中最难也最高频的考点。

---

## 一、LLM-as-Judge 是什么

**LLM-as-Judge**：用一个大模型（裁判）给另一个模型（考生）的输出评分。

适用场景：**无标准答案**的语义评价——回答的相关性、忠实度、有用性、风格、安全性。

为什么需要它？代码规则只能验证"格式 / 实体 / 长度"，但"这个回答是否切题、是否基于证据、是否对用户有用"只有语义理解能判断——这正好是 LLM 的强项。

---

## 二、裁判的两种部署形态

| 形态 | 位置 | 典型用法 |
|------|------|---------|
| 离线评测（Offline Evaluation） | 评测实验的评估器 | 在 Dataset 上批量打分，对比模型 / prompt |
| 在线评估（Online / Auto-Eval） | 生产 trace 上实时触发 | 生产请求跑完后，裁判模型自动打分，入监控 |

两种形态共用同一个"裁判配置"（评估 Prompt + 裁判模型 + few-shot），只是触发时机不同。

---

## 三、裁判 Prompt 怎么写（校准第一步）

一个可靠的裁判 Prompt 应包含：

1. **评分锚点**：每个分数档位的行为描述（5 分 = 完全切题且有依据；1 分 = 答非所问）
2. **评分维度**：要评什么（相关性 / 忠实度 / 有用性分开评，别混）
3. **输出约束**：JSON 输出（`{"score": 4, "reason": "..."}`），便于程序解析
4. **禁止项**：禁止"范围坍缩"（不要全给满分/全给中间分）；禁止凭印象不给依据

```python
from langsmith.evaluation import LangChainStringEvaluator

judge_prompt = """你是严格的中文客服质检裁判。
对"回答"相对"问题 + 商品资料"评分（1-5）：
- 5 分：完全切题，且所有事实都来自资料
- 3 分：基本切题，但有一处与资料不符
- 1 分：答非所问 或 明显编造
只输出 JSON：{{"score": <1-5>, "reason": "<一句话理由>"}}

问题：{question}
资料：{context}
回答：{answer}
"""

llm_judge = LangChainStringEvaluator(
    "criteria",
    config={
        "criteria": "回答质量",
        "llm": judge_model,             # 裁判模型（可用便宜小模型）
        "prompt": judge_prompt,
    },
)
```

> **工程建议**：裁判模型不必用最强模型——用便宜的小模型（如 gpt-4o-mini / 通义 qwen-turbo）做大批量评测，控制成本。

---

## 四、裁判的校准（面试重点）

裸写一个裁判 prompt 就上生产是常见的坑。校准四法：

### 1. few-shot 对齐
在裁判 prompt 中放 2-3 个"标准评分样例"（good / bad / medium），让裁判的尺度与你的期望对齐。

### 2. 人类反馈回灌（Aligning with Human Preferences）
LangSmith 支持把人工标注结果作为偏好数据，调整裁判 prompt 或微调裁判——让裁判越来越接近"人类判断"。

### 3. 防裁判漂移
- 固定裁判模型 + 固定温度（temperature=0），保证评分可复现
- 定期在"黄金样本"上复跑裁判，验证裁判自身是否漂移

### 4. 交叉校验
同一批样本用多个裁判（或裁判 + 规则 + 人工抽样）对比，发现系统性偏差。

---

## 五、自定义评估器进阶

### 签名与返回结构（面试必考）

```python
from langsmith.schemas import Run, Example

def my_evaluator(run: Run, example: Example | None = None) -> dict:
    # run.outputs: 目标函数输出
    # example.inputs / example.outputs: 数据集样本的输入 / 期望输出
    score = ...          # 0-1 或布尔
    return {
        "key": "metric_name",   # 指标名（聚合时按 key 分组）
        "score": score,          # 打分
        "comment": "...",        # 可追溯的说明
        "metadata": {...},       # 可选附加信息
    }
```

### 轨迹级评估器（Agent 场景）

普通评估器看单次输出；**轨迹级评估器接收整个 Run 树**，可断言"是否发生了某关键步骤"：

```python
def agent_path_check(run: Run, example: Example | None = None) -> dict:
    # 检查 Agent 轨迹：是否调用了检索工具、是否走了正确分支
    tool_calls = collect_tool_calls(run)          # 遍历 run 树的 tool run
    used_retriever = any(r.name == "retrieve" for r in tool_calls)
    return {
        "key": "used_retriever",
        "score": 1 if used_retriever else 0,
        "comment": "未调用检索工具，Agent 未按预期执行",
    }
```

> 对多轮对话 / Agent 轨迹评测的完整方法论见 `05-interview/scenario.md`。

---

## 六、三层互补体系（最佳实践）

| 层 | 类型 | 特点 | 负责什么 |
|----|------|------|---------|
| 第 1 层 | 代码规则 | 快、可复现、零成本 | 格式 / 实体 / 契约 / 路径断言 |
| 第 2 层 | LLM-as-Judge | 灵活、语义理解 | 相关性 / 忠实度 / 有用性 |
| 第 3 层 | 人工标注 | 最可靠、最贵 | 复杂轨迹 / 价值观 / 兜底抽样 |

**实战组合**：一个实验挂多个评估器——`evaluators=[规则, 裁判, 轨迹检查]`，规则层快速兜底，语义层综合评价，人工层抽样复核。

---

## 七、常见坑

| 坑 | 后果 | 解法 |
|----|------|------|
| 裁判 prompt 无评分锚点 | 分数不可复现、忽高忽低 | 写清每档行为描述 |
| 裁判模型不固定 | 分数漂移无法对比实验 | 固定模型 + temperature=0 |
| 只挂一个 LLM 裁判 | 语义分掩盖了格式错误 | 代码规则 + 裁判组合 |
| 裁判 prompt 泄漏业务数据 | 评测数据入库风险 | 脱敏后入评测集 |
| 忽略 self-bias | 裁判与考生同厂模型系统性偏差 | 换厂裁判 / 人工抽样校验 |

---

## 八、小结

- **LLM-as-Judge**：语义评分的利器，分离线评测与在线评估两种形态
- **校准四法**：评分锚点 / few-shot / 人类反馈回灌 / 防漂移
- **自定义评估器**：`(run, example) -> {"key", "score", "comment"}`，轨迹级可断言关键步骤
- **三层互补**：规则（快）+ 裁判（语义）+ 人工（兜底）

**下一步**：[02-prompt-hub.md](02-prompt-hub.md) —— Prompt Hub 提示词版本管理与热更新。