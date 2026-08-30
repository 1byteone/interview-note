# LangSmith 快速上手（入门基础）

> 目标：10 分钟跑通 LangSmith——完成环境配置、代码零侵入接入、控制台看懂第一条 Trace。
> 学习方式：动手为主，跟着抄一遍即可建立体感。

---

## 一、LangSmith 是什么（30 秒定位）

LangSmith 是 LangChain 官方推出的 **AI 应用可观测性与评测平台**，覆盖 LLM / Agent 应用全生命周期：

| 阶段 | 能力 | 解决的问题 |
|------|------|-----------|
| 开发期 | Tracing（追踪） | "这个回答为什么错？卡在哪一步？" |
| 评测期 | Dataset + Experiment | "换 prompt / 换模型后质量到底是好是坏？" |
| 生产期 | Monitoring + Feedback | "线上回答变差了怎么发现？失败样本怎么沉淀？" |

**铁三角分工**（必背）：LangChain **构建**（造）、LangGraph **编排**（跑）、LangSmith **验证**（看 + 量）。

---

## 二、前置条件

- 一个 [LangSmith 账号](https://smith.langchain.com/)（免费额度即可体验）
- Python 3.9+，已安装 `langchain` / `langgraph`（示例用 `langchain-openai`）
- 一个可用的大模型 API key（OpenAI / Anthropic / 通义均可）

```bash
pip install langsmith langchain langchain-openai
```

---

## 三、三步接入（重点）

### 第 1 步：创建项目与 API Key

1. 登录 [smith.langchain.com](https://smith.langchain.com/)
2. 左侧 Projects → 创建一个项目（如 `mall-ai-rag`）
3. Settings → API Keys → 创建 Key（形如 `lsv2_pt_...`）

### 第 2 步：配置环境变量

```bash
export LANGCHAIN_TRACING_V2=true        # 开启追踪（核心开关）
export LANGCHAIN_API_KEY="lsv2_xxx"     # API Key
export LANGCHAIN_PROJECT="mall-ai-rag"  # 归属项目（可选，默认 langchain）
```

> Windows PowerShell：`$env:LANGCHAIN_TRACING_V2="true"`，以此类推。
> 生产环境推荐用密钥管理（Vault / CI Secret），严禁硬编码。

### 第 3 步：跑一个最小例子

```python
from langchain_openai import ChatOpenAI
from langchain_core.prompts import ChatPromptTemplate

llm = ChatOpenAI(model="gpt-4o-mini")
prompt = ChatPromptTemplate.from_messages([
    ("system", "你是一名商品推荐助手，根据用户偏好推荐 3 款商品。"),
    ("human", "用户偏好：{preference}"),
])
chain = prompt | llm

result = chain.invoke({"preference": "性价比高的国产手机"})
print(result.content)
```

**跑完后去控制台看魔法**：`mall-ai-rag` 项目下出现一条 Trace，展开瀑布视图能看到：

```
Trace（一次请求）
└── ChatPromptTemplate  (输入: preference)
    └── ChatOpenAI       (输入/输出/token数/延迟/模型名)
```

**业务代码一行没改**——这就是"零侵入自动上报"：底层走 LangChain 的 Callbacks 机制，设置环境变量后自动生效。

---

## 四、控制台核心界面速览

| 界面 | 作用 |
|------|------|
| Projects | 项目列表，按应用划分的 trace 集合 |
| Traces | 瀑布视图：展开单次执行，看每一步输入输出、耗时、token |
| Datasets | 评测数据集（考卷）管理 |
| Experiments | 评测实验与对比矩阵（成绩单） |
| Prompts | Prompt Hub：提示词版本仓库 |
| Annotation Queues | 人工标注队列（评审工作流） |
| Monitoring | 生产监控看板（质量 / 健康 / 成本） |

---

## 五、非 LangChain 代码如何上报？

纯 Python 函数 / FastAPI 接口不经过 LangChain 组件时，用 `traceable` 装饰器手动打点：

```python
from langsmith import traceable
from langsmith.wrappers import wrap_openai

@traceable(name="compute_embedding")      # 普通函数一分钟接入
def get_embedding(text: str) -> list:
    return embed_model.embed_query(text)

# OpenAI 客户端也可以包装（自动记录每次调用）
client = wrap_openai(openai.Client())
```

`traceable` 是 LangSmith 的价值放大器：**不换框架也能获得统一 trace**。

---

## 六、常见问题排查

| 现象 | 原因 | 解决 |
|------|------|------|
| 控制台没有 trace | 环境变量没加载 / 项目名不对 | 确认 `LANGCHAIN_TRACING_V2=true` 与 Key 正确 |
| key 报 401 | API key 无效 | 重新创建 Key，确认复制完整 |
| trace 显示为 default 项目 | 未设置 PROJECT | 显式指定 `LANGCHAIN_PROJECT` |
| 想控制成本 | 全量上报费用高 | 设置采样率 `LANGCHAIN_TRACING_SAMPLING_RATE=0.1` |

---

## 七、小结与下一步

- 接入只需 **2 个环境变量 + 0 行业务代码**，10 分钟跑通
- Trace 是你调试 Agent 的第一现场：**先看 trace，再改代码**
- 纯函数用 `traceable`，OpenAI 客户端用 `wrap_openai` 也能接入

**下一步**：进入 [02-core/01-tracing-concepts.md](../02-core/01-tracing-concepts.md)，理解 Trace / Run / Thread 的层级关系，学会"读"瀑布视图。