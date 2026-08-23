# LangChain 快速入门

> 👶 入门级 | 预计阅读：20 分钟 | 难度：★☆☆☆☆

学习目标：安装环境、理解 LangChain 四大核心概念（LLM / ChatModel / PromptTemplate / OutputParser）、用最小代码搭建「商品推荐 Chain」。

---

## 1. 安装

```bash
pip install langchain langchain-openai
```

- `langchain`：核心框架，提供 Chain、LCEL、记忆、Agent 等编排能力
- `langchain-openai`：OpenAI 兼容模型的官方集成包（大模型 + Embedding + 多模态）

设置环境变量：

```bash
export OPENAI_API_KEY=sk-xxx
# 若使用 OpenAI 兼容接口（例如通义、硅基流动、OpenRouter），可指定 base_url：
# export OPENAI_BASE_URL=https://dashscope.aliyuncs.com/compatible-mode/v1
```

验证安装：

```python
import langchain
print(langchain.__version__)  # 0.3.x 或 1.x
```

---

## 2. 核心概念

### 2.1 LLM vs ChatModel

| 维度 | LLM（旧接口） | ChatModel（推荐） |
|------|---------------|-------------------|
| 输入 | 纯文本字符串 | 消息序列（System / User / Assistant） |
| 接口 | `llm.invoke(text)` | `chat.invoke([...])` |
| 能力 | 仅文字补全 | 支持多轮、系统提示、工具调用、流式 |
| 现状 | 多已废弃 | **新项目一律使用 ChatModel** |

```python
from langchain_openai import ChatOpenAI

chat = ChatOpenAI(model="gpt-4o-mini", temperature=0.3)
```

### 2.2 PromptTemplate — 提示词模板

把提示词做成「变量 + 模板」，避免在代码里拼接字符串：

```python
from langchain_core.prompts import PromptTemplate

prompt = PromptTemplate.from_template(
    "你是{store}的推荐助手。根据用户偏好【{preference}】推荐 3 件商品，"
    "只输出商品名和一句话理由。"
)
```

多轮对话用 `ChatPromptTemplate`，支持 HumanMessage / SystemMessage 插槽：

```python
from langchain_core.prompts import ChatPromptTemplate

chat_prompt = ChatPromptTemplate.from_messages([
    ("system", "你是{store}的推荐助手，语言必须使用中文。"),
    ("human", "用户偏好：{preference}"),
])
```

### 2.3 OutputParser — 输出解析

LLM 返回的是字符串，解析器把它变成结构化对象：

```python
from langchain_core.output_parsers import StrOutputParser

parser = StrOutputParser()          # 直接取字符串内容
```

## 3. 第一个 Chain

最原始的组装方式是 `LLMChain`（旧 API 风格），先看清它的本质：

```python
from langchain_core.prompts import PromptTemplate
from langchain_openai import ChatOpenAI
from langchain.chains import LLMChain

chat = ChatOpenAI(model="gpt-4o-mini", temperature=0.3)
prompt = PromptTemplate.from_template("把这句话翻译成英文：{text}")

chain = LLMChain(llm=chat, prompt=prompt)
result = chain.invoke({"text": "你好，世界"})
print(result["text"])  # Hello, world
```

> 注意：LLMChain 在 0.3.x 已标记 deprecated。新代码一律使用 LCEL 写法（见 02-lcel.md）：
> `chain = prompt | chat | parser`。本教程后续章节统一使用 LCEL。

## 4. 最小案例：商品推荐 Chain（AI 商城）

结合商城场景：根据用户偏好推荐商品。输入原始用户输入，输出推荐列表。

```python
from langchain_core.prompts import ChatPromptTemplate
from langchain_core.output_parsers import StrOutputParser
from langchain_openai import ChatOpenAI

chat = ChatOpenAI(model="gpt-4o-mini", temperature=0.5)

prompt = ChatPromptTemplate.from_messages([
    ("system", "你是{store}智能推荐助手。根据用户偏好推荐商品，"
               "必须输出「商品名 - 一句话推荐理由」，每行一条，最多{limit}条。"),
    ("human", "用户偏好：{preference}"),
])

chain = prompt | chat | StrOutputParser()

response = chain.invoke({
    "store": "AI 智能商城",
    "preference": "性价比高的无线降噪耳机，预算 200-400 元",
    "limit": 3,
})

print(response)
# 输出示例：
# 声阔 Life P3 - 主动降噪加 300 元档位，性价比非常突出
# 小米 Redmi Buds 5 - 半入耳设计，佩戴舒适，价格贴近预算下限
# 漫步者 W820NB - 头戴式降噪，日常通勤续航持久
```

### 加一个「价格条件提取」链

商城项目的 `/extract` 思路：先让模型从自然语言中提取结构化条件，再走传统搜索兜底。这里展示一个简化版：

```python
prompt_extract = ChatPromptTemplate.from_template(
    '从用户的搜索语句中提取购物条件，只输出 JSON：\n'
    '格式：{{"keyword": "...", "min_price": null, "max_price": null}}\n'
    '用户语句：{query}'
)

extract_chain = prompt_extract | chat | StrOutputParser()
print(extract_chain.invoke({"query": "300 块以内的入耳式耳机"}))
# {"keyword": "入耳式耳机", "min_price": null, "max_price": 300}
```

## 5. 小结与易错点

| 易错点 | 正确做法 |
|--------|----------|
| 用旧版 `LLMChain` 走到底 | 新项目用 `prompt \| chat \| parser` LCEL 写法 |
| 在代码里 `f-string` 拼提示词 | 用 PromptTemplate 管理变量，便于复用与测试 |
| 忽视 ChatModel 的多轮能力 | 多轮对话 / 工具调用用 ChatPromptTemplate + MessagesPlaceholder |
| 直接信任模型返回的 JSON | 用 OutputParser / Pydantic 解析，并对解析失败做兜底 |

进阶路径：真正的商城推荐链路是 **Chain + Agent + 向量检索工具** 的组合，接下来学习 02-lcel 掌握声明式编排，再进入 02-core 学习输出解析器与记忆管理。