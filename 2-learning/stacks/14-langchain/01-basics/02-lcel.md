# LCEL — 声明式编排管道

> 👶 入门级 | 预计阅读：25 分钟 | 难度：★★☆☆☆

学习目标：理解 LCEL（LangChain Expression Language）的设计思想与声明式语法，掌握管道操作符与 Runnable 系列组件，并能把「手写 Python 链路」改写成 LCEL。

---

## 1. 什么是 LCEL

LCEL 是 LangChain 0.2+ 引入的**声明式组合语法**，核心只有一件事：**用 `|` 管道符把可运行组件串联成一个 Runnable**。

```python
chain = prompt | chat | parser
```

- `prompt`、`chat`、`parser` 都是 `Runnable`（可运行对象）
- `|` 表示「上一个的输出传给下一个的输入」
- 组合结果是**新的 Runnable**，可以继续 `|`，也可以被并行、分支、回调包装

### 为什么需要 LCEL

| 手写 Python | LCEL |
|-------------|------|
| 每一步都要写调用代码，业务逻辑散落各处 | 一行管道表达完整链路，可读性高 |
| 不能被内置工具组合（并行/分支/流式） | 一步获得并行、流式、批处理、回调、重试 |
| 与 LangGraph、LangSmith 集成困难 | 原生可观测，LangSmith 自动记录每步 Trace |
| 每次都要手写异常处理 | 内置超时、重试、fallback 组合器 |

一句话：LCEL 把「管道」变成了**一等公民**，让 AI 链路像 Unix 管道一样可组合。

---

## 2. 管道操作符 `|`

```python
from langchain_core.prompts import ChatPromptTemplate
from langchain_core.output_parsers import StrOutputParser
from langchain_openai import ChatOpenAI

chat = ChatOpenAI(model="gpt-4o-mini", temperature=0.3)
prompt = ChatPromptTemplate.from_messages([
    ("system", "你是{store}推荐助手，请用中文回答。"),
    ("human", "{question}"),
])
parser = StrOutputParser()

chain = prompt | chat | parser
result = chain.invoke({"store": "AI 智能商城", "question": "推荐一款降噪耳机"})
print(result)
```

数据流：

```
invoke({store, question})
  → prompt 填充变量生成消息序列
  → chat 调用大模型返回 AIMessage
  → parser 提取字符串内容
  → 返回最终字符串
```

### chain 是可调用的 Runnable

```python
# 支持多种调用方式
chain.invoke({"store": "x", "question": "q"})   # 单次调用
chain.batch([                                        # 批量调用
    {"store": "x", "question": "q1"},
    {"store": "x", "question": "q2"},
])                                                    # 内部自动并发
for chunk in chain.stream({"store": "x", "question": "q"}):  # 流式输出
    print(chunk, end="")
```

- 流式：`|` 管道天然支持逐 token 输出
- 批处理：`RunnableBatch` 自动并发
- 并行：`RunnableParallel` 让多个分支同时执行

---

## 3. Runnable 核心组件

### 3.1 RunnablePassthrough — 透传

不修改输入，直接通过。常用场景：**把输入字段同时传给多个下游**。

```python
from langchain_core.runnables import RunnablePassthrough

chain = RunnablePassthrough() | (lambda x: f"收到:{x}")

# 更实用的用法：透传原始输入，同时执行一个补充逻辑
chain = {
    "query": RunnablePassthrough(),          # 原始输入原样传给 query
    "length": lambda x: len(x),              # 并行计算长度
}
```

### 3.2 RunnableParallel — 并行分支

多个分支同时处理同一输入，结果以 dict 聚合。

```python
from langchain_core.runnables import RunnableParallel, RunnablePassthrough

# AI 商城场景：对同一句用户输入，同时做「商品推荐」和「条件提取」
branch_recommend = prompt_recommend | chat | parser      # 假设已定义
branch_extract = prompt_extract | chat | parser          # 假设已定义

parallel = RunnableParallel(
    recommend=branch_recommend,
    extract=branch_extract,
    query=RunnablePassthrough(),
)

result = parallel.invoke({"question": "帮我找 300 元以内的入耳式耳机"})
# result = {
#   "recommend": "...商品推荐结果...",
#   "extract":   '{"keyword": "入耳式耳机", "max_price": 300}',
#   "query":     "帮我找 300 元以内的入耳式耳机",
# }
```

> 对应商城项目 `/api/v1` 中的 AI 模式：前端用 `Promise.allSettled` 并行请求 recommend 与 extract，service 内部同样可并行编排。

### 3.3 RunnableLambda — 普通函数适配

把任意 Python 函数包装成 Runnable，用于清洗、格式化、拼接等非模型逻辑。

```python
from langchain_core.runnables import RunnableLambda

def format_row(row: dict) -> str:
    return f"【{row['name']}】{row.get('price', '价格面议')} 元 - {row['reason']}"

chain = (
    RunnableLambda(lambda q: {"question": q})   # 输入整理
    | prompt
    | chat
    | parser
    | RunnableLambda(format_row)                # 输出格式化
)
```

也支持装饰器写法：

```python
from langchain_core.runnables import RunnableLambda

@RunnableLambda
def safe_parse(text: str) -> dict:
    import json
    try:
        return json.loads(text)
    except json.JSONDecodeError:
        return {"error": "解析失败", "raw": text}
```

### 3.4 RunnableBranch — 条件路由（进阶了解）

按输入内容选择不同分支，适合「有库存走检索、无库存走兜底」之类的路由：

```python
from langchain_core.runnables import RunnableBranch

route = RunnableBranch(
    (lambda x: x["has_stock"], db_chain),   # 条件 → 分支
    (lambda x: x["prefer_ai"], ai_chain),
    default_chain,                            # 兜底
)
```

---

## 4. 与手写 Python 代码对比

同一段「推荐商品并格式化」逻辑，两种写法对比：

### 手写 Python

```python
def recommend(preference: str) -> str:
    messages = [("system", "你是推荐助手"), ("human", f"偏好：{preference}")]
    resp = chat.invoke(messages)          # 1. 拼消息
    text = resp.content                    # 2. 取文本
    lines = []
    for line in text.split("\n"):          # 3. 手动解析
        if " - " in line:
            lines.append(line)
    return "\n".join(lines[:3])            # 4. 截断
```

### LCEL 写法

```python
chain = (
    RunnableLambda(lambda p: {"preference": p})     # 输入整理
    | prompt
    | chat
    | parser
    | RunnableLambda(lambda t: "\n".join(
        [l for l in t.split("\n") if " - " in l][:3]))
)
result = chain.invoke("高性价比降噪耳机")
```

| 对比 | 手写 Python | LCEL |
|------|-------------|------|
| 关注点 | 写清每一步代码 | 描述数据如何流动 |
| 复用 | 复制粘贴函数 | 组件即插即用 |
| 横切能力 | 自己实现 | 自动获得流式/批处理/回调 |
| 可观测 | 手动 print | LangSmith 自动 Trace |

---

## 5. 小结与易错点

| 易错点 | 正确做法 |
|--------|----------|
| 混淆 `RunnablePassthrough` 与 `RunnableLambda` | 透传用 Passthrough，加工用 Lambda |
| 在管道中间接 `invoke` | 让上游输出自动流入下游，不要手动调用 |
| 忽视字典字段名不一致 | RunnableParallel 的 key 必须与下游 PromptTemplate 变量名一致 |
| 以为 LCEL 不支持流程控制 | 分支用 RunnableBranch，循环用 LangGraph（03-advanced） |
| 用 `RunnableLambda` 包同步阻塞函数 | 长耗时调用考虑 `RunnableLambda(..., afunc=...)` 异步化 |

下一步：进入 02-core 学习输出解析器（Pydantic 结构化输出）、记忆管理与 RAG 集成，把单条管道升级为完整的 AI 商城链路。